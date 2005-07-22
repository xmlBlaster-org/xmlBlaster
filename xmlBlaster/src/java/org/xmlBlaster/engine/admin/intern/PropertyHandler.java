/*------------------------------------------------------------------------------
Name:      PropertyHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative property access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.jutils.JUtilsException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.plugin.I_Plugin;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
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
   private Global glob = null;
   private LogChannel log = null;
   private CommandManager commandManager = null;


   public PropertyHandler() {}

   /**
    * This is called after creation of the plugin. 
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My big brother taking care of me
    */
   public void initialize(Global glob, CommandManager commandManager) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.commandManager = commandManager;
      this.ME = "PropertyHandler" + this.glob.getLogPrefixDashed();
      this.commandManager.register("sysprop", this);
      log.info(ME, "Property administration plugin is initialized");
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

      if (isLogLevelRequest(cmdString)) {
         ret = ""+glob.getLogLevel(cmdString);
         if (log.TRACE) log.trace(ME, "Checking log level '" + cmdString + "', is " + ret);
      }
      else
         ret = glob.getProperty().get(cmdString, (String)null);

      if (log.TRACE) log.trace(ME, "Found for cmd " + cmdString + "=" + ret);
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
      String value = cmd.getValue();
         
      if (isLogLevelRequest(key)) {
         boolean bool = glob.changeLogLevel(key, value.trim());
         log.info(ME, "Changed log level '" + key + "' to " + bool);
         return ""+bool;
      }
      else {
         try {
            String ret = glob.getProperty().set(key, value);
            log.info(ME, "Changed property '" + key + "' to " + ret);
            return ret;
         }
         catch (JUtilsException e) {
            throw new XmlBlasterException(this.glob, ErrorCode.INTERNAL_ILLEGALARGUMENT, ME + ".set", e.id + " " + e.getMessage());
         }
      }
   }

   private boolean isLogLevelRequest(String cmdString) {
      if (cmdString == null) return false;
      cmdString = cmdString.toUpperCase();
      if (cmdString.startsWith("ERROR"))
         return true;
      else if (cmdString.startsWith("WARN"))
         return true;
      else if (cmdString.startsWith("INFO"))
         return true;
      else if (cmdString.startsWith("CALL"))
         return true;
      else if (cmdString.startsWith("TIME"))
         return true;
      else if (cmdString.startsWith("TRACE"))
         return true;
      else if (cmdString.startsWith("DUMP"))
         return true;
      else if (cmdString.startsWith("PLAIN"))
         return true;
      return false;
   }

   public String help() {
      return "Administration of properties from system, xmlBlaster.properties and command line";
   }

   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Shutdown ignored, nothing to do");
   }

}
