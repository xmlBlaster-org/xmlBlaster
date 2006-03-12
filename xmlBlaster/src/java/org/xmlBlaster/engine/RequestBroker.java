/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.FileLocator;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.qos.StatusQosData;
import org.xmlBlaster.util.key.KeyData;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.log.I_LogListener;
import org.xmlBlaster.util.log.XbNotifyHandler;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.dispatch.DispatchStatistic;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.qos.storage.TopicStoreProperty;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.TopicEntry;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.AccessPluginManager;
import org.xmlBlaster.engine.mime.I_PublishFilter;
import org.xmlBlaster.engine.mime.PublishPluginManager;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.qos.SubscribeQosServer;
import org.xmlBlaster.engine.qos.UnSubscribeQosServer;
import org.xmlBlaster.engine.qos.EraseQosServer;
import org.xmlBlaster.engine.qos.GetQosServer;
import org.xmlBlaster.engine.qos.GetReturnQosServer;
import org.xmlBlaster.engine.cluster.PublishRetQosWrapper;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.authentication.Authenticate;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
import org.xmlBlaster.engine.runlevel.RunlevelManager;
// import org.xmlBlaster.util.log.LogNotifierDeviceFactory;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;

import java.util.*;

import javax.management.NotificationBroadcasterSupport;
import javax.management.AttributeChangeNotification;
import javax.management.MBeanNotificationInfo;

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
public final class RequestBroker extends NotificationBroadcasterSupport
        implements I_ClientListener, /*I_AdminNode,*/ RequestBrokerMBean,
                     I_RunlevelListener, I_LogListener
{
   private String ME = "RequestBroker";
   private final ServerScope glob;
   private static Logger log = Logger.getLogger(RequestBroker.class.getName());

   /**
    * Contains total count of published messages and get() invocations. 
    */
   private DispatchStatistic dispatchStatistic = new DispatchStatistic();

   private String lastWarning = "";
   private String lastError = "";

   /** the authentication service */
   private Authenticate authenticate = null;          // The authentication service

   /**
    * All TopicHandler objects are stored in this map.
    * <p>
    * key   = oid value from <key oid="..."> (== topicHandler.getUniqueKey())
    * value = TopicHandler object
    */
   private final Map topicHandlerMap = new HashMap(); //Collections.synchronizedMap(new HashMap());
   /**
    * For listeners who want to be informed about topic creation / deletion events.
    */
   private final Set topicListenerSet = Collections.synchronizedSet(new TreeSet());

   private final Set remotePropertiesListeners = Collections.synchronizedSet(new TreeSet());

   /**
    * Store configuration of all topics in xmlBlaster for recovery
    */
   private I_Map topicStore;

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
    * For listeners who want to be informed about subscribe/unsubscribe events.
    * The key is an Integer number where the lowest is the first invoked on subscribe and the 
    * last invoked on unsubscribe.
    */
   private final Map subscriptionListenerMap = Collections.synchronizedMap(new TreeMap());

   /**
    * This is a handle on the big DOM tree with all XmlKey keys (all message meta data)
    */
   private BigXmlKeyDOM bigXmlKeyDOM = null;

   /**
    * This Interface allows to hook in you own persistence driver, configure it through xmlBlaster.properties
    */
   // private I_PersistenceDriver persistenceDriver = null;

   /** Flag for performance reasons only */
   private boolean useOldStylePersistence;

   /** The messageUnit for a login event */
   private boolean publishLoginEvent = true;
   private MsgKeyData xmlKeyLoginEvent = null;
   private org.xmlBlaster.client.qos.PublishQos publishQosForEvents;
   private PublishQosServer publishQosLoginEvent;

   /** Initialize a messageUnit for a userList event */
   private boolean publishUserList = true;
   private MsgKeyData xmlKeyUserListEvent = null;

   /** Initialize a messageUnit for a logout event */
   private boolean publishLogoutEvent = true;
   private MsgKeyData xmlKeyLogoutEvent = null;
   private PublishQosServer publishQosLogoutEvent;

   //private Timeout burstModeTimer;

   private AccessPluginManager accessPluginManager = null;

   private PublishPluginManager publishPluginManager = null;

   // Enforced by I_AdminNode
   /** Incarnation time of this object instance in millis */
   private long startupTime;
   
   /** State during construction */
   private static final int UNDEF = -1;
   private static final int ALIVE = 0;
   private int state = UNDEF;

   /** My JMX registration */
   private JmxMBeanHandle mbeanHandle;


   /**
    * One instance of this represents one xmlBlaster server.
    * @param authenticate The authentication service
    */
   public RequestBroker(Authenticate authenticate) throws XmlBlasterException
   {
      this.authenticate = authenticate;
      this.glob = this.authenticate.getGlobal();

      glob.setRequestBroker(this);
      this.startupTime = System.currentTimeMillis();
      this.mbeanHandle = this.glob.registerMBean(this.glob.getContextNode(), this);

      XbNotifyHandler.instance().register(Level.WARNING.intValue(), this);
      XbNotifyHandler.instance().register(Level.SEVERE.intValue(), this);

      this.useOldStylePersistence = glob.getProperty().get("useOldStylePersistence", false);
      if (this.useOldStylePersistence) {
         log.warning("Old style fielstorage is switched on which is deprecated (-useOldStylePersistence true).");
      }

      glob.getRunlevelManager().addRunlevelListener(this);

      //this.burstModeTimer = new Timeout("BurstmodeTimer");

      myselfLoginName = new SessionName(glob, glob.getNodeId(), internalLoginNamePrefix + "[" + glob.getId() + "]/1");

      initHelperQos();

      org.xmlBlaster.client.qos.ConnectQos connectQos = new org.xmlBlaster.client.qos.ConnectQos(glob);
      connectQos.setSessionName(myselfLoginName);
      connectQos.getSessionQos().setSessionTimeout(0L);  // Lasts forever
      this.unsecureSessionInfo = authenticate.unsecureCreateSession(connectQos);
      this.glob.setInternalSessionInfo(this.unsecureSessionInfo);

      try {
         glob.getCommandManager(this.unsecureSessionInfo);
      }
      catch(XmlBlasterException e) {
         log.severe(e.toString());
      }

      this.ME = "RequestBroker" + glob.getLogPrefixDashed();

      accessPluginManager = new AccessPluginManager(glob);

      publishPluginManager = new PublishPluginManager(glob);

      this.clientSubscriptions = new ClientSubscriptions(glob, this, authenticate);

      this.bigXmlKeyDOM = new BigXmlKeyDOM(this, authenticate);

      authenticate.addClientListener(this);

      this.state = ALIVE;

      if (log.isLoggable(Level.FINER)) log.finer("Server " + glob.getInstanceId() + " instance is created");
   }

   Authenticate getAuthenticate() {
      return this.authenticate;
   }

   /**
    * Put this code in a generic internal message producer class (future release)
    */
   private void initHelperQos() throws XmlBlasterException {

      // Create properties with infinite life time, forceUpdate and historySize=1
      org.xmlBlaster.client.qos.PublishQos publishQos = new org.xmlBlaster.client.qos.PublishQos(glob);
      publishQos.setLifeTime(-1L);
      publishQos.setForceUpdate(true);

      TopicProperty topicProperty = new TopicProperty(glob);
      HistoryQueueProperty historyQueueProperty = new HistoryQueueProperty(glob, glob.getId());
      historyQueueProperty.setMaxEntriesCache(2);
      historyQueueProperty.setMaxEntries(2);
      topicProperty.setHistoryQueueProperty(historyQueueProperty);
      publishQos.setTopicProperty(topicProperty);
      this.publishQosForEvents = publishQos;

      // Should we configure historyQueue and msgUnitStore to be RAM based only?

      this.publishLoginEvent = glob.getProperty().get("loginEvent", true);
      if (this.publishLoginEvent) {
         // Key '__sys__Login' for login event (allows you to subscribe on new clients which do a login)
         org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, Constants.EVENT_OID_LOGIN/*"__sys__Login"*/, "text/plain");
         this.xmlKeyLoginEvent = publishKey.getData();
         this.publishQosLoginEvent = new PublishQosServer(glob, publishQos.getData().toXml(), false); // take copy
      }

      this.publishLogoutEvent = glob.getProperty().get("logoutEvent", true);
      if (this.publishLogoutEvent) {
         // Key '__sys__Logout' for logout event (allows you to subscribe on clients which do a logout)
         org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, Constants.EVENT_OID_LOGOUT/*"__sys__Logout"*/, "text/plain");
         this.xmlKeyLogoutEvent = publishKey.getData();
         this.publishQosLogoutEvent = new PublishQosServer(glob, publishQos.getData().toXml(), false);
      }

      this.publishUserList = glob.getProperty().get("userListEvent", true);
      if (this.publishUserList) {
         // Key '__sys__UserList' for login/logout event
         org.xmlBlaster.client.key.PublishKey publishKey = new org.xmlBlaster.client.key.PublishKey(glob, Constants.EVENT_OID_USERLIST/*"__sys__UserList"*/, "text/plain");
         publishKey.setClientTags("<__sys__internal/>");
         this.xmlKeyUserListEvent = publishKey.getData();
      }
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

   public ClientSubscriptions getClientSubscriptions() {
      return this.clientSubscriptions;
   }

   /**
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.isLoggable(Level.FINER)) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY_PRE) {
           startupTopicStore();
         }
//         else if (to == RunlevelManager.RUNLEVEL_CLEANUP_PRE) {
           // Load all persistent topics from persistent storage
//           loadPersistentMessages();
//         }
      }

      if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            XbNotifyHandler.instance().unregister(Level.WARNING.intValue(), this);
            XbNotifyHandler.instance().unregister(Level.SEVERE.intValue(), this);
            this.glob.unregisterMBean(this.mbeanHandle);
         }
      }
   }

   /**
    * Access the global handle.
    * @return The Global instance of this xmlBlaster server
    */
   public final ServerScope getGlobal() {
      return this.glob;
   }

   /**
    * @return The handle on the persistence storage for all topics
    */
   I_Map getTopicStore() {
      return this.topicStore;
   }


   /**
    * This stores the topics configuration (the publish administrative message - the MsgUnit data struct)
    */
   private void startupTopicStore() throws XmlBlasterException   {
      if (log.isLoggable(Level.FINER)) log.finer("Entering startupTopicStore(), looking for persisted topics");

      boolean wipeOutJdbcDB = glob.getProperty().get("wipeOutJdbcDB", false);
      if (wipeOutJdbcDB) {
         this.glob.setWipeOutDB(wipeOutJdbcDB);
         // it is now the responsability of the QueuePlugin (see JdbcQueueCommonTable) to
         // really perform the wipeout, the request broker only gives him an order to do so. 
/*         
         String tableNamePrefix = "XMLBLASTER";
         tableNamePrefix = glob.getProperty().get("queue.persistent.tableNamePrefix", tableNamePrefix).toUpperCase();
         log.warn(ME, "You have set '-wipeOutJdbcDB true', we will destroy now the complete JDBC persistence store entries of prefix="+tableNamePrefix);
         try {
            java.util.Properties prop = new java.util.Properties();
            prop.put("tableNamePrefix", tableNamePrefix);
            JdbcManagerCommonTable.wipeOutDB(glob, "JDBC", "1.0", prop, true);
         }
          catch (XmlBlasterException e) {
          log.error(ME, "Wipe out of JDBC database entries failed: " + e.getMessage());
         }
*/
      }

      boolean useTopicStore = glob.getProperty().get("useTopicStore", true);
      if (!useTopicStore) {
         log.warning("Persistent and recoverable topics are switched off with '-useTopicStore false', topics are handled RAM based only.");
         return;
      }

      synchronized (this) {
         // TODO: get TopicStoreProperty from administrator
         //TopicStoreProperty topicStoreProperty = this.topicProperty.getTopicStoreProperty();
         TopicStoreProperty topicStoreProperty = new TopicStoreProperty(glob, glob.getStrippedId());

         if (this.topicStore == null) {
            String type = topicStoreProperty.getType();
            String version = topicStoreProperty.getVersion();
            // e.g. "topicStore:/node/heron" is the unique name of the data store:
            StorageId topicStoreId = new StorageId("topicStore", glob.getStrippedId());
            this.topicStore = glob.getStoragePluginManager().getPlugin(type, version, topicStoreId, topicStoreProperty);
            //this.topicStore = new org.xmlBlaster.engine.msgstore.ram.MapPlugin();
            log.info("Activated storage '" + this.topicStore.getStorageId() + "' for persistent topics, found " + this.topicStore.getNumOfEntries() + " topics to recover.");

            I_MapEntry[] mapEntryArr = this.topicStore.getAll(null);
            boolean fromPersistenceStore = true;
            for(int i=0; i<mapEntryArr.length; i++) {
               TopicEntry topicEntry = (TopicEntry)mapEntryArr[i];
               PublishQosServer publishQosServer = new PublishQosServer(glob,
                       (MsgQosData)topicEntry.getMsgUnit().getQosData(), fromPersistenceStore);
               publishQosServer.setTopicEntry(topicEntry); // Misuse PublishQosServer to transport the topicEntry
               try {
                  publish(unsecureSessionInfo, topicEntry.getMsgUnit(), publishQosServer);
               }
               catch (XmlBlasterException e) {
                  log.severe("Restoring topic '" + topicEntry.getMsgUnit().getKeyOid() + "' from persistency failed: " + e.getMessage());
               }
            }
         }
         else {
            log.info("Reconfiguring topics store.");
            this.topicStore.setProperties(topicStoreProperty);
         }
      }
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
      // Note: We could change to 'public' as the CommandManager transports it to public scope already
      //       with glob.getCommandManager().getSessionInfo()
      // and   engine.Global.getInternalSessionInfo()
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
    * <p>
    * The usual sources to send dead letters are:
    * </p>
    * <ol>
    *   <li>A publish of a message fails, the message is lost but you can handle it
    *       if you subscribe to dead messages
    *   </li>
    *   <li>A subscribe fails because a mime plugin throws an exception
    *   </li>
    *   <li>A callback fails
    *   </li>
    * </ol>
    * @param entries The message to send as dead letters
    * @param queue The belonging queue or null
    * @param reason A human readable text describing the problem
    * @return State information returned from the publish call (is never null)
    */
   public String[] deadMessage(MsgQueueEntry[] entries, I_Queue queue, String reason)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Publishing " + entries.length + " dead messages.");
      if (entries == null) {
         log.severe("deadMessage() with null argument");
         Thread.dumpStack();
         return new String[0];
      }

      try {
         if (log.isLoggable(Level.FINE)) log.fine("Publishing " + entries.length + " volatile dead messages");
         String[] retArr = new String[entries.length];
         PublishQos pubQos = new PublishQos(glob);
         pubQos.setVolatile(true);
         for (int ii=0; ii<entries.length; ii++) {
            MsgQueueEntry entry = entries[ii];
            if (entry == null) {
               log.severe("Didn't expect null element in MsgQueueEntry[], ignoring it");
               continue;
            }
            MsgUnit origMsgUnit = null;
            if (entry instanceof ReferenceEntry) {
               ReferenceEntry referenceEntry = (ReferenceEntry)entry;
               origMsgUnit = ((ReferenceEntry)entry).getMsgUnitOrNull();
               if (origMsgUnit == null) {
                  if (log.isLoggable(Level.FINE)) log.fine("Ignoring dead message for destroyed callback queue entry " + referenceEntry.getLogId());
                  continue;
               }
            }
            else {
               log.severe("PANIC: Internal error in deadMessage data type");
               retArr[ii] = "PANIC";
               continue;
            }
            try {
               if (entry.getKeyOid().equals(Constants.OID_DEAD_LETTER)) {  // Check for recursion of dead letters
                  log.severe("PANIC: Recursive dead message is lost, no recovery possible - dumping to file not yet coded: " +
                                origMsgUnit.toXml() + ": " +
                                ((reason != null) ? (": " + reason) : "") );
                  retArr[ii] = entry.getKeyOid();
                  Thread.dumpStack();
                  continue;
               }
               if (origMsgUnit.getQosData().getClientProperties().get("__isErrorHandled") != null) {  // Check for recursion of dead letters
                  log.warning("Recursive message '" + entry.getLogId() + "' is error handled already (sent as dead letter), we ignore it.");
                  retArr[ii] = entry.getKeyOid();
                  continue;
               }
               origMsgUnit.getQosData().addClientProperty("__isErrorHandled", true); // Mark the original to avoid looping if failed client is the dead message listener
               String text = "Generating dead message '" + entry.getLogId() + "'" +
                            " from publisher=" + entry.getSender() +
                            " because delivery with queue '" +            // entry.getReceiver() is recognized in queueId
                            ((queue == null) ? "null" : queue.getStorageId().toString()) + "' failed" +
                            ((reason != null) ? (": " + reason) : "");
               log.warning(text);
               PublishKey publishKey = new PublishKey(glob, Constants.OID_DEAD_LETTER);
               publishKey.setClientTags("<oid>"+entry.getKeyOid()+"</oid>");
               // null: use the content from origMsgUnit:
               pubQos.addClientProperty(Constants.CLIENTPROPERTY_DEADMSGKEY, origMsgUnit.getKey()); //"__key"
               pubQos.addClientProperty(Constants.CLIENTPROPERTY_DEADMSGQOS, origMsgUnit.getQos()); //"__qos"
               pubQos.addClientProperty(Constants.CLIENTPROPERTY_OID, origMsgUnit.getKeyOid()); //"__oid"
               pubQos.addClientProperty(Constants.CLIENTPROPERTY_RCVTIMESTAMP, origMsgUnit.getQosData().getRcvTimestamp()); //"__rcvTimestamp"
               pubQos.addClientProperty(Constants.CLIENTPROPERTY_DEADMSGREASON, text); //"__deadMessageReason"
               MsgUnit msgUnit = new MsgUnit(origMsgUnit, publishKey.getData(), null, pubQos.getData());
               retArr[ii] = publish(unsecureSessionInfo, msgUnit);
            }
            catch(Throwable e) {
               log.severe("PANIC: " + entry.getKeyOid() + " dead letter is lost, no recovery possible - dumping to file not yet coded: " + e.toString() + "\n" + origMsgUnit.toXml());
               e.printStackTrace();
               retArr[ii] = entry.getKeyOid();
            }
         }
         return retArr;
      }
      catch (Throwable e) {
         log.severe("PANIC: " + entries.length + " dead letters are lost, no recovery possible:" + e.getMessage());
         for (int ii=0; ii<entries.length; ii++) {
            MsgQueueEntry entry = entries[ii];
            try {
               if (entry == null) {
                  continue;
               }
               /*
               else if (entry instanceof MsgUnitWrapper) {
                  MsgUnitWrapper msgUnitWrapper = (MsgUnitWrapper)entry;
                  String fileName = glob.getMsgFileDumper().store(msgUnitWrapper);
                  log.warn(ME, "Dumped lost message to file " + fileName);
               }
               */
               else if (entry instanceof MsgQueueHistoryEntry) {
                  log.warning("History entry is lost: " + entry.toXml());
               }
               else if (entry instanceof MsgQueueUpdateEntry) {
                  ReferenceEntry referenceEntry = (ReferenceEntry)entry;
                  if (referenceEntry.isDestroyed()) {
                     if (log.isLoggable(Level.FINE)) log.fine("Ignoring detroyed callback message " + entry.getLogId());
                  }
                  else {
                     log.warning("Callback of message failed unrecoverably: " + entry.toXml());
                  }
               }
               else {
                  log.severe("PANIC: Unrecoverable lost message " + entry.toXml());
               }
            }
            catch (Throwable th) {
               log.severe("PANIC: Unrecoverable lost message " + entry.toXml() + ": " + th.getMessage());
            }
         }
      }

      return new String[0];
   }

   /**
    * Try to load all persistent stored messages.
    */
