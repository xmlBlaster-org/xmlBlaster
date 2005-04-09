/*------------------------------------------------------------------------------
Name:      DispatchManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueuePutListener;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import org.xmlBlaster.authentication.plugins.I_MsgSecurityInterceptor;
import org.xmlBlaster.util.property.PropString;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;

/**
 * Manages the sending of messages and commands and does error recovery
 * further we communicate with the dispatcher plugin if one is configured. 
 * <p />
 * There is one instance of this class per queue and remote connection.
 * @author xmlBlaster@marcelruff.info
 */
public final class DispatchManager implements I_Timeout, I_QueuePutListener
{
   public final String ME;
   private final Global glob;
   private final LogChannel log;
   private final I_Queue msgQueue;
   private final DispatchConnectionsHandler dispatchConnectionsHandler;
   private final I_MsgErrorHandler failureListener;
   private final I_MsgSecurityInterceptor securityInterceptor;
   private final I_MsgDispatchInterceptor msgInterceptor;
//   private I_ConnectionStatusListener connectionStatusListener;
   private HashSet connectionStatusListeners;
   private final String typeVersion;
   /** If > 0 does burst mode */
   private long collectTime = -1L;

   private boolean dispatchWorkerIsActive = false;

   /** The worker for synchronous invocations */
   private DispatchWorker syncDispatchWorker;

   private Timestamp timerKey = null;

   private int notifyCounter = 0;

   private boolean isShutdown = false;
   private boolean isSyncMode = false;
   private boolean trySyncMode = false; // true: client side queue embedding, false: server side callback queue

   private boolean inAliveTransition = false;
   private final Object ALIVE_TRANSITION_MONITOR = new Object();

   private int burstModeMaxEntries = -1;
   private long burstModeMaxBytes = -1L;

   /** async delivery is activated only when this flag is 'true'. Used to temporarly inhibit dispatch of messages */
   private boolean dispatcherActive = true;

   /**
    * @param msgQueue The message queue which i use (!!! TODO: this changes, we should pass it on every method where needed)
    * @param connectionStatusListener The implementation which listens on connectionState events (e.g. XmlBlasterAccess.java), or null
    * @param addrArr The addresses i shall connect to
    */
   public DispatchManager(Global glob, I_MsgErrorHandler failureListener,
                          I_MsgSecurityInterceptor securityInterceptor,
                          I_Queue msgQueue, I_ConnectionStatusListener connectionStatusListener,
                          AddressBase[] addrArr) throws XmlBlasterException {
      if (failureListener == null || msgQueue == null)
         throw new IllegalArgumentException("DispatchManager failureListener=" + failureListener + " msgQueue=" + msgQueue);

      this.ME = "DispatchManager-" + msgQueue.getStorageId().getId();
      this.glob = glob;
      this.log = glob.getLog("dispatch");

      if (log.TRACE) log.trace(ME, "Loading DispatchManager ...");

      this.msgQueue = msgQueue;
      this.failureListener = failureListener;
      this.securityInterceptor = securityInterceptor;
      this.dispatchConnectionsHandler = this.glob.createDispatchConnectionsHandler(this);
      this.connectionStatusListeners = new HashSet();
      if (connectionStatusListener != null) this.connectionStatusListeners.add(connectionStatusListener);
      
      initDispatcherActive(addrArr);

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
         this.msgInterceptor.addDispatchManager(this);
         if (log.TRACE) log.trace(ME, "Activated dispatcher plugin '" + this.typeVersion + "'");
      }

      this.msgQueue.addPutListener(this); // to get putPre() and putPost() events

