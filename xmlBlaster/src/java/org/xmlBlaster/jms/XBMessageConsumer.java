/*------------------------------------------------------------------------------
Name:      XBMessageConsumer.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.IllegalStateException;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.MessageConsumer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.qos.MsgQosData;
import org.xmlBlaster.util.qos.QuerySpecQos;

/**
 * XBMessageConsumer
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBMessageConsumer implements MessageConsumer, I_Callback {

   private String ME = "XBMessageConsumer";
   protected Global global;
   protected LogChannel log;
   protected String msgSelector;
   protected MessageListener msgListener;
   protected XBSession session;
   protected Message msg;
   protected SubscribeReturnQos subscribeReturnQos;
   protected Destination destination;
   protected boolean noLocal;
   protected ExceptionListener exceptionListener;
   protected boolean open = false;
   
   /**
    * For each consumer created, an own xmlBlaster subscription is done since
    * the msgSelector (i.e. in xmlBlaster the mime plugin) could be different from
    * one consumer to another. This is done in the constructor of the MessageConsumer and
    */
   XBMessageConsumer(XBSession session, Destination destination, String msgSelector, boolean noLocal) 
      throws JMSException {
      this.session = session;
      this.noLocal = noLocal;
      this.destination = destination;
      this.global = this.session.global;
      this.log = this.global.getLog("jms");
      this.subscribeReturnQos = subscribe(destination, msgSelector, noLocal);
      this.session.consumerMap.put(this.subscribeReturnQos.getSubscriptionId(), this); 
      this.ME = this.ME + "-" + this.subscribeReturnQos.getSubscriptionId();
      this.open = true;
   }

   protected final void checkIfOpen(String methodName) throws JMSException {
      if (this.log.CALL) this.log.call(ME, methodName);
      if (!this.open)
         throw new IllegalStateException(ME + "." + methodName, "the session has been closed, operation '" + methodName + "' not permitted");
   }

   private final String getOid(Destination destination) throws JMSException {
      String oid = null;
      if (destination instanceof Topic) 
         oid = ((Topic)destination).getTopicName();
      else oid = ((Queue)destination).getQueueName();
      return oid;
   }
   
   private final SubscribeReturnQos subscribe(Destination destination, String msgSelector, boolean noLocal) throws JMSException {
      this.destination = destination;
      String oid = getOid(destination);
      SubscribeKey key = new SubscribeKey(this.global, oid); 
      SubscribeQos qos = new SubscribeQos(this.global);
      qos.setWantInitialUpdate(false);
      qos.setWantLocal(!noLocal);
      // TODO add a mime plugin to handle jms conventions for the messageSelector
      // the code here exists already, but the plugin is not written yet ...
      if (msgSelector != null) {
         AccessFilterQos filterQos = new AccessFilterQos(this.global, "JmsMessageSelector", "1.0", msgSelector);
         qos.addAccessFilter(filterQos);
      }
      // qos.setPersistent(durable);
      try {
         return this.global.getXmlBlasterAccess().subscribe(key, qos, this);
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".subscribe: ");
      }
   }

   /**
    * unsubscribe here
    */
   synchronized public void close() throws JMSException {
      if (!this.open) return;
      try {
         if (this.log.CALL) this.log.call(ME, "close");
         String subId = this.subscribeReturnQos.getSubscriptionId();
         UnSubscribeKey key = new UnSubscribeKey(this.global, subId);
         UnSubscribeQos qos = new UnSubscribeQos(this.global);
         this.global.getXmlBlasterAccess().unSubscribe(key, qos);
         this.session.consumerMap.remove(subId); 
         this.msgListener = null;
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".close");
      }
   }

   public MessageListener getMessageListener() throws JMSException {
      this.session.checkControlThread();
      return this.msgListener;
   }

   public String getMessageSelector() throws JMSException {
      this.session.checkControlThread();
      return this.msgSelector;
   }

   public Message receive() throws JMSException {
      return receive(-1L);
   }

   // TODO acknowledge of synchronous messages
   // TODO implement in the I_Queue a search method (to allow to filter from a 
   // callback queue only messages coming from a given subscription
   public Message receive(long delay) throws JMSException {
      checkIfOpen("receive");
      this.session.checkControlThread();
      if (this.session.getSyncMode() == XBSession.MODE_ASYNC)
         throw new IllegalStateException(ME + ".receive: you have set a messageListener for this session so synchronous message consumption is currently not allowed");
      try {
         this.session.setSyncMode(XBSession.MODE_SYNC); // to disallow invocation of updates 
         // query with a given GetQos ...         
         // TODO cache the queryKey here 
         GetQos getQos = new GetQos(this.global);
         QuerySpecQos querySpecQos = new QuerySpecQos(this.global, "QueueQuery", "1.0", "maxEntries=1&maxSize=-1&consumable=true&waitingDelay=" + delay + "&subscriptionId=" + this.subscribeReturnQos.getSubscriptionId());
         getQos.addQuerySpec(querySpecQos);
         String getOid = "__cmd:" + this.session.sessionName + "/?cbQueueEntries";
         MsgUnit[] mu = this.global.getXmlBlasterAccess().get(new GetKey(this.global, getOid), getQos);
         if (mu == null || mu.length < 1 || mu[0] == null) return null;
         return buildMessage((MsgKeyData)mu[0].getKeyData(), mu[0].getContent(), (MsgQosData)mu[0].getQosData());
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".receive");
      }
     finally {
         this.session.setSyncMode(XBSession.MODE_UNSET);
      }
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
   public Message receiveNoWait() throws JMSException {
      return receive(0L);
   }

   public void setMessageListener(MessageListener msgListener) throws JMSException {
      checkIfOpen("setMessageListener");
      this.session.checkControlThread();
      this.session.setSyncMode(XBSession.MODE_ASYNC);
      synchronized (this.session) {
         try {
            if (this.session.connectionActivated) 
               this.session.activateDispatcher(true);
         }
         catch (XmlBlasterException ex) {
            throw new XBException(ex, ME + ".setMessageListener");
         }
      }
      this.msgListener = msgListener;
   }

   private Message buildMessage(MsgKeyData msgKeyData, byte[] content, MsgQosData msgQosData) 
      throws JMSException {
      int type = msgQosData.getClientProperty(XBPropertyNames.JMS_MESSAGE_TYPE, XBMessage.STREAM);
      XBMessage msg;
      switch (type) {
         case XBMessage.TEXT  : msg = new XBTextMessage(this.session, msgKeyData, content, msgQosData); break;
         case XBMessage.BYTES : msg = new XBBytesMessage(this.session, msgKeyData, content, msgQosData); break;
         case XBMessage.OBJECT: msg = new XBObjectMessage(this.session, msgKeyData, content, msgQosData); break;
         case XBMessage.MAP   : msg = new XBMapMessage(this.session, msgKeyData, content, msgQosData); break;
         default: msg = new XBStreamMessage(this.session, msgKeyData, content, msgQosData);
      }
      return msg;
   }
   
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "update cbSessionId='" + cbSessionId + "' oid='" + updateKey.getOid() + "'");
      try {
         if (this.msgListener != null) {
            Message msg = buildMessage(updateKey.getData(), content, updateQos.getData()); 
 
            int ackMode = this.session.getAcknowledgeMode();
            if (this.log.TRACE) this.log.trace(ME, "update: acknowledge mode is: " + ackMode);
            if (msg != null) {
               XBMsgEvent msgEvent = new XBMsgEvent(this.msgListener, msg);
               this.session.channel.put(msgEvent);

               // for the other modes the difference is made in the run() of the session
               if (ackMode != Session.AUTO_ACKNOWLEDGE) {
                  synchronized (this.session) {
                     long timeout = this.session.getUpdateTimeout();
                     if (timeout > 0) {
                        long t0 = System.currentTimeMillis();
                        if (this.log.TRACE) this.log.trace(ME, "update: waiting for ack");
                        this.session.wait(timeout);
                        if (this.log.TRACE) this.log.trace(ME, "update: waked up from ack");
                        long dt = System.currentTimeMillis() - t0;
                        if (dt >= timeout) {
                           if (this.exceptionListener != null) {
                              this.exceptionListener.onException(new XBException(ME + ".update", "timeout of '" + timeout + "' ms occured when waiting for acknowledge of msg '" + msg.getJMSMessageID() + "'"));
                           }
                           throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update timeout of '" + timeout + "' ms occured when waiting for acknowledge of msg '" + msg.getJMSMessageID() + "'");
                        }   
                     }
                     else
                        this.session.wait(timeout);
                  }
               }
               else {
                  if (this.log.TRACE) this.log.trace(ME, "update: acknowledge mode is AUTO: no waiting for user acknowledge");
                  msg.acknowledge();
               }
            }
            else {
               throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the message was null");         
            }
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the message listener has not been assigned yet");         
         }
         return "OK";
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update");         
      }
   }
}
