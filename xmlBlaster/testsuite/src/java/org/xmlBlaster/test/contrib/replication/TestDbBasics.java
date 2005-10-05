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
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestDbBasics
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestDbBasics.class);
      TestDbBasics test = new TestDbBasics();
      try {
         test.setUp();
         test.testOracleInternalFunctions();
         test.tearDown();

         test.setUp();
         test.testFunctions();
         test.tearDown();

         test.setUp();
         test.testCreate();
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

   
   private void setupProperties(I_Info info) {
      // we hardcode the first ...
      info.put("jdbc.drivers", "org.hsqldb.jdbcDriver:" +
                               "oracle.jdbc.driver.OracleDriver:" +
                               "com.microsoft.jdbc.sqlserver.SQLServerDriver:" + 
                               "com.microsoft.sqlserver.jdbc.SQLServerDriver:" +
                               "org.postgresql.Driver");
      setProp(info, "db.url", "jdbc:postgresql:test//localhost");
      setProp(info, "db.user", "postgres");
      setProp(info, "db.password", "");
      setProp(info, "replication.mapper.tables", "test_replication=test_replication2");
   }

   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
      this.info = new PropertiesInfo(System.getProperties());
      setupProperties(this.info);
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
         this.dbSpecific.bootstrap(conn, false);
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
   public final void testOracleInternalFunctions() throws Exception {
      log.info("Start testOracleInternalFunctions");

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      
      try {
         conn  = pool.reserve();
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

               String test = "table1"; // it does not exist but serves the purpose here.
               st.setString(2, test);
               st.setString(3, "INSERT");
               st.setString(4, "");
               st.setString(5, "");
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
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testCreate() throws Exception {
      log.info("Start testCreate");

      Connection conn = this.pool.reserve();
      // this.dbSpecific.bootstrap(conn);
      try {
         conn.setAutoCommit(true);
         String tableName = "test_replication";
         // clean up ...
         String cascade = "";
         // String cascade = " CASCADE";
         try { pool.update("DROP TABLE " + tableName + cascade); } catch (Exception ex) { }

         String sql = "CREATE TABLE " + tableName + "(name VARCHAR(20), age INT, PRIMARY KEY (name))";
         pool.update(sql);
         String storedTableName = this.dbHelper.getIdentifier(tableName);
         assertNotNull("the storage case of '" + tableName + "' could not be determined", storedTableName);
         ResultSet rs = conn.getMetaData().getTables(null, null, storedTableName, null);
         boolean tableExists = rs.next();
         rs.close();
         rs = null;
         assertTrue("The table '" + tableName + "' has not been created", tableExists);
         
         // add the tables to be detected to the repl_tables table
         pool.update("INSERT INTO repl_tables VALUES ('" + tableName + "','t', 'CREATING')");
         
         // force a call to the function which detects CREATE / DROP / ALTER operations: writes on repl_items
         this.dbSpecific.forceTableChangeCheck();

         Statement st = conn.createStatement();
         rs = st.executeQuery("SELECT * from repl_items");
         assertTrue("No entry found on the table 'repl_items': probably the function invoked by forceTableChangeDetect is wrong", rs.next());
         String name = rs.getString(4);
         String action = rs.getString(6);
         assertEquals("The table name in repl_items for action CREATE is wrong", tableName, name);
         assertEquals("The action in repl_items for action CREATE is wrong", "CREATE", action);
         assertFalse("The number of entries in the repl_items table is too big, there should be only one entry", rs.next());
         st.close();
         rs.close();
         
         // now we add the function and trigger which are associated to our business table ...
         this.dbSpecific.readNewTable(null, null, tableName, null); // this will invoke the publish method
         
         // from now on on an operation on that table should be detected and should start fill the repl_items table
         st = conn.createStatement();
         st.executeUpdate("DELETE FROM repl_items");
         st.close();
         st = conn.createStatement();
         st.executeUpdate("INSERT INTO " + tableName + " VALUES ('somebody', 11)");
         st.close();
         // now it should be in repl_items ...
         
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * FROM repl_items");
         assertTrue("there should be a new INSERT entry in the repl_items table", rs.next());
         name = rs.getString(4);
         action = rs.getString(6);
         assertEquals("The table name in repl_items for action CREATE is wrong", tableName, name);
         assertEquals("The action in repl_items for action CREATE is wrong", "INSERT", action);
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
