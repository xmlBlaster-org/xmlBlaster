/*------------------------------------------------------------------------------
Name:      ReplicationConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;


import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.io.OutputStream;
import java.sql.Clob;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.util.PersistentMap;

/**
 * Creates a standardized XML dump from the given ResultSets.
 * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter
 * @see org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter
 * @author Michele Laghi
 */
public class ReplicationConverter implements I_DataConverter, ReplicationConstants {
   private static Logger log = Logger.getLogger(ReplicationConverter.class.getName());
   
   private I_DbSpecific dbSpecific;
   private SqlInfo dbUpdateInfo;
   private I_Info info;
   private I_AttributeTransformer transformer;
   private OutputStream out;
   private boolean sendInitialTableContent = true;
   private long oldReplKey = -1L;
   private int consecutiveReplKeyErrors = 0; // used to detect serious problems in sequences
   private int maxConsecutiveReplKeyErrors = 10;
   private Map persistentMap;
   private String oldReplKeyPropertyName;
   private ChangeEvent event;
   private String transactionId;
   private String replPrefix;
   
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
    * @return
    * @throws Exception
    */
   public static I_DbSpecific getDbSpecific(I_Info info) throws Exception {
      String dbSpecificClass = info.get("replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificOracle");
      I_DbSpecific dbSpecific = (I_DbSpecific)info.getObject(dbSpecificClass + ".object");
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
      ClassLoader cl = this.getClass().getClassLoader();
      String transformerClassName = info.get("transformer.class", "");
      if (transformerClassName != null && transformerClassName.length() > 0) {
         this.transformer = (I_AttributeTransformer)cl.loadClass(transformerClassName).newInstance();
         this.transformer.init(info);
         log.info("Loaded transformer pluging '" + transformerClassName + "'");
      }
      this.replPrefix = this.info.get("replication.prefix", "repl_");
      this.dbSpecific = getDbSpecific(info);
      this.sendInitialTableContent = this.info.getBoolean("replication.sendInitialTableContent", true);
      this.persistentMap = new PersistentMap(CONTRIB_PERSISTENT_MAP);
      this.oldReplKeyPropertyName = this.dbSpecific.getName() + ".oldReplKey";
      Long tmp = (Long)this.persistentMap.get(this.oldReplKeyPropertyName);
      if (tmp != null) {
         this.oldReplKey = tmp.longValue();
         log.info("One entry found in persistent map '" + CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Will start with '" + this.oldReplKey + "'");
      }
      else {
         log.info("No entry found in persistent map '" + CONTRIB_PERSISTENT_MAP + "' with key '" + this.oldReplKeyPropertyName + "' found. Starting by 0'");
      }
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
         this.dbSpecific = null;
      }
   }
   
   
   
   /**
    * Add another result set to the XML string
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
      int replKey = rs.getInt(1);
      boolean markProcessed = false;
      if (replKey <= this.oldReplKey) {
         log.warning("the replication key '" + replKey + "' has already been processed since the former key was '" + this.oldReplKey + "'. It will be marked ");
         markProcessed = true;
         this.consecutiveReplKeyErrors++;
         if (this.consecutiveReplKeyErrors > this.maxConsecutiveReplKeyErrors) {
            log.severe("'" + this.consecutiveReplKeyErrors + "' consecutive errors on replKey occured now. HINT: Could it be that you have cleaned up the replKey counter but not the persistent Map ? (check the previous warnings)");
            this.consecutiveReplKeyErrors = 0; // to avoid too many errors
         }
      }
      else { // TODO move this after the sending of the message since this could still fail.
         this.consecutiveReplKeyErrors = 0;
         this.persistentMap.put(this.oldReplKeyPropertyName, new Long(this.oldReplKey));
         this.oldReplKey = replKey;
      }
      // puts this in the metadata attributes of the message to be sent over the mom
      this.event.getAttributeMap().put(REPL_KEY_ATTR, "" + replKey);

      String transKey = rs.getString(2);
      String dbId = rs.getString(3);
      String tableName = rs.getString(4);
      String guid = rs.getString(5);
      String action = rs.getString(6);
      String catalog = rs.getString(7);
      String schema = rs.getString(8);
      
      // TODO remove this after testing.
      // puts this in the metadata attributes of the message to be sent over the mom
      this.event.getAttributeMap().put("_tableName__", tableName);

      log.fine("sequence number '" + replKey + "' processing now for table '" + tableName + "'");
      if (this.transactionId == null)
         this.transactionId = transKey;
      else {
         if (!this.transactionId.equals(transKey)) {
            log.severe("the entry with replKey='" + replKey + "' tableName='" + tableName + "' with action='" + action + "' had transaction '" + transKey + "' but was expected '" + this.transactionId + "'");
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
      
      if (this.dbUpdateInfo.getRowCount() == 0L) {

         if (this.transformer != null) {
            Map attr = this.transformer.transform(rs, -1);
            if (attr != null) {
               this.dbUpdateInfo.getDescription().addAttributes(attr);
            }
         }
      }
      if (what == ALL || what == ROW_ONLY) {
         Map completeAttrs = new HashMap();
         completeAttrs.put(TABLE_NAME_ATTR, tableName);
         completeAttrs.put(REPL_KEY_ATTR, "" + replKey);
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
               this.dbSpecific.readNewTable(catalog, schema, tableName, completeAttrs, this.sendInitialTableContent);
            }
            catch (Exception ex) {
               ex.printStackTrace();
               log.severe("Could not correctly add trigger on table '" + tableName + "' : " + ex.getMessage());
            }
         }
         else if (action.equalsIgnoreCase(DROP_ACTION)) {
            SqlDescription description = this.dbUpdateInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(ALTER_ACTION)) {
            SqlDescription description = this.dbUpdateInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(INSERT_ACTION)) {
            SqlRow row = this.dbUpdateInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
            completeAttrs.put(OLD_CONTENT_ATTR, oldContent);
            SqlRow row = this.dbUpdateInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(DELETE_ACTION)) {
            SqlRow row = this.dbUpdateInfo.fillOneRow(rs, oldContent, this.transformer);
            row.addAttributes(completeAttrs);
         }

      }
   }

   public void addInfo(Map attributeMap) throws Exception {
      // nothing to be done here
   }

   public int done() throws Exception {
      int ret = this.dbUpdateInfo.getRowCount(); 
      this.out.write(this.dbUpdateInfo.toXml("").getBytes());
      this.out.flush();
      this.dbUpdateInfo = null;
      return ret;
   }

   public void setOutputStream(OutputStream out, String command, String ident, ChangeEvent event) throws Exception {
      this.out = out;
      this.transactionId = null;
      this.dbUpdateInfo = new SqlInfo(this.info);
      SqlDescription description = new SqlDescription(this.info);
      description.setCommand(REPLICATION_CMD);
      if (ident != null)
         description.setIdentity(ident);
      this.dbUpdateInfo.setDescription(description);
      this.event = event;
   }

   public String getPostStatement() {
      if (this.transactionId == null) {
         if (this.dbUpdateInfo != null)
            log.severe("No transaction id has been found for " + this.dbUpdateInfo.toXml(""));
         return null;
      }
      // TODO REMOVE THIS AFTER TESTING
      log.info("sending '" + this.event.getAttributeMap().get(REPL_KEY_ATTR) + "' for table '" + this.event.getAttributeMap().get("_tableName__") + "'");
      String statement = "DELETE FROM " + this.replPrefix + "ITEMS WHERE TRANS_KEY='" + this.transactionId + "'";
      return statement;
   }
   
   
}
