package org.xmlBlaster.test.classtest.queue;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.jdbc.JdbcManagerCommonTable;
import org.xmlBlaster.util.queuemsg.DummyEntry;

import junit.framework.*;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import java.util.ArrayList;

/**
 * Test
 */
public class JdbcManagerCommonTableTest extends TestCase {
   private String ME = "JdbcManagerCommonTableTest";
   protected Global glob;
   protected LogChannel log;
   private JdbcManagerCommonTable manager;

   public JdbcManagerCommonTableTest(Global glob, String name) {
      super(name);
      this.glob = glob;
      log = glob.getLog("test");
   }

   protected void setUp() {
      //here you should clean up the DB (the three tables)
      String me = ME + ".setUp";
      try {
         QueuePluginManager pluginManager = new QueuePluginManager(glob);
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");

         java.util.Properties
            prop = (java.util.Properties)pluginInfo.getParameters().clone();

         prop.put("tableNamePrefix", "TEST");
         prop.put("entriesTableName", "_entries");

         JdbcConnectionPool pool = new JdbcConnectionPool();
         pool.initialize(this.glob, prop);

         this.manager = new JdbcManagerCommonTable(pool, this.glob.getEntryFactory(), pluginInfo.getTypeVersion());
         this.manager.wipeOutDB(false);
      }
      catch (Exception ex) {
         log.error(me, "exception occured " + ex.toString());
         assertTrue(me, false);
      }
   }

   public void testManager() {
//      try {
         manager();
/*
      }
      catch (XmlBlasterException ex) {
         log.error(me, "Exception when testing manager " + ex.toString());
      }
*/
   }


