/*------------------------------------------------------------------------------
Name:      GlobalInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file

Switch on finer logging in xmlBlaster.properties:
trace[org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter]=true
trace[org.xmlBlaster.contrib.db.DbPool]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.MD5ChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.AlertScheduler]=true
trace[org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector]=true
trace[org.xmlBlaster.contrib.dbwatcher.plugin.GlobalInfo]=true
trace[org.xmlBlaster.contrib.dbwatcher.mom.XmlBlasterPublisher]=true
trace[org.xmlBlaster.contrib.dbwatcher.DbWatcher]=true
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib;

import org.xmlBlaster.contrib.replication.ReplicationConverter;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ReplaceVariable;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.admin.extern.JmxMBeanHandle;
import org.xmlBlaster.util.context.ContextNode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public abstract class GlobalInfo implements I_Plugin, I_Info {
   public final static String ORIGINAL_ENGINE_GLOBAL = "_originalEngineGlobal";
   public final static int UNTOUCHED = 0;
   public final static int UPPER_CASE = 1;
   public final static int LOWER_CASE = 2;
   
   private static Logger log = Logger.getLogger(GlobalInfo.class.getName());
   protected Global global;
   protected PluginInfo pluginInfo;
   private Map objects = new HashMap();
   private Set propsOfOwnInterest;
   private InfoHelper helper;

   /** My JMX registration */
   private Set jmxHandleSet = new HashSet();
   private ContextNode contextNode;

   public static String getStrippedString(String pureVal) {
      String corrected = Global.getStrippedString(pureVal);
      return ReplaceVariable.replaceAll(corrected, "-", "_");
   }
   
   private final static String fixCase(String val, int chCase) {
      if (val == null)
         return null;
      if (chCase == UPPER_CASE)
         return val.toUpperCase();
      if (chCase == LOWER_CASE)
         return val.toLowerCase();
      return val;
   }
   
   /**
    * Convenience to allow the usage of a name mapped to the hostname which can be used as an identifier in a database.
    * Specifically used for the prefix in the replication.
    * @param info can be null, in which case only system properties are changed.
    */
   public static String setStrippedHostname(I_Info info, int chCase) {
      String hostName = System.getProperty("host.name");
      if (hostName == null) {
         try {
            hostName = fixCase(InetAddress.getLocalHost().getHostName(), chCase);
            if (hostName == null) {
               log.warning("The property 'host.name' was not set and it was not possible to retrieve the default host name (will try the IP Address instead)");
               hostName = fixCase(InetAddress.getLocalHost().getHostAddress(), chCase);
               if (hostName == null) {
                  log.warning("the property 'host.name' is not set, will not set 'stripped.host.name'");
                  return null;
               }
               else {
                  log.warning("the property 'host.name' is not set and the default is set to the IP '" + hostName + "'");
               }
            }
            log.info("Setting 'host.name' to '" + hostName + "'");
            System.setProperty("host.name", hostName);
            info.put("host.name", hostName);
         }
         catch (UnknownHostException ex) {
            log.warning("Could not retrieve the local hostname (I wanted it since 'host.name' was not set)");
            return null;
         }
      }
      String strippedHostName = getStrippedString(hostName);
      String oldStrippedHostName = System.getProperty("stripped.host.name");
      if (oldStrippedHostName != null) {
         if (!oldStrippedHostName.equals(strippedHostName))
            log.warning("The system property 'stripped.host.name' was already set to '" + oldStrippedHostName + "' will NOT change it to '" + strippedHostName + "'");
      }
      else {
         System.setProperty("stripped.host.name", strippedHostName);
         log.fine("Set system property 'stripped.host.name' to '" + strippedHostName + "'");
      }
      if (info != null) {
         oldStrippedHostName = info.get("stripped.host.name", null);
         if (oldStrippedHostName != null) {
            if (!oldStrippedHostName.equals(strippedHostName))
               log.warning("The info property 'stripped.host.name' was already set to '" + oldStrippedHostName + "' will NOT change it to '" + strippedHostName + "'");
         }
         else {
            info.put("stripped.host.name", strippedHostName);
            log.fine("Set info property 'stripped.host.name' to '" + strippedHostName + "'");
         }
      }
      return strippedHostName;
   }
   
   /**
    * Checks in the registry if such an object exitsts and if not it
    * creates one for you and intializes it.
    * @param info The info object to use.
    * @param pluginClassName The complete name of the plugin to load.
    * @param registryName The name to search in the registry for this
    * instance. The registry will be in the info object passed. If you
    * specify null, the lookup is skipped.
    * @return 
    * @throws Exception
    */
   public static Object loadPlugin(I_Info info, String pluginClassName, String registryName) throws Exception {
      synchronized (info) {
         I_ContribPlugin plugin = null;
         if (pluginClassName == null || pluginClassName.length() < 1)
            throw new Exception("loadPlugin: The name of the plugin has not been specified");
         if (registryName != null)
            plugin = (I_ContribPlugin)info.getObject(registryName);
         if (plugin != null) {
            log.fine(pluginClassName + " returned (was already initialized)");
            return plugin;
         }
         ClassLoader cl = ReplicationConverter.class.getClassLoader();
         plugin = (I_ContribPlugin)cl.loadClass(pluginClassName).newInstance();
         plugin.init(info);
         if (registryName != null)
            info.putObject(registryName, plugin);
         log.fine(pluginClassName + " created and initialized");
         return plugin;
      }
   }
   
   
   
   /**
    * 
    */
   public GlobalInfo(Set propsOfOwnInterest) {
      this.propsOfOwnInterest = propsOfOwnInterest;
      if (this.propsOfOwnInterest == null)
         this.propsOfOwnInterest = new HashSet();
      this.helper = new InfoHelper(this);
   }
   
   /**
    * 
    */
   public GlobalInfo(String[] propKeysAsString) {
      this.propsOfOwnInterest = new HashSet();
      if (propKeysAsString != null) {
         for (int i=0; i < propKeysAsString.length; i++)
            this.propsOfOwnInterest.add(propKeysAsString[i]);
      }
      this.helper = new InfoHelper(this);
   }

   /**
    * Additional infos are added on top of the initial Global configuration.
    * 
    * @param otherGlobal can not be null.
    * @param additionalInfo can be null. If not null, these properties will be added on
    * top of the already set in global.
    */
   public GlobalInfo(Global otherGlobal, I_Info additionalInfo) throws XmlBlasterException {
      this.propsOfOwnInterest = new HashSet();
      this.helper = new InfoHelper(this);
      init(otherGlobal, null);
      InfoHelper.fillInfoWithEntriesFromInfo(this, additionalInfo);
   }
   
   /**
    * @param otherInfo
    * @param additionalInfo can be null. If not null, these properties will be added on
    * top of the already set in global.
    * @throws XmlBlasterException
    */
   public GlobalInfo(GlobalInfo baseInfo, I_Info additionalInfo) throws XmlBlasterException {
      this(baseInfo.global, additionalInfo);
   }
   
   /**
    * 
    * @param global The global passed by the RunLevelManager, this is not the object owned by the plugin. It is the original global.
    * @param pluginInfo
    * @throws XmlBlasterException
    */
   protected abstract void doInit(Global global, PluginInfo pluginInfo) throws XmlBlasterException;
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public final void init(Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      String[] additionalAttributes = null;
      // global_.getProperty().getProperties().list(System.out);      
      // boolean wantsClone = wantGlobalClone(global_);
      // if (this.onServer)
      if (global_.isServerSide())
         additionalAttributes = global_.getNativeConnectArgs();
      if (global_.isServerSide() /* || wantsClone */) {
         this.global = global_.getClone(additionalAttributes);
         this.global.addObjectEntry(ORIGINAL_ENGINE_GLOBAL, global_);
         this.global.addObjectEntry(Constants.OBJECT_ENTRY_ServerScope, global_);
      }
      else {
         this.global = global_;
         putObject("org.xmlBlaster.util.Global", this.global);
      }
      setStrippedHostname(this, UPPER_CASE);

      log.entering(this.getClass().getName(), "init");
      this.pluginInfo = pluginInfo;
      if (this.pluginInfo != null) {
         if (log.isLoggable(Level.FINER)) {
            log.finer("init: plugin paramenters: '" + this.pluginInfo.dumpPluginParameters() + "'");
            log.finer("init: plugin user data  : '" + this.pluginInfo.getUserData() + "'");
         }
      }

      // add the property 'id' if not set explicitly already ...
      String id = get(ID, null);
      if (id == null) {
         if (this.pluginInfo != null)
            put(ID, this.pluginInfo.getType());
         else
            log.warning("No id has been defined for this info, please add one since this could be used to find your instance: example '" + ID + "=someId'");
      }
      // To allow NATIVE access to xmlBlaster (there we need to take a clone!)
      // if (this.onServer)
      if (global_.isServerSide())
         putObject("org.xmlBlaster.engine.Global", this.global);
      
      // For JMX instanceName may not contain ","
      if (pluginInfo != null) {
         String instanceName = pluginInfo.getType();
         this.contextNode = new ContextNode(ContextNode.SERVICE_MARKER_TAG,
               instanceName, this.global.getScopeContextNode());
      }

      helper.replaceAllEntries(this, propsOfOwnInterest);
      if (pluginInfo != null && pluginInfo.getParameters() != null) {
         I_Info tmpPluginInfos = new PropertiesInfo(pluginInfo.getParameters());
         helper.replaceAllEntries(tmpPluginInfos, propsOfOwnInterest);
      }
      
      doInit(global_, pluginInfo);
      initJmx();
      replaceAllEntries();
   }

   protected void replaceAllEntries() {
      helper.replaceAllEntries(this, propsOfOwnInterest);
   }
   
   private void initJmx() {
      Map jmxMap = InfoHelper.getObjectsWithKeyStartingWith(JMX_PREFIX, this, null);
      if (jmxMap.size() < 1)
         return;
      String[] keys = (String[])jmxMap.keySet().toArray(new String[jmxMap.size()]);
      for (int i=0; i < keys.length; i++) {
         Object obj = jmxMap.get(keys[i]);
         String name = keys[i];
         ContextNode child = new ContextNode(ContextNode.CONTRIB_MARKER_TAG, name, this.contextNode);
         log.info("MBean '" + name + "' found. Will attach it as '" + child.getRelativeName() + "' to '" + this.contextNode.getAbsoluteName() + "'");
         try {
            JmxMBeanHandle mBeanHandle = this.global.registerMBean(child, obj);
            this.jmxHandleSet.add(mBeanHandle);
         }
         catch(XmlBlasterException e) {
            log.severe(e.getMessage());
         }
      }
   }
   
   /**
    * The plugin name as configured im <tt>xmlBlasterPlugins.xml</tt>
    * @see org.xmlBlaster.util.plugin.I_Plugin#getType()
    */
   public String getType() {
      if (this.pluginInfo != null)
         return this.pluginInfo.getType();
      return null;
   }

   /**
    * The plugin version as configured in <tt>xmlBlasterPlugins.xml</tt>
    * @see org.xmlBlaster.util.plugin.I_Plugin#getVersion()
    */
   public String getVersion() {
      if (this.pluginInfo != null)
         return this.pluginInfo.getVersion();
      return null;
   }

   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      if (this.jmxHandleSet.size() < 1)
         return;
      JmxMBeanHandle[] handles = (JmxMBeanHandle[])this.jmxHandleSet.toArray(new JmxMBeanHandle[this.jmxHandleSet.size()]);
      for (int i=0; i < handles.length; i++) {
         log.info("Unregistering MBean '" + handles[i].getContextNode().getAbsoluteName() + "'");
         this.global.unregisterMBean(handles[i]);
      }
   }
   
   /**
    */
   public String getRaw(String key) {
      if (key == null) return null;
      try {
         if (this.propsOfOwnInterest.contains(key)) {
            String ret = (this.pluginInfo == null) ? null : this.pluginInfo.getParameters().getProperty(key, null);
            String prefix = (this.pluginInfo == null) ? "" : this.pluginInfo.getPrefix(); 
            return this.global.getProperty().get(prefix + key, ret);
         }
         
         String value = this.global.get(key, null, null, this.pluginInfo);
         if ("jdbc.drivers".equals(key) && (value == null || value.length() < 1))
            return this.global.getProperty().get("JdbcDriver.drivers", ""); // ask xmlBlaster.properties
         log.fine("Resolving " + key + " to '" + value + "'");
         return value;
      }
      catch (XmlBlasterException e) {
         log.warning(e.toString());
         return null;
      }
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
    */
   public String get(String key, String def) {
      if (key == null)
         return def;
      def = this.helper.replace(def);
      key = this.helper.replace(key);
      
      // hack: if in topic name is a ${..} our global tries to replace it and
      // throws an exception, but we need it without replacement:
      // $_{...} resolves this issue, but nicer would be:
      //       <attribute id='db.queryMeatStatement' replace='false'>...</attribute>
      try {
         if (this.propsOfOwnInterest.contains(key)) {
            String ret = (this.pluginInfo == null) ? def : this.pluginInfo.getParameters().getProperty(key, def);
            ret = this.helper.replace(ret);
            String prefix = (this.pluginInfo == null) ? "" : this.pluginInfo.getPrefix(); 
            return this.global.getProperty().get(prefix + key, ret);
         }
         
         if ("jdbc.drivers".equals(key)) {
            def = global.getProperty().get("JdbcDriver.drivers", def); // ask xmlBlaster.properties
         }

         String value = this.global.get(key, def, null, this.pluginInfo);
         value = this.helper.replace(value);
         
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
       if (key != null)
          key = this.helper.replace(key);
       if (value != null)
          value = this.helper.replace(value);
       if (value == null)
          this.global.getProperty().removeProperty(key);
       else {
          try {
             String prefix = (this.pluginInfo == null) ? "" : pluginInfo.getPrefix();  // "plugin/" + getType() + "/"
             this.global.getProperty().set(prefix + key, value);
          }
          catch (Exception e) {
             
             log.warning(e.toString() + ": Ignoring setting " + key + "=" + value);
          }
       }
    }

    /**
     * @see org.xmlBlaster.contrib.I_Info#put(java.lang.String, java.lang.String)
     */
     public void putRaw(String key, String value) {
        if (value == null)
           this.global.getProperty().removeProperty(key);
        else {
           try {
              String prefix = (this.pluginInfo == null) ? "" : pluginInfo.getPrefix();  // "plugin/" + getType() + "/"
              this.global.getProperty().set(prefix + key, value);
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
      if (key == null)
         return def;
      key = this.helper.replace(key);
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
      if (key == null)
         return def;
      key = this.helper.replace(key);
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
      if (key == null)
         return def;
      key = this.helper.replace(key);
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
      return this.objects.get(key);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#putObject(java.lang.String, Object)
    */
   public Object putObject(String key, Object o) {
      if (o == null)
         return this.objects.remove(key);
      return this.objects.put(key, o);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getKeys()
    */
   public Set getKeys() {
      Iterator iter = this.global.getProperty().getProperties().keySet().iterator();
      HashSet out = new HashSet();
      String prefix = "";
      if (this.pluginInfo != null)
         prefix = this.pluginInfo.getPrefix();
      while (iter.hasNext()) {
         String key = (String)iter.next();
         if (key.startsWith(prefix))
            key = key.substring(prefix.length());
            out.add(key);
      }
      if (this.pluginInfo != null)
         PropertiesInfo.addSet(out, this.pluginInfo.getParameters().keySet());
      return out;
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#getObjectKeys()
    */
   public Set getObjectKeys() {
      return this.objects.keySet();
   }
   
   public static String dump(I_Info info) {
      StringBuffer buf = new StringBuffer();
      Iterator iter = info.getKeys().iterator();
      while (iter.hasNext()) {
         String key = (String)iter.next();
         String val = info.get(key, "");
         buf.append(key).append("=").append(val).append("\n");
      }
      return buf.toString();
   }
   
   public Global getGlobal() {
      return this.global;
   }
   
   private static Global getOriginalGlobal(Global global, boolean recursive) {
      if (global == null)
         return null;
      Global glob = (Global)global.getObjectEntry(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
      if (glob == null)
         glob = (Global)global.getObjectEntry(Constants.OBJECT_ENTRY_ServerScope);
      if (glob == null)
         glob = (Global)global.getObjectEntry("org.xmlBlaster.engine.Global"); // ServerScope ?
      if (glob == null)
         glob = (Global)global.getObjectEntry("org.xmlBlaster.util.Global");
      if (recursive) {
         if (glob != null && glob != global)
            return getOriginalGlobal(glob, recursive);
      }
      else {
         if (glob == null)
            return global;
         return glob;
      }
      return global;
   }
   
   /**
    * Returns the Base global. The Base Global is the Global 
    * @param info
    * @return
    */
   public static Global getOriginalGlobal(I_Info info) {
      final boolean recursive = false; 
      Global glob = (Global)info.getObject(GlobalInfo.ORIGINAL_ENGINE_GLOBAL);
      if (glob == null)
         glob = (Global)info.getObject(Constants.OBJECT_ENTRY_ServerScope);
      if (glob == null)
         glob = (Global)info.getObject("org.xmlBlaster.engine.Global"); // ServerScope ?
      if (glob == null)
         glob = (Global)info.getObject("org.xmlBlaster.util.Global");
      if (glob != null)
         return getOriginalGlobal(glob, recursive);
      return null;
   }
}
