/*------------------------------------------------------------------------------
Name:      MessageUnitHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling exactly one message content
Version:   $Id: MessageUnitHandler.java,v 1.16 1999/11/23 15:31:42 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import java.util.*;


/**
 * Handles a MessageUnit and its subscribers.
 */
public class MessageUnitHandler
{
   private final static String ME = "MessageUnitHandler";

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
    *
    * key   = a unique key identifying the subscription
    * value = SubscriptionInfo object
    */
   final private Map subscriberMap = Collections.synchronizedMap(new TreeMap(/*new Comparator()*/));


   /**
    * This is the Message itself
    */
   private MessageUnit messageUnit;
   private XmlKey xmlKey = null;     // may be null until the first publish() arrives
   //private QoSKey qosPublish;      // the flags from the publisher
   private String uniqueKey;         // Attribute oid of key tag: <key oid="..."> </key>

   private boolean handlerIsNewCreated=true;  // a little helper for RequestBroker, showing if MessageUnit is new created


   /**
    * Constructor if a subscription is made on a yet unknown object
    */
   public MessageUnitHandler(RequestBroker requestBroker, XmlKey xmlKey) throws XmlBlasterException
   {
      if (requestBroker == null || xmlKey == null) {
         Log.error(ME, "Invalid constructor parameter");
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }

      this.requestBroker = requestBroker;
      this.uniqueKey = xmlKey.getUniqueKey();
      // this.xmlKey = xmlKey; this is not the real xmlKey from a publish, its only the subscription syntax
      this.messageUnit = new MessageUnit(xmlKey.literal(), new byte[0]);

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler because of subscription. Key=" + uniqueKey);

      // mimeType and content remains unknown until first data is fed
   }


   /**
    * Constructor if a yet unknown object is fed by method publish()
    * @param requestBroker
    * @param xmlKey Since it is parsed in the calling method, we don't need to do it again from messageUnit.xmlKey_literal
    * @param the CORBA MessageUnit data container
    */
   public MessageUnitHandler(RequestBroker requestBroker, XmlKey xmlKey, MessageUnit messageUnit/*, QoSKey qosPublish*/) throws XmlBlasterException
   {
      if (requestBroker == null || messageUnit == null || messageUnit.xmlKey == null) {
         Log.error(ME, "Invalid constructor parameters");
         throw new XmlBlasterException(ME, "Invalid constructor parameters");
      }

      if (messageUnit.content == null)
         messageUnit.content = new byte[0];

      this.requestBroker = requestBroker;
      this.messageUnit = messageUnit;
      this.xmlKey = xmlKey;
      this.uniqueKey = this.xmlKey.getUniqueKey();
      //this.qosPublish = qosPublish;

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler setting new data. Key=" + uniqueKey);
   }


   /**
    * Accessing the key of this message
    */
   public XmlKey getXmlKey()
   {
      return xmlKey;
   }


   /**
    * Clean up everything, since i will be deleted now
    */
   public void erase() throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering erase()");

      SubscriptionInfo[] arr = null; // to avoid deadlock in subscriberMap, copy subs into this array
      synchronized(subscriberMap) {
         arr = new SubscriptionInfo[subscriberMap.size()];
         Iterator iterator = subscriberMap.values().iterator();
         int jj=0;
         while (iterator.hasNext()) {
            SubscriptionInfo subs = (SubscriptionInfo)iterator.next();
            arr[jj] = subs;
            jj++;
         }
      }

      for (int ii=0; ii<arr.length; ii++)
         requestBroker.fireSubscriptionEvent(arr[ii], false);

      Log.warning(ME, "No subscribed client notifiction about message erase() yet implemented");

      subscriberMap.clear();
      // subscriberMap = null;    is final, can't assign null

