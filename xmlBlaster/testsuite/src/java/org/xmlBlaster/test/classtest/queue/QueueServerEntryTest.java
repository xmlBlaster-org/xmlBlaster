package org.xmlBlaster.test.classtest.queue;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.client.key.PublishKey;

import java.util.ArrayList;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Test persistence of Queue entries. 
 * <p>
 * Invoke: java -Djava.compiler= org.xmlBlaster.test.classtest.queue.QueueServerEntryTest
 * </p>
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry
 * @see org.xmlBlaster.util.queue.I_Queue
 * @see org.xmlBlaster.util.queue.ram.RamQueuePlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin
 */
public class QueueServerEntryTest extends TestCase {
   private String ME = "QueueServerEntryTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(QueueServerEntryTest.class.getName());

   private I_Queue queue = null;

   public ArrayList queueList = null;
   public static String[] PLUGIN_TYPES = { new String("RAM"), new String("JDBC"), new String("CACHE") };
   public int count = 0;

   /** Constructor for junit
   public QueueServerEntryTest(String name) {
      this(new Global(), name);
   }
   */

   public QueueServerEntryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob        = glob;
      ME = "QueueServerEntryTest with class: " + PLUGIN_TYPES[this.count];

      this.count       = currImpl;
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         QueuePluginManager pluginManager = this.glob.getQueuePluginManager();
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         java.util.Properties pluginProp = (java.util.Properties)pluginInfo.getParameters();
         pluginProp.put("tableNamePrefix", "TEST");
         pluginProp.put("entriesTableName", "_entries");
         this.glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());

         QueuePropertyBase cbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "updateEntry");
         this.queue = pluginManager.getPlugin(PLUGIN_TYPES[currImpl], "1.0", queueId, cbProp);
         this.queue.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         log.severe("could not propertly set up the database: " + ex.getMessage());
      }
   }

   protected void setUp() {
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "QueueServerEntryTest with class: " + PLUGIN_TYPES[this.count];
      }
      catch (Exception ex) {
         log.severe("setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'" + ex.getMessage());
      }
      if (this.queue != null) {
         this.queue.shutdown();
      }
   }


   public void tearDown() {
      if (this.queue != null) {
         try {
            this.queue.clear();
            this.queue.shutdown();
            this.queue = null;
         }
         catch (Exception ex) {
            log.warning("error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp " + ex.getMessage());
         }
      }
   }


   // --------------------------------------------------------------------------

   public void testUpdateEntry() {
      String queueType = "unknown";
      try {
         updateEntry();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing UpdateEntry probably due to failed initialization of the queue of type " + queueType + " " + ex.getMessage());
         ex.printStackTrace();
      }
   }


   public void updateEntry() throws XmlBlasterException {

      // set up the queues ....
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      log.info("************ Starting updateEntry Test");

      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "updateEntry");
      this.queue.initialize(queueId, prop);
      this.queue.clear();

      try {

         byte[] content = "this is the content".getBytes();
         PublishKey key = new PublishKey(glob, "someKey");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><persistent/></qos>");
         MsgQosData msgQosData = publishQosServer.getData();
         ((MsgQosSaxFactory)glob.getMsgQosFactory()).sendRemainingLife(false); // so we can compare the toXml() directly
         // populate it
         String state = Constants.STATE_EXPIRED;
         msgQosData.setState(state);
         msgQosData.setSubscriptionId("someId");
         msgQosData.setPersistent(true);
         msgQosData.setForceUpdate(false);
         msgQosData.setReadonly(true);

         msgQosData.setSender(new SessionName(glob, "somebody"));
         msgQosData.setRedeliver(4);
         msgQosData.setQueueSize(1000L);
         msgQosData.setQueueIndex(500L);
         //msgQosData.addRouteInfo(null);
         //msgQosData.dirtyRead(null);//NodeId
         msgQosData.setPriority(PriorityEnum.LOW4_PRIORITY);
         msgQosData.setFromPersistenceStore(true);
         msgQosData.setLifeTime(4000L);
         msgQosData.setRemainingLifeStatic(6000L);
         MsgUnit msgUnit  = new MsgUnit(key.toXml(), content, msgQosData.toXml());

         log.fine("Testing" + msgQosData.toXml());

         SessionName receiver = new SessionName(glob, "receiver1");
         String subscriptionId = "subid";
         int redeliverCounter = 2;
         boolean updateOneway = true;
         org.xmlBlaster.engine.Global global = new org.xmlBlaster.engine.Global();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, queue.getStorageId());
         MsgQueueUpdateEntry entry = new MsgQueueUpdateEntry(global, msgWrapper, queue.getStorageId(),
                                         receiver, subscriptionId, updateOneway);
         entry.incrRedeliverCounter();
         entry.incrRedeliverCounter();

         queue.put(entry, false);
         I_QueueEntry returnEntry = queue.peek();

         boolean isUpdate = (returnEntry instanceof MsgQueueUpdateEntry);
         assertTrue("updateEntry: the return value is not an update ", isUpdate);
         
         MsgQueueUpdateEntry updateEntry = (MsgQueueUpdateEntry)returnEntry;

         assertEquals("The subscriptionId of the entry is different ", subscriptionId, updateEntry.getSubscriptionId());
         assertEquals("The state of the entry is different ", state, updateEntry.getState());
         assertEquals("The redeliverCounter of the entry is different ", redeliverCounter, updateEntry.getRedeliverCounter());
         assertEquals("The priority of the entry is different ", entry.getPriority(), updateEntry.getPriority());
         assertEquals("The oneway of the entry is different ", updateOneway, updateEntry.updateOneway());
         assertEquals("The persistent of the entry is different ", entry.isPersistent(), updateEntry.isPersistent());
         assertEquals("The receiver of the entry is different ", entry.getReceiver().toString(), updateEntry.getReceiver().toString());
         assertEquals("The uniqueId of the entry is different ", entry.getUniqueId(), updateEntry.getUniqueId());
         assertEquals("The msgUnitWrapperUniqueId of the entry is different ", entry.getMsgUnitWrapperUniqueId(), updateEntry.getMsgUnitWrapperUniqueId());
         assertEquals("The topic oid of the entry is different ", entry.getKeyOid(), updateEntry.getKeyOid());
         assertEquals("The topic oid of the entry is different ", entry.getStorageId().getId(), updateEntry.getStorageId().getId());
         log.info("SUCCESS: MsgQueueUpdateEntry: Persistent fields are read as expected");

         MsgUnit retMsgUnit = null;
         try {
            retMsgUnit = updateEntry.getMsgUnit();
         }
         catch (Throwable e) {  // Should not happen for RAM queue
            log.severe("Lookup failed, probably engine.Global has no Requestbroker, wi ignore the problem: " + e.getMessage());
            e.printStackTrace();
            return;
         }
         MsgQosData retMsgQosData = updateEntry.getMsgQosData();

         log.fine("Received" + retMsgQosData.toXml());

         // check message unit:
         assertEquals("The key of the message unit is different ", key.getOid(), retMsgUnit.getKeyData().getOid());
         assertEquals("The content of the message unit is different ", new String(retMsgUnit.getContent()), new String(content));
         //assertEquals("The qos of the message unit is different ", retMsgUnit.getQosData().isPersistent(), publishQosServer.isPersistent());
         //assertEquals("The qos of the message unit is different OLD="+oldXml+" NEW="+newXml, oldXml, newXml);

         assertEquals("msgQosData check failure: getSubscriptionId      ", msgQosData.getSubscriptionId(), retMsgQosData.getSubscriptionId());
