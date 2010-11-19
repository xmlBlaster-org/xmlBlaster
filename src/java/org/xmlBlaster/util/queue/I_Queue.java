/*------------------------------------------------------------------------------
Name:      I_Queue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for the queues (persistent and cache)
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import java.io.OutputStream;
import java.util.List;
import java.util.Properties;

import org.xmlBlaster.util.XmlBlasterException;


/**
 * The Interface which all queues (persistent queues and cache queues) must implement.
 *
 * Note on shared store (or single point of persistence). In most cases the storage
 * of the raw data for the queues is done centrally, for example in a database
 * or on a file system or on the Ram. We call this space which is shared by all
 * queues a 'shared store' or in case of persistent queues 'single point of
 * persistence'. On this space each queue is represented by a 'persistent queue
 * entity' (which in case of a DB could be a Table). In such a design it is
 * theoretically possible to have two queues (two java objects on the same- or on
 * different JVM) which point to the same persistent queue entity. It should be
 * avoided to have that happen simultaneously since wether the persistent queue
 * entity, nor the two objects have knowledge of each other. This could lead to
 * unexpected and undesired results in case the queues store part of the
 * information on cache.
 * <p>
 * As an example for sorting see org.xmlBlaster.util.queuemsg.MsgQueueEntry#compare(I_QueueEntry)
 * </p>
 * <p>
 * All methods are reentrant and thread safe
 * </p>
 * @author michele@laghi.eu
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Queue extends I_Storage, I_StorageProblemNotifier
{
   public final boolean IGNORE_PUT_INTERCEPTOR = true;
   public final boolean USE_PUT_INTERCEPTOR = false;

   /**
    * Is called after the instance is created.
    * @param uniqueQueueId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    *                "update:/node/heron/client/joe/2", "history:<oid>", "client:joe/2"
    * @param userData For example a Properties object or a String[] args object passing the configuration data
    */
   public void initialize(StorageId storageId, Object userData)
      throws XmlBlasterException;

   /**
    * Allows to overwrite properties which where passed on initialize()
    * The properties which support hot configuration are depending on the used implementation
    */
   public void setProperties(Object userData) throws XmlBlasterException;

   /**
    * Access the current queue configuration
    */
   public Object getProperties();

   /**
    * @param true The I_QueueEntry.addedToQueue() and removedFromQueue() are invoked<br />
    *        false The entries are not informed
    */
   public void setNotifiedAboutAddOrRemove(boolean notify);

   /**
    * Defaults to false. 
    * @return true The I_QueueEntry.addedToQueue() and removedFromQueue() are invoked<br />
    *        false The entries are not informed
    */
   public boolean isNotifiedAboutAddOrRemove();

   /**
    * Register a listener which wants to be informed on put() events.
    * <p />
    * Only one listener is currently supported.
    * @exception IllegalArgumentException If a listener is registered already 
    */
   public void addPutListener(I_QueuePutListener l);

   /**
    * Remove the listener which wanted to be informed on put() events. 
    * <p />
    * The currently registered listener is removed.
    * If no listener is registered, this call is silently ignored
    * @param The given listener is currently ignored
    */
   public void removePutListener(I_QueuePutListener l);

   /**
    * Gets the references of the entries in the queue. Note that the data
    * which is referenced here may be changed by other threads.
    * The entries are not removed.
    * @return Array with reference numbers
    */
   long[] getEntryReferences() throws XmlBlasterException;

   /**
    * Gets a copy of the entries (e.g the messages) in the queue. 
    * If the queue is modified, this copy will not be affected.
    *  This method is useful for client browsing, the entries are not removed
    *  @param entryFilter if not null the you can control which entries to return
    *  with the callback entryFilter.intercept(I_Entry).
    *  @return The found entries
    */
   List<I_Entry> getEntries(I_EntryFilter entryFilter) throws XmlBlasterException;

   // This is not true: Puts one queue entry on top of the queue possibly waiting indefinitely until it is accepted.

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
    * @see #put(I_QueueEntry[], boolean)
    */
   void put(I_QueueEntry queueEntry, boolean ignorePutInterceptor)
      throws XmlBlasterException;


   /**
    * Puts one queue entry on top of the queue. It does not wait. If the queue is ALREADY full at the time of
    * the invocation, it will throw an exception. Full means here that the maximum number of entries OR the
    * maximum size in bytes has been exceeded. This means that the queue can be overloaded once.
    * </p>
    * The implementation must assure that identical entries which are put
    * twice are only once in the store.
    * The behavior if the new entry overwrites the old entry is undefined.
    *
    * @param msgQueueEntries the queue entry to put into the queue.
    * @param ignorePutInterceptor if set to 'IGNORE_PUT_INTERCEPTOR=true' the put will not inform the
    *        QueuePutListener that a put occurred.
    * @throws XmlBlasterException in case an error occurs. Possible causes of
    * error can be a communication exception of the underlying implementation (jdbc, file system etc).
    * @return An ACK object for each queueEntry (ackObject.length == queueEntries.length) or null
    * @see I_QueuePutListener#putPre(I_QueueEntry[])
    * @see I_QueuePutListener#putPost(I_QueueEntry[])
    */
   void put(I_QueueEntry[] queueEntries, boolean ignorePutInterceptor)
      throws XmlBlasterException;

   /**
    * Takes an entry out of the queue. The ordering is first priority and secondly timestamp.
    * This method blocks until one entry is found
    * @return I_QueueEntry the least element with respect to the given ordering
    * @throws XmlBlasterException in case the underlying implementation gets an exception while retrieving the element.
    */
