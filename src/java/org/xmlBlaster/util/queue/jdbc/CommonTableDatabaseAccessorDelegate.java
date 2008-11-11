package org.xmlBlaster.util.queue.jdbc;

import java.util.ArrayList;
import java.util.logging.Logger;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.queue.I_Entry;
import org.xmlBlaster.util.queue.I_EntryFactory;
import org.xmlBlaster.util.queue.I_EntryFilter;
import org.xmlBlaster.util.queue.I_Storage;
import org.xmlBlaster.util.queue.StorageId;

public class CommonTableDatabaseAccessorDelegate extends CommonTableDatabaseAccessor {
   private static Logger log = Logger.getLogger(CommonTableDatabaseAccessorDelegate.class.getName());
   private /*final*/ int MAX_RETRIES;
   private /*final*/ int RETRY_SLEEP_MILLIS;
	
   public CommonTableDatabaseAccessorDelegate(JdbcConnectionPool pool,
			I_EntryFactory factory, String managerName, I_Storage storage)
			throws XmlBlasterException {
      super(pool, factory, managerName, storage);
      this.MAX_RETRIES = pool.getProp("maxExceptionRetries", 2);
      if (this.MAX_RETRIES < 1)
    	  this.MAX_RETRIES = 1;
      this.RETRY_SLEEP_MILLIS = pool.getProp("retrySleepMillis", 0);
      if (this.RETRY_SLEEP_MILLIS < 0)
    	  this.RETRY_SLEEP_MILLIS = 0;
      log.info("Using deadlock handler with maxExceptionRetries=" + this.MAX_RETRIES + " retrySleepMillis=" + this.RETRY_SLEEP_MILLIS);

      // See Main.java which checks the configuration!
      //String panicErrorCodes = pool.getGlobal().getProperty().get("xmlBlaster/panicErrorCodes", "");
      // Add us as an I_XmlBlasterExceptionHandler ...
      //if (XmlBlasterException.getExceptionHandler() == null)
      //   XmlBlasterException.setExceptionHandler(this); // see public void newException(XmlBlasterException e);
      // instead of Main.newException()
   }

   /**
    * If we re-throw the exception we change to RESOURCE_DB_UNAVAILABLE
    * as this triggers an immediate shutdown in Main.java newException(XmlBlasterException)
    * @param retryCounter
    * @param e
    * @throws XmlBlasterException
    */
   private void handleException(int retryCounter, XmlBlasterException e) throws XmlBlasterException {
      if (retryCounter >= (MAX_RETRIES-1)) {
         if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
            // Main.java intercepts and does an immediate shutdown
            e.changeErrorCode(ErrorCode.RESOURCE_DB_UNAVAILABLE);
         }
         throw e; // -> immediate shutdown
      }

