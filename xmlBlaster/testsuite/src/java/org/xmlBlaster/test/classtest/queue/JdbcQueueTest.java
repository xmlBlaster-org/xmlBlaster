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
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;

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
   private int numOfMsg    = 10000;
   private long sizeOfMsg  = 100L;
   private I_Queue queue   = null;

   public ArrayList queueList = null;
   public static String[] PLUGIN_TYPES = { new String("JDBC"), new String("CACHE") };
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
      this.glob = Global.instance();
      this.log = glob.getLog(null);

      this.numOfQueues = glob.getProperty().get("queues", 2);
      this.numOfMsg = glob.getProperty().get("entries", 100);
      this.sizeOfMsg = glob.getProperty().get("sizes", 10L);
      this.suppressTest = false;
      this.count = currImpl;

      try {
         String type = PLUGIN_TYPES[currImpl];
         this.glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         QueuePluginManager pluginManager = new QueuePluginManager(glob);
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, type, "1.0");
         java.util.Properties prop = (java.util.Properties)pluginInfo.getParameters();
         prop.put("tableNamePrefix", "TEST");
         prop.put("nodesTableName", "_nodes");
         prop.put("queuesTableName", "_queues");
         prop.put("entriesTableName", "_entries");

         CbQueueProperty cbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "SetupQueue");

         this.queue = pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         this.queue.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'");
      }
   }

   protected void setUp() {
      log = glob.getLog("test");
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "JdbcQueueTest with class: " + PLUGIN_TYPES[this.count];
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'" + ex.getMessage());
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()
         this.queue.destroy();
      }
      catch (Exception ex) {
         this.log.error(ME, "could not propertly set up the database: " + ex.getMessage());
         this.suppressTest = true;
      }
   }

   public void tearDown() {
      try {
         this.queue.destroy();
      }
      catch (Exception ex) {
         this.log.warn(ME, "error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp " + ex.getMessage());
      }
   }

   public void testPutWithBreak() {
      if (this.suppressTest) {
         log.error(ME, "JDBC test is not driven as no database was found");
         return;
      }
      try {
         putWithBreak();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutWithBreak probably due to failed initialization of the queue of type " + PLUGIN_TYPES[this.count] + " " + ex.getMessage() );
         ex.printStackTrace();
      }
   }

   public void putWithBreak()
      throws XmlBlasterException {
      // set up the queues ....
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      prop.setMaxMsg(10000);
      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "putWithBreak");
      queue.initialize(queueId, prop);
      queue.clear();

      int num = 5;
      for (int i=0; i < num; i++) {
         try {
            this.log.info(ME, "put with break entry " + i + "/" + num + " please kill the DB manually to test reconnect");
            DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), sizeOfMsg, true);
            queue.put(entry, false);
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

   public void testInitialEntries() {
      if (this.suppressTest) {
         log.error(ME, "JDBC test is not driven as no database was found");
         return;
      }
      try {
         initialEntries();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing InitialEntries probably due to failed initialization of the queue of type " + PLUGIN_TYPES[this.count] + " " + ex.getMessage() );
         ex.printStackTrace();
      }
   }

   public void initialEntries() throws XmlBlasterException {
      // set up the queues ....
      this.log.info(ME, "initialEntries test starts");
      QueuePropertyBase cbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      cbProp.setMaxMsg(10000L);
      cbProp.setMaxBytes(200000L);
      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "initialEntries");

      try {
         String type = PLUGIN_TYPES[this.count];
         this.glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         QueuePluginManager pluginManager = new QueuePluginManager(glob);
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, type, "1.0");
         java.util.Properties prop = (java.util.Properties)pluginInfo.getParameters();
         prop.put("tableNamePrefix", "TEST");
         prop.put("nodesTableName", "_nodes");
         prop.put("queuesTableName", "_queues");
         prop.put("entriesTableName", "_entries");
         I_Queue tmpQueue = pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         tmpQueue.clear();
         // add some persistent entries and then shutdown ...
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), 100, true);
         tmpQueue.put(entry, false);
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), 100, true);
         tmpQueue.put(entry, false);
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), 100, true);
         tmpQueue.put(entry, false);
         tmpQueue.shutdown(); // to allow to initialize again
         I_Queue tmpQueue2 = pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         long numOfEntries = tmpQueue2.getNumOfEntries();
         assertEquals("Wrong number of entries in queue", 3L, numOfEntries);
         ArrayList lst = tmpQueue2.peek(-1, -1L);
         assertEquals("Wrong number of entries retrieved from queue", 3, lst.size());
         queue.shutdown();
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'");
         ex.printStackTrace();
         assertTrue("exception occured when testing initialEntries", false);
      }
      this.log.info(ME, "initialEntries test successfully ended");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         suite.addTest(new JdbcQueueTest(glob, "testPutWithBreak", i));
         suite.addTest(new JdbcQueueTest(glob, "testInitialEntries", i));
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

      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         JdbcQueueTest testSub = new JdbcQueueTest(glob, "JdbcQueueTest", i);
         testSub.setUp();
         testSub.testInitialEntries();
         testSub.tearDown();

         testSub.setUp();
         long startTime = System.currentTimeMillis();
         testSub.testPutWithBreak();
         long usedTime = System.currentTimeMillis() - startTime;
         testSub.tearDown();

         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

