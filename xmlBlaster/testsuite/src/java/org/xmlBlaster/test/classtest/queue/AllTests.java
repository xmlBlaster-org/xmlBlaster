package org.xmlBlaster.test.classtest.queue;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.classtest.queue.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.classtest.queue.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.queue.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster queue tests");
      suite.addTest(I_QueueTest.suite());
      suite.addTest(new TestSuite(org.xmlBlaster.test.classtest.queue.CacheQueueTest.class));
      suite.addTest(org.xmlBlaster.test.classtest.queue.QueueThreadingTest.suite());
      suite.addTest(org.xmlBlaster.test.classtest.queue.QueueServerEntryTest.suite());
      suite.addTest(org.xmlBlaster.test.classtest.queue.QueueExtendedTest.suite());
      // This runs manually only as we need to kill the Database:
      suite.addTest(org.xmlBlaster.test.classtest.queue.JdbcQueueTest.suite());
      return suite;
   }
}
