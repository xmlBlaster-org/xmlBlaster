/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.AllTests
 */
public class AllTests extends junit.framework.TestCase
{

  public AllTests(String s) {
    super(s);
  }

  public static junit.framework.Test suite() {
    junit.framework.TestSuite suite = new junit.framework.TestSuite("Run all xmlBlaster tests");
    suite.addTest(org.xmlBlaster.test.classtest.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.qos.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.authentication.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.mime.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.jdbc.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.snmp.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.persistence.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.cluster.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.stress.AllTests.suite());
    return suite;
  }
}
