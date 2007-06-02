/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.VersionTransformerCache;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;

public abstract class SpecificDefault implements I_DbSpecific /*, I_ResultCb */ {

   class ResultHandler implements I_ResultCb {

      private int rowsPerMessage;
      private InitialUpdater initialUpdater;
      private SqlInfo sqlInfo;
      private long newReplKey;
      private String destination;
      
      public ResultHandler(InitialUpdater initialUpdater, SqlInfo sqlInfo, long newReplKey, int rowsPerMessage, String destination) {
         this.initialUpdater = initialUpdater;
         this.sqlInfo = sqlInfo;
         this.newReplKey = newReplKey;
         this.rowsPerMessage = rowsPerMessage;
         this.destination = destination;
      }
      
      /**
       * @see I_ResultCb#init(ResultSet)
       */
      public final void result(ResultSet rs) throws Exception {
         try {
            // TODO clear the columns since not really used anymore ...
            int msgCount = 1; // since 0 was the create, the first must be 1
            int internalCount = 0;
            while (rs != null && rs.next()) {
               // this.dbUpdateInfo.fillOneRowWithStringEntries(rs, null);
               this.sqlInfo.fillOneRowWithObjects(rs, null);
               internalCount++;
               log.fine("processing before publishing *" + internalCount + "' of '" + this.rowsPerMessage + "'");
               if (internalCount == this.rowsPerMessage) {
                  internalCount = 0;
                  // publish
                  log.fine("result: going to publish msg '" + msgCount + "' and internal count '" + internalCount + "'");
                  if (this.destination != null && cancelledUpdates.contains(this.destination)) {
                     cancelledUpdates.remove(this.destination);
                     log.info("The ongoing initial update for destination '" + this.destination + "' has been cancelled");
                     return;
                  }
                  this.initialUpdater.publishCreate(msgCount++, this.sqlInfo, this.newReplKey, this.destination);
                  this.sqlInfo.getRows().clear(); // clear since re-using the same dbUpdateInfo
               }
            } // end while
            if (this.sqlInfo.getRows().size() > 0) {
               log.fine("result: going to publish last msg '" + msgCount + "' and internal count '" + internalCount + "'");
               this.initialUpdater.publishCreate(msgCount, this.sqlInfo, this.newReplKey, this.destination);
            }
         } catch (Exception e) {
            e.printStackTrace();
            log.severe("Can't publish change event meat for CREATE");
         }
      }
   }
   
   public final static boolean ROLLBACK_YES = true;
   public final static boolean ROLLBACK_NO = false;
   public final static boolean COMMIT_YES = true;
   public final static boolean COMMIT_NO = false;
   
   private static Logger log = Logger.getLogger(SpecificDefault.class.getName());

   private int rowsPerMessage = 10;

   protected I_Info info;

   protected I_DbPool dbPool;

   protected DbMetaHelper dbMetaHelper;

   protected String replPrefix = "repl_";
   
   protected String replVersion = "0.0";
   
   protected ReplaceVariable replaceVariable;

   protected Replacer replacer;

   protected InitialUpdater initialUpdater;

   private boolean bootstrapWarnings;
   
   private int initCount = 0;
   
   private boolean isInMaster;
   
   private Set cancelledUpdates = new HashSet();
   
   protected boolean isDbWriteable = true;
      
   class Replacer implements I_ReplaceVariable {

      private I_Info info;
      private Map additionalMap;
      
      public Replacer(I_Info info, Map additionalMap) {
         this.info = info;
         this.additionalMap = additionalMap;
         if (this.additionalMap == null)
            this.additionalMap = new HashMap();
      }
      
      public String get(String key) {
         if (key == null)
            return null;
         String repl = (String)this.additionalMap.get(key);
         if (repl != null)
            return repl.trim();
         repl = this.info.get(key, null);
         if (repl != null)
            return repl.trim();
         return null;
      }
      
      public Map getAdditionalMapClone() {
         return new HashMap(this.additionalMap);
      }
   }
   
   /**
    * Not doing anything.
    */
   public SpecificDefault() {
   }

