/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using plain socket
Version:   $Id: SocketCallbackImpl.java,v 1.16 2002/05/02 12:36:39 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.protocol.socket.Executor;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import java.io.IOException;


/**
 * Used for client to receive xmlBlaster callbacks. 
 * <p />
 * One instance of this for each client. 
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 * @see org.xmlBlaster.protocol.socket.Parser
 */
public class SocketCallbackImpl extends Executor implements Runnable, I_CallbackServer
{
   private final String ME;
   private Global glob = null;
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
   SocketCallbackImpl(SocketConnection sockCon) throws XmlBlasterException, IOException
   {
      super(sockCon.getSocket(), null);
      setLoginName(sockCon.getLoginName());
      this.ME = "SocketCallbackImpl-" + sockCon.getLoginName();
      this.sockCon = sockCon;
      this.callbackAddressStr = sockCon.getLocalAddress();
      this.SOCKET_DEBUG = sockCon.SOCKET_DEBUG;

      Thread t = new Thread(this, "XmlBlaster.SOCKET.callback-"+sockCon.getLoginName());
      t.setPriority(XmlBlasterProperty.get("socket.threadPrio", Thread.NORM_PRIORITY));
      t.start();
   }

   /** Initialize and start the callback server */
   public final void initialize(Global glob, String loginName, I_CallbackExtended cbClient) throws XmlBlasterException
   {
      this.glob = glob;
      setCbClient(cbClient); // access callback client in super class Executor:callback
   }

   /**
    * Returns the protocol type. 
    * @return "SOCKET"
    */
   public final String getCbProtocol()
   {
      return "SOCKET";
   }

   /**
    * Returns the callback address. 
    * <p />
    * This is no listen socket, as we need no callback server.
    * It is just the client side socket data of the established connection to xmlBlaster.
    * @return "192.168.2.1:34520"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return sockCon.getLocalAddress();
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
      CallbackAddress addr = new CallbackAddress(glob, "SOCKET");
      addr.setAddress(callbackAddressStr);
      return addr;
   }

   /**
    * Shutdown callback, called by SocketConnection on problems
    * @return true everything is OK, false if probably messages are lost on shutdown
    */
   public boolean shutdownCb() {
      running = false;
      try { iStream.close(); } catch(IOException e) { Log.warn(ME, e.toString()); }
      if (responseListenerMap.size() > 0) {
         Log.warn(ME, "There are " + responseListenerMap.size() + " messages pending without a response");
         return false;
      }
      return true;
   }
} // class SocketCallbackImpl

