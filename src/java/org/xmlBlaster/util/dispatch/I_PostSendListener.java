/*------------------------------------------------------------------------------
Name:      I_PostSendListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;


/**
 * This interface handles ONLY asynchronous sending events. 
 * <p>
 * Notify when a message from a queue is successfully send asynchronously. 
 * @author Marcel Ruff
 */
public interface I_PostSendListener {
        
   /**
    * Called after a messages is send asynchronously from connection queue.
    * Is triggered for oneway messages as well (PUBLISH_ONEWAY, UPDATE_ONEWAY) 
    * @param msgQueueEntries, includes the returned QoS
    */
   public void postSend(MsgQueueEntry[] msgQueueEntries);

   /**
    * Called if an asynchronous message is rejected by the server. 
    * <p>
    * If the server e.g. throws an IllegalArgument back to the client
    * the message will most probably never succeed and retrying to send
    * the message makes no sense. You can intercept this case
    * here and eliminate the message.
    * <p>
    * ErrorCodes of type "communication.*" are not reported here
    * as the dispatcher framework automatically handles reconnect and retry.
    * <p>
    * NOTE:
    * For ErrorCodes of type "authentication.*" the connection will
    * go to DEAD and the connection queue entries remain for pubSessionId>0.
    * 
    * For pubSessionId<0 (none fail safe) the queue entries are removed (to be implemented TODO).  
    *  
    * @param entries Each MsgQueueEntry includes the returned QoS
    * @param exception The cause
    * 
    * @return 
    * false: We have not handled this case and the dispatcher framework
    * does its default handling.
    * For the client it is just a notification.
    * 
    * true: We have processed some error handling and the dispatch framework
    * will remove the message from the queue and continue with sending the next message.
    * This is for example done internally by cluster client plugins inside cluster nodes
    * which will propagate the message to the error handler which emits a dead message.
    * 
    * 1. true: Remove msg from queue
    * 2. false: DispatcherActive false
    * 3. toDead (remove all messages) -> you call disconnect()
    * 4. ErrorCode.authenticate.* -> leaveServer() for pubSessionId>0 and call I_ConnectionStateListener.toDead
    *    else toDead (removes all queue entries)
    */
   public boolean sendingFailed(MsgQueueEntry[] entries, XmlBlasterException exception);
}
