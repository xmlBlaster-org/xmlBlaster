/*------------------------------------------------------------------------------
Name:      TestPtDQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtDQueue.java,v 1.30 2002/09/09 13:39:53 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style, William sends to Averell a message.
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * William is the 'sender' and Averell the 'receiver'<br />
 * Averell is not online when William sends the message, and will receive the message
 * from her queue in the xmlBlaster when she logs in.
 * <p>
 * A second test checks if there is an Exception thrown, if the receiver
 * is not logged in and the <forceQueuing> is not set.
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue
 *
 *    java junit.swingui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue
 * </pre>
 */
public class TestPtDQueue extends TestCase implements I_Callback
{
   private final static String ME = "TestPtDQueue";
   private final Global glob;

   private final String senderName = "William";
   private String publishOid = "";
   private XmlBlasterConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Averell";
   private XmlBlasterConnection receiverConnection = null;

   private String passwd = "secret";

   private int numReceived = 0;
   private boolean messageArrived = false;


   /**
    * Constructs the TestPtDQueue object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPtDQueue(Global glob, String testName)
   {
      super(testName);
      this.glob = glob;
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
         senderConnection = new XmlBlasterConnection(glob);
         senderConnection.connect(new ConnectQos(glob, senderName, passwd), this);
         Log.info(ME, "Successful login for " + senderName);
      }
      catch (XmlBlasterException e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
          assertTrue("login - XmlBlasterException: " + e.reason, false);
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
      receiverConnection.disconnect(null);
      senderConnection.disconnect(null);
   }


   /**
    * TEST: Sending a message to a not logged in client, which logs in later.
    * <p />
    * The sent message will be stored in a xmlBlaster queue for this client and than delivered
    * only if the &lt;destination forceQueuing='true' is set.
    */
   public void testPtUnknownDestination()
   {
      {
         Log.info(ME, "Testing point to a unknown destination with NO forceQueuing set ...");

         // Construct a message and send it to "Averell"
         String xmlKey = "<key oid='' contentMime='text/plain'/>";
         String qos = "<qos>" +
                      "   <destination queryType='EXACT' forceQueuing='false'>" +
                              receiverName +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
         try {
            publishOid = senderConnection.publish(msgUnit).getOid();
            Log.error(ME, "Publishing to a not logged in client should throw an exception, forceQueuing is not set");
            assertTrue("Publishing to a not logged in client should throw an exception, forceQueuing is not set", false);
         } catch(XmlBlasterException e) {
            Log.info(ME, "Exception is correct, client is not logged in");
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;
      }

      {
         Log.info(ME, "Testing point to a unknown destination with forceQueuing set ...");

         // Construct a message and send it to "Martin Unknown"
         String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                         "</key>";

         String qos = "<qos>" +
                      "   <destination queryType='EXACT' forceQueuing='true'>" +
                              receiverName +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
         try {
            publishOid = senderConnection.publish(msgUnit).getOid();
            Log.info(ME, "Sending done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            Log.error(ME, "publish() XmlBlasterException: " + e.reason);
            assertTrue("publish - XmlBlasterException: " + e.reason, false);
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;

         // Now the receiver logs in and should get the message from the xmlBlaster queue ...
         try {
            receiverConnection = new XmlBlasterConnection(glob);
            receiverConnection.login(receiverName, passwd, new ConnectQos(glob), this);
         } catch (XmlBlasterException e) {
             Log.error(ME, e.toString());
             e.printStackTrace();
             assertTrue("login - XmlBlasterException: " + e.reason, false);
             return;
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after '" + receiverName + "' logged in", 1, numReceived); // message arrived?
         numReceived = 0;
      }
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (Log.CALL) Log.call(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));

      messageArrived = true;
      return "";
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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
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
       suite.addTest(new TestPtDQueue(new Global(), "testPtUnknownDestination"));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestPtDQueue
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestPtDQueue</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestPtDQueue testSub = new TestPtDQueue(glob, "TestPtDQueue");
      testSub.setUp();
      testSub.testPtUnknownDestination();
      testSub.tearDown();
      Log.exit(TestPtDQueue.ME, "Good bye");
   }
}
