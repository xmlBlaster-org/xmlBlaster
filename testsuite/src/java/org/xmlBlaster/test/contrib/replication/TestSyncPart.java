/*------------------------------------------------------------------------------
 Name:      TestSyncPart.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;
import org.xmlBlaster.jms.XBSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
         test.testDateFormat();
         test.tearDown();

         test.setUp();
         test.testTimestampFormat();
         test.tearDown();
         
         test.setUp();
         test.testPerformAllOperationsOnTable();
         test.tearDown();
         
         test.setUp();
         test.testTableWithLongs();
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
      this.pool = DbWatcher.getDbPool(this.info);
      assertNotNull("pool must be instantiated", this.pool);
      boolean forceCreationAndInit = true;
      this.dbSpecific = ReplicationConverter.getDbSpecific(this.info, forceCreationAndInit);
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
         String destination = null;
         boolean forceSend = false;
         TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName));
         tableToWatch.setActions("IDU");
         this.dbSpecific.addTableToWatch(tableToWatch, false, destination, forceSend);
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
      if (this.dbSpecific != null) {
         this.dbSpecific.shutdown();
         this.dbSpecific = null;
      }
      if (this.pool != null) {
         this.pool.shutdown();
         this.pool = null;
      }
   }

   
   /**
    * 
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
               //assertEquals("Testing the content of the replKey", 2, replKey); // 1 is the addition to the repl_tables
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

         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), null, true);
         
         {
            try {
               sql = "INSERT INTO " + this.tableName + " VALUES ('first', 44)";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing insertion into table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               content.read(tmp);
               String contentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "INSERT", dbAction);
               // assertEquals("Testing the content of the replKey", 2+ref, replKey);
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
               // assertEquals("Testing the content of the replKey", 3+ref, replKey);
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
               // long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               // InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               InputStream oldContent = rs.getAsciiStream(10);
               oldContent.read(tmp);
               String oldContentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "DELETE", dbAction);
               // assertEquals("Testing the content of the replKey", 4+ref, replKey);
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
               // long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "ALTER", dbAction);
               // assertEquals("Testing the content of the replKey", 5+ref, replKey);
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
               // long replKey = rs.getLong(1);
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "DROP", dbAction);
               // assertEquals("Testing the content of the replKey", 6+ref, replKey);
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


   /**
    * 
    */
   public final void testTableWithLongs() {
      
      log.info("Start testTableWithLongs");
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         {
            try {
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(20), comments LONG, PRIMARY KEY(name))";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing creation of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "CREATE", dbAction);
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

         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), null, true);
         
         {
            try {
               sql = "INSERT INTO " + this.tableName + " VALUES ('first', 'some very long text here')";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing insertion into table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               InputStream content = rs.getAsciiStream(9);
               // must be null since it contains LONG which must be read individually
               assertTrue("The content must be null since entry contains LONG", content == null);

               String guid = rs.getString(5);
               String dbCatalog = rs.getString(7);
               String dbSchema = rs.getString(8);
               assertTrue("The guid can not be null", guid != null);
               
               String contentTxt = this.dbSpecific.getContentFromGuid(guid, dbCatalog, dbSchema, tableName); 

               assertEquals("Testing the content of the action", "INSERT", dbAction);
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
               sql = "UPDATE " + this.tableName + " SET comments='some very long text here' WHERE name='first'";
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing UPDATE of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               byte[] tmp = new byte[10000];

               String guid = rs.getString(5);
               String dbCatalog = rs.getString(7);
               String dbSchema = rs.getString(8);
               String contentTxt = this.dbSpecific.getContentFromGuid(guid, dbCatalog, dbSchema, tableName);
               
               InputStream oldContent = rs.getAsciiStream(10);
               oldContent.read(tmp);
               String oldContentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "UPDATE", dbAction);
               // assertEquals("Testing the content of the replKey", 5+ref, replKey);
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
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               // InputStream content = rs.getAsciiStream(9);
               byte[] tmp = new byte[10000];
               InputStream oldContent = rs.getAsciiStream(10);
               oldContent.read(tmp);
               String oldContentTxt = new String(tmp);
               assertEquals("Testing the content of the action", "DELETE", dbAction);
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
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               Thread.sleep(500L);
               conn = this.pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
               assertEquals("Testing DROP table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
               String transKey = rs.getString(2);
               String tableName = rs.getString(4);
               String dbAction = rs.getString(6);
               assertEquals("Testing the content of the action", "DROP", dbAction);
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

   
   
   /**
    * 
    */
   public final void testDateFormat() {
      log.info("Start testDifferentFormats");
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         sql = "CREATE TABLE " + this.tableName + " (first DATE, PRIMARY KEY(first))";
         pool.update(sql);

         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), null, true);
         try {
            this.pool.update("DELETE from " + this.replPrefix + "items");
            // long time = System.currentTimeMillis();
            // Date date = new Date(time);
            // String txt = "YYYY-MM-DD HH24:MI:SS"
            String txt = "2005-11-05 23:04:31.345";
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            long time = format.parse(txt).getTime();
            Date date = new Date(time);
            System.out.println("Date: '" + time + "' is '" + date.toString() + "' and again as millis: '" + date.getTime());
            PreparedStatement st1 = conn.prepareStatement("INSERT INTO " + this.tableName + " VALUES (?)");
            st1.setDate(1, date);
            st1.executeUpdate();
            st1.close();
            st1 = null;

            st1 = conn.prepareStatement("SELECT * FROM " + tableName);
            ResultSet rs = st1.executeQuery();
            rs.next();
            Date date1 = rs.getDate(1);
            System.out.println("Date after taking it out from DB: '" + date1.getTime() + "'");
            System.out.println("Date diff: " + (date1.getTime() - date.getTime()));
            rs.close();
            st1.close();
           
            Thread.sleep(500L);
            Statement st2 = conn.createStatement();
            rs = st2.executeQuery("SELECT * from " + this.replPrefix + "items ORDER BY repl_key");
            assertEquals("Testing creation of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
            InputStream content = rs.getAsciiStream(9);
            byte[] tmp = new byte[10000];
            content.read(tmp);
            String val = new String(tmp);
            System.out.println("!!!!!! Result of a date is '" + val + "'");
            this.pool.update("DROP TABLE " + this.tableName);
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
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }
   /**
    * 
    */
   public final void testTimestampFormat() {
      log.info("Start testDifferentFormats");
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         String type = "TIMESTAMP";
         // TODO remove this after testing
         type = "DATE";
         sql = "CREATE TABLE " + this.tableName + " (first " + type + ", PRIMARY KEY(first))";
         pool.update(sql);

         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(this.pool), this.dbHelper.getIdentifier(this.tableName), null, true);
         try {
            this.pool.update("DELETE from " + this.replPrefix + "items");
            // long time = System.currentTimeMillis();
            // Date date = new Date(time);
            // String txt = "YYYY-MM-DD HH24:MI:SS"
            String txt = "2005-11-05 23:04:31";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            txt = "2005-11-05 23:04:31.1";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            txt = "2005-11-05 23:04:31.1000";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            txt = "2005-11-05 23:04:31.1000000";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            txt = "2005-11-05 23:04:31.100000000";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            txt = "2005-11-05 23:04:31.999999999";
            try {
               Timestamp ts = Timestamp.valueOf(txt);
               long millis = ts.getTime();
               long nanos = ts.getNanos();
               System.out.println("'" + txt + "' gave millis='" + millis + "' and nanos='" + nanos + "'");
               
            }
            catch (IllegalArgumentException e) {
               assertTrue("An exception should not occur when parsing '" + txt + "'", false);
            }
            
            DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
            long time = format.parse(txt).getTime();
            Timestamp date = new Timestamp(time);
            date.setNanos(999);
            System.out.println("Date: '" + time + "' is '" + date.toString() + "' and again as millis: '" + date.getTime());
            PreparedStatement st1 = conn.prepareStatement("INSERT INTO " + this.tableName + " VALUES (?)");
            st1.setTimestamp(1, date);
            st1.executeUpdate();
            st1.close();
            st1 = null;
            // Timestamp. timestamp = new Timestamp();
            st1 = conn.prepareStatement("SELECT * FROM " + tableName);
            ResultSet rs = st1.executeQuery();
            rs.next();
            Timestamp date1 = rs.getTimestamp(1);
            System.out.println("Date after taking it out from DB: '" + date1.getTime() + "'");
            System.out.println("Date diff: " + (date1.getTime() - date.getTime()));
            rs.close();
            st1.close();
           
            Thread.sleep(500L);
            Statement st2 = conn.createStatement();
            rs = st2.executeQuery("SELECT * from " + this.replPrefix + "items ORDER BY repl_key");
            assertEquals("Testing creation of table '" + this.tableName + "' checking that the operation generated an entry in " + this.replPrefix + "items", true, rs.next());
            InputStream content = rs.getAsciiStream(9);
            byte[] tmp = new byte[10000];
            content.read(tmp);
            String val = new String(tmp);
            System.out.println("!!!!!! Result of a date is '" + val + "'");
            this.pool.update("DROP TABLE " + this.tableName);
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
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   
   
   
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      return new HashSet();
   }

   public void init(I_Info info) throws Exception {
   }

   public String publish(String changeKey, byte[] message, Map attrMap) throws Exception {
      log.info(new String(message));
      return null;
   }

   public boolean registerAlertListener(I_Update momCb, Map attrs) throws Exception {
      return false;
   }

   public void shutdown() {
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_ChangePublisher#getJmsSession()
    */
   public XBSession getJmsSession() {
      return null;
   }
   
   
}