/*   
   private void loadPersistentMessages()
   {
      if (this.useOldStylePersistence == false) return;

      if(log.isLoggable(Level.FINER)) log.call(ME,"Loading messages from persistence to Memory ...");
      this.persistenceDriver = getPersistenceDriver(); // Load persistence driver
      if (this.persistenceDriver == null) return;
      int num=0;
      try {
         boolean lazyRecovery = glob.getProperty().get("Persistence.LazyRecovery", true);
         if(log.isLoggable(Level.FINE)) log.trace(ME,"LazyRecovery is switched="+lazyRecovery);

         if (lazyRecovery)
         {
            // Recovers all persistent messages from the loaded persistence driver.
            // The RequestBroker must self pulish messages.
            Enumeration oidContainer = this.persistenceDriver.fetchAllOids();

            while(oidContainer.hasMoreElements())
            {
               String oid = (String)oidContainer.nextElement();
               // Fetch the MsgUnit by oid from the persistence
               MsgUnit msgUnit = this.persistenceDriver.fetch(oid);

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
         log.info(ME,"Loaded " + num + " persistent messages from persistence to Memory.");
   }
*/

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
/*
   final I_PersistenceDriver getPersistenceDriver()
   {
      if (this.useOldStylePersistence == false) return (I_PersistenceDriver)null;

      if (this.persistenceDriver == null) {
         String pluginType    = glob.getProperty().get("Persistence.Driver.Type", "filestore");
         String pluginVersion = glob.getProperty().get("Persistence.Driver.Version", "1.0");

         try {
            this.persistenceDriver = pluginManager.getPlugin(pluginType, pluginVersion);
         } catch (Exception e) {
            log.error(ME, "xmlBlaster will run memory based only, no persistence driver is avalailable, can't instantiate [" + pluginType + "][" + pluginVersion +"]: " + e.toString());
            e.printStackTrace();
            this.useOldStylePersistence = false;
            return (I_PersistenceDriver)null;
         }

         //log.info(ME, "Loaded persistence driver '" + this.persistenceDriver.getName() + "[" + pluginType + "][" + pluginVersion +"]'");
         log.info(ME, "Loaded persistence driver plugin '[" + pluginType + "][" + pluginVersion +"]'");
         //Thread.currentThread().dumpStack();
      }
      return this.persistenceDriver;
   }
*/

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
      log.warning("setting client attributes is not yet supported: " + xmlAttr_literal);
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
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.subscribe.html">The interface.subscribe requirement</a>
    */
