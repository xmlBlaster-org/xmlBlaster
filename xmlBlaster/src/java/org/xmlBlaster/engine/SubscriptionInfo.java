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
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.jutils.time.TimeHelper;
import java.util.Vector;


/**
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MsgUnit of exactly one Client. 
 * <p />
 */
public class SubscriptionInfo /* implements Comparable see SORT_PROBLEM */
{
   private String ME = "SubscriptionInfo";

   /** The global handle */
   private Global glob;
   /** Logging to channel "core" */
   private LogChannel log;
   /** The initiatior of this subscription */
   private SessionInfo sessionInfo;
   /** reference to keyData */
   private KeyData keyData;
   /** reference to 'Quality of Service' of subscribe() / unSubscribe() */
   private QueryQosData subscribeQos = null;
   /** The unique key of a subscription (subscriptionId), which is a function of f(keyData,xmlQos). <br />
       This is the returned id of a subscribe() invocation */
   private String uniqueKey=null;
   /** reference to my managing container */
   private TopicHandler myHandler;
   /** A reference to the query subscription (XPATH), which created this subscription
       If the subscription was EXACT, querySub is null */
   private SubscriptionInfo querySub = null;
   /** It it is a query subscription, we remember all subscriptions which resulted from this query */
   private Vector childrenVec = null;

   /** If duplicateUpdates=false is set we can check here how often this message is
       subscribed from the same client */
   private int subscribeCounter = 0; // is incr/decr by fireSubscribeEvent() and fireUnSubscribeEvent()

   private static long uniqueCounter = 1L;

   private long creationTime = System.currentTimeMillis();

   /**
    * Use this constructor for an exact subscription.
    * @param sessionInfo The session which initiated this subscription
    * @param keyData     The message meta info
    * @param qos         This may be a SubscribeQosServer or a UnSubscribeQosServer instance
    */
   public SubscriptionInfo(Global glob, SessionInfo sessionInfo, KeyData keyData, QueryQosData qos) throws XmlBlasterException {
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
      init(glob, sessionInfo, keyData, querySub.getQueryQosData());
   }

   private void init(Global glob, SessionInfo sessionInfo, KeyData keyData, QueryQosData qos) throws XmlBlasterException {
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

      getSubscriptionId(); // initialize the unique id this.uniqueKey
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
   public final SessionInfo getSessionInfo()
   {
      return this.sessionInfo;
   }

   /**
    * My destination queue. 
    */
   public final I_Queue getMsgQueue() {
      return this.sessionInfo.getSessionQueue();
   }

   /**
    * For this query subscription remember all resulted child subscriptions
    */
   public final void addSubscription(SubscriptionInfo subs)
   {
      if (childrenVec == null) childrenVec = new Vector();
      childrenVec.addElement(subs);
   }

   /**
    * For this query subscription remember all resulted subscriptions
    */
   public final void removeChildSubscription(SubscriptionInfo subs)
   {
      if (childrenVec == null) return;

      boolean found = childrenVec.remove(subs);
      
      if (!found) {
         log.error(ME, "Failed to remove XPATH children subscription " + uniqueKey);
         Thread.currentThread().dumpStack();
         return;
      }

      if (log.TRACE) log.trace(ME, "Removed XPATH " + uniqueKey + " children subscription "); // + subs.getSubscriptionId());
   }

   /**
    * For this query subscription return all resulted subscriptions
    * @return null if not a query subscription with children
    */
   public final Vector getChildrenSubscriptions() {
      return childrenVec;
   }

   public boolean isQuery() {
      return this.keyData.isQuery();
   }

   public boolean isCreatedByQuerySubscription() {
      return querySub != null;
   }

   protected void finalize() {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect " + uniqueKey);
   }

   public final AccessFilterQos[] getAccessFilterArr() {
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
    * @param myHandler I'm handled (lifetime) by this handler
    */
   public final void addTopicHandler(TopicHandler myHandler) {
      if (myHandler == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "addTopicHandler with myHandler==null seems to be strange");
      }

      this.myHandler = myHandler;

      if (this.myHandler != null) {
         if (log.TRACE) log.trace(ME, "Assign to SubscriptionInfo '" + uniqueKey + "' for client '" + sessionInfo.getId() + "' message '" + this.myHandler.getUniqueKey() + "'");
      }
   }

   public final TopicHandler getTopicHandler() {
      if (myHandler == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "addTopicHandler with myHandler==null seems to be strange");
      }
      return myHandler;
   }

   /**
    * Time when this Subscription is invoked.
    * @return the creation time of this subscription (in millis)
    */
   public final long getCreationTime() {
      return creationTime;
   }

   /**
    * Telling my container that i am not subscribing any more.
    */
   final void removeSubscribe() {
      try {
         if (myHandler == null) {
            if (!isQuery()) {
               log.warn(ME, "The id=" + uniqueKey + " has no TopicHandler which takes care of it: " + toXml());
               Thread.dumpStack();
            }
            return;
         }
      } catch(XmlBlasterException e) {}
      myHandler.removeSubscriber(uniqueKey);
      erase();
   }

