/*------------------------------------------------------------------------------
Name:      XBMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.util.Collections;
import java.util.Enumeration;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
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
 * XBMessage
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessage implements Message { 
// TextMessage, ObjectMessage {

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
   protected boolean deliveryModeSet, prioritySet, timeToLiveSet, destinationSet;
   protected boolean acknowledged;
   
   XBMessage(Global global, MsgKeyData key, byte[] content, MsgQosData qos, int type) {
      this.global = global;
      this.log = this.global.getLog("jms");
      this.qos = qos;
      this.content = content;
      this.key = key;
      if (this.qos == null) this.qos = new MsgQosData(this.global, MethodName.PUBLISH);
      if (this.key == null) this.key = new MsgKeyData(this.global);
      this.type = type;
      this.qos.setClientProperty("jmsMessageType", "" + this.type);
   }

   boolean isAcknowledged() {
      return this.acknowledged;
   }

   void setAcknowledged(boolean acknowledged) {
      this.acknowledged = acknowledged;
   }

   /* (non-Javadoc)
    * @see javax.jms.Message#acknowledge()
    */
   synchronized public void acknowledge() throws JMSException {
      this.acknowledged = true;
      this.notify();
   }

   public void clearBody() throws JMSException {
      this.content = null;
   }

   public void clearProperties() throws JMSException {
      this.qos = new MsgQosData(this.global, this.qos.getMethod());
      this.deliveryModeSet = false;
      this.prioritySet = false;
      this.timeToLiveSet = false;
      this.qos.setClientProperty("jmsMessageType", "" + this.type);
   }

   /* (non-Javadoc)
    * @see javax.jms.Message#getBooleanProperty(java.lang.String)
    */
   public boolean getBooleanProperty(String key) throws JMSException {
      try {
         return Boolean.getBoolean(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public byte getByteProperty(String key) throws JMSException {
      try {
         return Byte.parseByte(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public double getDoubleProperty(String key) throws JMSException {
      try {
         return Double.parseDouble(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public float getFloatProperty(String key) throws JMSException {
      try {
         return Float.parseFloat(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public int getIntProperty(String key) throws JMSException {
      try {
         return Integer.parseInt(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
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

   public String getJMSMessageID() throws JMSException {
      return (String)this.qos.getClientProperties().get("messageId");
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
      // return (String)qos.getClientProperties().get("jmsType");
      return this.key.getContentMime();
   }

   public long getLongProperty(String key) throws JMSException {
      try {
         return Long.parseLong(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.Message#getObjectProperty(java.lang.String)
    */
   public Object getObjectProperty(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   public Enumeration getPropertyNames() throws JMSException {
      return Collections.enumeration(this.qos.getClientProperties().entrySet());
   }

   public short getShortProperty(String key) throws JMSException {
      try {
         return Short.parseShort(getStringProperty(key));
      }
      catch (Exception ex) {
         if (ex instanceof JMSException) throw (JMSException)ex;
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public String getStringProperty(String key) throws JMSException {
      try {
         return (String)this.qos.getClientProperties().get(key);
      }
      catch (Exception ex) {
         throw new JMSException(ME + ".getStringProperty('" + key + "')", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public boolean propertyExists(String key) throws JMSException {
      return this.qos.getClientProperties().containsKey(key);
   }

   public void setBooleanProperty(String key, boolean value)
      throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   public void setByteProperty(String key, byte value) throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   public void setDoubleProperty(String key, double value)
      throws JMSException {
         this.qos.setClientProperty(key, "" + value);   
   }

   public void setFloatProperty(String key, float value) throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   public void setIntProperty(String key, int value) throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   public void setJMSCorrelationID(String correlationId) throws JMSException {
      this.qos.setClientProperty("correlationId", correlationId);
   }

   public void setJMSCorrelationIDAsBytes(byte[] correlationId) throws JMSException {
      setJMSCorrelationID(new String(correlationId));
   }

   public void setJMSDeliveryMode(int deliveryMode) throws JMSException {
      setJMSDeliveryMode(deliveryMode, true);
   }   

   void setJMSDeliveryMode(int deliveryMode, boolean mark) throws JMSException {
      if (deliveryMode == DeliveryMode.PERSISTENT) { 
         this.qos.setPersistent(true);
         if (mark) this.deliveryModeSet = true;
      }
      else if (deliveryMode == DeliveryMode.NON_PERSISTENT) { 
         this.qos.setPersistent(false);
         if (mark) this.deliveryModeSet = true;
      }
      else 
         throw new JMSException("setJMSDeliveryMode('" + deliveryMode +"'): delivery mode is invalid", ErrorCode.USER_CONFIGURATION.getErrorCode());
   }

   public void setJMSDestination(Destination destination) throws JMSException {
      setJMSDestination(destination, true);
   }

   void setJMSDestination(Destination destination, boolean mark) throws JMSException {
      if (destination instanceof Topic) {
         String txt = ((Topic)destination).getTopicName();
         this.key.setOid(txt);
         if (mark) this.destinationSet = true;
      }
      else if (destination instanceof Queue) {
         String txt = ((Queue)destination).getQueueName();
         org.xmlBlaster.util.qos.address.Destination
           dst = new org.xmlBlaster.util.qos.address.Destination(new SessionName(this.global, txt));
         this.qos.addDestination(dst);
         if (mark) this.destinationSet = true;
      }
      else {
         throw new JMSException(ME + ".setJMSDestination: unallowed destination type (must be either topic or queue) but is '" + destination.getClass().getName(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());         
      }
   }

   public void setJMSExpiration(long lifeTime) throws JMSException {
      setJMSExpiration(lifeTime, true);
   }

   void setJMSExpiration(long lifeTime, boolean mark) throws JMSException {
      this.qos.setLifeTime(lifeTime);
      if (mark) this.timeToLiveSet = true;
   }

   public void setJMSMessageID(String messageId) throws JMSException {
      this.qos.setClientProperty("messageId", messageId);
   }

   public void setJMSPriority(int priority) throws JMSException {
      setJMSPriority(priority, true);
   }

   void setJMSPriority(int priority, boolean mark) throws JMSException {
      try {
         this.qos.setPriority(PriorityEnum.toPriorityEnum(priority));
         if (mark) this.prioritySet = true;
      }
      catch (IllegalArgumentException ex) {
         throw new JMSException(ex.getMessage(), ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());       
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.Message#setJMSRedelivered(boolean)
    */
   public void setJMSRedelivered(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + ".setJMSRedelivered not implemented yet");       
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

   public void setJMSTimestamp(long timestamp) throws JMSException {
      // not processed by xmlBlaster, only transported for jms purposes (we have an own set on server side)
      this.qos.setClientProperty("jmsTimestamp", "" + timestamp);
   }

   public void setJMSType(String contentMime) throws JMSException {
      this.key.setContentMime(contentMime);
   }

   public void setLongProperty(String key, long value) throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   /* (non-Javadoc)
    * @see javax.jms.Message#setObjectProperty(java.lang.String, java.lang.Object)
    */
   public void setObjectProperty(String arg0, Object arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + ".setObjectProperty not implemented yet");       
   }

   public void setShortProperty(String key, short value) throws JMSException {
      this.qos.setClientProperty(key, "" + value);   
   }

   public void setStringProperty(String key, String value)
      throws JMSException {
         this.qos.setClientProperty(key, value);   
   }

   // own package protected helper methods

   MsgUnit getMsgUnit() {
      return new MsgUnit(this.key, this.content, this.qos);
   }

   boolean isDeliveryModeSet() {
      return this.deliveryModeSet;
   }

   boolean isPrioritySet() {
      return this.prioritySet;
   }

   boolean isTimeToLiveSet() {
      return this.timeToLiveSet;
   }

   boolean isDestinationSet() {
      return this.destinationSet;
   }   

}
