/*------------------------------------------------------------------------------
Name:      QueuePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;

/**
 * QueuePluginManager loads the I_Queue implementation plugins. 
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * // xmlBlaster.properties or on command line
 * QueuePlugin[JDBC][1.0]=org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 * </pre>
 * <pre>
 * // Access it in the code
 * String defaultPersistent = glob.getProperty().get("queue.cache.persistentQueue", "JDBC,1.0");
 * I_Queue queue = pluginManager.getPlugin(defaultPersistent, uniqueQueueId, queuePropertyBase);
 * </pre>
 * @author <a href="mailto:laghi@swissinfo.com">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.queue.html" target="others">engine.queue</a>
 */
public class QueuePluginManager extends PluginManagerBase
{
   private final String ME;
   private final Global glob;
   private final LogChannel log;
   private final String pluginEnvClass = "queue"; // Used for env lookup like "queue/history/QueuePlugin[JDBC][1.0]=..."
   public static final String pluginPropertyName = "QueuePlugin";
   private static final String[][] defaultPluginNames = { {"RAM", "org.xmlBlaster.util.queue.ram.RamQueuePlugin"},
                                                          {"JDBC", "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin"},
                                                          {"CACHE", "org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin"} };

   public QueuePluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("core");
      this.ME = "QueuePluginManager" + this.glob.getLogPrefixDashed();
      if (log.CALL) log.call(ME, "Constructor QueuePluginManager");
   }

   /**
    * @see #getPlugin(String, String, StorageId, QueuePropertyBase)
    */
   public I_Queue getPlugin(String typeVersion, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      return getPlugin(new PluginInfo(glob, this, typeVersion, 
              new ContextNode(this.pluginEnvClass, storageId.getPrefix(), glob.getContextNode())),
              storageId, props);
   }

   /**
    * Return a new created (persistent) queue plugin. 
    * <p/>
    * @param String The type of the requested plugin, pass 'undef' to suppress using a storage.
    * @param String The version of the requested plugin.
    * @param fn The file name for persistence or null (will be generated or ignored if RAM based)
    * @return The plugin for this type and version or null if none is specified or type=="undef"
    */
   public I_Queue getPlugin(String type, String version, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      return getPlugin(new PluginInfo(glob, this, type, version,
              new ContextNode(this.pluginEnvClass, storageId.getPrefix(), glob.getContextNode())),
              storageId, props);
   }

   public I_Queue getPlugin(PluginInfo pluginInfo, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      if (pluginInfo.ignorePlugin())
         return null;

      I_Queue plugin = (I_Queue)super.instantiatePlugin(pluginInfo, false);
      plugin.initialize(storageId, props);

      return plugin;
   }

   /**
    * Enforced by PluginManagerBase. 
    * @return The name of the property in xmlBlaster.property "QueuePlugin"
    * for "QueuePlugin[JDBC][1.0]"
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
