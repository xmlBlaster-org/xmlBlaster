/*------------------------------------------------------------------------------
Name:      SoapDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SoapDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SoapDriver.java,v 1.2 2002/08/24 18:05:52 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.soap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;

import org.jafw.saw.*;
import org.jafw.saw.rpc.*;
import org.jafw.saw.util.*;
import org.jafw.saw.transport.*;
import org.jafw.saw.server.*;
import java.io.IOException;
import java.io.File;


/**
 * Soap driver class to invoke the xmlBlaster server over HTTP SOAP.
 * <p />
 * This driver needs to be registered in xmlBlaster.properties
 * and will be started on xmlBlaster startup, for example:
 * <pre>
 * ProtocolPlugin[SOAP][1.0]=org.xmlBlaster.protocol.soap.SoapDriver
 *
 * CbProtocolPlugin[SOAP][1.0]=org.xmlBlaster.protocol.soap.CallbackSoapDriver
 * </pre>
 *
 * The variable soap.port (default 8686) sets the http web server port,
 * you may change it in xmlBlaster.properties or on command line:
 * <pre>
 * java java -Djafw.saw.server.config=/home/xmlblast/xmlBlaster/config/config.xml -jar lib/xmlBlaster.jar  -soap.port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author ruff@swand.lake.de
 */
public class SoapDriver implements I_Driver
{
   private String ME = "SoapDriver";
   private Global glob;
   private LogChannel log;
   /** The singleton handle for this xmlBlaster server */
   private I_Authenticate authenticate = null;
   /** The singleton handle for this xmlBlaster server */
   private I_XmlBlaster xmlBlasterImpl = null;
   public static final int DEFAULT_HTTP_PORT = 8686;
   /** The port for the SOAP web server */
   private int xmlPort = DEFAULT_HTTP_PORT;
   /** The SOAP HTTP web server */
   private ServerManager manager = null;
   /** The URL which clients need to use to access this server */
   private String serverUrl = null;

   private final String CONFIG_SYSTEM_PROPERTY = "jafw.saw.server.config";


   private java.net.InetAddress inetAddr = null;

   public SoapDriver() {
      String vers = System.getProperty("java.version"); // "1.3.1" or "1.4.1-rc"
      if (vers.compareTo("1.4") < 0)
         Global.instance().getLog("soap").error(ME, "The SOAP plugin requires JDK 1.4 or higher to execute, you have JDK " + vers + ", please upgrade.");
      else
         Global.instance().getLog("soap").trace(ME, "The SOAP plugin requires JDK 1.4 or higher to execute, you have JDK " + vers + ", OK!");
   }

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
    * @return "SOAP"
    */
   public String getProtocolId() {
      return "SOAP";
   }

   /** Enforced by I_Plugin */
   public String getType() {
      return getProtocolId();
   }

   /** Enforced by I_Plugin */
   public String getVersion() {
      return "1.0";
   }

   /** Enforced by I_Plugin */
   public void init(org.xmlBlaster.util.Global glob, String[] options) {
   }

   /**
    * Get the address how to access this driver. 
    * @return "http://server.mars.universe:8686/"
    */
   public String getRawAddress() {
      return serverUrl;
   }

