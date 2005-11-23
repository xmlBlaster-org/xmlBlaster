/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;

public class SpecificOracle extends SpecificDefault {

   private static Logger log = Logger.getLogger(SpecificOracle.class.getName());

   private String ownSchema;
   
   /**
    * Not doing anything.
    */
   public SpecificOracle() {
      super();
   }

   public void init(I_Info info) throws Exception {
      super.init(info);
      this.ownSchema = this.info.get("db.user", null);
   }
   
   /**
    * Adds a schema to be watched. By Oracle it would add triggers to the
    * schema.
    * 
    * @param catalog
    * @param schema
    * @throws Exception
    */
   public void addSchemaToWatch(Connection conn, String catalog, String schema) throws Exception {
      if (schema == null || schema.length() < 1) return;
      Map map = this.replacer.getAdditionalMapClone();
      map.put("schemaName", schema);
      Replacer tmpReplacer = new Replacer(this.info, map);
      boolean doWarn = true;
      boolean force = true; // overwrites existing ones
      updateFromFile(conn, "createDropAlter", "replication.createDropAlterFile",
            "org/xmlBlaster/contrib/replication/setup/oracle/createDropAlter.sql", doWarn, force, tmpReplacer);
   }

   /**
    * 
    * @param description
    * @return
    */
   private boolean checkIfContainsLongs(SqlDescription description) {
      SqlColumn[] cols = description.getColumns();
      for (int i = 0; i < cols.length; i++) {
         int type = cols[i].getSqlType();
         if (type == Types.LONGVARCHAR || type == Types.LONGVARBINARY)
            return true;
      }
      return false;
   }
   
