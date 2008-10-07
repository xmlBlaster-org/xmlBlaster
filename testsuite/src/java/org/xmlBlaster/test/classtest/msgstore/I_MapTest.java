package org.xmlBlaster.test.classtest.msgstore;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.engine.msgstore.I_MapEntry;
import org.xmlBlaster.engine.msgstore.I_Map;
import org.xmlBlaster.util.qos.storage.MsgUnitStoreProperty;
import org.xmlBlaster.util.qos.storage.QueuePropertyBase;
import org.xmlBlaster.engine.qos.PublishQosServer;
import org.xmlBlaster.engine.MsgUnitWrapper;

import java.util.ArrayList;

import junit.framework.*;
import org.xmlBlaster.engine.msgstore.StoragePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;

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
   protected ServerScope glob;
   private static Logger log = Logger.getLogger(I_MapTest.class.getName());

   private final boolean IS_DURABLE = true;
   private final boolean IS_TRANSIENT = false;

   private I_Map currMap;
   private int currImpl;
/*
   static I_Map[] IMPL = {
                   new org.xmlBlaster.engine.msgstore.ram.MapPlugin(),
                   new org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin(),
                   new org.xmlBlaster.engine.msgstore.cache.PersistenceCachePlugin()
                 };
*/
   static String[] PLUGIN_TYPES = { new String("RAM"),
                                    new String("JDBC"),
                                    new String("CACHE") };

   public I_MapTest(String name, int currImpl) {
      super(name);
      this.currImpl = currImpl;

      String[] args = { //configure the cache
         "-persistence.persistentQueue", "JDBC,1.0",
         "-persistence.transientQueue", "RAM,1.0" };

      this.glob = new ServerScope(args);

      //this.ME = "I_MapTest[" + this.currMap.getClass().getName() + "]";
   }

   protected void setUp() {
      try {
         glob.getProperty().set("topic.queue.persistent.tableNamePrefix", "TEST");

         String type = PLUGIN_TYPES[this.currImpl];
         StoragePluginManager pluginManager = this.glob.getStoragePluginManager();
         // Overwrite JDBC settings from xmlBlaster.properties
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         java.util.Properties prop = (java.util.Properties)pluginInfo.getParameters();
         prop.put("tableNamePrefix", "TEST");
         prop.put("entriesTableName", "_entries");
         this.glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());

         if (!"JDBC".equals(type))
            pluginInfo = new PluginInfo(glob, pluginManager, type, "1.0");

         MsgUnitStoreProperty storeProp = new MsgUnitStoreProperty(glob, "/node/test");
         StorageId queueId = new StorageId("msgUnitStore", "SomeMapId");

         this.currMap = pluginManager.getPlugin(pluginInfo, queueId, storeProp);
         this.currMap.shutdown(); // to allow to initialize again
      }
      catch (Exception ex) {
         log.severe("setUp: error when setting the property 'topic.queue.persistent.tableNamePrefix' to 'TEST': " + ex.getMessage());
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
         log.severe("could not propertly set up the database: " + ex.getMessage());
      }
