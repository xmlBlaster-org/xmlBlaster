/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.8 $
           $Date: 1999/11/16 18:16:25 $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.ServerImpl;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;


/**
 * RequestBroker
 */
public class RequestBroker
{
   final private String ME = "RequestBroker";

   private static RequestBroker requestBroker = null; // Singleton pattern

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
   public void subscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
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
   public void unSubscribe(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS unSubscribeQoS) throws XmlBlasterException
   {
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
   public int publish(MessageUnit[] messageUnitArr, String[] qos_literal_Arr) throws XmlBlasterException
   {
      // !!! TODO: handling of qos

      int retVal = 0;

      for (int ii=0; ii<messageUnitArr.length; ii++) {

         MessageUnit messageUnit = messageUnitArr[ii];
         XmlKey xmlKey = new XmlKey(messageUnit.xmlKey);
         MessageUnitHandler msgHandler;

         synchronized(messageContainerMap) {
            Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
            if (obj == null) {
               msgHandler = new MessageUnitHandler(requestBroker, messageUnit);
               messageContainerMap.put(msgHandler.getUniqueKey(), msgHandler);
            }
            else {
               msgHandler = (MessageUnitHandler)obj;
               msgHandler.setContent(messageUnit.content);
            }
         }

         Map subscriberMap = msgHandler.getSubscriberMap();
         if (Log.TRACE) Log.trace(ME, "subscriberMap.size() = " + subscriberMap.size());

         // DANGER: The whole update blocks if one client blocks - needs a redesign !!!
         // PREFORMANCE: All updates for each client should be collected !!!
         synchronized(subscriberMap) {
            Iterator iterator = subscriberMap.values().iterator();

            while (iterator.hasNext())
            {
               if (Log.TRACE) Log.trace(ME, "Entering xmlBlaster.set(" + xmlKey.getUniqueKey() + ")");
               SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
               BlasterCallback cb = sub.getClientInfo().getCB();
               XmlQoSUpdate xmlQoS = new XmlQoSUpdate();
               MessageUnit[] marr = new MessageUnit[1];
               marr[0] = messageUnit;
               String[] qarr = new String[1];
               qarr[0] = xmlQoS.toString();
               cb.update(marr, qarr);
            }
         }

         retVal++;
      }

      return retVal;
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
