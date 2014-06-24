/*------------------------------------------------------------------------------
Name:      PropBoolean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;



/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public final class PropBoolean extends PropEntry implements java.io.Serializable, Cloneable
{
   private static final long serialVersionUID = 5664321757271875143L;
   private boolean value;

   /**
    * Constructor for the default value
    */
   public PropBoolean(boolean value) {
      super(null);
      this.value = value;
   }

   /**
    * Constructor for the default value
    */
   public PropBoolean(String propName, boolean value) {
      super(propName);
      this.value = value;
   }

   /**
    * @return "boolean"
    */
   public final String getTypeString() {
      return "boolean";
   }

   /**
    * @return The value in String form
    */
   public final String getValueString() {
      return ""+this.value;
   }

   public void setValue(boolean value) {
      this.value = value;
      super.creationOrigin = CREATED_BY_SETTER;
   }

   /**
    * @param The new value as String type, will be converted to native type
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(String value, int creationOrigin) {
      if (value == null) return;
      setValue(((value != null) && value.equalsIgnoreCase("true")), creationOrigin);
   }

   /**
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(boolean value, int creationOrigin) {
      this.value = value;
      super.creationOrigin = creationOrigin;
   }

   public boolean getValue() {
      return this.value;
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      return super.clone();
   }

   /** java org.xmlBlaster.util.property.PropBoolean */
   public static void main(String[] args) {
      PropBoolean forceDestroy = new PropBoolean("forceDestroy", true);
      System.out.println(forceDestroy.toXml());
   }
}