      if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
         // Only ErrorCode.RESOURCE_DB_UNKNOWN
         e.printStackTrace();
         log.severe("We try again (try #" + (retryCounter+1) + " of " + MAX_RETRIES + "): " + e.toString());
         if (this.RETRY_SLEEP_MILLIS > 0) {
            try {
               Thread.sleep(this.RETRY_SLEEP_MILLIS);
            } catch (InterruptedException e1) {
               e1.printStackTrace();
            }
         }
      }
      else { //ErrorCode.RESOURCE_DB_UNAVAILABLE
         if (e.isErrorCode(ErrorCode.RESOURCE_DB_UNKNOWN)) {
            // Main.java intercepts and does an immediate shutdown
            e.changeErrorCode(ErrorCode.RESOURCE_DB_UNAVAILABLE);
         }
         throw e; // -> immediate shutdown
      }
   }

   /**
2008-02-11 10:37:41.710 SEVERE  97-pool-1-thread-65 RL10 org.xmlBlaster.Main newException: PANIC: Doing immediate shutdown caused by exception: XmlBlasterException errorCode=[resource.db.unknown] serverSideException=true location=[JdbcManagerCommonTable.deleteEntries] message=[#16564M An unknown error with the backend database using JDBC occurred -> http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#resource.db.unknown : com.microsoft.sqlserver.jdbc.SQLServerException: Transaction (Process ID 76) was deadlocked on lock | communication buffer resources with another process and has been chosen as the deadlock victim. Rerun the transaction.] [See URL http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#resource.db.unknown]
2008-02-11 10:37:41.882 SEVERE  97-pool-1-thread-65 RL10 org.xmlBlaster.Main newException: errorCode=resource.db.unknown message=#16564M An unknown error with the backend database using JDBC occurred -> http://www.xmlblaster.org/xmlBlaster/doc/requirements/admin.errorcodes.listing.html#resource.db.unknown : com.microsoft.sqlserver.jdbc.SQLServerException: Transaction (Process ID 76) was deadlocked on lock | communication buffer resources with another process and has been chosen as the deadlock victim. Rerun the transaction.
	at org.xmlBlaster.util.queue.jdbc.JdbcManagerCommonTable.deleteEntriesNoSplit(JdbcManagerCommonTable.java:1673)
	at org.xmlBlaster.util.queue.jdbc.JdbcManagerCommonTable.deleteEntries(JdbcManagerCommonTable.java:1626)
	at org.xmlBlaster.util.queue.jdbc.JdbcQueueCommonTablePlugin.removeRandom(JdbcQueueCommonTablePlugin.java:792)
	at org.xmlBlaster.util.queue.cache.CacheQueueInterceptorPlugin.takeLowest(CacheQueueInterceptorPlugin.java:683)
	at org.xmlBlaster.engine.TopicHandler.publish(TopicHandler.java:708)
	at org.xmlBlaster.engine.RequestBroker.publish(RequestBroker.java:1595)
	at org.xmlBlaster.engine.RequestBroker.publish(RequestBroker.java:1369)
	at org.xmlBlaster.engine.RequestBroker.publish(RequestBroker.java:1357)
	at org.xmlBlaster.engine.XmlBlasterImpl.publishArr(XmlBlasterImpl.java:198)
	at org.xmlBlaster.util.protocol.RequestReplyExecutor.receiveReply(RequestReplyExecutor.java:408)
	at org.xmlBlaster.protocol.socket.HandleClient.handleMessage(HandleClient.java:230)
	at org.xmlBlaster.protocol.socket.HandleClient$1.run(HandleClient.java:388)
	at edu.emory.mathcs.backport.java.util.concurrent.ThreadPoolExecutor$Worker.runTask(ThreadPoolExecutor.java:665)
	at edu.emory.mathcs.backport.java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:690)
	at java.lang.Thread.run(Thread.java:595)
    */
   public boolean[] deleteEntries(String queueName, long[] uniqueIds) throws XmlBlasterException {
      for (int i=0; i<MAX_RETRIES; i++) {
         try {
            return super.deleteEntries(queueName, uniqueIds);
         }
         catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("JdbcManagerCommonTableDelegate.deleteEntries() MAX_RETRIES=" + MAX_RETRIES);
   }
   
   public ArrayList getEntries(StorageId storageId, int numOfEntries, long numOfBytes, I_EntryFilter entryFilter) throws XmlBlasterException {
      for (int i=0; i<MAX_RETRIES; i++) {
          try {
    	     return super.getEntries(storageId, numOfEntries, numOfBytes, entryFilter);
          }
          catch (XmlBlasterException e) {
             handleException(i, e);
          }
       }
       throw new IllegalStateException("JdbcManagerCommonTableDelegate.getEntries() MAX_RETRIES=" + MAX_RETRIES);
   }
   
   public int[] addEntries(String queueName, I_Entry[] entries) throws XmlBlasterException {
      for (int i=0; i<MAX_RETRIES; i++) {
         try {
            return super.addEntries(queueName, entries);
         }
         catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("JdbcManagerCommonTableDelegate.addEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

   public long modifyEntry(String queueName, I_Entry entry, I_Entry oldEntry) throws XmlBlasterException {
      for (int i=0; i<MAX_RETRIES; i++) {
         try {
            return super.modifyEntry(queueName, entry, oldEntry);
         }
         catch (XmlBlasterException e) {
            handleException(i, e);
         }
      }
      throw new IllegalStateException("JdbcManagerCommonTableDelegate.deleteEntries() MAX_RETRIES=" + MAX_RETRIES);
   }

}
