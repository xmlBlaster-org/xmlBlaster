package clustertest;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java clustertest.AllTests
 * java -Djava.compiler= clustertest.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading clustertest.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster cluster tests");
      suite.addTest(new TestSuite(clustertest.PublishTest.class));
      suite.addTest(new TestSuite(clustertest.SubscribeTest.class));
      suite.addTest(new TestSuite(clustertest.SubscribeXPathTest.class));
      suite.addTest(new TestSuite(clustertest.DirtyReadTest.class));
      suite.addTest(new TestSuite(clustertest.EraseTest.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new PublishTest(null); 
      new DirtyReadTest(null); 
   }
}
