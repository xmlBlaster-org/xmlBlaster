/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: RequestBroker.java,v 1.40 1999/12/12 13:29:08 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlToDom;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.serverIdl.XmlBlasterException;
import org.xmlBlaster.serverIdl.MessageUnit;
import org.xmlBlaster.clientIdl.BlasterCallback;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import java.util.*;
import java.io.*;

/**
 * This is the central message broker, all requests are routed through this singleton.
 * <p>
 * The interface ClientListener informs about Client login/logout<br />
 * The interface MessageEraseListener informs when a MessageUnit is erased<br />
 * <p>
 * Most events are fired from the RequestBroker
 *
 * @version $Revision: 1.40 $
 * @author $Author: ruff $
 */
public class RequestBroker implements ClientListener, MessageEraseListener
{
   final private static String ME = "RequestBroker";

   /** myself, singleton pattern */
   private static RequestBroker requestBroker = null;

   /** the authentication service */
   private Authenticate authenticate = null;          // The authentication service

   /**
    * All MessageUnitHandler objects are stored in this map.
    * <p>
    * key   = messageUnithandler.getUniqueKey() == xmlKey.getUniqueKey() == oid value from <key oid="...">
    * value = MessageUnitHandler object
    */
   final private Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   /**
    * Helper to handle the subscriptions
    */
   final private ClientSubscriptions clientSubscriptions;

   /**
    * For listeners who want to be informed about subscribe/unsubscribe events
    */
   final private Set subscriptionListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * For listeners who want to be informed about erase() of messages.
    */
   final private Set messageEraseListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * This is a handle on the big DOM tree with all XmlKey keys (all message meta data)
    */
   private BigXmlKeyDOM bigXmlKeyDOM = null;


   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance(Authenticate authenticate) throws XmlBlasterException
   {
      synchronized (RequestBroker.class) {
         if (requestBroker == null) {
            requestBroker = new RequestBroker(authenticate);
         }
      }
      return requestBroker;
   }


   /**
    * Access to RequestBroker singleton
    */
   public static RequestBroker getInstance()
   {
      synchronized (RequestBroker.class) {
         if (requestBroker == null) {
            Log.panic(ME, "Use other getInstance first");
         }
      }
      return requestBroker;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.clientSubscriptions = ClientSubscriptions.getInstance(this, authenticate);

      this.bigXmlKeyDOM = BigXmlKeyDOM.getInstance(this, authenticate);

      this.authenticate = authenticate;

      authenticate.addClientListener(this);
      addMessageEraseListener(this);
   }


   /**
    * Setting attributes for a client.
    * <p>
    * NOTE: This method is under construction, don't use it yet.
    *
    * @param clientName  The client which shall be administered
    * @param xmlAttr     the attributes of the client in xml syntax like group/role infos<br>
    *                    They are later queryable with XPath syntax<p>
    * <pre>
    *    &lt;client name='tim'>
    *       &lt;group>
    *          Marketing
    *       &lt;/group>
    *       &lt;role>
    *          Managing director
    *       &lt;/role>
    *    &lt;/client>
    * </pre>
    * @param qos         Quality of Service, flags for additional informations to control administration
    */
   public void setClientAttributes(String clientName, String xmlAttr_literal,
                            String qos_literal) throws XmlBlasterException
   {
      // !!! TODO
      Log.warning(ME, "setting client attributes is not yet supported: " + xmlAttr_literal);
   }


