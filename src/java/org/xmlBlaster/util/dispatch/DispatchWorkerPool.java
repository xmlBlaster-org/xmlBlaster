/*------------------------------------------------------------------------------
Name:      DispatchWorkerPool.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Pool of threads doing a callback.
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.dispatch;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
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

/**
 * Pool of threads doing a callback on server side or invocations on client side. 
 * 
 * Remember that for mobile devices with small bandwith a callback thread can be in use for long time for each client callback.
 * 
 * Default behaviour is
 * <pre>
 * 1.  "maximumPoolSize"=10'000 threads can be used.
 * 
 * 2. The pool keeps up to "createThreads"=20 threads for performance reasons ("createThreads.client"=1 on client side).
 * 
 * 3. After "threadLifetime"=180sec an idle thread is removed from pool
 * 
 * 4a In case all maximumPoolSize threads are in use, new tasks wait up to "maxWaitTime"=60'000 milli seconds on a thread.
 *    Unfortunately this is not thread safe programmed and not reliable, leading to unexpected RejectedExecutionException.
 *    If none gets available a RejectedExecutionException is thrown
 *
 * 4b In case all maximumPoolSize threads are in use and "maxWaitTime"=0 milli seconds the executes blocks forever until a Thread is available
 * <pre>
 */
public class DispatchWorkerPool //implements I_RunlevelListener
{
   public final String ME = "DispatchWorkerPool";
   private Global glob;
   private static Logger log = Logger.getLogger(DispatchWorkerPool.class.getName());
   // ExecutorService
   private ThreadPoolExecutor pool;
   //private PooledExecutor pool;
   private PropInt threadPrio = new PropInt(Thread.NORM_PRIORITY);
   /**
    * keepAliveTime when the number of threads is greater than the core, this is
	* the maximum time that excess idle threads will wait for new tasks before
	* terminating.
    */
   private PropLong threadIdleLifetime = new PropLong(180 * 1000L);
   /**
    * On exhaust and "maxWaitTime" == 0 or elapsed: java.util.concurrent.RejectedExecutionException
    *
	* 2018-02-17 Marcel Ruff Integer.MAX_VALUE changed to 10000
    */
   private PropInt maximumPoolSize = new PropInt(10000);
   /**
    * corePoolSize the number of threads to keep in the pool, even if they are
	* idle, unless {@code allowCoreThreadTimeOut} is set
	* 
	* 2018-02-06 Marcel Ruff increased from 5 to 20 threads on server side
    */
   private PropInt createThreads;
   private boolean isShutdown = false;
   private final String poolId = "dispatch";
   /**
    * How long wait until a thread is available from pool.
    * 
    * 2017-03-21 Michele Laghi, 60000 millis is default (1 minute)
    */
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

   public DispatchWorkerPool(Global glob) {
      this.glob = glob;
      if (this.glob.isServerSide()) {
    	  this.createThreads = new PropInt(20);
      }
      else {
    	  this.createThreads = new PropInt(1);
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
      
      //this.minimumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "minimumPoolSize");
      if (this.glob.isServerSide()) {
          this.maximumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maximumPoolSize");
    	  this.createThreads.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "createThreads");
      }
      else {
          this.maximumPoolSize.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maximumPoolSize.client");
    	  this.createThreads.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "createThreads.client");
      }
      
      if (this.maximumPoolSize.getValue() < this.createThreads.getValue()) {
    	  int t = this.createThreads.getValue();
    	  this.createThreads.setValue(this.maximumPoolSize.getValue());
    	  log.warning("Corrected createThreads="+t+" to maximumPoolSize=" + this.maximumPoolSize.getValue());
      }
      
