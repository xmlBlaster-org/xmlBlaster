package org.xmlBlaster.test.classtest.queue;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
// import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
// import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.PublishKey;

import org.xmlBlaster.util.queuemsg.DummyEntry;

import java.util.ArrayList;
import java.util.StringTokenizer;

import junit.framework.*;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry;
import org.xmlBlaster.util.queue.I_Queue;
import java.lang.reflect.Constructor;
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.qos.MsgQosSaxFactory;

/**
 * Test persistent of Queue entries. 
 * <p>
 * Invoke: java -Djava.compiler= org.xmlBlaster.test.classtest.queue.QueueServerEntryTest
 * </p>
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueUpdateEntry
 * @see org.xmlBlaster.engine.queuemsg.MsgQueueHistoryEntry
 * @see org.xmlBlaster.util.queue.I_Queue
 * @see org.xmlBlaster.util.queue.ram.RamQueuePlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class QueueServerEntryTest extends TestCase {
   private String ME = "QueueServerEntryTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

   private int numOfQueues = 10;
   private int numOfMsg = 10000;
   private int sizeOfMsg = 100;

   private I_Queue[] queues = null;

   public ArrayList queueList = null;
   public static int NUM_IMPL = 3;
   public Constructor[] constructor = new Constructor[NUM_IMPL];
   public int count = 0;

   /** Constructor for junit
   public QueueServerEntryTest(String name) {
      this(new Global(), name);
   }
   */

   public QueueServerEntryTest(Global glob, String name, int currImpl) {
      super(name);
      this.glob = glob;
      this.numOfQueues = glob.getProperty().get("queues", 2);
      this.numOfMsg = glob.getProperty().get("entries", 100);
      this.sizeOfMsg = glob.getProperty().get("sizes", 10);
      this.count = currImpl;

      try {
         Class clazz = RamQueuePlugin.class;
         this.constructor[0] = clazz.getConstructor(null);
         clazz = JdbcQueuePlugin.class;
         this.constructor[1] = clazz.getConstructor(null);
         clazz = CacheQueueInterceptorPlugin.class;
         this.constructor[2] = clazz.getConstructor(null);
      }
      catch (Exception ex) {
         fail(ME + "exception occured in constructor: " + ex.getMessage());
      }
   }

   protected void setUp() {
      log = glob.getLog("test");
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "QueueServerEntryTest with class: " + this.constructor[this.count].getName();
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'" + ex.getMessage());
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()

         prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
         StorageId queueId = new StorageId("cb", "SetupQueue");

         I_Queue jdbcQueue = (I_Queue)this.constructor[this.count].newInstance(null);
         jdbcQueue.initialize(queueId, prop);
         jdbcQueue.clear();

         jdbcQueue.destroy();

      }
      catch (Exception ex) {
         this.log.error(ME, "could not propertly set up the database: " + ex.getMessage());
      }

   }


   public void tearDown() {

      try {
         if (queues != null) {
            for (int i=0; i < queues.length; i++) {
               this.queues[i].destroy();
            }
         }
      }
      catch (Exception ex) {
         this.log.warn(ME, "error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp " + ex.getMessage());
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
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      this.log.info(ME, "************ Starting updateEntry Test");
      long t0 = System.currentTimeMillis();

      I_Queue queue = null;
      try {
         queue = (I_Queue)this.constructor[this.count].newInstance(null);
      }
      catch (Exception ex) {
         fail(ME + " exception when constructing the queue object. " + ex.getMessage());
      }
      queues[0] = queue;
      StorageId queueId = new StorageId("cb", "updateEntry");
      queue.initialize(queueId, prop);
      queue.clear();

      long t1 = System.currentTimeMillis() - t0;

      t0 = System.currentTimeMillis();

      try {

         byte[] content = "this is the content".getBytes();
         PublishKey key = new PublishKey(glob, "someKey");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><isDurable/></qos>");
         MsgQosData msgQosData = publishQosServer.getData();
         ((MsgQosSaxFactory)glob.getMsgQosFactory()).sendRemainingLife(false); // so we can compare the toXml() directly
         // populate it
         msgQosData.setState("state");
         msgQosData.setSubscriptionId("someId");
         msgQosData.setDurable(true);
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

         log.trace(ME, "Testing" + msgQosData.toXml());

         SessionName receiver = new SessionName(glob, "receiver1");
         String subscriptionId = "subid";
         String state = Constants.STATE_EXPIRED;
         int redeliverCounter = 2;
         org.xmlBlaster.engine.Global global = new org.xmlBlaster.engine.Global();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, queue.getStorageId());
         MsgQueueUpdateEntry entry = new MsgQueueUpdateEntry(global, msgWrapper, queue.getStorageId(),
                                         receiver, subscriptionId);
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
         assertEquals("The durable of the entry is different ", entry.isDurable(), updateEntry.isDurable());
         assertEquals("The receiver of the entry is different ", entry.getReceiver().toString(), updateEntry.getReceiver().toString());
         assertEquals("The uniqueId of the entry is different ", entry.getUniqueId(), updateEntry.getUniqueId());
         assertEquals("The msgUnitWrapperUniqueId of the entry is different ", entry.getMsgUnitWrapperUniqueId(), updateEntry.getMsgUnitWrapperUniqueId());
         assertEquals("The topic oid of the entry is different ", entry.getKeyOid(), updateEntry.getKeyOid());
         assertEquals("The topic oid of the entry is different ", entry.getStorageId().getId(), updateEntry.getStorageId().getId());
         log.info(ME, "SUCCESS: MsgQueueUpdateEntry: Persistent fields are read as expected");

         MsgUnit retMsgUnit = null;
         try {
            retMsgUnit = updateEntry.getMsgUnit();
         }
         catch (Throwable e) {  // Should not happen for RAM queue
            log.error(ME, "Lookup failed, probably engine.Global has no Requestbroker, wi ignore the problem: " + e.getMessage());
            e.printStackTrace();
            return;
         }
         MsgQosData retMsgQosData = updateEntry.getMsgQosData();

         log.trace(ME, "Received" + retMsgQosData.toXml());

         // check message unit:
         assertEquals("The key of the message unit is different ", key.getOid(), retMsgUnit.getKeyData().getOid());
         assertEquals("The content of the message unit is different ", new String(retMsgUnit.getContent()), new String(content));
         //assertEquals("The qos of the message unit is different ", retMsgUnit.getQosData().isDurable(), publishQosServer.isDurable());
         String oldXml = publishQosServer.toXml().trim();
         String newXml = retMsgUnit.getQosData().toXml().trim();
         //assertEquals("The qos of the message unit is different OLD="+oldXml+" NEW="+newXml, oldXml, newXml);

         assertEquals("msgQosData check failure: getSubscriptionId      ", msgQosData.getSubscriptionId(), retMsgQosData.getSubscriptionId());
//         assertEquals("msgQosData check failure: getDurable             ", msgQosData.getDurable(), retMsgQosData.getDurable());
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

         this.log.info(ME, "successfully completed tests for the updateEntry");

      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         this.log.error(ME, "exception occured : " + ex.getMessage());
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
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      this.log.info(ME, "********* Starting historyEntry Test");
      long t0 = System.currentTimeMillis();

      I_Queue queue = null;
      try {
         queue = (I_Queue)this.constructor[this.count].newInstance(null);
      }
      catch (Exception ex) {
         fail(ME + " exception when constructing the queue object. " + ex.getMessage());
      }
      queues[0] = queue;
      StorageId queueId = new StorageId("cb", "historyEntry");
      queue.initialize(queueId, prop);
      queue.clear();

      long t1 = System.currentTimeMillis() - t0;

      t0 = System.currentTimeMillis();

      try {

         byte[] content = "this is the content".getBytes();
         PublishKey key = new PublishKey(glob, "someKey");
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos><isDurable/></qos>");
         MsgQosData msgQosData = publishQosServer.getData();
         ((MsgQosSaxFactory)glob.getMsgQosFactory()).sendRemainingLife(false); // so we can compare the toXml() directly
         // populate it
         msgQosData.setState("state");
         msgQosData.setSubscriptionId("someId");
         msgQosData.setDurable(true);
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

         log.trace(ME, "Testing" + msgQosData.toXml());

         org.xmlBlaster.engine.Global global = new org.xmlBlaster.engine.Global();
         MsgUnitWrapper msgWrapper = new MsgUnitWrapper(glob, msgUnit, queue.getStorageId());
         MsgQueueHistoryEntry entry = new MsgQueueHistoryEntry(global, msgWrapper, queue.getStorageId());

         queue.put(entry, false);
         I_QueueEntry returnEntry = queue.peek();

         boolean isHistory = (returnEntry instanceof MsgQueueHistoryEntry);
         assertTrue("historyEntry: the return value is not an update ", isHistory);
         
         MsgQueueHistoryEntry historyEntry = (MsgQueueHistoryEntry)returnEntry;

         assertEquals("The priority of the entry is different ", entry.getPriority(), historyEntry.getPriority());
         assertEquals("The durable of the entry is different ", entry.isDurable(), historyEntry.isDurable());
         assertEquals("The uniqueId of the entry is different ", entry.getUniqueId(), historyEntry.getUniqueId());
         assertEquals("The msgUnitWrapperUniqueId of the entry is different ", entry.getMsgUnitWrapperUniqueId(), historyEntry.getMsgUnitWrapperUniqueId());
         assertEquals("The topic oid of the entry is different ", entry.getKeyOid(), historyEntry.getKeyOid());
         assertEquals("The topic oid of the entry is different ", entry.getStorageId().getId(), historyEntry.getStorageId().getId());
         log.info(ME, "Persistent fields are read as expected");

         MsgUnit retMsgUnit = null;
         try {
            retMsgUnit = historyEntry.getMsgUnit();
         }
         catch (Throwable e) {  // Should not happen for RAM queue
            log.error(ME, "Lookup failed, probably engine.Global has no Requestbroker, wi ignore the problem: " + e.getMessage());
            e.printStackTrace();
            return;
         }
         MsgQosData retMsgQosData = historyEntry.getMsgQosData();

         log.trace(ME, "Received" + retMsgQosData.toXml());

         // check message unit:
         assertEquals("The key of the message unit is different ", key.getOid(), retMsgUnit.getKeyData().getOid());
         assertEquals("The content of the message unit is different ", new String(retMsgUnit.getContent()), new String(content));
         String oldXml = publishQosServer.toXml().trim();
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

         this.log.info(ME, "successfully completed tests for the historyEntry");

      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         this.log.error(ME, "exception occured : " + ex.getMessage());
         throw ex;
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < NUM_IMPL; i++) {
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

      for (int i=0; i < NUM_IMPL; i++) {

         QueueServerEntryTest testSub = new QueueServerEntryTest(glob, "QueueServerEntryTest", i);
         long startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testUpdateEntry();
         testSub.tearDown();

         testSub.setUp();
         testSub.testHistoryEntry();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

