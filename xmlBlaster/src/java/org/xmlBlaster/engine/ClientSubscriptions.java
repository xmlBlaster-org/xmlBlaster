/*------------------------------------------------------------------------------
Name:      ClientSubscriptions.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling subscriptions, collected for each Client
Version:   $Id: ClientSubscriptions.java,v 1.1 1999/11/18 16:59:55 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientListener;
import org.xmlBlaster.authentication.ClientEvent;

import java.util.*;
import java.io.*;


/**
 * Handling subscriptions, collected for each Client
 *
 * The interface SubscriptionListener informs about subscribe/unsubscribe
 * @version: $Id: ClientSubscriptions.java,v 1.1 1999/11/18 16:59:55 ruff Exp $
 * @author Marcel Ruff
 */
public class ClientSubscriptions implements ClientListener, SubscriptionListener
{
   final private static String ME = "ClientSubscriptions";

   private static ClientSubscriptions clientSubscriptions = null; // Singleton pattern

   private final RequestBroker requestBroker;

   /**
    * All Subscriptions of a Client are in this map
    * A multimap would be appropriate, but since this is not supported
    * by the Collections API, a map with a set as value is used. 
    * <br>
    * Used for performant logout.
    * <p>
    * key   = client.getUniqueKey()
    * value = clientSubscriptionSet with SubscriptionInfo objects
    */
   final private Set clientSubscriptionSet = Collections.synchronizedSet(new HashSet());
   final private Map clientSubscriptionMap = Collections.synchronizedMap(new HashMap());


   /**
    * All generic subscriptions are collected here. 
    * Generic are all subscriptions who don't subscribe a precise key-oid,
    * but rather subscribe all MessageUnits matching a XPath query match.
    * <br>
    * If new MessageUnits are published, this set is consulted to check
    * if some older subscriptions would match as well
    * <p>
    * value = SubscriptionInfo objects with generic subscriptions, but not
    *         those, who subscribed a MessageUnit exactly by a known oid
    */
   final private Set subscribeRequestsSet = Collections.synchronizedSet(new HashSet());


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
      authenticate.addClientListener(this);
   }


   /**
    * Invoked on successfull client login (interface ClientListener)
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
   }


   /**
    * Invoked on new subscription (interface SubscriptionListener)
    */
   public void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException
   {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      ClientInfo clientInfo = subscriptionInfo.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Subscription add event for client " + clientInfo.toString());
      XmlKey xmlKey = subscriptionInfo.getXmlKey();
      String uniqueKey = subscriptionInfo.getUniqueKey();

      if (xmlKey.getQueryType() == XmlKey.PUBLISH) {
         Log.error(ME, "Subscription add event for PUBLISH message ignored");
         return;
      }

      Object obj;
      synchronized(clientSubscriptionMap) {
         obj = clientSubscriptionMap.put(uniqueKey, subscriptionInfo);
      }

      if (xmlKey.getQueryType() != XmlKey.EXACT_QUERY) {
         obj=null;
         synchronized(subscribeRequestsSet) {
            subscribeRequestsSet.add(subscriptionInfo);
         }
      }
   }


   /**
    * Invoked when a subscription is canceled (interface SubscriptionListener)
    */    
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException
   {
      SubscriptionInfo subscriptionInfo = e.getSubscriptionInfo();
      ClientInfo clientInfo = subscriptionInfo.getClientInfo();

      if (Log.TRACE) Log.trace(ME, "Subscription remove event for client " + clientInfo.toString());

      try {
         removeFromClientSubscriptionMap(clientInfo, subscriptionInfo);
      } catch (XmlBlasterException e1) {
      }

      try {
         removeFromSubscribeRequestsSet(clientInfo, subscriptionInfo);
      } catch (XmlBlasterException e2) {
      }
   }


   /**
    * @param wantedSubs ==null: Remove client with all its subscriptions
    *                   !=null: Remove only the given subscription
    */
   private void removeFromClientSubscriptionMap(ClientInfo clientInfo, SubscriptionInfo wantedSubs) throws XmlBlasterException
   {
      String uniqueKey = clientInfo.getUniqueKey();
      Object obj;
      synchronized(clientSubscriptionMap) {
         if (wantedSubs == null)
            obj = clientSubscriptionMap.remove(uniqueKey);
         else
            obj = clientSubscriptionMap.get(uniqueKey);
      }
      if (obj == null) {
         Log.warning(ME + ".ClientDoesntExist", "Sorry, can't remove client subscription for " + clientInfo.toString() + ", client doesn't exist");
         throw new XmlBlasterException(ME + ".ClientDoesntExist", "Sorry, can't remove client subscription for " + clientInfo.toString() + ", client doesn't exist");
      }
      Set subscriptionSet = (Set)obj;
      synchronized (subscriptionSet) {
         Iterator iterator = subscriptionSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (wantedSubs == null || wantedSubs.getUniqueKey() == sub.getUniqueKey())
               sub.removeSubscribe(); // removes me from MessageUnitHandler::subscriberMap
         }
      }
   }


   /**
    * @param wantedSubs ==null: Remove client with all its subscriptions
    *                   !=null: Remove only the given subscription
    */
   private void removeFromSubscribeRequestsSet(ClientInfo clientInfo, SubscriptionInfo wantedSubs) throws XmlBlasterException
   {  
      String uniqueKey = clientInfo.getUniqueKey();

      Vector vec = new Vector(subscribeRequestsSet.size());

      // Slow linear search!!!!
      synchronized(subscribeRequestsSet) {
         Iterator iterator = subscribeRequestsSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getClientInfo().getUniqueKey().equals(uniqueKey) && wantedSubs == null ||
                wantedSubs.getUniqueKey() == sub.getUniqueKey()) {
               vec.add(sub);
            }
         }
         for (int ii=0; ii<vec.size(); ii++)
            subscribeRequestsSet.remove(vec.elementAt(ii));
      }

      vec = null;
   }
}
