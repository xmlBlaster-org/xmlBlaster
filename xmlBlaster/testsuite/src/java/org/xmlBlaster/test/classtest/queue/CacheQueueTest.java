package org.xmlBlaster.test.classtest.queue;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;

import org.xmlBlaster.util.queuemsg.DummyEntry;

import java.util.ArrayList;
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
   private static Logger log = Logger.getLogger(CacheQueueTest.class.getName());
   private StopWatch stopWatch = new StopWatch();
   private CacheQueueInterceptorPlugin queue = null;
   private I_Queue[] queues;
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
         prop.put("entriesTableName", "_entries");
         this.glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());

         pluginInfo = new PluginInfo(glob, pluginManager, "CACHE", "1.0");
         this.queue = (CacheQueueInterceptorPlugin)pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         this.queues = new I_Queue[3];

         pluginInfo = new PluginInfo(glob, pluginManager, "RAM", "1.0");
         this.queues[0] = (I_Queue)pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         this.queues[1] = (I_Queue)pluginManager.getPlugin(pluginInfo, queueId, cbProp);
         this.queues[2] = queue;

         for (int i=0; i < 3; i++) this.queues[i].shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         log.severe("could not propertly set up the database: " + ex.getMessage());
      }

   }


   public void tearDown() {
      try {
         for (int i=0; i < 3; i++) {
            this.queues[i].clear();
            this.queues[i].shutdown();
         }
      }
      catch (Exception ex) {
         log.warning("error when tearing down " + ex.getMessage() + " this normally happens when invoquing multiple times cleanUp");
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
      prop.setMaxEntries(20L);
      prop.setMaxEntriesCache(10L);
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
         log.severe("ERROR: Wrong transient size" + this.queue.getTransientQueue().toXml(""));
      assertEquals("Wrong transient size" + this.queue.getTransientQueue().toXml(""), 200L, transientSize);
      assertEquals("Wrong num of transient msg", 10L, transientMsg);

   }


   /**
    * returns true if the combination is possible, false otherwise
    */
   private boolean checkIfPossible(long transientNumOfBytes, long persistentNumOfBytes,
      long maxTransientNumOfBytes, long maxPersistentNumOfBytes) {
      log.fine("checkIfPossible: transient number of bytes: " + transientNumOfBytes + " of (max) " + maxTransientNumOfBytes + " , persistent number of bytes: " + persistentNumOfBytes + " of (max) " + maxPersistentNumOfBytes);
      if (transientNumOfBytes > maxTransientNumOfBytes) return false;
      if (persistentNumOfBytes > maxPersistentNumOfBytes) return false;
      return true;
   }


   public void testPutPeekRemove() {
      String queueType = this.glob.getProperty().get("queueType", "CACHE");
      log.info("testPutPeekRemove will be done with a queue of type '" + queueType + "'");
      log.info("if you want to test with another queue type invoke '-queueType $TYPE' on the cmd line where $TYPE is either RAM JDBC or CACHE");
      int index = 2;
      if ("RAM".equalsIgnoreCase(queueType)) index = 0;
      else if ("JDBC".equalsIgnoreCase(queueType)) index = 1;

      try {
         putPeekRemove(this.queues[index]);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   public void putPeekRemove(I_Queue refQueue) throws XmlBlasterException {

      // set up the queues ....

      // every content is 80 bytes which gives an entry size of 100 bytes (80+20)
      long entrySize = 100;

      String lastSuccessfulLocation = "";
      long maxNumOfBytesCache[] = {700L, 10000L};
      long maxNumOfBytes[] = {700L, 50000L};
      int numOfTransientEntries[] = { 2, 50, 200};
      int numOfPersistentEntries[] =  { 0, 2, 50, 200};
//      int numPrio[] = { 1, 5, 9};

//      int it=0, id=0, ic=0, is=0;
//      try {
         for (int ic=0; ic < maxNumOfBytesCache.length; ic++) {
            for (int is=0; is < maxNumOfBytes.length; is++) {
               log.info("**** TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is]);
               // a new queue each time here ...
               QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
               prop.setMaxEntries(2000L);
               prop.setMaxEntriesCache(1000L);
               prop.setMaxBytes(maxNumOfBytes[is]);
               prop.setMaxBytesCache(maxNumOfBytesCache[ic]);
               StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "CacheQueueTest/jdbc" + maxNumOfBytes[is] + "/ram" + maxNumOfBytesCache[ic]);

//               this.queue = new CacheQueueInterceptorPlugin();
               refQueue.clear();
               refQueue.shutdown();

               refQueue.initialize(queueId, prop);

               for (int it=0; it < numOfTransientEntries.length; it++) {
                  // entry.setPrio(4+(it%3));
                  for (int id=0; id < numOfPersistentEntries.length; id++) {

                     log.info("**** SUB-TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is] +
                                   " -> numOfTransientEntries["+it+"]=" + numOfTransientEntries[it] + " numOfPersistentEntries["+id+"]=" + numOfPersistentEntries[id]);
                     if (!refQueue.isShutdown()) refQueue.shutdown();
                     refQueue.initialize(queueId, prop);
                     refQueue.clear();

                     long maxPersistentNumOfBytes = maxNumOfBytes[is];
                     long maxTransientNumOfBytes = maxNumOfBytesCache[ic];
                     long transientNumOfBytes  = 0L;
                     long persistentNumOfBytes  = 0L;

                     assertEquals(ME + " the number of bytes of the queue should be zero ", 0L, refQueue.getNumOfBytes());
                     assertEquals(ME + " the number of entries in the queue should be zero ", 0L, refQueue.getNumOfEntries());
                     assertEquals(ME + " the number of bytes of the persistent entries in the queue should be zero ", 0L, refQueue.getNumOfPersistentBytes());
                     assertEquals(ME + " the number of persistent entries in the queue should be zero ", 0L, refQueue.getNumOfPersistentEntries());

                     assertEquals(ME + " the maximum number of entries is wrong ", maxNumOfBytes[is], refQueue.getMaxNumOfBytes());

                     try {

                        refQueue.clear();
                        transientNumOfBytes = entrySize * numOfTransientEntries[it];
                        persistentNumOfBytes =entrySize * numOfPersistentEntries[id];
                        // prepare the inputs .
                        Hashtable[] inputTable = new Hashtable[3];
                        for (int i=0; i < 3; i++) inputTable[i] = new Hashtable();

                        DummyEntry[] transients = new DummyEntry[numOfTransientEntries[it]];
                        DummyEntry[] persistentEntries    = new DummyEntry[numOfPersistentEntries[id]];

                        log.info("putPeekRemove " + queueId + " persistent: " + persistentEntries.length + " transient: " + transients.length);

                        boolean persistent = false;
                        for (int i=0; i < transients.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enumer = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enumer, refQueue.getStorageId(), entrySize, persistent);
                           transients[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }
                        persistent = true;
                        for (int i=0; i < persistentEntries.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enumer = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enumer, refQueue.getStorageId(), entrySize, persistent);
                           persistentEntries[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }

                        // do the test here ....
                        assertEquals(ME + " number of persistent entries is wrong ", 0L, refQueue.getNumOfPersistentEntries());
                        assertEquals(ME + " number of entries is wrong ", 0L, refQueue.getNumOfEntries());
                        for (int i=0; i < transients.length; i++) {
                           lastSuccessfulLocation = "transientEntries put #" + i;
                           refQueue.put(transients[i], false);
                        }
                        assertEquals(ME + " number of entries after putting transients is wrong ", transients.length, refQueue.getNumOfEntries());
                        for (int i=0; i < persistentEntries.length; i++) {
                           lastSuccessfulLocation = "persistentEntries put #" + i;
                           refQueue.put(persistentEntries[i], false);
                        }
                        assertEquals(ME + " number of entries after putting transients is wrong ", persistentEntries.length + transients.length, refQueue.getNumOfEntries());
                        long nPersistents  = refQueue.getNumOfPersistentEntries();
                        long nTransient = refQueue.getNumOfEntries() - nPersistents;

                        assertEquals(ME + " number of persistent entries is wrong ", persistentEntries.length, nPersistents);
                        assertEquals(ME + " number of transient entries is wrong ", transients.length, nTransient);

                        ArrayList total = new ArrayList();
                        ArrayList ret = refQueue.peekSamePriority(-1, -1L);
                        refQueue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        while (ret.size() > 0) {
                           total.addAll(ret);
                           ret = refQueue.peekSamePriority(-1, -1L);
                           if (ret.size() > 0)
                              refQueue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        }
                        int mustEntries = inputTable[0].size() + inputTable[1].size() + inputTable[2].size();


                        long totNumOfBytes = entrySize * (numOfPersistentEntries[id]+numOfTransientEntries[it]);
                        log.fine("total number of bytes: " + totNumOfBytes + " maxNumOfBytes: " + maxNumOfBytes[is]);
                        log.fine("entries must be: " + mustEntries);

                        assertTrue("Overflow is not allowed " + refQueue.toXml("") + "total number of bytes " + totNumOfBytes + " max number of bytes: " + maxNumOfBytes[is], totNumOfBytes <= maxNumOfBytes[is]);
//                        assertTrue(ME + " Overflow is not allowed " + refQueue.toXml("") , checkIfPossible(transientNumOfBytes, persistentNumOfBytes, maxTransientNumOfBytes, maxPersistentNumOfBytes));
                        assertEquals(ME + " number of returned values differe from input values " + refQueue.toXml(""), mustEntries, total.size());
                        log.info("SUCCESS: cacheSize=" + maxNumOfBytesCache[ic] + " maxBytes=" + maxNumOfBytes[is] + " .... looks OK");

                        int count = 0;
                        for (int j=0; j < 3; j++) {
                           Hashtable table = inputTable[j];
                           Enumeration keys = table.keys();
                           while (keys.hasMoreElements()) {
                              long refId = ((I_QueueEntry)table.get(keys.nextElement())).getUniqueId();
                              long outId = ((I_QueueEntry)total.get(count)).getUniqueId();
                              assertEquals("uniqueId differe for count " + count + " " + refQueue.toXml(""), mustEntries, total.size());
                              count++;
                           }
                        }
                     }
                     catch(XmlBlasterException e) {
                        log.finest("Exception (might be ok): " + e.toString());
                        assertTrue("Overflow is not allowed on location '"+ lastSuccessfulLocation + "' " + refQueue.toXml("") + "total number of bytes " + entrySize*(numOfPersistentEntries[id]+numOfTransientEntries[it]) + " max muber of bytes: " + maxNumOfBytes[is], entrySize*(numOfPersistentEntries[id]+numOfTransientEntries[it]) > maxNumOfBytes[is]);
                        log.info("SUCCESS: Exception is OK: " + e.toString());
                     }
                  }
               }
            }
         }
   }


   public void testAvailability() {
      String queueType = "CACHE";
      try {
         availability();
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing availability probably due to failed initialization of the queue of type " + queueType);
         ex.printStackTrace();
      }
   }


   /**
    * when queue available:
    * -fill queue with 3 persistent and 2 transient messages -> RAM:5 JDBC:3
    * - queue is made unavailable
    * - queue is filled with 2 persistent and 3 transient msg -> RAM:10 JDBC:3 (since no comm)
    * - peek and then remove all available entries: -> RAM:0 JDBC:3 (since no comm)
    */
   public void availability() throws XmlBlasterException {
      // set up the queues ....
      long maxNumOfBytesCache = 10000L;
      long maxNumOfBytes = 50000L;
      int numOfTransientEntries = 200;
      int numOfPersistentEntries =  200;
      long entrySize = 100L;

      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
      prop.setMaxEntries(2000L);
      prop.setMaxEntriesCache(1000L);
      prop.setMaxBytes(maxNumOfBytes);
      prop.setMaxBytesCache(maxNumOfBytesCache);
      StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "CacheQueueTest/jdbc" + maxNumOfBytes + "/ram" + maxNumOfBytesCache);
      this.queue.clear();
      this.queue.shutdown();
      this.queue.initialize(queueId, prop);

      if (!this.queue.isShutdown()) this.queue.shutdown();
      this.queue.initialize(queueId, prop);
      this.queue.clear();

      long maxPersistentNumOfBytes = maxNumOfBytes;
      long maxTransientNumOfBytes = maxNumOfBytesCache;
      long transientNumOfBytes  = 0L;
      long persistentNumOfBytes  = 0L;

      int numOfEntries = 20;
      int entries1 = 5;
      int entries2 = 10;

      this.queue.clear();
      transientNumOfBytes = entrySize * numOfTransientEntries;
      persistentNumOfBytes = entrySize * numOfPersistentEntries;

      DummyEntry[] entries = new DummyEntry[numOfEntries];
      PriorityEnum prio = PriorityEnum.toPriorityEnum(4);

      boolean persistent = false;
      for (int i=0; i < numOfEntries; i++) {
         persistent = (i % 2) == 0; // even are persistent uneven are transient
         entries[i] = new DummyEntry(glob, prio, this.queue.getStorageId(), entrySize, persistent);
      }

      // do the test here ....
      for (int i=0; i < entries1; i++) {
         this.queue.put(entries[i], false);
//         assertEquals(ME + " number of entries after putting transients is wrong ", transients.length, queue.getNumOfEntries());
      }

      CacheQueueInterceptorPlugin cacheQueue = (CacheQueueInterceptorPlugin)this.queue;
      cacheQueue.storageUnavailable(I_StorageProblemListener.AVAILABLE);

      for (int i=entries1; i < entries2; i++) {
         this.queue.put(entries[i], false);
      }

      ArrayList list = this.queue.peek(-1, -1L);
      assertEquals(ME + " number of entries when retrieving is wrong ", entries2, list.size());
      for (int i=0; i < list.size(); i++) {
         long uniqueId = ((I_QueueEntry)list.get(i)).getUniqueId();
         assertEquals(ME + " entry sequence is wrong ", entries[i].getUniqueId(), uniqueId);
      }
      long ret = 0L;
      boolean[] tmpArr = this.queue.removeRandom( (I_QueueEntry[])list.toArray(new I_QueueEntry[list.size()]) );
      for (int i=0; i < tmpArr.length; i++) if (tmpArr[i]) ret++;
      assertEquals(ME + " number of entries removed is wrong ", (long)entries2, ret);

      list = this.queue.peek(-1, -1L);
      assertEquals(ME + " number of entries peeked after removal is wrong ", 0, list.size());

      long num = this.queue.getNumOfEntries();
      assertEquals(ME + " number of entries after removal is wrong ", 0L, num);

      cacheQueue.storageAvailable(I_StorageProblemListener.UNAVAILABLE);
      list = this.queue.peek(-1, -1L);
      assertEquals(ME + " number of entries peeked after reconnecting is wrong ", 0, list.size());

      num = this.queue.getNumOfEntries();
      assertEquals(ME + " number of entries after reconnecting is wrong ", 0L, num);

/*
      for (int i=entries2; i < numOfEntries; i++) {
         this.queue.put(entries[i], false);
      }
*/

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

      testSub.setUp();
      testSub.testAvailability();
      testSub.tearDown();

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
      testSub.log.info("time used for tests: " + usedTime/1000 + " seconds");
   }
}

                                                                       
