package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;

import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Enumeration;

/**
 * Holds data about a plugin (immutable). 
 *
 * @author <a href="mailto:Konrad.Krafft@doubleslash.de">Konrad Krafft</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class PluginInfo {

   private LogChannel log;
   private String ME;

   /** e.g. "ProtocolPlugin" */
   private String propertyName;
   /** e.g. "ProtocolPlugin[IOR][1.0]" */
   private String propertyKey;

   /** e.g. "IOR" */
   private String type;
   /** e.g. "1.0" */
   private String version;
   /** e.g. "org.xmlBlaster.protocol.soap.SoapDriver" */
   private String className;

   /** key/values from "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100" */
   private Properties params;
   private Object userData;

   /**
    * @param manager can be null if you only want to parse typeVersion
    * @param typeVersion null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   public PluginInfo(Global glob, I_PluginManager manager, String typeVersion) throws XmlBlasterException {
      if (typeVersion == null) {
         init(glob, manager, null, null);
         return;
      }
      int i = typeVersion.indexOf(',');
      String type_;
      String version_;
      if (i==-1) {  // version is optional
         version_ = null;
         type_ = typeVersion;
      }
      else {
         version_ = typeVersion.substring(i+1);
         type_ = typeVersion.substring(0,i);
      }
      init(glob, manager, type_, version_);
   }

   /**
    * @param type null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   public PluginInfo(Global glob, I_PluginManager manager, String type, String version) throws XmlBlasterException {
      init(glob, manager, type, version);
   }

   /**
    * Use this setUserData() / getUserData() pair to transport some user specific data
    * to postInitialize() if needed
    */
   public void setUserData(Object userData) {
      this.userData = userData;
   }

   public Object getUserData() {
      return this.userData;
   }

   /**
    * @param manager can be null if you only wanted to parse typeVersion
    * @param type null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   private void init(Global glob, I_PluginManager manager, String type_, String version_) throws XmlBlasterException {
      log = glob.getLog("plugin");
      this.type = type_;
      this.version = (version_ == null) ? "1.0" : version_;

      if (manager == null) return;

      propertyName = manager.getName();
      ME = "PluginInfo-"+propertyName;

      if (ignorePlugin()) {
         if (log.TRACE) log.trace(ME, "Plugin type set to 'undef', ignoring plugin");
         return;
      }

      propertyKey = manager.createPluginPropertyKey(type, version); // "ProtocolPlugin[IOR][1.0]"
      String rawString = glob.getProperty().get(propertyKey, (String)null);// "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"

      if (rawString==null) {
         if (this.type != null)
            log.warn(ME, "Plugin '" + toString() + "' not found, choosing default plugin");
         rawString = manager.getDefaultPluginName(this.type, this.version);   // "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
      }

      parsePropertyValue(rawString);
   }
   
   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    */
   private void parsePropertyValue(String rawString) throws XmlBlasterException {
      if (rawString==null) throw new IllegalArgumentException(ME + ".parsePropertyValue(null)");

      params = new Properties();
      if(rawString!=null) {
         StringTokenizer st = new StringTokenizer(rawString, ",");
         boolean first=true;
         while(st.hasMoreTokens()) {
            String tok = (String)st.nextToken();
            if (first) { // The first is always the class name
               className = tok;
               first = false;
               continue;
            }
            int pos = tok.indexOf("=");
            if (pos < 0) {
               log.info(ME, "Accepting param " + tok + " without value (missing '=')");
               params.put(tok, "");
            }
            params.put(tok.substring(0,pos), tok.substring(pos+1));
         }
      }
      else
         throw new XmlBlasterException(ME, "Missing plugin configuration for property " + propertyKey + ", please check your settings");
   }

   /**
    * Check if the plugin is marked with "undef", such configurations are not loaded
    */
   public boolean ignorePlugin() {
      if ("undef".equalsIgnoreCase(type) || "undef,1.0".equalsIgnoreCase(type))
         return true;
      return false;
   }

   public String getClassName() {
      return className;
   }

   public Properties getParameters() {
      return params;
   }

   public String[] getParameterArr() {
      String[] arr = new String[params.size()*2];
      Enumeration e = params.keys();
      int i = 0;
      while(e.hasMoreElements()) {
         String key = (String)e.nextElement();
         arr[i++] = key;
         arr[i++] = (String)params.get(key);
      }
      return arr;
   }

   public String getType() {
      return type;
   }

   public String getVersion() {
      return version;
   }

   public String getTypeVersion() {
      if (type == null) return null;
      if (version == null) return type;
      return type + "," + version;
   }

   /** @return for example "ProtocolPlugin[IOR][1.0]" */
   public String toString() {
      return propertyKey;
   }
}

