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

import org.xmlBlaster.util.qos.ClientProperty;

/**
 * DbStorage
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
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
   
   public DbStorage(I_DbPool pool, String tableName, String context) throws Exception {
      if (context == null || context.trim().length() < 1)
         this.context = "/";
      else
         this.context = context;
      if (pool == null)
         throw new Exception("DbStorage constructor: The Database pool 'pool' was null. This is not allowed.");
      this.pool = pool;
      this.dbHelper = new DbMetaHelper(this.pool);
      this.tableName = this.dbHelper.getIdentifier(tableName);
      createTableIfNeeded(this.tableName);
   }
   
   private final boolean tableExists(String tableName) throws Exception {
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
   
   private void createTableIfNeeded(String tableName) throws Exception {
      if (!tableExists(tableName)) {
         // TODO: Add schema as Oracle finds the same named table in another schema
         // and make tableName configurable 'xmlBlaster.DBINFO'
         String sql = "CREATE TABLE " + tableName + " (context VARCHAR(255), key VARCHAR(255), value VARCHAR(255), type VARCHAR(16), encoding VARCHAR(16), PRIMARY KEY (context, key))";
         log.info("Going to create the table with the statement '" + sql + "'");
         this.pool.update(sql);
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
         String sql = "INSERT INTO " + this.tableName + " VALUES(?, ?, ?, ?, ?)";
         st = conn.prepareStatement(sql);
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
         String sql = "UPDATE " + this.tableName + " SET value=?, type=?, encoding=? WHERE context=? AND key=?"; 
         st = conn.prepareStatement(sql);
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
         String sql = "SELECT * FROM " + this.tableName + " WHERE context=? AND key=?"; 
         st = conn.prepareStatement(sql);
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
      return this.pool.update("DELETE FROM " + this.tableName + " WHERE context='" + this.context + "' AND key='" + key + "'") != 0;
   }
   
   /**
    * Removes all the properties of this context.  
    * @return the number of entries removed.
    * @throws Exception
    */
   public int clean() throws Exception {
      return this.pool.update("DELETE FROM " + this.tableName + " WHERE context='" + this.context + "'");
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
         String sql = "SELECT key FROM " + this.tableName + " WHERE context=?"; 
         st = conn.prepareStatement(sql);
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