/*------------------------------------------------------------------------------
Name:      XBMessageConsumer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageConsumer;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

/**
 * XBMessageConsumer
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessageConsumer implements MessageConsumer, MessageListener {

   private final static String ME = "XBMessageConsumer";
   protected Global global;
   protected LogChannel log;
   protected String msgSelector;
   protected MessageListener msgListener;
   protected XBSession session;

   /**
    * 
    * @param access the connection to the xmlBlaster
    * @param destination the destination on which to subscribe
    * @param msgSelector the selector to apply on the destination for this subscription
    * @param noLocal messages sent by this connection should not be received by this 
    * @param ackMode the acknowledge mode to use
    * @param durable true if durable subscription (in jms terms) or false if transient
    */
   XBMessageConsumer(XBSession session, String msgSelector) {
      this.session = session;
      this.global = this.session.global;
      this.log = this.global.getLog("jms");
      this.msgSelector = msgSelector;
   }

   public void close() throws JMSException {
      if (this.session != null) this.session.close();
   }

   public MessageListener getMessageListener() throws JMSException {
      return this.msgListener;
   }

   public String getMessageSelector() throws JMSException {
      return this.msgSelector;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageConsumer#receive()
    */
   public Message receive() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageConsumer#receive(long)
    */
   public Message receive(long arg0) throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   /* (non-Javadoc)
    * @see javax.jms.MessageConsumer#receiveNoWait()
    */
   public Message receiveNoWait() throws JMSException {
      // TODO Auto-generated method stub
      return null;
   }

   synchronized public void setMessageListener(MessageListener msgListener) throws JMSException {
      if (this.msgListener == null) {
         this.msgListener = msgListener;
         if (this.session != null) this.session.setMessageListener(this);
      }
      else this.msgListener = msgListener;
   }

   public void onMessage(Message msg) {
      if (this.msgListener != null) this.msgListener.onMessage(msg);
      // TODO the other stuff like notify receiveNoWait or receive ...
   }

}
