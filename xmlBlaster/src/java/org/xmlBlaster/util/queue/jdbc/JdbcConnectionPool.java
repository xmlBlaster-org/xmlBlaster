/*------------------------------------------------------------------------------
Name:      JdbcConnectionPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   JDBC Connection Pool for persistent queues.
Author:    laghi@swissinfo.org
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.plugin.PluginInfo;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.DatabaseMetaData;
import java.util.Hashtable;
import java.util.StringTokenizer;

// only for testing
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.enum.ErrorCode;


/**
 * A Pool of connections to the database to be used for a persistent queue. To
 * keep genericity, queries and update strings are read from properties.
 */
public class JdbcConnectionPool implements I_Timeout {
   private static final String ME = "JdbcConnectionPool";
   private Connection[] connections = null;
   private LogChannel log = null;
   private Global glob = null;

   /** the initial capacity of this pool. */
   private int capacity;
   private int currentIndex = 0;
   private Thread threadToNotify = null;
   private Object busyConnectionTolken = null;
   private int waitingCalls = 0;
   private long connectionBusyTimeout = 60000L;
   private int   maxWaitingThreads = 200;
   private Hashtable mapping = null;
   private boolean initialized = false;

   // I use this dummy to synchronize because if I use threadToNotify it could
   // be null and thus generating a NullPointerException.
   private Object notifierSynch = new Object();

   private String tableNamePrefix = "XMLBLASTER";
   private int tableAllocationIncrement = 2;
   /** will be set when a connecton is broken */
   private boolean connectionLost = true;
   private boolean waitingForReentrantConnections = false;
   private String url;
   private String user;
   private String password;
   private long reconnectionTimeout = 10000L;
   private I_ConnectionListener connectionListener = null;

   private static boolean firstConnectError = true;


  /**
   * Invoked by the timer when a check for reconnection is wanted again
   * @see I_Timeout#timeout(Object)
   */
   public void timeout(Object userData) {
      this.log.warn(ME, "timeout, current index: " + this.currentIndex + ", waiting for reentrant connections: " + this.waitingForReentrantConnections);

      if (this.waitingForReentrantConnections) {
         if (this.currentIndex == this.capacity-1)
            this.waitingForReentrantConnections = false;
         else {
            // respan the timer ...
            this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
         }
      }

      // try a connection ...
      try {
         this.log.warn(ME, "timeout:retrying to establish connections");
         // initializing and establishing of connections to DB ...
         this.connections = new Connection[this.capacity];
         for (int i = 0; i < this.capacity; i++) {
            if (this.log.TRACE) this.log.trace(ME, "initializing DB connection "+ i);
            this.currentIndex = i;
            this.connections[i] = DriverManager.getConnection(url, user, password);
         }
         this.connectionLost = false;
         I_ConnectionListener lst = this.connectionListener;
         lst.reconnected();
         this.log.info(ME, "Successfully reconnected to database");

      }
      catch (SQLException ex) {
         // clean up the connections which might have been established
         for (int i = 0; i < this.currentIndex; i++) disconnect(i);
         // respan the timer ...
         this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
      }
   }

   /**
    * Sets the connection listener. Only one is allowed at a time.
    */
   public void setConnectionListener(I_ConnectionListener connectionListener) {
      this.connectionListener = connectionListener;
   }


   /**
    * returns null if no connection available
    */
   public Connection get() {
      if (this.log.CALL) this.log.call(ME, "get invoked");
      if (this.log.DUMP) {
         String txt = "get: ";
         for (int i=0; i < this.connections.length; i++) {
            txt += this.connections[i];
         }
         this.log.dump(ME, txt);
      }
      Connection ret = null;

      synchronized(this.connections) {
         if ((this.currentIndex >= 0) && (this.currentIndex < this.capacity)) {
            ret = this.connections[this.currentIndex];
            this.connections[this.currentIndex] = null;
            this.currentIndex--;
            return ret;
         }
         if (this.log.TRACE) log.trace(ME, "get: index out of range index is : " + this.currentIndex);
         this.threadToNotify = Thread.currentThread();
         return null;
      }
   }


