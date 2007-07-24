/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.xmlBlaster.test.client.TestCommand;

import junit.framework.*;


/**
 * This test client starts all known tests in directory xmlBlaster/testsuite/src/java/org/xmlBlaster/client
 * these are remote tests between clients and a running xmlBlaster. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.AllTests
 * </pre>
 */
public class AllTests
{
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster message client side tests");
      suite.addTest(org.xmlBlaster.test.client.TestSynchronousCache.suite());
      suite.addTest(new TestSuite(TestCommand.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestFailSafe.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestFailSafePing.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestFailSafeAsync.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestXmlBlasterAccessMultiThreaded.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestTailback.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestPtPDispatch.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestPtPPersistent.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestHistoryZero.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestFilePollerPlugin.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestRequestResponse.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestPersistentSession.class));
      suite.addTest(new TestSuite(org.xmlBlaster.test.client.TestLocalProtocol.class));
     return suite;
   }
}
