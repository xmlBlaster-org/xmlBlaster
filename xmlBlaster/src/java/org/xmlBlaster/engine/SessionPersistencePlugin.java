/*------------------------------------------------------------------------------
Name:      SessionPersitencePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.SubscribeStoreProperty;
import org.xmlBlaster.util.qos.storage.SessionStoreProperty;
import org.xmlBlaster.util.queue.StorageId;

/**
 * SessionPersitencePlugin
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SessionPersistencePlugin
   implements I_Plugin, I_ClientListener, I_SubscriptionListener {

   private final static String ME = "SessionPersistencePlugin";
   private PluginInfo info;
   private Global global;
   private LogChannel log;
   private boolean isOK;
   private I_Map sessionStore, subscribeStore;
   private StorageId sessionStorageId;
   private StorageId subscribeStorageId;
   

   private void recoverSessions() throws XmlBlasterException {
      I_MapEntry[] entries = this.sessionStore.getAll();
      for (int i=0; i < entries.length; i++) {
         if (entries[i] instanceof MsgQueueConnectEntry) {
            // do connect
            // this.global.getAuthenticate().
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".recoverSessions: the entry in the queue should be either of type 'MsgQueueSubscribeEntry' or 'MsgQueueConnectEntry' but is of type'" + entries[i].getClass().getName() + "'");
         }
      }
   }
   
   /**
    * Initializes the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo)
      throws XmlBlasterException {
      this.info = pluginInfo;
      this.global = (org.xmlBlaster.engine.Global)glob;
      this.isOK = true;
      this.log = this.global.getLog("subscription");
      if (this.log.CALL) this.log.call(ME, "init");
      // init the storages
      
      QueuePropertyBase sessionProp = new SessionStoreProperty(this.global, this.global.getStrippedId());   
      if (sessionProp.getMaxEntries() > 0L) {
         String type = sessionProp.getType();
         String version = sessionProp.getVersion();
         this.sessionStorageId = new StorageId(Constants.RELATING_SESSION, this.global.getStrippedId() +"/" + this.info.getId());
         this.sessionStore = this.global.getStoragePluginManager().getPlugin(type, version, this.sessionStorageId, sessionProp);
      }
      else {
         if (log.TRACE) log.trace(ME, Constants.RELATING_SUBSCRIBE + " persitence for subscribe is switched of with maxEntries=0");
      }

      QueuePropertyBase subscribeProp = new SubscribeStoreProperty(this.global, this.global.getStrippedId());   
      if (subscribeProp.getMaxEntries() > 0L) {
         String type = subscribeProp.getType();
         String version = subscribeProp.getVersion();
         this.subscribeStorageId = new StorageId(Constants.RELATING_SUBSCRIBE, this.global.getStrippedId() +"/" + this.info.getId());
         this.subscribeStore = this.global.getStoragePluginManager().getPlugin(type, version, this.subscribeStorageId, subscribeProp);
      }
      else {
         if (log.TRACE) log.trace(ME, Constants.RELATING_SUBSCRIBE + " persitence for subscribe is switched of with maxEntries=0");
      }
   }

   /**
    * returns the plugin type
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.info != null) return this.info.getType();
      return null;
   }

   /**
    * returns the plugin version
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.info != null) return this.info.getVersion();
      return null;
   }

   /**
    * Shutsdown the plugin
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "shutdown");
      this.sessionStore.shutdown();
      this.subscribeStore.shutdown();
      this.isOK = false;
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "sessionAdded '" + e.getSessionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionAdded: invoked when plugin already shut down");
      
      SessionInfo sessionInfo = e.getSessionInfo();
      // this is a fake timestamp since MsgQueueEntry needs a timestamp
      Timestamp uniqueId = new Timestamp(sessionInfo.getInstanceId());
      
      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();
      MsgQueueConnectEntry entry = new MsgQueueConnectEntry(this.global, PriorityEnum.NORM_PRIORITY, this.sessionStorageId, uniqueId, connectQosData.size(), connectQosData);
      this.sessionStore.put(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "sessionRemoved '" + e.getSessionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionRemoved: invoked when plugin already shut down");
      
      SessionInfo sessionInfo = e.getSessionInfo();
      // TODO add a method I_Queue.removeRandom(long uniqueId)
      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();
      Timestamp uniqueId = new Timestamp(sessionInfo.getInstanceId());
      MsgQueueConnectEntry entry = new MsgQueueConnectEntry(this.global, PriorityEnum.NORM_PRIORITY, this.sessionStorageId, uniqueId, connectQosData.size(), connectQosData);
      this.sessionStore.remove(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionAdd(SubscriptionEvent e)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "subscriptionAdd '" + e.getSubscriptionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".subscriptionAdded: invoked when plugin already shut down");

      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      // TODO add a method I_Queue.removeRandom(long uniqueId)
      QueryKeyData subscribeKeyData = (QueryKeyData)subscriptionInfo.getKeyData();
      QueryQosData subscribeQosData = subscriptionInfo.getQueryQosData();
      
      MsgQueueSubscribeEntry entry = new MsgQueueSubscribeEntry(this.global, this.subscribeStorageId, subscribeKeyData, subscribeQosData);
      this.sessionStore.remove(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent e)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "subscriptionRemove '" + e.getSubscriptionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".subscriptionRemove: invoked when plugin already shut down");

      // TODO add a method I_Queue.removeRandom(long uniqueId)
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      QueryQosData qosData = subscriptionInfo.getQueryQosData();
      QueryKeyData keyData = (QueryKeyData)subscriptionInfo.getKeyData();
      MsgQueueSubscribeEntry entry = new MsgQueueSubscribeEntry(this.global, this.subscribeStorageId, keyData, qosData);
      this.subscribeStore.remove(entry);
   }

   /**
    * does nothing
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException {
   }
   /**
    * does nothing
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException {
   }
}
