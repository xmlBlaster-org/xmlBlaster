package org.xmlBlaster.test.dispatch;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.dispatch.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster dispatch plugin tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.dispatch.ConfigurationParserTest.class));
      suite.addTest(TestPriorizedDispatchPlugin.suite());
      suite.addTest(TestPriorizedDispatchWithLostCallback.suite());
      return suite;
   }
}
