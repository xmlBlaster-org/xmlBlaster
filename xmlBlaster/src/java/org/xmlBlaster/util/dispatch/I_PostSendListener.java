/*------------------------------------------------------------------------------
Name:      I_PostSendListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * Notify when a message is successfully send. 
 * Does not notify for oneway messages (PUBLISH_ONEWAY, UPDATE_ONEWAY) 
 * @author Marcel Ruff
 */
public interface I_PostSendListener {
        
   /**
    * Called after a messages is send, but not for oneway messages. 
    * @param msgQueueEntry, includes the returned QoS
    */
   public void postSend(MsgQueueEntry msgQueueEntry);
}
