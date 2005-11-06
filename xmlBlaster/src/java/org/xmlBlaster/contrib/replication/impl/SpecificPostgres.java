/*------------------------------------------------------------------------------
 Name:      SpecificPostgres.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.Types;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;

public class SpecificPostgres extends SpecificDefault {

   /**
    * Not doing anything.
    */
   public SpecificPostgres() {
      super();
   }

   /**
    * Helper method used to construct the CREATE TABLE statement part belonging
    * to a single COLUMN.
    * 
    * @param colInfoDescription
    * @return
    */
   public StringBuffer getColumnStatement(SqlColumn colInfoDescription) {
      String type = colInfoDescription.getType();
      /*
       * if (charLength > 0) { type = type + "[" + charLength + "]"; }
       */
      StringBuffer buf = new StringBuffer(colInfoDescription.getColName())
            .append(" ").append(type);
      return buf;
   }

   /**
    * 
    * @param col
    * @param prefix
    *           can be 'old' or 'new'
    * @return
    */
   protected String createVariableSqlPart(SqlDescription description,
         String prefix) {
      SqlColumn[] cols = description
            .getColumns();
      StringBuffer buf = new StringBuffer("       ").append(prefix).append(
            "Cont = '';\n");

      for (int i = 0; i < cols.length; i++) {
         String colName = cols[i].getColName();
         int type = cols[i].getSqlType();
         if (type == Types.BINARY || type == Types.BLOB
               || type == Types.JAVA_OBJECT || type == Types.VARBINARY
               || type == Types.STRUCT) {
            buf.append("       blobCont = " + prefix + "." + colName + ";\n");
            buf.append("       tmp = " + prefix
                  + "Cont || " + this.replPrefix + "col2xml_base64('" + colName
                  + "', blobCont);\n");
            buf.append("       " + prefix + "Cont = tmp;\n");
         } else {
            buf.append("       tmp = " + prefix + "Cont || " + this.replPrefix + "col2xml('"
                  + colName + "'," + prefix + "." + colName + ");\n");
            buf.append("       " + prefix + "Cont = tmp;\n");
         }
      }
      buf.append("       oid = ").append(prefix).append(".oid;\n");
      return buf.toString();
   }

   /**
    * This method creates a function to be associated to a trigger to detect
    * INSERT DELETE and UPDATE operations on a particular table.
    * 
    * @param infoDescription
    *           the info object containing the necessary information for the
    *           table.
    * @return a String containing the sql update. It can be executed.
    */
   public String createTableFunction(SqlDescription infoDescription, String functionName) {

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
      buf.append("\n");
      buf.append("CREATE OR REPLACE FUNCTION ").append(functionName).append("() RETURNS trigger AS $").append(functionName).append("$\n");
      buf.append("DECLARE blobCont BYTEA; \n");
      buf.append("        oldCont TEXT; \n");
      buf.append("   newCont TEXT;\n");
      buf.append("   comment TEXT;\n");
      buf.append("   oid     TEXT;\n");
      buf.append("   tmp     TEXT;\n");
      buf.append("BEGIN\n");
      buf.append("    oldCont = NULL;\n");
      buf.append("    newCont = NULL;\n");
      buf.append("    tmp = " + this.replPrefix + "check_structure();\n");
      buf.append("\n");
      buf.append("    IF (TG_OP != 'INSERT') THEN\n");

      buf.append(createVariableSqlPart(infoDescription, "old"));
      buf.append("    END IF;\n");
      buf.append("\n");
      buf.append("    IF (TG_OP != 'DELETE') THEN\n");

      buf.append(createVariableSqlPart(infoDescription, "new"));
      buf.append("\n");
      buf.append("    END IF;\n");
      buf.append("    INSERT INTO " + this.replPrefix + "items (trans_key, dbId, tablename, guid,\n");
      buf.append("                           db_action, db_catalog, db_schema, \n");
      buf.append("                           content, oldContent, version) values \n");
      buf.append("                           (CURRENT_TIMESTAMP,current_database(),\n");
      buf.append("            TG_RELNAME, oid, TG_OP, NULL, current_schema(), newCont, \n");
      buf.append("            oldCont, '0.0');\n");
      buf.append("    tmp = inet_client_addr();\n");
      buf.append("\n");
      buf.append("    IF (TG_OP = 'DELETE') THEN RETURN OLD;\n");
      buf.append("    END IF;\n");
      buf.append("    RETURN NEW;\n");
      buf.append("END;\n");
      buf.append("$").append(functionName).append("$ LANGUAGE 'plpgsql';\n");
      buf.append("\n");
      return buf.toString();
   }

   /**
    * This method creates a trigger to detect INSERT DELETE and UPDATE
    * operations on a particular table.
    * 
    * @param infoDescription
    *           the info object containing the necessary information for the
    *           table.
    * @return a String containing the sql update. It can be executed.
    */
   public String createTableTrigger(SqlDescription infoDescription, String triggerName) {

      String tableName = infoDescription.getIdentity(); // should be the table
                                                         // name
      String functionName = tableName + "_f";

      StringBuffer buf = new StringBuffer();
      buf.append(createTableFunction(infoDescription, functionName));
      
      // and now append the associated trigger ....
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("-- THE TRIGGER FOR THE replTest TABLE                                           \n");
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("\n");
      buf.append("-- DROP TRIGGER ").append(triggerName).append(" ON ").append(tableName).append(" CASCADE;\n");
      buf.append("CREATE TRIGGER ").append(triggerName).append("\n");
      buf.append("AFTER UPDATE OR DELETE OR INSERT\n");
      buf.append("ON ").append(tableName).append("\n");
      buf.append("FOR EACH ROW\n");
      buf.append("EXECUTE PROCEDURE ").append(functionName).append("();\n");
      buf.append("\n");
      return buf.toString();
   }

   /**
    * Adds a schema to be watched. By Oracle it would add triggers to the schema. 
    * @param catalog
    * @param schema
    * @throws Exception
    */
   public void addSchemaToWatch(Connection conn, String catalog, String schema) throws Exception {
      // do nothing as a default
   }

   public boolean removeTrigger(String triggerName, String tableName, boolean isSchemaTrigger) {
      if (isSchemaTrigger || triggerName == null) // we don't have schema triggers 
         return false;
      try {
         this.dbPool.update("DROP TRIGGER " + triggerName + " ON " + tableName + " CASCADE");
         return true;
      }
      catch (Exception ex) {
         return false;
      }
   }

   
   
}
