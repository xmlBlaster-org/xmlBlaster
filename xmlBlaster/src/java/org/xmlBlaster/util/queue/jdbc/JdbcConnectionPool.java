/*------------------------------------------------------------------------------
Name:      JdbcConnectionPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   JDBC Connection Pool for persistent queues.
Author:    laghi@swissinfo.org
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.apache.commons.lang.Tokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;
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

import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.def.ErrorCode;

import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;

import EDU.oswego.cs.dl.util.concurrent.BoundedLinkedQueue;

/**
 * A Pool of connections to the database to be used for a persistent queue. To
 * keep genericity, queries and update strings are read from properties.
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/queue.jdbc.commontable.html">The queue.jdbc.commontable requirement</a>
 */
public class JdbcConnectionPool implements I_Timeout, I_StorageProblemNotifier {
   private static String ME = "JdbcConnectionPool";
   private static Logger log = Logger.getLogger(JdbcConnectionPool.class.getName());
   private Global glob = null;
   private BoundedLinkedQueue connections;
   
   /** the initial capacity of this pool. */
   private int capacity;
   private int waitingCalls = 0;
   private long connectionBusyTimeout = 60000L;
   private int   maxWaitingThreads = 200;
   private Hashtable mapping = null;
   private boolean initialized = false;

   private String tableNamePrefix = "XB"; // stands for "XMLBLASTER", it is chosen short for Postgres max. eval length = 26 chars (timestamp has already 19 chars)
   private String colNamePrefix = "";     // SQLServer does not allow column name 'byteSize' and 'dataId', so we can add a token e.g. XBbyteSize, XBdataId
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
   private int queryTimeout = 0; // wait indefinitely
   private int managerCount = 0;
   private boolean isShutdown = false;
   private boolean enableBatchMode;
   private String configurationIdentifier;
   private boolean cascadeDeleteSupported;
   private boolean nestedBracketsSupported;

   private final int MIN_POOL_SIZE = 1;

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
      log.info("Timeout, trying DB reconnect, current index: " + this.connections.size() + ", waiting for reentrant connections: " + this.waitingForReentrantConnections);

      synchronized (this) {
         if (this.waitingForReentrantConnections) {
            if (this.connections.size() == this.capacity)
               this.waitingForReentrantConnections = false;
            else {
               // respan the timer ...
               this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
               return; // wait until all connections have been given back to the pool ...
               // in case a connection is blocking this could be blocking everything: find a better way to 
               // throw away the blocking connection and avoid to get it back later.
            }
         }
         // try a connection ...
         try {
            if (log.isLoggable(Level.FINE)) log.fine("timeout:retrying to establish connections");
            // initializing and establishing of connections to DB but first clearing the connections ...
            connect(false);
         }
         catch (Throwable ex) {
            // clean up the connections which might have been established
            //for (int i = 0; i < this.connections.size(); i++) disconnect(i);
            // respan the timer ...
            disconnect(-1L, true);
            this.glob.getJdbcConnectionPoolTimer().addTimeoutListener(this, this.reconnectionTimeout, null);
         }
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
   private Connection get(long delay) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("get invoked");
      Connection ret = null;
      if (this.connections.size() > this.capacity)
         throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_UNKNOWN, ME, "get: Inconsistency in connection index: a negative one is not possible: '" + this.connections.size() + "'");
   
