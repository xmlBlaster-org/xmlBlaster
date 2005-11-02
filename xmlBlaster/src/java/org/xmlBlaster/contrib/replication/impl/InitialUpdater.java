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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;

import org.jutils.text.StringHelper;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_ContribPlugin;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
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
import org.xmlBlaster.util.qos.ClientProperty;

public class InitialUpdater implements I_Update, I_ContribPlugin {

   class ExecuteListener implements I_ExecuteListener {

      StringBuffer errors = new StringBuffer();

      public void stderr(String data) {
         log.warning(data);
      }

      public void stdout(String data) {
         log.info(data);
         this.errors.append(data).append("\n");
      }

      String getErrors() {
         return this.errors.toString();
      }
   }

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
      if (needsPublisher)
         this.publisher = DbWatcher.getChangePublisher(this.info);
      
      // registering this instance to the Replication Manager
      HashMap subscriptionMap = new HashMap();
      subscriptionMap.put("ptp", "true");
      if (this.publisher != null)
         this.publisher.registerAlertListener(this, subscriptionMap);
      // fill the info to be sent with the own info objects
      HashMap msgMap = new HashMap();
      new ClientPropertiesInfo(msgMap, this.info);
      msgMap.put("_destination", ReplicationConstants.REPL_MANAGER_SESSION);
      msgMap.put("_command", ReplicationConstants.REPL_MANAGER_REGISTER);

      if (this.publisher != null) {
         synchronized(this.info) {
            boolean isRegistered = this.info.getBoolean("_InitialUpdaterRegistered", false);
            if (!isRegistered) {
               String topic = this.info.get("mom.topicName", null);
               if (topic == null)
                  throw new Exception("InitialUpdater.init: registering the dbWatcher to the Replication Manager: no topic was defined but need one. Please add to your configuration 'mom.topicName'");
               msgMap.put("_topic", topic);
               this.publisher.publish(ReplicationConstants.REPL_MANAGER_TOPIC, ReplicationConstants.REPL_MANAGER_REGISTER.getBytes(), msgMap);
               this.info.put("_InitialUpdaterRegistered", "true");
            }
         }
      }
      this.initialCmdPath = this.info.get("replication.path", "${user.home}/tmp");
      this.initialCmd = this.info.get("replication.initialCmd", null);
      this.keepDumpFiles = info.getBoolean("replication.keepDumpFiles", false);
      
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
   public final String publishCreate(int counter, DbUpdateInfo updateInfo, long newReplKey) throws Exception {
      log.info("publishCreate invoked for counter '" + counter + "'");
      DbUpdateInfoDescription description = updateInfo.getDescription();
      
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
         this.dbSpecific.initiateUpdate(replTopic, destination, slaveName);
      }
      else {
         log.warning("update from '" + topic + "' with request '" + msg + "'");
      }
   }

   
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
      XBStreamingMessage msg = (XBStreamingMessage)session.createTextMessage();
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
   }
   

   /**
    * Executes an Operating System command.
    * 
    * @param cmd
    * @throws Exception
    */
   private void osExecute(String cmd) throws Exception {
      if (Execute.isWindows()) cmd = "cmd " + cmd;
      String[] args = StringHelper.toArray(cmd, " ");
      Execute execute = new Execute(args, null);
      ExecuteListener listener = new ExecuteListener();
      execute.setExecuteListener(listener);
      execute.run(); // blocks until finished
      if (execute.getExitValue() != 0) {
         throw new Exception("Exception occured on executing '" + cmd + "': " + listener.getErrors());
      }
   }
   
   
   /**
    * This is the intial command which is invoked on the OS. It is basically used for the
    * import and export of the DB. Could also be used for other operations on the OS.
    * It is a helper method.
    * 
    * @param argument the argument to execute. It is normally the absolute file name to be
    * exported/imported.
    * 
    * @throws Exception
    */
   public final String initialCommand(String completeFilename) throws Exception {
      String filename = null;
      if (completeFilename == null) {
         filename = "" + (new Timestamp()).getTimestamp() + ".dmp";
         completeFilename = this.initialCmdPath + "/" + filename;
      }
      if (this.initialCmd == null)
         log.warning("no initial command has been defined ('initialCmd'). I will ignore it");
      else {
         String cmd = this.initialCmd + " " + completeFilename;
         this.osExecute(cmd);
      }
      return filename;
   }

}
