/*------------------------------------------------------------------------------
Name:      I_CommandHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to hide command handling code
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.cluster.ClusterManager;

import java.util.Set;

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
    * @return Always string, binary data must be encoded (for XML base64), the key=value of the property
    *         or null if not found
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/admin.commands.html">command requirement</a>
    */
   public String get(CommandWrapper cmd) throws XmlBlasterException;

   public String help();

   public String help(String cmd);

   public void shutdown();
}
