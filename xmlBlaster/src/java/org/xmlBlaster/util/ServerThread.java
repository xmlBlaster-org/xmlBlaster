/*------------------------------------------------------------------------------
Name:      ServerThread.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id: ServerThread.java,v 1.7 2002/05/11 09:36:35 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.client.*;


/**
 * Helper to create/start/stop a xmlBlaster server instance in a thread. 
 * <p />
 * TODO: change to use util.Global (or engine.Global as xmlBlaster.Main needs it).
 */
public class ServerThread extends Thread
{
   private static final String ME = "ServerThread";
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
      String[] args = new String[4];
      args[0] = "-port";
      args[1] = "" + serverPort;
      args[2] = "-doBlocking";
      args[3] = "false";
      ServerThread serverThread = new ServerThread(args);
      serverThread.start();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      Log.info(ME, "Server is up and ready");
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
      if (args==null) args = new String[0];

      String[] args2 = new String[args.length + 2];
      for (int ii=0; ii<args.length; ii++)
         args2[ii] = args[ii];
      args2[args.length]   = "-doBlocking";
      args2[args.length+1] = "false";
      ServerThread serverThread = new ServerThread(args2);
      serverThread.start();
      while(!serverThread.isReady()) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      Log.info(ME, "Server is up and ready.");
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
      Log.info(ME, "Server is down!");
   }


   /*
    * Constructor is private, create an instance through ServerThread.starXmlBlaster()
    */
   private ServerThread(String[] args) { this.args = args; }

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
      Log.info(ME, "Starting a xmlBlaster server instance for testing ...");
      xmlBlasterMain = new org.xmlBlaster.Main(args);
      while(!stopServer) {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
      }
      xmlBlasterMain.shutdown();
      stopServer = false;
      Log.info(ME, "Stopping the xmlBlaster server instance ...");
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.ServerThread
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      ServerThread xmlBlaster = ServerThread.startXmlBlaster(7604);
      ServerThread.stopXmlBlaster(xmlBlaster);
      Log.exit(ServerThread.ME, "Good bye");
   }
} // class ServerThread