      if (log.isLoggable(Level.FINE)) log.fine("going to retreive a connection");
      try  {
         ret = (Connection)this.connections.poll(delay);
      }
      catch (InterruptedException ex) {
         log.warning("the waiting for a connection was interrupted: " + ex.getMessage());
      }
      if (log.isLoggable(Level.FINE)) log.fine("retreived the connection");
      if (ret == null)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "get: a timeout occured when waiting for a free DB connection. Either the timeout is too short or other connections are blocking");
      return ret;             
   }


   private boolean put(Connection conn) {
      if (log.isLoggable(Level.FINER)) log.finer("put invoked");
      if (conn == null) return false;
      if (log.isLoggable(Level.FINE)) {
         try {
            if (!conn.getAutoCommit()) log.severe("put: error: the connection has not properly been reset: autocommit is 'false'");
         }
         catch (Throwable ex) {
            log.severe("put:  checking for autocommit. reason: " + ex.toString());
            ex.printStackTrace();
         }
      }
      try {
         return this.connections.offer(conn, 5L);
      }
      catch (InterruptedException ex) {
         log.warning("put: an interruption occured: " + ex.getMessage());
         boolean ret = false;
         // we do this loop since a CTRL-C could cause an interrupted exception 
         // and thereby cause the loss of one entry in the connection pool.
         for (int i=0; i < 3; i++) {
            try {
               ret = this.connections.offer(conn, 5L);
               break;
            }
            catch (InterruptedException e) {
               log.warning("put: an interruption occured #" + i + " : " + e.getMessage());
            }
         }
         return ret;
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
      //this.connections = new BoundedLinkedQueue();
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
         for (int i = 0; i < this.capacity; i++) {
            if (log.isLoggable(Level.FINE)) log.fine("initializing DB connection "+ i + " url=" + url + " user=" + user); // + " password=" + password);
            //Logging since JDK 1.3:
            //java.io.OutputStream buf = new java.io.ByteArrayOutputStream();
            //java.io.PrintStream pr = new java.io.PrintStream(buf);
            //DriverManager.setLogStream(pr);
            try {
               this.connections.put(DriverManager.getConnection(url, user, password));
            //   log.info(ME, "DriverManager:" + buf.toString());
            }
            catch (InterruptedException e) {
               log.severe("connect: an interrupted exception occured " + e.getMessage());
            }
            if (log.isLoggable(Level.FINE)) log.fine("initialized DB connection "+ i + " success");
         }
         oldStatus = this.status;
         this.status = I_StorageProblemListener.AVAILABLE;
         lst = this.storageProblemListener;
      }
      this.isShutdown = false;
      if (lst != null) lst.storageAvailable(oldStatus);
      log.info("Successfully reconnected to database");
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
    * <li>prefix.driverName DEFAULTS TO "org.postgresql.Driver"</li> <li>prefix.connectionPoolSize "DEFAULTS TO 2"</li>
    * <li>prefix.url DEFAULTS TO "jdbc:postgresql://localhost/test"</li> <li>prefix.user DEFAULTS TO "postgres"</li>
    * <li>prefix.password  DEFAULTS TO ""</li> </ul>
    */
   public synchronized void initialize(Global glob, Properties pluginProperties)
      throws ClassNotFoundException, SQLException, XmlBlasterException {

      if (this.initialized) return;
      this.glob = glob;
      this.pluginProp = pluginProperties;

      if (log.isLoggable(Level.FINE)) log.fine("initialize");

      // could these also be part of the properties specific to the invoking plugin ?
      org.xmlBlaster.util.property.Property prop = glob.getProperty();
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

      // TODO:
      //
      // Instance settings:
      // "queue/history/maxEntries" -> "queue=history, maxEntries=5"
      //
      // Class settings:
      // "plugin/QueuePlugin[JDBC][1.0]/className/org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin"
      // "plugin/QueuePlugin[JDBC][1.0]/tableNamePrefix/XB_"

      // the old generic properties (for the defaults) outside the plugin 
      this.url = prop.get("queue.persistent.url", "jdbc:postgresql://localhost/test");
      this.user = prop.get("queue.persistent.user", "postgres");
      this.password = prop.get("queue.persistent.password", "");
      this.capacity = prop.get("queue.persistent.connectionPoolSize", MIN_POOL_SIZE);
      if (this.capacity < MIN_POOL_SIZE) {
         log.warning("queue.persistent.connectionPoolSize=" + this.capacity + " is too small, setting it to " + MIN_POOL_SIZE);
         this.capacity = MIN_POOL_SIZE;
      }
      this.connectionBusyTimeout = prop.get("queue.persistent.connectionBusyTimeout", 60000L);
      this.maxWaitingThreads = prop.get("queue.persistent.maxWaitingThreads", 200);
      // these should be handled by the JdbcManager
      this.tableAllocationIncrement = prop.get("queue.persistent.tableAllocationIncrement", 10);
      this.tableNamePrefix = prop.get("queue.persistent.tableNamePrefix", "XB").toUpperCase(); // XB stands for XMLBLASTER
      this.colNamePrefix = prop.get("queue.persistent.colNamePrefix", "").toUpperCase();

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
         log.warning("the 'connectionPoolSize' plugin-property is not parseable: '" + help + "' will be using the default '" + this.capacity + "'");
      }
      if (this.capacity < MIN_POOL_SIZE) {
         log.warning("connectionPoolSize=" + this.capacity + " is too small, setting it to " + MIN_POOL_SIZE);
         this.capacity = MIN_POOL_SIZE;
      }

      help = pluginProp.getProperty("connectionBusyTimeout", "" + this.connectionBusyTimeout);
      try {
         this.connectionBusyTimeout = Long.parseLong(help);
      }
      catch (Exception ex) {
         log.warning("the 'connectionBusyTimeout' plugin-property is not parseable: '" + help + "' will be using the default '" + this.connectionBusyTimeout + "'");
      }

      help = pluginProp.getProperty("maxWaitingThreads", "" + this.maxWaitingThreads);
      try {
         this.maxWaitingThreads = Integer.parseInt(help);
      }
      catch (Exception ex) {
         log.warning("the 'maxWaitingThreads' plugin-property is not parseable: '" + help + "' will be using the default '" + this.maxWaitingThreads + "'");
      }

      help = pluginProp.getProperty("enableBatchMode", "true");
      try {
         this.enableBatchMode = Boolean.getBoolean(help);
      }
      catch (Exception ex) {
         log.warning("the 'enableBatchMode' plugin-property is not parseable: '" + help + "' will be using the default '" + this.enableBatchMode + "'");
      }

      // these should be handled by the JdbcManager
      help = pluginProp.getProperty("tableAllocationIncrement", "" + this.tableAllocationIncrement);
      try {
         this.tableAllocationIncrement = Integer.parseInt(help);
      }
      catch (Exception ex) {
         log.warning("the 'tableAllocationIncrement' plugin-property is not parseable: '" + help + "' will be using the default '" + this.tableAllocationIncrement + "'");
      }

      this.tableNamePrefix = pluginProp.getProperty("tableNamePrefix", this.tableNamePrefix).trim().toUpperCase();
      this.colNamePrefix = pluginProp.getProperty("colNamePrefix", this.colNamePrefix).trim().toUpperCase();

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
         log.warning("the 'cascadeDeleteSupported' plugin-property is not parseable: '" + help + "' will be using the default '" + this.cascadeDeleteSupported + "'");
      }

      help = pluginProp.getProperty("nestedBracketsSupported", "true");
      try {
         this.nestedBracketsSupported = Boolean.getBoolean(help);
      }
      catch (Exception ex) {
         log.warning("the 'nestedBracketsSupported' plugin-property is not parseable: '" + help + "' will be using the default '" + this.nestedBracketsSupported + "'");
      }

      if (log.isLoggable(Level.FINEST)) {
         log.finest("initialize -url                    : " + this.url);
         log.finest("initialize -user                   : " + this.user);
         log.finest("initialize -password               : " + this.password);
         log.finest("initialize -max number of conn     : " + this.capacity);
         log.finest("initialize -conn busy timeout      : " + this.connectionBusyTimeout);
         log.finest("initialize -driver list            : " + xmlBlasterJdbc);
         log.finest("initialize -max. waiting Threads   :" + this.maxWaitingThreads);
         log.finest("initialize -tableNamePrefix        :" + this.tableNamePrefix);
         log.finest("initialize -colNamePrefix          :" + this.colNamePrefix);
         log.finest("initialize -dbAdmin                :" + this.dbAdmin);
         log.finest("initialize -cascadeDeleteSupported :" + this.cascadeDeleteSupported);
         log.finest("initialize -nestedBracketsSupported:" + this.nestedBracketsSupported);
         if (this.configurationIdentifier != null) 
            log.finest("initialize -configurationIdentifier:" + this.configurationIdentifier);
      }

      // could block quite a long time if the number of connections is big
      // or if the connection to the DB is slow.
      this.connections = new BoundedLinkedQueue();
      this.connections.setCapacity(this.capacity);
      try {
         // initializing and establishing of connections to DB (but first disconnect if already connected)
         connect(true);

         parseMapping(prop);
         if (log.isLoggable(Level.FINEST)) dumpMetaData();
         this.initialized = true;
      }
      catch (SQLException ex) {
         if (firstConnectError) {
            firstConnectError = false;
            log.severe(" connecting to DB, error code : '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
            log.info("diagnostics: initialize -url                 : '" + url + "'");
            log.info("diagnostics: initialize -user                : '" + user + "'");
            log.info("diagnostics: initialize -password            : '" + password + "'");
            log.info("diagnostics: initialize -max number of conn  : '" + this.capacity + "'");
            log.info("diagnostics: initialize -conn busy timeout   : '" + this.connectionBusyTimeout + "'");
            log.info("diagnostics: initialize -driver list         : '" + xmlBlasterJdbc + "'");
            log.info("diagnostics: initialize -max. waiting Threads: '" + this.maxWaitingThreads + "'");
            log.finest("diagnostics: initialize -tableNamePrefix     :" + this.tableNamePrefix);
            log.finest("diagnostics: initialize -colNamePrefix       :" + this.colNamePrefix);
            ex.printStackTrace();
         }
         else {
            if (log.isLoggable(Level.FINE)) log.fine(" connecting to DB, error code: '" + ex.getErrorCode() + " : " + ex.getMessage() + "' DB configuration details follow (check if the DB is running)");
         }

         // clean up the connections which might have been established
         // even if it probably won't help that much ...
         disconnect(-1L, false);
         throw ex;
      }
      log.info("Connections to DB '" + url + "' successfully established.");
   }


   /**
    * @return the prefix for the name of the tables to associate to the queues
    */
   public String getTableNamePrefix() {
      return this.tableNamePrefix;
   }


   /**
    * @return the prefix for the name of the columns in each DB table
    */
   public String getColNamePrefix() {
      return this.colNamePrefix;
   }


   /**
    * @return the number of tables to add each time no free tables are available
    *         when creating a new queue.
    */
   public int getTableAllocationIncrement() {
      return this.tableAllocationIncrement;
   }


   /** This method is used in the init method */
   private Hashtable parseMapping(org.xmlBlaster.util.property.Property prop)
         throws XmlBlasterException, SQLException {
      if (log.isLoggable(Level.FINER)) log.finer("parseMapping");
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
            if (log.isLoggable(Level.FINE)) 
               log.fine("parseMapping: the mapping will be done for the keyword (which is here is the DB product name)'" + mappingKey + "'");
         }
         finally {
            if (conn != null) releaseConnection(conn);
         }
      }
      else {
         mappingKey = this.configurationIdentifier;
         if (log.isLoggable(Level.FINE)) 
            log.fine("parseMapping: the mapping will be done for the keyword (name given in the plugin)'" + mappingKey + "'");
      } 

