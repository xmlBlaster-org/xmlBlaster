package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

// for client connections:
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


import java.util.Vector;
import java.io.File;

import junit.framework.*;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.PublishTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class PublishTest extends TestCase {
   private String ME = "PublishTest";
   private Global glob;
   private static Logger log = Logger.getLogger(PublishTest.class.getName());
   private ServerHelper serverHelper;

   private I_XmlBlasterAccess heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private int updateCounterBilbo = 0;
   private String oid = "PublishToBilbo";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "We win";

   private String assertInUpdate = null;

   public PublishTest(String name) {
      super(name);
      this.glob = new Global(null, true, false);
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {

      log.info("Entering setUp(), test starts");

      serverHelper = new ServerHelper(glob, log, ME);

      // Starts a cluster node
      serverHelper.startHeron();
      //serverHelper.startAvalon();
      //serverHelper.startGolan();
      serverHelper.startFrodo();
      serverHelper.startBilbo();
   }

   /**
    * cleaning up ...
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time

      if (bilboCon != null) { bilboCon.disconnect(null); bilboCon = null; }
      if (frodoCon != null) { frodoCon.disconnect(null); frodoCon = null; }
      if (golanCon != null) { golanCon.disconnect(null); golanCon = null; }
      if (avalonCon != null) { avalonCon.disconnect(null); avalonCon = null; }
      if (heronCon != null) { heronCon.disconnect(null); heronCon = null; }

      serverHelper.tearDown();
   }

   /**
    * We start all nodes as described in requirement
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * publish a message to bilbo which should be routed to heron.
    * Than we try to access the message at heron
    */ 
   public void testPublish() {
      System.err.println("***PublishTest: Publish a message to a cluster slave ...");
      try {
         bilboCon = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = serverHelper.getBilboGlob().getId() + ": Should not receive the message '" + updateKey.getOid() + "'";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         PublishKey pk = new PublishKey(glob, oid, "text/plain", "1.0");
         pk.setDomain(domain);
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, contentStr, pq);
         PublishReturnQos prq = bilboCon.publish(msgUnit);
         log.info("Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node bilbo with IP=" + serverHelper.getBilboGlob().getProperty().get("bootstrapPort",0) +
                                    ", the returned QoS is: " + prq.getKeyOid());

         heronCon = serverHelper.connect(serverHelper.getHeronGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.severe("Receive message '" + updateKey.getOid() + "'");
                  assertInUpdate = serverHelper.getHeronGlob().getId() + ": Did not expect message update in default handler";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         System.err.println("->Check if the message has reached the master node heron ...");
         GetKey gk = new GetKey(glob, oid);
         MsgUnit[] msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         assertTrue("Invalid message oid returned", msgs[0].getKey().indexOf(oid) > 0);
         log.info("SUCCESS: Got message:" + msgs[0].getKey());

         System.err.println("->Check if the message is available at the slave node bilbo ...");
         gk = new GetKey(glob, oid);
         gk.setDomain(domain);
         msgs = bilboCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         log.info("SUCCESS: Got message:" + msgs[0].getKey());

         System.err.println("->Trying to erase the message at the slave node ...");
         EraseKey ek = new EraseKey(glob, oid);
         ek.setDomain(domain);
         EraseQos eq = new EraseQos(glob);
         bilboCon.erase(ek.toXml(), eq.toXml());

         // Check if erased ...
         gk = new GetKey(glob, oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info("SUCCESS: Got no message after erase");

         System.err.println("***PublishTest: Publish a message to a cluster slave - frodo is offline ...");

         System.err.println("->Subscribe from heron, the message is currently erased ...");
         SubscribeKey sk = new SubscribeKey(glob, oid);
         sk.setDomain(domain);
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos srq = heronCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               assertInUpdate = serverHelper.getHeronGlob().getId() + ": Receiving unexpected asynchronous update message";
               assertEquals(assertInUpdate, oid, updateKey.getOid());
               assertInUpdate = serverHelper.getHeronGlob().getId() + ": Receiving corrupted asynchronous update message";
               assertEquals(assertInUpdate, contentStr, new String(content));
               log.info("heronCon - Receiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler, state=" + updateQos.getState());
               updateCounterHeron++;
               assertInUpdate = null;
               return "";
            }
         });  // subscribe with our specific update handler
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         serverHelper.stopFrodo();

         System.err.println("->Check: heron hasn't got the message ...");
         gk = new GetKey(glob, oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info("SUCCESS: Got no message after erase");

         // publish again ...
         pk = new PublishKey(glob, oid, "text/plain", "1.0");
         pk.setDomain(domain);
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk.toXml(), contentStr.getBytes(), pq.toXml());
         prq = bilboCon.publish(msgUnit);
         log.info("Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node bilbo with IP=" + serverHelper.getBilboGlob().getProperty().get("bootstrapPort",0) +
                                    ", the returned QoS is: " + prq.getKeyOid());


         assertEquals("heron is not reachable, publish should not have come through", 0, updateCounterHeron);

         serverHelper.startFrodo();

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("heron has not received message", 1, updateCounterHeron);
         updateCounterHeron = 0;

         System.err.println("->Connect to frodo ...");
         frodoCon = serverHelper.connect(serverHelper.getFrodoGlob(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = serverHelper.getFrodoGlob().getId() + ": Receive unexpected message '" + updateKey.getOid() + "'";
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         System.err.println("->Subscribe from frodo, is he able to organize it?");
         sk = new SubscribeKey(glob, oid);
         sk.setDomain(domain);
         sq = new SubscribeQos(glob);
         srq = frodoCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("frodoCon - Receiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler, state=" + updateQos.getState());
               updateCounterFrodo++;
               assertInUpdate = null;
               return "";
            }
         });  // subscribe with our specific update handler
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         try { Thread.currentThread().sleep(5000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("frodo is reachable again, subscribe should work", 1, updateCounterFrodo);
        
         updateCounterHeron = 0;
         updateCounterFrodo = 0;
         updateCounterBilbo = 0;

         System.err.println("->Check unSubscribe from client frodo ...");
         UnSubscribeKey uk = new UnSubscribeKey(glob, srq.getSubscriptionId());
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         frodoCon.unSubscribe(uk.toXml(), uq.toXml());

         System.err.println("->Check publish, frodo should not get it ...");
         pk = new PublishKey(glob, oid, "text/plain", "1.0", domain);
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk, contentStr, pq);
         prq = frodoCon.publish(msgUnit);
         log.info("Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node frodo with IP=" + serverHelper.getFrodoGlob().getProperty().get("bootstrapPort",0) +
                                    ", the returned QoS is: " + prq.getKeyOid());

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("frodo is unSubscribed and should not receive message", 0, updateCounterFrodo);
         assertEquals("heron has not received message", 1, updateCounterHeron);


      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("PublishToBilbo-Exception: " + e.toString());
      }
      /* is done in tearDown
      finally {
         if (bilboCon != null) {
            bilboCon.disconnect(null);
            bilboCon = null;
         }
      }
      */

      System.err.println("***PublishTest: testPublish [SUCCESS]");
   }

   /**
    * setUp() and tearDown() are ivoked between each test...() method
    */
    /*
   public void testDummy() {
      System.err.println("***PublishTest: testDummy [SUCCESS]");
   }
     */
}
