/*------------------------------------------------------------------------------
Name:      XmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java,v 1.23 2002/02/14 15:00:00 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
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
 * @see
 */
public class XmlRpcDriver implements I_Driver
{
   private static final String ME = "XmlRpcDriver";
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The port for the xml-rpc web server */
   private int xmlPort = 8080;
   /** The xml-rpc HTTP web server */
   private WebServer webServer = null;
   /** The URL which clients need to use to access this server */
   private String serverUrl = null;


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
    * Start xmlBlaster XML-RPC access.
    * <p />
    * Enforced by interface I_Driver.
    * @param args The command line parameters
    */
   public void init(String args[], I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      xmlPort = XmlBlasterProperty.get("xmlrpc.port", 8080);

      if (xmlPort < 1) {
         Log.info(ME, "Option xmlrpc.port set to " + xmlPort + ", xmlRpc server not started");
         return;
      }

      if (XmlBlasterProperty.get("xmlrpc.debug", false) == true)
         XmlRpc.setDebug(true);

      String hostname = XmlBlasterProperty.get("xmlrpc.hostname", (String)null);
      if (hostname == null) {
         try  {
            java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
            hostname = addr.getHostName();
         } catch (Exception e) {
            Log.info(ME, "Can't determine your hostname");
            hostname = "localhost";
         }
      }
      java.net.InetAddress inetAddr = null;
      try {
         inetAddr = java.net.InetAddress.getByName(hostname);
      } catch(java.net.UnknownHostException e) {
         throw new XmlBlasterException("InitXmlRpcFailed", "The host [" + hostname + "] is invalid, try '-xmlrpc.hostname=<ip>': " + e.toString());
      }

      try {
         webServer = new WebServer(xmlPort, inetAddr);
         // publish the public methods to the XmlRpc web server:
         webServer.addHandler("authenticate", new AuthenticateImpl(authenticate));
         webServer.addHandler("xmlBlaster", new XmlBlasterImpl(xmlBlasterImpl));
         serverUrl = "http://" + hostname + ":" + xmlPort + "/";
         Log.info(ME, "Started successfully XML-RPC driver, access url=" + serverUrl);
      } catch (IOException e) {
         Log.error(ME, "Error creating webServer on '" + inetAddr + ":" + xmlPort + "': " + e.toString());
         e.printStackTrace();
      }
   }


   /**
    * Instructs XML-RPC driver to shut down.
    * <p />
    * Enforced by interface I_Driver.
    */
   public void shutdown()
   {
      if (webServer != null) {
         webServer.removeHandler("authenticate");
         webServer.removeHandler("xmlBlaster");
         webServer.shutdown();
         webServer = null;
         Log.info(ME, "XML-RPC driver stopped, handler released.");
      }
      else
         Log.info(ME, "XML-RPC shutdown, nothing to do.");
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