   /**
    * 
    * @param col
    * @param prefix
    *           can be 'old' or 'new'
    * @return
    */
   protected String createVariableSqlPart(SqlDescription description, String prefix) {
      String newOldPrefix = ":"; // ":" on ora10 ?
      SqlColumn[] cols = description.getColumns();
      String contName = prefix + "Cont"; // will be newCont or oldCont
      StringBuffer buf = new StringBuffer();
      String tablePrefix = newOldPrefix + prefix;
      buf.append("       oid := ROWIDTOCHAR(").append(tablePrefix).append(".rowid);\n");
      if ("new".equals(prefix)) {
         if (checkIfContainsLongs(description))
            return buf.toString();
      }
      
      buf.append("       ").append(contName).append(" := EMPTY_CLOB;\n");
      buf.append("       dbms_lob.createtemporary(").append(contName).append(", TRUE);\n");
      buf.append("       dbms_lob.open(").append(contName).append(", dbms_lob.lob_readwrite);\n");
      for (int i = 0; i < cols.length; i++) {
         String colName = cols[i].getColName();
         String typeName = cols[i].getTypeName();
         int type = cols[i].getSqlType();
         String varName = tablePrefix + "." + colName; // for example :new.colname'
         if (type != Types.LONGVARCHAR && type != Types.LONGVARBINARY)
            buf.append("          IF ").append(varName).append(" IS NOT NULL THEN\n");
         
         if (type == Types.LONGVARCHAR || type == Types.LONGVARBINARY) {
            /*
            buf.append("          longKey := ").append(this.replPrefix).append("increment();\n");
            // buf.append("          INSERT INTO ").append(this.replPrefix).append("LONGS_TABLE SELECT longKey, TO_LOB(").append(colName).append(") FROM ").append(tableName).append(" WHERE rowid IN (").append(tablePrefix).append(".rowid").append(");\n");
            buf.append("--          INSERT INTO ").append(this.replPrefix).append("LONGS_TABLE SELECT longKey, TO_LOB(").append(colName).append(") FROM ").append(tableName).append(";\n");
            buf.append("--          SELECT content INTO tmpCont FROM ").append(this.replPrefix).append("LONGS_TABLE WHERE repl_key=longKey;\n");
            buf.append("--          dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
            "col2xml('").append(colName).append("', tmpCont));\n");
            */
         }
         else if (type == Types.BINARY || type == Types.BLOB || type == Types.JAVA_OBJECT || type == Types.VARBINARY
               || type == Types.STRUCT) {
            buf.append("             blobCont := EMPTY_BLOB;\n");
            buf.append("             dbms_lob.createtemporary(blobCont, TRUE);\n");
            buf.append("             dbms_lob.open(blobCont, dbms_lob.lob_readwrite);\n");
            if (type == Types.BLOB) {
               buf.append("             dbms_lob.append(blobCont,").append(varName).append(");\n");
            }
            else {
               buf.append("             dbms_lob.writeappend(blobCont,").append("length(");
               buf.append(varName).append("),").append(varName).append(");\n");
            }
            buf.append("             dbms_lob.close(blobCont);\n");
            buf.append("             dbms_lob.append(").append(contName).append(", ");
            buf.append(this.replPrefix).append("col2xml_base64('").append(colName).append("', blobCont));\n");
         }
         else if (type == Types.DATE || type == Types.TIMESTAMP || typeName.equals("TIMESTAMP")) {
            buf.append("             tmpCont := EMPTY_CLOB;\n");
            buf.append("             dbms_lob.createtemporary(tmpCont, TRUE);\n");
            buf.append("             dbms_lob.open(tmpCont, dbms_lob.lob_readwrite);\n");
            // on new oracle data coming from old versions could be sqlType=TIMESTAMP but type='DATE'
            if (typeName.equals("DATE") || type == Types.DATE) 
               buf.append("             tmpNum := TO_CHAR(").append(varName).append(",'YYYY-MM-DD HH24:MI:SS');\n");
            else // then timestamp
               buf.append("             tmpNum := TO_CHAR(").append(varName).append(",'YYYY-MM-DD HH24:MI:SSXFF');\n");
            buf.append("             dbms_lob.writeappend(tmpCont, LENGTH(tmpNum), tmpNum);\n");
            buf.append("             dbms_lob.close(tmpCont);\n");
            buf.append("             dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
                  "col2xml('").append(colName).append("', tmpCont));\n");
         }
         else {
            buf.append("             tmpCont := EMPTY_CLOB;\n");
            buf.append("             dbms_lob.createtemporary(tmpCont, TRUE);\n");
            buf.append("             dbms_lob.open(tmpCont, dbms_lob.lob_readwrite);\n");
            if (type == Types.INTEGER || type == Types.NUMERIC || type == Types.DECIMAL || type == Types.FLOAT
                  || type == Types.DOUBLE || type == Types.DATE || type == Types.TIMESTAMP || type == Types.OTHER) {
               buf.append("             tmpNum := TO_CHAR(").append(varName).append(");\n");
            }
            else {
               buf.append("             tmpNum := ").append(varName).append(";\n");
            }
            buf.append("             dbms_lob.writeappend(tmpCont, LENGTH(tmpNum), tmpNum);\n");
            buf.append("             dbms_lob.close(tmpCont);\n");
            buf.append("             dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
                  "col2xml('").append(colName).append("', tmpCont));\n");
         }
         
         if (type != Types.LONGVARCHAR && type != Types.LONGVARBINARY)
            buf.append("          END IF;\n");
         
      }
      return buf.toString();
   }

