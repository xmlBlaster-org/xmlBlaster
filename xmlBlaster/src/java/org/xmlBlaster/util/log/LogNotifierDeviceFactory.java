/*------------------------------------------------------------------------------
Name:      LogNotifierDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;

import java.util.HashSet;
import java.util.Set;

/**
 * Factory to register to get a notification if a log.error or log.warn occurs. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
 */
public class LogNotifierDeviceFactory implements I_LogDeviceFactory {
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
      //Properties pluginProps = pluginInfo.getParameters();
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
      * Register a listener. 
      * @param level Which levels you are interested in. Currently ignored, only ERROR are forwarded.
      * @param logNotification The interface to send the logging
      */
   public void register(int level, LogableDevice logNotification) {
      this.logNotifierDevice.register(level, logNotification);
   }

   public void unregister(int level, LogableDevice logNotification) {
      this.logNotifierDevice.unregister(level, logNotification);
   }

   public  LogableDevice getLogDevice(LogChannel channel) {
      return this.logNotifierDevice;
   }


   /**
    * Allow to register for log events. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
    */
   class LogNotifierDevice implements LogableDevice {
      
      //private LogNotifierDeviceFactory factory;
      private Set errorListenerSet = new HashSet();
      private LogableDevice[] errorCache;
      private Set warnListenerSet = new HashSet();
      private LogableDevice[] warnCache;
      
      /**
       * Constructor. 
       */
      LogNotifierDevice(LogNotifierDeviceFactory factory) {
         //this.factory = factory;
      }

      /**
       * Register a listener. 
       * This listener may NOT use logging himself to avoid recursion
       * If this set already contains the specified element, the call leaves this set unchanged and returns false
       * @param level Which levels you are interested in. Pass LogConstants.LOG_ERROR|LogConstants.LOG_WARN to register for all available levels.
       * @param logNotification The interface to send the logging
       * @return true if the given logNotification is added
       */
      public synchronized boolean register(int level, LogableDevice logNotification) {
         boolean ret = false;
         if ((LogChannel.LOG_WARN & level) != 0) {
            ret = this.warnListenerSet.add(logNotification);
            this.warnCache = null;
         }
         if ((LogChannel.LOG_ERROR & level) != 0) {
            ret = this.errorListenerSet.add(logNotification);
            this.errorCache = null;
         }
         return ret;
      }

      /**
       * Remove the listener. 
       * @param level Which levels you want to remove. Pass LogConstants.LOG_ERROR|LogConstants.LOG_WARN to unregister for all available levels.
       * @return true if the set contained the specified element.
       */
      public synchronized boolean unregister(int level, LogableDevice logNotification) {
         boolean ret = false;
         if ((LogChannel.LOG_WARN & level) != 0) {
            ret = this.warnListenerSet.remove(logNotification);
            this.warnCache = null;
         }
         if ((LogChannel.LOG_ERROR & level) != 0) {
            ret = this.errorListenerSet.remove(logNotification);
            this.errorCache = null;
         }
         return ret;
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
