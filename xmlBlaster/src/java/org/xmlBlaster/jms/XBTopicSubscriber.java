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
      
      private XBTopicSubscriber parent;
      private int ackMode;
      private Message msg;
      
      
      UpdateThread(XBTopicSubscriber parent, int ackMode, Message msg) {
         this.parent = parent;
         this.ackMode = ackMode;
         this.msg = msg;
      }
      
      public void run() {
         this.parent.isAck = true;
         if (msg != null) this.parent.msgListener.onMessage(msg);
         this.parent.isAck = false;
         
      }
      
   }

   private boolean isAck;
   private final static String ME = "XBTopicSubscriber";
   private Global global;
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
      this.topic = topic;
      this.noLocal = noLocal;
      this.msgSelector = msgSelector;
      this.ackMode = ackMode;
   }


   private void subscribe() throws JMSException {
      String oid = this.topic.getTopicName();
      SubscribeKey key = new SubscribeKey(this.global, oid); 
      SubscribeQos qos = new SubscribeQos(this.global);
      qos.setWantLocal(!this.noLocal);
      try {
         this.subscribeReturnQos = this.access.subscribe(key, qos, this);
      }
      catch (XmlBlasterException ex) {
         throw XBConnectionFactory.convert(ex, ME + ".subscribe: ");
      }
   }


   synchronized public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
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
            
            if (ackMode == TopicSession.AUTO_ACKNOWLEDGE) {
               if (msg != null) this.msgListener.onMessage(msg);
               return "OK";
            }
            else { // start an own thread
               throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the acknowledge mode has not been implemented yet");         
            }
/*
               case TopicSession.CLIENT_ACKNOWLEDGE  : ex = new JMSException("client acknowledge not implemented yet"); break;
               case TopicSession.DUPS_OK_ACKNOWLEDGE : ex = new JMSException("dups ok acknowledge not implemented yet"); break;
*/
         }
         else {
            throw new XmlBlasterException(this.global, ErrorCode.USER_UPDATE_ERROR, ME + ".update: the message listener has not been assigned yet");         
         }
      }
      catch (Throwable ex) {
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
