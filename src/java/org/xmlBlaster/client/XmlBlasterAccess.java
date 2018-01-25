/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.client.dispatch.ClientDispatchManager;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.jms.XBConnectionMetaData;
import org.xmlBlaster.util.FileDumper;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_ReplaceContent;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.I_TimeoutManager;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.checkpoint.I_Checkpoint;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchConnection;
import org.xmlBlaster.util.dispatch.DispatchStatistic;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.dispatch.I_PostSendListener;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.DisconnectQosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * This is the default implementation of the java client side remote access to xmlBlaster.
 * <p>
 * It hides a client side queue, the client side dispatcher framework for polling
 * or pinging the server and some more features.
 * </p>
 * <p>
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended.
 * </p>
 */
public /*final*/ class XmlBlasterAccess extends AbstractCallbackExtended
                   implements I_XmlBlasterAccess, I_ConnectionStatusListener, I_PostSendListener, XmlBlasterAccessMBean
{
   private static Logger log = Logger.getLogger(XmlBlasterAccess.class.getName());
   private String ME = "XmlBlasterAccess";
   private ContextNode contextNode;
   /**
    * The cluster node id (name) to which we want to connect, needed for nicer logging, typically null
    * Can be set manually from outside before connect
    */
   private String serverNodeId = null;
   private ConnectQos connectQos;
   /** The return from connect() */
   private ConnectReturnQos connectReturnQos;
   private long jmxPublicSessionId;
   /** Client side queue during connection failure */
   private I_Queue clientQueue;
   /** The dispatcher framework **/
   private I_DispatchManager dispatchManager;
   /** Statistic about send/received messages, can be null if there is a DispatchManager around */
   private volatile DispatchStatistic statistic;
   /** The object handling message delivery problems */
   private I_MsgErrorHandler msgErrorHandler;
   /** Client side helper classes to load the authentication xml string */
   private I_ClientPlugin secPlgn;
   /** The callback server */
   private I_CallbackServer cbServer;
   /** Handles the registered callback interfaces for given subscriptions. */
   private final UpdateDispatcher updateDispatcher;
   /** Used to callback the clients default update() method (as given on connect()) */
   private I_Callback updateListener;
   /** Is not null if the client wishes to be notified about connection state changes in fail safe operation */
   private I_ConnectionStateListener connectionListener;
   private I_PostSendListener postSendListener;
   /** Allow to cache updated messages for simulated synchronous access with get().
    * Do behind a get() a subscribe to allow cached synchronous get() access */
   private SynchronousCache synchronousCache;
   private boolean disconnectInProgress;
   private boolean connectInProgress;
   private String[] checkPointContext;

   /** this I_XmlBlasterAccess is valid until a 'leaveServer' invocation is done.*/
   private boolean isValid = true;

   private boolean firstWarn = true;

   private Timestamp sessionRefreshTimeoutHandle;
   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;
   /** First call to connect() in millis */
   private long startupTime;

   StreamingCallback streamingCb;

   private String storageIdPrefix;

   private FileDumper fileDumper;

   private boolean shutdown = false;
   
   private Object userObject;
   
   private CountDownLatch asyncGetLatch;
   
   private MsgUnit[] asyncGetMsgUnits;

   private Object asyncGetMonitor = new Object();
   
   private XmlBlasterException toDeadXmlBlasterException;
   
   /**
    * Create an xmlBlaster accessor.
    * Please don't create directly but use the factory instead:
    * <pre>
    *   import org.xmlBlaster.util.Global;
    *   ...
    *   final Global glob = new Global(args);
    *   final I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
    * </pre>
    * @param glob Your environment handle or null to use the default Global.instance()
    *        You must use a cloned Global for each XmlBlasterAccess created.
    *        engine.Global is not allowed here, only util.Global is supported
    * @exception IllegalArgumentException If engine.Global is used as parameter
    */
   public XmlBlasterAccess(Global glob) {
      super((glob==null) ? Global.instance() : glob);
      //if (glob.wantsHelp()) {
      //   usage();
      //}
      if (super.glob.getNodeId() != null) {
         // it is a engine.Global!
         throw new IllegalArgumentException("XmlBlasterAccess can't be created with a engine.Global, please clone a org.xmlBlaster.util.Global to create me");
      }
      this.updateDispatcher = new UpdateDispatcher(super.glob);
   }

   /**
    * Create an xmlBlaster accessor.
    * Please don't create directly but use the factory instead:
    * <pre>
    *   final Global glob = new Global(args);
    *   final I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
    * </pre>
    * @param args Your command line arguments
    */
   public XmlBlasterAccess(String[] args) {
      super(new Global(args, true, false));
      this.updateDispatcher = new UpdateDispatcher(super.glob);
   }

   /**
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#registerConnectionListener(I_ConnectionStateListener)
    */
   public synchronized void registerConnectionListener(I_ConnectionStateListener connectionListener) {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Initializing registering connectionListener");
      this.connectionListener = connectionListener;
   }

   /**
    * Register a listener to get notifications when a messages is successfully send from
    * the client side tail back queue.
    * Max one can be registered, any old one will be overwritten
    * @param postSendListener The postSendListener to set.
    * @return the old listener or null if no previous was registered
    */
   public final I_PostSendListener registerPostSendListener(I_PostSendListener postSendListener) {
      I_PostSendListener old = this.postSendListener;
      this.postSendListener = postSendListener;
      return old;
   }

   /**
    * Called after a messages is send from the client side queue, but not for oneway messages.
    * Enforced by I_PostSendListener
    * @param msgQueueEntry, includes the returned QoS (e.g. PublisReturnQos)
    */
   public final void postSend(MsgQueueEntry[] entries) {
      for (int i=0; i<entries.length; i++) {
         MsgQueueEntry msgQueueEntry = entries[i];
         if (msgQueueEntry.getMethodName() == MethodName.CONNECT) {
            this.connectReturnQos = (ConnectReturnQos)msgQueueEntry.getReturnObj();
            if (this.connectReturnQos != null) {
               setContextNodeId(this.connectReturnQos.getServerInstanceId());
               // break; Loop to the latest if any
            }
            else {
               //log.warning("Expected connectReturnQos for " + msgQueueEntry.toXml() + " " + Global.getStackTraceAsString(null));
               if (log.isLoggable(Level.FINE)) log.fine("Expected connectReturnQos for " + msgQueueEntry.toXml() + " " + Global.getStackTraceAsString(null));
            }
         }
         else if (msgQueueEntry.getMethodName().equals(MethodName.GET)) {
            if (this.asyncGetLatch != null) {
               try {
            	   this.asyncGetMsgUnits = (MsgUnit[])msgQueueEntry.getReturnObj();
                   //this.asyncGetMsgUnits = new MsgUnit[]{ msgQueueEntry.getMsgUnit() };
	           } catch (Throwable e) {
	               log.severe("get call with unexpected error, response is lost: " + e.toString() + "\n" + msgQueueEntry.toXml());
               } 
               this.asyncGetLatch.countDown();
            }
            else {
               log.severe("get call with nobody interesed in, response is lost: " + msgQueueEntry.toXml());
            }
         }
      }
      I_PostSendListener l = this.postSendListener;
      if (l != null) {
         try {
            l.postSend(entries);
         }
         catch (Throwable e) {
            e.printStackTrace();
         }
      }
   }
   
   public boolean sendingFailed(MsgQueueEntry[] entries, XmlBlasterException exception) {
      I_PostSendListener l = this.postSendListener;
      try {
         if (l == null) {
            for (int i=0; i<entries.length; i++) {
               MsgUnit msgUnit = entries[i].getMsgUnit();
               if (msgUnit != null) {
                  String fn = this.getFileDumper().dumpMessage(msgUnit.getKeyData(), msgUnit.getContent(),
                        msgUnit.getQosData());
                  log.severe("Async sending of message failed for message " + msgUnit.getKeyOid() + ", is dumped to "
                        + fn + ": " + exception.getMessage());
               } else {
                  log.severe("Async sending of message failed: " + entries[i].toXml() + ": " + exception.getMessage());
               }
            }
         }
         else {
            return l.sendingFailed(entries, exception);
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
         for (int i=0; i<entries.length; i++)
            log.severe("Async sending of message failed for message " + entries[i].toXml() +"\nreason is: " + exception.getMessage());
      }
      return false;
   }
   
   public FileDumper getFileDumper() throws XmlBlasterException {
      if (this.fileDumper == null) {
         synchronized (this) {
            if (this.fileDumper == null) {
               this.fileDumper = new FileDumper(this.glob);
            }
         }
      }
      return this.fileDumper;
   }


   /**
    */
   public SynchronousCache createSynchronousCache(int size) {
      if (this.synchronousCache != null)
         return this.synchronousCache; // Is initialized already
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Initializing synchronous cache: size=" + size);
      this.synchronousCache = new SynchronousCache(glob, size);
      log.info(getLogId()+"SynchronousCache has been initialized with size="+size);
      return this.synchronousCache;
   }

   public void setClientErrorHandler(I_MsgErrorHandler msgErrorHandler) {
      this.msgErrorHandler = msgErrorHandler;
   }

   public String getConnectionQueueId() {
      if (this.clientQueue != null) {
         return this.clientQueue.getStorageId().toString();
      }
      return "";
   }

   /**
    * The unique name of this session instance.
    * @return Never null, for example "/xmlBlaster/node/heron/client/joe/session/-2"
    */
   public final ContextNode getContextNode() {
      return this.contextNode;
   }

   public boolean forcePollingForTesting() {
      if (!isAlive())
         return false;
      DispatchConnection dcon = this.dispatchManager.getDispatchConnectionsHandler().getAliveDispatchConnection();
      if (dcon == null)
         return false;
      XmlBlasterException e = new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION,
            "forcePollingForTesting", "Forcing POLLING");
      try {
         dcon.handleTransition(true, e);
      } catch (XmlBlasterException e1) {
         e1.printStackTrace();
         return false;
      }
      return true;
   }

   
   public ConnectReturnQos connect(ConnectQos qos, I_StreamingCallback streamingUpdateListener, boolean withQueue) throws XmlBlasterException {
      if (streamingUpdateListener == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, "connect", "the streamingUpdateListener is null, you must provide one");
      this.streamingCb = new StreamingCallback(this.glob, streamingUpdateListener, 0, 0, withQueue);
      if (withQueue)
         registerConnectionListener(this.streamingCb);
      return connect(qos, this.streamingCb);
   }
   
   /**
    * The storageId must remain the same after a client restart
    * 
    * @param relating
    *           xbType like Constants.RELATING_CLIENT
    * @return
    */
   public StorageId createStorageId(String relating) {
      StorageId storageId = null;
      if (getStorageIdStr() != null && getStorageIdStr().length() > 0) {
         // client code forces a named client side storageId -
         // dangerous if the name conflicts with server name in same DB
         storageId = new StorageId(glob, serverNodeId, relating, getStorageIdStr());
      } else {
         if (getPublicSessionId() == 0) {
            // having no public sessionId we need to generate a unique
            // queue name
            storageId = new StorageId(glob, serverNodeId, relating, getId() + System.currentTimeMillis()
                  + Global.getCounter());
         } else {
            SessionName ses = getSessionName();
            if (ses != null)
               storageId = new StorageId(glob, serverNodeId, relating, ses);
            else
               storageId = new StorageId(glob, serverNodeId, relating, getId() + System.currentTimeMillis()
                     + Global.getCounter());
         }
      }
      return storageId;
   }

   /**
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#connect(ConnectQos, I_Callback)
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback updateListener) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "connect");

      I_DispatchManager dispatchManagerStale = null;
      synchronized (this) {

         if (this.startupTime == 0) {
            this.startupTime = System.currentTimeMillis();
         }

         if (isConnected() || this.connectInProgress) {
            String text = "connect() rejected, you are connected already, please check your code";
            throw new XmlBlasterException(glob, ErrorCode.USER_CONNECT_MULTIPLE, ME, text);
         }

         this.connectInProgress = true;

         try {
            this.connectQos = (qos==null) ? new ConnectQos(glob) : qos;

            ClientProperty tmp = this.connectQos.getClientProperty(Constants.UPDATE_BULK_ACK);
            if (tmp != null) {
               if (tmp.getBooleanValue()) {
                  log.info("Setting the flag '" + Constants.UPDATE_BULK_ACK + "' to 'true' since specified in ConnectQos");
                  this.updateBulkAck = true;
               }
            }


            // We need to set a unique ID for this client so that global.getId() is unique
            // which is used e.g. in the JDBC plugin
            SessionName sn = getSessionName();
            if (sn != null) {
               if (sn.isPubSessionIdUser()) {
                  this.glob.setId(sn.toString());
               }
               else {
                  this.glob.setId(sn.toString() + System.currentTimeMillis()); // Not secure if two clients start simultaneously
               }
            }
            else {
               this.glob.setId(getLoginName() + System.currentTimeMillis()); // Not secure if two clients start simultaneously
            }
            this.glob.resetInstanceId();
            this.connectQos.getData().setInstanceId(this.glob.getInstanceId());

            if (connectQos.getData().getGlobal().isServerSide()) {
               String text = "Your ConnectQos.getData() contains a ServerScope instead of a Global instance, this is not allowed";
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, text);
            }

            this.updateListener = updateListener;

            // TODO: This is done by ConnectQos already, isn't it?
            initSecuritySettings(this.connectQos.getData().getClientPluginType(),
                                 this.connectQos.getData().getClientPluginVersion());

            this.ME = "XmlBlasterAccess-" + getId();
            setContextNodeId(getServerNodeId());

            try {
               ClientQueueProperty prop = this.connectQos.getClientQueueProperty();
               StorageId storageId = createStorageId(Constants.RELATING_CLIENT);
               if (this.clientQueue != null) {
            	   log.severe(getLogId()+": clientQueue exists already " + this.clientQueue.getStorageId());
            	   Thread.dumpStack();
            	   if (false) { // was never in use, just to remember the decision:
            		   // is probably dead lock prone when calling back to user code
              	       try {
               		     // Persistent entries will NOT be deleted.
            	         this.clientQueue.shutdown();
            	       }
            	       catch (Throwable e) {
            		      e.printStackTrace();
            	       }
            	   }
               }
               this.clientQueue = glob.getQueuePluginManager().getPlugin(prop.getType(), prop.getVersion(), storageId,
                                                      this.connectQos.getClientQueueProperty());
               if (this.clientQueue == null) {
                  String text = "The client queue plugin is not found with this configuration, please check your connect QoS: " + prop.toXml();
                  throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, text);
               }

               if (this.msgErrorHandler == null) {
                  this.msgErrorHandler = new ClientErrorHandler(glob, this);
               }

               boolean forceCbAddressCreation = (updateListener != null);
               if (this.dispatchManager != null) {
            	   dispatchManagerStale = this.dispatchManager;
               }
               this.dispatchManager = new ClientDispatchManager(glob, this.msgErrorHandler,
                                       getSecurityPlugin(), this.clientQueue, this,
                                       this.connectQos.getAddresses(forceCbAddressCreation), sn);
               // the above can call toDead() and the client may have called shutdown(): this.connectQos == null again
               if (this.dispatchManager.isDead())
                   throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION_DEAD, ME, "connect call failed, your toDead() code did shutdown?");

               getDispatchStatistic(); // Force creation of dispatchStatistic as this syncs on 'this' and could deadlock if don later from a update()

               this.dispatchManager.getDispatchConnectionsHandler().registerPostSendListener(this);

               if (isTrySyncMode()) {
            	   if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Switching to synchronous delivery mode ...");
            	   this.dispatchManager.trySyncMode(true);
               }

               if (this.updateListener != null) { // Start a default callback server using same protocol
                  createDefaultCbServer();
               }

               if (this.connectQos.doSendConnect()) {
                  // Try to connect to xmlBlaster ...
                  sendConnectQos();
               }
               else {
                  log.info(getLogId()+"Initialized client library, but no connect() is send to xmlBlaster, a delegate should do any subscribe if required");
               }
            }
            catch (XmlBlasterException e) {
               if (isConnected()) disconnect((DisconnectQos)null);
               throw e;
            }
            catch (Throwable e) {
               if (isConnected()) disconnect((DisconnectQos)null);
               throw XmlBlasterException.convert(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Connection failed", e);
            }
         }
         finally {
            this.connectInProgress = false;
         }
      } // synchronized
      
      if (dispatchManagerStale != null) {
          try {
             // Michele was scared to do it inside synchronized
             dispatchManagerStale.shutdown();
          }
          catch (Throwable e) {
             e.printStackTrace();
          }
      }

      if (this.connectQos != null && this.connectQos.getRefreshSession()) {
         startSessionRefresher();
      }

      if (isAlive()) {
         if (this.connectionListener != null) {
            this.connectionListener.reachedAlive(ConnectionStateEnum.UNDEF, this);
         }
         if (this.connectQos != null && this.connectQos.getAddress() != null)
        	 log.info(glob.getReleaseId() + ": Successful " + this.connectQos.getAddress().getType() + " login as " + getId());

         I_Queue queue = this.clientQueue;
         if (queue == null) {
             if (isConnected()) disconnect((DisconnectQos)null);
             throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Connection failed, queue==null");
         }
         if (queue.getNumOfEntries() > 0) {
            long num = queue.getNumOfEntries();
            log.info(getLogId()+"We have " + num + " client side queued tail back messages");
            this.dispatchManager.switchToASyncMode();
            while (queue.getNumOfEntries() > 0) {
               try { Thread.sleep(20L); } catch( InterruptedException i) {}
            }
            log.info((num-queue.getNumOfEntries()) + " client side queued tail back messages sent");
            if (isTrySyncMode())
            	this.dispatchManager.switchToSyncMode();
         }
         else {
            if (this.connectionListener != null) {
               if (isTrySyncMode()) {
                 this.connectionListener.reachedAliveSync(ConnectionStateEnum.ALIVE, this);
               }
            }
         }
      }
      else {
         if (this.connectionListener != null) {
            this.connectionListener.reachedPolling(ConnectionStateEnum.UNDEF, this);
         }
         log.info(glob.getReleaseId() + ": Login request as " + getId() + " is queued");
      }

      if (this.connectReturnQos != null) {
         setContextNodeId(this.connectReturnQos.getServerInstanceId());
      }

      return this.connectReturnQos; // new ConnectReturnQos(glob, "");
   }
   
   /**
    * @return defaults to true
    */
   private boolean isTrySyncMode() {
	   ConnectQos qos = this.connectQos;
	   if (qos != null)
		   return qos.isTrySyncMode();
	   return true;
   }

   /**
    * Sends the current connectQos to xmlBlaster and stores the connectReturnQos.
    * @throws XmlBlasterException
    */
   private void sendConnectQos() throws XmlBlasterException {
      MsgQueueConnectEntry entry = new MsgQueueConnectEntry(this.glob, this.clientQueue.getStorageId(), this.connectQos.getData());
      // Try to connect to xmlBlaster ...
      this.connectReturnQos = (ConnectReturnQos)queueMessage(entry);
      this.connectReturnQos.getData().setInitialConnectionState(this.dispatchManager.getDispatchConnectionsHandler().getState());
   }

   public boolean isConnected() {
      if (this.dispatchManager != null) {
         return this.connectReturnQos != null && !this.dispatchManager.getDispatchConnectionsHandler().isDead();
      }
      return this.connectReturnQos != null;
   }

   private void startSessionRefresher() {
      if (this.connectQos == null) return;
      long sessionTimeout = this.connectQos.getSessionQos().getSessionTimeout();
      final long MIN = 2000L; // Sessions which live less than 2 seconds are not supported
      if (sessionTimeout >= MIN) {
         long gap = (sessionTimeout < 60*1000L) ? sessionTimeout/2 : sessionTimeout-30*1000L;
         final long refreshTimeout = sessionTimeout - gap;
         final I_TimeoutManager timeout = this.glob.getPingTimer();
         this.sessionRefreshTimeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  if (isAlive()) {
                     if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Refreshing session to not expire");
                     try {
                        refreshSession();
                     }
                     catch (XmlBlasterException e) {
                        log.warning(getLogId()+"Can't refresh the login session '" + getId() + "': " + e.toString());
                     }
                  }
                  else {
                     if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Can't refresh session as we have no connection");
                  }
                  try {
                     sessionRefreshTimeoutHandle = timeout.addOrRefreshTimeoutListener(this, refreshTimeout, null, sessionRefreshTimeoutHandle) ;
                  }
                  catch (XmlBlasterException e) {
                     log.warning(getLogId()+"Can't refresh the login session '" + getId() + "': " + e.toString());
                  }
               }
            },
            refreshTimeout, null);
      }
      else {
         log.warning(getLogId()+"Auto-refreshing session is not supported for session timeouts smaller " + MIN + " seconds");

      }
   }


   /**
    * @see I_XmlBlasterAccess#refreshSession()
    */
   public void refreshSession() throws XmlBlasterException {
      GetKey gk = new GetKey(glob, "__refresh");
      GetQos gq = new GetQos(glob);
      get(gk, gq);
   }

   /**
    * Extracts address data from ConnectQos (or adds default if missing)
    * and instantiate a callback server as specified in ConnectQos
    */
   private void createDefaultCbServer() throws XmlBlasterException {
      CbQueueProperty prop = connectQos.getSessionCbQueueProperty(); // Creates a default property for us if none is available
      CallbackAddress addr = prop.getCurrentCallbackAddress(); // may return null
      if (addr == null)
         addr = new CallbackAddress(glob);

      this.cbServer = initCbServer(getLoginName(), addr);

      addr.setType(this.cbServer.getCbProtocol());
      addr.setRawAddress(this.cbServer.getCbAddress());
      //addr.setVersion(this.cbServer.getVersion());
      //addr.setSecretSessionId(cbSessionId);
      prop.setCallbackAddress(addr);

      log.info(getLogId()+"Callback settings: " + prop.getSettings());
   }

   /**
    * @see I_XmlBlasterAccess#initCbServer(String, CallbackAddress)
    */
   public I_CallbackServer initCbServer(String loginName, CallbackAddress callbackAddress) throws XmlBlasterException {
      if (callbackAddress == null)
         callbackAddress = new CallbackAddress(glob);
      callbackAddress.setSessionName(this.getSessionName());
      if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Using 'client.cbProtocol=" + callbackAddress.getType() + "' to be used by " + getServerNodeId() + ", trying to create the callback server ...");
      I_CallbackServer server = glob.getCbServerPluginManager().getPlugin(callbackAddress.getType(), callbackAddress.getVersion());
      server.initialize(this.glob, loginName, callbackAddress, this);
      return server;
   }

   /**
    * Initializes the little client helper framework for authentication.
    * <p />
    * The first goal is a proper loginQoS xml string for authentication.
    * <p />
    * The second goal is to intercept the messages for encryption (or whatever the
    * plugin supports).
    * <p />
    * See xmlBlaster.properties, for example:
    * <pre>
    *   Security.Client.DefaultPlugin=gui,1.0
    *   Security.Client.Plugin[gui][1.0]=org.xmlBlaster.authentication.plugins.gui.ClientSecurityHelper
    * </pre>
    */
   private void initSecuritySettings(String secMechanism, String secVersion) {
      PluginLoader secPlgnMgr = glob.getClientSecurityPluginLoader();
      try {
         this.secPlgn = secPlgnMgr.getClientPlugin(secMechanism, secVersion);
         if (secMechanism != null)  // to avoid double logging for login()
            log.info(getLogId()+"Loaded security plugin=" + secMechanism + " version=" + secVersion);
      }
      catch (XmlBlasterException e) {
         log.severe(getLogId()+"Security plugin '" + secMechanism + "/" + secVersion +
                       "' initialization failed. Reason: "+e.getMessage());
         this.secPlgn = null;
      }
   }

   public I_ClientPlugin getSecurityPlugin() {
      return this.secPlgn;
   }

   /**
    * @see org.xmlBlaster.client.XmlBlasterAccessMBean#disconnect(String)
    */
   public String disconnect(String disconnectQos) {
	   DisconnectQosData dqd = new DisconnectQosData(this.glob, null, disconnectQos);
	   boolean success = disconnect(new DisconnectQos(this.glob, dqd));
	   return "Disconnect called, success=" + success;
   }

   /**
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#disconnect(DisconnectQos)
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   public synchronized boolean disconnect(DisconnectQos disconnectQos) {
      if (!this.isValid) return false;
      // Relaxed check to allow shutdown of database without successful connection
      if (this.connectQos == null /*!isConnected()*/) {
         log.warning(getLogId()+"You called disconnect() but you are are not logged in, we ignore it.");
         if (glob != null)
        	 glob.shutdown();
         //shutdown(disconnectQos);
         return false;
      }

      if (disconnectQos == null)
         disconnectQos = new DisconnectQos(glob);

      if (!disconnectQos.getClearClientQueueProp().isModified()) {
         boolean clearClientQueue = true;
         if (this.connectQos != null) {
            if (this.connectQos.getSessionName().isPubSessionIdUser())
               clearClientQueue = false;  // Keep tail back messages
         }
         disconnectQos.clearClientQueue(clearClientQueue);
      }

      return shutdown(disconnectQos);
   }

   /**
    * @see org.xmlBlaster.client.XmlBlasterAccessMBean#leaveServer()
    */
   public String leaveServer() {
	   leaveServer(null);
	   return "Clientlibrary is shutdown";
   }

   /**
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#leaveServer(Map)
    */
   public void leaveServer(Map map) {
      if (!this.isValid) return;
      synchronized(this) {
         this.isValid = false;
         DisconnectQos disconnectQos = new DisconnectQos(glob);
         disconnectQos.clearClientQueue(false);
         disconnectQos.clearSessions(false);
         disconnectQos.deleteSubjectQueue(false);
         disconnectQos.setLeaveServer(true);
         disconnectQos.shutdownCbServer(true);
         disconnectQos.shutdownDispatcher(true);
         shutdown(disconnectQos);
      }
   }

   private synchronized boolean shutdown(DisconnectQos disconnectQos) {
      if (this.disconnectInProgress) {
         log.warning(getLogId()+"Calling disconnect again is ignored, you are in shutdown progress already");
         return false;
      }

      this.disconnectInProgress = true;

      this.glob.unregisterMBean(this.mbeanHandle);

      if (disconnectQos == null)
         disconnectQos = new DisconnectQos(glob);

      if (isConnected()) {

         if (this.clientQueue != null) {
            long remainingEntries = this.clientQueue.getNumOfEntries();
            if (remainingEntries > 0) {
               if (disconnectQos.clearClientQueue())
                  log.warning(getLogId()+"You called disconnect(). Please note that there are " + remainingEntries +
                               " unsent invocations/messages in the queue which are discarded now.");
               else
                  log.info(getLogId()+"You called disconnect(). Please note that there are " + remainingEntries +
                               " unsent invocations/messages in the queue which are sent on next connect of the same client with the same public session ID.");
            }
         }

         if (!disconnectQos.isLeaveServer()) {
            String[] subscriptionIdArr = this.updateDispatcher.getSubscriptionIds();
            for (int ii=0; ii<subscriptionIdArr.length; ii++) {
               String subscriptionId = subscriptionIdArr[ii];
               UnSubscribeKey key = new UnSubscribeKey(glob, subscriptionId);
               try {
                  unSubscribe(key, null);
               }
               catch(XmlBlasterException e) {
                  if (e.isCommunication()) {
                     break;
                  }
                  log.warning(getLogId()+"Couldn't unsubscribe '" + subscriptionId + "' : " + e.getMessage());
               }
            }
         }

         // Now send the disconnect() to the server ...
         if (!disconnectQos.isLeaveServer() && this.clientQueue != null) {
            try {
               MsgQueueDisconnectEntry entry = new MsgQueueDisconnectEntry(this.glob, this.clientQueue.getStorageId(), disconnectQos);
               queueMessage(entry);  // disconnects are always transient
               log.info(getLogId()+"Successful disconnect from " + getServerNodeId());
            } catch(Throwable e) {
               e.printStackTrace();
               log.warning(e.toString());
            }
         }
      }

      if (this.synchronousCache != null) {
         this.synchronousCache.clear();
      }

      if (this.clientQueue != null && disconnectQos.clearClientQueue()) {
         this.clientQueue.clear();
      }

      if (disconnectQos.shutdownDispatcher()) {
         if (this.dispatchManager != null) {
            this.dispatchManager.shutdown();
            //this.dispatchManager = null;
         }
         if (this.clientQueue != null) {
            this.clientQueue.shutdown(); // added to make hsqldb shutdown
            this.clientQueue = null;
         }
      }

      if (disconnectQos.shutdownCbServer() && this.cbServer != null) {
         try {
            this.cbServer.shutdown();
            this.cbServer = null;
         } catch (Throwable e) {
            e.printStackTrace();
            log.warning(e.toString());
         }
      }

      this.updateDispatcher.clear();

      if (this.secPlgn != null) {
         this.secPlgn = null;
      }

      this.connectQos = null;
      this.connectReturnQos = null;
      this.disconnectInProgress = false;
      this.msgErrorHandler = null;
      this.updateListener = null;

      this.streamingCb = null;

      super.glob.shutdown();

      this.shutdown = true;
      return true;
   }

   /**
    * @return true if shutdown was called, typically by disconnect()
    */
   public boolean isShutdown() {
	   return this.shutdown;
   }

   /**
    * Access the callback server.
    * @return null if no callback server is established
    */
   public I_CallbackServer getCbServer() {
      return this.cbServer;
   }

   /**
    * Create a descriptive ME, for logging only
    * @return e.g. "/node/heron/client/joe/3" or "UNKNOWN_SESSION" if connect() was not successful
    */
   public String getId() {
      SessionName sessionName = getSessionName();
      return (sessionName == null) ? "UNKNOWN_SESSION" : sessionName.getAbsoluteName();
   }

   /**
    * Useful as a logging prefix.
    * @return For example "client/TheDesperate/-6: "
    */
   public String getLogId() {
      SessionName sessionName = getSessionName();
      return (sessionName == null) ? "" : sessionName.getRelativeName() + ": ";
   }

   /**
    * The public session ID of this login session.
    */
   public SessionName getSessionName() {
      if (this.connectReturnQos != null)
         return this.connectReturnQos.getSessionName();
      if (this.connectQos != null) {
         SessionName sessionName = this.connectQos.getSessionName();
         if (sessionName != null && sessionName.getNodeIdStr() == null && this.serverNodeId != null) {
            // In cluster setup the remote cluster node id is forced
            SessionName sn = new SessionName(glob, new NodeId(this.serverNodeId), sessionName.getLoginName(),
                  sessionName.getPublicSessionId());
            // log.info("Using sessionName=" + sn.getAbsoluteName());
            this.connectQos.setSessionName(sn);
            return sn;
         }
         return sessionName;
      }
      return null;
   }

   /**
    * @see I_XmlBlasterAccess#getStorageIdStr()
    */
   public String getStorageIdStr() {
      return this.storageIdPrefix;
   }

   /**
    * @see I_XmlBlasterAccess#setStorageIdStr(String)
    */
   public void setStorageIdStr(String prefix) {
      this.storageIdPrefix = Global.getStrippedString(prefix);
   }


   /**
    * Allows to set the node name for nicer logging.
    * Typically used by cluster clients and not by ordinary clients
    * @param serverNodeId For example "/node/heron/instanceId/1233435" or "/node/heron"
    */
   public void setServerNodeId(String nodeId) {
      if (nodeId == null) return;
      if (nodeId.startsWith("/node") || nodeId.startsWith("/xmlBlaster/node"))
         this.serverNodeId = nodeId;
      else
         this.serverNodeId = "/node/" + nodeId;
   }

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed for client queue storage identifier. see: setStorageIdStr()
    * <p />
    * for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan" until a successful connect
    * 
    * @return e.g. "/node/golan" or /xmlBlaster/node/heron"
    */
   public String getServerNodeId() {
      if (this.contextNode != null) return this.contextNode.getParent(ContextNode.CLUSTER_MARKER_TAG).getAbsoluteName();
      if (this.serverNodeId != null) return this.serverNodeId;
      return this.glob.getInstanceId(); // Changes for each restart
   }

   /**
    * Set my identity.
    * @param serverNodeId For example "/node/heron/instanceId/1233435" or "/node/heron"
    */
   private void setContextNodeId(String nodeId) {
      // Not for cluster with given serverNodeId: It is invariant
      if (this.serverNodeId != null)
         nodeId = this.serverNodeId;
      
      if (nodeId == null) return;
      if (nodeId.indexOf("/") == -1) nodeId = "/node/"+nodeId; // add CLUSTER_MARKER_TAG to e.g. "/node/avalon.mycomp.com"

      String oldClusterObjectName = "";      // e.g. "org.xmlBlaster:nodeClass=node,node=clientSUB1"
      String oldServerNodeInstanceName = ""; // e.g. "clientSUB1"
      ContextNode clusterContext = null;
      if (this.contextNode != null) {
         // same instance as glob.getContextNode():
         clusterContext = this.contextNode.getParent(ContextNode.CLUSTER_MARKER_TAG);
         oldServerNodeInstanceName = clusterContext.getInstanceName();
         oldClusterObjectName = clusterContext.getAbsoluteName(ContextNode.SCHEMA_JMX);
      }

      // Verify the publicSessionId ...
      if (this.glob.supportJmx()) {
         try {
            if (this.mbeanHandle != null && this.jmxPublicSessionId != getPublicSessionId()) {
            /*int count = */this.glob.getJmxWrapper().renameMBean(this.mbeanHandle.getObjectInstance().getObjectName().toString(),
                           ContextNode.SESSION_MARKER_TAG, ""+getPublicSessionId());
               this.mbeanHandle.getContextNode().setInstanceName(""+getPublicSessionId());
               this.jmxPublicSessionId = getPublicSessionId();
            }
            if (this.mbeanHandle == null &&
                this.contextNode != null &&
                !this.contextNode.getInstanceName().equals(""+getPublicSessionId())) {
               this.contextNode.setInstanceName(""+getPublicSessionId());
            }
         }
         catch (XmlBlasterException e) {
            log.warning(getLogId()+"Ignoring problem during JMX session registration: " + e.toString());
         }
      }
      else {
          this.jmxPublicSessionId = getPublicSessionId();
      }

      // parse new cluster node name ...
      ContextNode tmp = ContextNode.valueOf(nodeId);
      ContextNode tmpClusterContext = (tmp==null)?null:tmp.getParent(ContextNode.CLUSTER_MARKER_TAG);
      if (tmpClusterContext == null) {
         log.severe(getLogId()+"Ignoring unknown serverNodeId '" + nodeId + "'");
         return;
      }
      String newServerNodeInstanceName = tmpClusterContext.getInstanceName(); // e.g. "heron"

      if (oldServerNodeInstanceName.equals(newServerNodeInstanceName)) {
         return; // nothing to do, same cluster name
      }

      this.glob.getContextNode().setInstanceName(newServerNodeInstanceName);
      if (clusterContext == null) {
         clusterContext = this.glob.getContextNode();
         String ln = getLoginName();
         if (ln != null && ln.length() > 0) {
            String instanceName = this.glob.validateJmxValue(ln);
            ContextNode contextNodeSubject = new ContextNode(ContextNode.CONNECTION_MARKER_TAG, instanceName, clusterContext);
            this.contextNode = new ContextNode(ContextNode.SESSION_MARKER_TAG, ""+getPublicSessionId(), contextNodeSubject);
         }
      }
      else {
         clusterContext.setInstanceName(newServerNodeInstanceName);
      }

      this.glob.setScopeContextNode(this.contextNode);

      if (this.glob.supportJmx()) {
         try {
            // Query all "org.xmlBlaster:nodeClass=node,node=clientSUB1" + ",*" sub-nodes and replace the name by "heron"
            // For example our connectionQueue or our plugins like Pop3Driver
            if (oldClusterObjectName.length() > 0) {
               int num = this.glob.getJmxWrapper().renameMBean(oldClusterObjectName, ContextNode.CLUSTER_MARKER_TAG, this.contextNode);
               if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Renamed " + num + " jmx nodes to new '" + nodeId + "'");
            }

            if (this.mbeanHandle == null && this.contextNode != null) {   // "org.xmlBlaster:nodeClass=node,node=heron"
               this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);
            }
         }
         catch (XmlBlasterException e) {
             log.warning(getLogId()+"Ignoring problem during JMX registration: " + e.toString());
         }
      }

      setCheckpointContext(getLogId());
   }

   private void setCheckpointContext(String id) {
      if (id == null || id.length() < 1) {
         this.checkPointContext = null;
         return;
      }
      this.checkPointContext = new String[] { "sessionName", id };
   }

   /**
    * Put the given message entry into the queue
    */
   private Object queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      try {
         final I_Checkpoint cp = glob.getCheckpointPlugin();
         if (cp != null) {
            cp.passingBy(I_Checkpoint.CP_CONNECTION_PUBLISH_ENTER, entry.getMsgUnit(), null, this.checkPointContext);
         }
         this.clientQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);
         if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Forwarded one '" + entry.getEmbeddedType() + "' message, current state is " + getState().toString());
         return entry.getReturnObj();
      }
      catch (XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) log.fine(e.getMessage());
         throw e;
      }
      catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine(e.toString());
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,null,null,e);
         //msgErrorHandler.handleError(new MsgErrorInfo(glob, entry, null, xmlBlasterException));
         throw xmlBlasterException; // internal errors or not in failsafe mode: throw back to client
      }
   }

   /**
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos)
    */
   public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      return subscribe(new SubscribeKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                       new SubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)) );
   }

   /**
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos)
    */
   public SubscribeReturnQos subscribe(SubscribeKey subscribeKey, SubscribeQos subscribeQos) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "subscribe");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      if (getSessionName().isPubSessionIdUser() &&
          subscribeQos.getData().getMultiSubscribe()==false &&
          !subscribeQos.getData().hasSubscriptionId()) {
          // For failsave clients we generate on client side the subscriptionId
          // In case of offline/clientSideQueued operation we guarantee like this a not changing
          // subscriptionId and the client code can reliably use the subscriptionId for further dispatching
          // of update() messages.
          subscribeQos.getData().generateSubscriptionId(getSessionName(), subscribeKey.getData());
      }
      MsgQueueSubscribeEntry entry  = new MsgQueueSubscribeEntry(glob,
                                      this.clientQueue.getStorageId(), subscribeKey.getData(), subscribeQos.getData());
      return (SubscribeReturnQos)queueMessage(entry);
   }

   /**
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    */
   public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos, I_Callback cb) throws XmlBlasterException {
      return subscribe(new SubscribeKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                       new SubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)),
                       cb );
   }

   /**
    * @see I_XmlBlasterAccess#subscribe(SubscribeKey, SubscribeQos, I_Callback)
    */
   public SubscribeReturnQos subscribe(SubscribeKey subscribeKey, SubscribeQos subscribeQos, I_Callback cb) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "subscribe");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      if (this.updateListener == null) {
         String text = "No callback listener is registered. " +
                       " Please use XmlBlasterAccess.connect() with default I_Callback given.";
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, text);
      }

      // sync subscribe & put against update()'s check for entry
      // otherwise if the update was faster then the subscribe to return we miss the entry
      synchronized (this.updateDispatcher) {
         SubscribeReturnQos subscribeReturnQos = subscribe(subscribeKey, subscribeQos);
         this.updateDispatcher.addCallback(subscribeReturnQos.getSubscriptionId(), cb, subscribeQos.getPersistent());
         if (!subscribeReturnQos.isFakedReturn()) {
            this.updateDispatcher.ackSubscription(subscribeReturnQos.getSubscriptionId());
         }
         return subscribeReturnQos;
      }
   }

   /**
    * @see I_XmlBlasterAccess#get(GetKey, GetQos)
    */
   public MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      return get(new GetKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                 new GetQos(glob, glob.getQueryQosFactory().readObject(qos)) );
   }

   /**
    * @see I_XmlBlasterAccess#getCached(GetKey, GetQos)
    */
   public MsgUnit[] getCached(GetKey getKey, GetQos getQos) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "getCached");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      if (this.synchronousCache == null) {  //Is synchronousCache installed?
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME,
              "Can't handle getCached(), please install a cache with createSynchronousCache() first");
      }

      MsgUnit[] msgUnitArr = null;
      msgUnitArr = this.synchronousCache.get(getKey, getQos);
      if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"CacheDump: msgUnitArr=" + msgUnitArr + ": '" + getKey.toXml().trim() + "' \n" + getQos.toXml() + this.synchronousCache.toXml(""));
      //not found in this.synchronousCache
      if(msgUnitArr == null) {
         msgUnitArr = get(getKey, getQos);  //get messages from xmlBlaster (synchronous)
         SubscribeKey subscribeKey = new SubscribeKey(glob, getKey.getData());
         SubscribeQos subscribeQos = new SubscribeQos(glob, getQos.getData());
         SubscribeReturnQos subscribeReturnQos = null;
         synchronized (this.synchronousCache) {
            subscribeReturnQos = subscribe(subscribeKey, subscribeQos); //subscribe to this messages (asynchronous)
            this.synchronousCache.newEntry(subscribeReturnQos.getSubscriptionId(), getKey, msgUnitArr);     //fill messages to this.synchronousCache
         }
         log.info(getLogId()+"New entry in this.synchronousCache created (subscriptionId="+subscribeReturnQos.getSubscriptionId()+")");
      }
      return msgUnitArr;
   }

   /**
    * @see I_XmlBlasterAccess#get(GetKey, GetQos)
    */
   public MsgUnit[] get(GetKey getKey, GetQos getQos) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "get");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueGetEntry entry  = new MsgQueueGetEntry(glob,
              this.clientQueue.getStorageId(), getKey, getQos);
      
      // !isTrySyncMode() && 
      if (!dispatchManager.isSyncMode()) {
         synchronized (asyncGetMonitor) {
            try {
               this.asyncGetLatch = new CountDownLatch(1);
               MsgUnit[] arr = (MsgUnit[])queueMessage(entry);
               try {
            	   // timeout after 10sec
            	   long asyncGetTimeoutMillis = getQos.getData().getClientProperty(GetQos.CP_ASYNC_GET_TIMEOUT_MILLIS, 10000);
                   boolean responseOk = this.asyncGetLatch.await(asyncGetTimeoutMillis, TimeUnit.MILLISECONDS);
                   if (responseOk) {
                      return this.asyncGetMsgUnits;   
                   }
                   else {
                	  //log.warning("Async get call response timed out, no response, GetQos.CP_ASYNC_GET_TIMEOUT_MILLIS=" + asyncGetTimeoutMillis);
                      throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_TIMEOUT,
                    		  "get()", "async get() call timeoud out after " + GetQos.CP_ASYNC_GET_TIMEOUT_MILLIS + "=" + asyncGetTimeoutMillis + "millis");
                   }
               } catch(InterruptedException ie){
                   ie.printStackTrace();
               }
            }
            finally {
                this.asyncGetLatch = null;
                this.asyncGetMsgUnits = null;
            }
         }
      }
      MsgUnit[] arr = (MsgUnit[])queueMessage(entry);
      return (arr == null) ? new MsgUnit[0] : arr;
   }

   /**
    * @see I_XmlBlasterAccess#unSubscribe(UnSubscribeKey, UnSubscribeQos)
    */
   public UnSubscribeReturnQos[] unSubscribe(UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "unSubscribe");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueUnSubscribeEntry entry  = new MsgQueueUnSubscribeEntry(glob,
                                      this.clientQueue.getStorageId(), unSubscribeKey, unSubscribeQos);
      UnSubscribeReturnQos[] arr = (UnSubscribeReturnQos[])queueMessage(entry);
      this.updateDispatcher.removeCallback(unSubscribeKey.getOid());
      return (arr == null) ? new UnSubscribeReturnQos[0] : arr;
   }

   /**
    * @see I_XmlBlasterAccess#unSubscribe(UnSubscribeKey, UnSubscribeQos)
    */
   public UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      return unSubscribe(new UnSubscribeKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                       new UnSubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)) );
   }

   /**
    * @see I_XmlBlasterAccess#publish(MsgUnit)
    */
   public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "publish");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      if (!isTrySyncMode() && dispatchManager != null) {
    	  dispatchManager.trySyncMode(false);
    	  dispatchManager.switchToASyncMode();
      }
      MsgQueuePublishEntry entry  = new MsgQueuePublishEntry(glob, msgUnit, this.clientQueue.getStorageId());
      return (PublishReturnQos)queueMessage(entry);
   }

   /**
    * @see I_XmlBlasterAccess#publishOneway(MsgUnit[])
    */
   public void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "publishOneway");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      final boolean ONEWAY = true;
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MsgQueuePublishEntry entry  = new MsgQueuePublishEntry(glob, msgUnitArr[ii],
                                          this.clientQueue.getStorageId(), ONEWAY);
         queueMessage(entry);
      }
   }

   // rename to publish()
   public PublishReturnQos[] publishArr(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "publishArr");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      if (this.firstWarn) {
         log.warning(getLogId()+"Publishing arrays is not atomic implemented - TODO");
         this.firstWarn = false;
      }
      PublishReturnQos[] retQos = new PublishReturnQos[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         MsgQueuePublishEntry entry  = new MsgQueuePublishEntry(glob, msgUnitArr[ii],
                                          this.clientQueue.getStorageId());
         retQos[ii] = (PublishReturnQos)queueMessage(entry);
      }
      return retQos;
   }

   /**
    * @see I_XmlBlasterAccess#erase(EraseKey, EraseQos)
    */
   public EraseReturnQos[] erase(EraseKey eraseKey, EraseQos eraseQos) throws XmlBlasterException {
      if (!this.isValid)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_UNAVAILABLE, ME, "erase");
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueEraseEntry entry  = new MsgQueueEraseEntry(glob,
                                      this.clientQueue.getStorageId(), eraseKey, eraseQos);
      EraseReturnQos[] arr = (EraseReturnQos[])queueMessage(entry);
      return (arr == null) ? new EraseReturnQos[0] : arr;
   }

   /**
    * @see I_XmlBlasterAccess#erase(EraseKey, EraseQos)
    */
   public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      return erase(new EraseKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                       new EraseQos(glob, glob.getQueryQosFactory().readObject(qos)) );
   }
   
   /**
    * For example called by SOCKET layer (SocketCallbackImpl.java) on EOF. 
    * Does immediate ping to go to polling mode
    * @param xmlBlasterException
    * @see org.xmlBlaster.client.I_CallbackExtended#lostConnection(XmlBlasterException)
    */
   public void lostConnection(XmlBlasterException xmlBlasterException) {
      if (log.isLoggable(Level.FINE)) log.fine("Communication layer lost connection: " + ((xmlBlasterException==null)?"":xmlBlasterException.toString()));
      this.dispatchManager.pingCallbackServer(false, true);
   }

   /**
    * Force a async ping to re-check connection to server. Status change can be
    * got asynchronously via registerConnectionListener()
    */
   public void ping() {
      this.dispatchManager.pingCallbackServer(false, false);
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Entering update(updateKey=" + updateKey.getOid() +
                    ", subscriptionId=" + updateQos.getSubscriptionId() + ", " + ((this.synchronousCache != null) ? "using synchronousCache" : "no synchronousCache") + ") ...");

      if (this.synchronousCache != null) {
         boolean retVal;
         synchronized (this.synchronousCache) {
            retVal = this.synchronousCache.update(updateQos.getSubscriptionId(), updateKey, content, updateQos);
         }
         if (retVal) {
            if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Putting update message " + updateQos.getSubscriptionId() + " into cache");
            return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
         }
         if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Update message " + updateQos.getSubscriptionId() + " is not for cache");
      }

      Object obj = null;
      // sync against subscribe & put
      // otherwise if the update was faster then the subscribe to return we miss the entry
      synchronized (this.updateDispatcher) {
         obj = this.updateDispatcher.getCallback(updateQos.getSubscriptionId());
      }

      if (obj != null) {  // If a special callback was specified for this subscription:
         I_Callback cb = (I_Callback)obj;
         return cb.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
      }
      else if (this.updateListener != null) {
         // If a general callback was specified on login:
         return this.updateListener.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
      }
      else {
         log.severe(getLogId()+"Ignoring unexpected update message as client has not registered a callback: " + updateKey.toXml() + "" + updateQos.toXml());
      }

      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Call by DispatchManager on connection state transition.
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public void toAlive(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE + " connectInProgress=" + this.connectInProgress);
      if (this.clientQueue != null && this.clientQueue.getNumOfEntries() > 0) {
         log.info(getLogId()+"Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE +
                      " connectInProgress=" + this.connectInProgress +
                      " with " + this.clientQueue.getNumOfEntries() + " client side queued messages");
      }
      if (this.connectInProgress) {
    	 //if (isTrySyncMode()) {
    	 // until connecQos with prio MAX is in queue we MUST work in sync mode 
         dispatchManager.trySyncMode(true);
    	 //}
         if (this.clientQueue != null && this.clientQueue.getNumOfEntries() > 0) {
            try {
               MsgQueueEntry entry = (MsgQueueEntry)this.clientQueue.peek();
               if (entry == null) {
                   log.severe(getLogId()+"Removing connect entry is null in client tail back queue but numOfEntry=" + this.clientQueue.getNumOfEntries());
                   //log.severe(getLogId()+"Removing connect entry is null in client tail back queue but numOfEntry=" + this.clientQueue.getNumOfEntries() + " clearing now queue");
                   //this.clientQueue.clear();
               }
               if (entry != null && entry.getMethodName() == MethodName.CONNECT) {
                  this.clientQueue.remove();
                  log.info(getLogId()+"Removed queued connect message, our new connect has precedence");
               }
            }
            catch (XmlBlasterException e) {
               log.severe(getLogId()+"Removing connect entry in client tail back queue failed: " + e.getMessage() + "\n" + toXml());
            }
            catch (Throwable e) {
            	e.printStackTrace();
                log.severe(getLogId()+"Removing connect entry in client tail back queue failed: " + e.getMessage() + "\n" + toXml());
             }
         }
         return;
      }

      if (isTrySyncMode() && (this.clientQueue == null || this.clientQueue.getNumOfEntries() == 0)) {
         dispatchManager.trySyncMode(true);
      }

      if (this.connectReturnQos == null || !this.connectReturnQos.isReconnected()) {
         cleanupForNewServer();
      }

      if (this.connectionListener != null) {
         this.connectionListener.reachedAlive(oldState, this);
      }
   }

   public void toAliveSync(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
	  if (!isTrySyncMode())
		  return;
      if (this.connectionListener != null) {
         this.connectionListener.reachedAliveSync(oldState, this);
      }
   }
   
   /**
    * If we have reconnected to xmlBlaster and the xmlBlaster server instance
    * is another one which does not know our session state and subscribes we need to clear all
    * cached subscribes etc.
    */
   private void cleanupForNewServer() {
      if (this.updateDispatcher.size() > 0) {
         int num = this.updateDispatcher.clearAckNonPersistentSubscriptions(); // to avoid memory leaks, subscribes pending in the queue are not cleared
         if (num > 0) {
            log.info(getLogId()+"Removed " + num + " subscribe specific callback registrations");
         }
         // TODO: On switch to sync delivery and the client has
         // cleared subscribes from the queue manually we have still a memory leak here:
         // We would need to call clearNAKSubscriptions()
      }
      if (this.synchronousCache != null) {
         this.synchronousCache.clear(); // we need to re-subscribe
      }
   }

   /**
    * Call by DispatchManager on connection state transition.
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public void toPolling(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING + " connectInProgress=" + this.connectInProgress);
      if (this.connectInProgress) return;
      if (this.connectionListener != null) {
         this.connectionListener.reachedPolling(oldState, this);
      }
   }

   /**
    * Workaround to transport the reason for the toDead() transition as
    * the interface {@link I_ConnectionStateListener#reachedDead(ConnectionStateEnum, I_XmlBlasterAccess) is missing
    * to pass the exception to the client.
    * <p>
    * Currently the client needs a downcast to XmlBlasterAccess (not in I_XmlBlasterAccess)
    * @return Can be null
    */
   public XmlBlasterException getToDeadXmlBlasterException() {
		return toDeadXmlBlasterException;
	}

   /**
    * Call by DispatchManager on connection state transition.
    * <p>Enforced by interface I_ConnectionStatusListener</p>
    */
   public void toDead(I_DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException) {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD + " connectInProgress=" + this.connectInProgress);
      if (this.connectionListener != null) {
    	 this.toDeadXmlBlasterException = xmlBlasterException; // hack, description see #getToDeadXmlBlasterException
         this.connectionListener.reachedDead(oldState, this);
      }
   }

   /**
    * Access the environment settings of this connection.
    * <p>Enforced by interface I_XmlBlasterAccess</p>
    * @return The global handle (like a stack with local variables for this connection)
    */
   public Global getGlobal() {
      return this.glob;
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The queue used to store tailback messages.
    */
   public I_Queue getQueue() {
      return this.clientQueue;
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The current state of the connection
    */
   public ConnectionStateEnum getState() {
      if (!isConnected()) return ConnectionStateEnum.UNDEF;
      return this.dispatchManager.getDispatchConnectionsHandler().getState();
   }

   /**
    * Get the connection state.
    * String version for JMX access.
    * @return "UNDEF", "ALIVE", "POLLING", "DEAD"
    */
   public String getConnectionState() {
      return getState().toString();
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if the connection to xmlBlaster is operational
    */
   public boolean isAlive() {
      if (!isConnected()) return false;
      return this.dispatchManager.getDispatchConnectionsHandler().isAlive();
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if we are polling for the server
    */
   public boolean isPolling() {
      if (!isConnected()) return false;
      return this.dispatchManager.getDispatchConnectionsHandler().isPolling();
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if we have definitely lost the connection to xmlBlaster and gave up
    */
   public boolean isDead() {
      if (!isConnected()) return false;
      return this.dispatchManager.getDispatchConnectionsHandler().isDead();
   }

   /**
    * Access the returned QoS of a connect() call.
    * <p>Enforced by interface I_XmlBlasterAccess</p>
    * @return Can be null if not connected
    */
   public ConnectReturnQos getConnectReturnQos() {
      return this.connectReturnQos;
   }

   /**
    * Access the current ConnectQos
    * <p>Enforced by interface I_XmlBlasterAccess</p>
    * @return Can be null if not connected
    */
   public ConnectQos getConnectQos() {
      return this.connectQos;
   }

   /**
    * @return null if no callback is configured
    */
   public final DispatchStatistic getDispatchStatistic() {
      if (this.statistic == null) {
         synchronized (this) {
            if (this.statistic == null) {
               if (this.dispatchManager != null)
                  this.statistic = this.dispatchManager.getDispatchStatistic();
               else
                  this.statistic = new DispatchStatistic();
            }
         }
      }
      return this.statistic;
   }

   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public synchronized final String getLoginName() {
      SessionName sn = getSessionName();
      if (sn == null) return "xmlBlasterClient";
      return sn.getLoginName();
      /*
      //if (this.connectReturnQos != null)
      //   return this.connectReturnQos.getLoginName();
      //try {
         if (connectQos != null && connectQos.getSecurityQos() != null) {
            String nm = connectQos.getSecurityQos().getUserId();
            if (nm != null && nm.length() > 0)
               return nm;
         }
      //}
      //catch (XmlBlasterException e) {}
      return glob.getId(); // "client?";
      */
   }

   public final boolean isCallbackConfigured() {
      return (this.cbServer != null);
   }

   public final long getUptime() {
      return (System.currentTimeMillis() - this.startupTime)/1000L;
   }

   public final String getLoginDate() {
      long ll = this.startupTime;
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }

   public synchronized final long getPublicSessionId() {
      SessionName sn = getSessionName();
      if (sn == null) return 0;
      return sn.getPublicSessionId();
   }

   public final long getNumPublish() {
      return getDispatchStatistic().getNumPublish();
   }

   public final long getNumSubscribe() {
      return getDispatchStatistic().getNumSubscribe();
   }

   public final long getNumUnSubscribe() {
      return getDispatchStatistic().getNumUnSubscribe();
   }

   public final long getNumGet() {
      return getDispatchStatistic().getNumGet();
   }

   public final long getNumErase() {
      return getDispatchStatistic().getNumErase();
   }

   public final long getNumUpdateOneway() {
      return getDispatchStatistic().getNumUpdateOneway();
   }

   public final long getNumUpdate() {
      return getDispatchStatistic().getNumUpdate();
   }

   public synchronized final long getConnectionQueueNumMsgs() {
      if (this.clientQueue == null) return 0L;
      return this.clientQueue.getNumOfEntries();
   }

   public synchronized final long getConnectionQueueMaxMsgs() {
      if (this.clientQueue == null) return 0L;
      return this.clientQueue.getMaxNumOfEntries();
   }

   public final long getPingRoundTripDelay() {
      return getDispatchStatistic().getPingRoundTripDelay();
   }

   public final long getRoundTripDelay() {
      return getDispatchStatistic().getRoundTripDelay();
   }

   /** JMX **/
   public String invokePublish(String key, String content, String qos) throws Exception {
      if (key == null || key.length()==0 || key.equalsIgnoreCase("String"))
         throw new IllegalArgumentException("Please pass a valid XML key like '<key oid='Hello'/> or the simple oid like 'Hello'");
      if (key.indexOf("<") == -1) {
         key = "<key oid='" + key + "'/>";
      }
      qos = checkQueryKeyQos(key, qos);
      if (content == null) content = "";
      try {
         MsgUnit msgUnit = new MsgUnit(key, content, qos);
         PublishReturnQos prq = publish(msgUnit);
         return prq.toString();
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   private String checkQueryKeyQos(String url, String qos) {
      if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"url=" + url + " qos=" + qos);
      if (url == null || url.length()==0 || url.equalsIgnoreCase("String"))
         throw new IllegalArgumentException("Please pass a valid URL like 'xpath://key' or a simple oid like 'Hello'");
      if (qos == null || qos.length()==0 || qos.equalsIgnoreCase("String")) qos = "<qos/>";
      return qos;
   }

   /** JMX **/
   public String[] invokeUnSubscribe(String url, String qos) throws Exception {
      qos = checkQueryKeyQos(url, qos);
      try {
         UnSubscribeKey usk = new UnSubscribeKey(glob, url);
         UnSubscribeReturnQos[] usrq = unSubscribe(usk, new UnSubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)));
         if (usrq == null) return new String[0];
         String[] ret = new String[usrq.length];
         if (ret.length < 1) {
            return new String[] { "unSubscribe '"+url+"' did not match any subscription" };
         }
         for (int i=0; i<usrq.length; i++) {
            ret[i] = usrq[i].toXml();
         }
         return ret;
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /** JMX **/
   public String invokeSubscribe(String url, String qos) throws Exception {
      qos = checkQueryKeyQos(url, qos);
      try {
         SubscribeKey usk = new SubscribeKey(glob, url);
         SubscribeReturnQos srq = subscribe(usk, new SubscribeQos(glob, glob.getQueryQosFactory().readObject(qos)));
         if (srq == null) return "";
         return srq.toXml();
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /** JMX **/
   public String[] invokeGet(String url, String qos) throws Exception {
      qos = checkQueryKeyQos(url, qos);
      try {
         GetKey gk = new GetKey(glob, url);
         MsgUnit[] msgs = get(gk, new GetQos(glob, glob.getQueryQosFactory().readObject(qos)));
         if (msgs == null) return new String[0];
         if (msgs == null || msgs.length < 1) {
            return new String[] { "get('"+url+"') did not match any topic" };
         }
         ArrayList tmpList = new ArrayList();
         for (int i=0; i<msgs.length; i++) {
            tmpList.add("  "+msgs[i].getKeyData().toXml());
            tmpList.add("  "+msgs[i].getContentStr());
            tmpList.add("  "+msgs[i].getQosData().toXml());
         }
         return (String[])tmpList.toArray(new String[tmpList.size()]);
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /** JMX **/
   public String[] invokeErase(String url, String qos) throws Exception {
      qos = checkQueryKeyQos(url, qos);
      try {
         EraseKey ek = new EraseKey(glob, url);
         EraseReturnQos[] erq = erase(ek, new EraseQos(glob, glob.getQueryQosFactory().readObject(qos)));
         if (erq == null) return new String[0];
         String[] ret = new String[erq.length];
         if (ret.length < 1) {
            return new String[] { "erase('"+url+"') did not match any topic, nothing is erased." };
         }
         for (int i=0; i<erq.length; i++) {
            ret[i] = erq[i].toXml();
         }
         return ret;
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /**
    * Sets the DispachManager belonging to this session to active or inactive.
    * It is initially active. Setting it to false temporarly inhibits dispatch of
    * messages which are in the callback queue. Setting it to true starts the
    * dispatch again.
    * @param dispatchActive
    */
   public synchronized void setDispatcherActive(boolean dispatcherActive) {
      if (this.dispatchManager != null) {
         this.dispatchManager.setDispatcherActive(dispatcherActive);
      }
   }

   public synchronized  boolean getDispatcherActive() {
      if (this.dispatchManager != null) {
         return this.dispatchManager.isDispatcherActive();
      }
      return false;
   }

   public void setCallbackDispatcherActive(boolean activate) throws XmlBlasterException {
      if (this.streamingCb != null && !isCallbackDispatcherActive() && activate) {
         int ret = this.streamingCb.sendInitialQueueEntries();
         log.info("locally retrieved '" + ret + "' chunks");
      }

      String command = getSessionName() + "/?dispatcherActive=" + activate;
      sendAdministrativeCommand(command);
      this.connectQos.getSessionCbQueueProperty().getCurrentCallbackAddress().setDispatcherActive(activate);
   }

   public boolean isCallbackDispatcherActive() throws XmlBlasterException {
      String command = getSessionName() + "/?dispatcherActive";
      boolean ret = "true".equalsIgnoreCase(sendAdministrativeCommand(command));
      return ret;
   }

   public String sendAdministrativeCommand(String command) throws XmlBlasterException {
      if (command == null)
         throw new IllegalArgumentException("sendAdministrativeCommand() called with null argument");
      command = command.trim();
      boolean isGet = command.indexOf("get ") == 0 || command.indexOf("GET ") == 0;
      boolean isSet = command.indexOf("set ") == 0 || command.indexOf("SET ") == 0;
      String cmd = ((isGet || isSet)) ? command.substring(4) : command;

      if (isSet || (!isGet && cmd.indexOf("=") != -1)) {
         String oid = "__cmd:" + cmd;
         PublishKey key = new PublishKey(glob, oid); // oid="__cmd:/client/joe/1/?dispatcherActive=false"
         PublishQos qos = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(key, "", qos);
         try {
            PublishReturnQos ret = publish(msgUnit);
            if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Send '" + cmd + " '");
            return ret.getState();
         }
         catch (XmlBlasterException e) {
            if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Sending of '" + cmd + " ' failed: " + e.getMessage());
            throw e;
         }
      }
      else {
         String oid = "__cmd:" + cmd;
         GetKey getKey = new GetKey(glob, oid);
         GetQos getQos = new GetQos(glob);
         try {
            MsgUnit[] msgs = get(getKey, getQos);
            if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Send '" + cmd + " ', got array of size " + msgs.length);
            if (msgs.length == 0)
               return "";
            return msgs[0].getContentStr();
         }
         catch (XmlBlasterException e) {
            if (log.isLoggable(Level.FINE)) log.fine(getLogId()+"Sending of '" + cmd + " ' failed: " + e.getMessage());
            throw e;
         }
      }
   }

   public synchronized String[] peekClientMessages(int numOfEntries) throws Exception {
      try {
         if (numOfEntries == 0)
            return new String[] { "Please pass number of messages to peak" };
         if (this.clientQueue == null)
            return new String[] { "There is no client queue available" };
         if (this.clientQueue.getNumOfEntries() < 1)
            return new String[] { "The client queue is empty" };

         List<I_Entry> list = this.clientQueue.peek(numOfEntries, -1);

         if (list.size() == 0)
            return new String[] { "Peeking messages from client queue failed, the reason is not known" };

         ArrayList tmpList = new ArrayList();
         for (int i=0; i<list.size(); i++) {
            MsgQueueEntry entry = (MsgQueueEntry)list.get(i);
            if (entry instanceof MsgQueuePublishEntry) {
               MsgQueuePublishEntry pe = (MsgQueuePublishEntry)entry;
               tmpList.add("  "+pe.getMsgUnit().getKeyData().toXml());
               tmpList.add("  "+pe.getMsgUnit().getContentStr());
               tmpList.add("  "+pe.getMsgUnit().getQosData().toXml());
            }
            else if (entry instanceof MsgQueueConnectEntry) {
            	MsgQueueConnectEntry pe = (MsgQueueConnectEntry)entry;
                tmpList.add("  "+pe.getConnectQosData().toXml());
            }
            else if (entry instanceof MsgQueueDisconnectEntry) {
            	MsgQueueDisconnectEntry pe = (MsgQueueDisconnectEntry)entry;
                tmpList.add("  "+pe.getDisconnectQos().toXml());
            }
            else if (entry instanceof MsgQueueEraseEntry) {
            	MsgQueueEraseEntry pe = (MsgQueueEraseEntry)entry;
                tmpList.add("  "+pe.getEraseKey().toXml());
                tmpList.add("  "+pe.getEraseQos().toXml());
            }
            else if (entry instanceof MsgQueueGetEntry) {
            	MsgQueueGetEntry pe = (MsgQueueGetEntry)entry;
                tmpList.add("  "+pe.getGetKey().toXml());
                tmpList.add("  "+pe.getGetQos().toXml());
            }
            else if (entry instanceof MsgQueueSubscribeEntry) {
            	MsgQueueSubscribeEntry pe = (MsgQueueSubscribeEntry)entry;
                tmpList.add("  "+pe.getSubscribeKeyData().toXml());
                tmpList.add("  "+pe.getSubscribeQosData().toXml());
            }
            else if (entry instanceof MsgQueueUnSubscribeEntry) {
            	MsgQueueUnSubscribeEntry pe = (MsgQueueUnSubscribeEntry)entry;
                tmpList.add("  "+pe.getUnSubscribeKey().toXml());
                tmpList.add("  "+pe.getUnSubscribeQos().toXml());
            }
            else {
               tmpList.add("Unsupported message queue entry '" + entry.getClass().getName() + "'");
            }
         }

         return (String[])tmpList.toArray(new String[tmpList.size()]);
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /**
    * Peek messages from client queue and dump them to a file, they are not removed.
    * @param numOfEntries The number of messages to peek, taken from the front
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @return The file names of the dumped messages
    */
   public synchronized String[] peekClientMessagesToFile(int numOfEntries, String path) throws Exception {
      try {
         return this.glob.peekQueueMessagesToFile(this.clientQueue, numOfEntries, path, "client");
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /**
    * Command line usage.
    */
   public static String usage(Global glob) {
      glob = (glob == null) ? Global.instance() : glob;
      StringBuffer sb = new StringBuffer(4096);
      sb.append("\n");
      sb.append("Choose a connection protocol:\n");
      sb.append("   -protocol           Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XMLRPC'.\n");
      sb.append("                       This is used for connection to xmlBlaster and for the callback connection.\n");
      sb.append("                       Current setting is '" + glob.getProperty().get("client.protocol", "IOR") + "'. See below for protocol settings.\n");
      sb.append("   -dispatch/connection/protocol <protocol>\n");
      sb.append("                       Specify the protocol to connect to xmlBlaster only (not for the callback).\n");
      sb.append("   -dispatch/callback/protocol <protocol>\n");
      sb.append("                       Specify the protocol for the callback connection only.\n");
      sb.append("              Example: java MyApp -protocol SOCKET\n");
      sb.append("                       java MyApp -dispatch/connection/protocol RMI -dispatch/connection/plugin/rmi/hostname 192.168.10.34\n");
      sb.append("                       java MyApp -dispatch/connection/protocol RMI -dispatch/callback/protocol XMLRPC\n");
      sb.append("\n");
      sb.append("Security features:\n");
      sb.append("   -Security.Client.DefaultPlugin \"htpasswd,1.0\"\n");
      sb.append("                       Force the given authentication schema, here the 'htpasswd' is enforced\n");
      sb.append("                       Clients can overwrite this with ConnectQos.java\n");
      try {
      sb.append(new org.xmlBlaster.client.qos.ConnectQos(glob).usage());
      } catch (XmlBlasterException e) {}
      sb.append(new org.xmlBlaster.util.qos.address.Address(glob).usage());
      sb.append(new org.xmlBlaster.util.qos.storage.ClientQueueProperty(glob,null).usage());
      sb.append(new org.xmlBlaster.util.qos.address.CallbackAddress(glob).usage());
      sb.append(new org.xmlBlaster.util.qos.storage.CbQueueProperty(glob,null,null).usage());
      sb.append(new org.xmlBlaster.util.qos.storage.HistoryQueueProperty(glob,null).usage("Control the default size of the history queue for each topic (send with publish calls)"));
      sb.append(getPluginUsage("org.xmlBlaster.client.protocol.socket.SocketConnection"));
      sb.append(getPluginUsage("org.xmlBlaster.client.protocol.corba.CorbaConnection"));
      sb.append(getPluginUsage("org.xmlBlaster.client.protocol.rmi.RmiConnection"));
      sb.append(getPluginUsage("org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection"));
      //sb.append(org.xmlBlaster.util.Global.instance().usage()); // for Logger help
      return sb.toString();
   }

   /**
    * Access plugin specific usage()
    * @return if plugin is not in CLASSPATH return empty string
    */
   public static String getPluginUsage(String clazzName) {
      try {
         Class clazz = java.lang.Class.forName(clazzName);
         if (clazz != null) {
            Class[] paramCls = new Class[0];
            Object[] params = new Object[0];
            java.lang.reflect.Method method = clazz.getMethod("usage", paramCls);
            String tmp = (String)method.invoke(clazz, params);
            return tmp;
         }
      }
      catch (Exception ex) { // java.lang.ClassNotFoundException:
      }
      return "";
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of SubjectInfo as a XML ASCII string
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<XmlBlasterAccess id='").append(this.getId());
      if (this.dispatchManager != null && this.dispatchManager.getDispatchConnectionsHandler() != null) {
         sb.append("' state='").append(this.dispatchManager.getDispatchConnectionsHandler().getState());
      }
      sb.append("'>");
      sb.append(offset).append(" <connected>").append(isConnected()).append("</connected>");
      sb.append(offset).append("</XmlBlasterAccess>");

      return sb.toString();
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.XmlBlasterAccess
    */
   public static void main( String[] args ) {
      try {
         final Global glob = new Global(args);
         final String oid = "HelloWorld";

         final I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();

         /*
         try {
            log.info(ME, "Hit a key to subscribe on topic " + oid);
            try { System.in.read(); } catch(java.io.IOException e) {}
            SubscribeKey sk = new SubscribeKey(glob, oid);
            SubscribeQos sq = new SubscribeQos(glob);
            SubscribeReturnQos subRet = xmlBlasterAccess.subscribe(sk, sq);
            log.info(ME, "Subscribed for " + sk.toXml() + "\n" + sq.toXml() + " return:\n" + subRet.toXml());
         }
         catch(XmlBlasterException e) {
            log.error(ME, e.getMessage());
         }
         */

         xmlBlasterAccess.registerConnectionListener(new I_ConnectionStateListener() {
            public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE + " with " + connection.getQueue().getNumOfEntries() + " queue entries pending");
            }
            public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
            }
            public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
            }
            public void reachedAliveSync(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
               log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE + " in sync");
            }
         });

         ConnectReturnQos connectReturnQos = xmlBlasterAccess.connect(null, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("UPDATE: Receiving asynchronous callback message " + updateKey.toXml() + "\n" + updateQos.toXml());
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates
         if (xmlBlasterAccess.isAlive()) {
            log.info("Successfully connected to xmlBlaster");
         }
         else {
            log.info("We continue in fail safe mode: " + connectReturnQos.toXml());
         }

         {
            log.info("Hit a key to subscribe on topic " + oid);
            try { System.in.read(); } catch(java.io.IOException e) {}
            SubscribeKey sk = new SubscribeKey(glob, oid);
            SubscribeQos sq = new SubscribeQos(glob);
            SubscribeReturnQos subRet = xmlBlasterAccess.subscribe(sk, sq);
            log.info("Subscribed for " + sk.toXml() + "\n" + sq.toXml() + " return:\n" + subRet.toXml());

            log.info("Hit a key to publish '" + oid + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
            MsgUnit msgUnit = new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>");
            PublishReturnQos publishReturnQos = xmlBlasterAccess.publish(msgUnit);
            log.info("Successfully published message to xmlBlaster, msg=" + msgUnit.toXml() + "\n returned QoS=" + publishReturnQos.toXml());
            try { Thread.sleep(1000L); } catch( InterruptedException i) {} // wait for update

            {
               log.info("Hit a key to 3 times publishOneway '" + oid + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
               MsgUnit[] msgUnitArr = new MsgUnit[] {
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>")
               };
               xmlBlasterAccess.publishOneway(msgUnitArr);
               log.info("Successfully published " + msgUnitArr.length + " messages oneway");
               try { Thread.sleep(1000L); } catch( InterruptedException i) {} // wait for update
            }

            {
               log.info("Hit a key to 3 times publishArr '" + oid + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
               MsgUnit[] msgUnitArr = new MsgUnit[] {
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
               new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>")
               };
               PublishReturnQos[] retArr = xmlBlasterAccess.publishArr(msgUnitArr);
               log.info("Successfully published " + retArr.length + " acknowledged messages");
               try { Thread.sleep(1000L); } catch( InterruptedException i) {} // wait for update
            }

            {
               log.info("Hit a key to get '" + oid + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
               GetKey gk = new GetKey(glob, oid);
               GetQos gq = new GetQos(glob);
               MsgUnit[] msgs = xmlBlasterAccess.get(gk, gq);
               log.info("Successfully got message from xmlBlaster, msg=" + msgs[0].toXml());
            }

            int numGetCached = 4;
            xmlBlasterAccess.createSynchronousCache(100);
            for (int i=0; i<numGetCached; i++) {
               log.info("Hit a key to getCached '" + oid + "' #"+i+"/"+numGetCached);
               try { System.in.read(); } catch(java.io.IOException e) {}
               GetKey gk = new GetKey(glob, oid);
               GetQos gq = new GetQos(glob);
               MsgUnit[] msgs = xmlBlasterAccess.getCached(gk, gq);
               log.info("Successfully got message from xmlBlaster, msg=" + msgs[0].toXml());
            }

            log.info("Hit a key to unSubscribe on topic '" + oid + "' and '" + subRet.getSubscriptionId() + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
            UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
            UnSubscribeQos uq = new UnSubscribeQos(glob);
            UnSubscribeReturnQos[] unSubRet = xmlBlasterAccess.unSubscribe(uk, uq);
            log.info("UnSubscribed for " + uk.toXml() + "\n" + uq.toXml() + " return:\n" + unSubRet[0].toXml());

            log.info("Hit a key to erase on topic " + oid);
            try { System.in.read(); } catch(java.io.IOException e) {}
            EraseKey ek = new EraseKey(glob, oid);
            EraseQos eq = new EraseQos(glob);
            EraseReturnQos[] er = xmlBlasterAccess.erase(ek, eq);
            log.info("Erased for " + ek.toXml() + "\n" + eq.toXml() + " return:\n" + er[0].toXml());
         }

         int numPublish = 10;
         for (int ii=0; ii<numPublish; ii++) {
            log.info("Hit a key to publish #" + (ii+1) + "/" + numPublish);
            try { System.in.read(); } catch(java.io.IOException e) {}

            MsgUnit msgUnit = new MsgUnit(glob, "<key oid=''/>", ("Hi #"+(ii+1)).getBytes(), "<qos><persistent>true</persistent></qos>");
            PublishReturnQos publishReturnQos = xmlBlasterAccess.publish(msgUnit);
            log.info("Successfully published message #" + (ii+1) + " to xmlBlaster, msg=" + msgUnit.toXml() + "\n returned QoS=" + publishReturnQos.toXml());
         }

         log.info("Hit a key to disconnect ...");
         try { System.in.read(); } catch(java.io.IOException e) {}
         xmlBlasterAccess.disconnect(null);
      }
      catch (XmlBlasterException xmlBlasterException) {
         System.out.println("WARNING: Test failed: " + xmlBlasterException.getMessage());
      }
      catch (Throwable e) {
         e.printStackTrace();
         System.out.println("ERROR: Test failed: " + e.toString());
      }
      System.exit(0);
   }

   /**
    * The implementation which receives the callback messages.
    * @return Returns the updateListener or null if none was registered
    */
   public I_Callback getUpdateListener() {
      return this.updateListener;
   }

   /**
    * Register a listener to receive the callback messages.
    * <br />
    * Note: Usually you don't need to call this method directly
    * as you should pass your callback listener with connect().
    * @param updateListener The updateListener to set.
    */
   public void setUpdateListener(I_Callback updateListener) {
      this.updateListener = updateListener;
   }

   public String getVersion() {
      return glob.getVersion();
   }
   public String getRevisionNumber() {
      return glob.getRevisionNumber();
   }
   public String getBuildTimestamp() {
      return glob.getBuildTimestamp();
   }
   public String getBuildJavaVendor() {
      return glob.getBuildJavaVendor();
   }
   public String getBuildJavaVersion() {
      return glob.getBuildJavaVersion();
   }

   /**
    * Create a temporay topic.
    * You need to erase it yourself when not needed anymore
    * @param topicProperty Can be null (the default is no DOM entry)
    * @return The details about the created, temporary topic
    * @throws XmlBlasterException
    * @todo Automatically delete topic when session dies; don't allow other session to subscribe on it
    */
   public PublishReturnQos createTemporaryTopic(TopicProperty topicProperty) throws XmlBlasterException {
      if (topicProperty == null) {
         return createTemporaryTopic(-1, 10);
      }
      PublishKey pk = new PublishKey(glob, "");
      PublishQos pq = new PublishQos(glob);
      pq.setTopicProperty(topicProperty);
      MsgUnit msgUnit = new MsgUnit(pk, new byte[0], pq);
      PublishReturnQos prq = publish(msgUnit);
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Created temporary topic " + prq.getKeyOid());
      return prq;
   }

   public PublishReturnQos createTemporaryTopic(long destroyDelay, int historyMaxMsg) throws XmlBlasterException {
	   return createTemporaryTopic(null, destroyDelay, historyMaxMsg);
   }

   /**
    * 
    * @param uniqueTopicId Usually null, can be used to force a topicId. 
    * e.g. topicIdPrefix="device.joe.request" -> the topic is something like "device.joe.request135823058558"
    * @param destroyDelay
    * @param historyMaxMsg
    * @return
    * @throws XmlBlasterException
    */
   public PublishReturnQos createTemporaryTopic(String uniqueTopicId, long destroyDelay, int historyMaxMsg) throws XmlBlasterException {
	  if (uniqueTopicId == null) uniqueTopicId = "";
      PublishKey pk = new PublishKey(glob, uniqueTopicId);
      PublishQos pq = new PublishQos(glob);
      TopicProperty topicProperty = new TopicProperty(glob);
      topicProperty.setDestroyDelay(destroyDelay);
      topicProperty.setCreateDomEntry(false);
      topicProperty.setReadonly(false);
      pq.setAdministrative(true);
      if (historyMaxMsg >= 0L) {
         HistoryQueueProperty prop = new HistoryQueueProperty(this.glob, null);
         prop.setMaxEntries(historyMaxMsg);
         topicProperty.setHistoryQueueProperty(prop);
      }
      pq.setTopicProperty(topicProperty);
      MsgUnit msgUnit = new MsgUnit(pk, new byte[0], pq);
      PublishReturnQos prq = publish(msgUnit);
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Created temporary topic " + prq.getKeyOid());
      return prq;
   }

   // TODO: add other properties, add documentation requirement
   //       Add own class to support multiple request/reply over same temporary topic
   /**
    * @see org.xmlBlaster.test.client.TestRequestResponse
    */
   public MsgUnit[] request(MsgUnit msgUnit, long timeout, int maxEntries) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(getLogId()+"Entering request with timeout=" + timeout);
      if (msgUnit == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Please supply a valid msgUnit to request()");

      // Create a temporary reply topic ...
      long destroyDelay = timeout+86400000; // on client crash, cleanup after one day; //long destroyDelay = -1;
      // optional, "device.joe.response" -> can be useful for performance, NOT thread safe
      boolean createResponseTopic = msgUnit.getQosData().getClientProperty("__createResponseTopic", true);
      if (createResponseTopic == false) {
          msgUnit.getQosData().getClientProperties().remove("__createResponseTopic");
      }
      String responseTopicId = msgUnit.getQosData().getClientProperty("__responseTopicId", "");
      if (responseTopicId.length() > 0) {
          msgUnit.getQosData().getClientProperties().remove("__responseTopicId");
      }
      else {
          // "device.joe.response" -> can be useful for authorization, must be distinguishable to other clients
          String responseTopicIdPrefix = msgUnit.getQosData().getClientProperty("__responseTopicIdPrefix", "");
          if (responseTopicIdPrefix.length() > 0) {
             responseTopicId = responseTopicIdPrefix + new Timestamp().getTimestamp(); // now thread safe for request()s in parallel
             msgUnit.getQosData().getClientProperties().remove("__responseTopicIdPrefix");
          }
      }
      if (createResponseTopic) {
    	  PublishReturnQos tempTopic = createTemporaryTopic(responseTopicId, destroyDelay, maxEntries);
          responseTopicId = tempTopic.getKeyOid();
      }
     
      try {
         // Send the request ...
         // "__jms:JMSReplyTo"
         msgUnit.getQosData().addClientProperty(Constants.addJmsPrefix(Constants.JMS_REPLY_TO, log), responseTopicId); // "__jms:JMSReplyTo"
         publish(msgUnit);
      
         // Access the reply ...
         MsgUnit[] msgs = receive("topic/"+responseTopicId, maxEntries, timeout, true);

         return msgs;
      }
      finally {
         if (createResponseTopic && responseTopicId.length() == 0) {
            // Clean up temporary topic ...
            EraseKey ek = new EraseKey(glob, responseTopicId);
            EraseQos eq = new EraseQos(glob);
            eq.setForceDestroy(true);
            erase(ek, eq);
         }
      }
   }

   public MsgUnit[] receive(String oid, int maxEntries, long timeout, boolean consumable) throws XmlBlasterException {
      if (oid == null || oid.length() == 0)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Please supply a valid oid to receive()");

      ContextNode node = ContextNode.valueOf(oid);
      if (node == null) {
         throw new IllegalArgumentException("Can't parse '" + oid + "' to a ContextNode");
      }
      if (node.isOfClass(ContextNode.SESSION_MARKER_TAG))
         node = node.getParent();
      if (node == null) {
         throw new IllegalArgumentException("Can't parse '" + oid + "' to a ContextNode");
      }
      if (node.isOfClass(ContextNode.TOPIC_MARKER_TAG))
         oid = "__cmd:"+oid+"/?historyQueueEntries"; // "__cmd:topic/hello/?historyQueueEntries"
      else if (node.isOfClass(ContextNode.SUBJECT_MARKER_TAG) && node.getChild(ContextNode.SESSION_MARKER_TAG, null) != null)
            oid = "__cmd:"+oid+"/?callbackQueueEntries"; // "__cmd:client/joe/session/1/?callbackQueueEntries";
      else if (node.isOfClass(ContextNode.SUBJECT_MARKER_TAG))
            oid = "__cmd:"+oid+"/?subjectQueueEntries"; // "__cmd:client/joe/?subjectQueueEntries"

      GetKey getKey = new GetKey(glob, oid);
      String qos = "<qos>" +
                   "<querySpec type='QueueQuery'>" +
                   "maxEntries="+maxEntries+"&amp;maxSize=-1&amp;consumable="+consumable+"&amp;waitingDelay="+timeout+
                   "</querySpec>" +
                   "</qos>";
      GetQos getQos = new GetQos(glob, glob.getQueryQosFactory().readObject(qos));
      MsgUnit[] msgs = get(getKey, getQos);
      if (log.isLoggable(Level.FINEST)) log.finest(getLogId()+"Got " + msgs.length + " reply :\n" + ((msgs.length>0)?msgs[0].toXml():""));
      return msgs;
   }

   
   private PublishReturnQos publishSingleChunk(MsgKeyData keyData, MsgQosData chunkQosData, byte[] buf, int length, boolean isLastChunk, long count, Exception ex) throws XmlBlasterException {
      MsgKeyData chunkKeyData = keyData;
      MsgUnit msg = new MsgUnit(chunkKeyData, buf, chunkQosData);
      if (isLastChunk || ex != null)
         chunkQosData.addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_EOF, log), true);
      chunkQosData.addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_SEQ, log), count);
      if (ex != null)
         msg.getQosData().addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_EX, log), ex.getMessage());
      return publish(msg);
   }
   
   public PublishReturnQos[] publishStream(InputStream is, MsgKeyData keyData, MsgQosData qosData, int maxBufSize, I_ReplaceContent contentReplacer) throws XmlBlasterException {
      String streamId = (getGlobal()).getId() + "-" + (new Timestamp()).getTimestamp();
      qosData.addClientProperty(Constants.addJmsPrefix(XBConnectionMetaData.JMSX_GROUP_ID, log), streamId);
      int bufSize = 0;
      String tmpKey = Constants.addJmsPrefix(XBConnectionMetaData.JMSX_MAX_CHUNK_SIZE, log); 
      if (qosData.getClientProperty(tmpKey) != null)
         bufSize = qosData.getClientProperty(tmpKey).getIntValue();
      if (bufSize > maxBufSize || bufSize == 0)
         bufSize = maxBufSize;
      long count = 0L;
      PublishReturnQos pubRetQos = null;
      byte[] buf = new byte[bufSize];
      try {
         while (true) {
            buf = new byte[bufSize];
            int offset = 0;
            int remainingLength = bufSize;
            int lengthRead = 0;
            while ((lengthRead = is.read(buf, offset, remainingLength)) != -1) {
               remainingLength -= lengthRead;
               offset += lengthRead;
               if (remainingLength == 0)
                  break;
            }
            int length = offset;
            // cut the buffer if shorter than maximum buffer size
            if (length < buf.length) {
               byte[] tmpBuf = buf;
               buf = new byte[length];
               for (int i=0; i<buf.length;i++)
                  buf[i] = tmpBuf[i];
            }
            
            // We do not need to clone the key since it will not change, but the qos must be cloned
            MsgQosData chunkQosData = (MsgQosData)qosData.clone();
            
            if (contentReplacer != null)
               buf = contentReplacer.replace(buf, chunkQosData.getClientProperties());
            boolean isLastChunk = buf.length < bufSize;
            pubRetQos = publishSingleChunk(keyData, chunkQosData, buf, length, isLastChunk, count, null);
            count++;
            if (length < bufSize)
               return new PublishReturnQos[] { pubRetQos };
         }            
      }
      catch (IOException ex) {
         if (count > 0)
            publishSingleChunk(keyData, qosData, buf, 0, true, count, ex);
         throw new XmlBlasterException(getGlobal(), ErrorCode.RESOURCE, "Sending Chunked message", "failed due to an IOException", ex);
      }
      catch (XmlBlasterException ex) {
         if (count > 0)
            publishSingleChunk(keyData, qosData, buf, 0, true, count, ex);
         throw ex;
      }
   }
   
   public Object getUserObject() {
      return userObject;
   }

   public void setUserObject(Object userObject) {
      this.userObject = userObject;
   }
}

