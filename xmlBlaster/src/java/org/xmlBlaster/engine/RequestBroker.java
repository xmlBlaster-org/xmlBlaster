/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.2 $
           $Date: 1999/11/11 12:17:52 $
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
   public ClientInfo getClientInfo(XmlKey xmlKey, XmlQoS qos)
   {
      synchronized(clientInfoMap) {
         Object obj = clientInfoMap.get(xmlKey);
         if (obj == null) {
            ClientInfo cl = new ClientInfo(this, xmlKey, qos);
            clientInfoMap.put(cl.getUniqueKey(), cl);
            return cl;
         }
         return (ClientInfo)obj;
      }
   }


   /**
    */
   public MessageUnitHandler getMessageUnitHandler(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();
      SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS);

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            MessageUnitHandler msg = new MessageUnitHandler(this, subs);
            messageContainerMap.put(uniqueKey, msg);
            return msg;
         }
         else {
            MessageUnitHandler msg = (MessageUnitHandler)obj;
            msg.addSubscriber(subs);
         }
      }
      return (MessageUnitHandler)null;
   }


   /**
    */
   public void subscribe(XmlKey xmlKey, XmlQoS subscribeQoS) throws XmlBlasterException
   {
      ClientInfo cl = getClientInfo(xmlKey, subscribeQoS);
      MessageUnitHandler msg = getMessageUnitHandler(cl, xmlKey, subscribeQoS);
   }


   /**
    */
   public int set(XmlKey xmlKey, byte[] content) throws XmlBlasterException
   {

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

      Set subscriberSet = msg.getSubscriberSet();
      if (Log.DEBUG) Log.trace(ME, "subscriberSet.size() = " + subscriberSet.size());
      synchronized(subscriberSet) {
         Iterator iterator = subscriberSet.iterator();

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
}
