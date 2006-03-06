/*------------------------------------------------------------------------------
Name:      TestSubXPath.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() on message 3 should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubXPath
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubXPath
 * </pre>
 */
public class TestSubXPath extends TestCase
{
   private String ME = "TestSubXPath";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubXPath.class.getName());

   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String receiverName;         // sender/receiver is here the same client

   private int numPublish = 5;
   private final String contentMime = "text/xml";
   private String subscribeOid = null;

   private MsgInterceptor updateInterceptor;

   /**
    * Constructs the TestSubXPath object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubXPath(Global glob, String testName, String loginName) {
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
   protected void setUp() {
      try {
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(this.glob, this.senderName, "secret");
         this.updateInterceptor = new MsgInterceptor(this.glob, log, null);
         senderConnection.connect(qos, this.updateInterceptor); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
      this.updateInterceptor.clear();
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      senderConnection.disconnect(null);
   }

   /**
    * TEST: Subscribe to message number 3 with XPATH.
    * <p />
    */
   private void subscribeXPath(String query) {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      query +
                      "</key>";
      String qos = "<qos/>";
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeOid + " done:\n" + xmlKey);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }

   /**
    * TEST: Construct 5 messages and publish them.
    * <p />
    * The returned publishOid is checked
    */
   private void doPublish() {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      for (int counter= 1; counter <= numPublish; counter++) {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + counter + "' contentMime='" + contentMime + "'>\n" +
                         "<AGENT id='message_" + counter + "' subId='1' type='generic'>" +
                         "<DRIVER id='FileProof' pollingFreq='10'>" +
                         "</DRIVER>"+
                         "</AGENT>" +
                         "</key>";
         String content = "Content: message_" + counter;
         try {
            MsgUnit msgUnit = new MsgUnit(glob, xmlKey, content.getBytes(), "<qos></qos>");
            publishOid = senderConnection.publish(msgUnit).getKeyOid();
            log.info("Success: Publishing #" + counter + " done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
            assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
         }

         assertTrue("returned publishOid == null", publishOid != null);
         assertTrue("returned publishOid", 0 != publishOid.length());
      }
   }

   /**
    * TEST: Construct 5 messages and publish them,<br />
    * the previous XPath subscription should match message #3 and send an update.
    */
   public void testInitial()  {
      ME = "TestSubXPath:testInitial()";

      String oid = "INITIAL";

      subscribeXPath("//demoXPath");
      assertEquals("numReceived after subscribe", 0, this.updateInterceptor.waitOnUpdate(1000L, null, null));
      this.updateInterceptor.clear();

      try {
         PublishKey pk = new PublishKey(glob, oid, "text/xml", "1.0");
         pk.setClientTags("<org.xmlBlaster><demoXPath/></org.xmlBlaster>");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         PublishReturnQos tmp = senderConnection.publish(msgUnit);
         assertEquals("returned oid", oid, tmp.getKeyOid());
         assertEquals("numReceived after publishing", 1, this.updateInterceptor.waitOnUpdate(2000L, oid, Constants.STATE_OK));
         assertEquals("", 1, this.updateInterceptor.getMsgs().length);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
         fail(e.getMessage());
      }

      try {
         EraseReturnQos[] arr = senderConnection.erase("<key oid='"+oid+"'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
   }

   /**
    * TEST: Check if XPath finds XML-attributes
    */
   public void testAttribute()  {
      ME = "TestSubXPath:testAttribute()";

      String oid = "gunsNroses";

      this.updateInterceptor.clear();

      try {
         PublishKey pk = new PublishKey(glob, oid, "text/xml", "1.0");
         pk.setClientTags("<rose><color id='green'></color></rose>");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, "Hi", pq);
         PublishReturnQos tmp = senderConnection.publish(msgUnit);
         assertEquals("returned oid", oid, tmp.getKeyOid());
         subscribeXPath("//rose/color[@id='green']");
         assertEquals("numReceived after publishing", 1, this.updateInterceptor.waitOnUpdate(2000L, oid, Constants.STATE_OK));
         assertEquals("", 1, this.updateInterceptor.getMsgs().length);
      }
      catch (XmlBlasterException e) {
         log.severe(e.getMessage());
         fail(e.getMessage());
      }

      try {
         EraseReturnQos[] arr = senderConnection.erase("<key oid='"+oid+"'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
   }

   /**
    * TEST: Construct 5 messages and publish them,<br />
    * the previous XPath subscription should match message #3 and send an update.
    */
   public void testPublishAfterSubscribeXPath()  {
      ME = "TestSubXPath:testPublishAfterSubscribeXPath()";

      subscribeXPath("/xmlBlaster/key/AGENT[@id='message_3']");
      // there should be no Callback
      assertEquals("numReceived after subscribe", 0, this.updateInterceptor.waitOnUpdate(1000L, null, null));
      this.updateInterceptor.clear();

      int n = 4;
      for(int i=0; i<n; i++) {
         log.info("TEST " + (i+1) + " - publishing 5 messages, expecting No.3");
         doPublish();
         assertEquals("numReceived after publishing", 1, this.updateInterceptor.waitOnUpdate(2000L, "3", Constants.STATE_OK));
         assertEquals("", 1, this.updateInterceptor.getMsgs().length);
         Msg msg = this.updateInterceptor.getMsgs()[0];
         assertEquals("Corrupt content", senderName, msg.getUpdateQos().getSender().getLoginName());
         assertEquals("Corrupt content", "Content: message_3", msg.getContentStr());
         assertEquals("Message contentMime is corrupted", contentMime, msg.getUpdateKey().getContentMime());
         assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, msg.getUpdateQos().getSubscriptionId());
         this.updateInterceptor.clear();
      }

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/AGENT" +
                      "</key>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
         assertEquals("Erase", numPublish, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubXPath(new Global(), "testInitial", loginName));
       suite.addTest(new TestSubXPath(new Global(), "testAttribute", loginName));
       suite.addTest(new TestSubXPath(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubXPath
    */
   public static void main(String args[]) {
      TestSubXPath testSub = new TestSubXPath(new Global(args), "TestSubXPath", "Tim");
      testSub.setUp();
      testSub.testAttribute();
      //testSub.testInitial();
      //testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
   }
}