   /**
    * Invoked by a client, to subscribe to one/many MessageUnit.
    * <p />
    * Asynchronous read-access method.
    * <p>
    * In CORBA connection mode, the results are returned to the
    * Client-Callback interface via the update() method.
    * You need to implement the method BlasterCallback.update()<br />
    * This is the push modus.
    * <p>
    * Duplicate subscriptions are silently ignored (no Exception is thrown)
    * <br>
    *
    * @param xmlKey  Key allowing XPath or exact selection<br>
    *                See XmlKey.dtd for a description
    * @param qos     Quality of Service, flags to control subscription<br>
    *                See XmlQoS.dtd for a description, XmlQoS.xml for examples<p />
    *         Example (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;NoMeta />       &lt;!-- Don't send me the key meta data on updates -->
    *       &lt;NoContent />    &lt;!-- Don't send me the content data on updates (notify only) -->
    *       &lt;NoLocal />      &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
    *    &lt;/qos>
    * </pre>
    * @return oid    The oid of your subscribed Message<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object is returned.<br>
    *                You should use this oid if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    *
    * @see addListener in Java event model
    * @see addObserver in Java observer pattern
    */
   public String subscribe(ClientInfo clientInfo, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException
   {
      String returnOid = "";
      if (xmlKey.getQueryType() != XmlKey.EXACT_QUERY) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
         returnOid = xmlKey.getUniqueKey();
         fireSubscriptionEvent(new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS), true);
      }

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, subscribeQoS);

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.getQueryType() == XmlKey.EXACT_QUERY) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKeyExact, subscribeQoS);
         subscribeToOid(subs);                // fires event for subscription

         if (returnOid.equals("")) returnOid = xmlKeyExact.getUniqueKey();
      }

      return returnOid;
   }


   /**
    * Invoked by a client, to access one/many MessageUnit.
    * <p />
    * Synchronous read-access method.
    * <p>
    *
    * @param xmlKey  Key allowing XPath or exact selection<br>
    *                See XmlKey.dtd for a description
    * @param qos     Quality of Service, flags to control subscription<br>
    *                See XmlQoS.dtd for a description, XmlQoS.xml for examples<p />
    *         Example (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;NoMeta />       &lt;!-- Don't send me the key meta data on updates -->
    *       &lt;NoContent />    &lt;!-- Don't send me the content data on updates (notify only) -->
    *    &lt;/qos>
    * </pre>
    * @return A sequence of 0 - n MessageUnit structs
    */
   public MessageUnit[] get(ClientInfo clientInfo, XmlKey xmlKey, GetQoS subscribeQoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, subscribeQoS);
      MessageUnit[] messageUnitArr = new MessageUnit[xmlKeyVec.size()];

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.getQueryType() == XmlKey.EXACT_QUERY) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;

         MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
         messageUnitArr[ii] = messageUnitHandler.getMessageUnit();;
      }

      return messageUnitArr;
   }


   /**
    * This method does the query (queryType = "XPATH" | "EXACT").
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   private Vector parseKeyOid(ClientInfo clientInfo, XmlKey xmlKey, XmlQoSBase qos)  throws XmlBlasterException
   {
      Vector xmlKeyVec = null;
      String clientName = clientInfo.toString();

      if (xmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid
         xmlKeyVec = bigXmlKeyDOM.parseKeyOid(clientInfo, xmlKey, qos);
      }

      else if (xmlKey.getQueryType() == XmlKey.EXACT_QUERY) { // subscription with a given oid
         if (Log.TRACE) Log.trace(ME, "Access Client " + clientName + " with EXACT oid=\"" + xmlKey.getUniqueKey() + "\"");
         XmlKey xmlKeyExact = getXmlKeyFromOid(xmlKey.getUniqueKey());
         xmlKeyVec = new Vector();
         xmlKeyVec.addElement(xmlKeyExact);
      }

      else {
         Log.warning(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }

      if (Log.TRACE) Log.trace(ME, "Found " + ((xmlKeyVec == null) ? 0 : xmlKeyVec.size()) + " matching subscriptions");

      return xmlKeyVec == null ? new Vector() : xmlKeyVec;
   }


   /**
    * Try to access the XmlKey by its oid.
    *
    * @param oid  This is the XmlKey.uniqueKey
    * @return the XmlKey object if found in the Map<br />
    *         or null if not found
    */
   public final XmlKey getXmlKeyFromOid(String oid) throws XmlBlasterException
   {
      MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(oid);
      if (messageUnitHandler == null) {
         return null;
      }
      return messageUnitHandler.getXmlKeyOrNull();
   }


   /**
    * @param oid  This is the XmlKey:uniqueKey
    * @return null if not found
    */
   public final MessageUnitHandler getMessageHandlerFromOid(String oid)
   {
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(oid);
         if (obj == null) {
            if (Log.TRACE) Log.trace(ME, "getMessageHandlerFromOid(): key oid " + oid + " is unknown, messageUnitHandler == null");
            return null;
         }
         return (MessageUnitHandler)obj;
      }
   }


   /**
    * Low level subscribe, is called when the <key oid='...' queryType='EXACT'> to subscribe is exactly known.
    * <p>
    * @param uniqueKey from XmlKey - oid
    * @param subs
    */
   private void subscribeToOid(SubscriptionInfo subs) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering subscribeToOid() ...");
      String uniqueKey = subs.getXmlKey().getUniqueKey();
      MessageUnitHandler messageUnitHandler;
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            // This is a new Message, yet unknown ...
            messageUnitHandler = new MessageUnitHandler(this, subs.getXmlKey().getUniqueKey());
            messageContainerMap.put(uniqueKey, messageUnitHandler);
         }
         else {
            // This message was known before ...
            messageUnitHandler = (MessageUnitHandler)obj;
         }
      }

      // Now the MessageUnit exists, subscribe to it
      boolean newSubscribed = messageUnitHandler.addSubscriber(subs);

      if (!newSubscribed) return;         // client had already subscribed

      fireSubscriptionEvent(subs, true);  // inform all listeners about this new subscription
   }


   /**
    * Incoming unsubscribe request from a client.
    * <p />
    * If you have subscribed before, you can cancel your
    * subscription with this method again
    *
    * @param clientInfo
    * @param xmlKey    Key with the oid to unSubscribe<br>
    *                  See XmlKey.dtd for a description<br>
    *                  If you subscribed with XPath, you need to pass the oid you got from your subscription
    * @param qos       Quality of Service, flags to control unsubscription<br>
    *                  See XmlQoS.dtd for a description
    *         Example (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;NoNotify />     &lt;!-- The subscribers shall not be notified when this message is destroyed -->
    *    &lt;/qos>
    * </pre>
    */
   public void unSubscribe(ClientInfo clientInfo, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   {
      if (xmlKey.getQueryType() == XmlKey.XPATH_QUERY) {
         fireSubscriptionEvent(new SubscriptionInfo(clientInfo, xmlKey, unSubscribeQoS), false);
         Log.warning(ME, "SUPPORT FOR QUERY unSubscribe is not yet tested"); // !!! Use the returned oid from subscribe ?!
      }

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, unSubscribeQoS);
      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null) {
            Log.error(ME + ".OidUnknown", "Internal problem, can't access message, key oid is unknown: " + xmlKey.getUniqueKey());
            throw new XmlBlasterException(ME + ".OidUnknown", "Internal problem, can't access message, key oid is unknown: " + xmlKey.getUniqueKey());

         }
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKeyExact, unSubscribeQoS);
         fireSubscriptionEvent(subs, false);
      }
   }


   /**
    * Write-Access method to publish a new message from a data source.
    * <p />
    * There are to MoM styles supported:
    * <p />
    * <ul>
    * <li>PubSub style:<br />
    * If MessageUnit is created from subscribe or the MessageUnit is new, we need to add the
    * DOM here once; XmlKeyBase takes care of that</li>
    * <li>PTP style:<br />
    * Send message directly to all destinations, ignore if same message is known from Pub/Sub style</li>
    * </ul>
    * <p />
    * This triggers the method update() if observed by somebody
    * <p />
    * If the given key doesn't exist, it will be automatically added, <br>
    * so this covers the SQL'ish INSERT and UPDATE.
    * <p />
    * If MessageUnit is created from subscribe or MessageUnit is new, the key meta
    * data are added to the big DOM tree once (XmlKeyBase takes care of that).
    *
    * @param clientInfo  The ClientInfo object, describing the publishing client
    * @param messageUnit The CORBA MessageUnit struct
    * @param publishQoS  Quality of Service, flags to control the publishing<p />
    *         Example for Pub/Sub style (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;expires>        &lt;!-- Expires after given milliseconds, clients will get a notify about expiration -->
    *          12000         &lt;!-- Default is no expiration (similar to pass 0 milliseconds) -->
    *       &lt;/expires>
    *
    *       &lt;erase>          &lt;!-- Message is erased after given milliseconds, clients will get a notify about expiration -->
    *          24000         &lt;!-- Default is no erasing (similar to pass 0 milliseconds) -->
    *       &lt;/erase>
    *
    *       &lt;IsDurable />    &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
    *                        &lt;!-- Default is transient -->
    *
    *       &lt;ForceUpdate />  &lt;!-- An update is forced even when the content and meta data didn't change -->
    *                        &lt;!-- Default is that identical published messages aren't sent to clients again -->
    *
    *       &lt;Readonly />     &lt;!-- A final/const message which may not be changed with further updates -->
    *                        &lt;!-- Default is Read/Write -->
    *
    *       &lt;DefaultContent> &lt;!-- Used content if the content given is null -->
    *          Empty
    *       &lt;/DefaultContent>
    *
    *       &lt;check lang='TCL'> &lt;!-- Allow content checking with a scripting language -->
    *          $content GE 100 &lt;!-- Scripting inside xmlBlaster is not yet supported (JACL, Javascript) -->
    *       &lt;/check>
    *
    *       &lt;alter lang='TCL'> &lt;!-- Allow content manipulation with a scripting language -->
    *          set content [$key('4711') * 1.2 + $content] &lt;!-- Scripting inside xmlBlaster is not yet supported (JACL, Javascript) -->
    *       &lt;/alter>
    *    &lt;/qos>
    * </pre><p />
    * Example for PtP addressing style (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;destination queryType='EXACT'>
    *          Tim
    *       &lt;/destination>
    *
    *       &lt;destination queryType='EXACT'>
    *          Ben
    *       &lt;/destination>
    *
    *       &lt;destination queryType='XPATH'>
    *          //[GROUP='Manager']
    *       &lt;/destination>
    *
    *       &lt;destination queryType='XPATH'>
    *          //ROLE/[@id='Developer']
    *       &lt;/destination>
    *    &lt;/qos>
    * </pre>
    * @return String with the key oid of the messageUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MessageUnit<br />
    *         Rejected Messages will contain an empty string ""
    *
    * @see xmlBlaster.idl for comments
    */
   public String publish(ClientInfo clientInfo, MessageUnit messageUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish() ...");

      if (messageUnit == null || publishQoS==null) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
      }

      XmlKey xmlKey = new XmlKey(messageUnit.xmlKey, true);

      String retVal = xmlKey.getUniqueKey(); // id <key oid=""> was empty, there was a new oid generated

      if (publishQoS.isPubSubStyle()) {
         if (Log.TRACE) Log.trace(ME, "Doing publish() in Pub/Sub style");

         //----- 1. set new value or create the new message:
         MessageUnitHandler messageUnitHandler = setMessageUnit(xmlKey, messageUnit, publishQoS, clientInfo.getLoginName());

         //----- 2. now we can send updates to all interested clients:
         messageUnitHandler.invokeCallback();

         // this gap is not 100% thread save

         //----- 3. check all known query subscriptions if the new message fits as well
         checkExistingSubscriptions(clientInfo, xmlKey, messageUnitHandler, publishQoS);
      }
      else if (publishQoS.isPTP_Style()) {
         if (Log.TRACE) Log.trace(ME, "Doing publish() in PtP or broadcast style");

         MessageUnitWrapper messageUnitWrapper = new MessageUnitWrapper(xmlKey, messageUnit, publishQoS, clientInfo.getLoginName());
         Vector destinations = publishQoS.getDestinations(); // !!! add XPath client query here !!!

         //-----    Send message to every destination client
         for (int ii = 0; ii<destinations.size(); ii++) {
            String loginName = (String)destinations.elementAt(ii);
            ClientInfo destinationClient = authenticate.getOrCreateClientInfoByName(loginName);
            destinationClient.sendUpdate(messageUnitWrapper);
         }
      }
      else {
         Log.warning(ME + ".UnsopportedMoMStyle", "Unknown publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
         throw new XmlBlasterException(ME + ".UnsopportedMoMStyle", "Please verify your publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
      }

      return retVal;
   }


   /**
    * This helper method check for a published message which didn't exist before if
    * there are any XPath subscriptions pending which match
    */
   private final void checkExistingSubscriptions(ClientInfo clientInfo, XmlKey xmlKey,
                                  MessageUnitHandler messageUnitHandler, PublishQoS xmlQoS)
                                  throws XmlBlasterException
   {
      if (messageUnitHandler.isNewCreated()) {
         messageUnitHandler.setNewCreatedFalse();

         if (Log.TRACE) Log.trace(ME, "Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         org.w3c.dom.Document newXmlDoc = xmlKey.getXmlDoc();

         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               XmlKey queryXmlKey = existingQuerySubscription.getXmlKey();

               if (queryXmlKey.getQueryType() == XmlKey.XPATH_QUERY) { // query: subscription without a given oid

                  Vector matchVec = bigXmlKeyDOM.parseKeyOid(clientInfo, queryXmlKey, xmlQoS);

                  if (matchVec != null && matchVec.size() == 1 && matchVec.elementAt(0) != null) {
                     if (Log.TRACE) Log.trace(ME, "The new xmlKey=" + xmlKey.getUniqueKey() + " is matching the existing query subscription " + queryXmlKey.getUniqueKey());
                     SubscriptionInfo subs = new SubscriptionInfo(existingQuerySubscription.getClientInfo(),
                                                                  xmlKey,
                                                                  existingQuerySubscription.getSubscribeQoS());
                     matchingSubsVec.addElement(subs);
                  }
                  else {
                     if (Log.TRACE) Log.trace(ME, "The new xmlKey=" + xmlKey.getUniqueKey() + " does NOT match the existing query subscription " + queryXmlKey.getUniqueKey());
                  }
               }
               else {
                  Log.error(ME, "REGEX check for existing query subscriptions is still missing");
               }
            }

            // now after closing the synchronized block, me may fire the events
            // doing it inside the synchronized could cause a deadlock
            for (int ii=0; ii<matchingSubsVec.size(); ii++) {
               subscribeToOid((SubscriptionInfo)matchingSubsVec.elementAt(ii));    // fires event for subscription
            }
         }
      }
   }


   /**
    * Write-Access method to publish many messages from a data source.
    * <p />
    * This method invokes the other publish() method for every message in the array.<br />
    * Please consult this method for more informations
    * <p />
    *
    * @param messageUnitArr Contains an array of MessageUnit structs
    * @param qosArr         Quality of Service for every MessageUnit
    *
    * @return Array of Strings with the key oid of the messageUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MessageUnit<br />
    *         Rejected Messages will contain an empty string ""
    */
   public String[] publish(ClientInfo clientInfo, MessageUnit[] messageUnitArr, String[] qos_literal_Arr) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering publish(array) ...");

      if (messageUnitArr == null || qos_literal_Arr==null || messageUnitArr.length != qos_literal_Arr.length) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publishArr() are invalid");
      }

      String[] returnArr = new String[messageUnitArr.length];
      for (int ii=0; ii<messageUnitArr.length; ii++) {
         PublishQoS publishQoS = new PublishQoS(qos_literal_Arr[ii]);
         returnArr[ii] = publish(clientInfo, messageUnitArr[ii], publishQoS);
      }

      return returnArr;
   }


   /**
    * Store or update a new arrived message.
    * <p />
    * This is used only for publish/subscribe style.<br />
    * PTP messages are not stored in xmlBlaster
    *
    * @param xmlKey        So the messageUnit.xmlKey_literal is not parsed twice
    * @param messageUnit   Containing the new, published data
    * @param qos           The quality of service of this publish() message
    * @param publisherName The source of the data (unique login name)
    * @return messageUnitHandler MessageUnitHandler object, holding the new MessageUnit
    */
   private MessageUnitHandler setMessageUnit(XmlKey xmlKey, MessageUnit messageUnit,
                                             PublishQoS publishQoS, String publisherName) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Store the new arrived message ...");
      boolean messageExisted = false; // to shorten the synchronize block
      MessageUnitHandler messageUnitHandler=null;

      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
         if (obj == null) {
            messageUnitHandler = new MessageUnitHandler(requestBroker, new MessageUnitWrapper(xmlKey, messageUnit, publishQoS, publisherName));
            messageContainerMap.put(xmlKey.getUniqueKey(), messageUnitHandler);
         }
         else {
            messageUnitHandler = (MessageUnitHandler)obj;
            messageExisted = true;
         }
      }

      if (messageExisted) {
         messageUnitHandler.setContent(xmlKey, messageUnit, publishQoS, publisherName);
      }
      else {
         try {
            xmlKey.mergeRootNode(bigXmlKeyDOM);                    // merge the message DOM tree into the big xmlBlaster DOM tree
         } catch (XmlBlasterException e) {
            synchronized(messageContainerMap) {
               messageContainerMap.remove(xmlKey.getUniqueKey()); // it didn't exist before, so we have to clean up
            }
            throw new XmlBlasterException(e.id, e.reason);
         }
      }
      return messageUnitHandler;
   }


   /**
    * Client wants to erase a message. 
    * <p />
    * @param clientInfo  The ClientInfo object, describing the invoking client
    * @param xmlKey      Key allowing XPath or exact selection<br>
    *                    See XmlKey.dtd for a description
    * @param eraseQoS    Quality of Service, flags to control the erasing
    *
    * @return String array with the key oid's which are deleted
    *         "" strings mark query subscriptions
    */
   public String[] erase(ClientInfo clientInfo, XmlKey xmlKey, EraseQoS qoS) throws XmlBlasterException
   {
      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, qoS);
      String[] oidArr = new String[xmlKeyVec.size()];

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);

         if (xmlKeyExact == null) { // unSubscribe on a unknown message ...
            Log.warning(ME, "Erase on unknown message [" + xmlKey.getUniqueKey() + "] is ignored");
            oidArr[ii] = ""; // !!! how to report to client?
                             // !!! how to delete XPath subscriptions, still MISSING ???
            continue;
         }

         MessageUnitHandler messageUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());

         oidArr[ii] = messageUnitHandler.getUniqueKey();
         try {
            fireMessageEraseEvent(clientInfo, messageUnitHandler);
         } catch (XmlBlasterException e) {
         }
         messageUnitHandler.erase();
         messageUnitHandler = null;
      }

      return oidArr;

   }


   /**
    * Event invoked on message erase() invocation (interface MessageEraseListener).
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Erase event occured ...");

      MessageUnitHandler messageUnitHandler = e.getMessageUnitHandler();
      String uniqueKey = messageUnitHandler.getUniqueKey();
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warning(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
         }
      }
   }


   /**
    * Event invoked on successful client login (interface ClientListener).
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString());
   }


   /**
    * Event invoked when client does a logout (interface ClientListener).
    */
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString());
   }


   /**
    * Adds the specified subscription listener to receive subscribe/unSubscribe events.
    */
   public void addSubscriptionListener(SubscriptionListener l) {
      if (l == null) {
         return;
      }
      synchronized (subscriptionListenerSet) {
         subscriptionListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener.
    */
   public void removeSubscriptionListener(SubscriptionListener l) {
      if (l == null) {
         return;
      }
      synchronized (subscriptionListenerSet) {
         subscriptionListenerSet.remove(l);
      }
   }


   /**
    * Is fired on unSubscribe() and several times on erase().
    */
   public final void fireSubscriptionEvent(SubscriptionInfo subscriptionInfo, boolean subscribe) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Going to fire fireSubscriptionEvent() ...");

      synchronized (subscriptionListenerSet) {
         if (subscriptionListenerSet.size() == 0)
            return;

         SubscriptionEvent event = new SubscriptionEvent(subscriptionInfo);
         Iterator iterator = subscriptionListenerSet.iterator();

         while (iterator.hasNext()) {
            SubscriptionListener subli = (SubscriptionListener)iterator.next();
            if (subscribe)
               subli.subscriptionAdd(event);
            else
               subli.subscriptionRemove(event);
         }

         event = null;
      }
   }


   /**
    * Adds the specified messageErase listener to receive subscribe/unSubscribe events.
    */
   public void addMessageEraseListener(MessageEraseListener l) {
      if (l == null) {
         return;
      }
      synchronized (messageEraseListenerSet) {
         messageEraseListenerSet.add(l);
      }
   }


   /**
    * Removes the specified listener.
    */
   public void removeMessageEraseListener(MessageEraseListener l) {
      if (l == null) {
         return;
      }
      synchronized (messageEraseListenerSet) {
         messageEraseListenerSet.remove(l);
      }
   }


   /**
    * Notify all Listeners that a message is erased.
    *
    * @param clientInfo
    * @param messageUnitHandler
    */
   private final void fireMessageEraseEvent(ClientInfo clientInfo, MessageUnitHandler messageUnitHandler) throws XmlBlasterException
   {
      synchronized (messageEraseListenerSet) {
         if (messageEraseListenerSet.size() == 0)
            return;

         MessageEraseEvent event = new MessageEraseEvent(clientInfo, messageUnitHandler);
         Iterator iterator = messageEraseListenerSet.iterator();

         while (iterator.hasNext()) {
            MessageEraseListener erLi = (MessageEraseListener)iterator.next();
            erLi.messageErase(event);
         }

         event = null;
      }
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn() throws XmlBlasterException
   {
      return printOn((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final StringBuffer printOn(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      Iterator iterator = messageContainerMap.values().iterator();

      sb.append(offset + "<RequestBroker>");
      while (iterator.hasNext()) {
         MessageUnitHandler messageUnitHandler = (MessageUnitHandler)iterator.next();
         sb.append(messageUnitHandler.printOn(extraOffset + "   ").toString());
      }
      sb.append(bigXmlKeyDOM.printOn(extraOffset + "   ").toString());
      sb.append(offset + "</RequestBroker>\n");

      return sb;
   }
}
