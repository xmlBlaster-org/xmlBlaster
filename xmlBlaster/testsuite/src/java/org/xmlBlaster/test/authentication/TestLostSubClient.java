/*------------------------------------------------------------------------------
Name:      TestLostSubClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.EraseKeyWrapper;
import org.xmlBlaster.client.EraseQosWrapper;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client does test callbacks for two sessions and dead letters. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner authentication.TestLostSubClient
 *    java junit.swingui.TestRunner authentication.TestLostSubClient
 * </pre>
 */
public class TestLostSubClient extends TestCase
{
   private static String ME = "TestLostSubClient";
   private final Global glob;
   private final LogChannel log;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private XmlBlasterConnection lostCon = null;
   private XmlBlasterConnection publisherCon = null;
   private String assertInUpdate = null;
   private String oid = "TestLostSubClient-msg";
   private int deadLetterCounter = 0;

   /**
    * Constructs the TestLostSubClient object.
    */
   public TestLostSubClient(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog(null);
   }

   /**
    */
   protected void setUp() {
      glob.init(Util.getOtherServerPorts(serverPort));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);
   }

   /**
    */
   protected void tearDown() {
      if (publisherCon != null) {
         try {
            EraseKeyWrapper ek = new EraseKeyWrapper(oid);
            EraseQosWrapper eq = new EraseQosWrapper();
            publisherCon.erase(ek.toXml(), eq.toXml());
         } catch (XmlBlasterException e) {
            log.error(ME, e.toString());
         }
         publisherCon.disconnect(null);
         publisherCon = null;
      }

      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      Util.resetPorts(); // reset to default server port (necessary if other tests follow in the same JVM).
   }

   /**
    */
   public void testLostSessionWithSubscription() {
      log.info(ME, "testLostSessionWithSubscription() ...");
      try {

         {
            log.info(ME, "Connecting as subscriber which diappears ...");
            lostCon = new XmlBlasterConnection(glob);
            ConnectQos qos = new ConnectQos(glob, "disappearingSubscriber", "sercret");
            CallbackAddress cbAddress = new CallbackAddress(glob);
            cbAddress.setPingInterval(1000L);
            qos.getCbQueueProperty().setCallbackAddress(cbAddress);
            /*
            // Setup fail save handling ...
            //qos.getAddress().setPingInterval(60000L);
            Address addressProp = new Address(glob);
            addressProp.setDelay(4000L);      // retry connecting every 4 sec
            addressProp.setRetries(-1);       // -1 == forever
            addressProp.setPingInterval(-1L); // switched off
            addressProp.setMaxMsg(1000);      // queue up to 1000 messages
            con.initFailSave(this);
            connectQos.setAddress(addressProp);
            */
            assertInUpdate = null;
            lostCon.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     assertInUpdate = glob.getId() + ": Did not expect message update in first handler";
                     fail(assertInUpdate); // This is routed to server, not to junit
                     return "";
                  }
               });

            String subscriptionStr = "<key oid='' queryType='XPATH'>" +
                                     "/xmlBlaster/key[starts-with(@oid,'"+oid+"')]" +
                                     "</key>";
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos sr1 = lostCon.subscribe(subscriptionStr, sq.toXml());

            try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
            assertTrue(assertInUpdate, assertInUpdate == null);
            lostCon.shutdownCb();
         }

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait some time

         {
            log.info(ME, "Connecting as publisher and dead letter checker ...");
            assertInUpdate = null;
            ConnectQos qos = new ConnectQos(glob, "publisher", "secret");
            publisherCon = new XmlBlasterConnection(glob);
            publisherCon.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     assertInUpdate = glob.getId() + "Reveiving asynchronous message '" + updateKey.getOid() + "' in second handler";
                     log.info(ME, assertInUpdate);
                     return "";
                  }
               });

            SubscribeKeyWrapper sk = new SubscribeKeyWrapper(Constants.OID_DEAD_LETTER);
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos srDeadLetter = publisherCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  deadLetterCounter++;
                  log.info(ME, "Reveiving asynchronous message '" + updateKey.getOid() + "' in deadLetter handler, content=" + new String(content));
                  assertEquals("No dead letter received", Constants.OID_DEAD_LETTER, updateKey.getOid());
                  return "";
               }
            });  // subscribe with our specific update handler

            publish();
         }

         try { Thread.currentThread().sleep(4000); } catch( InterruptedException i) {} // Wait some time
         publish();
         try { Thread.currentThread().sleep(4000); } catch( InterruptedException i) {} // Wait some time
         publish();

         try { Thread.currentThread().sleep(1000000); } catch( InterruptedException i) {} // Wait some time
         /*
         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("DeadLetter is missing", 1, deadLetterCounter);
         assertTrue("Update is missing", assertInUpdate != null);

         try {
            lostCon.get("<key oid='__cmd:?freeMem'/>", null);
            fail("XmlBlaster should have killed us because of callback problems");
         }
         catch (XmlBlasterException e) {
            log.info(ME, "OK, expected destroyed session: " + e.toString());
         }
         */
         
      }
      catch (XmlBlasterException e) {
         fail("SessionCb test failed: " + e.toString());
      }
      log.info(ME, "Success in testLostSessionWithSubscription()");
   }

   private void publish() throws XmlBlasterException {
      PublishKeyWrapper pk = new PublishKeyWrapper(oid, "text/plain", "1.0");
      PublishQosWrapper pq = new PublishQosWrapper();
      MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
      PublishRetQos retQos = publisherCon.publish(msgUnit);
      log.info(ME, "Published message oid=" + oid);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       Global glob = new Global();
       suite.addTest(new TestLostSubClient(glob, "testLostSessionWithSubscription"));
       //suite.addTest(new TestLostSubClient(glob, "testLostSessionWithSubscription")); // Run it twice
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java authentication.TestLostSubClient
    *   java -Djava.compiler= junit.textui.TestRunner authentication.TestLostSubClient
    * <pre>
    */
   public static void main(String args[]) {
      TestLostSubClient testSub = new TestLostSubClient(new Global(args), "testLostSessionWithSubscription");
      testSub.setUp();
      testSub.testLostSessionWithSubscription();
      //testSub.testLostSessionWithSubscription();
      testSub.tearDown();
   }
}

