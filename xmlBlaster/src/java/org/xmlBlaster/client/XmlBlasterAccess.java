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
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.MsgUnit;

import java.util.HashMap;

/**
 * <p>
 * The interface I_CallbackRaw/I_Callback/I_CallbackExtenden are enforced by AbstractCallbackExtended
 * is for the InvocationRecorder to playback locally queued messages and for the protocol drivers.
 * </p>
 */
public final class XmlBlasterAccess extends AbstractCallbackExtended implements I_XmlBlaster
{
   private String ME = "XmlBlasterAccess";
   /** The cluster node id (name) to which we want to connect, needed for nicer logging, can be null */
   private String serverNodeId = "xmlBlaster";
   private ConnectQos connectQos;
   /** The return from connect() */
   private ConnectReturnQos connectReturnQos;
   /** The client queue configuration */
   private QueueProperty queueProperty;
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
   private I_Callback updateClient;

   /**
    * Create an xmlBlaster accessor. 
    * @param glob Your environment handle or null to use the default Global.instance()
    */
   public XmlBlasterAccess(Global glob) {
      super((glob==null) ? Global.instance() : glob);
      if (glob.wantsHelp()) {
         usage();
      }
      setServerNodeId(super.glob.getId());
      this.updateDispatcher = new UpdateDispatcher(super.glob);
   }

   /**
    * Login to xmlBlaster
    * @param qos Your configuration desire
    * @param client If not null callback messages will be routed to client.update()
    */
   public ConnectReturnQos connect(ConnectQos qos, I_Callback client) throws XmlBlasterException {
      
      this.connectQos = (qos==null) ? new ConnectQos(glob) : qos;

      initSecuritySettings(this.connectQos.getSecurityPluginType(), this.connectQos.getSecurityPluginVersion());

      this.ME = "XmlBlasterAccess-" + getId();

      String typeVersion = glob.getProperty().get("queue.defaultPlugin", "CACHE,1.0");
      StorageId queueId = new StorageId("client", getId());
      this.clientQueue = glob.getQueuePluginManager().getPlugin(typeVersion, queueId,
                                             this.connectQos.getClientQueueProperty());

      this.msgErrorHandler = new ClientErrorHandler(glob, this);

      this.deliveryManager = new DeliveryManager(glob, this.msgErrorHandler,
                              getSecurityPlugin(), this.clientQueue,
                              this.connectQos.getAddresses());

      log.info(ME, "Switching to synchronous delivery mode ...");
      this.deliveryManager.switchToSyncMode();

      if (client != null) { // Start a default callback server using same protocol
         createDefaultCbServer();
      }

      MsgQueueConnectEntry entry = new MsgQueueConnectEntry(this.glob, this.clientQueue.getStorageId(), this.connectQos);

      // Try to connect to xmlBlaster ...
      this.connectReturnQos = (ConnectReturnQos)queueMessage(entry);

      log.info(ME, "Successful login as " + getId());

      return this.connectReturnQos; // new ConnectReturnQos(glob, "");
   }

   /**
    * Todo: 
   public void toAlive(ConnectReturnQos ret) {
      this.connectReturnQos = ret;
      this.connectQos.setSessionId(this.connectReturnQos.getSessionId());
      this.connectQos.setPublicSessionId(this.connectReturnQos.getSessionName());
         if (isReconnectPolling)
            clientProblemCallback.reConnected();
   }
   */

   /**
    * Extracts address data from ConnectQos (or adds default if missing)
    * and instantiate a callback server as specified in ConnectQos
    */
   private void createDefaultCbServer() throws XmlBlasterException {
      CbQueueProperty prop = connectQos.getCbQueueProperty(); // Creates a default property for us if none is available
      CallbackAddress addr = prop.getCurrentCallbackAddress(); // may return null
      if (addr == null)
         addr = new CallbackAddress(glob);

      this.cbServer = initCbServer(getLoginName(), addr.getType(), addr.getVersion());

      addr.setAddress(this.cbServer.getCbAddress());
      addr.setType(this.cbServer.getCbProtocol());
      //addr.setVersion(this.cbServer.getVersion());
      //addr.setSessionId(cbSessionId);
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
   public synchronized boolean disconnect(DisconnectQos qos, boolean flush, boolean shutdown, boolean shutdownCb) {
      /*
      try {
         MsgQueueDisconnectEntry entry = new MsgQueueDisconnectEntry(this.glob, this.clientQueue, qos);
         queueMessage(entry);
         log.info(ME, "Disconnected");
         return true;
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }
      */
      log.error(ME, "disconnect not implemented");
      return false;
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
   public final Object queueMessage(MsgQueueEntry entry) throws XmlBlasterException {
      try {
         Object ret = clientQueue.put(entry, false);
         if (log.TRACE) log.trace(ME, "Sent one message");
         deliveryManager.notifyAboutNewEntry();
         return ret;
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME, e.toString());
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,null,null,e);
         //msgErrorHandler.handleError(new MsgErrorInfo(glob, entry, xmlBlasterException));
         throw xmlBlasterException; // internal errors or not in fail save mode: throw back to client
      }
   }

