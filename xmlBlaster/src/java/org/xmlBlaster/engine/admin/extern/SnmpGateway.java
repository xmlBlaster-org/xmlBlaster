/*------------------------------------------------------------------------------
Name:      SnmpGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.authentication.SessionInfo;

//!!!  import SNMP subagent specific classes here !!!

/**
 * The gateway from outside SNMP connections to inside CommandManager. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.SNMP.html">admin.snmp requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.command.html">admin.command requirement</a>
 */
public final class SnmpGateway implements I_ExternGateway // , SnmpInterface ?
{
   private String ME;

   private Global glob;
   private LogChannel log;
   private CommandManager manager;

   /**
    * This is called after creation of the plugin. 
    * <p />
    * You should register yourself with commandManager.register() during initialization.
    *
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My manager
    * @return false Ignore this implementation (gateway is switched off)
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "SnmpGateway-" + this.glob.getId();
      this.manager = commandManager;
      initSubagent();
      log.info(ME, "Started SNMP gateway for administration, try 'snmpget -v 1 -c public " + glob.getLocalIP() + " 1.3.6.1.4.1.11662.1.2.1.3' to access it.");
      return true;
   }

   /**
    * Creates a server which is accessible with SNMP. 
    * This allows you to access xmlBlaster and query for example the free memory:
    * <pre>
    *  SNMP 192.168.1.2 2702
    *  mem
    * </pre>
    * Enter 'help' for all available commands.
    */
   private void initSubagent() throws XmlBlasterException {
       // throw new XmlBlasterException(ME, "Initializing of SNMP subagent failed:" + e.toString());
   }

   /**
    * @param cmd The snmp command
    */
   public String getCommand(String cmd) {
      try {
         if (cmd == null || cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }
         cmd = cmd.trim();
         if (cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }

         // !!! Transfer MIB command to internal command !!!!
         // e.g. query="/node/heron/?freeMem"
         String query = cmd; // TODO mapping

         if (log.TRACE) log.trace(ME, "Invoking SNMP cmd=" + cmd + " as query=" + query);

         return manager.get(query);
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME+".SNMP", e.toString());
         return e.toString();
      }
   }

   private final String getErrorText(String error) {
      String text = "ERROR-XmlBlaster SNMP server: " + error + "Try a 'snmpget ...'.";
      log.info(ME, error);
      return text;
   }

   public String help() {
      return "XmlBlaster SNMP administration, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.snmp.html";
   }

   public String help(String cmd) {
      return help();
   }

   public String getName() {
      return "SnmpGateway";
   }

   public void shutdown() {
      /* Something like this!!!:
      if (agent != null) {
         agent.shutdown();
         agent = null;
      }
      */
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public synchronized final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<SnmpGateway>");
      sb.append(offset).append("</SnmpGateway>");

      return sb.toString();
   }
}

