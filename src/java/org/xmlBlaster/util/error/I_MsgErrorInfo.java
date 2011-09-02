/*------------------------------------------------------------------------------
Name:      I_MsgErrorInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.error;

import org.xmlBlaster.util.dispatch.I_DispatchManager;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.MsgUnitRaw;
import org.xmlBlaster.util.SessionName;
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
   I_DispatchManager getDispatchManager();

   /**
    * Access the exception object describing the problem
    * @return never null
    */
   XmlBlasterException getXmlBlasterException();
   
   
   /**
    * Access the sender.  
    * @return null if not known
    */
   SessionName getSessionName();

   /**
    * Is not null for client calls into server like XmlBlasterImpl.publish()
    * where no queue is involved.   
    * @return null if not known
    */
   MsgUnit getMsgUnit();

   /**
    * Is not null for client calls into server like XmlBlasterImpl.publish()
    * where no queue is involved and the message could not be parsed.   
    * @return null if not known
    */
   MsgUnitRaw getMsgUnitRaw();

   String toXml();

   String toXml(String extraOffset);
}

