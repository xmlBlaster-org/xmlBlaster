/*------------------------------------------------------------------------------
Name:      TestLoginLogoutEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import java.util.StringTokenizer;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests for login/logout events of other clients.
 * <p />
 * There are two internal messages which hold login and logout events.
 * You can subscribe to "__sys__Login" to be notified when a new client logs in,
 * and to "__sys__Logout" to be notified when a client logs out.
 * The message content contains the unique login name of this client.
 * <p />
 * Tests the '__sys__UserList' feature as well.
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.authentication.TestLoginLogoutEvent
 *    java junit.swingui.TestRunner org.xmlBlaster.test.authentication.TestLoginLogoutEvent
 * </pre>
 */
public class TestLoginLogoutEvent extends TestCase implements I_Callback
{
   private static String ME = "TestLoginLogoutEvent";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection firstConnection;
   private String firstName;

   private XmlBlasterConnection secondConnection;
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
   public TestLoginLogoutEvent(Global glob, String testName, String firstName, String secondName)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
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
         firstConnection = new XmlBlasterConnection(glob); // Find orb
         ConnectQos qos = new ConnectQos(glob, firstName, passwd);
         firstConnection.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, e.toString());
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
      if (firstConnection != null) {
         try {
            firstConnection.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            log.warn(ME+"-subscribe", "XmlBlasterException: " + e.getMessage());
            assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
         }

         firstConnection.disconnect(null);
         firstConnection = null;
      }

      if (secondConnection != null) {
         secondConnection.disconnect(null);
         secondConnection = null;
      }
   }


   /**
    * Subscribe to login events with oid="__sys__Login" or "__sys__Logout"
    */
   public void subscribe(String oid)
   {
      if (log.TRACE) log.trace(ME, "Subscribing to login events ...");
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         String subscribeOid = firstConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeOid", subscribeOid != null);
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME+"-subscribe", "XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
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
      expectedName = null;        // no check (the logout event exists with AllTests but not when this test is run alone
      subscribe("__sys__Logout");
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}          // no check

      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      try {
         secondConnection = new XmlBlasterConnection(); // Find orb
         ConnectQos qos = new ConnectQos(glob, secondName, passwd); // == "<qos></qos>";
         secondConnection.connect(qos, this); // Login to xmlBlaster
         waitOnUpdate(1000L, 1);  // login event arrived?

         // Test the '__sys__UserList' feature:
         MsgUnit[] msgArr = secondConnection.get(
                          "<key oid='__sys__UserList' queryType='EXACT'></key>",
                          "<qos></qos>");
         assertTrue("Expected on __sys__UserList", msgArr.length == 1);
         String clients = new String(msgArr[0].getContent());
         log.info(ME, "Current '__sys__UserList' is\n" + clients);
         StringTokenizer st = new StringTokenizer(clients);
         int found = 0;
         while (st.hasMoreTokens()) {
            String client = (String)st.nextToken();
            if (client.equals(this.firstName))
               found++;
            else if (client.equals(this.secondName))
               found++;
         }
         assertTrue("Check of '__sys__UserList' failed", found==2);
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.getMessage());
         assertTrue("Second login failed", false);
      }


      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      secondConnection.disconnect(null);
      secondConnection = null;

      waitOnUpdate(2000L, 1);    // expecting a logout event message
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      String name = new String(content);
      if (name.startsWith("_"))
         return "";  // Ignore internal logins from plugins
      numReceived++;
      log.info(ME, cbSessionId + " - Receiving update of a message " + updateKey.getOid() + ", event for client " + name);

      if (expectedName != null)
         assertEquals("Wrong login name returned", expectedName, name);
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
       suite.addTest(new TestLoginLogoutEvent(new Global(), "testLoginLogout", "Gesa", "Ben"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.authentication.TestLoginLogoutEvent
    */
   public static void main(String args[])
   {
      TestLoginLogoutEvent testSub = new TestLoginLogoutEvent(new Global(args), "TestLoginLogoutEvent", "Tim", "Joe");
      testSub.setUp();
      testSub.testLoginLogout();
      testSub.tearDown();
   }
}

