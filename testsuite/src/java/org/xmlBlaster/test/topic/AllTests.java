/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.topic;

import junit.framework.*;


/**
 * This test client starts all known tests in directory xmlBlaster/testsuite/src/java/org/xmlBlaster/topic
 * these are remote tests between clients and a running xmlBlaster. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.topic.AllTests
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.topic.AllTests
 * </pre>
 */
public class AllTests
{
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster message expiry tests");
      //suite.addTest(TestTopicHistory.suite());
      suite.addTest(TestReferenceCount.suite());
      suite.addTest(TestReferenceCountSwap.suite());
      suite.addTest(TestTopicLifeCycle.suite());
      return suite;
   }
}
