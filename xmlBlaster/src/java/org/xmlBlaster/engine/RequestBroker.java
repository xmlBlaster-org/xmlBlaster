/*------------------------------------------------------------------------------
Name:      RequestBroker.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;

import org.jutils.log.LogChannel;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlQoSBase;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.engine.xml2java.*;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.queue.MsgQueue;
import org.xmlBlaster.engine.queue.MsgQueueEntry;
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
import org.xmlBlaster.engine.callback.CbWorkerPool;
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
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
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
   private final String myselfLoginName; // "__RequestBroker_internal[heron]";
   public final static String internalLoginNamePraefix = "__RequestBroker_internal";

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
   private MessageUnitWrapper msgUnitLoginEvent = null;

   /** Initialize a messageUnit for a logout event */
   private MessageUnitWrapper msgUnitLogoutEvent = null;

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

      myselfLoginName = internalLoginNamePraefix + "[" + glob.getId() + "]";
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
         this.ME = "RequestBroker" + glob.getLogPraefixDashed();
      }

      accessPluginManager = new AccessPluginManager(glob);

      publishPluginManager = new PublishPluginManager(glob);

      pluginManager = new PersistencePluginManager(glob);

      this.loggedIn = new Hashtable();
      this.clientSubscriptions = new ClientSubscriptions(glob, this, authenticate);

      {
         // Key '__sys__Login' for login event (allows you to subscribe on new clients which do a login)
         // We store all necessary data in a MessageUnitWrapper, to reuse the static and already parsed data ...
         String xmlKeyLoginEvent = "<key oid='__sys__Login' contentMime='text/plain'>\n</key>";
         String publishQosLoginEvent = "<qos>\n   <forceUpdate/>\n</qos>";
         XmlKey key = new XmlKey(glob, xmlKeyLoginEvent, true);
         PublishQos qos = new PublishQos(glob, publishQosLoginEvent);
         qos.setRemainingLife(0L);
         this.msgUnitLoginEvent = new MessageUnitWrapper(glob, this, key, new MessageUnit(xmlKeyLoginEvent, new byte[0], publishQosLoginEvent), qos);
      }

      {
         // Key '__sys__Logout' for logout event (allows you to subscribe on clients which do a logout)
         // We store all necessary data in a MessageUnitWrapper, to reuse the static and already parsed data ...
         String xmlKeyLogoutEvent = "<key oid='__sys__Logout' contentMime='text/plain'>\n</key>";
         String publishQosLogoutEvent = "<qos>\n   <forceUpdate/>\n</qos>";
         XmlKey key = new XmlKey(glob, xmlKeyLogoutEvent, true);
         PublishQos qos = new PublishQos(glob, publishQosLogoutEvent);
         qos.setRemainingLife(0L);
         this.msgUnitLogoutEvent = new MessageUnitWrapper(glob, this, key, new MessageUnit(xmlKeyLogoutEvent, new byte[0], publishQosLogoutEvent), qos);
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
    *   &lt;key oid='__sys__deadLetter'>
    *      &lt;oid>aMessage&lt;/oid>
    *   &lt;key>
    * </pre>
    * @param entries The message to send as dead letters
    * @param reason A human readable text describing the problem
    * @return State information returned from the publish call (is never null)
    */
   public String[] deadLetter(MsgQueueEntry[] entries, String reason)
   {
      if (log.CALL) log.call(ME, "Publishing " + entries.length + " dead letters.");
      if (entries == null) {
         log.error(ME, "deadLetter() with null argument");
         return new String[0];
      }

      try {
         if (log.TRACE) log.trace(ME, "Publishing " + entries.length + " volatile dead letters");
         String[] retArr = new String[entries.length];
         PublishQosWrapper pubQos = new PublishQosWrapper();
         pubQos.isVolatile(true);
         for (int ii=0; ii<entries.length; ii++) {
            MsgQueueEntry entry = entries[ii];
            MessageUnit msgUnit = entry.getMessageUnitWrapper().getMessageUnitClone();
            try {
               if (entry.getMessageUnitWrapper().getXmlKey().isDeadLetter()) {  // Check for recursion of dead letters
                  log.error(ME, "PANIC: Recursive dead letter is lost, no recovery possible - dumping to file not yet coded: " +
                                msgUnit.toXml() + ": " +
                                ((reason != null) ? (": " + reason) : "") );
                  retArr[ii] = entry.getMessageUnitWrapper().getXmlKey().getUniqueKey();
                  continue;
               }

               log.warn(ME, "Generating dead letter oid=" + entry.getMessageUnitWrapper().getUniqueKey() +
                            " from publisher=" + entry.getPublisherName() +
                            " because delivery to '" + entry.getReceiverName() +
                            "' cbQueue=" + ((entry.getMsgQueue() == null) ? "null" : entry.getMsgQueue().getName()) + " failed" +
                            ((reason != null) ? (": " + reason) : "") );
               StringBuffer buf = new StringBuffer(256);
               buf.append("<key oid='").append(Constants.OID_DEAD_LETTER).append("'><oid>").append(entry.getMessageUnitWrapper().getUniqueKey()).append("</oid></key>");
               msgUnit.setKey(buf.toString());
               msgUnit.setQos(pubQos.toXml());
               XmlKey xmlKey = new XmlKey(glob, msgUnit.getXmlKey(), true);
               retArr[ii] = publish(unsecureSessionInfo, xmlKey, msgUnit, new PublishQos(glob, msgUnit.getQos()));
            }
            catch(Throwable e) {
               log.error(ME, "PANIC: " + entry.getMessageUnitWrapper().getUniqueKey() + " dead letter is lost, no recovery possible - dumping to file not yet coded: " + e.toString() + "\n" + msgUnit.toXml());
               e.printStackTrace();
               retArr[ii] = entry.getMessageUnitWrapper().getXmlKey().getUniqueKey();
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
               // Fetch the MessageUnit by oid from the persistence
               MessageUnit msgUnit = persistenceDriver.fetch(oid);

               PublishQos publishQos = new PublishQos(glob, msgUnit.getQos());

               // PublishQos flag: 'fromPersistenceStore' must be true
               publishQos.setFromPersistenceStore(true);

               XmlKey xmlKey = new XmlKey(glob, msgUnit.getXmlKey(), true);

               // RequestBroker publishes messages self
               this.publish(unsecureSessionInfo, xmlKey, msgUnit, publishQos);

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
    * Invoked by a client, to subscribe to one/many MessageUnit.
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
   String subscribe(SessionInfo sessionInfo, XmlKey xmlKey, SubscribeQoS subscribeQos) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering subscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "', domain='" + xmlKey.getDomain() + "') from client '" + sessionInfo.getLoginName() + "' ...");

         CbQueueProperty[] props = subscribeQos.getQueueProperties();
         if (props.length > 1) {         // WE NEED TO RETURN A STRING[] (currently wer return a simple String!!
            log.warn(ME, "subscribe for more than one queue is currently not supported");
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
               msgQueue = new MsgQueue("unrelated:"+props[ii].getCallbackAddresses()[0], new CbQueueProperty(glob, Constants.RELATING_UNRELATED, null), glob);

            SubscriptionInfo subsQuery = null;
            if (xmlKey.isQuery()) { // fires event for query subscription, this needs to be remembered for a match check of future published messages
               subsQuery = new SubscriptionInfo(glob, sessionInfo, msgQueue, xmlKey, subscribeQos);
               returnOid[ii] = subsQuery.getUniqueKey(); // XPath query
               fireSubscribeEvent(subsQuery);
            }

            Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, subscribeQos);

            for (int jj=0; jj<xmlKeyVec.size(); jj++) {
               XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(jj);
               if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
                  xmlKeyExact = xmlKey;
               SubscriptionInfo subs = null;
               if (sessionInfo.getConnectQos().duplicateUpdates() == false) {
                  Vector vec =  clientSubscriptions.getSubscriptionByOid(sessionInfo, xmlKeyExact.getUniqueKey(), true);
                  if (vec != null) {
                     if (vec.size() > 0) {
                        subs = (SubscriptionInfo)vec.firstElement();
                        if (log.TRACE) log.trace(ME, "Session '" + sessionInfo.getId() + "', message '" + xmlKeyExact.getUniqueKey() + "' is subscribed " + vec.size() + " times with duplicateUpdates==false");
                     }
                     if (vec.size() > 1)
                        log.error(ME, "Internal problem for session '" + sessionInfo.getId() + "', message '" + xmlKeyExact.getUniqueKey() + "' is subscribed " + vec.size() + " times but duplicateUpdates==false!");
                  }
               }

               if (subs == null) {
                  if (subsQuery != null) {
                     subs = new SubscriptionInfo(glob, sessionInfo, subsQuery, xmlKeyExact);
                     subsQuery.addSubscription(subs);
                  }
                  else
                     subs = new SubscriptionInfo(glob, sessionInfo, msgQueue, xmlKeyExact, subscribeQos);
               }

               subscribeToOid(subs);                // fires event for subscription

               if (returnOid[ii].equals("")) returnOid[ii] = subs.getUniqueKey();
            }
         }

         if (useCluster) { // cluster support - forward message to master
            try {
               SubscribeRetQos ret = glob.getClusterManager().forwardSubscribe(sessionInfo, xmlKey, subscribeQos);
               //Thread.currentThread().dumpStack();
               //if (ret != null) return ret.toXml();
            }
            catch (XmlBlasterException e) {
               if (e.id.equals("ClusterManager.PluginFailed")) {
                  useCluster = false;
               }
               else {
                  e.printStackTrace();
                  throw e;
               }
            }
         }

         StringBuffer sb = new StringBuffer(160);
         sb.append("<qos><state id='").append(Constants.STATE_OK).append("'/><subscribe id='").append(returnOid[0]).append("'/></qos>");
         return sb.toString();  //!!! Only index 0 supported
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
    * @return A sequence of 0 - n MessageUnit structs. 0 if no message matched.
    * @exception XmlBlasterException on internal errors
    * @see org.xmlBlaster.client.GetQosWrapper
    */
   public MessageUnit[] get(SessionInfo sessionInfo, XmlKey xmlKey, GetQoS qos) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering get(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

         if (glob.isAdministrationCommand(xmlKey)) {
            return glob.getMomClientGateway().getCommand(sessionInfo, xmlKey.getUniqueKey());
         }

         if (xmlKey.getKeyOid().equals(Constants.JDBC_OID/*"__sys__jdbc"*/)) { // Query RDBMS !!! hack, we need a general service interface
            String query = xmlKey.literal();
            // Extract the query from the xmlkey - this is a bad hack - we need a function to extract user tags in <key>...</key>!
            int start = query.indexOf(">")+1;
            int end = query.lastIndexOf("<");
            if (start<0 || end <0 || start >= end) {
               log.warn(ME, "The JDBC query is invalid '" + query + "'");
               throw new XmlBlasterException(ME, "Your JDBC query is invalid");
            }
            String content = query.substring(start, end);
            org.xmlBlaster.protocol.jdbc.XmlDBAdapter adap = new org.xmlBlaster.protocol.jdbc.XmlDBAdapter(glob, 
                        content.getBytes(), (org.xmlBlaster.protocol.jdbc.NamedConnectionPool)this.glob.getObjectEntry("NamedConnectionPool-"+glob.getId()));
            return adap.query();
         }

         Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, qos);
         Vector msgUnitVec = new Vector(xmlKeyVec.size());

         NEXT_MSG: for (int ii=0; ii<xmlKeyVec.size(); ii++) {
            XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
            if (xmlKeyExact == null && xmlKey.isExact()) // subscription on a yet unknown message ...
               xmlKeyExact = xmlKey;

            MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());

            if( msgUnitHandler == null ) {

               if (useCluster) { // cluster support - forward erase to master
                  try {
                     MessageUnit tmp[] = glob.getClusterManager().forwardGet(sessionInfo, xmlKey, qos);
                     if (tmp != null && tmp.length > 0) {
                        log.info(ME, "get() access of " + tmp.length + " messages from cluster master");
                        for (int jj=0; jj<tmp.length; jj++) {
                           msgUnitVec.addElement(tmp[jj]);
                           // We currently don' cache the message here in the slave !!!
                           // We could do it with the xmlBlasterConnection.initCache(int size)
                        }
                        continue NEXT_MSG;
                     }
                  }
                  catch (XmlBlasterException e) {
                     if (e.id.equals("ClusterManager.PluginFailed")) {
                        useCluster = false;
                     }
                     else {
                        e.printStackTrace();
                        throw e;
                     }
                  }
               }

               log.warn(ME, "get(): The key '"+xmlKeyExact.getUniqueKey()+"' is not available.");
               continue NEXT_MSG;
               //throw new  XmlBlasterException(ME+".UnavailableKey", "The key '"+xmlKeyExact.getUniqueKey()+"' is not available.");
            }

            if (msgUnitHandler.isPublishedWithData()) {

               MessageUnitWrapper msgUnitWrapper = msgUnitHandler.getMessageUnitWrapper();

               AccessFilterQos[] filterQos = qos.getFilterQos();
               if (filterQos != null) {
                  if (log.TRACE) log.trace(ME, "Checking " + filterQos.length + " filters");
                  for (int jj=0; jj<filterQos.length; jj++) {
                     XmlKey key = msgUnitHandler.getXmlKey(); // This key is DOM parsed
                     I_AccessFilter filter = getAccessPluginManager().getAccessFilter(
                                                  filterQos[jj].getType(),
                                                  filterQos[jj].getVersion(), 
                                                  xmlKey.getContentMime(),
                                                  xmlKey.getContentMimeExtended());
                     if (log.TRACE) log.trace(ME, "get("+xmlKeyExact.getUniqueKey()+") filter=" + filter + " qos=" + qos.toXml());
                     if (filter != null && filter.match(sessionInfo,
                                                  sessionInfo,
                                                  msgUnitWrapper, filterQos[jj].getQuery()) == false)
                        continue NEXT_MSG; // filtered message is not send to client
                  }
               }

               MessageUnit mm = msgUnitHandler.getMessageUnit().getClone();

               // Check with MsgQueueEntry.getUpdateQos() !!!
               StringBuffer buf = new StringBuffer();
               buf.append("\n<qos>\n");
               buf.append("   <state id='").append(Constants.STATE_OK).append("'/>\n");    // OK | TIMEOUT | ERASED
               buf.append("   <sender>").append(msgUnitWrapper.getPublisherName()).append("</sender>\n");
               buf.append("   ").append(msgUnitWrapper.getXmlRcvTimestamp()).append("\n");

               if (msgUnitWrapper.getPublishQos().getRemainingLife() >= 0L) {
                  buf.append("   <expiration remainingLife='").append(msgUnitWrapper.getPublishQos().getRemainingLife()).append("'/>\n");
               }
               buf.append("   <route>\n"); // server internal added routing informations
               buf.append("      <node id='").append(glob.getId()).append("'/>");
               buf.append("   </route>\n"); // server internal added routing informations

               buf.append("</qos>");
               mm.qos = buf.toString(); // !!! GetReturnQos should be an object
               msgUnitVec.addElement(mm);
            }
         }

         MessageUnit[] msgUnitArr = new MessageUnit[msgUnitVec.size()];
         for (int ii=0; ii<msgUnitArr.length; ii++)
            msgUnitArr[ii] = (MessageUnit)msgUnitVec.elementAt(ii);

         getMessages += msgUnitArr.length;
         if (log.TRACE) log.trace(ME, "Returning for get() " + msgUnitArr.length + " messages");
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
      if (log.TRACE) log.trace(ME, "Refreshed internal state for '" + oid + "'");
   }

   /**
    * Internal publishing helper.
    */
   private String[] publish(SessionInfo sessionInfo, MessageUnit[] msgUnitArr) throws XmlBlasterException
   {
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<msgUnitArr.length; ii++) {
         retArr[ii] = publish(sessionInfo, new XmlKey(glob, msgUnitArr[ii].xmlKey, true), msgUnitArr[ii], new PublishQos(glob, msgUnitArr[ii].qos));
      }
      return retArr;
   }

   /**
    * Internal publishing helper.
    */
   private String publish(SessionInfo sessionInfo, MessageUnit msgUnit) throws XmlBlasterException
   {
      return publish(sessionInfo, new XmlKey(glob, msgUnit.xmlKey, true), msgUnit, new PublishQos(glob, msgUnit.qos));
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
         if (log.TRACE) log.trace(ME, "Access Client " + clientName + " with EXACT oid='" + xmlKey.getUniqueKey() + "'");
         XmlKey xmlKeyExact = getXmlKeyFromOid(xmlKey.getUniqueKey());
         xmlKeyVec = new Vector();
         /* if (xmlKeyExact != null) */
         xmlKeyVec.addElement(xmlKeyExact); // if (xmlKeyExact == null) add nevertheless!
      }

      else {
         log.warn(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
         throw new XmlBlasterException(ME + ".UnsupportedQueryType", "Sorry, can't access, query snytax is unknown: " + xmlKey.getQueryType());
      }

      if (log.TRACE) log.trace(ME, "Found " + ((xmlKeyVec == null) ? 0 : xmlKeyVec.size()) + " matching subscriptions");

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
    * Find the MessageUnitHandler, note that for subscriptions
    * where never a message arrived this method will return null. 
    * 
    * Use ClientSubscriptions.getSubscriptionByOid() to find those as well.
    *
    * @param oid  This is the XmlKey:uniqueKey
    * @return null if not found
    */
   final MessageUnitHandler getMessageHandlerFromOid(String oid)
   {
      synchronized(messageContainerMap) {
         Object obj = messageContainerMap.get(oid);
         if (obj == null) {
            if (log.TRACE) log.trace(ME, "getMessageHandlerFromOid(): key oid " + oid + " is unknown, msgUnitHandler == null");
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
      if (log.TRACE) log.trace(ME, "Entering subscribeToOid() ...");
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

      subs.incrSubscribeCounter();

      // Now the MessageUnit exists, subscribe to it -> fires update to client
      msgUnitHandler.addSubscriber(subs);

      fireSubscribeEvent(subs);  // inform all listeners about this new subscription
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
         if (log.CALL) log.call(ME, "Entering unSubscribe(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() + "', query='" + xmlKey.getQueryString() + "') ...");

         String id = xmlKey.getUniqueKey();

         if (SubscriptionInfo.isSubscribeId(id)) {
            SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getUniqueKey());
            if (subs != null) {
               Vector childs = subs.getChildrenSubscriptions();
               if (childs != null) {
                  if (log.TRACE) log.trace(ME, "unSubscribe() Traversing " + childs.size() + " childs");
                  for (int ii=0; ii<childs.size(); ii++) {
                     SubscriptionInfo so = (SubscriptionInfo)childs.elementAt(ii);
                     fireUnSubscribeEvent(so);
                     so = null;
                  }
               }
               fireUnSubscribeEvent(subs);
               subs = null;
            }
            else {
               log.warn(ME, "UnSubscribe of " + xmlKey.getUniqueKey() + " failed");
               if (log.DUMP) log.dump(ME, toXml());
            }
         }
         else { // Try to unssubscribe with message oid instead of subscribe id:
            String suppliedXmlKey = xmlKey.getUniqueKey(); // remember supplied oid, another oid may be generated later

            Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, unSubscribeQos);

            if ((xmlKeyVec.size() == 0 || xmlKeyVec.size() == 1 && xmlKeyVec.elementAt(0) == null) && xmlKey.isExact()) {
               // Special case: the oid describes a returned oid from a XPATH subscription (if not, its an unknown oid - error)
               SubscriptionInfo subs = clientSubscriptions.getSubscription(sessionInfo, xmlKey.getUniqueKey()); // Access the XPATH subscription object ...
               if (subs != null && subs.getXmlKey().isQuery()) { // now do the query again ...
                  xmlKeyVec = parseKeyOid(sessionInfo, subs.getXmlKey(), unSubscribeQos);
                  fireUnSubscribeEvent(subs);    // Remove the object containing the XPath query
               }
            }

            for (int ii=0; ii<xmlKeyVec.size(); ii++) {
               XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);
               if (xmlKeyExact == null) {
                  log.warn(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey +") from " + sessionInfo.getLoginName() + ", can't access message, key oid '" + suppliedXmlKey + "' is unknown");
                  throw new XmlBlasterException(ME + ".OidUnknown", "unSubscribe(" + suppliedXmlKey + ") failed, can't access message, key oid '" + suppliedXmlKey + "' is unknown");
               }
               MessageUnitHandler handler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
               Vector subs = handler.findSubscriber(sessionInfo);
               for (int jj=0; subs != null && jj<subs.size(); jj++) {
                  SubscriptionInfo sub = (SubscriptionInfo)subs.elementAt(jj);
                  if (sub != null)
                     fireUnSubscribeEvent(sub);
                  else
                     log.warn(ME, "UnSubscribe of " + xmlKeyExact.getUniqueKey() + " failed");
               }
            }

            if (xmlKeyVec.size() < 1) {
               log.error(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
               throw new XmlBlasterException(ME + ".OidUnknown2", "Can't access subscription, unSubscribe failed, your supplied key oid '" + suppliedXmlKey + "' is invalid");
            }
         }
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
    * If MessageUnit is created from subscribe or MessageUnit is new, the key meta
    * data are added to the big DOM tree once (XmlKey takes care of that).
    * <p />
    * See <a href="http://www.xmlBlaster.org/xmlBlaster/src/java/org/xmlBlaster/protocol/corba/xmlBlaster.idl">xmlBlaster.idl</a>,
    * the CORBA access interface on how clients can access xmlBlaster.
    * <p />
    * TODO: Allow XML formatted returns which exactly match the update() return syntax (for clustering ClusterNode.java:update())
    *
    * @param sessionInfo  The SessionInfo object, describing the publishing client
    * @param msgUnit The MessageUnit struct
    * @param publishQos  Quality of Service, flags to control the publishing<p />
    * @return String with the XML encoded key oid of the msgUnit<br />
    *         If you let the oid be generated, you need this information
    *         for further publishing to the same MessageUnit<br />
    *         Rejected Messages will contain a string with state id!=OK
    *
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   public final String publish(SessionInfo sessionInfo, XmlKey xmlKey, MessageUnit msgUnit, PublishQos publishQos) throws XmlBlasterException
   {
      return publish(sessionInfo, xmlKey, msgUnit, publishQos, false);
   }

   /**
    * Used for cluster internal updates. 
    */
   public final String update(SessionInfo sessionInfo, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      if (updateQos.isErased()) {
         XmlKey key = new XmlKey(glob, updateKey.toXml(), false);
         EraseQoS qos = new EraseQoS(glob, updateQos);
         String[] ret = glob.getRequestBroker().erase(sessionInfo, key, qos, true);
         if (ret != null && ret.length > 0)
            return ret[0];
         else
            return "<qos/>";
      }
      else {
         //Transform an update to a publish: PublishKeyWrapper/PublishQosWrapper ?
         XmlKey key = new XmlKey(glob, updateKey.toXml(), true);
         //log.info(ME, "Dump of cluster update(): " + updateQos.toXml());
         PublishQos qos = new PublishQos(glob, updateQos);
         MessageUnit msgUnit = new MessageUnit(key.literal(), content, qos.toXml());

         return publish(sessionInfo, key, msgUnit, qos, true);
      }
   }

   /**
    * @param isClusterUpdate true if it is a update() callback message from another cluster node
    */
   private final String publish(SessionInfo sessionInfo, XmlKey xmlKey, MessageUnit msgUnit, PublishQos publishQos, boolean isClusterUpdate) throws XmlBlasterException
   {
      try {
         if (msgUnit == null || publishQos==null || xmlKey==null) {
            log.error(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
            throw new XmlBlasterException(ME + ".InvalidArguments", "The arguments of method publish() are invalid (null)");
         }

         if (log.CALL) log.call(ME, "Entering " + (isClusterUpdate?"cluster update message ":"") + "publish(oid='" + xmlKey.getKeyOid() + "', contentMime='" + xmlKey.getContentMime() + "', contentMimeExtended='" + xmlKey.getContentMimeExtended() + "' domain='" + xmlKey.getDomain() + "' from client '" + sessionInfo.getLoginName() + "' ...");
         if (log.DUMP) log.dump(ME, "Receiving " + (isClusterUpdate?"cluster update ":"") + " message in publish()\n" + xmlKey.literal() + "\n" + publishQos.toXml());

         PublishRetQos retVal = null;

         if (! publishQos.isFromPersistenceStore()) {

            if (publishQos.getSender() == null) // In cluster routing don't overwrite the original sender
               publishQos.setSender(sessionInfo.getLoginName());

            if (!myselfLoginName.equals(sessionInfo.getLoginName())) { // TODO: allow for cluster internal messages?
               int hopCount = publishQos.count(glob.getNodeId());
               if (hopCount > 0) {
                  log.warn(ME, "Warning, message oid='" + xmlKey.getKeyOid()
                     + "' passed my node id='" + glob.getId() + "' " + hopCount + " times before, we have a circular routing problem");
               }
               int stratum = -1; // not known yet, addRouteInfo() sets my stratum to one closer to the master,
                                 // this needs to be checked here as soon as we know which stratum we are!!!
               publishQos.addRouteInfo(new RouteInfo(glob.getNodeId(), stratum, publishQos.getRcvTimestamp()));
            }
         }

         publishedMessages++;

         if (glob.isAdministrationCommand(xmlKey)) {
            return glob.getMomClientGateway().setCommand(sessionInfo, xmlKey, msgUnit, publishQos, isClusterUpdate);
         }

         if (publishQos.isPubSubStyle()) {
            if (log.TRACE) log.trace(ME, "Doing publish() in Pub/Sub style");
synchronized (this) { // Change to snychronized(messageUnitHandler) {
            //----- 1. set new value or create the new message:
            MessageUnitHandler msgUnitHandler = null;
            boolean contentChanged = true;
            {
               if (log.TRACE) log.trace(ME, "Handle the new arrived Pub/Sub message ...");
               boolean messageExisted = true; // to shorten the synchronize block
               MessageUnitWrapper msgUnitWrapper = null;

               synchronized(messageContainerMap) {
                  Object obj = messageContainerMap.get(xmlKey.getUniqueKey());
                  if (obj == null) {
                     messageExisted = false;
                     msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);
                  }
                  else {
                     msgUnitHandler = (MessageUnitHandler)obj;
                  }


                  // Check if a publish filter is installed and if so invoke it ...
                  if (getPublishPluginManager().hasPlugins() && !isClusterUpdate) {
                     Map mimePlugins = getPublishPluginManager().findMimePlugins(xmlKey.getContentMime(),xmlKey.getContentMimeExtended());
                     if (mimePlugins != null) {
                        Iterator iterator = mimePlugins.values().iterator();
                        if (msgUnitWrapper == null)
                           msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);
                        // note that msgUnitWrapper.getMessageUnitHandler() is not allowed (is null)
                        while (iterator.hasNext()) {
                           I_PublishFilter plugin = (I_PublishFilter)iterator.next();
                           if (log.TRACE) log.trace(ME, "Message " + xmlKey.getKeyOid() + " is forwarded to publish plugin");
                           String ret = plugin.intercept(sessionInfo.getSubjectInfo(), msgUnitWrapper);
                           if (ret == null || ret.length() == 0 || ret.equals(Constants.STATE_OK))
                              break;
                           else {
                              if (log.TRACE) log.trace(ME, "Message " + xmlKey.getKeyOid() + " is rejected by PublishPlugin");
                              return "<qos><state id='" + ret + "'/></qos>";  // Message is rejected by PublishPlugin
                           }
                        }
                     }
                  }


                  // cluster support - forward message to master ...
                  if (useCluster) {
                     if (!isClusterUpdate) { // updates from other nodes are arriving here in publish as well
                        if (msgUnitWrapper == null)
                           msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);
                        // note that msgUnitWrapper.getMessageUnitHandler() is not allowed (is null)
                        try {
                           PublishRetQosWrapper ret = glob.getClusterManager().forwardPublish(sessionInfo, msgUnitWrapper);
                           //Thread.currentThread().dumpStack();
                           if (ret != null) { // Message was forwarded to master cluster
                              retVal = ret.getPublishRetQos();
                              if (ret.getNodeDomainInfo().getDirtyRead() == false) {
                                 if (log.TRACE) log.trace(ME, "Message " + xmlKey.getKeyOid() + " forwarded to master " + ret.getNodeDomainInfo().getId() + ", dirtyRead==false nothing more to do");
                                 return retVal.toXml();
                              }
                              // else we publish it locally as well (dirty read!)
                           }
                        }
                        catch (XmlBlasterException e) {
                           if (e.id.equals("ClusterManager.PluginFailed")) {
                              useCluster = false;
                           }
                           else {
                              e.printStackTrace();
                              throw e;
                           }
                        }
                     }
                  }
                     
                  if (msgUnitHandler == null)
                     msgUnitHandler = new MessageUnitHandler(this, xmlKey, msgUnitWrapper);

                  if (!messageExisted)
                     messageContainerMap.put(xmlKey.getUniqueKey(), msgUnitHandler);
               }

               boolean isYetUnpublished = !msgUnitHandler.isPublishedWithData(); // remember here as it may be changed in setContent()

               if (messageExisted) {
                  if (msgUnitWrapper == null)
                     msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);

                  contentChanged = msgUnitHandler.setContent(xmlKey, msgUnitWrapper, publishQos);
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
            if (log.TRACE) log.trace(ME, "Message " + xmlKey.getKeyOid() + " handled, now we can send updates to all interested clients.");
            if (contentChanged || publishQos.forceUpdate()) // if the content changed of the publisher forces updates ...
               msgUnitHandler.invokeCallback(sessionInfo, Constants.STATE_OK);

            //----- 3. check all known query subscriptions if the new message fits as well
            // TODO: Only check if it is a new message (XmlKey is immutable)
            checkExistingSubscriptions(sessionInfo, xmlKey, msgUnitHandler, publishQos);

            // First all callback calls must be successful - the CbWorker checks it as well
            if (msgUnitHandler.isPublishedWithData() &&
                msgUnitHandler.getMessageUnitWrapper().getPublishQos().isVolatile() &&
                msgUnitHandler.getMessageUnitWrapper().getEnqueueCounter() == 0)
               eraseVolatile(sessionInfo, msgUnitHandler);
} // synchronized
         }
         else if (publishQos.isPTP_Style()) {
            if (log.TRACE) log.trace(ME, "Doing publish() in PtP or broadcast style");
            if (log.DUMP) log.dump(ME, publishQos.toXml());

            MessageUnitWrapper msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);

            // Check if a publish filter is installed and if so invoke it ...
            if (getPublishPluginManager().hasPlugins() && !isClusterUpdate) {
               Map mimePlugins = getPublishPluginManager().findMimePlugins(xmlKey.getContentMime(),xmlKey.getContentMimeExtended());
               if (mimePlugins != null) {
                  Iterator iterator = mimePlugins.values().iterator();
                  if (msgUnitWrapper == null)
                     msgUnitWrapper = new MessageUnitWrapper(glob, this, xmlKey, msgUnit, publishQos);
                  // note that msgUnitWrapper.getMessageUnitHandler() is not allowed (is null)
                  while (iterator.hasNext()) {
                     I_PublishFilter plugin = (I_PublishFilter)iterator.next();
                     String ret = plugin.intercept(sessionInfo.getSubjectInfo(), msgUnitWrapper);
                     if (ret == null || ret.length() == 0 || ret.equals(Constants.STATE_OK))
                        break;
                     else
                        return "<qos><state id='" + ret + "'/></qos>";  // Message is rejected by PublishPlugin
                  }
               }
            }

            Vector destinationVec = publishQos.getDestinations(); // !!! add XPath client query here !!!

            //-----    Send message to every destination client
            for (int ii = 0; ii<destinationVec.size(); ii++) {
               Destination destination = (Destination)destinationVec.elementAt(ii);
               if (log.TRACE) log.trace(ME, "Delivering message to destination [" + destination.getDestination() + "]");
               if (destination.isSessionId()) {
                  SessionInfo receiverSessionInfo = authenticate.getSessionInfo(destination.getDestination());
                  if (receiverSessionInfo == null) {
                     String tmp = "Sending PtP message to unknown session '" + destination.getDestination() + "' failed, message is lost.";
                     log.warn(ME, tmp);
                     throw new XmlBlasterException("PtP.Failed", tmp);
                  }
                  receiverSessionInfo.queueMessage(new MsgQueueEntry(glob, receiverSessionInfo, msgUnitWrapper));
               }
               else {
                  if (destination.forceQueuing()) {
                     SubjectInfo destinationClient = authenticate.getOrCreateSubjectInfoByName(destination.getDestination());
                     destinationClient.queueMessage(new MsgQueueEntry(glob, destinationClient, msgUnitWrapper));
                  }
                  else {
                     SubjectInfo destinationClient = authenticate.getSubjectInfoByName(destination.getDestination());
                     if (destinationClient == null) {
                        String tmp = "Sending PtP message to '" + destination.getDestination() + "' failed, message is lost.";
                        log.warn(ME, tmp);
                        throw new XmlBlasterException("PtP.Failed", tmp+" Client is not logged in and <destination forceQueuing='true'> is not set");
                     }
                     destinationClient.queueMessage(new MsgQueueEntry(glob, destinationClient, msgUnitWrapper));
                  }
               }
            }
         }
         else {
            log.warn(ME + ".UnsupportedMoMStyle", "Unknown publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
            throw new XmlBlasterException(ME + ".UnsupportedMoMStyle", "Please verify your publish - QoS, only PTP (point to point) and Publish/Subscribe is supported");
         }
         
         if (retVal != null)
            return retVal.toXml(); // Use the return value of the cluster master node

         StringBuffer buf = new StringBuffer(160);
         buf.append("<qos><state id='").append(Constants.STATE_OK).append("'/><key oid='").append(xmlKey.getUniqueKey()).append("'/></qos>");
         return buf.toString();
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
      if (log.TRACE) log.trace(ME, "Published message is marked as volatile, erasing it");
      try {
         fireMessageEraseEvent(sessionInfo, msgUnitHandler, null); // null: no erase event for volatile messages
      } catch (XmlBlasterException e) {
         if (log.TRACE) log.error(ME, "Unexpected exception: " + e.toString());
      }
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

         if (log.TRACE) log.trace(ME, "Checking existing query subscriptions if they match with this new one");

         Set set = clientSubscriptions.getQuerySubscribeRequestsSet();
         Vector matchingSubsVec = new Vector();
         synchronized (set) {
            Iterator iterator = set.iterator();
            // for every XPath subscription ...
            while (iterator.hasNext()) {

               SubscriptionInfo existingQuerySubscription = (SubscriptionInfo)iterator.next();
               XmlKey queryXmlKey = existingQuerySubscription.getXmlKey();
               if (!queryXmlKey.isQuery() || queryXmlKey.getQueryType() != XmlKey.XPATH_QUERY) { // query: subscription without a given oid
                  log.warn(ME,"Only XPath queries are supported, ignoring subscription.");
                  continue;
               }
               String xpath = queryXmlKey.getQueryString();

               // ... check if the new message matches ...
               if (xmlKey.match(xpath) == true) {
                  SubscriptionInfo subs = new SubscriptionInfo(glob, existingQuerySubscription.getSessionInfo(), existingQuerySubscription, xmlKey);
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
    * @return String array with the xml encoded key oid's which are deleted
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.publish.html">The interface.publish requirement</a>
    */
   String[] erase(SessionInfo sessionInfo, XmlKey xmlKey, EraseQoS eraseQos) throws XmlBlasterException {
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
   private String[] erase(SessionInfo sessionInfo, XmlKey xmlKey, EraseQoS eraseQos, boolean isClusterUpdate) throws XmlBlasterException
   {
      try {
         if (log.CALL) log.call(ME, "Entering " + (isClusterUpdate?"cluster update message ":"") +
                "erase(oid='" + xmlKey.getKeyOid() + "', queryType='" + xmlKey.getQueryTypeStr() +
                "', query='" + xmlKey.getQueryString() + "') client '" + sessionInfo.getLoginName() + "' ...");

         Vector xmlKeyVec = parseKeyOid(sessionInfo, xmlKey, eraseQos);
         Set oidSet = new HashSet(xmlKeyVec.size());  // for return values (TODO: change to TreeSet to maintain order)

         for (int ii=0; ii<xmlKeyVec.size(); ii++) {
            XmlKey xmlKeyExact = (XmlKey)xmlKeyVec.elementAt(ii);

            if (useCluster && !isClusterUpdate) { // cluster support - forward erase to master
               try {
                  EraseRetQos ret[] = glob.getClusterManager().forwardErase(sessionInfo, xmlKey, eraseQos);
                  //Thread.currentThread().dumpStack();
                  //if (ret != null) return ret;
               }
               catch (XmlBlasterException e) {
                  if (e.id.equals("ClusterManager.PluginFailed")) {
                     useCluster = false;
                  }
                  else {
                     e.printStackTrace();
                     throw e;
                  }
               }
            }

            if (xmlKeyExact == null) { // unSubscribe on a unknown message ...
               log.warn(ME, "Erase on unknown message [" + xmlKey.getUniqueKey() + "] is ignored");
               // !!! how to delete XPath subscriptions, still MISSING ???
               continue;
            }

            if (log.TRACE) log.trace(ME, "erase oid='" + xmlKeyExact.getUniqueKey() + "' of total " + xmlKeyVec.size() + " ...");

            MessageUnitHandler msgUnitHandler = getMessageHandlerFromOid(xmlKeyExact.getUniqueKey());
            //log.info(ME, "Erasing " + msgUnitHandler.toXml());

            oidSet.add(msgUnitHandler.getUniqueKey());
            try {
               fireMessageEraseEvent(sessionInfo, msgUnitHandler, Constants.STATE_ERASED);
            } catch (XmlBlasterException e) {
               if (log.TRACE) log.error(ME, "Unexpected exception: " + e.toString());
            }
         }
         //log.info(ME, "AFTER ERASE: " + toXml());

         // Build the return values ...
         String[] oidArr = new String[oidSet.size()];
         //oidSet.toArray(oidArr);
         Iterator it = oidSet.iterator();
         StringBuffer sb = new StringBuffer(160);
         int ii = 0;
         while (it.hasNext()) {
            String oid = (String)it.next();
            sb.append("<qos><state id='").append(Constants.STATE_OK).append("'/><key oid='").append(oid).append("'/></qos>");
            oidArr[ii++] = sb.toString();
            sb.setLength(0);
         }
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
    * Event invoked on message erase() invocation. 
    */
   public void messageErase(MessageUnitHandler msgUnitHandler) throws XmlBlasterException
   {
      if (msgUnitHandler.hasExactSubscribers()) {
         if (log.TRACE) log.trace(ME, "Erase event occured for oid=" + msgUnitHandler.getUniqueKey() + ", not removing messageUnitHandler skeleton since " + msgUnitHandler.numSubscribers() + " subscribers exist ...");
         // MessageUnitHandler will call 
      }
      else {
         String uniqueKey = msgUnitHandler.getUniqueKey();
         if (log.TRACE) log.trace(ME, "Erase event occured for oid=" + uniqueKey + ", removing message from my map ...");
         synchronized(messageContainerMap) {
            Object obj = messageContainerMap.remove(uniqueKey);
            if (obj == null) {
               log.warn(ME + ".NotRemoved", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
               throw new XmlBlasterException(ME + ".NOT_REMOVED", "Sorry, can't remove message unit, because it didn't exist: " + uniqueKey);
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
      synchronized (msgUnitLoginEvent) {
         msgUnitLoginEvent.setContentRaw(sessionInfo.getLoginName().getBytes());
         publish(unsecureSessionInfo, msgUnitLoginEvent.getXmlKey(), msgUnitLoginEvent.getMessageUnit(),
                 msgUnitLoginEvent.getPublishQos()); // publish that this client logged in
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
      if (log.TRACE) log.trace(ME, "Logout event for client " + sessionInfo.toString());
      synchronized (msgUnitLogoutEvent) {
         msgUnitLogoutEvent.setContentRaw(sessionInfo.getLoginName().getBytes());
         publish(unsecureSessionInfo, msgUnitLogoutEvent.getXmlKey(), msgUnitLogoutEvent.getMessageUnit(),
                 msgUnitLogoutEvent.getPublishQos()); // publish that this client logged out
      }
      if (log.TRACE) log.trace(ME, " client removed:"+sessionInfo.getLoginName());
      synchronized (loggedIn) {
         if (sessionInfo.getSubjectInfo().getSessions().size() == 1) {
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
    * Notify all Listeners that a message is erased.
    *
    * @param sessionInfo
    * @param msgUnitHandler
    * @param state Constants.STATE_ERASED or null (no erase event for volatile messages)
    */
   final void fireMessageEraseEvent(SessionInfo sessionInfo, MessageUnitHandler msgUnitHandler, String state) throws XmlBlasterException
   {
      // 1. Remove Node in big xml dom
      try {
         bigXmlKeyDOM.messageErase(msgUnitHandler);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase event, we ignore it: " + e.toString());
         e.printStackTrace();
      }

      // 2. Send erase event
      if (state != null) {
         if (log.TRACE) log.trace(ME, "Sending client notification about message erase() event");
         try {
            msgUnitHandler.invokeCallback(sessionInfo, state);
         }
         catch (XmlBlasterException e) {
            // The access plugin or client may throw an exception. The behavior is not coded yet
            log.error(ME, "Received exception for message erase event (callback to client), we ignore it: " + e.toString());
         }
      }

      // 3. Remove from subscription set
      try {
         clientSubscriptions.messageErase(sessionInfo, msgUnitHandler);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase event, we ignore it: " + e.toString());
         e.printStackTrace();
      }

      // 4. Remove from my message map
      try {
         messageErase(msgUnitHandler);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase event, we ignore it: " + e.toString());
         e.printStackTrace();
      }

      // 5. Cleanup message handler
      try {
         msgUnitHandler.erase(sessionInfo);
      }
      catch (XmlBlasterException e) {
         log.error(ME, "Received exception on message erase event, we ignore it: " + e.toString());
         e.printStackTrace();
      }

      // 6. give gc() a hint
      msgUnitHandler = null;
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
      String offset = "\n";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      Iterator iterator = messageContainerMap.values().iterator();

      sb.append(offset + "<RequestBroker>");
      while (iterator.hasNext()) {
         MessageUnitHandler msgUnitHandler = (MessageUnitHandler)iterator.next();
         sb.append(msgUnitHandler.toXml(extraOffset));
      }
      sb.append(bigXmlKeyDOM.printOn(extraOffset).toString());
      sb.append(clientSubscriptions.toXml(extraOffset));
      if (useCluster) {
         sb.append(glob.getClusterManager().toXml(extraOffset));
      }
      sb.append(offset + "</RequestBroker>\n");

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
         throw new XmlBlasterException(e.id, e.reason);
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
      final long exitSleep = 2000L;
      Timeout timeout = new Timeout("ExitTimer");
      Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
            public void timeout(Object userData) {
               log.info(ME, "Administrative exit(" + exitVal + ") after exit-timeout of " + exitSleep + " millis.");
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
      return messageContainerMap.size();
   }
   public String getMsgList() {
      StringBuffer sb = new StringBuffer(messageContainerMap.size()*40);
      Iterator iterator = messageContainerMap.values().iterator();
      while (iterator.hasNext()) {
         if (sb.length() > 0)
            sb.append(",");
         MessageUnitHandler msgUnitHandler = (MessageUnitHandler)iterator.next();
         sb.append(msgUnitHandler.getUniqueKey());
      }
      return sb.toString();
   }

}
