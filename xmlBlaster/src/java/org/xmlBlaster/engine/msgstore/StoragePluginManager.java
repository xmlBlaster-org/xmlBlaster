/*------------------------------------------------------------------------------
Name:      StoragePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.context.ContextNode;

/**
 * StoragePluginManager loads the I_Map implementation plugins. 
 * <p>
 * Usage examples:
 * </p>
 * <pre>
 * // xmlBlaster.properties or on command line
 * #------------------------------------------------------------------------------
 * # Declare existing queue implementation plugins
 * # SEE: http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html
 * StoragePlugin[JDBC][1.0]=org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 * StoragePlugin[RAM][1.0]=org.xmlBlaster.engine.msgstore.ram.MapPlugin
 * StoragePlugin[CACHE][1.0]=org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin,transientMap=RAM,persistentMap=JDBC
 * 
 * # Choose the plugin (each publisher can overwrite this in its publish topic-QoS)
 * persistence/defaultPlugin=CACHE,1.0
 * persistence/topicStore/defaultPlugin=JDBC,1.0
 * persistence/msgUnitStore/defaultPlugin=CACHE,1.0
 *
 * # If you choose CACHE as defaultPlugin configure the CACHE plugin:
 * persistence.cache.persistentQueue=JDBC,1.0
 * persistence.cache.transientQueue=RAM,1.0
 * #------------------------------------------------------------------------------
 * </pre>
 * @author <a href="mailto:laghi@swissinfo.com">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.queue.html" target="others">engine.queue</a>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html" target="others">engine.message.lifecycle</a>
 */
public class StoragePluginManager extends PluginManagerBase
{
   private final String ME;
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(StoragePluginManager.class.getName());
   private final String pluginEnvClass = "persistence"; // Used for env lookup like "persistence/topicStore/StoragePlugin[JDBC][1.0]=..."
   private static final String[][] defaultPluginNames = { {"RAM", "org.xmlBlaster.engine.msgstore.ram.MapPlugin"},
                                                          {"JDBC", "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin"},
                                                          {"CACHE", "org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin"} };
   public static final String pluginPropertyName = "StoragePlugin";

   public StoragePluginManager(ServerScope glob) {
      super(glob);
      this.glob = glob;

      this.ME = "StoragePluginManager" + this.glob.getLogPrefixDashed();
      //this.glob.addObjectEntry("org.xmlBlaster.engine.msgstore.StoragePluginManager", this);
      if (log.isLoggable(Level.FINER)) log.finer("Constructor StoragePluginManager");
   }

   /**
    * @see #getPlugin(String, String, StorageId, QueuePropertyBase)
    */
   public I_Map getPlugin(String typeVersion, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      return getPlugin(new PluginInfo(glob, this, typeVersion, 
                                      new ContextNode(this.pluginEnvClass, storageId.getPrefix(), glob.getContextNode())),
                       storageId, props);
   }

   /**
    * Return a new created storage plugin. 
    * <p/>
    * @param String The type of the requested plugin, pass 'undef' to suppress using a storage.
    * @param String The version of the requested plugin.
    * @param fn The file name for persistence or null (will be generated or ignored if RAM based)
    * @return The plugin for this type and version or null if none is specified or type=="undef"
    */
   public I_Map getPlugin(String type, String version, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("getPlugin(type="+type+", version="+version+", storageId="+storageId+", pluginEnvClass="+this.pluginEnvClass+")");
      return getPlugin(new PluginInfo(glob, this, type, version,
                                      new ContextNode(this.pluginEnvClass, storageId.getPrefix(), glob.getContextNode())),
                       storageId, props);
   }

   public I_Map getPlugin(PluginInfo pluginInfo, StorageId storageId, QueuePropertyBase props) throws XmlBlasterException {
      if (pluginInfo.ignorePlugin())
         return null;

      I_Map plugin = (I_Map)super.instantiatePlugin(pluginInfo, false);
      plugin.initialize(storageId, props);

      return plugin;
   }

   /**
    * Enforced by PluginManagerBase. 
    * @return The name of the property in xmlBlaster.property "StoragePlugin"
    * for "StoragePlugin[JDBC][1.0]"
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
            if (log.isLoggable(Level.FINE)) log.fine("Choosing for type=" + type + " plugin " + defaultPluginNames[i][1]);
            return defaultPluginNames[i][1];
         }
      }
      log.warning("Choosing for type=" + type + " default plugin " + defaultPluginNames[0][1]);
      return defaultPluginNames[0][1];
   }
}

