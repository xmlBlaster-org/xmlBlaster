/*------------------------------------------------------------------------------
Name:      MapMsgToMasterPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: MapMsgToMasterPluginManager.java,v 1.6 2002/05/01 21:40:05 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;

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

   private final Global glob;
   private final Log log;
   private final ClusterManager clusterManager;

   private final Map mapMsgToMasterIdMap = Collections.synchronizedMap(new HashMap());

   public MapMsgToMasterPluginManager(Global glob, ClusterManager clusterManager) {
      super(glob);
      this.glob = glob;
      this.log = this.glob.getLog();
      this.clusterManager = clusterManager;
   }

   /**
    * Return a specific MIME based message plugin. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The MapMsgToMasterId for this type and version or null if none is specified
    */
   private final I_MapMsgToMasterId getPlugin(String type, String version) throws XmlBlasterException {
      if (log.CALL) log.call(ME+".getPlugin()", "Loading " + getPluginPropertyName(type, version));
      I_MapMsgToMasterId plugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         plugin = (I_MapMsgToMasterId)managers.get(pluginNameAndParam[0]);
         if (plugin!=null) return plugin;
         plugin = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested cluster mapper plugin isn't supported!");
      }

      return plugin;
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
   public final String getDefaultPluginName() {
      return defaultPluginName;
   }

   /**
    * Resolve type and version to the plugins name. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return String The name of the requested plugin.
    */
   protected final String[] choosePlugin(String type, String version) throws XmlBlasterException
   {
      return super.choosePlugin(type, version);
   }


   /**
    * Loads the plugin. 
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.engine.mime.demo.DemoFilter<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_MapMsgToMasterId
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected final I_MapMsgToMasterId loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      I_MapMsgToMasterId i = (I_MapMsgToMasterId)super.instantiatePlugin(pluginNameAndParam);
      i.initialize(glob, clusterManager);
      return i;
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
         log.error(ME, "Problems accessing cluster domain mapping " + getPluginPropertyName(type, version) + " mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
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
         log.info(ME, "Plugin '" + key.toString() + "' is loaded already");
         return (I_MapMsgToMasterId)obj;
      }

      try {
         I_MapMsgToMasterId plugin = getPlugin(type, version);
         if (plugin == null) {
            log.error(ME, "Problems accessing plugin " + getPluginPropertyName(type, version) + ", please check your configuration");
            return null;
         }

         mapMsgToMasterIdMap.put(key.toString(), plugin); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = plugin.getMimeTypes();
         String[] mimeExtended = plugin.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               log.error(ME, getPluginPropertyName(type, version) + ": Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            mapMsgToMasterIdMap.put(key.toString(), plugin);
            log.info(ME, "Loaded '" + key.toString() + "'");
            key.setLength(0);
         }

         return plugin;
      } catch (Throwable e) {
         log.error(ME, "Problems accessing cluster domain mapping plugin manager, can't instantiate " + getPluginPropertyName(type, version) + ": " + e.toString());
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
