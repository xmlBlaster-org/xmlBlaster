package org.xmlBlaster.test.classtest.queue;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
// import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
// import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.def.Constants;
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
 * Invoke: java org.xmlBlaster.test.classtest.queue.CacheQueueDisconnectTest
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
public class CacheQueueDisconnectTest extends TestCase {
   private String ME = "CacheQueueDisconnectTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(CacheQueueDisconnectTest.class.getName());
   private StopWatch stopWatch = new StopWatch();

   private int numOfQueues = 10;
   private int numOfMsg    = 10000;
   private long sizeOfMsg  = 100L;
   private I_Queue queue   = null;

   public ArrayList queueList = null;
   public static String[] PLUGIN_TYPES = { new String("CACHE") };
   public int count = 0;
   boolean suppressTest = false;

   /** Constructor for junit not possible since we need to run it 3 times
   public CacheQueueDisconnectTest(String name) {
      super(name);
      for (int i=0; i < NUM_IMPL; i++)
         initialize(new Global(), name, i);
   }
   */

   public CacheQueueDisconnectTest(Global glob, String name, int currImpl) {
      super(name);
      initialize(glob, name, currImpl);
   }

   private void initialize(Global glob, String name, int currImpl) {
      this.glob = Global.instance();


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
         prop.put("entriesTableName", "_entries");

         CbQueueProperty cbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "SetupQueue");

         this.queue = pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         this.queue.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         log.severe("setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'");
      }
   }

   protected void setUp() {

      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         ME = "CacheQueueDisconnectTest with class: " + PLUGIN_TYPES[this.count];
      }
      catch (Exception ex) {
         log.severe("setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'" + ex.getMessage());
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()
//         this.queue.destroy();
         this.queue.shutdown();
      }
      catch (Exception ex) {
         log.severe("could not propertly set up the database: " + ex.getMessage());
         this.suppressTest = true;
      }
   }

   public void tearDown() {
      try {
      this.queue.clear();
      this.queue.shutdown();
      }
      catch (Exception ex) {
         log.warning("error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp " + ex.getMessage());
      }
   }

   public void testPutWithBreak() {
      if (this.suppressTest) {
         log.severe("JDBC test is not driven as no database was found");
         return;
      }
      try {
         putWithBreak();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutWithBreak probably due to failed initialization of the queue of type " + PLUGIN_TYPES[this.count] + " " + ex.getMessage() );
         ex.printStackTrace();
      }
   }

   public void putWithBreak() throws XmlBlasterException {
      String me = ME + ".putWithBreak";
      // set up the queues ....
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      prop.setMaxEntries(10000);
      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "putWithBreak");
      queue.initialize(queueId, prop);
      queue.clear();

      int sweeps = 50;
      int num = 20;
      long sleepDelay = 50L;

      while (true) {
//      for (int j=0; j < sweeps; j++) {
         DummyEntry[] entries = new DummyEntry[num];
         for (int i=0; i < num; i++) {
            log.info("put one entry");
            entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), sizeOfMsg, true);
            queue.put(entries[i], false);
            try {
               Thread.sleep(sleepDelay);
            }
            catch (Exception ex) {
            }
         }

         ArrayList lst = queue.peek(-1, -1L);
         assertEquals(me + ": wrong number of entries in queue", entries.length, lst.size());
         for (int i=0; i < entries.length; i++) {
            long uniqueId = ((DummyEntry)lst.get(i)).getUniqueId();
            assertEquals(me + ": wrong order in entries: ", entries[i].getUniqueId(), uniqueId);
            log.info("remove one entry");
            queue.removeRandom(entries[i]);
         }
      }
  
//      log.info("successfully ended");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         suite.addTest(new CacheQueueDisconnectTest(glob, "testPutWithBreak", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.CacheQueueDisconnectTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(args);

      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         CacheQueueDisconnectTest testSub = new CacheQueueDisconnectTest(glob, "CacheQueueDisconnectTest", i);

         testSub.setUp();
         testSub.testPutWithBreak();
         testSub.tearDown();
      }
   }
}

