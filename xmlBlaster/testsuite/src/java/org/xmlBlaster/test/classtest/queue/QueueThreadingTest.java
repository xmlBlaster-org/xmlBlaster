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
 * Invoke: java -Djava.compiler= org.xmlBlaster.test.classtest.queue.QueueThreadingTest
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
public class QueueThreadingTest extends TestCase {
   private String ME = "QueueThreadingTest";
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
   public QueueThreadingTest(String name) {
      this(new Global(), name);
   }
   */

   public QueueThreadingTest(Global glob, String name, int currImpl) {
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
         ME = "QueueThreadingTest with class: " + this.constructor[this.count].getName();
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'" + ex.getMessage());
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()

         prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "SetupQueue");

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


// -----------------------------------------------------------------------------

  public void testPerfomancePutMultiThread() {
      String queueType = "unknown";
      try {
         performancePutMultiThread(this.numOfQueues, this.numOfMsg, this.sizeOfMsg);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing performancePutMultiThread probably due to failed initialization of the queue of type " + queueType + " " + ex.getMessage());
         ex.printStackTrace();
      }
   }


   public void performancePutMultiThread(int numOfQueues, int numOfMsg, int sizeOfMsg)
      throws XmlBlasterException {

      // set up the queues ....
      this.queues = new I_Queue[numOfQueues];
      int threadsPerQueue = 5;
      QueueThread[] queueThreads = new QueueThread[numOfQueues*threadsPerQueue];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      prop.setMaxMsg(numOfMsg*threadsPerQueue + 1);

      this.log.info(ME, "starting setting up " + numOfQueues + " queues");
      long t0 = System.currentTimeMillis();

      for (int i=0; i < numOfQueues; i++) {
         try {
            queues[i] = (I_Queue)this.constructor[this.count].newInstance(null);
         }
         catch (Exception ex) {
            fail(ME + " exception when constructing the queue object. " + ex.getMessage());
         }
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "perfomance/Put_" + i);
         queues[i].initialize(queueId, prop);
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
      this.log.info(ME, "setting up " + numOfQueues + " queues took " + (1.0*t1/1000) + " seconds");

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
      this.log.info(ME, "performacePutMultiThread putting " + numOfMsg + " messages in " + numOfQueues + " queues with " + threadsPerQueue + " threads per queue took " + (1.0*t1/1000) + " seconds");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < NUM_IMPL; i++) {
         suite.addTest(new QueueThreadingTest(glob, "testPerfomancePutMultiThread", i));
      }
      return suite;
   }

    /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.QueueThreadingTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);

      for (int i=0; i < NUM_IMPL; i++) {
         QueueThreadingTest testSub = new QueueThreadingTest(glob, "QueueThreadingTest", i);
         long startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testPerfomancePutMultiThread();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

