/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.dbwatcher;

import java.io.IOException;
import java.io.InputStream;
import java.util.prefs.Preferences;


import junit.framework.TestCase;
import org.custommonkey.xmlunit.XMLTestCase;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher;
import org.xmlBlaster.test.contrib.TestUtils;

import java.util.logging.Logger;

import java.util.Map;
import java.util.HashMap;

/**
 * Test basic functionality. 
 * <p> 
 * To run most of the tests you need to have a databse (for example Oracle)
 * and XmlBlaster up and running.
 * </p>
 * <p>
 * The connection configuration (url, password etc.) is configured
 * as JVM property or in {@link #createTest(I_Info, Map)} and
 * {@link #setUpDbPool(I_Info)}
 * </p> 
 *
 * @see DbWatcher
 * @author Marcel Ruff
 */
public class TestResultSetToXmlConverter extends XMLTestCase {
    private static Logger log = Logger.getLogger(TestResultSetToXmlConverter.class.getName());
    private Preferences prefs;
    private I_Info info;
    private I_DbPool dbPool;
    private Map updateMap = new HashMap(); // collects received update messages
    private DbWatcher processor;

    /**
     * Start the test. 
     * <pre>
     * java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        junit.swingui.TestRunner.run(TestResultSetToXmlConverter.class);
    }

    /**
     * Default ctor. 
     */
    public TestResultSetToXmlConverter() {
    }

   /**
    * Constructor for TestResultSetToXmlConverter.
    * @param arg0
    */
    public TestResultSetToXmlConverter(String arg0) {
       super(arg0);
    }

    /**
     * Configure database access. 
     * @see TestCase#setUp()
     */
   protected void setUp() throws Exception {
      super.setUp();
      this.prefs = Preferences.userRoot();
      this.prefs.clear();
      this.info = new Info(this.prefs);
      
      this.dbPool = setUpDbPool(info);
      try {
         this.dbPool.update("DROP TABLE TEST_POLL");
      } catch(Exception e) {
         log.warning(e.toString()); 
      }
      
      this.processor = null;
   }
   
