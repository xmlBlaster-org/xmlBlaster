package org.xmlBlaster.test.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

// for client connections:
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.*;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;


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
   private LogChannel log;
   private ServerHelper serverHelper;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private int updateCounterBilbo = 0;
   private String oid = "PublishToBilbo";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "We win";

   private String assertInUpdate = null;

   public PublishTest(String name) {
      super(name);
      this.glob = new Global();
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {
      log = glob.getLog(ME);
      log.info(ME, "Entering setUp(), test starts");

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
      log.info(ME, "Entering tearDown(), test is finished");
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

         PublishKeyWrapper pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), contentStr.getBytes(), pq.toXml());
         PublishRetQos prq = bilboCon.publish(msgUnit);
         log.info(ME+":"+serverHelper.getBilboGlob().getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node with IP=" + serverHelper.getBilboGlob().getProperty().get("port",0) +
                                    ", the returned QoS is: " + prq.getOid());

         heronCon = serverHelper.connect(serverHelper.getHeronGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.error(ME+":"+serverHelper.getHeronGlob().getId(), "Receive message '" + updateKey.getOid() + "'");
                  assertInUpdate = serverHelper.getHeronGlob().getId() + ": Did not expect message update in default handler";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         System.err.println("->Check if the message has reached the master node heron ...");
         GetKeyWrapper gk = new GetKeyWrapper(oid);
         MessageUnit[] msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         assertTrue("Invalid message oid returned", msgs[0].getXmlKey().indexOf(oid) > 0);
         log.info(ME+":"+serverHelper.getHeronGlob().getId(), "SUCCESS: Got message:" + msgs[0].getXmlKey());

         System.err.println("->Check if the message is available at the slave node bilbo ...");
         gk = new GetKeyWrapper(oid);
         gk.setDomain(domain);
         msgs = bilboCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         log.info(ME+":"+serverHelper.getBilboGlob().getId(), "SUCCESS: Got message:" + msgs[0].getXmlKey());

         System.err.println("->Trying to erase the message at the slave node ...");
         EraseKeyWrapper ek = new EraseKeyWrapper(oid);
         ek.setDomain(domain);
         EraseQosWrapper eq = new EraseQosWrapper();
         bilboCon.erase(ek.toXml(), eq.toXml());

         // Check if erased ...
         gk = new GetKeyWrapper(oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info(ME+":"+serverHelper.getHeronGlob().getId(), "SUCCESS: Got no message after erase");

         System.err.println("***PublishTest: Publish a message to a cluster slave - frodo is offline ...");

         System.err.println("->Subscribe from heron, the message is currently erased ...");
         SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
         sk.setDomain(domain);
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         SubscribeRetQos srq = heronCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               assertInUpdate = serverHelper.getHeronGlob().getId() + ": Reveiving unexpected asynchronous update message";
               assertEquals(assertInUpdate, oid, updateKey.getOid());
               assertInUpdate = serverHelper.getHeronGlob().getId() + ": Reveiving corrupted asynchronous update message";
               assertEquals(assertInUpdate, contentStr, new String(content));
               log.info(ME+":"+serverHelper.getHeronGlob().getId(), "Reveiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler");
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
         gk = new GetKeyWrapper(oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info(ME+":"+serverHelper.getHeronGlob().getId(), "SUCCESS: Got no message after erase");

         // publish again ...
         pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), contentStr.getBytes(), pq.toXml());
         prq = bilboCon.publish(msgUnit);
         log.info(ME+":"+serverHelper.getBilboGlob().getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node with IP=" + serverHelper.getBilboGlob().getProperty().get("port",0) +
                                    ", the returned QoS is: " + prq.getOid());


         assertEquals("heron is not reachable, publish should not have come through", 0, updateCounterHeron);

         serverHelper.startFrodo();

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
         sk = new SubscribeKeyWrapper(oid);
         sk.setDomain(domain);
         sq = new SubscribeQosWrapper();
         srq = frodoCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info(ME+":"+serverHelper.getFrodoGlob().getId(), "Reveiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler");
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
         UnSubscribeKeyWrapper uk = new UnSubscribeKeyWrapper(srq.getSubscriptionId());
         UnSubscribeQosWrapper uq = new UnSubscribeQosWrapper();
         frodoCon.unSubscribe(uk.toXml(), uq.toXml());

         System.err.println("->Check publish, frodo should not get it ...");
         pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), contentStr.getBytes(), pq.toXml());
         prq = frodoCon.publish(msgUnit);
         log.info(ME+":"+serverHelper.getFrodoGlob().getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node with IP=" + serverHelper.getFrodoGlob().getProperty().get("port",0) +
                                    ", the returned QoS is: " + prq.getOid());

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
