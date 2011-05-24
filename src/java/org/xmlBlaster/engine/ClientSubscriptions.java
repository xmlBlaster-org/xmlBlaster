/*------------------------------------------------------------------------------
Name:      ClientSubscriptions.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.ServerScope;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.Constants;

import java.util.*;


/**
 * Handling subscriptions, collected for each session of each client. 
 * <p />
 * There exists exactly one instance of this class for each xmlBlaster server,
 * the instance is handled by RequestBroker.
 * <p />
 * The interface SubscriptionListener informs about subscribe/unsubscribe events
 * @author Marcel Ruff
 */
public class ClientSubscriptions implements I_ClientListener, I_SubscriptionListener
{
   private static Logger log = Logger.getLogger(ClientSubscriptions.class.getName());

   /**
    * All exact subscriptions of a Client are in this map.
    * <p>
    * These are the subscriptions which are referenced from a TopicHandler<br>
    * including those with a query (XPATH).
    * <p>
    * A multimap would be appropriate, but since this is not supported
    * by the Collections API, a map with a set as value is used.
    * <br>
    * Used for performing logout.
    * <p>
    * key   = client.getSessionName().getRelativeName()
    * value = subMap (Collections.synchronizedMap(new HashMap());)
    *         with SubscriptionInfo objects
    */
   final private Map clientSubscriptionMap = Collections.synchronizedMap(new HashMap());


   /**
    * All generic subscriptions are collected here.
    * Generic are all subscriptions who don't subscribe a precise key-oid,
    * but rather subscribe all MsgUnit matching a XPath query match.
    * <br>
    * If new MsgUnit are published, this set is consulted to check
    * if some older subscriptions would match as well
    * <p>
    * value = SubscriptionInfo objects with generic subscriptions, but not
    *         those, who subscribed a MsgUnit exactly by a known oid
    */
   final private Set querySubscribeRequestsSet = Collections.synchronizedSet(new HashSet());


   /**
    * Exactly one instance for each xmlBlaster server.
    * <p />
    * (no singleton pattern to allow multiple servers)
    * @param requestBroker my master (singleton)
    * @param authenticate another master
    */
   ClientSubscriptions(ServerScope glob, RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      requestBroker.addSubscriptionListener(this);
      authenticate.addClientListener(this);
   }


   /**
    * All known subscriptions which match a query,
    * but not those subscriptions which address exactly via key-oid
    */
   public Set getQuerySubscribeRequestsSet()
   {
      return querySubscribeRequestsSet;
   }

   /**
    * If you have the unique id of a subscription, you may access the
    * SubscriptionInfo object here.
    * <p />
    * You can access XPATH or EXACT subscription objects through this method
    * @param sessionInfo All infos about the client
    * @param subscriptionInfoUniqueKey
    * @return corresponding subscriptionInfo object<br />
    *         or null if not found
    */
   public SubscriptionInfo getSubscription(SessionInfo sessionInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      Object obj;
      Map subMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(sessionInfo.getSessionName().getRelativeName());
         if (obj == null)
            return null;
         subMap = (Map)obj;
      }

