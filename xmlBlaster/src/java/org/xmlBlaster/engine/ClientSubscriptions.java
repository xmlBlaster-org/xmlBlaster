/*------------------------------------------------------------------------------
Name:      ClientSubscriptions.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling subscriptions, collected for each Client
Version:   $Id: ClientSubscriptions.java,v 1.31 2002/11/26 12:38:21 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.*;
import java.io.*;


/**
 * Handling subscriptions, collected for each session of each client. 
 * <p />
 * There exists exactly one instance of this class for each xmlBlaster server,
 * the instance is handled by RequestBroker.
 * <p />
 * The interface SubscriptionListener informs about subscribe/unsubscribe events
 * @version: $Id: ClientSubscriptions.java,v 1.31 2002/11/26 12:38:21 ruff Exp $
 * @author Marcel Ruff
 */
public class ClientSubscriptions implements I_ClientListener, SubscriptionListener
{
   private final String ME;

   private final Global glob;
   private final RequestBroker requestBroker;
   private final LogChannel log;

   /**
    * All exact subscriptions of a Client are in this map.
    * <p>
    * These are the subscriptions which are referenced from a MessageUnitHandler<br>
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
    * but rather subscribe all MessageUnit matching a XPath query match.
    * <br>
    * If new MessageUnit are published, this set is consulted to check
    * if some older subscriptions would match as well
    * <p>
    * value = SubscriptionInfo objects with generic subscriptions, but not
    *         those, who subscribed a MessageUnit exactly by a known oid
    */
   final private Set querySubscribeRequestsSet = Collections.synchronizedSet(new HashSet());


   /**
    * Exactly one instance for each xmlBlaster server.
    * <p />
    * (no singleton pattern to allow multiple servers)
    * @param requestBroker my master (singleton)
    * @param authenticate another master
    */
   ClientSubscriptions(Global glob, RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      this.glob = glob;
      this.requestBroker = requestBroker;
      this.ME = "ClientSubscriptions" + this.glob.getLogPrefixDashed();
      this.log = requestBroker.getGlobal().getLog("core");
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
      if (log.TRACE) log.trace(ME, "Looking for subscriptionId=" + subscriptionInfoUniqueKey + " found " + subs);
      return subs;
   }


