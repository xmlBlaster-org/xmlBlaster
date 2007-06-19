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
import org.xmlBlaster.util.I_EventDispatcher;
import org.xmlBlaster.util.plugin.PluginManagerBase;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.context.ContextNode;
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
 * @author <a href="mailto:laghi@swissinfo.com">Michele Laghi</a>.
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>.
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.queue.html" target="others">engine.queue</a>
 */
public class QueuePluginManager extends PluginManagerBase implements I_QueueSizeListener {
   
   public class EventHelper {
      private String eventType;
      private String type;
      private String id1;
      private String id2;
      long value;
      private String key;
      private boolean procent;
      private boolean alreadyAlarmed;
      
      private EventHelper() {
         
      }
      
      public EventHelper(String eventType, String type, String id1, String id2, String val) throws XmlBlasterException {
         if (id2 == null)
            id2 = "";
         this.eventType = eventType;
         this.type = type;
         this.id1 = id1;
         this.id2 = id2;
         this.key = type + "/" + id1 + "/" + id2;
         int pos = val.lastIndexOf('%');
         if (pos > -1) {
            this.procent = true;
            val = val.substring(0, pos);
         }
         try {
           this.value = Long.parseLong(val); 
         }
         catch (Throwable ex) {
            throw new XmlBlasterException(glob, ErrorCode.USER_CONFIGURATION, "EventHelper", "could not parse treshold string '" + val + "' to a long", ex);
         }
      }

      protected Object clone() {
         EventHelper helper = new EventHelper();
         helper.type = this.type;
         helper.id1 = this.id1;
         helper.id2 = this.id2;
         helper.key = this.key;
         helper.value = this.value;
         helper.procent = this.procent;
         helper.eventType = this.eventType;
         return helper;
      }
      
      public EventHelper getCopy(I_Queue queue) {
         EventHelper ret = (EventHelper)clone();
         long maxValue = 1L;
         if (queue != null)
            maxValue = queue.getMaxNumOfEntries();
         if (maxValue < 0L)
            maxValue = 1L;
         if (ret.procent)
            ret.value = (long)(0.01 * ret.value * maxValue);
         if (ret.value > maxValue) {
            log.warning("The treshold for queue '" + queue.getStorageId().getId() + "' was set to '" + ret.value + "' which is bigger than the maximum value '" + maxValue + "'. will set it to the maximum value");
            ret.value = maxValue;
         }
         return ret;
      }
      
      public boolean shallTrigger(long numEntries) {
         if (numEntries < this.value) {
            if (this.alreadyAlarmed) 
               this.alreadyAlarmed = false; // clear flag since treshold not reached anymore
            return false;
         }
         else {
            if (this.alreadyAlarmed)
               return false;
            this.alreadyAlarmed = true;
            return true;
         }
      }
      
      public String getKey() {
         return this.key;
      }

      public String getId1() {
         return id1;
      }

      public String getId2() {
         return id2;
      }

      public String getType() {
         return type;
      }

      public long getValue() {
         return value;
      }
      
      public String getEventType() {
         return this.eventType;
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
   private Map events;
   private Map unprocessedEvents; // needed in case the EventPlugin is instantiated after the first queue
   private Map processedEvents;
   private I_EventDispatcher eventDispatcher;
   
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
         EventHelper helper = generateEventHelper(storageId);
         registerListener(plugin, helper);
      }
      return plugin;
   }

