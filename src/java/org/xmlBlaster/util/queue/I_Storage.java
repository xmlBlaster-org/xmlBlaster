/*------------------------------------------------------------------------------
Name:      I_Storage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

public interface I_Storage
{
   /**
    * @return true for RAM based queue, false for other types like CACHE and JDBC queues
    */
   boolean isTransient();

   /**
    * Returns the unique ID of this queue. 
    * @return For example "history_heronhello"
    */
   StorageId getStorageId();

   
   /**
    * Returns the maximum number of elements for this queue
    * @return The maximum number of elements in the queue
    */
   long getMaxNumOfEntries();

   /**
    * Removes the specified listener from the queue.
    * 
    * @param listener the listener to be removed. Currently only one.
    *        If you pass null, all queueSizeListeners are removed.
    * @exception IllegalArgumentException if the listener was not found
    */
   void removeStorageSizeListener(I_StorageSizeListener listener);
   
   /**
    * Checks wether the specified listener is registered.
    * @param listener the listener to check against. If you pass null
    *        it checks if at least one listener exists.
    * @return true if the specified listener exists, false otherwise. If
    *         you passed null in the argument list it returns true if a
    *         listener exists.
    */
   boolean hasStorageSizeListener(I_StorageSizeListener listener);
   
   /**
    * 
    * @return the array of storage size listeners for this storage. It never returns 
    * null.
    */
   I_StorageSizeListener[] getStorageSizeListeners();
   
   /**
    * Adds a storage size listener to the storage. 
    * Every time the number of storage entries changes we will fire a
    * changed() event.
    * <p />
    * The changed() invocation is guaranteed to NOT be in any Queue specific synchronize
    * <p />
    * You can use this for example to add a threshold warning system.
    * @param listener the listener to be added, adding the same listener multiple times will only remember one and fire once
    * @exception IllegalArgumentException if you pass null
    */
   void addStorageSizeListener(I_StorageSizeListener listener);
   
   /**
    * Returns the number of elements in this queue.
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporary not available) it will return -1.
    * @return int the number of elements
    */
   long getNumOfEntries();

   /**
    * Returns the amount of bytes currently in the queue
    * If the implementation of this interface is not able to return the correct
    * number of entries (for example if the implementation must make a remote
    * call to a DB which is temporarly not available) it will return -1.
    * @return The number of elements currently in the queue
    */
   long getNumOfBytes();

   /**
    * Performs what has to be done when the Map Plugin shuts down.
    */
   boolean isShutdown();

   
   
}
