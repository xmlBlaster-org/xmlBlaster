/*------------------------------------------------------------------------------
Name:      PluginManagerBase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for  managers to load plugins.
------------------------------------------------------------------------------*/package org.xmlBlaster.util.plugin;
import org.xmlBlaster.util.XmlBlasterException;
/**
 * A managare that loads plugins.
 *
 * @author Peter Antman
 * @version $Revision: 1.1 $ $Date$
 */

public interface I_PluginManager {

   /**
    * Return a specific plugin.
    * @param String The type of the requested plugin.
    * @param String The version of the requested plugin.
    * @return I_Plugin The plugin which is suitable to handle the request.
    * @exception XmlBlasterException Thrown if no suitable plugin has been found.
    */
   public I_Plugin getPluginObject(String type, String version) throws XmlBlasterException;
   
   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   // Renamed because of protected access
   //public String getPluginPropertyName();
   public String getName();
   
   /**
    * @return e.g. "Security.Server.Plugin[simple][1.0]"
    */
   public String createPluginPropertyKey(String type, String version);
   
   /**
    * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
    * for "Security.Server.Plugin[simple][1.0]"
    */
   public String getDefaultPluginName(String type, String version);
   
}
