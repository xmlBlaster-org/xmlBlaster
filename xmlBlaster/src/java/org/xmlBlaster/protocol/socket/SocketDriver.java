/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SocketDriver.java,v 1.39 2004/08/22 22:38:33 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;


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
 * The variable plugin/socket/port (default 7607) sets the socket server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -plugin/socket/port 9090
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
public class SocketDriver extends Thread implements I_Driver /* which extends I_Plugin */
{
   private String ME = "SocketDriver";
   /** The global handle */
   private Global glob;
   private LogChannel log;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   /** The socket address info object holding hostname (useful for multi homed hosts) and port */
   private SocketUrl socketUrl;
   /** The socket server */
   private ServerSocket listen = null;
   /** The URL which clients need to use to access this server, e.g. "server.mars.univers:6701" */
   private DatagramSocket socketUDP = null;
   private String serverUrl = null;
   /** State of server */
   private Thread listenerUDP;

   private boolean running = true;
   private boolean runningUDP = true;
   private boolean listenerReady = false;
   private boolean listenerReadyUDP = false;
   /** Remember all client connections */
   private Set handleClientSet = new HashSet();

   private Map handleClientMap = new HashMap();

   /** The address configuration */
   private AddressServer addressServer;

   private PluginInfo pluginInfo;
   /**
    * Setting by plugin configuration, see xmlBlasterPlugins.xml, for example
    * <br />
    *  &lt;attribute id='useUdpForOneway'>true&lt;/attribute>
    */
   private boolean useUdpForOneway = false;

   void addClient(String sessionId, HandleClient h) {
      synchronized(handleClientMap) {
         handleClientMap.put(sessionId, h);
      }
   }

   HandleClient getClient(String sessionId) {
      synchronized(handleClientMap) {
         return (HandleClient) handleClientMap.get(sessionId);
      }
   }

   void removeClient(String sessionId) {
      synchronized(handleClientMap) {
         handleClientMap.remove(sessionId);
      }
   }

