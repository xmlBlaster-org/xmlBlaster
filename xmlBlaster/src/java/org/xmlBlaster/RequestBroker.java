/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling the Client data
           $Revision: 1.3 $
           $Date: 1999/11/10 20:26:49 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import java.util.*;
import org.xmlBlaster.util.*;


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
   public MessageContainer getMessageContainer(ClientInfo clientInfo, XmlKey xmlKey, XmlQoS subscribeQoS) throws org.xmlBlaster.XmlBlasterException
   {
      String uniqueKey = xmlKey.getUniqueKey();
      SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS);

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            MessageContainer msg = new MessageContainer(this, subs);
            messageContainerMap.put(uniqueKey, msg);
            return msg;
         }
         else {
            MessageContainer msg = (MessageContainer)obj;
            msg.addSubscriber(subs);
         }
      }
      return (MessageContainer)null;
   }


   /**
    */
   public void subscribe(XmlKey xmlKey, XmlQoS subscribeQoS) throws org.xmlBlaster.XmlBlasterException
   {
      ClientInfo cl = getClientInfo(xmlKey, subscribeQoS);
      MessageContainer msg = getMessageContainer(cl, xmlKey, subscribeQoS);
   }


   /**
    */
   public int set(XmlKey xmlKey, byte[] content) throws org.xmlBlaster.XmlBlasterException
   {

      MessageContainer msg;

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
         if (obj == null) {
            msg = new MessageContainer(requestBroker, xmlKey, content);
            messageContainerMap.put(msg.getUniqueKey(), msg);
         }
         else {
            msg = (MessageContainer)obj;
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
            BlasterCallback cb = sub.getCB();
            cb.update(xmlKey.toString(), content);
         }
      }

      return 1;
   }
}
