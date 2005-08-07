/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.jutils.time.TimeHelper;
import java.util.Vector;


/**
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MsgUnit of exactly one Client. 
 * <p />
 */
public final class SubscriptionInfo implements /*I_AdminSubscription,*/ SubscriptionInfoMBean /* implements Comparable see SORT_PROBLEM */
{
   private String ME = "SubscriptionInfo";
   private ContextNode contextNode;

   /** The global handle */
   private Global glob;
   /** Logging to channel "core" */
   private LogChannel log;
   /** The initiatior of this subscription */
   private SessionInfo sessionInfo;
   /** reference to keyData */
   private KeyData keyData;
   /** reference to 'Quality of Service' of subscribe() / unSubscribe() */
   private SubscribeQosServer subscribeQos = null;
   /** The unique key of a subscription (subscriptionId), which is a function of f(keyData,xmlQos). <br />
       This is the returned id of a subscribe() invocation */
   private String uniqueKey=null;
   /** reference to my managing container */
   private TopicHandler topicHandler;
   /** A reference to the query subscription (XPATH), which created this subscription
       If the subscription was EXACT, querySub is null */
   private SubscriptionInfo querySub = null;
   /** It it is a query subscription, we remember all subscriptions which resulted from this query */
   private Vector childrenVec = null;

   /** If duplicateUpdates=false is set we can check here how often this message is
       subscribed from the same client */
   private int subscribeCounter = 0; // is incr/decr by fireSubscribeEvent() and fireUnSubscribeEvent()

   private long creationTime = System.currentTimeMillis();

   /** uniqueId used to store this in queue */
   private long persistenceId = -1L; 

   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;

   /**
    * Use this constructor for an exact subscription.
    * @param sessionInfo The session which initiated this subscription
    * @param keyData     The message meta info
    * @param qos         This may be a SubscribeQosServer or a UnSubscribeQosServer instance
    */
   public SubscriptionInfo(Global glob, SessionInfo sessionInfo, KeyData keyData, SubscribeQosServer qos) throws XmlBlasterException {
      init(glob, sessionInfo, keyData, qos);
   }

   /**
    * Use this constructor it the subscription is a result of a XPath subscription
    * @param sessionInfo The session which initiated this subscription
    * @param querySub    The XPATH query subscription which is has us as a child
    * @param keyData     The matching key for the above querySub
    */
   public SubscriptionInfo(Global glob, SessionInfo sessionInfo, SubscriptionInfo querySub, KeyData keyData) throws XmlBlasterException {
      this.querySub = querySub;
      init(glob, sessionInfo, keyData, querySub.subscribeQos);
   }

   private void init(Global glob, SessionInfo sessionInfo, KeyData keyData, SubscribeQosServer qos) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("core");
      this.sessionInfo = sessionInfo;
      this.keyData = keyData;
      this.subscribeQos = qos;

      AccessFilterQos[] filterQos = this.subscribeQos.getAccessFilterArr();
      if (filterQos != null) {
         for (int ii=0; ii<filterQos.length; ii++) {
            this.glob.getRequestBroker().getAccessPluginManager().addAccessFilterPlugin(
                     filterQos[ii].getType(), filterQos[ii].getVersion());
         }
      }

      initSubscriptionId(); // initialize the unique id this.uniqueKey
      ME += "-" + this.uniqueKey ;

      // For JMX instanceName may not contain ","
      String instanceName = this.glob.validateJmxValue(this.uniqueKey);
      this.contextNode = new ContextNode(this.glob, ContextNode.SUBSCRIPTION_MARKER_TAG, 
                           instanceName, this.glob.getContextNode());
      this.mbeanHandle = this.glob.registerMBean(this.contextNode, this);

