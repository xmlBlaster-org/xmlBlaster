/*------------------------------------------------------------------------------
Name:      CommandManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for administrative commands
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.command;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.authentication.SessionInfo;

/**
 * The manager instance for administrative commands. 
 * <p />
 * Each xmlBlaster server instance has one instance
 * of this class to manage its administrative behavior.
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
 * for a detailed description.
 * @author ruff@swand.lake.de
 * @since 0.79e
 */
public final class CommandManager
{
   private String ME;

   // The following 3 declarations are 'final' but the SUN JDK 1.3.1 does not like it
   private final Global glob;
   private final LogChannel log;
   private final SessionInfo sessionInfo;

   /**
    * You need to call postInit() after all drivers are loaded.
    *
    * @param sessionInfo Internal handle to be used directly with RequestBroker
    *                    NOTE: We (the command code) are responsible for security checks
    *                    as we directly write into RequestBroker.
    */
   public CommandManager(Global glob, SessionInfo sessionInfo) {
      this.glob = glob;
      this.log = this.glob.getLog("cmd");
      this.ME = "CommandManager-" + this.glob.getId();
      this.sessionInfo = sessionInfo;
   }

   /**
    */
   public String get(String cmd) {
      if (log.CALL) log.call(ME, "get(" + cmd + ")");
      return "EMPTY";
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
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<commandManager>");
      /*
      if (commandNodeMap != null && commandNodeMap.size() > 0) {
         Iterator it = commandNodeMap.values().iterator();
         while (it.hasNext()) {
            CommandNode info = (CommandNode)it.next();
            sb.append(info.toXml(extraOffset + "   "));
         }
      }
      */
      sb.append(offset).append("</commandManager>");

      return sb.toString();
   }
}

