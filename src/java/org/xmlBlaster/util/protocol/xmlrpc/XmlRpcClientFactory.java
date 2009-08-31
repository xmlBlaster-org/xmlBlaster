package org.xmlBlaster.util.protocol.xmlrpc;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;
import java.util.logging.Logger;

import javax.net.ssl.SSLSocketFactory;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.apache.xmlrpc.client.XmlRpcSun15HttpTransportFactory;
import org.apache.xmlrpc.client.XmlRpcTransportFactory;
import org.xmlBlaster.protocol.xmlrpc.XmlRpcUrl;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.protocol.socket.SocketUrl;
import org.xmlBlaster.util.qos.address.AddressBase;


/** An implementation of {@link org.apache.xmlrpc.serializer.XmlWriterFactory},
 * which creates instances of
 * {@link org.apache.ws.commons.serialize.CharSetXMLWriter}.
 */
public class XmlRpcClientFactory {
   
   private final static Logger log = Logger.getLogger(XmlRpcClientFactory.class.getName());
   
   private final static void setProp(Properties props, String key, String val) {
      String oldVal = props.getProperty(key);
      if (oldVal != null && !oldVal.equals(val)) {
         log.warning("The property '" + key + "' is already set to '" + oldVal + "' will NOT set it to '" + val + "'");
         return;
      }
   }
   
   public final static XmlRpcClient getXmlRpcClient(Global glob, XmlRpcUrl xmlRpcUrl, AddressBase address, boolean useCDATA) throws MalformedURLException, XmlBlasterException {
      XmlRpcClient xmlRpcClient = new XmlRpcClient();
      useCDATA = address.getEnv("useCDATA", useCDATA).getValue();

      String transportFactoryName = address.getEnv("transportFactory", "").getValue();
      if (transportFactoryName.trim().length() > 1) {
         log.info("Going to initialize with transport factory " + transportFactoryName);
         try {
            Class<XmlRpcTransportFactory> 
               forName = (Class<XmlRpcTransportFactory>) Class.forName(transportFactoryName.trim());
            Constructor<XmlRpcTransportFactory> constr = forName.getConstructor(XmlRpcClient.class);
            XmlRpcTransportFactory
               transportFactory = constr.newInstance(xmlRpcClient);
            xmlRpcClient.setTransportFactory(transportFactory);
         }
         catch (ClassNotFoundException e) {
            log.severe("Exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
         }
         catch (NoSuchMethodException e) {
            log.severe("Exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
         }
         catch (InstantiationException e) {
            log.severe("Exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
         }
         catch (InvocationTargetException e) {
            log.severe("Exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
         }
         catch (IllegalAccessException e) {
            log.severe("Exception " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
         }
         
      }
      
      XblWriterFactory writerFactory = new XblWriterFactory(useCDATA);
      xmlRpcClient.setXmlWriterFactory(writerFactory);
      
      XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
      
      int val = address.getEnv("responseTimeout", 0).getValue();
      if (val > 0)
         config.setReplyTimeout(val);
      val = address.getEnv("SoTimeout", 0).getValue();
      if (val > 0)
         config.setConnectionTimeout(val);
      
      String compressType = address.getEnv("compress/type", "").getValue();
      if (Constants.COMPRESS_ZLIB.equals(compressType) || Constants.COMPRESS_ZLIB_STREAM.equals(compressType))
         config.setGzipCompressing(true);
      else
         config.setGzipCompressing(false);

      String proxyHost = address.getEnv("proxyHost", (String)null).getValue();
      String proxyPort = address.getEnv("proxyPort", (String)null).getValue();
      if (proxyPort != null && proxyPort.length() > 0 && proxyHost == null)
         proxyHost = "localhost";
      
      String proxyHostSSL = address.getEnv("proxyHostSSL", proxyHost).getValue();
      String proxyPortSSL = address.getEnv("proxyPortSSL", proxyPort).getValue();
      
      Properties props = System.getProperties();
      synchronized(props) {
         if (proxyHost != null)
            props.setProperty("http.proxySet", "true");
         if (proxyHostSSL != null)
            props.setProperty("https.proxySet", "true");
         
         setProp(props, "http.proxyHost", proxyHost);
         setProp(props, "http.proxyPort", proxyPort);
         setProp(props, "https.proxyHost", proxyHostSSL);
         setProp(props, "https.proxyPort", proxyPortSSL);
      }
      
      String basicUserName = address.getEnv("basicUserName", null).getValue();
      String basicPassword = address.getEnv("basicPassword", null).getValue();
      String basicEncoding = address.getEnv("basicEncoding", null).getValue();
      String userAgent = address.getEnv("userAgent", null).getValue();
      if (basicUserName != null)
         config.setBasicUserName(basicUserName);
      if (basicPassword != null)
         config.setBasicPassword(basicPassword);
      if (basicEncoding != null)
         config.setBasicEncoding(basicEncoding);
      if (userAgent != null)
         config.setUserAgent(userAgent);
      config.setEnabledForExceptions(true);
      config.setEnabledForExtensions(true);
      
      String url = null;
      if (xmlRpcUrl == null) {
         url = address.getRawAddress();
         xmlRpcUrl = new XmlRpcUrl(glob, url);
      }
      else
         url = xmlRpcUrl.getUrl();
      boolean isSSL = url.indexOf("https://") == 0;

      config.setServerURL(new URL(url));
      xmlRpcClient.setConfig(config);
      
      XmlRpcTransportFactory transportFactory = xmlRpcClient.getTransportFactory();
      
      if (transportFactory instanceof XmlRpcSun15HttpTransportFactory) {
         log.info("Going to set proxy for transport factory for " + transportFactory.getClass().getName());
         XmlRpcSun15HttpTransportFactory trFact = (XmlRpcSun15HttpTransportFactory)transportFactory;
         if (proxyHost != null) {
            if (proxyPort == null)
               proxyPort = "3128";
            trFact.setProxy(proxyHost, Integer.parseInt(proxyPort));
         }
         if (isSSL) {
            SocketUrl socketUrl = new SocketUrl(glob, xmlRpcUrl.getHostname(), xmlRpcUrl.getPort());
            SSLSocketFactory ssf = socketUrl.createSocketFactorySSL(address);
            if (ssf != null)
               trFact.setSSLSocketFactory(ssf);
            else
               log.warning("The SSL Socket Factory was null but SSL is required");
         }
      }
      else {
         log.info("The transport factory is of type '" + transportFactory.getClass().getName() + "' which is not pf type XmlRpcSun15HttpTransportFactory");
      }
      
      return xmlRpcClient;
   }

}
