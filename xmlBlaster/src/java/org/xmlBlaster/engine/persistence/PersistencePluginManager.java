/*------------------------------------------------------------------------------
Name:      PersistencePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Code for a plugin manager for persistence
Author:    goetzger@gmx.net
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.persistence;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Title: PluginManager
 * Description: Loads persistence plugin
 */

public class PersistencePluginManager extends PluginManagerBase
{
   private static final String defaultPluginName = "org.xmlBlaster.engine.persistence.filestore.FileDriver";
   private static final String pluginPropertyName = "Persistence.Driver";

   public PersistencePluginManager(ServerScope glob) throws XmlBlasterException {
      super(glob);
      // super.choosePlugin reads pluginName and parameters from properties
      // so read property file, if it's not there, write it to the properties
      glob.getProperty().set(pluginPropertyName + "[filestore][1.0]",
         glob.getProperty().get(pluginPropertyName + "[filestore][1.0]", "org.xmlBlaster.engine.persistence.filestore.FileDriver") );
   }

   /**
    * Return a specific persistence plugin from cache (on first request create it). 
    * <p/>
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Manager The persistence plugin
    * @exception XmlBlasterException Thrown if to suitable security manager has been found.
    */
   public I_PersistenceDriver getPlugin(String type, String version) throws XmlBlasterException {
      return (I_PersistenceDriver)getPluginObject(type, version);
   }

   public void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {}

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
   * @return The name of the property in xmlBlaster.property, e.g. "Persistence.Driver[][]"
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
}
