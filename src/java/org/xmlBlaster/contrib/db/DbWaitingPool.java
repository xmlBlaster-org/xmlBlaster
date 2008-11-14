/*------------------------------------------------------------------------------
Name:      DbPool.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.io.BufferedReader;
import java.io.FileReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.InfoHelper;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.ThreadLister;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.pool.I_PoolManager;
import org.xmlBlaster.util.pool.PoolManager;
import org.xmlBlaster.util.pool.ResourceWrapper;
import org.xmlBlaster.util.queue.jdbc.JdbcConnectionPool;

/**
 * Simple implementation of the database JDBC connection pool. 
 * <p>
 * Following configuration paramaters are available:
 * <pre>
 * db.url=jdbc:oracle:thin:@localhost:1521:orcl
 * db.user=system
 * db.password=manager
 * db.maxInstances=10
 * db.busyToIdleTimeout=0             // How long may a query last [millis]
 * db.idleToEraseTimeout=10*60*1000L  // How long does an unused connection survive (10 min)
 * db.maxResourceExhaustRetries=5
 * db.resourceExhaustSleepGap=1000    // [millis]
 * </pre>
 * @author Marcel Ruff
 */
public class DbWaitingPool implements I_DbPool {
   private static Logger log = Logger.getLogger(DbWaitingPool.class.getName());
   private I_Info info;
   private JdbcConnectionPool connectionPool;
   private String dbUrl;
   private String dbUser;
   private String dbPasswd;
   /** If the pool is exhausted, we poll the given times */
   private int maxResourceExhaustRetries;
   /** If the pool is exhausted, we poll every given millis<br />
       Please note that the current request thread will block for maxResourceExhaustRetries*resourceExhaustSleepGap millis. */
   private long resourceExhaustSleepGap;
   private final Object meetingPoint = new Object();
   private int initCount = 0;
   private I_DbCreateInterceptor createInterceptor;
   
