package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;

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
   //private static Logger log = Logger.getLogger(MqttInputStream.class.getName());

   private DataInputStream in;
   private ByteArrayOutputStream baos = new ByteArrayOutputStream();
   private int mqttVersion = -1;

   public MqttInputStream(InputStream in) {
      this.in = new DataInputStream(in);
   }

   @Override
   public int read() throws IOException {
      // TODO Auto-generated method stub
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

         return createWireMessage(type, reserved, messageData);
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
      // case MqttWireMessage.MESSAGE_TYPE_PUBACK:
      // case MqttWireMessage.MESSAGE_TYPE_PUBREC:
      // case MqttWireMessage.MESSAGE_TYPE_PUBREL:
      // case MqttWireMessage.MESSAGE_TYPE_PUBCOMP:
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
         return null;
      }

   }

}
