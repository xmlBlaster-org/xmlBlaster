/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.management.AttributeChangeNotification;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.ObjectName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.PluginLoader;
import org.xmlBlaster.client.XmlBlasterAccess;
import org.xmlBlaster.client.dispatch.ClientDispatchConnectionsHandler;
import org.xmlBlaster.client.protocol.CbServerPluginManager;
import org.xmlBlaster.client.protocol.ProtocolPluginManager;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.queuemsg.ClientEntryFactory;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.script.XmlScriptInterpreter;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.admin.extern.JmxWrapper;
import org.xmlBlaster.util.checkpoint.I_Checkpoint;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.StandaloneClassLoaderFactory;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.DispatchConnectionsHandler;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchWorkerPool;
import org.xmlBlaster.util.dispatch.plugins.DispatchPluginManager;
import org.xmlBlaster.util.http.HttpIORServer;
import org.xmlBlaster.util.key.I_MsgKeyFactory;
import org.xmlBlaster.util.key.I_QueryKeyFactory;
import org.xmlBlaster.util.key.MsgKeySaxFactory;
import org.xmlBlaster.util.key.QueryKeySaxFactory;
import org.xmlBlaster.util.log.XbFormatter;
import org.xmlBlaster.util.plugin.I_PluginConfig;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginRegistry;
import org.xmlBlaster.util.property.Property;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.DisconnectQosSaxFactory;
import org.xmlBlaster.util.qos.I_ConnectQosFactory;
import org.xmlBlaster.util.qos.I_DisconnectQosFactory;
import org.xmlBlaster.util.qos.I_MsgQosFactory;
import org.xmlBlaster.util.qos.I_QueryQosFactory;
import org.xmlBlaster.util.qos.I_StatusQosFactory;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.StatusQosQuickParseFactory;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.jdbc.JdbcQueue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

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
   private static Logger log = Logger.getLogger(Global.class.getName());
   private static boolean logIsInitialized;

   private volatile static Global firstInstance;

   /** The amount of physical RAM of this machine.
    * Set by JmxWrappter.java, else 0
    * @since JDK 1.5
    */
   public static long totalPhysicalMemorySize;

   /** Number of bytes this JVM can allocate max, the -Xmx???M setting
    * Set by JmxWrappter.java
    * (defaults to Runtime.getRuntime().maxMemory())
    * @since JDK 1.5
    */
   public static long heapMemoryUsage = Runtime.getRuntime().maxMemory();

   /** The max number of file descriptors this JVM may use
    * Set by JmxWrappter.java, else 0
    * @since JDK 1.5
    */
   public static long maxFileDescriptorCount;

   /** Version string, please change for new releases (4 digits) */
   private String versionDefault = "2.0.0";
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
   private volatile String instanceId;

   protected volatile Property property = null;
   protected String errorText = null;

   protected ContextNode contextNode;
   protected ContextNode scopeContextNode;

   protected String cbHostname = null;

   protected String addressNormalized = null;

   /** The xmlBlaster class loader factory */
   private ClassLoaderFactory classLoaderFactory = null;

   protected /*final*/ Map nativeCallbackDriverMap;
   /** Store objects in the scope of one client connection or server instance */
   protected /*final*/ Map objectMap;
   /** Helper to synchronize objectMap access */
   public final Object objectMapMonitor = new Object();

   protected volatile Address bootstrapAddress;
   protected PluginLoader clientSecurityLoader;

   protected volatile QueuePluginManager queuePluginManager;

   protected volatile DispatchPluginManager dispatchPluginManager;

   protected volatile ProtocolPluginManager protocolPluginManager;
   protected volatile CbServerPluginManager cbServerPluginManager;

   private volatile HttpIORServer httpServer;  // xmlBlaster publishes his AuthServer IOR

   protected Hashtable logChannels = new Hashtable();

   protected SAXParserFactory saxFactory;
   protected DocumentBuilderFactory docBuilderFactory;
   protected TransformerFactory transformerFactory;

   /** Must be loaded in runlevel 0 or 1 before any access as not synchronized */
   protected I_Checkpoint checkpointPlugin;

   protected volatile I_MsgKeyFactory msgKeyFactory;
   protected volatile I_QueryKeyFactory queryKeyFactory;
   protected volatile I_ConnectQosFactory connectQosFactory;
   protected volatile I_DisconnectQosFactory disconnectQosFactory;
   protected volatile I_MsgQosFactory msgQosFactory;
   protected volatile I_QueryQosFactory queryQosFactory;
   protected volatile I_StatusQosFactory statusQosFactory;

   protected volatile I_TimeoutManager pingTimer;
   protected volatile Timeout burstModeTimer;
   protected volatile Timeout messageTimer;
   protected volatile Timeout jdbcConnectionPoolTimer;
   protected volatile DispatchWorkerPool dispatchWorkerPool;

   protected static int counter = 0;

   private volatile PluginManagerBase pluginManager;
   private volatile PluginRegistry pluginRegistry;

   /** The client handle to access xmlBlaster */
   protected volatile I_XmlBlasterAccess xmlBlasterAccess;

   protected boolean isDoingShutdown = false;

   /** set to allow wipe out the persistence on restarts */
   protected boolean wipeOutDB = false;

   //** the entry factory to be used */
   protected I_EntryFactory entryFactory;

   /** JMX notification sequence number. */
   protected long sequenceNumber = 1;

   private Map weakRegistry = new WeakHashMap<Object, Object>();
   
   /**
    * Constructs an initial Global object,
    * same as Global(null, true, true)
    */
   public Global() {
      this(null, true, false);
   }

   /**
    * Constructs an initial Global object which is initialized
    * by your properties (without leading '-'),
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

   public Global(String[] args, boolean loadPropFile, boolean checkInstance) {
      this(args, loadPropFile, checkInstance, true);
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
   public Global(String[] args, boolean loadPropFile, boolean checkInstance, boolean doReplace)
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
      initProps(args,loadPropFile, doReplace);
      initId();
      nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());
      objectMap = new HashMap();

      try { // since JKD 1.4:
         URL url = initLogManager(args);
         if (url != null)
            log.info("Configuring JDK 1.4 logging with configuration '" + url.toString() + "'");
      }
      catch (XmlBlasterException e) {
         System.err.println("Configuring JDK 1.4 logging output failed: " + e.toString());
      }
   }

   public static int getCounter() { return counter; }

   /**
    * @return the JmxWrapper used to manage the MBean resources
    */
    public final JmxWrapper getJmxWrapper() throws XmlBlasterException {
      return JmxWrapper.getInstance(this);
   }

   /**
    * Check if JMX is activated.
    * @return true if JMX is in use
    */
   public boolean isJmxActivated() {
      try {
         return getJmxWrapper().isActivated();
      }
      catch (XmlBlasterException e) {
         return false;
      }
   }

   /**
    * Send an administrative notification.
    */
   public void sendNotification(NotificationBroadcasterSupport source,
          String msg, String attributeName,
          String attributeType, Object oldValue, Object newValue) {
      // Avoid any log.warning or log.severe to prevent looping alert events
      if (isJmxActivated()) {
         Notification n = new AttributeChangeNotification(source,
            sequenceNumber++,
            System.currentTimeMillis(),
            msg,
            attributeName,
            attributeType,
            oldValue,
            newValue);
         source.sendNotification(n);
      }
   }

   /**
    * JMX support.
    * Start xmlBlaster with <code>java -Dcom.sun.management.jmxremote org.xmlBlaster.Main</code>
    * You can access xmlBlaster from 'jconsole' delivered with JDK1.5 or above.
    * The root node is always the cluster node id.
    * @param contextNode Used to retrieve a unique instance name for the given MBean
    * @param mbean the MBean object instance
    * @return The object name used to register or null on error
    * @since 1.0.5
    * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.jmx.html
    */
   public JmxMBeanHandle registerMBean(ContextNode contextNode, Object mbean) throws XmlBlasterException {
      return getJmxWrapper().registerMBean(contextNode, mbean);
   }

   /**
    * Unregister a JMX MBean.
    * Never throws any exception
    * @param objectName The object you got from registerMBean() of type ObjectName,
    *                   if null nothing happens
    */
   public void unregisterMBean(Object objectName) {
      if (objectName == null) return;
      try {
         if (objectName instanceof JmxMBeanHandle)
            getJmxWrapper().unregisterMBean((JmxMBeanHandle)objectName);
         else
            getJmxWrapper().unregisterMBean((ObjectName)objectName);
      }
      catch (XmlBlasterException e) {
         log.warning("unregisterMBean(" + objectName.toString() + ") failed: " + e.toString());
      }
      catch (Throwable e) {
         log.severe("unregisterMBean(" + objectName.toString() + ") failed: " + e.toString());
      }
   }
   
   public boolean isRegisteredMBean(ContextNode ctxNode) throws XmlBlasterException {
      return getJmxWrapper().isRegistered(ctxNode);
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
   }

   /**
    * private, called from constructor
    * @param args arguments to initilize the property with.
    * @param loadPropFile if loading of xmlBlaster.properties
    *        file should be done, if false no loading of the file is done.
    * @return -1 on error
    * @exception If no Property instance can be created
    */
   private int initProps(String[] args, boolean loadPropFile, boolean doReplace) {
      if (property == null) {
         synchronized (Property.class) {
            if (property == null) {
               try {
                  if (loadPropFile)
                     property = new Property("xmlBlaster.properties", true, args, doReplace);
                  else
                     property = new Property(null, true, args, doReplace);
               }
               catch (XmlBlasterException e) {
                  errorText = ME + ": Error in xmlBlaster.properties: " + e.toString();
                  System.err.println(errorText);
                  try {
                     property = new Property(null, true, args, doReplace);  // initialize without properties file!
                  }
                  catch (XmlBlasterException e2) {
                     errorText = ME + " ERROR: " + e2.toString();
                     System.err.println(errorText);
                     try {
                        property = new Property(null, true, new String[0], doReplace);  // initialize without args
                     }
                     catch (XmlBlasterException e3) {
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
    * Configure JDK 1.4 java.util.logging (only once per JVM-Classloader, multiple Global instances share the same).
    * </p>
    * Switch off xmlBlaster specific logging:
    * <pre>
    * xmlBlaster/java.util.logging=false
    * </pre>
    * </p>
    * Lookup a specific logging.properties:
    * <pre>
    * java.util.logging.config.file=logging.properties
    * </pre>
    * @return The used configuration file (can be used for user notification) or null
    * @throws XmlBlasterException if redirection fails
    */
   private URL initLogManager(String[] args) throws XmlBlasterException {
      if (args == null) return null;
      if (logIsInitialized) return null;

      final String propertyName = "java.util.logging.config.file";

      if ("false".equals(getProperty().get("xmlBlaster/java.util.logging", (String)null))) {
         logIsInitialized = true;
         System.out.println("Switched off logging configuration with 'xmlBlaster/java.util.logging=false'");
         return null;
      }

      FileLocator fl = new FileLocator(this);
      URL url = fl.findFileInXmlBlasterSearchPath(propertyName, "logging.properties");
      if (url == null) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION,
         "Global", "Can't find java.util.logging.config.file=logging.properties");
      }
      try {
         InputStream in = url.openStream();

         LogManager logManager = LogManager.getLogManager();
         logManager.readConfiguration(in);
         in.close();

         // init from command line (or xmlBlaster.properties)
         synchronized (Global.class) {
            if (!logIsInitialized) {
               Map map = this.property.getPropertiesForContextNode(this.contextNode, ContextNode.LOGGING_MARKER_TAG, "__default");
               String defVal = (String)map.get("__default");
               if (defVal != null) {
                  try {
                     Level defaultLevel = Level.parse(defVal);
                     Logger defLogger = logManager.getLogger("");
                     if (defLogger != null) {
                        defLogger.setLevel(defaultLevel);
                        log.info("Setting default log level to '" + defaultLevel.getName() + "'");
                     }
                     else
                        log.warning("Setting default log level to '" + defaultLevel.getName() + "' failed since default log level is null");
                  }
                  catch (Throwable ex) {
                     log.warning("An exception occured when parsing '" + defVal + "' as a log level");
                  }
               }
               Iterator iter = map.entrySet().iterator();

               Logger defLogger = logManager.getLogger("");
               // Handler[] tmpHandlers = defLogger.getHandlers();
               // Handler[] refHandlers = new Handler[tmpHandlers.length];
               Handler[] refHandlers = defLogger.getHandlers();
               for (int i=0; i < refHandlers.length; i++) {
                  refHandlers[i].setLevel(Level.FINEST);
                  Formatter formatter = refHandlers[i].getFormatter();
                  if (formatter instanceof XbFormatter) {
                     XbFormatter xb = (XbFormatter)formatter;
                     xb.setGlobal(this);
                  }
               }

               while (iter.hasNext()) {
                  Map.Entry entry = (Map.Entry)iter.next();
                  String key = (String)entry.getKey();
                  String val = (String)entry.getValue();
                  try {
                     Level level = Level.parse(val);
                     Logger tmpLogger = Logger.getLogger(key);
                     if (tmpLogger != null) {
                        tmpLogger.setLevel(level);
                        tmpLogger.setUseParentHandlers(false);
                        for (int i=0; i < refHandlers.length; i++) {
                           // handlers[i].setLevel(level);
                           tmpLogger.addHandler(refHandlers[i]);
                        }
                        log.info("Setting log level for '" + key + "' to '" + level.getName() + "'");
                     }
                     else
                        log.info("Setting log level for '" + key + "' to '" + level.getName() + "' failed since logger was null");
                  }
                  catch (Throwable ex) {
                     log.warning("An exception occured when parsing '" + val + "' as a log level for '" + key + "'");
                  }
               }
               logIsInitialized = true;
            }
         }
         return url;
      }
      catch (Exception e) {
         throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION,
                   "Global.initLogManager", url.toString(), e);
      }
   }

   /**
    * Get the current loglevel.
    *
    * @param loggerName e.g. "logging" or "/node/heron/logging/org.xmlBlaster.util.Timestamp"
    * @return The logging level, for example "WARNING" or "FINE"
    */
    public Level getLogLevel(String loggerName) throws XmlBlasterException {
       if (loggerName == null || loggerName.length() < 1)
          throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "Illegal loglevel syntax '" + loggerName + "'");
       if (log != null) log.fine("Please implement me");
       return Level.INFO;

/*
       try {
          int start = loggerName.indexOf("[");
          if (start == -1) {
             start = loggerName.indexOf("/"); // JMX interpretes [ as index, so we use info/core syntax there
          }

          if (start != -1) { // Syntax is for example "info[core]"
             int end = loggerName.indexOf("]");
             if (end == -1 ) {
                end = loggerName.length();    // info/auth
             }
             if (start < 1 || end == -1 || end <= (start+1)) {
                throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "Illegal loglevel syntax '" + loggerName + "'");
             }
             String key = loggerName.substring(start+1, end);
             Object obj = logChannels.get(key);
             if (obj == null)
                throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME, "LogChannel '" + key + "' is not known");
             LogChannel log = (LogChannel)obj;
             return log.isLoglevelEnabled(loggerName.substring(0, start));
          }

          return logDefault.isLoglevelEnabled(loggerName);
       }
       catch (JUtilsException e) {
          throw new XmlBlasterException(this, ErrorCode.INTERNAL_UNKNOWN, ME, "", e);
       }
 */
    }

    /**
     * Changes the given logger to given level.
     * @param loggerName e.g. "logging" or "logging/org.xmlBlaster.util.StopWatch"
     * @param level For example "FINE"
     * @return The set level
     * @exception XmlBlasterException if your bool is strange
     */
   public Level changeLogLevel(String loggerName, Level level) throws XmlBlasterException {
      Logger logger = Logger.getLogger(loggerName);
      logger.setLevel(level);
      return level;
   }

   /**
    * Calls init(String[] args), the props keys have no leading "-".
    * @return 1 Show usage, 0 OK, -1 error
    */
   public int init(Map props) {
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
         try { // since JKD 1.4:
            URL url = initLogManager(args);
            if (url != null)
               log.info("Configuring JDK 1.4 logging with configuration '" + url.toString() + "'");
         }
         catch (XmlBlasterException e) {
            System.err.println("Configuring JDK 1.4 logging output failed: " + e.toString());
         }
         return property.wantsHelp() ? 1 : 0;
      }
      catch (XmlBlasterException e) {
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
    * The unique name of this instance.
    * @return Can be null during startup
    */
   public ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * The unique name of this instance.
    * @param contextNode The new node id
    */
   public void setContextNode(ContextNode contextNode) {
      this.contextNode = contextNode;
   }

   /**
    * Helper for the time being to be used on client side by
    * services like SmtpClient.
    * Is filled by XmlBlasterAccess with for example "/node/heron/client/joe/session/1"
    * @return
    */
   public ContextNode getScopeContextNode() {
      if (this.scopeContextNode == null) return getContextNode();
      return this.scopeContextNode;
   }
   public void setScopeContextNode(ContextNode contextNode) {
      this.scopeContextNode = contextNode;
   }

   // see ServerScope for implementationOverrides Global method
   public void doStorageCleanup(JdbcQueue jdbcQueue) {
   }

   /**
    * Check where we are, on client or on server side?
    * engine.Global overwrites this
    * @return false As we are util.Global and running client side
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
    * @return for XBSTORE.XBNODE, typically the cluster.node.id
    */
   public String getDatabaseNodeStr() {
      return getStrippedId();
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
      if (text == null) return null;
      String strippedId = ReplaceVariable.replaceAll(text, "/", "");
      // JMX does not like commas, but we can't introduce this change in 1.0.5
      // as the persistent queue names would change and this is not backward compatible
      //strippedId = ReplaceVariable.replaceAll(strippedId, ",", "_");
      strippedId = ReplaceVariable.replaceAll(strippedId, " ", "_");
      strippedId = ReplaceVariable.replaceAll(strippedId, ".", "_");
      strippedId = ReplaceVariable.replaceAll(strippedId, ":", "_");
      strippedId = ReplaceVariable.replaceAll(strippedId, "[", "_");
      strippedId = ReplaceVariable.replaceAll(strippedId, "]", "_");
      return ReplaceVariable.replaceAll(strippedId, "\\", "");
   }

   /**
    * @see org.xmlBlaster.util.admin.extern.JmxWrapper#validateJmxValue(String)
    */
   public final String validateJmxValue(String value) {
      return JmxWrapper.validateJmxValue(value);
   }

   /**
    * Currently set by enging.Global, used server side only.
    * @param a unique id
    */
   public synchronized void setId(String id) {
      if (id == null) return;
      this.id = id;
      if (getStrippedId() == null) return;
      if (this.contextNode == null) {
         String instanceName = validateJmxValue(getStrippedId());
         this.contextNode = new ContextNode(ContextNode.CLUSTER_MARKER_TAG, instanceName, ContextNode.ROOT_NODE);
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
    * The properties and log channels and ContextNode are copied with a deep copy
    * manipulating these will not affect the original Global.<br />
    * All other attributes are initialized as on startup.
    */
   protected Object clone() {
      // We should not use a ctor for clones, but instead:
      //Global newObject = (Global)super.clone();
      // but our Global ctor uses counter++, so we nevertheless do it like this (breaking Object.clone javadoc that no ctor is called):
      Global g = new Global(Property.propsToArgs(this.property.getProperties()), false, false);
      if (this.contextNode != null) {
         g.setContextNode(new ContextNode(this.contextNode.getClassName(), this.contextNode.getInstanceName(), this.contextNode.getParent()));
      }
      
      if (isServerSide()) {
         g.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, this);
      }
      else {
         Object obj = getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
         if (obj != null)
            g.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, obj);
      }
      
      return g;
   }

   /**
    * Access the environment properties, is never null.
    */
   public final Property getProperty() {
      return (this.property == null) ? new Property() : this.property;
   }

   /**
    * @return the checkpointPlugin
    */
   public I_Checkpoint getCheckpointPlugin() {
      return this.checkpointPlugin;
   }

   /**
    * @param checkpointPlugin the checkpointPlugin to set
    */
   public void setCheckpointPlugin(I_Checkpoint checkpointPlugin) {
      this.checkpointPlugin = checkpointPlugin;
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
      if (log.isLoggable(Level.FINER)) log.finer("getNativeCallbackDriver(" + key + ")");
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
      if (log.isLoggable(Level.FINER)) log.finer("addNativeCallbackDriver(" + key + "," + driver.getName() + ")");
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
      if (log.isLoggable(Level.FINER)) log.finer("removeNativeCallbackDriver(" + key + ")");
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
      synchronized (this.objectMapMonitor) {
         return objectMap.get(key);
      }
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
      synchronized (this.objectMapMonitor) {
         objectMap.put(key, driver);
      }
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
     synchronized (this.objectMapMonitor) {
        objectMap.remove(key);
     }
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
               if (log.isLoggable(Level.FINER)) log.finer("Entering getBootstrapAddress(), trying to resolve one ...");
               this.bootstrapAddress = new Address(this);
               if (log.isLoggable(Level.FINE)) log.fine("Initialized bootstrapAddress to host=" + this.bootstrapAddress.getBootstrapHostname() +
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
            if (log.isLoggable(Level.FINE)) log.fine("Default cb host is " + this.cbHostname);
         }
         catch (java.io.IOException e) {
            log.fine("Can't find default cb hostname: " + e.toString());
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
      if (log.isLoggable(Level.FINER))
         log.finer("Entering accessFromInternalHttpServer(" + ((address==null)?"null":address.getRawAddress()) + ") ...");
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

         if (log.isLoggable(Level.FINE))
            log.fine("Trying internal http server on " +
                               addr.getBootstrapHostname() + ":" + addr.getBootstrapPort() + "" + urlPath);
         java.net.URL nsURL = new java.net.URL("http", addr.getBootstrapHostname(), addr.getBootstrapPort(), urlPath);
         java.io.InputStream nsis = nsURL.openStream();
         byte[] bytes = new byte[4096];
         java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
         int numbytes;
         for (int ii=0; ii<20 && (nsis.available() <= 0); ii++) {
            if (log.isLoggable(Level.FINE)) log.fine("XmlBlaster on host " + addr.getBootstrapHostname() + " and bootstrapPort " + addr.getBootstrapPort() + " returns empty data, trying again after sleeping 10 milli ...");
            Timestamp.sleep(10); // On heavy logins, sometimes available() returns 0, but after sleeping it is OK
         }
         while (nsis.available() > 0 && (numbytes = nsis.read(bytes)) > 0) {
            bos.write(bytes, 0, numbytes);
         }
         nsis.close();
         String data = bos.toString();
         if (log.isLoggable(Level.FINE)) log.fine("Retrieved http data='" + data + "'");
         return data;
      }
      catch(MalformedURLException e) {
         String text = "XmlBlaster not found on host " + addr.getBootstrapHostname() + " and bootstrap port " + addr.getBootstrapPort() + ".";
         log.severe(text + e.toString());
         e.printStackTrace();
         throw new XmlBlasterException(this, ErrorCode.USER_CONFIGURATION, ME+"NoHttpServer", text, e);
      }
      catch(IOException e) {
         if (verbose) log.warning("XmlBlaster not found on host " + addr.getBootstrapHostname() + " and bootstrapPort " + addr.getBootstrapPort() + ": " + e.toString());
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
               log.warning("Can't determine local IP address, try e.g. '-bootstrapHostname 192.168.10.1' on command line: " + e.toString());
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
                  log.warning("Could not load custom classLoaderFactory " + clf + " using StandaloneClassLoaderFactory");
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
         log.warning("Problems during ORB cleanup: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Get the configured SAXParserFactory.
    *
    * <p>
    * The implementation of the SAXParser factory is decided
    * by the property <code>javax.xml.parsers.SAXParserFactory</code>
    * if available in Global, otherwise the JDK1.4 default
    * <code>org.apache.crimson.jaxp.SAXParserFactoryImpl</code>is returned.
    * </p>
    * <p>The JDK 1.5 default would be
    *    <code>com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl</code>
    *
    * @see #getDocumentBuilderFactory()
    */
    public SAXParserFactory getSAXParserFactory() throws XmlBlasterException{
      if ( saxFactory == null) {
         try {
            if (log.isLoggable(Level.FINEST)) log.finest(getProperty().toXml());

            String fac = getProperty().get(
                "javax.xml.parsers.SAXParserFactory", (String)null);
            if (fac == null)  {
               saxFactory = JAXPFactory.newSAXParserFactory();
            }
            else {
               saxFactory = JAXPFactory.newSAXParserFactory(fac);
            }
/*
            String defaultFac = (XmlNotPortable.JVM_VERSION<=14) ?
                  "org.apache.crimson.jaxp.SAXParserFactoryImpl" :
                  "com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl";
            if (isIbmVM()) {
               defaultFac = "org.apache.xerces.jaxp.SAXParserFactoryImpl";
            }

            saxFactory = JAXPFactory.newSAXParserFactory(
               getProperty().get(
                  "javax.xml.parsers.SAXParserFactory", defaultFac));
*/
         } catch (FactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_XML, ME, "SAXParserFactoryError", e);
         } // end of try-catch

      } // end of if ()
      return saxFactory;
   }

   //IBM1.4.2
   // <java.vendor>IBM Corporation</java.vendor>
   // <java.vm.vendor>IBM Corporation</java.vm.vendor>
   // <java.fullversion>J2RE 1.4.2 IBM build cxia32142-20060824 (SR6) (JIT enabled: jitc)</java.fullversion>
   // <java.vm.info>J2RE 1.4.2 IBM build cxia32142-20060824 (SR6) (JIT enabled: jitc)</java.vm.info>
   //IBM1.5
   //  <java.vendor>IBM Corporation</java.vendor>
   //  <java.vm.vendor>IBM Corporation</java.vm.vendor>
   //  <java.fullversion>J2RE 1.5.0 IBM J9 2.3 Linux amd64-64 j9vmxa6423-20060504 (JIT enabled)
   //  <java.vm.info>J2RE 1.5.0 IBM J9 2.3 Linux amd64-64 j9vmxa6423-20060504 (JIT enabled)
   //      J9VM - 20060501_06428_LHdSMr
   //      JIT  - 20060428_1800_r8
   //      GC   - 20060501_AA</java.vm.info>
   //  <java.vm.name>IBM J9 VM</java.vm.name>
   private final boolean isIbmVM() {
      String vm = System.getProperty("java.vm.vendor", "");
      if (vm.indexOf("IBM") != -1) return true;
      return false;
   }

   /**
    * Get the configured  DocumentBuilderFactoryFactory.
    *
    * <p>
    * The implementation of the  DocumentBuilderFactory is decided by the property
    * <code>javax.xml.parsers.DocumentBuilderFactory</code> if available in Global,
    * otherwise the default <code>org.apache.crimson.jaxp.DocumentBuilderFactoryImpl</code>
    * is returned for JDK 1.3 and smaller.
    * </p>
    * Currently only crimson is actually possible to use for JDK 1.3 and JDK 1.4
    * (see xmlBlaster/lib/parser.jar#/META-INF/services setting)
    * </p>
    * <p>
    * For JDK 1.5 the default delivered parser is used:
    * <code>com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl</code>
    * and xmlBlaster/lib/parser.jar and jaxp.jar are obsolete.
    * For JDK 1.5 or higher any DOM Level 3 compliant parser should be OK.
    * </p>
    */
   public DocumentBuilderFactory getDocumentBuilderFactory() throws XmlBlasterException {
      if ( docBuilderFactory == null) {
         try {
            if (log.isLoggable(Level.FINEST)) log.finest(getProperty().toXml());

            String fac = getProperty().get(
               "javax.xml.parsers.DocumentBuilderFactory", (String)null);
            if (fac == null) {
               docBuilderFactory =JAXPFactory.newDocumentBuilderFactory();
            }
            else {
               docBuilderFactory =JAXPFactory.newDocumentBuilderFactory(fac);
            }
/*
            String defaultFac = (XmlNotPortable.JVM_VERSION<=14) ?
                  "org.apache.crimson.jaxp.DocumentBuilderFactoryImpl" :
                  "com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl";
            if (isIbmVM()) {
               defaultFac =
                  "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl";
            }

            docBuilderFactory =JAXPFactory.newDocumentBuilderFactory(
               getProperty().get(
                  "javax.xml.parsers.DocumentBuilderFactory", defaultFac));
*/
               // We need to force "com.sun..." for JDK 1.5 as otherwise
               // the xmlBlaster/lib/parser.jar#/META-INF/services settings would choose crimson
               // As soon as we drop parser.jar we can leave this property==null
         } catch (FactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_XML, ME, "DocumentBuilderFactoryError", e);
         } // end of try-catch
      } // end of if ()
      return docBuilderFactory;
   }
   /**
    * Get the configured  TransformerFactory.
    *
    * <p>The implementation of the   TransformerFactory is decided by the property
    * <code>javax.xml.transform.TransformerFactory</code> if available in Global,
    * otherwise the default <code>org.apache.xalan.processor.TransformerFactoryImpl</code>
    * is returned
    * </p>
    * <p>The JDK 1.5 default would be
    * <code>com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl</code>
    *
    * @see #getDocumentBuilderFactory()
    */
   public TransformerFactory getTransformerFactory() throws XmlBlasterException {
      if ( transformerFactory == null) {
         try {
            String fac = getProperty().get(
                  "javax.xml.transform.TransformerFactory", (String)null);
            if (fac == null) {
               transformerFactory =JAXPFactory.newTransformerFactory();
            }
            else {
               transformerFactory =JAXPFactory.newTransformerFactory(fac);
            }
/*
            String defaultFac = (XmlNotPortable.JVM_VERSION<=14) ?
               "org.apache.xalan.processor.TransformerFactoryImpl" :
               "com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl";
            if (isIbmVM()) {
               defaultFac =
               "org.apache.xalan.processor.TransformerFactoryImpl";
            }

            transformerFactory =JAXPFactory.newTransformerFactory(
               getProperty().get(
                  "javax.xml.transform.TransformerFactory", defaultFac));
*/
         } catch (TransformerFactoryConfigurationError e) {
            throw new XmlBlasterException(this, ErrorCode.RESOURCE_CONFIGURATION_XML, ME, "TransformerFactoryError", e);
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
    * This notation is URLEncoder since JDK 1.4.
    * @param enc If null it defaults to "UTF-8"
    */
   public static String encode(String s, String enc) {
      try {
         return java.net.URLEncoder.encode(s, (enc==null) ? Constants.UTF8_ENCODING : enc);
      } catch (UnsupportedEncodingException e) {
         System.out.println("PANIC in encode(" + s + ", " + enc + "): " + e.toString());
         e.printStackTrace();
         return s;
      }
   }

   /**
    * This notation is URLDecoder since JDK 1.4.
    * @param enc If null it defaults to "UTF-8"
    */
   public static String decode(String s, String enc) {
      try {
         return java.net.URLDecoder.decode(s, (enc==null) ? Constants.UTF8_ENCODING : enc);
      }
      catch (Exception e) {
         System.out.println("PANIC in decode(" + s + ", " + enc + "): " + e.toString());
         e.printStackTrace();
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
   public final I_TimeoutManager getPingTimer() {
      if (this.pingTimer == null) {
         synchronized(this) {
            if (this.pingTimer == null)
               this.pingTimer = new TimeoutPooled("XmlBlaster.PingTimerPooled"); // Note: thread name is changed to DispatchConnection#PING_THREAD_NAME
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
      try {
         //if (log.isLoggable(Level.FINE)) log.fine("Entering finalize");
         shutdown();
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
      try {
         super.finalize();
      }
      catch (Throwable e) {
         e.printStackTrace();
      }
   }

   public void shutdown() {
      if (this.isDoingShutdown) {
         return;
      }
      this.isDoingShutdown = true;

      if (log != null && log.isLoggable(Level.FINE)) log.fine("Destroying util.Global handle");

      /* This is a singleton, so only the last Global instance may do a shutdown
      try {
         getJmxWrapper().shutdown();
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "Ignoring: " + e.toString());
      }
      */

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

   public int getRunlevel() {
      return -1;
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

   /**
    * Prints the stack trace as a String so it can be put on the normal logs.
    * @param ex The exception for which to write out the stack trace. If you pass null it will print the Stack trace of
    * a newly created exception.
    * @return The Stack trace as a String.
    */
   public static String getStackTraceAsString(Throwable ex) {
      // this is just to send the stack trace to the log file (stderr does not go there)
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      PrintStream pstr = new PrintStream(baos);
      if (ex == null)
         ex = new Exception();
      ex.printStackTrace(pstr);
      return new String(baos.toByteArray());
   }

   /**
    * Convenience method which returns the typical environment settings for a LOCAL connection.
    * <p>If you write a native plugin you can use these settings as a base.</p>
    * Don't use for plugins started on runlevel below AvailabilityChecker allows to publish
    * as we don't have a client side queue.
    * @return A string array which you can pass to <tt>this.global = glob.getClone(glob.getNativeConnectArgs());</tt>
    */
   public String[] getNativeConnectArgs() {
      /*
      Problem:
      These settings can't be overwritten in xmlBlaster.properties
      as they are assumed to be from command line which is strongest
      */
      final String[] nativeConnectArgs = {
              "-protocol", "LOCAL",
              "-session.timeout", "0",
              "-dispatch/connection/protocol", "LOCAL",
              "-dispatch/connection/pingInterval", "0",
              "-dispatch/connection/burstMode/collectTime", "0",
              "-dispatch/callback/protocol", "LOCAL",
              "-dispatch/callback/pingInterval", "10000", // For low run levels and persistent connections like DbWatcher
              "-dispatch/callback/retries", "-1",
              "-dispatch/callback/burstMode/collectTime", "0",
              "-queue/connection/defaultPlugin", "RAM,1.0",
              /*"-queue/callback/defaultPlugin", "CACHE,1.0", is already default */
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
      sb.append("Control properties framework:\n");
      sb.append("   -propertyFile <file> Specify an xmlBlaster.properties file to load.\n");
      sb.append("                        The contained settings overwrite a property file found in the xmlBlaster.jar file.\n");
      sb.append("   -property.verbose   0 switches logging off, 2 is most verbose when loading properties on startup [" + Property.DEFAULT_VERBOSE + "].\n");
      sb.append("   -pluginsFile  <file> Specify an xmlBlasterPlugins.xml property file to load.\n");
      sb.append("                        The contained settings overwrite a plugins file found in the xmlBlaster.jar file.\n");
      sb.append("   -java.util.logging.config.file  <file> The JDK 1.4 logging configuration, overwrite the file found in xmlBlaster.jar\n");
      sb.append("                        The default is from xmlBlaster/config/logging.properties\n");
      return sb.toString();
   }

   /** To play with a profiling tool */
   public static void main(String[] args) {
      System.out.println("NO GLOBAL, Hit a key");
      try { System.in.read(); } catch(java.io.IOException e) {}
      Global glob = new Global(args);

      synchronized(glob) {
         System.out.println(ThreadLister.getAllStackTraces());
      }


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
            a.disconnect((DisconnectQos)null);

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
    * @param pluginConfig the pluginConfig used, checks the properties from PluginInfo
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
         ret = getProperty().get(shortKey, ret); // without prefix (global) is weaker than with specific prefix
         ret = getProperty().get(prefix + shortKey, ret);
         if (map != null)
            ret = map.getProperty(shortKey, ret);
         return getProperty().replaceVariableWithException(shortKey, ret);
      }
      catch (XmlBlasterException ex) {
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
    * Checks PluginInfo as well.
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

   /**
    * Reset the cached instance id
    */
   public void resetInstanceId() {
      synchronized(this) {
         this.instanceId = null;
      }
   }

   /**
    * Unique id of the client, changes on each restart.
    * If 'client/joe' is restarted, the instanceId changes.
    * @return id + timestamp, '/client/joe/instanceId/33470080380'
    */
   public String getInstanceId() {
      if (this.instanceId == null) {
         synchronized(this) {
            if (this.instanceId == null) {
               ContextNode node = new ContextNode("instanceId", ""+System.currentTimeMillis(),
                                       getContextNode());
               this.instanceId = node.getAbsoluteName();
            }
         }
      }
      return this.instanceId;
   }

   /**
    * Dumps given amount of messages from queue to file.
    * TODO: This method is only partly implemented
    * @param queue The queue to observe
    * @param numOfEntries Maximum number of messages to dump
    * @param path The path to dump the messages to, it is automatically created if missing.
    * @param label A nice queue name for logging/exceptions
    * @return The file names dumped, including the path
    */
   public String[] peekQueueMessagesToFile(I_Queue queue, int numOfEntries, String path, String label) throws XmlBlasterException {
      if (numOfEntries < 1)
         return new String[] { "Please pass number of messages to peak" };
      if (queue == null)
         return new String[] { "There is no " + label + " queue available" };
      if (queue.getNumOfEntries() < 1)
         return new String[] { "The " + label + " queue is empty" };

      List<I_Entry> list = queue.peek(numOfEntries, -1);

      if (list.size() == 0)
         return new String[] { "Peeking messages from " + label + " queue failed, the reason is not known" };

      if (path != null && path.equalsIgnoreCase("String")) path = null;

      ArrayList<String> tmpList = new ArrayList<String>();
      for (int i=0; i<list.size(); i++) {
         MsgQueueEntry entry = (MsgQueueEntry)list.get(i);
         if (entry.isExpired() || entry.isDestroyed()) continue;

         String fn = entry.getKeyOid() + entry.getUniqueId() + ".xml";
         String xml = null;

         if (entry instanceof MsgQueuePublishEntry) {
            MsgQueuePublishEntry pub = (MsgQueuePublishEntry)entry;
            xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG,
               pub.getMsgUnit(),
               "Try to publish again: java javaclients.script.XmlScript -prepareForPublish true -requestFile 'thisFileName'");
            tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueConnectEntry) {
            xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(null, null, ((MsgQueueConnectEntry)entry).getConnectQosData()), "");
            fn = MethodName.CONNECT.toString() + entry.getUniqueId() + ".xml";
            tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueDisconnectEntry) {
            xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(null, null, ((MsgQueueDisconnectEntry)entry).getDisconnectQos().getData()), "");
            fn = MethodName.DISCONNECT.toString() + entry.getUniqueId() + ".xml";
            tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueEraseEntry) {
            xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(((MsgQueueEraseEntry)entry).getEraseKey().getData(), null, ((MsgQueueEraseEntry)entry).getEraseQos().getData()), "");
            tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueGetEntry) {
             xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(((MsgQueueGetEntry)entry).getGetKey().getData(), null, ((MsgQueueGetEntry)entry).getGetQos().getData()), "");
             tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueSubscribeEntry) {
             xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(((MsgQueueSubscribeEntry)entry).getSubscribeKeyData(), null, ((MsgQueueSubscribeEntry)entry).getSubscribeQosData()), "");
             tmpList.add(fn);
         }
         else if (entry instanceof MsgQueueUnSubscribeEntry) {
             xml = XmlScriptInterpreter.wrapForScripting(XmlScriptInterpreter.ROOT_TAG, new MsgUnit(((MsgQueueUnSubscribeEntry)entry).getUnSubscribeKey().getData(), null, ((MsgQueueUnSubscribeEntry)entry).getUnSubscribeQos().getData()), "");
             tmpList.add(fn);
         }
         else { // TODO: Get a proper dump, here we only dump the queueEntry information but not the message itself
            StringBuffer sb = new StringBuffer(4096);
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            sb.append("\n<xmlBlaster>");
            sb.append("\n <!-- Content dump is not yet implemented -->");
            sb.append(entry.toXml());
            sb.append("\n</xmlBlaster>");
            xml = sb.toString();
         }
         String fullName = XmlScriptInterpreter.dumpToFile(path, fn, xml);
         tmpList.add(fullName);
      }
      return (String[])tmpList.toArray(new String[tmpList.size()]);
   }

   /**
    * Access a file from the CLASSPATH, typically from xmlBlaster.jar
    *
    * It is searched in the directory of the package of the calling java class
    * <tt>org.xmlBlaster.util.http</tt> => <tt>org/xmlBlaster/util/http</tt>
    * @param file The file to lookup
    * @return The byte[] of the found file
    * @exception  IOException  if an I/O error occurs.
    *             or IllegalArgumentException if not found
    */
   public static byte[] getFromClasspath(String file, Object location) {
      try {
         //java.lang.IllegalArgumentException: Can't handle unknown status.html
         //  at org.xmlBlaster.util.Global.getFromClasspath(Global.java:2122)
         //  at org.xmlBlaster.contrib.htmlmonitor.HtmlMonitorPlugin.service(HtmlMonitorPlugin.java:151)
         //  at org.xmlBlaster.util.http.HandleRequest.run(HttpIORServer.java:368)
         // I had to throw it into org/xmlBlaster/contrib/htmlmonitor to be found because location was 'this' instance of HtmlMonitorPlugin
         java.net.URL oUrl = location.getClass().getResource(file); // "favicon.ico"
         if (oUrl != null) {
            InputStream in = oUrl.openStream();

            int size = 10;
            byte[] tmp = new byte[size];
            ByteArrayOutputStream bo = new ByteArrayOutputStream(size);
            while (in.available() > 0) {
               int length = in.read(tmp);
               if (length > 0)
                  bo.write(tmp, 0, length);
            }
            in.close();
            return bo.toByteArray();
         }
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new IllegalArgumentException("Can't find " + file + ": " + e.toString());
      }
      throw new IllegalArgumentException("Can't handle unknown " + file);
   }

   /**
    * Build a nice, human readable string for the size in MB/KB/Bytes.
    * <br><b>Example:</b><br>
    * <code>System.out.println(Memory.DataLenStr(136000));</code><br>
    *  -> "136 KB"
    * @param size is the size in bytes
    * @return a nice readable memory string
    */
   public static final String byteString(long size) {
      // 1060970496 bytes  532742144 bytes
      // 1.060 GBytes      532.742 MByte
      long gBytes = size / 1000000000L;
      long mBytes = size % 1000000000L / 1000000L;
      long kBytes = size % 1000000L / 1000L;
      long bytes = size % 1000L;
      String str;
      if (gBytes != 0) {
         long a = Math.abs(mBytes);
         String z = (a < 10) ? "00" : ((a < 100) ? "0" : "");
         str = "" + gBytes + "." + z + a + " GBytes";
      }
      else {
         if (mBytes != 0) {
            long a = Math.abs(kBytes);
            String z = (a < 10) ? "00" : ((a < 100) ? "0" : "");
            str = "" + mBytes + "." + z + a + " MBytes";
         }
         else
            if (kBytes != 0) {
               long a = Math.abs(bytes);
               String z = (a < 10) ? "00" : ((a < 100) ? "0" : "");
               str = "" + kBytes + "." + z + a + " KBytes";
            }
            else
               str = "" + bytes + " Bytes";
      }
      return str;
   }

   /**
    * http://www.xmlblaster.org/xmlBlaster/doc/api/org/xmlBlaster/util/admin/I_AdminPop3Driver.html#setPollingInterval(long)
    * @param className
    * @param methodName
    * @return http://www.xmlblaster.org/xmlBlaster/doc/api/org/xmlBlaster/util/admin/I_AdminPop3Driver.html#setPollingInterval(long)
    */
   public static String getJavadocUrl(String className, String methodName) {
      String prefix = "http://www.xmlblaster.org/xmlBlaster/doc/api/";
      className = ReplaceVariable.replaceAll(className, ".", "/");
      String url = prefix + className + ".html";
      if (methodName != null)
         url += "#" + methodName;
      return url;
   }

   /**
    * http://www.xmlblaster.org/xmlBlaster/doc/api/org/xmlBlaster/util/admin/I_AdminPop3Driver.html#setPollingInterval(long)
    * @param className
    * @param methodName
    * @return
    */
   public static String getJmxUsageLinkInfo(String className, String methodName) {
      return "\n\nUsage Details:"
            + "\n" + getJavadocUrl(className, methodName);
   }

   /**
    * Returns a persistent map.
    * @param id The id identifying the map. Normally this would be the sessionId. If you pass null or an empty String, then a default map is returned.
    * @return the persistent map.
    * @throws XmlBlasterException
    */
   public Map getPersistentMap(String id) throws XmlBlasterException {
      log.severe("Not yet implemented");
      return null;
      /*
      if (id == null | id.trim().length() < 1)
         id = "defaultPersistentMap";
      Map map = new PersistentMap(this, id, 0L, 0L);
      return map;
      */
   }
   
   /**
    * Do some garbage collect attempts
    */
   public static void gc(int numGc, long sleep) {
      for (int ii=0; ii<numGc; ii++) {
         System.gc();
         try { Thread.sleep(sleep); } catch( InterruptedException i) {}
      }
   }

  /**
   * Access a nice, human readable string with the current RAM memory situation.
   * @return a nice readable memory statistic string
   */
   public static final String getMemoryStatistic() {
      StringBuffer statistic = new StringBuffer();
      statistic.append("Total memory allocated = ");
      statistic.append(byteString(Runtime.getRuntime().totalMemory()));
      statistic.append(".");
      statistic.append(" Free memory available = ");
      statistic.append(byteString(Runtime.getRuntime().freeMemory()));
      statistic.append(".");
      return statistic.toString();
   }
   
   public void putInWeakRegistry(Object key, Object value) {
      synchronized(weakRegistry) {
         if (key != null)
            weakRegistry.put(key, value);
      }
   }

   public Object getFromWeakRegistry(Object key) {
      synchronized(weakRegistry) {
         if (key == null)
            return null;
         return weakRegistry.get(key);
      }
   }
   
   public Object removeFromWeakRegistry(Object key) {
      synchronized(weakRegistry) {
         if (key != null)
            return weakRegistry.remove(key);
      }
      return null;
   }

}