      // xmlKey = null; not my business
      messageUnit = null;
      uniqueKey = null;
   }


   /**
    * setting update of a changed content
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(XmlKey xmlKey, byte[] content)
   {
      if (Log.CALLS) Log.trace(ME, "Updating xmlKey " + uniqueKey);

      if (this.xmlKey == null) {
         this.xmlKey = xmlKey; // storing the key from the first publish() invocation
         this.messageUnit.xmlKey = xmlKey.literal();
      }

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


   /**
    * A little helper for RequestBroker, showing if MessageUnit is new created
    */
   public final boolean isNewCreated()
   {
      return handlerIsNewCreated;
   }
   public final void setNewCreatedFalse()
   {
      handlerIsNewCreated = false;
   }


   /*
    * The root node of the xmlBlaster DOM tree
    */
   public org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      return xmlKey.getRootNode();
   }


   /**
    * A client subscribed to this message
    */
   public void addSubscriber(SubscriptionInfo sub) throws XmlBlasterException
   {
      Object oldOne;
      synchronized(subscriberMap) {
         oldOne = subscriberMap.put(sub.getUniqueKey(), sub);
      }

      if (oldOne != null) {
         subscriberMap.put(((SubscriptionInfo)oldOne).getUniqueKey(), oldOne);  // restore the original one ...
         Log.warning(ME + ".DuplicateSubscription", "Client " + sub.getClientInfo().toString() + " has already subscribed to " + uniqueKey);
         //No exception, since it would cancel other subscription requests as well
         //-> the client is not informed about ignored duplicate subscriptions
         //throw new XmlBlasterException(ME + ".DuplicateSubscription", "You have already subscribed to " + uniqueKey);
      }

      sub.addMessageUnitHandler(this);

      if (Log.TRACE) Log.trace(ME, "You have successfully subscribed to " + uniqueKey);

      invokeCallback(sub);
   }


   /**
    * A client wants to unSubscribe from this message
    * @return the removed SubscriptionInfo object or null if not found
    */
   public SubscriptionInfo removeSubscriber(String subscriptionInfoUniqueKey) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Size of subscriberMap = " + subscriberMap.size());

      SubscriptionInfo subs = null;
      synchronized(subscriberMap) {
         subs = (SubscriptionInfo)subscriberMap.remove(subscriptionInfoUniqueKey);
      }
      if (subs == null)
         Log.warning(ME + ".DoesntExist", "Sorry, can't unsubscribe, you where not subscribed to subscription ID=" + subscriptionInfoUniqueKey);

      return subs;
   }


   /**
    * This is the unique key of the messageUnit
    */
   public String getUniqueKey()
   {
      return uniqueKey;
   }


   /**
    */
   public String getMimeType() throws XmlBlasterException
   {
      if (getMessageUnit().xmlKey == null) {
         Log.error(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
         throw new XmlBlasterException(ME + ".UnknownMime", "Sorry, mime type not yet known for " + getUniqueKey());
      }
      return xmlKey.getMimeType();
   }

   /**
    * A Set subscriberMap.entrySet() would be enough in most cases
    * but I'm not quite sure how to synchronize it ...
    */
   public Map getSubscriberMap()
   {
      return subscriberMap;
   }


   /**
    * This is the unique key of the messageUnit
    */
   public MessageUnit getMessageUnit() throws XmlBlasterException
   {
      if (messageUnit == null) {
         Log.error(ME + ".EmptyMessageUnit", "Internal problem, messageUnit = null");
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, messageUnit = null");
      }
      return messageUnit;
   }


   /**
    * Send updates to all subscribed clients.
    * The whole update blocks if one client would block - to avoid this the IDL update()
    * method is marked <code>oneway</code>
    */
   public void invokeCallback() throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Going to update dependent clients, subscriberMap.size() = " + subscriberMap.size());

      // PERFORMANCE: All updates for each client should be collected !!!
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();

         while (iterator.hasNext()) {
            invokeCallback((SubscriptionInfo)iterator.next());
         }
      }
   }


   /**
    */
   public final void invokeCallback(SubscriptionInfo sub) throws XmlBlasterException
   {
      if (xmlKey == null) {
         Log.warning(ME, "invokeCallback() not supported, this MessageUnit was created by a subscribe() and not a publish()");
         return;
      }
      MessageUnitHandler.sendUpdate(sub.getClientInfo(), xmlKey, getMessageUnit());
   }


   /**
    * This sends the update to the client.
    * TODO: 1. Create a singleton UpdateHandler.java, supporting CORBA,http,email
    *       2. change arguments?
    */
   public final static void sendUpdate(ClientInfo clientInfo, XmlKey xmlKey, MessageUnit messageUnit) throws XmlBlasterException
   {
      BlasterCallback cb = clientInfo.getCB();
      XmlQoSUpdate xmlQoS = new XmlQoSUpdate();
      MessageUnit[] updateMsgArr = new MessageUnit[1];
      updateMsgArr[0] = messageUnit;
      String[] qarr = new String[1];
      qarr[0] = xmlQoS.toString();
      if (Log.TRACE) Log.trace(ME, "xmlBlaster.update(" + xmlKey.getUniqueKey() + ") to " + clientInfo.toString());
      cb.update(updateMsgArr, qarr);
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


   /**
    * Dump state of this object into XML.
    * <br>
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitHandler
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<MessageUnitHandler>");
      sb.append(offset + "   <uniqueKey>" + getUniqueKey() + "</uniqueKey>");
      if (xmlKey==null)
         sb.append(offset + "   <XmlKey>null</XmlKey>");
      else
         sb.append(xmlKey.printOn(extraOffset + "   ").toString());
      sb.append(offset + "   <content>" + (messageUnit.content==null ? "null" : messageUnit.content.toString()) + "</content>");
      if (subscriberMap.size() == 0)
         sb.append(offset + "   <SubscriptionInfo>NO SUBSCRIPTIONS</SubscriptionInfo>");
      else {
         Iterator iterator = subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo subs = (SubscriptionInfo)iterator.next();
            sb.append(subs.printOn(extraOffset + "   ").toString());
         }
      }
      sb.append(offset + "   <handlerIsNewCreated>" + handlerIsNewCreated + "</handlerIsNewCreated>");
      sb.append(offset + "</MessageUnitHandler>\n");
      return sb;
   }
}
