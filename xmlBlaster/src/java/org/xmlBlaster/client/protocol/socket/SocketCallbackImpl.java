/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: SocketCallbackImpl.java,v 1.2 2002/02/15 12:56:34 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import java.io.InputStream;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;


/**
 * The methods of this callback class are exposed to SOCKET clients,
 * in this case to xmlBlaster when it wants to callback the client.
 * <p />
 * TODO: Do we need a listener garbage collection if a client does not
 * remove itself after a response or on failure?
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class SocketCallbackImpl extends Thread
{
   private final String ME;
   private final SocketConnection sockCon;
   private final InputStream inputStream;
   private final I_CallbackExtended callback;
   /** A unique name for this client socket */
   private String callbackAddressStr = null;

   /** Stop the thread */
   private boolean running = true;

   /**
    * For listeners who want to be informed about return messages or exceptions
    * The key is the String requestId, the value the listener thread I_ResponseListener
    */
   private final Map responseListenerMap = Collections.synchronizedMap(new HashMap());

   /**
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    * @param sockCon    The socket driver main code
    * @param callback   Our implementation of I_CallbackExtended.
    */
   SocketCallbackImpl(SocketConnection sockCon, InputStream inputStream, I_CallbackExtended callback) throws XmlBlasterException
   {
      this.ME = "SocketCallbackImpl-" + sockCon.getLoginName();
      this.sockCon = sockCon;
      this.inputStream = inputStream;
      this.callback = callback;
      this.callbackAddressStr = sockCon.getLocalAddress();
      start();
   }

   public void finalize() {
      Log.info(ME, "Garbage Collected");
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
    * Starts the callback thread
    */
   public void run()
   {
      Log.info(ME, "Started callback receiver");
      while(running) {

         try {
            Parser receiver = new Parser();
            receiver.parse(inputStream);
            Log.info(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

            // Handling callbacks ...
            if (receiver.isInvoke()) {
               // update()
               if (Constants.UPDATE.equals(receiver.getMethodName())) {
                  try {
                     /*String response =*/ callback.update(sockCon.getLoginName(), receiver.getMessageArr());
                     Log.error(ME, "!!!!! send response with 'requestId' is missing");
                  }
                  catch (XmlBlasterException e) {
                     Log.error(ME, "!!!!! send exception with 'requestId' is missing: " + e);
                  }
                  catch (Throwable e) {
                     Log.error(ME, "!!!!! send exception with 'requestId' is missing: " + e);
                  }
               }
               // ping()
               else if (Constants.PING.equals(receiver.getMethodName())) {
                  Log.error(ME, "!!!!! send response for ping with 'requestId' is missing: " + receiver.getRequestId());
               }
               else {
                  Log.info(ME, "Ignoring received message '" + receiver.getMethodName() + "' with requestId=" + receiver.getRequestId() + ", nobody is interested in it");
                  if (Log.DUMP) Log.dump(ME, "Ignoring received message, nobody is interested in it:\n>" + Parser.toLiteral(receiver.createRawMsg()) + "<");
               }
               continue;
            }

            // Handling response or exception ...
            I_ResponseListener listener = (I_ResponseListener)responseListenerMap.get(receiver.getRequestId());
            if (listener == null) {
               // logging should not dump whole message:!!!
               Log.info(ME, "Ignoring received message, nobody is interested in it: >" + Parser.toLiteral(receiver.createRawMsg()) + "<");
               continue;
            }

            if (receiver.isResponse()) {
               if (Constants.GET.equals(receiver.getMethodName())) {
                  listener.responseEvent(receiver.getRequestId(), receiver.getMessageArr());
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

            // We remove the listener here, so the SocketConnect does not need to take care of this
            removeResponseListener(receiver.getRequestId());
         }
         catch(XmlBlasterException e) {
            Log.error(ME, e.toString());
         }
         catch(Throwable e) {
            if (!(e instanceof IOException)) e.printStackTrace();
            Log.error(ME, "Closing connection to server: " + e.toString());
            sockCon.shutdown();
            throw new ConnectionException(ME, e.toString());  // does a sockCon.shutdown(); ?
         }
      }
   }

   /**
    * @return The XML-RPC registry entry of this server, which can be used for the loginQoS
    */
   public CallbackAddress getCallbackHandle()
   {
      CallbackAddress addr = new CallbackAddress("SOCKET");
      addr.setAddress(callbackAddressStr);
      return addr;
   }

   /**
    * Shutdown callback, called by SocketConnection on problems
    */
   public void shutdown() {
      running = false;
      try { inputStream.close(); } catch(IOException e) { Log.warn(ME, e.toString()); }
   }
} // class SocketCallbackImpl