   public boolean put(Connection conn) {
      if (this.log.CALL) this.log.call(ME, "put invoked");
      if (conn == null) return false;
      synchronized(this.connections) {
         if ((this.currentIndex >= -1) && (this.currentIndex < (this.capacity-1))) {
            this.connections[++this.currentIndex] = conn;

            // must be in the synchronized for the connections
            if (this.threadToNotify != null) {
               Thread thread  = this.threadToNotify;
               this.threadToNotify = null;
               thread.interrupt();
            }

            return true;
         }

         log.error(ME, "put: outside range. Index is " + this.currentIndex);
         return false;
      }
   }


   /**
    * Returns the global object associated with this pool.
    */
   public Global getGlobal() {
      return this.glob;
   }

   /** The default constructor currently does nothing. Initialization is done in the initialize() method. */
   public JdbcConnectionPool() {
      this.busyConnectionTolken = new Object();
   }


   /**
    * returns true if the pool already is initialized, false otherwise.
    */
   public boolean isInitialized() {
      return this.initialized;
   }

   /**
    * Is called after the instance is created. It reads the needed properties,
    * sets the driver and then connects to the database as many times as
    * specified in the properties. <p/>
    * Note that prefix is the string which identifies a certain database.
    * It is called prefix because it is the prefix in the xmlBlaster.properties
    * for the properties bound to the database.<br> A pool instance is created
    * for every such database. For example if you want the history queue (the
    * queue which stores the published messages) on a different database than
    * the callback queues, then you could define different databases.
    *
    * <li>prefix.driverName DEFAULTS TO "org.postgresql.Driver"</li> <li>prefix.connectionPoolSize "DEFAULTS TO 5"</li>
    * <li>prefix.url DEFAULTS TO "jdbc:postgresql://localhost/test"</li> <li>prefix.user DEFAULTS TO "postgres"</li>
    * <li>prefix.password  DEFAULTS TO ""</li> </ul>
    *
    * @deprecated you should use initialize(Global, PluginInfo) instead
    */
   public synchronized void initialize(Global glob, String prefix)
      throws ClassNotFoundException, SQLException, XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("jdbc");
      if (this.log.CALL) this.log.call(ME, "initialize. Used Property Prefix: " + prefix);

      if (this.initialized) return;

      org.jutils.init.Property prop = glob.getProperty();
      String xmlBlasterJdbc = prop.get("JdbcDriver.drivers", "org.postgresql.Driver:sun.jdbc.odbc.JdbcOdbcDriver:ORG.as220.tinySQL.dbfFileDriver:oracle.jdbc.driver.OracleDriver");

      // in xmlBlaster.properties file we separate the drivers with a ',' but
      // in the system properties they should be separated with ':' so it may
      // be time to change that in our properties
      //(THIS IS NOT NEEDED ANYMORE (fixed 2002-10-07 Michele)
      // xmlBlasterJdbc = xmlBlasterJdbc.replace(',', ':');

      System.setProperty("jdbc.drivers", xmlBlasterJdbc);

      // Default is:
      //   queue.persistent.url=jdbc:postgresql://localhost/test
      // This has precedence: 
      //   cb.queue.persistent.url=jdbc:postgresql://localhost/test
      //   client.queue.persistent.url=jdbc:postgresql://localhost/test

      this.url = prop.get("queue.persistent.url", "jdbc:postgresql://localhost/test");
      this.user = prop.get("queue.persistent.user", "postgres");
      this.password = prop.get("queue.persistent.password", "");
      this.capacity = prop.get("queue.persistent.connectionPoolSize", 5);
      this.connectionBusyTimeout = prop.get("queue.persistent.connectionBusyTimeout", 60000L);
      this.maxWaitingThreads = prop.get("queue.persistent.maxWaitingThreads", 200);
      this.tableAllocationIncrement = prop.get("queue.persistent.tableAllocationIncrement", 10);
      this.tableNamePrefix = prop.get("queue.persistent.tableNamePrefix", "XMLBLASTER").toUpperCase();

