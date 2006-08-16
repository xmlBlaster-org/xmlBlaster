/*------------------------------------------------------------------------------
 Name:      InitialUpdater.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.VersionTransformerCache;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.jms.XBDestination;
import org.xmlBlaster.jms.XBMessageProducer;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.jms.XBStreamingMessage;
import org.xmlBlaster.util.Execute;
import org.xmlBlaster.util.I_ExecuteListener;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.ClientProperty;

public class InitialUpdater implements I_Update, I_ContribPlugin, I_ConnectionStateListener {

   public class ConnectionInfo {
      private Connection connection;
      private boolean committed;

      public ConnectionInfo(Connection conn) {
         this.connection = conn;
      }
      
      /**
       * @return Returns the connection.
       */
      public Connection getConnection() {
         return connection;
      }
      
      /**
       * @return Returns the committed.
       */
      public boolean isCommitted() {
         return committed;
      }
      
      /**
       * @param committed The committed to set.
       */
      public synchronized void commit() {
         if (this.connection == null)
            return;
         try {
            this.connection.commit();
            this.committed = true;
         }
         catch (SQLException ex) {
            ex.printStackTrace();
         }
      }
   }
   
   class ExecuteListener implements I_ExecuteListener {

      StringBuffer errors = new StringBuffer();
      long sleepTime = 0L;
      ConnectionInfo connInfo;
      final String stringToCheck;

      public ExecuteListener(String stringToCheck, ConnectionInfo connInfo) {
         this.stringToCheck = stringToCheck;
         this.connInfo = connInfo;
      }
      
      public void stderr(String data) {
         log.warning(data);
         // log.info(data);
         // this.errors.append(data).append("\n");
         // sleep(this.sleepTime);
         // checkForCommit(data);
      }

      public void stdout(String data) {
         log.info(data);
         // sleep(this.sleepTime);
         // checkForCommit(data);
      }

      String getErrors() {
         return this.errors.toString();
      }
   }

   
   
   class ExecutionThread extends Thread {
      
      private String replTopic;
      private String destination;
      private String slaveName;
      private String version;
      private I_DbSpecific dbSpecific;
      private String initialFilesLocation;
      
      public ExecutionThread(String replTopic, String destination, String slaveName, String version, I_DbSpecific dbSpecific, String initialFilesLocation) {
         this.replTopic = replTopic;
         this.destination = destination;
         this.slaveName = slaveName;
         this.version = version;
         this.dbSpecific = dbSpecific;
         this.initialFilesLocation = initialFilesLocation;
      }
      
      public void run() {
         try {
            this.dbSpecific.initiateUpdate(replTopic, destination, slaveName, version, initialFilesLocation);
         }
         catch (Exception ex) {
            log.severe("An Exception occured when running intial update for '" + replTopic + "' for '" + destination + "' as slave '" + slaveName);
            ex.printStackTrace();
         }
      }
   };

   
   private String CREATE_COUNTER_KEY = "_createCounter";
   private static Logger log = Logger.getLogger(InitialUpdater.class.getName());

   /** used to publish CREATE changes */
   protected I_ChangePublisher publisher;
   protected I_Info info;
   private String initialCmd;
   private String initialCmdPre;
   private String initialCmdPath;
   private boolean keepDumpFiles;
   private String replPrefix;
   private I_DbSpecific dbSpecific;
   private String stringToCheck;
   private Map runningExecutes = new HashMap();
   private String initialDataTopic;
   
   /**
    * Not doing anything.
    */
   public InitialUpdater(I_DbSpecific dbSpecific) {
      this.dbSpecific = dbSpecific;
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public final Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("replication.prefix");
      set.add("maxRowsOnCreate");
      if (this.publisher != null)
         PropertiesInfo.addSet(set, this.publisher.getUsedPropertyKeys());
      return set;
   }

   public ConnectionInfo getConnectionInfo(Connection conn) {
      return new ConnectionInfo(conn);
   }
   
   /**
    * @see I_DbSpecific#init(I_Info)
    * 
    */
   public final void init(I_Info info) throws Exception {
      log.info("going to initialize the resources");
      this.info = info;
      this.replPrefix = SpecificDefault.getReplPrefix(this.info);

      this.initialCmdPath = this.info.get("replication.path", "${user.home}/tmp");
      this.initialCmd = this.info.get("replication.initialCmd", null);
      this.initialCmdPre = info.get("replication.initialCmdPre", null);
      this.keepDumpFiles = info.getBoolean("replication.keepDumpFiles", false);
      // this.stringToCheck = info.get("replication.initial.stringToCheck", "rows exported");
      this.stringToCheck = info.get("replication.initial.stringToCheck", null);
      this.initialDataTopic = info.get("replication.initialDataTopic", null);
      String currentVersion = this.info.get("replication.version", "0.0");
      this.info.put(ReplicationConstants.SUPPORTED_VERSIONS, getSupportedVersions(currentVersion));
      
      boolean needsPublisher = this.info.getBoolean(I_DbSpecific.NEEDS_PUBLISHER_KEY, true);
      if (needsPublisher) {
         this.info.putObject("_connectionStateListener", this);
         this.publisher = DbWatcher.getChangePublisher(this.info);
      }
      
      // registering this instance to the Replication Manager
      HashMap subscriptionMap = new HashMap();
      subscriptionMap.put("ptp", "true");
      if (this.publisher != null)
         this.publisher.registerAlertListener(this, subscriptionMap);
      // rewrite the default behaviour of the timestamp detector to detect even UPDATES (deletes are also updates)
      /*
      boolean detectUpdates = this.info.getBoolean("detector.detectUpdates", false);
      if (detectUpdates)
         throw new Exception("You have configured the DbWatcher to have 'detector.detectUpdates=true'. This is not allowed in replication");
      log.info("overwriting the default for 'detector.detectUpdates' from 'true' to 'false' since we are in replication");
      this.info.put("detector.detectUpdates", "" + false);
      */

      // used for versioning (shall be passed to the ConnectQos when connecting (make sure this is
      // invoked before the mom connects
   }

   /**
    * @see I_DbSpecific#shutdown()
    */
   public final void shutdown() throws Exception {
      try {
         if (this.publisher != null) {
            log.info("going to shutdown: cleaning up resources");
            this.publisher.shutdown();
            this.publisher = null;
         }
      } 
      catch (Throwable e) {
         e.printStackTrace();
         log.warning(e.toString());
      }
   }

   /**
    * Publishes a 'CREATE TABLE' operation to the XmlBlaster. It is used on the
    * DbWatcher side. Note that it is also used to publish the INSERT commands
    * related to a CREATE TABLE operation, i.e. if on a CREATE TABLE operation
    * it is found that the table is already populated when reading it, then
    * these INSERT operations are published with this method.
    * 
    * @param counter
    *           The counter indicating which message number it is. The create
    *           opeation itself will have '0', the subsequent associated INSERT
    *           operations will have an increasing number (it is the number of
    *           the message not the number of the associated INSERT operation).
    * @param destination in case it is a ptp it is sent only to that destination, otherwise it is sent as a pub/sub
    * @return a uniqueId identifying this publish operation.
    * 
    * @throws Exception
    */
   public final String publishCreate(int counter, SqlInfo updateInfo, long newReplKey, String destination) throws Exception {
      log.info("publishCreate invoked for counter '" + counter + "'");
      SqlDescription description = updateInfo.getDescription();
      
      description.setAttribute(new ClientProperty(CREATE_COUNTER_KEY, "int",
            null, "" + counter));
      description.setAttribute(new ClientProperty(ReplicationConstants.EXTRA_REPL_KEY_ATTR, null, null, "" + newReplKey));
      if (counter == 0) {
         description.setCommand(ReplicationConstants.CREATE_ACTION);
         description.setAttribute(new ClientProperty(
               ReplicationConstants.ACTION_ATTR, null, null,
               ReplicationConstants.CREATE_ACTION));
      } else {
         description.setCommand(ReplicationConstants.REPLICATION_CMD);
         description.setAttribute(new ClientProperty(
               ReplicationConstants.ACTION_ATTR, null, null,
               ReplicationConstants.INSERT_ACTION));
      }

      Map map = new HashMap();
      map.put("_command", "CREATE");
      if (destination != null)
         map.put("_destination", destination);
      // and later put the part number inside
      if (this.publisher == null) {
         log.warning("SpecificDefaut.publishCreate publisher is null, can not publish. Check your configuration");
         return null;
      }
      else
         return this.publisher.publish("createTableMsg", updateInfo.toXml("").getBytes(), map);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Update#update(java.lang.String, byte[], java.util.Map)
    */
   public final void update(String topic, byte[] content, Map attrMap) {

      try {
         if (content == null)
            content = new byte[0];
         String msg = new String(content);
         // this comes from the requesting ReplSlave
         log.info("update for '" + topic + "' and msg='" + msg + "'");
         if (ReplicationConstants.REPL_REQUEST_UPDATE.equals(msg)) {
            ClientProperty prop = (ClientProperty)attrMap.get("_sender");
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_sender' specified");
            String destination = prop.getStringValue();

            String replTopic = this.info.get("mom.topicName", null);
            if (replTopic == null)
               throw new Exception("update for '" + msg + "' failed since the property 'mom.topicName' has not been defined. Check your DbWatcher Configuration file");

            prop = (ClientProperty)attrMap.get(ReplicationConstants.SLAVE_NAME);
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
            String slaveName = prop.getStringValue();
            this.dbSpecific.clearCancelUpdate(slaveName);

            prop = (ClientProperty)attrMap.get(ReplicationConstants.REPL_VERSION);
            String requestedVersion = null;
            if (prop != null)
               requestedVersion = prop.getStringValue();
            // this.dbSpecific.initiateUpdate(replTopic, destination, slaveName);
            prop = (ClientProperty)attrMap.get(ReplicationConstants.INITIAL_FILES_LOCATION);
            String initialFilesLocation = null;
            if (prop != null)
               initialFilesLocation = prop.getStringValue();
            ExecutionThread executionThread = new ExecutionThread(replTopic, destination, slaveName, requestedVersion, this.dbSpecific, initialFilesLocation);
            executionThread.start();
            
         }
         else if (ReplicationConstants.REPL_REQUEST_CANCEL_UPDATE.equals(msg)) {
            // do cancel
            ClientProperty prop = (ClientProperty)attrMap.get(ReplicationConstants.SLAVE_NAME);
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
            String slaveName = prop.getStringValue();
            this.dbSpecific.cancelUpdate(slaveName);
            synchronized (this) {
               Execute exec = (Execute)this.runningExecutes.remove(slaveName);
               if (exec != null)
                  exec.stop();
            }
         }
         else if (ReplicationConstants.REPL_REQUEST_RECREATE_TRIGGERS.equals(msg)) {
            final boolean force = true;
            final boolean forceSend = false;
            this.dbSpecific.addTriggersIfNeeded(force, null, forceSend);
         }
         else if (ReplicationConstants.STATEMENT_ACTION.equals(msg)) {
            String sql = ((ClientProperty)attrMap.get(ReplicationConstants.STATEMENT_ATTR)).getStringValue();
            boolean isHighPrio = ((ClientProperty)attrMap.get(ReplicationConstants.STATEMENT_PRIO_ATTR)).getBooleanValue();
            long maxResponseEntries = ((ClientProperty)attrMap.get(ReplicationConstants.MAX_ENTRIES_ATTR)).getLongValue();
            String statementId = ((ClientProperty)attrMap.get(ReplicationConstants.STATEMENT_ID_ATTR)).getStringValue();
            String sqlTopic =  ((ClientProperty)attrMap.get(ReplicationConstants.SQL_TOPIC_ATTR)).getStringValue();
            log.info("Be aware that the number of entries in the result set will be limited to '" + maxResponseEntries + "'. To change this use 'replication.sqlMaxEntries'");
            final boolean isMaster = true;
            byte[] response  = null;
            Exception ex = null;
            try {
               response = this.dbSpecific.broadcastStatement(sql, maxResponseEntries, isHighPrio, isMaster, sqlTopic, statementId);
            }
            catch (Exception e) {
               response = "".getBytes();
               ex = e;
            }
            
            if (this.publisher != null) {
               Map map = new HashMap();
               map.put(ReplicationConstants.MASTER_ATTR, this.replPrefix);
               map.put(ReplicationConstants.STATEMENT_ID_ATTR, statementId);
               map.put("_command", ReplicationConstants.STATEMENT_ACTION);
               if (ex != null)
                  map.put(ReplicationConstants.EXCEPTION_ATTR, ex.getMessage());
               this.publisher.publish(sqlTopic, response, map);
            }
            if (ex != null)
               throw ex;
         }
         else {
            log.warning("update from '" + topic + "' with request '" + msg + "'");
         }
      }
      catch (Throwable ex) {
         log.severe("An exception occured when processing the received update '" + new String(content) + "': " + ex.getMessage());
         ex.printStackTrace();
      }
   }

   /**
    * 
    * @param topic
    * @param filename
    * @param destination
    * @param slaveName
    * @param minKey
    * @param maxKey
    * @throws Exception
    */
   public final void sendInitialDataResponse(String slaveSessionName, String filename, String destination, String slaveName, long minKey, long maxKey, String requestedVersion, String currentVersion, String initialFilesLocation) throws Exception {
      sendInitialFile(slaveSessionName, filename, minKey, requestedVersion, currentVersion, initialFilesLocation);
      HashMap attrs = new HashMap();
      attrs.put("_destination", destination);
      attrs.put("_command", "INITIAL_DATA_RESPONSE");
      attrs.put("_minReplKey", "" + minKey);
      attrs.put("_maxReplKey", "" + maxKey);
      attrs.put(ReplicationConstants.SLAVE_NAME, slaveName);
      if (this.publisher != null)
         this.publisher.publish("", "INITIAL_DATA_RESPONSE".getBytes(), attrs);
      else
         log.warning("request for sending initial response can not be done since no publisher configured");
   }
   
   
   /**
    * Sends/publishes the initial file as a high priority message.
    * @param filename the name of the file to publish. Can be null, if null, no file is sent, only the status change message is sent.
    * @throws FileNotFoundException
    * @throws IOException
    */
   private void sendInitialFile(String slaveSessionName, String shortFilename, long minKey, String requestedVersion, String currentVersion, String initialFilesLocation) throws FileNotFoundException, IOException, JMSException  {
      // in this case they are just decorators around I_ChangePublisher
      if (this.publisher == null) {
         if (shortFilename == null)
            shortFilename = "no file (since no initial data)";
         log.warning("The publisher has not been initialized, can not publish message for '" + shortFilename + "'");
         return;
      }
      XBSession session = this.publisher.getJmsSession();
      // XBMessageProducer producer = new XBMessageProducer(session, new XBDestination(topic, null));
      
      XBMessageProducer producer = new XBMessageProducer(session, new XBDestination(this.initialDataTopic, slaveSessionName));
      producer.setPriority(PriorityEnum.HIGH_PRIORITY.getInt());
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      
      String dumpId = "" + new Timestamp().getTimestamp();
      // now read the file which has been generated
      String filename = null;
      if (shortFilename != null) {
         log.info("sending initial file '" + shortFilename + "' for user '" + slaveSessionName  + "'");
         if (this.initialCmdPath != null)
            filename = this.initialCmdPath + File.separator + shortFilename;
         else
            filename = shortFilename;
         File file = new File(filename);
         
         FileInputStream fis = new FileInputStream(file);
         
         XBStreamingMessage msg = session.createStreamingMessage();
         msg.setStringProperty("_filename", shortFilename);
         msg.setLongProperty(ReplicationConstants.REPL_KEY_ATTR, minKey);
         msg.setStringProperty(ReplicationConstants.DUMP_ACTION, "true");
         if (initialFilesLocation != null) {
            msg.setStringProperty(ReplicationConstants.INITIAL_FILES_LOCATION, initialFilesLocation);
            msg.setStringProperty(ReplicationConstants.INITIAL_DATA_ID, dumpId);
         }
         msg.setInputStream(fis);
         producer.send(msg);
         // make a version copy if none exists yet
         boolean doDelete = true;
         if (currentVersion != null) {
            String backupFileName = this.initialCmdPath + File.separator + VersionTransformerCache.buildFilename(this.replPrefix, currentVersion);
            File backupFile = new File(backupFileName);
            if (!backupFile.exists()) {
               final boolean copy = true;
               if (copy) {
                  BufferedInputStream bis = new BufferedInputStream(file.toURL().openStream());
                  FileOutputStream os = new FileOutputStream(backupFileName);
                  long length = file.length();
                  long remaining = length;
                  final int BYTE_LENGTH = 100000; // For the moment it is hardcoded
                  byte[] buf = new byte[BYTE_LENGTH];
                  while (remaining > 0) {
                     int tot = bis.read(buf);
                     remaining -= tot;
                     os.write(buf, 0, tot);
                  }
                  bis.close();
                  os.close();
               }
               else {
                  boolean ret = file.renameTo(backupFile);
                  if (!ret)
                     log.severe("could not move the file '" + filename + "' to '" + backupFileName + "' reason: could it be that the destination is not a local file system ? try the flag 'copyOnMove='true' (see http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.filepoller.html");
                  else
                     doDelete = false;
               }
            }
         }
         else
            log.severe("The version is not set. Can not make a backup copy of the version file");
        
         boolean isRequestingCurrentVersion = currentVersion.equalsIgnoreCase(requestedVersion); 
         if (!this.keepDumpFiles && doDelete && isRequestingCurrentVersion) {
            if (file.exists()) { 
               boolean ret = file.delete();
               if (!ret)
                  log.warning("could not delete the file '" + filename + "'");
            }
         }
         fis.close();
      }
      else
         log.info("initial update requested with no real initial data for '" + slaveSessionName + "' and for replication '" + this.replPrefix + "'");

      // send the message for the status change
      if (initialFilesLocation != null) {
         // then we save it in a file but we must tell it is finished now
         TextMessage  endMsg = session.createTextMessage();
         endMsg.setText("INITIAL UPDATE WILL BE STORED UNDER '" + initialFilesLocation + "'");
         endMsg.setBooleanProperty(ReplicationConstants.INITIAL_DATA_END, true);
         endMsg.setStringProperty(ReplicationConstants.INITIAL_DATA_ID, dumpId);
         endMsg.setStringProperty(ReplicationConstants.INITIAL_FILES_LOCATION, initialFilesLocation);
         producer.send(endMsg);
         endMsg = session.createTextMessage();
         endMsg.setText("INITIAL UPDATE WILL BE STORED UNDER '" + initialFilesLocation + "' (going to remote)");
         endMsg.setBooleanProperty(ReplicationConstants.INITIAL_DATA_END_TO_REMOTE, true);
         endMsg.setStringProperty(ReplicationConstants.INITIAL_DATA_ID, dumpId);
         endMsg.setStringProperty(ReplicationConstants.INITIAL_FILES_LOCATION, initialFilesLocation);
         producer.send(endMsg);
      }
      TextMessage  endMsg = session.createTextMessage();
      endMsg.setText("INITIAL UPDATE ENDS HERE");
      endMsg.setBooleanProperty(ReplicationConstants.END_OF_TRANSITION , true);
      producer.send(endMsg);
   }
   
   /**
    * Executes an Operating System command.
    * 
    * @param cmd
    * @throws Exception
    */
   private void osExecute(String slaveName, String cmd, ConnectionInfo connInfo) throws Exception {
      try {
         // if (Execute.isWindows()) cmd = "cmd " + cmd;
         String[] args = ReplaceVariable.toArray(cmd, " ");
         log.info("running for '" + slaveName + "' for cmd '" + cmd + "'");
         Execute execute = new Execute(args, null);
         synchronized (this) {
            if (slaveName != null) {
               Execute oldExecute = (Execute)this.runningExecutes.remove(slaveName);
               if (oldExecute != null) {
                  log.warning("A new request for an initial update has come for '" + slaveName + "' but there is one already running. Will shut down the running one first");
                  oldExecute.stop();
                  log.info("old initial request for '" + slaveName + "' has been shut down");
                  this.runningExecutes.put(slaveName, execute);
               }
            }
         }
         ExecuteListener listener = new ExecuteListener(this.stringToCheck, connInfo);
         execute.setExecuteListener(listener);
         execute.run(); // blocks until finished
         if (execute.getExitValue() != 0) {
            throw new Exception("Exception occured on executing '" + cmd + "': " + listener.getErrors());
         }
      }
      finally {
         synchronized (this) {
            if (slaveName != null)
               this.runningExecutes.remove(slaveName);
         }
      }
   }
   
   public String getSupportedVersions(String currentReplVersion) throws Exception {
      if (this.initialCmdPath == null)
         throw new Exception("InitialUpdater.getSupportedVersions invoked with no initialCmdPath specified");
      File dir = new File(this.initialCmdPath);
      if (!dir.exists())
         throw new Exception("InitialUpdater.getSupportedVersions invoked but the directory '" + this.initialCmdPath + "' does not exist");
      if (!dir.isDirectory())
         throw new Exception("InitialUpdater.getSupportedVersions invoked but '" + this.initialCmdPath + "' is not a directory");
      File[] childs = dir.listFiles();
      TreeSet set = new TreeSet();
      for (int i=0; i < childs.length; i++) {
         if (childs[i].isDirectory())
            continue;
         if (!childs[i].canRead())
            continue;
         String filename = childs[i].getName();
         String prefix = VersionTransformerCache.stripReplicationPrefix(filename).trim();
         String version = VersionTransformerCache.stripReplicationVersion(filename);
         if (version != null && prefix.equals(this.replPrefix)) {
            log.info("added version='" + version + "' for prefix='" + prefix + "' when encountering file='" + filename + "'");
            set.add(prefix + ReplicationConstants.VERSION_TOKEN + version.trim());
         }
      }
      if (currentReplVersion != null) {
         String txt = this.replPrefix + ReplicationConstants.VERSION_TOKEN + currentReplVersion; 
         set.add(txt);
         log.info("added default version '" + txt + "'");
      }
      return InfoHelper.getIteratorAsString(set.iterator());
   }
   
   
   /**
    * This is the intial command which is invoked on the OS. It is basically used for the
    * import and export of the DB. Could also be used for other operations on the OS.
    * It is a helper method. If the initialCmd (the 'replication.initialCmd' property) is null,
    * then it silently returns null as the filename.
    * 
    * @param argument the argument to execute. It is normally the absolute file name to be
    * exported/imported. Can be null, if null, one is generated by using the current timestamp.
    * @param conn the connection to perform a commit on. Can be null, if null, no commit
    * is done asynchronously.
    * 
    * @throws Exception
    */
   public final String initialCommand(String invoker, String completeFilename, ConnectionInfo connInfo, String version) throws Exception {
      if (this.initialCmd == null)
         return null;
      String filename = null;
      if (completeFilename == null) {
         filename = "" + (new Timestamp()).getTimestamp() + ".dmp";
         completeFilename = this.initialCmdPath + File.separator + filename;
      }
      // String cmd = this.initialCmd + " \"" + completeFilename + "\"";
      String cmd = this.initialCmd + " " + completeFilename;
      if (version != null)
         cmd += " " + version;
      osExecute(invoker, cmd, connInfo);
      return filename;
   }

   public final void initialCommandPre() throws Exception {
      if (this.initialCmdPre == null)
         return;
      osExecute(null, this.initialCmdPre, null);
   }
   
   /**
    * Sends a new registration message
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAlive(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      try {
         log.info("connection is going from '" + oldState + " to 'ALIVE'");
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedDead(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("connection is going in DEAD from '" + oldState + "'");
   }

   /**
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedPolling(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public synchronized void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("connection is going in POLLING from '" + oldState + "'");
      this.info.put("_InitialUpdaterRegistered", "false");
   }

}
