package org.xmlBlaster.protocol.mqtt;

import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttConnAck;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class XbMqttConnAck extends MqttConnAck implements I_XbMqttWireMessage {
   private boolean isV5 = true;

   public XbMqttConnAck(boolean sessionPresent, int returnCode, MqttProperties properties) throws MqttException {
      super(sessionPresent, returnCode, properties);
   }

   public XbMqttConnAck(byte[] variableHeader) throws IOException, MqttException {
      super(variableHeader);
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      this.isV5 = true;
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      this.isV5 = false;
      return serialize();
   }

   @Override
   protected byte[] getVariableHeader() throws MqttException {
      if (isV5)
         return super.getVariableHeader();
      return new byte[] { getSessionPresent() ? (byte) 1 : (byte) 0, // acknowledge flag
            (byte) (getReturnCode() == 0 ? 0 : 5) // return codes are completely different.. always return 5 on failure
      };
   }
}
