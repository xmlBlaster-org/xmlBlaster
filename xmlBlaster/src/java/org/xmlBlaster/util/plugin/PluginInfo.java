package org.xmlBlaster.util.plugin;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.context.ContextNode;

import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Enumeration;
import org.xmlBlaster.util.enum.ErrorCode;

/**
 * Holds data about a plugin (immutable). 
 *
 * @author <a href="mailto:Konrad.Krafft@doubleslash.de">Konrad Krafft</a>
 * @author <a href="mailto:xmlBlaster@marcelruff.info">Marcel Ruff</a>
 */
public class PluginInfo {
   private Global glob;
   private LogChannel log;
   private String ME;

   /** e.g. "ProtocolPlugin" */ // can be removed ...
   private String propertyName;
   /** e.g. "ProtocolPlugin[IOR][1.0]" */
   private String propertyKey;   // can be removed ...

   /** e.g. "IOR" */
   private String type;
   /** e.g. "1.0" */
   private String version;
   /** e.g. "org.xmlBlaster.protocol.soap.SoapDriver" */
   private String className;

   /** key/values from "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100" */
   private Properties params;
   private Object userData;

   public PluginInfo(Global glob, String type, String className, Properties params) {
      ME = "PluginInfo-" + type;
      this.glob = glob;
      this.log = glob.getLog("core");
      if (this.log.CALL) this.log.call(ME, "constructor type='" + type + "' className='" + className + "'");
      this.type = type;
      this.className = className;
      this.params = params;
      this.version = "1.0"; // for the moment. Later remove this
   }

   public String getId() {
      return getTypeVersion();
   }

   /**
    * @param manager can be null if you only want to parse typeVersion
    * @param typeVersion null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   public PluginInfo(Global glob, I_PluginManager manager, String typeVersion) throws XmlBlasterException {
      this(glob, manager, typeVersion, (ContextNode)null);
   }

   /**
    * @param manager can be null if you only want to parse typeVersion
    * @param typeVersion null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   public PluginInfo(Global glob, I_PluginManager manager, String typeVersion, ContextNode contextNode) throws XmlBlasterException {
      if (typeVersion == null) {
         init(glob, manager, (String)null, (String)null, (ContextNode)null);
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
      init(glob, manager, type_, version_, contextNode);
   }

   /**
    * From pluginEnvClass and instanceId we build a string to lookup the key in the environment
    * e.g. "/xmlBlaster/node/heron/persistence/topicStore/PersistencePlugin[JDBC][1.0]"
    * @param manager can be null if you only wanted to parse typeVersion
    * @param type null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    * @param pluginEnvClass The classname for environment lookup e.g. "queue" or "persistence"
    * @param instanceId The instance name of the plugin e.g. "history" or "topicStore"
    */
   public PluginInfo(Global glob, I_PluginManager manager, String type, String version, 
                     ContextNode contextNode) throws XmlBlasterException {
      init(glob, manager, type, version, contextNode);
   }

   /**
    * @param type null: Choose default plugin pluginManager.getDefaultPluginName() <br />
    *             "undef": Don't load the plugin
    *             else: Load the given plugin or throw exception
    */
   public PluginInfo(Global glob, I_PluginManager manager, String type, String version) throws XmlBlasterException {
      this(glob, manager, type, version, (ContextNode)null);
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
    * see javadoc of constructor
    */
   private void init(Global glob, I_PluginManager manager, String type_, String version_,
                     ContextNode contextNode) throws XmlBlasterException {
      this.glob = glob;
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

      // propertyKey="ProtocolPlugin[IOR][1.0]"
      propertyKey = manager.createPluginPropertyKey(type, version);
      
      // Search for e.g. "ProtocolPlugin[IOR][1.0]" or "/xmlBlaster/node/heron/ProtocolPlugin[IOR][1.0]"
      String defaultClass = (String)null;
      PropString prop = new PropString(defaultClass);
      String usedPropertyKey = prop.setFromEnv(glob, contextNode, propertyKey);
      
      if (log.TRACE) log.trace(ME, "Trying contextNode=" + ((contextNode==null)?"null":contextNode.getRelativeName()) + " propertyKey=" + propertyKey);

      String rawString = prop.getValue();// "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"

      if (rawString==null) {
         if (this.type != null) {
            if (log.TRACE) log.trace(ME, "Plugin '" + toString() + "' not found, giving up.");
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION, ME, "Plugin '" + toString() + "' not found, please check your configuration");
         }
         rawString = manager.getDefaultPluginName(this.type, this.version);   // "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
      }

      parsePropertyValue(rawString);
   }
   
   /**
    * @param rawString e.g. "org.xmlBlaster.protocol.soap.SoapDriver,classpath=xerces.jar:soap.jar,MAXSIZE=100"
    */
   private void parsePropertyValue(String rawString) throws XmlBlasterException {
      if (rawString==null) throw new IllegalArgumentException(ME + ".parsePropertyValue(null)");

      this.params = new Properties();
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
               this.params.put(tok, "");
            }
            this.params.put(tok.substring(0,pos), tok.substring(pos+1));
         }
      }
      else
         throw new XmlBlasterException(this.glob, ErrorCode.RESOURCE_CONFIGURATION, ME + ".parsePropertyValue", "Missing plugin configuration for property " + propertyKey + ", please check your settings");
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

   /**
    * @return The configuration, never null
    */
   public Properties getParameters() {
      if (this.params == null) {
         this.params = new Properties();
      }
      return this.params;
   }

   private String[] getParameterArr() {
      String[] arr = new String[getParameters().size()*2];
      Enumeration e = this.params.keys();
      int i = 0;
      while(e.hasMoreElements()) {
         String key = (String)e.nextElement();
         arr[i++] = key;
         arr[i++] = (String)this.params.get(key);
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

   /**
    * Dumps the parameters passed to the plugin. So if you defined a property in
    * the property file like this:
<pre>
QueuePlugin[CACHE][1.0]=org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin,\
                        persistentQueue=JDBC,\
                        transientQueue=RAM
</pre>
    * It will be returnes as a string:
<pre>
org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin,persistentQueue=JDBC,transientQueue=RAM
</pre>
    * This method can be used to partially change a plugin property like in the
    * following example:
<pre>
  PluginInfo pluginInfo = new PluginInfo(glob, glob.getQueuePluginManager(), "JDBC", "1.0");
  Properties p = pluginInfo.getParameters();
  p.setProperty("tablePrefix", "test_");
  glob.getProperty().set("QueuePlugin[JDBC][1.0]", pluginInfo.dumpPluginParameters());
</pre>
    */
   public String dumpPluginParameters() {
      String[] arr = this.getParameterArr();
      StringBuffer buf = new StringBuffer();
      buf.append(this.className);
      if (arr.length > 0) buf.append(',');

      char ch = ',';
      for (int i=0; i< arr.length; i++) {
         buf.append(arr[i]);
         if (ch == ',') ch ='='; else ch = ',';
         if (i !=arr.length-1) buf.append(ch);
      }
      return buf.toString();
   }

   /** @return for example "ProtocolPlugin[IOR][1.0]" */
   public String toString() {
      return propertyKey;
   }
}

