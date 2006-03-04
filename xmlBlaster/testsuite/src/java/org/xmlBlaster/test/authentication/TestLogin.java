/*------------------------------------------------------------------------------
Name:      TestLogin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.protocol.corba.serverIdl.Server;

import junit.framework.*;


/**
 * This client does test login and logout.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.authentication.TestLogin
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.TestLogin
 * </pre>
 */
public class TestLogin extends TestCase implements I_Callback
{
   private static String ME = "TestLogin";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestLogin.class.getName());

   private String publishOid = "";
   private String firstOid = "FirstOid";
   private I_XmlBlasterAccess callbackConnection;
   private String senderName;
   private String senderContent;

   private I_XmlBlasterAccess secondConnection;
   private String secondName;
   private String secondOid = "SecondOid";

   private MsgUnit msgUnit;     // a message to play with

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   private final boolean IS_PTP = true;
   private final boolean IS_PUBSUB = false;

   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param loginName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   public TestLogin(Global glob, String testName, String senderName, String secondName)
   {
      super(testName);
      this.glob = glob;

      this.senderName = senderName;
      this.secondName = secondName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      log.info("######## Entering setup");
      try {
         String passwd = "secret";

         callbackConnection = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         callbackConnection.connect(qos, this); // Login to xmlBlaster

         Global secondGlobal = glob.getClone(null);
         secondConnection = secondGlobal.getXmlBlasterAccess(); // Find orb
         qos = new ConnectQos(secondGlobal, secondName, passwd);
         secondConnection.connect(qos, this);

      }
      catch (XmlBlasterException e) {
         if (e.getErrorCode() == ErrorCode.USER_CONNECT_MULTIPLE) {
            // ignore
         }
         else {
            log.severe(e.toString());
            fail(ME + ".setup failed: " + e.toString());
         }
      }

      // a sample message unit
      String xmlKey = "<key oid='" + firstOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                        "   <TestLogin-AGENT>" +
                        "   </TestLogin-AGENT>" +
                        "</key>";
      senderContent = "Some content";
      try {
         msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      }
      catch (XmlBlasterException e) {
         log.severe("setup() failed: " + e.getMessage());
         fail(e.getMessage());
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      {
         String xmlKey = "<key oid='" + firstOid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = callbackConnection.erase(xmlKey, qos);
            if (arr != null && arr.length != 1) log.severe("Erased " + arr.length + " messages:");
            assertEquals("Wrong number of messages erased", 1, arr.length);
         } catch(XmlBlasterException e) {
            log.severe("XmlBlasterException in erase(): " + e.getMessage());
            fail(ME+"-tearDown() XmlBlasterException in erase(): " + e.getMessage());
         }
      }

      {
         String xmlKey = "<key oid='" + secondOid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = callbackConnection.erase(xmlKey, qos);
            if (arr.length != 1)
               log.severe("Erased " + arr.length + " messages of '" + secondOid + "'");
            assertEquals("Wrong number of messages '" + secondOid + "' erased", 1, arr.length);
         } catch(XmlBlasterException e) { 
            log.severe("XmlBlasterException in erase(): " + e.getMessage());
            fail(ME+"-tearDown() XmlBlasterException in erase(): " + e.getMessage());
         }
      }

      DisconnectQos qos = new DisconnectQos(glob);
      qos.clearSessions(true);
      callbackConnection.disconnect(qos);
      secondConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void doSubscribeXPath()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestLogin-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         String subscribeOid = callbackConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeOid", subscribeOid != null);
         assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
         log.info("Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    * @param ptp Use the Point to Point style
    */
   public void doPublish(boolean ptp)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      try {
         PublishQos publishQos = new PublishQos(glob);
         msgUnit = new MsgUnit(msgUnit, null, null, publishQos.getData());
         if (ptp) {
            PublishKey pk = new PublishKey(glob);
            PublishQos pq = new PublishQos(glob);
            pq.addDestination(new Destination(new SessionName(glob, secondName)));
            msgUnit = new MsgUnit(msgUnit, pk.getData(), null, pq.getData());
         }
         publishOid = callbackConnection.publish(msgUnit).getKeyOid();
         log.info("Success: Publish " + msgUnit.getKey() + " done");
         if (!ptp)
            assertEquals("oid is different", firstOid, publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testLoginLogout()
   {
      log.info("TEST 1: Subscribe and publish -> Expecting one update");
      numReceived = 0;
      doSubscribeXPath();
      doPublish(IS_PUBSUB);
      waitOnUpdate(2000L, 1);              // message arrived?

      log.info("TEST 2: Login again without logout and publish PtP -> Expecting one update");
      setUp();
      doPublish(IS_PTP);                 // sending directly PtP to 'receiver'
      waitOnUpdate(2000L, 1);              // 2 times logged in, 2 messages arrived?

      log.info("TEST 3: Login again without logout and publish Pub/Sub -> Expecting no update");
      setUp();
      doPublish(IS_PUBSUB);
      waitOnUpdate(2000L, 1);              // 1 times subscribed (TEST 1), 1 messages arrived?
      numReceived = 0;

      log.info("TEST 4: Now subscribe -> Expecting one update");
      numReceived = 0;
      doSubscribeXPath();
      waitOnUpdate(2000L, 1);              // message arrived?

      log.info("TEST 5: Test publish from other user -> Expecting one update");
      numReceived = 0;
      try {
         // a sample message unit
         String xmlKey = "<key oid='" + secondOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "   <TestLogin-AGENT>" +
                         "   </TestLogin-AGENT>" +
                         "</key>";
         String content = "Some content";
         MsgUnit mu = new MsgUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid = secondConnection.publish(mu).getKeyOid();
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("second - publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 2); // 2 messages (we have subscribed 2 times, and the old session remained on relogin)

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());

      log.info("TEST 6: Test logout with following publish -> Should not be possible");
      // test logout with following subscribe()
      callbackConnection.disconnect(null);
      try {
         publishOid = callbackConnection.publish(msgUnit).getKeyOid();
         assertTrue("Didn't expect successful publish after logout", false);
      } catch(XmlBlasterException e) {
         log.info("Success got exception for publishing after logout: " + e.toString());
      }
      try { Thread.currentThread().sleep(1000L); } catch (Exception e) { } // wait a second
      assertEquals("Didn't expect an update", 0, numReceived);

      log.info("SUCCESS in testLoginLogout()");

      // login again
      setUp();

   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid() + " state=" + updateQos.getState() +
               " rcvTime=" + updateQos.getRcvTimestamp().toString());
      log.info("Receiving update of a message " + updateKey.getOid() + updateQos.toXml());
      numReceived++;
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
      // check if too few are arriving
      while (numReceived < numWait) {
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.severe("Timeout of " + timeout + " occurred without update");
            Thread.currentThread().dumpStack();
         }
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
      if (numWait != numReceived) {
         log.severe("Wrong number of messages arrived, expected numWait=" + numWait + " but got numReceived=" + numReceived);
         Thread.currentThread().dumpStack();
      }
      assertEquals("Wrong number of messages arrived", numWait, numReceived);

      numReceived = 0;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestLogin(new Global(), "testLoginLogout", "Tim", "Joe"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.authentication.TestLogin
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.authentication.TestLogin</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestLogin testSub = new TestLogin(glob, "TestLogin", "Tim", "Joe");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
   }
}

