/*------------------------------------------------------------------------------
Name:      MsgDistributorPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.distributor.plugins;

import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.engine.distributor.I_MsgDistributor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.PluginManagerBase;

/**
 * MsgDistributorPluginManager
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class MsgDistributorPluginManager extends PluginManagerBase {

   /** the default class name to use if nothing specified */
   private static final String defaultPluginName = "org.xmlBlaster.engine.distributor.plugins.ConsumableQueuePlugin";
   /** This is the prefix of the name used in xmlBlaster.properties to register the plugins of this type */
   public static final String pluginPropertyName = "MsgDistributorPlugin";

   public MsgDistributorPluginManager(Global glob) {
      super(glob);
   }

   /**
    * Return a specific msgDistributor plugin. 
    * <p/>
    * This PluginManager exists once in the scope of a Global instance (check util.Global)
    * @param typeVersion The type and version e.g. "ConsumableQueue,1.0"
    * @return null if no plugin was loaded
    */
      public I_MsgDistributor getPlugin(String typeVersion, TopicHandler topicHandler) throws XmlBlasterException {
         PluginInfo pluginInfo = new PluginInfo(getGlobal(), this, typeVersion);
         pluginInfo.setUserData(topicHandler); // transport to init
         // return (I_MsgDistributor)getPluginObject(pluginInfo);
         return (I_MsgDistributor)this.instantiatePlugin(pluginInfo);
         
      }

      /**
       * Enforced by PluginManagerBase. 
       * @return The name of the property in xmlBlaster.property "MsgDistributorPlugin"
       * for "MsgDistributorPlugin[ConsumableQueue][1.0]"
       */
      public String getPluginPropertyName() {
         return pluginPropertyName;
      }

      /**
       * @return please return your default plugin class name or null if not specified
       */
      public String getDefaultPluginName(String type, String version) {
         return defaultPluginName;
      }
}
