/*------------------------------------------------------------------------------
Name:      TestSubId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a forced subscriptionId by the client
 * <br />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubId
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubId
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.id.html">The engine.qos.subscribe.id requirement</a>
 */
public class TestSubId extends TestCase implements I_Callback
{
   private static String ME = "TestSubId";
   private final Global glob;
   private final LogChannel log;

   private boolean messageArrived = false;

   private String sentSubscribeId;
   private String subscribeId;
   private String oidExact = "HelloMessage";
   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   private ConnectQos connectQos;
   private ConnectReturnQos connectReturnQos;

   /**
    * Constructs the TestSubId object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubId(Global glob, String testName, String loginName)
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
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         connectQos = new ConnectQos(glob, senderName, passwd);
         connectReturnQos = senderConnection.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          fail("Login failed: " + e.toString());
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
    * The returned subscribeId is checked
    */
   public void subscribeExact()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='" + oidExact + "' queryType='EXACT'>\n" +
                      "</key>";
      SubscribeQos sq = new SubscribeQos(glob);
      String serverName = "heron";
      int myCounter = 99;
      try {
         sentSubscribeId = Constants.SUBSCRIPTIONID_CLIENT_PREFIX +
                  "/" + connectReturnQos.getSessionName() +
                  "/" + myCounter;
         sq.setSubscriptionId(sentSubscribeId);
         numReceived = 0;
         subscribeId = null;
         subscribeId = senderConnection.subscribe(xmlKey, sq.toXml()).getSubscriptionId();
         assertEquals("Sent sentSubscribeId= " + sentSubscribeId + " The returned subscriptionId=" + subscribeId + " is wrong", sentSubscribeId, subscribeId);
         log.info(ME, "Success: Subscribe on " + subscribeId + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }
      assertTrue("returned null subscribeId", subscribeId != null);
      assertTrue("returned subscribeId is empty", 0 != subscribeId.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + oidExact + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         publishOid = senderConnection.publish(msgUnit).getKeyOid();
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
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
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
      log.info(ME, "sentSubscribeId=" + sentSubscribeId + ":" + updateQos.toXml());

      if (updateQos.isErased()) {
         return "";
      }

      numReceived += 1;

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeId, updateQos.getSubscriptionId());
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
       suite.addTest(new TestSubId(new Global(), "testPublishAfterSubscribe", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubId
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubId</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubId testSub = new TestSubId(glob, "TestSubId", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribe();
      testSub.tearDown();
   }
}

