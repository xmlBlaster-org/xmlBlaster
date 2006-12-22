/*------------------------------------------------------------------------------
 Name:      TestDbBasics.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.io.ByteArrayInputStream;
import java.sql.CallableStatement;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
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
import org.xmlBlaster.jms.XBSession;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test basic functionality of the database. It does need a database conntected
 * but does not need any xmlBlaster running.
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
public class TestDbBasics extends XMLTestCase implements I_ChangePublisher {
   private static Logger log = Logger.getLogger(TestDbBasics.class.getName());
   private I_Info info;
   private I_DbPool pool;
   private I_DbSpecific dbSpecific;
   private DbMetaHelper dbHelper;
   private SpecificHelper specificHelper;
   private String replPrefix = "repl_";
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestDbBasics
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestDbBasics.class);
      TestDbBasics test = new TestDbBasics();
      try {
         test.setUp();
         test.testBasicPerformance();
         test.tearDown();

         test.setUp();
         test.testInternalFunctions();
         test.tearDown();

         test.setUp();
         test.testFunctions();
         test.tearDown();
         
         test.setUp();
         test.testChangesToReplTables();
         test.tearDown();
         
         test.setUp();
         test.testCreateThenAddToReplTables();
         test.tearDown();

         test.setUp();
         test.testAddToReplTablesThenCreate();
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
   public TestDbBasics() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Constructor for TestDbBasics.
    * 
    * @param arg0
    */
   public TestDbBasics(String arg0) {
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
      this.pool = DbWatcher.getDbPool(this.info);
      assertNotNull("pool must be instantiated", this.pool);
      boolean forceCreationAndInit = true;
      this.dbSpecific = ReplicationConverter.getDbSpecific(this.info, forceCreationAndInit);
      assertNotNull("the dbSpecific shall not be null", dbSpecific);
      Connection conn = this.pool.reserve();
      try {
         this.dbHelper = new DbMetaHelper(this.pool);
         log.info("setUp: going to cleanup now ...");
         conn.setAutoCommit(true);
         this.dbSpecific.cleanup(conn, false);
         for (int i=1; i < 5; i++) { // make sure we have deleted all triggers
            try {
               this.pool.update("DROP TRIGGER " + this.replPrefix + i);
            }
            catch (Exception ex) {
            }
         }
         log.info("setUp: cleanup done, going to bootstrap now ...");
         boolean doWarn = false;
         boolean force = true;
         this.dbSpecific.bootstrap(conn, doWarn, force);
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
      
      Connection conn = this.pool.reserve();
      try {
         this.dbSpecific.cleanup(conn, false);
      }
      catch (Exception ex) {
         log.warning(ex.getMessage());
         if (conn != null)
            this.pool.release(conn);
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
    */
   public final void testBasicPerformance() throws Exception {
      String txt = "testBasicPerformance";
      log.info("Start " + txt);
      
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      try {
         pool.update("DROP TABLE PERFORM");
      }
      catch (SQLException ex) {
         log.info("An Exception here is allowed");
      }
      
      pool.update("CREATE TABLE PERFORM (name1 VARCHAR(20), name2 VARCHAR(128), name3 BLOB, primary key (name1))"); 

      Connection conn = pool.reserve();
      conn.setAutoCommit(false);
      String sql = "INSERT INTO PERFORM VALUES (?, ?, ?)";
      byte[] blob = new byte[1024];
      for (int i=0; i < blob.length; i++)
         blob[i] = (byte)i;
      
      int nmax = 50;
      {
         long t0 = System.currentTimeMillis();
         PreparedStatement st = conn.prepareStatement(sql);
         ByteArrayInputStream bais = new ByteArrayInputStream(blob);
         for (int i=0; i < nmax; i++) {
            st.setString(1, "name01_" + i);
            st.setString(2, "name02_" + i);
            st.setBinaryStream(3, bais, blob.length);
            st.addBatch();
         }
         st.executeBatch();
         conn.commit();
         long t1 = System.currentTimeMillis();
         long dt = t1-t0;
         log.info("batch statements='" + nmax + "' took '" + dt + "' ms (per statement: " + dt/nmax + ")");
         pool.update("delete from PERFORM");
         conn.commit();
      }
      {
         long t0 = System.currentTimeMillis();
         for (int i=0; i < nmax; i++) {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            PreparedStatement st = conn.prepareStatement(sql);
            st.setString(1, "name01_" + i);
            st.setString(2, "name02_" + i + "_hhjdhsdsdjsdkljsdjsdljljsdljsdkljsljsdsdsdsd");
            st.setBinaryStream(3, bais, blob.length);
            st.execute();
         }
         conn.commit();
         long t1 = System.currentTimeMillis();
         long dt = t1-t0;
         log.info("non-batch (single commit) statements='" + nmax + "' took '" + dt + "' ms (per statement: " + dt/nmax + ")");
         pool.update("delete from PERFORM");
         conn.commit();
      }
      {
         long t0 = System.currentTimeMillis();
         for (int i=0; i < nmax; i++) {
            ByteArrayInputStream bais = new ByteArrayInputStream(blob);
            PreparedStatement st = conn.prepareStatement(sql);
            st.setString(1, "name01_" + i);
            st.setString(2, "name02_" + i + "_hhjdhsdsdjsdkljsdjsdljljsdljsdkljsljsdsdsdsd");
            st.setBinaryStream(3, bais, blob.length);
            st.execute();
            conn.commit();
         }
         long t1 = System.currentTimeMillis();
         long dt = t1-t0;
         log.info("non-batch (all commit) statements='" + nmax + "' took '" + dt + "' ms (per statement: " + dt/nmax + ")");
         pool.update("delete from PERFORM");
         conn.commit();
      }

      pool.update("DROP TABLE PERFORM");
      pool.release(conn);
      log.info("SUCCESS");
   }

   /**
    * This method makes some calls to system functions which are specific to oracle.
    * 
    * The tested functions are:
    * CHAR(8) repl_base64_helper(val INTEGER) (this is only tested for no exception thrown)
    * CLOB repl_base64_enc_raw(msg RAW)
    * CLOB repl_base64_enc_blob(msg BLOB)
    * CLOB repl_base64_enc_clob(msg CLOB)
    * 
    * @throws Exception Any type is possible
    */
   public final void testInternalFunctions() throws Exception {
      
      log.info("Start testInternalFunctions");
      if (!this.specificHelper.isOracle()) {
         log.info("Stop testInternalFunctions (nothing to be done since not oracle)");
         return;
      }
      
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;

         { // test the test methods themselves first
             sql = "{? = call " + this.replPrefix + "test_blob(?,?,?,?)}";
             try {
                CallableStatement st = conn.prepareCall(sql);
                st.setString(2, "TEST");
                String tmp = new String("Haloooooo");
                st.setBytes(3, tmp.getBytes());
                st.setString(4, "name");
                st.setLong(5, 1L);
                st.registerOutParameter(1, Types.CLOB);
                ResultSet rs = st.executeQuery();
                Clob clob = st.getClob(1);
                long len = clob.length();
                byte[] buf = new byte[(int)len];
                clob.getAsciiStream().read(buf);
                rs.close();
                st.close();
                log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
                assertEquals("", "TEST", new String(buf));
             }
             catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
                assertTrue("an exception should not occur when testing '" + sql + "'", false);
             }
          }
         { // test the test methods themselves first
             sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}";
             try {
                CallableStatement st = conn.prepareCall(sql);
                st.setString(2, "TEST");
                String tmp = new String("Haloooooo");
                st.setString(3, tmp);
                st.setString(4, "name");
                st.setLong(5, 1L);
                st.registerOutParameter(1, Types.CLOB);
                ResultSet rs = st.executeQuery();
                Clob clob = st.getClob(1);
                long len = clob.length();
                byte[] buf = new byte[(int)len];
                clob.getAsciiStream().read(buf);
                rs.close();
                st.close();
                log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
                assertEquals("", "TEST", new String(buf));
             }
             catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
                assertTrue("an exception should not occur when testing '" + sql + "'", false);
             }
         }
         {
            sql = "{? = call " + this.replPrefix + "base64_helper(?, ?)}";
            try {
               CallableStatement st = conn.prepareCall(sql);
               st.setInt(2, 2);
               st.setLong(3, 1000L);
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               // no assert here, just testing for exceptions 
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         {
            sql = "{? = call " + this.replPrefix + "base64_enc_raw_t(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               
               // int nmax = 256;
               int nmax = 2000;
               byte[] in = new byte[nmax];
               for (int i=0; i < nmax; i++) {
                  in[i] = (byte)i;
               }
               st.setBytes(2, in);
               // st.registerOutParameter(1, Types.VARCHAR); // worked for oracle 10
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               // String ret = st.getString(1);
               Clob clob = st.getClob(1);
               long len = clob.length();
               byte[] buf = new byte[(int)len];
               clob.getAsciiStream().read(buf);
               rs.close();
               st.close();
               byte[] out = Base64.decodeBase64(buf);
               log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
               assertEquals("wrong number of return values ", in.length, out.length);
               for (int i=0; i < in.length; i++) {
                  assertEquals("entry '" + i + "' is wrong: ", in[i], out[i]);
               }
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         {
            sql = "{? = call " + this.replPrefix + "base64_enc_vch_t(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);

               String test = "this is a simple base64 encoding test for clobs";
               st.setString(2, test);
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               // String ret = st.getString(1);
               Clob clob = st.getClob(1);
               long len = clob.length();
               byte[] buf = new byte[(int)len];
               clob.getAsciiStream().read(buf);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
               String out = new String(Base64.decodeBase64(buf));
               assertEquals("invocation '" + sql + "' gave the wrong result ", test, out);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         
         {
        	 // base64_enc_blob(?)
             sql = "{? = call " + this.replPrefix + "test_blob(?,?,?,?)}"; // name text, content text)
             try {
                CallableStatement st = conn.prepareCall(sql);
                int nmax = 2000;
                byte[] in = new byte[nmax];
                for (int i=0; i < nmax; i++) {
                   in[i] = (byte)i;
                }
                st.setString(2, "BASE64_ENC_BLOB");
                st.setBytes(3, in);
                st.setString(4, "unused");
                st.setLong(5, 1L); // loop only once
                st.registerOutParameter(1, Types.CLOB);
                ResultSet rs = st.executeQuery();
                Clob clob = st.getClob(1);
                long len = clob.length();
                byte[] buf = new byte[(int)len];
                clob.getAsciiStream().read(buf);
                rs.close();
                st.close();
                log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
                
                byte[] out = Base64.decodeBase64(buf);
                assertEquals("wrong number of return values ", in.length, out.length);
                for (int i=0; i < in.length; i++) {
                   assertEquals("entry '" + i + "' is wrong: ", in[i], out[i]);
                }
             }
             catch (SQLException sqlEx) {
                sqlEx.printStackTrace();
                assertTrue("an exception should not occur when testing '" + sql + "'", false);
             }
          }
         
/*
         {
            sql = "{? = call " + this.replPrefix + "base64_enc_blob(?)}"; // name text, content text)
            try {
               // conn.setAutoCommit(false); // needed since reusing lob objects
               // first retreiving the LOB object
               // String tmpSql = "{? = call " + this.replPrefix + "create_blob()}"; // name text, content text)
               // CallableStatement tmpSt = conn.prepareCall(tmpSql);
               // tmpSt.registerOutParameter(1, Types.BLOB);
               // ResultSet tmpRs = tmpSt.executeQuery();
               // Blob blob = tmpSt.getBlob(1);
               CallableStatement st = conn.prepareCall(sql);
               int nmax = 32000;
               byte[] in = new byte[nmax];
               for (int i=0; i < nmax; i++) {
                  in[i] = (byte)i;
               }
               // ByteArray works for ora10
               // ByteArrayInputStream bais = new ByteArrayInputStream(in);
               // st.setBinaryStream(2, bais, in.length);
               BLOB blob = BLOB.createTemporary(conn, true, BLOB.MODE_READWRITE);
               blob.open(BLOB.MODE_READWRITE);
               // The following did not work for 8.1.6. To make it 
               // work it needed the old driver and the next line code
               // which in the new driver is deprecated.
               // OutputStream os = blob.setBinaryStream(1);
               
               // this raises an AbstractMethodError with both old and new driver
               // OutputStream os = ((java.sql.Blob)blob).setBinaryStream(1L);
               
               // This works with old driver on 8.1.6 (but oracle specific)
               // OutputStream os = blob.getBinaryOutputStream();
               // os.write(in);
               // os.close();

               // this raises an AbstractMethodError too in oracle with old and new driver
               // ((java.sql.Blob)blob).setBytes(1, in);
               ((java.sql.Blob)blob).setBytes(1, in, 0, in.length);
               st.setBlob(2, blob);
               
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               // String ret = st.getString(1);
               Clob clob = st.getClob(1);
               long len = clob.length();
               byte[] buf = new byte[(int)len];
               clob.getAsciiStream().read(buf);
               // tmpRs.close();
               // tmpSt.close();
               rs.close();
               st.close();
               conn.setAutoCommit(true);
               log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
               
               byte[] out = Base64.decodeBase64(buf);
               assertEquals("wrong number of return values ", in.length, out.length);
               for (int i=0; i < in.length; i++) {
                  assertEquals("entry '" + i + "' is wrong: ", in[i], out[i]);
               }
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
 */     
         {
            // base64_enc_clob(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);

               String test = "this is a simple base64 encoding test for clobs";
               st.setString(2, "BASE64_ENC_CLOB");
               st.setString(3, test);
               st.setString(4, "unused");
               st.setLong(5, 1L); // loop only once
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               Clob clob = st.getClob(1);
               long len = clob.length();
               byte[] buf = new byte[(int)len];
               clob.getAsciiStream().read(buf);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
               String out = new String(Base64.decodeBase64(buf));
               assertEquals("invocation '" + sql + "' gave the wrong result ", test, out);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         {
            // fill_blob_char(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               String result = "<col name=\"fill\">this is a simple test string for the fill_blob_char</col>";
               String test = "this is a simple test string for the fill_blob_char";
               st.setString(2, "FILL_BLOB_CHAR2");
               st.setString(3, test);
               st.setString(4, "fill");
               st.setLong(5, 1L); // loop only once
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               Clob clob = st.getClob(1);
               long len = clob.length();
               byte[] buf = new byte[(int)len];
               clob.getAsciiStream().read(buf);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
               String out = new String(buf);
               assertEquals("invocation '" + sql + "' gave the wrong result ", result+result, out);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur ocalhostwhen testing '" + sql + "'", false);
            }
         }

         {
            sql = "{? = call " + this.replPrefix + "check_tables(?,?,?,?,NULL)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               st.setString(2, "dbName");    // database name
               st.setString(3, "schema");
               st.setString(4, "table");
               st.setString(5, "COMMAND");
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               rs.close();
               st.close();
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }

         {
            sql = "{? = call " + this.replPrefix + "check_tables(?,?,?,?,?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               st.setString(2, "dbName");    // database name
               st.setString(3, "schema");
               st.setString(4, "table");
               st.setString(5, "COMMAND");
               st.setString(6, "TEST CONTENT");
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               rs.close();
               st.close();
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }

         // TEST HERE THE repl_add_table function behaviour
         {
            sql = "{? = call " + this.replPrefix + "add_table(?,?,?,?)}"; // name text, content text)
            try {
               try {
                  this.pool.update("DELETE FROM " + "TEST_REPLICATION");
               }
               catch (Exception e) {
               }

               CallableStatement st = conn.prepareCall(sql);
               st.setString(2, null);
               st.setString(3, this.specificHelper.getOwnSchema(this.pool));
               st.setString(4, "TEST_REPLICATION");
               st.setString(5, "CREATE");
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               rs.close();
               st.close();
               // assertEquals("Checking the invocation of '" + this.replPrefix + "add_table': and addition which must result in no operation", "FALSE", ret);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         // TEST HERE THE repl_add_table function behaviour
         {
            sql = "{? = call " + this.replPrefix + "add_table(?,?,?,?)}"; // name text, content text)
            try {
               boolean force = false;
               boolean forceSend = false;
               TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(this.pool), "TEST_REPLICATION");
               tableToWatch.setActions("");
               tableToWatch.setTrigger(null);
               this.dbSpecific.addTableToWatch(tableToWatch, force, null, forceSend);
               CallableStatement st = conn.prepareCall(sql);
               st.setString(2, null);
               st.setString(3, this.specificHelper.getOwnSchema(this.pool));
               st.setString(4, "TEST_REPLICATION");
               st.setString(5, "CREATE");
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               rs.close();
               st.close();
               assertEquals("Checking the invocation of '" + this.replPrefix + "add_table': and addition which must result in no operation", "TRUE", ret);
               // and the entry should be in the repl_items table
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }
         try {
            this.pool.update("DELETE FROM " + this.replPrefix + "TEST_REPLICATION");
         }
         catch (Exception e) {
         }
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   
   /**
    * This method makes some calls to system functions.
    * @throws Exception Any type is possible
    */
   public final void testFunctions() throws Exception {
      log.info("Start testFunctions");

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String sql = null;
         {
        	// col2xml_cdata(?, ?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML_CDATA");
            st.setString(3, "prova");
            st.setString(4, "test");
            st.setLong(5, 1L); // loop only once
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"test\"><![CDATA[prova]]></col>", new String(buf));
         }
         {
            // col2xml_base64(?, ?)
            sql = "{? = call " + this.replPrefix + "test_blob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML_BASE64");
            st.setBytes(3, "prova".getBytes());
            st.setString(4, "test");
            st.setLong(5, 1L); // loop only once
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"test\" encoding=\"base64\">cHJvdmE=</col>", new String(buf));
         }
         // now testing the " + this.replPrefix + "needs_prot for the three cases ...
         { // needs no protection needs_prot(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "NEEDS_PROT");
            st.setString(3, "prova");
            st.setString(4, "unused");
            st.setLong(5, 1L);
            // st.registerOutParameter(1, Types.INTEGER);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            // int ret = st.getInt(1);
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            String txt = new String(buf);
            log.fine("The return value of the query '" + sql + "' is '" + txt + "'");
            int ret = -1000;
            try {
               ret = Integer.parseInt(txt);
            }
            catch (Throwable e) {
               assertTrue("Conversion exception should not occur on '" + sql + "' where ret is '" + txt + "'", false);
            }
            rs.close();
            st.close();
            assertEquals(sql, 0, ret);
         }
         { // needs BASE64 needs_prot(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "NEEDS_PROT");
            st.setString(3, "<![[CDATAsome text]]>");
            st.setString(4, "unused");
            st.setLong(5, 1L);
            // st.registerOutParameter(1, Types.INTEGER);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            // int ret = st.getInt(1);
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            String txt = new String(buf);
            log.fine("The return value of the query '" + sql + "' is '" + txt + "'");
            int ret = -1000;
            try {
               ret = Integer.parseInt(txt);
            }
            catch (Throwable e) {
               assertTrue("Conversion exception should not occur on '" + sql + "' where ret is '" + txt + "'", false);
            }
            rs.close();
            st.close();
            assertEquals(sql, 2, ret);
         }
         { // needs CDATA needs_prot(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "NEEDS_PROT");
            st.setString(3, "this is a &lt;a");
            st.setString(4, "unused");
            st.setLong(5, 1L);
            // st.registerOutParameter(1, Types.INTEGER);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            // int ret = st.getInt(1);
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            String txt = new String(buf);
            log.fine("The return value of the query '" + sql + "' is '" + txt + "'");
            int ret = -1000;
            try {
               ret = Integer.parseInt(txt);
            }
            catch (Throwable e) {
               assertTrue("Conversion exception should not occur on '" + sql + "' where ret is '" + txt + "'", false);
            }
            rs.close();
            st.close();
            assertEquals(sql, 1, ret);
         }
         { // needs CDATA needs_prot(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "NEEDS_PROT");
            st.setString(3, "&lt;this is a");
            st.setString(4, "unused");
            st.setLong(5, 1L);
            // st.registerOutParameter(1, Types.INTEGER);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            // int ret = st.getInt(1);
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            String txt = new String(buf);
            log.fine("The return value of the query '" + sql + "' is '" + txt + "'");
            int ret = -1000;
            try {
               ret = Integer.parseInt(txt);
            }
            catch (Throwable e) {
               assertTrue("Conversion exception should not occur on '" + sql + "' where ret is '" + txt + "'", false);
            }
            rs.close();
            st.close();
            assertEquals(sql, 1, ret);
         }
         { // needs CDATA needs_prot(?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "NEEDS_PROT");
            st.setString(3, "a&lt;this is a");
            st.setString(4, "unused");
            st.setLong(5, 1L);
            // st.registerOutParameter(1, Types.INTEGER);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            // int ret = st.getInt(1);
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            String txt = new String(buf);
            log.fine("The return value of the query '" + sql + "' is '" + txt + "'");
            int ret = -1000;
            try {
               ret = Integer.parseInt(txt);
            }
            catch (Throwable e) {
               assertTrue("Conversion exception should not occur on '" + sql + "' where ret is '" + txt + "'", false);
            }
            rs.close();
            st.close();
            assertEquals(sql, 1, ret);
         }
         
         // now testing the " + this.replPrefix + "needs_prot for the three cases ...
         { // needs no protection
            // col2xml(?, ?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML");
            st.setString(3, "colValue");
            st.setString(4, "colName");
            st.setLong(5, 1L);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"colName\">colValue</col>", new String(buf));
         }
         {  // col2xml(?, ?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML");
            st.setString(3, "prova");
            st.setString(4, "test");
            st.setLong(5, 1L);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"test\">prova</col>", new String(buf));
         }
         { // needs BASE64 col2xml(?, ?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML");
            st.setString(3, "<![CDATA[colValue]]>");
            st.setString(4, "colName");
            st.setLong(5, 1L);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"colName\" encoding=\"base64\">PCFbQ0RBVEFbY29sVmFsdWVdXT4=</col>", new String(buf));
         }
         { // needs CDATA col2xml(?, ?)
            sql = "{? = call " + this.replPrefix + "test_clob(?,?,?,?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "COL2XML");
            st.setString(3, "c&lt;olValue");
            st.setString(4, "colName");
            st.setLong(5, 1L);
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            Clob clob = st.getClob(1);
            long len = clob.length();
            byte[] buf = new byte[(int)len];
            clob.getAsciiStream().read(buf);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + new String(buf) + "'");
            assertEquals(sql, "<col name=\"colName\"><![CDATA[c&lt;olValue]]></col>", new String(buf));
         }
         // now test the counter ... (this invocation is used in SpecificDefault.incrementReplKey
         {
            long oldVal = 0;
            for (int i=0; i < 2; i++) {
               // sql = "{? = call nextval('repl_seq')}";
               sql = "{? = call " + this.replPrefix + "increment()}";
               CallableStatement st = conn.prepareCall(sql);
               // st.registerOutParameter(1, Types.BIGINT);
               st.registerOutParameter(1, Types.INTEGER);
               ResultSet rs = st.executeQuery();
               // long ret = st.getLong(1);
               long ret = st.getLong(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'. The maximum integer value is '" + Integer.MAX_VALUE + "'");
               if (i == 0)
                  oldVal = ret;
               else
                  assertEquals(sql, oldVal + i , ret);
            }
         }
         
         {  // tests that by broadcast statements they are written in the ITEMS table
            // make sure there is nothing in the ITEMS table
            sql = "DELETE FROM " + this.dbHelper.getIdentifier(this.replPrefix + "ITEMS");
            this.pool.update(sql);
            
            sql = "SELECT * FROM " + this.dbHelper.getIdentifier(this.replPrefix + "ITEMS");
            
            long maxResponseEntries = 10L;
            boolean isHighPrio = true;
            boolean isMaster = false;
            String sqlTopic = null;
            String statementId = "1";
            this.dbSpecific.broadcastStatement(sql, maxResponseEntries, isHighPrio, isMaster, sqlTopic, statementId);
            Statement st = conn.createStatement();
            try {
               ResultSet rs = st.executeQuery("SELECT content FROM " + this.dbHelper.getIdentifier(this.replPrefix + "ITEMS"));
               if (rs.next()) {
                  Clob clob = rs.getClob(1);
                  long len = clob.length();
                  byte[] buf = new byte[(int)len];
                  clob.getAsciiStream().read(buf);
                  String txt = new String(buf);
                  log.info("The statement to broadcast is '" + txt + "'");
                  assertFalse("There must not be any results after a SELECT statement to broadcast", rs.next());
               }
               else
                  assertTrue("There must be entries in the ITEMS table. Seems that the broadcastStatement function does not work", false);
            }
            finally {
               st.close();
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
    * Used by testChangesToReplTables
    * @param conn The jdbc connection to use
    * @param catalog the catalog (can be null)
    * @param schema the name of the schema (db specific). Can be null.
    * @param tableName The table name to add. Can NOT be null.
    * @param doReplicate the flag indicating it has to be replicated.
    * @throws Exception
    */
   private void changesToReplTables(Connection conn, String catalog, String schema, String tableName, boolean doReplicate) throws Exception {
      String flags = "";
      if (doReplicate)
         flags = "IDU";
      boolean force = false;
      String destination = null;
      boolean forceSend = false;
      TableToWatchInfo tableToWatch = new TableToWatchInfo(catalog, schema, tableName);
      tableToWatch.setActions(flags);
      tableToWatch.setTrigger(null);
      this.dbSpecific.addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
      Statement st = conn.createStatement();
      ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "tables WHERE tablename='" + this.dbHelper.getIdentifier(tableName) + "'");
      assertTrue("testing '" + tableName + "' went wrong since no entries found", rs.next());
      String name = rs.getString(3);
      assertEquals("testing if the name of the added table is the same as the retrieved one '" + tableName + "'", this.dbHelper.getIdentifier(tableName), name);
      String doReplTxt = rs.getString(4);
      boolean doRepl = false;
      if (doReplTxt != null && doReplTxt.length() > 0)
         doRepl = true;
      assertEquals("testing if the 'replicate' flag for table '" + tableName + "' is the same as used when adding it", doReplicate, doRepl);
      rs.close();
      st.close();
      // test removal now
      this.dbSpecific.removeTableToWatch(tableToWatch, false);
      st = conn.createStatement();
      rs = st.executeQuery("SELECT * from " + this.replPrefix + "tables WHERE tablename='" + this.dbHelper.getIdentifier(tableName) + "'");
      assertFalse("testing if removal of entries from the '" + this.replPrefix + "tables' for '" + tableName + "' works (tests if the entry is still there after removal)", rs.next());
      rs.close();
      st.close();
   }
   
   /**
    * This method tests adding and removing of entries to repl_tables. It checks if a table which has been added is correctly
    * written in the repl_tables table (case sensitivity is checked replicate' flag is checked and null and empty catalog and
    * schema are checked.
    * 
    * @throws Exception Any type is possible
    */
   public final void testChangesToReplTables() throws Exception {
      log.info("Start testChangesToReplTables");
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
         String schema = this.specificHelper.getOwnSchema(pool);
         changesToReplTables(conn, null, schema, "test_lowecase", true);
         changesToReplTables(conn, "", schema, "test_lowecase", true);
         changesToReplTables(conn, null, schema, "test_lowecase", false);
         changesToReplTables(conn, null, schema, "TEST_UPPERCASE", true);
         changesToReplTables(conn, null, schema, "testMixedCase", true);
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   
   /**
    * Testing if the metadata contains the correct information on how to write table names (if upper-
    * lower or mixedcase), A new table is created. A check is made to see if this generates an entry in
    * the repl_items table (it should not). It is then added to the repl_tables table. Checks are
    * made to detect if the CREATE operation as been detected (first create and then add to repl_tables),
    * if it is correctly stored in the repl_items table. Then Business Function and Trigger is added to that
    * table manually (this is normally done by the replication mechanism). Again it is checked if the
    * operation has been detected by the PL/SQL code (trigger and function) added to the business table.
    * 
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testCreateThenAddToReplTables() throws Exception {
      log.info("Start testCreateThenAddToReplTables");

      Connection conn = this.pool.reserve();
      conn.setAutoCommit(true);
      // this.dbSpecific.bootstrap(conn);
      try {
         conn.setAutoCommit(true);
         String tableName = "test_replication";
         // clean up ...
         String cascade = this.specificHelper.getCascade();
         try { pool.update("DROP TABLE " + tableName + cascade); } catch (Exception ex) { }

         String sql = "CREATE TABLE " + tableName + "(name VARCHAR(20), city VARCHAR(20), PRIMARY KEY (name))";
         pool.update(sql);
         String storedTableName = this.dbHelper.getIdentifier(tableName);
         assertNotNull("Testing if the metadata contains the correct information about the way identifiers such table names are stored mixed/upper or lower case. If an exception occurs, this information could not be retrieved. Here testing '" + tableName + "'", storedTableName);
         ResultSet rs = conn.getMetaData().getTables(null, null, storedTableName, null);
         boolean tableExists = rs.next();
         rs.close();
         rs = null;
         assertTrue("Testing if the creation of the table according to '" + sql + "' has been detected by the metadata. (with getTables). Here table '" + tableName + "' was not found", tableExists);
         
         
         // check that nothing has been written in repl_items
         Statement st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
         assertEquals("Testing if creation of a new table which is not in the " + this.replPrefix + "tables table generates an entry in " + this.replPrefix + "items (it should not)", false, rs.next());
         rs.close();
         st.close();
         
         // add the tables to be detected to the repl_tables table
         boolean force = false;
         String destination = null;
         boolean forceSend = false;

         TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
         tableToWatch.setActions("IDU");
         tableToWatch.setTrigger(null);
         this.dbSpecific.addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);
         
         // force a call to the function which detects CREATE / DROP / ALTER operations: writes on repl_items
         this.dbSpecific.forceTableChangeCheck();

         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
         assertTrue("Testing if the trigger/function associated to the business logic table '" + tableName + "' has been working. Here no entry was found in '" + this.replPrefix + "items' so it did not work property. Some DB (example postgres) detect this in forceTableChangeDetect (which was invoked here) and others (like Oracle) have a trigger on the SCHEMA", rs.next());
         String name = rs.getString(4);
         String action = rs.getString(6);
         assertEquals("Testing if the name of the table has been correctly stored in " + this.replPrefix + "items", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the name of the action performec is correctly stored in " + this.replPrefix + "items for action CREATE", "CREATE", action);
         assertFalse("Testing the number of entries in " + this.replPrefix + "items table. It is too big, there should be only one entry", rs.next());
         st.close();
         rs.close();
         
         // now we add the function and trigger which are associated to our business table ...
         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(pool), tableName, null, true); // this will invoke the publish method
         
         // from now on on an operation on that table should be detected and should start fill the repl_items table
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM " + this.replPrefix + "items");
         st.close();
         st = conn.createStatement();
         st.executeUpdate("INSERT INTO " + tableName + " VALUES ('somebody', 'Paris')");
         st.close();
         // now it should be in " + this.replPrefix + "items ...
         
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM " + this.replPrefix + "items");
         assertTrue("Testing if the INSERT operation on business table '" + tableName + "' worked. No entry was detected in " + this.replPrefix + "items. Probably the trigger or function on the business table was not working.", rs.next());
         name = rs.getString(4);
         action = rs.getString(6);
         assertEquals("Testing if the name of the table in " + this.replPrefix + "items for action INSERT is wrong.", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the action for the operation INSERT was correct in " + this.replPrefix + "items.", "INSERT", action);
         rs.close();
         st.close();
         // and now cleanup
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM " + this.replPrefix + "items");
         st.close();
         
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      finally {
         if (conn != null)
            this.pool.release(conn);
      }
      log.info("SUCCESS");
   }

   /**
    * Testing if the metadata contains the correct information on how to write table names (if upper-
    * lower or mixedcase), A table which does not exist yet is added to the repl_tables. A check is made
    * to see if the action generated an entry in repl_items (it should not). It is then
    * created. Checks are made to detect if the CREATE operation as been detected (first create and 
    * then add to repl_tables), if it is correctly stored in the repl_items table. Then Business Function 
    * and Trigger is added to that table manually (this is normally done by the replication mechanism). 
    * Again it is checked if the operation has been detected by the PL/SQL code (trigger and function) 
    * added to the business table.
    * 
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testAddToReplTablesThenCreate() throws Exception {
      log.info("Start testAddToReplTablesThenCreate");

      try { // make sure you have deleted all entries
         this.pool.update("DELETE FROM " + this.replPrefix + "ITEMS");
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
      Connection conn = this.pool.reserve();
      // this.dbSpecific.bootstrap(conn);
      try {
         conn.setAutoCommit(true);
         String tableName = "test_replication";
         // clean up ...
         String cascade = this.specificHelper.getCascade();
         // String cascade = " CASCADE";
         // just to make sure it is really gone.
         try { pool.update("DROP TABLE " + tableName + cascade); } catch (Exception ex) { }

         // add the tables to be detected to the repl_tables table
         boolean force = false;
         String destination = null;
         boolean forceSend = false;
         TableToWatchInfo tableToWatch = new TableToWatchInfo(null, this.specificHelper.getOwnSchema(pool), tableName);
         tableToWatch.setActions("IDU");
         tableToWatch.setTrigger(null);
         this.dbSpecific.addTableToWatch(tableToWatch, force, new String[] { destination }, forceSend);

         // check that nothing has been written in repl_items
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
         assertEquals("Testing if the addition of a (non created yet) table to " + this.replPrefix + "tables generates an entry in " + this.replPrefix + "items (it should not)", false, rs.next());
         rs.close();
         st.close();
         
         String sql = "CREATE TABLE " + tableName + "(name VARCHAR(20), city VARCHAR(20), PRIMARY KEY (name))";
         pool.update(sql);
         String storedTableName = this.dbHelper.getIdentifier(tableName);
         assertNotNull("Testing if the metadata contains the correct information about the way identifiers such table names are stored mixed/upper or lower case. If an exception occurs, this information could not be retrieved. Here testing '" + tableName + "'", storedTableName);
         rs = conn.getMetaData().getTables(null, null, storedTableName, null);
         boolean tableExists = rs.next();
         rs.close();
         rs = null;
         assertTrue("Testing if the creation of the table according to '" + sql + "' has been detected by the metadata. (with getTables). Here table '" + tableName + "' was not found", tableExists);
         
         // force a call to the function which detects CREATE / DROP / ALTER operations: writes on repl_items
         this.dbSpecific.forceTableChangeCheck();

         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + this.replPrefix + "items");
         assertTrue("Testing if the trigger/function associated to the business logic table '" + tableName + "' has been working. Here no entry was found in '" + this.replPrefix + "items' so it did not work property. Some DB (example postgres) detect this in forceTableChangeDetect (which was invoked here) and others (like Oracle) have a trigger on the SCHEMA", rs.next());
         String name = rs.getString(4);
         String action = rs.getString(6);
         assertEquals("Testing if the name of the table has been correctly stored in " + this.replPrefix + "items", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the name of the action performec is correctly stored in " + this.replPrefix + "items for action CREATE", "CREATE", action);
         assertFalse("Testing the number of entries in " + this.replPrefix + "items table. It is too big, there should be only one entry", rs.next());
         st.close();
         rs.close();
         
         // now we add the function and trigger which are associated to our business table ...
         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(pool), tableName, null, true); // this will invoke the publish method
         
         // from now on on an operation on that table should be detected and should start fill the repl_items table
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM " + this.replPrefix + "items");
         st.close();
         st = conn.createStatement();
         st.executeUpdate("INSERT INTO " + tableName + " VALUES ('somebody', 'Paris')");
         st.close();
         // now it should be in " + this.replPrefix + "items ...
         
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM " + this.replPrefix + "items");
         assertTrue("Testing if the INSERT operation on business table '" + tableName + "' worked. No entry was detected in " + this.replPrefix + "items. Probably the trigger or function on the business table was not working.", rs.next());
         name = rs.getString(4);
         action = rs.getString(6);
         assertEquals("Testing if the name of the table in " + this.replPrefix + "items for action INSERT is wrong.", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the action for the operation INSERT was correct in " + this.replPrefix + "items.", "INSERT", action);
         rs.close();
         st.close();
         // and now cleanup
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM " + this.replPrefix + "items");
         st.close();
         
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      finally {
         if (conn != null)
            this.pool.release(conn);
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

   public final void testAdhoc() throws Exception {
      log.info("Start testInternalFunctions");
      if (!this.specificHelper.isOracle()) {
         log.info("Stop testInternalFunctions (nothing to be done since not oracle)");
         return;
      }
      
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      
      try {
         conn  = pool.reserve();
         conn.setAutoCommit(true);
//         String sql = null;
      } 
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }
   /**
    * @see org.xmlBlaster.contrib.I_ChangePublisher#getJmsSession()
    */
   public XBSession getJmsSession() {
      return null;
   }
   
   
}
