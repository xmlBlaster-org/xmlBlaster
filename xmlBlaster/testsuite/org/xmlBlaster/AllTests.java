/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
//package testsuite.org.xmlBlaster;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.ui.TestRunner -noloading AllTests
 */
public class AllTests extends TestCase
{

  public AllTests(String s) {
    super(s);
  }

  public static Test suite() {
    TestSuite suite = new TestSuite();
    suite.addTest(classtest.AllTests.suite());
    suite.addTest(testsuite.org.xmlBlaster.TestAll.suite());
    suite.addTest(authentication.AllTests.suite());
    suite.addTest(clustertest.AllTests.suite());
    return suite;
  }
}
