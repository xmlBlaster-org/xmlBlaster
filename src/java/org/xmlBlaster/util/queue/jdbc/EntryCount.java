package org.xmlBlaster.util.queue.jdbc;

class EntryCount {
   long numOfEntries = 0L;
   long numOfPersistentEntries = 0L;
   long numOfBytes = 0L;
   long numOfPersistentBytes = 0L;
   public String toString() {
      return "numOfEntries=" + numOfEntries +
      " numOfPersistentEntries=" + numOfPersistentEntries +
      " numOfBytes=" + numOfBytes +
      " numOfPersistentBytes=" + numOfPersistentBytes;
   }
   
}

