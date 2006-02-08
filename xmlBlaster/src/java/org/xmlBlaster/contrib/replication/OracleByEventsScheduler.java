/*------------------------------------------------------------------------------
Name:      OracleByEventsScheduler.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.replication;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.contrib.I_Info;
import org.xmlBlaster.contrib.db.I_DbPool;
import org.xmlBlaster.contrib.dbwatcher.DbWatcher;
import org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer;
import org.xmlBlaster.contrib.dbwatcher.detector.I_ChangeDetector;

/**
 * This scheduler waiks up in intervals and triggers a database check. 
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
      
      private boolean forceShutdown;
      private long period;
      private I_ChangeDetector changeDetector;
      private I_Info info;
      private String event;
      
      SchedulerRunner(I_Info info, I_ChangeDetector changeDetector) {
         super("OracleByEventsScheduler");
         setDaemon(true);
         this.period = info.getLong("alertScheduler.pollInterval", 30000L);
         this.changeDetector = changeDetector;
         this.info = info;
         this.event = info.get("replication.prefix", "REPL_") + "ITEMS";
         if (this.period <= 0L) {
            log.warning("OracleByEventsScheduler is switched off with alertScheduler.pollInterval=" + this.period);
         }
         log.info("Starting timer with pollInterval=" + this.period + " ...");
      }
      
      public void doShutdown() {
         this.forceShutdown = true;
      }
      
      public void run() {
         
         I_DbPool pool = null;
         try {
            pool = DbWatcher.getDbPool(this.info);
            OracleByEventsScheduler.registerEvent(pool, this.event);
            
            while (!this.forceShutdown) {
               if (log.isLoggable(Level.FINE)) 
                  log.fine("Checking now Database again. pollInterval=" + this.period + " ...");
               try {
                  OracleByEventsScheduler.waitForEvent(pool, this.event, this.period);
                  this.changeDetector.checkAgain(null);
               }
               catch (Throwable e) {
                  log.severe("Don't know how to handle error: " + e.toString()); 
               }
            }
            OracleByEventsScheduler.registerEvent(pool, this.event);
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
    * @param pool the pool of database connections to use.
    * @param event the name of the even on which to wait
    * @param timeout the maximum time to wait in ms
    * @return true if there was an event, false if the return is caused by the timeout or an exception.
    */
   public static boolean waitForEvent(I_DbPool pool, String event, long timeout) {
      Connection conn = null;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
         CallableStatement st = null;
         try {
            String sql = "{call dbms_alert.waitone(?,?,?,?)}";
            st = conn.prepareCall(sql);
            st.setString(1, event);
            st.setLong(4, timeout / 1000L);
            st.registerOutParameter(2, Types.VARCHAR);
            st.registerOutParameter(3, Types.INTEGER);
            ResultSet rs = st.executeQuery();
            long ret = rs.getLong(3);
            return ret == 0;
         }
         finally {
            if (st != null)
               st.close();
         }
      }
      catch (Throwable ex) {
         try {
            pool.erase(conn);
         }
         catch (Throwable e) {
            ex.printStackTrace();
         }
         conn = null;
         return false;
      }
      finally {
         if (conn != null) {
            try {
               pool.release(conn);
               conn = null;
            }
            catch (Throwable ex) {
               log.severe("An exception occured when giving back the connection to its pool");
               ex.printStackTrace();
            }
         }
      }
   }

   public static void registerEvent(I_DbPool pool, String event) throws Exception {
      registerUnregisterForEvents(pool, event, true);
   }
   
   public static void unregisterEvent(I_DbPool pool, String event) throws Exception {
      registerUnregisterForEvents(pool, event, false);
   }

   /**
    * This method does not return exceptions (also catches Throwable).
    * @param pool the pool of database connections to use.
    * @param event the name of the even on which to wait
    * @param doRegister if true it will register, otherwise unregister.
    */
   private static void registerUnregisterForEvents(I_DbPool pool, String event, boolean doRegister) throws Exception {
      Connection conn = null;
      try {
         conn = pool.reserve();
         conn.setAutoCommit(true);
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
      catch (Exception ex) {
         pool.erase(conn);
         conn = null;
         throw ex;
      }
      finally {
         if (conn != null) {
            pool.release(conn);
            conn = null;
         }
      }
   }
   
}
