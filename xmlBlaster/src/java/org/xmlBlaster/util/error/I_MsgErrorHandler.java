/*------------------------------------------------------------------------------
Name:      I_MsgErrorHandler.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;

/**
 * You need to implement this interface to be notified on unrecoverable errors. 
 * <p>
 * For example called by deliveryManager if a message is lost or the queue overflows
 *
 * @author ruff@swand.lake.de
 */
public interface I_MsgErrorHandler
{
   /**
    * The final recovery, all informations necessary are transported in msgErrorInfo. 
    * <p>
    * This handler is called for example from the 'put' side of a queue if the queue is full
    * or from the 'take' side from the queue e.g. if DeliveryManager exhausted to reconnect.
    */
   public void handleError(I_MsgErrorInfo msgErrorInfo);
   
   public void shutdown();
}

