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
    *
    * @param queueEntry Is guaranteed to never be null
    * @return true: Continue to put message into queue, false: return without putting entry into queue
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   boolean putPre(I_QueueEntry queueEntry) throws XmlBlasterException;

   /**
    * Called by I_Queue implementation when a put() is invoked and
    * somebody has registered for such events
    *
    * @param queueEntries Is guaranteed to never be null
    * @return true: Continue to put message into queue, false: return without putting entry into queue
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   boolean putPre(I_QueueEntry[] queueEntries) throws XmlBlasterException;

   /**
    * Called by I_Queue implementation before leaving put() and
    * somebody has registered for such events.
    * The message is already safely entered to the queue.
    *
    * @param queueEntry Is guaranteed to never be null
    * @see I_Queue#put(I_QueueEntry, boolean)
    */
   void putPost(I_QueueEntry queueEntry) throws XmlBlasterException;

   /**
    * Called by I_Queue implementation before leaving put() and
    * somebody has registered for such events.
    * The message is already safely entered to the queue.
    *
    * @param queueEntries Is guaranteed to never be null
    * @see I_Queue#put(I_QueueEntry[], boolean)
    */
   void putPost(I_QueueEntry[] queueEntries) throws XmlBlasterException;
}
