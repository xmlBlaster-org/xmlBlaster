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
 * Test JdbcQueuePlugin failover when persistent store disappears. 
 * <p>
 * Invoke: java org.xmlBlaster.test.classtest.queue.JdbcQueueTest
 * </p>
 * <p>
 * Test database with PostgreSQL:
 * </p>
 * <pre>
 * initdb /tmp/postgres
 * cp /var/lib/pgsql/data/pg_hba.conf /tmp/postgres    (edit host access)
 * createdb test
 * postmaster -i -D /tmp/postgres
 * </pre>
 * @see org.xmlBlaster.util.queue.I_Queue
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
   public static int NUM_IMPL = 2;
   public Constructor[] constructor = new Constructor[NUM_IMPL];
   public int count = 0;

   boolean suppressTest = false;

   /** Constructor for junit not possible since we need to run it 3 times
   public JdbcQueueTest(String name) {
      super(name);
      for (int i=0; i < NUM_IMPL; i++)
         initialize(new Global(), name, i);
   }
   */

   public JdbcQueueTest(Global glob, String name, int currImpl) {
      super(name);
      initialize(glob, name, currImpl);
   }

   private void initialize(Global glob, String name, int currImpl) {
      this.glob = (glob == null) ? new Global() : glob;
      this.numOfQueues = glob.getProperty().get("queues", 2);
      this.numOfMsg = glob.getProperty().get("entries", 100);
      this.sizeOfMsg = glob.getProperty().get("sizes", 10);
      this.suppressTest = false;
      this.count = currImpl;
      try {
         int i=0;
         Class clazz;
         clazz = JdbcQueuePlugin.class;
         this.constructor[i++] = clazz.getConstructor(null);
         clazz = CacheQueueInterceptorPlugin.class;
         this.constructor[i++] = clazz.getConstructor(null);
      }
      catch (Exception ex) {
         fail(ME + "exception occured in constructor: " + ex.getMessage());
      }
   }

   protected void setUp() {
      log = glob.getLog("test");
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "JdbcQueueTest with class: " + this.constructor[this.count].getName();
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
         this.suppressTest = true;
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

   public void testPutWithBreak() {
      String queueType = "JDBC";
      if (this.suppressTest) {
         log.error(ME, "JDBC test is not driven as no database was found");
         return;
      }
      try {
         putWithBreak();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutWithBreak probably due to failed initialization of the queue of type " + queueType + " " + ex.getMessage() );
         ex.printStackTrace();
      }
   }

   public void putWithBreak()
      throws XmlBlasterException {
      // set up the queues ....
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");

      prop.setMaxMsg(10000);
      try {
         queues[0] = (I_Queue)this.constructor[this.count].newInstance(null);
      }
      catch (Exception ex) {
         fail(ME + " exception when constructing the queue object. " + ex.getMessage());
      }
      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "putWithBreak");
      queues[0].initialize(queueId, prop);
      queues[0].clear();

      int num = 5;
      for (int i=0; i < num; i++) {
         try {
            this.log.info(ME, "put with break entry " + i + "/" + num + " please kill the DB manually to test reconnect");
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queues[0].getStorageId(), sizeOfMsg, true);
            queues[0].put(entry, false);
         }
         catch (Exception ex) {
            this.log.error(ME, ex.getMessage());
            //ex.printStackTrace();
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
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < NUM_IMPL; i++) {
         suite.addTest(new JdbcQueueTest(glob, "testPutWithBreak", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.JdbcQueueTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);

      for (int i=0; i < NUM_IMPL; i++) {
         long startTime = System.currentTimeMillis();

         JdbcQueueTest testSub = new JdbcQueueTest(glob, "JdbcQueueTest", i);
         testSub.setUp();
         testSub.testPutWithBreak();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

