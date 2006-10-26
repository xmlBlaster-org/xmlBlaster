/*------------------------------------------------------------------------------
Name:      TableToWatchInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.util.StringPairTokenizer;


/**
 * 
 * TableToWatchInfo is a place holder (as an ejb) for data which is 
 * stored in the ${replPrefix}tables table. 
 * It also offers facility to retrieve the data from info objects.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TableToWatchInfo {

   private static Logger log = Logger.getLogger(TableToWatchInfo.class.getName());

   public final static String ACTION_KEY = "actions";
   public final static String TRIGGER_KEY = "trigger";
   public final static String SEQUENCE_KEY = "sequence";

   public final static String STATUS_CREATING = "CREATING";
   public final static String STATUS_OK = "OK";
   public final static String STATUS_REMOVE = "REMOVE";

   private String catalog;
   private String schema;
   private String table;
   private String status;
   private long replKey = -1L;
   private String trigger;
   private long debug;
   
   /**
    * flags which are set mean the replication does happen for these flags.
    * For example 'IDU' means everything will be replicated: (I)nserts, (D)eletes, and (U)pdates.
    */
   private String actions = "";
   
   public final static String TABLE_PREFIX = "table";
   public final static String SCHEMA_PREFIX = "schema";
   public final static String KEY_SEP = ".";
   public final static String VAL_SEP = ",";
   public final static String EMPTY = " ";
   public final static String ALL_TOKEN = "*";
   
   /** this is used as the prefix for all tables to replicate */
   public final static String TABLE_PREFIX_WITH_SEP = TABLE_PREFIX + KEY_SEP;

   /**
    * Checks if there are foreign keys which are not resolved yet
    * @return true if all foreign keys are resolved or if there was no foreign key, false otherwise.
    */
   private static boolean checkIfForeignKeysAreResolved(SqlDescription desc, Set setOfProcessedTables, Map allTables) throws Exception {
      SqlColumn[] cols = desc.getColumns();
      for (int i=0; i < cols.length; i++) {
         SqlColumn col = cols[i];
         if (col.isFk()) {
            String fkTable = col.getFkTable();
            if (fkTable == null)
               throw new Exception("The column '" + cols[i].getTable() + "' has a column '" + cols[i].getColName() + "' which is a foreign key but no associated table name was found");
            if (!setOfProcessedTables.contains(fkTable)) {
               if (!allTables.containsKey(fkTable)) 
                  log.warning("The column '" + cols[i].getTable() + "' has a column '" + cols[i].getColName() + "' which is a foreign key. It is associated with a table which is not replicated (remember to make sure that this table is available on the destination also.");
               else
                  return false;
            }
         }
      }
      return true;
   }

   /**
    * Returns all table names for the given catalog and schema. It only returns tables (not views),
    * and it uses the MetaData of the connection.
    * 
    * @param prefixToAdd the prefix to be added to the table names, if null nothing is added.
    * @param conn the connection to be used. 
    * @param tableToWatch The tableToWatch object containing the name of the catalog and schema
    * @return a String[] containing the names of the tables. The name of the tables is the absolute name.
    * @throws Exception if an exception on the backend occurs.
    */
   private final static String[] getTablesForSchema(String prefixToAdd, Connection conn, TableToWatchInfo tableToWatch) throws SQLException {
      if (tableToWatch == null)
         throw new SQLException("TableToWatchInfo.getTablesForSchema: table to watch is null");
      String table = tableToWatch.getTable(); 
      if (table != null) {
         table = table.trim();
         if (table.length() > 0 && !table.equals(ALL_TOKEN))
            log.warning("The table '" + table + "' should either be empty or '" + ALL_TOKEN + "' (we ignore it here but may be mis-configuration)");
      }
      
      String catalog = tableToWatch.getCatalog(); 
      if (catalog != null && catalog.trim().length() < 1)
         catalog = null;
      String schema = tableToWatch.getSchema(); 
      if (schema != null && schema.trim().length() < 1)
         schema = null;
      
      ResultSet rs = null;
      ArrayList list = new ArrayList();
      try {
         rs = conn.getMetaData().getTables(catalog, schema, null, new String[] {"TABLE"});
         while (rs.next()) {
            String tableName = rs.getString("TABLE_NAME");
            String completeTableName = "";
            if (prefixToAdd != null)
               completeTableName += prefixToAdd;
            if (catalog != null)
               completeTableName += catalog + KEY_SEP;
            if (schema != null)
               completeTableName += schema + KEY_SEP;
            completeTableName += tableName;
            list.add(completeTableName);
         }
      }
      finally {
         if (rs != null)
            rs.close();
      }
      return (String[])list.toArray(new String[list.size()]);
   }
   
   public static String getSortedTablesToWatch(Connection conn, I_Info info, List outputSequence) throws Exception {

      TableToWatchInfo[] tables = getTablesToWatch(conn, info);
      List nonExisting = new ArrayList();
      List toProcess = new ArrayList();
      Map tableMap = new HashMap();
       for (int i=0; i < tables.length; i++) {
          try {
             SqlInfo sqlInfo = new SqlInfo(info);
             String catalog = tables[i].getCatalog();
             String schema =  tables[i].getSchema();
             String tableName = tables[i].getTable();
             tableMap.put(tableName, tables[i]);
             sqlInfo.fillMetadata(conn, catalog, schema, tableName, null, null);
             toProcess.add(sqlInfo.getDescription());
          }
          catch (Throwable ex) {
             ex.printStackTrace();
             nonExisting.add(tables[i]);
          }
       }

       Set set = new HashSet(); // better performance
       StringBuffer buf = new StringBuffer();
       int sweepCount = 0;
       int maxSweepCount = toProcess.size();
       if (maxSweepCount < 2)
          maxSweepCount = 2;
       
       while (toProcess.size() > 0) {
          int count = 0;
          SqlDescription[] sqlDesc = (SqlDescription[])toProcess.toArray(new SqlDescription[toProcess.size()]);
          for (int i=0; i < sqlDesc.length; i++) {
             if (checkIfForeignKeysAreResolved(sqlDesc[i], set, tableMap)) {
                String tableName = sqlDesc[i].getIdentity();
                set.add(tableName);
                outputSequence.add(sqlDesc[i]);
                SqlDescription removed = (SqlDescription)toProcess.remove(i-count);
                count++;
                TableToWatchInfo tableToWatch = (TableToWatchInfo)tableMap.get(tableName);
                if (tableToWatch == null)
                   throw new Exception("Table '" + tableToWatch + "' was not found in the list of tables to be processed");
                tableToWatch.setReplKey((long)i);
                buf.append("    <attribute id='").append(tableToWatch.getConfigKey()).append("'>").append(tableToWatch.getConfigValue()).append("</attribute>\n");
                
                if (!removed.getIdentity().equalsIgnoreCase(tableName))
                   throw new Exception("An inconsistency aroze when trying to determine the correct loading sequence for tables. Failed for *" + sqlDesc[i-count].toXml("") + "' but has removed '" + removed.toXml(""));
             }
          }
          sweepCount++;
          if (sweepCount >= maxSweepCount) {
             StringBuffer exBuf = new StringBuffer();
             for (int i=0; i < toProcess.size(); i++) {
                SqlDescription desc = (SqlDescription)toProcess.get(i);
                exBuf.append(desc.getIdentity()).append(" ");
             }
             throw new Exception("Still entries to be processed after '" + sweepCount + "' sweeps. Still to be processed: '" + exBuf.toString() + "'");
          }
       }
       
       if (nonExisting.size() > 0) {
          buf.append("    <!-- THE FOLLOWING ENTRIES WHERE NOT FOUND ON THE DATABASE. THEIR SEQUENCE CAN THEREFORE NOT BE GUARANTEED -->\n");
          for (int i=0; i < nonExisting.size(); i++) {
             TableToWatchInfo tableToWatch = (TableToWatchInfo)nonExisting.get(i);
             if (tableToWatch == null)
                throw new Exception("Table '" + tableToWatch + "' was not found in the list of tables to be processed");
             tableToWatch.setReplKey(-1L);
             buf.append("    <attribute id='").append(tableToWatch.getConfigKey()).append("'>").append(tableToWatch.getConfigValue()).append("</attribute>\n");
          }
       }
       return buf.toString();
   }
   
   /**
    * Gets an array containing all the tables to watch found in this configuration 
    * info object.
    * 
    * @param info
    * @return
    * @throws Exception
    */
   public static TableToWatchInfo[] getTablesToWatch(Connection conn, I_Info originalInfo) throws Exception {
      synchronized (originalInfo) {
         Iterator iter = originalInfo.getKeys().iterator();

         // prepare defaults defined with a '*' token
         I_Info ownInfo = new PropertiesInfo(new Properties());
         while (iter.hasNext()) {
            String key = ((String)iter.next()).trim();
            if (!key.startsWith(TABLE_PREFIX_WITH_SEP))
               continue;
            String val = originalInfo.get(key, null);
            if (key.indexOf(ALL_TOKEN) < 0L) {
               ownInfo.put(key, val);
               continue;
            }
            TableToWatchInfo tableToWatch = new TableToWatchInfo();
            tableToWatch.assignFromInfoPair(key, val);
            String[] tableNames = getTablesForSchema(TABLE_PREFIX_WITH_SEP, conn, tableToWatch);
            for (int i=0; i < tableNames.length; i++)
               ownInfo.put(tableNames[i], "");
         }

         TreeMap map = new TreeMap();
         int count = 0;
         iter = ownInfo.getKeys().iterator();
         while (iter.hasNext()) {
            String key = ((String)iter.next()).trim();
            if (!key.startsWith(TABLE_PREFIX_WITH_SEP))
               continue;
            count++;
            String val = ownInfo.get(key, null);
            TableToWatchInfo tableToWatch = new TableToWatchInfo();
            tableToWatch.assignFromInfoPair(key, val);
            Long mapKey = new Long(tableToWatch.getReplKey());
            ArrayList list = (ArrayList)map.get(mapKey);
            if (list == null) {
               list = new ArrayList();
               map.put(mapKey, list);
            }
            list.add(tableToWatch);
         }

         // handle here allt tables which have been assigned as default: for example
         // <attribute id='table.XMLBLASTER.*'></attribute>
         
         TableToWatchInfo[] tables = new TableToWatchInfo[count];
         count = 0;
         iter = map.keySet().iterator();
         while (iter.hasNext()) {
            Object mapKey = iter.next();
            ArrayList list = (ArrayList)map.get(mapKey);
            for (int i=0; i < list.size(); i++) {
               TableToWatchInfo obj = (TableToWatchInfo)list.get(i);
               tables[count] = obj;
               count++;
            }
         }
         return tables;
      }
   }
   
   public TableToWatchInfo() {
   }

   public TableToWatchInfo(String catalog, String schema, String table) {
      this.catalog = catalog;
      this.schema = schema;
      this.table = table;
      if (this.catalog == null || this.catalog.trim().length() < 1)
         this.catalog = EMPTY;
   }

   
   /**
    * Parses the key and fills this object appropriately. The 
    * syntax to be parsed is of the kind: <br/>
    * table.[${catalog}.][${schema}.]${table}<br/>
    * examples:
    * <ul>
    *   <li>table.table1</li>
    *   <li>table.schema1.table1</li>
    *   <li>table.catalog1.schema1.table1</li>
    * </ul>
    * @param key
    * @throws Exception
    */
   private final void parseKey(String key) throws Exception {
      if (key == null)
         throw new Exception("TableToWatchInfo.assignFromInfoPair: The key is empty, you must assign one");
      key = key.trim();
      if (!key.startsWith(TABLE_PREFIX_WITH_SEP))
         throw new Exception("TableToWatchInfo.assignFromInfoPair: The key '" + key + "' does not start with '" + TABLE_PREFIX + "'");
      key = key.substring(TABLE_PREFIX_WITH_SEP.length());
      
      String tmp1 = null;
      String tmp2 = null;
      String tmp3 = null;
      StringTokenizer tokenizer = new StringTokenizer(key, KEY_SEP);
      if (tokenizer.hasMoreTokens()) {
         tmp1 = tokenizer.nextToken();
         if (tokenizer.hasMoreTokens()) {
            tmp2 = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens())
               tmp3 = tokenizer.nextToken();
         }
      }
      if (tmp3 == null) {
         this.catalog = null;
         if (tmp2 == null) {
            this.schema = null;
            this.table = tmp1.trim();
         }
         else {
            this.schema = tmp1.trim();
            this.table = tmp2.trim();
         }
      }
      else {
         this.catalog = tmp1.trim();
         this.schema = tmp2.trim();
         this.table = tmp3.trim();
      }
   }
   
   public String getConfigKey() {
      StringBuffer buf = new StringBuffer();
      buf.append("table").append(KEY_SEP);
      if (this.catalog != null && this.catalog.length() > 0)
         buf.append(this.catalog).append(KEY_SEP);
      if (this.schema != null && this.schema.length() > 0)
         buf.append(this.schema).append(KEY_SEP);
      else
         if (this.catalog != null && this.catalog.length() > 0)
            buf.append(KEY_SEP);

      buf.append(this.table);
      return buf.toString();
   }

   public String getConfigValue() {
      StringBuffer buf = new StringBuffer();
      boolean isFirst = true;
      if (this.actions != null && this.actions.length() > 0) {
         buf.append(ACTION_KEY).append("=").append(this.actions);
         isFirst = false;
      }
      if (this.trigger != null && this.trigger.length() > 0) {
         if (!isFirst)
            buf.append(VAL_SEP);
         buf.append(TRIGGER_KEY).append("=").append(this.trigger);
         isFirst = false;
      }
      if (this.replKey > -1L) {
         if (!isFirst)
            buf.append(VAL_SEP);
         buf.append(SEQUENCE_KEY).append("=").append(this.replKey);
      }
      return buf.toString();
   }
   
   /**
    * Parses the value and fills the object appropriately. The 
    * syntax to be parsed is of the kind: <br/>
    * action=IDU,trigger=TRIGGER_NAME,sequence=100
    * Defaults are action=IDU, triggerName=null (will be 
    * assigned by the application, sequenceNr=-1, will be assigned
    * by the application.
    * @param val
    * @throws Exception
    */
   private final void parseValue(String val) throws Exception {
      if (val == null || val.trim().length() < 1) {
         this.actions = "IDU";
         this.trigger = null;
         this.replKey = -1L;
         return;
      }
      
      Map map = StringPairTokenizer.parseToStringStringPairs(val.trim(), ",", "=");
      this.actions = (String)map.get(ACTION_KEY);
      if (this.actions == null) {
         if (map.containsKey(ACTION_KEY))
            this.actions = "";
         else
            this.actions = "IDU";
      }
      this.trigger = (String)map.get(TRIGGER_KEY);
      
      String tmp = (String)map.get(SEQUENCE_KEY);
      if (tmp == null || tmp.trim().length() < 1)
         this.replKey = -1;
      else {
         try {
            this.replKey = Long.parseLong(tmp.trim());
         }
         catch (Throwable ex) {
            String txt = "The string '" +tmp + "' could not be parsed as a long for 'sequence'. The complete value was '" + val;
            throw new Exception(txt);
         }
      }
   }
   
   /**
    * Parses the data which is passed as a key/value pair.
    * @param key
    * @param value
    * @throws Exception
    */
   public void assignFromInfoPair(String key, String value) throws Exception {
      parseKey(key);
      parseValue(value);
   }
   
   
   /**
    * @return Returns the catalog.
    */
   public String getCatalog() {
      if (this.catalog == null || this.catalog.trim().length() < 1)
         return EMPTY;
      return catalog;
   }

   /**
    * @param catalog The catalog to set.
    */
   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   /**
    * @return Returns the replicate.
    */
   public boolean isReplicate() {
      if (this.actions == null)
         return false;
      return this.actions.length() > 0;
   }

   /**
    * @param replicate The replicate to set.
    */
   public void setActions(String actions) {
      if (actions != null) {
         this.actions = actions.trim().toUpperCase();
      }
      else
         this.actions = "";
   }

   /**
    * @return Returns the replKey.
    */
   public long getReplKey() {
      return replKey;
   }

   /**
    * @param replKey The replKey to set.
    */
   public void setReplKey(long replKey) {
      this.replKey = replKey;
   }

   /**
    * @return Returns the schema.
    */
   public String getSchema() {
      if (this.schema == null || this.schema.trim().length() < 1)
         return EMPTY;
      return schema;
   }

   /**
    * @param schema The schema to set.
    */
   public void setSchema(String schema) {
      this.schema = schema;
   }

   /**
    * @return Returns the status.
    */
   public String getStatus() {
      return status;
   }

   /**
    * @param status The status to set.
    */
   public void setStatus(String status) {
      this.status = status;
   }

   /**
    * @return Returns the table.
    */
   public String getTable() {
      return table;
   }

   /**
    * @param table The table to set.
    */
   public void setTable(String table) {
      this.table = table;
   }

   /**
    * @return Returns the trigger.
    */
   public String getTrigger() {
      return trigger;
   }

   /**
    * @param trigger The trigger to set.
    */
   public void setTrigger(String trigger) {
      this.trigger = trigger;
   }

   /**
    * First checks if the entry exists already. If it exists, it is first removed, otherwise it is
    * just added.
    * 
    * @param replPrefix
    * @param dbPool
    * @param conn
    * @throws Exception
    */
   public void store(String replPrefix, I_DbPool dbPool, Connection conn) throws Exception {
      String selectSql = "SELECT * FROM " + replPrefix + "tables WHERE CATALOGNAME='" + getCatalog() + "' AND SCHEMANAME='" + getSchema() + "' AND TABLENAME='" + getTable() + "'";
      log.info("executing '" + selectSql + "'");
      Statement st = null;
      try {
         st = conn.createStatement();
         ResultSet rs = st.executeQuery(selectSql);
         if (rs.next()) {
            String delSql = "DELETE FROM " + replPrefix + "tables WHERE CATALOGNAME='" + getCatalog() + "' AND SCHEMANAME='" + getSchema() + "' AND TABLENAME='" + getTable() + "'";
            log.info("executing '" + delSql + "'");
            dbPool.update(conn, delSql);
         }
      }
      finally {
         if (st != null) {
            st.close();
         }
      }
      String sql = "INSERT INTO " + replPrefix + "tables VALUES ('" + getCatalog() + "','"
            + getSchema() + "','" + getTable() + "','" + getActions()
            + "', 'CREATING'," + getReplKey() + ",'" + getTrigger() + "'," + getDebug() + ")";
      log.info("Inserting the statement '" + sql + "' for '" + this + "'");
      dbPool.update(conn, sql);
   }

   /**
    * 
    * @param rs
    * @param tableToWatch can be null. If not it will be the instance returned (after having filled it of course).
    * @return
    * @throws SQLException
    */
   private static TableToWatchInfo get(ResultSet rs, TableToWatchInfo tableToWatch) throws SQLException {
      if (!rs.next())
         return null;
      String catalog = rs.getString(1);
      String schema = rs.getString(2);
      String table = rs.getString(3);
      String actions = rs.getString(4);
      String status = rs.getString(5);
      long replKey = rs.getLong(6);
      String triggerName = rs.getString(7);
      long debug = rs.getInt(8);

      if (tableToWatch == null)
         tableToWatch = new TableToWatchInfo(catalog, schema, table);
      else {
         tableToWatch.setCatalog(catalog);
         tableToWatch.setSchema(schema);
         tableToWatch.setTable(table);
      }
      tableToWatch.setActions(actions);
      tableToWatch.setStatus(status);
      tableToWatch.setReplKey(replKey);
      tableToWatch.setDebug((int)debug);
      tableToWatch.setTrigger(triggerName);
      return tableToWatch;
   }
   
   /**
    * 
    * @param conn
    * @param tableName The name of the table from which to retrieve the information
    * @param catalog
    * @param schema
    * @param table
    * @param tableToWatch can be null. If it is not null, it will be changed appropriately and returned.
    * @return
    * @throws Exception
    */
   public static TableToWatchInfo get(Connection conn, String tableName, String catalog, String schema, String table , TableToWatchInfo tableToWatch) throws Exception {
      Statement st = null;
      ResultSet rs = null;
      if (catalog == null)
         catalog = EMPTY;
      if (schema == null)
         schema = EMPTY;
      try {
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + tableName + " WHERE catalogname='" + catalog + "' AND schemaname='" + schema + "' AND tablename='" + table + "'");
         return get(rs, tableToWatch);
      }
      finally {
         if (rs != null)
            try { rs.close(); } catch (Exception e) {}
         if (st != null)
            try { st.close(); } catch (Exception e) {}
      }
   }
   
   /**
    * Gets the entire configuration information of the configuration table specified in the
    * argument list.
    * Never throws exception nor returns null.
    * @param conn
    * @param tableName
    * @return
    */
   public static TableToWatchInfo[] getAll(Connection conn, String confTableName) {
      if (conn == null || confTableName == null || confTableName.length() < 1)
         return new TableToWatchInfo[0];
      Statement st = null;
      ResultSet rs = null;
      try {
         ArrayList list = new ArrayList();
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + confTableName);
         
         TableToWatchInfo tmp = null;
         while ( (tmp=get(rs, null)) != null)
            list.add(tmp);
         return (TableToWatchInfo[])list.toArray(new TableToWatchInfo[list.size()]);
      }
      catch (Exception ex) {
         ex.printStackTrace();
         return new TableToWatchInfo[0];
      }
      finally {
         if (rs != null)
            try { rs.close(); } catch (Exception e) {}
         if (st != null)
            try { st.close(); } catch (Exception e) {}
      }
   }
   
   public String getActions() {
      if (this.actions == null)
         return "IDU";
      return this.actions;
   }

   public String toXml() {
      StringBuffer buf = new StringBuffer();
      buf.append("<tableToWatch ");
      if (this.catalog != null)
         buf.append(" catalog='" + this.catalog + "'");
      if (this.schema != null)
         buf.append(" schema='" + this.schema + "'");
      if (this.table != null)
         buf.append(" table='" + this.table + "'");
      if (this.status != null)
         buf.append(" status='" + this.status + "'");
      buf.append(" replKey='" + this.replKey + "'");
      if (this.trigger  != null)
         buf.append(" trigger='" + this.trigger + "'");
      if (this.actions  != null)
         buf.append(" actions='" + this.actions + "'");
      if (this.debug  >  0)
         buf.append(" debug='" + this.debug + "'");
      buf.append(" doReplicate='" + isReplicate() + "' />");
      return buf.toString();
   }

   public int getDebug() {
      return (int)this.debug;
   }

   public void setDebug(int debug) {
      this.debug = debug;
   }
   
   public void storeStatus(String replicationPrefix, I_DbPool dbPool) throws Exception {
      String sql = "UPDATE " + replicationPrefix + "tables SET status='" + this.status + "' WHERE catalogname='" + this.catalog + "' AND schemaname='" + this.schema + "' AND tablename='" + this.table + "'";
      dbPool.update(sql);
   }

   public void removeFromDb(String replicationPrefix, I_DbPool dbPool) throws Exception {
      String sql = "DELETE FROM " + replicationPrefix + "tables WHERE catalogname='" + this.catalog + "' AND schemaname='" + this.schema + "' AND tablename='" + this.table + "'";
      dbPool.update(sql);
   }

   /**
    * Checks if the status is OK. To return true the status flag in the tables table must
    * be OK, the trigger must exist and no exception shall be thrown by requesting the
    * existence of the trigger.
    * 
    * @param dbSpecific
    * @param conn
    * @return
    */
   public boolean isStatusOk(I_DbSpecific dbSpecific, Connection conn) {
      if (this.status == null) {
         log.warning("The status flag for trigger '" + this.trigger + "' on table '" + this.table + "' is null");
         return false;
      }
      try {
         boolean ret = dbSpecific.triggerExists(conn, this);
         if (!ret) {
            log.warning("The trigger '" + this.trigger + "' for table '" + this.table + "' does not exist and status is '" + getStatus() + "'");
            return ret;
         }
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         return false;
      }
      boolean ret = getStatus().equals(TableToWatchInfo.STATUS_OK);
      if (!ret)
         log.warning("The status flag for trigger '" + this.trigger + "' on table '" + this.table + "' is not OK, it is '" + getStatus() + "'");
      return ret;
   }
   
}
