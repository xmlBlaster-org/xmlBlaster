/*------------------------------------------------------------------------------
Name:      MessageContainer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling exactly one message content
           $Revision: 1.1 $  $Date: 1999/11/08 22:40:59 $
------------------------------------------------------------------------------*/
package org.xmlBlaster;

import org.xmlBlaster.util.*;
import java.util.*;


/**
 * MessageContainer
 */
public class MessageContainer
{
   private String ME = "MessageContainer";

   /**
    * The broker which manages me
    */
   private RequestBroker requestBroker;


   /**
    * This map knows all clients which have subscribed on this message content
    * and knows all individual wishes of the subscription (QoS). 
    *
    * The map contains SubscriptionInfo objects.
    *
    * It is a TreeMap, that means it keeps order information.
    * TODO: express order attribute so that the first client will be served first.
    */
   final private Map subscriberMap = Collections.synchronizedMap(new TreeMap());


   /**
    * This is the Message itself
    */
   private XmlKey xmlKey;
   private String uniqueKey;
   private byte[] content;


   /**
    * Constructor if a subscription is made on a yet unknown object
    */
   public MessageContainer(RequestBroker requestBroker, SubscriptionInfo sub)
   {
      this.uniqueKey = sub.getUniqueKey();
      this.xmlKey = sub.getXmlKey();

      if (Log.CALLS) Log.trace(ME, "Creating new MessageContainer because of subscription. Key=" + uniqueKey);

      subscriberMap.put(uniqueKey, sub);
   }


   /**
    * Constructor if a yet unknown object is fed by method set()
    */
   public MessageContainer(RequestBroker requestBroker, XmlKey xmlKey, byte[] content)
   {
      this.xmlKey = xmlKey;
      this.uniqueKey = xmlKey.getUniqueKey();

      if (Log.CALLS) Log.trace(ME, "Creating new MessageContainer setting new data. Key=" + uniqueKey);

      this.content = content;

      // mimeType and content remains unknown until first data is fed
   }


   public void setContent(byte[] content)
   {
      if (Log.CALLS) Log.trace(ME, "Updating xmlKey " + xmlKey.toString());
      this.content = content;
   }


   public String getUniqueKey()
   {
      return uniqueKey;
   }


   public String getMimeType() throws XmlBlasterException
   {
      if (xmlKey == null)
         throw new XmlBlasterException("MessageContainer.UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      return xmlKey.getMimeType();
   }

   public Map getSubscriberMap()
   {
      return subscriberMap;
   }
   /*
   public TreeSet getSubscriberEntrySet()
   {
      return subscriberMap.getEntrySet();
   }
   */



}
