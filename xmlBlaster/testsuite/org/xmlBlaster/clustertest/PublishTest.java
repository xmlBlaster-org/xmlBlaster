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

import junit.framework.*;

/**
 * Test publishing a message from bilbo to heron. 
 * <p />
 * <pre>
 * java -Djava.compiler= junit.textui.TestRunner -noloading clustertest.PublishTest
 * </pre>
 * @see http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html
 */
public class PublishTest extends TestCase {
   private String ME = "PublishTest";
   private Global glob;
   private LogChannel log;
   public static int heronPort = 7600;
   public static int avalonPort = 7601;
   public static int golanPort = 7602;
   public static int frodoPort = 7603;
   public static int bilboPort = 7604;

   private ServerThread heronThread, avalonThread, golanThread, frodoThread, bilboThread;

   private Global heronGlob, avalonGlob, golanGlob, frodoGlob, bilboGlob;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon;

   private int updateCounterHeron = 0;
   private int updateCounterFrodo = 0;
   private String oid = "PublishToBilbo";

   public PublishTest(String name) {
      super(name);
      this.glob = new Global();
   }

   private void initHeron() {
      String[] args = { "-propertyFile", "heron.properties", "-info[heron]", "true", "-call[heron]", "true" };
      heronGlob = glob.getClone(args);
   }

   private void initAvalon() {
      String[] args = { "-propertyFile", "avalon.properties" };
      avalonGlob = glob.getClone(args);
   }

   private void initGolan() {
      String[] args = { "-propertyFile", "golan.properties" };
      golanGlob = glob.getClone(args);
   }

   private void initFrodo() {
      String[] args = { "-propertyFile", "frodo.properties" };
      frodoGlob = glob.getClone(args);
   }

   private void initBilbo() {
      String[] args = { "-propertyFile", "bilbo.properties", "-call[bilbo]", "false" };
      bilboGlob = glob.getClone(args);
      assertEquals("Invalid cluster node id, check biblo.properties or" +
                   " change to the directory where the property files are!",
                   "bilbo", bilboGlob.getId());
   }

   private void startHeron() {
      heronThread = ServerThread.startXmlBlaster(heronGlob);
      log.info(ME, "'heron' is ready for testing on port " + heronPort);
   }

   private void startAvalon() {
      avalonThread = ServerThread.startXmlBlaster(avalonGlob);
      log.info(ME, "'avalon' is ready for testing on port " + avalonPort);
   }

   private void startGolan() {
      golanThread = ServerThread.startXmlBlaster(golanGlob);
      log.info(ME, "'golan' is ready for testing on port " + golanPort);
   }

   private void startFrodo() {
      frodoThread = ServerThread.startXmlBlaster(frodoGlob);
      log.info(ME, "'frodo' is ready for testing on port " + frodoPort);
   }

   private void startBilbo() {
      bilboThread = ServerThread.startXmlBlaster(bilboGlob);
      log.info(ME, "'bilbo' is ready for testing on port " + bilboPort);
   }

   private void stopHeron() {
      if (heronThread != null) { heronThread.stopServer(true); heronThread=null; }
   }

   private void stopAvalon() {
      if (avalonThread != null) { avalonThread.stopServer(true); avalonThread=null; }
   }

   private void stopGolan() {
      if (golanThread != null) { golanThread.stopServer(true); golanThread=null; }
   }

   private void stopFrodo() {
      if (frodoThread != null) { frodoThread.stopServer(true); frodoThread=null; }
   }

   private void stopBilbo() {
      if (bilboThread != null) { bilboThread.stopServer(true); bilboThread=null; }
   }

   /** Connect in fail save mode to a server node (as given in glob.getId()) */
   private XmlBlasterConnection connect(final Global glob, I_Callback cb) throws XmlBlasterException {
      final String clientName = "ClientTo[" + glob.getId() + "]";
      if (glob.getId() == null || glob.getId().length() < 1) log.error(ME, "glob.getId() is not set");
      XmlBlasterConnection con = new XmlBlasterConnection(glob);

      con.initFailSave(new I_ConnectionProblems() {
            public void reConnected() {
               log.info(clientName, "I_ConnectionProblems: We were lucky, reconnected to " + glob.getId());
            }
            public void lostConnection() {
               log.warn(clientName, "I_ConnectionProblems: Lost connection to " + glob.getId());
            }
         });

      ConnectQos qos = new ConnectQos(glob, clientName, "secret");
      ConnectReturnQos conRetQos = con.connect(qos, cb);

      log.info(clientName, "Connected to xmlBlaster.");
      return con;
   }

