package clustertest;

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
 * java -Djava.compiler= junit.textui.TestRunner -noloading clustertest.EraseTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class EraseTest extends TestCase {
   private String ME = "EraseTest";
   private Global glob;
   private LogChannel log;
   private ServerHelper serverHelper;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon;

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
      this.glob = new Global();
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {
      log = glob.getLog(ME);
      log.info(ME, "Entering setUp(), test starts");

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
                     log.info(ME+":"+serverHelper.getBilboGlob().getId(),
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
            SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
            sk.setDomain(domain);
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos srq = bilboCon.subscribe(sk.toXml(), sq.toXml());
         }

         System.err.println("->Publish to avalon ...");
         PublishKeyWrapper avalon_pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         PublishQosWrapper avalon_pq = new PublishQosWrapper();
         MessageUnit avalon_msgUnit = new MessageUnit(avalon_pk.toXml(), contentStr.getBytes(), avalon_pq.toXml());
         PublishRetQos avalon_prq = avalonCon.publish(avalon_msgUnit);
         assertEquals("oid changed", oid, avalon_prq.getOid());


         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {}
         if (1 != updateCounterBilbo) log.error(ME, "Did not expect " + updateCounterBilbo + " updates");
         assertEquals("message from avalon", 1, updateCounterBilbo);
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;
         updateCounterBilbo = 0;

         isErase = true;
         System.err.println("->Trying to erase the message at the avalon slave node ...");
         EraseKeyWrapper ek = new EraseKeyWrapper(oid);
         ek.setDomain(domain);
         EraseQosWrapper eq = new EraseQosWrapper();
         EraseRetQos[] arr = avalonCon.erase(ek.toXml(), eq.toXml());
         assertEquals("Erase", 1, arr.length);

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {}
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
