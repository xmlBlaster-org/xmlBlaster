/*------------------------------------------------------------------------------
Name:      PropInt.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;

import org.xmlBlaster.util.def.Constants;


/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public final class PropInt extends PropEntry implements java.io.Serializable, Cloneable
{
   private int value;

   /**
    * Constructor for the default value
    */
   public PropInt(int value) {
      super(null);
      this.value = value;
   }

   /**
    * Constructor for the default value
    */
   public PropInt(String propName, int value) {
      super(propName);
      this.value = value;
   }

   /**
    * @return "int"
    */
   public final String getTypeString() {
      return "int";
   }

   /**
    * @return The value in String form
    */
   public final String getValueString() {
      return ""+this.value;
   }

   public void setValue(int value) {
      this.value = value;
      super.creationOrigin = CREATED_BY_SETTER;
   }

   /**
    * @param The new value as String type, will be converted to native type
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(String value, int creationOrigin) {
      if (value == null) return;
      setValue(Integer.parseInt(value), creationOrigin);
   }

   /**
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(int value, int creationOrigin) {
      this.value = value;
      super.creationOrigin = creationOrigin;
   }

   public int getValue() {
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

   /** java org.xmlBlaster.util.property.PropInt */
   public static void main(String[] args) {
      PropInt lifeTime = new PropInt("lifeTime", 123456);
      System.out.println(lifeTime.toXml());
   }
}
