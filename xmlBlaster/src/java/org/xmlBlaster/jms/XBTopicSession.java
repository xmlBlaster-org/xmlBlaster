/*
 * Created on Sep 29, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
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

   XBTopicSession(XBConnection connection, int ackMode) {
      super(connection, ackMode);
   }

   public TopicPublisher createPublisher(Topic topic) throws JMSException {
      return new XBTopicPublisher(this.connection.getAccess(), topic);
   }

   public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, null, false, this.ackMode);
   }

   public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
      throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, messageSelector, noLocal, this.ackMode);
   }

}
