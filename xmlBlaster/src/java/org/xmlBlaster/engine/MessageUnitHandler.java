/*------------------------------------------------------------------------------
Name:      MessageUnitHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling exactly one message content
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.engine.mime.I_AccessFilter;

import java.util.*;


/**
 * Handles a MessageUnit and its subscribers. 
 */
public class MessageUnitHandler
{
   private final static String ME = "MessageUnitHandler";
   private final Global glob;
   private final LogChannel log;

   /** The broker which manages me */
   private final RequestBroker requestBroker;

   // Default is that a single client can subscribe the same message multiple times
   // private boolean allowMultiSubscriptionPerClient = glob.getProperty().get("Engine.allowMultiSubscriptionPerClient", true);

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
    * This is the wrapper of the Message itself. <p/>
    * This variable may be null, always use the getMessageUnitWrapper() access method, this checks for null
    */
   private MessageUnitWrapper msgUnitWrapper = null; 

   /** The xmlKey with parsed DOM tree */
   private XmlKey xmlKey;
   /** Attribute oid of key tag: <key oid="..."> </key> */
   private String uniqueKey;

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
         Global.instance().getLog("core").error(ME, "Invalid constructor parameter");
         throw new XmlBlasterException(ME, "Invalid constructor parameter");
      }

      this.glob = requestBroker.getGlobal();
      this.log = glob.getLog("core");
      this.requestBroker = requestBroker;
      this.uniqueKey = uniqueKey;

      if (log.CALL) log.trace(ME, "Creating new MessageUnitHandler because of subscription. Key=" + uniqueKey);

      // mimeType and content remains unknown until first data is fed
   }

   /**
    * Use this constructor if a yet unknown object is fed by method publish().
    * <p />
    * @param requestBroker
    * @param xmlKey On first occurrence of a message the XmlKey contains the parsed DOM
    * @param a MessageUnitWrapper containing the CORBA MessageUnit data container
    */
   public MessageUnitHandler(RequestBroker requestBroker, XmlKey xmlKey, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException
   {
      if (requestBroker == null || xmlKey == null || msgUnitWrapper == null) {
         Global.instance().getLog("core").error(ME, "Invalid constructor parameters");
         throw new XmlBlasterException(ME, "Invalid constructor parameters");
      }
      this.glob = requestBroker.getGlobal();
      this.log = glob.getLog("core");
      this.requestBroker = requestBroker;
      this.xmlKey = xmlKey;
      this.msgUnitWrapper = msgUnitWrapper;
      this.msgUnitWrapper.setMessageUnitHandler(this);
      this.uniqueKey = this.xmlKey.getUniqueKey();

      if (log.CALL) log.trace(ME, "Creating new MessageUnitHandler setting new data. Key=" + uniqueKey);
   }

   public void finalize()
   {
      if (log.TRACE) log.trace(ME, "finalize - garbage collect " + uniqueKey);
   }

   public RequestBroker getRequestBroker()
   {
      return this.requestBroker;
   }

   /**
    * Check if this MessageUnit is already published and contains correct data.
    * @return true a MessageUnit object is published, so you may do updates<br />
    *         false this handler holds subscriptions only, no message content is yet known
    */
   public final boolean isPublishedWithData()
   {
      return msgUnitWrapper != null;
   }

   private final void setIsNotPublishedWithData()
   {
      this.xmlKey = null;
      this.msgUnitWrapper = null;
   }


   /**
    * Accessing the wrapper object of the MessageUnit
    * @return MessageUnitWrapper object
    * @exception XmlBlasterException if MessageUnitWrapper is unknown
    */
   public final MessageUnitWrapper getMessageUnitWrapper() throws XmlBlasterException
   {
      if (msgUnitWrapper == null) {
         log.error(ME + ".EmptyMessageUnit", "Internal problem, msgUnit = null, there was not yet any message published, only subscription exists on this unpublished message:\n" + toXml() + "\n" + org.jutils.runtime.StackTrace.getStackTrace());
         throw new XmlBlasterException(ME + ".EmptyMessageUnit", "Internal problem, msgUnitWrapper = null");
      }
      return msgUnitWrapper;
   }

   /**
    * Accessing the DOM parsed key of this message. 
    */
   public final XmlKey getXmlKey() throws XmlBlasterException
   {
      return this.xmlKey;
   }

   /**
    * Accessing the key of this message.
    * <p />
    * Convenience if the caller is too lazy to catch exceptions
    * @return null            !!!! REMOVE
    */
   public final XmlKey getXmlKeyOrNull()
   {
      if (!isPublishedWithData())
         return null;
      return this.xmlKey;
   }

   /**
    * Clean up everything, since i will be deleted now
    */
   public void erase() throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Entering erase()");

      log.warn(ME, "No subscribed client notification about message erase() yet implemented");

      /*
      SubscriptionInfo[] arr = null; // to avoid deadlock in subscriberMap, copy subs into this array
      synchronized(subscriberMap) {
         arr = new SubscriptionInfo[subscriberMap.size()];
         Iterator iterator = subscriberMap.values().iterator();
         int jj=0;
         while (iterator.hasNext()) {
            SubscriptionInfo subs = (SubscriptionInfo)iterator.next();
            arr[jj++] = subs;
         }
      }


      for (int ii=0; ii<arr.length; ii++) {
         if (arr[ii].isQuery()) {
            log.info(ME, "Query subscription is not deleted when messages is erased");
         }
         else {
            // !!!! Keep subscriptions ruff 2002-04-26
            //   TODO: check behavior
            //requestBroker.fireUnSubscribeEvent(arr[ii]);
            synchronized(subscriberMap) {
               subscriberMap.remove(arr[ii].getUniqueKey());
            }
         }
      }
      */

      try {
         getMessageUnitWrapper().erase();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Problems erasing message: " + e.reason);
      }

      setIsNotPublishedWithData();

      synchronized(subscriberMap) {
         if (subscriberMap.size() == 0) { // No subscription reservation is existing?
            this.uniqueKey = null;
         }
         //subscriberMap.clear();
      }
   }

   /**
    * Setting update of a new content.
    *
    * @param xmlKey      The XmlKey object, derived from msgUnit.xmlKey string
    * @param msgUnit The CORBA MessageUnit struct
    * @param publishQos  Quality of Service, flags to control the publishing
    *
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(XmlKey unparsedXmlKey, MessageUnit msgUnit, PublishQos publishQos) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Setting content of xmlKey " + uniqueKey);
      if (this.xmlKey == null) this.xmlKey = unparsedXmlKey; // If MessageUnitHandler existed because of a subscription: remember xmlKey on first publish

      if (publishQos.readonly() && isPublishedWithData()) {
         log.warn(ME+".Readonly", "Sorry, published message '" + xmlKey.getUniqueKey() + "' rejected, message is readonly.");
         throw new XmlBlasterException(ME+".Readonly", "Sorry, published message '" + xmlKey.getUniqueKey() + "' rejected, message is readonly.");
      }

      boolean changed = (msgUnitWrapper != null) ? !msgUnitWrapper.sameContent(msgUnit.getContent()) : true;

      msgUnitWrapper = new MessageUnitWrapper(requestBroker, xmlKey, msgUnit, publishQos);
      msgUnitWrapper.setMessageUnitHandler(this);
      
      return changed;
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
    * A client subscribed to this message, multiple subscriptions from
    * the same client are OK.
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

      sub.addMessageUnitHandler(this);

      if (log.TRACE) log.trace(ME, "Client '" + sub.getSessionInfo().getLoginName() + "' has successfully subscribed to '" + uniqueKey + "'");

      if (invokeCallback(null, sub) == false) {
         Set removeSet = new HashSet();
         removeSet.add(sub);
         handleCallbackFailed(removeSet);
      }

      return true;
   }

   /**
    * If a callback fails, we remove it from the subscription. 
    * <p />
    * Generating dead letter and auto-logout to release all resources is done by CbWorker.
    */
   private void handleCallbackFailed(Set removeSet) throws XmlBlasterException
   {
      if (removeSet != null) {
         Iterator iterator = removeSet.iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            log.info(ME, "Removed subcriber [" + sub.getSessionInfo().getLoginName() + "] from message '" + sub.getXmlKey().getKeyOid() + "'");
            sub.removeSubscribe();
         }
         removeSet.clear();
         removeSet = null;
      }
   }

   /**
    * A client wants to unSubscribe from this message
    * @return the removed SubscriptionInfo object or null if not found
    */
   SubscriptionInfo removeSubscriber(String subscriptionInfoUniqueKey)
   {
      if (log.TRACE) log.trace(ME, "Before size of subscriberMap = " + subscriberMap.size());

      SubscriptionInfo subs = null;
      synchronized(subscriberMap) {
         subs = (SubscriptionInfo)subscriberMap.remove(subscriptionInfoUniqueKey);
      }
      if (subs == null)
         log.warn(ME + ".DoesntExist", "Sorry, can't unsubscribe, you where not subscribed to subscription ID=" + subscriptionInfoUniqueKey);

      if (log.TRACE) log.trace(ME, "After size of subscriberMap = " + subscriberMap.size());
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
    /*
   public Map getSubscriberMap()
   {
      return subscriberMap;
   }
     */
   /**
    * Access the raw CORBA msgUnit
    * @return MessageUnit object
    */
   public MessageUnit getMessageUnit() throws XmlBlasterException
   {
      return getMessageUnitWrapper().getMessageUnit();
   }

   /**
    * Send updates to all subscribed clients.
    * <p />
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    */
   public final void invokeCallback(SessionInfo publisherSessionInfo) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Going to update dependent clients, subscriberMap.size() = " + subscriberMap.size());

      Set removeSet = null;
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();

         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (invokeCallback(publisherSessionInfo, sub) == false) {
               if (removeSet == null) removeSet = new HashSet();
               removeSet.add(sub); // We can't delete directly since we are in the iterator
            }
         }
      }
      if (removeSet != null) handleCallbackFailed(removeSet);
   }

   /**
    * Send update to subscribed client (Pub/Sub mode only).
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    * @param sub The subscription handle of the client
    * @return false if the callback failed
    */
   private final boolean invokeCallback(SessionInfo publisherSessionInfo, SubscriptionInfo sub) throws XmlBlasterException
   {
      if (!isPublishedWithData()) {
         if (log.TRACE) log.trace(ME, "invokeCallback() not supported, this MessageUnit was created by a subscribe() and not a publish()");
         return true;
      }

      AccessFilterQos[] filterQos = sub.getFilterQos();
      if (filterQos != null) {
         SubjectInfo publisher = (publisherSessionInfo == null) ? null : publisherSessionInfo.getSubjectInfo();
         SubjectInfo destination = (sub.getSessionInfo() == null) ? null : sub.getSessionInfo().getSubjectInfo();
         for (int ii=0; ii<filterQos.length; ii++) {
            XmlKey key = sub.getMessageUnitHandler().getXmlKey(); // This key is DOM parsed
            I_AccessFilter filter = requestBroker.getAccessPluginManager().getAccessFilter(
                                      filterQos[ii].getType(), filterQos[ii].getVersion(), 
                                      xmlKey.getContentMime(), xmlKey.getContentMimeExtended());
            if (filter != null && filter.match(publisher, destination, msgUnitWrapper, filterQos[ii].getQuery()) == false)
               return true; // filtered message is not send to client
         }
      }

      try {
         sub.getMsgQueue().putMsg(new MsgQueueEntry(glob, sub, msgUnitWrapper));
      }
      catch(XmlBlasterException e) {
         if (e.id.equals("CallbackFailed")) {
            log.warn(ME, e.toString());
            // Error handling is to unsubscribe this client
            return false;
         }
         else
            throw e;
      }
      return true;
   }

   /**
    * Returns a Vector with SubscriptionInfo instances of this session
    * (a session may subscribe the same message multiple times)
    */
   final Vector findSubscriber(SessionInfo sessionInfo) {
      Vector vec = null;
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().getUniqueKey().equals(sessionInfo.getUniqueKey())) {
               if (vec == null) vec = new Vector();
               vec.addElement(sub);
            }
         }
      }
      return vec;
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
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into XML.
    * <br>
    * @param extraOffset indenting of tags
    * @return XML state of MessageUnitHandler
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset + "<MessageUnitHandler>");
      sb.append(offset + "   <uniqueKey>" + getUniqueKey() + "</uniqueKey>");

      if (msgUnitWrapper == null)
         sb.append(offset + "   <MessageUnitWrapper>null</MessageUnitWrapper>");
      else
         sb.append(getMessageUnitWrapper().toXml(extraOffset + "   "));

      if (subscriberMap.size() == 0)
         sb.append(offset + "   <SubscriptionInfo>NO SUBSCRIPTIONS</SubscriptionInfo>");
      else {
         synchronized(subscriberMap) {
            Iterator iterator = subscriberMap.values().iterator();
            while (iterator.hasNext()) {
               SubscriptionInfo subs = (SubscriptionInfo)iterator.next();
               sb.append(offset + "   <SubscriptionInfo id='").append(subs.getUniqueKey()).append("/>");
               //sb.append(subs.toXml(extraOffset + "   "));
            }
         }
      }

      sb.append(offset + "   <handlerIsNewCreated>" + handlerIsNewCreated + "</handlerIsNewCreated>");
      sb.append(offset + "</MessageUnitHandler>\n");
      return sb.toString();
   }
}