*/
   }

   private MsgUnit createMsgUnit(boolean persistent) {
      return createMsgUnit(persistent, -1);
   }

   private MsgUnit createMsgUnit(boolean persistent, long contentLen_) {
      try {
         int contentLen = (int)contentLen_;
         PublishQosServer publishQosServer = new PublishQosServer(glob, "<qos/>");
         publishQosServer.getData().setPersistent(persistent);
         String contentStr = "content";
         if (contentLen >= 0) {
            StringBuffer content = new StringBuffer(contentLen);
            for (int i=0; i<contentLen; i++) {
               content.append("X");
            }
            contentStr = content.toString();
         }
         return new MsgUnit(glob, "<key oid='Hi'/>", contentStr.getBytes(), publishQosServer.toXml());
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
         prop1.setMaxEntries(max);
         prop1.setMaxEntriesCache(max);
         assertEquals(ME+": Wrong capacity", max, prop1.getMaxEntries());
         assertEquals(ME+": Wrong cache capacity", max, prop1.getMaxEntriesCache());
         StorageId queueId = new StorageId("msgUnitStore", "SomeMapId");

         i_map.initialize(queueId, prop1);
         assertEquals(ME+": Wrong queue ID", queueId, i_map.getStorageId());

         try {
            prop = new MsgUnitStoreProperty(glob, "/node/test");
            prop.setMaxEntries(99);
            prop.setMaxEntriesCache(99);
            i_map.setProperties(prop);
         }
         catch(XmlBlasterException e) {
            fail("Changing properties failed: " + e.getMessage());
         }

      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }

      long len = prop.getMaxEntries();
      assertEquals(ME+": Wrong capacity", prop.getMaxEntries(), i_map.getMaxNumOfEntries());
      assertEquals(ME+": Wrong capacity", prop.getMaxEntries(), ((QueuePropertyBase)i_map.getProperties()).getMaxEntries());
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
            log.info("SUCCESS the exception is OK: " + e.getMessage());
         }

         log.info("toXml() test:" + i_map.toXml(""));
         log.info("usage() test:" + i_map.usage());

         assertEquals(ME+": should not be shutdown", false, i_map.isShutdown());
         i_map.shutdown();
         assertEquals(ME+": should be shutdown", true, i_map.isShutdown());

         log.info("#2 Success, filled " + i_map.getNumOfEntries() + " messages into queue");
         System.out.println("***" + ME + " [SUCCESS]");
         i_map.shutdown();
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
         log.info("#1 Success, filled " + i_map.getNumOfEntries() + " messages into queue");


         //========== Test 2: put(I_MapEntry)
         for (int ii=0; ii<numLoop; ii++) {
            MsgUnitWrapper queueEntry = new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId());
            list.add(queueEntry);
            i_map.put(queueEntry);
         }
         assertEquals(ME+": Wrong total size", numLoop+total, i_map.getNumOfEntries());
         this.checkSizeAndEntries(" put(I_MapEntry) ", list, i_map);
         log.info("#2 Success, filled " + i_map.getNumOfEntries() + " messages into queue");

         i_map.clear();
         checkSizeAndEntries("Test 2 put()", new I_MapEntry[0], i_map);
         assertEquals(ME+": Wrong empty size", 0L, i_map.getNumOfEntries());

         System.out.println("***" + ME + " [SUCCESS]");
         i_map.shutdown();
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }


   /**
    * Tests overflow of maxNumOfBytes() of a CACHE. 
    */
   public void testByteOverflow() {
      I_Map i_map = this.currMap;
      ME = "I_MapTest.testByteOverflow(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         StorageId storageId = new StorageId("msgUnitStore", "ByteOverflowMapId");
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");

         MsgUnitWrapper mu = new MsgUnitWrapper(glob, createMsgUnit(false, 0),  storageId);
         long sizeEmpty = mu.getSizeInBytes();

         MsgUnitWrapper[] queueEntries = {
            new MsgUnitWrapper(glob, createMsgUnit(false, 0),  storageId),
            new MsgUnitWrapper(glob, createMsgUnit(false, 0),  storageId),
            new MsgUnitWrapper(glob, createMsgUnit(false, 0),  storageId),
            // Each above entry has 3,311 bytes = 9,922, the next one has 9,932 bytes
            // so when it is entered two of the above need to be swapped away
            // as maxBytes=13,244
            new MsgUnitWrapper(glob, createMsgUnit(false, 2*sizeEmpty-1), storageId),
            new MsgUnitWrapper(glob, createMsgUnit(false, 0),  storageId)};

         final long maxBytesCache = 4*sizeEmpty;
         prop.setMaxBytes(1000000);
         prop.setMaxBytesCache(maxBytesCache);
         assertEquals(ME+": Wrong capacity", 1000000, prop.getMaxBytes());
         assertEquals(ME+": Wrong cache capacity", maxBytesCache, prop.getMaxBytesCache());
         i_map.initialize(storageId, prop);
         assertEquals(ME+": Wrong queue ID", storageId, i_map.getStorageId());

         long numOfBytes = 0;
         for(int i=0; i<queueEntries.length; i++) {
            i_map.put(queueEntries[i]);
            numOfBytes += queueEntries[i].getSizeInBytes();
         }

         assertEquals(ME+": Wrong size", queueEntries.length, i_map.getNumOfEntries());
         assertEquals(ME+": Wrong bytes", numOfBytes, i_map.getNumOfBytes());

         System.out.println("***" + ME + " [SUCCESS]");
         i_map.clear();
         i_map.shutdown();
      }
      catch(XmlBlasterException e) {
         log.severe("Exception thrown: " + e.getMessage());
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
         log.severe("Exception when testing getMsg probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
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
               log.info("#" + i + " id=" + queueEntries[i].getUniqueId() + " numSizeBytes()=" + queueEntries[i].getSizeInBytes());
            }
            log.info("storage bytes sum=" + i_map.getNumOfBytes() + " with persistent bytes=" + i_map.getNumOfPersistentBytes());

            assertEquals("", 3, i_map.getNumOfEntries());
            assertEquals("", 2, i_map.getNumOfPersistentEntries());

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
            assertEquals("", 2, i_map.getNumOfPersistentEntries());

            log.info("storage before remove [0], bytes sum=" + i_map.getNumOfBytes() + " with persistent bytes=" + i_map.getNumOfPersistentBytes());
            i_map.remove(queueEntries[0]); // Remove one
            log.info("storage after remove [0], bytes sum=" + i_map.getNumOfBytes() + " with persistent bytes=" + i_map.getNumOfPersistentBytes());
            ArrayList list = new ArrayList();
            list.add(queueEntries[1]);
            list.add(queueEntries[2]);
            this.checkSizeAndEntries(" getMsg() ", list, i_map);

            for (int ii=0; ii<10; ii++) {
               I_MapEntry result = i_map.get(queueEntries[1].getUniqueId());
               assertTrue("Missing entry", result != null);
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), result.getUniqueId());
            }
            i_map.remove(queueEntries[1].getUniqueId()); // Remove one
            assertEquals("", 1, i_map.getNumOfEntries());
            assertEquals("", 1, i_map.getNumOfPersistentEntries());

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
            assertEquals("", 0, i_map.getNumOfPersistentEntries());
            log.info("#1 Success, get()");
         }

         System.out.println("***" + ME + " [SUCCESS]");
         i_map.clear();
         i_map.shutdown();
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
   }



