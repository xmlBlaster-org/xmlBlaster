/*------------------------------------------------------------------------------
Name:      PublishPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id$
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.def.Constants;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * Loads publish() filter plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MimePublishPlugin[MyFilter][1.0]=com.mycompany.MyFilter
 * </pre>
 */
public class PublishPluginManager extends PluginManagerBase implements I_RunlevelListener {

   private final String ME;
   private static final String defaultPluginName = null; // "org.xmlBlaster.engine.mime.demo.DemoFilter
   public static final String pluginPropertyName = "MimePublishPlugin";

   private final Global glob;
   private static Logger log = Logger.getLogger(PublishPluginManager.class.getName());

   /** Map holds the known plugins */
   private final Map pluginMap = new TreeMap();
   /** Cache for up to now arrived message mime types */
   private final Map mimeCache = new TreeMap();
   /** A dummy object which we put into the mimeCache if no plugins are found */
   private final Object dummyObject = new Object();
   /** To avoid huge caches if every message sends crazy different mime types */
   private final int maxMimeCacheSize;

   public PublishPluginManager(Global glob) throws XmlBlasterException {
      super(glob);
      this.glob = glob;
      this.ME = "PublishPluginManager" + this.glob.getLogPrefixDashed();

      this.maxMimeCacheSize = glob.getProperty().get("MimePublishPlugin.maxMimeCacheSize", 1000);
      glob.getRunlevelManager().addRunlevelListener(this);
   }

   /** Called from base class after creation */
   public void postInstantiate(I_Plugin plugin_, PluginInfo pluginInfo) {
      I_PublishFilter plugin = (I_PublishFilter)plugin_;
      plugin.initialize(glob);
      if (pluginMap.get(plugin.getName()) != null)
         log.warning("Instantiating publish filter plugin '" + plugin.getName() + "' again, have you configured it twice?");
         
      pluginMap.put(plugin.getName(), plugin);
      if (log.isLoggable(Level.FINE)) log.fine("Instantiated publish filter plugin '" + plugin.getName() + "'");
   }

   /**
    * Instantiate all given plugins from xmlBlaster.properties. 
    * <p />
    * E.g.
    * <code>MimePublishPlugin[PublishLenChecker][1.0]=org.xmlBlaster.engine.mime.demo.PublishLenChecker,DEFAULT_MAX_LEN=200</code>
    * <p />
    * If invoked again, the loaded plugins are reset and reloaded with the current property settings
    */
   public synchronized void initializePlugins() throws XmlBlasterException {
      pluginMap.clear();
      mimeCache.clear();
      Map map = glob.getProperty().get(pluginPropertyName, (Map)null);
      if (map != null) {
         Iterator iterator = map.keySet().iterator();
         while (iterator.hasNext()) {
            String key = (String)iterator.next(); // contains "PublishFilterLen:1.0", the type and version are separated by a ":"
            String type = key.substring(0, key.indexOf(":"));
            String version = key.substring(key.indexOf(":")+1);
            I_PublishFilter plugin = (I_PublishFilter)getPluginObject(type, version);
            if (plugin == null) {
               log.severe("Problems accessing plugin " + PublishPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            }
         }
         log.info("Instantiated " + pluginMap.size() + " publish filter plugins");
      }
      else {
         log.info("No plugins configured with 'MimePublishPlugin'");
      }
   }

   /**
    * Are there any plugins?
    */
   public final boolean hasPlugins() {
      return pluginMap.size() > 0;
   }

   /**
    * @return null if no plugin for this mime type found, otherwise a map with I_PublishFilter
    * objects (= matching plugins) is returned
    */
   public synchronized Map findMimePlugins(String mime, String mimeExtended) {
      if (pluginMap.size() == 0)
         return null;

      if (mime == null) {
         Thread.dumpStack();
         throw new IllegalArgumentException("You must pass a valid contentMime type");
      }
      if (mimeExtended == null || mimeExtended.length() < 1) mimeExtended = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
      String key = mime + mimeExtended;

      // First we check the cache ...
      Object ret = mimeCache.get(key);
      if (ret != null && ret instanceof Map)
         return (Map)ret;
      else if (ret != null && ret instanceof Object)
         return null;  // dummy object to indicate that such messages have no plugin

      if (log.isLoggable(Level.FINE)) log.fine("mime=" + mime + " mimeExtended=" + mimeExtended + " not found in cache, searching the plugin ...");
      
      if (mimeCache.size() > maxMimeCacheSize) {
         log.warning("Cache has reached max size of " + maxMimeCacheSize + " entries, erasing entries");
         mimeCache.clear(); // This way we avoid very old entries and memory exhaust
      }

      // This is the first message of this mime type, add to cache ...
      
      Iterator iterator = pluginMap.values().iterator();
      while (iterator.hasNext()) {
         I_PublishFilter plugin = (I_PublishFilter)iterator.next();
         String[] mimes = plugin.getMimeTypes();
         String[] extended = plugin.getMimeExtended();
         if (mimes == null || extended == null || mimes.length != extended.length) {
            String text = "Your plugin '" + plugin.getName() + "' must pass a valid mime type with a corresponding mimeExtended type, plugin ignored.";
            log.severe(text);
            Thread.dumpStack();
            throw new IllegalArgumentException(text);
         }
         for (int ii=0; ii<mimes.length; ii++) {
            if (mimes[ii].equals("*") ||
                mimes[ii].equals(mime) && 
                  (extended[ii].equals("*") || extended[ii].equals(mimeExtended))) {
               // Ok, found a plugin, add it to cache
               Map plugins = (Map)mimeCache.get(key);
               if (plugins == null) { // we need a multimap, sadly JDK does not offer it, so we use a map in the map.
                  plugins = new TreeMap();
                  mimeCache.put(key, plugins);
               }
               plugins.put(plugin.getName(), plugin);
               log.info("mime=" + mime + " mimeExtended=" + mimeExtended + " added to cache with plugin=" + plugin.getName());
               break;
            }
         }
      }

      Object plugins = mimeCache.get(key);
      if (plugins == null) {
         mimeCache.put(key, dummyObject);
         log.info("mime=" + mime + " mimeExtended=" + mimeExtended + " added to cache, no plugin is available");
         return null;
      }
      if (plugins instanceof Map)
         return (Map)plugins;
      return null;
   }

   /**
   * @return The name of the property in xmlBlaster.property "MimePublishPlugin"
   * for "MimePublishPlugin[demo][1.0]"
   */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return defaultPluginName;
   }

   public void shutdown() {
      Iterator iterator = pluginMap.values().iterator();
      while (iterator.hasNext()) {
         I_PublishFilter plugin = (I_PublishFilter)iterator.next();
         plugin.shutdown();
      }
      pluginMap.clear();
      mimeCache.clear();
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            initializePlugins();
         }
      }
      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown();
         }
      }
   }
}
