/*------------------------------------------------------------------------------
Name:      TestSubGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the method subscribe() and get() with a later publish() with EXACT oid.
 * <br />
 * The subscribe() and get() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubGet
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubGet
 * </pre>
 * @author Michal Masa xmasam@sgi.felk.cvut.cz
 */
public class TestSubGet extends TestCase implements I_Callback
{
   private static String ME = "TestSubGet";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubGet.class.getName());

   private boolean messageArrived = false;

   private String subscribeOid;
   private String oidExact = "HelloMessage4";
   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestSubGet object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubGet(Global glob, String testName, String loginName)
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
         ConnectQos qos = new ConnectQos(glob, senderName, passwd); // == "<qos></qos>";
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
      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }


   /**
    * Subscribe to message with EXACT oid
    * <p />
    * The returned subscribeOid is checked
    */
   public void subscribeExact()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='" + oidExact + "' queryType='EXACT'>\n" +
                      "</key>";
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
   public void testPublish()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + oidExact + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         publishOid = senderConnection.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
      assertEquals("returned publishOid is wrong", oidExact, publishOid);
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribe()
   {
      subscribeExact();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      testGet();
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message from sender = " + updateQos.getSender() + " ...");

      numReceived += 1;

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      if (updateQos.isErased()) {
         return "";
      }

      try {
         assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
         assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQos.getSubscriptionId());
         assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
         assertEquals("Message content is corrupted", new String(senderContent), new String(content));
         assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
         assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());
      }
      catch (RuntimeException e) { // TODO: pass problem to junit
         e.printStackTrace();
         log.severe(e.toString());
         throw e;
      }

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
    * TEST: Get an not existing and an existing message
    * <p />
    * The returned content is checked
    */
   public void testGet()
   {
      if (log.isLoggable(Level.FINE)) log.fine("3. Get an existing message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MsgUnit[] msgArr = senderConnection.get(xmlKey, qos);

         assertEquals("Got wrong number of messages", 1, msgArr.length);
         log.info("Success, got the message '" + msgArr[0].getKey() + "'");
         if (log.isLoggable(Level.FINEST)) log.finest(msgArr[0].toXml());

         GetReturnQos getQos = new GetReturnQos(glob, msgArr[0].getQos());
         assertEquals("Sender is corrupted", senderName, getQos.getSender().getLoginName());
         log.info("Get success from sender " + getQos.getSender());

         assertEquals("Corrupted content", senderContent, new String(msgArr[0].getContent()));
      } catch(XmlBlasterException e) {
         log.severe("XmlBlasterException for trying to get a message: " + e.getMessage());
         assertTrue("Couldn't get() an existing message", false);
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubGet(new Global(), "testPublishAfterSubscribe", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubGet
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubGet</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubGet testSub = new TestSubGet(glob, "TestSubGet", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribe();
      testSub.tearDown();
   }
}

