/*------------------------------------------------------------------------------
Name:      TestLogin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestLogin.java,v 1.22 2002/05/09 11:54:53 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

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
 *    java test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin
 *    java test.ui.TestRunner testsuite.org.xmlBlaster.TestLogin
 * </pre>
 */
public class TestLogin extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;

   private String publishOid = "";
   private String oid = "TestLogin";
   private XmlBlasterConnection callbackConnection;
   private String senderName;
   private String senderContent;

   private XmlBlasterConnection secondConnection;
   private String secondName;
   private String secondOid = "SecondOid";

   private MessageUnit msgUnit;     // a message to play with

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
      try {
         String passwd = "secret";

         callbackConnection = new XmlBlasterConnection(glob); // Find orb
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         callbackConnection.connect(qos, this); // Login to xmlBlaster

         secondConnection = new XmlBlasterConnection(); // Find orb
         qos = new ConnectQos(glob, secondName, passwd);
         secondConnection.connect(qos, this);

         // a sample message unit
         String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "   <TestLogin-AGENT>" +
                         "   </TestLogin-AGENT>" +
                         "</key>";
         senderContent = "Some content";
         msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
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
      {
         String xmlKey = "<key oid='" + oid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = callbackConnection.erase(xmlKey, qos);
         } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
         if (strArr != null && strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      }

      {
         String xmlKey = "<key oid='" + secondOid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = callbackConnection.erase(xmlKey, qos);
         } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      }

      callbackConnection.disconnect(null);
      secondConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestLogin-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      String subscribeOid = null;
      try {
         subscribeOid = callbackConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-testSubscribeXPath", "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    * @param ptp Use the Point to Point style
    */
   public void testPublish(boolean ptp)
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      msgUnit.qos = "<qos></qos>";
      if (ptp)
         msgUnit.qos = "<qos>\n<destination>\n" + secondName + "\n</destination>\n</qos>";
      try {
         publishOid = callbackConnection.publish(msgUnit);
         Log.info(ME, "Success: Publish " + msgUnit.getXmlKey() + " done");
         assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-testPublish", "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
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
      Log.info(ME, "TEST 1: Subscribe and publish -> Expecting one update");
      numReceived = 0;
      testSubscribeXPath();
      testPublish(IS_PUBSUB);
      waitOnUpdate(2000L, 1);              // message arrived?

      Log.info(ME, "TEST 2: Login again without logout and publish PtP -> Expecting one update");
      setUp();
      testPublish(IS_PTP);                 // sending directly PtP to 'receiver'
      waitOnUpdate(2000L, 2);              // 2 times logged in, 2 messages arrived?

      Log.info(ME, "TEST 3: Login again without logout and publish Pub/Sub -> Expecting no update");
      setUp();
      testPublish(IS_PUBSUB);
      waitOnUpdate(2000L, 1);              // 1 times subscribed (TEST 1), 1 messages arrived?
      numReceived = 0;

      Log.info(ME, "TEST 4: Now subscribe -> Expecting one update");
      numReceived = 0;
      testSubscribeXPath();
      waitOnUpdate(2000L, 1);              // message arrived?

      Log.info(ME, "TEST 5: Test publish from other user -> Expecting one update");
      numReceived = 0;
      try {
         // a sample message unit
         String xmlKey = "<key oid='" + secondOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "   <TestLogin-AGENT>" +
                         "   </TestLogin-AGENT>" +
                         "</key>";
         String content = "Some content";
         MessageUnit mu = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         publishOid = secondConnection.publish(mu);
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-secondPublish", "XmlBlasterException: " + e.reason);
         assertTrue("second - publish - XmlBlasterException: " + e.reason, false);
      }
      waitOnUpdate(2000L, 2); // 2 messages (we have subscribed 2 times, and the old session remained on relogin)

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());

      Log.info(ME, "TEST 6: Test logout with following publish -> Should not be possible");
      // test logout with following subscribe()
      callbackConnection.logout();
      try {
         publishOid = callbackConnection.publish(msgUnit);
         assertTrue("Didn't expect successful publish after logout", false);
      } catch(XmlBlasterException e) {
         Log.info(ME, "Success got exception for publishing after logout: " + e.toString());
      }
      try { Thread.currentThread().sleep(1000L); } catch (Exception e) { } // wait a second
      assertEquals("Didn't expect an update", 0, numReceived);

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
      Log.info(ME, "Receiving update of a message " + updateKey.getUniqueKey());
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
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
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
    * Invoke: java testsuite.org.xmlBlaster.TestLogin
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestLogin testSub = new TestLogin(glob, "TestLogin", "Tim", "Joe");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
      Log.exit(TestLogin.ME, "Good bye");
   }
}

