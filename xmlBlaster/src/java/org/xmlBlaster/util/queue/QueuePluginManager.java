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
   private static final String defaultPluginName = "org.xmlBlaster.util.queue.ram.RamQueuePlugin";
   public static final String pluginPropertyName = "QueuePlugin";

   public QueuePluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.log = glob.getLog("queue");
      this.ME = "QueuePluginManager" + this.glob.getLogPrefixDashed();
      if (log.CALL) log.call(ME, "Constructor QueuePluginManager");
   }

   /**
    * @see #getPlugin(String, String, StorageId, QueuePropertyBase)
    */
   public I_Queue getPlugin(String typeVersion, StorageId uniqueQueueId, QueuePropertyBase props) throws XmlBlasterException {
      if (typeVersion == null)
         return null;
      String version;
      String type;
      int i = typeVersion.indexOf(',');
      if (i==-1) {  // version is optional
         version = null;
      }
      else {
         version = typeVersion.substring(i+1);
      }
      type = typeVersion.substring(0,i);
      return getPlugin(type, version, uniqueQueueId, props);
   }

   /**
    * Return a specific MIME based message filter plugin. 
    * <p/>
    * @param String The type of the requested plugin, pass 'undef' to suppress using a queue.
    * @param String The version of the requested plugin.
    * @param fn The file name for persistence or null (will be generated or ignored if RAM based)
    * @return The plugin for this type and version or null if none is specified or type=="undef"
    */
   public I_Queue getPlugin(String type, String version, StorageId uniqueQueueId, QueuePropertyBase props) throws XmlBlasterException {

      if (log.CALL) log.call(ME+".getPlugin()", "Loading " + createPluginPropertyKey(type, version));
      I_Queue plugin = null;

      PluginInfo pluginInfo = new PluginInfo(glob, this, type, version);
      if (pluginInfo.ignorePlugin())
         return null;

      /*
      plugin = getFromPluginCache(pluginInfo.getClassName());
      if (plugin!=null) return plugin; // from cache
      */

      // create a new one ...
      plugin = (I_Queue)super.instantiatePlugin(pluginInfo);
      plugin.initialize(uniqueQueueId, props);

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
      return defaultPluginName;
   }
}

