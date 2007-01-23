/*------------------------------------------------------------------------------
Name:      ReplSlave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.MomEventEngine;
import org.xmlBlaster.contrib.dbwatcher.DbWatcherConstants;
import org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.engine.admin.I_AdminSession;
import org.xmlBlaster.engine.queuemsg.ReferenceEntry;
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
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
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
public class ReplSlave implements I_ReplSlave, ReplSlaveMBean, ReplicationConstants {

   private static Logger log = Logger.getLogger(ReplSlave.class.getName());
   private final static String CONN_STALLED = "stalled";
   private final static String CONN_CONNECTED = "connected";
   private final static String CONN_DISCONNECTED = "disconnected";
   
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

   private boolean dispatcherActive;
   private long queueEntries;
   private boolean connected;
   private String sessionName = "";
   private long transactionSeq;
   /** These properties are used to transport the information from the check to the postCheck method. */
   private long tmpTransSeq;
   private long tmpTransSeq2;
   private long tmpReplKey;
   private int tmpStatus;
   private String lastMessageKey;
   private long maxChunkSize = 1024L*1024; // TODO make this configurable
   private long tmpMsgSeq;
   private long tmpMsgSeq2;
   private long messageSeq;
   private String masterConn = CONN_DISCONNECTED;
   
   /** we don't want to sync the check method because the jmx will synchronize on the object too */
   private Object initSync = new Object();
   
   /** The queue associated to this slave. It is associated on first invocation of check */
   private I_Queue queue;
   private boolean stalled;
   
   public ReplSlave(Global global, ReplManagerPlugin manager, String slaveSessionId) throws XmlBlasterException {
      this.forcedCounter = 0L;
      this.global = global;
      this.manager = manager;
      this.slaveSessionId = slaveSessionId;
      // this.status = STATUS_UNUSED;
      // setStatus(STATUS_NORMAL);
      this.status = STATUS_UNCONFIGURED;
      this.lastMessage = "";
      //final boolean doPersist = false;
      //final boolean dispatcherActive = false;
      this.lastMessageKey = this.slaveSessionId + ".lastMessage";
      try {
         //setDispatcher(dispatcherActive, doPersist);
         this.persistentInfo = this.manager.getPersistentInfo();
         this.lastMessage = this.persistentInfo.get(this.lastMessageKey, "");
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

   public int getStatusAsInt() {
      return this.status;
   }
   
   public String getStatus() {
      switch (this.status) {
         case STATUS_INITIAL : return "INITIAL";
         case STATUS_TRANSITION : return "TRANSITION";
         case STATUS_INCONSISTENT : return "INCONSISTENT";
         case STATUS_UNCONFIGURED : return "UNCONFIGURED";
         default : return "NORMAL";
      }
   }

   /**
    * The info comes as the client properties of the subscription Qos. Avoids double configuration.
    */
   public void init(I_Info info) throws Exception {
      synchronized(this.initSync) {
         // we currently allow re-init since we can serve severeal dbWatchers for one DbWriter 
         this.replPrefix = info.get("_replName", null);
         if (this.replPrefix == null) 
            throw new Exception("The replication name '_replName' has not been defined");
         this.name = "replSlave" + this.replPrefix + slaveSessionId;
         this.dataTopic = info.get(DbWatcherConstants.MOM_TOPIC_NAME, "replication." + this.replPrefix);
         // only send status messages if it has been configured that way
         this.statusTopic = info.get(DbWatcherConstants.MOM_STATUS_TOPIC_NAME, null);
         
         // TODO Remove this when a better solution is found : several ReplSlaves for same Writer if data comes from several DbWatchers.
         boolean forceSending = info.getBoolean(REPLICATION_FORCE_SENDING, false);
         if (forceSending)
            this.forceSending = true; 
         String instanceName = this.manager.getInstanceName() + ContextNode.SEP + this.slaveSessionId;
         ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, instanceName,
               this.global.getContextNode());
         this.mbeanHandle = this.global.registerMBean(contextNode, this);
         
         this.dbWatcherSessionName = info.get(this.slaveSessionId + DBWATCHER_SESSION_NAME, null);
         this.cascadedReplPrefix = this.persistentInfo.get(this.slaveSessionId + CASCADED_REPL_PREFIX, null);
         this.cascadedReplSlave = this.persistentInfo.get(this.slaveSessionId + CASCADED_REPL_SLAVE, null);
         log.info(this.name + ": associated DbWatcher='" + this.dbWatcherSessionName + "' cascaded replication prefix='" + this.cascadedReplPrefix + "' and cascaded repl. slave='" + this.cascadedReplSlave + "'");
         int tmpStatus = this.persistentInfo.getInt(this.slaveSessionId + ".status", -1);
         if (tmpStatus > -1)
            setStatus(tmpStatus);
         
         final boolean doPersist = false;
         setDispatcher(this.persistentInfo.getBoolean(this.slaveSessionId + ".dispatcher", false), doPersist);

         this.oldReplKeyPropertyName = this.slaveSessionId + ".oldReplData";
         long[] replData = ReplManagerPlugin.readOldReplData(this.persistentInfo, this.oldReplKeyPropertyName);
         this.tmpReplKey = replData[0];
         this.tmpTransSeq = replData[1];
         this.tmpMsgSeq = replData[2];
         this.maxReplKey = this.tmpReplKey;
         this.minReplKey = replData[3]; 
         this.transactionSeq = tmpTransSeq;
         this.messageSeq = tmpMsgSeq;
         
         this.srcVersion = info.get(REPLICATION_VERSION, "0.0");
         this.ownVersion = info.get(REPL_VERSION, null);
         
         if (this.ownVersion != null) {
            this.persistentInfo.put(this.slaveSessionId + "." + ReplicationConstants.REPL_VERSION, this.ownVersion);
         }
         else {
            this.ownVersion = this.persistentInfo.get(this.slaveSessionId + "." + ReplicationConstants.REPL_VERSION, this.srcVersion);
         }
         
         if (this.srcVersion != null && this.ownVersion != null && !this.srcVersion.equalsIgnoreCase(this.ownVersion))
            this.doTransform = true;

         this.initialFilesLocation = info.get(ReplicationConstants.INITIAL_FILES_LOCATION, null);
         this.initialized = true;
      }
   }
   
   private final void setStatus(int status) {
      boolean doStore = status != this.status;
      this.status = status;
      if (this.persistentInfo != null && doStore) { // can also be called before init is called.
         if (this.status != STATUS_UNCONFIGURED)
            this.persistentInfo.put(this.slaveSessionId + ".status", "" + status);
      }
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
   
   private final void setMaxReplKey(long replKey, long transKey, long msgKey, long minReplKey) {
      boolean doStore = this.maxReplKey != replKey || this.minReplKey != minReplKey || this.transactionSeq != transKey || msgKey != this.messageSeq;
      this.maxReplKey = replKey;
      this.minReplKey = minReplKey;
      if (doStore)
         this.persistentInfo.put(this.oldReplKeyPropertyName, "" + replKey + " " + transKey + " " + msgKey + " " + minReplKey);
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

   public boolean reInitiate(I_Info info) throws Exception {
      final boolean onlyRegister = true;
      return run(info, this.dbWatcherSessionName, this.cascadedReplPrefix, this.cascadedReplSlave, onlyRegister);
   }
   
   /**
    * 
    * @param info
    * @param dbWatcherSessionId
    * @param cascadeReplPrefix
    * @param cascadeSlaveSessionName
    * @param onlyRegister if true it only registers for initial update but does not execute it yet.
    * It will wait for a further (common) start message.
    * @return
    * @throws Exception
    */
   public boolean run(I_Info info, String dbWatcherSessionId, String cascadeReplPrefix, String cascadeSlaveSessionName, boolean onlyRegister) throws Exception {
      if (this.status != STATUS_NORMAL && this.status != STATUS_INCONSISTENT && this.status != STATUS_UNCONFIGURED) {
         log.warning("will not start initial update request since one already ongoing for '" + this.name + "'");
         return false;
      }
      this.persistentInfo.put(this.slaveSessionId + CASCADED_REPL_PREFIX, cascadeReplPrefix);
      this.persistentInfo.put(this.slaveSessionId + CASCADED_REPL_SLAVE, cascadeSlaveSessionName);
      
      info.put(this.slaveSessionId + DBWATCHER_SESSION_NAME, dbWatcherSessionId);
      init(info);
      prepareForRequest(info);
      requestInitialData(dbWatcherSessionId, onlyRegister);
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
      synchronized(this.initSync) {
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
   public void requestInitialData(String dbWatcherSessionId, boolean onlyRegister) throws Exception {
      log.info(this.name + " sends now an initial update request to the Master '" + dbWatcherSessionId + "'");
      I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
      // no oid for this ptp message 
      PublishKey pubKey = new PublishKey(this.global, REQUEST_INITIAL_DATA_TOPIC);
      Destination destination = new Destination(new SessionName(this.global, dbWatcherSessionId));
      destination.forceQueuing(true);
      PublishQos pubQos = new PublishQos(this.global, destination);
      pubQos.addClientProperty(ReplicationConstants.SLAVE_NAME, this.slaveSessionId);
      pubQos.addClientProperty(ReplicationConstants.REPL_VERSION, this.ownVersion);
      if (this.initialFilesLocation != null)
         pubQos.addClientProperty(ReplicationConstants.INITIAL_FILES_LOCATION, this.initialFilesLocation);
      pubQos.setPersistent(true);
      if (onlyRegister)
         pubQos.addClientProperty(ReplicationConstants.INITIAL_UPDATE_ONLY_REGISTER, onlyRegister);
      MsgUnit msg = new MsgUnit(pubKey, ReplicationConstants.REPL_REQUEST_UPDATE.getBytes(), pubQos);
      conn.publish(msg);
   }

   private org.xmlBlaster.engine.ServerScope getEngineGlobal(Global glob) {
      return (org.xmlBlaster.engine.ServerScope)glob.getObjectEntry(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
   }
   
   private I_AdminSession getSession() throws Exception {
      return this.manager.getSession(this.slaveSessionId);
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.I_ReplSlave#reactivateDestination()
    */
   public void reactivateDestination(long minReplKey, long maxReplKey) throws Exception {
      synchronized(this.initSync) {
         log.info("Initial Operation completed with replication key interval [" + minReplKey + "," + maxReplKey + "]");
         if (!this.initialized)
            throw new Exception("prepareForRequest: '" + this.name + "' has not been initialized properly or is already shutdown, check your logs");

         if (STATUS_INCONSISTENT == this.status) {
            log.warning("Will not change the status to transition since the initialUpdate has been cancelled");
            return;
         }
            
         this.minReplKey = minReplKey;
         this.maxReplKey = maxReplKey;
         setStatus(STATUS_TRANSITION);
         final boolean doPersist = true;
         doContinue(doPersist);
      }
   }

   /**
    * @see org.xmlBlaster.contrib.dbwriter.I_ContribPlugin#shutdown()
    */
   public void shutdown() {
      synchronized (this.initSync) {
         if (!this.initialized)
            return;
         this.global.unregisterMBean(this.mbeanHandle);
         this.initialized = false;
      }
   }


   private final void doTransform(MsgUnit msgUnit) throws Exception {
      if (this.doTransform) {
         // ClientProperty prop = msgUnit.getQosData().getClientProperty(ReplicationConstants.DUMP_ACTION);
         // if (prop == null) {
         if (msgUnit.getContentMime() != null && msgUnit.getContentMime().equals("text/xml")) {
            byte[] content = msgUnit.getContent();
            InputStream is = MomEventEngine.decompress(new ByteArrayInputStream(content), msgUnit.getQosData().getClientProperties());
            content = ReplManagerPlugin.getContent(is);
            content = this.manager.transformVersion(this.replPrefix, this.ownVersion, this.slaveSessionId, content);
            msgUnit.setContent(content);
         }
      }
   }
   
   /**
    * Returns the name of the directory where the entries have been stored.
    * @param entry The entry to add as a chunk.
    * @param location The location where to add it.
    * @param subDirProp
    * @return
    * @throws Exception
    */
   private String storeChunkLocally(ReferenceEntry entry, ClientProperty location, ClientProperty subDirProp) throws Exception {
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
      String completeSubdirName = subDir.getAbsolutePath();
      if (!subDir.exists()) {
         if (!subDir.mkdir()) {
            String txt = "could not make '" + completeSubdirName + "' to be a directory. Check your rights";
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
      return completeSubdirName;
   }
   
   /**
    * 
    * @param newMsg If newMsg is null, it cleans the message otherwise the behaviour depens on doAdd
    * @param doAdd if true, the message is added to the current message, if false it is replaced.
    */
   private void changeLastMessage(String newMsg, boolean doAdd) {
      log.fine("'" + newMsg + "' invoked with add='" + doAdd + "'");
      if (newMsg == null) {
         if (this.lastMessage != null && this.lastMessage.length() > 0) {
            this.lastMessage = "";
            this.persistentInfo.put(this.lastMessageKey, this.lastMessage);
         }
      }
      else {
         if (doAdd)
            this.lastMessage += "\n" + newMsg.trim();
         else
            this.lastMessage = newMsg.trim();
         this.persistentInfo.put(this.lastMessageKey, this.lastMessage);
      }
   }
   
   
   /**
    * 
    */
   public ArrayList check(ArrayList entries, I_Queue queue) throws Exception {
      this.queue = queue;
      synchronized (this.initSync) {
         this.tmpStatus = -1;
         this.forcedCounter++;
         log.info("check invoked with status '" + getStatus() + "' for client '" + this.slaveSessionId + "' (invocation since start is '" + this.forcedCounter + "'");
         if (!this.initialized) {
            log.warning("check invoked without having been initialized. Will repeat operation until the real client connects");
            Thread.sleep(250L); // to avoid too fast looping
            return new ArrayList();
         }
         if (this.status == STATUS_INITIAL && !this.forceSending) { // should not happen since Dispatcher is set to false
            log.warning("check invoked in INITIAL STATUS. Will stop the dispatcher");
            final boolean doPersist = true;
            doPause(doPersist);
            return new ArrayList();
         }

         changeLastMessage(null, false); // clean last message
         // if (entries != null && entries.size() > 1)
         //    log.severe("the entries are '" + entries.size() + "' but we currently only can process one single entry at a time");
         
         if (entries.size() > 0) {
            for (int i=entries.size()-1; i > -1; i--) {
               ReferenceEntry entry = (ReferenceEntry)entries.get(i);
               if (log.isLoggable(Level.FINEST)) {
                  String txt = new String(decompressQueueEntryContent(entry));
                  log.finest("Processing entry '" + txt + "' for client '"  + this.name + "'");
               }
               MsgUnit msgUnit = entry.getMsgUnit();
               long tmpCounter = msgUnit.getQosData().getClientProperty(ReplicationConstants.TRANSACTION_SEQ, 0L);
               if (tmpCounter != 0L)
                  this.tmpTransSeq = tmpCounter;
               this.tmpReplKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
               tmpCounter = msgUnit.getQosData().getClientProperty(ReplicationConstants.MESSAGE_SEQ, 0L);
               if (tmpCounter != 0L)
                  this.tmpMsgSeq =  tmpCounter;
               if (this.tmpReplKey > -1L) {
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
         
         // check if one of the messages is the transition end tag, also check if the total size is exceeded
         ArrayList remoteEntries = new ArrayList();
         long totalSize = 0L;
         for (int i=0; i < entries.size(); i++) {
            ReferenceEntry entry = (ReferenceEntry)entries.get(i);
            MsgUnit msgUnit = entry.getMsgUnit();
            ClientProperty endMsg = msgUnit.getQosData().getClientProperty(ReplicationConstants.END_OF_TRANSITION);
            
            // check if the message is the end of the data (only sent in case the initial data has to be stored on 
            // file in which case the dispatcher shall return in its waiting state.
            ClientProperty endOfData = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_END);
            ClientProperty initialFilesLocation = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_FILES_LOCATION);
            ClientProperty subDirName = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_ID);
            if (endOfData != null) {
               final boolean doPersist = true;
               doPause(doPersist);
               queue.removeRandom(entry);
               // entries.remove(i); // endOfData will be kept locally, not sent to slave
               String dirName = "unknown";
               if (subDirName != null) {
                  if (initialFilesLocation != null) {
                     File base = new File(initialFilesLocation.getStringValue().trim());
                     File complete = new File(base, subDirName.getStringValue().trim());
                     dirName = complete.getAbsolutePath();
                  }
               }
               changeLastMessage("Manual Data transfer: WAITING (stored on '" + dirName + "')", false);
               break; // we need to interrupt here: all subsequent entries will be processed later.
            }

            // check if the message has to be stored locally
            ClientProperty endToRemote = msgUnit.getQosData().getClientProperty(ReplicationConstants.INITIAL_DATA_END_TO_REMOTE);
            if (initialFilesLocation != null && (endToRemote == null || !endToRemote.getBooleanValue()) && (endMsg == null || !endMsg.getBooleanValue())) {
               storeChunkLocally(entry, initialFilesLocation, subDirName);
               queue.removeRandom(entry);
               // entries.remove(i);
               continue;
            }
            
            if (endMsg != null) {
               log.info("Received msg marking the end of the initial for client '" + this.slaveSessionId + "' update: '" + this.name + "' going into NORMAL operations");
               // entries.remove(i);
               // initiate a cascaded replication (if configured that way)
               if (this.cascadedReplPrefix != null && this.cascadedReplSlave != null && this.cascadedReplPrefix.trim().length() > 0 && this.cascadedReplSlave.trim().length() > 0) {
                  log.info("initiating the cascaded replication with replication.prefix='" + this.cascadedReplPrefix + "' for slave='" + this.cascadedReplSlave + "'. Was entry '" + i + "' of a set of '" + entries.size() + "'");
                  this.manager.initiateReplicationNonMBean(this.cascadedReplSlave, this.cascadedReplPrefix, null, null, null);
               }
               else {
                  log.info("will not cascade initiation of any further replication for '" + this.name + "' since no cascading defined");
               }
               setStatus(STATUS_NORMAL);
               // queue.removeRandom(entry); // we dont remove it anymore since cleanup on client is now done on this message
               // continue; 
            }
            byte[] content = msgUnit.getContent();
            if (content != null)
               totalSize += content.length;
            if (totalSize <= this.maxChunkSize || i == 0)
               remoteEntries.add(entry);
            else
               break;
         }
         entries = null; // we can free it here since not needed anymore
         
         if (this.status == STATUS_NORMAL || this.status == STATUS_INCONSISTENT || this.status == STATUS_UNCONFIGURED)
            return remoteEntries;
         
         ArrayList ret = new ArrayList();
         for (int i=0; i < remoteEntries.size(); i++) {
            ReferenceEntry entry = (ReferenceEntry)remoteEntries.get(i);
            MsgUnit msgUnit = entry.getMsgUnit();
            long replKey = msgUnit.getQosData().getClientProperty(ReplicationConstants.REPL_KEY_ATTR, -1L);
            /* this is done when acknowledge comes
            if (replKey > -1L) {
               setMaxReplKey(replKey, this.tmpTransSeq, this.tmpMsgSeq);
            }
            */
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
                  log.info("entry with replKey='" + replKey + "' is higher than maxReplKey)='" + this.maxReplKey + "' switching to normal operation again for client '" + this.slaveSessionId + "' ");
                  setStatus(STATUS_NORMAL);
                  // this.tmpStatus = STATUS_NORMAL;
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
         
         // check if there are more than one entry the keep-transaction-flag has to be set:
         if (ret.size() > 1) {
            for (int i=0; i < ret.size()-1; i++) {
               ReferenceEntry entry = (ReferenceEntry)entries.get(i);
               MsgUnit msgUnit = entry.getMsgUnit();
               msgUnit.getQosData().addClientProperty(KEEP_TRANSACTION_OPEN, true);
            }
            log.info("Sending '" + ret.size() + "' entries in one single message");
         }
         return ret;
      }
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

   public boolean setDispatcher(boolean status) {
      try {
         setDispatcher(status, true);
         return true;
      }
      catch (Exception ex) {
         log.severe("Exception occured when trying to set the dispatcher to '" + status + "': " + ex.getMessage());
         ex.printStackTrace();
         return false;
      }
   }
   
   public final boolean setDispatcher(boolean status, boolean doPersist) throws Exception {
      I_AdminSession session = getSession(); 
      session.setDispatcherActive(status);
      if (doPersist)
         this.persistentInfo.put(this.slaveSessionId + ".dispatcher", "" + status);
      // to speed up refresh on monitor
      this.dispatcherActive = session.getDispatcherActive();
      return this.dispatcherActive;
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
         final boolean add = true;
         if (ex instanceof XmlBlasterException) {
            XmlBlasterException xmlblEx = ((XmlBlasterException)ex);
            log.warning(xmlblEx.toXml());
            if (xmlblEx.getEmbeddedException() != null)
               changeLastMessage(xmlblEx.getEmbeddedMessage(), add);
            else
               changeLastMessage(ex.getMessage(), add);
         }
         else
            changeLastMessage(ex.getMessage(), add);
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
   public boolean toggleActive() throws Exception {
      synchronized(this.initSync) {
         I_AdminSession session = getSession();
         final boolean doPersist = true;
         return setDispatcher(!session.getDispatcherActive(), doPersist);
      }
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
      if (this.dbWatcherSessionName == null)
         throw new Exception("The DbWatcher Session Id is null, can not cancel");

      (new Thread() {
         public void run() {
            cancelUpdateAsyncPart();
         }
      }).start();
   }

   /**
    * The cancelUpdate is invoked asynchronously to avoid log blocking of the monitor 
    * when the cancel operation is going on.
    */
   private void cancelUpdateAsyncPart() {
      try {
         I_AdminSession session = getSession();
         long clearedMsg = session.clearCallbackQueue();
         log.info("clearing of callback queue: '" + clearedMsg + "' where removed since a cancel request was done");

         // sending the cancel op to the DbWatcher
         log.info(this.name + " sends now a cancel request to the Master '" + this.dbWatcherSessionName + "'");
         I_XmlBlasterAccess conn = this.global.getXmlBlasterAccess();
         // no oid for this ptp message 
         PublishKey pubKey = new PublishKey(this.global, REQUEST_CANCEL_UPDATE_TOPIC);
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
      catch (Exception ex) {
         log.severe("An exception occured when trying to cancel the initial update for '" + this.replPrefix + "'");
         ex.printStackTrace();
      }
   }
   
   
   public void clearQueue() throws Exception {
      setStatus(STATUS_INCONSISTENT);
      log.warning("has been invoked");
      (new Thread() {
         public void run() {
            try {
               getSession().clearCallbackQueue();
            }
            catch (Exception ex) {
               ex.printStackTrace();
            }
         }
      }).start();
   }

   public long removeQueueEntries(long entries) throws Exception {
      setStatus(STATUS_INCONSISTENT);
      log.warning("has been invoked with entries='" + entries + "'");
      return getSession().removeFromCallbackQueue(entries);
   }
   

   public void kill() throws Exception {
      getSession().killSession();
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

   
   
   
   
   /**
    * Convenience method enforced by the MBean which returns true if the dispatcher of
    * the slave session is active, false otherwise.
    */
   public boolean isActive() {
      return this.dispatcherActive;
   }
   
   /**
    * Convenience method enforced by the MBean which returns the number of entries in 
    * the queue.
    */
   public long getQueueEntries() {
      return this.queueEntries;
   }

   /**
    * Convenience method enforced by the MBean which returns true if the real slave is
    * connected or false otherwise.
    */
   public boolean isConnected() {
      return this.connected;
   }

   /**
    * Convenience method enforced by the MBean which returns true if the connection to the
    * real slave is stalled or false otherwise.
    */
   public boolean isStalled() {
      return this.stalled;
   }

   public String getSessionName() {
      return this.sessionName;
   }

   public String getLastMessage() {
      return this.lastMessage;
   }

   public synchronized void checkStatus() {
      log.finest("invoked for '" + this.sessionName + "'");
      I_AdminSession session = null;
      try {
         session = getSession();
      }
      catch (Exception ex) {
         log.severe("an exception occured when retieving the session for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
      }
      
      // isActive
      try {
         this.dispatcherActive = session.getDispatcherActive();
      }
      catch (Exception ex) {
         log.severe("an exception occured when retieving the status of the dispatcher for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
         this.dispatcherActive = false;
      }
      
      // getQueueEntries
      try {
         if (this.tmpTransSeq2 != 0) {
            this.transactionSeq = this.tmpTransSeq2;
            this.tmpTransSeq2 = 0;
         }
         if (this.tmpMsgSeq2 != 0) {
            this.messageSeq = this.tmpMsgSeq2;
            this.tmpMsgSeq2 = 0;
         }
         this.queueEntries = this.manager.calculateQueueEntries(this.replPrefix, this.transactionSeq, this.messageSeq, session.getCbQueueNumMsgs());
         // this.queueEntries = this.manager.calculateQueueEntries(this.replPrefix, this.tmpTransSeq, this.tmpMsgSeq, session.getCbQueueNumMsgs());
      }
      catch (Exception ex) {
         log.severe("an exception occured when retieving the number of queue entries for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
         this.queueEntries = -1L;
      }
      
      try {
         I_AdminSession masterSession = this.manager.getMasterSession(this.replPrefix);
         if (masterSession != null) {
            if (masterSession.isStalled())
               this.masterConn = CONN_STALLED;
            else if (masterSession.getConnectionState().equals(ConnectionStateEnum.ALIVE.toString()))
               this.masterConn = CONN_CONNECTED;
            else
               this.masterConn = CONN_DISCONNECTED;
         }
         else {
            this.masterConn = CONN_DISCONNECTED;
         }
      }
      catch (Exception ex) {
         this.masterConn = CONN_DISCONNECTED;
         ex.printStackTrace();
      }
      // isConnected
      try {
         this.connected = session.getConnectionState().equals(ConnectionStateEnum.ALIVE.toString());
      }
      catch (Exception ex) {
         log.severe("an exception occured when checking if connected for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
         this.connected = false;
      } 

      // isStalled
      try {
         this.stalled = session.isStalled();
      }
      catch (Exception ex) {
         log.severe("an exception occured when checking if stalled for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
         this.stalled = false;
      } 
      
      // sessionName
      try {
         this.sessionName = session.getLoginName() + "/" + session.getPublicSessionId(); 
      }
      catch (Exception ex) {
         log.severe("an exception occured when getting the session name:" + ex.getMessage());
         ex.printStackTrace();
      } 

      // lastMessage
      try {
         String tmp = session.getLastCallbackException();
         if (!this.lastDispatcherException.equals(tmp)) { 
            this.lastDispatcherException = tmp;
            final boolean add = true;
            changeLastMessage(tmp, add);
         }
      }
      catch (Exception ex) {
         log.severe("an exception occured when getting the last dispatcher exception for '" + this.sessionName + "':" + ex.getMessage());
         ex.printStackTrace();
      }
   }
   
   public synchronized void postCheck(MsgQueueEntry[] processedEntries) throws Exception {
      if (this.tmpTransSeq != 0)
        this.tmpTransSeq2 = this.tmpTransSeq;
      if (this.tmpMsgSeq != 0)
         this.tmpMsgSeq2 = this.tmpMsgSeq;
      setMaxReplKey(this.tmpReplKey, this.tmpTransSeq, this.tmpMsgSeq, this.minReplKey);
      if (this.tmpStatus > -1)
         setStatus(this.tmpStatus);
   }

   public long getTransactionSeq() {
      return this.transactionSeq;
   }
   
   public static byte[] decompressQueueEntryContent(ReferenceEntry entry) {
      try {
         MsgUnit msgUnit = entry.getMsgUnit();
         if (msgUnit.getContent() == null)
            return new byte[0];
         byte[] content = (byte[])msgUnit.getContent().clone();
         Map cp = new HashMap(msgUnit.getQosData().getClientProperties());
         return ReplManagerPlugin.getContent(MomEventEngine.decompress(new ByteArrayInputStream(content), cp));
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return new byte[0];
      }
   }
   
   public String dumpEntries(int maxNum, long maxSize, String fileName) {
      if (this.queue == null)
         return "The queue is null, the replication must first try to deliver one entry before you can invoke this method";
      if (this.queue.getNumOfEntries() == 0)
         return "The queue for the slave '" + this.name + "' is empty: not dumping anything";
      try {
         ArrayList list = this.queue.peek(maxNum, maxSize);
         FileOutputStream out = new FileOutputStream(fileName);
         for (int i=0; i < list.size(); i++) {
            ReferenceEntry entry = (ReferenceEntry)list.get(i);
            byte[] ret = decompressQueueEntryContent(entry);
            out.write(ret);
         }
         out.close();
         String txt = "successfully dumped " + list.size() + " entries on file '" + fileName + "'"; 
         log.info(txt);
         return txt;
      }
      catch (IOException ex) {
         String txt = "Could not dump entries because of exception: " + ex.getMessage();
         log.severe(txt);
         ex.printStackTrace();
         return txt;
      }
      catch (Exception ex) {
         String txt = "Could not dump entries because of exception: " + ex.getMessage();
         log.severe(txt);
         ex.printStackTrace();
         return txt;
      }
   }
   
   public String dumpFirstEntry() {
      String prefix = this.initialFilesLocation;
      if (prefix == null)
         prefix = System.getProperty("user.home");
      String name = this.name.replace('/', '-');
      String filename =  prefix + "/" + name + ".qdmp";
      return dumpEntries(1, -1L, filename);
   }
   
   // The following methods are used for JMX to represent the associated / cascaded MBean
   
   /**
    * Returns null if the manager is null or if the cascaded object does not exist.
    */
   private ReplSlave getCascaded() {
      if (this.manager == null)
         return null;
      return (ReplSlave)this.manager.getSlave(this.cascadedReplSlave);
   }
   
   public boolean isCascading() {
      return getCascaded() != null;
   }
   
   /**
    * 
    */
   public String getCascadedSessionName() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.getSessionName();
      return "";
   }
   
   public long getCascadedQueueEntries() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.getQueueEntries();
      return 0L;
   }
   
   public long getCascadedTransactionSeq() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.getTransactionSeq();
      return -1L;
   }
   
   public String getCascadedStatus() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.getStatus();
      return "empty";
   }
   
   public boolean isCascadedActive() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.isActive();
      return false;
   }
   
   public boolean isCascadedConnected() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.isConnected();
      return false;
   }
   
   public String getCascadedVersion() {
      ReplSlave cascaded = getCascaded();
      if (cascaded != null)
         return cascaded.getVersion();
      return "";
   }
   
   public String toString() {
      return this.sessionName;
   }
   
   /**
    * Returns a string telling in which state the connection is. It can be stalled, connected or disconnected.
    * @return
    */
   public String getConnection() {
      if (isStalled())
         return CONN_STALLED;
      if (isConnected())
         return CONN_CONNECTED;
      return CONN_DISCONNECTED;
   }
   
   public String getMasterConnection() {
      return this.masterConn;
   }
   
   public String getCascadedConnection() {
      ReplSlave cascadedSlave = getCascaded();
      if (cascadedSlave == null)
         return CONN_DISCONNECTED;
      return cascadedSlave.getConnection();
   }
   
   public String getCascadedMasterConnection() {
      ReplSlave cascadedSlave = getCascaded();
      if (cascadedSlave == null)
         return CONN_DISCONNECTED;
      return cascadedSlave.getMasterConnection();
   }
   
}
