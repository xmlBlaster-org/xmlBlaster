/*------------------------------------------------------------------------------
Name:      TopicHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.Map;
import java.util.TreeMap;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Iterator;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.query.plugins.QueueQueryPlugin;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.TopicEntry;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
import org.xmlBlaster.engine.distributor.I_MsgDistributor;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;

import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;

import org.xmlBlaster.client.qos.PublishReturnQos;

import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;


/**
 * A topic handles all MsgUnit entries of same oid and its subscribers. 
 * <p>
 * This handler has the state UNCONFIGURED | UNREFERENCED | ALIVE | DEAD, see
 * the boolean state access methods for a description
 * </p>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">The engine.message.lifecylce requirement</a>
 * @see org.xmlBlaster.test.topic.TestTopicLifeCycle
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class TopicHandler implements I_Timeout, TopicHandlerMBean //, I_ChangeCallback
{
   private String ME = "TopicHandler";
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(TopicHandler.class.getName());
   private final ContextNode contextNode;

   private boolean dyingInProgress = false;

   /** The unique identifier of this topic e.g. "/node/heron/topic/Hello" */
   private final String id;

   /** The broker which manages me */
   private final RequestBroker requestBroker;

   private TopicEntry topicEntry; // persistence storage entry

   // Default is that a single client can subscribe the same message multiple times
   // private boolean allowMultiSubscriptionPerClient = glob.getProperty().get("Engine.allowMultiSubscriptionPerClient", true);

   private I_MsgDistributor distributor;

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

   private int publishCounter = 0; //count the threads running in publish method
   
   /**
    * This topic is destroyed after given timeout
    * The timer is activated on state change to UNREFERENCED
    * and removed on change to ALIVE
    */
   private Timeout destroyTimer;
   private Timestamp timerKey = null;

   public final static int UNDEF = -1;
   public final static int UNCONFIGURED = 0;
   public final static int ALIVE = 1;
   public final static int UNREFERENCED = 2;
   public final static int SOFT_ERASED = 3;
   public final static int DEAD = 4;
   private int state = UNDEF;

   private final Object ADMIN_MONITOR = new Object();

   private I_SubscriptionListener subscriptionListener;

   private Map msgUnitWrapperUnderConstruction = new HashMap();
   
   /** this is used for administrative gets (queries on callback queue) */
   private QueueQueryPlugin queueQueryPlugin;

   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;
   
   private boolean administrativeInitialize;
   
   protected Object clone() {
      throw new RuntimeException("TopicHandler NO CLONEING PLEASE");
   }

   /**
    * Use this constructor if a subscription is made on a yet unknown topic.
    * <p />
    * @param requestBroker
    * @param uniqueKey The unique XmlKey-oid from the subscribe() call
    */
   public TopicHandler(RequestBroker requestBroker, String uniqueKey) throws XmlBlasterException {
      this(requestBroker, null, uniqueKey);
   }

   /**
    * Use this constructor if a yet unknown object is fed by method publish().
    * <p />
    * You should call publish() thereafter
    * @param requestBroker
    * @param publisherSessionInfo Is null if called from other constructor (subscription based)
    * @param a MsgUnitWrapper containing the CORBA MsgUnit data container
    */
   public TopicHandler(RequestBroker requestBroker, SessionInfo publisherSessionInfo, String uniqueKey) throws XmlBlasterException {
      this.glob = requestBroker.getGlobal();
      if (uniqueKey == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "Invalid constructor parameters");

      this.uniqueKey = uniqueKey;

      this.id = this.glob.getNodeId() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + this.uniqueKey;
      this.ME += this.glob.getLogPrefixDashed() + "/" + ContextNode.TOPIC_MARKER_TAG + "/" + this.uniqueKey;

      // JMX does not allow commas ','
      String instanceName = this.glob.validateJmxValue(this.uniqueKey);
      this.contextNode = new ContextNode(ContextNode.TOPIC_MARKER_TAG, instanceName, this.glob.getContextNode());

      this.requestBroker = requestBroker;
      this.destroyTimer = requestBroker.getGlobal().getTopicTimer();
      // this.msgErrorHandler = new MsgTopicErrorHandler(this.glob, this);

      toUnconfigured();
      TopicHandler t = this.requestBroker.addTopicHandler(this);
      if (t != null) {
         log.severe("Unexpected duplicated of TopicHandler in RequestBroker");
         Thread.dumpStack();
      }
      
      // JMX register "topic/hello"
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

      if (publisherSessionInfo == null) {
         if (log.isLoggable(Level.FINER)) log.fine("Creating new TopicHandler '" + uniqueKey + "' because of subscription.");
      }
      else {
         if (log.isLoggable(Level.FINER)) log.fine("Creating new TopicHandler '" + uniqueKey + "' because of publish.");
      }
      // mimeType and content remains unknown until first data is fed
   }

   /**
    * The unique identifier of this topic e.g. "/node/heron/topic/Hello"
    */
   public String getId() {
      return this.id;
   }

   /**
    * The unique name of this topic instance. 
    * @return Never null, for example "/xmlBlaster/node/heron/topic/hello"
    */
   public final ContextNode getContextNode() {
      return this.contextNode;
   }

   /**
    * Initialize the messageUnit cache and the history queue for this topic
    */
   private synchronized void administrativeInitialize(MsgKeyData msgKeyData, MsgQosData publishQos,
                             PublishQosServer publishQosServer) throws XmlBlasterException {
      if (publishQosServer.getTopicEntry() != null) {
         this.topicEntry = publishQosServer.getTopicEntry(); // Call from persistent layer, reuse the TopicEntry
         if (log.isLoggable(Level.FINE)) log.fine("Reuse TopicEntry persistence handle");
         if (log.isLoggable(Level.FINEST)) log.finest("Reuse TopicEntry persistence handle: " + this.topicEntry.toXml());
      }

      if (this.msgKeyData == null) {
         this.msgKeyData = msgKeyData;
      }

      if (log.isLoggable(Level.FINEST)) log.finest("administrativeInitialize()" + publishQos.toXml());

      this.creatorSessionName = publishQos.getSender();
      this.topicProperty = publishQos.getTopicProperty();

      startupMsgstore();

      // Todo: this needs to be done after TopicHandler is created
      startupHistoryQueue();

      if (isUnconfigured()) { // Startup of topic
         if (!hasCacheEntries() && !hasExactSubscribers()) {
            toUnreferenced(true);
         }
         else {
            toAlive();
         }
      }

      if (true /*log.INFO*/) {
         long maxEntriesHistory = this.topicProperty.getHistoryQueueProperty().getMaxEntries();
         String hist = (maxEntriesHistory > 0) ? "history/maxEntries="+maxEntriesHistory : "message history is switched off with queue/history/maxEntries=0";
         long maxEntriesStore = this.topicProperty.getMsgUnitStoreProperty().getMaxEntries();
         String store = (maxEntriesStore > 0) ? "persistence/msgUnitStore/maxEntries="+maxEntriesStore : "message storage is switched off with persistence/msgUnitStore/maxEntries=0";
         log.info("New topic '" + this.msgKeyData.getOid() + "' is ready, " + hist + ", " + store);
      }
      
      this.administrativeInitialize = true;
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
            log.info("Reconfiguring message store.");
            this.msgUnitCache.setProperties(msgUnitStoreProperty);
         }
      }
   }

   /**
    * Should be invoked delayed as soon as TopicHandler instance is created an registered everywhere
    * as we ask the msgUnitStore for the real messages if some history entries existed. 
    * <p>
    * NOTE: queue can be null if maxEntries=0 is configured
    * </p>
    * <p>
    * This history queue entries hold weak references to the msgUnitCache entries
    * </p>
    */
   private void startupHistoryQueue() throws XmlBlasterException {
      this.historyQueue = initQueue(this.historyQueue, "history");
   }

   /**
    * Creates a queue with the properties specified in the historyQueueProperty
    * @param queue the queue instance (if already existing or null otherwise)
    * @param queueName The name to be given as Id to this queue
    * @return returns the instance of the queue
    * @throws XmlBlasterException
    */
   private I_Queue initQueue(I_Queue queue, String queueName) throws XmlBlasterException {
      synchronized (this) {
         QueuePropertyBase prop = this.topicProperty.getHistoryQueueProperty();
         if (queue == null) {
            if (prop.getMaxEntries() > 0L) {
               String type = prop.getType();
               String version = prop.getVersion();
               StorageId queueId = new StorageId(queueName, glob.getNodeId()+"/"+getUniqueKey());
               queue = glob.getQueuePluginManager().getPlugin(type, version, queueId, prop);
               queue.setNotifiedAboutAddOrRemove(true); // Entries are notified to support reference counting
            }
            else {
               if (log.isLoggable(Level.FINE)) log.fine(queueName + " queuing of this topic is switched off with maxEntries=0");
            }
         }
         else {
            if (prop.getMaxEntries() > 0L) {
               log.info("Reconfiguring " + queueName + " queue.");
               queue.setProperties(prop);
            }
            else {
               log.warning("Destroying " + queueName + " queue with " + queue.getNumOfEntries() +
                            " entries because of new configuration with maxEntries=0");
               queue.clear();
               queue.shutdown();
               queue = null;
            }
         }
      }
      return queue;
   }

   // JMX does not allow hasXY
   public boolean getDomTreeExists() {
      return hasDomTree();
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
      if (log.isLoggable(Level.FINE)) log.fine("finalize - garbage collect " + getId());
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

   // JMX
   public final boolean getTopicXmlExists() {
      return hasXmlKey();
   }

   // JMX
   public final String getTopicXml() throws org.xmlBlaster.util.XmlBlasterException {
      return getXmlKey().literal();
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
               if (log.isLoggable(Level.FINE)) log.fine("Creating TopicEntry to make topic persistent");
               if (this.topicProperty==null || this.msgKeyData==null) {
                  log.severe("Can't create useful TopicEntry in state=" + getStateStr() + " no QoS is available");
                  return null;
               }
               MsgQosData msgQosData = new MsgQosData(glob, MethodName.PUBLISH);
               msgQosData.setTopicProperty(this.topicProperty);
               msgQosData.setAdministrative(true);
               msgQosData.touchRcvTimestamp();
               msgQosData.setPersistent(true);
               msgQosData.setSender(creatorSessionName);
               MsgUnit msgUnit = new MsgUnit(this.msgKeyData, null, msgQosData);
               this.topicEntry = new TopicEntry(glob, msgUnit);
               isNew = true;
               if (log.isLoggable(Level.FINE)) log.fine("Created persistent topicEntry '" + this.topicEntry.getUniqueId() + "'"); //: " + this.topicEntry.toXml());
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
         if (log.isLoggable(Level.FINE)) log.fine("Making topicHandler persistent, topicEntry=" + topicEntry.getUniqueId());
         int numAdded = this.requestBroker.addPersistentTopicHandler(entry);
         if (log.isLoggable(Level.FINE)) log.fine("Persisted " + numAdded + " TopicHandler");
         return numAdded>0;
      }
      catch (XmlBlasterException e) {
         log.severe("Persisting TopicHandler failed, we continue memory based: " + e.getMessage());
      }
      return false;
   }

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
      if (log.isLoggable(Level.FINE)) log.fine("publish() publisherSessionInfo '" + publisherSessionInfo.getId() + "', message '" + msgUnit.getLogId() + "' ...");

      PublishReturnQos publishReturnQos = null;
      MsgQosData msgQosData = null;

      synchronized (this) {
         StatusQosData qos = new StatusQosData(glob, MethodName.PUBLISH);
         qos.setKeyOid(this.uniqueKey);
         qos.setState(Constants.STATE_OK);
         qos.setRcvTimestamp(publishQosServer.getRcvTimestamp());
         publishReturnQos = new PublishReturnQos(glob, qos);

         MsgKeyData msgKeyData = (MsgKeyData)msgUnit.getKeyData();
         msgQosData = (MsgQosData)msgUnit.getQosData();
         /* Happens in RequestBroker already
         if (msgQosData.getSender() == null) {
            msgQosData.setSender(publisherSessionInfo.getSessionName());
         }
         */

         //if (this.msgKeyData == null) { // If TopicHandler existed because of a subscription: remember on first publish
         //   this.msgKeyData = msgKeyData;
         //}

         if (msgQosData.isAdministrative()) {
            synchronized (this.ADMIN_MONITOR) {
               if (!isUnconfigured() && !isSoftErased())
                  log.severe("Sorry we are in state '" + getStateStr() + "', reconfiguring TopicHandler is not yet supported, we ignore the request");
               else
                  administrativeInitialize(msgKeyData, msgQosData, publishQosServer);
               if (this.handlerIsNewCreated) {
                  this.handlerIsNewCreated = false;
                  // Check all known query subscriptions if the new message fits as well (does it only if TopicHandler is new)
                  glob.getRequestBroker().checkExistingSubscriptions(publisherSessionInfo, this, publishQosServer);
               }
               if (msgQosData.isFromPersistenceStore()) {
                  log.info("Topic is successfully recovered from persistency to state " + getStateStr() +
                           //((requestBroker.getTopicStore()!=null) ? (" '" + requestBroker.getTopicStore().getStorageId() + "'") : "") +
                           " with " + getNumOfHistoryEntries() + " history entries (" + getNumOfCacheEntries() + " currently referenced msgUnits are loaded).");
               }
               else {
                  log.info("Topic is successfully configured by administrative message.");
               }
               publishReturnQos.getData().setStateInfo("Administrative configuration request handled");
               return publishReturnQos;
            } // synchronized
         }

         if (!this.administrativeInitialize) {
            administrativeInitialize(msgKeyData, msgQosData, publishQosServer);
         }

         if (!isAlive()) {
            toAlive();
         }
      } // sync

      if (this.handlerIsNewCreated) {
         synchronized (this.ADMIN_MONITOR) {
            if (this.handlerIsNewCreated) {
               // Check all known query subscriptions if the new message fits as well (does it only if TopicHandler is new)
               glob.getRequestBroker().checkExistingSubscriptions(publisherSessionInfo, this, publishQosServer);
               this.handlerIsNewCreated = false;
            }
         }
      }

      int initialCounter = 1; // Force referenceCount until update queues are filled (volatile messages)
      MsgUnitWrapper msgUnitWrapper = null;
      
      try { // finally
         boolean changed = true;
         synchronized (this) {

            final boolean isInvisiblePtp = publishQosServer.isPtp() && !publishQosServer.isSubscribable();
            final boolean addToHistoryQueue = this.historyQueue != null && !isInvisiblePtp;

            if (!isInvisiblePtp) {  // readonly is only checked for Pub/Sub?
               if (this.topicProperty.isReadonly() && hasHistoryEntries()) {
                  log.warning("Sorry, published message '" + msgKeyData.getOid() + "' rejected, message is readonly.");
                  throw new XmlBlasterException(glob, ErrorCode.USER_PUBLISH_READONLY, ME, "Sorry, published message '" + msgKeyData.getOid() + "' rejected, message is readonly.");
               }
            }

            msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, this.msgUnitCache, initialCounter, 0, -1);
            
            publishCounter++;
            if (!isAlive()) {
                toAlive();
            }
            
            // Forcing RAM entry temporary (reset in finally below) to avoid performance critical harddisk IO during initialization, every callback/subject/history queue put()/take() is changing the reference counter of MsgUnitWrapper. For persistent messages this needs to be written to harddisk
            // If the server crashed during this RAM operation it is not critical as the publisher didn't get an ACK yet
            synchronized(this.msgUnitWrapperUnderConstruction) {
               // A queue (e.g. callback queue) could swap its entry and reload it during this initialization phase,
               // in this case we need to assure that it receives our RAM based MsgUnitWrapper (with all current settings)
               // in case it changes the referenceCounter
               this.msgUnitWrapperUnderConstruction.put(new Long(msgUnitWrapper.getUniqueId()), msgUnitWrapper);
            }
       
            if (addToHistoryQueue && msgUnitWrapper.hasRemainingLife()) { // no sense to remember
               if (msgQosData.isForceUpdate() == false && hasHistoryEntries()) {
                  MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)this.historyQueue.peek();
                  if (entry != null) {
                     MsgUnitWrapper old = entry.getMsgUnitWrapper();
                     if (old != null) {
                        changed = !old.getMsgUnit().sameContent(msgUnit.getContent());
                     }
                  }
               }

               try { // Cleanup if persistent queue was temporary unavailable
                  long numHist = getNumOfHistoryEntries();
                  if (numHist > 1L && numHist > this.historyQueue.getMaxNumOfEntries()) {
                     long count = numHist-this.historyQueue.getMaxNumOfEntries();
                     // TODO: Implement count>1 in takeLowest():
                     ArrayList entryList = this.historyQueue.takeLowest((int)count, -1L, null, false);
                     if (entryList.size() != count) {
                        log.severe("Can't remove expected entry, entryList.size()=" + entryList.size() + ": " + this.historyQueue.toXml(""));
                     }
                  }
               }
               catch (XmlBlasterException e) {
                  log.severe("History queue take() problem: " + e.getMessage());
               }

               try { // increments reference counter += 1
                  this.historyQueue.put(new MsgQueueHistoryEntry(glob, msgUnitWrapper, this.historyQueue.getStorageId()), I_Queue.USE_PUT_INTERCEPTOR);
               }
               catch (XmlBlasterException e) {
                  log.severe("History queue put() problem: " + e.getMessage());
               }

               try {
                  long numHist = getNumOfHistoryEntries();
                  if (numHist > 1L && numHist > this.historyQueue.getMaxNumOfEntries()) {
                     ArrayList entryList = this.historyQueue.takeLowest(1, -1L, null, false);
                     if (entryList.size() != 1) {
                        throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME,
                              "Can't remove expected entry, entryList.size()=" + entryList.size() + ": " + this.historyQueue.toXml(""));
                     }
                     MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)entryList.get(0);
                     if (log.isLoggable(Level.FINE)) { if (!entry.isInternal()) log.fine("Removed oldest entry in history queue."); }
                  }
               }
               catch (XmlBlasterException e) {
                  log.severe("History queue take() problem: " + e.getMessage());
               }
            }
         } // synchronized

         // NOTE: Putting entries into callback queues must be outside of a synchronized(topicHandler) to avoid deadlock
         //       The DispatchWorker removes a MsgUnitWrapper entry from the msgstore (see entryDestroyed()) and would deadlock

         //----- 2a. now we can send updates to all destination clients:
         if (publishQosServer.isPtp()) {
            /*publishReturnQos =*/ forwardToDestinations(publisherSessionInfo, msgUnitWrapper, publishQosServer);
            if (!publishQosServer.isSubscribable()) {
               publishReturnQos.getData().setStateInfo("PtP request handled");
               return publishReturnQos;
            }
         }

         //----- 2b. now we can send updates to all subscribed clients:
         if (log.isLoggable(Level.FINE)) log.fine("Message " + msgUnit.getLogId() + " handled, now we can send updates to all interested clients.");
         if (changed || msgQosData.isForceUpdate()) { // if the content changed of the publisher forces updates ...
            invokeCallbackAndHandleFailure(publisherSessionInfo, msgUnitWrapper);
         }
         msgUnitWrapper.startExpiryTimer();
      }
      catch (Throwable e) {
         log.severe(e.toString());
         e.printStackTrace();
         if (e instanceof XmlBlasterException)
            throw (XmlBlasterException)e;
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "TopicHandler", "", e);
      }
      finally {
         if (msgUnitWrapper != null) {
            synchronized(this) {
               publishCounter--;
               synchronized(msgUnitWrapper) {
                  synchronized(this.msgUnitCache) {
                     try {
                        // Event to check if counter == 0 to remove cache entry again (happens e.g. for volatile msg without a no subscription)
                        // MsgUnitWrapper calls topicEntry.destroyed(MsgUnitWrapper) if it is in destroyed state
                        if (initialCounter != 0) {
                           msgUnitWrapper.setReferenceCounter((-1)*initialCounter);
                        }
                        if (!msgUnitWrapper.isDestroyed()) {
                           this.msgUnitCache.put(msgUnitWrapper);
                        }
                     }
                     finally {
                        synchronized(this.msgUnitWrapperUnderConstruction) {
                           this.msgUnitWrapperUnderConstruction.remove(new Long(msgUnitWrapper.getUniqueId()));
                        }
                     }
                  }
               }
            }
         }
      }
      return publishReturnQos;
   }

   /**
    * Check if the MsgUnitWrapper is owned by the TopicHandler (during construction). 
    * NOTE: You need to synchronize this call over msgUnitCache
    */
   boolean isInMsgStore(MsgUnitWrapper msgUnitWrapper) {
      synchronized(this.msgUnitWrapperUnderConstruction) {
         return !this.msgUnitWrapperUnderConstruction.containsKey(new Long(msgUnitWrapper.getUniqueId()));
      }
   }

   /**
    * Implements I_ChangeCallback, invoked by this.msgUnitCache.change() above
   public I_MapEntry changeEntry(I_MapEntry entry) throws XmlBlasterException {
      MsgUnitWrapper msgUnitWrapper = (MsgUnitWrapper)entry;
      msgUnitWrapper.runningRamBased(false);
      return msgUnitWrapper;
   }
   */

   /**
    * Forward PtP messages. 
    * TODO: On exception continue to other destinations and return the
    *       successful/not-successful destinations in PublishReturnQos!!!
    */
   private void forwardToDestinations(SessionInfo publisherSessionInfo,
      MsgUnitWrapper cacheEntry, PublishQosServer publishQos)
      throws XmlBlasterException {
      // NOTE: cluster forwarded PtP destinations are removed already from this list:
      Destination[] destinationArr = publishQos.getDestinationArr(); // !!! add XPath client query here !!!
      Authenticate authenticate = this.requestBroker.getAuthenticate();

      //-----    Send message to every destination client
      for (int ii = 0; ii < destinationArr.length; ii++) {
         Destination destination = destinationArr[ii];

         if (log.isLoggable(Level.FINE)) log.fine("Working on PtP message for destination [" + destination.getDestination() + "]");

         SessionName destinationSessionName = destination.getDestination();
         boolean destinationIsSession = destinationSessionName.isSession();
         boolean forceQueing = destination.forceQueuing();
         boolean wantsPtP = true; // TODO if destination never has logged in spam would be possible!

         SubjectInfo destinationClient = null;

         // Handle PtP to subject in a thread safe manner
         if (!destinationIsSession) { // -> subject
            // 3 + 6 (force queing ignored since same reaction for both)
            destinationClient = authenticate.getSubjectInfoByName(destination.getDestination());
            if (!forceQueing && destinationClient==null) {
               String tmp = "Sending PtP message '" + cacheEntry.getLogId() + "' to '" + destination.getDestination() + "' failed, the destination is unkown, the message rejected.";
               log.warning(tmp);
               throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNDESTINATION, ME, tmp +
                   " Client is not logged in and <destination forceQueuing='true'> is not set");
            }
            if (log.isLoggable(Level.FINE)) log.fine("Queuing PtP message '" + cacheEntry.getLogId() + "' for subject destination [" + destination.getDestination() + "], forceQueing="+forceQueing);

            // We are responsible to call destinationClient.getLock().release()
            final boolean returnLocked = true;
            destinationClient = authenticate.getOrCreateSubjectInfoByName(destination.getDestination(), returnLocked, null, null);
            try {
               MsgQueueUpdateEntry msgEntrySubject = new MsgQueueUpdateEntry(glob, cacheEntry,
                        destinationClient.getSubjectQueue().getStorageId(), destination.getDestination(),
                        Constants.SUBSCRIPTIONID_PtP, false);
               destinationClient.queueMessage(msgEntrySubject);
               continue;
            }
            finally {
               destinationClient.getLock().release();
            }
         }

         // Handle PtP to session in a thread safe manner
         SessionInfo receiverSessionInfo = null;
         try {
            receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
            if (receiverSessionInfo != null) {
               receiverSessionInfo.getLock().lock();
               //receiverSessionInfo.waitUntilAlive();
               if (receiverSessionInfo.isAlive()) {
                  if (!receiverSessionInfo.getConnectQos().isPtpAllowed() &&
                      !Constants.EVENT_OID_ERASEDTOPIC.equals(cacheEntry.getKeyOid())) { // no spam, case 2
                     if (log.isLoggable(Level.FINE)) log.fine("Rejecting PtP message '" + cacheEntry.getLogId() + "' for destination [" + destination.getDestination() + "], isPtpAllowed=false");
                     throw new XmlBlasterException(glob, ErrorCode.USER_PTP_DENIED, ME,
                           receiverSessionInfo.getId() + " does not accept PtP messages '" + cacheEntry.getLogId() +
                           "' is rejected");
                  }
               }
               else {
                  receiverSessionInfo.getLock().release();
                  receiverSessionInfo = null;
               }
            }

            if (receiverSessionInfo == null && !forceQueing) {
               String tmp = "Sending PtP message '" + cacheEntry.getLogId() + "' to '" + destination.getDestination() + "' failed, the destination is unkown, the message rejected.";
               log.warning(tmp);
               throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNDESTINATION, ME, tmp +
                     " Client is not logged in and <destination forceQueuing='true'> is not set");
            }

            // Row 1 in table 
            if (receiverSessionInfo == null) { // We create a faked session without password check
               if (log.isLoggable(Level.FINE)) log.fine("Working on PtP message '" + cacheEntry.getLogId() + "' for destination [" + destination.getDestination() + "] which does not exist, forceQueuing=true, we create a dummy session");
               ConnectQos connectQos = new ConnectQos(glob);
               connectQos.setSessionName(destinationSessionName);
               connectQos.setUserId(destinationSessionName.getLoginName());
               ConnectQosServer connectQosServer = new ConnectQosServer(glob, connectQos.getData());
               connectQosServer.bypassCredentialCheck(true);
               long sessionTimeout = glob.getProperty().get("session.ptp.defaultTimeout", -1L);
               connectQosServer.getSessionQos().setSessionTimeout(sessionTimeout);  // Or use message timeout?
               for (int i=0; ; i++) {
                  if (i>=20) {
                     String tmp = "Sending PtP message '" + cacheEntry.getLogId() + "' to '" + destination.getDestination() + "' failed, the message is rejected.";
                     String status = "destinationIsSession='" + destinationIsSession + "'" +
                                     " forceQueing='" + forceQueing + "' wantsPtP='" + wantsPtP +"'";  
                     throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, tmp +
                        "the combination '" + status + "' is not handled");
                  }
                  if (i>0) { try { Thread.sleep(1L); } catch( InterruptedException ie) {}}
                  /*ConnectReturnQosServer q = */authenticate.connect(connectQosServer);
                  receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
                  if (receiverSessionInfo == null) continue;
                  receiverSessionInfo.getLock().lock();
                  if (!receiverSessionInfo.isAlive()) {
                     receiverSessionInfo.getLock().release();
                     receiverSessionInfo = null;
                     continue;
                  }
                  break;
               }
            } 

            if (log.isLoggable(Level.FINE)) log.fine("Queuing PtP message '" + cacheEntry.getLogId() + "' for destination [" + destination.getDestination() + "]");
            MsgQueueUpdateEntry msgEntry = new MsgQueueUpdateEntry(glob,
                     cacheEntry,
                     receiverSessionInfo.getSessionQueue().getStorageId(),
                     destination.getDestination(),
                     Constants.SUBSCRIPTIONID_PtP, false);
            receiverSessionInfo.queueMessage(msgEntry);
            continue;
         }
         finally {
            if (receiverSessionInfo != null)
               receiverSessionInfo.getLock().release();
         }
      } // for destinationArr.length
   }
   
   /**
    * @return The storage containing the 'meat' of a message
    */
   public I_Map getMsgUnitCache() {
      return this.msgUnitCache;
   }

   public MsgUnitWrapper getMsgUnitWrapper(long uniqueId) throws XmlBlasterException {

      synchronized(this.msgUnitWrapperUnderConstruction) {
         if (this.msgUnitWrapperUnderConstruction.size() > 0) {
            Object obj = this.msgUnitWrapperUnderConstruction.get(new Long(uniqueId));
            if (obj != null) {
               return (MsgUnitWrapper)obj;
            }
         }
      }

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
      synchronized (this) {
         int numHistory = msgUnitWrapper.getHistoryReferenceCounter();
         if (numHistory > 0) {
            // We need to remove it from the history queue or at least decrement the referenceCounter
            // in which case we have a stale reference in the history queue (which should be OK, it is
            // removed as soon as it is taken out of it)
            boolean preDestroyed = msgUnitWrapper.incrementReferenceCounter((-1)*numHistory, this.historyQueue.getStorageId());
            if (preDestroyed) msgUnitWrapper.toDestroyed();
            
         }
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
      if (log.isLoggable(Level.FINER)) log.finer("Entering entryDestroyed(" + msgUnitWrapper.getLogId() + ")");

      boolean underConstruction = false;
      synchronized(this.msgUnitWrapperUnderConstruction) {
         if (this.msgUnitWrapperUnderConstruction.size() > 0) {
            Object obj = this.msgUnitWrapperUnderConstruction.get(new Long(msgUnitWrapper.getUniqueId()));
            if (obj != null) {
               underConstruction = true;
            }
         }
      }

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

      if (!underConstruction) {
         try {
            if (getMsgUnitCache() == null) {
               Thread.dumpStack();
               log.severe("MsgUnitCache is unexpected null, topic: " + toXml() + "\n msgUnitWrapper is: " + msgUnitWrapper.toXml());
            }
            else {
               getMsgUnitCache().remove(msgUnitWrapper);
            }
         }
         catch (XmlBlasterException e) {
            log.warning("Internal problem in entryDestroyed removeRandom of msg store (this can lead to a memory leak of '" + msgUnitWrapper.getLogId() + "'): " +
                       e.getMessage()); // + ": " + toXml());
         }
      }
      
      // if it was a volatile message we need to check unreferenced state
      ArrayList notifyList = null;
      synchronized (this) {
         if (!hasCacheEntries() && !hasExactSubscribers()) {
            try {
               if (isSoftErased()) {
                  notifyList = toDead(this.creatorSessionName, null, null);
               }
               else if (publishCounter==0) {
                  notifyList = toUnreferenced(false);
               }
               else {
                   if (log.isLoggable(Level.FINE)) log.fine("Ignored the attempt to set topic unreferenced as other thread in publish");
               }
            }
            catch (XmlBlasterException e) {
               log.severe("Internal problem with entryDestroyed: " + e.getMessage() + ": " + toXml());
            }
         }
      }
      msgUnitWrapper = null;
      if (notifyList != null) notifySubscribersAboutErase(notifyList); // must be outside the synchronize
   }

   /*
    * The root node of the xmlBlaster DOM tree
    */
   public final org.w3c.dom.Node getRootNode() throws XmlBlasterException {
      return getXmlKey().getRootNode(); // don't cache it, as it may change after merge
   }

   /**
    * A client subscribed to this message, multiple subscriptions from
    * the same client are OK.
    * @param calleeIsXPathMatchCheck true The calling thread is internally to check if a Query matches a new published topic
    *        false The callee is a subscribe() thread from a client
    */
   public void addSubscriber(SubscriptionInfo sub, boolean calleeIsXPathMatchCheck) throws XmlBlasterException {
      if (sub.getSubscribeCounter() > 1)
         return;

      //Object oldOne;
      synchronized(this.subscriberMap) {
         /*oldOne = */this.subscriberMap.put(sub.getSubscriptionId(), sub);
      }

      sub.addTopicHandler(this);

      if (this.subscriptionListener != null) 
         this.subscriptionListener.subscriptionAdd(new SubscriptionEvent(sub));

      if (log.isLoggable(Level.FINE)) log.fine("Client '" + sub.getSessionInfo().getId() + "' has successfully subscribed");

      if (isUnconfigured()) {
         return;
      }

      if (isUnreferenced()) {
         toAlive();
      }
      
      // will be triggered by ConnectionStatusListener.toAlive() ..
      if (this.subscriptionListener != null) return;  

      SubscribeQosServer subscribeQosServer = sub.getSubscribeQosServer();
      if (subscribeQosServer == null) {
         return;
      }

      if (log.isLoggable(Level.FINE)) log.fine("addSubscriber("+sub.getId()+")");
      if (subscribeQosServer.getWantInitialUpdate() == true || calleeIsXPathMatchCheck) { // wantInitial==false is only checked if this is a subcribe() thread of a client
         MsgUnitWrapper[] wrappers = null;
         if (hasHistoryEntries())
            wrappers = getMsgUnitWrapperArr(subscribeQosServer.getData().getHistoryQos().getNumEntries(),
                                            subscribeQosServer.getData().getHistoryQos().getNewestFirst());

         if (wrappers != null && wrappers.length > 0) {
            int count = 0, currentCount = 0;
            for (int i=0; i < wrappers.length; i++) {
               if (this.distributor == null || wrappers[i].isInternal()) {
                  currentCount = invokeCallback(null, sub, wrappers[i], true);
               }
               if (currentCount == -1) break;
               count += currentCount;
            }
            count++;
            if (count < 1) {
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
    * Generating dead letter and auto-logout to release all resources is done by DispatchWorker.
    */
   public void handleCallbackFailed(Set removeSet) throws XmlBlasterException {
      // DON'T do a synchronized(this)! (the possibly triggered notifySubscribersAboutErase() could dead lock)
      if (removeSet != null) {
         Iterator iterator = removeSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (log.isLoggable(Level.FINE)) log.fine("Removed subscriber '" + sub.getSessionInfo().getId() + "' as callback failed.");
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

      // DON'T call from inside a synchronized(this)! (the notifySubscribersAboutErase() could dead lock)

      if (log.isLoggable(Level.FINE)) log.fine("Before size of subscriberMap = " + this.subscriberMap.size());

      SubscriptionInfo subs = null;
      synchronized(this.subscriberMap) {
         subs = (SubscriptionInfo)this.subscriberMap.remove(subscriptionInfoUniqueKey);
      }
      if (subs == null && !isDead() && !isSoftErased()) {
         //Thread.currentThread().dumpStack();
         log.warning(", can't unsubscribe, you where not subscribed to subscription ID=" + subscriptionInfoUniqueKey);
      }

      if (log.isLoggable(Level.FINE)) log.fine("After size of subscriberMap = " + this.subscriberMap.size());

      if (isDead()) {
         if (this.subscriptionListener != null && subs != null) {
            try {
               this.subscriptionListener.subscriptionRemove(new SubscriptionEvent(subs));
            }
            catch (XmlBlasterException ex) {
               log.severe("removeSubscriber: an exception occured: " + ex.getMessage());
            }
         }
         return subs; // during cleanup process
      }

      ArrayList notifyList = null;
      synchronized (this) {
         if (!hasCacheEntries() && !hasExactSubscribers()) {
            if (isUnconfigured())
               notifyList = toDead(this.creatorSessionName, null, null);
            else {
               try {
                  notifyList = toUnreferenced(false);
               }
               catch (XmlBlasterException e) {
                  log.severe("Internal problem with removeSubscriber: " + e.getMessage() + ": " + toXml());
               }
            }
         }
      }
      if (this.subscriptionListener != null && subs != null) { 
         try {
            this.subscriptionListener.subscriptionRemove(new SubscriptionEvent(subs));
         }
         catch (XmlBlasterException ex) {
            log.severe("removeSubscriber: an exception occured: " + ex.getMessage());
         }
      }
      if (notifyList != null) notifySubscribersAboutErase(notifyList); // must be outside the synchronize
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
   private final void invokeCallbackAndHandleFailure(SessionInfo publisherSessionInfo, MsgUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      if (msgUnitWrapper == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "MsgUnitWrapper is null");
      }
      if (log.isLoggable(Level.FINE)) log.fine("Going to update dependent clients for " + msgUnitWrapper.getKeyOid() + ", subscriberMap.size() = " + this.subscriberMap.size());

      if (this.distributor != null &&  !msgUnitWrapper.isInternal()) { // if there is a plugin
         this.distributor.distribute(msgUnitWrapper);
         return;   
      }
      
      // Take a copy of the map entries (a current snapshot)
      // If we would iterate over the map directly we can risk a java.util.ConcurrentModificationException
      // when one of the callback fails and the entry is removed by the callback worker thread
      SubscriptionInfo[] subInfoArr = getSubscriptionInfoArr();
      Set removeSet = null;
      for (int ii=0; ii<subInfoArr.length; ii++) {
         SubscriptionInfo sub = subInfoArr[ii];
         if (!subscriberMayReceiveIt(sub, msgUnitWrapper)) continue;
         if (invokeCallback(publisherSessionInfo, sub, msgUnitWrapper, true) < 1) {
            if (removeSet == null) removeSet = new HashSet();
            removeSet.add(sub); // We can't delete directly since we are in the iterator
         }
      }
      if (removeSet != null) handleCallbackFailed(removeSet);
   }


   /**
    * Checks if it is allowed to send the entry to the callback queue. 
    * @param publisherSessionInfo
    * @param sub
    * @return true if it is configured, there is a callback, and the topic is referenced 
    */
   private boolean checkIfAllowedToSend(SessionInfo publisherSessionInfo, SubscriptionInfo sub) {
      if (!sub.getSessionInfo().hasCallback()) {
         log.warning("A client which subscribes " + sub.toXml() + " should have a callback server: "
                       + sub.getSessionInfo().toXml(""));
         Thread.dumpStack();
         return false;
      }
      if (isUnconfigured()) {
         log.warning("invokeCallback() not supported, this MsgUnit was created by a subscribe() and not a publish()");
         return false;
      }
      if (isUnreferenced()) {
         log.severe("PANIC: invoke callback is strange in state 'UNREFERENCED'");
         Thread.dumpStack();
         return false;
      }
      return true;
   }

   /**
    * Checks if the filters allow this message to be sent to the specified session
    * 
    * @param publisherSessionInfo
    * @param sub
    * @param msgUnitWrapper
    * @return true if the message is approved to be sent, false otherwise
    * @throws XmlBlasterException in case an exception happened when checking the filters.
    * This method handles internally the publishing of dead letters in case of a throwable
    * and after that it throws this XmlBlasterException to notify the invoked about the
    * abnormal flow.
    */
   public final boolean checkFilter(SessionInfo publisherSessionInfo, SubscriptionInfo sub, MsgUnitWrapper msgUnitWrapper, boolean handleException) 
      throws XmlBlasterException {

      AccessFilterQos[] filterQos = sub.getAccessFilterArr();
      if (filterQos != null) {
         //SubjectInfo publisher = (publisherSessionInfo == null) ? null : publisherSessionInfo.getSubjectInfo();
         //SubjectInfo destination = (sub.getSessionInfo() == null) ? null : sub.getSessionInfo().getSubjectInfo();
         for (int ii=0; ii<filterQos.length; ii++) {
            try {
               I_AccessFilter filter = requestBroker.getAccessPluginManager().getAccessFilter(
                                         filterQos[ii].getType(), filterQos[ii].getVersion(), 
                                         getContentMime(), getContentMimeExtended());
               if (filter != null && filter.match(sub.getSessionInfo(),
                          msgUnitWrapper.getMsgUnit(), filterQos[ii].getQuery()) == false) {
                  return false;
               }
            }
            catch (Throwable e) {
               // sender =      publisherSessionInfo.getLoginName()
               // receiver =    sub.getSessionInfo().getLoginName()
               // 1. We just log the situation:
               SessionName publisherName = (publisherSessionInfo != null) ? publisherSessionInfo.getSessionName() :
                                  msgUnitWrapper.getMsgQosData().getSender();
               String reason = "Mime access filter '" + filterQos[ii].getType() + "' for message '" +
                         msgUnitWrapper.getLogId() + "' from sender '" + publisherName + "' to subscriber '" +
                         sub.getSessionInfo().getSessionName() + "' threw an exception, we don't deliver " +
                         "the message to the subscriber: " + e.toString();
               if (log.isLoggable(Level.FINE)) log.fine(reason);
               if (handleException) {
                  MsgQueueEntry[] entries = {
                       new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
                                   sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId(),
                                   sub.getSubscribeQosServer().getWantUpdateOneway()) };
                  requestBroker.deadMessage(entries, null, reason);
               }

               // 2. This error handling is wrong as the plugin should not invalidate the subscribe:
               //sub.getSessionInfo().getDispatchManager().internalError(e); // calls MsgErrorHandler
                     
               // 3. This error handling is wrong as we handle a subscribe and not a publish:
               /*
               MsgQueueEntry entry =
                    new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
                                sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId(),
                                sub.getSubscribeQosServer().getWantUpdateOneway());
               publisherSessionInfo.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entry, null, e));
               */
               //retCount++;
               throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME , "checkFilter: " + reason);
            }
         }
      } // if filterQos
      return true;
   }


   /**
    * Checks if the subscriber is a cluster and the message has the 'dirtyRead' flag set.
    * @param sub
    * @param msgQosData
    * @return true if dirtyRead is set, false otherwise.
    */
   public boolean isDirtyRead(SubscriptionInfo sub, MsgUnitWrapper msgUnitWrapper) 
      throws XmlBlasterException {
      MsgQosData msgQosData = msgUnitWrapper.getMsgQosData();
      if (sub.getSessionInfo().getSubjectInfo().isCluster()) {
         if (log.isLoggable(Level.FINEST)) log.finest("Slave node '" + sub.getSessionInfo() + "' has dirty read message '" + msgUnitWrapper.toXml());
         if (msgQosData.dirtyRead(sub.getSessionInfo().getSubjectInfo().getNodeId())) {
            if (log.isLoggable(Level.FINE)) log.fine("Slave node '" + sub.getSessionInfo() + "' has dirty read message '" + sub.getSubscriptionId() + "', '" + sub.getKeyData().getOid() + "' we don't need to send it back");
            return true;
         }
      }
      return false;
   }
   
   public final MsgQueueUpdateEntry createEntryFromWrapper(MsgUnitWrapper msgUnitWrapper, SubscriptionInfo sub) 
      throws XmlBlasterException {
      return new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
               sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId(),
               sub.getSubscribeQosServer().getWantUpdateOneway());
   }

   /**
    * Send update to subscribed client (Pub/Sub mode only).
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    * @param sub The subscription handle of the client
    * @return -1 in case it was not able to complete the invocation due to an incorrect status (for example
    * if it is unconfigured, unreferenced or if the session has no callback). Returns 0 if it was not able
    * to complete the request even if the status was OK, 1 if successful. 
    * Never throws an exception.
    * Returning -1 tells the invoker not to continue with these invocations (performance)
    */
   private final int invokeCallback(SessionInfo publisherSessionInfo, SubscriptionInfo sub,
      MsgUnitWrapper msgUnitWrapper, boolean doErrorHandling) {
      if (!checkIfAllowedToSend(publisherSessionInfo, sub)) return -1;

      if (msgUnitWrapper == null) {
         log.severe("invokeCallback() MsgUnitWrapper is null: " +
                       ((publisherSessionInfo != null) ? publisherSessionInfo.toXml() + "\n" : "") +
                       ((sub != null) ? sub.toXml() + "\n" : "") +
                       ((this.historyQueue != null) ? this.historyQueue.toXml("") : ""));
         Thread.dumpStack();
         return 0;
         //throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME, "MsgUnitWrapper is null");
      }

      try {
         if (isDirtyRead(sub, msgUnitWrapper)) return 1;

         try {
            if (!checkFilter(publisherSessionInfo, sub, msgUnitWrapper, true)) return 1;
         }
         catch (XmlBlasterException ex) {
            return 0;
         }

         if (log.isLoggable(Level.FINER)) log.finer("pushing update() message '" + sub.getKeyData().getOid() + "' " + msgUnitWrapper.getStateStr() +
                       "' into '" + sub.getSessionInfo().getId() + "' callback queue");

         MsgQueueUpdateEntry entry = createEntryFromWrapper(msgUnitWrapper, sub);
         
         sub.getMsgQueue().put(entry, I_Queue.USE_PUT_INTERCEPTOR);

         // If in MsgQueueUpdateEntry we set super.wantReturnObj = true; (see ReferenceEntry.java):
         //UpdateReturnQosServer retQos = (UpdateReturnQosServer)entry.getReturnObj();
         return 1;
      }
      catch (Throwable e) {
         SessionName publisherName = (publisherSessionInfo != null) ? publisherSessionInfo.getSessionName() :
                                     msgUnitWrapper.getMsgQosData().getSender();
         if ( doErrorHandling ) {
            if (log.isLoggable(Level.FINE)) log.fine("Sending of message from " + publisherName + " to " +
                               sub.getSessionInfo().getId() + " failed: " + e.toString());
            try {
               MsgQueueEntry[] entries = {
                     new MsgQueueUpdateEntry(glob, msgUnitWrapper, sub.getMsgQueue().getStorageId(),
                                 sub.getSessionInfo().getSessionName(), sub.getSubSourceSubscriptionId(),
                                 sub.getSubscribeQosServer().getWantUpdateOneway()) };
               String reason = e.toString();
               if (e instanceof XmlBlasterException)
                  reason = ((XmlBlasterException)e).getMessage();
               requestBroker.deadMessage(entries, null, reason);
            }
            catch (XmlBlasterException e2) {
               log.severe("PANIC: Sending of message '" + msgUnitWrapper.getLogId() + "' from " + publisherName + " to " +
                               sub.getSessionInfo().getId() + " failed, message is lost: " + e2.getMessage() + " original exception is: " + e.toString());
            }
            catch (Throwable e2) {
               e.printStackTrace(); // original stack
               log.severe("PANIC: Sending of message '" + msgUnitWrapper.getLogId() + "' from " + publisherName + " to " +
                               sub.getSessionInfo().getId() + " failed, message is lost: " + e2.getMessage() + " original exception is: " + e.toString());
            }
            return 1; // Don't remove subscriber for queue overflow exception
         }
         else {
            return 0;
         }
      }
   }

   // JMX
   public final int getNumSubscribers() {
      return numSubscribers();
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

   // JMX
   public final boolean getExactSubscribersExist() {
      return hasExactSubscribers();
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
    * Returns SubscriptionInfo instances of this session
    * (a session may subscribe the same message multiple times). 
    * <p />
    * This searches from a given SessionInfo.
    * @return never null but can be of length==0
    */
   public final SubscriptionInfo[] findSubscriber(SessionInfo sessionInfo) {
      synchronized(this.subscriberMap) {
         ArrayList list = null;
         Iterator iterator = this.subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().isSameSession(sessionInfo)) {
               if (list == null) list = new ArrayList();
               list.add(sub);
            }
         }
         if (list == null) return new SubscriptionInfo[0];
         return (SubscriptionInfo[])list.toArray(new SubscriptionInfo[list.size()]);
      }
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
            log.severe("Problems in clearSubscriber: " + e.getMessage());
         }
      }
      synchronized(this.subscriberMap) {
         this.subscriberMap.clear();  // see collectNotifySubscribersAboutErase() above
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
         log.severe("Internal problem: we have messages but are not alive: " + toXml());
         Thread.dumpStack();
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
    * @param newestFirst true is the normal case (the latest message is returned first)
    * @return Checked MsgUnitWrapper entries (destroyed and expired ones are removed), never null
    */
   public MsgUnitWrapper[] getMsgUnitWrapperArr(int num, boolean newestFirst) throws XmlBlasterException {
      if (this.historyQueue == null)
         return new MsgUnitWrapper[0];
      ArrayList historyList = this.historyQueue.peek(num, -1);
      if (log.isLoggable(Level.FINE)) log.fine("getMsgUnitWrapperArr("+num+","+newestFirst+"), found " + historyList.size() + " historyList entries");
      ArrayList aliveMsgUnitWrapperList = new ArrayList();
      ArrayList historyDestroyList = null;
      int n = historyList.size();
      for(int i=0; i<n; i++) {
         MsgQueueHistoryEntry entry = (MsgQueueHistoryEntry)historyList.get(i);
         if (entry != null) {
            MsgUnitWrapper wr = entry.getMsgUnitWrapper();
            if (wr != null) {
               if (wr.hasRemainingLife()) {
                  aliveMsgUnitWrapperList.add(wr);
               }
               else {
                  if (historyDestroyList == null) historyDestroyList = new ArrayList();
                  historyDestroyList.add(entry);
               }
            }
            else {
               if (entry.isExpired()) {
                  if (log.isLoggable(Level.FINE)) log.fine("getMsgUnitWrapperArr(): MsgUnitWrapper weak reference from history queue is null, it is expired: " + entry.toXml());
               }
               else {
                  log.severe("getMsgUnitWrapperArr(): MsgUnitWrapper weak reference from history queue is null, this could be a serious bug, please report it to xmlBlaster@xmlBlaster.org mailing list: " +
                     entry.toXml() + "\n" + // toXml() not possible as it call recursive getMsgUnitWrapperArr());
                     ((this.msgUnitCache != null) ? this.msgUnitCache.toXml("") + "\n" : "") +
                     ((this.historyQueue != null) ? this.historyQueue.toXml("") : "")
                     );
                  Thread.dumpStack();
               }
               if (historyDestroyList == null) historyDestroyList = new ArrayList();
               historyDestroyList.add(entry);
            }
         }
      }

      if (historyDestroyList != null && historyDestroyList.size() > 0) {
         this.historyQueue.removeRandom((I_Entry[])historyDestroyList.toArray(new I_Entry[historyDestroyList.size()]));
      }

      if (newestFirst) {
         return (MsgUnitWrapper[])aliveMsgUnitWrapperList.toArray(new MsgUnitWrapper[aliveMsgUnitWrapperList.size()]);
      }
      else {
         MsgUnitWrapper[] arr = new MsgUnitWrapper[aliveMsgUnitWrapperList.size()];
         int size = aliveMsgUnitWrapperList.size();
         for(int i=0; i<size; i++)
            arr[i] = (MsgUnitWrapper)aliveMsgUnitWrapperList.get(size-i-1);
         return arr;
      }
   }

   /**
    * Returns a snapshot of all entries in the history
    * @param num Number of entries wanted, not more than size of history queue are returned.<br />
    *            If -1 all entries in history queue are returned
    * @param newestFirst true is the normal case (the latest message is returned first)
    * @return Checked entries (destroyed and expired ones are removed), never null
    */
   public MsgUnit[] getHistoryMsgUnitArr(int num, boolean newestFirst) throws XmlBlasterException {
      MsgUnitWrapper[] msgUnitWrapper = getMsgUnitWrapperArr(num, newestFirst);
      MsgUnit[] msgUnitArr = new MsgUnit[msgUnitWrapper.length];
      for (int i=0; i<msgUnitWrapper.length; i++) {
         msgUnitArr[i] = msgUnitWrapper[i].getMsgUnit();
      }
      return msgUnitArr;
   }

   /**
    * Erase message instances but not the topic itself. 
    * @param sessionInfo The user which has called erase()
    * @param historyQos Describes which message instances
    * @throws XmlBlasterException Currently only all history entries can be destroyed
    */
   public long eraseFromHistoryQueue(SessionInfo sessionInfo, HistoryQos historyQos) throws XmlBlasterException {
      //if (hasHistoryEntries()) {
         if (log.isLoggable(Level.FINE)) log.fine("Erase request for " + historyQos.toXml() +
             " history entries, we currently contain " + this.historyQueue.getNumOfEntries() + " entries.");
         if (historyQos.getNumEntries() == -1) {
            return this.historyQueue.clear();
         }
         else {
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME,
                  "Erasing of specific history entries is not yet implemented, you can only erase all of them");
         }
      //}
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
      if (log.isLoggable(Level.FINER)) log.finer("Entering toUnconfigured(oldState="+getStateStr()+")");
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
      if (log.isLoggable(Level.FINER)) log.finer("Entering toAlive(oldState="+getStateStr()+")");
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
                  toDead(this.creatorSessionName, null, null);
               else if (isUnconfigured())
                  ; // ignore
               throw e;
            }
         }

         persistTopicEntry();
         initMsgDistributorPlugin();
         this.state = ALIVE;
      }
   }

   private void removeTopicPersistence() {
      if (requestBroker.getTopicStore() == null) {
         return;   // RAM based operation
      }
      try {
         if (this.topicEntry != null) {
            int num = this.requestBroker.removePersistentTopicHandler(this.topicEntry);
            this.topicEntry = null;
            if (num == 0) {
               log.warning("" + num + " TopicHandler removed from persistency");
            }
            else {
               if (log.isLoggable(Level.FINE)) log.fine("" + num + " TopicHandler removed from persistency");
            }
            return;
         }
      }
      catch (XmlBlasterException e) {
         log.severe("Persisting TopicHandler failed, we continue memory based: " + e.getMessage());
      }
   }

   private ArrayList toUnreferenced(boolean onAdministrativeCreate) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering toUnreferenced(oldState="+getStateStr()+", onAdministrativeCreate="+onAdministrativeCreate+")");
      ArrayList notifyList = null;
      synchronized (this) {
         if (isUnreferenced() || isDead()) {
            return null;
         }
         int oldState = this.state;

         if (hasHistoryEntries()) {
            if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "UNREFERENCED: Clearing " + getNumOfHistoryEntries() + " history entries");
            this.historyQueue.clear();
         }
         if (hasCacheEntries()) {
            if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "UNREFERENCED: Clearing " + this.msgUnitCache.getNumOfEntries() + " msgUnitStore cache entries");
            this.msgUnitCache.clear();  // Who removes the MsgUnitWrapper entries from their Timer?!!!! TODO
         }

         this.state = UNREFERENCED;

         if (!onAdministrativeCreate) {
            if (this.topicProperty == null) {
               EraseQosServer eraseQos = new EraseQosServer(glob, "<qos/>");
               eraseQos.setForceDestroy(true);
               notifyList = toDead(this.creatorSessionName, null, eraseQos);
               return notifyList; // ALIVE -> UNREFERENCED
            }

            if (topicProperty.getDestroyDelay() > 0L) {
               if (this.timerKey == null) {
                  this.timerKey = this.destroyTimer.addTimeoutListener(this, topicProperty.getDestroyDelay(), null);
               }
            }
            else if (topicProperty.getDestroyDelay() == 0L) {
               timeout();   // toDead()
               return null; // destroy immediately
            }
         // for destroyDelay < 0 we live forever or until an erase arrives
         }

         if (!isRegisteredInBigXmlDom) {
            addToBigDom(); // guarantee still XPATH visibility
         }

         // On administrative startup
         if (oldState == UNDEF || oldState == UNCONFIGURED) {
            persistTopicEntry();
         }
      }
      return notifyList;
   }

   /**
    * @param sessionInfo The session which triggered the erase
    * @param eraseQosServer TODO
    */
   private ArrayList toSoftErased(SessionInfo sessionInfo, QueryKeyData eraseKey, EraseQosServer eraseQos) {
      if (log.isLoggable(Level.FINER)) log.finer("Entering toSoftErased(oldState="+getStateStr()+")");
      ArrayList notifyList = null;
      synchronized (this) {
         if (isSoftErased()) {
            return null;
         }
         if (hasHistoryEntries()) {
            if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "SOFTERASED: Clearing " + getNumOfHistoryEntries() + " history entries");
            this.historyQueue.clear();
         }

         if (hasSubscribers()) {
            if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "SOFTERASED: Clearing " + numSubscribers() + " subscriber entries");
            notifyList = collectNotifySubscribersAboutErase(sessionInfo.getSessionName(), eraseKey, eraseQos);
            clearSubscribers();
         }

         this.state = SOFT_ERASED;
         removeFromBigDom();
         this.handlerIsNewCreated = true;
         this.administrativeInitialize = false;
      }
      return notifyList;
   }

   /**
    * @param sessionName The session which triggered this event
    * @param eraseKey TODO
    */
   private ArrayList toDead(SessionName sessionName, QueryKeyData eraseKey, EraseQosServer eraseQos) {
      
      if (log.isLoggable(Level.FINER)) log.finer("Entering toDead(oldState="+getStateStr()+")");
      long numHistory = 0L;
      ArrayList notifyList = null;

      this.glob.unregisterMBean(this.mbeanHandle);

      synchronized (this) {
         if (this.dyingInProgress || isDead()) {
            return null;
         }
         this.dyingInProgress = true;

         try {
            shutdownMsgDistributorPlugin();
            if (this.topicEntry != null) {
               removeTopicPersistence();
            }
            else {
               if (!isUnconfigured()) {
                  if (isSoftErased()) {
                     log.fine("In " + getStateStr() + " -> DEAD: this.topicEntry == null");
                  }
                  else {
                     log.severe("In " + getStateStr() + " -> DEAD: this.topicEntry == null");
                     Thread.dumpStack();
                  }
                  return null;
               }
            }

            if (isAlive()) {
               if (numSubscribers() > 0 || hasCacheEntries() || hasHistoryEntries())
                  log.info("Forced state transition ALIVE -> DEAD with " + numSubscribers() + " subscribers, " +
                            getNumOfCacheEntries() + " cache messages and " +
                            getNumOfHistoryEntries() + " history messages.");
            }

            if (/*!eraseQos.getForceDestroy() &&*/ !isSoftErased()) {
               notifyList = collectNotifySubscribersAboutErase(sessionName, eraseKey, eraseQos);
            }

            if (hasHistoryEntries()) {
               try {
                  numHistory = this.historyQueue.clear();
                  if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "DEAD: Cleared " + numHistory + " history entries");
               }
               catch (Throwable e) {
                  log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the history queue: " + e.getMessage());
               }
            }
            if (this.historyQueue != null) {
               this.historyQueue.shutdown();
            }

            if (hasCacheEntries()) {
               try {
                  long num = this.msgUnitCache.clear();
                  if (log.isLoggable(Level.FINE)) log.fine(getStateStr() + "->" + "DEAD: Cleared " + num + " message storage entries");
               }
               catch (XmlBlasterException e) {
                  log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the message store: " + e.getMessage());
               }
            }
            if (this.msgUnitCache != null) {
               this.msgUnitCache.shutdown();
            }

            if (this.topicEntry != null) { // a second time if the above collectNotifySubscribersAboutErase() made an unconfigured topic alive
               removeTopicPersistence();
            }
         }
         catch (Throwable e) {
            log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the message store: " + e.getMessage());
         }
         finally {
            this.state = DEAD;
            this.dyingInProgress = false; // no need for this anymore, reset it
         }


         try {
            removeFromBigSubscriptionSet();
         }
         catch (Throwable e) {
            log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the subscriptions: " + e.getMessage());
         }
         
         try {
            removeFromBigDom();
         }
         catch (Throwable e) {
            log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the big DOM: " + e.getMessage());
         }
         
         try {
            clearSubscribers(); // see notifySubscribersAboutErase() above
         }
         catch (Throwable e) {
            log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the subscribers: " + e.getMessage());
         }

         try {
            removeFromBigMessageMap();
         }
         catch (Throwable e) {
            log.severe(getStateStr() + "->" + "DEAD: Ignoring problems during clearing the big message map: " + e.getMessage());
         }

         if (this.timerKey != null) {
            this.destroyTimer.removeTimeoutListener(this.timerKey);
            this.timerKey = null;
         }

         this.handlerIsNewCreated = true;
      } // sync
      log.info("Topic reached state " + getStateStr() + ". " + numHistory + " history entries are destroyed.");
      return notifyList;
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
         log.severe("Received exception on BigDom erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Send erase event with a volatile non persistent erase message. 
    * The oid of the PtP message is temporary "__sys__ErasedTopic" and later
    * the oid of the erased topic, the state is set to STATE_ERASED 
    * <p>
    * This method may NOT be called from inside a synchronized((TopicHandler)this):
    * The CB worker thread which empties the callback queue may call this TopicHandler.entryDestroyed()
    * which could cause a dead lock.
    * </p>
    * 
    * @param sessionName The session which triggered the erase 
    * @return A set containing MsgUnit instances to send to the various clients
    */
   private void notifySubscribersAboutErase(ArrayList msgSet) {
      if (msgSet == null) return;
      SessionInfo publisherSessionInfo = glob.getRequestBroker().getInternalSessionInfo();

      Iterator it = msgSet.iterator();
      while (it.hasNext()) {
         MsgUnit msgUnit = (MsgUnit)it.next();
         try {
            requestBroker.publish(publisherSessionInfo, msgUnit);
         }
         catch (XmlBlasterException e) {
            // The access plugin or client may throw an exception. The behavior is not coded yet
            log.severe("Received exception when sending message erase event callback to client: " + e.getMessage() + ": " + msgUnit.toXml());
         }
      }
   }

   /**
    * Collect erase events with volatile non persistent erase messages. 
    * The oid of the PtP message is temporary "__sys__ErasedTopic" and later
    * the oid of the erased topic, the state is set to STATE_ERASED 
    * @param sessionName The session which triggered the erase 
    * @param eraseKey Can be null if not known (e.g. for implicit erase after unSubscribe or last message is expired)
    * @param eraseQos Can be null if not known
    * @return A set containing MsgUnit instances to send to the various clients
    */
   private ArrayList collectNotifySubscribersAboutErase(SessionName sessionName, QueryKeyData eraseKey, EraseQosServer eraseQos) {
      if (log.isLoggable(Level.FINER)) log.finer("Sending client notification about message erase() event");

      if (hasSubscribers()) { // && (isAlive() || isUnconfigured())) { // Filter for Approach 1. (supresses XPath notifies)
         if (Constants.EVENT_OID_ERASEDTOPIC.equals(getUniqueKey())) {
            return null;
         }
         try {
            ArrayList notifyList = new ArrayList();
            /* 
               // Approach 1: Send erase notify with same topic oid
               // This was used until 0.91+
               // Problem was that it triggered this dieing topic into ALIVE again
               org.xmlBlaster.client.key.PublishKey pk = new org.xmlBlaster.client.key.PublishKey(glob,
                                                         getUniqueKey(), "text/plain", "1.0");
               org.xmlBlaster.client.qos.PublishQos pq = new org.xmlBlaster.client.qos.PublishQos(glob);
               pq.setState(Constants.STATE_ERASED);
               pq.setVolatile(true);
               pq.setSender(sessionName);
               MsgUnit msgUnit = new MsgUnit(pk, getId(), pq); // content contains the global name?
               PublishQosServer ps = new PublishQosServer(glob, pq.getData());
            */

               // Approach 2: Send PtP message with a dedicated topic
               // Problem: We need to change the oid back to this topics oid to be back compatible
               // (see CbDispatchConnection.java)
               SubscriptionInfo[] arr = getSubscriptionInfoArr();
               for(int i=0; i<arr.length; i++) {
                  SubscriptionInfo sub = arr[i];
                  org.xmlBlaster.client.key.PublishKey pk = new org.xmlBlaster.client.key.PublishKey(glob,
                                                            Constants.EVENT_OID_ERASEDTOPIC/*+":"+getUniqueKey()*/, "text/plain", "1.0");
                  org.xmlBlaster.client.qos.PublishQos pq = new org.xmlBlaster.client.qos.PublishQos(glob);
                  pq.setState(Constants.STATE_ERASED);
                  pq.setVolatile(true);
                  pq.setSender(sessionName);
                  pq.addDestination(new Destination(sub.getSessionInfo().getSessionName()));
                  pq.addClientProperty("__oid", getUniqueKey());
                  MsgKeyData k = this.msgKeyData;
                  if (k != null && k.getDomain() != null)
                     pq.addClientProperty("__domain", k.getDomain());
                  pq.addClientProperty("__subscriptionId", sub.getSubSourceSubscriptionId());
                  if (eraseKey != null) // To have all attributes for cluster slaves getting forwarded the erase
                     pq.addClientProperty("__eraseKey", eraseKey.toXml());
                  if (eraseQos != null) // To have all attributes for cluster slaves getting forwarded the erase
                     pq.addClientProperty("__eraseQos", eraseQos.toXml());
                  if (i==0) {
                     TopicProperty topicProperty = new TopicProperty(glob);
                     //topicProperty.setDestroyDelay(destroyDelay);
                     topicProperty.setCreateDomEntry(false);
                     org.xmlBlaster.util.qos.storage.HistoryQueueProperty prop = new org.xmlBlaster.util.qos.storage.HistoryQueueProperty(this.glob, null);
                     prop.setMaxEntriesCache(0);
                     prop.setMaxEntries(0);
                     topicProperty.setHistoryQueueProperty(prop);
                     pq.setTopicProperty(topicProperty);
                     if (log.isLoggable(Level.FINE)) log.fine("Added TopicProperty to " + pk.getOid() + " on first publish: " + topicProperty.toXml());
                  }
                  MsgUnit msgUnit = new MsgUnit(pk, getId(), pq);
                  notifyList.add(msgUnit);
               }

            /* 
               // Approach 3: Shuffle it directly into the callback queues
               // Here the topic is not touched anymore but the msgUnitStore must remain alive
               // The problem with this approach is that the msgUnitCache may be destroyed before the callback is delivered
               org.xmlBlaster.client.key.PublishKey pk = new org.xmlBlaster.client.key.PublishKey(glob,
                                                         getUniqueKey(), "text/plain", "1.0");
               org.xmlBlaster.client.qos.PublishQos pq = new org.xmlBlaster.client.qos.PublishQos(glob);
               pq.setState(Constants.STATE_ERASED);
               pq.setVolatile(true);
               pq.setSender(sessionName);
               pq.getData().touchRcvTimestamp();
               MsgUnit msgUnit = new MsgUnit(pk, getId(), pq); // content contains the global name?
               MsgUnitWrapper msgUnitWrapper = null;
               StorageId storageId = this.msgUnitCache.getStorageId();
               int initialCounter = 1;
               try {
                  msgUnitWrapper = new MsgUnitWrapper(glob, msgUnit, this.msgUnitCache, initialCounter, 0, -1);
                  SubscriptionInfo[] arr = getSubscriptionInfoArr();
                  for(int i=0; i<arr.length; i++) {
                     SubscriptionInfo sub = arr[i];
                     int num = invokeCallback(publisherSessionInfo, sub, msgUnitWrapper, false);
                     if (num < 1) {
                        log.warn(ME, "Sending of erase notification message '" + msgUnitWrapper.getLogId() +
                                  "' from " + publisherSessionInfo.getId() + " to " +
                                  sub.getSessionInfo().getId() + " failed, no notification is possible");
                     }
                  }
               }
               finally {
                  if (msgUnitWrapper != null) msgUnitWrapper.incrementReferenceCounter(-1*initialCounter, storageId);
               }
            */
            return notifyList;
         }
         catch (XmlBlasterException e) {
            // The access plugin or client may throw an exception. The behavior is not coded yet
            log.severe("Received exception for message erase event (callback to client), we ignore it: " + e.getMessage());
            return null;
         }
      }
      return null;
   }

   /**
    * Remove myself from big subscription set
    */
   private void removeFromBigSubscriptionSet() {
      try {
         requestBroker.getClientSubscriptions().topicRemove(this);
      }
      catch (XmlBlasterException e) {
         log.severe("Received exception on message erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Remove from big message map in RequestBroker
    */
   private void removeFromBigMessageMap() {
      try {
         requestBroker.topicErase(this);
      }
      catch (XmlBlasterException e) {
         log.severe("Received exception on message erase, we ignore it: " + e.getMessage());
         e.printStackTrace();
      }
   }

   /**
    * Notify all Listeners that a message is erased.
    *
    * @param sessionInfo
    * @param eraseKey The original EraseKey
    * @param eraseQos
    */
   final void fireMessageEraseEvent(SessionInfo sessionInfo, QueryKeyData eraseKey, EraseQosServer eraseQos) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering fireMessageEraseEvent forceDestroy=" + eraseQos.getForceDestroy());
      eraseQos = (eraseQos==null) ? new EraseQosServer(glob, new QueryQosData(glob, MethodName.ERASE)) : eraseQos;
      ArrayList notifyList = null;
      try {
         synchronized (this) {
            if (isAlive() || isUnconfigured()) {
               if (eraseQos.getForceDestroy()) {
                  notifyList = toDead(sessionInfo.getSessionName(), eraseKey, eraseQos);
                  return;
               }
               else {
                  notifyList = toSoftErased(sessionInfo, eraseKey, eraseQos); // kills all history entries, notify subscribers
                  long numMsgUnitStore = (this.msgUnitCache==null) ? 0L : this.msgUnitCache.getNumOfEntries();
                  if (numMsgUnitStore < 1) { // has no callback references?
                     toDead(sessionInfo.getSessionName(), eraseKey, eraseQos);
                     return;
                  }
                  else {
                     log.info("Erase not possible, we are still referenced by " + numMsgUnitStore +
                     " callback queue entries, transition to topic state " + getStateStr() + ", all subscribers are removed.");
                  }
               }
            }

            if (isUnreferenced()) {
               notifyList = toDead(sessionInfo.getSessionName(), eraseKey, eraseQos);
               return;
            }
         }
      }
      finally {
         if (notifyList != null) notifySubscribersAboutErase(notifyList); // must be outside the synchronize
      }
   }


   /**
    * This timeout occurs after a configured delay (destroyDelay) in UNREFERENCED state
    */
   public final void timeout(Object userData) {
      if (log.isLoggable(Level.FINER)) log.finer("Timeout after destroy delay occurred - destroying topic now ...");
      ArrayList notifyList = timeout();
      if (notifyList != null) notifySubscribersAboutErase(notifyList); // must be outside the synchronize
   }

   private ArrayList timeout() {
      if (isDead())
         return null;
      synchronized (this) {
         if (isAlive()) // interim message arrived?
            return null;
         return toDead(this.creatorSessionName, null, null);
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
         log.severe("PANIC: Unknown internal state=" + state);
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
         log.severe(e.getMessage());
      }

      if (hasSubscribers()) {
         SubscriptionInfo[] subscriptionInfoArr = getSubscriptionInfoArr();
         for(int i=0; i<subscriptionInfoArr.length; i++) {
            sb.append(offset).append(" <SubscriptionInfo id='").append(subscriptionInfoArr[i].getSubscriptionId()).append("'/>");
         }
      }
      else {
         sb.append(offset + " <SubscriptionInfo>NO SUBSCRIPTIONS</SubscriptionInfo>");
      }

      sb.append(offset).append(" <newCreated>").append(this.handlerIsNewCreated).append("</newCreated>");
      sb.append(offset).append("</TopicHandler>\n");
      return sb.toString();
   }

   
   public I_Queue getHistoryQueue() {
      return this.historyQueue;
   }   
   
   /**
    * Query the history queue, can be peeking or consuming. 
    * @param querySpec Can be configured to be consuming
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.queryspec.QueueQuery.html">The engine.qos.queryspec.QueueQuery requirement</a>
    */
   public MsgUnit[] getHistoryQueueEntries(String querySpec) throws XmlBlasterException {
      if (this.queueQueryPlugin == null) {
         synchronized (this) {
            if (this.queueQueryPlugin == null) {
               this.queueQueryPlugin = new QueueQueryPlugin(this.glob);
            } 
         }
      }
      return this.queueQueryPlugin.query(this.historyQueue, querySpec);
   }

   /**
    * instantiates and initializes a MsgDistributorPlugin if the topic property requires so.
    * If such a plugin exists already it is left untouched.
    * @throws XmlBlasterException
    */
   private void initMsgDistributorPlugin() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("initMsgDistributorPlugin");
      if (this.distributor != null) return;
      String typeVersion = this.topicProperty.getMsgDistributor();
      // if (typeVersion == null) return; // no plugin has been configured for this topic
      this.distributor = this.glob.getMsgDistributorPluginManager().getPlugin(typeVersion, this);
      this.subscriptionListener = this.distributor; 
      if (this.subscriptionListener != null) {
         SubscriptionInfo[] subs = getSubscriptionInfoArr();
         for (int i=0; i < subs.length; i++)
            this.subscriptionListener.subscriptionAdd(new SubscriptionEvent(subs[i]));
      }
   }
   
   private void shutdownMsgDistributorPlugin() {
      if (log.isLoggable(Level.FINER)) log.finer("shutdownMsgDistributorPlugin");
      if (this.distributor == null) return;
      synchronized(this) {
         try {
            this.distributor.shutdown();
         }
         catch (XmlBlasterException ex) {
            log.severe("shutdownMsgDistributorPlugin " + ex.getMessage());
         }
         finally {
            this.distributor = null;
            this.subscriptionListener = null;
         }
      }
   }
   

   public final boolean subscriberMayReceiveIt(SubscriptionInfo sub, MsgUnitWrapper msgUnitWrapper) {
      if (sub == null) return false;
      if (sub.getSessionInfo() == null) return false;
      QueryQosData qos = sub.getQueryQosData();
      if (qos == null) return false;
      if (!qos.getWantLocal() && 
           sub.getSessionInfo().getSessionName().equalsAbsolute(msgUnitWrapper.getMsgQosData().getSender())) return false;
      if (!qos.getWantNotify() && msgUnitWrapper.getMsgQosData().isErased()) return false; 
      return true;
   }

   public final I_MsgDistributor getMsgDistributorPlugin() {
      return this.distributor;
   }

   /** JMX */
   public final String[] getSubscribers() {
      SubscriptionInfo[] infoArr = getSubscriptionInfoArr();
      
      if (infoArr.length < 1)
         return new String[0]; // { "This topic has currently no subscriber" };

      String[] ret = new String[infoArr.length];
      for (int i=0; i<infoArr.length; i++) {
         ret[i] = infoArr[i].getSessionName();
      }
      return ret;
   }

   public final String[] unSubscribeByIndex(int index, String qos) throws XmlBlasterException {
      SubscriptionInfo[] infoArr = getSubscriptionInfoArr();
      
      if (infoArr.length < 1)
         return new String[] { "This topic has currently no subscriber" };

      if (index < 0 || index >= infoArr.length) {
         return new String[] { "Please choose an index between 0 and " + (infoArr.length-1) + " (inclusiv)" };
      }

      log.info("Administrative unSubscribe() of client '" + infoArr[index].getSessionName() + "' for topic '" + getId() + "'");
      return unSubscribe(infoArr[index].getSessionInfo(), qos);
   }

   public final String[] unSubscribeBySessionName(String sessionName, String qos) throws XmlBlasterException {
      SubscriptionInfo[] infoArr = getSubscriptionInfoArr();
      
      if (infoArr.length < 1)
         return new String[] { "This topic has currently no subscriber" };

      SessionInfo sessionInfo = null;
      SessionName wanted = new SessionName(glob, sessionName);
      for (int i=0; i<infoArr.length; i++) {
         SessionName tmp = infoArr[i].getSessionInfo().getSessionName();
         if (wanted.equalsRelative(tmp) || wanted.equalsAbsolute(tmp)) {
            sessionInfo = infoArr[i].getSessionInfo();
            break;
         }
      }

      if (sessionInfo == null)
         return new String[] { "Unsubscribe of client '" + sessionName + "' failed, it did NOT match any client" };

      log.info("Administrative unSubscribe() of client '" + sessionName + "' for topic '" + getId() + "'");
      return unSubscribe(sessionInfo, qos);
   }
   
   /** private helper to unSubscribe */
   private String[] unSubscribe(SessionInfo sessionInfo, String qos) throws XmlBlasterException {
      String sessionName = sessionInfo.getSessionName().getAbsoluteName();
      UnSubscribeKey uk = new UnSubscribeKey(glob, uniqueKey);
      UnSubscribeQos uq;
      if (qos == null || qos.length() == 0 || qos.equalsIgnoreCase("String"))
         uq = new UnSubscribeQos(glob);
      else
         uq = new UnSubscribeQos(glob, glob.getQueryQosFactory().readObject(qos));
      UnSubscribeQosServer uqs = new UnSubscribeQosServer(glob, uq.getData());
      String[] ret = glob.getRequestBroker().unSubscribe(sessionInfo, uk.getData(), uqs);

      if (ret.length == 0)
         return new String[] { "Unsubscribe of client '" + sessionName + "' failed, the reason is not known" };

      for (int i=0; i<ret.length; i++) {
         UnSubscribeReturnQos tmp = new UnSubscribeReturnQos(glob, ret[i]);
         ret[i] = "Unsubscribe '" + sessionName + "' state is " + tmp.getState();
         if (tmp.getStateInfo() != null)
            ret[i] += " " + tmp.getStateInfo();
      }

      return ret;
   }

   public final String[] unSubscribeAll(String qos) throws XmlBlasterException {
      SubscriptionInfo[] infoArr = getSubscriptionInfoArr();
      
      if (infoArr.length < 1)
         return new String[] { "This topic has currently no subscribers" };

      log.info("Administrative unSubscribe() of " + infoArr.length + " clients");

      ArrayList retList = new ArrayList();
      for (int i=0; i<infoArr.length; i++) {
         String[] tmp = unSubscribe(infoArr[i].getSessionInfo(), qos);
         for (int j=0; j<tmp.length; j++) {
            retList.add(tmp[j]);
         }
      }

      if (retList.size() == 0)
         return new String[] { "Unsubscribe of all clients failed, the reason is not known" };

      return (String[])retList.toArray(new String[retList.size()]);
   }

   public String[] peekHistoryMessages(int numOfEntries) throws XmlBlasterException {
      return this.glob.peekMessages(this.historyQueue, numOfEntries, "history");
   } 

   public String[] peekHistoryMessagesToFile(int numOfEntries, String path) throws Exception {
      try {
         return this.glob.peekQueueMessagesToFile(this.historyQueue, numOfEntries, path, "history");
      }
      catch (XmlBlasterException e) {
         throw new Exception(e.toString());
      }
   }

   /** JMX */
   public final String eraseTopic() throws XmlBlasterException {
      EraseKey ek = new EraseKey(glob, uniqueKey);
      EraseQos eq = new EraseQos(glob);
      String[] eraseArr = this.requestBroker.erase(
                    this.requestBroker.getInternalSessionInfo(),
                    ek.getData(),
                    new EraseQosServer(this.glob, eq.getData()));
      if (eraseArr.length == 1) {
         log.info("Erased topic '" + getId() + "' due to administrative request");
         return "Erased topic '" + getId() + "'";
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME,
                   "Erasing of topic '" + getId() + "' due to administrative request failed");
      }
   }
   /** JMX */
   public java.lang.String usage() {
      return ServerScope.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }
   /** JMX */
   public java.lang.String getUsageUrl() {
      return ServerScope.getJavadocUrl(this.getClass().getName(), null);
   }
   /* JMX dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}
}
