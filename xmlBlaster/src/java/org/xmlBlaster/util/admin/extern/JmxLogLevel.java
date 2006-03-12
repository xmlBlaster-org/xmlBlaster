/*------------------------------------------------------------------------------
Name:      JmxLogLevel.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin.extern;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import java.lang.reflect.Constructor;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import javax.management.*;

/**
 * Definition of a dynamic MBean which exports the logging properties. 
 *
 * The "JmxLogLevel" dynamic MBean exposes management
 * attributes and operations, at runtime,  by implementing the  
 * "javax.management.DynamicMBean" interface.
 *
 * This MBean exposes for management all Logger values.
 *      - the read/write attribute,
 *      - the "reset()" operation.
 * It does so by putting this information in an MBeanInfo object that
 * is returned by the getMBeanInfo() method of the DynamicMBean interface.
 *
 * It implements the access to its attributes through the getAttribute(),
 * getAttributes(), setAttribute(), and setAttributes() methods of the
 * DynamicMBean interface.
 *
 * It implements the invocation of its reset() operation through the
 * invoke() method of the DynamicMBean interface.
 * 
 * Note that as "JmxLogLevel" explicitly defines one constructor,
 * this constructor must be public and exposed for management through
 * the MBeanInfo object.
 * @since 1.0.5
 */
public class JmxLogLevel implements DynamicMBean {
   private static Logger log = Logger.getLogger(JmxLogLevel.class.getName());
   private Global glob;
   private String dClassName = this.getClass().getName();
   private MBeanAttributeInfo[] dAttributes;
   private MBeanConstructorInfo[] dConstructors = new MBeanConstructorInfo[1];
   private MBeanInfo dMBeanInfo = null;
   private int numResets;
   private int numChannels;

   /**
    * Export all log-level settings. 
    */
   public JmxLogLevel() {
      this(Global.instance());
      log.severe("Wrong constructor");
   }

   /**
    * Export all log-level settings. 
    */
   public JmxLogLevel(Global glob) {
      this.glob = glob;
      log.fine("Constructor created");
   }
   
   /**
    * Allows the value of the specified attribute of the Dynamic MBean to be obtained.
    */
   public Object getAttribute(String attribute_name) 
                             throws AttributeNotFoundException,
                                    MBeanException,
                                    ReflectionException {
      if (attribute_name == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("Attribute name cannot be null"), 
                                                "Cannot invoke a getter of " + dClassName + " with null attribute name");
      }

      attribute_name = Global.decode(attribute_name, "US-ASCII"); // HtmlAdapter made from info/admin -> info%2Fadmin
      // "logging/org.xmlBlaster.engine.RequestBroker"
      if (attribute_name.startsWith("logging/"))
         attribute_name = attribute_name.substring(8); // "org.xmlBlaster.engine.RequestBroker"

