/*------------------------------------------------------------------------------
Name:      TopicHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.TopicEntry;

import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.qos.UpdateQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.mime.I_AccessFilter;

import org.xmlBlaster.client.qos.PublishReturnQos;

import java.util.*;


/**
 * Handles all MsgUnit entries of same oid and its subscribers. 
 * <p>
 * This handler has the state UNCONFIGURED | UNREFERENCED | ALIVE | DEAD, see
 * the boolean state access methods for a description
 * </p>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecylce.html">The engine.message.lifecylce requirement</a>
 * @see org.xmlBlaster.test.topic.TestTopicLifeCycle
 * @author xmlBlaster@marcelruff.info
 */
public final class TopicHandler implements I_Timeout
{
   private String ME = "TopicHandler";
   private final Global glob;
   private final LogChannel log;

   private boolean dyingInProgress = false;

   /** The unique identifier of this topic e.g. "/node/heron/topic/Hello" */
   private final String id;

   /** The broker which manages me */
   private final RequestBroker requestBroker;

   private MsgUnitWrapper tmpVolatileMsgUnitWrapper;

   private TopicEntry topicEntry; // persistence storage entry

   // Default is that a single client can subscribe the same message multiple times
   // private boolean allowMultiSubscriptionPerClient = glob.getProperty().get("Engine.allowMultiSubscriptionPerClient", true);

   /**
    * This map knows all clients which have subscribed on this message content
    * and knows all individual wishes of the subscription (QoS).
    *
    * The map contains SubscriptionInfo objects.
    *
    * It is a TreeMap, that means it keeps order information.
    * TODO: express order attribute so that the first client will be served first.
    *
    * key   = a unique key identifying the subscription
    * value = SubscriptionInfo object
    */
   final private Map subscriberMap = new TreeMap(); // Collections.synchronizedMap(new TreeMap(/*new Comparator()*/));

   /** Do error recovery if message can't be delivered and we give it up */
   private I_MsgErrorHandler msgErrorHandler;

   /**
    * MsgUnit references are stored in a persistent history queue. 
    */
   private I_Queue historyQueue;

   private SessionName creatorSessionName;

   /** The configuration for this TopicHandler */
   private TopicProperty topicProperty;

   private I_Map msgUnitCache;

   /** The xmlKey with parsed DOM tree, is null in state=UNCONFIGURED */
   private XmlKey xmlKey;
   /** Attribute oid of key tag: <key oid="..."> </key> */
   private String uniqueKey;
   /** This holds the quick parsed key information, if you need the DOM use xmlKey instead */
   private MsgKeyData msgKeyData;

   private boolean handlerIsNewCreated=true;  // a little helper showing if topic is new created

   private boolean isRegisteredInBigXmlDom = false;

   /**
    * This topic is destroyed after given timeout
    * The timer is activated on state change to UNREFERENCED
    * and removed on change to ALIVE
    */
   private Timeout destroyTimer;
   private Timestamp timerKey = null;

   private final static int UNDEF = -1;
   private final static int UNCONFIGURED = 0;
   private final static int ALIVE = 1;
   private final static int UNREFERENCED = 2;
   private final static int SOFT_ERASED = 3;
   private final static int DEAD = 4;
   private int state = UNDEF;


   /**
    * Use this constructor if a subscription is made on a yet unknown object.
    * <p />
    * @param requestBroker
    * @param uniqueKey The unique XmlKey-oid from the subscribe() call
    */
   public TopicHandler(RequestBroker requestBroker, String uniqueKey) throws XmlBlasterException
   {
      this.glob = requestBroker.getGlobal();
      if (uniqueKey == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid constructor parameters");

      this.log = glob.getLog("core");
      this.id = this.glob.getNodeId() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + uniqueKey;
      this.ME += this.glob.getLogPrefixDashed() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + uniqueKey;
      this.requestBroker = requestBroker;
      this.uniqueKey = uniqueKey;
      this.destroyTimer = requestBroker.getGlobal().getTopicTimer();
      this.msgErrorHandler = new MsgTopicErrorHandler(glob, this);

      toUnconfigured();
      TopicHandler t = this.requestBroker.addTopicHandler(this);
      if (t != null) {
         log.error(ME, "Unexpected duplicated of TopicHandler in RequestBroker");
         Thread.currentThread().dumpStack();
      }
      
      if (log.CALL) log.trace(ME, "Creating new TopicHandler because of subscription.");
      // mimeType and content remains unknown until first data is fed
   }

   /**
    * Use this constructor if a yet unknown object is fed by method publish().
    * <p />
    * You should call publish() thereafter
    * @param requestBroker
    * @param a MsgUnitWrapper containing the CORBA MsgUnit data container
    */
   public TopicHandler(RequestBroker requestBroker, SessionInfo publisherSessionInfo, String keyOid) throws XmlBlasterException {
      this.glob = requestBroker.getGlobal();
      if (keyOid == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid constructor parameters, keyOid=null");

      this.log = glob.getLog("core");
      this.requestBroker = requestBroker;
      this.uniqueKey = keyOid;
      this.id = this.glob.getNodeId() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + keyOid;
      this.ME += this.glob.getLogPrefixDashed() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + keyOid;
      this.destroyTimer = requestBroker.getGlobal().getTopicTimer();
      this.msgErrorHandler = new MsgTopicErrorHandler(glob, this);
      
      //Happens automatically on first publish
      //administrativeInitialize((MsgKeyData)msgUnit.getKeyData(), (MsgQosData)msgUnit.getQosData());

      toUnconfigured();
      TopicHandler t = requestBroker.addTopicHandler(this);
      if (t != null) {
         log.error(ME, "Unexpected duplicated of TopicHandler in RequestBroker");
         Thread.currentThread().dumpStack();
      }
      if (log.CALL) log.trace(ME, "Creating new TopicHandler.");
   }

   /**
    * The unique identifier of this topic e.g. "/node/heron/topic/Hello"
    */
   public String getId() {
      return this.id;
   }

   /**
    * Initialize the messageUnit cache and the history queue for this topic
    */
   private synchronized void administrativeInitialize(MsgKeyData msgKeyData, MsgQosData publishQos) throws XmlBlasterException {
      if (!isUnconfigured()) {
         log.error(ME, "Sorry, reconfiguring TopicHandler is not yet supported, we ignore the request");
         return;
      }

      if (this.msgKeyData == null) {
         this.msgKeyData = msgKeyData;
      }

      if (log.DUMP) log.dump(ME, "administrativeInitialize()" + publishQos.toXml());

      this.creatorSessionName = publishQos.getSender();
      this.topicProperty = publishQos.getTopicProperty();

      startupMsgstore();

      // Todo: this needs to be done after TopicHandler is created
      startupHistoryQueue();

      synchronized (this) {
         if (isUnconfigured()) { // Startup of topic
            if (!hasCacheEntries() && !hasSubscribers()) {
               toUnreferenced();
            }
            else {
               toAlive();
            }
         }
      }

      if (true /*log.INFO*/) {
         long maxMsgHistory = this.topicProperty.getHistoryQueueProperty().getMaxMsg();
         String hist = (maxMsgHistory > 0) ? "history/maxMsg="+maxMsgHistory : "message history is switched off with queue/history/maxMsg=0";
         long maxMsgStore = this.topicProperty.getMsgUnitStoreProperty().getMaxMsg();
         String store = (maxMsgStore > 0) ? "persistence/msgUnitStore/maxMsg="+maxMsgStore : "message storage is switched off with persistence/msgUnitStore/maxMsg=0";
         log.info(ME, "New topic is ready, " + hist + ", " + store);
      }
   }

   /**
    * This cache stores the 'real meat' (the MsgUnit data struct)
    */
   private void startupMsgstore() throws XmlBlasterException   {
      synchronized (this) {
         MsgUnitStoreProperty msgUnitStoreProperty = this.topicProperty.getMsgUnitStoreProperty();
         if (this.msgUnitCache == null) {
            String type = msgUnitStoreProperty.getType();
            String version = msgUnitStoreProperty.getVersion();
            StorageId msgUnitStoreId = new StorageId("msgUnitStore", glob.getNodeId()+"/"+getUniqueKey());
            this.msgUnitCache = glob.getStoragePluginManager().getPlugin(type, version, msgUnitStoreId, msgUnitStoreProperty); //this.msgUnitCache = new org.xmlBlaster.engine.msgstore.ram.MapPlugin();
            if (this.msgUnitCache == null) {
               throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "Can't load msgUnitStore persistence plugin [" + type + "][" + version + "]");
            }
         }
         else {
            log.info(ME, "Reconfiguring message store.");
            this.msgUnitCache.setProperties(msgUnitStoreProperty);
         }
      }
   }

   /**
    * Should be invoked delayed as soon as TopicHandler instance is created an registered everywhere
    * as we ask the msgUnitStore for the real messages if some history entries existed. 
    * <p>
    * NOTE: this.historyQueue can be null if maxMsgs=0 is configured
    * </p>
    * <p>
    * This history queue entries hold weak references to the msgUnitCache entries
    * </p>
    */
   private void startupHistoryQueue() throws XmlBlasterException {
      synchronized (this) {
         QueuePropertyBase prop = this.topicProperty.getHistoryQueueProperty();
         if (this.historyQueue == null) {
            if (prop.getMaxMsg() > 0L) {
               String type = prop.getType();
               String version = prop.getVersion();
               StorageId queueId = new StorageId("history", glob.getNodeId()+"/"+getUniqueKey());
               this.historyQueue = glob.getQueuePluginManager().getPlugin(type, version, queueId, prop);
               this.historyQueue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting
            }
            else {
               if (log.TRACE) log.trace(ME, "History queuing of this topic is switched of with maxMsg=0");
            }
         }
         else {
            if (prop.getMaxMsg() > 0L) {
               log.info(ME, "Reconfiguring history queue.");
               this.historyQueue.setProperties(prop);
            }
            else {
               log.warn(ME, "Destroying history queue with " + this.historyQueue.getNumOfEntries() +
                            " entries because of new configuration with maxMsg=0");
               this.historyQueue.clear();
               this.historyQueue.shutdown();
               this.historyQueue = null;
            }
         }
      }
   }

   /**
    * @return false if topicProperty.isCreateDomEntry() was configured to false
    */
   public boolean hasDomTree() {
      if (this.topicProperty == null) {
         return false;
      }
      return this.topicProperty.createDomEntry();
   }

   public void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect " + getId());
   }