   /**
    * Default constructor, you need to call <tt>init(info)</tt> thereafter. 
    */
   public DbWaitingPool() {
      // void
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("db.url");
      set.add("db.user");
      set.add("db.password");
      set.add("db.maxInstances");
      set.add("db.busyToIdleTimeout");
      set.add("db.idleToEraseTimeout");
      set.add("db.maxResourceExhaustRetries");
      set.add("db.resourceExhaustSleepGap");
      return set;
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#init(I_Info)
    */
   public synchronized void init(I_Info info)  {
      if (this.initCount > 0) {
         this.initCount++;
         return;
      }
      try {
         this.info = info;
         Global glob = GlobalInfo.getOriginalGlobal(info);
         if (glob != null) {
            String dbInstanceName = glob.getStrippedId();
            dbUrl = ReplaceVariable.replaceFirst(dbUrl, "$_{xmlBlaster_uniqueId}", dbInstanceName);
            
         }
         Properties props = new Properties();
         PropertiesInfo tmpInfo = new PropertiesInfo(props);
         InfoHelper.fillInfoWithEntriesFromInfo(tmpInfo, info);
         this.connectionPool = new JdbcConnectionPool();
         this.connectionPool.initialize(glob, props);
         this.initCount++;
         String createInterceptorClass = info.get("db.createInterceptor.class", null);
         if (createInterceptorClass != null) {
            ClassLoader cl = this.getClass().getClassLoader();
            try {
               createInterceptor = (I_DbCreateInterceptor)cl.loadClass(createInterceptorClass).newInstance();
               createInterceptor.init(info);
               if (log.isLoggable(Level.FINE)) 
                  log.fine(createInterceptorClass + " created and initialized");
            }
            catch (Exception ex) {
               ex.printStackTrace();
               throw new IllegalArgumentException(ex.toString());
            }
         }
         
      }
      catch (Exception ex) {
         ex.printStackTrace();
         log.severe("En exception occured when initializing the pool: " + ex.getMessage());
      }
   }
   
   public String getUser() {
      return this.dbUser;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#reserve()
    * @throws Exception of type XmlBlasterException or IllegalArgumentException
    */
   public Connection reserve() throws Exception {
      if (connectionPool == null) 
         throw new IllegalArgumentException("Pool is not initialized");
      return connectionPool.getConnection();
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#release(java.sql.Connection)
    * @throws Exception of type XmlBlasterException or IllegalArgumentException
    */
   public void release(Connection con) throws Exception {
      if (connectionPool == null) 
         throw new IllegalArgumentException("Pool is not initialized");
      // con.setAutoCommit(true);
      connectionPool.releaseConnection(con, true);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#erase(java.sql.Connection)
    * @throws IllegalArgumentException
    */
   public void erase(Connection con) throws IllegalArgumentException {
      if (connectionPool == null) 
         throw new IllegalArgumentException("Pool is not initialized");
      try {
         connectionPool.discardConnection(con);
      }
      catch (XmlBlasterException ex) {
         throw new IllegalArgumentException("Could not discard connection");
      }
   }

   /**
    * Cleanup and destroy everything. 
    */
   private void destroy() {
      synchronized(meetingPoint) {
         if (connectionPool != null) {
            if (log.isLoggable(Level.FINE)) 
               log.fine("Destroying pool: " + connectionPool.toString());
            connectionPool.shutdown();
            connectionPool = null;
         }
         if (createInterceptor != null) {
            createInterceptor.shutdown();
            createInterceptor = null;
         }
      }
   }
        
   /**
   * This callback does nothing (enforced by interface I_PoolManager)
   * @param resource The Connection object
   * @see org.xmlBlaster.util.pool.I_PoolManager#idleToBusy(Object)
   */
   public void idleToBusy(Object resource) {
      // Connection con = (Connection)resource;
   }

   /** 
   * This callback does nothing (enforced by interface I_PoolManager
   * @param resource The Connection object
   * @see org.xmlBlaster.util.pool.I_PoolManager#busyToIdle(Object)
   */
   public void busyToIdle(Object resource) {
      // Connection con = (Connection)resource;
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#update(String)
    */
   public int update(String command) throws Exception {
      Connection  conn = null;
      Statement   stmt = null;
      try {
         conn =  reserve();
         conn.setAutoCommit(true);
         stmt = conn.createStatement();
         if (log.isLoggable(Level.FINE)) log.fine("Running update command '" + command + "'");
         int rowsAffected = stmt.executeUpdate(command);
         return rowsAffected;
      }
      catch (SQLException e) {
         String str = "SQLException in query '" + command + "' : " + e;
         log.warning(str + ": sqlSTATE=" + e.getSQLState() + " we destroy the connection in case it's stale");
         //String sqlState = e.getSQLState(); // DatabaseMetaData method getSQLStateType can be used to discover whether the driver returns the XOPEN type or the SQL 99 type
         // To be on the save side we always destroy the connection:
         erase(conn);
         conn = null;
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         String str = "Unexpected exception in query '" + command + "' : " + e;
         log.severe(str + ": We destroy the connection in case it's stale");
         erase(conn);
         conn = null;
         throw new Exception(e);
      }
      finally {
         try {
            if (stmt!=null) stmt.close();
         }
         catch (SQLException e) {
            log.warning("Closing of stmt failed: " + e.toString());
         }
         if (conn!=null) release(conn);
      }
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#update(String)
    */
   public int update(Connection conn, String command) throws Exception {
      if (conn == null)
         return update(command);
      
      Statement   stmt = null;
      try {
         stmt = conn.createStatement();
         if (log.isLoggable(Level.FINE)) log.fine("Running update command '" + command + "'");
         int rowsAffected = stmt.executeUpdate(command);
         return rowsAffected;
      }
      catch (SQLException e) {
         String str = "SQLException in query '" + command + "' : " + e;
         log.warning(str + ": sqlSTATE=" + e.getSQLState());
         throw e;
      }
      catch (Throwable e) {
         e.printStackTrace();
         String str = "Unexpected exception in query '" + command + "' : " + e;
         log.severe(str);
         throw new Exception(e);
      }
      finally {
         try {
            if (stmt!=null) stmt.close();
         }
         catch (SQLException e) {
            log.warning("Closing of stmt failed: " + e.toString());
         }
      }
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#select(String, I_ResultCb)
    */
   public void select(String command, I_ResultCb cb) throws Exception {
      select(null, command, true, cb);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#select(java.sql.Connection, String, I_ResultCb)
    */
   public Connection select(Connection connection, String command, I_ResultCb cb) throws Exception {
      return select(connection, command, false, cb);
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#select(java.sql.Connection, String, I_ResultCb, boolean)
    */
   public Connection select(Connection connection, String command, boolean autoCommit, I_ResultCb cb) throws Exception {
      Statement stmt = null;
      ResultSet rs = null;
      Connection conn = null;
      try {
         conn = (connection == null) ? reserve() : connection;
         conn.setAutoCommit(autoCommit);
         stmt = conn.createStatement();
         if (log.isLoggable(Level.FINE)) log.fine("Running " + (autoCommit?"autoCommit":"in "+((connection==null)?"new ":"")+"open transaction") + " command '" + command + "'");
         rs = stmt.executeQuery(command);
         cb.result(conn, rs);
      }
      catch (SQLException e) {
         if (e.getSQLState() != null && e.getSQLState().indexOf("42000") != -1
             && e.toString().indexOf("ORA-00942") != -1) { // ORA-00942: table or view does not exist TODO: How to make this portable???
            // sqlStateXOpen=1, sqlStateSQL99=2 (Oracle 10g returns 0)
            //log.fine("SQLStateType=" + conn.getMetaData().getSQLStateType());
            log.fine("No db change detected, the table does not exist: " + e.toString());
            cb.result(conn, null);
            return conn;
         }
         String str = "SQLException in query '" + command + "' : " + e;
         log.warning(str + ": sqlSTATE=" + e.getSQLState() + " we destroy the connection in case it's stale");
         // To be on the save side we always destroy the connection:
         if (connection == null && conn != null && !conn.getAutoCommit()) conn.rollback();
         erase(conn);
         conn = null;
         throw e;
      }
      catch (Throwable e) {
         if (e instanceof NullPointerException) {
            e.printStackTrace();
         }
         String str = "Unexpected exception in query '" + command + "' : " + e;
         log.severe(str + ": We destroy the connection in case it's stale");
         if (connection == null && conn != null && !conn.getAutoCommit()) conn.rollback();
         erase(conn);
         conn = null;
         throw new Exception(e);
      }
      finally {
         try {
            if (stmt!=null) stmt.close();
         }
         catch (SQLException e) {
            log.warning("Closing of stmt failed: " + e.toString());
         }
      }
      
      if (autoCommit) {
         if (conn!=null) release(conn);
         conn = null;
      }
            
      return conn;
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#shutdown
    */
   public synchronized void shutdown() {
      this.initCount--;
      if (this.initCount > 0)
         return;
      destroy();
   }

   /**
    * may be empty and just contains db.* properties
    * @return
    */
   public I_Info getInfo() {
      return this.info;
   }
   
   public static void main(String[] args) {
      try {
         I_DbPool pool = new DbPool();
         I_Info info = new PropertiesInfo(System.getProperties());
         String filename = info.get("file", null);
         if (filename == null) {
            System.out.println("usage: java -Dfile=someFile org.xmlBlaster.contrib.db.DbWaitingPool");
            System.exit(-1);
         }
            
         pool.init(info);
         
         BufferedReader br = new BufferedReader(new FileReader(filename));
         String line = null;
         StringBuffer buf = new StringBuffer();
         while (  (line = br.readLine()) != null)
            buf.append(line).append("\n");
         br.close();
         String cmd = buf.toString();
         System.out.println(cmd);
         pool.update(buf.toString());
         pool.shutdown();
      }
      catch (Exception ex) {
         
      }
   }
   
}
