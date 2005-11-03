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
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.TreeMap;
import org.xmlBlaster.contrib.I_Info;


/**
 * 
 * TableToWatchInfo is a place holder (as an ejb) for data which is 
 * stored in the ${replPrefix}tables table. 
 * It also offers facility to retrieve the data from info objects.
 * 
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class TableToWatchInfo {

   private String catalog;
   private String schema;
   private String table;
   private boolean replicate = true;
   private String status;
   private long replKey = -1L;
   private String trigger;

   public final static String TABLE_PREFIX = "table";
   public final static String KEY_SEP = ".";
   public final static String VAL_SEP = ",";
   public final static String EMPTY = " ";
   public final static String TABLE_PREFIX_WITH_SEP = TABLE_PREFIX + KEY_SEP;
   
   /**
    * Gets an array containing all the tables to watch found in this configuration 
    * info object.
    * 
    * @param info
    * @return
    * @throws Exception
    */
   public static TableToWatchInfo[] getTablesToWatch(I_Info info) throws Exception {
      synchronized (info) {
         Iterator iter = info.getKeys().iterator();
         TreeMap map = new TreeMap();
         int count = 0;
         while (iter.hasNext()) {
            String key = ((String)iter.next()).trim();
            if (!key.startsWith(TABLE_PREFIX_WITH_SEP))
               continue;
            count++;
            String val = info.get(key, null);
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
   
   
   /**
    * Parses the value and fills the object appropriately. The 
    * syntax to be parsed is of the kind: <br/>
    * [${doReplicate}.][${triggerName}.]${sequenceNr}<br/>
    * Defaults are doReplicate=true, triggerName=null (will be 
    * assigned by the application, sequenceNr=-1, will be assigned
    * by the application.
    * @param val
    * @throws Exception
    */
   private final void parseValue(String val) throws Exception {
      if (val == null || val.trim().length() < 1) {
         this.replicate = true;
         this.trigger = null;
         this.replKey = -1L;
         return;
      }
      
      String tmp1 = null;
      String tmp2 = null;
      String tmp3 = null;
      val = val.trim();
      if (val.startsWith(VAL_SEP))
         val = " " + val;
      String doubleSep = VAL_SEP + VAL_SEP;
      // since it does not recognize ',,' as ', ,'
      int pos = val.indexOf(doubleSep);
      if (pos > -1L) {
         val = val.substring(0, pos) + VAL_SEP + " " + VAL_SEP + val.substring(pos + doubleSep.length());
      }
      
      StringTokenizer tokenizer = new StringTokenizer(val, VAL_SEP);
      if (tokenizer.hasMoreTokens()) {
         tmp1 = tokenizer.nextToken();
         if (tokenizer.hasMoreTokens()) {
            tmp2 = tokenizer.nextToken();
            if (tokenizer.hasMoreTokens())
               tmp3 = tokenizer.nextToken();
         }
      }
      try {
         if (tmp1 != null && tmp1.trim().length() > 0)
            this.replicate = (new Boolean(tmp1.trim())).booleanValue();
      }
      catch (Throwable ex) {
         throw new Exception("Can not parse the value '" + tmp1 + "' as a boolean inside the value '" + val + "' must either be true or false");
      }
      if (tmp2 != null && tmp2.trim().length() > 0)
         this.trigger = tmp2.trim();
      
      try {
         if (tmp3 != null && tmp3.trim().length() > 0)
            this.replKey = Long.parseLong(tmp3.trim());
      }
      catch (Throwable ex) {
         throw new Exception("Can not parse the value '" + tmp3 + "' as a long inside the value '" + val + "' must be a long value");
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
      return replicate;
   }

   /**
    * @param replicate The replicate to set.
    */
   public void setReplicate(boolean replicate) {
      this.replicate = replicate;
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

   
   private static TableToWatchInfo get(ResultSet rs) throws SQLException {
      if (!rs.next())
         return null;
      String catalog = rs.getString(1);
      String schema = rs.getString(2);
      String table = rs.getString(3);
      boolean doReplicate = false;
      String doReplicateTxt = rs.getString(4);
      doReplicate = doReplicateTxt.equalsIgnoreCase("t"); // stands for true
      String status = rs.getString(5);
      long replKey = rs.getLong(6);
      String triggerName  = rs.getString(7);
      TableToWatchInfo tableToWatch = new TableToWatchInfo(catalog, schema, table);
      tableToWatch.setReplicate(doReplicate);
      tableToWatch.setStatus(status);
      tableToWatch.setReplKey(replKey);
      tableToWatch.setTrigger(triggerName);
      return tableToWatch;
   }
   
   public static TableToWatchInfo get(Connection conn, String tableName, String catalog, String schema, String table) throws Exception {
      Statement st = null;
      ResultSet rs = null;
      try {
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + tableName + " WHERE catalogname='" + catalog + "' AND schemaname='" + schema + "' AND tablename='" + table + "'");
         return get(rs);
      }
      finally {
         if (rs != null)
            try { rs.close(); } catch (Exception e) {}
         if (st != null)
            try { st.close(); } catch (Exception e) {}
      }
   }
   
   /**
    * Never throws exception nor returns null.
    * @param conn
    * @param tableName
    * @return
    */
   public static TableToWatchInfo[] getAll(Connection conn, String tableName) {
      if (conn == null || tableName == null || tableName.length() < 1)
         return new TableToWatchInfo[0];
      Statement st = null;
      ResultSet rs = null;
      try {
         ArrayList list = new ArrayList();
         st = conn.createStatement();
         rs = st.executeQuery("SELECT * from " + tableName);
         
         TableToWatchInfo tmp = null;
         while ( (tmp=get(rs)) != null)
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
   
   
}
