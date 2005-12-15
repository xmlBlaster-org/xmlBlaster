/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.socket;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.xbformat.MsgInfo;

import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.Set;
import java.util.HashSet;

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
 * @author <a href="mailto:bpoka@axelero.hu">Balázs Póka</a> (SSL embedding, zlib compression)
 *
 * @see org.xmlBlaster.util.xbformat.MsgInfo
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.socket.html">The protocol.socket requirement</a>
 */
public class SocketDriver extends Thread implements I_Driver /* which extends I_Plugin */, SocketDriverMBean
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
   /** State of server */
   private Thread listenerUDP;

   private int sslMarker;

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

   private boolean startUdpListener = false;

   /**
    * Setting by plugin configuration, see xmlBlasterPlugins.xml, for example
    * <br />
    *  &lt;attribute id='useUdpForOneway'>true&lt;/attribute>
    */
   private boolean useUdpForOneway = false;

   /** My JMX registration */
   protected Object mbeanHandle;
   protected ContextNode contextNode;
   
   protected boolean isShutdown;
   
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

   /**
    * There is exactly one UDP listener thread which receives datagrams for all clients.
    * The datagrams are forwarded to the correct client with the sessionId
    * Only the methods publishOneway() and updateOneway() may use UDP, you can
    * choose TCP or UDP for those messages with the
    * <tt>plugin/socket/useUdpForOneway</tt> setting
    */
   class UDPListener
       implements Runnable {
      static final int MAX_PACKET_SIZE = 1024 * 10;
      public void run() {
         try {
            try {
               socketUDP = new DatagramSocket(socketUrl.getPort(), socketUrl.getInetAddress());
            }
            catch (java.net.SocketException e) {
               log.error(ME, "Cannot open UDP socket '" + socketUrl.getUrl() + "' : " + e.toString());
               return;
            }

            int threadPrio = getAddressServer().getEnv("threadPrio", Thread.NORM_PRIORITY).getValue();
            try {
               Thread.currentThread().setPriority(threadPrio);
               if (log.TRACE)
                  log.trace(ME, "-" + getEnvPrefix() + "threadPrio " + threadPrio);
            }
            catch (IllegalArgumentException e) {
               log.warn(ME,
                        "Your -" + getEnvPrefix() + "threadPrio " + threadPrio + " is out of range, we continue with default setting " + Thread.NORM_PRIORITY);
            }

            log.info(ME, "Started successfully " + getType() + " UDP driver on '" + socketUrl.getUrl() + "'");

            byte packetBuffer[] = new byte[MAX_PACKET_SIZE];
            DatagramPacket packet = new DatagramPacket(packetBuffer, packetBuffer.length);
            MsgInfo receiver = null;
            listenerReadyUDP = true;
            while (runningUDP) {
               try {
                  socketUDP.receive(packet);
               }
               catch (IOException e) {
                  if (e.toString().indexOf("closed") == -1) {
                     log.error(ME, "Error receiving packet from '" + socketUrl.getUrl() + "' : " + e.toString());
                  }
                  else {
                     if (log.TRACE) log.trace(ME, "UDP datagram socket shutdown '" + socketUrl.getUrl() + "' : " + e.toString());
                  }
                  return;
               }
               if (log.TRACE)
                  log.trace(ME, "UDP packet arrived, size=" + packet.getLength() + " bytes");
               if (!runningUDP) {
                  log.info(ME, "Closing server '" + socketUrl.getUrl() + "'");
                  return;
               }
               int actualSize = packet.getLength();
               if (packet.getLength() > MAX_PACKET_SIZE) {
                  log.warn(ME, "Packet has been truncated, size=" + packet.getLength() + ", MAX_PACKET_SIZE=" + MAX_PACKET_SIZE);
                  actualSize = MAX_PACKET_SIZE;
               }
               InputStream iStream = new ByteArrayInputStream(packet.getData(), 0, actualSize);
               try {
                  receiver = MsgInfo.parse(glob, null, iStream, null/*getMsgInfoParserClassName()*/)[0];
               }
               catch (Throwable e) {
                  log.error(ME, "Error parsing data from UDP packet: " + e);
                  continue;
               }
               String sessionId = receiver.getSecretSessionId();
               HandleClient hh = getClient(sessionId);
               if (hh == null)
                  log.error(ME, "Request from unknown client, sessionId: " + sessionId);
               else
                  hh.handleMessage(receiver, true);
            } // while (runningUDP) {
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
    * Switch on/off UDP socket listener
    */
   public boolean startUdpListener() {
      return this.startUdpListener;
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
      this.glob = glob;
      org.xmlBlaster.engine.Global engineGlob = (org.xmlBlaster.engine.Global)glob.getObjectEntry("ServerNodeScope");
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      // For JMX instanceName may not contain ","
      String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
            "SocketDriver[" + getType() + vers + "]", glob.getContextNode());
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

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
         this.startUdpListener = this.addressServer.getEnv("startUdpListener", this.startUdpListener).getValue();

         // Now we have logging ...
         if (log.TRACE) log.trace(ME, "Using pluginInfo=" + this.pluginInfo.toString() + ", startUdpListener=" + this.startUdpListener + ", useUdpForOneway=" + this.useUdpForOneway);

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

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressServer.getCompressType())) {
         log.info(ME, "Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "' for " + getType());
      }
      else if (Constants.COMPRESS_ZLIB.equals(this.addressServer.getCompressType())) {
         log.info(ME, "Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + this.addressServer.getMinSize() + " bytes for " + getType());
      }

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

      if (startUdpListener()) {
         listenerUDP = new Thread(new UDPListener());
         listenerUDP.setName("XmlBlaster."+getType()+".udpListener");
         listenerUDP.start();
         while (!listenerReadyUDP) {
            try { Thread.sleep(10); } catch( InterruptedException i) {}
         }
      }

      start(); // Start the listen thread
      while (!listenerReady) {
         try { Thread.sleep(10); } catch( InterruptedException i) {}
      }
   }
   
   public boolean isActive() {
      return running == true;
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public synchronized void deActivate() throws RuntimeException {
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
            log.warn(ME, "Tcp shutdown problem: " + e.toString());
         }
      }
      /*
      if (socketUDP != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.DatagramSocket socket = new DatagramSocket(this.socketUrl.getPort(), socketUDP.getLocalAddress());
            socket.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "Udp shutdown problem: " + e.toString());
         }
      }
      */
      if (listen != null) {
         try {
            listen.close();
         } catch (java.io.IOException e) {
            log.warn(ME, "TCP socket shutdown problem: " + e.toString());
         }
         listen = null;
         //log.info(ME, "TCP socket driver stopped, all resources released.");
      }
      if (socketUDP != null) {
         socketUDP.close();
         socketUDP = null;
         //log.info(ME, "UDP socket driver stopped, all resources released.");
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
      synchronized(handleClientMap) {
         handleClientMap.remove(h.getSecretSessionId());
      }
   }

   final Global getGlobal()
   {
      return this.glob;
   }

   /**
    * Is SSL support switched on?
    */
   public final boolean isSSL()
   {
      if (sslMarker != 0) return sslMarker==-1 ? false : true;

      boolean ssl = this.addressServer.getEnv("SSL", false).getValue();
      sslMarker = ssl ? 1 : -1;
      if (log.TRACE) log.trace(ME, addressServer.getEnvLookupKey("SSL") + "=" + ssl);
      return ssl;
   }

   /**
    * Starts the server socket and waits for clients to connect.
    */
   public void run()
   {
      try {
         int backlog = this.addressServer.getEnv("backlog", 50).getValue(); // queue for max 50 incoming connection request
         if (log.TRACE) log.trace(ME, addressServer.getEnvLookupKey("backlog") + "=" + backlog);
         
         
         if (isSSL()) {
             listen = this.socketUrl.createServerSocketSSL(backlog, this.addressServer);
         }
         else {
             listen = new ServerSocket(this.socketUrl.getPort(), backlog, this.socketUrl.getInetAddress());
         }
         
         log.info(ME, "Started successfully " + getType() + " driver on '" + this.socketUrl.getUrl() + "'");
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
      } catch (Exception e) {
         log.error(ME, e.toString());
      }

      this.glob.unregisterMBean(this.mbeanHandle);
      
      this.isShutdown = true;

      log.info(ME, "Socket driver '" + getType() + "' stopped, all resources released.");
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   /**
    * @return A link for JMX usage
    */
   public java.lang.String getUsageUrl() {
      return Global.getJavadocUrl(this.getClass().getName(), null);
   }

   /* dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {
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
      text += "   -"+getEnvPrefix()+"startUdpListener\n";
      text += "                       Start a UDP datagram listener socket [false].\n";
      text += "   -"+getEnvPrefix()+"useUdpForOneway\n";
      text += "                       Use UDP instead of TCP for updateOneway() calls [false].\n";
      text += "   -"+getEnvPrefix()+"SoTimeout\n";
      text += "                       How long may a socket read block in msec [0] (0 is forever).\n";
      text += "   -"+getEnvPrefix()+"responseTimeout\n";
      text += "                       Max wait for the method return value/exception in msec.\n";
//      text += "                       The default is " +getDefaultResponseTimeout() + ".\n";
      text += "                       Defaults to 'forever', the value to pass is milli seconds.\n";
      text += "   -"+getEnvPrefix()+"backlog\n";
      text += "                       Queue size for incoming connection request [50].\n";
      text += "   -"+getEnvPrefix()+"threadPrio\n";
      text += "                       The priority 1=min - 10=max of the listener thread [5].\n";
      text += "   -"+getEnvPrefix()+"SSL\n";
      text += "                       True enables SSL support on server socket [false].\n";
      text += "   -"+getEnvPrefix()+"keyStore\n";
      text += "                       The path of your keystore file. Use the java utility keytool.\n";
      text += "   -"+getEnvPrefix()+"keyStorePassword\n";
      text += "                       The password of your keystore file.\n";
      text += "   -"+getEnvPrefix()+"compress/type\n";
      text += "                       Valid values are: '', '"+Constants.COMPRESS_ZLIB_STREAM+"', '"+Constants.COMPRESS_ZLIB+"' [].\n";
      text += "                       '' disables compression, '"+Constants.COMPRESS_ZLIB_STREAM+"' compresses whole stream.\n";
      text += "                       '"+Constants.COMPRESS_ZLIB+"' only compresses flushed chunks bigger than 'compress/minSize' bytes.\n";
      text += "   -"+getEnvPrefix()+"compress/minSize\n";
      text += "                       Compress message bigger than given bytes, see above.\n";
      text += "   -dump[socket]       true switches on detailed "+getType()+" debugging [false].\n";
      text += "   " + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
      text += "\n";
      return text;
   }
}