      this.dispatchConnectionsHandler.initialize(addrArr);
   }

   public boolean isSyncMode() {
      return this.isSyncMode;
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

   /**
    * Reconfigure dispatcher with given properties. 
    *
    * Note that only a limited re-configuration is supported
    * @param addressArr The new configuration
    */
   public final void updateProperty(CallbackAddress[] addressArr) throws XmlBlasterException {
      initDispatcherActive(addressArr);
      this.dispatchConnectionsHandler.initialize(addressArr);
   }

   public void finalize() {
      removeBurstModeTimer();
      if (log.TRACE) log.trace(ME, "finalize - garbage collected");
   }

   public I_Queue getQueue() {
      return this.msgQueue;
   }

   /*
    * Register yourself if you want to be informed about the remote connection status. 
    * @param connectionStatusListener The implementation which listens on connectionState events (e.g. XmlBlasterAccess.java)
    */
   public void addConnectionStatusListener(I_ConnectionStatusListener connectionStatusListener) {
      this.connectionStatusListeners.add(connectionStatusListener);
   }

   public void removeConnectionStateListener(I_ConnectionStatusListener connectionStatusListener) {
      this.connectionStatusListeners.remove(connectionStatusListener);
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
   public DispatchConnectionsHandler getDispatchConnectionsHandler() {
      return this.dispatchConnectionsHandler;
   }

   /**
    * How many messages maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver in one bulk. 
    */
   public final int getBurstModeMaxEntries() {
      return this.burstModeMaxEntries;
   }

   /**
    * How many bytes maximum shall the callback thread take in one bulk out of the
    * callback queue and deliver in one bulk. 
    */
   public final long getBurstModeMaxBytes() {
      return this.burstModeMaxBytes;
   }

   /**
    * Call by DispatchConnectionsHandler on state transition
    * NOTE: toAlive is called initially when a protocol plugin is successfully loaded
    * but we don't know yet if it ever is able to connect
    */
   void toAlive(ConnectionStateEnum oldState) {
      
      if (log.CALL) log.call(ME, "Switch from " + oldState + " to ALIVE");

      // Remember the current collectTime
      AddressBase addr = this.dispatchConnectionsHandler.getAliveAddress();
      if (addr == null) {
         log.error(ME, "toAlive action has no alive address");
         return;
      }

      try {
         this.inAliveTransition = true;

         this.burstModeMaxEntries = addr.getBurstModeMaxEntries();
         this.burstModeMaxBytes = addr.getBurstModeMaxBytes();

         synchronized (this.ALIVE_TRANSITION_MONITOR) {
            // 1. We allow a client to intercept and for example destroy all entries in the queue
            if (this.connectionStatusListeners.size() > 0) {
               Iterator iter = this.connectionStatusListeners.iterator();
               while (iter.hasNext()) {
                  ((I_ConnectionStatusListener)iter.next()).toAlive(this, oldState);
               }
            }
            // 2. If a dispatch plugin is registered it may do its work
            if (this.msgInterceptor != null)
               this.msgInterceptor.toAlive(this, oldState);
         }
      }
      finally {
         this.inAliveTransition = false;
      }

      collectTime = addr.getCollectTime(); // burst mode if > 0L

      // 3. Deliver. Will be delayed if burst mode timer is activated, will switch to sync mode if necessary
      activateDispatchWorker();
   }

   /** Call by DispatchConnectionsHandler on state transition */
   void toPolling(ConnectionStateEnum oldState) {

      if (log.CALL) log.call(ME, "Switch from " + oldState + " to POLLING");
      switchToASyncMode();

      // 1. We allow a client to intercept and for example destroy all entries in the queue
      if (this.connectionStatusListeners.size() > 0) {
         Iterator iter = this.connectionStatusListeners.iterator();
         while (iter.hasNext()) {
            ((I_ConnectionStatusListener)iter.next()).toPolling(this, oldState);
         }
      }

      // 2. If a dispatch plugin is registered it may do its work
      if (this.msgInterceptor != null)
         this.msgInterceptor.toPolling(this, oldState);
   }

   /** Call by DispatchConnectionsHandler on state transition */
   public void toDead(ConnectionStateEnum oldState, XmlBlasterException ex) {
      if (log.CALL) log.call(ME, "Switch from " + oldState + " to DEAD");
      if (oldState == ConnectionStateEnum.DEAD) return;
      if (this.isShutdown) return;
      if (ex != null) {
         ex.changeErrorCode(ErrorCode.COMMUNICATION_NOCONNECTION_DEAD);
      }
      else {
         ex = new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME,
                  "Switch from " + oldState + " to DEAD, reason is not known");
      }
      
      // 1. We allow a client to intercept and for example destroy all entries in the queue
      if (this.connectionStatusListeners.size() > 0) {
         Iterator iter = this.connectionStatusListeners.iterator();
         while (iter.hasNext()) {
            ((I_ConnectionStatusListener)iter.next()).toDead(this, oldState, ex.getMessage());
         }
      }

      // 2. If a dispatch plugin is registered it may do its work
      if (this.msgInterceptor != null)
         this.msgInterceptor.toDead(this, oldState, ex.getMessage());

      if (oldState != ConnectionStateEnum.UNDEF)
         givingUpDelivery(ex);
   }

   private void givingUpDelivery(XmlBlasterException ex) {
      if (log.TRACE) log.trace(ME, "Entering givingUpDelivery(), state is " + this.dispatchConnectionsHandler.getState());
      removeBurstModeTimer();
      // The error handler flushed the queue and does error handling with them
      getMsgErrorHandler().handleError(new MsgErrorInfo(glob, (MsgQueueEntry)null, this, ex));
      shutdown();
   }
   
   /**
    * Called by DispatchWorker if an Exception occured in sync mode
    */
   void handleSyncWorkerException(ArrayList entryList, Throwable throwable) throws XmlBlasterException {

      if (log.CALL) log.call(ME, "Sync delivery failed connection state is " + this.dispatchConnectionsHandler.getState().toString() + ": " + throwable.toString());
      
      XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,ME,null,throwable);

      if (xmlBlasterException.isUser()) {
         // Exception from remote client from update(), pass it to error handler and carry on ...
         MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
         getMsgErrorHandler().handleErrorSync(new MsgErrorInfo(glob, entries, this, xmlBlasterException));
         return;
      }
      else if (xmlBlasterException.isCommunication()) {

         if (this.msgInterceptor != null && isPolling()) { // If we have a plugin it shall handle it
            try {
               entryList = this.msgInterceptor.handleNextMessages(this, entryList);
               if (entryList != null && entryList.size() > 0) {
                  MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                  getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, this, xmlBlasterException));
               }
            }
            catch (XmlBlasterException xmlBlasterException2) {
               internalError(xmlBlasterException2);
            }
            if (entryList != null && entryList.size() > 0) {
               MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
               getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, this, xmlBlasterException));
            }
            return;
         }

         // Exception from connection to remote client (e.g. from Corba layer)
         // DispatchManager handles this
         // Error handling in sync mode
         // 1. throwExceptionBackToPusher
         // 2. Switch to async mode and collect message (wait on better times)
         // 3. If we have serious problems (programming exceptions or isDead()) throw exception back
         // 4. Pass exception to an error handler plugin
         switchToASyncMode();

         // Simulate return values, and manipulate missing informations into entries ...
         I_QueueEntry[] entries = (I_QueueEntry[])entryList.toArray(new I_QueueEntry[entryList.size()]);
         getDispatchConnectionsHandler().createFakedReturnObjects(entries, Constants.STATE_OK, Constants.INFO_QUEUED);
         msgQueue.put(entries, I_Queue.IGNORE_PUT_INTERCEPTOR);

         if (log.TRACE) log.trace(ME, "Delivery failed, pushed " + entries.length + " entries into tail back queue");
      }
      else {
         if (log.TRACE) log.trace(ME, "Invocation failed: " + xmlBlasterException.getMessage());
         throw xmlBlasterException;
      }
   }

   /**
    * Called by DispatchWorker if an Exception occurred
    */
   void handleWorkerException(ArrayList entryList, Throwable throwable) {
      // Note: The DispatchManager is notified about connection problems directly by its DispatchConnectionsHandler
      //       we don't need to take care of ErrorCode.COMMUNICATION*
      if (log.CALL) log.call(ME, "Async delivery failed connection state is " + this.dispatchConnectionsHandler.getState().toString() + ": " + throwable.toString());
      //Thread.currentThread().dumpStack();
      if (entryList == null) {
         if (!this.isShutdown)
            log.warn(ME, "Didn't expect null entryList in handleWorkerException() for throwable " + throwable.getMessage() + toXml(""));
         return;
      }

      if (throwable instanceof XmlBlasterException) {
         XmlBlasterException ex = (XmlBlasterException)throwable;
         if (log.TRACE) log.trace(ME, "Invocation or callback failed: " + ex.getMessage());
         if (ex.isUser()) {
            // Exception from remote client from update(), pass it to error handler and carry on ...
            MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
            getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, this, ex));
         }
         else if (ex.isCommunication()) {

            if (this.msgInterceptor != null && isPolling()) { // If we have a plugin it shall handle it
               try {
                  entryList = this.msgInterceptor.handleNextMessages(this, entryList);
                  if (entryList != null && entryList.size() > 0) {
                     MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                     getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, this, ex));
                  }
               }
               catch (XmlBlasterException ex2) {
                  internalError(ex2);
               }
               if (entryList != null && entryList.size() > 0) {
                  MsgQueueEntry[] entries = (MsgQueueEntry[])entryList.toArray(new MsgQueueEntry[entryList.size()]);
                  getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entries, this, ex));
               }
            }

            // Exception from connection to remote client (e.g. from Corba layer)
            // DispatchManager handles this
         }
         else {
            //log.error(ME, "Callback failed: " + ex.toString());
            //ex.printStackTrace();
            internalError(ex);
         }
      }
      else {
         //log.error(ME, "Callback failed: " + throwable.toString());
         //throwable.printStackTrace();
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
         if (this.syncDispatchWorker == null) this.syncDispatchWorker = new DispatchWorker(glob, this);

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
         this.isSyncMode = false;
         activateDispatchWorker(); // just in case there are some messages pending in the queue
         log.info(ME, "Switched to asynchronous message delivery");
      }
   }

   /**
    * @see I_QueuePutListener#putPre(I_QueueEntry)
    */
   public boolean putPre(I_QueueEntry queueEntry) throws XmlBlasterException {
      //I_QueueEntry[] queueEntries = new I_QueueEntry[1];
      //queueEntries[0] = queueEntry;
      return putPre(new I_QueueEntry[] { queueEntry });
   }

   /**
    * @see #putPre(I_QueueEntry)
    * @see I_QueuePutListener#putPre(I_QueueEntry[])
    */
   public boolean putPre(I_QueueEntry[] queueEntries) throws XmlBlasterException {
      if (!this.isSyncMode) {
         if (this.inAliveTransition) {
            // Do not allow other threads to put messages to queue during transition to alive
            synchronized (ALIVE_TRANSITION_MONITOR) {
               // don't allow 
            }
         }
         return true; // Add entry to queue
      }
      
      if (log.TRACE) log.trace(ME, "putPre() - Got " + queueEntries.length + " QueueEntries to deliver synchronously ...");
      ArrayList entryList = new ArrayList(queueEntries.length);
      for (int ii=0; ii<queueEntries.length; ii++) {
         if (this.trySyncMode && !this.isSyncMode && queueEntries[ii] instanceof MsgQueueGetEntry) { // this.trySyncMode === isClientSide
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "You can't call get() in asynchronous mode (gets can't be queued because we don't know its return value)");
         }
         entryList.add(queueEntries[ii]);
      }
      this.syncDispatchWorker.run(entryList);
      return false;
   }

   /**
    * @see I_QueuePutListener#putPost(I_QueueEntry)
    */
   public void putPost(I_QueueEntry queueEntry) throws XmlBlasterException {
      //log.error(ME, "DEBUG ONLY: putPost() is not implemented");
      if (!this.isSyncMode) {
         if (this.dispatcherActive) notifyAboutNewEntry();
         if (((MsgQueueEntry)queueEntry).wantReturnObj()) {
            // Simulate return values, and manipulate missing informations into entries ...
            I_QueueEntry[] entries = new I_QueueEntry[] { queueEntry };
            getDispatchConnectionsHandler().createFakedReturnObjects(entries, Constants.STATE_OK, Constants.INFO_QUEUED);
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
         if (this.dispatcherActive) notifyAboutNewEntry();
         if (queueEntries.length > 0 && ((MsgQueueEntry)queueEntries[0]).wantReturnObj()) {
            // Simulate return values, and manipulate missing informations into entries ...
            getDispatchConnectionsHandler().createFakedReturnObjects(queueEntries, Constants.STATE_OK, Constants.INFO_QUEUED);
         }
      }
   }

   /**
    * Here we prepare messages which are coming directly from the queue.
    * <ol>
    *   <li>We eliminate destroyed messages</li>
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
      return prepareMsgsFromQueue(ME, this.log, this.msgQueue, entryList);
   }

   public static ArrayList prepareMsgsFromQueue(String logId, LogChannel log, I_Queue queue, ArrayList entryList) {
      // Remove all expired messages and do a shallow copy
      int size = entryList.size();
      ArrayList result = new ArrayList(size);
      for (int ii=0; ii<size; ii++) {
         MsgQueueEntry entry = (MsgQueueEntry)entryList.get(ii);
         // Take care to remove the filtered away messages from the queue as well
         //log.error(ME, "DEBUG ONLY: Analyze msg " + entry.toXml());
         if (entry.isDestroyed()) {
            log.info(logId, "Message " + entry.getLogId() + " is destroyed, ignoring it");
            if (log.TRACE) log.trace(logId, "Message " + entry.getLogId() + " is destroyed, ignoring it: " + entry.toXml());
            try {
               queue.removeRandom(entry); // Probably change to use [] for better performance
            }
            catch (Throwable e) {
               log.error(logId, "Internal error when removing expired message " + entry.getLogId() + " from queue, no recovery implemented, we continue: " + e.toString());
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
      //activateDispatchWorker();

      if (checkSending(true) == false)
         return;

      if (useBurstModeTimer() == true)
         return;

      startWorkerThread(false);
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
   private void activateDispatchWorker() {

      if (checkSending(false) == false)
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

      if (log.TRACE) log.trace(ME, "Executing useBurstModeTimer() collectTime=" + collectTime + " dispatchWorkerIsActive=" + dispatchWorkerIsActive);
      synchronized (this) {
         if (this.isShutdown) return false;
         if (this.timerKey == null) {
            if (log.TRACE) log.trace(ME, "Starting burstMode timer with " + collectTime + " msec");
            this.timerKey = this.glob.getBurstModeTimer().addTimeoutListener(this, collectTime, null);
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
            this.glob.getBurstModeTimer().removeTimeoutListener(timerKey);
            this.timerKey = null;
         }
      }
   }
   
   /**
    * @param fromTimeout for logging only
    */
   private void startWorkerThread(boolean fromTimeout) {
      if (this.dispatchWorkerIsActive == false) {
         //if (log.TRACE) log.trace(ME, "Doing startWorkerThread("+fromTimeout+")");
         synchronized (this) {
            //if (log.TRACE) log.trace(ME, "Doing startWorkerThread(isShutdown="+this.isShutdown+", dispatchWorkerIsActive="+this.dispatchWorkerIsActive+") inside sync");
            if (this.isShutdown) {
               if (log.TRACE) log.trace(ME, "startWorkerThread() failed, we are shutdown: " + toXml(""));
               return;
            }
            if (this.dispatchWorkerIsActive == false) { // send message directly
               this.dispatchWorkerIsActive = true;
               this.notifyCounter = 0;
               try {
                  this.glob.getDispatchWorkerPool().execute(new DispatchWorker(glob, this));
               }
               catch (Throwable e) {
                  this.dispatchWorkerIsActive = false;
                  log.error(ME, "Unexpected error occurred: " + e.toString());
                  e.printStackTrace();
               }
            }
         }
         return;
      }

      if (fromTimeout) {
         if (log.TRACE) log.trace(ME, "Burst mode timeout occurred, last callback worker thread is not finished - we do nothing (the worker thread will give us a kick)");
      }
      else {
         if (log.TRACE) log.trace(ME, "Last callback worker thread is not finished - we do nothing (the worker thread will give us a kick)");
      }
   }

   public boolean isDead() {
      return this.dispatchConnectionsHandler.isDead();
   }

   public boolean isPolling() {
      return this.dispatchConnectionsHandler.isPolling();
   }

   /**
    * @param isPublisherThread We take care that the publisher thread, coming through putPost()
    *        does never too much work to return fast enough and avoid possible dead locks.
    * @return true is status is OK and we can try to send a message
    */
   private boolean checkSending(boolean isPublisherThread) {
      if (this.isShutdown) {
         if (log.TRACE) log.trace(ME, "The dispatcher is shutdown, can't activate callback worker thread" + toXml(""));
         return false; // assert
      }

      if (this.isSyncMode) {
         return false;
      }

      if (!this.dispatcherActive) {
         return false;
      }

      if (msgQueue.isShutdown() && !isPublisherThread) { // assert
         if (log.TRACE) log.trace(ME, "The queue is shutdown, can't activate callback worker thread.");
         // e.g. client has disconnected on the mean time.
         //Thread.currentThread().dumpStack();
         shutdown();
         return false;
      }

      if (this.dispatchConnectionsHandler.isUndef()) {
         if (log.TRACE) log.trace(ME, "Not connected yet, state is UNDEF");
         return false;
      }

      if (this.dispatchConnectionsHandler.isDead() && !isPublisherThread) {
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

      if (this.dispatchConnectionsHandler.isPolling()) {
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
      if (log.TRACE) log.trace(ME, "Burst mode timeout occurred, queue entries=" + msgQueue.getNumOfEntries() + ", starting callback worker thread ...");
      startWorkerThread(true);
   }


   /**
    * @return The interceptor plugin if available, otherwise null
    */
   public I_MsgDispatchInterceptor getMsgDispatchInterceptor() {
      return this.msgInterceptor;
   }

   /**
    * Set new callback addresses, typically after a session login/logout
    */
   public void setAddresses(AddressBase[] addr) throws XmlBlasterException {
      this.dispatchConnectionsHandler.initialize(addr);
   }

   /**
    * Switch on/off the sending of messages. 
    */
   private void initDispatcherActive(AddressBase[] addrArr) {
      if (addrArr != null) {
         for (int ii=0; ii<addrArr.length; ii++) { // TODO: How to handle setting of multiple addresses??
            this.dispatcherActive = addrArr[ii].isDispatcherActive();
         }
      }
   }

   /**
    * The worker notifies us that it is finished, if messages are available
    * it is triggered again.
    */
   void setDispatchWorkerIsActive(boolean val) {
      this.dispatchWorkerIsActive = val;
      if (val == false) {
         if (this.isShutdown) {
            if (log.TRACE) log.trace(ME, "setDispatchWorkerIsActive(" + val + ") failed, we are shutdown: " + toXml(""));
            return;
         }

         if (msgQueue.getNumOfEntries() > 0) {
            if (log.TRACE) log.trace(ME, "Finished callback job. Giving a kick to send the remaining " + msgQueue.getNumOfEntries() + " messages.");
            try {
               activateDispatchWorker();
            }
            catch(Throwable e) {
               log.error(ME, e.toString()); e.printStackTrace(); // Assure the queue is flushed with another worker
            }
         }
         else {
            if (this.trySyncMode && !this.isSyncMode) {
               switchToSyncMode();
            }
         }
      }
   }

   /**
    * Called locally and from TopicHandler when internal error (Throwable) occurred to avoid infinite looping
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
   public DispatchStatistic getDispatchStatistic() {
      return this.dispatchConnectionsHandler.getDispatchStatistic();
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

         this.msgQueue.removePutListener(this);

         // remove all ConnectionStatusListeners
         this.connectionStatusListeners.clear();

         removeBurstModeTimer();

         // NOTE: We would need to remove the 'final' qualifier to be able to set to null

         if (this.msgInterceptor != null) {
            try {
               this.msgInterceptor.shutdown(this);
            }
            catch (XmlBlasterException e) {
               log.warn(ME, "Ignoring problems during shutdown of plugin: " + e.getMessage());
            }
            //this.msgInterceptor = null;
         }
         if (this.dispatchConnectionsHandler != null) {
            this.dispatchConnectionsHandler.shutdown();
            //this.dispatchConnectionsHandler = null;
         }
         removeBurstModeTimer();
         //this.msgQueue = null;
         //this.failureListener = null;
         //this.securityInterceptor = null;

         //if (this.dispatchWorkerPool != null) {
         //   this.dispatchWorkerPool.shutdown(); NO: not here, is the scope and duty of Global
         //   this.dispatchWorkerPool = null;
         //}

         if (this.syncDispatchWorker != null)
            this.syncDispatchWorker.shutdown();
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

      sb.append(offset).append("<DispatchManager id='").append(getId());
      if (this.msgQueue != null)
         sb.append("' numEntries='").append(this.msgQueue.getNumOfEntries());
      sb.append("' isShutdown='").append(this.isShutdown).append("'>");
      sb.append(this.dispatchConnectionsHandler.toXml(extraOffset+Constants.INDENT));
      sb.append(offset).append(" <dispatchWorkerIsActive>").append(dispatchWorkerIsActive).append("</dispatchWorkerIsActive>");
      sb.append(offset).append("</DispatchManager>");

      return sb.toString();
   }
   
   /**
    * Inhibits/activates the delivery of asynchronous dispatches of messages.
    * @param dispatcherActive
    */
   public void setDispatcherActive(boolean dispatcherActive) {
      this.dispatcherActive = dispatcherActive;
      if (this.dispatcherActive) notifyAboutNewEntry();
   }
   
   /**
    * 
    * @return true if the dispacher is currently activated, i.e. if it is 
    * able to deliver asynchronousy messages from the callback queue.
    */
   public boolean isDispatcherActive() {
      return this.dispatcherActive;
   }
   
   
}
