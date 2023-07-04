package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttPubAck;

public class XbMqttPubAck extends MqttPubAck implements I_XbMqttWireMessage {

   public static XbMqttPubAck parse(int mqttVersion, byte reserved, byte[] data) throws IOException, MqttException {
      if (mqttVersion >= 5)
         return new XbMqttPubAck(data);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      DataInputStream dis = new DataInputStream(bais);
      return new XbMqttPubAck(0, dis.readUnsignedShort(), null);
   }

   public XbMqttPubAck(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttPubAck(int returnCode, int msgId, MqttProperties properties) throws MqttException {
      super(returnCode, msgId, properties);
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      // in V3, there is no error code. If there is an error, we don't sent ack at
      // all..
      if (getReturnCode() != 0)
         return new byte[] {};

      org.eclipse.paho.client.mqttv3.internal.wire.MqttPubAck v3ack = new org.eclipse.paho.client.mqttv3.internal.wire.MqttPubAck(this.getMessageId());
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
         bos.write(v3ack.getHeader());
         bos.write(v3ack.getPayload());
      } catch (Throwable e) {
         throw new MqttException(e);
      }
      return bos.toByteArray();
   }
}
