/*------------------------------------------------------------------------------
Name:      DbUpdateInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class DbUpdateInfo implements ReplicationConstants {

   public final static String SQL_TAG = "sql";
   private static Logger log = Logger.getLogger(DbUpdateInfo.class.getName());

   private DbUpdateInfoDescription description;
   
   private List rows;
   
   private I_Info info;
   
   public DbUpdateInfo(I_Info info) {
      this.info = info;
      this.rows = new ArrayList();
   }

   /**
    * Fills the object with the metadata. By Oracle it seems that the colName is not returned (returns null). In such a case the method returns false.
    * 
    * @param conn
    * @param catalog
    * @param schema
    * @param table
    * @param queryRs
    * @param transformer
    * @throws Exception
    */
   public void fillMetadata(Connection conn, String catalog, String schema, String table, ResultSet queryRs, I_AttributeTransformer transformer) throws Exception {
      try {
         DatabaseMetaData meta = conn.getMetaData();
         ResultSet rs = null;
         try {
            rs = meta.getColumns(catalog, schema, table, null);
            if (this.description == null)
               this.description = new DbUpdateInfoDescription(this.info);
            this.description.setIdentity(table);
            while (rs.next()) {
               String tmpCat = rs.getString(1);
               if (catalog == null)
                  catalog = tmpCat;
               String tmpSchema = rs.getString(2);
               if (schema == null)
                  schema = tmpSchema;
               String tmpTableName = rs.getString(3);
               String colName = rs.getString(4);
               DbUpdateInfoColDescription colDescription = this.description.getUpdateInfoColDescription(colName);
               if (colDescription == null) {
                  colDescription = new DbUpdateInfoColDescription(this.info);
                  this.description.addColumnDescription(colDescription);
               }
               colDescription.setCatalog(tmpCat);
               colDescription.setSchema(tmpSchema);
               colDescription.setTable(tmpTableName);
               colDescription.setColName(colName);
               colDescription.setSqlType(rs.getInt(5));
               colDescription.setType(rs.getString(6));
               colDescription.setColSize(rs.getInt(7));
               colDescription.setPrecision(rs.getInt(9));
               colDescription.setRadix(rs.getInt(10));
               colDescription.setNullable(rs.getInt(11));
               colDescription.setRemarks(rs.getString(12));
               colDescription.setColDefault(rs.getString(13));
               colDescription.setCharLength(rs.getInt(16));
               colDescription.setPos(rs.getInt(17));
            }
         }
         finally {
            if (rs != null)
               rs.close();
            rs = null;
         }
         // retrieve additional information about columns
         Statement st = null;
         try {
            st = conn.createStatement();
            String completeTableName = table;
            if (schema != null)
               completeTableName = schema + "." + table;
            rs = st.executeQuery("SELECT * from " + completeTableName);
            ResultSetMetaData rsMeta = rs.getMetaData();
            int colCount = rsMeta.getColumnCount();
            if (colCount != this.description.getNumOfColumns())
               throw new Exception("DbUpdateInfo.fillMetaData: wrong number of colums in the SELECT Statement. is '" + colCount + "' but should be '" + this.description.getNumOfColumns() + "'");
            for (int i=1; i <= colCount; i++) {
               String colName = rsMeta.getColumnName(i);
               DbUpdateInfoColDescription colDesc = this.description.getColumnAtPosition(i);
               if (colDesc.getColName() == null)
                  colDesc.setColName(colName);
               colDesc.setLabel(rsMeta.getColumnLabel(i));
               colDesc.setAutoInc(rsMeta.isAutoIncrement(i));
               colDesc.setCaseSens(rsMeta.isCaseSensitive(i));
               colDesc.setTypeName(rsMeta.getColumnTypeName(i));
               try { // there seems to be a bug in the oracle jdbc driver internally using long but giving
                     // back integer
                  colDesc.setPrecision(rsMeta.getPrecision(i));
               }
               catch (NumberFormatException e) {
                  log.warning(e.getMessage());
               }
               colDesc.setScale(rsMeta.getScale(i));
               colDesc.setSigned(rsMeta.isSigned(i));
               colDesc.setReadOnly(rsMeta.isReadOnly(i));
               colDesc.setNullable(rsMeta.isNullable(i));
               // rsMeta.isSearchable(i);
               // rsMeta.isWritable(i);
               // rsMeta.isDefinitelyWritable(i);
            }
         }
         finally {
            try {
               if (st != null)
                  st.close();
               st = null;
            }
            catch (SQLException ex) {
               ex.printStackTrace();
            }
            if (rs != null)
               rs.close();
            rs = null;
         }
         
         // add PK and FK stuff here ...
         rs = meta.getPrimaryKeys(catalog, schema, table);
         try {
            while (rs.next()) {
               String colName = rs.getString(4);
               String pkName = rs.getString(6);
               DbUpdateInfoColDescription col = this.description.getUpdateInfoColDescription(colName);
               if (col != null) {
                  col.setPrimaryKey(true);
                  col.setPkName(pkName);
               }
            }
         }
         finally {
            if (rs != null)
               rs.close();
            rs = null;
         }

         rs = meta.getImportedKeys(catalog, schema, table);
         try {
            while (rs.next()) {
               String colName = rs.getString(8);
               DbUpdateInfoColDescription col = description.getUpdateInfoColDescription(colName);
               if (col != null) {
                  col.setFkCatalog(rs.getString(1));
                  col.setFkSchema(rs.getString(2));
                  col.setFkTable(rs.getString(3));
                  col.setFkCol(rs.getString(4));
                  col.setFkSeq(rs.getString(9));
                  col.setFkUpdRule(rs.getString(10));
                  col.setFkDelRule(rs.getString(11));
                  col.setFkDef(rs.getString(14));
               }
            }
         }
         finally {
            if (rs != null)
               rs.close();
            rs = null;
         }

         String version = "0.0";
         // replKey, timestamp, action must be filled later (on INSERT if any) (dbId will be empty) catalog is still missing
         
         // these shall be filled from addAttributes ...
         
         // this.description.setAttribute(new ClientProperty(TABLE_NAME_ATTR, null, null, table));
         // this.description.setAttribute(new ClientProperty(GUID_ATTR, "int", null, "0"));
         // this.description.setAttribute(new ClientProperty(SCHEMA_ATTR, null, null, schema));
         // this.description.setAttribute(new ClientProperty(VERSION_ATTR, null, null, version));
         
         if (transformer != null) {
            Map attr = transformer.transform(queryRs, -1);
            if (attr != null) {
               Iterator iter = attr.entrySet().iterator();
               while (iter.hasNext()) {
                  Map.Entry entry = (Map.Entry)iter.next();
                  ClientProperty prop = new ClientProperty((String)entry.getKey(), null, null, entry.getValue().toString());
                  description.setAttribute(prop);
               }
            }
         }
      }
      finally {
      }
      
   }
   
   /**
    * Result set must come from a select spaning over a single table.
    * @param rs
    * @param conn
    * @throws SQLException
    * @deprecated
    */
   public void fillFromTableSelect(ResultSet rs, boolean fillData, I_AttributeTransformer transformer) throws Exception {
      DbUpdateInfoDescription description = new DbUpdateInfoDescription(this.info);
      setDescription(description);
      
      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      String tableName = null;
      String schema = null;
      String catalog = null;
      for (int i=1; i <= numberOfColumns; i++) {
         DbUpdateInfoColDescription col = new DbUpdateInfoColDescription(this.info);
         description.addColumnDescription(col);
         tableName = meta.getTableName(i);
         if (tableName != null && tableName.length() > 0)
            col.setTable(meta.getTableName(i));
         schema = meta.getSchemaName(i);
         if (schema != null && schema.length() > 0)
            col.setSchema(schema);
         catalog = meta.getCatalogName(i);
         if (catalog != null && catalog.length() > 0)
            col.setCatalog(catalog);
         col.setType(meta.getColumnTypeName(i));
         if (meta.getPrecision(i) > 0)
            col.setPrecision(meta.getPrecision(i));
         if (meta.getScale(i) > 0)
            col.setScale(meta.getScale(i));
         // always write this since it is not a boolean and it has no default ...
         col.setNullable(meta.isNullable(i));
         if (meta.isSigned(i)==false)
            col.setSigned(meta.isSigned(i));
         if (meta.isReadOnly(i)==true)
            col.setReadOnly(meta.isReadOnly(i));
      }
      // add PK and FK stuff here ...
      
      Statement st = rs.getStatement();
      if (st != null) {
         Connection conn = st.getConnection();
         if (conn != null) {
            DatabaseMetaData dbMeta = conn.getMetaData();
            ResultSet pkRs = dbMeta.getPrimaryKeys(catalog, schema, tableName);
            while (pkRs.next()) {
               String colName = pkRs.getString(4);
               String pkName = pkRs.getString(6);
               DbUpdateInfoColDescription col = description.getUpdateInfoColDescription(colName);
               if (col != null)
                  col.setPrimaryKey(true);
            }
            ResultSet fkRs = dbMeta.getImportedKeys(catalog, schema, tableName);
            while (fkRs.next()) {
               String colName = fkRs.getString(8);
               DbUpdateInfoColDescription col = description.getUpdateInfoColDescription(colName);
               if (col != null) {
                  col.setFkCatalog(fkRs.getString(1));
                  col.setFkSchema(fkRs.getString(2));
                  col.setFkTable(fkRs.getString(3));
                  col.setFkCol(fkRs.getString(4));
                  col.setFkSeq(fkRs.getString(9));
                  col.setFkUpdRule(fkRs.getString(10));
                  col.setFkDelRule(fkRs.getString(11));
                  col.setFkDef(fkRs.getString(14));
               }
            }
         }
      }

      if (transformer != null) {
         Map attr = transformer.transform(rs, -1);
         if (attr != null) {
            Iterator iter = attr.entrySet().iterator();
            while (iter.hasNext()) {
               Map.Entry entry = (Map.Entry)iter.next();
               ClientProperty prop = new ClientProperty((String)entry.getKey(), null, null, entry.getValue().toString());
               description.setAttribute(prop);
            }
         }
      }
      
      if (fillData) {
         List rows = getRows();
         int count = 0;
         while (rs.next()) {
            DbUpdateInfoRow row = new DbUpdateInfoRow(this.info, count);
            count++;
            getRows().add(row);
            for (int i=1; i<=numberOfColumns; i++) {
               String value = rs.getString(i);
               ClientProperty prop = new ClientProperty(meta.getColumnName(i), null, null, value);
               row.setColumn(prop);
            }
            if (transformer != null) {
               Map attr = transformer.transform(rs, count);
               if (attr != null) {
                  Iterator iter = attr.entrySet().iterator();
                  while (iter.hasNext()) {
                     Map.Entry entry = (Map.Entry)iter.next();
                     ClientProperty prop = new ClientProperty((String)entry.getKey(), null, null, entry.getValue().toString());
                     row.setAttribute(prop);
                  }
               }
            }
         }
      }
   }

   public final int getRowCount() {
      return getRows().size();
   }
   
   /**
    * Result set must come from a select spaning over a single table.
    * @param rs
    * @param conn
    * @throws SQLException
    */
   public DbUpdateInfoRow fillOneRow(ResultSet rs, I_AttributeTransformer transformer) throws Exception {
      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      List rows = getRows();
      int count = getRowCount();
      DbUpdateInfoRow row = new DbUpdateInfoRow(this.info, count);
      getRows().add(row);
      for (int i=1; i<=numberOfColumns; i++) {
         String value = rs.getString(i);
         ClientProperty prop = new ClientProperty(meta.getColumnName(i), null, null, value);
         row.setColumn(prop);
      }
      if (transformer != null) {
         Map attr = transformer.transform(rs, count);
         if (attr != null)
            row.addAttributes(attr);
      }
      return row;
   }
   
   /**
    * Result set must come from a select spaning over a single table.
    * @param rawContent the raw content of all the columns belonging to this row.
    * @param conn
    * @throws SQLException
    */
   public DbUpdateInfoRow fillOneRow(ResultSet rs, String rawContent, I_AttributeTransformer transformer) throws Exception {
      List rows = getRows();
      int count = getRowCount();
      DbUpdateInfoRow row = new DbUpdateInfoRow(this.info, count);
      getRows().add(row);
      row.setColsRawContent(rawContent);
      if (transformer != null) {
         Map attr = transformer.transform(rs, count);
         if (attr != null)
            row.addAttributes(attr);
      }
      return row;
   }
   
   public DbUpdateInfoDescription getDescription() {
      return this.description;
   }

   public void setDescription(DbUpdateInfoDescription description) {
      this.description = description;
   }


   public List getRows() {
      return this.rows;
   }
   
   public String toXml(String extraOffset) {
      String charSet = info.get("charSet", "UTF-8");
      StringBuffer sb = new StringBuffer("<?xml version='1.0' encoding='" + charSet + "' ?>");
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;
      sb.append(offset).append("<").append(SQL_TAG).append(">");
      if (this.description != null)
         sb.append(this.description.toXml(extraOffset + "  "));

      Iterator iter = this.rows.iterator();
      while (iter.hasNext()) {
         DbUpdateInfoRow recordRow = (DbUpdateInfoRow)iter.next();
         sb.append(recordRow.toXml(extraOffset + "  "));
      }
      sb.append(offset).append("</").append(SQL_TAG).append(">");
      return sb.toString();
   }
   
}
