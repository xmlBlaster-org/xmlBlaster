/*------------------------------------------------------------------------------
Name:      PriorizedDispatchPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch.plugins.prio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.property.I_PropertyChangeListener;
import org.xmlBlaster.util.property.PropertyChangeEvent;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

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
 * @see org.xmlBlaster.test.dispatch.TestPriorizedDispatchPlugin
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html" target="others">the dispatch.control.plugin requirement</a>
 * @author xmlBlaster@marcelruff.info
 */
public final class PriorizedDispatchPlugin implements I_MsgDispatchInterceptor, I_Plugin, I_PropertyChangeListener, I_Notify
{
   private String ME = "PriorizedDispatchPlugin";
   private Global glob;
   private static Logger log = Logger.getLogger(PriorizedDispatchPlugin.class.getName());
   private ConfigurationParser parser = null;
   public static final String CONFIG_PROPERTY_KEY = "PriorizedDispatchPlugin/config";
   private String specificConfigPropertyKey = null;
   private boolean hasSpecificConf = false;

   /** This is the configuration for the current status of the last received status message: */
   private StatusConfiguration currMsgStatusConfiguration;
   private String currMsgStatus;

   private boolean hasDefaultActionOnly = true; // cache for performance
   private XmlBlasterNativeClient xmlBlasterClient;
   private Map dispatchManagerEntryMap = new HashMap();
   private boolean isShutdown = false;

   public DispatchAction QUEUE_ACTION;

   /**
    * Is called by DispatchPluginManager after the instance is created. 
    * @see I_MsgDispatchInterceptor#initialize(Global, String)
    */
   public void initialize(Global glob, String typeVersion) throws XmlBlasterException {

      this.glob = glob;
      String sessionId = null; // !!!! In future needed for native access?

      synchronized(this) {
         // We only have one status client in the Global scope
         Object obj = glob.getObjectEntry("PriorizedDispatchPlugin.xmlBlasterAccess");
         if (obj == null) {
            obj = new XmlBlasterNativeClient(glob, this, sessionId);
            glob.addObjectEntry("PriorizedDispatchPlugin.xmlBlasterAccess", obj);
         }
         xmlBlasterClient = (XmlBlasterNativeClient)obj;
      }

      this.QUEUE_ACTION = new DispatchAction(glob, DispatchAction.QUEUE); // Initialize this constant for later usage

      // Subscribe for configuration properties
      // "PriorizedDispatchPlugin/config[Priority,1.0]" has precedence over "PriorizedDispatchPlugin/config"

      // Note: This fires an initial event to statusChanged("startup")

      this.specificConfigPropertyKey = CONFIG_PROPERTY_KEY + "[" + typeVersion + "]";
      this.glob.getProperty().addPropertyChangeListener(CONFIG_PROPERTY_KEY, "startup", this);
      this.glob.getProperty().addPropertyChangeListener(this.specificConfigPropertyKey, "startup", this);
      log.info("Succefully initialized");
   }

   /**
    * This is called once for each dispatch manager using this plugin. 
    */
   public void addDispatchManager(I_DispatchManager dispatchManager) {
      DispatchManagerEntry managerEntry = new DispatchManagerEntry(dispatchManager);
      synchronized (this) {
         this.dispatchManagerEntryMap.put(dispatchManager, managerEntry);
         changeManagerState(dispatchManager, dispatchManager.getDispatchConnectionsHandler().getState(), false);
      }
      //flushHoldbackQueue(managerEntry);
      log.info("Stored dispatchManager=" + dispatchManager.getId() + ", dispatchManagerEntryMap.size()=" + dispatchManagerEntryMap.size());
   }

   /**
    * Invoked when the configuration <i>PriorizedDispatchPlugin/config</i> has changed. 
    * Supports changing configuration in hot operation.
    */
   public void propertyChanged(PropertyChangeEvent ev) {
      if (log.isLoggable(Level.FINE)) log.fine("propertyChanged event: " + ev.toString());
      String newConfig = ev.getNewValue();

      if (newConfig == null || newConfig.equals("startup")) { // && ev.getOldValue() == null)
         if (this.parser != null)
            return; // Ignore startup events without any setting
         // We need to initialize this.parser so we proceed with default setting
         newConfig = "<msgDispatch/>";
      }

      if (this.specificConfigPropertyKey.equals(ev.getKey())) 
         hasSpecificConf = true;

      if (hasSpecificConf && CONFIG_PROPERTY_KEY.equals(ev.getKey()))
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
            log.severe("The new property '" + ev.toString() + " is ignored: " + e.getMessage());
            return;
         }

