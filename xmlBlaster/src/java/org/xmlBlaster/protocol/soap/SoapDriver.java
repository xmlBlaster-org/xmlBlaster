/*------------------------------------------------------------------------------
Name:      SoapDriver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   SoapDriver class to invoke the xmlBlaster server in the same JVM.
Version:   $Id: SoapDriver.java,v 1.12 2003/03/22 12:28:08 laghi Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.protocol.soap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.protocol.I_XmlBlaster;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.qos.address.CallbackAddress;

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
 * java -Djafw.saw.server.config=/home/xmlblast/xmlBlaster/config/config.xml -jar lib/xmlBlaster.jar  -soap.port 9090
 * </pre>
 *
 * The interface I_Driver is needed by xmlBlaster to instantiate and shutdown
 * this driver implementation.
 * @author xmlBlaster@marcelruff.info
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/protocol.soap.html">The protocol.soap requirement</a>
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

   private static final String CONFIG_SYSTEM_PROPERTY = "jafw.saw.server.config";


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
      this.ME = "SoapDriver" + this.glob.getLogPrefixDashed();
      this.log = glob.getLog("soap");
      if (log.CALL) log.call(ME, "Entering init()");
      this.authenticate = authenticate;
      this.xmlBlasterImpl = xmlBlasterImpl;

      initializeSoapEnv(glob, this);

      xmlPort = glob.getProperty().get("soap.port", DEFAULT_HTTP_PORT);

      if (xmlPort < 1) {
         log.info(ME, "Option soap.port set to " + xmlPort + ", soap server not started");
         return;
      }

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
      log.info(ME, "See " + System.getProperty("user.dir") + File.separator + "saw.log for SOAP specific logging or check log4j configuration");
      //logger = Category.getInstance(Main.class);
   }

   /**
    * The soap plugin needs to be configured over the Java environment
    * This needs to be done for the server and for the client side, so this
    * method is 'public static' to be reused by client code.
    * <p>
    * Allows to set <i>saw.home</i> and <i>jafw.saw.server.config</i> over xmlBlaster.properties
    * or command line
    * </p>
    * <p>
    * Fall back is saw.home=XMLBLASTER_HOME/demo/soap
    */
   public static void initializeSoapEnv(Global glob, Object caller) throws XmlBlasterException {
      LogChannel log = glob.getLog("soap");
      final String ME = "SoapDriver";

      // soapanywhere has an allmost unconfigurable environment setting
      // A set saw.home switches off jafw.saw.server.config
      // A set saw.home assumes configuration in the conf subdirectory.

      // check saw.home
      if (glob.getProperty().get("saw.home", (String)null) != null)
         System.setProperty("saw.home", glob.getProperty().get("saw.home", (String)null));
      else {
         // Examine the CLASSPATH where we are loaded from and guess
         // that the home path is in ../demo/soap
         String rootPath = ClassLoaderFactory.getLoaderInfo(caller, "org.xmlBlaster.protocol.soap.SoapDriver").rootPath;
         String homePath = ".";
         if (rootPath != null) homePath = rootPath + "..";
         String sawHome = homePath + File.separator + "demo" + File.separator + "soap";
         File f = new File(sawHome);
         if (f.exists())
            System.setProperty("saw.home", glob.getProperty().get("saw.home", sawHome));
      }
      log.info(ME, "Using SOAP saw implementation home directory saw.home=" + System.getProperty("saw.home") + "");


      // try to find the config.xml file
      if (glob.getProperty().get(CONFIG_SYSTEM_PROPERTY, (String)null) != null)
         System.setProperty(CONFIG_SYSTEM_PROPERTY, glob.getProperty().get(CONFIG_SYSTEM_PROPERTY, (String)null));
      /*
      if (System.getProperty("saw.home") == null && // only if saw.home is null soapanywhere looks at CONFIG_SYSTEM_PROPERTY
          System.getProperty(CONFIG_SYSTEM_PROPERTY) == null) {

         String rootPath = PluginClassLoaderFactory.getLoaderInfo(caller, "org.xmlBlaster.protocol.soap.SoapDriver").rootPath;
         String confPath = null;
         if (rootPath != null) {    // our xmlBlaster specific location
            confPath = rootPath + ".." + File.separator + "config" + File.separator + "config.xml";
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
      */
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
         log.info(ME, "Activating SOAP driver ...");
         ServerManager manager = new ServerManager();    
         manager.loadConfig();
         if (manager.getServerCount() < 1) {
            String text = "When running in standalone mode you must specify at least one server in 'conf/config.xml'.";
            log.warn(ME, text);
            throw new XmlBlasterException(ME, text);
         }
           
         manager.startServers();
         log.info(ME, "SOAP driver ready");
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
   public void shutdown()
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
