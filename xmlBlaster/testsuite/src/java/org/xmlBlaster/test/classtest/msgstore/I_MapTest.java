package org.xmlBlaster.test.classtest.msgstore;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin;
import org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin;
import org.xmlBlaster.util.queue.jdbc.JdbcManager;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.engine.msgstore.ram.MapPlugin;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.MsgUnitWrapper;
import org.xmlBlaster.engine.xml2java.XmlKey;

import java.util.ArrayList;
import java.util.StringTokenizer;

import junit.framework.*;

/**
 * Test I_Map e.g. MapPlugin which allows to store randomly messages. 
 * <p>
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.msgstore.I_MapTest
 * </p>
 * @see org.xmlBlaster.engine.msgstore.I_Map
 * @see org.xmlBlaster.engine.msgstore.ram.MapPlugin
 * @see org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin
 */
public class I_MapTest extends TestCase {
   private String ME = "I_MapTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

   private I_Map currMap;
   static I_Map[] IMPL = {
                   new org.xmlBlaster.engine.msgstore.ram.MapPlugin(),
                   new org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin(),
                   new org.xmlBlaster.engine.msgstore.cache.MsgUnitStoreCachePlugin()
                 };

   public I_MapTest(String name, int currImpl) {
      super(name);
      this.currMap = IMPL[currImpl];
      String[] args = { // configure the cache
         "-msgUnitStore.persistentQueue", "JDBC,1.0",
         "-msgUnitStore.transientQueue", "RAM,1.0",
      };
      this.glob = new Global(args);
      //this.ME = "I_MapTest[" + this.currMap.getClass().getName() + "]";
   }

   protected void setUp() {
      log = glob.getLog(null);
      try {
         glob.getProperty().set("topic.queue.persistent.tableNamePrefix", "TEST");
      }
      catch (Exception ex) {
         this.log.error(ME, "setUp: error when setting the property 'topic.queue.persistent.tableNamePrefix' to 'TEST': " + ex.getMessage());
      }

      // cleaning up the database from previous runs ...
/*
      QueuePropertyBase prop = null;
      try {
         prop = new MsgUnitStoreProperty(glob, "/node/test");

         StorageId queueId = new StorageId("msgUnitStore", "SetupMap");
         JdbcMapPlugin jdbcMap = new JdbcMapPlugin();
         jdbcMap.initialize(queueId, prop);
         jdbcMap.destroy();
      }
      catch (Exception ex) {
         this.log.error(ME, "could not propertly set up the database: " + ex.getMessage());
      }
*/
   }

   private MsgUnit createMsgUnit(boolean isDurable) {
      try {
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos/>");
         publishQosServer.getData().setDurable(isDurable);
         return new MsgUnit(glob, "<key oid='Hi'/>", "content".getBytes(), publishQosServer.toXml());
      }
      catch (XmlBlasterException ex) {
         fail("msgUnit not constructed: " + ex.getMessage());
      }
      return null;
   }

   /**
    * Tests QueuePropertyBase() and getStorageId()
    * @param queueTypeList A space separated list of names for the
    *        implementations to be tested. Valid names are:
    *        RamMapPlugin JdbcMapPlugin
    */
   public void testConfig() {
      config(this.currMap);
   }

