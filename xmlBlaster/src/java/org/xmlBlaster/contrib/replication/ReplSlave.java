/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.Map;
import java.util.logging.Logger;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.mime.I_AccessFilter;
import org.xmlBlaster.engine.mime.Query;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * ReplSlave
 * 
 * Used Topics:
 * <ul>
 *    <li><b>com.avitech-ag.repl.${replName}.data</b><br/>
 *        This is the topic used to send the replication data to the slaves.
 *    </li>
 *    <li><b>com.avitech-ag.repl.${replName}.status</b><br/>
 *        This is the topic used to send the replication data to the slaves.
 *    </li>
 *    <li></li>
 * </ul>   
 *  

 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class ReplSlave implements I_ReplSlave, I_AccessFilter, I_Callback, I_Plugin {

   private static Logger log = Logger.getLogger(ReplSlave.class.getName());
   private String slaveSessionId;
   private String pluginName;
   private String statusTopic;
   private String dataTopic;
   private Global utilGlobal;
   private String masterSessionId;
   boolean initialized; 
   private String pluginVersion = "1.0";
   private long minReplKey;
   private long maxReplKey;
   
   public ReplSlave(String slaveSessionId, String replName, I_Info info) throws Exception {
      this.slaveSessionId = slaveSessionId;
      this.pluginName = "replSlave" + replName + slaveSessionId;
      this.initialized = false;
      this.dataTopic = info.get("mom.topicName", "replication." + replName);
      this.statusTopic = info.get("mom.statusTopicName", this.dataTopic + ".status");
      this.masterSessionId = info.get("_senderSession", null);
      if (this.masterSessionId == null)
         throw new Exception("ReplSlave '" + this.pluginName + "' constructor: the master Session Id (which is passed in the properties as '_senderSession' are not found. Can not continue with initial update");
      // this.utilGlobal = (Global)info.getObject("org.xmlBlaster.engine.Global");

      Global tmpGlobal = (Global)info.getObject("org.xmlBlaster.engine.Global");
      this.utilGlobal = tmpGlobal.getClone(tmpGlobal.getNativeConnectArgs());

      this.utilGlobal.addObjectEntry("ServerNodeScope", tmpGlobal.getObjectEntry("ServerNodeScope"));
      // add the original Global in case the extending classes need it
      String key = ReplManagerPlugin.ORIGINAL_ENGINE_GLOBAL;
      Object obj = tmpGlobal.getObjectEntry(key);
      if (obj != null)
         this.utilGlobal.addObjectEntry(key, obj);
      
      init(this.utilGlobal, null);
   }

   private org.xmlBlaster.engine.Global getEngineGlobal(Global glob) throws XmlBlasterException {
      org.xmlBlaster.engine.Global engineGlobal = null;
      if (glob == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "ReplSlave.init", "The util Global passed was null. Can not continue");
      if (glob instanceof org.xmlBlaster.engine.Global)
         engineGlobal = (org.xmlBlaster.engine.Global)glob;
      else
         engineGlobal = (org.xmlBlaster.engine.Global)glob.getObjectEntry(ReplManagerPlugin.ORIGINAL_ENGINE_GLOBAL);
      if (engineGlobal == null)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, "ReplSlave.init", "The engine global could not be retrieved. Can not continue");
      return engineGlobal;
   }
   
   public void run() throws Exception {
      prepareForRequest();
      requestInitialData();
      
   }
   
   /**
    * This is the first step in the process of requesting the initial Data.
    * <ul>
    *   <li>It clears the callback queue of the real slave</li>
    *   <li>It sends a message to the real slave to inform him that 
    *       a new initial update has been initiated. This is a PtP 
    *       message with a well defined topic, so administrators can 
    *       subscribe to it too.
    *   </li>
    *   <li>It then deactivates the callback dispatcher of the real slave</li>
    *   <li>makes a persistent subscription on behalf of the real slave
    *       by passing as a mime access filter an identifier for himself.
    *   </li>
    * </ul>
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#prepareForRequest()
    */
   public void prepareForRequest() throws Exception {
      if (!this.initialized)
         throw new Exception("prepareForRequest: '" + this.pluginName + "' has not been initialized properly or is already shutdown, check your logs");
      log.info("prepareForRequest");
      I_AdminSession session = getSession();
      long clearedMsg = session.clearCallbackQueue();
      log.info("clearing of callback queue before initiating: '" + clearedMsg + "' where removed since obsolete");

      sendStatusInformation("dbInitStart");
      session.setDispatcherActive(false); // stop the dispatcher
      
      SubscribeQos subQos = new SubscribeQos(this.utilGlobal);
      subQos.setMultiSubscribe(false);
      subQos.setWantInitialUpdate(false);
      
      AccessFilterQos accessFilterQos = new AccessFilterQos(this.utilGlobal, this.pluginName, this.pluginVersion, new Query(this.utilGlobal, ""));
      subQos.addAccessFilter(accessFilterQos);
      session.subscribe(this.dataTopic, subQos.toXml());
   }
   
   private void sendStatusInformation(String status) throws Exception {
      log.info("send status information '" + status + "'");
      I_XmlBlasterAccess conn = this.utilGlobal.getXmlBlasterAccess();
      PublishKey pubKey = new PublishKey(this.utilGlobal, this.statusTopic);
      Destination destination = new Destination(new SessionName(this.utilGlobal, this.slaveSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.utilGlobal, destination);
      pubQos.setPersistent(true);
      MsgUnit msg = new MsgUnit(pubKey, status.getBytes(), pubQos);
      conn.publish(msg);
   }
   
   /**
    * Sends a PtP message to the responsible for the initial update (which is the
    * DbWatcher or an object running in the DbWatcher jvm) telling a new initial
    * update has to be initiating. 
    * 
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#requestInitialData()
    */
   public void requestInitialData() throws Exception {
      log.info(this.pluginName + " sends now an initial update request to the Master '" + this.masterSessionId + "'");
      I_XmlBlasterAccess conn = this.utilGlobal.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.utilGlobal);
      Destination destination = new Destination(new SessionName(this.utilGlobal, this.masterSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.utilGlobal, destination);
      pubQos.setPersistent(true);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
   }

   
   private I_AdminSession getSession() throws Exception {
      I_Authenticate auth = getEngineGlobal(this.utilGlobal).getAuthenticate();
      if (auth == null)
         throw new Exception(this.pluginName + " prepareForRequest: could not retreive the Authenticator object. Can not continue.");
      SessionName sessionName = new SessionName(this.utilGlobal, this.slaveSessionId);
      I_AdminSubject subject = auth.getSubjectInfoByName(sessionName);
      if (subject == null)
         throw new Exception(this.pluginName + " prepareForRequest: no subject (slave) found with the session name '" + this.slaveSessionId + "'");
      I_AdminSession session = subject.getSessionByPubSessionId(sessionName.getPublicSessionId());
      if (session == null)
         throw new Exception(this.pluginName + " prepareForRequest: no session '" + this.slaveSessionId + "' found. Valid sessions for this user are '" + subject.getSessionList() + "'");
      return session;
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#reactivateDestination()
    */
   public synchronized void reactivateDestination(long minReplKey, long maxReplKey) throws Exception {
      log.info("Initial Operation completed with replication key interval [" + minReplKey + "," + maxReplKey + "]");
      if (!this.initialized)
         throw new Exception("prepareForRequest: '" + this.pluginName + "' has not been initialized properly or is already shutdown, check your logs");

      this.minReplKey = minReplKey;
      this.maxReplKey = maxReplKey;

      I_AdminSession session = getSession(); 
      session.setDispatcherActive(true);
      
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#checkForDestroy(java.lang.String)
    */
   public boolean checkForDestroy(String replKey) throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public synchronized void shutdown() {
      if (!this.initialized)
         return;
      try {
         org.xmlBlaster.engine.Global engineGlobal = getEngineGlobal(this.utilGlobal);
         PluginInfo pluginInfo = new PluginInfo(engineGlobal, null, pluginName, this.pluginVersion);
         engineGlobal.getPluginRegistry().unRegister(pluginInfo.getId());
         this.initialized = false;
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_EventHandler#update(java.lang.String, byte[], java.util.Map)
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      // TODO Auto-generated method stub
   }
   
   
   // for the I_AccessFilter

   public String[] getMimeExtended() {
      return new String[] { "*" };
   }

   public String[] getMimeTypes() {
      return new String[] { "*" };
   }

   public String getName() {
      return getVersion();
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return this.pluginName;
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return this.pluginVersion;
   }

   /**
    * 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      initialize(getEngineGlobal(glob));
   }

   public synchronized void initialize(org.xmlBlaster.engine.Global engineGlobal) {
      if (this.initialized)
         return;
      String user = this.pluginName;
      String pwd = "secret";
      try {
         I_XmlBlasterAccess conn = this.utilGlobal.getXmlBlasterAccess();
         // register the plugin first (so that it can be found on the subscription)
         PluginInfo pluginInfo = new PluginInfo(engineGlobal, null, pluginName, this.pluginVersion);
         engineGlobal.getPluginRegistry().register(pluginInfo.getId(), this);
         ConnectQos connectQos = new ConnectQos(this.utilGlobal, user, pwd);
         connectQos.setPersistent(true);
         SessionName sessionName = new SessionName(this.utilGlobal, user);
         connectQos.setSessionName(sessionName);
         connectQos.setMaxSessions(100);
         conn. connect(connectQos, this);
         this.initialized = true;
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   /**
    * 
    */
   public boolean match(SessionInfo receiver, MsgUnit msgUnit, Query query) throws XmlBlasterException {
      long replKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
      if (replKey < 0L) {
         log.severe("the message unit '" + msgUnit.toXml() + "' has no '" + ReplicationConstants.REPL_KEY_ATTR + "' defined");
      }
      log.fine("repl entry '" + replKey + "' for range [" + this.minReplKey + "," + this.maxReplKey + "]");
      // TODO destroy when finished
      if (replKey > this.maxReplKey)
         return true;
      return false;
   }

   // I_Callback

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (content == null)
         content = new byte[0];
      String command = updateQos.getClientProperty("_command", (String)null);
      if ("INITIAL_DATA_RESPONSE".equals(command)) {
         long minReplKey = updateQos.getClientProperty("_minReplKey", 0L);
         long maxReplKey = updateQos.getClientProperty("_maxReplKey", 0L);
         try {
            reactivateDestination(minReplKey, maxReplKey);
         }
         catch (Exception ex) {
            log.warning("reactivateDestination encountered an exception '" + ex.getMessage());
         }
      }
      else
         log.warning("update was invoked for an unknown message '" + new String(content));
      return "OK";
   }
   
}
