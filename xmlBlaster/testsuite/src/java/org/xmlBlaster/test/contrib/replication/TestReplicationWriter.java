/*------------------------------------------------------------------------------
Name:      TestReplicationWriter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.DbPool;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.I_Writer;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.ReplicationWriter;

/**
 * Tests the functionality of the ReplicationWriter. 
 * 
 * <pre>
 * java -Ddb=oracle .....
 * or if you want to use postgres:
 * java -Ddb=postgres
 * </pre>
 * <p>
 * <h2>What does this test ?</h2><br/>
 * <ul>
 *   <li>This test runs without the need of an xmlBlaster server, everything is checked internally.</li>
 *   <li>From an xml statement it creates a DbUpdateInfo object which is then executed on the database.
 *       This way the 'store' operation of this class is tested.
 *   </li>
 * </ul>
 * 
 * @author Michele Laghi
 */
public class TestReplicationWriter extends XMLTestCase {
    private static Logger log = Logger.getLogger(TestReplicationWriter.class.getName());
    private I_Info info;
    private I_DbPool dbPool;
    private SpecificHelper specificHelper; // this is static since the implementation of I_ChangePublisher is another instance
    DbMetaHelper dbHelper;
    private I_DbSpecific dbSpecific;
    private I_Writer replicationWriter;
    private String tableName;
    
