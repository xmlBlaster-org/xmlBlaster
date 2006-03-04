/*------------------------------------------------------------------------------
Name:      TestSubNoInitial.java
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
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubNoInitial
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubNoInitial
 * </pre>
 */
public class TestSubNoInitial extends TestCase implements I_Callback
{
   private static String ME = "TestSubNoInitial";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubNoInitial.class.getName());
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "dummyTestSubNoInitial";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private Timestamp sentTimestamp;
   private String cbSessionId = "0fxrc83plP";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   private String assertInUpdate = null;

   /**
    * Constructs the TestSubNoInitial object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubNoInitial(Global glob, String testName, String loginName)
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
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         CallbackAddress cbAddress = new CallbackAddress(this.glob);
         cbAddress.setSecretSessionId(cbSessionId); // to protect our callback server - see method update()
         cbAddress.setDispatchPlugin("undef");
         qos.addCallbackAddress(cbAddress);
         senderConnection.connect(qos, this); // Login to xmlBlaster
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
         waitOnUpdate(5000L);
         assertTrue(assertInUpdate, assertInUpdate == null);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }


   /**
    * Subscribe to messages with XPATH.
    */
   public void subscribeXPath()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //TestSubNoInitial-AGENT" +
                      "</key>";
      numReceived = 0;
      subscribeOid = null;
      SubscribeQos sk = new SubscribeQos(glob);
      sk.setWantInitialUpdate(false);
      String qos = sk.toXml(); // "<qos><initialUpdate>false</initialUpdate></qos>";
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void publish()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestSubNoInitial-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestSubNoInitial-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestSubNoInitial-DRIVER>"+
                      "   </TestSubNoInitial-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         sentTimestamp = new Timestamp();
         PublishReturnQos tmp = senderConnection.publish(msgUnit);
         assertEquals("Wrong publishOid", publishOid, tmp.getKeyOid());
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
   public void testNoInitialUpdate()
   {
      publish();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertEquals("numReceived after subscribe", 0, numReceived);

      subscribeXPath();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publish();
      waitOnUpdate(5000L);
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId_, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of message oid=" + updateKey.getOid() + " subId=" + updateQos.getSubscriptionId() + " ...");

      numReceived += 1;

      if (updateQos.isErased())
         return "";

      assertInUpdate = "Wrong cbSessionId, expected:" + this.cbSessionId + " but was:" + cbSessionId_;
      assertEquals("Wrong cbSessionId", this.cbSessionId, cbSessionId_);

      assertInUpdate = "Wrong sender, expected:" + senderName + " but was:" + updateQos.getSender();
      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());

      assertInUpdate = "Wrong subscriptionId, expected:" + subscribeOid + " but was:" + updateQos.getSubscriptionId();
      assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQos.getSubscriptionId());

      assertInUpdate = "Wrong oid of message returned expected:" + publishOid + " but was:" + updateKey.getOid();
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());

      assertInUpdate = "Message content is corrupted expected:" + new String(senderContent) + " but was:" + new String(content);
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      
      assertInUpdate = "Message contentMime is corrupted expected:" + contentMime + " but was:" + updateKey.getContentMime();
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      
      assertInUpdate = "Message contentMimeExtended is corrupted expected:" + contentMimeExtended + " but was: " + updateKey.getContentMimeExtended();
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

      // Test requirement "engine.qos.update.rcvTimestamp":
      assertInUpdate = "sentTimestamp not in hamony with rcvTimestamp";
      assertTrue("sentTimestamp="+sentTimestamp+" not in hamony with rcvTimestamp="+updateQos.getRcvTimestamp(),
             sentTimestamp.getMillis() < updateQos.getRcvTimestamp().getMillis() &&
             (sentTimestamp.getMillis()+1000) > updateQos.getRcvTimestamp().getMillis());

      assertInUpdate = null;
      messageArrived = true;
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
       String loginName = "Tim";
       suite.addTest(new TestSubNoInitial(new Global(), "testNoInitialUpdate", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubNoInitial
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNoInitial</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println("Init failed");
         System.exit(1);
      }
      TestSubNoInitial testSub = new TestSubNoInitial(glob, "TestSubNoInitial", "Tim");
      testSub.setUp();
      testSub.testNoInitialUpdate();
      testSub.tearDown();
   }
}

