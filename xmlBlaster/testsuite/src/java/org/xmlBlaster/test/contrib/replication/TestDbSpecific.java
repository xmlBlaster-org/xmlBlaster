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

import java.util.Map;

/**
 * Test basic functionality which is specific for each database implementation. 
 * <p> 
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
     * java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestDbSpecific
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestDbSpecific.class);
       TestDbSpecific test = new TestDbSpecific();
       
       try {
          test.setUp();
          test.testComplete();
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
         dbSpecific.bootstrap(conn, false);
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
         log.info("message '" + message + "'");

         if (this.doCheck) {
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
            
            String functionAndTrigger = dbSpecific.createTableFunction(dbUpdateInfo.getDescription());
            functionAndTrigger += dbSpecific.createTableTrigger(dbUpdateInfo.getDescription());
            
            System.out.println("-- ---------------------------------------------------------------------------");
            System.out.println(functionAndTrigger);
            System.out.println("-- ---------------------------------------------------------------------------");
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
   public final void testComplete() throws Exception {
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

}
