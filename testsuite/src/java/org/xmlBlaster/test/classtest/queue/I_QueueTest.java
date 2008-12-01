package org.xmlBlaster.test.classtest.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.util.queue.BlockingQueueWrapper;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StorageSizeListener;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queuemsg.DummyEntry;

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
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.queue.I_QueueTest
 * </p>
 * @see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(I_QueueEntry)
 * @see org.xmlBlaster.util.queue.I_Queue
 * @see org.xmlBlaster.util.queue.ram.RamQueuePlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class I_QueueTest extends TestCase {


   class QueueSizeListener  implements I_StorageSizeListener {

      private long lastNumEntries = 0L, 
                   lastNumBytes = 0L,
                   lastIncrementEntries = 0L,
                   lastIncrementBytes = 0L;
      private int count = 0;
      
      public long getLastIncrementEntries() {
         return this.lastIncrementEntries;
      }
      
      public long getLastIncrementBytes() {
         return this.lastIncrementBytes;
      }
      
      public int getCount() {
         return this.count;
      }
      
      public void clear() {
         this.lastNumEntries = 0L; 
         this.lastNumBytes = 0L;
         this.lastIncrementEntries = 0L;
         this.lastIncrementBytes = 0L;
         this.count = 0;
      }
      
      public void changed(I_Storage storage, long numEntries, long numBytes, boolean isShutdown) {
         this.lastIncrementEntries = numEntries - this.lastNumEntries;
         this.lastIncrementBytes = numBytes - this.lastNumBytes;
         this.lastNumEntries = numEntries;
         this.lastNumBytes = numBytes;
         this.count++;
      }

   }
   

   private String ME = "I_QueueTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(I_QueueTest.class.getName());

   private I_Queue queue;
   private QueueSizeListener queueSizeListener = new QueueSizeListener();
   
   static String[] PLUGIN_TYPES = {
                   new String("RAM"),
                   new String("JDBC"),
                   new String("CACHE")
                 };

/*
   static I_Queue[] IMPL = {
                   new org.xmlBlaster.util.queue.ram.RamQueuePlugin(),
                   new org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin(),
                   new org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin()
                 };
*/

   public class QueuePutter extends Thread {
      
      private I_Queue queue;
      private long delay;
      private int numOfEntries;
      private boolean ignoreInterceptor;
      
      public QueuePutter(I_Queue queue, long delay, int numOfEntries, boolean ignoreInterceptor) {
         this.queue = queue;
         this.delay = delay;
         this.numOfEntries = numOfEntries;
         this.ignoreInterceptor = ignoreInterceptor;
      }
      
      public void run() {
         try {
            for (int i=0; i < this.numOfEntries; i++) {
               Thread.sleep(this.delay);
               DummyEntry queueEntry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               this.queue.put(queueEntry, this.ignoreInterceptor);
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }
      
   }
   
   
   public I_QueueTest(String name, int currImpl, Global glob) {
      super(name);
//      this.queue = IMPL[currImpl];
      //this.ME = "I_QueueTest[" + this.queue.getClass().getName() + "]";

      if (glob == null) this.glob = Global.instance();
      else this.glob = glob;


      try {
         String type = PLUGIN_TYPES[currImpl];
         this.glob.getProperty().set("cb.queue.persistent.tableNamePrefix", "TEST");
         QueuePluginManager pluginManager = new QueuePluginManager(glob);

         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         java.util.Properties prop = (java.util.Properties)pluginInfo.getParameters();
         prop.put("tableNamePrefix", "TEST");
         prop.put("entriesTableName", "_entries");
         this.glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());

         pluginInfo = new PluginInfo(glob, pluginManager, type, "1.0");
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "SomeQueueId");
         this.queue = pluginManager.getPlugin(pluginInfo, queueId, new CbQueueProperty(this.glob, Constants.RELATING_CALLBACK, this.glob.getStrippedId()));
         this.queue.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         log.severe("setUp: error when setting the property 'cb.queue.persistent.tableNamePrefix' to 'TEST'");
      }
   }

   protected void setUp() {
      // cleaning up the database from previous runs ...
      QueuePropertyBase prop = null;
      try {
         prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "SomeQueueId");
         queue.initialize(queueId, prop);
         queue.clear();
         queue.shutdown();
      }
      catch (Exception ex) {
         log.severe("could not propertly set up the database: " + ex.getMessage());
      }
   }

   /**
    * Tests QueuePropertyBase() and getStorageId()
    * @param queueTypeList A space separated list of names for the
    *        implementations to be tested. Valid names are:
    *        RamQueuePlugin JdbcQueuePlugin
    */
   public void testConfig() {
      config(this.queue);
   }

   /**
    * Tests initialize(), getProperties(), setProperties() and capacity()
    * @param queue !!!Is not initialized in this case!!!!
    */
   private void config(I_Queue queue) {
      ME = "I_QueueTest.config(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);

      QueuePropertyBase prop1 = null;
      QueuePropertyBase prop = null;
      try {
         // test initialize()
         prop1 = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         int max = 12;
         prop1.setMaxEntries(max);
         prop1.setMaxEntriesCache(max);
         assertEquals(ME+": Wrong capacity", max, prop1.getMaxEntries());
         assertEquals(ME+": Wrong cache capacity", max, prop1.getMaxEntriesCache());
         //PluginInfo pluginInfo = new PluginInfo(glob, null, "");
         //queue.init(glob, pluginInfo);     // Init from pluginloader is first
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "SomeQueueId");
         queue.initialize(queueId, prop1);
         queue.clear(); // this is needed since the tearDown has cleaned the queue with previous cfg (other StorageId)
         assertEquals(ME+": Wrong queue ID", queueId, queue.getStorageId());

         try {
            prop = new CbQueueProperty(glob, Constants.RELATING_SUBJECT, "/node/test");
            prop.setMaxEntries(99);
            prop.setMaxEntriesCache(99);
            queue.setProperties(prop);
         }
         catch(XmlBlasterException e) {
            fail("Changing properties failed");
         }

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }

      long len = prop.getMaxEntries();
      assertEquals(ME+": Wrong capacity", prop.getMaxEntries(), queue.getMaxNumOfEntries());
      assertEquals(ME+": Wrong capacity", prop.getMaxEntries(), ((QueuePropertyBase)queue.getProperties()).getMaxEntries());
      assertEquals(ME+": Wrong size", 0, queue.getNumOfEntries());

      try {
         for (int ii=0; ii<len; ii++) {
            queue.put(new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true), false);
         }
         assertEquals(ME+": Wrong total size", len, queue.getNumOfEntries());

         try {
            DummyEntry queueEntry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            queue.put(queueEntry, false);
            queue.put(queueEntry, false);
            fail("Did expect an exception on overflow");
         }
         catch(XmlBlasterException e) {
            log.info("SUCCESS the exception is OK: " + e.getMessage());
         }

         log.info("toXml() test:" + queue.toXml(""));
         log.info("usage() test:" + queue.usage());

         assertEquals(ME+": should not be shutdown", false, queue.isShutdown());
         queue.shutdown();
         assertEquals(ME+": should be shutdown", true, queue.isShutdown());

         log.info("#2 Success, filled " + queue.getNumOfEntries() + " messages into queue");
         System.out.println("***" + ME + " [SUCCESS]");
         queue.shutdown();
         queue = null;
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

