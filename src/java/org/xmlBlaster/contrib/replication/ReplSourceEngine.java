package org.xmlBlaster.contrib.replication;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.replication.impl.ReplManagerPlugin;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;
import org.xmlBlaster.jms.XBDestination;
import org.xmlBlaster.jms.XBMessageProducer;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.util.I_ReplaceContent;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.MsgQosData;

public class ReplSourceEngine implements I_Update, ReplicationConstants, I_ReplaceContent {
   
   private final static Logger log = Logger.getLogger(ReplSourceEngine.class.getName());
   
   private String replPrefix;
   private I_ChangePublisher publisher;
   private I_ReplSource source;
   private long messageSeq;
   
   public ReplSourceEngine(String replPrefix, I_ChangePublisher publisher, I_ReplSource source) {
      this.replPrefix = replPrefix;
      this.publisher = publisher;
      this.source = source;
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Update#update(java.lang.String, byte[], java.util.Map)
    */
   public final void update(String topic, InputStream is, Map attrMap) {

      String msg = new String();
      try {
         if (is != null)
            msg = new String(ReplManagerPlugin.getContent(is));
         // this comes from the requesting ReplSlave
         log.info("update for '" + topic + "' and msg='" + msg + "'");
         if (REPL_REQUEST_UPDATE.equals(msg)) {
            ClientProperty prop = (ClientProperty)attrMap.get("_sender");
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_sender' specified");
            String replManagerAddress = prop.getStringValue();

            String replTopic = source.getTopic();
            if (replTopic == null)
               throw new Exception("update for '" + msg + "' failed since the property 'mom.topicName' has not been defined. Check your DbWatcher Configuration file");

            prop = (ClientProperty)attrMap.get(SLAVE_NAME);
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
            String slaveName = prop.getStringValue();
            
            prop = (ClientProperty)attrMap.get(REPL_VERSION);
            String requestedVersion = null;
            if (prop != null)
               requestedVersion = prop.getStringValue();
            // this.dbSpecific.initiateUpdate(replTopic, destination, slaveName);
            prop = (ClientProperty)attrMap.get(INITIAL_FILES_LOCATION);
            String initialFilesLocation = null;
            if (prop != null)
               initialFilesLocation = prop.getStringValue();
            
            prop = (ClientProperty)attrMap.get(INITIAL_UPDATE_ONLY_REGISTER);
            boolean onlyRegister = false;
            if (prop != null)
               onlyRegister = prop.getBooleanValue();
            
            // String slaveName, String replVersion, String initialFilesLocation, boolean onlyRegister
            source.initialUpdate(replTopic, replManagerAddress, slaveName, requestedVersion, initialFilesLocation, onlyRegister);
            
         }
         else if (REPL_REQUEST_CANCEL_UPDATE.equals(msg)) {
            // do cancel
            ClientProperty prop = (ClientProperty)attrMap.get(SLAVE_NAME);
            if (prop == null)
               throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
            String slaveName = prop.getStringValue();
            source.cancelUpdate(slaveName);
         }
         else if (REPL_REQUEST_RECREATE_TRIGGERS.equals(msg)) {
            source.recreateTriggers();
         }
         else if (STATEMENT_ACTION.equals(msg)) {
            String sql = ((ClientProperty)attrMap.get(STATEMENT_ATTR)).getStringValue();
            boolean isHighPrio = ((ClientProperty)attrMap.get(STATEMENT_PRIO_ATTR)).getBooleanValue();
            long maxResponseEntries = ((ClientProperty)attrMap.get(MAX_ENTRIES_ATTR)).getLongValue();
            String statementId = ((ClientProperty)attrMap.get(STATEMENT_ID_ATTR)).getStringValue();
            String sqlTopic =  ((ClientProperty)attrMap.get(SQL_TOPIC_ATTR)).getStringValue();
            log.info("Be aware that the number of entries in the result set will be limited to '" + maxResponseEntries + "'. To change this use 'replication.sqlMaxEntries'");
            final boolean isMaster = true;
            byte[] response  = null;
            Exception ex = null;
            try {
               response = source.executeStatement(sql, maxResponseEntries, isHighPrio, isMaster, sqlTopic, statementId);
            }
            catch (Exception e) {
               response = "".getBytes();
               ex = e;
            }
            
            if (this.publisher != null) {
               Map map = new HashMap();
               map.put(MASTER_ATTR, this.replPrefix);
               map.put(STATEMENT_ID_ATTR, statementId);
               map.put("_command", STATEMENT_ACTION);
               if (ex != null)
                  map.put(EXCEPTION_ATTR, ex.getMessage());
               this.publisher.publish(sqlTopic, response, map);
            }
            if (ex != null)
               throw ex;
         }
         else if (INITIAL_UPDATE_START_BATCH.equals(msg)) {
            source.startInitialUpdateBatch();
         }
         else if (INITIAL_UPDATE_COLLECT.equals(msg)) {
            source.collectInitialUpdate();
         }
         else {
            log.warning("update from '" + topic + "' with request '" + msg + "'");
         }
      }
      catch (Throwable ex) {
         log.severe("An exception occured when processing the received update '" + msg + "': " + ex.getMessage());
         ex.printStackTrace();
      }
   }
   
   
   /**
    * Sending this message will reactivate the Dispatcher of the associated slave
    * @param topic
    * @param filename
    * @param replManagerAddress
    * @param slaveName
    * @param minKey
    * @param maxKey
    * @throws Exception
    */
   public final void sendInitialDataResponse(String[] slaveSessionNames, String replManagerAddress, long minKey, long maxKey) throws Exception {
      HashMap attrs = new HashMap();
      attrs.put("_destination", replManagerAddress);
      attrs.put("_command", "INITIAL_DATA_RESPONSE");
      attrs.put("_minReplKey", "" + minKey);
      attrs.put("_maxReplKey", "" + maxKey);
      attrs.put(SLAVE_NAME, SpecificDefault.toString(slaveSessionNames));
      if (publisher != null)
         publisher.publish("", "INITIAL_DATA_RESPONSE".getBytes(), attrs);
      else
         log.warning("request for sending initial response can not be done since no publisher configured");
   }

   /**
    * Sending this message will reactivate the Dispatcher of the associated slave
    * @param topic
    * @param filename
    * @param replManagerAddress
    * @param slaveName
    * @param minKey
    * @param maxKey
    * @throws Exception
    */
   public static void sendInitReplMsg(I_ChangePublisher publisher, 
                                        String[] slaveSessionNames, 
                                        String prefixWithVersion,
                                        String cascadeSlaveSessionName,
                                        String cascadeReplicationPrefix,
                                        String realInitialFilesLocation,
                                        boolean force) throws Exception {
      HashMap attrs = new HashMap();
      attrs.put("_destination", ReplManagerPlugin.SESSION_ID);
      attrs.put("_command", "INITIATE_REPLICATION");
      StringBuffer buf = new StringBuffer();
      for (int i=0; i < slaveSessionNames.length; i++) {
         if (i != 0)
            buf.append(",");
         buf.append(slaveSessionNames[i]);
      }
      attrs.put("_slaveSessionName", buf.toString());
      attrs.put("_prefixWithVersion", prefixWithVersion);
      attrs.put("_cascadeSlaveSessionName", cascadeSlaveSessionName);
      attrs.put("_cascadeReplicationPrefix", cascadeReplicationPrefix);
      attrs.put("_realInitialFilesLocation", realInitialFilesLocation);
      if (force)
         attrs.put("_force", "true");
      if (publisher != null)
         publisher.publish("", "INITIAL_DATA_RESPONSE".getBytes(), attrs);
      else
         log.warning("request for sending initial response can not be done since no publisher configured");
   }

   public void sendEndOfTransitionMessage(I_Info info, String initialDataTopic, String[] slaveSessionNames) throws JMSException {
      XBSession session = this.publisher.getJmsSession();
      XBDestination dest = new XBDestination(initialDataTopic, SpecificDefault.toString(slaveSessionNames));
      XBMessageProducer producer = new XBMessageProducer(session, dest);
      producer.setPriority(PriorityEnum.HIGH_PRIORITY.getInt());
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      String dumpId = "" + new Timestamp().getTimestamp();
      sendEndOfTransitionMessage(info, session, null, null, dumpId, producer);
   }
   
   public void sendEndOfTransitionMessage(I_Info info, XBSession session, String initialFilesLocation, String shortFilename, String dumpId, XBMessageProducer producer) throws JMSException {
      TextMessage  endMsg = session.createTextMessage();
      SqlInfo sqlInfo = new SqlInfo(info);
      SqlDescription description = new SqlDescription(info);

      description.setAttribute(END_OF_TRANSITION , "" + true);
      endMsg.setBooleanProperty(END_OF_TRANSITION , true);
      description.setAttribute(FILENAME_ATTR, shortFilename);
      endMsg.setStringProperty(FILENAME_ATTR, shortFilename);
      if (initialFilesLocation != null) {
         description.setAttribute(INITIAL_FILES_LOCATION, initialFilesLocation);
         endMsg.setStringProperty(INITIAL_FILES_LOCATION, initialFilesLocation);
         description.setAttribute(INITIAL_DATA_ID, dumpId);
         endMsg.setStringProperty(INITIAL_DATA_ID, dumpId);
      }
      sqlInfo.setDescription(description);
      endMsg.setText(sqlInfo.toXml(""));
      producer.send(endMsg);
   }
   
   public MsgQosData preparePubQos(MsgQosData qosData) {
      if (qosData == null)
         return qosData;
      if (qosData.getDestinations() != null && qosData.getDestinations().size() > 0) {
         qosData.addClientProperty(NUM_OF_TRANSACTIONS, -1L);
         return qosData;
      }
      prepareQosMap(qosData.getClientProperties());
      return qosData;
   }
   
   private void prepareQosMap(Map props) {
      messageSeq++;
      ClientProperty prop = new ClientProperty(MESSAGE_SEQ, Constants.TYPE_LONG, null, "" + messageSeq);
      props.put(MESSAGE_SEQ, prop);
      prop = new ClientProperty(REPL_KEY_ATTR, Constants.TYPE_LONG, null, "" + messageSeq);
      props.put(REPL_KEY_ATTR, prop);
      prop = new ClientProperty(TRANSACTION_SEQ, Constants.TYPE_LONG, null, "" + messageSeq);
      props.put(TRANSACTION_SEQ, prop);
      prop = new ClientProperty(ABSOLUTE_COUNT, Constants.TYPE_BOOLEAN, null, "true");
      props.put(ABSOLUTE_COUNT, prop);
   }
   
   public byte[] replace(byte[] oldContent, Map clientProperties) {
      prepareQosMap(clientProperties);
      return oldContent;
   }

   
}