   /**
    * Put the given message entry into the queue
    */
   public final Object[] queueMessage(MsgQueueEntry[] entries) throws XmlBlasterException {
      try {
         Object[] ret = this.clientQueue.put(entries, false);
         this.deliveryManager.notifyAboutNewEntry();
         return ret;
      }
      catch (Throwable e) {
         if (log.TRACE) log.trace(ME, e.toString());
         XmlBlasterException xmlBlasterException = XmlBlasterException.convert(glob,null,null,e);
         // this.msgErrorHandler.handleError(new MsgErrorInfo(glob, entries, xmlBlasterException));
         throw xmlBlasterException; // internal errors or not in fail save mode: throw back to client
      }
   }

   public SubscribeReturnQos subscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   public MsgUnit[] get(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   public UnSubscribeReturnQos[] unSubscribe(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   public PublishReturnQos publish(MsgUnit msgUnit) throws XmlBlasterException {
      return (PublishReturnQos)queueMessage(new MsgQueuePublishEntry(glob, msgUnit, this.clientQueue.getStorageId()));
   }

   public void publishOneway(org.xmlBlaster.util.MsgUnit [] msgUnitArr) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   // rename to publish()
   public PublishReturnQos[] publishArr(org.xmlBlaster.util.MsgUnit[] msgUnitArr) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   public EraseReturnQos[] erase(java.lang.String xmlKey, java.lang.String qos) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "not implemented!");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public final String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering update(" + ((updateCache != null) ? "using updateCache" : "no updateCache") + ") ...");

      if( updateCache != null ) {
         if (updateCache.update(updateQos.getSubscriptionId(), updateKey, content, updateQos))
            return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
      }

      I_Callback cb = updateDispatcher.getCallback(updateQos.getSubscriptionId());

      if (cb != null) {
         // If a special callback was specified for this subscription:
         return cb.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
      }
      else if (updateClient != null) {
         // If a general callback was specified on login:
         return updateClient.update(cbSessionId, updateKey, content, updateQos); // deliver the update to our client
      }
      else {
         log.error(ME, "Ignoring unexpected update message: " + updateKey.toXml() + "" + updateQos.toXml());
      }

      return Constants.RET_OK; // "<qos><state id='OK'/></qos>";
   }

   /**
    * Command line usage.
    */
   public static void usage() {
      Global glob = Global.instance();
      String text = "\n";
      text += "Choose a connection protocol:\n";
      text += "   -client.protocol    Specify a protocol to talk with xmlBlaster, 'SOCKET' or 'IOR' or 'RMI' or 'SOAP' or 'XML-RPC'.\n";
      text += "                       Current setting is '" + glob.getProperty().get("client.protocol", "IOR") + "'. See below for protocol settings.\n";
      text += "                       Example: java MyApp -client.protocol RMI -rmi.hostname 192.168.10.34\n";
      text += "\n";
      text += "Security features:\n";
      text += "   -Security.Client.DefaultPlugin \"gui,1.0\"\n";
      text += "                       Force the given authentication schema, here the GUI is enforced\n";
      text += "                       Clients can overwrite this with ConnectQos.java\n";

      LogChannel log = glob.getLog(null);
      log.plain("",text);
      //try {
         log.plain("",new org.xmlBlaster.client.qos.ConnectQos(glob).usage());
      //} catch (XmlBlasterException e) {}
      log.plain("",new org.xmlBlaster.util.qos.address.Address(glob).usage());
      log.plain("",new org.xmlBlaster.util.qos.storage.QueueProperty(glob,null).usage());
      log.plain("",new org.xmlBlaster.util.qos.address.CallbackAddress(glob).usage());
      log.plain("",new org.xmlBlaster.util.qos.storage.CbQueueProperty(glob,null,null).usage());
      log.plain("",org.xmlBlaster.client.protocol.socket.SocketConnection.usage());
      log.plain("",org.xmlBlaster.client.protocol.corba.CorbaConnection.usage());
      log.plain("",org.xmlBlaster.client.protocol.rmi.RmiConnection.usage());
      log.plain("",org.xmlBlaster.client.protocol.xmlrpc.XmlRpcConnection.usage());
      log.plain("",org.xmlBlaster.util.Global.instance().usage()); // for LogChannel help
   }

   /**
    *  For testing invoke: java org.xmlBlaster.client.XmlBlasterAccess
    */
   public static void main( String[] args ) {
      try {
         Global glob = new Global(args);
         LogChannel log = glob.getLog("client");
         XmlBlasterAccess xmlBlasterAccess = new XmlBlasterAccess(glob);
         xmlBlasterAccess.connect(null, null);
         log.info("", "Successfully connect to xmlBlaster");
         MsgUnit msgUnit = new MsgUnit(glob, "<key oid='HelloWorld'/>", "Hi".getBytes(), "<qos/>");
         PublishReturnQos publishReturnQos = xmlBlasterAccess.publish(msgUnit);
         log.info("", "Successfully published a message to xmlBlaster");
         log.info("", "Sleeping");
         try { Thread.currentThread().sleep(10000000L); } catch( InterruptedException i) {}
      }
      catch (XmlBlasterException xmlBlasterException) {
         System.out.println("Test failed: " + xmlBlasterException.getMessage());
      }
      System.exit(0);
   }
}