   public RequestBroker getRequestBroker() {
      return this.requestBroker;
   }

   /**
    * Check if there is a valid DOM parsed XML key available
    * @return false in state UNCONFIGURED
    */
   public final boolean hasXmlKey() {
      return this.msgKeyData != null;
   }

   /**
    * Accessing the DOM parsed key of this message. 
    * @return Never null
    * @exception XmlBlasterException in state UNCONFIGURED or on DOM parse problems
    */
   public final XmlKey getXmlKey() throws XmlBlasterException {
      if (this.msgKeyData == null) { // isUnconfigured()) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, getId(), "In state '" + getStateStr() + "' no XmlKey object is available");
      }
      if (this.xmlKey == null) {
         synchronized (this) {
            if (this.xmlKey == null) {  // expensive DOM parse
               this.xmlKey = new XmlKey(glob, this.msgKeyData);
            }
         }
      }
      return this.xmlKey;
   }

   /**
    * Create or access the cached persistence storage entry of this topic. 
    * @return null If no PublishQos is available to create persistent information
    */
   private TopicEntry persistTopicEntry() throws XmlBlasterException {
      if (this.topicEntry == null) {
         boolean isNew = false;
         synchronized (this) {
            if (this.topicEntry == null) {
               if (log.TRACE) log.trace(ME, "Creating TopicEntry to make topic persistent");
               if (this.topicProperty==null || this.msgKeyData==null) {
                  log.error(ME, "Can't create useful TopicEntry in state=" + getStateStr() + " no QoS is available");
                  return null;
               }
               MsgQosData msgQosData = new MsgQosData(glob);
               msgQosData.setTopicProperty(this.topicProperty);
               msgQosData.setAdministrative(true);
               msgQosData.touchRcvTimestamp();
               msgQosData.setPersistent(true);
               msgQosData.setSender(creatorSessionName);
               MsgUnit msgUnit = new MsgUnit(glob, this.msgKeyData, null, msgQosData);
               this.topicEntry = new TopicEntry(glob, msgUnit);
               isNew = true;
               if (log.TRACE) log.trace(ME, "Created persistent topicEntry '" + this.topicEntry.getUniqueId() + "'"); //: " + this.topicEntry.toXml());
            }
         }

         if (isNew) {
            persistTopic(this.topicEntry);
         }
      }
      return this.topicEntry;
   }

   /**
    * @return true if this topicEntry was made persistent
    */
   private boolean persistTopic(TopicEntry entry) {
      try {
         if (log.TRACE) log.trace(ME, "Making topicHandler persistent, topicEntry=" + topicEntry.getUniqueId());
         int numAdded = this.requestBroker.addPersistentTopicHandler(entry);
         //log.error(ME, "DEBUG ONLY: Persisted " + numAdded + " TopicHandler: " + toXml());
         //Thread.currentThread().dumpStack();
         if (log.TRACE) log.trace(ME, "Persisted " + numAdded + " TopicHandler");
         return numAdded>0;
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Persisting TopicHandler failed, we continue memory based: " + e.getMessage());
      }
      return false;
   }

   /**
    * Triggered by persistent store, administrative message to configure this topic
    *
   public PublishReturnQos publish(SessionInfo publisherSessionInfo, MsgUnit msgUnit, PublishQosServer publishQosServer) throws XmlBlasterException
      this.topicEntry = topicEntry;
      MsgUnit msgUnit = topicEntry.getMsgUnit();
      log.info(ME, "Restoring topic '" + msgUnit.getKeyOid() + "' from persistency.");
      publish(publisherSessionInfo, msgUnit, publishQosServer);
   }
   */
   
   /**
    * A new publish event (PubSub or PtP) arrives. 
    * <br />
    * Publish filter plugin checks are done already<br />
    * Cluster forwards are done already.
    *
    * @param publisherSessionInfo  The publisher
    * @param msgUnit     The new message
    * @param publishQosServer  The decorator for msgUnit.getQosData()
    *
    * @return not null for PtP messages
    */
   public PublishReturnQos publish(SessionInfo publisherSessionInfo, MsgUnit msgUnit, PublishQosServer publishQosServer) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Setting content");

      StatusQosData qos = new StatusQosData(glob);
      qos.setKeyOid(this.uniqueKey);
      qos.setState(Constants.STATE_OK);
      qos.setRcvTimestamp(publishQosServer.getRcvTimestamp());
      PublishReturnQos publishReturnQos = new PublishReturnQos(glob, qos);

