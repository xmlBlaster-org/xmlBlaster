/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.GetReturnQosServer;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.AccessPluginManager;
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.mime.PublishPluginManager;
import org.xmlBlaster.engine.cluster.RouteInfo;
import org.xmlBlaster.engine.cluster.PublishRetQosWrapper;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.authentication.SubjectInfo;
import org.xmlBlaster.engine.persistence.I_PersistenceDriver;
import org.xmlBlaster.engine.persistence.PersistencePluginManager;
import org.xmlBlaster.util.dispatch.DeliveryWorkerPool;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.I_AdminNode;

import java.util.*;
import java.io.*;

/**
 * This is the central message broker, all requests are routed through this singleton.
 * <p>
 * The interface I_ClientListener informs about Client login/logout<br />
 * <p>
 * Most events are fired from the RequestBroker
 * <p>
 * See <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl">xmlBlaster.idl</a>,
 * the CORBA access interface on how clients can access xmlBlaster.
 *
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public final class RequestBroker implements I_ClientListener, I_AdminNode, I_RunlevelListener
{
   private String ME = "RequestBroker";
   private final Global glob;
   private final LogChannel log;

   /** Total count of published messages */
   public static long publishedMessages = 0L;
   /** Total count of accessed messages via get() */
   public static long getMessages = 0L;

   private PersistencePluginManager pluginManager = null;

   /** the authentication service */
   private Authenticate authenticate = null;          // The authentication service

   /**
    * All TopicHandler objects are stored in this map.
    * <p>
    * key   = oid value from <key oid="..."> (== topicHandler.getUniqueKey())
    * value = TopicHandler object
    */
   private final Map messageContainerMap = new HashMap(); //Collections.synchronizedMap(new HashMap());

   /**
    * This client is only for internal use, it is un secure to pass it outside because
    * there is no authentication.<br />
    * The login name "__RequestBroker_internal__" is reserved!<br />
    * TODO: security discussion
    */
   private final SessionInfo unsecureSessionInfo;
   private final SessionName myselfLoginName; // "__RequestBroker_internal[heron]";
   public final static String internalLoginNamePrefix = "__RequestBroker_internal";

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
   private boolean publishLoginEvent = true;
   private MsgKeyData xmlKeyLoginEvent = null;
   private PublishQosServer publishQosLoginEvent;

   /** Initialize a messageUnit for a zserList event */
   private boolean publishUserList = true;
   private MsgKeyData xmlKeyUserListEvent = null;
   private PublishQosServer publishQosUserListEvent;

   /** Initialize a messageUnit for a logout event */
   private boolean publishLogoutEvent = true;
   private MsgKeyData xmlKeyLogoutEvent = null;
   private PublishQosServer publishQosLogoutEvent;

   Hashtable loggedIn = null;

   //private Timeout burstModeTimer;

   private AccessPluginManager accessPluginManager = null;

   private PublishPluginManager publishPluginManager = null;

   private boolean useCluster = false;

   // Enforced by I_AdminNode
   /** Incarnation time of this object instance in millis */
   private long uptime;
   private long numUpdates = 0L;
   private int maxSessions;


   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.glob = this.authenticate.getGlobal();
      this.log = glob.getLog("core");
      glob.setRequestBroker(this);
      this.uptime = System.currentTimeMillis();

      glob.getRunlevelManager().addRunlevelListener(this);

      //this.burstModeTimer = new Timeout("BurstmodeTimer");

      myselfLoginName = new SessionName(glob, glob.getNodeId(), internalLoginNamePrefix + "[" + glob.getId() + "]");
      this.unsecureSessionInfo = authenticate.unsecureCreateSession(myselfLoginName);

      try {
         CommandManager manager = glob.getCommandManager(this.unsecureSessionInfo);
      }
      catch(XmlBlasterException e) {
         log.error(ME, e.toString());
      }

      useCluster = glob.useCluster();
      if (useCluster) {
         glob.getClusterManager(this.unsecureSessionInfo); // Initialize ClusterManager
         this.ME = "RequestBroker" + glob.getLogPrefixDashed();
      }

      accessPluginManager = new AccessPluginManager(glob);

      publishPluginManager = new PublishPluginManager(glob);

      pluginManager = new PersistencePluginManager(glob);

      this.loggedIn = new Hashtable();
      this.clientSubscriptions = new ClientSubscriptions(glob, this, authenticate);

      {// Put this code in a generic internal message producer class (future release)

         // Create properties with infinite life time, forceUpdate and historySize=1
         org.xmlBlaster.client.qos.PublishQos publishQos = new org.xmlBlaster.client.qos.PublishQos(glob);
         publishQos.setLifeTime(-1L);
         publishQos.setForceUpdate(true);

         TopicProperty topicProperty = new TopicProperty(glob);
         HistoryQueueProperty historyQueueProperty = new HistoryQueueProperty(glob, glob.getNodeId().getId());
         historyQueueProperty.setMaxMsgCache(1);
         historyQueueProperty.setMaxMsg(1);
         topicProperty.setHistoryQueueProperty(historyQueueProperty);
         publishQos.setTopicProperty(topicProperty);

         // Should we configure historyQueue and topicCache to be RAM based only?

         this.publishLoginEvent = glob.getProperty().get("loginEvent", true);
         if (this.publishLoginEvent) {
            // Key '__sys__Login' for login event (allows you to subscribe on new clients which do a login)
            org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, "__sys__Login", "text/plain");
            this.xmlKeyLoginEvent = publishKey.getData();
            this.publishQosLoginEvent = new PublishQosServer(glob, publishQos.getData().toXml(), false); // take copy
         }

         this.publishLogoutEvent = glob.getProperty().get("logoutEvent", true);
         if (this.publishLogoutEvent) {
            // Key '__sys__Logout' for logout event (allows you to subscribe on clients which do a logout)
            org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, "__sys__Logout", "text/plain");
            this.xmlKeyLogoutEvent = publishKey.getData();
            this.publishQosLogoutEvent = new PublishQosServer(glob, publishQos.getData().toXml(), false);
         }

         this.publishUserList = glob.getProperty().get("userListEvent", true);
         if (this.publishUserList) {
            // Key '__sys__UserList' for login/logout event
            org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, "__sys__UserList", "text/plain");
            publishKey.setClientTags("<__sys__internal/>");
            this.xmlKeyUserListEvent = publishKey.getData();
            this.publishQosUserListEvent = new PublishQosServer(glob, publishQos.getData().toXml(), false);
         }
      }

      this.bigXmlKeyDOM = new BigXmlKeyDOM(this, authenticate);

      authenticate.addClientListener(this);
   }

   /**
    * A human readable name of the listener for logging.
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   BigXmlKeyDOM getBigXmlKeyDOM() {
      return  this.bigXmlKeyDOM;
   }

   ClientSubscriptions getClientSubscriptions() {
      return this.clientSubscriptions;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_CLEANUP_PRE) {
            loadPersistentMessages();
         }
      }

      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            //
         }
      }
   }

   /**
    * Access the global handle.
    * @return The Global instance of this xmlBlaster server
    */
   public final Global getGlobal() {
      return this.glob;
   }

   /**
    * Access the "core" LogChannel.
    * @return The log channel for core xmlBlaster classes
    */
   public final LogChannel getLog() {
      return this.log;
   }

   public final AccessPluginManager getAccessPluginManager() {
      return this.accessPluginManager;
   }

   public final PublishPluginManager getPublishPluginManager() {
      return this.publishPluginManager;
   }

   /**
    * The cluster may access an internal session to publish its received messages.
    * AWARE: The clusterManager is responsible for the security as it call directly
    * into RequestBroker.
    * See ClusterNode.java update() where we check the cbSessionId
    * TODO: Security audit.
    */
    /*
   public final SessionInfo getClusterSessionInfo() {
      return this.unsecureSessionInfo;
   }
      */
   final SessionInfo getInternalSessionInfo() {
      return this.unsecureSessionInfo;
   }

   /**
    * Publish dead letters, expired letters should be filtered away before.
    * <p />
    * The key contains an attribute with the oid of the lost message:
    * <pre>
    *   &lt;key oid='__sys__deadMessage'>
    *      &lt;oid>aMessage&lt;/oid>
    *   &lt;key>
    * </pre>
    * @param entries The message to send as dead letters
    * @param queue The belonging queue or null
    * @param reason A human readable text describing the problem
    * @return State information returned from the publish call (is never null)
    */
   public String[] deadMessage(MsgQueueEntry[] entries, I_Queue queue, String reason)
   {
      if (log.CALL) log.call(ME, "Publishing " + entries.length + " dead messages.");
      if (entries == null) {
         log.error(ME, "deadMessage() with null argument");
         Thread.currentThread().dumpStack();
         return new String[0];
      }

      try {
         if (log.TRACE) log.trace(ME, "Publishing " + entries.length + " volatile dead messages");
         String[] retArr = new String[entries.length];
         PublishQos pubQos = new PublishQos(glob);
         pubQos.setVolatile(true);
         for (int ii=0; ii<entries.length; ii++) {
            MsgQueueEntry entry = entries[ii];
            if (entry == null) {
               log.error(ME, "Didn't expect null element in MsgQueueEntry[], ignoring it");
               continue;
            }
            MsgUnit origMsgUnit = null;
            if (entry instanceof ReferenceEntry)
               origMsgUnit = ((ReferenceEntry)entry).getMsgUnit();
            else if (entry instanceof MsgQueuePublishEntry)
               origMsgUnit = ((ReferenceEntry)entry).getMsgUnit();
            else {
               log.error(ME, "PANIC: Internal error in deadMessage data type");
               retArr[ii] = "PANIC";
               continue;
            }
            try {
               if (entry.getKeyOid().equals(Constants.OID_DEAD_LETTER)) {  // Check for recursion of dead letters
                  log.error(ME, "PANIC: Recursive dead message is lost, no recovery possible - dumping to file not yet coded: " +
                                origMsgUnit.toXml() + ": " +
                                ((reason != null) ? (": " + reason) : "") );
                  retArr[ii] = entry.getKeyOid();
                  Thread.currentThread().dumpStack();
                  continue;
               }

               log.warn(ME, "Generating dead message '" + entry.getLogId() + "'" +
                            " from publisher=" + entry.getSender() +
                            " because delivery with queue '" +            // entry.getReceiver() is recognized in queueId
                            ((queue == null) ? "null" : queue.getStorageId().toString()) + "' failed" +
                            ((reason != null) ? (": " + reason) : "") );
               PublishKey publishKey = new PublishKey(glob, Constants.OID_DEAD_LETTER);
               publishKey.setClientTags("<oid>"+entry.getKeyOid()+"</oid>");
               // null: use the content from origMsgUnit:
               MsgUnit msgUnit = new MsgUnit(origMsgUnit, publishKey.getData(), null, pubQos.getData());
               retArr[ii] = publish(unsecureSessionInfo, msgUnit);
            }
            catch(Throwable e) {
               log.error(ME, "PANIC: " + entry.getKeyOid() + " dead letter is lost, no recovery possible - dumping to file not yet coded: " + e.toString() + "\n" + origMsgUnit.toXml());
               e.printStackTrace();
               retArr[ii] = entry.getKeyOid();
            }
         }
         return retArr;
      }
      catch (Throwable e) {
         e.printStackTrace();
         log.error(ME, "PANIC: " + entries.length + " dead letters are lost, no recovery possible - dumping to file code is missing:" + e.toString());
      }

      return new String[0];
   }

   /**
    * Try to load all persistent stored messages.
    */
   private void loadPersistentMessages()
   {
      if(log.CALL) log.call(ME,"Loading messages from persistence to Memory ...");
      persistenceDriver = getPersistenceDriver(); // Load persistence driver
      if (persistenceDriver == null) return;
      int num=0;
      try {
         boolean lazyRecovery = glob.getProperty().get("Persistence.LazyRecovery", true);
         if(log.TRACE) log.trace(ME,"LazyRecovery is switched="+lazyRecovery);

         if (lazyRecovery)
         {
            // Recovers all persistent messages from the loaded persistence driver.
            // The RequestBroker must self pulish messages.
            Enumeration oidContainer = persistenceDriver.fetchAllOids();

            while(oidContainer.hasMoreElements())
            {
               String oid = (String)oidContainer.nextElement();
               // Fetch the MsgUnit by oid from the persistence
               MsgUnit msgUnit = persistenceDriver.fetch(oid);

               // PublishQosServer flag: 'fromPersistenceStore' must be true
               MsgQosData msgQosData = (MsgQosData)msgUnit.getQosData();
               msgQosData.setFromPersistenceStore(true);

               // RequestBroker publishes messages self
               this.publish(unsecureSessionInfo, msgUnit);

               num++;
            }
         }
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Complete recover from persistence store failed: " + e.toString());
      }
      catch (Exception e) {
         log.error(ME, "Complete recover from persistence store failed: " + e.toString());
         e.printStackTrace();
      }

      if (num > 0)
         log.info(ME,"Loaded " + num + " durable messages from persistence to Memory.");
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
         String driverClass = glob.getProperty().get("Persistence.Driver", "org.xmlBlaster.engine.persistence.filestore.FileDriver");

         if (driverClass == null) {
            log.warn(ME, "xmlBlaster will run memory based only, the 'Persistence.Driver' property is not set in xmlBlaster.properties");
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }

         try {
            Class cl = java.lang.Class.forName(driverClass);
            persistenceDriver = (I_PersistenceDriver)cl.newInstance();
            //persistenceDriver.initialize(driverPath);   // TODO shutdown's missing
            usePersistence = true;
         } catch (Exception e) {
            log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate " + driverClass + ": " + e.toString());
            usePersistence = false;
            return (I_PersistenceDriver)null;
         } catch (NoClassDefFoundError e1) {
            // log.info(ME, "java.class.path: " +  System.getProperty("java.class.path") );
            log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate " + driverClass + ": " + e1.toString());
            usePersistence = false;
            return (I_PersistenceDriver)null;
         } */
         String pluginType    = glob.getProperty().get("Persistence.Driver.Type", "filestore");
         String pluginVersion = glob.getProperty().get("Persistence.Driver.Version", "1.0");

         try {
            persistenceDriver = pluginManager.getPlugin(pluginType, pluginVersion);
         } catch (Exception e) {
            log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate [" + pluginType + "][" + pluginVersion +"]: " + e.toString());
            e.printStackTrace();
            usePersistence = false;
            return (I_PersistenceDriver)null;
         }

         //log.info(ME, "Loaded persistence driver '" + persistenceDriver.getName() + "[" + pluginType + "][" + pluginVersion +"]'");
         log.info(ME, "Loaded persistence driver plugin '[" + pluginType + "][" + pluginVersion +"]'");
         //Thread.currentThread().dumpStack();
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
      log.warn(ME, "setting client attributes is not yet supported: " + xmlAttr_literal);
   }


   /**
    * Invoked by a client, to subscribe to one/many MsgUnit.
    * <p />
    * Asynchronous read-access method.
    * <p>
    * The results are returned to the
    * Client-Callback interface via the update() method.
    * You need to implement the method BlasterCallback.update()<br />
    * This is the push modus.
    * <p />
    * See addListener in Java event model<br />
    * See addObserver in Java observer pattern
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
    * @return oid    A unique subscription ID embeded on XML<br>
    *                If you subscribed using a query, the subscription ID of this<br>
    *                query handling object (SubscriptionInfo.getUniqueKey()) is returned.<br>
    *                You should use this ID if you wish to unSubscribe()<br>
    *                If no match is found, an empty string "" is returned.
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.publish requirement</a>
    */
   String subscribe(SessionInfo sessionInfo, QueryKeyData xmlKey, SubscribeQosServer subscribeQos) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering subscribe(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "', domain='" + xmlKey.getDomain() + "') from client '" + sessionInfo.getLoginName() + "' ...");
         String returnOid = "";

         SubscriptionInfo subsQuery = null;
         if (xmlKey.isQuery()) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
            subsQuery = new SubscriptionInfo(glob, sessionInfo, xmlKey, subscribeQos.getData());
            returnOid = subsQuery.getSubscriptionId(); // XPath query
            fireSubscribeEvent(subsQuery);
         }

         KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, subscribeQos.getData());

         for (int jj=0; jj<keyDataArr.length; jj++) {
            KeyData xmlKeyExact = keyDataArr[jj];
            if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
               xmlKeyExact = xmlKey;
            SubscriptionInfo subs = null;
            if (sessionInfo.getConnectQos().duplicateUpdates() == false) {
               Vector vec =  clientSubscriptions.getSubscriptionByOid(sessionInfo, xmlKeyExact.getOid(), true);
               if (vec != null) {
                  if (vec.size() > 0) {
                     subs = (SubscriptionInfo)vec.firstElement();
                     if (log.TRACE) log.trace(ME, "Session '" + sessionInfo.getId() + "', message '" + xmlKeyExact.getOid() + "' is subscribed " + vec.size() + " times with duplicateUpdates==false");
                  }
                  if (vec.size() > 1)
                     log.error(ME, "Internal problem for session '" + sessionInfo.getId() + "', message '" + xmlKeyExact.getOid() + "' is subscribed " + vec.size() + " times but duplicateUpdates==false!");
               }
            }

            if (subs == null) {
               if (subsQuery != null) {
                  subs = new SubscriptionInfo(glob, sessionInfo, subsQuery, xmlKeyExact);
                  subsQuery.addSubscription(subs);
               }
               else
                  subs = new SubscriptionInfo(glob, sessionInfo, xmlKeyExact, subscribeQos.getData());
            }

            subscribeToOid(subs);                // fires event for subscription

            if (returnOid.equals("")) returnOid = subs.getSubscriptionId();
         }

         StatusQosData qos = null;
         if (useCluster) { // cluster support - forward message to master
            try {
               SubscribeReturnQos ret = glob.getClusterManager().forwardSubscribe(sessionInfo, xmlKey, subscribeQos);
               if (ret != null)
                  qos = ret.getData();
               //Thread.currentThread().dumpStack();
               //if (ret != null) return ret.toXml();
            }
            catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                  useCluster = false;
               }
               else {
                  e.printStackTrace();
                  throw e;
               }
            }
         }

         //if (qos == null) { Currently we can't use the cluster subId, as it is not unique in another cluster node
            qos = new StatusQosData(glob);
            qos.setSubscriptionId(returnOid);
         //}
         return qos.toXml();
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_SUBSCRIBE.toString(), e);
      }
   }


   /**
    * Invoked by a client, to access one/many MsgUnit.
    * <p />
    * Synchronous read-access method.
    * <p>
    *
    * @param xmlKey  Key allowing XPath or exact selection<br>
    *                See XmlKey.dtd for a description
    * @param getQos  Quality of Service, flags to control subscription<br>
    *                See XmlQoS.dtd for a description, XmlQoS.xml for examples<p />
    * @return A sequence of 0 - n MsgUnit structs. 0 if no message matched.
    *         They are clones from the internal messageUnit, so native clients can manipulate
    *         them without danger
    * @exception XmlBlasterException on internal errors
    * @see org.xmlBlaster.client.qos.GetQos
    */
   public MsgUnit[] get(SessionInfo sessionInfo, QueryKeyData xmlKey, GetQosServer getQos) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering get(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "') ...");

         if (xmlKey.isAdministrative()) {
            if (!glob.supportAdministrative())
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_ADMIN_UNAVAILABLE, ME, "Sorry administrative get() is not available, try to configure xmlBlaster.");
            MsgUnitRaw[] raw = glob.getMomClientGateway().getCommand(sessionInfo, xmlKey.getOid());
            MsgUnit[] msgUnitArr = new MsgUnit[raw.length];
            for(int i=0; i<raw.length; i++) {
               String key = "<key oid='" + raw[i].getKey() + "'/>";
               msgUnitArr[i] = new MsgUnit(glob, key, raw[i].getContent(), raw[i].getQos());
            }
            return msgUnitArr;
         }

         if (xmlKey.getOid().equals(Constants.JDBC_OID/*"__sys__jdbc"*/)) { // Query RDBMS !!! hack, we need a general service interface
            String query = xmlKey.toXml();
            // Extract the query from the xmlkey - this is a bad hack - we need a function to extract user tags in <key>...</key>!
            int start = query.indexOf(">")+1;
            int end = query.lastIndexOf("<");
            if (start<0 || end <0 || start >= end) {
               log.warn(ME, "The JDBC query is invalid '" + query + "'");
               throw new XmlBlasterException(glob, ErrorCode.USER_JDBC_INVALID, ME, "Your JDBC query is invalid");
            }
            String content = query.substring(start, end);
            org.xmlBlaster.protocol.jdbc.XmlDBAdapter adap = new org.xmlBlaster.protocol.jdbc.XmlDBAdapter(glob,
                        content.getBytes(), (org.xmlBlaster.protocol.jdbc.NamedConnectionPool)this.glob.getObjectEntry("NamedConnectionPool-"+glob.getId()));
            return adap.query();
         }

         KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, getQos.getData());
         ArrayList msgUnitList = new ArrayList(keyDataArr.length);

         NEXT_MSG: for (int ii=0; ii<keyDataArr.length; ii++) {
            KeyData xmlKeyExact = keyDataArr[ii];
            if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
               xmlKeyExact = xmlKey;

            TopicHandler topicHandler = getMessageHandlerFromOid(xmlKeyExact.getOid());

            if( topicHandler == null ) {

               if (useCluster) { // cluster support - forward erase to master
                  try {
                     MsgUnit tmp[] = glob.getClusterManager().forwardGet(sessionInfo, xmlKey, getQos);
                     if (tmp != null && tmp.length > 0) {
                        log.info(ME, "get() access of " + tmp.length + " messages from cluster master");
                        for (int jj=0; jj<tmp.length; jj++) {
                           msgUnitList.add(tmp[jj]);
                           // We currently don' cache the message here in the slave !!!
                           // We could do it with the xmlBlasterConnection.initCache(int size)
                        }
                        continue NEXT_MSG;
                     }
                  }
                  catch (XmlBlasterException e) {
                     if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                        useCluster = false;
                     }
                     else {
                        e.printStackTrace();
                        throw e;
                     }
                  }
               }

               log.warn(ME, "get(): The key '"+xmlKeyExact.getOid()+"' is not available.");
               continue NEXT_MSG;

            } // topicHandler==null

            if (topicHandler.isAlive()) {

               int numEntries = getQos.getHistoryQos().getNumEntries();
               MsgUnitWrapper[] msgUnitWrapperArr = topicHandler.getMsgUnitWrapperArr(numEntries);
               
               NEXT_HISTORY:
               for(int kk=0; kk<msgUnitWrapperArr.length; kk++) {

                  MsgUnitWrapper msgUnitWrapper = msgUnitWrapperArr[kk];
                  if (msgUnitWrapper == null) {
                     continue NEXT_HISTORY;
                  }

                  AccessFilterQos[] filterQos = getQos.getAccessFilterArr();
                  if (filterQos != null) {
                     if (log.TRACE) log.trace(ME, "Checking " + filterQos.length + " filters");
                     for (int jj=0; jj<filterQos.length; jj++) {
                        XmlKey key = topicHandler.getXmlKey(); // This key is DOM parsed
                        I_AccessFilter filter = getAccessPluginManager().getAccessFilter(
                                                     filterQos[jj].getType(),
                                                     filterQos[jj].getVersion(),
                                                     xmlKey.getContentMime(),
                                                     xmlKey.getContentMimeExtended());
                        if (log.TRACE) log.trace(ME, "get("+xmlKeyExact.getOid()+") filter=" + filter + " qos=" + getQos.toXml());
                        if (filter != null && filter.match(sessionInfo,
                                                     sessionInfo,
                                                     msgUnitWrapper.getMsgUnit(),
                                                     filterQos[jj].getQuery()) == false)
                           continue NEXT_HISTORY; // filtered message is not send to client
                     }
                  }

                  if (msgUnitWrapper == null || msgUnitWrapper.isExpired()) {
                     continue NEXT_HISTORY;
                  }
                  MsgUnit mm = msgUnitWrapper.getMsgUnit();
                  if (mm == null) {
                     continue NEXT_HISTORY; // WeakReference to cache lost and lookup failed
                  }

                  GetReturnQosServer retQos = new GetReturnQosServer(glob, msgUnitWrapper.getMsgQosData(), Constants.STATE_OK);
                  mm = new MsgUnit(mm, null, null, retQos.getData());
                  msgUnitList.add(mm);

               } // for each history entry

            } // topicHandler.isAlive()
         }

         MsgUnit[] msgUnitArr = (MsgUnit[])msgUnitList.toArray(new MsgUnit[msgUnitList.size()]);
         getMessages += msgUnitArr.length;
         if (log.TRACE) log.trace(ME, "Returning for get() " + msgUnitArr.length + " messages");
         return msgUnitArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_GET.toString(), e);
      }
   }

   private void updateInternalUserList(SessionInfo sessionInfo) throws XmlBlasterException {
      // "__sys__UserList";
      if (this.publishUserList) {
         this.publishQosUserListEvent.clearRoutes();
         MsgUnit msgUnit = new MsgUnit(glob, this.xmlKeyUserListEvent, 
                                 glob.getAuthenticate().getSubjectList().getBytes(), //content.getBytes(),
                                 this.publishQosUserListEvent.getData());
         publish(sessionInfo, msgUnit); // can we could reuse the PublishQos? -> better performing.
         this.publishQosUserListEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
         if (log.TRACE) log.trace(ME, "Refreshed internal state for '" + this.xmlKeyUserListEvent.getOid() + "'");
      }
   }

   /**
    * This method does the query (queryType = "XPATH" | "EXACT").
    *
    * @param clientName is only needed for nicer logging output
    * @return Array of matching XmlKey objects (may contain null elements), the array is never null
    *
    * TODO: a query Handler, allowing drivers for REGEX, XPath, SQL, etc. queries
    * @return The array is never null, but it may contain a null element at index 0 if the oid is yet unknown
    */
   private KeyData[] queryMatchingKeys(SessionInfo sessionInfo, QueryKeyData xmlKey, QueryQosData qos)  throws XmlBlasterException
   {
      String clientName = sessionInfo.toString();

      if (xmlKey.isQuery()) { // query: subscription without a given oid
         ArrayList oidList = bigXmlKeyDOM.parseKeyOid(sessionInfo, xmlKey.getQueryString(), qos);
         KeyData[] arr = new KeyData[oidList.size()];
         for(int i=0; i<arr.length; i++) {
            arr[i] = getXmlKeyFromOid((String)oidList.get(i)).getKeyData();
         }
         return arr;
      }

      else if (xmlKey.isExact()) { // subscription with a given oid
         if (log.TRACE) log.trace(ME, "Access Client " + clientName + " with EXACT oid='" + xmlKey.getOid() + "'");
         XmlKey xmlKeyExact = getXmlKeyFromOid(xmlKey.getOid());
         if (xmlKeyExact == null) {
            return new KeyData[] { (KeyData)null }; // add arr[0]=null as a place holder
         }
         return new KeyData[] { xmlKeyExact.getKeyData() };
      }

      else {
         log.warn(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_TYPE_INVALID, ME, "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }
   }


   /**
    * Try to access the XmlKey by its oid.
    *
    * @param oid  This is the XmlKey.uniqueKey
    * @return the XmlKey object if found in the Map<br />
    *         or null if not found
    */
   public final XmlKey getXmlKeyFromOid(String oid) {
      TopicHandler topicHandler = getMessageHandlerFromOid(oid);
      if (topicHandler == null) {
         return null;
      }
      return topicHandler.getXmlKeyOrNull();
   }


   /**
    * Find the TopicHandler, note that for subscriptions
    * where never a message arrived this method will return null.
    *
    * Use ClientSubscriptions.getSubscriptionByOid() to find those as well.
    *
    * @param oid  This is the XmlKey:uniqueKey
    * @return null if not found
    */
   public final TopicHandler getMessageHandlerFromOid(String oid) {
      synchronized(this.messageContainerMap) {
         Object obj = this.messageContainerMap.get(oid);
         if (obj == null) {
            if (log.TRACE) log.trace(ME, "getMessageHandlerFromOid(): key oid " + oid + " is unknown, topicHandler == null");
            return null;
         }
         return (TopicHandler)obj;
      }
   }

   /**
    * @return A current snapshot of all topics (never null)
    */
   public final TopicHandler[] getTopicHandlerArr() {
      synchronized(this.messageContainerMap) {
         return (TopicHandler[])this.messageContainerMap.values().toArray(new TopicHandler[this.messageContainerMap.size()]);
         /*
         TopicHandler[] arr = new TopicHandler[this.messageContainerMap.size()];
         Iterator it = this.messageContainerMap.values().iterator();
         int i=0;
         while (it.hasNext()) {
            arr[i++] = (TopicHandler)it.next();
         }
         return arr;
         */
      }
   }

   /**
    * @return The previous topic handler (there should never be any in our context).
    */
   public final TopicHandler addTopicHandler(TopicHandler topicHandler) {
      synchronized(messageContainerMap) {
         return (TopicHandler)messageContainerMap.put(topicHandler.getUniqueKey(), topicHandler);
      }
   }

   /**
    * Low level subscribe, is called when the <key oid='...' queryType='EXACT'> to subscribe is exactly known.
    * <p>
    * If the message is yet unknown, an empty is created to hold the subscription.
    * @param uniqueKey from XmlKey - oid
    * @param subs
    */
   private void subscribeToOid(SubscriptionInfo subs) throws XmlBlasterException {
      if (log.TRACE) log.trace(ME, "Entering subscribeToOid() ...");
      String uniqueKey = subs.getKeyData().getOid();
      TopicHandler topicHandler = null;
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(uniqueKey);
         if (obj == null) {
            // This is a new Message, yet unknown ...
            topicHandler = new TopicHandler(this, uniqueKey); // adds itself to messageContainerMap
         }
         else {
            // This message was known before ...
            topicHandler = (TopicHandler)obj;
         }
      }

      subs.incrSubscribeCounter();

      fireSubscribeEvent(subs);  // inform all listeners about this new subscription

      // Now the MsgUnit exists and all subcription handling is done, subscribe to it -> fires update to client
      topicHandler.addSubscriber(subs);
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
   String[] unSubscribe(SessionInfo sessionInfo, QueryKeyData xmlKey, UnSubscribeQosServer unSubscribeQos) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering unSubscribe(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "') ...");

         Set subscriptionIdSet = new HashSet();

         String id = xmlKey.getOid();

         if (SubscriptionInfo.isSubscribeId(id)) {
            SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getOid());
            if (subs != null) {
               Vector childs = subs.getChildrenSubscriptions();
               if (childs != null) {
                  if (log.TRACE) log.trace(ME, "unSubscribe() Traversing " + childs.size() + " childs");
                  for (int ii=0; ii<childs.size(); ii++) {
                     SubscriptionInfo so = (SubscriptionInfo)childs.elementAt(ii);
                     fireUnSubscribeEvent(so);
                     subscriptionIdSet.add(so.getSubscriptionId());
                     so = null;
                  }
               }
               fireUnSubscribeEvent(subs);
               subscriptionIdSet.add(subs.getSubscriptionId());
               subs = null;
            }
            else {
               log.warn(ME, "UnSubscribe of " + xmlKey.getOid() + " failed");
               if (log.DUMP) log.dump(ME, toXml());
            }
         }
         else { // Try to unssubscribe with message oid instead of subscribe id:
            String suppliedXmlKey = xmlKey.getOid(); // remember supplied oid, another oid may be generated later

            KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, unSubscribeQos.getData());

            if ((keyDataArr.length == 0 || keyDataArr.length == 1 && keyDataArr[0] == null) && xmlKey.isExact()) {
               // Special case: the oid describes a returned oid from a XPATH subscription (if not, its an unknown oid - error)
               SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getOid()); // Access the XPATH subscription object ...
               if (subs != null && subs.getKeyData().isQuery()) { // now do the query again ...
                  keyDataArr = queryMatchingKeys(sessionInfo, (QueryKeyData)subs.getKeyData(), unSubscribeQos.getData());
                  fireUnSubscribeEvent(subs);    // Remove the object containing the XPath query
                  subscriptionIdSet.add(subs.getSubscriptionId());
               }
            }

            for (int ii=0; ii<keyDataArr.length; ii++) {
               KeyData xmlKeyExact = keyDataArr[ii];
               if (xmlKeyExact == null) {
                  log.warn(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey +") from " + sessionInfo.getLoginName() + ", can't access message, key oid '" + suppliedXmlKey + "' is unknown");
                  throw new XmlBlasterException(glob, ErrorCode.USER_OID_UNKNOWN, ME, "unSubscribe(" + suppliedXmlKey + ") failed, can't access message, key oid '" + suppliedXmlKey + "' is unknown");
               }
               TopicHandler handler = getMessageHandlerFromOid(xmlKeyExact.getOid());
               Vector subs = handler.findSubscriber(sessionInfo);
               for (int jj=0; subs != null && jj<subs.size(); jj++) {
                  SubscriptionInfo sub = (SubscriptionInfo)subs.elementAt(jj);
                  if (sub != null) {
                     fireUnSubscribeEvent(sub);
                     subscriptionIdSet.add(sub.getSubscriptionId());
                  }
                  else
                     log.warn(ME, "UnSubscribe of " + xmlKeyExact.getOid() + " failed");
               }
            }

            if (keyDataArr.length < 1) {
               log.error(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
               throw new XmlBlasterException(glob, ErrorCode.USER_OID_UNKNOWN, ME, "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
            }
         }

         // Build the return values ...
         String[] oidArr = new String[subscriptionIdSet.size()];
         StatusQosData qos = new StatusQosData(glob);
         qos.setState(Constants.STATE_OK);
         Iterator it = subscriptionIdSet.iterator();
         int ii = 0;
         while (it.hasNext()) {
            qos.setSubscriptionId((String)it.next());
            oidArr[ii++] = qos.toXml();
         }
         return oidArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_UNSUBSCRIBE.toString(), e);
      }
   }

   /**
    * Used for cluster internal updates.
    */
   public final String update(SessionInfo sessionInfo, UpdateKey updateKey, byte[] content, MsgQosData msgQosData) throws XmlBlasterException
   {
      if (msgQosData.isErased()) {
         QueryKeyData key = glob.getQueryKeyFactory().readObject(updateKey.toXml());
         EraseQosServer qos = new EraseQosServer(glob, "<qos/>");
         String[] ret = glob.getRequestBroker().erase(sessionInfo, key, qos, true);
         if (ret != null && ret.length > 0)
            return ret[0];
         else
            return "<qos/>";
      }
      else {
         PublishQosServer qos = new PublishQosServer(glob, msgQosData);
         MsgUnit msgUnit = new MsgUnit(glob, updateKey.getData(), content, qos.getData());
         return publish(sessionInfo, msgUnit, true);
      }
   }

   /**
    * Internal publishing helper.
    */
   private String[] publish(SessionInfo sessionInfo, MsgUnit[] msgUnitArr) throws XmlBlasterException
   {
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         retArr[ii] = publish(sessionInfo, msgUnitArr[ii]);
      }
      return retArr;
   }

   /**
    * Internal publishing helper.
    */
   public final String publish(SessionInfo sessionInfo, MsgUnit msgUnit) throws XmlBlasterException {
      return publish(sessionInfo, msgUnit, false);
   }

   /**
    * Write-Access method to publish a new message from a data source.
    * <p />
    * There are two MoM styles supported:
    * <p />
    * <ul>
    * <li>PubSub style:<br />
    * If MsgUnit is created from subscribe or the MsgUnit is new, we need to add the
    * DOM here once; XmlKey takes care of that</li>
    * <li>PTP style:<br />
    * Send message directly to all destinations, ignore if same message is known from Pub/Sub style</li>
    * </ul>
    * <p />
    * This triggers the method update() if observed by somebody
    * <p />
    * If the given key oid doesn't exist, it will be automatically added, <br>
    * so this covers the SQL'ish INSERT and UPDATE.
    * <p />
    * If MsgUnit is created from subscribe or MsgUnit is new, the key meta
    * data are added to the big DOM tree once (XmlKey takes care of that).
    * <p />
    * See <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl">xmlBlaster.idl</a>,
    * the CORBA access interface on how clients can access xmlBlaster.
    * <p />
    * TODO: Allow XML formatted returns which exactly match the update() return syntax (for clustering ClusterNode.java:update())
    *
    * @param sessionInfo  The SessionInfo object, describing the publishing client
    * @param msgUnit The MsgUnit struct
    * @param isClusterUpdate true if it is a update() callback message from another cluster node
    * @return String with the XML encoded key oid of the msgUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MsgUnit<br />
    *         Rejected Messages will contain a string with state id!=OK
    *
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   private final String publish(SessionInfo sessionInfo, MsgUnit msgUnit, boolean isClusterUpdate) throws XmlBlasterException
   {
      try {
         if (msgUnit == null) {
            log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "The arguments of method publish() are invalid (null)");
         }

         MsgKeyData msgKeyData = (MsgKeyData)msgUnit.getKeyData();
         PublishQosServer publishQos = new PublishQosServer(glob, msgUnit.getQosData());

         if (log.CALL) log.call(ME, "Entering " + (isClusterUpdate?"cluster update message ":"") + "publish(oid='" + msgKeyData.getOid() + "', contentMime='" + msgKeyData.getContentMime() + "', contentMimeExtended='" + msgKeyData.getContentMimeExtended() + "' domain='" + msgKeyData.getDomain() + "' from client '" + sessionInfo.getLoginName() + "' ...");
         if (log.DUMP) log.dump(ME, "Receiving " + (isClusterUpdate?"cluster update ":"") + " message in publish()\n" + msgKeyData.toXml() + "\n" + publishQos.toXml());

         PublishReturnQos publishReturnQos = null;

         if (! publishQos.isFromPersistenceStore()) {

            if (publishQos.getSender() == null) // In cluster routing don't overwrite the original sender
               publishQos.setSender(sessionInfo.getSessionName());

            if (!myselfLoginName.getLoginName().equals(sessionInfo.getSessionName().getLoginName())) {
               // TODO: allow for cluster internal messages?
               // TODO: what about different sessions of myselfLoginName?
               int hopCount = publishQos.count(glob.getNodeId());
               if (hopCount > 0) {
                  log.warn(ME, "Warning, message oid='" + msgKeyData.getOid()
                     + "' passed my node id='" + glob.getId() + "' " + hopCount + " times before, we have a circular routing problem " +
                     " mySelf=" + myselfLoginName.getAbsoluteName() + " sessionName=" +
                     sessionInfo.getSessionName().getAbsoluteName() + ": " + publishQos.toXml(""));
               }
               int stratum = -1; // not known yet, addRouteInfo() sets my stratum to one closer to the master,
                                 // this needs to be checked here as soon as we know which stratum we are!!!
               publishQos.addRouteInfo(new RouteInfo(glob.getNodeId(), stratum, publishQos.getRcvTimestamp()));
            }
         }

         publishedMessages++;

         if (msgKeyData.isAdministrative()) {
            if (!glob.supportAdministrative())
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_ADMIN_UNAVAILABLE, ME, "Sorry administrative publish() is not available, try to configure xmlBlaster.");
            return glob.getMomClientGateway().setCommand(sessionInfo, msgKeyData, msgUnit, publishQos, isClusterUpdate);
         }

         // Check if a publish filter is installed and if so invoke it ...
         if (getPublishPluginManager().hasPlugins() && !isClusterUpdate) {
            Map mimePlugins = getPublishPluginManager().findMimePlugins(msgKeyData.getContentMime(),msgKeyData.getContentMimeExtended());
            if (mimePlugins != null) {
               Iterator iterator = mimePlugins.values().iterator();
               while (iterator.hasNext()) {
                  I_PublishFilter plugin = (I_PublishFilter)iterator.next();
                  if (log.TRACE) log.trace(ME, "Message " + msgKeyData.getOid() + " is forwarded to publish plugin");
                  String ret = plugin.intercept(sessionInfo.getSubjectInfo(), msgUnit);
                  if (ret == null || ret.length() == 0 || ret.equals(Constants.STATE_OK))
                     break;
                  else {
                     if (log.TRACE) log.trace(ME, "Message " + msgKeyData.getOid() + " is rejected by PublishPlugin");
                     return "<qos><state id='" + ret + "'/></qos>";  // Message is rejected by PublishPlugin
                  }
               }
            }
         }

         // cluster support - forward pubSub message to master ...
         if (useCluster) {
            if (!isClusterUpdate) { // updates from other nodes are arriving here in publish as well
               if (publishQos.isPubSubStyle()) {
                  try {
                     PublishRetQosWrapper ret = glob.getClusterManager().forwardPublish(sessionInfo, msgUnit);
                     //Thread.currentThread().dumpStack();
                     if (ret != null) { // Message was forwarded to master cluster
                        publishReturnQos = ret.getPublishReturnQos();
                        if (ret.getNodeDomainInfo().getDirtyRead() == false) {
                           if (log.TRACE) log.trace(ME, "Message " + msgKeyData.getOid() + " forwarded to master " + ret.getNodeDomainInfo().getId() + ", dirtyRead==false nothing more to do");
                           return publishReturnQos.toXml();
                        }
                        // else we publish it locally as well (dirty read!)
                     }
                  }
                  catch (XmlBlasterException e) {
                     if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                        useCluster = false;
                     }
                     else {
                        e.printStackTrace();
                        throw e;
                     }
                  }
               }
               else { // is PtP
                  Destination[] destinationArr = publishQos.getDestinationArr(); // !!! add XPath client query here !!!
                  for (int ii = 0; ii<destinationArr.length; ii++) {
                     if (log.TRACE) log.trace(ME, "Working on PtP message for destination [" + destinationArr[ii].getDestination() + "]");
                     publishReturnQos = forwardPtpPublish(sessionInfo, msgUnit, isClusterUpdate, destinationArr[ii]);
                     if (publishReturnQos != null) {
                        // Message was forwarded. TODO: How to return multiple publishReturnQos from multiple destinations? !!!
                        publishQos.removeDestination(destinationArr[ii]);
                     }
                  }
                  if (publishQos.getNumDestinations() == 0) { // we are done, all messages where forwarded
                     return publishReturnQos.toXml();
                  }
               }
            }
         }

         // Handle local message

         // Find or create the topic
         TopicHandler topicHandler = null;
         synchronized(messageContainerMap) {
            Object obj = messageContainerMap.get(msgKeyData.getOid());
            if (obj == null) {
               topicHandler = new TopicHandler(this, sessionInfo, msgUnit); // adds itself to messageContainerMap
            }
            else {
               topicHandler = (TopicHandler)obj;
            }
         }

         // Process the message
         publishReturnQos = topicHandler.publish(sessionInfo, msgUnit, publishQos);

         // Check all known query subscriptions if the new message fits as well (does it only if TopicHandler is new)
         checkExistingSubscriptions(sessionInfo, topicHandler, publishQos);

/*
         }
         else if (publishQos.isPtp()) {
            if (log.TRACE) log.trace(ME, "Doing publish() in PtP or broadcast style");
            if (log.DUMP) log.dump(ME, publishQos.toXml());

            // Check if a publish filter is installed and if so invoke it ...
            if (getPublishPluginManager().hasPlugins() && !isClusterUpdate) {
               Map mimePlugins = getPublishPluginManager().findMimePlugins(msgKeyData.getContentMime(),
                                                             msgKeyData.getContentMimeExtended());
               if (mimePlugins != null) {
                  Iterator iterator = mimePlugins.values().iterator();
                  while (iterator.hasNext()) {
                     I_PublishFilter plugin = (I_PublishFilter)iterator.next();
                     String ret = plugin.intercept(sessionInfo.getSubjectInfo(), msgUnit);
                     if (ret == null || ret.length() == 0 || ret.equals(Constants.STATE_OK))
                        break;
                     else
                        return "<qos><state id='" + ret + "'/></qos>";  // Message is rejected by PublishPlugin
                  }
               }
            }

            ArrayList destinationList = publishQos.getDestinations(); // !!! add XPath client query here !!!

            //-----    Send message to every destination client
            for (int ii = 0; ii<destinationList.size(); ii++) {
               Destination destination = (Destination)destinationList.get(ii);
               if (log.TRACE) log.trace(ME, "Working on PtP message for destination [" + destination.getDestination() + "]");

               if (useCluster) {
                  if (!isClusterUpdate) { // updates from other nodes are arriving here in publish as well
                     try {
                        PublishReturnQos ret = glob.getClusterManager().forwardPtpPublish(sessionInfo, msgUnit, destination);
                        if (ret != null) { // Message was forwarded to another cluster
                           publishReturnQos = ret; // TODO: How to return multiple publishReturnQos from multiple destinations? !!!
                           continue;
                        }
                     }
                     catch (XmlBlasterException e) {
                        e.printStackTrace();
                        throw e;
                     }
                  }
               }

               if (destination.getDestination().isSession()) {
                  SessionInfo receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
                  if (receiverSessionInfo == null) {
                     String tmp = "Sending PtP message to unknown session '" + destination.getDestination() + "' failed, message is lost.";
                     log.warn(ME, tmp);
                     throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNSESSION, ME, tmp);
                  }
                  receiverSessionInfo.queueMessage(msgUnit);
               }
               else {
                  if (destination.forceQueuing()) {
                     SubjectInfo destinationClient = authenticate.getOrCreateSubjectInfoByName(destination.getDestination());
                     destinationClient.queueMessage(msgUnit);
                  }
                  else {
                     SubjectInfo destinationClient = authenticate.getSubjectInfoByName(destination.getDestination());
                     if (destinationClient == null) {
                        String tmp = "Sending PtP message to '" + destination.getDestination() + "' failed, message is lost.";
                        log.warn(ME, tmp);
                        throw new XmlBlasterException(glob, ErrorCode.USER_PTP_UNKNOWNDESTINATION, ME, tmp+" Client is not logged in and <destination forceQueuing='true'> is not set");
                     }
                     destinationClient.queueMessage(msgUnit);
                  }
               }
            }
         }
         else {
            log.warn(ME + ".UnsupportedMoMStyle", "Unknown publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
            throw new XmlBlasterException(glob, ErrorCode.USER_PUBLISH, ME, "Please verify your publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
         }
*/
         if (publishReturnQos != null)
            return publishReturnQos.toXml(); // Use the return value of the cluster master node

         StringBuffer buf = new StringBuffer(160);
         buf.append("<qos><state id='").append(Constants.STATE_OK).append("'/><key oid='").append(msgKeyData.getOid()).append("'/></qos>");
         return buf.toString();
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME, "Throwing execption in publish: " + e.toXml()); // Remove again
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_PUBLISH.toString()+" "+sessionInfo.getId(), e);
      }
   }

   /**
    * Rorward a message to another cluster node. 
    * TODO: How to return multiple retVal from multiple destinations? !!!
    * @return if not null the message was forwarded to another cluster
    */
   public PublishReturnQos forwardPtpPublish(SessionInfo sessionInfo, MsgUnit msgUnit, boolean isClusterUpdate, Destination destination) throws XmlBlasterException {
      if (useCluster) {
         if (!isClusterUpdate) { // updates from other nodes are arriving here in publish as well
            try {
               return glob.getClusterManager().forwardPtpPublish(sessionInfo, msgUnit, destination);
            }
            catch (XmlBlasterException e) {
               e.printStackTrace();
               throw e;
            }
         }
      }
      return null;
   }

   /**
    * Called to erase a isVolatile() message.
    * @param sessionInfo can be null
    */
   public void eraseVolatile(SessionInfo sessionInfo, TopicHandler topicHandler) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Published message is marked as volatile, erasing it");
      if (sessionInfo == null) sessionInfo = this.unsecureSessionInfo;
      try {
         topicHandler.fireMessageEraseEvent(sessionInfo, null); // null: no erase event for volatile messages
      } catch (XmlBlasterException e) {
         if (log.TRACE) log.error(ME, "Unexpected exception: " + e.toString());
      }
   }

   /**
    * This helper method checks for a published message which didn't exist before if
    * there are any XPath subscriptions pending which match.
    * <p />
    */
   private final void checkExistingSubscriptions(SessionInfo sessionInfo,
                                  TopicHandler topicHandler, PublishQosServer xmlQoS)
                                  throws XmlBlasterException
   {
      if (topicHandler.isNewCreated()) {
         topicHandler.setNewCreatedFalse();
         XmlKey keyDom = topicHandler.getXmlKey();

         if (log.TRACE) log.trace(ME, "Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            // for every XPath subscription ...
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               KeyData queryXmlKey = existingQuerySubscription.getKeyData();
               if (!queryXmlKey.isXPath()) { // query: subscription without a given oid
                  log.warn(ME,"Only XPath queries are supported, ignoring subscription.");
                  continue;
               }
               String xpath = ((QueryKeyData)queryXmlKey).getQueryString();

               // ... check if the new message matches ...
               if (keyDom.match(xpath) == true) {
                  SubscriptionInfo subs = new SubscriptionInfo(glob, existingQuerySubscription.getSessionInfo(),
                                              existingQuerySubscription, keyDom.getKeyData());
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
         keyDom.cleanupMatch();
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
    * @return String array with the xml encoded key oid's which are deleted
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   String[] erase(SessionInfo sessionInfo, QueryKeyData xmlKey, EraseQosServer eraseQos) throws XmlBlasterException {
      return erase(sessionInfo, xmlKey, eraseQos, false);
   }

   /**
    * Client wants to erase a message.
    * <p />
    * @param sessionInfo  The SessionInfo object, describing the invoking client
    * @param xmlKey      Key allowing XPath or exact selection<br>
    *                    See XmlKey.dtd for a description
    * @param eraseQoS    Quality of Service, flags to control the erasing
    * @param isClusterUpdate true if it is a update() callback message from another cluster node
    *
    * @return String array with the xml encoded key oid's which are deleted
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   private String[] erase(SessionInfo sessionInfo, QueryKeyData xmlKey, EraseQosServer eraseQos, boolean isClusterUpdate) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering " + (isClusterUpdate?"cluster update message ":"") +
                "erase(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() +
                "', query='" + xmlKey.getQueryString() + "') client '" + sessionInfo.getLoginName() + "' ...");

         KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, eraseQos.getData());
         Set oidSet = new HashSet(keyDataArr.length);  // for return values (TODO: change to TreeSet to maintain order)

         for (int ii=0; ii<keyDataArr.length; ii++) {
            KeyData xmlKeyExact = keyDataArr[ii];

            if (useCluster && !isClusterUpdate) { // cluster support - forward erase to master
               try {
                  EraseReturnQos ret[] = glob.getClusterManager().forwardErase(sessionInfo, xmlKey, eraseQos);
                  //Thread.currentThread().dumpStack();
                  //if (ret != null) return ret;
               }
               catch (XmlBlasterException e) {
                  if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                     useCluster = false;
                  }
                  else {
                     e.printStackTrace();
                     throw e;
                  }
               }
            }

            if (xmlKeyExact == null) { // unSubscribe on a unknown message ...
               log.warn(ME, "Erase on unknown message [" + xmlKey.getOid() + "] is ignored");
               // !!! how to delete XPath subscriptions, still MISSING ???
               continue;
            }

            if (log.TRACE) log.trace(ME, "erase oid='" + xmlKeyExact.getOid() + "' of total " + keyDataArr.length + " ...");

            TopicHandler topicHandler = getMessageHandlerFromOid(xmlKeyExact.getOid());
            if (topicHandler == null) {
               continue;    // can happen as not synchronized
            }
            //log.info(ME, "Erasing " + topicHandler.toXml());

            oidSet.add(topicHandler.getUniqueKey());
            try {
               topicHandler.fireMessageEraseEvent(sessionInfo, eraseQos);
            } catch (XmlBlasterException e) {
               if (log.TRACE) log.error(ME, "Unexpected exception: " + e.toString());
            }
         }
         //log.info(ME, "AFTER ERASE: " + toXml());

         // Build the return values ...
         String[] oidArr = new String[oidSet.size()];
         //oidSet.toArray(oidArr);
         StatusQosData qos = new StatusQosData(glob);
         qos.setState(Constants.STATE_OK);
         Iterator it = oidSet.iterator();
         int ii = 0;
         while (it.hasNext()) {
            qos.setKeyOid((String)it.next());
            oidArr[ii++] = qos.toXml();
         }
         return oidArr;
      }
      catch (XmlBlasterException e) {
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw XmlBlasterException.convert(glob, ME, ErrorCode.INTERNAL_ERASE.toString(), e);
      }
   }


   /**
    * Event invoked on message erase() invocation.
    */
   public void messageErase(TopicHandler topicHandler) throws XmlBlasterException
   {
      if (topicHandler.hasExactSubscribers()) {
         if (log.TRACE) log.trace(ME, "Erase event occured for oid=" + topicHandler.getUniqueKey() + ", not removing topicHandler skeleton since " + topicHandler.numSubscribers() + " subscribers exist ...");
         // TopicHandler will call
      }
      else {
         String uniqueKey = topicHandler.getUniqueKey();
         if (log.TRACE) log.trace(ME, "Erase event occured for oid=" + uniqueKey + ", removing message from my map ...");
         synchronized(messageContainerMap) {
            Object obj = messageContainerMap.remove(uniqueKey);
            if (obj == null) {
               log.warn(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
               throw new XmlBlasterException(glob, ErrorCode.USER_OID_UNKNOWN, ME, "Sorry, can't remove message unit, because oid=" + uniqueKey + " doesn't exist");
            }
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
      if (log.TRACE) log.trace(ME, "Login event for client " + sessionInfo.toString());

      if (this.publishLoginEvent) {
         this.publishQosLoginEvent.clearRoutes();
         MsgUnit msgUnit = new MsgUnit(glob, this.xmlKeyLoginEvent, 
                                  sessionInfo.getLoginName().getBytes(),
                                  this.publishQosLoginEvent.getData());
         publish(this.unsecureSessionInfo, msgUnit); // publish that this client has logged in
         this.publishQosLoginEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
      }

      if (log.TRACE) log.trace(ME, " client added:"+sessionInfo.getLoginName());
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

      if (this.publishLogoutEvent) {
         if (log.TRACE) log.trace(ME, "Logout event for client " + sessionInfo.toString());
         this.publishQosLogoutEvent.clearRoutes();
         MsgUnit msgUnit = new MsgUnit(glob, this.xmlKeyLogoutEvent, sessionInfo.getLoginName().getBytes(),
                                       this.publishQosLogoutEvent.getData());
         publish(this.unsecureSessionInfo, msgUnit); // publish that this client logged out
         this.publishQosLogoutEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
      }

      if (log.TRACE) log.trace(ME, " client removed:"+sessionInfo.getLoginName());
      synchronized (loggedIn) {
         if (sessionInfo.getSubjectInfo().getSessions().length == 1) {
            loggedIn.remove(sessionInfo.getLoginName());
            updateInternalUserList(sessionInfo);
         }
      }
   }


   /**
    * Event invoked on new created SubjectInfo.
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException
   {
      log.warn(ME, "Ignoring SubjectInfo added event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on deleted SubjectInfo.
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException
   {
      log.warn(ME, "Ignoring SubjectInfo removed event for client " + e.getSubjectInfo().toString());
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

   final void fireUnSubscribeEvent(SubscriptionInfo subscriptionInfo) throws XmlBlasterException  {
      fireSubscriptionEvent(subscriptionInfo, false);
      subscriptionInfo.decrSubscribeCounter();
   }

   final void fireSubscribeEvent(SubscriptionInfo subscriptionInfo) throws XmlBlasterException  {
      fireSubscriptionEvent(subscriptionInfo, true);
   }

   /**
    * Is fired on subscribe(), unSubscribe() and several times on erase().
    * @param subscribe true: on subscribe, false: on unSubscribe
    */
   private final void fireSubscriptionEvent(SubscriptionInfo subscriptionInfo, boolean subscribe) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Going to fire fireSubscriptionEvent(" + subscribe + ") ...");

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
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml() throws XmlBlasterException {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the RequestBroker as a XML ASCII string
    */
   public final String toXml(String extraOffset) throws XmlBlasterException {
      StringBuffer sb = new StringBuffer(10000);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      TopicHandler[] topicHandlerArr = getTopicHandlerArr();

      sb.append(offset).append("<RequestBroker>");
      for (int ii=0; ii<topicHandlerArr.length; ii++) {
         sb.append(topicHandlerArr[ii].toXml(extraOffset+Constants.INDENT));
      }
      sb.append(bigXmlKeyDOM.printOn(extraOffset+Constants.INDENT).toString());
      sb.append(clientSubscriptions.toXml(extraOffset+Constants.INDENT));
      if (useCluster) {
         sb.append(glob.getClusterManager().toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</RequestBroker>");

      return sb.toString();
   }

   //====== These methods satisfy the I_AdminNode administration interface =======
   public int getNumNodes() {
      if (glob.useCluster() == false) return 1;
      try {
         return glob.getClusterManager().getNumNodes();
      } catch(XmlBlasterException e) {
         return 1;
      }
   }
   public String getNodeList() {
      if (glob.useCluster() == false) return glob.getId();
      try {
         return glob.getClusterManager().getNodeList();
      } catch(XmlBlasterException e) {
         return glob.getId();
      }
   }
   public String getNodeId() {
      return glob.getId();
   }
   public String getVersion() {
      return glob.getVersion();
   }
   public String getBuildTimestamp() {
      return glob.getBuildTimestamp();
   }
   public String getBuildJavaVendor() {
      return glob.getBuildJavaVendor();
   }
   public String getBuildJavaVersion() {
      return glob.getBuildJavaVersion();
   }
   public String getDump() throws XmlBlasterException {
      return glob.getDump();
   }
   public void setDump(String fn) throws XmlBlasterException{
      try {
         org.jutils.io.FileUtil.writeFile(fn, getDump());
         log.info(ME, "Dumped internal state to " + fn);
      }
      catch (org.jutils.JUtilsException e) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_FILEIO, e.id, e.getMessage());
      }

   }
   public String getRunlevel() {
      return ""+glob.getRunlevelManager().getCurrentRunlevel();
   }
   public void setRunlevel(String levelStr) throws XmlBlasterException {
      glob.getRunlevelManager().changeRunlevel(levelStr, true);
   }
   /** How long is the server running (in seconds) */
   public long getUptime() {
      return (System.currentTimeMillis() - this.uptime)/1000L;
   }
   /** Memory in bytes */
   public long getFreeMem() {
      return Runtime.getRuntime().freeMemory();
   }
/*   public void setFreeMem(long freeMem) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "Setting of property 'freeMem' is not supported");
   } */
   public long getTotalMem() {
      return Runtime.getRuntime().totalMemory();
   }
   public long getUsedMem() {
      return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
   }
   public String getGc() {
      System.gc();
      return "OK";
   }
   public void setGc(String dummy) {
      System.gc();
   }
   public String getExit() throws XmlBlasterException {
      setExit("0");
      return "OK";
   }
   public void setExit(String exitValue) throws XmlBlasterException {
      int val = 0;
      try { val = Integer.parseInt(exitValue.trim()); } catch(NumberFormatException e) { log.error(ME, "Invalid exit value=" + exitValue + ", expected an integer"); };
      final int exitVal = val;

      if (glob.isEmbedded()) {
         log.warn(ME, "Ignoring exit(" + exitVal + ") request in embeded mode ('xmlBlaster.isEmbeded' is set true).");
         return;
      }

      final long exitSleep = glob.getProperty().get("xmlBlaster.exit.delay", 2000L);
      Timeout exitTimeout = new Timeout("XmlBlaster ExitTimer");
      Timestamp timeoutHandle = exitTimeout.addTimeoutListener(new I_Timeout() {
            public void timeout(Object userData) {
               log.info(ME, "Administrative exit(" + exitVal + ") after exit-timeout of " + exitSleep + " millis.");
               try {
                  int errors = glob.getRunlevelManager().changeRunlevel(RunlevelManager.RUNLEVEL_HALTED, true);
               }
               catch(Throwable e) {
                  log.warn(ME, "Administrative exit(" + exitVal + ") problems: " + e.toString());
               }
               System.exit(exitVal);
            }
         },
         exitSleep, null);
      log.info(ME, "Administrative exit request, scheduled exit in " + exitSleep + " millis with exit value=" + exitVal + ".");
   }

   public String getHostname() {
      return glob.getBootstrapAddress().getAddress();
   }
   /** The bootstrap port */
   public int getPort() {
      return glob.getBootstrapAddress().getPort();
   }
   /** The number of different users, the sessions may be higher */
   public int getNumClients() {
      return authenticate.getNumSubjects();
   }
   public int getMaxClients() {
      return authenticate.getMaxSubjects();
   }
   /** These are the login names returned, every client may be logged in multiple times
       which you can't see here */
   public String getClientList() {
      return authenticate.getSubjectList();
   }
   public int getNumSysprop() {
      return glob.getProperty().getProperties().size();
   }
   public String getSyspropList() {
      java.util.Properties props = glob.getProperty().getProperties();
      StringBuffer sb = new StringBuffer(props.size()*30);
      for (Enumeration e = props.propertyNames(); e.hasMoreElements();) {
         if (sb.length() > 0)
            sb.append(",");
         sb.append((String) e.nextElement());
      }
      return sb.toString();
   }
   public int getNumMsgs() {
      synchronized (this.messageContainerMap) {
         return this.messageContainerMap.size();
      }
   }
   public String getMsgList() {
      TopicHandler[] topicHandlerArr = getTopicHandlerArr();
      StringBuffer sb = new StringBuffer(topicHandlerArr.length*60);
      for(int ii=0; ii<topicHandlerArr.length; ii++) {
         if (sb.length() > 0)
            sb.append(",");
         sb.append(topicHandlerArr[ii].getUniqueKey());
      }
      return sb.toString();
   }

}
