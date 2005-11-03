/*------------------------------------------------------------------------------
 Name:      TestHelperClasses.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.util.Properties;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;

/**
 * Test helper classes as for example beans used for the configuration.
 * Does not need a DB nor xmlBlaster running.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TestHelperClasses extends XMLTestCase {
   private static Logger log = Logger.getLogger(TestHelperClasses.class.getName());
   
   /**
    * Start the test.
    * <pre>
    *  java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestHelperClasses
    * </pre>
    * @param args  Command line settings
    */
   public static void main(String[] args) {
      // junit.swingui.TestRunner.run(TestHelperClasses.class);
      
      TestHelperClasses test = new TestHelperClasses();
      try {

         test.setUp();
         test.testTableToWatchInfoKeys();
         test.tearDown();

         test.setUp();
         test.testTableToWatchInfoValues();
         test.tearDown();

         test.setUp();
         test.testTableToWatchInfoStatic();
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
   public TestHelperClasses() {
      super();
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Constructor for TestHelperClasses.
    * 
    * @param arg0
    */
   public TestHelperClasses(String arg0) {
      super(arg0);
      XMLUnit.setIgnoreWhitespace(true);
   }

   /**
    * Configure database access.
    * @see TestCase#setUp()
    */
   protected void setUp() throws Exception {
      super.setUp();
   }

   /*
    * @see TestCase#tearDown()
    */
   protected void tearDown() throws Exception {
      super.tearDown();
   }

   
   /**
    * 
    */
   public final void testTableToWatchInfoKeys() {
      log.info("Start testTableToWatchInfoKeys");
      
      TableToWatchInfo tableToWatchInfo = new TableToWatchInfo();
      String key1 = "table.catalog1.schema1.table1";
      String key2 = "table.schema2.table2";
      String key3 = "table..schema3.table3";
      String key4 = "table.table4";
      String key5 = "table...table5";

      String key6 = "  table.  catalog6  .  schema6  . table6 ";
      String key7 = " table. schema7 . table7 ";
      String key8 = " table. . schema8 . table8 ";
      String key9 = " table. table9";
      String key10 = " table. . . table10 ";
      
      try {
         tableToWatchInfo.assignFromInfoPair(key1, null);
         assertEquals("catalog for key1", "catalog1", tableToWatchInfo.getCatalog());
         assertEquals("schema for key1", "schema1", tableToWatchInfo.getSchema());
         assertEquals("catalog for key1", "table1", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key1 '" + key1 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key2, null);
         assertEquals("catalog for key2", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key2", "schema2", tableToWatchInfo.getSchema());
         assertEquals("catalog for key2", "table2", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key2 '" + key2 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key3, null);
         assertEquals("catalog for key3", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key3", "schema3", tableToWatchInfo.getSchema());
         assertEquals("catalog for key3", "table3", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key3 '" + key3 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key4, null);
         assertEquals("catalog for key4", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key4", " ", tableToWatchInfo.getSchema());
         assertEquals("catalog for key4", "table4", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key4 '" + key4 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key5, null);
         assertEquals("catalog for key5", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key5", " ", tableToWatchInfo.getSchema());
         assertEquals("catalog for key5", "table5", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key5 '" + key5 + "'", false);
      }


      try {
         tableToWatchInfo.assignFromInfoPair(key6, null);
         assertEquals("catalog for key6", "catalog6", tableToWatchInfo.getCatalog());
         assertEquals("schema for key6", "schema6", tableToWatchInfo.getSchema());
         assertEquals("catalog for key6", "table6", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key6 '" + key6 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key7, null);
         assertEquals("catalog for key7", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key7", "schema7", tableToWatchInfo.getSchema());
         assertEquals("catalog for key7", "table7", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key7 '" + key7 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key8, null);
         assertEquals("catalog for key8", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key8", "schema8", tableToWatchInfo.getSchema());
         assertEquals("catalog for key8", "table8", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key8 '" + key8 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key9, null);
         assertEquals("catalog for key9", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key9", " ", tableToWatchInfo.getSchema());
         assertEquals("catalog for key9", "table9", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key9 '" + key9 + "'", false);
      }
      try {
         tableToWatchInfo.assignFromInfoPair(key10, null);
         assertEquals("catalog for key10", " ", tableToWatchInfo.getCatalog());
         assertEquals("schema for key10", " ", tableToWatchInfo.getSchema());
         assertEquals("catalog for key10", "table10", tableToWatchInfo.getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("an exception should not happen when testing key10 '" + key10 + "'", false);
      }

      log.info("SUCCESS");
   }

   /**
    * 
    */
   public final void testTableToWatchInfoValues() {
      log.info("Start testTableToWatchInfoValues");
      
      String key = "table.catalog.schema.table";

      try {
         
         String val1 = null;
         TableToWatchInfo tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val1);
         assertEquals("replicate for val1", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val1", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val1", -1L, tableToWatchInfo.getReplKey());

         String val2 = "";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val2);
         assertEquals("replicate for val2", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val2", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val2", -1L, tableToWatchInfo.getReplKey());
         
         String val3 = ",,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val3);
         assertEquals("replicate for val3", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val3", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val3", -1L, tableToWatchInfo.getReplKey());

         String val4 = " , , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val4);
         assertEquals("replicate for val4", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val4", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val4", -1L, tableToWatchInfo.getReplKey());

         String val5 = "false,trigger,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val5);
         assertEquals("replicate for val5", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val5", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val5", 10L, tableToWatchInfo.getReplKey());

         String val6 = " false , trigger , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val6);
         assertEquals("replicate for val6", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val6", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val6", 10L, tableToWatchInfo.getReplKey());

         String val7 = ",trigger,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val7);
         assertEquals("replicate for val7", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val7", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val7", 10L, tableToWatchInfo.getReplKey());

         String val8 = "  , trigger , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val8);
         assertEquals("replicate for val8", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val8", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val8", 10L, tableToWatchInfo.getReplKey());

         String val9 = "false,,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val9);
         assertEquals("replicate for val9", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val9", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val9", 10L, tableToWatchInfo.getReplKey());

         String val10 = " false , , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val10);
         assertEquals("replicate for val10", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val10", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val10", 10L, tableToWatchInfo.getReplKey());

         String val11 = "false,trigger,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val11);
         assertEquals("replicate for val11", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val11", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val11", -1L, tableToWatchInfo.getReplKey());

         String val12 = " false , trigger , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val12);
         assertEquals("replicate for val12", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val12", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val12", -1L, tableToWatchInfo.getReplKey());

         String val13 = ",,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val13);
         assertEquals("replicate for val13", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val13", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val13", 10L, tableToWatchInfo.getReplKey());

         String val14 = "  ,  , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val14);
         assertEquals("replicate for val14", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val14", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val14", 10L, tableToWatchInfo.getReplKey());

         String val15 = ",trigger,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val15);
         assertEquals("replicate for val15", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val15", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val15", -1L, tableToWatchInfo.getReplKey());

         String val16 = "  , trigger , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val16);
         assertEquals("replicate for val16", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val16", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val16", -1L, tableToWatchInfo.getReplKey());
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }

      log.info("SUCCESS");
   }


   /**
    * 
    */
   public final void testTableToWatchInfoStatic() {
      log.info("Start testTableToWatchInfoStatic");
      
      I_Info info = new PropertiesInfo(new Properties());
      // info.put("table.schema1.table1", null); does not work since info will no add this
      info.put("table.schema1.table1", "");
      info.put("table.schema1.table2", "true, trigger2,10");
      info.put("table.schema1.table3", "true, trigger3,155");
      info.put("table.schema1.table4", "true, trigger4,6");
      info.put("table.schema1.table5", "true, trigger5,13");
      info.put("tablesomethingother", "should be ignored");
      info.put("somethingother", "should be ignored");
      try {
         TableToWatchInfo[] tables = TableToWatchInfo.getTablesToWatch(info);
         assertEquals("number of tables", 5, tables.length);
         assertEquals("table sequence #1", "table1", tables[0].getTable());
         assertEquals("table sequence #2", "table4", tables[1].getTable());
         assertEquals("table sequence #3", "table2", tables[2].getTable());
         assertEquals("table sequence #4", "table5", tables[3].getTable());
         assertEquals("table sequence #5", "table3", tables[4].getTable());
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur here" + ex.getMessage(), false);
      }
      log.info("SUCCESS");
   }

   
   
   
}
