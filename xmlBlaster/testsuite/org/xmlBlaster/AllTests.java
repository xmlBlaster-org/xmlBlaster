/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
//package testsuite.org.xmlBlaster;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.swingui.TestRunner -noloading AllTests
 */
public class AllTests extends junit.framework.TestCase
{

  public AllTests(String s) {
    super(s);
  }

  public static junit.framework.Test suite() {
    junit.framework.TestSuite suite = new junit.framework.TestSuite("All xmlBlaster core tests");
    suite.addTest(classtest.AllTests.suite());
    suite.addTest(testsuite.org.xmlBlaster.TestAll.suite());
    suite.addTest(authentication.AllTests.suite());
    suite.addTest(testsuite.org.xmlBlaster.stress.AllTests.suite());
    suite.addTest(testsuite.org.xmlBlaster.mime.AllTests.suite());
    suite.addTest(testsuite.org.xmlBlaster.jdbc.AllTests.suite());
    suite.addTest(clustertest.AllTests.suite());
    return suite;
  }
}
