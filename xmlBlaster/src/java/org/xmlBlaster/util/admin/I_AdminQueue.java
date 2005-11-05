/*------------------------------------------------------------------------------
Name:      I_AdminQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;

import org.xmlBlaster.util.XmlBlasterException;

/**
 * Declares available methods of a queue implementation for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by I_Queue.java implementations delivering the meat.
 * <p />
 * Don't invoke any operations on this interface, it will certainly destroy
 * your running application!
 *
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.4
 * @see org.xmlBlaster.util.queue.I_Queue
 */
public interface I_AdminQueue extends I_AdminPlugin {
   /**
    * Access the current queue configuration
    */
   public String getPropertyStr();

   /**
    * Defaults to false. 
    * @return true The I_QueueEntry.addedToQueue() and removedFromQueue() are invoked<br />
    *        false The entries are not informed
    */
   public boolean isNotifiedAboutAddOrRemove();

   /**
    * Gets the references of the entries in the queue. Note that the data
    * which is referenced here may be changed by other threads.
    * @return Array with reference numbers
    */
   public long[] getEntryReferences() throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Returns the unique ID of this queue as found in the database XB_ENTRIES.queueName column. 
    * @return For example "history_heronhello"
    */
   public String getQueueName();

   /**
    * Takes given number of entries out of the queue. The ordering is first priority and secondly timestamp.
    * This method blocks until at least one entry is found
    * @param numOfEntries Take numOfEntries entries, if -1 take all entries currently found
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway.
    * @param minPriority The lower priority (inclusive), usually 0 lowest, 9 highest
    * @param maxPriority The higher priority (inclusive), usually 0 lowest, 9 highest
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException in case the underlying implementation gets an exception while retrieving the element.
    */
   public java.util.ArrayList takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)       throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Returns the first element in the queue
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @return I_QueueEntry the least element with respect to the given ordering or null if the queue is empty.
    * @throws Exception if the underlying implementation gets an exception.
    */
   public String peekStr() throws Exception;

   /**
    * Returns maximum the first num element in the queue
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @param numOfEntries Access num entries, if -1 access all entries currently found
    * @return list with I_QueueEntry.toString(), the least elements with respect to the given ordering, or size()==0
    * @throws Exception if the underlying implementation gets an exception.
    */
   public String[] peekEntries(int numOfEntries) throws Exception;

   /**
    * Removes the first element in the queue. 
    * This method does not block.
    * @return the size in bytes of the removed elements
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public int remove() throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Removes max num messages.
    * This method does not block.
    * @param numOfEntries Erase num entries or less if less entries are available, -1 erases everything
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is removed anyway.
    * @return Number of entries erased
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   public long remove(long numOfEntries, long numOfBytes) throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Removes max numOfEntries messages (or less depending on the numOfBytes).
    * This method does not block.
    * @param numOfEntries Erase num entries or less if less entries are available, -1 erases everything
    * @param numOfBytes so many entries are returned as not to exceed the amout specified. If the first
    *        entry is bigger than this amount, it is returned anyway.
    * @param minPriority The lower priority (inclusive), usually 0 lowest, 9 highest
    * @param maxPriority The higher priority (inclusive), usually 0 lowest, 9 highest
    * @return Number of entries erased
    * @throws XmlBlasterException in case the underlying implementation gets an exception while retrieving the element.
    */
   public long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * Returns the number of elements in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return int the number of elements currently in the queue
    */

   public long getNumOfEntries();
   /**
    * Returns the number of elements having the persistent flag set in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return int the number of elements currently in the queue
    */
   public long getNumOfPersistentEntries();

   /**
    * Returns the maximum number of elements for this queue
    * @return The maximum number of elements in the queue
    */
   public long getMaxNumOfEntries();

   /**
    * Returns the amount of bytes currently in the queue. 
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The amount of bytes currently in the queue
    */
   public long getNumOfBytes();

   /**
    * Returns the amount of bytes used by the persistent entries in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The amount of bytes currently in the queue
    */
   public long getNumOfPersistentBytes();

   /**
    * Access the configured capacity (maximum bytes) for this queue
    * @return The maximum capacity for the queue in bytes
    */
   public long getMaxNumOfBytes();

   /**
    * Removes all the transient entries (the ones which have the flag 'persistent'
    * set to false.
    */
   public int removeTransient() throws org.xmlBlaster.util.XmlBlasterException;

   /**
    * @return true for RAM based queue, false for other types like CACHE and JDBC queues
    */
   public boolean isTransient();

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

   /**
    * Check status of queue. 
    * @return true if queue is not available anymore
    */
   public boolean isShutdown();

   //public void addQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);
   //public void removeQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);
   //public boolean hasQueueSizeListener(org.xmlBlaster.util.queue.I_QueueSizeListener);

   /**
    * Dump state to XML string. 
    * @param extraOffset Indent the dump with given ASCII blanks
    * @return An xml encoded dump
    */
   public java.lang.String toXml();

   /**
    * Dump all entries of this queue to a file. 
    * The messages are XML formatted.
    * @param fileName The file name to dump, may contain a path.
    * @return Status string
    */
   public String dumpEmbeddedObjectsToFile(String fileName) throws Exception;
}
