/*------------------------------------------------------------------------------
Name:      MomClientGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Access with messages to administration tasks
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin.extern;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.admin.CommandManager;
import org.xmlBlaster.engine.admin.CommandWrapper;
import org.xmlBlaster.engine.admin.I_ExternGateway;
import org.xmlBlaster.engine.admin.SetReturn;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.StringTokenizer;

/**
 * The gateway allows to do administration with xmlBlaster messages. 
 * <p />
 * XmlBlaster messages having an oid that starts with "__cmd:" are
 * handled as administration messages.<br />
 * Example:<br />
 * <pre>
 *   &lt;key oid="__cmd:/node/heron/sysprop/?trace[core]"/>
 * </pre>
 * @author ruff@swand.lake.de
 * @since 0.79f
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.html">admin requirement</a>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.command.html">admin.command requirement</a>
 */
public final class MomClientGateway implements I_ExternGateway
{
   private String ME;
   private Global glob;
   private LogChannel log;
   private CommandManager commandManager;
   private static int instanceCounter = 0;

   /**
    * Creates the remote console server. 
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog("admin");
      this.instanceCounter++;
      this.ME = "MomClientGateway-" + this.glob.getId() + "-" + this.instanceCounter;
      this.commandManager = commandManager;
      return true;
   }

   /**
    * Called by RequestBroker on arrival of command messages. 
    * @param sessionInfo The client
    * @param command The key oid, for example "__cmd:/node/heron/?numClients"
    */
   public MessageUnit[] getCommand(SessionInfo sessionInfo, String command) throws XmlBlasterException {
      String cmdType = "get";
      //try {
         if (command == null) {
            throw new XmlBlasterException(ME, "Ignoring your empty command.");
         }
         command = command.trim();
         if (!command.startsWith("__cmd:") || command.length() < ("__cmd:".length() + 1)) {
            throw new XmlBlasterException(ME, "Ignoring your empty command '" + command + "'.");
         }

         int dotIndex = command.indexOf(":");
         String query = command.substring(dotIndex+1).trim();  // "/node/heron/?numClients"

         /*
         CommandWrapper cw = new CommandWrapper(glob, query);

         String topCommand = cw.getThirdLevel();
         log.info(ME, "Processing command " + cw.toXml());

         if (topCommand == null)
            throw new XmlBlasterException(ME, "Ignoring your command '" + command + "', can't understand it.");

         if (topCommand.equalsIgnoreCase("time")) {
            MessageUnit[] msgs = new MessageUnit[1];
            msgs[0] = new MessageUnit("<key oid='" + command + "'", ""+new java.util.Date(), "<qos/>");
            return msgs;
         }
         */

         return commandManager.get(sessionInfo.getSessionId(), query);

/*
         if (command.trim().toUpperCase().startsWith("MEM")) {
            Runtime rt = Runtime.getRuntime();
            return ""+rt.totalMemory()+"/"+rt.freeMemory();
         }

         StringTokenizer st = new StringTokenizer(command, " ");
         if (!st.hasMoreTokens()) {
            return getErrorText("Ignoring your empty command.");
         }

         if (!st.hasMoreTokens()) {
            if (cmdType.trim().equalsIgnoreCase("GET") ||
                cmdType.trim().equalsIgnoreCase("SET") ||
                cmdType.trim().equalsIgnoreCase("CONNECT")) {
               return getErrorText("Ignoring your empty command '" + command + "'");
            }
            if (cmdType.trim().equalsIgnoreCase("echo")) {
               return null;
            }
         }

         // Commands with login only:

         if (command.trim().equalsIgnoreCase("gc")) {
            System.gc();
            return "OK";
         }

         if (command.trim().toUpperCase().startsWith("EXIT")) {
            return "EXIT is not supported";
         }

         if (log.TRACE) log.trace(ME, "Invoking cmdType=" + cmdType + " command=" + command + " from '" + command + "'");

         if (cmdType.trim().equalsIgnoreCase("GET")) {
            MessageUnit[] msgs = commandManager.get(sessionInfo.getSessionId(), command);
            if (msgs.length == 0) return "NO ENTRY FOUND: " + command;
            StringBuffer sb = new StringBuffer(msgs.length * 40);
            for (int ii=0; ii<msgs.length; ii++) {
               MessageUnit msg = msgs[ii];
               if (msg.getQos().startsWith("text/plain"))
                  sb.append(msg.getXmlKey()).append("=").append(msg.getContentStr());
               else
                  sb.append(msg.toXml());
            }
            return sb.toString();
         }
         else if (cmdType.trim().equalsIgnoreCase("SET")) {
            SetReturn ret = commandManager.set(sessionInfo.getSessionId(), command);
            if (ret == null) return "NO ENTRY SET: " + ret.commandWrapper.getCommand();
            return ret.commandWrapper.getCommandStripAssign() + "=" + ret.returnString;
         }
         else {
            return null;
            //return getErrorText("Ignoring unknown command '" + cmdType + "' of '" + command + "'");
         }
      }
      catch (XmlBlasterException e) {
         if (log.TRACE) log.trace(ME+".momClient", e.toString());
         return e.toString();
      }
      */
   }

   private final String getErrorText(String error) {
      String text = "ERROR-XmlBlaster momClient server: " + error;
      text += " Try a oid='__cmd:...'";
      log.info(ME, error);
      return text;
   }

   public String getName() {
      return "MomClientGateway";
   }

   public void shutdown() {
      if (log.TRACE) log.trace(ME, "Shutdown.");
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

      sb.append(offset).append("<momClientGateway>");
      sb.append(offset).append("</momClientGateway>");

      return sb.toString();
   }
}