//         assertEquals("msgQosData check failure: getPersistent             ", msgQosData.getPersistent(), retMsgQosData.getPersistent());
//         assertEquals("msgQosData check failure: getForceUpdate         ", msgQosData.getForceUpdate(), retMsgQosData.getForceUpdate());
//         assertEquals("msgQosData check failure: getReadOnly            ", msgQosData.getReadOnly(), retMsgQosData.getReadOnly());
         assertEquals("msgQosData check failure: getSender              ", msgQosData.getSender().toString(), retMsgQosData.getSender().toString());
         assertEquals("msgQosData check failure: getRedeliver           ", msgQosData.getRedeliver(), retMsgQosData.getRedeliver());
         assertEquals("msgQosData check failure: getQueueSize           ", msgQosData.getQueueSize(), retMsgQosData.getQueueSize());
         assertEquals("msgQosData check failure: getQueueIndex          ", msgQosData.getQueueIndex(), retMsgQosData.getQueueIndex());
         assertEquals("msgQosData check failure: getPriority            ", msgQosData.getPriority().getInt(), retMsgQosData.getPriority().getInt());
//         assertEquals("msgQosData check failure: getFromPersistentStore ", msgQosData.getFromPersistentStore(), retMsgQosData.getFromPersistentStore());
         assertEquals("msgQosData check failure: getLifeTime            ", msgQosData.getLifeTime(), retMsgQosData.getLifeTime());
         //assertEquals("msgQosData check failure: getRemainingLifeStatic ", msgQosData.getRemainingLifeStatic(), retMsgQosData.getRemainingLifeStatic());
         assertEquals("msgQosData check failure: receiver", receiver, updateEntry.getReceiver());

         queue.removeRandom(returnEntry); //just for cleaning up

         log.info("successfully completed tests for the updateEntry");

      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
         throw ex;
      }
   }


   // --------------------------------------------------------------------------

   public void testHistoryEntry() {
      String queueType = "unknown";
      try {
         historyEntry();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing HistoryEntry probably due to failed initialization of the queue of type " + queueType + " " + ex.getMessage());
         ex.printStackTrace();
      }
   }


   public void historyEntry() throws XmlBlasterException {

      // set up the queues ....
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      log.info("********* Starting historyEntry Test");
      StorageId queueId = new StorageId(Constants.RELATING_HISTORY, "historyEntry");
      this.queue.initialize(queueId, prop);
      this.queue.clear();

      try {

         byte[] content = "this is the content".getBytes();
         PublishKey key = new PublishKey(glob, "someKey");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><persistent/></qos>");
         MsgQosData msgQosData = publishQosServer.getData();
         ((MsgQosSaxFactory)glob.getMsgQosFactory()).sendRemainingLife(false); // so we can compare the toXml() directly
         // populate it
         msgQosData.setState("state");
         msgQosData.setSubscriptionId("someId");
         msgQosData.setPersistent(true);
         msgQosData.setForceUpdate(false);
         msgQosData.setReadonly(true);

         msgQosData.setSender(new SessionName(glob, "somebody"));
         msgQosData.setRedeliver(4);
         msgQosData.setQueueSize(1000L);
         msgQosData.setQueueIndex(500L);
         //msgQosData.addRouteInfo(null);
         //msgQosData.dirtyRead(null);//NodeId
         msgQosData.setPriority(PriorityEnum.LOW4_PRIORITY);
         msgQosData.setFromPersistenceStore(true);
         msgQosData.setLifeTime(4000L);
         msgQosData.setRemainingLifeStatic(6000L);
         MsgUnit msgUnit  = new MsgUnit(key.toXml(), content, msgQosData.toXml());

         log.fine("Testing" + msgQosData.toXml());

         org.xmlBlaster.engine.Global global = new org.xmlBlaster.engine.Global();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, queue.getStorageId());
         MsgQueueHistoryEntry entry = new MsgQueueHistoryEntry(global, msgWrapper, queue.getStorageId());

         queue.put(entry, false);
         I_QueueEntry returnEntry = queue.peek();

         boolean isHistory = (returnEntry instanceof MsgQueueHistoryEntry);
         assertTrue("historyEntry: the return value is not an update ", isHistory);
         
         MsgQueueHistoryEntry historyEntry = (MsgQueueHistoryEntry)returnEntry;

         assertEquals("The priority of the entry is different ", entry.getPriority(), historyEntry.getPriority());
         assertEquals("The persistent of the entry is different ", entry.isPersistent(), historyEntry.isPersistent());
         // The history queue is s LIFO, we have inverted the unique key
         assertEquals("The uniqueId of the entry is different ", entry.getUniqueId(), historyEntry.getUniqueId());
         assertEquals("The msgUnitWrapperUniqueId of the entry is different ", entry.getMsgUnitWrapperUniqueId(), historyEntry.getMsgUnitWrapperUniqueId());
         assertEquals("The topic oid of the entry is different ", entry.getKeyOid(), historyEntry.getKeyOid());
         assertEquals("The topic oid of the entry is different ", entry.getStorageId().getId(), historyEntry.getStorageId().getId());
         log.info("Persistent fields are read as expected");

         MsgUnit retMsgUnit = null;
         try {
            retMsgUnit = historyEntry.getMsgUnit();
         }
         catch (Throwable e) {  // Should not happen for RAM queue
            log.severe("Lookup failed, probably engine.Global has no Requestbroker, wi ignore the problem: " + e.getMessage());
            e.printStackTrace();
            return;
         }
         MsgQosData retMsgQosData = historyEntry.getMsgQosData();

         log.fine("Received" + retMsgQosData.toXml());

         // check message unit:
         assertEquals("The key of the message unit is different ", key.getOid(), retMsgUnit.getKeyData().getOid());
         assertEquals("The content of the message unit is different ", new String(retMsgUnit.getContent()), new String(content));
         //oldXml = oldXml.substring(oldXml.indexOf("remainingLife=");
         //String newXml = retMsgUnit.getQosData().toXml().trim();  TODO: strip remaining life first
         //assertEquals("The qos of the message unit is different OLD="+oldXml+" NEW="+newXml, oldXml, newXml); TODO

         assertEquals("msgQosData check failure: getSubscriptionId      ", msgQosData.getSubscriptionId(), retMsgQosData.getSubscriptionId());
         assertEquals("msgQosData check failure: getSender              ", msgQosData.getSender().toString(), retMsgQosData.getSender().toString());
         assertEquals("msgQosData check failure: getRedeliver           ", msgQosData.getRedeliver(), retMsgQosData.getRedeliver());
         assertEquals("msgQosData check failure: getQueueSize           ", msgQosData.getQueueSize(), retMsgQosData.getQueueSize());
         assertEquals("msgQosData check failure: getQueueIndex          ", msgQosData.getQueueIndex(), retMsgQosData.getQueueIndex());
         assertEquals("msgQosData check failure: getPriority            ", msgQosData.getPriority().getInt(), retMsgQosData.getPriority().getInt());
         assertEquals("msgQosData check failure: getLifeTime            ", msgQosData.getLifeTime(), retMsgQosData.getLifeTime());

         queue.removeRandom(returnEntry); //just for cleaning up

         log.info("successfully completed tests for the historyEntry");

      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         log.severe("exception occured : " + ex.getMessage());
         throw ex;
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         suite.addTest(new QueueServerEntryTest(glob, "testUpdateEntry", i));
         suite.addTest(new QueueServerEntryTest(glob, "testHistoryEntry", i));
      }
      return suite;
   }

    /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.QueueServerEntryTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);

      for (int i=0; i < PLUGIN_TYPES.length; i++) {

         QueueServerEntryTest testSub = new QueueServerEntryTest(glob, "QueueServerEntryTest", i);
         long startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testUpdateEntry();
         testSub.tearDown();
         /*
         testSub.setUp();
         testSub.testHistoryEntry();
         testSub.tearDown();
         */
         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info("time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