   /**
    * Tests peek() and peek(int num) and remove()
    * For a discussion of the sorting order see Javadoc of this class
    */
   private void manager() {
      String me = ME + ".manager";
      this.log.info(me, "starting testing");
      try {
         boolean hasTables = this.manager.tablesCheckAndSetup(false);
         assertEquals(me + " initially the tables should not exist yet", false, hasTables);
 
         if (this.log.TRACE) this.log.trace(me, "going to set up");
         this.manager.setUp();
 
         StorageId storageId = new StorageId(this.glob, "cb:queue1");
         String queueName = storageId.getStrippedId();

         long size = 100; // entry.getSizeInBytes();
         DummyEntry entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);

         try {
            this.manager.addEntry(queueName, entry);
            assertTrue(me + " adding an entry with no queue nor node associated should fail", false);         
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the previous exception is expected and is OK in this context");
         }
         try {
            this.manager.addEntry(queueName, entry);
            assertTrue(me + " adding an entry with no queue associated should fail", false);
         }
         catch (XmlBlasterException ex) {
            this.log.info(me, "the previous exception is expected and is OK in this context");
         }
         boolean ret = this.manager.addEntry(queueName, entry);
         assertEquals(me + " adding an entry should give back true", true, ret);

         long totalSize = size;
 
         ret = this.manager.addEntry(queueName, entry);
         assertEquals(me + " adding an entry the second time should give back 'false'", false, ret);
 
         // add some more entries
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true); 
         this.manager.addEntry(queueName, entry);
         totalSize += size;

         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, false); 
         this.manager.addEntry(queueName, entry);
         totalSize += size;

         // check here a modification of an entry:
         long oldNumOfEntries = this.manager.getNumOfEntries(queueName);
         long oldNumOfBytes = this.manager.getNumOfBytes(queueName);

         entry.setPersistent(true);
         this.manager.modifyEntry(queueName, entry);
         long[] dataIds = new long[1];
         dataIds[0] = entry.getUniqueId();
         ArrayList tmp = this.manager.getEntries(storageId, dataIds);
         assertEquals(me + " modified entry is not correct ", true, ((DummyEntry)(tmp.get(0))).isPersistent());

         entry.setPersistent(false);
         this.manager.modifyEntry(queueName, entry);
         dataIds = new long[1];
         dataIds[0] = entry.getUniqueId();
         tmp = this.manager.getEntries(storageId, dataIds);
         assertEquals(me + " modified entry is not correct ", false, ((DummyEntry)(tmp.get(0))).isPersistent());

         // did the modification change the number or the size of the entries ?
         assertEquals(me + " modification did change the number of entries", oldNumOfEntries, this.manager.getNumOfEntries(queueName));
         assertEquals(me + " modification did change the number of bytes", oldNumOfBytes, this.manager.getNumOfBytes(queueName));

         long numOfBytes = this.manager.getNumOfBytes(queueName);
         assertEquals(me + " num of bytes in queue 'queue1'", totalSize, numOfBytes);

         // count the number of entries here: they should be 4
         this.manager.deleteAllTransient(queueName);
         numOfBytes = this.manager.getNumOfBytes(queueName);
         totalSize -= size;
         assertEquals(me + " num of bytes in queue 'queue1' after deleting transients", totalSize, numOfBytes);

         ReturnDataHolder retHolder = this.manager.getAndDeleteLowest(storageId, 2, -1, 10, -1, false, true);
         assertEquals(me + " getAndDeleteLowest check", 2, retHolder.countEntries);

         long entriesToDelete[] = new long[2];
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);
         entriesToDelete[0] = entry.getUniqueId();
         this.manager.addEntry(queueName, entry);
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);
         this.manager.addEntry(queueName, entry);
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);
         entriesToDelete[1] = entry.getUniqueId();
         this.manager.addEntry(queueName, entry);
         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);
         this.manager.addEntry(queueName, entry);

         boolean[] tmpArr = this.manager.deleteEntries(queueName, entriesToDelete);
         int numOfDel = 0;
         for (int i=0; i < tmpArr.length; i++) {
            if ( tmpArr[i] ) numOfDel++;
         }

         assertEquals(me + " deleteEntries check", 2, numOfDel);

         retHolder = this.manager.deleteFirstEntries(queueName, 1L, 10000L);
         assertEquals(me + " deleteFirstEntries check", 1, retHolder.countEntries);

         ArrayList arrayList = this.manager.getEntriesByPriority(storageId, -1, -1, 0, 9, null);
         assertEquals(me + " getEntriesByPriority check", 1, arrayList.size());


         long[] entriesToGet = new long[2];
         entry = new DummyEntry(glob, PriorityEnum.MAX_PRIORITY, storageId, size, true);
         entriesToGet[0] = entry.getUniqueId();
         this.manager.addEntry(queueName, entry);

         arrayList = this.manager.getEntriesBySamePriority(storageId, -1, -1);
         assertEquals(me + " getEntriesBySamePriority check", 1, arrayList.size());

         arrayList = this.manager.getEntries(storageId, -1, -1, null);
         assertEquals(me + " getEntries check", 2, arrayList.size());

         entry = new DummyEntry(glob, PriorityEnum.NORM_PRIORITY, storageId, size, true);
         entriesToGet[1] = entry.getUniqueId();

         this.manager.addEntry(queueName, entry);
         arrayList = this.manager.getEntriesWithLimit(storageId, entry);
         assertEquals(me + " getEntriesWithLimit check", 2, arrayList.size());

         arrayList = this.manager.getEntries(storageId, entriesToGet);
         assertEquals(me + " getEntries check", 2, arrayList.size());

         long num = this.manager.getNumOfEntries(queueName);
         assertEquals(me + " getNumOfEntries check", 3L, num);

         num = this.manager.getNumOfPersistents(queueName);
         assertEquals(me + " getNumOfPersistents check", 3L, num);

         num = this.manager.getSizeOfPersistents(queueName);
         assertEquals(me + " getSizeOfPersistents check", 3*size, num);

//         int retNum = this.manager.cleanUp(queueName);
//         assertEquals(me + " cleaning up the queue should remove all queue entries", 1, retNum);
         boolean pingOK = this.manager.ping();
         assertEquals(me + " check ping command", true, pingOK);

         // can be run manually by adding -numOfPings 10 at the command line ...
         int numOfPings = glob.getProperty().get("numOfPings", 0);
         for (int i=0; i < numOfPings; i++) {
            this.log.info(ME, "going to ping");
            this.manager.ping();
            try {
               Thread.sleep(200L);
            }
            catch (Exception ex) {
            }
         }

         this.log.info(me, "successfully completed the tests");
      }
      catch (Exception ex) {
         assertEquals(me + " exception occured when testing 'manager' " + ex.toString(), 0, 1);
      }
   }

   public void tearDown() {
      String me = ME + ".tearDown";
      try {
         this.manager.wipeOutDB(false);
      }
      catch (Exception ex) {
         this.log.error(me, "exception occured " + ex.toString());
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      Global glob = new Global();
      suite.addTest(new JdbcManagerCommonTableTest(glob, "testJdbcManagerCommonTable"));
      return suite;
   }


   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.queue.JdbcManagerCommonTableTest
    * </pre>
    */
   public static void main(String args[]) {
      Global glob = Global.instance();
      glob.init(args);
      JdbcManagerCommonTableTest
         testSub = new JdbcManagerCommonTableTest(glob, "JdbcManagerCommonTableTest");
      testSub.setUp();
      testSub.testManager();
      testSub.tearDown();
   }
}

