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

   private ServerThread heronThread = null;
   private ServerThread avalonThread = null;
   private ServerThread golanThread = null;
   private ServerThread frodoThread = null;
   private ServerThread bilboThread = null;

   private Global heronGlob = null;
   private Global avalonGlob = null;
   private Global golanGlob = null;
   private Global frodoGlob = null;
   private Global bilboGlob = null;

   private XmlBlasterConnection heronCon, avalonCon, golanCon, frodoCon, bilboCon;

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
      assertEquals("Invalid cluster node id, check biblo.properties", "bilbo", bilboGlob.getId());
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

   /** Connect in fail save mode to a server node (as given in glob.getId()) */
   private XmlBlasterConnection connect(final Global glob, I_Callback cb) throws XmlBlasterException {
      String clientName = "ClientTo[" + glob.getId() + "]";
      if (glob.getId() == null || glob.getId().length() < 1) log.error(ME, "glob.getId() is not set");
      XmlBlasterConnection con = new XmlBlasterConnection(glob);

      con.initFailSave(new I_ConnectionProblems() {
            public void reConnected() {
               log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to " + glob.getId());
            }
            public void lostConnection() {
               log.warn(ME, "I_ConnectionProblems: Lost connection to " + glob.getId());
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

      // The init is used for server nodes but used for client connections as well
      initHeron();
      initAvalon();
      initGolan();
      initFrodo();
      initBilbo();

      // Starts a cluster node
      startHeron();
      startAvalon();
      startGolan();
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

      if (heronThread != null) { heronThread.stopServer(true); heronThread=null; }
      if (avalonThread != null) { avalonThread.stopServer(true); avalonThread=null; }
      if (golanThread != null) { golanThread.stopServer(true); golanThread=null; }
      if (frodoThread != null) { frodoThread.stopServer(true); frodoThread=null; }
      if (bilboThread != null) { bilboThread.stopServer(true); bilboThread=null; }
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
         String oid = "PublishToBilbo";
         PublishKeyWrapper pk = new PublishKeyWrapper(oid, "text/plain", "1.0", domain);
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), content.getBytes(), pq.toXml());
         String retQos = bilboCon.publish(msgUnit);
         log.info(bilboGlob.getId(), "Published message of domain='" + pk.getDomain() + "' and content='" + content +
                                    "' to xmlBlaster node with IP=" + bilboGlob.getProperty().get("port",0) +
                                    ", the returned QoS is: " + retQos);


         heronCon = connect(heronGlob, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(heronGlob.getId(), "Receive message '" + updateKey.getOid() + "'");
                  return "";
               }
            });

         GetKeyWrapper gk = new GetKeyWrapper(oid);
         MessageUnit[] msgs = heronCon.get(gk.toXml(), null);
         assertTrue("Invalid msgs returned", msgs != null);
         assertEquals("Invalid number of messages returned", 1, msgs.length);
         log.info(heronGlob.getId(), "SUCCESS: Got message:" + msgs[0].getXmlKey());
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
}