    /**
     * Start the test. 
     * <pre>
     * java -Ddb=oracle junit.swingui.TestRunner -noloading org.xmlBlaster.test.contrib.replication.TestReplicationWriter
     * </pre>
     * @param args Command line settings
     */
    public static void main(String[] args) {
        // junit.swingui.TestRunner.run(TestReplicationWriter.class);
       TestReplicationWriter test = new TestReplicationWriter();
       
       try {
          test.setUp();
          test.testCreateSeq1();
          test.tearDown();

          test.setUp();
          test.testCreateSeq2();
          test.tearDown();
          
          test.setUp();
          test.testCreateSeq3();
          test.tearDown();
          
          test.setUp();
          test.testCreateSeq4();
          test.tearDown();
          
          test.setUp();
          test.testCreateSeq5();
          test.tearDown();
          
          /*
          test.setUp();
          test.testCreateSeq6();
          test.tearDown();
          */
          
          test.setUp();
          test.testCreateSeq7();
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
    public TestReplicationWriter() {
       super(); 
       XMLUnit.setIgnoreWhitespace(true);
    }

   /**
    * Constructor for TestReplicationWriter.
    * @param arg0
    */
    public TestReplicationWriter(String arg0) {
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
      this.dbPool = setUpDbPool(this.info);
      this.dbSpecific = ReplicationConverter.getDbSpecific(this.info);
      Connection conn = null;
      try {
         conn = dbPool.reserve();
         this.dbHelper = new DbMetaHelper(this.dbPool);
         this.tableName = this.dbHelper.getIdentifier("TEST_WRITER");
         log.info("setUp: going to cleanup now ...");
         this.dbSpecific.cleanup(conn, false);
         log.info("setUp: cleanup done, going to bootstrap now ...");
         boolean doWarn = false;
         boolean force = true;
         this.dbSpecific.bootstrap(conn, doWarn, force);
         this.replicationWriter = new ReplicationWriter();
         this.replicationWriter.init(this.info);
      }
      catch (Exception ex) {
         if (conn != null)
            dbPool.release(conn);
      }
   }
   
   public void init(I_Info info) throws Exception {
   }

   /**
    * Used to test the feature.
    * @param method The invoking method name.
    * @param message The xml message to parse and process.
    * @param tableName The name of the table to create.
    */
   private final void createSeq(String method, String message, String tableName) {
      try {
         Map map = new HashMap();
         map.put("tableName", this.tableName);
         map.put("schemaName", this.specificHelper.getOwnSchema(this.dbPool));
         message = this.specificHelper.replace(message, map);
         log.info(method + " START");
         // first clean up the table
         try {
            this.dbPool.update("DROP TABLE " + tableName);
         }
         catch (Exception e) {
         }
         
         // check if really empty
         Connection conn = null;
         try {
            conn = this.dbPool.reserve();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT * from " + tableName);
            rs.close();
            st.close();
            assertTrue("Testing if the table '" + tableName + "' really has been deleted before starting the tests", false);
         }
         catch (Exception e) {
         }
         finally {
            if (conn != null)
               this.dbPool.release(conn);
            conn = null;
         }
         
         // first check parsing (if an assert occurs here it means there is a discrepancy between toXml and parse
         DbUpdateParser parser = new DbUpdateParser(info);

         DbUpdateInfo dbUpdateInfo = parser.readObject(message);
         String sql = this.dbSpecific.getCreateTableStatement(dbUpdateInfo.getDescription(), null);
         try {
            this.replicationWriter.store(dbUpdateInfo);
         }
         catch (Exception e) {
            e.printStackTrace();
            assertTrue("when testing '" + sql + "': " + e.getMessage(), false);
         }
         // verify that it really has been stored
         try {
            conn = this.dbPool.reserve();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery("SELECT * from " + tableName);
            assertEquals("The table '" + tableName + "' exists but was not empty when testing '" + sql + "'", false, rs.next());
            rs.close();
            st.close();
         }
         catch (Exception e) {
            assertTrue("Testing if the table '" + tableName + "' for '" + sql + "' failed because the table was not created", false);
         }
         finally {
            if (conn != null)
               this.dbPool.release(conn);
            conn = null;
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
         fail();
      }
   }

   public void shutdown() {
   }

   /**
    * Creates a database pooling instance and puts it to info. 
    * @param info The configuration
    * @return The created pool
    */
   private DbPool setUpDbPool(I_Info info) {
      DbPool dbPool = new DbPool();
      dbPool.init(info);
      info.putObject("db.pool", dbPool);
      return dbPool;
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
   }

   
   public void testCreateSeq1() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CHAR' precision='10' nullable='0' sqlType='1' colSize='10' radix='10' charLength='10' pos='1' label='ONE' typeName='CHAR' caseSens='true' pk='true' pkName='SYS_C007059' dataType='CHAR'>ONE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='5' nullable='0' sqlType='12' colSize='5' radix='10' charLength='5' pos='2' label='TWO' typeName='VARCHAR2' caseSens='true' pk='true' pkName='SYS_C007059' dataType='VARCHAR'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='30' nullable='1' sqlType='12' colSize='30' radix='10' charLength='30' pos='3' label='THREE' typeName='VARCHAR2' caseSens='true' dataType='VARCHAR'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NCHAR' precision='30' nullable='1' sqlType='1111' colSize='60' radix='10' charLength='60' pos='4' label='FOUR' typeName='CHAR' caseSens='true' dataType='OTHER'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NCHAR' precision='20' nullable='1' sqlType='1111' colSize='40' radix='10' charLength='40' pos='5' label='FIVE' typeName='CHAR' caseSens='true' dataType='OTHER'>FIVE</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>1</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq1", message, this.tableName);
   }
   
   public void testCreateSeq2() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='LONG' precision='2147483647' nullable='1' sqlType='-1' radix='10' pos='1' label='ONE' typeName='LONG' caseSens='true' dataType='LONGVARCHAR'>ONE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' precision='10' scale='3' nullable='1' sqlType='3' colSize='10' radix='10' charLength='22' pos='2' label='TWO' typeName='NUMBER' dataType='DECIMAL'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' precision='38' nullable='0' sqlType='3' colSize='22' radix='10' charLength='22' pos='3' label='THREE' typeName='NUMBER' pk='true' pkName='SYS_C007061' dataType='DECIMAL'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' precision='38' nullable='1' sqlType='3' colSize='22' radix='10' charLength='22' pos='4' label='FOUR' typeName='NUMBER' dataType='DECIMAL'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='FLOAT' precision='3' scale='-127' nullable='1' sqlType='6' colSize='3' radix='10' charLength='22' pos='5' label='FIVE' typeName='NUMBER' dataType='FLOAT'>FIVE</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>2</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq2", message, this.tableName);
   }
   
   public void testCreateSeq3() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='10' nullable='0' sqlType='12' colSize='10' radix='10' charLength='10' pos='1' label='TWO' typeName='VARCHAR2' caseSens='true' pk='true' pkName='SYS_C007063' dataType='VARCHAR'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='10' nullable='0' sqlType='12' colSize='10' radix='10' charLength='10' pos='2' label='THREE' typeName='VARCHAR2' caseSens='true' pk='true' pkName='SYS_C007063' dataType='VARCHAR'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='10' nullable='0' sqlType='12' colSize='10' radix='10' charLength='10' pos='3' label='FOUR' typeName='VARCHAR2' caseSens='true' pk='true' pkName='SYS_C007063' dataType='VARCHAR'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='VARCHAR2' precision='10' nullable='1' sqlType='12' colSize='10' radix='10' charLength='10' pos='4' label='EIGHT' typeName='VARCHAR2' caseSens='true' dataType='VARCHAR'>EIGHT</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>3</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq3", message, this.tableName);
   }

