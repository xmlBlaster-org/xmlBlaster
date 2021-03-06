/*------------------------------------------------------------------------------
Name:      XBMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageNotWriteableException;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.qos.ClientProperty;

/**
 * XBMessage.
 * 
 * Implementation details about how 
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBMessage implements Message { 

   public final static int TEXT         = 0;
   public final static int BYTES        = 1;
   public final static int OBJECT       = 2;
   public final static int MAP          = 3;
   public final static int STREAM       = 4;
   public final static int STREAMING    = 5;
   public final static int DEFAULT_TYPE = XBMessage.STREAM;
   private static Logger log = Logger.getLogger(XBMessage.class.getName());
   private final static String ME = "XBMessage";
   protected Global  global;
   protected byte[]  content;
   protected int     type;
   protected boolean acknowledged;
   protected boolean readOnly;
   protected Map     props;
   private boolean   propertyReadOnly;
   protected XBSession   session;
   protected Destination destination;
   
   // thes are the properties which are not stored in the props map.
   private boolean redelivered;
   private int priority;
   private String messageID;
   private String correlationID;
   private int deliveryMode;
   private long expiration;
   private Destination replyTo;
   private long timestamp;
   private String jmsType;
   

   /**
    * 
    * @param session
    * @param content
    * @param type
    */
   public XBMessage(XBSession session, byte[] content, int type) {
      this.session = session;
      if (this.session == null) 
         this.global = new Global();
      else 
         this.global = this.session.global;

      this.content = content;
      this.props = new HashMap();
      this.type = type;
   }

   boolean isAcknowledged() {
      return this.acknowledged;
   }

   void setAcknowledged(boolean acknowledged) {
      this.acknowledged = acknowledged;
   }

   synchronized public void acknowledge() throws JMSException {
      this.acknowledged = true;
      if (this.session == null) return;
      this.session.connection.checkClosed();
      synchronized (this.session) {
         this.session.notifyAll();
      }
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
   }

   public void clearProperties() throws JMSException {
      this.props.clear();
      this.propertyReadOnly = false;
   }

   public boolean getBooleanProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Boolean.getBoolean((String)obj);
      if (obj instanceof Boolean) return ((Boolean)obj).booleanValue();
      throw new XBException(ME + ".getBooleanProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public byte getByteProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Byte.parseByte((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).byteValue();
      throw new XBException(ME + ".getByteProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public double getDoubleProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Double.parseDouble((String)obj);
      if (obj instanceof Float) return ((Float)obj).doubleValue();
      if (obj instanceof Double) return ((Double)obj).doubleValue();
      throw new XBException(ME + ".getDoubleProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public float getFloatProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Float.parseFloat((String)obj);
      if (obj instanceof Float) return ((Float)obj).floatValue();
      throw new XBException(ME + ".getDoubleProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public int getIntProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Integer.parseInt((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).intValue();
      if (obj instanceof Short) return ((Short)obj).intValue();
      if (obj instanceof Integer) return ((Integer)obj).intValue();
      throw new XBException(ME + ".getIntegerProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public String getJMSCorrelationID() throws JMSException {
      return this.correlationID;
   }

   public byte[] getJMSCorrelationIDAsBytes() throws JMSException {
      return getJMSCorrelationID().getBytes();
   }

   public int getJMSDeliveryMode() throws JMSException {
      return this.deliveryMode;
   }

   public Destination getJMSDestination() throws JMSException {
      return this.destination;
   }

   public long getJMSExpiration() throws JMSException {
      return this.expiration;
   }

   /**
    * xmlBlaster specific messageId is our unique timestamp
    */
   public String getJMSMessageID() throws JMSException {
      return this.messageID;
   }

   public int getJMSPriority() throws JMSException {
      return this.priority;
   }

   public boolean getJMSRedelivered() throws JMSException {
      return redelivered;
   }
   
   public Destination getJMSReplyTo() throws JMSException {
      return this.replyTo;
   }

   public long getJMSTimestamp() throws JMSException {
      return this.timestamp;
   }

   public String getJMSType() throws JMSException {
      return this.jmsType;
   }

   public long getLongProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Long.parseLong((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).longValue();
      if (obj instanceof Short) return ((Short)obj).longValue();
      if (obj instanceof Integer) return ((Integer)obj).longValue();
      if (obj instanceof Long) return ((Long)obj).longValue();
      throw new XBException(ME + ".getLongProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public Object getObjectProperty(String key) throws JMSException {
      ClientProperty prop = (ClientProperty)this.props.get(addToKeyAndCheck(key));
      if (prop == null)
         return null;
      return prop.getObjectValue();
   }

   public Enumeration getPropertyNames() throws JMSException {
      Set set = new HashSet();
      Iterator iter = this.props.keySet().iterator();
      while (iter.hasNext()) {
         String completeName = (String)iter.next();
         int pos = completeName.indexOf(Constants.JMS_PREFIX);
         if (pos == 0)
            set.add(completeName.substring(Constants.JMS_PREFIX.length()));
         else
            set.add(completeName);
      }
      return Collections.enumeration(set);
   }

   /**
    * Can handle String, Byte, and Short properties
    */
   public short getShortProperty(String key) throws JMSException {
      Object obj = getObjectProperty(key);
      if (obj instanceof String) return Short.parseShort((String)obj);
      if (obj instanceof Byte) return ((Byte)obj).shortValue();
      if (obj instanceof Short) return ((Short)obj).shortValue();
      throw new XBException(ME + ".getShortProperty('" + key + "') is illegal since of type '" + obj.getClass().getName() + "'", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
   }

   public String getStringProperty(String key) throws JMSException {
      return ((ClientProperty)this.props.get(addToKeyAndCheck(key))).getStringValue();
   }

   public boolean propertyExists(String key) throws JMSException {
      return this.props.containsKey(addToKeyAndCheck(key));
   }
   
   public static String addToKeyAndCheck(String key) {
      return Constants.addJmsPrefix(key, log);
   }
   
   public static ClientProperty get(String key, Map map) {
      return (ClientProperty)map.get(addToKeyAndCheck(key));
   }
   
   public void setBooleanProperty(String key, boolean value)
      throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setBooleanProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_BOOLEAN, null, "" + value));   
   }

   public void setByteProperty(String key, byte value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setByteProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_BYTE, null, "" + value));   
   }

   public void setDoubleProperty(String key, double value)
      throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setDoubleProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_DOUBLE, null, "" + value));   
   }

   public void setFloatProperty(String key, float value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setFloatProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_FLOAT, null, "" + value));   
   }

   public void setIntProperty(String key, int value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setIntProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_INT, null, "" + value));   
   }

   public void setJMSCorrelationID(String correlationID) throws JMSException {
      this.correlationID = correlationID;
   }

   public void setJMSCorrelationIDAsBytes(byte[] correlationId) throws JMSException {
      setJMSCorrelationID(Constants.toUtf8String(correlationId));
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
      this.deliveryMode = deliveryMode;
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSDestination(Destination destination) throws JMSException {
      this.destination = destination;
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSExpiration(long lifeTime) throws JMSException {
      this.expiration = lifeTime;
   }

   /**
    * This is overwritten when invoking the getter
    * This method is invoked by the send method
    */
   public void setJMSMessageID(String messageID) throws JMSException {
      this.messageID = messageID;
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSPriority(int priority) throws JMSException {
      this.priority = priority;
   }

   /**
    * Only useful for interprovider operations, otherwise ignored
    * This method is normally invoked by the provider
    */
   public void setJMSRedelivered(boolean redelivered) throws JMSException {
      this.redelivered = redelivered;
   }

   public void setJMSReplyTo(Destination sender) throws JMSException {
      this.replyTo = sender;
   }

   /**
    * This method is invoked by the send method
    */
   public void setJMSTimestamp(long timestamp) throws JMSException {
      this.timestamp = timestamp;
   }

   public void setJMSType(String jmsType) throws JMSException {
      this.jmsType = jmsType;
   }

   public void setLongProperty(String key, long value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setLongProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_LONG, null, "" + value));
   }

   public void setObjectProperty(String key, Object value)
      throws JMSException {
      if (value instanceof String) 
         setStringProperty(key, (String)value);
      else if (value instanceof Boolean) 
         setBooleanProperty(key, ((Boolean)value).booleanValue());
      else if (value instanceof Byte) 
         setByteProperty(key, ((Byte)value).byteValue());
      else if (value instanceof Short) 
         setShortProperty(key, ((Short)value).shortValue());
      else if (value instanceof Integer) 
         setIntProperty(key, ((Integer)value).intValue());
      else if (value instanceof Long) 
         setLongProperty(key, ((Long)value).longValue());
      else if (value instanceof Float) 
         setFloatProperty(key, ((Float)value).floatValue());
      else if (value instanceof Double) 
         setDoubleProperty(key, ((Double)value).doubleValue());
      else
         throw new javax.jms.MessageFormatException(ME + ".setObjectProperty: prop '" + key + "' is of type '" + value.getClass().getName() + "' which is not allowed here", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());       
   }

   public void setShortProperty(String key, short value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setShortProperty", key);
      this.props.put(key, new ClientProperty(key, Constants.TYPE_SHORT, null, "" + value));   
   }

   public void setStringProperty(String key, String value) throws JMSException {
      key = addToKeyAndCheck(key);
      checkPropertiesReadOnly("setStringProperty", key);
      this.props.put(key, new ClientProperty(key, null, null, value));   
   }

   /**
    * Used internally
    * @param propertyReadOnly
    */
   void setPropertyReadOnly(boolean propertyReadOnly) {
      this.propertyReadOnly = propertyReadOnly;
   }

   /**
    * Used internally
    * @param readOnly
    */
   void setReadOnly(boolean readOnly) {
      this.readOnly = readOnly;
   }

}
