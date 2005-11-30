/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.queue.I_Queue;


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
public class ReplSlave implements I_ReplSlave, ReplSlaveMBean {

   private static Logger log = Logger.getLogger(ReplSlave.class.getName());
   private String slaveSessionId;
   private String name;
   private String statusTopic;
   private String dataTopic;
   private Global global;
   // private String masterSessionId;
   boolean initialized; 
   private long minReplKey;
   private long maxReplKey;
   private int status;
   private Object mbeanHandle;
   private String sqlResponse;
   private String managerInstanceName;
   private boolean forceSending; // temporary Hack to be removed TODO
   
   public String getTopic() {
      return this.dataTopic;
   }
   
   public long getMinReplKey() {
      return this.minReplKey;
   }
   
   public long getMaxReplKey() {
      return this.maxReplKey;
   }

   public synchronized String getStatus() {
      switch (this.status) {
         case STATUS_INITIAL : return "INITIAL";
         case STATUS_TRANSITION : return "TRANSITION";
         // case STATUS_UNUSED : return "UNUSED";
         default : return "NORMAL";
      }
   }
   
   public ReplSlave(Global global, String managerInstanceName, String slaveSessionId) throws XmlBlasterException {
      this.global = global;
      this.managerInstanceName = managerInstanceName;
      this.slaveSessionId = slaveSessionId;
      // this.status = STATUS_UNUSED;
      this.status = STATUS_NORMAL;
      setStatus(this.status);
   }