   /*
    * If you have the key oid of a message and a session it belongs to, you may access the
    * SubscriptionInfo object here.
    * <p />
    * Note that MessageUnitHandler.findSubscriber() will not return a SubscriptionInfo
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
   public Vector getSubscriptionByOid(SessionInfo sessionInfo, String keyOid, boolean exactOnly) throws XmlBlasterException {
      Object obj;
      Map subMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(sessionInfo.getSessionName().getRelativeName());
         if (obj == null) {
            if (log.TRACE) log.trace(ME, "Session '" + sessionInfo.getId() + "' is unknown! Message '" + keyOid + "' ignored");
            return null;
         }
         subMap = (Map)obj;
      }

      //if (log.TRACE) log.trace(ME, "Found subscription map with " + subMap.size() + " entries for '" + sessionInfo.getId() + "'");

      // Slow linear search of all subscribes of a session
      // Don't use for performance critical tasks
      Vector vec = null;
      synchronized (subMap) {
         Iterator iterator = subMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (exactOnly && isAQuery(sub.getXmlKey())) {
               if (log.TRACE) log.trace(ME, "Ignoring subscription " + sub.getSubscriptionId() +
                      " for session '" + sessionInfo.getId() + "' for message '" + keyOid + "'");
               continue;
            }
            if (keyOid.equals(sub.getKeyOid())) {
               if (log.TRACE) log.trace(ME, "Found subscription " + sub.getSubscriptionId() +
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
    * Note that MessageUnitHandler.findSubscriber() will not return a SubscriptionInfo
    * if never a message arrived for such a subscription, so prefer this method.
    *
    * @param keyOid The unique message oid
    * @param exact true Access only EXACT subscription objects through this method.<br />
    *              false All subscriptions
    * @return Vector containing corresponding subscriptionInfo objects
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
                  if (exactOnly && isAQuery(sub.getXmlKey())) {
                     if (log.TRACE) log.trace(ME, "Ignoring subscription " + sub.getSubscriptionId() + " for message '" + keyOid + "'");
                     continue;
                  }
                  if (keyOid.equals(sub.getKeyOid())) {
                     if (log.TRACE) log.trace(ME, "Found subscription " + sub.getSubscriptionId() + " for message '" + keyOid + "'");
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
      if (log.CALL) log.call(ME, "Login event for client " + e.getSessionInfo().toString() + ", nothing to do");
   }


   /**
    * Invoked when client does a logout (interface I_ClientListener)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException
   {
      if (log.CALL) log.call(ME, "START-logout()");
      if (log.DUMP) log.dump(ME, requestBroker.toXml());

      SessionInfo sessionInfo = e.getSessionInfo();
      if (log.TRACE) log.trace(ME, "Logout event for client " + sessionInfo.toString() + ", removing entries");
      try {
         removeFromClientSubscriptionMap(sessionInfo, null);
      } catch (XmlBlasterException e1) {
      }

      try {
         removeFromQuerySubscribeRequestsSet(sessionInfo, null);
      } catch (XmlBlasterException e2) {
      }

      if (log.DUMP) log.dump(ME, "END-logout()\n" + requestBroker.toXml());
   }


   /**
    * Event invoked on new created SubjectInfo. 
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException
   {
      log.warn(ME, "Ignoring SubjectInfo added event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on deleted SubjectInfo. 
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException
   {
      log.warn(ME, "Ignoring SubjectInfo removed event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on message erase() invocation. 
    */
   public void messageErase(SessionInfo eraseSessionInfo, MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      String uniqueKey = msgUnitHandler.getUniqueKey();

      if (msgUnitHandler.hasExactSubscribers()) {
         if (log.TRACE) log.trace(ME, "Erase event for oid=" + uniqueKey + "' from client=" + eraseSessionInfo.toString() +
           ", we do nothing here as subscription reservation remains even on deleted messages");
         return;
      }
      Vector vec = getSubscriptionByOid(uniqueKey, false);
      if (vec == null) {
         if (log.TRACE) log.trace(ME, "Erase event for oid=" + uniqueKey + "' from client=" + eraseSessionInfo.toString() +
           ", we do nothing as no subscribes are found");
         return;
      }
      for (int ii=0; ii<vec.size(); ii++) {
         SubscriptionInfo sub = (SubscriptionInfo)vec.elementAt(ii);
         if (log.TRACE) log.trace(ME, "Erase event for oid=" + uniqueKey + "', removing subId=" + sub.getSubscriptionId() + " ...");
         subscriptionRemove(new SubscriptionEvent(sub));
      }
   }


   /**
    * Event invoked on new subscription (interface SubscriptionListener).
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException
   {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (subscriptionInfo.getSubscribeCounter() > 1) {
         if (log.TRACE) log.trace(ME, "Ignoring multisubscribe instance " + subscriptionInfo.getSubscribeCounter());
         return;
      }
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();
      if (log.TRACE) log.trace(ME, "Subscription add event for client " + sessionInfo.getId());
      XmlKey xmlKey = subscriptionInfo.getXmlKey();
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
         if (log.TRACE) log.trace(ME, "Adding subscriptionId=" + subscriptionInfo.getSubscriptionId() + " to subMap of client " + sessionInfo.getLoginName());
      }


      // Insert into second map:
      if (isAQuery(xmlKey)) {
         obj=null;
         synchronized(querySubscribeRequestsSet) {
            querySubscribeRequestsSet.add(subscriptionInfo);
         }
      }
   }


   /**
    * @return true if the XmlKey contained an exact oid
    *         false for example XPath query
    */
   private boolean isAQuery(XmlKey xmlKey) throws XmlBlasterException
   {
      if (xmlKey == null || xmlKey.getQueryType() != XmlKey.PUBLISH && xmlKey.isQuery())
         return true;
      return false;
   }


