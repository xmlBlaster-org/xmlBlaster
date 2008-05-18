/*------------------------------------------------------------------------------
Name:      RamQueuePlugin.cs
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/

using System.Text;
using System.Collections;

namespace org.xmlBlaster.util
{
   public class RamQueuePlugin : I_Queue
   {
      private long sizeInBytes = 0L;
      private long persistentSizeInBytes = 0L;
      private long numOfPersistentEntries = 0L;
      private long maxNumOfBytes;
      private Hashtable storage;

      public void Initialize(Object /*StorageId*/ storageId, Hashtable properties)
      {
         this.maxNumOfBytes = 10000000;
      }

      public void Put(I_QueueEntry queueEntry)
      {
      }

      public I_QueueEntry Peek()
      {
      }

      public int Remove()
      {
      }

      public long GetNumOfPersistentEntries()
      {
         return this.numOfPersistentEntries;
      }

      public long getNumOfPersistentBytes()
      {
         return this.persistentSizeInBytes;
      }

      public long getMaxNumOfBytes()
      {
         return this.maxNumOfBytes
      }

      public long clear()
      {
         return 0;
      }

      public void shutdown()
      {
      }
   }
} // namespace