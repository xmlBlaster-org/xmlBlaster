/*------------------------------------------------------------------------------
Name:      SqlDescription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xml.sax.InputSource;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.SqlInfoParser;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.dbwriter.I_Parser;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.impl.SearchableConfig;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * This info object is mainly used for two purposes: the one is the parsed object returned by each message.
 * The other is as an info object for each table in the replication (hold in cache to contain meta information
 * of the table on which to perform the operations (either INSERT, UPDATE or DELETE).
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */

public class SqlDescription {

   public final static String ME = "SqlDescription";
   
   public final static String DESC_TAG = "desc";
   
   public final static String IDENT_TAG = "ident";
   
   public final static String COMMAND_TAG = "command";
   
   final static String OLD_PREFIX = "<?xml version='1.0' encoding='UTF-8' ?>\n" +
                                      "<sql>\n" + 
                                      "  <desc><command>REPLICATION</command><ident>FAKE</ident></desc>\n" +
                                      "  <row num='0'>\n";

   final static String OLD_POSTFIX = "  </row>\n</sql>\n";

   private String identity;
   private String command;
   
   private List columnList;
   
   private String updateStatementTxt;
   private String deleteStatementTxt;
   private String insertStatementTxt;
   private boolean hasAddedStatements;

   private Map attributes;
   private List attributeKeys;
   private boolean caseSensitive;
   private boolean pk;
   private boolean pkKnown;
   private boolean schemaKnown;
   private String schema; // since this is contained in the col info
   private String charSet = null;
   
   /** this is only needed for tables which do not have any PK and on updates */
   private I_Parser parser;
   
   private static Logger log = Logger.getLogger(SqlDescription.class.getName());

   private I_Info info;
   
   /** 
    * if set, it means the configuration has specified which columns have to be used for searches
    * in this table. It is used for delete and update to find the entry on which to perform the
    * operation
    */
   private volatile Set configuredSearchableColumns;
   
   /**
    * Gets the name of the schema. Since this information is not contained in the object iself but in the
    * Column information (since views could be a combination of more than one schema or catalog), this 
    * method checks that the schema is the same for all columns. If it is different, or if it is not
    * assigned, then null is returned.
    * @return
    */
   public String getSchema() {
      if (this.schemaKnown)
         return this.schema;
      synchronized(this) {
         if (this.schemaKnown)
            return this.schema;
         this.schema = extractColumnInfo(true); // false means catalog, true means schema
      }
      return this.schema;
   }
   
   public String getCompleteTableName() {
      String schema = getSchema();
      if (schema == null || schema.length() < 1)
         return this.identity;
      return schema + "." + this.identity;
   }
   
   /**
    * Gets the name of the schema. Since this information is not contained in the object iself but in the
    * Column information (since views could be a combination of more than one schema or catalog), this 
    * method checks that the catalog is the same for all columns. If it is different, or if it is not
    * assigned, then null is returned.
    * @return
    */
   public String getCatalog() {
      return extractColumnInfo(false); // false means catalog, true means schema
   }
   
   /**
    * Used by getSchema and getCatalog.
    * @return
    */
   private final synchronized String extractColumnInfo(boolean isSchema) {
      String ret = null;
      String tmp = null;
      if (this.columnList != null && this.columnList.size() > 0) {
         for (int i=0; i < this.columnList.size(); i++) {
            if (isSchema)
               tmp = ((SqlColumn)this.columnList.get(i)).getSchema();
            else 
               tmp = ((SqlColumn)this.columnList.get(i)).getCatalog();
            if (i == 0)
               ret = tmp;
            else {
               if (ret == null || tmp == null)
                  return null;
               if (!ret.equalsIgnoreCase(tmp))
                  return null;
            }
         }
         return ret;
      }
      return null;
   }
   