//   I_QueueEntry take() throws XmlBlasterException;

   /**
    * Takes given number of entries out of the queue. The ordering is first priority and secondly
    * timestamp. The more restrictive of the limits (numOfEntries, numOfBytes) decides how many entries
    * to take. This method blocks until at least one entry is found.
    * @param numOfEntries Take numOfEntries entries, if -1 take all entries currently found. If there
    *        are less than so many entries in the queue, all entries are taken.
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway.
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException in case the underlying implementation gets an exception while retrieving the element.
    */
   // List<I_Entry> take(int numOfEntries, long numOfBytes) throws
   // XmlBlasterException;

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
   List<I_Entry> takeWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
         throws XmlBlasterException;

   /**
    * Takes entries from the back of the queue. It takes so many entries as the
    * LESS restrictive of the limits specified in the argument list.
    * If you invoke this method as:
    * takeLowest(10, 50000L, someEntry);
    * It will take either 10 entries or as much entries which together do not exceed 50 kB (all entries
    * together) whichever is higher. Note that numOfEntries is exclusive.
    * If there is no entry of lower order (lower priority and higher uniqueId)
    * than the one specified, an empty array list is returned.
    * A further restriction is the following: if 'leaveOne' is 'true', then at least one entry must be 
    * left on the queue. 
    * 
    * A little example: suppose the size of every entry is 100 bytes and you invoke the following:
    * takeLowest(3, 750, null, false);
    * then it will give back 8 entries because:
    * numEntries would give back 3 entries. 750 bytes / 100 bytes/entry gives 7.5 entries so it would 
    * round it up to 8. Since it gives back the less restrictive it will give back 8 entries.
    *
    * @param numOfEntries inclusive, zero up to numOfEntries, if -1 up to the whole queue
    * @param numOfBytes inclusive, and minimum one is returned (but not if limitEntry suppress it)
    * @param entry
    * @param leaveOne Usually set to false. (true for cache queue to never flush transient queue totally)
    * @return the list containing all 'I_QueueEntry' entries which fit into the constrains, never null.
    */
   List<I_Entry> takeLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException;



   /**
    * Same as takeLowest but it does not remove the entries.
    */
   List<I_Entry> peekLowest(int numOfEntries, long numOfBytes, I_QueueEntry limitEntry, boolean leaveOne)
      throws XmlBlasterException;

   /**
    * Returns the first element in the queue
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @return I_QueueEntry the least element with respect to the given ordering or null if the queue is empty.
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   I_QueueEntry peek() throws XmlBlasterException;

   /**
    * Returns maximum the first num element in the queue
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @param numOfEntries Access num entries, if -1 access all entries currently found
    * @param numOfBytes is the maximum size in bytes of the array to return, -1 is unlimited .
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   List<I_Entry> peek(int numOfEntries, long numOfBytes) throws XmlBlasterException;

   /**
    * Returns maximum the first num element in the queue
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @param numOfEntries Access num entries, if -1 access all entries currently found
    * @param numOfBytes is the maximum size in bytes of the array to return, -1 is unlimited .
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   List<I_Entry> peekStartAt(int numOfEntries, long numOfBytes, I_QueueEntry firstEntryExlusive)
         throws XmlBlasterException;

   /**
    * Returns maximum the first num element in the queue of highest priority
    * but does not remove it from that queue (leaves it untouched).
    * This method does not block.
    * @param numOfEntries Access num entries, if -1 access all entries currently found
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway. -1 is unlimited.
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   List<I_Entry> peekSamePriority(int numOfEntries, long numOfBytes) throws XmlBlasterException;

   /**
    * Returns maximum given number of entries from the queue (none blocking).
    * @param numOfEntries Access num entries, if -1 take all entries currently found
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is returned anyway.
    * @param minPriority The lower priority (inclusive), usually 0 lowest, 9 highest, <0 is not allowed
    * @param maxPriority The higher priority (inclusive), usually 0 lowest, 9 highest, <0 is not allowed
    * @return list with I_QueueEntry, the least elements with respect to the given ordering, or size()==0
    * @throws XmlBlasterException in case the underlying implementation gets an exception while retrieving the element.
    */
   List<I_Entry> peekWithPriority(int numOfEntries, long numOfBytes, int minPriority, int maxPriority)
         throws XmlBlasterException;

   /**
    * It returns the entries which are higher than the entry specified in the argument list.
    * @param limitEntry the entry which limits the peek. Only entries of higher order, i.e.
    *        entries having a higher priority, or same priority and lower uniqueId are
    *        returned. If entryLimit is null or no entries are higher than entryLimit,
    *        an empty list is returned.
    * Note: The limitEntry does not need to be in the queue.
    * @deprecated you should use directly removeWithLimitEntry
    */
   List<I_Entry> peekWithLimitEntry(I_QueueEntry limitEntry) throws XmlBlasterException;

   /**
    * It removes the entries which are higher than the entry specified in the argument list.
    * @param limitEntry the entry which limits the remove. Only entries of higher order, i.e.
    *        entries having a higher priority, or same priority and lower uniqueId are
    *        deleted. If entryLimit is null or no entries are higher than entryLimit,
    *        an empty list is returned.
    * @param inclusive if 'true', then also the entry specified will be removed (if it exists). If false
    *        the remove is exclusive, i.e. the specified entry is left in the queue.
    * Note: The limitEntry does not need to be in the queue.
    */
   long removeWithLimitEntry(I_QueueEntry limitEntry, boolean inclusive) throws XmlBlasterException;

   /**
    * Removes the first element in the queue. 
    * This method does not block.
    * @return the size in bytes of the removed elements
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   int remove() throws XmlBlasterException;

   /**
    * Removes max num messages.
    * This method does not block.
    * @param numOfEntries Erase num entries or less if less entries are available, -1 erases everything
    * @param numOfBytes so many entries are returned as not to exceed the amount specified. If the first
    *        entry is bigger than this amount, it is removed anyway.
    * @return Number of entries erased
    * @throws XmlBlasterException if the underlying implementation gets an exception.
    */
   long removeNum(long numOfEntries) throws XmlBlasterException;

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
   long removeWithPriority(long numOfEntries, long numOfBytes, int minPriority, int maxPriority) throws XmlBlasterException;

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

   /*
    * Updates the given queue entry with a new value. Note that this
    * can be used if an entry with the unique id already exists.
    * ?? Does this really make sense here since we need to store history ??
    * ?? Should we define a switch which can deactivate storage of history ??
    */
