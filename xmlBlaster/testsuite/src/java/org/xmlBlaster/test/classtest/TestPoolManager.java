package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.pool.PoolManager;
import org.xmlBlaster.util.pool.ResourceWrapper;
import org.xmlBlaster.util.pool.I_PoolManager;
import org.xmlBlaster.util.XmlBlasterException;
import java.util.*;
import junit.framework.*;

/**
 * Test org.xmlBlaster.util.pool.PoolManager
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.TestPoolManager
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * @see org.xmlBlaster.util.pool.PoolManager
 */
public class TestPoolManager extends TestCase {
   private final String ME = "TestPoolManager";

   public TestPoolManager(String name) {
      super(name);
   }

   protected void setUp() {
   }

   /**
    * This class is usually a UserSession object or a JDBC connection object
    * or whatever resource you want to handle
    */
   class TestResource {
      String name;
      String instanceId;
      boolean isBusy;
      boolean isErased = false;
      public TestResource(String name, String instanceId, boolean isBusy) {
         this.name = name;
         this.instanceId = instanceId;
         this.isBusy = isBusy;
      }
      public String toString() {
         return name;
      }
   }

   /**
    * This class does the resource pooling for TestResource,
    * with the help of PoolManager
    */
   class TestPool implements I_PoolManager {
      private int counter = 0;
      PoolManager poolManager;
      TestPool(int maxInstances, long busyToIdle, long idleToErase) {
         poolManager = new PoolManager(ME, this, maxInstances, busyToIdle, idleToErase);
      }

      // These four methods are callbacks from PoolManager ...
      public void idleToBusy(Object resource) {
         TestResource rr = (TestResource) resource;
         System.out.println("Entering idleToBusy(" + rr.name + ") ...");
         rr.isBusy = true; // you could do some re-initialization here ...
      }
      public void busyToIdle(Object resource) {
         TestResource rr = (TestResource) resource;
         System.out.println("Entering busyToIdle(" + rr.name + ") ...");
         rr.isBusy = false; // you could do some coding here ...
      }
      public Object toCreate(String instanceId) throws XmlBlasterException {
         TestResource rr = new TestResource("TestResource-" + (counter++), instanceId, true);
         System.out.println("Entering toCreate(instanceId='" + instanceId + "', " + rr.name + ") ...");
         return rr;
      }
      public void toErased(Object resource) {
         TestResource rr = (TestResource) resource;
         System.out.println("Entering toErased(" + rr.name + ") ...");
         rr.isErased = true;
      }

      // These methods are used by your application to get a recource ...
      TestResource reserve() {
         return reserve(PoolManager.GENERATE_RANDOM);
      }
      TestResource reserve(String instanceId) {
         System.out.println("Entering reserve(" + instanceId + ") ...");
         try {
            synchronized (this) {
               ResourceWrapper rw = (ResourceWrapper) poolManager.reserve(instanceId);
               TestResource rr = (TestResource) rw.getResource();
               if (rr == null)
                  fail("*****ERROR:rr==null");
               rr.instanceId = rw.getInstanceId(); // remember the generated unique id
               return rr;
            }
         }
         catch (XmlBlasterException e) {
            System.err.println("*****WARNING:Caught exception in reserve(): " + e.getMessage());
            return null;
         }
      }
      void release(TestResource rr) {
         System.out.println("Entering release() ...");
         try {
            synchronized (this) {
               poolManager.release(rr.instanceId);
            }
         }
         catch (XmlBlasterException e) {
            System.err.println("*****WARNING:Caught exception in release(): " + e.getMessage());
         }
      }
   }

