package org.xmlBlaster.test.C;

import junit.framework.*;

/**
 * TestSuite that runs the C client library tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.C.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.C.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster C client library tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.C.TestC.class));
      return suite;
   }
}
