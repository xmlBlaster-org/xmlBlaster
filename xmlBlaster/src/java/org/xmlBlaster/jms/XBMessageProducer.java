/*------------------------------------------------------------------------------
Name:      XBMessageProducer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishReturnQos;

/**
 * XBMessageProducer
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessageProducer implements MessageProducer {

   private final static String ME = "XBMessageProducer";
   protected I_XmlBlasterAccess access;
   protected Destination destination;
   protected int deliveryMode = DeliveryMode.PERSISTENT;
   protected int priority = 5;
   protected long timeToLive = -1L;
   protected PublishReturnQos publishReturnQos;      
      
   
   XBMessageProducer(I_XmlBlasterAccess access, Destination destination) {
      this.access = access;
      this.destination = destination;
   }

   public void close() throws JMSException {
      // only the administrator should erase the topic
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDeliveryMode()
    */
   public int getDeliveryMode() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDisableMessageID()
    */
   public boolean getDisableMessageID() throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getDisableMessageTimestamp()
    */
   public boolean getDisableMessageTimestamp() throws JMSException {
      // TODO Auto-generated method stub
      return false;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getPriority()
    */
   public int getPriority() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#getTimeToLive()
    */
   public long getTimeToLive() throws JMSException {
      // TODO Auto-generated method stub
      return 0;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDeliveryMode(int)
    */
   public void setDeliveryMode(int arg0) throws JMSException {
      // TODO Auto-generated method stub

   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDisableMessageID(boolean)
    */
   public void setDisableMessageID(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setDisableMessageTimestamp(boolean)
    */
   public void setDisableMessageTimestamp(boolean arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setPriority(int)
    */
   public void setPriority(int arg0) throws JMSException {
      // TODO Auto-generated method stub
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageProducer#setTimeToLive(long)
    */
   public void setTimeToLive(long arg0) throws JMSException {
      // TODO Auto-generated method stub
   }


   public void send(Message message, int deliveryMode, int priority, long timeToLive)  
      throws JMSException {
   }

   public void send(Message message) throws JMSException {
   }

   public void send(Destination dest, Message message)
      throws JMSException {
   }

   public void send(Destination dest, Message message, int deliveryMode, int priority, long timeToLive)
      throws JMSException {
   }

   public Destination getDestination() throws JMSException {
      return this.destination;
   }

}
