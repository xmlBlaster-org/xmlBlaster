/*------------------------------------------------------------------------------
Name:      PublishPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: PublishPluginManager.java,v 1.11 2002/06/15 16:05:31 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.I_RunlevelListener;
import org.xmlBlaster.engine.RunlevelManager;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;

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

   private static final String ME = "PublishPluginManager";
   private static final String defaultPluginName = null; // "org.xmlBlaster.engine.mime.demo.DemoFilter
   public static final String pluginPropertyName = "MimePublishPlugin";

   private final Global glob;
   private final LogChannel log;

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
      this.log = this.glob.getLog("mime");
      this.maxMimeCacheSize = glob.getProperty().get("MimePublishPlugin.maxMimeCacheSize", 1000);
      glob.getRunlevelManager().addRunlevelListener(this);
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
            if (plugin != null) {
               plugin.initialize(glob);
               if (pluginMap.get(plugin.getName()) != null)
                  log.warn(ME, "Instantiating publish filter plugin '" + plugin.getName() + "' again, have you configured it twice?");
                  
               pluginMap.put(plugin.getName(), getPluginObject(type, version));
               if (log.TRACE) log.trace(ME, "Instantiated publish filter plugin '" + plugin.getName() + "'");
            }
            else {
               log.error(ME, "Problems accessing plugin " + PublishPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            }
         }
         log.info(ME, "Instantiated " + pluginMap.size() + " publish filter plugins");
      }
      else {
         log.info(ME, "No plugins configured with 'MimePublishPlugin'");
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

      if (mime == null || mimeExtended == null) {
         Thread.currentThread().dumpStack();
         throw new IllegalArgumentException("You must pass a valid contentMime and contentMimeExtended type");
      }
      String key = mime + mimeExtended;

      // First we check the cache ...
      Object ret = mimeCache.get(key);
      if (ret != null && ret instanceof Map)
         return (Map)ret;
      else if (ret != null && ret instanceof Object)
         return null;  // dummy object to indicate that such messages have no plugin

      if (log.TRACE) log.trace(ME, "mime=" + mime + " mimeExtended=" + mimeExtended + " not found in cache, searching the plugin ...");
      
      if (mimeCache.size() > maxMimeCacheSize) {
         log.warn(ME, "Cache has reached max size of " + maxMimeCacheSize + " entries, erasing entries");
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
            log.error(ME, text);
            Thread.currentThread().dumpStack();
            throw new IllegalArgumentException(text);
         }
         for (int ii=0; ii<mimes.length; ii++) {
            if (mimes[ii].equals("*") || mimes[ii].equals(mime) && (extended[ii].equals("*") || extended[ii].equals(mimeExtended))) {
               // Ok, found a plugin, add it to cache
               Map plugins = (Map)mimeCache.get(key);
               if (plugins == null) { // we need a multimap, sadly JDK does not offer it, so we use a map in the map.
                  plugins = new TreeMap();
                  mimeCache.put(key, plugins);
               }
               plugins.put(plugin.getName(), plugin);
               log.info(ME, "mime=" + mime + " mimeExtended=" + mimeExtended + " added to cache with plugin=" + plugin.getName());
               break;
            }
         }
      }

      Object plugins = mimeCache.get(key);
      if (plugins == null) {
         mimeCache.put(key, dummyObject);
         log.info(ME, "mime=" + mime + " mimeExtended=" + mimeExtended + " added to cache, no plugin is available");
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

   public void shutdown(boolean force) {
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
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            initializePlugins();
         }
      }
      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown(force);
         }
      }
   }
}
