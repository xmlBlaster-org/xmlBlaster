/*------------------------------------------------------------------------------
Name:      PropEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;

/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public abstract class PropEntry implements java.io.Serializable, Cloneable
{
   public static final int CREATED_BY_DEFAULT = 0;
   public static final int CREATED_BY_JVMENV = 1;
   public static final int CREATED_BY_PROPFILE = 2;
   public static final int CREATED_BY_CMDLINE = 3;
   public static final int CREATED_BY_SETTER = 4;
   protected int creationOrigin;     // defaults to 0
   protected final String propName;

   /**
    * Constructor for the default value
    */
   public PropEntry(String propName) {
      this.propName = propName;
   }

   /**
    * Is unmanipulated default value?
    */
   public final boolean isModified() {
      return this.creationOrigin != CREATED_BY_DEFAULT;
   }

   public void setCreationOrigin(int creationOrigin) {
      this.creationOrigin = creationOrigin;
   }

   /** The literal XML string of the QoS */
   public abstract String toXml();

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the query as a XML ASCII string
    */
   public abstract String toXml(String extraOffset);

   /**
    * Returns a shallow clone, you can change savely all basic or immutable types
    * like boolean, String, int.
    * Currently RouteInfo is not cloned (so don't change it)
    */
   public Object clone() {
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}
