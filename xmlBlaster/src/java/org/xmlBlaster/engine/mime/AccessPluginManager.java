/*------------------------------------------------------------------------------
Name:      AccessPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: AccessPluginManager.java,v 1.6 2002/05/01 21:40:08 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

/**
 * Loads subscribe()/get() filter plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MimeAccessPlugin[MyFilter][1.0]=com.mycompany.MyFilter
 * </pre>
 */
public class AccessPluginManager extends PluginManagerBase {

   private static final String ME = "AccessPluginManager";
   private static final String defaultPluginName = null; // "org.xmlBlaster.engine.mime.demo.DemoFilter
   public static final String pluginPropertyName = "MimeAccessPlugin";

   private final Global glob;
   private final Log log;

   public AccessPluginManager(Global glob)
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
    * @return The AccessFilter for this type and version or null if none is specified
    */
   public I_AccessFilter getPlugin(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getPlugin()", "Loading " + getPluginPropertyName(type, version));
      I_AccessFilter filterPlugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if(pluginNameAndParam!=null && pluginNameAndParam[0]!=null && pluginNameAndParam[0].length()>1) {
         filterPlugin = (I_AccessFilter)managers.get(pluginNameAndParam[0]);
         if (filterPlugin!=null) return filterPlugin;
         filterPlugin = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return filterPlugin;
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
            throw new XmlBlasterException(ME+".NoAccess","It's not allowed to use the standard security manager!");
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
    * @return I_AccessFilter
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_AccessFilter loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      return (I_AccessFilter)super.instantiatePlugin(pluginNameAndParam);
   }


// here are extensions for MIME based plugin selection:

   private final Map accessFilterMap = Collections.synchronizedMap(new HashMap());

   /**
    * Get access filter object from cache, based on MIME type. 
    */
   public final I_AccessFilter getAccessFilter(String type, String version, String mime, String mimeExtended)
   {
      try {
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = accessFilterMap.get(key.toString());
         if (obj != null)
            return (I_AccessFilter)obj;

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version).append("*");
         obj = accessFilterMap.get(key.toString());
         if (obj != null)
            return (I_AccessFilter)obj;

         return addAccessFilterPlugin(type, version); // try to load it

      } catch (Exception e) {
         Log.error(ME, "Problems accessing subscribe filter [" + type + "][" + version +"] mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_AccessFilter)null;
      }
   }

   /**
    * Invoked on new subscription or get() invocation, loads plugin. 
    * @return null if not found
    */
   public final I_AccessFilter addAccessFilterPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = accessFilterMap.get(key.toString());
      if (obj != null) {
         Log.info(ME, "Access filter '" + key.toString() + "' is loaded already");
         return (I_AccessFilter)obj;
      }

      try {
         I_AccessFilter filter = getPlugin(type, version);
         if (filter == null) {
            Log.error(ME, "Problems accessing plugin " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            return null;
         }

         accessFilterMap.put(key.toString(), filter); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = filter.getMimeTypes();
         String[] mimeExtended = filter.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               Log.error(ME, "Subscribe plugin manager [" + type + "][" + version +"]: Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            accessFilterMap.put(key.toString(), filter);
            Log.info(ME, "Loaded subscribe filter '" + key.toString() + "'");
            key.setLength(0);
         }

         return filter;
      } catch (Throwable e) {
         Log.error(ME, "Problems accessing subscribe plugin manager, can't instantiate " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"]: " + e.toString());
         e.printStackTrace();
      }
      return null;
   }
}
