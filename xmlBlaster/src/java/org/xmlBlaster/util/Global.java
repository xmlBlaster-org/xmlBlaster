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
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.AddressBase;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.util.key.I_MsgKeyFactory;
import org.xmlBlaster.util.key.MsgKeySaxFactory;
import org.xmlBlaster.util.key.I_QueryKeyFactory;
import org.xmlBlaster.util.key.QueryKeySaxFactory;
import org.xmlBlaster.util.qos.I_MsgQosFactory;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.I_StatusQosFactory;
import org.xmlBlaster.util.qos.StatusQosSaxFactory;
import org.xmlBlaster.util.qos.StatusQosQuickParseFactory;
import org.xmlBlaster.util.recorder.RecorderPluginManager;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.dispatch.plugins.DispatchPluginManager;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.DeliveryWorkerPool;
import org.xmlBlaster.util.dispatch.DeliveryConnectionsHandler;
import org.xmlBlaster.client.dispatch.ClientDeliveryConnectionsHandler;
import org.xmlBlaster.client.protocol.ProtocolPluginManager;
import org.xmlBlaster.client.protocol.CbServerPluginManager;
import org.xmlBlaster.authentication.HttpIORServer;
import org.xmlBlaster.util.log.LogDevicePluginManager;
import org.xmlBlaster.util.log.I_LogDeviceFactory;
import org.jutils.log.LogableDevice;

import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.client.queuemsg.ClientEntryFactory;

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
import java.sql.SQLException;
import org.xmlBlaster.util.JAXPFactory;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
/**
 * Global variables to avoid singleton.
 *
 * @see org.xmlBlaster.test.classtest.GlobalTest
 */
public class Global implements Cloneable
{
   private static Global firstInstance = null;

   /** Version string, please change for new releases (4 digits) */
   private String versionDefault = "0.8x";
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

   protected RecorderPluginManager recorderPluginManager = null;
   private HttpIORServer httpServer = null;  // xmlBlaster publishes his AuthServer IOR

   protected Hashtable logChannels = new Hashtable();
   protected LogChannel logDefault = null;

   protected SAXParserFactory saxFactory = null;
   protected DocumentBuilderFactory docBuilderFactory = null;
   protected TransformerFactory transformerFactory = null;

   protected I_MsgKeyFactory msgKeyFactory;
   protected I_QueryKeyFactory queryKeyFactory;
   protected I_MsgQosFactory msgQosFactory;
   protected I_QueryQosFactory queryQosFactory;
   protected I_StatusQosFactory statusQosFactory;

   protected Timeout cbPingTimer;
   protected Timeout burstModeTimer;
   protected Timeout messageTimer;
   protected Timeout jdbcConnectionPoolTimer;
   protected DeliveryWorkerPool deliveryWorkerPool;

   protected LogDevicePluginManager logDevicePluginManager = null;
   /** used to guard agains log device plugin loading making cirkular calls*/
   private boolean creatingLogInstance = false;

   protected static int counter = 0;

   /** a hastable keeping all JdbcManager objects: one per DB */
   protected Hashtable jdbcQueueManagers = null;

   /**
    * Constructs an initial Global object.
    */
   public Global()
   {
      counter++;
      if (this.firstInstance != null) {
         System.out.println("######Global " + counter + " empty constructor invoked again, try Global.instance()");
         //Thread.currentThread().dumpStack();
      }
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
      }
      this.args = new String[0];
      initProps(this.args);
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
    * Constructs an initial Global object which is initialized
    * by your properties array (usually the command line args).
    */
   public Global(Properties props) {
      this(Property.propsToArgs(props));
      counter++;
   }
   /**
    * Constructs an initial Global object which is initialized
    * by your args array (usually the command line args).
    */
   public Global(String[] args)
   {
      this(args,true);
   }
   /**
    * Constructs an initial Global object which is initialized
    * by your args array (usually the command line args).
    *
    * <p>By setting loadPropFile to false it is possible to create a Global
    * which does not automtically search out the xmlBlaster.properties file, 
    * which is god when you want to start xmlBlaster in an embedded environment.
    * <p>Its possible to later load the property file if one wants, here is one way to do it:</p>
    * <pre>
         Property p = glob.getProperty();
         Properties prop = new Properties();
         FileInfo i = p.findPath("xmlBlaster.properties");
         InputStream is = i.getInputStream();
         prop.load(is);
         String[] ar = Property.propsToArgs(prop);
         p.addArgs2Props( ar != null ? ar : new String[0] );
       </pre>
     <p>It is also possible to load an entire other property file or find it 
        with some other algoritm byte using the same pattern as obove, just
        don't use findPath, but some other code.</p>
    * @param args args array (usually the command line args).
    * @param loadPropFile if automatic loading of xmlBlaster.properties should be done.
    */
   public Global(String[] args, boolean loadPropFile)
   {
      counter++;
      if (this.firstInstance != null) {
         System.out.println("######Global args constructor invoked again, try Global.instance()");
         //Thread.currentThread().dumpStack();
      }
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
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
      init(args);
   }

