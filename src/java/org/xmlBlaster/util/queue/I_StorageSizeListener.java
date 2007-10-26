/*------------------------------------------------------------------------------
Name:      I_StorageSizeListener.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

package org.xmlBlaster.util.queue;

/**
 * I_StorageSizeListener listens on number of containing entry changes in the queue.
 * 
 * @author <a href="mailto:mr@marcelruff.info">Marcel Ruff</a>
 * @author <a href="mailto:michele@laghi.eu">Michele Laghi</a>
 */
public interface I_StorageSizeListener {
   
   
   /**
    * This event is sent every time a change in entries has occurred in the
    * queue. 
    * <p />
    * It is invoked after the change has taken place. 
    * It allows for example to generate threshold alarms.
    * <p />
    * changed() is additionally called if the queue is shutdown (you can't operate on the queue
    * anymore in this case).
    * <p />
    * The changed() invocation is guaranteed to NOT be in any Queue specific synchronize
    *
    * @param queue The queue which fires the change event 
    * @param numEntries the number of entries in the queue after the
    *        change has taken place
    * @param numBytes number of bytes in the queue after the change
    *        has taken change.
    * @param isShutdown Is set to true if queue.shutdown() was executed
    */
   void changed(I_Storage queue, long numEntries, long numBytes, boolean isShutdown);
   
}