//      String mappingText = prop.get("JdbcDriver." + productName + ".mapping", "");
      String mappingText = prop.get("JdbcDriver.mapping[" + mappingKey + "]", "");
      if (log.isLoggable(Level.FINE)) 
         log.fine("parseMapping: the string to be mapped is '" + mappingText + "'");
      
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
         if (log.isLoggable(Level.FINE)) log.fine("parseMapping: mapping " + key + " to " + value);
         this.mapping.put(key, value);
      }
      if (ex != null) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("parseMapping: Exception occured: " + ex.getMessage());
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

   synchronized private final void disconnect(long waitTime, boolean silent) {
      if (waitTime < 5L) waitTime = 5L;
      Connection conn = null;
      for (int i=0; i < this.connections.size(); i++) {
         try {
            conn = get(waitTime);            
            if (conn == null) break;
            conn.close();
            if (log.isLoggable(Level.FINE)) log.fine("connection " + conn + " disconnected ( object address: " + conn + ")");
         }
         catch (Throwable ex) {
            if (silent) {
               if (log.isLoggable(Level.FINE)) log.fine("could not close connection " + conn + " correctly but resource is set to null. reason " + ex.toString());
            }
            else {
               log.severe("could not close connection " + conn + " correctly but resource is set to null. reason " + ex.toString());
            }
            ex.printStackTrace();
         }
      }
   }

   /**
    * Closes all connections to the Database. It should not throw any
    * exception. Exceptions coming from the backend or the communication
    * layer are catched and logged. When this occurs, the affected connections are set to null.
    */
   synchronized public void disconnect() {
      if (log.isLoggable(Level.FINER)) log.finer("disconnect invoked");
      disconnect(-1L, false);
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
    * Returns a free connection. If no connection is currently available
    * it waits no more than what specified in the configuration. If after that time
    * still no connection is available it throws an XmlBlasterException. It also throws
    * an XmlBlasterException if the number of threads which are waiting is already 
    * too high (also configurable). 
    */
   public Connection getConnection() throws XmlBlasterException {
      if (this.status != I_StorageProblemListener.AVAILABLE) 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "getConnection: Connection Lost. Going in polling modus");
      if (this.waitingCalls >= this.maxWaitingThreads)
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_TOO_MANY_THREADS, ME, "Too many threads waiting for a connection to the DB. Increase the property 'queue.persistent.maxWaitingThreads'");

      synchronized (this) {
         this.waitingCalls++;
      }
      if (log.isLoggable(Level.FINER)) log.finer("getConnection " + this.connections.size() + " waiting calls: " + this.waitingCalls);
      try {
         if (this.isShutdown) connect(false);
         return get(this.connectionBusyTimeout);
      }
      catch (SQLException ex) {
         String additionalMsg = "check system classpath and 'jdbc.drivers' system property\n";
         additionalMsg += "'classpath' is: '" + System.getProperty("classpath", "") + "'\n";
         additionalMsg +=  "'jdbc.drivers' is: '" + System.getProperty("jdbc.drivers", "") + "'\n";
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getConnection()", ex.getMessage() + " " + additionalMsg);
      }
      
      finally {
         synchronized (this) {
            this.waitingCalls--;
         }
      }
   }


   /**
    * Used to give back a connection to the pool. If the pool is already full
    * it will throw an exception.
    */
   public void releaseConnection(Connection conn) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("releaseConnection " + this.connections.size() + " waiting calls: " + this.waitingCalls);
      try {
         SQLWarning warns = conn.getWarnings();
         /*
            while (warns != null) {
               log.warn(ME, "errorCode=" + warns.getErrorCode() + " state=" + warns.getSQLState() + ": " + warns.toString().trim());
               Thread.currentThread().dumpStack();
               warns = warns.getNextWarning();
            }
         */
         if (log.isLoggable(Level.FINE)) {
            while (warns != null) {
               log.fine("errorCode=" + warns.getErrorCode() + " state=" + warns.getSQLState() + ": " + warns.toString().trim());
               warns = warns.getNextWarning();
            }
         }
         conn.clearWarnings(); // free memory
      }
      catch (Throwable e) {
         log.warning("clearWarnings() failed: " + e.toString());
      }
      boolean isOk = put(conn); // if an exception occured it would be better to throw away the connection and make a new one
      if (!isOk) {
         log.severe("the connection pool is already full");
      }

   }


   public void dumpMetaData() {
      Connection conn = null;
      try {
         if (this.isShutdown) connect(false);
         conn = getConnection();
         DatabaseMetaData metaData = conn.getMetaData();

         log.info("--------------- DUMP OF METADATA FOR THE DB START---------------------");
         String driverName = metaData.getDriverName();
         String productName = metaData.getDatabaseProductName();
         log.info("Driver name          :'" + driverName +"', product name: '" + productName + "'");
         log.info("max binary length    : " + metaData.getMaxBinaryLiteralLength());
         log.info("max char lit. length : " + metaData.getMaxCharLiteralLength());
         log.info("max column length    : " + metaData.getMaxColumnNameLength());
         log.info("max cols. in table   : " + metaData.getMaxColumnsInTable());
         log.info("max connections      : " + metaData.getMaxConnections());
         log.info("max statement length : " + metaData.getMaxStatementLength());
         log.info("max nr. of statements: " + metaData.getMaxStatements());
         log.info("max tablename length : " + metaData.getMaxTableNameLength());
         log.info("url                  : " + metaData.getURL());
         log.info("support for trans.   : " + metaData.supportsTransactions());
         log.info("--------------- DUMP OF METADATA FOR THE DB END  ---------------------");

      }
      catch (XmlBlasterException ex) {
         log.severe("dumpMetaData: exception: " + ex.getMessage());
         ex.printStackTrace();
      }
      catch (SQLException ex) {
         log.severe("dumpMetaData: SQL exception: " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         try {
            if (conn != null) releaseConnection(conn);
         }
         catch (XmlBlasterException ex) {
            log.severe("dumpMetaData:  releasing the connection: " + ex.getMessage());
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
      Connection conn = null;
      try {
         Global glob = Global.instance();
         glob.init(args);

         log.info("starting the application: The Tests start here ");

         String prefix = "cb.queue.persistent";
         org.xmlBlaster.util.property.Property prop = glob.getProperty();
         String xmlBlasterJdbc = prop.get("JdbcDriver.drivers", "org.postgresql.Driver");
         System.setProperty("jdbc.drivers", xmlBlasterJdbc);
         String url = prop.get(prefix + ".url", "");
         String user = prop.get(prefix + ".user", "postgres");
         String password = prop.get(prefix + ".password", "");

         if (log.isLoggable(Level.FINEST)) {
            log.finest("initialize -prefix is           : " + prefix);
            log.finest("initialize -url                 : " + url);
            log.finest("initialize -user                : " + user);
            log.finest("initialize -password            : " + password);
            log.finest("initialize -driver list         : " + xmlBlasterJdbc);
         }

         log.info("establishing the connection");
         conn = DriverManager.getConnection(url, user, password);
         log.info("connection established. Sleeping 1 second");
         Thread.sleep(1000L);
         log.info("finished");
      }
      catch (/*SQL*/Exception ex) {
         if (log !=null)
            log.severe(" connecting to DB, error code: " + " : " + ex.getMessage());
            ex.printStackTrace();
      }
      finally {
         try {
            if (log != null) log.info("closing the connection");
            if (conn != null) {
               conn.close();
            }
            if (log != null) log.info("connection closed");
         }
         catch (Exception ex) {
            ex.printStackTrace();
         }
      }
   }

   public synchronized void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer("shutdown");
      disconnect();
//      this.initialized = false;
      this.isShutdown = true;
   }

   synchronized public void registerManager(JdbcManagerCommonTable manager) {
      if (log.isLoggable(Level.FINER)) log.finer("registerManager, number of managers registered (before registering this one): '" + this.managerCount + "'");
      if (manager == null) return;
      this.managerCount++;
   }

   synchronized public void unregisterManager(JdbcManagerCommonTable manager) {
      if (log.isLoggable(Level.FINER)) log.finer("unregisterManager, number of managers registered (still including this one): '" + this.managerCount + "'");
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


