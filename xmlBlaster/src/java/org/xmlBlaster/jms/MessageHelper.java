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
   
   public static void copyProperties(Message sourceMsg, Message destMsg) throws JMSException {
      if (sourceMsg == null)
         throw new XBException("internal", "Convert: The passed 'sourceMsg' attribute is null.");
      if (destMsg == null)
         throw new XBException("internal", "Convert: The passed 'destMsg' attribute is null.");
      if (sourceMsg instanceof XBMessage)
         ((XBMessage)sourceMsg).giveFullAccess();
      if (destMsg instanceof XBMessage)
         ((XBMessage)destMsg).giveFullAccess();
      try {
         // first strip all qos properties which are specific
         Destination dest = sourceMsg.getJMSDestination();
         if (dest != null)
            destMsg.setJMSDestination(dest);
         String corrId = sourceMsg.getJMSCorrelationID();
         if (corrId != null)
            destMsg.setJMSCorrelationID(corrId);
         int deliveryMode = sourceMsg.getJMSDeliveryMode();
         destMsg.setJMSDeliveryMode(deliveryMode);
         long expiration = sourceMsg.getJMSExpiration();
         if (expiration > -1)
            destMsg.setJMSExpiration(expiration);
         int prio = sourceMsg.getJMSPriority();
         if (prio > -1)
            destMsg.setJMSPriority(prio);
         String mimeType = sourceMsg.getJMSType(); // is this correct ?
         if (mimeType != null)
            destMsg.setJMSType(mimeType);

         Enumeration eNum = sourceMsg.getPropertyNames();
         while (eNum.hasMoreElements()) {
            String propKey = (String)eNum.nextElement();
            Object obj = sourceMsg.getObjectProperty(propKey);
            destMsg.setObjectProperty(propKey, obj);
         }
      }
      finally {
         if (sourceMsg instanceof XBMessage)
            ((XBMessage)sourceMsg).resetAccess();
         if (destMsg instanceof XBMessage)
            ((XBMessage)destMsg).resetAccess();
      }
   }
   
   public static MsgUnit convert(Global global, Message msg) throws JMSException, XmlBlasterException, IOException {
      if (global == null)
         throw new XBException("internal", "Convert: The passed 'global' attribute is null.");
      if (msg == null)
         throw new XBException("internal", "Convert: The passed 'msg' attribute is null.");
      if (msg instanceof XBMessage)
         ((XBMessage)msg).giveFullAccess();
      try {
         // first strip all qos properties which are specific
         Destination dest = msg.getJMSDestination();
         if (!(dest instanceof XBDestination))
            throw new XBException("client.configuration", "destination is not an xmlblaster destination. Do not know how to handle it");
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
            else {
               qos = new PublishQos(global);
            }
            String tmp = xbDest.getTopicName();
            if (tmp == null && ptpName == null)
               throw new XBException("client.configuration", "A Topic must be specified in the message to be sent");

            // determine if it is a complete key 
            if (tmp != null && tmp.indexOf('<') > -1) { // complete key
               MsgKeySaxFactory keyFactory = new MsgKeySaxFactory(global);
               key = new PublishKey(global, keyFactory.readObject(tmp));
            }
            else { // then it is a simple oid
               if (tmp != null)
                  key = new PublishKey(global, tmp);
               else
                  key = new PublishKey(global);
            }
         }
         else
            throw new XBException("client.configuration", "A destination must be specified in the message to be sent");
         
         String corrId = msg.getJMSCorrelationID();
         if (corrId != null)
            qos.addClientProperty(XBPropertyNames.JMS_CORRELATION_ID, corrId);
         int deliveryMode = msg.getJMSDeliveryMode();
         if (deliveryMode == DeliveryMode.PERSISTENT)
            qos.setPersistent(true);
         long expiration = msg.getJMSExpiration();
         if (expiration > -1)
            qos.setLifeTime(expiration);

         int prio = msg.getJMSPriority();
         if (prio > -1)
            qos.setPriority(PriorityEnum.toPriorityEnum(prio));
      
         String mimeType = msg.getJMSType(); // is this correct ?
         if (mimeType != null)
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
               throw new XBException("feature.missing", "Handling of big message not implemented");
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
            throw new XBException("feature.missing", "MapMessage is not implemented");
         }
         else if (msg instanceof StreamMessage) { // TODO implement this
            throw new XBException("feature.missing", "StreamMessage is not implemented");
         }
         else {
            throw new XBException("feature.missing", "unknown message type '" + msg.getClass().getName() + "'");
         }
         return new MsgUnit(key, content, qos);
      }
      finally {
         if (msg instanceof XBMessage)
            ((XBMessage)msg).resetAccess();
      }
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
         default : throw new XBException("feature.missing", "message type '" + type + "' is unknown to the XmlBlaster JMS Implementation");
      }

      
      if (msg instanceof XBMessage)
         ((XBMessage)msg).giveFullAccess();
      try {
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
      finally {
         if (msg instanceof XBMessage)
            ((XBMessage)msg).resetAccess();
      }
      
   }


}
