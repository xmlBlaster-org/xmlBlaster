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
 * Test publishing a message to frodo which forwards it to heron, the message
 * is dirty read in fordo. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading clustertest.DirtyReadTest
 * </pre>
 * NOTE: asserts() in update() methods are routed back to server and are not handled
 *       by the junit testsuite, so we check double (see code).
 *
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html" target="others">Cluster requirement</a>
 */
public class DirtyReadTest extends TestCase {
   private String ME = "DirtyReadTest";
   private Global glob;
   private LogChannel log;
   private ServerHelper serverHelper;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterFrodo = 0;
   private String oid = "PublishToBilbo-DirtyRead";
   private String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
   private String contentStr = "Lets have another game.";

   private String assertInUpdate = null;

   public DirtyReadTest(String name) {
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

      serverHelper.startHeron();
      serverHelper.startFrodo();
   }

   /**
    * cleaning up ...
    */
   protected void tearDown() {
      log.info(ME, "Entering tearDown(), test is finished");
      try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time

      if (frodoCon != null) { frodoCon.disconnect(null); frodoCon = null; }
      if (heronCon != null) { heronCon.disconnect(null); heronCon = null; }

      serverHelper.tearDown();
   }

   /**
    * We start all nodes as described in requirement
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * publish a message to bilbo which should be routed to heron.
    * Than we try to access the message at heron
    */ 
   public void testDirtyRead() {
      System.err.println("***DirtyReadTest: Publish a message to a cluster slave ...");

      SubscribeKeyWrapper sk;
      SubscribeQosWrapper sq;
      SubscribeRetQos srq;

      PublishKeyWrapper pk;
      PublishQosWrapper pq;
      PublishRetQos prq;
      MessageUnit msgUnit;

      try {
         System.err.println("->Connect to frodo ...");
         frodoCon = serverHelper.connect(serverHelper.getFrodoGlob(), new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = serverHelper.getFrodoGlob().getId() + ": Receive unexpected message '" + updateKey.getOid() + "'";
                  return "";
               }
            });
         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);


         System.err.println("->Subscribe from frodo ...");
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


         System.err.println("->Check publish, frodo should not get it ...");
         pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), contentStr.getBytes(), pq.toXml());
         prq = frodoCon.publish(msgUnit);
         log.info(ME+":"+serverHelper.getFrodoGlob().getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + contentStr +
                                    "' to xmlBlaster node with IP=" + serverHelper.getFrodoGlob().getProperty().get("port",0) +
                                    ", the returned QoS is: " + prq.getOid());

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("frodo has not received message", 1, updateCounterFrodo);

         // TODO: !!! Query heron if he did not send any update message
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         fail("PublishToBilbo-DirtyRead-Exception: " + e.toString());
      }

      System.err.println("***DirtyReadTest: testDirtyRead [SUCCESS]");
   }
}
