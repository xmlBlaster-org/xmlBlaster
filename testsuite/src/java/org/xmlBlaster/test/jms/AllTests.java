package org.xmlBlaster.test.jms;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.jms.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.jms.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.jms.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster class tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.jms.TestJmsAdmin.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.jms.TestJmsSubscribe.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.jms.TestMessages.class));
      return suite;
   }
}
