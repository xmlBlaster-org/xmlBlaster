/*------------------------------------------------------------------------------
Name:      DeliveryManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDeliveryInterceptor;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.util.property.PropString;

import java.util.ArrayList;

/**
 * Manages the sending of messages and commands and does error recovery
 * further we communicate with the dispatcher plugin if one is configured. 
 * <p />
 * There is one instance of this class per queue and remote connection.
 * @author xmlBlaster@marcelruff.info
 */
public final class DeliveryManager implements I_Timeout, I_QueuePutListener
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;
   private final I_Queue msgQueue;
   private final DeliveryConnectionsHandler deliveryConnectionsHandler;
   private final DeliveryWorkerPool deliveryWorkerPool;
   private final I_MsgErrorHandler failureListener;
   private final I_MsgSecurityInterceptor securityInterceptor;
   private final I_MsgDeliveryInterceptor msgInterceptor;
   private I_ConnectionStatusListener connectionStatusListener;
   private final String typeVersion;
   /** If > 0 does burst mode */
   private long collectTime = -1L;

   private boolean deliveryWorkerIsActive = false;

   /** The worker for synchronous invocations */
   private DeliveryWorker syncDeliveryWorker;

   private final Timeout burstModeTimer;
   private Timestamp timerKey = null;

   private int notifyCounter = 0;

   private boolean isShutdown = false;
   private boolean isSyncMode = false;
   private boolean trySyncMode = false; // true: client side queue embedding, false: server side callback queue

   /**
    * @param msgQueue The message queue witch i use (!!! TODO: this changes, we should pass it on every method where needed)
    * @param addrArr The addresses i shall connect to
    */
   public DeliveryManager(Global glob, I_MsgErrorHandler failureListener, I_MsgSecurityInterceptor securityInterceptor,
                          I_Queue msgQueue, AddressBase[] addrArr) throws XmlBlasterException {
      if (failureListener == null || msgQueue == null)
         throw new IllegalArgumentException("DeliveryManager failureListener=" + failureListener + " msgQueue=" + msgQueue);

      this.ME = "DeliveryManager-" + msgQueue.getStorageId().getId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");

      if (log.TRACE) log.trace(ME, "Loading DeliveryManager ...");

      this.msgQueue = msgQueue;
      this.failureListener = failureListener;
      this.securityInterceptor = securityInterceptor;
      this.deliveryWorkerPool = glob.getDeliveryWorkerPool();
      this.burstModeTimer = glob.getBurstModeTimer();
      this.deliveryConnectionsHandler = glob.createDeliveryConnectionsHandler(this, addrArr);

      /*
       * Check i a plugin is configured ("DispatchPlugin/defaultPlugin")
       * If configured, the plugin instance is searched in the Global scope
       * and if none is found one is created (see DispatcherPluginManager)
       * Default server setting is to use no dispatcher plugin
       */
      PropString propString = new PropString(PluginManagerBase.NO_PLUGIN_TYPE); // "undef";
      if (addrArr != null && addrArr.length > 0) // Check if client wishes a specific plugin
         propString.setValue(addrArr[0].getDispatchPlugin());
      this.typeVersion = propString.getValue();
      this.msgInterceptor = glob.getDispatchPluginManager().getPlugin(this.typeVersion); // usually from cache
      if (log.TRACE) log.trace(ME, "DispatchPlugin/defaultPlugin=" + propString.getValue() + " this.msgInterceptor="  + this.msgInterceptor);
      if (this.msgInterceptor != null) {
         this.msgInterceptor.addDeliveryManager(this);
         if (log.TRACE) log.trace(ME, "Activated dispatcher plugin '" + this.typeVersion + "'");
      }

      this.msgQueue.addPutListener(this); // to get putPre() and putPost() events
   }

   /**
    * Set behavior of dispatch framework. 
    * @param trySyncMode true: client side queue embedding, false: server side callback queue
    * defaults to false
    */
   public void trySyncMode(boolean trySyncMode) {
      this.trySyncMode = trySyncMode;
      switchToSyncMode();
   }

   public final void updateProperty(CbQueueProperty cbQueueProperty) throws XmlBlasterException {
      this.deliveryConnectionsHandler.initialize(cbQueueProperty.getCallbackAddresses());
   }

   public void finalize() {
      removeBurstModeTimer();
      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   public I_Queue getQueue() {
      return this.msgQueue;
   }

   /**
    * Register yourself if you want to be informed about the remote connection status. 
    * NOTE: Currently max. one listener is implemented
    * @param connectionStatusListener The implementation which listens on connectionState events (e.g. XmlBlasterAccess.java)
    */
   public void addConnectionStatusListener(I_ConnectionStatusListener connectionStatusListener) {
      if (this.connectionStatusListener != null) {
         log.error(ME, "addConnectionStatusListener() ignored, there is already a listener registered");
         return;
      }
      this.connectionStatusListener = connectionStatusListener;
   }

   public void removeConnectionStateListener(I_ConnectionStatusListener connectionStatusListener) {
      this.connectionStatusListener = null;
   }

   /**
    * The name in the configuration file for the plugin
    * @return e.g. "Priority,1.0"
    */
   public String getTypeVersion() {
      return this.typeVersion;
   }

   /**
    * @return The import/export encrypt handle or null if created by a SubjectInfo (no session info available)
    */
   public I_MsgSecurityInterceptor getMsgSecurityInterceptor() {
      return this.securityInterceptor;
   }

   /**
    * @return The handler of all callback plugins, is never null
    */
   public DeliveryConnectionsHandler getDeliveryConnectionsHandler() {
      return this.deliveryConnectionsHandler;
   }

   /** Call by DeliveryConnectionsHandler on state transition */
   void toAlive(ConnectionStateEnum oldState) {
      
      if (this.trySyncMode) {
         switchToSyncMode();
      }

      // Remember the current collectTime
      AddressBase addr = deliveryConnectionsHandler.getAliveAddress();
      if (addr == null) {
         log.error(ME, "toAlive action has no alive address");
         return;
      }

      // 1. We allow a client to intercept and for example destroy all entries in the queue
      if (this.connectionStatusListener != null)
         this.connectionStatusListener.toAlive(this, oldState);

      // 2. If a dispatch plugin is registered it may do its work
      if (this.msgInterceptor != null)
         this.msgInterceptor.toAlive(this, oldState);

      collectTime = addr.getCollectTime(); // burst mode if > 0L

      activateDeliveryWorker(); // will be delayed if burst mode timer is activated
   }

   /** Call by DeliveryConnectionsHandler on state transition */
   void toPolling(ConnectionStateEnum oldState) {

      switchToASyncMode();

      // 1. We allow a client to intercept and for example destroy all entries in the queue
      if (this.connectionStatusListener != null)
         this.connectionStatusListener.toPolling(this, oldState);

      // 2. If a dispatch plugin is registered it may do its work
      if (this.msgInterceptor != null)
         this.msgInterceptor.toPolling(this, oldState);
   }

   /** Call by DeliveryConnectionsHandler on state transition */
   void toDead(ConnectionStateEnum oldState, AddressBase address, XmlBlasterException ex) {
      ex.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_DEAD);
      
      // 1. We allow a client to intercept and for example destroy all entries in the queue
      if (this.connectionStatusListener != null)
         this.connectionStatusListener.toDead(this, oldState, ex.getMessage());

      // 2. If a dispatch plugin is registered it may do its work
      if (this.msgInterceptor != null)
         this.msgInterceptor.toDead(this, oldState, ex.getMessage());

      givingUpDelivery(ex);
   }

   private void givingUpDelivery(XmlBlasterException ex) {
      if (log.TRACE) log.trace(ME, "Entering givingUpDelivery(), state is " + deliveryConnectionsHandler.getState());
      removeBurstModeTimer();
      // The error handler flushed the queue and does error handling with them
      this.failureListener.handleError(new MsgErrorInfo(glob, (MsgQueueEntry)null, ex));
      shutdown();
   }
   
   /**
    * Called by DeliveryWorker if an Exception occured in sync mode
    */
   void handleSyncWorkerException(ArrayList entryList, Throwable throwable) throws XmlBlasterException {

      if (log.CALL) log.call(ME, "Sync delivery failed connection state is " + deliveryConnectionsHandler.getState().toString() + ": " + throwable.toString());
      
      XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,ME,null,throwable);

      if (xmlBlasterException.isUser()) {
         // Exception from remote client from update(), pass it to error handler and carry on ...
         MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         getMsgErrorHandler().handleErrorSync(new MsgErrorInfo(glob, entries, xmlBlasterException));
         return;
      }
      else if (xmlBlasterException.isCommunication()) {

         if (this.msgInterceptor != null && isPolling()) { // If we have a plugin it shall handle it
            try {
               entryList = this.msgInterceptor.handleNextMessages(this, entryList);
               if (entryList != null && entryList.size() > 0) {
                  MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                  this.failureListener.handleError(new MsgErrorInfo(glob, entries, xmlBlasterException));
               }
            }
            catch (XmlBlasterException xmlBlasterException2) {
               internalError(xmlBlasterException2);
            }
            if (entryList != null && entryList.size() > 0) {
               MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
               this.failureListener.handleError(new MsgErrorInfo(glob, entries, xmlBlasterException));
            }
            return;
         }

         // Exception from connection to remote client (e.g. from Corba layer)
         // DeliveryManager handles this
         // Error handling in sync mode
         // 1. throwExceptionBackToPusher
         // 2. Switch to async mode and collect message (wait on better times)
         // 3. If we have serious problems (programming exceptions or isDead()) throw exception back
         // 4. Pass exception to an error handler plugin
         switchToASyncMode();

         // Simulate return values, and manipulate missing informations into entries ...
         I_QueueEntry[] entries = (I_QueueEntry[])entryList.toArray(new I_QueueEntry[entryList.size()]);
         getDeliveryConnectionsHandler().createFakedReturnObjects(entries, Constants.STATE_OK, Constants.INFO_QUEUED);
         msgQueue.put(entries, I_Queue.IGNORE_PUT_INTERCEPTOR);

         if (log.TRACE) log.trace(ME, "Delivery failed, pushed " + entries.length + " entries into tail back queue");
      }
      else {
         if (log.TRACE) log.trace(ME, "Invocation failed: " + xmlBlasterException.getMessage());
         throw xmlBlasterException;
      }
   }

   /**
    * Called by DeliveryWorker if an Exception occured
    */
   void handleWorkerException(ArrayList entryList, Throwable throwable) {
      // Note: The DeliveryManager is notified about connection problems directly by its DeliveryConnectionsHandler
      //       we don't need to take care of ErrorCode.COMMUNICATION*
      if (log.CALL) log.call(ME, "Async delivery failed connection state is " + deliveryConnectionsHandler.getState().toString() + ": " + throwable.toString());
      if (entryList == null) {
         if (!this.isShutdown)
            log.warn(ME, "Didn't expect null entryList in handleWorkerException() for throwable " + throwable.getMessage());
         return;
      }

      if (throwable instanceof XmlBlasterException) {
         XmlBlasterException ex = (XmlBlasterException)throwable;
         if (log.TRACE) log.trace(ME, "Invocation or callback failed: " + ex.getMessage());
         if (ex.isUser()) {
            // Exception from remote client from update(), pass it to error handler and carry on ...
            MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
            getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, ex));
         }
         else if (ex.isCommunication()) {

            if (this.msgInterceptor != null && isPolling()) { // If we have a plugin it shall handle it
               try {
                  entryList = this.msgInterceptor.handleNextMessages(this, entryList);
                  if (entryList != null && entryList.size() > 0) {
                     MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                     this.failureListener.handleError(new MsgErrorInfo(glob, entries, ex));
                  }
               }
               catch (XmlBlasterException ex2) {
                  internalError(ex2);
               }
               if (entryList != null && entryList.size() > 0) {
                  MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                  this.failureListener.handleError(new MsgErrorInfo(glob, entries, ex));
               }
            }

            // Exception from connection to remote client (e.g. from Corba layer)
            // DeliveryManager handles this
         }
         else {
            log.error(ME, "Callback failed: " + ex.toString());
            ex.printStackTrace();
            internalError(ex);
         }
      }
      else {
         log.error(ME, "Callback failed: " + throwable.toString());
         throwable.printStackTrace();
         internalError(new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "", throwable));
      }
   }

   public I_MsgErrorHandler getMsgErrorHandler() {
      return this.failureListener;
   }

   /**
    * We register a QueuePutListener and all put() into the queue are
    * intercepted - our put() is called instead.
    * We then deliver this QueueEntry directly to the remote
    * connection and return synchronously the returned value or the 
    * Exception if one is thrown.
    */
   public void switchToSyncMode() {
      if (this.isSyncMode) return;

      synchronized (this) {
         if (this.isSyncMode) return;
         if (this.syncDeliveryWorker == null) this.syncDeliveryWorker = new DeliveryWorker(glob, this);

         this.isSyncMode = true;
         log.info(ME, "Switched to synchronous message delivery");
         
         if (this.timerKey != null)
            log.error(ME, "Burst mode timer was activated and we switched to synchronous delivery" +
                          " - handling of this situation is not coded yet");
         removeBurstModeTimer();
      }
   }

   /**
    * Switch back to asynchronous mode. 
    * Our thread pool will take the messages out of the queue
    * and deliver them in asynchronous mode.
    */
   public void switchToASyncMode() {
      if (!this.isSyncMode) return;

      synchronized (this) {
         if (!this.isSyncMode) return;
         //this.msgQueue.removePutListener(this);
         activateDeliveryWorker(); // just in case there are some messages pending in the queue
         this.isSyncMode = false;
         log.info(ME, "Switched to asynchronous message delivery");
      }
   }

   /**
    * @see I_QueuePutListener#putPre(I_QueueEntry)
    */
   public boolean putPre(I_QueueEntry queueEntry) throws XmlBlasterException {
      if (!this.isSyncMode) return true; // Add entry to queue

      I_QueueEntry[] queueEntries = new I_QueueEntry[1];
      queueEntries[0] = queueEntry;
      return putPre(queueEntries);
   }

   /**
    * @see #putPre(I_QueueEntry)
    * @see I_QueuePutListener#putPre(I_QueueEntry[])
    */
   public boolean putPre(I_QueueEntry[] queueEntries) throws XmlBlasterException {
      if (!this.isSyncMode) return true; // Add entry to queue
      
      if (log.TRACE) log.trace(ME, "putPre() - Got " + queueEntries.length + " QueueEntries to deliver synchronously ...");
      ArrayList entryList = new ArrayList(queueEntries.length);
      for (int ii=0; ii<queueEntries.length; ii++) entryList.add(queueEntries[ii]);
      this.syncDeliveryWorker.run(entryList);
      return false;
   }

   /**
    * @see I_QueuePutListener#putPost(I_QueueEntry)
    */
   public void putPost(I_QueueEntry queueEntry) throws XmlBlasterException {
      //log.error(ME, "DEBUG ONLY: putPost() is not implemented");
      if (!this.isSyncMode) {
         notifyAboutNewEntry();
         if (((MsgQueueEntry)queueEntry).wantReturnObj()) {
            // Simulate return values, and manipulate missing informations into entries ...
            I_QueueEntry[] entries = new I_QueueEntry[] { queueEntry };
            getDeliveryConnectionsHandler().createFakedReturnObjects(entries, Constants.STATE_OK, Constants.INFO_QUEUED);
         }
      }
   }

   /**
    * @see #putPost(I_QueueEntry)
    * @see I_QueuePutListener#putPost(I_QueueEntry[])
    */
   public void putPost(I_QueueEntry[] queueEntries) throws XmlBlasterException {
      //log.error(ME, "DEBUG ONLY: putPost([]) is not implemented");
      if (!this.isSyncMode) {
         notifyAboutNewEntry();
         if (queueEntries.length > 0 && ((MsgQueueEntry)queueEntries[0]).wantReturnObj()) {
            // Simulate return values, and manipulate missing informations into entries ...
            getDeliveryConnectionsHandler().createFakedReturnObjects(queueEntries, Constants.STATE_OK, Constants.INFO_QUEUED);
         }
      }
   }

   /**
    * Here we prepare messages which are coming directly from the queue.
    * <ol>
    *   <li>We eliminate expired messages</li>
    *   <li>We make a shallow copy of the message.
    *       We need to do this, out messages are references directly into the queue.
    *       The delivery framework is later changing the QoS
    *       and plugins may change the content - and this should not modify the queue entries</li>
    * </ol>
    */
   public ArrayList prepareMsgsFromQueue(ArrayList entryList) {

      if (entryList == null || entryList.size() < 1) {
         if (log.TRACE) log.trace(ME, "Got zero messages from queue, expected at least one, can happen if client disconnected in the mean time: " + toXml(""));
         return null;
      }

      // Remove all expired messages and do a shallow copy
      int size = entryList.size();
      ArrayList result = new ArrayList(size);
      for (int ii=0; ii<size; ii++) {
         MsgQueueEntry entry = (MsgQueueEntry)entryList.get(ii);
         // Take care to remove the filtered away messages from the queue as well
         //log.error(ME, "DEBUG ONLY: Analyze msg " + entry.toXml());
         if (entry.isDestroyed()) {
            log.info(ME, "Message " + entry.getLogId() + " is destroyed, ignoring it");
            if (log.TRACE) log.trace(ME, "Message " + entry.getLogId() + " is destroyed, ignoring it: " + entry.toXml());
            try {
               msgQueue.removeRandom(entry);
            }
            catch (Throwable e) {
               log.error(ME, "Internal error when removing expired message " + entry.getLogId() + " from queue, no recovery implemented, we continue: " + e.toString());
            }
            continue;
         }
         result.add(entry.clone()); // expired messages are sent as well
      }

      return result;
   }

   /**
    * When somebody puts a new entry into the queue, we want to be
    * notified about this after the entry is fed.
    * <p>
    * Called by I_Queue.putPost()
    */
   public void notifyAboutNewEntry() {
      if (log.CALL) log.call(ME, "Entering notifyAboutNewEntry("+this.notifyCounter+")");
      this.notifyCounter++;
      activateDeliveryWorker();
   }

   /**
    * Counts how often a new entry was added since the current worker thread was started. 
    */
   public int getNotifyCounter() {
      return this.notifyCounter;
   }

   /**
    * Give the callback worker thread a kick to deliver the messages. 
    * Throws no exception.
    */
   private void activateDeliveryWorker() {

      if (checkSending() == false)
         return;

      if (useBurstModeTimer() == true)
         return;

      startWorkerThread(false);
   }

   /**
    * @return true if a burst mode timer was activated
    */
   private boolean useBurstModeTimer() {
      if (collectTime <= 0L) return false;
      
      // Messages are sent delayed on timeout (burst mode)

      if (log.TRACE) log.trace(ME, "Executing useBurstModeTimer() collectTime=" + collectTime + " deliveryWorkerIsActive=" + deliveryWorkerIsActive);
      synchronized (this) {
         if (this.isShutdown) return false;
         if (this.timerKey == null) {
            log.info(ME, "Starting burstMode timer with " + collectTime + " msec");
            this.timerKey = burstModeTimer.addTimeoutListener(this, collectTime, null);
         }
      }
      return true;
   }

   /**
    * Remove the burst mode timer
    */
   private void removeBurstModeTimer() {
      synchronized (this) {
         if (this.timerKey != null) {
            this.burstModeTimer.removeTimeoutListener(timerKey);
            this.timerKey = null;
         }
      }
   }
   
   /**
    * @param fromTimeout for logging only
    */
   private void startWorkerThread(boolean fromTimeout) {
      if (this.deliveryWorkerIsActive == false) {
         //if (log.TRACE) log.trace(ME, "Doing startWorkerThread("+fromTimeout+")");
         synchronized (this) {
            //if (log.TRACE) log.trace(ME, "Doing startWorkerThread(isShutdown="+this.isShutdown+", deliveryWorkerIsActive="+this.deliveryWorkerIsActive+") inside sync");
            if (this.isShutdown) return;
            if (this.deliveryWorkerIsActive == false) { // send message directly
               this.deliveryWorkerIsActive = true;
               this.notifyCounter = 0;
               try {
                  this.deliveryWorkerPool.execute(this, new DeliveryWorker(glob, this));
               } catch (XmlBlasterException e) {
                  log.error(ME, "Unexpected error occurred: " + e.toString());
                  e.printStackTrace();
               }
            }
         }
         return;
      }

      if (fromTimeout) {
         log.info(ME, "Burst mode timeout occurred, last callback worker thread is not finished - we do nothing (the worker thread will give us a kick)");
      }
      else {
         if (log.TRACE) log.trace(ME, "Last callback worker thread is not finished - we do nothing (the worker thread will give us a kick)");
      }
   }

   public boolean isDead() {
      return deliveryConnectionsHandler.isDead();
   }

   public boolean isPolling() {
      return deliveryConnectionsHandler.isPolling();
   }

   /**
    * @return true is status is OK and we can try to send a message
    */
   private boolean checkSending() {
      if (this.isShutdown) {
         if (log.TRACE) log.trace(ME, "The dispatcher is shutdown, can't activate callback worker thread.");
         return false; // assert
      }

      if (msgQueue.isShutdown()) { // assert
         if (log.TRACE) log.trace(ME, "The queue is shutdown, can't activate callback worker thread.");
         // e.g. client has disconnected on the mean time.
         //Thread.currentThread().dumpStack();
         shutdown();
         return false;
      }

      if (deliveryConnectionsHandler.isDead()) {
         String text = "No recoverable remote connection available, giving up queue " + msgQueue.getStorageId() + ".";
         if (log.TRACE) log.trace(ME, text);
         givingUpDelivery(new XmlBlasterException(glob,ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, text)); 
         return false;
      }

      if (msgQueue.getNumOfEntries() == 0L) {
         //if (log.TRACE) log.trace(ME, "numOfEntries==0");
         return false;
      }

      if (this.msgInterceptor != null) {
         if (this.msgInterceptor.doActivate(this) == false) {
            if (log.TRACE) log.trace(ME, "this.msgInterceptor.doActivate==false");
            return false; // A plugin told us to suppress sending the message
         }
         return true;
      }

      if (deliveryConnectionsHandler.isPolling()) {
         if (log.TRACE) log.trace(ME, "Can't send message as connection is lost and we are polling");
         return false;
      }

      //if (log.TRACE) log.trace(ME, "Check sending is OK");
      return true;
   }

   /**
    * We are notified about the burst mode timeout through this method.
    * @param userData You get bounced back your userData which you passed
    *                 with Timeout.addTimeoutListener()
    */
   public void timeout(Object userData) {
      this.timerKey = null;
      log.info(ME, "Burst mode timeout occurred, starting callback worker thread ...");
      startWorkerThread(true);
   }


   /**
    * @return The interceptor plugin if available, otherwise null
    */
   public I_MsgDeliveryInterceptor getMsgDeliveryInterceptor() {
      return this.msgInterceptor;
   }

   /**
    * Set new callback addresses, typically after a session login/logout
    */
   public void setAddresses(AddressBase[] addr) throws XmlBlasterException {
      this.deliveryConnectionsHandler.initialize(addr);
   }

   /**
    * The worker notifies us that it is finished, if messages are available
    * it is triggered again.
    */
   void setDeliveryWorkerIsActive(boolean val) {
      deliveryWorkerIsActive = val;
      if (val == false) {
         if (this.isShutdown) return;

         if (msgQueue.getNumOfEntries() > 0) {
            if (log.TRACE) log.trace(ME, "Finished callback job. Giving a kick to send the remaining " + msgQueue.getNumOfEntries() + " messages.");
            try {
               activateDeliveryWorker();
            }
            catch(Throwable e) {
               log.error(ME, e.toString()); e.printStackTrace(); // Assure the queue is flushed with another worker
            }
         }
      }
   }

   /**
    * Called from DeliveryWorker when internal error (Throwable) occurred to avoid infinite looping
    */
   public void internalError(Throwable throwable) {
      givingUpDelivery((throwable instanceof XmlBlasterException) ? (XmlBlasterException)throwable :
                       new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "", throwable));
      log.error(ME, "PANIC: Internal error, doing shutdown: " + throwable.getMessage());
      shutdown();
   }

   /**
    * @return A container holding some statistical delivery information
    */
   public DeliveryStatistic getDeliveryStatistic() {
      return deliveryConnectionsHandler.getDeliveryStatistic();
   }

   /**
    * Stop all callback drivers of this client.
    * Possibly invoked twice (givingUpDelivery() calls it indirectly as well)
    * We don't shutdown the corresponding queue.
    */
   public void shutdown() {
      if (log.CALL) log.call(ME, "Entering shutdown ...");
      if (this.isShutdown) return;
      synchronized (this) {
         if (this.isShutdown) return;
         this.isShutdown = true;

         removeBurstModeTimer();

         // NOTE: We would need to remove the 'final' qualifier to be able to set to null

         if (this.msgInterceptor != null) {
            this.msgInterceptor.shutdown(this);
            //this.msgInterceptor = null;
         }
         if (deliveryConnectionsHandler != null) {
            this.deliveryConnectionsHandler.shutdown();
            //this.deliveryConnectionsHandler = null;
         }
         removeBurstModeTimer();
         //this.burstModeTimer = null;
         //this.msgQueue = null;
         //this.failureListener = null;
         //this.securityInterceptor = null;

         //if (this.deliveryWorkerPool != null) {
         //   this.deliveryWorkerPool.shutdown(); NO: not here, is the scope and duty of Global
         //   this.deliveryWorkerPool = null;
         //}

         if (this.syncDeliveryWorker != null)
            this.syncDeliveryWorker.shutdown();
      }
   }

   /**
    * For logging
    */
   public String getId() {
      return this.msgQueue.getStorageId().getId();
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(2000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<DeliveryManager id='").append(getId()).append("'>");
      sb.append(deliveryConnectionsHandler.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append(" <deliveryWorkerIsActive>").append(deliveryWorkerIsActive).append("</deliveryWorkerIsActive>");
      sb.append(offset).append("</DeliveryManager>");

      return sb.toString();
   }
}
