/*------------------------------------------------------------------------------
Name:      I_RunlevelListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Listens on run level changes
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine;


/**
 * Listens on run level change events, for example shutdown is runlevel 0, up and running is runlevel 10. 
 * <p>
 * @see org.xmlBlaster.engine.Constants
 * @author Marcel Ruff
 * @since 0.79f
 */
public interface I_RunlevelListener extends java.util.EventListener {
   /**
    * A human readable name of the listener for logging. 
    */
   public String getName();

   /**
    * Invoked on run level change, see Constants.RUNLEVEL_HALTED and Constants.RUNLEVEL_RUNNING
    * @param from The current runlevel
    * @param to The runlevel we want to switch to
    * @param force If true force the change even if messages are lost
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException;
}
