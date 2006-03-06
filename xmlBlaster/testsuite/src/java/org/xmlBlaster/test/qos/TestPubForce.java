/*------------------------------------------------------------------------------
Name:      TestPubForce.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the method publish() with the forceUpdate QOS tag
 * <br />
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPubForce
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestPubForce
 * </pre>
 */
public class TestPubForce extends TestCase implements I_Callback
{
   private static String ME = "TestPubForce";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPubForce.class.getName());

   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "TestMessage";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestPubForce object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPubForce(Global glob, String testName, String loginName)
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
          log.severe(e.toString());
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
   public void testSubscribe()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeOid + " done");
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
   public void testPublish(boolean forceUpdate)
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestPubForce-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPubForce-AGENT>" +
                      "</key>";
      PublishQos qosWrapper = new PublishQos(glob);
      qosWrapper.setForceUpdate(forceUpdate);
      String qos = qosWrapper.toXml(); // == "<qos><forceUpdate/></qos>"

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         publishOid = senderConnection.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * Identical messages are not delivered unless ForceUpdate is set
    */
   public void testPublishForceUpdate()
   {
      testSubscribe();
      try { Thread.sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      senderContent = "I'm the new same content";

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      testPublish(false);
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 0, numReceived); // No message should arrive since they are identical

      testPublish(false);
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 0, numReceived); // No message should arrive since they are identical

      testPublish(true);   // true tests ForceUpdate flag
      waitOnUpdate(4000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid() + "...");

      numReceived += 1;

      if (updateQos.isErased()) {
         return "";
      }

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

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
            Thread.sleep(pollingInterval);
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
       suite.addTest(new TestPubForce(new Global(), "testPublishForceUpdate", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestPubForce
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPubForce</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPubForce testSub = new TestPubForce(glob, "TestPubForce", "Tim");
      testSub.setUp();
      testSub.testPublishForceUpdate();
      testSub.tearDown();
   }
}

