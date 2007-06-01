package org.xmlBlaster.contrib.replication.impl;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.VersionTransformerCache;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;

/**
 * @author laghi, ruff
 */
public class SpecificDbReadonly extends SpecificDefault {

   public synchronized void init(I_Info info) throws Exception {
      super.isDbWriteable = false;
      super.init(info);
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
      // TODO:
      long minKey = 0; // this.incrementReplKey(conn);
      long maxKey = 1; // this.incrementReplKey(conn); 
      String filename = null;
      this.initialUpdater.sendInitialDataResponse(slaveNames, filename, replManagerAddress, minKey, maxKey, requestedVersion, this.replVersion, initialFilesLocation);
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
         String table) throws Exception {
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
}
