/*------------------------------------------------------------------------------
Name:      PropertyHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Implementation for administrative property access
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.intern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.I_Plugin;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.admin.I_CommandHandler;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;


/**
 * Implementation of administrative access to properties. 
 * @author ruff@swand.lake.de 
 * @since 0.79f
 */
final public class PropertyHandler implements I_CommandHandler, I_Plugin {

   private String ME = "PropertyHandler";
   private Global glob = null;
   private LogChannel log = null;
   private CommandManager commandManager = null;

   /**
    * This is called after creation of the plugin. 
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My big brother taking care of me
    */
   public void initialize(Global glob, CommandManager commandManager) {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.commandManager = commandManager;
      this.ME = this.ME + "-" + glob.getId();
      this.commandManager.register("sysprop", this);
      log.info(ME, "Property administation plugin is initialized");
   }

   /**
    * This method is called by the PluginManager.
    * <p />
    * This xmlBlaster.properties entry example
    * <pre>
    *   LoadBalancerPlugin[PropertyHandler][1.0]=org.xmlBlaster.engine.command.simpledomain.PropertyHandler,DEFAULT_MAX_LEN=200
    * </pre>
    * passes 
    * <pre>
    *   options[0]="DEFAULT_MAX_LEN"
    *   options[1]="200"
    * </pre>
    * <p/>
    * @param Global   An xmlBlaster instance global object holding logging and property informations
    * @param String[] Some arguments from xmlBlaster.properties.
    */
   public void init(org.xmlBlaster.util.Global glob, String[] options) throws XmlBlasterException {
      if (options != null) {
         for (int ii=0; ii<options.length-1; ii++) {
            if (options[ii].equalsIgnoreCase("DUMMY")) {
               //DUMMY = (new Long(options[++ii])).longValue();  // ...
            }
         }
      }
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
    * Your plugin should process the command. 
    * <p />
    * @param cmd "?user.home"
    * @return "key=value" or null if not found, e.g. "/node/heron/sysprop/?user.home=/home/joe"
    * @see org.xmlBlaster.engine.admin.I_CommandHandler#getCommand(String get)
    */
   public synchronized String get(CommandWrapper cmd) throws XmlBlasterException {
      if (cmd == null)
         throw new XmlBlasterException(ME, "Please pass a command which is not null");
      if (cmd.getTail() == null)
         throw new XmlBlasterException(ME, "Please pass a command which has a valid property added, '" + cmd + "' is too short, aborted request.");

      String cmdString = cmd.getTail().trim();
      if (cmdString.startsWith("?"))
         cmdString = cmdString.substring(1);

      String ret = glob.getProperty().get(cmdString, (String)null);
      log.info(ME, "Found for cmd " + cmdString + "=" + ret);
      if (ret == null)
         return null;
      else
         return cmd.getCommand() + "=" + ret;
   }


   public String help() {
      return "Administration of properties from system, xmlBlaster.properties and command line";
   }

   public String help(String cmd) {
      return help();
   }

   public void shutdown() {
   }
}