   public void testCreateSeq4() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CHAR' precision='1' nullable='1' sqlType='1' colSize='1' radix='10' charLength='1' pos='1' label='ONE' typeName='CHAR' caseSens='true' dataType='CHAR'>ONE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CHAR' precision='10' nullable='1' sqlType='1' colSize='10' radix='10' charLength='10' pos='2' label='TWO' typeName='CHAR' caseSens='true' dataType='CHAR'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CHAR' precision='10' nullable='1' sqlType='1' colSize='10' radix='10' charLength='10' pos='3' label='THREE' typeName='CHAR' caseSens='true' dataType='CHAR'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CHAR' precision='10' nullable='1' sqlType='1' colSize='10' radix='10' charLength='10' pos='4' label='FOUR' typeName='CHAR' caseSens='true' dataType='CHAR'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NCHAR' precision='1' nullable='1' sqlType='1111' colSize='2' radix='10' charLength='2' pos='5' label='FIVE' typeName='CHAR' caseSens='true' dataType='OTHER'>FIVE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NCHAR' precision='10' nullable='1' sqlType='1111' colSize='20' radix='10' charLength='20' pos='6' label='SIX' typeName='CHAR' caseSens='true' dataType='OTHER'>SIX</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='CLOB' nullable='1' sqlType='2005' colSize='4000' radix='10' charLength='4000' pos='7' label='SEVEN' typeName='CLOB' dataType='CLOB'>SEVEN</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NCLOB' nullable='1' sqlType='1111' colSize='4000' radix='10' charLength='4000' pos='8' label='EIGHT' typeName='CLOB' dataType='OTHER'>EIGHT</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='BLOB' nullable='1' sqlType='2004' colSize='4000' radix='10' charLength='4000' pos='9' label='NINE' typeName='BLOB' dataType='BLOB'>NINE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='BFILE' nullable='1' sqlType='-13' colSize='530' radix='10' charLength='530' pos='10' label='TEN' typeName='BFILE' dataType='UNKNOWN'>TEN</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>4</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq4", message, this.tableName);
   }
   
   public void testCreateSeq5() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' scale='-127' nullable='1' sqlType='3' colSize='22' radix='10' charLength='22' pos='1' label='ONE' typeName='NUMBER' dataType='DECIMAL'>ONE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' precision='3' nullable='1' sqlType='3' colSize='3' radix='10' charLength='22' pos='2' label='TWO' typeName='NUMBER' dataType='DECIMAL'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='NUMBER' precision='3' scale='2' nullable='1' sqlType='3' colSize='3' radix='10' charLength='22' pos='3' label='THREE' typeName='NUMBER' dataType='DECIMAL'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='LONG' precision='2147483647' nullable='1' sqlType='-1' radix='10' pos='4' label='FOUR' typeName='LONG' caseSens='true' dataType='LONGVARCHAR'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='DATE' nullable='1' sqlType='91' colSize='7' radix='10' charLength='7' pos='5' label='FIVE' typeName='DATE' dataType='DATE'>FIVE</colname>\n" +
