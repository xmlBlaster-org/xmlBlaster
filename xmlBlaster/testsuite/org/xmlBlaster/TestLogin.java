/*------------------------------------------------------------------------------
Name:      TestLogin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestLogin.java,v 1.16 2000/10/18 20:45:44 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client does test login and logout.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestLogin
 * </pre>
 */
public class TestLogin extends TestCase implements I_Callback
{
   private static String ME = "Tim";

   private String publishOid = "";
   private String oid = "TestLogin";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String senderContent;

   private XmlBlasterConnection secondConnection;
   private String secondName;
   private String secondOid = "SecondOid";

   private MessageUnit msgUnit;     // a message to play with

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param loginName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   public TestLogin(String testName, String senderName, String secondName)
   {
       super(testName);
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
         senderConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";

         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster

         secondConnection = new XmlBlasterConnection(); // Find orb
         secondConnection.login(secondName, passwd, null, this); // Login to xmlBlaster

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
            strArr = senderConnection.erase(xmlKey, qos);
         } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      }

      {
         String xmlKey = "<key oid='" + secondOid + "' queryType='EXACT'>\n</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = senderConnection.erase(xmlKey, qos);
         } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      }

      senderConnection.logout();
      secondConnection.logout();
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
         subscribeOid = senderConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-testSubscribeXPath", "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
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
         publishOid = senderConnection.publish(msgUnit);
         assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME+"-testPublish", "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      assert("returned publishOid == null", publishOid != null);
      assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testLoginLogout()
   {
      // test ordinary login
      numReceived = 0;
      testSubscribeXPath();
      testPublish(false);
      waitOnUpdate(1000L, 1);              // message arrived?

      // login again, without logout
      setUp();
      testPublish(true);                   // sending directly PtP to 'receiver'
      waitOnUpdate(1000L, 1);              // message arrived?

      // login again, without logout
      setUp();
      testPublish(false);
      try { Thread.currentThread().sleep(1000L); } catch (Exception e) { } // wait a second
      assertEquals("Didn't expect an update", 0, numReceived);
      numReceived = 0;
      testSubscribeXPath();
      waitOnUpdate(1000L, 1);              // message arrived?

      // test publish from other user
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
         assert("second - publish - XmlBlasterException: " + e.reason, false);
      }
      waitOnUpdate(1000L, 1);              // message arrived?


      assert("returned publishOid == null", publishOid != null);
      assertNotEquals("returned publishOid", 0, publishOid.length());
      // test logout with following subscribe()
      senderConnection.logout();
      try {
         publishOid = senderConnection.publish(msgUnit);
         assert("Didn't expect successful subscribe after logout", false);
      } catch(XmlBlasterException e) {
         Log.info(ME, "Success: " + e.toString());
      }
      try { Thread.currentThread().sleep(1000L); } catch (Exception e) { } // wait a second
      assertEquals("Didn't expect an update", 0, numReceived);

      // login again
      setUp();

   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterConnection
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
      if (Log.CALL) Log.call(ME, "Receiving update of a message ...");
      numReceived++;
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
         assert("Timeout of " + timeout + " occurred without update", sum <= timeout);
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
       suite.addTest(new TestLogin("testLoginLogout", "Tim", "Joe"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestLogin
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestLogin testSub = new TestLogin("TestLogin", "Tim", "Joe");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
      Log.exit(TestLogin.ME, "Good bye");
   }
}

