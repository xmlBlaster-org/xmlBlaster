/*------------------------------------------------------------------------------
Name:      JdbcManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Global;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.Hashtable;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;

import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_QueueEntryFactory;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.ReturnDataHolder;

import org.xmlBlaster.util.Timestamp;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.TreeSet;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.Iterator;

/**
 * Delegate class which takes care of SQL specific stuff for the JdbcQueuePlugin
 * class.
 *
 *  to define:
 *             Postgres
 *  string
 *  longint
 *  int
 *  blob
 *
 * @author <a href="mailto:ruff@swand.lake.de">Marcel Ruff</a>
 * @author <a href='mailto:laghi@swissinfo.org'>Michele Laghi</a>
 *
 */
public class JdbcManager implements I_ConnectionListener {

   private static final String ME = "JdbcManager";
   private int queueIncrement = 2;
   private LogChannel log = null;
   private JdbcConnectionPool pool = null;
   private I_QueueEntryFactory factory = null;
   private HashSet listener;
   private TreeSet queues;

   private String tableNamePrefix = null;
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

   /**
    * @param JdbcConnectionPool the pool to be used for the connections to
    *        the database. IMPORTANT: The pool must have been previously
    *        initialized.
    */
   public JdbcManager(JdbcConnectionPool pool, I_QueueEntryFactory factory)
      throws XmlBlasterException {
      this.pool = pool;
      Global glob = this.pool.getGlobal();
      this.log = glob.getLog("queue");
      this.log.call(ME, "Constructor called");

      this.factory = factory;

      if (!this.pool.isInitialized())
         throw new XmlBlasterException(ME, "constructor: the Connection pool is not properly initialized");

      // get the necessary metadata
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         this.maxStatementLength = conn.getMetaData().getMaxStatementLength();
         if (this.maxStatementLength < 1) {
            this.log.warn(ME, "constructor: the maximum statement length is not defined, we set it to 2048");
            this.maxStatementLength = 2048;
         }

         if (!conn.getMetaData().supportsTransactions()) {
            String dbName = conn.getMetaData().getDatabaseProductName();
            this.log.error(ME, "the database '" + dbName + "' does not support transactions, unpredicted results may happen");
         }
      }
      catch (SQLException ex) {
         throw new XmlBlasterException(ME, "constructor: failed to get the metadata. Reason: " + ex.getMessage());
      }
      finally {
         if (conn != null) this.pool.releaseConnection(conn);
      }

      Hashtable names = pool.getMapping();
      this.queues = new TreeSet();
      this.listener = new HashSet();

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
      this.queueIncrement = this.pool.getTableAllocationIncrement();

      String errorCodesTxt = (String)names.get("connectionErrorCodes");
      if (errorCodesTxt == null) errorCodesTxt = "1089:17002";

