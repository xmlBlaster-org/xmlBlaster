/*------------------------------------------------------------------------------
Name:      HtmlMonitorPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file

Switch on finer logging in xmlBlaster.properties:
trace[org.xmlBlaster.contrib.htmlmonitor.HtmlMonitorPlugin]=true
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.htmlmonitor;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.admin.extern.JmxWrapper;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.http.HttpIORServer;
import org.xmlBlaster.util.http.I_HttpRequest;
import org.xmlBlaster.util.http.HttpResponse;
import org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.I_ReplaceVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.StringTokenizer;
import java.io.File;
import java.net.URLDecoder;

/**
 * HtmlMonitorPlugin is a native plugin to build simple HTML monitoring pages. 
 * <p />
 * The HTML pages are available over the tiny xmlBlaster HTTP server.
 * <p />
 * This plugin needs to be registered in <tt>xmlBlasterPlugins.xml</tt>
 * to be available on xmlBlaster server startup.
 * <pre>
&lt;plugin id='HtmlMonitorPlugin.MyCompany' className='org.xmlBlaster.contrib.htmlmonitor.HtmlMonitorPlugin'>
   &lt;attribute id='urlPath'>/monitor&lt;/attribute>
   &lt;attribute id='documentRoot'>${user.home}${file.separator}html&lt;/attribute>
   &lt;attribute id='urlPath.CLASSPATH'>/status.html&lt;/attribute>
   &lt;action do='LOAD' onStartupRunlevel='9' sequence='6' onFail='resource.configuration.pluginFailed'/>
   &lt;action do='STOP' onShutdownRunlevel='6' sequence='5'/>
&lt;/plugin>
 * </pre>
 *
 * <p>
 * Setting urlPath to <tt>/monitor</tt> and documentRoot to <tt>/home/xmlblast/html</tt>
 * and invoking in the browser <tt>http://localhost:3412/monitor/x.html</tt>
 * will lookup <tt>/home/xmlblast/html/x.html</tt>
 * </p>
 *
 * <p>
 * This plugin uses <tt>java.util.logging</tt> and redirects the logging to xmlBlasters default
 * logging framework. You can switch this off by setting the attribute <tt>xmlBlaster/jdk14loggingCapture</tt> to false.
 * </p>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class HtmlMonitorPlugin implements I_Plugin, I_HttpRequest {
   private static Logger log = Logger.getLogger(HtmlMonitorPlugin.class.getName());
   private Global global;
   private PluginInfo pluginInfo;
   private HttpIORServer httpServer;
   private Set urlPathClasspathSet = new HashSet();
   private String documentRoot;
   private CommandManager commandManager;
   private ReplaceVariable replaceVariable = new ReplaceVariable();
   
   private Map mimeTypes = new HashMap();
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public HtmlMonitorPlugin() {
      mimeTypes.put("css", Constants.MIME_CSS);
      mimeTypes.put("html", Constants.MIME_HTML);
      mimeTypes.put("htm", Constants.MIME_HTML);
      mimeTypes.put("xml", Constants.MIME_XML);
      mimeTypes.put("js", Constants.MIME_JS);
      mimeTypes.put("png", Constants.MIME_PNG);
      mimeTypes.put("jpg", Constants.MIME_JPG);
      mimeTypes.put("gif", Constants.MIME_GIF);
      // to be extended
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      this.global = (Global)global_; // .getClone(null); -> is done in XmlBlasterPublisher

      boolean jdk14loggingCapture = this.global.getProperty().get("xmlBlaster/jdk14loggingCapture", true);
      if (jdk14loggingCapture) {
         try {
            XmlBlasterJdk14LoggingHandler.initLogManager(this.global);
         }
         catch (Throwable e) {
            log.warning("Capturing JDK 1.4 logging output failed: " + e.toString());
         }
      }

      log.entering(this.getClass().getName(), "init");
      this.pluginInfo = pluginInfo;
      if (log.isLoggable(Level.FINER)) {
         log.finer("init: plugin paramenters: '" + this.pluginInfo.dumpPluginParameters() + "'");
         log.finer("init: plugin user data  : '" + this.pluginInfo.getUserData() + "'");
      }
      
      // We assume that the RequestBroker has created a commandManager already
      this.commandManager = this.global.getCommandManager(null);

      this.documentRoot = get("documentRoot", get("user.home","")+get("file.separator","/")+"html");

      this.httpServer = this.global.getHttpServer();
      if (this.httpServer == null) {
         log.info("No http server is available");
         return;
      }

      String urlPathList = get("urlPath", ""); // "/monitor"
      StringTokenizer st = new StringTokenizer(urlPathList, ",");
      while (st.hasMoreTokens()) {
         String path = (String)st.nextToken();
         if (path != null && path.length() > 0) {
            this.httpServer.registerRequest(path, this);
         }
      }

      String urlPathClasspathList = get("urlPath.CLASSPATH", ""); // "status.html"
      st = new StringTokenizer(urlPathClasspathList, ",");
      while (st.hasMoreTokens()) {
         String path = (String)st.nextToken();
         if (path != null && path.length() > 0) {
            this.urlPathClasspathSet.add(path);
            this.httpServer.registerRequest(path, this);
         }
      }

      String path = (urlPathClasspathList.indexOf("/status.html") != -1) ? "/status.html" : "/...";

      log.info("Loaded HtmlMonitor plugin '" + getType() +
               "', registered with urlPath='" + urlPathList +
               "' using documentRoot=" + this.documentRoot +
               " and '" + urlPathClasspathList + "' from CLASSPATH, try http://" +
               this.httpServer.getSocketInfo() + path);
   }

   /**
    * The plugin name as configured im <tt>xmlBlasterPlugins.xml</tt>
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      return this.pluginInfo.getType();
   }

   /**
    * The plugin version as configured in <tt>xmlBlasterPlugins.xml</tt>
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      return this.pluginInfo.getVersion();
   }
   
   private String getMimeType(String fileName) {
      int index = fileName.lastIndexOf('.');
      if (index < 0 || (index + 1) >= fileName.length()) {
         return Constants.MIME_HTML;
      }
      String extension = fileName.substring(index + 1);
      return (String) (mimeTypes.containsKey(extension)?mimeTypes.get(extension):Constants.MIME_HTML);
   }

   /**
    * A HTTP request needs to be processed
    * @param urlPath The url path like "/monitor/show.html" or "/status.html" which triggered this call
    *        contains the real file name as given in the browser window including the
    *        leading slash '/'.
    * @param properties The key values from the browser
    * @return The HTML page to return
    */
   public HttpResponse service(String urlPath, Map properties) {
	  if (log.isLoggable(Level.FINE)) {
         log.fine("Invoking with '" + urlPath + "' urlPath, properties='" + properties + "'");
	  }
      if (urlPath == null || urlPath.length() < 1) {
         return new HttpResponse("<html><h2>Empty request, please provide a URL path</h2></html>");
      }
      try {
         String path;
         String parameter = null;
    	  if (urlPath.indexOf('?') > -1) {
			 // has Parameter
    		 path = urlPath.substring(0, urlPath.indexOf('?'));
          parameter = URLDecoder.decode(urlPath.substring(urlPath.indexOf('?') + 1,
 					urlPath.length()), Constants.UTF8_ENCODING);
		 } else {
			// has no parameter 
			path = urlPath;
		 }
         byte[] text;
         String mimeType = getMimeType(path.toString());
         if (this.urlPathClasspathSet.contains(path)) { // "/status.html"
            if (urlPath.startsWith("/")) {
               // "status.html": lookup where the java class resides in xmlBlaster.jar
               urlPath = urlPath.substring(1);
            }
            text = Global.getFromClasspath(urlPath, this);
            if (log.isLoggable(Level.FINE)) log.fine("Reading '" + urlPath + "' from CLASSPATH");
         }
         else {
            File f = new File(path);
            // FIXME: check for security
            // dangerous because one could send: ../../../
            String name = f.getName();
            if (log.isLoggable(Level.FINE)) log.fine("Invoking with '" + urlPath + "' urlPath, name='" + name + "'");
            File template = new File(this.documentRoot, name);
            text = org.jutils.io.FileUtil.readFile(template.toString());
            if (log.isLoggable(Level.FINE)) log.fine("Reading template  '" + template.toString() + "'");
         }
         if (parameter != null && !(parameter.length() < 0)) {
            invokeAction(parameter);
         }
         if (mimeType.startsWith("text")) {
            text = replaceAllVariables(new String(text)).getBytes();
         }
         return new HttpResponse(text, mimeType);
      }
      catch (Throwable e) {
         e.printStackTrace();
         return new HttpResponse("<html><h2>" + e.toString() + "</h2></html>");
      }
   }

   /**
    * Checks for parameter in the URL and if it contains an invokeAction part,
    * it starts this action.
    * 
    * @param parameter
    *           contains the parameter given in the URL.
    */
   private void invokeAction(String parameter) {
		if (log.isLoggable(Level.FINE)) {
			log.fine("Using '" + parameter + "'");
		}

      JmxWrapper jmxwrapper = null;
      try {
         jmxwrapper = JmxWrapper.getInstance(global);
      } catch (XmlBlasterException e) {
         // TODO Auto-generated catch block
         e.printStackTrace();
      }
      
      Object obj = jmxwrapper.invokeAction(parameter);
      
      if (log.isLoggable(Level.FINE)) {
         log.fine("return '" + obj + "'");
      }

   }

