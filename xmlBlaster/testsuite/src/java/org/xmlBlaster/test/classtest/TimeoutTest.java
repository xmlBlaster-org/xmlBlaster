package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.Timeout;
import org.xmlBlaster.util.I_Timeout;
import org.xmlBlaster.util.XmlBlasterException;

import junit.framework.*;

/**
 * Test Timeout class (scheduling for timeouts). 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TimeoutTest
 * @see org.xmlBlaster.util.Timeout
 */
public class TimeoutTest extends TestCase {
   private String ME = "TimeoutTest";
   private static Logger log = Logger.getLogger(TimeoutTest.class.getName());
   private boolean event = false;
   private int counter = 0;

   public TimeoutTest(String name) {
      super(name);

   }

   /**
    * Test a simple timeout
    */
   public void testTimeout() {
      System.out.println("***TimeoutTest: testTimeout ...");

      {
         event = false;
         Timeout timeout = new Timeout(ME);
         Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  event = true;
                  log.info("Timeout happened after 0 millisec");
               }
            },
            0L, null);

         try { Thread.currentThread().sleep(100L); } catch (InterruptedException e) {}
         assertEquals("Timeout not occurred after 0 msec.", true, event);
      }
      
      {
         event = false;
         Timeout timeout = new Timeout(ME);
         Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  event = true;
                  log.info("Timeout happened after 500 millisec");
               }
            },
            500L, null);

         try { Thread.currentThread().sleep(800L); } catch (InterruptedException e) {}
         assertEquals("Timeout not occurred after 1 sec.", true, event);
      }
      
      {
         event = false;
         Timeout timeout = new Timeout(ME);
         Timestamp timeoutHandle = timeout.addTimeoutListener(new I_Timeout() {
               public void timeout(Object userData) {
                  event = true;
                  log.severe("Timeout happened after 1 sec");
               }
            },
            1000L, null);

         try { Thread.currentThread().sleep(200L); } catch (InterruptedException e) {}
         assertEquals("Timeout occurred unexpected", false, event);
         timeout.removeTimeoutListener(timeoutHandle);
         try { Thread.currentThread().sleep(1000L); } catch (InterruptedException e) {}
         assertEquals("Timeout occurred unexpected", false, event);
      }

      System.out.println("***TimeoutTest: testTimeout [SUCCESS]");
   }

   /**
    * Testing basic functionality
    */
   public void testFunctionality() {
      System.out.println("***TimeoutTest: testFunctionality ...");

      Timeout timeout = new Timeout();
      counter = 0;

      // Test to remove invalid keys
      timeout.removeTimeoutListener(null);
      timeout.removeTimeoutListener(new Timestamp(12));

      // We have the internal knowledge that the key is the scheduled timeout in millis since 1970
      // so we use it here for testing ...
      final Timestamp[] keyArr = new Timestamp[4];
      class Dummy1 implements I_Timeout {
         private String ME = "Dummy1";
         public void timeout(Object userData) {
            long time = System.currentTimeMillis();
            long diff = time - keyArr[counter].getMillis();
            if (Math.abs(diff) < 40)
               // Allow 40 millis wrong notification (for code execution etc.) ...
               log.info("Timeout occurred for " + userData.toString() + " at " + time + " millis, real time failure=" + diff + " millis.");
            else {
               System.err.println("*****ERROR: Wrong timeout occurred for " + userData.toString() + " at " + time + " millis, scheduled was " + keyArr[counter] + " , real time failure=" + diff + " millis.");
               fail("*****ERROR: Wrong timeout occurred for " + userData.toString() + " at " + time + " millis, scheduled was " + keyArr[counter] + " , real time failure=" + diff + " millis.");
            }
            counter++;
         }
      }
      Dummy1 dummy = new Dummy1();
      keyArr[2] = timeout.addTimeoutListener(dummy, 4000L, "timer-4000");

      keyArr[3] = timeout.addTimeoutListener(dummy, 2000L, "timer-5500");
      try { keyArr[3] = timeout.refreshTimeoutListener(keyArr[3], 5500L); } catch (XmlBlasterException e) { fail("Refresh failed: " + e.getMessage()); }
      long diffT = keyArr[3].getMillis() - System.currentTimeMillis();
      assertTrue("ERROR: refresh failed", (Math.abs(5500L - diffT) <= 30));

      keyArr[0] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      keyArr[1] = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");

      long span = timeout.spanToTimeout(keyArr[2]);
      assertTrue("*****ERROR: This short span to timeout = " + span + " is probably wrong, or you have a very slow computer.", span >= 3000L);

      Timestamp key = timeout.addTimeoutListener(dummy, 1000L, "timer-1000");
      timeout.removeTimeoutListener(key);
      try { key = timeout.refreshTimeoutListener(key, 1500L); } catch (XmlBlasterException e) { log.info("Refresh failed which is OK (it is a test): " + e.getMessage()); }

      assertEquals("Should not be expired", false, timeout.isExpired(keyArr[2]));

      try { Thread.currentThread().sleep(7000L); } catch (Exception e) { fail("*****ERROR: main interrupt: " + e.toString()); }

      assertEquals("Should be expired", true, timeout.isExpired(keyArr[2]));

      System.out.println("***TimeoutTest: testFunctionality [SUCCESS]");
   }

   /**
    * We test a big load
    */
   public void testStressLoad() {
      System.out.println("***TimeoutTest: testStressLoad ...");

      String ME = "Timeout-Tester";
      Timeout timeout = new Timeout();
      
      final int numTimers = 10000;
      timeout.shutdown();
      timeout = new Timeout(); // get a new handle
      class Dummy2 implements I_Timeout {
         private String ME = "Dummy2";
         private long start = 0L;
         public void timeout(Object userData) {
            if (counter == 0) {
               start = System.currentTimeMillis();
            }
            counter++;
            if (counter == numTimers) {
               long diff = System.currentTimeMillis() - start;
               assertTrue("Error testing " + numTimers + " timers, all updates needed " + diff + " millis", diff < 4000L);
               log.info("Success, tested " + numTimers + " timers, all updates came in " + diff + " millis");
            }
         }
      }
      Dummy2 dummy2 = new Dummy2();
      long start = System.currentTimeMillis();
      for (int ii = 0; ii < numTimers; ii++) {
         timeout.addTimeoutListener(dummy2, 6000L, "timer-" + ii);
      }
      assertEquals("Expected " + numTimers + " instead of " + timeout.getSize() + " active timers", numTimers, timeout.getSize());

      log.info("Feeding of " + numTimers + " done, " + (long) (1000 * (double) numTimers / (System.currentTimeMillis() - start)) + " adds/sec");

      while (counter != numTimers) {
         try { Thread.currentThread().sleep(500L); } catch (Exception e) { fail("*****ERROR:main interrupt: " + e.toString()); }
      }

      System.out.println("***TimeoutTest: testStressLoad [SUCCESS]");
   }
}
