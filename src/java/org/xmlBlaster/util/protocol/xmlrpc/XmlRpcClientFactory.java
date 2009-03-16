package org.xmlBlaster.util.protocol.xmlrpc;

import java.net.MalformedURLException;
import java.net.URL;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.xmlBlaster.util.qos.address.AddressBase;


/** An implementation of {@link org.apache.xmlrpc.serializer.XmlWriterFactory},
 * which creates instances of
 * {@link org.apache.ws.commons.serialize.CharSetXMLWriter}.
 */
public class XmlRpcClientFactory {
   
   
   public final static XmlRpcClient getXmlRpcClient(String url, AddressBase address) throws MalformedURLException {
      
      XmlRpcClient xmlRpcClient = new XmlRpcClient();

      String useCDATAtxt = address.getEnv("useCDATA", "false").getValue();
      boolean useCDATA = Boolean.parseBoolean(useCDATAtxt);
      
      XblWriterFactory writerFactory = new XblWriterFactory(useCDATA);
      xmlRpcClient.setXmlWriterFactory(writerFactory);
      
      XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
      config.setServerURL(new URL(url));
      xmlRpcClient.setConfig(config);
      return xmlRpcClient;
   }

}
