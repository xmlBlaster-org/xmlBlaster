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

}