   /**
    * 
    * @param filename
    * @param method
    * @return List of String[]
    * @throws Exception
    */
   public List getContentFromClasspath(String filename, String method, String flushSeparator, String cmdSeparator) throws Exception {
      if (filename == null)
         throw new Exception(method + ": no filename specified");
      ArrayList list = new ArrayList();
      ArrayList internalList = new ArrayList();
      try {
         Enumeration enm = this.getClass().getClassLoader().getResources(filename);
         if(enm.hasMoreElements()) {
            URL url = (URL)enm.nextElement();
            log.info(method + ": : loading file '" + url.getFile() + "'");
            try {
               StringBuffer buf = new StringBuffer();
               BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()));
               String line = null;
               while ( (line = reader.readLine()) != null) {
                  if (line.trim().startsWith(cmdSeparator)) {
                     String tmp =  buf.toString();
                     if (tmp.length() > 0)
                        internalList.add(tmp);
                     buf = new StringBuffer();
                  }
                  else if (line.trim().startsWith(flushSeparator)) {
                     if (buf.length() > 0)
                        internalList.add(buf.toString());
                     if (internalList.size() >0) {
                        String[] tmp = (String[])internalList.toArray(new String[internalList.size()]);
                        list.add(tmp);
                        internalList.clear();
                        buf = new StringBuffer();
                     }
                  }
                  else {
                     line = line.trim();
                     if (line.length() > 0 && !line.startsWith("--"))
                        buf.append(line).append("\n");
                  }
               }
               String end = buf.toString().trim();
               if (end.length() > 0)
                  internalList.add(end);
               if (internalList.size() >0) {
                  String[] tmp = (String[])internalList.toArray(new String[internalList.size()]);
                  list.add(tmp);
                  internalList.clear();
                  buf = null;
               }
               
            }
            catch(IOException ex) {
               log.warning("init: could not read properties from '" + url.getFile() + "' : " + ex.getMessage());
            }

            while(enm.hasMoreElements()) {
               url = (URL)enm.nextElement();
               log.warning("init: an additional matching file has been found in the classpath at '"
                  + url.getFile() + "' please check that the correct one has been loaded (see info above)"
               );
            }
            return list;
         }
         else {
            ClassLoader cl = this.getClass().getClassLoader();
            StringBuffer buf = new StringBuffer();
            if (cl instanceof URLClassLoader) {
               URL[] urls = ((URLClassLoader)cl).getURLs();
               for (int i=0; i < urls.length; i++) 
                  buf.append(urls[i].toString()).append("\n");
            }
            throw new Exception("init: no file found with the name '" + filename + "' : " + (buf.length() > 0 ? " classpath: " + buf.toString() : ""));
         }
      }
      catch(IOException e) {
         throw new Exception("init: an IOException occured when trying to load property file '" + filename + "'", e);
      }
   }
   
   /**
    * Replaces all tokens found in the content and returns the content with the values of the tokens.
    * @param content
    * @return
    */
   private final String replaceTokens(String content, Replacer repl) {
      return this.replaceVariable.replace(content, repl);
   }
   
   /**
    * Gets the specified object name and returns its value (name).
    * For example 'CREATE TABLE one' would return 'one'. Needed on bootstrapping.
    * NOTE: only made public for testing purposes.
    * @param op
    * @param req
    * @return
    */
   public final String getObjectName(String op, String req) {
      if (req == null) {
         log.warning("getObjectName had a null argument");
         return null;
      }  
      req = req.trim();
      if (req.length() < 1) {
         log.warning("getObjectName had an empty argument");
         return null;
      }
      String tmp = req.toUpperCase();
      if (tmp.startsWith(op)) {
         tmp = req.substring(op.length()).trim();
         int pos1 = tmp.indexOf('(');
         int pos2 = tmp.indexOf(' '); // whichever comes first
         if (pos1 < 0) {
            if (pos2 < 0) {
               log.warning("getObjectName for '" + op + "' on '" + req + "': no '(' or ' ' found.");
               return null;
            }
            return tmp.substring(0, pos2).trim();
         }
         else if (pos2 < 0) {
            return tmp.substring(0, pos1).trim();
         } 
         else {
            if (pos2 < pos1)
               pos1 = pos2;
            return tmp.substring(0, pos1).trim();
         }
      }
      return null;
   }
   
   /**
    * Checks if the table has to be created. 
    * If it is a 'CREATE TABLE' operation a non-negative value is returned,
    * if it is another kind of operation, -1 is returned.
    * If the table already exists, it returns zero.
    * If the table does not exist, it returns 1.
    * NOTE: only made public for testing purposes.
    * @param creationRequest the sql request to analyze.
    * @return
    * @throws Exception
    */
   public final int checkTableForCreation(String creationRequest) throws Exception {
      String tmp = getObjectName("CREATE TABLE", creationRequest);
      if (tmp == null)
         return -1;
      String name = this.dbMetaHelper.getIdentifier(tmp);
      if (name == null)
         return -1;
      
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         ResultSet rs = conn.getMetaData().getTables(null, null, name, null);
         boolean exists = rs.next();
         rs.close();
         if (exists) {
            log.info("table '" +  name + "' exists, will not create it");
            return 0; 
         }
         else {
            log.info("table '" +  name + "' does not exist, will create it");
            return 1;
         }
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }

   protected abstract boolean sequenceExists(Connection conn, String sequenceName) throws Exception;
   
   protected abstract boolean triggerExists(Connection conn, String triggerName) throws Exception;
   /**
    * Checks if the sequence has to be created. 
    * If it is a 'CREATE SEQUENCE' operation a non-negative value is returned,
    * if it is another kind of operation, -1 is returned.
    * If the sequence already exists, it returns zero.
    * If the sequence does not exist, it returns 1.
    * @param creationRequest the sql request to analyze.
    * NOTE: only made public for testing purposes.
    * @return 
    * @throws Exception
    */
   public int checkSequenceForCreation(String creationRequest) throws Exception {
      String tmp = getObjectName("CREATE SEQUENCE", creationRequest);
      if (tmp == null)
         return -1;
      String name = this.dbMetaHelper.getIdentifier(tmp);
      if (name == null)
         return -1;
      
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         try {
            if (sequenceExists(conn, name)) {
               log.info("sequence '" +  name + "' exists, will not create it");
               return 0;
            }
            else {
               log.info("sequence '" +  name + "' does not exist, will create it");
               return 1;
            }
            //incrementReplKey(conn);
            //log.info("sequence '" +  name + "' exists, will not create it");
            //return 0; 
         }
         catch (Exception ex) {
            log.info("table '" +  name + "' does not exist (an exception occured), will create it");
            return 1;
         }
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }
   
   /**
    * Checks if the trigger has to be created.
    * If it is a 'CREATE TRIGGER' operation a non-negative value is returned,
    * if it is another kind of operation, -1 is returned.
    * If the triggger already exists, it returns zero.
    * If the trigger does not exist, it returns 1.
    * @param creationRequest the sql request to analyze.
    * NOTE: only made public for testing purposes.
    * @return 
    * @throws Exception
    */
   public final int checkTriggerForCreation(String creationRequest) throws Exception {
      String tmp = getObjectName("CREATE TRIGGER", creationRequest);
      if (tmp == null)
         return -1;
      String name = this.dbMetaHelper.getIdentifier(tmp);
      if (name == null)
         return -1;
      
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         try {
            if (triggerExists(conn, name)) {
               log.info("trigger '" +  name + "' exists, will not create it");
               return 0;
            }
            else {
               log.info("trigger '" +  name + "' does not exist, will create it");
               return 1;
            }
            //incrementReplKey(conn);
            //log.info("sequence '" +  name + "' exists, will not create it");
            //return 0; 
         }
         catch (Exception ex) {
            log.info("trigger '" +  name + "' does not exist (an exception occured), will create it");
            return 1;
         }
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }

   /**
    * Convenience method for nice output, also used to set the _destination property in the 
    * Client properties of a message.
    * @param str
    * @return
    */
   public static String toString(String[] str) {
      if (str == null)
         return "";
      StringBuffer buf = new StringBuffer();
      for (int i=0; i < str.length; i++) {
         if (i != 0)
            buf.append(",");
         buf.append(str[i]);
      }
      return buf.toString();
   }
   
   /**
    * Reads the content to be executed from a file.
    * 
    * @param conn The connection on which to operate. Must not be null.
    * @param method The method which uses this invocation (used for logging
    *           purposes).
    * @param propKey The name (or key) of the property to retrieve. The content of
    *           this property is the bootstrap file name
    * @param propDefault The default of the property.
    * @param force if force is true it will add it no matter what (overwrites 
    * existing stuff), otherwise it will check for existence.
    * @throws Exception if an exception occurs when reading the bootstrap file. Note
    * that in case of an exception you need to erase the connection from the pool (if you
    * are using a pool)
    */
   protected void updateFromFile(Connection conn, String method, String propKey,
         String propDefault, boolean doWarn, boolean force, Replacer repl) throws Exception {
      Statement st = null;
      String fileName = this.info.get(propKey, propDefault);
      final String FLUSH_SEPARATOR = "-- FLUSH";
      final String CMD_SEPARATOR = "-- EOC";
      List sqls = getContentFromClasspath(fileName, method, FLUSH_SEPARATOR, CMD_SEPARATOR);
      for (int i = 0; i < sqls.size(); i++) {
         String[] cmds = (String[]) sqls.get(i);
         String cmd = "";
         try {
            st = conn.createStatement();

            boolean doExecuteBatch = false;
            for (int j = 0; j < cmds.length; j++) {
               cmd = replaceTokens(cmds[j], repl);
               if (!force) {
                  if (checkTableForCreation(cmd) == 0)
                     continue;
                  if (checkSequenceForCreation(cmd) == 0)
                     continue;
                  if (checkTriggerForCreation(cmd) == 0)
                     continue;
               }
               if (cmd.trim().length() > 0) {
                  doExecuteBatch = true;
                  st.addBatch(cmd);
               }
            }
            if (doExecuteBatch) {
               st.executeBatch();
               if (!conn.getAutoCommit())
                  conn.commit();
            }
         } 
         catch (SQLException ex) {
            if (doWarn /*|| log.isLoggable(Level.FINE)*/) {
               StringBuffer buf = new StringBuffer();
               for (int j = 0; j < cmds.length; j++)
                  buf.append(cmd).append("\n");
               log.warning("operation:\n" + buf.toString() + "\n failed: " + ex.getMessage());
            }
            if (conn != null && !conn.getAutoCommit())
               conn.rollback();
         }
         catch (Throwable ex) {
            StringBuffer buf = new StringBuffer();
            for (int j = 0; j < cmds.length; j++)
               buf.append(cmd).append("\n");
            log.severe("operation:\n" + buf.toString() + "\n failed: " + ex.getMessage());
            if (conn != null && !conn.getAutoCommit())
               conn.rollback();
            if (ex instanceof Exception)
               throw (Exception)ex;
            else
               throw new Exception(ex);
         }
         finally {
            if (st != null) {
               st.close();
               st = null;
            }
         }
      }
   }

   /**
    * @see I_DbSpecific#bootstrap(Connection).
    * In case of an exception you need to cleanup the connection yourself.
    */
   public void bootstrap(Connection conn, boolean doWarn, boolean force)
         throws Exception {
      updateFromFile(conn, "bootstrap", "replication.bootstrapFile",
            "org/xmlBlaster/contrib/replication/setup/postgres/bootstrap.sql",
            doWarn, force, this.replacer);
   }

   /**
    * @see I_DbSpecific#cleanup(Connection). In case of an exception you need to cleanup
    * the connection yourself.
    */
   public void cleanup(Connection conn, boolean doWarn) throws Exception {
      /*
       * This cleans up the triggers on the own schema by oracle. It is needed
       * since if there is an 'unclean' zombie trigger, then no operation is 
       * possible anymore on the schema and cleanup will fail.
       */
      removeTrigger(null, null, true);
      String replTables = this.dbMetaHelper.getIdentifier(this.replPrefix + "TABLES");
      TableToWatchInfo[] tables = TableToWatchInfo.getAll(conn, replTables);
      HashSet set = new HashSet(); // to remember removed schema triggers
      for (int i=0; i < tables.length; i++) {
         String schema = tables[i].getSchema();
         boolean doRemove = !set.contains(schema);
         set.add(schema);
         removeTableToWatch(tables[i], doRemove);
      }
      updateFromFile(conn, "cleanup", "replication.cleanupFile",
            "org/xmlBlaster/contrib/replication/setup/postgres/cleanup.sql",
            doWarn, true, this.replacer);
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public final Set getUsedPropertyKeys() {
      Set set = new HashSet();
      set.add("replication.prefix");
      set.add("maxRowsOnCreate");
      PropertiesInfo.addSet(set, this.dbPool.getUsedPropertyKeys());
      PropertiesInfo.addSet(set, this.initialUpdater.getUsedPropertyKeys());
      return set;
   }

   /**
    * Returns a name identifying this SpecificDefault. This is the replication.prefix.
    * @return
    */
   public final String getName() {
      return this.replPrefix;
   }
   
   /**
    * @see I_DbSpecific#init(I_Info)
    * 
    */
   public synchronized void init(I_Info info) throws Exception {
      if (this.initCount > 0) {
         this.initCount++;
         return;
      }
      log.info("going to initialize the resources");
      this.replaceVariable = new ReplaceVariable();
      this.info = info;
      this.replPrefix = SpecificDefault.getReplPrefix(this.info);
      this.replVersion =  this.info.get("replication.version", "0.0");
      Map map = new HashMap();
      map.put("replVersion", this.replVersion);
      map.put("replPrefix", this.replPrefix);
      map.put("charWidth", this.info.get("replication.charWidth", "50"));
      map.put("charWidthSmall", this.info.get("replication.charWidthSmall", "10"));
      this.replacer = new Replacer(this.info, map);

      this.initialUpdater = new InitialUpdater(this);
      this.initialUpdater.init(info);

      this.dbPool = DbWatcher.getDbPool(this.info);
      this.dbMetaHelper = new DbMetaHelper(this.dbPool);
      this.rowsPerMessage = this.info.getInt("replication.maxRowsOnCreate", 250);
      
      if (this.isDbWriteable) {
         Connection conn = this.dbPool.reserve();
         try { // just to check that the configuration  is OK (better soon than later)
            TableToWatchInfo.getTablesToWatch(conn, this.info);
         }
         catch (Exception ex) {
            log.severe("The syntax of one of the 'tables' attributes in the configuration is wrong. " + ex.getMessage());
            throw ex;
         }
         finally {
            if (conn != null)
               this.dbPool.release(conn);
         }
      }
      
      if (this.isDbWriteable) {
         boolean needsPublisher = this.info.getBoolean(NEEDS_PUBLISHER_KEY, true);
         if (needsPublisher) {
            this.isInMaster = true;
            this.bootstrapWarnings = this.info.getBoolean("replication.bootstrapWarnings", false);
            doBootstrapIfNeeded();
         }
      }

      this.initCount++;
   }

   /**
    * Checks the consistency of the triggers. If an entry is found in the TABLES table, and the 
    * table does not exist, nothing is done.
    */
   public void checkTriggerConsistency(boolean doFix) throws Exception {
      Connection conn = this.dbPool.reserve();
      try {
         conn.setAutoCommit(true);
         TableToWatchInfo[] tables = TableToWatchInfo.getAll(conn, this.replPrefix + "TABLES");
         for (int i=0; i < tables.length; i++) {
            if (!triggerExists(conn, tables[i])) {
               String txt = "Trigger '" + tables[i].getTrigger() + "' on table '" + tables[i].getTable() + "' does in fact not exist.";
               if (doFix) {
                  // check first if the table really exists
                  ResultSet rs = conn.getMetaData().getTables(null, null, tables[i].getTable(), null);
                  try {
                     if (!rs.next()) {
                        log.info(txt + " and the table does not exist either. Will not do anything");
                        continue;
                     }
                  }
                  finally {
                     if (rs != null)
                        rs.close();
                  }
                  log.info(txt + " Will add it now");
                  tables[i].setStatus(TableToWatchInfo.STATUS_REMOVE);
                  tables[i].storeStatus(this.replPrefix, this.dbPool);
                  // addTrigger(conn, tables[i], null);
                  String catalog = tables[i].getCatalog();
                  String schema = tables[i].getSchema();
                  String table = tables[i].getTable();
                  readNewTable(catalog, schema, table, null, false);
               }
               else
                  log.info(txt);
            }
         }
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }
   
   
   /**
    * Checks wheter a bootstrapping is needed. If it is needed it will do first
    * a complete cleanup and therafter a bootstrap.
    * The criteria to decide wether it is needed or not is if the table
    * ${replPrefix}tables exists or not. It it does not exist, then it will do a
    * bootstrap.
    * 
    * @return
    * @throws Exception
    */
   private final void doBootstrapIfNeeded() throws Exception {
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         boolean noForce = false;
         bootstrap(conn, this.bootstrapWarnings, noForce);
         
         /*
         ResultSet rs = conn.getMetaData().getTables(null, null, this.dbMetaHelper.getIdentifier(this.replPrefix + "TABLES"), null);
         if (!rs.next()) {
            rs.close();
            boolean noWarn = false;
            boolean noForce = false;
            boolean doWarn = true;
            log.warning("A BOOTSTRAP IS NEEDED SINCE THE TABLE '" + this.replPrefix + "TABLES' has not been found");
            cleanup(conn, noWarn);
            bootstrap(conn, doWarn, noForce);
            return true;
         }
         else 
            rs.close();
         return false;
         */
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }
   
   
   /**
    * @see I_DbSpecific#shutdown()
    */
   public final synchronized void shutdown() throws Exception {
      this.initCount--;
      if (this.initCount > 0)
         return;
      try {
         log.info("going to shutdown: cleaning up resources");
         // registering this instance to the Replication Manager
         this.initialUpdater.shutdown();
         this.initialUpdater = null;
      } catch (Throwable e) {
         e.printStackTrace();
         log.warning(e.toString());
      }

      if (this.dbPool != null) {
         this.dbPool.shutdown();
      }
   }

   /**
    * Increments and retreives the ${replPrefix}key sequence counter. The connection
    * must not be null.
    * 
    * Description of sequences for oracle:
    * http://www.lc.leidenuniv.nl/awcourse/oracle/server.920/a96540/statements_615a.htm#2067095
    * 
    * 
    * @param conn
    * @return
    * @throws Exception
    * @see I_DbSpecific#incrementReplKey(Connection)
    * 
    */
   public long incrementReplKey(Connection conn) throws Exception {
      if (conn == null)
         throw new Exception(
               "SpecificDefault.incrementReplKey: the DB connection is null");
      CallableStatement st = null;
      try {
         st = conn.prepareCall("{? = call " + this.replPrefix + "increment()}");
         st.registerOutParameter(1, Types.INTEGER);
         st.executeQuery();
         long ret = st.getLong(1);
         return ret;
      } finally {
         try {
            if (st != null)
               st.close();
         } catch (Exception ex) {
         }
      }
   }

   /**
    * Adds a trigger.
    * 
    * @param conn
    * @param tableToWatch
    * @param sqlInfo
    * @throws Exception
    */
   private void addTrigger(Connection conn, TableToWatchInfo tableToWatch, SqlInfo sqlInfo) throws Exception {
      Statement st = null;
      String table = tableToWatch.getTable();
      try {
         if (!tableToWatch.getStatus().equals(TableToWatchInfo.STATUS_OK)) {
            String createString = createTableTrigger(sqlInfo.getDescription(), tableToWatch);
            if (createString != null && createString.length() > 1) {
               log.info("adding triggers to '" + table + "':\n\n" + createString);
               st = conn.createStatement();
               st.executeUpdate(createString);
               st.close();
            }
            tableToWatch.setStatus(TableToWatchInfo.STATUS_OK);
            tableToWatch.storeStatus(this.replPrefix, this.dbPool);
         }
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
   /**
    * @see I_DbSpecific#readNewTable(String, String, String, Map)
    */
   public final void readNewTable(String catalog, String schema, String table,
         Map attrs, boolean sendInitialContents) throws Exception {
      Connection conn = this.dbPool.reserve();
      int oldTransIsolation = 0;
      boolean oldTransIsolationKnown = false;

      try {
         conn.setAutoCommit(true);
         oldTransIsolation = conn.getTransactionIsolation();
         oldTransIsolationKnown = true;
         SqlInfo sqlInfo = new SqlInfo(this.info);

         if (catalog != null)
            catalog = this.dbMetaHelper.getIdentifier(catalog);
         if (schema != null)
            schema = this.dbMetaHelper.getIdentifier(schema);
         table = this.dbMetaHelper.getIdentifier(table);

         sqlInfo.fillMetadata(conn, catalog, schema, table, null, null);
         SqlDescription description = sqlInfo.getDescription();
         description.addAttributes(attrs);

         // check if function and trigger are necessary (they are only if the
         // table has to be replicated.
         // it does not need this if the table only needs an initial synchronization. 
         if (this.isDbWriteable) {
            TableToWatchInfo tableToWatch = TableToWatchInfo.get(conn, this.replPrefix + "tables", catalog, schema, table, null);
            
            if (tableToWatch != null) {
               boolean addTrigger = tableToWatch.isReplicate();
               if (addTrigger) { // create the function and trigger here
                  addTrigger(conn, tableToWatch, sqlInfo);
               }
               else
                  log.info("trigger will not be added since entry '" + tableToWatch.toXml() + "' will not be replicated");
            }
            else {
               log.info("table to watch '" + table + "' not found");
            }
         }
         conn.commit(); // just to make oracle happy for the next set transaction
         conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
         boolean autoCommit = false;
         conn.setAutoCommit(autoCommit);
         // retrieve the Sequence number here ...
         long newReplKey = incrementReplKey(conn);
         // publish the structure of the table (need to be done here since we
         // must retreive repl key after having added the trigger)
         
         if (sendInitialContents) {
            String destination = null;
            if (attrs != null)
                destination = (String)attrs.get("_destination");
            this.initialUpdater.publishCreate(0, sqlInfo, newReplKey, destination);
            if (schema != null)
               table = schema + "." + table;
            String sql = new String("SELECT * FROM " + table);
            I_ResultCb resultHandler = new ResultHandler(this.initialUpdater, sqlInfo, newReplKey, this.rowsPerMessage, destination); 
            this.dbPool.select(conn, sql, autoCommit, resultHandler);
         }
         conn.commit();
      } 
      catch (Exception ex) {
         removeFromPool(conn, ROLLBACK_YES);
         throw ex;
      } 
      finally {
         if (conn != null) {
            if (oldTransIsolationKnown) {
               try {
                  conn.setAutoCommit(true);
                  conn.setTransactionIsolation(oldTransIsolation);
               } 
               catch (Exception e) {
                  e.printStackTrace();
               }
            }
            conn = releaseIntoPool(conn, COMMIT_NO);
         }
      }
   }

   public void forceTableChangeCheck() throws Exception {
      Connection conn = null;
      CallableStatement st = null;
      try {
         String sql = "{? = call " + this.replPrefix + "check_structure()}";
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareCall(sql);
         st.registerOutParameter(1, Types.VARCHAR);
         st.executeQuery();
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_NO);
      }
      finally {
         try {
            if (st != null)
               st.close();
         } catch (Exception ex) {
            ex.printStackTrace();
         }
         conn = releaseIntoPool(conn, COMMIT_NO);
      }
   }

   /**
    * To use this method the arguments must already have been cleaned.
    * @param schema can not be null (use ' ' for null)
    * @return true if the table is found among the registered tables, false if not.
    * @throws SQLException
    */
   private final boolean isSchemaRegistered(Connection conn, String schema) throws SQLException {
      Statement st = null;
      try {
         // check wether the item already exists, if it exists return false
         String sql = "SELECT * FROM " + this.replPrefix + "tables WHERE schemaname='" + schema + "'";
         st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql);
         return rs.next();
      }
      finally {
         if (st != null) {
            try { st.close(); } catch (SQLException ex) {ex.printStackTrace();}
         }
      }
   }
   
   
   /**
    * @see I_DbSpecific#addTableToWatch(String, String, String, String, String, boolean, String, boolean)
    */
   public final boolean addTableToWatch(TableToWatchInfo firstTableToWatch, boolean force, String[] destinations, boolean forceSend) throws Exception {
      String catalog = firstTableToWatch.getCatalog();
      String schema = firstTableToWatch.getSchema();
      String tableName = firstTableToWatch.getTable();
      String actions = firstTableToWatch.getActions();
      String triggerName = firstTableToWatch.getTrigger();
      if (catalog != null && catalog.trim().length() > 0)
         catalog = this.dbMetaHelper.getIdentifier(catalog);
      else
         catalog = " ";
      if (schema != null && schema.trim().length() > 0)
         schema = this.dbMetaHelper.getIdentifier(schema);
      else
         schema = " ";
      tableName = this.dbMetaHelper.getIdentifier(tableName);

      Connection conn = null;
      log.info("Checking for addition of '" + tableName + "'");
      try {
         conn = this.dbPool.reserve();
         conn.setAutoCommit(false);
         long tmp = this.incrementReplKey(conn);
         if (!isSchemaRegistered(conn, schema)) {
            log.info("schema '" + schema + "' is not registered, going to add it");
            addSchemaToWatch(conn, catalog, schema);
         }

         final String TABLES_TABLE = this.dbMetaHelper.getIdentifier(this.replPrefix + "TABLES");
         TableToWatchInfo tableToWatch = null;
         tableToWatch = TableToWatchInfo.get(conn, TABLES_TABLE, catalog, schema, tableName, tableToWatch);
         if (!conn.getAutoCommit())
            conn.commit(); // to be sure it is a new transaction
         if (!force && tableToWatch != null && tableToWatch.isStatusOk(this, conn)) {
            // send it manually since table exits already and trigger is OK.
            log.info("table '" + tableName + "' is already registered, will add directly an entry in the ENTRIES Table");
            String destAttrName = "?";
            if (destinations == null || destinations.length == 0)
               destAttrName = "NULL";
            String sql = "{? = call " + this.replPrefix + "check_tables(NULL,?,?,?," + destAttrName + ")}"; // name text, content text)
            CallableStatement st = conn.prepareCall(sql);
            st.setString(2, schema);
            st.setString(3, tableName);
            st.setString(4, ReplicationConstants.CREATE_ACTION);
            if (destinations != null && destinations.length != 0) {
               String post = "</desc>";
               if (forceSend)
                  post = "<attr id='_forceSend'>true</attr>" + post;
               String destinationTxt = "<desc><attr id='_destination'>" + toString(destinations) + "</attr>" + post;
               st.setString(5, destinationTxt);
            }
            st.registerOutParameter(1, Types.VARCHAR);
            st.executeQuery();
            st.close();
            return false;
         }
         /*
         if (force) {
            if (isTableRegistered(conn, tableToWatch)) {
               log.info("table '" + tableName + "' is already registered and 'force' has been choosed. Will set its status to 'REMOVE'");
               tableToWatch.setStatus(TableToWatchInfo.STATUS_REMOVE);
               tableToWatch.storeStatus(this.replPrefix, this.dbPool);
            }
         }
         */
         // then it is either not OK or force true. or null 
         if (tableToWatch != null) // then it is either not OK or force true. In both cases we need to remove old entry
            tableToWatch.removeFromDb(this.replPrefix, this.dbPool);
         
         if (triggerName == null)
            triggerName = this.replPrefix + tmp;
         triggerName = this.dbMetaHelper.getIdentifier(triggerName);
         
         long debug = 0;
         TableToWatchInfo finalTableToWatch = new TableToWatchInfo(catalog, schema, tableName);
         finalTableToWatch.setActions(actions);
         finalTableToWatch.setTrigger(triggerName);
         finalTableToWatch.setDebug((int)debug);
         finalTableToWatch.setReplKey(tmp);
         finalTableToWatch.store(this.replPrefix, this.dbPool, conn);
         
         return true;
      }
      catch (Throwable ex) {
         conn = removeFromPool(conn, ROLLBACK_YES);
         if (ex instanceof Exception)
            throw (Exception)ex;
         throw new Exception(ex);
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_YES);
      }
   }

   /**
    * Currently made public for testing.
    * @param schema
    */
   public void removeSchemaTriggers(String schema) {
      removeTrigger(this.replPrefix + "drtg_" + schema, null, true);
      removeTrigger(this.replPrefix + "altg_" + schema, null, true);
      removeTrigger(this.replPrefix + "crtg_" + schema, null, true);
   }
   
   /**
    * @see I_DbSpecific#removeTableToWatch(String)
    */
   public final void removeTableToWatch(TableToWatchInfo tableToWatch, boolean removeAlsoSchemaTrigger) throws Exception {
      String catalog = tableToWatch.getCatalog();
      String schema =  tableToWatch.getSchema();
      String tableName = tableToWatch.getTable();
      if (catalog != null && catalog.trim().length() > 0)
         catalog = this.dbMetaHelper.getIdentifier(catalog);
      else
         catalog = " ";
      if (schema != null && schema.trim().length() > 0)
         schema = this.dbMetaHelper.getIdentifier(schema);
      else
         schema = " ";
      tableName = this.dbMetaHelper.getIdentifier(tableName);

      String sql = "DELETE FROM " + this.replPrefix + "tables WHERE tablename='" + tableName
            + "' AND schemaname='" + schema + "' AND catalogname='" + catalog
            + "'";
      this.dbPool.update(sql);
      if (removeAlsoSchemaTrigger)
         removeSchemaTriggers(schema);

      if (tableToWatch.isReplicate()) {
         String triggerName = tableToWatch.getTrigger();
         removeTrigger(triggerName, tableName, false);
      }
   }

   /**
    * @see I_DbSpecific#getCreateTableStatement(SqlDescription,
    *      I_Mapper)
    */
   public final String getCreateTableStatement(
         SqlDescription infoDescription, I_Mapper mapper) {
      SqlColumn[] cols = infoDescription
            .getColumns();
      StringBuffer buf = new StringBuffer(1024);
      String originalTableName = infoDescription.getIdentity();
      String originalSchema = infoDescription.getSchema();
      String originalCatalog = infoDescription.getCatalog();
      String completeTableName = null;
      if (mapper != null) {
         String schema = null;
         String tableName = mapper.getMappedTable(originalCatalog, originalSchema, originalTableName, null, originalTableName);
         if (originalSchema != null)
            schema = mapper.getMappedSchema(originalCatalog, originalSchema, originalTableName, null, originalSchema);
         if (schema != null)
            completeTableName = schema + "." + tableName;
         else
            completeTableName = tableName;
      }
      else {
         if (originalSchema != null)
            completeTableName = originalSchema + "." + originalTableName;
         else
            completeTableName = originalTableName;
      }
      
      buf.append("CREATE TABLE ").append(completeTableName).append(" (");
      StringBuffer pkBuf = new StringBuffer();
      boolean hasPkAlready = false;
      for (int i = 0; i < cols.length; i++) {
         if (i != 0)
            buf.append(",");
         buf.append(getColumnStatement(cols[i]));
         if (cols[i].isPrimaryKey()) {
            buf.append(" NOT NULL");
            if (hasPkAlready)
               pkBuf.append(",");
            pkBuf.append(cols[i].getColName());
            hasPkAlready = true;
         }
      }
      if (hasPkAlready)
         buf.append(", PRIMARY KEY (").append(pkBuf).append(")");
      buf.append(")");
      return buf.toString();
   }

   /**
    * If force is true, it deletes first all entries from the Tables table (kind of reset).
    */
   public void addTriggersIfNeeded(boolean force, String[] destinations, boolean forceSend) throws Exception {
      if (force) {
         try {
            this.dbPool.update("DELETE FROM " + this.dbMetaHelper.getIdentifier(this.replPrefix + "TABLES"));
         }
         catch (Exception ex) {
            log.warning("Could not delete tables configuration before adding triggers with 'force' true");
            ex.printStackTrace();
         }
      }
      final boolean doFix = true;
      checkTriggerConsistency(doFix);
      Connection conn = this.dbPool.reserve();
      try {
         TableToWatchInfo[] tablesToWatch = TableToWatchInfo.getTablesToWatch(conn, this.info);
         log.info("there are '" + tablesToWatch.length + "' tables to watch (invoked with forceSend='" + forceSend + "'");
         for (int i=0; i < tablesToWatch.length; i++)
            addTableToWatch(tablesToWatch[i], force, destinations, forceSend);
      }
      finally {
         if (conn != null)
            this.dbPool.release(conn);
      }
   }

   /**
    * 
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initiateUpdate(java.lang.String)
    */
   public void initiateUpdate(String topic, String replManagerAddress, String[] slaveNames, String requestedVersion, String initialFilesLocation) throws Exception {
      
      log.info("initial replication for destinations='" + replManagerAddress + "' and slaves='" + toString(slaveNames) + "' and location '" + initialFilesLocation + "'");
      Connection conn = null;
      // int oldTransactionIsolation = Connection.TRANSACTION_SERIALIZABLE;
      // int oldTransactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
      int oldTransactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
      try {
         if (this.dbPool == null)
            throw new Exception("intitiate update: The Database pool has not been instantiated (yet)");
         conn = this.dbPool.reserve();
         conn.setAutoCommit(false);
         oldTransactionIsolation = conn.getTransactionIsolation();
         conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
         // the result must be sent as a high prio message to the real destination
         boolean forceFlag = false;
         boolean isRequestingCurrentVersion = false;
         log.info("current replication version is *" + this.replVersion + "' and requested version is '" + requestedVersion + "'");
         if (this.replVersion.equalsIgnoreCase(requestedVersion))
            isRequestingCurrentVersion = true;
         boolean forceSend = !isRequestingCurrentVersion;
         addTriggersIfNeeded(forceFlag, slaveNames, forceSend);
         InitialUpdater.ConnectionInfo connInfo = this.initialUpdater.getConnectionInfo(conn);
         long minKey = this.incrementReplKey(conn);
         String filename = null;
         String completeFilename = null;
         if (isRequestingCurrentVersion)
            filename = this.initialUpdater.initialCommand(slaveNames, completeFilename, connInfo, requestedVersion);
         else
            filename = VersionTransformerCache.buildFilename(this.replPrefix, requestedVersion);
         
         long maxKey = this.incrementReplKey(conn); 
         // if (!connInfo.isCommitted())
         conn.commit();
         List slavesList = new ArrayList();
         for (int i=0; i < slaveNames.length; i++) {
            if (!isCancelled(slaveNames[i]))
               slavesList.add(slaveNames[i]);
         }
         slaveNames = (String[])slavesList.toArray(new String[slavesList.size()]);
         this.initialUpdater.sendInitialDataResponse(slaveNames, filename, replManagerAddress, minKey, maxKey, requestedVersion, this.replVersion, initialFilesLocation);
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_YES);
         ex.printStackTrace();
      }
      finally {
         if (conn != null) {
            if (oldTransactionIsolation != Connection.TRANSACTION_READ_COMMITTED) {
               try {
                  conn.setTransactionIsolation(oldTransactionIsolation);
               }
               catch (SQLException e) { 
                  e.printStackTrace(); 
               }
            }
            // we always throw away the connection on initial update (to be on the safe side)
            // if rollback was done before this will not execute anything since conn=null 
            conn = removeFromPool(conn, ROLLBACK_NO);
         }
      }
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initialCommand(java.lang.String, java.lang.String)
    */
   public void initialCommand(String[] slaveNames, String completeFilename, String version) throws Exception {
      this.initialUpdater.initialCommand(slaveNames, completeFilename, null, version);
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initialCommandPre()
    */
   public void initialCommandPre() throws Exception {
      this.initialUpdater.initialCommandPre();
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#broadcastStatement(java.lang.String, long, long, boolean, boolean, String, String)
    */
   public byte[] broadcastStatement(String sql, long maxResponseEntries, boolean isHighPrio, boolean isMaster, String sqlTopic, String statementId) throws Exception {
      Connection conn = this.dbPool.reserve();
      byte[] response = null;
      try {
         conn.setAutoCommit(false);
         if (this.isInMaster) {
            CallableStatement st = null;
            try {
               StringBuffer buf = new StringBuffer();
               // buf.append("<desc>\n");
               buf.append("<attr id='").append(ReplicationConstants.STATEMENT_ATTR).append("'>").append(sql).append("</attr>\n");
               buf.append("<attr id='").append(ReplicationConstants.STATEMENT_PRIO_ATTR).append("'>").append(isHighPrio).append("</attr>\n");
               buf.append("<attr id='").append(ReplicationConstants.MAX_ENTRIES_ATTR).append("'>").append(maxResponseEntries).append("</attr>\n");
               buf.append("<attr id='").append(ReplicationConstants.STATEMENT_ID_ATTR).append("'>").append(statementId).append("</attr>\n");
               buf.append("<attr id='").append(ReplicationConstants.SQL_TOPIC_ATTR).append("'>").append(sqlTopic).append("</attr>\n");
               // buf.append("</desc>\n");
               
               String sqlTxt = "{? = call " + this.replPrefix + "prepare_broadcast(?)}";
               st = conn.prepareCall(sqlTxt);
               String value = buf.toString();
               st.setString(2, value);
               st.registerOutParameter(1, Types.VARCHAR);
               st.executeQuery();
            }
            finally {
               st.close();
            }
         }
         
         Statement st2 = conn.createStatement();
         try {
            if (st2.execute(sql)) {
               ResultSet rs = st2.getResultSet();
               response = ResultSetToXmlConverter.getResultSetAsXmlLiteral(rs, "statement", "query", maxResponseEntries);
            }
            else {
               int updateCount = st2.getUpdateCount();
               StringBuffer buf1 = new StringBuffer();
               buf1.append("<sql>\n");
               buf1.append("  <desc>\n");
               buf1.append("    <command>").append("statement").append("</command>");
               buf1.append("    <ident>").append("update").append("</ident>");
               buf1.append("    <attr id='").append("updateCount").append("'>").append(updateCount).append("</attr>");
               buf1.append("  </desc>\n");
               buf1.append("</sql>\n");
               response = buf1.toString().getBytes();
            }
            // TODO make this a fine
            log.info("statement to broadcast shall give this response: " + new String(response));
         }
         finally {
            if (st2 != null)
               st2.close();
         }
         return response;
      }
      catch (Exception ex) {
         conn = removeFromPool(conn, ROLLBACK_YES);
         throw ex;
      }
      finally {
         conn = releaseIntoPool(conn, COMMIT_YES);
      }
   }

   /**
    * Always returns null (to nullify the connection).
    * @param conn The connection. Can be null, in which case nothing is done.
    * @param doRollback if true, a rollback is done, on false no rollback is done.
    * @return always null.
    */
   protected Connection removeFromPool(Connection conn, boolean doRollback) {
      return removeFromPool(conn, doRollback, this.dbPool);
   }
   
   /**
    * Always returns null (to nullify the connection).
    * @param conn The connection. Can be null, in which case nothing is done.
    * @param doRollback if true, a rollback is done, on false no rollback is done.
    * @param pool the pool to which the connection belongs.
    * @return always null.
    */
   public static Connection removeFromPool(Connection conn, boolean doRollback, I_DbPool pool) {
      log.fine("Removing from Database pool of connection (rollback='" + doRollback + "')");
      if (conn == null)
         return null;
      if (doRollback) {
         try {
            conn.rollback();
         }
         catch (Throwable ex) {
            log.severe("An exception occured when trying to rollback the jdbc connection. " + ex.getMessage());
            ex.printStackTrace();
         }
      }
      try {
         pool.erase(conn);
      }
      catch (Throwable ex) {
         log.severe("An exception occured when trying to erase the connection from the pool. " + ex.getMessage());
         ex.printStackTrace();
      }
      return null;
   }
   

   /**
    * Always returns null (to nullify the connection).
    * @param conn The connection. Can be null, in which case nothing is done.
    * @param doCommit if true, a commit is done, on false no commit is done.
    * @return always null.
    */
   protected Connection releaseIntoPool(Connection conn, boolean doCommit) {
      return releaseIntoPool(conn, doCommit, this.dbPool);
   }
   
   /**
    * Always returns null (to nullify the connection).
    * @param conn The connection. Can be null, in which case nothing is done.
    * @param doCommit if true, a commit is done, on false no commit is done.
    * @param pool the pool to which the connection belongs.
    * @return always null.
    */
   public static Connection releaseIntoPool(Connection conn, boolean doCommit, I_DbPool pool) {
      if (conn == null)
         return null;
      if (doCommit) {
         try {
            conn.commit();
         }
         catch (Throwable ex) {
            ex.printStackTrace();
         }
      }
      try {
         pool.release(conn);
      }
      catch (Throwable ex) {
         log.severe("An exception occured when trying to release the connection into the pool. " + ex.getMessage());
         ex.printStackTrace();
      }
      return null;
   }
   
   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#cancelUpdate(java.lang.String)
    */
   public void cancelUpdate(String replSlave) {
      synchronized(this.cancelledUpdates) {
         this.cancelledUpdates.add(replSlave);
      }
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#clearCancelUpdate(java.lang.String)
    */
   public void clearCancelUpdate(String replSlave) {
      synchronized(this.cancelledUpdates) {
         this.cancelledUpdates.remove(replSlave);
      }
   }
   
   private boolean isCancelled(String replSlave) {
      synchronized(this.cancelledUpdates) {
         return this.cancelledUpdates.contains(replSlave);
      }
   }
   
   public static String getReplPrefix(I_Info info) {
      String pureVal = info.get(ReplicationConstants.REPL_PREFIX_KEY, ReplicationConstants.REPL_PREFIX_DEFAULT);
      String corrected = GlobalInfo.getStrippedString(pureVal);
      if (!corrected.equals(pureVal))
         log.warning("The " + ReplicationConstants.REPL_PREFIX_KEY + " property has been changed from '" + pureVal + "' to '" + corrected + "' to be able to use it inside a DB");
      return corrected;
   }
   
   /**
    * Example code.
    * <p />
    * <tt>java -Djava.util.logging.config.file=testlog.properties org.xmlBlaster.contrib.replication.ReplicationManager -db.password secret</tt>
    * 
    * @param args
    *           Command line
    */
   public static void main(String[] args) {
      I_DbPool pool = null;
      Connection conn = null;
      try {
         
         System.setProperty("java.util.logging.config.file",
               "testlog.properties");
         // LogManager.getLogManager().readConfiguration();

         // Preferences prefs = Preferences.userRoot();
         // prefs.node(ReplicationConstants.CONTRIB_PERSISTENT_MAP).clear();
         // prefs.clear();

         // ---- Database settings -----
         if (System.getProperty("jdbc.drivers", null) == null) {
            System.setProperty(
                        "jdbc.drivers",
                        "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         }
         if (System.getProperty("db.url", null) == null) {
            System.setProperty("db.url", "jdbc:postgresql:test//localhost/test");
         }
         if (System.getProperty("db.user", null) == null) {
            System.setProperty("db.user", "postgres");
         }
         if (System.getProperty("db.password", null) == null) {
            System.setProperty("db.password", "");
         }

         I_Info info = new PropertiesInfo(System.getProperties());
         boolean forceCreationAndInit = true;
         I_DbSpecific specific = ReplicationConverter.getDbSpecific(info, forceCreationAndInit);
         pool = (I_DbPool) info.getObject("db.pool");
         conn = pool.reserve();
         conn.setAutoCommit(true);
         String schema = info.get("wipeout.schema", null);
         String version = info.get("replication.version", "0.0");
         if (schema == null) {
            String initialUpdateFile = info.get("initialUpdate.file", null);
            if (initialUpdateFile != null) {
               specific.initialCommand(null, initialUpdateFile, version);
            }
            else
               specific.cleanup(conn, true);
         }
         else {
            specific.wipeoutSchema(null, schema, WIPEOUT_ALL);
         }
      } 
      catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
         conn = SpecificDefault.removeFromPool(conn, ROLLBACK_NO, pool);
      }
      finally {
         if (pool != null) {
            conn = releaseIntoPool(conn, COMMIT_NO, pool);
         }
      }
   }

}
