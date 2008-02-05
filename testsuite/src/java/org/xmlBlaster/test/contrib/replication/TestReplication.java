/*------------------------------------------------------------------------------
 Name:      TestReplication.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationAgent;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;

/**
 * Test basic functionality for the replication. This test needs an instance of xmlBlaster running.
 * <p>
 * To run most of the tests you need to have a database (for example Postgres).
 * </p>
 * <p>
 * The connection configuration (url, password etc.) is configured as JVM
 * property or in {@link #createTest(I_Info, Map)} and
 * {@link #setUpDbPool(I_Info)}
 * </p>
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class TestReplication extends XMLTestCase {
   private static Logger log = Logger.getLogger(TestReplication.class.getName());

   private I_Info readerInfo;
   private I_Info writerInfo;
   private ReplicationAgent agent;
   private SpecificHelper specificHelper;
   private DbMetaHelper dbHelper;
   private String tableName = "TEST_REPLICATION";
   private String tableName2 = "TEST_REPLICATION2";
   private String replPrefix = "repl_";
   private long sleepDelay;
   private MsgInterceptor interceptor;

   /**
    * Start the test.
    * <pre>
    *  java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestReplication
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestReplication.class);
      TestReplication test = new TestReplication();
      try {
         test.setUp();
         test.testNonAlphaCharOnTable();
         test.tearDown();

         test.setUp();
         test.testUntrimmedSpaces();
         test.tearDown();

         test.setUp();
         test.testNullEntriesOnTable();
         test.tearDown();

         test.setUp();
         test.testCreateAndInsert();
         test.tearDown();

         test.setUp();
         test.testPerformAllOperationsOnTable();
         test.tearDown();

         test.setUp();
         test.testBigBlobs();
         test.tearDown();

         // test.setUp();
         // test.testMultiTransaction();
         // test.tearDown();

      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

   /**
    * Default ctor.
    */
   public TestReplication() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
      this.interceptor = new MsgInterceptor(new Global(), log, null, (I_Update)null);
   }

   /**
    * Constructor for TestReplication.
    *
    * @param arg0
    */
   public TestReplication(String arg0) {
      super(arg0);
      XMLUnit.setIgnoreWhitespace(true);
      this.interceptor = new MsgInterceptor(new Global(), log, null, (I_Update)null);
   }

   /**
    * Helper method to fill the properties. If an entry is found in the system properties it is left as is.
    *
    * @param info
    * @param key
    * @param val
    */
   private void setProp(I_Info info, String key, String val, boolean force) {
      String tmp = info.get(key, null);
      if (tmp == null || force)
         info.put(key, val);
   }


   /**
    * This method is invoked directly by setUp.
    * @param readerInfo The info object for the reader (the dbWatcher).
    * @param writerInfo The info object for the writer (the DbWriter).
    */
   private void setupProperties(I_Info readerInfo, I_Info writerInfo, boolean extraUser) {
      if (readerInfo != null) {
         setProp(readerInfo, "replication.prefix", "repl_", true);
         setProp(readerInfo, "mom.loginName", "DbWatcherPlugin.testPoll/1", true);
         setProp(readerInfo, "mom.topicName", "testRepl", true);
         setProp(readerInfo, "changeDetector.groupColName", "repl_key", true);
         setProp(readerInfo, "alertScheduler.pollInterval", "1000", false);
         setProp(readerInfo, "changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector", false);
         setProp(readerInfo, "changeDetector.detectStatement", "SELECT MAX(repl_key) from " + this.replPrefix + "items", false);
         setProp(readerInfo, "db.queryMeatStatement", "SELECT * FROM " + this.replPrefix + "items ORDER BY repl_key", false);
         // setProp(readerInfo, "changeDetector.postUpdateStatement", "DELETE from " + this.replPrefix + "items", false);
         setProp(readerInfo, "converter.addMeta", "false", false);
         setProp(readerInfo, "converter.class", "org.xmlBlaster.contrib.replication.ReplicationConverter", false);
         setProp(readerInfo, "alertProducer.class", "org.xmlBlaster.contrib.replication.ReplicationScheduler", false);
         setProp(readerInfo, "replication.doBootstrap", "true", false);

         setProp(readerInfo, "xmlBlaster/useSessionMarker", "true", false);
         setProp(readerInfo, "_autoSubscribe", "true", false);
         setProp(readerInfo, "mom.dispatcherPlugin", "ReplManager,1.0", true);
      }
      if (writerInfo != null) {  // and here for the dbWriter ...
         // ---- Database settings -----
         if (extraUser)
            setProp(writerInfo, "mom.loginName", "DbWriterExtra/1", true);
         else
            setProp(writerInfo, "mom.loginName", "DbWriter/1", true);
         // setProp(writerInfo, "replication.mapper.tables", "test_replication=test_replication2,test1=test1_replica,test2=test2_replica,test3=test3_replica", false);

         setProp(writerInfo, "replication.mapper.table.test_replication", "test_replication2", false);
         setProp(writerInfo, "replication.mapper.table.test1", "test1_replica", false);
         setProp(writerInfo, "replication.mapper.table.test2", "test2_replica", false);
         setProp(writerInfo, "replication.mapper.table.test3", "test3_replica", false);
         /*
         String subscribeKey = System.getProperty("mom.subscribeKey", "<key oid='testRepl'/>");
         setProp(writerInfo, "mom.subscribeKey", subscribeKey, true);
         setProp(writerInfo, "mom.subscribeQos", "<qos><initialUpdate>false</initialUpdate><multiSubscribe>false</multiSubscribe><persistent>true</persistent></qos>", false);
         */
         setProp(writerInfo, "dbWriter.writer.class", "org.xmlBlaster.contrib.replication.ReplicationWriter", false);
         // these are pure xmlBlaster specific properties
         setProp(writerInfo, "dispatch/callback/retries", "-1", true);
         setProp(writerInfo, "dispatch/callback/delay", "10000", true);
         setProp(writerInfo, "queue/callback/maxEntries", "10000", true);
         // we need this since we are in testing, otherwise it would loop after the second tearDown
         // because there would be two XmlBlasterAccess having the same session name (and only one session is allowed)
         setProp(writerInfo, "dbWriter.shutdownMom", "true", true);
         setProp(writerInfo, "xmlBlaster/useSessionMarker", "true", false);
         setProp(writerInfo, "mom.dispatcherPlugin", "ReplManager,1.0", true);
         setProp(writerInfo, "replication.recreateTables", "true", true);
      }
   }

   private I_DbSpecific getDbSpecific() {
      assertTrue("The agent must not be null", this.agent != null);
      DbWriter writer = this.agent.getDbWriter();
      assertTrue("The writer must not be null", writer != null);
      I_Info tmpInfo = writer.getInfo();
      assertTrue("The writer info must not be null", tmpInfo != null);

      String dbSpecificClass = tmpInfo.get("replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificOracle");
      I_DbSpecific dbSpecific = (I_DbSpecific)tmpInfo.getObject(dbSpecificClass + ".object");
      assertTrue("The dbSpecific for the writer must not be null", dbSpecific != null);
      return dbSpecific;
   }


   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      this.specificHelper = new SpecificHelper(System.getProperties());
      this.readerInfo = new PropertiesInfo((Properties)this.specificHelper.getProperties().clone());
      this.writerInfo = new PropertiesInfo((Properties)this.specificHelper.getProperties().clone());
      boolean extraUser = false;
      setupProperties(this.readerInfo, this.writerInfo, extraUser);
      extraUser = true; // to make it easy to recognize by the session name

      // we use the writerInfo since this will not instantiate an publisher
      I_Info tmpInfo =  new PropertiesInfo((Properties)this.specificHelper.getProperties().clone());
      setupProperties(null, tmpInfo, extraUser);
      boolean forceCreationAndInit = true;
      I_DbSpecific dbSpecific = ReplicationConverter.getDbSpecific(tmpInfo, forceCreationAndInit);
      I_DbPool pool = (I_DbPool)tmpInfo.getObject("db.pool");

      DbInfo persistentInfo = new DbInfo(pool, "replication", tmpInfo);
      String name = readerInfo.get("replication.prefix", "repl_") + ".oldReplKey";
      persistentInfo.put(name, "0");

      Connection conn = null;
      try {
         this.dbHelper = new DbMetaHelper(pool);
         conn = pool.reserve();
         boolean doWarn = false; // we don't want warnings on SQL Exceptions here.
         log.info("setUp: going to cleanup now ...");
         this.sleepDelay = this.readerInfo.getLong("test.sleepDelay", -1L);
         if (this.sleepDelay < 0L)
            this.sleepDelay = 25000L;
         log.info("setUp: The sleep delay will be '" + this.sleepDelay + "' ms");
         dbSpecific.cleanup(conn, doWarn);
         try {
            pool.update("DROP TABLE " + this.tableName);
         }
         catch (Exception e) {
         }
         try {
            pool.update("DROP TABLE " + this.tableName2);
         }
         catch (Exception e) {
         }
         for (int i=1; i < 5; i++) { // make sure we have deleted all triggers
            try {
               pool.update("DROP TRIGGER " + this.replPrefix + i);
            }
            catch (Exception ex) {
            }
         }
         log.info("setUp: cleanup done, going to bootstrap now ...");
         boolean force = true;
         dbSpecific.bootstrap(conn, doWarn, force);
         dbSpecific.shutdown();
         pool.shutdown();
         pool = null;
         dbSpecific = null;
         tmpInfo = null;
      }
      catch (Exception ex) {
         if (conn != null && pool != null)
            pool.release(conn);
      }

      log.info("setUp: Instantiating");
      this.agent = new ReplicationAgent();
      this.agent.init(this.readerInfo, this.writerInfo);
      this.interceptor.clear();
      this.agent.registerForUpdates(this.interceptor);
      log.info("setUp: terminated");
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
      // here we should also cleanup all resources on the database : TODO
      this.agent.registerForUpdates(null);
      this.agent.shutdown();
      this.agent = null;
   }

   /**
    *
    * If the table does not exist we expect a null ResultSet
    *
    * @throws Exception Any type is possible
    */
   public final void testCreateAndInsert() throws Exception {
      log.info("Start");

      interceptor.waitOnUpdate(sleepDelay, 1);
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         try {
            pool.update("DROP TABLE " + this.tableName + this.specificHelper.getCascade());
         }
         catch (Exception ex) {
            conn = pool.reserve();
         }
         try {
            pool.update("DROP TABLE " + this.tableName2 + this.specificHelper.getCascade()); // This is the replica
         }
         catch (Exception ex) {
            conn = pool.reserve();
         }

         // verifying
         try {
            ResultSet rs = conn.getMetaData().getTables(null, this.specificHelper.getOwnSchema(pool), this.dbHelper.getIdentifier(this.tableName), null);
            assertFalse("Testing if the tables have been cleaned up. The table '" + this.tableName + "' is still here", rs.next()); // should be empty
            rs.close();
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing that the tables have been cleaned up correctly. An exception shall not occur here", false);
         }

         try {
            ResultSet rs = conn.getMetaData().getTables(null, this.specificHelper.getOwnSchema(pool), this.dbHelper.getIdentifier(this.tableName2), null);
            assertFalse("Testing if the tables have been cleaned up. The table '" + tableName2 + "' is still here", rs.next()); // should be empty
            rs.close();
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing that the tables have been cleaned up correctly. An exception shall not occur here", false);
         }

         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }
         this.interceptor.clear();
         String sql = "CREATE TABLE " + tableName + "(name VARCHAR(20), city VARCHAR(15), PRIMARY KEY (name))";
         pool.update(sql);

         sql = "INSERT INTO " + tableName + " (name, city) VALUES ('michele', 'caslano')";
         pool.update(sql);

         /* The DbWatcher shall now detect a table creation and an insert (after maximum two seconds)
          * here the xmlBlaster must run to allow the DbWatcher publish the messages.
          * The DbWriter shall receive the messages it subscribed to and the replica shall be created and filled.
          */

         this.interceptor.waitOnUpdate(this.sleepDelay, 2);
         // a new table must have been created ...
         conn = pool.reserve();
         ResultSet rs = conn.getMetaData().getTables(null, this.specificHelper.getOwnSchema(pool), this.dbHelper.getIdentifier(this.tableName2), null);
         boolean isThere = rs.next();
         log.info("the replicated table is " + (isThere ? "" : "not ") + "there");

         assertTrue("the copy of the table has not been created", isThere);
         rs.close();

         // and a new content must be there ...
         Statement st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM " + this.tableName2);
         isThere = rs.next();
         log.info("the entry in the replicated table is " + (isThere ? "" : "not ") + "there");

         assertTrue("the copy of the table has not been created", isThere);
         rs.close();
         st.close();

      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }


   /**
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void testPerformAllOperationsOnTable() {

      log.info("Start testPerformAllOperationsOnTable");
      interceptor.waitOnUpdate(sleepDelay, 1);
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try {
               this.interceptor.clear();
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(20), age INTEGER, PRIMARY KEY(name))";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "INSERT INTO " + this.tableName + " VALUES ('first', 44)";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 44, age);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "UPDATE " + this.tableName + " SET age=33 WHERE name='first'";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 33, age);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'UPDATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "ALTER TABLE " + this.tableName + " ADD (city VARCHAR(30))";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               // TODO ACTIVATE THIS ONCE ALTER IS IMPLEMENTED ON THE WRITER
               // assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'ALTER' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
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
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void testNullEntriesOnTable() {
      log.info("Start testNullEntriesOnTable");
      interceptor.waitOnUpdate(sleepDelay, 1);
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try { // we explicitly choose a table witout pk (to test searches)
               this.interceptor.clear();
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(20), age INTEGER, address VARCHAR(30))";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 2);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "INSERT INTO " + this.tableName + " (name, address) VALUES ('first', 'SOMEWHERE')";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               Object age = rs.getObject(2);
               String addr = rs.getString(3);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertNull("Testing '" + sql + "' for the age of the entry", age);
               assertEquals("Testing '" + sql + "' for the address of the entry", "SOMEWHERE", addr);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "UPDATE " + this.tableName + " SET age=33,address=NULL WHERE name='first'";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               String address = rs.getString(3);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 33, age);
               assertNull("Testing '" + sql + "' for the address of the entry", address);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'UPDATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
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
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void testUntrimmedSpaces() {
      log.info("Start testUntrimmedSpaces");
      interceptor.waitOnUpdate(sleepDelay, 1);
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try { // we explicitly choose a table without pk (to test searches)
               interceptor.waitOnUpdate(sleepDelay, 1);
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(30), age INTEGER, address VARCHAR(30))";
               pool.update(sql);

               // Thread.sleep(this.sleepDelay);
               this.interceptor.waitOnUpdate(this.sleepDelay, 2);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  String sqlStat = "SELECT * from " + this.tableName2;
                  rs = st.executeQuery(sqlStat);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "INSERT INTO " + this.tableName + " (name, address) VALUES (' firstOne ', ' SOMEWHERE ')";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               Object age = rs.getObject(2);
               String addr = rs.getString(3);
               assertEquals("Testing '" + sql + "' for the name of the entry", " firstOne ", name);
               assertNull("Testing '" + sql + "' for the age of the entry", age);
               assertEquals("Testing '" + sql + "' for the address of the entry", " SOMEWHERE ", addr);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "UPDATE " + this.tableName + " SET age=33,address=NULL WHERE name=' firstOne '";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               String address = rs.getString(3);
               assertEquals("Testing '" + sql + "' for the name of the entry", " firstOne ", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 33, age);
               assertNull("Testing '" + sql + "' for the address of the entry", address);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'UPDATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
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
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void testNonAlphaCharOnTable() {
      log.info("Start testNonAlphaCharOnTable");
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try { // we explicitly choose a table without pk (to test searches)
               interceptor.clear();
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(30), age INTEGER, address VARCHAR(30))";
               pool.update(sql);

               // Thread.sleep(this.sleepDelay);
               this.interceptor.waitOnUpdate(this.sleepDelay, 2);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               StringBuffer buf = new StringBuffer();
               buf.append((char)12);
               buf.append((char)13);
               String txt = buf.toString();
               sql = "INSERT INTO " + this.tableName + " (name, address) VALUES ('firstOne', '" + txt + "')";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               Object age = rs.getObject(2);
               String addr = rs.getString(3);
               assertEquals("Testing '" + sql + "' for the name of the entry", "firstOne", name);
               assertNull("Testing '" + sql + "' for the age of the entry", age);
               assertEquals("Testing '" + sql + "' for the address of the entry", txt, addr);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 3, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   private class Pusher extends Thread {

      private int sweeps;
      private byte[] blob;
      private OutputStream os;
      public Pusher(int sweeps, byte[] blob, OutputStream os) {
         this.sweeps = sweeps;
         this.blob = blob;
         this.os = os;
      }

      public void run() {
         try {
            Random random = new Random();
            for (int i=0; i < sweeps; i++) {
               random.nextBytes(blob);
               os.write(blob);
            }
            os.close();
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }
   }

   /**
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void testBigBlobs() {
      log.info("Start testBigBlobs");
      interceptor.waitOnUpdate(sleepDelay, 1);
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try { // we explicitly choose a table without pk (to test searches)
               this.interceptor.clear();
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(30), data BLOB)";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               conn = pool.reserve();
               conn.setAutoCommit(true);
               sql = "INSERT INTO " + this.tableName + " (name, data) VALUES (?, ?)";
               PreparedStatement ps = conn.prepareStatement(sql);

               ps.setString(1, "someName");
               byte[] blob = new byte[4096];
               int sweeps = 10240; // 40 MB

               int length = blob.length * sweeps;
               PipedInputStream pi = new PipedInputStream();
               PipedOutputStream po = new PipedOutputStream(pi);
               ps.setBinaryStream(2, pi, length);

               Pusher pusher = new Pusher(sweeps, blob, po);
               pusher.start();
               ps.execute();
               ps.close();
               pusher.join();
               ps = null;

               this.interceptor.waitOnUpdate(1000*this.sleepDelay, 1);

               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  rs.next();
                  String name = rs.getString(1);
                  assertEquals("comparing the name", "someName", name);

                  InputStream is = rs.getBinaryStream(2);
                  byte[] buf = new byte[1000];
                  int val;
                  int sum = 0;
                  while ( (val=is.read(buf)) > -1) {
                     sum += val;
                  }
                  is.close();
                  assertEquals("Comparing the size of the blob: ", length, sum);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
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
    * Tests the same operations as already tested in TestSyncPart but with the complete Replication.
    *
    */
   public final void probeMultithreading() {

      log.info("Start testMultithreading");
      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         try {
            boolean force = false;
            String destination = null;
            boolean forceSend = false;
            TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
            tableToWatch.setActions("IDU");
            getDbSpecific().addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("Testing if addition of table '" + tableName + "' to tables to replicate (" + this.replPrefix + "tables) succeeded: An exception should not occur here", false);
         }

         {
            try {
               this.interceptor.clear();
               sql = "CREATE TABLE " + this.tableName + " (name VARCHAR(20), age INTEGER, PRIMARY KEY(name))";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               conn.setAutoCommit(true);
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'CREATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {

            Connection conn1 = null;
            Connection conn2 = null;
            Statement st1 = null;
            Statement st2 = null;
            try {
               conn1 = pool.reserve();
               conn2 = pool.reserve();
               //conn1.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
               //conn2.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
               this.interceptor.clear();
               conn1.clearWarnings();
               conn2.clearWarnings();
               conn1.setAutoCommit(false);
               conn2.setAutoCommit(false);
               st1 = conn1.createStatement();
               st2 = conn2.createStatement();

               sql = "INSERT INTO " + this.tableName + " VALUES ('first', 1)";
               pool.update(sql);
               sql = "UPDATE " + this.tableName + " SET age=2 WHERE name='first'";
               st1.executeUpdate(sql);
               sql = "UPDATE " + this.tableName + " SET age=3 WHERE name='first'";
               st2.executeUpdate(sql);
               sql = "UPDATE " + this.tableName + " SET age=4 WHERE name='first'";
               st2.executeUpdate(sql);
               conn2.commit();
               conn2.setAutoCommit(false);
               st2.close();

               conn1.commit();
               conn1.setAutoCommit(false);
               st1.close();

               // Should be 2 since conn1 commits last
               this.interceptor.waitOnUpdate(this.sleepDelay, 2);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 2, age);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'INSERT' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
               if (conn1 != null)
                  conn1.close();
               if (conn2 != null)
                  conn2.close();
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "UPDATE " + this.tableName + " SET age=33 WHERE name='first'";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must not be empty", true, rs.next());
               String name = rs.getString(1);
               int age = rs.getInt(2);
               assertEquals("Testing '" + sql + "' for the name of the entry", "first", name);
               assertEquals("Testing '" + sql + "' for the age of the entry", 33, age);
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'UPDATE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "DELETE FROM " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DELETE' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }

         {
            try {
               this.interceptor.clear();
               sql = "ALTER TABLE " + this.tableName + " ADD (city VARCHAR(30))";
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
               }
               catch (Exception e) {
                  e.printStackTrace();
                  assertTrue("Testing '" + sql + "'. It resulted in an exception " + e.getMessage(), false);
               }
               // TODO ACTIVATE THIS ONCE ALTER IS IMPLEMENTED ON THE WRITER
               // assertEquals("Testing '" + sql + "' the number of columns returned", 2, rs.getMetaData().getColumnCount());
               assertEquals("Testing '" + sql + "' the table must be empty", false, rs.next());
               rs.close();
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'ALTER' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
         {
            try {
               this.interceptor.clear();
               sql = "DROP TABLE " + this.tableName;
               pool.update(sql);
               this.interceptor.waitOnUpdate(this.sleepDelay, 1);
               conn = pool.reserve();
               Statement st = conn.createStatement();
               ResultSet rs = null;
               try {
                  rs = st.executeQuery("SELECT * from " + this.tableName2);
                  assertTrue("Testing '" + sql + "'. It must have resulted in an exception but did not.", false);
               }
               catch (Exception e) {
               }
               finally {
                  if (rs != null)
                     rs.close();
                  rs = null;
               }
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("Exception when testing operation 'DROP' should not have happened: " + e.getMessage(), false);
            }
            finally {
               if (conn != null)
                  pool.release(conn);
            }
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }


}
