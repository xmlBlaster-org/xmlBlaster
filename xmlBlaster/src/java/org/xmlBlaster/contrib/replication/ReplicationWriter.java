/*------------------------------------------------------------------------------
Name:      ReplicationWriter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.contrib.dbwriter.I_Parser;
import org.xmlBlaster.contrib.dbwriter.I_Writer;
import org.xmlBlaster.contrib.dbwriter.info.I_PrePostStatement;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.filewriter.FileWriterCallback;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class ReplicationWriter implements I_Writer, ReplicationConstants {

private final static String ME = "ReplicationWriter";
   private static Logger log = Logger.getLogger(ReplicationWriter.class.getName());
   private I_DbPool pool;
   private I_Info info;
   private I_DbSpecific dbSpecific;
   I_Mapper mapper;
   private boolean overwriteTables;
   private boolean recreateTables;
   private String importLocation;
   private I_Update callback;
   private boolean keepDumpFiles;

   private boolean doDrop;
   private boolean doCreate;
   private boolean doAlter;
   private boolean doStatement;
   // private String sqlTopic;
   private String schemaToWipeout;
   private I_PrePostStatement prePostStatement;
   private boolean hasInitialCmd;
   private I_Parser parserForOldInUpdates;
   
   public ReplicationWriter() {
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
      set.add("dbWriter.prePostStatement.class");
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
      boolean forceCreationAndInit = true;
      this.dbSpecific = ReplicationConverter.getDbSpecific(this.info, forceCreationAndInit); 
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
      this.overwriteTables = this.info.getBoolean("replication.overwriteTables", true);
      this.recreateTables =  this.info.getBoolean("replication.recreateTables", false);
      
      this.importLocation = this.info.get("replication.importLocation", "${java.io.tmpdir}");
      boolean overwriteDumpFiles = true;
      String lockExtention =  null;
      this.callback = new FileWriterCallback(this.importLocation, lockExtention, overwriteDumpFiles);
      this.info = info;
      this.pool = (I_DbPool)info.getObject(DbWriter.DB_POOL_KEY);
      if (this.pool == null)
         throw new Exception(ME + ".init: the pool has not been configured, please check your '" + DbWriter.DB_POOL_KEY + "' configuration settings");
      this.keepDumpFiles = info.getBoolean("replication.keepDumpFiles", false);

      this.doDrop = info.getBoolean("replication.drops", true);
      this.doCreate = info.getBoolean("replication.creates", true);
      this.doAlter = info.getBoolean("replication.alters", true);
      this.doStatement = info.getBoolean("replication.statements", true);
      this.schemaToWipeout = info.get("replication.writer.schemaToWipeout", null);
      
      // if (this.doStatement)
      //    this.sqlTopic = this.info.get("replication.sqlTopic", null);
      String prePostStatementClass = this.info.get("dbWriter.prePostStatement.class", "");
      if (prePostStatementClass.length() > 0) {
         ClassLoader cl = ReplicationConverter.class.getClassLoader();
         this.prePostStatement = (I_PrePostStatement)cl.loadClass(prePostStatementClass).newInstance();
         this.prePostStatement.init(info);
         if (log.isLoggable(Level.FINE)) 
            log.fine(prePostStatementClass + " created and initialized");
      }
      String tmp = info.get("replication.initialCmd", null);
      this.hasInitialCmd = (tmp != null);
      // log.info(GlobalInfo.dump(info));
      
      if (tmp == null)
         log.info("The property 'replication.initialCmd' was not found in the configuration");
      else
         log.info("The property 'replication.initialCmd' is '" + tmp + "' and hasInitialCmd is '" + this.hasInitialCmd + "'");
      
      String parserClass = this.info.get("parser.class", "org.xmlBlaster.contrib.dbwriter.SqlInfoParser").trim();
      if (parserClass.length() > 0) {
         ClassLoader cl = this.getClass().getClassLoader();
         this.parserForOldInUpdates = (I_Parser)cl.loadClass(parserClass).newInstance();
         this.parserForOldInUpdates.init(info);
         if (log.isLoggable(Level.FINE)) log.fine(parserClass + " created and initialized");
      }
      else
         log.severe("Couldn't initialize I_Parser, please configure 'parser.class'");
   }

   public void shutdown() throws Exception {
      if (this.prePostStatement != null) {
         this.prePostStatement.shutdown();
         this.prePostStatement = null;
      }
      if (this.dbSpecific != null) {
         this.dbSpecific.shutdown();
         this.dbSpecific = null;
      }
      if (this.pool != null) {
         this.pool.shutdown();
         this.pool = null;
      }
      if (this.mapper != null) {
         this.mapper.shutdown();
         this.mapper = null;
      }
      if (this.parserForOldInUpdates != null) {
         this.parserForOldInUpdates.shutdown();
         this.parserForOldInUpdates = null;
      }
   }

   /**
    * It first searches in the row and if nothing found it searches in the description. Both row and
    * description are allowed to be null.
    * @param key
    * @param row
    * @return
    */
   private String getStringAttribute(String key, SqlRow row, SqlDescription description) {
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
   
   /**
    * Returns the number of columns modified.
    * @param originalCatalog
    * @param originalSchema
    * @param originalTable
    * @param row
    * @return
    * @throws Exception
    */
   private final int modifyColumnsIfNecessary(String originalCatalog, String originalSchema, String originalTable, SqlRow row) throws Exception {
      if (this.mapper == null)
         return 0;
      String[] cols = row.getColumnNames();
      Map colsToChange = new HashMap();
      for (int i=0; i < cols.length; i++) {
         String newCol = this.mapper.getMappedColumn(originalCatalog, originalSchema, originalTable, cols[i]);
         if (newCol == null)
            continue;
         if (cols[i].equalsIgnoreCase(newCol))
            continue;
         colsToChange.put(cols[i], newCol);
         log.info("Renaming '" + cols[i] + "' to '" + newCol + "'");
      }
      if (colsToChange.size() < 1)
         return 0;
      Iterator iter = colsToChange.entrySet().iterator();
      while (iter.hasNext()) {
         Map.Entry entry = (Map.Entry)iter.next();
         row.renameColumn((String)entry.getKey(), (String)entry.getValue());
      }
      return colsToChange.size();
   }

   /**
    * Checks wether an entry has already been processed, in which case it will not be processed anymore
    * @param dbInfo
    * @return
    */
   private boolean checkIfAlreadyProcessed(SqlInfo dbInfo) {
      ClientProperty prop = dbInfo.getDescription().getAttribute(ReplicationConstants.ALREADY_PROCESSED_ATTR);
      if (prop != null)
         return true;
      List rows = dbInfo.getRows();
      for (int i=0; i < rows.size(); i++) {
         SqlRow row = (SqlRow)rows.get(i);
         prop = row.getAttribute(ReplicationConstants.ALREADY_PROCESSED_ATTR);
         if (prop != null)
            return true;
      }
      return false;
   }
   
   
   public void store(SqlInfo dbInfo) throws Exception {
      if (checkIfAlreadyProcessed(dbInfo)) {
         log.info("Entry '" + dbInfo.toXml("") + "' already processed, will ignore it");
         return;
      }
      SqlDescription description = dbInfo.getDescription();
      if (description == null) {
         log.warning("store: The message was a dbInfo but lacked description. " + dbInfo.toXml(""));
         return;
      }
      String command = description.getCommand();

      String action = getStringAttribute(ACTION_ATTR, null, description);
      
      String originalCatalog = getStringAttribute(CATALOG_ATTR, null, description); 
      String originalSchema = getStringAttribute(SCHEMA_ATTR, null, description);
      String originalTable = getStringAttribute(TABLE_NAME_ATTR, null, description);
      // these are still without consideration of the column
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
                  SqlRow row = (SqlRow)rows.get(i);
                  // TODO consistency check
                  action = getStringAttribute(ACTION_ATTR, row, description);

                  originalCatalog = getStringAttribute(CATALOG_ATTR, row, description);
                  originalSchema = getStringAttribute(SCHEMA_ATTR, row, description);
                  originalTable = getStringAttribute(TABLE_NAME_ATTR, row, description);
                  // row specific but still without considering colums
                  catalog = this.mapper.getMappedCatalog(originalCatalog, originalSchema, originalTable, null);
                  schema = this.mapper.getMappedSchema(originalCatalog, originalSchema, originalTable, null);
                  table = this.mapper.getMappedTable(originalCatalog, originalSchema, originalTable, null);

                  if (action == null)
                     throw new Exception(ME + ".store: row with no action invoked '" + row.toXml(""));
                  int count = modifyColumnsIfNecessary(originalCatalog, originalSchema, originalTable, row);
                  if (count != 0)
                     log.fine("modified '" + count  + "' entries");
                  log.fine("store: " + row.toXml(""));
                  SqlDescription desc = getTableDescription(schema, table, conn);
                  boolean process = true;
                  if (this.prePostStatement != null)
                     process = this.prePostStatement.preStatement(action, conn, dbInfo, desc, row);
                  if (process) {
                     if (action.equalsIgnoreCase(INSERT_ACTION)) {
                        desc.insert(conn, row);
                     }
                     else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
                        desc.update(conn, row, this.parserForOldInUpdates);
                     }
                     else if (action.equalsIgnoreCase(DELETE_ACTION)) {
                        desc.delete(conn, row);
                     }
                     if (this.prePostStatement != null)
                        this.prePostStatement.postStatement(action, conn, dbInfo, desc, row);
                  }
               }
            }
            else { // then it is a CREATE / DROP / ALTER or DUMP command (does not have any rows associated)
               if (action.equalsIgnoreCase(CREATE_ACTION)) {
                  if (this.doCreate) {
                     // check if the table already exists ...
                     ResultSet rs = conn.getMetaData().getTables(catalog, schema, table, null);
                     boolean tableExistsAlready = rs.next();
                     rs.close();

                     boolean invokeCreate = true;
                     completeTableName = table;
                     if (schema != null && schema.length() > 1)
                        completeTableName = schema + "." + table;

                     if (tableExistsAlready) {
                        if (!this.overwriteTables) {
                           throw new Exception("ReplicationStorer.store: the table '" + completeTableName + "' exists already and 'replication.overwriteTables' is set to 'false'");
                        }
                        else {
                           if (this.recreateTables) {
                              log.warning("store: the table '" + completeTableName + "' exists already. 'replication.overwriteTables' is set to 'true': will drop the table and recreate it");
                              Statement st = conn.createStatement();
                              st.executeUpdate("DROP TABLE " + completeTableName);
                              st.close();
                           }
                           else {
                              log.warning("store: the table '" + completeTableName + "' exists already. 'replication.overwriteTables' is set to 'true' and 'replication.recreateTables' is set to false. Will only delete contents of table but keep the old structure");
                              invokeCreate = false;
                           }
                        }
                     }
                     String sql = null;
                     if (invokeCreate) {
                        sql = this.dbSpecific.getCreateTableStatement(description, this.mapper);
                        log.info("CREATE STATEMENT: '" + sql + "'");
                     }
                     else {
                        sql = "DELETE FROM " + completeTableName;
                        log.info("CLEANING UP TABLE '" + completeTableName + "'");
                     }
                     Statement st = conn.createStatement();
                     try {
                        st.executeUpdate(sql);
                     }
                     finally {
                        st.close();
                     }
                  }
                  else
                     log.fine("CREATE is disabled for this writer");
               }
               else if (action.equalsIgnoreCase(DROP_ACTION)) {
                  if (this.doDrop) {
                     completeTableName = table;
                     if (schema != null && schema.length() > 1)
                        completeTableName = schema + "." + table;
                     String sql = "DROP TABLE " + completeTableName;
                     Statement st = conn.createStatement();
                     try {
                        st.executeUpdate(sql);
                     }
                     catch (SQLException e) {
                        // this is currently only working on oracle: TODO make it work for other DB too.
                        if (e.getMessage().indexOf("does not exist") > -1)
                           log.warning("table '" + completeTableName + "' was not found and could therefore not be dropped. Continuing anyway");
                        else
                           throw e;
                     }
                     finally {
                        st.close();
                     }
                  }
                  else
                     log.fine("DROP is disabled for this writer");
               }
               else if (action.equalsIgnoreCase(ALTER_ACTION)) {
                  if (this.doAlter) {
                     log.severe("store: operation '" + action + "' invoked but not implemented yet '" + description.toXml(""));
                  }
                  else
                     log.fine("ALTER is disabled for this writer");
               }
               else if (action.equalsIgnoreCase(DUMP_ACTION)) {
                  log.severe("store: operation '" + action + "' invoked but not implemented yet '" + description.toXml(""));
                  // store entry on the file system at a location specified by the 'replication.importLocation' property
                  // the name of the file is stored in DUMP_FILENAME (it will be used to store the file)
               }
               else if (action.equalsIgnoreCase(STATEMENT_ACTION)) {
                  if (this.doStatement) {
                     String sql = getStringAttribute(STATEMENT_ATTR, null, description);
                     String tmp = getStringAttribute(MAX_ENTRIES_ATTR, null, description);
                     
                     long maxResponseEntries = 0L;
                     if (tmp != null)
                        maxResponseEntries = Long.parseLong(tmp.trim());
                     // tmp = getStringAttribute(STATEMENT_PRIO, null, description);
                     final boolean isHighPrio = false; // does not really matter here    
                     final boolean isMaster = false;
                     final String noId = null; // no statement id necessary here
                     final String noTopic = null; // no need for a topic in the slave
                     byte[] response = null;
                     Exception ex = null;
                     try {
                        response = this.dbSpecific.broadcastStatement(sql, maxResponseEntries, isHighPrio, isMaster, noTopic, noId);
                     }
                     catch (Exception e) {
                        ex = e;
                        response = "".getBytes();
                     }

                     if (true) { // TODO add here the possibility to block sending of messages
                        I_ChangePublisher momEngine = (I_ChangePublisher)this.info.getObject("org.xmlBlaster.contrib.dbwriter.mom.MomEventEngine");
                        if (momEngine == null)
                           throw new Exception("ReplicationWriter: the momEngine used can not handle publishes");
                        String statementId = getStringAttribute(STATEMENT_ID_ATTR, null, description);
                        String sqlTopic = getStringAttribute(SQL_TOPIC_ATTR, null, description);
                        HashMap map = new HashMap();
                        map.put(STATEMENT_ID_ATTR, statementId);
                        if (ex != null)
                           map.put(ReplicationConstants.EXCEPTION_ATTR, ex.getMessage());
                        momEngine.publish(sqlTopic, response, map);
                     }
                     else
                        log.info("statement '" + sql + "' resulted in response '" + new String(response));
                     if (ex != null) // now that we notified the server we can throw the exception
                        throw ex;
                  }
                  else
                     log.fine("STATEMENT is disabled for this writer");
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
         catch (Exception ex) {
            log.severe("An exception occured when trying storing the entry." + ex.getMessage());
            ex.printStackTrace();
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

   private synchronized SqlDescription getTableDescription(String schema, String tableName, Connection conn) throws Exception {
      SqlInfo sqlInfo = new SqlInfo(this.info);
      sqlInfo.fillMetadata(conn, null, schema, tableName, null, null);
      return sqlInfo.getDescription();
   }
   
   /**
    * This is invoked for dump files
    */
   public void update(String topic, byte[] content, Map attrMap) throws Exception {
      ClientProperty prop = (ClientProperty)attrMap.get("_filename");
      String filename = null;
      if (prop == null) {
         log.warning("The property '_filename' has not been found. Will choose an own temporary one");
         filename = "tmpFilename.dmp";
      }
      else 
         filename = prop.getStringValue();
      log.info("'" + topic + "' dumping file '" + filename + "' on '" + this.importLocation + "'");
      // will now write to the file system
      this.callback.update(topic, content, attrMap);
      // and now perform an import of the DB
      boolean isEof = true;
      boolean isException = false;
      prop = (ClientProperty)attrMap.get(Constants.CHUNK_SEQ_NUM);
      if (prop != null) {
         prop = (ClientProperty)attrMap.get(Constants.CHUNK_EOF);
         if (prop == null) {
            isEof = false;
         }
         else {
            prop = (ClientProperty)attrMap.get(Constants.CHUNK_EXCEPTION);
            if (prop != null)
               isException = true;
         }
      }
      
      if (isEof && !isException) {
         if (this.hasInitialCmd) {
            String completeFilename = this.importLocation + File.separator + filename;
            
            if (this.schemaToWipeout != null) {
               log.info("Going to clean up the schema '" + this.schemaToWipeout);
               final String catalog = null;
               try {
                  this.dbSpecific.wipeoutSchema(catalog, this.schemaToWipeout, null);
               }
               catch (Exception ex) {
                  log.severe("Could not clean up completely the schema");
                  ex.printStackTrace();
               }
            }
            String version = null;
            this.dbSpecific.initialCommand(null, completeFilename, version);
            if (!this.keepDumpFiles) {
               File fileToDelete = new File(completeFilename);
               boolean del = fileToDelete.delete();
               if (!del)
                  log.warning("could not delete the file '" + completeFilename + "' please delete it manually");
            }
         }
         else
            log.info("since no 'replication.initialCmd' property defined, the initial command will not be executed (not either the wipeout of the schema)");
      }
   }
   
}
