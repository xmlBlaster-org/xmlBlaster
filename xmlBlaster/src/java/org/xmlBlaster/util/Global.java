/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.jutils.JUtilsException;
import org.jutils.init.Property;
import org.jutils.text.StringHelper;
import org.jutils.log.LogChannel;
import org.jutils.log.LogDeviceConsole;
import org.jutils.log.LogDeviceFile;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.util.key.I_MsgKeyFactory;
import org.xmlBlaster.util.key.MsgKeySaxFactory;
import org.xmlBlaster.util.key.I_QueryKeyFactory;
import org.xmlBlaster.util.key.QueryKeySaxFactory;
import org.xmlBlaster.util.qos.I_ConnectQosFactory;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.I_DisconnectQosFactory;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;
import org.xmlBlaster.util.qos.I_MsgQosFactory;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.I_StatusQosFactory;
import org.xmlBlaster.util.qos.StatusQosQuickParseFactory;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.StandaloneClassLoaderFactory;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.dispatch.plugins.DispatchPluginManager;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchWorkerPool;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.client.dispatch.ClientDispatchConnectionsHandler;
import org.xmlBlaster.client.protocol.ProtocolPluginManager;
import org.xmlBlaster.client.protocol.CbServerPluginManager;
import org.xmlBlaster.authentication.HttpIORServer;
import org.xmlBlaster.util.log.LogDevicePluginManager;
import org.xmlBlaster.util.log.I_LogDeviceFactory;
import org.jutils.log.LogableDevice;

import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginRegistry;
import org.xmlBlaster.client.queuemsg.ClientEntryFactory;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;

import java.util.Properties;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.net.MalformedURLException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Hashtable;
import java.util.Enumeration;

import java.net.Socket;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.xmlBlaster.util.admin.extern.JmxWrapper;
import javax.management.ObjectName;

/**
 * Global variables to avoid singleton. 
 * <p>
 * Each Global instance holds all configuration and helper objects for
 * exactly one XmlBlasterAccess client instance. It is like a local
 * stack for a connection.
 * </p>
 * <p>
 * Use one instance of this for each XmlBlasterAccess client connection to xmlBlaster.
 * </p>
 * <p>
 * A Global instance is still usable after a call to its shutdown() method,
 * it can be used again in another XmlBlasterAccess instance.
 * </p>
 *
 * @see org.xmlBlaster.test.classtest.GlobalTest
 */
public class Global implements Cloneable
{
   private static Global firstInstance;

   /** Version string, please change for new releases (4 digits) */
   private String versionDefault = "1.0.4";
   /** This will be replaced by build.xml with the current version */
   private String version = "@version@";
   /** This will be replaced by build.xml with the current subversion revision number */
   private String revisionNumber = "@revision.number@";
   /** This will be replaced by build.xml with the build timestamp */
   private String buildTimestamp = "@build.timestamp@";
   /** This will be replaced by build.xml with the compiling JDK vendor */
   private String buildJavaVendor = "@build.java.vendor@";
   /** This will be replaced by build.xml with the compiling JDK version */
   private String buildJavaVersion = "@build.java.version@";

   protected String ME = "Global";
   protected String ip_addr = null;
   protected String id = "";

   protected Property property = null;
   protected String errorText = null;

   protected ContextNode contextNode;

   protected String cbHostname = null;

   protected String addressNormalized = null;

   // deprecated
   //protected org.xmlBlaster.util.Log log;
   protected final LogChannel log;

   /** The xmlBlaster class loader factory */
   private ClassLoaderFactory classLoaderFactory = null;

   protected /*final*/ Map nativeCallbackDriverMap;
   /** Store objects in the scope of one client connection or server instance */
   protected /*final*/ Map objectMap;
   protected Address bootstrapAddress;
   protected PluginLoader clientSecurityLoader;

   protected QueuePluginManager queuePluginManager;

   protected DispatchPluginManager dispatchPluginManager;

   protected ProtocolPluginManager protocolPluginManager;
   protected CbServerPluginManager cbServerPluginManager;

   private HttpIORServer httpServer;  // xmlBlaster publishes his AuthServer IOR

   protected XmlProcessor xmlProcessor;

   protected Hashtable logChannels = new Hashtable();
   protected LogChannel logDefault;

   protected SAXParserFactory saxFactory;
   protected DocumentBuilderFactory docBuilderFactory;
   protected TransformerFactory transformerFactory;

   protected I_MsgKeyFactory msgKeyFactory;
   protected I_QueryKeyFactory queryKeyFactory;
   protected I_ConnectQosFactory connectQosFactory;
   protected I_DisconnectQosFactory disconnectQosFactory;
   protected I_MsgQosFactory msgQosFactory;
   protected I_QueryQosFactory queryQosFactory;
   protected I_StatusQosFactory statusQosFactory;

   protected Timeout pingTimer;
   protected Timeout burstModeTimer;
   protected Timeout messageTimer;
   protected Timeout jdbcConnectionPoolTimer;
   protected DispatchWorkerPool dispatchWorkerPool;

   protected LogDevicePluginManager logDevicePluginManager = null;
   /** used to guard agains log device plugin loading making cirkular calls*/
   private boolean creatingLogInstance = false;

   protected static int counter = 0;

   private PluginManagerBase pluginManager;
   private PluginRegistry pluginRegistry;

   /** The client handle to access xmlBlaster */
   protected I_XmlBlasterAccess xmlBlasterAccess;

   protected boolean isDoingShutdown = false;
   
   /** set to allow wipe out the persistence on restarts */
   protected boolean wipeOutDB = false;
   
   //** the entry factory to be used */
   protected I_EntryFactory entryFactory;

   /** Support for JMX access */
   private JmxWrapper jmxWrapper;

   /**
    * Constructs an initial Global object,
    * same as Global(null, true, true)
    */
   public Global() {
      this(null, true, false);
   }

   /**
    * Constructs an initial Global object which is initialized
    * by your properties,
    * same as Global(args, true, true)
    */
   public Global(Properties props) {
      this(Property.propsToArgs(props));
   }
   /**
    * Constructs an initial Global object which is initialized
    * by your args array (usually the command line args).
    * Same as Global(args, true, true)
    */
   public Global(String[] args)
   {
      this(args, true, false);
   }

