/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test;

import junit.framework.*;

/**
 * java -Xms18M -Xmx256M -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.AllTests
 */
public class AllTests extends junit.framework.TestCase
{

  public AllTests(String s) {
    super(s);
  }

  public static junit.framework.Test suite() {
    junit.framework.TestSuite suite = new junit.framework.TestSuite("Run all xmlBlaster tests");
    suite.addTest(org.xmlBlaster.test.classtest.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.admin.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.qos.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.client.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.topic.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.authentication.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.mime.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.dispatch.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.distributor.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.jms.AllTests.suite());
    //suite.addTest(org.xmlBlaster.test.memoryleak.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.snmp.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.persistence.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.cluster.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.contrib.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.jmx.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.j2ee.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.stress.AllTests.suite());
    suite.addTest(org.xmlBlaster.test.classloader.AllTests.suite());
    suite.addTest(new TestSuite(org.xmlBlaster.test.StopXmlBlaster.class));
    return suite;
  }
}