      MsgKeyData msgKeyData = (MsgKeyData)msgUnit.getKeyData();
      MsgQosData msgQosData = (MsgQosData)msgUnit.getQosData();
      /* Happens in RequestBroker already
      if (msgQosData.getSender() == null) {
         msgQosData.setSender(publisherSessionInfo.getSessionName());
      }
      */

      //if (this.msgKeyData == null) { // If TopicHandler existed because of a subscription: remember on first publish
      //   this.msgKeyData = msgKeyData;
      //}

      if (isUnconfigured() || msgQosData.isAdministrative()) {
         if (publishQosServer.getTopicEntry() != null) {
            this.topicEntry = publishQosServer.getTopicEntry(); // Call from persistent layer, reuse the TopicEntry
            if (log.TRACE) log.trace(ME, "Reuse TopicEntry persistence handle: " + this.topicEntry.toXml());
         }

         administrativeInitialize(msgKeyData, msgQosData);
         if (msgQosData.isAdministrative()) {
            if (this.handlerIsNewCreated) {
               // Check all known query subscriptions if the new message fits as well (does it only if TopicHandler is new)
               glob.getRequestBroker().checkExistingSubscriptions(publisherSessionInfo, this, publishQosServer);
               this.handlerIsNewCreated = false;
            }
            if (msgQosData.isFromPersistenceStore()) {
               log.info(ME, "Topic is successfully recovered from persistency to state " + getStateStr() +
                       //((requestBroker.getTopicStore()!=null) ? (" '" + requestBroker.getTopicStore().getStorageId() + "'") : "") +
                       " with " + getNumOfHistoryEntries() + " history entries (" + getNumOfCacheEntries() + " currently referenced msgUnits are loaded).");
            }
            else {
               log.info(ME, "Topic is successfully configured by administrative message.");
            }
            publishReturnQos.getData().setStateInfo("Administrative configuration request handled");
            return publishReturnQos;
         }
      }

      if (!isAlive()) {
         toAlive();
      }

      int initialCounter = 0; // Force referenceCount until update queues are filled (volatile messages)
      MsgUnitWrapper msgUnitWrapper = null;
      
