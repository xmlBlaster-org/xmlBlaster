/*------------------------------------------------------------------------------
Name:      XBSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.ExceptionListener;
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
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;

import EDU.oswego.cs.dl.util.concurrent.Channel;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;


/**
 * XBSession
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBSession extends Thread implements Session, I_Callback {

   final static int MODE_UNSET = 0;
   final static int MODE_ASYNC = 1;
   final static int MODE_SYNC = 2;
   
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
   protected int syncMode = MODE_UNSET;
   protected long updateTimeout = 60000L;
   protected Map consumerMap;
   
   /**
    * Set in the constructor it can be changed in the update methods 
    * (also of the message consumers) only.
    */
   protected Thread controlThread;
   protected ConnectQos connectQos;
   protected String sessionName;
   private I_StatusChangeListener statusChangeListener;
   protected ExceptionListener exceptionListener;
   protected boolean connectionActivated;
   protected Channel channel;
   private boolean started;
   
   /**
    * This constructor extracts the global from the ConnectQos. Note that 
    * you need to clone the ConnectQos before passing it to this constructor.
    * global contained in the connectQos.
    * 
    * @param connectQos is the connectQos to be used for this session. It is an own instance and can not be null.
    */
   XBSession(ConnectQos connectQos, int ackMode, boolean transacted) {
      this.connectQos = connectQos;
      this.global = connectQos.getData().getGlobal();
      this.log = this.global.getLog("jms");
      if (this.log.CALL) this.log.call(ME, "constructor");
      this.ackMode = ackMode;
      this.durableSubscriptionMap = new HashMap();
      this.open = true;
      this.transacted = transacted;
      this.controlThread = Thread.currentThread();
      this.channel = new LinkedQueue();
      this.consumerMap = new HashMap();
   }

   /**
    * This constructor is used if you want to pass a Global which has already
    * done some work (connected) on the I_XmlBlasterAccess. Caution, you will not
    * be able to connect and disconnect if you use this constructor.
    * 
    * @param global
    * @param ackMode
    * @param transacted
    */
   public XBSession(Global global, int ackMode, boolean transacted) {
      this.global = global;
      this.log = this.global.getLog("jms");
      if (this.log.CALL) 
         this.log.call(ME, "constructor");
      this.ackMode = ackMode;
      this.durableSubscriptionMap = new HashMap();
      this.open = true;
      this.transacted = transacted;
      this.controlThread = Thread.currentThread();
      this.channel = new LinkedQueue();
      this.consumerMap = new HashMap();
      this.syncMode = MODE_ASYNC;
   }

   /**
    * registeres the listener about status changes. In general this listener is the
    * owner XBConnection since it needs to be notified everytime one of its sessions 
    * is closing. Care must be used when invoking since this is not synchronized.
    * @param statusChangeListener
    */
   void setStatusChangeListener(I_StatusChangeListener statusChangeListener) {
      if (this.log.CALL) this.log.call(ME, "setStatusChangeListener");
      if (statusChangeListener == null) {
         this.statusChangeListener = null;
      }
      else {
         if (statusChangeListener == this.statusChangeListener) return;
         //synchronized(statusChangeListener) {
            this.statusChangeListener = statusChangeListener;
         //}
      }
   }

   /**
    * Activates or deactivates the dispatcher associated to this session.
    * It does it only if the Session is in ASYNC Mode.
    * @param doActivate
    */
   final synchronized void activateDispatcher(boolean doActivate) throws XmlBlasterException {
      if (this.log.CALL) this.log.call(ME, "activateDispatcher '" + doActivate + "'");
      // only activate if already in asyc mode, i.e. if there is at least 
      // one msgListener associated to this session
      boolean realDoActivate = (doActivate && (this.syncMode == MODE_ASYNC));
      String oid = "__cmd:" + this.sessionName + "/?dispatcherActive=" + realDoActivate; 
      PublishQos qos = new PublishQos(this.global);
      PublishKey key = new PublishKey(this.global, oid);
      this.global.getXmlBlasterAccess().publish(new MsgUnit(key, (byte[])null, qos));
      this.connectionActivated = realDoActivate;
   }
   
   /**
    * 
    * @return a string containing the complete session name.
    * @throws JMSException
    */
   String connect() throws JMSException {
      if (this.log.CALL) this.log.call(ME, "connect");
      try {
         // TODO add a flag to the connectQos to inhibit the dispatcher already when connecting
         this.sessionName = this.global.getXmlBlasterAccess().connect(this.connectQos, this).getSessionName().getRelativeName();
         activateDispatcher(false);
         return this.sessionName;
      }
      catch (XmlBlasterException ex) {
         throw new XBException(ex, ME + ".connect");   
      }
   }
   
   protected final void checkIfOpen(String methodName) {
      if (!this.open)
         throw new IllegalStateException(ME + " the session has been closed, operation '" + methodName + "' not permitted");
   }

   protected final void checkIfTransacted(String methodName) throws JMSException {
      if (!this.transacted)
         throw new JMSException(ME, "the session is not transacted, operation '" + methodName + "' not permitted");
   }

   final void checkControlThread() throws JMSException {
      if (this.controlThread == null || this.controlThread == Thread.currentThread()) return;
         throw new JMSException(ME, "the session must be used within the same thread");
   }
   
   public TopicSubscriber createDurableSubscriber(Topic topic, String name)
      throws JMSException {
      return createDurableSubscriber(topic, name, this.msgSelectorDefault, this.noLocalDefault);
   }

   public TopicSubscriber createDurableSubscriber(Topic topic, String name, String msgSelector, boolean noLocal)
      throws JMSException {
      if (this.log.CALL) this.log.call(ME, "createDurableSubscriber '" + name + "' msgSelector=" + msgSelector + "' noLocal='" + noLocal + "'");
      checkIfOpen("createDurableSubscriber");
      checkControlThread();
      TopicSubscriber sub = new XBTopicSubscriber(this, topic, msgSelector, noLocal);
      this.durableSubscriptionMap.put(name, sub);
      return sub;
   }

   /**
    * It disconnects from xmlBlaster and deregisters from its XBConnection 
    */
   public void close() throws JMSException {
      if (this.log.CALL) this.log.call(ME, "close");
      if (this.statusChangeListener != null)
         this.statusChangeListener.statusPreChanged(this.sessionName, I_StatusChangeListener.RUNNING, I_StatusChangeListener.CLOSED);
      try {
         synchronized (this) {
            try {
               this.open = false;
               Object[] keys = this.consumerMap.keySet().toArray();
               if (this.log.TRACE) this.log.trace(ME, "close: going to close '" + keys.length + "' consumers too");
               for (int i=0; i < keys.length; i++) {
                  MessageConsumer consumer = (MessageConsumer)this.consumerMap.get(keys[i]);
                  if (consumer != null) consumer.close();
               }
               this.consumerMap.clear();
               this.global.getXmlBlasterAccess().disconnect(new DisconnectQos(this.global));
            }
            finally { // to avoid thread leak 
               if (this.syncMode == MODE_ASYNC) {
                  if (this.log.TRACE) this.log.trace(ME, "close: shutting down the running thread by sending a null message to its channel");
                  XBMsgEvent event = new XBMsgEvent(null, null);
                  try {
                     this.channel.put(event);
                  }
                  catch (InterruptedException ex) {
                     ex.printStackTrace();
                  }
               }
            }
         }
      }
      finally {
         if (this.statusChangeListener != null) 
            this.statusChangeListener.statusPostChanged(this.sessionName, I_StatusChangeListener.RUNNING, I_StatusChangeListener.CLOSED);
      }
   }

   public void commit() throws JMSException {
      checkIfOpen("commit");
      checkIfTransacted("commit");      
      checkControlThread();
      this.log.warn(ME, "transacted sessions not implemented");
   }

   public BytesMessage createBytesMessage() throws JMSException {
      checkIfOpen("createBytesMessage");
      checkControlThread();
      return new XBBytesMessage(this, null);
   }

   public MapMessage createMapMessage() throws JMSException {
      checkIfOpen("createMapMessage");
      checkControlThread();
      return new XBMapMessage(this, null);
   }

   public Message createMessage() throws JMSException {
      checkIfOpen("createMessage");
      checkControlThread();
      return new XBMessage(this, null, XBMessage.DEFAULT_TYPE);
   }

   public ObjectMessage createObjectMessage() throws JMSException {
      checkIfOpen("createObjectMessage");
      checkControlThread();
      return new XBObjectMessage(this, null);
   }

   public ObjectMessage createObjectMessage(Serializable content) throws JMSException {
      checkIfOpen("createObjectMessage");
      checkControlThread();
      ObjectMessage msg = createObjectMessage(); 
      msg.setObject(content);
      return msg;
   }

   public StreamMessage createStreamMessage() throws JMSException {
      checkIfOpen("createStreamMessage");
      checkControlThread();
      return new XBStreamMessage(this, null);
   }

   public XBStreamingMessage createStreamingMessage() throws JMSException {
      checkIfOpen("createStreamingMessage");
      checkControlThread();
      return new XBStreamingMessage(this, null);
      // return new XBTextMessage(this, null, null, null);      
   }

   public TextMessage createTextMessage() throws JMSException {
      checkIfOpen("createTextMessage");
      checkControlThread();
      return new XBTextMessage(this, null);      
   }

   public TextMessage createTextMessage(String text) throws JMSException {
      checkIfOpen("createTextMessage");
      checkControlThread();
      return new XBTextMessage(this, text.getBytes());      
   }

   public MessageListener getMessageListener() throws JMSException {
      checkIfOpen("getMessageListener");
      checkControlThread();
      return this.msgListener;
   }

   public boolean getTransacted() throws JMSException {
      checkIfOpen("getTransacted");
      checkControlThread();
      return this.transacted;
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#recover()
    */
   public void recover() throws JMSException {
      checkIfOpen("recover");
      checkIfTransacted("recover");      
      checkControlThread();
      this.log.warn(ME, "transacted sessions not implemented yet");
   }

   /* (non-Javadoc)
    * @see javax.jms.Session#rollback()
    */
   public void rollback() throws JMSException {
      checkIfOpen("rollback");
      checkIfTransacted("rollback");      
      checkControlThread();
      this.log.warn(ME, "transacted sessions not implemented yet");
   }

   public void run() {
      if (this.started) {
         this.log.info(ME, "run: session was already started, not doing anything");
         return;
      }
      this.started = true;
      this.log.info(ME, "run: session started");
      this.controlThread = Thread.currentThread();
      while (true) {
         try {
            XBMsgEvent msgEvent = (XBMsgEvent)this.channel.take();
            Message msg = msgEvent.getMessage();
            if (msg == null) {
               this.log.info(ME, "Shutting down the running thread since close event received");
               break;
            }
            msgEvent.getListener().onMessage(msg);
            // TODO notify the update thread waiting for ACK
            if (this.log.TRACE) this.log.trace(ME, "run: msg='" + msg.getJMSMessageID() + "' ack='" + this.ackMode + "'");
            if (this.ackMode == Session.DUPS_OK_ACKNOWLEDGE) 
               msg.acknowledge();
         }
         catch (InterruptedException ex) {
            this.log.error(ME, "run InterruptedException occured " + ex.getMessage());
            ex.printStackTrace();
         }
         catch (Throwable ex) {
            this.log.error(ME, "run a Throwable occured " + ex.getMessage());
            ex.printStackTrace();
         }
      }
   }
   
   public void setMessageListener(MessageListener msgListener) throws JMSException {
      checkIfOpen("setMessageListener");
      checkControlThread();
      this.log.warn(ME, "setMessageListener not implemented");
      if (this.log.TRACE) Thread.dumpStack();
      this.msgListener = msgListener;
   }

   public Queue createQueue(String name) throws JMSException {
      checkIfOpen("createQueue");
      checkControlThread();
      return new XBQueue(name);
   }

   public QueueBrowser createBrowser(Queue queue) throws JMSException {
      checkIfOpen("createBrowser");
      checkControlThread();
      return new XBQueueBrowser(queue, null);
   }

   public QueueBrowser createBrowser(Queue queue, String msgSelector)
      throws JMSException {
      checkIfOpen("createBrowser");
      checkControlThread();
      return new XBQueueBrowser(queue, msgSelector);
   }

   public TemporaryQueue createTemporaryQueue() throws JMSException {
      checkIfOpen("createTemporaryQueue");
      checkControlThread();
      return new XBTemporaryQueue();
   }

   public int getAcknowledgeMode() throws JMSException {
      checkIfOpen("getAcknowledgeMode");
      return this.ackMode;
   }

   public MessageProducer createProducer(Destination destination)
      throws JMSException {
      checkIfOpen("createProducer");
      checkControlThread();
      return new XBMessageProducer(this, destination);
   }

   /**
    * For each consumer created, an own xmlBlaster subscription is done since
    * the msgSelector (i.e. in xmlBlaster the mime plugin) could be different from
    * one consumer to another. This is done in the constructor of the MessageConsumer and 
    */
   public MessageConsumer createConsumer(Destination destination)
      throws JMSException {
      checkIfOpen("createConsumer");
      checkControlThread();
      return new XBMessageConsumer(this, destination, this.msgSelectorDefault, this.noLocalDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector)
      throws JMSException { 
      checkIfOpen("createConsumer");
      checkControlThread();
      return new XBMessageConsumer(this, destination, msgSelector,  this.noLocalDefault); 
   }

   public MessageConsumer createConsumer(Destination destination, String msgSelector, boolean noLocal)
      throws JMSException {
      checkIfOpen("createConsumer");
      checkControlThread();
      return new XBMessageConsumer(this, destination, msgSelector, noLocal); 
   }

   public Topic createTopic(String name) throws JMSException {
      checkIfOpen("createTopic");
      checkControlThread();
      return new XBDestination(name, null, false);
   }

   public void unsubscribe(String subName) throws JMSException {
      if (this.log.CALL) this.log.call(ME, "unsubscribe '" + subName + "'");
      checkIfOpen("unsubscribe");
      checkControlThread();
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
      checkControlThread();
      return new XBTemporaryTopic();
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      this.log.error(ME, "update: should never be invoked ...");      
      return "OK";
   }


   /**
    * @return Returns the asyncMode.
    */
   int getSyncMode() {
      return this.syncMode;
   }
   /**
    * @param asyncMode The asyncMode to set. This method starts the 
    * runner thread if not running yet in case async is set.
    */
   void setSyncMode(int asyncMode) {
      this.syncMode = asyncMode;
      if (asyncMode == XBSession.MODE_ASYNC && !this.started) start();
   }
   
   void setControlThread(Thread controlThread) {
      this.controlThread = controlThread;
   }
   
   long getUpdateTimeout() {
      return this.updateTimeout;
   }
   
}
