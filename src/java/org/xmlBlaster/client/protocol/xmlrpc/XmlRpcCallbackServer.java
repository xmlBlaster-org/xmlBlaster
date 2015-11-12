/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcHttpServer;
import org.apache.xmlrpc.server.XmlRpcServerConfigImpl;
import org.apache.xmlrpc.webserver.WebServer;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.property.PropBoolean;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.protocol.xmlrpc.XblRequestFactoryFactory;
import org.xmlBlaster.util.protocol.xmlrpc.XblWriterFactory;
import org.xmlBlaster.protocol.xmlrpc.XblWebServer;
import org.xmlBlaster.protocol.xmlrpc.XmlRpcUrl;

/**
 * Example for a XmlRpc callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * The xmlBlaster callback client call the update() from XmlRpcCallbackImpl
 * which delegates it to this update() method.
 * <p />
 * <pre>
 *     -dispatch/callback/plugin/xmlrpc/port    Specify a port number where xmlrpc callback webserver listens.
 *                         Default is port 8081, the port 0 switches this feature off.
 *     -dispatch/callback/plugin/xmlrpc/hostname  Specify a hostname where xmlrp callback server runs.
 *                         Default is the localhost.
 * </pre>
 * If the callback server can't be established because of the port is not free,
 * this driver loops and tries with a port number one higher until it finds a free port
 * to listen for callbacks.<br />
 * The correct port is automatically transferred in the login - QoS - so that xmlBlaster
 * can find the callback server.
 * <p />
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class XmlRpcCallbackServer implements I_CallbackServer {
   
   
   private class CbRunner extends Thread {
      
      private boolean doRun = true;
      
      private XmlRpcConnection conn;
      
      public CbRunner() throws XmlBlasterException {
         XmlRpcConnection tmpConn = getXmlRpcConnection();
         conn = tmpConn.getCopy();
      }
      
      public void shutdown() {
         if (conn != null)
            conn.sendShutdownCb();
         doRun = false;
      }
      
      private final void doSleep(long delay) {
         try {
            Thread.sleep(delay);
         }
         catch (Throwable e) {
            e.printStackTrace();
         }
      }
      
      public void run() {
         while (doRun) {
            if (conn != null) {
               try {
                  conn.getUpdates(client);
               }
               catch (XmlBlasterException ex) {
                  log.warning("An Exception occured when checking for updates. This may be common if the server has shut down while waiting");
                  // conn.sendAckOrEx("0", null, ex.getMessage());
                  doRun = false;
                  // doSleep(5000L);
               }
            }
            else {
               doSleep(5000L);
            }
         }
         shutdown();
      }
      
   }
   
   private String ME = "XmlRpcCallbackServer";
   private Global glob = null;
   private static Logger log = Logger.getLogger(XmlRpcCallbackServer.class.getName());
   private I_CallbackExtended client;
   private String loginName;
   /** Holds the XmlRpc URL string of the callback server */
   private XmlRpcUrl xmlRpcUrlCallback;
   /** XmlBlaster XMLRPC callback web server listen port is 8081 */
   public static final int DEFAULT_CALLBACK_PORT = 8081; // org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_CALLBACK_PORT;
   public CallbackAddress callbackAddress;
   private WebServer webServer = null;
   protected PluginInfo pluginInfo;

   private CbRunner singleChRunner;

   /** You must call initialize after constructing me */
   public XmlRpcCallbackServer() {}

   
   private XmlRpcConnection getXmlRpcConnection() {
      return (XmlRpcConnection)glob.getObjectEntry("xmlrpc3-connection");
   }

   private XmlScriptSerializer getSerializer() {
      XmlRpcConnection conn = getXmlRpcConnection();
      if (conn == null)
         return null;
      return conn.getSerializer();
   }
   
   /** Enforced by I_Plugin */
   public String getType() {
      return getCbProtocol();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
      this.pluginInfo = pluginInfo;
   }

   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public void initialize(Global glob, String name, CallbackAddress callbackAddress,
                          I_CallbackExtended client) throws XmlBlasterException
   {
      this.ME = "XmlRpcCallbackServer-" + name;
      this.glob = glob;

      this.callbackAddress = callbackAddress;
      if (this.pluginInfo != null)
         this.callbackAddress.setPluginInfoParameters(this.pluginInfo.getParameters());
      this.client = client;
      this.loginName = name;

      PropBoolean tmp = callbackAddress.getEnv("singleChannel", false);
      boolean useCDATA = callbackAddress.getEnv("useCDATA", false).getValue();

      if (!tmp.getValue())
         createCallbackServer(useCDATA);
      else {
         xmlRpcUrlCallback = new XmlRpcUrl(glob, callbackAddress.getBootstrapHostname(), DEFAULT_CALLBACK_PORT, "tunneled");
         callbackAddress.setRawAddress(xmlRpcUrlCallback.getUrl()); // e.g. "http://127.168.1.1:8082/"
         ME = "XmlRpcCallbackServer-" + xmlRpcUrlCallback.getUrl();
      }
      glob.addObjectEntry("xmlrpc-callback", this);
      log.info("Success, created XMLRPC callback server for " + loginName);
   }

   public void postInitialize() throws XmlBlasterException {
      PropBoolean tmp = callbackAddress.getEnv("singleChannel", false);
      if (tmp.getValue()) {
         xmlRpcUrlCallback = new XmlRpcUrl(glob, callbackAddress, false, DEFAULT_CALLBACK_PORT);
         singleChRunner = new CbRunner();
         singleChRunner.start();
      }
      
   }

   /**
    * Building a Callback server, using the tie approach.
    *
    * @param the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    *            id="CallbackCreationError"
    */
   private void createCallbackServer(boolean useCDATA) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("createCallbackServer() ...");

      this.xmlRpcUrlCallback = new XmlRpcUrl(glob, this.callbackAddress, false, DEFAULT_CALLBACK_PORT);
      try {
         if (this.xmlRpcUrlCallback.getPort() > 0) {
            // Start an 'xmlrpc webserver' if desired
            int numTries = 20; // start looking for a free port, begin with default port -dispatch/callback/plugin/xmlrpc/port <port>
            for (int ii=0; ii<numTries; ii++) {
               try {
                  // webServer = new WebServer(xmlRpcUrlCallback.getPort(), xmlRpcUrlCallback.getInetAddress());
                  SocketUrl socketUrl = new SocketUrl(glob, xmlRpcUrlCallback.getHostname(), xmlRpcUrlCallback.getPort());
                  webServer = new XblWebServer(xmlRpcUrlCallback, socketUrl, callbackAddress);
                  break;
               }
               /*
               catch(java.net.BindException e) {
                  log.warning("Port " + this.xmlRpcUrlCallback.getPort() + " for XMLRPC callback server is in use already, trying with port " +  (this.xmlRpcUrlCallback.getPort()+1) + ": " + e.toString());
                  this.xmlRpcUrlCallback.setPort(this.xmlRpcUrlCallback.getPort() + 1);
               } 
               catch(java.io.IOException e) {
               */
               catch(Throwable e) {
                  if (e.getMessage().indexOf("Cannot assign requested address") != -1) {
                     if (log.isLoggable(Level.FINE)) log.warning("Host " + this.xmlRpcUrlCallback.getHostname() + " for XMLRPC callback server is invalid: " + e.toString());
                     throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Local host IP '" + this.xmlRpcUrlCallback.getHostname() + "' for XMLRPC callback server is invalid: " + e.toString());
                  }
                  else {  // e.getMessage() = "Address already in use"
                     log.warning("Port " + this.xmlRpcUrlCallback.getPort() + " for XMLRPC callback server is in use already, trying with port " +  (this.xmlRpcUrlCallback.getPort()+1) + ": " + e.toString());
                  }
                  this.xmlRpcUrlCallback.setPort(this.xmlRpcUrlCallback.getPort() + 1);
               }
               if (ii == (numTries-1)) {
                  log.severe("Can't find free port " + this.xmlRpcUrlCallback.getPort() + " for XMLRPC callback server, please use '-dispatch/callback/plugin/xmlrpc/port <port>' to specify a free one.");
               }
            }
            
            boolean inhibitCbExceptions = callbackAddress.getEnv("inhibitCbExceptions", false).getValue();
            
            XmlRpcCallbackImpl xblImpl = new XmlRpcCallbackImpl(this, inhibitCbExceptions);
            PropertyHandlerMapping mapping = new PropertyHandlerMapping();
            XblRequestFactoryFactory factoryFactory = new XblRequestFactoryFactory();
            factoryFactory.add(xblImpl);
            mapping.setRequestProcessorFactoryFactory(factoryFactory);
            mapping.addHandler("$default", xblImpl.getClass());      // register update() method
            mapping.addHandler("", xblImpl.getClass());      // register update() method

            this.callbackAddress.setRawAddress(this.xmlRpcUrlCallback.getUrl()); // e.g. "http://127.168.1.1:8082/"
            this.ME = "XmlRpcCallbackServer-" + this.xmlRpcUrlCallback.getUrl();
            //log.info(ME, "Created XmlRpc callback http server");
         
            XmlRpcHttpServer xmlRpcServer = (XmlRpcHttpServer)webServer.getXmlRpcServer();
            XmlRpcServerConfigImpl serverCfg = new XmlRpcServerConfigImpl();
            serverCfg.setEnabledForExceptions(true);
            serverCfg.setEnabledForExtensions(true);
            xmlRpcServer.setConfig(serverCfg);
            xmlRpcServer.setHandlerMapping(mapping);
            
            XblWriterFactory writerFactory = new XblWriterFactory(useCDATA);
            xmlRpcServer.setXMLWriterFactory(writerFactory);
            webServer.start();
         }
         else
            log.info("XmlRpc callback http server not created, because of -dispatch/callback/plugin/xmlrpc/port is 0");
      } 
      catch (XmlBlasterException e) {
         throw e;
      } 
      catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, ME, "Could not initialize XMLRPC callback server on '" + this.xmlRpcUrlCallback.getUrl() + "': " + e.toString());
      }
   }

   /**
    * Returns the 'well known' protocol type. 
    * @return "XMLRPC"
    */
   public String getCbProtocol() {
      return "XMLRPC";
   }
   
   /**
    * Returns the current callback address. 
    * @return Something like "http://myserver.com:8081/"
    */
   public String getCbAddress() throws XmlBlasterException {
      if (xmlRpcUrlCallback == null)
         return null;
      return xmlRpcUrlCallback.getUrl();
   }
   
   /**
    * Shutdown the callback server.
    * @return true if everything went fine.
    */
   public void shutdown() {
      glob.removeObjectEntry("xmlrpc-callback");
      
      if (webServer != null) {
         try { 
            webServer.getXmlRpcServer().setHandlerMapping(null);
            // getHandlerMapping().removeHandler("$default");
            webServer.shutdown();
         }
         catch(Throwable e) {
            log.warning("Problems during shutdown of XMLRPC callback web server: " + e.toString());
            return;
         }
      }
      else if (this.singleChRunner != null) {
         singleChRunner.shutdown();
         singleChRunner = null;
      }
      log.info("The XMLRPC callback server is shutdown.");
   }

   /**
    * The update method.
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public String update(String cbSessionId, String updateKey, byte[] content,
                       String updateQos) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering update(): sessionId: " + cbSessionId);
      String ret = client.update(cbSessionId, updateKey, content, updateQos);
      // check if it is an inhibited Exception
      if (ret != null && XmlRpcCallbackImpl.INHIBITED_CALLBACK_EXCEPTION.equals(ret)) {
         throw new XmlBlasterException(glob, ErrorCode.COMMUNICATION_USER_HOLDBACK, ME, "Exception in Client Callback Method. The cause has been inhibited on the client side by the 'inhibitCbExceptions' flag. You can look at the client logs");
      }
      
      try {
         XmlScriptSerializer serializer = getSerializer();
         if (serializer != null)
            ret = serializer.getUpdateResponse(ret);
      }
      catch (Exception ex) {
         log.severe("Could not process update return due to " + ex.getMessage());
      }
      return ret;
   }

   /**
    * The 'oneway' update method. 
    * <p />
    * oneway is not natively supported by XmlRpc
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public void updateOneway(String cbSessionId, String updateKey, byte[] content,
                       String updateQos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering updateOneway(): sessionId: " + cbSessionId);
      try {
         client.updateOneway(cbSessionId, updateKey, content, updateQos);
      }
      catch (Throwable e) {
         log.severe("Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Ping to check if the callback server is alive. 
    * @see org.xmlBlaster.protocol.I_CallbackDriver#ping(String)
    */
   public String ping(String str) {
      try {
         XmlScriptSerializer serializer = getSerializer();
         if (serializer != null)
            return serializer.getPingResponse(Constants.RET_OK);
         return Constants.RET_OK;
      }
      catch (Exception ex) {
         return null;
      }
   }
} // class XmlRpcCallbackServer

