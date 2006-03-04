/*------------------------------------------------------------------------------
Name:      TestSubNoDup.java
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
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests multiple subscribe() on same message oid
 * and setting duplicateUpdates=false (requirement engine.qos.subscribe.duplicate).
 * <br />
 * The subscribe() should be recognized for the later arriving publish()
 * but only one update should arrive. For example cluster slave nodes
 * need this feature.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNoDup
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubNoDup
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.duplicate.html" target="others">duplicate subscribe requirement</a>
 */
public class TestSubNoDup extends TestCase implements I_Callback
{
   private static String ME = "TestSubNoDup";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubNoDup.class.getName());

   private String subscribeId1;
   private String subscribeId2;
   private String subscribeId3;
   private String oidExact = "HelloMessageNoDup";
   private String publishOid = null;
   private I_XmlBlasterAccess senderConnection;
   private String senderContent;

   private boolean duplicates = false;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";

   /**
    * Constructs the TestSubNoDup object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestSubNoDup(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      if (senderConnection != null) {
         if (publishOid != null) {
            String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
            try {
               EraseReturnQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
               assertEquals("Erase", 1, arr.length);
            } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
         }

         senderConnection.disconnect(null);
         senderConnection = null;
      }
   }

   /**
    * Subscribe three times to same message. 
    * <p />
    * The returned subscribeId1 is checked
    */
   public void subscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");

      String xmlKey = "<key oid='" + oidExact + "' queryType='EXACT'/>";
      String qos = "<qos/>";
      numReceived = 0;
      subscribeId1 = null;
      subscribeId2 = null;
      subscribeId3 = null;
      try {
         // if duplicate updates are suppressed, every subscribe gets the same subscription ID

         subscribeId1 = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeId1", subscribeId1 != null);
         assertTrue("returned subscribeId1 is empty", 0 != subscribeId1.length());
         log.info("Success: Subscribe 1 on " + subscribeId1 + " done");

         subscribeId2 = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned subscribeId2 is empty", 0 != subscribeId2.length());
         if (duplicates)
            assertTrue("Wrong subscriptionId", !subscribeId1.equals(subscribeId2));
         else
            assertEquals("Wrong subscriptionId", subscribeId1, subscribeId2);
         log.info("Success: Subscribe 2 on " + subscribeId2 + " done");

         subscribeId3 = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned subscribeId3 is empty", 0 != subscribeId3.length());
         if (duplicates)
            assertTrue("Wrong subscriptionId", !subscribeId1.equals(subscribeId2));
         else
            assertEquals("Wrong subscriptionId", subscribeId1, subscribeId3);
         log.info("Success: Subscribe 3 on " + subscribeId3 + " done");

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
   public void publish() {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + oidExact + "' contentMime='" + contentMime + "'/>";
      senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos/>");
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
    * unSubscribe three times to same message. 
    */
   public void unSubscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("unSubscribing ...");

      String qos = "<qos/>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe("<key oid='" + subscribeId1 + "'/>", qos);
         log.info("Success: unSubscribe 1 on " + subscribeId1 + " done");

         senderConnection.unSubscribe("<key oid='" + subscribeId2 + "'/>", qos);
         log.info("Success: unSubscribe 2 on " + subscribeId2 + " done");

         senderConnection.unSubscribe("<key oid='" + subscribeId3 + "'/>", qos);
         log.info("Success: unSubscribe 3 on " + subscribeId3 + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   private void connect() {
      try {
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         qos.setDuplicateUpdates(duplicates);
         senderConnection.connect(qos, this);
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
   }

   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterMultiSubscribeNoDup() {
      log.info("testPublishAfterMultiSubscribeNoDup ...");
      numReceived = 0;
      duplicates = false; // suppress multi update
      
      connect();

      subscribe();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publish();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      assertEquals("numReceived after publishing", 1, numReceived); // only one message arrived?

      unSubscribe();

      numReceived = 0;
      publish();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      assertEquals("numReceived after publishing", 0, numReceived); // no message arrived?
   }

   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterMultiSubscribe() {
      log.info("testPublishAfterMultiSubscribe ...");
      numReceived = 0;
      duplicates = true; // allow multi update (default)
      
      connect();

      subscribe();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publish();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      assertEquals("numReceived after publishing", 3, numReceived); // only one message arrived?

      unSubscribe();

      numReceived = 0;
      publish();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      assertEquals("numReceived after publishing", 0, numReceived); // no message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info("Receiving update of a message " + updateKey.getOid() + " state=" + updateQos.getState());

      numReceived += 1;

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      if (!duplicates)
         assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeId1, updateQos.getSubscriptionId());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSubNoDup(new Global(), "testPublishAfterMultiSubscribeNoDup"));
       suite.addTest(new TestSubNoDup(new Global(), "testPublishAfterMultiSubscribe"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubNoDup
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNoDup</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestSubNoDup testSub = new TestSubNoDup(glob, "TestSubNoDup");
      testSub.setUp();
      testSub.testPublishAfterMultiSubscribeNoDup();
      testSub.tearDown();
      testSub = new TestSubNoDup(glob, "TestSubNoDup");
      testSub.setUp();
      testSub.testPublishAfterMultiSubscribe();
      testSub.tearDown();
   }
}

