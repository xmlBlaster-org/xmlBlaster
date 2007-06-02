/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.Types;
import java.util.Map;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;

public class SpecificDB2 extends SpecificDefault {

   protected boolean sequenceExists(Connection conn, String sequenceName) throws Exception {
      if (true)
         throw new IllegalStateException("not implemented");
      return false;
   }

   protected boolean triggerExists(Connection conn, String triggerName) throws Exception {
      if (true)
         throw new IllegalStateException("not implemented");
      return false;
   }

   // private static Logger log = Logger.getLogger(SpecificDB2.class.getName());

   /**
    * Not doing anything.
    */
   public SpecificDB2() {
      super();
   }

   public void init(I_Info info) throws Exception {
      super.init(info);
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
      if (true)
         throw new IllegalStateException("not implemented");
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
   protected String createVariableSqlPart(SqlDescription description, String prefix, boolean containsLongs, boolean isInsert) {
      if (true)
         throw new IllegalStateException("not implemented");
      return null;
   }

   public String createTableTrigger(SqlDescription infoDescription, TableToWatchInfo tableToWatch) {
      if (true)
         throw new IllegalStateException("not implemented");
      return null;
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
      if (true)
         throw new IllegalStateException("not implemented");
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
         buf.append("VARCHAR").append("(").append(precision).append(")");
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
    * If the triggerName is null, then the own schema triggers are deleted. If
    * at least one of the triggers has been removed, it returns true.
    */
   public boolean removeTrigger(String triggerName, String tableName, boolean isSchemaTrigger) {
      if (true)
         throw new IllegalStateException("not implemented");
      return false;
   }
   
   public int wipeoutSchema(String catalog, String schema, boolean[] objectsToWipeout) throws Exception {
      if (true)
         throw new IllegalStateException("not implemented");
      return -1;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#getContentFromGuid(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
    */
   public String getContentFromGuid(String guid, String catalog, String schema, String table, I_AttributeTransformer transformer) throws Exception {
      if (true)
         throw new IllegalStateException("not implemented");
      return null;
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#triggerExists(java.sql.Connection, org.xmlBlaster.contrib.replication.TableToWatchInfo)
    */
   public boolean triggerExists(Connection conn, TableToWatchInfo tableToWatch) throws Exception {
      if (true)
         throw new IllegalStateException("not implemented");
      return false;
   }
   
}