   private EventHelper generateEventHelper(StorageId storageId) throws XmlBlasterException {
      String type = storageId.getPrefix();
      String postfix = storageId.getPostfix();
      if ("history".equals(type) || "subject".equals(type)) {
         int pos = postfix.lastIndexOf('/');
         if (pos > -1) {
            String id = postfix.substring(pos+1);
            return new EventHelper(null, type, id, "", "0"); // fake
         }
         else
            return null; 
      }
      else if ("callback".equals(type)) {
         int pos = postfix.lastIndexOf('/');
         if (pos > -1) {
            String sessionId = postfix.substring(pos+1);
            String tmp = postfix.substring(0, pos);
            pos = tmp.lastIndexOf('/');
            String subjectId = tmp.substring(pos+1);
            if (pos > -1) {
               return new EventHelper(null, type, subjectId, sessionId, "0"); // fake
            }
            else
               return null;
         }
         else
            return null;
      }
      else
         return null;
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

   private EventHelper getMatchingEvent(EventHelper helper, I_Queue queue) throws XmlBlasterException {
      if (helper == null || this.events == null)
         return null;

      synchronized(this.events) {
         EventHelper event = (EventHelper)this.events.get(helper.getKey());
         if (event != null)
            return event.getCopy(queue);
         EventHelper tmp = new EventHelper(null, helper.getType(), helper.getId2(), "*", "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(queue);
         tmp = new EventHelper(null, helper.getType(), "*", helper.getId2(), "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(queue);
         tmp = new EventHelper(null, helper.getType(), "*", "*", "0");
         event = (EventHelper)this.events.get(tmp.getKey());
         if (event != null)
            return event.getCopy(queue);
      }
      return null;
   }

   private void registerListener(I_Queue queue, EventHelper helper) throws XmlBlasterException {
      if (this.processedEvents != null) {
         synchronized(this.processedEvents) {
            if (this.processedEvents.containsKey(queue))
               return;
            helper = getMatchingEvent(helper, queue);
            if (helper != null) {
               this.processedEvents.put(queue, helper);
               queue.addQueueSizeListener(this);
            }
         }
      }
      else {
         if (this.unprocessedEvents == null)
            this.unprocessedEvents = new HashMap();
         synchronized(this.unprocessedEvents) {
            this.unprocessedEvents.put(queue, helper);
         }
      }
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
      if ("history".equals(type)) { // we need only the topicId
         // topic/[topicId]/queue/history/event/threshold.90%
         tmp = tmp.substring(0, index);
         // sessionId or topicId or subjectId
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      }
      else if ("callback".equals(type)) {
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
      else if ("subject".equals(type)) {
         // client/[subjectId]/queue/subject/event/threshold.66
         tmp = tmp.substring(0, index);
         end = "/";
         index = tmp.lastIndexOf(end);
         id1 = tmp.substring(index + end.length());
         id2 = "";
      }
      else {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, "QueuePluginManager.registerEvent", "event '" + event + "' is not supported");
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

   public void registerFinished() throws XmlBlasterException {
      if (this.unprocessedEvents != null) {
         synchronized (this.unprocessedEvents) {
            I_Queue[] keys = (I_Queue[])this.unprocessedEvents.keySet().toArray(new I_Queue[this.unprocessedEvents.size()]);
            this.processedEvents = new HashMap();
            synchronized(this.processedEvents) {
               for (int i=0; i < keys.length; i++) {
                  I_Queue queue = keys[i];
                  EventHelper tmpHelper = (EventHelper)this.unprocessedEvents.remove(queue);
                  registerListener(queue, tmpHelper);
               }
            }
         }
      }
   }
   
   /**
    * Enforced by I_QueueSizeListener
    * @param queue
    * @param numEntries
    * @param numBytes
    * @param isShutdown
    */
   public void changed(I_Queue queue, long numEntries, long numBytes, boolean isShutdown) {
      if (this.processedEvents == null)
         return;
      EventHelper helper = (EventHelper)this.processedEvents.get(queue);
      if (helper == null)
         return;
      if (!isShutdown && helper.shallTrigger(numEntries)) {
         String txt = "The queue '" + queue.getStorageId().getId() + "' has reached its treshold: '" + numEntries + "' of max '" + queue.getMaxNumOfEntries() + "' (message sent only once)";
         if (this.eventDispatcher != null) {
            String summary = "[" + new java.sql.Timestamp(System.currentTimeMillis()).toString()
             + " " + Thread.currentThread().getName()
             + " " + QueuePluginManager.class.getName() + "]";


            String description = txt;
            String eventType = helper.getEventType();
            this.eventDispatcher.dispatchEvent(summary, description, eventType);
         }
      }
   }

   
   
}
