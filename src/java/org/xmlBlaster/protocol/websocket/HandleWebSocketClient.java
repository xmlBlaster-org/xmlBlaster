/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.websocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.RequestReplyExecutor;
import org.xmlBlaster.util.protocol.socket.SocketExecutor;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.CallbackAddress;
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
public class HandleWebSocketClient extends RequestReplyExecutor implements Runnable, I_CallbackDriver
{
   private String ME = "HandleClient";
   private static Logger log = Logger.getLogger(HandleWebSocketClient.class.getName());
   private Global glob;
   private WebSocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;

   
   //private String cbKey = null; // Remember the key for the Global map
   /** Holds remote "host:port" for logging */
   protected String remoteSocketStr;
   /** The socket connection to/from one client */
   protected WebSocket sock;
   /** The unique client sessionId */
   private String secretSessionId = null;

   private boolean callCoreInSeparateThread=true;
   protected volatile static ExecutorService executorService;

   protected boolean disconnectIsCalled = false;
   
   private boolean isShutdownCompletly = false;
   

   /**
    * Creates an instance which serves exactly one client.
    */
   public HandleWebSocketClient(Global glob, WebSocketDriver driver, WebSocket webSocket, ClientHandshake clientHandshake) {
      this.glob = glob;
      this.driver = driver;
      this.sock = webSocket;
      this.authenticate = driver.getAuthenticate();
      this.ME = driver.getType()+"-HandleClient";

      this.remoteSocketStr = this.sock.getRemoteSocketAddress().toString();
      
      super.initialize(glob, driver.getAddressServer());
      super.setXmlBlasterCore(driver.getXmlBlaster());
   }


   public String getType() {
      return this.driver.getType();
   }

   public boolean isShutdownCompletly() {
      return this.isShutdownCompletly;
   }
   
   void onClose(int code, String reason, boolean remote) {
      log.info("WebSocket " + this.sock.getRemoteSocketAddress().toString() + " closed: code=" + code + ", reason=" + reason + ", remote=" + remote);
      this.isShutdownCompletly = true;
   }
   
   public void onError(Exception ex) {
      log.info("WebSocket " + this.sock.getRemoteSocketAddress().toString() + " error: " + ex.getMessage());
   }

   public void onMessage(String message) {
      //log.info("WebSocket " + this.sock.getRemoteSocketAddress().toString() + " message string: " + message);
      this.onMessage(message.getBytes());
   }

   public void onMessage(ByteBuffer message) {
      //log.info("WebSocket " + this.sock.getRemoteSocketAddress().toString() + " message: " + new String(message.array()));
      this.onMessage(message.array());
   }
   
   private void onMessage(byte[] message) {
      try {

         final MsgInfo[] msgInfoArr = MsgInfo.parse(glob, null, message, org.xmlBlaster.util.xbformat.XbfParser.class.getName(), driver.getPluginInfo());
         if (msgInfoArr.length < 1) {
            log.warning(toString() + ": Got unexpected empty data from WebSocket, closing connection now");
            this.shutdown();
            return;
         }
         final MsgInfo msgInfo = msgInfoArr[0];
         handleMessage(msgInfo);
         
      } catch (Throwable e) {
         e.printStackTrace();
         shutdown();
      }
   }
   