   /**
    * Tests initialize(), getProperties(), setProperties() and capacity()
    * @param queue !!!Is not initialized in this case!!!!
    */
   private void config(I_Map i_map) {
      ME = "I_MapTest.config(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);

      QueuePropertyBase prop1 = null;
      QueuePropertyBase prop = null;
      try {
         // test initialize()
         prop1 = new MsgUnitStoreProperty(glob, "/node/test");
         int max = 12;
         prop1.setMaxMsg(max);
         prop1.setMaxMsgCache(max);
         assertEquals(ME+": Wrong capacity", max, prop1.getMaxMsg());
         assertEquals(ME+": Wrong cache capacity", max, prop1.getMaxMsgCache());
         StorageId queueId = new StorageId("msgUnitStore", "SomeMapId");

         i_map.initialize(queueId, prop1);
         assertEquals(ME+": Wrong queue ID", queueId, i_map.getStorageId());

         try {
            prop = new MsgUnitStoreProperty(glob, "/node/test");
            prop.setMaxMsg(99);
            prop.setMaxMsgCache(99);
            i_map.setProperties(prop);
         }
         catch(XmlBlasterException e) {
            fail("Changing properties failed: " + e.getMessage());
         }

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }

      long len = prop.getMaxMsg();
      assertEquals(ME+": Wrong capacity", prop.getMaxMsg(), i_map.getMaxNumOfEntries());
      assertEquals(ME+": Wrong capacity", prop.getMaxMsg(), ((QueuePropertyBase)i_map.getProperties()).getMaxMsg());
      assertEquals(ME+": Wrong size", 0, i_map.getNumOfEntries());

      try {
         for (int ii=0; ii<len; ii++) {
            i_map.put(new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId()));
         }
         assertEquals(ME+": Wrong total size", len, i_map.getNumOfEntries());

         try {
            MsgUnitWrapper queueEntry = new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId());
            i_map.put(queueEntry);
            i_map.put(queueEntry);
            fail("Did expect an exception on overflow getMaxNumOfEntries=" + i_map.getMaxNumOfEntries() + " size=" + i_map.getNumOfEntries());
         }
         catch(XmlBlasterException e) {
            log.info(ME, "SUCCESS the exception is OK: " + e.getMessage());
         }

         log.info(ME, "toXml() test:" + i_map.toXml(""));
         log.info(ME, "usage() test:" + i_map.usage());

         assertEquals(ME+": should not be shutdown", false, i_map.isShutdown());
         i_map.shutdown(true);
         assertEquals(ME+": should be shutdown", true, i_map.isShutdown());

         log.info(ME, "#2 Success, filled " + i_map.getNumOfEntries() + " messages into queue");
         System.out.println("***" + ME + " [SUCCESS]");
         i_map.shutdown(true);
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

//------------------------------------
   public void testPutMsg() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");
         queueType = this.currMap.toString();
         StorageId queueId = new StorageId("msgUnitStore", "MapPlugin/putMsg");
         this.currMap.initialize(queueId, prop);
         this.currMap.clear();
         assertEquals(ME + "wrong size before starting ", 0L, this.currMap.getNumOfEntries());
         putMsg(this.currMap);
      }
      catch (XmlBlasterException ex) {
         fail("Exception when testing PutMsg probably due to failed initialization of the queue of type " + queueType + ": " + ex.getMessage());
      }
   }

   /**
    * Tests put(MsgMapEntry[]) and put(MsgMapEntry) and clear()
    */
   private void putMsg(I_Map i_map) {
      ME = "I_MapTest.putMsg(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: put(I_MapEntry[])
         int numLoop = 10;
         ArrayList list = new ArrayList();
         for (int ii=0; ii<numLoop; ii++) {
            MsgUnitWrapper[] queueEntries = {
                         new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId())};

            for(int i=0; i<queueEntries.length; i++)
               i_map.put(queueEntries[i]);

            for (int i=0; i < 3; i++) list.add(queueEntries[i]);

            this.checkSizeAndEntries(" put(I_MapEntry[]) ", list, i_map);
            assertEquals(ME+": Wrong size", (ii+1)*queueEntries.length, i_map.getNumOfEntries());
         }
         int total = numLoop*3;
         assertEquals(ME+": Wrong total size", total, i_map.getNumOfEntries());
         log.info(ME, "#1 Success, filled " + i_map.getNumOfEntries() + " messages into queue");


         //========== Test 2: put(I_MapEntry)
         for (int ii=0; ii<numLoop; ii++) {
            MsgUnitWrapper queueEntry = new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId());
            list.add(queueEntry);
            i_map.put(queueEntry);
         }
         assertEquals(ME+": Wrong total size", numLoop+total, i_map.getNumOfEntries());
         this.checkSizeAndEntries(" put(I_MapEntry) ", list, i_map);
         log.info(ME, "#2 Success, filled " + i_map.getNumOfEntries() + " messages into queue");

         i_map.clear();
         checkSizeAndEntries("Test 2 put()", new I_MapEntry[0], i_map);
         assertEquals(ME+": Wrong empty size", 0L, i_map.getNumOfEntries());

         System.out.println("***" + ME + " [SUCCESS]");
         i_map.shutdown(true);
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


