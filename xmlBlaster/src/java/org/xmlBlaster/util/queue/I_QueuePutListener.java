/*------------------------------------------------------------------------------
Name:      I_QueuePutListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.XmlBlasterException;
import java.util.EventListener;

public interface I_QueuePutListener extends EventListener
{
   /**
    * Called by I_Queue implementation when a put() is invoked and
    * somebody has registered for such events
    * @param queueEntry Is guaranteed to never be null
    * @return Some ACK object or null if none is supplied
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   Object put(I_QueueEntry queueEntry) throws XmlBlasterException;

   /**
    * Called by I_Queue implementation when a put() is invoked and
    * somebody has registered for such events
    * @param queueEntries Is guaranteed to never be null
    * @return An ACK object for each queueEntry (ackObject.length == queueEntries.length) or null
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   Object[] put(I_QueueEntry[] queueEntries) throws XmlBlasterException;
}
