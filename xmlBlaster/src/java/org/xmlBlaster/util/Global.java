/*------------------------------------------------------------------------------
Name:      Global.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Properties for xmlBlaster, using org.jutils
Version:   $Id: Global.java,v 1.12 2002/05/11 19:17:57 ruff Exp $
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
import org.xmlBlaster.client.PluginLoader;

import java.util.Properties;

import java.applet.Applet;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.net.MalformedURLException;
import java.io.IOException;
import java.util.Hashtable;


/**
 * Global variables to avoid singleton. 
 *
 * @see classtest.GlobalTest
 */
public class Global implements Cloneable
{
   private static Global firstInstance = null;

   private final static String ME = "Global";
   private String ip_addr = null;
   private String id = "";

   /**
    * The IANA registered xmlBlaster port,
    * see <a href="http://www.iana.org/assignments/port-numbers">IANA Port Numbers</a>
    * and <a href="http://andrew.triumf.ca/cgi-bin/port">Network Service Query</a>.
    * <pre>
    *  xmlblaster      3412/tcp   xmlBlaster
    *  xmlblaster      3412/udp   xmlBlaster
    *  #                          Marcel Ruff <ruff@swand.lake.de> February 2002
    * </pre>
    */
   public static final int XMLBLASTER_PORT = 3412;

   private String[] args;
   private Property property = null;

   // deprecated
   protected org.xmlBlaster.util.Log log;

   private /*final*/ Map nativeCallbackDriverMap;
   /** Store objecte in the scope of one client connection or server instance */
   private /*final*/ Map objectMap;
   private Address bootstrapAddress = null;
   private PluginLoader clientSecurityLoader = null;

   private Hashtable logChannels = new Hashtable();
   private LogChannel logDefault = null;

   /**
    * Constructs an initial Global object. 
    */
   public Global()
   {
      System.out.println("######Global constructor empty");
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
      }
      this.args = new String[0];
      initProps(this.args);
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
      System.out.println("######Global constructor args");
      synchronized (Global.class) {
         if (this.firstInstance == null)
            this.firstInstance = this;
      }
      initProps(args);
      logDefault = new LogChannel(null, getProperty());
      log = new org.xmlBlaster.util.Log(); // old style
      initLog(logDefault);
      nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap());
      objectMap = Collections.synchronizedMap(new HashMap());
      init(args);
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

      lc.setDefaultLogLevel();

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
         return true;
      }
      return false;
   }

   /**
    * @param if null, the default log channel is returned
    * @see #addLogChannel(String, LogChannel)
    */
   public LogChannel getLog(String key) {
      if (key == null)
         return logDefault;
      return (LogChannel)logChannels.get(key);
   }

   /**
    * Access the logging class. 
    * @deprecated Use getLog(String) instead
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
                  System.err.println(ME + ": Error in xmlBlaster.properties: " + e.toString());
                  try {
                     property = new Property(null, true, args, true);  // initialize without properties file!
                  }
                  catch (JUtilsException e2) {
                     System.err.println(ME + " ERROR: " + e2.toString());
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
         logDefault.initialize(property);
         // TODO: loop through logChannels Hashtable!!!

         // Old style:
         log.setLogLevel(property);   // Initialize logging as well.
         log.initialize(this);

         return property.wantsHelp() ? 1 : 0;
      } catch (JUtilsException e) {
         System.err.println(ME + " ERROR: " + e.toString()); // Log probably not initialized yet.
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
    * Currently set by enging.Global, used server side only. 
    * @param a unique id
    */
   public void setId(String id) {
      this.id = id;
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
         g.property = (Property)this.property.clone();
         //g.logDefault = 
         g.logChannels = (Hashtable)this.logChannels.clone();
         g.nativeCallbackDriverMap = Collections.synchronizedMap(new HashMap()); // (HashMap)((HashMap)this.nativeCallbackDriverMap).clone();
         g.objectMap = Collections.synchronizedMap(new HashMap());
         g.bootstrapAddress = null;
         g.clientSecurityLoader = null;
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
         bootstrapAddress = new Address(this);
         boolean supportOldStyle = true; // for a while we support the old style -iorHost and -iorPort
         if (supportOldStyle) {
            String iorHost = getProperty().get("iorHost", getLocalIP());
            int iorPort = getProperty().get("iorPort", XMLBLASTER_PORT);
            bootstrapAddress.setHostname(getProperty().get("hostname", iorHost));
            bootstrapAddress.setPort(getProperty().get("port", iorPort));
         }
         else {
            bootstrapAddress.setHostname(getProperty().get("hostname", getLocalIP()));
            bootstrapAddress.setPort(getProperty().get("port", XMLBLASTER_PORT));
         }
         bootstrapAddress.setAddress("http://" + bootstrapAddress.getHostname() + ":" + bootstrapAddress.getPort());
      }
      return bootstrapAddress;
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
      Address addr = address;
      if (addr != null && addr.getPort() > 0) {
         if (addr.getHostname() == null || addr.getHostname().length() < 1) {
            addr.setHostname(getLocalIP());
         }
      }
      else {
         addr = getBootstrapAddress();
      }

      if (logDefault.CALL) logDefault.call(ME, "Trying internal http server on " + addr.getHostname() + ":" + addr.getPort());
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
    * Get a usage string. 
    * <p />
    * Set the verbosity when loading properties (outputs with System.out).
    * <p />
    * 0=nothing, 1=info, 2=trace, configure with
    * <pre>
    * java -Dproperty.verbose 2
    *
    * java MyApp -property.verbose 2
    * </pre>
    */
   public static final String usage()
   {
      String text = "";
      text += "Logging of Properties:\n";
      text += "   -property.verbose   0 switches logging off, 2 is most verbose when loading properties on startup[" + Property.DEFAULT_VERBOSE + "].\n";
      return text;
   }
}
