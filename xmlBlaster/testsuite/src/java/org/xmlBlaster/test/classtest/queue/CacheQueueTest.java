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

   private I_Queue[] queues = null;

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
      try {
         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST': " + ex.getMessage());
      }

      // cleaning up the database from previous runs ...

      QueuePropertyBase prop = null;
      try {
         // test initialize()

         prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");

         StorageId queueId = new StorageId("cb", "SetupQueue");

         CacheQueueInterceptorPlugin queue = new CacheQueueInterceptorPlugin();
         queue.initialize(queueId, prop);
         queue.destroy();
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
      this.queues = new I_Queue[1];
      QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
      prop.setMaxMsg(200L);
      prop.setMaxMsgCache(100L);
      prop.setMaxBytes(5000L);
      prop.setMaxBytesCache(2000L);

      StorageId queueId = new StorageId("cb", "CacheQueueTest/config");

      CacheQueueInterceptorPlugin queue = new CacheQueueInterceptorPlugin();
      this.queues[0] = queue;
      queue.initialize(queueId, prop);

      long persistentSize = queue.getPersistentQueue().getMaxNumOfBytes();
      long persistentMsg  = queue.getPersistentQueue().getMaxNumOfEntries();
      long transientSize  = queue.getTransientQueue().getMaxNumOfBytes();
      long transientMsg   = queue.getTransientQueue().getMaxNumOfEntries();

      assertEquals("Wrong persistent size", 5000L, persistentSize);
      assertEquals("Wrong persistent num of msg", 200L, persistentMsg);
      if (2000L != transientSize)
         log.error(ME, "ERROR: Wrong transient size"+queue.getTransientQueue().toXml(""));
      assertEquals("Wrong transient size"+queue.getTransientQueue().toXml(""), 2000L, transientSize);
      assertEquals("Wrong num of transient msg", 100L, transientMsg);

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
      this.queues = new I_Queue[1];

      // every content is 80 bytes which gives an entry size of 100 bytes (80+20)

      long maxNumOfBytesCache[] = {7000L, 100000L};
      long maxNumOfBytes[] = {7000L, 500000L};
      int numOfTransientEntries[] = { 20, 500, 2000};
      int numOfDurableEntries[] =  { 0, 20, 500, 2000};
//      int numPrio[] = { 1, 5, 9};

//      int it=0, id=0, ic=0, is=0;
//      try {
         for (int ic=0; ic < maxNumOfBytesCache.length; ic++) {
            for (int is=0; is < maxNumOfBytes.length; is++) {
               log.info(ME, "**** TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is]);
               // a new queue each time here ...
               QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_SESSION, "/node/test");
               prop.setMaxMsg(20000L);
               prop.setMaxMsgCache(10000L);
               prop.setMaxBytes(maxNumOfBytes[is]);
               prop.setMaxBytesCache(maxNumOfBytesCache[ic]);
               StorageId queueId = new StorageId("cb", "CacheQueueTest/jdbc" + maxNumOfBytes[is] + "/ram" + maxNumOfBytesCache[ic]);

               CacheQueueInterceptorPlugin queue = new CacheQueueInterceptorPlugin();
               this.queues[0] = queue;
               queue.initialize(queueId, prop);

               for (int it=0; it < numOfTransientEntries.length; it++) {
                  // entry.setPrio(4+(it%3));
                  for (int id=0; id < numOfDurableEntries.length; id++) {

                     log.info(ME, "**** SUB-TEST maxNumOfBytesCache["+ic+"]=" + maxNumOfBytesCache[ic] + " maxNumOfBytes["+is+"]=" + maxNumOfBytes[is] +
                                   " -> numOfTransientEntries["+it+"]=" + numOfTransientEntries[it] + " numOfDurableEntries["+id+"]=" + numOfDurableEntries[id]);
                     if (!queue.isShutdown()) queue.shutdown(true);
                     queue.initialize(queueId, prop);
                     queue.clear();

                     long maxPersistentNumOfBytes = maxNumOfBytes[is];
                     long maxTransientNumOfBytes = maxNumOfBytesCache[ic];
                     long transientNumOfBytes  = 0L;
                     long durableNumOfBytes  = 0L;

                     assertEquals(ME + " the number of bytes of the queue should be zero ", 0L, queue.getNumOfBytes());
                     assertEquals(ME + " the number of entries in the queue should be zero ", 0L, queue.getNumOfEntries());
                     assertEquals(ME + " the number of bytes of the durable entries in the queue should be zero ", 0L, queue.getNumOfDurableBytes());
                     assertEquals(ME + " the number of durable entries in the queue should be zero ", 0L, queue.getNumOfDurableEntries());

                     assertEquals(ME + " the maximum number of entries is wrong ", maxNumOfBytes[is], queue.getMaxNumOfBytes());

                     try {

                        queue.clear();
                        transientNumOfBytes = 100 * numOfTransientEntries[it];
                        durableNumOfBytes =100 * numOfDurableEntries[id];
                        // prepare the inputs .
                        Hashtable[] inputTable = new Hashtable[3];
                        for (int i=0; i < 3; i++) inputTable[i] = new Hashtable();

                        DummyEntry[] transients = new DummyEntry[numOfTransientEntries[it]];
                        DummyEntry[] durable    = new DummyEntry[numOfDurableEntries[id]];

                        this.log.info(ME, "putPeekRemove " + queueId + " durable: " + durable.length + " transient: " + transients.length);

                        boolean isDurable = false;
                        for (int i=0; i < transients.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enum = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enum, queue.getStorageId(), 80, isDurable);
                           transients[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }
                        isDurable = true;
                        for (int i=0; i < durable.length; i++) {
                           int prio = i % 3;
                           PriorityEnum enum = PriorityEnum.toPriorityEnum(prio+4);
                           DummyEntry entry = new DummyEntry(glob, enum, queue.getStorageId(), 80, isDurable);
                           durable[i] = entry;
                           inputTable[prio].put(new Long(entry.getUniqueId()), entry);
                        }

                        // do the test here ....
                        assertEquals(ME + " number of durable entries is wrong ", 0L, queue.getNumOfDurableEntries());
                        assertEquals(ME + " number of entries is wrong ", 0L, queue.getNumOfEntries());
//                        queue.put(transients, false);
                        for (int i=0; i < transients.length; i++)
                           queue.put(transients[i], false);
                        assertEquals(ME + " number of entries after putting transients is wrong ", transients.length, queue.getNumOfEntries());
//                        queue.put(durable, false);
                        for (int i=0; i < durable.length; i++) {
                           queue.put(durable[i], false);
                        }
                        assertEquals(ME + " number of entries after putting transients is wrong ", durable.length + transients.length, queue.getNumOfEntries());
                        long nDurables  = queue.getNumOfDurableEntries();
                        long nTransient = queue.getNumOfEntries() - nDurables;

                        assertEquals(ME + " number of durable entries is wrong ", durable.length, nDurables);
                        assertEquals(ME + " number of transient entries is wrong ", transients.length, nTransient);

                        ArrayList total = new ArrayList();
                        ArrayList ret = queue.peekSamePriority(-1, -1L);
                        queue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        while (ret.size() > 0) {
                           total.addAll(ret);
                           ret = queue.peekSamePriority(-1, -1L);
                           if (ret.size() > 0)
                              queue.removeRandom((I_QueueEntry[])ret.toArray(new I_QueueEntry[ret.size()]));
                        }
                        int mustEntries = inputTable[0].size() + inputTable[1].size() + inputTable[2].size();


                        long totNumOfBytes = 100*(numOfDurableEntries[id]+numOfTransientEntries[it]);
                        this.log.trace(ME, "total number of bytes: " + totNumOfBytes + " maxNumOfBytes: " + maxNumOfBytes[is]);
                        this.log.trace(ME, "entries must be: " + mustEntries);

                        assertTrue("Overflow is not allowed " + queue.toXml("") + "total number of bytes " + totNumOfBytes + " max number of bytes: " + maxNumOfBytes[is], totNumOfBytes <= maxNumOfBytes[is]);
//                        assertTrue(ME + " Overflow is not allowed " + queue.toXml("") , checkIfPossible(transientNumOfBytes, durableNumOfBytes, maxTransientNumOfBytes, maxPersistentNumOfBytes));
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
                        assertTrue("Overflow is not allowed " + queue.toXml("") + "total number of bytes " + 100*(numOfDurableEntries[id]+numOfTransientEntries[it]) + " max muber of bytes: " + maxNumOfBytes[is], 100*(numOfDurableEntries[id]+numOfTransientEntries[it]) > maxNumOfBytes[is]);
//                        assertTrue(ME + " Overflow is not allowed" + queue.toXml(""), !checkIfPossible(transientNumOfBytes, durableNumOfBytes, maxTransientNumOfBytes, maxPersistentNumOfBytes));
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

      testSub.setUp();
      testSub.tearDown();

      long startTime = System.currentTimeMillis();
/*
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