//------------------------------------
   public void testSize1() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         int max = 1;
         prop.setMaxEntries(max);
         prop.setMaxEntriesCache(max);
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/size1");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0L, queue.getNumOfEntries());
         assertEquals(ME, 1L, queue.getMaxNumOfEntries());
         size1(this.queue);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing Size1 probably due to failed initialization of the queue of type " + queueType);
      }
   }

   /**
    * Tests put(MsgQueueEntry[]) and put(MsgQueueEntry) and clear()
    */
   private void size1(I_Queue queue) {
      this.queue = queue;
      ME = "I_QueueTest.size1(" + queue.getStorageId() + ")[" + this.queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      int maxEntries = (int)queue.getMaxNumOfEntries();
      try {
         //========== Test 1: put(I_QueueEntry[])
         int numLoop = 10;
         List<I_Entry> list = new ArrayList<I_Entry>();

         //========== Test 2: put(I_QueueEntry)
         this.queue.removeStorageSizeListener(null);
         this.queue.addStorageSizeListener(this.queueSizeListener);
         this.queueSizeListener.clear();

         for (int ii=0; ii<numLoop; ii++) {
            DummyEntry queueEntry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            try {
               queue.put(queueEntry, false);
               assertEquals("number of entries incremented on last invocation", 1, this.queueSizeListener.getLastIncrementEntries());
               assertEquals("number of bytes incremented on last invocation", queueEntry.getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());
               
               if (ii > maxEntries) { // queue allows on overload
                  fail("Didn't expect more than " + maxEntries + " entries" + queue.toXml(""));
               }
               else
                  list.add(queueEntry);
            }
            catch (XmlBlasterException e) {
               if (ii <= maxEntries) {
                  fail("Didn't expect exception" + e.getMessage());
               }
            }
         }
         assertEquals("number of invocations for queue size listener is wrong", maxEntries+1, this.queueSizeListener.getCount());

         // The queues allow temporary oversize (one extra put())
         assertEquals(ME+": Wrong total size " + queue.toXml(""), maxEntries+1, queue.getNumOfEntries());
         this.checkSizeAndEntries(" put(I_QueueEntry) ", list, queue);
         log.info("#2 Success, filled " + queue.getNumOfEntries() + " messages into queue");

         List<I_Entry> entryList = null;
         try {
            entryList = queue.peekLowest(1, -1L, null, false);
            assertEquals("PEEK #1 failed"+queue.toXml(""), 1, entryList.size());
            log.info("curr entries="+queue.getNumOfEntries());
         }
         catch (XmlBlasterException e) {
            if (e.getErrorCode()!=ErrorCode.INTERNAL_NOTIMPLEMENTED) throw e;
         }

         
         //this.queue.removeStorageSizeListener(null);
         //this.queue.addStorageSizeListener(this.queueSizeListener);
         //this.queueSizeListener.clear();

         entryList = queue.takeLowest(1, -1L, null, false);
         long singleSize = ((I_QueueEntry)entryList.get(0)).getSizeInBytes(); 
         assertEquals("TAKE #1 failed"+queue.toXml(""), 1, entryList.size());
         log.info("curr entries="+queue.getNumOfEntries());
         assertEquals("number of entries incremented on last invocation", -1, this.queueSizeListener.getLastIncrementEntries());
         assertEquals("number of bytes incremented on last invocation", -singleSize, this.queueSizeListener.getLastIncrementBytes());

         entryList = queue.takeLowest(1, -1L, null, false);
         assertEquals("TAKE #2 failed"+queue.toXml(""), 1, entryList.size());
         assertEquals("number of entries incremented on last invocation", -1, this.queueSizeListener.getLastIncrementEntries());
         assertEquals("number of bytes incremented on last invocation", -singleSize, this.queueSizeListener.getLastIncrementBytes());

         queue.clear();
         assertEquals(ME+": Wrong empty size", 0L, queue.getNumOfEntries());

         System.out.println("***" + ME + " [SUCCESS]");
         queue.shutdown();
         queue = null;

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
      log.info("SUCCESS");
   }


//------------------------------------
   public void testPutMsg() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/putMsg");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0L, queue.getNumOfEntries());
         putMsg(this.queue);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType);
      }
   }


   /**
    * @see checkSizeAndEntries(String, I_QueueEntry[], I_Queue)
    */
   private void checkSizeAndEntries(String txt, List<I_Entry> queueEntries, I_Queue queue) {
      checkSizeAndEntries(txt, (I_QueueEntry[])queueEntries.toArray(new I_QueueEntry[queueEntries.size()]), queue);
   }


   /**
    * Helper method to do a generic size check (size and number of entries)
    */
   private void checkSizeAndEntries(String txt, I_QueueEntry[] queueEntries, I_Queue queue) {
      long sizeOfTransients = 0L;
      long numOfPersistents = 0;
      long numOfTransients = 0;
      long sizeOfPersistents = 0L;

      for (int i=0; i < queueEntries.length; i++) {
         I_QueueEntry entry = queueEntries[i];
         if (entry.isPersistent()) {
            sizeOfPersistents += entry.getSizeInBytes();
            numOfPersistents++;
         }
         else {
            sizeOfTransients += entry.getSizeInBytes();
            numOfTransients++;
         }
      }

      long queueNumOfPersistents = queue.getNumOfPersistentEntries();
      long queueNumOfTransients = queue.getNumOfEntries() - queueNumOfPersistents;
      long queueSizeOfPersistents = queue.getNumOfPersistentBytes();
      long queueSizeOfTransients = queue.getNumOfBytes() - queueSizeOfPersistents;

      txt += " NumPersistents=" + queueNumOfPersistents + " NumOfTransients=" + queueNumOfTransients; 
      txt += " SizeOfPersistents=" + queueSizeOfPersistents + " SizeOfTransients=" + queueSizeOfTransients;

      assertEquals(ME + ": " + txt + " wrong number of persistents   ", numOfPersistents, queueNumOfPersistents);
      assertEquals(ME + ": " + txt + " wrong number of transients ", numOfTransients, queueNumOfTransients);
      assertEquals(ME + ": " + txt + " wrong size of persistents     ", sizeOfPersistents, queueSizeOfPersistents);
      assertEquals(ME + ": " + txt + " wrong size of transients   ", sizeOfTransients, queueSizeOfTransients);
   }



   /**
    * Tests put(MsgQueueEntry[]) and put(MsgQueueEntry) and clear()
    */
   private void putMsg(I_Queue queue) {
      ME = "I_QueueTest.putMsg(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: put(I_QueueEntry[])
         int numLoop = 10;
         List<I_Entry> list = new ArrayList<I_Entry>();

         this.queue.removeStorageSizeListener(null);
         this.queue.addStorageSizeListener(this.queueSizeListener);
         this.queueSizeListener.clear();

         for (int ii=0; ii<numLoop; ii++) {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true)};

            queue.put(queueEntries, false);

            assertEquals("number of entries incremented on last invocation", 3, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", 3*queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());
            for (int i=0; i < 3; i++) list.add(queueEntries[i]);

            this.checkSizeAndEntries(" put(I_QueueEntry[]) ", list, queue);
            assertEquals(ME+": Wrong size", (ii+1)*queueEntries.length, queue.getNumOfEntries());
         }
         assertEquals("number of invocations for queue size listener is wrong", numLoop, this.queueSizeListener.getCount());

         int total = numLoop*3;
         assertEquals(ME+": Wrong total size", total, queue.getNumOfEntries());
         log.info("#1 Success, filled " + queue.getNumOfEntries() + " messages into queue");


         //========== Test 2: put(I_QueueEntry)
         for (int ii=0; ii<numLoop; ii++) {
            DummyEntry queueEntry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            list.add(queueEntry);
            queue.put(queueEntry, false);
         }
         assertEquals(ME+": Wrong total size", numLoop+total, queue.getNumOfEntries());
         this.checkSizeAndEntries(" put(I_QueueEntry) ", list, queue);
         log.info("#2 Success, filled " + queue.getNumOfEntries() + " messages into queue");

         queue.clear();
         assertEquals(ME+": Wrong empty size", 0L, queue.getNumOfEntries());

         System.out.println("***" + ME + " [SUCCESS]");
         queue.shutdown();
         queue = null;

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


