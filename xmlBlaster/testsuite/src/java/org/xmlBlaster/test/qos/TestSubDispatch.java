/*------------------------------------------------------------------------------
Name:      TestSubDispatch.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


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
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubDispatch
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubDispatch
 * </pre>
 */
public class TestSubDispatch extends TestCase implements I_Callback
{
   private static String ME = "TestSubDispatch";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubDispatch.class.getName());

   private boolean messageArrived = false;

   private String publishOid = "dummyTestSubDispatch";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";
           
   private SubscribeReturnQos subscribeRetQos = null; // declare here to allow inner class access

   private String assertInUpdate = null;

   /**
    * Constructs the TestSubDispatch object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubDispatch(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;

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
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);
         senderConnection.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
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
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }

   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribeXPath()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //TestSubDispatch-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         subscribeRetQos = senderConnection.subscribe(xmlKey, qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("Receiving message with specialized update(" + updateKey.getOid() + ") ... " + updateQos.toXml());

                  if (updateQos.isErased()) {
                     return "";
                  }
                  numReceived += 1;

                  assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
                  assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeRetQos.getSubscriptionId(), updateQos.getSubscriptionId());
                  assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
                  assertEquals("Message content is corrupted", new String(senderContent), new String(content));
                  assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
                  assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

                  messageArrived = true;
                  return "";
               }
            });
         assertTrue("returned null subscribeRetQos", subscribeRetQos != null);
         assertTrue("returned null subscribeId", subscribeRetQos.getSubscriptionId() != null);
         assertTrue("returned subscribeId is empty", 0 != subscribeRetQos.getSubscriptionId().length());
         log.info("Success: Subscribe subscription-id=" + subscribeRetQos.getSubscriptionId() + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestSubDispatch-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestSubDispatch-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestSubDispatch-DRIVER>"+
                      "   </TestSubDispatch-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         String tmp = senderConnection.publish(msgUnit).getKeyOid();
         assertEquals("Wrong publishOid", publishOid, tmp);
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribeXPath();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback
      assertTrue(assertInUpdate, assertInUpdate == null);

      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      assertTrue(assertInUpdate, assertInUpdate == null);
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (updateQos.isErased()) {
         return "";
      }
      assertInUpdate = "Receiving update of message oid=" + updateKey.getOid() + " state=" + updateQos.getState() + " in default update handler ...";
      log.severe("Receiving update of message oid=" + updateKey.getOid() + " state=" + updateQos.getState() + " in default update handler ...");
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
       String loginName = TestSubDispatch.ME;
       suite.addTest(new TestSubDispatch(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubDispatch
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubDispatch</pre>
    */
   public static void main(String args[])
   {
      TestSubDispatch testSub = new TestSubDispatch(new Global(args), "TestSubDispatch", TestSubDispatch.ME);
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
   }
}

