/*------------------------------------------------------------------------------
Name:      XBMessageProducer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageFormatException;
import javax.jms.MessageProducer;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * XBMessageProducer
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessageProducer implements MessageProducer {

   private final static String ME = "XBMessageProducer";
   protected I_XmlBlasterAccess access;
   protected Destination destination;
   protected int deliveryMode = DeliveryMode.PERSISTENT;
   protected int priority = 5;
   protected long timeToLive = -1L;
   protected PublishReturnQos publishReturnQos;      
      
   
   XBMessageProducer(I_XmlBlasterAccess access, Destination destination) {
      this.access = access;
      this.destination = destination;
   }

   public void close() throws JMSException {
      // only the administrator should erase the topic
   }

   public int getDeliveryMode() throws JMSException {
      return this.deliveryMode;
   }

   /**
    * xmlBlaster always creates a unique message id (the unique timestamp)
    */
   public boolean getDisableMessageID() throws JMSException {
      return false;
   }

   /**
    * Ignored here since we always send the timestamp
    */
   public boolean getDisableMessageTimestamp() throws JMSException {
      return false;
   }

   public int getPriority() throws JMSException {
      return this.priority;
   }

   public long getTimeToLive() throws JMSException {
      return this.timeToLive;
   }

   public void setDeliveryMode(int deliveryMode) throws JMSException {
      this.deliveryMode = deliveryMode;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDisableMessageID(boolean)
    */
   public void setDisableMessageID(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDisableMessageTimestamp(boolean)
    */
   public void setDisableMessageTimestamp(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   public void setPriority(int priority) throws JMSException {
      this.priority = priority;
   }

   public void setTimeToLive(long timeToLive) throws JMSException {
      this.timeToLive = timeToLive;
   }

   public void send(Message message) throws JMSException {
      send(this.destination, message);
   }

   public void send(Destination dest, Message message)
      throws JMSException {
      send(dest, message, this.deliveryMode, this.priority, this.timeToLive);
   }

   public void send(Message message, int deliveryMode, int prio, long timeToLive)
      throws JMSException {
      send(this.destination, message, deliveryMode, prio, timeToLive);
   }

   public void send(Destination dest, Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
      if (message instanceof XBMessage) {
         XBMessage msg = (XBMessage)message;
         msg.setJMSDeliveryMode(deliveryMode);
         msg.setJMSPriority(priority);
         if (destination == null) 
            throw new UnsupportedOperationException(ME + ".send of message needs a destination topic to be set");
         msg.setJMSDestination(dest);
         msg.setJMSExpiration(timeToLive);
         try {
            MsgUnit msgUnit = msg.getMsgUnit();
            this.publishReturnQos = this.access.publish(msgUnit); 
            // what to do whith the publish return qos ?
         }
         catch (XmlBlasterException ex) {
            throw XBConnectionFactory.convert(ex, ME + ".send: ");         
         }
      }
      else {
         throw new MessageFormatException(ME + ".send of message from other provider is not supported.", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   public Destination getDestination() throws JMSException {
      return this.destination;
   }

}
