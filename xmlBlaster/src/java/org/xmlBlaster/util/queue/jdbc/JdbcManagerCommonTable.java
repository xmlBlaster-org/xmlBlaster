/*------------------------------------------------------------------------------
Name:      JdbcManagerCommonTable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.Global;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.BatchUpdateException;

import java.util.Hashtable;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;

import org.xmlBlaster.util.Timestamp;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.WeakHashMap;
import java.util.StringTokenizer;
import java.util.Iterator;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * Delegate class which takes care of SQL specific stuff for the JdbcQueuePlugin
 * class.
 * <p>
 * One instance of this is created by Global for each StorageId prefix,
 * so one instance for 'history', one for 'cb' etc.
 * </p>
 *
 *  to define:
 *             Postgres
 *  string
 *  longint
 *  int
 *  blob
 *
 * The tables needed for each JdbcManagerCommonTable instance are the following:
 * - nodesTableName  (defaults to 'NODES')
 * - queuesTableName (defaults to 'QUEUES')
 * - entriesTableName (defaults to 'ENTRIES')
 * The names of such tables are constituted by the tableNamePrefix (which defaults to 'XB') plus the 
 * nodesTableName
 * 
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 *
 */
public class JdbcManagerCommonTable implements I_StorageProblemListener, I_StorageProblemNotifier {

   private static final String ME = "JdbcManagerCommonTable";
   private final Global glob;
   private final LogChannel log;
   private final JdbcConnectionPool pool;
   private final I_EntryFactory factory;
   private final WeakHashMap listener;
   private final static String DUMMY_VALUE = "A";
   private final TreeSet queues;

   private final String tableNamePrefix;
   // the names to be used
   private String stringTxt = null;
   private String longintTxt = null;
   private String intTxt = null;
   private String blobTxt = null;
   private String booleanTxt = null;
   private String tablesTxt = null;
   private String tableNameTxt = null;

   private boolean dbInitialized = false;
   private int maxStatementLength = 0;
   private int[] errCodes = null;
   private boolean isConnected = true;

   private static boolean first = true;
   private String nodesTableName = null;
   private String queuesTableName = null;
   private String entriesTableName = null;
//   private boolean hasAdminPermission = true;

//   private final int queueIncrement;
   private java.util.HashSet nodesCache;


   /**
    * @param JdbcConnectionPool the pool to be used for the connections to
    *        the database. IMPORTANT: The pool must have been previously
    *        initialized.
    */
   public JdbcManagerCommonTable(JdbcConnectionPool pool, I_EntryFactory factory)
      throws XmlBlasterException {
      this.pool = pool;
      this.glob = this.pool.getGlobal();
      this.log = glob.getLog("jdbc");
      if (this.log.CALL) this.log.call(ME, "Constructor called");

      this.factory = factory;

      if (!this.pool.isInitialized())
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "constructor: the Connection pool is not properly initialized");

      // get the necessary metadata
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         this.maxStatementLength = conn.getMetaData().getMaxStatementLength();
         if (this.maxStatementLength < 1) {
            this.maxStatementLength = glob.getProperty().get("queue.persistent.maxStatementLength", 2048);
            if (first) {
               this.log.info(ME, "The maximum SQL statement length is not defined in JDBC meta data, we set it to " + this.maxStatementLength);
               first = false;
            }
         }

         if (!conn.getMetaData().supportsTransactions()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            this.log.error(ME, "the database '" + dbName + "' does not support transactions, unpredicted results may happen");
         }
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "constructor: failed to get the metadata", ex);
      }
      finally {
         if (conn != null) this.pool.releaseConnection(conn);
      }

      Hashtable names = pool.getMapping();
      this.queues = new TreeSet();
      this.listener = new WeakHashMap();

      this.stringTxt = (String)names.get("string");
      if (this.stringTxt == null) this.stringTxt = "text";

      this.longintTxt = (String)names.get("longint");
      if (this.longintTxt == null) this.longintTxt = "bigint";

      this.intTxt = (String)names.get("int");
      if (this.intTxt == null) this.intTxt = "integer";

      this.booleanTxt = (String)names.get("boolean");
      if (this.booleanTxt == null) this.booleanTxt = "boolean";

      this.blobTxt = (String)names.get("blob");
      if (this.blobTxt == null) this.blobTxt = "bytea";

      this.tablesTxt = (String)names.get("tables");
      if (this.tablesTxt == null) this.tablesTxt = "pg_tables";
      this.tableNameTxt = (String)names.get("tablename");
      if (this.tableNameTxt == null) tableNameTxt = "tablename";

      this.tableNamePrefix = this.pool.getTableNamePrefix();
      // this.queueIncrement = this.pool.getTableAllocationIncrement(); // 2

      String errorCodesTxt = (String)names.get("connectionErrorCodes");
      if (errorCodesTxt == null) errorCodesTxt = "1089:17002";

      StringTokenizer tokenizer = new StringTokenizer(errorCodesTxt, ":");
      this.errCodes = new int[tokenizer.countTokens()];
      int nmax = tokenizer.countTokens();

      String token = null;
      for (int i=0; i < nmax; i++) {
         try {
            token = tokenizer.nextToken().trim();
            this.errCodes[i] = Integer.parseInt(token);
         }
         catch (Exception ex) {
            this.errCodes[i] = -1;
            this.log.warn(ME, "error while parsing. '" + token + "' probably not an integer: ");
         }
      }

      if (this.log.DUMP) this.log.dump(ME, "Constructor: num of error codes: "  + nmax);

      this.nodesTableName = this.tableNamePrefix + 
                            (String)pool.getPluginProperties().getProperty("nodesTableName", "NODES");
      this.queuesTableName = this.tableNamePrefix + 
                            (String)pool.getPluginProperties().getProperty("queuesTableName", "QUEUES");
      this.entriesTableName = this.tableNamePrefix + 
                            (String)pool.getPluginProperties().getProperty("entriesTableName", "ENTRIES");
      this.nodesCache = new java.util.HashSet();
   }

   /**
    * pings the jdbc connection to check if the DB is up and running. It returns
    * 'true' if the connection is OK, false otherwise. The ping is done by invocation 
    */
   public boolean ping() {
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         boolean ret = ping(conn);
         return ret;
      }
      catch (XmlBlasterException ex) {
         this.log.warn(ME, "ping failed due to problems with the pool. Check the jdbc pool size in 'xmlBlaster.properties'. Reason :" + ex.getMessage());
         return false;
      }
      finally {
         try {
            if (conn != null) this.pool.releaseConnection(conn);
         }
         catch (XmlBlasterException e) {
            this.log.error(ME, "ping: releaseConnection failed: " + e.getMessage());
         }
      }
   }


   /**
    * pings the jdbc connection to check if the DB is up and running. It returns
    * 'true' if the connection is OK, false otherwise. The ping is done by invocation 
    */
