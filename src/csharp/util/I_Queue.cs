/*------------------------------------------------------------------------------
Name:      I_Queue.cs
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

using System.Text;

namespace org.xmlBlaster.util
{
   public interface I_Queue
   {

      /**
       * Is called after the instance is created.
       * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
       *                "update:/node/heron/client/joe/2", "history:<oid>", "client:joe/2"
       * @param userData For example a Properties object or a string[] args object passing the configuration data
       */
      public void Initialize(Object /*StorageId*/ storageId, Hashtable properties);

      /**
       * Puts one queue entry on top of the queue. 
       * See the other put() for a detailed description.
       * @param msgQueueEntry the queue entry to put into the queue.
       * @param ignorePutInterceptor if set to 'IGNORE_PUT_INTERCEPTOR=true' the put will not inform the
       *        QueuePutListener that a put occurred.
       * @throws XmlBlasterException in case an error occurs. Possible causes of
       * error can be a communication exception of the underlying implementation (jdbc, file system etc).
       * @see I_QueuePutListener#putPre(I_QueueEntry)
       * @see I_QueuePutListener#putPost(I_QueueEntry)
       * @see #put(I_QueueEntry[], bool)
       */
      void put(I_QueueEntry queueEntry);

      /**
       * Returns the first element in the queue
       * but does not remove it from that queue (leaves it untouched).
       * This method does not block.
       * @return I_QueueEntry the least element with respect to the given ordering or null if the queue is empty.
       * @throws XmlBlasterException if the underlying implementation gets an exception.
       */
      I_QueueEntry peek();

      /**
       * Returns maximum the first num element in the queue
       * but does not remove it from that queue (leaves it untouched).
       * This method does not block.
       * @param numOfEntries Access num entries, if -1 access all entries currently found
       * @param numOfBytes is the maximum size in bytes of the array to return, -1 is unlimited .
       * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
       * @throws XmlBlasterException if the underlying implementation gets an exception.
       */
      //ArrayList peek(int numOfEntries, long numOfBytes);

      /**
       * Removes the first element in the queue. 
       * This method does not block.
       * @return the size in bytes of the removed elements
       * @throws XmlBlasterException if the underlying implementation gets an exception.
       */
      int remove();

      /**
       * Returns the number of elements having the persistent flag set in this queue.
       * If the implementation of this interface is not able to return the correct
       * number of entries (for example if the implementation must make a remote
       * call to a DB which is temporarly not available) it will return -1.
       * @return int the number of elements currently in the queue
       */
      long getNumOfPersistentEntries();

      /**
       * Returns the amount of bytes used by the persistent entries in the queue
       * If the implementation of this interface is not able to return the correct
       * number of entries (for example if the implementation must make a remote
       * call to a DB which is temporarly not available) it will return -1.
       * @return The amount of bytes currently in the queue
       */
      long getNumOfPersistentBytes();

      /**
       * Access the configured capacity (maximum bytes) for this queue
       * @return The maximum capacity for the queue in bytes
       */
      long getMaxNumOfBytes();

      /**
       * Removes all the transient entries (the ones which have the flag 'persistent'
       * set to false.
      int removeTransient() throws XmlBlasterException;
       */

      /**
       * Remove all queue entries. 
       * @return The number of entries erased
       */
      public long clear();

      /**
       * Shutdown the implementation, sync with data store, free resources.
       * Persistent entries will NOT be deleted.
       */
      public void shutdown();
   }
} // namespace