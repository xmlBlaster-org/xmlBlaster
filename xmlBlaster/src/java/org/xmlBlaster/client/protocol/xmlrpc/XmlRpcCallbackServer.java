/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: XmlRpcCallbackServer.java,v 1.9 2001/12/30 10:41:14 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.xmlBlaster.client.protocol.I_CallbackExtended;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.UpdateKey;

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
 *     -xmlrpc.hostnameCB     Specify a hostname where xmlrpcregistry runs.
 *                         Default is the localhost.
 * </pre>
 * If the callback server can't be established because of the port is not free,
 * this driver loops and tries with a port number one higher until it finds a free port
 * to listen for callbacks.<br />
 * The correct port is automatically transferred in the login - QoS - so that xmlBlaster
 * can find the callback server.
 * <p />
 * @author michele.laghi@attglobal.net
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
class XmlRpcCallbackServer
{
   private String ME = "XmlRpcCallbackServer";
   private final I_CallbackExtended boss;
   private final String loginName;
   /** The name for the XML-RPC registry */
   private String callbackServerUrl = null;
   /** XmlBlaster XML-RPC callback web server listen port is 8081 */
   public static final int DEFAULT_CALLBACK_PORT = 8081; // org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver.DEFAULT_CALLBACK_PORT;
   private WebServer webServer = null;


   /**
    * Construct a persistently named object.
    * @param client    Your implementation of I_CallbackExtended, or null if you don't want any updates.
    */
   public XmlRpcCallbackServer(String name, I_CallbackExtended boss) throws XmlBlasterException
   {
      this.ME = "XmlRpcCallbackServer-" + name;
      this.boss = boss;
      this.loginName = name;
      createCallbackServer();
      Log.info(ME, "Success, created XML-RPC callback server for " + loginName);
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
      if (Log.CALL) Log.call(ME, "createCallbackServer() ...");

      // Use the xmlBlaster-server xmlrpcRegistry as a fallback:
      int callbackPort = XmlBlasterProperty.get("xmlrpc.port",
                                                DEFAULT_CALLBACK_PORT); // default xmlBlaster XML-RPC publishing port is 8081
      // Use the given callback port if specified :
      callbackPort = XmlBlasterProperty.get("xmlrpc.portCB", callbackPort);

      String hostname;
      try  {
         java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
         hostname = addr.getHostName();
      } catch (Exception e) {
         Log.info(ME, "Can't determine your hostname");
         // Use the xmlBlaster-server xmlrpcRegistry as a fallback:
         hostname = XmlBlasterProperty.get("xmlrpc.hostname", "localhost");
      }
      // Use the given callback hostname if specified :
      hostname = XmlBlasterProperty.get("xmlrpc.hostnameCB", hostname);
      java.net.InetAddress inetAddr = null;
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
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
                  Log.warn(ME, "Port " + callbackPort + " for XML-RCP callback server is in use already, trying with port " +  (callbackPort+1));
                  callbackPort++;
               }
               if (ii == (numTries-1)) {
                  Log.error(ME, "Can't find free port " + callbackPort + " for XML-RCP callback server, please use -xmlrpc.portCB=<port> to specify a free one.");
               }
            }
            webServer.addHandler("$default", new XmlRpcCallbackImpl(this)); // register update() method
            callbackServerUrl = "http://" + hostname + ":" + callbackPort + "/";
            this.ME = "XmlRpcCallbackServer-" + callbackServerUrl;
            Log.info(ME, "Created XmlRpc callback http server");
         }
         else
            Log.info(ME, "XmlRpc callback http server not created, because of -xmlrpc.portCB is 0");
      } catch (Exception e) {
         e.printStackTrace();
         throw new XmlBlasterException("InitXmlRpcFailed", "Could not initialize XML-RPC registry: " + e.toString());
      }
   }


   /**
    * @return The XML-RPC registry entry of this server, which can be used for the loginQoS
    */
   public CallbackAddress getCallbackHandle()
   {
      CallbackAddress addr = new CallbackAddress("XML-RPC");
      addr.setAddress(callbackServerUrl);
      return addr;
   }


   /**
    * Shutdown the callback server.
    */
   public void shutdown()
   {
      if (webServer != null)
         webServer.removeHandler("$default");
      webServer.shutdown();
      Log.info(ME, "The XML-RPC callback server is shutdown.");
   }


   /**
    * The update method.
    * <p />
    * Gets invoked from XmlRpcCallbackImpl.java (which was called by xmlBlaster)
    */
   public void update(String loginName, String updateKey, byte[] content,
                       String updateQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering update(): loginName: " + loginName);
      boss.update(loginName, updateKey, content, updateQoS);
   }
} // class XmlRpcCallbackServer

