/*------------------------------------------------------------------------------
Name:      TelnetGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.authentication.SessionInfo;

import remotecons.RemoteServer;
import remotecons.ifc.CommandHandlerIfc;

import java.util.LinkedList;
import java.util.StringTokenizer;
import java.io.IOException;

/**
 * The gateway from outside telnet connections to inside CommandManager. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.telnet.html">admin.telnet requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.command.html">admin.command requirement</a>
 */
public final class TelnetGateway implements CommandHandlerIfc, I_ExternGateway
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private CommandManager manager;
   private int port;
   private RemoteServer rs = null;
   private final String CRLF = "\r\n";

   /**
    * Creates the remote console server. 
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.ME = "TelnetGateway-" + this.glob.getId();
      this.manager = commandManager;
      port = glob.getProperty().get("admin.remoteconsole.port", 0); // 2702;
      port = glob.getProperty().get("admin.remoteconsole.port[" + glob.getId() + "]", port);
      if (port > 1000) {
         createRemoteConsole(port);
         log.info(ME, "Started remote console server for administration, try 'telnet " + glob.getLocalIP() + " " + port + "' to access it and type 'help'.");
         return true;
      }
      if (log.TRACE) log.trace(ME, "No telnet gateway configured, port=" + port + " try '-admin.remoteconsole.port 2702' if you want one");
      return false;
   }

   /**
    * Creates a server which is accessible with telnet. 
    * This allows you to access xmlBlaster and query for example the free memory:
    * <pre>
    *  telnet 192.168.1.2 2702
    *  mem
    * </pre>
    * Enter 'help' for all available commands.
    */
   private void createRemoteConsole(int port) throws XmlBlasterException {
      if (port > 1000) {
         rs = new RemoteServer();
         rs.setServer_port(port);
         rs.setAs_daemon(true);
         LinkedList ll = new LinkedList();
         ll.add(this);
         try {
           rs.initialize(ll);
         } catch (IOException e) {
           e.printStackTrace();
           log.error(ME, "Initializing of remote console failed:" + e.toString());
           throw new XmlBlasterException(ME, "Initializing of remote telnet console failed:" + e.toString());
         }
      }
   }

   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String handleCommand(String cmd) {
      try {
         if (cmd == null || cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }
         cmd = cmd.trim();
         if (cmd.length() < 1) {
            return getErrorText("Ignoring your empty command.");
         }

         StringTokenizer st = new StringTokenizer(cmd, " ");
         if (!st.hasMoreTokens()) {
            return getErrorText("Ignoring your empty command.");
         }
         String cmdType = (String)st.nextToken();

         if (!st.hasMoreTokens()) {
            if (cmdType.trim().equalsIgnoreCase("HELP")) {
               return help();
            }
            else if (cmdType.trim().equalsIgnoreCase("GET")) {
               return getErrorText("Ignoring your empty command '" + cmd + "'");
            }
            else
               return null;
         }

         String query = cmd.substring(cmdType.length()).trim();

         if (log.TRACE) log.trace(ME, "Invoking cmdType=" + cmdType + " query=" + query + " from '" + cmd + "'");

         if (cmdType.trim().equalsIgnoreCase("GET")) {
            MessageUnit[] msgs = manager.get(query);
            if (msgs.length == 0) return "NO ENTRY FOUND: " + cmd + CRLF;
            StringBuffer sb = new StringBuffer(msgs.length * 40);
            for (int ii=0; ii<msgs.length; ii++) {
               MessageUnit msg = msgs[ii];
               if (msg.getQos().startsWith("text/plain"))
                  sb.append(msg.getXmlKey()).append("=").append(msg.getContentStr()).append(CRLF);
            }
            return sb.toString() + CRLF;
         }
         else if (cmdType.trim().equalsIgnoreCase("SET")) {
            String ret = manager.set(query);
            CommandWrapper w = new CommandWrapper(glob, cmd); // To have the nicer query (not performing, should be passed from CommandManager back as well?)
            if (ret == null) return "NO ENTRY SET: " + cmd + CRLF;
            return w.getCommandStripAssign() + "=" + ret + CRLF;
         }
         else if (cmdType.trim().equalsIgnoreCase("HELP")) {
            return help(query);
         }
         else {
            return null;
            //return getErrorText("Ignoring unknown command '" + cmdType + "' of '" + cmd + "'" + CRLF);
         }
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME+".telnet", e.toString());
         return CRLF + e.toString() + CRLF + CRLF;
      }
   }

   private final String getErrorText(String error) {
      String text = "ERROR-XmlBlaster telnet server: " + error + CRLF + "Try a 'get sysprop/?user.home' or 'set sysprop/?trace[core]=true' or just 'help'" + CRLF + CRLF + "CMD> ";
      log.info(ME, error);
      return text;
   }

   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String help() {
      return CRLF + "XmlBlaster telnet administration, see http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.telnet.html" + CRLF;
   }
   /**
    * Enforced by "remotecons.CommandHandlerIfc"
    */
   public String help(String cmd) {
      return help();
   }

   public CommandHandlerIfc getInstance() {
      if (log.TRACE) log.trace(ME, "getInstance() is returning myself");
      return this;
   }

   public String getName() {
      return "TelnetGateway";
   }

   public void shutdown() {
      if (rs != null) {
         rs.disable();
         rs = null;
         if (log.TRACE) log.trace(ME, "Shutdown done, telnet disabled.");
      }
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

      sb.append(offset).append("<telnetGateway>");
      sb.append(offset).append("</telnetGateway>");

      return sb.toString();
   }
}

