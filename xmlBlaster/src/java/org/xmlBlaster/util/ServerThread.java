/*------------------------------------------------------------------------------
Name:      ServerThread.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id: ServerThread.java,v 1.8 2002/05/15 12:58:54 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.*;


/**
 * Helper to create/start/stop a xmlBlaster server instance in a thread. 
 * <p />
 * TODO: change to use util.Global (or engine.Global as xmlBlaster.Main needs it).
 */
public class ServerThread extends Thread
{
   private static final String ME = "ServerThread";
   private Global glob;
   LogChannel log;
   /** Key/value array, containing command line arguments or xmlBlaster.properties variables to be used */
   private String[] args;
   /** Invoke thread.stopServer=true; to stop it. */
   private boolean stopServer = false;
   private org.xmlBlaster.Main xmlBlasterMain = null;


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * The returned xmlBlaster handle allows to control more than one
    * xmlBlaster server simultaneously (on different ports).
    * @param port Default port is 3412
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static ServerThread startXmlBlaster(int serverPort)
   {
      Global glob = Global.instance().getClone(null);
      String[] args = new String[4];
      args[0] = "-port";
      args[1] = "" + serverPort;
      args[2] = "-doBlocking";
      args[3] = "false";
      glob.init(args);
      ServerThread serverThread = new ServerThread(glob);
      serverThread.start();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready");
      return serverThread;
   }


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * @param args Key/value array, containing command line arguments or xmlBlaster.properties variables to be used
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static ServerThread startXmlBlaster(String[] args)
   {
      return startXmlBlaster(args, (String)null);
   }

   public static ServerThread startXmlBlaster(String[] args, String clusterNodeId)
   {
      Global glob = Global.instance().getClone(args);
      String[] args2 = { "-doBlocking", "false" };
      glob.init(args2);
      ServerThread serverThread = new ServerThread(glob);
      serverThread.start();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready.");
      return serverThread;
   }


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * @param args Key/value array, containing command line arguments or xmlBlaster.properties variables to be used
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static ServerThread startXmlBlaster(Global glob)
   {
      String[] args = { "-doBlocking", "false" };
      glob.init(args);
      ServerThread serverThread = new ServerThread(glob);
      serverThread.start();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      glob.getLog(glob.getId()).info(ME, "Server is up and ready.");
      return serverThread;
   }


   /**
    * Stop xmlBlaster server.
    * @param serverThread The handle you got from startXmlBlaster()
    */
   public static void stopXmlBlaster(ServerThread serverThread)
   {
      serverThread.stopServer = true;
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {} // Wait some time
      serverThread.log.info(ME, "Server is down!");
   }


   /*
    * Constructor is private, create an instance through ServerThread.starXmlBlaster()
    */
   private ServerThread(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(glob.getId());
   }

   /**
    * @return true if xmlBlaster has started and is ready for requests
    */
   private boolean isReady() {
      return xmlBlasterMain != null;
   }


   /*
    * Start the server
    */
   public void run() {
      log.info(ME, "Starting a xmlBlaster server instance for testing ...");
      xmlBlasterMain = new org.xmlBlaster.Main(glob);
      while(!stopServer) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      xmlBlasterMain.shutdown();
      stopServer = false;
      log.info(ME, "Stopping the xmlBlaster server instance ...");
   }


   /**
    * Invoke: java org.xmlBlaster.util.ServerThread
    * <p />
    * instead of the JacORB ORB, which won't work.
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         glob.getLog(null).error("ServerThread", "Initialization of property failed, bye!");
         System.exit(1);
      }
      ServerThread xmlBlaster = ServerThread.startXmlBlaster(glob);
      ServerThread.stopXmlBlaster(xmlBlaster);
   }
} // class ServerThread

