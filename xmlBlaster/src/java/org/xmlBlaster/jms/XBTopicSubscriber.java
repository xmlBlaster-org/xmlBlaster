/*------------------------------------------------------------------------------
Name:      XBTopicSubscriber.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.Topic;
import javax.jms.TopicSession;
import javax.jms.TopicSubscriber;

import org.jutils.log.LogChannel;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * XBTopicSubscriber
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class XBTopicSubscriber implements TopicSubscriber, I_Callback {

   private class UpdateThread extends Thread {
      
      private XBMessage msg;
      private XBTopicSubscriber parent;
      
      UpdateThread(XBTopicSubscriber parent, XBMessage msg) {
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

   private final static String ME = "XBTopicSubscriber";
   private Global global;
   private LogChannel log;
   private I_XmlBlasterAccess access;
   private Topic topic;
   private boolean noLocal;
   private String msgSelector;
   private MessageListener msgListener;
   private SubscribeReturnQos subscribeReturnQos;
   private int ackMode;

   XBTopicSubscriber(I_XmlBlasterAccess access, Topic topic, String msgSelector, boolean noLocal, int ackMode) {
      this.access = access;
      this.global = access.getGlobal();
      this.log = this.global.getLog("jms");
      this.topic = topic;
      this.noLocal = noLocal;
      this.msgSelector = msgSelector;
      this.ackMode = ackMode;
   }


   private void subscribe() throws JMSException {
      String oid = this.topic.getTopicName();
      SubscribeKey key = new SubscribeKey(this.global, oid); 
      SubscribeQos qos = new SubscribeQos(this.global);
      qos.setWantInitialUpdate(false);
      qos.setWantLocal(!this.noLocal);
      try {
         this.subscribeReturnQos = this.access.subscribe(key, qos, this);
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
               // case XBMessage.BYTES: msg = new XBBytesMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               case XBMessage.OBJECT: msg = new XBObjectMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               // case XBMessage.MAP: msg = new XBMapMessage(this.global, updateKey.getData(), content, updateQos.getData()); break;
               default: msg = new XBStreamMessage(this.global, updateKey.getData(), content, updateQos.getData());
            }
            
            if (msg != null) {
               if (ackMode == TopicSession.AUTO_ACKNOWLEDGE) {
                  if (this.log.TRACE) this.log.trace(ME, "update: ack mode: AUTO");
                  this.msgListener.onMessage(msg);
                  if (this.log.CALL) this.log.call(ME, "update stop");
                  return "OK";
               }
               else { // start an own thread
                  if (this.log.TRACE) {
                     if (ackMode == TopicSession.CLIENT_ACKNOWLEDGE) this.log.trace(ME, "update: ack mode: CLIENT");
                     else this.log.trace(ME, "update: ack mode: DUPL");
                  } 
                  UpdateThread thread = new UpdateThread(this, msg);
                  synchronized (msg) {
                     if (this.log.TRACE) this.log.trace(ME, "update: starting the thread");
                     thread.start();
                     if (this.log.TRACE) this.log.trace(ME, "update: thread started");
                     msg.wait(); // should it wait until a given timeout ?
                     if (this.ackMode == TopicSession.DUPS_OK_ACKNOWLEDGE || msg.isAcknowledged()) {
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


   public boolean getNoLocal() throws JMSException {
      return this.noLocal;
   }

   public Topic getTopic() throws JMSException {
      return this.topic;
   }

   synchronized public void close() throws JMSException {
      if (this.subscribeReturnQos != null) {
         UnSubscribeKey key = new UnSubscribeKey(this.global, this.topic.getTopicName());
         UnSubscribeQos qos = new UnSubscribeQos(this.global);
         try {
            this.access.unSubscribe(key, qos);
         }
         catch (XmlBlasterException ex) {
            throw XBConnectionFactory.convert(ex, ME + ".close(): ");
         }
      }
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
         subscribe();
      }
      this.msgListener = msgListener;
   }

}
