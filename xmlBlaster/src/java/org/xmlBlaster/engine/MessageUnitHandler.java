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
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.qos.UpdateQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.qos.UpdateReturnQosServer;

import java.util.*;


/**
 * Handles a MessageUnit and its subscribers. 
 */
public class MessageUnitHandler
{
   private String ME = "MessageUnitHandler";
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
      this.ME += this.glob.getLogPrefixDashed() + "/msg/" + uniqueKey;
      this.requestBroker = requestBroker;
      this.uniqueKey = uniqueKey;

      if (log.CALL) log.trace(ME, "Creating new MessageUnitHandler because of subscription.");

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
      this.ME += this.glob.getLogPrefixDashed() + "/msg/" + xmlKey.getUniqueKey();
      this.msgUnitWrapper = msgUnitWrapper;
      this.msgUnitWrapper.setMessageUnitHandler(this);
      this.uniqueKey = this.xmlKey.getUniqueKey();

      if (log.CALL) log.trace(ME, "Creating new MessageUnitHandler setting new data.");
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
      //this.xmlKey = null; Keep if erased but still existent in some callback queues
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
      //boolean isVolatile = getMessageUnitWrapper().getPublishQos.getIsVolatile();

      try {
         if (isPublishedWithData())
            getMessageUnitWrapper().erase();
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Problems erasing message: " + e.getMessage());
      }

      setIsNotPublishedWithData();

