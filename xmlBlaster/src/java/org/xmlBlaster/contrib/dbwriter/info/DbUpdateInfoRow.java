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

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.ClientProperty;

public class DbUpdateInfoRow {

   public final static String ROW_TAG = "row";
   public final static String COL_TAG = "col";
   public final static String ATTR_TAG = "attr";
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

   public DbUpdateInfoRow(int position) {
      this.attributes = new HashMap();
      this.columns = new HashMap();
      this.attributeKeys = new ArrayList();
      this.columnKeys = new ArrayList();
      this.position = position;
   }


   public String[] getAttributeNames() {
      return (String[])this.attributeKeys.toArray(new String[this.attributeKeys.size()]);
   }


   public String[] getColumnNames() {
      return (String[])this.columnKeys.toArray(new String[this.columnKeys.size()]);
   }
   
   
   /**
    * Stores the client property as a new value. Note that it is not allowed to store an attribute with the same name
    * multiple times.
    * @throws IllegalArgumentException if the entry already existed or if the value was null.
    * @param value the value to store as an attribute.
    */
   private final static void storeProp(ClientProperty value, Map map, List list) {
      if (value == null)
         throw new IllegalArgumentException("RecordRow.storeProp: the value is null, which is not allowed");
      String name = value.getName();
      if (name == null)
         throw new IllegalArgumentException("RecordRow.storeProp: the name of the value is null, which is not allowed");
      if (map.containsKey(name))
         throw new IllegalArgumentException("RecordRow.storeProp: the value '" + name + "' exists already, this is not allowed");
      map.put(name, value);
      list.add(name);
   }
   
   public ClientProperty getAttribute(String key) {
      return (ClientProperty)this.attributes.get(key);
   }
   
   
   /**
    * Stores the client property as a new value. Note that it is not allowed to store an attribute with the same name
    * multiple times.
    * @throws IllegalArgumentException if the entry already existed or if the value was null.
    * @param value the value to store as an attribute.
    */
   public void setAttribute(ClientProperty value) {
      storeProp(value, this.attributes, this.attributeKeys);
   }
   
   
   public ClientProperty getColumn(String key) {
      return (ClientProperty)this.columns.get(key);
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
         sb.append(prop.toXml(extraOffset + "  ", ATTR_TAG));
      }
      sb.append(offset).append("</").append(ROW_TAG).append(">");
      return sb.toString();
   }
   
}
