/*------------------------------------------------------------------------------
Name:      ConsumableQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.distributor.plugins;

import java.util.ArrayList;
import java.util.List;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.SubscriptionEvent;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.engine.distributor.I_MsgDistributor;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.DispatchWorker;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * ConsumableQueuePlugin
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class ConsumableQueuePlugin implements I_MsgDistributor, I_ConnectionStatusListener {

   private final static String ME = "ConsumableQueuePlugin";
   // int status;
   boolean isReady;
   boolean isRunning;
   private Global global;
   private static Logger log = Logger.getLogger(ConsumableQueuePlugin.class.getName());
   private PluginInfo pluginInfo;
   private TopicHandler topicHandler;

   /**
    * The default constructor. Currently does nothing.
    */   
   public ConsumableQueuePlugin() {
      this.isReady = false;
   }

   /**
    * Invoked on status changes when it shall start to distribute 
    * entries. This can either happen on publish, on subscribe or when 
    * a dispatcher becomes alive again. This method is synchronized to avoid 
    * more threads running concurrently (see processHistoryQueue).
    */
   private synchronized void toRunning() {
      if (log.isLoggable(Level.FINER)) this.log.finer("toRunning, isRunning='" + this.isRunning + "' isReady='" + this.isReady + "'");
      if (this.isRunning || !this.isReady) return;
      this.isRunning = true;
      try {
         // the global owns a thread pool (Doug Lea's executor pattern)
         this.global.getDispatchWorkerPool().execute(new ConsumableQueueWorker(this.log, this));
      }
      catch (InterruptedException ex) {
         log.severe("toRunning: exception " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   /**
    * @see org.xmlBlaster.engine.distributor.I_MsgDistributor#distribute(org.xmlBlaster.engine.TopicHandler, org.xmlBlaster.authentication.SessionInfo, org.xmlBlaster.engine.MsgUnitWrapper)
    * Invoked by the TopicHandler on publish or subscribe. Starts the distributor thread and 
    * returnes immeditately. From here distribution is handled by another thread.
    **/
   public void distribute(MsgUnitWrapper msgUnitWrapper) {
      if (log.isLoggable(Level.FINER)) this.log.finer("distribute");
      toRunning(); 
   }
   
   /**
    * Initializes the plugin
    */
   synchronized public void init(Global global, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.global = global;

      if (log.isLoggable(Level.FINER)) this.log.finer("init");
      this.pluginInfo = pluginInfo;
      this.topicHandler = (TopicHandler)this.pluginInfo.getUserData();
      this.isReady = true;
      toRunning();
   }

   public String getType() {
      return this.pluginInfo.getType();
   }

   public String getVersion() {
      return this.pluginInfo.getVersion();
   }

   /**
    * It removes all subscriptions done on this topic
    */
   synchronized public void shutdown() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) this.log.finer("shutdown");
      SubscriptionInfo[] subs = this.topicHandler.getSubscriptionInfoArr();
      for (int i=0; i < subs.length; i++) this.subscriptionRemove(new SubscriptionEvent(subs[i]));
      this.isReady = false;
   }
   
   private final DispatchManager getDispatchManager(SubscriptionInfo subscriptionInfo) {
      if (subscriptionInfo == null) {
         log.severe("getDispatchManager the subscriptionInfo object is null");
         Thread.dumpStack();
         return null;
      }
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      if (sessionInfo == null) {
         log.severe("getDispatchManager the sessionInfo object is null");
         Thread.dumpStack();
         return null;
      }
      DispatchManager dispatchManager = sessionInfo.getDispatchManager();
      if (dispatchManager == null) {
         log.severe("getDispatchManager the dispatcherManager object is null");
         Thread.dumpStack();
         return null;
      }
      return dispatchManager;
   }
   
   /**
    * Invoked when a subscriber is added to the TopicHandler
    * @param subscriptionInfo
    */
   public synchronized void subscriptionAdd(SubscriptionEvent e) 
      throws XmlBlasterException {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (log.isLoggable(Level.FINER)) this.log.finer("onAddSubscriber");
      DispatchManager dispatchManager = getDispatchManager(subscriptionInfo);
      if (dispatchManager != null) dispatchManager.addConnectionStatusListener(this);
      this.isReady = true;
      toRunning();
   }
   
   /**
    * Invoked when a subscriber is removed from the TopicHandler
    * @param subscriptionInfo
    */
   synchronized public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (log.isLoggable(Level.FINER)) this.log.finer("onRemoveSubscriber");
      DispatchManager dispatchManager = getDispatchManager(subscriptionInfo);
      if (dispatchManager != null) dispatchManager.removeConnectionStatusListener(this);
   }

   /**
    * Event arriving from one DispatchManager telling this plugin it can 
    * start distribute again. 
    */
   public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      if (log.isLoggable(Level.FINER)) this.log.finer("toAlive");
      this.isReady = true;
      toRunning();
   }

   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }

   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
   }

   /**
    * Takes entries from the history queue and distributes it to the dispatcher
    * framework until there are entries available or until the dispatcher framework
    * is alive.
    */
   void processHistoryQueue() {
      if (log.isLoggable(Level.FINER)) this.log.finer("processQueue");
      try {
         ArrayList lst = null;
         while (true) {
            synchronized(this) {
               I_Queue historyQueue = this.topicHandler.getHistoryQueue();
               if (historyQueue == null) {
                  this.isRunning = false;
                  break;
               }
               lst = historyQueue.peek(-1, -1L);
               if (log.isLoggable(Level.FINE)) this.log.fine("processQueue: processing '" + lst.size() + "' entries from queue");
               if (lst == null || lst.size() < 1) {
                  this.isRunning = false;
                  break;
               }
            }
            

            // SubscriptionInfo[] subInfoArr = this.topicHandler.getSubscriptionInfoArr();
            SubscriptionInfo[] subInfoArr = this.topicHandler.getSubscriptionInfoArr();
            ArrayList subInfoList = new ArrayList();
            for (int i=0; i < subInfoArr.length; i++) subInfoList.add(subInfoArr[i]);
            
            for (int i=0; i < lst.size(); i++) {
               if (!this.isReady) return;
               MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)lst.get(i);
               MsgUnitWrapper msgUnitWrapper = (entry).getMsgUnitWrapper();
               if (msgUnitWrapper != null) { 
                  if (!this.distributeOneEntry(msgUnitWrapper, entry, subInfoList)) {
                     this.isReady = false;
                     this.isRunning = false;
                     return;
                  } 
               }
            }
              
         }
         this.isReady = true;
      }
      catch (Throwable ex) {
         this.isReady = false;
         this.isRunning = false;
         ex.printStackTrace();
         log.severe("processQueue: " + ex.getMessage());
      }
   }

   /**
    * Distributes one single entry taken from the history queue. This method 
    * is strict, it does not throw any exceptions. If one exception occurs 
    * inside this method, the distribution is interrupted, a dead letter is 
    * generated and the entry is removed from the history queue.
    * 
    * @param subInfoList contains the SubscriptionInfo objects to scan. Once the 
    *        message is processed by one of the dispatchers, the associated 
    *        SessionInfo is put at the end of the list to allow some simple
    *        load balancing mechanism.
    * 
    * @return true if the entry has been removed from the history queue. This happens
    * if the entry could be sent successfully, or if distribution was given up due to
    * an exception. It returns false if none of the subscribers were able to receive 
    * the message (to tell the invoker not to continue with distribution until
    * the next event.
    */
   private boolean distributeOneEntry(MsgUnitWrapper msgUnitWrapper, MsgQueueHistoryEntry entry, List subInfoList) { 
      I_Queue srcQueue = this.topicHandler.getHistoryQueue();
      if (srcQueue == null)
         return false;
      try {

         if (msgUnitWrapper == null) {
            log.severe("distributeOneEntry() MsgUnitWrapper is null");
            Thread.dumpStack();
            givingUpDistribution(null, msgUnitWrapper, entry, null);
            return true; // let the loop continue: other entries could be OK
         }

         if (log.isLoggable(Level.FINER)) this.log.finer("distributeOneEntry '" + msgUnitWrapper.getUniqueId() + "' '" + msgUnitWrapper.getKeyOid() + "'");
         // Take a copy of the map entries (a current snapshot)
         // If we would iterate over the map directly we can risk a java.util.ConcurrentModificationException
         // when one of the callback fails and the entry is removed by the callback worker thread

         SubscriptionInfo[] subInfoArr = (SubscriptionInfo[])subInfoList.toArray(new SubscriptionInfo[subInfoList.size()]);
         for (int ii=0; ii<subInfoArr.length; ii++) {
            SubscriptionInfo sub = subInfoArr[ii];
            if (this.topicHandler.isDirtyRead(sub, msgUnitWrapper)) {
               log.severe("ConsumableQueuePlugin used together with 'dirtyRead' is not supported");
               srcQueue.removeRandom(entry);
               return true; // even if it has not been sent
            } 
         }   

         for (int ii=0; ii<subInfoArr.length; ii++) {
            SubscriptionInfo sub = subInfoArr[ii];
            
            if (!this.topicHandler.subscriberMayReceiveIt(sub, msgUnitWrapper)) continue;
            //Has no effect:
            //if (!this.topicHandler.checkIfAllowedToSend(null, sub)) continue;

            // this is specific for this plugin
            if (sub.getSessionInfo().getDispatchManager() == null) continue;
            if (!sub.getSessionInfo().getDispatchManager().getDispatchConnectionsHandler().isAlive()) continue;

            try {

               try {
                  // the 'false' here is to tell the filter not to send a dead letter in case of an ex
                  if (!this.topicHandler.checkFilter(null, sub, msgUnitWrapper, false)) continue;
               }
               catch (XmlBlasterException ex) {
                  // continue;
                  givingUpDistribution(sub, msgUnitWrapper, entry, ex);
                  return true; // because the entry has been removed from the history queue
               }

               // put the current dispatcher at the end of the list for next invocation (round robin)
               subInfoList.remove(subInfoArr[ii]);
               subInfoList.add(subInfoArr[ii]);
               
               MsgQueueUpdateEntry updateEntry = this.topicHandler.createEntryFromWrapper(msgUnitWrapper,sub);

               UpdateReturnQosServer retQos = doDistribute(sub, updateEntry);

               if (log.isLoggable(Level.FINE)) {
                  if (retQos == null) log.fine("distributeOneEntry: the return object was null: callback has not sent the message (dirty reads ?)");
               }
               if (retQos == null || retQos.getException() == null) {
                  srcQueue.removeRandom(entry); // success
                  if (log.isLoggable(Level.FINE)) this.log.fine("distributeOneEntry: successfully removed entry from queue");
                  return true;
               }
               else {
                  log.severe("distributeOneEntry an exception occured: " + retQos.getException().getMessage());
                  Throwable ex = retQos.getException();
                  // continue if it is a communication exception stop otherwise
                  if (ex instanceof XmlBlasterException && ((XmlBlasterException)ex).isCommunication()) continue;
                  // we pass null for the exception since we don't want to shut down the dispatcher
                  givingUpDistribution(sub, msgUnitWrapper, entry, null);
                  return true; //since removed
               }
            }
            catch (Throwable e) {
               e.printStackTrace();
               givingUpDistribution(sub, msgUnitWrapper, entry, e);
               return true;
            }
         }
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         log.severe("distributeOneEntry " + ex.getMessage());
         givingUpDistribution(null, msgUnitWrapper, entry, ex);         
         // TODO or should we return true here to allow to continue ? 
         // I think it is a serious ex: probably does not make sense to cont.
      }
      return false;
   }

   private void givingUpDistribution(SubscriptionInfo sub, MsgUnitWrapper msgUnitWrapper, MsgQueueEntry entry, Throwable e) {
      try {
         String id = "";
         if (sub != null) id = sub.getSessionInfo().getId();
         String exTxt = "";
         if (e != null) exTxt = e.toString();
         SessionName publisherName = msgUnitWrapper.getMsgQosData().getSender();
         if (log.isLoggable(Level.FINE)) log.fine("Sending of message from " + publisherName + " to " +
                            id + " failed: " + exTxt);
               
         if (sub != null && e != null) 
            sub.getSessionInfo().getDispatchManager().internalError(e); // calls MsgErrorHandler
         else {
            this.topicHandler.getRequestBroker().deadMessage(new MsgQueueEntry[] { entry }, null, ME + ".givingUpDistribution: " + exTxt);
         }
         // remove the entry from the history queue now that a dead letter has been sent.
         I_Queue historyQueue = this.topicHandler.getHistoryQueue();
         if (historyQueue != null)
            historyQueue.removeRandom(entry);
      }
      catch (XmlBlasterException ex) {
         log.severe("givingUpDistribution: " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   /**
    * Enforced by the I_DistributionInterceptor interface. It sends sychronously to
    * the DispatchWorker this entry. 
    */    
   private UpdateReturnQosServer doDistribute(SubscriptionInfo sub, MsgQueueUpdateEntry entry) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) this.log.finer("doDistribute");
      // this is a sync call (all in the same thread)
      entry.setWantReturnObject(true);
      DispatchWorker worker = new DispatchWorker(this.global, sub.getSessionInfo().getDispatchManager());
      ArrayList list = new ArrayList();
      list.add(entry);
      worker.run(list);      
      return (UpdateReturnQosServer)entry.getReturnObj();
   }

   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#getPriority()
    */
   public Integer getPriority() {
      return PRIO_05;
   }
}
   
