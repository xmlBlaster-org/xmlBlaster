/*------------------------------------------------------------------------------
Name:      TestCorbaThreads.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.client.protocol.corba.CorbaCallbackServer;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ThreadLister;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.protocol.corba.OrbInstanceFactory;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.XmlBlasterException;

import org.omg.CORBA.ORB;
import org.omg.PortableServer.POA;

import org.xmlBlaster.test.Util;

import java.util.logging.Logger;
import java.util.logging.Level;

import junit.framework.*;


/**
 * This client tests the number of threads opened and cleaned up by JacORB corba library. 
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestCorbaThreads
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestCorbaThreads
 * </pre>
 */
public class TestCorbaThreads extends TestCase implements I_CallbackExtended
{
   private Global glob;
   private static Logger log = Logger.getLogger(TestCorbaThreads.class.getName());
   private EmbeddedXmlBlaster serverThread;
   private final String loginName = "TestCorbaThreads";
   private CorbaConnection corbaConnection = null;
   private I_CallbackServer cbServer = null;


   /**
    * Constructs the TestCorbaThreads object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestCorbaThreads(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Test ORB and POA creation and orb.shutdown()
    */
   public void testJacORB() {
      Util.gc(2);
      int threadsBefore = ThreadLister.countThreads();
      for (int ii=0; ii<20; ii++) {
         System.out.println("Hit a key for ORB #" + ii + "/20");
         try { System.in.read(); } catch(java.io.IOException e) {}
         ORB orb = OrbInstanceFactory.createOrbInstance(glob, new String[0], null, new CallbackAddress(glob));
         try {
            POA rootPOA = org.omg.PortableServer.POAHelper.narrow(orb.resolve_initial_references("RootPOA"));
            rootPOA.the_POAManager().activate();
         }
         catch (Throwable e) {
            e.printStackTrace();
            System.out.println("ERROR: " + e.toString());
         }
         // Without orb.shutdown we use 2 threads for each loop!
         orb.shutdown(true);
      }
      Util.gc(2);
      assertEquals("JacORB has a thread leak", threadsBefore, ThreadLister.countThreads());
   }

   protected void setUp() {
      glob.init(Util.getOtherServerPorts(8116));
      String[] args = { "-ProtocolPlugin[IOR][1.0]", "org.xmlBlaster.protocol.corba.CorbaDriver",
                        "-CbProtocolPlugin[IOR][1.0]", "org.xmlBlaster.protocol.corba.CallbackCorbaDriver" };
      glob.init(args);
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing on bootstrapPort 8116");
   }

   /**
    * Login. 
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    * - One connection for the receiver client
    * - One connection for the receiver2 client
    */
   private void login() {
      try {
         String passwd = "secret";

         cbServer = new CorbaCallbackServer();
         CallbackAddress cba = new CallbackAddress(this.glob);
         cbServer.initialize(this.glob, loginName, cba, this);

         corbaConnection = new CorbaConnection();
         corbaConnection.init(glob, null);
         //cbServer = new CorbaCallbackServer(this.glob, loginName, this, corbaConnection.getOrb());
         ConnectQos connectQos = new ConnectQos(glob, loginName, passwd);
         corbaConnection.connect(connectQos.toXml());
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }

   protected void tearDown() {
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      log.info("Ports reset to default: " + glob.getProperty().toXml());
   }

   /**
    * cleaning up .... logout
    */
   private void logout() {
      if (corbaConnection != null) {
         try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
         corbaConnection.disconnect(null);
         corbaConnection = null;
         System.gc();
      }
      if (cbServer != null) {
         try { cbServer.shutdown(); } catch(Exception e) { log.severe("shutdownCb(): " + e.toString()); }
         cbServer = null;
         System.gc();
      }
   }


   /**
    * TEST: Send a message to one destination
    * <p />
    * The returned subscribeOid is checked
    */
   public void testThread() {
      log.info("Testing thread consume on multiple login/logouts, used threads before any login=" + ThreadLister.countThreads());
      ThreadLister.listAllThreads(System.out);
      int threadsBefore = 0;

      for (int ii=0; ii<5; ii++) {
         log.info("Testing login/logout no = " + ii);
         login();
         logout();
         if (ii==0) {
            Util.gc(2);
            threadsBefore = ThreadLister.countThreads();
            log.info("Testing thread consume on multiple login/logouts, used threads after first login=" + threadsBefore);
            ThreadLister.listAllThreads(System.out);
         }
      }
      Util.gc(2);
      ThreadLister.listAllThreads(System.out);
      int threadsAfter = ThreadLister.countThreads();
      log.info("Currently used threads after 5 login/logout=" + threadsAfter);
      assertTrue("We have a thread leak, threadsBefore=" + threadsBefore +
                 " threadsAfter=" + threadsAfter, threadsAfter <= threadsBefore);
   }


   /**
    * These update() methods are enforced by I_CallbackExtended. 
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
      return "";
   }
   public String update(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
      return "";
   }
   public String[] update(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<retArr.length; ii++) retArr[ii] = "";
      return retArr;
   }
   public void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
   }
   public void updateOneway(String cbSessionId, org.xmlBlaster.util.MsgUnitRaw[] msgUnitArr) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
   }

   public void lostConnection(XmlBlasterException xmlBlasterException) {
      if (log.isLoggable(Level.FINER)) log.finer("Lost connection ...");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestCorbaThreads(new Global(), "testThread"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestCorbaThreads
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestCorbaThreads</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(); // initializes args etc.
      if (glob.init(args) < 0) {
         System.out.println("Wrong params");
         return;
      }

      TestCorbaThreads testSub = new TestCorbaThreads(glob, "TestCorbaThreads");
      testSub.setUp();
      testSub.testJacORB();
      testSub.testThread();
      testSub.tearDown();
   }
}
