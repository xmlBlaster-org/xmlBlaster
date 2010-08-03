/*------------------------------------------------------------------------------
 Name:      Timeout.java
 Project:   xmlBlaster.org
 Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
 Comment:   Allows you be called back after a given delay.
 ------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.util.NoSuchElementException;
import java.util.TreeMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.xmlBlaster.util.def.ErrorCode;

/**
 * Allows you be called back after a given delay.
 * <p />
 * Note that this class should be called Timer, but with JDK 1.3 there will be a
 * java.util.Timer.
 * <p />
 * There is pool of threads used to execute the
 * I_Timeout.timeout() callback. Timer callbacks should complete quickly to not exhaust the pool.
 * <p />
 * This singleton is thread-safe.
 * <p />
 * This class does not offer real-time guarantees, but usually notifies you
 * within ~ 20 milliseconds of the scheduled time.
 * <p />
 * Adding or removing a timer is good performing, also when huge amounts of
 * timers (> 1000) are used.<br />
 * Feeding of 10000: 10362 adds/sec and all updates came in 942 millis (600MHz
 * Linux PC with Sun JDK 1.3.1) *
 * <p />
 * <p />
 * Example:<br />
 * 
 * <pre>
 *  public class MyClass implements I_Timeout {
 *    ...
 *    TimeoutPooled timeout = new TimeoutPooled(&quot;TestTimer&quot;);
 *    Timestamp timeoutHandle = timeout.addTimeoutListener(this, 4000L, &quot;myTimeout&quot;);
 *    ...
 *    public void timeout(Object userData) {
 *       // userData contains String &quot;myTimeout&quot;
 *       System.out.println(&quot;Timeout happened&quot;);
 *       ...
 *       // If you want to activate the timer again:
 *       timeoutHandle = timeout.addTimeoutListener(this, 4000L, &quot;myTimeout&quot;);
 *    }
 *    ...
 *    // if you want to refresh the timer:
 *    timeoutHandle = timeout.refreshTimeoutListener(timeoutHandle, 1500L);
 *    ...
 *  }
 * </pre>
 * 
 * Or a short form:
 * 
 * <pre>
 * TimeoutPooled timeout = new TimeoutPooled(&quot;TestTimer&quot;);
 * 
 * Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
 *    public void timeout(Object userData) {
 *       System.out.println(&quot;Timeout happened&quot;);
 *       System.exit(0);
 *    }
 * }, 2000L, null);
 * </pre>
 * 
 * JDK 1.2 or higher only.
 * <p>
 *  -logging/org.xmlBlaster.util.TimeoutPooled FINER
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.TimeoutTest
 */
public class TimeoutPooled extends Thread implements I_TimeoutManager {
   private static Logger log = Logger.getLogger(TimeoutPooled.class.getName());

   /** Name for logging output */
   private static String ME = "Timeout";

   /** Sorted map */
   private TreeMap<Timestamp, Container> map = null;

   /** Start/Stop the Timeout manager thread */
   private boolean running = true;

   /** On creation wait until thread started */
   private boolean ready = false;

   /** Hold only weak reference on callback object? */
   private final boolean useWeakReference;

   /** To avoid sync */
   private boolean mapHasNewEntry;
   
   private final ThreadPoolExecutor threadPool;

   /**
    * Create a timer thread with a strong reference on the callback objects.
    */
   public TimeoutPooled() {
      this("TimeoutPooled-Thread", false);
   }

   /**
    * Create a timer thread with a strong reference on the callback objects.
    * 
    * @param name
    *           The name of the thread
    */
   public TimeoutPooled(String name) {
      this(name, false);
   }

