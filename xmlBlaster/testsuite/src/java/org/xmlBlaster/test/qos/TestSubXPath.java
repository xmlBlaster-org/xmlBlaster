/*------------------------------------------------------------------------------
Name:      TestSubXPath.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubXPath.java,v 1.3 2002/11/26 12:40:41 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.engine.helper.MessageUnit;

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
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubXPath
 * </pre>
 */
public class TestSubXPath extends TestCase implements I_Callback
{
   private static String ME = "TestSubXPath";
   private final Global glob;
   private final LogChannel log;
   private boolean messageArrived = false;

   private String publishOid = "";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private int numPublish = 5;
   private final String contentMime = "text/xml";
   private String subscribeOid = null;

   /**
    * Constructs the TestSubXPath object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubXPath(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
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
         senderConnection = new XmlBlasterConnection(glob); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
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
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/AGENT" +
                      "</key>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
         assertEquals("Erase", numPublish, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to message number 3 with XPATH.
    * <p />
    */
   public void testSubscribeXPath()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/AGENT[@id='message_3']" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done:\n" + xmlKey);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
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
   public void testPublish()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      for (int counter= 1; counter <= numPublish; counter++) {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + counter + "' contentMime='" + contentMime + "'>\n" +
                         "<AGENT id='message_" + counter + "' subId='1' type='generic'>" +
                         "<DRIVER id='FileProof' pollingFreq='10'>" +
                         "</DRIVER>"+
                         "</AGENT>" +
                         "</key>";
         String content = "Content: message_" + counter;
         MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         try {
            publishOid = senderConnection.publish(msgUnit).getKeyOid();
            log.info(ME, "Success: Publishing #" + counter + " done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.getMessage());
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
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribeXPath();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      numReceived = 0;
      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      numReceived = 0;
      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of message oid=" + updateKey.getOid() + " subId=" + updateQos.getSubscriptionId() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      String contentStr = new String(content);
      assertEquals("Message content is corrupted", "Content: message_3", contentStr);
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQos.getSubscriptionId());

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
            log.warn(ME, "Timeout of " + timeout + " occurred");
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
       suite.addTest(new TestSubXPath(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubXPath
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubXPath</pre>
    */
   public static void main(String args[])
   {
      TestSubXPath testSub = new TestSubXPath(new Global(args), "TestSubXPath", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
   }
}

