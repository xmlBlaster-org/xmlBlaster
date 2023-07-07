package org.xmlBlaster.protocol.mqtt;

import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttDisconnect;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class XbMqttDisconnect extends MqttDisconnect implements I_XbMqttWireMessage {

   public static XbMqttDisconnect parse(int mqttVersion, byte[] data) throws IOException, MqttException {
      if (mqttVersion >= 5)
         return new XbMqttDisconnect(data);
      // mqtt3: disconnect packet has no data
      return new XbMqttDisconnect(0, null);
   }

   public XbMqttDisconnect(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttDisconnect(int returnCode, MqttProperties properties) throws MqttException {
      super(returnCode, properties);
   }

   @Override
   public byte[] serializeV5() throws MqttException {
      return serialize(); // not needed
   }

   @Override
   public byte[] serializeV3() throws MqttException {
      return null; // not needed
   }

}
