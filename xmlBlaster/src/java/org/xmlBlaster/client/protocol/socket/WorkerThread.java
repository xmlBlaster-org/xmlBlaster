/*------------------------------------------------------------------------------
Name:      WorkerThread.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.socket;

import org.jutils.log.LogChannel;
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
   
   private final LogChannel log;

   /** Contains the received message */
   private final MsgInfo parser;

   /** The manager class, my creator and my destiny */
   private final SocketCallbackImpl cbHandler;

   /**
    * Creates the thread. 
    */
   public WorkerThread(Global glob, SocketCallbackImpl cbHandler, MsgInfo receiver) {
      super("XmlBlaster."+cbHandler.getType()+".cbWorkerThread");
      this.log = glob.getLog("socket");
      this.cbHandler = cbHandler;
      this.ME = "WorkerThread-" + cbHandler.getSocketConnection().getLoginName();
      this.parser = receiver;
   }

   /**
    * Starts the thread which calls update() into client code. 
    */
   public void run() {
      try {
         if (log.TRACE) log.trace(ME, "Starting worker thread, invoking client code with received message");
         cbHandler.receiveReply(this.parser, SocketUrl.SOCKET_TCP);  // Parse the message and invoke callback to client code
         if (log.TRACE) log.trace(ME, "Worker thread done");
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.toString());
      }
      catch (Throwable e) {
         if (!(e instanceof IOException)) e.printStackTrace();
         if (e instanceof java.net.SocketException) { // : Socket closed
            if (log.TRACE) log.trace(ME, "Shutting down because of: " + e.toString());
         }
         else {
            log.error(ME, "Shutting down because of: " + e.toString());
         }
         try {
            cbHandler.getSocketConnection().shutdown();
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "run() could not shutdown correctly. " + ex.getMessage());
         }
      }
   }
}
