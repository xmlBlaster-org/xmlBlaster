/*------------------------------------------------------------------------------
Name:      TestPtDQueueRedeliver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
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
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.RunlevelManager;

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
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtDQueueRedeliver
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtDQueueRedeliver
 * </pre>
 */
public class TestPtDQueueRedeliver extends TestCase
{
   private static String ME = "TestPtDQueueRedeliver";
   private final Global glob;
   private final LogChannel log;
   private String passwd = "secret";
   private int serverPort = 7615;
   private String oid = "TestPtDQueueRedeliver.Msg";
   private EmbeddedXmlBlaster serverThread = null;
   private String sessionNameRcv = "TestPtDQueueRedeliverReceiver";
   private XmlBlasterConnection conRcv;
   private boolean connectedRcv = false;
   private MsgInterceptor updateInterceptorRcv;

   private String sessionNameSnd = "TestPtDQueueRedeliverSender";
   private XmlBlasterConnection conSnd;
   private MsgInterceptor updateInterceptorSnd;

   /** For Junit */
   public TestPtDQueueRedeliver() {
      this(new Global(), "TestPtDQueueRedeliver");
   }

   /**
    * Constructs the TestPtDQueueRedeliver object.
    * <p />
    * @param testName   The name used in the test suite and to login to xmlBlaster
    */
   public TestPtDQueueRedeliver(Global glob, String testName) {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog(null);
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      glob.init(Util.getOtherServerPorts(serverPort));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing");
   }

   /**
    * Cleaning up. 
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(1000);} catch(Exception ex) {} 
      if (serverThread != null)
         serverThread.stopServer(true);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * xmlBlaster starts and sender sends persistent and forceQueuing PtP message.
    * xmlBlaster stops and starts again
    * receiver start and should receive the message
    */
   public void testPersitentPtp() {
      log.info(ME, "testPersitentPtp("+sessionNameRcv+") ...");

      try {

         log.info(ME, "============ STEP 1: Start publisher");
         conSnd = new XmlBlasterConnection(glob);
         ConnectQos qosPub = new ConnectQos(glob);
         ConnectReturnQos crqPub = conSnd.connect(qosPub, null);  // Login to xmlBlaster, no updates
         log.info(ME, "Connect success as " + crqPub.getSessionName());

         int numPub = 8;
         log.info(ME, "============ STEP 2: Publish " + numPub + " PtP messages");
         MsgUnit[] sentArr = new MsgUnit[numPub];
         PublishReturnQos[] sentQos = new PublishReturnQos[numPub];
         for(int i=0; i<numPub; i++) {
            PublishKey pk = new PublishKey(glob, oid, "text/xml", "1.0");
            pk.setClientTags("<org.xmlBlaster><demo/></org.xmlBlaster>");
            
            PublishQos pq = new PublishQos(glob);
            pq.setPriority(PriorityEnum.NORM_PRIORITY);
            pq.setPersistent(true);
            Destination dest = new Destination(glob, new SessionName(glob, sessionNameRcv));
            dest.forceQueuing(true);
            pq.addDestination(dest);
            pq.setForceUpdate(true);
            pq.setIsSubscribeable(false);
            pq.setLifeTime(60000L);
            
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(glob);
               topicProperty.setDestroyDelay(60000L);
               topicProperty.setCreateDomEntry(true);
               topicProperty.setReadonly(false);
               topicProperty.getHistoryQueueProperty().setMaxMsg(numPub+5);
               pq.setTopicProperty(topicProperty);
               log.info(ME, "Added TopicProperty on first publish: " + topicProperty.toXml());
            }

            byte[] content = "Hello".getBytes();
            MsgUnit msgUnit = new MsgUnit(glob, pk, content, pq);
            sentArr[i] = msgUnit;
            PublishReturnQos prq = conSnd.publish(msgUnit);
            sentQos[i] = prq;
            log.info(ME, "Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                        " for published message '" + prq.getKeyOid() + "'");
         }

         log.info(ME, "============ STEP 3: Stop xmlBlaster");
         this.serverThread.stopServer(true);

         log.info(ME, "============ STEP 4: Start xmlBlaster");
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info(ME, "XmlBlaster is ready for testing");

         log.info(ME, "============ STEP 5: Start subscriber");
         // A testsuite helper to collect update messages
         this.updateInterceptorRcv = new MsgInterceptor(glob, log, null);

         conRcv = new XmlBlasterConnection(glob);
         
         ConnectQos qosSub = new ConnectQos(glob, sessionNameRcv, passwd);

         CallbackAddress addr = new CallbackAddress(glob);
         addr.setRetries(-1);
         String secretCbSessionId = "TrustMeSub";
         addr.setSecretCbSessionId(secretCbSessionId);
         qosSub.getSessionCbQueueProperty().setCallbackAddress(addr);

         ConnectReturnQos crqSub = conRcv.connect(qosSub, this.updateInterceptorRcv); // Login to xmlBlaster
         log.info(ME, "Connect as subscriber '" + crqSub.getSessionName() + "' success");

         SubscribeKey sk = new SubscribeKey(glob, oid);
         SubscribeQos sq = new SubscribeQos(glob);
         sq.setWantInitialUpdate(false);
         sq.setWantLocal(true);
         sq.setWantContent(true);
         
         HistoryQos historyQos = new HistoryQos(glob);
         historyQos.setNumEntries(1);
         sq.setHistoryQos(historyQos);

         SubscribeReturnQos srq = conRcv.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "Subscription to '" + oid + "' done");

         log.info(ME, "============ STEP 6: Check if messages arrived");
         assertEquals("", numPub, this.updateInterceptorRcv.waitOnUpdate(2000L, oid, Constants.STATE_OK));
         updateInterceptorRcv.compareToReceived(sentArr, secretCbSessionId);
         updateInterceptorRcv.compareToReceived(sentQos);

         updateInterceptorRcv.clear();
      }
      catch (XmlBlasterException e) {
         log.error(ME, e.toString());
         fail(e.toString());
      }
      finally { // clean up
         log.info(ME, "Disconnecting '" + sessionNameRcv + "'");
         if (conRcv != null) conRcv.disconnect(null);
      }
      log.info(ME, "Success in testPersitentPtp()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "TestPtDQueueRedeliver";
       suite.addTest(new TestPtDQueueRedeliver(Global.instance(), "testPersitentPtp"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.qos.TestPtDQueueRedeliver
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtDQueueRedeliver
    * <pre>
    */
   public static void main(String args[]) {
      TestPtDQueueRedeliver testSub = new TestPtDQueueRedeliver(new Global(args), "TestPtDQueueRedeliver");
      testSub.setUp();
      testSub.testPersitentPtp();
      testSub.tearDown();
   }
}