/*   
   String subscribe(SessionInfo sessionInfo, QueryKeyData xmlKey, SubscribeQosServer subscribeQos) throws XmlBlasterException   {
      return subscribe(sessionInfo, xmlKey, subscribeQos, false);
   }   
*/
   public String subscribe(SessionInfo sessionInfo, QueryKeyData xmlKey, SubscribeQosServer subscribeQos) throws XmlBlasterException   {
      if (!sessionInfo.hasCallback()) {
         throw new XmlBlasterException(glob, ErrorCode.USER_SUBSCRIBE_NOCALLBACK, ME, "You can't subscribe to '" + xmlKey.getOid() + "' without having a callback server");
      }
      try {
         if (log.isLoggable(Level.FINER)) log.finer("Entering subscribe(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "', domain='" + xmlKey.getDomain() + "') from client '" + sessionInfo.getLoginName() + "' ...");
         String returnOid = "";

         if (subscribeQos.getMultiSubscribe() == false) {
            Vector vec =  clientSubscriptions.getSubscription(sessionInfo, xmlKey);
            if (vec != null && vec.size() > 0) {
               for (int i=0; i<vec.size(); i++) {
                  SubscriptionInfo sub = (SubscriptionInfo)vec.elementAt(i);
                  sub.update(subscribeQos);
               }
               log.info("Ignoring duplicate subscription '" + 
                       ((xmlKey.getOid()==null)?((xmlKey.getDomain()==null)?xmlKey.getQueryString():xmlKey.getDomain()):xmlKey.getOid()) +
                        "' as you have set multiSubscribe to false");
               StatusQosData qos = new StatusQosData(glob, MethodName.SUBSCRIBE);
               SubscriptionInfo i = (SubscriptionInfo)vec.elementAt(0);
               qos.setState(Constants.STATE_WARN);
               qos.setSubscriptionId(i.getSubscriptionId());
               return qos.toXml();
            }
         }

         SubscriptionInfo subsQuery = null;
         if (xmlKey.isQuery()) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
         // if (true) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
            subsQuery = new SubscriptionInfo(glob, sessionInfo, xmlKey, subscribeQos);
            returnOid = subsQuery.getSubscriptionId(); // XPath query
            fireSubscribeEvent(subsQuery);
         }

         KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, subscribeQos.getData());

         for (int jj=0; jj<keyDataArr.length; jj++) {
            KeyData xmlKeyExact = keyDataArr[jj];
            if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
               xmlKeyExact = xmlKey;
            else if (xmlKeyExact != null && xmlKey.isDomain()) {
               xmlKeyExact.setQueryType(xmlKey.getQueryType());
            }
            SubscriptionInfo subs = null;
            if (sessionInfo.getConnectQos().duplicateUpdates() == false) {
               Vector vec =  clientSubscriptions.getSubscriptionByOid(sessionInfo, xmlKeyExact.getOid(), true);
               if (vec != null) {
                  if (vec.size() > 0) {
                     subs = (SubscriptionInfo)vec.firstElement();
                     if (log.isLoggable(Level.FINE)) log.fine("Session '" + sessionInfo.getId() +
                                    "', message '" + xmlKeyExact.getOid() + "' is subscribed " +
                                    vec.size() + " times with duplicateUpdates==false");
                  }
                  if (vec.size() > 1)
                     log.severe("Internal problem for session '" + sessionInfo.getId() + "', message '" + xmlKeyExact.getOid() + "' is subscribed " + vec.size() + " times but duplicateUpdates==false!");
               }
            }

            if (subs == null) {
               if (subsQuery != null) {
                  subs = new SubscriptionInfo(glob, sessionInfo, subsQuery, xmlKeyExact);
                  subsQuery.addSubscription(subs);
               }
               else
                  subs = new SubscriptionInfo(glob, sessionInfo, xmlKeyExact, subscribeQos);
            }
            
            subscribeToOid(subs, false); // fires event for subscription

            if (returnOid.equals("")) returnOid = subs.getSubscriptionId();
         }

         StatusQosData qos = null;
         if (this.glob.isClusterManagerReady()) { // cluster support - forward message to master
            try {
               subscribeQos.setSubscriptionId(returnOid); // force the same subscriptionId on all cluster nodes
               SubscribeReturnQos ret = glob.getClusterManager().forwardSubscribe(sessionInfo, xmlKey, subscribeQos);
               if (ret != null)
                  qos = ret.getData();
               //Thread.currentThread().dumpStack();
               //if (ret != null) return ret.toXml();
            }
            catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                  this.glob.setUseCluster(false);
               }
               else {
                  e.printStackTrace();
                  throw e;
               }
            }
         }

         if (qos == null || qos.getSubscriptionId() == null ||  qos.getSubscriptionId().length() < 1) {
            // The cluster subId is unique in another cluster node as well e.g.: "__subId:heron-2"
            if (qos == null) qos = new StatusQosData(glob, MethodName.SUBSCRIBE);
            qos.setSubscriptionId(returnOid);
         }
         if (log.isLoggable(Level.FINER)) log.finer("Leaving subscribe(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "', domain='" + xmlKey.getDomain() + "') from client '" + sessionInfo.getLoginName() + "' -> subscriptionId='" + qos.getSubscriptionId() + "'");
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
    * <p />
    * In the cluster environment all messages are accessed from the master cluster node,
    * tuning with XmlBlasterAccess.synchronousCache is not yet implemented.
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
         if (log.isLoggable(Level.FINER)) log.finer("Entering get(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "') ...");

         if ("__refresh".equals(xmlKey.getOid())) {
            return new MsgUnit[0]; // get() with oid="__refresh" do only refresh the login session
         }

         if (xmlKey.isAdministrative()) {
            if (!glob.supportAdministrative())
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_ADMIN_UNAVAILABLE, ME, "Sorry administrative get() is not available, try to configure xmlBlaster.");
            MsgUnit[] raw = glob.getMomClientGateway().getCommand(sessionInfo, xmlKey, getQos.getData());
            if (getQos.getWantContent())  return raw;
            
            MsgUnit[] msgUnitArr = new MsgUnit[raw.length];
            for(int i=0; i<raw.length; i++) {
               // byte[] cont = (getQos.getWantContent()) ? raw[i].getContent() : new byte[0];
               // msgUnitArr[i] = new MsgUnit(key, cont, raw[i].getQos());
               msgUnitArr[i] = new MsgUnit(raw[i], null, new byte[0], null);
            }
            return msgUnitArr;
         }

         if (Constants.JDBC_OID.equals(xmlKey.getOid()/*"__sys__jdbc"*/)) { // Query RDBMS !!! hack, we need a general service interface
            org.xmlBlaster.protocol.jdbc.XmlDBAdapter adap = new org.xmlBlaster.protocol.jdbc.XmlDBAdapter(glob,
                        xmlKey.getQueryString().getBytes(), (org.xmlBlaster.protocol.jdbc.NamedConnectionPool)this.glob.getObjectEntry("NamedConnectionPool-"+glob.getId()));
            return adap.query();
         }

         KeyData[] keyDataArr = queryMatchingKeys(sessionInfo, xmlKey, getQos.getData());
         ArrayList msgUnitList = new ArrayList(keyDataArr.length);

         if (log.isLoggable(Level.FINE)) log.fine("get(): " + ((keyDataArr!=null&&keyDataArr.length>0&&keyDataArr[0]!=null)?"Found local match "+keyDataArr[0].toXml():"No local match"));
         
         // Always forward the get request to the master
         // even if there are no matching keys
         // In the cluster environment all messages are accessed from the master cluster node,
         // tuning with XmlBlasterAccess.synchronousCache is not yet implemented.
         if (this.glob.isClusterManagerReady()) { // cluster support - forward erase to master
           try {
               MsgUnit tmp[] = glob.getClusterManager().forwardGet(sessionInfo, xmlKey, getQos);
               if (tmp != null && tmp.length > 0) {
                   log.info("get() access of " + tmp.length + " messages from cluster master");
                   for (int jj=0; jj<tmp.length; jj++) {
                       msgUnitList.add(tmp[jj]);
                       // We currently don' cache the message here in the slave !!!
                       // We could do it with the xmlBlasterConnection.initCache(int size)
                   }
               }
           }
           catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                   this.glob.setUseCluster(false);
               }
               else {
                   e.printStackTrace();
                   throw e;
               }
           }
           if (log.isLoggable(Level.FINE)) log.fine("get(): Found " + msgUnitList.size() + " remote matches for " + xmlKey.toXml());
         }

         NEXT_MSG: for (int ii=0; ii<keyDataArr.length; ii++) {
            KeyData xmlKeyExact = keyDataArr[ii];
            if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
               xmlKeyExact = xmlKey;

            TopicHandler topicHandler = getMessageHandlerFromOid(xmlKeyExact.getOid());

            if( topicHandler == null ) {
               /*
               if (this.glob.useCluster()) { // cluster support - forward erase to master
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
                        this.glob.setUseCluster(false);
                     }
                     else {
                        e.printStackTrace();
                        throw e;
                     }
                  }
               }
               */
               if (log.isLoggable(Level.FINE)) log.fine("get(): The key '"+xmlKeyExact.getOid()+"' is not available.");
               continue NEXT_MSG;

            } // topicHandler==null

            if (topicHandler.isAlive()) {

               int numEntries = getQos.getHistoryQos().getNumEntries();
               MsgUnitWrapper[] msgUnitWrapperArr = topicHandler.getMsgUnitWrapperArr(numEntries, getQos.getHistoryQos().getNewestFirst());

               NEXT_HISTORY:
               for(int kk=0; kk<msgUnitWrapperArr.length; kk++) {

                  MsgUnitWrapper msgUnitWrapper = msgUnitWrapperArr[kk];
                  if (msgUnitWrapper == null) {
                     continue NEXT_HISTORY;
                  }

                  if (this.glob.useCluster() && !msgUnitWrapper.getMsgQosData().isAtMaster()) {
                     if (log.isLoggable(Level.FINE)) log.fine("get(): Ignore message as we are not the master: " + msgUnitWrapper.toXml());
                     continue NEXT_HISTORY;
                  }

                  AccessFilterQos[] filterQos = getQos.getAccessFilterArr();
                  if (filterQos != null) {
                     if (log.isLoggable(Level.FINE)) log.fine("Checking " + filterQos.length + " filters");
                     for (int jj=0; jj<filterQos.length; jj++) {
                        I_AccessFilter filter = getAccessPluginManager().getAccessFilter(
                                                     filterQos[jj].getType(),
                                                     filterQos[jj].getVersion(),
                                                     msgUnitWrapper.getContentMime(),
                                                     msgUnitWrapper.getContentMimeExtended());
                        if (log.isLoggable(Level.FINE)) log.fine("get("+xmlKeyExact.getOid()+") filter=" + filter + " qos=" + getQos.toXml());
                        if (filter != null && filter.match(sessionInfo,
                                                     msgUnitWrapper.getMsgUnit(),
                                                     filterQos[jj].getQuery()) == false)
                           continue NEXT_HISTORY; // filtered message is not send to client
                     }
                  }

                  if (msgUnitWrapper.isExpired()) {
                     continue NEXT_HISTORY;
                  }

                  MsgUnit mm = msgUnitWrapper.getMsgUnit();
                  if (mm == null) {
                     continue NEXT_HISTORY; // WeakReference to cache lost and lookup failed
                  }

                  GetReturnQosServer retQos = new GetReturnQosServer(glob, msgUnitWrapper.getMsgQosData(), Constants.STATE_OK);
                  byte[] cont = (getQos.getWantContent()) ? mm.getContent() : new byte[0];
                  mm = new MsgUnit(mm, null, cont, retQos.getData());
                  msgUnitList.add(mm);

               } // for each history entry

            } // topicHandler.isAlive()
         }

         MsgUnit[] msgUnitArr = (MsgUnit[])msgUnitList.toArray(new MsgUnit[msgUnitList.size()]);
         this.dispatchStatistic.incrNumGet(msgUnitArr.length);
         if (log.isLoggable(Level.FINE)) log.fine("Returning for get() " + msgUnitArr.length + " messages");
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

   public void updateInternalUserList() throws XmlBlasterException {
      // "__sys__UserList";
      if (this.publishUserList && this.state == ALIVE) {
         // Create QoS with new timestamp
         PublishQosServer publishQosUserListEvent = new PublishQosServer(glob, this.publishQosForEvents.getData().toXml(), false);
         //publishQosUserListEvent.clearRoutes();
         MsgUnit msgUnit = new MsgUnit(this.xmlKeyUserListEvent,
                                 this.authenticate.getSubjectList().getBytes(), //content.getBytes(),
                                 publishQosUserListEvent.getData());
         publish(this.unsecureSessionInfo, msgUnit);
         publishQosUserListEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
         if (log.isLoggable(Level.FINE)) log.fine("Refreshed internal state for '" + this.xmlKeyUserListEvent.getOid() + "'");
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
   private KeyData[] queryMatchingKeys(SessionInfo sessionInfo, QueryKeyData queryKeyData, QueryQosData qos)  throws XmlBlasterException
   {
      String clientName = sessionInfo.toString();

      if (queryKeyData.isQuery()) { // query: subscription without a given oid
         ArrayList oidList = bigXmlKeyDOM.parseKeyOid(sessionInfo, queryKeyData.getQueryString(), qos);
         ArrayList strippedList = new ArrayList();
         for(int i=0; i<oidList.size(); i++) {
            TopicHandler topicHandler = getMessageHandlerFromOid((String)oidList.get(i));
            if (topicHandler != null) {
               KeyData keyData = topicHandler.getMsgKeyData();
               if (keyData != null) {
                  strippedList.add(keyData);
               }
            }
         }
         return (KeyData[])strippedList.toArray(new KeyData[strippedList.size()]);
      }

      else if (queryKeyData.isExact()) { // subscription with a given oid
         if (log.isLoggable(Level.FINE)) log.fine("Access Client " + clientName + " with EXACT oid='" + queryKeyData.getOid() + "'");
         TopicHandler topicHandler = getMessageHandlerFromOid(queryKeyData.getOid());
         if (topicHandler == null || topicHandler.getMsgKeyData() == null) {
            return new KeyData[] { null }; // add arr[0]=null as a place holder
         }
         // return new KeyData[] { topicHandler.getMsgKeyData() };
         return new KeyData[] { queryKeyData };
      }

      else if (queryKeyData.isDomain()) { // a domain attribute is given
         String domain = queryKeyData.getDomain();
         if (log.isLoggable(Level.FINE)) log.fine("Access Client " + clientName + " with DOMAIN domain='" + domain + "'");
         if (domain == null) {
            log.warning("The DOMAIN query has a domain=null, no topics found");
            return new KeyData[0];
         }
         TopicHandler[] topics = getTopicHandlerArr();
         ArrayList strippedList = new ArrayList();
         for(int i=0; i<topics.length; i++) {
            TopicHandler topicHandler = topics[i];
            if (topicHandler.getMsgKeyData() != null &&
                domain.equals(topicHandler.getMsgKeyData().getDomain()))
               strippedList.add(topicHandler.getMsgKeyData());
         }
         if (log.isLoggable(Level.FINE)) log.fine("Found " + strippedList.size() + " domain matches for '" + domain + "'");
         return (KeyData[])strippedList.toArray(new KeyData[strippedList.size()]);
      }

      else {
         log.warning("Sorry, can't access, query syntax is unknown: " + queryKeyData.getQueryType());
         throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_TYPE_INVALID, ME, "Sorry, can't access, query syntax is unknown: " + queryKeyData.getQueryType());
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
   private TopicHandler[] queryMatchingTopics(SessionInfo sessionInfo, QueryKeyData queryKeyData, QueryQosData qos)  throws XmlBlasterException
   {
      String clientName = sessionInfo.toString();

      if (queryKeyData.isQuery()) { // query: subscription without a given oid
         ArrayList oidList = bigXmlKeyDOM.parseKeyOid(sessionInfo, queryKeyData.getQueryString(), qos);
         ArrayList strippedList = new ArrayList();
         for(int i=0; i<oidList.size(); i++) {
            TopicHandler topicHandler = getMessageHandlerFromOid((String)oidList.get(i));
            if (topicHandler != null) {
               strippedList.add(topicHandler);
            }
         }
         return (TopicHandler[])strippedList.toArray(new TopicHandler[strippedList.size()]);
      }

      else if (queryKeyData.isExact()) { // subscription with a given oid
         if (log.isLoggable(Level.FINE)) log.fine("Access Client " + clientName + " with EXACT oid='" + queryKeyData.getOid() + "'");
         TopicHandler topicHandler = getMessageHandlerFromOid(queryKeyData.getOid());
         if (topicHandler == null) {
            return new TopicHandler[] { null }; // add arr[0]=null as a place holder
         }
         return new TopicHandler[] { topicHandler };
      }

      else if (queryKeyData.isDomain()) { // a domain attribute is given
         String domain = queryKeyData.getDomain();
         if (log.isLoggable(Level.FINE)) log.fine("Access Client " + clientName + " with DOMAIN domain='" + domain + "'");
         if (domain == null) {
            log.warning("The DOMAIN query has a domain=null, no topics found");
            return new TopicHandler[0];
         }
         TopicHandler[] topics = getTopicHandlerArr();
         ArrayList strippedList = new ArrayList();
         for(int i=0; i<topics.length; i++) {
            TopicHandler topicHandler = topics[i];
            if (domain.equals(topicHandler.getMsgKeyData().getDomain()))
               strippedList.add(topicHandler);
         }
         if (log.isLoggable(Level.FINE)) log.fine("Found " + strippedList.size() + " domain matches for '" + domain + "'");
         return (TopicHandler[])strippedList.toArray(new TopicHandler[strippedList.size()]);
      }

      else {
         log.warning("Sorry, can't access, query syntax is unknown: " + queryKeyData.getQueryType());
         throw new XmlBlasterException(glob, ErrorCode.USER_QUERY_TYPE_INVALID, ME, "Sorry, can't access, query syntax is unknown: " + queryKeyData.getQueryType());
      }
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
      synchronized(this.topicHandlerMap) {
         Object obj = this.topicHandlerMap.get(oid);
         if (obj == null) {
            if (log.isLoggable(Level.FINE)) log.fine("getMessageHandlerFromOid(): key oid " + oid + " is unknown, topicHandler == null");
            return null;
         }
         return (TopicHandler)obj;
      }
   }

   /**
    * @return A current snapshot of all topics (never null)
    */
   public final TopicHandler[] getTopicHandlerArr() {
      synchronized(this.topicHandlerMap) {
         return (TopicHandler[])this.topicHandlerMap.values().toArray(new TopicHandler[this.topicHandlerMap.size()]);
      }
   }

   /**
    * Make the topicHandler persistent for crash recovery and shutdown/startup cycle.
    * @return Number of new entries added: 0 if entry existed, 1 if new entry added
    */
   public final int addPersistentTopicHandler(TopicEntry topicEntry) throws XmlBlasterException {
      if (this.topicStore != null) {
         if (log.isLoggable(Level.FINE)) log.fine("Persisting topicEntry");
         return this.topicStore.put(topicEntry);
      }
      return 0;
   }

   /**
    * Remove the persistent TopicHandler entry.
    * @return the number of elements erased.
    */
   public final int removePersistentTopicHandler(TopicEntry topicEntry) throws XmlBlasterException {
      if (this.topicStore != null) {
         if (log.isLoggable(Level.FINE)) log.fine("Removing persisting topicEntry");
         return this.topicStore.remove(topicEntry);
      }
      return 0;
   }

   /**
    * @return The previous topic handler (there should never be any in our context).
    */
   public final TopicHandler addTopicHandler(TopicHandler topicHandler) {
      synchronized(topicHandlerMap) {
         TopicHandler old = (TopicHandler)topicHandlerMap.put(topicHandler.getUniqueKey(), topicHandler); // ram lookup
         fireTopicEvent(topicHandler);
         return old;
      }
   }

   /**
    * Event invoked on message erase() invocation.
    */
   void topicErase(TopicHandler topicHandler) throws XmlBlasterException {
      if (topicHandler.hasExactSubscribers()) {
         log.warning("Erase event occured for oid=" + topicHandler.getUniqueKey() + ", " + topicHandler.numSubscribers() + " subscribers exist ...");
      }
      String uniqueKey = topicHandler.getUniqueKey();
      if (log.isLoggable(Level.FINE)) log.fine("Erase event occured for oid=" + uniqueKey + ", removing message from my map ...");
      synchronized(topicHandlerMap) {
         Object obj = topicHandlerMap.remove(uniqueKey);
         if (obj == null) {
            log.warning("Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
            throw new XmlBlasterException(glob, ErrorCode.USER_OID_UNKNOWN, ME, "Sorry, can't remove message unit, because oid=" + uniqueKey + " doesn't exist");
         }
         fireTopicEvent(topicHandler);
      }
   }

   /**
    * Low level subscribe, is called when the <key oid='...' queryType='EXACT'> to subscribe is exactly known.
    * <p>
    * If the message is yet unknown, an empty is created to hold the subscription.
    * @param subs
    * @param calleeIsXPathMatchCheck true The calling thread is internally to check if a Query matches a new published topic
    *        false The callee is a subscribe() thread from a client
    */
   private void subscribeToOid(SubscriptionInfo subs, boolean calleeIsXPathMatchCheck) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering subscribeToOid(subId="+subs.getSubscriptionId()+", oid="+subs.getKeyData().getOid()+", queryType="+subs.getKeyData().getQueryType()+") ...");
      String uniqueKey = subs.getKeyData().getOid();
      TopicHandler topicHandler = null;
      synchronized(topicHandlerMap) {
         Object obj = topicHandlerMap.get(uniqueKey);
         if (obj == null) {
            // This is a new Message, yet unknown ...
            topicHandler = new TopicHandler(this, uniqueKey); // adds itself to topicHandlerMap
         }
         else {
            // This message was known before ...
            topicHandler = (TopicHandler)obj;
         }
      }

      subs.incrSubscribeCounter();

      fireSubscribeEvent(subs);  // inform all listeners about this new subscription

      // Now the MsgUnit exists and all subcription handling is done, subscribe to it -> fires update to client
      topicHandler.addSubscriber(subs, calleeIsXPathMatchCheck);
   }

   /**
    * This method returns the unprotected Authenticate object. 
    * @param secretSessionId the secret Session Id of the invoker.
    * TODO in future an authorization operation shall be performed here
    * @return
    */
   public Authenticate getAuthenticate(String secretSessionId) {
      return this.authenticate;
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
    * @return An array of canceled subscriptions e.g.
    * <pre>
    *   &lt;qos>
    *      &lt;subscribe id='__subId:2'/>
    *      &lt;isUnSubscribe/>
    *   &lt;/qos>
    * </pre>
    */
   public String[] unSubscribe(SessionInfo sessionInfo, QueryKeyData xmlKey, UnSubscribeQosServer unSubscribeQos) throws XmlBlasterException
   {
      try {
         if (log.isLoggable(Level.FINER)) log.finer("Entering unSubscribe(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() + "', query='" + xmlKey.getQueryString() + "', domain='" + xmlKey.getDomain() + "') ...");

         if (this.glob.isClusterManagerReady()) { // cluster support - forward message to master
            try {
               UnSubscribeReturnQos[] ret = glob.getClusterManager().forwardUnSubscribe(sessionInfo, xmlKey, unSubscribeQos);
               if (ret != null) {
                  log.info("unSubscribe of '" + xmlKey.getNiceString() + "' matched " + ret.length + " entries in remote cluster");
                  // Currently we only return the local matched subscriptions,
                  // we need to discuss how they can differ from the remote cluster
                  // unSubscribes ...
               }
            }
            catch (XmlBlasterException e) {
               if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                  log.warning("unSubscribe of '" + xmlKey.getNiceString() + "' entries in remote cluster: " + e.getMessage());
                  this.glob.setUseCluster(false);
               }
               else {
                  log.warning("unSubscribe of '" + xmlKey.getNiceString() + "' in remote cluster: " + e.getMessage());
                  e.printStackTrace();
                  throw e;
               }
            }
         }
         else {
            if (this.glob.useCluster())
               log.warning("unSubscribe not forwarded to cluster as ClusterManager is not ready");
         }

         Set subscriptionIdSet = new HashSet();

         String id = xmlKey.getOid();

         if (SubscriptionInfo.isSubscribeId(id)) {
            SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getOid());
            if (subs != null) {
               SubscriptionInfo[] childs = subs.getChildrenSubscriptions();
               if (childs != null) {
                  if (log.isLoggable(Level.FINE)) log.fine("unSubscribe() Traversing " + childs.length + " childs");
                  for (int ii=0; ii<childs.length; ii++) {
                     SubscriptionInfo so = childs[ii];
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
               log.warning("UnSubscribe of " + xmlKey.getOid() + " by session " + sessionInfo.getId() + " failed");
               if (log.isLoggable(Level.FINEST)) log.finest(toXml());
            }
         }
         else { // Try to unSubscribe with topic oid instead of subscribe id:
            String suppliedXmlKey = xmlKey.getOid(); // remember supplied oid, another oid may be generated later

            TopicHandler[] topicHandlerArr = queryMatchingTopics(sessionInfo, xmlKey, unSubscribeQos.getData());
            //Set oidSet = new HashSet(topicHandlerArr.length);  // for return values (TODO: change to TreeSet to maintain order)
            for (int ii=0; ii<topicHandlerArr.length; ii++) {
               TopicHandler topicHandler = topicHandlerArr[ii];
               if (topicHandler == null) { // unSubscribe on a unknown message ...
                  log.warning("UnSubscribe on unknown topic [" + xmlKey.getOid() + "] is ignored");
                  continue;
               }

               SubscriptionInfo[] subs = topicHandler.findSubscriber(sessionInfo);
               for (int jj=0; jj<subs.length; jj++) {
                  SubscriptionInfo sub = subs[jj];
                  if (sub != null) {
                     fireUnSubscribeEvent(sub);
                     subscriptionIdSet.add(sub.getSubscriptionId());
                  }
                  else
                     log.warning("UnSubscribe of " + topicHandler.getId() + " by session " + sessionInfo.getId() + " failed");
               }
            }

            if (topicHandlerArr.length < 1) {
               log.severe("Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
               throw new XmlBlasterException(glob, ErrorCode.USER_OID_UNKNOWN, ME, "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
            }
         }

         // Build the return values ...
         String[] oidArr = new String[subscriptionIdSet.size()];
         StatusQosData qos = new StatusQosData(glob, MethodName.UNSUBSCRIBE);
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
         String eraseKey = msgQosData.getClientProperty("__eraseKey", updateKey.toXml());
         QueryKeyData key = glob.getQueryKeyFactory().readObject(eraseKey);
         String eraseQos = msgQosData.getClientProperty("__eraseQos", "<qos/>");
         EraseQosServer qos = new EraseQosServer(glob, eraseQos);
         String[] ret = erase(sessionInfo, key, qos, true);
         if (ret != null && ret.length > 0)
            return ret[0];
         else
            return "<qos/>";
      }
      else {
         PublishQosServer qos = new PublishQosServer(glob, msgQosData);
         MsgUnit msgUnit = new MsgUnit(updateKey.getData(), content, qos.getData());
         return publish(sessionInfo, msgUnit, true);
      }
   }

   /**
    * Internal publishing helper.
    */
   public final String publish(SessionInfo sessionInfo, MsgUnit msgUnit) throws XmlBlasterException {
      return publish(sessionInfo, msgUnit, false);
   }

   private final String publish(SessionInfo sessionInfo, MsgUnit msgUnit, boolean isClusterUpdate) throws XmlBlasterException {
      PublishQosServer publishQosServer = new PublishQosServer(glob, msgUnit.getQosData());
      publishQosServer.setClusterUpdate(isClusterUpdate);
      return publish(sessionInfo, msgUnit, publishQosServer);
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
   private final String publish(SessionInfo sessionInfo, MsgUnit msgUnit, PublishQosServer publishQos) throws XmlBlasterException
   {
      try {
         if (msgUnit == null) {
            log.severe("The arguments of method publish() are invalid (null)");
            throw new XmlBlasterException(glob, ErrorCode.USER_ILLEGALARGUMENT, ME, "The arguments of method publish() are invalid (null)");
         }

         MsgKeyData msgKeyData = (MsgKeyData)msgUnit.getKeyData();


         if (log.isLoggable(Level.FINER)) log.finer("Entering " + (publishQos.isClusterUpdate()?"cluster update message ":"") + "publish(oid='" + msgKeyData.getOid() + "', contentMime='" + msgKeyData.getContentMime() + "', contentMimeExtended='" + msgKeyData.getContentMimeExtended() + "' domain='" + msgKeyData.getDomain() + "' from client '" + sessionInfo.getId() + "' ...");
         if (log.isLoggable(Level.FINEST)) log.finest("Receiving " + (publishQos.isClusterUpdate()?"cluster update ":"") + " message in publish()\n" + msgUnit.toXml("",80) + "\n" + publishQos.toXml() + "\nfrom\n" + sessionInfo.toXml());

         PublishReturnQos publishReturnQos = null;

         if (! publishQos.isFromPersistenceStore()) {

            if (publishQos.getSender() == null) // In cluster routing don't overwrite the original sender
               publishQos.setSender(sessionInfo.getSessionName());

            if (!myselfLoginName.getLoginName().equals(sessionInfo.getSessionName().getLoginName())) {
               // TODO: allow for cluster internal messages?
               // TODO: what about different sessions of myselfLoginName?
               int hopCount = publishQos.count(glob.getNodeId());
               if (hopCount > 0) {
                  String text = "Warning, message oid='" + msgKeyData.getOid()
                     + "' passed my node id='" + glob.getId() + "' " + hopCount + " times before, we have a circular routing problem " +
                     " mySelf=" + myselfLoginName.getAbsoluteName() + " sessionName=" +
                     sessionInfo.getSessionName().getAbsoluteName();
                  if (publishQos.isPtp() && publishQos.getDestinationArr().length > 0) {
                     text += ", does the destination cluster node '" + publishQos.getDestinationArr()[0].getDestination() + "' exist?";
                  }
                  log.warning(text);
                  throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CLUSTER_CIRCULARLOOP, ME, text + " Your QoS:" + publishQos.toXml(""));
               }
               int stratum = -1; // not known yet, addRouteInfo() sets my stratum to one closer to the master,
                                 // this needs to be checked here as soon as we know which stratum we are!!!
               publishQos.addRouteInfo(new RouteInfo(glob.getNodeId(), stratum, publishQos.getRcvTimestamp()));
            }
         }

         this.dispatchStatistic.incrNumPublish(1);

         if (msgKeyData.isAdministrative()) {
            if (!glob.supportAdministrative())
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_ADMIN_UNAVAILABLE, ME, "Sorry administrative publish() is not available, try to configure xmlBlaster.");
            return glob.getMomClientGateway().setCommand(sessionInfo, msgKeyData, msgUnit, publishQos, publishQos.isClusterUpdate());
         }

         if (msgKeyData.isRemoteProperties()) {
            sessionInfo.setRemoteProperties(publishQos.getData().getClientProperties());
            I_RemotePropertiesListener[] arr = getRemotePropertiesListenerArr();
            for (int i=0; i<arr.length; i++)
               arr[i].update(sessionInfo, publishQos.getData().getClientProperties());
            return Constants.RET_OK;
         }
         
         // Check if a publish filter is installed and if so invoke it ...
         if (getPublishPluginManager().hasPlugins() && !publishQos.isClusterUpdate()) {
            Map mimePlugins = getPublishPluginManager().findMimePlugins(msgKeyData.getContentMime(),msgKeyData.getContentMimeExtended());
            if (mimePlugins != null) {
               Iterator iterator = mimePlugins.values().iterator();
               while (iterator.hasNext()) {
                  I_PublishFilter plugin = (I_PublishFilter)iterator.next();
                  if (log.isLoggable(Level.FINE)) log.fine("Message " + msgKeyData.getOid() + " is forwarded to publish plugin");
                  String ret = plugin.intercept(sessionInfo.getSubjectInfo(), msgUnit);
                  if (ret == null || ret.length() == 0 || ret.equals(Constants.STATE_OK))
                     continue;
                  else {
                     if (log.isLoggable(Level.FINE)) log.fine("Message " + msgKeyData.getOid() + " is rejected by PublishPlugin");
                     return "<qos><state id='" + ret + "'/></qos>";  // Message is rejected by PublishPlugin
                  }
               }
            }
         }

         // cluster support - forward pubSub message to master ...
         if (this.glob.useCluster()) {
            if (!publishQos.isClusterUpdate()) { // updates from other nodes are arriving here in publish as well
               //if (!glob.getClusterManager().isReady())
               //   glob.getClusterManager().blockUntilReady();
               if (this.glob.isClusterManagerReady()) {
                  if (publishQos.isPtp()) {  // is PtP message
                     Destination[] destinationArr = publishQos.getDestinationArr(); // !!! add XPath client query here !!!
                     for (int ii = 0; ii<destinationArr.length; ii++) {
                        if (log.isLoggable(Level.FINE)) log.fine("Working on PtP message for destination [" + destinationArr[ii].getDestination() + "]");
                        publishReturnQos = forwardPtpPublish(sessionInfo, msgUnit, publishQos.isClusterUpdate(), destinationArr[ii]);
                        if (publishReturnQos != null) {
                           if (destinationArr.length > 1) {
                              // TODO: cluster forwarding with multiple destinations:
                              String txt = "Messages with more than one destinations in a cluster environment is not implemented, only destination '" + destinationArr[ii].toXml() + "' of '" + msgUnit.getLogId() + "' was delivered";
                              log.warning(txt);
                              throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, txt);
                           }
                           return publishReturnQos.toXml();
                        }
                        /*
                        if (publishReturnQos != null) {
                           // Message was forwarded. TODO: How to return multiple publishReturnQos from multiple destinations? !!!
                           BUGGY: We need to take a clone to not remove the destination of the sent message
                           publishQos.removeDestination(destinationArr[ii]);
                        }
                        */
                     }
                     /*
                     if (publishQos.getNumDestinations() == 0) { // we are done, all messages where forwarded
                        return publishReturnQos.toXml();
                     }
                     */
                  }
                  else { // if (publishQos.isSubscribable()) {
                     try {
                        PublishRetQosWrapper ret = glob.getClusterManager().forwardPublish(sessionInfo, msgUnit);
                        //Thread.currentThread().dumpStack();
                        if (ret != null) { // Message was forwarded to master cluster
                           publishReturnQos = ret.getPublishReturnQos();
                           if (ret.getNodeDomainInfo().getDirtyRead() == false) {
                              if (log.isLoggable(Level.FINE)) log.fine("Message " + msgKeyData.getOid() + " forwarded to master " + ret.getNodeDomainInfo().getId() + ", dirtyRead==false nothing more to do");
                              return publishReturnQos.toXml();
                           }
                           // else we publish it locally as well (dirty read!)
                        }
                     }
                     catch (XmlBlasterException e) {
                        if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                           this.glob.setUseCluster(false);
                        }
                        else {
                           e.printStackTrace();
                           throw e;
                        }
                     }
                  }
               }
               else {
                  if (! publishQos.isFromPersistenceStore()) {
                     if (msgKeyData.isInternal()) {
                        if (log.isLoggable(Level.FINE)) log.fine("Cluster manager is not ready, handling message '" + msgKeyData.getOid() + "' locally");
                     }
                     else {
                        log.warning("Cluster manager is not ready, handling message '" + msgKeyData.getOid() + "' locally");
                     }
                  }
               }
            }
         }

         // Handle local message

         // Find or create the topic
         TopicHandler topicHandler = null;
         synchronized(topicHandlerMap) {
            Object obj = topicHandlerMap.get(msgKeyData.getOid());
            if (obj == null) {
               topicHandler = new TopicHandler(this, sessionInfo, msgUnit.getKeyOid()); // adds itself to topicHandlerMap
            }
            else {
               topicHandler = (TopicHandler)obj;
            }
         }

         // Process the message
         publishReturnQos = topicHandler.publish(sessionInfo, msgUnit, publishQos);

         if (publishReturnQos == null) {  // assert only
            StatusQosData qos = new StatusQosData(glob, MethodName.PUBLISH);
            qos.setKeyOid(msgKeyData.getOid());
            qos.setState(Constants.STATE_OK);
            publishReturnQos = new PublishReturnQos(glob, qos);
            publishReturnQos.getData().setRcvTimestamp(publishQos.getRcvTimestamp());
            log.severe("Internal: did not excpect to build a PublishReturnQos, but message '" + msgKeyData.getOid() + "' is processed correctly");
            Thread.dumpStack();
         }

         return publishReturnQos.toXml(); // Use the return value of the cluster master node
      }
      catch (XmlBlasterException e) {
         if (log.isLoggable(Level.FINE)) log.fine("Throwing exception in publish: " + e.toXml()); // Remove again
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
      if (this.glob.isClusterManagerReady()) {
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
    * This helper method checks for a published message which didn't exist before if
    * there are any XPath subscriptions pending which match.
    * <p />
    */
   final void checkExistingSubscriptions(SessionInfo sessionInfo,
                                  TopicHandler topicHandler, PublishQosServer xmlQoS)
                                  throws XmlBlasterException
   {
      if (log.isLoggable(Level.FINER)) log.finer("checkExistingSubscriptions(" + topicHandler.getUniqueKey() + "), should happen only once for each topic.");

      if (topicHandler.hasDomTree()) {  // A topic may suppress XPATH visibility
         XmlKey keyDom = topicHandler.getXmlKey();  // This is DOM parsed already

         if (log.isLoggable(Level.FINE)) log.fine("Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            // for every XPath subscription ...
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               KeyData queryXmlKey = existingQuerySubscription.getKeyData();
               if (!queryXmlKey.isXPath()) { // query: subscription without a given oid
                  log.warning("Only XPath queries are supported, ignoring subscription.");
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
            subscribeToOid((SubscriptionInfo)matchingSubsVec.elementAt(ii), true);    // fires event for subscription
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
         if (log.isLoggable(Level.FINER)) log.finer("Entering " + (isClusterUpdate?"cluster update message ":"") +
                "erase(oid='" + xmlKey.getOid() + "', queryType='" + xmlKey.getQueryType() +
                "', query='" + xmlKey.getQueryString() + "') client '" + sessionInfo.getLoginName() + "' ...");
         if (log.isLoggable(Level.FINEST)) log.finest("Entering " + (isClusterUpdate?"cluster update message ":"") + xmlKey.toXml() + eraseQos.toXml());

         TopicHandler[] topicHandlerArr = queryMatchingTopics(sessionInfo, xmlKey, eraseQos.getData());
         Set oidSet = new HashSet(topicHandlerArr.length);  // for return values (TODO: change to TreeSet to maintain order)
         EraseReturnQos[] clusterRetArr = null;

         for (int ii=0; ii<topicHandlerArr.length; ii++) {
            TopicHandler topicHandler = topicHandlerArr[ii];

            if (this.glob.isClusterManagerReady() && !isClusterUpdate) { // cluster support - forward erase to master
               try {
                  clusterRetArr = glob.getClusterManager().forwardErase(sessionInfo, xmlKey, eraseQos);
                  //Thread.currentThread().dumpStack();
                  //if (ret != null) return ret;
               }
               catch (XmlBlasterException e) {
                  if (e.getErrorCode() == ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED) {
                     this.glob.setUseCluster(false);
                  }
                  else {
                     e.printStackTrace();
                     throw e;
                  }
               }
            }
            if (this.glob.useCluster() && !this.glob.isClusterManagerReady()) {
               log.warning("erase not forwarded to cluster as ClusterManager is not ready");
            }
            
            if (topicHandler == null) { // unSubscribe on a unknown message ...
               if (clusterRetArr != null && clusterRetArr.length > 0) {
                  log.info("Erase for topic [" + xmlKey.getOid() + "] successfully forwarded to cluster master");
                  oidSet.add(xmlKey.getOid());
               }
               else {
                  log.warning("Erase on unknown topic [" + xmlKey.getOid() + "] is ignored");
               }
               // !!! how to delete XPath subscriptions, still MISSING ???
               continue;
            }

            if (log.isLoggable(Level.FINE)) log.fine("erase oid='" + topicHandler.getUniqueKey() + "' of total " + topicHandlerArr.length + " ...");

            //log.info(ME, "Erasing " + topicHandler.toXml());

            oidSet.add(topicHandler.getUniqueKey());
            if (eraseQos.getData().containsHistoryQos()) {
               if (log.isLoggable(Level.FINE)) log.fine("Erasing history instances only, the topic '" + topicHandler.getId() + "' remains");
               topicHandler.eraseFromHistoryQueue(sessionInfo, eraseQos.getData().getHistoryQos());
            }
            else { // erase the complete topic
               try {
                  topicHandler.fireMessageEraseEvent(sessionInfo, xmlKey, eraseQos);
               } catch (XmlBlasterException e) {
                  if (log.isLoggable(Level.FINE)) log.severe("Unexpected exception: " + e.toString());
               }
            }
         }
         //log.info(ME, "AFTER ERASE: " + toXml());

         // Build the return values ...
         String[] oidArr = new String[oidSet.size()];
         //oidSet.toArray(oidArr);
         StatusQosData qos = new StatusQosData(glob, MethodName.ERASE);
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
      if (log.isLoggable(Level.FINE)) log.fine("Login event for client " + sessionInfo.toString());

      this.glob.sendNotification(this, "Client '" + sessionInfo.getSessionName().getAbsoluteName() + "' logged in",
         "clientNew", "java.lang.String", "", sessionInfo.getSessionName().getAbsoluteName());

      if (this.publishLoginEvent) {
         this.publishQosLoginEvent.clearRoutes();
         MsgQosData msgQosData = (MsgQosData)this.publishQosLoginEvent.getData().clone();
         // __sessionId is deprecated, please use __publicSessionId
         msgQosData.addClientProperty("__sessionId", sessionInfo.getPublicSessionId());
         msgQosData.addClientProperty("__publicSessionId", sessionInfo.getPublicSessionId());
         msgQosData.addClientProperty("__absoluteName", sessionInfo.getSessionName().getAbsoluteName());
         
         MsgUnit msgUnit = new MsgUnit(this.xmlKeyLoginEvent,
                                  sessionInfo.getLoginName().getBytes(),
                                  msgQosData);
         publish(this.unsecureSessionInfo, msgUnit); // publish that this client has logged in
         this.publishQosLoginEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
      }

      if (log.isLoggable(Level.FINE)) log.fine(" client added:"+sessionInfo.getLoginName());
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

      this.glob.sendNotification(this, "Client '" + sessionInfo.getSessionName().getAbsoluteName() + "' logged out",
         "clientRemoved", "java.lang.String", sessionInfo.getSessionName().getAbsoluteName(), "");

      if (this.publishLogoutEvent) {
         if (log.isLoggable(Level.FINE)) log.fine("Logout event for client " + sessionInfo.toString());
         this.publishQosLogoutEvent.clearRoutes();
         
         MsgQosData msgQosData = (MsgQosData)this.publishQosLogoutEvent.getData().clone();
         // __sessionId is deprecated, please use __publicSessionId
         msgQosData.addClientProperty("__sessionId", sessionInfo.getPublicSessionId());
         msgQosData.addClientProperty("__publicSessionId", sessionInfo.getPublicSessionId());
         msgQosData.addClientProperty("__absoluteName", sessionInfo.getSessionName().getAbsoluteName());
         
         MsgUnit msgUnit = new MsgUnit(this.xmlKeyLogoutEvent, sessionInfo.getLoginName().getBytes(), msgQosData);
         publish(this.unsecureSessionInfo, msgUnit); // publish that this client logged out
         this.publishQosLogoutEvent.getData().setTopicProperty(null); // only the first publish needs to configure the topic
      }

      if (log.isLoggable(Level.FINE)) log.fine("Client session '" + sessionInfo.getId() + "' removed");
   }

   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {
   }
    
   /**
    * Event invoked on new created SubjectInfo.
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException
   {
      log.warning("Ignoring SubjectInfo added event for client " + e.getSubjectInfo().toString());
   }


   /**
    * Event invoked on deleted SubjectInfo.
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException
   {
      log.warning("Ignoring SubjectInfo removed event for client " + e.getSubjectInfo().toString());
   }
   
   /**
    * Add listener if new remote properties arrive. 
    * Clients which publish client side properties to their sessionInfo
    * @param RemotePropertiesListener
    * @return
    */
   public synchronized boolean addRemotePropertiesListener(I_RemotePropertiesListener remotePropertiesListener) {
      return this.remotePropertiesListeners.add(remotePropertiesListener);
   }

   /**
    * Remove the given listener
    * @param RemotePropertiesListener
    * @return true if it was removed
    */
   public synchronized boolean removeRemotePropertiesListener(I_RemotePropertiesListener remotePropertiesListener) {
      return this.remotePropertiesListeners.remove(remotePropertiesListener);
   }
   
   
   public synchronized I_RemotePropertiesListener[] getRemotePropertiesListenerArr() {
      return (I_RemotePropertiesListener[])this.remotePropertiesListeners.toArray(new I_RemotePropertiesListener[this.remotePropertiesListeners.size()]);
   }

   /**
    * Adds the specified Topic listener to receive creation/destruction events of Topics. 
    */
   public void addTopicListener(I_TopicListener l) {
      if (l == null) {
         throw new IllegalArgumentException(ME + ".addTopicListener: the listener is null");
      }
      synchronized (this.topicListenerSet) {
         this.topicListenerSet.add(l);
      }
   }

   /**
    * Removes the specified listener.
    */
   public void removeTopicListener(I_TopicListener l) {
      if (l == null) {
         throw new IllegalArgumentException(ME + ".removeTopicListener: the listener is null");
      }
      synchronized (this.topicListenerSet) {
         this.topicListenerSet.remove(l);
      }
   }

   /**
    * Is fired on topic creation or destruction. 
    */
   private final void fireTopicEvent(TopicHandler topicHandler)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Going to fire fireTopicEvent() ...");

      synchronized (this.topicListenerSet) {
         Iterator it = this.topicListenerSet.iterator();
         while (it.hasNext()) {
            try {
               I_TopicListener l = (I_TopicListener)it.next();
               TopicEvent event = new TopicEvent(topicHandler);
               l.changed(event);
            }
            catch (Throwable e) {
               e.printStackTrace();
            }
         }
      }
   }

   /**
    * Adds the specified subscription listener to receive subscribe/unSubscribe events.
    */
   public void addSubscriptionListener(I_SubscriptionListener l) {

      if (l == null) {
         throw new IllegalArgumentException(ME + ".addSubscriptionListener: the listener is null");
      }
      if ( l.getPriority() == null) {
         throw new IllegalArgumentException(ME + ".addSubscriptionListener: the priority of the listener is null");
      }
      synchronized (subscriptionListenerMap) {
         Integer prio = l.getPriority();
         I_SubscriptionListener oldListener = (I_SubscriptionListener)subscriptionListenerMap.get(prio);
         if (oldListener != null) {
            if (oldListener.equals(l))
               return;
            throw new IllegalArgumentException(ME + "addSubscriptionListener: a different listener was already registered with priority '" + prio.intValue() + "'");
         }
         subscriptionListenerMap.put(prio, l);
      }
   }

   /**
    * Removes the specified listener.
    */
   public void removeSubscriptionListener(I_SubscriptionListener l) {
      if (l == null) {
         throw new IllegalArgumentException(ME + ".removeSubscriptionListener: the listener is null");
      }
      if ( l.getPriority() == null) {
         throw new IllegalArgumentException(ME + ".removeSubscriptionListener: the priority of the listener is null");
      }
      synchronized (subscriptionListenerMap) {
         subscriptionListenerMap.remove(l.getPriority());
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
      if (log.isLoggable(Level.FINE)) log.fine("Going to fire fireSubscriptionEvent(" + subscribe + ") ...");

      synchronized (subscriptionListenerMap) {
         if (subscriptionListenerMap.size() == 0)
            return;

         SubscriptionEvent event = new SubscriptionEvent(subscriptionInfo);

         Integer[] keys = (Integer[])subscriptionListenerMap.keySet().toArray(new Integer[subscriptionListenerMap.size()]);
         if (subscribe) {
            for (int i=0; i < keys.length; i++) {
               I_SubscriptionListener subli = (I_SubscriptionListener)subscriptionListenerMap.get(keys[i]);
               if (subli != null)
                  subli.subscriptionAdd(event);
            }
         }
         else {
            for (int i=keys.length-1; i >= 0; i--) {
               I_SubscriptionListener subli = (I_SubscriptionListener)subscriptionListenerMap.get(keys[i]);
               if (subli != null)
                  subli.subscriptionRemove(event);
            }
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
      if (this.topicStore != null) {
         sb.append(this.topicStore.toXml(extraOffset+Constants.INDENT));
      }
      for (int ii=0; ii<topicHandlerArr.length; ii++) {
         sb.append(topicHandlerArr[ii].toXml(extraOffset+Constants.INDENT));
      }
      sb.append(bigXmlKeyDOM.toXml(extraOffset+Constants.INDENT, true));
      sb.append(clientSubscriptions.toXml(extraOffset+Constants.INDENT));
      if (this.glob.isClusterManagerReady()) {
         sb.append(glob.getClusterManager().toXml(extraOffset+Constants.INDENT));
      }
      sb.append(offset).append("</RequestBroker>");

      return sb.toString();
   }

   //====== These methods satisfy the I_AdminNode administration interface ======
   public int getNumNodes() {
      if (!glob.isClusterManagerReady()) return 1;
      try {
         return glob.getClusterManager().getNumNodes();
      } catch(XmlBlasterException e) {
         return 1;
      }
   }
   public String getNodeList() {
      if (!glob.isClusterManagerReady()) return glob.getId();
      try {
         return glob.getClusterManager().getNodeList();
      } catch(XmlBlasterException e) {
         return glob.getId();
      }
   }
   public String[] getNodes() {
      if (!glob.isClusterManagerReady()) return new String[] { glob.getId() };
      try {
         return glob.getClusterManager().getNodes();
      } catch(XmlBlasterException e) {
         return new String[] { glob.getId() };
      }
   }
   public String getNodeId() {
      return glob.getId();
   }
   public String getInstanceId() {
      return glob.getInstanceId();
   }
   public String getVersion() {
      return glob.getVersion();
   }
   public String getRevisionNumber() {
      return glob.getRevisionNumber();
   }
   public long getServerTimestampMillis() {
      return System.currentTimeMillis();
   }
   public String getServerTimestamp() {
      java.sql.Timestamp tt = new java.sql.Timestamp(System.currentTimeMillis());
      return tt.toString();
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
   public String dump() throws XmlBlasterException {
      return glob.getDump();
   }
   public void setDump(String fn) throws XmlBlasterException{
      FileLocator.writeFile(fn, glob.getDump());
      log.info("Dumped internal state to " + fn);
   }
   public String getRunlevel() {
      return ""+glob.getRunlevelManager().getCurrentRunlevel();
   }
   public void setRunlevel(String levelStr) throws XmlBlasterException {
      glob.getRunlevelManager().changeRunlevel(levelStr, true);
   }
   /** Get date when xmlBlaster was started. */
   public String getStartupDate() {
      long ll = this.startupTime;
      java.sql.Timestamp tt = new java.sql.Timestamp(ll);
      return tt.toString();
   }
   /** How long is the server running (in seconds) */
   public long getUptime() {
      return (System.currentTimeMillis() - this.startupTime)/1000L;
   }
   /** Access the last logged error */
   public String getLastWarning() {
      return this.lastWarning;
   }
   public void clearLastWarning() {
      this.lastWarning = "";
   }
   /** Access the last logged error */
   public String getLastError() {
      return this.lastError;
   }
   public void clearLastError() {
      this.lastError = "";
   }
   /** Memory in bytes */
   public long getFreeMem() {
      return Runtime.getRuntime().freeMemory();
   }
   public String getFreeMemStr() {
      return Global.byteString(getFreeMem());
   }
   /** Free memory in bytes */
   public long getMaxFreeMem() {
      return Global.heapMemoryUsage-getUsedMem();
   }
   public String getMaxFreeMemStr() {
      return Global.byteString(getMaxFreeMem());
   }
/*   public void setFreeMem(long freeMem) throws XmlBlasterException {
      throw new XmlBlasterException(ME, "Setting of property 'freeMem' is not supported");
   } */
   public long getTotalMem() {
      return Runtime.getRuntime().totalMemory();
   }
   public String getTotalMemStr() {
      return Global.byteString(getTotalMem());
   }
   public long getMaxMem() {
      return Runtime.getRuntime().maxMemory();
   }
   public String getMaxMemStr() {
      return Global.byteString(getMaxMem());
   }
   public long getUsedMem() {
      return (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
   }
   public String getUsedMemStr() {
      return Global.byteString(getUsedMem());
   }
   public String getGc() {
      if (log.isLoggable(Level.FINE)) log.fine("Garbage collector is activated");
      System.gc();
      return "OK";
   }
   public void setGc(String dummy) {
      if (log.isLoggable(Level.FINE)) log.fine("Garbage collector is activated");
      System.gc();
   }

   public void exit() throws XmlBlasterException {
      setExit("0");
   }
   public void setExit(String exitValue) throws XmlBlasterException {
      int val = 0;
      try { val = Integer.parseInt(exitValue.trim()); } catch(NumberFormatException e) { log.severe("Invalid exit value=" + exitValue + ", expected an integer"); };
      final int exitVal = val;

      if (glob.isEmbedded()) {
         log.warning("Ignoring exit(" + exitVal + ") request in embeded mode ('xmlBlaster.isEmbeded' is set true).");
         return;
      }

      final long exitSleep = glob.getProperty().get("xmlBlaster.exit.delay", 2000L);
      Timeout exitTimeout = new Timeout("XmlBlaster ExitTimer");
      exitTimeout.addTimeoutListener(new I_Timeout() {
            public void timeout(Object userData) {
               log.info("Administrative exit(" + exitVal + ") after exit-timeout of " + exitSleep + " millis.");
               try {
                  glob.getRunlevelManager().changeRunlevel(RunlevelManager.RUNLEVEL_HALTED, true);
               }
               catch(Throwable e) {
                  log.warning("Administrative exit(" + exitVal + ") problems: " + e.toString());
               }
               System.exit(exitVal);
            }
         },
         exitSleep, null);
      log.info("Administrative exit request, scheduled exit in " + exitSleep + " millis with exit value=" + exitVal + ".");
   }

   public String getHostname() {
      return glob.getBootstrapAddress().getBootstrapHostname();
   }
   /** The bootstrap bootstrap port */
   public int getPort() {
      return glob.getBootstrapAddress().getBootstrapPort();
   }
   /** The number of different users, the sessions may be higher */
   public int getNumClients() {
      return authenticate.getNumSubjects();
   }
   public int getMaxClients() {
      return authenticate.getMaxSubjects();
   }
   public String[] getAliveCallbackClients() {
      SessionInfo[] arr = this.authenticate.getSessionInfoArr();
      if (arr == null || arr.length == 0) return new String[0];
      ArrayList list = new ArrayList(arr.length);
      for (int i=0; i<arr.length; i++) {
         SessionInfo info = arr[i];
         org.xmlBlaster.util.dispatch.DispatchManager manager = info.getDispatchManager();
         if (manager != null && manager.getDispatchConnectionsHandler().isAlive()) {
            list.add(info.getSessionName().getAbsoluteName());
         }
      }
      return (String[])list.toArray(new String[list.size()]);
   }
   /** These are the login names returned, every client may be logged in multiple times
       which you can't see here */
   public String getClientList() {
      return authenticate.getSubjectList();
   }
   public String[] getClients() {
      return authenticate.getSubjects();
   }
   public int getNumSysprop() {
      return glob.getProperty().getProperties().size();
   }
   /** @deprecated Is not supported anymore */
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
   public int getNumTopics() {
      synchronized (this.topicHandlerMap) {
         return this.topicHandlerMap.size();
      }
   }
   public String getTopicList() {
      TopicHandler[] topicHandlerArr = getTopicHandlerArr();
      StringBuffer sb = new StringBuffer(topicHandlerArr.length*60);
      for(int ii=0; ii<topicHandlerArr.length; ii++) {
         if (sb.length() > 0)
            sb.append(",");
         sb.append(topicHandlerArr[ii].getUniqueKey());
      }
      return sb.toString();
   }
   public String[] getTopics() {
      TopicHandler[] topicHandlerArr = getTopicHandlerArr();
      if (topicHandlerArr == null || topicHandlerArr.length == 0)
         return new String[0];
      String[] ret = new String[topicHandlerArr.length];
      for (int i=0; i<topicHandlerArr.length; i++) {
         ret[i] = topicHandlerArr[i].getUniqueKey();
      }
      return ret;
   }
   public int getNumSubscriptions() {
      return getClientSubscriptions().getNumSubscriptions();
   }
   public String getSubscriptionList() {
      return getClientSubscriptions().getSubscriptionList();
   }
   public String[] getSubscriptions() {
      return getClientSubscriptions().getSubscriptions();
   }
   public String displayClassInfo(String clazzName) {
      if (clazzName == null || clazzName.trim().length() < 1) {
         throw new IllegalArgumentException("Please pass a full qualified java class name to displayClassInfo()");
      }
      try {
         Class clazz = java.lang.Class.forName(clazzName);
         StringBuffer results = new StringBuffer();
         org.xmlBlaster.util.classloader.ClassLoaderUtils.displayClassInfo(clazz, results);
         return results.toString();
      }
      catch (ClassNotFoundException e) {
         return "Class '" + clazzName + "' not found in '" + System.getProperty("java.class.path") + "'";
      }
   }
   
   /** JMX */
   public java.lang.String usage() {
      return ServerScope.getJmxUsageLinkInfo(this.getClass().getName(), null);
   }
   /** JMX */
   public java.lang.String getUsageUrl() {
      return ServerScope.getJavadocUrl(this.getClass().getName(), null);
   }
   /* JMX dummy to have a copy/paste functionality in jconsole */
   public void setUsageUrl(java.lang.String url) {}

   /**
    * Redirect logging, configure in xmlBlaster.properties. 
    * Enforced by interface LogableDevice
    */
   public void log(LogRecord record) {
      // We may not do any log.xxx() call here because of recursion!!
      String newLog = "[" + record.getSourceClassName() + "." + record.getSourceMethodName() + "] " + record.getMessage();

      // Remember error text
      if (Level.WARNING.intValue() == record.getLevel().intValue()) {
         this.lastWarning = newLog;
      }
      else if (Level.SEVERE.intValue() == record.getLevel().intValue()) {
         // Emit JMX notification
         this.glob.sendNotification(this, "New " + record.getLevel().toString() + " logging occurred",
            "lastError", "java.lang.String", this.lastError, newLog);
         this.lastError = newLog;
      }
   }
   
   /**
    * Declare available notification event types. 
    */
   public MBeanNotificationInfo[] getNotificationInfo() {
      String[] types = new String[] {
         AttributeChangeNotification.ATTRIBUTE_CHANGE
      };

      String name = AttributeChangeNotification.class.getName();
      MBeanNotificationInfo loggingEventInfo =
         new MBeanNotificationInfo(types, name, "Error logging events of cluster node '" + this.glob.getId() + "'");
      MBeanNotificationInfo loginEventInfo =
         new MBeanNotificationInfo(types, name, "Client login events of cluster node '" + this.glob.getId() + "'");
      MBeanNotificationInfo logoutEventInfo =
         new MBeanNotificationInfo(types, name, "Client logout events of cluster node '" + this.glob.getId() + "'");

      return new MBeanNotificationInfo[] {loggingEventInfo, loginEventInfo, logoutEventInfo};
   }

   /**
    * @return Returns the number of get() invocations
    */
   public long getNumGet() {
      return this.dispatchStatistic.getNumGet();
   }

   /**
    * @return Returns the number if publish() invocations
    */
   public long getNumPublish() {
      return this.dispatchStatistic.getNumPublish();
   }

   public long getNumUpdate() {
      SessionInfo[] arr = getAuthenticate().getSessionInfoArr();
      long numUpdate = 0;
      for (int i=0; i<arr.length; i++)
         numUpdate += arr[i].getDispatchStatistic().getNumUpdate();
      return numUpdate;
   }
}
