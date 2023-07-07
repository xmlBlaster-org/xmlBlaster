package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.packet.MqttDataTypes;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttUnsubscribe;

public class XbMqttUnsubscribe extends MqttUnsubscribe {

   public static XbMqttUnsubscribe parse(int mqttVersion, byte[] data) throws MqttException, IOException {
      if (mqttVersion >= 5)
         return new XbMqttUnsubscribe(data);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      DataInputStream dis = new DataInputStream(bais);
      int msgId = dis.readUnsignedShort();

      ArrayList<String> topics = new ArrayList<>();
      while (true) {
         try {
            topics.add(MqttDataTypes.decodeUTF8(dis));
         } catch (Exception e) {
            break;
         }
      }
      dis.close();

      XbMqttUnsubscribe unsub = new XbMqttUnsubscribe(topics.toArray(new String[topics.size()]), null);
      unsub.setMessageId(msgId);
      return unsub;
   }

   public XbMqttUnsubscribe(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttUnsubscribe(String[] topics, MqttProperties properties) {
      super(topics, properties);
   }

}
