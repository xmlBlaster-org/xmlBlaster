/*------------------------------------------------------------------------------
Name:      TestFailSafe.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.util.dispatch.DispatchManager;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * Tests the fail safe behavior of the I_XmlBlasterAccess client helper class (does not test server side fail safe).
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of I_XmlBlasterAccess.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestFailSafe
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestFailSafe
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestFailSafe extends TestCase implements I_ConnectionStateListener
{
   private static String ME = "TestFailSafe";
   private Global glob;
   private static Logger log = Logger.getLogger(TestFailSafe.class.getName());

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private I_XmlBlasterAccess con;
   private String senderName;

   private int numPublish = 8;
   private int numStop = 3;
   private int numStart = 5;
   private final String contentMime = "text/plain";

   private final long reconnectDelay = 2000L;

   public TestFailSafe(String testName) {
      this(null, testName);
   }

   public TestFailSafe(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.senderName = testName;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;


      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      try {
         con = glob.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd); // == "<qos>...</qos>";

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(-1L); // switched off
         con.registerConnectionListener(this);

         connectQos.setAddress(addressProp);

         this.updateInterceptor = new MsgInterceptor(this.glob, log, null); // Collect received msgs

         con.connect(connectQos, this.updateInterceptor);  // Login to xmlBlaster, register for updates
      }
      catch (XmlBlasterException e) {
          log.warning("setUp() - login failed: " + e.getMessage());
          fail("setUp() - login fail: " + e.getMessage());
      }
      catch (Exception e) {
          log.severe("setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login fail: " + e.toString());
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

         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info("Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
         
         if (defaultPlugin.getValue().startsWith("RAM"))
            assertEquals("Wrong number of message erased", (numPublish - numStop), arr.length);
         else
            assertEquals("Wrong number of message erased", numPublish, arr.length);
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }
      finally {
         con.disconnect(null);

         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;

         // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
         Util.resetPorts(glob);

         this.glob = null;
         this.con = null;
         Global.instance().shutdown();
      }
   }

   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void doSubscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSafe-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         SubscribeReturnQos subscriptionId = con.subscribe(xmlKey, qos);
         log.info("Success: Subscribe on subscriptionId=" + subscriptionId.getSubscriptionId() + " done");
         assertTrue("returned null subscriptionId", subscriptionId != null);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish(int counter) throws XmlBlasterException {
      String oid = "Message" + "-" + counter;
      log.info("Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSafe-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSafe-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      con.publish(msgUnit);
      log.info("Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSafe()
   {
      //doSubscribe(); -> see reachedAlive()
      log.info("Going to publish " + numPublish + " messages, xmlBlaster will be down for message 3 and 4");
      for (int ii=0; ii<numPublish; ii++) {
         try {
            if (ii == numStop) { // 3
               log.info("Stopping xmlBlaster, but continue with publishing ...");
               EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
               this.serverThread = null;
            }
            if (ii == numStart) {
               log.info("Starting xmlBlaster again, expecting the previous published two messages ...");
               serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
               log.info("xmlBlaster started, waiting on tail back messsages");
               
               // Message-4 We need to wait until the client reconnected (reconnect interval)
               // Message-5
               assertEquals("", 2, this.updateInterceptor.waitOnUpdate(reconnectDelay*2L, 2));
               this.updateInterceptor.clear();
            }
            doPublish(ii+1);
            if (ii < numStop || ii >= numStart ) {
               assertEquals("", 1, this.updateInterceptor.waitOnUpdate(4000L, 1));
            }
            this.updateInterceptor.clear();
         }
         catch(XmlBlasterException e) {
            if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_POLLING)
               log.warning("Lost connection, my connection layer is polling: " + e.getMessage());
            else if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_DEAD)
               assertTrue("Lost connection, my connection layer is NOT polling", false);
            else
               assertTrue("Publishing problems: " + e.getMessage(), false);
         }
      }
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAliveSync(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      doSubscribe();    // initialize on startup and on reconnect
   }
   
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
   }
   

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warning("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestFailSafe
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestFailSafe</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }
      TestFailSafe testSub = new TestFailSafe(glob, "TestFailSafe");
      testSub.setUp();
      testSub.testFailSafe();
      testSub.tearDown();
   }
}