      StringTokenizer tokenizer = new StringTokenizer(errorCodesTxt, ":");
      this.errCodes = new int[tokenizer.countTokens()];
      int nmax = tokenizer.countTokens();
      for (int i=0; i < nmax; i++) {
         try {
            this.errCodes[i] = Integer.parseInt(tokenizer.nextToken().trim());
         }
         catch (Exception ex) {
            this.errCodes[i] = -1;
         }
      }
      if (this.log.DUMP) this.log.dump(ME, "Constructor: num of error codes: "  + nmax);
   }



  /**
   * Adds (registers) a listener for connection/disconnection events.
   */
   public void registerListener(I_ConnectionListener entry) {
      this.listener.add(entry);
   }


  /**
   * Adds (registers) a listener for connection/disconnection events.
   * @return boolean true if the entry was successfully removed.
   */
   public boolean unregisterListener(I_ConnectionListener entry) {
      return this.listener.remove(entry);
   }


   /**
    * @see I_ConnectionListener#disconnected()
    */
   public void disconnected() {
      this.log.call(ME, "disconnected invoked");
      this.isConnected = false;
      Iterator iter = this.listener.iterator();
      while (iter.hasNext()) {
         if (this.isConnected == true) break;
         I_ConnectionListener singleListener = (I_ConnectionListener)iter.next();
         singleListener.disconnected();
      }
   }

   /**
    * @see I_ConnectionListener#reconnected()
    */
   public void reconnected() {
      this.log.call(ME, "reconnected invoked");
      this.isConnected = true;
      Iterator iter = this.listener.iterator();
      while (iter.hasNext()) {
         if (this.isConnected == false) break;
         I_ConnectionListener singleListener = (I_ConnectionListener)iter.next();
         singleListener.reconnected();
      }
   }


   /**
    * @see handleSQLException(String, SQLException)
    */
   protected final boolean handleSQLException(String location, SQLException ex) {
      return handleSQLException(location, ex, null);
   }

   /**
    * handles the SQLException. If it is a communication exception like the
    * connection has been broken it will inform the connection pool.
    * @param location where the exception occured.
    * @param ex the exception which has to be handled.
    * @param trace additional information to put in the logging trace.
    * @return boolean true if it was a communication exception
    * 
    */
   protected final boolean handleSQLException(String location, SQLException ex, String trace) {
      int err = ex.getErrorCode(), i=0;
      boolean ret = false;

      // postgres hack (since postgres does not return error codes other than 0)
      if (ex.getMessage().startsWith("FATAL"))
         ret = true;
      else {
         for (i=0; i < this.errCodes.length; i++) {
            if (this.errCodes[i] == err) break;
         }
         ret = (i < this.errCodes.length);
      }

      this.log.dump(ME, location + ": error code     : " + ex.getErrorCode());
      this.log.dump(ME, location + ": SQL state      : " + ex.getSQLState());
      if (trace != null && trace.length() > 0) {
         if (this.log.TRACE) this.log.trace(ME, location + ": additional info: " + trace);
      }

      if (ret) {
         this.log.error(ME, location + ": the connection to the DB has been lost. Going in polling modus");
         this.pool.setConnectionLost();
      }
      return ret;
   }


   /**
    * It searches first in the USEDTABLES for an entry. If none is found
    * a free table is associated to this queue. It also registers the queue
    * so to keep track when to cleanup.
    *
    */
   public final String getTable(String queueName, long capacity)
      throws SQLException, XmlBlasterException {

      this.log.call(ME, "getTable");
      if (!this.isConnected)
         throw new XmlBlasterException(ME, "getTable:the DB disconnected. Handling queue '" + queueName + "' is currently not possible");

      String associatedTableName = null;
      PreparedQuery query = null;

      String req = "SELECT tablename from " + this.tableNamePrefix + "USEDTABLES where queuename='" + queueName + "'";
      if (this.log.TRACE) this.log.trace(ME, "getTable: request: '" + req + "'");

      try {

         // check if a table already exists ...
         query = new PreparedQuery(this.pool, req, true, this.log, -1);
         boolean tableExists = query.rs.next();

         if (tableExists) {
            if (this.log.TRACE) this.log.trace(ME, "the table " + this.tableNamePrefix + "USEDTABLES exists");
            associatedTableName = query.rs.getString(1);
            query.close();
            return associatedTableName;
         }
         if (this.log.TRACE) this.log.trace(ME, "the table " + this.tableNamePrefix + "USEDTABLES does not exist");
         query.close();

         // ok none found, lets associate a new one ...
         if (this.log.CALL)
            this.log.call(ME, "getTable invoked on " + queueName + " with capacity " + capacity);
         req = "SELECT tableName from " + this.tableNamePrefix + "FREETABLES";
         query = new PreparedQuery(this.pool, req, false, this.log, 1);

         boolean freeTablesAvailable =query.rs.next();
         if (this.log.TRACE) this.log.trace(ME, "freetables available: " + freeTablesAvailable);
         if (!freeTablesAvailable) {
//            query.close();
            this.log.trace(ME, "no free tables: adding new tables");
            addFreeTables(this.queueIncrement, query.conn);
//            query = new PreparedQuery(this.pool, req, false, this.log, -1);
            query.inTransactionRequest(req /*, -1 */);
            query.rs.next();
         }
         associatedTableName = query.rs.getString(1);

         req = "DELETE FROM " + this.tableNamePrefix + "FREETABLES where tablename='" + associatedTableName + "'";
         if (this.log.TRACE) this.log.trace(ME, "getTable: request: '" + req + "'");
         update(req, query.conn);

         req = "INSERT INTO " + this.tableNamePrefix + "USEDTABLES VALUES ('" + queueName + "','" + associatedTableName + "'," + capacity + ")";         if (this.log.TRACE) this.log.trace(ME, "getTable: request: '" + req + "'");
         update(req, query.conn);
         this.queues.add(queueName);
         return associatedTableName;
      }
      catch (SQLException ex) {
         handleSQLException("getTable", ex);
         throw ex;
      }
      finally {
         if (query !=null) query.close();
      }

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
   public long getNumOfBytes(String tableName)
      throws SQLException, XmlBlasterException {

      this.log.call(ME, "getNumOfBytes");
      if (!this.isConnected)
         throw new XmlBlasterException(ME, "getNumOfBytes: the DB disconnected. Handling table '" + tableName + "' is currently not possible");

      Connection conn = null;
      PreparedStatement st = null;

      try {
         String req = "SELECT sum(byteSize) from " + tableName;
         conn = this.pool.getConnection();
         st = conn.prepareStatement(req);
         ResultSet rs = st.executeQuery();
         rs.next();
         return rs.getLong(1);
      }
      catch (SQLException ex) {
         handleSQLException("getNumOfBytes", ex);
         throw ex;
      }
      finally {
         if (st != null) st.close();
         if (conn != null) this.pool.releaseConnection(conn);
      }
   }


   /**
    * releases a table, i.e. it makes it available to the system to be
    * associated to another queue.
    *
    */
   public final long releaseTable(String queueName, String tableName)
      throws SQLException, XmlBlasterException {
      if (this.log.CALL)
         this.log.call(ME, "releaseTable invoked for " + queueName + " in table " + tableName);

      if (!this.isConnected)
         throw new XmlBlasterException(ME, "releaseTable: the DB disconnected. Handling queue '" + queueName + "' is currently not possible");

      String req = "DELETE FROM " + this.tableNamePrefix + "USEDTABLES where queueName='" + queueName + "'";
      if (this.log.TRACE) this.log.trace(ME, "releaseTable: request: '" + req + "'");

      Connection conn = null;

      try {
         conn = this.pool.getConnection();
         conn.setAutoCommit(false);
         int ret = update(req, conn);

         req = "DELETE FROM " + tableName;
         if (this.log.TRACE) this.log.trace(ME, "releaseTable: request: '" + req + "'");
         update(req, conn);

         req = "INSERT INTO " + this.tableNamePrefix + "FREETABLES VALUES ('" + tableName + "')";
         if (this.log.TRACE) this.log.trace(ME, "releaseTable: request: '" + req + "'");
         update(req, conn);
         conn.commit();
         return (long)ret;
      }
      catch (XmlBlasterException ex) {
         if (conn != null) conn.rollback();
         throw ex;
      }
      catch (SQLException ex) {
         if (conn != null) conn.rollback();
         handleSQLException("releaseTable", ex);
         throw ex;
      }
      finally {
         if (conn !=null) {
            conn.setAutoCommit(true);
            if (conn!= null) this.pool.releaseConnection(conn);
         }
      }
   }

   /**
    * creates the initial tables
    */
   private final boolean createInitialTables()
      throws SQLException, XmlBlasterException {
      this.log.call(ME, "createInitialTables");

      if (!this.isConnected)
         throw new XmlBlasterException(ME, "createInitialTables: the DB disconnected. Handling is currently not possible");

      boolean freetablesExists = false;
      boolean usedtablesExists = false;

      String req = "SELECT count(*) from " + tablesTxt + " where upper(" + tableNameTxt + ")='" + this.tableNamePrefix + "FREETABLES'";
      if (this.log.TRACE) this.log.trace(ME, "createInitialTables: request: '" + req + "'");

      PreparedQuery query = null;

      try {
         query = new PreparedQuery(pool, req, false, this.log, -1);
         query.rs.next();
         int size = query.rs.getInt(1);
         if (size > 0) freetablesExists = true;

         req = "SELECT count(*) from " + tablesTxt + " where upper(" + tableNameTxt + ")='" + this.tableNamePrefix + "USEDTABLES'";
         if (this.log.TRACE) this.log.trace(ME, "createInitialTables: request: '" + req + "'");

         query.inTransactionRequest(req /*, -1*/);
         query.rs.next();
         size = query.rs.getInt(1);

         if (size > 0) usedtablesExists = true;

         if (!freetablesExists) {
            req = "CREATE TABLE " + this.tableNamePrefix + "FREETABLES (tableName  " + stringTxt + " PRIMARY KEY)";
            if (this.log.TRACE) this.log.trace(ME, "createInitialTables: request: '" + req + "'");
            this.log.trace(ME, "createQueueTable: " + req);
            update(req, query.conn);
         }

         if (!usedtablesExists) {
            req = "CREATE TABLE " + this.tableNamePrefix + "USEDTABLES (queueName  " + stringTxt + " PRIMARY KEY, tableName " + stringTxt +
                ", capacity  " + longintTxt + ")";
            if (this.log.TRACE) this.log.trace(ME, "createInitialTables: request: '" + req + "'");
            this.log.trace(ME, "createQueueTable: " + req);
            update(req, query.conn);
         }

         addFreeTables(queueIncrement, query.conn);
         return !freetablesExists;
      }
      catch (SQLException ex) {
         handleSQLException("createInitialTables", ex, "SQL request giving problems: " + req);
         throw ex;
      }
      finally {
         if (query !=null) query.close();
      }
   }

   /**
    *
    */
   private final int addFreeTables(int numOfTables, Connection conn)
      throws SQLException, XmlBlasterException {
      try {
         if (this.log.CALL)
            log.call(ME, "addFreeTables will create " + numOfTables + " tables");

         if (!this.isConnected)
            throw new XmlBlasterException(ME, "addFreeTables: the DB disconnected. Handling is currently not possible");

         for (int i = 0; i < numOfTables; i++) {
            Timestamp timestamp = new Timestamp();
            String tableName = this.tableNamePrefix + timestamp.getTimestamp();
            createQueueTable(tableName, conn);
            String req = "INSERT INTO " + this.tableNamePrefix + "FREETABLES VALUES ('" + tableName + "')";
            if (this.log.TRACE) this.log.trace(ME, "addFreeTables: request: '" + req + "'");
            update(req, conn);
         }
         return numOfTables;
      }
      catch (SQLException ex) {
         handleSQLException("addFreeTables", ex);
         throw ex;
      }
   }

   /**
    * Sets up a table for the queue specified by this queue name.
    * If one already exists (i.e. if it recovers from a crash) its associated
    * table name is returned. If no such queue is found among the used table,
    * a new table is taken from the free tables. If no free tables are available,
    * a certain amount of such tables are created.
    */
   public final void setUp() throws SQLException, XmlBlasterException {
      this.log.call(ME, "setUp");

      try {
         if (!this.dbInitialized) {
            this.log.trace(ME, "initializing the first time the pool");
            createInitialTables();
            this.dbInitialized = true;
         }
      }
      catch (SQLException ex) {
         handleSQLException("setUp", ex, "table name giving Problems");
         throw ex;
      }
   }


   private final ArrayList processResultSet(ResultSet rs, I_Queue queue, int numOfEntries, long numOfBytes, boolean onlyId)
      throws SQLException, XmlBlasterException {

      this.log.call(ME,"processResultSet invoked");

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
            long dataId = rs.getLong(1); // preStatement.setLong(1, dataId);
            int prio = rs.getInt(2); // preStatement.setInt(2, prio);
            String typeName = rs.getString(3); // preStatement.setString(3, typeName);
//            boolean durable = rs.getBoolean(4); // preStatement.setBoolean(4, durable);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String durableAsChar = rs.getString(4);
            boolean durable = false;
            if ("T".equalsIgnoreCase(durableAsChar)) durable = true;

            long sizeInBytes = rs.getLong(5);
            byte[] blob = rs.getBytes(6); // preStatement.setObject(5, blob);

            if ( (numOfBytes < 0) || (sizeInBytes+amount < numOfBytes) || (count == 0)) {
               if (this.log.DUMP)
                  this.log.dump(ME, "processResultSet: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " isDurable: " + durable);
               entries.add(this.factory.createEntry(prio, dataId, typeName, durable, blob, queue));
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
//            boolean durable = rs.getBoolean(4); // preStatement.setBoolean(4, durable);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String durableAsChar = rs.getString(4);
            boolean durable = false;
            if ("T".equalsIgnoreCase(durableAsChar)) durable = true;

            long sizeInBytes = rs.getLong(5);
            byte[] blob = rs.getBytes(6); // preStatement.setObject(5, blob);

            if (this.log.DUMP)
               this.log.dump(ME, "processResultSet: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " isDurable: " + durable);
            entries.add(this.factory.createEntry(prio, dataId, typeName, durable, blob, queue));
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

      this.log.call(ME,"processResultSetForDeleting invoked");
      ReturnDataHolder ret = new ReturnDataHolder();
      ret.list = new ArrayList();
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
      this.log.call(ME, "update called with " + request);

      Connection conn = null;
      Statement  statement = null;
      int ret = 0;

      try {
         conn = pool.getConnection();
         if (this.log.TRACE) this.log.call(ME, "update: creating statement");
         statement = conn.createStatement();
         if (this.log.TRACE) this.log.call(ME, "update: executing statement");
         ret = statement.executeUpdate(request);
         if (this.log.TRACE) this.log.call(ME, "update: executed statement");
      }
      finally {
         if (this.log.TRACE) this.log.call(ME, "update: closing statement");
         if (statement !=null) statement.close();
         if (this.log.TRACE) this.log.call(ME, "update: releasing connection");
         if (conn != null) this.pool.releaseConnection(conn);
         if (this.log.TRACE) this.log.call(ME, "update: connection released");
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

      if (this.log.CALL)
         this.log.call(ME, "update called with " + request + " and connection " + conn);

      Statement  statement = null;
      int ret = 0;

      try {
         statement = conn.createStatement();
         ret = statement.executeUpdate(request);
      }

      finally {
         if (statement != null) statement.close();
      }

      return ret;
   }


   /**
    * deletes all transient messages
    */
   public int deleteAllTransient(String tableName) throws XmlBlasterException, SQLException {
      try {
         this.log.call(ME, "deleteAllTransient");
         if (!this.isConnected) {
            if (this.log.TRACE)
               this.log.trace(ME, "deleteAllTransient. for " + tableName + " currently not possible. No connection to the DB");
            return 0;
         }

   //      String req = "delete from " + tableName + " where durable='false'";
         String req = "delete from " + tableName + " where durable='F'";
         return update(req);
      }
      catch (SQLException ex) {
         handleSQLException("deleteAllTransient", ex);
         throw ex;
      }
   }


   /**
    * The prefix is the initial part of the SQL update/query. Note that this
    * method can be used both for SELECT statements as for updates such as
    * DELETE or UPDATE.
    * An example of prefix:
    * "delete from tableName where dataId in(";
    */
   private ArrayList whereInStatement(String reqPrefix, long[] uniqueIds) {
      this.log.call(ME, "whereInStatement");
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
    *
    * Adds a row to the specified queue table
    * @param tableName The name of the table on which to perform the operation
    * @param dataId The long specifying the unique Id of this entry in the queue
    * @param prio The priority of this entry
    * @param flag The flag which specifies which kind of object is stored in the blob
    * @param blob An object (must be Serializable) to store in the DB.
    *
    * @throws SQLException if an error occured while adding the row
    * @throws XmlBlasterException if an error occured when trying to get a connection
    */
   public boolean addEntry(String tableName, I_QueueEntry entry)
      throws SQLException, XmlBlasterException {

      if (this.log.CALL)
         this.log.call(ME, "addition called for " + tableName);

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "addEntry. On '" + tableName + "' for entry '" + entry.getUniqueId() + "' currently not possible. No connection to the DB");
         return false;
      }

      long dataId = entry.getUniqueId();
      int prio = entry.getPriority();
      byte[] blob = this.factory.toBlob(entry);
      String typeName = entry.getEmbeddedType();
      boolean durable = entry.isDurable();
      long sizeInBytes = entry.getSizeInBytes();

      if (this.log.DUMP)
         this.log.dump(ME, "addition. dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + ", byteSize in bytes: " + sizeInBytes);

      PreparedStatement preStatement = null;
      boolean ret = false;
      Connection conn = null;
      try {
         conn = this.pool.getConnection();
         String req = "INSERT INTO " + tableName + " VALUES ( ?, ?, ?, ?, ?, ?)";
         if (this.log.TRACE) this.log.trace(ME, req);

         preStatement = conn.prepareStatement(req);
         preStatement.setLong(1, dataId);
         preStatement.setInt(2, prio);
         preStatement.setString(3, typeName);
//         preStatement.setBoolean(4, durable);
         if (durable == true) preStatement.setString(4, "T");
         else preStatement.setString(4, "F");
         preStatement.setLong(5, sizeInBytes);
         preStatement.setBytes(6, blob);

         if (this.log.TRACE) this.log.trace(ME, preStatement.toString());

         preStatement.executeUpdate();
         ret = true;
      }
      catch (SQLException ex) {
         this.log.warn(ME, "could not insert entry '" +  entry.getUniqueId() + "' probably because already in DB");
         if (handleSQLException("addEntry", ex)) throw ex;
         ret = false;
      }
      finally {
         if (preStatement != null) preStatement.close();
         if (conn != null) this.pool.releaseConnection(conn);
      }

      return ret;
   }


   /**
    * Under the same transaction it gets and deletes all the entries which fit
    * into the constrains specified in the argument list.
    * @see I_Queue#takeLowest(int, long, int, long)
    */
   public ReturnDataHolder getAndDeleteLowest(String tableName, I_Queue queue, int numOfEntries, long numOfBytes,
      int maxPriority, long minUniqueId) throws XmlBlasterException, SQLException {

      this.log.call(ME, "getAndDeleteLowest called");
      ReturnDataHolder ret = new ReturnDataHolder();
      ret.list = new ArrayList();

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getAndDeleteLowest. On table '" + tableName + "' currently not possible. No connection to the DB");
         return ret;
      }

      PreparedQuery query = null;

      try {
         //inverse order here ...
         String req = "select * from " + tableName + " ORDER BY prio ASC, dataid DESC";
         query = new PreparedQuery(pool, req, false, this.log, -1);

         // process the result set. Give only back what asked for (and only delete that)
         ResultSet rs = query.rs;

         int count = 0;
         long amount = 0L;
//         long maxNumOfBytes = numOfBytes;
         boolean doContinue = true;
         boolean stillEntriesInQueue = false;

         while ( (stillEntriesInQueue=rs.next()) && ((count < numOfEntries) || (numOfEntries < 0)) &&
            (doContinue)) {

            long dataId = rs.getLong(1); // preStatement.setLong(1, dataId);
            int prio = rs.getInt(2); // preStatement.setInt(2, prio);
            String typeName = rs.getString(3); // preStatement.setString(3, typeName);
//            boolean durable = rs.getBoolean(4); // preStatement.setBoolean(4, durable);
            //this only to make ORACLE happy since it does not support BOOLEAN
            String durableAsChar = rs.getString(4);
            boolean durable = false;
            if ("T".equalsIgnoreCase(durableAsChar)) durable = true;

            long sizeInBytes = rs.getLong(5);
            byte[] blob = rs.getBytes(6); // preStatement.setObject(5, blob);

            // check if allowed or already outside the range ...
            if (((numOfBytes<0)||(sizeInBytes+amount<numOfBytes)||(count==0)) &&
               ((prio<maxPriority) || ((prio==maxPriority)&&(dataId>minUniqueId)) )) {
               if (this.log.DUMP)
                  this.log.dump(ME, "getAndDeleteLowest: dataId: " + dataId + ", prio: " + prio + ", typeName: " + typeName + " isDurable: " + durable);
               ret.list.add(this.factory.createEntry(prio, dataId, typeName, durable, blob, queue));
               amount += sizeInBytes;
               if (amount > numOfBytes) doContinue = false;
               if (numOfBytes < 0) doContinue = true;
            }
            else doContinue = false;
            count++;
         }

         ret.countBytes = amount;
         ret.countEntries = count;

//         numOfBytes = new Long(amount); // the return for the sizes in bytes
         // prepare for deleting (we don't use deleteEntries since we want
         // to use the same transaction (and the same connection)

         // leave at least one entry
         if (stillEntriesInQueue) stillEntriesInQueue = rs.next();
         if ((!stillEntriesInQueue) && (ret.list.size()>0))
            ret.list.remove(ret.list.size()-1);
         //first strip the unique ids:
         long[] uniqueIds = new long[ret.list.size()];
         for (int i=0; i < uniqueIds.length; i++)
            uniqueIds[i] = ((I_QueueEntry)ret.list.get(i)).getUniqueId();

         String reqPrefix = "delete from " + tableName + " where dataId in(";
         ArrayList reqList = this.whereInStatement(reqPrefix, uniqueIds);
         for (int i=0; i < reqList.size(); i++) {
            req = (String)reqList.get(i);
            if (this.log.TRACE)
               this.log.trace(ME, "deleteEntries, req: 'delete from " + req + "'");
            update(req, query.conn);
         }

         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getAndDeleteLowest", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }



   /**
    * Deletes the entries specified by the entries array. Since all entries
    * are deleted at the same time, in case of an exception the result
    * is uncertain (it depends on what the used database will do). In case the
    * statement would become too long, several statements are invoked.
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public int deleteEntries(String tableName, long[] uniqueIds)
      throws XmlBlasterException, SQLException {
     this.log.call(ME, "deleteEntries");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "deleteEntries. On table '" + tableName + "' currently not possible. No connection to the DB");
         return 0;
      }

      try {
         int count = 0;
         String reqPrefix = "delete from " + tableName + " where dataId in(";
         ArrayList reqList = this.whereInStatement(reqPrefix, uniqueIds);

         for (int i=0; i < reqList.size(); i++) {
            String req = (String)reqList.get(i);
            if (this.log.TRACE)
               this.log.trace(ME, "deleteEntries, req: 'delete from " + req + "'");

            count +=  update(req);
         }
         return count;
      }
      catch (SQLException ex) {
         handleSQLException("deleteEntries", ex);
         throw ex;
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
   public ReturnDataHolder deleteFirstEntries(String tableName, long numOfEntries, long amount)
      throws XmlBlasterException, SQLException {
      this.log.call(ME, "deleteFirstEntries called");

      ReturnDataHolder ret = new ReturnDataHolder();
      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "deleteFirstEntries. On table '" + tableName + "' currently not possible. No connection to the DB");
         return ret;
      }

      if (numOfEntries > Integer.MAX_VALUE) throw new XmlBlasterException(ME, "The number of entries to be deleted is too big for this system");

      PreparedQuery query = null;

//      int count = 0;

  //    ArrayList list = null;
      try {
         String req = "select dataId,byteSize from " + tableName + " ORDER BY prio DESC, dataid ASC";
         query = new PreparedQuery(pool, req, false, this.log, -1);
         // I only want the uniqueId (dataId)
         if (numOfEntries >= Integer.MAX_VALUE)
            throw new XmlBlasterException(ME, "deleteFirstEntries: the number of entries to delete exceeds the maximum allowed byteSize");

         ret = processResultSetForDeleting(query.rs, (int)numOfEntries, amount);

         int nmax = ret.list.size();
         if (nmax < 1) return ret;

         long[] uniqueIds = new long[nmax];
         for (int i=0; i < nmax; i++) uniqueIds[i] = ((Long)ret.list.get(i)).longValue();

         ArrayList reqList = whereInStatement("delete from " + tableName + " where dataId in(", uniqueIds);
         ret.countEntries = 0L;

         // everything in the same transaction (just in case)
         for (int i=0; i < reqList.size(); i++) {
            req = (String)reqList.get(i);
            if (this.log.TRACE) this.log.trace(ME, "deleteFirstEntries, req: '" + req + "'");

            ret.countEntries +=  update(req, query.conn);
         }

         return ret;

      }
      catch (SQLException ex) {
         handleSQLException("deleteFirstEntries", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }


   /**
    * gets the first numOfEntries of the queue which have the priority in the
    * range specified by prioMin and prioMax (inclusive).
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    *
    */
   public ArrayList getEntriesByPriority(int numOfEntries, long numOfBytes, int minPrio, int maxPrio, String tableName, I_Queue queue)
      throws XmlBlasterException, SQLException {

      this.log.call(ME, "getEntriesByPriority");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getEntriesByPriority.  Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + tableName + " where prio >= " + minPrio + " and prio <= " + maxPrio +
            " ORDER BY prio DESC, dataid ASC";

      if (this.log.TRACE) this.log.trace(ME, "getEntriesByPriority: request: '" + req + "'");

      PreparedQuery query =null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, queue, numOfEntries, numOfBytes, false);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getEntriesByPriority", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
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
   public ArrayList getEntriesBySamePriority(int numOfEntries, long numOfBytes, String tableName, I_Queue queue)
      throws XmlBlasterException, SQLException {
      // 65 ms (for 10000 msg)
      this.log.call(ME, "getEntriesBySamePriority");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getEntriesBySamePriority.  Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      req = "SELECT * from " + tableName + " where prio=(select max(prio) from " + tableName +
         ")  ORDER BY dataid ASC";

      if (this.log.TRACE) this.log.trace(ME, "getEntriesBySamePriority: request: '" + req + "'");

      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, queue, numOfEntries, numOfBytes, false);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getEntriesBySamePriority", ex);
         throw ex;
      }
      finally {
         if(query != null) query.close();
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
   public ArrayList getEntries(int numOfEntries, long numOfBytes, String tableName, I_Queue queue)
      throws XmlBlasterException, SQLException {
      this.log.call(ME, "getEntries");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getEntries.  Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = "SELECT * from " + tableName + " ORDER BY prio DESC, dataid ASC";
      if (this.log.TRACE) this.log.trace(ME, "getEntries: request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, numOfEntries);
         ArrayList ret = processResultSet(query.rs, queue, numOfEntries, numOfBytes, false);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getEntries", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }



   /**
    * gets the first numOfEntries of the queue until the limitEntry is reached.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public ArrayList getEntriesWithLimit(String tableName, I_Queue queue, I_QueueEntry limitEntry)
      throws XmlBlasterException, SQLException {
      this.log.call(ME, "getEntriesWithLimit");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getEntriesWithLimit.  Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      int limitPrio = limitEntry.getPriority();
      long limitId = limitEntry.getUniqueId();
      String req = "SELECT * from " + tableName + " WHERE (prio > " + limitPrio + " OR (prio = " + limitPrio + " AND dataId < "  + limitId + ") ) ORDER BY prio DESC, dataid ASC";
      if (this.log.TRACE) this.log.trace(ME, "getEntries: request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, -1);
         ArrayList ret = processResultSet(query.rs, queue, -1, -1L, false);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getEntries", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }





   /**
    * gets all the entries which have the dataid specified in the argument list.
    * If the list is empty or null, an empty ArrayList object is returned.
    */
   public ArrayList getEntries(long[] dataids, String tableName, I_Queue queue)
      throws XmlBlasterException, SQLException {
      this.log.call(ME, "getEntries");
      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getEntries.  Currently not possible. No connection to the DB");
         return new ArrayList();
      }

      String req = null;
      if ((dataids == null) || (dataids.length < 1)) return new ArrayList();

      req = "SELECT * FROM " + tableName + " WHERE dataid in (";

      ArrayList requests = this.whereInStatement(req, dataids);

      PreparedQuery query = null;
      try {
         req = (String)requests.get(0);
         if (this.log.TRACE) this.log.trace(ME, "getEntries: request: '" + req + "'");
         query = new PreparedQuery(pool, req, this.log, -1);

         ArrayList ret = processResultSet(query.rs, queue, -1, -1L, false);

         for (int i=1; i < requests.size(); i++) {
            req = (String)requests.get(i);
            if (this.log.TRACE) this.log.trace(ME, "getEntries: request: '" + req + "'");
            query.inTransactionRequest(req /*, -1 */);
            ret.addAll(processResultSet(query.rs, queue, -1, -1L, false));
         }
         return ret;

      }
      catch (SQLException ex) {
         handleSQLException("getEntries", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }

   }


   /**
    * gets the real number of entries. that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfEntries(String tableName)
      throws SQLException, XmlBlasterException {
      this.log.call(ME, "size");
      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "size.  on table '" + tableName + "' currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + tableName + "";
      if (this.log.TRACE) this.log.trace(ME, "size: request: '" + req + "'");
      PreparedQuery query = null;

      try {
         query = new PreparedQuery(pool, req, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getEntriesBySamePriority", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }


   /**
    * gets the real number of durable entries, that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getNumOfDurables(String tableName)
      throws SQLException, XmlBlasterException {
      this.log.call(ME, "getNumOfDurable");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getNumOfDurables.  on table '" + tableName + "' currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select count(*) from " + tableName + " where durable='T'";
      if (this.log.TRACE) this.log.trace(ME, "getNumOfDurable: request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getNumOfDurables", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }


   /**
    * gets the real size of durable entries, that is it really makes a call to the DB to find out
    * how big the size is.
    */
   public final long getSizeOfDurables(String tableName)
      throws SQLException, XmlBlasterException {
      this.log.call(ME, "getSizeOfDurable");

      if (!this.isConnected) {
         if (this.log.TRACE)
            this.log.trace(ME, "getSizeOfDurable.  on table '" + tableName + "' currently not possible. No connection to the DB");
         return 0L;
      }

      String req = "select sum(bytesize) from " + tableName + " where durable='T'";
      if (this.log.TRACE) this.log.trace(ME, "getNumOfDurable: request: '" + req + "'");
      PreparedQuery query = null;
      try {
         query = new PreparedQuery(pool, req, this.log, -1);
         query.rs.next();
         long ret = query.rs.getLong(1);
         return ret;
      }
      catch (SQLException ex) {
         handleSQLException("getNumOfDurables", ex);
         throw ex;
      }
      finally {
         if (query != null) query.close();
      }
   }


   /**
    * Creates a table with the given name. The table may later be used to
    * store the entries of the queue.
    * @tableName the name of the table
    */
   private boolean createQueueTable(String tableName, Connection conn)
      throws SQLException, XmlBlasterException {

      this.log.call(ME, "createQueueTable called for " + tableName);
      if (!this.isConnected) {
         throw new XmlBlasterException(ME, "createQueueTable. No Connection to the DB. Could not create table '" + tableName + "'");
      }

      String req = "CREATE TABLE " + tableName + " (" + "dataId  " + longintTxt + " PRIMARY KEY, prio " + intTxt +
          ", flag " + stringTxt + ", durable " + booleanTxt + ", byteSize " + longintTxt + ", blob " + blobTxt + ")";

      if (this.log.TRACE) this.log.trace(ME, "createQueueTable: request: '" + req + "'");

      try {
         int ret = update(req, conn);
         if (ret > 0) return true;
         else  return false;
      }
      catch (SQLException ex) {
         handleSQLException("createQueueTable", ex);
         throw ex;
      }

   }


   /**
    * @see JdbcManager#cleanUp(String, boolean)
    * no forcing used.
    */
   public final int cleanUp(String queueName) throws XmlBlasterException {
      return cleanUp(queueName, false);
   }

   /**
    * Cleans up the entire DB. It returns the number of tables deleted.
    * Deleting the entire DB means delete the following tables:
    *
    * FREETABLES
    * USEDTABLES
    * XMLBLASTER*
    *
    * where 'XMLBLASTER*' means all tables starting with the substring XMLBLASTER.
    * IMPORTANT: If you invoke this method you must be sure that there are no
    * other users who have created tables with the name starting with the
    * string XMLBLASTER.
    *  @param queueName the name of the queue to clean up.
    *  @force the flag telling if removing everything anyway
    * @return the number of queues still remaining to be cleaned up
    */
   public final int cleanUp(String queueName, boolean force)
      throws XmlBlasterException {

      this.log.call(ME, "cleanUp");

      if (!this.isConnected) {
         throw new XmlBlasterException(ME, "cleanUp. No Connection to the DB. Could not clean up queue '" + queueName + "'");
      }


      this.queues.remove(queueName);
      if ((!force) && (this.queues.size()>0)) return this.queues.size();

      if (!this.pool.isInitialized()) {
         this.log.trace(ME, "cleanUp: already cleaned up, not doing anything");
         return 0;
      }

      int count = 0;
      XmlBlasterException firstEx = null;

      // retrieve all tables to delete
      String req = "SELECT " + this.tableNameTxt + " FROM " + this.tablesTxt + "";
      if (this.log.TRACE) this.log.trace(ME, "cleanUp: request: '" + req + "'");
      PreparedQuery query = null;

      java.util.Vector vec = new java.util.Vector();
      try {
         query = new PreparedQuery(this.pool, req, this.log, -1);
         while (query.rs.next()) {
            String nameOfThisQueue = query.rs.getString(1).trim();
            if (nameOfThisQueue.toUpperCase().startsWith(this.tableNamePrefix)) vec.add(nameOfThisQueue);
         }
      }
      catch (SQLException ex) {
         handleSQLException("cleanUp", ex);
         throw new XmlBlasterException(ME, "cleanUp: SQLException when retrieving the list of tables");
      }
      finally {
         try {
            if (query != null) query.close();
         }
         catch (Exception ex) {
            throw new XmlBlasterException(ME, "cleanUp: exception when closing the query " + ex.getMessage());
         }
      }

      // delete (drop) all tables found which match our conventions
      for (int i=0; i < vec.size(); i++) {
         String name = (String)vec.elementAt(i);
         req = "DROP TABLE " + name + "";
         if (this.log.TRACE) this.log.trace(ME, "cleanUp: request: '" + req + "'");
         try {
            this.update(req);
            count++;
         }
         catch (Exception ex) {
            if (ex instanceof SQLException)
               handleSQLException("createQueueTable", (SQLException)ex);
            this.log.error(ME, "could not delete queue '" + name + "'");
         }
      }

      // free DB resources
      if (this.log.TRACE) this.log.trace(ME, "cleanUp: disconnecting the pool");
      this.pool.disconnect();
      this.dbInitialized = false;
      return 0;
   }


   /**
    * This main method can be used to delete all tables on the db which start
    * with a certain prefix. It is useful to cleanup the entire DB.
    *
    * IMPORTANT: caution must be used to avoid to delete the wrong data.
    */
   public static void main(String[] args) {
      Global glob = Global.instance();
      glob.init(args);
      String prefix = null;

      java.io.InputStreamReader reader = new java.io.InputStreamReader(System.in);
      java.io.BufferedReader buff = new java.io.BufferedReader(reader);

      String queueId = "dummy";
      System.out.println("DANGER DANGER DANGER. BE CAREFUL NOT TO DELETE THE");
      System.out.println("WRONG DATA.");
      System.out.println("");
      System.out.print("- Input a prefix (all tables found starting with the prefix specified will be deleted: ");

      try {

         prefix = buff.readLine();
         prefix = prefix.toUpperCase();

         if (prefix == null || prefix.length() < 1) {
            System.out.println("The prefix is empty. Not doing anything");
            System.exit(-1);
         }

         glob.getProperty().set("cb.queue.persistent.tableNamePrefix", prefix);
         System.out.println("when deleting the DB I will be using the settings for the callback queue (see them in xmlBlaster.properties)");
         JdbcManager manager = glob.getJdbcQueueManager("cb:dummy");
         manager.cleanUp("dummy", true);

      }
      catch (Exception ex) {
         ex.printStackTrace();
      }

   }

}
