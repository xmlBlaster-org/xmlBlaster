/*------------------------------------------------------------------------------
Name:      DbMetaHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TreeMap;

/**
 * 
 * DbMetaHelper offers methods which take care of stuff specific to particular databases. For
 * example if a database stores table names in uppercase you don't need to bother about this
 * knowledge. You just pass the name of the table to the method getUnquotedIdentifier and you
 * get back the correct name (if uppercase it will be uppercase).
 * 
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DbMetaHelper {

   private final static int CASE_UNKNOWN = 0;
   private final static int CASE_MIXED = 1;
   private final static int CASE_UPPER = 2;
   private final static int CASE_LOWER = 3;

   private int caseSense = CASE_UNKNOWN;

   private int maxProcLength;
   private String productName;
   
   /**
    * Initializes the object by reading the metadata of this database.
    * @param pool the pool must be non null and initialized.
    * @throws Exception If a backend exception occurs.
    * 
    */
   public DbMetaHelper(I_DbPool pool) throws Exception {
      if (pool == null)
         throw new Exception("DbMetaHelper constructor: the pool is null");
      Connection conn = null;
      try {
         conn = pool.reserve();
         DatabaseMetaData meta = conn.getMetaData();
         
         this.maxProcLength = meta.getMaxProcedureNameLength();
         
         if (meta.storesLowerCaseIdentifiers())
            this.caseSense = CASE_LOWER;
         else if (meta.storesUpperCaseIdentifiers())
            this.caseSense = CASE_UPPER;
         else if (meta.storesMixedCaseIdentifiers())
            this.caseSense = CASE_MIXED;
         else
            throw new Exception("DbMetaHelper constructor: can not determine which case the identifiers are stored");
         String tmp = meta.getDatabaseProductName();
         if (tmp != null)
            this.productName = tmp.trim().toUpperCase();
         else
            this.productName = "";
      }
      finally {
         if (conn != null)
            pool.release(conn);
      }
   }
   
   
   /**
    * Returns an array of column names correctly sorted of the specified table.
    * Never returns null, if no columns found it returns an empty array.
    * @throws Exception If a backend exception occurs.
    * 
    */
   public String[] getColumnNames(I_DbPool pool, String catalog, String schema, String table) throws Exception {
      if (pool == null)
         throw new Exception("DbMetaHelper.getColumnNames: the pool is null");
      Connection conn = null;
      ResultSet rs = null;
      int count = 0;
      try {
         conn = pool.reserve();
         DatabaseMetaData meta = conn.getMetaData();
         if (catalog != null)
            catalog = getIdentifier(catalog.trim());
         if (schema != null)
            schema = getIdentifier(schema.trim());
         if (table != null)
            table = getIdentifier(table.trim());
         TreeMap map = new TreeMap();
         rs = meta.getColumns(catalog, schema, table, null);
         while (rs.next()) {
            int pos = rs.getInt("ORDINAL_POSITION");
            String name = rs.getString("COLUMN_NAME");
            // should already be in the correct order according to 
            // javadoc but to be really sure we order it too
            map.put(new Integer(pos), name);
            count++;
         }
         if (count != map.size())
            throw new Exception("Probably multiple tables '" + table + "' found since more columns with same ordinal position found");
         return (String[])map.values().toArray(new String[map.size()]);
      }
      finally {
         try {
            if (rs != null)
               rs.close();
         }
         catch (SQLException ex) {
            ex.printStackTrace();
         }
         if (conn != null)
            pool.release(conn);
      }
   }
   
   
   /**
    * Returns the correct identifier depending on the properties of the database. If it can not
    * determine the case of the identifier it returns null.
    * @param proposedName the name which is proposed.
    * @return
    */
   public String getIdentifier(String proposedName) {
      if (proposedName == null)
         return null;
      switch (this.caseSense) {
      case CASE_MIXED : return proposedName;
      case CASE_UPPER : return proposedName.toUpperCase();
      case CASE_LOWER : return proposedName.toLowerCase();
      default:return null; 
      }
   }
   

   /**
    * If the name is too long it cuts first from the end of the schema, and then from the end of the
    * table name. Otherwise it is ${PREFIX}${SEP}${SCHEMA}${SEP}${TABLENAME}
    * @param schema
    * @param tableName
    * @return
    */
   public String createFunctionName(String prefix, String separator, String schema, String tableName) {
      if (schema == null)
         schema = "";
      if (prefix == null)
         prefix = "";
      if (separator == null)
         separator = "";
      StringBuffer buf = new StringBuffer(this.maxProcLength);
      buf.append(getIdentifier(prefix)).append(separator).append(getIdentifier(schema)).append(separator).append(getIdentifier(tableName));
      if (buf.length() < this.maxProcLength)
         return buf.toString();
      int toCut = buf.length() - this.maxProcLength;
      if (toCut >= schema.length()) {
         toCut -= schema.length();
         schema = "";
      }
      else {
         schema = schema.substring(0, schema.length() - toCut);
      }
      if (toCut > 0)
         tableName = tableName.substring(0, tableName.length() - toCut);
      return createFunctionName(prefix, separator, schema, tableName);
   }
   
   public boolean isOracle() {
      return this.productName.indexOf("ORACLE") != -1;
   }
}
