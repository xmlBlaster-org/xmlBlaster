/*------------------------------------------------------------------------------
Name:      DbUpdateInfoDescription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;


import java.io.ByteArrayInputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
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

public class DbUpdateInfoDescription {

   public final static String ME = "DbUpdateInfoDescription";
   
   public final static String DESC_TAG = "desc";
   
   public final static String IDENT_TAG = "ident";
   
   public final static String COMMAND_TAG = "command";
   
   private String identity;
   private String command;
   
   private List columnDescriptionList;
   
   private String updateStatementTxt;
   private String deleteStatementTxt;
   private String insertStatementTxt;
   private boolean hasAddedStatements;

   private Map attributes;
   private List attributeKeys;
   private boolean caseSensitive;
   private boolean hasPk;
   /** this is only needed for tables which do not have any PK and on updates */
   private I_Parser parser;
   
   private static Logger log = Logger.getLogger(DbUpdateInfoDescription.class.getName());

   
   /**
    * Gets the name of the schema. Since this information is not contained in the object iself but in the
    * Column information (since views could be a combination of more than one schema or catalog), this 
    * method checks that the schema is the same for all columns. If it is different, or if it is not
    * assigned, then null is returned.
    * @return
    */
   public String getSchema() {
      return extractColumnInfo(true); // false means catalog, true means schema
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
      if (this.columnDescriptionList != null && this.columnDescriptionList.size() > 0) {
         for (int i=0; i < this.columnDescriptionList.size(); i++) {
            if (isSchema)
               tmp = ((DbUpdateInfoColDescription)this.columnDescriptionList.get(i)).getSchema();
            else 
               tmp = ((DbUpdateInfoColDescription)this.columnDescriptionList.get(i)).getCatalog();
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
   
   public DbUpdateInfoDescription(I_Info info) {
      this.columnDescriptionList = new ArrayList();
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
      if (this.columnDescriptionList != null)
         this.columnDescriptionList.clear();
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
      DbUpdateInfoRow.storeProp(value, this.attributes, this.attributeKeys);
   }
   
   /**
    * Stores the String as a new value. The passed String is directly transformed into a ClientProperty object. 
    * @param value the value to store as an attribute.
    */
   public void setAttribute(String key, String value) {
      ClientProperty prop = new ClientProperty(key, null, null, value);
      DbUpdateInfoRow.storeProp(prop, this.attributes, this.attributeKeys);
   }
   
   /**
    * It copies (stores) all entries found in the map into the attributes. As values only String and ClientProperty
    * objects are allowed. If another type is found, an IllegalArgumentException is thrown. If null is passed, 
    * nothing is done.
    * 
    * @param map
    */
   public void addAttributes(Map map) {
      DbUpdateInfoRow.addProps(map, this.attributes, this.attributeKeys);
   }
   
   private synchronized final void addPreparedStatements(Connection conn) throws SQLException {
      if (this.hasAddedStatements)
         return;
      String table = getIdentity();
      StringBuffer buf1 = new StringBuffer();
      StringBuffer buf2 = new StringBuffer();
      StringBuffer bufUpd1 = new StringBuffer();
      StringBuffer bufUpd2 = new StringBuffer();

      boolean firstPK = true;
      this.hasPk = false;
      for (int i=0; i < this.columnDescriptionList.size(); i++) {
         DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i); 
         if (col.isPrimaryKey()) {
            this.hasPk = true;
            break;
         }
      }
      
      for (int i=0; i < this.columnDescriptionList.size(); i++) {
         DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i); 
         String colName = col.getColName();

         if (i > 0) {
            buf1.append(" , ");
            buf2.append(" , ");
            bufUpd1.append(" , ");
         }
         if (col.isPrimaryKey() || !this.hasPk) {
            if (!firstPK) {
               bufUpd2.append(" AND ");
            }
            firstPK = false;
         }
         buf1.append(colName);
         buf2.append(" ? ");
         bufUpd1.append(colName).append("= ? ");
         if (col.isPrimaryKey() || !this.hasPk) {
            bufUpd2.append(colName).append(" = ? ");
         }
      }
      
      String wherePart = bufUpd2.toString().trim();
      if (wherePart.length() > 0)
         wherePart = " WHERE " + wherePart;
      
      this.insertStatementTxt = "INSERT INTO " + table + " (" + buf1.toString() + ") VALUES (" + buf2.toString() + ")";
      this.updateStatementTxt = "UPDATE " + table + " SET " + bufUpd1.toString() + wherePart;
      this.deleteStatementTxt = "DELETE FROM " + table + wherePart;
      this.hasAddedStatements = true;
      log.info("statement for insert: \"" + this.insertStatementTxt + "\"");
      log.info("statement for update: \"" + this.updateStatementTxt + "\"");
      log.info("statement for delete: \"" + this.deleteStatementTxt + "\"");
   }
   
   public int insert(Connection conn, DbUpdateInfoRow row) throws Exception {
      addPreparedStatements(conn);
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.insertStatementTxt);
         for (int i=0; i < this.columnDescriptionList.size(); i++) {
            DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i); 
            String colName = col.getColName();
            ClientProperty prop = row.getColumn(colName);
            if (prop == null) 
               throw new Exception(ME + ".insert '" + this.identity + "' column '" + colName + "' not found in xml message:" + row.toXml(""));

            if (isBinaryType(col.getSqlType())) {
               log.info("Handling insert column=" + colName + " as binary (type=" + col.getSqlType() + ", count=" + (i+1) + ")");
               ByteArrayInputStream blob_stream = new ByteArrayInputStream(prop.getBlobValue());
               st.setBinaryStream(i + 1, blob_stream, prop.getBlobValue().length); //(int)sizeInBytes);
            }
            else {
               log.info("Handling insert column=" + colName + " (type=" + col.getSqlType() + ", count=" + (i+1) + ")");
               st.setObject(i + 1, prop.getObjectValue(), col.getSqlType());
            }
         }
         return st.executeUpdate();
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

   public int delete(Connection conn, DbUpdateInfoRow row) throws Exception {
      addPreparedStatements(conn);
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.deleteStatementTxt);
         int count = 1;
         for (int i=0; i < this.columnDescriptionList.size(); i++) {
            DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
            if (col.isPrimaryKey() || !this.hasPk) {
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
   
   
   public int update(Connection conn, DbUpdateInfoRow row) throws Exception {
      addPreparedStatements(conn);
      PreparedStatement st = null;
      try {
         st = conn.prepareStatement(this.updateStatementTxt);
         for (int i=0; i < this.columnDescriptionList.size(); i++) {
            DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i); 
            String colName = col.getColName();
            ClientProperty prop = row.getColumn(colName);
            if (prop == null) 
               throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + row.toXml(""));

            if (isBinaryType(col.getSqlType())) {
               log.info("Handling update column=" + colName + " as binary (type=" + col.getSqlType() + ", count=" + (i+1) + ")");
               ByteArrayInputStream blob_stream = new ByteArrayInputStream(prop.getBlobValue());
               st.setBinaryStream(i + 1, blob_stream, prop.getBlobValue().length); //(int)sizeInBytes);
            }
            else {
               log.info("Handling update column=" + colName + " (type=" + col.getSqlType() + ", count=" + (i+1) + ")");
               st.setObject(i + 1, prop.getObjectValue(), col.getSqlType());
            }
         }
         
         int count = this.columnDescriptionList.size() +1;
         
         if (this.hasPk) {
            for (int i=0; i < this.columnDescriptionList.size(); i++) {
               DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
               if (col.isPrimaryKey()) {
                  String colName = col.getColName();
                  ClientProperty prop = row.getColumn(colName);
                  if (prop == null) 
                     throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + row.toXml(""));

                  if (isBinaryType(col.getSqlType())) {
                     log.info("Handling update PK column=" + colName + " as binary (type=" + col.getSqlType() + ", count=" + count + ")");
                     ByteArrayInputStream blob_stream = new ByteArrayInputStream(prop.getBlobValue());
                     st.setBinaryStream(count++, blob_stream, prop.getBlobValue().length); //(int)sizeInBytes);
                  }
                  else {
                     log.info("Handling update PK column=" + colName + " (type=" + col.getSqlType() + ", count=" + count + ")");
                     st.setObject(count++, prop.getObjectValue(), col.getSqlType());
                  }
               }
            }
         }
         else { // no PK, then we need the old content for almost uniqueness
            ClientProperty oldRowProp = row.getAttribute(ReplicationConstants.OLD_CONTENT_ATTR);
            if (oldRowProp != null) {
               StringBuffer buf = new StringBuffer("<sql><desc></desc><row num='0'>").append(oldRowProp.getStringValue()).append("</row></sql>");
               DbUpdateInfo oldRowDbUpdateInfo = this.parser.parse(buf.toString());
               if (oldRowDbUpdateInfo.getRows().size() < 1)
                  throw new Exception("no old rows retrieved for the entry on update when no PK is defined for the table");
               DbUpdateInfoRow oldRow = (DbUpdateInfoRow)oldRowDbUpdateInfo.getRows().get(0);
               for (int i=0; i < this.columnDescriptionList.size(); i++) {
                  DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
                  String colName = col.getColName();
                  ClientProperty prop = oldRow.getColumn(colName);
                  if (prop == null) 
                     throw new Exception(ME + ".update '" + this.identity + "' column '" + colName + "' not found " + oldRow.toXml(""));

                  if (isBinaryType(col.getSqlType())) {
                     log.info("Handling update PK column=" + colName + " as binary (type=" + col.getSqlType() + ", count=" + count + ")");
                     ByteArrayInputStream blob_stream = new ByteArrayInputStream(prop.getBlobValue());
                     st.setBinaryStream(count++, blob_stream, prop.getBlobValue().length); //(int)sizeInBytes);
                  }
                  else {
                     log.info("Handling update PK column=" + colName + " (type=" + col.getSqlType() + ", count=" + count + ")");
                     st.setObject(count++, prop.getObjectValue(), col.getSqlType());
                  }
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

   public synchronized void addColumnDescription(DbUpdateInfoColDescription colDescription) {
      if (this.hasAddedStatements)
         throw new IllegalStateException("DbUpdateInfoDescription.addColumnDescription: can not add columns now since prepared statements have already been created");
      this.columnDescriptionList.add(colDescription);
   }

   public DbUpdateInfoColDescription[] getUpdateInfoColDescriptions() {
      return (DbUpdateInfoColDescription[])this.columnDescriptionList.toArray(new DbUpdateInfoColDescription[this.columnDescriptionList.size()]);
   }
   
   public DbUpdateInfoColDescription getUpdateInfoColDescription(String colName) {
      for (int i=0; i < this.columnDescriptionList.size(); i++) {
         DbUpdateInfoColDescription tmp = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
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
      return this.columnDescriptionList.size();
   }
   
   /**
    * Gets the column at position given by pos. Note that the first position is 1 (not 0).
    * @param pos
    * @return
    * @throws IllegalArgumentException if the number is less than 1 or bigger than the size of the list or if for some reason the entry has not been found.
    */
   public DbUpdateInfoColDescription getColumnAtPosition(int pos) {
      if (pos < 1 || pos > this.columnDescriptionList.size())
         throw new IllegalArgumentException("getColumnAtPosition has wrong argument '" + pos + "' must be in the range [1.." + this.columnDescriptionList.size() + "] (means inclusive)");
      DbUpdateInfoColDescription col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(pos-1); 
      int p = col.getPos(); // fast find 
      if (p == (pos-1))
         return col;
      StringBuffer buf = new StringBuffer();
      for (int i=0; i < this.columnDescriptionList.size(); i++) {
         col = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
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

      sb.append(offset).append("<").append(DESC_TAG).append(">");
      sb.append(offset + "  ").append("<").append(COMMAND_TAG).append(">").append(this.command).append("</").append(COMMAND_TAG).append(">");
      sb.append(offset + "  ").append("<").append(IDENT_TAG).append(">").append(this.identity).append("</").append(IDENT_TAG).append(">");
      
      for (int i=0; i < this.columnDescriptionList.size(); i++) {
         DbUpdateInfoColDescription desc = (DbUpdateInfoColDescription)this.columnDescriptionList.get(i);
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




