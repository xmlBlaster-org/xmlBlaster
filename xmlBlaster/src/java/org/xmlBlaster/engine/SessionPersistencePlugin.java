/*------------------------------------------------------------------------------
Name:      SessionPersitencePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.queuemsg.SessionEntry;
import org.xmlBlaster.engine.queuemsg.SubscribeEntry;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.key.QueryKeySaxFactory;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.SubscribeStoreProperty;
import org.xmlBlaster.util.qos.storage.SessionStoreProperty;
import org.xmlBlaster.util.queue.StorageId;

/**
 * SessionPersitencePlugin provides the persitent storage for both sessions
 * and subscriptions. 
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SessionPersistencePlugin implements I_SessionPersistencePlugin {

   private final static String ME = "SessionPersistencePlugin";
   private PluginInfo info;
   private Global global;
   private LogChannel log;
   
   /** flag indicating the status: true means initialized and not yet shut down) */
   private boolean isOK;
   
   private I_Map sessionStore;
   private I_Map subscribeStore;
   private StorageId sessionStorageId;
   private StorageId subscribeStorageId;
   private ConnectQosSaxFactory connectQosFactory;
   private QueryQosSaxFactory queryQosFactory;
   private QueryKeySaxFactory queryKeyFactory;
   private Object sync = new Object();
   
   private void recoverSessions() throws XmlBlasterException {
      I_MapEntry[] entries = this.sessionStore.getAll();
      boolean isInternal = true;
      for (int i=0; i < entries.length; i++) {
         if (entries[i] instanceof SessionEntry) {
            // do connect
            SessionEntry entry = (SessionEntry)entries[i];
            ConnectQosData data = this.connectQosFactory.readObject(entry.getQos());
            ConnectQosServer qos = new ConnectQosServer(this.global, data, isInternal);
            this.global.getAuthenticate().connect(qos);
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".recoverSessions: the entry in the queue should be either of type 'MsgQueueSubscribeEntry' or 'MsgQueueConnectEntry' but is of type'" + entries[i].getClass().getName() + "'");
         }
      }
   }
   
   private void recoverSubscriptions() throws XmlBlasterException {
      I_MapEntry[] entries = this.subscribeStore.getAll();
      boolean isInternal = true;
      for (int i=0; i < entries.length; i++) {
         if (entries[i] instanceof SubscribeEntry) {
            // do connect
            SubscribeEntry entry = (SubscribeEntry)entries[i];
            String sessionId = entry.getSessionId();
            this.log.trace(ME, "recoverSubscriptions: for entry '" + entry.getLogId());
            this.global.getAuthenticate().getXmlBlaster().subscribe(sessionId, entry.getKey(), entry.getQos());
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
      synchronized (this.sync) {   
         if (this.isOK) return;
         this.info = pluginInfo;
         this.global = (org.xmlBlaster.engine.Global)glob;
         this.log = this.global.getLog("subscription");
         if (this.log.CALL) this.log.call(ME, "init");

         this.connectQosFactory = new ConnectQosSaxFactory(this.global);
         this.queryQosFactory = new QueryQosSaxFactory(this.global);
         this.queryKeyFactory = new QueryKeySaxFactory(this.global);
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
         else if (log.TRACE) log.trace(ME, Constants.RELATING_SUBSCRIBE + " persitence for subscribe is switched of with maxEntries=0");
         recoverSessions();
         recoverSubscriptions();
         // register after having retreived the data
         // is this a security risk from a plugin ? 
         this.global.getRequestBroker().getAuthenticate().addClientListener(this);
         this.global.getRequestBroker().addSubscriptionListener(this);

         this.isOK = true;
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
      synchronized (this.sync) {
         this.isOK = false;
         this.global.getRequestBroker().getAuthenticate().addClientListener(this);
         this.global.getRequestBroker().addSubscriptionListener(this);
         this.sessionStore.shutdown();
         this.subscribeStore.shutdown();
      }
   }

   private void addSession(SessionInfo sessionInfo) throws XmlBlasterException {
      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();
      if (!connectQosData.getPersistentProp().getValue()) return;

      long uniqueId = sessionInfo.getInstanceId();
      if (this.log.TRACE) this.log.trace(ME, "addSession (persitent) for uniqueId: '" + uniqueId + "'");
      SessionEntry entry = new SessionEntry(connectQosData.toXml(), uniqueId);
      this.sessionStore.put(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "sessionAdded '" + e.getSessionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionAdded: invoked when plugin already shut down");

      SessionInfo sessionInfo = e.getSessionInfo();
      addSession(sessionInfo);      
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "sessionRemoved '" + e.getSessionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionRemoved: invoked when plugin already shut down");
      
      SessionInfo sessionInfo = e.getSessionInfo();
      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();
      if (!connectQosData.getPersistentProp().getValue()) return;

      // TODO add a method I_Queue.removeRandom(long uniqueId)
      long uniqueId = sessionInfo.getInstanceId();
      if (this.log.TRACE) this.log.trace(ME, "sessionRemoved (persitent) for uniqueId: '" + uniqueId + "'");
      SessionEntry entry = new SessionEntry(connectQosData.toXml(), uniqueId);
      this.sessionStore.remove(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "subscriptionAdd '" + e.getSubscriptionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".subscriptionAdded: invoked when plugin already shut down");

      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      // TODO add a method I_Queue.removeRandom(long uniqueId)
      QueryQosData subscribeQosData = subscriptionInfo.getQueryQosData();
      if (!subscribeQosData.getPersistentProp().getValue()) return;

      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo(); 
      if (!sessionInfo.getConnectQos().getData().getPersistentProp().getValue()) {
         sessionInfo.getConnectQos().getData().setPersistent(true);
         this.addSession(sessionInfo);         
      }

      QueryKeyData subscribeKeyData = (QueryKeyData)subscriptionInfo.getKeyData();
      SubscribeEntry entry = new SubscribeEntry(subscribeKeyData.toXml(), subscribeQosData.toXml(), sessionInfo.getSecretSessionId());
      this.sessionStore.put(entry);
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
      if (!qosData.getPersistentProp().getValue()) return;

      QueryKeyData keyData = (QueryKeyData)subscriptionInfo.getKeyData();
      SubscribeEntry entry = new SubscribeEntry(keyData.toXml(), qosData.toXml(), subscriptionInfo.getSessionInfo().getSecretSessionId(), subscriptionInfo.getPersistenceId(), 0L);
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
