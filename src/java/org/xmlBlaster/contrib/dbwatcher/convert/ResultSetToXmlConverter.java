/*------------------------------------------------------------------------------
Name:      ResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.convert;


import java.util.logging.Logger;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Iterator;
import java.util.Properties;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwatcher.ChangeEvent;

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
 *   <li><tt>converter.postStatement</tt>
 *       A statement to be executed after a message has been published
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
 * @author Marcel Ruff
 */
public class ResultSetToXmlConverter implements I_DataConverter
{
   private static Logger log = Logger.getLogger(ResultSetToXmlConverter.class.getName());
   protected I_AttributeTransformer transformer;
   protected String rootTag;
   protected OutputStream out;
   protected String command;
   protected String ident;
   protected int rowCounter;
   protected boolean commandIsAdded;
   protected boolean doneCalled;
   protected boolean addMeta;
   protected String postStatement;
   protected String charSet;
   private int maxRows;
   /**
    * Default constructor, you need to call <tt>init(info)</tt> thereafter. 
    */
   public ResultSetToXmlConverter() { 
      // void
   }

   /**
    * Create this plugin. 
    * @param info Possible configuration parameters you find in the class description
    * @throws Exception If transformer instantiation fails
    */
   public ResultSetToXmlConverter(I_Info info) throws Exception {
      init(info);
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#init(I_Info)
    */
   public synchronized void init(I_Info info) throws Exception {
      this.rootTag = info.get("converter.rootName", "sql");
      this.addMeta = info.getBoolean("converter.addMeta", true);
      this.postStatement = info.get("converter.postStatement", (String)null);
      if (this.postStatement != null) {
         this.postStatement = this.postStatement.trim();
         if (this.postStatement.length() < 1)
            this.postStatement = null;
      }
      this.charSet = info.get("charSet", "UTF-8");
      this.maxRows = info.getInt("converter.maxRows", 0);
      ClassLoader cl = this.getClass().getClassLoader();
      
      String transformerClassName = info.get("transformer.class", "");
      if (transformerClassName != null && transformerClassName.length() > 0) {
         this.transformer = (I_AttributeTransformer)cl.loadClass(transformerClassName).newInstance();
         this.transformer.init(info);
         log.info("Loaded transformer pluing '" + transformerClassName + "'");
      }
   }

   /**
    * This should be called before the first #addInfo(ResultSet) call.
    * @param event can be null since it is not used in this implementation.
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#setOutputStream(OutputStream, String, String)
    */
   public void setOutputStream(OutputStream out, String command, String ident, ChangeEvent event) throws Exception {
      if (this.out != null) {
         try { this.out.close(); } catch (java.io.IOException e) { /* Ignore */ }
      }
      this.out = out;
      this.command = command;
      this.ident = ident;
      this.rowCounter = 0;
      this.doneCalled = false;
      this.commandIsAdded = false;
      StringBuffer buf = new StringBuffer("<?xml version='1.0' encoding='").append(this.charSet).append("' ?>");
      buf.append("\n<").append(rootTag).append(">");
      this.out.write(buf.toString().getBytes(this.charSet));
   }

   /**
    * Add a map with attributes to the XML string. 
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#addInfo(Map)
    */
   public void addInfo(Map attributeMap) throws Exception {
      if (attributeMap == null)
         throw new IllegalArgumentException("ResultSetToXmlConverter: Given attribute map is null");
      if (this.out == null)
         throw new IllegalArgumentException("ResultSetToXmlConverter: Please call setOutputStream() first"); 
      
      StringBuffer buf = new StringBuffer(128*(attributeMap.size()+1));
      Iterator it = attributeMap.keySet().iterator();
      while (it.hasNext()) {
         String name = (String)it.next();
         // Possible attributes are 'name', 'size', 'type', 'encoding', see org.xmlBlaster.util.EncodableData
         buf.append("\n  <attr name='").append(name).append("'");
         String value = (String)attributeMap.get(name);
         int need = protectionNeeded(value);
         if (need == 0)
            buf.append(">").append(value==null?"":value);
         else if (need == 1)
            buf.append("><![CDATA[").append(value).append("]]>");
         else if (need == 2)
            buf.append(" encoding='").append(BASE64).append("'>").append(Base64.encode(value.getBytes()));
         buf.append("</attr>");
      }
      if (buf.length() > 0)
         this.out.write(buf.toString().getBytes(this.charSet));
   }
   
   /**
    * Add another result set to the XML string
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#addInfo(ResultSet, int)
    */
   public void addInfo(ResultSet rs, int what) throws Exception {
      if (rs == null)
         throw new IllegalArgumentException("ResultSetToXmlConverter: Given ResultSet is null");
      if (this.out == null)
         throw new IllegalArgumentException("ResultSetToXmlConverter: Please call setOutputStream() first"); 

      if (this.maxRows > 0 && this.rowCounter > this.maxRows)
         return;

      ResultSetMetaData meta = rs.getMetaData();
      int numberOfColumns = meta.getColumnCount();
      StringBuffer buf = new StringBuffer(4096);
     
      if (this.rowCounter == 0L) {
         // Create the header meta information
         buf.append("\n <desc>");
         //buf.append("\n  <statement><![CDATA[");
         //buf.append(rs.getStatement().toString()); // is not available!
         //buf.append("]]></statement>");
         if (this.command != null) {
            buf.append("\n  <command>").append(this.command).append("</command>");
         }
         if (this.ident != null) {
            buf.append("\n  <ident>").append(this.ident).append("</ident>");
         }

         // The CREATE command will allways have meta informations added
         if ((what == ALL && this.addMeta == true) || what == META_ONLY) { // rs.isFirst()) {
            for (int i=1; i<=numberOfColumns; i++) {
               buf.append("\n  <colname");
               String tn = meta.getTableName(i);
               if (tn != null && tn.length() > 0)
                  buf.append(" table='").append(meta.getTableName(i)).append("'");
               String schema = meta.getSchemaName(i);
               if (schema != null && schema.length() > 0)
                  buf.append(" schema='").append(schema).append("'");
               String catalog = meta.getCatalogName(i);
               if (catalog != null && catalog.length() > 0)
                  buf.append(" catalog='").append(catalog).append("'");
               buf.append(" type='").append(meta.getColumnTypeName(i)).append("'");
               if (meta.getPrecision(i) > 0)
                  buf.append(" precision='").append(meta.getPrecision(i)).append("'");
               if (meta.getScale(i) > 0)
                  buf.append(" scale='").append(meta.getScale(i)).append("'");
               // always write this since it is not a boolean and it has no default ...
               buf.append(" nullable='").append(meta.isNullable(i)).append("'");
               if (meta.isSigned(i)==false)
                  buf.append(" signed='").append(meta.isSigned(i)).append("'");
               if (meta.isReadOnly(i)==true)
                  buf.append(" readOnly='").append(meta.isReadOnly(i)).append("'");
               buf.append(">");
               buf.append(meta.getColumnName(i));
               buf.append("</colname>");
            }
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

         for (int i=1; i<=numberOfColumns; i++) {
            // Possible attributes are 'name', 'size', 'type', 'encoding', see org.xmlBlaster.util.EncodableData
            buf.append("\n  <col name='").append(meta.getColumnName(i)).append("'");
            String value = rs.getString(i);
            int need = protectionNeeded(value);
            if (need == 0)
               buf.append(">").append(value==null?"":value);
            else if (need == 1)
               buf.append("><![CDATA[").append(value).append("]]>");
            else if (need == 2)
               buf.append(" encoding='").append(BASE64).append("'>").append(Base64.encode(value.getBytes()));
            buf.append("</col>");
         }
         if (this.transformer != null) {
            Map attr = this.transformer.transform(rs, this.rowCounter);
            if (attr != null) {
               this.out.write(buf.toString().getBytes(this.charSet));
               buf.setLength(0);
               addInfo(attr);
            }
         }
         buf.append("\n </row>");
      }

      this.out.write(buf.toString().getBytes(this.charSet));

      /* isLast() is not always available!
      if (rs.isLast()) {
         done();
         log.info("Created new XML dump");
      }
      */
   }
   
   /**
    * If value contains XML harmful characters it needs to be
    * wrapped by CDATA or encoded to Base64. 
    * @param value The string to verify
    * @return 0 No protection necessary
    *         1 Protection with CDATA is needed
    *         2 Protection with Base64 is needed
    */
   protected int protectionNeeded(String value) {
      if (value == null) return 0;
      if (value.indexOf("]]>") >= 0)
         return 2;
      for (int i=0; i<value.length(); i++) {
         int c = value.charAt(i);
         if (c == '<' || c == '&')
                 return 1;
      }
      return 0;
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#done
    */
   public int done() throws Exception { //java.io.UnsupportedEncodingException, java.io.IOException {
      if (this.out == null) return 0;
      if (this.doneCalled) return this.rowCounter;
      this.doneCalled = true;
      StringBuffer buf = new StringBuffer();
      if (!this.commandIsAdded) {
         buf.append("\n <desc>");
         if (this.command != null) {
            buf.append("\n  <command>").append(this.command).append("</command>");
         }
         if (this.ident != null) {
            buf.append("\n  <ident>").append(this.ident).append("</ident>");
         }
         buf.append("\n </desc>");
      }
      buf.append("\n</").append(this.rootTag).append(">");
      this.out.write(buf.toString().getBytes(this.charSet));
      this.out.flush();
      return this.rowCounter;
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#shutdown
    */
   public synchronized void shutdown() throws Exception {
      if (this.transformer != null) {
         this.transformer.shutdown();
         this.transformer = null;
      }
   }

   public String getPostStatement() {
      return this.postStatement;
   }

   public static byte[] getResultSetAsXmlLiteral(ResultSet rs, String command, String ident, long maxRows) throws Exception {
      Properties props = new Properties();
      if (maxRows > 0L) {
         if (maxRows > Integer.MAX_VALUE)
            throw new Exception("Too many maxRows: '" + maxRows + "' but should be maximum '" + Integer.MAX_VALUE + "'");
         props.put("converter.maxRows", "" + maxRows);
      }
      ResultSetToXmlConverter converter = new ResultSetToXmlConverter(new PropertiesInfo(props));
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      converter.setOutputStream(baos, command, ident, null);
      while (rs.next())
         converter.addInfo(rs, ResultSetToXmlConverter.ROW_ONLY);
      converter.done();
      converter.shutdown();
      return baos.toByteArray();
   }
   
   
}
