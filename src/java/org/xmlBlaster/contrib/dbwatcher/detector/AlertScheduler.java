/*------------------------------------------------------------------------------
Name:      AlertScheduler.java
Project:   org.xmlBlasterProject:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.contrib.dbwatcher.detector;

import java.util.logging.Logger;
import java.util.logging.Level;


import java.util.Timer;
import java.util.TimerTask;

import org.xmlBlaster.contrib.I_Info;

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
 * @author Marcel Ruff
 */
public class AlertScheduler extends TimerTask implements I_AlertProducer
{
   private static Logger log = Logger.getLogger(AlertScheduler.class.getName());
   private I_ChangeDetector changeDetector;
   private Timer timer;
   private long period;

   /**
    * Default constructor, you need to call {@link #init} thereafter.  
    */
   public AlertScheduler() {
      // void
   }
   /**
    * Create a scheduler, calls {@link #init}.  
    * @param info The configuration environment
    * @param changeDetector The class to be alerted then and again
    */
   public AlertScheduler(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      init(info, changeDetector);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.convert.I_DataConverter#init(I_Info)
    */
   public void init(I_Info info, I_ChangeDetector changeDetector) throws Exception {
      if (changeDetector == null) throw new IllegalArgumentException("changeDetector is null, can't schedule anything.");
      log.fine("created");
      this.period = info.getLong("alertScheduler.pollInterval", 2000L);
      this.changeDetector = changeDetector;
   }
   
   /**
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#startProducing
    */
   public void startProducing() {
      if (this.period <= 0L) {
         log.warning("AlertScheduler is switched off with alertScheduler.pollInterval=" + this.period);
         return;
      }
      log.info("Starting timer with pollInterval=" + this.period + " ...");
      boolean isDaemon = true;
      this.timer = new Timer(/*"AlertScheduler", since JDK 1.5*/isDaemon);
      this.timer.schedule(this, 0L, this.period);
   }

   /**
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#stopProducing
    */
   public void stopProducing() throws Exception {
      log.info("stopProducing");
      shutdown();
   }
   
   /**
    * Used by scheduler thread internally. 
    */
   public void run() {
      // Thread.currentThread().setName("DbWatcher-Scheduler");
      if (log.isLoggable(Level.FINE)) log.fine("Checking now Database again. pollInterval=" + this.period + " ...");

      try {
         this.changeDetector.checkAgain(null);
      }
      catch (Throwable e) {
         log.severe("Don't know how to handle error: " + e.toString()); 
      }
   }

   /**
    * Stop the scheduler. 
    * @see org.xmlBlaster.contrib.dbwatcher.detector.I_AlertProducer#shutdown
    */
   public void shutdown() throws Exception {
      log.fine("Shutdown");
      if (this.period <= 0L) {
         return; 
      }
      cancel(); // TimerTask
      this.timer = null;
   }
}
