/*------------------------------------------------------------------------------
Name:      TestPtDQueue.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style, William sends to Averell a message.
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * William is the 'sender' and Averell the 'receiver'<br />
 * Averell is not online when William sends the message, and will receive the message
 * from his queue in the xmlBlaster when he logs in.
 * <p>
 * A second test checks if there is an Exception thrown, if the receiver
 * is not logged in and the <forceQueuing> is not set.
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtDQueue
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtDQueue
 * </pre>
 */
public class TestPtDQueue extends TestCase implements I_Callback
{
   private final static String ME = "TestPtDQueue";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPtDQueue.class.getName());

   private final String senderName = "William";
   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection = null;
   private String senderContent;

   private final String receiverName = "Averell";
   private I_XmlBlasterAccess receiverConnection = null;

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
         senderConnection = glob.getClone(null).getXmlBlasterAccess();
         senderConnection.connect(new ConnectQos(senderConnection.getGlobal(), senderName, passwd), this);
         log.info("Successful login for " + senderName);
      }
      catch (XmlBlasterException e) {
          log.severe(e.toString());
          e.printStackTrace();
          assertTrue("login - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown()
   {
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
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
         log.info("[1] Testing point to a unknown destination with NO forceQueuing set ...");

         // Construct a message and send it to "Averell"
         String xmlKey = "<key oid='' contentMime='text/plain'/>";
         String qos = "<qos>" +
                      "   <destination queryType='EXACT' forceQueuing='false'>" +
                              receiverName +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         try {
            MsgUnit msgUnit = new MsgUnit(senderConnection.getGlobal(), xmlKey, senderContent.getBytes(), qos);
            publishOid = senderConnection.publish(msgUnit).getKeyOid();
            log.severe("Publishing to a not logged in client should throw an exception, forceQueuing is not set");
            assertTrue("Publishing to a not logged in client should throw an exception, forceQueuing is not set", false);
         } catch(XmlBlasterException e) {
            log.info("Exception is correct, client is not logged in: " + e.getMessage());
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;
      }

      {
         log.info("[2] Testing point to a unknown destination with forceQueuing set ...");

         // Construct a message and send it to "Martin Unknown"
         String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                         "</key>";

         String qos = "<qos>" +
                      "   <destination queryType='EXACT' forceQueuing='true'>" +
                              receiverName +
                      "   </destination>" +
                      "</qos>";

         senderContent = "Hi " + receiverName + ", who are you? " + senderName;
         try {
            MsgUnit msgUnit = new MsgUnit(senderConnection.getGlobal(), xmlKey, senderContent.getBytes(), qos);
            publishOid = senderConnection.publish(msgUnit).getKeyOid();
            log.info("Sending done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            log.severe("publish() XmlBlasterException: " + e.getMessage());
            assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
         }

         waitOnUpdate(1000L);
         assertEquals("numReceived after sending to '" + receiverName + "'", 0, numReceived); // no message?
         numReceived = 0;

         log.info("[3] Now the receiver '" + receiverName + "' logs in and should get the message '" + publishOid + "' from the xmlBlaster queue ...");

         // Now the receiver logs in and should get the message from the xmlBlaster queue ...
         try {
            receiverConnection = glob.getClone(null).getXmlBlasterAccess();
            ConnectQos connectQos = new ConnectQos(receiverConnection.getGlobal(), receiverName, passwd);
            receiverConnection.connect(connectQos, this); // Login to xmlBlaster
         } catch (XmlBlasterException e) {
             log.severe(e.toString());
             e.printStackTrace();
             assertTrue("login - XmlBlasterException: " + e.getMessage(), false);
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
      log.info("Receiving update of a message '" + updateKey.getOid() + "' state=" + updateQos.getState() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
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
            Thread.sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warning("Timeout of " + timeout + " occurred");
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
    * Invoke: java org.xmlBlaster.test.qos.TestPtDQueue
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtDQueue</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPtDQueue testSub = new TestPtDQueue(glob, "TestPtDQueue");
      testSub.setUp();
      testSub.testPtUnknownDestination();
      testSub.tearDown();
   }
}
