/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.io.File;
import java.io.FileOutputStream;
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
import org.xmlBlaster.contrib.db.DbInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.admin.I_AdminSubject;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
import org.xmlBlaster.protocol.I_Authenticate;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.xbformat.MsgInfo;
import org.xmlBlaster.util.xbformat.XmlScriptParser;


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
   boolean initialized; 
   private long minReplKey;
   private long maxReplKey;
   private int status;
   private Object mbeanHandle;
   private String sqlResponse;
   private boolean forceSending; // temporary Hack to be removed TODO
   private I_DbPool pool;
   private I_Info persistentInfo;
   private String oldReplKeyPropertyName;
   private String dbWatcherSessionName;
   private ReplManagerPlugin manager;
   private String replPrefix;
   private String cascadedReplSlave;
   private String cascadedReplPrefix;
   private long forcedCounter;  // counter used when forceSending is set to 'true'
   private String ownVersion;
   private String srcVersion;
   private boolean doTransform;
   private String initialFilesLocation;
   private String lastMessage;
   private String lastDispatcherException = "";

   public ReplSlave(Global global, I_DbPool pool, ReplManagerPlugin manager, String slaveSessionId) throws XmlBlasterException {
      this.global = global;
      this.pool = pool;
      this.manager = manager;
      this.slaveSessionId = slaveSessionId;
      // this.status = STATUS_UNUSED;
      setStatus(STATUS_NORMAL);
      this.lastMessage = "";
      //final boolean doPersist = false;
      //final boolean dispatcherActive = false;
      try {
         //setDispatcher(dispatcherActive, doPersist);
         this.persistentInfo = new DbInfo(this.pool, "replication");
      }
      catch (Exception ex) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE, "ReplSlave constructor", "could not instantiate correctly", ex);
      }
   }

   public String getTopic() {
      return this.dataTopic;
   }
   
   public long getMinReplKey() {
      if (this.forceSending)
         return this.forcedCounter;
      return this.minReplKey;
   }
   
   public long getMaxReplKey() {
      if (this.forceSending)
         return this.forcedCounter;
      return this.maxReplKey;
   }

   public synchronized String getStatus() {
      switch (this.status) {
         case STATUS_INITIAL : return "INITIAL";
         case STATUS_TRANSITION : return "TRANSITION";
         case STATUS_INCONSISTENT : return "INCONSISTENT";
         // case STATUS_UNUSED : return "UNUSED";
         default : return "NORMAL";
      }
   }
   
   /**
    * The info comes as the client properties of the subscription Qos. Avoids double configuration.
    */
   public synchronized void init(I_Info info) throws Exception {
      // we currently allow re-init since we can serve severeal dbWatchers for one DbWriter 
      this.replPrefix = info.get("_replName", null);
      if (this.replPrefix == null) 
         throw new Exception("The replication name '_replName' has not been defined");
      this.name = "replSlave" + this.replPrefix + slaveSessionId;
      this.dataTopic = info.get("mom.topicName", "replication." + this.replPrefix);
      // only send status messages if it has been configured that way
      this.statusTopic = info.get("mom.statusTopicName", null);
      
      // TODO Remove this when a better solution is found : several ReplSlaves for same Writer if data comes from several DbWatchers.
      boolean forceSending = info.getBoolean("replication.forceSending", false);
      if (forceSending)
         this.forceSending = true; 
      String instanceName = this.manager.getInstanceName() + ContextNode.SEP + this.slaveSessionId;
      ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, instanceName,
            this.global.getContextNode());
      this.mbeanHandle = this.global.registerMBean(contextNode, this);
      
      this.dbWatcherSessionName = info.get(DBWATCHER_SESSION_NAME, null);
      this.cascadedReplPrefix = this.persistentInfo.get(CASCADED_REPL_PREFIX, null);
      this.cascadedReplSlave = this.persistentInfo.get(CASCADED_REPL_SLAVE, null);
      log.info(this.name + ": associated DbWatcher='" + this.dbWatcherSessionName + "' cascaded replication prefix='" + this.cascadedReplPrefix + "' and cascaded repl. slave='" + this.cascadedReplSlave + "'");
      int tmpStatus = this.persistentInfo.getInt(this.slaveSessionId + ".status", -1);
      if (tmpStatus > -1)
         setStatus(tmpStatus);
      
      final boolean doPersist = false;
      setDispatcher(this.persistentInfo.getBoolean(this.slaveSessionId + ".dispatcher", false), doPersist);
      this.oldReplKeyPropertyName = this.slaveSessionId + ".oldReplKey";
      long tmp = this.persistentInfo.getLong(this.oldReplKeyPropertyName, -1L);
      if (tmp > -1L) {
         this.maxReplKey = tmp;
         log.info("One entry found in persistent map '" + ReplicationConstants.CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Will start with '" + this.maxReplKey + "'");
      }
      else {
         log.info("No entry found in persistent map '" + ReplicationConstants.CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Starting by 0'");
         this.maxReplKey = 0L;
      }
      this.srcVersion = info.get("replication.version", "0.0");
      this.ownVersion = info.get(ReplicationConstants.REPL_VERSION, null);
      
      if (this.ownVersion != null) {
         this.persistentInfo.put(this.slaveSessionId + "." + ReplicationConstants.REPL_VERSION, this.ownVersion);
      }
      else {
         this.ownVersion = this.persistentInfo.get(this.slaveSessionId + "." + ReplicationConstants.REPL_VERSION, this.srcVersion);
      }
      
      if (this.srcVersion != null && this.ownVersion != null && !this.srcVersion.equalsIgnoreCase(this.ownVersion))
         this.doTransform = true;
      this.initialized = true;
      this.forcedCounter = 0L;
      this.initialFilesLocation = info.get(ReplicationConstants.INITIAL_FILES_LOCATION, null);
   }
   
   private final void setStatus(int status) {
      this.status = status;
      if (this.persistentInfo != null) // can also be called before init is called.
         this.persistentInfo.put(this.slaveSessionId + ".status", "" + status);
      // this is a temporary solution for the monitoring
      String client = "client/";
      int pos = this.slaveSessionId.indexOf(client);
      if (pos < 0)
         log.warning("session name '" + this.slaveSessionId + "' does not start with '" + client + "'");
      else {
         String key = "__" + this.slaveSessionId.substring(pos + client.length());
         org.xmlBlaster.engine.ServerScope engineGlob = this.getEngineGlobal(this.global);
         if (engineGlob == null)
            log.warning("Can not write status since no engine global found");
         else {
            log.info("setting property '" + key + "' to '" + getStatus());
            engineGlob.getProperty().getProperties().setProperty(key, getStatus());
         }
      }
   }
   
   private final void setMaxReplKey(long replKey) {
      this.maxReplKey = replKey;
      this.persistentInfo.put(this.oldReplKeyPropertyName, "" + replKey);
      String client = "client/";
      if (this.slaveSessionId == null)
         return;
      int pos = this.slaveSessionId.indexOf(client);
      if (pos < 0)
         log.warning("session name '" + this.slaveSessionId + "' does not start with '" + client + "'");
      else {
         String key = "__" + this.slaveSessionId.substring(pos + client.length()) + "_MaxReplKey";
         org.xmlBlaster.engine.ServerScope engineGlob = this.getEngineGlobal(this.global);
         if (engineGlob == null)
            log.warning("Can not write status since no engine global found");
         else {
            log.info("setting property '" + key + "' to '" + getMaxReplKey());
            engineGlob.getProperty().getProperties().setProperty(key, String.valueOf(getMaxReplKey()));
         }
      }
   }

   public boolean run(I_Info info, String dbWatcherSessionId, String cascadeReplPrefix, String cascadeSlaveSessionName) throws Exception {
      if (this.status != STATUS_NORMAL && this.status != STATUS_INCONSISTENT) {
         log.warning("will not start initial update request since one already ongoing for '" + this.name + "'");
         return false;
      }
      this.persistentInfo.put(CASCADED_REPL_PREFIX, cascadeReplPrefix);
      this.persistentInfo.put(CASCADED_REPL_SLAVE, cascadeSlaveSessionName);
      
      info.put(DBWATCHER_SESSION_NAME, dbWatcherSessionId);
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
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#prepareForRequest(I_Info)
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
      final boolean doPersist = true;
      doPause(doPersist); // stop the dispatcher
      
      // first unsubscribe (in case it did already an initial update previously, this is needed to remove the subscription
      // (and thereby its outdate subscription qos from persistence). On a back replication, i.e. where you have more than
      // one sources you don't want to do this.
      if (individualInfo.getBoolean("replication.forceNewSubscription", true)) {
         try {
            session.unSubscribe(this.dataTopic, "");
         }
         catch (Throwable ex) {
         }
      }
      
      SubscribeQos subQos = new SubscribeQos(this.global);
      subQos.setMultiSubscribe(false);
      subQos.setWantInitialUpdate(false);
      subQos.setPersistent(true);
      // this fills the client properties with the contents of the individualInfo object.
      new ClientPropertiesInfo(subQos.getData().getClientProperties(), individualInfo);
      session.subscribe(this.dataTopic, subQos.toXml());
      synchronized(this) {
         setStatus(STATUS_INITIAL);
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
      pubQos.addClientProperty(ReplicationConstants.REPL_VERSION, this.ownVersion);
      if (this.initialFilesLocation != null)
         pubQos.addClientProperty(ReplicationConstants.INITIAL_FILES_LOCATION, this.initialFilesLocation);
      pubQos.setPersistent(true);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
   }

   private org.xmlBlaster.engine.ServerScope getEngineGlobal(Global glob) {
      return (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
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
      setStatus(STATUS_TRANSITION);
      final boolean doPersist = true;
      doContinue(doPersist);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public synchronized void shutdown() {
      if (!this.initialized)
         return;
      this.global.unregisterMBean(this.mbeanHandle);
      this.initialized = false;
   }


   private final void doTransform(MsgUnit msgUnit) throws Exception {
      if (this.doTransform) {
         // ClientProperty prop = msgUnit.getQosData().getClientProperty(ReplicationConstants.DUMP_ACTION);
         // if (prop == null) {
         if (msgUnit.getContentMime() != null && msgUnit.getContentMime().equals("text/xml")) {
            String newContent = this.manager.transformVersion(this.replPrefix, this.ownVersion, this.slaveSessionId, msgUnit.getContentStr());
            msgUnit.setContent(newContent.getBytes());
         }
      }
   }
   
   private void storeChunkLocally(ReferenceEntry entry, ClientProperty location, ClientProperty subDirProp) throws Exception {
      if (entry == null)
         throw new Exception("The entry to store is null, can not store");
      MsgUnit msgUnit = entry.getMsgUnit();
      if (msgUnit == null)
         throw new Exception("The msgUnit to store is null, can not store");
      if (location == null || location.getStringValue() == null || location.getStringValue().trim().length() < 1)
         throw new Exception("The location is empty, can not store the message unit '" + msgUnit.getLogId() + "'");
      // String fileId = "" + new Timestamp().getTimestamp();
      // this way they are automatically sorted and in case of a repeated write it simply would be overwritten.
      String fileId = entry.getPriority() + "-" + entry.getUniqueId();
      
      String pathName = location.getStringValue().trim();
      File dirWhereToStore = ReplManagerPlugin.checkExistance(pathName);
      
      if (subDirProp == null)
         throw new Exception("The property to define the file name (dataId) is not set, can not continue");
      String subDirName = subDirProp.getStringValue();
      if (subDirName == null || subDirName.trim().length() < 1)
         throw new Exception("The subdirectory to be used to store the initial data is empty");
      File subDir = new File(dirWhereToStore, subDirName);
      if (!subDir.exists()) {
         if (!subDir.mkdir()) {
            String txt = "could not make '" + subDir.getAbsolutePath() + "' to be a directory. Check your rights";
            log.severe(txt);
            throw new Exception(txt);
         }
      }
      
      File file = new File(subDir, fileId);
      if (file.exists())
         log.warning("File '" + file.getAbsolutePath() + "' exists already. Will overwrite it");
      FileOutputStream fos = new FileOutputStream(file);
      MsgUnitRaw msgUnitRaw = new MsgUnitRaw(msgUnit.getKey(), msgUnit.getContent(), msgUnit.getQos());
      MsgInfo msgInfo = new MsgInfo(this.global, MsgInfo.INVOKE_BYTE, MethodName.UPDATE_ONEWAY, this.slaveSessionId);
      msgInfo.addMessage(msgUnitRaw);
      XmlScriptParser parser = new XmlScriptParser();
      parser.init(new Global(), null, null);
      fos.write(parser.toLiteral(msgInfo).getBytes());
      fos.close();
      log.info("MsgUnit '" + msgUnit.getQosData().getRcvTimestamp().getTimestamp() + "' has been written to file '" + file.getAbsolutePath() + "'");
   }
   
   /**
    * FIXME TODO HERE
    */
   public synchronized ArrayList check(ArrayList entries, I_Queue queue) throws Exception {
      this.forcedCounter++;
      this.lastMessage = "";
      log.info("check invoked with status '" + getStatus() + "' for client '" + this.slaveSessionId + "' (invocation since start is '" + this.forcedCounter + "'");
      if (!this.initialized) {
         log.warning("check invoked without having been initialized. Will repeat operation until the real client connects");
         return new ArrayList();
      }
      if (this.status == STATUS_INITIAL && !this.forceSending) { // should not happen since Dispatcher is set to false
         final boolean doPersist = true;
         doPause(doPersist);
         return new ArrayList();
      }

      if (entries.size() > 0) {
         for (int i=entries.size()-1; i > -1; i--) {
            ReferenceEntry entry = (ReferenceEntry)entries.get(i);
            MsgUnit msgUnit = entry.getMsgUnit();
            long replKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
            if (replKey > -1L) {
               setMaxReplKey(replKey);
               break; // the other messages will have lower numbers (if any) so we break for performance.
            }
         }      
      }
      
      // check if already processed ... and at the same time do the versioning transformation (if needed)
      for (int i=entries.size()-1; i > -1; i--) {
         ReferenceEntry entry = (ReferenceEntry)entries.get(i);
         MsgUnit msgUnit = entry.getMsgUnit();
         ClientProperty alreadyProcessed = msgUnit.getQosData().getClientProperty(ReplicationConstants.ALREADY_PROCESSED_ATTR);
         if (alreadyProcessed != null) {
            log.info("Received entry for client '" + this.slaveSessionId + "' which was already processed. Will remove it");
            queue.removeRandom(entry);
            entries.remove(i);
         }
         else
            doTransform(msgUnit);
      }
      
      // check if one of the messages is the transition end tag            
      for (int i=0; i < entries.size(); i++) {
         ReferenceEntry entry = (ReferenceEntry)entries.get(i);
         MsgUnit msgUnit = entry.getMsgUnit();
         ClientProperty endMsg = msgUnit.getQosData().getClientProperty(ReplicationConstants.END_OF_TRANSITION);
         
         // check if the message is the end of the data (only sent in case the initial data has to be stored on file in which
         // case the dispatcher shall return in its waiting state.
         ClientProperty endOfData = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_END);
         ClientProperty initialFilesLocation = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_FILES_LOCATION);
         ClientProperty subDirName = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_ID);
         if (endOfData != null) {
            final boolean doPersist = true;
            doPause(doPersist);
            queue.removeRandom(entry);
            entries.remove(i);
            String dirName = "unknown";
            if (subDirName != null) {
               if (initialFilesLocation != null) {
                  File base = new File(initialFilesLocation.getStringValue().trim());
                  File complete = new File(base, subDirName.getStringValue().trim());
                  dirName = complete.getAbsolutePath();
               }
            }
            this.lastMessage = "Manual Data transfer: WAITING (stored on '" + dirName + "')";
            continue;
         }

         // check if the message has to be stored locally
         ClientProperty endToRemote = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_END_TO_REMOTE);
         if (initialFilesLocation != null && (endToRemote == null || !endToRemote.getBooleanValue())) {
            storeChunkLocally(entry, initialFilesLocation, subDirName);
            queue.removeRandom(entry);
            entries.remove(i); // TODO INVERT SEQUENCE SINCE THEORETICALLY IT COULD BE MORE THAN ONE MSG IN THE LIST
            continue;
         }
         
         if (endMsg != null) {
            log.info("Received msg marking the end of the initial for client '" + this.slaveSessionId + "' update: '" + this.name + "' going into NORMAL operations");
            setStatus(STATUS_NORMAL);
            queue.removeRandom(entry);
            entries.remove(i);
            // initiate a cascaded replication (if configured that way)
            if (this.cascadedReplPrefix != null && this.cascadedReplSlave != null && this.cascadedReplPrefix.trim().length() > 0 && this.cascadedReplSlave.trim().length() > 0) {
               log.info("initiating the cascaded replication with replication.prefix='" + this.cascadedReplPrefix + "' for slave='" + this.cascadedReplSlave + "'. Was entry '" + i + "' of a set of '" + entries.size() + "'");
               this.manager.initiateReplication(this.cascadedReplSlave, this.cascadedReplPrefix, null, null, null);
            }
            else {
               log.info("will not cascade initiation of any further replication for '" + this.name + "' since no cascading defined");
            }
            break; // there should only be one such message 
         }
         
         
         
      }

      // if (this.status == STATUS_NORMAL || this.status == STATUS_UNUSED)
      // TODO find a clean solution for this: currently we have the case where several masters send data to one single
      // slave, this can result in a conflict where min- and maxReplKey are overwritten everytime. A quick and dirty solution
      // is to let everything pass for now.
      if (this.status == STATUS_NORMAL || this.status == STATUS_INCONSISTENT)
         return entries;
      
      ArrayList ret = new ArrayList();
      for (int i=0; i < entries.size(); i++) {
         ReferenceEntry entry = (ReferenceEntry)entries.get(i);
         MsgUnit msgUnit = entry.getMsgUnit();
         long replKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
         if (replKey > -1L) {
            setMaxReplKey(replKey);
         }
         log.info("check: processing '" + replKey + "' for client '" + this.slaveSessionId + "' ");
         if (replKey < 0L) { // this does not come from the normal replication, so these are other messages which we just deliver
            ClientProperty endMsg = msgUnit.getQosData().getClientProperty(ReplicationConstants.END_OF_TRANSITION);
            if (endMsg == null) {
               log.warning("the message unit with qos='" + msgUnit.getQosData().toXml() + "' and key '" + msgUnit.getKey() + "'  for client '" + this.slaveSessionId + "' has no 'replKey' Attribute defined.");
               ret.add(entry);
               continue;
            }
         }
         log.info("repl entry '" + replKey + "' for range [" + this.minReplKey + "," + this.maxReplKey + "] for client '" + this.slaveSessionId + "' ");
         if (replKey >= this.minReplKey || this.forceSending) {
            log.info("repl adding the entry for client '" + this.slaveSessionId + "' ");
            doTransform(msgUnit);
            ret.add(entry);
            if (replKey > this.maxReplKey || this.forceSending) {
               log.info("entry with replKey='" + replKey + "' is higher as maxReplKey)='" + this.maxReplKey + "' switching to normal operationa again for client '" + this.slaveSessionId + "' ");
               setStatus(STATUS_NORMAL);
               // initiate a cascaded replication (if so configured)
               if (this.cascadedReplPrefix != null && this.cascadedReplSlave != null && this.cascadedReplPrefix.trim().length() > 0 && this.cascadedReplSlave.trim().length() > 0) {
                  log.info("initiating the cascaded replication with replication.prefix='" + this.cascadedReplPrefix + "' for slave='" + this.cascadedReplSlave + "'");
                  this.manager.initiateReplication(this.cascadedReplSlave, this.cascadedReplPrefix, null, null, null);
               }
            }
         }
         else { // such messages have been already from the initial update. (obsolete messages are removed)
            log.info("removing entry with replKey='" + replKey + "' since older than minEntry='" + this.minReplKey + "' for client '" + this.slaveSessionId + "' ");
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

   private final void setDispatcher(boolean status, boolean doPersist) throws Exception {
      I_AdminSession session = getSession(); 
      session.setDispatcherActive(status);
      if (doPersist)
         this.persistentInfo.put(this.slaveSessionId + ".dispatcher", "" + status);
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#doContinue()
    */
   public void doContinue(boolean doPersist) throws Exception {
      setDispatcher(true, doPersist);
   }

   /**
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#doPause()
    */
   public void doPause(boolean doPersist) throws Exception {
      setDispatcher(false, doPersist);
   }
   
   public void handleException(Throwable ex) {
      try {
         this.lastMessage = ex.getMessage();
         final boolean doPersist = true;
         doPause(doPersist);
      }
      catch (Throwable e) {
         log.severe("An exception occured when trying to pause the connection: " + e.getMessage());
         ex.printStackTrace();
      }
   }
   
   /**
    * Toggles the dispatcher from active to inactive or vice versa.
    * Returns the actual state.
    * @see org.xmlBlaster.contrib.replication.ReplSlaveMBean#toggleActive()
    * @return the actual state.
    */
   public synchronized boolean toggleActive() throws Exception {
      I_AdminSession session = getSession();
      final boolean doPersist = true;
      setDispatcher(!session.getDispatcherActive(), doPersist);
      return session.getDispatcherActive();
   }
   
   /**
    * TODO fix this since it potentially could delete request from other slaves since the DbWatcher is serving
    * several slaves.
    * Cancels an ongoing initialUpdate Request.
    */
   public void cancelInitialUpdate() throws Exception {
      if (this.status == STATUS_NORMAL)
         return;
      if (!this.initialized)
         throw new Exception("cancelInitialUpdate: '" + this.name + "' has not been initialized properly or is already shutdown, check your logs");
      I_AdminSession session = getSession();
      long clearedMsg = session.clearCallbackQueue();
      log.info("clearing of callback queue: '" + clearedMsg + "' where removed since a cancel request was done");

      // sending the cancel op to the DbWatcher
      if (this.dbWatcherSessionName == null)
         throw new Exception("The DbWatcher Session Id is null, can not cancel");
      log.info(this.name + " sends now a cancel request to the Master '" + this.dbWatcherSessionName + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.global);
      Destination destination = new Destination(new SessionName(this.global, this.dbWatcherSessionName));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.addClientProperty(ReplicationConstants.SLAVE_NAME, this.slaveSessionId);
      pubQos.setPersistent(false);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_CANCEL_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
      // TODO Check this since it could mess up the current status if one is exaclty finished now
      //setStatus(STATUS_NORMAL);
      setStatus(STATUS_INCONSISTENT);
   }

   /**
    * Convenience method enforced by the MBean which returns the number of entries in 
    * the queue.
    */
   public long getQueueEntries() throws Exception {
      return getSession().getCbQueueNumMsgs();
   }

   /**
    * Convenience method enforced by the MBean which returns true if the dispatcher of
    * the slave session is active, false otherwise.
    */
   public boolean isActive() {
      try {
         return getSession().getDispatcherActive();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return false;
      } 
   }
   
   /**
    * Convenience method enforced by the MBean which returns true if the real slave is
    * connected or false otherwise.
    */
   public boolean isConnected() {
      try {
         return getSession().getConnectionState().equals(ConnectionStateEnum.ALIVE.toString());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return false;
      } 
   }

   public void clearQueue() throws Exception {
      getSession().clearCallbackQueue();
   }

   public long removeQueueEntries(long entries) throws Exception {
      return getSession().removeFromCallbackQueue(entries);
   }
   

   public void kill() throws Exception {
      getSession().killSession();
   }

   public String getSessionName() throws Exception {
      return getSession().getLoginName() + "/" + getSession().getPublicSessionId(); 
   }

   public String reInitiateReplication() throws Exception {
      return this.manager.initiateReplication(this.slaveSessionId, this.replPrefix + "_Ver_" + this.ownVersion, this.cascadedReplSlave, this.cascadedReplPrefix, this.initialFilesLocation);
   }
   
   public String getReplPrefix() {
      return this.replPrefix;
   }
   
   public String getVersion() {
      return this.ownVersion;
   }
 
   public String getLastMessage() {
      try {
         String tmp = getSession().getLastCallbackException();
         if (!this.lastDispatcherException.equals(tmp)) { 
            this.lastDispatcherException = tmp;
            this.lastMessage = tmp;
         }
      }
      catch (Exception ex) {
      }
      return this.lastMessage;
   }
}
