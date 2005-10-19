/*------------------------------------------------------------------------------
 Name:      TestSyncPart.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher;
import org.xmlBlaster.contrib.dbwatcher.mom.I_MomCb;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;

import java.util.Map;

/**
 * Tests the synchronous part of the replication, i.e. that an action as CREATE,
 * DROP, ALTER, INSERT, DELETE, UPDATE are written in the repl_items table.
 * Nothing else is tested here.
 * 
 * <p>
 * To run most of the tests you need to have a database (for example Postgres).
 * Does not need xmlBlaster running.
 * </p>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestSyncPart extends XMLTestCase implements I_ChangePublisher {
   private static Logger log = Logger.getLogger(TestSyncPart.class.getName());
   private I_Info info;
   private I_DbPool pool;
   private I_DbSpecific dbSpecific;
   private DbMetaHelper dbHelper;
   private SpecificHelper specificHelper;
   private String tableName = "TEST_SYNCPART";
   private String replPrefix = "repl_";
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestSyncParts
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestSyncParts.class);
      TestSyncPart test = new TestSyncPart();
      try {
         test.setUp();
         test.testPerformAllOperationsOnTable();
         test.tearDown();
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

   /**
    * Default ctor.
    */
   public TestSyncPart() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Constructor for TestSyncParts.
    * 
    * @param arg0
    */
   public TestSyncPart(String arg0) {
      super(arg0);
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      this.specificHelper = new SpecificHelper(System.getProperties());
      this.info = new PropertiesInfo(specificHelper.getProperties());
      this.replPrefix = this.info.get("replication.prefix", "repl_");
      this.info.put(SpecificDefault.NEEDS_PUBLISHER_KEY, "false"); // needed to avoid publishing when reading the table
      this.pool = DbWatcher.getDbPool(this.info, "test");
      assertNotNull("pool must be instantiated", this.pool);
      this.dbSpecific = ReplicationConverter.getDbSpecific(this.info);
      assertNotNull("the dbSpecific shall not be null", dbSpecific);
      Connection conn = this.pool.reserve();
      try {
         this.dbHelper = new DbMetaHelper(this.pool);
         log.info("setUp: going to cleanup now ...");
         this.dbSpecific.cleanup(conn, false);
         log.info("setUp: cleanup done, going to bootstrap now ...");
         try {
            this.pool.update("DROP TABLE " + this.tableName);
         }
         catch (Exception ex) {
         }
         for (int i=1; i < 5; i++) { // make sure we have deleted all triggers
            try {
               this.pool.update("DROP TRIGGER " + this.replPrefix + i);
            }
            catch (Exception ex) {
            }
         }
         boolean doWarn = false;
         boolean force = true;
         this.dbSpecific.bootstrap(conn, doWarn, force);
         boolean doReplicate = true;
         this.dbSpecific.addTableToWatch(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), doReplicate, null);
      }
      catch (Exception ex) {
         log.warning(ex.getMessage());
         if (conn != null)
            this.pool.release(conn);
      }
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
      try {
         this.pool.update("DROP TABLE " + this.tableName);
      }
      catch (Exception ex) {
      }
   }

   
   /**
    */
   public final void testPerformAllOperationsOnTable() {
      
      log.info("Start testPerformAllOperationsOnTable");
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         {
            try {
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(20), age INTEGER, PRIMARY KEY(name))";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing creation of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               // String dbId = rs.getString(3);
               String tableName = rs.getString(4);
               // String guid = rs.getString(5);
               String dbAction = rs.getString(6);
               // String dbCatalog = rs.getString(7);
               // String dbSchema = rs.getString(8);
               // InputStream content = rs.getAsciiStream(9);
               // byte[] tmp = new byte[10000];
               // content.read(tmp);
               // String contentTxt = new String(tmp);
               // InputStream oldContent = rs.getAsciiStream(10);
               // content.read(tmp);
               // String oldContentTxt = new String(tmp);
               // String version = rs.getString(11);
               assertEquals("Testing the content of the action", "CREATE", dbAction);
               assertEquals("Testing the content of the replKey", 2, replKey); // 1 is the addition to the repl_tables
               assertNotNull("Testing that the transaction is not null", transKey);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }

         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), null);
         
         {
            try {
               sql = "INSERT INTO " + this.tableName + " VALUES ('first', 44)";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing insertion into table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               content.read(tmp);
               String contentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "INSERT", dbAction);
               assertEquals("Testing the content of the replKey", 4, replKey);
               assertNotNull("Testing that the transaction is not null", transKey);
               assertNotNull("Testing that the content is not null", contentTxt);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               log.info("'" + sql + "' generates (new) '" + contentTxt + "'");
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }
      
         {
            try {
               sql = "UPDATE " + this.tableName + " SET age=33 WHERE name='first'";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing UPDATE of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               content.read(tmp);
               String contentTxt = new String(tmp);
               InputStream oldContent = rs.getAsciiStream(10);
               oldContent.read(tmp);
               String oldContentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "UPDATE", dbAction);
               assertEquals("Testing the content of the replKey", 5, replKey);
               assertNotNull("Testing that the transaction is not null", transKey);
               assertNotNull("Testing that the content is not null", contentTxt);
               assertNotNull("Testing that the old content is not null", oldContentTxt);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               log.info("'" + sql + "' generates (new) '" + contentTxt + "'");
               log.info("'" + sql + "' generates (old) '" + oldContentTxt + "'");
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'UPDATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }

         {
            try {
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing DELETE of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               // InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               InputStream oldContent = rs.getAsciiStream(10);
               oldContent.read(tmp);
               String oldContentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "DELETE", dbAction);
               assertEquals("Testing the content of the replKey", 6, replKey);
               assertNotNull("Testing that the transaction is not null", transKey);
               assertNotNull("Testing that the old content is not null", oldContentTxt);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               log.info("'" + sql + "' generates (old) '" + oldContentTxt + "'");
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }
      
         {
            try {
               sql = "ALTER TABLE " + this.tableName + " ADD (city VARCHAR(30))";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing ALTER of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "ALTER", dbAction);
               assertEquals("Testing the content of the replKey", 7, replKey);
               assertNotNull("Testing that the transaction is not null", transKey);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'ALTER' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }
         {
            try {
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing DROP table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "DROP", dbAction);
               assertEquals("Testing the content of the replKey", 8, replKey);
               assertNotNull("Testing that the transaction is not null", transKey);
               assertEquals("Testing that the table name is correct", this.dbHelper.getIdentifier(this.tableName), tableName);
               this.pool.update("DELETE from " + this.replPrefix + "items");
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  this.pool.release(conn);
            }
         }
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   public void init(I_Info info) throws Exception {
   }

   public String publish(String changeKey, String message, Map attrMap) throws Exception {
      log.info(message);
      return null;
   }

   public boolean registerAlertListener(I_MomCb momCb) throws Exception {
      return false;
   }

   public void shutdown() {
   }
   
   
}
