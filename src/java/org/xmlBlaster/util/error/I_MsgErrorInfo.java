/*------------------------------------------------------------------------------
Name:      I_MsgErrorInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.dispatch.DispatchManager;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.XmlBlasterException;

/**
 * Encapsulates all necessary information to allow error handling
 * of a lost message. 
 * <p>
 * </p>
 * @author xmlBlaster@marcelruff.info
 */
public interface I_MsgErrorInfo
{
   /**
    * Access the message entry object to be handled
    * @return never null
    */
   MsgQueueEntry[] getMsgQueueEntries();

   /**
    * Access the queue where the entries are inside. 
    * @return null if entries are not in a queue
    */
   I_Queue getQueue();

   /**
    * Access the DispatchManager which holds the queue. 
    * @return null if entries are not in a queue
    */
   DispatchManager getDispatchManager();

   /**
    * Access the exception object describing the problem
    * @return never null
    */
   XmlBlasterException getXmlBlasterException();

   String toXml();

   String toXml(String extraOffset);
}

