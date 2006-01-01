/*------------------------------------------------------------------------------
Name:      Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import java.lang.ref.WeakReference;
import java.util.TreeMap;
import java.util.NoSuchElementException;
import org.xmlBlaster.util.def.ErrorCode;

/**
 * Allows you be called back after a given delay.
 * <p />
 * Note that this class should be called Timer, but with JDK 1.3 there
 * will be a java.util.Timer.
 * <p />
 * There is a single background thread that is used to execute the I_Timeout.timeout() callback.
 * Timer callbacks should complete quickly. If a timeout() takes excessive time to complete, it "hogs" the timer's
 * task execution thread. This can, in turn, delay the execution of subsequent tasks, which may "bunch up" and execute in
 * rapid succession when (and if) the offending task finally completes.
 * <p />
 * This singleton is thread-safe.
 * <p />
 * This class does not offer real-time guarantees, but usually notifies you within ~ 20 milliseconds
 * of the scheduled time.
 * <p />
 * Adding or removing a timer is good performing, also when huge amounts of timers (> 1000) are used.<br />
 * Feeding of 10000: 10362 adds/sec and all updates came in 942 millis (600MHz Linux PC with Sun JDK 1.3.1)
 * * <p />
 * TODO: Use a thread pool to dispatch the timeout callbacks.
 * <p />
 * Example:<br />
 * <pre>
 * public class MyClass implements I_Timeout {
 *   ...
 *   Timeout timeout = new Timeout("TestTimer");
 *   Timestamp timeoutHandle = timeout.addTimeoutListener(this, 4000L, "myTimeout");
 *   ...
 *   public void timeout(Object userData) {
 *      // userData contains String "myTimeout"
 *      System.out.println("Timeout happened");
 *      ...
 *      // If you want to activate the timer again:
 *      timeoutHandle = timeout.addTimeoutListener(this, 4000L, "myTimeout");
 *   }
 *   ...
 *   // if you want to refresh the timer:
 *   timeoutHandle = timeout.refreshTimeoutListener(timeoutHandle, 1500L);
 *   ...
 * }
 * </pre>
 * Or a short form:
 * <pre>
 *  Timeout timeout = new Timeout("TestTimer");
 *  Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
 *        public void timeout(Object userData) {
 *           System.out.println("Timeout happened");
 *           System.exit(0);
 *        }
 *     },
 *     2000L, null);
 * </pre>
 *
 * JDK 1.2 or higher only.
 *
 * @author xmlBlaster@marcelruff.info
 * @see org.xmlBlaster.test.classtest.TimeoutTest
 */
public class Timeout extends Thread
{
   /** Name for logging output */
   private static String ME = "Timeout";
   /** Sorted map */
   private TreeMap map = null;
   /** Start/Stop the Timeout manager thread */
   private boolean running = true;
   /** On creation wait until thread started */
   private boolean ready = false;
   /** Switch on debugging output */
   private final boolean debug = false;
   /** Hold only weak reference on callback object? */
   private final boolean useWeakReference;
   /** To avoid sync */
   private boolean mapHasNewEntry;

   /**
    * Create a timer thread with a strong reference on the callback objects. 
    */
   public Timeout()
   {
      this("Timeout-Thread", false);
   }


   /**
    * Create a timer thread with a strong reference on the callback objects. 
    * @param name The name of the thread
    */
   public Timeout(String name)
   {
      this(name, false);
   }

   /**
    * @param name The name of the thread
    * @param useWeakReference If true the reference on your I_Timeout implementation is
    *        only weak referenced and may be garbage collected even that we hold a weak reference.
    */
   public Timeout(String name, boolean useWeakReference)
   {
      super(name);
      this.useWeakReference = useWeakReference;
      this.map = new TreeMap();
      setDaemon(true);
      start();
      while (!ready) { // We block until our timer thread is ready
         try { Thread.sleep(1); } catch (InterruptedException e) {}
      }
   }

