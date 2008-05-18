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
      //private long sizeInBytes = 0L;
      private long persistentSizeInBytes = 0L;
      private long numOfPersistentEntries = 0L;
      private long maxNumOfBytes;
      //private Hashtable storage;

      public void Initialize(object /*StorageId*/ storageId, Hashtable properties)
      {
         this.maxNumOfBytes = 10000000;
      }

      public void Put(I_QueueEntry queueEntry)
      {
      }

      public I_QueueEntry Peek()
      {
         return null;
      }

      public int Remove()
      {
         return 0;
      }

      public long GetNumOfPersistentEntries()
      {
         return this.numOfPersistentEntries;
      }

      public long GetNumOfPersistentBytes()
      {
         return this.persistentSizeInBytes;
      }

      public long GetMaxNumOfBytes()
      {
         return this.maxNumOfBytes;
      }

      public long Clear()
      {
         return 0;
      }

      public void Shutdown()
      {
      }
   }
} // namespace