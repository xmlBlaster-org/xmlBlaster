package org.xmlBlaster.test.jmx;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests.
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.persistence.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
     TestSuite suite= new TestSuite("All xmlBlaster jmx tests");
      suite.addTest(TestSerializer.suite());
      suite.addTest(TestConnector.suite());
      suite.addTest(TestRemoteMBeanServer.suite());
      return suite;
   }
}