      SubscriptionInfo subs = (SubscriptionInfo)subMap.get(subscriptionInfoUniqueKey);
      if (log.isLoggable(Level.FINE)) log.fine("Looking for subscriptionId=" + subscriptionInfoUniqueKey + " found " + subs);
      return subs;
   }

   /**
    * If you have the unique id of a subscription, you may access the
    * SubscriptionInfo object here.
    * <p />
    * You can access XPATH or EXACT subscription objects through this method
    * @param subscriptionInfoUniqueKey
    * @return corresponding subscriptionInfo object<br />
    *         or null if not found
    */
   public SubscriptionInfo getSubscription(String subscriptionInfoUniqueKey) {
      synchronized(this.clientSubscriptionMap) {
         Iterator iterator = this.clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Object obj = subMap.get(subscriptionInfoUniqueKey); 
               if (obj != null) {
                  return (SubscriptionInfo)obj;
               }
            }
         }
      }

      if (log.isLoggable(Level.FINE)) log.fine("subscriptionId=" + subscriptionInfoUniqueKey + " not found");
      return null;
   }

   /**
    * @return The number of all subscriptions in this cluster node.
    */
   public int getNumSubscriptions() {
      int num = 0;
      synchronized(this.clientSubscriptionMap) {
         Iterator iterator = this.clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               num += subMap.size();
            }
         }
      }
      return num;
   }

   /**
    * @return All subscriptionId in a comma separated string
    */
   public String getSubscriptionList() {
      StringBuffer sb = new StringBuffer(getNumSubscriptions()*20);
      synchronized(this.clientSubscriptionMap) {
         Iterator iterator = this.clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Iterator iterator2 = subMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  if (sb.length() > 0)
                     sb.append(",");
                  sb.append(sub.getSubscriptionId());
               }
            }
         }
      }
      return sb.toString();
   }
   public String[] getSubscriptions() {
      ArrayList list = new ArrayList(10+this.clientSubscriptionMap.size());
      synchronized(this.clientSubscriptionMap) {
         Iterator iterator = this.clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Iterator iterator2 = subMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  list.add(sub.getSubscriptionId());
               }
            }
         }
      }
      return (String[])list.toArray(new String[list.size()]);
   }

   /**
    * Access all subscriptions of a client
    * @return never null
    */
   public SubscriptionInfo[] getSubscriptions(SessionInfo sessionInfo) {
      synchronized(clientSubscriptionMap) {
         Object obj = clientSubscriptionMap.get(sessionInfo.getSessionName().getRelativeName());
         if (obj == null)
            return new SubscriptionInfo[0];
         java.util.Collection c = ((Map)obj).values();
         return (SubscriptionInfo[])c.toArray(new SubscriptionInfo[c.size()]);
      }
   }


   /*
    * If you have the key oid, the xpath query or domain of a message and a session it belongs to,
    * you may access the SubscriptionInfo object here.
    * <p />
    * Note that TopicHandler.findSubscriber() will not return a SubscriptionInfo
    * if never a message arrived for such a subscription, so prefer this method.
    *
    * @param sessionInfo All infos about the client
    * @param queryKey The query to look for
    * @return Vector containing corresponding subscriptionInfo objects<br />
    *         is > 1 if this session has subscribed multiple times on the
    *         same message, or null if this session has not subscribed it
    */
   public ArrayList<SubscriptionInfo> getSubscription(SessionInfo sessionInfo, QueryKeyData queryKey) throws XmlBlasterException {
      if (queryKey == null || sessionInfo==null) return null;
      Object obj;
      Map subMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(sessionInfo.getSessionName().getRelativeName());
         if (obj == null) {
            if (log.isLoggable(Level.FINE)) log.fine("Session '" + sessionInfo.getId() + "' is unknown, there was no subscription of this session yet.");
            return null;
         }
         subMap = (Map)obj;
      }

      // Slow linear search of all subscribes of a session
      // Don't use for performance critical tasks
      ArrayList<SubscriptionInfo> vec = null;
      synchronized (subMap) {
         Iterator iterator = subMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
//            if (queryKey.isXPath()) {
//            	log.info("getQueryString="+queryKey.getQueryString() + " <-> " + sub.getKeyData().getQueryString());
//            	log.info("");
//            	
//            }
            if (queryKey.equals(sub.getKeyData())) {
               if (vec == null) vec = new ArrayList<SubscriptionInfo>();
               vec.add(sub);
            }
         }
      }
      return vec;
   }


   /*
    * If you have the key oid of a message and a session it belongs to, you may access the
    * SubscriptionInfo object here.
    * <p />
    * Note that TopicHandler.findSubscriber() will not return a SubscriptionInfo
    * if never a message arrived for such a subscription, so prefer this method.
    *
    * @param sessionInfo All infos about the client
    * @param keyOid The unique message oid
    * @param exact true Access only EXACT subscription objects through this method.<br />
    *              false All subscriptions
    * @return Vector containing corresponding subscriptionInfo objects<br />
    *         is > 1 if this session has subscribed multiple times on the
    *         same message, or null if this session has not subscribed it
    */
   public Vector<SubscriptionInfo> getSubscriptionByOid(SessionInfo sessionInfo, String keyOid, boolean exactOnly) throws XmlBlasterException {
      if (keyOid == null || sessionInfo==null) return null;
      Object obj;
      Map subMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(sessionInfo.getSessionName().getRelativeName());
         if (obj == null) {
            if (log.isLoggable(Level.FINE)) log.fine("Session '" + sessionInfo.getId() + "' is unknown, there was no subscription of this session yet. Oid '" + keyOid + "' is not existing");
            return null;
         }
         subMap = (Map)obj;
      }

      //if (log.isLoggable(Level.FINE)) log.trace(ME, "Found subscription map with " + subMap.size() + " entries for '" + sessionInfo.getId() + "'");

      // Slow linear search of all subscribes of a session
      // Don't use for performance critical tasks
      Vector vec = null;
      synchronized (subMap) {
         Iterator iterator = subMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (exactOnly && sub.getKeyData().isQuery()) {
               if (log.isLoggable(Level.FINE)) log.fine("Ignoring subscription " + sub.getSubscriptionId() +
                      " for session '" + sessionInfo.getId() + "' for message '" + keyOid + "'");
               continue;
            }
            if (keyOid.equals(sub.getKeyOid())) {
               if (log.isLoggable(Level.FINE)) log.fine("Found subscription " + sub.getSubscriptionId() +
                      " for session '" + sessionInfo.getId() + "' for message '" + keyOid + "'");
               if (vec == null) vec = new Vector();
               vec.addElement(sub);
            }
         }
      }
      return vec;
   }


   /*
    * If you have the key oid of a message, you can access all SubscriptionInfo objects
    * of all sessions here.
    * <p />
    * Note that TopicHandler.findSubscriber() will not return a SubscriptionInfo
    * if never a message arrived for such a subscription, so prefer this method.
    *
    * @param keyOid The unique message oid
    * @param exact true Access only EXACT subscription objects through this method.<br />
    *              false All subscriptions
    * @return Vector containing corresponding subscriptionInfo objects or null
    */
   public Vector getSubscriptionByOid(String keyOid, boolean exactOnly) throws XmlBlasterException {
      Vector vec = null;
      // Slow linear search of SubscriptionInfos
      // Don't use for performance critical tasks
      synchronized(clientSubscriptionMap) {
         Iterator iterator = clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Iterator iterator2 = subMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  if (exactOnly && sub.getKeyData().isQuery()) {
                     if (log.isLoggable(Level.FINE)) log.fine("Ignoring subscription " + sub.getSubscriptionId() + " for message '" + keyOid + "'");
                     continue;
                  }
                  if (keyOid.equals(sub.getKeyOid())) {
                     if (log.isLoggable(Level.FINE)) log.fine("Found subscription " + sub.getSubscriptionId() + " for message '" + keyOid + "'");
                     if (vec == null) vec = new Vector();
                     vec.addElement(sub);
                  }
               }
            }
         }
      }
      return vec;
   }

   /**
    * Invoked on successful client login (interface I_ClientListener)
    */
   public void sessionAdded(ClientEvent e) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Login event for client " + e.getSessionInfo().toString() + ", nothing to do");
   }

   /**
    * Invoked on successful client re-login (interface I_ClientListener)
    */
   public void sessionUpdated(ClientEvent e) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("Session update event for client " + e.getSessionInfo().toString() + ", nothing to do");
   }

   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {
   }

   /**
    * Invoked when client does a logout (interface I_ClientListener)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("START-logout()");
      //if (log.isLoggable(Level.FINEST)) log.dump(ME, requestBroker.toXml());

      SessionInfo sessionInfo = e.getSessionInfo();
      if (log.isLoggable(Level.FINE)) log.fine("Logout event for client " + sessionInfo.toString() + ", removing entries");
      try {
         removeFromClientSubscriptionMap(sessionInfo, null);
      } catch (XmlBlasterException e1) {
      }

      try {
         removeFromQuerySubscribeRequestsSet(sessionInfo, null);
      } catch (XmlBlasterException e2) {
      }

      //if (log.isLoggable(Level.FINEST)) log.dump(ME, "END-logout()\n" + requestBroker.toXml());
   }


   /**
    * Event invoked on new created SubjectInfo. 
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException
   {
      log.warning("Ignoring SubjectInfo added event for client " + e.getSubjectInfo().toString());
   }

   /**
    * Event invoked on deleted SubjectInfo. 
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException
   {
      log.warning("Ignoring SubjectInfo removed event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on message erase() invocation. 
    */
   public void topicRemove(TopicHandler topicHandler) throws XmlBlasterException
   {
      String uniqueKey = topicHandler.getUniqueKey();

      if (topicHandler.hasExactSubscribers()) {
         if (log.isLoggable(Level.FINE)) log.fine("Erase event for oid=" + uniqueKey + "', we do nothing here as subscription reservation remains even on deleted messages");
         return;
      }
      Vector vec = getSubscriptionByOid(uniqueKey, false);
      if (vec == null) {
         if (log.isLoggable(Level.FINE)) log.fine("Erase event for oid=" + uniqueKey + "', we do nothing as no subscribes are found");
         return;
      }
      for (int ii=0; ii<vec.size(); ii++) {
         SubscriptionInfo sub = (SubscriptionInfo)vec.elementAt(ii);
         if (log.isLoggable(Level.FINE)) log.fine("Erase event for oid=" + uniqueKey + "', removing subId=" + sub.getSubscriptionId() + " ...");
         subscriptionRemove(new SubscriptionEvent(sub));
      }
   }

   /**
    * Event invoked on new subscription (interface SubscriptionListener).
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException {
      
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (subscriptionInfo.getSubscribeCounter() > 1) {
         if (log.isLoggable(Level.FINE)) log.fine("Ignoring multisubscribe instance " + subscriptionInfo.getSubscribeCounter());
         return;
      }
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      if (log.isLoggable(Level.FINE)) log.fine("Subscription add event " + e);
      KeyData keyData = subscriptionInfo.getKeyData();

      String uniqueKey = sessionInfo.getSessionName().getRelativeName();

      // Insert into first map:
      Object obj;
      Map subMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(uniqueKey);
         if (obj == null) {
            subMap = Collections.synchronizedMap(new HashMap());
            clientSubscriptionMap.put(uniqueKey, subMap);
         }
         else {
            subMap = (Map)obj;
         }
         subMap.put(subscriptionInfo.getSubscriptionId(), subscriptionInfo);
         if (log.isLoggable(Level.FINE)) log.fine("Adding subscriptionId=" + subscriptionInfo.getSubscriptionId() + " to subMap of client " + sessionInfo.getId());
      }


      // Insert into second map:
      if (keyData.isQuery()) {
         obj=null;
         synchronized(querySubscribeRequestsSet) {
            querySubscribeRequestsSet.add(subscriptionInfo);
         }
      }
   }

   /**
    * Invoked when a subscription is canceled (interface SubscriptionListener).
    * <p />
    * Note that the subscriptionInfo object carried in SubscriptionEvent
    * is not the real known subscription, but rather misused as a container to
    * carry the sessionInfo and subscriptionInfoUniqueKey
    */
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (subscriptionInfo.getSubscribeCounter() > 1) {
         if (log.isLoggable(Level.FINE)) log.fine("Ignoring multisubscribe instance " + subscriptionInfo.getSubscribeCounter());
         return;
      }

      String subscriptionInfoUniqueKey = subscriptionInfo.getSubscriptionId();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();

      if (log.isLoggable(Level.FINE)) log.fine("Subscription remove event " + e.toString());

      try {
         removeFromClientSubscriptionMap(sessionInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e1) {
         log.severe("removeFromClientSubscriptionMap: " + e1.toString());
      }

      try {
         removeFromQuerySubscribeRequestsSet(sessionInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e2) {
         log.severe("removeFromQuerySubscribeRequestsSet: " + e2.toString());
      }

      subscriptionInfo.shutdown();
   }


   /**
    * @param subscriptionInfoUniqueKey ==null: Remove client with all its subscriptions<br>
    *                                  !=null: Remove only the given subscription
    */
   private void removeFromClientSubscriptionMap(SessionInfo sessionInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      String uniqueKey = sessionInfo.getSessionName().getRelativeName();

      Object obj;
      synchronized(clientSubscriptionMap) {
         if (subscriptionInfoUniqueKey == null) {
            obj = clientSubscriptionMap.remove(uniqueKey); // client logout
            if (log.isLoggable(Level.FINE)) log.fine("Removing client " + sessionInfo.toString() + " from clientSubscriptionMap ...");
         }
         else {
            obj = clientSubscriptionMap.get(uniqueKey);    // client unsubscribes
            if (log.isLoggable(Level.FINE)) log.fine("Removing subscription " + subscriptionInfoUniqueKey + " from client " + sessionInfo.toString() + " from clientSubscriptionMap, subscriptionInfoUniqueKey=" + subscriptionInfoUniqueKey + " ...");
         }
      }
      if (obj == null) {
         if (log.isLoggable(Level.FINE)) log.fine("Sorry, can't remove client subscription for " + sessionInfo.toString() + ", client never subscribed something");
         return;
      }

      // Now we have a map of all subscriptions of this client

      Map subMap = (Map)obj;
      if (log.isLoggable(Level.FINE)) log.fine("Subscription=" + subscriptionInfoUniqueKey + " client=" + sessionInfo.toString() + " subMap.size=" + subMap.size());
      if (subscriptionInfoUniqueKey == null) {  // client does logout(), remove everything:
         synchronized (subMap) {
            Iterator iterator = subMap.values().iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               if (sub.getKeyData().isQuery()) {
                  if (log.isLoggable(Level.FINE)) log.fine("Ignoring subscription " + sub.getSubscriptionId() + " from TopicHandler '" + sub.getKeyOid() + "'");
                  continue;
               }
               if (log.isLoggable(Level.FINE)) log.fine("Removing subscription " + sub.getSubscriptionId() + " from TopicHandler '" + sub.getKeyOid() + "'");
               sub.removeSubscribe(); // removes me from TopicHandler::subscriberMap
            }
         }
         subMap.clear();
         subMap = null;
      }
      else {                                    // client does a single unSubscribe():
         SubscriptionInfo sub = null;
         synchronized (subMap) {
            sub = (SubscriptionInfo)subMap.remove(subscriptionInfoUniqueKey);
         }
         if (sub == null) {
            log.severe("Sorry, can't remove client subscriptionId=" + subscriptionInfoUniqueKey + " for " + sessionInfo.toString() + ", not found, subMap size=" + subMap.size());
            log.warning(toXml());
            Thread.dumpStack();
            return;
         }
         sub.removeSubscribe(); // removes me from TopicHandler::subscriberMap
      }
   }


   /**
    * @param subscriptionInfoUniqueKey ==null: Remove client with all its subscriptions<br>
    *                                  !=null: Remove only the given subscription
    */
   private int removeFromQuerySubscribeRequestsSet(SessionInfo sessionInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINE)) log.fine("removing client " + sessionInfo.toString() + " subscriptionInfoUniqueKey=" + subscriptionInfoUniqueKey + " from querySubscribeRequestsSet with size=" + querySubscribeRequestsSet.size() + " ...");
      String uniqueKey = sessionInfo.getSessionName().getRelativeName();

      ArrayList<SubscriptionInfo> vec = new ArrayList<SubscriptionInfo>(querySubscribeRequestsSet.size());

      // Slow linear search!!!!
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().getSessionName().getRelativeName().equals(uniqueKey)) {
            	if (subscriptionInfoUniqueKey == null || subscriptionInfoUniqueKey.equals(sub.getSubscriptionId())) {
            		vec.add(sub);
            		sub.shutdown();
            	}
            }
         }
         for (int ii=0; ii<vec.size(); ii++) {
        	SubscriptionInfo si = vec.get(ii);
            boolean found = querySubscribeRequestsSet.remove(si);
            if (log.isLoggable(Level.FINE)) log.fine("Removed " + found + " subscription " + ((SubscriptionInfo)vec.get(ii)).getSubscriptionId() + " from querySubscribeRequestsSet");
         }
      }
      int count = vec.size();
      vec = null;
      return count;
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of ClientSubscriptions
    */
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of ClientSubscriptions
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer(10000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<ClientSubscriptions>");
      sb.append(offset).append(" <ExactSubscriptions>");
      synchronized(this.clientSubscriptionMap) {
         Iterator iterator = clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Iterator iterator2 = subMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  if (sub.getKeyData().isExact()) {
                     sb.append(sub.toXmlSmall(extraOffset + Constants.INDENT + Constants.INDENT));
                  }
               }
            }
         }
      }
      sb.append(offset).append(" </ExactSubscriptions>");
      sb.append(offset).append(" <XPathSubscriptions>");
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            sb.append(offset).append(sub.toXml(extraOffset + Constants.INDENT + Constants.INDENT));
         }
      }
      sb.append(offset + " </XPathSubscriptions>");
      sb.append(offset + "</ClientSubscriptions>");
      return sb.toString();
   }

   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#getPriority()
    */
   public Integer getPriority() {
      return PRIO_01;
   }
}