   /**
    * Creates a database pooling instance and puts it to info. 
    * @param info The configuration
    * @return The created pool
    */
   public static DbPool setUpDbPool(I_Info info) {
      String driverClass = System.getProperty("jdbc.drivers", "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
      ////System.setProperty("jdbc.drivers", driverClass);

      /*
      String dbUrl = System.getProperty("db.url", "jdbc:oracle:thin:@localhost:1521:orcl");
      String dbUser = System.getProperty("db.user", "system");
      String dbPassword = System.getProperty("db.password", "");
      */
      
      String dbUrl = System.getProperty("db.url", "jdbc:oracle:thin:@desktop:1521:test");
      String dbUser = System.getProperty("db.user", "system");
      String dbPassword = System.getProperty("db.password", "frifra20");
      
      //String fs = System.getProperty("file.separator");
      //String dbUrl = "jdbc:hsqldb:"+System.getProperty("user.home")+fs+"tmp"+fs+"testpoll";
      //String dbUser = "sa";
      //String dbPassword = "";

      info.put("jdbc.drivers", driverClass);
      info.put("db.url", dbUrl);
      info.put("db.user", dbUser);
      info.put("db.password", dbPassword);
        
      DbPool dbPool = new DbPool();
      dbPool.init(info);
      info.putObject("db.pool", dbPool);
      
      return dbPool;
   }

   /**
    * Creates a DbWatcher instance and listens on MoM messages. 
    * @param info Configuration
    * @param updateMap The map for received messages
    * @return A new DbWatcher
    * @throws Exception
    */
   public static DbWatcher createTest(I_Info info, final Map updateMap) throws Exception {
      /*
      // Configure the MoM
      this.prefs.put("mom.connectQos", 
                     "<qos>" +
                     " <securityService type='htpasswd' version='1.0'>" +
                     "   <![CDATA[" + 
                     "   <user>michele</user>" +
                     "   <passwd>secret</passwd>" +
                     "   ]]>" +
                     " </securityService>" +
                     " <session name='joe/3'/>'" +
                     " <address type='SOCKET'>" +
                     "   socket://192.168.110.10:7607" +
                     " </address>" +
                     " </qos>");
      System.setProperty("protocol", "SOCKET");
      System.setProperty("protocol/socket/hostname", "192.168.110.10");
      */

      DbWatcher pc = new DbWatcher(info);
      XmlBlasterPublisher mom = (XmlBlasterPublisher)pc.getMom();
      mom.subscribe("XPATH://key", new I_Update() {
         public void update(String topic, java.io.InputStream is, Map attrMap) {
            log.info("Received '" + topic + "' from MoM");
            try {
               writeToFile(topic, new String(TestUtils.getContent(is)));
            }
            catch (Exception e) {
               // Ignore   
            }
            updateMap.put(topic, is);
         }
      });
      
      try { Thread.sleep(1000); } catch(Exception e) { /* Ignore */ }
      updateMap.clear(); // Ignore any existing topics

      pc.startAlertProducers();
      
      return pc;
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
       
      if (this.processor != null) {
         this.processor.shutdown();
         this.processor = null;
      }
       
      if (this.dbPool != null) {
         try {
            this.dbPool.update("DROP TABLE TEST_POLL");
         } catch(Exception e) {
            log.warning(e.toString()); 
         }
         this.dbPool.shutdown();
      }
   }

   /**
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testTableStates() throws Exception {
      log.info("Start testTableStates()");

      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", ""); // !!! Tests without grouping
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("changeDetector.detectStatement", "SELECT colKey, col1, col2, ICAO_ID FROM TEST_POLL");
      this.prefs.put("mom.topicName", "db.change.event.TEST_POLL");
      
      this.processor = createTest(new Info(prefs), this.updateMap);
      I_ChangeDetector changeDetector = processor.getChangeDetector();
      
      for (int i=0; i<2; i++) {
         log.info("Testing no table ...");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
      log.info("Now testing an empty table ...");
      this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
      assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
      assertXpathNotExists("/myRootTag/row[@num='0']", xml);
      assertXpathEvaluatesTo("CREATE", "/myRootTag/desc/command/text()", xml);
      this.updateMap.clear();

      writeToFile("db.change.event.CREATE", xml);

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
      log.info("Insert one row");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1.1', '<Bla', '9000', 'EDDI')");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
      assertNotNull("xml returned is null", xml);
      // TODO: We deliver a "UPDATE" because of the CREATE md5: Is it easy possible to detect the INSERT?
      assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
      assertXpathEvaluatesTo("<Bla", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      this.updateMap.clear();

      writeToFile("db.change.event.INSERT", xml);

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }
            
      {
         log.info("Update one row");
         this.dbPool.update("UPDATE TEST_POLL SET col1='BXXX' WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
         assertXpathEvaluatesTo("BXXX", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.UPDATE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Delete one row");
         this.dbPool.update("DELETE FROM TEST_POLL WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         // TODO: We deliver "UPDATE" instead of DELETE:
         assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
         assertXpathNotExists("/myRootTag/row[@num='0']", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.DELETE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Drop a table");
         this.dbPool.update("DROP TABLE TEST_POLL");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         assertXpathEvaluatesTo("DROP", "/myRootTag/desc/command/text()", xml);
         assertXpathNotExists("/myRootTag/row[@num='0']", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.DROP", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      log.info("SUCCESS");
   }

   /**
    * @throws Exception Any type is possible
    */
   public final void testNULLcol() throws Exception {
      log.info("Start testNULLcol()");

      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", ""); // !!! Tests without grouping
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("changeDetector.detectStatement", "SELECT colKey, col1, col2, ICAO_ID FROM TEST_POLL");
      this.prefs.put("mom.topicName", "db.change.event.TEST_POLL");

      this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
      //this.dbPool.update("INSERT INTO TEST_POLL (colKey, col1, col2) VALUES ('2.0', 'XXX', '2000')");
      this.dbPool.update("INSERT INTO TEST_POLL (colKey) VALUES ('2.0')");
      
      this.processor = createTest(new Info(prefs), this.updateMap);
      I_ChangeDetector changeDetector = processor.getChangeDetector();
      
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
      assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
      assertXpathEvaluatesTo("2", "/myRootTag/row[@num='0']/col[@name='COLKEY']/text()", xml);
      assertXpathEvaluatesTo("", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      assertXpathEvaluatesTo("", "/myRootTag/row[@num='0']/col[@name='COL2']/text()", xml);
      assertXpathEvaluatesTo("", "/myRootTag/row[@num='0']/col[@name='ICAO_ID']/text()", xml);

      log.info("SUCCESS");
   }

   private void sleep(long millis) {
      try { Thread.sleep(millis); } catch(Exception e) { /* Ignore */ }
   }
   
   /**
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testQueryMeatTableStates() throws Exception {
      log.info("Start testQueryMeatTableStates()");

      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "select 'Bla-'||COLKEY from TEST_POLL");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", ""); // !!! Tests without grouping
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("changeDetector.detectStatement", "SELECT colKey, col1, col2, ICAO_ID FROM TEST_POLL");
      this.prefs.put("mom.topicName", "db.change.event.TEST_POLL");
      
      this.processor = createTest(new Info(prefs), this.updateMap);
      I_ChangeDetector changeDetector = processor.getChangeDetector();
      
      for (int i=0; i<2; i++) {
         log.info("Testing no table ...");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
      log.info("Now testing an empty table ...");
      this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
      assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
      assertXpathNotExists("/myRootTag/row[@num='0']", xml);
      assertXpathEvaluatesTo("CREATE", "/myRootTag/desc/command/text()", xml);
      this.updateMap.clear();

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
      log.info("Insert one row");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1.1', '<Bla', '9000', 'EDDI')");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
      assertNotNull("xml returned is null", xml);
      assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
      //assertXpathEvaluatesTo("Bla-1,1", "/myRootTag/row[@num='0']/col[@name='BLA-||COLKEY']/text()", xml);
      assertTrue(xml.indexOf("Bla-1.1") != -1);
      this.updateMap.clear();

      writeToFile("db.change.event.INSERT", xml);

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }
            
      {
         log.info("Update one row");
         this.dbPool.update("UPDATE TEST_POLL SET colKey='4.44' WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
         //assertXpathEvaluatesTo("Bla-4.44", "/myRootTag/row[@num='0']/col[@name='BLA-||COLKEY']/text()", xml);
         assertTrue(xml.indexOf("Bla-4.44") != -1);
         this.updateMap.clear();

         writeToFile("db.change.event.UPDATE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Delete one row");
         this.dbPool.update("DELETE FROM TEST_POLL WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
         assertXpathNotExists("/myRootTag/row[@num='0']", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.DELETE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Drop a table");
         this.dbPool.update("DROP TABLE TEST_POLL");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.TEST_POLL");
         assertXpathEvaluatesTo("DROP", "/myRootTag/desc/command/text()", xml);
         assertXpathNotExists("/myRootTag/row[@num='0']", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.DROP", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      log.info("SUCCESS");
   }

   /**
    * Test synchronous all possible table changes.
    * We drive two test, one with meat and one as content less event messages.
    * @throws Exception Any type is possible
    */
   public final void testGroupedQueryMeatTableStates() throws Exception {
      log.info("Start testGroupedQueryMeatTableStates()");

      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "select ICAO_ID, 'Bla-'||COLKEY from TEST_POLL where ICAO_ID='${groupColValue}'");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("changeDetector.detectStatement", "SELECT colKey, col1, col2, ICAO_ID FROM TEST_POLL ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      
      boolean hasConverter = false;
      for (int run=0; run<2; run++) {
         if (run == 0) {
            this.prefs.put("converter.class", "");
         }
         else {        
            if (this.processor != null) this.processor.shutdown();
            this.prefs.put("converter.class", "org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter");
            hasConverter = true;
         }
         this.processor = createTest(new Info(prefs), this.updateMap);
         I_ChangeDetector changeDetector = processor.getChangeDetector();

         for (int i=0; i<2; i++) {
            log.info("Testing no table ...");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
         log.info("Now testing an empty table ...");
         this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
         if (hasConverter) {
            assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            assertXpathEvaluatesTo("CREATE", "/myRootTag/desc/command/text()", xml);
         }
         this.updateMap.clear();

         writeToFile("db.change.event.CREATE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
         log.info("Insert one row");
         this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1.1', '<Bla', '9000', 'EDDI')");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.EDDI");
         assertNotNull("No db.change.event.EDDI message has arrived", xml);
         if (hasConverter) {
            assertXpathEvaluatesTo("INSERT", "/myRootTag/desc/command/text()", xml);
            assertTrue(xml.indexOf("Bla-1.1") != -1);
         }
         this.updateMap.clear();

         writeToFile("db.change.event.INSERT", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }
               
         {
            log.info("Update one row");
            this.dbPool.update("UPDATE TEST_POLL SET col1='BXXX' WHERE ICAO_ID='EDDI'");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.EDDI");
            assertNotNull("No db.change.event.EDDI message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
               assertFalse(xml.indexOf("BXXX") != -1); // col is not in queryMeatStatement
            }
            this.updateMap.clear();

            writeToFile("db.change.event.UPDATE", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
            log.info("Delete one row");
            this.dbPool.update("DELETE FROM TEST_POLL WHERE ICAO_ID='EDDI'");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.EDDI");
            assertNotNull("No db.change.event.EDDI message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("DELETE", "/myRootTag/desc/command/text()", xml);
               assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            }
            this.updateMap.clear();

            writeToFile("db.change.event.DELETE", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
            log.info("Drop a table");
            this.dbPool.update("DROP TABLE TEST_POLL");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
            assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("DROP", "/myRootTag/desc/command/text()", xml);
               assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            }
            this.updateMap.clear();

            writeToFile("db.change.event.DROP", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

      }
      log.info("SUCCESS");
   }

   /**
    * Test synchronous all possible table changes.
    * We drive two test, one with meat and one as content less event messages.
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testGroupedTableStates() throws Exception {
      log.info("Start testGroupedTableStates()");

      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("changeDetector.detectStatement", "SELECT colKey, col1, col2, ICAO_ID FROM TEST_POLL ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      
      boolean hasConverter = false;
      for (int run=0; run<2; run++) {
         if (run == 0) {
            this.prefs.put("converter.class", "");
         }
         else {        
            this.processor.shutdown();
            this.prefs.put("converter.class", "org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter");
            hasConverter = true;
         }
         this.processor = createTest(new Info(prefs), this.updateMap);
         I_ChangeDetector changeDetector = processor.getChangeDetector();

         for (int i=0; i<2; i++) {
            log.info("Testing no table ...");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
         log.info("Now testing an empty table ...");
         this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
         if (hasConverter) {
            assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            assertXpathEvaluatesTo("CREATE", "/myRootTag/desc/command/text()", xml);
         }
         this.updateMap.clear();

         writeToFile("db.change.event.CREATE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
         log.info("Insert one row");
         this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1.1', '<Bla', '9000', 'EDDI')");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.EDDI");
         assertNotNull("No db.change.event.EDDI message has arrived", xml);
         if (hasConverter) {
            assertXpathEvaluatesTo("INSERT", "/myRootTag/desc/command/text()", xml);
            assertXpathEvaluatesTo("<Bla", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
         }
         this.updateMap.clear();

         writeToFile("db.change.event.INSERT", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }
               
         {
            log.info("Update one row");
            this.dbPool.update("UPDATE TEST_POLL SET col1='BXXX' WHERE ICAO_ID='EDDI'");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.EDDI");
            assertNotNull("No db.change.event.EDDI message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
               assertXpathEvaluatesTo("BXXX", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
            }
            this.updateMap.clear();

            writeToFile("db.change.event.UPDATE", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
            log.info("Delete one row");
            this.dbPool.update("DELETE FROM TEST_POLL WHERE ICAO_ID='EDDI'");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.EDDI");
            assertNotNull("No db.change.event.EDDI message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("DELETE", "/myRootTag/desc/command/text()", xml);
               assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            }
            this.updateMap.clear();

            writeToFile("db.change.event.DELETE", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }

         {
            log.info("Drop a table");
            this.dbPool.update("DROP TABLE TEST_POLL");
            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 1, this.updateMap.size());
            String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
            assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
            if (hasConverter) {
               assertXpathEvaluatesTo("DROP", "/myRootTag/desc/command/text()", xml);
               assertXpathNotExists("/myRootTag/row[@num='0']", xml);
            }
            this.updateMap.clear();

            writeToFile("db.change.event.DROP", xml);

            changeDetector.checkAgain(null);
            sleep(500);
            assertEquals("Number of message is wrong", 0, this.updateMap.size());
         }
      }
      log.info("SUCCESS");
   }

   /**
    * Test one round trip, the message content is created on the fly.  
    * You need a running database and a running xmlBlaster server.
    * @throws Exception
    */
   public final void testRoundTripWithImplicitMeat() throws Exception {
      log.info("Start testRoundTripWithImplicitMeat()");

      this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10), col1 VARCHAR(20), col2 VARCHAR(20), ICAO_ID VARCHAR(10))");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1', '<Bla', 'Blub', 'EDDI')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('2', 'Lol<', 'Lal', 'EDDF')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('3', 'Cl&&i', 'Clo', 'EDDP')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('4', 'Bl]]>Bl', 'BBBB', 'EDDI')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('5', 'BOO', 'BIII', 'EDDI')");
      
      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "");
      this.prefs.put("alertScheduler.pollInterval", "500");
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("changeDetector.detectStatement", "SELECT col1, col2, ICAO_ID FROM TEST_POLL ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      
      this.processor = createTest(new Info(prefs), this.updateMap);

      {
      log.info("Testing startup events ...");
      try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
      assertEquals("Number of message is wrong", 3, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.EDDP");
      assertNotNull("No db.change.event.EDDP message has arrived", xml);
      assertXpathEvaluatesTo("EDDP", "/myRootTag/desc/ident/text()", xml);
      assertXpathEvaluatesTo("Cl&&i", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      assertXpathNotExists("/myRootTag/row[@num='1']", xml);
      
      xml = (String)this.updateMap.get("db.change.event.EDDI");
      writeToFile("db.change.event.EDDI", xml);
      assertXpathEvaluatesTo("3", "count(/myRootTag/desc/colname)", xml);
      assertXpathEvaluatesTo("EDDI", "/myRootTag/desc/ident/text()", xml);
      assertXpathEvaluatesTo("1", "count(/myRootTag/desc)", xml);
      assertXpathEvaluatesTo("3", "count(/myRootTag/row)", xml);
      assertXpathEvaluatesTo("<Bla", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      
      this.updateMap.clear();
      }
      
      {      
      log.info("Testing change event ...");
      this.dbPool.update("UPDATE TEST_POLL SET col1='BXXX' WHERE ICAO_ID='EDDP'");
      try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
      String xml = (String)this.updateMap.get("db.change.event.EDDP");
      assertNotNull("No db.change.event.EDDP message has arrived", xml);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      assertXpathEvaluatesTo("BXXX", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      assertXpathNotExists("/myRootTag/row[@num='1']", xml);
      this.updateMap.clear();
      }
      
      {
         log.info("Drop a table with entries");
         this.dbPool.update("DROP TABLE TEST_POLL");
         try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
         assertEquals("Number of message is wrong", 3, this.updateMap.size());
         /*
         String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         assertXpathEvaluatesTo("DROP", "/myRootTag/desc/command/text()", xml);
         assertXpathNotExists("/myRootTag/row[@num='0']", xml);
         */
         this.updateMap.clear();
         //writeToFile("db.change.event.DROP", xml);
      }
      try { Thread.sleep(1000); } catch(Exception e) { /* Ignore */ }
      
      log.info("SUCCESS");
   }

   /**
    * Test one round trip, the message content is empty sending an event only.  
    * You need a running database and a running xmlBlaster server.
    * @throws Exception
    */
   public final void testRoundTripWithoutMeat() throws Exception {
      log.info("Start testRoundTripWithoutMeat()");

      this.dbPool.update("CREATE TABLE TEST_POLL (colKey NUMBER(10), col1 VARCHAR(20), col2 VARCHAR(20), ICAO_ID VARCHAR(10))");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('1', '<Bla', 'Blub', 'EDDI')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('2', 'Lol<', 'Lal', 'EDDF')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('3', 'Cl&&i', 'Clo', 'EDDP')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('4', 'Bl]]>Bl', 'BBBB', 'EDDI')");
      this.dbPool.update("INSERT INTO TEST_POLL VALUES ('5', 'BOO', 'BIII', 'EDDI')");
      
      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("db.queryMeatStatement", "");
      this.prefs.put("alertScheduler.pollInterval", "500");
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("changeDetector.detectStatement", "SELECT col1, col2, ICAO_ID FROM TEST_POLL ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      this.prefs.put("converter.class", ""); // No change detector class !
      
      this.processor = createTest(new Info(prefs), this.updateMap);

      {
      log.info("Testing startup events ...");
      try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
      assertEquals("Number of message is wrong", 3, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.EDDP");
      assertNotNull("No db.change.event.EDDP message has arrived", xml);
      assertEquals("No content expected", "", xml);
      this.updateMap.clear();
      }
      
      {      
      log.info("Testing change event ...");
      this.dbPool.update("UPDATE TEST_POLL SET col1='BXXX' WHERE ICAO_ID='EDDP'");
      try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
      String xml = (String)this.updateMap.get("db.change.event.EDDP");
      assertNotNull("No db.change.event.EDDP message has arrived", xml);
      assertEquals("No content expected", "", xml);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      this.updateMap.clear();
      }
      
      {
         log.info("Drop a table with entries");
         this.dbPool.update("DROP TABLE TEST_POLL");
         try { Thread.sleep(1500); } catch(Exception e) { /* Ignore */ }
         assertEquals("Number of message is wrong", 3, this.updateMap.size());
         this.updateMap.clear();
      }
      
      log.info("SUCCESS");
   }

   /**
    * Dump to file. 
    * @param topic The file name body
    * @param xml The file content
    * @throws Exception IOException
    */
   public static void writeToFile(String topic, String xml) throws Exception {
      java.io.File f = new java.io.File(System.getProperty("java.io.tmpdir")+System.getProperty("file.separator")+topic+".xml");
      java.io.FileOutputStream to = new java.io.FileOutputStream(f);
      to.write(xml.getBytes());
      to.close();
   }
}
