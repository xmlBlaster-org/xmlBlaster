/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.xmlBlaster.util.Log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlKeyBase;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.QueueProperty;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
import org.xmlBlaster.engine.mime.I_SubscribeFilter;
import org.xmlBlaster.engine.mime.SubscribePluginManager;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.engine.persistence.PersistencePluginManager;
import org.xmlBlaster.engine.callback.CbWorkerPool;
import org.xmlBlaster.client.PublishQosWrapper;

import java.util.*;
import java.io.*;

/**
 * This is the central message broker, all requests are routed through this singleton.
 * <p>
 * The interface I_ClientListener informs about Client login/logout<br />
 * The interface MessageEraseListener informs when a MessageUnit is erased<br />
 * <p>
 * Most events are fired from the RequestBroker
 * <p>
 * See <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl">xmlBlaster.idl</a>,
 * the CORBA access interface on how clients can access xmlBlaster.
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 */
public final class RequestBroker implements I_ClientListener, MessageEraseListener
{
   /** Total count of published messages */
   public static long publishedMessages = 0L;
   /** Total count of accessed messages via get() */
   public static long getMessages = 0L;

   private PersistencePluginManager pluginManager = null;

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
   private final SessionInfo unsecureSessionInfo;

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

   Hashtable loggedIn = null;

   private Timeout burstModeTimer;

   private SubscribePluginManager subscribePluginManager = null;
   private final Map subscribeFilterMap = Collections.synchronizedMap(new HashMap());

   private final Object pubSubMonitor = new Object();

   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      getGlobal().setRequestBroker(this);

      this.burstModeTimer = new Timeout("BurstmodeTimer");

      unsecureSessionInfo = authenticate.unsecureCreateSession("__RequestBroker_internal__");

      this.loggedIn = new Hashtable();
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

      authenticate.addClientListener(this);
      addMessageEraseListener(this);

