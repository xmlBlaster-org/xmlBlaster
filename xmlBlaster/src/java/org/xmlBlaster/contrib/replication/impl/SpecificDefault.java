/*------------------------------------------------------------------------------
Name:      SpecificDefault.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.replication.impl;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.db.I_ResultCb;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.mom.I_ChangePublisher;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoRow;
import org.xmlBlaster.contrib.replication.I_DbSpecific;
import org.xmlBlaster.contrib.replication.I_Mapper;
import org.xmlBlaster.contrib.replication.ReplicationConstants;
import org.xmlBlaster.contrib.replication.ReplicationManager;
import org.xmlBlaster.util.qos.ClientProperty;

public class SpecificDefault implements I_DbSpecific, I_ResultCb {

   private String CREATE_COUNTER_KEY = "_createCounter";
   
   private static Logger log = Logger.getLogger(SpecificDefault.class.getName());
   private I_Info info;
   /** used to publish CREATE changes */
   private I_ChangePublisher publisher;
   private I_DbPool dbPool;
   private int rowsPerMessage = 10;
   private DbUpdateInfo dbUpdateInfo;
   private int newReplKey;
   
   /**
    * Not doing anything.
    */
   public SpecificDefault() {
   }
   
   /**
    * Reads the content to be executed from a file.
    * @param conn The connection on which to operate. Must not be null.
    * @param method The method which uses this invocation (used for logging purposes).
    * @param propKey The name (or key) of the property to retrieve. The content of this property is the bootstrap file name 
    * @param propDefault The default of the property.
    * @throws Exception if an exception occurs when reading the bootstrap file.
    */
   private void updateFromFile(Connection conn, String method, String propKey, String propDefault) throws Exception {
      Statement st = null;
      String bootstrapFileName = this.info.get(propKey, propDefault);
      final String FLUSH_SEPARATOR = "-- FLUSH";
      final String CMD_SEPARATOR = "-- EOC";
      List sqls = ReplicationManager.getContentFromClasspath(bootstrapFileName, method, FLUSH_SEPARATOR, CMD_SEPARATOR);
      for (int i=0; i < sqls.size(); i++) {
         String[] cmds = (String[])sqls.get(i);
         try {
            conn.setAutoCommit(true);
            st = conn.createStatement();
            
            for (int j=0; j < cmds.length; j++) {
               if (cmds[j].trim().length() > 0)
                  st.addBatch(cmds[j]);
            }
            st.executeBatch();
         }
         catch (SQLException ex) {
            StringBuffer buf = new StringBuffer();
            for (int j=0; j < cmds.length; j++)
               buf.append(cmds[j]).append("\n");
            log.warning("operation:\n" + buf.toString() + "\n failed: " + ex.getMessage());
            // ex.printStackTrace();
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
   public void bootstrap(Connection conn) throws Exception {
      updateFromFile(conn, "bootstrap", "replication.bootstrapFile", "org/xmlBlaster/contrib/replication/setup/postgres/bootstrap.sql");
   }

   /**
    * @see I_DbSpecific#cleanup(Connection)
    */
   public void cleanup(Connection conn) throws Exception {
      updateFromFile(conn, "cleanup", "replication.cleanupFile", "org/xmlBlaster/contrib/replication/setup/postgres/cleanup.sql");
   }
   
   /**
    * @see I_DbSpecific#init(I_Info)
    * 
    */
   public void init(I_Info info) throws Exception {
      log.info("going to initialize the resources");
      this.info = info;
      boolean needsPublisher = this.info.getBoolean(NEEDS_PUBLISHER_KEY, true);
      if (needsPublisher)
         this.publisher = DbWatcher.getChangePublisher(this.info, "SpecificDefault");
      this.dbPool = DbWatcher.getDbPool(this.info, "SpecificDefault");
      this.rowsPerMessage = this.info.getInt("maxRowsOnCreate", 10);
   }

   /**
    * @see I_DbSpecific#shutdown()
    */
   public void shutdown() throws Exception {
      try {
         log.info("going to shutdown: cleaning up resources");
         boolean publisherOwner = this.info.get("mom.publisher.owner", "").equals("SpecificDefault");
         if (this.publisher != null && publisherOwner) { 
            this.publisher.shutdown();
            this.publisher = null;
            this.info.putObject("mom.publisher", null);
         }
      } 
      catch(Throwable e) { 
         e.printStackTrace(); log.warning(e.toString()); 
      }

      boolean poolOwner = this.info.get("db.pool.owner", "").equals("SpecificDefault");
      if (poolOwner && this.dbPool != null) {
         this.dbPool.shutdown();
         this.dbPool = null;
         this.info.putObject("db.pool", null);
      }
   }

   /**
    * Publishes a 'CREATE TABLE' operation to the XmlBlaster. It is used on the DbWatcher side. Note that it 
    * is also used to publish the INSERT commands related to a CREATE TABLE operation, i.e. if on a CREATE TABLE
    * operation it is found that the table is already populated when reading it, then these INSERT operations 
    * are published with this method.
    * 
    * @param counter The counter indicating which message number it is. The create opeation itself will have '0',
    * the subsequent associated INSERT operations will have an increasing number (it is the number of the message
    * not the number of the associated INSERT operation).
    *  
    * @return a uniqueId identifying this publish operation.
    * 
    * @throws Exception
    */
   private String publishCreate(int counter) throws Exception {
      log.info("publishCreate invoked for counter '" + counter + "'");
      DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription(); 
      description.setAttribute(new ClientProperty(CREATE_COUNTER_KEY, "int", null, "" + counter));
      description.setAttribute(new ClientProperty(ReplicationConstants.EXTRA_REPL_KEY_ATTR, null, null, "" + this.newReplKey));
      if (counter == 0) {
         this.dbUpdateInfo.getDescription().setCommand(ReplicationConstants.CREATE_ACTION);
         description.setAttribute(new ClientProperty(ReplicationConstants.ACTION_ATTR, null, null, ReplicationConstants.CREATE_ACTION));
      }
      else {
         this.dbUpdateInfo.getDescription().setCommand(ReplicationConstants.REPLICATION_CMD);
         description.setAttribute(new ClientProperty(ReplicationConstants.ACTION_ATTR, null, null, ReplicationConstants.INSERT_ACTION));
      }
      
      Map map = new HashMap(); 
      map.put("_command", "CREATE");
      // and later put the part number inside
      if (this.publisher == null)
         throw new Exception("SpecificDefaut.publishCreate publisher is null, can not publish. Check your configuration");
      return this.publisher.publish("", this.dbUpdateInfo.toXml(""), map);
   }


   /**
    * Increments and retreives the repl_key sequence counter. The connection must not be null.
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
   public int incrementReplKey(Connection conn) throws Exception {
      if (conn == null)
         throw new Exception("SpecificDefault.incrementReplKey: the DB connection is null");
      CallableStatement st = null;
      try {
         // st = conn.prepareCall("{? = call nextval('repl_seq')}");
         st = conn.prepareCall("{? = call repl_increment()}");
         // st.registerOutParameter(1, Types.BIGINT);
         st.registerOutParameter(1, Types.INTEGER);
         st.executeQuery();
         return st.getInt(1);
      }
      finally {
         try {  if (st != null) st.close(); } catch(Exception ex) { }
      }
   }
   
   /**
    * @see I_DbSpecific#readNewTable(String, String, String, Map)
    */
   public void readNewTable(String catalog, String schema, String table, Map attrs) throws Exception {
      Connection conn = this.dbPool.reserve();
      int oldTransIsolation = 0;
      boolean oldTransIsolationKnown = false;
      try {
         oldTransIsolation = conn.getTransactionIsolation();
         oldTransIsolationKnown = true;
         conn.setAutoCommit(false);
         conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

         this.dbUpdateInfo = new DbUpdateInfo(this.info);
         this.dbUpdateInfo.fillMetadata(conn, catalog, schema, table, null, null);
         DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription();
         description.addAttributes(attrs);
         this.newReplKey = 0;

         publishCreate(0);
         
         // check if function and trigger are necessary (they are only if the table has to be replicated.
         // it does not need this if the table only needs an initial synchronization.
         
         String sql = "SELECT replicate FROM repl_tables WHERE tablename='" + table + "'";
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(sql);
         String doReplicate = "f"; // if the entry does not even exsist, then it is not replicated
         if (rs.next()) {
            doReplicate = rs.getString(1);
         }
         rs.close();
         st.close();
         // retrieve the Sequence number here ...
         this.newReplKey = incrementReplKey(conn);
         if (doReplicate.equalsIgnoreCase("t")) { // stands for 'true'
            // create the function and trigger here
            String createString = this.createTableFunctionAndTrigger(this.dbUpdateInfo.getDescription());
            if (createString == null || createString.length() < 1)
               throw new Exception("SpecificDefault.readNewTable: could not generate the sql command for the creation of the table '" + table + "'");
            // add the function to the DB here
            // add the trigger here
            log.info("adding functions and triggers to '" + table + "':\n\n" + createString);
            st = conn.createStatement();
            st.executeUpdate(createString);
            st.close();
         }
         sql = new String("SELECT * FROM " + table);
         this.dbPool.select(conn, sql, false, this);
         conn.commit();
         conn.setTransactionIsolation(oldTransIsolation);
      }
      catch (Exception ex) {
         if (conn != null) {
            conn.rollback();
         }
         throw ex;
      }
      finally {
         if (conn != null) {
            if (oldTransIsolationKnown) {
               try { conn.setTransactionIsolation(oldTransIsolation); } catch(Exception e) { e.printStackTrace(); };
            }
            this.dbPool.release(conn);
            
         }
      }
   }

   /**
    * @see I_ResultCb#init(ResultSet)
    */
   public void result(ResultSet rs) throws Exception {
      boolean hasPublishedAlready = false;
      try {
         // TODO clear the columns since not really used anymore ...
         DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription();
         
         int msgCount = 1; // since 0 was the create, the first must be 1
         int internalCount = 0;
         while (rs != null && rs.next()) {
            DbUpdateInfoRow row = this.dbUpdateInfo.fillOneRow(rs, null);
            internalCount++;
            if (internalCount == this.rowsPerMessage) {
               internalCount = 0;
               // publish 
               log.info("result: going to publish msg '" + msgCount + "' and internal count '" + internalCount + "'");
               publishCreate(msgCount++);
               this.dbUpdateInfo.getRows().clear(); // clear since re-using the same dbUpdateInfo
            }
         } // end while
         if (this.dbUpdateInfo.getRows().size() > 0) {
            log.info("result: going to publish last msg '" + msgCount + "' and internal count '" + internalCount + "'");
            publishCreate(msgCount);
         }
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't publish change event meat for CREATE");
      }
   }
   
   /**
    * Helper method used to construct the CREATE TABLE statement part belonging to a single COLUMN.
    * @param colInfoDescription
    * @return
    */
   private StringBuffer getColumnStatement(DbUpdateInfoColDescription colInfoDescription) {
      String type = colInfoDescription.getType();
      int charLength = colInfoDescription.getCharLength();
      int precision = colInfoDescription.getPrecision();
      /*
      if (charLength > 0) {
         type = type + "[" + charLength + "]";
      }
      */
      StringBuffer buf = new StringBuffer(colInfoDescription.getColName()).append(" ").append(type);
      return buf;
   }

   /**
    * @see I_DbSpecific#getCreateTableStatement(DbUpdateInfoDescription, I_Mapper)
    */
   public String getCreateTableStatement(DbUpdateInfoDescription infoDescription, I_Mapper mapper) {
      DbUpdateInfoColDescription[] cols = infoDescription.getUpdateInfoColDescriptions();
      StringBuffer buf = new StringBuffer(1024);
      String tableName = infoDescription.getIdentity();
      if (mapper != null) 
         tableName = mapper.getMappedTable(null, null, tableName, null);
      buf.append("CREATE TABLE ").append(tableName).append(" (");
      StringBuffer pkBuf = new StringBuffer();
      boolean hasPkAlready = false;
      for (int i=0; i < cols.length; i++) {
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
    * 
    * @param col 
    * @param prefix can be 'old' or 'new'
    * @return
    */
   protected String createVariableSqlPart(DbUpdateInfoDescription description, String prefix) {
      DbUpdateInfoColDescription[] cols = description.getUpdateInfoColDescriptions();
      StringBuffer buf = new StringBuffer("       ").append(prefix).append("Cont = '';\n");

      for (int i=0; i < cols.length; i++) {
         String colName = cols[i].getColName();
         int type = cols[i].getSqlType();
         if (type == Types.BINARY || type == Types.BLOB || type == Types.JAVA_OBJECT || type == Types.VARBINARY || type == Types.STRUCT) {
            buf.append("       blobCont = " + prefix + "." + colName + ";\n");
            buf.append("       tmp = " + prefix + "Cont || repl_col2xml_base64('" + colName + "', blobCont);\n");
            buf.append("       " + prefix + "Cont = tmp;\n");
         }
         else {
            buf.append("       tmp = " + prefix + "Cont || repl_col2xml('" + colName + "'," + prefix + "." + colName + ");\n");
            buf.append("       " + prefix + "Cont = tmp;\n");
         }
      }
      buf.append("       oid = ").append(prefix).append(".oid;\n");
      return buf.toString();
   }
   
   /**
    * This method creates a function and the associated trigger to detect INSERT DELETE and UPDATE 
    * operations on a particular table.
    * @param infoDescription the info object containing the necessary information for the table.
    * @return a String containing the sql update. It can be executed. 
    */
   public String createTableFunctionAndTrigger(DbUpdateInfoDescription infoDescription) {

      String tableName = infoDescription.getIdentity();  // should be the table name
      String functionName =  tableName + "_repl_f";
      String triggerName = tableName + "_repl_t";
      
      StringBuffer buf = new StringBuffer();
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("-- This is the function which will be registered to the triggers.               \n");
      buf.append("-- It must not take any parameter.                                              \n");
      buf.append("-- This is the only method which is business data specific. It is depending on  \n");
      buf.append("-- the table to be replicated. This should be generated by a tool.              \n");
      buf.append("--                                                                              \n");
      buf.append("-- For each table you should just write out in a sequence the complete content  \n");
      buf.append("-- of the row to replicate. You could make more fancy stuff here, for example   \n");
      buf.append("-- you could just send the minimal stuff, i.e. only the stuff which has changed \n");
      buf.append("-- (for the new stuff) and for the old one you could always send an empty one.  \n");
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("\n");
      buf.append("CREATE OR REPLACE FUNCTION ").append(functionName).append("() RETURNS trigger AS $").append(functionName).append("$\n");
      buf.append("DECLARE blobCont BYTEA; \n");
      buf.append("        oldCont TEXT; \n");
      buf.append("   newCont TEXT;\n");
      buf.append("   comment TEXT;\n");
      buf.append("   oid     TEXT;\n");
      buf.append("   tmp     TEXT;\n");
      buf.append("BEGIN\n");
      buf.append("    oldCont = NULL;\n");
      buf.append("    newCont = NULL;\n");
      buf.append("    tmp = repl_check_structure();\n");
      buf.append("\n");
      buf.append("    IF (TG_OP != 'INSERT') THEN\n");
      
      buf.append(createVariableSqlPart(infoDescription, "old"));
      buf.append("    END IF;\n");
      buf.append("\n");
      buf.append("    IF (TG_OP != 'DELETE') THEN\n");
      
      buf.append(createVariableSqlPart(infoDescription, "new"));
      buf.append("\n");
      buf.append("    END IF;\n");
      buf.append("    INSERT INTO repl_items (trans_stamp, dbId, tablename, guid,\n");
      buf.append("                           db_action, db_catalog, db_schema, \n");
      buf.append("                           content, oldContent, version) values \n");
      buf.append("                           (CURRENT_TIMESTAMP,current_database(),\n");
      buf.append("            TG_RELNAME, oid, TG_OP, NULL, current_schema(), newCont, \n");
      buf.append("            oldCont, '0.0');\n");
      buf.append("    tmp = inet_client_addr();\n");
      buf.append("\n");
      buf.append("    IF (TG_OP = 'DELETE') THEN RETURN OLD;\n");
      buf.append("    END IF;\n");
      buf.append("    RETURN NEW;\n");
      buf.append("END;\n");
      buf.append("$").append(functionName).append("$ LANGUAGE 'plpgsql';\n");
      buf.append("\n");

      // and now append the associated trigger ....

      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("-- THE TRIGGER FOR THE replTest TABLE                                           \n");
      buf.append("-- ---------------------------------------------------------------------------- \n");
      buf.append("\n");
      buf.append("-- DROP TRIGGER ").append(triggerName).append(" ON ").append(tableName).append(" CASCADE;\n");
      buf.append("CREATE TRIGGER ").append(triggerName).append("\n");
      buf.append("AFTER UPDATE OR DELETE OR INSERT\n");
      buf.append("ON ").append(tableName).append("\n");
      buf.append("FOR EACH ROW\n");
      buf.append("EXECUTE PROCEDURE ").append(functionName).append("();\n");
      buf.append("\n");
      
      return buf.toString();
   }   
  
   public void forceTableChangeCheck() throws Exception {
      Connection conn = null;
      CallableStatement st = null;
      try {
         String sql = "{? = call repl_check_structure()}";
         conn = this.dbPool.reserve();
         conn.setAutoCommit(true);
         st = conn.prepareCall(sql);
         st.registerOutParameter(1, Types.VARCHAR);
         st.executeQuery();
      }
      finally {
         if (conn != null) {
            try {  if (st != null) st.close(); } catch(Exception ex) { }
            this.dbPool.release(conn);
         }
      }
   }
   
   
}