   public void handleMessage(MsgInfo receiver) {
      try {
         if (receiveReply(receiver, false)) {
            return;
         }

         if (log.isLoggable(Level.FINE)) log.fine("Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");


         if (MethodName.CONNECT == receiver.getMethodName()) {
            ConnectQosServer conQos = new ConnectQosServer(driver.getGlobal(), receiver.getQos());
            if (conQos.getSecurityQos() == null)
               throw new XmlBlasterException(glob, ErrorCode.USER_SECURITY_AUTHENTICATION_ILLEGALARGUMENT, ME, "connect() without securityQos");
            conQos.getSecurityQos().setClientIp (sock.getRemoteSocketAddress().getAddress().getHostAddress());

            conQos.setAddressServer(driver.getAddressServer());
            this.ME = this.driver.getType() + "-HandleClient-" + conQos.getSessionName().getRelativeName();


            // getInetAddress().toString() does no reverse DNS lookup (no blocking danger) ...
            log.info(ME+": Client connected, coming from host=" + sock.getRemoteSocketAddress().toString());
            
            
            CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty().getCallbackAddresses();
            for (int ii = 0; cbArr != null && ii < cbArr.length; ii++) {
               cbArr[ii].setRawAddress(this.sock.getRemoteSocketAddress().toString());//driver.getRawAddress());
               try {
                  cbArr[ii].setCallbackDriver(this);
               } catch (Exception e) {
                  e.printStackTrace();
                  log.severe(ME + " Internal error during setCallbackDriver: " + e.toString());
               }
            }
            
           
            ConnectReturnQosServer retQos = authenticate.connect(conQos);
            driver.getAddressServer().setSessionName(retQos.getSessionName());
            this.secretSessionId = retQos.getSecretSessionId();
            receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
            executeResponse(receiver, retQos.toXml(), SocketUrl.SOCKET_TCP);
         } else if (MethodName.DISCONNECT == receiver.getMethodName()) {
            this.disconnectIsCalled = true;
            executeResponse(receiver, Constants.RET_OK, SocketUrl.SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
            // Note: the disconnect will call over the CbInfo our shutdown as well
            // setting sessionId = null prevents that our shutdown calls disconnect() again.
            authenticate.disconnect(driver.getAddressServer(), receiver.getSecretSessionId(), receiver.getQos());
            shutdown();
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
      catch (Throwable e) {
         e.printStackTrace();
         log.severe("Lost connection to client: " + e.toString());
         shutdown();
      }
   }


   public boolean isShutdown() {
      return this.isShutdownCompletly;
   }


   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      if (log.isLoggable(Level.FINE)) log.fine("Shutdown cb connection to " + sock.getRemoteSocketAddress().toString() + " ...");
      
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
      ret.append(getType()).append("-").append(this.sock.getRemoteSocketAddress().toString());
     // if (loginName != null && loginName.length() > 0)
      //   ret.append("-").append(loginName);
      //else
         ret.append("-").append(getSecretSessionId());
      ret.append("-").append(remoteSocketStr);
      return ret.toString();
   }

   private void closeSocket() {
      this.sock.close();
   }
   
   private String getCbMsgInfoParserClassName() {
      return org.xmlBlaster.util.xbformat.XbfParser.class.getName();
   }


   /**
    * Flush the data to the socket.
    * Overwrites SocketExecutor.sendMessage()
    * @throws XmlBlasterException 
    */
   protected void sendMessage(MsgInfo msgInfo, String requestId, MethodName methodName, boolean udp) throws XmlBlasterException {
      byte[] msg = msgInfo.createRawMsg(getCbMsgInfoParserClassName());
      this.sock.send(msg);
   }
   
   @Override
   public AddressServer getAddressServer() {
      return driver.getAddressServer();
   }

   /**
    * @return Returns the secretSessionId.
    */
   public String getSecretSessionId() {
      return this.secretSessionId;
   }


   @Override
   public void run() {
   }


   @Override
   public String getVersion() {
      return "1.0";
   }


   @Override
   public String getUsageUrl() {
      return "";
   }


   @Override
   public void setUsageUrl(String url) {
      // TODO Auto-generated method stub
      
   }


   @Override
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      // TODO Auto-generated method stub
      
   }


   @Override
   public String getName() {
      return "HandleWebSocketClient";
   }


   @Override
   public void init(Global glob, CallbackAddress callbackAddress) throws XmlBlasterException {
     
   }


   @Override
   public String getProtocolId() {
      return driver.getProtocolId();
   }


   @Override
   public String getRawAddress() {
      return sock.getRemoteSocketAddress().toString();
   }
   
   private String[] sendUpdate(MsgUnitRaw[] msgArr, boolean oneway) throws XmlBlasterException {
      if (isShutdown())
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "update() invocation ignored, we are shutdown.");

      if (msgArr == null || msgArr.length < 1) {
         log.severe("The argument of method update() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }
      try {
         MsgInfo parser = new MsgInfo(glob, MsgInfo.INVOKE_BYTE, MethodName.UPDATE, getSecretSessionId(), progressListener, getCbMsgInfoParserClassName());
         parser.setPluginConfig(driver.getPluginInfo());
         parser.addMessage(msgArr);
         
         if (oneway) {
            parser.setMethodName(MethodName.UPDATE_ONEWAY);
            requestAndBlockForReply(parser, SocketExecutor.ONEWAY, false);
            return new String[] {"OK"};
         }
         else {
           Object response = requestAndBlockForReply(parser, SocketExecutor.WAIT_ON_RESPONSE, false);
           if (log.isLoggable(Level.FINE)) log.fine("Got update response " + response.toString());
           return (String[])response; // return the QoS
         }
      }
      catch (XmlBlasterException e) {
         throw XmlBlasterException.tranformCallbackException(e);
      }
      catch (IOException e1) {
         if (log.isLoggable(Level.FINE)) log.fine("IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "Callback of " + msgArr.length + " messages failed", e1);
      }
   }


   @Override
   public String[] sendUpdate(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      return sendUpdate(msgArr, false);
   }


   @Override
   public void sendUpdateOneway(MsgUnitRaw[] msgArr) throws XmlBlasterException {
      sendUpdate(msgArr, true);
   }


   @Override
   public String ping(String qos) throws XmlBlasterException {
      if (sock.isClosed() || sock.isClosing() || isShutdown()) {
         throw new XmlBlasterException(glob,
               ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "WebSocket callback ping failed");
      }
      // TODO Auto-generated method stub
      return null;
   }


   @Override
   public boolean isAlive() {
      return !isShutdown();
   }
   
}

