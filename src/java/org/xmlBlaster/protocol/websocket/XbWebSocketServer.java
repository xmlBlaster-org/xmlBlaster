package org.xmlBlaster.protocol.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.xmlBlaster.util.protocol.socket.SocketUrl;

public class XbWebSocketServer extends WebSocketServer {
   private static Logger log = Logger.getLogger(XbWebSocketServer.class.getName());
   private WebSocketDriver driver;
   private Map<WebSocket, HandleWebSocketClient> connectedClients = new HashMap<>();
   
   public XbWebSocketServer(WebSocketDriver driver, SocketUrl socketUrl) {
      super(new InetSocketAddress(socketUrl.getHostname(), socketUrl.getPort()));
      this.driver = driver;
      setReuseAddr(true);
      setDaemon(true);
   }

   @Override
   public void onClose(WebSocket conn, int code, String reason, boolean remote) {
      HandleWebSocketClient handler = this.connectedClients.get(conn);
      if (handler != null)
         handler.onClose(code, reason, remote);
      connectedClients.remove(conn);
   }

   @Override
   public void onError(WebSocket conn, Exception ex) {
      HandleWebSocketClient handler = this.connectedClients.get(conn);
      if (handler != null)
         handler.onError(ex);
   }

   @Override
   public void onMessage(WebSocket conn, String message) {
      HandleWebSocketClient handler = this.connectedClients.get(conn);
      if (handler != null)
         handler.onMessage(message);
   }

   @Override
   public void onMessage(WebSocket conn, ByteBuffer message) {
      HandleWebSocketClient handler = this.connectedClients.get(conn);
      if (handler != null)
         handler.onMessage(message);
   }

   @Override
   public void onOpen(WebSocket conn, ClientHandshake clientHandshake) {
      HandleWebSocketClient handler = new HandleWebSocketClient(driver.getGlobal(), driver, conn, clientHandshake);
      this.connectedClients.put(conn,  handler);
   }

   @Override
   public void onStart() {
   }
   
   public Collection<HandleWebSocketClient> getConnectedClients() {
      return this.connectedClients.values();
   }

}
