/*------------------------------------------------------------------------------
Name:      MessageHelper.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.jms;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Enumeration;
import java.util.logging.Logger;

import javax.jms.BytesMessage;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.ObjectMessage;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;

import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.key.MsgKeyData;
import org.xmlBlaster.util.key.MsgKeySaxFactory;
import org.xmlBlaster.util.qos.MsgQosData;

/**
 * MessageHelper.
 * 
 * Implementation details about how 
 *
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * 
 */
public class MessageHelper { 

   private static Logger log = Logger.getLogger(MessageHelper.class.getName());
   
   public MessageHelper() {
   }
   
   public static MsgUnit convert(Global global, Message msg) throws JMSException, XmlBlasterException, IOException {
      // first strip all qos properties which are specific
      Destination dest = msg.getJMSDestination();
      if (!(dest instanceof XBDestination))
         throw new JMSException("destination is not an xmlblaster destination. Do not know how to handle it");
      PublishQos qos = null;
      PublishKey key = null;
      if (dest != null) {
         XBDestination xbDest = (XBDestination)dest;
         String ptpName = xbDest.getQueueName(); 
         if (ptpName != null) {
            org.xmlBlaster.util.qos.address.Destination ptpDest = new org.xmlBlaster.util.qos.address.Destination(global, new SessionName(global, ptpName));
            if (xbDest.getForceQueuing())
               ptpDest.forceQueuing(true);
            qos = new PublishQos(global, ptpDest);
         }
         else 
            qos = new PublishQos(global);
         String tmp = xbDest.getTopicName();
         if (tmp == null)
            throw new JMSException("A Topic must be specified in the message to be sent");

         // determine if it is a complete key 
         if (tmp.indexOf('<') > -1) { // complete key
            MsgKeySaxFactory keyFactory = new MsgKeySaxFactory(global);
            key = new PublishKey(global, keyFactory.readObject(tmp));
         }
         else // then it is a simple oid
            key = new PublishKey(global, tmp);
      }
      else
         throw new JMSException("A destination must be specified in the message to be sent");
      
      String corrId = msg.getJMSCorrelationID();
      qos.addClientProperty(XBPropertyNames.JMS_CORRELATION_ID, corrId);
      int deliveryMode = msg.getJMSDeliveryMode();
      if (deliveryMode == DeliveryMode.PERSISTENT)
         qos.setPersistent(true);
      long expiration = msg.getJMSExpiration();
      qos.setLifeTime(expiration);

      int prio = msg.getJMSPriority();
      if (prio > -1)
         qos.setPriority(PriorityEnum.toPriorityEnum(prio));
   
      String mimeType = msg.getJMSType(); // is this correct ?
      key.setContentMime(mimeType);

      Enumeration eNum = msg.getPropertyNames();
      while (eNum.hasMoreElements()) {
         String propKey = (String)eNum.nextElement();
         Object obj = msg.getObjectProperty(propKey);
         qos.addClientProperty(propKey, obj);
      }
      byte[] content = null;
      if (msg instanceof TextMessage) {
         qos.addClientProperty(XBPropertyNames.JMS_MESSAGE_TYPE, XBMessage.TEXT);
         content = ((TextMessage)msg).getText().getBytes();
      }
      else if (msg instanceof BytesMessage) {
         qos.addClientProperty(XBPropertyNames.JMS_MESSAGE_TYPE, XBMessage.BYTES);
         BytesMessage bytesMsg = (BytesMessage)msg;
         long length = bytesMsg.getBodyLength();
         if (length >= Integer.MAX_VALUE)
            throw new JMSException("Handling of big message not implemented");
         content = new byte[(int)length];
         bytesMsg.readBytes(content);
      }
      else if (msg instanceof ObjectMessage) {
         qos.addClientProperty(XBPropertyNames.JMS_MESSAGE_TYPE, XBMessage.OBJECT);
         ObjectMessage objMsg = (ObjectMessage)msg;
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos);
         Object object = objMsg.getObject();
         oos.writeObject(object);
         content = baos.toByteArray();
         oos.close();
      }
      else if(msg instanceof MapMessage) { // TODO implement this
         throw new JMSException("MapMessage is not implemented");
      }
      else if (msg instanceof StreamMessage) { // TODO implement this
         throw new JMSException("StreamMessage is not implemented");
      }
      else {
         throw new JMSException("unknown message type '" + msg.getClass().getName() + "'");
      }
      return new MsgUnit(key, content, qos);
   }

   /**
    * Puts the extra header properties into the qos
    */
   /*
   private void exportExtraHeader() {
      if (this.qos != null) { 
         Enumeration enumer = this.extraHeader.keys();
         while (enumer.hasMoreElements()) {
            String key = (String)enumer.nextElement();
            Object value = this.extraHeader.get(key);
            this.qos.addClientProperty(XBPropertyNames.JMS_HEADER_PREFIX + key, ClientProperty.getPropertyType(value), (String)value);
         }
      }
   }
   */

   public static XBMessage convert(XBSession session, String sender, MsgUnit msgUnit) throws JMSException, XmlBlasterException, IOException {
      MsgQosData qosData = (MsgQosData)msgUnit.getQosData();
      MsgKeyData keyData = (MsgKeyData)msgUnit.getKeyData();
      byte[] content = msgUnit.getContent();
      return convert(session, sender, keyData, content, qosData);
   }
      
   public static XBMessage convert(XBSession session, String sender, MsgKeyData keyData, byte[] content, MsgQosData qosData) throws JMSException, XmlBlasterException, IOException {
      XBMessage msg = null;
      int type = qosData.getClientProperty(XBPropertyNames.JMS_MESSAGE_TYPE, XBMessage.DEFAULT_TYPE);
      switch (type) {
         case XBMessage.TEXT : msg = new XBTextMessage(session, content); break;
         case XBMessage.BYTES : msg = new XBBytesMessage(session, content); break;
         case XBMessage.OBJECT : msg = new XBObjectMessage(session, content); break;
         case XBMessage.MAP : msg = new XBMapMessage(session, content); break;
         case XBMessage.STREAM : msg = new XBStreamMessage(session, content); break;
         default : throw new JMSException("message type '" + type + "' is unknown to the XmlBlaster JMS Implementation");
      }
      
      String corrId = qosData.getClientProperty(XBPropertyNames.JMS_CORRELATION_ID, (String)null);
      if (corrId != null)
         msg.setJMSCorrelationID(corrId);

      if (qosData.isPersistent())
         msg.setJMSDeliveryMode(DeliveryMode.PERSISTENT);
      else
         msg.setJMSDeliveryMode(DeliveryMode.NON_PERSISTENT);

      org.xmlBlaster.util.qos.address.Destination[] destArr = qosData.getDestinationArr();
      Destination dest = null;
      if (destArr != null && destArr.length > 0) {
         if (destArr.length > 1)
            log.warning("there are more than one destinations defined. The current JMS Implementation only supports single PtP Destinations");
         dest = new XBDestination(keyData.toXml(), destArr[0].getDestination().getAbsoluteName(), destArr[0].forceQueuing());
      }
      else
         dest = new XBDestination(keyData.toXml(), null, false);
      msg.setJMSDestination(dest);

      long life = qosData.getLifeTime();
      msg.setJMSExpiration(life);

      String msgId = "ID:" + qosData.getRcvTimestamp().getTimestamp();
      msg.setJMSMessageID(msgId);

      msg.setJMSPriority(qosData.getPriority().getInt());

      boolean redelivered = qosData.getClientProperty(XBPropertyNames.JMS_REDELIVERED, false);
      if (redelivered)
         msg.setJMSRedelivered(true);

      if (sender != null) {
         // no force queuing (since I don't know better)
         Destination senderDest = new XBDestination(null, sender, false);
         msg.setJMSReplyTo(senderDest);
      }

      long timestamp = qosData.getClientProperty(XBPropertyNames.JMS_TIMESTAMP, 0L);
      if (timestamp != 0L)
         msg.setJMSTimestamp(timestamp);
      return msg;
   }


}
