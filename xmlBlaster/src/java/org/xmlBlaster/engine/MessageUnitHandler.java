/*------------------------------------------------------------------------------
Name:      MessageUnitHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling exactly one message content
Version:   $Id: MessageUnitHandler.java,v 1.25 2000/02/01 15:18:19 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.*;
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

   /** The broker which manages me */
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
    * This is the wrapper of the Message itself
    */
   private MessageUnitWrapper messageUnitWrapper = null; // this variable may be null
                                              // always use the getMessageUnitWrapper() access method, this checks for null

   private String uniqueKey;                  // Attribute oid of key tag: <key oid="..."> </key>

   private boolean handlerIsNewCreated=true;  // a little helper for RequestBroker, showing if MessageUnit is new created


   /**
    * Use this constructor if a subscription is made on a yet unknown object.
    * <p />
    * @param requestBroker
    * @param uniqueKey The unique XmlKey-oid from the subscribe() call
    */
   public MessageUnitHandler(RequestBroker requestBroker, String uniqueKey) throws XmlBlasterException
   {
      if (requestBroker == null || uniqueKey == null) {
         Log.error(ME, "Invalid constructor parameter");
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }

      this.requestBroker = requestBroker;
      this.uniqueKey = uniqueKey;

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler because of subscription. Key=" + uniqueKey);

      // mimeType and content remains unknown until first data is fed
   }


   /**
    * Use this constructor if a yet unknown object is fed by method publish().
    * <p />
    * @param requestBroker
    * @param a MessageUnitWrapper containing the CORBA MessageUnit data container
    */
   public MessageUnitHandler(RequestBroker requestBroker, MessageUnitWrapper messageUnitWrapper) throws XmlBlasterException
   {
      if (requestBroker == null || messageUnitWrapper == null) {
         Log.error(ME, "Invalid constructor parameters");
         throw new XmlBlasterException(ME, "Invalid constructor parameters");
      }

      this.requestBroker = requestBroker;
      this.messageUnitWrapper = messageUnitWrapper;
      this.uniqueKey = messageUnitWrapper.getXmlKey().getUniqueKey();

      if (Log.CALLS) Log.trace(ME, "Creating new MessageUnitHandler setting new data. Key=" + uniqueKey);
   }


   /**
    * Accessing the wrapper object of the MessageUnit
    * @return true a MessageUnit object is published, so you may do updates<br />
    *         false this handler holds subscriptions only, no message content is yet known
    */
   public final boolean isPublishedWithData()
   {
      return messageUnitWrapper != null;
   }


   /**
    * Accessing the wrapper object of the MessageUnit
    * @return MessageUnitWrapper object
    */
   final MessageUnitWrapper getMessageUnitWrapper() throws XmlBlasterException
   {
      if (messageUnitWrapper == null) {
         Log.error(ME + ".EmptyMessageUnit", "Internal problem, messageUnit = null, there was not yet any message published, only subscription exists on this unpublished message");
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, messageUnitWrapper = null");
      }
      return messageUnitWrapper;
   }


   /**
    * Accessing the key of this message
    */
   public final XmlKey getXmlKey() throws XmlBlasterException
   {
      return getMessageUnitWrapper().getXmlKey();
   }


   /**
    * Accessing the key of this message.
    * <p />
    * Convenience if the caller is too lazy to catch exceptions
    * @return null
    */
   public final XmlKey getXmlKeyOrNull()
   {
      if (!isPublishedWithData())
         return null;
      try {
         return getMessageUnitWrapper().getXmlKey();
      } catch (Exception e) {
         return null;
      }
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

      try {
         getMessageUnitWrapper().erase();
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Problems erasing message: " + e.reason);
      }

      messageUnitWrapper = null;
      uniqueKey = null;
   }


   /**
    * Setting update of a new content.
    *
    * @param xmlKey      The XmlKey object, derived from messageUnit.xmlKey string
    * @param messageUnit The CORBA MessageUnit struct
    * @param publishQoS  Quality of Service, flags to control the publishing
    * @param publisherName The source of the data (unique login name)
    *
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(XmlKey xmlKey, MessageUnit messageUnit, PublishQoS publishQoS, String publisherName) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Setting content of xmlKey " + uniqueKey);

      if (messageUnitWrapper == null) {  // storing the key from the first publish() invocation
         messageUnitWrapper = new MessageUnitWrapper(requestBroker, xmlKey, messageUnit, publishQoS, publisherName);
         return true;
      }

      return messageUnitWrapper.setContent(messageUnit.content, publisherName);
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
   public final org.w3c.dom.Node getRootNode() throws XmlBlasterException
   {
      return getXmlKey().getRootNode();
   }


   /**
    * A client subscribed to this message
    *
    * @return true new subscription added <br />
    *         false client had already subscribed
    */
   public boolean addSubscriber(SubscriptionInfo sub) throws XmlBlasterException
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
         return false;
      }

      sub.addMessageUnitHandler(this);

      if (Log.TRACE) Log.trace(ME, "You have successfully subscribed to " + uniqueKey);

      invokeCallback(sub);
      return true;
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
    * This is the unique key of the MessageUnit
    * <p />
    * @return the &lt;key oid='...'>
    */
   public String getUniqueKey()
   {
      return uniqueKey;
   }


   /**
    * What is the MIME type of this message content?
    * <p />
    * @return the MIME type of the MessageUnit.content
    */
   public String getContentMime() throws XmlBlasterException
   {
      return getXmlKey().getContentMime();
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
    * Access the raw CORBA messageUnit
    * @return MessageUnit object
    */
   public MessageUnit getMessageUnit() throws XmlBlasterException
   {
      return getMessageUnitWrapper().getMessageUnit();
   }


   /**
    * Send updates to all subscribed clients.
    * <p />
    * The whole update blocks if one client would block - to avoid this the IDL update()
    * method is marked <code>oneway</code>
    */
   public void invokeCallback() throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Going to update dependent clients, subscriberMap.size() = " + subscriberMap.size());

      // PERFORMANCE: All updates for each client should be collected !!!
      //              This "Burst mode" code increase performance if the messages are small
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
      if (!isPublishedWithData()) {
         if (Log.TRACE) Log.trace(ME, "invokeCallback() not supported, this MessageUnit was created by a subscribe() and not a publish()");
         return;
      }
      ClientInfo clientInfo = sub.getClientInfo();
      clientInfo.sendUpdate(getMessageUnitWrapper(), (Destination)null);
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

      if (messageUnitWrapper == null)
         sb.append(offset + "   <MessageUnitWrapper>null</MessageUnitWrapper>");
      else
         sb.append(getMessageUnitWrapper().printOn(extraOffset + "   ").toString());

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
