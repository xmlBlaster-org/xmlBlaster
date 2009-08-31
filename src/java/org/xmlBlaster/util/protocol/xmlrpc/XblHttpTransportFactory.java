package org.xmlBlaster.util.protocol.xmlrpc;

import java.net.InetSocketAddress;
import java.net.Proxy;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransportFactory;
import org.apache.xmlrpc.client.XmlRpcTransport;

public class XblHttpTransportFactory extends XmlRpcSun15HttpTransportFactory {
   
   private Proxy proxy;
   
   /** Creates a new factory, which creates transports for the given client.
    * @param pClient The client, which is operating the factory.
    */
   public XblHttpTransportFactory(XmlRpcClient pClient) {
      super(pClient);
    }

   /**
    * Sets the proxy to use.
    * @param proxyHost The proxy hostname.
    * @param proxyPort The proxy port number.
    * @throws IllegalArgumentException if the proxyHost parameter is null or if
    *     the proxyPort parameter is outside the range of valid port values.
    */
   public void setProxy(String proxyHost, int proxyPort) {
      Proxy tmpProxy = new Proxy(Proxy.Type.HTTP,new InetSocketAddress(proxyHost,proxyPort));
      setProxy(tmpProxy);
   }

   /**
    * Sets the proxy to use.
    * @param pProxy The proxy settings.
    */
   public void setProxy(Proxy pProxy) {
       proxy = pProxy;
       super.setProxy(proxy);
   }

   public XmlRpcTransport getTransport() {
      XblHttpTransport transport = new XblHttpTransport(getClient());
      transport.setSSLSocketFactory(getSSLSocketFactory());
      transport.setProxy(proxy);
      return transport;
  }       

}
