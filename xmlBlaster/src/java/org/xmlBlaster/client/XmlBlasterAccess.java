/*------------------------------------------------------------------------------
Name:      XmlBlasterAccess.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_ConnectionHandler;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import java.util.HashMap;

/**
 * <p>
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */
public final class XmlBlasterAccess extends AbstractCallbackExtended
                   implements I_XmlBlasterAccess, I_ConnectionHandler, I_ConnectionStatusListener
{
   private String ME = "XmlBlasterAccess";
   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   private String serverNodeId = "xmlBlaster";
   private ConnectQos connectQos;
   /** The return from connect() */
   private ConnectReturnQos connectReturnQos;
   /** Client side queue during connection failure */
   private I_Queue clientQueue;
   /** The dispatcher framework **/
   private DeliveryManager deliveryManager;
   /** The object handling message delivery problems */
   private I_MsgErrorHandler msgErrorHandler;
   /** Client side helper classes to load the authentication xml string */
   private I_ClientPlugin secPlgn;
   /** The callback server */
   private I_CallbackServer cbServer;
   /** Allow to cache updated messages for simulated synchronous access with get() */
   private BlasterCache updateCache;
   /** Handles the registered callback interfaces for given subscriptions. */
   private final UpdateDispatcher updateDispatcher;
   /** Used to callback the clients default update() method (as given on connect()) */
   private I_Callback updateListener;
   /** Is not null if the client wishes to be notified about connection state changes in fail safe operation */
   private I_ConnectionStateListener connectionListener;
   private boolean disconnectInProgress;


   /**
    * Create an xmlBlaster accessor. 
    * Please don't create directly but use the factory instead:
    * <pre>
    *   final Global glob = new Global(args);
    *   final I_XmlBlasterAccess xmlBlasterAccess = glob.getXmlBlasterAccess();
    * </pre>
    * @param glob Your environment handle or null to use the default Global.instance()
    */
   public XmlBlasterAccess(Global glob) {
      super((glob==null) ? Global.instance() : glob);
      //if (glob.wantsHelp()) {
      //   usage();
      //}
      setServerNodeId(super.glob.getId());
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
      super(new Global(args));
      //if (glob.wantsHelp()) {
      //   usage();
      //}
      setServerNodeId(super.glob.getId());
      this.updateDispatcher = new UpdateDispatcher(super.glob);
   }

   /**
    * Initialize and configure fail safe connection, with this the client library
    * automatically polls for the xmlBlaster server. 
    * @param address The configuration of the client connection
    * @param connectionListener null or your listener implementation on connection state changes (ALIVE | POLLING | DEAD)
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#registerConnectionListener(I_ConnectionStateListener)
    */
   public synchronized void registerConnectionListener(I_ConnectionStateListener connectionListener) {
      if (log.CALL) log.call(ME, "Initializing registering connectionListener");
      this.connectionListener = connectionListener;
   }

   /**
    * Login to xmlBlaster
    * <pre>
    *  ConnectQos qos = new ConnectQos(glob);
    *
    *  // Example how to configure fail safe settings
    *  Address addr = new Address(glob);
    *  addr.setDelay(2000L);
    *  addr.setRetries(-1);
    *  addr.setMaxMsg(2000);
    *  addr.setPingInterval(5000L);
    *  qos.addAddress(addr);
    *
    * </pre>
    * @param qos Your configuration desire
    * @param updateListener If not null a callback server will be created and 
    *        callback messages will be routed to your updateListener.update() method. 
    * @see org.xmlBlaster.client.I_XmlBlasterAccess#connect(ConnectQos, I_Callback)
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback updateListener) throws XmlBlasterException {
      
      this.connectQos = (qos==null) ? new ConnectQos(glob) : qos;

      this.updateListener = updateListener;

      initSecuritySettings(this.connectQos.getSecurityPluginType(), this.connectQos.getSecurityPluginVersion());

      this.ME = "XmlBlasterAccess-" + getId();

      try {
         ClientQueueProperty prop = this.connectQos.getClientQueueProperty();
         StorageId queueId = new StorageId("client", getId());
         this.clientQueue = glob.getQueuePluginManager().getPlugin(prop.getType(), prop.getVersion(), queueId,
                                                this.connectQos.getClientQueueProperty());

         this.msgErrorHandler = new ClientErrorHandler(glob, this);

         this.deliveryManager = new DeliveryManager(glob, this.msgErrorHandler,
                                 getSecurityPlugin(), this.clientQueue,
                                 this.connectQos.getAddresses());

         this.deliveryManager.addConnectionStatusListener(this);

         log.info(ME, "Switching to synchronous delivery mode ...");
         this.deliveryManager.trySyncMode(true);

         if (this.updateListener != null) { // Start a default callback server using same protocol
            createDefaultCbServer();
         }

         MsgQueueConnectEntry entry = new MsgQueueConnectEntry(this.glob, this.clientQueue.getStorageId(), this.connectQos);

         // Try to connect to xmlBlaster ...
         this.connectReturnQos = (ConnectReturnQos)queueMessage(entry);
      }
      catch (XmlBlasterException e) {
         shutdown(null, false, true, true);
         throw e;
      }
      catch (Throwable e) {
         shutdown(null, false, true, true);
         throw XmlBlasterException.convert(glob, ME, "Connection failed", e);
      }

      if (isAlive()) {
         log.info(ME, "Successful login as " + getId());
      }
      else {
         log.info(ME, "Login request as " + getId() + " is queued");
      }

      return this.connectReturnQos; // new ConnectReturnQos(glob, "");
   }

   public boolean isConnected() {
      return this.connectReturnQos != null;
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

      this.cbServer = initCbServer(getLoginName(), addr.getType(), addr.getVersion());

      addr.setAddress(this.cbServer.getCbAddress());
      addr.setType(this.cbServer.getCbProtocol());
      //addr.setVersion(this.cbServer.getVersion());
      //addr.setSecretSessionId(cbSessionId);
      prop.setCallbackAddress(addr);

      log.info(ME, "Callback settings: " + prop.getSettings());
   }

   /**
    * Create a new instance of the desired protocol driver like CORBA or RMI driver using the plugin loader. 
    * @param type  E.g. "IOR" or "RMI", if null we use the same protocol as our client access (corba is default).
    * @param version The version of the driver, e.g. "1.0"
    */
   public I_CallbackServer initCbServer(String loginName, String type, String version) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Using 'client.cbProtocol=" + type + "' to be used by " + getServerNodeId() + ", trying to create the callback server ...");
      I_CallbackServer server = glob.getCbServerPluginManager().getPlugin(type, version);
      server.initialize(this.glob, loginName, this);
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
         secPlgn = secPlgnMgr.getClientPlugin(secMechanism, secVersion);
         if (secMechanism != null)  // to avoid double logging for login()
            log.info(ME, "Loaded security plugin=" + secMechanism + " version=" + secVersion);
      }
      catch (Exception e) {
         log.error(ME, "Security plugin initialization failed. Reason: "+e.toString());
         secPlgn = null;
      }
   }

   public I_ClientPlugin getSecurityPlugin() {
      return secPlgn;
   }

   /**
    * Logout from the server.
    * <p />
    * Flushes pending publishOneway messages if any and destroys low level connection and callback server.
    * @see org.xmlBlaster.client.protocol.XmlBlasterConnection#disconnect(DisconnectQos, boolean, boolean, boolean)
    */
   public boolean disconnect(DisconnectQos qos) {
      return disconnect(qos, true, true, true);
   }

   /**
    * Logout from the server. 
    * <p>
    * Depending on your arguments, the callback server is removed as well, releasing all CORBA/RMI/XmlRpc threads.
    * Note that this kills the server ping thread as well (if in fail save mode)
    * </p>
    * @param qos The disconnect quality of service
    * @param flush Flushed pending publishOneway() messages if any
    * @param shutdown shutdown lowlevel connection as well (e.g. CORBA connection)
    * @param shutdownCb shutdown callback server as well (if any was established)
    * @return <code>true</code> successfully logged out<br />
    *         <code>false</code> failure on logout
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.disconnect.html">interface.disconnect requirement</a>
    */
   public synchronized boolean disconnect(DisconnectQos disconnectQos, boolean flush, boolean shutdown, boolean shutdownCb) {
      if (log.CALL) log.call(ME, "disconnect() ...");

      if (!isConnected()) {
         log.warn(ME, "You called disconnect() but you are are not logged in, we ignore it.");
         return false;
      }

      return shutdown(disconnectQos, flush, shutdown, shutdownCb);
   }

   private synchronized boolean shutdown(DisconnectQos disconnectQos, boolean flush, boolean shutdown, boolean shutdownCb) {
      if (this.disconnectInProgress) {
         log.warn(ME, "Calling disconnect again is ignored, you are in shutdown progress already");
         return false;
      }

      this.disconnectInProgress = true;

      if (isConnected()) {
         if (disconnectQos == null)
            disconnectQos = new DisconnectQos(glob);

         long remainingEntries = this.deliveryManager.getQueue().getNumOfEntries();
         if (remainingEntries > 0)
            log.warn(ME, "You called disconnect(). Please note that there are " + remainingEntries + " unsent invocations/messages in the queue");

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
               log.warn(ME+".logout", "Couldn't unsubscribe '" + subscriptionId + "' : " + e.getMessage());
            }
         }
         this.updateDispatcher.clear();

         if (this.clientQueue != null) {
            try {
               MsgQueueDisconnectEntry entry = new MsgQueueDisconnectEntry(this.glob, this.clientQueue.getStorageId(), disconnectQos);
               queueMessage(entry);
               log.info(ME, "Successful disconnect from " + getServerNodeId());
            } catch(Throwable e) {
               e.printStackTrace();
               log.warn(ME+".disconnect()", e.toString());
            }
         }
      }

      if (this.clientQueue != null) {
         this.clientQueue.clear();
      }

      if (shutdown) {
         if (this.deliveryManager != null) {
            this.deliveryManager.shutdown();
            this.deliveryManager = null;
         }
         if (this.clientQueue != null) {
            this.clientQueue = null;
         }
      }

      if (shutdownCb && this.cbServer != null) {
         try {
            this.cbServer.shutdown();
         } catch (Throwable e) {
            e.printStackTrace();
            log.warn(ME+".disconnect()", e.toString());
         }
      }

      this.connectReturnQos = null;
      this.disconnectInProgress = false;
      this.msgErrorHandler = null;
      this.updateListener = null;
      return true;
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
    * @return e.g. "/node/heron/client/joe/3"
    */
   public String getId() {
      SessionName sessionName = getSessionName();
      return (sessionName == null) ? "UNKNOWN_SESSION" : sessionName.getAbsoluteName();
   }

   /**
    * The public session ID of this login session. 
    */
   public SessionName getSessionName() {
      if (this.connectReturnQos != null)
         return this.connectReturnQos.getSessionName();
      if (this.connectQos != null) {
         return this.connectQos.getSessionName();
      }
      return null;
   }

   /**
    * Access the login name.
    * @return your login name or null if you are not logged in
    */
   public String getLoginName() {
      //if (this.connectReturnQos != null)
      //   return this.connectReturnQos.getLoginName();
      //try {
         if (connectQos != null) {
            String nm = connectQos.getSecurityQos().getUserId();
            if (nm != null && nm.length() > 0)
               return nm;
         }
      //}
      //catch (XmlBlasterException e) {}
      return glob.getId(); // "client?";
   }

   /**
    * Allows to set the node name for nicer logging.
    */
   public void setServerNodeId(String nodeId) {
      this.serverNodeId = nodeId;
   }

   /**
    * The cluster node id (name) to which we want to connect.
    * <p />
    * Needed only for nicer logging when running in a cluster.<br />
    * Is configurable with "-server.node.id golan"
    * @return e.g. "golan", defaults to "xmlBlaster"
    */
   public String getServerNodeId() {
      return this.serverNodeId;
   }

   /**
    * Put the given message entry into the queue
    */
   private Object queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      try {
         this.clientQueue.put(entry, I_Queue.USE_PUT_INTERCEPTOR);
         if (log.TRACE) log.trace(ME, "Sent one message");
         return entry.getReturnObj();
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME, e.toString());
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,null,null,e);
         //msgErrorHandler.handleError(new MsgErrorInfo(glob, entry, xmlBlasterException));
         throw xmlBlasterException; // internal errors or not in fail save mode: throw back to client
      }
   }

   private void queueMessage(MsgQueueEntry[] entries) throws XmlBlasterException {
      try {
         this.clientQueue.put(entries, I_Queue.USE_PUT_INTERCEPTOR);
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME, e.toString());
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,null,null,e);
         // this.msgErrorHandler.handleError(new MsgErrorInfo(glob, entries, xmlBlasterException));
         throw xmlBlasterException; // internal errors or not in fail save mode: throw back to client
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
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueSubscribeEntry entry  = new MsgQueueSubscribeEntry(glob,
                                      this.clientQueue.getStorageId(), subscribeKey, subscribeQos);
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
         this.updateDispatcher.addCallback(subscribeReturnQos.getSubscriptionId(), cb);
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
    * @see I_XmlBlasterAccess#get(GetKey, GetQos)
    */
   public MsgUnit[] get(GetKey getKey, GetQos getQos) throws XmlBlasterException {
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueGetEntry entry  = new MsgQueueGetEntry(glob,
                                      this.clientQueue.getStorageId(), getKey, getQos);
      return (MsgUnit[])queueMessage(entry);
   }

   /**
    * @see I_XmlBlasterAccess#unSubscribe(UnSubscribeKey, UnSubscribeQos)
    */
   public UnSubscribeReturnQos[] unSubscribe(UnSubscribeKey unSubscribeKey, UnSubscribeQos unSubscribeQos) throws XmlBlasterException {
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueUnSubscribeEntry entry  = new MsgQueueUnSubscribeEntry(glob,
                                      this.clientQueue.getStorageId(), unSubscribeKey, unSubscribeQos);
      UnSubscribeReturnQos[] arr = (UnSubscribeReturnQos[])queueMessage(entry);
      this.updateDispatcher.removeCallback(unSubscribeKey.getOid());
      return arr;
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
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueuePublishEntry entry  = new MsgQueuePublishEntry(glob, msgUnit, this.clientQueue.getStorageId());
      return (PublishReturnQos)queueMessage(entry);
   }

   /**
    * @see I_XmlBlasterAccess#publishOneway(MsgUnit[])
    */
   public void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException {
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
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      log.warn(ME, "Publishing arrays is not atomic implemented - TODO");
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
      if (!isConnected()) throw new XmlBlasterException(glob, ErrorCode.USER_NOT_CONNECTED, ME);
      MsgQueueEraseEntry entry  = new MsgQueueEraseEntry(glob,
                                      this.clientQueue.getStorageId(), eraseKey, eraseQos);
      return (EraseReturnQos[])queueMessage(entry);
   }

   /**
    * @see I_XmlBlasterAccess#erase(EraseKey, EraseQos)
    */
   public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      return erase(new EraseKey(glob, glob.getQueryKeyFactory().readObject(xmlKey)),
                       new EraseQos(glob, glob.getQueryQosFactory().readObject(qos)) );
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering update(" + ((updateCache != null) ? "using updateCache" : "no updateCache") + ") ...");

      if( updateCache != null ) {
         if (updateCache.update(updateQos.getSubscriptionId(), updateKey, content, updateQos))
            return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
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
         log.error(ME, "Ignoring unexpected update message: " + updateKey.toXml() + "" + updateQos.toXml());
      }

      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Call by DeliveryManager on connection state transition. 
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public void toAlive(DeliveryManager deliveryManager, ConnectionStateEnum oldState) {
      if (log.CALL) log.call(ME, "Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE);
      if (this.connectionListener != null) {
         this.connectionListener.reachedAlive(oldState, this);
      }
   }

   /**
    * Call by DeliveryManager on connection state transition. 
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public void toPolling(DeliveryManager deliveryManager, ConnectionStateEnum oldState) {
      if (log.CALL) log.call(ME, "Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
      if (this.connectionListener != null) {
         this.connectionListener.reachedPolling(oldState, this);
      }
   }

   /**
    * Call by DeliveryManager on connection state transition. 
    * <p>Enforced by interface I_ConnectionStatusListener</p>
    */
   public void toDead(DeliveryManager deliveryManager, ConnectionStateEnum oldState, String errorText) {
      if (log.CALL) log.call(ME, "Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
      if (this.connectionListener != null) {
         this.connectionListener.reachedDead(oldState, this);
      }
   }

   /**
    * Access the environment settings of this connection. 
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The global handle (like a stack with local variables for this connection)
    */
   public Global getGlobal() {
      return this.glob;
   }

   /**
    * Flushes all entries in the queue, i.e. the entries of the queue are sent to xmlBlaster. 
    * <p>
    * This method blocks until all entries in the queue have been sent.
    * </p>
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The number of sent messages. <br />
    *         If the queue is empty or NULL, then 0 is returned.<br />
    *         If the state is in POLLING or DEAD, then -1 is returned.<br />
    */
   public long flushQueue() throws XmlBlasterException {
      log.error(ME, "flushQueue() is not implemented");
      return 0;
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return The queue used to store tailback messages. 
    */
   public I_Queue getQueue() {
      return this.clientQueue;
   }

   /**
    * You can activate this mode by setting retries != 0
    * in the Address object of ConnectQos. 
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return Returns true if the connection is in failsafe mode
    */
   public boolean isFailSafe() {
      if (this.deliveryManager == null) return false;
      AddressBase addr = this.deliveryManager.getDeliveryConnectionsHandler().getAliveAddress();
      if (addr != null && addr.getRetries() != 0) return true;
      return false;
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if the connection to xmlBlaster is operational
    */
   public boolean isAlive() {
      if (!isConnected()) return false;
      return this.deliveryManager.getDeliveryConnectionsHandler().isAlive();
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if we are polling for the server
    */
   public boolean isPolling() {
      if (!isConnected()) return false;
      return this.deliveryManager.getDeliveryConnectionsHandler().isPolling();
   }

   /**
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return true if we have definitely lost the connection to xmlBlaster and gave up
    */
   public boolean isDead() {
      if (!isConnected()) return false;
      return this.deliveryManager.getDeliveryConnectionsHandler().isDead();
   }

   /**
    * Access the returned QoS of a connect() call. 
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return Can be null if not connected
    */
   public ConnectReturnQos getConnectReturnQos() {
      return this.connectReturnQos;
   }

   /**
    * Access the current ConnectQos
    * <p>Enforced by interface I_ConnectionHandler</p>
    * @return Can be null if not connected
    */
   public ConnectQos getConnectQos() {
      return this.connectQos;
   }

   /**
    * Command line usage.
    */
   public static String usage(Global glob) {
      glob = (glob == null) ? Global.instance() : glob;
      StringBuffer sb = new StringBuffer(4096);
      sb.append("\n");
      sb.append("Choose a connection protocol:\n");
      sb.append("   -client.protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XML-RPC'.\n");
      sb.append("                       Current setting is '" + glob.getProperty().get("client.protocol", "IOR") + "'. See below for protocol settings.\n");
      sb.append("                       Example: java MyApp -client.protocol RMI -rmi.hostname 192.168.10.34\n");
      sb.append("\n");
      sb.append("Security features:\n");
      sb.append("   -Security.Client.DefaultPlugin \"gui,1.0\"\n");
      sb.append("                       Force the given authentication schema, here the GUI is enforced\n");
      sb.append("                       Clients can overwrite this with ConnectQos.java\n");
      sb.append(new org.xmlBlaster.client.qos.ConnectQos(glob).usage());
      sb.append(new org.xmlBlaster.util.qos.address.Address(glob).usage());
      sb.append(new org.xmlBlaster.util.qos.storage.ClientQueueProperty(glob,null).usage());
      sb.append(new org.xmlBlaster.util.qos.address.CallbackAddress(glob).usage());
      sb.append(new org.xmlBlaster.util.qos.storage.CbQueueProperty(glob,null,null).usage());
      sb.append(org.xmlBlaster.client.protocol.socket.SocketConnection.usage());
      sb.append(org.xmlBlaster.client.protocol.corba.CorbaConnection.usage());
      sb.append(org.xmlBlaster.client.protocol.rmi.RmiConnection.usage());
      sb.append(org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection.usage());
      //sb.append(org.xmlBlaster.util.Global.instance().usage()); // for LogChannel help
      return sb.toString();
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

      sb.append(offset).append("<XmlBlasterAccess id='").append(this.getId()).append("'>");
      sb.append(offset).append(" <connected>").append(isConnected()).append("</connected>");
      if (isAlive()) {
      }
      sb.append(offset).append("</XmlBlasterAccess>");

      return sb.toString();
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.XmlBlasterAccess
    */
   public static void main( String[] args ) {
      try {
         final String ME = "XmlBlasterAccess-Test";
         final Global glob = new Global(args);
         final LogChannel log = glob.getLog("client");
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
            public boolean reachedAlive(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
               log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.ALIVE + " with " + connectionHandler.getQueue().getNumOfEntries() + " queue entries pending");
               return true;
            }
            public void reachedPolling(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
               log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
            }
            public void reachedDead(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
               log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
            }
         });

         ConnectReturnQos connectReturnQos = xmlBlasterAccess.connect(null, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(ME, "UPDATE: Receiving asynchronous callback message " + updateKey.toXml() + "\n" + updateQos.toXml());
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates
         if (xmlBlasterAccess.isAlive()) {
            log.info("", "Successfully connected to xmlBlaster");
         }
         else {
            log.info("", "We continue in fail safe mode: " + connectReturnQos.toXml());
         }

         {
            log.info(ME, "Hit a key to subscribe on topic " + oid);
            try { System.in.read(); } catch(java.io.IOException e) {}
            SubscribeKey sk = new SubscribeKey(glob, oid);
            SubscribeQos sq = new SubscribeQos(glob);
            SubscribeReturnQos subRet = xmlBlasterAccess.subscribe(sk, sq);
            log.info(ME, "Subscribed for " + sk.toXml() + "\n" + sq.toXml() + " return:\n" + subRet.toXml());

            log.info(ME, "Hit a key to publish '" + oid + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
            MsgUnit msgUnit = new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>");
            PublishReturnQos publishReturnQos = xmlBlasterAccess.publish(msgUnit);
            log.info(ME, "Successfully published message to xmlBlaster, msg=" + msgUnit.toXml() + "\n returned QoS=" + publishReturnQos.toXml());
            try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // wait for update

            {
               log.info(ME, "Hit a key to 3 times publishOneway '" + oid + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
               MsgUnit[] msgUnitArr = new MsgUnit[] {
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>")
               };
               xmlBlasterAccess.publishOneway(msgUnitArr);
               log.info(ME, "Successfully published " + msgUnitArr.length + " messages oneway");
               try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // wait for update
            }

            {
               log.info(ME, "Hit a key to 3 times publishArr '" + oid + "'");
               try { System.in.read(); } catch(java.io.IOException e) {}
               MsgUnit[] msgUnitArr = new MsgUnit[] {
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
               new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>"),
                  new MsgUnit(glob, "<key oid='"+oid+"'/>", "Hi".getBytes(), "<qos><persistent>true</persistent></qos>")
               };   
               PublishReturnQos[] retArr = xmlBlasterAccess.publishArr(msgUnitArr);
               log.info(ME, "Successfully published " + retArr.length + " acknowledged messages");
               try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // wait for update
            }

            log.info(ME, "Hit a key to get '" + oid + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
            GetKey gk = new GetKey(glob, oid);
            GetQos gq = new GetQos(glob);
            MsgUnit[] msgs = xmlBlasterAccess.get(gk, gq);
            log.info(ME, "Successfully got message from xmlBlaster, msg=" + msgs[0].toXml());

            log.info(ME, "Hit a key to unSubscribe on topic '" + oid + "' and '" + subRet.getSubscriptionId() + "'");
            try { System.in.read(); } catch(java.io.IOException e) {}
            UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
            UnSubscribeQos uq = new UnSubscribeQos(glob);
            UnSubscribeReturnQos[] unSubRet = xmlBlasterAccess.unSubscribe(uk, uq);
            log.info(ME, "UnSubscribed for " + uk.toXml() + "\n" + uq.toXml() + " return:\n" + unSubRet[0].toXml());

            log.info(ME, "Hit a key to erase on topic " + oid);
            try { System.in.read(); } catch(java.io.IOException e) {}
            EraseKey ek = new EraseKey(glob, oid);
            EraseQos eq = new EraseQos(glob);
            EraseReturnQos[] er = xmlBlasterAccess.erase(ek, eq);
            log.info(ME, "Erased for " + ek.toXml() + "\n" + eq.toXml() + " return:\n" + er[0].toXml());
         }

         int numPublish = 10;
         for (int ii=0; ii<numPublish; ii++) {
            log.info(ME, "Hit a key to publish #" + (ii+1) + "/" + numPublish);
            try { System.in.read(); } catch(java.io.IOException e) {}

            MsgUnit msgUnit = new MsgUnit(glob, "<key oid=''/>", ("Hi #"+(ii+1)).getBytes(), "<qos><persistent>true</persistent></qos>");
            PublishReturnQos publishReturnQos = xmlBlasterAccess.publish(msgUnit);
            log.info(ME, "Successfully published message #" + (ii+1) + " to xmlBlaster, msg=" + msgUnit.toXml() + "\n returned QoS=" + publishReturnQos.toXml());
         }

         log.info(ME, "Hit a key to disconnect ...");
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
}
