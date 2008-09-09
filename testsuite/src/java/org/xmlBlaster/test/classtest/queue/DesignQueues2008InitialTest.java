package org.xmlBlaster.test.classtest.queue;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBMeatFactory;
import org.xmlBlaster.util.queue.jdbc.XBRef;
import org.xmlBlaster.util.queue.jdbc.XBRefFactory;
import org.xmlBlaster.util.queue.jdbc.XBStore;
import org.xmlBlaster.util.queue.jdbc.XBStoreFactory;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Test
 */
public class DesignQueues2008InitialTest extends TestCase {
   
   public class TestInfo extends GlobalInfo {
      
      public TestInfo() {
         super((Set)null);
      }

      protected void doInit(Global glob, PluginInfo plugInfo) throws XmlBlasterException {
         
         Properties props = plugInfo.getParameters();
         
         String size = props.getProperty("connectionPoolSize");
         if (size != null)
            put("db.maxInstances", size);
         // has currently no effect
         String busyTimeout = props.getProperty("connectionBusyTimeout");
         if (busyTimeout != null) {
            put("db.busyToIdleTimeout", busyTimeout);
            put("db.idleToEraseTimeout", busyTimeout);
         }
         String url = props.getProperty("url");
         String user = props.getProperty("user");
         String password = props.getProperty("password");
         if (url != null)
            put("db.url", url);
         if (user != null)
            put("db.user", user);
         if (password != null)
            put("db.password", password);
         
         /*
         this.dbUrl = this.info.get("db.url", "");
         this.dbUser = this.info.get("db.user", "");
         this.dbPasswd = this.info.get("db.password", "");
         int maxInstances = this.info.getInt("db.maxInstances", 10);
         long busyToIdle = this.info.getLong("db.busyToIdleTimeout", 0);
         long idleToErase = this.info.getLong("db.idleToEraseTimeout", 120*60*1000L);
         this.maxResourceExhaustRetries = this.info.getInt("db.maxResourceExhaustRetries", 5);
         this.resourceExhaustSleepGap = this.info.getLong("db.resourceExhaustSleepGap", 1000);
         this.poolManager = new PoolManager("DbPool", this, maxInstances, busyToIdle, idleToErase);
         String createInterceptorClass = info.get("db.createInterceptor.class", null);
         */
      }
      
   }

   private String ME = "DesignQueues2008InitialTest";
   protected Global glob;
   private static Logger log = Logger.getLogger(JdbcManagerCommonTableTest.class.getName());
   private I_DbPool pool;
   private Random random;
   private I_Info info;
   private XBStoreFactory storeFactory;
   private XBMeatFactory meatFactory;
   private XBRefFactory refFactory;
   
   
   public DesignQueues2008InitialTest(Global glob, String name) {
      super(name);
      this.glob = glob;
   }

