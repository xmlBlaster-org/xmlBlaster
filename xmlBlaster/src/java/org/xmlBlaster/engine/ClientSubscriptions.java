/*------------------------------------------------------------------------------
Name:      ClientSubscriptions.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling subscriptions, collected for each Client
Version:   $Id: ClientSubscriptions.java,v 1.14 2000/02/24 22:19:52 ruff Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientListener;
import org.xmlBlaster.authentication.ClientEvent;

import java.util.*;
import java.io.*;


/**
 * Handling subscriptions, collected for each Client.
 * <p />
 * The interface SubscriptionListener informs about subscribe/unsubscribe events
 * @version: $Id: ClientSubscriptions.java,v 1.14 2000/02/24 22:19:52 ruff Exp $
 * @author Marcel Ruff
 */
public class ClientSubscriptions implements ClientListener, SubscriptionListener, MessageEraseListener
{
   final private static String ME = "ClientSubscriptions";

   private static ClientSubscriptions clientSubscriptions = null; // Singleton pattern

   private final RequestBroker requestBroker;

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
    * key   = client.getUniqueKey(), the active object map AOM id
    * value = aboMap (Collections.synchronizedMap(new HashMap());)
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
    * Access to ClientSubscriptions singleton
    */
   public static ClientSubscriptions getInstance(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      synchronized (ClientSubscriptions.class)
      {
         if (clientSubscriptions == null)
            clientSubscriptions = new ClientSubscriptions(requestBroker, authenticate);
      }
      return clientSubscriptions;
   }


   /**
    * Access to ClientSubscriptions singleton
    */
   public static ClientSubscriptions getInstance()
   {
      synchronized (ClientSubscriptions.class)
      {
         if (clientSubscriptions == null) {
            Log.panic(ME, "Use other getInstance first");
         }
      }
      return clientSubscriptions;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private ClientSubscriptions(RequestBroker requestBroker, Authenticate authenticate) throws XmlBlasterException
   {
      this.requestBroker = requestBroker;
      requestBroker.addSubscriptionListener(this);
      requestBroker.addMessageEraseListener(this);
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
    * If you have the ingredients to construct a unique id of a subscription, you may access the
    * SubscriptionInfo object here.
    * <p />
    * You can access XPATH or EXACT subscription objects through this method
    * @param clientInfo All infos about the client
    * @param xmlKey     The XML based message key
    * @param qos        The base QoS class
    * @return corresponding subscriptionInfo object<br />
    *         or null if not found
    */
   public SubscriptionInfo getSubscription(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos) throws XmlBlasterException
   {
      String subscriptionInfoUniqueKey = SubscriptionInfo.generateUniqueKey(clientInfo, xmlKey, qos).toString();
      return getSubscription(clientInfo, subscriptionInfoUniqueKey);
   }


   /**
    * If you have the unique id of a subscription, you may access the
    * SubscriptionInfo object here.
    * <p />
    * You can access XPATH or EXACT subscription objects through this method
    * @param clientInfo All infos about the client
    * @param subscriptionInfoUniqueKey
    * @return corresponding subscriptionInfo object<br />
    *         or null if not found
    */
   public SubscriptionInfo getSubscription(ClientInfo clientInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      Object obj;
      Map aboMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(clientInfo.getUniqueKey());
         if (obj == null)
            return null;
         aboMap = (Map)obj;
      }

      SubscriptionInfo subs = (SubscriptionInfo)aboMap.get(subscriptionInfoUniqueKey);
      return subs;
   }


   /**
    * Invoked on successful client login (interface ClientListener)
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString() + ", nothing to do");
   }


   /**
    * Invoked when client does a logout (interface ClientListener)
    */
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      if (Log.DUMP) Log.dump(ME, "-------START-logout()---------\n" + requestBroker.printOn().toString());

      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString() + ", removing entries");
      try {
         removeFromClientSubscriptionMap(clientInfo, null);
      } catch (XmlBlasterException e1) {
      }

      try {
         removeFromSubscribeRequestsSet(clientInfo, null);
      } catch (XmlBlasterException e2) {
      }

