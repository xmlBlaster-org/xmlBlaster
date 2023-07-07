package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttUnsubAck;

public class XbMqttUnsubAck extends MqttUnsubAck implements I_XbMqttWireMessage {
   private boolean isV5 = true;

   public XbMqttUnsubAck(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttUnsubAck(int[] returnCodes, MqttProperties properties) throws MqttException {
      super(returnCodes, properties);
   }

   @Override
   protected byte[] getVariableHeader() throws MqttException {
      if (isV5)
         return super.getVariableHeader();
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      DataOutputStream outputStream = new DataOutputStream(baos);
      try {
         outputStream.writeShort(this.msgId);
         outputStream.flush();

      } catch (IOException e) {
         throw new MqttException(e);
      }
      return baos.toByteArray();
   }

   @Override
   public byte[] getPayload() throws MqttException {
      if (isV5)
         return super.getPayload();
      return new byte[] {};

   }

   @Override
   public byte[] serializeV3() throws MqttException {
      this.isV5 = false;
      return serialize();
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      this.isV5 = true;
      return serialize();
   }

}
