/*------------------------------------------------------------------------------
Name:      DispatchWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.def.Constants;
import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
import EDU.oswego.cs.dl.util.concurrent.LinkedQueue;
import EDU.oswego.cs.dl.util.concurrent.ThreadFactory;

/**
 * Pool of threads doing a callback.
 */
public class DispatchWorkerPool //implements I_RunlevelListener
{
   public final String ME = "DispatchWorkerPool";
   private Global glob;
   private LogChannel log;
   private PooledExecutor pool;
   private PropInt threadPrio = new PropInt(Thread.NORM_PRIORITY);
   private PropInt maximumPoolSize = new PropInt(200);
   private PropInt minimumPoolSize = new PropInt(2);
   private PropInt createThreads = new PropInt(minimumPoolSize.getValue());
   private boolean isShutdown = false;
   private final String poolId = "dispatch";


   protected static class DeamonThreadFactory implements ThreadFactory {
      private final String id;
      private final int priority;
      DeamonThreadFactory(String id, int priority) {
         this.id = id;
         this.priority = priority;
      }
      public Thread newThread(Runnable command) {
         Thread t = new Thread(command, "XmlBlaster.DispatchWorkerPool."+id);
         t.setDaemon(true);
         t.setPriority(priority);
         //System.out.println("Created new daemon thread instance for DispatchWorkerPool");
         return t;
      }
   }

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public DispatchWorkerPool(Global glob) {
      this.glob = glob;
      this.log = glob.getLog(this.poolId);
      initialize();
      // Currently not used - on client side there is no RunlevelManager
      //glob.getRunlevelManager().addRunlevelListener(this);
   }

   private synchronized void initialize() {
      this.pool = new PooledExecutor(new LinkedQueue());

      // Example server side:
      // -dispatch/callback/minimumPoolSize 34
      // Example client side:
      // -dispatch/connection/minimumPoolSize 28
      String context = null; // usually 'client/joe'
      String instanceName = (glob.isServerSide()) ? Constants.RELATING_CALLBACK : Constants.RELATING_CLIENT;

      this.threadPrio.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "threadPriority");
      this.pool.setThreadFactory(new DeamonThreadFactory(glob.getId(), this.threadPrio.getValue()));
      
      this.maximumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maximumPoolSize");
      this.minimumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "minimumPoolSize");
      this.createThreads.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "createThreads");
      if (log.TRACE) log.trace(ME, "maximumPoolSize=" + this.maximumPoolSize.getValue() + " minimumPoolSize=" +
                    this.minimumPoolSize.getValue() + " createThreads=" + this.createThreads.getValue());

      this.pool.setMaximumPoolSize(this.maximumPoolSize.getValue());
      this.pool.setMinimumPoolSize(this.minimumPoolSize.getValue());
      this.pool.createThreads(this.createThreads.getValue());
      this.pool.setKeepAliveTime(-1); // Threads live forever
      this.pool.waitWhenBlocked();
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   final public synchronized void execute(java.lang.Runnable command) throws java.lang.InterruptedException {
      if (this.isShutdown) {
         log.trace(ME, "The pool is shudown, ignoring execute()");
         return;
      }
      this.pool.execute(command);
   }

   public String getStatistic() {
      return "Active threads=" + this.pool.getPoolSize() + " of max=" + this.pool.getMaximumPoolSize();
   }

   /**
    * A shut down pool cannot be restarted
    */
   public void shutdownAfterProcessingCurrentlyQueuedTasks() {
      this.pool.shutdownAfterProcessingCurrentlyQueuedTasks();
   }

   public synchronized void shutdown() {
      if (log.CALL) log.call(ME, "shutdown()");
      if (!this.isShutdown) {
         this.isShutdown = true;
         this.pool.shutdownNow();
      }
      //this.pool = null;
      //this.glob = null;
      //this.log = null;
   }

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }
}

