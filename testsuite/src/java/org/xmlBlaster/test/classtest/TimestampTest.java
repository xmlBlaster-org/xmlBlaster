package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBlasterException;

import junit.framework.*;

/**
 * Test Timestamp class (creating unique timestamps in JVM scope). 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TimestampTest
 * @see org.xmlBlaster.util.Timestamp
 */
public class TimestampTest extends TestCase {
   private String ME = "TimestampTest";
   private static Logger log = Logger.getLogger(TimestampTest.class.getName());
   private boolean event = false;
   private int counter = 0;

   public TimestampTest(String name) {
      super(name);

   }

   /**
    * Test basics
    */
   public void testTimestamp() {
      Timestamp ts1 = new Timestamp();
      Timestamp ts2 = new Timestamp();
      assertFalse("Same timestamp", ts1.equals(ts2));
      assertTrue("timestamp descending", ts2.compareTo(ts1) == 1);
      assertEquals("string parse", ts2.toString(), Timestamp.valueOf(ts2.toString()).toString());
      System.out.println("***TimestampTest: testTimestamp [SUCCESS]");
   }

   /**
    * Test that timstamps are unique and ascending
    */
   public void testUnique() {
      long last = 0L;
      int n = 10000;
      for(int i=0; i<n; i++) {
         Timestamp ts = new Timestamp();
         assertTrue("Timestamp not ascending or unique last="+last+" curr="+ts.getTimestamp(), ts.getTimestamp() > last);
         last = ts.getTimestamp();
      }
      System.out.println("***TimestampTest: testUnique [SUCCESS]");
   }

   int iThread;
   /**
    * Test that timstamps are unique and ascending
    */
   public void testSync() {
      final int n = 20;
      final int m = 10000;
      final java.util.Set set = java.util.Collections.synchronizedSet(new java.util.HashSet());
      Thread[] threadArr = new Thread[n];
      for(iThread=0; iThread<n; iThread++) {
         threadArr[iThread] = new Thread() {
            public void run() {
               super.setName(""+iThread);
               long last = 0L;
               for(int j=0; j<m; j++) {
                  Timestamp ts = new Timestamp();
                  set.add(ts.getTimestampLong());
                  assertTrue("Timestamp not ascending or unique", ts.getTimestamp() > last);
                  last = ts.getTimestamp();
               }
               System.out.println("Thread #" + super.getName() + " done");
            }
         };
         threadArr[iThread].start();
         System.out.println("Started #" + iThread);
      }
      try { Thread.sleep(2000L); } catch( InterruptedException i) {}
      for(int i=0; i<n; i++) {
         try {
            threadArr[i].join();
         }
         catch (InterruptedException e) {
         }
      }
      assertEquals("Missing timestamps", n*m, set.size());
      System.out.println("***TimestampTest: testSync [SUCCESS]");
   }
}
