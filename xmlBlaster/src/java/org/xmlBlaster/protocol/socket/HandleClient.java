/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.xbformat.I_ProgressListener;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.MsgUnitRaw;

import java.net.DatagramPacket;
import java.net.Socket;
import java.net.DatagramSocket;
import java.io.IOException;


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
   private final LogChannel log;
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   private CallbackSocketDriver callback;
   /** The socket connection to/from one client */
   protected DatagramSocket sockUDP;
   private boolean running = true;
   private String cbKey = null; // Remember the key for the Global map
   /** Holds remote "host:port" for logging */
   protected String remoteSocketStr;
   /** The socket connection to/from one client */
   protected Socket sock;
   /** The unique client sessionId */
   public String secretSessionId = null;

   /**
    * Creates an instance which serves exactly one client.
    */
   public HandleClient(Global glob, SocketDriver driver, Socket sock, DatagramSocket sockUDP) throws IOException {
      this.log = glob.getLog("socket");
      this.driver = driver;
      this.sock = sock;
      this.sockUDP = sockUDP;
      this.authenticate = driver.getAuthenticate();
      this.ME = driver.getType()+"-HandleClient";

      super.initialize(glob, driver.getAddressServer(), sock.getInputStream(), sock.getOutputStream());
      super.setXmlBlasterCore(driver.getXmlBlaster());

      this.remoteSocketStr = sock.getInetAddress().toString() + ":" + sock.getPort();
      
      // You should not activate SoTimeout, as this timeouts if InputStream.read() blocks too long.
      // But we always block on input read() to receive update() messages.
      setSoTimeout(driver.getAddressServer().getEnv("SoTimeout", 0L).getValue()); // switch off
      this.sock.setSoTimeout((int)this.soTimeout);
      if (log.TRACE) log.trace(ME, this.driver.getAddressServer().getEnvLookupKey("SoTimeout") + "=" + this.soTimeout);

      setSoLingerTimeout(driver.getAddressServer().getEnv("SoLingerTimeout", soLingerTimeout).getValue());
      if (log.TRACE) log.trace(ME, this.driver.getAddressServer().getEnvLookupKey("SoLingerTimeout") + "=" + getSoLingerTimeout());
      if (getSoLingerTimeout() >= 0L) {
         // >0: Try to send any unsent data on close(socket) (The UNIX kernel waits very long and ignores the given time)
         // =0: Discard remaining data on close()  <-- CHOOSE THIS TO AVOID BLOCKING close() calls
         this.sock.setSoLinger(true, (int)this.soLingerTimeout);
      }
      else
         this.sock.setSoLinger(false, 0); // false: default handling, kernel tries to send queued data after close() (the 0 is ignored)
      
      Thread t = new Thread(this, "XmlBlaster."+this.driver.getType() + (this.driver.isSSL()?".SSL":""));
      int threadPrio = driver.getAddressServer().getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
      try {
         t.setPriority(threadPrio);
         if (log.TRACE) log.trace(ME, "-plugin/socket/threadPrio "+threadPrio);
      }
      catch (IllegalArgumentException e) {
         log.warn(ME, "Your -plugin/socket/threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
      }
      t.start();
   }

   public String getType() {
      return this.driver.getType();
   }

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
      if (!running)
         return;
      synchronized (this) {
         if (log.TRACE) log.trace(ME, "Shutdown cb connection to " + loginName + " ...");
         if (cbKey != null)
            driver.getGlobal().removeNativeCallbackDriver(cbKey);

         running = false;

         driver.removeClient(this);

         this.secretSessionId = null;
         
         clearResponseListenerMap();

         freePendingThreads();
      }
      closeSocket();
   }

   private void closeSocket() {
      try { if (iStream != null) { iStream.close(); /*iStream=null;*/ } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      try { if (oStream != null) { oStream.close(); /*oStream=null;*/ } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      try { if (sock != null) { sock.close(); sock=null; } } catch (IOException e) { log.warn(ME+".shutdown", e.toString()); }
      if (log.TRACE) log.trace(ME, "Closed socket for '" + loginName + "'.");
   }

   /**
    * Updating multiple messages in one sweep, callback to client.
    * <p />
    * @param expectingResponse is WAIT_ON_RESPONSE or ONEWAY
    * @return null if oneway
    * @see org.xmlBlaster.engine.RequestBroker
    */
   public final String[] sendUpdate(String cbSessionId, MsgUnitRaw[] msgArr, boolean expectingResponse) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering update: id=" + cbSessionId + " numSend=" + msgArr.length + " oneway=" + !expectingResponse);
      if (!running)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "update() invocation ignored, we are shutdown.");

      if (msgArr == null || msgArr.length < 1) {
         log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }
      try {
         if (expectingResponse) {
            MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE, cbSessionId, progressListener);
            parser.addMessage(msgArr);
            Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, false);
            if (log.TRACE) log.trace(ME, "Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
         else {
            MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE_ONEWAY, cbSessionId, progressListener);
            parser.addMessage(msgArr);
            requestAndBlockForReply(parser, SocketExecutor.ONEWAY, this.driver.useUdpForOneway());
            return null;
         }
      }
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".update", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Callback of " + msgArr.length + " messages failed", e1);
      }
   }

   /**
    * Ping to check if callback server is alive.
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    * @exception XmlBlasterException If client not reachable
    */
   public final String ping(String qos) throws XmlBlasterException
   {
      if (!running)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "ping() invocation ignored, we are shutdown.");
      try {
         String cbSessionId = "";
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.PING, cbSessionId, progressListener);
         parser.addMessage(qos);
         Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, false);
         if (log.TRACE) log.trace(ME, "Got ping response " + response.toString());
         return (String)response; // return the QoS
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "Callback ping failed", e);
      }
   }

   public void handleMessage(MsgInfo receiver, boolean udp) {
      try {

         if (log.TRACE) log.trace(ME, "Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

         // receive() processes all invocations, only connect()/disconnect() we do locally ...
         if (receiveReply(receiver, udp) == false) {
            if (MethodName.CONNECT == receiver.getMethodName()) {
               ConnectQosServer conQos = new ConnectQosServer(driver.getGlobal(), receiver.getQos());
               conQos.setAddressServer(this.driver.getAddressServer());
               setLoginName(conQos.getUserId());
               Thread.currentThread().setName("XmlBlaster." + this.driver.getType() + (this.driver.isSSL()?".SSL":"") + ".tcpListener-" + conQos.getUserId());
               this.ME = this.driver.getType() + "-HandleClient-" + this.loginName;

               // getInetAddress().toString() does no reverse DNS lookup (no blocking danger) ...
               log.info(ME, "Client connected, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());

               CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty().getCallbackAddresses();
               for (int ii=0; cbArr!=null && ii<cbArr.length; ii++) {
                  cbKey = cbArr[ii].getType() + cbArr[ii].getRawAddress();
                  SocketUrl cbUrl = new SocketUrl(glob, cbArr[ii].getRawAddress());
                  SocketUrl remoteUrl = new SocketUrl(glob, this.sock.getInetAddress().getHostAddress(), this.sock.getPort());
                  if (driver.getAddressServer() != null) {
                     driver.getAddressServer().setRemoteAddress(remoteUrl);
                  }
                  if (log.TRACE) log.trace(ME, "remoteUrl='" + remoteUrl.getUrl() + "' cbUrl='" + cbUrl.getUrl() + "'");
                  if (true) { // !!!!! TODO remoteUrl.equals(cbUrl)) {
                     if (log.TRACE) log.trace(ME, "Tunneling callback messages through same SOCKET to '" + remoteUrl.getUrl() + "'");
                     this.callback = new CallbackSocketDriver(this.loginName, this);
                     org.xmlBlaster.protocol.I_CallbackDriver oldCallback = driver.getGlobal().getNativeCallbackDriver(cbKey);
                     if (oldCallback != null) { // Remove old and lost login of client with same callback address
                        log.warn(ME, "Destroying old callback driver '" + cbKey + "' ...");
                        //oldCallback.shutdown(); don't destroy socket, is done by others
                        driver.getGlobal().removeNativeCallbackDriver(cbKey);
                        oldCallback = null;
                     }
                     if (this.log.TRACE) this.log.trace(ME, "run: register new callback driver: '" + cbKey + "'");
                     driver.getGlobal().addNativeCallbackDriver(cbKey, this.callback); // tell that we are the callback driver as well
                  }
                  else {
                     log.error(ME, "Creating SEPARATE callback " + this.driver.getType() + " connection to '" + remoteUrl.getUrl() + "'");
                     this.callback = new CallbackSocketDriver(this.loginName);
                     // DispatchConnection.initialize() -> CbDispatchConnection.connectLowlevel()
                     // will later call callback.initialize(loginName, callbackAddress)
                  }
               }

               ConnectReturnQosServer retQos = authenticate.connect(driver.getAddressServer(), conQos);
               this.secretSessionId = retQos.getSecretSessionId();
               receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
               executeResponse(receiver, retQos.toXml(), SocketUrl.SOCKET_TCP);
               driver.addClient(this.secretSessionId, this);
             }
            else if (MethodName.DISCONNECT == receiver.getMethodName()) {
               this.secretSessionId = null;
               executeResponse(receiver, Constants.RET_OK, SocketUrl.SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
               // Note: the disconnect will call over the CbInfo our shutdown as well
               // setting sessionId = null prevents that our shutdown calls disconnect() again.
               authenticate.disconnect(driver.getAddressServer(), receiver.getSecretSessionId(), receiver.getQos());
               shutdown();
            }
         }
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "Can't handle message, throwing exception back to client: " + e.toString());
         try {
            if (receiver.getMethodName() != MethodName.PUBLISH_ONEWAY)
               executeException(receiver, e, false);
            else
               log.warn(ME, "Can't handle publishOneway message, ignoring exception: " + e.toString());
         }
         catch (Throwable e2) {
            log.error(ME, "Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
            shutdown();
         }
      }
      catch (IOException e) {
         if (running != false) { // Only if not triggered by our shutdown:sock.close()
            if (log.TRACE) log.trace(ME, "Lost connection to client: " + e.toString());
            shutdown();
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "Lost connection to client: " + e.toString());
         shutdown();
      }
   }

   /**
    * Flush the data to the socket.
    * Overwrites SocketExecutor.sendMessage() 
    */
   protected void sendMessage(byte[] msg, boolean udp) throws IOException {
      I_ProgressListener listener = this.progressListener;
      if (listener != null) {
         listener.progressWrite("", 0, msg.length);
      }
      if (udp && this.sockUDP!=null) {
         DatagramPacket dp = new DatagramPacket(msg, msg.length, sock.getInetAddress(), sock.getPort());
         //DatagramPacket dp = new DatagramPacket(msg, msg.length, sock.getInetAddress(), 32001);
         this.sockUDP.send(dp);
         if (log.TRACE) log.trace(ME, "UDP datagram is send");
      }
      else {
         synchronized (oStream) {
            oStream.write(msg);
            oStream.flush();
            if (log.TRACE) log.trace(ME, "TCP data is send");
         }
      }
      if (listener != null) {
         listener.progressWrite("", msg.length, msg.length);
      }
   }

   /**
    * Serve a client, we block until a message arrives ...
    */
   public void run() {
      if (log.CALL) log.call(ME, "Handling client request ...");
      try {
         if (log.TRACE)
            log.trace(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());
         while (running) {
            try {
               // blocks until a message arrives
               MsgInfo msgInfo = MsgInfo.parse(glob, progressListener, iStream, getMsgInfoParserClassName())[0];
               handleMessage(msgInfo, false);
            }
            catch (Throwable e) {
               if (e.toString().indexOf("closed") != -1) {
                  if (log.TRACE) log.trace(ME, "TCP socket '" + remoteSocketStr + "' is shutdown: " + e.toString());
               }
               else if (e.toString().indexOf("EOF") != -1) {
                  log.warn(ME, "Lost TCP connection from '" + remoteSocketStr + "': " + e.toString());
               }
               else {
                  log.warn(ME, "Error parsing TCP data from '" + remoteSocketStr + "', check if client and server have identical compression or SSL settings: " + e.toString());
               }
               shutdown();
               break;
            }
         }
      }
      finally {
         driver.removeClient(this);
         closeSocket();
         if (log.TRACE) log.trace(ME, "Deleted thread for '" + loginName + "'.");
      }
   }

   /**
    * @return Returns the secretSessionId.
    */
   public String getSecretSessionId() {
      return this.secretSessionId;
   }

}

