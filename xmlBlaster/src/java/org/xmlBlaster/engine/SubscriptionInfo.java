/*------------------------------------------------------------------------------
Name:      SubscriptionInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handles exactly one subscritpion (client reference and QoS of this subscrition
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.SubscribeQoS;
import org.xmlBlaster.engine.xml2java.UnSubscribeQoS;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.jutils.time.TimeHelper;
import java.util.Vector;


/**
 * This is just a container to hold references on all interesting data
 * concerning a subscription of exactly one MessageUnit of exactly one Client. 
 * <p />
 */
public class SubscriptionInfo /* implements Comparable see SORT_PROBLEM */
{
   private String ME = "SubscriptionInfo";

   /** The global handle */
   private Global glob;
   /** Logging to channel "core" */
   private final LogChannel log;
   /** The initiatior of this subscription */
   private SessionInfo sessionInfo;
   /** reference on MsgQueue */
   private MsgQueue msgQueue;
   /** reference to xmlKey */
   private XmlKey xmlKey;
   /** reference to 'Quality of Service' base class */
   private XmlQoSBase xmlQoSBase = null;
   /** reference to 'Quality of Service' of subscription */
   private SubscribeQoS subscribeQos = null;
   /** reference to 'Quality of Service' of unsubscription */
   private UnSubscribeQoS unSubscribeQos = null;
   /** The unique key of a subscription, which is a function of f(msgQueue,xmlKey,xmlQoS). <br />
       This is the returned id of a subscribe() invocation */
   private String uniqueKey=null;
   /** reference to my managing container */
   private MessageUnitHandler myHandler;
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
    * @param msgQueue   The client (data sink) we deal with
    * @param xmlKey     The message meta info
    * @param qos        This may be a SubscribeQoS or a UnSubscribeQoS instance (very bad hack!)
    */
   public SubscriptionInfo(Global glob, SessionInfo sessionInfo, MsgQueue msgQueue, XmlKey xmlKey, XmlQoSBase qos) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = this.glob.getLog("core");
      init(sessionInfo, msgQueue, xmlKey, qos);
   }

   /**
    * Use this constructor it the subscription is a result of a XPath subscription
    * @param sessionInfo The session which initiated this subscription
    * @param msgQueue   The client we deal with
    * @param xmlKey     The message meta info
    * @param qos        This may be a SubscribeQoS or a UnSubscribeQoS instance (very bad hack!)
    */
   public SubscriptionInfo(Global glob, SessionInfo sessionInfo, SubscriptionInfo querySub, XmlKey xmlKey) throws XmlBlasterException
   {
      this.glob = glob;
      this.log = this.glob.getLog("core");
      this.querySub = querySub;
      init(sessionInfo, querySub.getMsgQueue(), xmlKey, querySub.getSubscribeQoS());
   }

   private void init(SessionInfo sessionInfo, MsgQueue msgQueue, XmlKey xmlKey, XmlQoSBase qos) throws XmlBlasterException
   {
      this.sessionInfo = sessionInfo;
      this.msgQueue = msgQueue;
      this.xmlKey = xmlKey;
      getUniqueKey(); // initialize the unique id this.uniqueKey

      // very bad hack, needs redesign (SubscribeQoS or UnSubscribeQoS are handled here)
      if (qos instanceof SubscribeQoS) {
         this.subscribeQos = (SubscribeQoS)qos;

         AccessFilterQos[] filterQos = this.subscribeQos.getFilterQos();
         if (filterQos != null) {
            for (int ii=0; ii<filterQos.length; ii++) {
               this.glob.getRequestBroker().getAccessPluginManager().addAccessFilterPlugin(
                       filterQos[ii].getType(), filterQos[ii].getVersion());
            }
         }
      }
      else
         this.unSubscribeQos = (UnSubscribeQoS)qos;

      if (log.TRACE) log.trace(ME, "Created SubscriptionInfo '" + getUniqueKey() + "' for client '" + sessionInfo.getLoginName() + "'");
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

      if (log.TRACE) log.trace(ME, "Removed XPATH " + uniqueKey + " children subscription "); // + subs.getUniqueKey());
   }

   /**
    * For this query subscription return all resulted subscriptions
    * @return null if not a query subscription with children
    */
   public final Vector getChildrenSubscriptions()
   {
      return childrenVec;
   }

   public boolean isQuery() throws XmlBlasterException
   {
      return getXmlKey().isQuery();
   }

   public boolean isCreatedByQuerySubscription()
   {
      return querySub != null;
   }

   protected void finalize()
   {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect " + uniqueKey);
   }

   public final AccessFilterQos[] getFilterQos()
   {
      return subscribeQos.getFilterQos();
   }

   /**
    * Clean up everything, since i will be deleted now.
    */
   private void erase()
   {
      if (log.TRACE) log.trace(ME, "Entering erase()");
      // msgQueue = null; not my business
      xmlQoSBase = null;
      subscribeQos = null;
      unSubscribeQos = null;
      // Keep xmlKey for further processing
      // Keep uniqueKey for further processing
   }

   /**
    * This must be called as soon as my MessageUnitHandler handles me.
    * @param myHandler I'm handled (lifetime) by this handler
    */
   public final void addMessageUnitHandler(MessageUnitHandler myHandler)
   {
      if (myHandler == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "addMessageUnitHandler with myHandler==null seems to be strange");
      }

      this.myHandler = myHandler;

      if (this.myHandler != null) {
         if (log.TRACE) log.trace(ME, "Assign to SubscriptionInfo '" + uniqueKey + "' for client '" + sessionInfo.getLoginName() + "' message '" + this.myHandler.getUniqueKey() + "'");
      }
   }

   public final MessageUnitHandler getMessageUnitHandler()
   {
      if (myHandler == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "addMessageUnitHandler with myHandler==null seems to be strange");
      }
      return myHandler;
   }

   /**
    * Time when this Subscription is invoked.
    * @return the creation time of this subscription (in millis)
    */
   public final long getCreationTime()
   {
      return creationTime;
   }

   /**
    * Telling my container that i am not subscribing any more.
    */
   final void removeSubscribe()
   {
      try {
         if (myHandler == null) {
            if (!getXmlKey().isQuery()) {
               log.warn(ME, "The id=" + uniqueKey + " has no MessageUnitHandler which takes care of it: " + toXml());
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
    * @exception If no MessageUnitWrapper available
    */
   public final MessageUnitWrapper getMessageUnitWrapper() throws XmlBlasterException
   {
      if (myHandler == null) {
         if (!getXmlKey().isQuery()) {
            log.warn(ME, "Key oid=" + uniqueKey + " has no MessageUnitHandler which takes care of it: " + toXml());
            Thread.dumpStack();
         }
         throw new XmlBlasterException(ME + ".NoMessageUnitWrapper", "Key oid=" + uniqueKey + " has no MessageUnitHandler which takes care of it");
      }
      return myHandler.getMessageUnitWrapper();
   }

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
    * Access on MsgQueue object
    * @return MsgQueue object
    */
   public final MsgQueue getMsgQueue()
   {
      return msgQueue;
   }

   /**
    * Access on XmlKey object
    * @return XmlKey object
    */
   public final XmlKey getXmlKey()
   {
      return xmlKey;
   }

   /**
    * The oid of the message we belong to
    */
   public final String getKeyOid()
   {
      if (xmlKey != null) {
         try {
            return xmlKey.getUniqueKey();
         }
         catch (XmlBlasterException e) {
            return null;
         }
      }
      return null;
   }

   /**
    * Access on SubscribeQoS object
    * @return SubscribeQoS object
    */
   public final SubscribeQoS getSubscribeQoS()
   {
      return subscribeQos;
   }

   /**
    * Accessing a unique id generated for this SubscriptionInfo. 
    * <p />
    * The key will be generated on first invocation
    * @return A unique key for this particular subscription
    */
   public final String getUniqueKey() throws XmlBlasterException
   {
      if (this.uniqueKey == null) {
         if (querySub != null) {
            StringBuffer buf = new StringBuffer(126);
            synchronized (SubscriptionInfo.class) {
               uniqueCounter++;
               // Using praefix of my parent XPATH subscription object:
               buf.append(querySub.getUniqueKey()).append(":").append(uniqueCounter);
            }
            this.uniqueKey = buf.toString();
            if (log.TRACE) log.trace(ME, "Generated child subscription ID=" + this.uniqueKey);
         }
         else {
            this.uniqueKey = SubscriptionInfo.generateUniqueKey(msgQueue, xmlKey, xmlQoSBase).toString();
            if (log.TRACE) log.trace(ME, "Generated subscription ID=" + this.uniqueKey);
         }
      }
      return this.uniqueKey;
   }

   /**
    * Accessing the unique subscription id from method subscribe(), which was the reason for this SubscriptionInfo
    * @return The subscription id which is used in updateQos - $lt;subscritpionId>
    */
   public final String getSubSourceUniqueKey() throws XmlBlasterException
   {
      if (querySub != null) {
         return querySub.getUniqueKey();
      }
      return getUniqueKey();
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
      return id.startsWith(Constants.SUBSCRIPTIONID_PRAEFIX) ? true : false;
   }

   /**
    * This static method may be used from external objects to get the unique key
    * of a subscription. The key is only unique for this xmlBlaster instance. 
    * <p />
    * @return A unique key for this particular subscription, for example:<br>
    *         <code>53</code>
    */
   private static final String generateUniqueKey(MsgQueue msgQueue, XmlKey xmlKey, XmlQoSBase xmlQoS) throws XmlBlasterException
   {
      StringBuffer buf = new StringBuffer(126);
      synchronized (SubscriptionInfo.class) {
         uniqueCounter++;
         if (xmlKey.isQuery())
            buf.append(Constants.SUBSCRIPTIONID_PRAEFIX).append(xmlKey.getQueryTypeStr()).append(uniqueCounter);
         else
            buf.append(Constants.SUBSCRIPTIONID_PRAEFIX).append(uniqueCounter);
      }
      return buf.toString();
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of SubscriptionInfo
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of SubscriptionInfo
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<SubscriptionInfo id='" + getUniqueKey() + "'>");
      sb.append(offset + "   <msgQueue id='" + (msgQueue==null ? "null" : msgQueue.getName()) + "'/>");
      //sb.append(offset + "   <xmlKey oid='" + (xmlKey==null ? "null" : xmlKey.getUniqueKey()) + "'/>");
      if (xmlKey != null)
         sb.append(xmlKey.printOn(extraOffset + "   ").toString());
      if (subscribeQos != null)
         sb.append(subscribeQos.toXml(extraOffset + "   ").toString());
      else
         sb.append(offset + "   <SubscribeQos></SubscribeQos>");
      if (unSubscribeQos != null)
         sb.append(unSubscribeQos.printOn(extraOffset + "   ").toString());
      else
         sb.append(offset + "   <UnSubscribeQos></UnSubscribeQos>");
      sb.append(offset + "   <msgUnitHandler id='" + (myHandler==null ? "null" : myHandler.getUniqueKey()) + "'/>");
      sb.append(offset + "   <creationTime>" + TimeHelper.getDateTimeDump(creationTime) + "</creationTime>");
      if (childrenVec != null) {
         for (int ii=0; ii<childrenVec.size(); ii++) {
            SubscriptionInfo child = (SubscriptionInfo)childrenVec.elementAt(ii);
            sb.append(offset).append("   <child>").append(child.getUniqueKey()).append("</child>");
         }
      }
      sb.append(offset + "</SubscriptionInfo>\n");
      return sb.toString();
   }
}
