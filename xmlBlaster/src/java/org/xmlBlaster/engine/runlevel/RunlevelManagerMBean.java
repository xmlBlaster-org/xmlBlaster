/*------------------------------------------------------------------------------
Name:      RunlevelManagerMBean.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.runlevel;
import org.xmlBlaster.util.admin.I_AdminUsage;

/**
 * Export runlevel manager methods for JMX on cluster node level.
 *
 */
public interface RunlevelManagerMBean extends I_AdminUsage {
   /**
    * Access the current run level of xmlBlaster. 
    * @return 0 is halted and 9 is fully operational
    */
   public int getCurrentRunlevel();

   /**
    * Change the run level of xmlBlaster. 
    * @param 0 is halted and 9 is fully operational
    */
   public String setRunlevel(String level) throws Exception;

}