      url = prop.get(prefix + ".url", this.url);
      user = prop.get(prefix + ".user", this.user);
      password = prop.get(prefix + ".password", this.password);
      this.capacity = prop.get(prefix + ".connectionPoolSize", this.capacity);
      this.connectionBusyTimeout = prop.get(prefix + ".connectionBusyTimeout", this.connectionBusyTimeout);
      this.maxWaitingThreads = prop.get(prefix + ".maxWaitingThreads", this.maxWaitingThreads);
      this.tableAllocationIncrement = prop.get(prefix + ".tableAllocationIncrement", this.tableAllocationIncrement);
      this.tableNamePrefix = prop.get(prefix + ".tableNamePrefix", this.tableNamePrefix).trim().toUpperCase();

      if (this.log.DUMP) {
         this.log.dump(ME, "initialize -prefix is           : " + prefix);
         this.log.dump(ME, "initialize -url                 : " + url);
         this.log.dump(ME, "initialize -user                : " + user);
         this.log.dump(ME, "initialize -password            : " + password);
         this.log.dump(ME, "initialize -max number of conn  : " + this.capacity);
         this.log.dump(ME, "initialize -conn busy timeout   : " + this.connectionBusyTimeout);
         this.log.dump(ME, "initialize -driver list         : " + xmlBlasterJdbc);
         this.log.dump(ME, "initialize -max. waiting Threads:" + this.maxWaitingThreads);
      }

