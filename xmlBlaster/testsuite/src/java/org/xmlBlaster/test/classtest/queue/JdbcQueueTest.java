package org.xmlBlaster.test.classtest.queue;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
// import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
// import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.QueuePropertyBase;

import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.client.qos.PublishQos;

import org.xmlBlaster.util.queuemsg.DummyEntry;

import java.util.ArrayList;
import java.util.StringTokenizer;

import junit.framework.*;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.queuemsg.MsgQueueUpdateEntry;
import org.xmlBlaster.util.queue.I_Queue;
import java.lang.reflect.Constructor;
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;

/**
 * Test RamQueuePlugin.
 * <p>
 * The sorting order is priority,timestamp:
 * </p>
 * <pre>
 *   ->    5,100 - 5,98 - 5,50 - 9,3000 - 9,2500   ->
 * </pre>
 * <p>
 * As 9 is highest priority it is the first to be taken out.<br />
 * As we need to maintain the timely sequence and
 * id is a timestamp in (more or less) nano seconds elapsed since 1970)
 * the id 2500 (it is older) has precedence to the id 3000
 * </p>
 * <p>
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.queue.JdbcQueueTest
 * </p>
 * <p>
 * Configuration example:
 * </p>
 * <pre>
 * JdbcDriver.drivers=org.postgresql.Driver
 * JdbcDriver.postgresql.mapping=string=text,longint=bigint,int=integer,boolean=boolean
 * queue.callback.url=jdbc:postgresql://localhost/test
 * queue.callback.user=postgres
 * queue.callback.password=
 * </pre>
 * <p>
 * Test database with PostgreSQL:
 * </p>
 * <pre>
 * initdb /tmp/postgres
 * cp /var/lib/pgsql/data/pg_hba.conf /tmp/postgres    (edit host access)
 * createdb test
 * postmaster -i -D /tmp/postgres
 * </pre>
 * @see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(MsgQueueEntry)
 * @see org.xmlBlaster.util.queue.I_Queue
 * @see org.xmlBlaster.util.queue.ram.RamQueuePlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class JdbcQueueTest extends TestCase {
   private String ME = "JdbcQueueTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

   private int numOfQueues = 10;
   private int numOfMsg = 10000;
   private int sizeOfMsg = 100;

   private I_Queue[] queues = null;

   public ArrayList queueList = null;
   private Constructor[] constructor;
   public int count = 0;


/*
   public JdbcQueueTest(String name) {
      this(Global.instance(), name);
   }
*/

   public JdbcQueueTest(Global glob, String name) {
      super(name);
      this.glob = glob;
      this.numOfQueues = glob.getProperty().get("queues", 2);
      this.numOfMsg = glob.getProperty().get("entries", 100);
      this.sizeOfMsg = glob.getProperty().get("sizes", 10);
      this.constructor = new Constructor[3];

      try {
         Class clazz = RamQueuePlugin.class;
         this.constructor[0] = clazz.getConstructor(null);
         clazz = JdbcQueuePlugin.class;
         this.constructor[1] = clazz.getConstructor(null);
         clazz = CacheQueueInterceptorPlugin.class;
         this.constructor[2] = clazz.getConstructor(null);
      }
      catch (Exception ex) {
         fail(ME + "exception occured in constructor: " + ex.toString());
      }
   }

   protected void setUp() {
      glob = Global.instance();
      log = glob.getLog("test");
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "JdbcQueueTest with class: " + this.constructor[this.count].getName();
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'");
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()

         prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
         String queueId = "cb:SetupQueue";

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
         this.log.warn(ME, "error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp");
      }
   }


   // --------------------------------------------------------------------------

   public void testUpdateEntry() {
      String queueType = "unknown";
      try {
         updateEntry();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing UpdateEntry probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void updateEntry() throws XmlBlasterException {

      // set up the queues ....
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      this.log.info(ME, "starting updateEntry Test");
      long t0 = System.currentTimeMillis();

      I_Queue queue = null;
      try {
         queue = (I_Queue)this.constructor[this.count].newInstance(null);
      }
      catch (Exception ex) {
         fail(ME + " exception when constructing the queue object. " + ex.toString());
      }
      queues[0] = queue;
      queue.initialize("cb:updateEntry", prop);
      queue.clear();

      long t1 = System.currentTimeMillis() - t0;

      t0 = System.currentTimeMillis();

      try {

         byte[] content = "this is the content".getBytes();
         String key = "<key>some key</key>";
         String qos = "<qos>some qos</qos>";
         MessageUnit msgUnit  = new MessageUnit(key, content, qos);
         MsgQosData msgQosData = new MsgQosData(this.glob);
         // populate it
         msgQosData.setState("state");
         msgQosData.setVolatile(true);
         msgQosData.setSubscriptionId("subscription");
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

         String oid = "updateTest";
         SessionName receiver = new SessionName(glob, "receiver1");
         MsgQueueUpdateEntry entry = new MsgQueueUpdateEntry(glob, msgUnit, queue, oid, msgQosData, receiver);

         queue.put(entry, false);
         I_QueueEntry returnEntry = queue.peek();

         boolean isUpdate = (returnEntry instanceof MsgQueueUpdateEntry);
         assertTrue("updateEntry: the return value is not an update ", isUpdate);
         MsgQueueUpdateEntry updateEntry = (MsgQueueUpdateEntry)returnEntry;
         MessageUnit retMsgUnit = updateEntry.getMessageUnit();
         MsgQosData retMsgQosData = updateEntry.getMsgQosData();

         assertEquals("The uniqueId of the entry is different ", updateEntry.getUniqueId(), entry.getUniqueId());

         // check message unit:
         assertEquals("The key of the message unit is different ", retMsgUnit.getKey(), key);
         assertEquals("The content of the message unit is different ", new String(retMsgUnit.getContent()), new String(content));
         assertEquals("The qos of the message unit is different ", retMsgUnit.getQos(), qos);

         assertEquals("The oid is different ", updateEntry.getKeyOid(), oid);

//         assertEquals("msgQosData check failure: getVolatile            ", msgQosData.getVolatile(), retMsgQosData.getVolatile());
         assertEquals("msgQosData check failure: getSubscriptionId      ", msgQosData.getSubscriptionId(), retMsgQosData.getSubscriptionId());
//         assertEquals("msgQosData check failure: getDurable             ", msgQosData.getDurable(), retMsgQosData.getDurable());
//         assertEquals("msgQosData check failure: getForceUpdate         ", msgQosData.getForceUpdate(), retMsgQosData.getForceUpdate());
//         assertEquals("msgQosData check failure: getReadOnly            ", msgQosData.getReadOnly(), retMsgQosData.getReadOnly());
         assertEquals("msgQosData check failure: getSender              ", msgQosData.getSender(), retMsgQosData.getSender());
         assertEquals("msgQosData check failure: getRedeliver           ", msgQosData.getRedeliver(), retMsgQosData.getRedeliver());
         assertEquals("msgQosData check failure: getQueueSize           ", msgQosData.getQueueSize(), retMsgQosData.getQueueSize());
         assertEquals("msgQosData check failure: getQueueIndex          ", msgQosData.getQueueIndex(), retMsgQosData.getQueueIndex());
         assertEquals("msgQosData check failure: getPriority            ", msgQosData.getPriority().getInt(), retMsgQosData.getPriority().getInt());
//         assertEquals("msgQosData check failure: getFromPersistentStore ", msgQosData.getFromPersistentStore(), retMsgQosData.getFromPersistentStore());
         assertEquals("msgQosData check failure: getLifeTime            ", msgQosData.getLifeTime(), retMsgQosData.getLifeTime());
         assertEquals("msgQosData check failure: getRemainingLifeStatic ", msgQosData.getRemainingLifeStatic(), retMsgQosData.getRemainingLifeStatic());
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

   public void testPerfomancePut() {
      String queueType = "unknown";
      try {
         performancePut(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
      }
   }


   public void performancePut(int numOfQueues, int numOfMsg, int sizeOfMsg)
      throws XmlBlasterException {

      // set up the queues ....
      this.queues = new I_Queue[numOfQueues];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      this.log.info(ME, "performancePut: number of queues: " + numOfQueues + ", number of messages per queue: " + numOfMsg + ", size of each message: " + sizeOfMsg);

      prop.setMaxMsg(numOfMsg + 1);

      this.log.info(ME, "starting setting up " + numOfQueues + " queues");
      long t0 = System.currentTimeMillis();

      for (int i=0; i < numOfQueues; i++) {
         try {
            queues[i] = (I_Queue)this.constructor[this.count].newInstance(null);
         }
         catch (Exception ex) {
            fail(ME + " exception when constructing the queue object. " + ex.toString());
         }

         queues[i].initialize("cb:perfomance/Put_" + i, prop);
         queues[i].clear();
      }

      long t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      ArrayList entryList = new ArrayList(numOfMsg*numOfQueues);
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[i], sizeOfMsg, true);
            entryList.add(entry);
            queues[i].put(entry, false);
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "putting " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");


      // peek all messages one single sweep ...
      t0 = System.currentTimeMillis();
      for (int i=0; i < numOfQueues; i++) {
         queues[i].peek(numOfMsg, -1L);
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "peek in one sweep " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // remove all messages one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            queues[i].removeRandom(((DummyEntry)entryList.get(i+j*numOfQueues)));
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "remove one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

   }


   // --------------------------------------------------------------------------
   public void testPerfomanceMultiPut() {
      String queueType = "unknown";
      try {
         performanceMultiPut(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void performanceMultiPut(int numOfQueues, int numOfMsg, int sizeOfMsg)
      throws XmlBlasterException {

      // set up the queues ....
      this.queues = new I_Queue[numOfQueues];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");

      prop.setMaxMsg(numOfMsg + 1);

      this.log.info(ME, "starting setting up " + numOfQueues + " queues");
      long t0 = System.currentTimeMillis();

      for (int i=0; i < numOfQueues; i++) {
         try {
            queues[i] = (I_Queue)this.constructor[this.count].newInstance(null);
         }
         catch (Exception ex) {
            fail(ME + " exception when constructing the queue object. " + ex.toString());
         }
         queues[i].initialize("cb:perfomance/MultiPut_" + i, prop);
         queues[i].clear();
      }

      long t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      ArrayList entryList = new ArrayList(numOfQueues*numOfMsg);
      t0 = System.currentTimeMillis();
      for (int i=0; i < numOfQueues; i++) {
         DummyEntry[] entries = new DummyEntry[numOfMsg];
         for (int j=0; j < numOfMsg; j++) {
            entries[j] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[i], sizeOfMsg, true);
            entryList.add(entries[j]);
         }
         queues[i].put(entries, false);

      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "multi-putting " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // peek one msg and then remove it one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            I_QueueEntry entry = queues[i].peek();
            queues[i].removeRandom(entry);
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "peek /removeRandom one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // remove all messages one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            queues[i].removeRandom(((I_QueueEntry)entryList.get(i+j*numOfQueues)));
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "remove one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");
   }


// -----------------------------------------------------------------------------

  public void testPerfomancePutMultiThread() {
      String queueType = "unknown";
      try {
         performancePutMultiThread(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing performancePutMultiThread probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void performancePutMultiThread(int numOfQueues, int numOfMsg, int sizeOfMsg)
      throws XmlBlasterException {

      // set up the queues ....
      this.queues = new I_Queue[numOfQueues];
      int threadsPerQueue = 5;
      QueueThread[] queueThreads = new QueueThread[numOfQueues*threadsPerQueue];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      prop.setMaxMsg(numOfMsg*threadsPerQueue + 1);

      this.log.info(ME, "starting setting up " + numOfQueues + " queues");
      long t0 = System.currentTimeMillis();

      for (int i=0; i < numOfQueues; i++) {
         try {
            queues[i] = (I_Queue)this.constructor[this.count].newInstance(null);
         }
         catch (Exception ex) {
            fail(ME + " exception when constructing the queue object. " + ex.toString());
         }
         queues[i].initialize("cb:perfomance/Put_" + i, prop);
         queues[i].clear();
         try {
            for (int j=0; j < threadsPerQueue; j++) 
               queueThreads[i*threadsPerQueue+j] = new QueueThread(glob, "queue"+i, queues[i], this.log, numOfMsg, sizeOfMsg);
         }
         catch (Exception ex) {
            this.log.error(ME, "Exception when instantiating the thread " + i + " " + ex.getMessage());
         }
      }

      long t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      t0 = System.currentTimeMillis();

      for (int i=0; i < numOfQueues*threadsPerQueue; i++) queueThreads[i].start();

      try {
         while (QueueThread.counter > 0) {
            Thread.currentThread().sleep(100L);
         }
      }
      catch (Exception ex) {
      }

      t1 = System.currentTimeMillis() - t0;
      this.log.error(ME, "performacePutMultiThread putting " + numOfMsg + " messages in " + numOfQueues + " queues with " + threadsPerQueue + " threads per queue took " + (1.0*t1/1000) + " seconds");
   }


// -----------------------------------------------------------------------------

   public void testPutWithBreak() {
      String queueType = "JDBC";
      try {
         putWithBreak();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutWithBreak probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void putWithBreak()
      throws XmlBlasterException {
      // set up the queues ....
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");

      prop.setMaxMsg(10000);
      try {
         queues[0] = (I_Queue)this.constructor[this.count].newInstance(null);
      }
      catch (Exception ex) {
         fail(ME + " exception when constructing the queue object. " + ex.toString());
      }
      queues[0].initialize("cb:putWithBreak", prop);
      queues[0].clear();

      for (int i=0; i < 20; i++) {
         try {
            this.log.info(ME, "put with break entry " + i + " please cut the DB");
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[0], sizeOfMsg, true);
            queues[0].put(entry, false);
         }
         catch (Exception ex) {
            this.log.error(ME, ex.toString());
            ex.printStackTrace();
         }
         finally {
            try {
               Thread.currentThread().sleep(6000L);
            }
            catch (Exception ex) {
            }
         }
      }
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.JdbcQueueTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);
      JdbcQueueTest testSub = new JdbcQueueTest(glob, "JdbcQueueTest");
/*
      testSub.setUp();
      testSub.testPutWithBreak();
      testSub.tearDown();
*/
      long startTime = System.currentTimeMillis();

      for (testSub.count=0; testSub.count < 3; testSub.count++) {

         testSub.setUp();
         testSub.testUpdateEntry();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPerfomancePut();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPerfomanceMultiPut();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPerfomancePutMultiThread();
         testSub.tearDown();


         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

