/*------------------------------------------------------------------------------
Name:      DbInfoUpdateColDescription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import org.xmlBlaster.util.def.Constants;

public class DbUpdateInfoColDescription {

   public final static String COLNAME_TAG = "colname";
   public final static String TABLE_ATTR = "table";
   public final static String SCHEMA_ATTR = "schema";
   public final static String CATALOG_ATTR = "catalog";
   public final static String TYPE_ATTR = "type";
   public final static String PRECISION_ATTR = "precision";
   public final static String SCALE_ATTR = "scale";
   public final static String NULLABLE_ATTR = "nullable";
   public final static String SIGNED_ATTR = "signed";
   public final static String RO_ATTR = "readOnly";
   // new attributes
   public final static String PK_ATTR = "pk"; // primary key
   
   private String colName;
   private String table;
   private String schema;
   private String catalog;
   private String type;
   private String precision;
   private String scale;
   private int nullable; // no default: is always written out
   private boolean signed = true;   // defaults to true
   private boolean readOnly; // defaults to false
   private boolean primaryKey; // defaults to false (but unknown)
   private boolean primaryKeyKnown; // on first write it will be known

   // note these parameters are not serialized (i.e. they are not written out on the toXml method)
   private int sqlType;
   
   public DbUpdateInfoColDescription() {
   }

   
   public String getCatalog() {
      return this.catalog;
   }


   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public String getPrecision() {
      return this.precision;
   }


   public void setPrecision(String precision) {
      this.precision = precision;
   }


   public boolean isReadOnly() {
      return this.readOnly;
   }


   public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
   }


   public String getScale() {
      return this.scale;
   }


   public void setScale(String scale) {
      this.scale = scale;
   }


   public String getSchema() {
      return this.schema;
   }


   public void setSchema(String schema) {
      this.schema = schema;
   }


   public boolean isSigned() {
      return this.signed;
   }


   public void setSigned(boolean signed) {
      this.signed = signed;
   }


   public String getTable() {
      return this.table;
   }


   public void setTable(String table) {
      this.table = table;
   }


   public String getType() {
      return this.type;
   }


   public void setSqlType(int sqlType) {
      this.sqlType = sqlType;
   }
   
   public int getSqlType() {
      return this.sqlType;
   }
   
   public void setType(String type) {
      this.type = type;
   }


   public String getColName() {
      return this.colName;
   }

   public void setColName(String colName) {
      this.colName = colName;
   }


   private boolean stringExists(String str) {
      return str != null && str.length() > 0;
   }
   
   public boolean isPrimaryKeyKnown() {
      return this.primaryKeyKnown;
   }

   public boolean isPrimaryKey() {
      return this.primaryKey;
   }

   public void setPrimaryKey(boolean primaryKey) {
      this.primaryKeyKnown = true;
      this.primaryKey = primaryKey;
   }


   public int getNullable() {
      return this.nullable;
   }

   public void setNullable(int nullable) {
      this.nullable = nullable;
   }


   public String toXml(String extraOffset) {
      StringBuffer buf = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      buf.append(offset).append("<").append(COLNAME_TAG);
      if (stringExists(this.table))
         buf.append(" ").append(TABLE_ATTR).append("='").append(this.table).append("'");
      if (stringExists(this.schema))
         buf.append(" ").append(SCHEMA_ATTR).append("='").append(this.schema).append("'");
      if (stringExists(this.catalog))
         buf.append(" ").append(CATALOG_ATTR).append("='").append(this.catalog).append("'");
      if (stringExists(this.type))
         buf.append(" ").append(TYPE_ATTR).append("='").append(this.type).append("'");
      if (stringExists(this.precision))
         buf.append(" ").append(PRECISION_ATTR).append("='").append(this.precision).append("'");
      if (stringExists(this.scale))
         buf.append(" ").append(SCALE_ATTR).append("='").append(this.scale).append("'");
      // always write this out since there is no default defined.
      buf.append(" ").append(NULLABLE_ATTR).append("='").append(this.nullable).append("'");
      if (!this.signed) // don't write the default which is 'true' (also check the parser)
         buf.append(" ").append(SIGNED_ATTR).append("='").append(this.signed).append("'");
      if (this.readOnly) // don't write the default which is 'false' (also check the parser)
         buf.append(" ").append(RO_ATTR).append("='").append(this.readOnly).append("'");
      if (this.primaryKeyKnown)
         buf.append(" ").append(PK_ATTR).append("='").append(this.primaryKey).append("'");
      buf.append(">");
      buf.append(this.colName);
      buf.append("</").append(COLNAME_TAG).append(">");
      return buf.toString();
   }
   
}