   protected void setUp() {
      random = new Random();
      //here you should clean up the DB (the three tables)
      String me = ME + ".setUp";
      try {
         // connectionBusyTimeout
         // connectionPoolSize
         QueuePluginManager pluginManager = new QueuePluginManager(glob);
         PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, "JDBC", "1.0");
         info = new TestInfo();
         ((TestInfo)info).init(glob, pluginInfo);
         pool = new DbPool();
         pool.init(info);
         
         // delete tables
         String prefix = "queue.jdbc";
         storeFactory = new XBStoreFactory(prefix);
         meatFactory = new XBMeatFactory(prefix);
         refFactory = new XBRefFactory(prefix);

         Connection conn = pool.reserve();
         conn.setAutoCommit(true);

         storeFactory.init(info);
         meatFactory.init(info);
         refFactory.init(info);
         
         refFactory.drop(conn);
         meatFactory.drop(conn);
         storeFactory.drop(conn);

         storeFactory.create(conn);
         meatFactory.create(conn);
         refFactory.create(conn);
         
         pool.release(conn);
         
      }
      catch (Exception ex) {
         log.severe("exception occured " + ex.toString());
         assertTrue(me, false);
      }
   }

   /**
    * If the size is negative then a random size is choosen between 1 and the abs value specified
    * @param id
    * @param size
    * @return
    */
   private XBMeat createSimpleMeat(long id, int size) {
      XBMeat ret = new XBMeat();
      ret.setId(id);
      
      if (size < 0)
         size = random.nextInt(-size);
      byte[] content = new byte[size];
      for (int i=0; i < content.length; i++)
         content[i] = (byte)i;
      ret.setContent(content);
      ret.setDataType("DUMMYTESTTYPE");
      ret.setDurable(true);
      ret.setFlag1(null);
      ret.setKey("<key></key>\n\n");
      ret.setQos("<qos></qos>");
      ret.setRefCount(0);
      ret.setByteSize(size + 128);
      return ret;
   }

   private XBStore createSimpleStore(long id, String name) {
      XBStore store = new XBStore();
      store.setId(id);
      store.setName(name);
      store.setFlag1("dummyflag");
      return store;
   }
   
   private XBRef createSimpleRef(long id, long meatId, long storeId) {
      XBRef ref = new XBRef();
      ref.setRefId(id);
      ref.setMeatId(meatId);
      ref.setStoreId(storeId);
      ref.setByteSize(100);
      ref.setFlag1("fu,,y");
      ref.setDurable(false);
      ref.setMetaInfo("simple metainfo data");
      ref.setPrio(6);
      return ref;
   }
   
   public void testEncoding() {
      XBMeat meat = new XBMeat();
   }

   /**
    * Tests the performance of many entries (50000 entries).
    * The size of the meat is a normal size of 10 kB. 
    */
   public void testManyEntries() {
      List logs = new ArrayList();
      XBMeat[] meats = null;
      int nmax = 50000;
      try {
         // insert store
         XBStore[] stores = new XBStore[nmax];
         for (int i=0; i < nmax; i++) {
            stores[i] = createSimpleStore((long)i, "queuename" + i);
         }

         long t0 = System.currentTimeMillis();
         for (int i=0; i < stores.length; i++) {
            Connection conn = pool.reserve();
            conn.setAutoCommit(true);
            storeFactory.insert(stores[i], conn, 60);
            pool.release(conn);
         }
         long t1 = System.currentTimeMillis();
         String txt = "XBStore inserts: microseconds per request: " + (1000L*(t1-t0)/nmax) + " (total time " + ((t1-t0)/1000) + " seconds) for '" + nmax + "' requests";
         logs.add(txt);
         log.info(txt); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      
      try {
         // insert meat
         meats = new XBMeat[nmax];
         for (int i=0; i < nmax; i++) {
            meats[i] = createSimpleMeat((long)i, 10240);
         }

         long t0 = System.currentTimeMillis();
         for (int i=0; i < meats.length; i++) {
            Connection conn = pool.reserve();
            conn.setAutoCommit(true);
            meatFactory.insert(meats[i], conn, 60);
            pool.release(conn);
         }
         long t1 = System.currentTimeMillis();
         String txt = "XBMeat inserts: microseconds per request: " + (1000L*(t1-t0)/nmax) + " (total time " + ((t1-t0)/1000) + " seconds) for '" + nmax + "' requests"; 
         logs.add(txt);
         log.info(txt); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      
      try {
         // insert ref
         XBRef[] refs = new XBRef[nmax];
         for (int i=0; i < nmax; i++) {
            refs[i] = createSimpleRef((long)i, (long)i, (long)i);
         }

         long t0 = System.currentTimeMillis();
         for (int i=0; i < refs.length; i++) {
            Connection conn = pool.reserve();
            conn.setAutoCommit(true);
            refFactory.insert(refs[i], conn, 60);
            pool.release(conn);
         }
         long t1 = System.currentTimeMillis();
         String txt = "XBRef inserts: microseconds per request: " + (1000L*(t1-t0)/nmax) + " (total time " + ((t1-t0)/1000) + " seconds) for '" + nmax + "' requests"; 
         logs.add(txt);
         log.info(txt); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }

      // increment the ref counter (optimized way) 
      try {
         long t0 = System.currentTimeMillis();
         for (int i=0; i < meats.length; i++) {
            Connection conn = pool.reserve();
            conn.setAutoCommit(true);
            meatFactory.incrementRefCounter(meats[i].getId(), 1L, conn, 60);
            pool.release(conn);
         }

         long t1 = System.currentTimeMillis();
         String txt = "Increment Ref Counter (optimized): microseconds per request: " + (1000L*(t1-t0)/meats.length) + " (total time " + ((t1-t0)/1000) + " seconds) for '" + meats.length + "' requests"; 
         logs.add(txt);
         log.info(txt); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      
      // increment the ref counter by classical read / increment store 
      try {
         long t0 = System.currentTimeMillis();
         for (int i=0; i < meats.length; i++) {
            Connection conn = pool.reserve();
            conn.setAutoCommit(true);
            XBMeat meat = meatFactory.get(meats[i].getId(), conn, 60);
            meat.setRefCount(meat.getRefCount()+1);
            meatFactory.updateRefCounter(meat, conn, 60);
            pool.release(conn);
         }

         long t1 = System.currentTimeMillis();
         String txt = "Increment Ref Counter (classic): microseconds per request: " + (1000L*(t1-t0)/meats.length) + " (total time " + ((t1-t0)/1000) + " seconds) for '" + meats.length + "' requests"; 
         logs.add(txt);
         log.info(txt); 
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail(ex.getMessage());
      }
      log.info("\n\n --------------------   RESULTS   ---------------------------");
      for (int i=0; i < logs.size(); i++)
         log.info((String)logs.get(i));
      log.info(" --------------------  END RESULTS   ---------------------------\n\n");
   }



   public void tearDown() {
      try {
         if (pool != null)
            pool.shutdown();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue(ex.getMessage(), false);
      }
      pool = null;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
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
      DesignQueues2008InitialTest
         testSub = new DesignQueues2008InitialTest(glob, "DesignQueues2008InitialTest");
      
      testSub.setUp();
      testSub.testManyEntries();
      testSub.tearDown();
/*      
      testSub.setUp();
      testSub.testManyTopics();
      testSub.tearDown();
      
      testSub.setUp();
      testSub.testBigEntries();
      testSub.tearDown();
*/      
   }
}

