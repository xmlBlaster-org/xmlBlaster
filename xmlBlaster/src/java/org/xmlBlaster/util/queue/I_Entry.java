/*------------------------------------------------------------------------------
Name:      I_Entry.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.queue;

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
    * Notification if this entry is added to storage
    */
   void added(StorageId storageId) throws XmlBlasterException;

   /**
    * Notification if this entry is removed from storage
    */
   void removed(StorageId storageId) throws XmlBlasterException;
}
