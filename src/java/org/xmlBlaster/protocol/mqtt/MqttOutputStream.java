package org.xmlBlaster.protocol.mqtt;

import java.io.IOException;
import java.io.OutputStream;

import org.eclipse.paho.mqttv5.common.MqttException;

public class MqttOutputStream {
   private int mqttVersion;
   private OutputStream os;

   public MqttOutputStream(int mqttVersion, OutputStream os) {
      this.mqttVersion = mqttVersion;
      this.os = os;
   }

   public synchronized void write(I_XbMqttWireMessage message) throws IOException, MqttException {
      byte[] data;
      if (mqttVersion < 5 && message instanceof I_XbMqttWireMessage) {
         data = ((I_XbMqttWireMessage) message).serializeV3();
      } else {
         data = ((I_XbMqttWireMessage) message).serializeV5();
      }
      if (data != null)
         os.write(data);
   }
}
