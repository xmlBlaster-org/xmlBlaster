package org.xmlBlaster.util.queue.jdbc;

import java.util.List;
import java.util.logging.Logger;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_QueueEntry;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.ReturnDataHolder;

public final class XBDatabaseAccessorDelegate extends XBDatabaseAccessor {

   private static Logger log = Logger.getLogger(XBDatabaseAccessorDelegate.class.getName());
   private/* final */int MAX_RETRIES;
   private/* final */int RETRY_SLEEP_MILLIS;

   public XBDatabaseAccessorDelegate() {
      super();
   }

   protected void doInit(I_Info info) throws XmlBlasterException {
      super.doInit(info);
      this.MAX_RETRIES = info.getInt("maxExceptionRetries", 2);
      if (this.MAX_RETRIES < 1)
         this.MAX_RETRIES = 1;
      this.RETRY_SLEEP_MILLIS = info.getInt("retrySleepMillis", 0);
      if (this.RETRY_SLEEP_MILLIS < 0)
         this.RETRY_SLEEP_MILLIS = 0;
      log.info("Using deadlock handler with maxExceptionRetries=" + this.MAX_RETRIES + " retrySleepMillis="
            + this.RETRY_SLEEP_MILLIS);
   }

   /**
    * If we re-throw the exception we change to RESOURCE_DB_UNAVAILABLE as this
    * triggers an immediate shutdown in Main.java
    * newException(XmlBlasterException)
    * 
    * @param retryCounter
    * @param e
    * @throws XmlBlasterException
    */
   private void handleException(int retryCounter, XmlBlasterException e) throws XmlBlasterException {
      if (retryCounter >= (MAX_RETRIES - 1)) {
         if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
            // Main.java intercepts and does an immediate shutdown
            e.changeErrorCode(ErrorCode.RESOURCE_DB_UNAVAILABLE);
         }
         throw e; // -> immediate shutdown
      }

      if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
         // Only ErrorCode.RESOURCE_DB_UNKNOWN
         e.printStackTrace();
         log.severe("We try again (try #" + (retryCounter + 1) + " of " + MAX_RETRIES + "): " + e.toString());
         if (this.RETRY_SLEEP_MILLIS > 0) {
            try {
               Thread.sleep(this.RETRY_SLEEP_MILLIS);
            } catch (InterruptedException e1) {
               e1.printStackTrace();
            }
         }
      } else { // ErrorCode.RESOURCE_DB_UNAVAILABLE
         if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
            // Main.java intercepts and does an immediate shutdown
            e.changeErrorCode(ErrorCode.RESOURCE_DB_UNAVAILABLE);
         }
         throw e; // -> immediate shutdown
      }
   }

   @Override
   public long modifyEntry(XBStore store, XBMeat entry, XBMeat oldEntry, boolean onlyRefCounters)
         throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.modifyEntry(store, entry, oldEntry, onlyRefCounters);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.modifyEntry() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public boolean addEntry(XBStore store, I_Entry entry) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.addEntry(store, entry);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.addEntry() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public int[] addEntries(XBStore store, I_Entry[] entries) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.addEntries(store, entries);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.addEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public int cleanUp(XBStore store) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.cleanUp(store);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.cleanUp() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public int wipeOutDB(boolean doSetupNewTables) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.wipeOutDB(doSetupNewTables);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.wipeOutDB() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public long deleteAllTransient(XBStore store) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.deleteAllTransient(store);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteAllTransient() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public ReturnDataHolder getAndDeleteLowest(XBStore store, int numOfEntries, long numOfBytes, int maxPriority,
         long minUniqueId, boolean leaveOne, boolean doDelete, I_Storage storage) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getAndDeleteLowest(store, numOfEntries, numOfBytes, maxPriority, minUniqueId, leaveOne,
                  doDelete, storage);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getAndDeleteLowest() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public void deleteStore(long storeId) throws XmlBlasterException {
      XmlBlasterException ex = null;
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            super.deleteStore(storeId);
            return;
         } catch (XmlBlasterException e) {
            ex = e;
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteStore() MAX_RETRIES=" + MAX_RETRIES + " "
            + (ex == null ? "" : ex.getMessage()));
   }

   @Override
   public long deleteEntries(XBStore store, XBRef[] refs, XBMeat[] meats) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.deleteEntries(store, refs, meats);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   public int deleteEntries(XBStore store, long refId, long meatId) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.deleteEntry(store, refId, meatId);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public long deleteFirstRefs(XBStore store, long numOfEntries) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.deleteFirstRefs(store, numOfEntries);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteFirstRefs() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getEntriesByPriority(XBStore store, int numOfEntries, long numOfBytes, int minPrio, int maxPrio)
         throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntriesByPriority(store, numOfEntries, numOfBytes, minPrio, maxPrio);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntriesByPriority() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getEntriesBySamePriority(XBStore store, int numOfEntries, long numOfBytes)
         throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntriesBySamePriority(store, numOfEntries, numOfBytes);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntriesBySamePriority() MAX_RETRIES="
            + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getRefEntries(XBStore store, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter,
         I_Storage storage, I_QueueEntry firstEntryExlusive) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getRefEntries(store, numOfEntries, numOfBytes, entryFilter, storage, firstEntryExlusive);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getRefEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getEntries(XBStore store, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter,
         boolean isRef, I_Storage storage) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntries(store, numOfEntries, numOfBytes, entryFilter, isRef, storage);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   public XBEntry[] getEntriesLike(String queueNamePattern, String flag, int numOfEntries, long numOfBytes,
         I_EntryFilter entryFilter) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntriesLike(queueNamePattern, flag, numOfEntries, numOfBytes, entryFilter);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntriesLike() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getEntriesWithLimit(XBStore store, I_Entry limitEntry, I_Storage storage)
         throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntriesWithLimit(store, limitEntry, storage);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntriesWithLimit() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public long removeEntriesWithLimit(XBStore store, XBRef limitEntry, boolean inclusive) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.removeEntriesWithLimit(store, limitEntry, inclusive);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.removeEntriesWithLimit() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public List<I_Entry> getEntries(XBStore store, XBRef[] refs, XBMeat[] meats) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getEntries(store, refs, meats);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   @Override
   public EntryCount getNumOfAll(XBStore store) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.getNumOfAll(store);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.getNumOfAll() MAX_RETRIES=" + MAX_RETRIES + " "
            + store.toString());
   }

   @Override
   public long clearQueue(XBStore store) throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.cleanUp(store);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.clearQueue() MAX_RETRIES=" + MAX_RETRIES);
   }

}
