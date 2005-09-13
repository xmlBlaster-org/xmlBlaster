/*------------------------------------------------------------------------------
Name:      TestDbSpecific.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher;
import org.xmlBlaster.contrib.dbwatcher.mom.I_MomCb;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.replication.I_DbSpecific;

import java.util.Map;

/**
 * Test basic functionality. 
 * <p> 
 * To run most of the tests you need to have a database (for example Postgres).
 * </p>
 * <p>
 * The connection configuration (url, password etc.) is configured
 * as JVM property or in {@link #createTest(I_Info, Map)} and
 * {@link #setUpDbPool(I_Info)}
 * </p> 
 *
 * @author Michele Laghi
 */
public class TestDbSpecific extends XMLTestCase implements I_ChangePublisher {
    private static Logger log = Logger.getLogger(TestDbSpecific.class.getName());
    private static I_Info info;
    private static I_DbPool dbPool;
    private static I_DbSpecific dbSpecific;
    private static String currentMethod; // since there are two instances running (I_ChangePublisher also)
    private static String[] sql = new String[] {
       "CREATE TABLE test_dbspecific (name VARCHAR(20) PRIMARY KEY)",
       "CREATE TABLE test_dbspecific (col1 CHAR, col2 CHAR(5), col3 VARCHAR, col4 VARCHAR(10), col5 int, col6 int2, col7 bytea, col8 boolean, PRIMARY KEY (col1, col2))",
       "CREATE TABLE test_dbspecific (col1 REAL, col2 REAL[10], col3 FLOAT, col4 FLOAT[4], col5 double precision, col6 double precision[4], col7 date, col8 date[100], col9 timestamp, col10 timestamp[8], PRIMARY KEY (col1, col2))",
       "CREATE TABLE test_dbspecific (col1 bpchar, col2 int[3][4][5], PRIMARY KEY (col1))"
    };
    
    /**
     * Start the test. 
     * <pre>
     * java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestDbSpecific
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestDbSpecific.class);
       TestDbSpecific test = new TestDbSpecific();
       
       try {
          test.setUp();
          // test.informativeStuff();
          test.testCharAndBlobs();
          test.tearDown();

          test.setUp();
          test.testNumbers();
          test.tearDown();

          test.setUp();
          test.testOtherTypes();
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
    public TestDbSpecific() {
       super(); 
       XMLUnit.setIgnoreWhitespace(true);
    }

   /**
    * Constructor for TestDbSpecific.
    * @param arg0
    */
    public TestDbSpecific(String arg0) {
       super(arg0);
       XMLUnit.setIgnoreWhitespace(true);
    }

    /**
     * Configure database access. 
     * @see TestCase#setUp()
     */
   protected void setUp() throws Exception {
      super.setUp();
      Preferences prefs = Preferences.userRoot();
      prefs.clear();
      info = new Info(prefs);
      
      dbPool = setUpDbPool(info);
      dbSpecific = setUpDbSpecific(info, null);
      Connection conn = null;
      try {
         conn = dbPool.reserve();
         log.info("setUp: going to cleanup now ...");
         dbSpecific.cleanup(conn);
         log.info("setUp: cleanup done, going to bootstrap now ...");
         dbSpecific.bootstrap(conn);
      }
      catch (Exception ex) {
         if (conn != null)
            dbPool.release(conn);
      }
      
      
      try {
         dbPool.update("DROP TRIGGER test_dbspecific_trigger ON test_dbspecific CASCADE");
      } catch(Exception e) {
         // log.warning(e.toString()); silent warning since it should have been erased by shutDown 
      }
      try {
         dbPool.update("DROP TABLE test_dbspecific CASCADE");
      } catch(Exception e) {
         // log.warning(e.toString()); silent warning since it should have been erased by shutDown 
      }
   }
   
   public void init(I_Info info) throws Exception {
   }

