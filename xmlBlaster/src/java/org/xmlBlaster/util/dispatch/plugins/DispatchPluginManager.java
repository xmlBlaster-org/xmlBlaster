/*------------------------------------------------------------------------------
Name:      DispatchPluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * DispatchPluginManager loads the I_MsgDispatchInterceptor implementation plugins. 
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * // xmlBlaster.properties or on command line
 * DispatchPlugin[Priority][1.0]=org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
 * DispatchPlugin[FeedMotion][1.0]=org.xmlBlaster.util.dispatch.plugins.motion.FeedMotionDispatchPlugin
 * DispatchPlugin/defaultPlugin=Priority,1.0
 * # Switch off:
 * # DispatchPlugin/defaultPlugin=undef
 * </pre>
 * <pre>
 *    I_MsgDispatchInterceptor interceptor = glob.getDispatchPluginManager().getPlugin("Priority", "1.0", "XY", queuePropertyBase);
 * </pre>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/dispatch.plugin.html" target="others">dispatch.plugin</a>
 */
public class DispatchPluginManager extends PluginManagerBase
{
   private static final String defaultPluginName = "org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin";
   public static final String pluginPropertyName = "DispatchPlugin";

   public DispatchPluginManager(Global glob) {
      super(glob);
   }

   /**
    * Return a specific dispatcher plugin, if possible from the cache. 
    * <p/>
    * This PluginManager exists once in the scope of a Global instance (check util.Global)
    * therefore the plugin of same typeVersion is only loaded once.
    * @param typeVersion The type and version e.g. "Priority,1.0"
    * @return null if no plugin was loaded
    */
   public I_MsgDispatchInterceptor getPlugin(String typeVersion) throws XmlBlasterException {
      PluginInfo pluginInfo = new PluginInfo(getGlobal(), this, typeVersion);
      pluginInfo.setUserData(typeVersion); // transport to postInstantiate() without any modification
      return (I_MsgDispatchInterceptor)getPluginObject(pluginInfo);
   }

   /**
    * Enforced by PluginManagerBase. 
    * @return The name of the property in xmlBlaster.property "DispatchPlugin"
    * for "DispatchPlugin[Priority][1.0]"
    */
   public String getPluginPropertyName() {
      return pluginPropertyName;
   }

   /**
    * Called after getPlugin() but only if plugin was new created
    */
   protected void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) throws XmlBlasterException {
      String typeVersion = (String)pluginInfo.getUserData();
      ((I_MsgDispatchInterceptor)plugin).initialize(getGlobal(), typeVersion);
   }

   /**
    * @return please return your default plugin class name or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      return defaultPluginName;
   }
}

