/*------------------------------------------------------------------------------
Name:      JdbcConnectionPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   JDBC Connection Pool for persistent queues.
Author:    laghi@swissinfo.org
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.apache.commons.lang.Tokenizer;
import org.jutils.log.LogChannel;
import org.jutils.text.StringHelper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;
import java.util.Properties;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.DatabaseMetaData;
import java.util.Hashtable;

// only for testing
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.enum.ErrorCode;

import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;

/**
 * A Pool of connections to the database to be used for a persistent queue. To
 * keep genericity, queries and update strings are read from properties.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/queue.jdbc.commontable.html">The queue.jdbc.commontable requirement</a>
 */
public class JdbcConnectionPool implements I_Timeout, I_StorageProblemNotifier {
   private static String ME = "JdbcConnectionPool";
   private Connection[] connections = null;
   private LogChannel log = null;
   private Global glob = null;

   /** the initial capacity of this pool. */
   private int capacity;
   private int currentIndex = 0;
   private Object getConnectionMonitor = null;
   private Object waitingCallsMonitor = null;
   private int waitingCalls = 0;
   private long connectionBusyTimeout = 60000L;
   private int   maxWaitingThreads = 200;
   private Hashtable mapping = null;
   private boolean initialized = false;
   private boolean isWaiting = false;
   private boolean isNotified = false;

   private String tableNamePrefix = "XB"; // stands for "XMLBLASTER", it is chosen short for Postgres max. eval length = 26 chars (timestamp has already 19 chars)
   private int tableAllocationIncrement = 2;
   /** will be set when a connecton is broken */
   private int status = I_StorageProblemListener.UNDEF;
   private boolean waitingForReentrantConnections = false;
   private String url;
   private String user;
   private String password;
   private long reconnectionTimeout = 10000L;
   private I_StorageProblemListener storageProblemListener = null;

   private static boolean firstConnectError = true;
   private Properties pluginProp = null;
   private boolean dbAdmin = true;
   private int queryTimeout = 0; // wait indefinetely
   private int managerCount = 0;
   private boolean isShutdown = false;
   private boolean enableBatchMode;
   private String configurationIdentifier;
   private boolean cascadeDeleteSupported;
   private boolean nestedBracketsSupported;

   /**
    * returns the plugin properties, i.e. the specific properties passed to the jdbc queue plugin.
    * These are commonly used by the jdbc manager.
    */
    public Properties getPluginProperties() {
       return this.pluginProp;
    }

  /**
   * Invoked by the timer when a check for reconnection is wanted again
   * @see I_Timeout#timeout(Object)
   */
   public void timeout(Object userData) {
      this.log.warn(ME, "timeout, current index: " + this.currentIndex + ", waiting for reentrant connections: " + this.waitingForReentrantConnections);

      synchronized (this) {
         if (this.waitingForReentrantConnections) {
            if (this.currentIndex == this.capacity-1)
               this.waitingForReentrantConnections = false;
            else {
               // respan the timer ...
               this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
               return; // wait until all connections have been given back to the pool ...
               // in case a connection is blocking this could be blocking everything: find a better way to 
               // throw away the blocking connection and avoid to get it back later.
            }
         }
      }

      // try a connection ...
      try {
         this.log.warn(ME, "timeout:retrying to establish connections");
         // initializing and establishing of connections to DB but first clearing the connections ...
         connect(true);
      }
      catch (Throwable ex) {
         // clean up the connections which might have been established
         for (int i = 0; i < this.currentIndex; i++) disconnect(i);
         // respan the timer ...
         this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
      }
   }

   /**
    * Sets the connection listener. Only one is allowed at a time. So if there is
    * already a connection listener, it will be overwritten (and the old one will
    * not get anyu notification anymore).
    */
   synchronized public boolean registerStorageProblemListener(I_StorageProblemListener storageProblemListener) {
      this.storageProblemListener = storageProblemListener;
      return true; // always true
   }

   /**
    * Unregisters the storageProblemListener. If no one has been defined, or
    * if the one you want to unregister is different from the one you have
    * registered, nothing is done and 'false' is returned.
    */
   synchronized public boolean unRegisterStorageProblemListener(I_StorageProblemListener storageProblemListener) {
      if ((this.storageProblemListener == null) || (this.storageProblemListener != storageProblemListener)) return false;
      this.storageProblemListener = null;
      return true;
   }


