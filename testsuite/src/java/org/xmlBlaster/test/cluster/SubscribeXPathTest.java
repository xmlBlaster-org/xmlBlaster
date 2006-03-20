package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

// for client connections:
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;


import java.util.Vector;
import java.io.File;

import junit.framework.*;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.SubscribeXPathTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class SubscribeXPathTest extends TestCase {
   private String ME = "SubscribeXPathTest";
   private Global glob;
   private static Logger log = Logger.getLogger(SubscribeXPathTest.class.getName());
   private ServerHelper serverHelper;

   private I_XmlBlasterAccess heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private int updateCounterBilbo = 0;
   private String oid = "SubscribeToBilbo";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "We win";

   private String assertInUpdate = null;

   public SubscribeXPathTest(String name) {
      super(name);
      this.glob = new Global(null, true, false);
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {

      log.info("Entering setUp(), test starts");

      updateCounterHeron = 0;
      updateCounterFrodo = 0;
      updateCounterBilbo = 0;

      serverHelper = new ServerHelper(glob, log, ME);

      // Starts a cluster node
      serverHelper.startHeron();
      serverHelper.startAvalon();
      //serverHelper.startGolan();
      serverHelper.startFrodo();
      serverHelper.startBilbo();
   }

   /**
    * cleaning up ...
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time

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
    * <p />
    * - Subscribe to RUGBY messages from client at bilbo with XPath<br />
    * - publish RUGBY messages to avalon (heron is the master)<br />
    * - Does the client at bilbo receive them?
    * <pre>
    *   avalonClient -> avalon(slave) -> heron(master) -> bilbo(slave) -> bilboClient
    * </pre>
    */ 
   public void testSubscribeXpath() {
      System.err.println("***SubscribeXPathTest.testSubscribeXpath: Subscribe a message from a cluster slave ...");
      try {
         System.err.println("->Connect to avalon ...");
         avalonCon = serverHelper.connect(serverHelper.getAvalonGlob(), null);

         System.err.println("->Connect to bilbo ...");
         bilboCon = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  updateCounterBilbo++;
                  log.info(
                           "Receiving update '" + updateKey.getOid() + "' " + updateCounterBilbo + " ...");
                  assertEquals("Wrong message updated", oid, updateKey.getOid());
                  return "";
               }
            });

         System.err.println("->Subscribe from bilbo ...");
         SubscribeKey sk = new SubscribeKey(glob, "/xmlBlaster/key[@oid='SubscribeToBilbo']", Constants.XPATH);
         sk.setDomain(domain);  // set domain to allow cluster forwarding of subscription
         // without setting the domain the subscribe would just be handled by the slave connected to
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos srq = bilboCon.subscribe(sk.toXml(), sq.toXml());

         System.err.println("->Publish to avalon ...");
         PublishKey avalon_pk = new PublishKey(glob, oid, "text/plain", "1.0", domain);
         PublishQos avalon_pq = new PublishQos(glob);
         MsgUnit avalon_msgUnit = new MsgUnit(avalon_pk, contentStr, avalon_pq);
         PublishReturnQos avalon_prq = avalonCon.publish(avalon_msgUnit);
         assertEquals("oid changed", oid, avalon_prq.getKeyOid());


         try { Thread.sleep(2000); } catch( InterruptedException i) {}
         if (1 != updateCounterBilbo) log.severe("Did not expect " + updateCounterBilbo + " updates");
         assertEquals("message from avalon", 1, updateCounterBilbo);
         updateCounterBilbo = 0;

         System.err.println("->testSubscribeXpath done, SUCCESS.");

         System.err.println("->Trying to unSubscribe ...");
         bilboCon.unSubscribe("<key oid='" + srq.getSubscriptionId() + "'/>", null);

         System.err.println("->Trying to erase the message at the slave node ...");
         EraseKey ek = new EraseKey(glob, oid);
         ek.setDomain(domain);
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] arr = avalonCon.erase(ek.toXml(), eq.toXml());
         assertEquals("Erase", 1, arr.length);
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("SubscribeToBilbo-Exception: " + e.toString());
      }
      finally {
         if (bilboCon != null) {
            bilboCon.disconnect(null);
            bilboCon = null;
         }   
         if (avalonCon != null) {
            avalonCon.disconnect(null);
            avalonCon = null;
         }
      }

      System.err.println("***SubscribeXPathTest.testSubscribeXpath: testSubscribeXpath [SUCCESS]");
   }

   /*
   private void waitOnUpdate(final long timeout, final int numWait) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (updateCounterBilbo < numWait) {
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
   */

   /**
    * setUp() and tearDown() are ivoked between each test...() method
    */
    /*
   public void testDummy() {
      System.err.println("***SubscribeXPathTest: testDummy [SUCCESS]");
   }
     */
}
