/*------------------------------------------------------------------------------
Name:      ReplicationConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;


import java.util.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter;

/**
 * Creates a standardized XML dump from the given ResultSets.
 * <p />
 * Configurations are:
 * <ul>
 *   <li><tt>converter.rootName</tt>
 *       The root tag name, defaults to <tt>sql</tt>
 *   </li>
 *   <li><tt>converter.addMeta</tt>
 *       Suppress meta information, the CREATE statement however will
 *       always transport the meta tags
 *   </li>
 *    <li><tt>charSet</tt>  The encoding, defaults to <tt>UTF-8</tt></li>
 *    <li><tt>transformer.class</tt>
 *      If not empty or null the specified plugin implementing
 *       {@link org.xmlBlaster.contrib.dbwatcher.convert.I_AttributeTransformer} is loaded. 
 *      This plugin is called once for each xml dump and adds <tt>&lt;attr></tt> tags as returned by the plugin
 *    </li>
 * </ul>
 * <p>
 * Here is an example XML dump, note that all meta data settings (like isNullable)
 * are as described in JDBC (see ResultSetMetaData.java):
 * <pre>
&lt;?xml version='1.0' encoding='UTF-8' ?>
&lt;sql>
 &lt;desc>
  &lt;command>INSERT&lt;/command>
  &lt;ident>AFTN_CIRCUIT_STATE&lt;/ident>
  &lt;colname type='DATE' nullable='0'>DATUM&lt;/colname>
  &lt;colname type='NUMBER' precision='11' signed='false'>CPU&lt;/colname>
  &lt;colname type='NUMBER' precision='10' scale='3'>OLG&lt;/colname>
  &lt;colname type='VARCHAR2' precision='8' nullable='0'>FS_ST&lt;/colname>
 &lt;/desc>
 &lt;row num='0'>
  &lt;col name='DATUM'>2005-01-05 15:41:36.0&lt;/col>
  &lt;col name='CPU'>238089&lt;/col>
  &lt;col name='OLG'>-12.333&lt;/col>
  &lt;col name='FS_ST'>GW&lt;/col>
  &lt;attr name='SUBNET_ID'>TCP&lt;/attr>
  &lt;attr name='CIRCUIT_STATE'>OPERATIVE&lt;/attr>
 &lt;/row>
 &lt;row num='1'>
  &lt;col name='DATUM'>2005-01-05 15:41:36.0&lt;/col>
  &lt;col name='CPU'>238092&lt;/col>
  &lt;col name='OLG'>1.513&lt;/col>
  &lt;col name='FS_ST'>GW&lt;/col>
  &lt;attr name='SUBNET_ID'>TCP&lt;/attr>
  &lt;attr name='CIRCUIT_STATE'>OPERATIVE&lt;/attr>
 &lt;/row>
&lt;/sql>
 * </pre>
 * <p>
 * The additional &lt;attr> tags can be created by configuring an
 * {@link I_AttributeTransformer} plugin.
 * </p>
 * <p>
 * This class is not thread save,
 * use separate instances if used by multiple threads.
 * </p>
 * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter
 * @author Michele Laghi
 */
public class ReplicationConverter extends ResultSetToXmlConverter implements ReplicationConstants {
   private static Logger log = Logger.getLogger(ReplicationConverter.class.getName());
   
   /**
    * Default constructor, you need to call <tt>init(info)</tt> thereafter. 
    */
   public ReplicationConverter() { 
      super();
   }

   /**
    * Create this plugin. 
    * @param info Possible configuration parameters you find in the class description
    * @throws Exception If transformer instantiation fails
    */
   public ReplicationConverter(I_Info info) throws Exception {
      super(info);
   }
   
   /**
    * Add another result set to the XML string
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#addInfo(ResultSet, int)
    */
   public void addInfo(ResultSet rs, int what) throws Exception {
      if (rs == null)
         throw new IllegalArgumentException("ReplicationConverter: Given ResultSet is null");
      if (this.out == null)
         throw new IllegalArgumentException("ReplicationConverter: Please call setOutputStream() first"); 

      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
     
      if (numberOfColumns != 10)
         throw new Exception("ReplicationConverter.addInfo: wrong number of columns: should be 10 but was " + numberOfColumns);
      
      int replKey = rs.getInt(1);
      Timestamp transactionTimestamp = rs.getTimestamp(2);
      String dbId = rs.getString(3);
      String tableName = rs.getString(4);
      String guid = rs.getString(5);
      String action = rs.getString(6);
      String schema = rs.getString(7);
      String newContent = rs.getString(8); // could be null
      String oldContent = rs.getString(9);
      String version = rs.getString(10);
      
      // this.ident = tableName; // we let the replication table name to avoid multiple msg on same transaction
      // this.command = action;
      this.command = REPLICATION_CMD; // since it can be different on a row basis ...

      StringBuffer buf = new StringBuffer(4096);
      
      if (this.rowCounter == 0L) {
         // Create the header meta information
         buf.append("\n <desc>");
         if (this.command != null) {
            buf.append("\n  <command>").append(this.command).append("</command>");
         }
         if (this.ident != null) {
            buf.append("\n  <ident>").append(this.ident).append("</ident>");
         }

         if (this.transformer != null) {
            Map attr = this.transformer.transform(rs, -1);
            if (attr != null) {
               this.out.write(buf.toString().getBytes(this.charSet));
               buf.setLength(0);
               addInfo(attr);
            }
         }
         buf.append("\n </desc>");
         this.commandIsAdded = true;
      }

      if (what == ALL || what == ROW_ONLY) {
         buf.append("\n <row num='").append(""+this.rowCounter).append("'>");
         this.rowCounter++;

         if (action.equalsIgnoreCase(INSERT_ACTION)) {
            buf.append(newContent);
         }
         else if (action.equalsIgnoreCase(UPDATE_ACTION)) {
            buf.append(newContent);
            // TODO add the old content for check in an attribute (only if configured that way)
            // could be useful for debugging and inconsistency detection.
         }
         else if (action.equalsIgnoreCase(DELETE_ACTION)) {
            buf.append(oldContent);
         }

         Map completeAttrs = new HashMap();
         completeAttrs.put(TABLE_NAME_ATTR, tableName);
         completeAttrs.put(REPL_KEY_ATTR, "" + replKey);
         completeAttrs.put(TRANSACTION_ATTR, transactionTimestamp.toString());
         completeAttrs.put(DB_ID_ATTR, dbId);
         completeAttrs.put(GUID_ATTR, guid);
         completeAttrs.put(SCHEMA_ATTR, schema);
         completeAttrs.put(VERSION_ATTR, version);
         completeAttrs.put(ACTION_ATTR, action);
         
         if (this.transformer != null) {
            Map attr = this.transformer.transform(rs, this.rowCounter);
            if (attr != null) {
               completeAttrs.putAll(attr);
            }
         }
         this.out.write(buf.toString().getBytes(this.charSet));
         buf.setLength(0);
         addInfo(completeAttrs);
         buf.append("\n </row>");
      }

      this.out.write(buf.toString().getBytes(this.charSet));
   }
}
