/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: RequestBroker.java,v 1.86 2001/01/30 14:25:02 freidlin Exp $
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;

import java.util.*;
import java.io.*;

/**
 * This is the central message broker, all requests are routed through this singleton.
 * <p>
 * The interface I_ClientListener informs about Client login/logout<br />
 * The interface MessageEraseListener informs when a MessageUnit is erased<br />
 * <p>
 * Most events are fired from the RequestBroker
 *
 * @version $Revision: 1.86 $
 * @author ruff@swand.lake.de
 */
public class RequestBroker implements I_ClientListener, MessageEraseListener
{
   /** Total count of published messages */
   public static long publishedMessages = 0L;
   /** Total count of accessed messages via get() */
   public static long getMessages = 0L;

   private static final String ME = "RequestBroker";

   /** the authentication service */
   private Authenticate authenticate = null;          // The authentication service

   /**
    * All MessageUnitHandler objects are stored in this map.
    * <p>
    * key   = msgUnithandler.getUniqueKey() == xmlKey.getUniqueKey() == oid value from <key oid="...">
    * value = MessageUnitHandler object
    */
   private final Map messageContainerMap = Collections.synchronizedMap(new HashMap());

   /**
    * This client is only for internal use, it is un secure to pass it outside because
    * there is no authentication.<br />
    * The login name "__RequestBroker_internal__" is reserved!<br />
    * TODO: security discussion
    */
   private final ClientInfo unsecureClientInfo = new ClientInfo("__RequestBroker_internal__");

   /**
    * Helper to handle the subscriptions
    */
   private final ClientSubscriptions clientSubscriptions;

   /**
    * For listeners who want to be informed about subscribe/unsubscribe events
    */
   private final Set subscriptionListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * For listeners who want to be informed about erase() of messages.
    */
   private final Set messageEraseListenerSet = Collections.synchronizedSet(new HashSet());

   /**
    * This is a handle on the big DOM tree with all XmlKey keys (all message meta data)
    */
   private BigXmlKeyDOM bigXmlKeyDOM = null;

   /**
    * This Interface allows to hook in you own persistence driver, configure it through xmlBlaster.properties
    */
   private I_PersistenceDriver persistenceDriver = null;

   /** Flag for performance reasons only */
   private boolean usePersistence = true;

   /** The messageUnit for a login event */
   private MessageUnit msgUnitLoginEvent = null;

   /** The messageUnit for a logout event */
   private MessageUnit msgUnitLogoutEvent = null;


   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.clientSubscriptions = new ClientSubscriptions(this, authenticate);

      // Key '__sys__Login' for login event (allows you to subscribe on new clients which do a login)
      String xmlKeyLoginEvent = "<key oid='__sys__Login' contentMime='text/plain'>\n</key>";
      String publishQosLoginEvent = "<qos>\n   <forceUpdate/>\n</qos>";
      this.msgUnitLoginEvent = new MessageUnit(xmlKeyLoginEvent, new byte[0], publishQosLoginEvent);

      // Key '__sys__Logout' for logout event (allows you to subscribe on clients which do a logout)
      String xmlKeyLogoutEvent = "<key oid='__sys__Logout' contentMime='text/plain'>\n</key>";
      String publishQosLogoutEvent = "<qos>\n   <forceUpdate/>\n</qos>";
      this.msgUnitLogoutEvent = new MessageUnit(xmlKeyLogoutEvent, new byte[0], publishQosLogoutEvent);

      this.bigXmlKeyDOM = new BigXmlKeyDOM(this, authenticate);

      this.authenticate = authenticate;

      authenticate.addClientListener(this);
      addMessageEraseListener(this);

