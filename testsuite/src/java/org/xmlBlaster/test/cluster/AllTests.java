package org.xmlBlaster.test.cluster;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java org.xmlBlaster.test.cluster.AllTests
 * java -Djava.compiler= org.xmlBlaster.test.cluster.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.cluster.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster cluster tests");
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.NodeParserTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.PtPTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.PublishTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.SubscribeTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.SubscribeXPathTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.DirtyReadTest.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.cluster.EraseTest.class));
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new PublishTest(null); 
      new DirtyReadTest(null); 
   }
}
