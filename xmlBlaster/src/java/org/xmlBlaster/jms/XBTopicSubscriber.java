/*------------------------------------------------------------------------------
Name:      XBTopicSubscriber.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

/**
 * XBTopicSubscriber
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicSubscriber extends XBMessageConsumer implements TopicSubscriber {

   private final static String ME = "XBTopicSubscriber";

   XBTopicSubscriber(XBSession session, Destination destination, String msgSelector, boolean noLocal) 
      throws JMSException {
      super(session, destination, msgSelector, noLocal);
   }

   public boolean getNoLocal() throws JMSException {
      return this.noLocal;
   }

   public Topic getTopic() throws JMSException {
      return (Topic)this.destination;
   }
}
