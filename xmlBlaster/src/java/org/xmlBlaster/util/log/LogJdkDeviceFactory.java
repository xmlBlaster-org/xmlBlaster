/*------------------------------------------------------------------------------
Name:      LogJdkDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Logging into JDK 1.4 java.util.logging
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.log;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.log.I_LogDeviceFactory;
import org.xmlBlaster.util.plugin.PluginInfo;
import org.jutils.log.LogableDevice;
import org.jutils.log.LogChannel;

import java.util.logging.*;

/**
 * Redirect xmlBlaster logging to JDK 1.4 java.util.logging framework. 
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html#JDK14">The util.log.plugin requirement</a>
 */
public class LogJdkDeviceFactory implements I_LogDeviceFactory {
   private static final String LOG_DOMAIN="org.xmlBlaster";
   private Global glob;

   /**
    * Constructor. 
    */
   public LogJdkDeviceFactory (){
      
   }

   /**
    * Configure plugin. 
    */
   public void init(Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.glob = glob;
      
   }
   
   public String getType() {return "jdk14logging";}
   public String getVersion() {return "1.0";}
   public void shutdown() {}

   public  LogableDevice getLogDevice(LogChannel channel) {
      String key = channel.getChannelKey();
      String domain = LOG_DOMAIN;
      if ( key != null) {
         domain = domain + "."+key;
         
      } // end of if ()
      Logger l = Logger.getLogger(domain);
      return new LogJdkDevice(l);

   }


   /**
    * Redirect xmlBlaster logging to JDK 1.4 java.util.logging framework. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
    */
   class LogJdkDevice implements LogableDevice {
      Logger log = null;

      /**
       * Constructor. 
       */
      LogJdkDevice(Logger log) {
         if ( log == null) {
            throw new NullPointerException("Logger is not allowed to be null");  
         } // end of if ()
         
         this.log = log;
      }

      /**
       * Redirect logging. 
       */
      public void log(int level, String source, String str) {
         switch (level) {
         case LogChannel.LOG_CALL:
            if (log.isLoggable(Level.FINER))
               log.logrb(Level.FINER, source, "", null, str);
            break;
         case LogChannel.LOG_DUMP:
            if (log.isLoggable(Level.FINEST))
               log.logrb(Level.FINEST, source, "", null, str);
            break;
         case LogChannel.LOG_TIME:
         case LogChannel.LOG_TRACE:
            if (log.isLoggable(Level.FINE))
               log.logrb(Level.FINE, source, "", null, str);
            break;
         case LogChannel.LOG_ERROR:
            log.logrb(Level.SEVERE, source, "", null, str);
            break;
         case LogChannel.LOG_INFO:
            log.logrb(Level.INFO, source, "", null, str);
            break;
         case LogChannel.LOG_WARN:
            log.logrb(Level.WARNING, source, "", null, str);
            break;
         default:
            log.logrb(Level.INFO, source, "", null, str);
            break;
         }; // end of switch
      } // end of log
   }
}// LogJdkDeviceFactory
