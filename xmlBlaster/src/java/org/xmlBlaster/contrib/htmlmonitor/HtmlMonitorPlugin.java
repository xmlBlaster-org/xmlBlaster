/*------------------------------------------------------------------------------
Name:      HtmlMonitorPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file

Switch on finer logging in xmlBlaster.properties:
trace[org.xmlBlaster.contrib.htmlmonitor.HtmlMonitorPlugin]=true
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.htmlmonitor;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.http.HttpIORServer;
import org.xmlBlaster.util.http.I_HttpRequest;
import org.xmlBlaster.util.http.HttpResponse;
import org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.key.QueryKeyData;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.I_ReplaceVariable;


import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.io.File;

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
   &lt;attribute id='user'>joe&lt;/attribute>
   &lt;attribute id='password'>secret&lt;/attribute>
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
public class HtmlMonitorPlugin implements I_Plugin, I_Info, I_HttpRequest {
   private static Logger log = Logger.getLogger(HtmlMonitorPlugin.class.getName());
   private Global global;
   private PluginInfo pluginInfo;
   private HttpIORServer httpServer;
   private String urlPath;
   private String documentRoot;
   private CommandManager commandManager;
   private SessionInfo sessionInfo;
   private ReplaceVariable replaceVariable = new ReplaceVariable();
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public HtmlMonitorPlugin() {
      // void
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
      this.urlPath = get("urlPath", "/monitor");

      this.httpServer.registerRequest(this.urlPath, this);

      log.info("Loaded HtmlMonitor plugin '" + getType() +
               "', registered with urlPath=" + this.urlPath +
               " using documentRoot=" + this.documentRoot);
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

   /**
    * A HTTP request needs to be processed
    * @param urlPath The url path like "/monitor" which triggered this call
    * @param properties The key values from the browser
    * @return The HTML page to return
    */
   public HttpResponse service(String urlPath, Map properties) {
      try {
         File f = new File(urlPath);
         String name = (urlPath.toLowerCase().indexOf(".html")!=-1 || urlPath.toLowerCase().indexOf(".htm")!=-1) ? f.getName() : "index.html";
         if (log.isLoggable(Level.FINE)) log.fine("Invoking with '" + urlPath + "' urlPath, name='" + name + "'");
         File template = new File(this.documentRoot, name);
         String text = org.jutils.io.FileUtil.readAsciiFile(template.toString());
         if (log.isLoggable(Level.FINE)) log.fine("Reading template  '" + template.toString() + "'");
         text = replaceAllVariables(text);
         return new HttpResponse(text);
      }
      catch (Throwable e) {
         e.printStackTrace();
         return new HttpResponse("<html><h2>" + e.toString() + "</h2></html>");
      }
   }

   
   /**
    * Replace ${...} occurences. 
    * @param template The template text containing ${}
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
         this.httpServer.removeRequest(this.urlPath);
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      log.info("Stopped HtmlMonitor plugin '" + getType() + "'");
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
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

   /**
    * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
    */
    public void put(String key, String value) {
       if (value == null)
          this.global.getProperty().removeProperty(key);
       else {
          try {
             this.global.getProperty().set(key, value);
          }
          catch (Exception e) {
             log.warning(e.toString());
          }
       }
    }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getLong(java.lang.String, long)
    */
   public long getLong(String key, long def) {
      if (key == null) return def;
      try {
        return this.global.get(key, def, null, this.pluginInfo);
      }
      catch (XmlBlasterException e) {
         log.warning(e.toString());
         return def;
      }
   }

    /**
    * @see org.xmlBlaster.contrib.I_Info#getInt(java.lang.String, int)
    */
   public int getInt(String key, int def) {
      if (key == null) return def;
      try {
        return this.global.get(key, def, null, this.pluginInfo);
      }
      catch (XmlBlasterException e) {
         log.warning(e.toString());
         return def;
      }
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getBoolean(java.lang.String, boolean)
    */
   public boolean getBoolean(String key, boolean def) {
      if (key == null) return def;
      try {
        return this.global.get(key, def, null, this.pluginInfo);
      }
      catch (XmlBlasterException e) {
         log.warning(e.toString());
         return def;
      }
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObject(java.lang.String)
    */
   public Object getObject(String key) {
      return null;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#putObject(java.lang.String, Object)
    */
   public Object putObject(String key, Object o) {
      return null;
   }
}
