/*------------------------------------------------------------------------------
Name:      XBMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableException;
import javax.jms.Queue;
import javax.jms.Topic;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.MethodName;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.MsgQosData;

/**
 * XBMessage.
 * 
 * Implementation details about how 
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessage implements Message { 

   public final static int TEXT   = 0;
   public final static int BYTES  = 1;
   public final static int OBJECT = 2;
   public final static int MAP    = 3;
   public final static int STREAM = 4;
   public final static int DEFAULT_TYPE = XBMessage.STREAM;
   
   private final static String ME = "XBMessage";
   protected Global global;
   protected LogChannel log;
   protected MsgQosData qos;
   protected MsgKeyData key;
   protected byte[] content;
   protected int type;
   protected boolean acknowledged;
   protected boolean readOnly;
   protected boolean writeOnly;
   protected Hashtable extraHeader; 
   private boolean propertyReadOnly;
   
   public XBMessage(Global global, MsgKeyData key, byte[] content, MsgQosData qos, int type) {
      this.global = global;
      this.log = this.global.getLog("jms");
      this.qos = qos;
      this.content = content;
      this.key = key;
      this.extraHeader = new Hashtable();
      importExtraHeader();
      if (this.qos == null) this.qos = new MsgQosData(this.global, MethodName.PUBLISH);
      if (this.key == null) this.key = new MsgKeyData(this.global);
      this.type = type;
      this.qos.setClientProperty("jmsMessageType", "" + this.type);
      if (this.content == null) this.writeOnly = true;
      else {
         this.readOnly = true;
         this.propertyReadOnly = true;
      } 
   }

   /**
    * Imports the extra header properties from the qos
    */
   private void importExtraHeader() {
      if (this.qos != null) { 
         String[] keys = (String[])this.qos.getClientProperties().keySet().toArray(new String[this.qos.getClientProperties().size()]);
         for (int i=0; i < keys.length; i++) {
            if (keys[i].startsWith("jms/")) {
               this.extraHeader.put(keys[i].substring("jms/".length()), this.qos.getClientProperties().get(keys[i]));
            }
         }
      }
   }

   /**
    * Puts the extra header properties into the qos
    */
   private void exportExtraHeader() {
      if (this.qos != null) { 
         Enumeration enum = this.extraHeader.keys();
         while (enum.hasMoreElements()) {
            String key = (String)enum.nextElement();
            this.qos.setClientProperty("jms/" + key, this.extraHeader.get(key));
         }
      }
   }

   boolean isAcknowledged() {
      return this.acknowledged;
   }

   void setAcknowledged(boolean acknowledged) {
      this.acknowledged = acknowledged;
   }

   synchronized public void acknowledge() throws JMSException {
      this.acknowledged = true;
      this.notify();
   }

   /**
    * Checks if the properties are readonly. If yes, then an exception is 
    * thrown 
    * Also checks if the key is null or empty in which case it throws
    * an IllegalArgumentException is thrown
    * @param methodName
    * @param key
    * @throws MessageNotWriteableException
    */
   final protected void checkPropertiesReadOnly(String methodName, String key) throws MessageNotWriteableException {
      if (this.propertyReadOnly)
         throw new MessageNotWriteableException(ME + "." + methodName + " for '" + key + "' message properties are in readonly modus", ErrorCode.USER_CLIENTCODE.getErrorCode()); 
      if (key == null || key.trim().length() < 1)
         throw new IllegalArgumentException(ME + "." + methodName + ": Empty or null key values are not allowed"); 
   }

   public void clearBody() throws JMSException {
      this.content = null;
      this.readOnly = false;
      this.writeOnly = true;
   }

   public void clearProperties() throws JMSException {
      this.qos = new MsgQosData(this.global, this.qos.getMethod());
      this.extraHeader.clear();
      this.propertyReadOnly = false;
   }

   public boolean getBooleanProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Boolean.getBoolean((String)obj);
      if (obj instanceof Boolean) return ((Boolean)obj).booleanValue();
      throw new JMSException(ME + ".getBooleanProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public byte getByteProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Byte.parseByte((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).byteValue();
      throw new JMSException(ME + ".getByteProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public double getDoubleProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Double.parseDouble((String)obj);
      if (obj instanceof Float) return ((Float)obj).doubleValue();
      if (obj instanceof Double) return ((Double)obj).doubleValue();
      throw new JMSException(ME + ".getDoubleProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public float getFloatProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Float.parseFloat((String)obj);
      if (obj instanceof Float) return ((Float)obj).floatValue();
      throw new JMSException(ME + ".getDoubleProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public int getIntProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Integer.parseInt((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).intValue();
      if (obj instanceof Short) return ((Short)obj).intValue();
      if (obj instanceof Integer) return ((Integer)obj).intValue();
      throw new JMSException(ME + ".getIntegerProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public String getJMSCorrelationID() throws JMSException {
      return (String)this.qos.getClientProperties().get("correlationId");
   }

   public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
      return getJMSCorrelationID().getBytes();
   }

   public int getJMSDeliveryMode() throws JMSException {
      if (this.qos.isVolatile()) return DeliveryMode.NON_PERSISTENT;
      return DeliveryMode.PERSISTENT;
   }

   public Destination getJMSDestination() throws JMSException {
      String txt = this.key.getOid();
      if (this.qos.isPtp()) return new XBQueue(txt);
      else return new XBTopic(txt);
   }

   public long getJMSExpiration() throws JMSException {
      return this.qos.getRemainingLife();
   }

   /**
    * xmlBlaster specific messageId is our unique timestamp
    */
   public String getJMSMessageID() throws JMSException {
      return "ID:" + this.qos.getRcvTimestamp();
   }

   public int getJMSPriority() throws JMSException {
      return this.qos.getPriority().getInt();
   }

   public boolean getJMSRedelivered() throws JMSException {
      return this.qos.getRedeliver() != 0;
   }

   public Destination getJMSReplyTo() throws JMSException {
      return new XBQueue(this.qos.getSender().getAbsoluteName());
   }

   public long getJMSTimestamp() throws JMSException {
      return this.qos.getRcvTimestamp().getMillis();
   }

   public String getJMSType() throws JMSException {
      return (String)qos.getClientProperties().get("JMSType");
      // return this.key.getContentMime();
   }

   public long getLongProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Long.parseLong((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).longValue();
      if (obj instanceof Short) return ((Short)obj).longValue();
      if (obj instanceof Integer) return ((Integer)obj).longValue();
      if (obj instanceof Long) return ((Long)obj).longValue();
      throw new JMSException(ME + ".getLongProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public Object getObjectProperty(String key) throws JMSException {
      return this.qos.getClientProperties().get(key);
   }

   public Enumeration getPropertyNames() throws JMSException {
      return Collections.enumeration(this.qos.getClientProperties().entrySet());
   }

   /**
    * Can handle String, Byte, and Short properties
    */
   public short getShortProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Short.parseShort((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).shortValue();
      if (obj instanceof Short) return ((Short)obj).shortValue();
      throw new JMSException(ME + ".getShortProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public String getStringProperty(String key) throws JMSException {
      return this.qos.getClientProperties().get(key).toString();
   }

   public boolean propertyExists(String key) throws JMSException {
      return this.qos.getClientProperties().containsKey(key);
   }

   public void setBooleanProperty(String key, boolean value)
      throws JMSException {
      checkPropertiesReadOnly("setBooleanProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setByteProperty(String key, byte value) throws JMSException {
      checkPropertiesReadOnly("setByteProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setDoubleProperty(String key, double value)
      throws JMSException {
      checkPropertiesReadOnly("setDoubleProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setFloatProperty(String key, float value) throws JMSException {
      checkPropertiesReadOnly("setFloatProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setIntProperty(String key, int value) throws JMSException {
      checkPropertiesReadOnly("setIntProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setJMSCorrelationID(String correlationId) throws JMSException {
      this.qos.setClientProperty("correlationId", correlationId);
   }

   public void setJMSCorrelationIDAsBytes(byte[] correlationId) throws JMSException {
      setJMSCorrelationID(new String(correlationId));
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
      if (deliveryMode == DeliveryMode.PERSISTENT) { 
         this.qos.setPersistent(true);
      }
      else if (deliveryMode == DeliveryMode.NON_PERSISTENT) { 
         this.qos.setPersistent(false);
      }
      else 
         throw new JMSException("setJMSDeliveryMode('" + deliveryMode +"'): delivery mode is invalid", ErrorCode.USER_CONFIGURATION.getErrorCode());
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSDestination(Destination destination) throws JMSException {
      if (destination instanceof Topic) {
         String txt = ((Topic)destination).getTopicName();
         this.key.setOid(txt);
      }
      else if (destination instanceof Queue) {
         String txt = ((Queue)destination).getQueueName();
         org.xmlBlaster.util.qos.address.Destination
           dst = new org.xmlBlaster.util.qos.address.Destination(new SessionName(this.global, txt));
         this.qos.addDestination(dst);
      }
      else {
         throw new JMSException(ME + ".setJMSDestination: unallowed destination type (must be either topic or queue) but is '" + destination.getClass().getName(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());         
      }
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSExpiration(long lifeTime) throws JMSException {
      this.qos.setLifeTime(lifeTime);
   }

   /**
    * This is overwritten when invoking the getter
    * This method is invoked by the send method
    */
   public void setJMSMessageID(String messageId) throws JMSException {
      this.qos.setClientProperty("JMSMessageID", messageId);
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSPriority(int priority) throws JMSException {
      try {
         this.qos.setPriority(PriorityEnum.toPriorityEnum(priority));
      }
      catch (IllegalArgumentException ex) {
         throw new JMSException(ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());       
      }
   }

   /**
    * Only useful for interprovider operations, otherwise ignored
    * This method is normally invoked by the provider
    */
   public void setJMSRedelivered(boolean redelivered) throws JMSException {
      this.qos.setClientProperty("JMSRedelivered", redelivered);
   }

   public void setJMSReplyTo(Destination sender) throws JMSException {
      if (sender instanceof Topic) {
         // TODO: should this be allowed ?
         String txt = ((Topic)sender).getTopicName();
         this.qos.setSender(new SessionName(this.global, txt));
      }
      else if (sender instanceof Queue) {
         String txt = ((Queue)sender).getQueueName();
         this.qos.setSender(new SessionName(this.global, txt));
      }
      else {
         throw new JMSException(ME + ".setJMSReplyTo: unallowed destination type (must be either topic or queue) but is '" + sender.getClass().getName(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());         
      }
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSTimestamp(long timestamp) throws JMSException {
      // not processed by xmlBlaster, only transported for jms purposes (we have an own set on server side)
      this.qos.setClientProperty("jmsTimestamp", "" + timestamp);
   }

   public void setJMSType(String jmsType) throws JMSException {
      // this.key.setContentMime(jmsType);
      this.qos.setClientProperty("JMSType", jmsType);
   }

   public void setLongProperty(String key, long value) throws JMSException {
      checkPropertiesReadOnly("setLongProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setObjectProperty(String key, Object value)
      throws JMSException {
      if (value instanceof String) setStringProperty(key, (String)value);
      if (value instanceof Boolean) setBooleanProperty(key, ((Boolean)value).booleanValue());
      if (value instanceof Byte) setByteProperty(key, ((Byte)value).byteValue());
      if (value instanceof Short) setShortProperty(key, ((Short)value).shortValue());
      if (value instanceof Integer) setIntProperty(key, ((Integer)value).intValue());
      if (value instanceof Long) setLongProperty(key, ((Long)value).longValue());
      if (value instanceof Float) setFloatProperty(key, ((Float)value).floatValue());
      if (value instanceof Double) setDoubleProperty(key, ((Double)value).doubleValue());
      throw new javax.jms.MessageFormatException(ME + ".setObjectProperty: prop '" + key + "' is of type '" + value.getClass().getName() + "' which is not allowed here", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());       
   }

   public void setShortProperty(String key, short value) throws JMSException {
      checkPropertiesReadOnly("setShortProperty", key);
      this.qos.setClientProperty(key, value);   
   }

   public void setStringProperty(String key, String value)
      throws JMSException {
         this.qos.setClientProperty(key, value);   
   }

   // own package protected helper methods
   MsgUnit getMsgUnit() {
      exportExtraHeader();
      return new MsgUnit(this.key, this.content, this.qos);
   }

}