// isClosed() does not work
   private boolean ping(Connection conn) {
      if (this.log.CALL) this.log.call(ME, "ping");
      if (conn == null) return false; // this could occur if it was not possible to create the connection

      Statement st = null;
      try {
         // conn.isClosed();
         st = conn.createStatement();
         st.setQueryTimeout(this.pool.getQueryTimeout());
//          st.execute(""); <-- this will not work on ORACLE (fails)
         st.execute("SELECT count(*) from " + this.tablesTxt);
         if (this.log.TRACE) this.log.trace(ME, "ping successful");
         return true;
      }
      catch (Throwable ex) {
         this.log.warn(ME, "ping to DB failed. DB may be down. Reason " + ex.toString());
         return false;
      }
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable e) {
            this.log.warn(ME, "ping exception when closing the statement " + e.toString());
         }
      }
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
      synchronized (this.listener) {
         return this.listener.remove(entry) != null;
      }
   }

   /**
    * @see I_StorageProblemListener#storageUnavailable(int)
    */
   public void storageUnavailable(int oldStatus) {
      if (this.log.CALL) this.log.call(ME, "storageUnavailable (old status '" + oldStatus + "')");
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
      if (this.log.CALL) this.log.call(ME, "storageAvailable (old status '" + oldStatus + "')");
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
    * handles the SQLException. If it is a communication exception like the
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
         this.log.error(ME, location + ": the connection to the DB has been lost. Going in polling modus");
         this.pool.setConnectionLost();
      }
      return ret;
   }

   /**
    * checks if all necessary tables exist. If a table does not exist and 'createTables' true, then the 
    * table is created.
    * @return boolean 'true' if the tables are all there after the invocation to this method, 'false' if at
    *         least one of the required tables is missing after the invocation to this method.
    */
   public final boolean tablesCheckAndSetup(boolean createTables)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "tablesCheckAndSetup");

      if (!this.isConnected)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "tablesCheckAndSetup: the DB disconnected. Handling is currently not possible");

      boolean nodesTableExists = false;
      boolean queuesTableExists = false;
      boolean entriesTableExists = false;

      String req = "SELECT count(*) from " + tablesTxt + " where upper(" + tableNameTxt + ")='" + this.nodesTableName.toUpperCase() + "'";
      if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");

      PreparedQuery query = null;

      try {
         query = new PreparedQuery(pool, req, false, this.log, -1);
         query.rs.next();         
         int size = query.rs.getInt(1);
         if (size > 0) nodesTableExists = true;
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "number of '" + this.nodesTableName  + "' is " + size);

         req = "SELECT count(*) from " + tablesTxt + " where upper(" + tableNameTxt + ")='" + this.queuesTableName.toUpperCase() + "'";
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");
         query.inTransactionRequest(req /*, -1*/);
         query.rs.next();
         size = query.rs.getInt(1);
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "number of '" + this.queuesTableName  + "' is " + size);
         if (size > 0) queuesTableExists = true;

         req = "SELECT count(*) from " + tablesTxt + " where upper(" + tableNameTxt + ")='" + this.entriesTableName.toUpperCase() + "'";
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");
         query.inTransactionRequest(req /*, -1*/);
         query.rs.next();
         size = query.rs.getInt(1);
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "number of '" + this.entriesTableName  + "' is " + size);
         if (size > 0) entriesTableExists = true;

         if (!createTables) 
            return  nodesTableExists && queuesTableExists && entriesTableExists;


         if (!nodesTableExists) {
            log.info(getLogId(null, null, "tablesCheckAndSetup"), "adding table '" + this.nodesTableName + "' as the 'nodes' table");
            req = "CREATE TABLE " + this.nodesTableName.toUpperCase() + " (nodeId " + this.stringTxt + ", PRIMARY KEY (nodeId))";
            if (this.log.TRACE) 
               this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");
            update(req, query.conn);
         }

         if (!queuesTableExists) {
            log.info(getLogId(null, null, "tablesCheckAndSetup"), "adding table '" + this.queuesTableName + "' as the 'queues' table");
            req = "CREATE TABLE " + this.queuesTableName.toUpperCase() + " (queueName " + this.stringTxt +
                  ", nodeId " + this.stringTxt +
                  ", numOfBytes " + this.longintTxt +
                  ", numOfEntries " + this.longintTxt +
                  ", PRIMARY KEY (queueName, nodeId)" + 
                  ", FOREIGN KEY (nodeId) REFERENCES " + this.nodesTableName + " ON DELETE CASCADE)";
            if (this.log.TRACE) 
               this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");
            update(req, query.conn);
         }

         if (!entriesTableExists) {
            log.info(getLogId(null, null, "tablesCheckAndSetup"), "adding table '" + this.entriesTableName + "' as the 'entries' table");
            req = "CREATE TABLE " + this.entriesTableName.toUpperCase() + " (dataId " + this.longintTxt +
                  ", nodeId " + this.stringTxt +
                  ", queueName " + this.stringTxt +
                  ", prio " + this.intTxt +
                  ", flag " + this.stringTxt +
                  ", durable " + this.booleanTxt +
                  ", byteSize " + this.longintTxt +
                  ", blob " + this.blobTxt +
                  ", PRIMARY KEY (dataId, queueName)" + 
                  ", FOREIGN KEY (queueName, nodeId) REFERENCES " + this.queuesTableName + " ON DELETE CASCADE)";
            if (this.log.TRACE) 
               this.log.trace(getLogId(null, null, "tablesCheckAndSetup"), "Request: '" + req + "'");
            update(req, query.conn);
         }

         return true;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(null, null, "tablesCheckAndSetup"), ex, "SQL request giving problems: " + req))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".tablesCheckAndSetup", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".tablesCheckAndSetup", "", ex); 
      }
      finally {
         try {
            if (query !=null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * Adds a node to the DB. It caches all nodes in memory to better the
    * performance.
    * @return boolean false if the node already existed, true otherwise.
    */
   public final boolean addNode(String nodeId) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "addNode '" + nodeId + "'");
      if (this.nodesCache.contains(nodeId)) {
         if (this.log.DUMP) {
            Object[] objs = this.nodesCache.toArray();
            for (int i=0; i < this.nodesCache.size(); i++) {
               this.log.dump(ME, "addNode: node '" + (String)objs[i] + "' is in cache");
            }
         }
         return false;
      }
      if (this.log.TRACE) this.log.trace(ME, "addNode: the node did not exist in cache. Will add it now");

      // add it to cache after the db invocation to be on the safe side ...

      String req = "SELECT count(*) from " + this.nodesTableName + " where nodeId='" + nodeId + "'";
      if (this.log.TRACE) this.log.trace(getLogId(null, nodeId, "addNode"), "Request: '" + req + "'");
      PreparedQuery query = null;
      PreparedStatement preStatement = null;
      boolean ret = false;
      try {
         query = new PreparedQuery(pool, req, false, this.log, -1);
         query.rs.next();         
         int size = query.rs.getInt(1);
         if (size > 0) {
            ret = false;
         }
         else {
            req = "INSERT INTO " + this.nodesTableName + " VALUES (?)";
            preStatement = query.conn.prepareStatement(req);
            preStatement.setQueryTimeout(this.pool.getQueryTimeout());

            preStatement.setString(1, nodeId);
            if (this.log.TRACE) this.log.trace(getLogId(null, nodeId, "addNode"), preStatement.toString());
            preStatement.executeUpdate();
            ret = true;
         }

// Currently don't add it to the cache since the manager on which wipeOutDB is invoked is another (and clearing of cache is not possible)
/*
         synchronized (this.nodesCache) {
            if (!this.nodesCache.contains(nodeId)) this.nodesCache.add(nodeId);
         }
*/
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(null, nodeId, "addNode"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addNode", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addNode", "", ex); 
      }
      finally {
         try {
            if (preStatement !=null) preStatement.close();
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "addNode: exception occured when closing statement and query, reason: " + ex1.toString());
         }
      }
   }

   /**
    * Removes a node from the DB.
    * @return boolean false if the node was not on the DB, true if it has been successfully removed.
    */
   public final boolean removeNode(String nodeId) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "removeNode");
      // to be sure remove it from cache as first thing ...

      synchronized (this.nodesCache) {
         if (this.nodesCache.contains(nodeId)) this.nodesCache.remove(nodeId);
         String postfix =  this.nodesTableName + " where nodeId='" + nodeId + "'";
         String req = "SELECT count(*) from " + postfix;
         if (this.log.TRACE) this.log.trace(getLogId(null, nodeId, "removeNode"), "Request: '" + req + "'");
         PreparedQuery query = null;
         try {
            query = new PreparedQuery(pool, req, false, this.log, -1);
            query.rs.next();         
            int size = query.rs.getInt(1);
            if (size < 1) return false;
     
            // remove all queues associated with this node here ...
            req = "DELETE FROM " + postfix;
            update(req, query.conn);
            return true;
         }
         catch (XmlBlasterException ex) {
            throw ex;
         }
         catch (Throwable ex) {
            if (checkIfDBLoss(query != null ? query.conn : null, getLogId(null, nodeId, "removeNode"), ex))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".removeNode", "", ex); 
            else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeNode", "", ex);
         }
         finally {
            try {
               if (query != null) query.close();
            }
            catch (Throwable ex1) {
               this.log.error(ME, "addNode: exception occured when closing statement and query, reason: " + ex1.toString());
            }
         }
      }

   }

   /**
    * Adds a queue to the DB. If the node to which the queue belongs does not exist one is created.
    * @return boolean false if the node already existed, true otherwise.
    */
   public final boolean addQueue(String queueName, String nodeId, long numOfBytes, long numOfEntries) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "addQueue");
      addNode(nodeId); // returns immediately if in cache ...
      String req = "SELECT count(*) from " + this.queuesTableName + " where (queueName='" + queueName + "' AND nodeId='" + nodeId + "')";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addQueue"), "Request: '" + req + "'");
      PreparedQuery query = null;
      PreparedStatement preStatement = null;
      try {
         query = new PreparedQuery(pool, req, false, this.log, -1);
         query.rs.next();         
         int size = query.rs.getInt(1);
         if (size > 0) return false;

         // remove all entries associated with this queue here ...

         req = "INSERT INTO " + this.queuesTableName + " VALUES (?,?,?,?)";
         preStatement = query.conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());
         preStatement.setString(1, queueName);
         preStatement.setString(2, nodeId);
         preStatement.setLong(3, numOfBytes);
         preStatement.setLong(4, numOfEntries);

         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addQueue"), preStatement.toString());
         preStatement.executeUpdate();
         return true;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "addQueue"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addQueue", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addQueue", "", ex);
      }
      finally {
         try {
            if (preStatement !=null) preStatement.close();
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * Removes a node from the DB.
    * @return boolean false if the node was not on the DB, true if it has been successfully removed.
    */
   public final boolean removeQueue(String queueName, String nodeId) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "removeQueue");
      String postfix = this.queuesTableName + " where (queueName='" + queueName + "' AND nodeId='" + nodeId + "')";
      String req = "SELECT count(*) from " + postfix;
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "removeQueue"), "Request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, false, this.log, -1);
         query.rs.next();         
         int size = query.rs.getInt(1);
         if (size < 1) return false;

         req = "DELETE FROM " + postfix;
         update(req, query.conn);
         return true;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "removeQueue"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".removeQueue", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeQueue", "", ex);
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
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
    * @throws XmlBlasterException if an error occured when trying to get a connection or an SQLException
    *         occured.
    */
   public final boolean modifyEntry(String queueName, String nodeId, I_Entry entry)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "addSingleEntry"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addSingleEntry"), "For entry '" + entry.getUniqueId() + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addSingleEntry", " the connection to the DB is unavailable already before trying to add an entry"); 
      }

      Connection conn = null;
      PreparedStatement preStatement = null;
      Statement exStatement = null;
      boolean ret = false;

      long dataId = entry.getUniqueId();
      int prio = entry.getPriority();
      byte[] blob = this.factory.toBlob(entry);
      String typeName = entry.getEmbeddedType();
      boolean persistent = entry.isPersistent();
      long sizeInBytes = entry.getSizeInBytes();
      
      if (this.log.DUMP)
         this.log.dump(ME, "modification dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + ", byteSize in bytes: " + sizeInBytes);

      try {
         conn = this.pool.getConnection();
         String req = "UPDATE " + this.entriesTableName + " SET prio = ? , flag = ? , durable = ? , byteSize = ? , blob = ? WHERE  dataId = ? AND nodeId = ? AND queueName = ?";

         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "modifyEntry"), req);
         preStatement = conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());
         preStatement.setInt(1, prio);
         preStatement.setString(2, typeName);
         if (persistent == true) preStatement.setString(3, "T");
         else preStatement.setString(3, "F");
         preStatement.setLong(4, sizeInBytes);
         preStatement.setBytes(5, blob);
         preStatement.setLong(6, dataId);
         preStatement.setString(7, nodeId);
         preStatement.setString(8, queueName);

         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "modifyEntry"), preStatement.toString());

         int num = preStatement.executeUpdate();
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "modifyEntry"), "Modified " + num + " entries, entryId='" + entry.getUniqueId() + "'");
         ret = true;
      }
      catch (Throwable ex) {
         if (this.log.TRACE) {
            if (ex instanceof SQLException) {
               this.log.trace(ME, "modifyEntry: sql exception, the sql state: '" + ((SQLException)ex).getSQLState() );
               this.log.trace(ME, "modifyEntry: sql exception, the error code: '" + ((SQLException)ex).getErrorCode() );
            }
            else this.log.trace(ME, "modifyEntry: exception, the error reason: '" + ex.toString());
         }

         try {
            preStatement.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "modifyEntry: Exception when closing the statement: " + ex1.toString());
         }
