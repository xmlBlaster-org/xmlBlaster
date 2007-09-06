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

// TODO implement the check for thread of control ...

/**
 * XBTopicPublisher
 *
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * 
 */
public class XBTopicPublisher extends XBMessageProducer implements TopicPublisher {

   XBTopicPublisher(XBSession session, Topic topic) {
      super(session, topic);
   }

   public Topic getTopic() throws JMSException {
      return (Topic)this.destination;
   }

   public void publish(Message msg) throws JMSException {
      send(msg);
   }

   public void publish(Message msg, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
      send(this.destination, msg, deliveryMode, priority, timeToLive);
   }

   public void publish(Topic topic, Message msg) throws JMSException {
      send(topic, msg);
   }

   public void publish(Topic topic, Message msg, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
      send(topic, msg, deliveryMode, priority, timeToLive);
   }

}
