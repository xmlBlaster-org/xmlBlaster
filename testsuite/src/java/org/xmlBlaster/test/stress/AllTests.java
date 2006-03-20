package org.xmlBlaster.test.stress;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Xms18M -Xmx256M -Djava.compiler= org.xmlBlaster.test.stress.AllTests
 * java -Xms18M -Xmx256M -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.stress.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster stress tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.stress.LoadTestSub.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.stress.RamTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.stress.BigMessage.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.stress.MassiveSubTest.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new BigMessage("BigMessage"); 
   }
}
