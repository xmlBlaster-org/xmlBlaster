/*------------------------------------------------------------------------------
Name:      I_ExternGateway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to hide extern gateway implementations
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.admin;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.MessageUnit;

/**
 * Interface to allow different external gateway implementations. 
 * <p />
 * @author ruff@swand.lake.de
 * @since 0.79f
 */
public interface I_ExternGateway {
   /**
    * This is called after creation of the plugin. 
    * <p />
    * You should register yourself with commandManager.register() during initialization.
    *
    * @param glob The Global handle of this xmlBlaster server instance.
    * @param commandManager My manager
    * @return false Ignore this implementation (gateway is switched off)
    */
   public boolean initialize(Global glob, CommandManager commandManager) throws XmlBlasterException;

   /** Get a human readable name of this filter implementation */
   public String getName();

   public void shutdown();
}
