/*------------------------------------------------------------------------------
Name:      WorkerThread.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.xbformat.MsgInfo;

import java.io.IOException;

/**
 * Takes a received messages and invokes the desired action. 
 * <p />
 * Usually this is an update() or probably a ping()
 */
public class WorkerThread extends Thread
{
   private final String ME;
   
   private static Logger log = Logger.getLogger(WorkerThread.class.getName());

   /** Contains the received message */
   private final MsgInfo parser;

   /** The manager class, my creator and my destiny */
   private final SocketCallbackImpl cbHandler;

   /**
    * Creates the thread. 
    */
   public WorkerThread(Global glob, SocketCallbackImpl cbHandler, MsgInfo receiver) {
      super("XmlBlaster."+cbHandler.getType()+".cbWorkerThread");

      this.cbHandler = cbHandler;
      this.ME = "WorkerThread-" + cbHandler.getSocketConnection().getLoginName();
      this.parser = receiver;
   }

   /**
    * Starts the thread which calls update() into client code. 
    */
   public void run() {
      try {
         if (log.isLoggable(Level.FINE)) log.fine("Starting worker thread, invoking client code with received message");
         boolean processed = cbHandler.receiveReply(this.parser, SocketUrl.SOCKET_TCP);  // Parse the message and invoke callback to client code
         if (!processed)
            log.warning("Received message is not processed: " + this.parser.toLiteral());
         else {
            if (log.isLoggable(Level.FINE)) log.fine("Worker thread done");
         }
      }
      catch (XmlBlasterException e) {
         log.severe(e.toString());
      }
      catch (Throwable e) {
         if (!(e instanceof IOException)) e.printStackTrace();
         if (e instanceof java.net.SocketException) { // : Socket closed
            if (log.isLoggable(Level.FINE)) log.fine("Shutting down because of: " + e.toString());
         }
         else {
            log.severe("Shutting down because of: " + e.toString());
         }
         try {
            cbHandler.getSocketConnection().shutdown();
         }
         catch (XmlBlasterException ex) {
            log.severe("run() could not shutdown correctly. " + ex.getMessage());
         }
      }
   }
}