   /**
    * Initialize the test ...
    */
   protected void setUp() {
      log = glob.getLog(ME);
      log.info(ME, "Entering setUp(), test starts");

      // The init is used for server nodes but used for client connections as well
      initHeron();
      initAvalon();
      initGolan();
      initFrodo();
      initBilbo();

      // Starts a cluster node
      startHeron();
      //startAvalon();
      //startGolan();
      startFrodo();
      startBilbo();
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

      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {} // Wait some time

      stopHeron();
      stopAvalon();
      stopGolan();
      stopFrodo();
      stopBilbo();
   }

   /**
    * We start all nodes as described in requirement
    * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/cluster.html" target="others">cluster</a>
    * publish a message to bilbo which should be routed to heron.
    * Than we try to access the message at heron
    */ 
   public void testPublish() {
      System.out.println("***PublishTest: Publish a message to a cluster slave ...");
      try {
         bilboCon = connect(bilboGlob, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  fail("bilbo should not receive the message '" + updateKey.getOid() + "'");
                  return "";
               }
            });

         String domain = "RUGBY_NEWS"; // heron is master for RUGBY_NEWS
         String content = "We win";

         PublishKeyWrapper pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), content.getBytes(), pq.toXml());
         String retQos = bilboCon.publish(msgUnit);
         log.info(ME+":"+bilboGlob.getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + content +
                                    "' to xmlBlaster node with IP=" + bilboGlob.getProperty().get("port",0) +
                                    ", the returned QoS is: " + retQos);


         heronCon = connect(heronGlob, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(ME+":"+heronGlob.getId(), "Receive message '" + updateKey.getOid() + "'");
                  return "";
               }
            });

         // Check if the message has reached the master node heron ...
         GetKeyWrapper gk = new GetKeyWrapper(oid);
         MessageUnit[] msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         log.info(ME+":"+heronGlob.getId(), "SUCCESS: Got message:" + msgs[0].getXmlKey());

         // Check if the message is available at the slave node bilbo ...
         gk = new GetKeyWrapper(oid);
         gk.setDomain(domain);
         msgs = bilboCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         log.info(ME+":"+bilboGlob.getId(), "SUCCESS: Got message:" + msgs[0].getXmlKey());

         // Trying to erase the message at the slave node ...
         EraseKeyWrapper ek = new EraseKeyWrapper(oid);
         ek.setDomain(domain);
         EraseQosWrapper eq = new EraseQosWrapper();
         bilboCon.erase(ek.toXml(), eq.toXml());

         // Check if erased ...
         gk = new GetKeyWrapper(oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info(ME+":"+heronGlob.getId(), "SUCCESS: Got no message after erase");

         System.out.println("***PublishTest: Publish a message to a cluster slave - frodo is offline ...");

         // Subscribe from heron, the message is currently erased ...
         SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
         sk.setDomain(domain);
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         String subId = heronCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               assertEquals("Reveiving unexpected asynchronous update message", oid, updateKey.getOid());
               log.info(ME+":"+heronGlob.getId(), "Reveiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler");
               updateCounterHeron++;
               return "";
            }
         });  // subscribe with our specific update handler

         stopFrodo();

         // Check: heron is not available ...
         gk = new GetKeyWrapper(oid);
         msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 0, msgs.length);
         log.info(ME+":"+heronGlob.getId(), "SUCCESS: Got no message after erase");

         // publish again ...
         pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), content.getBytes(), pq.toXml());
         retQos = bilboCon.publish(msgUnit);
         log.info(ME+":"+bilboGlob.getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + content +
                                    "' to xmlBlaster node with IP=" + bilboGlob.getProperty().get("port",0) +
                                    ", the returned QoS is: " + retQos);


         assertEquals("heron is not reachable, publish should not have come through", 0, updateCounterHeron);
         updateCounterHeron = 0;

         startFrodo();

         // Connect to frodo ...
         frodoCon = connect(frodoGlob, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  fail(frodoGlob.getId() + ": Receive unexpected message '" + updateKey.getOid() + "'");
                  return "";
               }
            });

         // Subscribe from frodo, is he able to organize it?
         sk = new SubscribeKeyWrapper(oid);
         sk.setDomain(domain);
         sq = new SubscribeQosWrapper();
         subId = frodoCon.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info(ME+":"+frodoGlob.getId(), "Reveiving asynchronous message '" + updateKey.getOid() + "' in " + oid + " handler");
               updateCounterFrodo++;
               return "";
            }
         });  // subscribe with our specific update handler

         try { Thread.currentThread().sleep(5000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("frodo is reachable again, subscribe should work", 1, updateCounterFrodo);
      }
      catch (XmlBlasterException e) {
         fail("PublishToBilbo-Exception: " + e.toString());
      }
      finally {
         if (bilboCon != null) {
            bilboCon.disconnect(null);
         }
      }

      System.out.println("***PublishTest: testPublish [SUCCESS]");
   }

   /**
    * setUp() and tearDown() are ivoked between each test...() method
    */
    /*
   public void testDummy() {
      System.out.println("***PublishTest: testDummy [SUCCESS]");
   }
     */
}
