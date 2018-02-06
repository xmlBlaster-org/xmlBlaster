/*------------------------------------------------------------------------------
Name:      DispatchWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropInt;
import org.xmlBlaster.util.property.PropLong;

//import EDU.oswego.cs.dl.util.concurrent.PooledExecutor;
//import EDU.oswego.cs.dl.util.concurrent.ThreadFactory;

/**
 * Pool of threads doing a callback.
 */
public class DispatchWorkerPool //implements I_RunlevelListener
{
   public final String ME = "DispatchWorkerPool";
   private Global glob;
   private static Logger log = Logger.getLogger(DispatchWorkerPool.class.getName());
   private ThreadPoolExecutor pool;
   //private PooledExecutor pool;
   private PropInt threadPrio = new PropInt(Thread.NORM_PRIORITY);
   private PropLong threadLifetime = new PropLong(180 * 1000L);
   private PropInt maximumPoolSize = new PropInt(Integer.MAX_VALUE);
   //private PropInt minimumPoolSize = new PropInt(20);
   // 2018-02-06 Marcel Ruff increased from 5 to 20 threads
   private PropInt createThreads;// = new PropInt(5);
   private boolean isShutdown = false;
   private final String poolId = "dispatch";
   // 2017-03-21 Michele Laghi
   private PropLong maxWaitTime = new PropLong(60000L);
   private static int counterPool = 0;

   protected static class DeamonThreadFactory implements ThreadFactory {
      private Global glob;
      private final String id;
      private final int priority;
      private int count; // to have a nice logging
       
      DeamonThreadFactory(Global glob, String id, int priority) {
         this.glob = glob;
         this.id = id;
         this.priority = priority;
      }
      public Thread newThread(Runnable command) {
         String threadName = "";
         synchronized (DispatchWorkerPool.class) {
            String scope = glob.isServerSide() ? "ServerScope" : "ClientGlobal";
            threadName = "XmlBlaster." + scope + ".DispatchWorkerPool." +id + "-pool#" + counterPool + "-thread#" + this.count++;
         }
         log.fine("Created a new thread '" + threadName + "'");
         Thread t = new Thread(command, threadName);
         t.setDaemon(true);
         t.setPriority(priority);
         return t;
      }
   }

   /**
    * @param maxWorkers Maximum allowed callback threads
    */
   public DispatchWorkerPool(Global glob) {
      this.glob = glob;
      if (this.glob.isServerSide()) {
    	  this.createThreads = new PropInt(20);
      }
      else {
    	  this.createThreads = new PropInt(2);
      }
      
      synchronized (this.getClass()) {
         counterPool++;
      }

      initialize();
      // Currently not used - on client side there is no RunlevelManager
      //glob.getRunlevelManager().addRunlevelListener(this);
   }

   private synchronized void initialize() {

      // Example server side:
      // -dispatch/callback/minimumPoolSize 34
      // Example client side:
      // -dispatch/connection/minimumPoolSize 28
      String context = null; // usually 'client/joe'
      String instanceName = (glob.isServerSide()) ? Constants.RELATING_CALLBACK : Constants.RELATING_CLIENT;

      this.threadPrio.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "threadPriority");
      
