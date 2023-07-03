package org.xmlBlaster.protocol.mqtt;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttPingReq;

public class XbMqttPingReq extends MqttPingReq implements I_XbMqttWireMessage {

   public XbMqttPingReq() {
      super();
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      return serialize();
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      return serialize();
   }

}
