package clustertest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ServerThread;

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
 * java -Djava.compiler= junit.textui.TestRunner -noloading clustertest.SubscribeTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class SubscribeTest extends TestCase {
   private String ME = "SubscribeTest";
   private Global glob;
   private LogChannel log;
   private ServerHelper serverHelper;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon, bilboCon2;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private int updateCounterBilbo = 0;
   private int updateCounterBilbo2 = 0;
   private String oid = "SubscribeToBilbo";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "We win";

   private String assertInUpdate = null;

   public SubscribeTest(String name) {
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
      updateCounterBilbo2 = 0;


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
      if (bilboCon2 != null) { bilboCon2.disconnect(null); bilboCon2 = null; }
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
    * - Subscribe to RUGBY messages from bilbo twice<br />
    * - publish RUGBY messages to avalon (heron is the master)<br />
    * - Kill bilbo, restart bilbo and check if we still get them
    */ 
   public void testSubscribeTwice() {
      System.err.println("***SubscribeTest.testSubscribeTwice: Subscribe a message from a cluster slave ...");
      try {
         System.err.println("->Connect to avalon ...");
         avalonCon = serverHelper.connect(serverHelper.getAvalonGlob(), null);

         {
            System.err.println("->Connect to bilbo ...");
            bilboCon = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     if (updateQos.isErased()) {
                        log.info(ME, "Ignoring erase message");
                        return "";
                     }
                     updateCounterBilbo++;
                     log.info(ME+":"+serverHelper.getBilboGlob().getId(),
                              "Receiving update '" + updateKey.getOid() + "' " + updateCounterBilbo + " ...");
                     assertEquals("Wrong message updated", oid, updateKey.getOid());
                     return "";
                  }
               });

            System.err.println("->Subscribe from bilbo ...");
            SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
            sk.setDomain(domain);
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos srq = bilboCon.subscribe(sk.toXml(), sq.toXml());
         }

         {
            System.err.println("->Connect to bilbo 2 ...");
            bilboCon2 = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     if (updateQos.isErased()) {
                        log.info(ME, "Ignoring erase message");
                        return "";
                     }
                     updateCounterBilbo2++;
                     log.info(ME+":"+serverHelper.getBilboGlob().getId() + "#2",
                              "Receiving update '" + updateKey.getOid() + "' " + updateCounterBilbo2 + " ...");
                     assertEquals("#2 Wrong message updated", oid, updateKey.getOid());
                     return "";
                  }
               });

            System.err.println("->Subscribe from bilbo 2 ...");
            SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
            sk.setDomain(domain);
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos srq = bilboCon2.subscribe(sk.toXml(), sq.toXml());
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
         if (1 != updateCounterBilbo2) log.error(ME, "Did not expect " + updateCounterBilbo2 + " updates");
         assertEquals("message from avalon #2", 1, updateCounterBilbo2);
         updateCounterBilbo = 0;
         updateCounterBilbo2 = 0;

         System.err.println("->testSubscribeTwice done, SUCCESS.");

         System.err.println("->Trying to erase the message at the slave node ...");
         EraseKeyWrapper ek = new EraseKeyWrapper(oid);
         ek.setDomain(domain);
         EraseQosWrapper eq = new EraseQosWrapper();
         EraseRetQos[] arr = avalonCon.erase(ek.toXml(), eq.toXml());
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
         if (bilboCon2 != null) {
            bilboCon2.disconnect(null);
            bilboCon2 = null;
         }   
         if (avalonCon != null) {
            avalonCon.disconnect(null);
            avalonCon = null;
         }
      }

      System.err.println("***SubscribeTest.testSubscribeTwice: testSubscribeTwice [SUCCESS]");
   }

   /**
    * We start all nodes as described in requirement
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * <p />
    * 1. publish RUGBY messages to avalon (heron is the master)<br />
    * 2. Subscribe those messages from bilbo<br />
    * 3. Kill bilbo, restart bilbo and check if we still get them
    */ 
   public void testSubscribe() {
      System.err.println("***SubscribeTest: Subscribe a message from a cluster slave ...");

      int num = 2;
      XmlBlasterConnection[] bilboCons = new XmlBlasterConnection[num];

      try {
         System.err.println("->Connect to avalon ...");
         avalonCon = serverHelper.connect(serverHelper.getAvalonGlob(), null);
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time

         for (int ii=0; ii<num; ii++) {
            final int counter = ii;
            System.err.println("->Connect to bilbo #" + ii + " ...");
            bilboCons[ii] = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
                  int bilboConInstanceCounter = counter; 
                  public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                     log.info(ME+":"+serverHelper.getBilboGlob().getId() + "#" + bilboConInstanceCounter,
                              "Receiving update '" + updateKey.getOid() + "' state=" + updateQos.getState() + ", " + updateCounterBilbo + " ...");
                     if (updateQos.isErased()) {
                        log.info(ME, "Ignoring erase message");
                        return "";
                     }
                     updateCounterBilbo++;
                     log.info(ME+":"+serverHelper.getBilboGlob().getId() + "#" + bilboConInstanceCounter,
                              "Receiving update '" + updateKey.getOid() + "' " + updateCounterBilbo + " ...");
                     assertEquals("Wrong message updated", oid, updateKey.getOid());
                     return "";
                  }
               });

            System.err.println("->Publish to avalon #" + ii + " ...");
            PublishKeyWrapper avalon_pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
            PublishQosWrapper avalon_pq = new PublishQosWrapper();
            MessageUnit avalon_msgUnit = new MessageUnit(avalon_pk.toXml(), contentStr.getBytes(), avalon_pq.toXml());
            PublishRetQos avalon_prq = avalonCon.publish(avalon_msgUnit);
            assertEquals("oid changed", oid, avalon_prq.getOid());

            try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
            
            System.err.println("->Subscribe from bilbo #" + ii + ", the message from avalon should arrive ...");
            SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
            sk.setDomain(domain);
            SubscribeQosWrapper sq = new SubscribeQosWrapper();
            SubscribeRetQos srq = bilboCons[ii].subscribe(sk.toXml(), sq.toXml());

            waitOnUpdate(2000L, 1);
            try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // wait longer to check if too many arrive
            if (1 != updateCounterBilbo) log.error(ME, "Did not expect " + updateCounterBilbo + " updates");
            assertEquals("message from avalon", 1, updateCounterBilbo);
            updateCounterBilbo = 0;

            System.err.println("->Trying to erase the message at the slave node ...");
            EraseKeyWrapper ek = new EraseKeyWrapper(oid);
            ek.setDomain(domain);
            EraseQosWrapper eq = new EraseQosWrapper();
            EraseRetQos[] arr = avalonCon.erase(ek.toXml(), eq.toXml());
            assertEquals("Erase", 1, arr.length);

            // We stay logged in but kill over callback server ...
            bilboCons[ii].shutdownCb();
         }

         System.err.println("->testSubscribe done, SUCCESS.");
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("SubscribeToBilbo-Exception: " + e.toString());
      }
      finally {
         for (int jj=0; jj<bilboCons.length; jj++) {
            if (bilboCons[jj] != null) {
               bilboCons[jj].disconnect(null);
               bilboCons[jj] = null;
            }
         }
         if (avalonCon != null) {
            avalonCon.disconnect(null);
            avalonCon = null;
         }
      }

      System.err.println("***SubscribeTest: testSubscribe [SUCCESS]");

   }

   private void waitOnUpdate(final long timeout, final int numWait) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (updateCounterBilbo < numWait) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warn(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
   }
   /**
    * setUp() and tearDown() are ivoked between each test...() method
    */
    /*
   public void testDummy() {
      System.err.println("***SubscribeTest: testDummy [SUCCESS]");
   }
     */
}
