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
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.helper.CbQueueProperty;
import org.xmlBlaster.engine.helper.QueuePropertyBase;

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
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.queue.QueueExtendedTest
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
 * @see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(I_QueueEntry)
 * @see org.xmlBlaster.util.queue.I_Queue
 * @see org.xmlBlaster.util.queue.ram.RamQueuePlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class QueueExtendedTest extends TestCase {
   private String ME = "QueueExtendedTest";
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
   public QueueExtendedTest(String name) {
      this(new Global(), name);
   }
   */

   public QueueExtendedTest(Global glob, String name, int currImpl) {
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
         ME = "QueueExtendedTest with class: " + this.constructor[this.count].getName();
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

   public void testPerfomancePut() {
      String queueType = "unknown";
      try {
         performancePut(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType + ": " + ex.getMessage());
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
            fail(ME + " exception when constructing the queue object. " + ex.getMessage());
         }

         StorageId queueId = new StorageId("cb", "perfomance/Put_" + i);
         queues[i].initialize(queueId, prop);
         queues[i].clear();
      }

      long t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      ArrayList entryList = new ArrayList(numOfMsg*numOfQueues);
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[i].getStorageId(), sizeOfMsg, true);
            entryList.add(entry);
            queues[i].put(entry, false);
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "putting " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");


      // peek all messages one single sweep ...
      t0 = System.currentTimeMillis();
      for (int i=0; i < numOfQueues; i++) {
         queues[i].peek(numOfMsg, -1L);
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "peek in one sweep " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // remove all messages one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            queues[i].removeRandom(((DummyEntry)entryList.get(i+j*numOfQueues)));
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "remove one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

   }


   // --------------------------------------------------------------------------
   public void testPerfomanceMultiPut() {
      String queueType = "unknown";
      try {
         performanceMultiPut(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType + " " + ex.getMessage());
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
            fail(ME + " exception when constructing the queue object. " + ex.getMessage());
         }
         StorageId queueId = new StorageId("cb", "perfomance/MultiPut_" + i);
         queues[i].initialize(queueId, prop);
         queues[i].clear();
      }

      long t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      ArrayList entryList = new ArrayList(numOfQueues*numOfMsg);
      t0 = System.currentTimeMillis();
      for (int i=0; i < numOfQueues; i++) {
         DummyEntry[] entries = new DummyEntry[numOfMsg];
         for (int j=0; j < numOfMsg; j++) {
            entries[j] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[i].getStorageId(), sizeOfMsg, true);
            entryList.add(entries[j]);
         }
         queues[i].put(entries, false);

      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "multi-putting " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // peek one msg and then remove it one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            I_QueueEntry entry = queues[i].peek();
            queues[i].removeRandom(entry);
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "peek /removeRandom one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

      // remove all messages one by one ..
      t0 = System.currentTimeMillis();
      for (int j=0; j < numOfMsg; j++) {
         for (int i=0; i < numOfQueues; i++) {
            queues[i].removeRandom(((I_QueueEntry)entryList.get(i+j*numOfQueues)));
         }
      }
      t1 = System.currentTimeMillis() - t0;
      this.log.info(ME, "remove one by one " + numOfMsg + " messages in " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < NUM_IMPL; i++) {
         suite.addTest(new QueueExtendedTest(glob, "testPerfomancePut", i));
         suite.addTest(new QueueExtendedTest(glob, "testPerfomanceMultiPut", i));
      }
      return suite;
   }

    /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.QueueExtendedTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);

      for (int i=0; i < NUM_IMPL; i++) {

         QueueExtendedTest testSub = new QueueExtendedTest(glob, "QueueExtendedTest", i);
         long startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testPerfomancePut();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPerfomanceMultiPut();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

