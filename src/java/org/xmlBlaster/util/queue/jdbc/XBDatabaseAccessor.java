/*------------------------------------------------------------------------------
Name:      XBDatabaseAccessor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue.jdbc;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StorageProblemListener;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;
import org.xmlBlaster.util.queue.QueuePluginManager;
import org.xmlBlaster.util.queue.ReturnDataHolder;
import org.xmlBlaster.util.queue.StorageId;


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
 * @author <a href='mailto:michele@laghi.eu'>Michele Laghi</a>
 *
 */
public class XBDatabaseAccessor extends XBFactoryBase implements I_StorageProblemListener, I_StorageProblemNotifier {

   
   private class QueueGlobalInfo extends GlobalInfo {

      private String poolId;
      public QueueGlobalInfo() {
         super((String[])null);
      }

      // @Override
      protected void doInit(Global glob, PluginInfo plugInfo) throws XmlBlasterException {
         Properties props = plugInfo.getParameters();
         
         String size = props.getProperty("connectionPoolSize");
         if (size != null)
            put("db.maxInstances", size);
         // has currently no effect
         String busyTimeout = props.getProperty("connectionBusyTimeout");
         if (busyTimeout != null) {
            put("db.busyToIdleTimeout", busyTimeout);
            put("db.idleToEraseTimeout", busyTimeout);
         }
         String url = props.getProperty("url");
         String user = props.getProperty("user");
         String password = props.getProperty("password");
         if (url != null)
            put("db.url", url);
         if (user != null)
            put("db.user", user);
         if (password != null)
            put("db.password", password);
         
         String tmp = props.getProperty("maxResourceExhaustRetries");
         if (tmp != null)
            put("db.maxResourceExhaustRetries", tmp);
         tmp = props.getProperty("resourceExhaustSleepGap");
         if (tmp != null)
            put("db.resourceExhaustSleepGap", tmp);
         
         /*
         this.dbUrl = this.info.get("db.url", "");
         this.dbUser = this.info.get("db.user", "");
         this.dbPasswd = this.info.get("db.password", "");
         int maxInstances = this.info.getInt("db.maxInstances", 10);
         long busyToIdle = this.info.getLong("db.busyToIdleTimeout", 0);
         long idleToErase = this.info.getLong("db.idleToEraseTimeout", 120*60*1000L);
         this.maxResourceExhaustRetries = this.info.getInt("db.maxResourceExhaustRetries", 5);
         this.resourceExhaustSleepGap = this.info.getLong("db.resourceExhaustSleepGap", 1000);
         this.poolManager = new PoolManager("DbPool", this, maxInstances, busyToIdle, idleToErase);
         String createInterceptorClass = info.get("db.createInterceptor.class", null);
         */
         poolId = "db.pool-" + url + "-" + user + "-" + size + "-" + busyTimeout;
      }
      
      /**
       * Important: only invoke this method once to initially retrieve the pool. Every invocation results in initializing the
       * pool with the side effect of increasing the reference counting of it.
       * @param info
       * @return
       * @throws Exception
       */
      public I_DbPool getDbPool(I_Info info) throws Exception {
         synchronized (info) {
            I_DbPool dbPool = (I_DbPool)info.getObject(poolId);
            if (dbPool == null) {
               ClassLoader cl = DbWatcher.class.getClassLoader();
               String dbPoolClass = info.get("dbPool.class", "org.xmlBlaster.contrib.db.DbPool");
               if (dbPoolClass.length() > 0) {
                   dbPool = (I_DbPool)cl.loadClass(dbPoolClass).newInstance();
                   if (log.isLoggable(Level.FINE)) 
                      log.fine(dbPoolClass + " created and initialized");
               }
               else
                  throw new IllegalArgumentException("Couldn't initialize I_DbPool, please configure 'dbPool.class' to provide a valid JDBC access.");
               info.putObject(poolId, dbPool);
            }
            dbPool.init(info);
            return dbPool;
         }
      }
      
      
   }
   
   private static final String ME = "XBDatabaseAccessor";
   //private I_Info info;
   private static Logger log = Logger.getLogger(XBDatabaseAccessor.class.getName());
   private I_DbPool pool;
   private I_EntryFactory factory;
   private WeakHashMap listener;

   private XBStoreFactory storeFactory;
   private XBMeatFactory meatFactory;
   private XBRefFactory refFactory;
   
   private long initCount;
   
   private static String DUMMY_VALUE = "A";

   private int maxStatementLength = 0;
   private boolean isConnected = true;

   private static boolean first = true;
   
   /**
    * Counts the queues using this manager.
    */
   private int queueCounter = 0;

   /**
    * tells wether the used database supports batch updates or not.
    */
    private boolean supportsBatch = true;
    
    /** forces the desactivation of batch mode when adding entries */
    private boolean enableBatchMode = false;

    private Global glob;
    
    /** Column index into XB_ENTRIES table */
    final static int DATA_ID = 1;
    final static int QUEUE_NAME = 2;
    final static int PRIO = 3;
    final static int TYPE_NAME = 4;
    final static int PERSISTENT = 5;
    final static int SIZE_IN_BYTES = 6;
    final static int BLOB = 7;
    
    private int maxNumStatements;
    private int maxSelectLimit;
    private int timeout;
    private boolean dbAdmin = true;
    
    public XBDatabaseAccessor() {
       super();
    }

    private final void prepareDefaultStatements() {
       if (getDbVendor().equals(POSTGRES)) { // not tested
       }
       else if (getDbVendor().equals(ORACLE)) { // not tested
       }
       else if (getDbVendor().equals(DB2)) { // not tested
          
       }
       else if (getDbVendor().equals(FIREBIRD)) { // not tested
          
       }
       else if (getDbVendor().equals(SQLSERVER_2000) || getDbVendor().equals(SQLSERVER_2005)) { // not tested
          
       }
       else if (getDbVendor().equals(MYSQL)) { // not tested
          
       }
       else if (getDbVendor().equals(SQLITE)) { // not tested
          
       }
       else { // if (getDbVendor().equals(HSQLDB)) // not tested
       }
    }
    
