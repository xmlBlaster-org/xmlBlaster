/*------------------------------------------------------------------------------
Name:      MessageContainer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org (LGPL)
Comment:   Handling exactly one message content
           $Revision: 1.2 $  $Date: 1999/11/10 20:26:49 $
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
   final private Set subscriberSet = Collections.synchronizedSet(new TreeSet(/*new Comparator()*/));


   /**
    * This is the Message itself
    */
   private XmlKey xmlKey;
   private String uniqueKey;
   private byte[] content;


   /**
    * Constructor if a subscription is made on a yet unknown object
    */
   public MessageContainer(RequestBroker requestBroker, SubscriptionInfo sub) throws XmlBlasterException
   {
      this.uniqueKey = sub.getUniqueKey();
      this.xmlKey = sub.getXmlKey();
      this.content = new byte[0];

      if (Log.CALLS) Log.trace(ME, "Creating new MessageContainer because of subscription. Key=" + uniqueKey);

      addSubscriber(sub);
   }


   /**
    * Constructor if a yet unknown object is fed by method set()
    */
   public MessageContainer(RequestBroker requestBroker, XmlKey xmlKey, byte[] content)
   {
      this.xmlKey = xmlKey;
      this.uniqueKey = xmlKey.getUniqueKey();

      if (content == null)
         content = new byte[0];

      if (Log.CALLS) Log.trace(ME, "Creating new MessageContainer setting new data. Key=" + uniqueKey);

      this.content = content;

      // mimeType and content remains unknown until first data is fed
   }


   /**
    * setting update of a changed content
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(byte[] content)
   {
      if (Log.CALLS) Log.trace(ME, "Updating xmlKey " + xmlKey.toString());

      if (content == null)
         content = new byte[0];

      boolean changed = false;
      if (this.content.length != content.length) {
         changed = true;
      }
      else {
         for (int ii=0; ii<content.length; ii++)
            if (this.content[ii] != content[ii]) {
               changed = true;
               break;
            }
      }

      if (changed) {  // new content is not the same as old one
         this.content = content;
         return true;
      }
      else {
         return false;
      }
   }


   public void addSubscriber(SubscriptionInfo sub) throws XmlBlasterException
   {
      synchronized(subscriberSet) {
         if (subscriberSet.contains(sub)) {
            Log.warning(ME + ".DuplicateSubscription", "You have already subscribed to " + sub.getXmlKey().getUniqueKey());
            throw new XmlBlasterException(ME + ".DuplicateSubscription", "You have already subscribed to " + sub.getXmlKey().getUniqueKey());
         }
         subscriberSet.add(sub);
      }
   }


   /**
    * This is the unique key of the messageUnit
    */
   public String getUniqueKey()
   {
      return uniqueKey;
   }


   public String getMimeType() throws XmlBlasterException
   {
      if (xmlKey == null) {
         Log.error(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getMimeType();
   }

   public Set getSubscriberSet()
   {
      return subscriberSet;
   }


   /**
    * This class determines the sorting order, by which the
    * client receive their updates.
    * For now, the client which subscribed first, is served first
    */
   /*
   class subscriberSorter implements Comparator
   {
      public int compare(Object o1, Object o2)
      {
         SubscriptionInfo s1 = (SubscriptionInfo)o1;
         SubscriptionInfo s2 = (SubscriptionInfo)o2;
         return o2.getCreationTime() - o1.getCreationTime;
      }
      public boolean equals(Object obj)
      {
         //SubscriptionInfo sub = (SubscriptionInfo)obj;
         this.equals(obj);
      }
   }
   */

}