      // could block quite a long time if the number of connections is big
      // or if the connection to the DB is slow.
      try {
         // initializing and establishing of connections to DB ...
         this.connections = new Connection[this.capacity];
         for (int i = 0; i < this.capacity; i++) {
            if (this.log.TRACE) this.log.trace(ME, "initializing DB connection "+ i);
            this.currentIndex = i;
            this.connections[i] = DriverManager.getConnection(url, user, password);
         }
         this.connectionLost = false;
         parseMapping(prop);
         this.initialized = true;
      }
      catch (SQLException ex) {
         if (firstConnectError) {
            firstConnectError = false;
            this.log.error(ME, "exception when connecting to DB, error code: '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
            this.log.info(ME, "diagnostics: initialize -prefix is           : '" + prefix + "'");
            this.log.info(ME, "diagnostics: initialize -url                 : '" + url + "'");
            this.log.info(ME, "diagnostics: initialize -user                : '" + user + "'");
            this.log.info(ME, "diagnostics: initialize -password            : '" + password + "'");
            this.log.info(ME, "diagnostics: initialize -max number of conn  : '" + this.capacity + "'");
            this.log.info(ME, "diagnostics: initialize -conn busy timeout   : '" + this.connectionBusyTimeout + "'");
            this.log.info(ME, "diagnostics: initialize -driver list         : '" + xmlBlasterJdbc + "'");
            this.log.info(ME, "diagnostics: initialize -max. waiting Threads: '" + this.maxWaitingThreads + "'");
         }
         else {
            if (this.log.TRACE) this.log.trace(ME, "exception when connecting to DB, error code: '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
         }

         // clean up the connections which might have been established
         // even if it probably won't help that much ...
         for (int i = 0; i < this.currentIndex; i++) disconnect(i);
         throw ex;
      }
      this.log.info(ME, "Connections for group '" + prefix + "' to DB '" + url + "' successfully established.");
   }

   /**
    * Is called after the instance is created. It reads the needed properties,
    * sets the driver and then connects to the database as many times as
    * specified in the properties. <p/>
    * Note that prefix is the string which identifies a certain database.
    * It is called prefix because it is the prefix in the xmlBlaster.properties
    * for the properties bound to the database.<br> A pool instance is created
    * for every such database. For example if you want the history queue (the
    * queue which stores the published messages) on a different database than
    * the callback queues, then you could define different databases.
    *
    * <li>prefix.driverName DEFAULTS TO "org.postgresql.Driver"</li> <li>prefix.connectionPoolSize "DEFAULTS TO 5"</li>
    * <li>prefix.url DEFAULTS TO "jdbc:postgresql://localhost/test"</li> <li>prefix.user DEFAULTS TO "postgres"</li>
    * <li>prefix.password  DEFAULTS TO ""</li> </ul>
    */
   public synchronized void initialize(Global glob, PluginInfo pluginInfo)
      throws ClassNotFoundException, SQLException, XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("jdbc");
      if (this.log.CALL) this.log.call(ME, "initialize. Used Plugin type and version: " + pluginInfo.getTypeVersion());

      if (this.initialized) return;

      // could these also be part of the properties specific to the invoking plugin ?
      org.jutils.init.Property prop = glob.getProperty();
      String xmlBlasterJdbc = prop.get("JdbcDriver.drivers", "org.postgresql.Driver:sun.jdbc.odbc.JdbcOdbcDriver:ORG.as220.tinySQL.dbfFileDriver:oracle.jdbc.driver.OracleDriver");

      // in xmlBlaster.properties file we separate the drivers with a ',' but
      // in the system properties they should be separated with ':' so it may
      // be time to change that in our properties
      //(THIS IS NOT NEEDED ANYMORE (fixed 2002-10-07 Michele)
      // xmlBlasterJdbc = xmlBlasterJdbc.replace(',', ':');

      System.setProperty("jdbc.drivers", xmlBlasterJdbc);

      // Default is:
      //   queue.persistent.url=jdbc:postgresql://localhost/test
      // This has precedence: 
      //   cb.queue.persistent.url=jdbc:postgresql://localhost/test
      //   client.queue.persistent.url=jdbc:postgresql://localhost/test

      java.util.Properties pluginProp = pluginInfo.getParameters();

      // the old generic properties (for the defaults) outside the plugin 
      this.url = prop.get("queue.persistent.url", "jdbc:postgresql://localhost/test");
      this.user = prop.get("queue.persistent.user", "postgres");
      this.password = prop.get("queue.persistent.password", "");
      this.capacity = prop.get("queue.persistent.connectionPoolSize", 5);
      this.connectionBusyTimeout = prop.get("queue.persistent.connectionBusyTimeout", 60000L);
      this.maxWaitingThreads = prop.get("queue.persistent.maxWaitingThreads", 200);
      // these should be handled by the JdbcManager
      this.tableAllocationIncrement = prop.get("queue.persistent.tableAllocationIncrement", 10);
      this.tableNamePrefix = prop.get("queue.persistent.tableNamePrefix", "XMLBLASTER").toUpperCase();

      // the property settings specific to this plugin type / version
      url = pluginProp.getProperty("url", this.url);
      user = pluginProp.getProperty("user", this.user);
      password = pluginProp.getProperty("password", this.password);

      String help = pluginProp.getProperty("connectionPoolSize", "" + this.capacity);
      try {
         this.capacity = Integer.parseInt(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'connectionPoolSize' plugin-property is not parseable: '" + help + "' will be using the default '" + this.capacity + "'");
      }

      help = pluginProp.getProperty("connectionBusyTimeout", "" + this.connectionBusyTimeout);
      try {
         this.connectionBusyTimeout = Long.parseLong(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'connectionBusyTimeout' plugin-property is not parseable: '" + help + "' will be using the default '" + this.connectionBusyTimeout + "'");
      }

      help = pluginProp.getProperty("maxWaitingThreads", "" + this.maxWaitingThreads);
      try {
         this.maxWaitingThreads = Integer.parseInt(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'maxWaitingThreads' plugin-property is not parseable: '" + help + "' will be using the default '" + this.maxWaitingThreads + "'");
      }

      // these should be handled by the JdbcManager
      help = pluginProp.getProperty("tableAllocationIncrement", "" + this.tableAllocationIncrement);
      try {
         this.tableAllocationIncrement = Integer.parseInt(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'tableAllocationIncrement' plugin-property is not parseable: '" + help + "' will be using the default '" + this.tableAllocationIncrement + "'");
      }

      this.tableNamePrefix = pluginProp.getProperty("tableNamePrefix", this.tableNamePrefix).trim().toUpperCase();

      if (this.log.DUMP) {
         this.log.dump(ME, "initialize -type/version is     : '" + pluginInfo.getTypeVersion() + "'");
         this.log.dump(ME, "initialize -url                 : " + url);
         this.log.dump(ME, "initialize -user                : " + user);
         this.log.dump(ME, "initialize -password            : " + password);
         this.log.dump(ME, "initialize -max number of conn  : " + this.capacity);
         this.log.dump(ME, "initialize -conn busy timeout   : " + this.connectionBusyTimeout);
         this.log.dump(ME, "initialize -driver list         : " + xmlBlasterJdbc);
         this.log.dump(ME, "initialize -max. waiting Threads:" + this.maxWaitingThreads);
      }

      // could block quite a long time if the number of connections is big
      // or if the connection to the DB is slow.
      try {
         // initializing and establishing of connections to DB ...
         this.connections = new Connection[this.capacity];
         for (int i = 0; i < this.capacity; i++) {
            if (this.log.TRACE) this.log.trace(ME, "initializing DB connection "+ i);
            this.currentIndex = i;
            this.connections[i] = DriverManager.getConnection(url, user, password);
         }
         this.connectionLost = false;
         parseMapping(prop);
         this.initialized = true;
      }
      catch (SQLException ex) {
         if (firstConnectError) {
            firstConnectError = false;
            this.log.error(ME, "exception when connecting to DB, error code : '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
            this.log.info(ME, "diagnostics: initialize -type/version is     : '" + pluginInfo.getTypeVersion() + "'");
            this.log.info(ME, "diagnostics: initialize -url                 : '" + url + "'");
            this.log.info(ME, "diagnostics: initialize -user                : '" + user + "'");
            this.log.info(ME, "diagnostics: initialize -password            : '" + password + "'");
            this.log.info(ME, "diagnostics: initialize -max number of conn  : '" + this.capacity + "'");
            this.log.info(ME, "diagnostics: initialize -conn busy timeout   : '" + this.connectionBusyTimeout + "'");
            this.log.info(ME, "diagnostics: initialize -driver list         : '" + xmlBlasterJdbc + "'");
            this.log.info(ME, "diagnostics: initialize -max. waiting Threads: '" + this.maxWaitingThreads + "'");
         }
         else {
            if (this.log.TRACE) this.log.trace(ME, "exception when connecting to DB, error code: '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
         }

         // clean up the connections which might have been established
         // even if it probably won't help that much ...
         for (int i = 0; i < this.currentIndex; i++) disconnect(i);
         throw ex;
      }
      this.log.info(ME, "Connections for plugin type/version '" + pluginInfo.getTypeVersion() + "' to DB '" + url + "' successfully established.");
   }


   /**
    * @return the prefix for the name of the tables to associate to the queues
    */
   public String getTableNamePrefix() {
      return this.tableNamePrefix;
   }


   /**
    * @return the number of tables to add each time no free tables are available
    *         when creating a new queue.
    */
   public int getTableAllocationIncrement() {
      return this.tableAllocationIncrement;
   }


   /** This method is used in the init method */
   private Hashtable parseMapping(org.jutils.init.Property prop)
         throws XmlBlasterException, SQLException {
      if (this.log.CALL) this.log.call(ME, "parseMapping");
      Connection conn = null;
      String productName = null;
      try {
         conn = this.getConnection();
         productName = conn.getMetaData().getDatabaseProductName();
      }
      finally {
         if (conn != null) releaseConnection(conn);
      }
//      String mappingText = prop.get("JdbcDriver." + productName + ".mapping", "");
      String mappingText = prop.get("JdbcDriver.mapping[" + productName + "]", "");
      this.mapping = new Hashtable();
      StringTokenizer tokenizer = new StringTokenizer(mappingText, ",");
      XmlBlasterException ex = null;
      while (tokenizer.hasMoreTokens()) {
         String singleMapping = tokenizer.nextToken();
         int pos = singleMapping.indexOf("=");
         if (pos < 0)
            ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "syntax in xmlBlaster.properties for " + singleMapping +
                " is wrong: no equality sign between key and value");
//            ex = new XmlBlasterException(ME, "syntax in xmlBlaster.properties for " + singleMapping +
//                " is wrong: no equality sign between key and value");
         String key = singleMapping.substring(0, pos);
         String value = singleMapping.substring(pos + 1, singleMapping.length());
         if (this.log.TRACE) this.log.trace(ME, "mapping " + key + " to " + value);
         this.mapping.put(key, value);
      }
      if (ex != null) throw ex;
      return this.mapping;
   }

   /**
    * The mapping is  taken out of the xmlBlaster.properties file. It is an
    * Hashtable where the keys are the logical names used for a type in the
    * JdbcQueuePlugin and the values are the real names to be used in the database used (which may be vendor specific).
    */
   public Hashtable getMapping() {
      return this.mapping;
   }

   /**
    * Disconnects the specified connection. If the range is outside an error
    * log is written. If the connection has been disconnected previously,
    * then the disconnection is silently ignored. If an exception occurs when
    * closing the connection, an error log is written but the resource is cleaned up (the connection is set to null).
    */
   private final void disconnect(int connNumber) {
      if ((connNumber < 0) || (connNumber >= this.capacity)) {
         log.error(ME, "failed to disconnect connection nr. " + connNumber + " because of value out of range");
         return;
      }
      //if already disconnected it will silently return
      if (this.connections[connNumber] == null) return;
      try {
         Connection conn = this.connections[connNumber];
         this.connections[connNumber] = null;
         conn.close();
         if (this.log.TRACE) this.log.trace(ME, "connection nr. " + connNumber + " disconnected ( object address: " + conn + ")");
      }
      catch (/*SQL*/ Exception ex) {
         log.error(ME, "could not close connection " + connNumber + " correctly but resource is set to null");
      }
      this.initialized = false;
   }

   /**
    * Closes all connections to the Database. It should not throw any
    * exception. Exceptions coming from the backend or the communication
    * layer are catched and logged. When this occurs, the affected connections are set to null.
    */
   public void disconnect() {
      if (this.log.CALL) this.log.call(ME, "disconnect invoked");
      for (int i = 0; i < this.capacity; i++) disconnect(i);
      this.initialized = false;
   }

   public void finalize() {
      disconnect();
   }


   /**
    * returns true if the connection is temporarly lost (and the pool is polling
    * for new connections)
    */
   public final boolean isConnectionLost() {
      return this.connectionLost;
   }

   /**
    * informs this pool that the connection to the DB has been lost
    */
   public final void setConnectionLost() {
      if (!this.connectionLost) {
         synchronized (this) {
            if (!this.connectionLost) {
                this.connectionLost = true;
                this.waitingForReentrantConnections = true;
            }
         }

         // start polling to wait until all connections have returned
         // start pooling to see if new connections can be established again
         this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
         I_ConnectionListener lst = this.connectionListener;
         lst.disconnected();
      }
   }

   /**
    *
    */
   public Connection getConnection() throws XmlBlasterException {
//      if (this.connectionLost) return null;
      if (this.connectionLost) throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "getConnection: Connection Lost. Going in polling modus");
      if (this.waitingCalls > this.maxWaitingThreads)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_TOO_MANY_THREADS, ME, "Too many threads waiting for a connection to the DB. Increase the property 'queue.persistent.maxWaitingThreads'");

      this.waitingCalls++;
      if (this.log.CALL) this.log.call(ME, "getConnection " + this.currentIndex + " waiting calls: " + this.waitingCalls);

      Connection conn = null;

      synchronized (this.busyConnectionTolken) {

         conn = this.get();
         if (conn != null) {
            //if (this.log.TRACE) this.log.trace(ME, ".getConnection: returning a connection the normal way");
            this.waitingCalls--;
            return conn;
         }

         if (this.log.TRACE) this.log.trace(ME, ".getConnection: returned connection was null: waiting for a free connection");

         synchronized (this.notifierSynch) {
            try {
               this.threadToNotify.sleep(this.connectionBusyTimeout);
               this.waitingCalls--;
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "no free DB connections available after timeout. This could be a temporary problem");
            }
            catch (InterruptedException ex) {
               if (this.log.TRACE) this.log.trace(ME, "continuing after waiting for a free DB Connection");
               conn = get();
               this.waitingCalls--;
               if (conn != null) return conn;
               this.log.error(ME, "connection still null: this is serious");
               return null;
            }
         }

      }
   }


   /**
    * Used to give back a connection to the pool. If the pool is already full
    * it will throw an exception.
    */
   public void releaseConnection(Connection conn)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "releaseConnection " + this.currentIndex + " waiting calls: " + this.waitingCalls);
      boolean isOk = put(conn);
      if (!isOk) {
         this.log.error(ME, "the connection pool is already full");
      }

   }


