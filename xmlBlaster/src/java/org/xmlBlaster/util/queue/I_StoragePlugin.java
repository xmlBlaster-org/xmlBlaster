/*------------------------------------------------------------------------------                              
Name:      I_StoragePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Interface for storage plugins
 *
 * @author laghi@swissinfo.org
 * @author xmlBlaster@marcelruff.info
 */
public interface I_StoragePlugin extends I_Plugin
{
   /**
    * returns the pluginInfo object for this plugin.
    */
   public PluginInfo getInfo();
}

