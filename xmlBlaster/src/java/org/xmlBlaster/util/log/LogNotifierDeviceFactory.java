/*------------------------------------------------------------------------------
Name:      LogNotifierDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.log.I_LogDeviceFactory;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.jutils.init.Property;
import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;

import java.util.Properties;
import java.util.HashSet;
import java.util.Set;

/**
 * Factory to register to get a notification if a log.error or log.warn occurs. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
 */
public class LogNotifierDeviceFactory implements I_LogDeviceFactory {
   private static final String ME = "LogNotifierDeviceFactory";
   private org.xmlBlaster.util.Global glob;
   private PluginInfo info;
   private LogNotifierDevice logNotifierDevice; // This factory has exactly one device instance
   
   /**
    * Constructor. 
    */
   public LogNotifierDeviceFactory() {
   }

   /**
    * Configure plugin. 
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.info = pluginInfo;
      this.glob = glob;
      Properties pluginProps = pluginInfo.getParameters();
      this.logNotifierDevice = new LogNotifierDevice(this);
   }
   
   /** return "notification" */
   public String getType() {
      return this.info.getType();
   }
   
   /** @return return "1.0" */
   public String getVersion() {
      return this.info.getVersion();
   }
   
   public void shutdown() {
   }

   /**
      * Register a listener (currently max one)
      * @param level Which levels you are interested in. Currently ignored, only ERROR are forwarded.
      * @param logNotification The interface to send the logging
      */
   public void register(int level, LogableDevice logNotification) {
      this.logNotifierDevice.register(level, logNotification);
   }

   public void unregister(int level, LogableDevice logNotification) {
      this.logNotifierDevice.register(level, logNotification);
   }

   public  LogableDevice getLogDevice(LogChannel channel) {
      return this.logNotifierDevice;
   }


   /**
    * Allow to register for log events. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
    */
   class LogNotifierDevice implements LogableDevice {
      
      private LogNotifierDeviceFactory factory;
      private Set errorListenerSet = new HashSet();
      private LogableDevice[] errorCache;
      private Set warnListenerSet = new HashSet();
      private LogableDevice[] warnCache;
      
      /**
       * Constructor. 
       */
      LogNotifierDevice(LogNotifierDeviceFactory factory) {
         this.factory = factory;
      }

      /**
       * Register a listener (currently max one). 
       * This listener may NOT use logging himself to avoid recursion
       * @param level Which levels you are interested in. Pass LogConstants.LOG_ERROR|LogConstants.LOG_WARN to register for all available levels.
       * @param logNotification The interface to send the logging
       */
      public synchronized void register(int level, LogableDevice logNotification) {
         if ((LogChannel.LOG_WARN & level) != 0) {
            this.warnListenerSet.add(logNotification);
            this.warnCache = null;
         }
         if ((LogChannel.LOG_ERROR & level) != 0) {
            this.errorListenerSet.add(logNotification);
            this.errorCache = null;
         }
      }

      /**
       * Remove the listener. 
       * @param level Which levels you want to remove. Pass LogConstants.LOG_ERROR|LogConstants.LOG_WARN to unregister for all available levels.
       */
      public synchronized void unregister(int level, LogableDevice logNotification) {
         if ((LogChannel.LOG_WARN & level) != 0) {
            this.warnListenerSet.remove(logNotification);
            this.warnCache = null;
         }
         if ((LogChannel.LOG_ERROR & level) != 0) {
            this.errorListenerSet.remove(logNotification);
            this.errorCache = null;
         }
      }

      /**
       * Get a snapshot of warn listeners. 
       */
      public LogableDevice[] getWarnListeners() {
         if (this.warnCache == null) {
            synchronized (this) {
               if (this.warnCache == null) {
                  this.warnCache = (LogableDevice[])this.warnListenerSet.toArray(new LogableDevice[this.warnListenerSet.size()]);
               }
            }
         }
         return this.warnCache;
      }

      /**
       * Get a snapshot of error listeners. 
       */
      public LogableDevice[] getErrorListeners() {
         if (this.errorCache == null) {
            synchronized (this) {
               if (this.errorCache == null) {
                  this.errorCache = (LogableDevice[])this.errorListenerSet.toArray(new LogableDevice[this.errorListenerSet.size()]);
               }
            }
         }
         return this.errorCache;
      }

      /**
       * Redirect logging. 
       */
      public void log(int level, String source, String str) {
         if (LogChannel.LOG_WARN == level) {
            LogableDevice[] arr = getWarnListeners();
            for (int i=0; i<arr.length; i++) {
               arr[i].log(level, source, str);
            }
         }
         else if (LogChannel.LOG_ERROR == level) {
            LogableDevice[] arr = getErrorListeners();
            for (int i=0; i<arr.length; i++) {
               arr[i].log(level, source, str);
            }
         }
      }
   }
}