   /**
    * returns null if no connection available
    */
   synchronized private Connection get() throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "get invoked");
      if (this.log.DUMP) {
         String txt = "get: ";
         for (int i=0; i < this.connections.length; i++) {
            txt += this.connections[i];
         }
         this.log.dump(ME, txt);
      }
      Connection ret = null;
      if (this.currentIndex >= this.capacity)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME, "get: Inconsistency in connection index: a negative one is not possible: '" + this.currentIndex + "'");

      if (this.currentIndex >= 0) {
         ret = this.connections[this.currentIndex];
         this.connections[this.currentIndex] = null;
         this.currentIndex--;
         return ret;
      }
      if (this.log.TRACE) log.trace(ME, "get: index out of range index is : " + this.currentIndex);
      this.isNotified = false;
      this.isWaiting = true;
      try {
         this.wait(this.connectionBusyTimeout);
      }
      catch (Exception ex) {
         this.log.error(ME, "get:  waiting for a connection: " + ex.toString());
         ex.printStackTrace();
      }
      this.isWaiting = false;
      if (this.isNotified) {
         ret = this.connections[this.currentIndex];
         this.connections[this.currentIndex] = null;
         this.currentIndex--;
         return ret;
      }
      throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "get: a timeout occured when waiting for a free DB connection. Either the timeout is too short or other connections are blocking");
   }


   private boolean put(Connection conn) {
      if (this.log.CALL) this.log.call(ME, "put invoked");
      if (conn == null) return false;
      if (this.log.TRACE) {
         try {
            if (!conn.getAutoCommit()) this.log.error(ME, "put: error: the connection has not properly been reset: autocommit is 'false'");
         }
         catch (Throwable ex) {
            this.log.error(ME, "put:  checking for autocommit. reason: " + ex.toString());
            ex.printStackTrace();
         }
      }
      synchronized(this) {
         if ((this.currentIndex >= -1) && (this.currentIndex < (this.capacity-1))) {
            this.connections[++this.currentIndex] = conn;
            if (this.isWaiting) {
               this.isNotified = true;
               this.notify();
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
      this.getConnectionMonitor = new Object();
      this.waitingCallsMonitor = new Object();
   }


   /**
    * returns true if the pool already is initialized, false otherwise.
    */
   public boolean isInitialized() {
      return this.initialized;
   }

   /**
    * Connects to the DB (so many connections as configured)
    * @param disconnectFirst if 'true' then all connections to the db are closed before reconnecting.
    */
   private void connect(boolean disconnectFirst) throws SQLException {
      int oldStatus;
      I_StorageProblemListener lst = null;
      synchronized(this) {
         if (disconnectFirst) disconnect();
         this.connections = new Connection[this.capacity];
         for (int i = 0; i < this.capacity; i++) {
            if (this.log.TRACE) this.log.trace(ME, "initializing DB connection "+ i + " url=" + url + " user=" + user); // + " password=" + password);
            this.connections[i] = DriverManager.getConnection(url, user, password);
            if (this.log.TRACE) this.log.trace(ME, "initializing DB connection "+ i + " success");
            this.currentIndex = i;
         }
         oldStatus = this.status;
         this.status = I_StorageProblemListener.AVAILABLE;
         lst = this.storageProblemListener;
      }
      this.isShutdown = false;
      if (lst != null) lst.storageAvailable(oldStatus);
      this.log.info(ME, "Successfully reconnected to database");
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
   public synchronized void initialize(Global glob, Properties pluginProperties)
      throws ClassNotFoundException, SQLException, XmlBlasterException {

      if (this.initialized) return;
      this.glob = glob;
      this.pluginProp = pluginProperties;
      this.log = this.glob.getLog("jdbc");
      if (this.log.TRACE) this.log.trace(ME, "initialize");

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


      // the old generic properties (for the defaults) outside the plugin 
      this.url = prop.get("queue.persistent.url", "jdbc:postgresql://localhost/test");
      this.user = prop.get("queue.persistent.user", "postgres");
      this.password = prop.get("queue.persistent.password", "");
      this.capacity = prop.get("queue.persistent.connectionPoolSize", 5);
      this.connectionBusyTimeout = prop.get("queue.persistent.connectionBusyTimeout", 60000L);
      this.maxWaitingThreads = prop.get("queue.persistent.maxWaitingThreads", 200);
      // these should be handled by the JdbcManager
      this.tableAllocationIncrement = prop.get("queue.persistent.tableAllocationIncrement", 10);
      this.tableNamePrefix = prop.get("queue.persistent.tableNamePrefix", "XB").toUpperCase(); // XB stands for XMLBLASTER

      // the property settings specific to this plugin type / version
      this.url = pluginProp.getProperty("url", this.url);
      String dbInstanceName = glob.getStrippedId();
      this.url = StringHelper.replaceFirst(this.url, "$_{xmlBlaster_uniqueId}", dbInstanceName);
      ME = "JdbcConnectionPool-" + this.url;
      this.user = pluginProp.getProperty("user", this.user);
      this.password = pluginProp.getProperty("password", this.password);

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

      help = pluginProp.getProperty("enableBatchMode", "true");
      try {
         this.enableBatchMode = Boolean.getBoolean(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'enableBatchMode' plugin-property is not parseable: '" + help + "' will be using the default '" + this.enableBatchMode + "'");
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

      String tmp = pluginProp.getProperty("dbAdmin", "true").trim();
      this.dbAdmin = true;
      if ("false".equalsIgnoreCase(tmp)) this.dbAdmin = false;

      this.configurationIdentifier =  pluginProp.getProperty("configurationIdentifier", null);
      if (this.configurationIdentifier != null) 
         this.configurationIdentifier = this.configurationIdentifier.trim();

      help = pluginProp.getProperty("cascadeDeleteSupported", "true");
      try {
         this.cascadeDeleteSupported = Boolean.getBoolean(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'cascadeDeleteSupported' plugin-property is not parseable: '" + help + "' will be using the default '" + this.cascadeDeleteSupported + "'");
      }

      help = pluginProp.getProperty("nestedBracketsSupported", "true");
      try {
         this.nestedBracketsSupported = Boolean.getBoolean(help);
      }
      catch (Exception ex) {
         this.log.warn(ME, "the 'nestedBracketsSupported' plugin-property is not parseable: '" + help + "' will be using the default '" + this.nestedBracketsSupported + "'");
      }

      if (this.log.DUMP) {
         this.log.dump(ME, "initialize -url                    : " + this.url);
         this.log.dump(ME, "initialize -user                   : " + this.user);
         this.log.dump(ME, "initialize -password               : " + this.password);
         this.log.dump(ME, "initialize -max number of conn     : " + this.capacity);
         this.log.dump(ME, "initialize -conn busy timeout      : " + this.connectionBusyTimeout);
         this.log.dump(ME, "initialize -driver list            : " + xmlBlasterJdbc);
         this.log.dump(ME, "initialize -max. waiting Threads   :" + this.maxWaitingThreads);
         this.log.dump(ME, "initialize -tableNamePrefix        :" + this.tableNamePrefix);
         this.log.dump(ME, "initialize -dbAdmin                :" + this.dbAdmin);
         this.log.dump(ME, "initialize -cascadeDeleteSupported :" + this.cascadeDeleteSupported);
         this.log.dump(ME, "initialize -nestedBracketsSupported:" + this.nestedBracketsSupported);
         if (this.configurationIdentifier != null) 
            this.log.dump(ME, "initialize -configurationIdentifier:" + this.configurationIdentifier);
      }

      // could block quite a long time if the number of connections is big
      // or if the connection to the DB is slow.
      try {
         // initializing and establishing of connections to DB (but first disconnect if already connected)
         connect(true);

         parseMapping(prop);
         if (this.log.DUMP) dumpMetaData();
         this.initialized = true;
      }
      catch (SQLException ex) {
         if (firstConnectError) {
            firstConnectError = false;
            this.log.error(ME, " connecting to DB, error code : '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
            this.log.info(ME, "diagnostics: initialize -url                 : '" + url + "'");
            this.log.info(ME, "diagnostics: initialize -user                : '" + user + "'");
            this.log.info(ME, "diagnostics: initialize -password            : '" + password + "'");
            this.log.info(ME, "diagnostics: initialize -max number of conn  : '" + this.capacity + "'");
            this.log.info(ME, "diagnostics: initialize -conn busy timeout   : '" + this.connectionBusyTimeout + "'");
            this.log.info(ME, "diagnostics: initialize -driver list         : '" + xmlBlasterJdbc + "'");
            this.log.info(ME, "diagnostics: initialize -max. waiting Threads: '" + this.maxWaitingThreads + "'");
            this.log.dump(ME, "diagnostics: initialize -tableNamePrefix     :" + this.tableNamePrefix);
            ex.printStackTrace();
         }
         else {
            if (this.log.TRACE) this.log.trace(ME, " connecting to DB, error code: '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
         }

         // clean up the connections which might have been established
         // even if it probably won't help that much ...
         for (int i = 0; i < this.currentIndex; i++) disconnect(i);
         throw ex;
      }
      this.log.info(ME, "Connections to DB '" + url + "' successfully established.");
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
      if (this.isShutdown) connect(false);


      String mappingKey = null;
      
      if (this.configurationIdentifier == null) {
         Connection conn = null;
         try {
            conn = this.getConnection();
            mappingKey = conn.getMetaData().getDatabaseProductName();
            // replace "Microsoft SQL Server" to "MicrosoftSQLServer"
            // blanks are not allowed, thanks to zhang zhi wei
            mappingKey = StringHelper.replaceAll(mappingKey, " ", "");
            if (this.log.TRACE) 
               this.log.trace(ME, "parseMapping: the mapping will be done for the keyword (which is here is the DB product name)'" + mappingKey + "'");
         }
         finally {
            if (conn != null) releaseConnection(conn);
         }
      }
      else {
         mappingKey = this.configurationIdentifier;
         if (this.log.TRACE) 
            this.log.trace(ME, "parseMapping: the mapping will be done for the keyword (name given in the plugin)'" + mappingKey + "'");
      } 

//      String mappingText = prop.get("JdbcDriver." + productName + ".mapping", "");
      String mappingText = prop.get("JdbcDriver.mapping[" + mappingKey + "]", "");
      if (this.log.TRACE) 
         this.log.trace(ME, "parseMapping: the string to be mapped is '" + mappingText + "'");
      
      this.mapping = new Hashtable();
      // StringTokenizer tokenizer = new StringTokenizer(mappingText, ",");
      Tokenizer tokenizer = new Tokenizer(mappingText, ',', '"');
      XmlBlasterException ex = null;
      while (tokenizer.hasNext()) {
         String singleMapping = tokenizer.nextToken();
         int pos = singleMapping.indexOf("=");
         if (pos < 0)
            ex = new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "syntax in xmlBlaster.properties for " + singleMapping +
                " is wrong: no equality sign between key and value");
//            ex = new XmlBlasterException(ME, "syntax in xmlBlaster.properties for " + singleMapping +
//                " is wrong: no equality sign between key and value");
         String key = singleMapping.substring(0, pos);
         String value = singleMapping.substring(pos + 1, singleMapping.length());
         if (this.log.TRACE) this.log.trace(ME, "parseMapping: mapping " + key + " to " + value);
         this.mapping.put(key, value);
      }
      if (ex != null) {
         if (this.log.TRACE) 
            this.log.trace(ME, "parseMapping: Exception occured: " + ex.getMessage());
         throw ex;
      }
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
   synchronized private final void disconnect(int connNumber) {
      if ((connNumber < 0) || (connNumber >= this.capacity)) {
         log.error(ME, "failed to disconnect connection nr. " + connNumber + " because of value out of range");
         return;
      }
      //if already disconnected it will silently return
      if (this.connections == null || this.connections[connNumber] == null) return;
      try {
         Connection conn = this.connections[connNumber];
         this.connections[connNumber] = null;
         conn.close();
         if (this.log.TRACE) this.log.trace(ME, "connection nr. " + connNumber + " disconnected ( object address: " + conn + ")");
      }
      catch (Throwable ex) {
         log.error(ME, "could not close connection " + connNumber + " correctly but resource is set to null. reason " + ex.toString());
         ex.printStackTrace();
      }
   }

   /**
    * Closes all connections to the Database. It should not throw any
    * exception. Exceptions coming from the backend or the communication
    * layer are catched and logged. When this occurs, the affected connections are set to null.
    */
   synchronized public void disconnect() {
      if (this.log.CALL) this.log.call(ME, "disconnect invoked");
      for (int i = 0; i < this.capacity; i++) disconnect(i);
      this.currentIndex = -1;
   }

   public void finalize() {
      shutdown();
   }


   /**
    * returns true if the connection is temporarly lost (and the pool is polling
    * for new connections)
    */
   public final int getStatus() {
      return this.status;
   }

   /**
    * informs this pool that the connection to the DB has been lost
    */
   public final void setConnectionLost() {
      if (this.status != I_StorageProblemListener.UNAVAILABLE) {
         int oldStatus = this.status;
         synchronized (this) {
            if (this.status == I_StorageProblemListener.UNAVAILABLE) return;
            oldStatus = this.status;
            if (this.status != I_StorageProblemListener.UNAVAILABLE) {
               this.status = I_StorageProblemListener.UNAVAILABLE;
               this.waitingForReentrantConnections = true;
            }
         }

         // start polling to wait until all connections have returned
         // start pooling to see if new connections can be established again
         this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);

         I_StorageProblemListener lst = this.storageProblemListener;
         lst.storageUnavailable(oldStatus);
      }
   }


   /**
    *
    */
   public Connection getConnection() throws XmlBlasterException {
      if (this.status != I_StorageProblemListener.AVAILABLE) 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "getConnection: Connection Lost. Going in polling modus");
      if (this.waitingCalls > this.maxWaitingThreads)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_TOO_MANY_THREADS, ME, "Too many threads waiting for a connection to the DB. Increase the property 'queue.persistent.maxWaitingThreads'");

      synchronized (this.waitingCallsMonitor) {
         this.waitingCalls++;
      }
      if (this.log.CALL) this.log.call(ME, "getConnection " + this.currentIndex + " waiting calls: " + this.waitingCalls);
      synchronized (this.getConnectionMonitor) {
         synchronized (this.waitingCallsMonitor) {
            this.waitingCalls--;
         }
         if (this.isShutdown) {
            try {
               connect(false);
            }
            catch (SQLException ex) {
               String additionalMsg = "check system classpath and 'jdbc.drivers' system property\n";
               additionalMsg += "'classpath' is: '" + System.getProperty("classpath", "") + "'\n";
               additionalMsg +=  "'jdbc.drivers' is: '" + System.getProperty("jdbc.drivers", "") + "'\n";
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getConnection()", ex.getMessage() + " " + additionalMsg);
            }
         }
         return get();
      }
   }


   /**
    * Used to give back a connection to the pool. If the pool is already full
    * it will throw an exception.
    */
   public void releaseConnection(Connection conn) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "releaseConnection " + this.currentIndex + " waiting calls: " + this.waitingCalls);
      try {
         SQLWarning warns = conn.getWarnings();
         /*
            while (warns != null) {
               log.warn(ME, "errorCode=" + warns.getErrorCode() + " state=" + warns.getSQLState() + ": " + warns.toString().trim());
               Thread.currentThread().dumpStack();
               warns = warns.getNextWarning();
            }
         */
         if (log.TRACE) {
            while (warns != null) {
               log.trace(ME, "errorCode=" + warns.getErrorCode() + " state=" + warns.getSQLState() + ": " + warns.toString().trim());
               warns = warns.getNextWarning();
            }
         }
         conn.clearWarnings(); // free memory
      }
      catch (Throwable e) {
         log.warn(ME, "clearWarnings() failed: " + e.toString());
      }
      boolean isOk = put(conn); // if an exception occured it would be better to throw away the connection and make a new one
      if (!isOk) {
         this.log.error(ME, "the connection pool is already full");
      }

   }


   public void dumpMetaData() {
      Connection conn = null;
      try {
         if (this.isShutdown) connect(false);
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
         ex.printStackTrace();
      }
      catch (SQLException ex) {
         this.log.error(ME, "dumpMetaData: SQL exception: " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         try {
            if (conn != null) releaseConnection(conn);
         }
         catch (XmlBlasterException ex) {
            this.log.error(ME, "dumpMetaData:  releasing the connection: " + ex.getMessage());
            ex.printStackTrace();
         }
      }
   }


   public boolean isDbAdmin() {
      return this.dbAdmin;
   }


   public int getQueryTimeout() {
      return this.queryTimeout;
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
         Thread.sleep(1000L);
         log.info(ME, "finished");
      }
      catch (/*SQL*/Exception ex) {
         if (log !=null)
            log.error(ME, " connecting to DB, error code: " + " : " + ex.getMessage());
            ex.printStackTrace();
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

   public synchronized void shutdown() {
      if (this.log.CALL) this.log.call(ME, "shutdown");
      disconnect();
//      this.initialized = false;
      this.isShutdown = true;
   }

   synchronized public void registerManager(JdbcManagerCommonTable manager) {
      if (this.log.CALL) this.log.call(ME, "registerManager, number of managers registered (before registering this one): '" + this.managerCount + "'");
      if (manager == null) return;
      this.managerCount++;
   }

   synchronized public void unregisterManager(JdbcManagerCommonTable manager) {
      if (this.log.CALL) this.log.call(ME, "unregisterManager, number of managers registered (still including this one): '" + this.managerCount + "'");
      if (manager == null) return;
      this.managerCount--;
      if (this.managerCount == 0) shutdown();
   }

   /**
    * The batch mode means that insertions in the database are made in batch mode,
    * i.e. several entries in one sweep. This can increase perfomance significantly
    * on some DBs.   
    *    
    * @return true if batch mode has been enabled, false otherwise (defaults to true).
    */
   public boolean isBatchModeEnabled() {
      return this.enableBatchMode;
   }   

   /**
    * 
    * @return true if 'cascadeDeleteSupported' has been set to true
    */
   public boolean isCascadeDeleteSuppported() {
      return this.cascadeDeleteSupported;
   }
   
   /**
    * 
    * @return true if 'nestedBracketsSupported' has been set to true
    */
   public boolean isNestedBracketsSuppported() {
      return this.nestedBracketsSupported;
   }


}


