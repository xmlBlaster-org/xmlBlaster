package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttPubComp;

public class XbMqttPubComp extends MqttPubComp implements I_XbMqttWireMessage {
   private boolean isV5 = true;
   
   public static XbMqttPubComp parse(int mqttVersion, byte[] data) throws IOException, MqttException {
      if (mqttVersion >= 5)
         return new XbMqttPubComp(data);
      
      org.eclipse.paho.client.mqttv3.internal.wire.MqttPubComp v3comp = new org.eclipse.paho.client.mqttv3.internal.wire.MqttPubComp((byte) 0, data);
      return new XbMqttPubComp(0, v3comp.getMessageId(), null);
   }

   public XbMqttPubComp(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttPubComp(int returnCode, int msgId, MqttProperties properties) throws MqttException {
      super(returnCode, msgId, properties);
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      isV5 = true;
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      isV5 = false;
      return serialize();
   }
   
   @Override
   protected byte[] getVariableHeader() throws MqttException {
      if (isV5)
         return super.getVariableHeader();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream outputStream = new DataOutputStream(baos);
      try {
         outputStream.writeShort(this.getMessageId());
         outputStream.flush();
      } catch (Throwable e) {
         throw new MqttException(e);
      }
      return baos.toByteArray();
   }

}
