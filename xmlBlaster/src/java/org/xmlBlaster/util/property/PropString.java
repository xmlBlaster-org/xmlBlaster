/*------------------------------------------------------------------------------
Name:      PropString.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;

import org.xmlBlaster.util.enum.Constants;


/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public final class PropString extends PropEntry implements java.io.Serializable, Cloneable
{
   private String value;

   /**
    * Constructor for the default value
    */
   public PropString(String value) {
      super(null);
      this.value = value;
   }

   /**
    * Constructor for the default value
    */
   public PropString(String propName, String value) {
      super(propName);
      this.value = value;
   }

   public void setValue(String value) {
      this.value = value;
      super.creationOrigin = CREATED_BY_SETTER;
   }

   /**
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public void setValue(String value, int creationOrigin) {
      this.value = value;
      super.creationOrigin = creationOrigin;
   }

   public String getValue() {
      return this.value;
   }

   /** The literal XML string of the QoS */
   public String toXml() {
      return toXml("");
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(100);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<property");
      if (this.propName != null) {
         sb.append(" key='").append(this.propName).append("'");
      }
      sb.append(" type='string'>");
      if (isModified()) {
         sb.append(" modified='").append(true).append("'");
      }
      if (this.value != null && this.value.indexOf("<") > -1) {
         sb.append("<![CDATA[").append(this.value).append("]]>");
      }
      else {
         sb.append(this.value);
      }
      sb.append("</property>");
      return sb.toString();
   }

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      return super.clone();
   }

   /** java org.xmlBlaster.util.property.PropString */
   public static void main(String[] args) {
      PropString dummy = new PropString("dummy", "Hi");
      System.out.println(dummy.toXml());
   }
}