      loadPersistentMessages();
   }


   /**
    * Access the global handle. 
    * @return The Global instance of this xmlBlaster server
    */
   public final Global getGlobal()
   {
      return this.authenticate.getGlobal();
   }

   /**
    * Get filter object from cache. 
    */
   final I_SubscribeFilter getSubcribeFilter(String type, String version, String mime, String mimeExtended)
   {
      try {
         StringBuffer key = new StringBuffer(80);
         key.append(type).append(version).append(mime).append(mimeExtended);
         Object obj = subscribeFilterMap.get(key.toString());
         if (obj != null)
            return (I_SubscribeFilter)obj;

         // Check if the plugin is for all mime types
         key.setLength(0);
         key.append(type).append(version).append("*");
         return (I_SubscribeFilter)subscribeFilterMap.get(key.toString());

      } catch (Exception e) {
         Log.error(ME, "Problems accessing subcribe filter [" + type + "][" + version +"] mime=" + mime + " mimeExtended=" + mimeExtended + ": " + e.toString());
         e.printStackTrace();
         return (I_SubscribeFilter)null;
      }
   }

   /**
    * Invoked on new subscription, loads plugin
    */
   final void addSubcribeFilterPlugin(String type, String version)
   {
      StringBuffer key = new StringBuffer(80);
      key.append(type).append(version);
      Object obj = subscribeFilterMap.get(key.toString());
      if (obj != null) {
         Log.info(ME, "Subscribe filter '" + key.toString() + "' is loaded already");
         return;
      }

      try {
         subscribePluginManager = SubscribePluginManager.getInstance();
         I_SubscribeFilter filter = subscribePluginManager.getPlugin(type, version);
         if (filter == null) {
            Log.error(ME, "Problems accessing plugin " + SubscribePluginManager.pluginPropertyName + "[" + type + "][" + version +"] please check your configuration");
            return;
         }

         subscribeFilterMap.put(key.toString(), filter); // Add a dummy instance without mime, so we can check above if loaded already
         key.setLength(0);

         String[] mime = filter.getMimeTypes();
         String[] mimeExtended = filter.getMimeExtended();
         // check plugin code:
         if (mimeExtended == null || mimeExtended.length != mime.length) {
            if (mimeExtended.length != mime.length)
               Log.error(ME, "Subcribe plugin manager [" + type + "][" + version +"]: Number of mimeExtended does not match mime, ignoring mimeExtended.");
            mimeExtended = new String[mime.length];
            for (int ii=0; ii < mime.length; ii++)
               mimeExtended[ii] = XmlKeyBase.DEFAULT_contentMimeExtended;
         }

         for (int ii = 0; ii < mime.length; ii++) {
            key.append(type).append(version).append(mime[ii]).append(mimeExtended[ii]);
            subscribeFilterMap.put(key.toString(), filter);
            Log.info(ME, "Loaded subscribe filter '" + key.toString() + "'");
            key.setLength(0);
         }
      } catch (Throwable e) {
         Log.error(ME, "Problems accessing subcribe plugin manager, can't instantiate " + SubscribePluginManager.pluginPropertyName + "[" + type + "][" + version +"]: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * Publish dead letters, expired letters should be filtered away before. 
    * <p />
    * The key contains an attribute with the oid of the lost message:
    * <pre>
    *   &lt;key oid='__sys__deadLetter'>
    *      &lt;oid>aMessage&lt;/oid>
    *   &lt;key>
    * </pre>
    * @return State inforamtion (is never null)
    */
   public String[] deadLetter(MsgQueueEntry[] entries)
   {
      if (Log.CALL) Log.call(ME, "Publishing " + entries.length + " dead letters.");
      if (entries == null) {
         Log.error(ME, "deadLetter() with null argument");
         return new String[0];
      }

      try {
         Log.info(ME, "Publishing " + entries.length + " volatile dead letters");
         String[] retArr = new String[entries.length];
         PublishQosWrapper pubQos = new PublishQosWrapper();
         pubQos.isVolatile(true);
         for (int ii=0; ii<entries.length; ii++) {
            MsgQueueEntry entry = entries[ii];
            MessageUnit msgUnit = entry.getMessageUnitWrapper().getMessageUnitClone();
            try {
               if (entry.getMessageUnitWrapper().getXmlKey().isDeadLetter()) {  // Check for recursion of dead letters
                  Log.error(ME, "PANIC: Recursive dead letter is lost, no recovery possible - dumping to file not yet coded: " + msgUnit.toXml());
                  retArr[ii] = entry.getMessageUnitWrapper().getXmlKey().getUniqueKey();
                  continue;
               }

               StringBuffer buf = new StringBuffer(256);
               buf.append("<key oid='").append(Constants.OID_DEAD_LETTER).append("'><oid>").append(entry.getMessageUnitWrapper().getUniqueKey()).append("</oid></key>");
               msgUnit.setKey(buf.toString());
               msgUnit.setQos(pubQos.toXml());
               XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);
               retArr[ii] = publish(unsecureSessionInfo, xmlKey, msgUnit, new PublishQos(msgUnit.getQos()));
            }
            catch(Throwable e) {
               Log.error(ME, "PANIC: " + entry.getMessageUnitWrapper().getUniqueKey() + " dead letter is lost, no recovery possible - dumping to file not yet coded: " + e.toString() + "\n" + msgUnit.toXml());
               retArr[ii] = entry.getMessageUnitWrapper().getXmlKey().getUniqueKey();
            }
         }
         return retArr;
      }
      catch (Throwable e) {
         e.printStackTrace();
         Log.error(ME, "PANIC: " + entries.length + " dead letters are lost, no recovery possible - dumping to file code is missing:" + e.toString());
      }

      return new String[0];
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

               PublishQos publishQos = new PublishQos(msgUnit.getQos());

               // PublishQos flag: 'fromPersistenceStore' must be true
               publishQos.setFromPersistenceStore(true);

               XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);

               // RequestBroker publishes messages self
               this.publish(unsecureSessionInfo, xmlKey, msgUnit, publishQos);
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

         /*
         String driverClass = XmlBlasterProperty.get("Persistence.Driver", "org.xmlBlaster.engine.persistence.filestore.FileDriver");

         if (driverClass == null) {
            Log.warn(ME, "xmlBlaster will run memory based only, the 'Persistence.Driver' property is not set in xmlBlaster.properties");
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }

         try {
            Class cl = java.lang.Class.forName(driverClass);
            persistenceDriver = (I_PersistenceDriver)cl.newInstance();
            //persistenceDriver.initialize(driverPath);   // TODO shutdown's missing
            usePersistence = true;
         } catch (Exception e) {
            Log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate " + driverClass + ": " + e.toString());
            usePersistence = false;
            return (I_PersistenceDriver)null;
         } catch (NoClassDefFoundError e1) {
            // Log.info(ME, "java.class.path: " +  System.getProperty("java.class.path") );
            Log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate " + driverClass + ": " + e1.toString());
            usePersistence = false;
            return (I_PersistenceDriver)null;
         } */
         String pluginType    = XmlBlasterProperty.get("Persistence.Driver.Type", "filestore");
         String pluginVersion = XmlBlasterProperty.get("Persistence.Driver.Version", "1.0");

         try {
            pluginManager = PersistencePluginManager.getInstance();
            persistenceDriver = pluginManager.getPlugin(pluginType, pluginVersion);
         } catch (Exception e) {
            Log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate [" + pluginType + "][" + pluginVersion +"]: " + e.toString());
            e.printStackTrace();
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }

         //Log.info(ME, "Loaded persistence driver '" + persistenceDriver.getName() + "[" + pluginType + "][" + pluginVersion +"]'");
         Log.info(ME, "Loaded persistence driver '[" + pluginType + "][" + pluginVersion +"]'");
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
    * The results are returned to the
    * Client-Callback interface via the update() method.
    * You need to implement the method BlasterCallback.update()<br />
    * This is the push modus.
    * <p>
    *
    * @param xmlKey  Key allowing XPath or exact selection<br>
    *                See XmlKey.dtd for a description
    * @param qos     Quality of Service, flags to control subscription<br>
    *                See XmlQoS.dtd for a description, XmlQoS.xml for examples<p />
    *         Example (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;meta>false&lt;/meta>       &lt;!-- Don't send me the key meta data on updates -->
    *       &lt;content>false&lt;/content> &lt;!-- Don't send me the content data on updates (notify only) -->
    *       &lt;local>false&lt;/false>     &lt;!-- Inhibit the delivery of messages to myself if i have published it -->
    *    &lt;/qos>
    * </pre>
    * @return oid    A unique subscription ID<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this ID if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    *
    * @see addListener in Java event model
    * @see addObserver in Java observer pattern
    */
   String subscribe(SessionInfo sessionInfo, XmlKey xmlKey, SubscribeQoS subscribeQos) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "Entering subscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

         if (xmlKey.isInternalStateQuery()) {
            updateInternalStateInfo(sessionInfo); // TODO!!! only login/logout events, but mem not subscribeable
         }

         QueueProperty[] props = subscribeQos.getQueueProperties();
         if (props.length > 1) {         // WE NEED TO RETURN A STRING[] (currently wer return a simple String!!
            Log.warn(ME, "subscribe for more than one queue is currently not supported");
            throw new XmlBlasterException(ME, "subscribe for more than one queue is currently not supported");
         }
         String[] returnOid = new String[props.length];

         MsgQueue msgQueue = null;
         for (int ii=0; ii<props.length; ii++) {
            returnOid[ii] = "";
            if (props[ii].isSessionRelated())
               msgQueue = sessionInfo.getSessionQueue();
            else if (props[ii].isSubjectRelated())
               msgQueue = sessionInfo.getSubjectInfo().getSubjectQueue();
            else
               msgQueue = new MsgQueue("unrelated:"+props[ii].getCallbackAddresses()[0], new QueueProperty(Constants.RELATING_UNRELATED), getGlobal());

            SubscriptionInfo subsQuery = null;
            if (xmlKey.isQuery()) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
               subsQuery = new SubscriptionInfo(getGlobal(), sessionInfo, msgQueue, xmlKey, subscribeQos);
               returnOid[ii] = subsQuery.getUniqueKey();
               fireSubscriptionEvent(subsQuery, true);
            }

            Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, subscribeQos);

            for (int jj=0; jj<xmlKeyVec.size(); jj++) {
               XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(jj);
               if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
                  xmlKeyExact = xmlKey;
               SubscriptionInfo subs = new SubscriptionInfo(getGlobal(), sessionInfo, msgQueue, xmlKeyExact, subscribeQos);
               if (subsQuery != null)
                  subsQuery.addSubscription(subs);
               subscribeToOid(subs);                // fires event for subscription

               if (returnOid[ii].equals("")) returnOid[ii] = subs.getUniqueKey();
            }
         }

         return returnOid[0];  //!!! Only index 0 supported
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("RequestBroker.subscribe.InternalError", e.toString());
      }
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
    * @return A sequence of 0 - n MessageUnit structs
    * @see org.xmlBlaster.client.GetQosWrapper
    */
   MessageUnit[] get(SessionInfo sessionInfo, XmlKey xmlKey, GetQoS qos) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "Entering get(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");
         if (xmlKey.isInternalStateQuery())
            updateInternalStateInfo(sessionInfo);

         if (xmlKey.getKeyOid().equals("__sys__jdbc")) { // Query RDBMS !!! hack, we need a general service interface
            String query = xmlKey.toXml();
            String content = query.substring(query.indexOf(">")+1, query.lastIndexOf("<"));
            org.xmlBlaster.protocol.jdbc.XmlDBAdapter adap = new org.xmlBlaster.protocol.jdbc.XmlDBAdapter(
                        content.getBytes(), org.xmlBlaster.protocol.jdbc.JdbcDriver.getNamedPool());
            return adap.query();
         }

         Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, qos);
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
               MessageUnitWrapper msgUnitWrapper = msgUnitHandler.getMessageUnitWrapper();

               // Check with MsgQueueEntry.getUpdateQos() !!!
               StringBuffer buf = new StringBuffer();
               buf.append("\n<qos>\n");
               buf.append("   <state>OK</state>\n");    // OK | EXPIRED | ERASED
               buf.append("   <sender>").append(msgUnitWrapper.getPublisherName()).append("</sender>\n");
               buf.append("   ").append(msgUnitWrapper.getXmlRcvTimestamp()).append("\n");

               if (msgUnitWrapper.getPublishQos().getRemainingLife() >= 0L) {
                  buf.append("   <expiration remainingLife='").append(msgUnitWrapper.getPublishQos().getRemainingLife()).append("'/>\n");
               }

               buf.append("</qos>");
               mm.qos = buf.toString(); // !!! GetReturnQos should be an object
               msgUnitVec.addElement(mm);
            }
         }

         MessageUnit[] msgUnitArr = new MessageUnit[msgUnitVec.size()];
         for (int ii=0; ii<msgUnitArr.length; ii++)
            msgUnitArr[ii] = (MessageUnit)msgUnitVec.elementAt(ii);

         getMessages += msgUnitArr.length;
         if (Log.TRACE) Log.trace(ME, "Returning for get() " + msgUnitArr.length + " messages");
         return msgUnitArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("RequestBroker.get.InternalError", e.toString());
      }
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
    * @param sessionInfo The client who triggered the refresh
    * @return A sequence of 0...n MessageUnit structs
    */
   private void updateInternalStateInfo(SessionInfo sessionInfo) throws XmlBlasterException
   {
      String oid = "__sys__TotalMem";
      String content = "" + Runtime.getRuntime().totalMemory();
      updateInternalStateInfoHelper(sessionInfo, oid, content);

      oid = "__sys__FreeMem";
      content = "" + Runtime.getRuntime().freeMemory();
      updateInternalStateInfoHelper(sessionInfo, oid, content);

      oid = "__sys__UsedMem";
      content = "" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
      updateInternalStateInfoHelper(sessionInfo, oid, content);
   }

   private void updateInternalUserList(SessionInfo sessionInfo) throws XmlBlasterException
   {
      String oid = "__sys__UserList";
      String content = "";
      synchronized (loggedIn) {
         Enumeration e=loggedIn.elements();
         while(e.hasMoreElements()) {
            content=content+((SubjectInfo)e.nextElement()).getLoginName()+"\n";
         }
      }
      updateInternalStateInfoHelper(sessionInfo, oid, content);

      // Add here more internal states
   }

   /**
    * Little helper to publish internal data into myself
    */
   private void updateInternalStateInfoHelper(SessionInfo sessionInfo, String oid, String content) throws XmlBlasterException
   {
      String xmlKey_literal = "<key oid='" + oid + "' contentMime='text/plain'>\n   <__sys__internal>\n   </__sys__internal>\n</key>";
      String qos_literal = "<qos></qos>";
      MessageUnit msgUnit = new MessageUnit(xmlKey_literal, content.getBytes(), qos_literal);
      publish(sessionInfo, msgUnit); // can we could reuse the PublishQos? -> better performing.
      if (Log.TRACE) Log.trace(ME, "Refreshed internal state for '" + oid + "'");
   }

   /**
    * Internal publishing helper.
    */
   private String[] publish(SessionInfo sessionInfo, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         retArr[ii] = publish(sessionInfo, new XmlKey(msgUnitArr[ii].xmlKey, true), msgUnitArr[ii], new PublishQos(msgUnitArr[ii].qos));
      }
      return retArr;
   }

   /**
    * Internal publishing helper.
    */
   private String publish(SessionInfo sessionInfo, MessageUnit msgUnit) throws XmlBlasterException
   {
      return publish(sessionInfo, new XmlKey(msgUnit.xmlKey, true), msgUnit, new PublishQos(msgUnit.qos));
   }

   /**
    * This method does the query (queryType = "XPATH" | "EXACT").
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements)
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    */
   private Vector parseKeyOid(SessionInfo sessionInfo, XmlKey xmlKey, XmlQoSBase qos)  throws XmlBlasterException
   {
      Vector xmlKeyVec = null;
      String clientName = sessionInfo.toString();

      if (xmlKey.isQuery()) { // query: subscription without a given oid
         xmlKeyVec = bigXmlKeyDOM.parseKeyOid(sessionInfo, xmlKey, qos);
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
      subs.addMessageUnitHandler(msgUnitHandler);

      if (!newSubscribed) return;         // client had already subscribed

      fireSubscriptionEvent(subs, true);  // inform all listeners about this new subscription
   }


   /**
    * Incoming unsubscribe request from a client.
    * <p />
    * If you have subscribed before, you can cancel your
    * subscription with this method again
    *
    * @param sessionInfo
    * @param xmlKey    Key with the oid to unSubscribe<br>
    *                  See XmlKey.dtd for a description<br>
    *                  If you subscribed with XPath, you need to pass the id you got from your subscription
    * @param qos       Quality of Service, flags to control unsubscription<br>
    *                  See XmlQoS.dtd for a description
    *         Example (note that the qos are not yet fully implemented):<p />
    * <pre>
    *    &lt;qos>
    *       &lt;notify>false</notify>     &lt;!-- The subscribers shall not be notified when this message is destroyed -->
    *    &lt;/qos>
    * </pre>
    */
   void unSubscribe(SessionInfo sessionInfo, XmlKey xmlKey, UnSubscribeQoS unSubscribeQos) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "Entering unSubscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

         SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getUniqueKey());
         if (subs != null) {
            Vector childs = subs.getChildrenSubscriptions();
            if (childs != null) {
               Log.info(ME, "unSubscribe() Traversing " + childs.size() + " childs");
               for (int ii=0; ii<childs.size(); ii++) {
                  SubscriptionInfo so = (SubscriptionInfo)childs.elementAt(ii);
                  fireSubscriptionEvent(so, false);
                  so = null;
               }
            }
            fireSubscriptionEvent(subs, false);
            subs = null;
         }
         else
            Log.warn(ME, "UnSubscribe of " + xmlKey.getUniqueKey() + " failed");

         /*
         String suppliedXmlKey = xmlKey.getUniqueKey(); // remember (clone) supplied oid, another oid may be generated later

         Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, unSubscribeQos);

         if ((xmlKeyVec.size() == 0 || xmlKeyVec.size() == 1 && xmlKeyVec.elementAt(0) == null) && xmlKey.isExact()) {
            // Special case: the oid describes a returned oid from a XPATH subscription (if not, its an unknown oid - error)
            SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getUniqueKey()); // Access the XPATH subscription object ...
            if (subs != null && subs.getXmlKey().isQuery()) { // now do the query again ...
               xmlKeyVec = parseKeyOid(sessionInfo, subs.getXmlKey(), unSubscribeQos);
               fireSubscriptionEvent(subs, false);    // Remove the object containing the XPath query
            }
         }

         for (int ii=0; ii<xmlKeyVec.size(); ii++) {
            XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
            if (xmlKeyExact == null) {
               Log.warn(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey +") from " + sessionInfo.getLoginName() + ", can't access message, key oid '" + suppliedXmlKey + "' is unknown");
               throw new XmlBlasterException(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey + ") failed, can't access message, key oid '" + suppliedXmlKey + "' is unknown");

            }
            SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKeyExact.getUniqueKey());
            if (subs != null)
               fireSubscriptionEvent(subs, false);
            else
               Log.warn(ME, "UnSubscribe of " + xmlKeyExact.getUniqueKey() + " failed");
         }

         if (xmlKeyVec.size() < 1) {
            Log.error(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
            throw new XmlBlasterException(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
         }
         */
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("RequestBroker.unSubscribe.InternalError", e.toString());
      }
   }


   /**
    * Write-Access method to publish a new message from a data source.
    * <p />
    * There are two MoM styles supported:
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
    * If the given key oid doesn't exist, it will be automatically added, <br>
    * so this covers the SQL'ish INSERT and UPDATE.
    * <p />
    * If MessageUnit is created from subscribe or MessageUnit is new, the key meta
    * data are added to the big DOM tree once (XmlKeyBase takes care of that).
    * <p />
    * See <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl">xmlBlaster.idl</a>,
    * the CORBA access interface on how clients can access xmlBlaster.
    *
    * @param sessionInfo  The SessionInfo object, describing the publishing client
    * @param msgUnit The MessageUnit struct
    * @param publishQos  Quality of Service, flags to control the publishing<p />
    * @return String with the key oid of the msgUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MessageUnit<br />
    *         Rejected Messages will contain an empty string ""
    *
    * @see org.xmlBlaster.engine.xml2java.PublishQos
    */
   String publish(SessionInfo sessionInfo, XmlKey xmlKey, MessageUnit msgUnit, PublishQos publishQos) throws XmlBlasterException
   {
      try {
         if (msgUnit == null || publishQos==null || xmlKey==null) {
            Log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
            throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
         }

         if (Log.CALL) Log.call(ME, "Entering publish(oid='" + xmlKey.getKeyOid() + "', contentMime='" + xmlKey.getContentMime() + "', contentMimeExtended='" + xmlKey.getContentMimeExtended() + "') ...");
         if (Log.DUMP) Log.dump(ME, "Receiving message in publish()\n" + xmlKey.toXml() + "\n" + publishQos.toXml());

         String retVal = xmlKey.getUniqueKey(); // if <key oid=""> was empty, there was a new oid generated

         if (! publishQos.isFromPersistenceStore())
            publishQos.setSender(sessionInfo.getLoginName());

         if (publishQos.isPubSubStyle()) {
            if (Log.TRACE) Log.trace(ME, "Doing publish() in Pub/Sub style");

            MessageUnitHandler msgUnitHandler = null;
            synchronized(messageContainerMap) {
               //----- 1. set new value or create the new message:
               boolean contentChanged = true;
               if (Log.TRACE) Log.trace(ME, "Handle the new arrived Pub/Sub message ...");
               boolean messageExisted = false; // to shorten the synchronize block

               //synchronized(messageContainerMap) {
                  Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
                  if (obj == null) {
                     msgUnitHandler = new MessageUnitHandler(this, xmlKey, new MessageUnitWrapper(this, xmlKey, msgUnit, publishQos));
                     messageContainerMap.put(xmlKey.getUniqueKey(), msgUnitHandler);
                  }
                  else {
                     msgUnitHandler = (MessageUnitHandler)obj;
                     messageExisted = true;
                  }
               //}

               boolean isYetUnpublished = !msgUnitHandler.isPublishedWithData(); // remember here as it may be changed in setContent()

               if (messageExisted) {
                  contentChanged = msgUnitHandler.setContent(xmlKey, msgUnit, publishQos);
               }

               if (!messageExisted || isYetUnpublished) {
                  try {
                     xmlKey.mergeRootNode(bigXmlKeyDOM);                   // merge the message DOM tree into the big xmlBlaster DOM tree
                  } catch (XmlBlasterException e) {
                     //synchronized(messageContainerMap) {
                        messageContainerMap.remove(xmlKey.getUniqueKey()); // it didn't exist before, so we have to clean up
                     //}
                     throw new XmlBlasterException(e.id, e.reason);
                  }
               }

               //----- 2. now we can send updates to all interested clients:
               if (contentChanged || publishQos.forceUpdate()) // if the content changed of the publisher forces updates ...
                  msgUnitHandler.invokeCallback(sessionInfo);

               // this gap is not 100% thread save

               //----- 3. check all known query subscriptions if the new message fits as well
               // TODO: Only check if it is a new message (XmlKey is immutable)
               checkExistingSubscriptions(sessionInfo, xmlKey, msgUnitHandler, publishQos);

               // We can't do it here, first all callback calls must be successful - the CbWorker does it
               //eraseVolatile(sessionInfo, msgUnitHandler);
            } // end synchronized messageContainerMap
         }
         else if (publishQos.isPTP_Style()) {
            if (Log.TRACE) Log.trace(ME, "Doing publish() in PtP or broadcast style");
            if (Log.DUMP) Log.dump(ME, publishQos.toXml());

            MessageUnitWrapper msgUnitWrapper = new MessageUnitWrapper(this, xmlKey, msgUnit, publishQos);
            Vector destinationVec = publishQos.getDestinations(); // !!! add XPath client query here !!!

            //-----    Send message to every destination client
            for (int ii = 0; ii<destinationVec.size(); ii++) {
               Destination destination = (Destination)destinationVec.elementAt(ii);
               if (Log.TRACE) Log.trace(ME, "Delivering message to destination [" + destination.getDestination() + "]");
               if (destination.isSessionId()) {
                  SessionInfo receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
                  if (receiverSessionInfo == null) {
                     String tmp = "Sending PtP message to unknown session '" + destination.getDestination() + "' failed, message is lost.";
                     Log.warn(ME, tmp);
                     throw new XmlBlasterException("PtP.Failed", tmp);
                  }
                  receiverSessionInfo.queueMessage(new MsgQueueEntry(receiverSessionInfo, msgUnitWrapper));
               }
               else {
                  if (publishQos.forceQueuing()) {
                     SubjectInfo destinationClient = authenticate.getOrCreateSubjectInfoByName(destination.getDestination());
                     destinationClient.queueMessage(new MsgQueueEntry(destinationClient, msgUnitWrapper));
                  }
                  else {
                     SubjectInfo destinationClient = authenticate.getSubjectInfoByName(destination.getDestination());
                     if (destinationClient == null) {
                        String tmp = "Sending PtP message to '" + destination.getDestination() + "' failed, message is lost.";
                        Log.warn(ME, tmp);
                        throw new XmlBlasterException("PtP.Failed", tmp+" Client is not logged in and <destination forceQueuing='true'> is not set");
                     }
                     destinationClient.queueMessage(new MsgQueueEntry(destinationClient, msgUnitWrapper));
                  }
               }
            }
         }
         else {
            Log.warn(ME + ".UnsupportedMoMStyle", "Unknown publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
            throw new XmlBlasterException(ME + ".UnsupportedMoMStyle", "Please verify your publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
         }

         publishedMessages++;
         return retVal;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("RequestBroker.publish.InternalError", e.toString());
      }
   }

   /**
    * Called from CbWorker when a isVolatile() message is successfully delivered to all clients
    */
   public void eraseVolatile(SessionInfo sessionInfo, MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      if (Log.TRACE) Log.trace(ME, "Published message is marked as volatile, erasing it");
      fireMessageEraseEvent(sessionInfo, msgUnitHandler);
      msgUnitHandler.erase();
      msgUnitHandler = null;
   }

   /**
    * This helper method checks for a published message which didn't exist before if
    * there are any XPath subscriptions pending which match.
    * <p />
    */
   private final void checkExistingSubscriptions(SessionInfo sessionInfo, XmlKey xmlKey,
                                  MessageUnitHandler msgUnitHandler, PublishQos xmlQoS)
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
                  SubscriptionInfo subs = new SubscriptionInfo(getGlobal(), sessionInfo, existingQuerySubscription, xmlKey);
                  subs.addMessageUnitHandler(msgUnitHandler);
                  existingQuerySubscription.addSubscription(subs);
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
    * @param sessionInfo  The SessionInfo object, describing the invoking client
    * @param xmlKey      Key allowing XPath or exact selection<br>
    *                    See XmlKey.dtd for a description
    * @param eraseQoS    Quality of Service, flags to control the erasing
    *
    * @return String array with the key oid's which are deleted
    *         "" strings mark query subscriptions
    */
   String[] erase(SessionInfo sessionInfo, XmlKey xmlKey, EraseQoS qoS) throws XmlBlasterException
   {
      try {
         if (Log.CALL) Log.call(ME, "Entering erase(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

         Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, qoS);
         Set oidSet = new HashSet(xmlKeyVec.size());

         for (int ii=0; ii<xmlKeyVec.size(); ii++) {
            XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);

            if (xmlKeyExact == null) { // unSubscribe on a unknown message ...
               Log.warn(ME, "Erase on unknown message [" + xmlKey.getUniqueKey() + "] is ignored");
               // !!! how to delete XPath subscriptions, still MISSING ???
               continue;
            }

            MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
            //Log.info(ME, "Erasing " + msgUnitHandler.toXml());

            oidSet.add(msgUnitHandler.getUniqueKey());
            try {
               fireMessageEraseEvent(sessionInfo, msgUnitHandler);
            } catch (XmlBlasterException e) {
            }
            msgUnitHandler.erase();
            msgUnitHandler = null;
         }

         String[] oidArr = new String[oidSet.size()];
         oidSet.toArray(oidArr);
         return oidArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException("RequestBroker.erase.InternalError", e.toString());
      }
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
   public void sessionAdded(ClientEvent e) throws XmlBlasterException
   {
      SessionInfo sessionInfo = e.getSessionInfo();
      if (Log.TRACE) Log.trace(ME, "Login event for client " + sessionInfo.toString());
      synchronized (msgUnitLoginEvent.content) {
         msgUnitLoginEvent.content = sessionInfo.getLoginName().getBytes();
         publish(sessionInfo, msgUnitLoginEvent); // publish that this client logged in
      }

      if (Log.TRACE) Log.trace(ME, " client added:"+sessionInfo.getLoginName());
      synchronized (loggedIn){
         Object obj = loggedIn.get(sessionInfo.getLoginName());
         if (obj == null) {
            loggedIn.put(sessionInfo.getLoginName(), sessionInfo.getSubjectInfo());
            updateInternalUserList(sessionInfo);
         }
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
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException
   {
      SessionInfo sessionInfo = e.getSessionInfo();
      if (Log.TRACE) Log.trace(ME, "Logout event for client " + sessionInfo.toString());
      synchronized (msgUnitLogoutEvent.content) {
         msgUnitLogoutEvent.content = sessionInfo.getLoginName().getBytes();
         publish(sessionInfo, msgUnitLogoutEvent); // publish that this client logged out
      }
      if (Log.TRACE) Log.trace(ME, " client removed:"+sessionInfo.getLoginName());
      synchronized (loggedIn) {
         if (sessionInfo.getSubjectInfo().getSessions().size() == 1) {
            loggedIn.remove(sessionInfo.getLoginName());
            updateInternalUserList(sessionInfo);
         }
      }
   }


   /**
    * Event invoked on new SubjectInfo. 
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException
   {
      Log.warn(ME, "Ignoring SubjectInfo added event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on deleted SubjectInfo. 
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException
   {
      Log.warn(ME, "Ignoring SubjectInfo removed event for client " + e.getSubjectInfo().toString());
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
    * @param sessionInfo
    * @param msgUnitHandler
    */
   private final void fireMessageEraseEvent(SessionInfo sessionInfo, MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      synchronized (messageEraseListenerSet) {
         if (messageEraseListenerSet.size() == 0)
            return;

         MessageEraseEvent event = new MessageEraseEvent(sessionInfo, msgUnitHandler);
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