//------------------------------------
   public void testGetAllMsgs() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");
         queueType = this.currMap.toString();
         StorageId queueId = new StorageId("msgUnitStore", "MapPlugin/getAllMsgs");
         this.currMap.initialize(queueId, prop);
         this.currMap.clear();
         assertEquals(ME + "wrong size before starting ", 0, this.currMap.getNumOfEntries());
         getAllMsgs(this.currMap);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing getAllMsgs probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
      }

   }

   /**
    * Tests get() and get(int num) and remove()
    * NOTE: Currently the MapPlugin returns getAll() sorted (it uses a TreeMap)
    *       But we haven't yet forced this in the I_Map#getAll() Javadoc!
    *       This test assumes sorting order and needs to be changed if we once
    *       decide to specify the exact behaviour in I_Map#getAll() javadoc
    */
   private void getAllMsgs(I_Map i_map) {
      ME = "I_MapTest.getAllMsgs(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: getAll()
         {
            MsgUnitWrapper[] queueEntries = {
                         new MsgUnitWrapper(glob, createMsgUnit(IS_TRANSIENT), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(IS_DURABLE), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(IS_DURABLE), i_map.getStorageId())
                                        };
            for(int i=0; i<queueEntries.length; i++) {
               i_map.put(queueEntries[i]);
               log.info("#" + i + " id=" + queueEntries[i].getUniqueId() + " numSizeBytes()=" + queueEntries[i].getSizeInBytes());
            }
            log.info("storage bytes sum=" + i_map.getNumOfBytes() + " with persistent bytes=" + i_map.getNumOfPersistentBytes());

            assertEquals("", 3, i_map.getNumOfEntries());
            assertEquals("", 2, i_map.getNumOfPersistentEntries());

            for (int ii=0; ii<10; ii++) {
               I_MapEntry[] results = i_map.getAll(null);
               assertEquals("Missing entry", queueEntries.length, results.length);
               assertEquals(ME+": Wrong result", queueEntries[0].getUniqueId(), results[0].getUniqueId());
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), results[1].getUniqueId());
               assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), results[2].getUniqueId());
            }
            assertEquals("", 3, i_map.getNumOfEntries());
            assertEquals("", 2, i_map.getNumOfPersistentEntries());
            /*
            I_MapEntry[] results = i_map.getAll(new I_EntryFilter() {
               public I_Entry intercept(I_Entry entry, I_Storage storage) {
                  assertTrue("NULL storage", storage!=null);
                  if (!storage.isTransient()) return entry;
                  entryCounter++;
                  if (entryCounter == 2)
                     return null;
                  return entry;
               }
            });
            assertEquals("Missing entry", queueEntries.length-1, results.length);
            assertEquals(ME+": Wrong result", queueEntries[0].getUniqueId(), results[0].getUniqueId());
            assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), results[1].getUniqueId());
            */
            i_map.clear();
            log.info("#1 Success, getAll()");
         }

         System.out.println("***" + ME + " [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
      finally {
         try {
            i_map.clear();
            i_map.shutdown();
         }
         catch(XmlBlasterException e) {
            e.printStackTrace();
            fail(ME + ": Exception thrown in cleanup: " + e.getMessage());
         }
      }
   }


