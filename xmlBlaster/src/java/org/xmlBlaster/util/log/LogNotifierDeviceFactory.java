/*------------------------------------------------------------------------------
Name:      LogNotifierDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Logging into JDK 1.4 java.util.logging
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

/**
 * Redirect xmlBlaster logging to JDK 1.4 java.util.logging framework. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html#JDK14">The util.log.plugin requirement</a>
 */
public class LogNotifierDeviceFactory implements I_LogDeviceFactory {
   private static final String ME = "LogNotifierDeviceFactory";
   private org.xmlBlaster.util.Global glob;
   /** This is the log used in this class. Errors logged to this log channel will not be sent per email to avoid recursivity. */
   private LogChannel log;
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
      private LogableDevice logNotification; // Just change to map for multiple registrants on demand
      
      /**
       * Constructor. 
       */
      LogNotifierDevice(LogNotifierDeviceFactory factory) {
         this.factory = factory;
      }

      /**
       * Register a listener (currently max one). 
       * This listener may NOT use logging himself to avoid recursion
       * @param level Which levels you are interested in. Currently ignored, only ERROR are forwarded.
       * @param logNotification The interface to send the logging
       */
      public void register(int level, LogableDevice logNotification) {
         this.logNotification = logNotification;
      }

      public void unregister(int level, LogableDevice logNotification) {
         this.logNotification = null;
      }

      /**
       * Redirect logging. 
       */
      public void log(int level, String source, String str) {
         if (this.logNotification != null) {
            if (LogChannel.LOG_WARN == level) {
               this.logNotification.log(level, source, str);
            }
            else if (LogChannel.LOG_ERROR == level) {
               this.logNotification.log(level, source, str);
            }
         }
      }
   }
}
