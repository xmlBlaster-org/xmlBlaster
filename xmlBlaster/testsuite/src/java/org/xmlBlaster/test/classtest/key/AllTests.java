package org.xmlBlaster.test.classtest.key;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.classtest.key.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.classtest.key.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.key.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster Key tests");
      suite.addTest(MsgKeyFactoryTest.suite());
      suite.addTest(QueryKeyFactoryTest.suite());
      return suite;
   }
}
