/*------------------------------------------------------------------------------
Name:      AccessPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: AccessPluginManager.java,v 1.3 2002/04/16 20:42:30 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;

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

   private static AccessPluginManager me = null;

   /** To protect the singleton */
   private static final java.lang.Object SYNCHRONIZER = new java.lang.Object();

   public AccessPluginManager() throws XmlBlasterException
   {
      // No default plugin to initialize
   }

   /**
    * Return an instance of this singleton. 
    *
    * @return AccessPluginManager
    */
   public static AccessPluginManager getInstance() throws XmlBlasterException {
      if (me == null) { // avoid 'expensive' synchronized
         synchronized (SYNCHRONIZER) {
            if (me == null)
               me = new AccessPluginManager();
         }
      }
      return me;
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
}