// ------------------------------------
   public void testPeekMsg() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/peekMsg");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         peekMsg(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing peekMsg probably due to failed initialization of the queue " + queueType);
      }

   }


// ------------------------------------
   public void testPeekMsgBlocking() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/peekMsg");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         
         // fill the queue:
         DummyEntry[] queueEntries = {
               new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
               new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
               new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true)
                              };
         queue.put(queueEntries, false);
         BlockingQueueWrapper wrapper = new BlockingQueueWrapper(200L);
         wrapper.init(queue);
         int numOfEntries = 2;
         List<I_Entry> ret = wrapper.blockingPeek(numOfEntries, 1000L);
         assertEquals("Wrong number of entries found", 2, ret.size());
         queue.removeNum(2);
         numOfEntries = 2;
         ret = wrapper.blockingPeek(numOfEntries, 1000L);
         assertEquals("Wrong number of entries found", 1, ret.size());
         queue.clear();
         ret = wrapper.blockingPeek(numOfEntries, 1000L);
         assertEquals("Wrong number of entries found", 0, ret.size());
         
         // and now making asynchronous putting with events
         numOfEntries = 3;
         long delay = 500L;
         boolean inhibitEvents = false;
         QueuePutter putter = new QueuePutter(this.queue, delay, numOfEntries, inhibitEvents);
         putter.start();
         long t0 = System.currentTimeMillis();
         ret = wrapper.blockingPeek(numOfEntries, 10000L);
         assertEquals("Wrong number of entries when blocking with events", numOfEntries, ret.size());
         long delta = System.currentTimeMillis() - t0;
         log.info("The blocking request with events took '" + delta + "' milliseconds");
         assertTrue("The method was blocking too long (did probably not wake up correctly", delta < 7000L);
         queue.clear();
         // and now making asynchronous putting without events (polling should detect it)
         numOfEntries = 3;
         delay = 500L;
         inhibitEvents = true;
         putter = new QueuePutter(this.queue, delay, numOfEntries, inhibitEvents);
         putter.start();
         t0 = System.currentTimeMillis();
         ret = wrapper.blockingPeek(numOfEntries, 10000L);
         assertEquals("Wrong number of entries when blocking with events", numOfEntries, ret.size());
         delta = System.currentTimeMillis() - t0;
         log.info("The blocking request without events took '" + delta + "' milliseconds");
         assertTrue("The method was blocking too long (did probably not wake up correctly", delta < 7000L);
         queue.clear();
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing peekMsg probably due to failed initialization of the queue " + queueType);
      }

   }


   /**
    * Tests peek() and peek(int num) and remove()
    * For a discussion of the sorting order see Javadoc of this class
    */
   private void peekMsg(I_Queue queue) {
      ME = "I_QueueTest.peekMsg(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: peek()
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);
            for (int ii=0; ii<10; ii++) {
               I_QueueEntry result = queue.peek();
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[0].getUniqueId(), result.getUniqueId());
            }
            queue.remove(); // Remove one
            for (int ii=0; ii<10; ii++) {
               I_QueueEntry result = queue.peek();
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), result.getUniqueId());
            }
            queue.remove(); // Remove one
            for (int ii=0; ii<10; ii++) {
               I_QueueEntry result = queue.peek();
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), result.getUniqueId());
            }
            queue.remove(); // Remove one
            for (int ii=0; ii<10; ii++) {
               I_QueueEntry result = queue.peek();
               assertTrue("Unexpected entry", result == null);
            }
            log.info("#1 Success, peek()");
         }


         //========== Test 2: peek(num)
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);

            for (int ii=-1; ii<100; ii++) {
               List<I_Entry> results = queue.peek(ii, -1L); // does no remove
               assertTrue("Missing entry", results != null);
               int expected = ii;
               if (ii == -1 || ii >= queueEntries.length)
                  expected = queueEntries.length;
               assertEquals(ME+": Wrong number of entries returned ii=" + ii, expected, results.size());
            }

            queue.clear();
            log.info("#2 Success, peek(int)");
         }

         //========== Test 3: peekSamePriority(-1)
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);

            int[] prios = { 9, 7, 5 };
            for (int j=0; j<prios.length; j++) {
               for (int ii=0; ii<10; ii++) {
                  List<I_Entry> results = queue.peekSamePriority(-1, -1L); // does no remove
                  assertTrue("Expected results", results != null);
                  assertEquals(ME+": Wrong number of 9 priorities", 4, results.size());
                  for (int k=0; k<results.size(); ++k)
                     assertEquals(ME+": Wrong priority returned", prios[j], ((I_QueueEntry)results.get(k)).getPriority());
               }
               for (int ii=0; ii<4; ii++) {
                  int num = queue.remove();
                  assertEquals(ME+": Expected remove", 1, num);
               }
            }

            assertEquals(ME+": Expected empty queue", 0, queue.getNumOfEntries());

            log.info("#3 Success, peekSamePriority()");
         }

         //========== Test 4: peekWithPriority(-1,7,9)
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MIN_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);

            for (int ii=0; ii<10; ii++) {
               List<I_Entry> results = queue.peekWithPriority(-1, -1L, 7, 9); // does no remove
               assertTrue("Expected results", results != null);
               assertEquals(ME+": Wrong number of 9 priorities", 8, results.size());
               for (int k=0; k<results.size(); ++k) {
                  assertEquals(ME+": Wrong priority returned", (k<4)?9L:7L, ((I_QueueEntry)results.get(k)).getPriority());
               }
            }
            queue.clear();
            assertEquals(ME+": Expected empty queue", 0, queue.getNumOfEntries());

            log.info("#4 Success, peekWithPriority()");
         }


         //========== Test 5: peek(100, 60)
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), 80, true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), 80, true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), 80, true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), 80, true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), 80, true),
                                        };
            queue.put(queueEntries, false);

            try {
               List<I_Entry> results = queue.peek(100, 60); // does no remove
               assertNotNull(ME+": the result should not be null");
               assertEquals(ME+": Expected one entry on peek(100,60)", 1, results.size());
            }
            catch (XmlBlasterException e) {
               e.printStackTrace();
               assertTrue("An exception should not occur here " + e.getMessage(), false);
            }
            
            queue.clear();
            assertEquals(ME+": Expected empty queue", 0, queue.getNumOfEntries());

            log.info("#5 Success, peek(100, 60)");
         }

         System.out.println("***" + ME + " [SUCCESS]");
         queue.shutdown();
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