      this.threadIdleLifetime.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "threadLifetime");
      
      // millis
      this.maxWaitTime.setFromEnv(glob, glob.getStrippedId(), context, this.poolId, instanceName, "maxWaitTime");
      if (log.isLoggable(Level.FINE)) log.fine("maximumPoolSize=" + this.maximumPoolSize.getValue()/* + " minimumPoolSize=" +
                    this.minimumPoolSize.getValue()*/ + " createThreads=" + this.createThreads.getValue() + " threadLifetime=" + this.threadIdleLifetime + "' ms");

      //if (this.minimumPoolSize.getValue() < 3)
      //   log.warning("The minimumPoolSize of '" + this.minimumPoolSize.getValue() + "' is less than 2: if one single callback blocks it could block all other callbacks");
      ThreadFactory threadFactory = new DeamonThreadFactory(this.glob, glob.getId(), this.threadPrio.getValue());
      // Default: corePoolSize=0, maximumPoolSize=Integer.MAX_VALUE, keepAliveTime=60L, TimeUnit.SECONDS
      // SynchronousQueue} that hands off tasks to threads without otherwise holding them. Here, an attempt to queue a task will fail if no threads are immediately available to run it, so a new thread will be constructed.

      // BlockingQueue<Runnable> blockingQueue = new SynchronousQueue<Runnable>(); 
      BlockingQueue<Runnable> blockingQueue = null;
      if (maxWaitTime.getValue() > 0L) {
    	  // 1 is just queue size, the pool has still createThreads threads
    	  // BUGGY 2018-02-17: Should block given time on thread exhaust before RejectedExecutionException
    	  // but was blocking to execute more then createThreads and slowing down server. 
    	  //blockingQueue = new BlockingOnOfferQueue<Runnable>(1, maxWaitTime.getValue());
    	  //blockingQueue = new BlockingOnOfferQueue<Runnable>(this.maximumPoolSize.getValue(), maxWaitTime.getValue());
    	  blockingQueue = new SynchronousQueue<Runnable>(); // Throws RejectedExecutionException on no new thread available
      }
      else {
    	  // blockingQueue = new SynchronousQueue<Runnable>(); // Throws RejectedExecutionException on no new thread available
    	  // 2018-02-17 Marcel changed to:
    	  blockingQueue = new LinkedBlockingQueue<Runnable>(); // blocks forever until thread is free
      }
      
      log.info("Creating " + (glob.isServerSide() ? "server side" : "client side") + " pool#" + counterPool + ", maxWaitTime=" + this.maxWaitTime.getValue() + " milli, maxPoolSize=" + this.maximumPoolSize.getValue() + ", threadPriority=" + this.threadPrio.getValue());

      // ExecutorService pool = Executors.newFixedThreadPool(this.createThreads.getValue());
      this.pool = new ThreadPoolExecutor(this.createThreads.getValue(), this.maximumPoolSize.getValue(),
    		  this.threadIdleLifetime.getValue(), TimeUnit.MILLISECONDS,
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
			else if (r != null) {
				text = "Internal error, can't start thread for " + r.toString();
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
	  if (command == null) {
         log.severe("The given command to execute is null, ignored");
         return false;
	  }
      if (this.isShutdown) {
         log.severe("The pool is shudown, ignoring execute()");
         return false;
      }
      
      // Caution: This code is not reliable thread save as getActiveCount() may change until pool.execute() is called!
      if (maxWaitTime.getValue() > 0L) {
    	  final long waitMillis = maxWaitTime.getValue();
    	  final int sleepRetryMillis = 10;
    	  int count = (int)(waitMillis / sleepRetryMillis);
    	  for (int i=0; i<count; i++) {
    		  // approximate number of threads that are actively executing tasks.
        	  int active = this.pool.getActiveCount();
        	  int max = this.pool.getMaximumPoolSize();
    		  if (active >= max-1) {
    			  if (i == 0)
    				  log.warning("Waiting up to " + waitMillis + " millis for a thread to get available to execute command " + command.toString() + "...: " + getStatistic());
    			  Thread.sleep(sleepRetryMillis);
    			  continue;
    		  }
    		  if (i > 0)
    			  log.info("Success, a thread is available after waiting " + (i*sleepRetryMillis) + " millis for command " + command.toString() + ": " + getStatistic());
    		  break;
    	  }
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
      return prefix + " Threads activeCount=" + this.pool.getActiveCount() + " poolSize=" + this.pool.getPoolSize() +
    		  " maximumPoolSize=" + this.maximumPoolSize.getValue() +
    		  " queuedTasks=" + this.pool.getQueue().size() +
              " maxWaitMillis=" + maxWaitTime.getValue();
   }

   public int getActiveCount() {
	  return this.pool.getActiveCount();
   }

   public String toString() {
	   return getStatistic();
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