   public String createTableTrigger(SqlDescription infoDescription, String triggerName, String replFlags) {
      if (replFlags == null)
         replFlags = "";
      boolean doDeletes = replFlags.indexOf('D') > -1;
      boolean doInserts = replFlags.indexOf('I') > -1;
      boolean doUpdates = replFlags.indexOf('U') > -1;
      
      String tableName = infoDescription.getIdentity(); // should be the table
                                                         // name
      String completeTableName = tableName;
      String schemaName = infoDescription.getSchema();
      if (schemaName != null && schemaName.trim().length() > 0) {
         completeTableName = schemaName + "." + tableName;
      }
      String dbName = "NULL"; // still unsure on how to retrieve this
                              // information on a correct way.
      StringBuffer buf = new StringBuffer();
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("-- This is the function which will be registered to the triggers.               \n");
      buf.append("-- It must not take any parameter.                                              \n");
      buf.append("-- This is the only method which is business data specific. It is depending on  \n");
      buf.append("-- the table to be replicated. This should be generated by a tool.              \n");
      buf.append("--                                                                              \n");
      buf.append("-- For each table you should just write out in a sequence the complete content  \n");
      buf.append("-- of the row to replicate. You could make more fancy stuff here, for example   \n");
      buf.append("-- you could just send the minimal stuff, i.e. only the stuff which has changed \n");
      buf.append("-- (for the new stuff) and for the old one you could always send an empty one.  \n");
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("-- AND THE TRIGGER FOR THE replTest TABLE                                       \n");
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("\n");
      buf.append("CREATE OR REPLACE TRIGGER ").append(triggerName).append("\n");
      boolean first = true;
      buf.append("AFTER");
      if (doUpdates) {
         buf.append(" UPDATE");
         first = false;
      }
      if (doDeletes) {
         if (!first)
            buf.append(" OR");
         else
            first = false;
         buf.append(" DELETE");
      }
      if (doInserts) {
         if (!first)
            buf.append(" OR");
         else
            first = false;
         buf.append(" INSERT");
      }
      buf.append("ON ").append(completeTableName).append("\n");
      buf.append("FOR EACH ROW\n");
      buf.append("DECLARE\n");
      buf.append("   blobCont BLOB; \n");
      buf.append("   oldCont CLOB; \n");
      buf.append("   newCont CLOB;\n");
      buf.append("   tmpCont CLOB;\n");
      buf.append("   tmpNum  VARCHAR(100);\n");
      buf.append("   oid     VARCHAR(30);\n");
      buf.append("   replKey INTEGER;\n");
      buf.append("   ret     VARCHAR(10);\n");
      buf.append("   transId VARCHAR2(30);\n");
      buf.append("   op      VARCHAR(10);\n");
      buf.append("   longKey INTEGER;\n");
      buf.append("BEGIN\n");
      buf.append("\n");
      buf.append("    IF INSERTING THEN\n");
      buf.append("       op := 'INSERT';\n");
      buf.append(createVariableSqlPart(infoDescription, "new"));
      buf.append("    ELSIF DELETING THEN\n");
      buf.append("       op := 'DELETE';\n");
      buf.append(createVariableSqlPart(infoDescription, "old"));
      buf.append("    ELSE\n");
      buf.append("       op := 'UPDATE';\n");
      buf.append(createVariableSqlPart(infoDescription, "old"));
      buf.append(createVariableSqlPart(infoDescription, "new"));
      buf.append("    END IF;\n");
      String dbNameTmp = null;
      String tableNameTmp = "'" + tableName + "'";
      String schemaNameTmp = null;
      if (dbName == null)
         dbNameTmp = "NULL";
      else dbNameTmp = "'" + dbName + "'";
      if (schemaName == null)
         schemaNameTmp = "NULL";
      else schemaNameTmp = "'" + schemaName + "'";
      buf.append("    SELECT " + this.replPrefix + "seq.nextval INTO replKey FROM DUAL;\n");
      buf.append("    transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);\n");
      buf.append("    INSERT INTO " + this.replPrefix + "items (repl_key, trans_key, dbId, tablename, guid,\n");
      buf.append("                           db_action, db_catalog, db_schema, \n");
      buf.append("                           content, oldContent, version) values \n");
      buf.append("                           (replKey, transId,").append(dbNameTmp).append(",\n");
      buf.append("            ").append(tableNameTmp).append(", oid, op, NULL, ").append(schemaNameTmp).append(
            ", newCont, \n");
      buf.append("            oldCont, '0.0');\n");
      buf.append("END ").append(triggerName).append(";\n");
      buf.append("\n");
      return buf.toString();
   }

