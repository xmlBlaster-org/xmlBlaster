/*------------------------------------------------------------------------------
Name:      XBTopicSubscriber.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

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

   XBTopicSubscriber(XBSession session, String msgSelector) {
      super(session, msgSelector);
   }

   public boolean getNoLocal() throws JMSException {
      return this.session.noLocal;
   }

   public Topic getTopic() throws JMSException {
      return (Topic)this.session.destination;
   }
}
