/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
Version:   $Id: Global.java,v 1.37 2002/06/25 07:39:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceConsole;
import org.jutils.log.LogDeviceFile;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.util.recorder.RecorderPluginManager;
import org.xmlBlaster.authentication.HttpIORServer;

import java.util.Properties;

import java.applet.Applet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.net.MalformedURLException;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Enumeration;

import java.net.Socket;


/**
 * Global variables to avoid singleton. 
 *
 * @see classtest.GlobalTest
 */
public class Global implements Cloneable
{
   private static Global firstInstance = null;

   /** Version string, please change for new releases (4 digits) */
   private String versionDefault = "0.79f";
   /** This will be replaced by build.xml with the current version */
   private String version = "@version@";
   /** This will be replaced by build.xml with the build timestamp */
   private String buildTimestamp = "@build.timestamp@";
   /** This will be replaced by build.xml with the compiling JDK vendor */
   private String buildJavaVendor = "@build.java.vendor@";
   /** This will be replaced by build.xml with the compiling JDK version */
   private String buildJavaVersion = "@build.java.version@";

   protected String ME = "Global";
   protected String ip_addr = null;
   protected String id = "";

   protected String[] args;
   protected Property property = null;
   protected String errorText = null;

   protected String cbHostname = null;


   // deprecated
   protected org.xmlBlaster.util.Log log;

   protected /*final*/ Map nativeCallbackDriverMap;
   /** Store objecte in the scope of one client connection or server instance */
   protected /*final*/ Map objectMap;
   protected Address bootstrapAddress = null;
   protected PluginLoader clientSecurityLoader = null;

   protected RecorderPluginManager recorderPluginManager = null;
   private HttpIORServer httpServer = null;  // xmlBlaster publishes his AuthServer IOR

   protected Hashtable logChannels = new Hashtable();
   protected LogChannel logDefault = null;

