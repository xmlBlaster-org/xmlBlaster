package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
// for client connections:
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.Destination;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.PtPTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">Cluster requirement</a>
 */
public class PtPTest extends TestCase {
   private String ME = "PtPTest";
   private Global glob;
   private static Logger log = Logger.getLogger(PtPTest.class.getName());
   private ServerHelper serverHelper;

   private I_XmlBlasterAccess heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private String oid = "PublishToBilbo";
   private String contentStr = "We win";

   private String assertInUpdateHeron = null;
   private String assertInUpdateBilbo = null;

   public PtPTest(String name) {
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
      try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time

      if (bilboCon != null) { bilboCon.disconnect(null); bilboCon = null; }
      if (frodoCon != null) { frodoCon.disconnect(null); frodoCon = null; }
      if (golanCon != null) { golanCon.disconnect(null); golanCon = null; }
      if (avalonCon != null) { avalonCon.disconnect(null); avalonCon = null; }
      if (heronCon != null) { heronCon.disconnect(null); heronCon = null; }

      serverHelper.tearDown();
   }

   /**
    * We start bilbo, frodo and heron nodes as described in requirement
    * PtP messages are routed from ClientTo[bilbo] -> bilbo -> frodo -> heron -> ClientTo[heron]
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * publish a message to bilbo which should be routed to client XX which is logged in to heron.
    */ 
   public void testPublishPtP() {
      System.err.println("***PtPTest: Publish a message to a cluster slave ...");
      try {
         log.info("Login to heron and wait for PtP message ...");
         heronCon = serverHelper.connect(serverHelper.getHeronGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("Received message '" + updateKey.getOid() + "' state=" +
                           updateQos.getState() + " from '" + updateQos.getSender() + "'");
                  if (!updateQos.getSender().equalsAbsolute(bilboCon.getConnectReturnQos().getSessionName())) {
                     assertInUpdateHeron = serverHelper.getHeronGlob().getId() + ": Did not expect message update in default handler";
                  }
                  updateCounterHeron++;
                  return "";
               }
            });
         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdateHeron, assertInUpdateHeron == null);
         assertInUpdateHeron = null;

         log.info("Login to bilbo to send PtP message ...");
         bilboCon = serverHelper.connect(serverHelper.getBilboGlob(), new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdateBilbo = serverHelper.getBilboGlob().getId() + ": Should not receive the message '" + updateKey.getOid() + "'";
                  fail(assertInUpdateBilbo); // This is routed to server, not to junit
                  return "";
               }
            });
         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdateBilbo, assertInUpdateBilbo == null);
         assertInUpdateBilbo = null;

         int num = 5;
         for (int i=0; i<num; i++) {
            PublishKey pk = new PublishKey(glob, oid, "text/plain", "1.0");
            pk.setDomain("RUGBY_NEWS"); // heron is master: need for routing as heron is not directly connected to us
            PublishQos pq = new PublishQos(glob);
            SessionName sessionName = heronCon.getConnectReturnQos().getSessionName(); // destination client
            Destination destination = new Destination(sessionName);
            destination.forceQueuing(true);
            pq.addDestination(destination);
            log.info("Sending PtP message '" + oid + "' from bilbo to '" + sessionName + "' :" + pq.toXml());
            MsgUnit msgUnit = new MsgUnit(pk, (contentStr+"-"+i).getBytes(), pq);
            PublishReturnQos prq = bilboCon.publish(msgUnit);
            log.info("Published message to destination='" + sessionName +
                                       "' content='" + (contentStr+"-"+i) +
                                       "' to xmlBlaster node with IP=" + serverHelper.getBilboGlob().getProperty().get("bootstrapPort",0) +
                                       ", the returned QoS is: " + prq.getKeyOid());
         }

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdateHeron, assertInUpdateHeron == null);
         assertEquals("Heron client did not receive PtP message", num, updateCounterHeron);
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("PublishToBilbo-Exception: " + e.getMessage());
      }

      System.err.println("***PtPTest: testPublishPtP [SUCCESS]");
   }

   /**
    * Invoke: 
    * <pre>
    *  java -Dtrace[cluster]=true -Dcall[cluster]=true -Dcall[core]=true org.xmlBlaster.test.cluster.PtPTest
    *  java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.PtPTest
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(null, true, false);
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      PtPTest testSub = new PtPTest("PtPTest");
      testSub.setUp();
      testSub.testPublishPtP();
      testSub.tearDown();
   }
}