   /**
    * Get number of current used timers. 
    * @return The number of active timers
    */
   public int getSize()
   {
      synchronized (map) {
         return map.size();
      }
   }

   /**
    * Starts the Timeout manager thread.
    */
   public void run()
   {
      Container container;
      while (running) {
         long delay = 100000; // sleep veeery long
         container = null;
         synchronized (map) {
            try {
               Timestamp nextWakeup = (Timestamp) map.firstKey(); // throws exception if empty
               long next = nextWakeup.getMillis();
               long current = System.currentTimeMillis();
               delay = next - current;
               if (delay <= 0) {
                  container = (Container) map.remove(nextWakeup);
                  if (debug) {
                     long time = System.currentTimeMillis();
                     long diff = time - nextWakeup.getMillis();
                     System.out.println("Timeout occurred, calling listener with real time error of " + diff + " millis");
                  }
               }
            }
            catch (NoSuchElementException e) {
               if (debug)
                  System.out.println("The listener map is empty, nothing to do.");
            }
            this.mapHasNewEntry = false;
         }

         if (container != null) {
            I_Timeout callback = container.getCallback();
            //System.out.println("useWeakReference=" + useWeakReference + " callback=" + callback);
            if (callback != null) {
               callback.timeout(container.getUserData());
            }
            continue;
         }

         if (delay > 0) {
            try {
               synchronized (this) {
                  ready = true; // only needed on thread creation / startup
                  if (!this.mapHasNewEntry) { // If in the sync gap (~5 lines higher) a new timer was registered we need to loop again and recalculate the delay
                     wait(delay);
                  }
               }
            }
            catch (InterruptedException i) {
               // Wakeing up, and check if there is something to do
            }
         }
      }
   }


   /**
    * Add a listener which gets informed after 'delay' milliseconds. 
    * <p />
    * After the timeout happened, you are not registered any more. If you 
    * want to cycle timeouts, you need to register again.<p />
    *
    * @param      listener 
    *             Your callback handle (you need to implement this interface).
    * @param      delay 
    *             The timeout in milliseconds.
    *             You can pass 0L and the Timeout thread will fire immediately,
    *             this can be useful to dispatch a task to the timeoutlistener
    * @param      userData 
    *             Some arbitrary data you supply, it will be routed back 
    *             to you when the timeout occurs through method 
    *             I_Timeout.timeout().
    * @return     A handle which you can use to unregister with 
    *             removeTimeoutListener().
    */
   public final Timestamp addTimeoutListener(I_Timeout listener, long delay, Object userData)
   {
      if (listener == null) {
         throw new IllegalArgumentException(ME+": addTimeoutListener() with listener=null");
      }
      Timestamp key = null;
      //if (delay < 1) System.out.println(ME + ": addTimeoutListener with delay = " + delay);
      int nanoCounter = 0;
      long timeMillis = System.currentTimeMillis();
      synchronized (map) {
         while (true) {
            long endNanos = (timeMillis + delay) * Timestamp.MILLION + nanoCounter;
            key = new Timestamp(endNanos);
            Object obj = map.get(key);
            if (obj == null) {
               map.put(key, new Container(this.useWeakReference, listener, userData));
               break;
            }
            else {
               nanoCounter++; // We loop to avoid two similar keys, this should happen very seldom
               //System.out.println("Looping nanoCounter=" + nanoCounter);
            }
         }
         this.mapHasNewEntry = true;
      }
      synchronized (this) {
         notify();
      }
      return key;
   }


