/*------------------------------------------------------------------------------
Name:      TestLoginLogoutEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout event test for xmlBlaster
Version:   $Id: TestLoginLogoutEvent.java,v 1.6 2000/06/20 13:32:58 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.Log;

import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;

import test.framework.*;


/**
 * This client tests for login/logout events of other clients.
 * <p />
 * There are two internal messages which hold login and logout events.
 * You can subscribe to "__sys__Login" to be notified when a new client logs in,
 * and to "__sys__Logout" to be notified when a client logs out.
 * The message content contains the unique login name of this client.
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestLoginLogoutEvent
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestLoginLogoutEvent
 * </pre>
 */
public class TestLoginLogoutEvent extends TestCase implements I_Callback
{
   private static String ME = "TestLoginLogoutEvent";

   private CorbaConnection firstConnection;
   private Server xmlBlaster = null;
   private String firstName;

   private CorbaConnection secondConnection;
   private Server secondBlaster;
   private String secondName;

   private String expectedName;

   private String passwd = "secret";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestLoginLogoutEvent object.
    * <p />
    * @param testName   The name used in the test suite
    * @param firstName  The name to login to the xmlBlaster
    * @param secondName The name to login to the xmlBlaster again
    */
   public TestLoginLogoutEvent(String testName, String firstName, String secondName)
   {
       super(testName);
       this.firstName = firstName;
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
         firstConnection = new CorbaConnection(); // Find orb
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         xmlBlaster = firstConnection.login(firstName, passwd, qos, this); // Login to xmlBlaster
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
      String xmlKey = "<key oid='__sys__Logout' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         xmlBlaster.unSubscribe(xmlKey, qos);
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warning(ME+"-subscribe", "XmlBlasterException: " + e.reason);
         assert("unSubscribe - XmlBlasterException: " + e.reason, false);
      }

      firstConnection.logout();
   }


   /**
    * Subscribe to login events with oid="__sys__Login" or "__sys__Logout"
    */
   public void subscribe(String oid)
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing to login events ...");
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      String subscribeOid = null;
      try {
         subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(org.xmlBlaster.protocol.corba.serverIdl.XmlBlasterException e) {
         Log.warning(ME+"-subscribe", "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      assertEquals("returned subscribeOid is wrong", oid, subscribeOid);
   }


   /**
    * TEST: Test to receive a login and a logout event.
    */
   public void testLoginLogout()
   {
      numReceived = 0;
      expectedName = firstName;   // my first login name should be returned on this subscribe
      subscribe("__sys__Login");
      waitOnUpdate(1000L, 1);     // expecting a login event message (the login event message exists from my own login)

      numReceived = 0;
      expectedName = null;        // no check (the logout event exists with TestAll but not when this test is run alone
      subscribe("__sys__Logout");
      Util.delay(1000L);          // no check

      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      try {
         secondConnection = new CorbaConnection(); // Find orb
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         secondBlaster = secondConnection.login(secondName, passwd, qos, this); // Login to xmlBlaster
         waitOnUpdate(1000L, 1);  // login event arrived?
      }
      catch (XmlBlasterException e) {
         Log.error(ME, e.id + ": " + e.reason);
         assert("Second login failed", false);
      }


      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      secondConnection.logout();
      waitOnUpdate(2000L, 1);    // expecting a logout event message
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
      numReceived++;
      String name = new String(content);
      Log.info(ME, loginName + " - Receiving update of a message " + updateKey.getUniqueKey() + ", event for client " + name);

      if (expectedName != null)
         assertEquals("Wrong login name returned", expectedName, name);
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
       suite.addTest(new TestLoginLogoutEvent("testLoginLogout", "Tim", "Joe"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestLoginLogoutEvent
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestLoginLogoutEvent</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestLoginLogoutEvent testSub = new TestLoginLogoutEvent("TestLoginLogoutEvent", "Tim", "Joe");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
      Log.exit(TestLoginLogoutEvent.ME, "Good bye");
   }
}

