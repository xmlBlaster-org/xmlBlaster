/*------------------------------------------------------------------------------
Name:      TestReconnectSameClientOnly.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


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
   private LogChannel log;

   private I_XmlBlasterAccess con = null;
   private String passwd = "secret";
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
      this.log = glob.getLog("test");
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
      log.info(ME, "#################### setup-testReconnectSameClientOnly ...");
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info(ME, "XmlBlaster is ready for testing");
      }
   }

   /**
    * @param The oid of the status message 
    * @param state Choose one of "2M" or "64k"
    */
   public void testReconnectSameClientOnly() {
      log.info(ME, "#################### testReconnectSameClientOnly ...");

      try {
         log.info(ME, "Connecting first ...");
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
         Thread.currentThread().dumpStack();
         fail(ME+": Can't connect to xmlBlaster: " + e.toString());
      }

      try {
         log.info(ME, "Connecting other ...");
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
         log.info(ME, "SUCCESS, reconnect is not possible: " + e.getMessage());
      }

      // Now shutdown callback server so that xmlBlaster destroys our first session:
      try {
         this.con.getCbServer().shutdown();
      }
      catch (XmlBlasterException e) {
         fail("Can't setup test: " + e.getMessage());
      }

      try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait

      try {
         log.info(ME, "Connecting other ...");
         Global glob2 = glob.getClone(null);
         I_XmlBlasterAccess con2 = glob2.getXmlBlasterAccess();

         // Activate plugin for callback only:
         ConnectQos qos = new ConnectQos(glob2, "JOE/1", "secret");
         SessionQos sessionQos = qos.getSessionQos();
         sessionQos.setMaxSessions(1);
         sessionQos.setReconnectSameClientOnly(true);
         con2.connect(qos, this);
         log.info(ME, "SUCCESS, reconnect is OK after first session died");
      }
      catch (XmlBlasterException e) {
         fail(ME + ": Reconnect should now be possible: " + e.getMessage());
      }

      log.info(ME, "Success in testReconnectSameClientOnly()");
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.error(ME, "TEST FAILED: UpdateKey.toString()=" + updateKey.toString() +
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
      log.info(ME, "#################### tearDown-testReconnectSameClientOnly ...");

      this.con.disconnect(null);
      this.con = null;

      if (this.startEmbedded) {
         try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
      this.log = null;
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
    *  java org.xmlBlaster.test.qos.TestReconnectSameClientOnly -trace[qos] true -call[core] true
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

