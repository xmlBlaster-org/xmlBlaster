/*------------------------------------------------------------------------------
Name:      ReplicationWriter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.dbwriter.I_Writer;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoRow;
import org.xmlBlaster.contrib.filewriter.FileWriterCallback;
import org.xmlBlaster.util.qos.ClientProperty;

public class ReplicationWriter implements I_Writer, ReplicationConstants {

private final static String ME = "ReplicationWriter";
   private static Logger log = Logger.getLogger(ReplicationWriter.class.getName());
   private Map tableMap;
   private I_DbPool pool;
   private I_Info info;
   private I_DbSpecific dbSpecific;
   I_Mapper mapper;
   private boolean overwriteTables;
   private DbMetaHelper dbMetaHelper;
   private String importLocation;
   private I_Update callback;
   
   public ReplicationWriter() {
      this.tableMap = new HashMap();
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add(I_DbSpecific.NEEDS_PUBLISHER_KEY);
      set.add("replication.mapper.class");
      set.add("replication.overwriteTables");
      set.add("replication.importLocation");
      PropertiesInfo.addSet(set, this.mapper.getUsedPropertyKeys());
      PropertiesInfo.addSet(set, this.pool.getUsedPropertyKeys());
      PropertiesInfo.addSet(set, this.dbSpecific.getUsedPropertyKeys());
      return set;
   }


   public void init(I_Info info) throws Exception {
      log.info("init invoked");
      this.info = info;
      this.pool = (I_DbPool)info.getObject(DbWriter.DB_POOL_KEY);
      if (this.pool == null)
         throw new Exception(ME + ".init: the pool has not been configured, please check your '" + DbWriter.DB_POOL_KEY + "' configuration settings");

      // this avoids the publisher to be instantiated (since we are on the slave side)
      this.info.put(I_DbSpecific.NEEDS_PUBLISHER_KEY, "false");
      this.dbSpecific = ReplicationConverter.getDbSpecific(info); 
      this.dbMetaHelper = new DbMetaHelper(this.pool);
      String mapperClass = info.get("replication.mapper.class", "org.xmlBlaster.contrib.replication.impl.DefaultMapper");
      if (mapperClass.length() > 0) {
         ClassLoader cl = ReplicationConverter.class.getClassLoader();
         this.mapper = (I_Mapper)cl.loadClass(mapperClass).newInstance();
         this.mapper.init(info);
         if (log.isLoggable(Level.FINE)) 
            log.fine(mapperClass + " created and initialized");
      }
      else
         log.info("Couldn't initialize I_DataConverter, please configure 'converter.class' if you need a conversion.");
      this.overwriteTables = this.info.getBoolean("replication.overwriteTables", false);
      this.importLocation = this.info.get("replication.importLocation", "${java.io.tmpdir}");
      boolean overwriteDumpFiles = true;
      String lockExtention =  null;
      this.callback = new FileWriterCallback(this.importLocation, lockExtention, overwriteDumpFiles);
      this.info = info;
      this.pool = (I_DbPool)info.getObject(DbWriter.DB_POOL_KEY);
      if (this.pool == null)
         throw new Exception(ME + ".init: the pool has not been configured, please check your '" + DbWriter.DB_POOL_KEY + "' configuration settings");
   }

   public void shutdown() throws Exception {
   }

   /**
    * It first searches in the row and if nothing found it searches in the description. Both row and
    * description are allowed to be null.
    * @param key
    * @param row
    * @return
    */
   private String getStringAttribute(String key, DbUpdateInfoRow row, DbUpdateInfoDescription description) {
      ClientProperty prop = null;
      if (row != null)
         prop = row.getAttribute(key);
      if (prop == null && description != null) {
         prop = description.getAttribute(key);
      }
      if (prop == null)
         return null;
      return prop.getStringValue();
   }
   
   private boolean isAllowedCommand(String command) {
      if (command == null)
         return false;
      command = command.trim();
      if (REPLICATION_CMD.equalsIgnoreCase(command) || 
          ALTER_ACTION.equalsIgnoreCase(command) ||
          CREATE_ACTION.equalsIgnoreCase(command) ||
          DROP_ACTION.equalsIgnoreCase(command) ||
          STATEMENT_ACTION.equalsIgnoreCase(command) ||
          DUMP_ACTION.equalsIgnoreCase(command))
         return true;
      return false;
   }
   
   public void store(DbUpdateInfo dbInfo) throws Exception {
      
      DbUpdateInfoDescription description = dbInfo.getDescription();
      if (description == null) {
         throw new Exception("store: The message was a dbInfo but lacked description. " + dbInfo.toXml(""));
      }
      String command = description.getCommand();

      String action = getStringAttribute(ACTION_ATTR, null, description);
      
      String originalCatalog = getStringAttribute(CATALOG_ATTR, null, description); 
      String originalSchema = getStringAttribute(SCHEMA_ATTR, null, description);
      String originalTable = getStringAttribute(TABLE_NAME_ATTR, null, description);
      
      String catalog = this.mapper.getMappedCatalog(originalCatalog, originalSchema, originalTable, null);
      String schema = this.mapper.getMappedSchema(originalCatalog, originalSchema, originalTable, null);
      String table = this.mapper.getMappedTable(originalCatalog, originalSchema, originalTable, null);

      String completeTableName = table;
      if (schema != null && schema.length() > 1)
         completeTableName = schema + "." + table;
      
      // log.info(ME + ".store invoked for cmd='" + command + "' and identity ='" + identity + "'");
      log.info("store invoked for \n" + dbInfo.toXml(""));
      if (isAllowedCommand(command)) {
         Connection conn = this.pool.reserve();
         boolean oldAutoCommitKnown = false;
         boolean oldAutoCommit = false;
         boolean isCommitted = false;
         try {
            List rows = dbInfo.getRows();
            oldAutoCommit = conn.getAutoCommit();
            oldAutoCommitKnown = true;
            conn.setAutoCommit(false); // everything will be handled within the same transaction 

            if (command.equalsIgnoreCase(REPLICATION_CMD)) {
               for (int i=0; i < rows.size(); i++) {
                  DbUpdateInfoRow row = (DbUpdateInfoRow)rows.get(i);
                  // TODO consistency check
                  action = getStringAttribute(ACTION_ATTR, row, description);

                  originalCatalog = getStringAttribute(CATALOG_ATTR, row, description);
                  originalSchema = getStringAttribute(SCHEMA_ATTR, row, description);
                  originalTable = getStringAttribute(TABLE_NAME_ATTR, row, description);

                  catalog = this.mapper.getMappedCatalog(originalCatalog, originalSchema, originalTable, null);
                  schema = this.mapper.getMappedSchema(originalCatalog, originalSchema, originalTable, null);
                  table = this.mapper.getMappedTable(originalCatalog, originalSchema, originalTable, null);

                  if (action == null)
                     throw new Exception(ME + ".store: row with no action invoked '" + row.toXml(""));
                  log.fine("store: " + row.toXml(""));
                  DbUpdateInfoDescription desc = getTableDescription(schema, table, conn);
                  if (action.equalsIgnoreCase(INSERT_ACTION)) {
                     desc.insert(conn, row);
                  }
                  else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
                     desc.update(conn, row);
                  }
                  else if (action.equalsIgnoreCase(DELETE_ACTION)) {
                     desc.delete(conn, row);
                  }
                  else {
                     throw new Exception(ME + ".store: row with unknown action '" + action + "' invoked '" + row.toXml(""));
                  }
               }
            }
            else { // then it is a CREATE / DROP / ALTER or DUMP command (does not have any rows associated)
               if (action.equalsIgnoreCase(CREATE_ACTION)) {
                  
                  // check if the table already exists ...
                  ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, null);
                  boolean tableExistsAlready = rs.next();
                  rs.close();

                  if (tableExistsAlready) {
                     if (!this.overwriteTables) {
                        throw new Exception("ReplicationStorer.store: the table '" + completeTableName + "' exists already and 'replication.overwriteTables' is set to 'false'");
                     }
                     else {
                        log.warning("store: the table '" + completeTableName + "' exists already. 'replication.overwriteTables' is set to 'true': will drop the table and recreate it");
                        Statement st = conn.createStatement();
                        st.executeUpdate("DROP TABLE " + completeTableName);
                        st.close();
                     }
                  }
                  
                  String sql = this.dbSpecific.getCreateTableStatement(description, this.mapper);
                  Statement st = conn.createStatement();
                  try {
                     st.executeUpdate(sql);
                  }
                  finally {
                     st.close();
                  }
               }
               else if (action.equalsIgnoreCase(DROP_ACTION)) {
                  String sql = "DROP TABLE " + completeTableName;
                  Statement st = conn.createStatement();
                  try {
                     st.executeUpdate(sql);
                  }
                  finally {
                     st.close();
                  }
               }
               else if (action.equalsIgnoreCase(ALTER_ACTION)) {
                  log.severe("store: operation '" + action + "' invoked but not implemented yet '" + description.toXml(""));
               }
               else if (action.equalsIgnoreCase(DUMP_ACTION)) {
                  log.severe("store: operation '" + action + "' invoked but not implemented yet '" + description.toXml(""));
                  // store entry on the file system at a location specified by the 'replication.importLocation' property
                  // the name of the file is stored in DUMP_FILENAME (it will be used to store the file)
               }
               else if (action.equalsIgnoreCase(STATEMENT_ACTION)) {
                  String sql = getStringAttribute(STATEMENT_ATTR, null, description);
                  if (sql != null && sql.length() > 1) {
                     Statement st = conn.createStatement();
                     try {
                        boolean ret = st.execute(sql);
                        if (ret)
                           log.severe("store: operation '" + action + "' invoked but not implemented yet '" + description.toXml("") + "' was a query. This is not implemented yet");
                     }
                     finally {
                        st.close();
                     }
                  }
               }
               else {
                  log.severe("store: description with unknown action '" + action + "' invoked '" + description.toXml(""));
               }
            }
            try {
               conn.commit();
               isCommitted = true;
            }
            catch (Throwable ex) {
               log.severe("store: an exception occured when trying to commit: " + ex.getMessage());
               ex.printStackTrace();
            }
         }
         finally {
            if (conn != null) {
               try {
                  if (!isCommitted)
                     conn.rollback();
               }
               catch (Throwable ex) {
                  log.severe("store: an exception occured when trying to rollback: " + ex.getMessage());
                  ex.printStackTrace();
               }
               if (oldAutoCommitKnown) {
                  try {
                     if (oldAutoCommit)
                        conn.setAutoCommit(oldAutoCommit);
                  }
                  catch (Throwable ex) {
                     log.severe("store: an exception occured when reverting to original autocommit settings: " + ex.getMessage());
                     ex.printStackTrace();
                  }
               }
               this.pool.release(conn);
            }
         }
      }
      else {
         log.severe("store with not command. The entry will be ignored. " + dbInfo.toXml(""));
      }
   }

   
   /**
    * This retrieves the complete Information about a table description.
    * @param tableName
    * @param conn
    */
   private synchronized DbUpdateInfoDescription getTableDescription(String schema, String tableName, Connection conn) throws Exception {
      if (tableName == null)
         throw new Exception(ME + ".getTableDescription: the table name is null");
      log.fine("Table Meta info initialization lookup: schema='" + schema + "' tableName='" + tableName + "'");
      tableName = this.dbMetaHelper.getIdentifier(tableName);
      schema = this.dbMetaHelper.getIdentifier(schema);
      
      DbUpdateInfoDescription description = (DbUpdateInfoDescription)this.tableMap.get(tableName);
      if (description != null)
         return description;
      DatabaseMetaData meta = conn.getMetaData();
      ResultSet rs = null;
      log.info("Retrieving table meta information from database: schema='" + schema + "' tableName='" + tableName + "'");

      description = new DbUpdateInfoDescription(this.info);
      description.setIdentity(tableName);
      try {
         rs = meta.getColumns(null, schema, tableName, null);
         while (rs.next()) {
            String colName = rs.getString(4);
            int type = rs.getInt(5);
            DbUpdateInfoColDescription colDescription = new DbUpdateInfoColDescription(this.info);
            colDescription.setSchema(schema);
            colDescription.setTable(tableName);
            colDescription.setSqlType(type);
            colDescription.setColName(colName);
            description.addColumnDescription(colDescription);
            log.info("Table Meta info: name='" + tableName + "' colName='" + colName + "' type='" + type + "'");
         }
         if (description.getUpdateInfoColDescriptions().length == 0) {
            log.severe("No table meta information for database schema='" + schema + "' tableName='" + tableName + "' found");
         }
      }
      finally {
         if (rs != null)
            rs.close();
      }
      
      try {
         rs = meta.getPrimaryKeys(null, schema, tableName);
         //List pk = new ArrayList();
         int count = 0;
         while (rs.next()) {
            count++;
            String colName = rs.getString(4);
            DbUpdateInfoColDescription colDescription = description.getUpdateInfoColDescription(colName);
            if (colDescription == null)
               throw new Exception(ME + ".getTableDescription: the column name '" + colName + "' was among the PK but not in the table meta description");
            colDescription.setPrimaryKey(true);
            log.info("Primary key found colName=" + colName);
         }
         if (count == 0) {
            log.severe("No primary key found for table '" + tableName + "'");
         }
      }
      finally {
         if (rs != null)
            rs.close();
      }
      tableName = this.dbMetaHelper.getIdentifier(tableName);
      this.tableMap.put(tableName, description);
      return description;
   }

   /**
    * This is invoked for dump files
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      String filename = (String)attrMap.get("_filename");
      if (filename == null)
         filename = "(null)";
      log.info("'" + topic + "' dumping file '" + filename + "' on '" + this.importLocation + "'");  
      this.callback.update(topic, content, attrMap);
   }

   
}
