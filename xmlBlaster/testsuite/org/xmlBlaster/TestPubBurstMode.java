/*------------------------------------------------------------------------------
Name:      TestPubBurstMode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestPubBurstMode.java,v 1.1 2000/11/07 19:29:00 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests the method publish() in burst mode, messages are
 * collected and sent in one method call. 
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *  java testsuite.org.xmlBlaster.TestPubBurstMode -numPublish 10000 -client.protocol RMI -warn false
 *  jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPubBurstMode
 *  jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPubBurstMode
 * </pre>
 */
public class TestPubBurstMode extends TestCase
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "AMessage";
   private XmlBlasterConnection senderConnection;
   private String senderName;

   private int numPublish;
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";
   
   private StopWatch stopWatch = new StopWatch();


   /**
    * Constructs the TestPubBurstMode object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPubBurstMode(String testName, String loginName)
   {
       super(testName);
       this.senderName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         numPublish = XmlBlasterProperty.get("numPublish", 100);
         senderConnection = new XmlBlasterConnection();   // Find orb
         String passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper();     // == "<qos></qos>";
         senderConnection.login(senderName, passwd, qos); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout();
   }


   /**
    * TEST: Construct a message and publish it.
    */
   public void testPublish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing messages ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestPubBurstMode-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPubBurstMode-AGENT>" +
                      "</key>";
      PublishQosWrapper qosWrapper = new PublishQosWrapper();
      String qos = qosWrapper.toXml(); // == "<qos></qos>"

      MessageUnit[] msgUnitArr = new MessageUnit[numPublish];
      for (int ii=0; ii<numPublish; ii++) {
         String senderContent = ME + ii; // different content forcing xmlBlaster to store
         msgUnitArr[ii] = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      }

      String[] publishOidArr = null;
      try {
         Log.info(ME, "Publishing " + numPublish + " messages in burst mode ...");
         stopWatch = new StopWatch();
         publishOidArr = senderConnection.publishArr(msgUnitArr);
         double elapsed = (double)stopWatch.elapsed()/1000.; // msec -> sec
         Log.info(ME, "Published " + numPublish + " messages in burst mode in " + elapsed + " sec: " +
                       (long)(numPublish/elapsed) + " messages/sec");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }
      assert("returned publishOidArr == null", publishOidArr != null);
      assertEquals("returned publishOidArr", numPublish, publishOidArr.length);
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishMany()
   {
      testPublish();
      Log.plain(ME, "");
      testPublish();
      Log.plain(ME, "");
      testPublish();
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestPubBurstMode("testPublishMany", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPubBurstMode
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestPubBurstMode</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestPubBurstMode testPub = new TestPubBurstMode("TestPubBurstMode", "Tim");
      testPub.setUp();
      testPub.testPublishMany();
      testPub.tearDown();
      Log.exit(TestPubBurstMode.ME, "Good bye");
   }
}

