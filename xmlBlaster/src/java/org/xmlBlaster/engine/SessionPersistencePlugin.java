/*------------------------------------------------------------------------------
Name:      SessionPersistencePlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.engine;

import java.util.HashMap;

import org.jutils.log.LogChannel;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.ConnectReturnQosServer;
import org.xmlBlaster.engine.queuemsg.SessionEntry;
import org.xmlBlaster.engine.queuemsg.SubscribeEntry;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.QueryQosSaxFactory;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.SubscribeStoreProperty;
import org.xmlBlaster.util.qos.storage.SessionStoreProperty;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Entry;

/**
 * SessionPersistencePlugin provides the persistent storage for both sessions
 * and subscriptions. 
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class SessionPersistencePlugin implements I_SessionPersistencePlugin {

   private final static String ME = "SessionPersistencePlugin";
   /** when recovering all subscriptions must be 'noInitialUpdate' because otherwise
    * we would get messages which we already got in the past
    */
   private final static String ORIGINAL_INITIAL_UPDATES = "__originalInitialUpdates";
   
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
   private AddressServer addressServer;
   private Object sync = new Object();
   
   private int duplicateCounter;
   private int errorCounter;
   
   /**
    * 
    * @return hash map containing the secret sessionId of the entries recovered 
    *         as values and as keys the corresponding absolute name for the session (String)
    * @throws XmlBlasterException
    */
   private HashMap recoverSessions() throws XmlBlasterException {
      I_MapEntry[] entries = this.sessionStore.getAll(null);
      HashMap sessionIds = new HashMap();
      for (int i=0; i < entries.length; i++) {
         if (entries[i] instanceof SessionEntry) {
            // do connect
            SessionEntry entry = (SessionEntry)entries[i];
            ConnectQosData data = this.connectQosFactory.readObject(entry.getQos());
            
            this.addressServer = new AddressServer(this.global, "NATIVE", this.global.getId(), (java.util.Properties)null);

            ConnectQosServer qos = new ConnectQosServer(this.global, data);
            qos.isFromPersistenceRecovery(true);
            qos.setPersistenceUniqueId(entry.getUniqueId());
            qos.setAddressServer(this.addressServer);

            SessionName sessionName = data.getSessionName();
            String sessionId = data.getSessionQos().getSecretSessionId();
            sessionIds.put(sessionName.getAbsoluteName(), sessionId); 
            if (this.log.TRACE) this.log.trace(ME, "recoverSessions: store in map session='" + sessionName.getAbsoluteName() + "' has secret sessionId='" + sessionId + "' and persistenceUniqueId=" + entry.getUniqueId());
            // if (this.log.TRACE) this.log.trace(ME, "recoverSessions: session: '" + data.getSessionName() + "' secretSessionId='" + qos.getSessionQos().getSecretSessionId() + "' qos='" + qos.toXml() + "'");
            ConnectReturnQosServer ret = this.global.getAuthenticate().connect(this.addressServer, qos);
            if (this.log.DUMP) this.log.dump(ME, "recoverSessions: return of connect: returnConnectQos='" + ret.toXml() + "'");
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".recoverSessions: the entry in the storage should be of type 'SessionEntry' but is of type'" + entries[i].getClass().getName() + "'");
         }
      }
      return sessionIds;
   }


   /**
    * When recovering due to a run level change (without shutting down the
    * application) this will not work. 
    * 
    * @throws XmlBlasterException
    */   
   private void recoverSubscriptions(final HashMap sessionIds) throws XmlBlasterException {
      {
         boolean checkForDuplicateSubscriptions = this.global.getProperty().get("xmlBlaster/checkForDuplicateSubscriptions", false);
         if (checkForDuplicateSubscriptions) {
            duplicateCounter = 0;
            errorCounter = 0;
            final java.util.Map duplicates = new java.util.TreeMap();
            /*I_MapEntry[] results = */this.subscribeStore.getAll(new I_EntryFilter() {
               public I_Entry intercept(I_Entry entry, I_Storage storage) {
                  if (storage.isTransient()) return null;
                  try {
                     SubscribeEntry subscribeEntry = (SubscribeEntry)entry;
                     //QueryKeyData keyData = queryKeyFactory.readObject(subscribeEntry.getKey());
                     QueryQosData qosData = queryQosFactory.readObject(subscribeEntry.getQos());
                     //String key = keyData.getOid() + qosData.getSender().getAbsoluteName();

                     SessionName sessionName = new SessionName(global, subscribeEntry.getSessionName());
                     Object found = sessionIds.get(sessionName.getAbsoluteName());
                     if (found == null) {
                        if (errorCounter == 0) {
                           log.warn(ME, "Ignoring invalid entry '" + sessionName.getAbsoluteName() + "' as user is not known");
                        }
                        errorCounter++;
                        return null;
                     }

                     String key = qosData.getSubscriptionId();
                     if (log.TRACE) log.trace(ME, "Cleanup of duplicate subscriptions, key=" + key);
                     if (duplicates.containsKey(key)) {
                        if (duplicateCounter == 0)
                           log.warn(ME, "Cleanup of duplicate subscriptions, this may take a while, please wait ...");
                        duplicateCounter++;
                        //log.warn(ME, "Removing duplicate subscription '" + key + "' oid=" + keyData.getOid());
                        //subscribeStore.remove(subscribeEntry);
                     }
                     else {
                        duplicates.put(key, subscribeEntry);
                     }
                  }
                  catch (XmlBlasterException e) {
                     log.error(ME, "Ignoring unexpected problem in checkForDuplicateSubscriptions :" + e.toString());
                  }
                  return null;
               }
            });
            if (duplicateCounter > 0) {
               this.subscribeStore.clear();
               if (this.subscribeStore.getNumOfEntries() > 0)
                     log.error(ME, "Internal prpblem with checkForDuplicateSubscriptions");
               java.util.Iterator it = duplicates.keySet().iterator();
               while (it.hasNext()) {
                  this.subscribeStore.put((I_MapEntry)duplicates.get(it.next()));
               }
               log.warn(ME, "Removed " + (duplicateCounter-duplicates.size()) + " identical subscriptions, keeping " + duplicates.size() + ". Ignored " + errorCounter + " invalid subscriptions as no session was found");
            }
         }
      }
      
      I_MapEntry[] entries = this.subscribeStore.getAll(null);
      
      for (int i=0; i < entries.length; i++) {
         if (entries[i] instanceof SubscribeEntry) {
            // do connect
            SubscribeEntry entry = (SubscribeEntry)entries[i];
            String qos = entry.getQos();
            QueryQosData qosData = this.queryQosFactory.readObject(qos);

            ClientProperty clientProperty = qosData.getClientProperty(Constants.PERSISTENCE_ID);
            if (clientProperty == null) {
               log.error(ME, "SubscribeQos with missing " + Constants.PERSISTENCE_ID + ": " + qosData.toXml());
               long uniqueId = new Timestamp().getTimestamp();
               qosData.getClientProperties().put(Constants.PERSISTENCE_ID, new ClientProperty(Constants.PERSISTENCE_ID, "long", null, "" + uniqueId));
            }
            
            boolean initialUpdates = qosData.getInitialUpdateProp().getValue();
            if (initialUpdates) {
               qosData.getClientProperties().put(ORIGINAL_INITIAL_UPDATES, new ClientProperty(ORIGINAL_INITIAL_UPDATES, "boolean", null, "true"));
            }
            SessionName sessionName = new SessionName(this.global, entry.getSessionName());
            String sessionId = (String)sessionIds.get(sessionName.getAbsoluteName());
            if (sessionId == null) {
               log.error(ME, "The secret sessionId was not found for session='" + sessionName.getAbsoluteName() + "', removing persistent subscription " + entry.getLogId());
               this.subscribeStore.remove(entry);
               continue;
               //throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_NULLPOINTER, ME + ".recoverSubscriptions", "The secret sessionId was not found for session='" + sessionName.getAbsoluteName() + "'");
            }
            // TODO remove the setting of client properties and invoke directly requestBroker.subscribe with subscribeQosServer.inhibitInitialUpdates(true);
            // also get the sessionInfo object from authenticate => eliminate sessionIds      
            this.global.getAuthenticate().getXmlBlaster().subscribe(this.addressServer, sessionId, entry.getKey(), qosData.toXml());
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".recoverSubscriptions: the entry in the storage should be of type 'SubscribeEntry'but is of type'" + entries[i].getClass().getName() + "'");
         }
      }
   }

   private void removeAssociatedSubscriptions(SessionInfo sessionInfo) 
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "removeAssociatedSubscriptions for session '" + sessionInfo.getId() + "'");   
      XmlBlasterException e = null;
      SubscriptionInfo[] subs = this.global.getRequestBroker().getClientSubscriptions().getSubscriptions(sessionInfo);
      for (int i=0; i < subs.length; i++) {
         try {
            if (this.log.CALL) this.log.call(ME, "removeAssociatedSubscriptions for session '" + sessionInfo.getId() + "' subscription '" + subs[i].getId() + "'");   
            this.subscriptionRemove(new SubscriptionEvent(subs[i]));
         }
         catch (XmlBlasterException ex) {
            if (e == null) e = ex;
            this.log.error(ME, "removeAssociatedSubscriptions: exception occured for session '" + sessionInfo.getId() + "' and subscriptions '" + subs[i].getId() + "' : ex: " + ex.getMessage());
         }
      }
      // just throw the first exception encountered (if any)
      if (e != null) throw e;
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
         // init the storages
         QueuePropertyBase sessionProp = new SessionStoreProperty(this.global, this.global.getStrippedId());   
         if (sessionProp.getMaxEntries() > 0L) {
            String type = sessionProp.getType();
            String version = sessionProp.getVersion();
            this.sessionStorageId = new StorageId(Constants.RELATING_SESSION, this.global.getStrippedId() +"/" + this.info.getId());
            this.sessionStore = this.global.getStoragePluginManager().getPlugin(type, version, this.sessionStorageId, sessionProp);
         }
         else {
            if (log.TRACE) log.trace(ME, Constants.RELATING_SUBSCRIBE + " persistence for subscribe is switched of with maxEntries=0");
         }
         QueuePropertyBase subscribeProp = new SubscribeStoreProperty(this.global, this.global.getStrippedId());   
         if (subscribeProp.getMaxEntries() > 0L) {
            String type = subscribeProp.getType();
            String version = subscribeProp.getVersion();
            this.subscribeStorageId = new StorageId(Constants.RELATING_SUBSCRIBE, this.global.getStrippedId() +"/" + this.info.getId());
            this.subscribeStore = this.global.getStoragePluginManager().getPlugin(type, version, this.subscribeStorageId, subscribeProp);
         }
         else if (log.TRACE) log.trace(ME, Constants.RELATING_SUBSCRIBE + " persistence for subscribe is switched of with maxEntries=0");
         this.isOK = true;

         // register before having retreived the data since needed to fill info objects with persistenceId
         this.global.getRequestBroker().getAuthenticate().addClientListener(this);
         this.global.getRequestBroker().addSubscriptionListener(this);
         HashMap sessionIds = recoverSessions();
         recoverSubscriptions(sessionIds);
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
         this.global.getRequestBroker().getAuthenticate().removeClientListener(this);
         this.global.getRequestBroker().removeSubscriptionListener(this);
         this.sessionStore.shutdown(); 
         this.subscribeStore.shutdown();
      }
   }

   /**
    * A new session is added, checks if it shall be persisted. 
    */
   private void addSession(SessionInfo sessionInfo) throws XmlBlasterException {

      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();

      // Is transient?
      if (connectQosData.getPersistentProp() == null || !connectQosData.getPersistentProp().getValue()) return;
      
      // Avoid recursion
      if (sessionInfo.getConnectQos().isFromPersistenceRecovery()) return;

      if (sessionInfo.getSessionName().isPubSessionIdInternal()) { // negative pubSessionId?
         log.warn(ME,"To use persistent session/subscriptions you should login with a given publicSessionId, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.persistence.session.html");
      }

      // Persist it
      long uniqueId = new Timestamp().getTimestamp();
      SessionEntry entry = new SessionEntry(connectQosData.toXml(), uniqueId, connectQosData.size());
      if (this.log.TRACE) this.log.trace(ME, "addSession (persistent) for NEW uniqueId: '" + entry.getUniqueId() + "'");
      sessionInfo.setPersistenceUniqueId(uniqueId);
      this.sessionStore.put(entry);
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionAdded(ClientEvent e) throws XmlBlasterException {
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionAdded: invoked when plugin already shut down");
      SessionInfo sessionInfo = e.getSessionInfo();
      addSession(sessionInfo);      
   }

   public void sessionRemoved(ClientEvent e) throws XmlBlasterException {
   }

   /**
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "sessionRemoved '" + e.getSessionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".sessionRemoved: invoked when plugin already shut down");
      
      SessionInfo sessionInfo = e.getSessionInfo();
      ConnectQosData connectQosData = sessionInfo.getConnectQos().getData();
      if (!connectQosData.getPersistentProp().getValue()) return;

      removeAssociatedSubscriptions(sessionInfo); 

      // TODO add a method I_Queue.removeRandom(long uniqueId)
      long uniqueId = sessionInfo.getPersistenceUniqueId();
      if (this.log.TRACE) this.log.trace(ME, "sessionRemoved (persistent) for uniqueId: '" + uniqueId + "'");
      // String sessionId = getOriginalSessionId(connectQosData.getSessionQos().getSecretSessionId());
      SessionEntry entry = new SessionEntry(connectQosData.toXml(), uniqueId, 0L);
      int num = this.sessionStore.remove(entry);
      if (num != 1) {
         this.log.error(ME, "sessionRemoved (persistent) for uniqueId: '" + uniqueId + "' failed, entry not found.");
      }
   }

   /**
    * This event is invoked even by child subscriptions. However only 
    * parent subscriptions should be stored, so all child subscriptions are
    * ignored. 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "subscriptionAdd '" + e.getSubscriptionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".subscriptionAdded: invoked when plugin already shut down");
      //Thread.dumpStack();

      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      KeyData data = subscriptionInfo.getKeyData();
      // if (!(data instanceof QueryKeyData)) return; // this filters away child subscriptions
      if (subscriptionInfo.isCreatedByQuerySubscription()) return;
      
      // TODO add a method I_Queue.removeRandom(long uniqueId)
      QueryQosData subscribeQosData = subscriptionInfo.getQueryQosData();
      if (this.log.DUMP) this.log.dump(ME, "subscriptionAdd: key='" + data.toXml() + "'");
      if (subscribeQosData != null) if (this.log.DUMP) this.log.dump(ME, "subscriptionAdd: qos='" + subscribeQosData.toXml() + "'");
      if (subscribeQosData == null || !subscribeQosData.isPersistent()) return;

      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo(); 
      if (!sessionInfo.getConnectQos().getData().isPersistent()) {
         sessionInfo.getConnectQos().getData().setPersistent(true);
         this.addSession(sessionInfo);         
      }

      // is it a remote connect ?
      ClientProperty clientProperty = subscribeQosData.getClientProperty(Constants.PERSISTENCE_ID);
      if (clientProperty == null) {
         long uniqueId = new Timestamp().getTimestamp();
         subscribeQosData.getClientProperties().put(Constants.PERSISTENCE_ID, new ClientProperty(Constants.PERSISTENCE_ID, "long", null, "" + uniqueId));
         QueryKeyData subscribeKeyData = (QueryKeyData)data;
         
         // to be found when the client usubscribes after a server crash ...
         subscribeQosData.setSubscriptionId(subscriptionInfo.getSubscriptionId());         
         SubscribeEntry entry = new SubscribeEntry(subscribeKeyData.toXml(), subscribeQosData.toXml(), sessionInfo.getConnectQos().getSessionName().getAbsoluteName(), uniqueId, 0L);
         if (this.log.TRACE) this.log.trace(ME, "subscriptionAdd: putting to persistence NEW entry '" + entry.getUniqueId() + "' key='" + subscribeKeyData.toXml() + "' qos='" + subscribeQosData.toXml() + "' secretSessionId='" + sessionInfo.getSecretSessionId() + "'");
         subscriptionInfo.setPersistenceId(uniqueId);
         this.subscribeStore.put(entry);
      }
      else  {    // ... or from a recovery ?
         // TODO handle by recoverSubscriptions(..)
         // No remove: To avoid danger of looping we keep the marker (Marcel 2005-08-08)
         //   subscribeQosData.getClientProperties().remove(Constants.PERSISTENCE_ID);

         long uniqueId = clientProperty.getLongValue();
         if (this.log.TRACE) this.log.trace(ME, "subscriptionAdd: filling OLD uniqueId into subscriptionInfo '" + uniqueId + "'");
         subscriptionInfo.setPersistenceId(uniqueId);
         ClientProperty prop = subscribeQosData.getClientProperty(ORIGINAL_INITIAL_UPDATES);
         if (prop != null) {
            if (subscriptionInfo.getSubscribeQosServer() != null) { 
               subscriptionInfo.getSubscribeQosServer().inhibitInitalUpdates(true);
               subscribeQosData.getClientProperties().remove(ORIGINAL_INITIAL_UPDATES);
            }
         }
      }
   }

   /**
    * 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent e)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "subscriptionRemove '" + e.getSubscriptionInfo().getId() + "'");
      if (!this.isOK) throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_UNAVAILABLE, ME + ".subscriptionRemove: invoked when plugin already shut down");

      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      KeyData keyData = subscriptionInfo.getKeyData();
      if (!(keyData instanceof QueryKeyData)) {
         if (this.log.TRACE) this.log.trace(ME, "subscriptionRemove keyData wrong instance'");
         return;
      }
      if (subscriptionInfo.getPersistenceId() < 1L) {
         return;
      }
      // TODO add a method I_Queue.removeRandom(long uniqueId)
      QueryQosData qosData = subscriptionInfo.getQueryQosData();
      if (qosData == null || qosData.getPersistentProp() == null || !qosData.getPersistentProp().getValue()) {
         return;
      }

      this.subscribeStore.remove(subscriptionInfo.getPersistenceId());
      /*
      SubscribeEntry entry = new SubscribeEntry(keyData.toXml(), qosData.toXml(), subscriptionInfo.getSessionInfo().getConnectQos().getSessionName().getAbsoluteName(), subscriptionInfo.getPersistenceId(), 0L);
      if (this.log.TRACE) this.log.trace(ME, "subscriptionRemove: removing from persistence entry '" + entry.getUniqueId() + "' secretSessionId='" + subscriptionInfo.getSessionInfo().getConnectQos().getSessionName().getAbsoluteName());
      this.subscribeStore.remove(entry);
      */
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
   
   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#getPriority()
    */
   public Integer getPriority() {
      return PRIO_10;
   }
}
