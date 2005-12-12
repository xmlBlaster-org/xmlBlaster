/*------------------------------------------------------------------------------
Name:      TestResultSetToXmlConverter.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher;

import java.util.EventObject;
import java.util.Map;
import java.util.HashMap;

/**
 * Transports the change event information to the listeners. 
 * @author Marcel Ruff
 */
public final class ChangeEvent extends EventObject {
   private static final long serialVersionUID = -2797657105273724009L;
   private String groupColName;
   private String groupColValue;
   private String xml;
   private Map attrMap;

   /**
    * @param source Transported data
    */
   public ChangeEvent(Object source) {
      super(source);
   }

  /**
   * Create the instance. 
   * @param groupColName for example 'ICAO_ID'
   * @param groupColValue for example 'EDDI'
   * @param xml The result xml from a query
   * @param command The command which probably caused the event, like <tt>INSERT</tt>
   */
   public ChangeEvent(String groupColName, String groupColValue, String xml, String command, Map map) {
      super("ChangeEvent");
      if (map != null)
         this.attrMap = map;
      this.groupColName = groupColName;
      this.groupColValue = (groupColValue==null) ? "${"+groupColName+"}" : groupColValue;
      this.xml = xml;
      setCommand(command);
   }

   public void setCommand(String command) {
      if (command != null) {
         if (this.attrMap == null)
            this.attrMap = new HashMap();
         this.attrMap.put("_command", command);
      }
      else {
         if (this.attrMap != null)
            this.attrMap.remove("_command");
      }
   }
   
   /**
    * The DB columns name. 
    * Each change of the value triggers a new event
    * @return Returns the groupColName.
    */
   public String getGroupColName() {
      return this.groupColName;
   }

   /**
    * The current DB column name's value.  
    * Each change of this triggers a new event
    * @return Never null, defautls to ${<groupColName>}
    */
   public String getGroupColValue() {
      return this.groupColValue;
   }

   /**
    * The command token which caused the event. 
    * <p>
    * For example <tt>CREATE</tt> or <tt>UPDATE</tt> 
    * @return Returns the command.
    */
   public String getCommand() {
      return (this.attrMap != null) ? (String)this.attrMap.get("_command") : (String)null;
   }
   
   /**
    * Access properties. 
    * @return Map with attributes, is never null
    */
   public Map getAttributeMap() {
      if (this.attrMap == null) this.attrMap = new HashMap();
      return this.attrMap;
   }

   /**
   * The data to transfer. 
   * @return Returns the xml result.
   */
   public String getXml() {
      return this.xml;
   }

   /**
    * Sets the data to transfer.
    * @param xml
    */
   public void setXml(String xml) {
      this.xml = xml;
   }
   
  /**
   * For informative logging. 
   * @return dump 
   */
   public String toString() {
      StringBuffer buf = new StringBuffer(1024);
      if (this.groupColName != null) buf.append(this.groupColName).append("=");
      if (this.groupColValue != null) buf.append(this.groupColValue).append(":");
      if (this.xml != null) buf.append("\n").append(this.xml);
      return buf.toString();
   }
}
