/*------------------------------------------------------------------------------
Name:      TestPtD.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtD.java,v 1.1 2002/09/12 21:01:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


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
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtD
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestPtD
 * </pre>
 */
public class TestPtD extends TestCase implements I_Callback
{
   private final static String ME = "TestPtD";
   private final Global glob;

   private final String senderName = "Manuel";
   private String publishOid = "";
   private XmlBlasterConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private XmlBlasterConnection receiverConnection = null;

   private final String receiver2Name = "KGB";
   private XmlBlasterConnection receiver2Connection = null;

   private int numReceived = 0;


   /**
    * Constructs the TestPtD object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPtD(Global glob, String testName)
   {
       super(testName);
      this.glob = glob;
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

         receiverConnection = new XmlBlasterConnection(glob);
         receiverConnection.connect(new ConnectQos(glob, receiverName, passwd), this);

         receiver2Connection = new XmlBlasterConnection();
         receiver2Connection.connect(new ConnectQos(glob, receiver2Name, passwd), this);

         senderConnection = new XmlBlasterConnection();
         senderConnection.connect(new ConnectQos(glob, senderName, passwd), this);
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
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
      receiverConnection.disconnect(null);
      receiver2Connection.disconnect(null);
      senderConnection.disconnect(null);
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
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      try {
         publishOid = senderConnection.publish(msgUnit).getOid();
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
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
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      try {
         publishOid = senderConnection.publish(msgUnit).getOid();
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      waitOnUpdate(5000L, 2);
      assertEquals("numReceived after sending", 2, numReceived); // message arrived at receiver and receiver2 ?
      numReceived = 0;
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

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      return "";
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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
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
       Global glob = new Global();
       suite.addTest(new TestPtD(glob, "testPtOneDestination"));
       suite.addTest(new TestPtD(glob, "testPtManyDestinations"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestPtD
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtD</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestPtD testSub = new TestPtD(glob, "TestPtD");
      testSub.setUp();
      testSub.testPtOneDestination();
      testSub.tearDown();
      Log.exit(TestPtD.ME, "Good bye");
   }
}
