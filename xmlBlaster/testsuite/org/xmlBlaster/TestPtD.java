/*------------------------------------------------------------------------------
Name:      TestPtD.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster and publishing to destinations
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtD.java,v 1.2 1999/12/13 12:20:09 ruff Exp $
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
 * This client tests the PtP (or PtD = point to destination) style, Manuel sends to Ulrike a love letter.
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * Manuel is the 'sender' and Ulrike the 'receiver'
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPtD
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPtD
 * </code>
 */
public class TestPtD extends TestCase implements I_Callback
{
   private Server senderXmlBlaster = null;
   private final static String ME = "TestPtD";
   private final String[] args;

   private final String senderName = "Manuel";
   private String publishOid = "";
   private CorbaConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private CorbaConnection receiverConnection = null;
   private Server receiverXmlBlaster = null;
   private int numReceived = 0;

   private boolean messageArrived = false;


   /**
    * Constructs the TestPtD object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param args      Array of command line start parameters
    */
   public TestPtD(String testName, String[] args)
   {
       super(testName);
       this.args = args;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    * - One connection for the receiver client
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";

         receiverConnection = new CorbaConnection(args);
         receiverXmlBlaster = receiverConnection.login(receiverName, passwd, "<qos></qos>", this);

         senderConnection = new CorbaConnection(args);
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
      receiverConnection.logout(receiverXmlBlaster);
      senderConnection.logout(senderXmlBlaster);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
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

      waitOnUpdate(5000L);
      assertEquals("numReceived after sending", 1, numReceived); // message arrived?
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
    * @param keyOid    the unique message key for your convenience (redundant to updateKey.getUniqueKey())
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, String keyOid, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong receveiver", receiverName, loginName);
      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Wrong oid of message returned", publishOid, keyOid);
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

       String[] args = new String[0];  // dummy

       suite.addTest(new TestPtD("testPtOneDestination", args));

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
      TestPtD testSub = new TestPtD("TestPtD", args);
      testSub.setUp();
      testSub.testPtOneDestination();
      testSub.tearDown();
      Log.exit(TestPtD.ME, "Good bye");
   }
}