//-----------------------------------------
   public void testRemoveWithPriority() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/removeWithPriority");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         removeWithPriority(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing removeWithpriority probably due to failed initialization of the queue " + queueType);
      }
   }


   /**
    * Test removeWithPriority(long[])
    */
   private void removeWithPriority(I_Queue queue) {
      ME = "I_QueueTest.removeWithPriority(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: remove prio 7 and 9
         {
           DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MIN_PRIORITY, queue.getStorageId(), true)
                                        };
            this.queue.removeStorageSizeListener(null);
            this.queue.addStorageSizeListener(this.queueSizeListener);
            this.queueSizeListener.clear();
                                        
            queue.put(queueEntries, false);

            long numRemoved = queue.removeWithPriority(-1, -1L, 7, 9);

            assertEquals("number of invocations", 2, this.queueSizeListener.getCount());
            assertEquals("number of entries incremented on last invocation", -8, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", -8*queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());
                                        
            assertEquals(ME+": Wrong number removed", 8, numRemoved);
            assertEquals(ME+": Wrong size", queueEntries.length-8, queue.getNumOfEntries());

            numRemoved = queue.removeWithPriority(-1, -1L, 27, 99);
            long sizeInBytes = (queueEntries.length - 8) * queueEntries[0].getSizeInBytes();
            assertEquals(ME+": Wrong size in bytes ", sizeInBytes, queue.getNumOfBytes());
            assertEquals(ME+": Wrong number removed", 0, numRemoved);
            assertEquals(ME+": Wrong number of entries ", queueEntries.length-8, queue.getNumOfEntries());

            queue.clear();

            log.info("#1 Success, fill and remove");
         }

         //========== Test 2: remove prio 7 and 9 with num limit
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.HIGH_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.MIN_PRIORITY, queue.getStorageId(), true)
                                        };
            this.queue.removeStorageSizeListener(null);
            this.queue.addStorageSizeListener(this.queueSizeListener);
            this.queueSizeListener.clear();
                                        
            queue.put(queueEntries, false);

            long numRemoved = queue.removeWithPriority(2, -1L, 7, 9);

            assertEquals(ME+": Wrong number removed", 2, numRemoved);
            assertEquals(ME+": Wrong size", queueEntries.length-2, queue.getNumOfEntries());
            assertEquals("number of invocations", 2, this.queueSizeListener.getCount());
            assertEquals("number of entries incremented on last invocation", -2, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", -2*queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());
                                        
            log.info("#2 Success, fill and remove");
         }
         queue.shutdown();
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

