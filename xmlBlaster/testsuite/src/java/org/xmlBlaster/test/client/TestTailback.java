/*------------------------------------------------------------------------------
Name:      TestTailback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.jutils.init.Property;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * Tests the correct reference counting for persistent messages
 * after recovery. 
 * <pre>
 *   1. Start xmlBlaster server, configure cache size to 2
 *   2. Publish two messages
 *   3. Subscribe to message in fail save mode and block in callback
 *   4. Kill client subscriber
 *   5. Publish two more message to swap the first two
 *   6. Start same subscriber without initial update - it will receive the messages from 3.
 *   7. Start another subscriber with history = 10: we expect 4 updates
 *   If the reference counter is not properly swapped and resored test 6. will fail.
 * </pre>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner org.xmlBlaster.test.client.TestTailback
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestTailback
 * </pre>
 */
public class TestTailback extends TestCase implements I_ConnectionStateListener
{
   private static String ME = "TestTailback";
   private Global glob;
   private LogChannel log;

   private int serverPort = 7694;
   private EmbeddedXmlBlaster serverThread;

   private String oid = "tailbackMsg";

   class Client {
      I_XmlBlasterAccess con;
      MsgInterceptor updateInterceptor;
   }

   public TestTailback(String testName) {
      this(null, testName);
   }

   /**
    * Constructs the TestTailback object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestTailback(Global glob, String testName) {
      super(testName);
      this.glob = glob;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? new Global() : this.glob;
      this.log = this.glob.getLog("test");
      this.glob.init(Util.getOtherServerPorts(serverPort));
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info(ME, "Entering tearDown(), test is finished");

      if (this.serverThread != null) {
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(this.glob);
      Global.instance().shutdown();
      this.glob = null;
      this.log = null;
      this.serverThread = null;
   }

   /**
    * Create a new connection
    * @param loginName The login name
    * @param cb The callback handle or null
    */
   private Client doConnect(String loginName, I_Callback cb) {
      try {
         Client client = new Client();
         Global gg = this.glob.getClone(null);
         ConnectQos connectQos = new ConnectQos(gg);
         SessionName sessionName = new SessionName(gg, loginName);
         connectQos.setSessionName(sessionName);
         client.con = gg.getXmlBlasterAccess();
         client.con.registerConnectionListener(this);
         client.updateInterceptor = new MsgInterceptor(gg, this.log, cb); // Collect received msgs
         client.con.connect(connectQos, client.updateInterceptor); // Login to xmlBlaster
         return client;
      }
      catch (XmlBlasterException e) {
         log.warn(ME, "doConnect() - login failed: " + e.getMessage());
         fail(ME+".doConnect() failed: " + e.getMessage());
      }
      return null;
   }

   /**
    * Subscribe to message. 
    */
   private void doSubscribe(I_XmlBlasterAccess con) {
      if (log.TRACE) log.trace(ME, "Subscribing using EXACT oid syntax ...");
      try {
         SubscribeKey subscribeKey = new SubscribeKey(con.getGlobal(), this.oid);
         SubscribeQos subscribeQos = new SubscribeQos(con.getGlobal());
         String subscribeOid = con.subscribe(subscribeKey, subscribeQos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
         assertTrue("returned null subscribeOid", subscribeOid != null);
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.getMessage());
         fail(ME+".doSubscribe() failed: " + e.getMessage());
      }
   }

   /**
    * Construct a message and publish it persistent. 
    */
   private void doPublish(I_XmlBlasterAccess con) {
      if (log.TRACE) log.trace(ME, "Publishing a message");
      try {
         PublishKey publishKey = new PublishKey(con.getGlobal(), this.oid);
         PublishQos publishQos = new PublishQos(con.getGlobal());
         publishQos.setPersistent(true);
         String content = "Hi";
         MsgUnit msgUnit = new MsgUnit(publishKey, content.getBytes(), publishQos);
         con.publish(msgUnit);
         log.info(ME, "Success: Publishing of " + this.oid + " done");
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.getMessage());
         fail(ME+".doPublish() failed: " + e.getMessage());
      }
   }

   /**
    * Erase the message. 
    */
   private void doErase(I_XmlBlasterAccess con) {
      log.info(ME, "Erasing " + this.oid + " ...");
      try {
         EraseKey eraseKey = new EraseKey(con.getGlobal(), this.oid);
         EraseQos eraseQos = new EraseQos(con.getGlobal());
         eraseQos.setForceDestroy(true);
         EraseReturnQos[] arr = con.erase(eraseKey, eraseQos);
      }
      catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.getMessage());
         fail(ME+".doErase() failed: " + e.getMessage());
      }
   }

   /**
    * Test as described in class javadoc. 
    */
   public void testTailback() {
      log.info(ME, "testTailback START");

      log.info(ME, "STEP1: Publish a persistent message twice, will be tailed back as no xmlBlaster runs");
      Client pub = doConnect("publisher/2", null); // with public session ID
      doPublish(pub.con);
      doPublish(pub.con);
      pub.con.disconnect(null);

      log.info(ME, "STEP2: Start xmlBlaster server");
      this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));

      log.info(ME, "STEP3: Start subscriber and subscribe");
      Client sub1 = doConnect("subscribe/1", null);
      doSubscribe(sub1.con);
      assertEquals("", 0, sub1.updateInterceptor.waitOnUpdate(1000L));
      sub1.updateInterceptor.clear();

      log.info(ME, "STEP4: Start same publisher again");
      pub = doConnect("publisher/2", null); // with public session ID
      assertEquals("", 2, sub1.updateInterceptor.waitOnUpdate(1000L, 2));

      log.info(ME, "STEP5: Success, cleanup");
      doErase(pub.con);
      pub.con.disconnect(null);
      sub1.con.disconnect(null);
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info(ME, "I_ConnectionStateListener-"+connection.getId()+": We were lucky, reconnected to xmlBlaster");
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (log!=null) log.warn(ME, "I_ConnectionStateListener-"+connection.getId()+": Lost connection to xmlBlaster");
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      if (log!=null) log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestTailback(null, "testTailback"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestTailback
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestTailback</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestTailback testSub = new TestTailback(glob, "TestTailback");
      testSub.setUp();
      testSub.testTailback();
      testSub.tearDown();
   }
}

