/*------------------------------------------------------------------------------
Name:      Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;


import java.util.*;


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
 * @author ruff@swand.lake.de
 * @see classtest.TimeoutTest
 */
public class Timeout extends Thread
{
   /** Name for logging output */
   private static String ME = "Timeout";
   /** Sorted map */
   private TreeMap map = null;
   /** Start/Stop the Timeout manager thread */
   private boolean running = true;


   /**
    * Helper holding the callback interface an some user data to be 
    * looped through.
    */
   private class Container
   {
     I_Timeout callback;
     Object userData;
     long creation;
     Container(I_Timeout callback, Object userData) {
       this.callback = callback;
       this.userData = userData;
       this.creation = System.currentTimeMillis();
     }
   }


   /**
    * Create a timer thread. 
    */
   public Timeout()
   {
      super("Timeout-Thread");
      map = new TreeMap();
      setDaemon(true);
      start();
   }


   /**
    * Create a timer thread. 
    */
   public Timeout(String name)
   {
      super(name);
      map = new TreeMap();
      setDaemon(true);
      start();
   }


   /**
    * Get number of current used timers. 
    * @return The number of active timers
    */
   public int getSize()
   {
      return map.size();
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
               Timestamp nextWakeup = (Timestamp) map.firstKey();
               long next = nextWakeup.getMillis();
               long current = System.currentTimeMillis();
               delay = next - current;
               if (delay <= 0) {
                  container = (Container) map.remove(nextWakeup);
                  if (false) {
                     long time = System.currentTimeMillis();
                     long diff = time - nextWakeup.getMillis();
                     System.out.println("Timeout occurred, calling listener with real time error of " + diff + " millis");
                  }
               }
            }
            catch (NoSuchElementException e) {
               // The listener map is empty, nothing to do.
            }
         }

         if (container != null) {
            container.callback.timeout(container.userData);
            continue;
         }

         try {
            synchronized (this) {
               wait(delay);
            }
         }
         catch (InterruptedException i) {
            // Wakeing up, and check if there is something to do
         }
      }
   }


   /**
    * Add a listener which gets informed after 'delay' milliseconds.<p />
    * 
    * After the timeout happened, you are not registered any more. If you 
    * want to cycle timeouts, you need to register again.<p />
    *
    * @param      listener 
    *             Your callback handle (you need to implement this interface).
    * @param      delay 
    *             The timeout in milliseconds.
    * @param      userData 
    *             Some arbitrary data you supply, it will be routed back 
    *             to you when the timeout occurs through method 
    *             I_Timeout.timeout().
    * @return     A handle which you can use to unregister with 
    *             removeTimeoutListener().
    */
   public final Timestamp addTimeoutListener(I_Timeout listener, long delay, Object userData)
   {
      Timestamp key = null;
      if (delay < 1) Log.warn(ME, "addTimeoutListener with delay = " + delay);
      int nanoCounter = 0;
      long timeMillis = System.currentTimeMillis();
      synchronized (map) {
         while (true) {
            long endNanos = (timeMillis + delay) * Timestamp.MILLION + nanoCounter;
            key = new Timestamp(endNanos);
            Object obj = map.get(key);
            if (obj == null) {
               map.put(key, new Container(listener, userData));
               break;
            }
            else {
               nanoCounter++; // We loop to avoid two similar keys, this should happen very seldom
               //System.out.println("Looping nanoCounter=" + nanoCounter);
            }
         }
      }
      synchronized (this) {
         notify();
      }
      return key;
   }


   /**
    * Refresh a listener before the timeout happened.<p />
    * 
    * NOTE: The returned timeout handle is different from the original one.<p />
    *
    * NOTE: If you are not shure if the key has elapsed already try this:
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
         Thread.currentThread().dumpStack();
         throw new XmlBlasterException(ME, "The timeout handle is null, no timeout refresh done");
      }
      Timestamp newKey = null;
      synchronized (map) {
         Object obj = map.remove(key);
         if (obj == null) {
            throw new XmlBlasterException(ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done");
         }
         Container container = (Container)obj;
         return addTimeoutListener(container.callback, delay, container.userData);
      }
   }


   /**
    * Remove a listener before the timeout happened.<p />
    * 
    * @param      key
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.
    */
   public final void removeTimeoutListener(Timestamp key)
   {
      synchronized (map) {
         Container container = (Container) map.remove(key);
         if (container != null) {
            container.callback = null;
            container.userData = null;
            container = null;
         }
      }
   }


   /**
    * Is this handle expired?<p />
    * 
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
    * How long to my timeout.<p />
    *
    * @param      key 
    *             The timeout handle you received by a previous addTimeoutListener() call.
    * @return     Milliseconds to timeout, or -1 if not known.
    */
   public final long spanToTimeout(Timestamp key)
   {
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
    * How long am i running.<p />
    *
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
    * Access the end of life span.<p />
    * 
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
    * Reset all pending timeouts.<p />
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
    * Method for testing only.<p />
    *
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.Timeout
    */
   public static void main(String args[]) throws Exception {

      Timeout timeout = new Timeout("TestTimer");
      Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
            public void timeout(Object userData) {
               System.out.println("Timeout happened");
               System.exit(0);
            }
         },
         2000L, null);

      try { Thread.currentThread().sleep(4000); } catch (InterruptedException e) {}
      System.err.println("ERROR: Timeout not occurred.");
      System.exit(1);
   }
}
