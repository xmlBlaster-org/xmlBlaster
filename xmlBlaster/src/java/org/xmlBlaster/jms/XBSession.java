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
   protected final boolean noLocalDefault = false; // is this conform to jms ?
   protected final boolean durableDefault = false;   
   protected final String msgSelectorDefault = null;
   protected MessageListener msgListener;
   protected HashMap durableSubscriptionMap;
   protected boolean open;
   protected boolean transacted;

   XBSession(XBConnection connection, int ackMode, boolean transacted) {
      this.connection = connection;
      this.global = connection.getGlobal();
      this.log = this.global.getLog("jms");
      this.ackMode = ackMode;
      this.durableSubscriptionMap = new HashMap();
      this.open = true;
      this.transacted = transacted;
   }

   protected void checkIfOpen(String methodName) throws JMSException {
      if (!this.open)
         throw new JMSException(ME, "the session has been closed, operation '" + methodName + "' not permitted");
   }

   protected void checkIfTransacted(String methodName) throws JMSException {
      if (!this.transacted)
         throw new JMSException(ME, "the session is not transacted, operation '" + methodName + "' not permitted");
   }

   public TopicSubscriber createDurableSubscriber(Topic topic, String name)
      throws JMSException {
      checkIfOpen("createDurableSubscriber");
      return createDurableSubscriber(topic, name, this.msgSelectorDefault, this.noLocalDefault);
   }

   public TopicSubscriber createDurableSubscriber(Topic topic, String name, String msgSelector, boolean noLocal)
      throws JMSException {
      checkIfOpen("createDurableSubscriber");
      TopicSubscriber sub = new XBTopicSubscriber(this.connection.getAccess(), topic, msgSelector, noLocal, this.ackMode, true);
      this.durableSubscriptionMap.put(name, sub);
      return sub;
   }

   public void close() throws JMSException {
      this.open = true;
   }

   public void commit() throws JMSException {
      checkIfOpen("commit");
      checkIfTransacted("commit");      
      // TODO Auto-generated method stub
      this.log.warn(ME, "transacted sessions not implemented yet");
   }

   public BytesMessage createBytesMessage() throws JMSException {
      checkIfOpen("createBytesMessage");
      return new XBBytesMessage(this.global, null, null, null);
   }

   public MapMessage createMapMessage() throws JMSException {
      checkIfOpen("createMapMessage");
      return new XBMapMessage(this.global, null, null, null);
   }

   public Message createMessage() throws JMSException {
      checkIfOpen("createMessage");
      return new XBMessage(this.global, null, null, null, XBMessage.DEFAULT_TYPE);
   }

   public ObjectMessage createObjectMessage() throws JMSException {
      checkIfOpen("createObjectMessage");
      return new XBObjectMessage(this.global, null, null, null);
   }

   public ObjectMessage createObjectMessage(Serializable content)
      throws JMSException {
      checkIfOpen("createObjectMessage");
      ObjectMessage msg = createObjectMessage(); 
      msg.setObject(content);
      return msg;
   }

   public StreamMessage createStreamMessage() throws JMSException {
      checkIfOpen("createStreamMessage");
      return new XBStreamMessage(this.global, null, null, null);
   }

   public TextMessage createTextMessage() throws JMSException {
      checkIfOpen("createTextMessage");
      return new XBTextMessage(this.global, null, null, null);      
   }

   public TextMessage createTextMessage(String text) throws JMSException {
      checkIfOpen("createTextMessage");
      return new XBTextMessage(this.global, null, text.getBytes(), null);      
   }

   public MessageListener getMessageListener() throws JMSException {
      checkIfOpen("getMessageListener");
      return this.msgListener;
   }

   public boolean getTransacted() throws JMSException {
      checkIfOpen("getTransacted");
      return this.transacted;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#recover()
    */
   public void recover() throws JMSException {
      checkIfOpen("recover");
      checkIfTransacted("recover");      
      // TODO Auto-generated method stub
      this.log.warn(ME, "transacted sessions not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#rollback()
    */
   public void rollback() throws JMSException {
      checkIfOpen("rollback");
      checkIfTransacted("rollback");      
      // TODO Auto-generated method stub
      this.log.warn(ME, "transacted sessions not implemented yet");
   }

   public void run() {
   }

   public void setMessageListener(MessageListener msgListener) throws JMSException {
      checkIfOpen("setMessageListener");
      this.msgListener = msgListener;
   }

   public Queue createQueue(String name) throws JMSException {
      checkIfOpen("createQueue");
      return new XBQueue(name);
   }

   public QueueBrowser createBrowser(Queue queue) throws JMSException {
      checkIfOpen("createBrowser");
      return new XBQueueBrowser(queue, null);
   }

   public QueueBrowser createBrowser(Queue queue, String msgSelector)
      throws JMSException {
      checkIfOpen("createBrowser");
      return new XBQueueBrowser(queue, msgSelector);
   }

   public TemporaryQueue createTemporaryQueue() throws JMSException {
      checkIfOpen("createTemporaryQueue");
      return new XBTemporaryQueue();
   }

   public int getAcknowledgeMode() throws JMSException {
      checkIfOpen("getAcknowledgeMode");
      return this.ackMode;
   }

   public MessageProducer createProducer(Destination destination)
      throws JMSException {
      checkIfOpen("createProducer");
      return new XBMessageProducer(this.connection.getAccess(), destination);
   }

   public MessageConsumer createConsumer(Destination destination)
      throws JMSException {
      checkIfOpen("createConsumer");
      return new XBMessageConsumer(this.connection.getAccess(), destination, this.msgSelectorDefault, this.noLocalDefault, this.ackMode, this.durableDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector)
      throws JMSException { 
      checkIfOpen("createConsumer");
      return new XBMessageConsumer(this.connection.getAccess(), destination, msgSelector, this.noLocalDefault, this.ackMode, this.durableDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector, boolean noLocal)
      throws JMSException {
      checkIfOpen("createConsumer");
      return new XBMessageConsumer(this.connection.getAccess(), destination, msgSelector, noLocal, this.ackMode, this.durableDefault); 
   }

   public Topic createTopic(String name) throws JMSException {
      checkIfOpen("createTopic");
      return new XBTopic(name);
   }

   public void unsubscribe(String subName) throws JMSException {
      checkIfOpen("unsubscribe");
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
      checkIfOpen("createTemporaryTopic");
      return new XBTemporaryTopic();
   }

}
