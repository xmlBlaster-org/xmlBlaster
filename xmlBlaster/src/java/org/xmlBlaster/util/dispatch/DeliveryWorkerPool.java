/*------------------------------------------------------------------------------
Name:      DeliveryWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Version:   $Id: DeliveryWorkerPool.java,v 1.5 2003/03/22 12:28:11 laghi Exp $
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.dispatch.DeliveryManager;
//import org.xmlBlaster.engine.runlevel.I_RunlevelListener;
//import org.xmlBlaster.engine.runlevel.RunlevelManager;
import org.jutils.runtime.Sleeper;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;


/**
 * Pool of threads doing a callback.
 */
public class DeliveryWorkerPool //implements I_RunlevelListener
{
   public final String ME = "DeliveryWorkerPool";
   private final Global glob;
   private final LogChannel log;
   private PooledExecutor pool;
   private int maximumPoolSize;
   private int minimumPoolSize;
   private int createThreads;
   private boolean isShutdown = false;

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public DeliveryWorkerPool(Global glob) {
      this.glob = glob;
      this.log = glob.getLog("dispatch");
      initialize();
      // Currently not used - on client side there is no RunlevelManager
      //glob.getRunlevelManager().addRunlevelListener(this);
   }

   private void initialize() {
      if (pool != null && isShutdown == false)
         return;

      this.pool = new PooledExecutor(new LinkedQueue());

      maximumPoolSize = glob.getProperty().get("cb.maximumPoolSize", 200);
      minimumPoolSize = glob.getProperty().get("cb.minimumPoolSize", 10);
      createThreads = glob.getProperty().get("cb.createThreads", minimumPoolSize);

      pool.setMaximumPoolSize(maximumPoolSize);
      pool.setMinimumPoolSize(minimumPoolSize);
      pool.createThreads(createThreads);
      pool.setKeepAliveTime(-1); // Threads live forever
      pool.waitWhenBlocked();
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   public final void execute(DeliveryManager deliveryManager, java.lang.Runnable command) throws XmlBlasterException {
      try {
         //deliveryManager.setDeliveryWorkerIsActive(true); // Done in DeliveryManager already
         pool.execute(command);
      }
      catch (Throwable e) {
         deliveryManager.setDeliveryWorkerIsActive(false);
         if (log.TRACE) log.trace(ME, "Callback failed: " + e.toString());
         throw new XmlBlasterException(ME, "Callback failed: " + e.toString());
      }
   }

   public String getStatistic() {
      return "Active threads=" + pool.getPoolSize() + " of max=" + pool.getMaximumPoolSize();
   }

   /**
    * A shut down pool cannot be restarted
    */
   public void shutdownAfterProcessingCurrentlyQueuedTasks() {
      pool.shutdownAfterProcessingCurrentlyQueuedTasks();
   }

   public synchronized void shutdown() {
      if (log.CALL) log.call(ME, "shutdown()");
      if (!isShutdown) {
         isShutdown = true;
         pool.shutdownNow();
      }
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /*
    * Invoked on run level change, see RunlevelManager.RUNLEVEL_HALTED and RunlevelManager.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      //if (log.CALL) log.call(ME, "Changing from run level=" + from + " to level=" + to + " with force=" + force);
      if (to == from)
         return;

      if (to > from) { // startup
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
         }
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
            if (isShutdown)
               initialize(); // create a new pool
         }
         if (to == RunlevelManager.RUNLEVEL_RUNNING) {
         }
      }
      else if (to < from) { // shutdown
         if (to == RunlevelManager.RUNLEVEL_CLEANUP) {
         }
         if (to == RunlevelManager.RUNLEVEL_STANDBY) {
            shutdownAfterProcessingCurrentlyQueuedTasks();
            isShutdown = true;
         }
         if (to == RunlevelManager.RUNLEVEL_HALTED) {
            shutdown();
         }
      }
   }
    */
}

