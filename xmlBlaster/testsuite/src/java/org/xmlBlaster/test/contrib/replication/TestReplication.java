/*------------------------------------------------------------------------------
 Name:      TestReplication.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationAgent;
import org.xmlBlaster.contrib.replication.ReplicationConverter;

/**
 * Test basic functionality.
 * <p>
 * To run most of the tests you need to have a database (for example Postgres).
 * </p>
 * <p>
 * The connection configuration (url, password etc.) is configured as JVM
 * property or in {@link #createTest(I_Info, Map)} and
 * {@link #setUpDbPool(I_Info)}
 * </p>
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestReplication extends XMLTestCase {
   private static Logger log = Logger.getLogger(TestReplication.class.getName());

   private I_Info readerInfo;
   private I_Info writerInfo;
   
   private ReplicationAgent agent;
   
   // private DbWatcher dbWatcher;
   // private DbWriter dbWriter;
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestReplication
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestReplication.class);
      TestReplication test = new TestReplication();
      try {
         test.setUp();
         test.testCreateAndInsert();
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
   public TestReplication() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Constructor for TestReplication.
    * 
    * @param arg0
    */
   public TestReplication(String arg0) {
      super(arg0);
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Helper method to fill the properties. If an entry is found in the system properties it is left as is.
    * 
    * @param info
    * @param key
    * @param val
    */
   private void setProp(I_Info info, String key, String val) {
      String tmp = info.get(key, null);
      if (tmp == null)
         info.put(key, val);
   }

   
   private void setupProperties(I_Info readerInfo, I_Info writerInfo) {
      // we hardcode the first ...
      readerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
                               "oracle.jdbc.driver.OracleDriver:" +
                               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
                               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
                               "org.postgresql.Driver");
      setProp(readerInfo, "db.url", "jdbc:postgresql:test//localhost");
      setProp(readerInfo, "db.user", "postgres");
      setProp(readerInfo, "db.password", "");
      setProp(readerInfo, "mom.loginName", "DbWatcherPlugin.testPoll/1");
      setProp(readerInfo, "mom.topicName", "trans_stamp");
      setProp(readerInfo, "alertScheduler.pollInterval", "2000");
      setProp(readerInfo, "changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
      setProp(readerInfo, "changeDetector.detectStatement", "SELECT MAX(repl_key) from repl_items");
      setProp(readerInfo, "db.queryMeatStatement", "SELECT * FROM repl_items ORDER BY repl_key");
      setProp(readerInfo, "changeDetector.postUpdateStatement", "DELETE from repl_items");
      setProp(readerInfo, "converter.addMeta", "false");
      setProp(readerInfo, "converter.class", "org.xmlBlaster.contrib.replication.ReplicationConverter");
      setProp(readerInfo, "alertProducer.class", "org.xmlBlaster.contrib.replication.ReplicationScheduler");
      setProp(readerInfo, "replication.doBootstrap", "true");
      
      // and here for the dbWriter ...
      // ---- Database settings -----
      writerInfo.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
            "oracle.jdbc.driver.OracleDriver:" +
            "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
            "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
            "org.postgresql.Driver");
      setProp(writerInfo, "db.url", "jdbc:postgresql:test//localhost");
      setProp(writerInfo, "db.user", "postgres");
      setProp(writerInfo, "db.password", "");
      setProp(writerInfo, "mom.loginName", "DbWriter/1");
      setProp(writerInfo, "replication.mapper.tables", "test_replication=test_replication2,test1=test1_replica,test2=test2_replica,test3=test3_replica");

      String subscribeKey = System.getProperty("mom.subscribeKey", "<key oid='trans_stamp'/>");
      setProp(writerInfo, "mom.subscribeKey", subscribeKey);
      setProp(writerInfo, "mom.subscribeQos", "<qos><initialUpdate>false</initialUpdate><multiSubscribe>false</multiSubscribe><persistent>true</persistent></qos>");
      setProp(writerInfo, "dbWriter.writer.class", "org.xmlBlaster.contrib.replication.ReplicationWriter");
      // these are pure xmlBlaster specific properties
      setProp(writerInfo, "dispatch/callback/retries", "-1");
      setProp(writerInfo, "dispatch/callback/delay", "10000");
      setProp(writerInfo, "queue/callback/maxEntries", "10000");
      
      /**
       * Complete set of properties ...
       * 
       * alertProducer.class
       * changeDetector.MINSTR
       * changeDetector.class
       * changeDetector.detectStatement
       * changeDetector.groupColName
       * changeDetector.postUpdateStatement
       * charSet
       * converter.class
       * converter.rootName
       * db.password
       * db.pool.owner
       * db.queryMeatStatement
       * db.url
       * db.user
       * dbPool.class
       * jdbc.drivers
       * mom.alertSubscribeKey
       * mom.alertSubscribeQos
       * mom.class
       * mom.connectQos
       * mom.loginName
       * mom.password
       * mom.publishKey
       * mom.publishQos
       * mom.publisher.owner
       * mom.subscriptions
       * mom.topicName
       * parser.class
       * replication.bootstrapFile
       * replication.cleanupFile
       * replication.dbSpecific.class
       * replication.doBootstrap
       * replication.mapper.class
       * replication.mapper.tables
       * replication.overwriteTables
       * dbWriter.writer.class
       * transformer.class
       * 
       */
      
   }

   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      this.readerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      this.writerInfo = new PropertiesInfo(new Properties(System.getProperties()));
      setupProperties(this.readerInfo, this.writerInfo);

      // we use the writerInfo since this will not instantiate an publisher
      I_DbSpecific dbSpecific = ReplicationConverter.getDbSpecific(this.writerInfo);
      I_DbPool pool = (I_DbPool)this.writerInfo.getObject("db.pool");
      Connection conn = null;
      try {
         conn = pool.reserve();
         log.info("setUp: going to cleanup now ...");
         dbSpecific.cleanup(conn);
         log.info("setUp: cleanup done, going to bootstrap now ...");
         dbSpecific.bootstrap(conn);
         dbSpecific.shutdown();
         dbSpecific = null;
      }
      catch (Exception ex) {
         if (conn != null)
            pool.release(conn);
      }
      
      log.info("setUp: Instantiating");
      this.agent = new ReplicationAgent();
      this.agent.init(this.readerInfo, this.writerInfo);
      log.info("setUp: terminated");
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
      // here we should also cleanup all resources on the database : TODO
      this.agent.shutdown();
      this.agent = null;
   }

   /**
    * If the table does not exist we expect a null ResultSet
    * 
    * @throws Exception
    *            Any type is possible
    */
   public final void testCreateAndInsert() throws Exception {
      log.info("Start");

      I_DbPool pool = (I_DbPool)this.readerInfo.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      I_DbSpecific dbSpecific = ReplicationConverter.getDbSpecific(this.readerInfo);
      assertNotNull("the dbSpecific shall not be null", dbSpecific);
      try {
         conn  = pool.reserve();
         dbSpecific.bootstrap(conn);
         pool.release(conn);
      
         String tableName = "test_replication";
         // cleaning up
         try {
            pool.update("DROP TABLE " + tableName + " CASCADE");
         }
         catch (Exception ex) {
            conn = pool.reserve();
         }
         try {
            pool.update("DROP TABLE " + tableName + "2 CASCADE"); // This is the replica
         }
         catch (Exception ex) {
            conn = pool.reserve();
         }

         // verifying
         try {
            ResultSet rs = conn.getMetaData().getTables(null, null, tableName + "2", null);
            assertFalse("the table '" + tableName + "2' has not been cleaned up correctly", rs.next()); // should be empty
            rs.close();
         }
         catch (Exception ex) {
            assertTrue("an exception shall not occur here", false);
         }
         
         try {
            pool.update("INSERT INTO repl_tables VALUES ('" + tableName + "','t')");
         }
         catch (Exception ex) {
            assertTrue("an exception should not occur here", false);
         }
         String sql = "CREATE TABLE " + tableName + "(name VARCHAR(20), age INT, PRIMARY KEY (name))";
         pool.update(sql);
         
         sql = "INSERT INTO " + tableName + " (name, age) VALUES ('michele', 80)";
         pool.update(sql);

         // we artificially trigger the initial filling of the repl_items table (not necessary anymore since handled by alerter) 
         // dbSpecific.forceTableChangeCheck();
         
         /* The DbWatcher shall now detect a table creation and an insert (after maximum two seconds)
          * here the xmlBlaster must run to allow the DbWatcher publish the messages.
          * The DbWriter shall receive the messages it subscribed to and the replica shall be created and filled.
          */

         Thread.sleep(3000L);
         // a new table must have been created ...
         conn = pool.reserve();
         ResultSet rs = conn.getMetaData().getTables(null, null, tableName + "2", null);
         boolean isThere = rs.next();
         log.info("the replicated table is " + (isThere ? "" : "not ") + "there");

         assertTrue("the copy of the table has not been created", isThere);
         rs.close();
         
         // and a new content must be there ...
         Statement st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM " + tableName + "2");
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

}
