/*------------------------------------------------------------------------------
Name:      PriorizedDeliveryPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import org.jutils.log.LogChannel;
import org.jutils.init.I_PropertyChangeListener;
import org.jutils.init.PropertyChangeEvent;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDeliveryInterceptor;
import org.xmlBlaster.util.dispatch.plugins.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This dispatcher plugin allows to control how messages are sent to the remote side. 
 * <p>
 * We subscribe to a status message which describes the current connection to the remote side.
 * Depending on a status message we pick messages with specific priorities and send only these.
 * </p>
 * <p>
 * This allows for example to send all messages if a 2MBit connection is up, and send
 * only high priority messages when the line drops to 64kBit.
 * </p>
 * <p>
 * The class ConfigurationParser Javadoc has an xml example of the configuration
 * </p>
 * <p>
 * This plugin class has only one instance per typeVersion for each Global scope.
 * The xmlBlaster client used to subscribe to the status messages is a singleton (in Global.instance() scope).
 * </p>
 * <p>
 * Note that two status sources exist:
 * </p>
 * <ol>
 *   <li>The state of the connection of the dispatcher framework, it may for example be <i>POLLING</i>
 *       for a remote connection or have an <i>ALIVE</i> state or even be in <i>DEAD</i> state.
 *       If a configuration is found for this state it has highest precedence.
 *   </li>
 *   <li>
 *      The status of a status message from outside. This is freely configurable and is for example <i>2M</i>
 *      or <i>BACKUP</i>. This status message has lower precedence.
 *   </li>
 * </ol>
 * @see org.xmlBlaster.util.dispatch.plugins.prio.ConfigurationParser
 * @see org.xmlBlaster.test.dispatch.TestPriorizedDeliveryPlugin
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html" target="others">the delivery.control.plugin requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public final class PriorizedDeliveryPlugin implements I_MsgDeliveryInterceptor, I_Plugin, I_PropertyChangeListener, I_Notify
{
   private String ME = "PriorizedDeliveryPlugin";
   private Global glob;
   private LogChannel log;
   private ConfigurationParser parser = null;
   private final String configPropertyKey = "PriorizedDeliveryPlugin.config";
   private String specificConfigPropertyKey = null;
   private boolean hasSpecificConf = false;

   /** This is the configuration for the current status of the last received status message: */
   private StatusConfiguration currMsgStatusConfiguration;
   private String currMsgStatus;

   private boolean hasDefaultActionOnly = true; // cache for performance
   private XmlBlasterNativeClient xmlBlasterClient;
   private Map deliveryManagerEntryMap = new HashMap();
   private boolean isShutdown = false;

   public DispatchAction QUEUE_ACTION;

   /**
    * Is called by DispatchPluginManager after the instance is created. 
    * @see I_MsgDeliveryInterceptor#initialize(Global, String)
    */
   public void initialize(Global glob, String typeVersion) throws XmlBlasterException {
      this.log = glob.getLog("dispatch");
      this.glob = glob;
      String sessionId = null; // !!!! In future needed for native access?

      synchronized(this) {
         // We only have one status client in the Global scope
         Object obj = glob.getObjectEntry("PriorizedDeliveryPlugin.xmlBlasterAccess");
         if (obj == null) {
            obj = new XmlBlasterNativeClient(glob, this, sessionId);
            glob.addObjectEntry("PriorizedDeliveryPlugin.xmlBlasterAccess", obj);
         }
         xmlBlasterClient = (XmlBlasterNativeClient)obj;
      }

      this.QUEUE_ACTION = new DispatchAction(glob, DispatchAction.QUEUE); // Initialize this constant for later usage

      // Subscribe for configuration properties
      // "PriorizedDeliveryPlugin.config[Priority,1.0]" has precedence over "PriorizedDeliveryPlugin.config"

      // Note: This fires an initial event to statusChanged("startup")

      this.specificConfigPropertyKey = this.configPropertyKey + "[" + typeVersion + "]";
      this.glob.getProperty().addPropertyChangeListener(this.configPropertyKey, "startup", this);
      this.glob.getProperty().addPropertyChangeListener(this.specificConfigPropertyKey, "startup", this);
      log.info(ME, "Succefully initialized");
   }

   /**
    * This is called once for each delivery manager using this plugin. 
    */
   public void addDeliveryManager(DeliveryManager deliveryManager) {
      DeliveryManagerEntry managerEntry = new DeliveryManagerEntry(deliveryManager);
      synchronized (this) {
         this.deliveryManagerEntryMap.put(deliveryManager, managerEntry);
         changeManagerState(deliveryManager, deliveryManager.getDeliveryConnectionsHandler().getState(), false);
      }
      //flushHoldbackQueue(managerEntry);
      log.info(ME, "Stored deliveryManager=" + deliveryManager + ", deliveryManagerEntryMap.size()=" + deliveryManagerEntryMap.size());
   }

   /**
    * Invoked when the configuration <i>PriorizedDeliveryPlugin.config</i> has changed. 
    * Supports changing configuration in hot operation.
    */
   public void propertyChanged(PropertyChangeEvent ev) {
      if (log.TRACE) log.trace(ME, "propertyChanged event: " + ev.toString());
      String newConfig = ev.getNewValue();

      if (newConfig == null || newConfig.equals("startup")) { // && ev.getOldValue() == null)
         if (this.parser != null)
            return; // Ignore startup events without any setting
         // We need to initialize this.parser so we proceed with default setting
         newConfig = "<msgDispatch/>";
      }

      if (this.specificConfigPropertyKey.equals(ev.getKey())) 
         hasSpecificConf = true;

      if (hasSpecificConf && this.configPropertyKey.equals(ev.getKey()))
         return;  // Ignore unspecific configuration

      synchronized (this) {
         ConfigurationParser oldParser = this.parser;
         StatusConfiguration oldConf = this.currMsgStatusConfiguration;
         boolean oldDef = this.hasDefaultActionOnly;

         try {
            // Parse and set the new configuration ...
            this.parser = new ConfigurationParser(this.glob, newConfig);
         }
         catch (XmlBlasterException e) {
            log.error(ME, "The new property '" + ev.toString() + " is ignored: " + e.toString());
            return;
         }

         try {
            // Now subscribe to messages according to new configuration ...
            subscribeStatusMessages();

            // Activate ...
            statusChanged(this.currMsgStatus);
            log.info(ME, "Reconfigured priorized delivery plugin with '" + ev.getKey() + "', currMsgStatus=" + this.currMsgStatus);
         }
         catch (XmlBlasterException e) {
            log.error(ME, "The new property '" + ev.toString() + " is ignored: " + e.toString());
            // rollback ...
            this.parser = oldParser;
            this.currMsgStatusConfiguration = oldConf;
            this.hasDefaultActionOnly = oldDef;
            if (this.parser != null) {
               try { subscribeStatusMessages(); } catch (XmlBlasterException e2) { log.error(ME, "Rollback to old configuration failed: "+ e2.toString()); }
               statusChanged(this.currMsgStatus);
            }
         }
      }
   }

   /**
    * Subscribe to messages according to the current configuration. 
    */
   private void subscribeStatusMessages() throws XmlBlasterException {
      this.xmlBlasterClient.unSubscribeStatusMessages(this); // cleanup first
      Iterator it = this.parser.getStatusConfigurationMap().values().iterator();
      while (it.hasNext()) {
         StatusConfiguration conf = (StatusConfiguration)it.next();
         this.xmlBlasterClient.subscribeToStatusMessage(conf.getOid(), this);
      }
   }
 
   /**
    * Enforced by I_Plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) {
      java.util.Properties props = pluginInfo.getParameters();
   }

   /**
    * Enforced by I_Plugin
    * @return "Priority"
    */
   public String getType() { return "Priority"; }

   /**
    * Enforced by I_Plugin
    * @return "1.0"
    */
   public final String getVersion() { return "1.0"; }

   /**
    * Changing the status of the delivery strategy. 
    * <p>
    * Enforced by I_Notify
    * </p>
    * On initialize: addPropertyChangeListener(this.configPropertyKey, "startup", this);
    * an initial event is fired an calls this method to initialize all attributes here
    */
   public final void statusChanged(String status) {
      if (log.TRACE) log.trace(ME, "statusChanged(status=" + status + ")");
      synchronized (this) {
         String oldStatus = this.currMsgStatus;
         this.currMsgStatus = status;
         this.currMsgStatusConfiguration = parser.getStatusConfiguration(currMsgStatus);
         this.hasDefaultActionOnly = this.currMsgStatusConfiguration.defaultActionOnly(); // cache for performance
         log.info(ME, "Changed priorized delivery from old status=" + oldStatus + " to new status=" + this.currMsgStatus);
         if ((oldStatus==null&&this.currMsgStatus!=null) ||
             (oldStatus!=null && !oldStatus.equals(this.currMsgStatus))) {
            Iterator it = this.deliveryManagerEntryMap.values().iterator();
            while (it.hasNext()) {
               DeliveryManagerEntry managerEntry = (DeliveryManagerEntry)it.next();
               managerEntry.setCurrConnectionStateConfiguration(parser.getStatusConfiguration(managerEntry.getCurrConnectionState()));
               flushHoldbackQueue(managerEntry);
            }
         }
      }
      //deliveryManager.activateDeliveryWorker();
   }

   /**
    * Lookup the corresponding DispatchAction object this message priority. 
    */
   private final DispatchAction getDispatchAction(DeliveryManagerEntry managerEntry, MsgQueueEntry entry) {

      if (managerEntry.getCurrConnectionStateConfiguration() != null) { // Dispatcher state has precedence
         return managerEntry.getCurrConnectionStateConfiguration().getDispatchAction(entry.getPriorityEnum());
      }

      return this.currMsgStatusConfiguration.getDispatchAction(entry.getPriorityEnum());
   }

   /**
    * Called when new messages are available. 
    * @see I_MsgDeliveryInterceptor#doActivate(DeliveryManager)
    */
   public final boolean doActivate(DeliveryManager deliveryManager) {
      return true; // The DeliveryManager knows what and why it does it

      /*
      if (deliveryManager.getNotifyCounter() > 0 && deliveryManager.getQueue().getNumOfEntries() > 0) {
         if (log.TRACE) log.trace(ME, "doAvtivate -> true: notifyCounter=" + deliveryManager.getNotifyCounter() + " currEntries=" + deliveryManager.getQueue().getNumOfEntries());
         return true;
      }

      return false;
      */
   }

   /**
    * Enforced by I_MsgDeliveryInterceptor
    * <p>
    * NOTE: When copying entries from one queue to another one we have
    * to take care that the reference counter in msgstore is not temporary zero (and is
    * garbage collected). This is avoided by a peek() and later remove() - which is
    * necessary for durable messages anyhow to ensure 100% crash safety.
    * </p>
    * @see I_MsgDeliveryInterceptor#handleNextMessages(DeliveryManager, ArrayList)
    */
   public final ArrayList handleNextMessages(DeliveryManager deliveryManager, ArrayList entries) throws XmlBlasterException {

      // take messages from queue (none blocking) ...
      ArrayList entryList = deliveryManager.getQueue().peekSamePriority(-1, -1L);

      // filter expired entries etc. ...
      // you should always call this method after taking messages from queue
      entryList = deliveryManager.prepareMsgsFromQueue(entryList);

      DeliveryManagerEntry managerEntry = getDeliveryManagerEntry(deliveryManager);
      if (managerEntry == null) {
         String text = "Internal error: can't queue " + ((entries==null) ? 0 : entries.size()) +
                       " messages, deliveryManager=" + deliveryManager +
                       " is unknown, deliveryManagerEntryMap.size()=" + ((deliveryManagerEntryMap==null) ? 0 : deliveryManagerEntryMap.size());
         log.error(ME, text);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, text);
      }

      if (managerEntry.getCurrConnectionState() == ConnectionStateEnum.ALIVE &&
          managerEntry.getCurrConnectionStateConfiguration() == null &&
          this.hasDefaultActionOnly) {
         if (log.TRACE) log.trace(ME, "We have default action only, returning all " + entryList.size() + " messages");
         return entryList;
      }

      if (log.TRACE) log.trace(ME, "Working with " + entryList.size() + " messages ...");

      // ... do plugin specific work ...
      ArrayList resultList = new ArrayList();
      for (int i=0; i<entryList.size(); i++) {
         MsgQueueEntry entry = (MsgQueueEntry)entryList.get(i);
         DispatchAction action = getDispatchAction(managerEntry, entry);

         if (log.TRACE) log.trace(ME, "Working on '" + entry.getLogId() + "', action=" + action.getAction() + " from sender " + entry.getSender());

         if (managerEntry.getCurrConnectionState() == ConnectionStateEnum.ALIVE) {
            if (entry.isInternal()) {
               log.info(ME, "Sending out of bound internal message '" + entry.getLogId() + "'");
               resultList.add(entry);
               continue; // Send internal message out of bound
            }

            if (this.xmlBlasterClient.getLoginName().equals(entry.getSender().getLoginName())) {
               log.info(ME, "Sending out of bound PtP message '" + entry.getLogId() + "'");
               resultList.add(entry);
               continue; // Send PtP notifications out of bound to avoid looping
            }
         }

         if (managerEntry.getCurrConnectionState() != ConnectionStateEnum.ALIVE && action.doSend()) {
            log.error(ME, "We are in state " + managerEntry.getCurrConnectionState() + " and the configuration tells us to send nevertheless, we queue instead: " + entry.getLogId());
            action = this.QUEUE_ACTION;
         }

         if (action.doSend()) {
            resultList.add(entry);
         }
         else if (action.doQueue()) {
            // ignore in async - put to queue in sync mode !!
            if (log.TRACE) log.trace(ME, "Queueing holdback message " + entry.getLogId());
            try {
               putToHoldbackQueue(managerEntry, entry);
            }
            catch (XmlBlasterException e) {
               deliveryManager.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entry, e));
            }
            try {
               deliveryManager.getQueue().removeRandom(entry);
            }
            catch (XmlBlasterException e) {
               log.error(ME, "PANIC: Can't remove " + entry.toXml("") + " from queue '" + deliveryManager.getQueue().getStorageId() + "': " + e.toString());
               e.printStackTrace();
            }
         }
         else if (action.doDestroy()) {
            try {
               deliveryManager.getQueue().removeRandom(entry);
            }
            catch (XmlBlasterException e) {
               log.error(ME, "PANIC: Can't remove " + entry.toXml("") + " from queue '" + deliveryManager.getQueue().getStorageId() + "': " + e.toString());
               e.printStackTrace();
            }
         }

         if (action.doNotifySender()) {
            this.xmlBlasterClient.sendPtPMessage(entry, this.specificConfigPropertyKey, action.getAction(), this.currMsgStatus);
         }
      }
      entryList.clear();

      return resultList;
   }

   private DeliveryManagerEntry getDeliveryManagerEntry(DeliveryManager deliveryManager) {
      synchronized (this) {
         return (DeliveryManagerEntry)this.deliveryManagerEntryMap.get(deliveryManager);
      }
   }

   private void putToHoldbackQueue(DeliveryManagerEntry managerEntry, MsgQueueEntry entry) throws XmlBlasterException {
      I_Queue queue = managerEntry.getHoldbackQueue();
      if (queue == null) {
         synchronized (this) {
            if (queue == null) {
               // Create a queue for this plugin, inherit the settings from the original queue of DeliveryManager
               QueuePropertyBase queueProperties = (QueuePropertyBase)managerEntry.getDeliveryManager().getQueue().getProperties();
               String type = queueProperties.getType();
               String version = queueProperties.getVersion();
               String typeVersion = glob.getProperty().get("PriorizedDeliveryPlugin.queue.plugin", type+","+version);
               StorageId storageId = new StorageId("PriorizedDeliveryPlugin", managerEntry.getDeliveryManager().getQueue().getStorageId().getPostfix());
               queue = glob.getQueuePluginManager().getPlugin(typeVersion, storageId, queueProperties);
               queue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting (otherwise we have memory leaks)
               managerEntry.setHoldbackQueue(queue);
               log.info(ME, "Created holdback queue '" + queue.getStorageId() + "' with " + queue.getNumOfEntries() + " entries");
            }
         }
      }
      queue.put(entry, true);
      if (log.TRACE) log.trace(ME, "Filled to holdback queue '" + queue.getStorageId() + "' one entry, it has now " + queue.getNumOfEntries() + " entries");
   }

   /**
    * All entries from our holdback queue are flushed to the official queues
    * of the DeliveryManager
    */
   private void flushHoldbackQueue(DeliveryManagerEntry managerEntry) {
      synchronized (this)  {
         DeliveryManager deliveryManager = managerEntry.getDeliveryManager();
         I_Queue holdbackQueue = managerEntry.getHoldbackQueue();
         if (holdbackQueue != null && holdbackQueue.getNumOfEntries() > 0) {
            log.info(ME, "Flushing " + holdbackQueue.getNumOfEntries() + " entries from holdback queue " + holdbackQueue.getStorageId());
            ArrayList list = null;
            int lastSize = -99;
            while (holdbackQueue.getNumOfEntries() > 0) {

               try {
                  list = holdbackQueue.peek(-1, -1);
                  if (holdbackQueue.getNumOfEntries() == lastSize) {
                     log.error(ME, "PANIC: " + holdbackQueue.getNumOfEntries() + " entries from holdback queue " + holdbackQueue.getStorageId() + " can't be flushed, giving up!");
                     break;
                  }
                  lastSize = (int)holdbackQueue.getNumOfEntries();
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "PANIC: Can't flush holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries: " + e.toString());
                  e.printStackTrace();
                  continue;
               }

               MsgQueueEntry[] queueEntries = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
               // On error we send them as dead letters, as we don't know what to do with them in our holdback queue
               try {
                  deliveryManager.getQueue().put(queueEntries, false);
               }
               catch (XmlBlasterException e) {
                  log.warn(ME, "flushHoldbackQueue() failed: " + e.toString());
                  // errorCode == "ONOVERFLOW"
                  deliveryManager.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, queueEntries, e));
               }

               try {
                  long num = holdbackQueue.remove(list.size(), -1);
                  if (num != list.size()) {
                     log.error(ME, "PANIC: Expected to remove from holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries " + list.size() + " entries, but only " + num + " where removed");
                  }
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "PANIC: Expected to remove from holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries " + list.size() + " entries: " + e.toString());
               }
            }

            holdbackQueue.clear();
            deliveryManager.notifyAboutNewEntry();
         }
         else {
            log.info(ME, "No holdback queue for " + deliveryManager.getId() + ", nothing to flush");
         }
      }
   }

   /**
    * Call by DeliveryConnectionsHandler on state transition. 
    * <p />
    * Enforced by interface I_ConnectionStateListener
    */
   public final void toAlive(DeliveryManager deliveryManager, ConnectionStateEnum oldState) {
      changeManagerState(deliveryManager, ConnectionStateEnum.ALIVE, true);
   }

   /**
    * Call by DeliveryConnectionsHandler on state transition
    * <p />
    * Enforced by interface I_ConnectionStateListener
    */
   public final void toPolling(DeliveryManager deliveryManager, ConnectionStateEnum oldState) {
      changeManagerState(deliveryManager, ConnectionStateEnum.POLLING, true);
   }

   /**
    * Call by DeliveryConnectionsHandler on state transition
    * <p />
    * Enforced by interface I_ConnectionStateListener
    */
   public final void toDead(DeliveryManager deliveryManager, ConnectionStateEnum oldState, String errorText) {
      changeManagerState(deliveryManager, ConnectionStateEnum.DEAD, true);
   }

   private DeliveryManagerEntry changeManagerState(DeliveryManager deliveryManager, ConnectionStateEnum newState, boolean flush) {
      DeliveryManagerEntry managerEntry = getDeliveryManagerEntry(deliveryManager);
      if (managerEntry == null) {
         throw new IllegalArgumentException("Internal error in " + newState + ": deliveryManager=" + deliveryManager + " is unknown, deliveryManagerEntryMap.size()=" + deliveryManagerEntryMap.size());
      }
      managerEntry.setCurrConnectionState(newState);
      StatusConfiguration tmp = parser.getStatusConfiguration(newState);
      managerEntry.setCurrConnectionStateConfiguration(tmp);
      if (tmp != null)
         log.info(ME, "Changing to " + newState + ", found configuration is '" + tmp.toXml(null) + "'");
      else 
         log.info(ME, "Changing to connection state " + newState);
      if (flush)
         flushHoldbackQueue(managerEntry);
      return managerEntry; 
   }

   /**
    * Deregister a delivery manager. 
    * @see I_MsgDeliveryInterceptor#shutdown(DeliveryManager)
    */ 
   public void shutdown(DeliveryManager deliveryManager) {
      DeliveryManagerEntry de = null;
      synchronized (this)  {
         de = (DeliveryManagerEntry)this.deliveryManagerEntryMap.remove(deliveryManager);
      }
      if (de != null) {
         if (de.getHoldbackQueue() != null) {
            try { de.getHoldbackQueue().destroy(); } catch (XmlBlasterException e) { log.error(ME, "Problems on shutdown of holdback queue: " + e.toString()); }
            de.getHoldbackQueue().shutdown(true);
         }
      }
   }

   /**
    */ 
   public void shutdown(boolean force) {
      synchronized (this) {
         if (isShutdown) return;

         glob.getProperty().removePropertyChangeListener(configPropertyKey, this);
         this.deliveryManagerEntryMap.clear();
         this.xmlBlasterClient.shutdown(this);
         isShutdown = true;
      }
   }

   /**
    * @return true if shutdown
    */
   public boolean isShutdown() {
      return isShutdown;
   }

   /**
    * @return a human readable usage help string
    */
   public String usage() {
      return "";
   }

   /**
    * @see I_MsgDeliveryInterceptor#toXml(String)
    */
   public String toXml(String extraOffset) {
      return "";
   }
}
