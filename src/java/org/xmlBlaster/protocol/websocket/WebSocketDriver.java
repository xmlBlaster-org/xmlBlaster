/*------------------------------------------------------------------------------
Name:      SocketDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SocketDriver class to invoke the xmlBlaster server in the same JVM.
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.websocket;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.protocol.socket.SocketUrl;


/**
 * Socket driver class to invoke the xmlBlaster server over a native message format
 * <p />
 * This "WEBSOCKET:" driver needs to be activated in xmlBlasterPlugins.xml
 * and will be started on xmlBlaster startup, for example using port 3414 on localhost:
 * <pre>
   <plugin create='true' id='WEBSOCKET' className='org.xmlBlaster.protocol.websocket.WebSocketDriver'>
      <action do='LOAD' onStartupRunlevel='4' sequence='20'
              onFail='resource.configuration.pluginFailed'/>
      <action do='STOP' onShutdownRunlevel='3' sequence='50'/>
      <attribute id='port'>3414</attribute>
      <attribute id='hostname'>localhost</attribute>
   </plugin>
 * </pre>
 *
 * The variable plugin/websocket/port (default 3414) sets the socket server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -plugin/websocket/port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * <p />
 * All adjustable parameters are explained in {@link org.xmlBlaster.protocol.websocket.WebSocketDriver#usage()}
 * @author <a href="mailto:">Adrian Batzill</a>
 *
 * @see org.xmlBlaster.util.xbformat.MsgInfo
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.websocket.html">The protocol.websocket requirement</a>
 */
public class WebSocketDriver implements I_Driver /* which extends I_Plugin */, WebSocketDriverMBean
{
   private String ME = "WebSocketDriver";
   /** The global handle */
   private Global glob;
   private static Logger log = Logger.getLogger(WebSocketDriver.class.getName());

   private PluginInfo pluginInfo;

   /**
    * The socket address info object holding hostname (useful for multi homed
    * hosts) and port
    */
   private SocketUrl socketUrl;
   private AddressServer addressServer;
   /** The singleton handle for this authentication server */
   private I_Authenticate authenticate;
   /* The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl;
   /** My JMX registration */
   protected Object mbeanHandle;
   protected ContextNode contextNode;
   
   private boolean isShutdown;

   
   private XbWebSocketServer webSocketServer;
   