//         if (!conn.getAutoCommit()) conn.rollback(); // DANGER !!!!!!! NOT SAFE YET 
         this.log.warn(getLogId(queueName, nodeId, "modifyEntry"), "Could not insert entry '" +
                  entry.getClass().getName() + "'-'" +  entry.getLogId() + "-" + entry.getUniqueId() + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "modifyEntry"), ex)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".modifyEntry", "", ex); 
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".modifyEntry", "", ex); 
         }
      }
      finally {
         try {
            if (exStatement != null) exStatement.close();
            if (preStatement != null) preStatement.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "modifyEntry: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) this.pool.releaseConnection(conn);
      }
      return ret;
   }


   /**
    *
    * Adds a row to the specified queue table
    * @param queueName The name of the queue on which to perform the operation
    * @param entry the object to be stored.
    *
    * @return true on success
    *
    * @throws SQLException if an error occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   private final boolean addSingleEntry(String queueName, String nodeId, I_Entry entry, Connection conn)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "addSingleEntry"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addSingleEntry"), "For entry '" + entry.getUniqueId() + "' currently not possible. No connection to the DB");
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
      
      if (this.log.DUMP)
         this.log.dump(ME, "addition. dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + ", byteSize in bytes: " + sizeInBytes);

      try {
         String req = "INSERT INTO " + this.entriesTableName + " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?)";
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEntry"), req);

         preStatement = conn.prepareStatement(req);
         preStatement.setQueryTimeout(this.pool.getQueryTimeout());
         preStatement.setLong(1, dataId);
         preStatement.setString(2, nodeId);
         preStatement.setString(3, queueName);
         preStatement.setInt(4, prio);
         preStatement.setString(5, typeName);
         if (persistent == true) preStatement.setString(6, "T");
         else preStatement.setString(6, "F");
         preStatement.setLong(7, sizeInBytes);
         preStatement.setBytes(8, blob);

         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEntry"), preStatement.toString());

         int num = preStatement.executeUpdate();
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEntry"), "Added " + num + " entries, entryId='" + entry.getUniqueId() + "'");
         ret = true;
      }
      catch (Throwable ex) {
         if (this.log.TRACE) {
            if (ex instanceof SQLException) {
               this.log.trace(ME, "addEntry: sql exception, the sql state: '" + ((SQLException)ex).getSQLState() );
               this.log.trace(ME, "addEntry: sql exception, the error code: '" + ((SQLException)ex).getErrorCode() );
            }
         }
         try {
            preStatement.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing statement: " + ex1.toString());
            ex1.printStackTrace();
         }
//         if (!conn.getAutoCommit()) conn.rollback(); // DANGER !!!!!!! NOT SAFE YET 
         this.log.warn(getLogId(queueName, nodeId, "addEntry"), "Could not insert entry '" +
                  entry.getClass().getName() + "'-'" +  entry.getLogId() + "-" + entry.getUniqueId() + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "addEntry"), ex)) {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex); 
         }
         // check if the exception was due to an existing entry. If yes, no exception will be thrown
         try {
            String req = "SELECT count(*) from " + this.entriesTableName + " where (dataId='" + dataId + "' AND nodeId='" + nodeId + "')";
            if (this.log.TRACE) this.log.trace(ME, "addEntry: checking if entry already in db: request='" + req + "'");
            exStatement = conn.createStatement();
//            exStatement.setQueryTimeout(this.pool.getQueryTimeout());
            ResultSet rs = exStatement.executeQuery(req);
            rs.next();         
            int size = rs.getInt(1);
            if (size < 1) throw ex;
         }
         catch (Throwable ex1) {
            if (this.log.TRACE) this.log.trace(ME, "addEntry: checking if entry already in db: exception in select: '" + ex.toString() + "'");
            if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "addEntry"), ex1))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex1); 
            else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntry", "", ex1); 
         }
         ret = false;
      }
      finally {
         try {
            if (exStatement != null) exStatement.close();
            if (preStatement != null) preStatement.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "addEntry: throwable when closing the connection: " + ex.toString());
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
   public final boolean addEntry(String queueName, String nodeId, I_Entry entry)
      throws XmlBlasterException {
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         return addSingleEntry(queueName, nodeId, entry, conn);
      }
      finally {
         if (conn != null) this.pool.releaseConnection(conn);
      }
   }


   /**
    *
    * Adds several rows to the specified queue table in batch mode to improve performance
    * @param queueName The name of the queue on which to perform the operation
    * @param nodeId the node id to which the queue belongs
    * @param entries the entries to store
    * @return array of boolean telling which entries where stored and which not.
    *
    * @throws SQLException if an error occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   public int[] addEntries(String queueName, String nodeId, I_Entry[] entries)
      throws XmlBlasterException {

      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "addEntries"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEnties"), " for '" + entries.length + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", " the connection to the DB is unavailable already before trying to add entries"); 
      }

      PreparedStatement preStatement = null;
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         if (conn.getAutoCommit()) conn.setAutoCommit(false);
         String req = "INSERT INTO " + this.entriesTableName + " VALUES ( ?, ?, ?, ?, ?, ?, ?, ?)";
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEntries"), req);

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
            preStatement.setLong(1, dataId);
            preStatement.setString(2, nodeId);
            preStatement.setString(3, queueName);
            preStatement.setInt(4, prio);
            preStatement.setString(5, typeName);
            if (persistent == true) preStatement.setString(6, "T");
            else preStatement.setString(6, "F");
            preStatement.setLong(7, sizeInBytes);
            preStatement.setBytes(8, blob);
            if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "addEntries"), preStatement.toString());
            preStatement.addBatch();
         }
         int[] ret = preStatement.executeBatch();
         if (!conn.getAutoCommit()) conn.commit();
         return ret;
      }
      catch (Throwable ex) {
         int i = 0;
         try {
            if (!conn.getAutoCommit()) {
               conn.rollback(); // rollback the original request ...
               conn.setAutoCommit(true); // since if an exeption occurs it infects future queries within the same transaction
            }
         }
         catch (Throwable ex1) {
            this.log.error(ME, "error occured when trying to rollback after exception: reason: " + ex1.toString());
         }
         this.log.warn(getLogId(queueName, nodeId, "addEntries"), "Could not insert entries: " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "addEntries"), ex)) 
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", "", ex); 
         else { // check if the exception was due to an already existing entry by re

            int[] ret = new int[entries.length];
            try {
               if (this.log.TRACE) this.log.trace(ME, "addEntries adding each entry in single mode since an exception occured when using 'batch mode'");
               for (i=0; i < entries.length; i++) {
                  if (addSingleEntry(queueName, nodeId, entries[i], conn)) ret[i] = 1; 
                  else ret[i] = 0;
                  if (this.log.TRACE) this.log.trace(ME, "addEntries adding entry '" + i + "' in single mode succeeded");
               }
               if (!conn.getAutoCommit()) conn.commit();
               return ret;
            }
            catch (XmlBlasterException ex1) {
               // conn.rollback();
               try {
                  for (int ii=0; ii < i; ii++) {
                     if (ret[ii] > 0) deleteEntry(queueName, nodeId, entries[ii].getUniqueId()); // this could be collected and done in one shot
                  }
               }
               catch (Throwable ex2) {
                  this.log.error(ME, "addEntries exception occured when rolling back (this could generate inconsistencies in the data) : " + ex2.toString());
               }
               throw ex1;
            }
            catch (Throwable ex1) {
               // conn.rollback();
               try {
                  for (int ii=0; ii < i; ii++) {
                     if (ret[ii] > 0) deleteEntry(queueName, nodeId, entries[ii].getUniqueId()); // this could be collected and done in one shot
                  }
               }
               catch (Throwable ex2) {
                  this.log.error(ME, "addEntries exception occured when rolling back (this could generate inconsistencies in the data) : " + ex2.toString());
               }
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntries", "", ex1); 
            }
         }
      }
      finally {
         try {
            if (preStatement != null) preStatement.close();
            if (conn != null) {
               if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }
         }
         catch (Throwable ex) {
            this.log.warn(ME, "addEntries: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) this.pool.releaseConnection(conn);
      }
   }

   /**
    * Cleans up the specified queue. It deletes the queue from the 'queues' table which implicitly deletes
    * all entries in the 'entries' table associated to this queue.
    * @return the number of queues deleted (not the number of entries).
    */
   public final int cleanUp(String queueName, String nodeId) throws XmlBlasterException {

      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "cleanUp"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "cleanUp"), "Currently not possible. No connection to the DB");
         return 0;
      }

      try {
         String req = "delete from " + this.queuesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "'";
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "cleanUp"), " request is '" + req + "'");
         return update(req);
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         Connection conn = null;
         try {
            conn = this.pool.getConnection();
            if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "deleteEntries"), ex))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex); 
         }
         finally {
            if (conn != null) this.pool.releaseConnection(conn);
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex);
      }
   }


   /**
    * wipes out the entire DB, i.e. it deletes the three tables from the DB. IMPORTANT: 
    * @param doSetupNewTables if set to 'true' it recreates the necessary empty tables.
    */
   public int wipeOutDB(boolean doSetupNewTables) throws XmlBlasterException {
      // retrieve all tables to delete
      if (this.log.CALL)  log.call(ME, "wipeOutDB");

      // reset the cached nodes 
      this.nodesCache = new java.util.HashSet();


      PreparedQuery query = null;
      int count = 0;
      Connection conn = null;
      try {
        try {
           String req = "DROP TABLE " + this.entriesTableName;
            if (log.CALL)  log.call(ME, "wipeOutDB " + req + " will be invoked on the DB");
            conn = this.pool.getConnection();
            if (conn.getAutoCommit()) conn.setAutoCommit(false);
            this.update(req, conn);
            count++;
         }
         catch (SQLException ex) {
            if (checkIfDBLoss(conn, getLogId(null, null, "wipeOutDB"), (SQLException)ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(null, null, "wipeOutDB"), "SQLException when wiping out DB", ex);
            else {
               this.log.warn(ME, "Exception occurred when trying to drop the table '" + this.entriesTableName + "', it probably is already dropped. Reason: " + ex.toString());
            }
         }

        try {
            String req = "DROP TABLE " + this.queuesTableName;
            if (log.CALL)  log.call(ME, "wipeOutDB " + req + " will be invoked on the DB");
            this.update(req, conn);
            count++;
         }
         catch (Throwable ex) {
            if (checkIfDBLoss(conn, getLogId(null, null, "wipeOutDB"), (SQLException)ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(null, null, "wipeOutDB"), "SQLException when wiping out DB", ex);
            else {
               this.log.warn(ME, "Exception occurred when trying to drop the table '" + this.queuesTableName + "', it probably is already dropped. Reason " + ex.toString());
            }
         }

         try {
           String req = "DROP TABLE " + this.nodesTableName;
            if (log.CALL)  log.call(ME, "wipeOutDB " + req + " will be invoked on the DB");
            this.update(req, conn);
            count++;
         }
         catch (Throwable ex) {
            if (checkIfDBLoss(conn, getLogId(null, null, "wipeOutDB"), (SQLException)ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(null, null, "wipeOutDB"), "SQLException when wiping out DB", ex);
            else {
               this.log.warn(ME, "Exception occurred when trying to drop the table '" + this.nodesTableName + "', it probably is already dropped. Reason " + ex.toString());
            }
         }
         if (!conn.getAutoCommit()) conn.commit();
      }
      catch (Throwable ex) {
         try {
            if (conn != null) conn.rollback();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "wipeOutDB: exception occurred when rolling back: " + ex1.toString());
         }
         if (ex instanceof XmlBlasterException) throw (XmlBlasterException)ex;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, getLogId(null, null, "wipeOutDB"), "wipeOutDB: exception ", ex);
      }
      finally {
         try {
            if (conn != null) {
               if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }
         }
         catch (Throwable ex) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, getLogId(null, null, "wipeOutDB"), "wipeOutDB: exception when closing the query", ex);
         }
         finally {
            if (conn != null) this.pool.releaseConnection(conn);
         }
      }

      try {
         if (doSetupNewTables) setUp();
      }
      catch (Throwable ex) {
         this.log.error(ME, "SQLException occured when cleaning up the table. Reason " + ex.toString()); 
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
    * @param tableName the name of the table in which to count
    * @return the current amount of bytes used in the table.
    */
   public long getNumOfBytes(String queueName, String nodeId)
      throws XmlBlasterException {

      if (!this.isConnected)
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(queueName, nodeId, "getNumOfBytes"), "The DB is disconnected. Handling queue '" + queueName + "' is currently not possible");
      Connection conn = null;
      PreparedStatement st = null;

      try {
         String req = "SELECT sum(byteSize) from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "'";
         conn = this.pool.getConnection();
         st = conn.prepareStatement(req);
         st.setQueryTimeout(this.pool.getQueryTimeout());
         ResultSet rs = st.executeQuery();
         rs.next();
         return rs.getLong(1);
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "getNumOfBytes"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfBytes", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfBytes", "", ex); 
      }
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, ".getNumOfBytes: exception when closing statement: " + ex.toString());
         }
         if (conn != null) this.pool.releaseConnection(conn);
      }
   }


   /**
    * Sets up a table for the queue specified by this queue name.
    * If one already exists (i.e. if it recovers from a crash) its associated
    * table name is returned. If no such queue is found among the used table,
    * a new table is taken from the free tables. If no free tables are available,
    * a certain amount of such tables are created.
    */
   public final void setUp() throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "setUp");

      try {
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "setUp"), "Initializing the first time the pool");
         tablesCheckAndSetup(this.pool.isDbAdmin());
         this.dbInitialized = true;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         Connection conn = null;
         try {
            conn = this.pool.getConnection();
            if (checkIfDBLoss(conn, getLogId(null, null, "setUp"), ex, "Table name giving Problems"))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".setUp", "", ex); 
         }
         finally {
            if (conn != null) this.pool.releaseConnection(conn);
         }
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".setUp", "", ex);
      }
   }

   private final ArrayList processResultSet(ResultSet rs, StorageId storageId, int numOfEntries, long numOfBytes, boolean onlyId)
      throws SQLException, XmlBlasterException {

      if (this.log.CALL) this.log.call(getLogId(storageId.getStrippedId(), null, "processResultSet"), "Entering");

      ArrayList entries = new ArrayList();
      int count = 0;
      long amount = 0L;
      long currentAmount = 0L;

      while ( (rs.next()) && ((count < numOfEntries) || (numOfEntries < 0)) &&
         ((amount < numOfBytes) || (numOfBytes < 0))) {

         if(onlyId) {
            entries.add(new Long(rs.getLong(1)));
         }
         else {
            long dataId = rs.getLong(1);            // preStatement.setLong(1, dataId);
            /*String nodeId =*/ rs.getString(2);    // preStatement.setString(2, nodeId);
            /*String queueName =*/ rs.getString(3); // preStatement.setString(3, queueName);
            int prio = rs.getInt(4);                // preStatement.setInt(4, prio);
            String typeName = rs.getString(5);      // preStatement.setString(5, typeName);
//            boolean persistent = rs.getBoolean(4); // preStatement.setBoolean(4, persistent);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String persistentAsChar = rs.getString(6);
            boolean persistent = false;
            if ("T".equalsIgnoreCase(persistentAsChar)) persistent = true;

            long sizeInBytes = rs.getLong(7);
            byte[] blob = rs.getBytes(8); // preStatement.setObject(5, blob);

            if ( (numOfBytes < 0) || (sizeInBytes+amount < numOfBytes) || (count == 0)) {
               if (this.log.DUMP)
                  this.log.dump(ME, "processResultSet: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " persistent: " + persistent);
               entries.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, blob, storageId));
               amount += sizeInBytes;
            }
         }
         count++;
      }

      while ((rs.next()) && ((count < numOfEntries) || (numOfEntries <0))) {
         if(onlyId) {
            entries.add(new Long(rs.getLong(1)));
         }
         else {
            long dataId = rs.getLong(1); // preStatement.setLong(1, dataId);
            int prio = rs.getInt(2); // preStatement.setInt(2, prio);
            String typeName = rs.getString(3); // preStatement.setString(3, typeName);
//            boolean persistent = rs.getBoolean(4); // preStatement.setBoolean(4, persistent);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String persistentAsChar = rs.getString(4);
            boolean persistent = false;
            if ("T".equalsIgnoreCase(persistentAsChar)) persistent = true;

            long sizeInBytes = rs.getLong(5);
            byte[] blob = rs.getBytes(6); // preStatement.setObject(5, blob);

            if (this.log.DUMP)
               this.log.dump(ME, "processResultSet: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " persistent: " + persistent);
            entries.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, blob, storageId));
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

      if (this.log.CALL) this.log.call(ME,"processResultSetForDeleting invoked");
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
      if (this.log.CALL) this.log.call(getLogId(null, null, "update"), "Entering request=" + request);

      Connection conn = null;
      Statement  statement = null;
      int ret = 0;

      try {
         conn = pool.getConnection();
         statement = conn.createStatement();
         statement.setQueryTimeout(this.pool.getQueryTimeout());
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "update"), "Executing statement: " + request);
         ret = statement.executeUpdate(request);
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "update"), "Executed statement, number of changed entries=" + ret);
      }
      finally {
         try {
            if (statement !=null) statement.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "update: throwable when closing statement: " + ex.toString());
         }
         if (conn != null) this.pool.releaseConnection(conn);
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

      if (this.log.CALL) this.log.call(getLogId(null, null, "update"), "Request=" + request + " and connection " + conn);

      Statement  statement = null;
      int ret = 0;

      try {
         statement = conn.createStatement();
         statement.setQueryTimeout(this.pool.getQueryTimeout());
         ret = statement.executeUpdate(request);
         if (this.log.TRACE) this.log.trace(getLogId(null, null, "update"), "Executed statement, number of changed entries=" + ret);
      }

      finally {
         if (statement != null) statement.close();
      }

      return ret;
   }


   /**
    * deletes all transient messages
    */
   public int deleteAllTransient(String queueName, String nodeId) throws XmlBlasterException {
      try {
         if (this.log.CALL) 
            this.log.call(getLogId(queueName, nodeId, "deleteAllTransient"), "deleteAllTransient");
         if (!this.isConnected) {
            if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "deleteAllTransient"), "Currently not possible. No connection to the DB");
            return 0;
         }

   //      String req = "delete from " + tableName + " where durable='false'";
         String req = "delete from " + this.entriesTableName  + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND durable='F'";
         return update(req);
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         Connection conn = null;
         try {
            conn = this.pool.getConnection();
            if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "deleteAllTransient"), ex))
               throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteAllTransient", "", ex); 
         }
         finally {
            if (conn != null) this.pool.releaseConnection(conn);
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
      if (this.log.CALL) this.log.call(ME, "whereInStatement");
      final String reqPostfix = ")";
      boolean isFirst = true;
      int initialLength = reqPrefix.length() + reqPostfix.length() + 2;
      StringBuffer buf = new StringBuffer();
      int length = initialLength;
      int currentLength = 0;

      ArrayList ret = new ArrayList();

      for (int i=0; i<uniqueIds.length; i++) {
         String req = null;
         String entryId = Long.toString(uniqueIds[i]);
         currentLength = entryId.length();
         length += currentLength;
         if ((length>this.maxStatementLength) || (i == (uniqueIds.length-1))) { // then make the update

            if (i == (uniqueIds.length-1)) {
               if (!isFirst) buf.append(",");
               buf.append(entryId);
            }
            req = reqPrefix + buf.toString() + reqPostfix;
            ret.add(req);

            length = initialLength + currentLength;
            buf = new StringBuffer();

            isFirst = true;
         }

         if (!isFirst) {
            buf.append(",");
            length++;
         }
         else isFirst = false;
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
   public ReturnDataHolder getAndDeleteLowest(StorageId storageId, String nodeId, int numOfEntries, long numOfBytes,
      int maxPriority, long minUniqueId, boolean leaveOne, boolean doDelete) throws XmlBlasterException {

      String queueName = storageId.getStrippedId();
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getAndDeleteLowest"), "Entering");
      ReturnDataHolder ret = new ReturnDataHolder();

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "getAndDeleteLowest"), "Currently not possible. No connection to the DB");
         return ret;
      }

      PreparedQuery query = null;

      try {
         //inverse order here ...
         String req = "select * from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' ORDER BY prio ASC, dataid DESC";
         query = new PreparedQuery(pool, req, false, this.log, -1);

         // process the result set. Give only back what asked for (and only delete that)
         ResultSet rs = query.rs;

         int count = 0;
         long amount = 0L;
//         long maxNumOfBytes = numOfBytes;
         boolean doContinue = true;
         boolean stillEntriesInQueue = false;

// while (iter.hasPrevious() && (count<numOfEntries && (totalSizeInBytes<numOfBytes||numOfBytes<0L) )) {
         while ( (stillEntriesInQueue=rs.next()) && doContinue) {
            long dataId = rs.getLong(1);            // preStatement.setLong(1, dataId);
            /*String nodeId =*/ rs.getString(2);    // preStatement.setString(2, nodeId);
            /*String queueName =*/ rs.getString(3); // preStatement.setString(3, queueName);
            int prio = rs.getInt(4);                // preStatement.setInt(4, prio);
            String typeName = rs.getString(5);      // preStatement.setString(5, typeName);
//            boolean persistent = rs.getBoolean(4); // preStatement.setBoolean(4, persistent);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String persistentAsChar = rs.getString(6);
            boolean persistent = false;
            if ("T".equalsIgnoreCase(persistentAsChar)) persistent = true;

            long sizeInBytes = rs.getLong(7);
            if (!isInsideRange(count, numOfEntries, amount, numOfBytes)) break;
            byte[] blob = rs.getBytes(8); // preStatement.setObject(5, blob);

            // check if allowed or already outside the range ...
            if ((prio<maxPriority) || ((prio==maxPriority)&&(dataId>minUniqueId)) ) {
               if (this.log.DUMP) this.log.dump(getLogId(queueName, nodeId, "getAndDeleteLowest"), "dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " persistent: " + persistent);
               ret.list.add(this.factory.createEntry(prio, dataId, typeName, persistent, sizeInBytes, blob, storageId));
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
               if (this.log.TRACE) this.log.trace(ME, "takeLowest size to delete: "  + entryToDelete.getSizeInBytes());
            }
         }

         if (doDelete) {
            //first strip the unique ids:
            long[] uniqueIds = new long[ret.list.size()];
            for (int i=0; i < uniqueIds.length; i++)
               uniqueIds[i] = ((I_Entry)ret.list.get(i)).getUniqueId();
           
            String reqPrefix = "delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND dataId in(";
            ArrayList reqList = this.whereInStatement(reqPrefix, uniqueIds);
            for (int i=0; i < reqList.size(); i++) {
               req = (String)reqList.get(i);
               if (this.log.TRACE)
                  this.log.trace(getLogId(queueName, nodeId, "getAndDeleteLowest"), "'delete from " + req + "'");
               update(req, query.conn);
            }
         }

         if (!query.conn.getAutoCommit()) query.conn.commit();
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         try {
            query.conn.rollback();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "could not rollback: " + ex.toString());
            ex1.printStackTrace();
         }

         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getAndDeleteLowest"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getAndDeleteLowest", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getAndDeleteLowest", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
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
   public boolean[] deleteEntries(String queueName, String nodeId, long[] uniqueIds)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "deleteEntries"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "deleteEntries"), "Currently not possible. No connection to the DB");
         return new boolean[uniqueIds.length];
      }

      Connection conn = null;
      try {
         int count = 0;
         String reqPrefix = "delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND dataId in(";
         ArrayList reqList = this.whereInStatement(reqPrefix, uniqueIds);

         conn = pool.getConnection();
         if (conn.getAutoCommit()) conn.setAutoCommit(false);

         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            if (this.log.TRACE)
               this.log.trace(getLogId(queueName, nodeId, "deleteEntries"), "'delete from " + req + "'");
            count +=  update(req, conn);
         }
         if (count != uniqueIds.length) conn.rollback();
         else {
            if (!conn.getAutoCommit()) conn.commit();
            boolean[] ret = new boolean[uniqueIds.length];
            for (int i=0; i < ret.length; i++) ret[i] = true;
            return ret;
         }

      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "deleteEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex); 
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex); 
      }
      finally {
         if (conn != null) {
            try {
               if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }
            catch (Throwable ex) {
               this.log.error(ME, "error when setting autocommit to 'true'. reason: " + ex.toString());
               ex.printStackTrace();
            }
            this.pool.releaseConnection(conn);
         }
      }

      boolean[] ret = new boolean[uniqueIds.length];
      for (int i=0; i < uniqueIds.length; i++) {
         ret[i] = deleteEntry(queueName, nodeId, uniqueIds[i]) == 1;
      }
      return ret;
   }


   /**
    * Deletes the entry specified
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public int[] deleteEntriesBatch(String queueName, String nodeId, long[] uniqueIds)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "deleteEntriesBatch"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "deleteEntry"), "Currently not possible. No connection to the DB");
         int[] ret = new int[uniqueIds.length];
         for (int i=0; i < ret.length; i++) ret[i] = 0;
         return ret;
      }

      Connection conn = null;
      PreparedStatement st = null;
      try {
         conn =  this.pool.getConnection();
         if (conn.getAutoCommit()) conn.setAutoCommit(false);
         String req = "delete from " + this.entriesTableName + " where queueName=? AND nodeId=? AND dataId=?";
         st = conn.prepareStatement(req);

         for (int i=0; i < uniqueIds.length; i++) {
            st.setString(1, queueName);
            st.setString(2, nodeId);
            st.setLong(3, uniqueIds[i]);
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
            this.log.error(ME, "could not commit correclty: " + ex1.toString());
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
            this.log.error(ME, "could not rollback correclty: " + ex1.toString());
            ex1.printStackTrace();
         }
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "deleteEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntry", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntry", "", ex); 
      }
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "deleteEntry: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) {
            try {
               if (!conn.getAutoCommit()) conn.setAutoCommit(true);
            }
            catch (Throwable ex1) {
               this.log.error(ME, "could not rollback correclty: " + ex1.toString());
               ex1.printStackTrace();
            }
            this.pool.releaseConnection(conn);
         }
      }
   }


   /**
    * Deletes the entry specified
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public int deleteEntry(String queueName, String nodeId, long uniqueId)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "deleteEntry"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "deleteEntry"), "Currently not possible. No connection to the DB");
         return 0;
      }

      Connection conn = null;
      PreparedStatement st = null;
      try {
         String req = "delete from " + this.entriesTableName + " where queueName=? AND nodeId=? AND dataId=?";
//         String req = "update " + this.entriesTableName + " SET durable='X' where queueName=? AND nodeId=? AND dataId=?";
         conn =  this.pool.getConnection();
         st = conn.prepareStatement(req);
         st.setString(1, queueName);
         st.setString(2, nodeId);
         st.setLong(3, uniqueId);
         return st.executeUpdate();
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(queueName, nodeId, "deleteEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntry", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntry", "", ex); 
      }
      finally {
         try {
            if (st != null) st.close();
         }
         catch (Throwable ex) {
            this.log.warn(ME, "deleteEntry: throwable when closing the connection: " + ex.toString());
         }
         if (conn != null) this.pool.releaseConnection(conn);
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
   public ReturnDataHolder deleteFirstEntries(String queueName, String nodeId, long numOfEntries, long amount)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "deleteFirstEntries called"), "Entering");

      ReturnDataHolder ret = new ReturnDataHolder();
      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "deleteFirstEntries"), "Currently not possible. No connection to the DB");
         return ret;
      }

      if (numOfEntries > Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, getLogId(queueName, nodeId, "deleteFirstEntries"),
               "The number of entries=" + numOfEntries + " to be deleted is too big for this system");

      PreparedQuery query = null;

//      int count = 0;

  //    ArrayList list = null;
      try {
         String req = "select dataId,byteSize from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' ORDER BY prio DESC, dataid ASC";
         query = new PreparedQuery(pool, req, false, this.log, -1);
         // I only want the uniqueId (dataId)
         if (numOfEntries >= Integer.MAX_VALUE)
            throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, getLogId(queueName, nodeId, "deleteFirstEntries"),
                     "The number of entries=" + numOfEntries + " to delete exceeds the maximum allowed byteSize");

         ret = processResultSetForDeleting(query.rs, (int)numOfEntries, amount);

         int nmax = ret.list.size();
         if (nmax < 1) return ret;

         long[] uniqueIds = new long[nmax];
         for (int i=0; i < nmax; i++) uniqueIds[i] = ((Long)ret.list.get(i)).longValue();

         ArrayList reqList = whereInStatement("delete from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND dataId in(", uniqueIds);
         ret.countEntries = 0L;

         // everything in the same transaction (just in case)
         for (int i=0; i < reqList.size(); i++) {
            req = (String)reqList.get(i);
            if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "deleteFirstEntries"), "'" + req + "'");

            ret.countEntries +=  update(req, query.conn);
         }

         return ret;

      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "deleteFirstEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteFirstEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteFirstEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
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
    * @param nodeId the nodeId of the actual cluster 
    * @param numOfEntries the maximum number of elements to retrieve. If negative there is no constriction.
    * @param numOfBytes the maximum number of bytes to retrieve. If negative, there is no constriction.
    * @param minPrio the minimum priority to retreive (inclusive). 
    * @param maxPrio the maximum priority to retrieve (inclusive).
    *
    */
   public ArrayList getEntriesByPriority(StorageId storageId, String nodeId, int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
      throws XmlBlasterException {

      String queueName = storageId.getStrippedId();

      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getEntriesByPriority"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesByPriority"), "Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND prio >= " + minPrio + " and prio <= " + maxPrio +
            " ORDER BY prio DESC, dataid ASC";
                                                                 
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesByPriority"), "Request: '" + req + "'");

      PreparedQuery query =null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesByPriority"), "Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
        if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getEntriesByPriority"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesByPriority", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesByPriority", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
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
   public ArrayList getEntriesBySamePriority( StorageId storageId, String nodeId, int numOfEntries, long numOfBytes)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      // 65 ms (for 10000 msg)
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getEntriesBySamePriority"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(getLogId(queueName, nodeId, "getEntriesBySamePriority"), "Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + "' and nodeId='" + nodeId + 
            "' and prio=(select max(prio) from " + this.entriesTableName + " where queueName='" + queueName + 
            "' AND nodeId='" + nodeId + "')  ORDER BY dataid ASC";

      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesBySamePriority"), "Request: '" + req + "'");

      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesBySamePriority"), "Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getEntriesBySamePriority"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesBySamePriority", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesBySamePriority", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
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
   public ArrayList getEntries(StorageId storageId, String nodeId, int numOfEntries, long numOfBytes)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getEntries"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' ORDER BY prio DESC, dataid ASC";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Request: '" + req + "' wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, storageId, numOfEntries, numOfBytes, false);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Found " + ret.size() + " entries. Wanted limits: numOfEntries="+numOfEntries+" numOfBytes="+numOfBytes);
         return ret;
      }
      catch (SQLException ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }



   /**
    * gets the first numOfEntries of the queue until the limitEntry is reached.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public ArrayList getEntriesWithLimit(StorageId storageId, String nodeId, I_Entry limitEntry)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getEntriesWithLimit"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesWithLimit"), "Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      int limitPrio = limitEntry.getPriority();
      long limitId = limitEntry.getUniqueId();
      String req = "SELECT * from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND dataId < "  + limitId + ") ) ORDER BY prio DESC, dataid ASC";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesWithLimit"), "Request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, -1);
         ArrayList ret = processResultSet(query.rs, storageId, -1, -1L, false);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntriesWithLimit"), "Found " + ret.size() + " entries");
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getEntriesWithLimit"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesWithLimit", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesWithLimit", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }

   /**
    * deletes the first numOfEntries of the queue until the limitEntry is reached.
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public long removeEntriesWithLimit(StorageId storageId, String nodeId, I_Entry limitEntry, boolean inclusive)
      throws XmlBlasterException {
      try {
         String queueName = storageId.getStrippedId();
         if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "removeEntriesWithLimit"), "Entering");
        
         if (!this.isConnected) {
            if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "removeEntriesWithLimit"), "Currently not possible. No connection to the DB");
            return 0L;
         }
        
         int limitPrio = limitEntry.getPriority();
         long limitId = limitEntry.getUniqueId();
        
         String req = null;
         if (inclusive) 
            req = "DELETE from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND dataId <= "  + limitId + ") )";
         else
            req = "DELETE from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND dataId < "  + limitId + ") )";      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "removeEntriesWithLimit"), "Request: '" + req + "'");
         int ret = update(req);
         if (this.log.TRACE) this.log.trace(ME, "removeEntriesWithLimit the result of the request '" + req + "' is : '" + ret + "'");
         return (long)ret;
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeEntriesWithLimit", "", ex); 
      }
   }


   /**
    * gets all the entries which have the dataid specified in the argument list.
    * If the list is empty or null, an empty ArrayList object is returned.
    */
   public ArrayList getEntries(StorageId storageId, String nodeId, long[] dataids)
      throws XmlBlasterException {
      String queueName = storageId.getStrippedId();
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getEntries"), "Entering");
      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      if ((dataids == null) || (dataids.length < 1)) return new ArrayList();

      req = "SELECT * FROM " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND dataid in (";

      ArrayList requests = this.whereInStatement(req, dataids);

      PreparedQuery query = null;
      try {
         req = (String)requests.get(0);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Request: '" + req + "'");
         query = new PreparedQuery(pool, req, this.log, -1);

         ArrayList ret = processResultSet(query.rs, storageId, -1, -1L, false);

         for (int i=1; i < requests.size(); i++) {
            req = (String)requests.get(i);
            if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Request: '" + req + "'");
            query.inTransactionRequest(req /*, -1 */);
            ret.addAll(processResultSet(query.rs, storageId, -1, -1L, false));
         }
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getEntries"), "Found " + ret.size() + " entries");
         return ret;

      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }

   }


   /**
    * gets the real number of entries. that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfEntries(String queueName, String nodeId)
      throws XmlBlasterException {
      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfEntries"), "Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + this.entriesTableName + " WHERE queueName='" + queueName + "' AND nodeId='" + nodeId + "'";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfEntries"), "Request: '" + req + "'");
      PreparedQuery query = null;

      try {
         query = new PreparedQuery(pool, req, true, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfEntries"), "Num=" + ret);
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getNumOfEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfEntries", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the real number of persistent entries, that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfPersistents(String queueName, String nodeId)
      throws XmlBlasterException {

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfPersistents"), "Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND durable='T'";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfPersistent"), "Request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, true, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getNumOfPersistents"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfPersistents", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfPersistents", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * gets the real size of persistent entries, that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getSizeOfPersistents(String queueName, String nodeId)
      throws XmlBlasterException {
      if (this.log.CALL) this.log.call(getLogId(queueName, nodeId, "getSizeOfPersistent"), "Entering");

      if (!this.isConnected) {
         if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getSizeOfPersistent"), "Currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select sum(bytesize) from " + this.entriesTableName + " where queueName='" + queueName + "' AND nodeId='" + nodeId + "' AND durable='T'";
      if (this.log.TRACE) this.log.trace(getLogId(queueName, nodeId, "getNumOfPersistents"), "Request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, true, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(query != null ? query.conn : null, getLogId(queueName, nodeId, "getNumOfPersistents"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfPersistents", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfPersistents", "", ex); 
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Throwable ex1) {
            this.log.error(ME, "exception when closing query: " + ex1.toString());
            ex1.printStackTrace();
         }
      }
   }


   /**
    * @param queueName or null
    * @param nodeId or null
    * @return A nice location string for logging (instead of plain ME)
    */
   private final String getLogId(String queueName, String nodeId, String methodName) {
      StringBuffer sb = new StringBuffer(200);
      sb.append(ME);
      if (this.tableNamePrefix != null) {
         sb.append("-").append(this.tableNamePrefix);
      }
      if (nodeId != null) {
         sb.append("-").append(nodeId);
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
    * This main method can be used to delete all tables on the db which start
    * with a certain prefix. It is useful to cleanup the entire DB.
    * 
    *
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
         glob.wipeOutDB(type, version, null, false);
      }
      catch (Exception ex) {
         System.err.println("Main" + ex.toString());
      }

   }

   public void shutdown() {
      if (this.pool != null) this.pool.shutdown();
   }

}
