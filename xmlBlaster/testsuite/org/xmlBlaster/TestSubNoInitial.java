/*------------------------------------------------------------------------------
Name:      TestSubNoInitial.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubNoInitial.java,v 1.1 2002/06/27 12:56:46 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;

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
 *    java junit.textui.TestRunner -noloading testsuite.org.xmlBlaster.TestSubNoInitial
 *    java junit.swingui.TestRunner -noloading testsuite.org.xmlBlaster.TestSubNoInitial
 * </pre>
 */
public class TestSubNoInitial extends TestCase implements I_Callback
{
   private static String ME = "TestSubNoInitial";
   private final Global glob;
   private final LogChannel log;
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "dummyTestSubNoInitial";
   private XmlBlasterConnection senderConnection;
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
      this.log = glob.getLog("test");
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
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         senderConnection.connect(qos, this, cbSessionId); // Login to xmlBlaster
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseRetQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 1, arr.length);
         waitOnUpdate(5000L);
         assertTrue(assertInUpdate, assertInUpdate == null);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }

      senderConnection.logout();
   }


   /**
    * Subscribe to messages with XPATH.
    */
   public void subscribeXPath()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //TestSubNoInitial-AGENT" +
                      "</key>";
      numReceived = 0;
      subscribeOid = null;
      SubscribeQosWrapper sk = new SubscribeQosWrapper();
      sk.setInitialUpdate(false);
      String qos = sk.toXml(); // "<qos><initialUpdate>false</initialUpdate></qos>";
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe subscription-id=" + subscribeOid + " done");
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
   public void publish()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestSubNoInitial-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestSubNoInitial-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestSubNoInitial-DRIVER>"+
                      "   </TestSubNoInitial-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         sentTimestamp = new Timestamp();
         PublishRetQos tmp = senderConnection.publish(msgUnit);
         assertEquals("Wrong publishOid", publishOid, tmp.getOid());
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testNoInitialUpdate()
   {
      publish();
      Util.delay(1000L);
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertEquals("numReceived after subscribe", 0, numReceived);

      subscribeXPath();
      Util.delay(1000L);
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
      log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " subId=" + updateQos.getSubscriptionId() + " ...");

      numReceived += 1;

      assertInUpdate = "Wrong cbSessionId, expected:" + this.cbSessionId + " but was:" + cbSessionId_;
      assertEquals("Wrong cbSessionId", this.cbSessionId, cbSessionId_);

      assertInUpdate = "Wrong sender, expected:" + senderName + " but was:" + updateQos.getSender();
      assertEquals("Wrong sender", senderName, updateQos.getSender());

      assertInUpdate = "Wrong subscriptionId, expected:" + subscribeOid + " but was:" + updateQos.getSubscriptionId();
      assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQos.getSubscriptionId());

      assertInUpdate = "Wrong oid of message returned expected:" + publishOid + " but was:" + updateKey.getUniqueKey();
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());

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
       suite.addTest(new TestSubNoInitial(new Global(), "testNoInitialUpdate", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestSubNoInitial
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubNoInitial</pre>
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

