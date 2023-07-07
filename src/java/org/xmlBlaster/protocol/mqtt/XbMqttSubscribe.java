package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttDataTypes;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.eclipse.paho.mqttv5.common.packet.MqttSubscribe;

public class XbMqttSubscribe extends MqttSubscribe {

   public static XbMqttSubscribe parse(int mqttVersion, byte[] data) throws IOException, MqttException {
      if (mqttVersion >= 5)
         return new XbMqttSubscribe(data);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      DataInputStream dis = new DataInputStream(bais);
      int msgId = dis.readUnsignedShort();

      List<MqttSubscription> topics = new ArrayList<>();
      while (true) {
         try {
            String topic = MqttDataTypes.decodeUTF8(dis);
            byte qos = dis.readByte();
            topics.add(new MqttSubscription(topic, qos));
         } catch (Exception e) {
            break;
         }
      }
      dis.close();
      return new XbMqttSubscribe(msgId, topics.toArray(new MqttSubscription[topics.size()]), null);
   }

   public XbMqttSubscribe(byte[] data) throws IOException, MqttException {
      super(data);
   }

   public XbMqttSubscribe(MqttSubscription subscription, MqttProperties properties) {
      super(subscription, properties);
   }

   public XbMqttSubscribe(int msgId, MqttSubscription[] subscriptions, MqttProperties properties) {
      super(subscriptions, properties);
      this.msgId = msgId;
   }

}