   /**
    * Constructs an initial Global object which is initialized
    * by your args array (usually the command line args).
    *
    * <p>By setting loadPropFile to false it is possible to create a Global
    * which does not automatically search out the xmlBlaster.properties file,
    * which is good when you want to start xmlBlaster in an embedded environment.
    * <p>It is possible to later load the property file if one wants, here is one 
    * way to do it:</p>
    * <pre>
         Property p = glob.getProperty();
         Properties prop = new Properties();
         FileInfo i = p.findPath("xmlBlaster.properties");
         InputStream is = i.getInputStream();
         prop.load(is);
         String[] ar = Property.propsToArgs(prop);
         p.addArgs2Props( ar != null ? ar : new String[0] );
       </pre>
     <p>It is also possible to load an entire second property file or find it
        with some other algorithm byte using the same pattern as above, just
        don't use findPath, but some other code.</p>
    * @param args args array (usually the command line args).
    * @param loadPropFile if automatic loading of xmlBlaster.properties should be done.
    */
   public Global(String[] args, boolean loadPropFile, boolean checkInstance)
   {
      counter++;
      if (checkInstance == true) {
         if (firstInstance != null) {
            System.out.println("######Global args constructor invoked again, try Global.instance()");
            Thread.dumpStack();
         }
      }
      synchronized (Global.class) {
         if (firstInstance == null)
            firstInstance = this;
      }
      initProps(args,loadPropFile);
      initId();
      logDevicePluginManager = new LogDevicePluginManager(this);
      logDefault = new LogChannel(null, getProperty());
      log = logDefault;
      //log = new org.xmlBlaster.util.Log(); // old style
      initLog(logDefault);
      nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());
      objectMap = Collections.synchronizedMap(new HashMap());
   }

   /**
    * @return the JmxWrapper used to manage the MBean resources
    */
    public final JmxWrapper getJmxWrapper() throws XmlBlasterException {
      if (this.jmxWrapper == null) {
         synchronized (this) {
            if (this.jmxWrapper == null) {
               this.jmxWrapper = new JmxWrapper(this);
            }
         }
      }
      return this.jmxWrapper;
   }

   /**
    * JMX support. 
    * Start xmlBlaster with <code>java -Dcom.sun.management.jmxremote org.xmlBlaster.Main</code>
    * You can access xmlBlaster from 'jconsole' delivered with JDK1.5 or above.
    * The root node is always the cluster node id.
    * @param name the instance for example "client/joe/-1"
    * @param mbean the MBean object instance 
    * @return The object name used to register or null on error
    * @since 1.0.4
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.jmx.html
    */
   public ObjectName registerMBean(String name, Object mbean) throws XmlBlasterException {
      return getJmxWrapper().registerMBean(name, mbean);
   }

   /**
    * Unregister a JMX MBean. 
    * @param objectName The object you got from registerMBean() of type ObjectName,
    *                   if null nothing happens
    */
   public void unregisterMBean(Object objectName) {
      if (this.jmxWrapper != null)
         this.jmxWrapper.unregisterMBean(objectName);
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
    * See @revision.number@ which will be replaced by build.xml with the current subversion revision
    * @return e.g. "12702" or "12702M". If no subversion is available getVersion() is returned
    */
   public String getRevisionNumber() {
      if (this.revisionNumber.indexOf("@") == -1 && !"${revision.number}".equals(this.revisionNumber)) // Check if replaced
         return this.revisionNumber;
      return versionDefault;
   }

   /**
    * Combination from getVersion() and getRevisionNumber(). 
    * @return e.g. "0.91 #12702"
    */
   public String getReleaseId() {
      if (!getVersion().equals(getRevisionNumber()))
         return getVersion() + " #" + getRevisionNumber();
      return getVersion();
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
    * Blocks until a key on the keyboard is hit. 
    * Consumes multiple hits (for Windows DOS box)
    * @param str If not null it will be printed on console with System.out
    * @return The int pressed (for example 49 for '1')
    */
   public static int waitOnKeyboardHit(String str) {
      if (str != null) {
         System.out.println(str);
      }
      int ret = 0;
      try {
         ret = System.in.read();
         int num = System.in.available();
         for(int i=0; i<num; i++)
            System.in.read();
      } catch(java.io.IOException e) {}
      return ret;
   }

   /**
    * Our identifier, the cluster node we want connect to
    */
   protected void initId() {
      this.id = getProperty().get("server.node.id", (String)null);
      if (this.id == null)
         this.id = getProperty().get("cluster.node.id", "xmlBlaster");  // fallback
      if (this.id == null && getBootstrapAddress().getBootstrapPort() > 0) {
         this.id = getBootstrapAddress().getBootstrapHostname() + ":" + getBootstrapAddress().getBootstrapPort();
      }
   }

   protected void shallowCopy(org.xmlBlaster.util.Global utilGlob)
   {
      this.ip_addr = utilGlob.ip_addr;
      this.id = utilGlob.id;
      this.property = utilGlob.property;
      this.errorText = utilGlob.errorText;
      this.cbHostname =  utilGlob.cbHostname;
      this.nativeCallbackDriverMap = utilGlob.nativeCallbackDriverMap;
      this.objectMap = utilGlob.objectMap;
      this.bootstrapAddress = utilGlob.bootstrapAddress;
      this.clientSecurityLoader = utilGlob.clientSecurityLoader;
      this.logChannels = utilGlob.logChannels;
      this.logDefault = utilGlob.logDefault;
      this.logDevicePluginManager = utilGlob.logDevicePluginManager;
   }

   /**
    * Initialize logging.
    *
    * <p>The pluggable loggers is tested first, see {@link org.xmlBlaster.util.log.LogDevicePluginManager}.
    * <p>If no pluggable loggers is configured the logging is initialized
    * with environment variables.</p>
    * <pre>
    *   -logFile  output.txt
    *   -logFile[cluster] cluster-output.txt
    *   -logConsole false
    *   -logConsole[cluster] true
    * </pre>
    */
   private void initLog(LogChannel lc) {
      String key = lc.getChannelKey();
      boolean useOld = true;//Used to guaranty some logging

      // There are situations where the manager is actually not there because
      // its parent sets up logging
      if (logDevicePluginManager != null) {
         // We have to protect this part, if the plugin bootstrapping makes us
         // call this stuff
         synchronized(this) {
            if (creatingLogInstance) {
               // We simply use an oldtimer ;-)
               lc.addLogDevice(new LogDeviceConsole(lc) );
               return;// Get out if here quick!
            }
            creatingLogInstance=true;

            // Get the plugins for the lc.key, first try with key, then global
            String[] devices = null;
            if (key != null)
               devices = getProperty().get("logDevice[" + key + "]", new String[0], ",");
            if (devices == null  ||  devices.length == 0)
               devices = getProperty().get("logDevice", new String[0], ",");

            if (devices != null && devices.length > 0) {
               for(int i = 0;i<devices.length;i++) {
                  try {
                     I_LogDeviceFactory fac = logDevicePluginManager.getFactory(devices[i],"1.0");
                     LogableDevice dev = fac.getLogDevice(lc);
                     if (log != null && log.TRACE) log.trace(ME,"Setting logDevice " +key+"[" + devices[i]+"]="+((dev!=null && dev.getClass()!=null)?dev.getClass().getName():"null"));
                     if (dev != null)
                        lc.addLogDevice(dev);
                  }catch(XmlBlasterException ex) {
                     if (log != null)
                        log.error(ME,"initLog(): Error in getting LogDeviceFactory for " + key);
                     else
                        System.out.println(ME+".initLog(): Error in getting LogDeviceFactory for " + key);
                     continue;
                  }
                  //If we ever reach here, we have some logging device set up
                  useOld=false;
               }


            }
            creatingLogInstance = false;
         }
      }

      if (useOld) {
         if (log.TRACE) log.trace(ME,"Using old logging behaviour for '" + key + "'");
         //System.err.println("Using old logging behaviour");
         //Old behaviour
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
      }

      //lc.setDefaultLogLevel();



      // Old logging style:
      //log.initialize(this);
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
         Thread.dumpStack();
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
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_UNKNOWN, ME, "changeLogLevel failed", e);
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
               throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "Illegal loglevel syntax '" + logLevel + "'");
            }
            String key = logLevel.substring(start+1, end);
            Object obj = logChannels.get(key);
            if (obj == null)
               throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "LogChannel '" + key + "' is not known");
            LogChannel log = (LogChannel)obj;
            if (value == true)
               log.addLogLevelChecked(logLevel.substring(0, start));
            else
               log.removeLogLevelChecked(logLevel.substring(0, start));
            return;
         }

         if (value == true) {
            logDefault.addLogLevelChecked(logLevel);
            //Log.addLogLevel(logLevel); // deprecated
         }
         else {
            logDefault.removeLogLevelChecked(logLevel);
            //Log.removeLogLevel(logLevel); // deprecated
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
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_UNKNOWN, ME, "", e);
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
         throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "Illegal loglevel syntax '" + logLevel + "'");

      try {
         int start = logLevel.indexOf("[");

         if (start != -1) { // Syntax is for example "info[core]"
            int end = logLevel.indexOf("]");
            if (start < 1 || end == -1 || end <= (start+1)) {
               throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "Illegal loglevel syntax '" + logLevel + "'");
            }
            String key = logLevel.substring(start+1, end);
            Object obj = logChannels.get(key);
            if (obj == null)
               throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "LogChannel '" + key + "' is not known");
            LogChannel log = (LogChannel)obj;
            return log.isLoglevelEnabled(logLevel.substring(0, start));
         }

         return logDefault.isLoglevelEnabled(logLevel);
      }
      catch (JUtilsException e) {
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_UNKNOWN, ME, "", e);
      }
   }

   /**
    * private, called from constructor
    * @return -1 on error
    */
   private int initProps(String[] args) {
      return initProps(args,true);
   }

   /**
    * private, called from constructor
    * @param args arguments to initilize the property with.
    * @param loadPropFile if loading of xmlBlaster.properties 
    *        file should be done, if false no loading of the file is done.
    * @return -1 on error
    * @exception If no Property instance can be created
    */
   private int initProps(String[] args, boolean loadPropFile) {
      if (property == null) {
         synchronized (Property.class) {
            if (property == null) {
               try {
                  if (loadPropFile)
                     property = new Property("xmlBlaster.properties", true, args, true);
                  else
                     property = new Property(null, true, args, true);
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
                     try {
                        property = new Property(null, true, new String[0], true);  // initialize without args
                     }
                     catch (JUtilsException e3) {
                        errorText = ME + " ERROR: " + e3.toString();
                        System.err.println(errorText);
                        e3.printStackTrace();
                        throw new IllegalArgumentException("Can't create Property instance: " + errorText);
                     }
                  }
                  return -1;
               }
            }
         }
      }
      return 0;
   }

   /**
    * Calls init(String[] args), the props keys have no leading "-". 
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(Properties props) {
      return init(Property.propsToArgs(props));
   }

   /**
    * The args key needs a leading "-". 
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      args = (args==null) ? new String[0] : args;

      if (args.length > 0) {
         this.bootstrapAddress = null;   // clear cached address
         // shutdownHttpServer(); Should be done as well if address changes?
      }

      try {
         property.addArgs2Props(args);

         initId();

         logDefault.initialize(property);
         // TODO: loop through logChannels Hashtable!!!

         // Old style:
         //log.setLogLevel(property);   // Initialize logging as well.
         //log.initialize(this);

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
    * The unique name of this xmlBlaster server instance.
    * @return Can be null during startup
    */
   public final ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * @return false
    */
   public boolean isServerSide() {
      return false;
   }

   /**
    * @return null on client side
    */
   public NodeId getNodeId() {
      return null;
   }

   /**
    * Access the unique local id (as a String), 
    * on client side typically the loginName with the public sessionId,
    * on server side the server instance unique id.
    * @return ""
    */
   public String getId() {
      return this.id;
   }

   /**
    * Same as getId() but all 'special characters' are stripped
    * so you can use it for file names.
    * @return ""
    */
   public String getStrippedId() {
      return getStrippedString(getId());
   }

   /**
    * Utility method to strip any string, all characters which prevent
    * to be used for e.g. file names are replaced.
    * <p>
    * This conversion is used for file names and for the administrative
    * hierarchy e.g. "/node/heron/client/joe" is OK but 'http://xy:8080' instead of 'heron' is not
    * </p>
    * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
    * @return e.g. "http_www_xmlBlaster_org_homex"
    */
   public static final String getStrippedString(String text) {
      String strippedId = StringHelper.replaceAll(text, "/", "");
      strippedId = StringHelper.replaceAll(strippedId, " ", "_");
      strippedId = StringHelper.replaceAll(strippedId, ".", "_");
      strippedId = StringHelper.replaceAll(strippedId, ":", "_");
      strippedId = StringHelper.replaceAll(strippedId, "[", "_");
      strippedId = StringHelper.replaceAll(strippedId, "]", "_");
      return StringHelper.replaceAll(strippedId, "\\", "");
   }

   /**
    * Currently set by enging.Global, used server side only.
    * @param a unique id
    */
   public void setId(String id) {
      this.id = id;
      if (this.contextNode == null) {
         this.contextNode = new ContextNode(this, ContextNode.CLUSTER_MARKER_TAG, getStrippedId(), ContextNode.ROOT_NODE);
      }
      else {
         this.contextNode.setInstanceName(getStrippedId());
      }
   }

   /**
    * Is coded in derived engine.Global
    */
   public String getLogPrefixDashed() {
      return "";
   }

   /**
    * Is coded in derived engine.Global
    */
   public String getLogPrefix() {
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
    * <p>
    * Note that you should avoid to use Global.instance() and preferably
    * use the global which describes your current context, e.g. the specific
    * client connection like xmlBlasterAccess.getGlobal().
    * Use global.getClone(String[]) to create a new Global instance.
    * </p>
    */
   public static Global instance() {
      if (firstInstance == null) {
         synchronized (Global.class) {
            if (firstInstance == null)
               new Global();
         }
      }
      //System.out.println("Accessing Global.instance()");
      //Thread.currentThread().dumpStack();
      return firstInstance;
   }

   /**
    * Get a cloned instance. 
    * <p>
    * Calls clone() and sets the given args thereafter.
    * </p>
    * <p>
    * This is the preferred way to create a new and independent
    * Global instance for example for another client connection.
    * </p>
    * <p>
    * Note that Global.instance() will return the original instance
    * even if called on the cloned object (it's a static variable).
    * You should avoid to use Global.instance()
    * </p>
    *
    * @param args Additional configuration parameters
    */
   public final Global getClone(String[] args) {
      Global g = (Global)clone();
      if (args != null && args.length > 0)
         g.init(args);
      return g;
   }

   /**
    * Get a deep clone (everything is independent from the origin).
    * <p />
    * The properties and log channels are copied with a deep copy
    * manipulating these will not affect the original Global.<br />
    * All other attributes are initialized as on startup.
    */
   protected Object clone() {
      return new Global(Property.propsToArgs(this.property.getProperties()), false, false);
   }

   /**
    * Access the environment properties.
    */
   public final Property getProperty() {
      return property;
   }

   /**
    * Return a factory parsing key XML strings from publish() and update() messages.
    */
   public final I_MsgKeyFactory getMsgKeyFactory() {
      if (this.msgKeyFactory == null) {
         synchronized (this) {
            if (this.msgKeyFactory == null) {
               this.msgKeyFactory = new MsgKeySaxFactory(this);
            }
         }
      }
      return this.msgKeyFactory;
   }

   /**
    * Return a factory parsing key XML strings from subscribe() and other query invocations.
    */
   public final I_QueryKeyFactory getQueryKeyFactory() {
      if (this.queryKeyFactory == null) {
         synchronized (this) {
            if (this.queryKeyFactory == null) {
               this.queryKeyFactory = new QueryKeySaxFactory(this);
            }
         }
      }
      return this.queryKeyFactory;
   }

   /**
    * Return a factory parsing QoS XML strings from connect() and connect-return messages.
    */
   public final I_ConnectQosFactory getConnectQosFactory() {
      if (this.connectQosFactory == null) {
         synchronized (this) {
            if (this.connectQosFactory == null) {
               this.connectQosFactory = new ConnectQosSaxFactory(this);
            }
         }
      }
      return this.connectQosFactory;
   }

   /**
    * Return a factory parsing QoS XML strings from disconnect() requests. 
    */
   public final I_DisconnectQosFactory getDisconnectQosFactory() {
      if (this.disconnectQosFactory == null) {
         synchronized (this) {
            if (this.disconnectQosFactory == null) {
               this.disconnectQosFactory = new DisconnectQosSaxFactory(this);
            }
         }
      }
      return this.disconnectQosFactory;
   }

   /**
    * Return a factory parsing QoS XML strings from publish() and update() messages.
    */
   public final I_MsgQosFactory getMsgQosFactory() {
      if (this.msgQosFactory == null) {
         synchronized (this) {
            if (this.msgQosFactory == null) {
               this.msgQosFactory = new MsgQosSaxFactory(this);
            }
         }
      }
      return this.msgQosFactory;
   }

   /**
    * Return a factory parsing QoS XML strings from publish() and update() messages.
    */
   public final I_QueryQosFactory getQueryQosFactory() {
      if (this.queryQosFactory == null) {
         synchronized (this) {
            if (this.queryQosFactory == null) {
               this.queryQosFactory = new QueryQosSaxFactory(this);
            }
         }
      }
      return this.queryQosFactory;
   }

   /**
    * Return a factory parsing QoS XML strings from subcribe(), unSubscribe() and erase() returns.
    */
   public final I_StatusQosFactory getStatusQosFactory() {
      if (this.statusQosFactory == null) {
         synchronized (this) {
            if (this.statusQosFactory == null) {
               //this.statusQosFactory = new StatusQosSaxFactory(this);
               this.statusQosFactory = new StatusQosQuickParseFactory(this);
            }
         }
      }
      return this.statusQosFactory;
   }

   /**
    * The key is the protocol and the address to access the callback instance.
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getRawAddress()'
    * @return The instance of the protocol callback driver or null if not known
    */
   public final I_CallbackDriver getNativeCallbackDriver(String key)
   {
      if (log.CALL) log.call(ME, "getNativeCallbackDriver(" + key + ")");
      return (I_CallbackDriver)nativeCallbackDriverMap.get(key);
   }

   /**
    * The key is the protocol and the address to access the callback instance.
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getRawAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addNativeCallbackDriver(String key, I_CallbackDriver driver)
   {
      if (log.CALL) log.call(ME, "addNativeCallbackDriver(" + key + "," + driver.getName() + ")");
      nativeCallbackDriverMap.put(key, driver);
   }

   /**
    * The key is the protocol and the address to access the callback instance.
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getRawAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void removeNativeCallbackDriver(String key)
   {
      if (log.CALL) log.call(ME, "removeNativeCallbackDriver(" + key + ")");
      nativeCallbackDriverMap.remove(key);
   }

   /**
    * Get an object in the scope of an xmlBlaster client connection or of one cluster node.
    * <p />
    * This is helpful if you have more than one I_XmlBlasterAccess or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. <i>"SOCKET192.168.2.2:7604"</i> from 'cbAddr.getType() + cbAddr.getRawAddress()'<br />
    *             or <i>"/xmlBlaster/I_Authenticate"</i>
    * @return The instance of this object
    */
   public final Object getObjectEntry(String key)
   {
      return objectMap.get(key);
   }

   /**
    * Add an object in the scope of an I_XmlBlasterAccess or of one cluster node.
    * <p />
    * This is helpful if you have more than one I_XmlBlasterAccess or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getRawAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void addObjectEntry(String key, Object driver)
   {
      objectMap.put(key, driver);
   }

   /**
    * Remove an object from the scope of an I_XmlBlasterAccess or of one cluster node.
    * <p />
    * This is helpful if you have more than one I_XmlBlasterAccess or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getRawAddress()'
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
    *   -bootstrapHostname myhost.mycompany.com   (or the raw IP)
    *   -bootstrapPort 3412
    * </pre>
    * Defaults to the local machine and the IANA xmlBlaster port.<br />
    * You can set "-bootstrapPort 0" to avoid starting the internal HTTP server
    */
   public final Address getBootstrapAddress() {
      if (this.bootstrapAddress == null) {
         synchronized (this) {
            if (this.bootstrapAddress == null) {
               if (log.CALL) log.call(ME, "Entering getBootstrapAddress(), trying to resolve one ...");
               this.bootstrapAddress = new Address(this);
               if (log.TRACE) log.trace(ME, "Initialized bootstrapAddress to host=" + this.bootstrapAddress.getBootstrapHostname() +
                              " port=" + this.bootstrapAddress.getBootstrapPort() + ", rawAddress='" + this.bootstrapAddress.getRawAddress()+"'");
               this.bootstrapAddress.setRawAddress(this.bootstrapAddress.getBootstrapUrl());
            }
         }
      }
      return this.bootstrapAddress;
   }

   /**
    * Returns a local IP or bootstrapHostname as a default setting to use for callback servers.
    * <p />
    * It is determined by doing a short connect to the xmlBlaster HTTP server
    * an reading the used local hostname.
    * The precedence of finding the callback hostname is:
    * <ol>
    *  <li>Evaluate the -bootstrapHostnameCB property</li>
    *  <li>Try to determine it by a temporary connection to the xmlBlaster bootstrap server and reading the used local IP</li>
    *  <li>Use default IP of this host</li>
    * </ol>
    * @return The default IP, is never null
    */
   public String getCbHostname() {
      if (this.cbHostname == null) {
         Address addr = getBootstrapAddress();
         this.cbHostname = getProperty().get("bootstrapHostnameCB",
                           getCbHostname(addr.getBootstrapHostname(), addr.getBootstrapPort()));
      }
      return this.cbHostname;
   }

   /**
    * Returns a local IP as a default setting to use for callback servers.
    * <p />
    * It is determined by doing a short connect to the given hostname/socket
    * an reading the used local hostname.
    * The precedence of finding the callback hostname is:
    * <ol>
    *  <li>Try to determine it by a temporary connection to the given hostname/socket and reading the used local IP</li>
    *  <li>Use default IP of this host</li>
    * </ol>
    * @return The default IP, is never null
    */
   public String getCbHostname(String hostname, int port) {
      String cbHostname = null;
      if (port > 0) {
         try {
            Socket sock = new Socket(hostname, port);
            cbHostname = sock.getLocalAddress().getHostAddress();
            sock.close();
            sock = null;
            if (log.TRACE) log.trace(ME, "Default cb host is " + this.cbHostname);
         }
         catch (java.io.IOException e) {
            log.trace(ME, "Can't find default cb hostname: " + e.toString());
         }
      }
      if (cbHostname == null)
         cbHostname = getLocalIP();
      return cbHostname;
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
      if (logDefault.CALL) logDefault.call(ME, "Entering accessFromInternalHttpServer(" + ((address==null)?"null":address.getRawAddress()) + ") ...");
      //log.info(ME, "accessFromInternalHttpServer address=" + address.toXml());
      Address addr = address;
      if (addr != null && addr.getBootstrapPort() > 0) {
         if (addr.getBootstrapHostname() == null || addr.getBootstrapHostname().length() < 1) {
            addr.setBootstrapHostname(getLocalIP());
         }
      }
      else {
         addr = getBootstrapAddress();
      }

      try {
         if (urlPath != null && urlPath.startsWith("/") == false)
            urlPath = "/" + urlPath;

         if (logDefault.TRACE) logDefault.trace(ME, "Trying internal http server on " + 
                               addr.getBootstrapHostname() + ":" + addr.getBootstrapPort() + "" + urlPath);
         java.net.URL nsURL = new java.net.URL("http", addr.getBootstrapHostname(), addr.getBootstrapPort(), urlPath);
         java.io.InputStream nsis = nsURL.openStream();
         byte[] bytes = new byte[4096];
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
         int numbytes;
         for (int ii=0; ii<20 && (nsis.available() <= 0); ii++) {
            if (logDefault.TRACE) logDefault.trace(ME, "XmlBlaster on host " + addr.getBootstrapHostname() + " and bootstrapPort " + addr.getBootstrapPort() + " returns empty data, trying again after sleeping 10 milli ...");
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
         String text = "XmlBlaster not found on host " + addr.getBootstrapHostname() + " and bootstrap port " + addr.getBootstrapPort() + ".";
         logDefault.error(ME, text + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME+"NoHttpServer", text, e);
      }
      catch(IOException e) {
         if (verbose) logDefault.warn(ME, "XmlBlaster not found on host " + addr.getBootstrapHostname() + " and bootstrapPort " + addr.getBootstrapPort() + ": " + e.toString());
         throw new XmlBlasterException(this, ErrorCode.COMMUNICATION_NOCONNECTION, ME+"NoHttpServer",
                   "XmlBlaster not found on host " + addr.getBootstrapHostname() + " and bootstrap port " + addr.getBootstrapPort() + ".", e);
      }
   }

   /**
    * The IP address where we are running.
    * <p />
    * You can specify the local IP address with e.g. -bootstrapHostname 192.168.10.1
    * on command line, useful for multi-homed hosts.
    *
    * @return The local IP address, defaults to '127.0.0.1' if not known.
    */
   public final String getLocalIP()
   {
      if (this.ip_addr == null) {
         if (getBootstrapAddress().hasBootstrapHostname()) { // check if bootstrapHostname is available to avoid infinit looping
            this.ip_addr = getBootstrapAddress().getBootstrapHostname();
         }
         else {
            try {
               this.ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
            } catch (java.net.UnknownHostException e) {
               logDefault.warn(ME, "Can't determine local IP address, try e.g. '-bootstrapHostname 192.168.10.1' on command line: " + e.toString());
            }
            if (this.ip_addr == null) this.ip_addr = "127.0.0.1";
         }
      }
      return this.ip_addr;
   }

   /**
    * Needed by java client helper classes to load
    * the security plugin
    */
   public final PluginLoader getClientSecurityPluginLoader() {
      synchronized (PluginLoader.class) {
         if (clientSecurityLoader == null)
            clientSecurityLoader = new PluginLoader(this);
      }
      return clientSecurityLoader;
   }

   public final QueuePluginManager getQueuePluginManager() {
      if (queuePluginManager == null) {
         synchronized (QueuePluginManager.class) {
            if (queuePluginManager == null)
               queuePluginManager = new QueuePluginManager(this);
         }
      }
      return queuePluginManager;
   }

   public final DispatchPluginManager getDispatchPluginManager() {
      if (dispatchPluginManager == null) {
         synchronized (DispatchPluginManager.class) {
            if (dispatchPluginManager == null)
               dispatchPluginManager = new DispatchPluginManager(this);
         }
      }
      return dispatchPluginManager;
   }

   public final XmlProcessor getXmlProcessor() throws XmlBlasterException {
      if (this.xmlProcessor == null) {
         synchronized (XmlProcessor.class) {
            if (this.xmlProcessor == null)
               this.xmlProcessor = new XmlProcessor(this);
         }
      }
      return this.xmlProcessor;
   }

   /**
    * Access the xmlBlaster Classloader.
    * Every Global instance may have an own factory instance.
    * Set classLoaderFactory property to not use default StandaloneClassLoaderFactory.
    * @return null if switched off with "useXmlBlasterClassloader=false"
    */
   public ClassLoaderFactory getClassLoaderFactory() {
      boolean useXmlBlasterClassloader = getProperty().get("useXmlBlasterClassloader", true);
      if (useXmlBlasterClassloader == false) return null;

      synchronized (ClassLoaderFactory.class) {
         if (classLoaderFactory == null) {
            String clf = getProperty().get("classLoaderFactory",(String)null);
            if ( clf != null) {
               try {
                  Class clfc = Thread.currentThread().getContextClassLoader().loadClass(clf);
                  
                  
                  classLoaderFactory = (ClassLoaderFactory)clfc.newInstance();
                  classLoaderFactory.init(this);
                  return classLoaderFactory;
                  
               } catch (Exception e) {
                  log.warn(ME,"Could not load custom classLoaderFactory " + clf + " using StandaloneClassLoaderFactory");
               } // end of try-catch
            } // end of if ()
            
            classLoaderFactory = new StandaloneClassLoaderFactory(this);
         }
      }
      return classLoaderFactory;
   }

   /**
    * Access the http server which allows bootstrapping the CORBA IOR
    */
   public final HttpIORServer getHttpServer() throws XmlBlasterException {
      if (this.httpServer == null) {
         synchronized(this) {
            if (this.httpServer == null)
               this.httpServer = new HttpIORServer(this, getBootstrapAddress().getBootstrapHostname(), getBootstrapAddress().getBootstrapPort());
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
    * Get the configured SAXParserFactory.
    *
    * <p>The implementation of the SAXParser factory is decided by the property <code>javax.xml.parsers.SAXParserFactory</code> if available in Global, othervise the default <code>org.apache.crimson.jaxp.SAXParserFactoryImpl</code>is returned</p>
    */
    public SAXParserFactory getSAXParserFactory() throws XmlBlasterException{
      if ( saxFactory == null) {
         try {
            if (log.DUMP) log.dump(ME, getProperty().toXml());
            saxFactory = JAXPFactory.newSAXParserFactory(
               getProperty().get(
                  "javax.xml.parsers.SAXParserFactory",
                  "org.apache.crimson.jaxp.SAXParserFactoryImpl")
               );
         } catch (FactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "SAXParserFactoryError", e);
         } // end of try-catch

      } // end of if ()
      return saxFactory;
   }
   /**
    * Get the configured  DocumentBuilderFactoryFactory.
    *
    * <p>The implementation of the  DocumentBuilderFactory is decided by the property <code>javax.xml.parsers.DocumentBuilderFactory</code> if available in Global, othervise the default <code>org.apache.crimson.jaxp.DocumentBuilderFactoryImpl</code>is returned</p>
    */
   public DocumentBuilderFactory getDocumentBuilderFactory()throws XmlBlasterException {
      if ( docBuilderFactory == null) {
         try {
            if (log.DUMP) log.dump(ME, getProperty().toXml());
            docBuilderFactory =JAXPFactory.newDocumentBuilderFactory(
               getProperty().get(
                  "javax.xml.parsers.DocumentBuilderFactory",
                  "org.apache.crimson.jaxp.DocumentBuilderFactoryImpl")
               );
         } catch (FactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "DocumentBuilderFactoryError", e);
         } // end of try-catch
      } // end of if ()
      return docBuilderFactory;
   }
   /**
    * Get the configured  TransformerFactory.
    *
    * <p>The implementation of the   TransformerFactory is decided by the property <code>javax.xml.transform.TransformerFactory</code> if available in Global, othervise the default <code>org.apache.xalan.processor.TransformerFactoryImpl</code>is returned</p>
    */
   public TransformerFactory getTransformerFactory()throws XmlBlasterException {
      if ( transformerFactory == null) {
         try {
            transformerFactory =JAXPFactory.newTransformerFactory(
               getProperty().get(
                  "javax.xml.transform.TransformerFactory",
                  "org.apache.xalan.processor.TransformerFactoryImpl")
               );
         } catch (TransformerFactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "TransformerFactoryError", e);
         } // end of try-catch
      } // end of if ()
      return transformerFactory;
   }

   /**
    * The factory creating queue or msgUnitStore entries from persistent store.
    * Is overwritten in engine.Global
    * @param name A name identifying this plugin.
    */
   public I_EntryFactory getEntryFactory() {
      if (this.entryFactory != null) return this.entryFactory;
      synchronized(this) {
         this.entryFactory = new ClientEntryFactory();
         this.entryFactory.initialize(this);
         return this.entryFactory;
      }
   }


   /**
    * wipes out the db. The Properties to use as a default are these from the QueuePlugin with the 
    * configuration name specified by defaultConfName (default is 'JDBC'). You can overwrite these 
    * properties entirely or partially with 'properties'.
    * @param confType the name of the configuration to use as default. If you pass null, then 
    *                 'JDBC' will be taken.
    * @param confVersion the version to use as a default. If you pass null, then '1.0' will be taken.
    * @param properties the properties to use to overwrite the default properties. If you pass null, no 
    *        properties will be overwritten, and the default will be used.
    * @param setupNewTables tells the manager to recreate empty tables if set to 'true'. Note that this flag only
    *        has effect if the JdbcManagerCommonTable is used.
    */
/*
   public void wipeOutDB(String confType, String confVersion, java.util.Properties properties, boolean setupNewTables) 
      throws XmlBlasterException {
      if (confType == null) confType = "JDBC";
      if (confVersion == null) confVersion = "1.0";
      QueuePluginManager pluginManager = new QueuePluginManager(this);
      PluginInfo pluginInfo = new PluginInfo(this, pluginManager, confType, confVersion);
      // clone the properties (to make sure they only belong to us) ...
      java.util.Properties
         ownProperties = (java.util.Properties)pluginInfo.getParameters().clone();
      //overwrite our onw properties ...
      if (properties != null) {
         java.util.Enumeration enumer = properties.keys();
         while (enumer.hasMoreElements()) {
            String key =(String)enumer.nextElement();
            ownProperties.put(key, properties.getProperty(key));
         }
      }
      JdbcConnectionPool pool = new JdbcConnectionPool();
      try {
         pool.initialize(this, pluginInfo.getParameters());
      }
      catch (ClassNotFoundException ex) {
         this.log.error(ME, "wipOutDB class not found: " + ex.getMessage());
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "wipeOutDB class not found", ex);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "wipeOutDB SQL exception", ex);
      }

      // determine which jdbc manager class to use
      String queueClassName = pluginInfo.getClassName();
      if ("org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin".equals(queueClassName)) {
         this.log.error(ME, "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin is not supported anymore");
      }
      else if ("org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin".equals(queueClassName)) {
         // then it is a JdbcManagerCommontTable
         // then it is a JdbcManager
         JdbcManagerCommonTable manager = new JdbcManagerCommonTable(pool, null);
         pool.registerStorageProblemListener(manager);
         manager.setUp();
         manager.wipeOutDB(setupNewTables);
      }
      else {
         throw new XmlBlasterException(this, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "wipeOutDB for plugin '" + queueClassName + "' is not implemented");
      }
   }
*/

   /**
    * This notation is URLEncoder since JDK 1.4.
    * To avoid deprecation warnings
    * at many places and support JDK < 1.4 we provide it here
    * and simply map it to the old encode(String)
    */
   public static String encode(String s, String enc) {
      return java.net.URLEncoder.encode(s);
   }

   /**
    * This notation is URLDecoder since JDK 1.4.
    * To avoid deprecation warnings
    * at many places and support JDK < 1.4 we provide it here
    * and simply map it to the old encode(String)
    */
   public static String decode(String s, String enc) {
      try {
         return java.net.URLDecoder.decode(s);
      }
      catch (Exception e) {
         System.out.println("PANIC in decode(" + s + "): " + e.toString());
         return s;
      }
   }

   /**
    * Access the handle of the burst mode timer thread.
    * @return The Timeout instance
    */
   public final ProtocolPluginManager getProtocolPluginManager() {
      if (this.protocolPluginManager == null) {
         synchronized(this) {
            if (this.protocolPluginManager == null)
               this.protocolPluginManager = new ProtocolPluginManager(this);
         }
      }
      return this.protocolPluginManager;
   }

   /**
    * Access the handle of the burst mode timer thread.
    * @return The Timeout instance
    */
   public final CbServerPluginManager getCbServerPluginManager() {
      if (this.cbServerPluginManager == null) {
         synchronized(this) {
            if (this.cbServerPluginManager == null)
               this.cbServerPluginManager = new CbServerPluginManager(this);
         }
      }
      return this.cbServerPluginManager;
   }

   /**
    * Access the handle of the burst mode timer thread.
    * @return The Timeout instance
    */
   public final Timeout getBurstModeTimer() {
      if (this.burstModeTimer == null) {
         synchronized(this) {
            if (this.burstModeTimer == null)
               this.burstModeTimer = new Timeout("XmlBlaster.BurstmodeTimer");
         }
      }
      return this.burstModeTimer;
   }

   /**
    * Access the handle of the ping timer thread to test a client or callback connection. 
    * @return The Timeout instance
    */
   public final Timeout getPingTimer() {
      if (this.pingTimer == null) {
         synchronized(this) {
            if (this.pingTimer == null)
               this.pingTimer = new Timeout("XmlBlaster.PingTimer");
         }
      }
      return this.pingTimer;
   }

   /**
    * Access the handle of the message expiry timer thread.
    * NOTE: This holds only weak references to its callback I_Timeout
    * So there is no need to clear the timer registration
    * @return The Timeout instance
    */
   public final Timeout getMessageTimer() {
      if (this.messageTimer == null) {
         synchronized(this) {
            if (this.messageTimer == null) {
               boolean useWeakReferences = true;
               this.messageTimer = new Timeout("XmlBlaster.MessageTimer", useWeakReferences);
            }
         }
      }
      return this.messageTimer;
   }


   /**
    * Access the handle of the jdbcConnectionPool timer (the timer for the
    * polling when the connection has been lost).
    * @return The Timeout instance
    */
   public final Timeout getJdbcConnectionPoolTimer() {
      if (this.jdbcConnectionPoolTimer == null) {
         synchronized(this) {
            if (this.jdbcConnectionPoolTimer == null)
               this.jdbcConnectionPoolTimer = new Timeout("XmlBlaster.JdbcConnectionPoolTimer");
         }
      }
      return this.jdbcConnectionPoolTimer;
   }


   /**
    * Access the handle of the callback thread pool.
    * @return The DispatchWorkerPool instance
    */
   public final DispatchWorkerPool getDispatchWorkerPool() {
      if (this.dispatchWorkerPool == null) {
         synchronized(this) {
            if (this.dispatchWorkerPool == null)
               this.dispatchWorkerPool = new DispatchWorkerPool(this);
         }
      }
      return this.dispatchWorkerPool;
   }

   /**
    * Returns the client access layer implementations 'ClientDispatchConnectionsHandler'
    */
   public DispatchConnectionsHandler createDispatchConnectionsHandler(DispatchManager dispatchManager) throws XmlBlasterException {
      return new ClientDispatchConnectionsHandler(this, dispatchManager);
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "Entering finalize");
      shutdown();
   }

   public void shutdown() {
      if (this.isDoingShutdown) {
         return;
      }
      this.isDoingShutdown = true;

      if (log.TRACE) log.trace(ME, "Destroying util.Global handle");


      //Thread.currentThread().dumpStack();
      if (this.dispatchWorkerPool != null) {
         this.dispatchWorkerPool.shutdown();
         // registered itself to Runlevel changes dispatchWorkerPool.shutdown();?
         this.dispatchWorkerPool = null;
      }
      if (this.burstModeTimer != null) {
         this.burstModeTimer.shutdown();
         this.burstModeTimer = null;
      }
      if (this.pingTimer != null) {
         this.pingTimer.shutdown();
         this.pingTimer = null;
      }
      if (this.messageTimer != null) {
         this.messageTimer.shutdown();
         this.messageTimer = null;
      }

      if (this.jdbcConnectionPoolTimer != null) {
         this.jdbcConnectionPoolTimer.shutdown();
         this.jdbcConnectionPoolTimer = null;
      }

      shutdownHttpServer();

      if (this.xmlProcessor != null) {
         this.xmlProcessor.shutdown();
         this.xmlProcessor = null;
      }
      /*
      try {
         unregisterJmx();
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Ignoring exception during JMX unregister: " + e.getMessage());
      }
      */
      synchronized (Global.class) {
         if (firstInstance != null && this == firstInstance) {
            //System.out.println("###################################First instance of Global destroyed");
            firstInstance = null;
         }
      }
      
      this.isDoingShutdown = false;
   }

   /**
    * Check where we are, on client or on server side?
    * engine.Global overwrites this
    * @return false As we are util.Global and running client side
    */
   public boolean isServer() {
      return false;
   }

   /**
    * Returns the plugin manager used by the run level manager. All other specific
    * Managers extend this class and reference the cache on this instance.
    */
   public PluginManagerBase getPluginManager() {
      if (this.pluginManager == null) {
         synchronized(this) {
            if (this.pluginManager == null)
               this.pluginManager = new PluginManagerBase(this);
         }
      }
      return this.pluginManager;
   }


   /**
    * Returns the plugin registry.
    */
   public PluginRegistry getPluginRegistry() {
      if (this.pluginRegistry == null) {
         synchronized(this) {
            if (this.pluginRegistry == null)
               this.pluginRegistry = new PluginRegistry(this);
         }
      }
      return this.pluginRegistry;
   }


   /**
    * The client handle to access xmlBlaster remotely. 
    * <p>
    * Access your client side handle with this method only, it
    * is the with this Global instance configured client connection to xmlBlaster
    * (a singleton regarding this Global).
    * </p>
    * <p>
    * Multiple invocations return the same instance.
    * </p>
    * <p>
    * NOTE: On server side engine.Global.getXmlBlasterAccess() returns the native access handle.
    * </p>
    * @exception IllegalArgumentException If we are extended by engine.Global (use getClone() to create a util.Global)
    */
   public I_XmlBlasterAccess getXmlBlasterAccess() {
      if (this.xmlBlasterAccess == null) {
         synchronized(this) {
            if (this.xmlBlasterAccess == null)
               this.xmlBlasterAccess = new XmlBlasterAccess(this);
         }
      }
      return this.xmlBlasterAccess;
   }

   /**
    * Tells the queue manager it should remove all entries from the persisence.
    * This is used by the RequestBroker when starting up
    * @param doWipeout
    */
   public void setWipeOutDB(boolean doWipeout) {
      this.wipeOutDB = doWipeout;
   }

   /**
    * Tells the jdbc queue manager if a cleanup of the DB is necessary
    * @return true if a cleanup is necessary
    */
   public boolean getWipeOutDB() {
      return this.wipeOutDB;
   }

   public static String getStackTraceAsString() {
      // this is just to send the stack trace to the log file (stderr does not go there)
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream pstr = new PrintStream(baos);
      new Exception().printStackTrace(pstr);
      return new String(baos.toByteArray());
   }

   /**
    * Convenience method which returns the typical environment settings for a LOCAL connection. 
    * <p>If you write a native plugin you can use these settings as a base.</p>
    * @return A string array which you can pass to <tt>this.global = glob.getClone(glob.getNativeConnectArgs());</tt>
    */
   public final String[] getNativeConnectArgs() {
      final String[] nativeConnectArgs = {
              "-protocol", "LOCAL",
              "-session.timeout", "0",
              "-dispatch/connection/protocol", "LOCAL",
              "-dispatch/connection/pingInterval", "0",
              "-dispatch/connection/burstMode/collectTime", "0",
              "-dispatch/callback/protocol", "LOCAL",
              "-dispatch/callback/pingInterval", "0",
              "-dispatch/callback/burstMode/collectTime", "0",
              /*"-queue/defaultPlugin", "RAM,1.0",*/
              "-queue/connection/defaultPlugin", "RAM,1.0",
              "-queue/callback/defaultPlugin", "RAM,1.0",
              "-queue/subject/defaultPlugin", "RAM,1.0"
           };
      return nativeConnectArgs;
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
   public String usage() {
      StringBuffer sb = new StringBuffer(4028);
      sb.append(org.xmlBlaster.client.XmlBlasterAccess.usage(this));
      sb.append(logUsage());
      return sb.toString();
   }

   public static String logUsage() {
      StringBuffer sb = new StringBuffer(1024);
      sb.append("Logging options:\n");
      sb.append("   -info  false        Supress info output.\n");
      sb.append("   -trace true         Show code trace.\n");
      sb.append("   -dump  true         Dump internal state.\n");
      sb.append("   -call  true         Show important method entries\n");
      sb.append("   -time true          Display some performance data.\n");
      sb.append("   -logFile <fileName> Log to given file.\n");
      sb.append("   -logDevice file,console  Log to console and above file.\n");
      sb.append("   Example:  -logFile /tmp/test.log -logDevice file,console -call true -trace[corba] true.\n");
      sb.append("\n");
      sb.append("Control properties framework:\n");
      sb.append("   -propertyFile <file> Specify an xmlBlaster property file to load.\n");
      sb.append("                        The contained settings overwrite a property file found in the xmlBlaster.jar file.\n");
      sb.append("   -property.verbose   0 switches logging off, 2 is most verbose when loading properties on startup [" + Property.DEFAULT_VERBOSE + "].\n");
      return sb.toString();
   }

   /** To play with a profiling tool */
   public static void main(String[] args) {
      System.out.println("NO GLOBAL, Hit a key");
      try { System.in.read(); } catch(java.io.IOException e) {}
      Global glob = new Global(args);
      try {
         while (true) {
            System.out.println("NO XmlBlasterAccess, Hit a key");
            try { System.in.read(); } catch(java.io.IOException e) {}
            XmlBlasterAccess a = new XmlBlasterAccess(glob);

            System.out.println("connecting ...");
            a.connect(null, null);
            System.out.println("connected ...");
            
            System.out.println("Hit a key");
            try { System.in.read(); } catch(java.io.IOException e) {}
            a.disconnect(null);

            System.out.println("All is shutdown: Hit a key");
            try { System.in.read(); } catch(java.io.IOException e) {}
         }
      }
      catch (Exception e) {
         System.out.println("Global.main: " + e.toString());
      }
   }
   
   /**
    * It searches for the given property. 
    * The replacement for '${...}' is supported. Note that the assignment of a '$'
    * variable can only be done in global scope, that is in the xmlBlaster.properties or command line,
    * and JVM properties but not in the xmlBlasterPlugins.xml.
    * 
    * It first looks into the map (the hardcoded properties). If one is found it is returned.
    * Then it looks into the global. If one is found it is returned. If none is found it is 
    * searched in the plugin
    * @param shortKey the key (in its short form without prefix) of the property
    * @param defaultValue the default value of the property (weakest)
    * @param map the hardcoded properties (strongest)
    * @param pluginConfig the pluginConfig used 
    * @return
    */
   public String get(String shortKey, String defaultValue, Properties map, I_PluginConfig pluginConfig) 
      throws XmlBlasterException {
      try {
         if (shortKey == null) {
            return defaultValue;
         }
         String ret = (pluginConfig == null) ? defaultValue : pluginConfig.getParameters().getProperty(shortKey, defaultValue);
         String prefix = (pluginConfig == null) ? "" : pluginConfig.getPrefix();  // "plugin/" + getType() + "/"
         ret = getProperty().get(prefix + shortKey, ret);
         if (map != null)
            ret = map.getProperty(shortKey, ret);
         return getProperty().replaceVariableWithException(shortKey, ret);
      }
      catch (JUtilsException ex) {
         throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME + ".get", "exception when getting property '" + shortKey + "'", ex);
      }
   }
   
   /**
    * 
    * @param shortKey
    * @param defaultValue
    * @param map
    * @param pluginConfig
    * @return
    * @throws XmlBlasterException
    * @see get(String, String, map, I_PluginConfig)
    */
   public long get(String shortKey, long defaultValue, Properties map, I_PluginConfig pluginConfig) 
      throws XmlBlasterException {
      String tmp = get(shortKey, null, map, pluginConfig);
      if (tmp == null) // should never happen
         return defaultValue; 
      try {
         return Long.parseLong(tmp);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION, ME + ".get", "wrong type for '" + shortKey + "': should be long but is '" + tmp + "'");
      }
   }
   
   /**
    * 
    * @param shortKey
    * @param defaultValue
    * @param map
    * @param pluginConfig
    * @return
    * @throws XmlBlasterException
    * @see get(String, String, map, I_PluginConfig)
    */
   public int get(String shortKey, int defaultValue, Properties map, I_PluginConfig pluginConfig) 
      throws XmlBlasterException {
      String tmp = get(shortKey, null, map, pluginConfig);
      if (tmp == null) // should never happen
         return defaultValue; 
      try {
         return Integer.parseInt(tmp);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION, ME + ".get", "wrong type for '" + shortKey + "': should be int but is '" + tmp + "'");
      }
   }

   /**
    * 
    * @param shortKey
    * @param defaultValue
    * @param map
    * @param pluginConfig
    * @return
    * @throws XmlBlasterException
    * @see get(String, String, map, I_PluginConfig)
    */
   public boolean get(String shortKey, boolean defaultValue, Properties map, I_PluginConfig pluginConfig) 
      throws XmlBlasterException {
      String tmp = get(shortKey, null, map, pluginConfig);
      if (tmp == null) // should never happen
         return defaultValue; 
      try {
         return new Boolean(tmp).booleanValue();
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION, ME + ".get", "wrong type for '" + shortKey + "': should be boolean but is '" + tmp + "'");
      }
   }

   /**
    * 
    * @param shortKey
    * @param defaultValue
    * @param map
    * @param pluginConfig
    * @return
    * @throws XmlBlasterException
    * @see get(String, String, map, I_PluginConfig)
    */
   public double get(String shortKey, double defaultValue, Properties map, I_PluginConfig pluginConfig) 
      throws XmlBlasterException {
      String tmp = get(shortKey, null, map, pluginConfig);
      if (tmp == null) // should never happen
         return defaultValue; 
      try {
         return Double.parseDouble(tmp);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION, ME + ".get", "wrong type for '" + shortKey + "': should be double but is '" + tmp + "'");
      }
   }


}
