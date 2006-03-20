/*------------------------------------------------------------------------------
Name:      AccessPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.xmlBlaster.util.def.Constants;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Iterator;

/**
 * Loads subscribe()/get() filter plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MimeAccessPlugin[MyFilter][1.0]=com.mycompany.MyFilter
 * </pre>
 */
public class AccessPluginManager extends PluginManagerBase implements I_RunlevelListener {

   private final String ME;
   public static final String pluginPropertyName = "MimeAccessPlugin";

   private final ServerScope glob;
   private static Logger log = Logger.getLogger(AccessPluginManager.class.getName());

   public AccessPluginManager(ServerScope glob)
   {
      super(glob);
      this.glob = glob;
      this.ME = "AccessPluginManager" + this.glob.getLogPrefixDashed();

      glob.getRunlevelManager().addRunlevelListener(this);
   }

   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * It is returned from cache if loaded already.
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The AccessFilter for this type and version or null if none is specified
    */
   public I_AccessFilter getPlugin(String type, String version) throws XmlBlasterException {
      return (I_AccessFilter)getPluginObject(type, version);
   }

   /**
    * Called by PluginManagerBase.instantiatePluginSecondPhase()
    */
   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {
      ((I_AccessFilter)plugin).initialize(glob);
   }

   /**
   * @return The name of the property in xmlBlaster.property "MimeAccessPlugin"
   * for "MimeAccessPlugin[demo][1.0]"
   */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      if (type != null) {
         if (type.equals("GnuRegexFilter"))
            return "org.xmlBlaster.engine.mime.regex.GnuRegexFilter";
      }
      return null;
   }

// here are extensions for MIME based plugin selection:

   private final Map accessFilterMap = Collections.synchronizedMap(new HashMap());

   /**
    * Get access filter object from cache, based on MIME type. 
    * @return null if not applicable
    */
   public final I_AccessFilter getAccessFilter(String type, String version, String mime, String mimeExtended)
   {
      if (mimeExtended == null || mimeExtended.length() < 1) {
         mimeExtended = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         if (log.isLoggable(Level.FINE)) log.fine("Needed to set empty mimeExtended to default=" + mimeExtended);
      }

      if (log.isLoggable(Level.FINER)) log.finer("Trying to find plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended);
      try {
         // Try to find it in the cache...
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Found filter for key=" + key.toString());
            return (I_AccessFilter)obj;
         }
         if (log.isLoggable(Level.FINE)) log.fine("No filter for key=" + key.toString());

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version).append("*");
         obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Found filter for key=" + key.toString());
            return (I_AccessFilter)obj;
         }
         if (log.isLoggable(Level.FINE)) log.fine("No filter for key=" + key.toString());

         // Check if the plugin is loaded already
         key.setLength(0);
         key.append(type).append(version);
         obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Filter plugin type=" + type + " version=" + version + " is loaded but does not support mime=" + mime + " mimeExtended=" + mimeExtended);
            return null; // Plugin is loaded but does not support the mime types
         }

         log.info("Going to load filter plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended);

         if (addAccessFilterPlugin(type, version)) { // try to load it
            // try again if our mime type matches:
            key.setLength(0);
            key.append(type).append(version).append(mime).append(mimeExtended);
            obj = accessFilterMap.get(key.toString());
            if (obj != null) {
               if (log.isLoggable(Level.FINE)) log.fine("Found filter for key=" + key.toString());
               return (I_AccessFilter)obj;
            }

            // Check if the plugin is for all mime types
            key.setLength(0);
            key.append(type).append(version).append("*");
            obj = accessFilterMap.get(key.toString());
            if (obj != null) {
               if (log.isLoggable(Level.FINE)) log.fine("Found filter for key=" + key.toString());
               return (I_AccessFilter)obj;
            }
         }
         if (log.isLoggable(Level.FINE)) log.fine("There is no plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended + " available");
         return null;

      } catch (Exception e) {
         log.severe("Problems accessing subscribe filter [" + type + "][" + version +"] mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_AccessFilter)null;
      }
   }

   /**
    * Invoked on new subscription or get() invocation, loads plugin. 
    * You have to check yourself if it is loaded already
    * @return true if initially loaded, and false if was loaded already or on error
    */
   public final boolean addAccessFilterPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = accessFilterMap.get(key.toString());
      if (obj != null) {
         if (log.isLoggable(Level.FINE)) log.fine("Access filter '" + key.toString() + "' is loaded already");
         return false;
      }

      try {
         I_AccessFilter filterPlugin = getPlugin(type, version);
         if (filterPlugin == null) {
            log.severe("Problems accessing plugin " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            return false;
         }

         // filterPlugin.initialize(glob); is done via PluginManagerBase -> postInstantiate()

         accessFilterMap.put(key.toString(), filterPlugin); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = filterPlugin.getMimeTypes();
         String[] mimeExtended = filterPlugin.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               log.severe("Access plugin manager [" + type + "][" + version +"]: Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            if (mime[ii] == null || mime[ii].length() < 1 || mime[ii].equals("*"))
               key.append(type).append(version).append("*");
            else
               key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            accessFilterMap.put(key.toString(), filterPlugin);
            log.info("Loaded access filter for mime types '" + key.toString() + "' to cache");
            key.setLength(0);
         }

         return true;
      } catch (Throwable e) {
         log.severe("Problems accessing subscribe plugin manager, can't instantiate " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"]: " + e.toString());
         e.printStackTrace();
      }
      return false;
   }

   public void shutdown() {
      Iterator iterator = accessFilterMap.values().iterator();
      while (iterator.hasNext()) {
         I_AccessFilter plugin = (I_AccessFilter)iterator.next();
         plugin.shutdown();
      }
      accessFilterMap.clear();
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
            //initializePlugins();
         }
      }
      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown();
         }
      }
   }
}
