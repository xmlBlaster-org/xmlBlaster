/*------------------------------------------------------------------------------
Name:      I_CommandHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to hide command handling code
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface to allow different command processing implementations. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public interface I_CommandHandler {
   /**
    * This is called after creation of the plugin. 
    * <p />
    * You should register yourself with commandManager.register() during initialization.
    *
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My manager
    */
   public void initialize(Global glob, CommandManager commandManager);

   /**
    * Your plugin should process the command. 
    * <p />
    * @param cmd The command to process
    * @return An array of MessageUnit object:
    *       <ul>
    *         <li>Internal message are delivered as is, please don't manipulate them</li>
    *         <li>System properties and internal state queries are marked with msgUnit.getQos() == "text/plain"
    *             and the key contains the plain key and the content the plain value. The MessageUnit
    *             is just misused to carry the key/value data.
    *         </li>
    *       </ul>
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
    */
   public MessageUnit[] get(CommandWrapper cmd) throws XmlBlasterException;

   /**
    * Your plugin should process the set command. 
    * <p />
    * @param cmd The command to process, e.g. ?trace=true
    * @return null if not set
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
    */
   public String set(CommandWrapper cmd) throws XmlBlasterException;

   public String help();

   public String help(String cmd);

   public void shutdown();
}
