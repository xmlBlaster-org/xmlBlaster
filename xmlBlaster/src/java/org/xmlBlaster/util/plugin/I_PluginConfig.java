package org.xmlBlaster.util.plugin;

import java.util.Properties;

/**
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_PluginConfig {
   
   /**
    * @return The configuration, never null
    */
   public Properties getParameters();

   /**
    * Gets the prefix for the implementing plugin. Is of the type
    * 'plugin/pluginType/'
    * @return
    */
   public String getPrefix();
   
}

