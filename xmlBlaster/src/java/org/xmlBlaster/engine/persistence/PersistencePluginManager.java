/*------------------------------------------------------------------------------
Name:      PersistencePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Version:   $Id: PersistencePluginManager.java,v 1.2 2002/02/08 00:48:15 goetzger Exp $
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.util.PluginManagerBase;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.plugins.I_Manager;
import org.xmlBlaster.authentication.plugins.I_Session;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.engine.ClientInfo;
import java.util.Hashtable;
import java.util.Vector;
import java.util.StringTokenizer;

/**
 * Title: PluginManager
 * Description: Loads persistence plugin
 */

public class PersistencePluginManager extends PluginManagerBase {

   private static final String ME = "PersistencePluginManager";
   private static final String defaultPluginName = "org.xmlBlaster.engine.persistence.filestore.FileDriver";
   private static final String pluginPropertyName = "Persistence.Driver";

   private static PersistencePluginManager me = null;

   /** To protect the singleton */
   private static final java.lang.Object SYNCHRONIZER = new java.lang.Object();

   public PersistencePluginManager() throws XmlBlasterException {

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

   }


   /**
    * Return an instance of this singleton
    *
    * @return PersistencePluginManager
    */
   public static PersistencePluginManager getInstance() throws XmlBlasterException {
      if (me == null) { // avoid 'expensive' synchronized
         synchronized (SYNCHRONIZER) {
            if (me == null)
               me = new PersistencePluginManager();
         }
      }
      return me;
   }


   /**
    * Return a specific persistence plugin
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Manager The persistence plugin
    * @exception XmlBlasterException Thrown if to suitable security manager has been found.
    */
   public I_PersistenceDriver getPlugin(String type, String version) throws XmlBlasterException {
      if (Log.CALL) Log.call(ME+".getPlugin()", "Loading peristence plugin type[" + type + "] version[" + version +"]");
      I_PersistenceDriver persistencePlugin = null;
      String[] pluginNameAndParam = null;

      pluginNameAndParam = choosePlugin(type, version);

      if((pluginNameAndParam!=null) &&
         (pluginNameAndParam[0]!=null) &&
         (!pluginNameAndParam.equals("")))
      {
         persistencePlugin = (I_PersistenceDriver)managers.get(pluginNameAndParam[0]);
         if (persistencePlugin!=null) return persistencePlugin;

         persistencePlugin = loadPlugin(pluginNameAndParam);
      }
      else {
         throw new XmlBlasterException(ME+".notSupported","The requested security manager isn't supported!");
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
      // currently just a dummy implementation
      // thus, it's impossible the switch the default security manager off
      return true;
   }


   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Persistence.Driver"
   * for "Persistence.Driver[xindice][1.0]"
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
    * Resolve type and version to the plugins name
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
    * Loads a persistence plugin
    * <p/>
    * @param String[] The first element of this array contains the class name
    *                 e.g. org.xmlBlaster.engine.persistence.xmldb.XMLDBPlugin<br />
    *                 Following elements are arguments for the plugin. (Like in c/c++ the command-line arguments.)
    * @return I_PersistenceDriver
    * @exception XmlBlasterException Thrown if loading or initializing failed.
    */
   protected I_PersistenceDriver loadPlugin(String[] pluginNameAndParam) throws XmlBlasterException
   {
      return (I_PersistenceDriver)super.instantiatePlugin(pluginNameAndParam);
   }
}
