/*------------------------------------------------------------------------------
Name:      XbMqttDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   MqttDriver class to invoke the xmlBlaster with an MQTT client.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.mqtt;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketUrl;

public class XbMqttDriver extends Thread implements I_Driver /* which extends I_Plugin */, XbMqttDriverMBean {
   private String ME = "MqttDriver";
   /** The global handle */
   private Global glob;
   private static Logger log = Logger.getLogger(XbMqttDriver.class.getName());
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   /**
    * The socket address info object holding hostname (useful for multi homed
    * hosts) and port
    */
   private SocketUrl socketUrl;
   /** The socket server */
   private ServerSocket listen = null;

   private int sslMarker;

   private boolean running = true;
   private boolean listenerReady = false;
   /** Remember all client connections */
   private Set<HandleMqttClient> handleClientSet = new HashSet<>();

   private Map<String, HandleMqttClient>/* <secretSessionId, HandleClient> */ handleClientMap = new HashMap<>();

   /** The address configuration */
   private AddressServer addressServer;

   private PluginInfo pluginInfo;

   /** My JMX registration */
   protected Object mbeanHandle;
   protected ContextNode contextNode;

   public static int DEFAULT_SERVER_PORT = 1883;
   public static int DEFAULT_SERVER_PORT_SSL = 1884;

   protected boolean isShutdown;

   void addClient(String sessionId, HandleMqttClient h) {
      synchronized (handleClientMap) {
         handleClientMap.put(sessionId, h);
      }
   }

   HandleMqttClient getClient(String sessionId) {
      synchronized (handleClientMap) {
         return (HandleMqttClient) handleClientMap.get(sessionId);
      }
   }

   public I_PluginConfig getPluginConfig() {
      return this.pluginInfo;
   }

   /**
    * Creates the driver. Note: getName() is enforced by interface I_Driver, but is
    * already defined in Thread class
    */
   public XbMqttDriver() {
      super("XmlBlaster.MqttDriver");
      setDaemon(true);
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * 
    * @return The configured [type] in xmlBlaster.properties, defaults to "MQTT"
    */
   public String getProtocolId() {
      return (this.pluginInfo == null) ? "MQTT" : this.pluginInfo.getType();
   }

   /**
    * Enforced by I_Plugin
    * 
    * @return The configured type in xmlBlaster.properties, defaults to "SOCKET"
    */
   public String getType() {
      return getProtocolId();
   }

   /**
    * The command line key prefix
    * 
    * @return The configured type in xmlBlasterPlugins.xml, defaults to
    *         "plugin/socket"
    */
   public String getEnvPrefix() {
      return (addressServer != null) ? addressServer.getEnvPrefix() : "plugin/" + getType().toLowerCase();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return (this.pluginInfo == null) ? "1.0" : this.pluginInfo.getVersion();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.pluginInfo = pluginInfo;
      this.glob = glob;
      this.ME = getType();
      org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope) glob.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (engineGlob == null)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "could not retreive the ServerNodeScope. Am I really on the server side ?");

