/*------------------------------------------------------------------------------
Name:      XBSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Serializable;
import java.util.HashMap;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.Session;
import javax.jms.TopicSubscriber;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

/**
 * XBSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBSession implements Session {

   private final static String ME = "XBSession";
   protected Global global;
   protected LogChannel log;
   protected XBConnection connection;
   protected int ackMode;
   protected final boolean noLocalDefault = true; // is this conform to jms ?
   protected final boolean durableDefault = false;   
   protected final String msgSelectorDefault = null;
   protected MessageListener msgListener;
   protected HashMap durableSubscriptionMap;

   XBSession(XBConnection connection, int ackMode) {
      this.connection = connection;
      this.global = connection.getGlobal();
      this.log = this.global.getLog("jms");
      this.ackMode = ackMode;
      this.durableSubscriptionMap = new HashMap();
   }

   public TopicSubscriber createDurableSubscriber(Topic topic, String name)
      throws JMSException {
      return createDurableSubscriber(topic, name, this.msgSelectorDefault, this.noLocalDefault);
   }

   public TopicSubscriber createDurableSubscriber(Topic topic, String name, String msgSelector, boolean noLocal)
      throws JMSException {
      TopicSubscriber sub = new XBTopicSubscriber(this.connection.getAccess(), topic, msgSelector, noLocal, this.ackMode, true);
      this.durableSubscriptionMap.put(name, sub);
      return sub;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#close()
    */
   public void close() throws JMSException {
      // TODO Auto-generated method stub
   }

   public void commit() throws JMSException {
      // TODO Auto-generated method stub
      this.log.warn(ME, " 'commit' not implemented yet");
   }

   public BytesMessage createBytesMessage() throws JMSException {
      return new XBBytesMessage(this.global, null, null, null);
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createMapMessage()
    */
   public MapMessage createMapMessage() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createMapMessage' not implemented yet");
   }

   public Message createMessage() throws JMSException {
      return new XBMessage(this.global, null, null, null, XBMessage.DEFAULT_TYPE);
   }

   public ObjectMessage createObjectMessage() throws JMSException {
      return new XBObjectMessage(this.global, null, null, null);
   }

   public ObjectMessage createObjectMessage(Serializable content)
      throws JMSException {
      ObjectMessage msg = createObjectMessage(); 
      msg.setObject(content);
      return msg;
   }

   public StreamMessage createStreamMessage() throws JMSException {
      return new XBStreamMessage(this.global, null, null, null);
   }

   public TextMessage createTextMessage() throws JMSException {
      return new XBTextMessage(this.global, null, null, null);      
   }

   public TextMessage createTextMessage(String text) throws JMSException {
      return new XBTextMessage(this.global, null, text.getBytes(), null);      
   }

   public MessageListener getMessageListener() throws JMSException {
      return this.msgListener;
   }

   public boolean getTransacted() throws JMSException {
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#recover()
    */
   public void recover() throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#rollback()
    */
   public void rollback() throws JMSException {
      // TODO Auto-generated method stub
   }

   public void run() {
   }

   public void setMessageListener(MessageListener msgListener) throws JMSException {
      this.msgListener = msgListener;
   }

   public Queue createQueue(String name) throws JMSException {
      return new XBQueue(name);
   }

   public QueueBrowser createBrowser(Queue queue) throws JMSException {
      return new XBQueueBrowser(queue, null);
   }

   public QueueBrowser createBrowser(Queue queue, String msgSelector)
      throws JMSException {
         return new XBQueueBrowser(queue, msgSelector);
   }

   public TemporaryQueue createTemporaryQueue() throws JMSException {
      return new XBTemporaryQueue();
   }

   public int getAcknowledgeMode() throws JMSException {
      return this.ackMode;
   }

   public MessageProducer createProducer(Destination destination)
      throws JMSException {
      return new XBMessageProducer(this.connection.getAccess(), destination);
   }

   public MessageConsumer createConsumer(Destination destination)
      throws JMSException {
      return new XBMessageConsumer(this.connection.getAccess(), destination, this.msgSelectorDefault, this.noLocalDefault, this.ackMode, this.durableDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector)
      throws JMSException {
      return new XBMessageConsumer(this.connection.getAccess(), destination, msgSelector, this.noLocalDefault, this.ackMode, this.durableDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector, boolean noLocal)
      throws JMSException {
      return new XBMessageConsumer(this.connection.getAccess(), destination, msgSelector, noLocal, this.ackMode, this.durableDefault); 
   }

   public Topic createTopic(String name) throws JMSException {
      return new XBTopic(name);
   }

   public void unsubscribe(String subName) throws JMSException {
      try {
         TopicSubscriber sub = (TopicSubscriber)this.durableSubscriptionMap.remove(subName);
         if (sub == null) 
            throw new JMSException(ME, "unsubscribe '" + subName + "'failed because the topic has not been found in this session");
         sub.close();
      }
      catch (Exception ex) {
         throw new JMSException(ME, "unsubscribe '" + subName + "'failed. Cause: " + ex.getMessage());
      }
   }

   public TemporaryTopic createTemporaryTopic() throws JMSException {
      return new XBTemporaryTopic();
   }

}
