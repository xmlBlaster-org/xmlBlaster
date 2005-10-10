/*------------------------------------------------------------------------------
 Name:      TestDbBasics.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.io.ByteArrayInputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
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

import java.util.Map;

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
         {
            sql = "{? = call repl_base64_helper(?, ?)}";
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
            sql = "{? = call repl_base64_enc_raw(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               
               int nmax = 256;
               byte[] in = new byte[nmax];
               for (int i=0; i < nmax; i++) {
                  in[i] = (byte)i;
               }
               st.setBytes(2, in);
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               
               byte[] out = Base64.decodeBase64(ret.getBytes());
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
            sql = "{? = call repl_base64_enc_varchar2(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);

               String test = "this is a simple base64 encoding test for clobs";
               st.setString(2, test);
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               
               String out = new String(Base64.decodeBase64(ret.getBytes()));
               assertEquals("invocation '" + sql + "' gave the wrong result ", test, out);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }

         {
            sql = "{? = call repl_base64_enc_blob(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               
               int nmax = 32000;
               byte[] in = new byte[nmax];
               for (int i=0; i < nmax; i++) {
                  in[i] = (byte)i;
               }
               
               // ByteArray
               ByteArrayInputStream bais = new ByteArrayInputStream(in);
               st.setBinaryStream(2, bais, in.length);
               st.registerOutParameter(1, Types.CLOB);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               
               byte[] out = Base64.decodeBase64(ret.getBytes());
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
            sql = "{? = call repl_base64_enc_clob(?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);

               String test = "this is a simple base64 encoding test for clobs";
               st.setString(2, test);
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
               log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
               
               String out = new String(Base64.decodeBase64(ret.getBytes()));
               assertEquals("invocation '" + sql + "' gave the wrong result ", test, out);
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
            }
         }

         {
            sql = "{? = call repl_check_tables(?,?,?,?)}"; // name text, content text)
            try {
               CallableStatement st = conn.prepareCall(sql);
               st.setString(2, "dbName");    // database name
               st.setString(3, "schema");
               st.setString(4, "table");
               st.setString(5, "COMMAND");
               st.registerOutParameter(1, Types.VARCHAR);
               ResultSet rs = st.executeQuery();
               String ret = st.getString(1);
               rs.close();
               st.close();
            }
            catch (SQLException sqlEx) {
               sqlEx.printStackTrace();
               assertTrue("an exception should not occur when testing '" + sql + "'", false);
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
            sql = "{? = call repl_col2xml_cdata(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "test");
            st.setString(3, "prova");
            st.registerOutParameter(1, Types.VARCHAR);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"test\"><![CDATA[prova]]></col>", ret);
         }
         {
            sql = "{? = call repl_col2xml_base64(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "test");
            st.setBytes(3, "prova".getBytes());
            st.registerOutParameter(1, Types.CLOB);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"test\" encoding=\"base64\">cHJvdmE=</col>", ret);
         }
         // now testing the repl_needs_prot for the three cases ...
         { // needs no protection
            sql = "{? = call repl_needs_prot(?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "prova");
            st.registerOutParameter(1, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            int ret = st.getInt(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, 0, ret);
         }
         { // needs BASE64
            sql = "{? = call repl_needs_prot(?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "<![[CDATAsome text]]>");
            st.registerOutParameter(1, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            int ret = st.getInt(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, 2, ret);
         }
         { // needs CDATA
            sql = "{? = call repl_needs_prot(?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "this is a &lt;a");
            st.registerOutParameter(1, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            int ret = st.getInt(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, 1, ret);
         }
         { // needs CDATA
            sql = "{? = call repl_needs_prot(?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "&lt;this is a");
            st.registerOutParameter(1, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            int ret = st.getInt(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, 1, ret);
         }
         { // needs CDATA
            sql = "{? = call repl_needs_prot(?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "a&lt;this is a");
            st.registerOutParameter(1, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            int ret = st.getInt(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, 1, ret);
         }
         
         // now testing the repl_needs_prot for the three cases ...
         { // needs no protection
            sql = "{? = call repl_col2xml(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "colName");
            st.setString(3, "colValue");
            st.registerOutParameter(1, Types.VARCHAR);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"colName\">colValue</col>", ret);
         }
         {
            sql = "{? = call repl_col2xml(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "test");
            st.setString(3, "prova");
            st.registerOutParameter(1, Types.VARCHAR);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"test\">prova</col>", ret);
         }
         { // needs BASE64
            sql = "{? = call repl_col2xml(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "colName");
            st.setString(3, "<![CDATA[colValue]]>");
            st.registerOutParameter(1, Types.VARCHAR);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"colName\" encoding=\"base64\">PCFbQ0RBVEFbY29sVmFsdWVdXT4=</col>", ret);
         }
         { // needs CDATA
            sql = "{? = call repl_col2xml(?, ?)}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, "colName");
            st.setString(3, "c&lt;olValue");
            st.registerOutParameter(1, Types.VARCHAR);
            ResultSet rs = st.executeQuery();
            String ret = st.getString(1);
            rs.close();
            st.close();
            log.fine("The return value of the query '" + sql + "' is '" + ret + "'");
            assertEquals(sql, "<col name=\"colName\"><![CDATA[c&lt;olValue]]></col>", ret);
         }
         // now test the counter ... (this invocation is used in SpecificDefault.incrementReplKey
         {
            long oldVal = 0;
            for (int i=0; i < 2; i++) {
               // sql = "{? = call nextval('repl_seq')}";
               sql = "{? = call repl_increment()}";
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
      this.dbSpecific.addTableToWatch(catalog, schema, tableName, doReplicate);
      Statement st = conn.createStatement();
      ResultSet rs = st.executeQuery("SELECT * from repl_tables WHERE tablename='" + this.dbHelper.getIdentifier(tableName) + "'");
      assertTrue("testing '" + tableName + "' went wrong since no entries found", rs.next());
      String name = rs.getString(3);
      assertEquals("testing if the name of the added table is the same as the retrieved one '" + tableName + "'", this.dbHelper.getIdentifier(tableName), name);
      String doReplTxt = rs.getString(4);
      boolean doRepl = false;
      if (doReplTxt.equalsIgnoreCase("t"))
         doRepl = true;
      assertEquals("testing if the 'replicate' flag for table '" + tableName + "' is the same as used when adding it", doReplicate, doRepl);
      rs.close();
      st.close();
      // test removal now
      this.dbSpecific.removeTableToWatch(catalog, schema, tableName);
      st = conn.createStatement();
      rs = st.executeQuery("SELECT * from repl_tables WHERE tablename='" + this.dbHelper.getIdentifier(tableName) + "'");
      assertFalse("testing if removal of entries from the 'repl_tables' for '" + tableName + "' works (tests if the entry is still there after removal)", rs.next());
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
         rs = st.executeQuery("SELECT * from repl_items");
         assertEquals("Testing if creation of a new table which is not in the repl_tables table generates an entry in repl_items (it should not)", false, rs.next());
         rs.close();
         st.close();
         
         // add the tables to be detected to the repl_tables table
         this.dbSpecific.addTableToWatch(null, this.specificHelper.getOwnSchema(pool), tableName, true);
         
         // force a call to the function which detects CREATE / DROP / ALTER operations: writes on repl_items
         this.dbSpecific.forceTableChangeCheck();

         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from repl_items");
         assertTrue("Testing if the trigger/function associated to the business logic table '" + tableName + "' has been working. Here no entry was found in 'repl_items' so it did not work property. Some DB (example postgres) detect this in forceTableChangeDetect (which was invoked here) and others (like Oracle) have a trigger on the SCHEMA", rs.next());
         String name = rs.getString(4);
         String action = rs.getString(6);
         assertEquals("Testing if the name of the table has been correctly stored in repl_items", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the name of the action performec is correctly stored in repl_items for action CREATE", "CREATE", action);
         assertFalse("Testing the number of entries in repl_items table. It is too big, there should be only one entry", rs.next());
         st.close();
         rs.close();
         
         // now we add the function and trigger which are associated to our business table ...
         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(pool), tableName, null); // this will invoke the publish method
         
         // from now on on an operation on that table should be detected and should start fill the repl_items table
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM repl_items");
         st.close();
         st = conn.createStatement();
         st.executeUpdate("INSERT INTO " + tableName + " VALUES ('somebody', 'Paris')");
         st.close();
         // now it should be in repl_items ...
         
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM repl_items");
         assertTrue("Testing if the INSERT operation on business table '" + tableName + "' worked. No entry was detected in repl_items. Probably the trigger or function on the business table was not working.", rs.next());
         name = rs.getString(4);
         action = rs.getString(6);
         assertEquals("Testing if the name of the table in repl_items for action INSERT is wrong.", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the action for the operation INSERT was correct in repl_items.", "INSERT", action);
         rs.close();
         st.close();
         // and now cleanup
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM repl_items");
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
         this.dbSpecific.addTableToWatch(null, this.specificHelper.getOwnSchema(pool), tableName, true);

         // check that nothing has been written in repl_items
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * from repl_items");
         assertEquals("Testing if the addition of a (non created yet) table to repl_tables generates an entry in repl_items (it should not)", false, rs.next());
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
         rs = st.executeQuery("SELECT * from repl_items");
         assertTrue("Testing if the trigger/function associated to the business logic table '" + tableName + "' has been working. Here no entry was found in 'repl_items' so it did not work property. Some DB (example postgres) detect this in forceTableChangeDetect (which was invoked here) and others (like Oracle) have a trigger on the SCHEMA", rs.next());
         String name = rs.getString(4);
         String action = rs.getString(6);
         assertEquals("Testing if the name of the table has been correctly stored in repl_items", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the name of the action performec is correctly stored in repl_items for action CREATE", "CREATE", action);
         assertFalse("Testing the number of entries in repl_items table. It is too big, there should be only one entry", rs.next());
         st.close();
         rs.close();
         
         // now we add the function and trigger which are associated to our business table ...
         this.dbSpecific.readNewTable(null, this.specificHelper.getOwnSchema(pool), tableName, null); // this will invoke the publish method
         
         // from now on on an operation on that table should be detected and should start fill the repl_items table
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM repl_items");
         st.close();
         st = conn.createStatement();
         st.executeUpdate("INSERT INTO " + tableName + " VALUES ('somebody', 'Paris')");
         st.close();
         // now it should be in repl_items ...
         
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM repl_items");
         assertTrue("Testing if the INSERT operation on business table '" + tableName + "' worked. No entry was detected in repl_items. Probably the trigger or function on the business table was not working.", rs.next());
         name = rs.getString(4);
         action = rs.getString(6);
         assertEquals("Testing if the name of the table in repl_items for action INSERT is wrong.", this.dbHelper.getIdentifier(tableName), name);
         assertEquals("Testing if the action for the operation INSERT was correct in repl_items.", "INSERT", action);
         rs.close();
         st.close();
         // and now cleanup
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM repl_items");
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
