/*------------------------------------------------------------------------------
Name:      SqlInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class SqlInfo implements ReplicationConstants {

   public final static String SQL_TAG = "sql";
   private static Logger log = Logger.getLogger(SqlInfo.class.getName());

   private SqlDescription description;
   
   private List rows;
   
   private I_Info info;

   public SqlInfo(I_Info info) {
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
               this.description = new SqlDescription(this.info);
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
               SqlColumn colDescription = this.description.getColumn(colName);
               if (colDescription == null) {
                  colDescription = new SqlColumn(this.info);
                  this.description.addColumn(colDescription);
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
               // TODO URL ENCODE THIS
               // colDescription.setColDefault(rs.getString(13));
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
         String completeTableName = null;
         String colName = null;
         SqlColumn colDesc = null;
         try {
            st = conn.createStatement();
            completeTableName = table;
            if (schema != null)
               completeTableName = schema + "." + table;
            rs = st.executeQuery("SELECT * from " + completeTableName);
            ResultSetMetaData rsMeta = rs.getMetaData();
            int colCount = rsMeta.getColumnCount();
            if (colCount != this.description.getNumOfColumns()) {
               if (this.description.getNumOfColumns() != 0)
                  throw new Exception("SqlInfo.fillMetaData: wrong number of colums in the SELECT Statement. is '" + colCount + "' but should be '" + this.description.getNumOfColumns() + "'");
               // why does this happen ? Is it on old oracle ?
               for (int i=0; i < colCount; i++) {
                  SqlColumn colDescription = new SqlColumn(this.info);
                  colDescription.setPos(i+1);
                  this.description.addColumn(colDescription);
               }
            }
            for (int i=1; i <= colCount; i++) {
               colName = rsMeta.getColumnName(i);
               colDesc = this.description.getColumnAtPosition(i);
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
               colDesc.setSearchable(rsMeta.isSearchable(i));
               // rsMeta.isWritable(i);
               // rsMeta.isDefinitelyWritable(i);
               colDesc = null; // to get correct info on exceptions
               colName = null;
            }
         }
         catch (SQLException ex) {
            // could still work but UROWID throws an exception here on ora8.1.6
            // "ORA-03115: unsupported network datatype or representation"
            // if (ex.getSQLState() != null && ex.getSQLState().indexOf("ORA-03115") > -1) { // does not work at least on ora8.1.6
            if ( (ex.getMessage() != null && ex.getMessage().indexOf("ORA-03115") > -1) ||
                  (ex.getSQLState() != null && ex.getSQLState().indexOf("ORA-03115") > -1)) {
               if (completeTableName == null)
                  completeTableName = "";
               if (colName == null)
                  colName = "";
               String colDescTxt = "";
               if (colDesc != null)
                  colDescTxt = colDesc.toXml("");
               log.warning("fillMetaData: could not build complete column information for " + completeTableName + "." + colName + ": " + colDescTxt + " " + ex.getMessage());
            }
            else
               throw ex;
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
               colName = rs.getString(4);
               String pkName = rs.getString(6);
               SqlColumn col = this.description.getColumn(colName);
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
               colName = rs.getString(8);
               SqlColumn col = description.getColumn(colName);
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

         // String version = "0.0";
         // replKey, timestamp, action must be filled later (on INSERT if any) (dbId will be empty) catalog is still missing
         
         // these shall be filled from addAttributes ...
         
         // this.description.setAttribute(new ClientProperty(TABLE_NAME_ATTR, null, null, table));
         // this.description.setAttribute(new ClientProperty(GUID_ATTR, "int", null, "0"));
         // this.description.setAttribute(new ClientProperty(SCHEMA_ATTR, null, null, schema));
         // this.description.setAttribute(new ClientProperty(VERSION_ATTR, null, null, version));
         
         if (transformer != null && queryRs != null) {
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
         if (this.description != null)
            this.description.isColumnSearchConfigured(null); // just to force initialization
      }
   }
   
   /**
    * Result set must come from a select spaning over a single table.
    * @param rs
    * @param conn
    * @throws SQLException
    * @deprecated
    */
   public void fillFromTableSelectDELETED(ResultSet rs, boolean fillData, I_AttributeTransformer transformer) throws Exception {
      SqlDescription description = new SqlDescription(this.info);
      setDescription(description);
      
      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      String tableName = null;
      String schema = null;
      String catalog = null;
      for (int i=1; i <= numberOfColumns; i++) {
         SqlColumn col = new SqlColumn(this.info);
         description.addColumn(col);
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
               //String pkName = pkRs.getString(6);
               SqlColumn col = description.getColumn(colName);
               if (col != null)
                  col.setPrimaryKey(true);
            }
            ResultSet fkRs = dbMeta.getImportedKeys(catalog, schema, tableName);
            while (fkRs.next()) {
               String colName = fkRs.getString(8);
               SqlColumn col = description.getColumn(colName);
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
         int count = 0;
         while (rs.next()) {
            SqlRow row = new SqlRow(this.info, count);
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
   public SqlRow fillOneRowWithStringEntriesDEPRECATED(ResultSet rs, I_AttributeTransformer transformer) throws Exception {
      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      int count = getRowCount();
      SqlRow row = new SqlRow(this.info, count);
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
    * Never returns null. If the result is null, the value of the returned client property will be null.
    * @param name
    * @param val
    * @return
    */
   public static ClientProperty buildClientProperty(ResultSetMetaData meta, ResultSet rs, int pos) throws Exception {
      String name = meta.getColumnName(pos);
      Object val = rs.getObject(pos);

      if (val == null)
         return new ClientProperty(name, null, null, (String)null);
      if (val instanceof String) // TODO FIX THIS DIRTY HACK
         return new ClientProperty(name, null, Constants.ENCODING_BASE64, (String)val);
      
      if (val instanceof Boolean)
         return new ClientProperty(name, null, null, "" + ((Boolean)val).booleanValue());

      if (val instanceof Short)
         return new ClientProperty(name, null, null, "" + ((Short)val).shortValue());

      if (val instanceof Integer)
         return new ClientProperty(name, null, null, "" + ((Integer)val).intValue());

      if (val instanceof Long)
         return new ClientProperty(name, null, null, "" + ((Long)val).longValue());

      if (val instanceof Float)
         return new ClientProperty(name, null, null, "" + ((Float)val).floatValue());
         
      if (val instanceof Double)
         return new ClientProperty(name, null, null, "" + ((Double)val).doubleValue());

      if (val instanceof byte[]) {
         ClientProperty prop = new ClientProperty(name, null, null);
         prop.setValue((byte[])val);
      }
      if (val instanceof Clob) { // only for relatively small clobs (< 10MB)
         try {
            Clob clob = (Clob)val;
            InputStream in = clob.getAsciiStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // StringBuffer strBuf = new StringBuffer();
            byte[] buf = new byte[100000];
            int read = 0;
            int count=0;
            while (  (read=in.read(buf)) != -1) {
               baos.write(buf, 0, read);
               count++;
               if (count > 100)
                  throw new IllegalArgumentException("The clob '" + name + "' is too big, already exceeding 10 MB. Will stop processing it");
            }
            in.close();
            ClientProperty prop  = new ClientProperty(name, null, null);
            prop.setValue(new String(baos.toByteArray()));
            return prop;
         }
         catch (Exception ex) {
            throw new Exception("An exception occured when processing '" + name + "'", ex);
         }
      }
      if (val instanceof Blob) { // only for relatively small clobs (< 10MB)
         try {
            Blob blob = (Blob)val;
            InputStream in = blob.getBinaryStream();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // StringBuffer strBuf = new StringBuffer();
            byte[] buf = new byte[100000];
            int read = 0;
            int count=0;
            while (  (read=in.read(buf)) != -1) {
               baos.write(buf, 0, read);
               count++;
               if (count > 100)
                  throw new IllegalArgumentException("The blob '" + name + "' is too big, already exceeding 10 MB. Will stop processing it");
            }
            in.close();
            ClientProperty prop  = new ClientProperty(name, null, null);
            prop.setValue(new String(baos.toByteArray()));
         }
         catch (Exception ex) {
            throw new Exception("An exception occured when processing '" + name + "'", ex);
         }
      }
      if (val instanceof Timestamp) {
         Timestamp ts = (Timestamp)val;
         return new ClientProperty(name, null, null, ts.toString());
      }
      if (val instanceof BigDecimal) {
         BigDecimal dec = (BigDecimal)val;
         try {
            return new ClientProperty(name, null, null, "" + dec.longValue());
         }
         catch (Exception ex) {
            return new ClientProperty(name, null, null, "" + dec.doubleValue());
         }
      }
      if (val instanceof BigInteger) {
         BigInteger dec = (BigInteger)val;
         return new ClientProperty(name, null, null, "" + dec.longValue());
      }
      if (val instanceof Date) {
         // Date date = (Date)val;
         Timestamp ts = rs.getTimestamp(pos);
         // DateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
         // String dateTxt = format.format(date);
         String dateTxt = ts.toString();
         return new ClientProperty(name, null, null, dateTxt);
      }
      if (val instanceof Time) {
         Time time = (Time)val;
         return new ClientProperty(name, null, null, "" + time.getTime());
      }
      else {
         throw new Exception("The object '" + name + "' of type '" + val.getClass().getName() + "' can not be processed since this type is not implemented");
      }
   }
   
   /**
    * Result set must come from a select spaning over a single table.
    * 
    * @param rs
    * @param conn
    * @throws SQLException
    */
   public SqlRow fillOneRowWithObjects(ResultSet rs, I_AttributeTransformer transformer) throws Exception {
      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      int count = getRowCount();
      SqlRow row = new SqlRow(this.info, count);
      getRows().add(row);
      for (int i=1; i<=numberOfColumns; i++) {
         ClientProperty prop = buildClientProperty(meta, rs, i);
         if (prop.getValueRaw() != null)
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
   public SqlRow fillOneRow(ResultSet rs, String rawContent, I_AttributeTransformer transformer) throws Exception {
      int count = getRowCount();
      SqlRow row = new SqlRow(this.info, count);
      getRows().add(row);
      row.setColsRawContent(rawContent);
      if (transformer != null) {
         Map attr = transformer.transform(rs, count);
         if (attr != null)
            row.addAttributes(attr);
      }
      return row;
   }
   
   public SqlDescription getDescription() {
      return this.description;
   }

   public void setDescription(SqlDescription description) {
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
         SqlRow recordRow = (SqlRow)iter.next();
         sb.append(recordRow.toXml(extraOffset + "  "));
      }
      sb.append(offset).append("</").append(SQL_TAG).append(">");
      return sb.toString();
   }
   
   public static SqlInfo getStructure(I_Info info) throws Exception {
      I_DbPool pool = (I_DbPool) info.getObject("db.pool");
      Connection conn = null;
      try {
         SqlInfo sqlInfo = new SqlInfo(info);
         SqlDescription description = new SqlDescription(info);
         conn = pool.reserve();
         conn.setAutoCommit(true);
         ArrayList list = new ArrayList();
         TableToWatchInfo.getSortedTablesToWatch(conn, info, list);
         for (int i=0; i < list.size(); i++) {
            SqlDescription descr = (SqlDescription)list.get(i);
            SqlColumn[] cols = descr.getColumns();
            for (int j=0; j < cols.length; j++) {
               description.addColumn(cols[j]);
            }
         }
         sqlInfo.setDescription(description);
         return sqlInfo;
      }
      finally {
         if (conn != null)
            pool.release(conn);
      }
   }
   
   public static void main(String[] args) {
      I_DbPool pool = null;
      Connection conn = null;
      try {
         // ---- Database settings -----
         if (System.getProperty("jdbc.drivers", null) == null) {
            System.setProperty(
                        "jdbc.drivers",
                        "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         }
         if (System.getProperty("db.url", null) == null) {
            System.setProperty("db.url", "jdbc:postgresql:test//localhost/test");
         }
         if (System.getProperty("db.user", null) == null) {
            System.setProperty("db.user", "postgres");
         }
         if (System.getProperty("db.password", null) == null) {
            System.setProperty("db.password", "");
         }

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
         
         boolean forceCreationAndInit = true;
         ReplicationConverter.getDbSpecific(info, forceCreationAndInit);
         
         SqlInfo sqlInfo = SqlInfo.getStructure(info);
         System.out.println(sqlInfo.toXml(""));
      } 
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
         final boolean ROLLBACK_NO = false;
         conn = SpecificDefault.removeFromPool(conn, ROLLBACK_NO, pool);
      }
      finally {
         if (pool != null) {
            final boolean COMMIT_NO = false;
            conn = SpecificDefault.releaseIntoPool(conn, COMMIT_NO, pool);
         }
      }
   }
   
}
