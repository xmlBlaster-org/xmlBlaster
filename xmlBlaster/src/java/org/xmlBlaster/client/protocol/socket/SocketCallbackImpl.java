/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using plain socket
Version:   $Id: SocketCallbackImpl.java,v 1.13 2002/02/26 10:46:54 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.protocol.socket.Executor;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import java.io.IOException;


/**
 * Used for client to receive xmlBlaster callbacks. 
 * <p />
 * One instance of this for each client. 
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.socket.Parser
 */
public class SocketCallbackImpl extends Executor implements Runnable
{
   private final String ME;
   /** The connection manager 'singleton' */
   private final SocketConnection sockCon;
   /** A unique name for this client socket */
   private String callbackAddressStr = null;

   /** Stop the thread */
   boolean running = true;

   /**
    * A thread receiving all messages from xmlBlaster, and delivering them back to the client code.
    * @param sockCon    The socket driver main code
    * @param callback   Our implementation of I_CallbackExtended.
    */
   SocketCallbackImpl(SocketConnection sockCon, I_CallbackExtended callback) throws XmlBlasterException, IOException
   {
      super(sockCon.getSocket(), null, callback);
      setLoginName(sockCon.getLoginName());
      this.ME = "SocketCallbackImpl-" + sockCon.getLoginName();
      this.sockCon = sockCon;
      this.callback = callback;
      this.callbackAddressStr = sockCon.getLocalAddress();
      this.SOCKET_DEBUG = sockCon.SOCKET_DEBUG;

      Thread t = new Thread(this, "XmlBlaster.SOCKET.callback-"+sockCon.getLoginName());
      t.setPriority(XmlBlasterProperty.get("socket.threadPrio", Thread.NORM_PRIORITY));
      t.start();
   }

   /**
    * Starts the callback thread
    */
   public void run()
   {
      Log.info(ME, "Started callback receiver");
      Parser receiver = new Parser();
      receiver.SOCKET_DEBUG = SOCKET_DEBUG;
      
      while(running) {

         try {
            receiver.parse(iStream);
            if (SOCKET_DEBUG>1) Log.info(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");
            receive(receiver);
         }
         catch(XmlBlasterException e) {
            Log.error(ME, e.toString());
         }
         catch(Throwable e) {
            if (!(e instanceof IOException)) e.printStackTrace();
            if (running == true) {
               Log.error(ME, "Closing connection to server: " + e.toString());
               sockCon.shutdown();
               throw new ConnectionException(ME, e.toString());  // does a sockCon.shutdown(); ?
            }
            // else a normal disconnect()
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
      try { iStream.close(); } catch(IOException e) { Log.warn(ME, e.toString()); }
      if (responseListenerMap.size() > 0)
         Log.warn(ME, "There are " + responseListenerMap.size() + " messages pending without a response");
   }
} // class SocketCallbackImpl

