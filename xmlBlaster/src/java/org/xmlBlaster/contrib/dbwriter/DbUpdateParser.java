/*------------------------------------------------------------------------------
Name:      DbUpdateParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwriter;

import org.jutils.log.LogChannel;
import org.xml.sax.Attributes;
import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoColDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfo;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoDescription;
import org.xmlBlaster.contrib.dbwriter.info.DbUpdateInfoRow;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * @author laghi@swissinfo.org
 */
public class DbUpdateParser extends XmlParserBase implements I_Parser {
   
   private String ME = "DbUpdateParser";
   
   public final static String ATTR_TAG = "attr";
   
   private DbUpdateInfo updateRecord;
   
   private boolean inDescription = false;
   private boolean inRow = false;
   private DbUpdateInfoRow recordRow;
   private DbUpdateInfoDescription recordDescription;
   private LogChannel log;
   private DbUpdateInfoColDescription colDescription;
   private I_Info info; 
   boolean caseSensitive;
   
   
   public DbUpdateParser() throws Exception {
      this(null);
   }

   /**
    * Can be used as singleton.
    */
   public DbUpdateParser(I_Info info) throws Exception {
      super(null,  DbUpdateInfo.SQL_TAG);
      super.addAllowedTag(DbUpdateInfoRow.COL_TAG);
      super.addAllowedTag(ATTR_TAG);
      this.log = glob.getLog("contrib");
      init(info);
   }
   
   public void init(I_Info info) throws Exception {
      this.info = info;
   }

   public void shutdown() throws Exception {
   }

   public DbUpdateInfo parse(String data) throws Exception {
      return readObject(data);
   }



   /**
    * Parses the given xml Qos and returns a StatusQosData holding the data. 
    * Parsing of update() and publish() QoS is supported here.
    * @param the XML based ASCII string
    */
   public synchronized DbUpdateInfo readObject(String xmlQos) throws XmlBlasterException {
      if (xmlQos == null) {
         xmlQos = "<" + DbUpdateInfo.SQL_TAG + "/>";
      }

      this.updateRecord = new DbUpdateInfo(this.info);

      if (!isEmpty(xmlQos)) // if possible avoid expensive SAX parsing
         init(xmlQos);      // use SAX parser to parse it (is slow)

      return this.updateRecord;
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

      if (name.equalsIgnoreCase(DbUpdateInfoDescription.DESC_TAG)) {
         if (!this.inRootTag)
            return;
         this.inDescription = true;
         this.recordDescription = new DbUpdateInfoDescription(this.info);
         this.updateRecord.setDescription(this.recordDescription);
         return;
      }

      if (name.equalsIgnoreCase(DbUpdateInfoRow.ROW_TAG)) {
         if (!this.inRootTag)
            return;
         this.inRow = true;
         int number = 0;
         if (attrs != null) {
            String tmp = attrs.getValue(DbUpdateInfoRow.NUM_ATTR);
            if (tmp != null) {
               number = Integer.parseInt(tmp.trim());
            }
         }
         this.recordRow = new DbUpdateInfoRow(this.info, number);
         this.updateRecord.getRows().add(this.recordRow);
               
         return;
      }

      // we still don't know the name, this will be handed in end of tag
      if (name.equalsIgnoreCase(DbUpdateInfoColDescription.COLNAME_TAG)) {
         if (!this.inRootTag)
            return;
         this.colDescription = new DbUpdateInfoColDescription(this.info);
         if (attrs != null) {
            String tmp = attrs.getValue(DbUpdateInfoColDescription.TABLE_ATTR);
            if (tmp != null)
               colDescription.setTable(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.SCHEMA_ATTR);
            if (tmp != null)
               colDescription.setSchema(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.CATALOG_ATTR);
            if (tmp != null)
               colDescription.setCatalog(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.TYPE_ATTR);
            if (tmp != null)
               colDescription.setType(tmp.trim());
            
            int tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.PRECISION_ATTR, 0);
            colDescription.setPrecision(tmpInt);
            
            tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.DATA_TYPE_ATTR, 0);
            colDescription.setSqlType(tmpInt);

            tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.COLUMN_SIZE_ATTR, 0);
            colDescription.setColSize(tmpInt);
            
            tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.NUM_PREC_RADIX_ATTR, 0);
            colDescription.setRadix(tmpInt);
            
            tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.CHAR_OCTET_LENGTH_ATTR, 0);
            colDescription.setCharLength(tmpInt);
            
            tmpInt = getIntAttr(attrs, DbUpdateInfoColDescription.ORDINAL_POSITION_ATTR, 0);
            colDescription.setPos(tmpInt);

            tmp = attrs.getValue(DbUpdateInfoColDescription.REMARKS_ATTR);
            if (tmp != null)
               colDescription.setRemarks(tmp.trim());
            
            tmp = attrs.getValue(DbUpdateInfoColDescription.COLUMN_DEF_ATTR);
            if (tmp != null)
               colDescription.setColDefault(tmp.trim());
            
            int intTmp = getIntAttr(attrs, DbUpdateInfoColDescription.SCALE_ATTR, 0);
            colDescription.setScale(intTmp);
            
            tmp = attrs.getValue(DbUpdateInfoColDescription.NULLABLE_ATTR);
            if (tmp != null) {
               try {
                  int nullable = Integer.parseInt(tmp);
                  colDescription.setNullable(nullable);
               }
               catch (NumberFormatException ex) {
                  ex.printStackTrace();
               }
            }
            boolean bool = getBoolAttr(attrs, DbUpdateInfoColDescription.SIGNED_ATTR, true);
            colDescription.setSigned(bool);
            bool = getBoolAttr(attrs, DbUpdateInfoColDescription.RO_ATTR, false);
            colDescription.setReadOnly(bool);
            bool = getBoolAttr(attrs, DbUpdateInfoColDescription.PK_ATTR, false);
            colDescription.setPrimaryKey(bool);
            if (bool) {
               tmp = attrs.getValue(DbUpdateInfoColDescription.PK_NAME_ATTR);
               if (tmp != null)
                  colDescription.setPkName(tmp.trim());
            }
            
            bool = getBoolAttr(attrs, DbUpdateInfoColDescription.AUTO_INCREMENT_ATTR, false);
            colDescription.setAutoInc(bool);
            bool = getBoolAttr(attrs, DbUpdateInfoColDescription.CASE_SENSITIVE_ATTR, false);
            colDescription.setCaseSens(bool);
            tmp = attrs.getValue(DbUpdateInfoColDescription.LABEL_ATTR);
            if (tmp != null)
               colDescription.setLabel(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.TYPE_NAME_ATTR);
            if (tmp != null)
               colDescription.setTypeName(tmp.trim());

            tmp = attrs.getValue(DbUpdateInfoColDescription.PKTABLE_CAT_ATTR);
            if (tmp != null)
               colDescription.setFkCatalog(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.PKTABLE_SCHEM_ATTR);
            if (tmp != null)
               colDescription.setFkSchema(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.PKTABLE_NAME_ATTR);
            if (tmp != null)
               colDescription.setFkTable(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.PKCOLUMN_NAME_ATTR);
            if (tmp != null)
               colDescription.setFkCol(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.KEY_SEQ_ATTR);
            if (tmp != null)
               colDescription.setFkSeq(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.UPDATE_RULE_ATTR);
            if (tmp != null)
               colDescription.setFkUpdRule(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.DELETE_RULE_ATTR);
            if (tmp != null)
               colDescription.setFkDelRule(tmp.trim());
            tmp = attrs.getValue(DbUpdateInfoColDescription.DEFERRABILITY_ATTR);
            if (tmp != null)
               colDescription.setFkDef(tmp.trim());
         }
      }
      
  }

   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) {
      if (super.endElementBase(uri, localName, name) == true) {
         if (!this.inRow && !this.inDescription) 
            return;
         
         if (name.equalsIgnoreCase(DbUpdateInfoRow.COL_TAG)) {
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
               log.warn("DbUpdateParser", "the attr is wether in description nor row, something is fishy, will ignore it.");
            }
         }
         return;
      }

      if (name.equalsIgnoreCase(DbUpdateInfoRow.ROW_TAG)) {
         this.inRow = false;
         this.recordRow = null;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase(DbUpdateInfoDescription.DESC_TAG)) {
         this.inDescription = false;
         this.recordDescription = null;
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase(DbUpdateInfoDescription.IDENT_TAG)) {
         if (this.inDescription) 
            this.recordDescription.setIdentity(this.character.toString().trim());
         character.setLength(0);
         return;
      }

      if (name.equalsIgnoreCase(DbUpdateInfoDescription.COMMAND_TAG)) {
         if (this.inDescription) 
            this.recordDescription.setCommand(this.character.toString().trim());
         character.setLength(0);
         return;
      }

      // we still don't know the name, this will be handed in end of tag
      if (name.equalsIgnoreCase(DbUpdateInfoColDescription.COLNAME_TAG)) {
         this.colDescription.setColName(new String(this.character).trim());
         this.recordDescription.addColumnDescription(this.colDescription);
         this.colDescription = null;
         character.setLength(0);
         return;
      }
      
      character.setLength(0); // reset data from unknown tags
   }

}
