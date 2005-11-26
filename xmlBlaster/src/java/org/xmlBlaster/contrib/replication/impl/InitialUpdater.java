/*------------------------------------------------------------------------------
 Name:      InitialUpdater.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.jutils.text.StringHelper;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
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
      
      /**
       * Use since the CPU time is getting high when outputs are coming slowly
       * @param sleepTime
       */
      private final void sleep(long sleepTime) {
         if (sleepTime < 1L)
            return;
         try {
            Thread.sleep(sleepTime);
         }
         catch (Exception ex) {
         }
      }

      /**
       * This method will commit the current transaction on the connection passed in case
       * the string to be searched is found. 
       * @param data
       */
      private final void checkForCommit(String data) {
         if (this.connInfo == null || this.connInfo.isCommitted())
            return;
         synchronized (this) {
            if (this.connInfo == null || this.connInfo.isCommitted())
               return;
            if (data != null && this.stringToCheck != null && data.indexOf(this.stringToCheck) > -1)
               this.connInfo.commit();
         }
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
      private I_DbSpecific dbSpecific;
      
      public ExecutionThread(String replTopic, String destination, String slaveName, I_DbSpecific dbSpecific) {
         this.replTopic = replTopic;
         this.destination = destination;
         this.slaveName = slaveName;
         this.dbSpecific = dbSpecific;
      }
      
      public void run() {
         try {
            this.dbSpecific.initiateUpdate(replTopic, destination, slaveName);
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
   private String initialCmdPath;
   private boolean keepDumpFiles;
   private String replPrefix;
   private I_DbSpecific dbSpecific;
   private String stringToCheck;
   private Map runningExecutes = new HashMap();
   
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
   
   
   private synchronized void sendRegistrationMessage() throws Exception {
      log.info("Sending registration message for '" + this.replPrefix + "'");
      // fill the info to be sent with the own info objects
      HashMap msgMap = new HashMap();
      new ClientPropertiesInfo(msgMap, this.info);
      msgMap.put("_destination", ReplicationConstants.REPL_MANAGER_SESSION);
      msgMap.put("_command", ReplicationConstants.REPL_MANAGER_REGISTER);

      log.info("going to initialize publisher for replication '" + this.replPrefix + "'");
      if (this.publisher != null) {
         synchronized(this.info) {
            boolean isRegistered = this.info.getBoolean("_InitialUpdaterRegistered", false);
            log.info("replication '" + this.replPrefix + "' registered='" + isRegistered + "'");
            if (!isRegistered) {
               String topic = this.info.get("mom.topicName", null);
               if (topic == null)
                  throw new Exception("InitialUpdater.init: registering the dbWatcher to the Replication Manager: no topic was defined but need one. Please add to your configuration 'mom.topicName'");
               msgMap.put("_topic", topic);
               log.info("replication '" + this.replPrefix + "' publishing registration message on topic '" + topic + "'");
               this.publisher.publish(ReplicationConstants.REPL_MANAGER_TOPIC, ReplicationConstants.REPL_MANAGER_REGISTER.getBytes(), msgMap);
               this.info.put("_InitialUpdaterRegistered", "true");
            }
         }
      }
      this.initialCmdPath = this.info.get("replication.path", "${user.home}/tmp");
      this.initialCmd = this.info.get("replication.initialCmd", null);
      this.keepDumpFiles = info.getBoolean("replication.keepDumpFiles", false);
      // this.stringToCheck = info.get("replication.initial.stringToCheck", "rows exported");
      this.stringToCheck = info.get("replication.initial.stringToCheck", null);
   }
   
   /**
    * @see I_DbSpecific#init(I_Info)
    * 
    */
   public final void init(I_Info info) throws Exception {
      log.info("going to initialize the resources");
      this.info = info;
      this.replPrefix = this.info.get("replication.prefix", "repl_");
      Map map = new HashMap();
      map.put("replPrefix", this.replPrefix);
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
      sendRegistrationMessage();
   }

   /**
    * @see I_DbSpecific#shutdown()
    */
   public final void shutdown() throws Exception {
      try {
         if (this.publisher != null) {
            log.info("going to shutdown: cleaning up resources");
            // registering this instance to the Replication Manager
            // fill the info to be sent with the own info objects
            HashMap msgMap = new HashMap();
            new ClientPropertiesInfo(msgMap, this.info);
            msgMap.put("_destination", ReplicationConstants.REPL_MANAGER_SESSION);
            msgMap.put("_command", ReplicationConstants.REPL_MANAGER_UNREGISTER);
            this.publisher.publish(ReplicationConstants.REPL_MANAGER_TOPIC, ReplicationConstants.REPL_MANAGER_UNREGISTER.getBytes(), msgMap);
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
    * 
    * @return a uniqueId identifying this publish operation.
    * 
    * @throws Exception
    */
   public final String publishCreate(int counter, SqlInfo updateInfo, long newReplKey) throws Exception {
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
      // and later put the part number inside
      if (this.publisher == null) {
         log.warning("SpecificDefaut.publishCreate publisher is null, can not publish. Check your configuration");
         return null;
      }
      else
         return this.publisher.publish("", updateInfo.toXml("").getBytes(), map);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Update#update(java.lang.String, byte[], java.util.Map)
    */
   public final void update(String topic, byte[] content, Map attrMap) throws Exception {

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
         
         // this.dbSpecific.initiateUpdate(replTopic, destination, slaveName);
         ExecutionThread executionThread = new ExecutionThread(replTopic, destination, slaveName, this.dbSpecific);
         executionThread.start();
         
      }
      else if (ReplicationConstants.REPL_REQUEST_CANCEL_UPDATE.equals(msg)) {
         // do cancel
         ClientProperty prop = (ClientProperty)attrMap.get(ReplicationConstants.SLAVE_NAME);
         if (prop == null)
            throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
         String slaveName = prop.getStringValue();
         synchronized (this) {
            Execute exec = (Execute)this.runningExecutes.remove(slaveName);
            if (exec != null)
               exec.stop();
         }
      }
      else {
         log.warning("update from '" + topic + "' with request '" + msg + "'");
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
   public final void sendInitialDataResponse(String topic, String filename, String destination, String slaveName, long minKey, long maxKey) throws Exception {
      sendInitialFile(topic, filename, minKey);
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
    * @param filename
    * @throws FileNotFoundException
    * @throws IOException
    */
   private void sendInitialFile(String topic, String shortFilename, long minKey)throws FileNotFoundException, IOException, JMSException  {
      // now read the file which has been generated
      String filename = null;
      
      if (this.initialCmdPath != null)
         filename = this.initialCmdPath + "/" + shortFilename;
      else
         filename = shortFilename;
      File file = new File(filename);
      
      FileInputStream fis = new FileInputStream(file);
      // in this case they are just decorators around I_ChangePublisher
      if (this.publisher == null) {
         log.warning("The publisher has not been initialized, can not publish message for '" + shortFilename + "'");
         return;
      }
      XBSession session = this.publisher.getJmsSession();
      XBMessageProducer producer = new XBMessageProducer(session, new XBDestination(topic, null));
      producer.setPriority(PriorityEnum.HIGH_PRIORITY.getInt());
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      XBStreamingMessage msg = session.createStreamingMessage();
      msg.setStringProperty("_filename", shortFilename);
      msg.setLongProperty(ReplicationConstants.REPL_KEY_ATTR, minKey);
      msg.setStringProperty(ReplicationConstants.DUMP_ACTION, "true");
      msg.setInputStream(fis);
      producer.send(msg);
      if (!this.keepDumpFiles) {
         if (file.exists()) { 
            boolean ret = file.delete();
            if (!ret)
               log.warning("could not delete the file '" + filename + "'");
         }
      }
      fis.close();
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
    * @return true if the transaction has already been committed, false otherwise
    */
   private void osExecute(String slaveName, String cmd, ConnectionInfo connInfo) throws Exception {
      try {
         if (Execute.isWindows()) cmd = "cmd " + cmd;
         String[] args = StringHelper.toArray(cmd, " ");
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
   
   
   /**
    * This is the intial command which is invoked on the OS. It is basically used for the
    * import and export of the DB. Could also be used for other operations on the OS.
    * It is a helper method.
    * 
    * @param argument the argument to execute. It is normally the absolute file name to be
    * exported/imported. Can be null, if null, one is generated by using the current timestamp.
    * @param conn the connection to perform a commit on. Can be null, if null, no commit
    * is done asynchronously.
    * 
    * @throws Exception
    */
   public final String initialCommand(String invoker, String completeFilename, ConnectionInfo connInfo) throws Exception {
      String filename = null;
      if (completeFilename == null) {
         filename = "" + (new Timestamp()).getTimestamp() + ".dmp";
         completeFilename = this.initialCmdPath + "/" + filename;
      }
      if (this.initialCmd == null)
         log.warning("no initial command has been defined ('initialCmd'). I will ignore it");
      else {
         String cmd = this.initialCmd + " " + completeFilename;
         osExecute(invoker, cmd, connInfo);
         
      }
      return filename;
   }

   
   
   /**
    * Sends a new registration message
    * @see org.xmlBlaster.client.I_ConnectionStateListener#reachedAlive(org.xmlBlaster.util.dispatch.ConnectionStateEnum, org.xmlBlaster.client.I_XmlBlasterAccess)
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      try {
         log.info("connection is going in ALIVE from '" + oldState + "'");
         sendRegistrationMessage();
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