//------------------------------------
   public void testGetMsg() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");
         queueType = this.currMap.toString();
         StorageId queueId = new StorageId("msgUnitStore", "MapPlugin/getMsg");
         this.currMap.initialize(queueId, prop);
         this.currMap.clear();
         assertEquals(ME + "wrong size before starting ", 0, this.currMap.getNumOfEntries());
         getMsg(this.currMap);
      }
      catch (XmlBlasterException ex) {
         log.error(ME, "Exception when testing getMsg probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
      }

   }

   /**
    * Tests get() and get(int num) and remove()
    * For a discussion of the sorting order see Javadoc of this class
    */
   private void getMsg(I_Map i_map) {
      ME = "I_MapTest.getMsg(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: get()
         {
            MsgUnitWrapper[] queueEntries = {
                         new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(true), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(true), i_map.getStorageId())
                                        };
            for(int i=0; i<queueEntries.length; i++) {
               i_map.put(queueEntries[i]);
               log.info(ME, "#" + i + " id=" + queueEntries[i].getUniqueId() + " numSizeBytes()=" + queueEntries[i].getSizeInBytes());
            }
            log.info(ME, "storage bytes sum=" + i_map.getNumOfBytes() + " with durable bytes=" + i_map.getNumOfDurableBytes());

            assertEquals("", 3, i_map.getNumOfEntries());
            assertEquals("", 2, i_map.getNumOfDurableEntries());

            for (int ii=0; ii<10; ii++) {
               I_MapEntry result = i_map.get(queueEntries[0].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[0].getUniqueId(), result.getUniqueId());

               result = i_map.get(queueEntries[1].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), result.getUniqueId());

               result = i_map.get(queueEntries[2].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), result.getUniqueId());
            }
            assertEquals("", 3, i_map.getNumOfEntries());
            assertEquals("", 2, i_map.getNumOfDurableEntries());

            log.info(ME, "storage before remove [0], bytes sum=" + i_map.getNumOfBytes() + " with durable bytes=" + i_map.getNumOfDurableBytes());
            i_map.remove(queueEntries[0]); // Remove one
            log.info(ME, "storage after remove [0], bytes sum=" + i_map.getNumOfBytes() + " with durable bytes=" + i_map.getNumOfDurableBytes());
            ArrayList list = new ArrayList();
            list.add(queueEntries[1]);
            list.add(queueEntries[2]);
            this.checkSizeAndEntries(" getMsg() ", list, i_map);

            for (int ii=0; ii<10; ii++) {
               I_MapEntry result = i_map.get(queueEntries[1].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), result.getUniqueId());
            }
            i_map.remove(queueEntries[1]); // Remove one
            assertEquals("", 1, i_map.getNumOfEntries());
            assertEquals("", 1, i_map.getNumOfDurableEntries());

            for (int ii=0; ii<10; ii++) {
               I_MapEntry result = i_map.get(queueEntries[2].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), result.getUniqueId());
            }
            i_map.remove(queueEntries[2]); // Remove one
            for (int ii=0; ii<10; ii++) {
               I_MapEntry result = i_map.get(queueEntries[0].getUniqueId());
               assertTrue("Unexpected entry", result == null);
            }
            assertEquals("", 0, i_map.getNumOfEntries());
            assertEquals("", 0, i_map.getNumOfDurableEntries());
            log.info(ME, "#1 Success, get()");
         }

         System.out.println("***" + ME + " [SUCCESS]");
         i_map.clear();
         i_map.shutdown(true);
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   public void testPutEntriesTwice() {
      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");
         queueType = this.currMap.toString();
         StorageId queueId = new StorageId("msgUnitStore", "MapPlugin/putEntriesTwice");
         this.currMap.initialize(queueId, prop);
         this.currMap.clear();
         assertEquals(ME + " wrong size before starting ", 0, this.currMap.getNumOfEntries());
         putEntriesTwice(this.currMap);
      }
      catch (XmlBlasterException ex) {
         log.error(ME, "Exception when testing putEntriesTwice probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
      }
   }

   private void putEntriesTwice(I_Map i_map) {
      ME = "I_MapTest.putEntriesTwice(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: checks if entries are returned in the correct
         // order even if they are inserted in the wrong order
         {
            this.log.trace(ME, "putEntriesTwice test 1");
            int imax = 5;
            long size = 0L;

            MsgUnitWrapper[] entries = new MsgUnitWrapper[imax];
            for (int i=0; i < entries.length; i++) {
               entries[i] = new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId());
               size += entries[i].getSizeInBytes();
            }

            for(int i=0; i<entries.length; i++) {
               i_map.put(entries[i]);
               i_map.put(entries[i]);
            }

            assertEquals(ME+": Wrong number of entries after putting same entries twice", entries.length, i_map.getNumOfEntries());
            i_map.clear();
            assertEquals(ME+": Wrong size after cleaning", i_map.getNumOfEntries(), 0);
         }
         System.out.println("***" + ME + " [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }

   public void tearDown() {
      try {
         this.currMap.destroy();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         this.log.error(ME, "error when tearing down " + ex.getMessage());
      }
   }

   /**
    * @see checkSizeAndEntries(String, I_MapEntry[], I_Map)
    */
   private void checkSizeAndEntries(String txt, ArrayList queueEntries, I_Map map) {
      checkSizeAndEntries(txt, (I_MapEntry[])queueEntries.toArray(new I_MapEntry[queueEntries.size()]), map);
   }

   /**
    * Helper method to do a generic size check (size and number of entries)
    */
   private void checkSizeAndEntries(String txt, I_MapEntry[] queueEntries, I_Map i_map) {
      long sizeOfTransients = 0L;
      long numOfDurables = 0;
      long numOfTransients = 0;
      long sizeOfDurables = 0L;
      for (int i=0; i < queueEntries.length; i++) {
         I_MapEntry entry = queueEntries[i];
         if (entry.isDurable()) {
            sizeOfDurables += entry.getSizeInBytes();
            numOfDurables++;
         }
         else {
            sizeOfTransients += entry.getSizeInBytes();
            numOfTransients++;
         }
      }

      long queueNumOfDurables = i_map.getNumOfDurableEntries();
      long queueNumOfTransients = i_map.getNumOfEntries() - queueNumOfDurables;
      long queueSizeOfDurables = i_map.getNumOfDurableBytes();
      long queueSizeOfTransients = i_map.getNumOfBytes() - queueSizeOfDurables;

      txt += " getNumOfDurableEntries=" + queueNumOfDurables + " NumOfTransients=" + queueNumOfTransients; 
      txt += " getNumOfDurableBytes=" + queueSizeOfDurables + " SizeOfTransients=" + queueSizeOfTransients;

      assertEquals(ME + ": " + txt + " wrong number of durables   ", numOfDurables, queueNumOfDurables);
      assertEquals(ME + ": " + txt + " wrong number of transients ", numOfTransients, queueNumOfTransients);
      assertEquals(ME + ": " + txt + " wrong size of durables     ", sizeOfDurables, queueSizeOfDurables);
      assertEquals(ME + ": " + txt + " wrong size of transients   ", sizeOfTransients, queueSizeOfTransients);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      for (int i=0; i<IMPL.length; i++) {
         suite.addTest(new I_MapTest("testConfig", i));
         suite.addTest(new I_MapTest("testPutMsg", i));
         suite.addTest(new I_MapTest("testGetMsg", i));
         suite.addTest(new I_MapTest("testPutEntriesTwice", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.msgstore.I_MapTest
    * </pre>
    */
   public static void main(String args[]) {

      Global glob = new Global(args);

      for (int i=0; i < IMPL.length; i++) {
         I_MapTest testSub = new I_MapTest("I_MapTest", i);

         long startTime = System.currentTimeMillis();

/*
         testSub.setUp();
         testSub.testConfig();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPutMsg();
         testSub.tearDown();
*/

         testSub.setUp();
         testSub.testGetMsg();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPutEntriesTwice();
         testSub.tearDown();

         long usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info(testSub.ME, "time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