      // For JMX instanceName may not contain ","
      String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
      this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG, "MqttDriver[" + getType() + vers + "]", glob.getContextNode());
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

         // Now we have logging ...
         if (log.isLoggable(Level.FINE))
            log.fine("Using pluginInfo=" + this.pluginInfo.toString());

         activate();
      } catch (XmlBlasterException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "init. Couldn't initialize the driver.", ex);
      }
   }

   /**
    * Get the address how to access this driver.
    * 
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
    * Start xmlBlaster MQTT access.
    * <p />
    * Enforced by interface I_Driver.<br />
    * This method returns as soon as the listener socket is alive and ready or on
    * error.
    * 
    * @param glob           Global handle to access logging, property and
    *                       commandline args
    * @param authenticate   Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl) throws XmlBlasterException {
      this.glob = glob;
      this.ME = "MqttDriver" + this.glob.getLogPrefixDashed() + "-" + getType();

      if (log.isLoggable(Level.FINER))
         log.finer("Entering init()");
      this.addressServer = addressServer;
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      this.socketUrl = new SocketUrl(glob, this.addressServer);
      // override default..
      if (!this.socketUrl.isEnforced()) {
         if (this.isSSL())
            this.socketUrl.setPort(DEFAULT_SERVER_PORT_SSL);
         else
            this.socketUrl.setPort(DEFAULT_SERVER_PORT);

      }

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressServer.getCompressType())) {
         log.info("Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "' for " + getType());
      } else if (Constants.COMPRESS_ZLIB.equals(this.addressServer.getCompressType())) {
         log.info("Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + this.addressServer.getMinSize() + " bytes for " + getType());
      }

      if (this.socketUrl.getPort() < 1) {
         log.info("Option protocol/socket/port set to " + this.socketUrl.getPort() + ", socket server not started");
         return;
      }
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering activate");

      super.setName("XmlBlaster.MqttDriver-" + getType()); // Thread name
      start(); // Start the listen thread
      while (!listenerReady) {
         try {
            Thread.sleep(10);
         } catch (InterruptedException i) {
         }
      }
   }

   public boolean isActive() {
      return running == true;
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public synchronized void deActivate() throws RuntimeException {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering deActivate");

      boolean closeHack = true;
      if (listen != null && closeHack) {
         // On some JDKs, listen.close() is not immediate (has a delay for about 1 sec.)
         // force closing by invoking server with this temporary client:
         try {
            java.net.Socket socket = new Socket(listen.getInetAddress(), this.socketUrl.getPort());
            socket.close();
         } catch (java.io.IOException e) {
            log.warning("Tcp shutdown problem: " + e.toString());
         }
      }
      if (listen != null) {
         try {
            listen.close();
         } catch (java.io.IOException e) {
            log.warning("TCP socket shutdown problem: " + e.toString());
         }
         listen = null;
         // log.info(ME, "TCP socket driver stopped, all resources released.");
      }

      // shutdown all clients connected
      while (true) {
         HandleMqttClient h = null;
         synchronized (handleClientSet) {
            Iterator it = handleClientSet.iterator();
            if (it.hasNext()) {
               h = (HandleMqttClient) it.next();
               it.remove();
            } else
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

   final void removeClient(HandleMqttClient h) {
      synchronized (handleClientSet) {
         boolean removed = handleClientSet.remove(h);
         if (!removed) { // May be called twice: from SessionInfo.shutdown() and from
                         // HandleClient->exiting run()
            if (log.isLoggable(Level.FINE))
               log.fine("Didn't find a client object to remove: " + h.toString());
         }
      }
      synchronized (handleClientMap) {
         Object removed = handleClientMap.remove(h.getSecretSessionId());
         if (removed == null) {
            if (log.isLoggable(Level.FINE))
               log.fine("Didn't find a client handle to remove: " + h.toString());
         }
      }
   }

   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Is SSL support switched on?
    */
   public final boolean isSSL() {
      if (sslMarker != 0)
         return sslMarker == -1 ? false : true;

      boolean ssl = this.addressServer.getEnv("SSL", false).getValue();
      sslMarker = ssl ? 1 : -1;
      if (log.isLoggable(Level.FINE))
         log.fine(addressServer.getEnvLookupKey("SSL") + "=" + ssl);
      return ssl;
   }

   /**
    * Starts the server socket and waits for clients to connect.
    */
   public void run() {
      try {
         int backlog = this.addressServer.getEnv("backlog", 50).getValue(); // queue for max 50 incoming connection request
         if (log.isLoggable(Level.FINE))
            log.fine(addressServer.getEnvLookupKey("backlog") + "=" + backlog);

         if (isSSL()) {
            listen = this.socketUrl.createServerSocketSSL(backlog, this.addressServer);
         } else {
            listen = new ServerSocket(this.socketUrl.getPort(), backlog, this.socketUrl.getInetAddress());
         }

         log.info("Started successfully " + getType() + " driver on '" + this.socketUrl.getUrl() + "'");
         listenerReady = true;
         while (running) {
            Socket accept = listen.accept();

            if (log.isLoggable(Level.INFO))
               log.info(ME + ": New incoming request on " + this.socketUrl.getUrl() + " from " + accept.getInetAddress() + ":" + accept.getPort());
            if (!running) {
               log.info("Closing server '" + this.socketUrl.getUrl() + "'");
               break;
            }
            HandleMqttClient hh = new HandleMqttClient((ServerScope) glob, this, accept);
            synchronized (handleClientSet) {
               handleClientSet.add(hh);
            }
            hh.startThread();
         }
      } catch (java.net.UnknownHostException e) {
         log.severe("Socket server problem, IP address '" + this.socketUrl.getHostname() + "' is invalid: " + e.toString());
      } catch (java.net.BindException e) {
         log.severe("Socket server problem '" + this.socketUrl.getUrl() + "', the port " + this.socketUrl.getPort() + " is not available: " + e.toString());
      } catch (java.net.SocketException e) {
         log.info("Socket '" + this.socketUrl.getUrl() + "' closed successfully: " + e.toString());
      } catch (IOException e) {
         log.severe("Socket server problem on '" + this.socketUrl.getUrl() + "': " + e.toString());
      } catch (Throwable e) {
         log.severe("Socket server problem on '" + this.socketUrl.getUrl() + "': " + e.toString());
         e.printStackTrace();
      } finally {
         listenerReady = false;
         if (listen != null) {
            try {
               listen.close();
            } catch (java.io.IOException e) {
               log.warning("listen.close()" + e.toString());
            }
            listen = null;
         }
      }
   }

   /**
    * Close the listener port, the driver shuts down.
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER))
         log.finer("Entering shutdown");

      try {
         deActivate();
      } catch (Exception e) {
         log.severe(e.toString());
      }

      this.glob.unregisterMBean(this.mbeanHandle);

      this.isShutdown = true;

      log.info("Socket driver '" + getType() + "' stopped, all resources released.");
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
    * <li><i>-plugin/socket/port</i> The SOCKET web server port [7607]</li>
    * <li><i>-plugin/socket/hostname</i> Specify a hostname where the SOCKET web
    * server runs Default is the localhost.</li>
    * <li><i>-plugin/socket/backlog</i> Queue size for incoming connection request
    * [50]</li>
    * <li><i>-dump[socket]</i> true switches on detailed SOCKET debugging
    * [false]</li>
    * </ul>
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage() {
      String text = "\n";
      text += "MqttDriver options:\n";
      text += "   -" + getEnvPrefix() + "port\n";
      text += "                       The MQTT server port [1883].\n";
      text += "   -" + getEnvPrefix() + "hostname\n";
      text += "                       Specify a hostname where the MQTT server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -" + getEnvPrefix() + "SoTimeout\n";
      text += "                       How long may a socket read block in msec [0] (0 is forever).\n";
      text += "   -" + getEnvPrefix() + "responseTimeout\n";
      text += "                       Max wait for the method return value/exception in msec.\n";
//      text += "                       The default is " +getDefaultResponseTimeout() + ".\n";
      text += "                       Defaults to 'forever', the value to pass is milli seconds.\n";
      text += "   -" + getEnvPrefix() + "backlog\n";
      text += "                       Queue size for incoming connection request [50].\n";
      text += "   -" + getEnvPrefix() + "threadPrio\n";
      text += "                       The priority 1=min - 10=max of the listener thread [5].\n";
      text += "   -" + getEnvPrefix() + "SSL\n";
      text += "                       True enables SSL support on server socket [false].\n";
      text += "   -" + getEnvPrefix() + "keyStore\n";
      text += "                       The path of your keystore file. Use the java utility keytool.\n";
      text += "   -" + getEnvPrefix() + "keyStorePassword\n";
      text += "                       The password of your keystore file.\n";
      text += "   -dump[socket]       true switches on detailed " + getType() + " debugging [false].\n";
      text += "   " + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
      text += "\n";
      return text;
   }
}
