/*------------------------------------------------------------------------------
Name:      XmlRpcCallbackServer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Helper to connect to xmlBlaster using IIOP
Version:   $Id: XmlRpcCallbackServer.java,v 1.3 2000/10/24 12:02:09 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.client.protocol.xmlrpc;


import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.UpdateKey;

import helma.xmlrpc.WebServer;


/**
 * Example for a XmlRpc callback implementation.
 * <p />
 * You can use this default callback handling with your clients,
 * but if you need other handling of callbacks, take a copy
 * of this Callback implementation and add your own code.
 * <p />
 * The xmlBlaster callback client call one of the update methods defined in AbstractCallbackExtended
 * which delegates it to this update() method.
 * <p />
 * <pre>
 *     -xmlrpc.portCB      Specify a port number where xmlrpc callback webserver listens.
 *                         Default is port 8081, the port 0 switches this feature off.
 *     -xmlrpc.hostnameCB     Specify a hostname where xmlrpcregistry runs.
 *                         Default is the localhost.
 * </pre>
 * <p />
 * Invoke options: <br />
 * <pre>
 *   java -Dsax.driver=com.sun.xml.parser.Parser MyApp
 * </pre>
 * @author michele.laghi@attglobal.net
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>.
 */
class XmlRpcCallbackServer extends AbstractCallbackExtended
{
   private final String ME;
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

      try {
         if (callbackPort > 0) {
            // Start an 'xmlrpc webserver' if desired
            webServer = new WebServer(callbackPort); // !!! missing: we can't pass the hostname
            webServer.addHandler("$default", new XmlRpcCallbackImpl(this)); // register update() method
            callbackServerUrl = "http://" + hostname + ":" + callbackPort + "/";
            Log.info(ME, "Created XmlRpc callback web server " + callbackServerUrl);
         }
         else
            Log.info(ME, "XmlRpc callback web server not created, because of -xmlrpc.portCB is 0");
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
      // missing code to close socket !!!
      Log.info(ME, "The XML-RPC callback server is shutdown.");
   }


   /**
    * The update method.
    * <p />
    * Gets invoked from xmlBlaster callback via client WebServer,
    * which calls one of the update methods defined in AbstractCallbackExtended
    * which delegates it to this update() method.
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content,
                       UpdateQoS updateQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering update(): loginName: " + loginName);
      boss.update(loginName, updateKey, content, updateQoS);
   }
} // class XmlRpcCallbackServer

