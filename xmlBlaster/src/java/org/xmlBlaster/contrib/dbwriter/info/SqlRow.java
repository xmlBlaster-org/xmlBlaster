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

public class SqlRow {

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

   /** If this is set, then no columns must be filled and this is used to print it out as xml */
   private String colsRawContent;
   
   public SqlRow(I_Info info, int position) {
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
    * It copies (stores) all entries found in the map into the attributes. As values only String and ClientProperty
    * objects are allowed. If another type is found, an IllegalArgumentException is thrown. If null is passed, 
    * nothing is done.
    * 
    * @param map
    */
   final static void addProps(Map map, Map destinationMap, List destinationList) {
      if (map == null || map.size() < 1)
         return;
      Iterator iter = map.keySet().iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         if (key == null)
            continue;
         Object val = map.get(key);
         if (val == null)
            continue;
         if (val instanceof String) {
            ClientProperty prop = new ClientProperty((String)key, null, null, (String)val);
            storeProp(prop, destinationMap, destinationList);
         }
         else if (val instanceof ClientProperty) {
            storeProp((ClientProperty)val, destinationMap, destinationList);
         }
         else {
            throw new IllegalArgumentException("SqlDescription.addAttributes can only be done on String or ClientProperty, but '" + key + "' has a value of type '" + val.getClass().getName() + "'");
         }
      }
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
   
   /**
    * Stores the String as a new value. The passed String is directly transformed into a ClientProperty object. 
    * @param value the value to store as an attribute.
    */
   public void setAttribute(String key, String value) {
      ClientProperty prop = new ClientProperty(key, null, null, value);
      SqlRow.storeProp(prop, this.attributes, this.attributeKeys);
   }
   
   /**
    * It copies (stores) all entries found in the map into the attributes. As values only String and ClientProperty
    * objects are allowed. If another type is found, an IllegalArgumentException is thrown. If null is passed, 
    * nothing is done.
    * 
    * @param map
    */
   public void addAttributes(Map map) {
      addProps(map, this.attributes, this.attributeKeys);
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
    * @throws IllegalArgumentException if the entry already existed, if the value is null or if the raw columns have already been set.
    * @param value the value to store as an attribute.
    */
   public void setColumn(ClientProperty value) {
      if (this.colsRawContent != null)
         throw new IllegalStateException("SqlRow.setColumn can not be invoked since the raw value '" + this.colsRawContent + "' has already been set");
         storeProp(value, this.columns, this.columnKeys);
   }
   
   public String toXml(String extraOffset) {
      return toXml(extraOffset, true);
   }
   
   public String toXml(String extraOffset, boolean withRowTag) {
      StringBuffer sb = new StringBuffer(256);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      if (withRowTag) {
         sb.append(offset).append("<").append(ROW_TAG);
         sb.append(" ").append(NUM_ATTR).append("='").append(this.position).append("'>");
      }

      if (this.colsRawContent != null) {
        sb.append("  ").append(this.colsRawContent); 
      }
      else {
         Iterator iter = this.columnKeys.iterator();
         while (iter.hasNext()) {
            Object key = iter.next();
            ClientProperty prop = (ClientProperty)this.columns.get(key);
            sb.append(prop.toXml(extraOffset + "  ", COL_TAG));
         }
      }
      Iterator iter = this.attributeKeys.iterator();
      while (iter.hasNext()) {
         Object key = iter.next();
         ClientProperty prop = (ClientProperty)this.attributes.get(key);
         sb.append(prop.toXml(extraOffset + "  ", DbUpdateParser.ATTR_TAG));
      }
      if (withRowTag)
         sb.append(offset).append("</").append(ROW_TAG).append(">");
      return sb.toString();
   }


   public boolean isCaseSensitive() {
      return caseSensitive;
   }


   public void setCaseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
   }


   public String getColsRawContent() {
      return this.colsRawContent;
   }


   /**
    * @param colsRawContent
    * @throws IllegalStateException if at least one column has already been set.
    */
   public void setColsRawContent(String colsRawContent) {
      if (this.columns.size() > 0)
         throw new IllegalStateException("SqlRow.setColsRawContent can not be invoked since there are already '" + this.columns.size() + "' columns defined");
      this.colsRawContent = colsRawContent;
   }
   
}