   /**
    * There is exactly one UDP listener thread which receives datagrams for all clients. 
    * The datagrams are forwarded to the correct client with the sessionId
    * Only the methods publishOneway() and updateOneway() may use UDP, you can
    * choose TCP or UDP for those messages with the "

    */
   class UDPListener implements Runnable {
      static final int MAX_PACKET_SIZE = 1024*10;
      public void run() {
         try {
//         int backlog = this.addressServer.getEnv("backlog", 50).getValue(); // queue for max 50 incoming connection request
//         if (log.TRACE) log.trace(ME, addressServer.getEnvLookupKey("backlog") + "=" + backlog);

            socketUDP = new DatagramSocket(socketUrl.getPort(), socketUrl.getInetAddress());
            /*
            setSoTimeout(addressConfig.getEnv("SoTimeout", 0L).getValue()); // switch off
            socketUDP.setSoTimeout((int)this.soTimeout);
            if (log.TRACE) log.trace(ME, this.addressConfig.getEnvLookupKey("SoTimeout") + "=" + this.soTimeout);
            */

            int threadPrio = getAddressServer().getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
            try {
               Thread.currentThread().setPriority(threadPrio);
               if (log.TRACE) log.trace(ME, "-"+getEnvPrefix()+"threadPrio "+threadPrio);
            }
            catch (IllegalArgumentException e) {
               log.warn(ME, "Your -"+getEnvPrefix()+"threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
            }

            log.info(ME, "Started successfully " + getType() + " driver on '" + socketUrl.getUrl() + "'");

            byte packetBuffer[] = new byte[MAX_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
            Parser receiver = new Parser(glob);
            listenerReadyUDP = true;
            while (runningUDP) {
               socketUDP.receive(packet);
               //log.trace(ME, "New incoming request on port=" + this.socketUrl.getPort() + " ...");

               if (log.TRACE) log.trace(ME, "UDP packet arrived, size=" + packet.getLength() + " bytes");
               if (!runningUDP) {
                  log.info(ME, "Closing server '" + socketUrl.getUrl() + "'");
                  break;
               }
               int actualSize = packet.getLength();
               if (packet.getLength() > MAX_PACKET_SIZE) {
                  log.warn(ME, "Packet has been truncated, size=" + packet.getLength() + ", MAX_PACKET_SIZE=" + MAX_PACKET_SIZE);
                  actualSize = MAX_PACKET_SIZE;
               }
               InputStream iStream = new ByteArrayInputStream(packet.getData(), 0, actualSize);
               HandleClient hh = null;
               try {
                  receiver.parse(iStream);
                  if (log.TRACE) log.trace(ME, "Receiving message " + receiver.getMethodName() + "(" + receiver.getRequestId() + ")");
                  if (log.DUMP) log.dump(ME, "Receiving message >" + Parser.toLiteral(receiver.createRawMsg()) + "<");

                  String sessionId = receiver.getSecretSessionId();
                  hh = getClient(sessionId);
                  if (hh == null)
                     log.warn(ME, "Request from unknown client");
                  if (hh.receive(receiver, true) == false)
                     log.warn(ME, "Connect and Disconnect via UDP is not implemented.");
               }
               catch (XmlBlasterException e) {
                  if (log.TRACE) log.trace(ME, "Can't handle message, throwing exception back to client: " + e.toString());
                  try {
                     hh.executeException(receiver, e, true);
                  }
                  catch (Throwable e2) {
                     log.error(ME, "Lost connection, can't deliver exception message: " + e.toString() + " Reason is: " + e2.toString());
                     hh.shutdown();
                  }
               }
               catch (IOException e) {
                  if (runningUDP != false) { // Only if not triggered by our shutdown:sock.close()
                     if (log.TRACE) log.trace(ME, "Lost connection to client: " + e.toString());
                     hh.shutdown();
                  }
               }
               catch (Throwable e) {
                  e.printStackTrace();
                  log.error(ME, "Lost connection to client: " + e.toString());
                  hh.shutdown();
               }
            }
         }
         catch (java.net.UnknownHostException e) {
            log.error(ME, "Socket server problem, IP address '" + socketUrl.getHostname() + "' is invalid: " + e.toString());
         }
         catch (java.net.BindException e) {
            log.error(ME, "Socket server problem '" + socketUrl.getUrl() + "', the port " + socketUrl.getPort() + " is not available: " + e.toString());
         }
         catch (java.net.SocketException e) {
            log.info(ME, "Socket '" + socketUrl.getUrl() + "' closed successfully: " + e.toString());
         }
         catch (IOException e) {
            log.error(ME, "Socket server problem on '" + socketUrl.getUrl() + "': " + e.toString());
         }
         catch (Throwable e) {
            log.error(ME, "Socket server problem on '" + socketUrl.getUrl() + "': " + e.toString());
            e.printStackTrace();
         }
         finally {
            listenerReadyUDP = false;
            if (socketUDP != null) {
               socketUDP.close();
               socketUDP = null;
            }
         }
      }

   }

   void sendMessage(byte[] msg) throws IOException {

   }

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
    * @return The configured [type] in xmlBlaster.properties, defaults to "SOCKET"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "SOCKET" : this.pluginInfo.getType();
   }

   /**
    * Enforced by I_Plugin
    * @return The configured type in xmlBlaster.properties, defaults to "SOCKET"
    */
   public String getType() {
      return getProtocolId();
   }

