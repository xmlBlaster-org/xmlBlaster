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
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * XBSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBSession implements Session, I_Callback {

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
   protected Destination destination;
   protected boolean noLocal = noLocalDefault;
   protected String msgSelector = msgSelectorDefault;
   protected SubscribeReturnQos subscribeReturnQos;
   protected boolean durable = durableDefault;


   private class UpdateThread extends Thread {
      private XBMessage msg;
      private XBSession parent;
      
      UpdateThread(XBSession parent, XBMessage msg) {
         super("update-thread");
         // setDaemon(true);
         this.parent = parent;
         this.msg = msg;
      }
      
      public void run() {
         if (this.msg != null) {
            // synchronized(this.msg) {
               if (this.parent.log.CALL) this.parent.log.call("UpdateThread.run()", "start");
               this.parent.msgListener.onMessage(this.msg);
               if (this.parent.log.CALL) this.parent.log.call("UpdateThread.run()", "end");
            // }
         }
      }
   }

   XBSession(XBConnection connection, int ackMode, boolean transacted) {
      this.connection = connection;
      this.global = connection.getGlobal();
      this.log = this.global.getLog("jms");
      this.ackMode = ackMode;
      this.durableSubscriptionMap = new HashMap();
      this.open = true;
      this.transacted = transacted;
   }

   private void subscribe() throws JMSException {
      String oid = null;
      if (this.destination instanceof Topic) 
         oid = ((Topic)this.destination).getTopicName();
      SubscribeKey key = new SubscribeKey(this.global, oid); 
      SubscribeQos qos = new SubscribeQos(this.global);
      qos.setWantInitialUpdate(false);
      qos.setWantLocal(!this.noLocal);
      qos.setPersistent(this.durable);
      try {
         this.subscribeReturnQos = this.connection.getAccess().subscribe(key, qos, this);
      }
      catch (XmlBlasterException ex) {
         throw XBConnectionFactory.convert(ex, ME + ".subscribe: ");
      }
   }

   synchronized public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "update cbSessionId='" + cbSessionId + "' oid='" + updateKey.getOid() + "'");
      try {
         if (this.msgListener != null) {
            int type = XBMessage.STREAM;
            try {
               String val = (String)updateQos.getData().getClientProperties().get("jmsMessageType");
               if (val != null) type = Integer.parseInt(val);
            }
            catch (Exception e) {
            }
            
            XBMessage msg = null;
            switch (type) {
               case XBMessage.TEXT: msg = new XBTextMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               case XBMessage.BYTES: msg = new XBBytesMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               case XBMessage.OBJECT: msg = new XBObjectMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               case XBMessage.MAP: msg = new XBMapMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               default: msg = new XBStreamMessage(this.global, updateKey.getData(), content, updateQos.getData());
            }
            
            if (msg != null) {
               if (this.ackMode == Session.AUTO_ACKNOWLEDGE) {
                  if (this.log.TRACE) this.log.trace(ME, "update: ack mode: AUTO");
                  this.msgListener.onMessage(msg);
                  if (this.log.CALL) this.log.call(ME, "update stop");
                  return "OK";
               }
               else { // start an own thread
                  if (this.log.TRACE) {
                     if (this.ackMode == Session.CLIENT_ACKNOWLEDGE) this.log.trace(ME, "update: ack mode: CLIENT");
                     else this.log.trace(ME, "update: ack mode: DUPL");
                  } 
                  UpdateThread thread = new UpdateThread(this, msg);
                  synchronized (msg) {
                     if (this.log.TRACE) this.log.trace(ME, "update: starting the thread");
                     thread.start();
                     if (this.log.TRACE) this.log.trace(ME, "update: thread started");
                     msg.wait(); // should it wait until a given timeout ?
                     if (this.ackMode == Session.DUPS_OK_ACKNOWLEDGE || msg.isAcknowledged()) {
                        if (this.log.CALL) this.log.call(ME, "update stop");
                        return "OK";
                     } 
                     throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the acknowledge mode has not been invoked");         
                  }
               }
            }
            else {
               throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the message was null");         
            }
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the message listener has not been assigned yet");         
         }
      }
      catch (Throwable ex) {
         ex.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update");         
      }
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
      this.destination = topic;
      this.noLocal = noLocal;
      TopicSubscriber sub = new XBTopicSubscriber(this, msgSelector);
      this.durableSubscriptionMap.put(name, sub);
      return sub;
   }

   synchronized public void close() throws JMSException {
      this.open = true;
      if (this.subscribeReturnQos != null) {
         String oid = null;
         if (this.destination instanceof Topic) 
            oid = ((Topic)this.destination).getTopicName();
         UnSubscribeKey key = new UnSubscribeKey(this.global, oid);
         UnSubscribeQos qos = new UnSubscribeQos(this.global);
         try {
            this.connection.getAccess().unSubscribe(key, qos);
         }
         catch (XmlBlasterException ex) {
            throw XBConnectionFactory.convert(ex, ME + ".close(): ");
         }
      }
   }

   public void commit() throws JMSException {
      checkIfOpen("commit");
      checkIfTransacted("commit");      
      // TODO Auto-generated method stub
      this.log.warn(ME, "transacted sessions not implemented");
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
      subscribe();
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
      this.destination = destination;
      return new XBMessageConsumer(this, this.msgSelectorDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector)
      throws JMSException { 
      checkIfOpen("createConsumer");
      this.destination = destination;
      return new XBMessageConsumer(this, msgSelector); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector, boolean noLocal)
      throws JMSException {
      checkIfOpen("createConsumer");
      this.destination = destination;
      this.noLocal = noLocal;
      return new XBMessageConsumer(this, msgSelector); 
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
