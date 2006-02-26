 /*------------------------------------------------------------------------------
Name:      ReplManagerPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file

Switch on finer logging in xmlBlaster.properties:
trace[org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter]=true
trace[org.xmlBlaster.contrib.db.DbPool]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.MD5ChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.plugin.ReplManagerPlugin]=true
trace[org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher]=true
trace[org.xmlBlaster.contrib.dbwatcher.DbWatcher]=true
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import org.xmlBlaster.authentication.ClientEvent;
import org.xmlBlaster.authentication.I_ClientListener;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.VersionTransformerCache;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.replication.I_ReplSlave;
import org.xmlBlaster.contrib.replication.ReplSlave;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.SqlStatement;
import org.xmlBlaster.engine.I_SubscriptionListener;
import org.xmlBlaster.engine.SubscriptionEvent;
import org.xmlBlaster.engine.SubscriptionInfo;
import org.xmlBlaster.engine.qos.ConnectQosServer;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.QueuePluginManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.logging.Logger;

/**
 * ReplManagerPlugin is a plugin wrapper if you want to run DbWatcher inside xmlBlaster. 
 * <p />
 * DbWatcher checks a database for changes and publishes these to the MoM
 * <p />
 * This plugin needs to be registered in <tt>xmlBlasterPlugins.xml</tt>
 * to be available on xmlBlaster server startup.
 *
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ReplManagerPlugin extends GlobalInfo implements ReplManagerPluginMBean, I_Callback, I_MsgDispatchInterceptor, I_ClientListener, I_SubscriptionListener {
   
   public final static String SESSION_ID = "replManager/1";
   private final static String SENDER_SESSION = "_senderSession";
   private static Logger log = Logger.getLogger(ReplManagerPlugin.class.getName());
   private Object mbeanHandle;
   private String user = "replManager";
   private String password = "secret";
   private Map replications;
   private Map replSlaveMap;
   /** Keys are requestId Strings, and values are SqlStatement objects */
   private Map sqlStatementMap;
   private boolean shutdown;
   private boolean initialized;
   
   private String instanceName;
   private long maxSize = 999999L;
   private String sqlTopic;
   private long maxResponseEntries; 
   private I_DbPool pool;
   private VersionTransformerCache transformerCache;
   private String cachedListOfReplications;
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public ReplManagerPlugin() {
      super(new String[] {});
      this.replications = new TreeMap();
      this.replSlaveMap = new TreeMap();
      this.sqlStatementMap = new TreeMap();
      this.transformerCache = new VersionTransformerCache();
   }

   public String transformVersion(String replPrefix, String srcVersion, String destVersion, String destination, String srcData) throws Exception {
      if (destVersion == null)
         return srcData;
      if (destVersion.equalsIgnoreCase(srcVersion))
         return srcData;
      return this.transformerCache.transform(replPrefix, srcVersion, destVersion, destination, srcData, null);
   }
   
   
   public String transformVersion(String replPrefix, String destVersion, String destination, String srcData) throws Exception {
      I_Info tmpInfo = (I_Info)this.replications.get(replPrefix);
      if (tmpInfo == null)
         throw new Exception("The replication with replication.prefix='" + replPrefix + "' was not found");
      String srcVersion = tmpInfo.get("replication.version", "").trim();
      if (srcVersion.length() < 1)
         throw new Exception("The replication '" + replPrefix + "' has no version defined");
      return transformVersion(replPrefix, srcVersion, destVersion, destination, srcData);
   }
   
   public void clearVersionCache() {
      this.transformerCache.clearCache();
      this.cachedListOfReplications = null;
   }

   
   /**
    * Never returns null. It returns a list of keys identifying the slaves using the replication 
    * manager.
    * @return
    */
   public String getSlaves() {
      return InfoHelper.getIteratorAsString(this.replSlaveMap.keySet().iterator());
   }
   
   /**
    * Never returns null. It returns a list of keys identifying the ongoing replications.
    * @return
    */
   public String getReplications() {
      if (this.cachedListOfReplications != null)
         return this.cachedListOfReplications;
      // rebuild the cache
      Iterator iter = this.replications.values().iterator();
      boolean isFirst = true;
      StringBuffer buf = new StringBuffer();
      while (iter.hasNext()) {
         I_Info tmpInfo = (I_Info)iter.next();
         String tmp = tmpInfo.get(ReplicationConstants.SUPPORTED_VERSIONS, null);
         log.info("replications : '" + tmp + "'");
         if (tmp != null) {
            if (!isFirst)
               buf.append(",");
            isFirst = false;
            buf.append(tmp);
         }
         else {
            String replPrefix = tmpInfo.get("replication.prefix", "REPL_");
            log.warning("Property '" + ReplicationConstants.SUPPORTED_VERSIONS + "' not found for '" + replPrefix + "'");
            if (!isFirst)
               buf.append(",");
            isFirst = false;
            buf.append(replPrefix);
         }
      }
      // return InfoHelper.getIteratorAsString(this.replications.keySet().iterator());
      this.cachedListOfReplications = buf.toString();
      return this.cachedListOfReplications;
   }
   
   public String getType() {
      return "ReplManager";
   }
   
   public String getVersion() {
      return "1.0";
   }

   /**
    * Creates a I_DbPool object out of the JDBC,1.0 Queue Properties and initializes the pool.
    * @return
    * @throws Exception
    */
   private I_DbPool getDbPool() throws Exception {
      QueuePluginManager pluginManager = new QueuePluginManager(this.global);
      PluginInfo queuePluginInfo = new PluginInfo(this.global, pluginManager, "JDBC", "1.0");
      Properties prop = (Properties)queuePluginInfo.getParameters();
      String dbUrl = prop.getProperty("url", null);
      String dbUser = prop.getProperty("user", null);
      String dbPassword = prop.getProperty("password", null);
      log.info("db.url='" + dbUrl + "' db.user='" + dbUser + "'");
      
      I_Info tmpInfo = new PropertiesInfo(new Properties());
      if (dbUrl != null)
         tmpInfo.put("db.url", dbUrl);
      else
         log.warning("the property 'url' was not set");
      if (dbUser != null)
         tmpInfo.put("db.user", dbUser);
      else
         log.warning("the property 'user' was not set");
      if (dbPassword != null)
         tmpInfo.put("db.password", dbPassword);
      else
         log.warning("the property 'password' was not set");
      I_DbPool pool = new DbPool();
      pool.init(tmpInfo);
      return pool;
   }
   
   /**
    * Intiates the replication for the given slave.
    * TODO Specify that the replicationKey (dbmasterid) must be short and DB conform.
    * Usually called by Human being via JMX Console.
    * 
    * The cascaded replication is the replication which will be automatically started once the initial update of the first replication is finished. This is 
    * used to concatenate replications. A typical usecase is in two way replication, then the initial update of the back replication can be automatically triggered
    * once the initial update of the main replication is finished.
    * 
    * @param slaveSessionName
    * @param replicationKey This is the dbWatcher replication.prefix attribute.
    * @param cascadeSlaveSessionName The Name of the session of the dbWriter to be used for the cascaded replication. Can be null.
    * @param cascadedReplicationPrefix the prefix identifing the DbWatcher for the cascaded replication. Can be null.  
    * @param cascadeReplicationPrefix
    * @throws Exception
    */
   public String initiateReplication(String slaveSessionName, String prefixWithVersion, String cascadeSlaveSessionName, String cascadeReplicationPrefix) throws Exception {
      try {
         if (slaveSessionName == null || slaveSessionName.trim().length() < 1)
            throw new Exception("ReplManagerPlugin.initiateReplication: The slave session name is null, please provide one");
         if (prefixWithVersion == null || prefixWithVersion.length() < 1)
            throw new Exception("ReplManagerPlugin.initiateReplication: The replication.prefix is null, please provide one");
         slaveSessionName = slaveSessionName.trim();
         String ret = "initiateReplication invoked for slave '" + slaveSessionName + "' and on replication '" + prefixWithVersion + "'";
         log.info(ret);
         
         
         String replicationPrefix = VersionTransformerCache.stripReplicationPrefix(prefixWithVersion);
         String requestedVersion = VersionTransformerCache.stripReplicationVersion(prefixWithVersion);
         
         I_Info individualInfo = (I_Info)this.replications.get(replicationPrefix);
         if (individualInfo != null) {
            individualInfo.put(ReplicationConstants.REPL_VERSION, requestedVersion);
            individualInfo.putObject("org.xmlBlaster.engine.Global", this.global);
            I_ReplSlave slave = null;
            synchronized (this.replSlaveMap) {
               slave = (I_ReplSlave)this.replSlaveMap.get(slaveSessionName);
            }
            if (slave != null) {
               individualInfo.put("_replName", replicationPrefix);
               String dbWatcherSessionId = individualInfo.get(SENDER_SESSION, null);
               if (dbWatcherSessionId == null)
                  throw new Exception("ReplSlave '" + slave + "' constructor: the master Session Id (which is passed in the properties as '" + SENDER_SESSION + "' are not found. Can not continue with initial update");

               if (cascadeSlaveSessionName != null) {
                  // check to avoid loops
                  cascadeSlaveSessionName = cascadeSlaveSessionName.trim();
                  if (slaveSessionName.equals(cascadeSlaveSessionName))
                     return "error: " + ret + " did fail since having the same slave '" + slaveSessionName + "' for both replications would result in a loop";
               }
               
               boolean isOkToStart = slave.run(individualInfo, dbWatcherSessionId, cascadeReplicationPrefix, cascadeSlaveSessionName);
               if (isOkToStart == false) {
                  ret += " did fail since your status is '" + slave.getStatus() + "'. Please invoke first 'Cancel Update'";
                  return "error: " + ret; // don't throw an exception here since MX4J seems to loose exception msg.
               }
            }
            else
               throw new Exception("the replication slave '" + slaveSessionName + "' was not found among the list of slaves which is '" + getSlaves() + "'");
         }
         else
            throw new Exception("initiateReplication failed for '" + slaveSessionName + "' with replication key '" + replicationPrefix + "' since not known. Known are '" + getReplications() + "'");
         return ret;
      }
      catch (Exception ex) {
         ex.printStackTrace();
         throw ex;
      }
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   protected synchronized void doInit(Global global, PluginInfo pluginInfo) throws XmlBlasterException {
      if (this.initialized)
         return;
      try {
         // String momClass = get("mom.class", "org.xmlBlaster.contrib.MomEventEngine").trim();
         // String registryName = "mom.publisher";
         synchronized (ReplManagerPlugin.class) {
            this.instanceName = "replication";
         }
         
         ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, instanceName,
               this.global.getContextNode());
         this.mbeanHandle = this.global.registerMBean(contextNode, this);
         
         this.pool = getDbPool();
         
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         ConnectQos connectQos = new ConnectQos(this.global, this.user, this.password);
         boolean persistentConnection = true;
         boolean persistentSubscription = true;
         connectQos.setPersistent(persistentConnection);
         connectQos.setMaxSessions(1);
         connectQos.setPtpAllowed(true);
         connectQos.setSessionTimeout(0L);
         String sessionName = ReplicationConstants.REPL_MANAGER_SESSION;
         connectQos.setSessionName(new SessionName(this.global, sessionName));
         conn.connect(connectQos, this);
         
         // this is the instance passed from the outside, not a clone, otherwise
         // it will not find the plugin registry for the MIME plugin
         putObject("org.xmlBlaster.engine.Global", global);
         getEngineGlobal(this.global).getPluginRegistry().register(getType() + "," + getVersion(), this);

         this.sqlTopic = this.get("replication.sqlTopic", "sqlTopic");
         if (this.sqlTopic != null) {
            SubscribeKey subKey = new SubscribeKey(this.global, this.sqlTopic);
            SubscribeQos subQos = new SubscribeQos(this.global);
            subQos.setPersistent(persistentSubscription);
            subQos.setMultiSubscribe(false);
            conn.subscribe(subKey, subQos);
         }
         this.maxResponseEntries = this.getLong("replication.sqlMaxEntries", 10L);

         getEngineGlobal(this.global).getRequestBroker().getAuthenticate(null).addClientListener(this);

         SessionInfo[] sessionInfos = getEngineGlobal(this.global).getRequestBroker().getAuthenticate(null).getSessionInfoArr();
         for (int i=0; i < sessionInfos.length; i++) {
            SessionInfo sessionInfo = sessionInfos[i];
            ClientEvent event = new ClientEvent(sessionInfo);
            sessionAdded(event);

            getEngineGlobal(this.global).getRequestBroker().addSubscriptionListener(this);
            
            SubscriptionInfo[] subInfos = getEngineGlobal(this.global).getRequestBroker().getClientSubscriptions().getSubscriptions(sessionInfo);
            for (int j=0; j < subInfos.length; j++) { 
               SubscriptionEvent subEvent = new SubscriptionEvent(subInfos[j]);
               subscriptionAdd(subEvent);
            }
         }
         
         this.initialized = true;
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "ReplManagerPlugin", "init failed", e); 
      }
      log.info("Loaded ReplManagerPlugin '" + getType() + "'");
   }

   private org.xmlBlaster.engine.Global getEngineGlobal(Global glob) {
      return (org.xmlBlaster.engine.Global)glob.getObjectEntry(ORIGINAL_ENGINE_GLOBAL);
   }
   
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public synchronized void shutdown() throws XmlBlasterException {
      if (this.shutdown)
         return;
      super.shutdown();
      try {
         this.global.unregisterMBean(this.mbeanHandle);
         getEngineGlobal(this.global).getRequestBroker().getAuthenticate(null).removeClientListener(this);
         getEngineGlobal(this.global).getRequestBroker().removeSubscriptionListener(this);
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         if (this.sqlTopic != null) {
            UnSubscribeKey key = new UnSubscribeKey(this.global, this.sqlTopic);
            conn.unSubscribe(key, new UnSubscribeQos(this.global));
         }
         conn.disconnect(new DisconnectQos(this.global));
         this.replications.clear();
         this.replSlaveMap.clear();
         
         synchronized(this.sqlStatementMap) {
            String[] keys = (String[])this.sqlStatementMap.keySet().toArray(new String[this.sqlStatementMap.size()]);
            for (int i=0; i < keys.length; i++)
               unregisterSqlStatement(keys[i]);
         }
         
         getEngineGlobal(this.global).getPluginRegistry().unRegister(getType() + "," + getVersion());
         this.pool.shutdown();
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      this.shutdown = true;
      log.info("Stopped DbWatcher plugin '" + getType() + "'");
   }
   
   /**
    * Used to register a dbWatcher. This is a request coming directly from the
    * DbWatcher which registeres himself to this plugin.
    * Note that if you are using the same id for the replication on several DbWatcher
    * (several writers) only the first dbWatcher will pass the configuration. You are
    * responsible of ensuring that the relevant configuration parameters are the same
    * for all such DbWatcher instances.
    * 
    * @param senderSession The session requesting this registration. This is needed
    * to reply to the right requestor.
    * 
    * @param replId
    * @param info These are the Configuration of the DbWatcher, for example Table Names and so forth.
    */
   public synchronized void register(String senderSession, String replicationPrefix, I_Info info) {
      I_Info oldInfo = (I_Info)this.replications.get(replicationPrefix);
      info.put(SENDER_SESSION, senderSession);
      if (oldInfo != null) {
         log.info("register '" + replicationPrefix + "' by senderSession='" + senderSession + "'");
         String oldSenderSession = oldInfo.get(SENDER_SESSION, senderSession);
         if (oldSenderSession.equals(senderSession)) {
            log.info("register '" + replicationPrefix + "' by senderSession='" + senderSession + "' will overwrite old registration done previously");
            this.replications.put(replicationPrefix, info);
         }
         else {
            log.info("register '" + replicationPrefix + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Will ignore the new one.");
         }
      }
      else
         this.replications.put(replicationPrefix, info);
      this.cachedListOfReplications = null; // clear the cache
   }
   
   public synchronized void unregister(String senderSession, String replicationPrefix) {
      I_Info oldInfo = (I_Info)this.replications.get(replicationPrefix);
      if (oldInfo == null)
         log.info("unregister '" + replicationPrefix + "' by senderSession='" + senderSession + "' is ignored since there is no such registration done");
      else {
         log.info("unregister '" + replicationPrefix + "' by senderSession='" + senderSession + "'");
         /*
         if (log.isLoggable(Level.FINE)) {
            log.fine("unregister '" + replId + "' by senderSession='" + senderSession + "' the stack trace is:");
            Thread.dumpStack();
         }
         */
         String oldSenderSession = oldInfo.get(SENDER_SESSION, senderSession);
         if (oldSenderSession.equals(senderSession)) {
            this.replications.remove(replicationPrefix);
         }
         else {
            log.warning("unregister '" + replicationPrefix + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Please do it with the correct Session");
         }
      }
      this.cachedListOfReplications = null; // clear the cache
   }
   
   /**
    * It becomes events from all ReplicationConverter instances which want to register themselves for
    * administration of initial updates.
    * 
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      try {
         SessionName senderSession = updateQos.getSender();
         String request = updateQos.getClientProperty("_command", "");
         log.info("The master Replicator with session '" + senderSession.getRelativeName() + "' is sending '" + request + "'");

         if ("broadcastSql".equalsIgnoreCase(request)) {
            try {
               final boolean highPrio = true;
               String requestId = updateQos.getClientProperty("requestId", (String)null);
               if (requestId == null)
                  throw new Exception("The requestId has not been defined");
               String repl =  updateQos.getClientProperty("replication.prefix", (String)null);
               String sql =  new String(content);
               sendBroadcastRequest(repl, sql, highPrio, requestId);
               return "OK";
            }
            catch (Throwable ex) {
               ex.printStackTrace();
               return "NOK";
            }
         }
         else if ("removeBroadcast".equalsIgnoreCase(request)) {
            try {
               removeSqlStatement(new String(content));
               return "OK";
            }
            catch (Throwable ex) {
               ex.printStackTrace();
               return "NOK";
            }
         }
         // 1. This is a response from an sql statement which has been previously sent to the slaves.
         else if (this.sqlTopic != null && updateKey.getOid().equals(this.sqlTopic)) {
            ClientProperty prop = (ClientProperty)updateQos.getClientProperties().get(ReplicationConstants.STATEMENT_ID_ATTR);
            if (prop == null) {
               log.severe("The statement id is not specified, can not process it");
               return "NOK";
            }
            String reqId = prop.getStringValue();
            SqlStatement sqlStatement = (SqlStatement)this.sqlStatementMap.get(reqId);
            if (sqlStatement == null) {
               log.severe("The statement with id '" + reqId + "' has not been found");
               return "NOK";
            }

            prop = (ClientProperty)updateQos.getClientProperties().get(ReplicationConstants.EXCEPTION_ATTR);
            String response = null;
            boolean isException = false;
            if (prop != null) {
               response = prop.getStringValue();
               isException = true;
            }
            prop = (ClientProperty)updateQos.getClientProperties().get(ReplicationConstants.MASTER_ATTR);
            if (prop != null) { // then it is the response from the master
               String replPrefix = prop.getStringValue();
               if (response == null)
                  response = new String(content);
               sqlStatement.setResponse(replPrefix, response, isException);
            }
            else {
               if (response == null)
                  response = new String(content);
               sqlStatement.setResponse(senderSession.getRelativeName(), response, isException);
            }
         }
         // 2. This is the response coming from a DbWatcher on a request for initial update which one of the ReplSlaves has previously requested.
         else if ("INITIAL_DATA_RESPONSE".equals(request)) {
            long minReplKey = updateQos.getClientProperty("_minReplKey", 0L);
            long maxReplKey = updateQos.getClientProperty("_maxReplKey", 0L);
            try {
               String slaveName = updateQos.getClientProperty("_slaveName", (String)null);
               if (slaveName == null)
                  log.severe("on initial data response the slave name was not specified. Can not perform operation");
               else {
                  I_ReplSlave slave = null;
                  synchronized (this.replSlaveMap) {
                     slave = (I_ReplSlave)this.replSlaveMap.get(slaveName);
                  }
                  if (slave == null)
                     log.severe("on initial data response the slave name '" + slaveName + "' was not registered (could have already logged out)");
                  else
                     slave.reactivateDestination(minReplKey, maxReplKey);
               }
            }
            catch (Exception ex) {
               log.warning("reactivateDestination encountered an exception '" + ex.getMessage());
            }
         }
         // 3. then it must be a register or unregister requests coming from a DbWatcher (when they connect or reconnect)
         else { // PtP Messages from DbWatcher (Register/Unregister)
            String replId = updateQos.getClientProperty(ReplicationConstants.REPL_PREFIX_KEY, (String)null);
            if (replId == null || replId.length() < 1)
               log.severe(request + ": the client property '" + ReplicationConstants.REPL_PREFIX_KEY + "' must be defined but is empty");
            else {
               if (request.equals(ReplicationConstants.REPL_MANAGER_REGISTER)) {
                  I_Info info = new ClientPropertiesInfo(updateQos.getClientProperties());
                  register(senderSession.getRelativeName(), replId, info);
               }
               else if (request.equals(ReplicationConstants.REPL_MANAGER_UNREGISTER)) {
                  unregister(senderSession.getRelativeName(), replId);
               }
               else {
                  log.warning("The Replication Manager does not recognize the command '" + request + "' it only knows 'REGISTER' and 'UNREGISTER'");
               }
            }
         }
         return "OK";
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         log.severe("Throwable occured in the update method of ReplManagerPlugin");
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_HOLDBACK, "XmlBlasterPublisher.update", "user exception", ex);
      }
   }
   

   
   // enforced by I_MsgDispatchInterceptor 
   
   /**
    * This method is invoked always so see sessionAdded javadoc.
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#addDispatchManager(org.xmlBlaster.util.dispatch.DispatchManager)
    */
   public void addDispatchManager(DispatchManager dispatchManager) {
      try {
         SessionName sessionName = dispatchManager.getSessionName();
         if (sessionName == null) {
            log.severe("The sessionName is null: "   + dispatchManager.toXml(""));
            Thread.dumpStack();
         }
         else {
            log.info("Adding dispatch Manager for '" + sessionName + "'");
            String relativeSessionName = sessionName.getRelativeName();
            I_ReplSlave slave = new ReplSlave(this.global, this.pool, this, relativeSessionName);
            synchronized (this.replSlaveMap) {
               this.replSlaveMap.put(relativeSessionName, slave);
            }
         }
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }

   public String getInstanceName() {
      return this.instanceName;
   }
   
   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#doActivate(org.xmlBlaster.util.dispatch.DispatchManager)
    */
   public boolean doActivate(DispatchManager dispatchManager) {
      if (dispatchManager.getDispatchConnectionsHandler().isPolling()) {
         log.fine("Can't send message as connection is lost and we are polling");
         return false;
      }
      return true;
   }


   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#handleNextMessages(org.xmlBlaster.util.dispatch.DispatchManager, java.util.ArrayList)
    */
   public ArrayList handleNextMessages(DispatchManager dispatchManager, ArrayList pushEntries) throws XmlBlasterException {

      if (!this.initialized) {
         synchronized(this) {
            if (!this.initialized) {
               log.warning("too early to get messages since not initialized yet");
               try {
                  Thread.sleep(500L);
               }
               catch (Throwable ex) {
                  ex.printStackTrace();
               }
            }
         }
      }
      
      if (pushEntries != null) {
         log.warning("got " + pushEntries.size() + " entries in Dispatcher Sync mode (happens on communication Exceptions. Will ignore this");
         // return pushEntries;
         return null;
      }

      // take messages from queue (none blocking) ...
      I_Queue cbQueue = dispatchManager.getQueue();
      // ArrayList entryList = cbQueue.peekSamePriority(-1, this.maxSize);
      // TODO: FIXME: Is 1 stable?
      ArrayList entryList = cbQueue.peekSamePriority(1, this.maxSize);
      log.info("handleNextMessages invoked with '" + entryList.size() + " entries");

      // filter expired entries etc. ...
      // you should always call this method after taking messages from queue
      entryList = dispatchManager.prepareMsgsFromQueue(entryList);
      log.info("handleNextMessages after cleaning up with '" + entryList.size() + " entries");

      I_ReplSlave slave = null;
      synchronized (this.replSlaveMap) {
         slave = (I_ReplSlave)this.replSlaveMap.get(dispatchManager.getSessionName().getRelativeName());
      }
      if (slave == null) {
         log.warning("could not find a slave for replication client '" + dispatchManager.getSessionName().getRelativeName() + "'");
         return entryList;
      }
      try {
         return slave.check(entryList, cbQueue);
      }
      catch (Exception ex) {
         if (ex instanceof XmlBlasterException)
            throw (XmlBlasterException)ex;
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "exception occured when filtering replication messages", "", ex);
      }
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#initialize(org.xmlBlaster.util.Global, java.lang.String)
    */
   public void initialize(Global glob, String typeVersion) throws XmlBlasterException {
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#isShutdown()
    */
   public synchronized boolean isShutdown() {
      return this.shutdown;
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#shutdown(org.xmlBlaster.util.dispatch.DispatchManager)
    */
   public synchronized void shutdown(DispatchManager dispatchManager) throws XmlBlasterException {
      I_ReplSlave slave = null;
      String name = dispatchManager.getSessionName().getRelativeName();
      synchronized (this.replSlaveMap) {
         slave = (I_ReplSlave)this.replSlaveMap.remove(name);
      }
      if (slave != null) {
         try {
            // slave.shutdown();
         }
         catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Exception occured when shutting down slave '" + name + "'");
         }
      }
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#toXml(java.lang.String)
    */
   public String toXml(String extraOffset) {
      return "";
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#usage()
    */
   public String usage() {
      return "";
   }

   /**
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toAlive(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toAlive(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }

   /**
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toDead(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum, java.lang.String)
    */
   public void toDead(DispatchManager dispatchManager, ConnectionStateEnum oldState, String errorText) {
   }

   /**
    * @see org.xmlBlaster.util.dispatch.I_ConnectionStatusListener#toPolling(org.xmlBlaster.util.dispatch.DispatchManager, org.xmlBlaster.util.dispatch.ConnectionStateEnum)
    */
   public void toPolling(DispatchManager dispatchManager, ConnectionStateEnum oldState) {
   }

   private synchronized void registerSqlStatement(String replPrefix, String reqId, String statement) throws Exception {
      log.info("registering statement '" + replPrefix + "-" + reqId + "' for statement '" + statement + "'");
      ArrayList slaves = new ArrayList();
      Iterator iter = this.replSlaveMap.keySet().iterator();
      synchronized (this.replSlaveMap) {
         while (iter.hasNext()) {
            Object key = iter.next();
            ReplSlave replSlave = (ReplSlave)this.replSlaveMap.get(key);
            String tmpPrefix = replSlave.getReplPrefix();
            if (replPrefix.equals(tmpPrefix)) {
               slaves.add(key);
            }
         }
      }
      SqlStatement sqlStatement = new SqlStatement(replPrefix, reqId, statement, slaves);
      this.sqlStatementMap.put(reqId, sqlStatement);
      String instanceName = getInstanceName() + ContextNode.SEP + replPrefix + ContextNode.SEP + reqId;
      ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, instanceName, this.global.getContextNode());
      sqlStatement.setHandle(this.global.registerMBean(contextNode, sqlStatement));
   }

   private synchronized void unregisterSqlStatement(String reqId) {
      log.info("unregistering statement '" + reqId + "'");
      SqlStatement sqlStatement = (SqlStatement)this.sqlStatementMap.remove(reqId);
      if (sqlStatement == null)
         log.warning("The sql statement with request id '" + reqId + "' was not found in the map, can not unregister it");
      else
         log.info("The sql statement with request id '" + reqId + "' will be unregistered now");
      this.global.unregisterMBean(sqlStatement.getHandle());
   }
   
   public void removeSqlStatement(String statementId) {
      unregisterSqlStatement(statementId);
   }
   
   private void sendBroadcastRequest(String replicationPrefix, String sql, boolean isHighPrio, String requestId) throws Exception {
      if (replicationPrefix == null)
         throw new Exception("executeSql: the replication id is null. Can not perform it.");
      if (sql == null)
         throw new Exception("executeSql: the sql statement to perform on  '" + replicationPrefix + "' is null. Can not perform it.");

      I_Info individualInfo = (I_Info)this.replications.get(replicationPrefix);
      if (individualInfo == null)
         throw new Exception("executeSql: the replication with Id='" + replicationPrefix + "' was not found (has not been registered). Allowed ones are : " + getReplications());
      
      log.info("Sending Broadcast request for repl='" + replicationPrefix + "' and statement='" + sql + "' and requestId='" + requestId + "'");
      String dbWatcherSessionId = individualInfo.get(SENDER_SESSION, null);
      registerSqlStatement(replicationPrefix, requestId, sql);
      
      log.info("Broadcasting sql statement '" + sql + "' for master '" + replicationPrefix + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.global);
      Destination destination = new Destination(new SessionName(this.global, dbWatcherSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.setPersistent(true);
      if (isHighPrio)
         pubQos.setPriority(PriorityEnum.HIGH8_PRIORITY);
      // pubQos.addClientProperty(ReplicationConstants.ACTION_ATTR, ReplicationConstants.STATEMENT_ACTION);
      pubQos.addClientProperty(ReplicationConstants.STATEMENT_ATTR, sql);
      pubQos.addClientProperty(ReplicationConstants.STATEMENT_PRIO_ATTR, isHighPrio);
      pubQos.addClientProperty(ReplicationConstants.STATEMENT_ID_ATTR, requestId);
      pubQos.addClientProperty(ReplicationConstants.SQL_TOPIC_ATTR, this.sqlTopic);
      if (this.maxResponseEntries > -1L) {
         pubQos.addClientProperty(ReplicationConstants.MAX_ENTRIES_ATTR, this.maxResponseEntries);
         log.info("Be aware that the number of entries in the result set will be limited to '" + this.maxResponseEntries + "'. To change this use 'replication.sqlMaxEntries'");
      }
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.STATEMENT_ACTION.getBytes(), pubQos);
      conn.publish(msg);
      
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.impl.ReplManagerPluginMBean#broadcastSql(java.lang.String, java.lang.String)
    */
   public void broadcastSql(String repl, String sql) throws Exception {
      final boolean highPrio = true;
      String requestId = "" + new Timestamp().getTimestamp();
      sendBroadcastRequest(repl, sql, highPrio, requestId);
   }

   /**
    * Convenience method to determine if a connect Qos is for us, i.e. if they have
    * defined us as the DispatchPlugin in their connect qos.
    * 
    * @param connQos
    * @return
    */
   private final boolean hasUsAsDispatchPlugin(ConnectQosServer connQos) {
      if (connQos == null)
         return false;
      CallbackAddress cbAddr = connQos.getData().getCurrentCallbackAddress();
      if (cbAddr == null) {
         log.info("entry '" + connQos.toXml() + "' has no callback address defined");
         return false;
      }
      String dispatchPluginName = cbAddr.getDispatchPlugin();
      if (dispatchPluginName == null)
         return false;
      String ownName = getType() + "," + getVersion();
      if (ownName.equals(dispatchPluginName))
         return true;
      return false;
   }
   
   // For I_ClientListener ...

   /**
    * This code is called always and could be avoided since addManager is always called .
    * TODO Remove this functionality.
    * 
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public synchronized void sessionAdded(ClientEvent e) throws XmlBlasterException {
      if (e == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with null event object");
      ConnectQosServer connQos = e.getConnectQos();
      if (!hasUsAsDispatchPlugin(connQos))
         return;
      log.fine("Connecting with qos : " + connQos.toXml());
      String sessionName = e.getSessionInfo().getSessionName().getRelativeName();
      log.info("addition of session for '" + sessionName +"' occured");
      synchronized (this.replSlaveMap) {
         if (!this.replSlaveMap.containsKey(sessionName)) {
            I_ReplSlave slave = new ReplSlave(this.global, this.pool, this, sessionName);
            this.replSlaveMap.put(sessionName, slave);
         }
      }
   }

   /**
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionPreRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionPreRemoved(ClientEvent e) throws XmlBlasterException {
      if (e == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with null event object");
      ConnectQosServer connQos = e.getConnectQos();
      if (!hasUsAsDispatchPlugin(connQos))
         return;
      String sessionName = e.getSessionInfo().getSessionName().getRelativeName();
      log.info("removal of session for '" + sessionName +"' occured");
      synchronized (this.replSlaveMap) {
         if (!this.replSlaveMap.containsKey(sessionName))
            log.warning("The slave '" + sessionName + "' is not registered.");
         else {
            I_ReplSlave slave = (I_ReplSlave)this.replSlaveMap.remove(sessionName);
            if (slave != null) {
               try {
                  slave.shutdown();
               }
               catch (Exception ex) {
                  log.severe("Could not shut down the slave '" + sessionName + "' properly");
                  ex.printStackTrace();
               }
            }
         }
      }
   }

   /**
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void sessionRemoved(ClientEvent e) throws XmlBlasterException {
   }

   /**
    * @see org.xmlBlaster.authentication.I_ClientListener#subjectAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public void subjectAdded(ClientEvent e) throws XmlBlasterException {
   }

   /**
    * @see org.xmlBlaster.authentication.I_ClientListener#subjectRemoved(org.xmlBlaster.authentication.ClientEvent)
    */
   public void subjectRemoved(ClientEvent e) throws XmlBlasterException {
   }

   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#getPriority()
    */
   public Integer getPriority() {
      // TODO Check if the priority is correct
      return new Integer(100);
   }

   /**
    * To make it simpler one could think to put this method together with sessionAdded. 
    * This is however not possible since at the time the initiateReplication is invoked,
    * the subcription is done for the first time. However if sessionAdded was not invoked
    * previously, there would no be any chance to know that this is wanting to subscribe.
    * 
    * It checks if the event is for one of our guys and dispatches the call to them
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public synchronized void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException {
      ConnectQosServer connQos = e.getSubscriptionInfo().getSessionInfo().getConnectQos();
      if (!hasUsAsDispatchPlugin(connQos))
         return;
      
      String relativeSessionName = e.getSubscriptionInfo().getSessionInfo().getSessionName().getRelativeName();
      log.info("addition of subscription for '" + relativeSessionName +"' occured");
      
      I_ReplSlave slave = null;
      synchronized (this.replSlaveMap) {
          slave = (I_ReplSlave)this.replSlaveMap.get(relativeSessionName);
      }
      
      if (slave != null) {
         Map clientProperties = e.getSubscriptionInfo().getSubscribeQosServer().getData().getClientProperties();
         try {
            slave.init(new ClientPropertiesInfo(clientProperties));
         }
         catch (Exception ex) {
            throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.subscriptionAdd", "", ex);
         }
      }
      else
         log.severe("Could not find nor create slave '" + relativeSessionName + "'");
   }

   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException {
      if (e == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with null event object");
      ConnectQosServer connQos = e.getSubscriptionInfo().getSessionInfo().getConnectQos();
      if (!hasUsAsDispatchPlugin(connQos))
         return;
      String sessionName = e.getSubscriptionInfo().getSessionInfo().getSessionName().getRelativeName();
      log.info("removal of one subscription for '" + sessionName +"' occured");
      /*
      synchronized (this.replSlaveMap) {
         if (!this.replSlaveMap.containsKey(sessionName))
            log.warning("The slave '" + sessionName + "' is not registered.");
         else {
            this.replSlaveMap.remove(sessionName);
         }
      }
      */
   }

   void setEngineGlobalProperty(String key, String val) {
      org.xmlBlaster.engine.Global engineGlobal = (org.xmlBlaster.engine.Global)this.global.getObjectEntry(ORIGINAL_ENGINE_GLOBAL);
      if (engineGlobal != null)
         engineGlobal.getProperty().getProperties().setProperty(key, val);
   }

   
   public final String recreateTriggers(String replPrefix) throws Exception {
      // sending the cancel op to the DbWatcher
      log.info("'will recreate triggers for source '" + replPrefix + "'");
      
      I_Info individualInfo = (I_Info)this.replications.get(replPrefix);
      if (individualInfo != null) {
         String dbWatcherSessionId = individualInfo.get(SENDER_SESSION, null);
         if (dbWatcherSessionId == null)
            throw new Exception("The replication source with replication.prefix='" +  replPrefix + "' had no '_senderSession' attribute set in its configuration");

         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         // no oid for this ptp message 
         PublishKey pubKey = new PublishKey(this.global);
         Destination destination = new Destination(new SessionName(this.global, dbWatcherSessionId));
         destination.forceQueuing(true);
         PublishQos pubQos = new PublishQos(this.global, destination);
         pubQos.setPersistent(false);
         MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_RECREATE_TRIGGERS.getBytes(), pubQos);
         conn.publish(msg);
         return "Recreate Triggers for '" + replPrefix + "' is ongoing now";
      }
      else
         throw new Exception("Could not find a replication source with replication.prefix='" + replPrefix + "'");
   }
   

   private static void mainUsage() {
      System.err.println("You must invoke at least java org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin -cmd insert|delete -requestId someId -replication.prefix somePrefix < filename");
      System.exit(-1);
   }
   
   public static void main(String[] args) {
      try {
         Global global = new Global(args);
         I_XmlBlasterAccess conn = global.getXmlBlasterAccess();
         ConnectQos connectQos = new ConnectQos(global);
         conn.connect(connectQos, new ReplManagerPlugin()); // just a fake
         
         String cmd = global.getProperty().get("cmd", (String)null);
         if (cmd == null)
            mainUsage();
         
         String requestId = global.getProperty().get("requestId", (String)null);
         if (requestId == null)
            mainUsage();
         
         int count = Integer.parseInt(requestId.trim());
         
         String repl = global.getProperty().get("replication.prefix", (String)null);
         if (repl == null)
            mainUsage();
         
         PublishKey pubKey = new PublishKey(global, "broadcastChecker");

         BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
         String line = null;
         while ( (line=br.readLine()) != null) {
            PublishQos pubQos = new PublishQos(global,new Destination(new SessionName(global, SESSION_ID)));
            requestId = "" + count++;
            MsgUnit msg = null;
            if (cmd.equals("insert")) {
               pubQos.addClientProperty("_command", "broadcastSql");
               pubQos.addClientProperty("requestId", requestId);
               pubQos.addClientProperty("replication.prefix", repl);
               msg = new MsgUnit(pubKey, line.trim().getBytes(), pubQos);
            }
            else {
               pubQos.addClientProperty("_command", "removeBroadcast");
               msg = new MsgUnit(pubKey, requestId.getBytes(), pubQos);
            }
            if (line != null && line.trim().length() > 0) {
               conn.publish(msg);
            }
         }
         conn.erase(new EraseKey(global, "broadcastChecker"), new EraseQos(global));
         conn.disconnect(new DisconnectQos(global));
         br.close();
      }
      catch (Throwable ex) {
         ex.printStackTrace();
      }
   }
   
}
