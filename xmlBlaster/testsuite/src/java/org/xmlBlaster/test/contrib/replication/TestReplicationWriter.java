/*------------------------------------------------------------------------------
Name:      TestReplicationWriter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.ReplicationWriter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.util.qos.ClientProperty;

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
 *   <li>From an xml statement it creates a SqlInfo object which is then executed on the database.
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
    private long sleepDelay;
    
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
       String path = System.getProperty("java.class.path");
       if (path == null)
          path = "";
       System.out.println("THE PATH IS: " + path);
       try {
          
          test.setUp();
          test.testReadAllTables();
          test.tearDown();
/*          
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
*/          
          /*
          test.setUp();
          test.testCreateSeq6();
          test.tearDown();
          */
/*          
          test.setUp();
          test.testCreateSeq7();
          test.tearDown();
*/          
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
      this.sleepDelay = info.getLong("test.sleepDelay", 0L);
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

         SqlInfo dbUpdateInfo = parser.readObject(message);
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

   
   private String displayInfo(Object obj) {
      try {
         String clazzName = obj.getClass().getName();
         Class clazz = java.lang.Class.forName(clazzName);
         StringBuffer results = new StringBuffer();
         org.xmlBlaster.util.classloader.ClassLoaderUtils.displayClassInfo(clazz, results);
         return results.toString();
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return "";
      }
   }
   
   public void processOneTable(Connection conn, String schemaName, String tableName) throws Exception {
      ResultSet rs = null;
      Statement st = null;
      try {
         String name = null;
         if (schemaName != null)
            name = schemaName + "." + tableName;
         else
            name = tableName;
         try {
            String sql = "SELECT * from " + name;
            conn.setAutoCommit(true);
            st = conn.createStatement();
            rs = st.executeQuery(sql);
         }
         catch (SQLException ex) {
            log.info("The Table '" + name + "' could not be processed since not found on the DB");
            if (rs != null)
               rs.close();
            if (st != null)
               st.close();
            return;
         }
         SqlInfo obj = new SqlInfo(this.info);
         obj.fillMetadata(conn, null, schemaName, tableName, rs, null);

         int maxCount = 20;
         int count = 0;
         while (rs.next() && count < maxCount) {
            obj.fillOneRowWithObjects(rs, null);
            count++;
         }

         rs.close();
         st.close();
         // obj.getDescription().addPreparedStatements();

         count = obj.getRowCount();
         if (count == 0) {
            log.info("Could not test '" + name + "' since empty");
         }
         else {
            List rows = obj.getRows();
            SqlDescription desc = obj.getDescription();

            conn.setAutoCommit(false);
            try {
               for (int i=0; i < rows.size(); i++) {
                  SqlRow row = (SqlRow)rows.get(i);
                  ClientProperty oldRowProp = new ClientProperty(ReplicationConstants.OLD_CONTENT_ATTR, null, null, row.toXml("", false));
                  row.setAttribute(oldRowProp);
                  try {
                     int ret = desc.update(conn, row);
                     if (ret != 1)
                        throw new Exception("the number of updated entries is wrong '" + ret + "' but should be 1");
                  }
                  catch(Exception ex) {
                     log.info("exception when updating '" + row.toXml("") + " where description is '" + desc.toXml("") + "'");
                     throw ex;
                  }
                  try {
                     int ret = desc.delete(conn, row);
                     if (ret != 1)
                        throw new Exception("the number of deleted entries is wrong '" + ret + "' but should be 1");
                  }
                  catch(Exception ex) {
                     log.info("exception when deleting '" + row.toXml("") + " where description is '" + desc.toXml("") + "'");
                     throw ex;
                  }
                  try {
                     int ret = desc.insert(conn, row);
                     if (ret != 1)
                        throw new Exception("the number of inserted entries is wrong '" + ret + "' but should be 1");
                  }
                  catch(Exception ex) {
                     log.info("exception when inserting '" + row.toXml("") + " where description is '" + desc.toXml("") + "'");
                     throw ex;
                  }
               }
               Thread.sleep(this.sleepDelay);
               conn.commit();
            }
            catch (Exception ex) {
               if (conn != null)
                  conn.rollback();
            }
            finally {
               if (conn != null)
                  conn.setAutoCommit(true);
            }
         }
         
         // System.out.println("\n\n");
         // System.out.println(obj.getDescription().toXml(""));
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   public void testReadAllTables() {

      PropertiesInfo info = new PropertiesInfo(new Properties());

      info.put("table.ais.test001", "IDU,ndb001tr,1");
      info.put("table.ais.test002", "IDU,ndb002tr,2");
      info.put("table.ais.test003", "IDU,,ndb003tr,2");
      info.put("table.AIS.AD_ICAO_LOCATIONS", "IDU,REPL_NDB_001");
      info.put("table.AIS.AERODROME_RUNWAYS", "IDU,REPL_NDB_002");
      info.put("table.AIS.SIDS", "IDU,REPL_NDB_003");
      info.put("table.AIS.STARS", "IDU,REPL_NDB_004");
      info.put("table.AIS.NAVAIDS", "IDU,REPL_NDB_005");
      info.put("table.AIS.WAYPOINTS", "IDU,REPL_NDB_006");
      info.put("table.AIS.FIR_ICAO_LOCATIONS", "IDU,REPL_NDB_007");
      info.put("table.AIS.FIR_NEIGHBOURS", "IDU,REPL_NDB_008");
      info.put("table.AIS.FIR_ADDITIONAL_INFOS", "IDU,REPL_NDB_009");
      info.put("table.AIS.FAI_POINTS", "IDU,REPL_NDB_010");
      info.put("table.AIS.RESTRICTIVE_AIRSPACES", "IDU,REPL_NDB_011");
      info.put("table.AIS.RAREA_POINTS", "IDU,REPL_NDB_012");
      info.put("table.AIS.RAREA_FIRS", "IDU,REPL_NDB_013");
      info.put("table.AIS.RAREA_AWYWPS", "IDU,REPL_NDB_014");
      info.put("table.AIS.AIRWAYS", "IDU,REPL_NDB_015");
      info.put("table.AIS.AIRWAY_WAYPOINTS", "IDU,REPL_NDB_016");
      info.put("table.AIS.NOF_ICAO_LOCATIONS", "IDU,REPL_NDB_017");
      info.put("table.AIS.NOF_SERIES", "IDU,REPL_NDB_018");
      info.put("table.AIS.NOF_NATS", "IDU,REPL_NDB_019");
      info.put("table.AIS.NOF_FIRS", "IDU,REPL_NDB_020");
      info.put("table.AIS.SELECTION_CRITERIAS", "IDU,REPL_NDB_021");
      info.put("table.AIS.DECODE23S", "IDU,REPL_NDB_022");
      info.put("table.AIS.DECODE45S", "IDU,REPL_NDB_023");
      info.put("table.AIS.ICAO_NATIONAL_LETTERS", "IDU,REPL_NDB_024");
      info.put("table.AIS.AGENCIES", "IDU,REPL_NDB_025");
      info.put("table.AIS.AIRCRAFTS", "IDU,REPL_NDB_026");
      info.put("table.AIS.AIS_POOLS", "IDU,REPL_NDB_027");
      info.put("table.AIS.QUERY_USERS", "IDU,REPL_NDB_028");
      info.put("table.AIS.USER_PIPES", "IDU,REPL_NDB_029");
      info.put("table.AIS.DEVICE_DESCRIPTIONS", "IDU,REPL_NDB_030");
      info.put("table.AIS.DEVICE_FORMS", "IDU,REPL_NDB_031");
      info.put("table.AIS.PROCESSREGIONS", "IDU,REPL_NDB_032");
      info.put("table.AIS.AD_RESPS", "IDU,REPL_NDB_033");
      info.put("table.AIS.CALL_SIGNS", "IDU,REPL_NDB_034");
      info.put("table.AIS.AFOD_ADDRESSES", "IDU,REPL_NDB_035");
      info.put("table.AIS.FIR_FPL_ADDRS", "IDU,REPL_NDB_036");
      info.put("table.AIS.HOSTS", "IDU,REPL_NDB_037");
      info.put("table.AIS.TERMINAL_USERS", "IDU,REPL_NDB_038");
      info.put("table.AIS.PARAMETERS", "IDU,REPL_NDB_039");
      info.put("table.AIS.OPERATORS", "IDU,REPL_NDB_040");
      info.put("table.AIS.OP_PRIVILEGES", "IDU,REPL_NDB_041");
      info.put("table.AIS.NOTAM", "IDU,REPL_NDB_042");
      info.put("table.AIS.NCB_AIRWAYS", "IDU,REPL_NDB_043");
      info.put("table.AIS.NCB_AWYWPS", "IDU,REPL_NDB_044");
      info.put("table.AIS.NCB_COORS", "IDU,REPL_NDB_045");
      info.put("table.AIS.NCB_RAREAS", "IDU,REPL_NDB_046");
      info.put("table.AIS.NCB_RETR_KEYS", "IDU,REPL_NDB_047");
      info.put("table.AIS.NCB_TEXT_LINES", "IDU,REPL_NDB_048");
      info.put("table.AIS.NCB_RUNWAYS", "IDU,REPL_NDB_049");
      info.put("table.AIS.NOTAM_CONTROL_BLOCKS", "IDU,REPL_NDB_050");
      info.put("table.AIS.FPL", "IDU,REPL_NDB_051");
      info.put("table.AIS.FCBS", "IDU,REPL_NDB_052");
      info.put("table.AIS.FCB_AWYWPS", "IDU,REPL_NDB_054");
      info.put("table.AIS.SNOWTAM_CONTROL_BLOCKS", "IDU,REPL_NDB_056");
      info.put("table.AIS.SNOWTAM_RUNWAYS", "IDU,REPL_NDB_057");
      info.put("table.AIS.AF_LOCATION_STATUSES", "IDU,REPL_NDB_058");
      info.put("table.AIS.LR_MCBS", "IDU,REPL_NDB_059");
      info.put("table.AIS.LR_NCBS", "IDU,REPL_NDB_060");
      info.put("table.AIS.LR_SCBS", "IDU,REPL_NDB_061");
      info.put("table.AIS.LR_FCBS", "IDU,REPL_NDB_062");
      info.put("table.AIS.LR_RETRS", "IDU,REPL_NDB_063");
      info.put("table.AIS.LR_MCB_TEXTS", "IDU,REPL_NDB_064");
      info.put("table.AIS.LR_NCB_TEXTS", "IDU,REPL_NDB_065");
      info.put("table.AIS.LR_SCB_TEXTS", "IDU,REPL_NDB_066");
      info.put("table.AIS.LR_FCB_TEXTS", "IDU,REPL_NDB_067");
      info.put("table.AIS.LR_PIBS", "IDU,REPL_NDB_068");
      info.put("table.AIS.R_AD_ICAO_LOCATIONS", "IDU,REPL_NDB100");
      info.put("table.AIS.R_AD_RESPS", "IDU,REPL_NDB101");
      info.put("table.AIS.R_AERODROME_RUNWAYS", "IDU,REPL_NDB102");
      info.put("table.AIS.R_AF_LOCATION_STATUSES", "IDU,REPL_NDB103");
      info.put("table.AIS.R_AGENCIES", "IDU,REPL_NDB104");
      info.put("table.AIS.R_AIRCRAFTS", "IDU,REPL_NDB105");
      info.put("table.AIS.R_AIRWAYS", "IDU,REPL_NDB106");
      info.put("table.AIS.R_AIRWAY_WAYPOINTS", "IDU,REPL_NDB107");
      info.put("table.AIS.R_AIS_POOLS", "IDU,REPL_NDB108");
      info.put("table.AIS.R_DECODE23S", "IDU,REPL_NDB109");
      info.put("table.AIS.R_DECODE45S", "IDU,REPL_NDB110");
      info.put("table.AIS.R_DEVICE_DESCRIPTIONS", "IDU,REPL_NDB111");
      info.put("table.AIS.R_DEVICE_FORMS", "IDU,REPL_NDB112");
      info.put("table.AIS.R_FAI_POINTS", "IDU,REPL_NDB113");
      info.put("table.AIS.R_FCBS", "IDU,REPL_NDB114");
      info.put("table.AIS.R_FCB_ADDRESSS", "IDU,REPL_NDB115");
      info.put("table.AIS.R_FCB_AWYWPS", "IDU,REPL_NDB116");
      info.put("table.AIS.R_FCB_ROUTES", "IDU,REPL_NDB117");
      info.put("table.AIS.R_FCB_TEXT_LINES", "IDU,REPL_NDB118");
      info.put("table.AIS.R_FIR_ADDITIONAL_INFOS", "IDU,REPL_NDB119");
      info.put("table.AIS.R_FIR_FPL_ADDRS", "IDU,REPL_NDB120");
      info.put("table.AIS.R_FIR_ICAO_LOCATIONS", "IDU,REPL_NDB121");
      info.put("table.AIS.R_FIR_NEIGHBOURS", "IDU,REPL_NDB122");
      info.put("table.AIS.R_ICAO_NATIONAL_LETTERS", "IDU,REPL_NDB123");
      info.put("table.AIS.R_MAP_NOTAM_SYMBOLS", "IDU,REPL_NDB124");
      info.put("table.AIS.R_MAP_Q_CODE23S", "IDU,REPL_NDB125");
      info.put("table.AIS.R_NAVAIDS", "IDU,REPL_NDB126");
      info.put("table.AIS.R_NCB_AIRWAYS", "IDU,REPL_NDB127");
      info.put("table.AIS.R_NCB_AWYWPS", "IDU,REPL_NDB128");
      info.put("table.AIS.R_NCB_COORS", "IDU,REPL_NDB129");
      info.put("table.AIS.R_NCB_RAREAS", "IDU,REPL_NDB130");
      info.put("table.AIS.R_NCB_RETR_KEYS", "IDU,REPL_NDB131");
      info.put("table.AIS.R_NCB_TEXT_LINES", "IDU,REPL_NDB132");
      info.put("table.AIS.R_NCB_RUNWAYS", "IDU,REPL_NDB_150");

      info.put("table.AIS.R_NOF_FIRS", "IDU,REPL_NDB133");
      info.put("table.AIS.R_NOF_ICAO_LOCATIONS", "IDU,REPL_NDB134");
      info.put("table.AIS.R_NOF_NATS", "IDU,REPL_NDB135");
      info.put("table.AIS.R_NOF_SERIES", "IDU,REPL_NDB136");
      info.put("table.AIS.R_NOTAM_CONTROL_BLOCKS", "IDU,REPL_NDB137");
      info.put("table.AIS.R_PROCESSREGIONS", "IDU,REPL_NDB138");
      info.put("table.AIS.R_QUERY_USERS", "IDU,REPL_NDB139");
      info.put("table.AIS.R_RAREA_FIRS", "IDU,REPL_NDB140");
      info.put("table.AIS.R_RAREA_POINTS", "IDU,REPL_NDB141");
      info.put("table.AIS.R_RESTRICTIVE_AIRSPACES", "IDU,REPL_NDB142");
      info.put("table.AIS.R_SELECTION_CRITERIAS", "IDU,REPL_NDB143");
      info.put("table.AIS.R_SIDS", "IDU,REPL_NDB144");
      info.put("table.AIS.R_SNOWTAM_CONTROL_BLOCKS", "IDU,REPL_NDB145");
      info.put("table.AIS.R_SNOWTAM_RUNWAYS", "IDU,REPL_NDB146");
      info.put("table.AIS.R_STARS", "IDU,REPL_NDB147");
      info.put("table.AIS.R_USER_PIPES", "IDU,REPL_NDB148");
      info.put("table.AIS.R_WAYPOINTS", "IDU,REPL_NDB149");

      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         /*
         ResultSet rs = conn.getMetaData().getTables(null, schema, null, null);
         ArrayList list = new ArrayList();
         while (rs.next()) {
           list.add(rs.getString(3)); // add the name
         }
         rs.close();
         */
         TableToWatchInfo[] tables = TableToWatchInfo.getTablesToWatch(info); 
         for (int i=0; i < tables.length; i++) {
            try {
               String name = tables[i].getTable();
               String schema = tables[i].getSchema();
               processOneTable(conn, schema, name);
               // System.out.println("\n\n");
               // System.out.println(obj.getDescription().toXml(""));
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
      finally {
         try {
            if (conn != null)
               this.dbPool.release(conn);
         }
         catch (Exception ex) {
         }
      }
      
   }
   
}
