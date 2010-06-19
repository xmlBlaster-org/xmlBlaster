/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.socket.CallbackSocketDriver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.MsgInfo;


/**
 * Used for client to receive xmlBlaster callbacks over plain sockets.
 * <p />
 * One instance of this for each client, as a separate thread blocking
 * on the socket input stream waiting for messages from xmlBlaster.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see org.xmlBlaster.util.xbformat.MsgInfo
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class SocketCallbackImpl extends SocketExecutor implements Runnable, I_CallbackServer
{
   private String ME = "SocketCallbackImpl";
   private Global glob;
   private static Logger log = Logger.getLogger(SocketCallbackImpl.class.getName());
   /** The connection manager 'singleton' */
   private SocketConnection sockCon;
   /** A unique name for this client socket */
   private SocketUrl socketUrl;
   private CallbackAddress callbackAddress;
   private PluginInfo pluginInfo;
   /** The socket connection to/from one client */
   protected Socket sock;

   /** Stop the thread */
   private boolean threadRunning = false;

   /** For cluster environment only */
   private boolean useRemoteLoginAsTunnel;
   private SocketExecutor remoteLoginAsTunnelSocketExecutor;
   private boolean acceptRemoteLoginAsTunnel;
   private String secretSessionId; // only for acceptRemoteLoginAsTunnel
   
   private I_Authenticate authenticateCore;
   
   private Thread callbackListenerThread;

   /**
    * Called by plugin loader which calls init(Global, PluginInfo) thereafter.
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    * <p>
    * After polling -> alive NO new instance is created, but only initialize() is called
    */
   public SocketCallbackImpl() {
      if (log.isLoggable(Level.FINEST)) log.finest("ctor");
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
   }
   
   public void setRunning(boolean run) {
      super.setRunning(run);
      this.threadRunning = run;
   }
   
   public SocketExecutor getSocketExecutor() {
      return (this.useRemoteLoginAsTunnel && this.remoteLoginAsTunnelSocketExecutor != null) ? this.remoteLoginAsTunnelSocketExecutor : this;
   }

   /**
    * Initialize and start the callback server
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    * <p>
    * Same SocketCallback instance is called several times by SocketConnection.connectLowLevel() (on polling->alive)
    */
   public synchronized final void initialize(Global glob, String loginName,
                            CallbackAddress callbackAddress, I_CallbackExtended cbClient) throws XmlBlasterException {
      this.glob = (glob == null) ? Global.instance() : glob;

      this.ME = "SocketCallbackImpl-" + loginName;
      this.callbackAddress = callbackAddress;
      if (this.pluginInfo != null)
         this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
      setLoginName(loginName);
      
      // The cluster slave accepts publish(), subscribe() etc callbacks
      this.acceptRemoteLoginAsTunnel = this.callbackAddress.getEnv("acceptRemoteLoginAsTunnel", false).getValue();
      
      ///// TODO Don't start thread!!!
      // If we are a client XmlBlasterAccess reusing a remote login socket
      this.useRemoteLoginAsTunnel = this.callbackAddress.getEnv("useRemoteLoginAsTunnel", false).getValue();
      String entryKey = SocketExecutor.getGlobalKey(this.callbackAddress.getSessionName());
      Object obj = glob.getObjectEntry(entryKey); //getAddressServer()
      if (obj != null) {
         log.info(loginName + " Getting " + getType() + " obj=" + obj +
                  " entryKey=" + entryKey + " global.instanceId=" +glob.getInstanceId() + "-" + glob.hashCode());
         if (obj instanceof org.xmlBlaster.util.protocol.socket.SocketExecutor) {
            this.remoteLoginAsTunnelSocketExecutor = (SocketExecutor)obj;
         }
         this.useRemoteLoginAsTunnel = true;
         //if (obj instanceof org.xmlBlaster.protocol.socket.HandleClient) {
         //   org.xmlBlaster.protocol.socket.HandleClient h = (org.xmlBlaster.protocol.socket.HandleClient)obj;
         //}
      }

      if (cbClient != null)
         getSocketExecutor().setCbClient(cbClient); // access callback client in super class SocketExecutor:callback

      // If we are server side and a client which receives publish(),subscribe()... from remote cluster node
      obj = glob.getObjectEntry("ClusterManager[cluster]/I_Authenticate");
      if (obj != null) {
         setAuthenticateCore((I_Authenticate)obj);
         log.info("Setting I_Authenticate for acceptRemoteLoginAsTunnel=" + this.acceptRemoteLoginAsTunnel);
      }
      obj = glob.getObjectEntry("ClusterManager[cluster]/I_XmlBlaster");
      if (obj != null) {
         super.setXmlBlasterCore((I_XmlBlaster)obj);
         log.info("Setting I_XmlBlaster for acceptRemoteLoginAsTunnel=" + this.acceptRemoteLoginAsTunnel);
      }
      
      // Lookup SocketConnection instance in the NameService
      this.sockCon = (SocketConnection)glob.getObjectEntry("org.xmlBlaster.client.protocol.socket.SocketConnection");

      if (this.sockCon == null) {
         // SocketConnection.java must be instantiated first and registered to reuse the socket for callbacks
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
               "Sorry, creation of SOCKET callback handler is not possible if client connection is not of type 'SOCKET'");
      }

      this.sockCon.registerCbReceiver(this);

      if (this.threadRunning == false) {
         try {
            this.sock = this.sockCon.getSocket();
         }
         catch (XmlBlasterException e) {
            log.fine("There is no client socket connection which i could use: " + e.getMessage());
            return ;
         }

         if (useRemoteLoginAsTunnel) {
            log.info("We use the remote socket connection to tunnel our communication");
            if (this.remoteLoginAsTunnelSocketExecutor != null)
               this.remoteLoginAsTunnelSocketExecutor.setRunning(true); // Fake that we are OK
            return;
         }

         try { // SocketExecutor
            super.initialize(this.sockCon.getGlobal(), this.callbackAddress, this.sock.getInputStream(), this.sock.getOutputStream());
         }
         catch (IOException e) {
            throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "Creation of SOCKET callback handler failed", e);
         }

         try {
            // You should not activate SoTimeout, as this timeouts if InputStream.read() blocks too long.
            // But we always block on input read() to receive update() messages.
            setSoTimeout(this.callbackAddress.getEnv("SoTimeout", 0L).getValue()); // switch off
            this.sock.setSoTimeout((int)this.soTimeout);
            if (log.isLoggable(Level.FINE)) log.fine(this.callbackAddress.getEnvLookupKey("SoTimeout") + "=" + this.soTimeout);

            setSoLingerTimeout(this.callbackAddress.getEnv("SoLingerTimeout", soLingerTimeout).getValue());
            if (log.isLoggable(Level.FINE)) log.fine(this.callbackAddress.getEnvLookupKey("SoLingerTimeout") + "=" + getSoLingerTimeout());
            if (getSoLingerTimeout() >= 0L) {
               // >0: Try to send any unsent data on close(socket) (The UNIX kernel waits very long and ignores the given time)
               // =0: Discard remaining data on close()  <-- CHOOSE THIS TO AVOID BLOCKING close() calls
               this.sock.setSoLinger(true, (int)this.soLingerTimeout);
            }
            else
               this.sock.setSoLinger(false, 0); // false: default handling, kernel tries to send queued data after close() (the 0 is ignored)
         }
         catch (SocketException e) {
            log.severe("Failed to set socket attributes, we ignore it and continue: " + e.toString());
         }

         this.socketUrl = this.sockCon.getLocalSocketUrl();
         this.callbackAddress.setRawAddress(this.socketUrl.getUrl());
         if (log.isLoggable(Level.FINE)) log.fine("Callback uri=" + this.socketUrl.getUrl());

         callbackListenerThread = new Thread(this, "XmlBlaster."+getType());
         callbackListenerThread.setDaemon(true);
         int threadPrio = this.callbackAddress.getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
         try {
            callbackListenerThread.setPriority(threadPrio);
            if (log.isLoggable(Level.FINE)) log.fine("-dispatch/callback/plugin/socket/threadPrio = " + threadPrio);
         }
         catch (IllegalArgumentException e) {
            log.warning("Your -dispatch/callback/plugin/socket/threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
         }
         this.threadRunning = true;
         callbackListenerThread.start();
      }
   }

   /*
    * TODO: Is this needed anymore?
    * @return
    */
   protected boolean hasConnection() {
      return (this.sock != null);
   }

   /**
    * Returns the protocol type.
    * @return The configured [type] in xmlBlaster.properties, defaults to "SOCKET"
    */
   public final String getCbProtocol()
   {
      return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
   }

   /**
    * Returns the callback address.
    * <p />
    * This is no listen socket, as we need no callback server.
    * It is just the client side socket data of the established connection to xmlBlaster.
    * @return "socket://192.168.2.1:34520"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      if ( socketUrl == null ) {
         return "";
      }
      return socketUrl.getUrl();
   }

   /**
    * Starts the callback thread
    */
   public void run()
   {
      log.info("Started callback receiver plugin on '" + this.socketUrl.getUrl() + "'");
      boolean multiThreaded = this.callbackAddress.getEnv("multiThreaded", true).getValue();
      if (log.isLoggable(Level.FINE)) log.fine("SOCKET multiThreaded=" + multiThreaded);

      while(threadRunning && this.callbackListenerThread == Thread.currentThread()) {

         try {
            // This method blocks until a message arrives
            MsgInfo[] msgInfoArr = MsgInfo.parse(glob, progressListener, iStream, getMsgInfoParserClassName(), this.pluginInfo);
            if (msgInfoArr.length < 1) {
               log.warning(toString() + ": Got unexpected empty data from SOCKET, closing connection now");
               break;
            }
            final MsgInfo receiver = msgInfoArr[0];
            
            //if (MethodName.CONNECT.equals(receiver.getMethodName())) 
            //   log.info("Test: Got connectQos");

            if (log.isLoggable(Level.FINEST)) log.finest("Receiving message >" + receiver.toLiteral() + "<\n" + receiver.dump());

            if (this.acceptRemoteLoginAsTunnel
                  && receiver.isInvoke()
                  && !MethodName.UPDATE.equals(receiver.getMethodName())
                  && !MethodName.UPDATE_ONEWAY.equals(receiver.getMethodName())
                  && !MethodName.EXCEPTION.equals(receiver.getMethodName())
                  && !MethodName.PING.equals(receiver.getMethodName())) { 
               
               //getSocketExecutor().getXmlBlasterCore().
               if (MethodName.CONNECT == receiver.getMethodName()) {
                  // TODO: crypt.importMessage(receiver.getQos()); see also ClientDispatchConnection.java:440
                  Socket socket = this.sock;
                  if (socket == null) return; // Is possible when EOF arrived inbetween
                  ConnectQosServer conQos = new ConnectQosServer(glob, receiver.getQos());
                  if (conQos.getSecurityQos() == null)
                     throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT, ME, "connect() without securityQos");
                  conQos.getSecurityQos().setClientIp (socket.getInetAddress().getHostAddress());
                  conQos.setAddressServer(getAddressServer());
                  Object callbackSocketDriver = new CallbackSocketDriver(conQos.getSessionName().getLoginName(), this);
                  conQos.getAddressServer().setCallbackDriver(callbackSocketDriver);
                  conQos.getData().getCurrentCallbackAddress().setCallbackDriver(callbackSocketDriver);
                  ConnectReturnQosServer retQos = getAuthenticateCore().connect(conQos);
                  this.secretSessionId = retQos.getSecretSessionId();
                  receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
                  executeResponse(receiver, retQos.toXml(), SocketUrl.SOCKET_TCP);
               }
               else if (MethodName.DISCONNECT == receiver.getMethodName()) {
                  executeResponse(receiver, Constants.RET_OK, SocketUrl.SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
                  // Note: the disconnect will call over the CbInfo our shutdown as well
                  // setting sessionId = null prevents that our shutdown calls disconnect() again.
                  getAuthenticateCore().disconnect(getAddressServer(), receiver.getSecretSessionId(), receiver.getQos());
                  shutdown();
               }
               else {
                  if (log.isLoggable(Level.FINE)) log.fine("Received tunneled message, forwarding now to xmlBlaster core: " + receiver.getMethodNameStr());
                  boolean processed = receiveReply(receiver, SocketUrl.SOCKET_TCP);    // Parse the message and invoke actions in same thread
                  if (!processed)
                     log.warning("Received message is not processed: " + receiver.toLiteral());
               }
            }
            else if (this.acceptRemoteLoginAsTunnel
                  && receiver.isResponse()
                  && MethodName.UPDATE.equals(receiver.getMethodName())
                  && MethodName.UPDATE_ONEWAY.equals(receiver.getMethodName()) // ONEWAY have no response, just do be complete
            ) {
               log.severe("UPDATE RESPONSE IS NOT YET IMPLEMENTED");               
               boolean processed = receiveReply(receiver, SocketUrl.SOCKET_TCP);    // Parse the message and invoke actions in same thread
               if (!processed)
                  log.warning("Received message is not processed: " + receiver.toLiteral());
            }
            else {
               // Normal client operation
               if (receiver.isInvoke() && multiThreaded) {
                  // Parse the message and invoke callback to client code in a separate thread
                  // to avoid dead lock when client does a e.g. publish() during this update()
                  WorkerThread t = new WorkerThread(glob, this, receiver);
                  // -dispatch/callback/plugin/socket/invokerThreadPrio 5
                  t.setPriority(this.callbackAddress.getEnv("invokerThreadPrio", Thread.NORM_PRIORITY).getValue());
                  t.start();
               }
               else {
                  boolean processed = receiveReply(receiver, SocketUrl.SOCKET_TCP);    // Parse the message and invoke actions in same thread
                  if (!processed)
                     log.warning("Received message is not processed: " + receiver.toLiteral());
               }
               if (MethodName.DISCONNECT == receiver.getMethodName() && receiver.isResponse()) {
                  if (log.isLoggable(Level.FINE)) log.fine("Terminating socket callback thread because of disconnect response");
                  threadRunning = false;
               }
            }
         }
         catch(XmlBlasterException e) {
            log.warning(e.toString());
         }
         catch(Throwable e) {
            if (e instanceof NullPointerException)
               e.printStackTrace();
            if (threadRunning == true) {
               if (e.toString().indexOf("javax.net.ssl") != -1) {
                  log.warning("Closing connection to server, please try debugging SSL with 'java -Djavax.net.debug=all ...': " + e.toString());
               }
               else if (e instanceof IOException) {
                  log.warning("Closing connection to server: " + e.toString());
               }
               else {
                  log.severe("Closing connection to server: " + e.toString());
               }
               try {
                  // calls our shutdownSocket() which does this.threadRunning = false
                  sockCon.shutdown();
               }
               catch (XmlBlasterException ex) {
                  log.severe("run() could not shutdown correctly. " + ex.getMessage());
               }
               // Exceptions ends nowhere but terminates the thread
               
               // Notify client library  XmlBlasterAccess.java to go to polling
               try {
                  I_CallbackExtended cb = this.cbClient;
                  if (cb != null) {
                     cb.lostConnection(XmlBlasterException.convert(this.glob, ME, "Lost socket connection", e));
                  }
               }
               catch(Throwable xx) {
                  xx.printStackTrace();
               }
               
               if (this.acceptRemoteLoginAsTunnel) {
                  try {
                     AddressServer addr = this.addressServer;
                     if (addr != null) {
                        CallbackSocketDriver cbd = (CallbackSocketDriver)addr.getCallbackDriver();
                        if (cbd != null) {
                           cbd.shutdown(); // notify about lost connection
                        }
                     }
                  }
                  catch(Throwable xx) {
                     xx.printStackTrace();
                  }
               }
               
               if (e instanceof IOException || e instanceof java.net.SocketException) {
                  //2008-06-24 07:22:34.544 WARNING 14-XmlBlaster.SOCKET org.xmlBlaster.client.protocol.socket.SocketCallbackImpl run: Closing connection to server: java.net.SocketException: Socket closed
            	   shutdownSocket(); // stop thread
               }
            }
         }
      }
      if (log.isLoggable(Level.FINE)) log.fine("Terminating socket callback thread");
   }

   final SocketConnection getSocketConnection() {
      return sockCon;
   }

   /**
    * Shutdown callback only.
    */
   public synchronized void shutdown() {
      super.shutdown();
      //setCbClient(null); NO, not sure if this is a good idea // reset callback client in super class SocketExecutor:callback
   }

   /**
    * Shutdown SOCKET connection and callback
    * Called by SocketConnection.shutdown() on problems
    */
   public synchronized void shutdownSocket() {
      if (log.isLoggable(Level.FINE)) log.fine("Entering shutdownSocket()");
      boolean needsCleanup = this.threadRunning; // good enough for all cases?
      this.threadRunning = false;
      if (!this.useRemoteLoginAsTunnel) { // Do we own the socket?
         if (this.iStream != null) {
            try {
               this.iStream.close();
               //this.iStream = null; multi threading -> NPE danger
            } catch(IOException e) {
               log.warning(e.toString());
            }
         }
      }
      clearResponseListenerMap();
      freePendingThreads();
      if (this.acceptRemoteLoginAsTunnel) {
         if (needsCleanup) {
            I_Authenticate a = getAuthenticateCore();
            String s = this.secretSessionId;
            if (a != null && s != null)
               a.connectionState(s, ConnectionStateEnum.DEAD);
         }
      }
   }

   public I_Authenticate getAuthenticateCore() {
      return authenticateCore;
   }

   public void setAuthenticateCore(I_Authenticate authenticateImpl) {
      this.authenticateCore = authenticateImpl;
   }
} // class SocketCallbackImpl

