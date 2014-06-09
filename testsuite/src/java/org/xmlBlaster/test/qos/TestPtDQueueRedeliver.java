/*------------------------------------------------------------------------------
Name:      TestPtDQueueRedeliver.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.qos.address.Destination;


/**
 * This client does test if a subscriber can reconnect to its session and 
 * its callback queue holded the messages during downtime. 
 * <p>
 * See method testPersistentPtp() for a description.
 * </p>
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
   private static Logger log = Logger.getLogger(TestPtDQueueRedeliver.class.getName());
   private String passwd = "secret";
   private int serverPort = 7615;
   private String oid = "TestPtDQueueRedeliver.Msg";
   private EmbeddedXmlBlaster serverThread = null;
   private String sessionNameRcv = "TestPtDQueueRedeliverReceiver";
   private I_XmlBlasterAccess conRcv;
   private boolean connectedRcv = false;
   private MsgInterceptor updateInterceptorRcv;

   private String sessionNameSnd = "TestPtDQueueRedeliverSender";
   private I_XmlBlasterAccess conSnd;
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
      try { Thread.sleep(1000);} catch(Exception ex) {} 
      if (serverThread != null)
         serverThread.stopServer(true);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * <p>
    * 1. xmlBlaster starts and sender sends persistent and forceQueuing PtP message.
    * </p>
    * <p>
    * 2. xmlBlaster stops and starts again
    * </p>
    * <p>
    * 3. receiver start and should receive the message
    * </p>
    */
   public void testPersistentPtp() {
      log.info("testPersistentPtp("+sessionNameRcv+") ...");

      try {

         log.info("============ STEP 1: Start publisher");
         conSnd = glob.getXmlBlasterAccess();
         ConnectQos qosPub = new ConnectQos(glob);
         ConnectReturnQos crqPub = conSnd.connect(qosPub, null);  // Login to xmlBlaster, no updates
         log.info("Connect success as " + crqPub.getSessionName());

         int numPub = 8;
         log.info("============ STEP 2: Publish " + numPub + " PtP messages");
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
            pq.setSubscribable(false);
            pq.setLifeTime(60000L);
            
            if (i == 0) {
               TopicProperty topicProperty = new TopicProperty(glob);
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
            PublishReturnQos prq = conSnd.publish(msgUnit);
            sentQos[i] = prq;
            log.info("Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                        " for published message '" + prq.getKeyOid() + "'");
         }

         log.info("============ STEP 3: Stop xmlBlaster");
         this.serverThread.stopServer(true);

         log.info("============ STEP 4: Start xmlBlaster");
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing");

         log.info("============ STEP 5: Start subscriber");
         // A testsuite helper to collect update messages
         this.updateInterceptorRcv = new MsgInterceptor(glob, log, null);

         Global globRcv = glob.getClone(null);
         conRcv = globRcv.getXmlBlasterAccess();
         
         ConnectQos qosSub = new ConnectQos(globRcv, sessionNameRcv, passwd);

         CallbackAddress addr = new CallbackAddress(globRcv);
         addr.setRetries(-1);
         String secretCbSessionId = "TrustMeSub";
         addr.setSecretCbSessionId(secretCbSessionId);
         qosSub.getSessionCbQueueProperty().setCallbackAddress(addr);

         ConnectReturnQos crqSub = conRcv.connect(qosSub, this.updateInterceptorRcv); // Login to xmlBlaster
         log.info("Connect as subscriber '" + crqSub.getSessionName() + "' success");
         log.info(this.updateInterceptorRcv.toString());
         
         /*
         SubscribeKey sk = new SubscribeKey(globRcv, oid);
         SubscribeQos sq = new SubscribeQos(globRcv);
         sq.setWantInitialUpdate(false);
         sq.setWantLocal(true);
         sq.setWantContent(true);
         
         HistoryQos historyQos = new HistoryQos(globRcv);
         historyQos.setNumEntries(1);
         sq.setHistoryQos(historyQos);

         SubscribeReturnQos srq = conRcv.subscribe(sk.toXml(), sq.toXml());
         log.info("Subscription to '" + oid + "' done");
         */

         log.info("============ STEP 6: Check if messages arrived");
         assertEquals("", numPub, this.updateInterceptorRcv.waitOnUpdate(4000L, oid, Constants.STATE_OK));
         this.updateInterceptorRcv.compareToReceived(sentArr, secretCbSessionId);
         this.updateInterceptorRcv.compareToReceived(sentQos);

         this.updateInterceptorRcv.clear();
      }
      catch (XmlBlasterException e) {
         log.severe(e.toString());
         e.printStackTrace();
         fail(e.toString());
      }
      finally { // clean up
         log.info("Disconnecting '" + sessionNameRcv + "'");
         if (conRcv != null) conRcv.disconnect(null);
      }
      log.info("Success in testPersistentPtp()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "TestPtDQueueRedeliver";
       suite.addTest(new TestPtDQueueRedeliver(Global.instance(), "testPersistentPtp"));
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
      testSub.testPersistentPtp();
      testSub.tearDown();
   }
}

