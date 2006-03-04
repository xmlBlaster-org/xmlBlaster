/*------------------------------------------------------------------------------
Name:      TestPtPSubscribable.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * Here we test how to make PtP messages invisible to subscribers using the <i>subscribable</i> QoS. 
 * <p>
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtPSubscribable
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtPSubscribable
 * </pre>
 */
public class TestPtPSubscribable extends TestCase
{
   private static String ME = "TestPtPSubscribable";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPtPSubscribable.class.getName());
   private String passwd = "secret";
   private int serverPort = 7615;
   private String oid = "TestPtPSubscribable.Msg";
   private EmbeddedXmlBlaster serverThread = null;
   private String sessionNameRcv = "TestPtPSubscribableReceiver";
   private I_XmlBlasterAccess conRcv;
   private boolean connectedRcv = false;
   private MsgInterceptor updateInterceptorRcv;

   private String sessionNameSnd = "TestPtPSubscribableSender";
   private I_XmlBlasterAccess conSnd;
   private MsgInterceptor updateInterceptorSnd;

   /** For Junit */
   public TestPtPSubscribable() {
      this(new Global(), "TestPtPSubscribable");
   }

   /**
    * Constructs the TestPtPSubscribable object.
    * <p />
    * @param testName   The name used in the test suite and to login to xmlBlaster
    */
   public TestPtPSubscribable(Global glob, String testName) {
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
   public void testSubscribable() {
      log.info("testSubscribable("+sessionNameRcv+") ...");

      try {
         log.info("============ STEP 1: Start publisher client");
         Global globSnd = glob.getClone(null);
         conSnd = globSnd.getXmlBlasterAccess();
         ConnectQos qosSnd = new ConnectQos(globSnd);
         String secretSndCbSessionId = "TrustMeSubSnd";
         CallbackAddress addrSnd = new CallbackAddress(globSnd);
         addrSnd.setSecretCbSessionId(secretSndCbSessionId);
         qosSnd.getSessionCbQueueProperty().setCallbackAddress(addrSnd);
         this.updateInterceptorSnd = new MsgInterceptor(globSnd, log, null);
         ConnectReturnQos crqPub = conSnd.connect(qosSnd, this.updateInterceptorSnd);
         log.info("Connect success as " + crqPub.getSessionName());

         log.info("============ STEP 2: Subscribe in Pub/Sub mode");
         SubscribeKey sk = new SubscribeKey(globSnd, oid);
         SubscribeQos sq = new SubscribeQos(globSnd);
         sq.setWantInitialUpdate(false);
         sq.setWantLocal(true);
         SubscribeReturnQos srq = conSnd.subscribe(sk, sq);
         log.info("Subscription to '" + oid + "' done");
         assertEquals("", 0, this.updateInterceptorSnd.waitOnUpdate(1000L, oid, Constants.STATE_OK));

         log.info("============ STEP 3: Start receiver");
         Global globRcv = glob.getClone(null);
         conRcv = globRcv.getXmlBlasterAccess();
         ConnectQos qosRcv = new ConnectQos(globRcv, sessionNameRcv, passwd);
         CallbackAddress addr = new CallbackAddress(globRcv);
         addr.setRetries(-1);
         String secretCbSessionId = "TrustMeSub";
         addr.setSecretCbSessionId(secretCbSessionId);
         qosRcv.getSessionCbQueueProperty().setCallbackAddress(addr);
         this.updateInterceptorRcv = new MsgInterceptor(globRcv, log, null);
         ConnectReturnQos crqRcv = conRcv.connect(qosRcv, this.updateInterceptorRcv); // Login to xmlBlaster
         log.info("Connect as subscriber '" + crqRcv.getSessionName() + "' success");

         {
            log.info("============ STEP 4: Publish PtP message which is NOT subscribable");
            PublishKey pk = new PublishKey(globSnd, oid, "text/xml", "1.0");
            PublishQos pq = new PublishQos(globSnd);
            Destination dest = new Destination(globSnd, new SessionName(globSnd, sessionNameRcv));
            pq.addDestination(dest);
            pq.setSubscribable(false);
            byte[] content = "Hello".getBytes();
            MsgUnit msgUnit = new MsgUnit(pk, content, pq);
            PublishReturnQos prq = conSnd.publish(msgUnit);
            log.info("Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                         " for published message '" + prq.getKeyOid() + "'");
            assertEquals("", 1, this.updateInterceptorRcv.waitOnUpdate(1000L, oid, Constants.STATE_OK));
            assertEquals("", secretCbSessionId, this.updateInterceptorRcv.getMsg(oid, Constants.STATE_OK).getCbSessionId());
            assertEquals("", 0, this.updateInterceptorSnd.waitOnUpdate(1000L, oid, Constants.STATE_OK));
            
            this.updateInterceptorRcv.clear();
            this.updateInterceptorSnd.clear();
         }

         {
            log.info("============ STEP 5: Publish PtP message which IS subscribable");
            PublishKey pk = new PublishKey(globSnd, oid, "text/xml", "1.0");
            PublishQos pq = new PublishQos(globSnd);
            Destination dest = new Destination(globSnd, new SessionName(globSnd, sessionNameRcv));
            pq.addDestination(dest);
            pq.setSubscribable(true);
            byte[] content = "Hello".getBytes();
            MsgUnit msgUnit = new MsgUnit(pk, content, pq);
            PublishReturnQos prq = conSnd.publish(msgUnit);
            log.info("Got status='" + prq.getState() + "' rcvTimestamp=" + prq.getRcvTimestamp().toString() +
                         " for published message '" + prq.getKeyOid() + "'");

            assertEquals("", 1, this.updateInterceptorRcv.waitOnUpdate(1000L, oid, Constants.STATE_OK));
            assertEquals("", secretCbSessionId, this.updateInterceptorRcv.getMsg(oid, Constants.STATE_OK).getCbSessionId());
            assertEquals("", 1, this.updateInterceptorSnd.waitOnUpdate(1000L, oid, Constants.STATE_OK));
            assertEquals("", secretSndCbSessionId, this.updateInterceptorSnd.getMsg(oid, Constants.STATE_OK).getCbSessionId());
            
            this.updateInterceptorRcv.clear();
            this.updateInterceptorSnd.clear();
         }
      }
      catch (XmlBlasterException e) {
         log.severe(e.toString());
         fail(e.toString());
      }
      finally { // clean up
         log.info("Disconnecting '" + sessionNameRcv + "'");
         if (conRcv != null) conRcv.disconnect(null);
         if (conSnd != null) conSnd.disconnect(null);
      }
      log.info("Success in testSubscribable()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "TestPtPSubscribable";
       suite.addTest(new TestPtPSubscribable(Global.instance(), "testSubscribable"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.qos.TestPtPSubscribable
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtPSubscribable
    * <pre>
    */
   public static void main(String args[]) {
      TestPtPSubscribable testSub = new TestPtPSubscribable(new Global(args), "TestPtPSubscribable");
      testSub.setUp();
      testSub.testSubscribable();
      testSub.tearDown();
   }
}

