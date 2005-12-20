/*------------------------------------------------------------------------------
 Name:      TestHelperClasses.java
 Project:   org.xmlBlasterProject:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.SqlInfoParser;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.contrib.replication.impl.DefaultMapper;

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
         test.testInfoHelper();
         test.tearDown();

         test.setUp();
         test.testTableMapper();
         test.tearDown();

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
   public final void testInfoHelper() {
      log.info("Start testInfoHelper");
      
      PropertiesInfo info = new PropertiesInfo(new Properties());
      
      info.put("replication.mapper.schema.testSchema", "testSchema1");
      info.put("replication.mapper.schema.testSchema1", "testSchema2");
      info.put("replication.mapper.schema2.testSchema100", "testSchema2");
      info.put("replication.mapper.sch.testSchema100", "testSchema2");
      
      Map map = InfoHelper.getPropertiesStartingWith("replication.mapper.schema.", info, null);
      assertEquals("testing amount", 2, map.size());
      assertEquals("testing 1/2", "testSchema1", (String)map.get("testSchema"));
      assertEquals("testing 1/2", "testSchema2", (String)map.get("testSchema1"));
      
      map = InfoHelper.getPropertiesStartingWith(null, info, null);
      assertEquals("testing complete info size", 4, map.size());

      log.info("SUCCESS");
   }

   /**
    * 
    */
   public final void testTableMapper() {
      log.info("Start testTableMapper");
      
      PropertiesInfo info = new PropertiesInfo(new Properties());
      
      info.put("replication.mapper.schema.AIS", "AIS1");
      info.put("replication.mapper.table.AIS.C_OUTS", "C_INS");
      info.put("replication.mapper.column.AIS.C_OUTS.COM_MESSAGEID", "COM_RECORDID");

      DefaultMapper mapper = new DefaultMapper();
      try {
         mapper.init(info);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         assertTrue("An exception should not occur when initiating the mapper '" + ex.getMessage(), false);
      }
      {
         String catalog = null;
         String schema = "AIS";
         String table = "C_OUTS";
         String column = "COM_MESSAGEID";
         String res = mapper.getMappedSchema(catalog, schema, table, column);
         assertEquals("checking schema", "AIS1", res);
         res = mapper.getMappedTable(catalog, schema, table, column);
         assertEquals("checking table", "C_INS", res);
         res = mapper.getMappedColumn(catalog, schema, table, column);
         assertEquals("checking column", "COM_RECORDID", res);
      }
      {
         String catalog = null;
         String schema = "AIS";
         String table = "C_OUTS";
         String column = "COM_RECORDID";
         String res = mapper.getMappedSchema(catalog, schema, table, column);
         assertEquals("checking schema", "AIS1", res);
         res = mapper.getMappedTable(catalog, schema, table, column);
         assertEquals("checking table", "C_INS", res);
         res = mapper.getMappedColumn(catalog, schema, table, column);
         assertEquals("checking column", "COM_RECORDID", res);
         String xmlTxt = 
            "<?xml version='1.0' encoding='UTF-8' ?>\n" + 
            "<sql>\n" + 
            "  <desc>\n" + 
            "    <command>REPLICATION</command>\n" + 
            "    <ident>16</ident>\n" + 
            "  </desc>\n" + 
            "  <row num='0'>\n" + 
            "    <col name='COM_MESSAGEID'></col>\n" + 
            "    <col name='COM_RECORDID'>55</col>\n" + 
            "    <col name='COM_TABLE' encoding='base64'>TjF8Mg==</col>\n" + 
            "    <col name='COM_COMMAND' encoding='base64'>RA==</col>\n" + 
            "    <col name='COM_TXT1'></col>\n" + 
            "    <col name='COM_TXTL' encoding='base64'>OTg0NTA5AwM=</col>\n" + 
            "    <col name='COM_CHANNEL'></col>\n" + 
            "    <col name='COM_DEBUG'></col>\n" + 
            "    <attr name='replKey'>16</attr>\n" + 
            "    <attr name='action'>INSERT</attr>\n" + 
            "    <attr name='transaction'>4.24.2232</attr>\n" + 
            "    <attr name='guid'>AAAT0EAAGAAAAKsAAA</attr>\n" + 
            "    <attr name='tableName'>C_OUTS</attr>\n" + 
            "    <attr name='schema'>AIS</attr>\n" + 
            "    <attr name='dbId'>NULL</attr>\n" + 
            "    <attr name='version'>0.0</attr>\n" + 
            "  </row>\n" + 
            "</sql>\n";
         try {
            String oldName = "COM_RECORDID";
            String newName = "COM_MESSAGEID";
            SqlInfoParser  parser = new SqlInfoParser();
            parser.init(info);
            SqlInfo sqlInfo = parser.parse(xmlTxt);
            List rows = sqlInfo.getRows();
            assertEquals("The number of rows is wrong", 1, rows.size());
            SqlRow row = (SqlRow)rows.get(0);
            log.info(row.toXml(""));
            row.renameColumn(oldName, newName);
            log.info(row.toXml(""));
         }
         catch (Exception ex) {
            ex.printStackTrace();
            assertTrue("An Exception should not occur when testing renaming of columns " + ex.getMessage(), false);
         }
      }
      {
         String catalog = null;
         String schema = "AIS";
         String table = "OTHER";
         String column = "COM_MESSAGEID";
         String res = mapper.getMappedSchema(catalog, schema, table, column);
         assertEquals("checking schema", "AIS1", res);
         res = mapper.getMappedTable(catalog, schema, table, column);
         assertEquals("checking table", "OTHER", res);
         res = mapper.getMappedColumn(catalog, schema, table, column);
         assertEquals("checking column", "COM_MESSAGEID", res);
      }
      log.info("SUCCESS");
   }
   
   /**
    * 
    */
   public final void oldTestTableToWatchInfoValues() {
      log.info("Start testTableToWatchInfoValues");
      
      String key = "table.catalog.schema.table";

      try {
         
         String val1 = null;
         TableToWatchInfo tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val1);
         assertEquals("replicate for val1", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val1", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val1", -1L, tableToWatchInfo.getReplKey());

         String val2 = "";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val2);
         assertEquals("replicate for val2", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val2", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val2", -1L, tableToWatchInfo.getReplKey());
         
         String val3 = ",,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val3);
         assertEquals("replicate for val3", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val3", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val3", -1L, tableToWatchInfo.getReplKey());

         String val4 = " , , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val4);
         assertEquals("replicate for val4", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val4", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val4", -1L, tableToWatchInfo.getReplKey());

         String val5 = "IDU,trigger,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val5);
         assertEquals("replicate for val5", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val5", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val5", 10L, tableToWatchInfo.getReplKey());

         String val6 = " IDU , trigger , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val6);
         assertEquals("replicate for val6", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val6", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val6", 10L, tableToWatchInfo.getReplKey());

         String val7 = ",trigger,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val7);
         assertEquals("replicate for val7", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val7", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val7", 10L, tableToWatchInfo.getReplKey());

         String val8 = "  , trigger , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val8);
         assertEquals("replicate for val8", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val8", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val8", 10L, tableToWatchInfo.getReplKey());

         String val9 = "IDU,,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val9);
         assertEquals("replicate for val9", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val9", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val9", 10L, tableToWatchInfo.getReplKey());

         String val10 = " IDU , , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val10);
         assertEquals("replicate for val10", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val10", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val10", 10L, tableToWatchInfo.getReplKey());

         String val11 = "IDU,trigger,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val11);
         assertEquals("replicate for val11", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val11", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val11", -1L, tableToWatchInfo.getReplKey());

         String val12 = " IDU , trigger , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val12);
         assertEquals("replicate for val12", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val12", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val12", -1L, tableToWatchInfo.getReplKey());

         String val13 = ",,10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val13);
         assertEquals("replicate for val13", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val13", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val13", 10L, tableToWatchInfo.getReplKey());

         String val14 = "  ,  , 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val14);
         assertEquals("replicate for val14", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val14", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val14", 10L, tableToWatchInfo.getReplKey());

         String val15 = ",trigger,";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val15);
         assertEquals("replicate for val15", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val15", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val15", -1L, tableToWatchInfo.getReplKey());

         String val16 = "  , trigger , ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val16);
         assertEquals("replicate for val16", false, tableToWatchInfo.isReplicate());
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

         String val5 = "actions=IDU,trigger=trigger,sequence=10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val5);
         assertEquals("replicate for val5", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val5", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val5", 10L, tableToWatchInfo.getReplKey());

         String val6 = "actions =  IDU , trigger = trigger , sequence = 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val6);
         assertEquals("replicate for val6", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val6", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val6", 10L, tableToWatchInfo.getReplKey());

         String val7 = "actions=,trigger=trigger,sequence=10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val7);
         assertEquals("replicate for val7", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val7", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val7", 10L, tableToWatchInfo.getReplKey());

         String val8 = "actions =  , trigger = trigger , sequence = 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val8);
         assertEquals("replicate for val8", false, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val8", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val8", 10L, tableToWatchInfo.getReplKey());

         String val9 = "actions=IDU,sequence=10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val9);
         assertEquals("replicate for val9", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val9", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val9", 10L, tableToWatchInfo.getReplKey());

         String val10 = " actions = IDU , sequence = 10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val10);
         assertEquals("replicate for val10", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val10", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val10", 10L, tableToWatchInfo.getReplKey());

         String val11 = "actions=IDU,trigger=trigger";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val11);
         assertEquals("replicate for val11", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val11", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val11", -1L, tableToWatchInfo.getReplKey());

         String val12 = " actions = IDU , trigger = trigger";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val12);
         assertEquals("replicate for val12", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val12", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val12", -1L, tableToWatchInfo.getReplKey());

         String val13 = "sequence=10";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val13);
         assertEquals("replicate for val13", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val13", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val13", 10L, tableToWatchInfo.getReplKey());

         String val14 = " sequence =   10 ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val14);
         assertEquals("replicate for val14", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val14", (String)null, tableToWatchInfo.getTrigger());
         assertEquals("replKey for val14", 10L, tableToWatchInfo.getReplKey());

         String val15 = "trigger=trigger";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val15);
         assertEquals("replicate for val15", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val15", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val15", -1L, tableToWatchInfo.getReplKey());

         String val16 = " trigger =  trigger  ";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val16);
         assertEquals("replicate for val16", true, tableToWatchInfo.isReplicate());
         assertEquals("trigger for val16", "trigger", tableToWatchInfo.getTrigger());
         assertEquals("replKey for val16", -1L, tableToWatchInfo.getReplKey());
         
         String val17 = "actions=IDU";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val17);
         assertEquals("replicate for val17", "IDU", tableToWatchInfo.getActions());
         
         String val18 = "actions=U";
         tableToWatchInfo = new TableToWatchInfo();
         tableToWatchInfo.assignFromInfoPair(key, val18);
         assertEquals("replicate for val17", "U", tableToWatchInfo.getActions());
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
      info.put("table.schema1.table1", "IDU");
      info.put("table.schema1.table2", "IDU,trigger2,10");
      info.put("table.schema1.table3", "IDU,trigger3,155");
      info.put("table.schema1.table4", "IDU,trigger4,6");
      info.put("table.schema1.table5", "IDU,trigger5,13");
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
