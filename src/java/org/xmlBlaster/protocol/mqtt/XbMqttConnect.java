package org.xmlBlaster.protocol.mqtt;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;

import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.packet.MqttDataTypes;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;

public class XbMqttConnect extends org.eclipse.paho.mqttv5.common.packet.MqttConnect {

   public static XbMqttConnect parse(int mqttVersion, byte[] data) throws MqttException, IOException {
      if (mqttVersion >= 5)
         return new XbMqttConnect((byte) 0, data);

      ByteArrayInputStream bais = new ByteArrayInputStream(data);
      DataInputStream dis = new DataInputStream(bais);

      /* String protocolName = */ MqttDataTypes.decodeUTF8(dis);
      int protocolVersion = dis.readByte();
      byte connectFlags = dis.readByte();
      int keepAliveInterval = dis.readUnsignedShort();
      String clientId = MqttDataTypes.decodeUTF8(dis);
      String willTopic = null;
      String willMessage = null;
      String user = null;
      String password = null;

      boolean cleanSession = (connectFlags & 0b00000010) > 0;
      boolean hasWill = (connectFlags & 0b00000100) > 0;
      boolean willRetain = (connectFlags & 0b00100000) > 0;
      boolean hasPassword = (connectFlags & 0b01000000) > 0;
      boolean hasUser = (connectFlags & 0b10000000) > 0;

      if (hasWill) {
         willTopic = MqttDataTypes.decodeUTF8(dis);
         willMessage = MqttDataTypes.decodeUTF8(dis);
      }
      if (hasUser)
         user = MqttDataTypes.decodeUTF8(dis);
      if (hasPassword)
         password = MqttDataTypes.decodeUTF8(dis);

      dis.close();

      XbMqttConnect connect = new XbMqttConnect(clientId, protocolVersion, cleanSession, keepAliveInterval, new MqttProperties(), new MqttProperties());
      connect.setUserName(user);
      connect.setPassword(password.getBytes());
      if (hasWill) {
         connect.setWillDestination(willTopic);
         MqttMessage willMsg = new MqttMessage(willMessage.getBytes());
         willMsg.setRetained(willRetain);
         connect.setWillMessage(willMsg);
      }
      return connect;
   }

   public XbMqttConnect(byte info, byte[] data) throws IOException, MqttException {
      super(info, data);
   }

   public XbMqttConnect(String clientId, int mqttVersion, boolean cleanStart, int keepAliveInterval, MqttProperties properties, MqttProperties willProperties) {
      super(clientId, mqttVersion, cleanStart, keepAliveInterval, properties, willProperties);
   }

   public String toLogString() {
      return "MqttConnect [clientId=" + this.getClientId() + ", cleanStart=" + this.isCleanStart() + ", willDestination=" + this.getWillDestination() + ", userName=" + this.getUserName() + ", keepAliveInterval=" + this.getKeepAliveInterval() + ", mqttVersion=" + this.getMqttVersion() + "]";

   }

}