   private final boolean cleanupType(String schema, String objName, String sql, String postfix) {
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         List names = new ArrayList();
         log.info(sql);
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql);
         while (rs.next()) {
            names.add(rs.getString(1));
         }
         st.close();
         rs.close();
         st = null;
         rs = null;
         for (int i = 0; i < names.size(); i++) {
            String name = (String) names.get(i);
            if (name != null) {
               sql = "DROP " + objName + " " + name + postfix;
               log.info(sql);
               st = conn.createStatement();
               try {
                  st.executeUpdate(sql);
               }
               catch (Exception e) {
                  e.printStackTrace();
               }
               finally {
                  st.close();
                  st = null;
               }
            }
         }
         return true;
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return false;
      }
      finally {
         if (conn != null) {
            try {
               this.dbPool.release(conn);
            }
            catch (Exception ex) {
               ex.printStackTrace();
            }
         }
         conn = null;
      }
   }

   public void cleanupSchema(String schema) {
      String sql = "SELECT synonym_name FROM all_synonyms WHERE owner='" + schema + "'";
      cleanupType(schema, "synonym", sql, "");
      sql = "SELECT trigger_name FROM all_triggers WHERE owner='" + schema + "'";
      cleanupType(schema, "trigger", sql, "");
      // sql = "SELECT name FROM all_source WHERE owner='" + schema + "' AND
      // LINE=1";
      // cleanupType(schema, "function", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='PACKAGE' AND LINE=1";
      cleanupType(schema, "package", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='PROCEDURE' AND LINE=1";
      cleanupType(schema, "procedure", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='FUNCTION' AND LINE=1";
      cleanupType(schema, "function", sql, "");
      // sql = "SELECT procedure_name FROM all_procedures WHERE owner='" +
      // schema + "'";
      // cleanupType(schema, "function", sql, "");
      // sql = "SELECT procedure_name FROM all_procedures WHERE owner='" +
      // schema + "'";
      // cleanupType(schema, "procedure", sql, "");
      sql = "SELECT view_name FROM all_views WHERE owner='" + schema + "'";
      cleanupType(schema, "view", sql, " CASCADE CONSTRAINTS");
      sql = "SELECT table_name FROM all_tables WHERE owner='" + schema + "'";
      cleanupType(schema, "table", sql, " CASCADE CONSTRAINTS");
      sql = "SELECT sequence_name FROM all_sequences WHERE sequence_owner='" + schema + "'";
      cleanupType(schema, "sequence", sql, "");
   }

   /**
    * Helper method used to construct the CREATE TABLE statement part belonging
    * to a single COLUMN.
    * 
    * There is currently no way to distinguish the following:
    * <ul>
    * <li>DECIMAL from SMALLINT and INTEGER (they are all threaded as INTEGER)</li>
    * <li>CHAR are all threated the same, so: CHAR(10) is the same as CHAR(10
    * BYTE) which is the same as CHAR(10 CHAR)</li>
    * <li></li>
    * 
    * </ul>
    * 
    * @param colInfoDescription
    * @return
    */
   public StringBuffer getColumnStatement(SqlColumn colInfoDescription) {
      StringBuffer buf = new StringBuffer(colInfoDescription.getColName());
      buf.append(" ");
      String type = colInfoDescription.getType();
      int precision = colInfoDescription.getPrecision();
      int sqlType = colInfoDescription.getSqlType();
      if (sqlType == Types.CHAR || sqlType == Types.VARCHAR) {
         buf.append(type).append("(").append(precision).append(")");
      }
      else if (sqlType == Types.OTHER) {
         if (type.equalsIgnoreCase("NCHAR")) { // two bytes per character
            buf.append(type);
            if (precision > 0) buf.append("(").append(precision).append(")");
         }
         else {
            buf.append(type);
         }
      }
      else if (sqlType == Types.LONGVARCHAR || sqlType == Types.CLOB || sqlType == Types.BLOB) {
         buf.append(type);
      }
      else if (sqlType == Types.DECIMAL) {
         int scale = colInfoDescription.getScale();
         buf.append(type);
         if (precision > 0) {
            buf.append("(").append(precision);
            if (scale > 0) buf.append(",").append(scale);
            buf.append(")");
         }
      }
      else if (sqlType == Types.FLOAT) {
         buf.append(type).append("(").append(precision).append(")");
      }
      else if (sqlType == Types.DATE) {
         buf.append(type);
      }
      else if (sqlType == Types.VARBINARY) {
         buf.append(type);
         int width = colInfoDescription.getColSize();
         if (width > 0) buf.append("(").append(width).append(")");
      }
      else if (sqlType == Types.LONGVARBINARY) {
         buf.append(type);
      }
      else {
         buf.append(type);
         /*
          * if (type.equalsIgnoreCase("BFILE")) { // for example BFILE (sqlType =
          * -13) buf.append(type); } else if
          * (type.equalsIgnoreCase("BINARY_FLOAT")) { // binaryfloat (100)
          * buf.append(type); } else if (type.equalsIgnoreCase("BINARY_DOUBLE")) { //
          * binaryfloat (100) buf.append(type); } else { buf.append(type); }
          */
      }
      return buf;
   }

   /**
    */
   public static void main(String[] args) {
      try {
         System.setProperty("java.util.logging.config.file", "testlog.properties");
         LogManager.getLogManager().readConfiguration();
         // ---- Database settings -----
         if (System.getProperty("jdbc.drivers", null) == null) {
            System
                  .setProperty(
                        "jdbc.drivers",
                        "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         }
         if (System.getProperty("db.url", null) == null) {
            System.setProperty("db.url", "jdbc:oracle:thin:@localhost:1521:test");
         }
         if (System.getProperty("db.user", null) == null) {
            System.setProperty("db.user", "xmlblaster");
         }
         if (System.getProperty("db.password", null) == null) {
            System.setProperty("db.password", "secret");
         }
         SpecificOracle oracle = new SpecificOracle();
         I_Info info = new PropertiesInfo(System.getProperties());
         oracle.init(info);
         I_DbPool pool = (I_DbPool) info.getObject("db.pool");
         Connection conn = pool.reserve();
         oracle.cleanupSchema("AIS");
         pool.release(conn);
      }
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * If the triggerName is null, then the own schema triggers are deleted. If
    * at least one of the triggers has been removed, it returns true.
    */
   public boolean removeTrigger(String triggerName, String tableName, boolean isSchemaTrigger) {
      boolean ret = false;
      if (triggerName == null) {
         try {
            this.dbPool.update("DROP TRIGGER " + this.replPrefix + "drtg_" + this.ownSchema);
           ret = true;
         }
         catch (Exception ex) {
         }
         try {
            this.dbPool.update("DROP TRIGGER " + this.replPrefix + "altg_" + this.ownSchema);
           ret = true;
         }
         catch (Exception ex) {
         }
         try {
            this.dbPool.update("DROP TRIGGER " + this.replPrefix + "crtg_" + this.ownSchema);
           ret = true;
         }
         catch (Exception ex) {
         }
         return ret;
      }
      try {
         this.dbPool.update("DROP TRIGGER " + triggerName);
         return true;
      }
      catch (Exception ex) {
         return false;
      }
   }
   
   private int cleanupOp(Connection conn, ArrayList names, String schema, String prefix, String postfix) throws SQLException {
      int sum = 0;
      for (int i=0; i < names.size(); i++) {
         Statement st = null;
         try {
            String name = (String)names.get(i);
            if (schema != null)
               name = schema + "." + name;
            // String sql = "DROP TABLE "  + name + " CASCADE CONSTRAINTS";
            String sql = prefix + " " + name + " " + postfix;
            st = conn.createStatement();
            sum += st.executeUpdate(sql);
         }
         catch (SQLException ex) {
            if (st != null) {
               st.close();
            }
         }
      }
      return sum;
   }
   
   public int wipeoutSchema(String catalog, String schema) throws Exception {
      int sum = 0;
      int count = 0;
      int maxCount = 50;
      int oldSum = 0;
      while (count < maxCount) {
         sum = wipeoutSchemaSingleSweep(catalog, schema);
         count++;
         log.info("sweep '" + count + "' for schema '" + schema + "' has erased '" + sum + "' objects");
         if (sum == oldSum)
            break;
         oldSum = sum;
      }
      if (sum != 0)
         log.warning("Could not clean up properly all objects belonging to '" + schema + "' '" + sum + "' objects remain. Continuing anyway");
      if (count == maxCount)
         throw new Exception("Could not clean up complete schema '" + schema + "' after maximum sweeps '" + maxCount + "'. Processed/deleted '" + sum + "' objects");
      return sum;
   }

   private int wipeoutSchemaSingleSweep(String catalog, String schema) throws Exception {
      Connection conn = null;
      Statement st = null;
      ResultSet rs = null;
      int sum = 0;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         
         {  // TRIGGERS
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='TRIGGER'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            // since cleanupOp does not really return the number of effectively removed entries
            cleanupOp(conn, names, schema, "DROP TRIGGER", "");
            rs.close();
            st.close();
         }
         {  // SEQUENCES
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='SEQUENCE'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP SEQUENCE", "");
            rs.close();
            st.close();
         }
         {  // FUNCTIONS
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='FUNCTION'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP FUNCTION", "");
            rs.close();
            st.close();
         }
         {  // PROCEDURES
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='PROCEDURE'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP PROCEDURE", "");
            rs.close();
            st.close();
         }
         {  // VIEWS
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='VIEW'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP VIEW", "CASCADE CONSTRAINTS");
            rs.close();
            st.close();
         }
         {  // TABLES
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='TABLE'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP TABLE", "CASCADE CONSTRAINTS");
            rs.close();
            st.close();
         }
         {  // SYNONYMS
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='SYNONYM'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP SYNONYM", "");
            rs.close();
            st.close();
         }
         {  // INDEXES
            st = conn.createStatement();
            String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='INDEX'";
            rs = st.executeQuery(sql);
            ArrayList names = new ArrayList();
            while (rs.next())
               names.add(rs.getString(1));
            sum += names.size();
            cleanupOp(conn, names, schema, "DROP INDEX", "FORCE");
            rs.close();
            st.close();
         }
      }
      finally {
         if (rs != null)
            rs.close();
         if (st != null)
            st.close();
         
      }
      return sum;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#getContentFromGuid(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
    */
   public String getContentFromGuid(String guid, String catalog, String schema, String table) throws Exception {
      // throw new Exception("SpecificOracle.getContentFromGuid is not implemented yet for table='" + table + "' and guid='" + guid + "'");
      SqlInfo obj = new SqlInfo(this.info);
      Connection conn = null;
      Statement st = null;
      String completeTable = schema;
      if (completeTable != null)
         completeTable += "." + table;
      else
         completeTable = table;
      try {
         conn = this.dbPool.reserve();
         st = conn.createStatement();
         String sql = "select * from " + completeTable + " WHERE rowid=CHARTOROWID('" + guid + "')";
         ResultSet rs = st.executeQuery(sql);
         if (rs.next()) {
            obj.fillOneRowWithObjects(rs, null);
            SqlRow row = (SqlRow)obj.getRows().get(0);
            rs.close();
            return row.toXml("", false);
         }
         else {
            log.severe("The entry guid='" + guid + "' for table '" + completeTable + "' was not found (anymore)");
            return "";
         }
            
      }
      catch (Exception ex) {
         if (conn != null) {
            try {
               this.dbPool.erase(conn);
            }
            catch (Exception e) {
               e.printStackTrace();
            }
            conn = null;
         }
         throw ex;
      }
      finally { 
         if (st != null) {
            try {
               st.close();
            }
            catch (Exception e) {
               e.printStackTrace();
            }
         }
         if (conn != null)
            this.dbPool.release(conn);
         conn = null;
      }
   }

}