   /**
    * @return The message wrapper object
    * @exception If no MsgUnitWrapper available
   public final MsgUnitWrapper getMsgUnitWrapper() throws XmlBlasterException {
      if (myHandler == null) {
         if (!isQuery()) {
            log.warn(ME, "Key oid=" + uniqueKey + " has no TopicHandler which takes care of it: " + toXml());
            Thread.dumpStack();
         }
         throw new XmlBlasterException(ME + ".NoMsgUnitWrapper", "Key oid=" + uniqueKey + " has no TopicHandler which takes care of it");
      }
      return myHandler.getMsgUnitWrapper();
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
   public final KeyData getKeyData() {
      return keyData;
   }

   /**
    * The oid of the message we belong to
    */
   public final String getKeyOid() {
      if (keyData != null) {
         return keyData.getOid();
      }
      return null;
   }

   /**
    * Access on SubscribeQosServer object
    * @return SubscribeQosServer object
    */
   public final QueryQosData getQueryQosData() {
      return subscribeQos;
   }

   /**
    * Accessing a unique subscription id generated for this SubscriptionInfo. 
    * <p />
    * The key will be generated on first invocation
    * @return A unique key for this particular subscription
    */
   public final String getSubscriptionId() throws XmlBlasterException {
      if (this.uniqueKey == null) {
         if (querySub != null) {
            StringBuffer buf = new StringBuffer(126);
            synchronized (SubscriptionInfo.class) {
               uniqueCounter++;
               // Using prefix of my parent XPATH subscription object:
               buf.append(querySub.getSubscriptionId()).append(":").append(uniqueCounter);
            }
            this.uniqueKey = buf.toString();
            if (log.TRACE) log.trace(ME, "Generated child subscription ID=" + this.uniqueKey);
         }
         else {
            this.uniqueKey = SubscriptionInfo.generateUniqueKey(keyData, subscribeQos).toString();
            if (log.TRACE) log.trace(ME, "Generated subscription ID=" + this.uniqueKey);
         }
      }
      return this.uniqueKey;
   }

   /**
    * Accessing the unique subscription id from method subscribe(), which was the reason for this SubscriptionInfo
    * @return The subscription id which is used in updateQos - $lt;subscritpionId>
    */
   public final String getSubSourceSubscriptionId() throws XmlBlasterException
   {
      if (querySub != null) {
         return querySub.getSubscriptionId();
      }
      return getSubscriptionId();
   }

   /**
    * Cleanup subscription. 
    */
   public final void shutdown() throws XmlBlasterException
   {
      if (querySub != null) {
         querySub.removeChildSubscription(this);
      }
   }

   /**
    * Test if this id is a subscribeId (starts with "__subId:")
    */
   static boolean isSubscribeId(String id)
   {
      return id.startsWith(Constants.SUBSCRIPTIONID_PREFIX) ? true : false;
   }

   /**
    * This static method may be used from external objects to get the unique key
    * of a subscription. The key is only unique for this xmlBlaster instance. 
    * <p />
    * @return A unique key for this particular subscription, for example:<br>
    *         <code>53</code>
    */
   private static final String generateUniqueKey(KeyData keyData, QueryQosData xmlQos) throws XmlBlasterException {
      if (xmlQos.getSubscriptionId() != null && xmlQos.getSubscriptionId().length() > 0) {
         return xmlQos.getSubscriptionId(); // Client forced his own key
      }
      StringBuffer buf = new StringBuffer(126);
      synchronized (SubscriptionInfo.class) {
         uniqueCounter++;
         if (keyData.isQuery())
            buf.append(Constants.SUBSCRIPTIONID_PREFIX).append(keyData.getQueryType()).append(uniqueCounter);
         else
            buf.append(Constants.SUBSCRIPTIONID_PREFIX).append(uniqueCounter);
      }
      return buf.toString();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of SubscriptionInfo
    */
   public final String toXml() throws XmlBlasterException {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of SubscriptionInfo
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<SubscriptionInfo id='" + getSubscriptionId() + "'>");
      //sb.append(offset + "   <keyData oid='" + (keyData==null ? "null" : keyData.getUniqueKey()) + "'/>");
      if (keyData != null)
         sb.append(keyData.toXml(extraOffset + "   ").toString());
      sb.append(subscribeQos.toXml(extraOffset + "   ").toString());
      sb.append(offset + "   <topicHandler id='" + (myHandler==null ? "null" : myHandler.getUniqueKey()) + "'/>");
      sb.append(offset + "   <creationTime>" + TimeHelper.getDateTimeDump(creationTime) + "</creationTime>");
      if (childrenVec != null) {
         for (int ii=0; ii<childrenVec.size(); ii++) {
            SubscriptionInfo child = (SubscriptionInfo)childrenVec.elementAt(ii);
            sb.append(offset).append("   <child>").append(child.getSubscriptionId()).append("</child>");
         }
      }
      sb.append(offset + "</SubscriptionInfo>\n");
      return sb.toString();
   }
}
