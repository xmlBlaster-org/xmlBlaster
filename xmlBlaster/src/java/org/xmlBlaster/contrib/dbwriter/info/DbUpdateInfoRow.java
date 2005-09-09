/*------------------------------------------------------------------------------
Name:      RecordRow.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.dbwriter.info;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwriter.DbUpdateParser;
import org.xmlBlaster.contrib.dbwriter.DbWriter;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class DbUpdateInfoRow {

   public final static String ROW_TAG = "row";
   public final static String COL_TAG = "col";
   public final static String NUM_ATTR = "num";
   
   /**
    *  Contains the attributes of this row. The key is a string describing the name and the content is a ClientProperty
    */
   private Map attributes;

   /**
    *  Contains the colums of this row. The key is a String which stands for the column number and the content is a ClientProperty
    */
   private Map columns;

   private List attributeKeys;
   private List columnKeys;
   
   private int position;
   
   private boolean caseSensitive; 

   public DbUpdateInfoRow(I_Info info, int position) {
      this.attributes = new HashMap();
      this.columns = new HashMap();
      this.attributeKeys = new ArrayList();
      this.columnKeys = new ArrayList();
      this.position = position;
      this.caseSensitive = info.getBoolean(DbWriter.CASE_SENSITIVE_KEY, false);
   }


   public String[] getAttributeNames() {
      return (String[])this.attributeKeys.toArray(new String[this.attributeKeys.size()]);
   }


   public String[] getColumnNames() {
      return (String[])this.columnKeys.toArray(new String[this.columnKeys.size()]);
   }
   
   
   /**
    * Stores the client property as a new value. If the attribute is found, then its value is overwritten.
    * @param value the value to store as an attribute.
    */
   final static void storeProp(ClientProperty value, Map map, List list) {
      if (value == null)
         throw new IllegalArgumentException("RecordRow.storeProp: the value is null, which is not allowed");
      String name = value.getName();
      if (name == null)
         throw new IllegalArgumentException("RecordRow.storeProp: the name of the value is null, which is not allowed");
      if (map.containsKey(name)) {
         map.put(name, value);
      }
      else {
         map.put(name, value);
         list.add(name);
      }
   }
   
   /**
    * Returns the requested attribute. If 'caseSensitive' has been set, the characters of the key are compared
    * case sensitively. If it is set to false, then it first searches for the case sensitive match, if nothing
    * is found it looks for the lowercase of the key, and finally if still no match it looks for the uppercase
    * alternative. If none of these is found, null is returned.
    *  
    * @param key the key of the attribute
    * @return the ClientProperty object associated with the key, or if none found, null is returned.
    */
   public ClientProperty getAttribute(String key) {
      ClientProperty prop = (ClientProperty)this.attributes.get(key);
      if (!this.caseSensitive && prop == null) {
         prop = (ClientProperty)this.attributes.get(key.toLowerCase());
         if (prop == null)
            prop = (ClientProperty)this.attributes.get(key.toUpperCase());
      }
      return prop;
   }
   
   
   /**
    * Stores the client property as a new value. It it exists already it is overwritten.
    * @param value the value to store as an attribute.
    */
   public void setAttribute(ClientProperty value) {
      storeProp(value, this.attributes, this.attributeKeys);
   }
   
   
   public ClientProperty getColumn(String key) {
      ClientProperty prop = (ClientProperty)this.columns.get(key);
      if (!this.caseSensitive && prop == null) {
         prop = (ClientProperty)this.columns.get(key.toLowerCase());
         if (prop == null)
            prop = (ClientProperty)this.columns.get(key.toUpperCase());
      }
      return prop;
   }
   
   /**
    * Stores the client property as a new value. Note that it is not allowed to store an attribute with the same name
    * multiple times.
    * @throws IllegalArgumentException if the entry already existed or if the value was null.
    * @param value the value to store as an attribute.
    */
   public void setColumn(ClientProperty value) {
      storeProp(value, this.columns, this.columnKeys);
   }
   
   
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<").append(ROW_TAG);
      sb.append(" ").append(NUM_ATTR).append("='").append(this.position).append("'>");

      Iterator iter = this.columnKeys.iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         ClientProperty prop = (ClientProperty)this.columns.get(key);
         sb.append(prop.toXml(extraOffset + "  ", COL_TAG));
      }
      iter = this.attributeKeys.iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         ClientProperty prop = (ClientProperty)this.attributes.get(key);
         sb.append(prop.toXml(extraOffset + "  ", DbUpdateParser.ATTR_TAG));
      }
      sb.append(offset).append("</").append(ROW_TAG).append(">");
      return sb.toString();
   }


   public boolean isCaseSensitive() {
      return caseSensitive;
   }


   public void setCaseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
   }
   
}
