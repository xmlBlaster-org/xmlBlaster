/*------------------------------------------------------------------------------
Name:      Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Allows you be called back after a given delay.
Version:   $Id: Timeout.java,v 1.4 2000/05/27 22:32:12 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.util;

import org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException;
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
 * Adding or removing a timer is good performing, also when huge amounts of timers (> 1000) are used.
 * <p />
 * TODO: Use a thread pool to dispatch the timeout callbacks.
 * <p />
 * Example:<br />
 * <pre>
 * public class MyClass implements I_Timeout {
 *   ...
 *   Timeout timeout = Timeout.getInstance();
 *   Long timeoutHandle = timeout.addTimeoutListener(this, 4000L, "myTimeout");
 *   ...
 *   public void timeout(Object userData) {
 *      System.out.println("Timeout happened");
 *   }
 *   ...
 * }
 * </pre>
 */
public class Timeout extends Thread
{
   /** Name for logging output */
   private static String ME = "Timeout";
   /** The singleton handle */
   private static Timeout theTimeout = null;
   /** Sorted map */
   private TreeMap map = null;
   /** Start/Stop the Timeout manager thread */
   private boolean running = true;
   /** To protect the singleton */
   private static final java.lang.Object SYNCHRONIZER = new java.lang.Object();

   /**
    * Access to Timeout singleton
    */
   public static Timeout getInstance()
   {
      if (theTimeout == null) { // avoid 'expensive' synchronized
         synchronized (SYNCHRONIZER) {
            if (theTimeout == null) {
               theTimeout = new Timeout();
               theTimeout.start();
            }
         }
      }
      return theTimeout;
   }


   /**
    * private Constructor for Singleton Pattern
    */
   private Timeout()
   {
      super("Timeout-Thread");
      if (Log.TRACE) Log.trace(ME, "Constructing Timeout singleton ...");
      map = new TreeMap();
   }


   /**
    * Starts the Timeout manager thread.
    */
   public void run()
   {
      Log.info(ME, "Starting Timeout thread ...");
      while (running) {
         long delay = 100000; // sleep veeery long
         // if (Log.TRACE) Log.trace(ME, "Looking if there is some timeout listener to notify");
         synchronized(map) {
            try {
               Long nextWakeup = (Long)map.firstKey();
               long next = nextWakeup.longValue();
               Date date = new Date();
               long current = date.getTime();
               delay = next - current;
               if (delay <= 0) {
                  Container container = (Container)map.remove(nextWakeup);
                  if (Log.TRACE) {
                     Date tmp = new Date();
                     long time = tmp.getTime();
                     long diff = time - nextWakeup.longValue();
                     Log.trace(ME, "Timeout occurred, calling listener with real time error of " + diff + " millis");
                  }
                  container.callback.timeout(container.userData);
                  continue;
               }
            }
            catch (NoSuchElementException e) {
               // if (Log.TRACE) Log.trace(ME, "The listener map is empty, nothing to do.");
            }
         }

         try {
            synchronized(theTimeout) { theTimeout.wait(delay); }
         } catch(InterruptedException i) {
            // if (Log.TRACE) Log.trace(ME, "Wakeing up, and check if there is something to do");
         }
      }
   }


   /**
    * Add a listener which gets informed after 'delay' milliseconds.
    * <p />
    * @param listener Your callback handle (you need to implement this interface)
    * @param delay The timeout in milliseconds
    * @param userData Some arbitrary data you supply, it will be routed back to you when the timeout occurs through method I_Timeout.timeout()
    * @return A handle which you can use to unregister with removeTimeoutListener()
    */
   public final Long addTimeoutListener(I_Timeout listener, long delay, Object userData)
   {
      if (Log.CALLS) Log.calls(ME, "Entering addTimeoutListener(" + delay + ") ...");
      Long key = null;
      synchronized(map) {
         while (true) {
            Date date = new Date();
            key = new Long(date.getTime() + delay);
            Object obj = map.get(key);
            if (obj == null) {
               map.put(key, new Container(listener, userData));
               break;
            }
            else {
               // We loop to avoid two similar keys, this should happen very seldom
               // As we don't guarantee real time, we don't care to be 1 milli inaccurate.
               // If we don't like this delay, we would need to use a multimap.
               if (Log.TRACE) Log.trace(ME, "Looping for a free slot (for at most one millisecond) ...");
            }
         }
      }
      // if (Log.TRACE) Log.trace(ME, "Added addTimeoutListener(" + delay + ") with key=" + key);
      synchronized(theTimeout) { theTimeout.notify(); }
      return key;
   }


   /**
    * Refresh a listener before the timeout happened.
    * <p />
    * NOTE: The returned timeout handle is different from the original one.
    *
    * @param key The timeout handle you received by a previous addTimeoutListener() call<br />
    *            It is invalid after this call.
    * @param delay The timeout in milliseconds measured from now.
    * @return A new handle which you can use to unregister with removeTimeoutListener()
    * @exception XmlBlasterException if key is invalid
    */
   public final Long refreshTimeoutListener(Long key, long delay) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering refreshTimeoutListener(" + key + ") ...");
      Long newKey = null;
      synchronized(map) {
         Container container = (Container)map.remove(key);
         if (container == null) {
            Log.warning(ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done");
            throw new XmlBlasterException(ME, "The timeout handle '" + key + "' is unknown, no timeout refresh done");
         }
         while (true) {
            Date date = new Date();
            newKey = new Long(date.getTime() + delay);
            Object obj = map.get(newKey);
            if (obj == null) {
               map.put(newKey, container);
               break;
            } 
            // We loop to avoid two similar keys, this should happen very seldom
         }
      }
      if (Log.TRACE) Log.trace(ME, "refreshTimeoutListener(" + delay + ") with newKey=" + newKey);
      synchronized(theTimeout) { theTimeout.notify(); }
      return newKey;
   }


