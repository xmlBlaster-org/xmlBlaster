/*------------------------------------------------------------------------------
Name:      XmlRpcDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XmlRpcDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: XmlRpcDriver.java,v 1.48 2004/02/22 17:28:36 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.xmlrpc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;

import org.apache.xmlrpc.*;
import java.io.IOException;


/**
 * XmlRpc driver class to invoke the xmlBlaster server over HTTP XMLRPC.
 * <p />
 * This driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 * ProtocolPlugin[XMLRPC][1.0]=org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver
 * CbProtocolPlugin[XMLRPC][1.0]=org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver
 * </pre>
 *
 * The variable plugin/xmlrpc/port (default 8080) sets the http web server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java -jar lib/xmlBlaster.jar  -plugin/xmlrpc/port 9090
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
   /** The xml-rpc HTTP web server */
   private WebServer webServer;
   /** The URL which clients need to use to access this server */
   private XmlRpcUrl xmlRpcUrl;


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
    * @return "XMLRPC"
    */
   public String getProtocolId() {
      return "XMLRPC";
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
         this.authenticate = engineGlob.getAuthenticate();
         if (this.authenticate == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "authenticate object is null");
         }
         I_XmlBlaster xmlBlasterImpl = this.authenticate.getXmlBlaster();
         if (xmlBlasterImpl == null) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME + ".init", "xmlBlasterImpl object is null");
         }

         init(glob, new AddressServer(glob, getType(), glob.getId()), this.authenticate, xmlBlasterImpl);

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
      return this.xmlRpcUrl.getUrl();
   }

   /**
    * Start xmlBlaster XMLRPC access.
    * <p />
    * Enforced by interface I_Driver.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   private synchronized void init(Global glob, AddressServer addressServer, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException
   {
      this.glob = glob;
      this.ME = "XmlRpcDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("xmlrpc");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      this.xmlRpcUrl = new XmlRpcUrl(glob, addressServer); // e.g. "http://127.168.1.1:8080/"
      if (this.xmlRpcUrl.getPort() < 1) {
         log.info(ME, "Option plugin/xmlrpc/port set to " + this.xmlRpcUrl.getPort() + ", xmlRpc server not started");
         return;
      }

      // "-plugin/xmlrpc/debug true"
      if (addressServer.getEnv("debug", false).getValue() == true)
         XmlRpc.setDebug(true);
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");
      try {
         webServer = new WebServer(this.xmlRpcUrl.getPort(), this.xmlRpcUrl.getInetAddress());
         // publish the public methods to the XmlRpc web server:
         webServer.addHandler("authenticate", new AuthenticateImpl(glob, authenticate));
         webServer.addHandler("xmlBlaster", new XmlBlasterImpl(glob, xmlBlasterImpl));
         log.info(ME, "Started successfully XMLRPC driver, access url=" + this.xmlRpcUrl.getUrl());
      } catch (IOException e) {
         log.error(ME, "Error creating webServer on '" + this.xmlRpcUrl.getUrl() + "': " + e.toString());
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
         log.info(ME, "XMLRPC driver stopped, handler released.");
      }
      else
         log.info(ME, "XMLRPC shutdown, nothing to do.");
   }

   /**
    * Instructs XMLRPC driver to shut down.
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
      text += "   -plugin/xmlrpc/port\n";
      text += "                       The XMLRPC web server port [" + DEFAULT_HTTP_PORT + "].\n";
      text += "   -plugin/xmlrpc/hostname\n";
      text += "                       Specify a hostname where the XMLRPC web server runs.\n";
      text += "                       Default is the localhost.\n";
      text += "   -plugin/xmlrpc/debug\n";
      text += "                       true switches on detailed XMLRPC debugging [false].\n";
      text += "\n";
      return text;
   }
}
