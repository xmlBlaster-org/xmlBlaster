package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttDataTypes;
import org.eclipse.paho.mqttv5.common.packet.MqttWireMessage;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * Inspired by Pahos MqttInputStream, but doesn't do client handling and auto
 * detects MQTT v3/v5
 */
public class MqttInputStream extends InputStream {
   private static Logger log = Logger.getLogger(MqttInputStream.class.getName());
   
   public interface I_MessageListener {
      /** Return true to intercept message */
      boolean onMessage(MqttWireMessage msg) throws MqttException, IOException;
   }

   private DataInputStream in;
   private ByteArrayOutputStream baos = new ByteArrayOutputStream();
   private int mqttVersion = -1;
   
   private Set<I_MessageListener> listeners = new HashSet<>();

   public MqttInputStream(InputStream in) {
      this.in = new DataInputStream(in);
   }
   
   /**
    * Listener will be called until it returns true once, Then it is removed. 
    * @param listener
    */
   public void addResponseListener(I_MessageListener listener) {
      synchronized (listeners) {
         this.listeners.add(listener);
      }
   }
   
   public void removeResponseListener(I_MessageListener listener) {
      synchronized (listeners) {
         this.listeners.remove(listener);
      }
   }
   
   private boolean notifyResponseListeners(MqttWireMessage msg) throws MqttException, IOException {
      synchronized (listeners) {
         Iterator<I_MessageListener> it = listeners.iterator();
         while (it.hasNext()) {
            I_MessageListener l = it.next();
            if (l.onMessage(msg)) {
               it.remove();
               return true;
            }
         }
      }
      return false;
   }

   @Override
   public int read() throws IOException {
      return in.read();
   }

   @Override
   public int available() throws IOException {
      return in.available();
   }

   @Override
   public void close() throws IOException {
      in.close();
   }

   public int getMqttVersion() {
      return mqttVersion;
   }

   public MqttWireMessage readMessage() throws IOException, MqttException, XmlBlasterException {
      MqttWireMessage message = null;
      try {
         baos.reset();

         byte first = in.readByte();

         byte type = (byte) ((first >>> 4) & 0x0F);
         if ((type < MqttWireMessage.MESSAGE_TYPE_CONNECT) || (type > MqttWireMessage.MESSAGE_TYPE_AUTH)) {
            // Invalid MQTT message type...
            throw new XmlBlasterException(null, ErrorCode.USER_MESSAGE_INVALID, "MqttInputStream", "Invalid MQTT message type " + type);
         }
         if (mqttVersion < 0 && type != MqttWireMessage.MESSAGE_TYPE_CONNECT)
            throw new XmlBlasterException(null, ErrorCode.USER_MESSAGE_INVALID, "MqttInputStream", "Unexpected: First received packet is not MQTT Connect. Unable to determine protocol version");

         byte reserved = (byte) (first & 0x0F);

         int remLen = MqttDataTypes.readVariableByteInteger(in).getValue();
         baos.write(first);
         baos.write(MqttWireMessage.encodeVariableByteInteger((int) remLen));

         byte[] messageData = new byte[remLen];

         int read = 0;
         while (read < remLen)
            read += in.read(messageData, read, remLen - read);

         if (mqttVersion < 0 && type == MqttWireMessage.MESSAGE_TYPE_CONNECT)
            mqttVersion = messageData[6];

         message = createWireMessage(type, reserved, messageData);
         if (message != null)
            log.info("mqtt recv: " + message.toString());
         if (notifyResponseListeners(message))
            message = null;
      } catch (SocketTimeoutException e) {
         // ignore socket read timeout
      }
      


      return message;
   }

   private MqttWireMessage createWireMessage(byte type, byte reserved, byte[] data) throws MqttException, IOException {
      switch (type) {
      case MqttWireMessage.MESSAGE_TYPE_CONNECT:
         return XbMqttConnect.parse(mqttVersion, data);
      // case MqttWireMessage.MESSAGE_TYPE_CONNACK:
      case MqttWireMessage.MESSAGE_TYPE_PUBLISH:
         return XbMqttPublish.parse(mqttVersion, reserved, data);
      case MqttWireMessage.MESSAGE_TYPE_PUBACK:
         return XbMqttPubAck.parse(mqttVersion, reserved, data);
      case MqttWireMessage.MESSAGE_TYPE_PUBREC:
         return XbMqttPubRec.parse(mqttVersion, data);
      case MqttWireMessage.MESSAGE_TYPE_PUBREL:
         return XbMqttPubRel.parse(mqttVersion, data);
      case MqttWireMessage.MESSAGE_TYPE_PUBCOMP:
         return XbMqttPubComp.parse(mqttVersion, data);
      case MqttWireMessage.MESSAGE_TYPE_SUBSCRIBE:
         return XbMqttSubscribe.parse(mqttVersion, data);
      // case MqttWireMessage.MESSAGE_TYPE_SUBACK:
      case MqttWireMessage.MESSAGE_TYPE_UNSUBSCRIBE:
         return XbMqttUnsubscribe.parse(mqttVersion, data);
      // case MqttWireMessage.MESSAGE_TYPE_UNSUBACK:
      case MqttWireMessage.MESSAGE_TYPE_PINGREQ:
         return new XbMqttPingReq();
      // case MqttWireMessage.MESSAGE_TYPE_PINGRESP:
      case MqttWireMessage.MESSAGE_TYPE_DISCONNECT:
         return XbMqttDisconnect.parse(mqttVersion, data);
      // case MqttWireMessage.MESSAGE_TYPE_AUTH:
      default:
         //System.out.println("unknown message type " + type);
         return null;
      }

   }

}
