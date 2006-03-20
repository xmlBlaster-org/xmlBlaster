package org.xmlBlaster.test.distributor;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.distributor.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.distributor.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster distributor plugin tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.distributor.TestConsumableQueue.class));
      return suite;
   }
}
