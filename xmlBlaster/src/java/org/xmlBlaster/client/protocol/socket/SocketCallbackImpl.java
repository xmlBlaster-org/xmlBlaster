/*------------------------------------------------------------------------------
Name:      SocketCallbackImpl.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: SocketCallbackImpl.java,v 1.6 2002/02/16 11:24:10 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;


import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.protocol.socket.Parser;
import org.xmlBlaster.protocol.socket.Executor;
import org.xmlBlaster.client.protocol.ConnectionException;
import org.xmlBlaster.client.protocol.I_CallbackExtended;

import java.io.IOException;


/**
 * The methods of this callback class are exposed to SOCKET clients,
 * in this case to xmlBlaster when it wants to callback the client.
 * <p />
 * TODO: Do we need a listener garbage collection if a client does not
 * remove itself after a response or on failure?
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
public class SocketCallbackImpl extends Executor implements Runnable
{
   private final String ME;
   private final SocketConnection sockCon;
   /** A unique name for this client socket */
   private String callbackAddressStr = null;

   /** Stop the thread */
   private boolean running = true;

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
      Thread t = new Thread(this);
      t.start();
   }

   /**
    * Starts the callback thread
    */
   public void run()
   {
      Log.info(ME, "Started callback receiver");
      while(running) {

         Parser receiver = new Parser();
         try {
            receiver.parse(iStream);
            if (Log.DUMP) Log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

            receive(receiver);


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
         finally {
            // We remove the listener here, so the SocketConnect does not need to take care of this
            removeResponseListener(receiver.getRequestId());
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
      Log.info(ME, "There are " + responseListenerMap.size() + " messages pending (no response arrived yet");
   }
} // class SocketCallbackImpl

