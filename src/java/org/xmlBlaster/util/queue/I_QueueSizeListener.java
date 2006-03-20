/*------------------------------------------------------------------------------
Name:      I_QueueSizeListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

/**
 * I_QueueSizeListener listens on number of containing entry changes in the queue.
 * 
 * @author <a href="mailto:mr@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:laghi@swissinfo.org">Michele Laghi</a>
 */
public interface I_QueueSizeListener {
   
   
   /**
    * This event is sent every time a change in entries has occurred in the
    * queue. 
    * <p /
    * It is invoked after the change has taken place. 
    * It allows for example to generate threshold alarms.
    * <p /
    * The changed() invocation is guaranteed to NOT be in any Queue specific synchronize
    *
    * @param queue The queue which fires the change event 
    * @param numEntries the number of entries in the queue after the
    *        change has taken place
    * @param numBytes number of bytes in the queue after the change
    *        has taken change.
    */
   public void changed(I_Queue queue, long numEntries, long numBytes);

}
