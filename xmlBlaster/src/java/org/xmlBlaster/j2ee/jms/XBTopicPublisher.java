/*------------------------------------------------------------------------------
Name:      XBTopicPublisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.j2ee.jms;

import javax.jms.DeliveryMode;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * XBTopicPublisher
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicPublisher implements TopicPublisher {

   private final static String ME = "XBTopicPublisher";
   private I_XmlBlasterAccess access;
   private Topic topic;
   private int deliveryMode = DeliveryMode.PERSISTENT;
   private int priority = 5;
   private long timeToLive = -1L;
   private PublishReturnQos publishReturnQos;      
      
   
   XBTopicPublisher(I_XmlBlasterAccess access, Topic topic) {
      this.access = access;
      this.topic = topic;
   }

   public Topic getTopic() throws JMSException {
      return this.topic;
   }

   public void publish(Message message) throws JMSException {
      if (message instanceof XBMessage) {
         XBMessage msg = (XBMessage)message;
         if (!msg.isDeliveryModeSet()) {
            msg.setJMSDeliveryMode(this.deliveryMode, false);
         }
         if (!msg.isPrioritySet()) {
            msg.setJMSPriority(this.priority, false);
         }
         if (!msg.isTimeToLiveSet()) {
            msg.setJMSExpiration(this.timeToLive, false);
         }
         if (!msg.isDestinationSet()) {
            if (topic == null) 
               throw new JMSException(ME + ".publish of message needs a destination topic to be set", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
            msg.setJMSDestination(this.topic, false);
         }
         
         try {
            this.publishReturnQos = this.access.publish(msg.getMsgUnit()); // what to do whith the publish return qos ?
         }
         catch (XmlBlasterException ex) {
            throw XBConnectionFactory.convert(ex, ME + ".publish: ");         
         }
      }
      else {
         throw new JMSException(ME + ".publish of message from other provider is not supported.", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
      }
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicPublisher#publish(javax.jms.Message, int, int, long)
    */
   public void publish(Message arg0, int arg1, int arg2, long arg3)
      throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.TopicPublisher#publish(javax.jms.Topic, javax.jms.Message)
    */
   public void publish(Topic arg0, Message arg1) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.TopicPublisher#publish(javax.jms.Topic, javax.jms.Message, int, int, long)
    */
   public void publish(Topic arg0, Message arg1, int arg2, int arg3, long arg4)
      throws JMSException {
      // TODO Auto-generated method stub

   }

   public void close() throws JMSException {
      // only the administrator should erase the topic
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDeliveryMode()
    */
   public int getDeliveryMode() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDisableMessageID()
    */
   public boolean getDisableMessageID() throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDisableMessageTimestamp()
    */
   public boolean getDisableMessageTimestamp() throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getPriority()
    */
   public int getPriority() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getTimeToLive()
    */
   public long getTimeToLive() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDeliveryMode(int)
    */
   public void setDeliveryMode(int arg0) throws JMSException {
      // TODO Auto-generated method stub

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

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setPriority(int)
    */
   public void setPriority(int arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setTimeToLive(long)
    */
   public void setTimeToLive(long arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

}