      if (log.TRACE) log.trace(ME, "Created SubscriptionInfo '" + getSubscriptionId() + "' for client '" + sessionInfo.getLoginName() + "'");
   }

   /**
    * If same client subscribes multiple times on same message but wants
    * to suppress multi-updates (e.g. cluster node clients).
    */
   public void incrSubscribeCounter() {
      subscribeCounter++;
   }

   /**
    * If same client subscribes multiple times on same message but wants
    * to suppress multi-updates (e.g. cluster node clients).
    */
   public void decrSubscribeCounter() {
      subscribeCounter--;
   }

   /**
    * If same client subscribes multiple times on same message but wants
    * to suppress multi-updates (e.g. cluster node clients).
    */
   public int getSubscribeCounter() {
      return subscribeCounter;
   }
   /**
    * The session info of the client who initiated this subscription
    */
   public SessionInfo getSessionInfo()
   {
      return this.sessionInfo;
   }

   /**
    * My destination queue. 
    */
   public I_Queue getMsgQueue() {
      return this.sessionInfo.getSessionQueue();
   }

   /**
    * For this query subscription remember all resulted child subscriptions
    */
   public synchronized void addSubscription(SubscriptionInfo subs)
   {
      if (this.childrenVec == null) this.childrenVec = new Vector();
      this.childrenVec.addElement(subs);
   }

   /**
    * For this query subscription remember all resulted subscriptions
    */
   public synchronized void removeChildSubscription(SubscriptionInfo subs)
   {
      if (this.childrenVec == null) return;

      boolean found = this.childrenVec.remove(subs);
      
      if (!found) {
         log.error(ME, "Failed to remove XPATH children subscription " + uniqueKey);
         Thread.dumpStack();
         return;
      }

      if (log.TRACE) log.trace(ME, "Removed XPATH " + uniqueKey + " children subscription "); // + subs.getSubscriptionId());
   }

   /**
    * For this query subscription return all resulted subscriptions
    * @return null if not a query subscription with children
    */
   public synchronized SubscriptionInfo[] getChildrenSubscriptions() {
      if (this.childrenVec==null) return null;
      return (SubscriptionInfo[])this.childrenVec.toArray(new SubscriptionInfo[this.childrenVec.size()]);
   }

   public boolean isQuery() {
      return this.keyData.isQuery();
   }

   /**
    * @return true if it is an exact subscription. Not a 
    * query nor a domain subscriptions.
    */
   public boolean isExact() {
      return !isQuery() && !isCreatedByQuerySubscription();
   }

   /**
    * If true this is a child. It is automatically
    * generated by a query subscription.
    * @return
    */
   public boolean isCreatedByQuerySubscription() {
      return querySub != null;
   }

   protected void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect " + uniqueKey);
   }

   public AccessFilterQos[] getAccessFilterArr() {
      return subscribeQos.getAccessFilterArr();
   }

   /**
    * Clean up everything, since i will be deleted now.
    */
   private void erase() {
      if (log.TRACE) log.trace(ME, "Entering erase()");
      subscribeQos = null;
      // Keep keyData for further processing
      // Keep uniqueKey for further processing
   }

   /**
    * This must be called as soon as my TopicHandler handles me.
    * @param topicHandler I'm handled (lifetime) by this handler
    */
   public void addTopicHandler(TopicHandler topicHandler) {
      if (topicHandler == null) {
         Thread.dumpStack();
         log.error(ME, "addTopicHandler with topicHandler==null seems to be strange");
      }

      this.topicHandler = topicHandler;

      if (this.topicHandler != null) {
         if (log.TRACE) log.trace(ME, "Assign to SubscriptionInfo '" + uniqueKey + "' for client '" + sessionInfo.getId() + "' message '" + this.topicHandler.getUniqueKey() + "'");
      }
   }

   public TopicHandler getTopicHandler() {
      if (topicHandler == null) {
         Thread.dumpStack();
         log.error(ME, "addTopicHandler with topicHandler==null seems to be strange");
      }
      return topicHandler;
   }

   /**
    * Time when this Subscription is invoked.
    * @return the creation time of this subscription (in millis)
    */
   public long getCreationTime() {
      return this.creationTime;
   }

   /**
    * Telling my container that i am not subscribing any more.
    */
   void removeSubscribe() {
      if (topicHandler == null) {
         if (!isQuery()) {
            log.warn(ME, "The id=" + uniqueKey + " has no TopicHandler which takes care of it: " + toXml());
            Thread.dumpStack();
         }
         return;
      }
      topicHandler.removeSubscriber(uniqueKey);
      erase();
   }

   /**
    * @return The message wrapper object
    * @exception If no MsgUnitWrapper available
   public MsgUnitWrapper getMsgUnitWrapper() throws XmlBlasterException {
      if (topicHandler == null) {
         if (!isQuery()) {
            log.warn(ME, "Key oid=" + uniqueKey + " has no TopicHandler which takes care of it: " + toXml());
            Thread.dumpStack();
         }
         throw new XmlBlasterException(ME + ".NoMsgUnitWrapper", "Key oid=" + uniqueKey + " has no TopicHandler which takes care of it");
      }
      return topicHandler.getMsgUnitWrapper();
   }
    */

   /**
    * Compare method needed for Interface Comparable.
    *
    * This determines the sorting order, by which the
    * client receive their updates.
    * For now, the client which subscribed first, is served first
    */
   /*SORT_PROBLEM: Works fine with TreeSet, but with TreeMap i get the key here :-(
   public int compareTo(Object o)
   {
      SubscriptionInfo sub = (SubscriptionInfo)o;
      long diff = sub.getCreationTime() - getCreationTime();
      if (diff < 0L)
         return -1;
      else if (diff > 0L)
         return +1;
      else
         return 0;
   }
   */

   /**
    * Access on KeyData object
    * @return KeyData object
    */
   public KeyData getKeyData() {
      return keyData;
   }

   /**
    * The oid of the message we belong to
    */
   public String getKeyOid() {
      if (keyData != null) {
         return keyData.getOid();
      }
      return null;
   }

   /**
    * Access on SubscribeQosServer object
    * @return SubscribeQosServer object
    */
   public QueryQosData getQueryQosData() {
      if (this.subscribeQos == null) return null;
      return this.subscribeQos.getData();
   }

   /**
    * Supports limited reconfiguration with the given newQos. 
    * @param newQos The new QueryQosData to use
    */
   public void update(SubscribeQosServer newQos) {
      if (this.subscribeQos == null) {
         this.subscribeQos = newQos;
         if (log.TRACE) log.trace(ME, "Updated SubscribeQos for " + getId());
      }
      else {
         AccessFilterQos[] arr = newQos.getAccessFilterArr();
         if (log.TRACE) log.trace(ME, "Updated SubscribeQos AccessFilterArr for " + getId());
         this.subscribeQos.getData().setFilters(arr);
      }
      /*
      QuerySpecQos[] qarr = subscribeQos.getQuerySpecArr();
      if (qarr != null) {
         ...
      }
      */
   }

   public SubscribeQosServer getSubscribeQosServer() {
      return this.subscribeQos;
   }

   /**
    * Accessing a unique subscription id generated for this SubscriptionInfo. 
    * <p />
    * The key will be generated on first invocation
    * @return A unique key for this particular subscription
    */
   public String getSubscriptionId() {
      if (this.uniqueKey == null)
         throw new IllegalArgumentException(ME+".getSubscriptionId() is not initialized");
      return this.uniqueKey;
   }

   /**
    * For JMX the uniqueKey may not contain commas ','. 
    */
   private void initSubscriptionId() throws XmlBlasterException {
      if (this.uniqueKey == null) {
         if (this.querySub != null) {
            StringBuffer buf = new StringBuffer(126);
            Timestamp tt = new Timestamp();
            // Using prefix of my parent XPATH subscription object:
            buf.append(this.querySub.getSubscriptionId()).append(":").append(String.valueOf(tt.getTimestamp()));
            this.uniqueKey = buf.toString();
            if (log.TRACE) log.trace(ME, "Generated child subscription ID=" + this.uniqueKey);
         }
         else {
            this.uniqueKey = SubscriptionInfo.generateUniqueKey(keyData, subscribeQos.getData(), this.glob.useCluster());
            if (log.TRACE) log.trace(ME, "Generated subscription ID=" + this.uniqueKey);
         }
      }
   }

   /**
    * Accessing the unique subscription id from method subscribe(), which was the reason for this SubscriptionInfo
    * @return The subscription id which is used in updateQos - $lt;subscritpionId>
    */
   public String getSubSourceSubscriptionId() throws XmlBlasterException
   {
      if (querySub != null) {
         return querySub.getSubscriptionId();
      }
      return getSubscriptionId();
   }

   /**
    * Cleanup subscription. 
    */
   public void shutdown() throws XmlBlasterException
   {
      this.glob.unregisterMBean(this.mbeanHandle);
      if (querySub != null) {
         querySub.removeChildSubscription(this);
      }
   }

   /**
    * Test if this id is a subscribeId (starts with "__subId:")
    */
   static boolean isSubscribeId(String id)
   {
      if (id == null) return false;
      return id.startsWith(Constants.SUBSCRIPTIONID_PREFIX) ? true : false;
   }

   /**
    * This static method may be used from external objects to get the unique key
    * of a subscription. 
    * <p />
    * For JMX the uniqueKey may not contain commas ','. 
    *
    * @param clusterWideUnique If false the key is unique for this xmlBlaster instance only
    * @return A unique key for this particular subscription, for example:<br>
    *         <code>__subId:heron-53</code>
    * @see org.xmlBlaster.util.key.QueryKeyData#generateSubscriptionId(String)
    */
   private static String generateUniqueKey(KeyData keyData, QueryQosData xmlQos, boolean clusterWideUnique) throws XmlBlasterException {
      if (xmlQos.getSubscriptionId() != null && xmlQos.getSubscriptionId().length() > 0) {
         return xmlQos.getSubscriptionId(); // Client forced his own key
      }
      StringBuffer buf = new StringBuffer(126);
      buf.append(Constants.SUBSCRIPTIONID_PREFIX);
      if (clusterWideUnique)
         buf.append(keyData.getGlobal().getNodeId().getId()).append("-");
      if (keyData.isQuery())
         buf.append(keyData.getQueryType());
      Timestamp tt = new Timestamp();
      buf.append(String.valueOf(tt.getTimestamp()));
      return buf.toString();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of SubscriptionInfo
    */
   public String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of SubscriptionInfo
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(2048);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<subscription id='").append(getSubscriptionId()).append("'");
      sb.append(" sessionName='").append(getSessionInfo().getSessionName()).append("'");
      if (this.topicHandler != null) {
         sb.append(" oid='").append(topicHandler.getUniqueKey()).append("'");
      }
      if (this.querySub != null) {
         sb.append(" parent='").append(this.querySub.getSubscriptionId()).append("'");
      }
      SubscriptionInfo[] childrenSubs = getChildrenSubscriptions();
      if (childrenSubs != null) {
         sb.append(" numChilds='").append(childrenSubs.length).append("'");
      }
      sb.append(" creationTime='" + TimeHelper.getDateTimeDump(this.creationTime) + "'");
      sb.append(">");

      //sb.append(offset).append("<SubscriptionInfo id='").append(getSubscriptionId()).append("'>");
      //sb.append(offset + "   <keyData oid='" + (keyData==null ? "null" : keyData.getUniqueKey()) + "'/>");
      if (keyData != null)
         sb.append(keyData.toXml(extraOffset+Constants.INDENT));
      if (subscribeQos != null) 
         sb.append(subscribeQos.toXml(extraOffset+Constants.INDENT));
      else 
         sb.append(extraOffset+Constants.INDENT).append("<!-- subscribe qos is null ERROR -->");
      //sb.append(offset).append(" <topicHandler id='").append((topicHandler==null ? "null" : topicHandler.getUniqueKey())).append("'/>");
      //sb.append(offset).append(" <creationTime>").append(TimeHelper.getDateTimeDump(this.creationTime)).append("</creationTime>");
      if (childrenSubs != null) {
         for (int ii=0; ii<childrenSubs.length; ii++) {
            sb.append(offset).append(" <child>").append(childrenSubs[ii].getSubscriptionId()).append("</child>");
         }
      }
      sb.append(offset).append("</subscription>");
      return sb.toString();
   }

  /**
    * Dump state of this object into XML.
    * <pre>
    * &lt;subscribe id='_subId:1' sessionName='/node/heron/client/joe/-2' oid='HelloWorld' parent='_sub:XPATH-2'/>
    * </pre>
    * @param extraOffset indenting of tags
    * @return XML state of SubscriptionInfo
    */
   public String toXmlSmall(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("  <subscription id='").append(getSubscriptionId()).append("'");
      sb.append(" sessionName='").append(getSessionInfo().getSessionName()).append("'");
      if (this.topicHandler != null) {
         sb.append(" oid='").append(topicHandler.getUniqueKey()).append("'");
      }
      if (this.querySub != null) {
         sb.append(" parent='").append(this.querySub.getSubscriptionId()).append("'");
      }
      if (this.childrenVec != null) {
         sb.append(" numChilds='").append(this.childrenVec.size()).append("'");
      }
      sb.append(" creationTime='" + TimeHelper.getDateTimeDump(this.creationTime) + "'");
      sb.append("/>");
      return sb.toString();
   }

   /**
    * Gets the uniqueId for the persistence of this session.
    * @return the uniqueId used to identify this session as an  entry
    * in the queue where it is stored  (for persistent subscriptions).
    * If the session is not persistent it returns -1L.
    * 
    */
   public final long getPersistenceId() {
      return this.persistenceId;
   }
   
   /**
    * Sets the uniqueId used to retrieve this session from the persistence
    * @param persistenceId
    */
   public final void setPersistenceId(long persistenceId) {
      this.persistenceId = persistenceId;
   }
   
