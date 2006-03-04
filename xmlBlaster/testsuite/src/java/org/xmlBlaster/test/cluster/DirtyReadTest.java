package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

// for client connections:
import org.xmlBlaster.util.*;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;


import java.util.Vector;
import java.io.File;

import junit.framework.*;

/**
 * Test publishing a message to frodo which forwards it to heron, the message
 * is dirty read in fordo. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.cluster.DirtyReadTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html" target="others">Cluster requirement</a>
 */
public class DirtyReadTest extends TestCase {
   private String ME = "DirtyReadTest";
   private Global glob;
   private static Logger log = Logger.getLogger(DirtyReadTest.class.getName());
   private ServerHelper serverHelper;

   private I_XmlBlasterAccess heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterFrodo = 0;
   private String contentStr = "Lets have another game.";

   private String assertInUpdate = null;

   public DirtyReadTest(String name) {
      super(name);
      this.glob = new Global(null, true, false);
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {

      log.info("Entering setUp(), test starts");

      assertInUpdate = null;
      updateCounterFrodo = 0;

      serverHelper = new ServerHelper(glob, log, ME);

      serverHelper.startHeron();
      serverHelper.startFrodo();
   }

   /**
    * cleaning up ...
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time

      if (frodoCon != null) { frodoCon.disconnect(null); frodoCon = null; }
      if (heronCon != null) { heronCon.disconnect(null); heronCon = null; }

      serverHelper.tearDown();
   }

   public void testDirtyRead() {
      runIt("RUGBY_NEWS"); // heron is master for RUGBY_NEWS and has dirtyRead allowed
   }

   public void testNoDirtyRead() {
      runIt("SOCCER_NEWS"); // heron is master for SOCCER_NEWS WITHOUT dirty read!
   }

   /**
    * We start all nodes as described in requirement
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * publish a message to bilbo which should be routed to heron.
    * Than we try to access the message at heron and check if heron has not
    * updated one to frodo because of dirtyRead configured in heron.properties
    */ 
   public void runIt(String domain) {
      boolean isDirtyReadTest = domain.equals("RUGBY_NEWS");
      ME = "DirtyReadTest domain=" + domain + ": ";
      System.err.println("***DirtyReadTest: Publish a message to a cluster slave isDirtyReadTest=" + isDirtyReadTest + " ...");

      final String oid = isDirtyReadTest ? "PublishToBilbo-DirtyRead" : "PublishToBilbo-NODirtyRead";

      SubscribeKey sk;
      SubscribeQos sq;
      SubscribeReturnQos srq;

      PublishKey pk;
      PublishQos pq;
      PublishReturnQos prq;
      MsgUnit msgUnit;

      try {
         System.err.println(ME+"->Connect to frodo ...");
         frodoCon = serverHelper.connect(serverHelper.getFrodoGlob(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = serverHelper.getFrodoGlob().getId() + ": Receive unexpected message '" + updateKey.getOid() + "'";
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);


         System.err.println(ME+"->Subscribe '" + oid + "' from frodo ...");
         sk = new SubscribeKey(glob, oid);
         sk.setDomain(domain);
         sq = new SubscribeQos(glob);
         srq = frodoCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("Reveiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler");
               updateCounterFrodo++;
               assertInUpdate = null;
               return "";
            }
         });  // subscribe with our specific update handler
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         assertInUpdate = null;


         System.err.println(ME+"->Check publish '" + oid + "', frodo should get it ...");
         pk = new PublishKey(glob, oid, "text/plain", "1.0", domain);
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk, contentStr.getBytes(), pq);
         prq = frodoCon.publish(msgUnit);
         log.info("Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node with IP=" + serverHelper.getFrodoGlob().getProperty().get("bootstrapPort",0) +
                                    ", the returned QoS is: " + prq.getKeyOid());

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("frodo has not received message", 1, updateCounterFrodo);

         System.err.println("Query heron if he did not send any update message ...");
         System.err.println("->Connect to heron ...");
         heronCon = serverHelper.connect(serverHelper.getHeronGlob(), null);


         String cmd = "__cmd:client/frodo/?sessionList";
         System.err.println(ME+"->Find out the public session Id of slave frodo at heron with '" + cmd + "' ...");
         MsgUnit[] msgs = heronCon.get("<key oid='" + cmd + "'/>", null);
         assertEquals("Command failed", 1, msgs.length);
         String pubSessionId = msgs[0].getContentStr();


         // command = "__cmd:client/frodo/2/?numUpdate" : (the cluster slave loggs in usually with its glob.getId()
         cmd = "__cmd:client/frodo/" + pubSessionId + "/?numUpdate";
         System.err.println("->Query numUpdate with '" + cmd + "' ...");
         msgs = heronCon.get("<key oid='" + cmd + "'/>", null);

         assertEquals("Command failed", 1, msgs.length);
         if (isDirtyReadTest) {
            assertEquals("frodo has received updates from heron but should not because of dirty read",
                      "0", msgs[0].getContentStr());
            log.info("Success, the update was a dirty read as heron did not send it!");
         }
         else {
            assertEquals("frodo has not received updates from its master heron",
                      "1", msgs[0].getContentStr());
            log.info("Success, the update was NO dirty read as heron did send it!");
         }

         System.err.println(ME+"Check if heron has got the message ...");
         msgs = heronCon.get("<key oid='" + oid + "'/>", null);
         assertEquals("The master never got the message", 1, msgs.length);
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("PublishToBilbo-DirtyRead-Exception: " + e.toString());
      }

      System.err.println("***DirtyReadTest: testDirtyRead [SUCCESS]");
   }
}
