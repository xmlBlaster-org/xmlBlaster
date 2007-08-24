/*------------------------------------------------------------------------------
Name:      PropEntry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.property;

import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.context.ContextNode;

/**
 * Base class for the various property data type implementations. 
 * @author xmlBlaster@marcelruff.info
 */
public abstract class PropEntry implements java.io.Serializable, Cloneable
{
   public static final String SEP = ContextNode.SEP; // "/";

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

   public final boolean isDefault() {
      return this.creationOrigin == CREATED_BY_DEFAULT;
   }

   public final boolean isSet() {
      return this.creationOrigin == CREATED_BY_SETTER;
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

   public final String setFromEnv(org.xmlBlaster.util.Global glob, ContextNode contextNode, String propName) {
      String className = null;
      String instanceName = null;
      if (contextNode != null) {
         className = contextNode.getClassName();
         instanceName = contextNode.getInstanceName();
      }
      return setFromEnv(glob, glob.getStrippedId(), null, className, instanceName, propName);
   }

   /**
    * An environment is checked for a property. 
    * <pre>
    * "/node/frodo/client/joe/queue/history/maxEntries=10"
    *
    * nodeId='frodo'
    * context='client/joe' or '/topic/HelloWorld'
    * className='queue'
    * instanceName='history'
    * propName='maxEntries'
    * </pre>
    *
    * Old style:
    * <pre>
    * nodeId="heron" context="history.queue." propName="maxEntries" -> "history.queue.maxEntries[heron]"
    * </pre>
    *
    * Currently this precedence is supported:
    * <pre>
    *  maxEntries                                         (weakest, not recommended)
    *
    *  /node/heron/plugin/socket/port                     (recommended)
    *
    *  queue/maxEntries                                   (supported until all is ported)
    *
    *  queue/callback/maxEntries                          (recommended)
    *
    *  /node/heron/queue/callback/maxEntries              (recommended in cluster)
    *
    *  /node/heron/topic/hello/queue/callback/maxEntries  (strongest)
    * </pre>
    *
    * <!-- 
    *  maxEntries                                         (weakest, not recommended)
    *  /node/heron/maxEntries                             (not recommended)
    *  queue.maxEntries                                   (deprecated)
    *  queue/maxEntries                                   (not recommended)
    *  callback.queue.maxEntries                          (deprecated)
    *  queue/callback/maxEntries                          (recommended)
    *  /node/heron/queue/callback/maxEntries              (recommended)
    *  callback.queue.maxEntries[heron]                   (deprecated)
    *  /node/heron/queue/callback/maxEntries
    *  /node/heron/topic/hello/queue/callback/maxEntries  (strongest)
    * -->
    * @return the matching key
    */
   public final String setFromEnv(org.xmlBlaster.util.Global glob,
                             String nodeId, String context, String className,
                             String instanceName, String propName) {
      return setFromEnv(glob, nodeId, context, className, instanceName, propName, true);
   }

   /**
    * @param simpleLookup If false the given propName is not checked directly but
    *                     only in its context (e.g. to avoid naming conflicts for simple
    *                     properties like 'version'
    * @return The found environment key which we used to set the property
    *         or "" if property was set by a setter (which has precedence)
    */
   public final String setFromEnv(org.xmlBlaster.util.Global glob,
                             String nodeId, String context, String className,
                             String instanceName, String propName, boolean simpleLookup) {
      if (propName == null) {
         propName = this.propName;
         if (propName == null)
            throw new IllegalArgumentException("setFromEnv(): Internal problem: Missing property name");
      }

      if (creationOrigin == CREATED_BY_SETTER)
         return "";
      
      org.xmlBlaster.util.property.Property props = glob.getProperty();

      StringBuffer name = new StringBuffer(100);
      String usedName = name.toString();

      if (simpleLookup) {
         // check propName="maxEntries" or propName="plugin/socket/port" variant
         name.append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      // check "/node/heron/maxEntries" or "/node/heron/plugin/socket/port" variant
      if (nodeId != null) {
         name.setLength(0);
         name.append("/node/").append(nodeId);
         name.append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            //System.out.println("EXISTS prop=" + name.toString() + " value=" + props.get(name.toString(), "NULL"));
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      /*
      // check OLD STYLE "queue.maxEntries" (deprecated)
      if (nodeId != null) {
         name.setLength(0);
         name.append(className).append(".").append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), PropEntry.CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }
      */

      /* This makes sense to set generally maxEntries for all queues, otherwise we
         need to specify it for every queue instance like 'callback' 'history' 'connection'
         e.g. queue/callback/defaultPlugin=RAM,1.0
         With the new markup 'plugin/callback/maxEntries' this will be not supported anymore
      */
      // check "queue/maxEntries" variant
      if (className != null) {
         name.setLength(0);
         name.append(className).append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      /*
      // check OLD STYLE "history.queue.maxEntries" (deprecated)
      if (nodeId != null) {
         name.setLength(0);
         name.append(instanceName).append(".").append(className).append(".").append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), PropEntry.CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }
      */

      // check "queue/history/maxEntries" variant
      if (className != null && instanceName != null) {
         name.setLength(0);
         name.append(className).append(SEP).append(instanceName).append(SEP).append(propName);
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }

      /*
      // check OLD STYLE "history.queue.maxEntries[heron]" (deprecated)
      if (nodeId != null) {
         name.setLength(0);
         name.append(instanceName).append(".").append(className).append(".").append(propName).append("[").append(nodeId).append("]");
         //System.out.println("Checking prop=" + name.toString());
         if (props.propertyExists(name.toString())) {
            setValue(props.get(name.toString(), getValueString()), PropEntry.CREATED_BY_PROPFILE);
            usedName = name.toString();
         }
      }
      */

      // check "/node/frodo/queue/history/maxEntries" variant
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

      // check "/node/frodo/client/joe/queue/history/maxEntries" variant
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

   public String toString() {
      String tmp = getClass().getName();
      if (this.propName != null && this.propName.length() > 0) {
         tmp = this.propName;
      }
      String value = getValueString();
      if (value != null) {
         if (value.length() < 1) {
            tmp += "='" + value + "'";
         }
         else {
            tmp += "=" + value;
         }
      }
      return tmp;
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
    * Returns a shallow clone. 
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
