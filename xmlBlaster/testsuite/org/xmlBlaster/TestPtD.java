/*------------------------------------------------------------------------------
Name:      TestPtD.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtD.java,v 1.8 2000/01/30 18:42:56 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import test.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style.
 * <p>
 * Note that the three clients (client logins) are simulated in this class.<br />
 * Tests performed:<br />
 * <ul>
 *    <li>Manuel is the 'sender' and Ulrike the 'receiver' of a love letter</li>
 *    <li>Manuel sends a message to two destinations</li>
 * </ul>
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPtD
 *
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPtD
 * </code>
 */
public class TestPtD extends TestCase implements I_Callback
{
   private Server senderXmlBlaster = null;
   private final static String ME = "TestPtD";

   private final String senderName = "Manuel";
   private String publishOid = "";
   private CorbaConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private CorbaConnection receiverConnection = null;
   private Server receiverXmlBlaster = null;

   private final String receiver2Name = "KGB";
   private CorbaConnection receiver2Connection = null;
   private Server receiver2XmlBlaster = null;

   private int numReceived = 0;


   /**
    * Constructs the TestPtD object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPtD(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    * - One connection for the receiver client
    * - One connection for the receiver2 client
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";

         receiverConnection = new CorbaConnection();
         receiverXmlBlaster = receiverConnection.login(receiverName, passwd, "<qos></qos>", this);

         receiver2Connection = new CorbaConnection();
         receiver2XmlBlaster = receiver2Connection.login(receiver2Name, passwd, "<qos></qos>", this);

         senderConnection = new CorbaConnection();
         senderXmlBlaster = senderConnection.login(senderName, passwd, "<qos></qos>", this);
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown()
   {
      Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...
      receiverConnection.logout(receiverXmlBlaster);
      receiver2Connection.logout(receiver2XmlBlaster);
      senderConnection.logout(senderXmlBlaster);
   }


   /**
    * TEST: Send a message to one destination
    * <p />
    * The returned subscribeOid is checked
    */
   public void testPtOneDestination()
   {
      if (Log.TRACE) Log.trace(ME, "Testing point to one destination ...");

      // Construct a love message and send it to Ulrike
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "</qos>";

      senderContent = "Hi " + receiverName + ", i love you, " + senderName;
      MessageUnit messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
      try {
         publishOid = senderXmlBlaster.publish(messageUnit, qos);
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      waitOnUpdate(5000L, 1);
      assertEquals("numReceived after sending", 1, numReceived); // message arrived?
      numReceived = 0;
   }


   /**
    * TEST: Send a message to two destinations
    * <p />
    */
   public void testPtManyDestinations()
   {
      if (Log.TRACE) Log.trace(ME, "Testing point to many destinations ...");

      // Construct a love message and send it to Ulrike
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "   <destination queryType='EXACT'>" +
                           receiver2Name +
                   "   </destination>" +
                   "</qos>";

      senderContent = "Hi " + receiver2Name + ", i know you are listening, " + senderName;
      MessageUnit messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
      try {
         publishOid = senderXmlBlaster.publish(messageUnit, qos);
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      waitOnUpdate(5000L, 2);
      assertEquals("numReceived after sending", 2, numReceived); // message arrived at receiver and receiver2 ?
      numReceived = 0;
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

      if (!receiverName.equals(loginName) && !receiver2Name.equals(loginName))
         assert("Wrong receveiver " + receiverName, false);
      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
   }


   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (numReceived < numWait) {
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
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestPtD("testPtOneDestination"));
       suite.addTest(new TestPtD("testPtManyDestinations"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPtD
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestPtD</code>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestPtD testSub = new TestPtD("TestPtD");
      testSub.setUp();
      testSub.testPtOneDestination();
      testSub.tearDown();
      Log.exit(TestPtD.ME, "Good bye");
   }
}