      this.maximumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maximumPoolSize");
      //this.minimumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "minimumPoolSize");
      if (this.glob.isServerSide())
    	  this.createThreads.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "createThreads");
      else
    	  this.createThreads.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "createThreadsClient");
      this.threadLifetime.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "threadLifetime");
      this.maxWaitTime.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maxWaitTime");
      if (log.isLoggable(Level.FINE)) log.fine("maximumPoolSize=" + this.maximumPoolSize.getValue()/* + " minimumPoolSize=" +
                    this.minimumPoolSize.getValue()*/ + " createThreads=" + this.createThreads.getValue() + " threadLifetime=" + this.threadLifetime + "' ms");

      //if (this.minimumPoolSize.getValue() < 3)
      //   log.warning("The minimumPoolSize of '" + this.minimumPoolSize.getValue() + "' is less than 2: if one single callback blocks it could block all other callbacks");
      ThreadFactory threadFactory = new DeamonThreadFactory(this.glob, glob.getId(), this.threadPrio.getValue());
      // Default: corePoolSize=0, maximumPoolSize=Integer.MAX_VALUE, keepAliveTime=60L, TimeUnit.SECONDS
      // SynchronousQueue} that hands off tasks to threads without otherwise holding them. Here, an attempt to queue a task will fail if no threads are immediately available to run it, so a new thread will be constructed.

      // BlockingQueue<Runnable> blockingQueue = new SynchronousQueue<Runnable>(); 
      BlockingQueue<Runnable> blockingQueue = null;
      if (maxWaitTime.getValue() > 0L) {
    	  // 1 is just queue size, the pool has still createThreads threads
    	  blockingQueue = new BlockingOnOfferQueue<Runnable>(1, maxWaitTime.getValue());
      }
      else
    	  blockingQueue = new SynchronousQueue<Runnable>();

      this.pool = new ThreadPoolExecutor(this.createThreads.getValue(), this.maximumPoolSize.getValue(),
    		  this.threadLifetime.getValue(), TimeUnit.MILLISECONDS,
              blockingQueue,
              threadFactory);

      this.pool.setRejectedExecutionHandler(new RejectedExecutionHandler() {
         // @Override
		public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
			String text = "";
			if (r != null && r instanceof I_DispatchManager) {
				I_DispatchManager dm = (I_DispatchManager)r;
				text = "Internal error, can't start thread for dispatchWorker " + dm.getId();
			}
			else {
				text = "Internal error, can't start thread for unkown dispatchWorker";
			}
			log.severe(text);
			throw new RejectedExecutionException(text);
		}
	});
      //this.pool = Executors.newCachedThreadPool(new DeamonThreadFactory(glob.getId(), this.threadPrio.getValue()));
      
//      this.pool = new PooledExecutor(new LinkedQueue());
//      this.pool.setThreadFactory(new DeamonThreadFactory(glob.getId(), this.threadPrio.getValue()));
//      this.pool.setMaximumPoolSize(this.maximumPoolSize.getValue());
//      this.pool.setMinimumPoolSize(this.minimumPoolSize.getValue());
//      this.pool.createThreads(this.createThreads.getValue());
//      this.pool.setKeepAliveTime(this.threadLifetime.getValue());
//      this.pool.waitWhenBlocked();
   }

   public boolean isShutdown() {
      return this.isShutdown;
   }

   final public synchronized boolean execute(java.lang.Runnable command) throws java.lang.InterruptedException {
      if (this.isShutdown) {
         log.severe("The pool is shudown, ignoring execute()");
         return false;
      }
      try {
         this.pool.execute(command);
      }
      catch (RejectedExecutionException e) {
    	  log.severe("Can't gather new thread " + getStatistic() + ": " + e.toString());
    	  return false;
      }
      catch (Throwable e) {
    	  e.printStackTrace();
    	  log.severe("Can't gather new thread " + getStatistic() + ": " + e.toString());
    	  return false;
      }
      return true;
   }

   public String getStatistic() {
	  //return "None statistic";
	  String prefix = this.glob.isServerSide() ? this.glob.getLogPrefix() : "ClientGlobal";
      return prefix + " Threads activeCount=" + this.pool.getActiveCount() + " poolSize=" + this.pool.getPoolSize() + " of max=" + this.pool.getMaximumPoolSize()
      + " maxWaitTime=" + maxWaitTime.getValue();
   }

   /**
    * A shut down pool cannot be restarted
    */
   public void shutdownAfterProcessingCurrentlyQueuedTasks() {
      //this.pool.shutdownAfterProcessingCurrentlyQueuedTasks();
      this.pool.shutdown();
   }

   public synchronized void shutdown() {
      if (log.isLoggable(Level.FINER)) log.finer("shutdown()");
      if (!this.isShutdown) {
         this.isShutdown = true;
         this.pool.shutdownNow();
      }
      //this.pool = null;
      //this.glob = null;
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

