/*------------------------------------------------------------------------------
Name:      Executor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Executor class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: Executor.java,v 1.2 2002/02/15 22:45:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.ConnectReturnQos;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.ClientInfo;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.protocol.ConnectionException; // Move java file to server package!

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


/**
 * Send/receive messages over outStream and inStream. 
 * <p />
 * A common base class for socket based messaging.
 * Allows to block during a request and deliver the return message
 * to the waiting thread.
 */
public abstract class Executor
{
   private String ME = "ExecutorRequest";
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   /** The socket connection to/from one client */
   protected Socket sock;
   /** Reading from socket */
   protected InputStream iStream;
   /** Writing to socket */
   protected OutputStream oStream;
   /** Praefix for requestId */
   protected String praefix = null;
   /** The unique client sessionId */
   protected String sessionId = null;
   /** The client login name */
   protected String loginName = "";
   /** How long to block on remote call */
   protected long responseWaitTime = 0;

   /**
    * For listeners who want to be informed about return messages or exceptions
    * The key is the String requestId, the value the listener thread I_ResponseListener
    */
   protected final Map responseListenerMap = Collections.synchronizedMap(new HashMap());

   /**
    * Constructor for SocketConnection, which is only interested in
    * execute() method and common attributes
    */
   protected Executor() {
   }

   /**
    * Constructor for client specific code:<br />
    * SocketCallbackImpl on client side
    * HandleClient on server side
    * @param sock The open socket to/from a specific client
    */
   protected Executor(Socket sock) throws IOException {
      this.sock = sock;
      this.oStream = sock.getOutputStream();
      this.iStream = sock.getInputStream();
      this.responseWaitTime = XmlBlasterProperty.get("socket.responseTimeout", Constants.MINUTE_IN_MILLIS);
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
    * Adds the specified subscription listener to receive subscribe/unSubscribe events.
    */
   public void addResponseListener(String requestId, I_ResponseListener l) {
      if (requestId == null || l == null) {
         throw new IllegalArgumentException("addResponseListener() with requestId=null");
      }
      synchronized (responseListenerMap) {
         responseListenerMap.put(requestId, l);
      }
   }


   /**
    * Removes the specified listener.
    */
   public void removeResponseListener(String requestId) {
      if (requestId == null) {
         throw new IllegalArgumentException("removeResponseListener() with requestId=null");
      }
      synchronized (responseListenerMap) {
         Object o = responseListenerMap.remove(requestId);
         if (o == null) Log.error(ME, "removeResponseListener(" + requestId + ") entry not found");
      }
   }

   /**
    * Send a message and block until the response arrives. 
    * <p/>
    * We simulate RPC (remote procedure call) here.
    * This should be thread save and may be invoked by many
    * client threads in parallel (though i have not tested it).
    * @param praefix The praefix to create a unique requestId namspace (just pass the loginName)
    * @return the response object of the request, of type String(QoS), MessageUnit[] or XmlBlasterException
    */
   public Object execute(Parser parser, String praefix, boolean expectingResponse) throws XmlBlasterException, IOException {

      String requestId = parser.createRequestId(praefix);
      final Object[] response = new Object[2];  // As only final variables are accessable from the inner class, we put changeable variables in this array
      response[0] = response[1] = null;
      final Object monitor = new Object();

      if (expectingResponse) {
         addResponseListener(requestId, new I_ResponseListener() {
            public void responseEvent(String reqId, Object responseObj) {
               if (Log.TRACE) Log.trace(ME+".responseEvent()", "RequestId=" + reqId + ": return value arrived ...");
               synchronized(monitor) {
                  response[0] = responseObj;
                  response[1] = ""; // marker that notify() is called
                  monitor.notify();
               }
            }
         });
      }

      byte[] rawMsg = parser.createRawMsg();
      if (Log.DUMP) Log.dump(ME, Parser.toLiteral(rawMsg));
      oStream.write(rawMsg);
      oStream.flush();

      if (!expectingResponse)
         return null;
      
      //if (Log.TRACE) Log.trace(ME, parser.getMethodName() + "(" + requestId + ") send, waiting for response ...");
      
      try {
         synchronized(monitor) {
            // If response is faster, we will go into wait() after notify() TODO!!!
            monitor.wait(responseWaitTime);
            if (response[1] != null) {
               if (Log.TRACE) Log.trace(ME, "Waking up (waited on " + parser.getMethodName() + "(" + requestId + ") response)");
               if (Log.DUMP) Log.dump(ME, "Waking up (waited on " + parser.getMethodName() + "(" + requestId + ") response): " + response[0]);
               if (response[0] instanceof XmlBlasterException)
                  throw (XmlBlasterException)response[0];
               return response[0];
            }
            else {
               String str = "Timeout of " + responseWaitTime + " milliseconds occured when waiting on " + parser.getMethodName() + "(" + requestId + ") response. You can change it with -socket.responseTimeout <millis>";
               throw new XmlBlasterException(ME, str);
            }
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
      Parser returner = new Parser(Parser.RESPONSE_TYPE, receiver.getRequestId(),
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
      if (Log.DUMP) Log.dump(ME, "Successful " + receiver.getMethodName() + "(), sending back to client '" + Parser.toLiteral(returner.createRawMsg()) + "'");
      oStream.write(returner.createRawMsg());
      oStream.flush();
      Log.info(ME, "Successful sent response for " + receiver.getMethodName() + "()");
   }

   /**
    * Send a one way exception back to the other side
    */
   protected final void executeExecption(Parser receiver, XmlBlasterException e) throws XmlBlasterException, IOException {
      Parser returner = new Parser(Parser.EXCEPTION_TYPE, receiver.getRequestId(), receiver.getMethodName(), receiver.getSessionId());
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
   }

   //abstract boolean shutdown();
   //abstract void shutdown();
}

