/*------------------------------------------------------------------------------
Name:      TestPtDQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtDQueue.java,v 1.5 2000/02/01 15:18:20 ruff Exp $
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
 * This client tests the PtP (or PtD = point to destination) style, Manuel sends to Ulrike a message.
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * Manuel is the 'sender' and Ulrike the 'receiver'<br />
 * Ulrike is not on line when Manuel sends the message, and will receive the message
 * from her queue in the xmlBlaster when she logs in.
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue
 *
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue
 * </code>
 */
public class TestPtDQueue extends TestCase implements I_Callback
{
   private Server senderXmlBlaster = null;
   private final static String ME = "TestPtDQueue";

   private final String senderName = "Manuel";
   private String publishOid = "";
   private CorbaConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private CorbaConnection receiverConnection = null;
   private Server receiverXmlBlaster = null;

   private String passwd = "secret";

   private int numReceived = 0;
   private boolean messageArrived = false;


   /**
    * Constructs the TestPtDQueue object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPtDQueue(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      try {
         senderConnection = new CorbaConnection();
         senderXmlBlaster = senderConnection.login(senderName, passwd, "<qos></qos>", this);
      }
      catch (XmlBlasterException e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
          assert("login - XmlBlasterException: " + e.reason, false);
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
      senderConnection.logout(senderXmlBlaster);
   }


   /**
    * TEST: Sending a message to a not logged in client, which logs in later.
    * <p />
    * The sent message will be stored in a xmlBlaster queue for this client and than delivered
    * only if the &lt;qos>&lt;ForceQueuing />&lt;/qos> is set.
    */
   public void testPtUnknownDestination()
   {
      {
         if (Log.TRACE) Log.trace(ME, "Testing point to a unknown destination with NO <ForceQueuing /> set ...");

         // Construct a message and send it to "Martin Unknown"
         String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                         "</key>";

         String qos = "<qos>" +
                      "   <destination queryType='EXACT'>" +
                              receiverName +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         MessageUnit messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
         try {
            publishOid = senderXmlBlaster.publish(messageUnit, qos);
            Log.error(ME, "Publishing to a not logged in client should throw an exception");
            assert("Publishing to a not logged in client should throw an exception", false);
         } catch(XmlBlasterException e) {
            Log.info(ME, "Exception is correct, client is not logged in");
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;
      }

      {
         if (Log.TRACE) Log.trace(ME, "Testing point to a unknown destination with <ForceQueuing /> set ...");

         // Construct a message and send it to "Martin Unknown"
         String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                         "</key>";

         String qos = "<qos>" +
                      "   <destination queryType='EXACT'>" +
                              receiverName +
                      "      <ForceQueuing />" +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         MessageUnit messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
         try {
            publishOid = senderXmlBlaster.publish(messageUnit, qos);
            Log.info(ME, "Sending done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            Log.error(ME, "publish() XmlBlasterException: " + e.reason);
            assert("publish - XmlBlasterException: " + e.reason, false);
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;

         // Now the receiver logs in, and should get the message from the xmlBlaster queue ...
         try {
            receiverConnection = new CorbaConnection();
            receiverXmlBlaster = receiverConnection.login(receiverName, passwd, "<qos></qos>", this);
         } catch (XmlBlasterException e) {
             Log.error(ME, e.toString());
             e.printStackTrace();
             assert("login - XmlBlasterException: " + e.reason, false);
             return;
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after '" + receiverName + "' logged in", 1, numReceived); // message arrived?
         numReceived = 0;
      }
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
       suite.addTest(new TestPtDQueue("testPtUnknownDestination"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPtDQueue
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue</code>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestPtDQueue testSub = new TestPtDQueue("TestPtDQueue");
      testSub.setUp();
      testSub.testPtUnknownDestination();
      testSub.tearDown();
      Log.exit(TestPtDQueue.ME, "Good bye");
   }
}
