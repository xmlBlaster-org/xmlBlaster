/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;

public class SpecificOracle extends SpecificDefault {

   private static Logger log = Logger.getLogger(SpecificOracle.class.getName());

   private String ownSchema;
   
   private boolean debug;
   private String debugFunction;
   
   /**
    * Not doing anything.
    */
   public SpecificOracle() {
      super();
   }

   public void init(I_Info info) throws Exception {
      super.init(info);
      this.ownSchema = this.info.get("db.user", null);
      this.debug = this.info.getBoolean("replication.plsql.debug", false);
      this.debugFunction = this.info.get("replication.plsql.debugFunction", null);
   }
   
   /**
    * Adds a schema to be watched. By Oracle it would add triggers to the
    * schema.
    * 
    * @param catalog
    * @param schema
    * @throws Exception. Thrown if an exception occurs on the backend. Note that if an
    * exception occurs you must cleanup the connection since it might become stale.
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
   protected String createVariableSqlPart(SqlDescription description, String prefix, boolean containsLongs) {
      String newOldPrefix = ":"; // ":" on ora10 ?
      SqlColumn[] cols = description.getColumns();
      String contName = prefix + "Cont"; // will be newCont or oldCont
      StringBuffer buf = new StringBuffer();
      String tablePrefix = newOldPrefix + prefix;
      buf.append("       ").append(contName).append(" := NULL;\n");
      buf.append("       oid := ROWIDTOCHAR(").append(tablePrefix).append(".rowid);\n");
      if ("new".equals(prefix)) {
         if (containsLongs)
            return buf.toString();
      }
      // note when using LONGS the newCont must be NULL (not EMPTY_CLOB)
      buf.append("       ").append(contName).append(" := EMPTY_CLOB;\n");
      buf.append("       dbms_lob.createtemporary(").append(contName).append(", TRUE);\n");
      buf.append("       dbms_lob.open(").append(contName).append(", dbms_lob.lob_readwrite);\n");
      for (int i = 0; i < cols.length; i++) {
         String colName = cols[i].getColName();
         String typeName = cols[i].getTypeName();
         int type = cols[i].getSqlType();
         String varName = tablePrefix + "." + colName; // for example :new.colname'
         
         if (this.debug) {
            buf.append("       IF debug != 0 THEN\n");
            if (this.debugFunction != null)
               buf.append("          ").append(this.debugFunction).append("('   col ").append(colName).append(" type ").append(typeName).append(" typeNr ").append(type).append(" prefix ").append(prefix).append("');\n");
            buf.append("       END IF;\n");
         }
         
         if (type != Types.LONGVARCHAR && type != Types.LONGVARBINARY)
            buf.append("          IF ").append(varName).append(" IS NOT NULL THEN\n");
         
         if (type == Types.LONGVARCHAR || type == Types.LONGVARBINARY) {
            /*
             * don't use the LONGS since they can not be used inside a trigger. For INSERT
             * the RAWID will be used and the entry will be read when processing the data, for
             * DELETE and UPDATE the LONGS will not be used to search.
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
            if (type == Types.INTEGER || type == Types.NUMERIC || type == Types.DECIMAL || type == Types.FLOAT
                  || type == Types.DOUBLE || type == Types.DATE || type == Types.TIMESTAMP || type == Types.OTHER) {
               buf.append("             tmpNum := TO_CHAR(").append(varName).append(");\n");
               buf.append("             dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append("fill_blob_char(tmpNum, '").append(colName).append("'));\n");
            }
            else {
               // buf.append("             tmpNum := ").append(varName).append(";\n");
               buf.append("             dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append("fill_blob_char(").append(varName).append(", '").append(colName).append("'));\n");
            }
            
            // buf.append("             tmpCont := EMPTY_CLOB;\n");
            // buf.append("             dbms_lob.createtemporary(tmpCont, TRUE);\n");
            // buf.append("             dbms_lob.open(tmpCont, dbms_lob.lob_readwrite);\n");
            // buf.append("             dbms_lob.writeappend(tmpCont, LENGTH(tmpNum), tmpNum);\n");
            // buf.append("             dbms_lob.close(tmpCont);\n");
            // buf.append("             dbms_lob.append(").append(contName).append(", ").append(this.replPrefix).append(
            //       "col2xml('").append(colName).append("', tmpCont));\n");
         }
         
         if (type != Types.LONGVARCHAR && type != Types.LONGVARBINARY)
            buf.append("          END IF;\n");
         
      }
      return buf.toString();
   }

   public String createTableTrigger(SqlDescription infoDescription, TableToWatchInfo tableToWatch) {
      String triggerName = tableToWatch.getTrigger();
      String replFlags = tableToWatch.getActions();
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
      buf.append(" ON ").append(completeTableName).append("\n");
      buf.append("FOR EACH ROW\n");
      buf.append("DECLARE\n");
      buf.append("   blobCont BLOB; \n");
      buf.append("   oldCont CLOB; \n");
      buf.append("   newCont CLOB;\n");
      buf.append("   tmpCont CLOB;\n");
      buf.append("   tmpNum  VARCHAR(255);\n");
      buf.append("   oid     VARCHAR(50);\n");
      buf.append("   replKey INTEGER;\n");
      buf.append("   ret     VARCHAR(10);\n");
      buf.append("   transId VARCHAR2(50);\n");
      buf.append("   op      VARCHAR(15);\n");
      buf.append("   longKey INTEGER;\n");
      buf.append("   debug   INTEGER;\n");
      
      buf.append("BEGIN\n");
      buf.append("\n");
      if (this.debug) {
         buf.append("    debug := ").append(this.replPrefix).append("debug_trigger('").append(schemaName).append("','").append(tableName).append("');\n");
         buf.append("    IF debug != 0 THEN\n");
         if (this.debugFunction != null)
            buf.append("       ").append(this.debugFunction).append("('TRIGGER ON ").append(completeTableName).append(" invoked');\n");
         // buf.append("       ").append(this.replPrefix).append("debug('TRIGGER ON ").append(completeTableName).append(" invoked');\n");
         buf.append("    END IF;\n");
         // buf.append("    ").append(this.replPrefix).append("debug('TRIGGER ON '").append(completeTableName).append("' invoked');\n");
         // buf.append("    KG_WAKEUP.PG$DBGMESS('TRIGGER ON '").append(completeTableName).append("' invoked');\n");
      }
      
      boolean containsLongs = checkIfContainsLongs(infoDescription);
      
      buf.append("    IF INSERTING THEN\n");
      buf.append("       op := 'INSERT';\n");
      buf.append(createVariableSqlPart(infoDescription, "new", containsLongs));
      buf.append("    ELSIF DELETING THEN\n");
      buf.append("       op := 'DELETE';\n");
      buf.append(createVariableSqlPart(infoDescription, "old", containsLongs));
      buf.append("    ELSE\n");
      buf.append("       op := 'UPDATE';\n");
      buf.append(createVariableSqlPart(infoDescription, "old", containsLongs));
      buf.append(createVariableSqlPart(infoDescription, "new", containsLongs));
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
      
      buf.append("    INSERT INTO " + this.replPrefix + "items (repl_key, trans_key, dbId, tablename, guid,\n");
      buf.append("                           db_action, db_catalog, db_schema, \n");
      buf.append("                           content, oldContent, version) values \n");
      buf.append("                           (replKey, 'UNKNOWN',").append(dbNameTmp).append(",\n");
      buf.append("            ").append(tableNameTmp).append(", oid, op, NULL, ").append(schemaNameTmp).append(
            ", newCont, \n");
      buf.append("            oldCont, '").append(this.replVersion).append("');\n");
      // INSERT + UPDATE instead of only INSERT since it appears that the sequence is incremented outside the transaction
      buf.append("    transId := DBMS_TRANSACTION.LOCAL_TRANSACTION_ID(FALSE);\n");
      buf.append("    if transId = NULL THEN\n");
      buf.append("       transId := CHR(replKey);\n");
      buf.append("    END IF;\n");
      buf.append("    UPDATE " + this.replPrefix + "items SET trans_key=transId WHERE repl_key=replKey;\n");
      
      // clean up (close) the used lobs. Note that if the table contains longs then the
      // newClob has not been opened and shall therefore not be closed.
      if (containsLongs) {
         buf.append("    IF NOT INSERTING THEN\n");
         buf.append("       dbms_lob.close(oldCont);\n");
         buf.append("    END IF;\n");
      }
      else {
         buf.append("    IF INSERTING THEN\n");
         buf.append("       dbms_lob.close(newCont);\n");
         buf.append("    ELSIF DELETING THEN\n");
         buf.append("       dbms_lob.close(oldCont);\n");
         buf.append("    ELSE\n");
         buf.append("       dbms_lob.close(oldCont);\n");
         buf.append("       dbms_lob.close(newCont);\n");
         buf.append("    END IF;\n");
      }
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
         conn = removeFromPool(conn, ROLLBACK_NO);
         return false;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }

   /**
    * Cleans up the specified schema for the specified type.
    * @param schema can not be null.
    * @param type can be null. If null all types are cleaned up, otherwise only the ones contained in the string will be cleaned up.
    * For example "table alltriggers" will clean up both 'table' and 'trigger' types. The types must be specified in lowercase.
    * Allowed types are synonym,trigger,package,procedure,function,view,table,sequence.
    * @param referencedSchema is the schema which is referenced by the object. It only has an effect on triggers where the 
    * owner of the trigger would be the schema but the table on which the trigger resides it the referenced schema. If null, all
    * schemas referenced are deleted. 
    */
   public void cleanupSchema(String schema, String type, String referencedSchema) {
      String sql = "SELECT synonym_name FROM all_synonyms WHERE owner='" + schema + "'";
      if (type == null || type.indexOf("synonym") != -1)
         cleanupType(schema, "synonym", sql, "");
      if (referencedSchema == null || referencedSchema.trim().length() < 1)
         sql = "SELECT trigger_name FROM all_triggers WHERE owner='" + schema + "'";
      else
         sql = "SELECT trigger_name FROM all_triggers WHERE owner='" + schema + "' AND table_owner='" + referencedSchema + "'";
      if (type == null || type.indexOf("trigger") != -1)
         cleanupType(schema, "trigger", sql, "");
      // sql = "SELECT name FROM all_source WHERE owner='" + schema + "' AND
      // LINE=1";
      // cleanupType(schema, "function", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='PACKAGE' AND LINE=1";
      if (type == null || type.indexOf("package") != -1)
         cleanupType(schema, "package", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='PROCEDURE' AND LINE=1";
      if (type == null || type.indexOf("procedure") != -1)
         cleanupType(schema, "procedure", sql, "");
      sql = "SELECT NAME FROM all_source WHERE owner='" + schema + "' AND type='FUNCTION' AND LINE=1";
      if (type == null || type.indexOf("function") != -1)
         cleanupType(schema, "function", sql, "");
      // sql = "SELECT procedure_name FROM all_procedures WHERE owner='" +
      // schema + "'";
      // cleanupType(schema, "function", sql, "");
      // sql = "SELECT procedure_name FROM all_procedures WHERE owner='" +
      // schema + "'";
      // cleanupType(schema, "procedure", sql, "");
      sql = "SELECT view_name FROM all_views WHERE owner='" + schema + "'";
      if (type == null || type.indexOf("view") != -1)
         cleanupType(schema, "view", sql, " CASCADE CONSTRAINTS");
      sql = "SELECT table_name FROM all_tables WHERE owner='" + schema + "'";
      if (type == null || type.indexOf("table") != -1)
         cleanupType(schema, "table", sql, " CASCADE CONSTRAINTS");
      sql = "SELECT sequence_name FROM all_sequences WHERE sequence_owner='" + schema + "'";
      if (type == null || type.indexOf("sequence") != -1)
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
         // System.setProperty("java.util.logging.config.file", "testlog.properties");
         // LogManager.getLogManager().readConfiguration();
         // ---- Database settings -----
         if (System.getProperty("jdbc.drivers", null) == null) {
            System.setProperty("jdbc.drivers", "oracle.jdbc.driver.OracleDriver");
         }
         if (System.getProperty("db.url", null) == null) {
            System.setProperty("db.url", "jdbc:oracle:thin:@localhost:1521:test");
         }
         if (System.getProperty("db.user", null) == null) {
            System.setProperty("db.user", "xmlblaster");
         }
         if (System.getProperty("db.password", null) == null) {
            System.setProperty("db.password", "xbl");
         }
         SpecificOracle oracle = new SpecificOracle();
         I_Info info = new PropertiesInfo(System.getProperties());
         oracle.init(info);
         I_DbPool pool = (I_DbPool) info.getObject("db.pool");
         Connection conn = pool.reserve();
         String objectTypes = info.get("objectTypes", null);
         String schema = info.get("schema", "AIS");
         String referencedSchema = info.get("referencedSchema", null);
         oracle.cleanupSchema(schema, objectTypes, referencedSchema);
         conn = SpecificDefault.releaseIntoPool(conn, COMMIT_NO, pool);
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
   
   private int cleanupOp(Connection conn, ArrayList names, String schema, String prefix, String postfix) throws Exception {
      int sum = 0;
      for (int i=0; i < names.size(); i++) {
         Statement st = null;
         try {
            String name = (String)names.get(i);
            if (name.indexOf('$') > -1)
               continue;
            if (schema != null)
               name = schema + "." + name;
            // String sql = "DROP TABLE "  + name + " CASCADE CONSTRAINTS";
            String sql = prefix + " " + name + " " + postfix;
            st = conn.createStatement();
            log.fine("statement: " + sql + "' for cleanup");
            sum += st.executeUpdate(sql);
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         finally {
            if (st != null)
               st.close();
         }
      }
      return sum;
   }
   
   public int wipeoutSchema(String catalog, String schema, boolean[] objectsToWipeout) throws Exception {
      if (objectsToWipeout == null)
         objectsToWipeout = WIPEOUT_ALL;
      int sum = 0;
      int count = 0;
      int maxCount = 50;
      int oldSum = 0;
      while (count < maxCount) {
         sum = wipeoutSchemaSingleSweep(catalog, schema, objectsToWipeout);
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

   /**
    * 
    * @param conn
    * @param st
    * @param rs
    * @return
    * @throws SQLException
    */
   private int invokeListStatement(Connection conn, List names, String sql) throws SQLException {
      Statement st = null;
      ResultSet rs = null;
      
      try {
         st = conn.createStatement();
         rs = st.executeQuery(sql);
         while (rs.next())
            names.add(rs.getString(1));
         log.info("processing '" + names.size() + "' entries");
         return names.size();
      }
      finally {
         try {
            if (rs != null)   
               rs.close();
         }
         catch (Throwable ex) { ex.printStackTrace(); }
         try {
            if (st != null)
               st.close();
         }
         catch (Throwable ex) { ex.printStackTrace(); }
      }
   }

   private int wipeoutSchemaSingleSweep(String catalog, String schema, boolean[] objectsToWipeout) throws Exception {
      Connection conn = null;
      int sum = 0;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         
         try {  
            // TRIGGERS
            if (objectsToWipeout[WIPEOUT_TRIGGERS]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='TRIGGER'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP TRIGGER", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         
         try {  // SEQUENCES
            if (objectsToWipeout[WIPEOUT_SEQUENCES]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='SEQUENCE'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP SEQUENCE", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // FUNCTIONS
            if (objectsToWipeout[WIPEOUT_FUNCTIONS]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='FUNCTION'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP FUNCTION", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // PACKAGES
            if (objectsToWipeout[WIPEOUT_PACKAGES]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='PACKAGE'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP PACKAGE", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // PROCEDURES
            if (objectsToWipeout[WIPEOUT_PROCEDURES]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='PROCEDURE'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP PROCEDURE", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // VIEWS
            if (objectsToWipeout[WIPEOUT_VIEWS]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='VIEW'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP VIEW", "CASCADE CONSTRAINTS");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // TABLES
            if (objectsToWipeout[WIPEOUT_TABLES]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='TABLE'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP TABLE", "CASCADE CONSTRAINTS");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // SYNONYMS
            if (objectsToWipeout[WIPEOUT_SYNONYMS]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='SYNONYM'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP SYNONYM", "");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
         try {  // INDEXES
            if (objectsToWipeout[WIPEOUT_INDEXES]) {
               ArrayList names = new ArrayList();
               String sql = "SELECT OBJECT_NAME FROM ALL_OBJECTS WHERE OWNER='" + schema + "' AND OBJECT_TYPE='INDEX'";
               log.info("going to execute sql statement '" + sql + "'");
               sum += invokeListStatement(conn, names, sql);
               // since cleanupOp does not really return the number of effectively removed entries
               if (names.size() > 0)
                  cleanupOp(conn, names, schema, "DROP INDEX", "FORCE");
            }
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }

      }
      catch (SQLException ex) {
        conn = removeFromPool(conn, ROLLBACK_NO);
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
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
         conn = removeFromPool(conn, ROLLBACK_NO);
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
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }

   /**
    * returns true if the sequence exists already.
    */
   protected boolean sequenceExists(Connection conn, String sequenceName) throws Exception {
      Statement st = null;
      try {
         st = conn.createStatement();
         ResultSet rs = st.executeQuery("SELECT * from ALL_SEQUENCES WHERE SEQUENCE_NAME='" + sequenceName + "'");
         return rs.next();
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Exception ex) {
               log.warning("An exception occured when closing the statement, but the result will be considered: " + ex.getMessage());
               ex.printStackTrace();
            }
         }
      }
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#triggerExists(java.sql.Connection, org.xmlBlaster.contrib.replication.TableToWatchInfo)
    */
   public boolean triggerExists(Connection conn, TableToWatchInfo tableToWatch) throws Exception {
      PreparedStatement st = null;
      try {
         String sql = "SELECT table_name, table_owner, status FROM all_triggers where trigger_name=?";
         st = conn.prepareStatement(sql);
         String triggerName = tableToWatch.getTrigger();
         st.setString(1, triggerName);
         ResultSet rs = st.executeQuery();
         if (rs.next()) {
            String tableName = rs.getString(1);
            String tableOwner = rs.getString(2);
            String status = rs.getString(3);
            boolean ret = true;
            if (!tableName.equalsIgnoreCase(tableToWatch.getTable())) {
               log.warning("trigger '" + triggerName + "' exists, is on table '" + tableName + "' but is expected to be on '" + tableToWatch.getTable() + "'");
               ret = false;
            }
            if (!tableOwner.equalsIgnoreCase(tableToWatch.getSchema())) {
               log.warning("trigger '" + tableToWatch.getTrigger() + "' exists, is onwed by schema '" + tableOwner + "' but is expected to be on '" + tableToWatch.getSchema() + "'");
               ret = false;
            }
            if (!status.equalsIgnoreCase("ENABLED")) {
               log.warning("trigger '" + tableToWatch.getTrigger() + "' exists but is not enabled");
               ret = false;
            }
            if (!ret) {
               tableToWatch.setStatus(TableToWatchInfo.STATUS_REMOVE);
               tableToWatch.storeStatus(this.replPrefix, this.dbPool);
            }
            return ret;
         }
         return false;
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
}
