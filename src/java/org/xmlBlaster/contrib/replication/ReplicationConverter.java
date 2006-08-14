/*------------------------------------------------------------------------------
Name:      ReplicationConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;


import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.io.OutputStream;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.DbInfo;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.DbWatcherConstants;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;
import org.xmlBlaster.util.ReplaceVariable;

/**
 * Creates a standardized XML dump from the given ResultSets.
 * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter
 * @see org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter
 * @author Michele Laghi
 */
public class ReplicationConverter implements I_DataConverter, ReplicationConstants {
   private static Logger log = Logger.getLogger(ReplicationConverter.class.getName());
   
   private I_DbSpecific dbSpecific;
   private SqlInfo sqlInfo;
   private I_Info info;
   private I_AttributeTransformer transformer;
   private OutputStream out;
   private boolean sendInitialTableContent = true;
   private long oldReplKey = -1L;
   private I_Info persistentInfo;
   private String oldReplKeyPropertyName;
   private ChangeEvent event;
   private String transactionId;
   private String replPrefix;
   private I_DbPool dbPool;
   private String transSeqPropertyName;
   private long transSeq;
   private long newReplKey;
   
   /**
    * Default constructor, you need to call <tt>init(info)</tt> thereafter. 
    */
   public ReplicationConverter() {
   }

   /**
    * Create this plugin. 
    * @param info Possible configuration parameters you find in the class description
    * @throws Exception If transformer instantiation fails
    */
   public ReplicationConverter(I_Info info) throws Exception {
      this();
      init(info);
   }
   
