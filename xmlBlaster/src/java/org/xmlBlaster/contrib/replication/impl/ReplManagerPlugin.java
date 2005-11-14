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
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.replication.I_ReplSlave;
import org.xmlBlaster.contrib.replication.ReplSlave;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
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
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.queue.I_Queue;

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
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class ReplManagerPlugin extends GlobalInfo implements ReplManagerPluginMBean, I_Callback, I_MsgDispatchInterceptor, I_ClientListener, I_SubscriptionListener {
   
   public final static String SESSION_ID = "replManager/1";
   private static Logger log = Logger.getLogger(ReplManagerPlugin.class.getName());
   // private I_ChangePublisher publisher;
   private Map replications;
   private Object mbeanHandle;
   private String user = "replManager";
   private String password = "secret";
   private Map replSlaveMap;
   private boolean shutdown;
   private boolean initialized;
   
   private static int debugInstanceNum;
   private String instanceName;
   private long maxSize = 999999L;
   private String sqlTopic;
   private long maxResponseEntries; 
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public ReplManagerPlugin() {
      super(new String[] {});
      this.replications = new TreeMap();
      this.replSlaveMap = new TreeMap();
   }
   
   /**
    * Never returns null. It returns a list of keys identifying the slaves using the replication 
    * manager.
    * @return
    */
   public String[] getSlaves() {
      return (String[])this.replSlaveMap.keySet().toArray(new String[this.replSlaveMap.size()]);
   }
   
   /**
    * Never returns null. It returns a list of keys identifying the ongoing replications.
    * @return
    */
   public String[] getReplications() {
      return (String[])this.replications.keySet().toArray(new String[this.replications.size()]);
   }
   
   public String getType() {
      return "ReplManager";
   }
   
   public String getVersion() {
      return "1.0";
   }
   
   /**
    * Intiates the replication for the given slave.
    * 
    * @param slaveSessionName
    * @param replicationKey
    * @throws Exception
    */
   public void initiateReplication(String slaveSessionName, String replicationKey) throws Exception {
      try {
         log.info("initiateReplication invoked for slave '" + slaveSessionName + "' and on replication '" + replicationKey + "'");

         I_Info individualInfo = (I_Info)this.replications.get(replicationKey);
         if (individualInfo != null) {
            individualInfo.putObject("org.xmlBlaster.engine.Global", this.global);
            I_ReplSlave slave = null;
            synchronized (this.replSlaveMap) {
               slave = (I_ReplSlave)this.replSlaveMap.get(slaveSessionName);
            }
            if (slave != null) {
               individualInfo.put("_replName", replicationKey);
               slave.run(individualInfo);
            }
            else {
               StringBuffer buf = new StringBuffer();
               String[] slaves = getSlaves();
               for (int i=0; i < slaves.length; i++) {
                  if (i > 0)
                     buf.append(",");
                  buf.append(slaves);
               }
               throw new Exception("the replication slave '" + slaveSessionName + "' was not found among the list of slaves which is '" + buf.toString() + "'");
            }
         }
         else {
            String[] repl = this.getReplications();
            StringBuffer buf = new StringBuffer();
            for (int i=0; i < repl.length; i++)
               buf.append(repl[i]).append(" ");
            throw new Exception("initiateReplication failed for '" + slaveSessionName + "' with replication key '" + replicationKey + "' since not known. Known are '" + buf.toString() + "'");
         }
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
            debugInstanceNum++;
            this.instanceName = "replication" + debugInstanceNum;
         }
         
         ContextNode contextNode = new ContextNode(this.global, ContextNode.CONTRIB_MARKER_TAG,
               instanceName, this.global.getContextNode());
         this.mbeanHandle = this.global.registerMBean(contextNode, this);
         // this.global.getJmxWrapper().registerMBean(contextNode, this);
         
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         ConnectQos connectQos = new ConnectQos(this.global, this.user, this.password);
         connectQos.setPersistent(true);
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

         this.sqlTopic = this.get("replication.sqlTopic", null);
         if (this.sqlTopic != null) {
            SubscribeKey subKey = new SubscribeKey(this.global, this.sqlTopic);
            SubscribeQos subQos = new SubscribeQos(this.global);
            subQos.setPersistent(true);
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
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         if (this.sqlTopic != null) {
            UnSubscribeKey key = new UnSubscribeKey(this.global, this.sqlTopic);
            conn.unSubscribe(key, new UnSubscribeQos(this.global));
         }
         conn.disconnect(new DisconnectQos(this.global));
         this.replications.clear();
         this.replSlaveMap.clear();
         
         getEngineGlobal(this.global).getPluginRegistry().unRegister(getType() + "," + getVersion());
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
    * @param senderSession The session requesting this registration. This is needed
    * to reply to the right requestor.
    * 
    * @param replId
    * @param info
    */
   public synchronized void register(String senderSession, String replId, I_Info info) {
      I_Info oldInfo = (I_Info)this.replications.get(replId);
      info.put("_senderSession", senderSession);
      if (oldInfo != null) {
         String oldSenderSession = oldInfo.get("_senderSession", senderSession);
         if (oldSenderSession.equals(senderSession)) {
            log.info("register '" + replId + "' by senderSession='" + senderSession + "' will overwrite old registration done previously");
            this.replications.put(replId, info);
         }
         else {
            log.warning("register '" + replId + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Please remove first old registration" + "");
         }
      }
      else
         this.replications.put(replId, info);
   }
   
   public synchronized void unregister(String senderSession, String replId) {
      I_Info oldInfo = (I_Info)this.replications.get(replId);
      if (oldInfo == null)
         log.info("unregister '" + replId + "' by senderSession='" + senderSession + "' is ignored since there is no such registration done");
      else {
         String oldSenderSession = oldInfo.get("_senderSession", senderSession);
         if (oldSenderSession.equals(senderSession)) {
            this.replications.remove(replId);
         }
         else {
            log.warning("unregister '" + replId + "' by senderSession='" + senderSession + "' was not done since there is a registration done by '" + oldSenderSession + "'. Please do it with the correct Session");
         }
      }
   }
   
   /**
    * It becomes events from all ReplicationConverter instances which want to register themselves for
    * administration of initial updates.
    * 
    * @see org.xmlBlaster.client.I_Callback#update(java.lang.String, org.xmlBlaster.client.key.UpdateKey, byte[], org.xmlBlaster.client.qos.UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      SessionName senderSession = updateQos.getSender();
      String request = updateQos.getClientProperty("_command", "");
      log.info("The master Replicator with session '" + senderSession.getRelativeName() + "' is sending '" + request + "'");

      if (this.sqlTopic != null && updateKey.getOid().equals(this.sqlTopic)) {
         String sender = updateQos.getSender().getRelativeName();
         
         I_ReplSlave slave = (I_ReplSlave)this.replSlaveMap.get(sender);
         if (slave == null) {
            StringBuffer buf = new StringBuffer();
            String[] slaves = getSlaves();
            for (int i=0; i < slaves.length; i++) {
               if (i > 0)
                  buf.append(",");
               buf.append(slaves);
            }
            log.warning("Update data from SQL request came from user '" + sender + "' but the user is not registered. Registered users: " + buf.toString());
         }
         else {
            log.info("Update data from SQL request came from user '" + sender + "'");
            slave.setSqlResponse(new String(content));
         }
         
               
               
               
      }
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
      else {
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
   

   
   // enforced by I_MsgDispatchInterceptor 
   
   /**
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
            String relativeSessionName = sessionName.getRelativeName();
            I_ReplSlave slave = new ReplSlave(this.global, this.instanceName, relativeSessionName);
            synchronized (this.replSlaveMap) {
               this.replSlaveMap.put(relativeSessionName, slave);
            }
         }
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }

   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#doActivate(org.xmlBlaster.util.dispatch.DispatchManager)
    */
   public boolean doActivate(DispatchManager dispatchManager) {
      return true;
   }


   /**
    * @see org.xmlBlaster.util.dispatch.plugins.I_MsgDispatchInterceptor#handleNextMessages(org.xmlBlaster.util.dispatch.DispatchManager, java.util.ArrayList)
    */
   public ArrayList handleNextMessages(DispatchManager dispatchManager, ArrayList pushEntries) throws XmlBlasterException {
      if (pushEntries != null) {
         log.warning("got " + pushEntries.size() + " pushEntries. Will return them as is");
         return pushEntries;
      }

      // take messages from queue (none blocking) ...
      I_Queue cbQueue = dispatchManager.getQueue();
      // ArrayList entryList = cbQueue.peekSamePriority(-1, this.maxSize);
      ArrayList entryList = cbQueue.peekSamePriority(2, this.maxSize);
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

   /**
    * @see org.xmlBlaster.contrib.replication.impl.ReplManagerPluginMBean#broadcastSql(java.lang.String, java.lang.String)
    */
   public void broadcastSql(String repl, String sql, boolean highPrio) throws Exception {
      if (repl == null)
         throw new Exception("executeSql: the replication id is null. Can not perform it.");
      if (sql == null)
         throw new Exception("executeSql: the sql statement to perform on  '" + repl + "' is null. Can not perform it.");
      
      I_Info dbWatcherInfo = (I_Info)this.replications.get(repl);
      if (dbWatcherInfo == null) {
         StringBuffer buf = new StringBuffer();
         String[] repls = getReplications();
         for (int i=0; i < repls.length; i++)
            buf.append(repls[i]).append(" ");
         throw new Exception("executeSql: the replication with Id='" + repl + "' was not found (has not been registered). Allowed ones are : " + buf.toString());
      }
      
      String oid = dbWatcherInfo.get("mom.topicName", null);
      if (oid == null) {
         StringBuffer buf = new StringBuffer();
         Iterator iter = dbWatcherInfo.getKeys().iterator();
         while (iter.hasNext())
            buf.append(iter.next()).append(" ");
         throw new Exception("executeSql: the replication with Id='" + repl + "' did not contain a property 'mom.topicName' which is needed. Found properties where: " + buf.toString());
      }
      
      PropertiesInfo tmpInfo = new PropertiesInfo(new Properties());
      SqlInfo sqlInfo = new SqlInfo(tmpInfo);
      sqlInfo.getDescription().setAttribute(ReplicationConstants.ACTION_ATTR, ReplicationConstants.STATEMENT_ACTION);
      sqlInfo.getDescription().setAttribute(ReplicationConstants.STATEMENT_ATTR, sql);
      
      PublishKey key = new PublishKey(this.global, oid);
      PublishQos qos = new PublishQos(this.global);
      qos.setPersistent(true);
      if (highPrio)
         qos.setPriority(PriorityEnum.HIGH_PRIORITY);
      if (this.maxResponseEntries > -1L) {
         qos.addClientProperty(ReplicationConstants.MAX_ENTRIES_ATTR, this.maxResponseEntries);
         log.info("Be aware that the number of entries in the result set will be limited to '" + this.maxResponseEntries + "'. To change this use 'replication.sqlMaxEntries'");
      }
      // reset all responses ....
      synchronized (this.replSlaveMap) {
         String[] slaves = getSlaves();
         for (int i=0; i < slaves.length; i++) {
            I_ReplSlave slave = (I_ReplSlave)this.replSlaveMap.get(slaves[i]);
            slave.setSqlResponse("");
         }
      }
      MsgUnit msgUnit = new MsgUnit(key, sqlInfo.toXml("").getBytes(), qos);
      this.global.getXmlBlasterAccess().publish(msgUnit);
      
   }

   // For I_ClientListener ...

   /**
    * @see org.xmlBlaster.authentication.I_ClientListener#sessionAdded(org.xmlBlaster.authentication.ClientEvent)
    */
   public synchronized void sessionAdded(ClientEvent e) throws XmlBlasterException {
      if (e == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with null event object");
      ConnectQosServer connQos = e.getConnectQos();
      if (connQos == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with empty connenctQos event object");
      CallbackAddress cbAddr = connQos.getData().getCurrentCallbackAddress();
      if (cbAddr == null) {
         log.info("entry '" + connQos.toXml() + "' has no callback address defined");
         return;
      }
      
      String dispatchPluginName = cbAddr.getDispatchPlugin();
      if (dispatchPluginName != null) {
         String ownName = getType() + "," + getVersion();
         if (ownName.equals(dispatchPluginName)) {
            String sessionName = e.getSessionInfo().getSessionName().getRelativeName();
            synchronized (this.replSlaveMap) {
               if (this.replSlaveMap.containsKey(sessionName))
                  log.warning("The slave '" + sessionName + "' is already registered.");
               else {
                  I_ReplSlave slave = new ReplSlave(this.global, this.instanceName, sessionName);
                  this.replSlaveMap.put(sessionName, slave);
               }
            }
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
      if (connQos == null)
         throw new XmlBlasterException(this.global, ErrorCode.INTERNAL_UNKNOWN, "ReplManagerPlugin.sessionAdded with empty connenctQos event object");
      CallbackAddress cbAddr = connQos.getData().getCurrentCallbackAddress();
      if (cbAddr == null) {
         log.info("entry '" + connQos.toXml() + "' has no callback address defined");
         return;
      }
      
      String dispatchPluginName = cbAddr.getDispatchPlugin();
      if (dispatchPluginName != null) {
         String ownName = getType() + "," + getVersion();
         if (ownName.equals(dispatchPluginName)) {
            String sessionName = e.getSessionInfo().getSessionName().getRelativeName();
            synchronized (this.replSlaveMap) {
               if (!this.replSlaveMap.containsKey(sessionName))
                  log.warning("The slave '" + sessionName + "' is not registered.");
               else {
                  this.replSlaveMap.remove(sessionName);
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
      return new Integer(1);
   }

   /**
    * It checks if the event is for one of our guys and dispatches the call to them
    * 
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionAdd(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public synchronized void subscriptionAdd(SubscriptionEvent e) throws XmlBlasterException {
      String relativeName = e.getSubscriptionInfo().getSessionInfo().getSessionName().getRelativeName();
      
      I_ReplSlave slave = null;
      synchronized (this.replSlaveMap) {
         slave = (I_ReplSlave)this.replSlaveMap.get(relativeName);
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
   }

   /**
    * @see org.xmlBlaster.engine.I_SubscriptionListener#subscriptionRemove(org.xmlBlaster.engine.SubscriptionEvent)
    */
   public void subscriptionRemove(SubscriptionEvent e) throws XmlBlasterException {
      String relativeName = e.getSubscriptionInfo().getSessionInfo().getSessionName().getRelativeName();
      
      I_ReplSlave slave = null;
      synchronized (this.replSlaveMap) {
         slave = (I_ReplSlave)this.replSlaveMap.get(relativeName);
      }
      if (slave != null) {
         try {
            slave.shutdown();
         }
         catch (Exception ex) {
            ex.printStackTrace();
            log.severe("Exception occured when shutting down slave '" + relativeName + "'");
         }
      }
   }

   
   // Subscription Listener here 
   
   
   
   
   
}
