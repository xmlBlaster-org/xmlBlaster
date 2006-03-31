package org.xmlBlaster.test.mime;

import junit.framework.*;

/**
 * TestSuite that runs all the sample tests. 
 * <pre>
 * java -Djava.compiler= org.xmlBlaster.test.mime.AllTests
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.mime.AllTests
 * </pre>
 */
public class AllTests {
   public static void main (String[] args) {
      junit.textui.TestRunner.run (suite());
   }
   public static Test suite ( ) {
      TestSuite suite= new TestSuite("All xmlBlaster mime plugin tests");
      suite.addTest(new TestSuite(XPathTransformerTest.class));
      suite.addTest(TestGetRegexFilter.suite());
      suite.addTest(TestGetSql92Filter.suite());
      suite.addTest(TestGetFilter.suite());
      suite.addTest(TestSubscribeFilter.suite());
      suite.addTest(TestXPathSubscribeFilter.suite());
      suite.addTest(TestPublishFilter.suite());
      return suite;
   }
   // To force compilation:
   public void dummy() {
      new TestGetRegexFilter(null,null,null);
      new TestGetSql92Filter(null,null,null);
      new TestGetFilter(null,null,null); 
      new TestSubscribeFilter(null,null,null); 
      new TestXPathSubscribeFilter(null,null,null); 
      new TestPublishFilter(null,null,null); 
   }
}