    protected synchronized boolean initFactory(Global global, PluginInfo plugInfo) throws XmlBlasterException {
       if (initCount > 0) {
          initCount++;
          return false;
       }
       this.factory  = global.getEntryFactory();
       this.listener = new WeakHashMap();

       QueueGlobalInfo globalInfo = new QueueGlobalInfo();
       globalInfo.init(global, plugInfo);
       try {
          pool = globalInfo.getDbPool(globalInfo);
          init(globalInfo);
       }
       catch (Exception ex) {
          throw new XmlBlasterException(global, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME, "wipeOutDB SQL exception", ex);
       }
       initCount++;
       return true;
    }
 
    
    
    // @Override
   public I_Info init(I_Info origInfo) throws XmlBlasterException {
      I_Info info = super.init(origInfo);
      doInit(info);
      return info;
   }

   /**
    * @param storage TODO
    * @param JdbcConnectionPool the pool to be used for the connections to
    *        the database. IMPORTANT: The pool must have been previously
    *        initialized.
    */
   protected void doInit(I_Info info) throws XmlBlasterException {
      prepareDefaultStatements();
      
      if (info instanceof GlobalInfo) {
         glob = ((GlobalInfo)info).getGlobal();
      }
      else {
         glob = new Global();
         String[] keys = (String[])info.getKeys().toArray(new String[info.getKeys().size()]);;
         for (int i=0; i < keys.length; i++)
            glob.getProperty().set(keys[i], info.get(keys[i], null));
         keys = (String[])info.getObjectKeys().toArray(new String[keys.length]);;
         for (int i=0; i < keys.length; i++)
            glob.addObjectEntry(keys[i], info.getObject(keys[i]));
      }

      // get the necessary metadata
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         DatabaseMetaData dbmd = conn.getMetaData();
         maxStatementLength = dbmd.getMaxStatementLength();
         if (maxStatementLength < 1) {
            maxStatementLength = info.getInt("queue.persistent.maxStatementLength", 2048);
            if (first) {
               log.info("The maximum SQL statement length is not defined in JDBC meta data, we set it to " + this.maxStatementLength);
               first = false;
            }
         }

         if (!dbmd.supportsTransactions()) {
            String dbName = dbmd.getDatabaseProductName();
            log.severe("the database '" + dbName + "' does not support transactions, unpredicted results may happen");
         }

         if (!dbmd.supportsBatchUpdates()) {
            String dbName = dbmd.getDatabaseProductName();
            supportsBatch = false;
            log.fine("the database '" + dbName + "' does not support batch mode. No problem I will work whitout it");
         }
         
         // zero means not limit (to be sure we also check negative Values
         boolean logWarn = false;
         int defaultMaxNumStatements = dbmd.getMaxStatements();
         if (defaultMaxNumStatements < 1) {
            defaultMaxNumStatements = 50;
            logWarn = true;
         }
         // -queue.persistent.maxNumStatements 50
         maxNumStatements = info.getInt("maxNumStatements", defaultMaxNumStatements);
         log.info("The maximum Number of statements for this database instance are '" + this.maxNumStatements + "'");
         if (logWarn && info.getInt("maxNumStatements",-1)==-1)
            log.warning("The maxStatements returned fromt the database metadata is '" + defaultMaxNumStatements + "', will set the default to 50 unless you explicitly set '-queue.persistent.maxNumStatements <num>'");

         // -queue.persistent.maxSelectLimit -1 (off)
         maxSelectLimit = info.getInt("maxSelectLimit", -1);
         if (this.maxSelectLimit > 0)
            log.info("The maximum results returned by a select is set to '" + maxSelectLimit + "' (MSSQLerver only)");

         timeout = (int)(info.getLong("queue.persistent.queryTimeout", 0L) / 1000L);
         dbAdmin = info.getBoolean("dbAdmin", true);
         
         // the property settings specific to this plugin type / version
         
         String prefix = "queue.jdbc";
         storeFactory = new XBStoreFactory(prefix);
         meatFactory = new XBMeatFactory(prefix);
         refFactory = new XBRefFactory(prefix);
         storeFactory.init(info);
         meatFactory.init(info);
         refFactory.init(info);
         storeFactory.create(conn);
         meatFactory.create(conn);
         refFactory.create(conn);
         isConnected = true;
         log.info("Using DB " + dbmd.getDatabaseProductName() + " " + dbmd.getDatabaseProductVersion() + " " + dbmd.getDriverName());
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
         releaseConnection(conn, success, null);
      }
   }

   
   private final void throwXblEx(Throwable origEx, ErrorCode errCode, String msg) throws XmlBlasterException {
      if (origEx instanceof XmlBlasterException)
         throw (XmlBlasterException)origEx;
      if (errCode == null)
         errCode = ErrorCode.RESOURCE_DB_UNKNOWN;
      if (msg == null)
         msg = origEx.getMessage();
      throw new XmlBlasterException(glob, errCode, XBDatabaseAccessor.class.getName(), msg, origEx);
      
   }
   private final void releaseConnection(Connection conn, boolean success, String msg) throws XmlBlasterException {
      if (conn == null)
         return;
      try {
         if (success)
            pool.release(conn);
         else
            pool.erase(conn);
      }
      catch (Exception ex) {
         throwXblEx(ex, null, null);
      }
   }
   
   /**
    * pings the jdbc connection to check if the DB is up and running. It returns
    * 'true' if the connection is OK, false otherwise. The ping is done by invocation 
    */
   public boolean ping() {
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         boolean ret = ping(conn);
         return ret;
      }
      catch (Exception ex) {
         success = false;
         log.warning("ping failed due to problems with the pool. Check the jdbc pool size in 'xmlBlaster.properties'. Reason :" + ex.getMessage());
         return false;
      }
      finally {
         try {
            releaseConnection(conn, success, null);
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
      if (log.isLoggable(Level.FINER)) 
         log.finer("ping");
      if (conn == null) 
         return false; // this could occur if it was not possible to create the connection

      try {
         storeFactory.ping(conn, timeout);
         if (log.isLoggable(Level.FINE)) 
            log.fine("ping successful");
         return true;
      }
      catch (Throwable ex) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("ping to DB failed. DB may be down. Reason " + ex.toString());
         return false;
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
      if (entry == null) return false;
      synchronized (this.listener) {
         return this.listener.remove(entry) != null;
      }
   }

   /**
    * @see I_StorageProblemListener#storageUnavailable(int)
    */
   public void storageUnavailable(int oldStatus) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("storageUnavailable (old status '" + oldStatus + "')");
      isConnected = false;

      I_StorageProblemListener[] listenerArr = getStorageProblemListenerArr();
      for(int i=0; i<listenerArr.length; i++) {
         if (isConnected == true) 
            break;
         I_StorageProblemListener singleListener = listenerArr[i];
         singleListener.storageUnavailable(oldStatus);
      }
   }

   /**
    * @see I_StorageProblemListener#storageAvailable(int)
    */
   public void storageAvailable(int oldStatus) {
      if (log.isLoggable(Level.FINER)) 
         log.finer("storageAvailable (old status '" + oldStatus + "')");
      isConnected = true;
      //change this once this class implements I_StorageProblemNotifier
      if (oldStatus == I_StorageProblemListener.UNDEF) 
         return;
      I_StorageProblemListener[] listenerArr = getStorageProblemListenerArr();
      for(int i=0; i<listenerArr.length; i++) {
         if (isConnected == false) 
            break;
         I_StorageProblemListener singleListener = listenerArr[i];
         singleListener.storageAvailable(oldStatus);
      }
   }

   /**
    * @return A current snapshot of the connection listeners where we can work on (unsynchronized) and remove
    * listeners without danger
    */
   public I_StorageProblemListener[] getStorageProblemListenerArr() {
      synchronized (listener) {
         return (I_StorageProblemListener[])listener.keySet().toArray(new I_StorageProblemListener[listener.size()]);
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
         // TODO add this again if necessary
         // pool.setConnectionLost();
      }
      return ret;
   }

   private final boolean isSameString(String str1, String str2) {
      if (str1 == null && str2 == null)
         return true;
      if (str1 == null || str2 == null)
         return false;
      return str1.equals(str2);
   }
   
   private final boolean areSameCounters(XBMeat newEntry, XBMeat oldEntry) {
      if (newEntry.getRefCount() != oldEntry.getRefCount())
         return false;
      if (newEntry.getRefCount2() != oldEntry.getRefCount2())
         return false;
      return true;
   }
   
   private final boolean isSame(XBMeat newEntry, XBMeat oldEntry, boolean checkCounters, boolean checkContent) {
      if (newEntry == null || oldEntry == null)
         return false;
      if (newEntry.getId() != oldEntry.getId())
         return false;
      
      if (newEntry.getStoreId() != oldEntry.getStoreId())
         return false;
      
      if (newEntry.getByteSize() != oldEntry.getByteSize())
         return false;
      
      if (!isSameString(newEntry.getFlag1(), oldEntry.getFlag1()))
         return false;

      if (!isSameString(newEntry.getDataType(), oldEntry.getDataType()))
         return false;
      if (!isSameString(newEntry.getKey(), oldEntry.getKey()))
         return false;
      if (!isSameString(newEntry.getQos(), oldEntry.getQos()))
         return false;
      
      if (checkContent) {
         byte[] content1 = newEntry.getContent();
         byte[] content2 = oldEntry.getContent();
         if (content1 == null && content2 == null)
            return true;
         if (content1 == null || content2 == null)
            return false;
         for (int i=0; i < content1.length; i++) {
            if (content1[i] != content2[i])
               return false;
         }
      }
      
      if (checkCounters) {
         if (areSameCounters(newEntry, oldEntry))
            return false;
      }
      return true;
   }
   
   /**
    *
    * modifies a row in the specified queue table
    * @param queueName The name of the queue on which to perform the operation
    * @param entry the object to be stored.
    * @param oldEntry the old one
    * @return true on success
    *
    * @throws XmlBlasterException if an error occurred when trying to get a connection or an SQLException
    *         occurred.
    */
   public long modifyEntry(XBStore store, XBMeat entry, XBMeat oldEntry, boolean onlyRefCounters)
      throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("For entry '" + entry.getId() + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".modifyEntry", " the connection to the DB is unavailable already before trying to add an entry"); 
      }

      if (entry != null)
         entry.setStoreId(store.getId());
      if (oldEntry != null)
         oldEntry.setStoreId(store.getId());
      
      /*
       * TODO
       * DANGER: DO NOT TRY TO OPTIMIZE THIS SINCE THE OLD AND NEW ENTRY CAN BE THE SAME BUT THE STORED ENTRY IS 
       * DIFFERENT. MUST BE CLEARED UP
      if (!onlyRefCounters) {
         final boolean checkCounters = false;
         final boolean checkContent = false; // TODO CHECK IF THIS IS CORRECT
         onlyRefCounters = isSame(entry, oldEntry, checkCounters, checkContent);
      }
      */
      
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         if (onlyRefCounters) {
            // if (areSameCounters(entry, oldEntry))
            //    return 0;
            meatFactory.updateRefCounters(store, entry, conn, timeout);
         }
         else
            meatFactory.update(entry, conn, timeout);
         if (oldEntry == null)
            return entry.getByteSize();
         return entry.getByteSize() - oldEntry.getByteSize();
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

         log.warning("Could not update entry '" +
                  entry.getClass().getName() + "'-'" +  entry.getId() + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId("" + store.getId(), "modifyEntry"), ex)) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".modifyEntry", "", ex); 
         }
         else {
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".modifyEntry", "", ex); 
         }
      }
      finally {
         releaseConnection(conn, success, null);
      }
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
   private final boolean addSingleEntry(XBStore store, I_Entry entry, Connection conn)
      throws XmlBlasterException {
      
      XBMeat meat = entry.getMeat();
      XBRef ref = entry.getRef(); 

      StringBuffer buf = new StringBuffer();
      if (meat != null) {
         meat.setStoreId(store.getId());
         buf.append(meat.getId());
      }
      if (ref != null) {
         ref.setStoreId(store.getId());
         buf.append("-").append(ref.getId());
      }
      String logId = buf.toString();
      
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("For entry '" + logId + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addSingleEntry", " the connection to the DB is unavailable already before trying to add an entry"); 
      }

      boolean ret = false;
      try {
         if (meat != null)
            meatFactory.insert(meat, conn, timeout);
         if (ref != null)
            refFactory.insert(ref, conn, timeout);
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
         if (meat != null)
            log.severe("Could not insert entry '" + meat.getDataType() + "'-'" + logId + "': " + ex.toString());
         else if (ref != null)
            log.severe("Could not insert entry '" + ref.getMethodName() + "'-'" + logId + "': " + ex.toString());
         else
            log.severe("Could not insert null entry " + logId + "': " + ex.toString());
         if (checkIfDBLoss(conn, getLogId(store.toString(), "addEntry"), ex)) {
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex); 
         }
         // check if the exception was due to an existing entry. If yes, no exception will be thrown
         try {
            boolean exists = false;
            if (meat != null)
               exists = meatFactory.get(store, meat.getId(), conn, timeout) != null;
            if (!exists && ref != null)
               exists = refFactory.get(store, ref.getId(), conn, timeout) != null;
            if (!exists)
               throw ex;
         }
         catch (Throwable ex1) {
            String secondException = Global.getStackTraceAsString(ex1);
            log.warning("The exception '" + ex1.getMessage() + "' occured at " + secondException + "'. The original exception was '" + originalExceptionReason + "' at '" + originalExceptionStack);
            
            if (log.isLoggable(Level.FINE)) 
               log.fine("addEntry: checking if entry already in db: exception in select: '" + ex.toString() + "'");
            if (checkIfDBLoss(conn, getLogId(store.toString(), "addEntry"), ex1))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex1); 
            else 
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntry", "", ex1); 
         }
         ret = false;
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
   public boolean addEntry(XBStore store, I_Entry entry) throws XmlBlasterException {
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         return addSingleEntry(store, entry, conn);
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(store.toString(), "addEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntry", "", ex);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".addEntry", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }

   private int[] addEntriesSingleMode(Connection conn, XBStore store, I_Entry[] entries)
      throws XmlBlasterException {
      // due to a problem in Postgres (implicit abortion of transaction by exceptions)
      // we can't do everything in the same transaction. That's why we simulate a single 
      // transaction by deleting the processed entries in case of a failure other than
      // double entries exception.
      int i = 0;
      int[] ret = new int[entries.length];
      try {
         for (i=0; i < entries.length; i++) {
            if (addSingleEntry(store, entries[i], conn)) 
               ret[i] = 1; 
            else ret[i] = 0;
            if (log.isLoggable(Level.FINE)) 
               log.fine("addEntriesSingleMode adding entry '" + i + "' in single mode succeeded");
         }
         if (!conn.getAutoCommit()) conn.commit();
         return ret;
      }
      catch (Throwable ex1) {
         // conn.rollback();
         try {
            for (int ii=0; ii < i; ii++) {
               if (ret[ii] > 0) {
                  long refId = -1L;
                  long meatId = -1L;
                  if (entries[ii].getMeat() != null)
                     meatId = entries[ii].getMeat().getId();
                  if (entries[ii].getRef() != null)
                     refId = entries[ii].getRef().getId();
                  deleteEntry(store, refId, meatId); 
               }
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
   public int[] addEntries(XBStore store, I_Entry[] entries) throws XmlBlasterException {

      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine(" for '" + entries.length + "' currently not possible. No connection to the DB");
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", " the connection to the DB is unavailable already before trying to add entries"); 
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         if (!this.supportsBatch || !this.enableBatchMode)
            return addEntriesSingleMode(conn, store, entries);
         
         // if (conn.getAutoCommit()) 
         //   conn.setAutoCommit(false);
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME + ".addEntries", " using batch is not implemented"); 
       }
      catch (Throwable ex) {
         success = false;
         try {
            if (!conn.getAutoCommit()) {
               conn.rollback(); // rollback the original request ...
            }
         }
         catch (Throwable ex1) {
            log.severe("error occured when trying to rollback after exception: reason: " + ex1.toString() + " original reason:" + ex.toString());
            ex.printStackTrace(); // original stack trace
         }
         if (log.isLoggable(Level.FINE)) 
            log.fine("Could not insert entries: " + ex.toString());
         if ((!this.supportsBatch || !this.enableBatchMode) ||
            checkIfDBLoss(conn, getLogId(store.toString(), "addEntries"), ex)) 
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".addEntries", "", ex); 
         else { // check if the exception was due to an already existing entry by re
            return addEntriesSingleMode(conn, store, entries);
         }
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }

   /**
    * Cleans up the specified queue. It deletes all queue entries in the 'entries' table.
    * @return the number of queues deleted (not the number of entries).
    */
   public final int cleanUp(XBStore store) throws XmlBlasterException {

      if (!isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return 0;
      }
      Connection conn = null;
      boolean success = false;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         int num = refFactory.deleteAllStore(store, conn, timeout);
         success = true;
         return (num > 0) ? 1 : 0;
      }
      catch (XmlBlasterException ex) {
         throw ex;
      }
      catch (Throwable ex) {
         try {
            if (checkIfDBLoss(conn, getLogId(store.toString(), "deleteEntries"), ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex); 
         }
         catch (XmlBlasterException e) {
            throw e;
         }
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex);
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }


   /**
    * Wipes out the entire DB. 
    * i.e. it deletes the three tables from the DB. IMPORTANT: 
    * @param doSetupNewTables if set to 'true' it recreates the necessary empty tables.
    */
   public int wipeOutDB(boolean doSetupNewTables) throws XmlBlasterException {
      if (!dbAdmin)
         return 0;
      int count = 0;
      Connection conn = null;
      boolean success = true;
      try {
        try {
           conn = pool.reserve();
           conn.setAutoCommit(true);
           refFactory.drop(conn);
           meatFactory.drop(conn);
           storeFactory.drop(conn);
           if (doSetupNewTables) {
              storeFactory.create(conn);
              meatFactory.create(conn);
              refFactory.create(conn);
           }
           count++;
         }
         catch (SQLException ex) {
            success = false;
            if (checkIfDBLoss(conn, getLogId(null, "wipeOutDB"), ex))
               throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, getLogId(null, "wipeOutDB"), "SQLException when wiping out DB", ex);
            else {
               log.warning("Exception occurred when trying to drop the tables, it probably is already dropped. Reason: " + ex.toString());
            }
         }

         if (!conn.getAutoCommit()) 
            conn.commit();
      }
      catch (Throwable ex) {
         success = false;
         if (ex instanceof XmlBlasterException) 
            throw (XmlBlasterException)ex;
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, getLogId(null, "wipeOutDB"), "wipeOutDB: exception ", ex);
      }
      finally {
         releaseConnection(conn, success, null);
      }
      return count;
   }


   public static String isolationToString(int isolation) {
      if (isolation == Connection.TRANSACTION_READ_COMMITTED)
         return "TRANSACTION_READ_COMMITTED";
      if (isolation == Connection.TRANSACTION_READ_UNCOMMITTED)
         return "TRANSACTION_READ_UNCOMMITTED";
      if (isolation == Connection.TRANSACTION_REPEATABLE_READ)
         return "TRANSACTION_REPEATABLE_READ";
      if (isolation == Connection.TRANSACTION_SERIALIZABLE)
         return "TRANSACTION_SERIALIZABLE";
      if (isolation == Connection.TRANSACTION_NONE)
         return "TRANSACTION_NONE";
      return "" + isolation;
   }

   public static String getIsolationLevel(Connection conn) {
      try {
         int isolation = conn.getTransactionIsolation();
         return "Supports connection TRANSACTION_READ_COMMITTED-"+Connection.TRANSACTION_READ_COMMITTED+"="
               + conn.getMetaData().supportsTransactionIsolationLevel(
                     Connection.TRANSACTION_READ_COMMITTED)
               + ", TRANSACTION_READ_UNCOMMITTED-"+Connection.TRANSACTION_READ_UNCOMMITTED+"="
               + conn.getMetaData().supportsTransactionIsolationLevel(
                     Connection.TRANSACTION_READ_UNCOMMITTED)
               + ", TRANSACTION_REPEATABLE_READ-"+Connection.TRANSACTION_REPEATABLE_READ+"="
               + conn.getMetaData().supportsTransactionIsolationLevel(
                     Connection.TRANSACTION_REPEATABLE_READ)
               + ", TRANSACTION_SERIALIZABLE-"+Connection.TRANSACTION_SERIALIZABLE+"="
               + conn.getMetaData().supportsTransactionIsolationLevel(
                     Connection.TRANSACTION_SERIALIZABLE)
               + ". Using transaction isolation "
               + isolationToString(isolation) + "=" + isolation;
         // conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
      } catch (SQLException e) {
         log.warning(e.toString());
         return "";
      }
   }
   
   /**
    * Dumps the metadata to the log object. The log will write out this
    * information as an info. This means you don't need to have the switch
    * 'dump' set to true to see this information.
    */
   public void dumpMetaData() {
      Connection conn = null;
      boolean success = false;
      try {
         conn = pool.reserve();
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
         log.info("support transactions : " + getIsolationLevel(conn));
         log.info("--------------- DUMP OF METADATA FOR THE DB END  ---------------------");
         success = true;
      }
      catch (Exception ex) {
         log.severe("dumpMetaData: exception: " + ex.getMessage());
         ex.printStackTrace();
      }
      finally {
         try {
            releaseConnection(conn, success, null);
         }
         catch (XmlBlasterException ex) {
            ex.printStackTrace();
         }
      }
   }

   /**
    * Sets up a table for the queue specified by this queue name.
    * If one already exists (i.e. if it recovers from a crash) its associated
    * table name is returned.
    */
   public final void setUp(boolean deleteAllTransients) throws XmlBlasterException {
      if (log.isLoggable(Level.FINE)) 
         log.fine("Initializing the first time the pool");
      synchronized(XBDatabaseAccessor.class) {
         // Should be only done on startup for each database instance
         // To have a real JVM singleton a static bool is not enough (ClassLoader)
         // so we use System.properties
         if (deleteAllTransients)
            deleteAllTransient(null);
       }
   }

   /**
    * Returns the pool associated to this object.
    * @return JdbcConnectionPool the pool managing the connections to the DB
    */
   public I_DbPool getPool() {
      return pool;
   }

   /**
    * deletes all transient messages
    */
   public long deleteAllTransient(XBStore store) throws XmlBlasterException {
      Connection conn = null;
      boolean success = false;
      try {
         if (!this.isConnected) {
            if (log.isLoggable(Level.FINE)) 
               log.fine("Currently not possible. No connection to the DB");
            return 0;
         }
         conn = pool.reserve();
         conn.setAutoCommit(true);
         
         long num = refFactory.deleteTransients(store.getId(), conn, timeout);
         num += meatFactory.deleteTransients(store.getId(), conn, timeout);
         success = true;
         return num;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(store.toString(), "deleteAllTransient"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteAllTransient", "", ex);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteAllTransient", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }

   /**
    * Under the same transaction it gets and deletes all the entries which fit
    * into the constrains specified in the argument list.
    * The entries are really deleted only if doDelete is true, otherwise they are left untouched on the queue
    * @see org.xmlBlaster.util.queue.I_Queue#takeLowest(int, long, org.xmlBlaster.util.queue.I_QueueEntry, boolean)
    */
   public ReturnDataHolder getAndDeleteLowest(XBStore store, int numOfEntries, long numOfBytes,
         int maxPriority, long minUniqueId, boolean leaveOne, boolean doDelete, I_Storage storage) throws XmlBlasterException {
      
      Connection conn = null;
      boolean success = false;
      try {
         if (!this.isConnected) {
            if (log.isLoggable(Level.FINE)) 
               log.fine("Currently not possible. No connection to the DB");
            return null;
         }
         conn = pool.reserve();
         conn.setAutoCommit(true);
         ReturnDataHolder ret = null;
         ret = refFactory.getAndDeleteLowest(store, conn, numOfEntries, numOfBytes, maxPriority, minUniqueId, leaveOne, doDelete, maxStatementLength, maxNumStatements, timeout);
         final I_EntryFilter filter = null;
         ret.list = (ArrayList)createEntries(store, null, ret.list, filter, storage);
         success = true;
         return ret;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(store.toString(), "getAndDeleteLowest"), ex))
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getAndDeleteLowest", "", ex);
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getAndDeleteLowest", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }
   
   private long deleteEntries(Connection conn, XBStore store, XBRef[] refs, XBMeat[] meats) throws SQLException {
      // no need to commit explicitly here since this is done on each sweep internally
      long ret = -1L;
      final boolean commitInBetween = true;
      if (refs != null)
         ret = refFactory.deleteList(store, conn, refs, maxStatementLength, maxNumStatements, commitInBetween, timeout);
      if (meats != null) {
         long ret1 = meatFactory.deleteList(store, conn, meats, maxStatementLength, maxNumStatements, commitInBetween, timeout);
         if (ret > -1 && ret1 != ret)
            throw new SQLException(ME +".deleteEntries: wrong number of entries deleted: meat " + meats.length + " and ref " + refs.length + " but should be the same for both");
         else
            ret = ret1;
      }
      return ret;
   }
   
   /**
    * 
    * Deletes the store.
    */
   public void deleteStore(long storeId) throws XmlBlasterException {
      Connection conn = null;
      long ret = 0L;
      boolean success = false;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         this.storeFactory.delete(storeId, 0, conn, timeout);
         success = true;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId("" + storeId, "deleteStore"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteStore", "", ex);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteStore", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
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
    * @param   refs the array containing all entries to delete for the refs.
    * @param   meats the array containing all entries to delete for the meats.
    */
   public long deleteEntries(XBStore store, XBRef[] refs, XBMeat[] meats) throws XmlBlasterException {
      if (refs != null && meats != null && refs.length != meats.length)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, ME, ".deleteEntries: wrong number of entries : meat " + meats.length + " and ref " + refs.length + " but should be the same for both");
      Connection conn = null;
      long ret = 0L;
      boolean success = false;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(false);
         ret = deleteEntries(conn, store, refs, meats);
         success = true;
         return ret;
      }
      catch (Throwable ex) {
         if (checkIfDBLoss(conn, getLogId(store.toString(), "deleteEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntries", "", ex);
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntries", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }

   private final void rollback(Connection conn) {
      try {
         if (conn != null)
            conn.rollback();
      }
      catch (SQLException ex) {
         log.severe("Exception occured when trying to roll back " + ex.getMessage());
         ex.printStackTrace();
      }
   }
   
   /**
    * Deletes the entry specified
    *
    * @param   tableName the name of the table on which to delete the entries
    * @param   uniqueIds the array containing all the uniqueId for the entries to delete.
    */
   public int deleteEntry(XBStore store, long refId, long meatId) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return 0;
      }
      Connection conn = null;
      boolean success = true;
      int ret = 0;
      try {
         conn =  pool.reserve();
         conn.setAutoCommit(false);
         int ret1 = 0;
         if (refId > -1L)
            ret = refFactory.delete(store.getId(), refId, conn, timeout);
         if (meatId > -1L)
            ret1 = meatFactory.delete(store.getId(), meatId, conn, timeout);
         if (ret == 0)
            ret += ret1;
         conn.commit();
         return ret;
      }
      catch (Throwable ex) {
         rollback(conn);
         success = false;
         if (checkIfDBLoss(conn, getLogId(store.toString(), "deleteEntry"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteEntry", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteEntry", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
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
   public long deleteFirstRefs(XBStore store, long numOfEntries, long numOfBytes) throws XmlBlasterException {
      if (log.isLoggable(Level.FINER)) log.finer("Entering");

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE))
            log.fine("Currently not possible. No connection to the DB");
         return 0;
      }

      if (numOfEntries >= Integer.MAX_VALUE)
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, getLogId(store.toString(), "deleteFirstEntries"),
               "The number of entries=" + numOfEntries + " to be deleted is too big for this system");

      Connection conn = null;
      boolean success = false;
      
      try {
         conn = pool.reserve();
         conn.setAutoCommit(false);
         List/*<XBRef>*/ tmp = refFactory.getFirstEntries(store, conn, numOfEntries, numOfBytes, timeout);
         XBRef[] refs = (XBRef[])tmp.toArray(new XBRef[tmp.size()]);
         XBMeat[] meats = null;
         for (int i=0; i < refs.length; i++) {
            if (meats != null) {
               meats[i] = refs[i].getMeat();
               if (meats[i] == null) {
                  meats[i] = new XBMeat();
                  meats[i].setId(refs[i].getMeatId());
               }
            }
         }
         long ret = deleteEntries(conn, store, refs, meats);
         conn.commit();
         return ret;
      }
      catch (Throwable ex) {
         rollback(conn);
         success = false;
         if (checkIfDBLoss(conn, getLogId(store.toString(), "deleteFirstEntries"), ex))
            throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".deleteFirstEntries", "", ex); 
         else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".deleteFirstEntries", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
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
   public List/*<I_Entry>*/ getEntriesByPriority(XBStore store, int numOfEntries,
                             long numOfBytes, int minPrio, int maxPrio)
      throws XmlBlasterException {

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         final boolean onlyId = false;
         List ret = refFactory.getEntriesByPriority(store, conn, numOfEntries, numOfBytes, minPrio, maxPrio, onlyId);
         final I_Storage storage = null;
         final I_EntryFilter filter = null;
         ret = createEntries(store, null, ret, filter, storage);
         return ret;
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getEntriesByPriority"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesByPriority", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesByPriority", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
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
   public List/*<XBRef>*/ getEntriesBySamePriority(XBStore store, int numOfEntries, long numOfBytes)
      throws XmlBlasterException {

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         List ret = refFactory.getEntriesBySamePriority(store, conn, numOfEntries, numOfBytes);
         final I_Storage storage = null;
         final I_EntryFilter filter = null;
         ret = createEntries(store, null, ret, filter, storage);
         return ret;
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getEntriesBySamePriority"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesBySamePriority", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesBySamePriority", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }
   }

   /**
    * gets the first numOfEntries of the queue.
    * If there are not so many entries in the queue, all elements in the queue
    * are returned.
    * <p>
    * Is public for testsuite only.
    * @param numOfEntries Access num entries, if -1 access all entries currently found
    * @param numOfBytes is the maximum size in bytes of the array to return, -1 is unlimited .
    */
   public List/*<I_Entry>*/ getEntries(XBStore store, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter, boolean isRef, I_Storage storage) throws XmlBlasterException {
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         List /*<XBEntry>*/ ret = null;
         if (isRef) {
            ret = refFactory.getFirstEntries(store, conn, numOfEntries, numOfBytes, timeout);
            ret = createEntries(store, null, ret, entryFilter, storage);
         }
         else {
            ret = meatFactory.getFirstEntries(store, conn, numOfEntries, numOfBytes, timeout);
            ret = createEntries(store, ret, null, entryFilter, storage);
         }
         return ret;
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getEntries"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntries", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntries", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
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
   public XBEntry[] getEntriesLike(String queueNamePattern, String flag,
                    int numOfEntries, long numOfBytes,
                    I_EntryFilter entryFilter)
      throws XmlBlasterException {
      throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME + ".getEntriesLike", "");
   }

   /**
    * gets the first numOfEntries of the queue until the limitEntry is reached.
    *
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public List/*<XBEntry[]>*/ getEntriesWithLimit(XBStore store, I_Entry limitEntry, I_Storage storage)
      throws XmlBlasterException {
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         XBRef limitRef = limitEntry.getRef();
         List list = refFactory.getWithLimit(store, conn, limitRef);
         final I_EntryFilter filter = null;
         return createEntries(store, (List)null, list, filter, storage);
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getEntriesWithLimit"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getEntriesWithLimit", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getEntriesWithLimit", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }

   /**
    * deletes the first numOfEntries of the queue until the limitEntry is reached.
    * @param numOfEntries the maximum number of elements to retrieve
    */
   public long removeEntriesWithLimit(XBStore store, XBRef limitEntry, boolean inclusive)
      throws XmlBlasterException {
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("Currently not possible. No connection to the DB");
         return 0L;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         return refFactory.deleteWithLimit(store, conn, limitEntry, inclusive);
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "removeEntriesWithLimit"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".removeEntriesWithLimit", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeEntriesWithLimit", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }


   /**
    * gets all the entries which have the dataid specified in the argument list.
    * If the list is empty or null, an empty ArrayList object is returned.
    */
   public List/*<XBEntry>*/ getEntries(XBStore store, XBRef[] refs, XBMeat[] meats)
      throws XmlBlasterException {

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         
         List retRef = null;
         List retMeat = null;
         if (refs != null)
            retRef = refFactory.getList(store, conn, refs, maxStatementLength, maxNumStatements, timeout);
         else
            retMeat = meatFactory.getList(store, conn, meats, maxStatementLength, maxNumStatements, timeout);
         final I_Storage storage = null;
         final I_EntryFilter filter = null;
         List ret = createEntries(store, retMeat, retRef, filter, storage);
         return ret;
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "removeEntriesWithLimit"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".removeEntriesWithLimit", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".removeEntriesWithLimit", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }
   
   /**
    * Gets the real number of entries. 
    * That is it really makes a call to the DB to find out
    * how big the size is.
    * @return never null
    */
   public final EntryCount getNumOfAll(XBStore store) throws XmlBlasterException {
      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) log.fine("Currently not possible. No connection to the DB");
         return null;
      }
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         if (store.getStoreType() == XBStore.TYPE_REF)
            return refFactory.getNumOfAll(store, conn);
         if (store.getStoreType() == XBStore.TYPE_MEAT)
            return refFactory.getNumOfAll(store, conn);
         EntryCount ret = refFactory.getNumOfAll(store, conn);
         if (ret.numOfEntries == 0L) {
            ret = meatFactory.getNumOfAll(store, conn);
            if (ret.numOfEntries != 0L)
               store.setStoreType(XBStore.TYPE_MEAT);
         }
         else {
            store.setStoreType(XBStore.TYPE_REF);
         }
         return ret;
      }
      catch (Throwable ex) {
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getNumOfAll"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfAll", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfAll", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }


   /**
    * @param queueName or null
    * @return A nice location string for logging (instead of plain ME)
    */
   private final String getLogId(String queueName, String methodName) {
      StringBuffer sb = new StringBuffer(200);
      sb.append(ME);
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
   public static XBDatabaseAccessor createInstance(Global glob, String confType, String confVersion, Properties properties) 
      throws XmlBlasterException {
      if (confType == null) confType = "JDBC";
      if (confVersion == null) confVersion = "1.0";
      QueuePluginManager pluginManager = new QueuePluginManager(glob);
      PluginInfo pluginInfo = new PluginInfo(glob, pluginManager, confType, confVersion);
      // clone the properties (to make sure they only belong to us) ...
      java.util.Properties ownProperties = (java.util.Properties)pluginInfo.getParameters().clone();
      //overwrite our onw properties ...
      if (properties != null) {
         java.util.Enumeration enumer = properties.keys();
         while (enumer.hasMoreElements()) {
            String key =(String)enumer.nextElement();
            ownProperties.put(key, properties.getProperty(key));
         }
      }

      // determine which jdbc manager class to use
      String queueClassName = pluginInfo.getClassName();
      if ("org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin".equals(queueClassName)) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "org.xmlBlaster.util.queue.jdbc.JdbcQueuePlugin is not supported anymore");
      }
      else if ("org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin".equals(queueClassName)) {
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_NOTIMPLEMENTED, ME, "org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin is not supported anymore");
      }
      else if ("org.xmlBlaster.util.queue.jdbc.JdbcQueue".equals(queueClassName)) {
         boolean useXBDatabaseAccessorDelegate = glob.get("xmlBlaster/useXBDatabaseAccessorDelegate", true, null,
               pluginInfo);

         XBDatabaseAccessor queueFactory = (useXBDatabaseAccessorDelegate) ? new XBDatabaseAccessorDelegate()
               : new XBDatabaseAccessor();
         queueFactory.initFactory(glob, pluginInfo);
         final boolean deleteAllTransients = false; // TODO check if this is correct
         queueFactory.setUp(deleteAllTransients);
         return queueFactory;
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
      XBDatabaseAccessor factory = createInstance(glob, confType, confVersion, properties); 
      factory.wipeOutDB(setupNewTables);
   }

   synchronized public void shutdown() {
      initCount--;
      if (initCount > 0)
         return;
      try {
         if (this.pool != null)
            pool.shutdown();
         isConnected = false;
      }
      catch (Exception ex) {
         ex.printStackTrace();
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

   
   private I_Entry createEntry(XBStore store, XBMeat meat, XBRef ref) throws XmlBlasterException {
      return factory.createEntry(store, meat, ref);
   }
   
   private List createEntries(XBStore store, List/*<XBMeat>*/ meatList, List/*<XBRef>*/ refList, I_EntryFilter filter, I_Storage storage) throws XmlBlasterException {
      if (meatList != null && refList != null && meatList.size() != refList.size())
         throw new XmlBlasterException(glob, ErrorCode.INTERNAL_ILLEGALSTATE, ME, ".createEntries: wrong number of entries : meat " + meatList.size() + " and ref " + refList.size() + " but should be the same for both");
      int nmax = 0;
      if (meatList != null)
         nmax = meatList.size();
      else if (refList != null)
         nmax = refList.size();
         
      List ret = new ArrayList();
      for (int i=0; i < nmax; i++) {
         XBMeat meat = null;
         XBRef ref = null;
         if (meatList != null)
            meat = (XBMeat)meatList.get(i);
         if (refList != null) {
            ref = (XBRef)refList.get(i);
            if (meat == null)
               meat = ref.getMeat();
         }
         I_Entry entry = createEntry(store, meat, ref);
         if (filter != null)
            entry = filter.intercept(entry, storage);
         if (entry != null)
            ret.add(entry);
      }
      return ret;
   }
   
   
   public XBStore getXBStore(StorageId uniqueQueueId) throws XmlBlasterException {
      XBStore store = uniqueQueueId.getXBStore();

      if (!this.isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("Currently not possible. No connection to the DB");
         return null;
      }

      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         XBStore newStore = storeFactory.getByName(store.getNodeDb(), store.getTypeDb(), store.getPostfixDb(), conn,
               timeout);
         if (newStore == null) {
            store.setId(new Timestamp().getTimestamp());
            storeFactory.insert(store, conn, timeout);
            newStore = store;
         }
         return newStore;
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         success = false;
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getXBStore"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getXBStore", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getXBStore", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }

   public long clearQueue(XBStore store) throws XmlBlasterException {
      if (!isConnected) {
         if (log.isLoggable(Level.FINE)) 
            log.fine("Currently not possible. No connection to the DB");
         return 0L;
      }
      Connection conn = null;
      boolean success = true;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(false);
         long countMeats = meatFactory.count(store, conn, timeout);
         long countRefs = refFactory.count(store, conn, timeout);
         final long fakeId = 0; // since the argument is not used
         storeFactory.delete(store.getId(), fakeId, conn, timeout);
         countMeats -= meatFactory.count(store, conn, timeout);
         countRefs -= refFactory.count(store, conn, timeout);
         // add the store entry again since it could be used
         storeFactory.insert(store, conn, timeout);
         if (log.isLoggable(Level.FINE))
           log.fine("cleared " + countMeats + " meats and " + countRefs + " refs when clearing store " + store.getId() + " : " + store.toString());
         conn.commit();
         if (countRefs > 0L)
            return countRefs;
         return countMeats;
      }
      catch (Throwable ex) {
         success = false;
         rollback(conn);
        if (checkIfDBLoss(conn, getLogId(store.toString(), "getNumOfAll"), ex))
           throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNAVAILABLE, ME + ".getNumOfAll", "", ex); 
        else throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_DB_UNKNOWN, ME + ".getNumOfAll", "", ex); 
      }
      finally {
         releaseConnection(conn, success, null);
      }

   }
}
