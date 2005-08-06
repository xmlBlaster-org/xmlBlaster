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
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;

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
public class HandleClient extends Executor implements Runnable
{
   private String ME = "HandleClient";
   private final LogChannel log;
   private SocketDriver driver;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   private CallbackSocketDriver callback;
   private boolean running = true;
   private String cbKey = null; // Remember the key for the Global map


   /**
    * Creates an instance which serves exactly one client.
    */
   public HandleClient(Global glob, SocketDriver driver, Socket sock, DatagramSocket sockUDP) throws IOException {
      super.initialize(glob, driver.getAddressServer(), sock, driver.getXmlBlaster(), sockUDP);
      this.log = glob.getLog("socket");
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      this.ME = driver.getType()+"-HandleClient";

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

   synchronized public boolean isShutdown() {
      return (this.running == false);
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

         if (sessionId != null) {
            String tmp = sessionId;
            sessionId = null;
            try { // check first if session is in shutdown process already (avoid recursive disconnect()):
               if (authenticate.sessionExists(sessionId))
                  authenticate.disconnect(driver.getAddressServer(), tmp, "<qos/>");
            }
            catch(Throwable e) {
               log.warn(ME, e.toString());
               e.printStackTrace();
            }
         }
         if (responseListenerMap.size() > 0) {
            java .util.Iterator iterator = responseListenerMap.keySet().iterator();
            StringBuffer buf = new StringBuffer(256);
            while (iterator.hasNext()) {
               if (buf.length() > 0) buf.append(", ");
               String key = (String)iterator.next();
               buf.append(key);
            }
            log.warn(ME, "There are " + responseListenerMap.size() + " messages pending without a response, request IDs are " + buf.toString());
            responseListenerMap.clear();
         }

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
            Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.UPDATE, cbSessionId, progressListener);
            parser.addMessage(msgArr);
            Object response = execute(parser, WAIT_ON_RESPONSE, false);
            if (log.TRACE) log.trace(ME, "Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
         else {
            Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.UPDATE_ONEWAY, cbSessionId, progressListener);
            parser.addMessage(msgArr);
            execute(parser, ONEWAY, this.driver.useUdpForOneway());
            return null;
         }
      }
      catch (XmlBlasterException xmlBlasterException) {
         // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         if (xmlBlasterException.isUser())
            throw xmlBlasterException;

         // and server side communication problems (how to assure if from server?)
         if (xmlBlasterException.isCommunication() && xmlBlasterException.isServerSide())
            throw xmlBlasterException;

         // The SOCKET protocol plugin throws this when a client has shutdown its callback server
         //if (xmlBlasterException.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_CALLBACKSERVER_NOTAVAILABLE)
         //   throw xmlBlasterException;

         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "Callback of " + msgArr.length + " messages failed", xmlBlasterException);
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
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PING, cbSessionId, progressListener);
         parser.addMessage(qos);
         Object response = execute(parser, WAIT_ON_RESPONSE, false);
         if (log.TRACE) log.trace(ME, "Got ping response " + response.toString());
         return (String)response; // return the QoS
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "Callback ping failed", e);
      }
   }

   public void handleMessage(Parser receiver, boolean udp) {
      try {

         if (log.TRACE) log.trace(ME, "Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

         // receive() processes all invocations, only connect()/disconnect() we do locally ...
         if (receive(receiver, udp) == false) {
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
                  SocketUrl remoteUrl = new SocketUrl(glob, super.sock.getInetAddress().getHostAddress(), super.sock.getPort());
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
               this.sessionId = retQos.getSecretSessionId();
               receiver.setSecretSessionId(retQos.getSecretSessionId()); // executeResponse needs it
               executeResponse(receiver, retQos.toXml(), SOCKET_TCP);
               driver.addClient(sessionId, this);
             }
            else if (MethodName.DISCONNECT == receiver.getMethodName()) {
               this.sessionId = null;
               executeResponse(receiver, Constants.RET_OK, SOCKET_TCP);   // ACK the disconnect to the client and then proceed to the server core
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
    * Serve a client, we block until a message arrives ...
    */
   public void run() {
      if (log.CALL) log.call(ME, "Handling client request ...");
      Parser receiver = new Parser(glob, progressListener);
      try {
         if (log.TRACE)
            log.trace(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());
         while (running) {
            try {
               receiver.parse(iStream); // blocks until a message arrives
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
            handleMessage(receiver, false);
         }
      }
      finally {
         driver.removeClient(this);
         closeSocket();
         if (log.TRACE) log.trace(ME, "Deleted thread for '" + loginName + "'.");
      }
   }

}

