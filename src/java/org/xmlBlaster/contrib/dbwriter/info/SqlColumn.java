/*------------------------------------------------------------------------------
Name:      DbInfoUpdateColDescription.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.sql.Timestamp;
import java.sql.Types;
import java.util.Properties;
import java.util.Random;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.PropertiesInfo;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class SqlColumn {

   public final static String COLNAME_TAG = "colname";   
   public final static String SCHEMA_ATTR = "schema";
   public final static String CATALOG_ATTR = "catalog";
   public final static String TABLE_ATTR = "table";
   public final static String TYPE_ATTR = "type";
   public final static String PRECISION_ATTR = "precision";
   public final static String SCALE_ATTR = "scale";
   public final static String NULLABLE_ATTR = "nullable";
   public final static String SIGNED_ATTR = "signed";
   public final static String SEARCHABLE_ATTR = "searchable";
   public final static String RO_ATTR = "readOnly";
   public final static String DATA_TYPE = "datatype";
   
   // new attributes
   public final static String PK_ATTR = "pk"; // primary key
   public final static String PK_NAME_ATTR = "pkName"; // primary key
 
   public final static String FK_TABLE_CAT_ATTR = "fkCatalog";
   public final static String FK_TABLE_SCHEM_ATTR = "fkSchema";
   public final static String FK_TABLE_NAME_ATTR = "fkTable";
   public final static String FK_COLUMN_NAME_ATTR = "fkCol";
   public final static String FK_KEY_SEQ_ATTR = "fkSeq";
   public final static String FK_UPDATE_RULE_ATTR = "fkUpdRule";
   public final static String FK_DELETE_RULE_ATTR = "fkDelRule";
   public final static String FK_DEFERRABILITY_ATTR = "fkDef";

   public final static String DATA_TYPE_ATTR = "sqlType";            // position(05)
   public final static String COLUMN_SIZE_ATTR = "colSize";          // position(07)
   public final static String DECIMAL_DIGITS_ATTR = "digits";        // position(09)
   public final static String NUM_PREC_RADIX_ATTR = "radix";         // position(10);
   public final static String REMARKS_ATTR = "remarks";              // position(12)
   public final static String COLUMN_DEF_ATTR = "colDefault";        // position(13) this is the default
   public final static String CHAR_OCTET_LENGTH_ATTR = "charLength"; // position(16)
   public final static String ORDINAL_POSITION_ATTR = "pos";         // position(17)

   public final static String LABEL_ATTR = "label";
   public final static String AUTO_INCREMENT_ATTR = "autoInc";
   public final static String CASE_SENSITIVE_ATTR = "caseSens";
   public final static String TYPE_NAME_ATTR = "typeName";
   
/*   
   System.out.println(rs.getString(1) + "1. PKTABLE_CAT String => primary key table catalog being imported (may be null)");
   System.out.println(rs.getString(2) + "2. PKTABLE_SCHEM String => primary key table schema being imported (may be null)");
   System.out.println(rs.getString(3) + "3. PKTABLE_NAME String => primary key table name being imported");
   System.out.println(rs.getString(4) + "4. PKCOLUMN_NAME String => primary key column name being imported");
   System.out.println(rs.getString(9) + "9. KEY_SEQ short => sequence number within a foreign key");
   System.out.println(rs.getString(10) + "10. UPDATE_RULE short => What happens to a foreign key when the primary key is updated:");
   System.out.println("       * importedNoAction - do not allow update of primary key if it has been imported");
   System.out.println("       * importedKeyCascade - change imported key to agree with primary key update");
   System.out.println("       * importedKeySetNull - change imported key to NULL if its primary key has been updated");
   System.out.println("       * importedKeySetDefault - change imported key to default values if its primary key has been updated");
   System.out.println("       * importedKeyRestrict - same as importedKeyNoAction (for ODBC 2.x compatibility) ");
   System.out.println(rs.getString(11) + "11. DELETE_RULE short => What happens to the foreign key when primary is deleted.");
   System.out.println("       * importedKeyNoAction - do not allow delete of primary key if it has been imported");
   System.out.println("       * importedKeyCascade - delete rows that import a deleted key");
   System.out.println("       * importedKeySetNull - change imported key to NULL if its primary key has been deleted");
   System.out.println("       * importedKeyRestrict - same as importedKeyNoAction (for ODBC 2.x compatibility)");
   System.out.println("       * importedKeySetDefault - change imported key to default if its primary key has been deleted ");
   System.out.println(rs.getString(14) + "14. DEFERRABILITY short => can the evaluation of foreign key constraints be deferred until commit");
   System.out.println("       * importedKeyInitiallyDeferred - see SQL92 for definition");
   System.out.println("       * importedKeyInitiallyImmediate - see SQL92 for definition");
   System.out.println("       * importedKeyNotDeferrable - see SQL92 for definition ");
*/   
   
   private String colName;
   private String table;
   private String schema;
   private String catalog;
   private String type;
   private int precision; // DECIMAL_DIGITS position (9)
   private int scale;  // <-- not used in replication 
   private int nullable; // no default: is always written out
   private boolean searchable = true; // defaults to true 
   private boolean signed = true;   // defaults to true <-- not used in replication
   private boolean readOnly; // defaults to false <-- not used in replication 
   private boolean primaryKey; // defaults to false (but unknown)
   private String  pkName;
   // private boolean primaryKeyKnown; // on first write it will be known

   // additional variables not defined in original DbWatcher
   private int sqlType; // DATA_TYPE (position 5)
   private int colSize; // COLUMN_SIZE (position 7)
   private int radix;  // NUM_PREC_RADIX position(10);
   private String remarks; // REMARKS position(12)
   private String colDefault; // COLUMN_DEF position(13) this is the default
   private int charLength; // CHAR_OCTET_LENGTH position(16)
   private int pos; // ORDINAL_POSITION position(17)

   private String fkCatalog;
   private String fkSchema;
   private String fkTable; // if this is set, then it is a foreign key
   private String fkCol;
   private String fkSeq;
   private String fkUpdRule;
   private String fkDelRule;
   private String fkDef;
   // new additions 2005-10-10
   private String label;
   private boolean autoInc;
   private boolean caseSens;
   private String typeName;
   
   public SqlColumn(I_Info info) {
   }

   public String getCatalog() {
      return this.catalog;
   }

   public void setCatalog(String catalog) {
      this.catalog = catalog;
   }

   public int getPrecision() {
      return this.precision;
   }

   public void setPrecision(int precision) {
      this.precision = precision;
   }

   public boolean isReadOnly() {
      return this.readOnly;
   }
   
   public boolean isSearchable() {
      return this.searchable;
   }

   public void setSearchable(boolean searchable) {
      this.searchable = searchable;
   }

   public void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
   }

   public int getScale() {
      return this.scale;
   }

   public void setScale(int scale) {
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

   public String getFkCatalog() {
      return fkCatalog;
   }

   public void setFkCatalog(String fkCatalog) {
      this.fkCatalog = fkCatalog;
   }

   public String getFkCol() {
      return fkCol;
   }

   public void setFkCol(String fkCol) {
      this.fkCol = fkCol;
   }

   public String getFkDef() {
      return fkDef;
   }

   public void setFkDef(String fkDef) {
      this.fkDef = fkDef;
   }

   public String getFkDelRule() {
      return fkDelRule;
   }

   public void setFkDelRule(String fkDelRule) {
      this.fkDelRule = fkDelRule;
   }

   public String getFkSchema() {
      return fkSchema;
   }

   public void setFkSchema(String fkSchema) {
      this.fkSchema = fkSchema;
   }

   public String getFkSeq() {
      return fkSeq;
   }

   public void setFkSeq(String fkSeq) {
      this.fkSeq = fkSeq;
   }

   public String getFkTable() {
      return fkTable;
   }

   public void setFkTable(String fkTable) {
      this.fkTable = fkTable;
   }

   public String getFkUpdRule() {
      return fkUpdRule;
   }

   public void setFkUpdRule(String fkUpdRule) {
      this.fkUpdRule = fkUpdRule;
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

   public boolean isPrimaryKey() {
      return this.primaryKey;
   }

   public void setPrimaryKey(boolean primaryKey) {
      // this.primaryKeyKnown = true;
      this.primaryKey = primaryKey;
   }

   public int getNullable() {
      return this.nullable;
   }

   public void setNullable(int nullable) {
      this.nullable = nullable;
   }

   public int getCharLength() {
      return charLength;
   }

   public void setCharLength(int charLength) {
      this.charLength = charLength;
   }

   public int getColSize() {
      return colSize;
   }

   public void setColSize(int colSize) {
      this.colSize = colSize;
   }

   public String getColDefault() {
      return this.colDefault;
   }

   public void setColDefault(String colDefault) {
      this.colDefault = colDefault;
   }

   public int getPos() {
      return pos;
   }

   public void setPos(int pos) {
      this.pos = pos;
   }

   public int getRadix() {
      return radix;
   }

   public void setRadix(int radix) {
      this.radix = radix;
   }

   public String getRemarks() {
      return remarks;
   }

   public void setRemarks(String remarks) {
      this.remarks = remarks;
   }

   public String getPkName() {
      return pkName;
   }

   public void setPkName(String pkName) {
      this.pkName = pkName;
   }
 
   public boolean isAutoInc() {
      return autoInc;
   }

   public void setAutoInc(boolean autoInc) {
      this.autoInc = autoInc;
   }

   public boolean isCaseSens() {
      return caseSens;
   }

   public void setCaseSens(boolean caseSens) {
      this.caseSens = caseSens;
   }

   public String getLabel() {
      return label;
   }

   public void setLabel(String label) {
      this.label = label;
   }

   public String getTypeName() {
      return typeName;
   }

   public void setTypeName(String typeName) {
      this.typeName = typeName;
   }


   
   
   private boolean stringExists(String str) {
      return str != null && str.length() > 0;
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
      if (this.precision != 0)
         buf.append(" ").append(PRECISION_ATTR).append("='").append(this.precision).append("'");
      if (this.scale != 0)
         buf.append(" ").append(SCALE_ATTR).append("='").append(this.scale).append("'");
      // always write this out since there is no default defined.
      buf.append(" ").append(NULLABLE_ATTR).append("='").append(this.nullable).append("'");
      if (!this.searchable) // don't write the default which is 'true' (also check the parser)
         buf.append(" ").append(SEARCHABLE_ATTR).append("='").append(this.searchable).append("'");
      if (!this.signed) // don't write the default which is 'true' (also check the parser)
         buf.append(" ").append(SIGNED_ATTR).append("='").append(this.signed).append("'");
      if (this.readOnly) // don't write the default which is 'false' (also check the parser)
         buf.append(" ").append(RO_ATTR).append("='").append(this.readOnly).append("'");
      if (this.sqlType != 0)
         buf.append(" ").append(DATA_TYPE_ATTR).append("='").append(this.sqlType).append("'");
      if (this.colSize != 0)
         buf.append(" ").append(COLUMN_SIZE_ATTR).append("='").append(this.colSize).append("'");
      if (this.radix != 0)
         buf.append(" ").append(NUM_PREC_RADIX_ATTR).append("='").append(this.radix).append("'");
      if (this.charLength != 0)
         buf.append(" ").append(CHAR_OCTET_LENGTH_ATTR).append("='").append(this.charLength).append("'");
      if (this.pos != 0)
         buf.append(" ").append(ORDINAL_POSITION_ATTR).append("='").append(this.pos).append("'");
      if (this.remarks != null)
         buf.append(" ").append(REMARKS_ATTR).append("='").append(this.remarks).append("'");
      if (this.colDefault != null)
         buf.append(" ").append(COLUMN_DEF_ATTR).append("='").append(this.colDefault).append("'");

      if (this.label != null)
         buf.append(" ").append(LABEL_ATTR).append("='").append(this.label).append("'");
      if (this.typeName != null)
         buf.append(" ").append(TYPE_NAME_ATTR).append("='").append(this.typeName).append("'");
      if (this.autoInc)
         buf.append(" ").append(AUTO_INCREMENT_ATTR).append("='").append(this.autoInc).append("'");
      if (this.caseSens)
         buf.append(" ").append(CASE_SENSITIVE_ATTR).append("='").append(this.caseSens).append("'");
      
      if (this.primaryKey) {
         buf.append(" ").append(PK_ATTR).append("='").append(this.primaryKey).append("'");
         if (this.pkName != null) {
            buf.append(" ").append(PK_NAME_ATTR).append("='").append(this.pkName).append("'");
         }
      }
      if (isFk()) {
         if (this.fkCatalog != null)
            buf.append(" ").append(FK_TABLE_CAT_ATTR).append("='").append(this.fkCatalog).append("'");
         if (this.fkSchema != null)
            buf.append(" ").append(FK_TABLE_SCHEM_ATTR).append("='").append(this.fkSchema).append("'");
         if (this.fkTable != null)
            buf.append(" ").append(FK_TABLE_NAME_ATTR).append("='").append(this.fkTable).append("'");
         if (this.fkCol != null)
            buf.append(" ").append(FK_COLUMN_NAME_ATTR).append("='").append(this.fkCol).append("'");
         if (this.fkSeq != null)
            buf.append(" ").append(FK_KEY_SEQ_ATTR).append("='").append(this.fkSeq).append("'");
         if (this.fkUpdRule != null)
            buf.append(" ").append(FK_UPDATE_RULE_ATTR).append("='").append(this.fkUpdRule).append("'");
         if (this.fkDelRule != null)
            buf.append(" ").append(FK_DELETE_RULE_ATTR).append("='").append(this.fkDelRule).append("'");
         if (this.fkDef != null)
            buf.append(" ").append(FK_DEFERRABILITY_ATTR).append("='").append(this.fkDef).append("'");
      }
      // this will not be parsed.
      String tmp = getSqlTypeAsText(this.getSqlType());
      if (tmp != null && !"NULL".equals(tmp))
         buf.append(" ").append(DATA_TYPE).append("='").append(tmp).append("'");

      buf.append(">");
      buf.append(this.colName);
      buf.append("</").append(COLNAME_TAG).append(">");
      return buf.toString();
   }


   public boolean isFk() {
      return this.fkTable != null;
   }

   public static String getSqlTypeAsText(int sqlType) {
      if (sqlType == Types.ARRAY)
         return "ARRAY";
      if (sqlType == Types.BIGINT)
         return "BIGINT";
      if (sqlType == Types.BINARY)
         return "BINARY";
      if (sqlType == Types.BIT)
         return "BIT";
      if (sqlType == Types.BLOB)
         return "BLOB";
      if (sqlType == Types.BOOLEAN)
         return "BOOLEAN";
      if (sqlType == Types.CHAR)
         return "CHAR";
      if (sqlType == Types.CLOB)
         return "CLOB";
      if (sqlType == Types.DATALINK)
         return "DATALINK";
      if (sqlType == Types.DATE)
         return "DATE";
      if (sqlType == Types.DECIMAL)
         return "DECIMAL";
      if (sqlType == Types.DISTINCT)
         return "DISTINCT";
      if (sqlType == Types.DOUBLE)
         return "DOUBLE";
      if (sqlType == Types.FLOAT)
         return "FLOAT";
      if (sqlType == Types.INTEGER)
         return "INTEGER";
      if (sqlType == Types.JAVA_OBJECT)
         return "JAVA_OBJECT";
      if (sqlType == Types.LONGVARBINARY)
         return "LONGVARBINARY";
      if (sqlType == Types.LONGVARCHAR)
         return "LONGVARCHAR";
      if (sqlType == Types.NULL)
         return "NULL";
      if (sqlType == Types.NUMERIC)
         return "NUMERIC";
      if (sqlType == Types.OTHER)
         return "OTHER";
      if (sqlType == Types.REAL)
         return "REAL";
      if (sqlType == Types.REF)
         return "REF";
      if (sqlType == Types.SMALLINT)
         return "SMALLINT";
      if (sqlType == Types.STRUCT)
         return "STRUCT";
      if (sqlType == Types.TIME)
         return "TIME";
      if (sqlType == Types.TIMESTAMP)
         return "TIMESTAMP";
      if (sqlType == Types.TINYINT)
         return "TINYINT";
      if (sqlType == Types.VARBINARY)
         return "VARBINARY";
      if (sqlType == Types.VARCHAR)
         return "VARCHAR";
      if (sqlType == Types.SQLXML)
    	  return "SQLXML";
      return "UNKNOWN";
   }

   
   public static SqlColumn getComplete() {
      SqlColumn desc = new SqlColumn(new PropertiesInfo(new Properties())); 
      desc.setColName("someName");
      desc.setTable("tableName");
      desc.setSchema("schemaName");
      desc.setCatalog("catalogName");
      desc.setType("typeName");
      desc.setPrecision(4);
      desc.setScale(3);
      desc.setPrimaryKey(true);
      desc.setPkName("somePkName");
      desc.setSqlType(2000);
      desc.setColSize(10);
      desc.setRadix(5);
      desc.setRemarks("Some remarks");
      desc.setColDefault("someDef");
      desc.setCharLength(29);
      desc.setPos(4);
      desc.setFkCatalog("someForCat");
      desc.setFkSchema("ForeignSchema");
      desc.setFkTable("ForeignTable");
      desc.setFkCol("FkColumn");
      desc.setFkSeq("FkSequence");
      desc.setFkUpdRule("forUpdateRule");
      desc.setFkDelRule("forDelRule");
      desc.setFkDef("FkDef");
      desc.setLabel("SomeLabel");
      desc.setAutoInc(true);
      desc.setTypeName("VARCHAR");
      desc.setCaseSens(true);
      desc.setReadOnly(true);
      desc.setNullable(4);
      desc.setSigned(true);
      return desc;
   }
   
   
   public static void main(String[] args) {

      System.out.println(SqlColumn.getComplete().toXml(""));
      System.out.println("\n\n\n");
      
      System.out.println("THE java.sql.Types integer codes");
      System.out.println("=====================================");
      System.out.println("ARRAY\t" + Types.ARRAY);
      System.out.println("BIGINT\t" + Types.BIGINT);
      System.out.println("BINARY\t" + Types.BINARY);
      System.out.println("BIT\t" + Types.BIT);
      System.out.println("BLOB\t" + Types.BLOB);
      System.out.println("BOOLEAN\t" + Types.BOOLEAN);
      System.out.println("CHAR\t" + Types.CHAR);
      System.out.println("CLOB\t" + Types.CLOB);
      System.out.println("DATALINK\t" + Types.DATALINK);
      System.out.println("DATE\t" + Types.DATE);
      System.out.println("DECIMAL\t" + Types.DECIMAL);
      System.out.println("DISTINCT\t" + Types.DISTINCT);
      System.out.println("DOUBLE\t" + Types.DOUBLE);
      System.out.println("FLOAT\t" + Types.FLOAT);
      System.out.println("INTEGER\t" + Types.INTEGER);
      System.out.println("JAVA_OBJECT\t" + Types.JAVA_OBJECT);
      System.out.println("LONGVARBINARY\t" + Types.LONGVARBINARY);
      System.out.println("LONGVARCHAR\t" + Types.LONGVARCHAR);
      System.out.println("NULL\t" + Types.NULL);
      System.out.println("NUMERIC\t" + Types.NUMERIC);
      System.out.println("OTHER\t" + Types.OTHER);
      System.out.println("REAL\t" + Types.REAL);
      System.out.println("REF\t" + Types.REF);
      System.out.println("SMALLINT\t" + Types.SMALLINT);
      System.out.println("STRUCT\t" + Types.STRUCT);
      System.out.println("TIME\t" + Types.TIME);
      System.out.println("TIMESTAMP\t" + Types.TIMESTAMP);
      System.out.println("TINYINT\t" + Types.TINYINT);
      System.out.println("VARBINARY\t" + Types.VARBINARY);
      System.out.println("VARCHAR\t" + Types.VARCHAR);
      System.out.println("SQLXML\t" + Types.SQLXML);
      System.out.println("=====================================");
   }

   private static boolean isSameString(String str1, String str2) {
      if (str1 == null) {
         if (str2 == null)
            return true;
         return false;
      }
      if (str2 == null)
         return false;
      return str1.equals(str2);
   }

   public boolean isSame(SqlColumn col) {
      if (!isSameString(getType(), col.getType()))
         return false;
      if (getPrecision() != col.getPrecision())
         return false;
      if (getScale() != col.getScale())
         return false;
      if (getNullable() != col.getNullable())
         return false;
      if (isSearchable() != col.isSearchable())
         return false;
      if (isSigned() != col.isSigned())
         return false;
      if (isReadOnly() != col.isReadOnly())
         return false;
      if (getSqlType() != col.getSqlType())
         return false;
      if (getColSize() != col.getColSize())
         return false;
      if (getRadix() != col.getRadix())
         return false;
      if (getCharLength() != col.getCharLength())
         return false;
      // if (getPos() != col.getPos())
      //    return false;
      if (getRemarks() != col.getRemarks())
         return false;
      if (getColDefault() != col.getColDefault())
         return false;
      if (!isSameString(getLabel(), col.getLabel()))
         return false;
      if (!isSameString(getTypeName(), col.getTypeName()))
         return false;
      if (isAutoInc() != col.isAutoInc())
         return false;
      if (isCaseSens() != col.isCaseSens())
         return false;
      if (isPrimaryKey() != col.isPrimaryKey())
         return false;
      if (!isSameString(getPkName(), col.getPkName()))
         return false;
      if (isFk() != col.isFk())
         return false;
      if (isFk()) {
         if (!isSameString(getFkCatalog(), col.getFkCatalog()))
            return false;
         if (!isSameString(getFkSchema(), col.getFkSchema()))
            return false;
         if (!isSameString(getFkTable(), col.getFkTable()))
            return false;
         if (!isSameString(getFkCol(), col.getFkCol()))
            return false;
         if (!isSameString(getFkSeq(), col.getFkSeq()))
            return false;
         if (!isSameString(getFkUpdRule(), col.getFkUpdRule()))
            return false;
         if (!isSameString(getFkDelRule(), col.getFkDelRule()))
            return false;
         if (!isSameString(getFkDef(), col.getFkDef()))
            return false;
      }
      return true;
   }

   private final ClientProperty nextChar(Random random, int numOfChars) {
      if (this.colSize < numOfChars)
         numOfChars = this.colSize;
      StringBuffer buf = new StringBuffer(numOfChars);
      for (int i=0; i < numOfChars; i++) {
         int val = 32 + random.nextInt(96);
         buf.append((char)val);
      }
      return new ClientProperty(this.colName, null, null, buf.toString());
   }
   
   private final ClientProperty nextNumber(Random random, int max) {
      int val = random.nextInt(max);
      return new ClientProperty(this.colName, null, null, "" + val);
   }
   
   private final ClientProperty nextDate(Random random) {
      int val = random.nextInt(87600);
      long time = System.currentTimeMillis() - (5L*365*24*3600*1000L);
      time += (60000L * val);
      Timestamp ts = new Timestamp(time);
      return new ClientProperty(this.colName, null, null, ts.toString());
   }
   
   public ClientProperty generateRandomObject() {
      if (sqlType == Types.ARRAY)
         return null;
      if (sqlType == Types.BIT)
         return null;
      if (sqlType == Types.DATALINK)
         return null;
      if (sqlType == Types.DISTINCT)
         return null;
      if (sqlType == Types.REF)
         return null;
      if (sqlType == Types.STRUCT)
         return null;
      if (sqlType == Types.JAVA_OBJECT)
         return null;
      if (sqlType == Types.NULL)
         return null;
      if (sqlType == Types.OTHER)
         return null;

      Random random = new Random(System.currentTimeMillis());
      ClientProperty ret = new ClientProperty(this.colName, null, null);
      if (sqlType == Types.BOOLEAN) {
         ret.setValue("" + random.nextBoolean());
         return ret;
      }
      if (sqlType == Types.CHAR)
         return nextChar(random, 1);
      if (sqlType == Types.BINARY)
         return nextChar(random, 24);
      if (sqlType == Types.BLOB)
         return nextChar(random, 24);
      if (sqlType == Types.CLOB)
         return nextChar(random, 24);
      if (sqlType == Types.LONGVARBINARY)
         return nextChar(random, 24);
      if (sqlType == Types.LONGVARCHAR)
         return nextChar(random, 24);
      if (sqlType == Types.VARBINARY)
         return nextChar(random, 24);
      if (sqlType == Types.VARCHAR)
         return nextChar(random, 24);

      if (sqlType == Types.DATE)
         return nextDate(random);
      if (sqlType == Types.TIME)
         return nextDate(random);
      if (sqlType == Types.TIMESTAMP)
         return nextDate(random);

      if (sqlType == Types.BIGINT)
         return nextNumber(random, 60000);
      if (sqlType == Types.DECIMAL)
         return nextNumber(random, 60000);
      if (sqlType == Types.DOUBLE)
         return nextNumber(random, 60000);
      if (sqlType == Types.FLOAT)
         return nextNumber(random, 60000);
      if (sqlType == Types.INTEGER)
         return nextNumber(random, 60000);
      if (sqlType == Types.NUMERIC)
         return nextNumber(random, 60000);
      if (sqlType == Types.REAL)
         return nextNumber(random, 60000);
      if (sqlType == Types.SMALLINT)
         return nextNumber(random, 256);
      if (sqlType == Types.TINYINT)
         return nextNumber(random, 256);
      if (sqlType == Types.SQLXML)
         return nextChar(random, 24);

      return null;
   }
   

   public SqlColumn doClone() {
      SqlColumn other = new SqlColumn(null);
      other.setCatalog(getCatalog());
      other.setPrecision(getPrecision());
      other.setSearchable(isSearchable());
      other.setReadOnly(isReadOnly());
      other.setScale(getScale());
      other.setSchema(getSchema());
      other.setFkCatalog(getFkCatalog());
      other.setFkCol(getFkCol());
      other.setFkDef(getFkDef());
      other.setFkDelRule(getFkDelRule());
      other.setFkSchema(getFkSchema());
      other.setFkSeq(getFkSeq());
      other.setFkTable(getFkTable());
      other.setFkUpdRule(getFkUpdRule());
      other.setSigned(isSigned());
      other.setTable(getTable());
      other.setType(getType());
      other.setColName(getColName());
      other.setPrimaryKey(isPrimaryKey());
      other.setCharLength(getCharLength());
      other.setColSize(getColSize());
      other.setColDefault(getColDefault());
      other.setPos(getPos());
      other.setRadix(getRadix());
      other.setRemarks(getRemarks());
      other.setPkName(getPkName());
      other.setAutoInc(isAutoInc());
      other.setCaseSens(isCaseSens());
      other.setLabel(getLabel());
      other.setTypeName(getTypeName());
      other.setSqlType(getSqlType());
      other.setNullable(getNullable());
      return other;
   }

   
}
