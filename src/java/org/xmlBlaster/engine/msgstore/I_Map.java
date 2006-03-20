/*------------------------------------------------------------------------------
Name:      I_Map.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for a map (persistent and cache)
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import java.io.OutputStream;
import java.util.Properties;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;


/**
 * The Interface which our persistent map and cache map must implement. 
 * </p>
 * All methods are reentrant and thread safe
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Map extends I_Storage, I_StorageProblemNotifier
{
   /**
    * Is called after the instance is created.
    * @param uniqueMapId A unique name, allowing to create a unique name for a persistent store (e.g. file name)
    *                "history:/node/heron/topic/<oid>"
    * @param userData For example a Properties object or a String[] args object passing the configuration data
    */
   void initialize(StorageId uniqueMapId, Object userData)
      throws XmlBlasterException;

   /**
    * Returns the unique ID of this cache
    */
   StorageId getStorageId();

   /**
    * Allows to overwrite properties which where passed on initialize()
    * The properties which support hot configuration are depending on the used implementation
    */
   void setProperties(Object userData) throws XmlBlasterException;

   /**
    * Access the current queue configuration
    */
   Object getProperties();

   /**
    * Lookup entry without removing. 
    * @return null if not found
    */
   I_MapEntry get(final long uniqueId) throws XmlBlasterException;

   /**
    * Retrieve all entries in the storage, please take care on memory consumption.
    * @param entryFilter null to get everything. If specified you can filter by this
    * callback the wanted entries.
    * @return A current snapshot of all entries
    */
   I_MapEntry[] getAll(I_EntryFilter entryFilter) throws XmlBlasterException;
   
   /**
    * Adds one entry and automatically increments the reference counter. 
    * Note: If an entry existed already (0 is returned), it is NOT updated in storage
    *
    * @param msgMapEntry the entry
    * @throws XmlBlasterException in case an error occurs. Possible causes of
    * error can be a communication exception of the underlying implementation (jdbc, file system etc).
    * @return Number of new entries added: 0 if entry existed, 1 if new entry added
    */
   int put(I_MapEntry mapEntry) throws XmlBlasterException;

   /**
    * @return the number of elements erased.
    */
   int remove(final I_MapEntry mapEntry) throws XmlBlasterException;

   /**
    * @return the number of elements erased.
    */
   int remove(final long uniqueId) throws XmlBlasterException;

   /**
    * Remove the oldest entry. 
    * 'Oldest' is defined in the context of the implementation to support an
    * efficient cache, typically it is the LRU (last recent used) entry.
    * @return the removed entry
    */
   I_MapEntry removeOldest() throws XmlBlasterException;

   /**
    * Removes all the transient entries (the ones which have the flag 'persistent'
    * set to false.
    * @return Number of entries erased
    */
   int removeTransient() throws XmlBlasterException;

   /**
    * Delete all entries
    * @return Number of entries removed
    */
   long clear() throws XmlBlasterException;

   /**
    * Returns the number of elements in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporary not available) it will return -1.
    * @return int the number of elements
    */
   long getNumOfEntries();

   /**
    * Returns the number of elements having the persistent flag set in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return int the number of elements currently in the queue
    */
   long getNumOfPersistentEntries();

   /**
    * returns the maximum number of elements for this cache
    * @return int the maximum number of elements in the queue
    */
   long getMaxNumOfEntries();

   /**
    * Returns the amount of bytes currently in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The number of elements currently in the queue
    */
   long getNumOfBytes();

   /**
    * Returns the amount of bytes used by the persistent entries in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return int the number of elements currently in the queue
    */
   long getNumOfPersistentBytes();

   /**
    * returns the capacity (maximum bytes) for this queue
    * @return int the maximum number of elements in the queue
    */
   long getMaxNumOfBytes();

   /**
    * Shutdown the implementation, sync with data store, free resources
    */
   void shutdown();

   /**
    * Performs what has to be done when the Map Plugin shuts down.
    */
   boolean isShutdown();

   /**
    * destroys all the resources associated to this queue. This means that all
    * temporary and persistent resources are removed.
    */
   void destroy() throws XmlBlasterException;

   /**
    * @return a human readable usage help string
    */
   String usage();

   /**
    * @param extraOffset Indent the dump with given ASCII blanks
    * @return An xml encoded dump
    */
   String toXml(String extraOffset);

   /**
    * Dump all entries of this map to the given output stream. 
    * The messages are XML formatted.
    * @param out The output stream to dump the entries
    * @param props Configuration properties, not yet specified, just pass null
    * @return Number of entries dumped
    */
   public long embeddedObjectsToXml(OutputStream out, Properties props) throws Exception;

   /**
    * @param entry the entry to change. This is the old entry, i.e. the entry on which the modification
    *        has to take place by callback.
    *        Or it is the new entry and you can pass null for callback.
    *        IMPORTANT: This method is not threadsafe since it does not make a lookup
    *        to get the actual entry. The specified entry could be a dirty read, in which case the 
    *        current entry would be overwritten with this dirty value. If you want to work threadsafe 
    *        you should invoke change(long, callback). That method makes a lookup within the same 
    *        synchronization point.
    * @param callback the object on which the callback method 'changeEntry' is invoked. The modification
    *        of the object is done in that method. If you pass null, then the changeEntry is not invoked
    *        and the processing continues with entry.
    * @return I_MapEntry the modified entry.
    * @throws XmlBlasterException if something goes wrong when making the change (for example if the
    *         entry is not in the map) or if the callback throws an exception.
    */
   I_MapEntry change(I_MapEntry entry, I_ChangeCallback callback) throws XmlBlasterException;

   /**
    * This method is threadsafe because it makes a lookup for the updated entry within the synchronization
    * point.
    * @param uniqueId the uniqueId of the entry to change. This is the old entry, i.e. the entry on 
    *        which the modification has to take place.
    * @param callback the object on which the callback method 'changeEntry' is invoked. The modification
    *        of the object is done in that method. If you pass null, then the changeEntry is not invoked
    *        and the processing continues with entry.
    * @return I_Entry the modified entry.
    * @throws XmlBlasterException if something goes wrong when making the change (for example if the
    *         entry is not in the map) or if the callback throws an exception.
    */
   I_MapEntry change(long uniqueId, I_ChangeCallback callback) throws XmlBlasterException;

}