   public SqlDescription(I_Info info) {
      this.columnList = new ArrayList();
      this.attributes = new HashMap();
      this.attributeKeys = new ArrayList();
      this.caseSensitive = info.getBoolean(DbWriter.CASE_SENSITIVE_KEY, false);
      this.charSet = info.get("charSet", null);
      this.info = info;
      try {
         this.parser = new SqlInfoParser();
         this.parser.init(info);
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }


   public String[] getAttributeNames() {
      return (String[])this.attributeKeys.toArray(new String[this.attributeKeys.size()]);
   }

   public void clearColumnDescriptions() {
      if (this.columnList != null)
         this.columnList.clear();
   }
   
   public Map getAttributesClone() {
      if (this.attributes == null)
         return new HashMap();
      return new HashMap(this.attributes);
   }
   
   /**
    * Returns the requested attribute. If 'caseSensitive' has been set, the characters of the key are compared
    * case sensitively. If it is set to false, then it first searches for the case sensitive match, if nothing
    * is found it looks for the lowercase of the key, and finally if still no match it looks for the uppercase
    * alternative. If none of these is found, null is returned.
    *  
    * @param key the key of the attribute
    * @return the ClientProperty object associated with the key, or if none found, null is returned.
    */
   public ClientProperty getAttribute(String key) {
      ClientProperty prop = (ClientProperty)this.attributes.get(key);
      if (!this.caseSensitive && prop == null) {
         prop = (ClientProperty)this.attributes.get(key.toLowerCase());
         if (prop == null)
            prop = (ClientProperty)this.attributes.get(key.toUpperCase());
      }
      return prop;
   }
   
   
   /**
    * Stores the client property as a new value.
    * 
    * @param value the value to store as an attribute.
    */
   public void setAttribute(ClientProperty value) {
      SqlRow.storeProp(value, this.attributes, this.attributeKeys);
   }
   
   /**
    * Stores the String as a new value. The passed String is directly transformed into a ClientProperty object. 
    * @param value the value to store as an attribute.
    */
   public void setAttribute(String key, String value) {
      ClientProperty prop = new ClientProperty(key, null, null, value);
      SqlRow.storeProp(prop, this.attributes, this.attributeKeys);
   }
   
   /**
    * It copies (stores) all entries found in the map into the attributes. As values only String and ClientProperty
    * objects are allowed. If another type is found, an IllegalArgumentException is thrown. If null is passed, 
    * nothing is done.
    * 
    * @param map
    */
   public void addAttributes(Map map) {
      SqlRow.addProps(map, this.attributes, this.attributeKeys);
   }

   /**
    * Note this method has only sense when used in such cases where all columns belonging to this
    * description are in the same table.
    * 
    * @return
    */
   private final boolean hasPk() {
      if (this.pkKnown)
         return this.pk;
      synchronized (this) {
         if (this.pkKnown)
            return this.pk;
         this.pk = false;
         this.pkKnown = true;
         for (int i=0; i < this.columnList.size(); i++) {
            SqlColumn col = (SqlColumn)this.columnList.get(i); 
            if (col.isPrimaryKey()) {
               this.pk = true;
               return this.pk;
            }
         }
         return this.pk;
      }
   }
   
   final private boolean canAddColToSearch(SqlColumn sqlCol) {
      if (isColumnSearchConfigured(null))
         return isColumnSearchConfigured(sqlCol.getColName());
      return (sqlCol.isPrimaryKey() || !hasPk()) && sqlCol.isSearchable();
   }
   
   /**
    * 
    * @param searchEntries an empty list. This will be filled with the values of the
    * entries (ClientProperties) found in the row object: also NULL objects are considered.
    * and which can be used as search path (either pk or all).
    * @return a string containing the search statement. As an example:
    * " WHERE one=? AND two=? AND three=? " 
    */
   private final String createWhereStatement(SqlRow row, List searchEntries) {
      String[] colNames = row.getColumnNames();
      StringBuffer buf = new StringBuffer(256);
      buf.append(" WHERE ");
      boolean firstHit = true;
      for (int i=0; i < colNames.length; i++) {
         ClientProperty colContent = row.getColumn(colNames[i]);
         SqlColumn sqlCol = getColumn(colNames[i]);
         if (sqlCol == null) {
            log.info("column '" + colNames[i] + "' not found, will ignore it");
            continue;
         }
         // if ((sqlCol.isPrimaryKey() || !hasPk()) && sqlCol.isSearchable()) {
         boolean isNull = colContent.getType() != null && Constants.TYPE_NULL.equals(colContent.getType());
         if (canAddColToSearch(sqlCol)) {
            if (firstHit)
               firstHit = false;
            else
               buf.append("AND ");
            if (isNull) {
               buf.append(colNames[i]).append(" is NULL ");
            }
            else {
               searchEntries.add(colContent);
               buf.append(colNames[i]).append("=? ");
            }
         }
      }
      /*
       * This code does not work since the COL=NULL gives always back nothing (at least in oracle)
      if (!hasPk()) { // find possible NULL which will serve to determine uniqueness
         for (int i=0; i < this.columnList.size(); i++) {
            String colName = ((SqlColumn)this.columnList.get(i)).getColName();
            ClientProperty prop = row.getColumn(colName);
            if (prop == null) {
               buf.append(" AND ").append(colName).append("=NULL");
            }
         }
      }
      */
      return buf.toString();
   }
   
   /**
    * 
    * @param searchEntries an empty list. This will be filled with the values of the
    * entries (ClientProperties) found in the row object. Also NULL objects are now
    * added and which can be used as search path (either pk or all).
    * @return a string containing the search statement. As an example:
    * " (first, second, third) VALUES ( ? , ? , ? ) " 
    */
   private final String createInsertStatement(SqlRow row, List searchEntries) {
      String[] colNames = row.getColumnNames();
      StringBuffer buf1 = new StringBuffer(256);
      StringBuffer buf2 = new StringBuffer(256);
      buf1.append(" ( ");
      boolean firstHit = true;
      for (int i=0; i < colNames.length; i++) {
         ClientProperty colContent = row.getColumn(colNames[i]);
         if (getColumn(colNames[i]) != null) {
            searchEntries.add(colContent);
            if (firstHit) {
               firstHit = false;
            }
            else {
               buf1.append(", ");
               buf2.append(", ");
            }
            buf1.append(colNames[i]).append(" ");
            buf2.append("? ");
         }
      }
      buf1.append(") VALUES (").append(buf2).append(")");
      return buf1.toString();
   }
   
   /**
    * 
    * @param searchEntries an empty list. This will be filled with the values of the
    * entries (ClientProperties) found in the row object. Also null objects are added.
    * and which can be used as search path (either pk or all).
    * @return a string containing the search statement. As an example:
    * " SET one=? , two=? , three=? " 
    */
   private final String createSetStatement(SqlRow row, List searchEntries) {
      String[] colNames = row.getColumnNames();
      StringBuffer buf = new StringBuffer(256);
      buf.append(" SET ");
      boolean firstHit = true;
      for (int i=0; i < colNames.length; i++) {
         ClientProperty colContent = row.getColumn(colNames[i]);
         if (true) { // we need all entries
            searchEntries.add(colContent);
            if (firstHit)
               firstHit = false;
            else
               buf.append(", ");
            buf.append(colNames[i]).append("=? ");
         }
      }
      return buf.toString();
   }

   private final byte[] cutEndIfTooLong(SqlColumn col, String colName, byte[] val) {
      // if too log cut the end
      int colSize = col.getColSize();
      if (colSize < 1 || val.length <= colSize)
         return val;
      
      int sqlType = col.getSqlType();
      if (colSize < 1 || sqlType == Types.BLOB || sqlType == Types.CLOB || sqlType == Types.LONGVARBINARY || sqlType == Types.LONGVARCHAR) {
         log.fine("Not cutting entry '" + col.getColName() + "' since colSize=" + colSize + " and sqlType=" + sqlType);
         return val;
      }      
      log.warning("The entry on column='" + colName + "' is too long: " + val.length + " but should be max " + colSize + ". Will cut the end");
      byte[] tmpBuf = val;
      val = new byte[colSize];
      for (int i=0; i < colSize; i++)
         val[i] = tmpBuf[i];
      return val;
   }
   
   private final double getDouble(ClientProperty prop) {
      double val = prop.getDoubleValue();
      return val;
   }
   
   private final long getLong(ClientProperty prop) {
      try {
         long val = prop.getLongValue();
         return val;
      }
      catch (NumberFormatException ex) {
         double val = getDouble(prop);
         return (long)val;
      }
   }
   
   private final void insertIntoStatement(PreparedStatement st, int pos, ClientProperty prop) throws SQLException, IOException, ParseException  {
      String colName = prop.getName();
      SqlColumn col = getColumn(colName);
      int sqlType = col.getSqlType();

      boolean isNull = prop != null && Constants.TYPE_NULL.equals(prop.getType());
      if (prop == null) {
         st.setObject(pos, null);
         return;
      }
      String tmp = prop.getStringValue();
      if (sqlType == Types.INTEGER) {
         if (isNull || tmp == null || tmp.trim().length() < 1) {
            st.setNull(pos, Types.INTEGER);
            return;
         }
         long val = getLong(prop);
         log.fine("Handling insert column=" + colName + " as INTEGER (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setLong(pos, val);
      }
      if (sqlType == Types.DECIMAL) {
         if (isNull || tmp == null || tmp.trim().length() < 1) {
            st.setNull(pos, Types.DECIMAL);
            return;
         }
         double val = getDouble(prop);
         log.fine("Handling insert column=" + colName + " as DECIMAL (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setDouble(pos, val);
      }
      else if (sqlType == Types.SMALLINT) {
         if (isNull || tmp == null || tmp.trim().length() < 1) {
            st.setNull(pos, Types.SMALLINT);
            return;
         }
         // int val = prop.getIntValue();
         int val = (int)getLong(prop);
         log.fine("Handling insert column=" + colName + " as SMALLINT (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setInt(pos, val);
      }
      else if (sqlType == Types.DOUBLE) {
         if (isNull || tmp == null || tmp.trim().length() < 1) {
            st.setNull(pos, Types.DOUBLE);
            return;
         }
         // double val = prop.getDoubleValue();
         double val = getDouble(prop);
         log.fine("Handling insert column=" + colName + " as DOUBLE (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setDouble(pos, val);
      }
      else if (sqlType == Types.FLOAT) {
         if (isNull || tmp == null || tmp.trim().length() < 1) {
            st.setNull(pos, Types.FLOAT);
            return;
         }
         // float val = prop.getFloatValue();
         float val = (float)getDouble(prop);
         log.fine("Handling insert column=" + colName + " as FLOAT (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setFloat(pos, val);
      }
      else if (sqlType == Types.VARBINARY) {
         if (isNull) {
            st.setNull(pos, Types.VARBINARY);
            return;
         }
         byte[] val = prop.getBlobValue();
         val = cutEndIfTooLong(col, colName, val);
         log.fine("Handling insert column=" + colName + " as VARBINARY (type=" + sqlType + ", count=" + pos + ")");
         st.setBytes(pos, val);
      }
      else if (sqlType == Types.VARCHAR) {
         if (isNull) {
            st.setNull(pos, Types.VARCHAR);
            return;
         }
         String val = prop.getStringValue();
         log.fine("Handling insert column=" + colName + " as VARCHAR (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         // if too log cut the end
         if (col.getCharLength() > 0 && col.getCharLength() < val.length()) {
            log.warning("The entry on column='" + colName + "' is too long: " + val.length() + " but should be max " + col.getCharLength() + ". Will cut the end");
            val = val.substring(0, col.getCharLength());
         }
         st.setString(pos, val);
      }
      else if (sqlType == Types.CHAR) {
         if (isNull) {
            st.setNull(pos, Types.CHAR);
            return;
         }
         String val = prop.getStringValue();
         log.fine("Handling insert column=" + colName + " as CHAR (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         // if too log cut the end
         if (col.getCharLength() > 0 && col.getCharLength() < val.length()) {
            log.warning("The entry on column='" + colName + "' is too long: " + val.length() + " but should be max " + col.getCharLength() + ". Will cut the end");
            val = val.substring(0, col.getCharLength());
         }
         st.setString(pos, val);
      }
      else if (sqlType == Types.BLOB) {
         if (isNull) {
            st.setNull(pos, Types.BLOB);
            return;
         }
         byte[] val = prop.getBlobValue();
         log.fine("Handling insert column=" + colName + " as BLOB (type=" + sqlType + ", count=" + pos + ")");
         val = cutEndIfTooLong(col, colName, val);
         ByteArrayInputStream bais = new ByteArrayInputStream(val);
         st.setBinaryStream(pos, bais, val.length);
      }
      else if (sqlType == Types.CLOB) {
         if (isNull) {
            st.setNull(pos, Types.CLOB);
            return;
         }
         log.fine("Handling insert column=" + colName + " as CLOB (type=" + sqlType + ", count=" + pos + ")");
         byte[] val = prop.getBlobValue();
         val = cutEndIfTooLong(col, colName, val);
         ByteArrayInputStream bais = new ByteArrayInputStream(val);
         st.setAsciiStream(pos, bais, val.length);
      }
      else if (isBinaryType(sqlType)) {
         if (isNull) {
            st.setNull(pos, sqlType);
            return;
         }
         log.fine("Handling insert column=" + colName + " as binary (type=" + sqlType + ", count=" + pos + ")");
         byte[] val = prop.getBlobValue();
         val = cutEndIfTooLong(col, colName, val);
         ByteArrayInputStream blob_stream = new ByteArrayInputStream(val);
         st.setBinaryStream(pos, blob_stream, val.length); //(int)sizeInBytes);
      }
      else if (sqlType == Types.DATE || sqlType == Types.TIMESTAMP) {
         if (isNull || tmp == null || tmp.length() < 1) {
            st.setNull(pos, sqlType);
            return;
         }
         log.fine("Handling insert column=" + colName + " as Date (type=" + sqlType + ", count=" + pos + ")");
         
         String dateTxt = prop.getStringValue();
         Timestamp ts = Timestamp.valueOf(dateTxt);
         // this works even for older oracles where the content is a Date and not a Timestamp
         st.setTimestamp(pos, ts);
      }
      else {
         if (isNull || tmp == null) {
            st.setNull(pos, sqlType);
            return;
         }
         log.fine("Handling insert column=" + colName + " (type=" + sqlType + ", count=" + pos + ")");
         st.setObject(pos, prop.getObjectValue(), sqlType);
      }
   }
   
   
   private boolean isBinaryType(int type) {
      return ( type == Types.BINARY ||
            type == Types.BLOB ||
            type == Types.CLOB ||
            type == Types.DATALINK ||
            type == Types.JAVA_OBJECT ||
            type == Types.LONGVARBINARY ||
            type == Types.OTHER ||
            type == Types.STRUCT ||
            type == Types.VARBINARY);
   }

   private static String getVal(SqlDescription description, String key) {
      ClientProperty prop = description.getAttribute(key);
      if (prop == null)
         return null;
      return prop.getStringValue();
   }
   
   private static void setVal(SqlDescription description, String key, String val) {
      ClientProperty prop = description.getAttribute(key);
      if (prop == null)
         return;
      prop.setValue(val);
   }
   
   private static void processMapping(SqlInfo sqlInfo, I_Mapper mapper) throws Exception {
      SqlDescription description = sqlInfo.getDescription();
      // String originalTable = sqlInfo.getDescription().getIdentity();
      String originalCatalog = getVal(description, ReplicationConstants.CATALOG_ATTR);
      String originalSchema = getVal(description, ReplicationConstants.SCHEMA_ATTR);
      String originalTable = getVal(description, ReplicationConstants.TABLE_NAME_ATTR);
      
      String catalog = mapper.getMappedCatalog(originalCatalog, originalSchema, originalTable, null, originalCatalog);
      String schema = mapper.getMappedSchema(originalCatalog, originalSchema, originalTable, null, originalSchema);
      String table =  mapper.getMappedTable(originalCatalog, originalSchema, originalTable, null, null);
      
      if (catalog != null) {
         setVal(description, ReplicationConstants.CATALOG_ATTR, catalog);
      }
         
      if (schema != null) {
         setVal(description, ReplicationConstants.SCHEMA_ATTR, schema);
      }
         
      if (table != null) {
         setVal(description, ReplicationConstants.TABLE_NAME_ATTR, table);
         description.setIdentity(table);
      }

      List rows = sqlInfo.getRows();
      if (rows != null) {
         for (int i=0; i < rows.size(); i++) {
            SqlRow row = (SqlRow)rows.get(i);
            String[] colNames = row.getColumnNames();
            for (int j=0; j < colNames.length; j++) {
               String colName = colNames[i];
               String newName =  mapper.getMappedTable(originalCatalog, originalSchema, originalTable, colName, colName);
               if (!colName.equals(newName))
                  row.renameColumn(colName, newName);
            }
         }
      }
   }
   
   /**
    * Returns the number of entries updated
    * @param conn
    * @param row
    * @return
    * @throws Exception
    */
   public int update(Connection conn, SqlRow newRow, I_Parser parserForOld) throws Exception {
      PreparedStatement st = null;
      String sql = "";
      try {
         ArrayList entries = new ArrayList();
         String setSt = createSetStatement(newRow, entries);
         int setSize = entries.size();
         if (setSize < 1)
            throw new Exception("SqlDescription.update: could not update since the row did generate an empty set of columns to update. Row: " + newRow.toXml("") + " cols: " + toXml(""));
         if (parserForOld == null)
            throw new Exception("SqlDescription.update: the parser is null. It is needed to parse the old value");
         
         ClientProperty prop = newRow.getAttribute(ReplicationConstants.OLD_CONTENT_ATTR);
         if (prop == null || prop.getValueRaw() == null)
            throw new Exception("The attribute '" + ReplicationConstants.OLD_CONTENT_ATTR + "' was not defined for '" + newRow.toXml("") + "'");

         String xmlLiteral = OLD_PREFIX + prop.getStringValue() + OLD_POSTFIX;
         ByteArrayInputStream bais = new ByteArrayInputStream(xmlLiteral.getBytes());
         SqlInfo sqlInfo = parserForOld.parse(bais, this.charSet);
         
         // CONVERT
         
         if (sqlInfo.getRowCount() < 1)
            throw new Exception("The string '" + xmlLiteral + "' did not contain any row for '" + newRow.toXml("") + "'");
         
         SqlRow oldRow = (SqlRow)sqlInfo.getRows().get(0);
         String whereSt = createWhereStatement(oldRow, entries);
         /**
          * If it does not have a Primary key it needs to check wether the entry exists
          * and is really unique. If it is not unique it will warn. If nothing is found
          * it will warn too.
          */
         if (!hasPk()) {
            sql = "SELECT count(*) FROM " + getCompleteTableName() + whereSt;
            ResultSet rs = null;
            try {
               st = conn.prepareStatement(sql);
               for (int i=setSize; i < entries.size(); i++) {
                  insertIntoStatement(st, i-setSize+1, (ClientProperty)entries.get(i));
               }
               rs = st.executeQuery();
               if (!rs.next())
                  throw new Exception("When updating '" + newRow.toXml("") + "' for '" + toXml("") + "' the statement '" + sql + "' returned an emtpy result set. Can not determine what to do");
               long entriesFound = rs.getLong(1);
               if (entriesFound == 0) {
                  log.warning("no entries found for the statement '" + sql + "' I will not do anything. The msg was '" + newRow.toXml("") + "' and the old msg was '" + oldRow.toXml("") + "'");
                  
                  return 0;
               }
               if (entriesFound > 1)
                  log.warning("" + entriesFound + " entries found matching the statement '" + sql + "'. I will continue by updating these entries. You probably will get " + entriesFound + " warnings of entries not found in this same transaction. You can then ignore these");
            }
            finally {
               if (rs != null) {
                  try { rs.close(); rs = null; } catch (Exception ex) { }
               }
               if (st != null) {
                  try { st.close(); st = null; } catch (Exception ex) { }
               }
            }
         }
         
         sql = "UPDATE " + getCompleteTableName() + setSt + whereSt;
         st = conn.prepareStatement(sql);

         for (int i=0; i < entries.size(); i++)
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
         return st.executeUpdate();
      }
      catch (Throwable ex) {
         log.severe(" Entry '" + newRow.toXml("") + "' caused a (throwable) exception. Statement was '" + sql + "': " + ex.getMessage());
         if (ex instanceof Exception)
            throw (Exception)ex;
         else
            throw new Exception(ex);
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
   /**
    * Returns the number of entries deleted
    * @param conn
    * @param row
    * @return
    * @throws Exception
    */
   public int delete(Connection conn, SqlRow row) throws Exception {
      PreparedStatement st = null;
      String sql = "";
      try {
         ArrayList entries = new ArrayList();
         String whereSt = createWhereStatement(row, entries);
         /**
          * If it does not have a Primary key it needs to check wether the entry exists
          * and is really unique. If it is not unique it will warn. If nothing is found
          * it will warn too.
          */
         if (!hasPk()) {
            sql = "SELECT count(*) FROM " + getCompleteTableName() + whereSt;
            ResultSet rs = null;
            try {
               st = conn.prepareStatement(sql);
               for (int i=0; i < entries.size(); i++)
                  insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
               rs = st.executeQuery();
               if (!rs.next())
                  throw new Exception("When deleting '" + row.toXml("") + "' for '" + toXml("") + "' the statement '" + sql + "' returned an emtpy result set. Can not determine what to do");
               long entriesFound = rs.getLong(1);
               if (entriesFound == 0) {
                  log.warning("no entries found for the statement '" + sql + "' I will not do anything");
                  return 0;
               }
               if (entriesFound > 1)
                  log.warning("" + entriesFound + " entries found matching the statement '" + sql + "'. I will continue by updating these entries. You probably will get " + entriesFound + " warnings of entries not found in this same transaction. You can then ignore these");
            }
            finally {
               if (rs != null) {
                  try { rs.close(); rs = null; } catch (Exception ex) { }
               }
               if (st != null) {
                  try { st.close(); st = null; } catch (Exception ex) { }
               }
            }
         }
         
         sql = "DELETE FROM " + getCompleteTableName() + whereSt;
         st = conn.prepareStatement(sql);

         for (int i=0; i < entries.size(); i++)
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
         return st.executeUpdate();
      }
      catch (Throwable ex) {
         log.severe(" Entry '" + row.toXml("") + "' caused a (throwable) exception. Statement was '" + sql + "': " + ex.getMessage());
         if (ex instanceof Exception)
            throw (Exception)ex;
         else
            throw new Exception(ex);
      }
      
      finally {
         if (st != null)
            st.close();
      }
   }

   /**
    * Returns the number of entries inserted
    * @param conn
    * @param row
    * @return
    * @throws Exception
    */
   public int insert(Connection conn, SqlRow row) throws Exception {
      PreparedStatement st = null;
      int ret = 0;
      String sql = null;
      try {
         ArrayList entries = new ArrayList();

         String insertSt = createInsertStatement(row, entries);

         sql = "INSERT INTO " + getCompleteTableName() + insertSt;
         st = conn.prepareStatement(sql);

         //StringBuffer buf = new StringBuffer(512);
         for (int i=0; i < entries.size(); i++) {
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
            //if (i>0) buf.append(", ");
            //buf.append((ClientProperty)entries.get(i));
         }
         //log.info("Writing: " + buf.toString());
         
         ret = st.executeUpdate();
         
         return ret;
      }
      catch (SQLException ex) {
         // unique constraint for Oracle: TODO implement also for other DB
         if (ex.getMessage().indexOf("ORA-00001") > -1) {
            log.severe("Entry '" + row.toXml("") + "' exists already. Will ignore it an continue");
            return 0;
         }
         else {
            log.severe(" Entry '" + row.toXml("") + "' caused an exception. Statement was *" + sql + "': " + ex.getMessage());
            throw ex;
         }
      }
      catch (Throwable ex) {
         log.severe(" Entry '" + row.toXml("") + "' caused a (throwable) exception. Statement was '" + sql + "': " + ex.getMessage());
         if (ex instanceof Exception)
            throw (Exception)ex;
         else
            throw new Exception(ex);
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   public String getCommand() {
      return this.command;
   }


   public void setCommand(String command) {
      this.command = command;
   }


   public String getIdentity() {
      return this.identity;
   }


   public void setIdentity(String identity) {
      this.identity = identity;
   }

   public synchronized void addColumn(SqlColumn column) {
      if (this.hasAddedStatements)
         throw new IllegalStateException("SqlDescription.addColumnDescription: can not add columns now since prepared statements have already been created");
      this.columnList.add(column);
      this.pkKnown = false;
   }

   public SqlColumn[] getColumns() {
      return (SqlColumn[])this.columnList.toArray(new SqlColumn[this.columnList.size()]);
   }
   
   public SqlColumn getColumn(String colName) {
      for (int i=0; i < this.columnList.size(); i++) {
         SqlColumn tmp = (SqlColumn)this.columnList.get(i);
         if (tmp == null) 
            continue;
         String tmpColName = tmp.getColName();
         if (tmpColName == null)
            continue;
         if (tmpColName.equalsIgnoreCase(colName))
            return tmp;
      }
      return null;
   }
   
   public int getNumOfColumns() {
      return this.columnList.size();
   }
   
   /**
    * Gets the column at position given by pos. Note that the first position is 1 (not 0).
    * @param pos
    * @return
    * @throws IllegalArgumentException if the number is less than 1 or bigger than the size of the list or if for some reason the entry has not been found.
    */
   public SqlColumn getColumnAtPosition(int pos) {
      if (pos < 1 || pos > this.columnList.size())
         throw new IllegalArgumentException("getColumnAtPosition has wrong argument '" + pos + "' must be in the range [1.." + this.columnList.size() + "] (means inclusive)");
      SqlColumn col = (SqlColumn)this.columnList.get(pos-1); 
      int p = col.getPos(); // fast find 
      if (p == (pos-1))
         return col;
      StringBuffer buf = new StringBuffer();
      for (int i=0; i < this.columnList.size(); i++) {
         col = (SqlColumn)this.columnList.get(i);
         p = col.getPos();
         buf.append(p).append(" ");
         if (p == pos)
            return col;
      }
      throw new IllegalArgumentException("getColumnAtPosition: The position '" + pos + "' has not been found among the ones processed which are '" + buf.toString() + "'");
   }

   public String toXml(String extraOffset) {
      return toXml(extraOffset, false);
   }
   
   final String toXml(String extraOffset, boolean doTruncate) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      if (this.updateStatementTxt != null)
         sb.append(offset).append("<!-- update: ").append(this.updateStatementTxt).append("-->");
      if (this.insertStatementTxt != null)
         sb.append(offset).append("<!-- insert: ").append(this.insertStatementTxt).append("-->");
      if (this.deleteStatementTxt != null)
         sb.append(offset).append("<!-- delete: ").append(this.deleteStatementTxt).append("-->");
      
      sb.append(offset).append("<").append(DESC_TAG).append(">");
      sb.append(offset + "  ").append("<").append(COMMAND_TAG).append(">").append(this.command).append("</").append(COMMAND_TAG).append(">");
      sb.append(offset + "  ").append("<").append(IDENT_TAG).append(">").append(this.identity).append("</").append(IDENT_TAG).append(">");
      
      for (int i=0; i < this.columnList.size(); i++) {
         SqlColumn desc = (SqlColumn)this.columnList.get(i);
         sb.append(desc.toXml(extraOffset + "  "));
         if (doTruncate && sb.length() > SqlInfo.MAX_BUF_SIZE) {
            sb.append(" ...");
            return sb.toString();
         }
      }
      
      Iterator iter = this.attributeKeys.iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         ClientProperty prop = (ClientProperty)this.attributes.get(key);
         sb.append(prop.toXml(extraOffset + "  ", SqlInfoParser.ATTR_TAG));
         if (doTruncate && sb.length() > SqlInfo.MAX_BUF_SIZE) {
            sb.append(" ...");
            return sb.toString();
         }
      }
      sb.append(offset).append("</").append(DESC_TAG).append(">");
      return sb.toString();
   }
   
   public static String getDifferences(SqlDescription description1, SqlDescription description2) {
      if (description1 == null || description2 == null)
         return "";
      
      Map map1 = new HashMap();
      Map map2 = new HashMap();
      SqlColumn[] cols = description1.getColumns();
      for (int i=0; i < cols.length; i++) {
         String name = /*cols[i].getCatalog() + "." +*/ cols[i].getSchema() + "." + cols[i].getTable() + "." + cols[i].getColName();
         map1.put(name, cols[i]);
      }
      cols = description2.getColumns();
      for (int i=0; i < cols.length; i++) {
         String name = /*cols[i].getCatalog() + "." +*/ cols[i].getSchema() + "." + cols[i].getTable() + "." + cols[i].getColName();
         map2.put(name, cols[i]);
      }
      
      // scan map1
      Map ret = new TreeMap();
      String[] keys = (String[])map1.keySet().toArray(new String[map1.size()]);
      for (int i=0; i < keys.length; i++) {
         SqlColumn[] val = new SqlColumn[2];
         SqlColumn val1 = (SqlColumn)map1.get(keys[i]);
         SqlColumn val2 = (SqlColumn)map2.remove(keys[i]);
         if (val2 == null) { // then the column has been deleted
            val[0] = val1;
            val[1] = null;
            ret.put(keys[i], val);
         }
         else { // check if it has changed
            if(!val1.isSame(val2)) {
               ret.put(keys[i], new SqlColumn[] { val1, val2 });
            }
         }
      }
      // and now scam map2 for still remaining (new) columns
      keys = (String[])map2.keySet().toArray(new String[map2.size()]);
      for (int i=0; i < keys.length; i++) {
         SqlColumn[] val = new SqlColumn[2];
         val[0] = null;
         val[1] = (SqlColumn)map2.get(keys[i]);
         ret.put(keys[i], val);
      }
      // and now build the output string ...
      StringBuffer buf = new StringBuffer(1024);
      buf.append("<descDiff>\n");
      Iterator iter = ret.keySet().iterator();
      while (iter.hasNext()) {
         String name = (String)iter.next();
         buf.append("\n\n\n  <!-- column ").append(name);   
         SqlColumn[] val = (SqlColumn[])ret.get(name);
         if (val[0] == null) { // new column
            buf.append("  NEW -->\n");
            buf.append(val[1].toXml("  "));
         }
         else if (val[1] == null) { // deleted column
            buf.append("  DELETED -->\n");
            buf.append(val[0].toXml("  "));
         }
         else { // modified column
            buf.append("  MODIFIED -->\n");
            buf.append(val[0].toXml("  "));
            buf.append("\n");
            buf.append(val[1].toXml("  "));
         }
      }
      buf.append("</descDiff>\n");
      return buf.toString();
   }
   
   public static void main(String[] args) {
      try {
         String propFile = System.getProperty("properties", null);
         Properties props = null;
         if (propFile == null) {
            props = System.getProperties();
            System.err.println("not using any properties file");
         }
         else {
            System.err.println("Using properties file '" + propFile + "'");
            props = new Properties(System.getProperties());
            props.load(new FileInputStream(propFile));
         }
         I_Info info = new PropertiesInfo(props);
         
         if (args.length < 2) {
            System.err.println("Usage: " + SqlDescription.class.getName() + " oldFile.xml newFile.xml");
            System.exit(-1);
         }
         
         String oldFile = args[0];
         String newFile = args[1];
         BufferedReader reader = new BufferedReader(new FileReader(oldFile));
         String line = null;
         StringBuffer buf = new StringBuffer(1024);
         while ( (line = reader.readLine()) != null) {
            buf.append(line).append("\n");
         }
         String oldData = buf.toString();
         reader.close();
         reader = new BufferedReader(new FileReader(newFile));
         buf = new StringBuffer(1024);
         while ( (line = reader.readLine()) != null) {
            buf.append(line).append("\n");
         }
         String newData = buf.toString();
         reader.close();
         SqlInfoParser parser = new SqlInfoParser();
         parser.init(info);
         SqlInfo oldInfo = parser.parse(oldData);
         SqlInfo newInfo = parser.parse(newData);
         String ret = SqlDescription.getDifferences(oldInfo.getDescription(), newInfo.getDescription());
         System.out.println(ret);
      } 
      catch (Throwable ex) {
         ex.printStackTrace();
      }
   }

   /**
    * @param column the name of the column to check or null if a global check for the table.
    * 
    * @return true if the specified column is configured as searchable false otherwise. If you 
    * passed null as the column name, then true is returned if at least one column is searchable,
    * false otherwise.
    * 
    */
   public boolean isColumnSearchConfigured(String column) {
      if (this.configuredSearchableColumns == null) {
         synchronized (this) {
            if (this.configuredSearchableColumns == null) {
               SearchableConfig searchableConfig = (SearchableConfig)this.info.getObject(SearchableConfig.NAME);
               String catalog = getCatalog();
               String table = this.identity;
               if (searchableConfig != null)
                  this.configuredSearchableColumns = searchableConfig.getSearchableColumnNames(catalog, getSchema(), table);
               if (this.configuredSearchableColumns == null)
                  this.configuredSearchableColumns = new HashSet();
               else { // do the complete check here
                  if (this.hasPk())
                     log.warning("The table '" + getCompleteTableName() + "' has primary keys defined. You configured explicitly searchable columns which overwrite the PK defaults. I hope you know what you are doing");
                  String[] columns = (String[])this.configuredSearchableColumns.toArray(new String[this.configuredSearchableColumns.size()]);
                  for (int i=0; i < columns.length; i++) {
                     SqlColumn col = getColumn(columns[i]);
                     if (col == null)
                        log.warning("The column '" + columns[i] + "' was not found on table '" + getCompleteTableName() + "' but you have configured it to be searchable: is this really correct ? Are you maybe adding the table later ?");
                     else {
                        if (!col.isSearchable()) {
                           log.warning("The column '" + columns[i] + "' is not searchable. Can not be used to search: will remove it from the searchable list of columns: please update your configuration file");
                           this.configuredSearchableColumns.remove(columns[i]);
                        }
                     }
                  }
               }
            }
         }
      }
      if (column == null) // we have forced an initialization even with null.
         return this.configuredSearchableColumns.size() > 0; 
      return this.configuredSearchableColumns.contains(column);
   }
   
}

