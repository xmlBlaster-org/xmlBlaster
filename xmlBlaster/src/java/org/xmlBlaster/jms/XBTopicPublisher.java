/*------------------------------------------------------------------------------
Name:      XBTopicPublisher.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Topic;
import javax.jms.TopicPublisher;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * XBTopicPublisher
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicPublisher extends XBMessageProducer implements TopicPublisher {

   private final static String ME = "XBTopicPublisher";

   XBTopicPublisher(I_XmlBlasterAccess access, Topic topic) {
      super(access, topic);
   }

   public Topic getTopic() throws JMSException {
      return (Topic)this.destination;
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
            if (destination == null) 
               throw new JMSException(ME + ".publish of message needs a destination topic to be set", ErrorCode.USER_ILLEGALARGUMENT.getErrorCode());
            msg.setJMSDestination(this.destination, false);
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

}
