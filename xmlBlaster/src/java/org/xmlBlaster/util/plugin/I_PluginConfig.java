package org.xmlBlaster.util.plugin;

import java.util.Properties;

/**
 * Abstraction to allow access configuration parameters of a Plugin. 
 * Example: xmlBlasterPlugins. <attributes:
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_PluginConfig {
   
   /**
    * @return The configuration, never null
    */
   public Properties getParameters();

   /**
    * Gets the prefix for the implementing plugin. 
    * Suppose you have two plugins ('pluginA1' and 'pluginB2') both containing an 
    * attribute called 'directoryName'. Then without a context there is now way to 
    * distinguish between them. This method returns the prefix (the context).
    * For example 
    * 'plugin/${pluginType}/' which would be 'plugin/pluginA1/'
    * @return
    */
   public String getPrefix();
   
   public String getType();

   public String getVersion();
}

