/*------------------------------------------------------------------------------
Name:      TestPub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestPub.java,v 1.25 2002/06/25 18:02:57 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client tests the method publish() with its different qos variants.
 * <br />
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestPub
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestPub
 * </pre>
 */
public class TestPub extends TestCase implements I_Callback
{
   private static String ME = "TestPub";
   private final Global glob;
   private final LogChannel log;
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "ReadonlyMessage";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   private String assertInUpdate = null;

   /**
    * Constructs the TestPub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPub(Global glob, String testName, String loginName)
   {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog(null);
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
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, e.toString());
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
         EraseRetQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 1, arr.length);
         assertTrue(assertInUpdate, assertInUpdate == null);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }

      senderConnection.logout();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribe()
   {
      System.out.println("***** Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish(boolean first)
   {
      System.out.println("***** Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestPub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPub-AGENT>" +
                      "</key>";
      PublishQosWrapper qosWrapper = new PublishQosWrapper();
      qosWrapper.setReadonly(true);
      String qos = qosWrapper.toXml(); // == "<qos><readonly /></qos>"

      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);

      if (first) {
         try {
            publishOid = senderConnection.publish(msgUnit).getOid();
            log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.reason);
            assertTrue("publish - XmlBlasterException: " + e.reason, false);
         }
         assertTrue("returned publishOid == null", publishOid != null);
         assertTrue("returned publishOid", 0 != publishOid.length());
      }
      else {
         try {
            publishOid = senderConnection.publish(msgUnit).getOid();
            assertTrue("Publishing readonly protected message again should not be possible", false);
         } catch(XmlBlasterException e) {
            log.info(ME, "Success: Publishing again throws an exception");
         }
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribe();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      log.info(ME, "*** Test #1");
      senderContent = "Yeahh, i'm the new content 1";
      testPublish(true);
      waitOnUpdate(5000L);
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertInUpdate = null;
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?

      log.info(ME, "*** Test #2");
      senderContent = "Yeahh, i'm the new content 2";
      testPublish(false);
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after publishing", 0, numReceived); // message arrived?
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertInUpdate = null;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message state=" + updateQos.getState());
      if (updateQos.isErased()) {
         log.info(ME, "Ignore erase event");
         return ""; // We ignore the erase event on tearDown
      }

      numReceived += 1;

      assertInUpdate = "Wrong sender, expected:" + senderName + " but was:" + updateQos.getSender();
      assertEquals("Wrong sender", senderName, updateQos.getSender());

      assertInUpdate = "Wrong oid of message returned expected:" + publishOid + " but was:" + updateKey.getUniqueKey();
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());

      assertInUpdate = "Message content is corrupted expected:" + new String(senderContent) + " but was:" + new String(content);
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));

      assertInUpdate = "Message contentMime is corrupted expected:" + contentMime + " but was:" + updateKey.getContentMime();
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      assertInUpdate = "Message contentMimeExtended is corrupted expected:" + contentMimeExtended + " but was: " + updateKey.getContentMimeExtended();
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

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
       suite.addTest(new TestPub(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestPub
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestPub</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("******* " + ME + ": Init failed");
      }
      TestPub testSub = new TestPub(glob, "TestPub", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
   }
}

