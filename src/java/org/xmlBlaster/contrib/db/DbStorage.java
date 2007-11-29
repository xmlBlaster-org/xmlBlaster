/*------------------------------------------------------------------------------
Name:      DbStorage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * DbStorage
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public class DbStorage {

   private static Logger log = Logger.getLogger(DbStorage.class.getName());
   
   private final static int CONTEXT = 1;
   private final static int KEY = 2;
   private final static int VALUE = 3;
   private final static int TYPE = 4;
   private final static int ENCODING = 5;

   private String context;
   private I_DbPool pool;
   private DbMetaHelper dbHelper;
   private String tableName;
   
   private String createSql;
   private String modifySql;
   private String addSql;
   private String getSql;
   private String cleanSql;
   private String deleteSql;
   private String getKeysSql;
   
   private final void prepareSql(I_Info info, String ctx) {
      
      String tmp = info.get("dbs.context", null);
      if (tmp != null && !tmp.equals(ctx))
         log.warning("Property 'dbs.context' already set to '" + tmp + "' will overwrite it to '" + ctx + "'");
      info.put("dbs.context", ctx);
      
      String keyNameDef = "name";
      if (dbHelper.isOracle())
         keyNameDef = "key"; // to be backwards compatible for replication
      String table = info.get("dbs.table", "DBINFO");
      String keyName = info.get("dbs.keyName", keyNameDef);
      String valueName = info.get("dbs.valueName", "value");
      String typeName = info.get("dbs.typeName", "type");
      String encodingName = info.get("dbs.encodingName", "encoding");
      String contextName = info.get("dbs.table", "context");
      
      info.put("dbs.table", table);
      info.put("dbs.keyName", keyName);
      info.put("dbs.valueName", valueName);
      info.put("dbs.typeName", typeName);
      info.put("dbs.encodingName", encodingName);
      info.put("dbs.contextName", contextName);
      
      InfoHelper helper = new InfoHelper(info);
      tmp = "CREATE TABLE ${dbs.table} (${dbs.contextName} VARCHAR(255), ${dbs.keyName} VARCHAR(255), ${dbs.valueName} VARCHAR(255), ${dbs.typeName} VARCHAR(16), {dbs.encodingName} VARCHAR(16), PRIMARY KEY (${dbs.contextName}, ${dbs.keyName}))";
      createSql = helper.replace(info.get("dbs.createSql", tmp));
      log.fine("create statement: '" + createSql + "'");
      tmp = "UPDATE ${dbs.table} SET ${dbs.valueName}=?, ${dbs.typeName}=?, ${dbs.encodingName}=? WHERE ${dbs.contextName}=? AND ${dbs.keyName}=?";
      modifySql = helper.replace(info.get("dbs.mofifySql", tmp));
      log.fine("modify statement: '" + modifySql + "'");
      tmp = "INSERT INTO ${dbs.table} VALUES(?, ?, ?, ?, ?)";
      addSql = helper.replace(info.get("dbs.addSql", tmp));
      log.fine("add statement: '" + addSql + "'");
      tmp = "SELECT * FROM ${dbs.table} WHERE ${dbs.contextName}=? AND ${dbs.keyName}=?";
      getSql = helper.replace(info.get("dbs.getSql", tmp));
      log.fine("get statement: '" + getSql + "'");
      tmp = "DELETE FROM ${dbs.table} WHERE ${dbs.contextName}='${dbs.context}'";
      cleanSql = helper.replace(info.get("dbs.cleanSql", tmp));
      log.fine("clean statement: '" + cleanSql + "'");
      tmp = "SELECT ${dbs.keyName} FROM ${dbs.table} WHERE ${dbs.contextName}=?";
      getKeysSql = helper.replace(info.get("db.getKeysSql", tmp));
      log.fine("getKeys statement: '" + getKeysSql + "'");
      tmp = "DELETE FROM ${dbs.table} WHERE ${dbs.contextName}='${dbs.context}' AND ${dbs.keyName}=?";
      deleteSql = helper.replace(info.get("dbs.deleteSql", tmp));
      log.fine("delete statement: '" + deleteSql + "'");
      /*
      this.createSql = "CREATE TABLE " + table + " (context VARCHAR(255), " + KEY_TXT + " VARCHAR(255), value VARCHAR(255), type VARCHAR(16), encoding VARCHAR(16), PRIMARY KEY (context, " + KEY_TXT + "))";
      this.modifySql = "UPDATE " + table + " SET value=?, type=?, encoding=? WHERE context=? AND " + KEY_TXT + "=?"; 
      this.addSql = "INSERT INTO " + table + " VALUES(?, ?, ?, ?, ?)";
      this.getSql = "SELECT * FROM " + table + " WHERE context=? AND " + KEY_TXT + "=?"; 
      this.cleanSql = "DELETE FROM " + table + " WHERE context='" + ctx + "'";
      this.getKeysSql = "SELECT " + KEY_TXT + " FROM " + table + " WHERE context=?";
      this.deleteSql = "DELETE FROM " + table + " WHERE context='" + ctx + "' AND " + KEY_TXT + "=?";
      */
   }
   
   public DbStorage(I_Info info, I_DbPool pool, String context) throws Exception {
      if (context == null || context.trim().length() < 1)
         this.context = "/";
      else
         this.context = context;
      if (pool == null)
         throw new Exception("DbStorage constructor: The Database pool 'pool' was null. This is not allowed.");
      this.pool = pool;
      this.dbHelper = new DbMetaHelper(this.pool);
      this.tableName = this.dbHelper.getIdentifier(tableName);
      prepareSql(info, this.context);
      createTableIfNeeded();
   }
   
   private final boolean tableExists() throws Exception {
      tableName = this.dbHelper.getIdentifier(tableName);
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null);
         boolean exists = rs.next();
         rs.close();
         return exists;
      }
      finally {
         if (conn != null)
            this.pool.release(conn);
      }
   }
   
   private void createTableIfNeeded() throws Exception {
      if (!tableExists()) {
         // TODO: Add schema as Oracle finds the same named table in another schema
         // and make tableName configurable 'xmlBlaster.DBINFO'
         // TODO !!!!!
         log.info("Going to create the table with the statement '" + createSql + "'");
         this.pool.update(createSql);
      }
   }

   /**
    * Adds a new property to the Table. If the entry already exists, an exception is thrown.
    * @param prop
    * @throws Exception
    */
   public boolean addProperty(ClientProperty prop) throws Exception {
      if (prop == null)
         throw new Exception("The client property was null");
      String key = prop.getName();
      if (key == null)
         throw new Exception("The name of the property '" + prop.toXml() + "' was not defined.");
      PreparedStatement st = null;
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareStatement(addSql);
         st.setString(CONTEXT, this.context);
         st.setString(KEY, key);
         st.setString(VALUE, prop.getValueRaw());
         st.setString(TYPE, prop.getType());
         st.setString(ENCODING, prop.getEncoding());
         int ret = st.executeUpdate();
         return ret != 0;
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Throwable ex) { 
               ex.printStackTrace(); 
            }
         }
         if (conn != null)
            this.pool.release(conn);
      }
   }
   
   
   /**
    * Modifies an existing property. If the property does not exist, an exception is thrown. 
    * 
    * @param prop
    * @throws Exception
    */
   public boolean modifyProperty(ClientProperty prop) throws Exception {
      if (prop == null)
         throw new Exception("The client property was null");
      String key = prop.getName();
      if (key == null)
         throw new Exception("The name of the property '" + prop.toXml() + "' was not defined.");
      PreparedStatement st = null;
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareStatement(modifySql);
         st.setString(1, prop.getValueRaw());
         st.setString(2, prop.getType());
         st.setString(3, prop.getEncoding());
         st.setString(4, this.context);
         st.setString(5, key);
         int ret = st.executeUpdate();
         return ret != 0;
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Throwable ex) { 
               ex.printStackTrace(); 
            }
         }
         if (conn != null)
            this.pool.release(conn);
      }
   }

   
   /**
    * Modifies an existing property. If the property does not exist, an exception is thrown. 
    * 
    * @param prop
    * @throws Exception
    */
   public ClientProperty getProperty(String key) throws Exception {
      if (key == null)
         throw new Exception("The key to search was null");
      PreparedStatement st = null;
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareStatement(getSql);
         st.setString(1, this.context);
         st.setString(2, key);
         ResultSet rs = st.executeQuery();
         if (!rs.next())
            return null; // then no entry found.
         // we don't need context and string.
         String value = rs.getString(3);
         String type = rs.getString(4);
         String encoding = rs.getString(5);
         return new ClientProperty(key, type, encoding, value);
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Throwable ex) { 
               ex.printStackTrace(); 
            }
         }
         if (conn != null)
            this.pool.release(conn);
      }
   }
   
   /**
    * Removes the property with the given key. If none found, nothing happens. 
    * @param key
    * @return true if the entry was removed, false otherwise (i.e. if the entry was not found).
    * @throws Exception
    */
   public boolean remove(String key) throws Exception {
      if (key == null)
         throw new Exception("The key to remove was null");

      PreparedStatement st = null;
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareStatement(deleteSql);
         st.setString(1, key);
         return st.executeUpdate() != 0;
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Throwable ex) { 
               ex.printStackTrace(); 
            }
         }
         if (conn != null)
            this.pool.release(conn);
      }
   }
   
   /**
    * Removes all the properties of this context.  
    * @return the number of entries removed.
    * @throws Exception
    */
   public int clean() throws Exception {
      return this.pool.update(cleanSql);
   }
   
   /**
    * This method tries first to update the entry. If an exception, then presumably the entry did not exist, so an insert is made. If this fails too,
    * then an exception is thrown.
    * 
    * @param prop
    * @throws Exception
    */
   public void put(ClientProperty prop) throws Exception {
      if (prop == null)
         throw new Exception("The property to put into the table was null");
      boolean hasAdded = this.modifyProperty(prop);
      if (!hasAdded)
         this.addProperty(prop);
      
   }

   public Set getKeys() throws Exception {
      PreparedStatement st = null;
      Connection conn = null;
      try {
         conn = this.pool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareStatement(getKeysSql);
         st.setString(1, this.context);

         ResultSet rs = st.executeQuery();
         Set ret = new TreeSet();
         
         while (rs.next()) {
            ret.add(rs.getString(1));
         }
         return ret;
      }
      finally {
         if (st != null) {
            try {
               st.close();
            }
            catch (Throwable ex) { 
               ex.printStackTrace(); 
            }
         }
         if (conn != null)
            this.pool.release(conn);
      }
   }
   
}