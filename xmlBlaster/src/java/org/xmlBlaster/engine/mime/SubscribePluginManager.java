/*------------------------------------------------------------------------------
Name:      SubscribePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: SubscribePluginManager.java,v 1.2 2002/03/14 19:18:32 ruff Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.mime;

import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Loads subscribe filter plugin depending on message MIME type. 
 * Please register your plugins in xmlBlaster.properties, for example:
 * <pre>
 * MimeSubscribePlugin[text/xml][1.0]=com.mycompany.MyFilter
 * </pre>
 */
public class SubscribePluginManager extends PluginManagerBase {

   private static final String ME = "SubscribePluginManager";
   private static final String defaultPluginName = null; // "org.xmlBlaster.engine.mime.demo.DemoFilter
   private static final String pluginPropertyName = "MimeSubscribePlugin";

   private static SubscribePluginManager me = null;

   /** To protect the singleton */
   private static final java.lang.Object SYNCHRONIZER = new java.lang.Object();

   public SubscribePluginManager() throws XmlBlasterException
   {
         /*
      try {
         // super.choosePlugin reads pluginName and parameters from porperties
         // so read property file, if it's not there, write it to the properties
         XmlBlasterProperty.set(pluginPropertyName + "[filestore][1.0]",
            XmlBlasterProperty.get(pluginPropertyName + "[filestore][1.0]", "org.xmlBlaster.engine.persistence.filestore.FileDriver") );

         XmlBlasterProperty.set(pluginPropertyName + "[xmldb][xindice]",
            XmlBlasterProperty.get(pluginPropertyName + "[xmldb][xindice]", "org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin, xindice") );
      } catch (org.jutils.JUtilsException e) {
         throw new XmlBlasterException( e.id, e.reason );
      }
         */
   }


   /**
    * Return an instance of this singleton. 
    *
    * @return SubscribePluginManager
    */
   public static SubscribePluginManager getInstance() throws XmlBlasterException {
      if (me == null) { // avoid 'expensive' synchronized
         synchronized (SYNCHRONIZER) {
            if (me == null)
               me = new SubscribePluginManager();
         }
      }
      return me;
   }


   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return The SubscribeFilter for this mime and version or null if none is specified
    */
   public I_SubscribeFilter getPlugin(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getPlugin()", "Loading peristence plugin type[" + type + "] version[" + version +"]");
      I_SubscribeFilter persistencePlugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if((pluginNameAndParam!=null) &&
         (pluginNameAndParam[0]!=null) &&
         (!pluginNameAndParam.equals("")))
      {
         persistencePlugin = (I_SubscribeFilter)managers.get(pluginNameAndParam[0]);
         if (persistencePlugin!=null) return persistencePlugin;

         persistencePlugin = loadPlugin(pluginNameAndParam);
      }
      else {
         //throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
      }

      return persistencePlugin;
   }


   /**
    * Check if the requested plugin is supported. 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return boolean true, if supported. else -> false
    */
   public boolean isSupported(String type, String version) {
      Log.error(ME, "Not implemented yet");
      return false;
   }


   /**
   * @return The name of the property in xmlBlaster.property "MimeSubscribePlugin"
   * for "MimeSubscribePlugin[demo][1.0]"
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
    * @return I_SubscribeFilter
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_SubscribeFilter loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      return (I_SubscribeFilter)super.instantiatePlugin(pluginNameAndParam);
   }
}
