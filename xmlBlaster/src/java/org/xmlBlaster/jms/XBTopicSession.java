/*------------------------------------------------------------------------------
Name:      XBTopicSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

/**
 * XBTopicSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicSession extends XBSession implements TopicSession {

   private final static String ME = "XBTopicSession";

   XBTopicSession(XBConnection connection, int ackMode, boolean transacted) {
      super(connection, ackMode, transacted);
   }

   public TopicPublisher createPublisher(Topic topic) throws JMSException {
      return new XBTopicPublisher(this.connection.getAccess(), topic);
   }

   public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, null, false, this.ackMode, false);
   }

   public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
      throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, messageSelector, noLocal, this.ackMode, false);
   }

}
