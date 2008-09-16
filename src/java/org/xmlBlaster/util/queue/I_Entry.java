/*------------------------------------------------------------------------------
Name:      I_Entry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

import org.xmlBlaster.util.queue.jdbc.XBMeat;
import org.xmlBlaster.util.queue.jdbc.XBRef;
import org.xmlBlaster.util.XmlBlasterException;

public interface I_Entry extends java.io.Serializable
{
   /**
    * Allows to query the priority of this entry.
    * This is the highest order precedence in the sorted queue
    * @return The priority
    */
   int getPriority();

   /**
    * Returns true if the entry is persistent (persistent on HD), false otherwise.
    */
   boolean isPersistent();

   /**
    * This is the second order criteria in the queue
    * @return The unique Id of this entry.
    */
   long getUniqueId();

   /**
    * gets the content of this queue entry (the embedded object). In
    * persistent queues this is the data which is stored as a blob.
    */
   Object getEmbeddedObject();

   /**
    * Gets the type of the object embedded in this entry, how the object is serialized.
    * @return String the identifier which tells the I_EntryFactory how to
    *         deserialize this entry.<br />
    *         ServerEntryFactory.ENTRY_TYPE_MSG_XML or ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL or ClientEntryFactory.ENTRY_TYPE_MSG_XML
    */
   String getEmbeddedType();

   /**
    * Return a human readable identifier for logging output.
    * <p>
    * See the derived class for a syntax description.
    * </p>
    */
   String getLogId();

   /**
    * returns the size in bytes of this entry.
    */
   long getSizeInBytes();

   /**
    * Notification if this entry is added to storage. 
    * <p>
    * NOTE: This event is NOT triggered on startup if entries come from harddisk
    * from the last run. It is NOT triggered during swapping.
    *</p>
    * <p>This callback may or may not be called from within the queue specific synchronized block</p>
    * <p>NOTE: This callback is currently only implemented for I_Queue, but not for I_Map!</p>
    */
   void added(StorageId storageId);

   /**
    * Notification if this entry is removed from storage
    * <p>
    * NOTE: This event is NOT triggered during swapping.
    *</p>
    * <p>
    * The callback is guaranteed to be NEVER called from inside a queue specific synchronized block.
    * </p>
    * <p>NOTE: This callback is currently only implemented for I_Queue, but not for I_Map!</p>
    * @param storageId The storage id
    */
   void removed(StorageId storageId);

   /**
    * Is invoked by the storage implementation with 'true' when the entry is put
    * in a storage and with 'false' when the entry is removed from the storage.
    * The storage is for example a 'cache', a 'ram' or a 'jdbc' implementation.
    * <p>Note that this callback is guaranteed to be called from within the queue specific synchronized block</p>
    * Todo: Remove this in favour of added()/removed() (isStored() is currently only used by CacheQueueInterceptorPlugin.java)
    *       I'm not sure if this usage relies on being inside sync
    * <p>This method may never throw any exception</p>
    * @param stored 'true' if the entry will be put into the storage, 'false' if it is removed.
    */
   void setStored(boolean stored);

   /**
    * @return boolean 'true' if the entry is still in the storage, 'false' if the entry has been removed
    *         or if it has not been put in the storage yet.
    */
   boolean isStored();
   
   /**
    * Dump content to xml representation
    * @param out The stream to dump to
    * @param props Control porperties
    */
   void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException;
   
   
   XBMeat getMeat() throws XmlBlasterException;
   
   
   XBRef getRef();
   
}
