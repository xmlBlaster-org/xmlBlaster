/*------------------------------------------------------------------------------
Name:      Executor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Send/receive messages over outStream and inStream. 
Version:   $Id: Executor.java,v 1.19 2002/05/11 09:36:34 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.protocol.ConnectionException; // Move java file to server package!
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import EDU.oswego.cs.dl.util.concurrent.Latch;

import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


/**
 * Send/receive messages over outStream and inStream. 
 * <p />
 * A common base class for socket based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public abstract class Executor implements ExecutorBase
{
   private String ME = "SocketExecutor";
   private final Global glob;
   /** The socket connection to/from one client */
   protected Socket sock;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** The praefix to create a unique requestId namspace (is set to the loginName) */
   protected String praefix = null;
   /** The unique client sessionId */
   protected String sessionId = null;
   /** The client login name */
   protected String loginName = "";
   /** How long to block on remote call */
   protected long responseWaitTime = 0;
   /** This is the client side */
   protected I_CallbackExtended cbClient = null;
   /** The singleton handle for this xmlBlaster server (the server side) */
   protected I_XmlBlaster xmlBlasterImpl = null;
   /** To avoid creating a new dummy on each request, we do it here */
   private final String DUMMY_OBJECT = "";
   /** Set debug level */
   protected int SOCKET_DEBUG=0;

   /**
    * For listeners who want to be informed about return messages or exceptions,
    * the invocation is blocking during this period.
    * <p />
    * The key is the String requestId, the value the listener thread I_ResponseListener
    */
   protected final Map responseListenerMap = Collections.synchronizedMap(new HashMap());


   /**
    * Used by SocketCallbackImpl on client side, uses I_CallbackExtended to invoke client classes
    * <p />
    * Used by HandleClient on server side, uses I_XmlBlaster to invoke xmlBlaster core 
    * <p />
    * This executor has mixed client and server specific code for two reasons:<br />
    * - Possibly we can use the same socket between two xmlBlaster server (load balance)<br />
    * - Everything is together<br />
    * @param sock The open socket to/from a specific client
    * @param xmlBlasterImpl Handle for the server implementation
    */
   protected Executor(Global glob, Socket sock, I_XmlBlaster xmlBlasterImpl) throws IOException {
      this.glob = glob;
      this.sock = sock;
      this.xmlBlasterImpl = xmlBlasterImpl;
      this.oStream = sock.getOutputStream();
      this.iStream = sock.getInputStream();
      this.responseWaitTime = glob.getProperty().get("socket.responseTimeout", Constants.MINUTE_IN_MILLIS);
   }

   public final void setCbClient(I_CallbackExtended cbClient)
   {
      this.cbClient = cbClient;
   }

   public final OutputStream getOutputStream() {
      return this.oStream;
   }

   public final InputStream getInputStream() {
      return this.iStream;
   }

   public void finalize() {
      Log.info(ME, "Garbage Collected");
   }

   public Socket getSocket() throws ConnectionException
   {
      if (this.sock == null) {
         if (Log.TRACE) Log.trace(ME, "No socket connection available.");
         throw new ConnectionException(ME+".init", "No plain socket connection available.");
      }
      return this.sock;
   }

   /**
    * Sets the loginName and automatically the requestId as well
    */
   protected final void setLoginName(String loginName) {
      this.loginName = loginName;
      if (loginName != null && loginName.length() > 0)
         this.praefix = this.loginName + ":";
      else
         this.praefix = null;
   }

   /**
    * Adds the listener to receive response/exception events.
    */
   public final void addResponseListener(String requestId, I_ResponseListener l) {
      if (requestId == null || l == null) {
         throw new IllegalArgumentException("addResponseListener() with requestId=null");
      }
      responseListenerMap.put(requestId, l);
   }


   /**
    * Removes the specified listener.
    */
   public final void removeResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("removeResponseListener() with requestId=null");
      }
      synchronized (responseListenerMap) {
         Object o = responseListenerMap.remove(requestId);
         if (o == null) Log.error(ME, "removeResponseListener(" + requestId + ") entry not found");
      }
   }


   /**
    * Get the response listener object
    */
   public final I_ResponseListener getResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("getResponseListener() with requestId=null");
      }
      return (I_ResponseListener)responseListenerMap.get(requestId);
   }


   /**
    * Handle common messages
    * @return false: for connect() and disconnect() which must be handled by the base class
    */
   protected final boolean receive(Parser receiver) throws XmlBlasterException, IOException {

      if (Log.TRACE || SOCKET_DEBUG>0)  Log.info(ME, "Receiving '" + receiver.getType() + "' message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");

      if (receiver.isInvoke()) {
         // handling invocations ...

         if (Constants.PUBLISH_ONEWAY.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1) {
               Log.error(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
               return true;
            }
            xmlBlasterImpl.publishOneway(receiver.getSessionId(), arr);
         }
         else if (Constants.PUBLISH.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
            String[] response = xmlBlasterImpl.publishArr(receiver.getSessionId(), arr);
            executeResponse(receiver, response);
         }
         else if (Constants.UPDATE_ONEWAY.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1) {
               Log.error(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
               return true;
            }
            this.cbClient.updateOneway(receiver.getSessionId(), arr);
         }
         else if (Constants.UPDATE.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length < 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, missing arguments");
            String[] response = this.cbClient.update(receiver.getSessionId(), arr);
            executeResponse(receiver, response);
         }
         else if (Constants.GET.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            MessageUnit[] response = xmlBlasterImpl.get(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
            executeResponse(receiver, response);
         }
         else if (Constants.PING.equals(receiver.getMethodName())) {
            executeResponse(receiver, Constants.RET_OK); // "<qos><state id='OK'/></qos>"
         }
         else if (Constants.SUBSCRIBE.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String response = xmlBlasterImpl.subscribe(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
            executeResponse(receiver, response);
         }
         else if (Constants.UNSUBSCRIBE.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            xmlBlasterImpl.unSubscribe(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
            // !!! TODO better return value?
            executeResponse(receiver, Constants.RET_OK);
         }
         else if (Constants.ERASE.equals(receiver.getMethodName())) {
            MessageUnit[] arr = receiver.getMessageArr();
            if (arr == null || arr.length != 1)
               throw new XmlBlasterException(ME, "Invocation of " + receiver.getMethodName() + "() failed, wrong arguments");
            String[] response = xmlBlasterImpl.erase(receiver.getSessionId(), arr[0].getXmlKey(), arr[0].getQos());
            executeResponse(receiver, response);
         }
         else if (Constants.CONNECT.equals(receiver.getMethodName())) {
            return false;
         }
         else if (Constants.DISCONNECT.equals(receiver.getMethodName())) {
            return false;
         }
         else {
            Log.info(ME, "Ignoring received message '" + receiver.getMethodName() + "' with requestId=" + receiver.getRequestId() + ", nobody is interested in it");
            if (SOCKET_DEBUG>1) Log.info(ME, "Ignoring received message, nobody is interested in it:\n>" + Parser.toLiteral(receiver.createRawMsg()) + "<");
         }
         
         return true;
      }

      // Handling response or exception ...
      I_ResponseListener listener = getResponseListener(receiver.getRequestId());
      if (listener == null) {
         Log.warn(ME, "Ignoring received '" + receiver.getMethodName() + "' message id=" + receiver.getRequestId() + ", nobody is interested in it");
         if (SOCKET_DEBUG>1) Log.info(ME, "Ignoring received message, nobody is interested in it: >" + Parser.toLiteral(receiver.createRawMsg()) + "<");
         return true;
      }
      removeResponseListener(receiver.getRequestId());

      if (receiver.isResponse()) {
         if (Constants.GET.equals(receiver.getMethodName())) {
            listener.responseEvent(receiver.getRequestId(), receiver.getMessageArr());
         }
         else if (Constants.ERASE.equals(receiver.getMethodName()) ||
                  Constants.UPDATE.equals(receiver.getMethodName()) ||
                  Constants.PUBLISH.equals(receiver.getMethodName())) {
            listener.responseEvent(receiver.getRequestId(), receiver.getQosArr());
         }
         else {
            listener.responseEvent(receiver.getRequestId(), receiver.getQos());
         }
      }
      else if (receiver.isException()) { // XmlBlasterException
         listener.responseEvent(receiver.getRequestId(), receiver.getException());
      }
      else {
         Log.error(ME, "Invalid response message");
         listener.responseEvent(receiver.getRequestId(), new XmlBlasterException(ME, "Invalid response message '" + receiver.getMethodName()));
      }

      return true;
   }


   /**
    * Send a message and block until the response arrives. 
    * <p/>
    * We simulate RPC (remote procedure call) here.
    * This should be thread save and may be invoked by many
    * client threads in parallel (though i have not tested it).
    * @param expectingResponse WAIT_ON_RESPONSE=true or ONEWAY=false
    * @return the response object of the request, of type String(QoS), MessageUnit[] or XmlBlasterException
    */
   public Object execute(Parser parser, boolean expectingResponse) throws XmlBlasterException, IOException {

      String requestId = parser.createRequestId(praefix);
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Invoking  parser type='" + parser.getType() + "' message " + parser.getMethodName() + "(requestId=" + requestId + ") expectingResponse=" + expectingResponse);

      final Object[] response = new Object[1];  // As only final variables are accessable from the inner class, we put the response in this array
      response[0] = null;
      final Latch startSignal = new Latch(); // defaults to false

      // Register the return value / Exception listener ...
      if (expectingResponse) {
         addResponseListener(requestId, new I_ResponseListener() {
            public void responseEvent(String reqId, Object responseObj) {
               if (Log.TRACE) Log.trace(ME+".responseEvent()", "RequestId=" + reqId + ": return value arrived ...");
               response[0] = responseObj;
               startSignal.release(); // wake up
            }
         });
      }

      // Send the message / method invocation ...
      byte[] rawMsg = parser.createRawMsg();
      if (SOCKET_DEBUG>1) Log.info(ME, "Sending now : >" + Parser.toLiteral(rawMsg) + "<");
      oStream.write(rawMsg);
      oStream.flush();
      // if (Log.TRACE) Log.trace(ME, "Successfully sent " + parser.getNumMessages() + " messages");

      //if (SOCKET_DEBUG>1) Log.info(ME, "Successful sent message: >" + Parser.toLiteral(rawMsg) + "<");

      if (!expectingResponse)
         return null;
      
      // Waiting for the response to arrive ...
      try {
         boolean awaikened = startSignal.attempt(responseWaitTime); // block max. milliseconds
         if (awaikened) {
            if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Waking up, got response for " + parser.getMethodName() + "(requestId=" + requestId + ")");
            if (SOCKET_DEBUG>1) Log.info(ME, "Response for " + parser.getMethodName() + "(" + requestId + ") is: " + response[0].toString());
            if (response[0] instanceof XmlBlasterException)
               throw (XmlBlasterException)response[0];
            return response[0];
         }
         else {
            String str = "Timeout of " + responseWaitTime + " milliseconds occured when waiting on " + parser.getMethodName() + "(" + requestId + ") response. You can change it with -socket.responseTimeout <millis>";
            throw new XmlBlasterException(ME, str);
         }
      }
      catch (InterruptedException e) {
         throw new XmlBlasterException(ME, "Waking up (waited on " + parser.getMethodName() + "(" + requestId + ") response): " + e.toString());
      }
   }

   /**
    * Send a one way message back to the other side
    */
   protected final void executeResponse(Parser receiver, Object response) throws XmlBlasterException, IOException {
      Parser returner = new Parser(Parser.RESPONSE_BYTE, receiver.getRequestId(),
                           receiver.getMethodName(), receiver.getSessionId());
      if (response instanceof String)
         returner.addMessage((String)response);
      else if (response instanceof String[])
         returner.addMessage((String[])response);
      else if (response instanceof MessageUnit[])
         returner.addMessage((MessageUnit[])response);
      else if (response instanceof MessageUnit)
         returner.addMessage((MessageUnit)response);
      else
         throw new XmlBlasterException(ME, "Invalid response data type " + response.toString());
      oStream.write(returner.createRawMsg());
      oStream.flush();
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Successfully sent response for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (SOCKET_DEBUG>1) Log.info(ME, "Successful sent response for " + receiver.getMethodName() + "() >" + Parser.toLiteral(returner.createRawMsg()) + "<");
   }

   /**
    * Send a one way exception back to the other side
    */
   protected final void executeExecption(Parser receiver, XmlBlasterException e) throws XmlBlasterException, IOException {
      Parser returner = new Parser(Parser.EXCEPTION_BYTE, receiver.getRequestId(), receiver.getMethodName(), receiver.getSessionId());
      returner.setChecksum(false);
      returner.setCompressed(false);
      returner.addException(e);
      oStream.write(returner.createRawMsg());
      oStream.flush();
      /*
      try {
         oStream.write(returner.createRawMsg());
         oStream.flush();
      }
      catch (Throwable e2) {
         Log.error(ME, "Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
         shutdown();
      }
      */
      if (Log.TRACE || SOCKET_DEBUG>0) Log.info(ME, "Successfully sent exception for " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
      if (SOCKET_DEBUG>1) Log.info(ME, "Successful sent exception for " + receiver.getMethodName() + "() >" + Parser.toLiteral(returner.createRawMsg()) + "<");
   }

   //abstract boolean shutdown();
   //abstract void shutdown();
}