   public void dumpMetaData() {

      Connection conn = null;
      try {
         conn = getConnection();
         DatabaseMetaData metaData = conn.getMetaData();

         this.log.info(ME, "--------------- DUMP OF METADATA FOR THE DB START---------------------");
         String driverName = metaData.getDriverName();
         String productName = metaData.getDatabaseProductName();
         this.log.info(ME, "Driver name          :'" + driverName +"', product name: '" + productName + "'");
         this.log.info(ME, "max binary length    : " + metaData.getMaxBinaryLiteralLength());
         this.log.info(ME, "max char lit. length : " + metaData.getMaxCharLiteralLength());
         this.log.info(ME, "max column length    : " + metaData.getMaxColumnNameLength());
         this.log.info(ME, "max cols. in table   : " + metaData.getMaxColumnsInTable());
         this.log.info(ME, "max connections      : " + metaData.getMaxConnections());
         this.log.info(ME, "max statement length : " + metaData.getMaxStatementLength());
         this.log.info(ME, "max nr. of statements: " + metaData.getMaxStatements());
         this.log.info(ME, "max tablename length : " + metaData.getMaxTableNameLength());
         this.log.info(ME, "url                  : " + metaData.getURL());
         this.log.info(ME, "support for trans.   : " + metaData.supportsTransactions());
         this.log.info(ME, "--------------- DUMP OF METADATA FOR THE DB END  ---------------------");

      }
      catch (XmlBlasterException ex) {
         this.log.error(ME, "dumpMetaData: exception: " + ex.getMessage());
      }
      catch (SQLException ex) {
         this.log.error(ME, "dumpMetaData: SQL exception: " + ex.getMessage());
      }
      finally {
         try {
            if (conn != null) releaseConnection(conn);
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "dumpMetaData: exception when releasing the connection: " + ex.getMessage());
         }
      }
   }


   public static void main(String[] args) {

      //here starts the main method ...
      LogChannel log = null;
      Connection conn = null;
      try {
         Global glob = Global.instance();
         glob.init(args);
         log = glob.getLog("queue");
         log.info(ME, "starting the application: The Tests start here ");

         String prefix = "cb.queue.persistent";
         org.jutils.init.Property prop = glob.getProperty();
         String xmlBlasterJdbc = prop.get("JdbcDriver.drivers", "org.postgresql.Driver");
         System.setProperty("jdbc.drivers", xmlBlasterJdbc);
         String url = prop.get(prefix + ".url", "");
         String user = prop.get(prefix + ".user", "postgres");
         String password = prop.get(prefix + ".password", "");

         if (log.DUMP) {
            log.dump(ME, "initialize -prefix is           : " + prefix);
            log.dump(ME, "initialize -url                 : " + url);
            log.dump(ME, "initialize -user                : " + user);
            log.dump(ME, "initialize -password            : " + password);
            log.dump(ME, "initialize -driver list         : " + xmlBlasterJdbc);
         }

         log.info(ME, "establishing the connection");
         conn = DriverManager.getConnection(url, user, password);
         log.info(ME, "connection established. Sleeping 1 second");
         Thread.currentThread().sleep(1000L);
         log.info(ME, "finished");
      }
      catch (/*SQL*/Exception ex) {
         if (log !=null)
            log.error(ME, "exception when connecting to DB, error code: " + " : " + ex.getMessage());
      }
      finally {
         try {
            if (log != null) log.info(ME, "closing the connection");
            if (conn != null) {
               conn.close();
            }
            if (log != null) log.info(ME, "connection closed");
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }

   }
}


