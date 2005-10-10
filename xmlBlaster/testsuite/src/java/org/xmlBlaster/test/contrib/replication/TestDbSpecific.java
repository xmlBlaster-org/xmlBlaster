/*------------------------------------------------------------------------------
Name:      TestDbSpecific.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher;
import org.xmlBlaster.contrib.dbwatcher.mom.I_MomCb;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;

import java.util.Map;

/**
 * Test basic functionality which is specific for each database implementation. 
 * There is a predefined set of default properties which are specific to the database you use. For instance
 * if you want to use the predefined settings for oracle use:
 * <pre>
 * java -Ddb=oracle .....
 * or if you want to use postgres:
 * java -Ddb=postgres
 * </pre>
 * <p>
 * <h2>What does this test ?</h2><br/>
 * <ul>
 *   <li>This test runs without the need of an xmlBlaster server, everything is checked internally.</li>
 *   <li>Creates a user table (which is not in the repl_tables, so it will not fire any trigger)</li>
 *   <li>Explicitly reads the table (readNewTable). This shall fill the metadata of the table and publish the message</li>
 *   <li>The message is catched by our publish Method. In it all asserts will be done
 *      <ul>
 *         <li>Number of columns must be at least one (to detect that metadata is retrieved)</li>
 *        <li>Parsing of message is working correctly</li>
 *      </ul>   
 *   </li>
 *   <li>tests the method getObjectName(String op, String req)</li>
 *   <li>tests the method checkTableForCreation(String creationRequest)</li>
 *   <li>tests the method checkSequenceForCreation(String creationRequest)</li>
 * </ul> 
 * To run most of the tests you need to have a database (for example Postgres or Oracle).
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
    private boolean doCheck = true;
    
    private static SpecificHelper specificHelper; // this is static since the implementation of I_ChangePublisher is another instance
    
    /**
     * Start the test. 
     * <pre>
     * java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestDbSpecific
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestDbSpecific.class);
       TestDbSpecific test = new TestDbSpecific();
       
       try {
          test.setUp();
          test.testGetObjectName();
          test.tearDown();

          test.setUp();
          test.testCheckTableForCreation();
          test.tearDown();

          test.setUp();
          test.testCheckSequenceForCreation();
          test.tearDown();

          test.setUp();
          test.testCreateTablesWithDifferentTypes();
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
      
      specificHelper = new SpecificHelper(System.getProperties());
      
      info = new PropertiesInfo(specificHelper.getProperties());
      dbPool = setUpDbPool(info);
      String dbSpecificName = specificHelper.getProperties().getProperty("replication.dbSpecific.class");
      // , "org.xmlBlaster.contrib.replication.impl.SpecificOracle";" +
      dbSpecific = setUpDbSpecific(info, dbSpecificName);
      Connection conn = null;
      try {
         conn = dbPool.reserve();
         log.info("setUp: going to cleanup now ...");
         dbSpecific.cleanup(conn, false);
         log.info("setUp: cleanup done, going to bootstrap now ...");
         boolean doWarn = false;
         boolean force = true;
         dbSpecific.bootstrap(conn, doWarn, force);
      }
      catch (Exception ex) {
         if (conn != null)
            dbPool.release(conn);
      }
      
      try {
         // dbPool.update("DROP TRIGGER test_dbspecific_trigger ON test_dbspecific CASCADE");
         dbPool.update(specificHelper.getDropSql()[0]);
      } catch(Exception e) {
         // log.warning(e.toString()); silent warning since it should have been erased by shutDown 
      }
      try {
         dbPool.update(specificHelper.getDropSql()[1]);
         // dbPool.update("DROP TABLE test_dbspecific CASCADE");
      } catch(Exception e) {
         // log.warning(e.toString()); silent warning since it should have been erased by shutDown 
      }
   }
   
   public void init(I_Info info) throws Exception {
   }

   /**
    * This method gets invoked when a change is detected. It will check that the message is parsed correctly. 
    * It does not check that the create statement is mapped correctly to xml.
    */
   public String publish(String changeKey, String message, Map attrMap) throws Exception {
      try {
         log.info("publish invoked in method '" + currentMethod + "'");
         log.fine("message '" + message + "'");

         if (this.doCheck) {
            // first check parsing (if an assert occurs here it means there is a discrepancy between toXml and parse
            DbUpdateParser parser = new DbUpdateParser(info);
            DbUpdateInfo dbUpdateInfo = parser.readObject(message);
            String createStatement = dbSpecific.getCreateTableStatement(dbUpdateInfo.getDescription(), null);
            log.fine("=============================================");
            log.fine(createStatement);
            log.fine("=============================================");
            String msg1 = dbUpdateInfo.toXml("");
            log.fine("original message: " + message);
            log.fine("parsed message: " + msg1);
            int numOfCols = dbUpdateInfo.getDescription().getUpdateInfoColDescriptions().length;
            assertTrue("Number of columns must be at least one (to detect that metadata is retrieved)", numOfCols > 0);
            assertXMLEqual("Parsing of message is working correctly: output xml is not the same as input xml", message, msg1);
            String functionAndTrigger = dbSpecific.createTableFunction(dbUpdateInfo.getDescription());
            functionAndTrigger += dbSpecific.createTableTrigger(dbUpdateInfo.getDescription());
            
            log.fine("-- ---------------------------------------------------------------------------");
            log.fine(functionAndTrigger);
            log.fine("-- ---------------------------------------------------------------------------");
         }
         else {
            assertTrue("should never come here", false);
            // Thread.dumpStack();
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
            dbPool.update(specificHelper.getDropSql()[1]);
         } catch(Exception e) {
            log.warning(e.toString()); 
         }
         dbPool.shutdown();
      }
   }

   /**
    * This method tests all the sql statements. It checks the creation statements
    * for the different sql data types. 
    * 
    * @throws Exception Any type is possible
    */
   public final void testCreateTablesWithDifferentTypes() throws Exception {
      currentMethod = new String("testComplete");
      log.info("Start " + currentMethod);

      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      Connection conn = null;
      this.doCheck = true;
      for (int i=0; i < specificHelper.getSql().length; i++) {
         try {
            int ret = pool.update(specificHelper.getSql()[i]);
            // don't do this since oracle 10.1.0 returns zero (don't know why)
            // assertEquals("the number of created tables must be one", 1, ret);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("an exception should not occur when testing nr." + i + " which is '" + specificHelper.getSql()[i] + "' : " + ex.getMessage(), false);
         }
         try {
            log.info("processing now '" + specificHelper.getSql()[i] + "'");
            dbSpecific.readNewTable(null, null, "test_dbspecific", null);
            Thread.sleep(1000L);
            try {
               dbPool.update(specificHelper.getDropSql()[0]);
            }
            catch (Exception e) {
            }
            try {
               dbPool.update(specificHelper.getDropSql()[1]);
            }
            catch (Exception e) {
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("an exception should not occur when dropping test tables : " + ex.getMessage(), false);
         }
      }
      this.doCheck = true;
      log.info("SUCCESS");
   }

   
   /**
    * 
    * @throws Exception Any type is possible
    */
   public final void testGetObjectName() throws Exception {
      currentMethod = new String("testGetObjectName");
      log.info("Start " + currentMethod);
      SpecificDefault specificDefault = (SpecificDefault)dbSpecific;
      { 
         String op = "CREATE TABLE";
         String sql = "CREATE TABLE someName(aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "CREATE TABLE someName (aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "CREATE TABLE someName\n\n  (aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "CREATE TABLE someName    aaa";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "CREATE TABLE    someName() ";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "   CREATE TABLE someName(aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "\n\nCREATE TABLE someName(aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "  CREATE TABLE\n someName (aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { 
         String op = "CREATE TABLE";
         String sql = "  CREATE TABLE\n someName (aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         assertNotNull("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall not be null");
         assertEquals("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "')", "someName", tableName);
      }
      { // this shall be null 
         String op = "CREATE TABLE";
         String sql = "  CREATE SEQUENCE someName(aaa)";
         String tableName = specificDefault.getObjectName(op, sql);
         if (tableName != null)
            assertTrue("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall be null", false);
      }
      { // this shall be null 
         String op = "CREATE TABLE";
         String sql = null;
         String tableName = specificDefault.getObjectName(op, sql);
         if (tableName != null)
            assertTrue("the operation of SpecificDefault.getObjectName('" + op + "',null) shall be null", false);
      }
      { // this shall be null 
         String op = "CREATE TABLE";
         String sql = "";
         String tableName = specificDefault.getObjectName(op, sql);
         if (tableName != null)
            assertTrue("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall be null", false);
      }
      { // this shall be null 
         String op = "CREATE TABLE";
         String sql = "    ";
         String tableName = specificDefault.getObjectName(op, sql);
         if (tableName != null)
            assertTrue("the operation of SpecificDefault.getObjectName('" + op + "','" + sql + "') shall be null", false);
      }
      log.info("SUCCESS");
   }
   

   /**
    * This method tests all the sql statements. It checks the creation statements
    * for the different sql data types. 
    * 
    * @throws Exception Any type is possible
    */
   public final void testCheckTableForCreation() throws Exception {
      currentMethod = new String("testCheckTableForCreation");
      log.info("Start " + currentMethod);
      SpecificDefault specificDefault = (SpecificDefault)dbSpecific;
      {
         String sql = "CREATE TABLE repl_items values(sss)";
         int ret = specificDefault.checkTableForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkTableForCreation('" + sql + "')", 0, ret);
      }
      {
         String sql = "CREATE TABLE repl_tables()";
         int ret = specificDefault.checkTableForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkTableForCreation('" + sql + "')", 0, ret);
      }
      {
         String sql = "CREATE SEQUENCE repl_items values(sss) ";
         int ret = specificDefault.checkTableForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkTableForCreation('" + sql + "')", -1, ret);
      }
      {
         String sql = "CREATE TABLE aaaxmsjd values(sss) ";
         int ret = specificDefault.checkTableForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkTableForCreation('" + sql + "')", 1, ret);
      }
   }

   /**
    * This method tests all the sql statements. It checks the creation statements
    * for the different sql data types. 
    * 
    * @throws Exception Any type is possible
    */
   public final void testCheckSequenceForCreation() throws Exception {
      currentMethod = new String("testCheckSequenceForCreation");
      log.info("Start " + currentMethod);
      SpecificDefault specificDefault = (SpecificDefault)dbSpecific;
      I_DbPool pool = (I_DbPool)info.getObject("db.pool");
      assertNotNull("pool must be instantiated", pool);
      {
         String sql = "CREATE SEQUENCE repl_seq  values(sss)";
         int ret = specificDefault.checkSequenceForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkSequenceForCreation('" + sql + "')", 0, ret);
      }
      {
         String sql = "CREATE SEQUENCE blabla  values(sss)";
         int ret = specificDefault.checkSequenceForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkTableForCreation('" + sql + "') shall be zero since it only checks for the existence or 'repl_seq' by invoking a function", 0, ret);
      }
      String dropSql = "DROP SEQUENCE repl_seq";
      pool.update(dropSql); // erases the sequence
      {
         String sql = "CREATE SEQUENCE repl_seq  values(sss)";
         int ret = specificDefault.checkSequenceForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkSequenceForCreation('" + sql + "')", 1, ret);
      }
      {
         String sql = "CREATE TABLE repl_seq  values(sss)";
         int ret = specificDefault.checkSequenceForCreation(sql);
         assertEquals("check the operation of SpecificDefault.checkSequenceForCreation('" + sql + "')", -1, ret);
      }
      log.info("SUCCESS");
   }
}