         try {
            // Now subscribe to messages according to new configuration ...
            subscribeStatusMessages();

            // Activate ...
            statusChanged(this.currMsgStatus);
            log.info("Reconfigured priorized dispatch plugin with '" + ev.getKey() + "', currMsgStatus=" + this.currMsgStatus);
         }
         catch (XmlBlasterException e) {
            log.severe("The new property '" + ev.toString() + " is ignored: " + e.getMessage());
            // rollback ...
            this.parser = oldParser;
            this.currMsgStatusConfiguration = oldConf;
            this.hasDefaultActionOnly = oldDef;
            if (this.parser != null) {
               try { subscribeStatusMessages(); } catch (XmlBlasterException e2) { log.severe("Rollback to old configuration failed: "+ e2.getMessage()); }
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
      //java.util.Properties props = pluginInfo.getParameters();
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
    * Changing the status of the dispatch strategy. 
    * <p>
    * Enforced by I_Notify
    * </p>
    * On initialize: addPropertyChangeListener(this.CONFIG_PROPERTY_KEY, "startup", this);
    * an initial event is fired an calls this method to initialize all attributes here
    */
   public final void statusChanged(String status) {
      if (log.isLoggable(Level.FINE)) log.fine("statusChanged(status=" + status + ")");
      synchronized (this) {
         String oldStatus = this.currMsgStatus;
         this.currMsgStatus = status;
         this.currMsgStatusConfiguration = parser.getStatusConfiguration(currMsgStatus);
         this.hasDefaultActionOnly = this.currMsgStatusConfiguration.defaultActionOnly(); // cache for performance
         log.info("Changed priorized dispatch from old status=" + oldStatus + " to new status=" + this.currMsgStatus);
         if ((oldStatus==null&&this.currMsgStatus!=null) ||
             (oldStatus!=null && !oldStatus.equals(this.currMsgStatus))) {
            Iterator it = this.dispatchManagerEntryMap.values().iterator();
            while (it.hasNext()) {
               DispatchManagerEntry managerEntry = (DispatchManagerEntry)it.next();
               managerEntry.setCurrConnectionStateConfiguration(parser.getStatusConfiguration(managerEntry.getCurrConnectionState()));
               flushHoldbackQueue(managerEntry);
            }
         }
      }
      //dispatchManager.activateDispatchWorker();
   }

   /**
    * Lookup the corresponding DispatchAction object this message priority. 
    */
   private final DispatchAction getDispatchAction(DispatchManagerEntry managerEntry, MsgQueueEntry entry) {

      if (managerEntry.getCurrConnectionStateConfiguration() != null) { // Dispatcher state has precedence
         return managerEntry.getCurrConnectionStateConfiguration().getDispatchAction(entry.getPriorityEnum());
      }

      return this.currMsgStatusConfiguration.getDispatchAction(entry.getPriorityEnum());
   }

   /**
    * Called when new messages are available. 
    * @see I_MsgDispatchInterceptor#doActivate(DispatchManager)
    */
   public final boolean doActivate(I_DispatchManager dispatchManager) {
      return true; // The DispatchManager knows what and why it does it

      /*
      if (dispatchManager.getNotifyCounter() > 0 && dispatchManager.getQueue().getNumOfEntries() > 0) {
         if (log.isLoggable(Level.FINE)) log.trace(ME, "doAvtivate -> true: notifyCounter=" + dispatchManager.getNotifyCounter() + " currEntries=" + dispatchManager.getQueue().getNumOfEntries());
         return true;
      }

      return false;
      */
   }

   /**
    * Enforced by I_MsgDispatchInterceptor. 
    * <p>
    * NOTE: When copying entries from one queue to another one we have
    * to take care that the reference counter in msgUnitStore is not temporary zero (and is
    * garbage collected). This is avoided by a peek() and later remove() - which is
    * necessary for persistent messages anyhow to ensure 100% crash safety.
    * </p>
    * @see I_MsgDispatchInterceptor#handleNextMessages(DispatchManager, ArrayList)
    */
   public final List<I_Entry> handleNextMessages(I_DispatchManager dispatchManager, List<I_Entry> entries)
         throws XmlBlasterException {

      // take messages from queue (none blocking) ...
      List<I_Entry> entryList = dispatchManager.getQueue().peekSamePriority(-1, -1L);

      // filter expired entries etc. ...
      // you should always call this method after taking messages from queue
      entryList = dispatchManager.prepareMsgsFromQueue(entryList);

      DispatchManagerEntry managerEntry = getDispatchManagerEntry(dispatchManager);
      if (managerEntry == null) {
         String text = "Internal error: can't queue " + ((entries==null) ? 0 : entries.size()) +
                       " messages, dispatchManager=" + dispatchManager +
                       " is unknown, dispatchManagerEntryMap.size()=" + ((dispatchManagerEntryMap==null) ? 0 : dispatchManagerEntryMap.size());
         log.severe(text);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, text);
      }

      if (managerEntry.getCurrConnectionState() == ConnectionStateEnum.ALIVE &&
          managerEntry.getCurrConnectionStateConfiguration() == null &&
          this.hasDefaultActionOnly) {
         if (log.isLoggable(Level.FINE)) log.fine("We have default action only, returning all " + entryList.size() + " messages");
         return entryList;
      }

      if (log.isLoggable(Level.FINE)) log.fine("Working with " + entryList.size() + " messages ...");

      // ... do plugin specific work ...
      ArrayList resultList = new ArrayList();
      for (int i=0; i<entryList.size(); i++) {
         MsgQueueEntry entry = (MsgQueueEntry)entryList.get(i);
         DispatchAction action = getDispatchAction(managerEntry, entry);

         if (log.isLoggable(Level.FINE)) log.fine("Working on '" + entry.getLogId() + "', action=" + action.getAction() + " from sender " + entry.getSender());

         if (managerEntry.getCurrConnectionState() == ConnectionStateEnum.ALIVE) {
            if (entry.isInternal()) {
               log.info("Sending out of bound internal message '" + entry.getLogId() + "'");
               resultList.add(entry);
               continue; // Send internal message out of bound
            }

            if (this.xmlBlasterClient.getLoginName().equals(entry.getSender().getLoginName())) {
               log.info("Sending out of bound PtP message '" + entry.getLogId() + "'");
               resultList.add(entry);
               continue; // Send PtP notifications out of bound to avoid looping
            }
         }

         if (managerEntry.getCurrConnectionState() != ConnectionStateEnum.ALIVE && action.doSend()) {
            log.severe("We are in state " + managerEntry.getCurrConnectionState() + " and the configuration tells us to send nevertheless, we queue instead: " + entry.getLogId());
            action = this.QUEUE_ACTION;
         }

         if (action.doSend()) {
            resultList.add(entry);
         }
         else if (action.doQueue()) {
            // ignore in async - put to queue in sync mode !!
            if (log.isLoggable(Level.FINE)) log.fine("Queueing holdback message " + entry.getLogId());
            try {
               putToHoldbackQueue(managerEntry, entry);
            }
            catch (XmlBlasterException e) {
               dispatchManager.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entry, dispatchManager, e));
            }
            if (log.isLoggable(Level.FINE)) log.fine("Removing from callback queue " + entry.getLogId() + " (is now a holdback message)");
            try {
               dispatchManager.getQueue().removeRandom(entry);
               if (log.isLoggable(Level.FINE)) log.fine("Callback queue size is now " + dispatchManager.getQueue().getNumOfEntries());
            }
            catch (XmlBlasterException e) {
               log.severe("PANIC: Can't remove " + entry.toXml("") + " from queue '" + dispatchManager.getQueue().getStorageId() + "': " + e.getMessage());
               e.printStackTrace();
            }
         }
         else if (action.doDestroy()) {
            try {
               dispatchManager.getQueue().removeRandom(entry);
            }
            catch (XmlBlasterException e) {
               log.severe("PANIC: Can't remove " + entry.toXml("") + " from queue '" + dispatchManager.getQueue().getStorageId() + "': " + e.getMessage());
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

   private DispatchManagerEntry getDispatchManagerEntry(I_DispatchManager dispatchManager) {
      synchronized (this) {
         return (DispatchManagerEntry)this.dispatchManagerEntryMap.get(dispatchManager);
      }
   }

   private void putToHoldbackQueue(DispatchManagerEntry managerEntry, MsgQueueEntry entry) throws XmlBlasterException {
      I_Queue queue = managerEntry.getHoldbackQueue();
      if (queue == null) {
         synchronized (this) {
            if (queue == null) {
               // Create a queue for this plugin, inherit the settings from the original queue of DispatchManager
               I_Queue origQueue = managerEntry.getDispatchManager().getQueue();
               QueuePropertyBase queueProperties = (QueuePropertyBase) origQueue.getProperties();
               String type = queueProperties.getType();
               String version = queueProperties.getVersion();
               String typeVersion = glob.getProperty().get("PriorizedDispatchPlugin.queue.plugin", type+","+version);
               StorageId storageId = new StorageId(glob, origQueue.getStorageId().getXBStore().getNode(),
                     "PriorizedDispatchPlugin", origQueue.getStorageId().getXBStore().getPostfix());
               queue = glob.getQueuePluginManager().getPlugin(typeVersion, storageId, queueProperties);
               queue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting (otherwise we have memory leaks)
               managerEntry.setHoldbackQueue(queue);
               log.info("Created holdback queue '" + queue.getStorageId() + "' with " + queue.getNumOfEntries() + " entries");
            }
         }
      }
      queue.put(entry, true);
      if (log.isLoggable(Level.FINE)) log.fine("Filled to holdback queue '" + queue.getStorageId() + "' one entry '" + entry.getLogId() +
                               "', it has now " + queue.getNumOfEntries() + " entries");
   }

   /**
    * All entries from our holdback queue are flushed to the official queues
    * of the DispatchManager
    */
   private void flushHoldbackQueue(DispatchManagerEntry managerEntry) {
      synchronized (this)  {
         I_DispatchManager dispatchManager = managerEntry.getDispatchManager();
         I_Queue holdbackQueue = managerEntry.getHoldbackQueue();
         if (holdbackQueue != null && holdbackQueue.getNumOfEntries() > 0) {
            log.info("Flushing " + holdbackQueue.getNumOfEntries() + " entries from holdback queue " + holdbackQueue.getStorageId());
            List<I_Entry> list = null;
            int lastSize = -99;
            while (holdbackQueue.getNumOfEntries() > 0) {

               try {
                  list = holdbackQueue.peek(-1, -1);
                  if (holdbackQueue.getNumOfEntries() == lastSize) {
                     log.severe("PANIC: " + holdbackQueue.getNumOfEntries() + " entries from holdback queue " + holdbackQueue.getStorageId() + " can't be flushed, giving up!");
                     break;
                  }
                  lastSize = (int)holdbackQueue.getNumOfEntries();
               }
               catch (XmlBlasterException e) {
                  log.severe("PANIC: Can't flush holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries: " + e.getMessage());
                  e.printStackTrace();
                  continue;
               }

               MsgQueueEntry[] queueEntries = (MsgQueueEntry[])list.toArray(new MsgQueueEntry[list.size()]);
               // On error we send them as dead letters, as we don't know what to do with them in our holdback queue
               try {
                  dispatchManager.getQueue().put(queueEntries, false);
               }
               catch (XmlBlasterException e) {
                  log.warning("flushHoldbackQueue() failed: " + e.getMessage());
                  // errorCode == "ONOVERFLOW"
                  dispatchManager.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, queueEntries, dispatchManager, e));
               }

               try {
                  long num = holdbackQueue.removeNum(list.size());
                  if (num != list.size()) {
                     log.severe("PANIC: Expected to remove from holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries " + list.size() + " entries, but only " + num + " where removed");
                  }
               }
               catch (XmlBlasterException e) {
                  log.severe("PANIC: Expected to remove from holdbackQueue '" + holdbackQueue.getStorageId() + "' with " + holdbackQueue.getNumOfEntries() + " entries " + list.size() + " entries: " + e.getMessage());
               }
            }

            holdbackQueue.clear();
            dispatchManager.notifyAboutNewEntry();
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine("No holdback queue for " + dispatchManager.getId() + ", nothing to flush");
         }
      }
   }

   /**
    * Call by DispatchConnectionsHandler on state transition. 
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public final void toAlive(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      changeManagerState(dispatchManager, ConnectionStateEnum.ALIVE, true);
   }

   public void toAliveSync(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }
   
   /**
    * Call by DispatchConnectionsHandler on state transition
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public final void toPolling(I_DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      changeManagerState(dispatchManager, ConnectionStateEnum.POLLING, true);
   }

   /**
    * Call by DispatchConnectionsHandler on state transition
    * <p />
    * Enforced by interface I_ConnectionStatusListener
    */
   public final void toDead(I_DispatchManager dispatchManager, ConnectionStateEnum oldState, XmlBlasterException xmlBlasterException) {
      changeManagerState(dispatchManager, ConnectionStateEnum.DEAD, true);
   }

   private DispatchManagerEntry changeManagerState(I_DispatchManager dispatchManager, ConnectionStateEnum newState, boolean flush) {
      DispatchManagerEntry managerEntry = getDispatchManagerEntry(dispatchManager);
      if (managerEntry == null) {
         throw new IllegalArgumentException("Internal error in " + newState + ": dispatchManager=" + dispatchManager.toXml("") + " is unknown, dispatchManagerEntryMap.size()=" + dispatchManagerEntryMap.size());
      }
      managerEntry.setCurrConnectionState(newState);
      StatusConfiguration tmp = parser.getStatusConfiguration(newState);
      managerEntry.setCurrConnectionStateConfiguration(tmp);
      if (tmp != null)
         log.info("Changing to " + newState + ", found configuration is '" + tmp.toXml(null) + "'");
      else 
         log.info("Changing to connection state " + newState);
      if (flush)
         flushHoldbackQueue(managerEntry);
      return managerEntry; 
   }

   /**
    * @return A current snapshot (thread save etc)
    */
   private DispatchManagerEntry[] getDispatchManagerEntryArr() {
     synchronized (this) {
        return (DispatchManagerEntry[])this.dispatchManagerEntryMap.values().toArray(new DispatchManagerEntry[this.dispatchManagerEntryMap.size()]);
      }
   }

   /**
    * Deregister a dispatch manager. 
    * @see I_MsgDispatchInterceptor#shutdown(DispatchManager)
    */ 
   public void shutdown(I_DispatchManager dispatchManager) throws XmlBlasterException {
      DispatchManagerEntry de = null;
      synchronized (this)  {
         de = (DispatchManagerEntry)this.dispatchManagerEntryMap.remove(dispatchManager);
      }
      if (de != null) {
         if (de.getHoldbackQueue() != null) {
            // org.xmlBlaster.test.dispatch.TestPriorizedDispatchWithLostCallback throws an exception if
            // we activate the following line -> we need to investigate this issue
            //try { de.getHoldbackQueue().destroy(); } catch (XmlBlasterException e) { log.error(ME, "Problems on shutdown of holdback queue: " + e.getMessage()); }
            de.getHoldbackQueue().shutdown();
         }
      }
      synchronized (this)  {
         if (this.dispatchManagerEntryMap.size() == 0)
            shutdown(); // Remove the whole plugin on last DispatchManager
      }
   }

   /**
   */ 
   public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) log.fine("shutdown()");
      synchronized (this) {
         if (isShutdown) return;

         glob.getProperty().removePropertyChangeListener(CONFIG_PROPERTY_KEY, this);

         DispatchManagerEntry[] arr = getDispatchManagerEntryArr();
         for(int i=0; i<arr.length; i++) {
            shutdown(arr[i].getDispatchManager());
         }
         if (this.dispatchManagerEntryMap.size() > 0) {
            log.severe("Internal cleanup error in dispatchManagerEntryMap");
         }
         this.dispatchManagerEntryMap.clear();
         
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
    * @see I_MsgDispatchInterceptor#toXml(String)
    */
   public String toXml(String extraOffset) {
      return "";
   }

   /**
    * Not doing anything in this method since no cleanup needed.
    */
   public void postHandleNextMessages(I_DispatchManager dispatchManager, MsgUnit[] processedEntries) throws XmlBlasterException {
   }

   /**
    * Not doing anything in this method since no Exception handling is done.
    */
   public void onDispatchWorkerException(I_DispatchManager dispatchManager, Throwable ex) {
   }
   
   
}
