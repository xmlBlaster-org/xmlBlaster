/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.3 $
           $Date: 1999/11/11 16:15:00 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;


/**
 * RequestBroker
 */
public class RequestBroker
{
   final private String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

   final private Map clientInfoMap = Collections.synchronizedMap(new HashMap());

   final private Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   final private ServerImpl serverImpl;



   public static RequestBroker getInstance(ServerImpl serverImpl)
   {
      synchronized (RequestBroker.class)
      {
         if (requestBroker == null)
            requestBroker = new RequestBroker(serverImpl);
      }
      return requestBroker;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private RequestBroker(ServerImpl serverImpl)
   {
      this.serverImpl = serverImpl;
   }


   /**
    */
   public BlasterCallback getBlasterCallback(String callbackIOR)
   {
      return serverImpl.getBlasterCallback(callbackIOR);
   }


   /**
    */
   public ClientInfo getClientInfo(XmlKey xmlKey, XmlQoS qos) throws XmlBlasterException
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.get(xmlKey);
         if (obj == null) {
            ClientInfo clientInfo = new ClientInfo(this, xmlKey, qos);
            clientInfoMap.put(clientInfo.getUniqueKey(), clientInfo);
            return clientInfo;
         }
         return (ClientInfo)obj;
      }
   }


   /**
    */
   public void subscribe(XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = getClientInfo(xmlKey, subscribeQoS);
      String uniqueKey = xmlKey.getUniqueKey();
      SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS);

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            MessageUnitHandler msg = new MessageUnitHandler(this, subs);
            messageContainerMap.put(uniqueKey, msg);
         }
         else {
            MessageUnitHandler msg = (MessageUnitHandler)obj;
            msg.addSubscriber(subs);
         }
      }
   }


   /**
    */
   public void unSubscribe(XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
      ClientInfo clientInfo = getClientInfo(xmlKey, unSubscribeQoS);
      String uniqueKey = xmlKey.getUniqueKey();

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".DoesntExist", "Sorry, can't unsubscribe, message unit doesn't exist: " + uniqueKey);
            throw new XmlBlasterException(ME + ".DoesntExist", "Sorry, can't unsubscribe, message unit doesn't exist: " + uniqueKey);
         }
         MessageUnitHandler msg = (MessageUnitHandler)obj;
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, unSubscribeQoS);
         int numRemoved = msg.removeSubscriber(subs);
         if (numRemoved < 1) {
            Log.warning(ME + ".NotSubscribed", "Sorry, can't unsubscribe, you never subscribed to " + uniqueKey);
            throw new XmlBlasterException(ME + ".NotSubscribed", "Sorry, can't unsubscribe, you never subscribed to " + uniqueKey);
         }
      }
   }


   /**
    */
   public int set(XmlKey xmlKey, byte[] content, XmlQoS setQoS) throws XmlBlasterException
   {
      // !!! TODO: handling of setQoS

      MessageUnitHandler msg;

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
         if (obj == null) {
            msg = new MessageUnitHandler(requestBroker, xmlKey, content);
            messageContainerMap.put(msg.getUniqueKey(), msg);
         }
         else {
            msg = (MessageUnitHandler)obj;
            msg.setContent(content);
         }
      }

      Map subscriberMap = msg.getSubscriberMap();
      if (Log.DEBUG) Log.trace(ME, "subscriberMap.size() = " + subscriberMap.size());
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();

         while (iterator.hasNext())
         {
            if (Log.DEBUG) Log.trace(ME, "Entering xmlBlaster.set(" + xmlKey.getUniqueKey() + ")");
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            BlasterCallback cb = sub.getClientInfo().getCB();
            cb.update(xmlKey.toString(), content);
         }
      }

      return 1;
   }


   /**
    */
   public int erase(XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            return 0;
            // throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
         }
         else {
            obj = null;
            return 1;
         }
      }
   }
}