//------------------------------------
   public void testGetAllSwappedMsgs() {

      String queueType = "unknown";
      try {
         QueuePropertyBase prop = new MsgUnitStoreProperty(glob, "/node/test");
         queueType = this.currMap.toString();
         StorageId queueId = new StorageId("msgUnitStore", "MapPlugin/getAllSwappedMsgs");
         prop.setMaxEntries(10);      // Overall size (RAM or JDBC or CACHE)
         prop.setMaxEntriesCache(2);  // Is only interpreted for cache implementations (-> the size of the RAM map)
         this.currMap.initialize(queueId, prop);
         this.currMap.clear();
         assertEquals(ME + "wrong size before starting ", 0, this.currMap.getNumOfEntries());
         getAllSwappedMsgs(this.currMap);
      }
      catch (XmlBlasterException ex) {
         log.severe("Exception when testing getAllSwappedMsgs probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
      }

   }

   /**
    * Tests getAll() and the entries are swapped as the RAM size is only 2
    * NOTE: see NOTE of getAllMsgs(I_Map)
    */
   private void getAllSwappedMsgs(I_Map i_map) {
      ME = "I_MapTest.getAllSwappedMsgs(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      
      QueuePropertyBase prop = (QueuePropertyBase)i_map.getProperties();
      assertEquals(ME+": Wrong capacity", 10, prop.getMaxEntries());
      assertEquals(ME+": Wrong cache capacity", 2, prop.getMaxEntriesCache());
      log.info("Current settings: " + prop.toXml());

      try {
         //========== Test 1: getAllSwapped()
         {
            MsgUnitWrapper[] queueEntries = {
                         new MsgUnitWrapper(glob, createMsgUnit(IS_TRANSIENT), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(IS_TRANSIENT), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(IS_TRANSIENT), i_map.getStorageId()),
                         new MsgUnitWrapper(glob, createMsgUnit(IS_TRANSIENT), i_map.getStorageId())
                                        };
            for(int i=0; i<queueEntries.length; i++) {
               i_map.put(queueEntries[i]);
               log.info("#" + i + " id=" + queueEntries[i].getUniqueId() + " numSizeBytes()=" + queueEntries[i].getSizeInBytes());
            }
            //log.info("storage bytes sum=" + i_map.getNumOfBytes() + " with persistent bytes=" + i_map.getNumOfPersistentBytes());
            log.info("storage state=" + i_map.toXml(""));

            assertEquals("", queueEntries.length, i_map.getNumOfEntries());

            for (int ii=0; ii<10; ii++) {
               I_MapEntry[] results = i_map.getAll(null);
               for(int jj=0; jj<results.length; jj++) {
                  log.info("#" + jj + ": " + results[jj].getUniqueId());
               }
               assertEquals("Missing entry", queueEntries.length, results.length);
               assertEquals(ME+": Wrong result", queueEntries[0].getUniqueId(), results[0].getUniqueId());
               assertEquals(ME+": Wrong result", queueEntries[1].getUniqueId(), results[1].getUniqueId());
               assertEquals(ME+": Wrong result", queueEntries[2].getUniqueId(), results[2].getUniqueId());
               assertEquals(ME+": Wrong result", queueEntries[3].getUniqueId(), results[3].getUniqueId());
            }
            assertEquals("", 4, i_map.getNumOfEntries());
            assertEquals("", 0, i_map.getNumOfPersistentEntries());
            log.info("#1 Success, getAllSwapped()");
         }

         System.out.println("***" + ME + " [SUCCESS]");
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         fail(ME + ": Exception thrown: " + e.getMessage());
      }
      finally {
         try {
            i_map.clear();
            i_map.shutdown();
         }
         catch(XmlBlasterException e) {
            e.printStackTrace();
            fail(ME + ": Exception thrown in cleanup: " + e.getMessage());
         }
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
         log.severe("Exception when testing putEntriesTwice probably due to failed initialization of the queue " + queueType + ": " + ex.getMessage());
      }
   }

   private void putEntriesTwice(I_Map i_map) {
      ME = "I_MapTest.putEntriesTwice(" + i_map.getStorageId() + ")[" + i_map.getClass().getName() + "]";
      System.out.println("***" + ME);
      try {
         //========== Test 1: checks if entries are returned in the correct
         // order even if they are inserted in the wrong order
         {
            log.fine("putEntriesTwice test 1");
            int imax = 5;
            long size = 0L;

            MsgUnitWrapper[] entries = new MsgUnitWrapper[imax];
            for (int i=0; i < entries.length; i++) {
               entries[i] = new MsgUnitWrapper(glob, createMsgUnit(false), i_map.getStorageId());
               size += entries[i].getSizeInBytes();
            }

            for(int i=0; i<entries.length; i++) {
               int numPut = i_map.put(entries[i]);
               assertEquals("Putting first entry should be OK", 1, numPut);
               numPut = i_map.put(entries[i]);
               assertEquals("Putting entries twices should fail", 0, numPut);
            }

            assertEquals(ME+": Wrong number of entries after putting same entries twice", entries.length, i_map.getNumOfEntries());
            assertEquals(ME+": Wrong size after putting same entries twice", size, i_map.getNumOfBytes());
            i_map.clear();
            assertEquals(ME+": Wrong num entries after cleaning", i_map.getNumOfEntries(), 0);
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
         log.severe("error when tearing down " + ex.getMessage());
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
      long numOfPersistents = 0;
      long numOfTransients = 0;
      long sizeOfPersistents = 0L;
      for (int i=0; i < queueEntries.length; i++) {
         I_MapEntry entry = queueEntries[i];
         if (entry.isPersistent()) {
            sizeOfPersistents += entry.getSizeInBytes();
            numOfPersistents++;
         }
         else {
            sizeOfTransients += entry.getSizeInBytes();
            numOfTransients++;
         }
      }

      long queueNumOfPersistents = i_map.getNumOfPersistentEntries();
      long queueNumOfTransients = i_map.getNumOfEntries() - queueNumOfPersistents;
      long queueSizeOfPersistents = i_map.getNumOfPersistentBytes();
      long queueSizeOfTransients = i_map.getNumOfBytes() - queueSizeOfPersistents;

      txt += " getNumOfPersistentEntries=" + queueNumOfPersistents + " NumOfTransients=" + queueNumOfTransients; 
      txt += " getNumOfPersistentBytes=" + queueSizeOfPersistents + " SizeOfTransients=" + queueSizeOfTransients;

      assertEquals(ME + ": " + txt + " wrong number of persistents   ", numOfPersistents, queueNumOfPersistents);
      assertEquals(ME + ": " + txt + " wrong number of transients ", numOfTransients, queueNumOfTransients);
      assertEquals(ME + ": " + txt + " wrong size of persistents     ", sizeOfPersistents, queueSizeOfPersistents);
      assertEquals(ME + ": " + txt + " wrong size of transients   ", sizeOfTransients, queueSizeOfTransients);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      ServerScope glob = new ServerScope();
      suite.addTest(new I_MapTest("testByteOverflow", 2)); // For CACHE only
      for (int i=0; i<PLUGIN_TYPES.length; i++) {
         suite.addTest(new I_MapTest("testConfig", i));
         suite.addTest(new I_MapTest("testPutMsg", i));
         suite.addTest(new I_MapTest("testGetMsg", i));
         suite.addTest(new I_MapTest("testGetAllMsgs", i));
         suite.addTest(new I_MapTest("testGetAllSwappedMsgs", i));
         suite.addTest(new I_MapTest("testPutEntriesTwice", i));
      }
      return suite;
   }

   /**
    * <pre>
    *  java -Dtrace=true org.xmlBlaster.test.classtest.msgstore.I_MapTest  > test.log
    * </pre>
    */
   public static void main(String args[]) {

      ServerScope glob = new ServerScope(args);

      I_MapTest testSub = new I_MapTest("I_MapTest", 1); // JDBC check
      //I_MapTest testSub = new I_MapTest("I_MapTest", 2); // CACHE check
      long startTime = System.currentTimeMillis();
      testSub.setUp();
      testSub.testGetAllMsgs();
      testSub.tearDown();
      long usedTime = System.currentTimeMillis() - startTime;
      testSub.log.info("time used for tests: " + usedTime/1000 + " seconds");

      for (int i=0; i < PLUGIN_TYPES.length; i++) {
         testSub = new I_MapTest("I_MapTest", i);

         startTime = System.currentTimeMillis();

         testSub.setUp();
         testSub.testConfig();
         testSub.tearDown();

         testSub.setUp();
         testSub.testPutMsg();
         testSub.tearDown();

         testSub.setUp();
         testSub.testGetMsg();
         testSub.tearDown();

         // already tested outside
         // testSub.setUp();
         // testSub.testGetAllMsgs();
         // testSub.tearDown();

         testSub.setUp();
         testSub.testGetAllSwappedMsgs();
         testSub.tearDown();
         testSub.setUp();
         testSub.testPutEntriesTwice();
         testSub.tearDown();

         usedTime = System.currentTimeMillis() - startTime;
         testSub.log.info("time used for tests: " + usedTime/1000 + " seconds");
      }
   }
}

