package testsuite.org.xmlBlaster.mime;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java clustertest.AllTests
 * java -Djava.compiler= clustertest.AllTests
 * java -Djava.compiler= junit.ui.TestRunner -noloading clustertest.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster cluster tests");
      suite.addTest(TestGetRegexFilter.suite());
      suite.addTest(TestGetFilter.suite());
      suite.addTest(TestSubscribeFilter.suite());
      suite.addTest(TestPublishFilter.suite());
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestGetRegexFilter(null,null,null); 
      new TestGetFilter(null,null,null); 
      new TestSubscribeFilter(null,null,null); 
      new TestPublishFilter(null,null,null); 
   }
}
