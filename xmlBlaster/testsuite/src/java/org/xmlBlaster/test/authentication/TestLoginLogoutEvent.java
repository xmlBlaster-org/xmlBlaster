/*------------------------------------------------------------------------------
Name:      TestLoginLogoutEvent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import java.util.StringTokenizer;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.def.Constants;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

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
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.TestLoginLogoutEvent
 * </pre>
 */
public class TestLoginLogoutEvent extends TestCase
{
   private static String ME = "TestLoginLogoutEvent";
   private Global glob;
   private static Logger log = Logger.getLogger(TestLoginLogoutEvent.class.getName());

   private I_XmlBlasterAccess firstConnection;
   private String firstName;

   private I_XmlBlasterAccess secondConnection;
   private String secondName;

   private String expectedName;

   private String passwd = "secret";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   private MsgInterceptor updateInterceptFirst; // collects updated messages
   private MsgInterceptor updateInterceptSecond; // collects updated messages

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
         Global firstGlob = glob.getClone(null);
         firstConnection = firstGlob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(firstGlob, firstName, passwd);
         this.updateInterceptFirst = new MsgInterceptor(firstGlob, log, null);
         firstConnection.connect(qos, this.updateInterceptFirst); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }

      this.updateInterceptFirst.clear();
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
      this.numReceived = 0;
      if (this.firstConnection != null) {
         try {
            this.firstConnection.unSubscribe(xmlKey, qos);
         } catch(XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
            assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
         }

         this.firstConnection.disconnect(null);
         this.firstConnection = null;
      }

      if (this.secondConnection != null) {
         this.secondConnection.disconnect(null);
         this.secondConnection = null;
      }
      this.glob = null;
      this.log = null;
      Global.instance().shutdown();
   }


   /**
    * Subscribe to login events with oid="__sys__Login" or "__sys__Logout"
    */
   public void subscribe(String oid)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing to login events ...");
      String xmlKey = "<key oid='" + oid + "' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         String subscribeOid = firstConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeOid", subscribeOid != null);
         log.info("Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Test to receive a login and a logout event.
    */
   public void testLoginLogout()
   {
      long sleep = 1000L;

      numReceived = 0;
      expectedName = firstName;   // my first login name should be returned on this subscribe
      subscribe("__sys__Login");
      // expecting a login event message (the login event message exists from my own login)
      assertEquals("Missing my login event", 1, this.updateInterceptFirst.waitOnUpdate(sleep, "__sys__Login", Constants.STATE_OK));
      {
         String content = this.updateInterceptFirst.getMsgs()[0].getContentStr();
         log.info("Received login event for " + content);
         assertEquals("Wrong login name", expectedName, content);
         this.updateInterceptFirst.clear();
      }

      numReceived = 0;
      expectedName = null;        // no check (the logout event exists with AllTests but not when this test is run alone
      subscribe("__sys__Logout");
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}          // no check
      this.updateInterceptFirst.clear();

      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      try {
         Global secondGlob = glob.getClone(null);
         this.secondConnection = secondGlob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(secondGlob, secondName, passwd); // == "<qos></qos>";
         this.updateInterceptSecond = new MsgInterceptor(secondGlob, log, null);
         this.secondConnection.connect(qos, this.updateInterceptSecond); // Login to xmlBlaster
         
         // login event arrived?
         assertEquals("Missing my login event", 1, this.updateInterceptFirst.waitOnUpdate(sleep, "__sys__Login", Constants.STATE_OK));
         String content = this.updateInterceptFirst.getMsgs()[0].getContentStr();
         log.info("Received login event for " + content);
         assertEquals("Wrong login name", expectedName, content);
         this.updateInterceptFirst.clear();

         assertEquals("Not expected update for second con", 0, this.updateInterceptSecond.waitOnUpdate(500L));
         this.updateInterceptSecond.clear();

         // Test the '__sys__UserList' feature:
         MsgUnit[] msgArr = secondConnection.get(
                          "<key oid='__sys__UserList' queryType='EXACT'></key>",
                          "<qos></qos>");
         assertTrue("Expected on __sys__UserList", msgArr.length == 1);
         String clients = new String(msgArr[0].getContent());
         log.info("Current '__sys__UserList' is\n" + clients);
         StringTokenizer st = new StringTokenizer(clients, ",");  // joe,jack,averell,...
         int found = 0;
         while (st.hasMoreTokens()) {
            String client = (String)st.nextToken();
            log.info("Parsing name=" + client);
            SessionName sessionName = new SessionName(glob, client);
            if (sessionName.getLoginName().equals(this.firstName))
               found++;
            else if (sessionName.getLoginName().equals(this.secondName))
               found++;
         }
         assertTrue("Check of '__sys__UserList' failed", found==2);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
         assertTrue("Second login failed", false);
      }


      numReceived = 0;
      expectedName = secondName; // second name should be returned on this login
      secondConnection.disconnect(null);
      secondConnection = null;

      // expecting a logout event message
      {
         assertEquals("Missing my logout event", 1, this.updateInterceptFirst.waitOnUpdate(sleep, "__sys__Logout", Constants.STATE_OK));
         String content = this.updateInterceptFirst.getMsgs()[0].getContentStr();
         log.info("Received logout event for " + content);
         assertEquals("Wrong logout name", expectedName, content);
         this.updateInterceptFirst.clear();
      }

      assertEquals("Not expected update for second con", 0, this.updateInterceptSecond.waitOnUpdate(500L));
      this.updateInterceptSecond.clear();
   }

   /** ----> see this.updateInterceptFirst
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      String name = new String(content);
      if (name.startsWith("_"))
         return "";  // Ignore internal logins from plugins
      numReceived++;
      log.info(cbSessionId + " - Receiving update of a message " + updateKey.getOid() + ", event for client " + name);

      if (expectedName != null)
         assertEquals("Wrong login name returned", expectedName, name);
      return "";
   }
    */

   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
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

    */

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

