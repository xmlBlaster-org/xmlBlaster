package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;

/**
 * @author laghi, ruff
 */
public class SpecificDbReadonly extends SpecificDefault {

   private long replKey;
   private final static Logger log = Logger.getLogger(SpecificDbReadonly.class.getName());
   
   public synchronized void init(I_Info info) throws Exception {
      super.isDbWriteable = false;
      super.init(info);
      this.replKey = System.currentTimeMillis(); // HACK ,, TODO make this persistent
   }
   
   public void addTriggersIfNeeded(boolean force, String[] destinations, boolean forceSend) {
   }

   public final void checkTriggerConsistency(boolean doFix) {
   }
   
   public final void bootstrap(Connection conn, boolean doWarn, boolean force) {
   }

   public final void cleanup(Connection conn, boolean doWarn) {
   }
   
   public final void forceTableChangeCheck() {
   }
   
   public final void initiateUpdate(String topic, String replManagerAddress, String[] slaveNames, String requestedVersion, String initialFilesLocation) throws Exception {
      long minKey = incrementReplKey(null);
      long maxKey = incrementReplKey(null); 
      Connection  conn = this.dbPool.reserve();
      TableToWatchInfo[] tablesToSend = null;
      try {
         tablesToSend = TableToWatchInfo.getTablesToWatch(conn, this.info);   
         this.dbPool.release(conn);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         this.dbPool.erase(conn);
      }
      if (tablesToSend != null) {
         this.initialUpdater.sendInitialDataResponseOnly(slaveNames, replManagerAddress, minKey, maxKey);
         for (int i=0; i < tablesToSend.length; i++) {
            final boolean sendInitialContents = true;
            String catalog = tablesToSend[i].getCatalog();
            String schema = tablesToSend[i].getSchema();
            String table = tablesToSend[i].getTable();
            Map attrs = new HashMap(); // TODO ADD THE CORRECT ATTRIBUTES HERE !!!!!
            try {
               readNewTable(catalog, schema, table, attrs, sendInitialContents);
            }
            catch (Exception ex) {
               // ex.printStackTrace();
               log.severe("Could not send initial data for table '" + table + "'");
            }
         }
         this.initialUpdater.sendEndOfTransitionMessage(slaveNames);
      }
      else
         log.severe("Could not send initial data due to an error when retrieving the lit of tables to replicate");
   }

   protected boolean sequenceExists(Connection conn, String sequenceName)
         throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   protected boolean triggerExists(Connection conn, String triggerName)
         throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   public void addSchemaToWatch(Connection conn, String catalog, String schema)
         throws Exception {
      // TODO Auto-generated method stub

   }

   public String createTableTrigger(SqlDescription infoDescription,
         TableToWatchInfo tableToWatch) {
      // TODO Auto-generated method stub
      return null;
   }

   public StringBuffer getColumnStatement(SqlColumn colInfoDescription) {
      // TODO Auto-generated method stub
      return null;
   }

   public String getContentFromGuid(String guid, String catalog, String schema,
         String table, I_AttributeTransformer transformer) throws Exception {
      // TODO Auto-generated method stub
      return null;
   }

   public boolean removeTrigger(String triggerName, String tableName,
         boolean isSchemaTrigger) {
      // TODO Auto-generated method stub
      return false;
   }

   public boolean triggerExists(Connection conn, TableToWatchInfo tableToWatch)
         throws Exception {
      // TODO Auto-generated method stub
      return false;
   }

   public int wipeoutSchema(String catalog, String schema,
         boolean[] objectsToWipeout) throws Exception {
      // TODO Auto-generated method stub
      return 0;
   }

   public long incrementReplKey(Connection conn) throws Exception {
      this.replKey++;
      return this.replKey;
   }
   
   public boolean isDatasourceReadonly() {
      return true;
   }
   
}