/**
 * Replace ${...} occurrences.
 * 
 * @param template
 *            The template text containing ${}
 * @return The result text with replaced ${}
 */
   private String replaceAllVariables(String template) throws XmlBlasterException {
      String text = replaceVariable.replace(template,
         new I_ReplaceVariable() {
            public String get(String key) {
               try {
                  return lookup(key);
               }
               catch (XmlBlasterException e) {
                  log.warning("Replacing variable for '" + key + "' failed: " + e.getMessage());
                  return null;
               }
            }
         });
      return text;
   }

   /**
    * Lookup the given administrative command. 
    * @return The result of the command
    */
   private String lookup(String query) throws XmlBlasterException {
      QueryKeyData keyData = new QueryKeyData(this.global);
      keyData.setOid("__cmd:" + query);
      MsgUnit[] msgs = this.commandManager.get(null, null, keyData, null);
      if (msgs.length == 0) return null;
      StringBuffer sb = new StringBuffer(msgs.length * 40);
      for (int ii=0; ii<msgs.length; ii++) {
         MsgUnit msg = msgs[ii];
         sb.append(msg.getContentStr());
      }
      return sb.toString();
   }


   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      try {
         this.httpServer.removeRequest(this);
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      log.info("Stopped HtmlMonitor plugin '" + getType() + "'");
   }
   
   /**
    * Access a property. 
    * @param key The key to lookup
    * @param value The found value
    */
   public String get(String key, String def) {
      if (key == null) return def;
      
      try {
         String value = this.global.get(key, def, null, this.pluginInfo);
         if (value == null || value.equals(def)) {
            value = this.global.getProperty().get(key, def);
         }
         log.fine("Resolving " + key + " to '" + value + "'");
         return value;
      }
      catch (XmlBlasterException e) {
         log.warning(e.toString());
         return def;
      }
   }
}
