/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.apache.xmlrpc.server.PropertyHandlerMapping;
import org.apache.xmlrpc.server.XmlRpcServer;
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
import org.xmlBlaster.util.protocol.xmlrpc.XblRequestFactoryFactory;
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
public class XmlRpcCallbackServer implements I_CallbackServer
{
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

   /** You must call initialize after constructing me */
   public XmlRpcCallbackServer() {}

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
      createCallbackServer();
      log.info("Success, created XMLRPC callback server for " + loginName);
   }


   /**
    * Building a Callback server, using the tie approach.
    *
    * @param the BlasterCallback server
    * @exception XmlBlasterException if the BlasterCallback server can't be created
    *            id="CallbackCreationError"
    */
   public void createCallbackServer() throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("createCallbackServer() ...");

      this.xmlRpcUrlCallback = new XmlRpcUrl(glob, this.callbackAddress, false, DEFAULT_CALLBACK_PORT);
      try {
         if (this.xmlRpcUrlCallback.getPort() > 0) {
            // Start an 'xmlrpc webserver' if desired
            int numTries = 20; // start looking for a free port, begin with default port -dispatch/callback/plugin/xmlrpc/port <port>
            for (int ii=0; ii<numTries; ii++) {
               try {
                  webServer = new WebServer(this.xmlRpcUrlCallback.getPort(), this.xmlRpcUrlCallback.getInetAddress());
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
            
            XmlRpcCallbackImpl xblImpl = new XmlRpcCallbackImpl(this);
            PropertyHandlerMapping mapping = new PropertyHandlerMapping();
            XblRequestFactoryFactory factoryFactory = new XblRequestFactoryFactory();
            factoryFactory.add(xblImpl);
            mapping.setRequestProcessorFactoryFactory(factoryFactory);
            mapping.addHandler("$default", xblImpl.getClass());      // register update() method

            this.callbackAddress.setRawAddress(this.xmlRpcUrlCallback.getUrl()); // e.g. "http://127.168.1.1:8082/"
            this.ME = "XmlRpcCallbackServer-" + this.xmlRpcUrlCallback.getUrl();
            //log.info(ME, "Created XmlRpc callback http server");
         
            XmlRpcServer xmlRpcServer = webServer.getXmlRpcServer();
            xmlRpcServer.setHandlerMapping(mapping);
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
   public String getCbProtocol()
   {
      return "XMLRPC";
   }
   
   /**
    * Returns the current callback address. 
    * @return Something like "http://myserver.com:8081/"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return xmlRpcUrlCallback.getUrl();
   }
   
   /**
    * Shutdown the callback server.
    * @return true if everything went fine.
    */
   public void shutdown()
   {
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
      return client.update(cbSessionId, updateKey, content, updateQos);
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
   public String ping(String str)
   {
      return Constants.RET_OK;
   }
} // class XmlRpcCallbackServer

