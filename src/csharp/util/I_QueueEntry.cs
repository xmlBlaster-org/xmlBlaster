/*------------------------------------------------------------------------------
Name:      I_QueueEntry.cs
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

using System.Text;
using System.Collections;

namespace org.xmlBlaster.util
{
   public interface I_QueueEntry
   {
      /**
       * Allows to query the priority of this entry.
       * This is the highest order precedence in the sorted queue
       * @return The priority
       */
      int GetPriority();

      /**
       * Returns true if the entry is persistent (persistent on HD), false otherwise.
       */
      bool IsPersistent();

      /**
       * This is the second order criteria in the queue
       * @return The unique Id of this entry.
       */
      string GetUniqueId();

      /**
       * gets the content of this queue entry (the embedded object). In
       * persistent queues this is the data which is stored as a blob.
       */
      object getEmbeddedObject();

      /**
       * Gets the type of the object embedded in this entry, how the object is serialized.
       * @return String the identifier which tells the I_EntryFactory how to
       *         deserialize this entry.<br />
       *         ServerEntryFactory.ENTRY_TYPE_MSG_XML or ServerEntryFactory.ENTRY_TYPE_MSG_SERIAL or ClientEntryFactory.ENTRY_TYPE_MSG_XML
       */
      string getEmbeddedType();

      /**
       * Return a human readable identifier for logging output.
       * <p>
       * See the derived class for a syntax description.
       * </p>
       */
      string getLogId();

      /**
       * returns the size in bytes of this entry.
       */
      long GetSizeInBytes();

      /**
       * Notification if this entry is added to storage. 
       * <p>
       * NOTE: This event is NOT triggered on startup if entries come from harddisk
       * from the last run. It is NOT triggered during swapping.
       *</p>
       * <p>This callback may or may not be called from within the queue specific synchronized block</p>
       * <p>NOTE: This callback is currently only implemented for I_Queue, but not for I_Map!</p>
       */
      //void added(StorageId storageId);

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
      //void removed(StorageId storageId);

      /**
       * Dump content to xml representation
       * @param out The stream to dump to
       * @param props Control porperties
       */
      //void embeddedObjectToXml(java.io.OutputStream out, java.util.Properties props) throws java.io.IOException;

      /**
       * Needed for sorting in queue
       */
      int compare(I_QueueEntry m2);

      /**
       * Needed for sorting in queue
       */
      bool equals(I_QueueEntry m2);
   }
} // namespace