/*------------------------------------------------------------------------------
Name:      DbWatcherPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.plugin;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;


import java.util.Map;
import java.util.HashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * DbWatcherPlugin is a plugin wrapper if you want to run DbWatcher inside xmlBlaster. 
 * <p />
 * DbWatcher checks a database for changes and publishes these to the MoM
 * <p />
 * This plugin needs to be registered in <tt>xmlBlasterPlugins.xml</tt>
 * to be available on xmlBlaster server startup.
 * <pre>
&lt;plugin id='DbWatcherPlugin.TEST_TS' className='org.xmlBlaster.contrib.dbwatcher.plugin.DbWatcherPlugin'>
   &lt;attribute id='jdbc.drivers'>oracle.jdbc.driver.OracleDriver&lt;/attribute>
   &lt;attribute id='db.url'>${db.url}&lt;/attribute>
   &lt;attribute id='db.user'>${db.user}&lt;/attribute>
   &lt;attribute id='db.password'>${db.password}&lt;/attribute>
   &lt;attribute id='db.queryMeatStatement'>SELECT * FROM TEST_TS WHERE TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF') > '${oldTimestamp}' ORDER BY ICAO_ID&lt;/attribute>
   &lt;attribute id='mom.topicName'>db.change.event.${groupColValue}&lt;/attribute>
   &lt;attribute id='mom.loginName'>dbWatcher/3&lt;/attribute>
   &lt;attribute id='mom.password'>secret&lt;/attribute>
   &lt;attribute id='mom.alertSubscribeKey'>&lt;key oid=''/>&lt;/attribute>
   &lt;attribute id='mom.alertSubscribeQos'>&lt;qos/>&lt;/attribute>
   &lt;attribute id='changeDetector.class'>org.xmlBlaster.contrib.dbwatcher.detector.TimestampChangeDetector&lt;/attribute>
   &lt;attribute id='alertScheduler.pollInterval'>10000&lt;/attribute>
   &lt;attribute id='changeDetector.groupColName'>ICAO_ID&lt;/attribute>
   &lt;attribute id='changeDetector.detectStatement'>SELECT MAX(TO_CHAR(ts, 'YYYY-MM-DD HH24:MI:SSXFF')) FROM TEST_TS&lt;/attribute>
   &lt;attribute id='converter.class'>org.xmlBlaster.contrib.dbwatcher.convert.ResultSetToXmlConverter&lt;/attribute>
   &lt;attribute id='converter.addMeta'>true&lt;/attribute>
   &lt;attribute id='transformer.class'>&lt;/attribute>
   &lt;action do='LOAD' onStartupRunlevel='9' sequence='6' onFail='resource.configuration.pluginFailed'/>
   &lt;action do='STOP' onShutdownRunlevel='6' sequence='5'/>
&lt;/plugin>
 * </pre>
 * 
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class DbWatcherPlugin implements I_Plugin, I_Info {
   private static Logger log = Logger.getLogger(DbWatcherPlugin.class.getName());
   private Global global;
   private PluginInfo pluginInfo;
   private Map objects = new HashMap();
   private String converterClassName;
   private String changeDetectorClassName;
   private DbWatcher dbWatcher;
   
   /**
    * Default constructor, you need to call <tt>init()<tt> thereafter.
    */
   public DbWatcherPlugin() {
      // void
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      this.global = global_; // .getClone(null);
      log.entering(this.getClass().getName(), "init");
      this.pluginInfo = pluginInfo;
      if (log.isLoggable(Level.FINER)) {
         log.finer("init: plugin paramenters: '" + this.pluginInfo.dumpPluginParameters() + "'");
         log.finer("init: plugin user data  : '" + this.pluginInfo.getUserData() + "'");
      }
      
      // To allow NATIVE access to xmlBlaster (there we need to take a clone!)
      putObject("org.xmlBlaster.engine.Global", this.global);
      
      try {
         this.dbWatcher = new DbWatcher(this);
         this.dbWatcher.startAlertProducers();
      }
      catch (Throwable e) {
         throw new XmlBlasterException(this.global, ErrorCode.RESOURCE_CONFIGURATION, "DbWatcherPlugin", "init failed", e); 
      }
      log.info("Loaded DbWatcher plugin '" + getType() + "'");
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
    * @see org.xmlBlaster.util.plugin.I_Plugin#shutdown()
    */
   public void shutdown() throws XmlBlasterException {
      try {
         this.dbWatcher.shutdown();
      }
      catch (Throwable e) {
         log.warning("Ignoring shutdown problem: " + e.toString());
      }
      log.info("Stopped DbWatcher plugin '" + getType() + "'");
   }
   
   /**
    * @see org.xmlBlaster.contrib.I_Info#get(java.lang.String, java.lang.String)
    */
   public String get(String key, String def) {
      if (key == null) return def;
      
      try {
         // hack: if in topic name is a ${..} our global tries to replace it and
         // throws an exception, but we need it without replacement:
         // $_{...} resolves this issue, but nicer would be:
         //       <attribute id='db.queryMeatStatement' replace='false'>...</attribute>
         if ("mom.topicName".equals(key) || "mom.publishKey".equals(key) ||
             "mom.alertSubscribeKey".equals(key) || "db.queryMeatStatement".equals(key) ||
             "db.typeStatement".equals(key)) {
            String ret = (this.pluginInfo == null) ? def : this.pluginInfo.getParameters().getProperty(key, def);
            String prefix = (this.pluginInfo == null) ? "" : this.pluginInfo.getPrefix(); 
            return this.global.getProperty().get(prefix + key, ret);
         }
         
         String value = this.global.get(key, def, null, this.pluginInfo);
         if ("jdbc.drivers".equals(key) && (value == null || value.length() < 1))
            return this.global.getProperty().get("JdbcDriver.drivers", ""); // ask xmlBlaster.properties
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
      return this.objects.get(key);
   }

   /**
    * @see org.xmlBlaster.contrib.I_Info#putObject(java.lang.String, Object)
    */
   public Object putObject(String key, Object o) {
      return this.objects.put(key, o);
   }
}
