/*------------------------------------------------------------------------------
Name:      MX4JAdaptor.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.contrib.jmx;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import mx4j.tools.adaptor.http.HttpAdaptor;
import mx4j.tools.adaptor.http.XSLTProcessor;

import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxWrapper;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.PluginInfo;

/**
 * MX4JAdaptor
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public class MX4JAdaptor extends GlobalInfo {

   private static Logger log = Logger.getLogger(MX4JAdaptor.class.getName());
   private ObjectName name;
   
   public class ContribXsltProcessor extends XSLTProcessor {
      
      private String prefix;
      private Set warnings;
      
      public ContribXsltProcessor() {
         super();
         this.warnings = new HashSet();
      }
      
      public void setFile(String prefix) {
         this.prefix = prefix;
      }
      
      public String getFile(){
         return this.prefix;
      }
      
      protected InputStream getInputStream(String path) {
         if (path == null)
            log.severe("ContribXsltProcessor.getInputStream: no filename specified");
         // path = this.prefix + File.separatorChar + path;
         if (!path.startsWith(File.separator))
            path = File.separator + path;
               
         path = this.prefix + path;
         
         InputStream ret = null;
         try {
            Enumeration enm = this.getClass().getClassLoader().getResources(path);
            if(enm.hasMoreElements()) {
               URL url = (URL)enm.nextElement();
               log.fine("loading file '" + url.getFile() + "'");
               try {
                  ret = url.openStream();
               }
               catch(IOException ex) {
                  log.warning("init: could not read properties from '" + url.getFile() + "' : " + ex.getMessage());
               }
               while(enm.hasMoreElements() && !this.warnings.contains(path)) {
                  this.warnings.add(path);
                  url = (URL)enm.nextElement();
                  log.warning("an additional matching file has been found in the classpath at '"
                     + url.getFile() + "' please check that the correct one has been loaded (see info above)"
                  );
               }
            }
            else {
               ClassLoader cl = this.getClass().getClassLoader();
               StringBuffer buf = new StringBuffer();
               if (cl instanceof URLClassLoader) {
                  URL[] urls = ((URLClassLoader)cl).getURLs();
                  for (int i=0; i < urls.length; i++) 
                     buf.append(urls[i].toString()).append("\n");
               }
               log.warning("no file found with the name '" + path + "'" /*+ "' : " + (buf.length() > 0 ? " classpath: " + buf.toString() : ""*/);
            }
         }
         catch(IOException e) {
            log.severe("an IOException occured when trying to load property file '" + name + "'" + e.getMessage());
         }
         return ret;
      }
   }
   
   
   public MX4JAdaptor() {
      super((Set)null);
   }
   
   private final void instantiateAdaptor(Global global) throws Exception {
      
      int port = getInt("port", 9999);
      String host = get("host", "localhost");
      String adaptorName = get("adaptorName", "HttpAdaptorMX4J");
      MBeanServer server = global.getJmxWrapper().getMBeanServer();

      HttpAdaptor adapter = new HttpAdaptor();
      this.name = new ObjectName("Adaptor:name=" + adaptorName);
      server.registerMBean(adapter, name);
      adapter.setHost(host);
      adapter.setPort(port);
      
      String xsltProcessor = get("xsltProcessor", null);
      ObjectName processorName = null;
      if (xsltProcessor != null) {

         ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, "jmx" + ContextNode.SEP + xsltProcessor,
               this.global.getContextNode());
         processorName = new ObjectName(JmxWrapper.getObjectNameLiteral(this.global, contextNode));
         
         // XSLTProcessor processor = new XSLTProcessor();
         ContribXsltProcessor processor = new ContribXsltProcessor();
         
         server.registerMBean(processor, processorName);
         
         // set it to use a dir
         String xsltPath = get("xsltPath", null); // can be a directory or a jar file
         String xsltPathInJar = null;

         if (xsltPath != null) {
            processor.setFile(xsltPath);
         }
         boolean xsltCache = getBoolean("xsltCache", true);
         processor.setUseCache(xsltCache);
         // set not to use cache
         String xsltLocale = get("xsltLocale", null);
         if (xsltLocale != null) {
         }
         // adds a mime type
         // server.invoke(processorName, "addMimeType", new Object[] {".pdf", "application/pdf"}, new String[] {"java.lang.String", "java.lang.String"});
         log.info("Xslt Processor: " + xsltProcessor + "' on xsltPath='" + xsltPath + "' and xsltPathInJar='" + xsltPathInJar + "' and xsltCache='" + xsltCache + "' and xsltLocale='" + xsltLocale + "'");
         adapter.setProcessorName(processorName);
      }
      
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
