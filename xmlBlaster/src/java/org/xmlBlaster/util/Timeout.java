/*------------------------------------------------------------------------------
Name:      Timeout.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Handling the Client data
Version:   $Id: Timeout.java,v 1.2 2000/05/26 08:23:39 ruff Exp $
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
 * There is a single background thread that is used to execute all of the timer's tasks,
 * sequentially. Timer tasks should complete quickly. If a timer task takes excessive time to complete, it "hogs" the timer's
 * task execution thread. This can, in turn, delay the execution of subsequent tasks, which may "bunch up" and execute in
 * rapid succession when (and if) the offending task finally completes.
 * <p />
 * This singleton is thread-safe.
 * <p />
 * This class does not offer real-time guarantees.
 * <p />
 * Adding or removing a timer is very perfomant, also when huge amounts of timers (> 1000) are used.
 * <p />
 * TODO: Use a thread pool to dispatch the timeout callbacks.
 */
public class Timeout extends Thread
{
   private static String ME = "Timeout";
   private static Timeout theTimeout = null;   // Singleton pattern
   /** Sorted map */
   private TreeMap map = null;
   private boolean running = true;

   /**
    * Access to Timeout singleton
    */
   public static Timeout getInstance()
   {
      synchronized (Timeout.class) {
         if (theTimeout == null) {
            theTimeout = new Timeout();
            theTimeout.start();
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
    * @return A handle which you can use to unregister with removeTimeoutListener()
    */
   public final Long addTimeoutListener(I_Timeout listener, long delay, Object userData) throws XmlBlasterException
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
    * Remove a listener before the timeout happened.
    * <p />
    * @param listener Your callback handle (you need to implement this interface)
    * @param delay The timeout in milliseconds
    * @return A handle which you can use to unregister
    */
   public final void removeTimeoutListener(Long key) throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering removeTimeoutListener(" + key + ") ...");
      synchronized(map) {
         Container container = (Container)map.remove(key);
         container.callback = null;
         container.userData = null;
         container = null;
      }
   }


   /**
    * Reset all pending timeouts.
    * <p />
    */
   public final void removeAll() throws XmlBlasterException
   {
      if (Log.CALLS) Log.calls(ME, "Entering removeAll() ...");
      synchronized(map) {
         map.clear();
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

      // We have the internal knowledge that the key is the scheduled timeout in millis since 1970
      // so we use it here for testing ...
      final Long[] keyArr =  new Long[4];

      class Dummy implements I_Timeout
      {
         private String ME = "Dummy";
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

      Dummy dummy = new Dummy();
      keyArr[2] = timeout.addTimeoutListener(dummy, 4000L, "timer-4000");
      keyArr[3] = timeout.addTimeoutListener(dummy, 5000L, "timer-5000");
      keyArr[0] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      keyArr[1] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      Long key = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      timeout.removeTimeoutListener(key);

      try { Thread.currentThread().sleep(7000L); } catch (Exception e) { Log.panic(ME, "main interrupt: " + e.toString());}


      //===== 2. We test a big load:
      final int numTimers = 1000;
      Log.info(ME, "Phase 2: Testing " + numTimers + " timeouts ...");
      timeout.removeAll();
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


