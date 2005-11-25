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
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.TableToWatchInfo;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;

public abstract class SpecificDefault implements I_DbSpecific, I_ResultCb {

   private static Logger log = Logger.getLogger(SpecificDefault.class.getName());

   private int rowsPerMessage = 10;

   private SqlInfo dbUpdateInfo;

   private long newReplKey;

   protected I_Info info;

   protected I_DbPool dbPool;

   protected DbMetaHelper dbMetaHelper;

   protected String replPrefix = "repl_";
   
   protected ReplaceVariable replaceVariable;

   protected Replacer replacer;

   private InitialUpdater initialUpdater;

   private boolean bootstrapWarnings;
   
   private int initCount = 0;
   
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
            log.fine(method + ": : loading file '" + url.getFile() + "'");
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
      finally {
         if (conn != null)
            this.dbPool.release(conn);
      }
   }

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
   public final int checkSequenceForCreation(String creationRequest) throws Exception {
      String tmp = getObjectName("CREATE SEQUENCE", creationRequest);
      if (tmp == null)
         return -1;
      String name = this.dbMetaHelper.getIdentifier(tmp);
      if (name == null)
         return -1;
      
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         try {
            incrementReplKey(conn);
            log.info("sequence '" +  name + "' exists, will not create it");
            return 0; 
         }
         catch (Exception ex) {
            log.info("table '" +  name + "' does not exist, will create it");
            return 1;
         }
      }
      finally {
         if (conn != null)
            this.dbPool.release(conn);
      }
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
    * @throws Exception if an exception occurs when reading the bootstrap file.
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
            conn.setAutoCommit(true);
            st = conn.createStatement();

            boolean doExecuteBatch = false;
            for (int j = 0; j < cmds.length; j++) {
               cmd = replaceTokens(cmds[j], repl);
               if (!force) {
                  if (checkTableForCreation(cmd) == 0)
                     continue;
                  if (checkSequenceForCreation(cmd) == 0)
                     continue;
               }
               if (cmd.trim().length() > 0) {
                  doExecuteBatch = true;
                  st.addBatch(cmd);
               }
            }
            if (doExecuteBatch)
               st.executeBatch();
         } 
         catch (SQLException ex) {
            if (doWarn /*|| log.isLoggable(Level.FINE)*/) {
               StringBuffer buf = new StringBuffer();
               for (int j = 0; j < cmds.length; j++)
                  buf.append(cmd).append("\n");
               log.warning("operation:\n" + buf.toString() + "\n failed: " + ex.getMessage());
            }
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
    * @see I_DbSpecific#bootstrap(Connection)
    */
   public final void bootstrap(Connection conn, boolean doWarn, boolean force)
         throws Exception {
      updateFromFile(conn, "bootstrap", "replication.bootstrapFile",
            "org/xmlBlaster/contrib/replication/setup/postgres/bootstrap.sql",
            doWarn, force, this.replacer);
   }

   /**
    * @see I_DbSpecific#cleanup(Connection)
    */
   public final void cleanup(Connection conn, boolean doWarn) throws Exception {
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
         this.removeTableToWatch(tables[i], doRemove);
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
      this.replPrefix = this.info.get("replication.prefix", "repl_");
      Map map = new HashMap();
      map.put("replPrefix", this.replPrefix);
      this.replacer = new Replacer(this.info, map);

      this.initialUpdater = new InitialUpdater(this);
      this.initialUpdater.init(info);

      this.dbPool = DbWatcher.getDbPool(this.info);
      this.dbMetaHelper = new DbMetaHelper(this.dbPool);
      this.rowsPerMessage = this.info.getInt("maxRowsOnCreate", 10);
      try { // just to check that the configuration  is OK (better soon than later)
         TableToWatchInfo.getTablesToWatch(this.info);
      }
      catch (Exception ex) {
         log.severe("The syntax of one of the 'tables' attributes in the configuration is wrong. " + ex.getMessage());
         throw ex;
      }
      
      // do a bootstrapping if needed !!!!!
      boolean needsPublisher = this.info.getBoolean(NEEDS_PUBLISHER_KEY, true);
      if (needsPublisher) {
         this.bootstrapWarnings = this.info.getBoolean("replication.bootstrapWarnings", false);
         doBootstrapIfNeeded();
      }
      this.initCount++;
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
      finally {
         if (conn == null) {
            this.dbPool.release(conn);
         }
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
   public final long incrementReplKey(Connection conn) throws Exception {
      if (conn == null)
         throw new Exception(
               "SpecificDefault.incrementReplKey: the DB connection is null");
      CallableStatement st = null;
      try {
         // st = conn.prepareCall("{? = call nextval('" + this.replPrefix + "seq')}");
         st = conn.prepareCall("{? = call " + this.replPrefix + "increment()}");
         // st.registerOutParameter(1, Types.BIGINT);
         st.registerOutParameter(1, Types.INTEGER);
         st.executeQuery();
         return st.getLong(1);
      } finally {
         try {
            if (st != null)
               st.close();
         } catch (Exception ex) {
         }
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
         this.dbUpdateInfo = new SqlInfo(this.info);

         if (catalog != null)
            catalog = this.dbMetaHelper.getIdentifier(catalog);
         if (schema != null)
            schema = this.dbMetaHelper.getIdentifier(schema);
         table = this.dbMetaHelper.getIdentifier(table);

         this.dbUpdateInfo.fillMetadata(conn, catalog, schema, table, null,
               null);
         SqlDescription description = this.dbUpdateInfo
               .getDescription();
         description.addAttributes(attrs);
         this.newReplKey = 0;

         // check if function and trigger are necessary (they are only if the
         // table has to be replicated.
         // it does not need this if the table only needs an initial synchronization. 
         TableToWatchInfo tableToWatch = TableToWatchInfo.get(conn, this.replPrefix + "tables", catalog, schema, table);
         
         if (tableToWatch != null) {
            String triggerName = tableToWatch.getTrigger();
            boolean addTrigger = tableToWatch.isReplicate();
            Statement st = null;
            if (addTrigger) { // create the function and trigger here
               String createString = createTableTrigger(this.dbUpdateInfo.getDescription(), triggerName, tableToWatch.getFlags());
               if (createString != null && createString.length() > 1) {
                  log.info("adding triggers to '" + table + "':\n\n" + createString);
                  st = conn.createStatement();
                  st.executeUpdate(createString);
                  st.close();
               }
            }
            else
               log.info("trigger will not be added since entry '" + tableToWatch.toXml() + "' will not be replicated");
         }
         else
            log.info("table to watch '" + table + "' not found");

         conn.commit(); // just to make oracle happy for the next set transaction
         conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
         conn.setAutoCommit(false);
         // retrieve the Sequence number here ...
         this.newReplKey = incrementReplKey(conn);
         // publish the structure of the table (need to be done here since we
         // must retreive repl key after having added the trigger)
         
         if (sendInitialContents) {
            this.initialUpdater.publishCreate(0, this.dbUpdateInfo, this.newReplKey);
            String sql = new String("SELECT * FROM " + table);
            this.dbPool.select(conn, sql, false, this);
         }
         conn.commit();
      } 
      catch (Exception ex) {
         if (conn != null) {
            conn.rollback();
            this.dbPool.erase(conn);
            conn = null;
         }
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
            this.dbPool.release(conn);
         }
      }
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
            this.dbUpdateInfo.fillOneRowWithStringEntries(rs, null);
            internalCount++;
            if (internalCount == this.rowsPerMessage) {
               internalCount = 0;
               // publish
               log.info("result: going to publish msg '" + msgCount
                     + "' and internal count '" + internalCount + "'");
               this.initialUpdater.publishCreate(msgCount++, this.dbUpdateInfo, this.newReplKey);
               this.dbUpdateInfo.getRows().clear(); // clear since re-using
                                                      // the same dbUpdateInfo
            }
         } // end while
         if (this.dbUpdateInfo.getRows().size() > 0) {
            log.info("result: going to publish last msg '" + msgCount
                  + "' and internal count '" + internalCount + "'");
            this.initialUpdater.publishCreate(msgCount, this.dbUpdateInfo, this.newReplKey);
         }
      } catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't publish change event meat for CREATE");
      }
   }

   public final void forceTableChangeCheck() throws Exception {
      Connection conn = null;
      CallableStatement st = null;
      try {
         String sql = "{? = call " + this.replPrefix + "check_structure()}";
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareCall(sql);
         st.registerOutParameter(1, Types.VARCHAR);
         st.executeQuery();
      } finally {
         if (conn != null) {
            try {
               if (st != null)
                  st.close();
            } catch (Exception ex) {
            }
            this.dbPool.release(conn);
         }
      }
   }

   
   /**
    * To use this method the arguments must already have been cleaned.
    * @param catalog can not be null (use ' ' for null).
    * @param schema can not be null (use ' ' for null)
    * @param tablename can not be null
    * @return true if the table is found among the registered tables, false if not.
    * @throws SQLException
    */
   private final boolean isTableRegistered(Connection conn, String catalog, String schema, String tableName) throws SQLException {
      Statement st = null;
      ResultSet rs = null;
      try {
         // check wether the item already exists, if it exists return false
         String sql = "SELECT * FROM " + this.replPrefix + "tables WHERE catalogname='" + catalog + "' AND schemaname='" + schema + "' AND tablename='" + tableName + "'";
         st = conn.createStatement();
         rs = st.executeQuery(sql);
         return rs.next();
      }
      finally {
         if (rs == null) {
            try { rs.close(); } catch (SQLException ex) {}
         }
         if (st == null) {
            try { st.close(); } catch (SQLException ex) {}
         }
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
      ResultSet rs = null;
      try {
         // check wether the item already exists, if it exists return false
         String sql = "SELECT * FROM " + this.replPrefix + "tables WHERE schemaname='" + schema + "'";
         st = conn.createStatement();
         rs = st.executeQuery(sql);
         return rs.next();
      }
      finally {
         if (rs == null) {
            try { rs.close(); } catch (SQLException ex) {}
         }
         if (st == null) {
            try { st.close(); } catch (SQLException ex) {}
         }
      }
   }
   
   /**
    * @see I_DbSpecific#addTableToWatch(String, boolean)
    */
   public final boolean addTableToWatch(String catalog, String schema, String tableName,
         String replFlags, String triggerName) throws Exception {
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
         long tmp = this.incrementReplKey(conn);
         if (!isSchemaRegistered(conn, schema)) {
            log.info("schema '" + schema + "' is not registered, going to add it");
            addSchemaToWatch(conn, catalog, schema);
         }
         if (isTableRegistered(conn, catalog, schema, tableName)) {
            log.info("table '" + tableName + "' is already registered, will not add it");
            return false;
         }
         if (triggerName == null)
            triggerName = this.replPrefix + tmp;
         triggerName = this.dbMetaHelper.getIdentifier(triggerName);
         String sql = "INSERT INTO " + this.replPrefix + "tables VALUES ('" + catalog + "','"
               + schema + "','" + tableName + "','" + replFlags
               + "', 'CREATING'," + tmp + ",'" + triggerName + "')";
         log.info("Inserting the statement '" + sql + "'");
         this.dbPool.update(conn, sql);
         return true;
      } 
      finally {
         if (conn != null)
            this.dbPool.release(conn);
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
      String tableName = infoDescription.getIdentity();
      if (mapper != null)
         tableName = mapper.getMappedTable(null, null, tableName, null);
      buf.append("CREATE TABLE ").append(tableName).append(" (");
      StringBuffer pkBuf = new StringBuffer();
      boolean hasPkAlready = false;
      for (int i = 0; i < cols.length; i++) {
         if (i != 0)
            buf.append(",");
         buf.append(getColumnStatement(cols[i]));
         if (cols[i].isPrimaryKey()) {
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

   private final void addTriggersIfNeeded() throws Exception {
      TableToWatchInfo[] tablesToWatch = TableToWatchInfo.getTablesToWatch(this.info);
      log.info("there are '" + tablesToWatch.length + "' tables to watch");
      for (int i=0; i < tablesToWatch.length; i++) {
         String catalog = tablesToWatch[i].getCatalog();
         String schema = tablesToWatch[i].getSchema();
         String table = tablesToWatch[i].getTable();
         String replFlags = tablesToWatch[i].getFlags();
         String trigger =  tablesToWatch[i].getTrigger();
         addTableToWatch(catalog, schema, table, replFlags, trigger);
      }
   }

   /**
    * 
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initiateUpdate(java.lang.String)
    */
   public final void initiateUpdate(String topic, String destination, String slaveName) throws Exception {
      
      log.info("initial replication for destination=*" + destination + "' and slave='" + slaveName + "'");
      Connection conn = null;
      // int oldTransactionIsolation = Connection.TRANSACTION_SERIALIZABLE;
      // int oldTransactionIsolation = Connection.TRANSACTION_REPEATABLE_READ;
      int oldTransactionIsolation = Connection.TRANSACTION_READ_COMMITTED;
      try {
         conn = this.dbPool.reserve();
         oldTransactionIsolation = conn.getTransactionIsolation();
         conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
         long minKey = this.incrementReplKey(conn);
         // the result must be sent as a high prio message to the real
         // destination
         addTriggersIfNeeded();
         InitialUpdater.ConnectionInfo connInfo = this.initialUpdater.getConnectionInfo(conn);
         String filename = this.initialUpdater.initialCommand(null, connInfo);
         
         long maxKey = this.incrementReplKey(conn); 
         // if (!connInfo.isCommitted())
         conn.commit();
         this.initialUpdater.sendInitialDataResponse(topic, filename, destination, slaveName, minKey, maxKey);
      }
      catch (Exception ex) {
         if (conn != null) {
            try {
               conn.rollback();
            }
            catch (SQLException e) { }
            try {
               this.dbPool.erase(conn);
            }
            catch (Exception e) { }
            conn = null;
         }
         ex.printStackTrace();
      }
      finally {
         if (conn != null) {
            if (oldTransactionIsolation != Connection.TRANSACTION_READ_COMMITTED)
               try {
                  conn.setTransactionIsolation(oldTransactionIsolation);
               }
               catch (SQLException e) { }
               try {
                  conn.close();
               }
               catch (SQLException e) { }
         }
      }
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
      try {
         System.setProperty("java.util.logging.config.file",
               "testlog.properties");
         LogManager.getLogManager().readConfiguration();

         Preferences prefs = Preferences.userRoot();
         prefs.clear();

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
         I_DbSpecific specific = ReplicationConverter.getDbSpecific(info);
         I_DbPool pool = (I_DbPool) info.getObject("db.pool");
         Connection conn = pool.reserve();
         String schema = info.get("wipeout.schema", null);
         if (schema == null) {
            specific.cleanup(conn, true);
         }
         else {
            specific.wipeoutSchema(null, schema);
         }
         pool.release(conn);
      } catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
      }
   }

   /**
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initialCommand(java.lang.String)
    */
   public void initialCommand(String completeFilename) throws Exception {
      this.initialUpdater.initialCommand(completeFilename, null);
   }
   

}
