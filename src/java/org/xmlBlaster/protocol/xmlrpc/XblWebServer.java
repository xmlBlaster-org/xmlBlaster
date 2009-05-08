package org.xmlBlaster.protocol.xmlrpc;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;

import org.apache.xmlrpc.webserver.WebServer;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.AddressBase;


public class XblWebServer extends WebServer {
   
   private final XmlRpcUrl xmlRpcUrl;
   private final SocketUrl socketUrl;
   private final AddressBase addressBase;
   
   public XblWebServer(XmlRpcUrl xmlRpcUrl, SocketUrl socketUrl, AddressBase addressBase) {
      super(socketUrl.getPort(), socketUrl.getInetAddress());
      this.socketUrl = socketUrl;
      this.addressBase = addressBase;
      this.xmlRpcUrl = xmlRpcUrl;
   }
   
   @Override
   protected ServerSocket createServerSocket(int port, int backlog, InetAddress addr) throws IOException {
      try {
         if ("http://".equals(xmlRpcUrl.getProtocol()))
            return super.createServerSocket(port, backlog, addr);
         return socketUrl.createServerSocketSSL(backlog, addressBase);
      }
      catch (XmlBlasterException ex) {
         throw new IOException(ex.getMessage(), ex);
      }
   }

}