   /**
    * Constructs an initial Global object. 
    */
   public Global()
   {
      if (this.firstInstance != null) {
         System.out.println("######Global empty constructor invoked again, try Global.instance()");
         //Thread.currentThread().dumpStack();
      }
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
      }
      this.args = new String[0];
      initProps(this.args);
      initId();
      logDefault = new LogChannel(null, getProperty());
      log = new org.xmlBlaster.util.Log(); // old style
      initLog(logDefault);
      nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());
      objectMap = Collections.synchronizedMap(new HashMap());
   }

   /**
    * Constructs an initial Global object which is initialized
    * by your args array (usually the command line args). 
    */
   public Global(String[] args)
   {
      if (this.firstInstance != null) {
         System.out.println("######Global args constructor invoked again, try Global.instance()");
         //Thread.currentThread().dumpStack();
      }
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
      }
      initProps(args);
      initId();
      logDefault = new LogChannel(null, getProperty());
      log = new org.xmlBlaster.util.Log(); // old style
      initLog(logDefault);
      nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());
      objectMap = Collections.synchronizedMap(new HashMap());
      init(args);
   }

   /**
    * If you have a util.Global and need a shallow copy. 
    */
   public Global(org.xmlBlaster.util.Global utilGlob) {
      shallowCopy(utilGlob);
      //Thread.currentThread().dumpStack();
   }

   /**
    * See @version@ which will be replaced by build.xml with the current version
    * @return e.g. "0.79f"
    */
   public String getVersion() {
      if (version.indexOf("@") == -1) // Check if replaced
         return version;
      return versionDefault;
   }

   /**
    * See @build.timestamp@ which will be replaced by build.xml with the current timestamp
    * @return e.g. "06/17/2002 01:38 PM"
    */
   public String getBuildTimestamp() {
      return buildTimestamp;
   }

   /**
    * @return e.g. "1.3.1-beta"
    */
   public String getBuildJavaVendor() {
      return buildJavaVendor;
   }

   /**
    * @return e.g. "1.3.1-beta"
    */
   public String getBuildJavaVersion() {
      return buildJavaVersion;
   }

   /**
    * Our identifier, the cluster node we want connect to
    */
   private void initId() {
      this.id = getProperty().get("server.node.id", (String)null);
      if (this.id == null)
         this.id = getProperty().get("cluster.node.id", "xmlBlaster");  // fallback
      if (this.id == null && getBootstrapAddress().getPort() > 0) {
         this.id = getBootstrapAddress().getAddress();
      }
   }

   protected void shallowCopy(org.xmlBlaster.util.Global utilGlob)
   {
      this.ip_addr = utilGlob.ip_addr;
      this.id = utilGlob.id;
      this.args = utilGlob.args;
      this.property = utilGlob.property;
      this.errorText = utilGlob.errorText;
      this.nativeCallbackDriverMap = utilGlob.nativeCallbackDriverMap;
      this.objectMap = utilGlob.objectMap;
      this.bootstrapAddress = utilGlob.bootstrapAddress;
      this.clientSecurityLoader = utilGlob.clientSecurityLoader;
      this.recorderPluginManager = utilGlob.recorderPluginManager;
      this.logChannels = utilGlob.logChannels;
      this.logDefault = utilGlob.logDefault;
   }

   /**
    * Initialize logging with environment variables. 
    * <pre>
    *   -logFile  output.txt
    *   -logFile[cluster] cluster-output.txt
    *   -logConsole false
    *   -logConsole[cluster] true
    * </pre>
    */
   private void initLog(LogChannel lc) {
      String key = lc.getChannelKey();

      //lc.setDefaultLogLevel();

      boolean bVal = getProperty().get("logConsole", true);
      if (key != null) getProperty().get("logConsole[" + key + "]", bVal);
      if (bVal == true) {
         LogDeviceConsole ldc = new LogDeviceConsole(lc);
         lc.addLogDevice(ldc);
      }

      String strFilename = getProperty().get("logFile", (String)null);
      if (key != null) strFilename = getProperty().get("logFile[" + key + "]", strFilename);
      if (strFilename != null) {
         LogDeviceFile ldf = new LogDeviceFile(lc, strFilename);
         lc.addLogDevice(ldf);
         System.out.println("Global: Redirected logging output to file '" + strFilename + "'");
      }

      // Old logging style:
      log.initialize(this);
   }

   /**
    * Add a new logging output channel. 
    * <pre>
    *   glob.addLogChannel(new LogChannel("cluster", glob.getProperty()));
    *   ...
    *   LogChannel log = glob.getLog("cluster");
    *   if (log.TRACE) log.trace("ClusterManager", "Problems with cluster node frodo");
    * </pre>
    * Start your application and switch on trace logging for classes using the "cluster" logging key:
    * <pre>
    *  java MyApp -trace[cluster] true
    * </pre>
    * @param log The channel must contain a none null channel key (here it is "cluster")
    * @return true if channel is accepted
    */
   public boolean addLogChannel(LogChannel log) {
      if (log == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("Global.addLogChannel(null)");
      }
      String key = log.getChannelKey();
      if (key != null && key.length() > 0) {
         initLog(log);
         logChannels.put(key, log);
         //log.info(ME, "New log channel '" + key + "' ready: " + LogChannel.bitToLogLevel(log.getLogLevel()));
         if (log.TRACE) log.trace(ME, "New log channel '" + key + "' ready: " + LogChannel.bitToLogLevel(log.getLogLevel()));
         return true;
      }
      return false;
   }

   /**
    * If the log channel for the given key is not known, a new channel is created. 
    * @param if null, the default log channel is returned
    * @see #addLogChannel(LogChannel)
    */
   public LogChannel getLog(String key) {
      if (key == null)
         return logDefault;
      Object obj = logChannels.get(key);
      if (obj != null)
         return (LogChannel)obj;

      LogChannel lc = new LogChannel(key, getProperty());
      addLogChannel(lc);
      return lc;
   }

   /**
   * Changes the given loglevel to given state. 
   * <p />
   * See org.jutils.init.Property#toBool(boolean) at www.jutils.org
   *
   * @param logLevel e.g. "trace" or "trace[core]"
   * @param bool A string like "true" or "false"
   * @return true/false to witch bool was parsed
   * @exception XmlBlasterException if your bool is strange
   */
   public boolean changeLogLevel(String logLevel, String bool) throws XmlBlasterException {
      try {
         boolean b = org.jutils.init.Property.toBool(bool);
         changeLogLevel(logLevel, b);
         return b;
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e.id, e.reason);
      }
   }

   /**
   * Changes the given loglevel to given state. 
   *
   * @param logLevel e.g. "trace" or "trace[core]"
   */
   public void changeLogLevel(String logLevel, boolean value) throws XmlBlasterException {
      if (logLevel == null || logLevel.length() < 1) return;

      try {
         int start = logLevel.indexOf("[");

         if (start != -1) { // Syntax is for example "info[core]"
            int end = logLevel.indexOf("]");
            if (start < 1 || end == -1 || end <= (start+1)) {
               throw new XmlBlasterException(ME, "Illegal loglevel syntax '" + logLevel + "'");
            }
            String key = logLevel.substring(start+1, end);
            Object obj = logChannels.get(key);
            if (obj == null)
               throw new XmlBlasterException(ME, "LogChannel '" + key + "' is not known");
            LogChannel log = (LogChannel)obj;
            if (value == true)
               log.addLogLevelChecked(logLevel.substring(0, start));
            else
               log.removeLogLevelChecked(logLevel.substring(0, start));
            return;
         }

         if (value == true) {
            logDefault.addLogLevelChecked(logLevel);
            Log.addLogLevel(logLevel); // deprecated
         }
         else {
            logDefault.removeLogLevelChecked(logLevel);
            Log.removeLogLevel(logLevel); // deprecated
         }

         for (Enumeration e = logChannels.elements(); e.hasMoreElements();) {
            LogChannel log = (LogChannel)e.nextElement();
            if (value == true) {
               log.info(ME, "Setting logLevel '" + logLevel + "' for '" + log.getChannelKey() + "' to true");
               log.addLogLevelChecked(logLevel);
            }
            else {
               log.info(ME, "Removing logLevel '" + logLevel + "' for '" + log.getChannelKey() + "'");
               log.removeLogLevelChecked(logLevel);
            }
         }
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e.id, e.reason);
      }
   }

   /**
   * Get the current loglevel. 
   *
   * @param @param logLevel e.g. "trace" or "trace[core]"
   * @return true is given log level is set, false otherwise.
   */
   public boolean getLogLevel(String logLevel) throws XmlBlasterException {
      if (logLevel == null || logLevel.length() < 1)
         throw new XmlBlasterException(ME, "Illegal loglevel syntax '" + logLevel + "'");

      try {
         int start = logLevel.indexOf("[");

         if (start != -1) { // Syntax is for example "info[core]"
            int end = logLevel.indexOf("]");
            if (start < 1 || end == -1 || end <= (start+1)) {
               throw new XmlBlasterException(ME, "Illegal loglevel syntax '" + logLevel + "'");
            }
            String key = logLevel.substring(start+1, end);
            Object obj = logChannels.get(key);
            if (obj == null)
               throw new XmlBlasterException(ME, "LogChannel '" + key + "' is not known");
            LogChannel log = (LogChannel)obj;
            return log.isLoglevelEnabled(logLevel.substring(0, start));
         }

         return logDefault.isLoglevelEnabled(logLevel);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(e.id, e.reason);
      }
   }

   /**
    * Access the logging class. 
    * @deprecated Use getLog(String) instead, e.g. <code>LogChannel log = glob.getLog(null);</code>
    */
   public final org.xmlBlaster.util.Log getLog() {
      return log;
   }

   /**
    * private, called from constructor
    * @return -1 on error
    */
   private int initProps(String[] args) {
      if (property == null) {
         synchronized (Property.class) {
            if (property == null) {
               try {
                  property = new Property("xmlBlaster.properties", true, args, true);
               }
               catch (JUtilsException e) {
                  errorText = ME + ": Error in xmlBlaster.properties: " + e.toString();
                  System.err.println(errorText);
                  try {
                     property = new Property(null, true, args, true);  // initialize without properties file!
                  }
                  catch (JUtilsException e2) {
                     errorText = ME + " ERROR: " + e2.toString();
                     System.err.println(errorText);
                  }
                  return -1;
               }
            }
         }
      }
      return 0;
   }

   /**
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      this.args = (args==null) ? new String[0] : args;

      try {
         property.addArgs2Props(this.args);
         
         initId();

         logDefault.initialize(property);
         // TODO: loop through logChannels Hashtable!!!

         // Old style:
         log.setLogLevel(property);   // Initialize logging as well.
         log.initialize(this);

         return property.wantsHelp() ? 1 : 0;
      } catch (JUtilsException e) {
         errorText = ME + " ERROR: " + e.toString();
         System.err.println(errorText); // Log probably not initialized yet.
         return -1;
      }
   }

   /**
    * Allows you to query if user wants help. 
    * @return true If '-help' or '-?' was passed to us
    */
   public final boolean wantsHelp() {
      return property.wantsHelp();
   }

   /**
    * @return If not null there was an error during construction / initialization
    */
   public String getErrorText() {
      return this.errorText;
   }

   /**
    * @return 1 Show usage, 0 OK
    */
   public int init(java.applet.Applet applet) {
      property.setApplet(applet);
      return property.wantsHelp() ? 1 : 0;
   }

   /**
    * Access the id (as a String) currently used on server side. 
    * @return ""
    */
   public String getId() {
      return id;
   }

   /**
    * Same as getId() but all slashes '/' are stripped
    * so you can use it for cluster node id (see requirement admin.command). 
    * @return ""
    */
   public String getAdminId() {
      return org.jutils.text.StringHelper.replaceAll(getId(), "/", "");
   }

   /**
    * Same as getId() but all 'special characters' are stripped
    * so you can use it for file names.
    * @return ""
    */
   public String getStrippedId() {
      String strippedId = org.jutils.text.StringHelper.replaceAll(getId(), "/", "");
      strippedId = org.jutils.text.StringHelper.replaceAll(strippedId, ".", "_");
      strippedId = org.jutils.text.StringHelper.replaceAll(strippedId, ":", "_");
      return org.jutils.text.StringHelper.replaceAll(strippedId, "\\", "");
   }

   /**
    * Currently set by enging.Global, used server side only. 
    * @param a unique id
    */
   public void setId(String id) {
      this.id = id;
   }

   /**
    * Is coded in derived engine.Global
    */
   public String getLogPraefixDashed() {
      return "";
   }

   /**
    * Is coded in derived engine.Global
    */
   public String getLogPraefix() {
      return "";
   }

   /**
    * Global access to the default 'global' instance. 
    * If you have parameters (e.g. from the main() mehtod) you should
    * initialize Global first before using instance():
    * <pre>
    *    public static void main(String[] args) {
    *       new Global(args);
    *       ...
    *    }
    *
    *    //later you can get this initialized instance with:
    *    Global glob = Global.instance();   
    *    ...
    * </pre>
    */
   public static Global instance() {
      if (firstInstance == null) {
         synchronized (Global.class) {
            if (firstInstance == null)
               new Global();
         }
      }
      return firstInstance;
   }

   /**
    * Get a cloned instance. 
    * Note that instance() will return the original instance
    * even if called on the cloned object (it's a static variable).
    */
   public final Global getClone(String[] args) {
      Global g = (Global)clone();
      if (args != null && args.length > 0)
         g.init(args);
      return g;
   }

   /**
    * Get a clone, it is a mixture between shallow and deep copy. 
    * <p />
    * All immutable elements are a shallow clone.<br />
    * The properties and log channels are copied with a deep copy
    * manipulating these will not affect the original Global.<br />
    * All other attributes are initialized as on startup.
    */
   protected Object clone() {
      try {
         Global g = (Global)super.clone();
         g.errorText = null;
         g.property = (Property)this.property.clone();
         //g.logDefault = 
         g.logChannels = (Hashtable)this.logChannels.clone();
         g.nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap()); // (HashMap)((HashMap)this.nativeCallbackDriverMap).clone();
         g.objectMap = Collections.synchronizedMap(new HashMap());
         g.bootstrapAddress = null;
         g.clientSecurityLoader = null;
         g.recorderPluginManager = null;
         if (g.id != id)
            log.error(ME, "g.id=" + g.id + " and id=" + id);
         return g;
      }
      catch (CloneNotSupportedException e) {
         logDefault.error(ME, "Global clone failed: " + e.toString());
         return null;
      }
   }

   /**
    * Access the environment properties. 
    */
   public final Property getProperty() {
      return property;
   }

   /**
    * The command line arguments. 
    * @return the arguments, is never null
    */
   public final String[] getArgs()
   {
      return this.args;
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @return The instance of the protocol callback driver or null if not known
    */
   public final I_CallbackDriver getNativeCallbackDriver(String key)
   {
      return (I_CallbackDriver)nativeCallbackDriverMap.get(key);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addNativeCallbackDriver(String key, I_CallbackDriver driver)
   {
      nativeCallbackDriverMap.put(key, driver);
   }

   /**
    * The key is the protocol and the address to access the callback instance. 
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void removeNativeCallbackDriver(String key)
   {
      nativeCallbackDriverMap.remove(key);
   }

   /**
    * Get an object in the scope of an XmlBlasterConnection or of one cluster node. 
    * <p />
    * This is helpful if you have more than one XmlBlasterConnection or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @return The instance of this object
    */
   public final Object getObjectEntry(String key)
   {
      return objectMap.get(key);
   }

   /**
    * Add an object in the scope of an XmlBlasterConnection or of one cluster node. 
    * <p />
    * This is helpful if you have more than one XmlBlasterConnection or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addObjectEntry(String key, Object driver)
   {
      objectMap.put(key, driver);
   }

   /**
    * Remove an object from the scope of an XmlBlasterConnection or of one cluster node. 
    * <p />
    * This is helpful if you have more than one XmlBlasterConnection or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    */
   public final void removeObjectEntry(String key)
   {
      objectMap.remove(key);
   }

   /**
    * Force to use the given bootstrap address, used for cluster connections
    */
   public final void setBootstrapAddress(Address address) {
      this.bootstrapAddress = address;
   }

   /**
    * Returns the address of the xmlBlaster internal http server. 
    * <p />
    * Is configurable with
    * <pre>
    *   -hostname myhost.mycompany.com   (or the raw IP)
    *   -port 3412
    * </pre>
    * Defaults to the local machine and the IANA xmlBlaster port.<br />
    * You can set "-port 0" to avoid starting the internal HTTP server
    */
   public final Address getBootstrapAddress() {
      if (bootstrapAddress == null) {
         if (log.CALL) log.call(ME, "Entering getBootstrapAddress(), trying to resolve one ...");
         bootstrapAddress = new Address(this);
         boolean supportOldStyle = true; // for a while we support the old style -iorHost and -iorPort
         if (supportOldStyle) {
            String iorHost = getProperty().get("iorHost", getLocalIP());
            int iorPort = getProperty().get("iorPort", Constants.XMLBLASTER_PORT);
            bootstrapAddress.setHostname(getProperty().get("hostname", iorHost));
            bootstrapAddress.setPort(getProperty().get("port", iorPort));
         }
         else {
            bootstrapAddress.setHostname(getProperty().get("hostname", getLocalIP()));
            bootstrapAddress.setPort(getProperty().get("port", Constants.XMLBLASTER_PORT));
         }
         bootstrapAddress.setAddress("http://" + bootstrapAddress.getHostname() + ":" + bootstrapAddress.getPort());
         if (log.TRACE) log.trace(ME, "Initialized bootstrapAddress to host=" + bootstrapAddress.getHostname() +
                        " port=" + bootstrapAddress.getPort() + ": " + bootstrapAddress.getAddress());
      }
      return bootstrapAddress;
   }

   /**
    * Returns a local IP or hostname to use. 
    * <p />
    * The precedence of finding the callback hostname is as
    * described in getCbHostname() but if the given param is found
    * as a property this has precedence.
    * @return The bootstrap callback hostname, is never null
    */
   public String getCbHostname(String param) {
      return getProperty().get(param, getCbHostname());
   }

   /**
    * Returns a local IP or hostname as a default setting to use for callback servers. 
    * <p />
    * It is determined by doing a short connect to the xmlBlaster HTTP server
    * an reading the used local hostname.
    * The precedence of finding the callback hostname is:
    * <ol>
    *  <li>Evaluate the -hostnameCB property</li>
    *  <li>Try to determine it by a temporary connection to the xmlBlaster bootstrap server and reading the used local IP</li>
    *  <li>Use default IP of this host</li>
    * </ol>
    * @return The default IP, is never null
    */
   public String getCbHostname() {
      if (this.cbHostname == null) {
         try {
            Address addr = getBootstrapAddress();
            Socket sock = new Socket(addr.getHostname(), addr.getPort());
            this.cbHostname = sock.getLocalAddress().getHostAddress();
            sock.close();
            sock = null;
            if (log.TRACE) log.trace(ME, "Default cb host is " + this.cbHostname);
         }
         catch (java.io.IOException e) {
            log.trace(ME, "Can't find default cb hostname: " + e.toString());
            this.cbHostname = getLocalIP();
         }
         this.cbHostname = getProperty().get("hostnameCB", this.cbHostname);
      }
      return this.cbHostname;
   }

   /**
    * Access the xmlBlaster internal HTTP server and download the requested path. 
    * <p />
    * Currently we only use it for CORBA IOR download. To avoid the name service,
    * one can access the AuthServer IOR directly
    * using a http connection.
    *
    * @param address The address we want to connect to or null
    * @param urlPath The part after the host:port, from an URL "http://myhost.com:3412/AuthenticationService.ior"
    *                urlPath is "AuthenticationService.ior"
    * @param false Suppress error logging when server not found
    */
   public String accessFromInternalHttpServer(Address address, String urlPath, boolean verbose) throws XmlBlasterException
   {
      if (logDefault.CALL) logDefault.call(ME, "Entering accessFromInternalHttpServer(" + ((address==null)?"null":address.getAddress()) + ") ...");
      Address addr = address;
      if (addr != null && addr.getPort() > 0) {
         if (addr.getHostname() == null || addr.getHostname().length() < 1) {
            addr.setHostname(getLocalIP());
         }
      }
      else {
         addr = getBootstrapAddress();
      }

      if (logDefault.TRACE) logDefault.trace(ME, "Trying internal http server on " + addr.getHostname() + ":" + addr.getPort());
      try {
         if (urlPath != null && urlPath.startsWith("/") == false)
            urlPath = "/" + urlPath;

         java.net.URL nsURL = new java.net.URL("http", addr.getHostname(), addr.getPort(), urlPath);
         java.io.InputStream nsis = nsURL.openStream();
         byte[] bytes = new byte[4096];
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
         int numbytes;
         for (int ii=0; ii<20 && (nsis.available() <= 0); ii++) {
            if (logDefault.TRACE) logDefault.trace(ME, "XmlBlaster on host " + addr.getHostname() + " and port " + addr.getPort() + " returns empty data, trying again after sleeping 10 milli ...");
            org.jutils.runtime.Sleeper.sleep(10); // On heavy logins, sometimes available() returns 0, but after sleeping it is OK
         }
         while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
            bos.write(bytes, 0, numbytes);
         }
         nsis.close();
         String data = bos.toString();
         if (logDefault.TRACE) logDefault.trace(ME, "Retrieved http data='" + data + "'");
         return data;
      }
      catch(MalformedURLException e) {
         String text = "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ".";
         logDefault.error(ME, text + e.toString());
         throw new XmlBlasterException(ME+"NoHttpServer", text);
      }
      catch(IOException e) {
         if (verbose) logDefault.warn(ME, "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ": " + e.toString());
         throw new XmlBlasterException(ME+"NoHttpServer", "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ".");
      }
   }

   /**
    * The IP address where we are running. 
    * <p />
    * You can specify the local IP address with e.g. -hostname 192.168.10.1
    * on command line, useful for multi-homed hosts.
    *
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public final String getLocalIP()
   {
      if (ip_addr == null) {
         ip_addr = getBootstrapAddress().getHostname();
         if (ip_addr == null || ip_addr.length() < 1) {
            try {
               ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
            } catch (java.net.UnknownHostException e) {
               logDefault.warn(ME, "Can't determine local IP address, try e.g. '-hostname 192.168.10.1' on command line: " + e.toString());
            }
            if (ip_addr == null) ip_addr = "127.0.0.1";
         }
      }
      return ip_addr;
   }

   /**
    * Needed by java client helper classes to load
    * the security plugin
    */
   public PluginLoader getClientSecurityPluginLoader() {
      synchronized (PluginLoader.class) {
         if (clientSecurityLoader == null)
            clientSecurityLoader = new PluginLoader(this);
      }
      return clientSecurityLoader;
   }

   /**
    * Needed by java client helper classes to load
    * the tail back queuing mechanism (invocation recorder). 
    */
   public RecorderPluginManager getRecorderPluginManager() {
      synchronized (RecorderPluginManager.class) {
         if (recorderPluginManager == null)
            recorderPluginManager = new RecorderPluginManager(this);
      }
      return recorderPluginManager;
   }

   /**
    * Access the http server which allows bootstrapping the CORBA IOR
    */
   public final HttpIORServer getHttpServer() throws XmlBlasterException {
      if (this.httpServer == null) {
         synchronized(this) {
            if (this.httpServer == null)
               this.httpServer = new HttpIORServer(this, getBootstrapAddress().getHostname(), getBootstrapAddress().getPort());
         }
      }
      return this.httpServer;
   }

   public synchronized final void shutdownHttpServer() {
      try {
         if (httpServer != null) httpServer.shutdown();
         httpServer = null;
      }
      catch (Throwable e) {
         log.warn(ME, "Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Command line usage.
    * <p />
    * These variables may be set in your property file as well.
    * Don't use the "-" prefix there.
    * <p />
    * Set the verbosity when loading properties (outputs with System.out).
    * <p />
    * 0=nothing, 1=info, 2=trace, configure with
    * <pre>
    * java -Dproperty.verbose 2 ...
    *
    * java org.xmlBlaster.Main -property.verbose 2
    * </pre>
    */
   public String usage()
   {
      StringBuffer sb = new StringBuffer(512);
      sb.append("Logging options:\n");
      sb.append("   -info  false        Supress info output.\n");
      sb.append("   -trace true         Show code trace.\n");
      sb.append("   -dump  true         Dump internal state.\n");
      sb.append("   -call  true         Show important method entries\n");
      sb.append("   -time true          Display some performance data.\n");
      sb.append("   -logFile <fileName> Log to given file.\n");
      sb.append("   -logConsole false   Supress logging to console.\n");
      sb.append("\n");
      sb.append("Control logging of properties framework:\n");
      sb.append("   -property.verbose   0 switches logging off, 2 is most verbose when loading properties on startup [" + Property.DEFAULT_VERBOSE + "].\n");
      return sb.toString();
   }
}
