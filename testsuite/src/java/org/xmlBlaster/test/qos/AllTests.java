/*------------------------------------------------------------------------------
Name:      AllTests.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Start all tests
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import junit.framework.*;


/**
 * This test client starts all known tests in directory xmlBlaster/testsuite/src/java/org/xmlBlaster/qos
 * these are remote tests between clients and a running xmlBlaster. 
 * <p />
 * The complete testsuite runs ~2 minutes.
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.AllTests
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.AllTests
 * </pre>
 */
public class AllTests
{
   public static Test suite()
   {
      TestSuite suite= new TestSuite("All xmlBlaster core features and QoS");

      //System.out.println("\n\n========= TESTING CORBA ==========\n");
      //try { glob.getProperty().set("client.protocol", "SOCKET"); } catch(JUtilsException e) { System.err.println("AllTests: " + e.toString()); }
      //try { glob.getProperty().set("client.protocol", "IOR"); } catch(JUtilsException e) { System.err.println("AllTests: " + e.toString()); }
      //try { glob.getProperty().set("client.protocol", "RMI"); } catch(JUtilsException e) { System.err.println("AllTests: " + e.toString()); }
      //try { glob.getProperty().set("client.protocol", "XMLRPC"); } catch(JUtilsException e) { System.err.println("AllTests: " + e.toString()); }
      
      // !!!!!! RMI, CORBA XMLRPC etc. all have thread leaks on shutdown
      // Only the SOCKET protocol seems to be clean
      // So we currently switch off this test
      // suite.addTest(new TestSuite(org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster.class));
      
      suite.addTest(TestCorbaThreads.suite());
      suite.addTest(TestCallback.suite());
      suite.addTest(TestCallbackConfig.suite());
      suite.addTest(TestGet.suite());
      suite.addTest(TestReconnectSameClientOnly.suite());
      suite.addTest(TestSubExact.suite());
      suite.addTest(TestSubOneway.suite());
      suite.addTest(TestSubGet.suite());
      suite.addTest(TestSubNoDup.suite());
      suite.addTest(TestSubNotify.suite());
      suite.addTest(TestSub.suite());
      suite.addTest(TestSubId.suite());
      // suite.addTest(TestSubHistory.suite());
      suite.addTest(TestSubLostClient.suite());
      suite.addTest(TestSubNoInitial.suite());
      suite.addTest(TestSubNoLocal.suite());
      suite.addTest(TestSubMultiSubscribe.suite());
      suite.addTest(TestSubDispatch.suite());
      suite.addTest(TestSubXPath.suite());
      suite.addTest(TestSubManyClients.suite());
      suite.addTest(new TestSuite(org.xmlBlaster.test.qos.TestSubXPathMany.class));
      suite.addTest(TestSubMulti.suite());
      suite.addTest(TestUnSub.suite());
      suite.addTest(TestPtD.suite());
      suite.addTest(TestPtSession.suite());
      suite.addTest(TestPtPSubscribable.suite());
      suite.addTest(TestPtDQueue.suite());
      suite.addTest(TestPtDQueueRedeliver.suite());
      suite.addTest(TestPub.suite());
      suite.addTest(TestPubBurstMode.suite());
      suite.addTest(TestPubForce.suite());
      suite.addTest(TestErase.suite());
      suite.addTest(TestUpdateClientException.suite());
      suite.addTest(TestInvocationRecorder.suite());
      suite.addTest(TestClientProperty.suite());
      return suite;
   }
}
