/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SocketDriver.java,v 1.32 2003/03/27 10:32:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;


/**
 * Socket driver class to invoke the xmlBlaster server over a native message format
 * <p />
 * This "SOCKET:" driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 * ProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.protocol.socket.SocketDriver
 *
 * CbProtocolPlugin[SOCKET][1.0]=org.xmlBlaster.protocol.socket.CallbackSocketDriver
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
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see org.xmlBlaster.protocol.socket.Parser
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class SocketDriver extends Thread implements I_Driver
{
   private String ME = "SocketDriver";
   /** The global handle */
   private Global glob;
   private LogChannel log;
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
   private boolean listenerReady = false;
   /** Remember all client connections */
   private Set handleClientSet = new HashSet();


   /**
    * Creates the driver. 
    * Note: getName() is enforced by interface I_Driver, but is already defined in Thread class
    */
   public SocketDriver() {
      super("XmlBlaster.SocketDriver");
      setDaemon(true);
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "SOCKET"
    */
   public String getProtocolId() {
      return "SOCKET";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) 
      throws XmlBlasterException {
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");
      try {
         Authenticate authenticate = engineGlob.getAuthenticate();
         if (authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }
         init(glob, authenticate, xmlBlasterImpl);
         activate();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Could'nt initialize the driver.", ex);
      }
   }

   /**
    * Get the address how to access this driver. 
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress() {
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
      this.glob = glob;
      this.ME = "SocketDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("socket");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      socketPort = glob.getProperty().get("socket.port", 7607);

      if (socketPort < 1) {
         log.info(ME, "Option socket.port set to " + socketPort + ", socket server not started");
         return;
      }

      hostname = glob.getProperty().get("socket.hostname", (String)null);
      if (hostname == null) {
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            hostname = addr.getHostName();
         } catch (Exception e) {
            log.info(ME, "Can't determine your hostname");
            hostname = "localhost";
         }
      }
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
         throw new XmlBlasterException("InitSocketFailed", "The host [" + hostname + "] is invalid, try '-socket.hostname=<ip>': " + e.toString());
      }

      serverUrl = hostname + ":" + socketPort;
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");
      start(); // Start the listen thread

      while (!listenerReady) {
         try { Thread.currentThread().sleep(10); } catch( InterruptedException i) {}
      }
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      running = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), socketPort);
            socket.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      try {
         if (listen != null) {
            listen.close();
            listen = null;
            //log.info(ME, "Socket driver stopped, all resources released.");
         }
      } catch (java.io.IOException e) {
         log.warn(ME, "shutdown problem: " + e.toString());
      }

      // shutdown all clients connected
      while (true) {
         HandleClient h = null;
         synchronized (handleClientSet) {
            Iterator it = handleClientSet.iterator();
            if (it.hasNext()) {
               h = (HandleClient)it.next();
               it.remove();
            }
            else
               break;
         }
         if (h == null)
            break;
         h.shutdown();
      }
      synchronized (handleClientSet) {
        handleClientSet.clear();
      }
   }

   final void removeClient(HandleClient h) {
      synchronized (handleClientSet) {
         handleClientSet.remove(h);
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
         int backlog = glob.getProperty().get("socket.backlog", 50); // queue for max 50 incoming connection request
         listen = new ServerSocket(socketPort, backlog, inetAddr);
         log.info(ME, "Started successfully socket driver on hostname=" + hostname + " port=" + socketPort);
         serverUrl = hostname + ":" + socketPort;
         listenerReady = true;
         while (running) {
            Socket accept = listen.accept();
            //log.trace(ME, "New incoming request on port=" + socketPort + " ...");
            if (!running) {
               log.info(ME, "Closing server " + hostname + " on port " + socketPort + ".");
               break;
            }
            HandleClient hh = new HandleClient(glob, this, accept);
            synchronized (handleClientSet) {
               handleClientSet.add(hh);
            }
         }
      }
      catch (java.net.UnknownHostException e) {
         log.error(ME, "Socket server problem, IP address '" + hostname + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         log.error(ME, "Socket server problem, port " + hostname + ":" + socketPort + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         log.info(ME, "Socket " + hostname + ":" + socketPort + " closed successfully: " + e.toString());
      }
      catch (IOException e) {
         log.error(ME, "Socket server problem on " + hostname + ":" + socketPort + ": " + e.toString());
      }
      finally {
         listenerReady = true;
         if (listen != null) {
            try { listen.close(); } catch (java.io.IOException e) { log.warn(ME, "listen.close()" + e.toString()); }
            listen = null;
         }
      }
   }


   /**
    * Close the listener port, the driver shuts down. 
    */
   public void shutdown() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering shutdown");
      
      //System.out.println(org.jutils.runtime.StackTrace.getStackTrace());

      try {
         deActivate();
      } catch (XmlBlasterException e) {
         log.error(ME, e.toString());
      }

      serverUrl = null;
      log.info(ME, "Socket driver stopped, all resources released.");
   }

   /**
    * Command line usage.
    * <p />
    * <ul>
    *  <li><i>-socket.port</i>        The SOCKET web server port [7607]</li>
    *  <li><i>-socket.hostname</i>    Specify a hostname where the SOCKET web server runs
    *                          Default is the localhost.</li>
    *  <li><i>-socket.backlog</i>     Queue size for incoming connection request [50]</li>
    *  <li><i>-dump[socket]</i>       true switches on detailed SOCKET debugging [false]</li>
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
      text += "   -socket.SoTimeout   How long may a socket read block in msec [0] (0 is forever).\n";
      text += "   -socket.responseTimeout Max wait for the method return value/exception [60000] msec.\n";
      text += "   -socket.backlog     Queue size for incoming connection request [50].\n";
      text += "   -socket.threadPrio  The priority 1=min - 10=max of the listener thread [5].\n";
      text += "   -dump[socket]       true switches on detailed SOCKET debugging [false].\n";
      text += "\n";
      return text;
   }
}