   public String publish(String changeKey, String message, Map attrMap) throws Exception {
      try {
         log.info("publish invoked in method '" + currentMethod + "'");
         log.info("message '" + message + "'");
         
         if (currentMethod.equals("testAddSimpleTable") ||
               currentMethod.equals("testCharAndBlobs") ||
               currentMethod.equals("testNumbers") ||
               currentMethod.equals("testOtherTypes")) {
            // first check parsing (if an assert occurs here it means there is a discrepancy between toXml and parse
            DbUpdateParser parser = new DbUpdateParser(info);
            DbUpdateInfo dbUpdateInfo = parser.readObject(message);
            String createStatement = dbSpecific.getCreateTableStatement(dbUpdateInfo.getDescription(), null);
            System.out.println("=============================================");
            System.out.println(createStatement);
            System.out.println("=============================================");
            String msg1 = dbUpdateInfo.toXml("");
            log.info("original message: " + message);
            log.info("parsed message: " + msg1);
            assertXMLEqual("output xml is not the same as input xml", message, msg1);
            
            String functionAndTrigger = dbSpecific.createTableFunctionAndTrigger(dbUpdateInfo.getDescription());
            
            System.out.println("-- ---------------------------------------------------------------------------");
            System.out.println(functionAndTrigger);
            System.out.println("-- ---------------------------------------------------------------------------");
            
            /*
            try {
               dbPool.update(null, functionAndTrigger);
            }
            catch (Exception e) {
               e.printStackTrace();
               assertTrue("error when executing \n" + functionAndTrigger, false);
            }
            */
         }
         else {
            assertTrue("should never come here", false);
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
      return null;
   }

   public boolean registerAlertListener(I_MomCb momCb) throws Exception {
      return false;
   }

   public void shutdown() {
   }

   /**
    * Creates a database pooling instance and puts it to info. 
    * @param info The configuration
    * @return The created pool
    */
   private DbPool setUpDbPool(I_Info info) {
      String driverClass = System.getProperty("jdbc.drivers", "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");

      String dbUrl = System.getProperty("db.url", "jdbc:postgresql:test//localhost/test");
      String dbUser = System.getProperty("db.user", "postgres");
      String dbPassword = System.getProperty("db.password", "");

      info.put("jdbc.drivers", driverClass);
      info.put("db.url", dbUrl);
      info.put("db.user", dbUser);
      info.put("db.password", dbPassword);
        
      DbPool dbPool = new DbPool(info);
      info.putObject("db.pool", dbPool);
      
      return dbPool;
   }

   /**
    * Creates an I_DbSpecific object. 
    * @param info The configuration.
    * @param dbSpecificName the name of the class to instantiate. If null, a default is taken.
    * @return The created I_DbSpecific object.
    */
   private I_DbSpecific setUpDbSpecific(I_Info info, String dbSpecificName) {
      try {
         if (dbSpecificName == null)
            dbSpecificName = "org.xmlBlaster.contrib.replication.impl.SpecificDefault";
         ClassLoader cl = DbWatcher.class.getClassLoader();
         I_DbSpecific dbSpecific = (I_DbSpecific)cl.loadClass(dbSpecificName).newInstance();
         assertNotNull("dbSpecific must not be null, check the classname", dbSpecific);
         info.put("mom.class", "org.xmlBlaster.test.contrib.replication.TestDbSpecific");
         dbSpecific.init(info);
         return dbSpecific;
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("exception when setting up publisher should not occur: " + ex.getMessage(), false);
         return null;
      }
   }
      
   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
       
      if (dbSpecific != null) {
         dbSpecific.shutdown();
         dbSpecific = null;
      }
       
      if (dbPool != null) {
         try {
            dbPool.update("DROP TABLE test_dbspecific CASCADE");
         } catch(Exception e) {
            log.warning(e.toString()); 
         }
         dbPool.shutdown();
      }
   }

   /**
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testCharAndBlobs() throws Exception {
      currentMethod = new String("testCharAndBlobs");
      log.info("Start " + currentMethod);

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         int ret = pool.update(sql[1]);
         // don't do this since oracle 10.1.0 returns zero (don't know why)
         // assertEquals("the number of created tables must be one", 1, ret);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      try {
         dbSpecific.readNewTable(null, null, "test_dbspecific", null);
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
   public final void testNumbers() throws Exception {
      currentMethod = new String("testNumbers");
      log.info("Start " + currentMethod);

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         int ret = pool.update(sql[2]);
         // don't do this since oracle 10.1.0 returns zero (don't know why)
         // assertEquals("the number of created tables must be one", 1, ret);
         dbSpecific.readNewTable(null, null, "test_dbspecific", null);
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
   public final void testOtherTypes() throws Exception {
      currentMethod = new String("testOtherTypes");
      log.info("Start " + currentMethod);

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         int ret = pool.update(sql[3]);
         // don't do this since oracle 10.1.0 returns zero (don't know why)
         // assertEquals("the number of created tables must be one", 1, ret);
         dbSpecific.readNewTable(null, null, "test_dbspecific", null);
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
   public final void informativeStuff() throws Exception {
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      try {
         conn = pool.reserve();
         DatabaseMetaData meta = conn.getMetaData();
         
         ResultSet rs = meta.getTypeInfo();
         
         while (rs.next()) {
            System.out.println(meta.getDatabaseProductName());
            System.out.println("==========================================================");
            System.out.println("'" + rs.getString(1) + "'\t  TYPE_NAME String => Type name");
            System.out.println("'" + rs.getInt(2) + "'\t DATA_TYPE int => SQL data type from java.sql.Types");
            System.out.println("'" + rs.getInt(3) + "'\t PRECISION int => maximum precision");
            System.out.println("'" + rs.getString(4) + "'\t LITERAL_PREFIX String => prefix used to quote a literal (may be null)");
            System.out.println("'" + rs.getString(5) + "'\t LITERAL_SUFFIX String => suffix used to quote a literal (may be null)");
            System.out.println("'" + rs.getString(6) + "'\t CREATE_PARAMS String => parameters used in creating the type (may be null)");
            System.out.println("'" + rs.getShort(7) + "'\t NULLABLE short => can you use NULL for this type.");
            System.out.println("'" + rs.getBoolean(8) + "'\t CASE_SENSITIVE boolean=> is it case sensitive.");
            System.out.println("'" + rs.getShort(9) + "'\t SEARCHABLE short => can you use \"WHERE\" based on this type:");
            System.out.println("'" + rs.getBoolean(10) + "'\t UNSIGNED_ATTRIBUTE boolean => is it unsigned.");
            System.out.println("'" + rs.getBoolean(11) + "'\t FIXED_PREC_SCALE boolean => can it be a money value.");
            System.out.println("'" + rs.getBoolean(12) + "'\t AUTO_INCREMENT boolean => can it be used for an auto-increment value.");
            System.out.println("'" + rs.getString(13) + "'\t LOCAL_TYPE_NAME String => localized version of type name (may be null)");
            System.out.println("'" + rs.getShort(14) + "'\t MINIMUM_SCALE short => minimum scale supported");
            System.out.println("'" + rs.getShort(15) + "'\t MAXIMUM_SCALE short => maximum scale supported");
            System.out.println("'" + rs.getInt(16) + "'\t SQL_DATA_TYPE int => unused");
            System.out.println("'" + rs.getInt(17) + "'\t SQL_DATETIME_SUB int => unused");
            System.out.println("'" + rs.getInt(18) + "'\t NUM_PREC_RADIX int => usually 2 or 10             ");
            System.out.println("==========================================================");
         }
         rs.close();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not occur " + ex.getMessage(), false);
      }
      finally {
         if (conn != null)
            pool.release(conn);
      }
      log.info("SUCCESS");
   }
}
