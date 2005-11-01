/*------------------------------------------------------------------------------
 Name:      SpecificDefault.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 ------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.jms.DeliveryMode;
import javax.jms.JMSException;

import org.jutils.text.StringHelper;
import org.xmlBlaster.contrib.ClientPropertiesInfo;
import org.xmlBlaster.contrib.I_ChangePublisher;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.I_Update;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.db.DbMetaHelper;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.contrib.replication.ReplicationManager;
import org.xmlBlaster.jms.XBDestination;
import org.xmlBlaster.jms.XBMessageProducer;
import org.xmlBlaster.jms.XBSession;
import org.xmlBlaster.jms.XBStreamingMessage;
import org.xmlBlaster.util.Execute;
import org.xmlBlaster.util.I_ExecuteListener;
import org.xmlBlaster.util.I_ReplaceVariable;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.ClientProperty;

public abstract class SpecificDefault implements I_DbSpecific, I_ResultCb, I_Update {

   class ExecuteListener implements I_ExecuteListener {

      StringBuffer errors = new StringBuffer();

      public void stderr(String data) {
         log.warning(data);
      }

      public void stdout(String data) {
         log.info(data);
         this.errors.append(data).append("\n");
      }

      String getErrors() {
         return this.errors.toString();
      }
   }

   private String CREATE_COUNTER_KEY = "_createCounter";
   private static Logger log = Logger.getLogger(SpecificDefault.class.getName());

   /** used to publish CREATE changes */
   protected I_ChangePublisher publisher;

   private int rowsPerMessage = 10;

   private DbUpdateInfo dbUpdateInfo;

   private long newReplKey;

   protected I_Info info;

   protected I_DbPool dbPool;

   protected DbMetaHelper dbMetaHelper;

   protected String replPrefix = "repl_";
   
   private String initialCmd;

   private String initialCmdPath;
 
   protected ReplaceVariable replaceVariable;

   protected Replacer replacer;

   private boolean keepDumpFiles;
   
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
      List sqls = ReplicationManager.getContentFromClasspath(fileName,
            method, FLUSH_SEPARATOR, CMD_SEPARATOR);
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
            StringBuffer buf = new StringBuffer();
            for (int j = 0; j < cmds.length; j++)
               buf.append(cmd).append("\n");
            if (doWarn || log.isLoggable(Level.FINE)) {
               log.warning("operation:\n" + buf.toString() + "\n failed: "
                     + ex.getMessage());
               ex.printStackTrace();
            }
         } finally {
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
      PropertiesInfo.addSet(set, this.publisher.getUsedPropertyKeys());
      return set;
   }

   /**
    * @see I_DbSpecific#init(I_Info)
    * 
    */
   public final void init(I_Info info) throws Exception {
      log.info("going to initialize the resources");
      this.replaceVariable = new ReplaceVariable();
      this.info = info;
      this.replPrefix = this.info.get("replication.prefix", "repl_");
      Map map = new HashMap();
      map.put("replPrefix", this.replPrefix);
      this.replacer = new Replacer(this.info, map);
      boolean needsPublisher = this.info.getBoolean(NEEDS_PUBLISHER_KEY, true);
      if (needsPublisher)
         this.publisher = DbWatcher.getChangePublisher(this.info);
      this.dbPool = DbWatcher.getDbPool(this.info);
      this.dbMetaHelper = new DbMetaHelper(this.dbPool);
      this.rowsPerMessage = this.info.getInt("maxRowsOnCreate", 10);
      
      // registering this instance to the Replication Manager
      if (this.publisher != null) {
         HashMap subscriptionMap = new HashMap();
         subscriptionMap.put("ptp", "true");
         this.publisher.registerAlertListener(this, subscriptionMap);
         // fill the info to be sent with the own info objects
         HashMap msgMap = new HashMap();
         new ClientPropertiesInfo(msgMap, this.info);
         msgMap.put("_destination", ReplicationConstants.REPL_MANAGER_SESSION);
         msgMap.put("_command", ReplicationConstants.REPL_MANAGER_REGISTER);
         synchronized(this.info) {
            boolean isRegistered = this.info.getBoolean("_specificDefaultRegistered", false);
            if (!isRegistered) {
               String topic = this.info.get("mom.topicName", null);
               if (topic == null)
                  throw new Exception("SpecificDefault.init: registering the dbWatcher to the Replication Manager: no topic was defined but need one. Please add to your configuration 'mom.topicName'");
               msgMap.put("_topic", topic);
               this.publisher.publish(ReplicationConstants.REPL_MANAGER_TOPIC, ReplicationConstants.REPL_MANAGER_REGISTER.getBytes(), msgMap);
               this.info.put("_specificDefaultRegistered", "true");
            }
         }
      }
      
      this.initialCmdPath = this.info.get("replication.path", "${user.home}/tmp");
      this.initialCmd = this.info.get("replication.initialCmd", null);
      this.keepDumpFiles = info.getBoolean("replication.keepDumpFiles", false);
      
   }

   /**
    * @see I_DbSpecific#shutdown()
    */
   public final void shutdown() throws Exception {
      try {
         log.info("going to shutdown: cleaning up resources");
         // registering this instance to the Replication Manager
         if (this.publisher != null) {
            // fill the info to be sent with the own info objects
            HashMap msgMap = new HashMap();
            new ClientPropertiesInfo(msgMap, this.info);
            msgMap.put("_destination", ReplicationConstants.REPL_MANAGER_SESSION);
            msgMap.put("_command", ReplicationConstants.REPL_MANAGER_UNREGISTER);
            this.publisher.publish(ReplicationConstants.REPL_MANAGER_TOPIC, ReplicationConstants.REPL_MANAGER_UNREGISTER.getBytes(), msgMap);
         }

         if (this.publisher != null) {
            this.publisher.shutdown();
            this.publisher = null;
         }
      } catch (Throwable e) {
         e.printStackTrace();
         log.warning(e.toString());
      }

      if (this.dbPool != null) {
         this.dbPool.shutdown();
      }
   }

   /**
    * Publishes a 'CREATE TABLE' operation to the XmlBlaster. It is used on the
    * DbWatcher side. Note that it is also used to publish the INSERT commands
    * related to a CREATE TABLE operation, i.e. if on a CREATE TABLE operation
    * it is found that the table is already populated when reading it, then
    * these INSERT operations are published with this method.
    * 
    * @param counter
    *           The counter indicating which message number it is. The create
    *           opeation itself will have '0', the subsequent associated INSERT
    *           operations will have an increasing number (it is the number of
    *           the message not the number of the associated INSERT operation).
    * 
    * @return a uniqueId identifying this publish operation.
    * 
    * @throws Exception
    */
   private final String publishCreate(int counter) throws Exception {
      log.info("publishCreate invoked for counter '" + counter + "'");
      DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription();
      description.setAttribute(new ClientProperty(CREATE_COUNTER_KEY, "int",
            null, "" + counter));
      description.setAttribute(new ClientProperty(
            ReplicationConstants.EXTRA_REPL_KEY_ATTR, null, null, ""
                  + this.newReplKey));
      if (counter == 0) {
         this.dbUpdateInfo.getDescription().setCommand(
               ReplicationConstants.CREATE_ACTION);
         description.setAttribute(new ClientProperty(
               ReplicationConstants.ACTION_ATTR, null, null,
               ReplicationConstants.CREATE_ACTION));
      } else {
         this.dbUpdateInfo.getDescription().setCommand(
               ReplicationConstants.REPLICATION_CMD);
         description.setAttribute(new ClientProperty(
               ReplicationConstants.ACTION_ATTR, null, null,
               ReplicationConstants.INSERT_ACTION));
      }

      Map map = new HashMap();
      map.put("_command", "CREATE");
      // and later put the part number inside
      if (this.publisher == null)
         throw new Exception(
               "SpecificDefaut.publishCreate publisher is null, can not publish. Check your configuration");
      return this.publisher.publish("", this.dbUpdateInfo.toXml("").getBytes(), map);
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
         Map attrs) throws Exception {
      Connection conn = this.dbPool.reserve();
      int oldTransIsolation = 0;
      boolean oldTransIsolationKnown = false; // needed to restore old value
      try {
         conn.setAutoCommit(true);
         this.dbUpdateInfo = new DbUpdateInfo(this.info);

         if (catalog != null)
            catalog = this.dbMetaHelper.getIdentifier(catalog);
         if (schema != null)
            schema = this.dbMetaHelper.getIdentifier(schema);
         table = this.dbMetaHelper.getIdentifier(table);

         this.dbUpdateInfo.fillMetadata(conn, catalog, schema, table, null,
               null);
         DbUpdateInfoDescription description = this.dbUpdateInfo
               .getDescription();
         description.addAttributes(attrs);
         this.newReplKey = 0;

         // check if function and trigger are necessary (they are only if the
         // table has to be replicated.
         // it does not need this if the table only needs an initial
         // synchronization. Also it is only
         // needed if this is not invoked for a PtP, i.e. it is only neeed for
         // global alignement, not
         // for local alignement.

         String sql = "SELECT replicate,trigger_name FROM " + this.replPrefix + "tables WHERE tablename='"
               + table + "'";
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql);
         String doReplicateTxt = "f"; // if the entry does not exist (yet),
                                       // then it is not replicated
         boolean doReplicate = false;
         String triggerName = null;
         if (rs.next()) {
            doReplicateTxt = rs.getString(1);
            doReplicate = doReplicateTxt.equalsIgnoreCase("t"); // stands for true
            triggerName = rs.getString(2);
         }
         rs.close();
         st.close();
         boolean addTrigger = doReplicate;

         if (addTrigger) { // create the function and trigger here
            String createString = this.createTableTrigger(this.dbUpdateInfo.getDescription(), triggerName);
            if (createString != null && createString.length() > 1) {
               log.info("adding triggers to '" + table + "':\n\n"
                     + createString);
               st = conn.createStatement();
               st.executeUpdate(createString);
               st.close();
            }
         }

         oldTransIsolation = conn.getTransactionIsolation();
         conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
         conn.setAutoCommit(false);
         // retrieve the Sequence number here ...
         this.newReplKey = incrementReplKey(conn);
         // publish the structure of the table (need to be done here since we
         // must retreive repl key after having added the trigger)
         if (this.publisher != null)
            publishCreate(0);
         else { // normally this will only happen for testing purposes
            log.warning("not publishing the 'CREATE' Operation since publisher is null.");
         }

         sql = new String("SELECT * FROM " + table);
         this.dbPool.select(conn, sql, false, this);
         conn.commit();
         conn.setTransactionIsolation(oldTransIsolation);
      } catch (Exception ex) {
         if (conn != null) {
            conn.rollback();
         }
         throw ex;
      } finally {
         if (conn != null) {
            if (oldTransIsolationKnown) {
               try {
                  conn.setTransactionIsolation(oldTransIsolation);
               } catch (Exception e) {
                  e.printStackTrace();
               }
               ;
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
            this.dbUpdateInfo.fillOneRow(rs, null);
            internalCount++;
            if (internalCount == this.rowsPerMessage) {
               internalCount = 0;
               // publish
               log.info("result: going to publish msg '" + msgCount
                     + "' and internal count '" + internalCount + "'");
               publishCreate(msgCount++);
               this.dbUpdateInfo.getRows().clear(); // clear since re-using
                                                      // the same dbUpdateInfo
            }
         } // end while
         if (this.dbUpdateInfo.getRows().size() > 0) {
            log.info("result: going to publish last msg '" + msgCount
                  + "' and internal count '" + internalCount + "'");
            publishCreate(msgCount);
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
    * @see I_DbSpecific#addTableToWatch(String, boolean)
    */
   public final void addTableToWatch(String catalog, String schema, String tableName,
         boolean doReplicate, String triggerName) throws Exception {
      if (catalog != null && catalog.trim().length() > 0)
         catalog = this.dbMetaHelper.getIdentifier(catalog);
      else
         catalog = " ";
      if (schema != null && schema.trim().length() > 0)
         schema = this.dbMetaHelper.getIdentifier(schema);
      else
         schema = " ";
      tableName = this.dbMetaHelper.getIdentifier(tableName);

      char replTxt = 'f';
      if (doReplicate)
         replTxt = 't';
      Connection conn = null;
      try {
         conn = this.dbPool.reserve();
         long tmp = this.incrementReplKey(conn);
         // TODO check if the schema already exists and only do it if it does not exist
         addSchemaToWatch(conn, catalog, schema);
         
         if (triggerName == null)
            triggerName = this.replPrefix + tmp;
         triggerName = this.dbMetaHelper.getIdentifier(triggerName);
         String sql = "INSERT INTO " + this.replPrefix + "tables VALUES ('" + catalog + "','"
               + schema + "','" + tableName + "','" + replTxt
               + "', 'CREATING'," + tmp + ",'" + triggerName + "')";
         this.dbPool.update(sql);
      } finally {
      }
   }

   /**
    * @see I_DbSpecific#removeTableToWatch(String)
    */
   public final void removeTableToWatch(String catalog, String schema,
         String tableName) throws Exception {
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
   }

   /**
    * @see I_DbSpecific#getCreateTableStatement(DbUpdateInfoDescription,
    *      I_Mapper)
    */
   public final String getCreateTableStatement(
         DbUpdateInfoDescription infoDescription, I_Mapper mapper) {
      DbUpdateInfoColDescription[] cols = infoDescription
            .getUpdateInfoColDescriptions();
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

   /**
    * @see org.xmlBlaster.contrib.I_Update#update(java.lang.String, byte[], java.util.Map)
    */
   public final void update(String topic, byte[] content, Map attrMap) throws Exception {
      if (content == null)
         content = new byte[0];
      String msg = new String(content);
      // this comes from the requesting ReplSlave
      if (ReplicationConstants.REPL_REQUEST_UPDATE.equals(msg)) {
         ClientProperty prop = (ClientProperty)attrMap.get("_sender");
         if (prop == null)
            throw new Exception("update for '" + msg + "' failed since no '_sender' specified");
         String destination = prop.getStringValue();

         String replTopic = this.info.get("mom.topicName", null);
         if (replTopic == null)
            throw new Exception("update for '" + msg + "' failed since the property 'mom.topicName' has not been defined. Check your DbWatcher Configuration file");

         prop = (ClientProperty)attrMap.get(ReplicationConstants.SLAVE_NAME);
         if (prop == null)
            throw new Exception("update for '" + msg + "' failed since no '_slaveName' specified");
         String slaveName = prop.getStringValue();
         initiateUpdate(replTopic, destination, slaveName);
      }
      else {
         log.warning("update from '" + topic + "' with request '" + msg + "'");
      }
   }

   /**
    * 
    * @see org.xmlBlaster.contrib.replication.I_DbSpecific#initiateUpdate(java.lang.String)
    */
   public final void initiateUpdate(String topic, String destination, String slaveName) throws Exception {
      
      Connection conn = null;
      String filename = "" + (new Timestamp()).getTimestamp() + ".dmp";
      String completeFilename = this.initialCmdPath + "/" + filename;
      int oldTransactionIsolation = Connection.TRANSACTION_SERIALIZABLE;
      try {
         conn = this.dbPool.reserve();
         oldTransactionIsolation = conn.getTransactionIsolation();
         conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
         long minKey = this.incrementReplKey(conn);
         // the result must be sent as a high prio message to the real
         // destination
         initialCommand(completeFilename);
         long maxKey = this.incrementReplKey(conn); 
         conn.commit();

         sendInitialFile(topic, this.initialCmdPath, filename, minKey);
         
         HashMap attrs = new HashMap();
         attrs.put("_destination", destination);
         attrs.put("_command", "INITIAL_DATA_RESPONSE");
         attrs.put("_minReplKey", "" + minKey);
         attrs.put("_maxReplKey", "" + maxKey);
         attrs.put(ReplicationConstants.SLAVE_NAME, slaveName);
         this.publisher.publish("", "INITIAL_DATA_RESPONSE".getBytes(), attrs);
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
            if (oldTransactionIsolation != Connection.TRANSACTION_SERIALIZABLE)
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
    * Sends/publishes the initial file as a high priority message.
    * @param filename
    * @throws FileNotFoundException
    * @throws IOException
    */
   private void sendInitialFile(String topic, String path, String shortFilename, long minKey)throws FileNotFoundException, IOException, JMSException  {
      // now read the file which has been generated
      String filename = null;
      if (path != null)
         filename = path + "/" + shortFilename;
      else
         filename = shortFilename;
      File file = new File(filename);
      
      FileInputStream fis = new FileInputStream(file);
      // in this case they are just decorators around I_ChangePublisher
      XBSession session = this.publisher.getJmsSession();
      XBMessageProducer producer = new XBMessageProducer(session, new XBDestination(topic, null));
      producer.setPriority(PriorityEnum.HIGH_PRIORITY.getInt());
      producer.setDeliveryMode(DeliveryMode.PERSISTENT);
      XBStreamingMessage msg = (XBStreamingMessage)session.createTextMessage();
      msg.setStringProperty("_filename", shortFilename);
      msg.setLongProperty(ReplicationConstants.REPL_KEY_ATTR, minKey);
      msg.setStringProperty(ReplicationConstants.DUMP_ACTION, "true");
      msg.setInputStream(fis);
      producer.send(msg);
      if (!this.keepDumpFiles) {
         if (file.exists()) { 
            boolean ret = file.delete();
            if (!ret)
               log.warning("could not delete the file '" + filename + "'");
         }
      }
      fis.close();
   }
   

   /**
    * Executes an Operating System command.
    * 
    * @param cmd
    * @throws Exception
    */
   private void osExecute(String cmd) throws Exception {
      if (Execute.isWindows()) cmd = "cmd " + cmd;
      String[] args = StringHelper.toArray(cmd, " ");
      Execute execute = new Execute(args, null);
      ExecuteListener listener = new ExecuteListener();
      execute.setExecuteListener(listener);
      execute.run(); // blocks until finished
      if (execute.getExitValue() != 0) {
         throw new Exception("Exception occured on executing '" + cmd + "': " + listener.getErrors());
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
            System
                  .setProperty(
                        "jdbc.drivers",
                        "org.hsqldb.jdbcDriver:oracle.jdbc.driver.OracleDriver:com.microsoft.jdbc.sqlserver.SQLServerDriver:org.postgresql.Driver");
         }
         if (System.getProperty("db.url", null) == null) {
            System
                  .setProperty("db.url", "jdbc:postgresql:test//localhost/test");
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
         specific.cleanup(conn, true);
         pool.release(conn);
      } catch (Throwable e) {
         System.err.println("SEVERE: " + e.toString());
         e.printStackTrace();
      }
   }

   public final void initialCommand(String completeFilename) throws Exception {
      if (this.initialCmd == null)
         log.warning("no initial command has been defined ('initialCmd'). I will ignore it");
      else {
         String cmd = this.initialCmd + " " + completeFilename;
         this.osExecute(cmd);
      }
   }

}
