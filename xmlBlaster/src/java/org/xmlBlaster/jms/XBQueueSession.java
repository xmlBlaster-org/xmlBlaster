/*------------------------------------------------------------------------------
Name:      XBQueueSession.java
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
import javax.jms.Queue;
import javax.jms.QueueBrowser;
import javax.jms.QueueReceiver;
import javax.jms.QueueSender;
import javax.jms.QueueSession;
import javax.jms.StreamMessage;
import javax.jms.TemporaryQueue;
import javax.jms.TextMessage;

/**
 * XBQueueSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBQueueSession implements QueueSession {

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createBrowser(javax.jms.Queue)
    */
   public QueueBrowser createBrowser(Queue arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createBrowser(javax.jms.Queue, java.lang.String)
    */
   public QueueBrowser createBrowser(Queue arg0, String arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createQueue(java.lang.String)
    */
   public Queue createQueue(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue)
    */
   public QueueReceiver createReceiver(Queue arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createReceiver(javax.jms.Queue, java.lang.String)
    */
   public QueueReceiver createReceiver(Queue arg0, String arg1)
      throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createSender(javax.jms.Queue)
    */
   public QueueSender createSender(Queue arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.QueueSession#createTemporaryQueue()
    */
   public TemporaryQueue createTemporaryQueue() throws JMSException {
      // TODO Auto-generated method stub
      return null;
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

   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createBytesMessage()
    */
   public BytesMessage createBytesMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createMapMessage()
    */
   public MapMessage createMapMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createMessage()
    */
   public Message createMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createObjectMessage()
    */
   public ObjectMessage createObjectMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createObjectMessage(java.io.Serializable)
    */
   public ObjectMessage createObjectMessage(Serializable arg0)
      throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createStreamMessage()
    */
   public StreamMessage createStreamMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createTextMessage()
    */
   public TextMessage createTextMessage() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#createTextMessage(java.lang.String)
    */
   public TextMessage createTextMessage(String arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
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