//------------------------------------
   public void testRemoveRandom() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/removeRandom");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         removeRandom(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing removeRandom probably due to failed initialization of the queue " + queueType);
      }

   }


   /**
    * Test removeRandom(long[])
    */
   private void removeRandom(I_Queue queue) {
      ME = "I_QueueTest.removeRandom(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "][" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: remove 1 from 1
         {
            this.queue.removeStorageSizeListener(null);
            this.queue.addStorageSizeListener(this.queueSizeListener);
            this.queueSizeListener.clear();

            //MsgUnit msgUnit = new MsgUnit("<key/>", "bla".getBytes(), "<qos/>");
            DummyEntry[] queueEntries = { new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true) };
            queue.put(queueEntries, false);

            I_QueueEntry[] testEntryArr = { queueEntries[0] };
            long numRemoved = 0L;
            boolean[] tmpArr = queue.removeRandom(testEntryArr);
            for (int i=0; i < tmpArr.length; i++) if(tmpArr[i]) numRemoved++;

            assertEquals("number of entries incremented on last invocation", -1, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", -queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());

            assertEquals(ME+": Wrong number removed", 1, numRemoved);
            assertEquals(ME+": Wrong size", 0, queue.getNumOfEntries());
            log.info("#1 Success, fill and random remove");
         }

         //========== Test 2: removeRandom 2 from 3
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);

            I_QueueEntry[] testEntryArr = { queueEntries[0], 
                                            queueEntries[2]
                                          };
            long numRemoved = 0L;
            boolean[] tmpArr = queue.removeRandom(testEntryArr);
            for (int i=0; i < tmpArr.length; i++) if(tmpArr[i]) numRemoved++;

            assertEquals("number of entries incremented on last invocation", -2, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", -2*queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());

            assertEquals(ME+": Wrong number removed", 2, numRemoved);
            assertEquals(ME+": Wrong size", 1, queue.getNumOfEntries());
            I_QueueEntry result = queue.peek();
            assertEquals(ME+": Wrong timestamp", queueEntries[1].getUniqueId(), result.getUniqueId());
            queue.clear();
            log.info("#2 Success, fill and random remove");
         }

         //========== Test 3: removeRandom 5 from 3
         {
            DummyEntry[] queueEntries = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true)
                                        };
            queue.put(queueEntries, false);

            I_QueueEntry[] dataIdArr = {
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         queueEntries[0],
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true),
                         queueEntries[2],
                                        };
            long numRemoved = 0L;
            boolean[] tmpArr = queue.removeRandom(dataIdArr);
            for (int i=0; i < tmpArr.length; i++) if(tmpArr[i]) numRemoved++;

            assertEquals("number of entries incremented on last invocation", -2, this.queueSizeListener.getLastIncrementEntries());
            assertEquals("number of bytes incremented on last invocation", -2*queueEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());

            assertEquals(ME+": Wrong number removed", 2, numRemoved);
            assertEquals(ME+": Wrong size", 1, queue.getNumOfEntries());

            I_QueueEntry entry = queue.peek();
            assertTrue("Missing entry", (I_QueueEntry)null != entry);
            assertEquals(ME+": Wrong entry removed", queueEntries[1].getUniqueId(), entry.getUniqueId());

            queue.clear();
            log.info("#3 Success, fill and random remove");
         }

         //========== Test 4: removeRandom 0 from 0
         {
            DummyEntry[] queueEntries = new DummyEntry[0];
            queue.put(queueEntries, false);

            I_QueueEntry[] dataIdArr = new I_QueueEntry[0];

            long numRemoved = 0L;
            boolean[] tmpArr = queue.removeRandom(dataIdArr);
            for (int i=0; i < tmpArr.length; i++) if(tmpArr[i]) numRemoved++;

            assertEquals(ME+": Wrong number removed", 0, numRemoved);
            assertEquals(ME+": Wrong size", 0, queue.getNumOfEntries());
            queue.clear();
            log.info("#4 Success, fill and random remove");
         }

         //========== Test 5: removeRandom null from null
         {
            queue.put((DummyEntry[])null, false);

//            long numRemoved = queue.removeRandom((I_QueueEntry[])null);
            long numRemoved = 0L;
            boolean[] tmpArr = queue.removeRandom((I_QueueEntry[])null);
            for (int i=0; i < tmpArr.length; i++) if(tmpArr[i]) numRemoved++;

            assertEquals(ME+": Wrong number removed", 0, numRemoved);
            assertEquals(ME+": Wrong size", 0, queue.getNumOfEntries());
            queue.clear();
            log.info("#5 Success, fill and random remove");
         }

         queue.shutdown();
         System.out.println("***" + ME + " [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }



//------------------------------------
   public void testTakeLowest() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/takeLowest");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         takeLowest(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing removeRandomLong probably due to failed initialization of the queue " + queueType);
      }

   }



   /**
    * returns the number of entries left in the queue after this processing operation 
    * @param queue the queue to use for this test
    * @param numEntries the number of entries to pass to the takeLowest operation
    * @param numBytes the num of bytes to pass to the takeLowest operation
    * @param leaveOne the flag to pass to the takeLowest operation
    * @param origEntries the array of the original entries put into the queue
    * @param entriesLeft number of entries left in the queue before this operation
    * @param currentEntries the number of entries which should have been processed by this operation
    */
   private final int assertCheckForTakeLowest(I_Queue queue, int numEntries, long numBytes, 
      I_QueueEntry refEntry, boolean leaveOne, I_QueueEntry[] origEntries, int entriesLeft, 
      int currentEntries, long size) throws XmlBlasterException {
      String me = ME + "/" + numEntries + "/" + numBytes + "/" + leaveOne + "/" + entriesLeft + "/" + currentEntries;
      if (log.isLoggable(Level.FINE)) log.fine("");
      assertEquals(me+": Wrong size of entry ", size, origEntries[0].getSizeInBytes());
      assertEquals(me+": Wrong amount of entries in queue before takeLowest invocation ", entriesLeft, queue.getNumOfEntries());
      assertEquals(me+": Wrong size of entries in queue before takeLowest invocation ", size*entriesLeft, queue.getNumOfBytes());
      assertEquals(me+": Wrong amount of persistent entries in queue before takeLowest invocation ", entriesLeft, queue.getNumOfPersistentEntries());
      assertEquals(me+": Wrong size of persistent entries in queue before takeLowest invocation ", size*entriesLeft, queue.getNumOfPersistentBytes());

      List<I_Entry> list = null;
      try {
         list = queue.peekLowest(numEntries, numBytes, refEntry, leaveOne);  // gives back all minus one
         assertEquals(me+": Wrong number of entries in peekLowest return ", currentEntries, list.size());
         assertEquals(me+": Wrong number of entries in queue after peekLowest invocation ", entriesLeft, queue.getNumOfEntries());
         assertEquals(me+": Wrong number of bytes in queue after peekLowest invocation ", size*(entriesLeft), queue.getNumOfBytes());
         assertEquals(me+": Wrong number of persistent bytes in queue after takeLowest invocation ", size*(entriesLeft), queue.getNumOfPersistentBytes());
      }
      catch (XmlBlasterException e) {
         if (e.getErrorCode()!=ErrorCode.INTERNAL_NOTIMPLEMENTED) throw e;
      }

      list = queue.takeLowest(numEntries, numBytes, refEntry, leaveOne);  // gives back all minus one

      assertEquals("number of entries incremented on last invocation", -currentEntries, this.queueSizeListener.getLastIncrementEntries());
      assertEquals("number of bytes incremented on last invocation", -currentEntries*origEntries[0].getSizeInBytes(), this.queueSizeListener.getLastIncrementBytes());

      assertEquals(me+": Wrong number of entries in takeLowest return ", currentEntries, list.size());
      assertEquals(me+": Wrong number of entries in queue after takeLowest invocation ", entriesLeft-currentEntries, queue.getNumOfEntries());
      assertEquals(me+": Wrong number of bytes in queue after takeLowest invocation ", size*(entriesLeft-currentEntries), queue.getNumOfBytes());
      assertEquals(me+": Wrong number of persistent bytes in queue after takeLowest invocation ", size*(entriesLeft-currentEntries), queue.getNumOfPersistentBytes());

      for (int i=entriesLeft-currentEntries; i < entriesLeft; i++) {
         int j = entriesLeft - 1 - i;
         long ref = ((I_QueueEntry)list.get(j)).getUniqueId();
         assertEquals(me+": Wrong sequence in takeLowest", origEntries[i].getUniqueId(), ref);
      }
      return entriesLeft - currentEntries;
   }


   /**
    * Test takeLowest(I_Queue)
    */
   private void takeLowest(I_Queue queue) {

      if (queue instanceof CacheQueueInterceptorPlugin) return;

      ME = "I_QueueTest.takeLowest(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: takeLowest without restrictions
         {
            log.fine("takeLowest test 1");
            int imax = 50;
            long size = 0L;
            long msgSize = 100L; // every msg is 100 bytes long
            int entriesLeft = imax;


            this.queue.removeStorageSizeListener(null);
            this.queue.addStorageSizeListener(this.queueSizeListener);
            this.queueSizeListener.clear();

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), msgSize, true);
               size += entries[i].getSizeInBytes();
               queue.put(entries[i], false);
            }

            assertEquals(ME+": Wrong number put", imax, queue.getNumOfEntries());
            assertEquals(ME+": Wrong expected size in bytes of entries", msgSize*imax, size);
            assertEquals(ME+": Wrong size in bytes put", size, queue.getNumOfBytes());

            entriesLeft = assertCheckForTakeLowest(queue,  0, -1L, null, true, entries, entriesLeft, 0, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, -1L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, -1L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, 0L, null, true, entries, entriesLeft, 0, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  0, 0L, null, true, entries, entriesLeft, 0, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, 0L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, 0L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, 50L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  0, 50L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, 50L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, 50L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, 100L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  0, 100L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, 100L, null, true, entries, entriesLeft, 1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, 100L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, 150L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  0, 150L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, 150L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, 150L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, 200L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  0, 200L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  1, 200L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue,  2, 200L, null, true, entries, entriesLeft, 2, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, -1L, null, true, entries, entriesLeft, entriesLeft-1, msgSize);
            entriesLeft = assertCheckForTakeLowest(queue, -1, -1L, null, false, entries, entriesLeft, 1, msgSize);
            assertEquals(ME+": Wrong size in takeLowest after cleaning ", queue.getNumOfEntries(), 0);
            queue.clear();
         }

         //========== Test 2: takeLowest which should return an empty array
         {
            log.fine("takeLowest test 2");
            int imax = 20;
            long size = 0L;

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               size += entries[i].getSizeInBytes();
               queue.put(entries[i], false);
            }

            DummyEntry queueEntry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);

            assertEquals(ME+": Wrong number put", imax, queue.getNumOfEntries());
            assertEquals(ME+": Wrong size in bytes put", size, queue.getNumOfBytes());

            // should return an empty array since the timestamp is  the last
            List<I_Entry> list = queue.takeLowest(-1, -1, queueEntry, true);

            assertEquals(ME+": Wrong size in takeLowest return ", 0, list.size());
            queue.clear();
            assertEquals(ME+": Wrong size in takeLowest after cleaning ", 0, queue.getNumOfEntries());
         }


         //========== Test 3: takeLowest should return 13 entries
         {
            log.fine("takeLowest test 3");
            int imax = 20;
            long size = 0L;

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               size += entries[i].getSizeInBytes();
               queue.put(entries[i], false);
            }
            DummyEntry queueEntry = entries[6];

            assertEquals(ME+": Wrong number put", imax, queue.getNumOfEntries());
            assertEquals(ME+": Wrong size in bytes put", size, queue.getNumOfBytes());

            // should return an empty array since the timestamp is  the last
            List<I_Entry> list = queue.takeLowest(-1, -1, queueEntry, true);

            assertEquals(ME+": Wrong size in takeLowest return ", list.size(), imax-6-1);
            queue.clear();
            assertEquals(ME+": Wrong size in takeLowest after cleaning ", queue.getNumOfEntries(), 0);
         }


         //========== Test 4: takeLowest without restrictions
         {
            log.fine("takeLowest test 4 (with entry null)");
            int imax = 20;
            long size = 0L;

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               size += entries[i].getSizeInBytes();
               queue.put(entries[i], false);
            }

            assertEquals(ME+": Wrong number put", imax, queue.getNumOfEntries());
            assertEquals(ME+": Wrong size in bytes put", size, queue.getNumOfBytes());

            List<I_Entry> list = queue.takeLowest(-1, -1, null, true);

            assertEquals(ME+": Wrong size in takeLowest return ", list.size(), entries.length-1);
            for (int i=1; i < imax; i++) {
               int j = imax - 1 - i;
               long ref = ((I_QueueEntry)list.get(j)).getUniqueId();
               assertEquals(ME+": Wrong unique ID", entries[i].getUniqueId(), ref);
            }
            queue.clear();
            assertEquals(ME+": Wrong size in takeLowest after cleaning ", queue.getNumOfEntries(), 0);
         }


      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


   public void testWrongOrder() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/takeLowest");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + "wrong size before starting ", 0, queue.getNumOfEntries());
         wrongOrder(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing removeRandomLong probably due to failed initialization of the queue " + queueType);
      }

   }

   /**
    * Test wrongOrder(I_Queue)
    */
   private void wrongOrder(I_Queue queue) {
      ME = "I_QueueTest.wrongOrder(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: checks if entries are returned in the correct
         // order even if they are inserted in the wrong order
         {
            log.fine("wrongOrder test 1");
            int imax = 5;
            long size = 0L;

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               size += entries[i].getSizeInBytes();
            }

            DummyEntry[] putEntries = new DummyEntry[imax];
            putEntries[0] = entries[3];
            putEntries[1] = entries[4];
            putEntries[2] = entries[2];
            putEntries[3] = entries[0];
            putEntries[4] = entries[1];

            queue.put(putEntries, false);

            assertEquals(ME+": Wrong number put", imax, queue.getNumOfEntries());
            assertEquals(ME+": Wrong size in bytes put", size, queue.getNumOfBytes());

            List<I_Entry> listPeekSamePrio = queue.peekSamePriority(-1, -1L);
            List<I_Entry> listPeekWithPrio = queue.peekWithPriority(-1, -1L, 0, 10);
            List<I_Entry> listPeek = queue.peek(-1, -1L);

            //they all should give the same result ...
            for (int i=0; i<imax; i++) {
               long id = entries[i].getUniqueId();
               long idPeekSamePrio = ((I_QueueEntry)listPeekSamePrio.get(i)).getUniqueId();
               long idPeekWithPrio = ((I_QueueEntry)listPeekWithPrio.get(i)).getUniqueId();
               long idPeek = ((I_QueueEntry)listPeek.get(i)).getUniqueId();
               assertEquals(ME+": Wrong entry for peekSamePrio ", id, idPeekSamePrio);
               assertEquals(ME+": Wrong entry for peekWithPrio ", id, idPeekWithPrio);
               assertEquals(ME+": Wrong entry for peek ", id, idPeek);
            }
            queue.clear();
            assertEquals(ME+": Wrong size in takeLowest after cleaning ", queue.getNumOfEntries(), 0);
         }

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


   public void testPutEntriesTwice() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/putEntriesTwice");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         putEntriesTwice(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing putEntriesTwice probably due to failed initialization of the queue " + queueType);
      }
   }


   /**
    * Test wrongOrder(I_Queue)
    */
   private void putEntriesTwice(I_Queue queue) {
      ME = "I_QueueTest.putEntriesTwice(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: checks if entries are returned in the correct
         // order even if they are inserted in the wrong order
         {
            log.fine("putEntriesTwice test 1");
            int imax = 5;
            long size = 0L;

            DummyEntry[] entries = new DummyEntry[imax];
            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               size += entries[i].getSizeInBytes();
            }

            queue.put(entries, false);
            queue.put(entries, false);

            assertEquals(ME+": Wrong number of entries after putting same entries twice ", imax, queue.getNumOfEntries());
            queue.removeRandom(entries);

            assertEquals(ME+": Wrong size in takeLowest after cleaning ", queue.getNumOfEntries(), 0);
         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }



   public void testPeekWithLimitEntry() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/peekWithLimitEntry");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         peekWithLimitEntry(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing peekWithLimitEntry probably due to failed initialization of the queue " + queueType);
      }
   }


   /**
    * Test testPeekWithLimitEntry(I_Queue)
    */
   private void peekWithLimitEntry(I_Queue queue) {
      ME = "I_QueueTest.peekWithLimitEntry(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: normal case where limitEntry is contained in the queue
         {
            log.fine("peekWithLimitEntry test 1");
            int imax = 5;

            DummyEntry[] entries = new DummyEntry[imax];
            entries[0] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[3] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[1] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[4] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[2] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);

            queue.put(entries, false);
            assertEquals(ME+": Wrong number of entries after putting same entries ", imax, queue.getNumOfEntries());

            List<I_Entry> list = queue.peekWithLimitEntry(entries[3]);
            assertEquals(ME+": Wrong number of peeked entries (with limit) ", 3, list.size());
            for (int i=0; i < list.size(); i++) {
               assertEquals(ME + ": Wrong order in peeked entries (with limit): ", entries[i].getUniqueId(), ((I_QueueEntry)list.get(i)).getUniqueId());
            }

            queue.removeRandom(entries);
            assertEquals(ME+": Wrong size in peekWithLimitEntry after cleaning ", queue.getNumOfEntries(), 0);
         }

         //========== Test 2: normal case where limitEntry is NOT contained in the queue (should not return anything)
         {
            log.fine("peekWithLimitEntry test 2");
            int imax = 5;

            DummyEntry[] entries = new DummyEntry[imax];
            entries[0] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[3] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[1] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[4] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[2] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);

            DummyEntry limitEntry = new DummyEntry(glob, PriorityEnum.HIGH8_PRIORITY, queue.getStorageId(), true);

            queue.put(entries, false);
            assertEquals(ME+": Wrong number of entries after putting same entries ", imax, queue.getNumOfEntries());

            List<I_Entry> list = queue.peekWithLimitEntry(limitEntry);
            assertEquals(ME+": Wrong number of peeked entries (with limit) ", 0, list.size());
            queue.removeRandom(entries);
            assertEquals(ME+": Wrong size in peekWithLimitEntry after cleaning ", queue.getNumOfEntries(), 0);
         }

         //========== Test 3: normal case where limitEntry is NOT contained in the queue
         {
            log.fine("peekWithLimitEntry test 3");
            int imax = 5;

            DummyEntry[] entries = new DummyEntry[imax];
            entries[0] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[3] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[1] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[4] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[2] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);

            DummyEntry limitEntry = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);

            queue.put(entries, false);
            assertEquals(ME+": Wrong number of entries after putting same entries ", imax, queue.getNumOfEntries());

            List<I_Entry> list = queue.peekWithLimitEntry(limitEntry);
            assertEquals(ME+": Wrong number of peeked entries (with limit) ", imax, list.size());
            for (int i=0; i < list.size(); i++) {
               assertEquals(ME + ": Wrong order in peeked entries (with limit): ", entries[i].getUniqueId(), ((I_QueueEntry)list.get(i)).getUniqueId());
            }

            queue.removeRandom(entries);
            assertEquals(ME+": Wrong size in peekWithLimitEntry after cleaning ", queue.getNumOfEntries(), 0);
         }

         //========== Test 4: normal case where limitEntry is NOT contained in the queue
         {
            log.fine("peekWithLimitEntry test 4");
            int imax = 5;

            DummyEntry[] entries = new DummyEntry[imax];
            entries[0] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[3] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[1] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
            entries[4] = new DummyEntry(glob, PriorityEnum.LOW_PRIORITY, queue.getStorageId(), true);
            entries[2] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);

            queue.put(entries, false);
            assertEquals(ME+": Wrong number of entries after putting same entries ", imax, queue.getNumOfEntries());

            List<I_Entry> list = queue.peekWithLimitEntry(null);
            assertEquals(ME+": Wrong number of peeked entries (with limit) ", 0, list.size());

            queue.removeRandom(entries);
            assertEquals(ME+": Wrong size in peekWithLimitEntry after cleaning ", queue.getNumOfEntries(), 0);
         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }



   public void testSizesCheck() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/testSizes");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         sizesCheck(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing sizesCheck probably due to failed initialization of the queue " + queueType);
      }
   }


   /**
    * Test sizesCheck(I_Queue)
    */
   private void sizesCheck(I_Queue queue) {
      ME = "I_QueueTest.sizesCheck(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: normal case where limitEntry is contained in the queue
         {
            log.fine("sizesCheck test 1");
            int imax = 20;

            DummyEntry[] entries = new DummyEntry[imax];
            List<I_Entry> list = new ArrayList<I_Entry>();

            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), true);
               list.add(entries[i]);
            }

            queue.put(entries, false);
            this.checkSizeAndEntries("sizesCheck test 1: ", list, queue);

            if (queue instanceof CacheQueueInterceptorPlugin) return;
            log.info("size of list before: " + list.size());
            queue.takeLowest(2, 100L, null, true);
            list.remove(list.size()-1);
            list.remove(list.size()-1);
            log.info("size of list after: " + list.size());

            this.checkSizeAndEntries("sizesCheck test 1 (after takeLowest): ", list, queue);

            queue.removeRandom(entries);
            list.removeAll(list);
            this.checkSizeAndEntries("sizesCheck test 1 (after removing): ", list, queue);


         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   public void testBigEntries() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/testSizes");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         bigEntries(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing sizesCheck probably due to failed initialization of the queue " + queueType);
      }
   }

   /*
   public void testPublishMsgBigEntry() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(Constants.RELATING_CALLBACK, "QueuePlugin/testSizes");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         publishMsgBigEntry(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing sizesCheck probably due to failed initialization of the queue " + queueType);
      }
   }
   */
   
   /**
    * Test bigEngtries(I_Queue)
    * It tests the insertion and removal of entries which contain a large blob (2.1MB)
    */
   private void publishMsgBigEntry(I_Queue queue) {
      ME = "I_QueueTest.publishMsgBigEntry(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         {
            log.fine("start test");
            int msgSize = 1000000;
            
            StorageId storageId = new StorageId(glob, "mystore", "test");
            byte[] content = new byte[msgSize];
            MsgUnit msgUnit = new MsgUnit(this.glob, "<key oid='aaa'/>", content, "<qos/>");
            MsgQueuePublishEntry pubEntry = new MsgQueuePublishEntry(this.glob, msgUnit, storageId);
            queue.put(pubEntry, false);
            I_Entry entry = queue.peek();
            queue.removeRandom(entry);
         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   /**
    * Test bigEngtries(I_Queue)
    * It tests the insertion and removal of entries which contain a large blob (2.1MB)
    */
   private void bigEntries(I_Queue queue) {
      ME = "I_QueueTest.bigEntries(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         {
            log.fine("start test");
            int imax = 3; 
            long msgSize = 202010L;
            DummyEntry[] entries = new DummyEntry[imax];
            List<I_Entry> list = new ArrayList<I_Entry>();

            for (int i=0; i < imax; i++) {
               entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), msgSize, true);
               list.add(entries[i]);
            }

            queue.put(entries, false);
            this.checkSizeAndEntries("sizesCheck test 1: ", list, queue);
            List<I_Entry> entriesArray = queue.peek(imax, -1L);
            assertEquals("wrong number of big entries retrieved", imax, entriesArray.size());
            queue.removeRandom(entries);
            list.removeAll(list);
            this.checkSizeAndEntries("sizesCheck test 1 (after removing): ", list, queue);


         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

