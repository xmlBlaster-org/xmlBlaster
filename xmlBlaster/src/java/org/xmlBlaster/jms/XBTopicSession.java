/*------------------------------------------------------------------------------
Name:      XBTopicSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Serializable;

import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TemporaryTopic;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

/**
 * XBTopicSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicSession implements TopicSession {

   private final static String ME = "XBTopicSession";
   private Global global;
   private LogChannel log;
   private XBConnection connection;
   private int ackMode;


   XBTopicSession(XBConnection connection, int ackMode) {
      this.connection = connection;
      this.global = connection.getGlobal();
      this.log = this.global.getLog("jms");
      this.ackMode = ackMode;
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createDurableSubscriber(javax.jms.Topic, java.lang.String)
    */
   public TopicSubscriber createDurableSubscriber(Topic arg0, String arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createDurableSubscriber' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createDurableSubscriber(javax.jms.Topic, java.lang.String, java.lang.String, boolean)
    */
   public TopicSubscriber createDurableSubscriber(
      Topic arg0,
      String arg1,
      String arg2,
      boolean arg3)
      throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createDurableSubscriber' not implemented yet");
   }

   public TopicPublisher createPublisher(Topic topic) throws JMSException {
      return new XBTopicPublisher(this.connection.getAccess(), topic);
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createSubscriber(javax.jms.Topic)
    */
   public TopicSubscriber createSubscriber(Topic topic) throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, null, false, this.ackMode);
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createSubscriber(javax.jms.Topic, java.lang.String, boolean)
    */
   public TopicSubscriber createSubscriber(Topic topic, String messageSelector, boolean noLocal)
      throws JMSException {
      return new XBTopicSubscriber(this.connection.getAccess(), topic, messageSelector, noLocal, this.ackMode);
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createTemporaryTopic()
    */
   public TemporaryTopic createTemporaryTopic() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#createTopic(java.lang.String)
    */
   public Topic createTopic(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.TopicSession#unsubscribe(java.lang.String)
    */
   public void unsubscribe(String arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.Session#close()
    */
   public void close() throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.Session#commit()
    */
   public void commit() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'commit' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createBytesMessage()
    */
   public BytesMessage createBytesMessage() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createBytesMessage' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createMapMessage()
    */
   public MapMessage createMapMessage() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createMapMessage' not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createMessage()
    */
   public Message createMessage() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createMessage' not implemented yet");
   }

   public ObjectMessage createObjectMessage() throws JMSException {
      return new XBObjectMessage(this.global, null, null, null);
   }

   public ObjectMessage createObjectMessage(Serializable content)
      throws JMSException {
      ObjectMessage msg = new XBObjectMessage(this.global, null, null, null);
      msg.setObject(content);
      return msg;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createStreamMessage()
    */
   public StreamMessage createStreamMessage() throws JMSException {
      // TODO Auto-generated method stub
      throw new JMSException(ME + " 'createStreamMessage' not implemented yet");
   }

   public TextMessage createTextMessage() throws JMSException {
      return new XBTextMessage(this.global, null, null, null);      
   }

   public TextMessage createTextMessage(String text) throws JMSException {
      return new XBTextMessage(this.global, null, text.getBytes(), null);      
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#getMessageListener()
    */
   public MessageListener getMessageListener() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#getTransacted()
    */
   public boolean getTransacted() throws JMSException {
      // TODO Auto-generated method stub
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

   /* (non-Javadoc)
    * @see java.lang.Runnable#run()
    */
   public void run() {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.Session#setMessageListener(javax.jms.MessageListener)
    */
   public void setMessageListener(MessageListener arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

}
