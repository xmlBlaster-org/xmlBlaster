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
 *   Timeout timeout = Timeout.getInstance();
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
 *
 * JDK 1.2 or higher only.
 *
 * @author ruff@swand.lake.de
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
      while (running) {
         long delay = 100000; // sleep veeery long
         synchronized (map) {
            try {
               Timestamp nextWakeup = (Timestamp) map.firstKey();
               long next = nextWakeup.getMillis();
               long current = System.currentTimeMillis();
               delay = next - current;
               if (delay <= 0) {
                  Container container = (Container) map.remove(nextWakeup);
                  if (false) {
                     long time = System.currentTimeMillis();
                     long diff = time - nextWakeup.getMillis();
                     System.out.println("Timeout occurred, calling listener with real time error of " + diff + " millis");
                  }
                  container.callback.timeout(container.userData);
                  continue;
               }
            }
            catch (NoSuchElementException e) {
               // The listener map is empty, nothing to do.
            }
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
               System.out.println("Looping nanoCounter=" + nanoCounter);
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
    * @param      key 
    *             The timeout handle you received by a previous 
    *             addTimeoutListener() call.<br />
    *             It is invalid after this call.
    * @param      delay 
    *             The timeout in milliseconds measured from now.
    * @return     A new handle which you can use to unregister with 
    *             removeTimeoutListener()
    * @exception  XmlBlasterException 
    *             if key is invalid
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
     /*
      String ME = "Timeout-Tester";
      Timeout timeout = new Timeout();

      //==== 1. We test the functionality:
      System.out.println("Phase 1: Testing basic functionality ...");

      // Test to remove invalid keys
      timeout.removeTimeoutListener(null);
      timeout.removeTimeoutListener(new Timestamp(12));

      // We have the internal knowledge that the key is the scheduled timeout in millis since 1970
      // so we use it here for testing ...
      final Timestamp[] keyArr = new Timestamp[4];
      class Dummy1 implements I_Timeout {
         private String ME = "Dummy1";
         private int counter = 0;
         public void timeout(Object userData) {
            long time = System.currentTimeMillis();
            long diff = time - keyArr[counter].getMillis();
            if (Math.abs(diff) < 40)
               // Allow 40 millis wrong notification (for code execution etc.) ...
               System.out.println("Timeout occurred for " + userData.toString() + " at " + time + " millis, real time failure=" + diff + " millis.");
            else
               System.err.println("*****ERROR: Wrong timeout occurred for " + userData.toString() + " at " + time + " millis, scheduled was " + keyArr[counter] + " , real time failure=" + diff + " millis.");
            counter++;
         }
      }
      Dummy1 dummy = new Dummy1();
      keyArr[2] = timeout.addTimeoutListener(dummy, 4000L, "timer-4000");

      keyArr[3] = timeout.addTimeoutListener(dummy, 2000L, "timer-5500");
      keyArr[3] = timeout.refreshTimeoutListener(keyArr[3], 5500L);
      long diffT = keyArr[3].getMillis() - System.currentTimeMillis();
      if (Math.abs(5500L - diffT) > 30)
         System.out.println("ERROR: refresh failed");

      keyArr[0] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      keyArr[1] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      long span = 0;
      if ((span = timeout.spanToTimeout(keyArr[2])) < 3000L)
         System.err.println("*****ERROR: This short span to timeout = " + span + " is probably wrong, or you have a very slow computer.");
      else
         System.out.println("Span to life of " + span + " is reasonable");
      Timestamp key = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      timeout.removeTimeoutListener(key);
      try {
         key = timeout.refreshTimeoutListener(key, 1500L);
      }
      catch (XmlBlasterException e) {
         System.out.println("Refresh failed which is OK (it is a test): " + e.reason);
      }
      if (timeout.isExpired(keyArr[2]))
         System.err.println("*****ERROR: Should not be expired");
      else
         System.out.println("Correct, is not expired");
      try {
         Thread.currentThread().sleep(7000L);
      }
      catch (Exception e) {
         System.err.println("*****ERROR: main interrupt: " + e.toString());
      }
      if (!timeout.isExpired(keyArr[2]))
         System.err.println("*****ERROR: Should be expired");
      else
         System.out.println("Correct, is expired");


      //===== 2. We test a big load:
      final int numTimers = 10000;
      System.out.println("Phase 2: Testing " + numTimers + " timeouts ...");
      timeout.shutdown();
      timeout = new Timeout(); // get a new handle
      class Dummy2 implements I_Timeout {
         private String ME = "Dummy2";
         private int counter = 0;
         private long start = 0L;
         public void timeout(Object userData) {
            if (counter == 0) {
               start = System.currentTimeMillis();
            }
            counter++;
            if (counter == numTimers) {
               long diff = System.currentTimeMillis() - start;
               if (diff < 2000L)
                  System.out.println("Success, tested " + numTimers + " timers, all updates came in " + diff + " millis");
               else
                  System.err.println("*****ERROR: Error testing " + numTimers + " timers, all updates needed " + diff + " millis");
            }
         }
      }
      Dummy2 dummy2 = new Dummy2();
      long start = System.currentTimeMillis();
      for (int ii = 0; ii < numTimers; ii++) {
         timeout.addTimeoutListener(dummy2, 4000L, "timer-" + ii);
      }
      if (numTimers != timeout.getSize())
         System.out.println("ERROR: Expected " + numTimers + " instead of " + timeout.getSize() + " active timers");
      System.out.println("Feeding of " + numTimers + " done, " + (long) (1000 * (double) numTimers / (System.currentTimeMillis() - start)) + " adds/sec");
      System.out.println("Waiting in main");
      try {
         Thread.currentThread().sleep(100000L);
      }
      catch (Exception e) {
         System.err.println("*****ERROR:main interrupt: " + e.toString());
      }
      System.err.println("Test OK");
      */
   }
}