// The following would work on oracle 10 but does not on 8.1.6                       
//                       "     <colname table='${tableName}' schema='${schemaName}' type='BINARY_FLOAT' nullable='1' sqlType='100' colSize='4' radix='10' charLength='4' pos='6' label='SIX' typeName='BINARY_FLOAT' dataType='UNKNOWN'>SIX</colname>\n" +
//                       "     <colname table='${tableName}' schema='${schemaName}' type='BINARY_DOUBLE' nullable='1' sqlType='101' colSize='8' radix='10' charLength='8' pos='7' label='SEVEN' typeName='BINARY_DOUBLE' dataType='UNKNOWN'>SEVEN</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>5</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq5", message, this.tableName);
   }
   
   /* does not work on ora 8.1.6
   public void testCreateSeq6() {
      String tableName = "${tableName}";
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(6)' scale='1' nullable='1' sqlType='93' colSize='11' radix='10' charLength='11' pos='1' label='ONE' typeName='TIMESTAMP' dataType='TIMESTAMP'>ONE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(2)' scale='1' nullable='1' sqlType='1111' colSize='11' radix='10' charLength='11' pos='2' label='TWO' typeName='TIMESTAMP' dataType='OTHER'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(6) WITH TIME ZONE' scale='1' nullable='1' sqlType='-101' colSize='13' radix='10' charLength='13' pos='3' label='THREE' typeName='TIMESTAMPTZ' dataType='UNKNOWN'>THREE</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(2) WITH TIME ZONE' scale='1' nullable='1' sqlType='1111' colSize='13' radix='10' charLength='13' pos='4' label='FOUR' typeName='TIMESTAMPTZ' dataType='OTHER'>FOUR</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(6) WITH LOCAL TIME ZONE' scale='1' nullable='1' sqlType='-102' colSize='11' radix='10' charLength='11' pos='5' label='SIX' typeName='TIMESTAMPLTZ' dataType='UNKNOWN'>SIX</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='TIMESTAMP(2) WITH LOCAL TIME ZONE' scale='1' nullable='1' sqlType='1111' colSize='11' radix='10' charLength='11' pos='6' label='SEVEN' typeName='TIMESTAMPLTZ' dataType='OTHER'>SEVEN</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>6</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq6", message, tableName);
   }
   */
   
   public void testCreateSeq7() {
      String message = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                       " <sql>\n" +
                       "   <desc>\n" +
                       "     <command>CREATE</command>\n" +
                       "     <ident>${tableName}</ident>\n" +
//                       "     <colname table='${tableName}' schema='${schemaName}' type='INTERVAL YEAR(2) TO MONTH' precision='2' nullable='1' sqlType='-103' colSize='2' radix='10' charLength='5' pos='1' label='ONE' typeName='INTERVALYM' dataType='UNKNOWN'>ONE</colname>\n" +
//                       "     <colname table='${tableName}' schema='${schemaName}' type='INTERVAL YEAR(3) TO MONTH' precision='3' nullable='1' sqlType='1111' colSize='3' radix='10' charLength='5' pos='2' label='TWO' typeName='INTERVALYM' dataType='OTHER'>TWO</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='RAW' nullable='1' sqlType='-3' colSize='200' radix='10' charLength='200' pos='3' label='SEVEN' typeName='RAW' dataType='VARBINARY'>SEVEN</colname>\n" +
                       "     <colname table='${tableName}' schema='${schemaName}' type='LONG RAW' precision='2147483647' nullable='1' sqlType='-4' radix='10' pos='4' label='EIGHT' typeName='LONG RAW' dataType='LONGVARBINARY'>EIGHT</colname>\n" +
//                       "     <colname table='${tableName}' schema='${schemaName}' type='ROWID' nullable='1' sqlType='1111' colSize='10' radix='10' charLength='10' pos='5' label='NINE' typeName='ROWID' dataType='OTHER'>NINE</colname>\n" +
//                       "     <colname table='${tableName}' schema='${schemaName}' type='UROWID' nullable='1' sqlType='1111' colSize='4000' radix='10' charLength='4000' pos='6' label='TEN' dataType='OTHER'>TEN</colname>\n" +
                       "     <attr name='_createCounter' type='int'>0</attr>\n" +
                       "     <attr name='extraReplKey'>7</attr>\n" +
                       "     <attr name='action'>CREATE</attr>\n" +
                       "     <attr name='tableName'>${tableName}</attr>\n" +
                       "     <attr name='replKey'>100</attr>\n" +
                       "     <attr name='transaction'>100.1001</attr>\n" +
                       "     <attr name='dbId'>db</attr>\n" +
                       "     <attr name='guid'>1000100101</attr>\n" +
                       "     <attr name='schema'>${schemaName}</attr>\n" +
                       "     <attr name='version'>0.0</attr>\n" +
                       "   </desc>\n" +
                       " </sql>\n";
      createSeq("testCreateSeq7", message, this.tableName);
   }
   
}
