/*------------------------------------------------------------------------------
Name:      I_AdminMap.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface to access information about a client instance
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.admin;


/**
 * Declares available methods of a map implementation for administration. 
 * <p />
 * SNMP or telnet tools can access only the here declared properties.<br />
 * This interface is implemented by I_Map.java implementations delivering the meat.
 * @author xmlBlaster@marcelruff.info
 * <p />
 * Don't invoke any operations on this interface, it will certainly destroy
 * your running application!
 *
 * @author xmlBlaster@marcelruff.info
 * @since 1.0.5
 * @see org.xmlBlaster.engine.msgstore.I_Map
 */
public interface I_AdminMap {
   /**
    * Access the current queue configuration. 
    */
   public String getPropertyStr();
   /**
    * Returns the unique ID of this queue as found in the database XB_ENTRIES.queueName column. 
    * @return For example "topicStore_heron"
    */
   public String getQueueName();
   /**
    * Remove the specified storage entry. 
    * @param uniqueId The entry identifier
    * @return the number of elements erased.
    */
   public int removeById(long uniqueId) throws Exception;
   /**
    * Remove the oldest storage entry from cache. 
    * @return The entry removed
    */
   public String removeOldestEntry() throws Exception;
   /**
    * Returns the number of elements in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporary not available) it will return -1.
    * @return int the number of elements
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
    * returns the maximum number of elements for this cache store
    * @return int the maximum number of elements in the queue
    */
   public long getMaxNumOfEntries();
   /**
    * Returns the amount of bytes currently in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The number of elements currently in the queue
    */
   public long getNumOfBytes();
   /**
    * Returns the amount of bytes used by the persistent entries in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return int the number of elements currently in the queue
    */
   public long getNumOfPersistentBytes();
   /**
    * returns the capacity (maximum bytes) for this queue
    * @return int the maximum number of elements in the queue
    */
   public long getMaxNumOfBytes();
   /**
    * Removes all the transient entries (the ones which have the flag 'persistent'
    * set to false.
    * @return Number of entries erased
    */
   public int removeTransientEntries() throws Exception;
   /**
    * Delete all entries
    * @return Number of entries removed
    */
   public long clear();
   /**
    * Performs what has to be done when the Map Plugin shuts down.
    */
   public void shutdown();
   /**
    * Is the storage available? 
    * @return true if shutdown
    */
   public boolean isShutdown();

   /**
    * Dump all entries of this map to a file. 
    * The messages are XML formatted.
    * @param fileName The file name to dump, may contain a path.
    * @return Status string
    */
   public String dumpEmbeddedObjectsToFile(String fileName) throws Exception;
}
