/*------------------------------------------------------------------------------
Name:      PluginManagerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Baseclass to load plugins.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.classloader.ClassLoaderFactory;
import org.xmlBlaster.util.classloader.PluginClassLoader;

import java.util.Hashtable;
import java.util.Vector;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.net.URL;

/**
 * Base class to load plugins.
 * <p />
 * A typical syntax in the xmlBlaster.properties file is:
 * <pre>
 *   MimeSubscribePlugin[ContentLenFilter][1.0]=\
 *       org.xmlBlaster.engine.mime.demo.ContentLenFilter,\
 *       DEFAULT_MAX_LEN=200,DEFAULT_MIN_LEN=20,classpath=mime.jar
 * </pre>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author W. Kleinertz (wkl) H. Goetzger
 * @author <a href="mailto:Konrad.Krafft@doubleslash.de">Konrad Krafft</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class PluginManagerBase implements I_PluginManager {

   private static String ME = "PluginManagerBase";
   private Hashtable pluginCache; // currently loaded plugins  (REMOVE???)
   protected final Global glob;
   private final LogChannel log;
   public final static String NO_PLUGIN_TYPE = "undef";

   public PluginManagerBase(org.xmlBlaster.util.Global glob) {
      this.glob = glob;
      this.log = glob.getLog("classloader");
   }

   protected Global getGlobal() {
      return this.glob;
   }

   /**
    * @param type and version with comma separator e.g. "RMI,1.0"
    */
   public I_Plugin getPluginObject(String typeVersion) throws XmlBlasterException {
      if (typeVersion == null)
         return null;
      String type_;
      String version_;
      int i = typeVersion.indexOf(',');
      if (i==-1) {  // version is optional
         version_ = null;
         type_ = typeVersion;
      }
      else {
         version_ = typeVersion.substring(i+1);
         type_ = typeVersion.substring(0,i);
      }
      return getPluginObject(type_, version_);
   }

   /**
    * Return a specific plugin, if one is loaded already it is taken from cache. 
    * <p/>
    * This code is thread save.
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request or null if type=="undef"
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException {
      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);
      return getPluginObject(pluginInfo);
   }

   /**
    * Return a specific plugin, if one is loaded already it is taken from cache. 
    * <p/>
    * This code is thread save.
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request or null if type=="undef"
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(PluginInfo pluginInfo) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".getPluginObject()", "Loading plugin " + pluginInfo.toString());
      I_Plugin plug = null;

      if (pluginInfo.ignorePlugin()) return null;

      synchronized (this) {
         // check in hash if plugin is instantiated already
         plug = this.glob.getPluginRegistry().getPlugin(pluginInfo.getId());
         if (plug!=null) return plug;
         // not in hash, instantiate plugin
         plug = instantiatePluginFirstPhase(pluginInfo, true);
      }
      if (plug == null) return null;
      synchronized(plug) {
         return instantiatePluginSecondPhase(plug, pluginInfo);
      }
   }

   public I_Plugin getFromPluginCache(String id) {
      if (id == null) return null;
      return this.glob.getPluginRegistry().getPlugin(id);
   }

   public I_Plugin removeFromPluginCache(String id) {
      if (id == null) return null;
      return this.glob.getPluginRegistry().unRegister(id);
   }

   /**
    * Is called after a plugin in instantiated, allows the base class to do specific actions.
    * Is NOT called when plugin got from cache.
    */
   protected void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException {
   }

   /**
    * @param type can be null
    * @param version can be null
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return null;
   }

   /**
    * Tries to return an instance of the default plugin.
    */
   public I_Plugin getDummyPlugin() throws XmlBlasterException {
      return getPluginObject(null, null);
   }

   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   protected String getPluginPropertyName() {
      return null;
   }

   public String getName() {
      return getPluginPropertyName();
   }

   /**
    * @return e.g. "Security.Server.Plugin[simple][1.0]"
    */
   public final String createPluginPropertyKey(String type, String version) {
      StringBuffer buf = new StringBuffer(80);
      buf.append(getPluginPropertyName());
      if (type != null)
         buf.append("[").append(type).append("]");
      if (version != null)
         buf.append("[").append(version).append("]");
      return buf.toString();
   }

   /**
    * Create a plugin instance <b>without</b> caching it. 
    *
    * @see #instantiatePlugin(PluginInfo, boolean false)
    */
   protected I_Plugin instantiatePlugin(PluginInfo pluginInfo) throws XmlBlasterException {
      return instantiatePlugin(pluginInfo, false);
   }

   /**
    * Loads a plugin.
    *
    * @param pluginInfo Contains the plugin information
    * @param usePluginCache If true the plugin is remembered in our cache and e.g. retrievable with getPluginObject()
    *
    * @return I_Plugin or null if plugin type is set to "undef"
    *
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_Plugin instantiatePlugin(PluginInfo pluginInfo, boolean usePluginCache) throws XmlBlasterException
   {
      I_Plugin plugin = instantiatePluginFirstPhase(pluginInfo, usePluginCache);
      if (plugin != null) {
         return instantiatePluginSecondPhase(plugin, pluginInfo);
      }
      return null;
   }


   private I_Plugin instantiatePluginFirstPhase(PluginInfo pluginInfo, boolean usePluginCache) 
      throws XmlBlasterException {
      // separate parameter and plugin name

      if (pluginInfo.ignorePlugin()) return null;

      I_Plugin plugin = null;
      String pluginName = pluginInfo.getClassName();
      if (pluginName == null) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME,
               "The plugin class name is null, please check the property setting of '" + pluginInfo.toString() + "'");
      }
      try {
         ClassLoaderFactory factory = glob.getClassLoaderFactory();
         if (factory != null) {
            if (log.TRACE) log.trace(ME, "useXmlBlasterClassloader=true: Trying Class.forName('" + pluginName + "') ...");

            PluginClassLoader myLoader = factory.getPluginClassLoader(pluginInfo);
            if (log.TRACE) log.trace(ME, "Found " + myLoader.getURLs().length + " plugin specific jar files for '" + pluginName + "' preferenced by xmlBlaster classLoader");

            plugin = (I_Plugin)myLoader.loadClass(pluginName).newInstance();
            if (log.TRACE) log.trace(ME, "Found I_Plugin '" + pluginName + "', loaded by PluginClassLoader");
         }
         else { // Use JVM default class loader:
           Class cl = java.lang.Class.forName(pluginName);
           plugin = (I_Plugin)cl.newInstance();
         }
         if (usePluginCache) {
            this.glob.getPluginRegistry().register(pluginInfo.getId(), plugin);
         }
         return plugin;
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "instantiatePlugin for() '" + pluginName + "' failed: " + e.getMessage());
         throw e;
      }
      catch (IllegalAccessException e) {
         log.error(ME, "The plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin: " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME+".NoClass", "The Plugin class '" + pluginName + "' is not accessible\n -> check the plugin name and/or the CLASSPATH to the plugin", e);
      }
      catch (SecurityException e) {
         log.error(ME, "No right to access the plugin class or initializer '" + pluginName + "': " + e.toString());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME+".NoAccess", "No right to access the plugin class or initializer '" + pluginName + "'", e);
      }
      catch (Throwable e) {
         log.error(ME, "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file: \n'" + e.toString() + "'");
         e.printStackTrace();
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME+".Invalid", "The plugin class or initializer '" + pluginName + "' is invalid\n -> check the plugin name and/or the CLASSPATH to the driver file", e);
      }
   }


   private I_Plugin instantiatePluginSecondPhase(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException {
      // Initialize the plugin
      try {
         plugin.init(glob, pluginInfo);
         postInstantiate(plugin, pluginInfo);
         if (log.TRACE) log.trace(ME, "Plugin '" + pluginInfo.getId() + " successfully initialized.");
         //log.info(ME, "Plugin " + pluginInfo.toString() + "=" + pluginName + " successfully initialized.");
      } catch (XmlBlasterException e) {
         //log.error(ME, "Initializing of plugin " + plugin.getType() + " failed:" + e.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME+".NoInit", "Initializing of plugin " + plugin.getType() + " failed:" + e.getMessage());
      }
      return plugin;
   }

   /**
    * Plugin with type=="undef" are ignored
    */
   public final static boolean ignorePlugin(String typeVersion) {
      if (NO_PLUGIN_TYPE.equalsIgnoreCase(typeVersion.trim()) || "undef,1.0".equalsIgnoreCase(typeVersion.trim()))
         return true;
      return false;
   }

   public void shutdown() {
      if (this.pluginCache != null)
         this.pluginCache.clear();
   }

}
