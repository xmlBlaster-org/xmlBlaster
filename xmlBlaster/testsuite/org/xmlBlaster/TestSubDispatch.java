/*------------------------------------------------------------------------------
Name:      TestSubDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubDispatch.java,v 1.5 2002/03/18 00:31:23 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() should be recognized for this later arriving publish()
 * and will be received in the specialized, anonymous, update implementation.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestSubDispatch
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestSubDispatch
 * </pre>
 */
public class TestSubDispatch extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "dummy";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestSubDispatch object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubDispatch(String testName, String loginName)
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
         senderConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          assert("Login failed: " + e.toString(), false);
      }
   }


   /**
    * Tears down the fixture.
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
                      "   //TestSubDispatch-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS) {
                  Log.info(ME, "Receiving message with specialized update() ...");
                  numReceived += 1;

                  assertEquals("Wrong sender", senderName, updateQoS.getSender());
                  assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQoS.getSubscriptionId());
                  assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
                  assertEquals("Message content is corrupted", new String(senderContent), new String(content));
                  assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
                  assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

                  messageArrived = true;
                  return "";
               }
            });
         Log.info(ME, "Success: Subscribe subscription-id=" + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestSubDispatch-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestSubDispatch-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestSubDispatch-DRIVER>"+
                      "   </TestSubDispatch-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         String tmp = senderConnection.publish(msgUnit);
         assertEquals("Wrong publishOid", publishOid, tmp);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribeXPath();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQoS)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      Log.error(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + "...");
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
      messageArrived = false;
   }


   // Helper for inner class
   private void assertEquals(String a, String b, String c)
   {
      super.assertEquals(a, b, c);
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubDispatch("testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestSubDispatch
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSubDispatch</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestSubDispatch testSub = new TestSubDispatch("TestSubDispatch", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
      Log.exit(TestSubDispatch.ME, "Good bye");
   }
}

