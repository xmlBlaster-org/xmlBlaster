/*------------------------------------------------------------------------------
Name:      XmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java,v 1.5 2000/10/11 20:47:37 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.client.LoginQosWrapper;

import helma.xmlrpc.*;
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
   private Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   /** The port for the xml-rpc web server */
   private int xmlPort = 8080;
   /** The xml-rpc HTTP web server */
   private WebServer webserver;


   /** Get a human readable name of this driver.
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
   public void init(String args[], Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      // similar to -Dsax.driver=com.sun.xml.parser.Parser
      String dr = System.getProperty("sax.driver");
      if (dr == null) {
         System.setProperty("sax.driver", "com.sun.xml.parser.Parser");
      }
      xmlPort = XmlBlasterProperty.get("xmlrpc.port", 8080);
      try {
         webserver = new WebServer(xmlPort);
         // publish the public methods to the XmlRpc web server:
         webserver.addHandler("authenticate", authenticate);
         webserver.addHandler("xmlBlaster", new XmlBlasterImpl(xmlBlasterImpl));
         Log.info(ME, "Started successfully XML-RPC driver, the web server is listening on port " + xmlPort);
      } catch (IOException e) {
         Log.error(ME, "Error creating webserver: " + e.toString());
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
      Log.info(ME, "Shutting down ...");
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
      text += "   java -Dsax.driver=  JVM property [com.sun.xml.parser.Parser].\n";
      text += "\n";
      return text;
   }
}
