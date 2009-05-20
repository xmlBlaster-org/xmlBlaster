/*------------------------------------------------------------------------------
Name:      TestReconnectSameClientOnly.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.socket.SocketCallbackImpl;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcCallbackImpl;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;


/**
 * This client tests the
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.configuration.html">client.configuration requirement</a>
 * and especially the <i>-session.reconnectSameClientOnly true</i> setting. 
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestReconnectSameClientOnly
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestReconnectSameClientOnly
 * </pre>
 * @see org.xmlBlaster.util.qos.SessionQos
 */
public class TestReconnectSameClientOnly extends TestCase implements I_Callback
{
   private static String ME = "TestReconnectSameClientOnly";
   private Global glob;
   private static Logger log = Logger.getLogger(TestReconnectSameClientOnly.class.getName());

   private I_XmlBlasterAccess con = null;
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9560;
   private boolean startEmbedded = true;

   /**
    * Constructs the TestReconnectSameClientOnly object.
    * <p />
    * @param testName   The name used in the test suite
    */
   public TestReconnectSameClientOnly(Global glob, String testName) {
      super(testName);
      this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load our demo qos plugin.
    * <p />
    * Then we connect as a client
    */
   protected void setUp() {
      //Global embeddedGlobal = glob.getClone(null);  
      log.info("#################### setup-testReconnectSameClientOnly ...");
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing");
      }
   }

   /**
    * @param The oid of the status message 
    * @param state Choose one of "2M" or "64k"
    */
   public void testReconnectSameClientOnly() {
      log.info("#################### testReconnectSameClientOnly ...");

      try {
         log.info("Connecting first ...");
         this.con = glob.getXmlBlasterAccess();

         ConnectQos qos = new ConnectQos(glob, "JOE/1", "secret");

         CallbackAddress callback = new CallbackAddress(glob);
         callback.setPingInterval(1000);
         qos.addCallbackAddress(callback);

         SessionQos sessionQos = qos.getSessionQos();
         sessionQos.setMaxSessions(1);
         sessionQos.setReconnectSameClientOnly(true);
         this.con.connect(qos, this);
      }
      catch (Exception e) {
         Thread.dumpStack();
         fail(ME+": Can't connect to xmlBlaster: " + e.toString());
      }

      try {
         log.info("Connecting other ...");
         Global glob2 = glob.getClone(null);
         I_XmlBlasterAccess con2 = glob2.getXmlBlasterAccess();

         // Activate plugin for callback only:
         ConnectQos qos = new ConnectQos(glob2, "JOE/1", "secret");
         SessionQos sessionQos = qos.getSessionQos();
         sessionQos.setMaxSessions(1);
         sessionQos.setReconnectSameClientOnly(true);
         con2.connect(qos, this);
         fail(ME+": Reconnect to xmlBlaster should not be possible");
      }
      catch (XmlBlasterException e) {
         log.info("SUCCESS, reconnect is not possible: " + e.getMessage());
      }

      boolean isSocket = (this.con.getCbServer() instanceof SocketCallbackImpl);
      boolean isRpc = (this.con.getCbServer() instanceof XmlRpcCallbackImpl);

      if (isSocket || isRpc) {
         // xmlBlaster destroys our first session:
         this.con.leaveServer(null);
      }
      else { // "IOR"
         // Now shutdown callback server so that xmlBlaster destroys our first session:
         try {
            this.con.getCbServer().shutdown();
         }
         catch (XmlBlasterException e) {
            fail("Can't setup test: " + e.getMessage());
         }
      }

      try { Thread.sleep(2000); } catch( InterruptedException i) {} // Wait

      try {
         log.info("Connecting other ...");
         Global glob2 = glob.getClone(null);
         I_XmlBlasterAccess con2 = glob2.getXmlBlasterAccess();

         // Activate plugin for callback only:
         ConnectQos qos = new ConnectQos(glob2, "JOE/1", "secret");
         SessionQos sessionQos = qos.getSessionQos();
         sessionQos.setMaxSessions(1);
         sessionQos.setReconnectSameClientOnly(true);
         con2.connect(qos, this);
         log.info("SUCCESS, reconnect is OK after first session died");
      }
      catch (XmlBlasterException e) {
         fail(ME + ": Reconnect should now be possible: " + e.getMessage());
      }

      log.info("Success in testReconnectSameClientOnly()");
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.severe("TEST FAILED: UpdateKey.toString()=" + updateKey.toString() +
                    "UpdateQos.toString()=" + updateQos.toString());
      fail("Unexpected UpdateKey.toString()=" + updateKey.toString());
      return "";
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info("#################### tearDown-testReconnectSameClientOnly ...");

      this.con.disconnect(null);
      this.con = null;

      if (this.startEmbedded) {
         try { Thread.sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
      this.con = null;
      Global.instance().shutdown();
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestReconnectSameClientOnly(Global.instance(), "testReconnectSameClientOnly"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *  java org.xmlBlaster.test.qos.TestReconnectSameClientOnly -logging/org.xmlBlaster.client.qos FINE -logging/org.xmlBlaster.util.qos FINE -logging/org.xmlBlaster.engine FINEST 
    *  java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestReconnectSameClientOnly
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestReconnectSameClientOnly testSub = new TestReconnectSameClientOnly(glob, "TestReconnectSameClientOnly");
      testSub.setUp();
      testSub.testReconnectSameClientOnly();
      testSub.tearDown();
   }
}

