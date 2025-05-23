package org.xmlBlaster.protocol.websocket;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.java_websocket.WebSocket;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.exceptions.InvalidHandshakeException;
import org.java_websocket.extensions.IExtension;
import org.java_websocket.extensions.permessage_deflate.PerMessageDeflateExtension;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.handshake.HandshakeBuilder;
import org.java_websocket.handshake.ServerHandshakeBuilder;
import org.java_websocket.protocols.IProtocol;
import org.java_websocket.server.WebSocketServer;
import org.xmlBlaster.util.protocol.socket.SocketUrl;

public class XbWebSocketServer extends WebSocketServer {
   private static Logger log = Logger.getLogger(XbWebSocketServer.class.getName());
   private WebSocketDriver driver;
   private Map<WebSocket, HandleWebSocketClient> connectedClients = new HashMap<>();
   
   /**
    * Ugly, but unfortunately, the only way to override the "Server:" header seems to be subclassing Draft_6455 and properly implementing copyInstance..
    */
   private static class XbDraftImpl extends Draft_6455 {
      public XbDraftImpl(IExtension ext) {
         super(ext);
      }
      
      public XbDraftImpl(List<IExtension> extensions, List<IProtocol> protocols, int maxFrameSize) {
         super(extensions, protocols, maxFrameSize);
      }
      
      @Override
      public HandshakeBuilder postProcessHandshakeResponseAsServer(ClientHandshake request, ServerHandshakeBuilder response) throws InvalidHandshakeException {
         HandshakeBuilder hb = super.postProcessHandshakeResponseAsServer(request, response);
         hb.put("Server", "XmlBlaster");
         return hb;
      }
      @Override
      public Draft copyInstance() {
         List<IExtension> exts = getKnownExtensions().stream().map(e -> e.copyInstance()).toList();
         List<IProtocol> protocols = getKnownProtocols().stream().map(p -> p.copyInstance()).toList();
         return new XbDraftImpl(exts, protocols, getMaxFrameSize());
      }
   }
   
   // see https://github.com/TooTallNate/Java-WebSocket/blob/master/src/main/example/PerMessageDeflateExample.java
   private static final Draft_6455 draftInstance = new XbDraftImpl(new PerMessageDeflateExtension());
   
   
   public XbWebSocketServer(WebSocketDriver driver, SocketUrl socketUrl) {
      super(new InetSocketAddress(socketUrl.getHostname(), socketUrl.getPort()), Collections.singletonList(draftInstance));
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
