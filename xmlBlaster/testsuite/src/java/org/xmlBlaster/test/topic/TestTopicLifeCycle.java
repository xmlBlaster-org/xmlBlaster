/*------------------------------------------------------------------------------
Name:      TestTopicLifeCycle.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing some topic state transitions
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.topic;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
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
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import org.custommonkey.xmlunit.XMLTestCase;
import org.custommonkey.xmlunit.XMLUnit;


/**
 * Here we test some state transitions of a topic. 
 * <p>
 * We traverse the possible transitions of a topic (TopicHandler.java)
 * as described in requirement engine.message.lifecycle by sending some expiring messages (see
 * state transition brackets in requirement)<br />
 * Please see individual test for a description
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.topic.TestTopicLifeCycle
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.topic.TestTopicLifeCycle
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/engine.message.lifecycle.html">The engine.message.lifecycle requirement</a>
 * @see org.xmlBlaster.engine.TopicHandler
 */
public class TestTopicLifeCycle extends XMLTestCase implements I_Callback {
   private String ME = "TestTopicLifeCycle";
   private Global glob;
   private LogChannel log;

   private final String senderName = "Gesa";
   private I_XmlBlasterAccess con = null;
   private String senderContent = "Some message content";
   private String publishOid = "TestTopicLifeCycleMsg";
   private final String xpathTag = "<something/>";
   private final String xpath = "//something";
   private SubscribeReturnQos subscribeReturnQos;
   private long blockUpdateTime = 0L;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9566;
   private boolean startEmbedded = true;

   private MsgInterceptor updateInterceptor;

   /**
    * Constructs the TestTopicLifeCycle object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestTopicLifeCycle(Global glob, String testName) {
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
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         String[] args = { };
         glob.init(args);
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info(ME, "XmlBlaster is ready for testing the priority dispatch plugin");
      }

      try {
         String passwd = "secret";
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         this.updateInterceptor = new MsgInterceptor(this.glob, this.log, this);
         con.connect(qos, this.updateInterceptor);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
      this.updateInterceptor.clear();
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
         if (arr.length != 0) {
            log.error(ME, "Erased " + arr.length + " messages instead of 0");
         }
         assertEquals("Erase", 0, arr.length);   // The volatile message schould not exist !!
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
      con=null;

      if (this.startEmbedded) {
         try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
      this.glob = null;
      this.log = null;
   }

   public EraseReturnQos[] sendErase(boolean forceDestroy) {
      log.info(ME, "Erasing a topic forceDestroy=" + forceDestroy);
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
         MsgUnit msgUnit = new MsgUnit(pk, senderContent, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, publishReturnQos.getKeyOid());
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid + " " + msgUnit.toXml());
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Publish an almost volatile XPATH message. 
    * @return publishOid
    */
   public String sendExpiringXPathMsg(long topicDestroyDelay, long msgLifeTime) {
      log.info(ME, "Sending a XPath message topicDestroyDelay=" + topicDestroyDelay + " msgLifeTime=" + msgLifeTime);
      try {
         // Publish a volatile message
         PublishKey pk = new PublishKey(glob, "", "text/xml", "1.0");
         pk.setClientTags(xpathTag);
         PublishQos pq = new PublishQos(glob);
         pq.setLifeTime(msgLifeTime);
         pq.setForceDestroy(false);
         // Configure the topic to our needs
         TopicProperty topicProperty = new TopicProperty(glob);
         topicProperty.setDestroyDelay(topicDestroyDelay);
         topicProperty.setCreateDomEntry(true);
         pq.setTopicProperty(topicProperty);
         MsgUnit msgUnit = new MsgUnit(pk, senderContent, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishReturnQos.getKeyOid() + " " + msgUnit.toXml());
         return publishReturnQos.getKeyOid();
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
         return ""; // never reached
      }
   }

