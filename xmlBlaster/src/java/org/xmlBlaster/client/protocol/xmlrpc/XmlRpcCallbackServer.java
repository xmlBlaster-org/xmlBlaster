/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.I_CallbackServer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;

import org.apache.xmlrpc.WebServer;


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
 *     -xmlrpc.portCB      Specify a port number where xmlrpc callback webserver listens.
 *                         Default is port 8081, the port 0 switches this feature off.
 *     -xmlrpc.hostnameCB  Specify a hostname where xmlrp callback server runs.
 *                         Default is the localhost.
 * </pre>
 * If the callback server can't be established because of the port is not free,
 * this driver loops and tries with a port number one higher until it finds a free port
 * to listen for callbacks.<br />
 * The correct port is automatically transferred in the login - QoS - so that xmlBlaster
 * can find the callback server.
 * <p />
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 */
public class XmlRpcCallbackServer implements I_CallbackServer
{
   private String ME = "XmlRpcCallbackServer";
   private Global glob = null;
   private LogChannel log = null;
   private I_CallbackExtended client;
   private String loginName;
   /** The name for the XML-RPC registry */
   private String callbackServerUrl = null;
   /** XmlBlaster XML-RPC callback web server listen port is 8081 */
   public static final int DEFAULT_CALLBACK_PORT = 8081; // org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_CALLBACK_PORT;
   private WebServer webServer = null;

   /** You must call initialize after constructing me */
   public XmlRpcCallbackServer() {}

   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public void initialize(Global glob, String name, I_CallbackExtended client) throws XmlBlasterException
   {
      this.ME = "XmlRpcCallbackServer-" + name;
      this.glob = glob;
      this.log = glob.getLog("xmlrpc");
      this.client = client;
      this.loginName = name;
      createCallbackServer();
      log.info(ME, "Success, created XML-RPC callback server for " + loginName);
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
      if (log.CALL) log.call(ME, "createCallbackServer() ...");

      // TODO: Use ConnectQos to allow hardcoded hostname/socket

      // Use the xmlBlaster-server xmlrpcRegistry as a fallback:
      int callbackPort = glob.getProperty().get("xmlrpc.port",
                                                DEFAULT_CALLBACK_PORT); // default xmlBlaster XML-RPC publishing port is 8081
      // Use the given callback port if specified :
      callbackPort = glob.getProperty().get("xmlrpc.portCB", callbackPort);

      String hostname = glob.getCbHostname("xmlrpc.hostnameCB");
      java.net.InetAddress inetAddr = null;
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
         if (log.TRACE) log.trace("InitXmlRpcFailed", "The host [" + hostname + "] for the callback server is invalid, try '-xmlrpc.hostnameCB=<ip>': " + e.toString());
         throw new XmlBlasterException("InitXmlRpcFailed", "The host [" + hostname + "] for the callback server is invalid, try '-xmlrpc.hostnameCB=<ip>': " + e.toString());
      }

      try {
         if (callbackPort > 0) {
            // Start an 'xmlrpc webserver' if desired
            int numTries = 20; // start looking for a free port, begin with default port -xmlrpc.portCB=<port>
            for (int ii=0; ii<numTries; ii++) {
               try {
                  webServer = new WebServer(callbackPort, inetAddr);
                  break;
               } catch(java.net.BindException e) {
                  log.warn(ME, "Port " + callbackPort + " for XML-RCP callback server is in use already, trying with port " +  (callbackPort+1) + ": " + e.toString());
                  callbackPort++;
               } catch(java.io.IOException e) {
                  if (e.getMessage().indexOf("Cannot assign requested address") != -1) {
                     if (log.TRACE) log.warn(ME, "Host " + hostname + " for XML-RCP callback server is invalid: " + e.toString());
                     throw new XmlBlasterException(ME, "Local host IP '" + hostname + "' for XML-RCP callback server is invalid: " + e.toString());
                  }
                  else {  // e.getMessage() = "Address already in use"
                     log.warn(ME, "Port " + callbackPort + " for XML-RCP callback server is in use already, trying with port " +  (callbackPort+1) + ": " + e.toString());
                  }
                  callbackPort++;
               }
               if (ii == (numTries-1)) {
                  log.error(ME, "Can't find free port " + callbackPort + " for XML-RCP callback server, please use -xmlrpc.portCB=<port> to specify a free one.");
               }
            }
            webServer.addHandler("$default", new XmlRpcCallbackImpl(this)); // register update() method
            callbackServerUrl = "http://" + hostname + ":" + callbackPort + "/";
            this.ME = "XmlRpcCallbackServer-" + callbackServerUrl;
            //log.info(ME, "Created XmlRpc callback http server");
         }
         else
            log.info(ME, "XmlRpc callback http server not created, because of -xmlrpc.portCB is 0");
      } catch (XmlBlasterException e) {
         throw e;
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitXmlRpcFailed", "Could not initialize XML-RPC callback server host=" + hostname + " port=" + callbackPort + ": " + e.toString());
      }
   }

   /**
    * Returns the 'well known' protocol type. 
    * @return "XML-RPC"
    */
   public String getCbProtocol()
   {
      return "XML-RPC";
   }
   
   /**
    * Returns the current callback address. 
    * @return Something like "http://myserver.com/xmlrpc"
    */
   public String getCbAddress() throws XmlBlasterException
   {
      return callbackServerUrl;
   }
   
   /**
    * Shutdown the callback server.
    * @return true if everything went fine.
    */
   public boolean shutdownCb()
   {
      if (webServer != null) {
         try { 
            webServer.removeHandler("$default");
            webServer.shutdown();
         }
         catch(Throwable e) {
            log.warn(ME, "Problems during shutdown of XML-RPC callback web server: " + e.toString());
            return false;
         }
      }
      log.info(ME, "The XML-RPC callback server is shutdown.");
      return true;
   }

   /**
    * The update method.
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public String update(String cbSessionId, String updateKey, byte[] content,
                       String updateQos) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "Entering update(): sessionId: " + cbSessionId);
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
      if (log.CALL) log.call(ME, "Entering updateOneway(): sessionId: " + cbSessionId);
      try {
         client.updateOneway(cbSessionId, updateKey, content, updateQos);
      }
      catch (Throwable e) {
         log.error(ME, "Caught exception which can't be delivered to xmlBlaster because of 'oneway' mode: " + e.toString());
      }
   }

   /**
    * Ping to check if the callback server is alive. 
    * This ping checks the availability on the application level.
    * @param qos Currently an empty string ""
    * @return    Currently an empty string ""
    */
   public String ping(String str)
   {
      return "";
   }
} // class XmlRpcCallbackServer