   /**
    * Start xmlBlaster SOAP access.
    * <p />
    * Enforced by interface I_Driver.
    * @param glob Global handle to access logging, property and commandline args
    * @param authenticate Handle to access authentication server
    * @param xmlBlasterImpl Handle to access xmlBlaster core
    */
   public void init(Global glob, I_Authenticate authenticate, I_XmlBlaster xmlBlasterImpl)
      throws XmlBlasterException {
      this.glob = glob;
      this.ME = "SoapDriver" + this.glob.getLogPraefixDashed();
      this.log = glob.getLog("soap");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      // try to find the xmlBlaster/config/conf.xml file
      if (System.getProperty(CONFIG_SYSTEM_PROPERTY) == null) {
         java.net.URL url = this.getClass().getResource("SoapDriver");
         //java.net.URL url = this.getClass().getResource("org.xmlBlaster.protocol.soap.SoapDriver");
         String confPath = null;
         if (url != null) {
            String libPath = url.getFile().toString();
            libPath = libPath.substring(0, libPath.lastIndexOf("org/xmlBlaster/protocol/soap/SoapDriver"));
            confPath = libPath + ".." + File.separator + "config" + File.separator + "config.xml";
         }
         else {
            confPath = "config" + File.separator + "config.xml";
         }
         File f = new File(confPath);
         if (f.exists() && f.canRead()) {
            log.info(ME, "Using " + CONFIG_SYSTEM_PROPERTY + "=" + confPath);
            System.setProperty(CONFIG_SYSTEM_PROPERTY, confPath);
         }
         else
            log.warn(ME, "Can't locate " + confPath + ", specify with " + CONFIG_SYSTEM_PROPERTY + "=<file>");
      }

      if (System.getProperty("saw.home") == null)
         System.setProperty("saw.home", glob.getProperty().get("saw.home", "/home/xmlblast/saw_0.995"));
      log.info(ME, "Using SOAP saw implementation home directory saw.home=" + System.getProperty("saw.home") + "");

      xmlPort = glob.getProperty().get("soap.port", DEFAULT_HTTP_PORT);

      if (xmlPort < 1) {
         log.info(ME, "Option soap.port set to " + xmlPort + ", soap server not started");
         return;
      }

      //if (glob.getProperty().get("soap.debug", false) == true)
      //   Soap.setDebug(true);

      String hostname = glob.getProperty().get("soap.hostname", (String)null);
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
         throw new XmlBlasterException("InitSoapFailed", "The host [" + hostname + "] is invalid, try '-soap.hostname=<ip>': " + e.toString());
      }
      serverUrl = "http://" + hostname + ":" + xmlPort + "/";

      SAWHelper.initLogging();    
      //logger = Category.getInstance(Main.class);
   }

   /**
    * Activate xmlBlaster access through this protocol.
    */
   public synchronized void activate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering activate");
      String action = "start";

      try {
        SAWHelper.initConfig();
      } catch (ConfigException ce) {
        System.out.println(ce.getMessage());
        Throwable t = ce.getRootCause();
        if (t != null)
          t.printStackTrace();
        throw new XmlBlasterException(ME, ce.getMessage());
      }

      try {
         ServerManager manager = new ServerManager();    
         manager.loadConfig();
         if (manager.getServerCount() < 1) {
            String text = "When running in standalone mode you must specify at least one server in 'conf/config.xml'.";
            log.warn(ME, text);
            throw new XmlBlasterException(ME, text);
         }
           
         manager.startServers();
      }
      catch (IOException e) {
         String text = "Starting SOAP server failed: " + e.toString();
         log.error(ME, text);
         throw new XmlBlasterException(ME, text);
      }

      /*
         webServer = new WebServer(xmlPort, inetAddr);
         // publish the public methods to the Soap web server:
         webServer.addHandler("authenticate", new AuthenticateImpl(glob, authenticate));
         webServer.addHandler("xmlBlaster", new XmlBlasterImpl(xmlBlasterImpl));
         //serverUrl = "http://" + hostname + ":" + xmlPort + "/";
      */
   }

   /**
    * Deactivate xmlBlaster access (standby), no clients can connect. 
    */
   public synchronized void deActivate() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering deActivate");
      if (manager != null) {
         try {
            manager.killServers();        
         }
         catch (IOException e) {
            String text = "Stopping SOAP server failed: " + e.toString();
            log.warn(ME, text);
            throw new XmlBlasterException(ME, text);
         }
         manager = null;
         log.info(ME, "SOAP driver stopped, handler released.");
      }
      else
         log.info(ME, "SOAP shutdown, nothing to do.");
   }

   /**
    * Instructs SOAP driver to shut down.
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
      text += "SoapDriver options:\n";
      text += "   -soap.port        The SOAP web server port [" + DEFAULT_HTTP_PORT + "].\n";
      text += "   -soap.hostname    Specify a hostname where the SOAP web server runs.\n";
      text += "                     Default is the localhost.\n";
      text += "\n";
      return text;
   }
}
