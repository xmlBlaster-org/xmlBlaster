/*------------------------------------------------------------------------------
Name:      XmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java,v 1.42 2003/03/22 12:28:09 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.authentication.Authenticate;

import org.apache.xmlrpc.*;
import java.io.IOException;


/**
 * XmlRpc driver class to invoke the xmlBlaster server over HTTP XML-RPC.
 * <p />
 * This driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 * ProtocolPlugin[XML-RPC][1.0]=org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 * CbProtocolPlugin[XML-RPC][1.0]=org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver
 * </pre>
 *
 * The variable xmlrpc.port (default 8080) sets the http web server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -xmlrpc.port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @see <a href="http://marc.theaimsgroup.com/?l=rpc-user&m=102009663407418&w=2">Configuring SSL with XmlRpc</a>
 * @author xmlBlaster@marcelruff.info
 */
public class XmlRpcDriver implements I_Driver
{
   private String ME = "XmlRpcDriver";
   private Global glob;
   private LogChannel log;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   public static final int DEFAULT_HTTP_PORT = 8080;
   /** The port for the xml-rpc web server */
   private int xmlPort = DEFAULT_HTTP_PORT;
   /** The xml-rpc HTTP web server */
   private WebServer webServer = null;
   /** The URL which clients need to use to access this server */
   private String serverUrl = null;

   private java.net.InetAddress inetAddr = null;


   /**
    * Get a human readable name of this driver.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String getName() {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "XML-RPC"
    */
   public String getProtocolId() {
      return "XML-RPC";
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
    * @return "http://server.mars.universe:8080/"
    */
   public String getRawAddress() {
      return serverUrl;
   }

   /**
    * Start xmlBlaster XML-RPC access.
    * <p />
    * Enforced by interface I_Driver.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   public void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "XmlRpcDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      xmlPort = glob.getProperty().get("xmlrpc.port", DEFAULT_HTTP_PORT);

      if (xmlPort < 1) {
         log.info(ME, "Option xmlrpc.port set to " + xmlPort + ", xmlRpc server not started");
         return;
      }

      if (glob.getProperty().get("xmlrpc.debug", false) == true)
         XmlRpc.setDebug(true);

      String hostname = glob.getProperty().get("xmlrpc.hostname", (String)null);
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
         throw new XmlBlasterException("InitXmlRpcFailed", "The host [" + hostname + "] is invalid, try '-xmlrpc.hostname=<ip>': " + e.toString());
      }
      serverUrl = "http://" + hostname + ":" + xmlPort + "/";
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");
      try {
         webServer = new WebServer(xmlPort, inetAddr);
         // publish the public methods to the XmlRpc web server:
         webServer.addHandler("authenticate", new AuthenticateImpl(glob, authenticate));
         webServer.addHandler("xmlBlaster", new XmlBlasterImpl(glob, xmlBlasterImpl));
         //serverUrl = "http://" + hostname + ":" + xmlPort + "/";
         log.info(ME, "Started successfully XML-RPC driver, access url=" + serverUrl);
      } catch (IOException e) {
         log.error(ME, "Error creating webServer on '" + inetAddr + ":" + xmlPort + "': " + e.toString());
         //e.printStackTrace();
      }
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      if (webServer != null) {
         try {
            webServer.removeHandler("authenticate");
            webServer.removeHandler("xmlBlaster");
            webServer.shutdown();
         }
         catch(Throwable e) {
            log.warn(ME, "Problems during shutdown of xmlrpc web server: " + e.toString());
         }
         webServer = null;
         log.info(ME, "XML-RPC driver stopped, handler released.");
      }
      else
         log.info(ME, "XML-RPC shutdown, nothing to do.");
   }

   /**
    * Instructs XML-RPC driver to shut down.
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown() throws XmlBlasterException {
      try {
         deActivate();
      } catch (XmlBlasterException e) {
         log.error(ME, e.toString());
      }
   }

   /**
    * Command line usage.
    * <p />
    * Enforced by interface I_Driver.
    */
   public String usage()
   {
      String text = "\n";
      text += "XmlRpcDriver options:\n";
      text += "   -xmlrpc.port        The XML-RPC web server port [" + DEFAULT_HTTP_PORT + "].\n";
      text += "   -xmlrpc.hostname    Specify a hostname where the XML-RPC web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -xmlrpc.debug       true switches on detailed XML-RPC debugging [false].\n";
      text += "\n";
      return text;
   }
}