   /**
    * @param name
    *           The name of the thread
    * @param useWeakReference
    *           If true the reference on your I_Timeout implementation is only
    *           weak referenced and may be garbage collected even that we hold a
    *           weak reference.
    */
   public TimeoutPooled(String name, boolean useWeakReference) {
      this(name, useWeakReference, null);
   }
   public TimeoutPooled(String name, boolean useWeakReference, ThreadPoolExecutor threadPool) {
      super(name);
      if (threadPool == null) {
         int corePoolSize = 1; // On XmlBlasterAccess client side only one needed, on server side it can easily grow
         int maxPoolSize = 100;
         long keepAliveTime = 60;
//         corePoolSize - the number of threads to keep in the pool, even if they are idle.
//         maximumPoolSize - the maximum number of threads to allow in the pool.
//         keepAliveTime - when the number of threads is greater than the core, this is the maximum time that excess idle threads will wait for new tasks before terminating.
//         unit - the time unit for the keepAliveTime argument.
//         workQueue - the queue to use for holding tasks before they are executed. This queue will hold only the Runnable tasks submitted by the execute method. 
         final SynchronousQueue<Runnable> queue = new SynchronousQueue<Runnable>();
         this.threadPool = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
               keepAliveTime, TimeUnit.SECONDS, queue);
         //ExecutorService service = Executors.newCachedThreadPool();
         //service.execute(command)
         log.info("Created TimeoutPooled corePoolSize=" + this.threadPool.getCorePoolSize() + " maximumPoolSize=" + this.threadPool.getMaximumPoolSize() + " keepAliveSec=" + this.threadPool.getKeepAliveTime(TimeUnit.SECONDS));
      }
      else {
         this.threadPool = threadPool;
         log.info("Created TimeoutPooled corePoolSize=" + this.threadPool.getCorePoolSize() + " maximumPoolSize=" + this.threadPool.getMaximumPoolSize() + " keepAliveSec=" + this.threadPool.getKeepAliveTime(TimeUnit.SECONDS));
      }
      this.useWeakReference = useWeakReference;
      this.map = new TreeMap<Timestamp, Container>();
      setDaemon(true);
      start();
      while (!ready) { // We block until our timer thread is ready
         try {
            Thread.sleep(1);
         } catch (InterruptedException e) {
         }
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#getSize()
    */
   public int getSize() {
      synchronized (map) {
         return map.size();
      }
   }
   
   public String toString() {
      return "TimeoutPooled pending=" + getSize();
   }
   
   public String dumpStatus() {
      StringBuilder buf = new StringBuilder(256);
      Container[] arr = getContainers();
      buf.append("TimeoutPooled pending=").append(getSize());
      for (Container container: arr) {
         buf.append("\n");
         buf.append("callback=").append(container.getCallback().toString()).append(": userData=").append(container.getUserData());
      }
      buf.append("\nactiveCount=").append(this.threadPool.getActiveCount()).append(" completedTaskCount=").append(this.threadPool.getCompletedTaskCount()).append(" maximumPoolSize=").append(this.threadPool.getMaximumPoolSize());
      buf.append("\nshutdown=").append(this.threadPool.isShutdown()).append(" terminated=").append(this.threadPool.isTerminated()).append(" terminating=").append(this.threadPool.isTerminating());
      return buf.toString();
   }

   public Container[] getContainers() {
      synchronized (map) {
         return map.values().toArray(new Container[map.size()]);
      }
   }

   /**
    * Starts the Timeout manager thread.
    */
   public void run() {
      Container container;
      if (log.isLoggable(Level.FINE))
         log.fine("Timer main thread is ready");
      while (running) {
         long delay = 100000; // sleep veeery long
         container = null;
         synchronized (map) {
            try {
               Timestamp nextWakeup = map.firstKey(); // throws exception if empty
               long next = nextWakeup.getMillis();
               long current = System.currentTimeMillis();
               delay = next - current;
               if (delay <= 0) {
                  container = map.remove(nextWakeup);
                  if (log.isLoggable(Level.FINE)) {
                     long time = System.currentTimeMillis();
                     long diff = time - nextWakeup.getMillis();
                     log.fine("Timeout occurred, calling listener " + (container==null?"null":container.toString()) + " with real time error of "
                                 + diff + " millis");
                  }
               }
            } catch (NoSuchElementException e) {
               if (log.isLoggable(Level.FINEST)) {
                  log.finest("The listener map is empty, nothing to do.");
               }
            }
            this.mapHasNewEntry = false;
         } // sync

         if (container != null) {
            final I_Timeout callback = container.getCallback();
            // System.out.println("useWeakReference=" + useWeakReference + "
            // callback=" + callback);
            if (callback != null) {
               final Object userData = container.getUserData();
               try {
                  if (log.isLoggable(Level.FINER))
                     log.finer("Executing " + callback.toString() + " now via pool: " + dumpStatus());
                  threadPool.execute(new Runnable(){
                     public void run() {
                        try {
                           if (log.isLoggable(Level.FINER))
                              log.finer("Timeout occurred, calling listener now in pooled thread");
                           callback.timeout(userData);
                           if (log.isLoggable(Level.FINER))
                              log.finer("Timeout occurred, calling listener in pooled thread done");
                        }
                        catch (Throwable e) {
                           log.severe("Unexpected exception: " + e.toString());
                           e.printStackTrace();
                        }
                     };
                  });
                  if (log.isLoggable(Level.FINER))
                     log.finer("Poll execute to create new thread has returned");
               }
               catch (RejectedExecutionException e) {
                  log.severe("Thread exhaust " + dumpStatus() + ": " + e.toString());
               }
               catch (Throwable e) {
                  e.printStackTrace();
                  log.severe("Thread exhaust " + dumpStatus() + ": " + e.toString());
               }
            }
            continue;
         }

         if (delay > 0) {
            try {
               synchronized (this) {
                  ready = true; // only needed on thread creation / startup
                  if (!this.mapHasNewEntry) { // If in the sync gap (~5 lines
                                                // higher) a new timer was
                                                // registered we need to loop
                                                // again and recalculate the
                                                // delay
                     wait(delay);
                  }
               }
            } catch (InterruptedException i) {
               // Wakeing up, and check if there is something to do
            }
         }
      }
      if (log.isLoggable(Level.FINE))
         log.fine("Timer main thread dies");
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#addTimeoutListener(org.xmlBlaster.util.I_Timeout, long, java.lang.Object)
    */
   public final Timestamp addTimeoutListener(I_Timeout listener, long delay,
         Object userData) {
      if (listener == null) {
         throw new IllegalArgumentException(ME
               + ": addTimeoutListener() with listener=null");
      }
      Timestamp key = null;
      // if (delay < 1) System.out.println(ME + ": addTimeoutListener with delay
      // = " + delay);
      int nanoCounter = 0;
      long timeMillis = System.currentTimeMillis();
      synchronized (map) {
         while (true) {
            long endNanos = (timeMillis + delay) * Timestamp.MILLION
                  + nanoCounter;
            key = new Timestamp(endNanos);
            if (log.isLoggable(Level.FINE))
               log.fine("addTimeoutListener for " + listener.toString() + " delayMillis=" + delay + " key=" + key);
            Object obj = map.get(key);
            if (obj == null) {
               map.put(key, new Container(this.useWeakReference, listener,
                     userData));
               break;
            } else {
               nanoCounter++; // We loop to avoid two similar keys, this should
                              // happen very seldom
               log.info("Looping nanoCounter=" + nanoCounter);
            }
         }
         this.mapHasNewEntry = true;
      }
      synchronized (this) {
         notify();
      }
      return key;
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#refreshTimeoutListener(org.xmlBlaster.util.Timestamp, long)
    */
   public final Timestamp refreshTimeoutListener(Timestamp key, long delay)
         throws XmlBlasterException {
      if (key == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(Global.instance(),
               ErrorCode.INTERNAL_NULLPOINTER, ME + "addTimeoutListener",
               "The timeout handle is null, no timeout refresh done");
      }
      Object obj;
      synchronized (map) {
         obj = map.remove(key);
      }

      if (obj == null) {
         String pos = Global.getStackTraceAsString(null);
         throw new XmlBlasterException(Global.instance(),
               ErrorCode.RESOURCE_UNAVAILABLE, ME, "The timeout handle '" + key
                     + "' is unknown, no timeout refresh done: " + pos);
      }
      Container container = (Container) obj;
      I_Timeout callback = container.getCallback();
      if (callback == null) {
         if (this.useWeakReference)
            throw new XmlBlasterException(Global.instance(),
                  ErrorCode.INTERNAL_UNKNOWN, ME,
                  "The weak callback reference for timeout handle '" + key
                        + "' is garbage collected, no timeout refresh done");
         else
            throw new XmlBlasterException(Global.instance(),
                  ErrorCode.INTERNAL_UNKNOWN, ME,
                  "Internal error for timeout handle '" + key
                        + "', no timeout refresh done");
      }
      return addTimeoutListener(callback, delay, container.getUserData());
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#addOrRefreshTimeoutListener(org.xmlBlaster.util.I_Timeout, long, java.lang.Object, org.xmlBlaster.util.Timestamp)
    */
   public final Timestamp addOrRefreshTimeoutListener(I_Timeout listener,
         long delay, Object userData, Timestamp key) throws XmlBlasterException {
      if (key == null) {
         return addTimeoutListener(listener, delay, userData);
      } else {
         try {
            return refreshTimeoutListener(key, delay);
         } catch (XmlBlasterException e) {
            if (ErrorCode.RESOURCE_UNAVAILABLE == e.getErrorCode()) {
               return addTimeoutListener(listener, delay, userData);
            }
            throw e;
         }
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#removeTimeoutListener(org.xmlBlaster.util.Timestamp)
    */
   public final void removeTimeoutListener(Timestamp key) {
      if (key == null)
         return;
      synchronized (map) {
         Container container = map.remove(key);
         if (container != null) {
            if (log.isLoggable(Level.FINE))
               log.fine("removeTimeoutListener key=" + key + " container=" + container.toString());
            container.reset();
            container = null;
         }
         else {
            if (log.isLoggable(Level.FINE))
               log.fine("removeTimeoutListener key=" + key + ", container is not existing");
         }
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#isExpired(org.xmlBlaster.util.Timestamp)
    */
   public final boolean isExpired(Timestamp key) {
      synchronized (map) {
         return map.get(key) == null;
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#spanToTimeout(org.xmlBlaster.util.Timestamp)
    */
   public final long spanToTimeout(Timestamp key) {
      if (key == null) {
         return -1;
      }
      synchronized (map) {
         Container container = map.get(key);
         if (container == null) {
            return -1;
         }
         // We know that the key contains the timeout date
         return getTimeout(key) - System.currentTimeMillis();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#elapsed(org.xmlBlaster.util.Timestamp)
    */
   public final long elapsed(Timestamp key) {
      synchronized (map) {
         Container container = map.get(key);
         if (container == null) {
            return -1;
         }
         return System.currentTimeMillis() - container.creation;
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#getTimeout(org.xmlBlaster.util.Timestamp)
    */
   public final long getTimeout(Timestamp key) {
      if (key == null)
         return -1;
      return key.getMillis();
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#removeAll()
    */
   public final void removeAll() {
      synchronized (map) {
         map.clear();
      }
   }

   /* (non-Javadoc)
    * @see org.xmlBlaster.util.I_TimeoutManager#shutdown()
    */
   public void shutdown() {
      if (log.isLoggable(Level.FINE))
         log.fine("shutdown size=" + getSize());
      removeAll();
      threadPool.shutdown();
      running = false;
      synchronized (this) {
         notify();
      }
      // destroy();
   }

   /**
    * Method for testing only.
    * <p />
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.TimeoutPooled
    */
   public static void main(String args[]) throws Exception {
      Timeout t = new Timeout();
      System.out.println("Timeout constructor done, sleeping 10 sec "
            + t.toString());
      try {
         Thread.sleep(10000);
      } catch (InterruptedException e) {
      }
      System.out.println(t.dumpStatus());
   }

   /** Eample for the standard case */
   static void testStrongReference() {
      Timeout timeout = new Timeout("TestTimer");
      System.out.println("Timer created and ready.");
      Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
         public void timeout(Object userData) {
            System.out.println("Timeout happened");
            System.exit(0);
         }
      }, 2000L, null);
      try {
         Thread.sleep(4000L);
      } catch (InterruptedException e) {
      }
      System.err.println("ERROR: Timeout not occurred "
            + timeoutHandle.toString());
      System.exit(1);
   }

   /** Test a weak reference */
   static void testWeakReference() {
      Timeout timeout = new Timeout("TestTimer", true);
      System.out.println("Timer created and ready.");

      {
         WeakObjPooled weakObj = new WeakObjPooled();
         /* Timestamp timeoutHandle = */timeout.addTimeoutListener(weakObj,
               4000L, weakObj);
         weakObj = null;
      }

      System.gc();
      System.out.println("Called gc()"); // NOTE: Without "weakObj = null" and
                                          // "System.gc()" the test fails
      try {
         Thread.sleep(8000L);
      } catch (InterruptedException e) {
      }
      System.out.println("SUCCESS: No timeout happened");
      System.exit(0);
   }
}

/** Test a weak reference */
final class WeakObjPooled implements I_Timeout {
   public void timeout(Object userData) {
      System.err
            .println("ERROR: Timeout invoked, weak object was not garbage collected.");
      System.exit(1);
   }

   //public void finalize() {
   //   super.finalize();
   //   System.out.println("WeakObjPooled is garbage collected");
   //}
}
