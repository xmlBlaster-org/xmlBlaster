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


import java.util.Vector;
import java.io.File;

import junit.framework.*;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.EraseTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class EraseTest extends TestCase {
   private String ME = "EraseTest";
   private Global glob;
   private static Logger log = Logger.getLogger(EraseTest.class.getName());
   private ServerHelper serverHelper;

   private I_XmlBlasterAccess heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private int updateCounterBilbo = 0;
   private String oid = "SubscribeToBilbo";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "We win";

   private String assertInUpdate = null;

   private boolean isErase = false;

   public EraseTest(String name) {
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
    * - Subscribe to RUGBY messages from bilbo<br />
    * - publish RUGBY messages to avalon (heron is the master)<br />
    * - bilbo should get an update
    * - erase RUGBY message at avalon
    * - bilbo should get an erase event
    */ 
   public void testErase() {
      System.err.println("***EraseTest.testErase: Subscribe a message from a cluster slave ...");
      try {
         System.err.println("->Connect to avalon ...");
         avalonCon = serverHelper.connect(serverHelper.getAvalonGlob(), null);

         {
            System.err.println("->Connect to bilbo ...");
            bilboCon = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     updateCounterBilbo++;
                     log.info(
                              "Receiving update '" + updateKey.getOid() + "' state=" + updateQos.getState() +
                              " #" + updateCounterBilbo + " ...");
                     if (isErase && !updateQos.isErased()) {
                        assertInUpdate = "Expected message erase event, expected:" + isErase + " but was:" + updateQos.isErased();
                        assertEquals("Expected message erase event", isErase, updateQos.isErased());
                     }
                     assertInUpdate = "Wrong message updated, expected:" + oid + " but was:" + updateKey.getOid();
                     assertEquals("Wrong message updated", oid, updateKey.getOid());
                     assertInUpdate = null;
                     return "";
                  }
               });

            System.err.println("->Subscribe from bilbo ...");
            SubscribeKey sk = new SubscribeKey(glob, oid);
            sk.setDomain(domain);
            SubscribeQos sq = new SubscribeQos(glob);
            SubscribeReturnQos srq = bilboCon.subscribe(sk.toXml(), sq.toXml());
         }

         System.err.println("->Publish to avalon ...");
         PublishKey avalon_pk = new PublishKey(glob, oid, "text/plain", "1.0", domain);
         PublishQos avalon_pq = new PublishQos(glob);
         MsgUnit avalon_msgUnit = new MsgUnit(avalon_pk, contentStr.getBytes(), avalon_pq);
         PublishReturnQos avalon_prq = avalonCon.publish(avalon_msgUnit);
         assertEquals("oid changed", oid, avalon_prq.getKeyOid());


         try { Thread.sleep(2000); } catch( InterruptedException i) {}
         if (1 != updateCounterBilbo) log.severe("Did not expect " + updateCounterBilbo + " updates");
         assertEquals("message from avalon", 1, updateCounterBilbo);
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;
         updateCounterBilbo = 0;

         isErase = true;
         System.err.println("->Trying to erase the message at the avalon slave node ...");
         EraseKey ek = new EraseKey(glob, oid);
         ek.setDomain(domain);
         EraseQos eq = new EraseQos(glob);
         EraseReturnQos[] arr = avalonCon.erase(ek.toXml(), eq.toXml());
         assertEquals("Erase", 1, arr.length);

         try { Thread.sleep(2000); } catch( InterruptedException i) {}
         assertEquals("message erase event for bilbo", 1, updateCounterBilbo);
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;

         System.err.println("->testErase done, SUCCESS.");
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

      System.err.println("***EraseTest.testSubscribeTwice: testSubscribeTwice [SUCCESS]");
   }
}