//   int update(I_QueueEntry queueEntry) throws XmlBlasterException;

   /*
    * Removes the given entry.
    * @param dataId the unique id. It must be unique within the storage area
    *        of the implementing queue. In other words, if the underlying
    *        implementation is on RAM, then the storage area is the JVM, that
    *        is the queue must be unique in the same JVM. If the queue is a
    *        jdbc, the dataId is unique in the DB used.
    * @returns the number of elements erased (0 or 1)
    */
//   int removeRandom(long dataId) throws XmlBlasterException;

   /*
    * Removes the given entries.
    * @param dataIdArray the entries to erase.
    * @return the number of elements erased.
    */
//   long removeRandom(long[] dataIdArray) throws XmlBlasterException;

   /**
    * Removes the given entries.
    * @param queueEntries the entries to erase.
    * @return a boolean array of the same size as the queueEntries array. If an entry is true it means
    *         it could be removed, if false it means it could not be removed (probably already removed)
    */
   boolean[] removeRandom(I_Entry[] queueEntries) throws XmlBlasterException;

   /**
    * Removes the given entry.
    * @param entry The entry to erase.
    * @return the number of elements erased.
    */
   int removeRandom(I_Entry entry) throws XmlBlasterException;

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

   /**
    * removes the head of the queue until (but not included) the entry specified
    * as the argument.
    * @param toEntry the entry until to remove.
    * @return long the number of entries deleted.
    */
   public long removeHead(I_QueueEntry toEntry) throws XmlBlasterException;


   /**
    * destroys all the resources associated to this queue. This means that all
    * temporary and persistent resources are removed.
    */
//   public void destroy() throws XmlBlasterException;

   /**
    * @return a human readable usage help string
    */
   public String usage();

   /**
    * Dump state to XML string. 
    * @param extraOffset Indent the dump with given ASCII blanks
    * @return An xml encoded dump
    */
   public String toXml(String extraOffset);

   /**
    * NOTE: rename from embeddedObjectsToXml to embeddedQueueObjectsToXml as it used the map lookup and lost priority info
    * Dump all entries of this queue to the given output stream. 
    * The messages are XML formatted.
    * @param out The output stream to dump the entries
    * @param props Configuration properties, not yet specified, just pass null
    * @return Number of entries dumped
    */
   public long embeddedQueueObjectsToXml(OutputStream out, Properties props) throws Exception;
   
}
