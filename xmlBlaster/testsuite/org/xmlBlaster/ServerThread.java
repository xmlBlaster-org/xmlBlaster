/*------------------------------------------------------------------------------
Name:      ServerThread.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to create/start/stop a xmlBlaster server in a thread
Version:   $Id: ServerThread.java,v 1.2 2000/03/13 16:17:03 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;


/**
 * Helper to create/start/stop a xmlBlaster server instance in a thread.
 */
public class ServerThread extends Thread
{
   private static final String ME = "ServerThread";
   /** This is the default xmlBlaster server port, which is probably blocked by another xmlBlaster server */
   private int port = 7609;
   /** Invoke thread.stopServer=true; to stop it. */
   private boolean stopServer = false;
   private org.xmlBlaster.Main xmlBlasterMain = null;


   /**
    * Creates an instance of xmlBlaster and starts the server.
    * <p />
    * The returned xmlBlaster handle allows to control more than one
    * xmlBlaster server simultaneously (on different ports).
    * @param port Default port is 7609
    * @return the xmlBlaster handle, pass this to method stopXmlBlaster
    *         to shutdown the server again.
    */
   public static ServerThread startXmlBlaster(int serverPort)
   {
      ServerThread serverThread = new ServerThread(serverPort);
      serverThread.start();
      Util.delay(3000L);    // Wait some time
      Log.info(ME, "Server is up!");
      return serverThread;
   }


   /**
    * Stop xmlBlaster server.
    * @param serverThread The handle you got from startXmlBlaster()
    */
   public static void stopXmlBlaster(ServerThread serverThread)
   {
      serverThread.stopServer = true;
      Util.delay(500L);    // Wait some time
      Log.info(ME, "Server is down!");
   }


   /*
    * Constructor is private, create an instance through ServerThread.starXmlBlaster()
    */
   private ServerThread(int port) { this.port = port; }


   /*
    * Start the server
    */
   public void run() {
      Log.info(ME, "Starting a xmlBlaster server instance for testing ...");
      String[] args = new String[4];
      args[0] = "-iorPort";
      args[1] = "" + port;
      args[2] = "-doBlocking";
      args[3] = "false";
      xmlBlasterMain = new org.xmlBlaster.Main(args);
      while(!stopServer) {
         try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      }
      xmlBlasterMain.shutdown(false);
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
      Log.setLogLevel(args);
      ServerThread xmlBlaster = ServerThread.startXmlBlaster(7604);
      ServerThread.stopXmlBlaster(xmlBlaster);
      Log.exit(ServerThread.ME, "Good bye");
   }
} // class ServerThread