   /**
    * Refresh a listener before the timeout happened. 
    * <p />
    * NOTE: The returned timeout handle is different from the original one.<p />
    *
    * NOTE: If you are not sure if the key has elapsed already try this:
    * <pre>
    *  timeout.removeTimeoutListener(timeoutHandle);
    *  timeoutHandle = timeout.addTimeoutListener(this, "1000L", "UserData");
    * </pre>
    * @param      key 
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.<br />
    *             It is invalid after this call.
    * @param      delay 
    *             The timeout in milliseconds measured from now.
    * @return     A new handle which you can use to unregister with 
    *             removeTimeoutListener()
    * @exception  XmlBlasterException 
    *             if key is null or unknown or invalid because timer elapsed already
    */
   public final Timestamp refreshTimeoutListener(Timestamp key, long delay) 
      throws XmlBlasterException
   {
      if (key == null) {
         Thread.dumpStack();
         throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_NULLPOINTER,
            ME + "addTimeoutListener",
            "The timeout handle is null, no timeout refresh done");
      }
      Object obj;
      synchronized (map) {
         obj = map.remove(key);
      }

      if (obj == null) {
         String pos = Global.getStackTraceAsString();
         throw new XmlBlasterException(Global.instance(), ErrorCode.RESOURCE_UNAVAILABLE, ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done: " + pos);
      }
      Container container = (Container)obj;
      I_Timeout callback = container.getCallback();
      if (callback == null) {
         if (this.useWeakReference)
            throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_UNKNOWN, ME, "The weak callback reference for timeout handle '" + key + "' is garbage collected, no timeout refresh done");
         else
            throw new XmlBlasterException(Global.instance(), ErrorCode.INTERNAL_UNKNOWN, ME, "Internal error for timeout handle '" + key + "', no timeout refresh done");
      }
      return addTimeoutListener(callback, delay, container.getUserData());
   }

   /**
    * Checks if key is null -> addTimeoutListener else refreshTimeoutListener() in a thread save way. 
    * <br />
    * Note however that your passed key is different from the returned key and you need
    * to synchronize this call to avoid having a stale key (two threads enter this method
    * the same time, the key gets invalid by the first thread and the second passed a stale key
    * as the first thread has not yet returned to update 'key')
    */
   public final Timestamp addOrRefreshTimeoutListener(I_Timeout listener, long delay, Object userData, Timestamp key) 
      throws XmlBlasterException
   {
      if (key == null) {
         return addTimeoutListener(listener, delay, userData);
      }
      else {
         try {
            return refreshTimeoutListener(key, delay);
         }
         catch (XmlBlasterException e) {
            if (ErrorCode.RESOURCE_UNAVAILABLE == e.getErrorCode()) {
               return addTimeoutListener(listener, delay, userData);
            }
            throw e;
         }
      }
   }

   /**
    * Remove a listener before the timeout happened. 
    * <p />
    * @param      key
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.
    */
   public final void removeTimeoutListener(Timestamp key)
   {
      if (key == null) return;
      synchronized (map) {
         Container container = (Container) map.remove(key);
         if (container != null) {
            container.reset();
            container = null;
         }
      }
   }


   /**
    * Is this handle expired? 
    * <p />
    * @param      key
    *             The timeout handle you received by a previous addTimeoutListener() call<br />
    * @return     true/false
    */
   public final boolean isExpired(Timestamp key)
   {
      synchronized (map) {
         return map.get(key) == null;
      }
   }


   /**
    * How long to my timeout. 
    * <p />
    * @param      key 
    *             The timeout handle you received by a previous addTimeoutListener() call.
    * @return     Milliseconds to timeout, or -1 if not known.
    */
   public final long spanToTimeout(Timestamp key)
   {
      if (key == null) {
         return -1;
      }
      synchronized (map) {
         Container container = (Container) map.get(key);
         if (container == null) {
            return -1;
         }
         // We know that the key contains the timeout date
         return getTimeout(key) - System.currentTimeMillis();
      }
   }


   /**
    * How long am i running. 
    * <p />
    * @param      key 
    *             The timeout handle you received by a previous addTimeoutListener() call.
    * @return     Milliseconds since creation, or -1 if not known.
    */
   public final long elapsed(Timestamp key) {
      synchronized (map) {
         Container container = (Container) map.get(key);
         if (container == null) {
            return -1;
         }
         return System.currentTimeMillis() - container.creation;
      }
   }


   /**
    * Access the end of life span. 
    * <p />
    * @param      key 
    *             The timeout handle you received by a previous addTimeoutListener() call.
    * @return     Time in milliseconds since midnight, January 1, 1970 UTC 
    *             or -1 if not known.
    */
   public final long getTimeout(Timestamp key) {
      if (key == null)
         return -1;
      return key.getMillis();
   }


   /**
    * Reset all pending timeouts. 
    */
   public final void removeAll() {
      synchronized (map) {
         map.clear();
      }
   }


   /**
    * Reset and stop the Timeout manager thread. 
    */
   public void shutdown() {
      removeAll();
      running = false;
      synchronized (this) {
         notify();
      }
      //destroy();
   }


   /**
    * Method for testing only. 
    * <p />
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.Timeout
    */
   public static void main(String args[]) throws Exception {
      {
         Timeout t = new Timeout();
         System.out.println("Timeout constructor done, sleeping 10 sec");
         try { Thread.sleep(10000); } catch (InterruptedException e) {}
      }
      //testWeakReference();
      //testStrongReference();
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
         },
         2000L, null);
      try { Thread.sleep(4000L); } catch (InterruptedException e) {}
      System.err.println("ERROR: Timeout not occurred.");
      System.exit(1);
   }

   /** Test a weak reference */
   static void testWeakReference() {
      Timeout timeout = new Timeout("TestTimer", true);
      System.out.println("Timer created and ready.");

      {
         WeakObj weakObj = new WeakObj();
         Object anotherRef = new Object();
         Timestamp timeoutHandle = timeout.addTimeoutListener(weakObj, 4000L, weakObj); //anotherRef);
         weakObj = null;
         //anotherRef = null;
      }

      System.gc();
      System.out.println("Called gc()"); // NOTE: Without "weakObj = null" and "System.gc()" the test fails
      try { Thread.sleep(8000L); } catch (InterruptedException e) {}
      System.out.println("SUCCESS: No timeout happened");
      System.exit(0);
   }
}

   /** Test a weak reference */
   final class WeakObj implements I_Timeout {
      public void timeout(Object userData) {
         System.err.println("ERROR: Timeout invoked, weak object was not garbage collected.");
         System.exit(1);
      }
      public void finalize() {
         System.out.println("WeakObj is garbage collected");
      }
   }


   /**
    * Helper holding the callback interface an some user data to be 
    * looped through.
    */
   final class Container
   {
      private final boolean useWeakReference;
      private Object callback;
      private Object userData;
      final long creation;
      
      /** @param callback The handle to callback a client (is checked already to be not null) */
      Container(boolean useWeakReference, I_Timeout callback, Object userData) {
         this.useWeakReference = useWeakReference;
         if (this.useWeakReference) {
            this.callback = new WeakReference(callback);
            if (userData != null) 
               this.userData = new WeakReference(userData);
         }
         else {
            this.callback = callback;
            this.userData = userData;
         }
         this.creation = System.currentTimeMillis();
      }

      /** @return The callback handle can be null for weak references */
      I_Timeout getCallback() {
         if (this.useWeakReference) {
            WeakReference weak = (WeakReference)this.callback;
            return (I_Timeout)weak.get();
         }
         else {
            return (I_Timeout)this.callback;
         }
      }
      /** @return The userData, can be null for weak references */
      Object getUserData() {
         if (this.userData == null) {
            return null;
         }
         if (this.useWeakReference) {
            WeakReference weak = (WeakReference)this.userData;
            return weak.get();
         }
         else {
            return this.userData;
         }
      }

      void reset() {
         if (this.callback != null && useWeakReference) {
            ((WeakReference)this.callback).clear();
         }
         this.callback = null;

         if (this.userData != null && useWeakReference) {
            ((WeakReference)this.userData).clear();
         }
         this.userData = null;
      }
   }


