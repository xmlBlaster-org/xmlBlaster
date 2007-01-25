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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import mx4j.tools.adaptor.http.HttpAdaptor;
import mx4j.tools.adaptor.http.HttpInputStream;
import mx4j.tools.adaptor.http.HttpOutputStream;
import mx4j.tools.adaptor.http.XSLTProcessor;
import mx4j.util.Base64Codec;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xmlBlaster.contrib.GlobalInfo;
import org.xmlBlaster.contrib.InfoHelper;
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

   public class XblHttpAdaptor extends HttpAdaptor {
      public XblHttpAdaptor() {
         super();
      }
   }
   
   private static Logger log = Logger.getLogger(MX4JAdaptor.class.getName());
   private final static String USER_ROLE = "user";
   private final static String ADMIN_ROLE = "admin";
   private final static String INITIATOR_ROLE = "initiator";

   private ObjectName name;
   private Map roles;
   private String authMethod;
   
   public class ContribXsltProcessor extends XSLTProcessor {
      
      private String prefix;
      private Set warnings;
      private String authMethod; 
      private Map roles;
      
      public ContribXsltProcessor(Map roles, String authMethod) {
         super();
         this.warnings = new HashSet();
         this.roles = roles;
         this.authMethod = authMethod;
      }
      
      public void setFile(String prefix) {
         this.prefix = prefix;
      }
      
      public String getFile(){
         return this.prefix;
      }
      
      private boolean isPathAbsolute(String path) {
         if (path == null)
            return false;
         File test = new File(path.trim());
         return test.isAbsolute();
      }
      
      /**
       *
       * Note on the classpath: to find a resource it has to be according to java nomenclature,
       * it will not find a document specified with '\'.
       * @param path
       * @param cl
       * @param fixFileSeparator
       * @return
       */
      private InputStream getInputStream(String path, ClassLoader cl, boolean fixFileSeparator) {

         log.fine("using classloader '" + cl.getClass().getName() + "'");
         if (path == null)
            log.severe("ContribXsltProcessor.getInputStream: no filename specified");
         // path = this.prefix + File.separatorChar + path;
         log.fine("Requesting path '" + path + "'");
         if (!path.startsWith(File.separator) && !path.startsWith("/"))
         // if (!isPathAbsolute(path)) 
            // path = File.separator + path;
            path = "/" + path;
               
         path = this.prefix + path;
         if (fixFileSeparator)
            path = path.replace('/', File.separatorChar);

         log.fine("Requesting path (after cleaning) '" + path + "'");
         
         InputStream ret = null;
         try {
            Enumeration enm = cl.getResources(path);
            if(enm.hasMoreElements()) {
               URL url = (URL)enm.nextElement();
               String urlTxt = "loading file '" + url.getFile() + "'";
               log.fine(urlTxt);
               try {
                  ret = url.openStream();
               }
               catch(IOException ex) {
                  log.warning("init: could not read properties from '" + url.getFile() + "' : " + ex.getMessage());
               }
               while(enm.hasMoreElements() && !this.warnings.contains(path)) {
                  this.warnings.add(path);
                  url = (URL)enm.nextElement();
                  log.warning(urlTxt + ". An additional matching file has been found in the classpath at '"
                     + url.getFile() + "' please check that the correct one has been loaded (see info above)."
                  );
               }
            }
            else {
               StringBuffer buf = new StringBuffer();
               if (cl instanceof URLClassLoader) {
                  URL[] urls = ((URLClassLoader)cl).getURLs();
                  for (int i=0; i < urls.length; i++) 
                     buf.append(urls[i].toString()).append("\n");
               }
               else {
                  buf.append(" not an URLClassLoader (can not get list of files found) ");
               }
               log.warning("no file found with the name '" + path + "'" /*+ "' : " + (buf.length() > 0 ? " classpath: " + buf.toString() : ""*/);
               log.fine("no file found with the name '" + path + "'" + " classpath: '" + buf.toString() + "'");
            }
         }
         catch(IOException e) {
            log.severe("an IOException occured when trying to load property file '" + name + "'" + e.getMessage());
         }
         return ret;
      }

      protected InputStream getInputStream(String path) {
         boolean fixFileSeparator = false;
         InputStream ret = null;
         if (ret == null)
            ret = getInputStream(path, this.getClass().getClassLoader(), fixFileSeparator);
         /*
         if (ret == null)
            ret = getInputStream(path, ClassLoader.getSystemClassLoader(), fixFileSeparator);
         fixFileSeparator = true;
         */
         /*
         if (ret == null)
            ret = getInputStream(path, this.getClass().getClassLoader(), fixFileSeparator);
         if (ret == null)
            ret = getInputStream(path, ClassLoader.getSystemClassLoader(), fixFileSeparator);
         return ret;
         */
         return ret;
      }

      private String debug(HttpInputStream is) throws IOException {
         Map headers = is.getHeaders();
         Iterator iter = headers.entrySet().iterator();
         StringBuffer buf = new StringBuffer(1024);
         buf.append("headers: \n");
         while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            String key = (String)entry.getKey();
            String val = (String)entry.getValue();
            buf.append("   ").append(key).append("\t: ").append(val).append("\n");
         }
         buf.append("\n");
         buf.append("method\t: ").append(is.getMethod()).append("\n");
         buf.append("path  \t: ").append(is.getPath()).append("\n");
         buf.append("query\t: ").append(is.getQueryString()).append("\n");
         buf.append("query\t: ").append(is.getQueryString()).append("\n");
         buf.append("query\t: ").append(is.getQueryString()).append("\n");
         Map variables  = is.getHeaders();
         iter = variables.entrySet().iterator();
         buf.append("variables: \n");
         while (iter.hasNext()) {
            Map.Entry entry = (Map.Entry)iter.next();
            String key = (String)entry.getKey();
            String val = (String)entry.getValue();
            buf.append("   ").append(key).append("\t: ").append(val).append("\n");
         }
         buf.append("\n");
         return buf.toString();
      }
    
      private String getTemplate(HttpInputStream is) {
         String query = is.getQueryString();
         if (query == null)
            return null;
         final String template = "template=";
         final int length = template.length();
         int pos = query.indexOf(template);
         if (pos < 0)
            return null;
         String end = query.substring(pos + length);
         pos = end.indexOf('&');
         if (pos < 0)
            return end;
         return end.substring(0, pos);
      }
      

      private String extractUserName(HttpInputStream is) throws IOException {
         String authentication = is.getHeader("authorization");
         if (authentication == null)
            return null;
         
         if (this.authMethod.equals("basic")) {
            authentication = authentication.substring(5, authentication.length());
            String decodeString = new String(Base64Codec.decodeBase64(authentication.getBytes()));
            if (decodeString.indexOf(":") > 0) {
               try {
                  StringTokenizer tokens = new StringTokenizer(decodeString, ":");
                  return tokens.nextToken(); // username
               }
               catch (Exception ex) {
                  ex.printStackTrace();
                  return null;
               }
            }
         }
         return null;
      }

      
      public void writeResponse(HttpOutputStream outputStream, HttpInputStream inputStream, Document doc) throws IOException {
         // log.severe(debug(inputStream));
         Element el = doc.getDocumentElement();

         Attr admin = doc.createAttribute(ADMIN_ROLE);
         Attr initiator = doc.createAttribute(INITIATOR_ROLE);
         Attr user = doc.createAttribute(USER_ROLE);
         if (this.authMethod != null) {
            String userName = extractUserName(inputStream);
            if (userName == null)
               userName = "";
            String roles = (String)this.roles.get(userName);
            if (roles == null) {
               admin.setValue("false");
               initiator.setValue("false");
               user.setValue("false");
            }
            else if (roles.indexOf(ADMIN_ROLE) > -1) {
               admin.setValue("true");
               initiator.setValue("true");
               user.setValue("true");
            }
            else if (roles.indexOf(INITIATOR_ROLE) > -1) {
               admin.setValue("false");
               initiator.setValue("true");
               user.setValue("true");
            }
            else {
               admin.setValue("false");
               initiator.setValue("false");
               user.setValue("true");
            }
         }
         else {
            admin.setValue("true");
            initiator.setValue("true");
            user.setValue("true");
         }
         el.setAttributeNode(admin);
         el.setAttributeNode(initiator);
         el.setAttributeNode(user);
         // outputStream.setHeader("Cache-Control", "no-cache");
         // outputStream.setHeader("Content-Type", "text/xml");
         super.writeResponse(outputStream, inputStream, doc);
      }
   }
   
   public MX4JAdaptor() {
      super((Set)null);
      this.roles = new HashMap();
   }
   
   private final void instantiateAdaptor(Global global) throws Exception {
      
      int port = getInt("port", 9999);
      String host = get("host", "0.0.0.0");
      String adaptorName = get("adaptorName", "HttpAdaptorMX4J");
      MBeanServer server = global.getJmxWrapper().getMBeanServer();
      HttpAdaptor adapter = new HttpAdaptor();
      this.name = new ObjectName("Adaptor:name=" + adaptorName);
      server.registerMBean(adapter, name);
      adapter.setHost(host);
      adapter.setPort(port);
      this.authMethod = get("authenticationMethod", null);
      log.info("Authentication Method is '" + this.authMethod + "'");
      if (this.authMethod != null) {
         this.authMethod = this.authMethod.trim();
         if ("basic".equals(this.authMethod))
            adapter.setAuthenticationMethod(this.authMethod);
         else
            log.warning("Authentication method '" + authMethod + "' not recognized, will switch to 'basic'");
         log.info("Authentication Method is '" + this.authMethod + "'");
         Map users = InfoHelper.getPropertiesStartingWith("replication.monitor.user.", this, null);
         if (users != null && users.size() > 0) {
            Iterator iter = users.entrySet().iterator();
            while (iter.hasNext()) {
               Map.Entry entry = (Map.Entry)iter.next();
               String name = (String)entry.getKey();
               String val = (String)entry.getValue();
               log.fine("name='" + name + "' value='" + val + "'");
               int pos = val.indexOf(':');
               String pwd = null;
               String roles = USER_ROLE;
               if (pos > -1) {
                  pwd = val.substring(0, pos);
                  roles = val.substring(pos+1);
               }
               else
                  pwd = val;
               log.fine("registering monitor user '" + name + "' having roles : " + roles + "'");
               adapter.addAuthorization(name, pwd);
               this.roles.put(name, roles);
            }
         }
         else
            log.info("No Users found for the monitor");
      }
      
      String xsltProcessor = get("xsltProcessor", null);
      ObjectName processorName = null;
      if (xsltProcessor != null) {

         ContextNode contextNode = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, "jmx" + ContextNode.SEP + xsltProcessor,
               this.global.getContextNode());
         processorName = new ObjectName(JmxWrapper.getObjectNameLiteral(this.global, contextNode));
         
         // XSLTProcessor processor = new XSLTProcessor();
         ContribXsltProcessor processor = new ContribXsltProcessor(this.roles, this.authMethod);
         
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
