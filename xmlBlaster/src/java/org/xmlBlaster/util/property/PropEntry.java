/*------------------------------------------------------------------------------
Name:      PropEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;

import org.xmlBlaster.util.enum.Constants;

/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public abstract class PropEntry implements java.io.Serializable, Cloneable
{
   public static final String SEP = "/";

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

   /**
    * @return e.g. "long" or "string"
    */
   public abstract String getTypeString();

   /**
    * @return The value in String form
    */
   public abstract String getValueString();

   /**
    * @param The new value as String type, will be converted to native type
    * @param creationOrigin e.g. PropEntry.CREATED_BY_JVMENV
    */
   public abstract void setValue(String value, int creationOrigin);

   /**
    * Example how an environment is checked for a property:
    * <pre>
    * "/node/frodo/client/joe/queue/history/maxEntries=10"
    *
    * nodeId='frodo'
    * context='client/joe' or '/topic/HelloWorld'
    * className='queue'
    * instanceName='history'
    * propName='maxMsg'
    * </pre>
    *
    * Old style:
    * <pre>
    * nodeId="heron" context="history.queue." propName="maxMsg" -> "history.queue.maxMsg[heron]"
    * </pre>
    *
    * Currently this precedence is supported:
    * <pre>
    *  maxMsg                                                   (weakest, not recommended)
    *  msgUnitStore.persistence.maxMsg                          (deprecated)
    *  persistence/msgUnitStore/maxMsg                          (recommended)
    *  msgUnitStore.persistence.maxMsg[heron]                   (deprecated)
    *  /node/heron/persistence/msgUnitStore/maxMsg
    *  /node/heron/topic/hello/persistence/msgUnitStore/maxMsg  (strongest)
    * </pre>
    */
   public String setFromEnv(org.xmlBlaster.util.Global glob,
                             String nodeId, String context, String className,
                             String instanceName, String propName) {
      if (propName == null) {
         propName = this.propName;
         if (propName == null)
            throw new IllegalArgumentException("setFromEnv(): Internal problem: Missing property name");
      }
      
      org.jutils.init.Property props = glob.getProperty();

      StringBuffer name = new StringBuffer(100);
      String usedName = name.toString();

      // check "maxMsg" variant
      name.append(propName);
      //System.out.println("Checking prop=" + name.toString());
      if (props.propertyExists(name.toString())) {
         setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
         usedName = name.toString();
      }

      // check OLD STYLE "history.queue.maxMsg" (deprecated)
      if (nodeId != null) {
         name.setLength(0);
         name.append(instanceName).append(".").append(className).append(".").append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), PropEntry.CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      // check "queue/history/maxMsg" variant
      if (className != null && instanceName != null) {
         name.setLength(0);
         name.append(className).append(SEP).append(instanceName).append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      // check OLD STYLE "history.queue.maxMsg[heron]" (deprecated)
      if (nodeId != null) {
         name.setLength(0);
         name.append(instanceName).append(".").append(className).append(".").append(propName).append("[").append(nodeId).append("]");
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), PropEntry.CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      // check "/node/frodo/queue/history/maxMsg" variant
      if (nodeId != null && className != null && instanceName != null) {
         name.setLength(0);
         name.append("/node/").append(nodeId);
         name.append(SEP).append(className).append(SEP).append(instanceName);
         name.append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      // check "/node/frodo/client/joe/queue/history/maxMsg" variant
      if (nodeId != null && className != null && instanceName != null && context != null) {
         name.setLength(0);
         name.append("/node/").append(nodeId);
         name.append(SEP).append(context);
         name.append(SEP).append(className).append(SEP).append(instanceName);
         name.append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            //System.out.println("EXISTS prop=" + name.toString() + " value=" + props.get(name.toString(), "NULL"));
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      return usedName;
   }

   /** The literal XML string of the QoS */
   public String toXml() {
      return toXml("");
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * <br>
    * @param extraOffset indenting of tags for nice output
    * @return internal state of the property as a XML ASCII string
    */
   public String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(100);
      if (extraOffset == null) extraOffset = "";
      String offset = Constants.OFFSET + extraOffset;

      sb.append(offset).append("<property");
      if (this.propName != null) {
         sb.append(" key='").append(this.propName).append("'");
      }
      sb.append(" type='").append(getTypeString()).append("'");
      if (isModified()) {
         sb.append(" modified='").append(true).append("'");
      }
      sb.append(">");
      String value = getValueString();
      if (value != null && value.indexOf("<") > -1) {
         sb.append("<![CDATA[").append(value).append("]]>");
      }
      else {
         sb.append(value);
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
      try {
         return super.clone();
      }
      catch (CloneNotSupportedException e) {
         return null;
      }
   }
}
