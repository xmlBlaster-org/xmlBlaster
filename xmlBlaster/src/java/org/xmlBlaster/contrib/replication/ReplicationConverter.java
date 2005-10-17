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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer;
import org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoRow;

/**
 * Creates a standardized XML dump from the given ResultSets.
 * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter
 * @see org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter
 * @author Michele Laghi
 */
public class ReplicationConverter implements I_DataConverter, ReplicationConstants {
   private static Logger log = Logger.getLogger(ReplicationConverter.class.getName());
   
   private I_DbSpecific dbSpecific;
   private DbUpdateInfo dbUpdateInfo;
   private I_Info info;
   private I_AttributeTransformer transformer;
   private OutputStream out;
   
   
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
      String dbSpecificClass = info.get("replication.dbSpecific.class", "org.xmlBlaster.contrib.replication.impl.SpecificDefault");
      I_DbSpecific dbSpecific = null;
      if (dbSpecificClass.length() > 0) {
         ClassLoader cl = ReplicationConverter.class.getClassLoader();
         dbSpecific = (I_DbSpecific)cl.loadClass(dbSpecificClass).newInstance();
         dbSpecific.init(info);
         if (log.isLoggable(Level.FINE)) log.fine(dbSpecificClass + " created and initialized");
     }
     else
        log.info("Couldn't initialize I_DataConverter, please configure 'converter.class' if you need a conversion.");
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
         log.info("Loaded transformer pluing '" + transformerClassName + "'");
      }
      this.dbSpecific = getDbSpecific(info);
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
      
      int replKey = rs.getInt(1);
      String transKey = rs.getString(2);
      String dbId = rs.getString(3);
      String tableName = rs.getString(4);
      String guid = rs.getString(5);
      String action = rs.getString(6);
      String catalog = rs.getString(7);
      String schema = rs.getString(8);
      String newContent = rs.getString(9); // could be null
      String oldContent = rs.getString(10);
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
         
         if (action.equalsIgnoreCase(CREATE_ACTION)) {
            log.info("addInfo: going to create a new table '" + tableName + "'");
            this.dbSpecific.readNewTable(catalog, schema, tableName, completeAttrs);
         }
         else if (action.equalsIgnoreCase(DROP_ACTION)) {
            DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(ALTER_ACTION)) {
            DbUpdateInfoDescription description = this.dbUpdateInfo.getDescription(); 
            description.setCommand(action);
            description.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(INSERT_ACTION)) {
            DbUpdateInfoRow row = this.dbUpdateInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
            completeAttrs.put(OLD_CONTENT_ATTR, oldContent);
            DbUpdateInfoRow row = this.dbUpdateInfo.fillOneRow(rs, newContent, this.transformer);
            row.addAttributes(completeAttrs);
         }
         else if (action.equalsIgnoreCase(DELETE_ACTION)) {
            DbUpdateInfoRow row = this.dbUpdateInfo.fillOneRow(rs, oldContent, this.transformer);
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

   public void setOutputStream(OutputStream out, String command, String ident) throws Exception {
      this.out = out;
      this.dbUpdateInfo = new DbUpdateInfo(this.info);
      DbUpdateInfoDescription description = new DbUpdateInfoDescription(this.info);
      description.setCommand(REPLICATION_CMD);
      if (ident != null)
         description.setIdentity(ident);
      this.dbUpdateInfo.setDescription(description);
   }

}
