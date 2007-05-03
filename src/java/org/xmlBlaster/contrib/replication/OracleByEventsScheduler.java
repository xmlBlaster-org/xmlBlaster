/*------------------------------------------------------------------------------
Name:      OracleByEventsScheduler.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Types;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;
import org.xmlBlaster.contrib.replication.impl.SpecificDefault;

/**
 * This scheduler wakes up in intervals and triggers a database check. 
 * <p> 
 * Supported configuration:
 * </p>
 * <ul>
 *   <li><tt>alertScheduler.pollInterval</tt> The polling interval in milliseconds.
 *       Setting it to 0 switches the timer off.
 *   </li>
 * </ul>
 *
 */
public class OracleByEventsScheduler implements I_AlertProducer {
   
   private class SchedulerRunner extends Thread {
      /* The minimum amount of ms to sleep after an exception (to avoid looping). Normally it will be 'alertScheduler.pollInterval' ms */
      private final static long MIN_SLEEP_DELAY = 1000L;
      /* The maximum amount of ms to sleep after an exception (to avoid looping). Normally it will be 'alertScheduler.pollInterval' ms  */
      private final static long MAX_SLEEP_DELAY = 60000L;
      private boolean forceShutdown;
      private long pollIntervall;
      private I_ChangeDetector changeDetector;
      private I_Info info;
      private String event;
      
      private final void sleepToAvoidLooping() throws InterruptedException {
         if (this.pollIntervall < MIN_SLEEP_DELAY)
            Thread.sleep(MIN_SLEEP_DELAY);
         else {
            if (this.pollIntervall > MAX_SLEEP_DELAY)
               Thread.sleep(MAX_SLEEP_DELAY);
            else
               Thread.sleep(this.pollIntervall);
         }
      }
      
      SchedulerRunner(I_Info info, I_ChangeDetector changeDetector) {
         super("OracleByEventsScheduler");
         setDaemon(true);
         this.pollIntervall = info.getLong("alertScheduler.pollInterval", 30000L);
         this.changeDetector = changeDetector;
         this.info = info;
         this.event = SpecificDefault.getReplPrefix(info) + "ITEMS";
         if (this.pollIntervall <= 0L) {
            log.warning("OracleByEventsScheduler is switched off with alertScheduler.pollInterval=" + this.pollIntervall);
         }
         log.info("Starting timer with pollInterval=" + this.pollIntervall + " ...");
      }
      
      public void doShutdown() {
         this.forceShutdown = true;
      }
      
      public void run() {
         
         I_DbPool pool = null;
         long count = 0L;
         try {
            pool = DbWatcher.getDbPool(this.info);
            // OracleByEventsScheduler.registerEvent(pool, this.event);
            while (!this.forceShutdown) {
               Connection conn = null;
               try {
                  conn = pool.reserve();
                  conn.setAutoCommit(true);
                  if (log.isLoggable(Level.FINE)) 
                     log.fine("Checking now Database again. pollInterval=" + this.pollIntervall + " ...");
                  try {
                     OracleByEventsScheduler.registerEvent(conn, this.event);
                     this.changeDetector.checkAgain(null);
                     log.fine("scheduler: before blocking " + count);
                     OracleByEventsScheduler.waitForEvent(conn, this.event, this.pollIntervall);
                     log.fine("scheduler: after blocking " + count);
                  }
                  catch (Throwable e) {
                     log.severe("Don't know how to handle error: " + e.toString()); 
                     this.sleepToAvoidLooping();
                  }
                  count++;
                  if (count == Long.MAX_VALUE)
                     count = 0L;
               }
               catch (Throwable ex) {
                  log.severe("An exception occured when waiting for oracle scheduler: " + ex.getMessage());
                  ex.printStackTrace();
                  conn = SpecificDefault.removeFromPool(conn, SpecificDefault.ROLLBACK_NO, pool);
                  this.sleepToAvoidLooping();
               }
               finally {
                  conn = SpecificDefault.releaseIntoPool(conn, SpecificDefault.COMMIT_NO, pool);
               }
            }
            // OracleByEventsScheduler.unregisterEvent(pool, this.event);
         }
         catch (Throwable ex) {
            log.severe("An exception occured in the running thread of the scheduler. It will be halter. " + ex.getMessage());
            ex.printStackTrace();
         }
         finally {
            if (pool != null) {
               try {
                  pool.shutdown();
               }
               catch (Exception ex) {
                  log.severe("Exception occured when shutting down the scheduler");
                  ex.printStackTrace();
               }
               pool = null;
            }
         }
      }
   }
   
   
   private static Logger log = Logger.getLogger(OracleByEventsScheduler.class.getName());
   private I_ChangeDetector changeDetector;
   private SchedulerRunner runner;
   private I_Info info;
   
   /**
    * Default constructor, you need to call {@link #init} thereafter.  
    */
   public OracleByEventsScheduler() {
   }

   /**
    * Create a scheduler, calls {@link #init}.  
    * @param info The configuration environment
    * @param changeDetector The class to be alerted then and again
    */
   public OracleByEventsScheduler(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      init(info, changeDetector);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#init(I_Info)
    */
   public void init(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      if (changeDetector == null) 
         throw new IllegalArgumentException("changeDetector is null, can't schedule anything.");
      log.fine("created");
      this.info = info;
      this.changeDetector = changeDetector;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#startProducing
    */
   public synchronized void startProducing() {
      if (this.runner != null) {
         log.warning("A Scheduler is already running");
         return;
      }
      this.runner = new SchedulerRunner(this.info, this.changeDetector);
      this.runner.start();
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#stopProducing
    */
   public void stopProducing() throws Exception {
      log.info("stopProducing");
      shutdown();
   }
   
   /**
    * Stop the scheduler. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#shutdown
    */
   public synchronized void shutdown() throws Exception {
      log.fine("Shutdown");
      if (this.runner != null) {
         this.runner.doShutdown();
         this.runner = null;
      }
   }

   
   
   /**
    * This method does not return exceptions (also catches Throwable).
    * @param conn The database connection to use.
    * @param event the name of the even on which to wait
    * @param timeout the maximum time to wait in ms
    */
   public static void waitForEvent(Connection conn, String event, long timeout) throws Exception {
      CallableStatement st = null;
      try {
         String sql = "{call dbms_alert.waitone(?,?,?,?)}";
         st = conn.prepareCall(sql);
         st.setString(1, event);
         st.setLong(4, timeout / 1000L);
         st.registerOutParameter(2, Types.VARCHAR);
         st.registerOutParameter(3, Types.INTEGER);
         st.executeQuery();
      }
      finally {
         if (st != null)
            st.close();
      }
   }

   public static void registerEvent(Connection conn, String event) throws Exception {
      registerUnregisterForEvents(conn, event, true);
   }
   
   public static void unregisterEvent(Connection conn, String event) throws Exception {
      registerUnregisterForEvents(conn, event, false);
   }

   /**
    * This method does not return exceptions (also catches Throwable).
    * @param conn the database connection to use.
    * @param event the name of the even on which to wait
    * @param doRegister if true it will register, otherwise unregister.
    */
   private static void registerUnregisterForEvents(Connection conn, String event, boolean doRegister) throws Exception {
      CallableStatement st = null;
      String sql = null;
      if (doRegister)
         sql = "{call dbms_alert.register(?)}";
      else
         sql = "{call dbms_alert.remove(?)}";
      try {
         st = conn.prepareCall(sql);
         st.setString(1, event);
         st.executeQuery();
      }
      finally {
         if (st != null)
            st.close();
      }
   }
   
}