   /**
    * Creates the driver.
    * Note: getName() is enforced by interface I_Driver, but is already defined in Thread class
    */
   public WebSocketDriver() {
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver.
    * @return The configured [type] in xmlBlaster.properties, defaults to "WEBSOCKET"
    */
   @Override
   public String getProtocolId() {
      return "WEBSOCKET";
   }

   /**
    * Enforced by I_Plugin
    * @return The configured type in xmlBlaster.properties, defaults to "WEBSOCKET"
    */
   @Override
   public String getType() {
      return getProtocolId();
   }


   /** Enforced by I_Plugin */
   @Override
   public String getVersion() {
      return "1.0";
   }
   
   /**
    * The command line key prefix
    * 
    * @return The configured type in xmlBlasterPlugins.xml, defaults to
    *         "plugin/websocket"
    */
   public String getEnvPrefix() {
           return (addressServer != null) ? addressServer.getEnvPrefix()
                           : "plugin/" + getType().toLowerCase();
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin).
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.glob = glob;
      this.ME = "WebSocketDriver" + this.glob.getLogPrefixDashed() + "-" + getType();
      try {
         org.xmlBlaster.engine.ServerScope engineGlob = (org.xmlBlaster.engine.ServerScope) glob
                              .getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
         if (engineGlob == null)
            throw new XmlBlasterException(this.glob,
                                      ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
                                      "could not retreive the ServerNodeScope. Am I really on the server side ?");

         // For JMX instanceName may not contain ","
         String vers = ("1.0".equals(getVersion())) ? "" : getVersion();
         this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
                         "WebSocketDriver[" + getType() + vers + "]", glob
                                        .getContextNode());
         this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob,
                                ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
                                "authenticate object is null");
         }
         this.xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob,
                                ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
                                "xmlBlasterImpl object is null");
         }

         this.addressServer = new AddressServer(glob, getType(), glob
                        .getId(), pluginInfo.getParameters());

         this.socketUrl = new SocketUrl(glob, this.addressServer);

         if (this.socketUrl.getPort() < 1) {
            log.info(ME + "Option protocol/websocket/port set to "
                                + this.socketUrl.getPort()
                                + ", server not started");
            return;
         }

         activate();
      } catch (XmlBlasterException ex) {
         throw ex;
      } catch (Throwable ex) {
         throw new XmlBlasterException(this.glob,
                              ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
                              "init. Could'nt initialize the driver " + getProtocolId() + ".", ex);
      }
   }

   /**
    * Get the address how to access this driver.
    * @return "server.mars.univers:6701"
    */
   public String getRawAddress() {
      return this.webSocketServer.getAddress().toString();
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
   
   PluginInfo getPluginInfo() {
      return pluginInfo;
   }

   /*
    * Start xmlBlaster SOCKET access.
    * <p />
    * Enforced by interface I_Driver.<br />
    * This method returns as soon as the listener socket is alive and ready or on error.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "WebSocketDriver" + this.glob.getLogPrefixDashed() + "-" + getType();

      if (log.isLoggable(Level.FINER)) log.finer("Entering init()");
      this.addressServer = addressServer;
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;
      
      SocketUrl socketUrl = new SocketUrl(glob, this.addressServer);

      if (Constants.COMPRESS_ZLIB_STREAM.equals(this.addressServer.getCompressType())) {
         log.info("Full stream compression enabled with '" + Constants.COMPRESS_ZLIB_STREAM + "' for " + getType());
      }
      else if (Constants.COMPRESS_ZLIB.equals(this.addressServer.getCompressType())) {
         log.info("Message compression enabled with  '" + Constants.COMPRESS_ZLIB + "', minimum size for compression is " + this.addressServer.getMinSize() + " bytes for " + getType());
      }

      if (socketUrl.getPort() < 1) {
         log.info("Option protocol/websocket/port set to " + socketUrl.getPort() + ", socket server not started");
         return;
      }
   }
    */

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(toString() + " Entering activate");
      this.isShutdown = false;
      try {
         this.webSocketServer = new XbWebSocketServer(this, this.socketUrl);
         this.webSocketServer.start();
         log.info("Started successfully " + getType() + " driver on '" + this.socketUrl.getUrl() + "'");
       } catch (Exception e) {
           log.severe("Failed starting " + getType() + " driver on '" + this.socketUrl.getUrl() + "': " + e.getMessage());
           throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION, "activate", e.getMessage(), e);
       }
   }

   public boolean isActive() {
      return webSocketServer != null;
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect.
    */
   public synchronized void deActivate() throws RuntimeException {
      if (log.isLoggable(Level.FINER)) log.finer(toString() + " Entering deActivate");
      if (this.webSocketServer == null)
         return;
      
      for (HandleWebSocketClient client : this.webSocketServer.getConnectedClients()) {
         client.shutdown();
      }
      
      try {
         log.info(toString() + " Stopping " + getType() + " driver on '" + this.socketUrl.getUrl() + "'");
         this.webSocketServer.stop();
         this.webSocketServer = null;
       } catch (Throwable ex) {
          ex.printStackTrace();
          //throw new XmlBlasterException(this.glob,
          //              ErrorCode.INTERNAL_UNKNOWN, ME + ".init",
          //              "init. Could'nt shutdown the driver.", ex);
       }
   }


   final Global getGlobal() {
      return this.glob;
   }

   /**
    * Close the listener port, the driver shuts down.
    */
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer(toString() + " Entering shutdown");

      try {
         deActivate();
      } catch (Exception e) {
         log.severe(e.toString());
      }

      this.glob.unregisterMBean(this.mbeanHandle);

      this.isShutdown = true;

      log.info(toString() + " Driver stopped, all resources released.");
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
    *  <li><i>-plugin/websocket/port</i>        The WebSocket server port [3414]</li>
    *  <li><i>-plugin/websocket/hostname</i>    Specify a hostname where the WebSocket server runs
    *                                          Default is the localhost.</li>
    * </ul>
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "SocketDriver options:\n";
      text += "   -"+getEnvPrefix()+"port\n";
      text += "                       The WebSocket server port [3414].\n";
      text += "   -"+getEnvPrefix()+"hostname\n";
      text += "                       Specify a hostname where the WebSocket server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   " + Global.getJmxUsageLinkInfo(this.getClass().getName(), null);
      text += "\n";
      return text;
   }

   @Override
   public String getName() {
      return "XmlBlaster.WebSocketDriver";
   }
   
   public String toString() {
	   return getProtocolId() + "-" + getRawAddress();
   }
}