//++++++++++ Enforced by I_AdminSubscription ++++++++++++++++
   public String getId() {
      return getSubscriptionId();
   }
   public String getSessionName() {
      if (this.sessionInfo == null) return "";
      return sessionInfo.getId();
   }
   public String getTopicId() {
      if (this.topicHandler == null) return "";
      return this.topicHandler.getId();
   }
   public String getParentSubscription() {
      if (this.querySub == null) return "";
      return this.querySub.getSubscriptionId();
   }
   public String getCreationTimestamp() {
      return org.jutils.time.TimeHelper.getDateTimeDump(this.creationTime);
   }
   public String getSubscribeQosStr() {
      return (this.subscribeQos==null) ? "" : this.subscribeQos.toXml();
   }
   public String[] getAccessFilters() {
      AccessFilterQos[] arr = subscribeQos.getAccessFilterArr();
      if (arr == null) return new String[0];
      String[] ret = new String[arr.length];
      for (int i=0; i<arr.length; i++) {
         ret[i] = arr[i].toXml();
      }
      return ret;
   }
   public synchronized String[] getDependingSubscriptions() {
      if (this.childrenVec==null || this.childrenVec.size() < 1) return new String[0];
      String[] ret = new String[this.childrenVec.size()];
      for (int i=0; i<this.childrenVec.size(); i++) {
         SubscriptionInfo info = (SubscriptionInfo)this.childrenVec.elementAt(i);
         ret[i] = info.toXml();
      }
      return ret;
   }
}
