/*------------------------------------------------------------------------------
Name:      PropertyHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative property access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.qos.AddressServer;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;


/**
 * Implementation of administrative access to properties. 
 * @author xmlBlaster@marcelruff.info 
 * @since 0.79f
 */
final public class PropertyHandler implements I_CommandHandler, I_Plugin {

   private String ME = "PropertyHandler";
   private ServerScope glob = null;
   private static Logger log = Logger.getLogger(PropertyHandler.class.getName());
   private CommandManager commandManager = null;


   public PropertyHandler() {}

   /**
    * This is called after creation of the plugin. 
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My big brother taking care of me
    */
   public void initialize(ServerScope glob, CommandManager commandManager) {
      this.glob = glob;

      this.commandManager = commandManager;
      this.ME = "PropertyHandler" + this.glob.getLogPrefixDashed();
      this.commandManager.register("sysprop", this);
      this.commandManager.register("logging", this);
      log.info("Property administration plugin is initialized");
   }

   /**
    * This method is called by the PluginManager (enforced by I_Plugin). 
    * @see org.xmlBlaster.util.plugin.I_Plugin#init(org.xmlBlaster.util.Global,org.xmlBlaster.util.plugin.PluginInfo)
    */
   public void init(org.xmlBlaster.util.Global glob, org.xmlBlaster.util.plugin.PluginInfo pluginInfo) {
   }

   /**
    * Return plugin type for Plugin loader
    * @return "PropertyHandler"
    */
   public String getType() {
      return "PropertyHandler";
   }

   /**
    * Return plugin version for Plugin loader
    * @return "1.0"
    */
   public String getVersion() {
      return "1.0";
   }

   /**
    * Get a human readable name of this filter implementation
    * @return "PropertyHandler"
    */
   public String getName() {
      return "PropertyHandler";
   }

   /**
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#get(String,CommandWrapper)
    */
   public synchronized MsgUnit[] get(AddressServer addressServer, String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which is not null");
      if (cmd.getTail() == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".get", "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is too short, aborted request.");

      String cmdString = cmd.getTail().trim();
      if (cmdString.startsWith("?"))
         cmdString = cmdString.substring(1);

      String ret = null;

      /*
      if (isLogLevelRequest(cmdString)) {
         // ret = ""+glob.getLogLevel(cmdString);
         if (log.isLoggable(Level.FINE)) 
            log.fine("Checking log level '" + cmdString + "', is " + ret);
      }
      else
      */
         ret = glob.getProperty().get(cmdString, (String)null);

      if (log.isLoggable(Level.FINE)) log.fine("Found for cmd " + cmdString + "=" + ret);
      if (ret == null)
         return new MsgUnit[0];
      else {
         MsgUnit[] msgs = new MsgUnit[1];
         msgs[0] = new MsgUnit("<key oid='" + cmd.getCommand() + "' />", ret.getBytes(), "text/plain");
         return msgs;
      }
   }

   /**
    * @return The new value set, it can be different to the passed value for example if ${} replacement occured
    */
   public String set(AddressServer addressServer, String sessionId, CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which is not null");
      if (cmd.getTail() == null)
         throw new XmlBlasterException(this.glob, ErrorCode.USER_ILLEGALARGUMENT, ME + ".set", "Please pass a command which has a valid property added, '" + cmd.getCommand() + "' is too short, aborted request.");

      /*
      String cmdString = cmd.getTail().trim();
      if (cmdString.startsWith("?"))
         cmdString = cmdString.substring(1);

      int equalsIndex = cmdString.lastIndexOf("=");
      if (equalsIndex < 1 || cmdString.length() <= (equalsIndex+1))
         throw new XmlBlasterException(ME, "Invalid command '" + cmd.getCommand() + "', don't know what to set with your request");

      String key = cmdString.substring(0,equalsIndex).trim();
      String value = cmdString.substring(equalsIndex+1);
      */
      String key = cmd.getKey();
      String[] values = cmd.getArgs();
      
      String type = cmd.getThirdLevel(); // "logging" or "sysprop"

      /*
      if (isLogLevelRequest(key)) {
         boolean bool = glob.changeLogLevel(key, values[0].trim());
         log.info("Changed log level '" + key + "' to " + bool);
         return ""+bool;
      }
      else {
      */
      if ("logging".equals(type)) {
         String value = (values != null && values.length > 0) ? values[0] : "INFO";
         log.info("Changed property '" + key + "' to " + value);
         Level level = Level.parse(value);
         this.glob.changeLogLevel(key, level);
         return value;
      }
      else {
         try {
            String ret = glob.getProperty().set(key, values[0]);
            log.info("Changed property '" + key + "' to " + ret);
            return ret;
         }
         catch (XmlBlasterException e) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".set", e.getErrorCodeStr() + " " + e.getMessage());
         }
      }
      // }
   }

   public String help() {
      return "Administration of properties from system, xmlBlaster.properties and command line";
   }

   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
      if (log.isLoggable(Level.FINE)) log.fine("Shutdown ignored, nothing to do");
   }

}