   /**
    * Remove a listener before the timeout happened.
    * <p />
    * @param key The timeout handle you received by a previous addTimeoutListener() call
    */
   public final void removeTimeoutListener(Long key)
   {
      if (Log.CALLS) Log.calls(ME, "Entering removeTimeoutListener(" + key + ") ...");
      synchronized(map) {
         Container container = (Container)map.remove(key);
         if (container != null) {
            container.callback = null;
            container.userData = null;
            container = null;
         }
      }
   }


   /**
    * Reset all pending timeouts.
    * <p />
    */
   public final void removeAll()
   {
      if (Log.CALLS) Log.calls(ME, "Entering removeAll() ...");
      synchronized(map) {
         map.clear();
      }
   }


   /**
    * Reset this singleton, stop the Timeout manager thread.
    */
   public final void destroy()
   {
      removeAll();
      running = false;
      synchronized(theTimeout) { theTimeout.notify(); }
      synchronized (SYNCHRONIZER) {
         theTimeout = null;
      }
   }


   /**
    * Helper holding the callback interface an some user data to be looped through.
    */
   private class Container
   {
      I_Timeout callback;
      Object userData;
      Container(I_Timeout callback, Object userData) {
         this.callback = callback;
         this.userData = userData;
      }
   }


   /**
    * Only for testing.
    * <p />
    * Invoke: java -Djava.compiler= org.xmlBlaster.util.Timeout
    */
   public static void main(String args[]) throws Exception
   {
      String ME = "Timeout-Tester";
      Log.setLogLevel(args); // initialize log level and xmlBlaster.property file
      Timeout timeout = Timeout.getInstance();

      //==== 1. We test the functionality:
      Log.info(ME, "Phase 1: Testing basic functionality ...");

      // Test to remove invalid keys
      timeout.removeTimeoutListener(null);
      timeout.removeTimeoutListener(new Long(12));

      // We have the internal knowledge that the key is the scheduled timeout in millis since 1970
      // so we use it here for testing ...
      final Long[] keyArr =  new Long[4];

      class Dummy1 implements I_Timeout
      {
         private String ME = "Dummy1";
         private int counter = 0;
         public void timeout(Object userData)
         {
            Date date = new Date();
            long time = date.getTime();
            long diff = time - keyArr[counter].longValue();
            if (Math.abs(diff) < 40) // Allow 40 millis wrong notification (for code execution etc.) ...
               Log.info(ME, "Timeout occurred for " + userData.toString() + " at " + time + " millis, real time failure=" + diff + " millis.");
            else
               Log.error(ME, "Wrong timeout occurred for " + userData.toString() + " at " + time + " millis, scheduled was " + keyArr[counter] + " , real time failure=" + diff + " millis.");
            counter++;
         }
      }

      Dummy1 dummy = new Dummy1();
      keyArr[2] = timeout.addTimeoutListener(dummy, 4000L, "timer-4000");
      keyArr[3] = timeout.addTimeoutListener(dummy, 5000L, "timer-5000");
      keyArr[3] = timeout.refreshTimeoutListener(keyArr[3], 5500L);
      keyArr[0] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      keyArr[1] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      
      Long key = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      timeout.removeTimeoutListener(key);

      try {
         timeout.refreshTimeoutListener(key, 1500L);
      }
      catch (XmlBlasterException e) {
         Log.info(ME, "Refresh failed which is OK (it is a test): " + e.reason);
      }

      try { Thread.currentThread().sleep(7000L); } catch (Exception e) { Log.panic(ME, "main interrupt: " + e.toString());}


      //===== 2. We test a big load:
      final int numTimers = 1000;
      Log.info(ME, "Phase 2: Testing " + numTimers + " timeouts ...");
      timeout.destroy();
      timeout = Timeout.getInstance(); // get a new handle
      class Dummy2 implements I_Timeout
      {
         private String ME = "Dummy2";
         private int counter = 0;
         private long start = 0L;
         public void timeout(Object userData)
         {
            if (counter == 0) { Date dd = new Date(); start = dd.getTime(); }

            counter++;

            if (counter == numTimers) {
               Date dd = new Date();
               long diff = dd.getTime() - start;
               if (diff < 2000L)
                  Log.exit(ME, "Success, tested " + numTimers + " timers, all updates came in " + diff + " millis");
               else
                  Log.panic(ME, "Error testing " + numTimers + " timers, all updates needed " + diff + " millis");
            }
         }
      }
      Dummy2 dummy2 = new Dummy2();
      StopWatch stopWatch = new StopWatch();
      for (int ii=0; ii<numTimers; ii++) {
         timeout.addTimeoutListener(dummy2, 4000L, "timer-"+ii);
      }
      Log.info(ME, "Feeding of " + numTimers + " done, " + (long)(1000*(double)numTimers/stopWatch.elapsed()) + " adds/sec" + stopWatch.toString());

      Log.info(ME, "Waiting in main");
      try { Thread.currentThread().sleep(10000L); } catch (Exception e) { Log.panic(ME, "main interrupt: " + e.toString());}
      Log.panic(ME, "Test failed");
   }

}


