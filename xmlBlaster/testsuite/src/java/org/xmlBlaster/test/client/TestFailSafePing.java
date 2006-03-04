/*------------------------------------------------------------------------------
Name:      TestFailSafePing.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.jutils.init.Property;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * Tests the fail save behavior of the I_XmlBlasterAccess client helper class,
 * especially the pinging to xmlBlaster. This allows auto detection if the
 * connection to xmlBlaster is lost.
 *
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of I_XmlBlasterAccess.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner org.xmlBlaster.test.client.TestFailSafePing
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestFailSafePing
 * </pre>
 */
public class TestFailSafePing extends TestCase implements I_ConnectionStateListener
{
   private static String ME = "TestFailSafePing";
   private Global glob;
   private static Logger log = Logger.getLogger(TestFailSafePing.class.getName());

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private I_XmlBlasterAccess con;
   private String senderName;
   private MsgUnit[] msgUnitArr;
   private int counter;

   private final String contentMime = "text/plain";

   public TestFailSafePing(String testName) {
      this(null, testName);
   }

   /**
    * Constructs the TestFailSafePing object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestFailSafePing(Global glob, String testName)
   {
      super(testName);
      this.glob = glob;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      this.glob = (this.glob == null) ? new Global() : this.glob;

      this.senderName = "TestFailSafePing-joe/97";

      this.glob.init(Util.getOtherServerPorts(serverPort));

      //this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      try {

         con = this.glob.getXmlBlasterAccess(); // Find server

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(this.glob, senderName, passwd);

         // Setup fail save handling ...
         Address addressProp = new Address(this.glob);
         addressProp.setDelay(1000L);         // retry connecting every 1 sec
         addressProp.setRetries(-1);          // -1 == forever
         addressProp.setPingInterval(1000L);  // ping every second
         con.registerConnectionListener(this);

         connectQos.setAddress(addressProp);
         
         this.updateInterceptor = new MsgInterceptor(this.glob, log, null); // Collect received msgs

         // and do the login ...
         con.connect(connectQos, this.updateInterceptor); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
          log.warning("setUp() - login failed");
      }
      catch (Exception e) {
          log.severe("setUp() - login failed: " + e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSafe-AGENT" +
                      "</key>";
      String qos = "<qos><forceDestroy>true</forceDestroy></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }

      con.disconnect(null);
      con = null;

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(this.glob);
      Global.instance().shutdown();
      this.glob = null;
      this.log = null;
      this.serverThread = null;
      this.updateInterceptor = null;
      this.msgUnitArr = null;
   }

   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void doSubscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSafePing-AGENT" +
                      "</key>";
      String qos = "<qos><initialUpdate>false</initialUpdate></qos>";
      try {
         String subscribeOid = con.subscribe(xmlKey, qos).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeOid + " done");
         assertTrue("returned null subscribeOid", subscribeOid != null);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish() throws XmlBlasterException
   {
      counter++;
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message " + counter + " ...");

      String oid = "MyMessage-" + counter;
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSafePing-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSafePing-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());
      msgUnitArr = new MsgUnit[] { msgUnit };
      con.publish(msgUnit);
      log.info("Success: Publishing of " + oid + " done");
   }

   /**
    * TEST: <br />
    */
   public void testFailSafe()
   {
      for (int i=0; i<3; i++) {
         this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
         // Wait some time, to allow the login poller to reconnect
         try { Thread.currentThread().sleep(2000L); } catch( InterruptedException ie) {}

         // reachedAlive published a msg on reconnect, check it here:
         assertEquals("", 1, this.updateInterceptor.waitOnUpdate(2000L, 1));
         this.updateInterceptor.compareToReceived(msgUnitArr, null);
         this.updateInterceptor.clear();

         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
         // Wait some time, ping should activate login polling
         try { Thread.currentThread().sleep(2000L); } catch( InterruptedException ie) {}
      }
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      doSubscribe();    // initialize subscription
      try {
         doPublish();
      }
      catch(XmlBlasterException e) {
         if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_POLLING)
            log.warning("Lost connection, my connection layer is polling: " + e.getMessage());
         else if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_DEAD)
            assertTrue("Lost connection, my connection layer is not polling", false);
         else
            assertTrue("Publishing problems: " + e.getMessage(), false);
      }
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warning("I_ConnectionStateListener: Lost connection to xmlBlaster");
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestFailSafePing(null, "testFailSafe"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestFailSafePing
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestFailSafePing</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestFailSafePing testSub = new TestFailSafePing(glob, "TestFailSafePing");
      testSub.setUp();
      testSub.testFailSafe();
      testSub.tearDown();
   }
}

