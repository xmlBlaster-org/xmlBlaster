/*------------------------------------------------------------------------------
Name:      TestTimestamp.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.dbwatcher;

import java.util.prefs.Preferences;


import junit.framework.TestCase;
import org.custommonkey.xmlunit.XMLTestCase;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;

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
 * as JVM property or in
 * {@link org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter#createTest(I_Info, Map)}
 * and 
 * {@link org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter#setUpDbPool(I_Info)}
 * </p> 
 *
 * @see DbWatcher
 * @author Marcel Ruff
 */
public class TestTimestamp extends XMLTestCase {
    private static Logger log = Logger.getLogger(TestTimestamp.class.getName());
    private Preferences prefs;
    private I_Info info;
    private I_DbPool dbPool;
    private Map updateMap = new HashMap(); // collects received update messages
    private String dbUrl;
    private String dbUser;
    private String dbPassword;
    private DbWatcher processor;

    /**
     * Start the test. 
     * <pre>
     * java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.dbwatcher.TestTimestamp
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        junit.swingui.TestRunner.run(TestResultSetToXmlConverter.class);
    }

    /**
     * Default ctor. 
     */
    public TestTimestamp() {
    }

   /**
    * Constructor for TestResultSetToXmlConverter.
    * @param arg0
    */
    public TestTimestamp(String arg0) {
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
      this.dbPool = org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter.setUpDbPool(info);
      this.processor = null;
      try {
         this.dbPool.update("DROP TABLE TEST_TS");
      } catch(Exception e) {
         log.warning(e.toString()); 
      }
   }

   /**
    * Creates a DbWatcher instance and listens on MoM messages. 
    * @see org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter#createTest(I_Info, Map) 
    */
   private DbWatcher createTest(I_Info info, final Map updateMap) throws Exception {
      return org.xmlBlaster.test.contrib.dbwatcher.TestResultSetToXmlConverter.createTest(info, updateMap);
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
            this.dbPool.update("DROP TABLE TEST_TS");
         } catch(Exception e) {
            log.warning(e.toString()); 
         }
         this.dbPool.shutdown();
      }
   }

   private void sleep(long millis) {
      try { Thread.sleep(millis); } catch(Exception e) { /* Ignore */ }
   }
   
   /**
    * Check detection of changes on a table and deliver the change as
    * pure event without XML dump.  
    * @throws Exception Any type is possible
    */
   public final void testEmptyTableStates() throws Exception {

      this.prefs.put("changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
      this.prefs.put("converter.class", "");
      this.prefs.put("changeDetector.detectStatement", "SELECT MAX(TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF')) FROM TEST_TS");
      this.prefs.put("changeDetector.timestampColName", "ts");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("db.queryMeatStatement", "SELECT * FROM TEST_TS WHERE TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      
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
      this.dbPool.update("CREATE TABLE TEST_TS (ts TIMESTAMP(9), colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
      assertNotNull("No db.change.event.${groupColValue} message has arrived", xml);
      assertEquals("", xml);
      this.updateMap.clear();

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
      log.info("Insert one row");
      this.dbPool.update("INSERT INTO TEST_TS VALUES (CURRENT_TIMESTAMP, '1.1', '<Bla', '9000', 'EDDI')");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.EDDI");
      assertEquals("", xml);
      this.updateMap.clear();

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Update one row");
         this.dbPool.update("UPDATE TEST_TS SET ts=CURRENT_TIMESTAMP, colKey='4.44' WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         Thread.sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.EDDI");
         assertNotNull("xml returned is null", xml);
         assertEquals("", xml);
         this.updateMap.clear();

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      // Is not detected by Timestamp poller!
      {
         log.info("Delete one row");
         this.dbPool.update("DELETE FROM TEST_TS WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         // TODO: We don't know that EDDI was deleted
         //String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         //assertNotNull("xml returned is null", xml);
         //assertEquals("", xml);
         this.updateMap.clear();

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Drop a table");
         this.dbPool.update("DROP TABLE TEST_TS");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         assertNotNull("xml returned is null", xml);
         assertEquals("", xml);
         this.updateMap.clear();

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      log.info("SUCCESS");
   }

   /**
    * Check detection of changes on a table and deliver the change as XML. 
    * @throws Exception Any type is possible
    */
   public final void testQueryMeatTableStates() throws Exception {
      log.info("Start testQueryMeatTableStates()");

      this.prefs.put("changeDetector.class", "org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector");
      this.prefs.put("changeDetector.detectStatement", "SELECT MAX(TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF')) FROM TEST_TS");
      this.prefs.put("changeDetector.timestampColName", "ts");
      this.prefs.put("alertScheduler.pollInterval", "0"); // switch off
      this.prefs.put("changeDetector.groupColName", "ICAO_ID");
      this.prefs.put("converter.rootName", "myRootTag");
      this.prefs.put("converter.addMeta", ""+true);
      this.prefs.put("db.queryMeatStatement", "SELECT * FROM TEST_TS WHERE TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY ICAO_ID");
      this.prefs.put("mom.topicName", "db.change.event.${groupColValue}");
      
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
      this.dbPool.update("CREATE TABLE TEST_TS (ts TIMESTAMP(9), colKey NUMBER(10,3), col1 VARCHAR(20), col2 NUMBER(12), ICAO_ID VARCHAR(10))");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
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
      this.dbPool.update("INSERT INTO TEST_TS VALUES (CURRENT_TIMESTAMP, '1.1', '<Bla', '9000', 'EDDI')");
      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 1, this.updateMap.size());
      String xml = (String)this.updateMap.get("db.change.event.EDDI");
      assertNotNull("xml returned is null", xml);
      assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
      assertXpathEvaluatesTo("<Bla", "/myRootTag/row[@num='0']/col[@name='COL1']/text()", xml);
      //assertTrue(xml.indexOf("Bla-1.1") != -1);
      this.updateMap.clear();

      writeToFile("db.change.event.INSERT", xml);

      changeDetector.checkAgain(null);
      sleep(500);
      assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Update one row");
         this.dbPool.update("UPDATE TEST_TS SET ts=CURRENT_TIMESTAMP, colKey='4.44' WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         Thread.sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.EDDI");
         assertNotNull("xml returned is null", xml);
         assertXpathEvaluatesTo("UPDATE", "/myRootTag/desc/command/text()", xml);
         assertXpathEvaluatesTo("4.44", "/myRootTag/row[@num='0']/col[@name='COLKEY']/text()", xml);
         this.updateMap.clear();

         writeToFile("db.change.event.UPDATE", xml);

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      // Is not detected by Timestamp poller!
      {
         log.info("Delete one row");
         this.dbPool.update("DELETE FROM TEST_TS WHERE ICAO_ID='EDDI'");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
         // TODO: We don't know that EDDI was deleted
         //String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         //assertNotNull("xml returned is null", xml);
         this.updateMap.clear();

         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 0, this.updateMap.size());
      }

      {
         log.info("Drop a table");
         this.dbPool.update("DROP TABLE TEST_TS");
         changeDetector.checkAgain(null);
         sleep(500);
         assertEquals("Number of message is wrong", 1, this.updateMap.size());
         String xml = (String)this.updateMap.get("db.change.event.${groupColValue}");
         assertNotNull("xml returned is null", xml);
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
