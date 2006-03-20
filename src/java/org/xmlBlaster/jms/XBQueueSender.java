/*------------------------------------------------------------------------------
Name:      XBQueueSender.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Queue;
import javax.jms.QueueSender;


// TODO implement check for thread of control

/**
 * XBQueueSender
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBQueueSender extends XBMessageProducer implements QueueSender {

   private final static String ME = "XBQueueSender";

   XBQueueSender(XBSession session, Queue queue) {
      super(session, queue);
   }

   public Queue getQueue() throws JMSException {
      return (Queue)this.destination;
   }

   public void send(Queue dest, Message message)
      throws JMSException {
   }

   public void send(Queue dest, Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
   }
}
