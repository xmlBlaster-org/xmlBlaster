/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.msgexpiry;

import org.jutils.JUtilsException;
import junit.framework.*;


/**
 * This test client starts all known tests in directory xmlBlaster/testsuite/src/java/org/xmlBlaster/msgexpiry
 * these are remote tests between clients and a running xmlBlaster. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.msgexpiry.AllTests
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.msgexpiry.AllTests
 * </pre>
 */
public class AllTests
{
   public static Test suite() {
      TestSuite suite= new TestSuite("All xmlBlaster message expiry tests");
      suite.addTest(TestVolatile.suite());
      suite.addTest(TestTopicLifeCycle.suite());
      return suite;
   }
}
