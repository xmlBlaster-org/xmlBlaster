/*------------------------------------------------------------------------------
Name:      TestUnSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestUnSub.java,v 1.11 2000/06/25 18:32:45 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests the method subscribe() with XPath query and with a later unSubscribe().
 * <br />
 * Multiple identical subscribes shouldn't harm and all following are ignored.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestUnSub
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestUnSub
 * </pre>
 */
public class TestUnSub extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "";
   private CorbaConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private String xpath = "//TestUnSub-AGENT";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestUnSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestUnSub(String testName, String loginName)
   {
       super(testName);
       this.senderName = loginName;
       this.receiverName = loginName;
   }


   /**
    * Sets up the fixture (login).
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = new CorbaConnection(); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture (logout).
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath  +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
   }


   /**
    * TEST: UnSubscribe to messages with XPATH.
    * <p />
    */
   public void testUnSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "UnSubscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         Log.info(ME, "Success: UnSubscribe with " + xpath + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("unSubscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: UnSubscribe to messages with EXACT oid (which was returned from our XPATH subscription).
    * <p />
    */
   public void testUnSubscribeExact()
   {
      if (Log.TRACE) Log.trace(ME, "UnSubscribing using EXACT syntax ...");

      String xmlKey = "<key oid='" + subscribeOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         Log.info(ME, "Success: UnSubscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("unSubscribe - XmlBlasterException: " + e.reason, false);
      }
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestUnSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestUnSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestUnSub-DRIVER>"+
                      "   </TestUnSub-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         publishOid = senderConnection.publish(msgUnit);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      assert("returned publishOid == null", publishOid != null);
      assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the returned oid.
    */
   public void testSubscribeUnSubscribeExact()
   {
      Log.info(ME, "Starting testSubscribeUnSubscribeExact()");
      numReceived = 0;
      testPublish();           // Feed some data
      testSubscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      testUnSubscribeExact();  // cancel XPATH subscription with XPATH-subscription-oid
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the same XPATH query.
    */
   public void testSubscribeUnSubscribeXPath()
   {
      Log.info(ME, "Starting testSubscribeUnSubscribeXPath()");
      numReceived = 0;
      testPublish();           // Feed some data
      testSubscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      testUnSubscribeXPath();  // cancel with XPATH syntax
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

      numReceived += 1;

      assertEquals("Wrong receveiver", receiverName, loginName);
      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

      messageArrived = true;
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
            Log.warning(ME, "Timeout of " + timeout + " occurred");
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
       String loginName = "Tim";
       suite.addTest(new TestUnSub("testSubscribeUnSubscribeExact", loginName));
       suite.addTest(new TestUnSub("testSubscribeUnSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestUnSub
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestUnSub</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestUnSub testSub = new TestUnSub("TestUnSub", "Tim");

      testSub.setUp();
      testSub.testSubscribeUnSubscribeXPath();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubscribeUnSubscribeExact();
      testSub.tearDown();

      Log.exit(TestUnSub.ME, "Good bye");
   }
}

