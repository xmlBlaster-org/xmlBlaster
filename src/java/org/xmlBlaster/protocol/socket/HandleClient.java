/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;


/**
 * Holds one socket connection to a client and handles
 * all requests from one client with plain socket messaging.
 * <p />
 * <ol>
 *   <li>We block on the socket input stream to read incoming messages
 *       in a separate thread (see run() method)</li>
 *   <li>We send update() and ping() back to the client</li>
 * </ol>
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class HandleClient extends SocketExecutor implements Runnable
{
   private String ME = "HandleClient";
   private static Logger log = Logger.getLogger(HandleClient.class.getName());
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   private CallbackSocketDriver callback;
   /** The socket connection to/from one client */
   protected DatagramSocket sockUDP;
   //private String cbKey = null; // Remember the key for the Global map
   /** Holds remote "host:port" for logging */
   protected String remoteSocketStr;
   /** The socket connection to/from one client */
   protected Socket sock;
   /** The unique client sessionId */
   private String secretSessionId = null;

   private boolean callCoreInSeparateThread=true;
   protected volatile static ExecutorService executorService;

   protected boolean disconnectIsCalled = false;
   
   private boolean isShutdownCompletly = false;
   
   private Thread socketHandlerThread;

   /**
    * Creates an instance which serves exactly one client.
    */
   public HandleClient(Global glob, SocketDriver driver, Socket sock, DatagramSocket sockUDP) throws IOException {

      this.driver = driver;
      this.sock = sock;
      this.sockUDP = sockUDP;
      this.authenticate = driver.getAuthenticate();
      this.ME = driver.getType()+"-HandleClient";

      if (executorService == null) {
         synchronized (HandleClient.class) {
            if (executorService == null) {
               executorService = Executors.newCachedThreadPool();
            }
         }
      }

      // Fills a clone to this.addressServer
      super.initialize(glob, driver.getAddressServer(), this.sock.getInputStream(), this.sock.getOutputStream());
      super.setXmlBlasterCore(driver.getXmlBlaster());

      this.remoteSocketStr = this.sock.getInetAddress().toString() + ":" + this.sock.getPort();

      // You should not activate SoTimeout, as this timeouts if InputStream.read() blocks too long.
      // But we always block on input read() to receive update() messages.
      setSoTimeout(getAddressServer().getEnv("SoTimeout", 0L).getValue()); // switch off
      this.sock.setSoTimeout((int)this.soTimeout);
      if (log.isLoggable(Level.FINE)) log.fine(getAddressServer().getEnvLookupKey("SoTimeout") + "=" + this.soTimeout);

      setSoLingerTimeout(getAddressServer().getEnv("SoLingerTimeout", soLingerTimeout).getValue());
      if (log.isLoggable(Level.FINE)) log.fine(getAddressServer().getEnvLookupKey("SoLingerTimeout") + "=" + getSoLingerTimeout());
      if (getSoLingerTimeout() >= 0L) {
         // >0: Try to send any unsent data on close(socket) (The UNIX kernel waits very long and ignores the given time)
         // =0: Discard remaining data on close()  <-- CHOOSE THIS TO AVOID BLOCKING close() calls
         this.sock.setSoLinger(true, (int)this.soLingerTimeout);
      }
      else
         this.sock.setSoLinger(false, 0); // false: default handling, kernel tries to send queued data after close() (the 0 is ignored)

      this.callCoreInSeparateThread = getAddressServer().getEnv("callCoreInSeparateThread", callCoreInSeparateThread).getValue();

      this.socketHandlerThread = new Thread(this, "XmlBlaster."+this.driver.getType() + (this.driver.isSSL()?".SSL":""));
      int threadPrio = getAddressServer().getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
      try {
         this.socketHandlerThread.setPriority(threadPrio);
         if (log.isLoggable(Level.FINE)) log.fine("-plugin/socket/threadPrio "+threadPrio);
      }
      catch (IllegalArgumentException e) {
         log.warning("Your -plugin/socket/threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
      }
   }
   
   public void startThread() {
      this.socketHandlerThread.start();
   }
   
   public boolean useUdpForOneway() {
      return (this.driver != null) ? this.driver.useUdpForOneway() : false;
   }

   public String getType() {
      return this.driver.getType();
   }

   public boolean isShutdownCompletly() {
      return this.isShutdownCompletly;
   }

   @Override
   synchronized public boolean isShutdown() {
      return (this.running == false);
   }

   /*
    * TODO: Is this needed anymore?
    * @return
    */
   protected boolean hasConnection() {
      return (this.sock != null);
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      super.shutdown();
      if (!running)
         return;
      synchronized (this) {
         if (!running)
            return;
         if (log.isLoggable(Level.FINE)) log.fine("Shutdown cb connection to " + loginName + " ...");
         //if (cbKey != null)
         //   driver.getGlobal().removeNativeCallbackDriver(cbKey);

         running = false;
         
         driver.removeClient(this);

         clearResponseListenerMap();

         freePendingThreads();
      }
      I_Authenticate auth = this.authenticate;
      if (auth != null) {
         // From the point of view of the incoming client connection we are dead
         // The callback dispatch framework may have another point of view (which is not of interest here)
         auth.connectionState(this.secretSessionId, ConnectionStateEnum.DEAD);
      }
      closeSocket();
      this.isShutdownCompletly = true;
   }

   public String toString() {
      StringBuffer ret = new StringBuffer(256);
      ret.append(getType()).append("-").append(this.addressConfig.getName());
      if (loginName != null && loginName.length() > 0)
         ret.append("-").append(loginName);
      else
         ret.append("-").append(getSecretSessionId());
      ret.append("-").append(remoteSocketStr);
      return ret.toString();
   }

   private void closeSocket() {
      try { if (iStream != null) { iStream.close(); /*iStream=null;*/ } } catch (IOException e) { log.warning(e.toString()); }
      try { if (oStream != null) { oStream.close(); /*oStream=null;*/ } } catch (IOException e) { log.warning(e.toString()); }
      Socket sock = this.sock;
      try { if (sock != null) { this.sock=null; sock.close(); } } catch (IOException e) { log.warning(e.toString()); }
      if (log.isLoggable(Level.FINE)) log.fine("Closed socket for '" + loginName + "'.");
   }

   public void handleMessage(MsgInfo receiver, boolean udp) {
      try {

         if (log.isLoggable(Level.FINE)) log.fine("Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

         // receive() processes all invocations, only connect()/disconnect() we do locally ...
         if (receiveReply(receiver, udp) == false) {
            if (MethodName.CONNECT == receiver.getMethodName()) {
               // TODO: crypt.importMessage(receiver.getQos()); see also ClientDispatchConnection.java:440
               Socket socket = this.sock;
               if (socket == null) return; // Is possible when EOF arrived inbetween
               ConnectQosServer conQos = new ConnectQosServer(driver.getGlobal(), receiver.getQos());
               if (conQos.getSecurityQos() == null)
                  throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT, ME, "connect() without securityQos");
               conQos.getSecurityQos().setClientIp (socket.getInetAddress().getHostAddress());

               conQos.setAddressServer(getAddressServer());
               setLoginName(conQos.getSessionName().getRelativeName());
               if (this.callCoreInSeparateThread) {
                  Thread.currentThread().setName("XmlBlaster.HandleClient");
               }
               else {
                  Thread.currentThread().setName("XmlBlaster.socket"+ (this.driver.isSSL()?".SSL":"") + ".tcpListener-" + conQos.getUserId());
               }
               this.ME = this.driver.getType() + "-HandleClient-" + this.loginName;


               // getInetAddress().toString() does no reverse DNS lookup (no blocking danger) ...
               log.info(ME+": Client connected, coming from host=" + socket.getInetAddress().toString() + " port=" + socket.getPort());

               CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty().getCallbackAddresses();
               for (int ii=0; cbArr!=null && ii<cbArr.length; ii++) {
                  SocketUrl cbUrl = new SocketUrl(glob, cbArr[ii].getRawAddress());
                  SocketUrl remoteUrl = new SocketUrl(glob, socket.getInetAddress().getHostAddress(), socket.getPort());
                  if (this.addressServer != null) {
                     this.addressServer.setRemoteAddress(remoteUrl);
                  }
                  if (log.isLoggable(Level.FINE)) log.fine(ME+": remoteUrl='" + remoteUrl.getUrl() + "' cbUrl='" + cbUrl.getUrl() + "'");
//                  if (true) { // !!!!! TODO remoteUrl.equals(cbUrl)) {
                     if (log.isLoggable(Level.FINE)) log.fine(ME+": Tunneling callback messages through same SOCKET to '" + remoteUrl.getUrl() + "'");
                     
                     // Set client ConnectQos wishes like
                     // <attribute name='updateResponseTimeout' type='long'>20000</attribute>
                     initializeCb(cbArr[ii]);

                     this.callback = new CallbackSocketDriver(this.loginName, this);
                     //this.callback.init(this.glob, cbArr[ii]); is done in connectLowLeve()
                     cbArr[ii].setCallbackDriver(this.callback);
                     if (this.addressServer != null) { // pass for "useRemoteLoginAsTunnel"
                    	this.addressServer.setCallbackDriver(this.callback);
                     }
//                  }
//                  else {
//                     log.severe(ME+": Creating SEPARATE callback " + this.driver.getType() + " connection to '" + remoteUrl.getUrl() + "'");
//                     this.callback = new CallbackSocketDriver(this.loginName);
//                     // DispatchConnection.initialize() -> CbDispatchConnection.connectLowlevel()
//                     // will later call callback.initialize(loginName, callbackAddress)
//                  }
               }

               conQos.setInhibitDispatcherActiveOnConnect(true);
               ConnectReturnQosServer retQos = authenticate.connect(conQos);
               this.addressServer.setSessionName(retQos.getSessionName());
               this.secretSessionId = retQos.getSecretSessionId();
               receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
               executeResponse(receiver, retQos.toXml(), SocketUrl.SOCKET_TCP);
               driver.addClient(this.secretSessionId, this);
               
               // TODO: authenticate plugin may disable the dispatcher, and we enable it here again? Is ConnectQosServer a clone?
               if (conQos.getData().getCurrentCallbackAddress().isDispatcherActive()) {
                  try {
                     I_AdminSubject sub = authenticate.getSubjectInfoByName(conQos.getSessionName());
                     I_AdminSession sess = sub.getSessionByPubSessionId(conQos.getSessionName().getPublicSessionId());
                     sess.setDispatcherActive(true);
                  } catch (Throwable e) {
                     String msg = "Unexpected: no session info after connect: " + conQos.getSessionName();
                     log.severe(msg);
                     e.printStackTrace();
                     throw new XmlBlasterException(glob, ErrorCode.INTERNAL, ME+".handleMessage()", msg, e);
                  }
               }
             }
            else if (MethodName.DISCONNECT == receiver.getMethodName()) {
               this.disconnectIsCalled = true;
               executeResponse(receiver, Constants.RET_OK, SocketUrl.SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
               // Note: the disconnect will call over the CbInfo our shutdown as well
               // setting sessionId = null prevents that our shutdown calls disconnect() again.
               authenticate.disconnect(getAddressServer(), receiver.getSecretSessionId(), receiver.getQos());
               shutdown();
            }
         }
      }
      catch (XmlBlasterException e) {
         /*if (log.isLoggable(Level.FINE)) log.fine*/log.info("Can't handle message, throwing exception back to client: " + e.toString());
         try {
            if (log.isLoggable(Level.FINE)) log.fine(receiver.toLiteral());
         } catch (Throwable e1) {
            e1.printStackTrace();
         }
         try {
            if (receiver.getMethodName() != MethodName.PUBLISH_ONEWAY)
               executeException(receiver, e, false);
            else
               log.warning("Can't handle publishOneway message, ignoring exception: " + e.toString());

            if (e.isCleanupSession()) {
            //if (e.getErrorCode().equals(ErrorCode.USER_SECURITY_AUTHENTICATION_ACCESSDENIED) ||
            //      e.getErrorCode().equals(ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT)) {
               shutdown(); // cleanup to avoid thread/memory leak for a client trying again an again
            }
         }
         catch (Throwable e2) {
            log.warning("Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
            shutdown();
         }
      }
      catch (IOException e) {
         if (running != false) { // Only if not triggered by our shutdown:sock.close()
            if (log.isLoggable(Level.FINE)) log.fine("Lost connection to client: " + e.toString());
            shutdown();
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Lost connection to client: " + e.toString());
         shutdown();
      }
   }

   /**
    * Flush the data to the socket.
    * Overwrites SocketExecutor.sendMessage()
    */
   protected void sendMessage(byte[] msg, boolean udp) throws IOException {
      I_ProgressListener listener = this.progressListener;
      try {
         if (listener != null) {
            listener.progressWrite("", 0, msg.length);
         }
         else
            log.fine("The progress listener is null");

         if (udp && this.sockUDP!=null && this.sock!=null) {
            DatagramPacket dp = new DatagramPacket(msg, msg.length, this.sock.getInetAddress(), this.sock.getPort());
            //DatagramPacket dp = new DatagramPacket(msg, msg.length, sock.getInetAddress(), 32001);
            this.sockUDP.send(dp);
            if (log.isLoggable(Level.FINE)) log.fine("UDP datagram is send");
         }
         else {
            int bytesLeft = msg.length;
            int bytesRead = 0;
            synchronized (oStream) {
               while (bytesLeft > 0) {
                  int toRead = bytesLeft > this.maxChunkSize ? this.maxChunkSize : bytesLeft;
                  oStream.write(msg, bytesRead, toRead);
                  oStream.flush();
                  bytesRead += toRead;
                  bytesLeft -= toRead;
                  if (listener != null)
                     listener.progressWrite("", bytesRead, msg.length);
               }
            }
         }
         if (listener != null) {
            listener.progressWrite("", msg.length, msg.length);
         }
      }
      catch (IOException ex) {
         if (listener != null) {
            listener.clearCurrentWrites();
            listener.clearCurrentReads();
         }
         throw ex;
      }
   }
   
   public String getCbMsgInfoParserClassName() {
      if (this.callback != null)
         return this.callback.getMsgInfoParserClassName();
      else
         return super.getCbMsgInfoParserClassName();
   }

   /**
    * Serve a client, we block until a message arrives ...
    */
   public void run() {
      if (log.isLoggable(Level.FINER)) log.finer("Handling client request ...");
      try {
         if (log.isLoggable(Level.FINE)) {
            Socket socket = this.sock;
            if (socket != null)
               log.fine("Client accepted, coming from host=" + socket.getInetAddress().toString() + " port=" + socket.getPort());
         }
         while (running) {
            try {
               // blocks until a message arrives (see XbfParser.java)
               final MsgInfo[] msgInfoArr = MsgInfo.parse(glob, progressListener, iStream, getMsgInfoParserClassName(), driver.getPluginConfig());
               if (msgInfoArr.length < 1) {
                  log.warning(toString() + ": Got unexpected empty data from SOCKET, closing connection now");
                  break;
               }
               final MsgInfo msgInfo = msgInfoArr[0];

               if (this.callCoreInSeparateThread) {
                  executorService.execute(new Runnable() {
                     public void run() {
                        handleMessage(msgInfo, false);
                     }
                  });
               }
               else {
                  handleMessage(msgInfo, false);
               }
            }
            catch (Throwable e) {
               if (e.toString().indexOf("closed") != -1 || (e instanceof java.net.SocketException && e.toString().indexOf("Connection reset") != -1)) {
                  if (log.isLoggable(Level.FINE)) log.fine(toString() + ": TCP socket is shutdown: " + e.toString());
               }
               else if (e.toString().indexOf("EOF") != -1) {
                  if (this.disconnectIsCalled)
                     if (log.isLoggable(Level.FINE)) log.fine(toString() + ": Lost TCP connection after sending disconnect(): " + e.toString());
                  else
                     log.warning(toString() + ": Lost TCP connection: " + e.toString());
               }
               else {
                  log.warning(toString() + ": Error parsing TCP data from '" + remoteSocketStr + "', check if client and server have identical compression or SSL settings: " + e.toString());
               }
               if (e instanceof OutOfMemoryError || e instanceof IllegalArgumentException) {
                  e.printStackTrace();
               }
               if (e.getCause() != null && (e.getCause() instanceof OutOfMemoryError || e.getCause() instanceof IllegalArgumentException)) {
                  e.printStackTrace();
               }
//               I_Authenticate auth = this.authenticate;
//               if (auth != null) {
//                  // From the point of view of the incoming client connection we are dead
//                  // The callback dispatch framework may have another point of view (which is not of interest here)
//                  auth.connectionState(this.secretSessionId, ConnectionStateEnum.DEAD);
//               }
               shutdown();
               break;
            }
         }
      }
      finally {
         if (log.isLoggable(Level.FINE)) log.fine("Deleted thread for '" + loginName + "'.");
      }
   }

   /**
    * @return Returns the secretSessionId.
    */
   public String getSecretSessionId() {
      return this.secretSessionId;
   }
   
   public Socket getSocket() {
      return this.sock;
   }
}