   /**
    * Invoked when a subscription is canceled (interface SubscriptionListener).
    * <p />
    * Note that the subscriptionInfo object carried in SubscriptionEvent
    * is not the real known subscription, but rather misused as a container to
    * carry the sessionInfo and subscriptionInfoUniqueKey
    */
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException
   {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      if (subscriptionInfo.getSubscribeCounter() > 1) {
         if (log.TRACE) log.trace(ME, "Ignoring multisubscribe instance " + subscriptionInfo.getSubscribeCounter());
         return;
      }

      String subscriptionInfoUniqueKey = subscriptionInfo.getSubscriptionId();
      SessionInfo sessionInfo = subscriptionInfo.getSessionInfo();

      if (log.TRACE) log.trace(ME, "Subscription remove event for client " + sessionInfo.toString() + " for subscriptionId=" + subscriptionInfoUniqueKey);

      try {
         removeFromClientSubscriptionMap(sessionInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e1) {
         log.error(ME+".subscriptionRemove", "removeFromClientSubscriptionMap: " + e1.toString());
      }

      try {
         removeFromQuerySubscribeRequestsSet(sessionInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e2) {
         log.error(ME+".subscriptionRemove", "removeFromQuerySubscribeRequestsSet: " + e2.toString());
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
            if (log.TRACE) log.trace(ME, "Removing client " + sessionInfo.toString() + " from clientSubscriptionMap ...");
         }
         else {
            obj = clientSubscriptionMap.get(uniqueKey);    // client unsubscribes
            if (log.TRACE) log.trace(ME, "Removing subscription " + subscriptionInfoUniqueKey + " from client " + sessionInfo.toString() + " from clientSubscriptionMap, subscriptionInfoUniqueKey=" + subscriptionInfoUniqueKey + " ...");
         }
      }
      if (obj == null) {
         if (log.TRACE) log.trace(ME + ".ClientDoesntExist", "Sorry, can't remove client subscription for " + sessionInfo.toString() + ", client never subscribed something");
         return;
      }

      // Now we have a map of all subscriptions of this client

      Map subMap = (Map)obj;
      if (log.TRACE) log.trace(ME, "Subscription=" + subscriptionInfoUniqueKey + " client=" + sessionInfo.toString() + " subMap.size=" + subMap.size());
      if (subscriptionInfoUniqueKey == null) {  // client does logout(), remove everything:
         synchronized (subMap) {
            Iterator iterator = subMap.values().iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               if (isAQuery(sub.getXmlKey())) {
                  if (log.TRACE) log.trace(ME, "Ignoring subscription " + sub.getSubscriptionId() + " from MessageUnitHandler '" + sub.getKeyOid() + "'");
                  continue;
               }
               if (log.TRACE) log.trace(ME, "Removing subscription " + sub.getSubscriptionId() + " from MessageUnitHandler '" + sub.getKeyOid() + "'");
               sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
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
            log.error(ME + ".Internal", "Sorry, can't remove client subscriptionId=" + subscriptionInfoUniqueKey + " for " + sessionInfo.toString() + ", not found, subMap size=" + subMap.size());
            log.plain(ME, toXml());
            Thread.currentThread().dumpStack();
            return;
         }
         sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
      }
   }


   /**
    * @param subscriptionInfoUniqueKey ==null: Remove client with all its subscriptions<br>
    *                                  !=null: Remove only the given subscription
    */
   private void removeFromQuerySubscribeRequestsSet(SessionInfo sessionInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "removing client " + sessionInfo.toString() + " subscriptionInfoUniqueKey=" + subscriptionInfoUniqueKey + " from querySubscribeRequestsSet with size=" + querySubscribeRequestsSet.size() + " ...");
      String uniqueKey = sessionInfo.getSessionName().getRelativeName();

      Vector vec = new Vector(querySubscribeRequestsSet.size());

      // Slow linear search!!!!
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().getSessionName().getRelativeName().equals(uniqueKey) && subscriptionInfoUniqueKey == null ||
                subscriptionInfoUniqueKey == sub.getSubscriptionId()) {
               vec.addElement(sub);
            }
         }
         for (int ii=0; ii<vec.size(); ii++) {
            if (log.TRACE) log.trace(ME, "Removing subscription " + ((SubscriptionInfo)vec.elementAt(ii)).getSubscriptionId() + " from querySubscribeRequestsSet");
            querySubscribeRequestsSet.remove(vec.elementAt(ii));
         }
      }

      vec = null;
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
      StringBuffer sb = new StringBuffer(1024);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<ClientSubscriptions>");
      sb.append(offset).append("   <ExactSubscriptions>");
      synchronized(clientSubscriptionMap) {
         Iterator iterator = clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map subMap = (Map)iterator.next();
            synchronized(subMap) {
               Iterator iterator2 = subMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  if (sub.getXmlKey().isExact())
                     sb.append(offset).append("      <id>").append(sub.getSubscriptionId()).append("</id>");
               }
            }
         }
      }
      sb.append(offset).append("   </ExactSubscriptions>");
      sb.append(offset).append("   <XPathSubscriptions>");
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            //sb.append(offset).append("      <id>").append(sub.getSubscriptionId()).append("</id>");
            sb.append(offset).append(sub.toXml(extraOffset + "      "));
         }
      }
      sb.append(offset + "   </XPathSubscriptions>");
      sb.append(offset + "</ClientSubscriptions>\n");
      return sb.toString();
   }
}
