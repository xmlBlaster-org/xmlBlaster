/*------------------------------------------------------------------------------
Name:      SqlDescription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.dbwriter.I_Parser;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
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
   
   /** this is only needed for tables which do not have any PK and on updates */
   private I_Parser parser;
   
   private static Logger log = Logger.getLogger(SqlDescription.class.getName());

   
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
      try {
         this.parser = new DbUpdateParser();
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
   
   /**
    * 
    * @param searchEntries an empty list. This will be filled with the values of the
    * entries (ClientProperties) found in the row object and which are not null.
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
         if (colContent != null && colContent.getStringValue() != null) {
            SqlColumn sqlCol = getColumn(colNames[i]);
            if (sqlCol == null) {
               log.info("column '" + colNames[i] + "' not found, will ignore it");
               continue;
            }
            if (sqlCol.isPrimaryKey() || !hasPk()) {
               searchEntries.add(colContent);
               if (firstHit)
                  firstHit = false;
               else
                  buf.append("AND ");
               buf.append(colNames[i]).append("=? ");
            }
         }
      }
      return buf.toString();
   }
   
   /**
    * 
    * @param searchEntries an empty list. This will be filled with the values of the
    * entries (ClientProperties) found in the row object and which are not null.
    * and which can be used as search path (either pk or all).
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

         if (colContent != null && colContent.getStringValue() != null && getColumn(colNames[i]) != null) {
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
    * entries (ClientProperties) found in the row object and which are not null.
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
         if (colContent != null && colContent.getStringValue() != null) {
            // SqlColumn sqlCol = getColumn(colNames[i]);
            // if (!sqlCol.isPrimaryKey() || !hasPk()) {
            if (true) { // we need all entries
               searchEntries.add(colContent);
               if (firstHit)
                  firstHit = false;
               else
                  buf.append(", ");
               buf.append(colNames[i]).append("=? ");
            }
         }
      }
      return buf.toString();
   }
   
   /**
    * TODO remove this
    * 
    * @throws SQLException
    * @deprecated
    */
   public synchronized final void addPreparedStatements() throws SQLException {
      if (this.hasAddedStatements)
         return;
      String table = getIdentity();
      StringBuffer buf1 = new StringBuffer();
      StringBuffer buf2 = new StringBuffer();
      StringBuffer bufUpd1 = new StringBuffer();
      StringBuffer bufUpd2 = new StringBuffer();

      boolean firstPK = true;
      String schema = null;
      for (int i=0; i < this.columnList.size(); i++) {
         SqlColumn col = (SqlColumn)this.columnList.get(i); 
         String colName = col.getColName();
         if (schema == null)
            schema = col.getSchema();
         if (i > 0) {
            buf1.append(" , ");
            buf2.append(" , ");
            bufUpd1.append(" , ");
         }
         if (col.isPrimaryKey() || !hasPk()) {
            if (!firstPK) {
               bufUpd2.append(" AND ");
            }
            firstPK = false;
         }
         buf1.append(colName);
         buf2.append(" ? ");
         bufUpd1.append(colName).append("= ? ");
         if (col.isPrimaryKey() || !hasPk()) {
            bufUpd2.append(colName).append(" = ? ");
         }
      }
      
      String wherePart = bufUpd2.toString().trim();
      if (wherePart.length() > 0)
         wherePart = " WHERE " + wherePart;
      
      String completeTable = null;
      if (schema == null || schema.trim().length() < 1)
         completeTable = table;
      else
         completeTable = schema + "." + table;
      this.insertStatementTxt = "INSERT INTO " + completeTable + " (" + buf1.toString() + ") VALUES (" + buf2.toString() + ")";
      this.updateStatementTxt = "UPDATE " + completeTable + " SET " + bufUpd1.toString() + wherePart;
      this.deleteStatementTxt = "DELETE FROM " + completeTable + wherePart;
      this.hasAddedStatements = true;
      log.info("statement for insert: \"" + this.insertStatementTxt + "\"");
      log.info("statement for update: \"" + this.updateStatementTxt + "\"");
      log.info("statement for delete: \"" + this.deleteStatementTxt + "\"");
   }
   
//   private final void insertIntoStatement(String colName, PreparedStatement st, int pos, ClientProperty prop, int sqlType) throws SQLException, IOException  {
   private final void insertIntoStatement(PreparedStatement st, int pos, ClientProperty prop) throws SQLException, IOException, ParseException  {
      String colName = prop.getName();
      SqlColumn col = getColumn(colName);
      int sqlType = col.getSqlType();
      
      if (prop == null) {
         st.setObject(pos, null);
         return;
      }
      String tmp = prop.getStringValue();
      if (tmp != null)
         tmp = tmp.trim();
      if (sqlType == Types.INTEGER) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         long val = prop.getLongValue();
         log.fine("Handling insert column=" + colName + " as INTEGER (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setLong(pos, val);
      }
      if (sqlType == Types.DECIMAL) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         double val = prop.getDoubleValue();
         log.fine("Handling insert column=" + colName + " as DECIMAL (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setDouble(pos, val);
      }
      else if (sqlType == Types.SMALLINT) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         int val = prop.getIntValue();
         log.fine("Handling insert column=" + colName + " as SMALLINT (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setInt(pos, val);
      }
      else if (sqlType == Types.DOUBLE) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         double val = prop.getDoubleValue();
         log.fine("Handling insert column=" + colName + " as DOUBLE (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setDouble(pos, val);
      }
      else if (sqlType == Types.FLOAT) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         float val = prop.getFloatValue();
         log.fine("Handling insert column=" + colName + " as FLOAT (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setFloat(pos, val);
      }
      else if (sqlType == Types.VARBINARY) {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         byte[] val = prop.getBlobValue();
         log.fine("Handling insert column=" + colName + " as VARBINARY (type=" + sqlType + ", count=" + pos + ")");
         st.setBytes(pos, val);
      }
      else if (sqlType == Types.VARCHAR) {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         String val = prop.getStringValue();
         log.fine("Handling insert column=" + colName + " as VARCHAR (type=" + sqlType + ", count=" + pos + ") '" + val + "'");
         st.setString(pos, val);
      }
      else if (sqlType == Types.BLOB) {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         byte[] val = prop.getBlobValue();
         log.fine("Handling insert column=" + colName + " as BLOB (type=" + sqlType + ", count=" + pos + ")");
         ByteArrayInputStream bais = new ByteArrayInputStream(val);
         st.setBinaryStream(pos, bais, val.length);
      }
      else if (sqlType == Types.CLOB) {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         log.fine("Handling insert column=" + colName + " as CLOB (type=" + sqlType + ", count=" + pos + ")");
         byte[] val = prop.getBlobValue();
         ByteArrayInputStream bais = new ByteArrayInputStream(val);
         st.setAsciiStream(pos, bais, val.length);
      }
      else if (isBinaryType(sqlType)) {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         log.info("Handling insert column=" + colName + " as binary (type=" + sqlType + ", count=" + pos + ")");
         ByteArrayInputStream blob_stream = new ByteArrayInputStream(prop.getBlobValue());
         st.setBinaryStream(pos, blob_stream, prop.getBlobValue().length); //(int)sizeInBytes);
      }
      else if (sqlType == Types.DATE || sqlType == Types.TIMESTAMP) {
         if (tmp == null || tmp.length() < 1) {
            st.setObject(pos, null);
            return;
         }
         log.info("Handling insert column=" + colName + " as Date (type=" + sqlType + ", count=" + pos + ")");
         
         String dateTxt = prop.getStringValue();
         Timestamp ts = Timestamp.valueOf(dateTxt);
         // this works even for older oracles where the content is a Date and not a Timestamp
         st.setTimestamp(pos, ts);
      }
      else {
         if (tmp == null) {
            st.setObject(pos, null);
            return;
         }
         log.info("Handling insert column=" + colName + " (type=" + sqlType + ", count=" + pos + ")");
         st.setObject(pos, prop.getObjectValue(), sqlType);
      }
   }
   
   
   /**
    * TODO remove this
    * 
    * @param conn
    * @param row
    * @return
    * @throws Exception
    * @deprecated
    */
   public int insertOLD(Connection conn, SqlRow row) throws Exception {
      addPreparedStatements();
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.insertStatementTxt);
         for (int i=0; i < this.columnList.size(); i++) {
            SqlColumn col = (SqlColumn)this.columnList.get(i); 
            String colName = col.getColName();
            ClientProperty prop = row.getColumn(colName);
            if (prop == null) 
               throw new Exception(ME + ".insert '" + this.identity + "' column '" + colName + "' not found in xml message:" + row.toXml(""));
            insertIntoStatement(st, i+1, prop);
         }
         return st.executeUpdate();
      }
      catch (SQLException ex) {
         log.info("inserting '" + this.insertStatementTxt + "' went wrong");
         throw ex;
      }
      finally {
         if (st != null)
            st.close();
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

   /**
    * TODO remove this
    * @param conn
    * @param row
    * @return
    * @throws Exception
    * @deprecated
    */
   public int deleteOLD(Connection conn, SqlRow row) throws Exception {
      addPreparedStatements();
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.deleteStatementTxt);
         int count = 1;
         for (int i=0; i < this.columnList.size(); i++) {
            SqlColumn col = (SqlColumn)this.columnList.get(i);
            if (col.isPrimaryKey() || !hasPk()) {
               String colName = col.getColName();
               ClientProperty prop = row.getColumn(colName);
               if (prop == null) 
                  throw new Exception(ME + ".delete '" + this.identity + "' column '" + colName + "' not found " + row.toXml(""));
               st.setObject(count++, prop.getObjectValue(), col.getSqlType());
            }
         }
         return st.executeUpdate();
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   
   /**
    * Returns the number of entries updated
    * @param conn
    * @param row
    * @return
    * @throws Exception
    */
   public int update(Connection conn, SqlRow row) throws Exception {
      PreparedStatement st = null;
      try {
         ArrayList entries = new ArrayList();
         String setSt = createSetStatement(row, entries);
         int setSize = entries.size();
         if (setSize < 1)
            throw new Exception("SqlDescription.update: could not update since the row did generate an empty set of columns to update. Row: " + row.toXml("") + " cols: " + toXml(""));
         String whereSt = createWhereStatement(row, entries);
         /**
          * If it does not have a Primary key it needs to check wether the entry exists
          * and is really unique. If it is not unique it will warn. If nothing is found
          * it will warn too.
          */
         if (!hasPk()) {
            String sql = "SELECT count(*) FROM " + getCompleteTableName() + whereSt;
            ResultSet rs = null;
            try {
               st = conn.prepareStatement(sql);
               for (int i=setSize; i < entries.size(); i++) {
                  insertIntoStatement(st, i-setSize+1, (ClientProperty)entries.get(i));
               }
               rs = st.executeQuery();
               if (!rs.next())
                  throw new Exception("When updating '" + row.toXml("") + "' for '" + toXml("") + "' the statement '" + sql + "' returned an emtpy result set. Can not determine what to do");
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
         
         String sql = "UPDATE " + getCompleteTableName() + setSt + whereSt;
         st = conn.prepareStatement(sql);

         for (int i=0; i < entries.size(); i++)
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
         return st.executeUpdate();
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
      try {
         ArrayList entries = new ArrayList();
         String whereSt = createWhereStatement(row, entries);
         /**
          * If it does not have a Primary key it needs to check wether the entry exists
          * and is really unique. If it is not unique it will warn. If nothing is found
          * it will warn too.
          */
         if (!hasPk()) {
            String sql = "SELECT count(*) FROM " + getCompleteTableName() + whereSt;
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
         
         String sql = "DELETE FROM " + getCompleteTableName() + whereSt;
         st = conn.prepareStatement(sql);

         for (int i=0; i < entries.size(); i++)
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
         return st.executeUpdate();
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
      try {
         ArrayList entries = new ArrayList();
         String insertSt = createInsertStatement(row, entries);

         String sql = "INSERT INTO " + getCompleteTableName() + insertSt;
         st = conn.prepareStatement(sql);

         Statement st2 = null;
         // Hack 1
         if (getCompleteTableName().indexOf("C_INS") != -1) {
            try {
               ClientProperty tmpCh = new ClientProperty("COM_CHANNEL", Constants.TYPE_INT, null, "20");
               row.setColumn(tmpCh);
            }
            catch (Exception e1) {
               log.warning("error when trying to add a new column to the row");
               e1.printStackTrace();
            }
         }
         
         for (int i=0; i < entries.size(); i++)
            insertIntoStatement(st, i+1, (ClientProperty)entries.get(i));
         ret = st.executeUpdate();
         
         // hack 2
         if (getCompleteTableName().indexOf("C_INS") != -1) {
            ClientProperty prop = row.getColumn("COM_MESSAGEID");
            long comMsgId = 0L;
            int ch;
            String txtl = null;
            if (prop != null) {
               comMsgId = prop.getLongValue();
            }
            prop = row.getColumn("COM_CHANNEL");
            if (prop != null) {
               ch = prop.getIntValue();
            }
            else
               ch = 20;
            prop = row.getColumn("COM_TXTL");
            if (prop != null) {
               txtl = prop.getStringValue();
               if (txtl == null)
                  txtl = "";
            }
            else
               txtl = "";
            st2 = conn.createStatement();            
            String sql1 = "insert into AIS.C_IN_TEXTS (COM_MESSAGEID, COM_CHANNEL, COM_TXTL) VALUES (" + comMsgId + ", " + ch + ", '" + txtl + "')";
            String sql2 = "delete from AIS.C_INS";
            log.info("fix insert '" + sql1 + "'");
            // log.info("(fix delete '" + sql2 + "'");

            try {
               st2.executeUpdate(sql1);
               try { st2.close(); } catch (Exception e) { e.printStackTrace(); }
               // st2 = conn.createStatement();            
               // st2.executeUpdate(sql2);
            }
            finally {
               try { if (st2 != null) st2.close(); } catch (Exception e) { e.printStackTrace(); }
            }
         }
         return ret;
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   /**
    * TODO remove this
    * 
    * Returns the number of entries updated
    * @param conn
    * @param row
    * @return
    * @throws Exception
    * @deprecated
    */
   public int updateOLD(Connection conn, SqlRow row) throws Exception {
      addPreparedStatements();
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.updateStatementTxt);
         for (int i=0; i < this.columnList.size(); i++) {
            SqlColumn col = (SqlColumn)this.columnList.get(i); 
            String colName = col.getColName();
            ClientProperty prop = row.getColumn(colName);
            if (prop == null) 
               throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + row.toXml(""));
            insertIntoStatement(st, i+1, prop);
         }
         
         int count = this.columnList.size() +1;
         
         if (hasPk()) {
            for (int i=0; i < this.columnList.size(); i++) {
               SqlColumn col = (SqlColumn)this.columnList.get(i);
               if (col.isPrimaryKey()) {
                  String colName = col.getColName();
                  ClientProperty prop = row.getColumn(colName);
                  if (prop == null) 
                     throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + row.toXml(""));
                  insertIntoStatement(st, count++, prop);
               }
            }
         }
         else { // no PK, then we need the old content for 'almost' uniqueness
            ClientProperty oldRowProp = row.getAttribute(ReplicationConstants.OLD_CONTENT_ATTR);
            if (oldRowProp != null) {
               StringBuffer buf = new StringBuffer("<sql><desc></desc><row num='0'>").append(oldRowProp.getStringValue()).append("</row></sql>");
               SqlInfo oldRowDbUpdateInfo = this.parser.parse(buf.toString());
               if (oldRowDbUpdateInfo.getRows().size() < 1)
                  throw new Exception("no old rows retrieved for the entry on update when no PK is defined for the table");
               SqlRow oldRow = (SqlRow)oldRowDbUpdateInfo.getRows().get(0);
               for (int i=0; i < this.columnList.size(); i++) {
                  SqlColumn col = (SqlColumn)this.columnList.get(i);
                  String colName = col.getColName();
                  ClientProperty prop = oldRow.getColumn(colName);
                  if (prop == null) 
                     throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + oldRow.toXml(""));
                  insertIntoStatement(st, count++, prop);
               }
            }
         }
         return st.executeUpdate();
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
      }
      
      Iterator iter = this.attributeKeys.iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         ClientProperty prop = (ClientProperty)this.attributes.get(key);
         sb.append(prop.toXml(extraOffset + "  ", DbUpdateParser.ATTR_TAG));
      }
      sb.append(offset).append("</").append(DESC_TAG).append(">");
      return sb.toString();
   }
   
}




