/*------------------------------------------------------------------------------
Name:      TestLogin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: TestLogin.java,v 1.1 2000/01/11 00:29:43 ruff Exp $
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
 * This client does test login, logout, login without logout.<br />
 * Successful login/logout is checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestLogin
 * </code>
 */
public class TestLogin extends TestCase implements I_Callback
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";

   private String publishOid = "";
   private String oid = "TestLogin";
   private CorbaConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private MessageUnit messageUnit;     // a message to play with

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestLogin object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestLogin(String testName, String loginName)
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
         String qos = "<qos></qos>";
         xmlBlaster = senderConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster

         // a sample message unit
         String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "   <TestLogin-AGENT>" +
                         "   </TestLogin-AGENT>" +
                         "</key>";
         senderContent = "Some content";
         messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
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
      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = xmlBlaster.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME+"-tearDown()", "XmlBlasterException in erase(): " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout(xmlBlaster);
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
         subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME+"-testSubscribeXPath", "XmlBlasterException: " + e.reason);
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
   public void testPublish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      try {
         publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
         assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         Log.warning(ME+"-testPublish", "XmlBlasterException: " + e.reason);
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
      testSubscribeXPath();
      testPublish();
      waitOnUpdate(1000L, 1);              // message arrived?

      // login again, without logout
      setUp();
      testPublish();
      try { Thread.currentThread().sleep(1000L); } catch (Exception e) { } // wait a second
      assertEquals("Didn't expect an update", 0, numReceived);
      testSubscribeXPath();
      waitOnUpdate(1000L, 1);              // message arrived?

      // test logout with following subscribe()
      senderConnection.logout(xmlBlaster);
      try {
         publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
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
       suite.addTest(new TestLogin("testLoginLogout", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestLogin
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestLogin</code>
    */
   public static void main(String args[])
   {
      TestLogin testSub = new TestLogin("TestLogin", "Tim");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
      Log.exit(TestLogin.ME, "Good bye");
   }
}

