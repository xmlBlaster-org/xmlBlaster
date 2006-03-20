package org.xmlBlaster.test.classtest.msgstore;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.classtest.msgstore.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.classtest.msgstore.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.classtest.msgstore.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster msgUnitStore tests");
      suite.addTest(I_MapTest.suite());
      suite.addTest(MsgUnitWrapperTest.suite());
      return suite;
   }
}
