/*------------------------------------------------------------------------------
Name:      HandleClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnitRaw;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


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
   private String ME = "HandleClientRequest";
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
   public HandleClient(Global glob, SocketDriver driver, Socket sock) throws IOException {
      super(glob, sock, driver.getXmlBlaster());
      this.log = glob.getLog("socket");
      this.driver = driver;
      this.authenticate = driver.getAuthenticate();
      Thread t = new Thread(this, "XmlBlaster.SOCKET.HandleClient.BlockOnInputStreamForMessageFromClient");
      t.setPriority(glob.getProperty().get("socket.threadPrio", Thread.NORM_PRIORITY));
      t.start();
   }

   /**
    * Close connection for one specific client
    */
   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Schutdown cb connection to " + loginName + " ...");
      if (cbKey != null)
         driver.getGlobal().removeNativeCallbackDriver(cbKey);

      running = false;

      driver.removeClient(this);
      
      closeSocket();
   
      if (sessionId != null) {
         String tmp = sessionId;
         sessionId = null;
         try { // check first if session is in shutdown process already (avoid recursive disconnect()):
            if (authenticate.sessionExists(sessionId))
               authenticate.disconnect(tmp, "<qos/>");
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

   private synchronized void closeSocket() {
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
      if (log.CALL) log.call(ME, "Entering update: id=" + cbSessionId);
      if (!running)
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME, "update() invocation ignored, we are shutdown.");

      if (msgArr == null || msgArr.length < 1) {
         log.error(ME + ".InvalidArguments", "The argument of method update() are invalid");
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Illegal sendUpdate() argument");
      }
      try {
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.UPDATE, cbSessionId);
         parser.addMessage(msgArr);
         if (expectingResponse) {
            Object response = execute(parser, WAIT_ON_RESPONSE);
            if (log.TRACE) log.trace(ME, "Got update response " + response.toString());
            return (String[])response; // return the QoS
         }
         else {
            execute(parser, ONEWAY);
            return null;
         }
      }
      catch (XmlBlasterException xmlBlasterException) {
         // WE ONLY ACCEPT ErrorCode.USER... FROM CLIENTS !
         if (xmlBlasterException.isUser())
            throw xmlBlasterException;

         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME,
                   "SOCKET callback of " + msgArr.length + " messages failed", xmlBlasterException);
      }
      catch (IOException e1) {
         if (log.TRACE) log.trace(ME+".update", "IO exception: " + e1.toString());
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
               "SOCKET callback of " + msgArr.length + " messages failed", e1);
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
         Parser parser = new Parser(glob, Parser.INVOKE_BYTE, MethodName.PING, cbSessionId);
         parser.addMessage(qos);
         Object response = execute(parser, WAIT_ON_RESPONSE);
         if (log.TRACE) log.trace(ME, "Got ping response " + response.toString());
         return (String)response; // return the QoS
      } catch (Throwable e) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_NOCONNECTION, ME,
                     "SOCKET callback ping failed", e);
      }
   }

   /**
    * Serve a client, we block until a message arrives ...
    */
   public void run() {
      if (log.CALL) log.call(ME, "Handling client request ...");
      Parser receiver = new Parser(glob);

      try {
         if (log.TRACE) log.trace(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());

         while (running) {
            try {
               //iStream = sock.getInputStream();
               receiver.parse(iStream);  // blocks until a message arrive

               if (log.TRACE) log.trace(ME, "Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
               if (log.DUMP) log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

               // receive() processes all invocations, only connect()/disconnect() we do locally ...
               if (receive(receiver) == false) {
                  if (MethodName.CONNECT == receiver.getMethodName()) {
                     ConnectQos conQos = new ConnectQos(driver.getGlobal(), receiver.getQos());
                     setLoginName(conQos.getUserId());
                     Thread.currentThread().setName("XmlBlaster.SOCKET.HandleClient.BlockOnInputStreamForMessageFromClient-" + conQos.getUserId());
                     this.ME += "-" + this.loginName;
                     log.info(ME, "Client accepted, coming from host=" + sock.getInetAddress().toString() + " port=" + sock.getPort());
                     callback = new CallbackSocketDriver(this.loginName, this);

                     CallbackAddress[] cbArr = conQos.getSessionCbQueueProperty().getCallbackAddresses();
                     for (int ii=0; cbArr!=null && ii<cbArr.length; ii++) {
                        cbKey = cbArr[ii].getType() + cbArr[ii].getAddress();
                        org.xmlBlaster.protocol.I_CallbackDriver oldCallback = driver.getGlobal().getNativeCallbackDriver(cbKey);
                        if (oldCallback != null) { // Remove old and lost login of client with same callback address
                           log.warn(ME, "Destroying old callback driver '" + cbKey + "' ...");
                           //oldCallback.shutdown(); don't destroy socket, is done by others
                           driver.getGlobal().removeNativeCallbackDriver(cbKey);
                           oldCallback = null;
                        }
                        driver.getGlobal().addNativeCallbackDriver(cbKey, callback); // tell that we are the callback driver as well
                     }

                     ConnectReturnQos retQos = authenticate.connect(conQos);
                     this.sessionId = retQos.getSessionId();
                     receiver.setSessionId(retQos.getSessionId()); // executeResponse needs it

                     executeResponse(receiver, retQos.toXml());
                   }
                  else if (MethodName.DISCONNECT == receiver.getMethodName()) {
                     this.sessionId = null;
                     // Note: the diconnect will call over the CbInfo our shutdown as well
                     // setting sessionId = null prevents that our shutdown calls disconnect() again.
                     authenticate.disconnect(receiver.getSessionId(), receiver.getQos());
                     //executeResponse(receiver, qos);   // The socket is closed already
                     shutdown();
                  }
               }
            }
            catch (XmlBlasterException e) {
               if (log.TRACE) log.trace(ME, "Can't handle message, throwing exception back to client: " + e.toString());
               try {
                  executeExecption(receiver, e);
               }
               catch (Throwable e2) {
                  log.error(ME, "Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
                  shutdown();
               }
            }
            catch (IOException e) {
               if (running != false) { // Only if not triggered by our shutdown:sock.close()
                  log.warn(ME, "Lost connection to client: " + e.toString());
                  shutdown();
               }
            }
            catch (Throwable e) {
               e.printStackTrace();
               log.error(ME, "Lost connection to client: " + e.toString());
               shutdown();
            }
         } // while(running)
      }
      finally {
         driver.removeClient(this);
         closeSocket();
         if (log.TRACE) log.trace(ME, "Deleted thread for '" + loginName + "'.");
      }
   }
}

