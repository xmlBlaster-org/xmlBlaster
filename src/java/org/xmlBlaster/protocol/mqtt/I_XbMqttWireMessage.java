package org.xmlBlaster.protocol.mqtt;

import org.eclipse.paho.mqttv5.common.MqttException;

public interface I_XbMqttWireMessage {
   byte[] serializeV5() throws MqttException;

   byte[] serializeV3() throws MqttException;
}
