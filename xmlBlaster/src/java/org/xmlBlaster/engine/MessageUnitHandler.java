/*------------------------------------------------------------------------------
Name:      MessageUnitHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling exactly one message content
Version:   $Id: MessageUnitHandler.java,v 1.5 1999/11/17 13:51:25 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import java.util.*;


/**
 * Handles a MessageUnit and its subscribers. 
 */
public class MessageUnitHandler
{
   private String ME = "MessageUnitHandler";

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
   final private Map subscriberMap = Collections.synchronizedMap(new TreeMap(/*new Comparator()*/));


   /**
    * This is the Message itself
    */
   private MessageUnit messageUnit;
   private XmlKey xmlKey;
   //private QoSKey qosPublish; // the flags from the publisher
   private String uniqueKey;  // Attribute oid of key tag: <key oid="..."> </key>


   /**
    * Constructor if a subscription is made on a yet unknown object
    */
   public MessageUnitHandler(RequestBroker requestBroker, SubscriptionInfo sub) throws XmlBlasterException
   {
      this.xmlKey = sub.getXmlKey();
      this.messageUnit = new MessageUnit(sub.getXmlKey().toString(), new byte[0]);
      this.uniqueKey = xmlKey.getUniqueKey();

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler because of subscription. Key=" + uniqueKey);

      addSubscriber(sub);

      // mimeType and content remains unknown until first data is fed
   }


   /**
    * Constructor if a yet unknown object is fed by method set()
    */
   public MessageUnitHandler(RequestBroker requestBroker, MessageUnit messageUnit/*, QoSKey qosPublish*/) throws XmlBlasterException
   {
      if (requestBroker == null || messageUnit == null || messageUnit.xmlKey == null) {
         Log.error(ME, "Invalid constructor parameters");
         throw new XmlBlasterException(ME, "Invalid constructor parameters");
      }

      if (messageUnit.content == null)
         messageUnit.content = new byte[0];

      this.messageUnit = messageUnit;
      this.xmlKey = new XmlKey(messageUnit.xmlKey);
      this.uniqueKey = this.xmlKey.getUniqueKey();
      //this.qosPublish = qosPublish;

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler setting new data. Key=" + uniqueKey);
   }


   /**
    * setting update of a changed content
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(byte[] content)
   {
      if (Log.CALLS) Log.trace(ME, "Updating xmlKey " + messageUnit.xmlKey.getUniqueKey());

      if (content == null)
         content = new byte[0];

      boolean changed = false;
      if (this.messageUnit.content.length != content.length) {
         changed = true;
      }
      else {
         for (int ii=0; ii<content.length; ii++)
            if (this.messageUnit.content[ii] != content[ii]) {
               changed = true;
               break;
            }
      }

      if (changed) {  // new content is not the same as old one
         this.messageUnit.content = content;
         return true;
      }
      else {
         return false;
      }
   }


   public void addSubscriber(SubscriptionInfo sub) throws XmlBlasterException
   {
      Object oldOne;
      synchronized(subscriberMap) {
         oldOne = subscriberMap.put(sub.getUniqueKey(), sub);
      }
      if (oldOne != null) {
         Log.warning(ME + ".DuplicateSubscription", "You have already subscribed to " + sub.getXmlKey().getUniqueKey());
         throw new XmlBlasterException(ME + ".DuplicateSubscription", "You have already subscribed to " + sub.getXmlKey().getUniqueKey());
      }
   }


   public int removeSubscriber(SubscriptionInfo sub) throws XmlBlasterException
   {
      Object removedIt;
      synchronized(subscriberMap) {
         removedIt = subscriberMap.remove(sub.getUniqueKey());
      }
      if (removedIt == null) {
         Log.warning(ME + ".DoesntExist", "Sorry, can't unsubscribe, you where not subscribed to " + uniqueKey);
         return 0;
         // throw new XmlBlasterException(ME + ".DoesntExist", "Sorry, can't unsubscribe, you where not subscribed to " + uniqueKey);
      }
      return 1;
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
      if (messageUnit.xmlKey == null) {
         Log.error(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getMimeType();
   }

   /**
    * A Set subscriberMap.entrySet() would be enough in most cases
    * but I'm not quite shure how to synchronize it ...
    */
   public Map getSubscriberMap()
   {
      return subscriberMap;
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
