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
   protected boolean listenerStarted;
   protected boolean waitingForReceive;
   protected Message msg;
   
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
   
   final protected synchronized void startToListen() throws JMSException {
      if (this.listenerStarted) return;
      if (this.session != null) this.session.setMessageListener(this);
      this.listenerStarted = true;;
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

   public Message receive() throws JMSException {
      return receive(-1L);
   }

   synchronized public Message receive(long delay) throws JMSException {
      startToListen();
      this.waitingForReceive = true;
      try {
         if (delay > -1L) wait(delay);
         else wait();
      }
      catch (InterruptedException ex) {
      }
      this.waitingForReceive = false;
      return this.msg;
   }

   /**
    * Currently the implementation is such that if no msgListener has been
    * associated to this consumer, the onMessage blocks until receiveNoWait has
    * been invoked (if there is a message pending). This has the disadvantage
    * of blocking subscriptions of other sessions (or subscriptions on 
    * other topics). Using the get() method of XmlBlasterAccess would always
    * return the last message (which is not wanted here).
    * TODO we would need something as 'noInitialUpdates' for the getQos. 
    */
   synchronized public Message receiveNoWait() throws JMSException {
      startToListen();
      if (this.waitingForReceive) return null;
      notify();
      return this.msg;
   }

   synchronized public void setMessageListener(MessageListener msgListener) throws JMSException {
      if (this.msgListener == null) {
         this.msgListener = msgListener;
         startToListen();
      }
      else this.msgListener = msgListener;
   }

   public void onMessage1(Message msg) {
      if (this.msgListener != null) {
         this.msgListener.onMessage(msg);
      }
   }

   public void onMessage(Message msg) {
      this.msg = msg;
      if (this.waitingForReceive) {
         synchronized (this) {
            if (this.waitingForReceive) notify();
         }
      }
      else {
         if (this.msgListener != null) {
            this.msgListener.onMessage(this.msg);
         }
         else { // TODO: see comments on receiveNoWait 
            synchronized (this) {
               if (this.msgListener != null) return;
               try {
                  wait();
               }
               catch (InterruptedException ex) {
               }
            }   
         } 
      }
      this.msg = null;
   }

}
