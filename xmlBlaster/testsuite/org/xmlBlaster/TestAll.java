/*------------------------------------------------------------------------------
Name:      TestAll.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
Version:   $Id: TestAll.java,v 1.3 1999/12/12 15:22:19 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import test.framework.*;


/**
 * This test client starts all known tests.
 * <p>
 * Invoke examples:
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestAll
 */
public class TestAll
{
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      suite.addTest(TestSub.suite());
      return suite;
   }
}
