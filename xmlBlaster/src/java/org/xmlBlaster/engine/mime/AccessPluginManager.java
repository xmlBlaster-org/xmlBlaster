/*------------------------------------------------------------------------------
Name:      AccessPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: AccessPluginManager.java,v 1.10 2002/05/16 15:36:23 ruff Exp $
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
   public String getDefaultPluginName(String type, String version) {
      if (type != null) {
         if (type.equals("GnuRegexFilter"))
            return "org.xmlBlaster.engine.mime.regex.GnuRegexFilter";
      }
      return null;
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
         if (glob.getProperty().get("Security.Server.allowSimpleDriver", true) == false){
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
    * @return null if not applicable
    */
   public final I_AccessFilter getAccessFilter(String type, String version, String mime, String mimeExtended)
   {
      if (mimeExtended == null || mimeExtended.length() < 1) {
         mimeExtended = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         if (Log.TRACE) Log.trace(ME, "Needed to set empty mimeExtended to default=" + mimeExtended);
      }

      if (Log.CALL) Log.call(ME, "Trying to find plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended);
      try {
         // Try to find it in the cache...
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            if (Log.TRACE) Log.trace(ME, "Found filter for key=" + key.toString());
            return (I_AccessFilter)obj;
         }
         if (Log.TRACE) Log.trace(ME, "No filter for key=" + key.toString());

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version);
         obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            return (I_AccessFilter)obj;
         }
         if (Log.TRACE) Log.trace(ME, "No filter for key=" + key.toString());

         // Check if the plugin is loaded already
         key.setLength(0);
         key.append(type).append(version);
         obj = accessFilterMap.get(key.toString());
         if (obj != null) {
            Log.info(ME, "Filter plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended + " is loaded but does not match");
            return null; // Plugin is loaded but does not support the mime types
         }

         Log.info(ME, "Going to load filter plugin for type=" + type + " version=" + version + " mime=" + mime + " mimeExtended=" + mimeExtended);

         if (addAccessFilterPlugin(type, version)) { // try to load it
            // try again if our mime type matches:
            key.setLength(0);
            key.append(type).append(version).append(mime).append(mimeExtended);
            obj = accessFilterMap.get(key.toString());
            if (obj != null) {
               if (Log.TRACE) Log.trace(ME, "Found filter for key=" + key.toString());
               return (I_AccessFilter)obj;
            }

            // Check if the plugin is for all mime types
            key.setLength(0);
            key.append(type).append(version);
            obj = accessFilterMap.get(key.toString());
            if (obj != null) {
               return (I_AccessFilter)obj;
            }
         }
         return null;

      } catch (Exception e) {
         Log.error(ME, "Problems accessing subscribe filter [" + type + "][" + version +"] mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_AccessFilter)null;
      }
   }

   /**
    * Invoked on new subscription or get() invocation, loads plugin. 
    * You have to check yourself if it is loaded already
    * @return true if loaded
    */
   public final boolean addAccessFilterPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = accessFilterMap.get(key.toString());
      if (obj != null) {
         Log.warn(ME, "Access filter '" + key.toString() + "' is loaded already");
         return false;
      }

      try {
         I_AccessFilter filterPlugin = getPlugin(type, version);
         if (filterPlugin == null) {
            Log.error(ME, "Problems accessing plugin " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            return false;
         }

         accessFilterMap.put(key.toString(), filterPlugin); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = filterPlugin.getMimeTypes();
         String[] mimeExtended = filterPlugin.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               Log.error(ME, "Access plugin manager [" + type + "][" + version +"]: Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = Constants.DEFAULT_CONTENT_MIME_EXTENDED;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            if (mime[ii] == null || mime[ii].length() < 1 || mime[ii].equals("*"))
               key.append(type).append(version);
            else
               key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            accessFilterMap.put(key.toString(), filterPlugin);
            Log.info(ME, "Loaded access filter for mime types '" + key.toString() + "'");
            key.setLength(0);
         }

         return true;
      } catch (Throwable e) {
         Log.error(ME, "Problems accessing subscribe plugin manager, can't instantiate " + AccessPluginManager.pluginPropertyName + "[" + type + "][" + version +"]: " + e.toString());
         e.printStackTrace();
      }
      return false;
   }
}
