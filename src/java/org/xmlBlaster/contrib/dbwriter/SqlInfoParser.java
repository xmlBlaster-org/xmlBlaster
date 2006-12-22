/*------------------------------------------------------------------------------
Name:      SqlInfoParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.MomEventEngine;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlColumn;
import org.xmlBlaster.contrib.dbwriter.info.SqlInfo;
import org.xmlBlaster.contrib.dbwriter.info.SqlDescription;
import org.xmlBlaster.contrib.dbwriter.info.SqlRow;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * @author laghi@swissinfo.org
 */
public class SqlInfoParser extends XmlParserBase implements I_Parser {
   
   public final static String ATTR_TAG = "attr";
   
   private SqlInfo updateRecord;
   
   private boolean inDescription = false;
   private boolean inRow = false;
   private SqlRow recordRow;
   private SqlDescription recordDescription;
   private static Logger log = Logger.getLogger(MomEventEngine.class.getName());
   private SqlColumn colDescription;
   private I_Info info; 
   boolean caseSensitive;

   public SqlInfoParser() throws Exception {
      this(null);
   }

   public static void main(String[] args) {
      if (args.length < 1) {
         System.err.println("Usage: java " + SqlInfoParser.class.getName() + " inputFilename");
         System.exit(-1);
      }
      
      try {
         I_Info info = new PropertiesInfo(System.getProperties());
         SqlInfoParser parser = new SqlInfoParser(info);
         InputSource is = new InputSource(new FileInputStream(args[0]));
         SqlInfo sqlInfo = parser.readObject(is);
         System.out.println("Number of rows: " + sqlInfo.getRowCount());
      }
      catch (Exception ex) {
         ex.printStackTrace();
      }
   }
   
   /**
    * Can be used as singleton.
    */
   public SqlInfoParser(I_Info info) throws Exception {
      super(null,  SqlInfo.SQL_TAG);
      super.addAllowedTag(SqlRow.COL_TAG);
      super.addAllowedTag(ATTR_TAG);
      init(info);
   }
   
   public void init(I_Info info) throws Exception {
      this.info = info;
   }

   /**
    * @see org.xmlBlaster.contrib.I_ContribPlugin#getUsedPropertyKeys()
    */
   public Set getUsedPropertyKeys() {
      return new HashSet();
   }

   public void shutdown() throws Exception {
   }

   public SqlInfo parse(String data) throws Exception {
      InputSource is = new InputSource(new ByteArrayInputStream(data.getBytes()));
      return readObject(is);
   }

   public SqlInfo parse(InputSource is) throws Exception {
      clearCharacter();
      return readObject(is);
   }

