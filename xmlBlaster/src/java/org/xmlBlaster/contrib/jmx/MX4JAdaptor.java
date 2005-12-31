/*------------------------------------------------------------------------------
Name:      MX4JAdaptor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.jmx;

import java.util.Set;
import java.util.logging.Logger;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import mx4j.tools.adaptor.http.HttpAdaptor;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * MX4JAdaptor
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MX4JAdaptor extends GlobalInfo {

   private static Logger log = Logger.getLogger(MX4JAdaptor.class.getName());
   private ObjectName name;
   
   public MX4JAdaptor() {
      super((Set)null);
   }
   
   private final void instantiateAdaptor(Global global) throws Exception {
      
      // check if the system property is already set
      final String PROP_KEY = "javax.management.builder.initial";
      final String PROP_VALUE = "mx4j.server.MX4JMBeanServerBuilder";
      
      String prop = System.getProperty(PROP_KEY, null);
      if (prop == null) {
         log.info("The System property '" + PROP_KEY + "' will now be set to '" + PROP_VALUE + "'");
         System.setProperty(PROP_KEY, PROP_VALUE);
      }
      else {
         if (!PROP_VALUE.equals(prop.trim()))
            throw new Exception("The System property '" + PROP_KEY + "' has been found. It is '" + prop + "' but we need it to be '" + "'" + PROP_VALUE + "'. Can not start the MX4JAdaptor");
         else
            log.info("The System property '" + PROP_KEY + "' was already set. Will use it.");
      }

      // import mx4j.tools.adaptor.http.HttpAdaptor;
      int port = getInt("port", 9999);
      String host = get("host", "localhost");
      String adaptorName = get("adaptorName", "HttpAdaptorMX4J");
      MBeanServer server = global.getJmxWrapper().getMBeanServer();

      String xsltProcessor = get("xsltProcessor", null);
      ObjectName processorName = null;
      if (xsltProcessor != null) {
         processorName= new ObjectName("Adaptor:name=" + xsltProcessor);
         server.createMBean("mx4j.tools.adaptor.http.XSLTProcessor", processorName, null);
         // set it to use a dir
         String xsltPath = get("xsltPath", null); // can be a directory or a jar file
         String xsltPathInJar = null;
         if (xsltPath != null) {
            server.setAttribute(processorName, new Attribute("File", xsltPath));
            xsltPathInJar = get("xsltPathInJar", null);
            if (xsltPathInJar != null) // set the target dir
               server.setAttribute(processorName, new Attribute("PathInJar", xsltPathInJar));
         }
         boolean xsltCache = getBoolean("xsltCache", true);
         if (xsltCache)
            server.setAttribute(processorName, new Attribute("UseCache", Boolean.TRUE));
         else
            server.setAttribute(processorName, new Attribute("UseCache", Boolean.FALSE));
         // set not to use cache
         String xsltLocale = get("xsltLocale", null);
         if (xsltLocale != null)
            server.setAttribute(processorName, new Attribute("LocaleString", xsltLocale));
         // adds a mime type
         // server.invoke(processorName, "addMimeType", new Object[] {".pdf", "application/pdf"}, new String[] {"java.lang.String", "java.lang.String"});
         log.info("Xslt Processor: " + xsltProcessor + "' on xsltPath='" + xsltPath + "' and xsltPathInJar='" + xsltPathInJar + "' and xsltCache='" + xsltCache + "' and xsltLocale='" + xsltLocale + "'");
      }
      
      HttpAdaptor adapter = new HttpAdaptor();
      this.name = new ObjectName("Adaptor:name=" + adaptorName);
      server.registerMBean(adapter, name);
      adapter.setHost(host);
      adapter.setPort(port);
      if (processorName != null)
         adapter.setProcessorName(processorName);
      adapter.start();
      log.info("The adaptor '" + adaptorName + "' is running. You can access it at 'http://" + host + ":" + port + "'");
   }

   private final void stopAdaptor() throws Exception {
      MBeanServer server = this.global.getJmxWrapper().getMBeanServer();
      server.unregisterMBean(this.name);
   }
   
   /**
    * @see org.xmlBlaster.contrib.GlobalInfo#doInit(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   protected void doInit(Global global, PluginInfo pluginInfo)
         throws XmlBlasterException {
      try {
         instantiateAdaptor(this.global);
      }
      catch (Throwable e) {
         e.printStackTrace();
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "ReplManagerPlugin", "init failed", e); 
      }
   }

   public void shutdown() throws XmlBlasterException {
      try {
         stopAdaptor();
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "ReplManagerPlugin", "shutdown failed", e); 
      }
      super.shutdown();
   }
}
