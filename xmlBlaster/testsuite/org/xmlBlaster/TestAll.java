/*------------------------------------------------------------------------------
Name:      TestAll.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
Version:   $Id: TestAll.java,v 1.32 2002/02/16 17:41:43 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.jutils.JUtilsException;
import test.framework.*;


/**
 * This test client starts all known tests.
 * <p />
 * The complete testsuite runs ~2 minutes.
 * <p />
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

      //System.out.println("\n\n========= TESTING CORBA ==========\n");
      //try { XmlBlasterProperty.set("client.protocol", "SOCKET"); } catch(JUtilsException e) { Log.error("TestAll", e.toString()); }
      //try { XmlBlasterProperty.set("client.protocol", "IOR"); } catch(JUtilsException e) { Log.error("TestAll", e.toString()); }
      //try { XmlBlasterProperty.set("client.protocol", "RMI"); } catch(JUtilsException e) { Log.error("TestAll", e.toString()); }
      //try { XmlBlasterProperty.set("client.protocol", "XML-RPC"); } catch(JUtilsException e) { Log.error("TestAll", e.toString()); }
      suite.addTest(TestCorbaThreads.suite());
      suite.addTest(TestLogin.suite());
      suite.addTest(TestLoginLogoutEvent.suite());
      suite.addTest(TestGet.suite());
      suite.addTest(TestSubExact.suite());
      suite.addTest(TestSub.suite());
      suite.addTest(TestSubDispatch.suite());
      suite.addTest(TestSubXPath.suite());
      suite.addTest(TestSubMulti.suite());
      suite.addTest(TestUnSub.suite());
      suite.addTest(TestPtD.suite());
      suite.addTest(TestPtDQueue.suite());
      suite.addTest(TestPersistence.suite());
      suite.addTest(TestPersistence2.suite());
      suite.addTest(TestPub.suite());
      suite.addTest(TestPubBurstMode.suite());
      suite.addTest(TestPubForce.suite());
      suite.addTest(TestInvocationRecorder.suite());
      suite.addTest(TestFailSave.suite());
      suite.addTest(TestFailSavePing.suite());
      suite.addTest(TestSubManyClients.suite());
      suite.addTest(LoadTestSub.suite());
      suite.addTest(RamTest.suite());
      return suite;
   }
}
