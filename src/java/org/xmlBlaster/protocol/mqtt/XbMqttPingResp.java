package org.xmlBlaster.protocol.mqtt;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttPingResp;

public class XbMqttPingResp extends MqttPingResp implements I_XbMqttWireMessage {

   @Override
   public byte[] serializeV5() throws MqttException {
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      return serialize();
   }

}
