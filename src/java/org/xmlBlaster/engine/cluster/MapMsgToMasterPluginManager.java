/*------------------------------------------------------------------------------
Name:      MapMsgToMasterPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id$
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.def.Constants;

import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Collections;

/**
 * Loads the cluster plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MapMsgToMasterPlugin[MyClusterMapper][1.0]=com.mycompany.MyClusterMapper
 * </pre>
 */
public class MapMsgToMasterPluginManager extends PluginManagerBase {

   private static final String ME = "MapMsgToMasterPluginManager";
   /**
    * The default plugin "org.xmlBlaster.engine.cluster.simpledomain.DomainToMaster" 
    * is loaded if not otherwise specified.
    */
   private static final String defaultPluginName = "org.xmlBlaster.engine.cluster.simpledomain.DomainToMaster";
   /** Entry name for xmlBlaster.properties: "MapMsgToMasterPlugin" */
   public static final String pluginPropertyName = "MapMsgToMasterPlugin";

   private final ServerScope glob;
   private static Logger log = Logger.getLogger(MapMsgToMasterPluginManager.class.getName());
   private final ClusterManager clusterManager;

   private final Map mapMsgToMasterIdMap = Collections.synchronizedMap(new HashMap());

   public MapMsgToMasterPluginManager(ServerScope glob, ClusterManager clusterManager) {
      super(glob);
      this.glob = glob;

      this.clusterManager = clusterManager;
   }

   /**
    * Return a specific MIME based message plugin from cache (initialize it on first request). 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The MapMsgToMasterId for this type and version or null if none is specified
    */
   private final I_MapMsgToMasterId getPlugin(String type, String version) throws XmlBlasterException {
      return (I_MapMsgToMasterId)getPluginObject(type, version);
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {
      ((I_MapMsgToMasterId)plugin).initialize(glob, clusterManager);
   }

   /**
   * @return The name of the property in xmlBlaster.property "MapMsgToMasterPlugin"
   * for "MapMsgToMasterPlugin[demo][1.0]"
   */
   protected final String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * @return please return your default plugin classname or null if not specified
    */
   public final String getDefaultPluginName(String type, String version) {
      return defaultPluginName;
   }

   /**
    * Access cluster domain mapping object from cache. 
    */
   public final I_MapMsgToMasterId getMapMsgToMasterId(String type, String version, String mime, String mimeExtended)
   {
      try {
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = mapMsgToMasterIdMap.get(key.toString());
         if (obj != null)
            return (I_MapMsgToMasterId)obj;

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version).append("*");
         obj = mapMsgToMasterIdMap.get(key.toString());
         if (obj != null)
            return (I_MapMsgToMasterId)obj;

         return addMapMsgToMasterIdPlugin(type, version); // try to load it

      } catch (Exception e) {
         log.severe("Problems accessing cluster domain mapping " + createPluginPropertyKey(type, version) + " mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_MapMsgToMasterId)null;
      }
   }

   /**
    * Invoked on new subscription or get() invocation, loads plugin. 
    * @return null if not found
    */
   private final I_MapMsgToMasterId addMapMsgToMasterIdPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = mapMsgToMasterIdMap.get(key.toString());
      if (obj != null) {
         if (log.isLoggable(Level.FINE)) log.fine("Plugin '" + key.toString() + "' is loaded already");
         return (I_MapMsgToMasterId)obj;
      }

      try {
         I_MapMsgToMasterId plugin = getPlugin(type, version);
         if (plugin == null) {
            log.severe("Problems accessing plugin " + createPluginPropertyKey(type, version) + ", please check your configuration");
            return null;
         }

         mapMsgToMasterIdMap.put(key.toString(), plugin); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = plugin.getMimeTypes();
         String[] mimeExtended = plugin.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               log.severe(createPluginPropertyKey(type, version) + ": Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            mapMsgToMasterIdMap.put(key.toString(), plugin);
            log.info("Loaded '" + key.toString() + "'");
            key.setLength(0);
         }

         return plugin;
      } catch (Throwable e) {
         log.severe("Problems accessing cluster domain mapping plugin manager, can't instantiate " + createPluginPropertyKey(type, version) + ": " + e.toString());
         e.printStackTrace();
      }
      return null;
   }

   /**
    * Is called when new configuration arrived, notify all plugins to empty their
    * cache or do whatever they need to do. 
    */
   public void reset() {
      Iterator it = mapMsgToMasterIdMap.values().iterator();
      while (it.hasNext()) {
         I_MapMsgToMasterId i = (I_MapMsgToMasterId)it.next();
         i.reset();
      }
   }
}
