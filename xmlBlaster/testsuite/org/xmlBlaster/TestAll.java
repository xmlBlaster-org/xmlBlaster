/*------------------------------------------------------------------------------
Name:      TestAll.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
Version:   $Id: TestAll.java,v 1.8 2000/01/07 20:33:50 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import test.framework.*;


/**
 * This test client starts all known tests.
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestAll
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestAll
 * </code>
 */
public class TestAll
{
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      suite.addTest(TestSub.suite());
      suite.addTest(TestPtD.suite());
      suite.addTest(TestPtDQueue.suite());
      suite.addTest(LoadTestSub.suite());
      suite.addTest(RamTest.suite());
      return suite;
   }
}
