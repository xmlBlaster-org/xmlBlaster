/*------------------------------------------------------------------------------
Name:      EmailOnErrorLogDeviceFactory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Logging into JDK 1.4 java.util.logging
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.log;

import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.engine.Global;
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
public class EmailOnErrorLogDeviceFactory implements I_LogDeviceFactory {
   private static final String ME = "EmailOnErrorLogDeviceFactory";
   private org.xmlBlaster.util.Global glob;
   private Global subGlobal;
   /** This is the log used in this class. Errors logged to this log channel will not be sent per email to avoid recursivity. */
   private LogChannel log;
   private String logDevice = "console";
   private String smtpHost;
   private String from;
   private String to;
   private String subject = "xmlBlaster error";
   private long sleepDelay = 0L;
   private long maxQueueSize = 100L;
   private PluginInfo info;
   
   private I_CallbackDriver cbDriver;
   
   /**
    * Constructor. 
    */
   public EmailOnErrorLogDeviceFactory() {
      
   }

   public synchronized void send(int level, String source, String str) throws XmlBlasterException {
      String key ="<key />";
      String qos ="<oqs/>";
      String content = source + " : " + str;
      MsgUnitRaw raw = new MsgUnitRaw("", content.getBytes(), qos);
      this.cbDriver.sendUpdate(new MsgUnitRaw[] {raw} );
   }
   
   /** Load the appropriate protocol driver */
   private final synchronized void loadPlugin() throws XmlBlasterException {
      String protocolType = "EMAIL";
      if (this.cbDriver == null) { // instantiate the callback plugin ...
         this.cbDriver = this.subGlobal.getCbProtocolManager().getNewCbProtocolDriverInstance(protocolType);
         if (this.cbDriver == null)
            throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Sorry, callback protocol type='" + protocolType + "' is not supported");
         CallbackAddress cbAddress = new CallbackAddress(this.subGlobal, protocolType);
         cbAddress.setRawAddress(to);
         this.cbDriver.init(this.subGlobal, cbAddress);
         if (this.log.TRACE)
            this.log.trace(ME, "Created callback plugin '" + protocolType + "'");
      }
      else {
         if (this.log.TRACE) 
            this.log.trace(ME, "Created native callback driver for protocol '" + protocolType + "'");
      }
   }

   
   
   private String setMandatoryString(String varName, String var, Properties prop) throws XmlBlasterException {
      var = prop.getProperty(varName, null);
      if (var == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, ME + " constructor", " the '" + varName + "' property must be set");
      return var;
   }
   
   /**
    * Configure plugin. 
    */
   public void init(org.xmlBlaster.util.Global glob, PluginInfo pluginInfo) throws XmlBlasterException {
      this.info = pluginInfo;
      this.glob = glob;
      Properties pluginProps = pluginInfo.getParameters();
      this.logDevice = pluginProps.getProperty("logDevice", "console");
      this.smtpHost = setMandatoryString("smtpHost", this.smtpHost, pluginProps);
      this.from = setMandatoryString("from", this.from, pluginProps);
      this.to = setMandatoryString("to", this.to, pluginProps);
      if (this.to == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, ME + " constructor", " the 'to' property must be set");
      this.subject = pluginProps.getProperty("subject", "xmlBlaster error");
      String tmp = pluginProps.getProperty("sleepDelay", "0");
      try {
         this.sleepDelay = Long.parseLong(tmp);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, ME + " constructor", " the 'sleepDelay' property is set to '" + tmp + "' but it must be set to a long");
      }
      tmp = pluginProps.getProperty("maxQueueSize", "100");
      try {
         this.maxQueueSize = Long.parseLong(tmp);
      }
      catch (Throwable ex) {
         throw new XmlBlasterException(this.glob, ErrorCode.USER_CONFIGURATION, ME + " constructor", " the 'maxQueueSize' property is set to '" + tmp + "' but it must be set to a long");
      }
      
      // this.subGlobal = this.glob.getClone(args); this causes recursivity
      Properties props = (Properties)this.glob.getProperty().getProperties().clone();
      props.setProperty("logDevice", this.logDevice);

      props.setProperty("EmailDriver.smtpHost", this.smtpHost);
      props.setProperty("EmailDriver.from", this.from);
      props.setProperty("EmailDriver.subject", this.subject);
      
      this.subGlobal = new Global(props, false);
      this.log = this.subGlobal.getLog("logEmailOnError");
      this.log.info(ME, "init: the logger has been successfully initialized");
      if (false) {
         this.log.info(ME, "init: property 'logDevice'   : '" + this.logDevice + "'");
         this.log.info(ME, "init: property 'smtpHost'    : '" + this.smtpHost + "'");
         this.log.info(ME, "init: property 'from'        : '" + this.from + "'");
         this.log.info(ME, "init: property 'to'          : '" + this.to + "'");
         this.log.info(ME, "init: property 'subject'     : '" + this.subject + "'");
         this.log.info(ME, "init: property 'sleepDelay'  : '" + this.sleepDelay + "' ms");
         this.log.info(ME, "init: property 'maxQueueSize': '" + this.maxQueueSize + "' entries");
      }
      loadPlugin();
   }
   
   public String getType() {
      //return "emailOnError";
      return this.info.getType();
   }
   
   public String getVersion() {
      //return "1.0";
      return this.info.getVersion();
   }
   
   public void shutdown() {
   }

   public  LogableDevice getLogDevice(LogChannel channel) {
      String channelKey = channel.getChannelKey();
      return new LogEmailOnErrorDevice(this, this.subGlobal.getLog(channelKey));
   }


   /**
    * Redirect xmlBlaster logging to JDK 1.4 java.util.logging framework. 
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/util.log.plugin.html">The util.log.plugin requirement</a>
    */
   class LogEmailOnErrorDevice implements LogableDevice {
      
      private LogChannel log;
      private EmailOnErrorLogDeviceFactory factory;
      
      /**
       * Constructor. 
       */
      LogEmailOnErrorDevice(EmailOnErrorLogDeviceFactory factory, LogChannel log) {
         this.log = log;
         this.factory = factory;
      }

      /**
       * Redirect logging. 
       */
      public void log(int level, String source, String str) {
         if (LogChannel.LOG_ERROR == level) {
            try {
               this.factory.send(level, source, str);
            }
            catch (XmlBlasterException ex) {
               ex.printStackTrace();
            }
         }
         this.log.log(level, source, str);
      }
   }
}