   /**
    * The command line key prefix
    * @return The configured type in xmlBlasterPlugins.xml, defaults to "plugin/socket"
    */
   public String getEnvPrefix() {
      return (addressServer != null) ? addressServer.getEnvPrefix() : "plugin/"+getType().toLowerCase();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * Configuration option to use UDP for updateOneway() calls. 
    * <br />
    * Typically a setting from the plugin configuration, see xmlBlasterPlugins.xml, for example
    * <br />
    *  &lt;attribute id='useUdpForOneway'>true&lt;/attribute>
    */
   public boolean useUdpForOneway() {
      return this.useUdpForOneway;
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      try {
         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }

         init(glob, new AddressServer(glob, getType(), glob.getId(), pluginInfo.getParameters()), this.authenticate, xmlBlasterImpl);

         this.useUdpForOneway = this.addressServer.getEnv("useUdpForOneway", this.useUdpForOneway).getValue();

         // Now we have logging ...
         if (log.TRACE) log.trace(ME, "Using pluginInfo=" + this.pluginInfo.toString() + ", useUdpForOneway=" + this.useUdpForOneway);

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
      return this.socketUrl.getUrl(); // this.socketUrl.getHostname() + ":" + this.socketUrl.getPort();
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

   AddressServer getAddressServer() {
      return this.addressServer;
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
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "SocketDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("socket");
      if (log.CALL) log.call(ME, "Entering init()");
      this.addressServer = addressServer;
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      this.socketUrl = new SocketUrl(glob, this.addressServer);

      if (this.socketUrl.getPort() < 1) {
         log.info(ME, "Option protocl/socket/port set to " + this.socketUrl.getPort() + ", socket server not started");
         return;
      }
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");

      listenerUDP = new Thread(new UDPListener());
      listenerUDP.setName("XmlBlaster."+getType()+".udpListener");
      listenerUDP.start();
      while (!listenerReadyUDP) {
         try { Thread.sleep(10); } catch( InterruptedException i) {}
      }

      start(); // Start the listen thread
      while (!listenerReady) {
         try { Thread.sleep(10); } catch( InterruptedException i) {}
      }
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      running = false; runningUDP = false;

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), this.socketUrl.getPort());
            socket.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "shutdown problem: " + e.toString());
         }
      }

      if (socketUDP != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.DatagramSocket socket = new DatagramSocket(this.socketUrl.getPort(), socketUDP.getLocalAddress());
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
         if (socketUDP != null) {
            socketUDP.close();
            socketUDP = null;
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
      synchronized (handleClientMap) {
        handleClientMap.clear();
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
         int backlog = this.addressServer.getEnv("backlog", 50).getValue(); // queue for max 50 incoming connection request
         if (log.TRACE) log.trace(ME, addressServer.getEnvLookupKey("backlog") + "=" + backlog);

         listen = new ServerSocket(this.socketUrl.getPort(), backlog, this.socketUrl.getInetAddress());
         log.info(ME, "Started successfully socket driver on '" + this.socketUrl.getUrl() + "'");
         listenerReady = true;
         while (running) {
            Socket accept = listen.accept();
            //log.trace(ME, "New incoming request on port=" + this.socketUrl.getPort() + " ...");
            if (!running) {
               log.info(ME, "Closing server '" + this.socketUrl.getUrl() + "'");
               break;
            }
            HandleClient hh = new HandleClient(glob, this, accept, socketUDP);
            synchronized (handleClientSet) {
               handleClientSet.add(hh);
            }
         }
      }
      catch (java.net.UnknownHostException e) {
         log.error(ME, "Socket server problem, IP address '" + this.socketUrl.getHostname() + "' is invalid: " + e.toString());
      }
      catch (java.net.BindException e) {
         log.error(ME, "Socket server problem '" + this.socketUrl.getUrl() + "', the port " + this.socketUrl.getPort() + " is not available: " + e.toString());
      }
      catch (java.net.SocketException e) {
         log.info(ME, "Socket '" + this.socketUrl.getUrl() + "' closed successfully: " + e.toString());
      }
      catch (IOException e) {
         log.error(ME, "Socket server problem on '" + this.socketUrl.getUrl() + "': " + e.toString());
      }
      catch (Throwable e) {
         log.error(ME, "Socket server problem on '" + this.socketUrl.getUrl() + "': " + e.toString());
         e.printStackTrace();
      }
      finally {
         listenerReady = false;
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

      log.info(ME, "Socket driver stopped, all resources released.");
   }

   /**
    * Command line usage.
    * <p />
    * <ul>
    *  <li><i>-plugin/socket/port</i>        The SOCKET web server port [7607]</li>
    *  <li><i>-plugin/socket/hostname</i>    Specify a hostname where the SOCKET web server runs
    *                                          Default is the localhost.</li>
    *  <li><i>-plugin/socket/backlog</i>     Queue size for incoming connection request [50]</li>
    *  <li><i>-dump[socket]</i>       true switches on detailed SOCKET debugging [false]</li>
    * </ul>
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "SocketDriver options:\n";
      text += "   -"+getEnvPrefix()+"port\n";
      text += "                       The SOCKET server port [7607].\n";
      text += "   -"+getEnvPrefix()+"hostname\n";
      text += "                       Specify a hostname where the SOCKET server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -"+getEnvPrefix()+"useUdpForOneway\n";
      text += "                       Use UDP instead of TCP for updateOneway() calls [false].\n";
      text += "   -"+getEnvPrefix()+"SoTimeout\n";
      text += "                       How long may a socket read block in msec [0] (0 is forever).\n";
      text += "   -"+getEnvPrefix()+"responseTimeout\n";
      text += "                       Max wait for the method return value/exception [60000] msec.\n";
      text += "   -"+getEnvPrefix()+"backlog\n";
      text += "                       Queue size for incoming connection request [50].\n";
      text += "   -"+getEnvPrefix()+"threadPrio\n";
      text += "                       The priority 1=min - 10=max of the listener thread [5].\n";
      text += "   -dump[socket]       true switches on detailed "+getType()+" debugging [false].\n";
      text += "\n";
      return text;
   }
}
