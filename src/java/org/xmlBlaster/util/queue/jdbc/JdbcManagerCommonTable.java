/*------------------------------------------------------------------------------
Name:      JdbcManagerCommonTable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.Global;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
// import java.sql.BatchUpdateException;

import java.util.Hashtable;

import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;
import java.util.WeakHashMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;


/**
 * Delegate class which takes care of SQL specific stuff for the JdbcQueuePlugin
 * class.
 * <p>
 * One instance of this is created by Global for each StorageId prefix,
 * so one instance for 'history', one for 'cb' etc.
 * </p>
 *
 * The tables needed for each JdbcManagerCommonTable instance are the following:<br />
 * - entriesTableName (defaults to 'ENTRIES')<br />
 * The names of such tables are constituted by the tableNamePrefix (which defaults to 'XB') plus the 
 * entriesTableName
 * 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/queue.jdbc.commontable.html">The queue.jdbc.commontable requirement</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 *
 */
public class JdbcManagerCommonTable implements I_StorageProblemListener, I_StorageProblemNotifier {

   private static final String ME = "JdbcManagerCommonTable";
   private final Global glob;
   private static Logger log = Logger.getLogger(JdbcManagerCommonTable.class.getName());
   private final JdbcConnectionPool pool;
   private final I_EntryFactory factory;
   private final WeakHashMap listener;
   private final static String DUMMY_VALUE = "A";

   private final String tableNamePrefix;
   private final String colNamePrefix;
   // the names to be used
   private String stringTxt = null;
   private String longintTxt = null;
   private String intTxt = null;
   private String blobTxt = null;
   private String booleanTxt = null;

   private int maxStatementLength = 0;
   private boolean isConnected = true;

   private static boolean first = true;
   private String entriesTableName = null;
   private String blobVarName;
   private String byteSizeColName;
   private String dataIdColName;
   private String keyAttr;

  // private final String managerName;
   private final I_Storage storage;

   PreparedStatement pingPrepared = null;

   /**
    * Counts the queues using this manager.
    */
   private int queueCounter = 0;

   /**
    * tells wether the used database supports batch updates or not.
    */
    private boolean supportsBatch = true;

    /** forces the desactivation of batch mode when adding entries */
    private boolean enableBatchMode = true;

    /** Column index into XB_ENTRIES table */
    final static int DATA_ID = 1;
    final static int QUEUE_NAME = 2;
    final static int PRIO = 3;
    final static int TYPE_NAME = 4;
    final static int PERSISTENT = 5;
    final static int SIZE_IN_BYTES = 6;
    final static int BLOB = 7;
    
    private int maxNumStatements;
    
   /**
    * @param storage TODO
    * @param JdbcConnectionPool the pool to be used for the connections to
    *        the database. IMPORTANT: The pool must have been previously
    *        initialized.
    */
   public JdbcManagerCommonTable(JdbcConnectionPool pool, I_EntryFactory factory, String managerName, I_Storage storage)
      throws XmlBlasterException {
   //   this.managerName = managerName;
      this.pool = pool;
      this.glob = this.pool.getGlobal();

      this.storage = storage;
      if (log.isLoggable(Level.FINER)) log.finer("Constructor called");

      this.factory = factory;

      if (!this.pool.isInitialized())
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "constructor: the Connection pool is not properly initialized");

      // get the necessary metadata
      Connection conn = null;
      boolean success = true;
      try {
         conn = this.pool.getConnection();
         this.maxStatementLength = conn.getMetaData().getMaxStatementLength();
         if (this.maxStatementLength < 1) {
            this.maxStatementLength = glob.getProperty().get("queue.persistent.maxStatementLength", 2048);
            if (first) {
               log.info("The maximum SQL statement length is not defined in JDBC meta data, we set it to " + this.maxStatementLength);
               first = false;
            }
         }

         if (!conn.getMetaData().supportsTransactions()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            log.severe("the database '" + dbName + "' does not support transactions, unpredicted results may happen");
         }

         if (!conn.getMetaData().supportsBatchUpdates()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            this.supportsBatch = false;
            log.fine("the database '" + dbName + "' does not support batch mode. No problem I will work whitout it");
         }
         
         // zero means not limit (to be sure we also check negative Values
         int defaultMaxNumStatements = conn.getMetaData().getMaxStatements();
         if (defaultMaxNumStatements < 1) {
            log.warning("The maxStatements returned fromt the database metadata is '" + defaultMaxNumStatements + "', will set the default to 50 unless you explicitly set 'maxNumStatements'");
            defaultMaxNumStatements = 50;
         }
         this.maxNumStatements = this.pool.getProp("maxNumStatements", defaultMaxNumStatements);
         log.info("The maximum Number of statements for this database instance are '" + this.maxNumStatements + "'");
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "constructor: failed to get the metadata", ex);
      }
      finally {
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }

      Hashtable names = pool.getMapping();
      this.listener = new WeakHashMap();

      this.stringTxt = (String)names.get("string");
      if (this.stringTxt == null) this.stringTxt = "text";

      this.longintTxt = (String)names.get("longint");
      if (this.longintTxt == null) this.longintTxt = "bigint";

      this.intTxt = (String)names.get("int");
      if (this.intTxt == null) this.intTxt = "integer";

      this.booleanTxt = (String)names.get("boolean");
      if (this.booleanTxt == null) this.booleanTxt = "char(1)";

      this.blobTxt = (String)names.get("blob");
      if (this.blobTxt == null) this.blobTxt = "bytea";

      this.blobVarName = (String)names.get("blobVarName");
      if (this.blobVarName == null) this.blobVarName = "blob";

      this.keyAttr = (String)names.get("keyAttr");
      if (this.keyAttr == null) this.keyAttr = ""; // could be "not null" for MySQL

      this.tableNamePrefix = this.pool.getTableNamePrefix();
      this.colNamePrefix = this.pool.getColNamePrefix();
      // this.queueIncrement = this.pool.getTableAllocationIncrement(); // 2

      this.entriesTableName = this.tableNamePrefix + 
                            pool.getPluginProperties().getProperty("entriesTableName", "ENTRIES");
      this.entriesTableName = this.entriesTableName.toUpperCase();

      // byteSize and dataId are reserved in MS-SQLServer, prefixing other column names are not yet coded
      this.byteSizeColName = this.colNamePrefix + "byteSize";

      this.dataIdColName = this.colNamePrefix + "dataId";
      
      this.enableBatchMode = this.pool.isBatchModeEnabled();

      this.pool.registerManager(this);
   }

   /**
    * pings the jdbc connection to check if the DB is up and running. It returns
    * 'true' if the connection is OK, false otherwise. The ping is done by invocation 
    */
   public boolean ping() {
      Connection conn = null;
      boolean success = true;
      try {
         conn = this.pool.getConnection();
         boolean ret = ping(conn);
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         log.warning("ping failed due to problems with the pool. Check the jdbc pool size in 'xmlBlaster.properties'. Reason :" + ex.getMessage());
         return false;
      }
      finally {
         try {
            if (conn != null) 
               this.pool.releaseConnection(conn, success);
         }
         catch (XmlBlasterException e) {
            log.severe("ping: releaseConnection failed: " + e.getMessage());
         }
      }
   }


   /**
    * pings the jdbc connection to check if the DB is up and running. It returns
    * 'true' if the connection is OK, false otherwise. The ping is done by invocation 
    */
