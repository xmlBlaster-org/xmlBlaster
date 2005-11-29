/**
 * 
 */
package org.xmlBlaster.util.protocol;

import org.xmlBlaster.util.admin.I_AdminPlugin;

/**
 * JMX manage Request/Reply pattern. 
 * @author xmlblast@marcelruff.info
 */
public interface RequestReplyExecutorMBean extends I_AdminPlugin {
   /**
    * Interrupts a blocking request with a not returning reply. 
    * The pending message is handled as not delivered and will be queued
    */
   public int interruptInvocation();

}
