package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttPubAck;

public class XbMqttPubAck extends MqttPubAck implements I_XbMqttWireMessage {

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
