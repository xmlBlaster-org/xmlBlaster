/*------------------------------------------------------------------------------
Name:      XmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java,v 1.34 2002/06/19 12:36:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;

import org.apache.xmlrpc.*;
import java.io.IOException;


/**
 * XmlRpc driver class to invoke the xmlBlaster server over HTTP XML-RPC.
 * <p />
 * This driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 *   Protocol.Drivers=IOR:org.xmlBlaster.protocol.corba.CorbaDriver,\
 *                    RMI:org.xmlBlaster.protocol.rmi.RmiDriver,\
 *                    XML-RPC:org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 *
 *   Protocol.CallbackDrivers=IOR:org.xmlBlaster.protocol.corba.CallbackCorbaDriver,\
 *                            RMI:org.xmlBlaster.protocol.rmi.CallbackRmiDriver,\
 *                            XML-RPC:org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver
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
 * @author ruff@swand.lake.de
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
   public String getName()
   {
      return ME;
   }

   /**
    * Access the xmlBlaster internal name of the protocol driver. 
    * @return "XML-RPC"
    */
   public String getProtocolId()
   {
      return "XML-RPC";
   }

   /**
    * Get the address how to access this driver. 
    * @return "http://server.mars.universe:8080/"
    */
   public String getRawAddress()
   {
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
      this.ME = "XmlRpcDriver" + this.glob.getLogPraefixDashed();
      this.log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      xmlPort = glob.getProperty().get("xmlrpc.port", 8080);

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
         webServer.addHandler("xmlBlaster", new XmlBlasterImpl(xmlBlasterImpl));
         //serverUrl = "http://" + hostname + ":" + xmlPort + "/";
         log.info(ME, "Started successfully XML-RPC driver, access url=" + serverUrl);
      } catch (IOException e) {
         log.error(ME, "Error creating webServer on '" + inetAddr + ":" + xmlPort + "': " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      if (webServer != null) {
         webServer.removeHandler("authenticate");
         webServer.removeHandler("xmlBlaster");
         webServer.shutdown();
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
   public void shutdown(boolean force)
   {
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
      text += "   -xmlrpc.port        The XML-RPC web server port [8080].\n";
      text += "   -xmlrpc.hostname    Specify a hostname where the XML-RPC web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -xmlrpc.debug       true switches on detailed XML-RPC debugging [false].\n";
      text += "\n";
      return text;
   }
}
