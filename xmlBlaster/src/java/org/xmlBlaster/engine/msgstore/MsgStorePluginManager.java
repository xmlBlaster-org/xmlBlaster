/*------------------------------------------------------------------------------
Name:      MsgStorePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

/**
 * MsgStorePluginManager loads the I_Map implementation plugins. 
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * // xmlBlaster.properties or on command line
 * MsgStorePlugin[RAM][1.0]=org.xmlBlaster.engine.msgstore.ram.MapPlugin
 * </pre>
 * <pre>
 * // Access it in the code
 * String defaultPersistent = glob.getProperty().get("msgstore.cache.persistentQueue", "JDBC,1.0");
 * I_Map storage = glob.getMsgStorePluginManager().getPlugin(defaultPersistent, new StorageId("SpecialJdbcQueue"), queuePropertyBase);
 * </pre>
 * @author <a href="mailto:laghi@swissinfo.com">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.queue.html" target="others">engine.queue</a>
 */
public class MsgStorePluginManager extends PluginManagerBase
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private static final String[][] defaultPluginNames = { {"RAM", "org.xmlBlaster.engine.msgstore.ram.MapPlugin"},
                                                          {"JDBC", "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin"},
                                                          {"CACHE", "org.xmlBlaster.engine.msgstore.cache.MsgStoreCachePlugin"} };
   public static final String pluginPropertyName = "MsgStorePlugin";

   public MsgStorePluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("msgstore");
      this.ME = "MsgStorePluginManager" + this.glob.getLogPrefixDashed();
      if (log.CALL) log.call(ME, "Constructor MsgStorePluginManager");
   }

   /**
    * @see #getPlugin(String, String, StorageId, QueuePropertyBase)
    */
   public I_Map getPlugin(String typeVersion, StorageId uniqueQueueId, QueuePropertyBase props) throws XmlBlasterException {
      return getPlugin(new PluginInfo(glob, this, typeVersion), uniqueQueueId, props);
   }

   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * @param String The type of the requested plugin, pass 'undef' to suppress using a storage.
    * @param String The version of the requested plugin.
    * @param fn The file name for persistence or null (will be generated or ignored if RAM based)
    * @return The plugin for this type and version or null if none is specified or type=="undef"
    */
   public I_Map getPlugin(String type, String version, StorageId uniqueQueueId, QueuePropertyBase props) throws XmlBlasterException {
      return getPlugin(new PluginInfo(glob, this, type, version), uniqueQueueId, props);
   }

   public I_Map getPlugin(PluginInfo pluginInfo, StorageId uniqueQueueId, QueuePropertyBase props) throws XmlBlasterException {
      if (pluginInfo.ignorePlugin())
         return null;

      I_Map plugin = (I_Map)super.instantiatePlugin(pluginInfo);
      plugin.initialize(uniqueQueueId, props);

      return plugin;
   }

   /**
    * Enforced by PluginManagerBase. 
    * @return The name of the property in xmlBlaster.property "MsgStorePlugin"
    * for "MsgStorePlugin[JDBC][1.0]"
    */
   protected String getPluginPropertyName() {
      return pluginPropertyName;
   }

   protected void postInstantiate(I_Plugin plugin, PluginInfo pluginInfo) {
   }

   /**
    * @return please return your default plugin class name or null if not specified
    */
   public String getDefaultPluginName(String type, String version) {
      for (int i=0; i<defaultPluginNames.length; i++) {
         if (defaultPluginNames[i][0].equalsIgnoreCase(type)) {
            if (log.TRACE) log.trace(ME, "Choosing for type=" + type + " plugin " + defaultPluginNames[i][1]);
            return defaultPluginNames[i][1];
         }
      }
      log.warn(ME, "Choosing for type=" + type + " default plugin " + defaultPluginNames[0][1]);
      return defaultPluginNames[0][1];
   }
}