// isClosed() does not work
   private boolean ping(Connection conn) {
      if (log.isLoggable(Level.FINER)) log.finer("ping");
      if (conn == null) return false; // this could occur if it was not possible to create the connection

//      Statement st = null;
      try {
         // conn.isClosed();

         if (log.isLoggable(Level.FINE)) log.fine("Trying ping ...");
         conn.getMetaData().getTables("xyx", "xyz", "xyz", null);

         /*
         if (false) {  // Postgres: 1 millis   Oracle: 2 millis
            if (this.pingPrepared == null) {
               //this.pingPrepared = conn.prepareStatement("SELECT count(nodeid) from " + this.nodesTableName);
               this.pingPrepared = conn.prepareStatement("SELECT nodeid from " + this.nodesTableName + " where nodeid='bla'");
            }
            org.xmlBlaster.util.StopWatch stopWatchToBlob = new org.xmlBlaster.util.StopWatch();
            this.pingPrepared.executeQuery();
            log.info(ME, "ping on Prepared select nodeid elapsed=" + stopWatchToBlob.nice());
         }
         {  // Postgres: 1 millis   Oracle: 4 millis
            org.xmlBlaster.util.StopWatch stopWatchToBlob = new org.xmlBlaster.util.StopWatch();
            Statement st = null;
            st = conn.createStatement();
            st.setQueryTimeout(this.pool.getQueryTimeout());
            st.execute("SELECT nodeid from " + this.nodesTableName + " where nodeid='bla'");// + this.tablesTxt);
            log.info(ME, "ping on select nodeid elapsed=" + stopWatchToBlob.nice());
         }
         {  // Postgres: 6 millis    Oracle: 9 millis
            org.xmlBlaster.util.StopWatch stopWatchToBlob = new org.xmlBlaster.util.StopWatch();
            ResultSet rs = conn.getMetaData().getTables("xyx", "xyz", "xyz", null);
            log.info(ME, "ping xy elapsed=" + stopWatchToBlob.nice());
         }
         {  // Postgres: 14 millis   Oracle: 2 sec 527
            org.xmlBlaster.util.StopWatch stopWatchToBlob = new org.xmlBlaster.util.StopWatch();
            conn.getMetaData().getTables(null, null, null, null);
            log.info(ME, "ping null elapsed=" + stopWatchToBlob.nice());
         }
         */
         if (log.isLoggable(Level.FINE)) log.fine("ping successful");
         return true;
      }
      catch (Throwable ex) {
         if (log.isLoggable(Level.FINE)) log.fine("ping to DB failed. DB may be down. Reason " + ex.toString());
         return false;
      }
/*
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable e) {
            log.warn(ME, "ping exception when closing the statement " + e.toString());
         }
      }
*/
   }

  /**
   * Adds (registers) a listener for connection/disconnection events.
   */
   public boolean registerStorageProblemListener(I_StorageProblemListener entry) {
      synchronized (this.listener) {
         this.listener.put(entry, DUMMY_VALUE);  // use DUMMY_VALUE to support check in unregisterListener()
      }
      return true;
   }

  /**
   * Adds (registers) a listener for connection/disconnection events.
   * @return boolean true if the entry was successfully removed.
   */
   public boolean unRegisterStorageProblemListener(I_StorageProblemListener entry) {
      if (entry == null) return false;
      synchronized (this.listener) {
         return this.listener.remove(entry) != null;
      }
   }

   /**
    * @see I_StorageProblemListener#storageUnavailable(int)
    */
   public void storageUnavailable(int oldStatus) {
      if (log.isLoggable(Level.FINER)) log.finer("storageUnavailable (old status '" + oldStatus + "')");
      this.isConnected = false;

      I_StorageProblemListener[] listenerArr = getStorageProblemListenerArr();
      for(int i=0; i<listenerArr.length; i++) {
         if (this.isConnected == true) break;
         I_StorageProblemListener singleListener = listenerArr[i];
         singleListener.storageUnavailable(oldStatus);
      }
   }

   /**
    * @see I_StorageProblemListener#storageAvailable(int)
    */
   public void storageAvailable(int oldStatus) {
      if (log.isLoggable(Level.FINER)) log.finer("storageAvailable (old status '" + oldStatus + "')");
      this.isConnected = true;
      //change this once this class implements I_StorageProblemNotifier
      if (oldStatus == I_StorageProblemListener.UNDEF) return;
      I_StorageProblemListener[] listenerArr = getStorageProblemListenerArr();
      for(int i=0; i<listenerArr.length; i++) {
         if (this.isConnected == false) break;
         I_StorageProblemListener singleListener = listenerArr[i];
         singleListener.storageAvailable(oldStatus);
      }
   }

   /**
    * @return A current snapshot of the connection listeners where we can work on (unsynchronized) and remove
    * listeners without danger
    */
   public I_StorageProblemListener[] getStorageProblemListenerArr() {
      synchronized (this.listener) {
         return (I_StorageProblemListener[])this.listener.keySet().toArray(new I_StorageProblemListener[this.listener.size()]);
      }
   }

   /**
    * @see #checkIfDBLoss(Connection, String, Throwable, String)
    */
   protected final boolean checkIfDBLoss(Connection conn, String location, Throwable ex) {
      return checkIfDBLoss(conn, location, ex, null);
   }

   /**
    * Handles the SQLException. 
    * If it is a communication exception like the
    * connection has been broken it will inform the connection pool.
    * @param location where the exception occured.
    * @param ex the exception which has to be handled.
    * @param trace additional information to put in the logetLogId(tableName, storageId, "getEntries") logging trace.
    * @return boolean true if it was a communication exception
    * 
    */
   protected final boolean checkIfDBLoss(Connection conn, String location, Throwable ex, String trace) {
      boolean ret = false;

      if (conn != null) ret = !ping(conn);
      else ret = !ping();

      if (ret) {
         log.severe(location + ": the connection to the DB has been lost. Going in polling modus");
         this.pool.setConnectionLost();
      }
      return ret;
   }

   /**
    * Gets the names of all the tables used by XmlBlaster. 
    * This information is retrieved via the database's metadata.
    * @param conn the connection on which to retrieve the metadata.
    * @return HashSet the set containing all the existing tablenames.
    */
   synchronized private HashSet getXbTableNames(Connection conn) throws SQLException {
      String[] types = { "TABLE" };
      ResultSet rs = conn.getMetaData().getTables(null, null, null, types);
      HashSet ret = new HashSet();
      while (rs.next()) { // retrieve the result set ...
         String table = rs.getString(3).toUpperCase();
         // if (table.startsWith(this.tablePrefix))
         // we currently add everything since I don't know what's better: speed here or when searching
         if (log.isLoggable(Level.FINE)) log.fine("getXbTableNames found table '" + table + "': adding to the set of found tables"); 
         ret.add(table);
      }
      return ret;
   }


   /**
    * Checks if all necessary tables exist. 
    * If a table does not exist and 'createTables' true, then the 
    * table is created.
    * @return boolean 'true' if the tables are all there after the invocation to this method, 'false' if at
    *         least one of the required tables is missing after the invocation to this method.
    */
   public final boolean tablesCheckAndSetup(boolean createTables)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("tablesCheckAndSetup");

      if (!this.isConnected)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "tablesCheckAndSetup: the DB disconnected. Handling is currently not possible");

      boolean entriesTableExists = false;

      Connection conn = null;
      boolean success = true;
      String req = "retrieving metadata";
      try {
         conn = this.pool.getConnection();
         conn.setAutoCommit(false);
         HashSet set = getXbTableNames(conn);

         if (set.contains(this.entriesTableName.toUpperCase())) entriesTableExists = true;
         if (log.isLoggable(Level.FINE)) log.fine("entries table exists : " + entriesTableExists);

         if (!createTables) return entriesTableExists;

         if (!entriesTableExists) {
            log.info("adding table '" + this.entriesTableName + "' as the 'entries' table");
            req = "CREATE TABLE " + this.entriesTableName.toUpperCase() + 
                  " (" + this.dataIdColName + " " + this.longintTxt + " " + this.keyAttr +
                  //", nodeId " + this.stringTxt + " " + this.keyAttr +
                  ", queueName " + this.stringTxt + " " + this.keyAttr +
                  ", prio " + this.intTxt +
                  ", flag " + this.stringTxt +
                  ", durable " + this.booleanTxt +
                  ", " + this.byteSizeColName + " " + this.longintTxt +
                  ", " + this.blobVarName + " " + this.blobTxt +
                  ", PRIMARY KEY (" + this.dataIdColName + ", queueName)";
            if (this.pool.isCascadeDeleteSuppported()) req  += " ON DELETE CASCADE)";
            else req += ")";
            if (log.isLoggable(Level.FINE)) 
               log.fine("Request: '" + req + "'");
            update(req, conn);
         }
         if (!conn.getAutoCommit()) 
            conn.commit();
         return true;
      }
      catch (XmlBlasterException ex) {
         success = false;
         try {
            if (!conn.getAutoCommit()) 
               conn.rollback();
         }
         catch (Throwable e) {
            log.severe("tablesCheckAndSetup: exception occured when rolling back: " + e.toString());
         }
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         try {
            if (conn != null && !conn.getAutoCommit()) 
               conn.rollback();
         }
         catch (Throwable e) {
            log.severe("tablesCheckAndSetup: exception occured when rolling back: " + e.toString());
         }

         if (checkIfDBLoss(conn, getLogId(null, "tablesCheckAndSetup"), ex, "SQL request giving problems: " + req))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".tablesCheckAndSetup", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".tablesCheckAndSetup", "", ex); 
      }
      finally {
         if (conn != null) {
            try {
               conn.setAutoCommit(true);
            }
            catch (Throwable e) {
               log.severe("tablesCheckAndSetup: exception occured when setting back autocommit flag, reason: " + e.toString());
            }
            this.pool.releaseConnection(conn, success);
         }
      }
   }

   /**
    *
    * modifies a row in the specified queue table
    * @param queueName The name of the queue on which to perform the operation
    * @param entry the object to be stored.
    *
    * @return true on success
    *
    * @throws XmlBlasterException if an error occurred when trying to get a connection or an SQLException
    *         occurred.
    */
   public final boolean modifyEntry(String queueName, I_Entry entry)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("For entry '" + entry.getUniqueId() + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".modifyEntry", " the connection to the DB is unavailable already before trying to add an entry"); 
      }

      Connection conn = null;
      boolean success = true;
      PreparedStatement preStatement = null;
      Statement exStatement = null;
      boolean ret = false;

      long dataId = entry.getUniqueId();
      int prio = entry.getPriority();
      byte[] blob = this.factory.toBlob(entry);
      String typeName = entry.getEmbeddedType();
      boolean persistent = entry.isPersistent();
      long sizeInBytes = entry.getSizeInBytes();
      
      if (log.isLoggable(Level.FINEST))
         log.finest("modification dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + ", byteSize in bytes: " + sizeInBytes);

      try {
         conn = this.pool.getConnection();
         String req = "UPDATE " + this.entriesTableName + " SET prio = ? , flag = ? , durable = ? , " +
                      this.byteSizeColName + " = ? , " + this.blobVarName + " = ? WHERE  " + this.dataIdColName +
                      " = ? AND queueName = ?";

         if (log.isLoggable(Level.FINE)) log.fine(req);
         preStatement = conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());
         preStatement.setInt(1, prio);
         preStatement.setString(2, typeName);
         if (persistent == true) preStatement.setString(3, "T");
         else preStatement.setString(3, "F");
         preStatement.setLong(4, sizeInBytes);
         
         ByteArrayInputStream blob_stream = new ByteArrayInputStream(blob);
         preStatement.setBinaryStream(5, blob_stream, blob.length); //(int)sizeInBytes);
         // preStatement.setBytes(5, blob);

         preStatement.setLong(6, dataId);
         preStatement.setString(7, queueName);

         if (log.isLoggable(Level.FINE)) log.fine(preStatement.toString());

         int num = preStatement.executeUpdate();
         if (log.isLoggable(Level.FINE)) log.fine("Modified " + num + " entries, entryId='" + entry.getUniqueId() + "'");
         ret = true;
      }
      catch (Throwable ex) {
         success = false;
         if (log.isLoggable(Level.FINE)) {
            if (ex instanceof SQLException) {
               log.fine("modifyEntry: sql exception, the sql state: '" + ((SQLException)ex).getSQLState() );
               log.fine("modifyEntry: sql exception, the error code: '" + ((SQLException)ex).getErrorCode() );
            }
            else log.fine("modifyEntry: exception, the error reason: '" + ex.toString());
         }

         try {
            preStatement.close();
            preStatement = null;
         }
         catch (Throwable ex1) {
            log.severe("modifyEntry: Exception when closing the statement: " + ex1.toString());
         }
//         if (!conn.getAutoCommit()) conn.rollback(); // DANGER !!!!!!! NOT SAFE YET 
         log.warning("Could not update entry '" +
                  entry.getClass().getName() + "'-'" +  entry.getLogId() + "-" + entry.getUniqueId() + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(queueName, "modifyEntry"), ex)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".modifyEntry", "", ex); 
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".modifyEntry", "", ex); 
         }
      }
      finally {
         try {
            if (exStatement != null) 
               exStatement.close();
         }
         catch (Throwable ex) {
            log.warning("modifyEntry: throwable when closing the connection: " + ex.toString());
         }
         try {
            if (preStatement != null) 
               preStatement.close();
         }
         catch (Throwable ex) {
            success = false;
            log.warning("modifyEntry: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }
      return ret;
   }


   /**
    *
    * Adds a row to the specified queue table
    * @param queueName The name of the queue on which to perform the operation
    * @param entry the object to be stored.
    *
    * @return true on success false if the entry was already in the table.
    *
    * @throws SQLException if an error other than double entry occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   private final boolean addSingleEntry(String queueName, I_Entry entry, Connection conn)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("For entry '" + entry.getUniqueId() + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addSingleEntry", " the connection to the DB is unavailable already before trying to add an entry"); 
      }

      PreparedStatement preStatement = null;
//      PreparedStatement exStatement = null;
      Statement exStatement = null;
      boolean ret = false;

      long dataId = entry.getUniqueId();
      int prio = entry.getPriority();
      byte[] blob = this.factory.toBlob(entry);
      String typeName = entry.getEmbeddedType();
      boolean persistent = entry.isPersistent();
      long sizeInBytes = entry.getSizeInBytes();

      if (log.isLoggable(Level.FINEST))
         log.finest("addition. dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + ", byteSize in bytes: " + sizeInBytes);
      
      try {
         String req = "INSERT INTO " + this.entriesTableName + " VALUES ( ?, ?, ?, ?, ?, ?, ?)";
         if (log.isLoggable(Level.FINE)) log.fine(req);

         preStatement = conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());
         preStatement.setLong(DATA_ID, dataId);
         preStatement.setString(QUEUE_NAME, queueName);
         preStatement.setInt(PRIO, prio);
         preStatement.setString(TYPE_NAME, typeName);
         if (persistent == true) preStatement.setString(PERSISTENT, "T");
         else preStatement.setString(PERSISTENT, "F");
         preStatement.setLong(SIZE_IN_BYTES, sizeInBytes);
         ByteArrayInputStream blob_stream = new ByteArrayInputStream(blob);
         preStatement.setBinaryStream(BLOB, blob_stream, blob.length); //(int)sizeInBytes);
         // preStatement.setBytes(BLOB, blob);
         if (log.isLoggable(Level.FINE)) log.fine(preStatement.toString());
         int num = preStatement.executeUpdate();
         if (log.isLoggable(Level.FINE)) log.fine("Added " + num + " entries, entryId='" + entry.getUniqueId() + "'");
         ret = true;
      }
      catch (Throwable ex) {
         String originalExceptionStack = Global.getStackTraceAsString(ex);
         String originalExceptionReason = ex.getMessage();
         if (log.isLoggable(Level.FINE)) {
            if (ex instanceof SQLException) {
               log.fine("addEntry: sql exception, the sql state: '" + ((SQLException)ex).getSQLState() );
               log.fine("addEntry: sql exception, the error code: '" + ((SQLException)ex).getErrorCode() );
            }
         }
         try {
            if (preStatement != null) {
               preStatement.close();
               preStatement = null;
            }
         }
         catch (Throwable ex1) {
            log.severe("exception when closing statement: " + ex1.toString());
            ex1.printStackTrace();
         }
//         if (!conn.getAutoCommit()) conn.rollback(); // DANGER !!!!!!! NOT SAFE YET
         log.warning("Could not insert entry '" +
                  entry.getClass().getName() + "'-'" +  entry.getLogId() + "-" + entry.getUniqueId() + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(queueName, "addEntry"), ex)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex); 
         }
         // check if the exception was due to an existing entry. If yes, no exception will be thrown
         try {
            String req = "SELECT count(*) from " + this.entriesTableName + " where (" + this.dataIdColName + "='" + dataId + "')";
            if (log.isLoggable(Level.FINE)) log.fine("addEntry: checking if entry already in db: request='" + req + "'");
            exStatement = conn.createStatement();
//            exStatement.setQueryTimeout(this.pool.getQueryTimeout());
            ResultSet rs = exStatement.executeQuery(req);
            rs.next();         
            int size = rs.getInt(1);
            if (size < 1) throw ex;
         }
         catch (Throwable ex1) {
            String secondException = Global.getStackTraceAsString(ex1);
            log.warning("The exception '" + ex1.getMessage() + "' occured at " + secondException + "'. The original exception was '" + originalExceptionReason + "' at '" + originalExceptionStack);
            
            
            if (log.isLoggable(Level.FINE)) log.fine("addEntry: checking if entry already in db: exception in select: '" + ex.toString() + "'");
            if (checkIfDBLoss(conn, getLogId(queueName, "addEntry"), ex1))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex1); 
            else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntry", "", ex1); 
         }
         ret = false;
      }
      finally {
         try {
            if (exStatement != null) exStatement.close();
         }
         catch (Throwable ex) {
            log.warning("addEntry: throwable when closing the connection: " + ex.toString());
         }
         try {
            if (preStatement != null) preStatement.close();
         }
         catch (Throwable ex) {
            log.warning("addEntry: throwable when closing the connection: " + ex.toString());
         }
      }
      return ret;
   }


   /**
    * Adds a row to the specified queue table
    * @param queueName The name of the queue on which to perform the operation
    * @param entry the object to be stored.
    *
    * @return true on success
    *
    * @throws SQLException if an error occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   public final boolean addEntry(String queueName, I_Entry entry)
      throws XmlBlasterException {
      Connection conn = null;
      boolean success = true;
      try {
         conn = this.pool.getConnection();
         return addSingleEntry(queueName, entry, conn);
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
         
      }
      finally {
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }
   }


   private int[] addEntriesSingleMode(Connection conn, String queueName, I_Entry[] entries)
      throws XmlBlasterException {
      // due to a problem in Postgres (implicit abortion of transaction by exceptions)
      // we can't do everything in the same transaction. That's why we simulate a single 
      // transaction by deleting the processed entries in case of a failure other than
      // double entries exception.
      int i = 0;
      int[] ret = new int[entries.length];
      try {
         if (log.isLoggable(Level.FINE)) log.fine("addEntriesSingleMode adding each entry in single mode since an exception occured when using 'batch mode'");
         for (i=0; i < entries.length; i++) {
            if (addSingleEntry(queueName, entries[i], conn)) ret[i] = 1; 
            else ret[i] = 0;
            if (log.isLoggable(Level.FINE)) log.fine("addEntriesSingleMode adding entry '" + i + "' in single mode succeeded");
         }
         if (!conn.getAutoCommit()) conn.commit();
         return ret;
      }
      catch (XmlBlasterException ex1) {
         // conn.rollback();
         try {
            for (int ii=0; ii < i; ii++) {
               if (ret[ii] > 0) deleteEntry(queueName, entries[ii].getUniqueId()); // this could be collected and done in one shot
            }
         }
         catch (Throwable ex2) {
            log.severe("addEntriesSingleMode exception occured when rolling back (this could generate inconsistencies in the data) : " + ex2.toString());
         }
         throw ex1;
      }
      catch (Throwable ex1) {
         // conn.rollback();
         try {
            for (int ii=0; ii < i; ii++) {
               if (ret[ii] > 0) deleteEntry(queueName, entries[ii].getUniqueId()); // this could be collected and done in one shot
            }
         }
         catch (Throwable ex2) {
            log.severe("addEntriesSingleMode exception occured when rolling back (this could generate inconsistencies in the data) : " + ex2.toString());
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntriesSingleMode", "", ex1); 
      }
   }


   /**
    *
    * Adds several rows to the specified queue table in batch mode to improve performance
    * @param queueName The name of the queue on which to perform the operation
    * @param entries the entries to store
    * @return array of boolean telling which entries where stored and which not.
    *
    * @throws SQLException if an error occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   public int[] addEntries(String queueName, I_Entry[] entries)
      throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine(" for '" + entries.length + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", " the connection to the DB is unavailable already before trying to add entries"); 
      }

      PreparedStatement preStatement = null;
      Connection conn = null;
      boolean success = true;
      try {
         conn = this.pool.getConnection();
         if (!this.supportsBatch || !this.enableBatchMode)
            return addEntriesSingleMode(conn, queueName, entries);
         if (conn.getAutoCommit()) conn.setAutoCommit(false);
         String req = "INSERT INTO " + this.entriesTableName + " VALUES ( ?, ?, ?, ?, ?, ?, ?)";
         if (log.isLoggable(Level.FINE)) log.fine(req);

         preStatement = conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());

         for (int i=0; i < entries.length; i++) {
            I_Entry entry = entries[i];

            long dataId = entry.getUniqueId();
            int prio = entry.getPriority();
            byte[] blob = this.factory.toBlob(entry);
            String typeName = entry.getEmbeddedType();
            boolean persistent = entry.isPersistent();
            long sizeInBytes = entry.getSizeInBytes();
            preStatement.setLong(DATA_ID, dataId);
            preStatement.setString(QUEUE_NAME, queueName);
            preStatement.setInt(PRIO, prio);
            preStatement.setString(TYPE_NAME, typeName);
            if (persistent == true) preStatement.setString(PERSISTENT, "T");
            else preStatement.setString(PERSISTENT, "F");
            preStatement.setLong(SIZE_IN_BYTES, sizeInBytes);
            ByteArrayInputStream blob_stream = new ByteArrayInputStream(blob);
            preStatement.setBinaryStream(BLOB, blob_stream, blob.length); //(int)sizeInBytes);
            //preStatement.setBytes(7, blob);
            if (log.isLoggable(Level.FINE)) log.fine(preStatement.toString());
            preStatement.addBatch();
         }
         int[] ret = preStatement.executeBatch();
         if (!conn.getAutoCommit()) conn.commit();
         return ret;
       }
      catch (Throwable ex) {
         success = false;
         try {
            if (!conn.getAutoCommit()) {
               conn.rollback(); // rollback the original request ...
               conn.setAutoCommit(true); // since if an exeption occurs it infects future queries within the same transaction
            }
         }
         catch (Throwable ex1) {
            log.severe("error occured when trying to rollback after exception: reason: " + ex1.toString() + " original reason:" + ex.toString());
            ex.printStackTrace(); // original stack trace
         }
         if (log.isLoggable(Level.FINE)) log.fine("Could not insert entries: " + ex.toString());
         if ((!this.supportsBatch || !this.enableBatchMode) ||
            checkIfDBLoss(conn, getLogId(queueName, "addEntries"), ex)) 
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", "", ex); 
         else { // check if the exception was due to an already existing entry by re
            return addEntriesSingleMode(conn, queueName, entries);
         }
      }
      finally {
         try {
            if (preStatement != null) 
               preStatement.close();
         }
         catch (Throwable ex) {
            success = false;
            log.warning("addEntries: throwable when closing the statement: " + ex.toString());
         }
         try {
            if (conn != null) {
               if (!conn.getAutoCommit()) 
                  conn.setAutoCommit(true);
            }
         }
         catch (Throwable ex) {
            success = false;
            log.warning("addEntries: throwable when closing the connection: " + ex.toString());
         }

         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }
   }

   /**
    * Cleans up the specified queue. It deletes all queue entries in the 'entries' table.
    * @return the number of queues deleted (not the number of entries).
    */
   public final int cleanUp(String queueName) throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return 0;
      }

      try {
         String req = "delete from " + this.entriesTableName + " where queueName='" + queueName + "'";
         if (log.isLoggable(Level.FINE))
            log.fine(" request is '" + req + "'");
         int num = update(req);
         return (num > 0) ? 1 : 0;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         Connection conn = null;
         boolean success = true;
         try {
            conn = this.pool.getConnection();
            if (checkIfDBLoss(conn, getLogId(queueName, "deleteEntries"), ex))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex); 
         }
         catch (XmlBlasterException e) {
            success = false;
            throw e;
         }
         finally {
            if (conn != null) 
               this.pool.releaseConnection(conn, success);
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex);
      }
   }


   /**
    * Wipes out the entire DB. 
    * i.e. it deletes the three tables from the DB. IMPORTANT: 
    * @param doSetupNewTables if set to 'true' it recreates the necessary empty tables.
    */
   public int wipeOutDB(boolean doSetupNewTables) throws XmlBlasterException {
      // retrieve all tables to delete
      if (log.isLoggable(Level.FINER))  log.finer("wipeOutDB");

      int count = 0;
      Connection conn = null;
      boolean success = true;
      try {
        try {
           String req = "DROP TABLE " + this.entriesTableName;
            if (log.isLoggable(Level.FINER))  log.finer("wipeOutDB " + req + " will be invoked on the DB");
            conn = this.pool.getConnection();
            if (conn.getAutoCommit()) conn.setAutoCommit(false);
            this.update(req, conn);
            count++;
         }
         catch (SQLException ex) {
            success = false;
            if (checkIfDBLoss(conn, getLogId(null, "wipeOutDB"), ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(null, "wipeOutDB"), "SQLException when wiping out DB", ex);
            else {
               log.warning("Exception occurred when trying to drop the table '" + this.entriesTableName + "', it probably is already dropped. Reason: " + ex.toString());
            }
         }

         if (!conn.getAutoCommit()) 
            conn.commit();
      }
      catch (Throwable ex) {
         success = false;
         try {
            if (conn != null) 
               conn.rollback();
         }
         catch (Throwable ex1) {
            success = false;
            log.severe("wipeOutDB: exception occurred when rolling back: " + ex1.toString());
         }
         if (ex instanceof XmlBlasterException) throw (XmlBlasterException)ex;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, getLogId(null, "wipeOutDB"), "wipeOutDB: exception ", ex);
      }
      finally {
         try {
            if (conn != null) {
               if (!conn.getAutoCommit()) 
                  conn.setAutoCommit(true);
            }
         }
         catch (Throwable ex) {
            success = false;
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, getLogId(null, "wipeOutDB"), "wipeOutDB: exception when closing the query", ex);
         }
         finally {
            if (conn != null) 
               this.pool.releaseConnection(conn, success);
         }
      }

      try {
         if (doSetupNewTables) setUp();
      }
      catch (Throwable ex) {
         log.severe("SQLException occured when cleaning up the table. Reason " + ex.toString()); 
      }
      return count;
   }


   /**
    * Dumps the metadata to the log object. The log will write out this
    * information as an info. This means you don't need to have the switch
    * 'dump' set to true to see this information.
    */
   public void dumpMetaData() {
      this.pool.dumpMetaData();
   }


   /**
    * returns the amount of bytes currently in the specified queue
    * TODO: Replace all four selects with one:
    *  "select count(*), sum(byteSize), durable from XB_ENTRIES WHERE queueName='history_xmlBlaster_192_168_1_4_3412Hello' GROUP BY durable;"
    * @param tableName the name of the table in which to count
    * @return the current amount of bytes used in the table.
    */
   public long getNumOfBytes(String queueName)
      throws XmlBlasterException {

      if (!this.isConnected)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(queueName, "getNumOfBytes"), "The DB is disconnected. Handling queue '" + queueName + "' is currently not possible");
      Connection conn = null;
      boolean success = true;
      PreparedStatement st = null;

      try {
         String req = "SELECT sum(" + this.byteSizeColName + ") from " + this.entriesTableName + " where queueName='" + queueName + "'";
         conn = this.pool.getConnection();
         st = conn.prepareStatement(req);
         st.setQueryTimeout(this.pool.getQueryTimeout());
         ResultSet rs = st.executeQuery();
         rs.next();
         return rs.getLong(1);
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         try {
            if (st != null) st.close();
            st = null;
         }
         catch (Throwable ex1) {
            log.warning(".getNumOfBytes: exception when closing statement: " + ex1.toString());
         }
         if (checkIfDBLoss(conn, getLogId(queueName, "getNumOfBytes"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfBytes", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfBytes", "", ex); 
      }
      finally {
         try {
            if (st != null) 
               st.close();
         }
         catch (Throwable ex) {
            success = false;
            log.warning(".getNumOfBytes: exception when closing statement: " + ex.toString());
         }
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }
   }


   /**
    * Sets up a table for the queue specified by this queue name.
    * If one already exists (i.e. if it recovers from a crash) its associated
    * table name is returned.
    */
   public final void setUp() throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("setUp");
      if (log.isLoggable(Level.FINE)) log.fine("Initializing the first time the pool");
      tablesCheckAndSetup(this.pool.isDbAdmin());
   }

   private final ArrayList processResultSet(ResultSet rs, StorageId storageId,
                             int numOfEntries, long numOfBytes, boolean onlyId,
                             I_EntryFilter entryFilter)
      throws SQLException, XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      ArrayList entries = new ArrayList();
      int count = 0;
      long amount = 0L;

      while ( (rs.next()) && ((count < numOfEntries) || (numOfEntries < 0)) &&
         ((amount < numOfBytes) || (numOfBytes < 0))) {

         if(onlyId) {
            entries.add(new Long(rs.getLong(1)));
         }
         else {
            long dataId = rs.getLong(DATA_ID);            // preStatement.setLong(1, dataId);
            String queueName = rs.getString(QUEUE_NAME); // preStatement.setString(3, queueName);
            int prio = rs.getInt(PRIO);                // preStatement.setInt(4, prio);
            String typeName = rs.getString(TYPE_NAME);      // preStatement.setString(5, typeName);
            if (typeName != null) 
               typeName = typeName.trim();
            //this only to make ORACLE happy since it does not support BOOLEAN
            String persistentAsChar = rs.getString(PERSISTENT);
            if (persistentAsChar != null) 
               persistentAsChar = persistentAsChar.trim();
            boolean persistent = false;
            if ("T".equalsIgnoreCase(persistentAsChar)) persistent = true;

            long sizeInBytes = rs.getLong(SIZE_IN_BYTES);
            InputStream is = rs.getBinaryStream(BLOB);
            // byte[] blob = rs.getBytes(7); // preStatement.setObject(5, blob);
            if (storageId == null)
               storageId = StorageId.valueOf(queueName);
            if (is == null) {
               String txt = "dataId='" + dataId + "' prio='" + prio + "' typeName='" + typeName + "' persistent='" + persistent + "' sizeInBytes='" + sizeInBytes + "'";
               log.warning("The stream for the blob of data: " + txt + " is null");
            }
            if ( (numOfBytes < 0) || (sizeInBytes+amount < numOfBytes) || (count == 0)) {
               if (log.isLoggable(Level.FINEST))
                  log.finest("processResultSet: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " persistent: " + persistent);
//               entries.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, blob, storageId));
               I_Entry entry = this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, is, storageId);
               if (entryFilter != null)
                  entry = entryFilter.intercept(entry, this.storage);
               entries.add(entry);
               amount += sizeInBytes;
            }
         }
         count++;
      }
      return entries;
   }


   /**
    * It accepts result sets with (long dataId, long size)
    * @param numOfBytes as input is the maximum number of bytes to process. As
    *        output it stores the number of bytes processed.
    * @param numOfEntries the maximum number of entries to process
    *
    */
   private final ReturnDataHolder processResultSetForDeleting(ResultSet rs, int numOfEntries, long numOfBytes)
      throws SQLException, XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("processResultSetForDeleting invoked");
      ReturnDataHolder ret = new ReturnDataHolder();
      long currentAmount = 0L;
      while ( (rs.next()) && ((ret.countEntries < numOfEntries) || (numOfEntries < 0)) &&
         ((ret.countBytes < numOfBytes) || (numOfBytes < 0))) {
         currentAmount = rs.getLong(2);
         if ( (numOfBytes < 0) || (ret.countBytes+currentAmount < numOfBytes) || (ret.countEntries == 0)) {
            ret.list.add(new Long(rs.getLong(1)));
            ret.countBytes += currentAmount;
            ret.countEntries++;
         }
      }
      return ret;
   }


   /**
    * Returns the pool associated to this object.
    * @return JdbcConnectionPool the pool managing the connections to the DB
    */
   public JdbcConnectionPool getPool() {
      return this.pool;
   }

   /**
    * A generic invocation for a simple update on the database. It will use
    * a normal statement (not a prepared statement),
    * @param request the request string
    * @param fetchSize the number of entries to fetch, i.e. to update
    */
   private final int update(String request)
      throws SQLException, XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering request=" + request);

      Connection conn = null;
      boolean success = false;
      Statement  statement = null;
      int ret = 0;

      try {
         conn = pool.getConnection();
         statement = conn.createStatement();
         statement.setQueryTimeout(this.pool.getQueryTimeout());
         ret = statement.executeUpdate(request);
         if (log.isLoggable(Level.FINE)) log.fine("Executed statement '" + request + "', number of changed entries=" + ret);
         success = true;
      }
      finally {
         try {
            if (statement !=null) 
               statement.close();
         }
         catch (Throwable ex) {
            success = false;
            log.warning("update: throwable when closing statement: " + ex.toString());
         }
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }

      return ret;
   }


   /**
    * This version makes an update but does no connection management, i.e. a
    * connection must be established outside this method and passed in the
    * argument list. The closing and cleaning up of the connection must also
    * be handled outside.
    *
    * @param request the string specifying which request to make
    * @param conn the connection to use for this request
    */
   private final int update(String request, Connection conn)
      throws SQLException {

      if (log.isLoggable(Level.FINER)) log.finer("Request=" + request + " and connection " + conn);

      Statement  statement = null;
      int ret = 0;

      try {
         statement = conn.createStatement();
         statement.setQueryTimeout(this.pool.getQueryTimeout());
         ret = statement.executeUpdate(request);
         if (log.isLoggable(Level.FINE)) log.fine("Executed statement '" + request + "', number of changed entries=" + ret);
      }

      finally {
         try {
            if (statement !=null) statement.close();
         }
         catch (Throwable ex) {
            log.warning("update: throwable when closing statement: " + ex.toString());
         }
      }
      return ret;
   }


   /**
    * deletes all transient messages
    */
   public int deleteAllTransient(String queueName) throws XmlBlasterException {
      try {
         if (log.isLoggable(Level.FINER)) 
            log.finer("deleteAllTransient");
         if (!this.isConnected) {
            if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
            return 0;
         }

         String req = "delete from " + this.entriesTableName  + " where queueName='" + queueName + "' AND durable='F'";
         return update(req);
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         Connection conn = null;
         boolean success = false;
         try {
            conn = this.pool.getConnection();
            if (checkIfDBLoss(conn, getLogId(queueName, "deleteAllTransient"), ex))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteAllTransient", "", ex);
            success = true;
         }
         finally {
            if (conn != null) 
               this.pool.releaseConnection(conn, success);
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteAllTransient", "", ex); 
      }
   }

   /**
    * The prefix is the initial part of the SQL update/query. Note that this
    * method can be used both for SELECT statements as for updates such as
    * DELETE or UPDATE.
    * An example of prefix:
    * "delete from tableName where dataId in(";
    */
   private final ArrayList whereInStatement(String reqPrefix, long[] uniqueIds) {
      if (log.isLoggable(Level.FINER)) log.finer("whereInStatement");
      final String reqPostfix = ")";
      boolean isFirst = true;
      int initialLength = reqPrefix.length() + reqPostfix.length() + 2;
      StringBuffer buf = new StringBuffer();
      int length = initialLength;
      int currentLength = 0;

      ArrayList ret = new ArrayList();
      int count = 0;
      for (int i=0; i<uniqueIds.length; i++) {
         String req = null;
         String entryId = Long.toString(uniqueIds[i]);
         currentLength = entryId.length();
         length += currentLength;
         if ((length > this.maxStatementLength) || (i == (uniqueIds.length-1)) || count >= this.maxNumStatements) { // then make the update
            if (i == (uniqueIds.length-1)) {
               if (!isFirst) buf.append(",");
               count++;
               buf.append(entryId);
            }
            req = reqPrefix + buf.toString() + reqPostfix;
            if (count > 0)
               ret.add(req);

            length = initialLength + currentLength;
            buf = new StringBuffer();
            count = 0;
            isFirst = true;
         }
         else
            count++;

         if (!isFirst) {
            buf.append(",");
            length++;
         }
         else 
            isFirst = false;
         count++;
         buf.append(entryId);
      }

      return ret;
   }


   /**
    * Helper method to find out if still to retrieve entries in getAndDeleteLowest or not. 
    */
   private final boolean isInsideRange(int numEntries, int maxNumEntries, long numBytes, long maxNumBytes) {
      if (maxNumEntries < 0) {
         if (maxNumBytes <0L) return true;
         return numBytes < maxNumBytes;
      }
      // then maxNumEntries >= 0
      if (maxNumBytes <0L) return numEntries < maxNumEntries;
      // then the less restrictive of both is used (since none is negative)
      return numEntries < maxNumEntries || numBytes < maxNumBytes;
   }



   /**
    * Under the same transaction it gets and deletes all the entries which fit
    * into the constrains specified in the argument list.
    * The entries are really deleted only if doDelete is true, otherwise they are left untouched on the queue
    * @see org.xmlBlaster.util.queue.I_Queue#takeLowest(int, long, org.xmlBlaster.util.queue.I_QueueEntry, boolean)
    */
   public ReturnDataHolder getAndDeleteLowest(StorageId storageId, int numOfEntries, long numOfBytes,
      int maxPriority, long minUniqueId, boolean leaveOne, boolean doDelete) throws XmlBlasterException {

      String queueName = storageId.getStrippedId();
      if (log.isLoggable(Level.FINER)) log.finer("Entering");
      ReturnDataHolder ret = new ReturnDataHolder();

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return ret;
      }

      PreparedQuery query = null;
      boolean success = true;
      try {
         String req = "select * from " + this.entriesTableName + " WHERE queueName='" + queueName + "' ORDER BY prio ASC, " + this.dataIdColName + " DESC";
         query = new PreparedQuery(pool, req, false, -1);

         // process the result set. Give only back what asked for (and only delete that)
         ResultSet rs = query.rs;

         int count = 0;
         long amount = 0L;
         boolean doContinue = true;
         boolean stillEntriesInQueue = false;

         while ( (stillEntriesInQueue=rs.next()) && doContinue) {
            long dataId = rs.getLong(DATA_ID);                // preStatement.setLong(1, dataId);
            /*String queueName =*/ rs.getString(QUEUE_NAME);  // preStatement.setString(3, queueName);
            int prio = rs.getInt(PRIO);                       // preStatement.setInt(4, prio);
            String typeName = rs.getString(TYPE_NAME);        // preStatement.setString(5, typeName);
            //boolean persistent = rs.getBoolean(PERSISTENT); // preStatement.setBoolean(4, persistent);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String persistentAsChar = rs.getString(PERSISTENT);
            boolean persistent = false;
            if ("T".equalsIgnoreCase(persistentAsChar)) persistent = true;

            long sizeInBytes = rs.getLong(SIZE_IN_BYTES);
            if (!isInsideRange(count, numOfEntries, amount, numOfBytes)) break;
            // byte[] blob = rs.getBytes(7); // preStatement.setObject(5, blob);
            InputStream is = rs.getBinaryStream(BLOB);

            // check if allowed or already outside the range ...
            if ((prio<maxPriority) || ((prio==maxPriority)&&(dataId>minUniqueId)) ) {
               if (log.isLoggable(Level.FINEST)) log.finest("dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " persistent: " + persistent);
//               ret.list.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, blob, storageId));
               ret.list.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, is, storageId));
               amount += sizeInBytes;
            }
            else doContinue = false;
            count++;
         }

         ret.countBytes = amount;
         ret.countEntries = count;

         // prepare for deleting (we don't use deleteEntries since we want
         // to use the same transaction (and the same connection)
         if (leaveOne) {
            // leave at least one entry
            if (stillEntriesInQueue) stillEntriesInQueue = rs.next();
            if ((!stillEntriesInQueue) && (ret.list.size()>0)) {
               ret.countEntries--;
               I_Entry entryToDelete = (I_Entry)ret.list.remove(ret.list.size()-1);
               ret.countBytes -= entryToDelete.getSizeInBytes();
               if (log.isLoggable(Level.FINE)) log.fine("takeLowest size to delete: "  + entryToDelete.getSizeInBytes());
            }
         }

         if (doDelete) {
            //first strip the unique ids:
            long[] uniqueIds = new long[ret.list.size()];
            for (int i=0; i < uniqueIds.length; i++)
               uniqueIds[i] = ((I_Entry)ret.list.get(i)).getUniqueId();
            String reqPrefix = "delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND " + this.dataIdColName + " in(";
            ArrayList reqList = this.whereInStatement(reqPrefix, uniqueIds);
            for (int i=0; i < reqList.size(); i++) {
               req = (String)reqList.get(i);
               if (log.isLoggable(Level.FINE))
                  log.fine("'delete from " + req + "'");
               update(req, query.conn);
            }
         }

         if (!query.conn.getAutoCommit()) query.conn.commit();
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         try {
            if (query != null && query.rs != null) {
               query.rs.close();
               query.rs = null;
            }
         }
         catch (Throwable ex1) {
            log.severe("exception occured when closing query: " + ex1.toString());
         }
         try {
            if (query != null && query.conn != null) query.conn.rollback();
         }
         catch (Throwable ex1) {
            log.severe("could not rollback: " + ex.toString());
            ex1.printStackTrace();
         }

         Connection tmpConn = null;
         if (query != null) {
            tmpConn = query.conn;
            query.closeStatement();
         }
         if (checkIfDBLoss(tmpConn, getLogId(queueName, "getAndDeleteLowest"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getAndDeleteLowest", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getAndDeleteLowest", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * Deletes the entries specified by the entries array. Since all entries
    * are deleted at the same time, in case of an exception the result
    * is uncertain (it depends on what the used database will do). In case the
    * statement would be too long for the DB, the request is split into several 
    * statements which are invoked separately.
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public boolean[] deleteEntries(String queueName, long[] uniqueIds) throws XmlBlasterException {
      if (this.maxNumStatements > 0 && this.maxNumStatements < uniqueIds.length) {
         int rest = uniqueIds.length;
         int offset = 0;
         boolean[] ret = new boolean[rest];
         while (rest > 0) {
            int nmax = this.maxNumStatements;
            if (rest < nmax)
               nmax = rest;
            long[] ids = new long[nmax];
            for (int i=0; i < ids.length; i++)
               ids[i] = uniqueIds[i+offset];
            boolean[] tmpRet = deleteEntriesNoSplit(queueName, ids);
            for (int i=0; i < tmpRet.length; i++) {
               ret[offset + i] = tmpRet[i];
            }
            offset+= tmpRet.length;
            rest -= ids.length;
         }
         return ret;
      }
      else
         return deleteEntriesNoSplit(queueName, uniqueIds);
   }

   private boolean[] deleteEntriesNoSplit(String queueName, long[] uniqueIds)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return new boolean[uniqueIds.length];
      }

      Connection conn = null;
      boolean success = true;
      try {
         int count = 0;
         String reqPrefix = "delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND " + this.dataIdColName + " in(";
         ArrayList reqList = whereInStatement(reqPrefix, uniqueIds);

         conn = pool.getConnection();
         if (conn.getAutoCommit()) conn.setAutoCommit(false);

         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            if (log.isLoggable(Level.FINE))
               log.fine("' " + req + "'");
            count +=  update(req, conn);
         }
         if (count != uniqueIds.length) 
            conn.rollback(); // not all entries have been deleted: 
                             // will be handled individually below (to know which is deleted and which not)
         else {
            if (!conn.getAutoCommit()) conn.commit();
            boolean[] ret = new boolean[uniqueIds.length];
            for (int i=0; i < ret.length; i++) ret[i] = true;
            return ret;
         }
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (checkIfDBLoss(conn, getLogId(queueName, "deleteEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex); 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex); 
      }
      finally {
         if (conn != null) {
            try {
               if (!conn.getAutoCommit()) 
                  conn.setAutoCommit(true);
            }
            catch (Throwable ex) {
               success = false;
               log.severe("error when setting autocommit to 'true'. reason: " + ex.toString());
               ex.printStackTrace();
            }
            this.pool.releaseConnection(conn, success);
         }
      }

      // handle individually because of the above rollback
      boolean[] ret = new boolean[uniqueIds.length];
      for (int i=0; i < uniqueIds.length; i++) {
         ret[i] = deleteEntry(queueName, uniqueIds[i]) == 1;
      }
      return ret;
   }


   /**
    * Deletes the entry specified
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
/*   
   public int[] deleteEntriesBatch(String queueName, long[] uniqueIds)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.call(getLogId(queueName, "deleteEntriesBatch"), "Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.trace(getLogId(queueName, "deleteEntry"), "Currently not possible. No connection to the DB");
         int[] ret = new int[uniqueIds.length];
         for (int i=0; i < ret.length; i++) ret[i] = 0;
         return ret;
      }

      Connection conn = null;
      PreparedStatement st = null;
      try {
         conn =  this.pool.getConnection();
         if (conn.getAutoCommit()) conn.setAutoCommit(false);
         String req = "delete from " + this.entriesTableName + " where queueName=? AND dataId=?";
         st = conn.prepareStatement(req);

         for (int i=0; i < uniqueIds.length; i++) {
            st.setString(1, queueName);
            st.setLong(2, uniqueIds[i]);
            st.addBatch();
         }
         int[] ret = st.executeBatch();
         if (!conn.getAutoCommit()) conn.commit();
         return ret;
      }
      catch (BatchUpdateException ex) {
         try {
            if (!conn.getAutoCommit()) conn.commit();
         }
         catch (Throwable ex1) {
            log.error(ME, "could not commit correclty: " + ex1.toString());
            ex1.printStackTrace();
         }
         return ex.getUpdateCounts();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         try {
            if (!conn.getAutoCommit()) conn.commit();
         }
         catch (Throwable ex1) {
            log.error(ME, "could not rollback correclty: " + ex1.toString());
            ex1.printStackTrace();
         }
         if (checkIfDBLoss(conn, getLogId(queueName, "deleteEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntry", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntry", "", ex); 
      }
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable ex) {
            log.warn(ME, "deleteEntry: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) {
            try {
               if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }
            catch (Throwable ex1) {
               log.error(ME, "could not rollback correclty: " + ex1.toString());
               ex1.printStackTrace();
            }
            this.pool.releaseConnection(conn);
         }
      }
   }
*/

   /**
    * Deletes the entry specified
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public int deleteEntry(String queueName, long uniqueId)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return 0;
      }

      Connection conn = null;
      boolean success = true;
      String req = "delete from " + this.entriesTableName + " where queueName='"+queueName+"' AND " + this.dataIdColName + "="+uniqueId;
      try {
         conn =  this.pool.getConnection();
         return update(req, conn);
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (checkIfDBLoss(conn, getLogId(queueName, "deleteEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntry", req, ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntry", req, ex); 
      }
      finally {
         if (conn != null) 
            this.pool.releaseConnection(conn, success);
      }
   }


   /**
    * Deletes number of entries specified by the argument list. If less than
    * that amount of elements is given, then all entries will be deleted.
    * If you specify -1 as the number of entries, then all entries will be
    * deleted.
    *
    * @param tableName the name of the table on which to act.
    * @param numOfEntries the number of entries to be deleted.
    * @param amount the maximum amount of bytes to remove. Note that if no entries
    *        fit into this size, the first entry is taken anyway (to avoid deadlocks)
    */
   public ReturnDataHolder deleteFirstEntries(String queueName, long numOfEntries, long amount)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      ReturnDataHolder ret = new ReturnDataHolder();
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return ret;
      }

      if (numOfEntries >= Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, getLogId(queueName, "deleteFirstEntries"),
               "The number of entries=" + numOfEntries + " to be deleted is too big for this system");

      PreparedQuery query = null;
      boolean success = true;
      try {
         String req = "select " + this.dataIdColName + "," + this.byteSizeColName + " from " + this.entriesTableName + " WHERE queueName='" + queueName + "' ORDER BY prio DESC, " + this.dataIdColName + " ASC";
         query = new PreparedQuery(pool, req, false, -1);
         // I only want the uniqueId (dataId)
         ret = processResultSetForDeleting(query.rs, (int)numOfEntries, amount);

         int nmax = ret.list.size();
         if (nmax < 1) return ret;

         long[] uniqueIds = new long[nmax];
         for (int i=0; i < nmax; i++) uniqueIds[i] = ((Long)ret.list.get(i)).longValue();

         ArrayList reqList = whereInStatement("delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND " + this.dataIdColName + " in(", uniqueIds);
         ret.countEntries = 0L;

         // everything in the same transaction (just in case)
         for (int i=0; i < reqList.size(); i++) {
            req = (String)reqList.get(i);
            if (log.isLoggable(Level.FINE)) log.fine("'" + req + "'");

            ret.countEntries +=  update(req, query.conn);
         }

         return ret;

      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) 
            query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "deleteFirstEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteFirstEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteFirstEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the first numOfEntries of the queue which have the priority in the
    * range specified by prioMin and prioMax (inclusive).
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param storageId the storageId of the queue/storage from which to retrieve the information.
    * @param numOfEntries the maximum number of elements to retrieve. If negative there is no constriction.
    * @param numOfBytes the maximum number of bytes to retrieve. If negative, there is no constriction.
    * @param minPrio the minimum priority to retreive (inclusive). 
    * @param maxPrio the maximum priority to retrieve (inclusive).
    *
    */
   public ArrayList getEntriesByPriority(StorageId storageId, int numOfEntries,
                             long numOfBytes, int minPrio, int maxPrio, I_EntryFilter entryFilter)
      throws XmlBlasterException {

      String queueName = storageId.getStrippedId();

      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + "' AND prio >= " + minPrio + " and prio <= " + maxPrio +
            " ORDER BY prio DESC, " + this.dataIdColName + " ASC";
                                                                 
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");

      PreparedQuery query =null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false, entryFilter);
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) 
            query.closeStatement();
        if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getEntriesByPriority"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesByPriority", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesByPriority", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the first numOfEntries of the queue which have the same priority.
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    *
    */
   public ArrayList getEntriesBySamePriority( StorageId storageId, int numOfEntries, long numOfBytes)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      // 65 ms (for 10000 msg)
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      PreparedQuery query = null;
      boolean success = true;
      try {
         if (!this.pool.isNestedBracketsSuppported()) {
            int prio = 0;
            req = "SELECT max(prio) from " + this.entriesTableName + " where queueName='" + queueName + "'";
            query = new PreparedQuery(pool, req, numOfEntries);
            query.rs.next();
            prio = query.rs.getInt(1);
            if (log.isLoggable(Level.FINE)) log.fine("Max prio " + new Integer(prio).toString());
            query.close(success);
            query = null;
            req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + 
                  "' and prio=" + prio + " ORDER BY " + this.dataIdColName + " ASC";
         }
         else {
            req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + 
                  "' and prio=(select max(prio) from " + this.entriesTableName + " where queueName='" + queueName + 
                  "')  ORDER BY " + this.dataIdColName + " ASC";
         }
         if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");

         query = new PreparedQuery(pool, req, numOfEntries);
         I_EntryFilter entryFilter = null;
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false, entryFilter);
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) 
            query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getEntriesBySamePriority"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesBySamePriority", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesBySamePriority", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the first numOfEntries of the queue.
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    *
    */
   public ArrayList getEntries(StorageId storageId, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + "' ORDER BY prio DESC, " + this.dataIdColName + " ASC";
      if (log.isLoggable(Level.FINE)) 
         log.fine("Request: '" + req + "' wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
      PreparedQuery query = null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false, entryFilter);
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries. Wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) 
            query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * Raw access to the table. 
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    *
    */
   public ArrayList getEntriesLike(String queueNamePattern, String flag,
                    int numOfEntries, long numOfBytes,
                    I_EntryFilter entryFilter)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");
      StorageId storageId = null;

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + this.entriesTableName + " where queueName like'" + queueNamePattern + "'";
      if (flag != null) req += " and flag='" + flag + "'";
      req += " ORDER BY prio DESC, " + this.dataIdColName + " ASC";
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "' wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false, entryFilter);
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries. Wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
         return ret;
      }
      catch (SQLException ex) {
         if (query != null) query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueNamePattern, "getEntriesLike"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesLike", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesLike", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close(true);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the first numOfEntries of the queue until the limitEntry is reached.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public ArrayList getEntriesWithLimit(StorageId storageId, I_Entry limitEntry)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      int limitPrio = limitEntry.getPriority();
      long limitId = limitEntry.getUniqueId();
      String req = "SELECT * from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND " + this.dataIdColName + " < "  + limitId + ") ) ORDER BY prio DESC, " + this.dataIdColName + " ASC";
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
      PreparedQuery query = null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, -1);
         I_EntryFilter entryFilter = null;
         ArrayList ret = processResultSet(query.rs, storageId, -1, -1L, false, entryFilter);
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getEntriesWithLimit"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesWithLimit", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesWithLimit", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * deletes the first numOfEntries of the queue until the limitEntry is reached.
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public long removeEntriesWithLimit(StorageId storageId, I_Entry limitEntry, boolean inclusive)
      throws XmlBlasterException {
      try {
         String queueName = storageId.getStrippedId();
         if (log.isLoggable(Level.FINER)) log.finer("Entering");
        
         if (!this.isConnected) {
            if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
            return 0L;
         }
        
         int limitPrio = limitEntry.getPriority();
         long limitId = limitEntry.getUniqueId();
        
         String req = null;
         if (inclusive) 
            req = "DELETE from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND " + this.dataIdColName + " <= "  + limitId + ") )";
         else
            req = "DELETE from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND " + this.dataIdColName + " < "  + limitId + ") )";
         if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
         int ret = update(req);
         if (log.isLoggable(Level.FINE)) log.fine("removeEntriesWithLimit the result of the request '" + req + "' is : '" + ret + "'");
         return ret;
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeEntriesWithLimit", "", ex); 
      }
   }


   /**
    * gets all the entries which have the dataid specified in the argument list.
    * If the list is empty or null, an empty ArrayList object is returned.
    */
   public ArrayList getEntries(StorageId storageId, long[] dataids)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (log.isLoggable(Level.FINER)) log.finer("Entering");
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      if ((dataids == null) || (dataids.length < 1)) return new ArrayList();

      req = "SELECT * FROM " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND " + this.dataIdColName + " in (";

      ArrayList requests = this.whereInStatement(req, dataids);

      PreparedQuery query = null;
      boolean success = true;
      try {
         req = (String)requests.get(0);
         if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
         query = new PreparedQuery(pool, req, -1);

         ArrayList ret = processResultSet(query.rs, storageId, -1, -1L, false, null);

         for (int i=1; i < requests.size(); i++) {
            req = (String)requests.get(i);
            if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
            query.inTransactionRequest(req /*, -1 */);
            ret.addAll(processResultSet(query.rs, storageId, -1, -1L, false, null));
         }
         if (log.isLoggable(Level.FINE)) log.fine("Found " + ret.size() + " entries");
         return ret;

      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }

   }


   /**
    * Gets the real number of entries. 
    * That is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfEntries(String queueName)
      throws XmlBlasterException {
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + this.entriesTableName + " WHERE queueName='" + queueName + "'";
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
      PreparedQuery query = null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, true, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         if (log.isLoggable(Level.FINE)) log.fine("Num=" + ret);
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getNumOfEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * Gets the real number of persistent entries. 
    * That is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfPersistents(String queueName)
      throws XmlBlasterException {

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + this.entriesTableName + " where queueName='" + queueName + "' AND durable='T'";
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
      PreparedQuery query = null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, true, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) 
            query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getNumOfPersistents"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfPersistents", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfPersistents", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the real size of persistent entries, that is it really makes a call to the DB to find out
    * how big the size is.
    * <br />
    * TODO: Replace all four selects with one:
    *  "select count(*), sum(byteSize), durable from XB_ENTRIES WHERE queueName='history_xmlBlaster_192_168_1_4_3412Hello' GROUP BY durable;"
    */
   public final long getSizeOfPersistents(String queueName)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select sum(" + this.byteSizeColName + ") from " + this.entriesTableName + " where queueName='" + queueName + "' AND durable='T'";
      if (log.isLoggable(Level.FINE)) log.fine("Request: '" + req + "'");
      PreparedQuery query = null;
      boolean success = true;
      try {
         query = new PreparedQuery(pool, req, true, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (XmlBlasterException ex) {
         success = false;
         throw ex;
      }
      catch (Throwable ex) {
         success = false;
         if (query != null) query.closeStatement();
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, "getSizeOfPersistents"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getSizeOfPersistents", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getSizeOfPersistents", "", ex); 
      }
      finally {
         try {
            if (query != null) 
               query.close(success);
         }
         catch (Throwable ex1) {
            log.severe("exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * @param queueName or null
    * @return A nice location string for logging (instead of plain ME)
    */
   private final String getLogId(String queueName, String methodName) {
      StringBuffer sb = new StringBuffer(200);
      sb.append(ME);
      if (this.tableNamePrefix != null) {
         sb.append("-").append(this.tableNamePrefix);
      }
      if (queueName != null) {
         sb.append("-").append(queueName);
      }
      if (methodName != null) {
         sb.append("-").append(methodName).append("()");
      }
      return sb.toString();
   }

   /**
    * The Properties to use as a default are these from the QueuePlugin with the 
    * configuration name specified by defaultConfName (default is 'JDBC'). You can overwrite these 
    * properties entirely or partially with 'properties'.
    * @param confType the name of the configuration to use as default. If you pass null, then 
    *                 'JDBC' will be taken.
    * @param confVersion the version to use as a default. If you pass null, then '1.0' will be taken.
    * @param properties the properties to use to overwrite the default properties. If you pass null, no 
    *        properties will be overwritten, and the default will be used.
    */
   public static JdbcManagerCommonTable createInstance(Global glob, I_EntryFactory factory, String confType, String confVersion, Properties properties) 
      throws XmlBlasterException {
      if (confType == null) confType = "JDBC";
      if (confVersion == null) confVersion = "1.0";
      QueuePluginManager pluginManager = new QueuePluginManager(glob);
      PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, confType, confVersion);
      // clone the properties (to make sure they only belong to us) ...
      java.util.Properties
         ownProperties = (java.util.Properties)pluginInfo.getParameters().clone();
      //overwrite our onw properties ...
      if (properties != null) {
         java.util.Enumeration enumer = properties.keys();
         while (enumer.hasMoreElements()) {
            String key =(String)enumer.nextElement();
            ownProperties.put(key, properties.getProperty(key));
         }
      }
      JdbcConnectionPool pool = new JdbcConnectionPool();
      try {
         pool.initialize(glob, pluginInfo.getParameters());
      }
      catch (ClassNotFoundException ex) {
         log.severe("wipOutDB class not found: " + ex.getMessage());
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "wipeOutDB class not found", ex);
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "wipeOutDB SQL exception", ex);
      }
   
      // determine which jdbc manager class to use
      String queueClassName = pluginInfo.getClassName();
      if ("org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin".equals(queueClassName)) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin is not supported anymore");
      }
      else if ("org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin".equals(queueClassName)) {
         // then it is a JdbcManagerCommontTable
         // then it is a JdbcManager
         JdbcManagerCommonTable manager = new JdbcManagerCommonTable(pool, factory, "cleaner", null);
         pool.registerStorageProblemListener(manager);
         manager.setUp();
         return manager;
      }
      else {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "wipeOutDB for plugin '" + queueClassName + "' is not implemented");
      }
   }

   /**
    * wipes out the db. The Properties to use as a default are these from the QueuePlugin with the 
    * configuration name specified by defaultConfName (default is 'JDBC'). You can overwrite these 
    * properties entirely or partially with 'properties'.
    * @param confType the name of the configuration to use as default. If you pass null, then 
    *                 'JDBC' will be taken.
    * @param confVersion the version to use as a default. If you pass null, then '1.0' will be taken.
    * @param properties the properties to use to overwrite the default properties. If you pass null, no 
    *        properties will be overwritten, and the default will be used.
    * @param setupNewTables tells the manager to recreate empty tables if set to 'true'. Note that this flag only
    *        has effect if the JdbcManagerCommonTable is used.
    */
   public static void wipeOutDB(Global glob, String confType, String confVersion, java.util.Properties properties, boolean setupNewTables) 
      throws XmlBlasterException {
      JdbcManagerCommonTable manager = createInstance(glob, glob.getEntryFactory(), confType, confVersion,
                                       properties); 
      manager.wipeOutDB(setupNewTables);
   }

   /**
    * This main method can be used to delete all tables on the db which start
    * with a certain prefix. It is useful to cleanup the entire DB.
    * 
    * </pre>
    */
   public static void main(String[] args) {
      Global glob = Global.instance();
      glob.init(args);

      String type = glob.getProperty().get("wipeout.pluginType", (String)null);
      String version = glob.getProperty().get("wipeout.pluginVersion", (String)null);

      if ((type == null) || (version == null)) {
         System.out.println("usage: java org.xmlBlaster.util.queue.jdbc.JdbcManagerCommonTable -wipeout.pluginType JDBC -wipeout.pluginVersion 1.0");
         System.exit(1);
      }

      try {
         JdbcManagerCommonTable.wipeOutDB(glob, type, version, null, false);
      }
      catch (Exception ex) {
         System.err.println("Main" + ex.toString());
      }

   }

   synchronized public void shutdown() {
//      if (this.pool != null) this.pool.shutdown();
      if (log.isLoggable(Level.FINER)) log.finer("shutdown");
      if (this.pool != null) {
         this.pool.unregisterManager(this);
      }
   }

   synchronized public void registerQueue(I_Queue queue) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("registerQueue, number of queues registered (before registering this one): '" + this.queueCounter + "'");
      if (queue == null) return;
      this.queueCounter++;
   }

   synchronized public void unregisterQueue(I_Queue queue) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("unregisterQueue, number of queues registered (still including this one): '" + this.queueCounter + "'");
      if (queue == null) return;
      this.queueCounter--;
      if (this.queueCounter == 0) {
         shutdown();
      }
   }
}