      try {
         Level level = this.glob.getLogLevel(attribute_name);
         return level.toString();
      }
      catch (XmlBlasterException e) {
         if (attribute_name == null || attribute_name.length() == 0 || "logging/".equals(attribute_name)) return Level.INFO.toString();
         throw(new AttributeNotFoundException("Cannot find '" + attribute_name + "' attribute in " + dClassName));
      }
   }

   /**
    * Sets the value of the specified attribute of the Dynamic MBean.
    */
   public void setAttribute(Attribute attribute) 
                         throws AttributeNotFoundException,
                                InvalidAttributeValueException,
                                MBeanException, 
                                ReflectionException {
      if (attribute == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("Attribute cannot be null"), 
                                             "Cannot invoke a setter of " + dClassName + " with null attribute");
      }
      String name = attribute.getName();
      if (name == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("Attribute name cannot be null"), 
                                             "Cannot invoke the setter of " + dClassName + " with null attribute name");
      }

      name = Global.decode(name, "US-ASCII"); // HtmlAdapter made from info/admin -> info%2Fadmin
      // "logging/org.xmlBlaster.engine.RequestBroker"
      if (name.startsWith("logging/"))
            name = name.substring(8); // "org.xmlBlaster.engine.RequestBroker"

      String value = (String)attribute.getValue();
      if (log.isLoggable(Level.FINE)) log.fine("Setting log level of name=" + name + " to '" + value + "'");

      try {
         Level level = Level.parse(value);
         this.glob.changeLogLevel(name, level);
      }
      catch (XmlBlasterException e) {
         throw(new AttributeNotFoundException("Cannot set log level attribute '"+ name +"':" + e.getMessage()));
      }
      catch (Throwable e) {
         throw(new AttributeNotFoundException("Cannot set log level attribute '"+ name +"':" + e.toString()));
      }
   }

   /**
    * Enables the to get the values of several attributes of the Dynamic MBean.
    */
   public AttributeList getAttributes(String[] attributeNames) {
      if (attributeNames == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("attributeNames[] cannot be null"),
                                             "Cannot invoke a getter of " + dClassName);
      }
      AttributeList resultList = new AttributeList();

      if (attributeNames.length == 0)
         return resultList;
      
      for (int i=0 ; i<attributeNames.length ; i++){
         try {        
            Object value = getAttribute(attributeNames[i]);     
            resultList.add(new Attribute(attributeNames[i],value));
         } catch (Exception e) {
            e.printStackTrace();
         }
      }
      return resultList;
   }

   /**
    * Sets the values of several attributes of the Dynamic MBean, and returns the
    * list of attributes that have been set.
    */
   public AttributeList setAttributes(AttributeList attributes) {
      if (attributes == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("AttributeList attributes cannot be null"),
                                             "Cannot invoke a setter of " + dClassName);
      }
      AttributeList resultList = new AttributeList();

      if (attributes.isEmpty())
         return resultList;

      for (Iterator i = attributes.iterator(); i.hasNext();) {
         Attribute attr = (Attribute) i.next();
         try {
            setAttribute(attr);
            String name = attr.getName();
            Object value = getAttribute(name); 
            resultList.add(new Attribute(name,value));
         } catch(Exception e) {
            e.printStackTrace();
         }
      }
      return resultList;
   }

   /**
    * Allows an operation to be invoked on the Dynamic MBean.
    */
   public Object invoke(String operationName, Object params[], String signature[])
      throws MBeanException,
            ReflectionException {
      if (operationName == null) {
         throw new RuntimeOperationsException(new IllegalArgumentException("Operation name cannot be null"), 
                                             "Cannot invoke a null operation in " + dClassName);
      }
      // Check for a recognized operation name and call the corresponding operation
      if (operationName.equals("reset")){
         return reset();
      } else { 
         // unrecognized operation name:
         throw new ReflectionException(new NoSuchMethodException(operationName), 
                                       "Cannot find the operation " + operationName + " in " + dClassName);
      }
   }

   /**
    * This method provides the exposed attributes and operations of the Dynamic MBean.
    * It provides this information using an MBeanInfo object.
    */
   public MBeanInfo getMBeanInfo() {
      buildDynamicMBeanInfo();
      return dMBeanInfo;
   }

   /**
    * Operation: reset to their initial values
    */
   public String reset() {
      //Enumeration e = LogManager.getLogManager().getLoggerNames();
      /*
      for (int i= 0; i < arr.length; i++) {
         arr[i].setDefaultLogLevel();
      }
      */
      numResets++;
      return "NOT IMPLEMENTED: Logging level is reset to default values";
   }

   /**
    * @return never null
    */
   private Logger[] getLoggers() {
      Enumeration e = LogManager.getLogManager().getLoggerNames();
      ArrayList list = new ArrayList();
      while (e.hasMoreElements()) {
         String name = (String)e.nextElement();
         Logger logger = LogManager.getLogManager().getLogger(name);
         if (logger != null)
            list.add(logger);
      }
      if (list.size() == 0) return new Logger[0];
      return (Logger[])list.toArray(new Logger[list.size()]);
   }

   /**
    * Build the private dMBeanInfo field,
    * which represents the management interface exposed by the MBean;
    * that is, the set of attributes, constructors, operations and notifications
    * which are available for management. 
    *
    * A reference to the dMBeanInfo object is returned by the getMBeanInfo() method
    * of the DynamicMBean interface. Note that, once constructed, an MBeanInfo object is immutable.
    */
   private void buildDynamicMBeanInfo() {
      Logger[] loggers = getLoggers();
      if (this.numChannels == loggers.length) {
         return; // no change -> no need to refresh meta informations
      }

      boolean isReadable = true;
      boolean isWritable = true;
      boolean isIs = false; // true if we use "is" getter

      this.numChannels = loggers.length;

      //String[] levels = { "ERROR", "WARN", "INFO", "CALL",  "TIME", "TRACE", "DUMP", "PLAIN" };
      /*
      String[] levels = { "severe", "warning", "info", "call",  "trace", "dump" };
      String[] comments = { "Critical xmlBlaster server error",
                            "Warning of wrong or problematic usage",
                            "Informations about operation",
                            "Tracing functon calls",
                            "Tracing program executioin",
                            "Dump internal states" };
      ArrayList tmp = new ArrayList();
      for (int i= 0; i < arr.length; i++) {
         String name = arr[i].getChannelKey();
         for (int j=0; j<levels.length; j++) {
            tmp.add(new MBeanAttributeInfo("logging/"+name,  // trace/core, info/queue, etc.
                                    "java.lang.String",
                                    comments[j],
                                    isReadable,
                                    isWritable,
                                    isIs));
         }
      }
      */
      ArrayList tmp = new ArrayList();
      for (int i= 0; i < loggers.length; i++) {
         String name = loggers[i].getName();
         tmp.add(new MBeanAttributeInfo("logging/"+name,  // trace/core, info/queue, etc.
                                    "java.lang.String",
                                    "log level for a specific category",
                                    isReadable,
                                    isWritable,
                                    isIs));
      }

      dAttributes = (MBeanAttributeInfo[])tmp.toArray(new MBeanAttributeInfo[tmp.size()]);

      Constructor[] constructors = this.getClass().getConstructors();
      dConstructors[0] = new MBeanConstructorInfo("JmxLogLevel(): Constructs a JmxLogLevel object",
                                                    constructors[0]);


      MBeanOperationInfo[] dOperations = new MBeanOperationInfo[1];
      MBeanParameterInfo[] params = null;        
      dOperations[0] = new MBeanOperationInfo("reset",
                                             "reset(): reset log levels to default state",
                                             params , 
                                             "java.util.String", 
                                             MBeanOperationInfo.ACTION);

      dMBeanInfo = new MBeanInfo(dClassName,
                                 "Exposing the logging environment.",
                                 dAttributes,
                                 dConstructors,
                                 dOperations,
                                 new MBeanNotificationInfo[0]);
      if (log.isLoggable(Level.FINE)) log.fine("Created MBeanInfo with " + tmp.size() + " attributes");
   }
}
