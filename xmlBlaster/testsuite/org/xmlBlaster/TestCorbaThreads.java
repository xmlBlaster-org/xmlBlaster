/*------------------------------------------------------------------------------
Name:      TestCorbaThreads.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestCorbaThreads.java,v 1.19 2002/09/10 19:00:27 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.jutils.runtime.ThreadLister;

import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.client.protocol.corba.CorbaConnection;
import org.xmlBlaster.client.protocol.corba.CorbaCallbackServer;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

import junit.framework.*;


/**
 * This client tests the number of threads opened and cleaned up by JacORB corba library. 
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading testsuite.org.xmlBlaster.TestCorbaThreads
 *
 *    java junit.swingui.TestRunner -noloading testsuite.org.xmlBlaster.TestCorbaThreads
 * </pre>
 */
public class TestCorbaThreads extends TestCase implements I_CallbackExtended
{
   private final static String ME = "TestCorbaThreads";

   private Global glob;
   private final LogChannel log;
   private EmbeddedXmlBlaster serverThread;
   private final String loginName = "TestCorbaThreads";
   private String publishOid = "";
   private CorbaConnection corbaConnection = null;
   private I_CallbackServer cbServer = null;
   private String corbaContent;
   private int numReceived = 0;


   /**
    * Constructs the TestCorbaThreads object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestCorbaThreads(Global glob, String testName) {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog("test");
   }

   protected void setUp() {
      glob.init(Util.getOtherServerPorts(8116));
      String[] args = { "-ProtocolPlugin[IOR][1.0]", "org.xmlBlaster.protocol.corba.CorbaDriver",
                        "-CbProtocolPlugin[IOR][1.0]", "org.xmlBlaster.protocol.corba.CallbackCorbaDriver" };
      glob.init(args);
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing on port 8116");
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
         cbServer.initialize(this.glob, loginName, this);

         corbaConnection = new CorbaConnection(glob);
         //cbServer = new CorbaCallbackServer(this.glob, loginName, this, corbaConnection.getOrb());
         corbaConnection.connect(new ConnectQos(glob, loginName, passwd));
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }

   protected void tearDown() {
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * cleaning up .... logout
    */
   private void logout() {
      if (corbaConnection != null) {
         Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...
         corbaConnection.disconnect(null);
         corbaConnection = null;
         System.gc();
      }
      if (cbServer != null) {
         try { cbServer.shutdownCb(); } catch(Exception e) { log.error(ME, "shutdownCb(): " + e.toString()); }
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
      log.info(ME, "Testing thread consume on multiple login/logouts, used threads before any login=" + ThreadLister.countThreads());
      ThreadLister.listAllThreads(System.out);
      int threadsBefore = 0;

      for (int ii=0; ii<5; ii++) {
         log.info(ME, "Testing login/logout no = " + ii);
         login();
         logout();
         if (ii==0) {
            threadsBefore = ThreadLister.countThreads();
            log.info(ME, "Testing thread consume on multiple login/logouts, used threads after first login=" + threadsBefore);
            ThreadLister.listAllThreads(System.out);
         }
      }
      ThreadLister.listAllThreads(System.out);
      int threadsAfter = ThreadLister.countThreads();
      log.info(ME, "Currently used threads after 5 login/logout=" + threadsAfter);
      int allow = threadsBefore + 1; // This 1 thread is temporary
      assertTrue("We have a thread leak, threadsBefore=" + threadsBefore + " threadsAfter=" + threadsAfter, threadsAfter <= allow);
   }


   /**
    * These update() methods are enforced by I_CallbackExtended. 
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
      return "";
   }
   public String update(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
      return "";
   }
   public String[] update(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
      String[] retArr = new String[msgUnitArr.length];
      for (int ii=0; ii<retArr.length; ii++) retArr[ii] = "";
      return retArr;
   }
   public void updateOneway(String cbSessionId, String updateKeyLiteral, byte[] content, String updateQosLiteral) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
   }
   public void updateOneway(String cbSessionId, org.xmlBlaster.engine.helper.MessageUnit[] msgUnitArr) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");
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
    * Invoke: java testsuite.org.xmlBlaster.TestCorbaThreads
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestCorbaThreads</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global(); // initializes args etc.
      if (glob.init(args) < 0) {
         System.out.println("Wrong params");
         return;
      }

      TestCorbaThreads testSub = new TestCorbaThreads(glob, "TestCorbaThreads");
      testSub.testThread();
   }
}
