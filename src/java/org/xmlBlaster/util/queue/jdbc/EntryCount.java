package org.xmlBlaster.util.queue.jdbc;

public class EntryCount {
   public long numOfEntries = 0L;
   public long numOfPersistentEntries = 0L;
   public long numOfBytes = 0L;
   public long numOfPersistentBytes = 0L;
   public String toString() {
      return "numOfEntries=" + numOfEntries +
      " numOfPersistentEntries=" + numOfPersistentEntries +
      " numOfBytes=" + numOfBytes +
      " numOfPersistentBytes=" + numOfPersistentBytes;
   }
   
}

