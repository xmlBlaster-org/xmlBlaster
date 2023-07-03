package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttSubAck;

public class XbMqttSubAck extends MqttSubAck implements I_XbMqttWireMessage {
   private boolean isV5 = true;

   public XbMqttSubAck(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttSubAck(int[] returnCodes, MqttProperties properties) throws MqttException {
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
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream outputStream = new DataOutputStream(baos);
         byte b;
         int i, arrayOfInt[];
         for (i = (arrayOfInt = this.reasonCodes).length, b = 0; b < i;) {
            int returnCode = arrayOfInt[b];
            // in mqtt3, only 0x80 indicates failure
            if (returnCode > 0x80)
               returnCode = 0x80;
            outputStream.writeByte(returnCode);
            b++;
         }
         outputStream.flush();
         return baos.toByteArray();
      } catch (IOException ioe) {
         throw new MqttException(ioe);
      }

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
