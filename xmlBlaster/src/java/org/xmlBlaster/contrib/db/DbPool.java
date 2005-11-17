/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.db;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.DriverManager;

import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;


import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.pool.PoolManager;
import org.xmlBlaster.util.pool.I_PoolManager;
import org.xmlBlaster.util.pool.ResourceWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

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
public class DbPool implements I_DbPool, I_PoolManager {
   private static Logger log = Logger.getLogger(DbPool.class.getName());
   private I_Info info;
   private PoolManager poolManager;
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
   
   /**
    * Default constructor, you need to call <tt>init(info)</tt> thereafter. 
    */
   public DbPool() {
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
   public synchronized void init(I_Info info) {
      if (this.initCount > 0) {
         this.initCount++;
         return;
      }

      this.info = info;
      this.dbUrl = this.info.get("db.url", "");
      this.dbUser = this.info.get("db.user", "");
      this.dbPasswd = this.info.get("db.password", "");
      int maxInstances = this.info.getInt("db.maxInstances", 10);
      // How long may a query last
      long busyToIdle = this.info.getLong("db.busyToIdleTimeout", 0);
      // How long does an unused connection survive (10 min)
      long idleToErase = this.info.getLong("db.idleToEraseTimeout", 120*60*1000L);
      this.maxResourceExhaustRetries = this.info.getInt("db.maxResourceExhaustRetries", 5);
      // millis
      this.resourceExhaustSleepGap = this.info.getLong("db.resourceExhaustSleepGap", 1000);
      initDrivers();
      this.poolManager = new PoolManager("DbPool", this, maxInstances, busyToIdle, idleToErase);
      this.initCount++;
   }
   
   /**
    * Load the JDBC drivers given in environment. 
    * <p />
    * Default is JdbcDriver.drivers=sun.jdbc.odbc.JdbcOdbcDriver
    */
   private void initDrivers() {
      String drivers = this.info.get("jdbc.drivers", "oracle.jdbc.driver.OracleDriver");
      StringTokenizer st = new StringTokenizer(drivers, ":");
      int numDrivers = st.countTokens();
      String driver = "";

      for (int i = 0; i < numDrivers; i++) {
         try {
            driver = st.nextToken().trim();
            //log.info("Trying JDBC driver Class.forName('" + driver + "') ...");
            Class cl = Class.forName(driver);
            java.sql.Driver dr = (java.sql.Driver)cl.newInstance();
            java.sql.DriverManager.registerDriver(dr);
            log.info("Jdbc driver '" + driver + "' loaded.");
         }
         catch (Throwable e) {
            log.warning("Couldn't initialize driver <" + driver + ">, please check your CLASSPATH");
         }
      }
      if (numDrivers == 0) {
         log.warning("No JDBC drivers given, set 'jdbc.drivers' to point to your DB drivers if wanted, e.g. jdbc.drivers=oracle.jdbc.driver.OracleDriver:org.gjt.mm.mysql.Driver:postgresql.Driver");
      }
   }


   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#reserve()
    * @throws Exception of type XmlBlasterException or IllegalArgumentException
    */
   public Connection reserve() throws Exception {
      if (this.poolManager == null) throw new IllegalArgumentException("PoolManager is not initialized");
      int ii=0;
      while (true) {
         try {
            synchronized(this.meetingPoint) {
               ResourceWrapper rw = (ResourceWrapper)this.poolManager.reserve(PoolManager.USE_OBJECT_REF);
               Connection con = (Connection)rw.getResource();
               return con;
            }
          }
          catch (XmlBlasterException e) {
             if (e.getErrorCode() == ErrorCode.RESOURCE_EXHAUST && ii < this.maxResourceExhaustRetries) {
                if (ii == 0) log.warning("Caught exception in reserve(), going to poll " + this.maxResourceExhaustRetries + " times every " + resourceExhaustSleepGap + " millis");
                try { Thread.sleep(this.resourceExhaustSleepGap); } catch (InterruptedException ie) { /* Ignore */ }
                ii++;
             }
             else {
                throw e;
             }
         }
      } // while
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#release(java.sql.Connection)
    * @throws Exception of type XmlBlasterException or IllegalArgumentException
    */
   public void release(Connection con) throws Exception {
      if (this.poolManager == null) throw new IllegalArgumentException("PoolManager is not initialized");
      synchronized(this.meetingPoint) {
         this.poolManager.release(""+con);
      }
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#erase(java.sql.Connection)
    * @throws IllegalArgumentException
    */
   public void erase(Connection con) throws IllegalArgumentException {
      if (this.poolManager == null) throw new IllegalArgumentException("PoolManager is not initialized");
      synchronized(this.meetingPoint) {
         this.poolManager.erase(""+con);
      }
   }

   /**
    * Cleanup and destroy everything. 
    */
   private void destroy() {
      synchronized(this.meetingPoint) {
         if (this.poolManager != null) {
            if (log.isLoggable(Level.FINE)) log.fine("Destroying pool: " + this.poolManager.toXml());
            this.poolManager.destroy();
            this.poolManager = null;
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
   * Create a new JDBC connection, the driver must be registered already.
   * @param instanceId A unique identifier
   * @return The JDBC connection
   */
   public Object toCreate(String instanceId) {
      try {
         return DriverManager.getConnection (this.dbUrl, this.dbUser, this.dbPasswd);
      }
      catch(Exception e) {
         throw new IllegalArgumentException(this.getClass().getName() + ": Couldn't open database connection dbUrl=" + this.dbUrl + " dbUser=" + this.dbUser + ": " + e.toString());
      }
   }

   /**
   * Destroy the JDBC connection.
   * The driver remains.
   * @param resource The Connection object
   */
   public void toErased(Object resource) {
      Connection con = (Connection)resource;
      try {
         con.close();
         log.info("JDBC connection closed for '" + this.dbUrl + "', '" + this.dbUser + "'");
      }
      catch (Exception e) {
         log.severe("System Exception in close JDBC connection: " + e.toString());
         // For example Oracle throws this if you have shutdown Oracle in the mean time:
         // System Exception in close JDBC connection: java.sql.SQLException: Io exception: End of TNS data channel
      }
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.db.I_DbPool#update(String)
    */
   public int update(String command) throws Exception {
      Connection  conn = null;
      Statement   stmt = null;
      ResultSet   rs = null;
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
            if (rs!=null) rs.close();
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
      ResultSet   rs = null;
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
            if (rs!=null) rs.close();
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
         cb.result(rs);
      }
      catch (SQLException e) {
         if (e.getSQLState() != null && e.getSQLState().indexOf("42000") != -1
             && e.toString().indexOf("ORA-00942") != -1) { // ORA-00942: table or view does not exist TODO: How to make this portable???
            // sqlStateXOpen=1, sqlStateSQL99=2 (Oracle 10g returns 0)
            //log.fine("SQLStateType=" + conn.getMetaData().getSQLStateType());
            log.fine("No db change detected, the table does not exist: " + e.toString());
            cb.result(null);
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
            if (rs!=null) rs.close();
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
}