// ---------------------------------------------------------------------

   public void testOverflow() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new CbQueueProperty(glob, Constants.RELATING_CALLBACK, "/node/test");
         prop.setMaxEntries(1L);
         prop.setMaxEntriesCache(1L);
         
         queueType = this.queue.toString();
         StorageId queueId = new StorageId(glob, Constants.RELATING_CALLBACK, "QueuePlugin/testOverflow");
         this.queue.initialize(queueId, prop);
         queue.clear();
         assertEquals(ME + " wrong size before starting ", 0, queue.getNumOfEntries());
         overflow(this.queue);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing overflowCheck probably due to failed initialization of the queue " + queueType);
      }
   }


   /**
    * Test overflow(I_Queue)
    * It tests if the overflow mechanism works OK
    */
   private void overflow(I_Queue queue) {
      ME = "I_QueueTest.overflow(" + queue.getStorageId() + ")[" + queue.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         log.fine("start test");
         int imax = 4; 
         long msgSize = 100L;
         boolean isPersistent = true;
         DummyEntry[] entries = new DummyEntry[imax];

         for (int i=0; i < imax; i++) {
            entries[i] = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, queue.getStorageId(), msgSize, isPersistent);
         }

         queue.put(entries[0], false); // <-- OK
         queue.put(entries[1], false); // <-- OK
         try {
            queue.put(entries[2], false); // <-- overflow
            assertTrue("here we expect an overflow exception", false);
         }
         catch (XmlBlasterException ex) {
            log.info("overflow: an exception here is OK since it was expected due to overflow of the queue");
         }
         try {
            queue.put(entries[3], false); // <-- overflow
            assertTrue("here we expect an overflow exception", false);
         }
         catch (XmlBlasterException ex) {
            log.info("overflow: an exception here is OK since it was expected due to overflow of the queue");
         }
   
         List<I_Entry> ret = queue.peek(4, -1L);
         assertEquals("the number of entries in the queue", 2, ret.size());
         for (int i=0; i < 2; i++) {
            assertEquals(ME + ".overflow: entry '" + i + "' in sequence is wrong", entries[i].getUniqueId(), ((I_QueueEntry)ret.get(i)).getUniqueId());
         }
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