   /**
    * Parses the given xml Qos and returns a StatusQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized SqlInfo readObject(InputSource is) throws XmlBlasterException {
      try {
         this.updateRecord = new SqlInfo(this.info);
         init(is);      // use SAX parser to parse it (is slow)
         return this.updateRecord;
      }
      catch (XmlBlasterException ex) {
         log.severe("SqlInfoParser.readObject: could not parse input stream");
         throw ex;
      }
   }

   private final boolean getBoolAttr(Attributes attrs, String key, boolean def) {
      String tmp = attrs.getValue(key);
      if (tmp == null)
         return def;
      return (new Boolean(tmp)).booleanValue();
   }
   
   private final int getIntAttr(Attributes attrs, String key, int def) {
      String tmp = attrs.getValue(key);
      if (tmp == null)
         return def;
      return Integer.parseInt(tmp);
   }
   
   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs) {
      if (super.startElementBase(uri, localName, name, attrs) == true)
         return;

      if (name.equalsIgnoreCase(SqlDescription.DESC_TAG)) {
         if (!this.inRootTag)
            return;
         this.inDescription = true;
         this.recordDescription = new SqlDescription(this.info);
         this.updateRecord.setDescription(this.recordDescription);
         return;
      }

      if (name.equalsIgnoreCase(SqlRow.ROW_TAG)) {
         if (!this.inRootTag)
            return;
         this.inRow = true;
         int number = 0;
         if (attrs != null) {
            String tmp = attrs.getValue(SqlRow.NUM_ATTR);
            if (tmp != null) {
               number = Integer.parseInt(tmp.trim());
            }
         }
         this.recordRow = new SqlRow(this.info, number);
         this.updateRecord.getRows().add(this.recordRow);
               
         return;
      }

      // we still don't know the name, this will be handed in end of tag
      if (name.equalsIgnoreCase(SqlColumn.COLNAME_TAG)) {
         if (!this.inRootTag)
            return;
         this.colDescription = new SqlColumn(this.info);
         if (attrs != null) {
            String tmp = attrs.getValue(SqlColumn.TABLE_ATTR);
            if (tmp != null)
               colDescription.setTable(tmp.trim());
            tmp = attrs.getValue(SqlColumn.SCHEMA_ATTR);
            if (tmp != null)
               colDescription.setSchema(tmp.trim());
            tmp = attrs.getValue(SqlColumn.CATALOG_ATTR);
            if (tmp != null)
               colDescription.setCatalog(tmp.trim());
            tmp = attrs.getValue(SqlColumn.TYPE_ATTR);
            if (tmp != null)
               colDescription.setType(tmp.trim());
            
            int tmpInt = getIntAttr(attrs, SqlColumn.PRECISION_ATTR, 0);
            colDescription.setPrecision(tmpInt);
            
            tmpInt = getIntAttr(attrs, SqlColumn.DATA_TYPE_ATTR, 0);
            colDescription.setSqlType(tmpInt);

            tmpInt = getIntAttr(attrs, SqlColumn.COLUMN_SIZE_ATTR, 0);
            colDescription.setColSize(tmpInt);
            
            tmpInt = getIntAttr(attrs, SqlColumn.NUM_PREC_RADIX_ATTR, 0);
            colDescription.setRadix(tmpInt);
            
            tmpInt = getIntAttr(attrs, SqlColumn.CHAR_OCTET_LENGTH_ATTR, 0);
            colDescription.setCharLength(tmpInt);
            
            tmpInt = getIntAttr(attrs, SqlColumn.ORDINAL_POSITION_ATTR, 0);
            colDescription.setPos(tmpInt);

            tmp = attrs.getValue(SqlColumn.REMARKS_ATTR);
            if (tmp != null)
               colDescription.setRemarks(tmp.trim());
            
            tmp = attrs.getValue(SqlColumn.COLUMN_DEF_ATTR);
            if (tmp != null)
               colDescription.setColDefault(tmp.trim());
            
            int intTmp = getIntAttr(attrs, SqlColumn.SCALE_ATTR, 0);
            colDescription.setScale(intTmp);
            
            tmp = attrs.getValue(SqlColumn.NULLABLE_ATTR);
            if (tmp != null) {
               try {
                  int nullable = Integer.parseInt(tmp);
                  colDescription.setNullable(nullable);
               }
               catch (NumberFormatException ex) {
                  ex.printStackTrace();
               }
            }
            boolean bool = getBoolAttr(attrs, SqlColumn.SIGNED_ATTR, true);
            colDescription.setSigned(bool);
            bool = getBoolAttr(attrs, SqlColumn.SEARCHABLE_ATTR, true);
            colDescription.setSearchable(bool);
            bool = getBoolAttr(attrs, SqlColumn.RO_ATTR, false);
            colDescription.setReadOnly(bool);
            bool = getBoolAttr(attrs, SqlColumn.PK_ATTR, false);
            colDescription.setPrimaryKey(bool);
            if (bool) {
               tmp = attrs.getValue(SqlColumn.PK_NAME_ATTR);
               if (tmp != null)
                  colDescription.setPkName(tmp.trim());
            }
            
            bool = getBoolAttr(attrs, SqlColumn.AUTO_INCREMENT_ATTR, false);
            colDescription.setAutoInc(bool);
            bool = getBoolAttr(attrs, SqlColumn.CASE_SENSITIVE_ATTR, false);
            colDescription.setCaseSens(bool);
            tmp = attrs.getValue(SqlColumn.LABEL_ATTR);
            if (tmp != null)
               colDescription.setLabel(tmp.trim());
            tmp = attrs.getValue(SqlColumn.TYPE_NAME_ATTR);
            if (tmp != null)
               colDescription.setTypeName(tmp.trim());

            tmp = attrs.getValue(SqlColumn.FK_TABLE_CAT_ATTR);
            if (tmp != null)
               colDescription.setFkCatalog(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_TABLE_SCHEM_ATTR);
            if (tmp != null)
               colDescription.setFkSchema(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_TABLE_NAME_ATTR);
            if (tmp != null)
               colDescription.setFkTable(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_COLUMN_NAME_ATTR);
            if (tmp != null)
               colDescription.setFkCol(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_KEY_SEQ_ATTR);
            if (tmp != null)
               colDescription.setFkSeq(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_UPDATE_RULE_ATTR);
            if (tmp != null)
               colDescription.setFkUpdRule(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_DELETE_RULE_ATTR);
            if (tmp != null)
               colDescription.setFkDelRule(tmp.trim());
            tmp = attrs.getValue(SqlColumn.FK_DEFERRABILITY_ATTR);
            if (tmp != null)
               colDescription.setFkDef(tmp.trim());
         }
      }
      
  }

   private final void clearCharacter() {
      // character.setLength(0);
      character = new StringBuffer(4096);
   }
   
   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (super.endElementBase(uri, localName, name) == true) {
         if (this.inClientProperty > 0)
            return;
         if (!this.inRow && !this.inDescription) 
            return;
         
         if (name.equalsIgnoreCase(SqlRow.COL_TAG)) {
            this.recordRow.setColumn(this.clientProperty);
         }
         else if (name.equalsIgnoreCase(ATTR_TAG)) {
            if (this.inRow) { // we must distinguish between attr in description from these in rows
               this.recordRow.setAttribute(this.clientProperty);
            }
            else if (this.inDescription) {
               this.recordDescription.setAttribute(this.clientProperty);
            }
            else {
               log.warning("the attr is wether in description nor row, something is fishy, will ignore it.");
            }
         }
         return;
      }

      if (name.equalsIgnoreCase(SqlRow.ROW_TAG)) {
         this.inRow = false;
         this.recordRow = null;
         clearCharacter();
         return;
      }

      if (name.equalsIgnoreCase(SqlDescription.DESC_TAG)) {
         this.inDescription = false;
         this.recordDescription = null;
         clearCharacter();
         return;
      }

      if (name.equalsIgnoreCase(SqlDescription.IDENT_TAG)) {
         if (this.inDescription) 
            this.recordDescription.setIdentity(this.character.toString().trim());
         clearCharacter();
         return;
      }

      if (name.equalsIgnoreCase(SqlDescription.COMMAND_TAG)) {
         if (this.inDescription) 
            this.recordDescription.setCommand(this.character.toString().trim());
         clearCharacter();
         return;
      }

      // we still don't know the name, this will be handed in end of tag
      if (name.equalsIgnoreCase(SqlColumn.COLNAME_TAG)) {
         this.colDescription.setColName(new String(this.character).trim());
         this.recordDescription.addColumn(this.colDescription);
         this.colDescription = null;
         clearCharacter();
         return;
      }
      clearCharacter(); // reset data from unknown tags
   }

}
