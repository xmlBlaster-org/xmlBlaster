/*------------------------------------------------------------------------------
Name:      TestPersistentSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * Tests the persistent sessions .
 * <br />For a description of what this persistent sessions and subscriptions are
 * please read the requirement engine.persistent.session.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestPersistentSession
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestPersistentSession
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestPersistentSession extends TestCase implements I_ConnectionStateListener, I_Callback
{
   private static String ME = "TestPersistentSession";
   private static final boolean TRANSIENT = false;
   private static final boolean PERSISTENT = true;
   
   private Global glob;
   private LogChannel log;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor[] updateInterceptors;
   private I_XmlBlasterAccess con;
   private String senderName;

   private int numPublish = 8;
   private int numStop = 3;
   private int numStart = 5;
   private final String contentMime = "text/plain";

   private final long reconnectDelay = 2000L;
   private boolean failsafeCallback = true;
   /** the session is persistent from the beginning */
   private boolean persistent = true;
   private boolean exactSubscription = false;
   private boolean initialUpdates = true;
   private int numSubscribers = 4;

   public TestPersistentSession(String testName) {
      this(null, testName);
   }

   public TestPersistentSession(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.senderName = testName;
      this.updateInterceptors = new MsgInterceptor[this.numSubscribers];
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;
      this.log = this.glob.getLog("test");
      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      try {
         con = glob.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd); // == "<qos>...</qos>";
         // set the persistent connection 
         connectQos.setPersistent(this.persistent);
         // Setup fail save handling for connection ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(-1L); // switched off
         con.registerConnectionListener(this);
         connectQos.setAddress(addressProp);
         
         // setup failsafe handling for callback ...
         if (this.failsafeCallback) {
            CallbackAddress cbAddress = new CallbackAddress(this.glob);
            cbAddress.setRetries(-1);
            cbAddress.setPingInterval(-1);
            cbAddress.setDelay(1000L);
            connectQos.addCallbackAddress(cbAddress);
         }

         con.connect(connectQos, this);  // Login to xmlBlaster, register for updates
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed: " + e.getMessage());
          fail("setUp() - login fail: " + e.getMessage());
      }
      catch (Exception e) {
          log.error(ME, "setUp() - login failed: " + e.toString());
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
      log.info(ME, "Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestPersistentSession-AGENT" +
                      "</key>";

      String qos = "<qos><forceDestroy>true</forceDestroy></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);

         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info(ME, "Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
      }
      catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.getMessage());
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
   public void doSubscribe(int num, boolean isExact, boolean isPersistent) {
      try {
         SubscribeKey key = null;
         if (isExact)  key = new SubscribeKey(this.glob, "Message-1");
         else key = new SubscribeKey(this.glob, "//TestPersistentSession-AGENT", "XPATH");

         SubscribeQos qos = new SubscribeQos(this.glob); // "<qos><persistent>true</persistent></qos>";
         qos.setPersistent(isPersistent);
         qos.setWantInitialUpdate(this.initialUpdates);
         qos.setWantNotify(false); // to avoig getting erased messages

         this.updateInterceptors[num] = new MsgInterceptor(this.glob, log, null); // Collect received msgs
         this.updateInterceptors[num].setLogPrefix("interceptor-" + num);
         SubscribeReturnQos subscriptionId = con.subscribe(key, qos, this.updateInterceptors[num]);

         log.info(ME, "Success: Subscribe on subscriptionId=" + subscriptionId.getSubscriptionId() + " done");
         assertTrue("returned null subscriptionId", subscriptionId != null);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }
 
   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish(int counter) throws XmlBlasterException {
      String oid = "Message" + "-" + counter;
      log.info(ME, "Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestPersistentSession-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPersistentSession-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      con.publish(msgUnit);
      log.info(ME, "Success: Publishing of " + oid + " done");
   }

   /**
    * TEST: <br />
    */
   public void persistentSession(boolean doStop) {
      //doSubscribe(); -> see reachedAlive()
      log.info(ME, "Going to publish " + numPublish + " messages, xmlBlaster will be down for message 3 and 4");
      // 
      doSubscribe(0, this.exactSubscription, TRANSIENT);
      doSubscribe(1, this.exactSubscription, PERSISTENT);
      
      for (int i=0; i<numPublish; i++) {
         try {
            if (i == numStop) { // 3
               if (doStop) {
                  log.info(ME, "Stopping xmlBlaster, but continue with publishing ...");
                  EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
                  this.serverThread = null;
               }
               else {
                  log.info(ME, "changing run level but continue with publishing ...");
                  this.serverThread.changeRunlevel(0, true);
               }
            }
            if (i == numStart) {
               if (doStop) {
                  log.info(ME, "Starting xmlBlaster again, expecting the previous published two messages ...");
                  serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
                  log.info(ME, "xmlBlaster started, waiting on tail back messsages");
               }
               else {
                  log.info(ME, "changing runlevel again to runlevel 9. Expecting the previous published two messages ...");
                  this.serverThread.changeRunlevel(9, true);
                  log.info(ME, "xmlBlaster runlevel 9 reached, waiting on tail back messsages");
               }
               
               // Message-4 We need to wait until the client reconnected (reconnect interval)
               // Message-5
               assertEquals("", 2, this.updateInterceptors[1].waitOnUpdate(reconnectDelay*2L, 2));
               assertEquals("", 2, this.updateInterceptors[3].waitOnUpdate(reconnectDelay*2L, 2));
               
               for (int j=0; j < this.numSubscribers; j++) this.updateInterceptors[j].clear();
            }
            doPublish(i+1);
            if (i == 0) {
               doSubscribe(2, this.exactSubscription, TRANSIENT);
               doSubscribe(3, this.exactSubscription, PERSISTENT);
            }

            if (i < numStop || i >= numStart ) {
               int n = 1;
               if (i == 0 && !this.initialUpdates) n = 0;
               assertEquals("Message nr. " + (i+1), 1, this.updateInterceptors[1].waitOnUpdate(4000L, 1));
               assertEquals("Message nr. " + (i+1), n, this.updateInterceptors[3].waitOnUpdate(4000L, n));
            }
            for (int j=0; j < this.numSubscribers; j++) this.updateInterceptors[j].clear();
         }
         catch(XmlBlasterException e) {
            if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_POLLING)
               log.warn(ME, "Lost connection, my connection layer is polling: " + e.getMessage());
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
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info(ME, "I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      // doSubscribe();    // initialize on startup and on reconnect
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warn(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      this.log.info(ME, "Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      this.log.info(ME, "further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      this.log.error(ME, "update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");
      return "OK";
   }


   public void testXPathInitialStop() {
      this.exactSubscription = false;
      this.initialUpdates = true;
      persistentSession(true);
   }

   public void testXPathNoInitialStop() {
      this.exactSubscription = false;
      this.initialUpdates = false;
      persistentSession(true);
   }

   public void testXPathInitialRunlevelChange() {
      this.persistent = true;
      this.exactSubscription = false;
      this.initialUpdates = true;
      persistentSession(false);
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestPersistentSession
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestPersistentSession</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestPersistentSession testSub = new TestPersistentSession(glob, "TestPersistentSession/1");
/*
      testSub.setUp();
      testSub.testXPathInitialStop();
      testSub.tearDown();

      testSub.setUp();
      testSub.testXPathNoInitialStop();
      testSub.tearDown();
*/
      testSub.setUp();
      testSub.testXPathInitialRunlevelChange();
      testSub.tearDown();
   }
}

