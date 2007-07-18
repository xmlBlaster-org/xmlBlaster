/*------------------------------------------------------------------------------
Name:      QueuePluginManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;

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
public class QueuePluginManager extends PluginManagerBase implements I_StorageSizeListener {

   public class QueueEventHandler extends StorageEventHandler {

      private Global global;
      public QueueEventHandler(I_StorageSizeListener listener, Global global) {
         super(listener);
         this.global = global;
      }
      
      public void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException {
         // client/*/session/[publicSessionId]/queue/callback/event/threshold.90%
         // client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%
         // topic/[topicId]/queue/history/event/threshold.90%
         // */queue/*/event/threshold*

         String end = "/event/threshold.";
         int index = event.lastIndexOf(end);
         String value = event.substring(index + end.length());

         String tmp = event.substring(0, index);
         end = "/queue/";
         index = tmp.lastIndexOf(end);
         String type = tmp.substring(index + end.length());
         String id1 = null;
         String id2 = null;
         if (Constants.RELATING_HISTORY.equals(type)) { // we need only the topicId
            // topic/[topicId]/queue/history/event/threshold.90%
            tmp = tmp.substring(0, index);
            // sessionId or topicId or subjectId
            end = "/";
            index = tmp.lastIndexOf(end);
            id1 = tmp.substring(index + end.length());
            id2 = "";
         }
         else if (Constants.RELATING_CALLBACK.equals(type)) {
            // client/[subjectId]/session/[publicSessionId]/queue/callback/event/threshold.90%
            tmp = tmp.substring(0, index);
            // sessionId or topicId or subjectId
            end = "/";
            index = tmp.lastIndexOf(end);
            id2 = tmp.substring(index + end.length());
            tmp = tmp.substring(0, index);
            index = tmp.lastIndexOf(end);
            if (index > -1)
               tmp = tmp.substring(0, index);
            index = tmp.lastIndexOf(end);
            if (index > -1)
               id1 = tmp.substring(index+1);
            else
               id1 = tmp;
         }
         else if (Constants.RELATING_SUBJECT.equals(type)) {
            // client/[subjectId]/queue/subject/event/threshold.66
            tmp = tmp.substring(0, index);
            end = "/";
            index = tmp.lastIndexOf(end);
            id1 = tmp.substring(index + end.length());
            id2 = "";
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.USER_CONFIGURATION, "QueuePluginManager.registerEvent", "event '" + event + "' is not supported");
         }
         
         if (this.events == null)
            this.events = new HashMap();
         if (this.eventDispatcher == null)
            this.eventDispatcher = dispatcher;
         EventHelper helper = new EventHelper(event, type, id1, id2, value);
         synchronized(this.events) {
            this.events.put(helper.getKey(), helper);
         }
      }
   }
   
   // private final String ME;
   private final Global glob;
   private static Logger log = Logger.getLogger(QueuePluginManager.class.getName());
   private final String pluginEnvClass = "queue"; // Used for env lookup like "queue/history/QueuePlugin[JDBC][1.0]=..."
   public static final String pluginPropertyName = "QueuePlugin";
   private static final String[][] defaultPluginNames = { {"RAM", "org.xmlBlaster.util.queue.ram.RamQueuePlugin"},
                                                          {"JDBC", "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin"},
                                                          {"CACHE", "org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin"} };
   private StorageEventHandler storageEventHandler;
   
   public QueuePluginManager(Global glob) {
      super(glob);
      this.glob = glob;
      this.storageEventHandler = new QueueEventHandler(this, glob);
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
         EventHelper helper = this.storageEventHandler.generateEventHelper(storageId);
         this.storageEventHandler.registerListener(plugin, helper);
      }
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
            if (log.isLoggable(Level.FINE)) log.fine("Choosing for type=" + type + " plugin " + defaultPluginNames[i][1]);
            return defaultPluginNames[i][1];
         }
      }
      log.warning("Choosing for type=" + type + " default plugin " + defaultPluginNames[0][1]);
      return defaultPluginNames[0][1];
   }

   public void registerEvent(I_EventDispatcher dispatcher, String event) throws XmlBlasterException {
      this.storageEventHandler.registerEvent(dispatcher, event);
   }

   public void registerFinished() throws XmlBlasterException {
      this.storageEventHandler.registerFinished();
   }

   /**
    * Enforced by I_StorageSizeListener
    */
   public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
      this.storageEventHandler.changed(storage, numEntries, numBytes, isShutdown);
   }
   
}
