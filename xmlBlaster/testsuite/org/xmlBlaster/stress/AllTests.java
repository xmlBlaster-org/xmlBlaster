package testsuite.org.xmlBlaster.stress;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java clustertest.AllTests
 * java -Djava.compiler= clustertest.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading testsuite.org.xmlBlaster.stress.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster stress tests");
      suite.addTest(new TestSuite(testsuite.org.xmlBlaster.stress.BigMessage.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new BigMessage("BigMessage"); 
   }
}