   /**
    * The info comes as the client properties of the subscription Qos. Avoids double configuration.
    */
   public synchronized void init(I_Info info) throws Exception {
      // we currently allow re-init since we can serve severeal dbWatchers for one DbWriter 
      // if (this.initialized)
      //    return;
      String replName = info.get("_replName", null);
      if (replName == null) 
         throw new Exception("The replication name '_replName' has not been defined");
      this.name = "replSlave" + replName + slaveSessionId;
      this.dataTopic = info.get("mom.topicName", "replication." + replName);
      // only send status messages if it has been configured that way
      this.statusTopic = info.get("mom.statusTopicName", null);
      // this.statusTopic = info.get("mom.statusTopicName", this.dataTopic + ".status");

      //this.masterSessionId = info.get("_senderSession", null);
      //if (this.masterSessionId == null)
      //   throw new Exception("ReplSlave '" + this.name + "' constructor: the master Session Id (which is passed in the properties as '_senderSession' are not found. Can not continue with initial update");

      // this.global = (Global)info.getObject("org.xmlBlaster.engine.Global");
      // String instanceName = "replication" + ContextNode.SEP + slaveSessionId;
      
      // TODO Remove this when a better solution is found : several ReplSlaves for same Writer if data comes from several DbWatchers.
      boolean forceSending = info.getBoolean("replication.forceSending", false);
      if (forceSending)
         this.forceSending = true; 
      String instanceName = this.managerInstanceName + ContextNode.SEP + this.slaveSessionId;
      ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, instanceName,
            this.global.getContextNode());
      this.mbeanHandle = this.global.registerMBean(contextNode, this);
      this.initialized = true;
   }
   
   private void setStatus(int status) {
      String client = "client/";
      int pos = this.slaveSessionId.indexOf(client);
      if (pos < 0)
         log.warning("session name '" + this.slaveSessionId + "' does not start with '" + client + "'");
      else {
         String key = "__" + this.slaveSessionId.substring(pos + client.length());
         org.xmlBlaster.engine.Global engineGlob = this.getEngineGlobal(this.global);
         if (engineGlob == null)
            log.warning("Can not write status since no engine global found");
         else {
            log.info("setting property '" + key + "' to '" + getStatus());
            engineGlob.getProperty().getProperties().setProperty(key, getStatus());
         }
      }
   }
   
   private void setMaxReplKey(long replKey) {
      String client = "client/";
      if (this.slaveSessionId == null)
         return;
      int pos = this.slaveSessionId.indexOf(client);
      if (pos < 0)
         log.warning("session name '" + this.slaveSessionId + "' does not start with '" + client + "'");
      else {
         String key = "__" + this.slaveSessionId.substring(pos + client.length()) + "_MaxReplKey";
         org.xmlBlaster.engine.Global engineGlob = this.getEngineGlobal(this.global);
         if (engineGlob == null)
            log.warning("Can not write status since no engine global found");
         else {
            log.info("setting property '" + key + "' to '" + getMaxReplKey());
            engineGlob.getProperty().getProperties().setProperty(key, String.valueOf(getMaxReplKey()));
         }
      }
   }

   public boolean run(I_Info info, String dbWatcherSessionId) throws Exception {
      if (this.status != STATUS_NORMAL) {
         log.warning("will not start initial update request since one already ongoing for '" + this.name + "'");
         return false;
      }
      init(info);
      prepareForRequest(info);
      requestInitialData(dbWatcherSessionId);
      return true;
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
   public void prepareForRequest(I_Info individualInfo) throws Exception {
      if (!this.initialized)
         throw new Exception("prepareForRequest: '" + this.name + "' has not been initialized properly or is already shutdown, check your logs");
      log.info("prepareForRequest");
      I_AdminSession session = getSession();
      long clearedMsg = session.clearCallbackQueue();
      log.info("clearing of callback queue before initiating: '" + clearedMsg + "' where removed since obsolete");

      if (this.statusTopic != null)
         sendStatusInformation("dbInitStart");
      doPause(); // stop the dispatcher
      SubscribeQos subQos = new SubscribeQos(this.global);
      subQos.setMultiSubscribe(false);
      subQos.setWantInitialUpdate(false);
      subQos.setPersistent(true);
      // this fills the client properties with the contents of the individualInfo object.
      new ClientPropertiesInfo(subQos.getData().getClientProperties(), individualInfo);
      session.subscribe(this.dataTopic, subQos.toXml());
      synchronized(this) {
         this.status = STATUS_INITIAL;
         setStatus(this.status);
      }
   }
   
   private void sendStatusInformation(String status) throws Exception {
      log.info("send status information '" + status + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      PublishKey pubKey = new PublishKey(this.global, this.statusTopic);
      Destination destination = new Destination(new SessionName(this.global, this.slaveSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
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
   public void requestInitialData(String dbWatcherSessionId) throws Exception {
      log.info(this.name + " sends now an initial update request to the Master '" + dbWatcherSessionId + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.global);
      Destination destination = new Destination(new SessionName(this.global, dbWatcherSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.addClientProperty(ReplicationConstants.SLAVE_NAME, this.slaveSessionId);
      pubQos.setPersistent(true);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
   }

   private org.xmlBlaster.engine.Global getEngineGlobal(Global glob) {
      return (org.xmlBlaster.engine.Global)glob.getObjectEntry(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
   }
   
   private I_AdminSession getSession() throws Exception {
      I_Authenticate auth = getEngineGlobal(this.global).getAuthenticate();
      if (auth == null)
         throw new Exception("prepareForRequest: could not retreive the Authenticator object. Can not continue.");
      SessionName sessionName = new SessionName(this.global, this.slaveSessionId);
      I_AdminSubject subject = auth.getSubjectInfoByName(sessionName);
      if (subject == null)
         throw new Exception("prepareForRequest: no subject (slave) found with the session name '" + this.slaveSessionId + "'");
      I_AdminSession session = subject.getSessionByPubSessionId(sessionName.getPublicSessionId());
      if (session == null)
         throw new Exception("prepareForRequest: no session '" + this.slaveSessionId + "' found. Valid sessions for this user are '" + subject.getSessionList() + "'");
      return session;
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#reactivateDestination()
    */
   public synchronized void reactivateDestination(long minReplKey, long maxReplKey) throws Exception {
      log.info("Initial Operation completed with replication key interval [" + minReplKey + "," + maxReplKey + "]");
      if (!this.initialized)
         throw new Exception("prepareForRequest: '" + this.name + "' has not been initialized properly or is already shutdown, check your logs");

      this.minReplKey = minReplKey;
      this.maxReplKey = maxReplKey;
      this.status = STATUS_TRANSITION;
      setStatus(this.status);
      doContinue();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public synchronized void shutdown() {
      if (!this.initialized)
         return;
      this.global.unregisterMBean(this.mbeanHandle);
      this.initialized = false;
   }

   /**
    * FIXME TODO HERE
    */
   public synchronized ArrayList check(ArrayList entries, I_Queue queue) throws Exception {
      log.info("check invoked with status '" + getStatus() + "'");
      if (this.status == STATUS_INITIAL)
         return new ArrayList();
      
      // if (this.status == STATUS_NORMAL || this.status == STATUS_UNUSED)
      // TODO find a clean solution for this: currently we have the case where several masters send data to one single
      // slave, this can result in a conflict where min- and maxReplKey are overwritten everytime. A quick and dirty solution
      // is to let everything pass for now.
      if (this.status == STATUS_NORMAL || this.forceSending)
         return entries;
      
      ArrayList ret = new ArrayList();
      for (int i=0; i < entries.size(); i++) {
         ReferenceEntry entry = (ReferenceEntry)entries.get(i);
         MsgUnit msgUnit = entry.getMsgUnit();
         long replKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
         if (replKey > -1L) {
            this.maxReplKey = replKey;
            setMaxReplKey(this.maxReplKey);
         }
         log.info("check: processing '" + replKey + "'");
         if (replKey < 0L) { // this does not come from the normal replication, so these are other messages which we just deliver
            ClientProperty endMsg = msgUnit.getQosData().getClientProperty(ReplicationConstants.END_OF_TRANSITION);
            if (endMsg == null) {
               log.warning("the message unit with qos='" + msgUnit.getQosData().toXml() + "' and key '" + msgUnit.getKey() + "' has no 'replKey' Attribute defined.");
               ret.add(entry);
               continue;
            }
            else {
               log.info("Received msg marking the end of the initial update: '" + this.name + "' going into NORMAL operations");
               this.status = STATUS_NORMAL;
               setStatus(this.status);
               queue.removeRandom(entry);
               continue;
            }
         }
         setMaxReplKey(replKey);
         log.info("repl entry '" + replKey + "' for range [" + this.minReplKey + "," + this.maxReplKey + "]");
         if (replKey >= this.minReplKey) {
            log.info("repl adding the entry");
            ret.add(entry);
            
            if (replKey > this.maxReplKey) {
               log.info("entry with replKey='" + replKey + "' is higher as maxReplKey)='" + this.maxReplKey + "' switching to normal operationa again");
               this.status = STATUS_NORMAL;
               setStatus(this.status);
            }
         }
         else { // such messages have been already from the initial update. (obsolete messages are removed)
            log.info("removing entry with replKey='" + replKey + "' since older than minEntry='" + this.minReplKey + "'");
            queue.removeRandom(entry);
            
         }
      }
      return ret;
   }
   
   /**
    * @return Returns the sqlResponse.
    */
   public String getSqlResponse() {
      return this.sqlResponse;
   }

   
   /**
    * @param sqlResponse The sqlResponse to set.
    */
   public void setSqlResponse(String sqlResponse) {
      this.sqlResponse = sqlResponse;
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      return new HashSet();
   }

   /**
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#doContinue()
    */
   public void doContinue() throws Exception {
      I_AdminSession session = getSession(); 
      session.setDispatcherActive(true);
   }

   /**
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#doPause()
    */
   public void doPause() throws Exception {
      I_AdminSession session = getSession(); 
      session.setDispatcherActive(false);
   }
   
   /**
    * Toggles the dispatcher from active to inactive or vice versa.
    * Returns the actual state.
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#toggleActive()
    * @return the actual state.
    */
   public boolean toggleActive() throws Exception {
      I_AdminSession session = getSession();
      session.setDispatcherActive(!session.getDispatcherActive());
      return session.getDispatcherActive();
   }
   
   /**
    * TODO fix this since it potentially could delete request from other slaves since the DbWatcher is serving
    * several slaves.
    * Cancels an ongoing initialUpdate Request.
    */
   public void cancelInitialUpdate(String dbWatcherSessionId) throws Exception {
      if (this.status == STATUS_NORMAL)
         return;
      if (!this.initialized)
         throw new Exception("cancelInitialUpdate: '" + this.name + "' has not been initialized properly or is already shutdown, check your logs");
      I_AdminSession session = getSession();
      long clearedMsg = session.clearCallbackQueue();
      log.info("clearing of callback queue: '" + clearedMsg + "' where removed since a cancel request was done");

      // sending the cancel op to the DbWatcher
      log.info(this.name + " sends now a cancel request to the Master '" + dbWatcherSessionId + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.global);
      Destination destination = new Destination(new SessionName(this.global, dbWatcherSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.addClientProperty(ReplicationConstants.SLAVE_NAME, this.slaveSessionId);
      pubQos.setPersistent(false);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_CANCEL_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
      // TODO Check this since it could mess up the current status if one is exaclty finished now
      this.status = STATUS_NORMAL;
      setStatus(this.status);
   }
   
}