   public void testWithGeneratedInstanceIdAndBusyTimeout() {
      System.out.println("\n\n=========================\nStarting TEST 1 ...");
      TestPool testPool = new TestPool(3, 2000, 0);
      TestResource r0 = testPool.reserve();
      TestResource r1 = testPool.reserve(PoolManager.GENERATE_RANDOM); // is default
      TestResource r2 = testPool.reserve();
      testPool.reserve();
      if (testPool.poolManager.getNumBusy() != 3 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 1.1 FAILED: Wrong number of busy/idle resources");
      testPool.release(r0);
      if (testPool.poolManager.getNumBusy() != 2 || testPool.poolManager.getNumIdle() != 1)
         fail("TEST 1.2 FAILED: Wrong number of busy/idle resources");
      testPool.reserve();
      testPool.release(r2);
      System.out.println(testPool.poolManager.toXml());

      // The resources are swapped to idle in 2 seconds, lets wait 3 seconds ...
      try {
         Thread.sleep(3000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 3)
         fail("TEST 1.3 FAILED: Wrong number of busy/idle resources");
      testPool.reserve();
      if (testPool.poolManager.getNumBusy() != 1 || testPool.poolManager.getNumIdle() != 2)
         fail("TEST 1.4 FAILED: Wrong number of busy/idle resources");
      try {
         Thread.sleep(1000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
   }

   public void testWithSuppliedInstanceIdAndBusyTimeout() {
      System.out.println("\n\n=========================\nStarting TEST 2 ...");
      TestPool testPool = new TestPool(3, 2000, 0);
      TestResource r0 = testPool.reserve("ID-0");
      TestResource r1 = testPool.reserve("ID-1");
      TestResource r2 = testPool.reserve("ID-2");
      r2 = testPool.reserve("ID-2");
      r2 = testPool.reserve("ID-2");
      r2 = testPool.reserve("ID-2");
      if (testPool.poolManager.getNumBusy() != 3 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 2.1 FAILED: Wrong number of busy/idle resources");
      testPool.reserve("ID-3");
      testPool.release(r0);
      if (testPool.poolManager.getNumBusy() != 2 || testPool.poolManager.getNumIdle() != 1)
         fail("TEST 2.2 FAILED: Wrong number of busy/idle resources");
      testPool.reserve("ID-4");
      testPool.release(r2);
      System.out.println(testPool.poolManager.toXml());


      // The resources are swapped to idle in 2 seconds, lets wait 3 seconds ...
      try {
         Thread.sleep(3000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 3)
         fail("TEST 2.3 FAILED: Wrong number of busy/idle resources");
      testPool.reserve("ID-5");
      if (testPool.poolManager.getNumBusy() != 1 || testPool.poolManager.getNumIdle() != 2)
         fail("TEST 2.4 FAILED: Wrong number of busy/idle resources");
      testPool.poolManager.destroy();
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 2.5 FAILED: Wrong number of busy/idle resources");
      testPool.reserve("ID-6");
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 1 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 2.6 FAILED: Wrong number of busy/idle resources");
      try {
         Thread.sleep(1000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
   }

   public void testWithSuppliedInstanceIdBusyTimeoutAndEraseTimeout() {
      System.out.println("\n\n=========================\nStarting TEST 3 ...");
      TestPool testPool = new TestPool(3, 2000, 3000); // erase resource after 3 sec in idle state
      TestResource r0 = testPool.reserve("ID-0");
      TestResource r1 = testPool.reserve("ID-1");
      TestResource r2 = testPool.reserve("ID-2");
      testPool.poolManager.erase("ID-2"); // erase from busy list
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 2 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 3.1 FAILED: Wrong number of busy/idle resources");

      // The resources are swapped to idle in 2 seconds, lets wait 3 seconds ...
      try {
         Thread.sleep(3000);
      }
      catch (InterruptedException i) {
      }
      testPool.poolManager.erase("ID-1"); // erase from idle list
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 1)
         fail("TEST 3.2 FAILED: Wrong number of busy/idle resources");

      // The resources are erased after 3 seconds in idle state, lets wait 4 seconds ...
      try {
         Thread.sleep(4000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 3.3 FAILED: Wrong number of busy/idle resources");
      try {
         Thread.sleep(1000);
      }
      catch (InterruptedException i) {
      }
      System.out.println(testPool.poolManager.toXml());
   }

   public void testMultiThreaded() {
      System.out.println("\n\n=========================\nStarting TEST 4 ...");
      class Test extends Thread {
         String ME = "TestThread";
         TestPool testPool;
         Test(String name, TestPool testPool) {
            this.ME = name;
            this.testPool = testPool;
         }
         public void run() {
            try {
               System.out.println("\n\nStarted " + ME + " ...");
               TestResource r0 = testPool.reserve(PoolManager.USE_OBJECT_REF);
               TestResource r1 = testPool.reserve(PoolManager.USE_OBJECT_REF);
               TestResource r2 = testPool.reserve(PoolManager.USE_OBJECT_REF);
               TestResource r3 = testPool.reserve(PoolManager.USE_OBJECT_REF);
               TestResource r4 = testPool.reserve(PoolManager.USE_OBJECT_REF);
               org.jutils.runtime.Sleeper.sleep(20L);

               // multi thread access
               testPool.poolManager.isBusy("unknown");
               testPool.poolManager.isBusy("TestResource-1");
               testPool.poolManager.busyRefresh("TestResource-1");
               testPool.poolManager.busyRefresh("TestResource-1");
               testPool.poolManager.busyRefresh("TestResource-1");
               testPool.poolManager.getNumBusy();
               testPool.poolManager.getNumIdle();
               testPool.poolManager.getState();
               testPool.poolManager.toXml();
               testPool.poolManager.erase("" + r2); // the instanceId
               r2 = testPool.reserve();
               testPool.release(r2);
               r2 = testPool.reserve();
               testPool.release(r2);
               testPool.release(r3);
               System.out.println("\n\nFinished " + ME + " ...");
               //System.out.println(testPool.poolManager.toXml());
            }
            catch (Throwable e) {
               e.printStackTrace();
               fail("\n++++++++++++++++++++++++\nTEST 4.1 FAILED: " + e.toString());
            }
         }
      }
      int numThreads = 6;
      long busyToIdle = 5000L;
      long idleToErase = 4000L;
      TestPool testPool = new TestPool(60, busyToIdle, idleToErase);
      for (int jj = 0; jj < 2; jj++) {
         System.out.println("\n run # " + jj);
         for (int ii = 0; ii < numThreads; ii++) {
            String name = "TestThread-" + ii;
            Test p = new Test(name, testPool);
            p.setDaemon(true);
            p.start();
         }
         org.jutils.runtime.Sleeper.sleep(2000L);
         if (testPool.poolManager.getNumBusy() != 3 * numThreads)
            fail("TEST 4.2 FAILED: Wrong number " + testPool.poolManager.getNumBusy() + " of busy resources");
         if (jj == 0)
            org.jutils.runtime.Sleeper.sleep(4000L); // now all busy resources are idle
      }
      org.jutils.runtime.Sleeper.sleep(1000L);
      testPool.poolManager.destroy();
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 4.3 FAILED: Wrong number of busy/idle resources");
   }

   public void testDestroy() {
      System.out.println("\n\n=========================\nStarting TEST 5 ...");
      TestPool testPool = new TestPool(10, 2000, 3000); // erase resource after 3 sec in idle state
      TestResource r0 = testPool.reserve(PoolManager.USE_HASH_CODE);
      TestResource r1 = testPool.reserve(PoolManager.GENERATE_RANDOM);
      TestResource r2 = testPool.reserve(PoolManager.USE_HASH_CODE);
      TestResource r3 = testPool.reserve(PoolManager.USE_OBJECT_REF);
      TestResource r4 = testPool.reserve();
      testPool.release(r2);
      r2 = testPool.reserve();
      testPool.release(r2);
      testPool.release(r3);
      System.out.println(testPool.poolManager.toXml());
      testPool.poolManager.destroy();
      System.out.println(testPool.poolManager.toXml());
      if (testPool.poolManager.getNumBusy() != 0 || testPool.poolManager.getNumIdle() != 0)
         fail("TEST 5.1 FAILED: Wrong number of busy/idle resources");
   }
}