      synchronized(subscriberMap) {
         /*
         if (subscriberMap.size() == 0) { // No subscription reservation is existing?
            this.uniqueKey = null;
         }
         */
         subscriberMap.clear();
      }
   }

   /**
    * Setting update of a new content.
    *
    * @param xmlKey      The XmlKey object, derived from msgUnit.getKey() string
    * @param msgUnitWr     The MessageUnitWrapper instance
    * @param publishQos  Quality of Service, flags to control the publishing
    *
    * @return changed? true:  if content has changed
    *                  false: if content didn't change
    */
   public boolean setContent(XmlKey unparsedXmlKey, MessageUnitWrapper msgUnitWr, PublishQosServer publishQos) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Setting content");
      if (this.xmlKey == null) this.xmlKey = unparsedXmlKey; // If MessageUnitHandler existed because of a subscription: remember xmlKey on first publish

      if (publishQos.isReadonly() && isPublishedWithData()) {
         log.warn(ME+".Readonly", "Sorry, published message '" + xmlKey.getUniqueKey() + "' rejected, message is readonly.");
         throw new XmlBlasterException(ME+".Readonly", "Sorry, published message '" + xmlKey.getUniqueKey() + "' rejected, message is readonly.");
      }

      boolean changed = (this.msgUnitWrapper != null) ? !this.msgUnitWrapper.sameContent(msgUnitWr.getMessageUnit().getContent()) : true;

      this.msgUnitWrapper = msgUnitWr;
      this.msgUnitWrapper.setMessageUnitHandler(this);
      
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
    */
   public void addSubscriber(SubscriptionInfo sub) throws XmlBlasterException
   {
      if (sub.getSubscribeCounter() > 1)
         return;

      Object oldOne;
      synchronized(subscriberMap) {
         oldOne = subscriberMap.put(sub.getSubscriptionId(), sub);
      }

      sub.addMessageUnitHandler(this);

      if (log.TRACE) log.trace(ME, "Client '" + sub.getSessionInfo().getLoginName() + "' has successfully subscribed to '" + uniqueKey + "'");

      if (sub.getQueryQosData().getWantInitialUpdate() == true) {
         if (invokeCallback(null, sub, Constants.STATE_OK) == false) {
            Set removeSet = new HashSet();
            removeSet.add(sub);
            handleCallbackFailed(removeSet);
         }
      }

      return;
   }

   /**
    * If a callback fails, we remove it from the subscription. 
    * <p />
    * Generating dead letter and auto-logout to release all resources is done by DeliveryWorker.
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

      if (!isPublishedWithData() && !hasSubscribers()) {
         try {
            // clean up previously erased message as no subscribers exist any more
            // we use internal session to assure we are authorized to do it
            SessionInfo sessionInfo = requestBroker.getInternalSessionInfo();
            /*
            EraseQosServer eraseQos = new EraseQosServer(glob);
            eraseQos.setNotify(false);
            String[] dummy = requestBroker.erase(requestBroker.getInternalSessionInfo(), xmlKey, eraseQos);
            */
            if (log.TRACE) log.trace(ME, "Erasing message '" + getUniqueKey() + "' with last subscriber disappearing");
            requestBroker.fireMessageEraseEvent(sessionInfo, this, null); // Constants.STATE_ERASED);
         }
         catch(XmlBlasterException e) {
            log.error(ME, "Internal problem erasing the message skeleton of '" + getUniqueKey() + "', ignoring problem: " + e.toString());
            e.printStackTrace();
         }
      }
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
    * @param state The state of the message on update, e.g. Constants.STATE_OK
    */
   public final void invokeCallback(SessionInfo publisherSessionInfo, String state) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Going to update dependent clients, subscriberMap.size() = " + subscriberMap.size());

      // Take a copy of the map entries (a current snapshot)
      // If we would iterate over the map directly we can risk a java.util.ConcurrentModificationException
      // when one of the callback fails and the entry is removed by the callback worker thread
      SubscriptionInfo[] subInfoArr;
      synchronized(subscriberMap) {
         subInfoArr = (SubscriptionInfo[])subscriberMap.values().toArray(new SubscriptionInfo[subscriberMap.size()]);
      }

      Set removeSet = null;
      for (int ii=0; ii<subInfoArr.length; ii++) {
         SubscriptionInfo sub = subInfoArr[ii];
         if (invokeCallback(publisherSessionInfo, sub, state) == false) {
            if (removeSet == null) removeSet = new HashSet();
            removeSet.add(sub); // We can't delete directly since we are in the iterator
         }
      }
      if (removeSet != null) handleCallbackFailed(removeSet);
   }

   /**
    * Send update to subscribed client (Pub/Sub mode only).
    * @param publisherSessionInfo The sessionInfo of the publisher or null if not known or not online
    * @param sub The subscription handle of the client
    * @param state The status of the message e.g. Constants.STATE_OK or Constants.STATE_ERASED="ERASED"
    * @return false if the callback failed, throws never an exception
    */
   private final boolean invokeCallback(SessionInfo publisherSessionInfo, SubscriptionInfo sub, String state)
   {
      try {
         if (!isPublishedWithData()) {
            if (log.TRACE) log.trace(ME, "invokeCallback() not supported, this MessageUnit was created by a subscribe() and not a publish()");
            return true;
         }

         if (sub.getSessionInfo().getSubjectInfo().isCluster()) {
            if (log.DUMP) log.dump(ME, "Slave node '" + sub.getSessionInfo() + "' has dirty read message '" +
                                       sub.getMessageUnitWrapper().toXml());
            if (msgUnitWrapper.getPublishQos().dirtyRead(sub.getSessionInfo().getSubjectInfo().getNodeId())) {
               if (log.TRACE) log.trace(ME, "Slave node '" + sub.getSessionInfo() + "' has dirty read message '" + sub.getSubscriptionId() + "', '" + sub.getXmlKey().getKeyOid() + "' we don't need to send it back");
               return true;
            }
         }

         UpdateQosServer.setData(msgUnitWrapper.getPublishQos().getData(), state, sub.getSubSourceSubscriptionId());

         AccessFilterQos[] filterQos = sub.getAccessFilterArr();
         if (filterQos != null) {
            //SubjectInfo publisher = (publisherSessionInfo == null) ? null : publisherSessionInfo.getSubjectInfo();
            //SubjectInfo destination = (sub.getSessionInfo() == null) ? null : sub.getSessionInfo().getSubjectInfo();
            for (int ii=0; ii<filterQos.length; ii++) {
               XmlKey key = sub.getMessageUnitHandler().getXmlKey(); // This key is DOM parsed
               try {
                  I_AccessFilter filter = requestBroker.getAccessPluginManager().getAccessFilter(
                                            filterQos[ii].getType(), filterQos[ii].getVersion(), 
                                            xmlKey.getContentMime(), xmlKey.getContentMimeExtended());
                  if (filter != null && filter.match(publisherSessionInfo, sub.getSessionInfo(),
                                                     msgUnitWrapper, filterQos[ii].getQuery()) == false)
                     return true; // filtered message is not send to client
               }
               catch (Throwable e) {
                  if (log.TRACE) log.trace(ME, "Mime access filter '" + filterQos[ii].getType() + " threw an exception: " + e.toString()); 
                  // sender =      publisherSessionInfo.getLoginName()
                  // receiver =    sub.getSessionInfo().getLoginName()
                  MsgQueueEntry entry = //new MsgQueuePublishEntry(glob, msgUnitWrapper.getMessageUnit(), sub.getMsgQueue());
                       new MsgQueueUpdateEntry(glob, msgUnitWrapper.getMessageUnit(),
                        sub.getMsgQueue(), msgUnitWrapper.getUniqueKey(), msgUnitWrapper.getPublishQos().getData(),
                        sub.getSessionInfo().getSessionName());
                  publisherSessionInfo.getMsgErrorHandler().handleError(new MsgErrorInfo(glob, entry, e));
                  return true;
               }
            }
         }

         if (log.CALL) log.call(ME, "pushing update() message '" + sub.getXmlKey().getKeyOid() + "' state '" + state + "' into '" + sub.getSessionInfo().getId() + "' callback queue");
         
         UpdateReturnQosServer retQos = (UpdateReturnQosServer)sub.getMsgQueue().put(
              new MsgQueueUpdateEntry(glob, msgUnitWrapper.getMessageUnit(),
                  sub.getMsgQueue(), msgUnitWrapper.getUniqueKey(), msgUnitWrapper.getPublishQos().getData(),
                  sub.getSessionInfo().getSessionName())
                   , false);

         sub.getSessionInfo().getDeliveryManager().notifyAboutNewEntry();
      }
      catch (Throwable e) {
         e.printStackTrace();
         if (log.TRACE) log.trace(ME, "Sending of message from " + publisherSessionInfo.getId() + " to " +
                            sub.getSessionInfo().getId() + " failed: " + e.toString());
         sub.getSessionInfo().getDeliveryManager().internalError(e); // calls MsgErrorHandler
         return false;
      }
      return true;
   }

   public final int numSubscribers() {
      return subscriberMap.size();
   }

   public final boolean hasSubscribers() {
      return subscriberMap.size() != 0;
   }

   /**
    * Returns true if there are subscribers with exact query on oid or domain
    * @return false If no subscriber exists or all subscribers are through XPath query
    */
   public final boolean hasExactSubscribers() {
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (!sub.isCreatedByQuerySubscription())
               return true;
         }
      }
      return false;
   }

   /**
    * Returns a Vector with SubscriptionInfo instances of this session
    * (a session may subscribe the same message multiple times). 
    * <p />
    * This searches from a given SessionInfo.
    */
   public final Vector findSubscriber(SessionInfo sessionInfo) {
      Vector vec = null;
      synchronized(subscriberMap) {
         Iterator iterator = subscriberMap.values().iterator();
         while (iterator.hasNext()) {
            SubscriptionInfo sub = (SubscriptionInfo)iterator.next();
            if (sub.getSessionInfo().isSameSession(sessionInfo)) {
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
               sb.append(offset + "   <SubscriptionInfo id='").append(subs.getSubscriptionId()).append("/>");
               //sb.append(subs.toXml(extraOffset + "   "));
            }
         }
      }

      sb.append(offset + "   <handlerIsNewCreated>" + handlerIsNewCreated + "</handlerIsNewCreated>");
      sb.append(offset + "</MessageUnitHandler>\n");
      return sb.toString();
   }
}
