package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttPublish;

public class XbMqttPublish extends MqttPublish implements I_XbMqttWireMessage {

   public static XbMqttPublish parse(int mqttVersion, byte reserved, byte[] data) throws IOException, MqttException {
      if (mqttVersion >= 5)
         return new XbMqttPublish(reserved, data);

      try {
         org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish v3pub = new org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish(reserved, data);
         org.eclipse.paho.client.mqttv3.MqttMessage v3msg = v3pub.getMessage();
         
         MqttMessage v5msg = new MqttMessage(v3msg.getPayload(), v3msg.getQos(), v3msg.isRetained(), new MqttProperties());
         v5msg.setId(v3msg.getId());
         XbMqttPublish v5pub = new XbMqttPublish(v3pub.getTopicName(), v5msg, null);
         v5pub.setMessageId(v3pub.getMessageId());

         return v5pub;
      } catch (Throwable e) {
         throw new MqttException(e);
      }
   }

   public XbMqttPublish(String topic, MqttMessage message, MqttProperties properties) {
      super(topic, message, properties);
   }

   public XbMqttPublish(byte info, byte[] data) throws MqttException, IOException {
      super(info, data);
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      org.eclipse.paho.client.mqttv3.MqttMessage v3msg = new org.eclipse.paho.client.mqttv3.MqttMessage();
      MqttMessage v5msg = this.getMessage();

      v3msg.setPayload(v5msg.getPayload());
      v3msg.setQos(v5msg.getQos());
      v3msg.setId(v5msg.getId());
      v3msg.setRetained(v5msg.isRetained());

      org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish v3pub = new org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish(this.getTopicName(), v3msg);
      v3pub.setMessageId(this.getMessageId());
      v3pub.setDuplicate(this.isDuplicate());

      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
         bos.write(v3pub.getHeader());
         bos.write(v3pub.getPayload());
      } catch (Throwable e) {
         throw new MqttException(e);
      }
      return bos.toByteArray();
   }

}
