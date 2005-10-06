/*------------------------------------------------------------------------------
Name:      SpecificHelper.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.contrib.replication;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import org.xmlBlaster.contrib.db.I_DbPool;
import java.util.Properties;

/**
 * Helper class for tests. Encapsulates all database specific stuff. 
 *
 * @author Michele Laghi
 */
final class SpecificHelper {
    
   public final static String ORACLE = "oracle";
   public final static String POSTGRES = "postgres";
   
    private String[] postgresTypesSql = new String[] {
       "CREATE TABLE test_dbspecific (name VARCHAR(20) PRIMARY KEY)",
       "CREATE TABLE test_dbspecific (col1 CHAR, col2 CHAR(5), col3 VARCHAR, col4 VARCHAR(10), col5 int, col6 int2, col7 bytea, col8 boolean, PRIMARY KEY (col1, col2))",
       "CREATE TABLE test_dbspecific (col1 REAL, col2 REAL[10], col3 FLOAT, col4 FLOAT[4], col5 double precision, col6 double precision[4], col7 date, col8 date[100], col9 timestamp, col10 timestamp[8], PRIMARY KEY (col1, col2))",
       "CREATE TABLE test_dbspecific (col1 bpchar, col2 int[3][4][5], PRIMARY KEY (col1))"
    };

    private String[] oracleTypesSql = new String[] {
       "CREATE TABLE test_dbspecific (one CHARACTER(10),two CHARACTER VARYING(5),three CHAR VARYING(30),four NATIONAL CHARACTER(30),five NATIONAL CHAR(20),PRIMARY KEY (one, two))",       
       "CREATE TABLE test_dbspecific (one LONG,two DECIMAL(10,3),three INTEGER,four SMALLINT,five FLOAT(3),PRIMARY KEY (three))",
       "CREATE TABLE test_dbspecific (two VARCHAR2(10),three VARCHAR2(10 BYTE), four VARCHAR2(10 CHAR),eight VARCHAR(10 BYTE),PRIMARY KEY(two, three ,four))",
       "CREATE TABLE test_dbspecific (one CHAR,two CHAR(10),three CHAR(10 BYTE),four CHAR(10 CHAR),five NCHAR,six NCHAR(10),seven CLOB,eight NCLOB,nine BLOB,ten BFILE)",
       "CREATE TABLE test_dbspecific (one NUMBER,two NUMBER(3),three NUMBER(3,2),four LONG,five DATE,six BINARY_FLOAT,seven BINARY_DOUBLE)",
       "CREATE TABLE test_dbspecific (one TIMESTAMP,two TIMESTAMP(2),three TIMESTAMP WITH TIME ZONE,four TIMESTAMP(2) WITH TIME ZONE,six TIMESTAMP WITH LOCAL TIME ZONE,seven TIMESTAMP(2) WITH LOCAL TIME ZONE)",
       "CREATE TABLE test_dbspecific (one INTERVAL YEAR TO MONTH,two INTERVAL YEAR(3) TO MONTH,seven RAW(200),eight LONG RAW,nine ROWID,ten UROWID)"
    };

    private String[] sql = oracleTypesSql;
    private String[] dropSqlOracle = new String[] {"DROP TRIGGER test_dbspecific_repl_t", "DROP TABLE test_dbspecific" };
    private String[] dropSqlPostgres = new String[] {"DROP TRIGGER test_dbspecific_repl_t ON test_dbspecific CASCADE", "DROP TABLE test_dbspecific CASCADE" };
    private String[] dropSql = dropSqlOracle;
    
    private String dbType = ORACLE;
    
    private Properties props;

    public SpecificHelper(Properties props) {
       this.props = (Properties)props.clone();
       String db = this.props.getProperty("db");
       if (db == null)
          db = ORACLE;
       setDefaultProperty(props, "jdbc.drivers", "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
       if (ORACLE.equalsIgnoreCase(db)) {
          this.dbType = setOracleDefault(this.props);
       }
       else if (POSTGRES.equalsIgnoreCase(db)) {
          this.dbType = setPostgresDefault(this.props);
       }
    }

    private void setDefaultProperty(Properties props, String key, String val) {
       if (props == null || key == null || val == null)
          return;
       
       String tmp = props.getProperty(key);
       if (tmp == null)
          props.put(key, val);
    }

    private String setOracleDefault(Properties props) {
       if (props == null)
          return "oracle";
       // export CLASSPATH=/home/michele/adhoc/ojdbc14_g.jar:${CLASSPATH}
       setDefaultProperty(props, "db.url", "jdbc:oracle:thin:@localhost:1521:test");
       setDefaultProperty(props, "db.user", "xmlblaster");
       setDefaultProperty(props, "db.password", "secret");
       setDefaultProperty(props, "replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificOracle");
       setDefaultProperty(props, "replication.bootstrapFile", "org/xmlBlaster/contrib/replication/setup/oracle/bootstrap.sql");
       setDefaultProperty(props, "replication.cleanupFile", "org/xmlBlaster/contrib/replication/setup/oracle/cleanup.sql");
       this.sql = this.oracleTypesSql;
       this.dropSql = this.dropSqlOracle;
       return "oracle";
    }
    
    private String setPostgresDefault(Properties props) {
       if (props == null)
          return "postgres";
       // export CLASSPATH=/home/michele/adhoc/ojdbc14_g.jar:${CLASSPATH}
       setDefaultProperty(props, "db.url", "");
       setDefaultProperty(props, "db.user", "");
       setDefaultProperty(props, "db.password", "");
       setDefaultProperty(props, "replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificDefault");
       setDefaultProperty(props, "replication.bootstrapFile", "org/xmlBlaster/contrib/replication/setup/postgres/bootstrap.sql");
       setDefaultProperty(props, "replication.cleanupFile", "org/xmlBlaster/contrib/replication/setup/postgres/cleanup.sql");
       this.sql = this.postgresTypesSql;
       this.dropSql = this.dropSqlOracle;
       return "postgres";
    }
   /**
    * If the table does not exist we expect a null ResultSet
    * @throws Exception Any type is possible
    */
   public final void informativeStuff(I_DbPool pool) throws Exception {
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
      finally {
         if (conn != null)
            pool.release(conn);
      }
   }

   public Properties getProperties() {
      return this.props;
   }
   
   public String[] getSql() {
      return this.sql;
   }
   
   public String[] getDropSql() {
      return this.dropSql;
   }
   
}