   /**
    * If you have a util.Global and need a shallow copy.
    */
   public Global(org.xmlBlaster.util.Global utilGlob) {
      counter++;
      log = utilGlob.getLog(null);
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
   protected void initId() {
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
      this.cbHostname =  utilGlob.cbHostname;
      this.nativeCallbackDriverMap = utilGlob.nativeCallbackDriverMap;
      this.objectMap = utilGlob.objectMap;
      this.bootstrapAddress = utilGlob.bootstrapAddress;
      this.clientSecurityLoader = utilGlob.clientSecurityLoader;
      this.recorderPluginManager = utilGlob.recorderPluginManager;
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
                     if (log.TRACE) log.trace(ME,"Setting logDevice " +key+"[" + devices[i]+"]="+dev.getClass().getName());
                     if (dev != null)
                        lc.addLogDevice(dev);
                  }catch(XmlBlasterException ex) {
                     log.error(ME,"Global: error in getting LogDeviceFactory for " + key);
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
    * @param loadPropFile if loading of xmlBlaster.properties file should be done, if false no loading of the file is done.
    * @return -1 on error
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
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(Properties props) {
      return init(Property.propsToArgs(props));
   }

   /**
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(String[] args)
   {
      this.args = (args==null) ? new String[0] : args;

      if (this.args != null && this.args.length > 0)
         this.bootstrapAddress = null;   // clear cached address

      try {
         property.addArgs2Props(this.args);

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
    * Access the id (as a String) currently used on server side.
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
    * @param text e.g. "http://www.xmlBlaster.org:/home\\x"
    * @return e.g. "http_www_xmlBlaster_org_homex"
    */
   public static final String getStrippedString(String text) {
      String strippedId = StringHelper.replaceAll(text, "/", "");
      strippedId = StringHelper.replaceAll(strippedId, ".", "_");
      strippedId = StringHelper.replaceAll(strippedId, ":", "_");
      return StringHelper.replaceAll(strippedId, "\\", "");
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
         g.msgQosFactory = this.msgQosFactory;
         g.queryQosFactory = this.queryQosFactory;
         g.statusQosFactory = this.statusQosFactory;
         g.dispatchPluginManager = null; // Force a new instance of DispatchPluginManager (which has its separate cache of plugins)

         if (g.id != id)
            getLog("core").error(ME, "g.id=" + g.id + " and id=" + id);
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
    * The command line arguments only.
    * @return the arguments, is never null
    */
   public final String[] getArgs()
   {
      return this.args;
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
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
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
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
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
    * @param key  e.g. "SOCKET192.168.2.2:7604" from 'cbAddr.getType() + cbAddr.getAddress()'
    * @param The instance of the protocol callback driver
    */
   public final void removeNativeCallbackDriver(String key)
   {
      if (log.CALL) log.call(ME, "removeNativeCallbackDriver(" + key + ")");
      nativeCallbackDriverMap.remove(key);
   }

   /**
    * Get an object in the scope of an XmlBlasterConnection or of one cluster node.
    * <p />
    * This is helpful if you have more than one XmlBlasterConnection or cluster nodes
    * running in the same JVM
    *
    * @param key  e.g. <i>"SOCKET192.168.2.2:7604"</i> from 'cbAddr.getType() + cbAddr.getAddress()'<br />
    *             or <i>"/xmlBlaster/I_Authenticate"</i>
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
         bootstrapAddress.setHostname(getBootstrapHostname());
         bootstrapAddress.setPort(getBootstrapPort());
         bootstrapAddress.setAddress("http://" + bootstrapAddress.getHostname() + ":" + bootstrapAddress.getPort());
         if (log.TRACE) log.trace(ME, "Initialized bootstrapAddress to host=" + bootstrapAddress.getHostname() +
                        " port=" + bootstrapAddress.getPort() + ": " + bootstrapAddress.getAddress());
      }
      return bootstrapAddress;
   }

   /**
    * Please prefer getBootstrapAddress().getHostname() as this is cached and better performing
    */
   public final String getBootstrapHostname() {
      boolean supportOldStyle = true; // for a while we support the old style -iorHost and -iorPort
      if (supportOldStyle) {
         String iorHost = getProperty().get("iorHost", getLocalIP());
         return getProperty().get("hostname", iorHost);
      }
      else {
         return getProperty().get("hostname", getLocalIP());
      }
   }

   /**
    * Please prefer getBootstrapAddress().getPort() as this is cached and better performing
    */
   public final int getBootstrapPort() {
      boolean supportOldStyle = true; // for a while we support the old style -iorHost and -iorPort
      if (supportOldStyle) {
         int iorPort = getProperty().get("iorPort", Constants.XMLBLASTER_PORT);
         return getProperty().get("port", iorPort);
      }
      else {
         return getProperty().get("port", Constants.XMLBLASTER_PORT);
      }
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
         }
         if (this.cbHostname == null)
            this.cbHostname = getLocalIP();
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
      //log.info(ME, "accessFromInternalHttpServer address=" + address.toXml());
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
         e.printStackTrace();
         throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME+"NoHttpServer", text, e);
      }
      catch(IOException e) {
         if (verbose) logDefault.warn(ME, "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ": " + e.toString());
         throw new XmlBlasterException(this, ErrorCode.COMMUNICATION_NOCONNECTION, ME+"NoHttpServer",
                   "XmlBlaster not found on host " + addr.getHostname() + " and port " + addr.getPort() + ".", e);
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
      if (this.ip_addr == null) {
         if (getBootstrapAddress().hasHostname()) { // check if hostname is available to avoid infinit looping
            this.ip_addr = getBootstrapAddress().getHostname();
         }
         else {
            try {
               this.ip_addr = java.net.InetAddress.getLocalHost().getHostAddress(); // e.g. "204.120.1.12"
            } catch (java.net.UnknownHostException e) {
               logDefault.warn(ME, "Can't determine local IP address, try e.g. '-hostname 192.168.10.1' on command line: " + e.toString());
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

   /**
    * Access the xmlBlaster Classloader.
    * Every Global instance may have an own factory instance.
    * @return null if switched off with "useXmlBlasterClassloader=false"
    */
   public ClassLoaderFactory getClassLoaderFactory() {
      boolean useXmlBlasterClassloader = getProperty().get("useXmlBlasterClassloader", true);
      if (useXmlBlasterClassloader == false) return null;

      synchronized (ClassLoaderFactory.class) {
         if (classLoaderFactory == null)
            classLoaderFactory = new ClassLoaderFactory(this);
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
    * The factory creating queue or msgstore entries from persistent store. 
    * Is overwritten in engine.Global
    * @param name A name identifying this plugin.
    */
   public I_EntryFactory getEntryFactory(String name) {
      ClientEntryFactory factory = new ClientEntryFactory();
      factory.initialize(this, name);
      return factory;
   }

   /**
    * Returns a JdbcManager for a specific queue. It strips the queueId to
    * find out to which manager it belongs. If such a manager does not exist
    * yet, it is created and initialized.
    * A queueId must be of the kind: cb:some/id/or/someother
    * where the important requirement here is that it contains a ':' character.
    * text on the left side of the separator (in this case 'cb') tells which
    * kind of queue it is: for example a callback queue (cb) or a client queue.
    */
   public synchronized JdbcManager getJdbcQueueManager(StorageId queueId)
      throws XmlBlasterException {

      String location = ME + "/Queue '" + queueId + "'";

      if (this.jdbcQueueManagers == null) this.jdbcQueueManagers = new Hashtable();

      String managerName = queueId.getPrefix();

      Object obj = this.jdbcQueueManagers.get(managerName);
      JdbcManager manager = null;
      if (obj == null) {

         JdbcConnectionPool pool = new JdbcConnectionPool();
         try {
            pool.initialize(this, managerName + ".queue.persistent");
            manager = new JdbcManager(pool, getEntryFactory(managerName));
            pool.setConnectionListener(manager);
            manager.setUp();
         }
         catch (ClassNotFoundException ex) {
            this.log.error(location, "getJdbcQueueManager class not found: " + ex.getMessage());
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, location, "getJdbcQueueManager class not found", ex);
         }
         catch (SQLException ex) {
            if (this.log.TRACE) this.log.trace(location, "getJdbcQueueManager SQL exception: " + ex.getMessage());
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, location, "getJdbcQueueManager SQL exception", ex);
         }

         this.jdbcQueueManagers.put(managerName, manager);
      }
      else manager = (JdbcManager)obj;

      try {
         if (!manager.getPool().isInitialized())
            manager.getPool().initialize(this, managerName + ".queue.persistent");
      }
      catch (ClassNotFoundException ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, location, "getJdbcQueueManager: class not found when initializing the connection pool", ex);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_DB_UNAVAILABLE, location, "getJdbcQueueManager: sql exception when initializing the connection pool", ex);
      }
      return manager;
   }


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
      return java.net.URLDecoder.decode(s);
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
    * Access the handle of the callback ping timer thread. 
    * @return The Timeout instance
    */
   public final Timeout getCbPingTimer() {
      if (this.cbPingTimer == null) {
         synchronized(this) {
            if (this.cbPingTimer == null)
               this.cbPingTimer = new Timeout("XmlBlaster.CbPingTimer");
         }
      }
      return this.cbPingTimer;
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
    * @return The DeliveryWorkerPool instance
    */
   public final DeliveryWorkerPool getDeliveryWorkerPool() {
      if (this.deliveryWorkerPool == null) {
         synchronized(this) {
            if (this.deliveryWorkerPool == null)
               this.deliveryWorkerPool = new DeliveryWorkerPool(this);
         }
      }
      return this.deliveryWorkerPool;
   }

   /**
    * Returns the client access layer implementations 'ClientDeliveryConnectionsHandler'
    */
   public DeliveryConnectionsHandler createDeliveryConnectionsHandler(DeliveryManager deliveryManager, AddressBase[] addrArr) throws XmlBlasterException {
      return new ClientDeliveryConnectionsHandler(this, deliveryManager, addrArr);
   }

   public void shutdown() { 
      log.info(ME, "Destroying global handle");
      if (deliveryWorkerPool != null) {
         deliveryWorkerPool.shutdown();
         // registered itself to Runlevel changes deliveryWorkerPool.shutdown();?
         deliveryWorkerPool = null;
      }
      if (burstModeTimer != null) {
         burstModeTimer.shutdown();
         burstModeTimer = null;
      }
      if (cbPingTimer != null) {
         cbPingTimer.shutdown();
         cbPingTimer = null;
      }
      if (messageTimer != null) {
         messageTimer.shutdown();
         messageTimer = null;
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
      sb.append("   -logDevice file,console  Log to console and above file.\n");
      sb.append("\n");
      sb.append("Control properties framework:\n");
      sb.append("   -propertyFile <file> Specify an xmlBlaster property file to load.\n");
      sb.append("                        The contained settings overwrite a property file found in the xmlBlaster.jar file.\n");
      sb.append("   -property.verbose   0 switches logging off, 2 is most verbose when loading properties on startup [" + Property.DEFAULT_VERBOSE + "].\n");
      return sb.toString();
   }
}
