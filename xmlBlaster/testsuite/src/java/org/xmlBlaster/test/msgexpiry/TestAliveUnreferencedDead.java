/*------------------------------------------------------------------------------
Name:      TestAliveUnreferencedDead.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing some topic state transitions
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.msgexpiry;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.util.ConnectQos;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import junit.framework.*;


/**
 * Here we test some state transitions of a topic. 
 * <p>
 * We traverse the transitions
 * <pre>
 * Start -[2]->  ALIVE (3 sec)
 *       -[6]->  UNREFERENCED (3 sec)
 *       -[11]-> DEAD
 * <pre>
 * as described in requirement engine.message.lifecycle by sending some expiring messages (see
 * state transition brackets in requirement)<br />
 * Please see individual test for a description
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.msgexpiry.TestAliveUnreferencedDead
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.msgexpiry.TestAliveUnreferencedDead
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">The engine.message.lifecycle requirement</a>
 * @see org.xmlBlaster.engine.TopicHandler
 */
public class TestAliveUnreferencedDead extends TestCase implements I_Callback {
   private String ME = "TestAliveUnreferencedDead";
   private final Global glob;
   private final LogChannel log;

   private final String senderName = "Gesa";
   private XmlBlasterConnection con = null;
   private String senderContent = "Some volatile content";
   private String publishOid = "TestAliveUnreferencedDeadMsg";
   private SubscribeReturnQos subscribeReturnQos;
   private long blockUpdateTime = 0L;

   private int numReceived = 0;

