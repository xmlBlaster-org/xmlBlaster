/*------------------------------------------------------------------------------
Name:      TestPubForce.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestPubForce.java,v 1.4 2000/06/18 15:22:02 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.PublishQosWrapper;
import org.jutils.log.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import test.framework.*;


/**
 * This client tests the method publish() with the forceUpdate QOS tag
 * <br />
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPubForce
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPubForce
 * </pre>
 */
public class TestPubForce extends TestCase implements I_Callback
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "TestMessage";
   private CorbaConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestPubForce object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPubForce(String testName, String loginName)
   {
       super(testName);
       this.senderName = loginName;
       this.receiverName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = new CorbaConnection(); // Find orb
         String passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         xmlBlaster = senderConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster
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
         strArr = xmlBlaster.erase(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribe()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish(boolean forceUpdate)
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestPubForce-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPubForce-AGENT>" +
                      "</key>";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes());
      PublishQosWrapper qosWrapper = new PublishQosWrapper();
      if (forceUpdate)
         qosWrapper.setForceUpdate();
      String qos = qosWrapper.toXml(); // == "<qos><forceUpdate/></qos>"

      try {
         publishOid = xmlBlaster.publish(msgUnit, qos);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }
      assert("returned publishOid == null", publishOid != null);
      assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * Identical messages are not delivered unless ForceUpdate is set
    */
   public void testPublishForceUpdate()
   {
      testSubscribe();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      senderContent = "I'm the new same content";

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      testPublish(false);
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 0, numReceived); // No message should arrive since they are identical

      testPublish(false);
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 0, numReceived); // No message should arrive since they are identical

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
   }


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong receveiver", receiverName, loginName);
      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

      messageArrived = true;
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
   private void waitOnUpdate(final long timeout)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (!messageArrived) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            Log.warning(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived = false;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestPubForce("testPublishForceUpdate", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPubForce
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestPubForce</pre>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestPubForce testSub = new TestPubForce("TestPubForce", "Tim");
      testSub.setUp();
      testSub.testPublishForceUpdate();
      testSub.tearDown();
      Log.exit(TestPubForce.ME, "Good bye");
   }
}