      try { // finally
         boolean changed = true;
         synchronized (this) {
            if (this.topicProperty.isReadonly() && hasHistoryEntries()) {
               log.warn(ME+".Readonly", "Sorry, published message '" + msgKeyData.getOid() + "' rejected, message is readonly.");
               throw new XmlBlasterException(glob, ErrorCode.USER_PUBLISH_READONLY, ME, "Sorry, published message '" + msgKeyData.getOid() + "' rejected, message is readonly.");
            }

            if (msgQosData.isForceUpdate() == false && hasHistoryEntries()) {
               MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)this.historyQueue.peek();
               if (entry != null) {
                  MsgUnitWrapper old = entry.getMsgUnitWrapper();
                  if (old != null) {
                     changed = !old.getMsgUnit().sameContent(msgUnit.getContent());
                  }
               }
            }

            // Remove oldest history entry (if queue is full) and decrease reference counter in msgUnitStore
            long numHist = getNumOfHistoryEntries();
            if (numHist > 0L && numHist >= this.historyQueue.getMaxNumOfEntries()) {
               ArrayList entryList = this.historyQueue.takeLowest(1, -1L, null, false);
               if (entryList.size() != 1) {
                  throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME,
                        "Can't remove expected entry, entryList.size()=" + entryList.size() + ": " + this.historyQueue.toXml(""));
               }
               /*
               MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)entryList.get(0);
               MsgUnitWrapper msgUnitEntry = entry.getMsgUnitWrapper();
               if (msgUnitEntry != null) { // Check WeakReference
                  this.msgUnitCache.remove(msgUnitEntry.getUniqueId()); // decrements reference counter -= 1 -> the entry is only removed if reference counter == 0
               }
               */
               MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)entryList.get(0);
               if (log.TRACE) { if (!entry.isInternal()) log.trace(ME, "Removed oldest entry in history queue."); }
            }


            initialCounter = 1; // Force referenceCount until update queues are filled (volatile messages)
            msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, this.msgUnitCache.getStorageId(), initialCounter, 0);
       
            this.msgUnitCache.put(msgUnitWrapper);

            if (this.historyQueue != null && msgUnitWrapper.isAlive() &&             // no volatile messages
                !(publishQosServer.isPtp() && publishQosServer.isSubscribeable())) { // no invisible PtP 
               try { // increments reference counter += 1
                  this.historyQueue.put(new MsgQueueHistoryEntry(glob, msgUnitWrapper, this.historyQueue.getStorageId()), false);
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "History queue put() problem: " + e.getMessage());
               }
            }
         } // synchronized

         // NOTE: Putting entries into callback queues must be outside of a synchronized(topicHandler) to avoid deadlock
         //       The DeliveryWorker removes a MsgUnitWrapper entry from the msgstore (see entryDestroyed()) and would deadlock

         //----- 2a. now we can send updates to all destination clients:
         if (publishQosServer.isPtp()) {
            /*publishReturnQos =*/ forwardToDestinations(publisherSessionInfo, msgUnitWrapper, publishQosServer);
            if (!publishQosServer.isSubscribeable()) {
               publishReturnQos.getData().setStateInfo("PtP request handled");
               return publishReturnQos;
            }
         }

         //----- 2b. now we can send updates to all subscribed clients:
         if (log.TRACE) log.trace(ME, "Message " + msgUnit.getLogId() + " handled, now we can send updates to all interested clients.");
         if (changed || msgQosData.isForceUpdate()) { // if the content changed of the publisher forces updates ...
            invokeCallback(publisherSessionInfo, msgUnitWrapper);
         }

         if (this.handlerIsNewCreated) {
            // Check all known query subscriptions if the new message fits as well (does it only if TopicHandler is new)
            this.tmpVolatileMsgUnitWrapper = msgUnitWrapper;
            glob.getRequestBroker().checkExistingSubscriptions(publisherSessionInfo, this, publishQosServer);
            this.tmpVolatileMsgUnitWrapper = null;
            this.handlerIsNewCreated = false;
         }
      }
      finally {
         // Event to check if counter == 0 to remove cache entry again (happens e.g. for volatile msg without a no subscription)
         // MsgUnitWrapper calls topicEntry.destroyed(MsgUnitWrapper) if it is in destroyed state
         if (initialCounter != 0) {
            msgUnitWrapper.incrementReferenceCounter((-1)*initialCounter, null); // Reset referenceCount until update queues are filled
         }
      }
      return publishReturnQos;
   }

   /**
    * Forward PtP messages
    */
   public PublishReturnQos forwardToDestinations(SessionInfo publisherSessionInfo, MsgUnitWrapper cacheEntry, PublishQosServer publishQos) throws XmlBlasterException {

      // NOTE: cluster forwarded PtP destinations are removed already from this list:
      Destination[] destinationArr = publishQos.getDestinationArr(); // !!! add XPath client query here !!!
      PublishReturnQos publishReturnQos = null;
      Authenticate authenticate = glob.getAuthenticate();

      //-----    Send message to every destination client
      for (int ii = 0; ii<destinationArr.length; ii++) {
         Destination destination = destinationArr[ii];
         if (log.TRACE) log.trace(ME, "Working on PtP message for destination [" + destination.getDestination() + "]");

         SessionInfo receiverSessionInfo = null;
         if (destination.getDestination().isSession()) {
            receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
         }

         //if (destination.getDestination().isSession() && receiverSessionInfo == null) {
         //   String tmp = "Sending PtP message to unknown session '" + destination.getDestination() + "' failed, message is lost.";
         //   log.warn(ME, tmp);
         //   throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNSESSION, ME, tmp);
         //}

         if (receiverSessionInfo != null) { // PtP to session which is logged in.
            MsgQueueUpdateEntry msgEntry = new MsgQueueUpdateEntry(glob, cacheEntry,
                             receiverSessionInfo.getSessionQueue().getStorageId(), destination.getDestination(),
                             Constants.SUBSCRIPTIONID_PtP);
            receiverSessionInfo.queueMessage(msgEntry);
         }
         else {  // PtP sent to subject or PtP sent to unknown session:
            if (destination.forceQueuing()) {
               SubjectInfo destinationClient = authenticate.getOrCreateSubjectInfoByName(destination.getDestination());
               MsgQueueUpdateEntry msgEntry = new MsgQueueUpdateEntry(glob, cacheEntry,
                             destinationClient.getSubjectQueue().getStorageId(), destination.getDestination(),
                             Constants.SUBSCRIPTIONID_PtP);
               destinationClient.queueMessage(msgEntry);
            }
            else {
               SubjectInfo destinationClient = authenticate.getSubjectInfoByName(destination.getDestination());
               if (destinationClient == null) {
                  String tmp = "Sending PtP message to '" + destination.getDestination() + "' failed, message is lost.";
                  log.warn(ME, tmp);
                  throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNDESTINATION, ME,
                            tmp+" Client is not logged in and <destination forceQueuing='true'> is not set");
               }
               MsgQueueUpdateEntry msgEntry = new MsgQueueUpdateEntry(glob, cacheEntry,
                              destinationClient.getSubjectQueue().getStorageId(), destination.getDestination(),
                              Constants.SUBSCRIPTIONID_PtP);
               destinationClient.queueMessage(msgEntry);
            }
         }
      }

      return publishReturnQos;
   }

   /**
    * @return The storage containing the 'meat' of a message
    */
   public I_Map getMsgUnitCache() {
      return this.msgUnitCache;
   }

   public MsgUnitWrapper getMsgUnitWrapper(long uniqueId) throws XmlBlasterException {
      if (this.msgUnitCache == null) { // on startup
         return null;
      }
      return (MsgUnitWrapper)this.msgUnitCache.get(uniqueId);
   }

   /**
    * Event triggered by MsgUnitWrapper itself when it expires
    */
   public void entryExpired(MsgUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (this.historyQueue == null) {
         return;
      }
      int numHistory = msgUnitWrapper.getHistoryReferenceCounter();
      if (numHistory > 0) {
         // We need to remove it from the history queue or at least decrement the referenceCounter
         // in which case we have a stale reference in the history queue (which should be OK, it is
         // removed as soon as it is taken out of it)
         msgUnitWrapper.incrementReferenceCounter((-1)*numHistory, this.historyQueue.getStorageId());
      }
      /*
      // Task: We need to check if msgUnitWrapper is referenced from history queue
      // and if it is we need to remove it from the history queue or at least decrement the referenceCounter
      // Another problem: There is no lookup with msgUnitWrapper.getUniqueId() into history queue
      // possible since the history entry has its own uniqueId as primary key
      // Therefore: Slow sequence lookup:
      ArrayList list = this.historyQueue.peek(-1, -1);
      int n = list.size();
      MsgQueueHistoryEntry entry = null;
      for(int i=0; i<n; i++) {
         MsgQueueHistoryEntry tmp = (MsgQueueHistoryEntry)list.get(i);
         if (tmp.getMsgUnitWrapperUniqueId() == msgUnitWrapper.getUniqueId()) {
            entry = tmp;
            break;
         }
      }
      if (entry != null) {
         boolean useRandom = false;
         if (useRandom) {
            this.historyQueue.removeRandom(entry); // Note: This reduces the msgUnitStore referencecounter
         }
         else {
            // found entry, decrement but leave in history queue as we don't have a removeRandom
            msgUnitWrapper.incrementReferenceCounter(-1, null);
         }
      }
      */
   }

   /**
    * Event triggered by MsgUnitWrapper itself when it reaches destroy state
    */
   public void entryDestroyed(MsgUnitWrapper msgUnitWrapper) {
      /*
      if (this.historyQueue != null) {
         try {
            !! where to get msgUnitWrapperHistoryEntry from? avoid removeRandom() as it complicates persistent queue implementation
            this.historyQueue.removeRandom(msgUnitWrapperHistoryEntry); // Note: This reduces the msgUnitStore referencecounter
         }
         catch (XmlBlasterException e) {
            log.error(ME, "Internal problem in entryDestroyed removeRandom of history queue (this can lead to a memory leak of '" + msgUnitWrapper.getLogId() + "'): " +
                          e.getMessage() + ": " + toXml());
         }
      }
      */

      try {
         getMsgUnitCache().remove(msgUnitWrapper);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Internal problem in entryDestroyed removeRandom of msg store (this can lead to a memory leak of '" + msgUnitWrapper.getLogId() + "'): " +
                       e.getMessage() + ": " + toXml());
      }
      
      // if it was a volatile message we need to check unreferenced state
      synchronized (this) {
         if (!hasCacheEntries() && !hasSubscribers()) {
            try {
               if (isSoftErased()) {
                  toDead(this.creatorSessionName, false);
               }
               else {
                  toUnreferenced();
               }
            }
            catch (XmlBlasterException e) {
               log.error(ME, "Internal problem with removeSubscriber: " + e.getMessage() + ": " + toXml());
            }
         }
      }
      msgUnitWrapper = null;
   }

   /*
    * The root node of the xmlBlaster DOM tree
    */
   public final org.w3c.dom.Node getRootNode() throws XmlBlasterException {
      return getXmlKey().getRootNode();
   }

   /**
    * A client subscribed to this message, multiple subscriptions from
    * the same client are OK.
    */
   public void addSubscriber(SubscriptionInfo sub) throws XmlBlasterException {
      if (sub.getSubscribeCounter() > 1)
         return;

      Object oldOne;
      synchronized(this.subscriberMap) {
         oldOne = this.subscriberMap.put(sub.getSubscriptionId(), sub);
      }

      sub.addTopicHandler(this);

      if (log.TRACE) log.trace(ME, "Client '" + sub.getSessionInfo().getId() + "' has successfully subscribed");

      if (isUnconfigured()) {
         return;
      }

      if (isUnreferenced()) {
         toAlive();
      }

      QueryQosData queryQos = sub.getQueryQosData();

      if (queryQos.getWantInitialUpdate() == true) {
         MsgUnitWrapper[] wrappers = null;
         if (this.tmpVolatileMsgUnitWrapper != null)
            wrappers = new MsgUnitWrapper[] { this.tmpVolatileMsgUnitWrapper };
         else if (hasHistoryEntries())
            wrappers = getMsgUnitWrapperArr(queryQos.getHistoryQos().getNumEntries(), false);

         if (wrappers != null) {
            if (invokeCallback(null, sub, wrappers) == 0) {
               Set removeSet = new HashSet();
               removeSet.add(sub);
               handleCallbackFailed(removeSet);
            }
         }
      }

      return;
   }

   /**
    * If a callback fails, we remove it from the subscription. 
    * <p />
    * Generating dead letter and auto-logout to release all resources is done by DeliveryWorker.
    */
   private void handleCallbackFailed(Set removeSet) throws XmlBlasterException {
      if (removeSet != null) {
         Iterator iterator = removeSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (log.TRACE) log.trace(ME, "Removed subscriber '" + sub.getSessionInfo().getId() + "' as callback failed.");
            sub.removeSubscribe();
         }
         removeSet.clear();
         removeSet = null;
      }
   }

   /**
    * A client wants to unSubscribe from this message
    * @return the removed SubscriptionInfo object or null if not found
    */
   SubscriptionInfo removeSubscriber(String subscriptionInfoUniqueKey) {
      if (log.TRACE) log.trace(ME, "Before size of subscriberMap = " + this.subscriberMap.size());

      SubscriptionInfo subs = null;
      synchronized(this.subscriberMap) {
         subs = (SubscriptionInfo)this.subscriberMap.remove(subscriptionInfoUniqueKey);
      }
      if (subs == null && !isDead() && !isSoftErased()) {
         Thread.currentThread().dumpStack();
         log.warn(ME, "Sorry, can't unsubscribe, you where not subscribed to subscription ID=" + subscriptionInfoUniqueKey);
      }

      if (log.TRACE) log.trace(ME, "After size of subscriberMap = " + this.subscriberMap.size());

      if (isDead()) {
         return subs; // during cleanup process
      }

      synchronized (this) {
         if (!hasCacheEntries() && !hasSubscribers()) {
            if (isUnconfigured())
               toDead(this.creatorSessionName, false);
            else {
               try {
                  toUnreferenced();
               }
               catch (XmlBlasterException e) {
                  log.error(ME, "Internal problem with removeSubscriber: " + e.getMessage() + ": " + toXml());
               }
            }
         }
      }
      return subs;
   }

   /**
    * This is the unique key of the topic and MsgUnit
    * <p />
    * @return the &lt;key oid='...'>
    */
   public String getUniqueKey() {
      return uniqueKey;
   }

   /**
    * @return The key data of this topic (not DOM parsed) or null of not yet known
    */
   public MsgKeyData getMsgKeyData() {
      return this.msgKeyData;
   }

   /**
    * What is the MIME type of this message content?
    * <p />
    * @return the MIME type of the MsgUnit.content or null if not known
    */
   public String getContentMime() {
      return (this.msgKeyData != null) ? this.msgKeyData.getContentMime() : null;
   }

   public String getContentMimeExtended() {
      return (this.msgKeyData != null) ? this.msgKeyData.getContentMimeExtended() : null;
   }

   /**
    * Access the raw CORBA msgUnit
    * @return MsgUnit object
   public MsgUnit getMsgUnit() throws XmlBlasterException {
      return getMsgUnitWrapper().getMsgUnit();
   }
    */

   /**
    * Send updates to all subscribed clients.
    * <p />
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    */
   private final void invokeCallback(SessionInfo publisherSessionInfo, MsgUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "MsgUnitWrapper is null");
      }
      if (log.TRACE) log.trace(ME, "Going to update dependent clients, subscriberMap.size() = " + this.subscriberMap.size());

      // Take a copy of the map entries (a current snapshot)
      // If we would iterate over the map directly we can risk a java.util.ConcurrentModificationException
      // when one of the callback fails and the entry is removed by the callback worker thread
      SubscriptionInfo[] subInfoArr = getSubscriptionInfoArr();
      Set removeSet = null;
      for (int ii=0; ii<subInfoArr.length; ii++) {
         SubscriptionInfo sub = subInfoArr[ii];
         MsgUnitWrapper[] msgUnitWrapperArr = { msgUnitWrapper };
         if (invokeCallback(publisherSessionInfo, sub, msgUnitWrapperArr) == 0) {
            if (removeSet == null) removeSet = new HashSet();
            removeSet.add(sub); // We can't delete directly since we are in the iterator
         }
      }
      if (removeSet != null) handleCallbackFailed(removeSet);
   }

   /**
    * Send update to subscribed client (Pub/Sub mode only).
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    * @param sub The subscription handle of the client
    * @return Number of successful processed messages, throws never an exception
    */
   private final int invokeCallback(SessionInfo publisherSessionInfo, SubscriptionInfo sub,
                                        MsgUnitWrapper[] msgUnitWrapperArr) {
      if (isUnconfigured()) {
         log.warn(ME, "invokeCallback() not supported, this MsgUnit was created by a subscribe() and not a publish()");
         return 0;
      }
      if (isUnreferenced()) {
         log.error(ME, "PANIC: invoke callback is strange in state 'UNREFERENCED'");
         Thread.currentThread().dumpStack();
         return 0;
      }

      if (msgUnitWrapperArr == null || msgUnitWrapperArr.length < 1) {
         Thread.currentThread().dumpStack();
         log.error(ME, "invokeCallback() MsgUnitWrapper is null");
         //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "MsgUnitWrapper is null");
      }

      AccessFilterQos[] filterQos = sub.getAccessFilterArr();
      int retCount = 0;

      NEXT_MSG:
      for(int xx=0; xx<msgUnitWrapperArr.length; xx++) {
         MsgUnitWrapper msgUnitWrapper = msgUnitWrapperArr[xx];
         MsgQosData msgQosData = msgUnitWrapper.getMsgQosData();
         try {
            if (sub.getSessionInfo().getSubjectInfo().isCluster()) {
               if (log.DUMP) log.dump(ME, "Slave node '" + sub.getSessionInfo() + "' has dirty read message '" + msgUnitWrapper.toXml());
               if (msgQosData.dirtyRead(sub.getSessionInfo().getSubjectInfo().getNodeId())) {
                  if (log.TRACE) log.trace(ME, "Slave node '" + sub.getSessionInfo() + "' has dirty read message '" + sub.getSubscriptionId() + "', '" + sub.getKeyData().getOid() + "' we don't need to send it back");
                  retCount++;
                  continue NEXT_MSG;
               }
            }

            if (filterQos != null) {
               //SubjectInfo publisher = (publisherSessionInfo == null) ? null : publisherSessionInfo.getSubjectInfo();
               //SubjectInfo destination = (sub.getSessionInfo() == null) ? null : sub.getSessionInfo().getSubjectInfo();
               for (int ii=0; ii<filterQos.length; ii++) {
                  try {
                     I_AccessFilter filter = requestBroker.getAccessPluginManager().getAccessFilter(
                                               filterQos[ii].getType(), filterQos[ii].getVersion(), 
                                               getContentMime(), getContentMimeExtended());
                     if (filter != null && filter.match(publisherSessionInfo, sub.getSessionInfo(),
                                msgUnitWrapper.getMsgUnit(), filterQos[ii].getQuery()) == false) {
                        retCount++; // filtered message is not send to client
                        continue NEXT_MSG;
                     }
                  }
                  catch (Throwable e) {
                     if (log.TRACE) log.trace(ME, "Mime access filter '" + filterQos[ii].getType() + " threw an exception: " + e.toString()); 
                     // sender =      publisherSessionInfo.getLoginName()
                     // receiver =    sub.getSessionInfo().getLoginName()
                     MsgQueueEntry entry =
                          new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
                                      sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId());
                     publisherSessionInfo.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entry, e));
                     retCount++;
                     continue NEXT_MSG;
                  }
               }
            } // if filterQos

            if (log.CALL) log.call(ME, "pushing update() message '" + sub.getKeyData().getOid() + "' " + msgUnitWrapper.getStateStr() +
                          "' into '" + sub.getSessionInfo().getId() + "' callback queue");
            
            UpdateReturnQosServer retQos = (UpdateReturnQosServer)sub.getMsgQueue().put(
                 new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
                     sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId()),
                 false);

            sub.getSessionInfo().getDeliveryManager().notifyAboutNewEntry();

            retCount++;
         }
         catch (Throwable e) {
            e.printStackTrace();
            if (log.TRACE) log.trace(ME, "Sending of message from " + publisherSessionInfo.getId() + " to " +
                               sub.getSessionInfo().getId() + " failed: " + e.toString());
            sub.getSessionInfo().getDeliveryManager().internalError(e); // calls MsgErrorHandler
            //retCount does not change
         }
      } // for iMsg
      return retCount;
   }

   public final int numSubscribers() {
      return this.subscriberMap.size();
   }

   public final boolean hasSubscribers() {
      return this.subscriberMap.size() != 0;
   }

   /**
    * Get a snapshot of all subscriptions
    */
   public final SubscriptionInfo[] getSubscriptionInfoArr() {
      synchronized(this.subscriberMap) {
         return (SubscriptionInfo[])this.subscriberMap.values().toArray(new SubscriptionInfo[this.subscriberMap.size()]);
      }
   }

   /**
    * Returns true if there are subscribers with exact query on oid or domain
    * @return false If no subscriber exists or all subscribers are through XPath query
    */
   public final boolean hasExactSubscribers() {
      synchronized(this.subscriberMap) {
         Iterator iterator = this.subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (!sub.isCreatedByQuerySubscription())
               return true;
         }
      }
      return false;
   }

   /**
    * Returns a Vector with SubscriptionInfo instances of this session
    * (a session may subscribe the same message multiple times). 
    * <p />
    * This searches from a given SessionInfo.
    */
   public final Vector findSubscriber(SessionInfo sessionInfo) {
      Vector vec = null;
      synchronized(this.subscriberMap) {
         Iterator iterator = this.subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().isSameSession(sessionInfo)) {
               if (vec == null) vec = new Vector();
               vec.addElement(sub);
            }
         }
      }
      return vec;
   }

   /**
    * subscribers are not informed here
    */
   private void clearSubscribers() {
      SubscriptionInfo[] subscriptionInfoArr = getSubscriptionInfoArr();
      for(int i=0; i<subscriptionInfoArr.length; i++) {
         try {
            glob.getRequestBroker().fireUnSubscribeEvent(subscriptionInfoArr[i]);
         }
         catch (XmlBlasterException e) {
            log.error(ME, "Problems in clearSubscriber: " + e.getMessage());
         }
      }
      synchronized(this.subscriberMap) {
         this.subscriberMap.clear();  // see notifySubscribersAboutErase() above
      }
   }

   /**
    * Do we contain at least one message?
    */
   public boolean hasHistoryEntries() {
      return getNumOfHistoryEntries() > 0L;
   }

   /**
    * Get the number of history message references we contain. 
    */
   public long getNumOfHistoryEntries() {
      if (this.historyQueue == null) {
         return 0L;
      }
      long num = this.historyQueue.getNumOfEntries();
      if (num > 0L && !this.dyingInProgress && !isAlive() && !isUnconfigured()) { // assert
         // isUnconfigured is possible on administrative startup with persistent messages
         log.error(ME, "Internal problem: we have messages but are not alive: " + toXml());
         Thread.currentThread().dumpStack();
      }
      return num;
   }

   /**
    * Do we contain at least one message?
    */
   public boolean hasCacheEntries() {
      return getNumOfCacheEntries() > 0L;
   }

   /**
    * The number of entries in the msgCache. 
    */
   public long getNumOfCacheEntries() {
      if (this.msgUnitCache == null) { // on startup
         return 0L;
      }
      return this.msgUnitCache.getNumOfEntries();
   }

   /**
    * Returns a snapshot of all entries in the history
    * @param num Number of entries wanted, not more than size of history queue are returned.<br />
    *            If -1 all entries in history queue are returned
    * @param reverseOrdered false is the normal case (the latest message is returned first)
    * @return Checked MsgUnitWrapper entries (destroyed and expired ones are removed), never null
    */
   public MsgUnitWrapper[] getMsgUnitWrapperArr(int num, boolean reverseOrdered) throws XmlBlasterException {
      if (this.historyQueue == null)
         return new MsgUnitWrapper[0];
      ArrayList historyList = this.historyQueue.peek(num, -1);
      ArrayList aliveMsgUnitWrapperList = new ArrayList();
      ArrayList historyDestroyList = null;
      int n = historyList.size();
      for(int i=0; i<n; i++) {
         MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)historyList.get(i);
         if (entry != null) {
            MsgUnitWrapper wr = entry.getMsgUnitWrapper();
            if (wr != null) {
               if (wr.isAlive()) {
                  aliveMsgUnitWrapperList.add(wr);
               }
               else {
                  if (historyDestroyList == null) historyDestroyList = new ArrayList();
                  historyDestroyList.add(entry);
               }
            }
         }
      }

      if (historyDestroyList != null && historyDestroyList.size() > 0) {
         this.historyQueue.removeRandom((I_Entry[])historyDestroyList.toArray(new I_Entry[historyDestroyList.size()]));
      }

      if (reverseOrdered) {
         MsgUnitWrapper[] arr = new MsgUnitWrapper[aliveMsgUnitWrapperList.size()];
         int size = aliveMsgUnitWrapperList.size();
         for(int i=0; i<size; i++)
            arr[i] = (MsgUnitWrapper)aliveMsgUnitWrapperList.get(size-i-1);
         return arr;
      }
      else {
         return (MsgUnitWrapper[])aliveMsgUnitWrapperList.toArray(new MsgUnitWrapper[aliveMsgUnitWrapperList.size()]);
      }
   }

   /**
    * Returns a snapshot of all entries in the history
    * @param num Number of entries wanted, not more than size of history queue are returned.<br />
    *            If -1 all entries in history queue are returned
    * @param reverseOrdered false is the normal case (the latest message is returned first)
    * @return Checked entries (destroyed and expired ones are removed), never null
    */
   public MsgUnit[] getHistoryMsgUnitArr(int num, boolean reverseOrdered) throws XmlBlasterException {
      MsgUnitWrapper[] msgUnitWrapper = getMsgUnitWrapperArr(num, reverseOrdered);
      MsgUnit[] msgUnitArr = new MsgUnit[msgUnitWrapper.length];
      for (int i=0; i<msgUnitWrapper.length; i++) {
         msgUnitArr[i] = msgUnitWrapper[i].getMsgUnit();
      }
      return msgUnitArr;
   }

   /**
    * The initial state at creation
    */
   public boolean isUndef() {
      return this.state == UNDEF;
   }

   /**
    * This state is reached if the TopicHandler is initially
    * created by a subscribe() and is not yet configured by an initial publish()
    */
   public boolean isUnconfigured() {
      return this.state == UNCONFIGURED;
   }

   /**
    * This state is defined if we are still referenced by
    * subscriptions or if we contain some messages
    */
   public boolean isAlive() {
      return this.state == ALIVE;
   }

   /**
    * This is a cleanup state (it is recoverable)
    * This state is reached when we are configured but not
    * referenced by any subscribes and without any messages.
    * We are still in registered in the BigDom tree for XPath queries etc.
    */
   public boolean isUnreferenced() {
      return this.state == UNREFERENCED;
   }

   /**
    * This state is reached on an erase(forceDestroy==false) invocation if
    * there are still message referenced from the callback queue.
    */
   public boolean isSoftErased() {
      return this.state == SOFT_ERASED;
   }

   /**
    * true if the instance is ready for garbage collection
    */
   public boolean isDead() {
      return this.state == DEAD;
   }

   private void toUnconfigured() {
      if (log.CALL) log.call(ME, "Entering toUnconfigured(oldState="+getStateStr()+")");
      synchronized (this) {
         if (isUnconfigured()) {
            return;
         }
         this.state = UNCONFIGURED;
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
      }
   }

   private void toAlive() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering toAlive(oldState="+getStateStr()+")");
      synchronized (this) {
         if (isAlive()) {
            return;
         }
         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
         if (!isRegisteredInBigXmlDom) {
            try {
               addToBigDom();
            } catch (XmlBlasterException e) {
               if (isUnreferenced())
                  toDead(this.creatorSessionName, false);
               else if (isUnconfigured())
                  ; // ignore
               throw e;
            }
         }

         persistTopicEntry();

         this.state = ALIVE;
      }
   }

   private boolean removeTopicPersistence() {
      try {
         if (this.topicEntry != null) {
            int num = this.requestBroker.removePersistentTopicHandler(this.topicEntry);
            this.topicEntry = null;
            if (num == 0) {
               log.warn(ME, "" + num + " TopicHandler removed from persistency");
            }
            else {
               if (log.TRACE) log.trace(ME, "" + num + " TopicHandler removed from persistency");
            }
            return num>0;
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Persisting TopicHandler failed, we continue memory based: " + e.getMessage());
      }
      return false;
   }

   private void toUnreferenced() throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering toUnreferenced(oldState="+getStateStr()+")");
      synchronized (this) {
         if (isUnreferenced() || isDead()) {
            return;
         }
         int oldState = this.state;

         if (hasHistoryEntries()) {
            if (log.TRACE) log.trace(ME, getStateStr() + "->" + "UNREFERENCED: Clearing " + getNumOfHistoryEntries() + " history entries");
            this.historyQueue.clear();
         }
         if (hasCacheEntries()) {
            if (log.TRACE) log.trace(ME, getStateStr() + "->" + "UNREFERENCED: Clearing " + this.msgUnitCache.getNumOfEntries() + " msgUnitStore cache entries");
            this.msgUnitCache.clear();  // Who removes the MsgUnitWrapper entries from their Timer?!!!! TODO
         }

         this.state = UNREFERENCED;

         if (this.topicProperty == null) {
            toDead(this.creatorSessionName, true);
            return; // ALIVE -> UNREFERENCED
         }

         if (topicProperty.getDestroyDelay() > 0L) {
            if (this.timerKey == null) {
               this.timerKey = this.destroyTimer.addTimeoutListener(this, topicProperty.getDestroyDelay(), null);
            }
         }
         else if (topicProperty.getDestroyDelay() == 0L) {
            timeout(null); // toDead()
            return; // destroy immediately
         }
         // for destroyDelay < 0 we live forever or until an erase arrives

         if (!isRegisteredInBigXmlDom) {
            addToBigDom(); // guarantee still XPATH visibility
         }

         // On administrative startup
         if (oldState == UNDEF || oldState == UNCONFIGURED) {
            persistTopicEntry();
         }
      }
   }

   /**
    * @param sessionInfo The session which triggered the erase
    */
   private void toSoftErased(SessionInfo sessionInfo) {
      if (log.CALL) log.call(ME, "Entering toSoftErased(oldState="+getStateStr()+")");
      synchronized (this) {
         if (isSoftErased()) {
            return;
         }
         if (hasHistoryEntries()) {
            if (log.TRACE) log.trace(ME, getStateStr() + "->" + "SOFTERASED: Clearing " + getNumOfHistoryEntries() + " history entries");
            this.historyQueue.clear();
         }

         if (hasSubscribers()) {
            if (log.TRACE) log.trace(ME, getStateStr() + "->" + "SOFTERASED: Clearing " + numSubscribers() + " subscriber entries");
            notifySubscribersAboutErase(sessionInfo.getSessionName());
            clearSubscribers();
         }

         this.state = SOFT_ERASED;
         removeFromBigDom();
      }
   }

   /**
    * @param sessionName The session which triggered this event
    */
   private void toDead(SessionName sessionName, boolean forceDestroy) {
      if (log.CALL) log.call(ME, "Entering toDead(oldState="+getStateStr()+")");
      long numHistory = 0L;
      synchronized (this) {
         if (this.dyingInProgress || isDead()) {
            return;
         }
         this.dyingInProgress = true;

         try {
            if (this.topicEntry != null) {
               removeTopicPersistence();
            }
            else {
               if (!isUnconfigured()) {
                  log.error(ME, "In " + getStateStr() + " -> DEAD: this.topicEntry == null");
                  Thread.currentThread().dumpStack();
               }
            }

            if (isAlive()) {
               if (numSubscribers() > 0 || hasCacheEntries() || hasHistoryEntries())
                  log.info(ME, "Forced state transition ALIVE -> DEAD with " + numSubscribers() + " subscribers, " +
                            getNumOfCacheEntries() + " cache messages and " +
                            getNumOfHistoryEntries() + " history messages.");
            }

            if (!forceDestroy && !isSoftErased()) {
               notifySubscribersAboutErase(sessionName);
            }

            if (hasHistoryEntries()) {
               try {
                  numHistory = this.historyQueue.clear();
                  if (log.TRACE) log.trace(ME, getStateStr() + "->" + "DEAD: Cleared " + numHistory + " history entries");
               }
               catch (Throwable e) {
                  log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the history queue: " + e.getMessage());
               }
            }
            if (this.historyQueue != null) {
               this.historyQueue.shutdown();
            }

            if (hasCacheEntries()) {
               try {
                  long num = this.msgUnitCache.clear();
                  if (log.TRACE) log.trace(ME, getStateStr() + "->" + "DEAD: Cleared " + num + " message storage entries");
               }
               catch (XmlBlasterException e) {
                  log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the message store: " + e.getMessage());
               }
            }
            if (this.msgUnitCache != null) {
               this.msgUnitCache.shutdown();
            }

            if (this.topicEntry != null) { // a second time if the above notifySubscribersAboutErase() made an unconfigured topic alive
               removeTopicPersistence();
            }
         }
         catch (Throwable e) {
            log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the message store: " + e.getMessage());
         }
         finally {
            this.state = DEAD;
            this.dyingInProgress = false; // no need for this anymore, reset it
         }


         try {
            removeFromBigSubscriptionSet();
         }
         catch (Throwable e) {
            log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the subscriptions: " + e.getMessage());
         }
         
         try {
            removeFromBigDom();
         }
         catch (Throwable e) {
            log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the big DOM: " + e.getMessage());
         }
         
         try {
            clearSubscribers(); // see notifySubscribersAboutErase() above
         }
         catch (Throwable e) {
            log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the subscribers: " + e.getMessage());
         }

         try {
            removeFromBigMessageMap();
         }
         catch (Throwable e) {
            log.error(ME, getStateStr() + "->" + "DEAD: Ignoring problems during clearing the big message map: " + e.getMessage());
         }

         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }
      }
      log.info(ME, "Topic reached state " + getStateStr() + ". " + numHistory + " history entries are destroyed.");
   }

   /**
    * Merge the message DOM tree into the big xmlBlaster DOM tree
    */
   private void addToBigDom() throws XmlBlasterException {
      if (isRegisteredInBigXmlDom) {
         return;
      }
      if (!this.topicProperty.createDomEntry()) {
         return;
      }
      getXmlKey().mergeRootNode(requestBroker.getBigXmlKeyDOM());
      isRegisteredInBigXmlDom = true;
   }

   /**
    * Remove Node in big xml dom
    */
   private void removeFromBigDom() {
      if (!isRegisteredInBigXmlDom) {
         return;
      }
      //if (!this.topicProperty.createDomEntry()) {
      //   return;
      //}
      try {
         requestBroker.getBigXmlKeyDOM().messageErase(this);
         isRegisteredInBigXmlDom = false;
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on BigDom erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Send erase event
    * @param sessionName The session which triggered the erase
    */
   private void notifySubscribersAboutErase(SessionName sessionName) {
      if (log.CALL) log.call(ME, "Sending client notification about message erase() event");

      if (hasSubscribers()) {
         try {
            SessionInfo publisherSessionInfo = glob.getRequestBroker().getInternalSessionInfo();
            org.xmlBlaster.client.key.PublishKey pk = new org.xmlBlaster.client.key.PublishKey(glob,
                                                      getUniqueKey(), "text/plain", "1.0");
            org.xmlBlaster.client.qos.PublishQos pq = new org.xmlBlaster.client.qos.PublishQos(glob);
            pq.setState(Constants.STATE_ERASED);
            pq.setVolatile(true);
            pq.setSender(sessionName);
            MsgUnit msgUnit = new MsgUnit(glob, pk, getId(), pq); // content contains the global name?
            PublishQosServer ps = new PublishQosServer(glob, pq.getData());
            //log.error(ME, "DEBUG ONLY" + msgUnit.toXml());
            publish(publisherSessionInfo, msgUnit, ps);
         }
         catch (XmlBlasterException e) {
            // The access plugin or client may throw an exception. The behavior is not coded yet
            log.error(ME, "Received exception for message erase event (callback to client), we ignore it: " + e.getMessage());
         }
      }
   }

   /**
    * Remove myself from big subscription set
    */
   private void removeFromBigSubscriptionSet() {
      try {
         requestBroker.getClientSubscriptions().messageErase(this);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Remove from big message map in RequestBroker
    */
   private void removeFromBigMessageMap() {
      try {
         requestBroker.messageErase(this);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Notify all Listeners that a message is erased.
    *
    * @param sessionInfo
    * @param topicHandler
    */
   final void fireMessageEraseEvent(SessionInfo sessionInfo, EraseQosServer eraseQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering fireMessageEraseEvent forceDestroy=" + eraseQos.getForceDestroy());
      eraseQos = (eraseQos==null) ? new EraseQosServer(glob, new QueryQosData(glob)) : eraseQos;

      synchronized (this) {
         if (isAlive() || isUnconfigured()) {
            if (eraseQos.getForceDestroy()) {
               toDead(sessionInfo.getSessionName(), eraseQos.getForceDestroy());
               return;
            }
            else {
               toSoftErased(sessionInfo); // kills all history entries, notify subscribers
               long numMsgUnitStore = this.msgUnitCache.getNumOfEntries();
               if (numMsgUnitStore < 1) { // has no callback references?
                  toDead(sessionInfo.getSessionName(), eraseQos.getForceDestroy());
                  return;
               }
               else {
                  log.info(ME, "Erase not possible, we are still referenced by " + numMsgUnitStore +
                  " callback queue entries, transition to topic state " + getStateStr());
               }
            }
         }

         if (isUnreferenced()) {
            toDead(sessionInfo.getSessionName(), eraseQos.getForceDestroy());
            return;
         }
         /*
         if (isUnconfigured()) {
            toDead(sessionInfo.getSessionName(), eraseQos.getForceDestroy());
            return;
         }
         */
      }

      // else ignore
   }


   /**
    * This timeout occurs after a configured delay (destroyDelay) in UNREFERENCED state
    */
   public final void timeout(Object userData) {
      if (log.CALL) log.call(ME, "Timeout after destroy delay occurred - destroying topic now ...");
      this.timerKey = null;
      if (isDead())
         return;
      synchronized (this) {
         if (isAlive()) // interim message arrived?
            return;
         toDead(this.creatorSessionName, false);
      }
   }

   public String getStateStr() {
      if (isAlive()) {
         return "ALIVE";
      }
      else if (isUnconfigured()) {
         return "UNCONFIGURED";
      }
      else if (isUnreferenced()) {
         return "UNREFERENCED";
      }
      else if (isDead()) {
         return "DEAD";
      }
      else if (isSoftErased()) {
         return "SOFTERASED";
      }
      else if (state == UNDEF) {
         return "UNDEF";
      }
      else {
         log.error(ME, "PANIC: Unknown internal state=" + state);
         return "ERROR";
      }
   }

   /*
   public void shutdown() {
      requestBroker.removeTopicHandler(this);
   }
   */

   /**
    * This class determines the sorting order, by which the
    * client receive their updates.
    * For now, the client which subscribed first, is served first
    */
   /*
   class subscriberSorter implements Comparator
   {
      public int compare(Object o1, Object o2)
      {
         SubscriptionInfo s1 = (SubscriptionInfo)o1;
         SubscriptionInfo s2 = (SubscriptionInfo)o2;
         return o2.getCreationTime() - o1.getCreationTime;
      }
      public boolean equals(Object obj)
      {
         //SubscriptionInfo sub = (SubscriptionInfo)obj;
         this.equals(obj);
      }
   }
   */

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of TopicHandler
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of TopicHandler
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(4000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<TopicHandler id='").append(getId()).append("' state='").append(getStateStr()).append("'>");
      sb.append(offset).append(" <uniqueKey>").append(getUniqueKey()).append("</uniqueKey>");
 
      if (this.topicEntry != null) {
         sb.append(offset).append(" <topicEntry>").append(this.topicEntry.getLogId()).append("</topicEntry>");
      }
   
      if (this.topicProperty != null)
         sb.append(this.topicProperty.toXml(extraOffset+Constants.INDENT));
   
      if (this.msgUnitCache != null) {
         sb.append(this.msgUnitCache.toXml(extraOffset+Constants.INDENT));
      }
      if (this.historyQueue != null) {
         sb.append(this.historyQueue.toXml(extraOffset+Constants.INDENT));
      }

      try {
         MsgUnitWrapper[] msgUnitWrapperArr = getMsgUnitWrapperArr(-1, false);
         for (int ii=0; ii<msgUnitWrapperArr.length; ii++) {
            MsgUnitWrapper msgUnitWrapper = msgUnitWrapperArr[ii];
            if (msgUnitWrapper != null)
               sb.append(msgUnitWrapper.toXml(extraOffset + Constants.INDENT));
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
      }

      if (hasSubscribers()) {
         SubscriptionInfo[] subscriptionInfoArr = getSubscriptionInfoArr();
         for(int i=0; i<subscriptionInfoArr.length; i++) {
            try {
               sb.append(offset).append(" <SubscriptionInfo id='").append(subscriptionInfoArr[i].getSubscriptionId()).append("'/>");
            }
            catch (XmlBlasterException e) {
               log.error(ME, e.getMessage());
            }
            //sb.append(subscriptionInfoArr[i].toXml(extraOffset + "   "));
         }
      }
      else {
         sb.append(offset + " <SubscriptionInfo>NO SUBSCRIPTIONS</SubscriptionInfo>");
      }

      sb.append(offset).append(" <newCreated>").append(this.handlerIsNewCreated).append("</newCreated>");
      sb.append(offset).append("</TopicHandler>\n");
      return sb.toString();
   }
}
