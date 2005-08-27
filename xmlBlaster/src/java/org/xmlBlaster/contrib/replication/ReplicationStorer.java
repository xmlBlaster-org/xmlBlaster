/*------------------------------------------------------------------------------
Name:      ReplicationStorer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.dbwriter.I_Storer;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoRow;
import org.xmlBlaster.util.qos.ClientProperty;

public class ReplicationStorer implements I_Storer, ReplicationConstants {

   private final static String ME = "ReplicationStorer";
   private static Logger log = Logger.getLogger(ReplicationStorer.class.getName());
   private Map tableMap;
   private I_DbPool pool;
   private long oldReplKey = 0;
   
   public ReplicationStorer() {
      this.tableMap = new HashMap();
   }
   
   public void init(I_Info info) throws Exception {
      log.info(ME + ".init invoked");
      this.pool = (I_DbPool)info.getObject(DbWriter.DB_POOL_KEY);
      if (this.pool == null)
         throw new Exception(ME + ".init: the pool has not been configured, please check your '" + DbWriter.DB_POOL_KEY + "' configuration settings");
      
   }

   public void shutdown() throws Exception {
   }

   private String getStringAttribute(String key, DbUpdateInfoRow row) {
      ClientProperty prop = row.getAttribute(key);
      if (prop == null)
         return null;
      return prop.getStringValue();
   }
   
   public void store(DbUpdateInfo info) throws Exception {
      String command = info.getDescription().getCommand();
      String identity = info.getDescription().getIdentity();
      
      log.info(ME + ".store invoked '" + command + "' and '" + identity + "'");
      if (command != null) {
         if (info.getRows().size() < 1) {
            log.warning(ME + ".store invoked with no rows. Not doing anything. " + info.toXml(""));
            return;
         }
         
         if (command.equalsIgnoreCase(REPLICATION_CMD)) {
            Connection conn = this.pool.reserve();
            boolean oldAutoCommitKnown = false;
            boolean oldAutoCommit = false;
            boolean isCommitted = false;
            try {
               List rows = info.getRows();
               oldAutoCommit = conn.getAutoCommit();
               oldAutoCommitKnown = true;
               conn.setAutoCommit(false); // everything will be handled within the same transaction 
               
               for (int i=0; i < rows.size(); i++) {
                  DbUpdateInfoRow row = (DbUpdateInfoRow)rows.get(i);
                  // some minor consistency check
                  ClientProperty prop = row.getAttribute(REPL_KEY_ATTR);
                  if (prop == null) {
                     log.severe(ME + ".store: the replication key is not passed for row:" + row.toXml(""));
                  }
                  else {
                     long newReplKey = prop.getLongValue();
                     if (newReplKey <= this.oldReplKey) {
                        log.severe(ME + ".store: the old key is '" + this.oldReplKey + "' and the new is '" + newReplKey + "' : NEGATIVE SEQUENCE OF REPLICATION KEY !!!!");
                     }
                     this.oldReplKey = newReplKey;
                  }
                  
                  String action = getStringAttribute(ACTION_ATTR, row);
                  
                  String table = getStringAttribute(TABLE_NAME_ATTR, row);
                  // TODO change this once it has been tested ...
                  table += "repl";
                  
                  
                  String schema = getStringAttribute(SCHEMA_ATTR, row);
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
               try {
                  conn.commit();
                  isCommitted = true;
               }
               catch (Throwable ex) {
                  log.severe(ME + ".store: an exception occured when trying to commit: " + ex.getMessage());
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
                     log.severe(ME + ".store: an exception occured when trying to rollback: " + ex.getMessage());
                     ex.printStackTrace();
                  }
                  if (oldAutoCommitKnown) {
                     try {
                        if (oldAutoCommit)
                           conn.setAutoCommit(oldAutoCommit);
                     }
                     catch (Throwable ex) {
                        log.severe(ME + ".store: an exception occured when reverting to original autocommit settings: " + ex.getMessage());
                        ex.printStackTrace();
                     }
                  }
                  this.pool.release(conn);
               }
            }
         }
         else if (command.equalsIgnoreCase(CREATE_ACTION)) { // this does not come via replication, it comes as a msg
            log.severe(ME + ".store '" + command + "' is not implemented. The entry will be ignored");
         }
         else if (command.equalsIgnoreCase(DROP_ACTION)) { // this does not come via replication, it comes as a msg
            log.severe(ME + ".store '" + command + "' is not implemented. The entry will be ignored");
         }
      }
      else {
         log.severe(ME + ".store with not command. The entry will be ignored. " + info.toXml(""));
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
      // TODO to be changed later (upcase / lowcase fix later)
      DbUpdateInfoDescription description = (DbUpdateInfoDescription)this.tableMap.get(tableName.toLowerCase());
      if (description != null)
         return description;
      DatabaseMetaData meta = conn.getMetaData();
      ResultSet rs = null;

      description = new DbUpdateInfoDescription();
      description.setIdentity(tableName);
      try {
         rs = meta.getColumns(null, schema, tableName, null);
         while (rs.next()) {
            String colName = rs.getString(4);
            int type = rs.getInt(5);
            DbUpdateInfoColDescription colDescription = new DbUpdateInfoColDescription();
            colDescription.setSchema(schema);
            colDescription.setTable(tableName);
            colDescription.setSqlType(type);
            colDescription.setColName(colName);
            description.addColumnDescription(colDescription);
         }
      }
      finally {
         if (rs != null)
            rs.close();
      }
      
      try {
         rs = meta.getPrimaryKeys(null, schema, tableName);
         List pk = new ArrayList();
         while (rs.next()) {
            String colName = rs.getString(4);
            DbUpdateInfoColDescription colDescription = description.getUpdateInfoColDescription(colName);
            if (colDescription == null)
               throw new Exception(ME + ".getTableDescription: the column name '" + colName + "' was among the PK but not in the table meta description");
            colDescription.setPrimaryKey(true);
         }
      }
      finally {
         if (rs != null)
            rs.close();
      }
      // TODO change this later, now lowcase
      this.tableMap.put(tableName.toLowerCase(), description);
      return description;
   }

   
}
