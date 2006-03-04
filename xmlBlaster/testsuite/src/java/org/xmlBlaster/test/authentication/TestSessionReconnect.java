/*------------------------------------------------------------------------------
Name:      TestSessionReconnect.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.runlevel.RunlevelManager;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client does test if a subscriber can reconnect to its session and 
 * its callback queue holded the messages during downtime. 
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionReconnect
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.authentication.TestSessionReconnect
 * </pre>
 */
public class TestSessionReconnect extends TestCase
{
   private static String ME = "TestSessionReconnect";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSessionReconnect.class.getName());
   private String passwd = "secret";
   private int serverPort = 7615;
   private String oid = "TestSessionReconnect.Msg";
   private EmbeddedXmlBlaster serverThread = null;
   private String sessionNameSub = "TestSessionReconnectSubscriber";
   private I_XmlBlasterAccess conSub;
   private I_XmlBlasterAccess conSub2;
   private MsgInterceptor updateInterceptorSub;

   private String sessionNamePub = "TestSessionReconnectPublisher";
   private I_XmlBlasterAccess conPub;

   /** For Junit */
   public TestSessionReconnect() {
      this(new Global(), "TestSessionReconnect");
   }

   /**
    * Constructs the TestSessionReconnect object.
    * <p />
    * @param testName   The name used in the test suite and to login to xmlBlaster
    */
   public TestSessionReconnect(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      glob.init(Util.getOtherServerPorts(serverPort));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing");
   }

   /**
    * Cleaning up. 
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(1000);} catch(Exception ex) {} 
      if (serverThread != null)
         serverThread.stopServer(true);
      // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    */
   public void testSessionReconnect() {
      log.info("testSessionReconnect("+sessionNameSub+") ...");

      try {
         log.info("============ STEP 1: Start subscriber");

         Global globSub = glob.getClone(null);
         // A testsuite helper to collect update messages
         this.updateInterceptorSub = new MsgInterceptor(globSub, log, null);

         conSub = globSub.getXmlBlasterAccess();
         
         ConnectReturnQos crqSub = null;
         {
            ConnectQos qosSub = new ConnectQos(globSub, sessionNameSub, passwd);

            CallbackAddress addr = new CallbackAddress(globSub);
            addr.setRetries(-1);
            String secretCbSessionId = "TrustMeSub";
            addr.setSecretCbSessionId(secretCbSessionId);
            qosSub.getSessionCbQueueProperty().setCallbackAddress(addr);

            log.info("First subscribe connect QoS = " + qosSub.toXml());
            crqSub = conSub.connect(qosSub, this.updateInterceptorSub); // Login to xmlBlaster
            log.info("Connect as subscriber '" + crqSub.getSessionName() + "' success");
         }

         SubscribeKey sk = new SubscribeKey(globSub, oid);
         SubscribeQos sq = new SubscribeQos(globSub);
         sq.setWantInitialUpdate(false);
         sq.setWantLocal(true);
         sq.setWantContent(true);
         
         HistoryQos historyQos = new HistoryQos(globSub);
         historyQos.setNumEntries(1);
         sq.setHistoryQos(historyQos);

         SubscribeReturnQos srq = conSub.subscribe(sk.toXml(), sq.toXml());
         log.info("Subscription to '" + oid + "' done");

         log.info("============ STEP 2: Start publisher");
         Global globPub = glob.getClone(null);
         conPub = globPub.getXmlBlasterAccess();
         ConnectQos qosPub = new ConnectQos(globPub, sessionNamePub, passwd);
         ConnectReturnQos crqPub = conPub.connect(qosPub, null);  // Login to xmlBlaster, no updates
         log.info("Connect success as " + crqPub.getSessionName());

         log.info("============ STEP 3: Stop subscriber callback");
         try {
            conSub.getCbServer().shutdown();
         }
         catch (XmlBlasterException e) {
            fail("ShutdownCB: " + e.getMessage());
         }

         log.info("============ STEP 4: Publish messages");
         int numPub = 8;
         MsgUnit[] sentArr = new MsgUnit[numPub];
         PublishReturnQos[] sentQos = new PublishReturnQos[numPub];
         for(int i=0; i<numPub; i++) {
            PublishKey pk = new PublishKey(globPub, oid, "text/xml", "1.0");
            pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
            PublishQos pq = new PublishQos(globPub);
            pq.setPriority(PriorityEnum.NORM_PRIORITY);
            pq.setPersistent(false);
            pq.setLifeTime(60000L);
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(globPub);
               topicProperty.setDestroyDelay(60000L);
               topicProperty.setCreateDomEntry(true);
               topicProperty.setReadonly(false);
               topicProperty.getHistoryQueueProperty().setMaxEntries(numPub+5);
               pq.setTopicProperty(topicProperty);
               log.info("Added TopicProperty on first publish: " + topicProperty.toXml());
            }

            byte[] content = "Hello".getBytes();
            MsgUnit msgUnit = new MsgUnit(pk, content, pq);
            sentArr[i] = msgUnit;
            PublishReturnQos prq = conPub.publish(msgUnit);
            sentQos[i] = prq;
            log.info("Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                        " for published message '" + prq.getKeyOid() + "'");
         }

         log.info("============ STEP 5: Start subscriber callback with same public sessionId");
         Global globSub2 = glob.getClone(null);
         MsgInterceptor updateInterceptorSub2 = new MsgInterceptor(globSub2, log, null);
         updateInterceptorSub2.setLogPrefix("TrustMeSub2");

         conSub2 = globSub2.getXmlBlasterAccess(); // Create a new client
         String secretCbSessionId2 = "TrustMeSub2";
         {
            ConnectQos qosSub = new ConnectQos(globSub, sessionNameSub, passwd);
            CallbackAddress addr = new CallbackAddress(globSub);
            addr.setRetries(-1);
            addr.setSecretCbSessionId(secretCbSessionId2);
            qosSub.getSessionCbQueueProperty().setCallbackAddress(addr);
            qosSub.getSessionQos().setSessionName(crqSub.getSessionQos().getSessionName());

            log.info("Second subscribe connect QoS = " + qosSub.toXml());
            ConnectReturnQos crqSub2 = conSub2.connect(qosSub, updateInterceptorSub2); // Login to xmlBlaster
            log.info("Connect as subscriber '" + crqSub2.getSessionName() + "' success");
         }

         assertEquals("", 0, updateInterceptorSub.count()); // The first login session should not receive anything

         assertEquals("", numPub, updateInterceptorSub2.waitOnUpdate(2000L, oid, Constants.STATE_OK));
         updateInterceptorSub2.compareToReceived(sentArr, secretCbSessionId2);
         updateInterceptorSub2.compareToReceived(sentQos);

         updateInterceptorSub2.clear();
      }
      catch (XmlBlasterException e) {
         log.severe(e.toString());
         fail(e.toString());
      }
      finally { // clean up
         log.info("Disconnecting '" + sessionNameSub + "'");
         conSub.disconnect(null);
         conSub2.disconnect(null);
      }
      log.info("Success in testSessionReconnect()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "TestSessionReconnect";
       suite.addTest(new TestSessionReconnect(Global.instance(), "testSessionReconnect"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.authentication.TestSessionReconnect
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionReconnect
    * <pre>
    */
   public static void main(String args[]) {
      TestSessionReconnect testSub = new TestSessionReconnect(new Global(args), "TestSessionReconnect");
      testSub.setUp();
      testSub.testSessionReconnect();
      testSub.tearDown();
   }
}

