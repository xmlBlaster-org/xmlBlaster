/*------------------------------------------------------------------------------
Name:      PublishPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: PublishPluginManager.java,v 1.3 2002/04/19 11:00:18 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Loads publish() filter plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MimePublishPlugin[MyFilter][1.0]=com.mycompany.MyFilter
 * </pre>
 */
public class PublishPluginManager extends PluginManagerBase {

   private static final String ME = "PublishPluginManager";
   private static final String defaultPluginName = null; // "org.xmlBlaster.engine.mime.demo.DemoFilter
   public static final String pluginPropertyName = "MimePublishPlugin";

   private final Global glob;
   private final Log log;

   public PublishPluginManager(Global glob)
   {
      super(glob);
      this.glob = glob;
      this.log = this.glob.getLog();
   }

   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The PublishFilter for this type and version or null if none is specified
    */
   public I_PublishFilter getPlugin(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getPlugin()", "Loading peristence plugin type[" + type + "] version[" + version +"]");
      I_PublishFilter filterPlugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         filterPlugin = (I_PublishFilter)managers.get(pluginNameAndParam[0]);
         if (filterPlugin!=null) return filterPlugin;
         filterPlugin = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return filterPlugin;
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
   public String getDefaultPluginName() {
      return defaultPluginName;
   }

   /**
    * Resolve type and version to the plugins name. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return String The name of the requested plugin.
    */
   protected String[] choosePlugin(String type, String version) throws XmlBlasterException
   {
      /*if (type == null || type.equals("simple")) {
         if (XmlBlasterProperty.get("Security.Server.allowSimpleDriver", true) == false){
            throw new XmlBlasterException(ME+".NoPublish","It's not allowed to use the standard security manager!");
         }
      }*/

      return super.choosePlugin(type, version);
   }


   /**
    * Loads a persistence plugin. 
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.engine.mime.demo.DemoFilter<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_PublishFilter
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_PublishFilter loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      return (I_PublishFilter)super.instantiatePlugin(pluginNameAndParam);
   }


// here are extensions for MIME based plugin selection:

   private final Map publishFilterMap = Collections.synchronizedMap(new HashMap());

   /**
    * Get publish filter object from cache, based on MIME type. 
    */
   public final I_PublishFilter getPublishFilter(String type, String version, String mime, String mimeExtended)
   {
      try {
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = publishFilterMap.get(key.toString());
         if (obj != null)
            return (I_PublishFilter)obj;

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version).append("*");
         obj = publishFilterMap.get(key.toString());
         if (obj != null)
            return (I_PublishFilter)obj;

         return addPublishFilterPlugin(type, version); // try to load it

      } catch (Exception e) {
         Log.error(ME, "Problems accessing publish filter [" + type + "][" + version +"] mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_PublishFilter)null;
      }
   }

   /**
    * Invoked on new subscription or get() invocation, loads plugin. 
    * @return null if not found
    */
   public final I_PublishFilter addPublishFilterPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = publishFilterMap.get(key.toString());
      if (obj != null) {
         Log.info(ME, "Publish filter '" + key.toString() + "' is loaded already");
         return (I_PublishFilter)obj;
      }

      try {
         I_PublishFilter filter = getPlugin(type, version);
         if (filter == null) {
            Log.error(ME, "Problems accessing plugin " + PublishPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            return null;
         }

         publishFilterMap.put(key.toString(), filter); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = filter.getMimeTypes();
         String[] mimeExtended = filter.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               Log.error(ME, "Publish plugin manager [" + type + "][" + version +"]: Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = org.xmlBlaster.util.XmlKeyBase.DEFAULT_contentMimeExtended;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            publishFilterMap.put(key.toString(), filter);
            Log.info(ME, "Loaded publish filter '" + key.toString() + "'");
            key.setLength(0);
         }

         return filter;
      } catch (Throwable e) {
         Log.error(ME, "Problems accessing publish plugin manager, can't instantiate " + PublishPluginManager.pluginPropertyName + "[" + type + "][" + version +"]: " + e.toString());
         e.printStackTrace();
      }
      return null;
   }
}
