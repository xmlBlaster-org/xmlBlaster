package org.xmlBlaster.test.classtest.queue;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.ram.RamQueuePlugin;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.client.qos.PublishQos;

import org.xmlBlaster.util.queuemsg.DummyEntry;
import org.xmlBlaster.util.enum.PriorityEnum;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Hashtable;

import junit.framework.*;
import java.util.Enumeration;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Test CacheQueueInterceptorPlugin.
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
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.queue.CacheQueueTest
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
public class CacheQueueTest extends TestCase {
   private String ME = "CacheQueueTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

//   private I_Queue[] queues = null;
   private CacheQueueInterceptorPlugin queue = null;

//   public ArrayList queueList = null;
   public ArrayList queueList = null;

   public CacheQueueTest(String name) {
      this(Global.instance(), name);
   }

   public CacheQueueTest(Global glob, String name) {
      super(name);
      this.glob = glob;
   }

   protected void setUp() {
      glob = Global.instance();
      log = glob.getLog(null);
      QueuePropertyBase cbProp = null;

      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");

         cbProp = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "SetupQueue");

         this.glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         QueuePluginManager pluginManager = new QueuePluginManager(glob);
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         java.util.Properties prop = (java.util.Properties)pluginInfo.getParameters();
         prop.put("tableNamePrefix", "TEST");
         prop.put("nodesTableName", "_nodes");
         prop.put("queuesTableName", "_queues");
         prop.put("entriesTableName", "_entries");
         this.glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());

         pluginInfo = new PluginInfo(glob, pluginManager, "CACHE", "1.0");
         this.queue = (CacheQueueInterceptorPlugin)pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         this.queue.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         this.log.error(ME, "could not propertly set up the database: " + ex.getMessage());
      }

   }


   public void tearDown() {
      try {
         this.queue.destroy();
      }
      catch (Exception ex) {
         this.log.warn(ME, "error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp");
      }
   }


   public void testConfig() {
      String queueType = "CACHE";
      try {
         config();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void config()
      throws XmlBlasterException {

      // set up the queues ....
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      prop.setMaxMsg(20L);
      prop.setMaxMsgCache(10L);
      prop.setMaxBytes(500L);
      prop.setMaxBytesCache(200L);

      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "CacheQueueTest/config");

      // this.queue = new CacheQueueInterceptorPlugin();
      this.queue.initialize(queueId, prop);

      long persistentSize = this.queue.getPersistentQueue().getMaxNumOfBytes();
      long persistentMsg  = this.queue.getPersistentQueue().getMaxNumOfEntries();
      long transientSize  = this.queue.getTransientQueue().getMaxNumOfBytes();
      long transientMsg   = this.queue.getTransientQueue().getMaxNumOfEntries();

      assertEquals("Wrong persistent size", 500L, persistentSize);
      assertEquals("Wrong persistent num of msg", 20L, persistentMsg);
      if (200L != transientSize)
         log.error(ME, "ERROR: Wrong transient size" + this.queue.getTransientQueue().toXml(""));
      assertEquals("Wrong transient size" + this.queue.getTransientQueue().toXml(""), 200L, transientSize);
      assertEquals("Wrong num of transient msg", 10L, transientMsg);

   }


   /**
    * returns true if the combination is possible, false otherwise
    */
   private boolean checkIfPossible(long transientNumOfBytes, long persistentNumOfBytes,
      long maxTransientNumOfBytes, long maxPersistentNumOfBytes) {
      this.log.trace(ME, "checkIfPossible: transient number of bytes: " + transientNumOfBytes + " of (max) " + maxTransientNumOfBytes + " , persistent number of bytes: " + persistentNumOfBytes + " of (max) " + maxPersistentNumOfBytes);
      if (transientNumOfBytes > maxTransientNumOfBytes) return false;
      if (persistentNumOfBytes > maxPersistentNumOfBytes) return false;
      return true;
   }


   public void testPutPeekRemove() {
      String queueType = "CACHE";
      try {
         putPeekRemove();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void putPeekRemove() throws XmlBlasterException {

      // set up the queues ....

      // every content is 80 bytes which gives an entry size of 100 bytes (80+20)

      long maxNumOfBytesCache[] = {700L, 10000L};
      long maxNumOfBytes[] = {700L, 50000L};
      int numOfTransientEntries[] = { 2, 50, 200};
      int numOfPersistentEntries[] =  { 0, 2, 50, 200};
//      int numPrio[] = { 1, 5, 9};

//      int it=0, id=0, ic=0, is=0;
//      try {
         for (int ic=0; ic < maxNumOfBytesCache.length; ic++) {
            for (int is=0; is < maxNumOfBytes.length; is++) {
               log.info(ME, "**** TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is]);
               // a new queue each time here ...
               QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
               prop.setMaxMsg(2000L);
               prop.setMaxMsgCache(1000L);
               prop.setMaxBytes(maxNumOfBytes[is]);
               prop.setMaxBytesCache(maxNumOfBytesCache[ic]);
               StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "CacheQueueTest/jdbc" + maxNumOfBytes[is] + "/ram" + maxNumOfBytesCache[ic]);

//               this.queue = new CacheQueueInterceptorPlugin();
               this.queue.destroy();
               this.queue.initialize(queueId, prop);

               for (int it=0; it < numOfTransientEntries.length; it++) {
                  // entry.setPrio(4+(it%3));
                  for (int id=0; id < numOfPersistentEntries.length; id++) {

                     log.info(ME, "**** SUB-TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is] +
                                   " -> numOfTransientEntries["+it+"]=" + numOfTransientEntries[it] + " numOfPersistentEntries["+id+"]=" + numOfPersistentEntries[id]);
                     if (!this.queue.isShutdown()) this.queue.shutdown();
                     this.queue.initialize(queueId, prop);
                     this.queue.clear();

                     long maxPersistentNumOfBytes = maxNumOfBytes[is];
                     long maxTransientNumOfBytes = maxNumOfBytesCache[ic];
                     long transientNumOfBytes  = 0L;
                     long persistentNumOfBytes  = 0L;

                     assertEquals(ME + " the number of bytes of the queue should be zero ", 0L, queue.getNumOfBytes());
                     assertEquals(ME + " the number of entries in the queue should be zero ", 0L, queue.getNumOfEntries());
                     assertEquals(ME + " the number of bytes of the persistent entries in the queue should be zero ", 0L, queue.getNumOfPersistentBytes());
                     assertEquals(ME + " the number of persistent entries in the queue should be zero ", 0L, queue.getNumOfPersistentEntries());

                     assertEquals(ME + " the maximum number of entries is wrong ", maxNumOfBytes[is], queue.getMaxNumOfBytes());

                     try {

                        this.queue.clear();
                        transientNumOfBytes = 100 * numOfTransientEntries[it];
                        persistentNumOfBytes =100 * numOfPersistentEntries[id];
                        // prepare the inputs .
                        Hashtable[] inputTable = new Hashtable[3];
                        for (int i=0; i < 3; i++) inputTable[i] = new Hashtable();

                        DummyEntry[] transients = new DummyEntry[numOfTransientEntries[it]];
                        DummyEntry[] persistentEntries    = new DummyEntry[numOfPersistentEntries[id]];

                        this.log.info(ME, "putPeekRemove " + queueId + " persistent: " + persistentEntries.length + " transient: " + transients.length);

                        boolean persistent = false;
                        for (int i=0; i < transients.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enum = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enum, this.queue.getStorageId(), 80, persistent);
                           transients[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }
                        persistent = true;
                        for (int i=0; i < persistentEntries.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enum = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enum, this.queue.getStorageId(), 80, persistent);
                           persistentEntries[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }

                        // do the test here ....
                        assertEquals(ME + " number of persistent entries is wrong ", 0L, this.queue.getNumOfPersistentEntries());
                        assertEquals(ME + " number of entries is wrong ", 0L, this.queue.getNumOfEntries());
//                        queue.put(transients, false);
                        for (int i=0; i < transients.length; i++)
                           this.queue.put(transients[i], false);
                        assertEquals(ME + " number of entries after putting transients is wrong ", transients.length, queue.getNumOfEntries());
//                        queue.put(persistent, false);
                        for (int i=0; i < persistentEntries.length; i++) {
                           this.queue.put(persistentEntries[i], false);
                        }
                        assertEquals(ME + " number of entries after putting transients is wrong ", persistentEntries.length + transients.length, queue.getNumOfEntries());
                        long nPersistents  = queue.getNumOfPersistentEntries();
                        long nTransient = queue.getNumOfEntries() - nPersistents;

                        assertEquals(ME + " number of persistent entries is wrong ", persistentEntries.length, nPersistents);
                        assertEquals(ME + " number of transient entries is wrong ", transients.length, nTransient);

                        ArrayList total = new ArrayList();
                        ArrayList ret = queue.peekSamePriority(-1, -1L);
                        this.queue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        while (ret.size() > 0) {
                           total.addAll(ret);
                           ret = this.queue.peekSamePriority(-1, -1L);
                           if (ret.size() > 0)
                              this.queue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        }
                        int mustEntries = inputTable[0].size() + inputTable[1].size() + inputTable[2].size();


                        long totNumOfBytes = 100*(numOfPersistentEntries[id]+numOfTransientEntries[it]);
                        this.log.trace(ME, "total number of bytes: " + totNumOfBytes + " maxNumOfBytes: " + maxNumOfBytes[is]);
                        this.log.trace(ME, "entries must be: " + mustEntries);

                        assertTrue("Overflow is not allowed " + queue.toXml("") + "total number of bytes " + totNumOfBytes + " max number of bytes: " + maxNumOfBytes[is], totNumOfBytes <= maxNumOfBytes[is]);
//                        assertTrue(ME + " Overflow is not allowed " + queue.toXml("") , checkIfPossible(transientNumOfBytes, persistentNumOfBytes, maxTransientNumOfBytes, maxPersistentNumOfBytes));
                        assertEquals(ME + " number of returned values differe from input values " + queue.toXml(""), mustEntries, total.size());
                        log.info(ME, "SUCCESS: cacheSize=" + maxNumOfBytesCache[ic] + " maxBytes=" + maxNumOfBytes[is] + " .... looks OK");

                        int count = 0;
                        for (int j=0; j < 3; j++) {
                           Hashtable table = inputTable[j];
                           Enumeration keys = table.keys();
                           while (keys.hasMoreElements()) {
                              long refId = ((I_QueueEntry)table.get(keys.nextElement())).getUniqueId();
                              long outId = ((I_QueueEntry)total.get(count)).getUniqueId();
                              assertEquals("uniqueId differe for count " + count + " " + queue.toXml(""), mustEntries, total.size());
                              count++;
                           }
                        }
                     }
                     catch(XmlBlasterException e) {
                        log.dump(ME, "Exception (might be ok): " + e.toString());
                        assertTrue("Overflow is not allowed " + queue.toXml("") + "total number of bytes " + 100*(numOfPersistentEntries[id]+numOfTransientEntries[it]) + " max muber of bytes: " + maxNumOfBytes[is], 100*(numOfPersistentEntries[id]+numOfTransientEntries[it]) > maxNumOfBytes[is]);
//                        assertTrue(ME + " Overflow is not allowed" + queue.toXml(""), !checkIfPossible(transientNumOfBytes, persistentNumOfBytes, maxTransientNumOfBytes, maxPersistentNumOfBytes));
                        log.info(ME, "SUCCESS: Exception is OK: " + e.toString());
                     }
                  }
               }
            }
         }
   }


   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.CacheQueueTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);
      CacheQueueTest testSub = new CacheQueueTest(glob, "CacheQueueTest");

      long startTime = System.currentTimeMillis();

/*
      testSub.setUp();
      testSub.tearDown();

      testSub.setUp();
      testSub.testConfig();
      testSub.tearDown();
*/
      testSub.setUp();
      testSub.testPutPeekRemove();
      testSub.tearDown();

      long usedTime = System.currentTimeMillis() - startTime;
      testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
   }
}

                                                                       
