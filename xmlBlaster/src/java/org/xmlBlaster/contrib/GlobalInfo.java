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

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.log.XmlBlasterJdk14LoggingHandler;

import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 * @author <a href="mailto:xmlblast@marcelruff.info">Marcel Ruff</a>
 */
public class GlobalInfo implements I_Plugin, I_Info {
   private static Logger log = Logger.getLogger(GlobalInfo.class.getName());
   protected Global global;
   protected PluginInfo pluginInfo;
   private Map objects = new HashMap();
   private Set propsOfOwnInterest;
   
   /**
    * 
    */
   public GlobalInfo(Set propsOfOwnInterest) {
      this.propsOfOwnInterest = propsOfOwnInterest;
      if (this.propsOfOwnInterest == null)
         this.propsOfOwnInterest = new HashSet();
   }
   
   /**
    * 
    */
   public GlobalInfo(String[] propKeysAsString) {
      this.propsOfOwnInterest = new HashSet();
      if (propsOfOwnInterest != null) {
         for (int i=0; i < propKeysAsString.length; i++)
            this.propsOfOwnInterest.add(propKeysAsString[i]);
      }
   }
   
   /**
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global, org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(Global global_, PluginInfo pluginInfo) throws XmlBlasterException {
      this.global = global_; // .getClone(null); -> is done in XmlBlasterPublisher

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
      
      // To allow NATIVE access to xmlBlaster (there we need to take a clone!)
      putObject("org.xmlBlaster.engine.Global", this.global);
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
         if (this.propsOfOwnInterest.contains(key)) {
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
