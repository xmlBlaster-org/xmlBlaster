/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SocketDriver.java,v 1.15 2002/04/19 10:59:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


/**
 * Socket driver class to invoke the xmlBlaster server over a native message format
 * <p />
 * This "SOCKET:" driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                 SOCKET:org.xmlBlaster.protocol.socket.SocketDriver,\
 *                 RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                 XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver,\
 *                 JDBC:org.xmlBlaster.protocol.jdbc.JdbcDriver
 *Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                         SOCKET:org.xmlBlaster.protocol.socket.CallbackSocketDriver,\
 *                         RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                         XML-RPC:org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver,\
 *                         JDBC:org.xmlBlaster.protocol.jdbc.CallbackJdbcDriver,\
 *                         EMAIL:org.xmlBlaster.protocol.email.CallbackEmailDriver
 * </pre>
 *
 * The variable socket.port (default 7607) sets the socket server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -socket.port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.protocol.socket.SocketDriver#usage()}
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 * @see org.xmlBlaster.protocol.socket.Parser
 */
public class SocketDriver extends Thread implements I_Driver
{
   private static final String ME = "SocketDriver";
   /** The global handle */
   private Global glob;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** Default port of xmlBlaster socket server is 7607 */
   public static final int DEFAULT_SERVER_PORT = 7607;
   /** The port for the socket server */
   private int socketPort = DEFAULT_SERVER_PORT;
   /** The socket server */
   private ServerSocket listen = null;
   /** The URL which clients need to use to access this server, e.g. "server.mars.univers:6701" */
   private String serverUrl = null;
   /** The string representation like "192.168.1.1", useful if multihomed computer */
   private String hostname = null;
   /** xmlBlaster server host */
   private java.net.InetAddress inetAddr = null;
   /** State of server */
   private boolean running = true;
   int SOCKET_DEBUG=0;
   private boolean listenerReady = false;


   /**
    * Creates the driver. 
    * Note: getName() is enforced by interface I_Driver, but is already defined in Thread class
    */
   public SocketDriver()
   {
      super(ME);
      SOCKET_DEBUG = XmlBlasterProperty.get("socket.debug", 0);
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "SOCKET"
    */
   public String getProtocolId()
   {
      return "SOCKET";
   }

   /**
    * Get the address how to access this driver. 
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress()
   {
      return serverUrl; // hostname + ":" + socketPort;
   }

   /**
    * Access the handle to the xmlBlaster authenication core
    */
   I_Authenticate getAuthenticate() {
      return this.authenticate;
   }

   /**
    * Access the handle to the xmlBlaster core
    */
   I_XmlBlaster getXmlBlaster() {
      return this.xmlBlasterImpl;
   }

   /**
    * Start xmlBlaster SOCKET access.
    * <p />
    * Enforced by interface I_Driver.<br />
    * This method returns as soon as the listener socket is alive and ready or on error.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   public void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering init()");
      this.glob = glob;
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      socketPort = XmlBlasterProperty.get("socket.port", 7607);

      if (socketPort < 1) {
         Log.info(ME, "Option socket.port set to " + socketPort + ", socket server not started");
         return;
      }

      hostname = XmlBlasterProperty.get("socket.hostname", (String)null);
      if (hostname == null) {
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            hostname = addr.getHostName();
         } catch (Exception e) {
            Log.info(ME, "Can't determine your hostname");
            hostname = "localhost";
         }
      }
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
         throw new XmlBlasterException("InitSocketFailed", "The host [" + hostname + "] is invalid, try '-socket.hostname=<ip>': " + e.toString());
      }

      start(); // Start the listen thread

      while (!listenerReady) {
         try { Thread.currentThread().sleep(10); } catch( InterruptedException i) {}
      }
   }

   final Global getGlobal()
   {
      return this.glob;
   }

   /**
    * Starts the server socket and waits for clients to connect. 
    */
   public void run()
   {
      try {
         int backlog = XmlBlasterProperty.get("socket.backlog", 50); // queue for max 50 incoming connection request
         listen = new ServerSocket(socketPort, backlog, inetAddr);
         Log.info(ME, "Started successfully socket driver on hostname=" + hostname + " port=" + socketPort);
         serverUrl = hostname + ":" + socketPort;
         listenerReady = true;
         while (running) {
            Socket accept = listen.accept();
            //Log.trace(ME, "New incoming request on port=" + socketPort + " ...");
            if (!running) {
               Log.info(ME, "Closing server " + hostname + " on port " + socketPort + ".");
               break;
            }
            HandleClient hh = new HandleClient(this, accept);
         }
      }
      catch (java.net.UnknownHostException e) {
         Log.error(ME, "Socket server problem, IP address '" + hostname + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         Log.error(ME, "Socket server problem, port " + hostname + ":" + socketPort + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         Log.info(ME, "Socket " + hostname + ":" + socketPort + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         Log.error(ME, "Socket server problem on " + hostname + ":" + socketPort + ": " + e.toString());
      }
      finally {
         listenerReady = true;
         if (listen != null) {
            try { listen.close(); } catch (java.io.IOException e) { Log.warn(ME, "listen.close()" + e.toString()); }
            listen = null;
         }
      }
   }


   /**
    * Close the listener port, the driver shuts down. 
    */
   public void shutdown()// throws IOException
   {
      if (Log.CALL) Log.call(ME, "Entering shutdown");
      
      //System.out.println(org.jutils.runtime.StackTrace.getStackTrace());

      running = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), socketPort);
            socket.close();
         } catch (java.io.IOException e) {
            Log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (listen != null) {
            listen.close();
            listen = null;
            //Log.info(ME, "Socket driver stopped, all resources released.");
         }
      } catch (java.io.IOException e) {
         Log.warn(ME, "shutdown problem: " + e.toString());
      }

      serverUrl = null;
      Log.info(ME, "Socket driver stopped, all resources released.");
   }

   /**
    * Command line usage.
    * <p />
    * <ul>
    *  <li><i>-socket.port</i>        The SOCKET web server port [7607]</li>
    *  <li><i>-socket.hostname</i>    Specify a hostname where the SOCKET web server runs
    *                          Default is the localhost.</li>
    *  <li><i>-socket.backlog</i>     Queue size for incoming connection request [50]</li>
    *  <li><i>-socket.debug</i>       1 or 2 switches on detailed SOCKET debugging [0]</li>
    * </ul>
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "SocketDriver options:\n";
      text += "   -socket.port        The SOCKET server port [7607].\n";
      text += "   -socket.hostname    Specify a hostname where the SOCKET server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -socket.backlog     Queue size for incoming connection request [50].\n";
      text += "   -socket.threadPrio  The priority 1=min - 10=max of the listener thread [5].\n";
      text += "   -socket.debug       1 or 2 switches on detailed SOCKET debugging [0].\n";
      text += "\n";
      return text;
   }
}
