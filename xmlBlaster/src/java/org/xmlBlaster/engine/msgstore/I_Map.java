/*------------------------------------------------------------------------------
Name:      I_Map.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Interface for a map (persistent and cache)
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.msgstore;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.queue.I_StorageProblemNotifier;
import java.util.ArrayList;


/**
 * The Interface which our persistent map and cache map must implement. 
 * </p>
 * All methods are reentrant and thread safe
 * @author xmlBlaster@marcelruff.info
 */
public interface I_Map extends I_StorageProblemNotifier
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
    * @return A current snapshot of all entries
    */
   I_MapEntry[] getAll() throws XmlBlasterException;
   
   /**
    * Adds one entry and automatically increments the reference counter. 
    * @param msgMapEntry the entry
    * @throws XmlBlasterException in case an error occurs. Possible causes of
    * error can be a communication exception of the underlying implementation (jdbc, file system etc).
    * @return Number of new entries added: 0 if entry existed, 1 if new entry added
    *         Note: If an entry existed already (0 is returned), it is NOT updated in storage
    */
   int put(I_MapEntry mapEntry) throws XmlBlasterException;

   /**
    * @return the number of elements erased.
    */
   int remove(final I_MapEntry mapEntry) throws XmlBlasterException;

   /**
    * Removes all the transient entries (the ones which have the flag 'persistent'
    * set to false.
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
    * @param true: force shutdown, don't flush everything
    */
   void shutdown(boolean force);

   /**
    * performs what has to be done when the Map Plugin shuts down.
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
}
