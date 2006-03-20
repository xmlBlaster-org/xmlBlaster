/*------------------------------------------------------------------------------
Name:      PropLong.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;


/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public final class PropLong extends PropEntry implements java.io.Serializable, Cloneable
{
   private long valueDefault; // Remember default setting for usage output etc.
   private long value;

   /**
    * Constructor for the default value
    */
   public PropLong(long value) {
      this(null, value);
   }

   /*
    * Constructor for the default value
    * @param propName The environment property name
    */
   public PropLong(String propName, long value) {
      super(propName);
      this.valueDefault = value;
      this.value = value;
   }

   /**
    * @return "long"
    */
   public final String getTypeString() {
      return "long";
   }

   /**
    * @return The value in String form
    */
   public final String getValueString() {
      return ""+this.value;
   }

   /**
    * Overwrites any default or environment settings. 
    * Used by clients to set hardcoded values or by SAX parser if enforced by XML
    */
   public void setValue(long value) {
      this.value = value;
      super.creationOrigin = CREATED_BY_SETTER;
   }

   /**
    * @param The new value as String type, will be converted to native type
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(String value, int creationOrigin) {
      if (value == null) return;
      setValue(Long.parseLong(value), creationOrigin);
   }

   /**
    * @param the new value to use
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(long value, int creationOrigin) {
      if (creationOrigin >= super.creationOrigin) {
         this.value = value;
         super.creationOrigin = creationOrigin;
      }
      else
         System.out.println("Old value=" + this.value + " not overwritten with " + value + " as old origin=" + super.creationOrigin + " and new origin=" + creationOrigin + " is weaker");
   }

   public long getValue() {
      return this.value;
   }

   /**
    * Overwrite the default value given to the constructor. 
    */
   public void setDefaultValue(long value) {
      this.valueDefault = value;
      if (CREATED_BY_DEFAULT == super.creationOrigin) {
         this.value = value; // overwrite the default setting
      }
   }

   public long getDefaultValue() {
      return this.valueDefault;
   }

  /**
    * Returns a shallow clone, you can change safely all basic or immutable types
    * like boolean, String, int.
    */
   public Object clone() {
      return super.clone();
   }

   /** java org.xmlBlaster.util.property.PropLong */
   public static void main(String[] args) {
      PropLong maxEntries = new PropLong(123456L);
      System.out.println(maxEntries.toXml());

      org.xmlBlaster.util.Global glob = new org.xmlBlaster.util.Global(args);
      String nodeId = null;
      String prefix = null;
      String className = null;
      String instanceName = null;
      String propName = "maxEntries";

      try {
         glob.getProperty().set("maxEntries", "444444");
         glob.getProperty().set("persistence/msgUnitStore/maxEntries", "666666");
         glob.getProperty().set("topic/hello/persistence/msgUnitStore/maxEntries", "777777"); // this should be ignored in current version
         glob.getProperty().set("/node/heron/topic/hello/persistence/msgUnitStore/maxEntries", "999999");
         //System.out.println(glob.getProperty().toXml());


         System.out.println("PropName=" + propName + ", used env name=" +
             maxEntries.setFromEnv(glob, nodeId, prefix, className, instanceName, propName) +
             ": " + maxEntries.toXml(""));

         nodeId = "heron";
         prefix = "topic/hello";
         className = "persistence";
         instanceName = "msgUnitStore";
         System.out.println("PropName=" + propName + ", used env name=" +
             maxEntries.setFromEnv(glob, nodeId, prefix, className, instanceName, propName) +
             ": " + maxEntries.toXml(""));
      }
      catch (Exception e) {
         System.out.println("ERROR: " + e.toString());
      }
   }
}
