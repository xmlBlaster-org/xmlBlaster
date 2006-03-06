/*------------------------------------------------------------------------------
Name:      TestTopicHistory.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing some topic state transitions
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.topic;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * Here we test access to history messages of a topic. 
 * <p>
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.topic.TestTopicHistory
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.topic.TestTopicHistory
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">The engine.message.lifecycle requirement</a>
 * @see org.xmlBlaster.engine.TopicHandler
 */
public class TestTopicHistory extends TestCase implements I_Callback {
   private String ME = "TestTopicHistory";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestTopicHistory.class.getName());

   private final String senderName = "Gesa";
   private I_XmlBlasterAccess con = null;
   private String senderContent = "Some message content";
   private String publishOid = "TestTopicHistoryMsg";
   private SubscribeReturnQos subscribeReturnQos;
   private long blockUpdateTime = 0L;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9566;
   private boolean startEmbedded = false;

   private int numReceived = 0;

   /**
    * Constructs the TestTopicHistory object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestTopicHistory(Global glob, String testName) {
      super(testName);
      this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp() {
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         String[] args = { };
         glob.init(args);
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing the priority dispatch plugin");
      }

      try {
         String passwd = "secret";
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         con.connect(qos, this);
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown() {
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);
         if (arr.length != 0) {
            log.severe("Erased " + arr.length + " messages instead of 0");
         }
         assertEquals("Erase", 0, arr.length);   // The volatile message schould not exist !!
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);

      if (this.startEmbedded) {
         try { Thread.sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   public EraseReturnQos[] sendErase(boolean forceDestroy) {
      log.info("Erasing a topic forceDestroy=" + forceDestroy);
      try {
         EraseQos eq = new EraseQos(glob);
         eq.setForceDestroy(forceDestroy);
         EraseKey ek = new EraseKey(glob, publishOid);
         EraseReturnQos[] er = con.erase(ek.toXml(), eq.toXml());
         return er;
      } catch(XmlBlasterException e) {
         fail("Erase XmlBlasterException: " + e.getMessage());
      }
      return null;
   }

   /**
    * Create a topic. 
    */
   public void createTopic(String keyOid, TopicProperty topicProperty) {
      log.info("Creating topic " + keyOid);
      try {
         PublishKey pk = new PublishKey(glob, publishOid, "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         pq.setTopicProperty(topicProperty);
         MsgUnit msgUnit = new MsgUnit(pk, senderContent, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, publishReturnQos.getKeyOid());
         log.info("Topic oid=" + publishOid + " created: " + msgUnit.toXml());
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Publish an almost volatile message.
    */
   public void sendExpiringMsg(boolean initializeTopic, long topicDestroyDelay, long msgLifeTime) {
      log.info("Sending a message initializeTopic=" + initializeTopic + " topicDestroyDelay=" + topicDestroyDelay + " msgLifeTime=" + msgLifeTime);
      try {
         // Publish a volatile message
         PublishKey pk = new PublishKey(glob, publishOid, "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         pq.setLifeTime(msgLifeTime);
         pq.setForceDestroy(false);
         if (initializeTopic) {
            // Configure the topic to our needs
            TopicProperty topicProperty = new TopicProperty(glob);
            topicProperty.setDestroyDelay(topicDestroyDelay);
            topicProperty.setCreateDomEntry(false);
            pq.setTopicProperty(topicProperty);
         }
         MsgUnit msgUnit = new MsgUnit(pk, senderContent, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, publishReturnQos.getKeyOid());
         log.info("Sending of '" + senderContent + "' done, returned oid=" + publishOid + " " + msgUnit.toXml());
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Subscribe a volatile message.
    */
   public void subscribeMsg() {
      log.info("Subscribing message '" + publishOid + "'...");
      try {
         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, publishOid);
         SubscribeQos sq = new SubscribeQos(glob);
         subscribeReturnQos = con.subscribe(sk.toXml(), sq.toXml());
         log.info("Subscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.severe("subscribe() XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * unSubscribe a message.
    */
   public void unSubscribeMsg() {
      log.info("unSubscribing a volatile message ...");
      try {
         // Subscribe for the volatile message
         UnSubscribeKey sk = new UnSubscribeKey(glob, subscribeReturnQos.getSubscriptionId());
         UnSubscribeQos sq = new UnSubscribeQos(glob);
         con.unSubscribe(sk.toXml(), sq.toXml());
         log.info("UnSubscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.severe("unSubscribe() XmlBlasterException: " + e.getMessage());
         assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Retrieve a dump of xmlBlaster to analyse
    */
   private String getDump() {
      try {
         GetKey gk = new GetKey(glob, "__cmd:?dump");
         GetQos gq = new GetQos(glob);
         MsgUnit[] msgs = con.get(gk.toXml(), gq.toXml());
         assertEquals("Did not expect returned msg for get()", 1, msgs.length);
         return msgs[0].getContentStr();
      }
      catch (XmlBlasterException e) {
         fail("Didn't expect an exception in get(): " + e.getMessage());
      }
      return "";
   }

   /**
    * THIS IS THE TEST
    * <p>
    * <pre>
    * <pre>
    * </p>
    */
   public void testHistory() {
      this.ME = "TestTopicHistory-testHistory";
      log.info("Entering testHistory ...");
      numReceived = 0;

      String keyOid = "smallTopic";
      TopicProperty topicProperty = new TopicProperty(glob);
      long topicDestroyDelay = 6000L;
      topicProperty.setDestroyDelay(topicDestroyDelay);
      topicProperty.setCreateDomEntry(false);
      createTopic(keyOid, topicProperty);

      /*
      {  // topic transition from START -> [2] -> ALIVE (3 sec)
         long msgLifeTime = 3000L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         waitOnUpdate(1000L, 0);
         assertEquals("numReceived after sending", 0, numReceived); // no message arrived?
         String dump = getDump();
         log.fine(dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicHistoryMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicHistoryMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicHistoryMsg' state='ALIVE'") != -1);
      }


      {  // topic transition from ALIVE -> [6] -> UNREFERENCED (3 sec)
         try { Thread.sleep(3500L); } catch( InterruptedException i) {}
         String dump = getDump();
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicHistoryMsg' state='UNREFERENCED'>
         //  <uniqueKey>TestTopicHistoryMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicHistoryMsg' state='UNREFERENCED'") != -1);
      }

      {  // topic transition from UNREFERENCED -> [11] -> DEAD
         log.info("Sleeping for another 5 sec, the topic (with destroyDelay=6sec) should be dead then");
         try { Thread.sleep(6000); } catch( InterruptedException i) {}
         // Topic should be destroyed now

         String dump = getDump();
         log.fine("IS DEAD?"+dump);
         assertTrue("Not expected a dead topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      */
      log.info("SUCCESS testHistory");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info("Receiving update of a message " + updateKey.getOid() + " " + updateQos.getState());

      numReceived += 1;

      if (updateQos.isOk()) {
         assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
         assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      }

      if (this.blockUpdateTime > 0L) {
         log.info("Blocking the update callback for " + this.blockUpdateTime + " millis");
         try { Thread.sleep(this.blockUpdateTime); } catch( InterruptedException i) {}
         this.blockUpdateTime = 0L;
         log.info("Block released, reset blockTimer");
      }
      return "";
   }

   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (numReceived < numWait) {
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
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestTopicHistory(new Global(), "testHistory"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.topic.TestTopicHistory -startEmbedded false
    */
   public static void main(String args[]) {
      TestTopicHistory testSub = new TestTopicHistory(new Global(args), "TestTopicHistory");
      testSub.setUp();
      testSub.testHistory();
      testSub.tearDown();
   }
}