      if (Log.DUMP) Log.dump(ME, "-------END-logout()---------\n" + requestBroker.printOn().toString());
   }


   /**
    * Event invoked on message erase() invocation (interface MessageEraseListener).
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Erase event for client " + clientInfo.toString());
      MessageUnitHandler msgUnitHandler = e.getMessageUnitHandler();
      String uniqueKey = msgUnitHandler.getUniqueKey();
   }


   /**
    * Event invoked on new subscription (interface SubscriptionListener).
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException
   {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      ClientInfo clientInfo = subscriptionInfo.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Subscription add event for client " + clientInfo.toString());
      XmlKey xmlKey = subscriptionInfo.getXmlKey();
      String uniqueKey = clientInfo.getUniqueKey();

      // Insert into first map:
      Object obj;
      Map aboMap;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.get(uniqueKey);
         if (obj == null) {
            aboMap = Collections.synchronizedMap(new HashMap());
            clientSubscriptionMap.put(uniqueKey, aboMap);
         }
         else {
            aboMap = (Map)obj;
         }
         aboMap.put(subscriptionInfo.getUniqueKey(), subscriptionInfo);
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
      if (xmlKey.getQueryType() != XmlKey.PUBLISH && xmlKey.getQueryType() != XmlKey.EXACT_QUERY)
         return true;
      return false;
   }


   /**
    * Invoked when a subscription is canceled (interface SubscriptionListener).
    * <p />
    * Note that the subscriptionInfo object carried in SubscriptionEvent
    * is not the real known subscription, but rather misused as a container to
    * carry the clientInfo and subscriptionInfoUniqueKey
    */
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException
   {
      String subscriptionInfoUniqueKey = e.getSubscriptionInfo().getUniqueKey();
      ClientInfo clientInfo = e.getSubscriptionInfo().getClientInfo();

      if (Log.TRACE) Log.trace(ME, "Subscription remove event for client " + clientInfo.toString());

      try {
         removeFromClientSubscriptionMap(clientInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e1) {
         Log.error(ME+".subscriptionRemove", "removeFromClientSubscriptionMap: " + e1.toString());
      }

      try {
         removeFromSubscribeRequestsSet(clientInfo, subscriptionInfoUniqueKey);
      } catch (XmlBlasterException e2) {
         Log.error(ME+".subscriptionRemove", "removeFromSubscribeRequestsSet: " + e2.toString());
      }
   }


   /**
    * @param subscriptionInfoUniqueKey ==null: Remove client with all its subscriptions<br>
    *                                  !=null: Remove only the given subscription
    */
   private void removeFromClientSubscriptionMap(ClientInfo clientInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      String uniqueKey = clientInfo.getUniqueKey();

      Object obj;
      synchronized(clientSubscriptionMap) {
         if (subscriptionInfoUniqueKey == null) {
            obj = clientSubscriptionMap.remove(uniqueKey); // client logout
            if (Log.TRACE) Log.trace(ME, "Removing client " + clientInfo.toString() + " from clientSubscriptionMap ...");
         }
         else {
            obj = clientSubscriptionMap.get(uniqueKey);    // client unsubscribes
            if (Log.TRACE) Log.trace(ME, "Removing subscription " + subscriptionInfoUniqueKey + " from client " + clientInfo.toString() + " from clientSubscriptionMap ...");
         }
      }
      if (obj == null) {
         if (Log.TRACE) Log.trace(ME + ".ClientDoesntExist", "Sorry, can't remove client subscription for " + clientInfo.toString() + ", client never subscribed something");
         return;
      }

      // Now we have a map of all subsriptions of this client

      Map aboMap = (Map)obj;
      if (subscriptionInfoUniqueKey == null) {  // client does logout(), remove everything:
         synchronized (aboMap) {
            Iterator iterator = aboMap.values().iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               if (isAQuery(sub.getXmlKey()))
                  continue;
               if (Log.TRACE) Log.trace(ME, "Removing subscription " + sub.getUniqueKey() + " from MessageUnitHandler");
               sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
            }
         }
         aboMap.clear();
         aboMap = null;
      }
      else {                                    // client does a single unSubscribe():
         SubscriptionInfo sub = null;
         synchronized (aboMap) {
            sub = (SubscriptionInfo)aboMap.remove(subscriptionInfoUniqueKey);
         }
         if (sub == null) {
            Log.error(ME + ".Internal", "Sorry, can't remove client subscription for " + clientInfo.toString() + ", not found");
            return;
         }
         sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
      }
   }


   /**
    * @param subscriptionInfoUniqueKey ==null: Remove client with all its subscriptions<br>
    *                                  !=null: Remove only the given subscription
    */
   private void removeFromSubscribeRequestsSet(ClientInfo clientInfo, String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "removing client " + clientInfo.toString() + " from querySubscribeRequestsSet ...");
      String uniqueKey = clientInfo.getUniqueKey();

      Vector vec = new Vector(querySubscribeRequestsSet.size());

      // Slow linear search!!!!
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getClientInfo().getUniqueKey().equals(uniqueKey) && subscriptionInfoUniqueKey == null ||
                subscriptionInfoUniqueKey == sub.getUniqueKey()) {
               vec.addElement(sub);
            }
         }
         for (int ii=0; ii<vec.size(); ii++) {
            if (Log.TRACE) Log.trace(ME, "Removing subscription " + ((SubscriptionInfo)vec.elementAt(ii)).getUniqueKey() + " from querySubscribeRequestsSet");
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
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of ClientSubscriptions
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<ClientSubscriptions>");
      sb.append(offset + "   <ExactSubscriptions>");
      synchronized(clientSubscriptionMap) {
         Iterator iterator = clientSubscriptionMap.values().iterator();
         while (iterator.hasNext()) {
            Map aboMap = (Map)iterator.next();
            synchronized(aboMap) {
               Iterator iterator2 = aboMap.values().iterator();
               while (iterator2.hasNext()) {
                  SubscriptionInfo sub = (SubscriptionInfo)iterator2.next();
                  if (sub.getXmlKey().isExact())
                     sb.append(offset).append("      <").append(sub.getUniqueKey()).append(" />");
               }
            }
         }
      }
      sb.append(offset + "   </ExactSubscriptions>");
      sb.append(offset + "   <XPathSubscriptions>");
      synchronized(querySubscribeRequestsSet) {
         Iterator iterator = querySubscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            sb.append(offset).append("      <").append(sub.getUniqueKey()).append(" />");
         }
      }
      sb.append(offset + "   </XPathSubscriptions>");
      sb.append(offset + "</ClientSubscriptions>\n");
      return sb;
   }
}