   /**
    * Constructs the TestAliveUnreferencedDead object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestAliveUnreferencedDead(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
   }

   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp() {
      try {
         String passwd = "secret";
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         con.connect(qos, this);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);
         assertEquals("Erase", 0, arr.length);   // The volatile message schould not exist !!
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
   }

   public EraseReturnQos[] sendErase(boolean forceDestroy) {
      log.info(ME, "Erasing a topic forceDestroy=" + forceDestroy);
      try {
         EraseQos eq = new EraseQos(glob);
         EraseKey ek = new EraseKey(glob, publishOid);
         EraseReturnQos[] er = con.erase(ek.toXml(), eq.toXml());
         return er;
      } catch(XmlBlasterException e) {
         fail("Erase XmlBlasterException: " + e.getMessage());
      }
      return null;
   }

   /**
    * Publish an almost volatile message.
    */
   public void sendExpiringMsg(boolean initializeTopic, long topicDestroyDelay, long msgLifeTime) {
      log.info(ME, "Sending a message initializeTopic=" + initializeTopic + " topicDestroyDelay=" + topicDestroyDelay + " msgLifeTime=" + msgLifeTime);
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
         MsgUnit msgUnit = new MsgUnit(glob, pk, senderContent, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, publishReturnQos.getKeyOid());
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid + " " + msgUnit.toXml());
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Subscribe a volatile message.
    */
   public void subscribeVolatile() {
      log.info(ME, "Subscribing message '" + publishOid + "'...");
      try {
         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, publishOid);
         SubscribeQos sq = new SubscribeQos(glob);
         subscribeReturnQos = con.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "Subscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.error(ME, "subscribe() XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * unSubscribe a message.
    */
   public void unSubscribeVolatile() {
      log.info(ME, "unSubscribing a volatile message ...");
      try {
         // Subscribe for the volatile message
         UnSubscribeKey sk = new UnSubscribeKey(glob, subscribeReturnQos.getSubscriptionId());
         UnSubscribeQos sq = new UnSubscribeQos(glob);
         con.unSubscribe(sk.toXml(), sq.toXml());
         log.info(ME, "UnSubscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.error(ME, "unSubscribe() XmlBlasterException: " + e.getMessage());
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
    * We traverse the transitions
    * <pre>
    * Start -[2]->  ALIVE (3 sec)
    *       -[6]->  UNREFERENCED (3 sec)
    *       -[11]-> DEAD
    * <pre>
    * as described in requirement engine.message.lifecycle by sending some expiring messages (see
    * state transition brackets in requirement)
    * </p>
    */
   public void testExpiry() {
      this.ME = "TestAliveUnreferencedDead-testExpiry";
      log.info(ME, "Entering testExpiry ...");
      if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());

      {  // topic transition from START -> [2] -> ALIVE (3 sec)
         long topicDestroyDelay = 6000L;
         long msgLifeTime = 3000L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         waitOnUpdate(1000L, 0);
         assertEquals("numReceived after sending", 0, numReceived); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='ALIVE'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='ALIVE'") != -1);
      }


      {  // topic transition from ALIVE -> [6] -> UNREFERENCED (3 sec)
         try { Thread.currentThread().sleep(3500L); } catch( InterruptedException i) {}
         String dump = getDump();
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='UNREFERENCED'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='UNREFERENCED'") != -1);
      }

      {  // topic transition from UNREFERENCED -> [11] -> DEAD
         log.info(ME, "Sleeping for another 5 sec, the topic (with destroyDelay=6sec) should be dead then");
         try { Thread.currentThread().sleep(6000); } catch( InterruptedException i) {}
         // Topic should be destroyed now

         String dump = getDump();
         log.trace(ME, "IS DEAD?"+dump);
         assertTrue("Not expected a dead topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testExpiry");
   }

   /**
    * THIS IS THE TEST
    * <p>
    * We traverse the transitions
    * <pre>
    * Start -[2]->  ALIVE (0 sec)
    *       -[6]->  UNREFERENCED (0 sec)
    *       -[11]-> DEAD
    * <pre>
    * as described in requirement engine.message.lifecycle by sending some expiring messages (see
    * state transition brackets in requirement)<br />
    * Please see individual test for a description
    * </p>
    */
   public void testVolatile() {
      this.ME = "TestAliveUnreferencedDead-testVolatile";
      log.info(ME, "Entering testVolatile ...");
      if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());

      {  // topic transition from START -> [2] -> ALIVE -> DEAD
         long topicDestroyDelay = 0L;
         long msgLifeTime = 0L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertTrue("Not expected a dead topic", getDump().indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testVolatile");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [4] -> [6] -> [11]
    */
   public void testSubscribeVolatile() {
      this.ME = "TestAliveUnreferencedDead-testSubscribeVolatile";
      log.info(ME, "Entering testSubscribeVolatile ...");

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeVolatile();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [4] -> ALIVE
         long topicDestroyDelay = 0L;
         long msgLifeTime = 0L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         waitOnUpdate(2000L, 1);
         assertEquals("numReceived after sending", 1, numReceived); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='ALIVE'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [6] -> UNREFERENCED -> [11] DEAD
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
         unSubscribeVolatile();
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testSubscribeVolatile");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [4] -> [7] -> [12]
    */
   public void testSoftErased() {
      this.ME = "TestAliveUnreferencedDead-testSoftErased";
      log.info(ME, "Entering testSoftErased ...");

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeVolatile();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [4] -> ALIVE
         long topicDestroyDelay = 400000L;
         long msgLifeTime = 400000L;
         this.blockUpdateTime = 3000L; // Blocking callback thread for 3 sec to force state SOFTERASED !!
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         waitOnUpdate(2000L, 1);
         assertEquals("numReceived after sending", 1, numReceived); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestAliveUnreferencedDeadMsg' state='ALIVE'>
         //  <uniqueKey>TestAliveUnreferencedDeadMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [7] -> SOFTERASED
         boolean forceDestroy = false;
         EraseReturnQos[] erq = sendErase(forceDestroy);
         assertEquals("erase failed", 1, erq.length);
         String dump = getDump();
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestAliveUnreferencedDeadMsg' state='SOFTERASED'") != -1);
      }

      {  // topic transition from SOFTERASED -> [12] --> DEAD
         try { Thread.currentThread().sleep(4500L); } catch( InterruptedException i) {}
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testSoftErased");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info(ME, "Receiving update of a message " + updateKey.getOid() + " " + updateQos.getState());

      numReceived += 1;

      if (updateQos.isOk()) {
         assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
         assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      }

      if (this.blockUpdateTime > 0L) {
         log.info(ME, "Blocking the update callback for " + this.blockUpdateTime + " millis");
         try { Thread.currentThread().sleep(this.blockUpdateTime); } catch( InterruptedException i) {}
         this.blockUpdateTime = 0L;
         log.info(ME, "Block released, reset blockTimer");
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
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestAliveUnreferencedDead(new Global(), "testExpiry"));
       suite.addTest(new TestAliveUnreferencedDead(new Global(), "testVolatile"));
       suite.addTest(new TestAliveUnreferencedDead(new Global(), "testSubscribeVolatile"));
       suite.addTest(new TestAliveUnreferencedDead(new Global(), "testSoftErased"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.msgexpiry.TestAliveUnreferencedDead
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.msgexpiry.TestAliveUnreferencedDead</pre>
    */
   public static void main(String args[]) {
      TestAliveUnreferencedDead testSub = new TestAliveUnreferencedDead(new Global(args), "TestAliveUnreferencedDead");
      testSub.setUp();
      testSub.testExpiry();
      testSub.testVolatile();
      testSub.testSubscribeVolatile();
      testSub.testSoftErased();
      testSub.tearDown();
   }
}
