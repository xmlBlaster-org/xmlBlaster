/*------------------------------------------------------------------------------
Name:      XBTopicSubscriber.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicSubscriber;

import org.xmlBlaster.client.I_XmlBlasterAccess;

/**
 * XBTopicSubscriber
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicSubscriber extends XBMessageConsumer implements TopicSubscriber {

   private final static String ME = "XBTopicSubscriber";

   XBTopicSubscriber(I_XmlBlasterAccess access, Topic topic, String msgSelector, boolean noLocal, int ackMode, boolean durable) {
      super(access, topic, msgSelector, noLocal, ackMode, durable);
   }

   public boolean getNoLocal() throws JMSException {
      return this.noLocal;
   }

   public Topic getTopic() throws JMSException {
      return (Topic)this.destination;
   }
}
