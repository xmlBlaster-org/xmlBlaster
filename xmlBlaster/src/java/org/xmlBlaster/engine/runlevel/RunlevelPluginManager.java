/*------------------------------------------------------------------------------
Name:      RunlevelPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * This class is used to load the static plugins, i.e. the plugins loaded by the 
 * RunlevelManager.
 * <p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel requirement</a>
 */
public class RunlevelPluginManager extends PluginManagerBase {

   public RunlevelPluginManager(org.xmlBlaster.util.Global glob) {
      super(glob);
   }

   /**
    * Is called after a plugin in instantiated, allows the base class to do specific actions.
    * Is NOT called when plugin got from cache.
    */
   protected void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException {
   }

   /**
    * @param type can be null
    * @param version can be null
    * @return please return your default plugin classname or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return null;
   }

   /**
   * @return The name of the property in xmlBlaster.property, e.g. "Security.Server.Plugin"
   * for "Security.Server.Plugin[simple][1.0]"
   */
   protected String getPluginPropertyName() {
      return null;
   }
}