   /**
    * This method creates every time a new instance
    * @param info
    * @param forceNewIfNeeded if true and the entry is not found in the registry, a new object is created, initialized and added
    * to the registry, otherwise it only returns entries found in the registry (without initializing the object) or null if none is found.
    * @return
    * @throws Exception
    */
   public static I_DbSpecific getDbSpecific(I_Info info, boolean forceNewIfNeeded) throws Exception {
      String dbSpecificClass = info.get("replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificOracle");
      I_DbSpecific dbSpecific = (I_DbSpecific)info.getObject(dbSpecificClass + ".object");
      if (!forceNewIfNeeded)
         return dbSpecific;
      if (dbSpecific == null) {
         if (dbSpecificClass.length() > 0) {
            ClassLoader cl = ReplicationConverter.class.getClassLoader();
            dbSpecific = (I_DbSpecific)cl.loadClass(dbSpecificClass).newInstance();
            if (log.isLoggable(Level.FINE)) 
               log.fine(dbSpecificClass + " created and initialized");
            info.putObject(dbSpecificClass + ".object" ,dbSpecific);
        }
        else
           log.info("Couldn't initialize I_DataConverter, please configure 'converter.class' if you need a conversion.");
      }
      dbSpecific.init(info);
      return dbSpecific;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#init(I_Info)
    */
   public synchronized void init(I_Info info) throws Exception {
      this.info = info;
      String propsToConnect = this.info.get(DbWatcherConstants.MOM_PROPS_TO_ADD_TO_CONNECT, null);
      if (propsToConnect == null) {
         // String val = REPL_PREFIX_KEY + "," + REPL_VERSION;
         String val = "*";
         log.info("Adding the property '" + DbWatcherConstants.MOM_PROPS_TO_ADD_TO_CONNECT + "' with value '" + val + "'");
         this.info.put(DbWatcherConstants.MOM_PROPS_TO_ADD_TO_CONNECT, val);
      }
      else {
         Set set = getKeys(propsToConnect, ",");
         if (!set.contains(REPL_PREFIX_KEY)) {
            String txt = "the property '" + DbWatcherConstants.MOM_PROPS_TO_ADD_TO_CONNECT + "' has been explicitly set to '" + propsToConnect + "' but it must contain '" + REPL_PREFIX_KEY + "'";
            throw new Exception(txt); 
         }
         if (!set.contains(REPL_VERSION)) {
            String txt = "the property '" + DbWatcherConstants.MOM_PROPS_TO_ADD_TO_CONNECT + "' has been explicitly set to '" + propsToConnect + "' but it must contain '" + REPL_VERSION + "'";
            throw new Exception(txt); 
         }
      }
      ClassLoader cl = this.getClass().getClassLoader();
      String transformerClassName = info.get("transformer.class", "");
      if (transformerClassName != null && transformerClassName.length() > 0) {
         this.transformer = (I_AttributeTransformer)cl.loadClass(transformerClassName).newInstance();
         this.transformer.init(info);
         log.info("Loaded transformer pluging '" + transformerClassName + "'");
      }
      this.replPrefix = SpecificDefault.getReplPrefix(this.info);
      boolean forceCreationAndInit = true;
      this.dbSpecific = getDbSpecific(info, forceCreationAndInit);
      final boolean doFix = true;
      this.dbSpecific.checkTriggerConsistency(doFix);
      this.sendInitialTableContent = this.info.getBoolean("replication.sendInitialTableContent", true);
      // this.persistentMap = new PersistentMap(CONTRIB_PERSISTENT_MAP);
      // this.persistentMap = new Info(CONTRIB_PERSISTENT_MAP);
      this.dbPool = DbWatcher.getDbPool(this.info);
      this.persistentInfo = new DbInfo(this.dbPool, "replication");
      this.oldReplKeyPropertyName = this.dbSpecific.getName() + ".oldReplKey";
      this.transSeqPropertyName = this.dbSpecific.getName() + ".transactionSequence";
      this.transSeq = this.persistentInfo.getLong(this.transSeqPropertyName, 0L);
      this.info.put(TRANSACTION_SEQ, "" + this.transSeq);
      long tmp = this.persistentInfo.getLong(this.oldReplKeyPropertyName, -1L);
      if (tmp > -1L) {
         this.oldReplKey = tmp;
         log.info("One entry found in persistent map '" + CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Will start with '" + this.oldReplKey + "'");
         // the following to fix the situation where the peristent DBINFO has not been cleaned up but the sequence has been reset
         Connection conn = null;
         try {
            conn = this.dbPool.reserve();
            conn.setAutoCommit(true);
            long realCounter = this.dbSpecific.incrementReplKey(conn);
            if (this.oldReplKey > realCounter) {
               log.warning("The counter from persistence is '" + this.oldReplKey + "' while the sequence is '" + realCounter + "' which is lower. Supposly you have reset the sequence but not the persistence. This is now fixed by initially setting the old replKey to be '" + realCounter + "'");
               this.oldReplKey = realCounter;
            }
         }
         catch (Throwable ex) {
            log.severe("An exception occured when verifying the intial status of the counter against the old replKeY: " + ex.getMessage());
            ex.printStackTrace();
            SpecificDefault.removeFromPool(conn, SpecificDefault.ROLLBACK_NO, this.dbPool);
            conn = null;
         }
         finally {
            SpecificDefault.releaseIntoPool(conn, SpecificDefault.COMMIT_NO, this.dbPool);
         }
      }
      else {
         log.info("No entry found in persistent map '" + CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Starting by 0'");
         this.oldReplKey = 0L;
      }
   }

   private static Set getKeys(String val, String sep) {
      Set ret = new HashSet();
      if (val == null || val.trim().length() < 1)
         return ret;
      StringTokenizer tokenizer = new StringTokenizer(val, sep);
      while (tokenizer.hasMoreTokens())
         ret.add(tokenizer.nextToken().trim());
      return ret;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#shutdown
    */
   public synchronized void shutdown() throws Exception {
      try {
         if (this.dbSpecific != null)
            this.dbSpecific.shutdown();
      }
      finally {
         this.dbPool.shutdown();
         this.dbSpecific = null;
      }
   }
   
   
   
   /**
    * Add another result set to the XML string.
    * This method is invoked for each SQL Operation. Each transaction, i.e. each message
    * can contain several such operations.
    * 
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#addInfo(ResultSet, int)
    */
   public void addInfo(ResultSet rs, int what) throws Exception {
      if (rs == null)
         throw new IllegalArgumentException("ReplicationConverter: Given ResultSet is null");

      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
     
      if (numberOfColumns != 11)
         throw new Exception("ReplicationConverter.addInfo: wrong number of columns: should be 11 but was " + numberOfColumns);
      
      /*
       * Note that this test implies that the replKeys come in a growing sequence. If this is not the case, this test is useless.
       */
      this.newReplKey = rs.getLong(1);
      boolean markProcessed = false;
      
      if (this.newReplKey <= this.oldReplKey) {
         log.warning("the replication key '" + this.newReplKey + "' has already been processed since the former key was '" + this.oldReplKey + "'. It will be marked ");
         markProcessed = true;
      }
      else {
         this.oldReplKey = this.newReplKey;
      }
      // puts this in the metadata attributes of the message to be sent over the mom
      this.event.getAttributeMap().put(REPL_KEY_ATTR, "" + this.newReplKey);

      String transKey = rs.getString(2);
      String dbId = rs.getString(3);
      String tableName = rs.getString(4);
      String guid = rs.getString(5);
      String action = rs.getString(6);
      String catalog = rs.getString(7);
      String schema = rs.getString(8);

      if (transKey == null)
         log.severe("The transaction key was not set. Must be set in order to be processed correctly");
      else {
         if (transKey.equals("UNKNOWN")) {
            String txt = "The transaction key has not been set at the time of entry collection. This is a bug which shall be fixed";
            log.severe(txt);
            throw new Exception(txt);
         }
      }
      log.fine("sequence number '" + this.newReplKey + "' processing now for table '" + tableName + "'");
      if (this.transactionId == null)
         this.transactionId = transKey;
      else {
         if (!this.transactionId.equals(transKey)) {
            log.severe("the entry with replKey='" + this.newReplKey + "' tableName='" + tableName + "' with action='" + action + "' had transaction '" + transKey + "' but was expected '" + this.transactionId + "'");
         }
      }
      
      // String newContent = rs.getString(9); // could be null
      String newContent = null;
      Clob clob = rs.getClob(9);
      if (clob != null) {
         long length = clob.length();
         if (length > Integer.MAX_VALUE)
            throw new Exception("ReplicationConverter.addInfo: the content is too big ('" + length + "' bytes) to fit in one msg, can not process");
         byte[] buf = new byte[(int)length];
         clob.getAsciiStream().read(buf);
         newContent = new String(buf);
      }
      // String oldContent = rs.getString(10);
      String oldContent = null;
      clob = rs.getClob(10);
      if (clob != null) {
         long length = clob.length();
         if (length > Integer.MAX_VALUE)
            throw new Exception("ReplicationConverter.addInfo: the old content is too big ('" + length + "' bytes) to fit in one msg, can not process");
         byte[] buf = new byte[(int)length];
         clob.getAsciiStream().read(buf);
         oldContent = new String(buf);
      }
      // check if it needs to read the new content explicitly, this is used for cases
      // where it was not possible to fill with meat in the synchronous PL/SQL part.
      if (newContent == null && ("INSERT".equals(action) || ("UPDATE".equals(action)))) {
         if (guid == null)
            log.severe("could not operate since no guid and no newContent on UPDATE or INSERT");
         else
            newContent = this.dbSpecific.getContentFromGuid(guid, catalog, schema, tableName);
      }
      
      String version = rs.getString(11);
      if (this.sqlInfo.getRowCount() == 0L) {

         if (this.transformer != null) {
            Map attr = this.transformer.transform(rs, -1);
            if (attr != null) {
               this.sqlInfo.getDescription().addAttributes(attr);
            }
         }
      }
      if (what == ALL || what == ROW_ONLY) {
         Map completeAttrs = new HashMap();
         completeAttrs.put(TABLE_NAME_ATTR, tableName);
         completeAttrs.put(REPL_KEY_ATTR, "" + this.newReplKey);
         completeAttrs.put(TRANSACTION_ATTR, transKey);
         completeAttrs.put(DB_ID_ATTR, dbId);
         completeAttrs.put(GUID_ATTR, guid);
         completeAttrs.put(CATALOG_ATTR, catalog);
         completeAttrs.put(SCHEMA_ATTR, schema);
         completeAttrs.put(VERSION_ATTR, version);
         completeAttrs.put(ACTION_ATTR, action);
         if (markProcessed)
            completeAttrs.put(ALREADY_PROCESSED_ATTR, "true");
         if (action.equalsIgnoreCase(CREATE_ACTION)) {
            try {
               log.info("addInfo: going to create a new table '" + tableName + "'");
               boolean forceSend = false;
               if (newContent != null) {
                  String destination = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='_destination'");
                  if (destination == null || destination.length() < 1)
                     log.severe("The destination could not be extracted from '" + newContent + "'");
                  else
                     completeAttrs.put("_destination", destination);
                  String forceSendTxt = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='_forceSend'");
                  if (forceSendTxt != null && "true".equalsIgnoreCase(forceSendTxt.trim())) {
                     forceSend = true;
                     log.info("Force Send was set to 'true'");
                  }
               }
               this.dbSpecific.readNewTable(catalog, schema, tableName, completeAttrs, this.sendInitialTableContent || forceSend);
            }
            catch (Exception ex) {
               ex.printStackTrace();
               log.severe("Could not correctly add trigger on table '" + tableName + "' : " + ex.getMessage());
            }
         }
         else if (action.equalsIgnoreCase(DROP_ACTION)) {
            SqlDescription description = this.sqlInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(ALTER_ACTION)) {
            SqlDescription description = this.sqlInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(INSERT_ACTION)) {
            SqlRow row = this.sqlInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
            completeAttrs.put(OLD_CONTENT_ATTR, oldContent);
            SqlRow row = this.sqlInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(DELETE_ACTION)) {
            SqlRow row = this.sqlInfo.fillOneRow(rs, oldContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(STATEMENT_ACTION)) {
            String sql = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='" + STATEMENT_ATTR + "'");
            String statementPrio = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='" + STATEMENT_PRIO_ATTR + "'");
            String maxEntries = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='" + MAX_ENTRIES_ATTR + "'");
            String id = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='" + STATEMENT_ID_ATTR + "'");
            String sqlTopic = ReplaceVariable.extractWithMatchingAttrs(newContent, "attr", " id='" + SQL_TOPIC_ATTR + "'");
            this.sqlInfo.getDescription().setCommand(STATEMENT_ACTION);
            this.sqlInfo.getDescription().setAttribute(ACTION_ATTR, STATEMENT_ACTION);
            this.sqlInfo.getDescription().setAttribute(STATEMENT_ID_ATTR, id);
            this.sqlInfo.getDescription().setAttribute(STATEMENT_ATTR, sql);
            this.sqlInfo.getDescription().setAttribute(STATEMENT_PRIO_ATTR, statementPrio);
            this.sqlInfo.getDescription().setAttribute(MAX_ENTRIES_ATTR, maxEntries);
            this.sqlInfo.getDescription().setAttribute(SQL_TOPIC_ATTR, sqlTopic);
         }
      }
   }

   public void addInfo(Map attributeMap) throws Exception {
      // nothing to be done here
   }

   /**
    * This method is invoked before sending the message over the mom.
    */
   public int done() throws Exception {
      int ret = this.sqlInfo.getRowCount();
      boolean doSend = true;
      if (ret < 1) {
         // mark it to block delivery if it has no data and is not a STATEMENT
         String command = this.sqlInfo.getDescription().getCommand();
         if (command == null || REPLICATION_CMD.equals(command)) {
            // puts this in the metadata attributes of the message to be sent over the mom
            this.event.getAttributeMap().put("_ignore_this_message", "" + true);
            doSend = false;
         }
      }
      if (doSend) { // we put it in the attribute map not in the message itself
         this.event.getAttributeMap().put(TRANSACTION_SEQ, "" + this.transSeq++);
         this.persistentInfo.put(this.transSeqPropertyName, "" + this.transSeq);
         this.persistentInfo.put(this.oldReplKeyPropertyName, "" + this.oldReplKey);
      }
         
      String tmp = this.sqlInfo.toXml("");
      this.out.write(tmp.getBytes());
      this.out.flush();
      this.sqlInfo = null;
      return ret;
   }

   public void setOutputStream(OutputStream out, String command, String ident, ChangeEvent event) throws Exception {
      this.out = out;
      this.transactionId = null;
      this.sqlInfo = new SqlInfo(this.info);
      SqlDescription description = new SqlDescription(this.info);
      description.setCommand(REPLICATION_CMD);
      if (ident != null)
         description.setIdentity(ident);
      this.sqlInfo.setDescription(description);
      this.event = event;
   }

   public String getPostStatement() {
      if (this.transactionId == null) {
         if (this.sqlInfo != null)
            log.severe("No transaction id has been found for " + this.sqlInfo.toXml(""));
         return null;
      }
      String statement = "DELETE FROM " + this.replPrefix + "ITEMS WHERE TRANS_KEY='" + this.transactionId + "'";
      return statement;
   }

}

