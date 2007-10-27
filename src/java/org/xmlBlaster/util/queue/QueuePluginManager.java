/*------------------------------------------------------------------------------
Name:      QueuePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
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
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.queue.html" target="others">engine.queue</a>
 */
public class QueuePluginManager extends PluginManagerBase {


   // private final String ME;
   private final Global glob;
   private static Logger log = Logger.getLogger(QueuePluginManager.class.getName());
   private final String pluginEnvClass = "queue"; // Used for env lookup like "queue/history/QueuePlugin[JDBC][1.0]=..."
   public static final String pluginPropertyName = "QueuePlugin";
   private static final String[][] defaultPluginNames = { {"RAM", "org.xmlBlaster.util.queue.ram.RamQueuePlugin"},
                                                          {"JDBC", "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin"},
                                                          {"CACHE", "org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin"} };

   private Map/*<String(storageId), I_Queue>*/ storagesMap = new HashMap();
   private StorageEventHandler eventHandler;

   public QueuePluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      // this.ME = "QueuePluginManager" + this.glob.getLogPrefixDashed();
      if (log.isLoggable(Level.FINER)) log.finer("Constructor QueuePluginManager");
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

      if (!props.isEmbedded()) {
         synchronized(this.storagesMap) {
            this.storagesMap.put(storageId.getId(), plugin);
            if (eventHandler != null)
               eventHandler.registerListener(plugin);
         }
      }
      return plugin;
   }

   public void cleanup(I_Storage storage) {
      synchronized (this.storagesMap) {
         storagesMap.remove(storage.getStorageId().getId());
         if (eventHandler != null)
            eventHandler.removeListener(storage);
      }
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
            if (log.isLoggable(Level.FINE)) log.fine("Choosing for type=" + type + " plugin " + defaultPluginNames[i][1]);
            return defaultPluginNames[i][1];
         }
      }
      log.warning("Choosing for type=" + type + " default plugin " + defaultPluginNames[0][1]);
      return defaultPluginNames[0][1];
   }

   /**
    * Set an EventHandler singleton
    * @param handler null resets an existing handler
    * @return if false another one existed already and your handler is not set
    * @throws XmlBlasterException
    */
   public boolean setEventHandler(StorageEventHandler handler) throws XmlBlasterException {
      synchronized (this.storagesMap) {
         if (handler != null && this.eventHandler != null)
            return false; // can't overwrite existing handler

         if (handler != null)
            handler.initialRegistration(this.storagesMap);
         else if (eventHandler != null)
            eventHandler.removeListeners(this.storagesMap);
         eventHandler = handler;
      }
      return true;
   }

}
