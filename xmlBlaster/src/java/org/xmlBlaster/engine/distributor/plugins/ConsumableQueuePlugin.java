/*------------------------------------------------------------------------------
Name:      ConsumableQueuePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.distributor.plugins;

import java.util.ArrayList;
import java.util.Set;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.TopicHandler;
import org.xmlBlaster.engine.distributor.I_MsgDistributor;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.QueryQosData;

/**
 * ConsumableQueuePlugin
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class ConsumableQueuePlugin implements I_MsgDistributor, I_ConnectionStatusListener {

   public final static int UNINITIALIZED = 0;
   public final static int WORKING = 1;
   public final static int SLEEPING = 2;
   public final static int DEAD = 3;
   private final static String ME = "ConsumableQueuePlugin";
   private int status;
   private Global global;
   private LogChannel log;
   private PluginInfo pluginInfo;
   private TopicHandler topicHandler;
   
   public ConsumableQueuePlugin() {
      this.status = UNINITIALIZED;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.engine.distributor.I_MsgDistributor#syncDistribution(org.xmlBlaster.engine.TopicHandler, org.xmlBlaster.authentication.SessionInfo, org.xmlBlaster.engine.MsgUnitWrapper)
    */
   synchronized public void syncDistribution(MsgUnitWrapper msgUnitWrapper) { 
      try {
         if (this.log.CALL) this.log.call(ME, "distribute '" + msgUnitWrapper.getUniqueId() + "' '" + msgUnitWrapper.getKeyOid() + "'");
         // Take a copy of the map entries (a current snapshot)
         // If we would iterate over the map directly we can risk a java.util.ConcurrentModificationException
         // when one of the callback fails and the entry is removed by the callback worker thread
         SubscriptionInfo[] subInfoArr = this.topicHandler.getSubscriptionInfoArr();
         Set removeSet = null;
         int count = 0;
         for (int ii=0; ii<subInfoArr.length; ii++) {
            SubscriptionInfo sub = subInfoArr[ii];
            if (sub == null) continue;
                     QueryQosData qos = sub.getQueryQosData();
            if (qos == null) continue;
            if (!qos.getWantLocal() && 
                 sub.getSessionInfo().getSessionName().equalsAbsolute(msgUnitWrapper.getMsgQosData().getSender()))
               continue;
            if (!qos.getWantNotify() && msgUnitWrapper.getMsgQosData().isErased()) {
               continue;
            }

            // TODO synchronization
            if (sub.getSessionInfo() == null) continue;
            if (!sub.getSessionInfo().hasCallback()) continue;
            if (sub.getSessionInfo().getDispatchManager() == null) continue;
            if (!sub.getSessionInfo().getDispatchManager().getDispatchConnectionsHandler().isAlive()) continue;
         
            // modify the qos to see if it has passed this plugin ...
            msgUnitWrapper.getMsgQosData().setClientProperty("MsgDistributorPlugin", this.getType() + "," + this.getVersion());
            if (this.topicHandler.invokeCallback(null, sub, msgUnitWrapper) < 1) continue;
            else {
               count++;
               break;
            } 
         }
         if (count == 1) this.topicHandler.removeFromHistory(msgUnitWrapper);
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         this.log.error(ME, "syncDistribution " + ex.getMessage());
      }
   }

   synchronized public void init(Global global, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.global = global;
      this.log = this.global.getLog("distributor");
      if (this.log.CALL) this.log.call(ME, "init");
      this.pluginInfo = pluginInfo;
      this.topicHandler = (TopicHandler)this.pluginInfo.getUserData();
      this.status = SLEEPING;
   }

   public String getType() {
      return this.pluginInfo.getType();
   }

   public String getVersion() {
      return this.pluginInfo.getVersion();
   }

   synchronized public void shutdown() throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "shutdown");
      SubscriptionInfo[] subs = this.topicHandler.getSubscriptionInfoArr();
      for (int i=0; i < subs.length; i++) onRemoveSubscriber(subs[i]);
      this.status = DEAD;
   }

   
   private final DispatchManager getDispatchManager(SubscriptionInfo subscriptionInfo) {
      if (subscriptionInfo == null) {
         this.log.error(ME, "getDispatchManager the subscriptionInfo object is null");
         Thread.dumpStack();
         return null;
      }
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      if (sessionInfo == null) {
         this.log.error(ME, "getDispatchManager the sessionInfo object is null");
         Thread.dumpStack();
         return null;
      }
      DispatchManager dispatchManager = sessionInfo.getDispatchManager();
      if (dispatchManager == null) {
         this.log.error(ME, "getDispatchManager the dispatcherManager object is null");
         Thread.dumpStack();
         return null;
      }
      return dispatchManager;
   }
   
   /**
    * Invoked when a subscriber is added to the TopicHandler
    * @param subscriptionInfo
    */
   synchronized public void onAddSubscriber(SubscriptionInfo subscriptionInfo) {
      if (this.log.CALL) this.log.call(ME, "onAddSubscriber");
      DispatchManager dispatchManager = getDispatchManager(subscriptionInfo);
      if (dispatchManager != null) dispatchManager.addConnectionStatusListener(this);
   }
   
   /**
    * Invoked when a subscriber is removed from the TopicHandler
    * @param subscriptionInfo
    */
   synchronized public void onRemoveSubscriber(SubscriptionInfo subscriptionInfo) {
      if (this.log.CALL) this.log.call(ME, "onRemoveSubscriber");
      DispatchManager dispatchManager = getDispatchManager(subscriptionInfo);
      if (dispatchManager != null) dispatchManager.removeConnectionStateListener(this);
   }

   synchronized public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
      // TODO synchronization
      if (this.log.CALL) this.log.call(ME, "toAlive");
      try {
         ArrayList lst = this.topicHandler.peekFromHistory(-1, -1L);
         for (int i=0; i < lst.size(); i++) {
            MsgUnitWrapper msgUnitWrapper = ((MsgQueueHistoryEntry)lst.get(i)).getMsgUnitWrapper();
            this.syncDistribution(msgUnitWrapper);
         }  
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         this.log.error(ME, "toAlive: " + ex.getMessage());
      }
   }

   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }

   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
   }
}
