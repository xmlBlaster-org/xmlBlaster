/*------------------------------------------------------------------------------
Name:      TestAll.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
Version:   $Id: TestAll.java,v 1.16 2000/02/25 18:56:23 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import test.framework.*;


/**
 * This test client starts all known tests.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestAll
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestAll
 * </pre>
 */
public class TestAll
{
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      suite.addTest(TestLogin.suite());
      suite.addTest(TestGet.suite());
      suite.addTest(TestSub.suite());
      suite.addTest(TestUnSub.suite());
      suite.addTest(TestPtD.suite());
      suite.addTest(TestPtDQueue.suite());
      suite.addTest(TestPersistence.suite());
      suite.addTest(TestPub.suite());
      suite.addTest(TestInvocationRecorder.suite());
      suite.addTest(TestFailSave.suite());
      suite.addTest(LoadTestSub.suite());
      suite.addTest(RamTest.suite());
      return suite;
   }
}
