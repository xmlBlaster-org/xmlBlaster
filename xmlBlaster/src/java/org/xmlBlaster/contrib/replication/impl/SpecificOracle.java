/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.ResultSet;
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
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;

public class SpecificOracle extends SpecificDefault {

   private static Logger log = Logger.getLogger(SpecificOracle.class.getName());

   /**
    * Not doing anything.
    */
   public SpecificOracle() {
      super();
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
    * @param col
    * @param prefix
    *           can be 'old' or 'new'
    * @return
    */
   protected String createVariableSqlPart(DbUpdateInfoDescription description, String prefix) {
      String newOldPrefix = ":"; // ":" on ora10 ?
      DbUpdateInfoColDescription[] cols = description.getUpdateInfoColDescriptions();
      String contName = prefix + "Cont"; // will be newCont or oldCont
      StringBuffer buf = new StringBuffer();
      buf.append("       ").append(contName).append(" := EMPTY_CLOB;\n");
      buf.append("       dbms_lob.createtemporary(").append(contName).append(", TRUE);\n");
      buf.append("       dbms_lob.open(").append(contName).append(", dbms_lob.lob_readwrite);\n");
      for (int i = 0; i < cols.length; i++) {
         String colName = cols[i].getColName();
         int type = cols[i].getSqlType();
         String varName = newOldPrefix + prefix + "." + colName; // for example
                                                                  // ':new.colname'
         if (type == Types.BINARY || type == Types.BLOB || type == Types.JAVA_OBJECT || type == Types.VARBINARY
               || type == Types.STRUCT) {
            buf.append("       blobCont := EMPTY_BLOB;\n");
            buf.append("       dbms_lob.createtemporary(blobCont, TRUE);\n");
            buf.append("       dbms_lob.open(blobCont, dbms_lob.lob_readwrite);\n");
            if (type == Types.BLOB)
               buf.append("       dbms_lob.append(blobCont,").append(varName).append(");\n");
            else buf.append("       dbms_lob.writeappend(blobCont,").append("length(").append(varName).append("),")
                  .append(varName).append(");\n");
            buf.append("       dbms_lob.close(blobCont);\n");
            buf.append("       dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
                  "col2xml_base64('").append(colName).append("', blobCont));\n");
         }
         else {
            buf.append("       tmpCont := EMPTY_CLOB;\n");
            buf.append("       dbms_lob.createtemporary(tmpCont, TRUE);\n");
            buf.append("       dbms_lob.open(tmpCont, dbms_lob.lob_readwrite);\n");
            if (type == Types.INTEGER || type == Types.NUMERIC || type == Types.DECIMAL || type == Types.FLOAT
                  || type == Types.DOUBLE || type == Types.DATE || type == Types.TIMESTAMP || type == Types.OTHER) {
               buf.append("       tmpNum := TO_CHAR(").append(varName).append(");\n");
            }
            else {
               buf.append("       tmpNum := ").append(varName).append(";\n");
            }
            buf.append("       dbms_lob.writeappend(tmpCont, LENGTH(tmpNum), tmpNum);\n");
            buf.append("       dbms_lob.close(tmpCont);\n");
            buf.append("       dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
                  "col2xml('").append(colName).append("', tmpCont));\n");
         }
      }
      // buf.append(" oid :=
      // ROWIDTOCHAR(:").append(prefix).append(".rowid);\n");
      // TODO this has to be changed later on
      buf.append("       oid := ''; -- TODO: this has to be changed later on \n");
      return buf.toString();
   }

   public String createTableTrigger(DbUpdateInfoDescription infoDescription, String triggerName) {
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
      buf.append("AFTER UPDATE OR DELETE OR INSERT\n");
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
   public StringBuffer getColumnStatement(DbUpdateInfoColDescription colInfoDescription) {
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


}
