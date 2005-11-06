/*------------------------------------------------------------------------------
Name:      TestRecordParsing.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.dbwriter;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.Info;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;

import java.util.logging.Logger;
import java.util.prefs.Preferences;
import java.util.List;

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
public class TestRecordParsing extends XMLTestCase {
    private static Logger log = Logger.getLogger(TestRecordParsing.class.getName());
    private I_Info info;
    
    /**
     * Start the test. 
     * <pre>
     * java -Ddb.password=secret junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.dbwriter.TestRecordParsing
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestRecordParsing.class);

       TestRecordParsing test = new TestRecordParsing();
       try {
          test.setUp();
          test.testParsing();
          test.tearDown();
       }
       catch (Exception ex) {
          ex.printStackTrace();
       }
    }

    /**
     * Default ctor. 
     */
    public TestRecordParsing() {
       super();
       Preferences prefs = Preferences.userRoot();
       this.info = new Info(prefs);
       XMLUnit.setIgnoreWhitespace(true);
    }

   /**
    * Constructor for TestRecordParsing.
    * @param arg0
    */
    public TestRecordParsing(String arg0) {
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
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void testParsing() throws Exception {
      log.info("Start testParsing()");
      /** Comments are not allowed otherwise the xml are not considered the same */
      String xml = "" + 
      "<?xml version='1.0' encoding='UTF-8' ?>\n" +
      "<sql>\n" +
      " <desc>\n" +
      "  <command>INSERT</command>\n" +
      "  <ident>EDDI</ident>\n" +
      "  <colname type='DATE' nullable='0'>DATUM</colname>\n" +
      "  <colname type='NUMBER' precision='11' scale='0'\n" +
      "              signed='false' nullable='1'>CPU</colname>\n" +
      "  <colname type='VARCHAR2' precision='20' nullable='0' readOnly='true'>COL1</colname>\n" +
      "  <colname table='OMZ' nullable='0' schema='AA' catalog='CAT' type='VARCHAR2'\n" +
      "              precision='10' pk='true' fkCatalog='dummy' fkSchema='dummy1' fkTable='fkTab'" +
      " fkCol='colName' fkSeq='1' fkUpdRule='none' fkDelRule='some' fkDef='somedef'>ICAO_ID</colname>\n" +
      "  <attr name='TEST3'>SOMEATTR3</attr>\n" +
      "  <attr name='TEST1'>SOMEATTR1</attr>\n" +
      " </desc>\n" +
      " <row num='0'>\n" +
      "  <col name='DATUM'>2005-01-05 15:52:06.0</col>\n" +
      "  <col name='CPU'>238333</col>\n" +
      "  <col name='COL1'>Bla</col>\n" +
      "  <col name='ICAO_ID'>EDDI</col>\n" +
      "  <attr name='LR'>SRANIL</attr>\n" +
      "  <attr name='SUBNET_ID'>TCP</attr>\n" +
      " </row>\n" +
      " <row num='1'>\n" +
      "  <col name='DATUM'>2005-01-05 15:52:07.0</col>\n" +
      "  <col name='CPU'>238340</col>\n" +
      "  <col name='COL1' encoding='base64'>QmxdXT5CbA==</col>\n" +
      "  <col name='ICAO_ID'>EDDI</col>\n" +
      " </row>\n" +
      " <row num='2'>\n" +
      "  <col name='DATUM'>2005-01-05 15:52:08.0</col>\n" +
      "  <col name='CPU'>238343</col>\n" +
      "  <col name='COL1'>BOO</col>\n" +
      "  <col name='ICAO_ID'>EDDI</col>\n" +
      "  <attr name='SUBNET_ID'>X25</attr>\n" +
      " </row>\n" +
      "</sql>      \n";

      
      DbUpdateParser parser = new DbUpdateParser(this.info);
      SqlInfo record = parser.readObject(xml);

      SqlDescription description = record.getDescription();
      assertNotNull("the description shall not be null", description);
      assertNotNull("the identity shall not be null", description.getIdentity());
      assertNotNull("the command shall not be null", description.getCommand());

      assertEquals("the identity content is wrong", "EDDI", description.getIdentity());
      assertEquals("the command content is wrong", "INSERT", description.getCommand());

      // test the column descriptions 
      SqlColumn[] colDescriptions = description.getColumns();
      assertEquals("the number of column descriptions is wrong", 4, colDescriptions.length);
      String[] names = new String[] { "DATUM", "CPU", "COL1", "ICAO_ID"};
      for (int i=0; i < colDescriptions.length; i++) {
         log.info("test column description #" + i + " names: '" + names[i] + "' and '" + colDescriptions[i].getColName() + "'");
         assertEquals("the name of the column description #" + i + " is wrong", names[i], colDescriptions[i].getColName());
      }
      
      List rows = record.getRows();
      assertEquals("the number of rows is wrong", 3, rows.size());
      int[] attr = new int[] { 2, 0, 1 };
      for (int i=0; i < 3; i++) {
         SqlRow row = (SqlRow)rows.get(i);
         assertEquals("wrong number of columns for row '" + i+ "'", 4, row.getColumnNames().length);
         assertEquals("wrong number of attributes for row '" + i+ "'", attr[i], row.getAttributeNames().length);
      }
      
      System.out.println("\n\nshould be:\n" + xml);
      System.out.println("\nis:\n" + record.toXml(""));
      assertXMLEqual("output xml is not the same as input xml", xml, record.toXml(""));
      //assertXpathNotExists("/myRootTag/row[@num='0']", xml);
      //assertXpathEvaluatesTo("CREATE", "/myRootTag/desc/command/text()", xml);
      log.info("SUCCESS");
   }
   

}