// ---------------------------------------------------------------------

   public void tearDown() {
      try {
         this.queue.clear();
         this.queue.shutdown();
      }
      catch (Exception ex) {
         log.severe("error when tearing down " + ex.getMessage());
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<PLUGIN_TYPES.length; i++) {
         suite.addTest(new I_QueueTest("testConfig", i, glob));
         suite.addTest(new I_QueueTest("testSize1", i, glob));
         suite.addTest(new I_QueueTest("testPutMsg", i, glob));
         suite.addTest(new I_QueueTest("testPeekMsg", i, glob));
         suite.addTest(new I_QueueTest("testRemoveRandom", i, glob));
         suite.addTest(new I_QueueTest("testRemoveWithPriority", i, glob));
         suite.addTest(new I_QueueTest("testTakeLowest", i, glob));
         suite.addTest(new I_QueueTest("testPutEntriesTwice", i, glob));
         suite.addTest(new I_QueueTest("testPeekWithLimitEntry", i, glob));
         suite.addTest(new I_QueueTest("testSizesCheck", i, glob));
         suite.addTest(new I_QueueTest("testBigEntries", i, glob));
         suite.addTest(new I_QueueTest("testOverflow", i, glob));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.I_QueueTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);

      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         I_QueueTest testSub = new I_QueueTest("I_QueueTest", i, glob);

         long startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testSizesCheck();
         testSub.tearDown();

         /*
         testSub.setUp();
         testSub.testPublishMsgBigEntry();
         testSub.tearDown();
         */
         
         testSub.setUp();
         testSub.testPeekMsgBlocking();
         testSub.tearDown();

         testSub.setUp();
         testSub.testConfig();
         testSub.tearDown();

         testSub.setUp();
         testSub.testSize1();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPutMsg();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPeekMsg();
         testSub.tearDown();

         testSub.setUp();
         testSub.testRemoveRandom();
         testSub.tearDown();

         testSub.setUp();
         testSub.testRemoveWithPriority();
         testSub.tearDown();

         testSub.setUp();
         testSub.testTakeLowest();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPutEntriesTwice();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPeekWithLimitEntry();
         testSub.tearDown();

         testSub.setUp();
         testSub.testBigEntries();
         testSub.tearDown();

         testSub.setUp();
         testSub.testOverflow();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         I_QueueTest.log.info("time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

