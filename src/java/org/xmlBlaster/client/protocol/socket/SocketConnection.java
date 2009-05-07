/*------------------------------------------------------------------------------
Name:      SocketConnection.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles connection to xmlBlaster with plain sockets
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import java.io.IOException;
import java.net.BindException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.I_XmlBlasterConnection;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;



/**
 * This driver establishes exactly one connection to xmlBlaster-Server and
 * uses this socket for asynchronous callbacks as well. This way we don't need
 * to setup a callbackserver.
 * <p />
 * This "SOCKET:" driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup:
 * <pre>
 * ClientProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.client.protocol.socket.SocketConnection
 * </pre>
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.client.protocol.socket.SocketConnection#usage()}
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class SocketConnection implements I_XmlBlasterConnection
{
   private String ME = "SocketConnection";
   private Global glob;
   private static Logger log = Logger.getLogger(SocketConnection.class.getName());
   /** The info object holding hostname and port on the other side */
   private SocketUrl socketUrl;
   /** The info object holding hostname and port on this side */
   private SocketUrl localSocketUrl;
   /** The socket connection to/from one client */
   protected Socket sock;
   /** SocketCallbackImpl listens on socket to receive callbacks */
   protected SocketCallbackImpl cbReceiver;
   /** The unique client sessionId */
   protected String sessionId;
   protected String loginName = "dummyLoginName";
   protected Address clientAddress;
   private I_CallbackExtended cbClient;
   private PluginInfo pluginInfo;
   /**
    * Setting by plugin configuration, see xmlBlaster.properties, for example
    * <br />
    * ClientProtocolPlugin[SOCKET_UDP][1.0]=org.xmlBlaster.client.protocol.socket.SocketConnection,useUdpForOneway=true
    */
   private boolean useUdpForOneway = false;

   /** Placeholder for the progess listener in case the registration happens before the cbReceiver has been registered */
   private I_ProgressListener tmpProgressListener;
   /** Cluster node re-uses conection from remote node */
   boolean useRemoteLoginAsTunnel;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter.
    */
   public SocketConnection() {
   }

   /**
    * Connect to xmlBlaster using plain socket with native message format.
    */
   public SocketConnection(Global glob) throws XmlBlasterException {
      init(glob, null);
   }

   /**
    * Connect to xmlBlaster using plain socket messaging.
    */
   public SocketConnection(Global glob, java.applet.Applet ap) throws XmlBlasterException {
      init(glob, null);
   }

   /**
    */
   public String getLoginName() {
      return this.loginName;
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;

      this.pluginInfo = pluginInfo;

      // String tmp = pluginInfo.getParameters().getProperty("useUdpForOneway", ""+this.useUdpForOneway);
      // this.useUdpForOneway = Boolean.valueOf(tmp).booleanValue();
      this.useUdpForOneway = this.glob.get("useUdpForOneWay", this.useUdpForOneway, null, pluginInfo);
      if (log.isLoggable(Level.FINER))
         log.finer("Entering init(useUdpForOneway=" + this.useUdpForOneway + ")");
      // Put this instance in the NameService, will be looked up by SocketCallbackImpl
      this.glob.addObjectEntry("org.xmlBlaster.client.protocol.socket.SocketConnection", this);
   }

   /**
    * Get the raw socket handle
    */
   public Socket getSocket() throws XmlBlasterException
   {
      if (this.sock == null) {
         if (log.isLoggable(Level.FINE)) log.fine("No socket connection available.");
         //Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "No plain SOCKET connection available.");
      }
      return this.sock;
   }

   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Connects to xmlBlaster with one socket connection.
    * @see I_XmlBlasterConnection#connectLowlevel(Address)
    */
   public void connectLowlevel(Address address) throws XmlBlasterException {
       if (isConnected())
         return;

      // TODO: USE address for configuration
      this.clientAddress = address;
      this.useRemoteLoginAsTunnel = this.clientAddress.getEnv("useRemoteLoginAsTunnel", false).getValue();
      // The cluster slave accepts publish(), subscribe() etc callbacks

      if (this.pluginInfo != null)
         this.clientAddress.setPluginInfoParameters(this.pluginInfo.getParameters());

      if (log.isLoggable(Level.FINER)) log.finer("Entering connectLowlevel(), connection with raw socket to server, plugin setting is: " + this.pluginInfo.dumpPluginParameters());

      this.socketUrl = new SocketUrl(glob, this.clientAddress);

      if (this.socketUrl.getPort() < 1) {
         String str = "Option dispatch/connection/plugin/socket/port set to " + this.socketUrl.getPort() +
                      ", socket client not started";
         log.info(str);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_ADDRESS, ME, str);
      }

      this.localSocketUrl = new SocketUrl(glob, this.clientAddress, true, -1);

      // SSL support
      boolean ssl = this.clientAddress.getEnv("SSL", false).getValue();
      if (log.isLoggable(Level.FINE)) log.fine(clientAddress.getEnvLookupKey("SSL") + "=" + ssl);
      
      try {
         if (this.useRemoteLoginAsTunnel) {
        	String entryKey = SocketExecutor.getGlobalKey(this.clientAddress.getSessionName());
            Object obj = glob.getObjectEntry(entryKey);
            if (obj != null && obj instanceof org.xmlBlaster.protocol.socket.HandleClient) {
               org.xmlBlaster.protocol.socket.HandleClient h = (org.xmlBlaster.protocol.socket.HandleClient)obj;
               this.sock = h.getSocket();
               // TODO: HandleClient.closeSocket() can set sock = null!
               if (this.sock != null) {
                  log.info(getLoginName() + " " + getType() + " entryKey=" + entryKey + " global.instanceId=" +glob.getInstanceId() + "-" + glob.hashCode() + (ssl ? " SSL" : "") +
                     " client is reusing existing SOCKET '"+this.sock.getInetAddress().getHostAddress() + ":" + this.sock.getPort()+ "' configured was '" +
                     this.socketUrl.getUrl() +
                     "', your configured local parameters are localHostname=" + this.localSocketUrl.getHostname() +
                     " on localPort=" + this.localSocketUrl.getPort() + " useUdpForOneway=" + this.useUdpForOneway +
                     "', callback address is '" + this.sock.getLocalAddress().getHostAddress() + ":" + this.sock.getLocalPort() + "'");
               }
               else {
                  log.severe(getLoginName() + " " + getType() + " " + getLocalSocketUrlStr()
                        + " Didn't expect null socket for sessionName="
                        + this.clientAddress.getSessionName() + ": Removing entryKey=" + entryKey + " global.instanceId=" +glob.getInstanceId() + "-" + glob.hashCode() + ": " + Global.getStackTraceAsString(null));
                  glob.removeObjectEntry(entryKey);
               }
            }
            else {
               // instance of dummy string: no HandleClient available, remote node has not yet connected
               String str = "Connection to xmlBlaster server failed, no established socket to reuse found entryKey=" + entryKey;
               if (log.isLoggable(Level.FINE)) log.fine(str);
               throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str);
            }
         }
         else {
            if (ssl) {
               this.sock = this.socketUrl.createSocketSSL(this.localSocketUrl, this.clientAddress);
            }
            else {
               if (this.localSocketUrl.isEnforced()) {
                  this.sock = new Socket(this.socketUrl.getInetAddress(), this.socketUrl.getPort(),
                                      this.localSocketUrl.getInetAddress(), this.localSocketUrl.getPort());
               }
               else {
                  if (log.isLoggable(Level.FINE)) log.fine("Trying socket connection to " + socketUrl.getUrl() + " ...");
                  this.sock = new Socket(this.socketUrl.getInetAddress(), this.socketUrl.getPort());
               }
            }
   
            if (this.localSocketUrl.isEnforced()) {
               log.info(getType() + (ssl ? " SSL" : "") +
                     " client connected to '" + this.socketUrl.getUrl() +
                     "', your configured local parameters are localHostname=" + this.localSocketUrl.getHostname() +
                     " on localPort=" + this.localSocketUrl.getPort() + " useUdpForOneway=" + this.useUdpForOneway +
                     "', callback address is '" + this.sock.getLocalAddress().getHostAddress() + ":" + this.sock.getLocalPort() + "'");
            }
            else {
               // SocketUrl constructor updates client address
               this.localSocketUrl = new SocketUrl(glob, this.sock.getLocalAddress().getHostAddress(), this.sock.getLocalPort());
               this.clientAddress.setRawAddress(this.socketUrl.getUrl());
               log.info(getType() + (ssl ? " SSL" : "") +
                     " client connected to '" + socketUrl.getUrl() +
                     "', callback address is '" + this.localSocketUrl.getUrl() +
                     "' useUdpForOneway=" + this.useUdpForOneway + " clientAddress='" + this.clientAddress.getRawAddress() + "'");
            }
         }

         // start the socket sender and callback thread here
         if (this.cbReceiver != null) { // only the first time, not on reconnect
            CallbackAddress cba = this.clientAddress.getCallbackAddress();
            if (cba == null) {
               log.severe("No callback address given " + Global.getStackTraceAsString(null));
               cba = new org.xmlBlaster.util.qos.address.CallbackAddress(glob);
            }
            this.cbReceiver.initialize(glob, getLoginName(), cba, this.cbClient);
         }
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (java.net.UnknownHostException e) {
         String str = "XmlBlaster server host is unknown, '-dispatch/connection/plugin/socket/hostname=<ip>': " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                                       "XmlBlaster server is unknown, '-dispatch/connection/plugin/socket/hostname=<ip>'", e);
      }
      catch (BindException e) { // If client host changed address: Cannot assign requested address
         String str = "Connection to xmlBlaster server failed local=" + this.localSocketUrl + " remote=" + this.socketUrl + ": " + e.toString();
         log.warning(str);
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str);
      }
      catch (java.net.SocketException e) { // ifconfig eth0 down: Network is unreachable
         String str = "Connection to xmlBlaster server failed local=" + this.localSocketUrl + " remote=" + this.socketUrl + ": " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str);
      }
      catch (java.io.IOException e) {
         String str = "Connection to xmlBlaster server failed local=" + this.localSocketUrl + " remote=" + this.socketUrl + ": " + e.toString();
         if (log.isLoggable(Level.FINE)) log.fine(str);
         //e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, str);
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         String str = "Socket client connection to '" + this.socketUrl.getUrl() + "' failed, try options '-dispatch/connection/plugin/socket/hostname <ip> -dispatch/connection/plugin/socket/port <port>' and check if the xmlBlaster server has loaded the socket driver in xmlBlaster.properties";
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, str, e);
      }

      if (log.isLoggable(Level.FINE)) log.fine("Created '" + getProtocol() + "' protocol plugin and connect to xmlBlaster server");
   }


   /**
    * Reset the driver on problems
    */
   public void resetConnection()
   {
      if (log.isLoggable(Level.FINE)) log.fine("SocketClient is re-initialized, no connection available");
      try {
         shutdown();
      }
      catch (XmlBlasterException ex) {
         log.severe("disconnect. Could not shutdown properly. " + ex.getMessage());
      }
   }


   /**
    * A string with the local address and port (the client side).
    * @return For example "localhost:66557"
    */
   public SocketUrl getLocalSocketUrl() {
      if (this.localSocketUrl == null) {
         // Happens if on client startup an xmlBlaster server is not available
         if (log.isLoggable(Level.FINE)) log.fine("Can't determine client address, no socket connection available");
         return null;
      }
      return this.localSocketUrl;
   }

   /**
    * A string with the local address and port (the client side).
    * 
    * @return For example "socket://myServer.com:7607", never null
    */
   public String getLocalSocketUrlStr() {
      SocketUrl url = this.localSocketUrl;
      if (url != null) {
         return url.getUrl();
      }
      return "";
   }

   /**
    * @see I_XmlBlasterConnection#setConnectReturnQos(ConnectReturnQos)
    */
   public void setConnectReturnQos(ConnectReturnQos connectReturnQos) {
      this.sessionId = connectReturnQos.getSecretSessionId();
      this.loginName = connectReturnQos.getSessionName().getLoginName();
      this.ME = "SocketConnection-"+loginName;
   }

   /**
    * Login to the server.
    * <p />
    * @param connectQos The encrypted connect QoS
    * @exception XmlBlasterException if login fails
    */
   public String connect(String connectQos) throws XmlBlasterException {
      if (connectQos == null)
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME+".connect()", "Please specify a valid QoS");
      if (log.isLoggable(Level.FINER)) log.finer("Entering connect");
      if (isConnected() && isLoggedIn()) {
         log.warning("You are already logged in, we try again: " + toXml());
         Thread.dumpStack();
         //log.warn(ME, "You are already logged in, no relogin possible.");
         //return "";
      }

      connectLowlevel(this.clientAddress);

      if (getCbReceiver() == null) {
         // SocketCallbackImpl.java must be instantiated first
         //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
         //      "Sorry, SOCKET callback handler is not available but is necessary if client connection is of type 'SOCKET', please do not mix 'SOCKET' with other protocols in the same client connection.");
         log.info("Creating default callback server type=" + getType());
         I_CallbackServer server = glob.getCbServerPluginManager().getPlugin(getType(), getVersion());
         // NOTE: This address should come from the client !!!
         org.xmlBlaster.util.qos.address.CallbackAddress cba = new org.xmlBlaster.util.qos.address.CallbackAddress(glob);
         // TODO: extract the real loginName from connectQos
         server.initialize(this.glob, getLoginName(), cba, this.cbClient);
         // NOTE: This happens only if the client has no callback configured, we create a faked one here (as the SOCKET plugin needs it)
      }

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.CONNECT, sessionId); // sessionId is usually null on login, on reconnect != null
         parser.setPluginConfig(this.pluginInfo);
         parser.addMessage(connectQos);
         return (String)getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         if (!(e instanceof IOException) && !(e instanceof java.net.ConnectException)) e.printStackTrace();
         if (log.isLoggable(Level.FINE)) log.fine(e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "login failed", e);
      }
   }

   /**
    * Returns the protocol type.
    * @return "SOCKET"
    */
   public final String getProtocol() {
      return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
   }

    /**
    * Does a logout and removes the callback server.
    * <p />
    * @param sessionId The client sessionId
    */
   public boolean disconnect(String qos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering logout/disconnect: id=" + sessionId);

      if (!isLoggedIn()) {
         log.warning("You are not logged in, no logout possible.");
         return false;
      }

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.DISCONNECT, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addMessage((qos==null)?"":qos);
         // We close first the callback thread, this could be a bit early ?
         getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE/*ONEWAY*/, SocketUrl.SOCKET_TCP);
         getCbReceiver().setRunning(false); // To avoid error messages as xmlBlaster closes the connection during disconnect()
         return true;
      }
      catch (XmlBlasterException e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e);
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "disconnect", e1);
      }
      finally {
       //  shutdown(); // the callback server
       //  sessionId = null;
      }
   }

   /**
    * Shut down the callback server.
    * Is called by logout()
    */
   public void shutdown() throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering shutdown of callback server");
      if (this.cbReceiver != null) {
         I_CallbackExtended cb = this.cbReceiver.getCbClient();
         if (cb != null)
            this.cbClient = cb; // remember for reconnects
         this.cbReceiver.shutdownSocket();
      }

      if (this.useRemoteLoginAsTunnel) { // we don't own the socket
         this.sock = null;
         return;
      }

      Socket sk = this.sock;
      if (sk != null) {
         try { sk.getInputStream().close();  } catch (IOException e) { log.fine("InputStream.close(): " + e.toString()); }
         try { sk.getOutputStream().close(); } catch (IOException e) { log.fine("OutputStream.close(): " + e.toString()); }
         try { sk.close(); this.sock=null;   } catch (IOException e) { log.warning("socket.close(): " + e.toString()); }
      }
   }

   /**
    * @return true if you are logged in
    */
   public final boolean isLoggedIn()
   {
      return this.sessionId != null;
   }

   /**
    * @return true if the socket connection is established
    */
   public final boolean isConnected()
   {
      return this.sock != null; // && cbReceiver != null
   }

   /**
    * Access handle of callback server.
    * <p />
    * Opens the socket connection if not logged in.
    */
   //public I_CallbackServer getCallbackServer() throws XmlBlasterException
   //{
   //   return getCbReceiver();
   //}

   /**
    * Called by SocketCallbackImpl on creation
    */
   final void registerCbReceiver(SocketCallbackImpl cbReceiver) {
      this.cbReceiver = cbReceiver;
      if (this.tmpProgressListener != null) {
         if (log.isLoggable(Level.FINE)) log.fine("The progressListener will be registered now since it could not register it before");
         this.cbReceiver.registerProgressListener(this.tmpProgressListener);
         this.tmpProgressListener = null;
      }
      if (this.cbReceiver != null && this.cbReceiver.getCbClient() != null) {
         this.cbClient = this.cbReceiver.getCbClient(); // remember for reconnects
      }
   }

   /**
    * Access handle of callback server.
    * <p />
    * Returns the valid SocketCallbackImpl:SocketExecutor, opens the socket connection if not logged in.
    */
   private final SocketExecutor getCbReceiver() {
      SocketCallbackImpl cbr = this.cbReceiver;
      if (cbr != null)
         return cbr.getSocketExecutor();
      return null;
   }

   /**
    * Enforced by I_XmlBlasterConnection interface (failsafe mode).
    * Subscribe to messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
   public final String subscribe(String xmlKey_literal, String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe(id=" + sessionId + ")");
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.SUBSCRIBE, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String)response; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.SUBSCRIBE.toString(), e1);
      }
   }

   /**
    * Unsubscribe from messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.unSubscribe.html">The interface.unSubscribe requirement</a>
    */
   public final String[] unSubscribe(String xmlKey_literal,
                                 String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe(): id=" + sessionId);
      if (log.isLoggable(Level.FINEST)) log.finest("Entering unSubscribe(): id=" + sessionId + " key='" + xmlKey_literal + "' qos='" + qos_literal + "'");

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UNSUBSCRIBE, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response;
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.UNSUBSCRIBE.toString(), e1);
      }
   }

   /**
    * Publish a message.
    * The normal publish is handled here like a publishArr
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final String publish(MsgUnitRaw msgUnit) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publish(): id=" + sessionId);

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addMessage(msgUnit);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         String[] arr = (String[])response; // return the QoS
         return arr[0]; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.PUBLISH.toString(), e1);
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final String[] publishArr(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishArr: id=" + sessionId);

      if (msgUnitArr == null) {
         if (log.isLoggable(Level.FINE)) log.fine("The argument of method publishArr() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".InvalidArguments",
                                       "The argument of method publishArr() are invalid");
      }
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addMessage(msgUnitArr);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response; // return the QoS
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "publishArr", e1);
      }
   }

   /**
    * Publish multiple messages in one sweep.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final void publishOneway(MsgUnitRaw[] msgUnitArr) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering publishOneway: id=" + sessionId);

      if (msgUnitArr == null) {
         if (log.isLoggable(Level.FINE)) log.fine("The argument of method publishOneway() are invalid");
         return;
      }

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PUBLISH_ONEWAY, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addMessage(msgUnitArr);
         getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.ONEWAY, this.useUdpForOneway);
      }
      catch (Throwable e) {
         if (log.isLoggable(Level.FINE)) log.fine("Sending of oneway message failed: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.PUBLISH_ONEWAY.toString(), e);
      }
   }

   /*
   public final String[] sendUpdate(MsgUnitRaw[] msgUnitArr)
      throws XmlBlasterException
      see HandleClient.java
   */


   /**
    * Delete messages.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.erase.html">The interface.erase requirement</a>
    */
   public final String[] erase(String xmlKey_literal, String qos_literal) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering erase() id=" + sessionId);

      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.ERASE, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (String[])response; // return the QoS TODO
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.ERASE.toString(), e1);
      }
   }

   /**
    * Synchronous access a message.
    * <p />
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.get.html">The interface.get requirement</a>
    */
   public final MsgUnitRaw[] get(String xmlKey_literal,
                                  String qos_literal) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering get() xmlKey=\n" + xmlKey_literal + ") ...");
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.GET, sessionId);
         parser.setPluginConfig(this.pluginInfo);
         parser.addKeyAndQos(xmlKey_literal, qos_literal);
         Object response = getCbReceiver().requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, SocketUrl.SOCKET_TCP);
         return (MsgUnitRaw[])response;
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, MethodName.GET.toString(), e1);
      }
   }

   /**
    * Check server.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl" target="others">CORBA xmlBlaster.idl</a>
    */
   public String ping(String qos) throws XmlBlasterException {
      SocketExecutor receiver = getCbReceiver();
      if (receiver == null) {
         //if (this.useRemoteLoginAsTunnel)
         //   throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "No connection tunnel");

         
         return Constants.RET_OK; // fake a return for ping on startup
         /*
         // SocketCallbackImpl.java must be instantiated first
         //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
         //      "Sorry, SOCKET callback handler is not available but is necessary if client connection is of type 'SOCKET', please do not mix 'SOCKET' with other protocols in the same client connection.");
         log.info(ME, "Creating default callback server type=" + getType());
         I_CallbackServer server = glob.getCbServerPluginManager().getPlugin(getType(), getVersion());
         server.initialize(this.glob, getLoginName(), null);
         // NOTE: This happens only if the client has no callback configured, we create a faked one here (as the SOCKET plugin needs it)
         */
      }
      return receiver.ping(qos);
   }

   /**
    * Dump of the state, remove in future.
    */
   public String toXml() throws XmlBlasterException
   {
      return toXml("");
   }

   /**
    * Dump of the state, remove in future.
    */
   public String toXml(String extraOffset) throws XmlBlasterException
   {
      if (this.sock == null) return "<noConnection />";
      else return "<connected/>";
   }

   /**
    * Register a listener for to receive information about the progress of incoming data.
    * Only one listener is supported, the last call overwrites older calls.
    * @param listener Your listener, pass 0 to unregister.
    * @return The previously registered listener or 0
    */
   public I_ProgressListener registerProgressListener(I_ProgressListener listener) {
      SocketExecutor cbRec = getCbReceiver();
      if (cbRec != null)
         return cbRec.registerProgressListener(listener);
      else {
         if (log.isLoggable(Level.FINE)) log.fine("The callback receiver is null, will be registered when the callback receiver is registered.");
         I_ProgressListener ret = this.tmpProgressListener;
         this.tmpProgressListener = listener;
         return ret;
      }
   }


   /**
    * Command line usage.
    * <p />
    *  <li>-dispatch/connection/plugin/socket/port
    *                      Specify a port number where xmlBlaster SOCKET server listens
    *                      Default is port "+DEFAULT_SERVER_PORT+", the port 0 switches this feature off</li>
    *  <li>-dispatch/connection/plugin/socket/hostname
    *                      Specify a hostname where the xmlBlaster web server runs.
    *                      Default is the localhost</li>
    *  <li>-dispatch/connection/plugin/socket/localPort
    *                      You can specify our client side port as well (usually you shouldn't)
    *                      Default is that the port is chosen by the operating system</li>
    *  <li>-dispatch/connection/plugin/socket/localHostname
    *                      Specify the hostname who we are. Makes sense for multi homed computers
    *                      Defaults to our hostname</li>
    *  <li>-dispatch/connection/plugin/socket/responseTimeout  How long to wait for a method invocation to return
    *                      Defaults to 'forever', the value to pass is milli seconds</li>
    *  <li>-dispatch/connection/plugin/socket/multiThreaded Use seperate threads per update() on client side [true]</li>
    *  <li>-dump[socket]   true switches on detailed SOCKET debugging [false]</li>
    * <p />
    * These variables may be set in xmlBlaster.properties as well.
    * Don't use the "-" prefix there.
    */
   public static String usage()
   {
      String text = "\n";
      text += "SocketConnection 'SOCKET' options:\n";
      text += "   -dispatch/connection/plugin/socket/port\n";
      text += "                       Specify a port number where xmlBlaster SOCKET server listens.\n";
      text += "                       Default is port "+SocketUrl.DEFAULT_SERVER_PORT+", the port 0 switches this feature off.\n";
      text += "   -dispatch/connection/plugin/socket/hostname\n";
      text += "                       Specify a hostname where the xmlBlaster web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -dispatch/connection/plugin/socket/localPort\n";
      text += "                       You can specify our client side port as well (usually you shouldn't)\n";
      text += "                       Default is that the port is chosen by the operating system.\n";
      text += "   -dispatch/connection/plugin/socket/localHostname\n";
      text += "                       Specify the hostname who we are. Makes sense for multi homed computers.\n";
      text += "                       Defaults to our hostname.\n";
      text += "   -dispatch/connection/plugin/socket/responseTimeout\n";
      text += "                       How long to wait for a method invocation to return.\n";
//    text += "                       The default is " +getDefaultResponseTimeout() + ".\n";
      text += "                       Defaults to 'forever', the value to pass is milli seconds.\n";
      text += "   -dispatch/connection/plugin/socket/multiThreaded\n";
      text += "                       Use seperate threads per update() on client side [true].\n";
      text += "   -dispatch/connection/plugin/socket/SSL\n";
      text += "                       True enables SSL support on server socket [false].\n";
      text += "   -dispatch/connection/plugin/socket/trustStore\n";
      text += "                       The path of your trusted keystore file. Use the java utility keytool.\n";
      text += "   -dispatch/connection/plugin/socket/trustStorePassword\n";
      text += "                       The password of your trusted keystore file.\n";
      text += "   -dispatch/connection/plugin/socket/compress/type\n";
      text += "                       Valid values are: '', '"+Constants.COMPRESS_ZLIB_STREAM+"', '"+Constants.COMPRESS_ZLIB+"' [].\n";
      text += "                       '' disables compression, '"+Constants.COMPRESS_ZLIB_STREAM+"' compresses whole stream.\n";
      text += "                       '"+Constants.COMPRESS_ZLIB+"' only compresses flushed chunks bigger than 'compress/minSize' bytes.\n";
      text += "   -dispatch/connection/plugin/socket/compress/minSize\n";
      text += "                       Compress message bigger than given bytes, see above.\n";
      text += "   -dump[socket]       true switches on detailed SOCKET debugging [false].\n";
      text += "\n";
      return text;
   }
}