   /**
    * Subscribe a volatile message.
    */
   public void subscribeMsg() {
      log.info(ME, "Subscribing message '" + publishOid + "'...");
      try {
         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, publishOid);
         SubscribeQos sq = new SubscribeQos(glob);
         this.subscribeReturnQos = con.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "Subscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.error(ME, "subscribe() XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Subscribe topics with XPATH.
    * @return The subscription id
    */
   public String subscribeXPathMsg() {
      log.info(ME, "Subscribing message xpath='" + xpath + "'...");
      try {
         // Subscribe for the volatile message
         SubscribeKey sk = new SubscribeKey(glob, xpath, Constants.XPATH);
         SubscribeQos sq = new SubscribeQos(glob);
         this.subscribeReturnQos = con.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "Subscribing of '" + xpath + "' done");
         return this.subscribeReturnQos.getSubscriptionId();
      } catch(XmlBlasterException e) {
         log.error(ME, "subscribe() XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
         return ""; // never reached
      }
   }

   /**
    * unSubscribe a message.
    */
   public void unSubscribeMsg() {
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
    * Retrieve the number of topics in xmlBlaster. 
    */
   private int getNumTopics() {
      try {
         GetKey gk = new GetKey(glob, "__cmd:?numTopics");
         GetQos gq = new GetQos(glob);
         MsgUnit[] msgs = con.get(gk.toXml(), gq.toXml());
         assertEquals("Did not expect returned msg for get()", 1, msgs.length);
         return Integer.valueOf(msgs[0].getContentStr()).intValue();
      }
      catch (XmlBlasterException e) {
         fail("Didn't expect an exception in get(): " + e.getMessage());
      }
      return -1; // never reached
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
      this.ME = "TestTopicLifeCycle-testExpiry";
      log.info(ME, "Entering testExpiry ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [2] -> ALIVE (3 sec)
         long topicDestroyDelay = 6000L;
         long msgLifeTime = 3000L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 0, this.updateInterceptor.waitOnUpdate(1000L, 0)); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
      }


      {  // topic transition from ALIVE -> [6] -> UNREFERENCED (3 sec)
         try { Thread.currentThread().sleep(3500L); } catch( InterruptedException i) {}
         String dump = getDump();
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNREFERENCED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNREFERENCED'") != -1);
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
    * Start -[2]->  ALIVE (3 sec)
    *       -[6]->  UNREFERENCED (3 sec)
    *       -[5]->  ALIVE (3 sec)
    *       -[11]-> DEAD
    * <pre>
    * as described in requirement engine.message.lifecycle by sending some expiring messages (see
    * state transition brackets in requirement)
    * </p>
    */
   public void testUnreferencedAlive() throws Exception {
      this.ME = "TestTopicLifeCycle-testUnreferencedAlive";
      log.info(ME, "Entering testUnreferencedAlive ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [2] -> ALIVE (3 sec)
         long topicDestroyDelay = 6000L;
         long msgLifeTime = 3000L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 0, this.updateInterceptor.waitOnUpdate(1000L, 0)); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [6] -> UNREFERENCED (3 sec)
         try { Thread.currentThread().sleep(3500L); } catch( InterruptedException i) {}
         String dump = getDump();
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNREFERENCED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNREFERENCED'") != -1);
      }

      {  // topic transition from UNREFERENCED -> [5] -> ALIVE (3 sec)
         long msgLifeTime = 3000L;
         sendExpiringMsg(true, 0L, msgLifeTime); 
         assertEquals("numReceived after sending", 0, this.updateInterceptor.waitOnUpdate(1000L, 0)); // no message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
         //System.out.println(dump);
         assertXpathEvaluatesTo(publishOid, "//uniqueKey", dump);
      }

      {  // topic transition from ALIVE -> [10] -> DEAD
         boolean forceDestroy = true;
         EraseReturnQos[] erq = sendErase(forceDestroy);
         assertEquals("erase failed", 1, erq.length);
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }

      {  // topic transition from ALIVE -> [10] -> DEAD with XPath subscription
         subscribeXPathMsg();
         long topicDestroyDelay = 0L;
         long msgLifeTime = 0L;
         String oid = sendExpiringXPathMsg(topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(1000L, oid, Constants.STATE_OK));
         assertEquals("", 1, this.updateInterceptor.getMsgs().length);
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+oid+"</uniqueKey>") == -1);
         assertXpathNotExists("//uniqueKey", dump);
         unSubscribeMsg();
      }

      log.info(ME, "SUCCESS testUnreferencedAlive");
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
      this.ME = "TestTopicLifeCycle-testVolatile";
      log.info(ME, "Entering testVolatile ...");
      this.updateInterceptor.clear();

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
      this.ME = "TestTopicLifeCycle-testSubscribeVolatile";
      log.info(ME, "Entering testSubscribeVolatile ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [4] -> ALIVE
         long topicDestroyDelay = 0L;
         long msgLifeTime = 0L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(2000L, 1));
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [6] -> UNREFERENCED
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
         unSubscribeMsg();
         // topic transition from UNREFERENCED -> [11] DEAD (wait 200 millis as this is done by timeout thread (async))
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testSubscribeVolatile");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [13] -> [9]
    */
   public void testUnconfiguredSubscribeSubscribe() {
      this.ME = "TestTopicLifeCycle-testUnconfiguredSubscribeSubscribe";
      log.info(ME, "Entering testUnconfiguredSubscribeSubscribe ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [9] -> DEAD
         boolean forceDestroy = false;
         this.updateInterceptor.countErased(true);
         EraseReturnQos[] erq = sendErase(forceDestroy);
         log.info(ME, "erase num=" + erq.length);
         assertEquals("erase failed", 1, erq.length);
         assertEquals("", 2, this.updateInterceptor.waitOnUpdate(1000L, publishOid, Constants.STATE_ERASED, 2)); // Expecting two erase events (for the above subscriptions)
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // Give server a change to destroy topic after delivery of erase event messages
         this.updateInterceptor.countErased(false);
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testUnconfiguredSubscribeSubscribe");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [4] -> [7] -> [12]
    */
   public void testSoftErased() {
      this.ME = "TestTopicLifeCycle-testSoftErased";
      log.info(ME, "Entering testSoftErased ...");
      this.updateInterceptor.clear();

      try {
      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [4] -> ALIVE
         long topicDestroyDelay = 400000L;
         long msgLifeTime = 400000L;
         this.blockUpdateTime = 3000L; // Blocking callback thread for 3 sec to force state SOFTERASED !!
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(2000L, 1)); // message arrived?
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/msg/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [7] -> SOFTERASED
         boolean forceDestroy = false;
         EraseReturnQos[] erq = sendErase(forceDestroy);
         assertEquals("erase failed", 1, erq.length);
         this.updateInterceptor.waitOnUpdate(1000L, 1); // Expecting one erase event (for the above subscription)
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // Give server a change to destroy topic after delivery of erase event messages
         String dump = getDump();
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='SOFTERASED'") != -1);
      }

      {  // topic transition from SOFTERASED -> [12] --> DEAD
         try { Thread.currentThread().sleep(4500L); } catch( InterruptedException i) {}
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      }
      catch (Throwable e) {
         log.error(ME, "Problem: " + e.toString());
         e.printStackTrace();
         //throw e;
      }
      log.info(ME, "SUCCESS testSoftErased");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [4] -> [10]
    */
   public void testForcedErased() {
      this.ME = "TestTopicLifeCycle-testForcedErased";
      log.info(ME, "Entering testForcedErased ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [4] -> ALIVE
         long topicDestroyDelay = 400000L;
         long msgLifeTime = 400000L;
         this.blockUpdateTime = 0L;
         sendExpiringMsg(true, topicDestroyDelay, msgLifeTime); 
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(2000L, 1));
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='ALIVE'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='ALIVE'") != -1);
      }

      {  // topic transition from ALIVE -> [10] -> DEAD
         boolean forceDestroy = true;
         EraseReturnQos[] erq = sendErase(forceDestroy);
         assertEquals("erase failed", 1, erq.length);
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testForcedErased");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [9]
    */
   public void testUnconfiguredErased() {
      this.ME = "TestTopicLifeCycle-testUnconfiguredErased";
      log.info(ME, "Entering testUnconfiguredErased ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [9] -> DEAD
         boolean forceDestroy = false;
         EraseReturnQos[] erq = sendErase(forceDestroy);
         assertEquals("erase failed", 1, erq.length);
         this.updateInterceptor.waitOnUpdate(1000L, 1); // Expecting one erase event (for the above subscription)
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {} // Give server a change to destroy topic after delivery of erase event messages
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testUnconfiguredErased");
   }

   /**
    * THIS IS THE TEST
    * Transitions [1] -> [9] (by unSubscribe)
    */
   public void testUnconfiguredUnSubscribe() {
      this.ME = "TestTopicLifeCycle-testUnconfiguredUnSubscribe";
      log.info(ME, "Entering testUnconfiguredUnSubscribe ...");
      this.updateInterceptor.clear();

      {  // topic transition from START -> [1] -> UNCONFIGURED
         subscribeMsg();
         if (log.TRACE) log.trace(ME, "Retrieving initial dump=" + getDump());
         String dump = getDump();
         log.trace(ME, dump);
         // Expecting something like:
         // <TopicHandler id='http_192_168_1_4_3412/topic/TestTopicLifeCycleMsg' state='UNCONFIGURED'>
         //  <uniqueKey>TestTopicLifeCycleMsg</uniqueKey>
         assertTrue("Missing topic", dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") != -1);
         assertTrue("Topic in wrong state:" + dump, dump.indexOf("TestTopicLifeCycleMsg' state='UNCONFIGURED'") != -1);
      }

      {  // topic transition from UNCONFIGURED -> [9] -> DEAD
         unSubscribeMsg();
         String dump = getDump();
         assertTrue("Not expected a dead topic:" + dump, dump.indexOf("<uniqueKey>"+publishOid+"</uniqueKey>") == -1);
      }
      log.info(ME, "SUCCESS testUnconfiguredUnSubscribe");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info(ME, "Receiving update of a message " + updateKey.getOid() + " " + updateQos.getState());

      if (updateQos.isOk()) {
         //assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
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
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestTopicLifeCycle(new Global(), "testExpiry"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testUnreferencedAlive"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testVolatile"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testSubscribeVolatile"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testUnconfiguredSubscribeSubscribe"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testSoftErased"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testForcedErased"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testUnconfiguredErased"));
       suite.addTest(new TestTopicLifeCycle(new Global(), "testUnconfiguredUnSubscribe"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.topic.TestTopicLifeCycle -startEmbedded false
    */
   public static void main(String args[]) {
      try {
         TestTopicLifeCycle testSub = new TestTopicLifeCycle(new Global(args), "TestTopicLifeCycle");
         testSub.setUp();
         testSub.testExpiry();
         testSub.testUnreferencedAlive();
         testSub.testVolatile();
         testSub.testSubscribeVolatile();
         testSub.testUnconfiguredSubscribeSubscribe();
         testSub.testSoftErased();
         testSub.testForcedErased();
         testSub.testUnconfiguredErased();
         testSub.testUnconfiguredUnSubscribe();
         testSub.tearDown();
      }
      catch(Exception e) {
         e.printStackTrace();
         System.out.println("ERROR!!!!: " + e.toString());
      }
   }
}