      loadPersistentMessages();
   }


   /**
    * Try to load all persistent stored messages.
    */
   private void loadPersistentMessages()
   {
      if(Log.CALL) Log.call(ME,"Loding messages from persistence to Memory.....");
      persistenceDriver = getPersistenceDriver(); // Load persistence driver
      if (persistenceDriver == null) return;
      try {
         boolean lazyRecovery = XmlBlasterProperty.get("Persistence.LazyRecovery", true);
         if(Log.TRACE) Log.trace(ME,"LazyRecovery is switched="+lazyRecovery);

         if (lazyRecovery)
         {
            // Recovers all persistent messages from the loaded persistence driver.
            // The RequestBroker must self pulish messages.
            Enumeration oidContainer = persistenceDriver.fetchAllOids();

            while(oidContainer.hasMoreElements())
            {
               String oid = (String)oidContainer.nextElement();
               // Fetch the MessageUnit by oid from the persistence
               MessageUnit msgUnit = persistenceDriver.fetch(oid);

               PublishQoS publishQos = new PublishQoS(msgUnit.getQos());

               // PublishQos flag: 'fromPersistenceStore' must be true
               publishQos.setFromPersistenceStore(true);

               XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);

               // RequestBroker publishes messages self
               this.publish(unsecureClientInfo, xmlKey, msgUnit, publishQos);
            }
         }
      }
      catch (Exception e) {
         Log.error(ME, "Complete recover from persistence store failed: " + e.toString());
      }
   }


   /**
    * This Interface allows to hook in your own persistence driver.
    * <p />
    * Configure the driver through xmlBlaster.properties<br />
    *    Persistence.Driver=org.xmlBlaster.engine.persistence.filestore.FileDriver<br />
    * is default.
    * <p />
    * Note that you can't change the driver during runtime (this would need some code added).
    * @return interface to the configured persistence driver or null if no is available
    */
   final I_PersistenceDriver getPersistenceDriver()
   {
      if (usePersistence == false) return (I_PersistenceDriver)null;

      if (persistenceDriver == null) {
         String driverClass = XmlBlasterProperty.get("Persistence.Driver", "org.xmlBlaster.engine.persistence.filestore.FileDriver");
         if (driverClass == null) {
            Log.warn(ME, "xmlBlaster will run memory based only, the 'Persistence.Driver' property is not set in xmlBlaster.properties");
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }

         try {
            Class cl = java.lang.Class.forName(driverClass);
            persistenceDriver = (I_PersistenceDriver)cl.newInstance();
            usePersistence = true;
         }
         catch (Exception e) {
            Log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate " + driverClass + ": " + e.toString());
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }
         Log.info(ME, "Loaded persistence driver " + driverClass);
      }
      return persistenceDriver;
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
      Log.warn(ME, "setting client attributes is not yet supported: " + xmlAttr_literal);
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
    *       &lt;noMeta />       &lt;!-- Don't send me the key meta data on updates -->
    *       &lt;noContent />    &lt;!-- Don't send me the content data on updates (notify only) -->
    *       &lt;noLocal />      &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
    *    &lt;/qos>
    * </pre>
    * @return oid    The oid of your subscribed Message<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this oid if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    *
    * @see addListener in Java event model
    * @see addObserver in Java observer pattern
    */
   String subscribe(ClientInfo clientInfo, XmlKey xmlKey, SubscribeQoS subscribeQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering subscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

      if (xmlKey.isInternalStateQuery())
         updateInternalStateInfo(clientInfo);

      String returnOid = "";
      if (xmlKey.isQuery()) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKey, subscribeQoS);
         returnOid = subs.getUniqueKey();
         fireSubscriptionEvent(subs, true);
      }

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, subscribeQoS);

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
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
    *       &lt;noMeta />       &lt;!-- Don't send me the key meta data on updates -->
    *       &lt;noContent />    &lt;!-- Don't send me the content data on updates (notify only) -->
    *    &lt;/qos>
    * </pre>
    * @return A sequence of 0 - n MessageUnit structs
    */
   MessageUnit[] get(ClientInfo clientInfo, XmlKey xmlKey, GetQoS qos) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering get(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");
      if (xmlKey.isInternalStateQuery())
         updateInternalStateInfo(clientInfo);

      if (xmlKey.getKeyOid().equals("__sys__jdbc")) { // Query RDBMS !!! hack, we need a general service interface
         String query = xmlKey.toXml();
         String content = query.substring(query.indexOf(">")+1, query.lastIndexOf("<"));
         org.xmlBlaster.protocol.jdbc.XmlDBAdapter adap = new org.xmlBlaster.protocol.jdbc.XmlDBAdapter(
                     content.getBytes(), org.xmlBlaster.protocol.jdbc.JdbcDriver.getNamedPool());
         return adap.query();
      }

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, qos);
      Vector msgUnitVec = new Vector(xmlKeyVec.size());

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
            xmlKeyExact = xmlKey;

         MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());

         if( msgUnitHandler == null ) {
            Log.warn(ME, "The key '"+xmlKeyExact.getUniqueKey()+"' is not available.");
            throw new  XmlBlasterException(ME+".UnavailableKey", "The key '"+xmlKeyExact.getUniqueKey()+"' is not available.");
         }

         if (msgUnitHandler.isPublishedWithData()) {
            MessageUnit mm = msgUnitHandler.getMessageUnit().getClone();
            mm.qos = "<qos></qos>"; // !!! GetReturnQos is still missing
            msgUnitVec.addElement(mm);
         }
      }

      MessageUnit[] msgUnitArr = new MessageUnit[msgUnitVec.size()];
      for (int ii=0; ii<msgUnitArr.length; ii++)
         msgUnitArr[ii] = (MessageUnit)msgUnitVec.elementAt(ii);

      getMessages += msgUnitArr.length;
      return msgUnitArr;
   }


   /**
    * Refresh internal informations about the xmlBlaster state.
    * <p />
    * Sets for example the totally allocated memory in the JVM.
    * <br />
    * This is the internal representation:
    * <pre>
    *    &lt;xmlBlaster>                   &lt;!-- Deliver informations about internal state of xmlBlaster -->
    *
    *       &lt;key oid='__sys__TotalMem'> &lt;!-- Amount of totally allocated RAM [bytes] -->
    *          &lt;__sys__internal>
    *          &lt;/__sys__internal>
    *       &lt;/key>
    *
    *       &lt;key oid='__sys__FreeMem'>  &lt;!-- Amount of free RAM in virtual machine, before new Ram must be allocated [bytes] -->
    *          &lt;__sys__internal>
    *          &lt;/__sys__internal>
    *       &lt;/key>
    * </pre>
    *
    * @param clientInfo The client who triggered the refresh
    * @return A sequence of 0...n MessageUnit structs
    */
   private void updateInternalStateInfo(ClientInfo clientInfo) throws XmlBlasterException
   {
      String oid = "__sys__TotalMem";
      String content = "" + Runtime.getRuntime().totalMemory();
      updateInternalStateInfoHelper(clientInfo, oid, content);

      oid = "__sys__FreeMem";
      content = "" + Runtime.getRuntime().freeMemory();
      updateInternalStateInfoHelper(clientInfo, oid, content);

      oid = "__sys__UsedMem";
      content = "" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
      updateInternalStateInfoHelper(clientInfo, oid, content);

      // Add here more internal states
   }


   /**
    * Little helper to publish internal data into myself
    */
   private void updateInternalStateInfoHelper(ClientInfo clientInfo, String oid, String content) throws XmlBlasterException
   {
      String xmlKey_literal = "<key oid='" + oid + "' contentMime='text/plain'>\n   <__sys__internal>\n   </__sys__internal>\n</key>";
      String qos_literal = "<qos></qos>";
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, content.getBytes(), qos_literal);
      publish(clientInfo, msgUnit); // can we could reuse the PublishQoS? -> better performing.
      if (Log.TRACE) Log.trace(ME, "Refreshed internal state for '" + oid + "'");
   }


   /**
    * Internal publishing helper.
    */
   private String publish(ClientInfo clientInfo, MessageUnit msgUnit) throws XmlBlasterException
   {
      return publish(clientInfo, new XmlKey(msgUnit.xmlKey, true), msgUnit, new PublishQoS(msgUnit.qos));
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

      if (xmlKey.isQuery()) { // query: subscription without a given oid
         xmlKeyVec = bigXmlKeyDOM.parseKeyOid(clientInfo, xmlKey, qos);
      }

      else if (xmlKey.isExact()) { // subscription with a given oid
         if (Log.TRACE) Log.trace(ME, "Access Client " + clientName + " with EXACT oid='" + xmlKey.getUniqueKey() + "'");
         XmlKey xmlKeyExact = getXmlKeyFromOid(xmlKey.getUniqueKey());
         xmlKeyVec = new Vector();
         /* if (xmlKeyExact != null) */
         xmlKeyVec.addElement(xmlKeyExact); // if (xmlKeyExact == null) add nevertheless!
      }

      else {
         Log.warn(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
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
      MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(oid);
      if (msgUnitHandler == null) {
         return null;
      }
      return msgUnitHandler.getXmlKeyOrNull();
   }


   /**
    * @param oid  This is the XmlKey:uniqueKey
    * @return null if not found
    */
   final MessageUnitHandler getMessageHandlerFromOid(String oid)
   {
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(oid);
         if (obj == null) {
            if (Log.TRACE) Log.trace(ME, "getMessageHandlerFromOid(): key oid " + oid + " is unknown, msgUnitHandler == null");
            return null;
         }
         return (MessageUnitHandler)obj;
      }
   }


   /**
    * Low level subscribe, is called when the <key oid='...' queryType='EXACT'> to subscribe is exactly known.
    * <p>
    * If the message is yet unknown, an empty is created to hold the subscription.
    * @param uniqueKey from XmlKey - oid
    * @param subs
    */
   private void subscribeToOid(SubscriptionInfo subs) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Entering subscribeToOid() ...");
      String uniqueKey = subs.getXmlKey().getUniqueKey();
      MessageUnitHandler msgUnitHandler;
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            // This is a new Message, yet unknown ...
            msgUnitHandler = new MessageUnitHandler(this, subs.getXmlKey().getUniqueKey());
            messageContainerMap.put(uniqueKey, msgUnitHandler);
         }
         else {
            // This message was known before ...
            msgUnitHandler = (MessageUnitHandler)obj;
         }
      }

      // Now the MessageUnit exists, subscribe to it
      boolean newSubscribed = msgUnitHandler.addSubscriber(subs);

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
    *       &lt;noNotify />     &lt;!-- The subscribers shall not be notified when this message is destroyed -->
    *    &lt;/qos>
    * </pre>
    */
   void unSubscribe(ClientInfo clientInfo, XmlKey xmlKey, UnSubscribeQoS unSubscribeQoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering unSubscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

      String suppliedXmlKey = xmlKey.getUniqueKey().substring(0); // remember (clone) supplied oid, another oid may be generated later

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, unSubscribeQoS);

      if ((xmlKeyVec.size() == 0 || xmlKeyVec.size() == 1 && xmlKeyVec.elementAt(0) == null) && xmlKey.isExact()) {
         // Special case: the oid describes a returned oid from a XPATH subscription (if not, its an unknown oid - error)
         SubscriptionInfo subs = clientSubscriptions.getSubscription(clientInfo, xmlKey.getUniqueKey()); // Access the XPATH subscription object ...
         if (subs != null && subs.getXmlKey().isQuery()) { // now do the query again ...
            xmlKeyVec = parseKeyOid(clientInfo, subs.getXmlKey(), unSubscribeQoS);
            fireSubscriptionEvent(subs, false);    // Remove the object containing the XPath query
         }
      }

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
         if (xmlKeyExact == null) {
            Log.warn(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey +") from " + clientInfo.getLoginName() + ", can't access message, key oid '" + suppliedXmlKey + "' is unknown");
            throw new XmlBlasterException(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey + ") failed, can't access message, key oid '" + suppliedXmlKey + "' is unknown");

         }
         SubscriptionInfo subs = new SubscriptionInfo(clientInfo, xmlKeyExact, unSubscribeQoS);
         fireSubscriptionEvent(subs, false);
      }

      if (xmlKeyVec.size() < 1) {
         Log.error(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
         throw new XmlBlasterException(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
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
    * @param msgUnit The CORBA MessageUnit struct
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
    *       &lt;isDurable />    &lt;!-- The message shall be recoverable if xmlBlaster crashes -->
    *                        &lt;!-- Default is transient -->
    *
    *       &lt;forceUpdate />  &lt;!-- An update is forced even when the content and meta data didn't change -->
    *                        &lt;!-- Default is that identical published messages aren't sent to clients again -->
    *
    *       &lt;readonly />     &lt;!-- A final/const message which may not be changed with further updates -->
    *                        &lt;!-- Default is Read/Write -->
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
    * @return String with the key oid of the msgUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MessageUnit<br />
    *         Rejected Messages will contain an empty string ""
    *
    * @see xmlBlaster.idl for comments
    */
   String publish(ClientInfo clientInfo, XmlKey xmlKey, MessageUnit msgUnit, PublishQoS publishQoS) throws XmlBlasterException
   {
      if (msgUnit == null || publishQoS==null || xmlKey==null) {
         Log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
         throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
      }

      if (Log.CALL) Log.call(ME, "Entering publish(oid='" + xmlKey.getKeyOid() + "', contentMime='" + xmlKey.getContentMime() + "', contentMimeExtended='" + xmlKey.getContentMimeExtended() + "') ...");
      if (Log.DUMP) Log.dump(ME, "Receiving message in publish()\n" + xmlKey.toXml() + "\n" + publishQoS.toXml());

      String retVal = xmlKey.getUniqueKey(); // id <key oid=""> was empty, there was a new oid generated

      if (! publishQoS.isFromPersistenceStore())
         publishQoS.setSender(clientInfo.getLoginName());

      if (publishQoS.isPubSubStyle()) {
         if (Log.TRACE) Log.trace(ME, "Doing publish() in Pub/Sub style");

         //----- 1. set new value or create the new message:
         MessageUnitHandler msgUnitHandler = null;
         boolean contentChanged = true;
         {
            if (Log.TRACE) Log.trace(ME, "Handle the new arrived Pub/Sub message ...");
            boolean messageExisted = false; // to shorten the synchronize block

            synchronized(messageContainerMap) {
               Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
               if (obj == null) {
                  msgUnitHandler = new MessageUnitHandler(this, new MessageUnitWrapper(this, xmlKey, msgUnit, publishQoS));
                  messageContainerMap.put(xmlKey.getUniqueKey(), msgUnitHandler);
               }
               else {
                  msgUnitHandler = (MessageUnitHandler)obj;
                  messageExisted = true;
               }
            }

            boolean isYetUnpublished = !msgUnitHandler.isPublishedWithData(); // remember here as it may be changed in setContent()

            if (messageExisted) {
               contentChanged = msgUnitHandler.setContent(xmlKey, msgUnit, publishQoS);
            }

            if (!messageExisted || isYetUnpublished) {
               try {
                  xmlKey.mergeRootNode(bigXmlKeyDOM);                   // merge the message DOM tree into the big xmlBlaster DOM tree
               } catch (XmlBlasterException e) {
                  synchronized(messageContainerMap) {
                     messageContainerMap.remove(xmlKey.getUniqueKey()); // it didn't exist before, so we have to clean up
                  }
                  throw new XmlBlasterException(e.id, e.reason);
               }
            }
         }

         //----- 2. now we can send updates to all interested clients:
         if (contentChanged || publishQoS.forceUpdate()) // if the content changed of the publisher forces updates ...
            msgUnitHandler.invokeCallback();

         // this gap is not 100% thread save

         //----- 3. check all known query subscriptions if the new message fits as well
         checkExistingSubscriptions(clientInfo, xmlKey, msgUnitHandler, publishQoS);
      }
      else if (publishQoS.isPTP_Style()) {
         if (Log.TRACE) Log.trace(ME, "Doing publish() in PtP or broadcast style");
         if (Log.DUMP) Log.dump(ME, publishQoS.toXml());

         MessageUnitWrapper msgUnitWrapper = new MessageUnitWrapper(this, xmlKey, msgUnit, publishQoS);
         Vector destinationVec = publishQoS.getDestinations(); // !!! add XPath client query here !!!

         //-----    Send message to every destination client
         for (int ii = 0; ii<destinationVec.size(); ii++) {
            Destination destination = (Destination)destinationVec.elementAt(ii);
            if (Log.TRACE) Log.trace(ME, "Delivering message to destination [" + destination.getDestination() + "]");
            ClientInfo destinationClient = authenticate.getOrCreateClientInfoByName(destination.getDestination());
            destinationClient.sendUpdate(msgUnitWrapper, destination);
         }
      }
      else {
         Log.warn(ME + ".UnsupportedMoMStyle", "Unknown publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
         throw new XmlBlasterException(ME + ".UnsupportedMoMStyle", "Please verify your publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
      }

      publishedMessages++;
      return retVal;
   }


   /**
    * This helper method checks for a published message which didn't exist before if
    * there are any XPath subscriptions pending which match.
    * <p />
    */
   private final void checkExistingSubscriptions(ClientInfo clientInfo, XmlKey xmlKey,
                                  MessageUnitHandler msgUnitHandler, PublishQoS xmlQoS)
                                  throws XmlBlasterException
   {
      if (msgUnitHandler.isNewCreated()) {
         msgUnitHandler.setNewCreatedFalse();

         if (Log.TRACE) Log.trace(ME, "Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            // for every XPath subscription ...
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               XmlKey queryXmlKey = existingQuerySubscription.getXmlKey();
               if (!queryXmlKey.isQuery() || queryXmlKey.getQueryType() != XmlKey.XPATH_QUERY) { // query: subscription without a given oid
                  Log.warn(ME,"Only XPath queries are supported, ignoring subscription.");
                  continue;
               }
               String xpath = queryXmlKey.getQueryString();

               // ... check if the new message matches ...
               if (xmlKey.match(xpath) == true) {
                  SubscriptionInfo subs = new SubscriptionInfo(existingQuerySubscription, xmlKey);
                  matchingSubsVec.addElement(subs);
               }
            }
         }

         // now after closing the synchronized block, me may fire the events
         // doing it inside the synchronized could cause a deadlock
         for (int ii=0; ii<matchingSubsVec.size(); ii++) {
            subscribeToOid((SubscriptionInfo)matchingSubsVec.elementAt(ii));    // fires event for subscription
         }

         // we don't need this DOM tree anymore ...
         xmlKey.cleanupMatch();
      }
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
   String[] erase(ClientInfo clientInfo, XmlKey xmlKey, EraseQoS qoS) throws XmlBlasterException
   {
      if (Log.CALL) Log.call(ME, "Entering erase(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

      Vector xmlKeyVec = parseKeyOid(clientInfo, xmlKey, qoS);
      String[] oidArr = new String[xmlKeyVec.size()];

      for (int ii=0; ii<xmlKeyVec.size(); ii++) {
         XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);

         if (xmlKeyExact == null) { // unSubscribe on a unknown message ...
            Log.warn(ME, "Erase on unknown message [" + xmlKey.getUniqueKey() + "] is ignored");
            oidArr[ii] = ""; // !!! how to report to client?
                             // !!! how to delete XPath subscriptions, still MISSING ???
            continue;
         }

         MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());

         oidArr[ii] = msgUnitHandler.getUniqueKey();
         try {
            fireMessageEraseEvent(clientInfo, msgUnitHandler);
         } catch (XmlBlasterException e) {
         }
         msgUnitHandler.erase();
         msgUnitHandler = null;
      }

      return oidArr;

   }


   /**
    * Event invoked on message erase() invocation (interface MessageEraseListener).
    */
   public void messageErase(MessageEraseEvent e) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Erase event occured ...");

      MessageUnitHandler msgUnitHandler = e.getMessageUnitHandler();
      String uniqueKey = msgUnitHandler.getUniqueKey();
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.remove(uniqueKey);
         if (obj == null) {
            Log.warn(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
         }
      }
   }


   /**
    * Event invoked on successful client login (interface I_ClientListener).
    * <p />
    * Publishes a login event for this client with key oid="__sys_Login"
    * <pre>
    *    &lt;key oid='__sys__Login'>    &lt;!-- Client name is delivered in the content -->
    *    &lt;/key>
    * </pre>
    */
   public void clientAdded(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + clientInfo.toString());
      synchronized (msgUnitLoginEvent.content) {
         msgUnitLoginEvent.content = clientInfo.getLoginName().getBytes();
         publish(clientInfo, msgUnitLoginEvent); // publish that this client logged in
      }
   }


   /**
    * Event invoked when client does a logout (interface I_ClientListener).
    * <p />
    * Publishes a logout event for this client with key oid="__sys_Logout"
    * <pre>
    *    &lt;key oid='__sys__Logout'>    &lt;!-- Client name is delivered in the content -->
    *    &lt;/key>
    * </pre>
    */
   public void clientRemove(ClientEvent e) throws XmlBlasterException
   {
      ClientInfo clientInfo = e.getClientInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + clientInfo.toString());
      synchronized (msgUnitLogoutEvent.content) {
         msgUnitLogoutEvent.content = clientInfo.getLoginName().getBytes();
         publish(clientInfo, msgUnitLogoutEvent); // publish that this client logged out
      }
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
   final void fireSubscriptionEvent(SubscriptionInfo subscriptionInfo, boolean subscribe) throws XmlBlasterException
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
    * @param msgUnitHandler
    */
   private final void fireMessageEraseEvent(ClientInfo clientInfo, MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      synchronized (messageEraseListenerSet) {
         if (messageEraseListenerSet.size() == 0)
            return;

         MessageEraseEvent event = new MessageEraseEvent(clientInfo, msgUnitHandler);
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
   public final String toXml() throws XmlBlasterException
   {
      return toXml((String)null);
   }


   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      Iterator iterator = messageContainerMap.values().iterator();

      sb.append(offset + "<RequestBroker>");
      while (iterator.hasNext()) {
         MessageUnitHandler msgUnitHandler = (MessageUnitHandler)iterator.next();
         sb.append(msgUnitHandler.toXml(extraOffset + "   "));
      }
      sb.append(bigXmlKeyDOM.printOn(extraOffset + "   ").toString());
      sb.append(clientSubscriptions.printOn(extraOffset + "   ").toString());
      sb.append(offset + "</RequestBroker>\n");

      return sb.toString();
   }
}
