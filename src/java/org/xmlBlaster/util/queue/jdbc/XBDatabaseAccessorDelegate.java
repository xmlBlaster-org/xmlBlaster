package org.xmlBlaster.util.queue.jdbc;

import java.util.List;
import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Storage;

public class XBDatabaseAccessorDelegate extends XBDatabaseAccessor {

   private static Logger log = Logger.getLogger(XBDatabaseAccessorDelegate.class.getName());
   private/* final */int MAX_RETRIES;
   private/* final */int RETRY_SLEEP_MILLIS;

   public XBDatabaseAccessorDelegate(JdbcConnectionPool pool) throws XmlBlasterException {
      super();
      this.MAX_RETRIES = pool.getProp("maxExceptionRetries", 2);
      if (this.MAX_RETRIES < 1)
         this.MAX_RETRIES = 1;
      this.RETRY_SLEEP_MILLIS = pool.getProp("retrySleepMillis", 0);
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
   
   public List/* <I_Entry> */getEntries(XBStore store, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter,
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

   public long modifyEntry(XBStore store, XBMeat entry, XBMeat oldEntry, boolean onlyRefCounters)
         throws XmlBlasterException {
      for (int i = 0; i < MAX_RETRIES; i++) {
         try {
            return super.modifyEntry(store, entry, oldEntry, onlyRefCounters);
         } catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("XBDatabaseAccessorDelegate.deleteEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

}
