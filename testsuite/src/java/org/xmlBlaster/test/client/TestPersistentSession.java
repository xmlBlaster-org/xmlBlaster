/*------------------------------------------------------------------------------
Name:      TestPersistentSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.*;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * Tests the persistent sessions .
 * <br />For a description of what this persistent sessions and subscriptions are
 * please read the requirement engine.persistence.session.
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
   private Global origGlobal;
   private Global serverGlobal;
   private static Logger log = Logger.getLogger(TestPersistentSession.class.getName());

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor[] updateInterceptors;
   //private I_XmlBlasterAccess con;
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
      this.origGlobal = glob;
      this.senderName = testName;
      this.updateInterceptors = new MsgInterceptor[this.numSubscribers];
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      setup(false);
   }
   
   
   private void setup(boolean restrictedEntries) {
      this.origGlobal = (this.origGlobal == null) ? Global.instance() : this.origGlobal;

      
      this.origGlobal.init(Util.getOtherServerPorts(serverPort));
      this.glob = this.origGlobal.getClone(null);

      String[] args = null;
      if (restrictedEntries) {
         args = new String[] {"-persistence/session/maxEntriesCache", "1",
                       "-persistence/session/maxEntries","2",
                       "-persistence/subscribe/maxEntriesCache", "2",
                       "-persistence/subscribe/maxEntries","3",
                      };
      }
      this.serverGlobal = this.origGlobal.getClone(args);
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(this.serverGlobal);
      log.info("XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      
      System.out.println("============== Connect/Disconnect for general/1 to cleanup first");

      try { // we just connect and disconnect to make sure all resources are really cleaned up
         Global tmpGlobal = this.origGlobal.getClone(null);
         I_XmlBlasterAccess con = tmpGlobal.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(tmpGlobal, senderName, passwd); // == "<qos>...</qos>";
         connectQos.setSessionName(new SessionName(tmpGlobal, "general/1"));
         // set the persistent connection 
         connectQos.setPersistent(this.persistent);
         // Setup fail save handling for connection ...
         Address addressProp = new Address(tmpGlobal);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(-1L); // switched off
         con.registerConnectionListener(this);
         connectQos.setAddress(addressProp);
         
         // setup failsafe handling for callback ...
         if (this.failsafeCallback) {
            CallbackAddress cbAddress = new CallbackAddress(tmpGlobal);
            cbAddress.setRetries(-1);
            cbAddress.setPingInterval(-1);
            cbAddress.setDelay(1000L);
            cbAddress.setSecretCbSessionId("someSecredSessionId");
            connectQos.addCallbackAddress(cbAddress);
         }
         con.connect(connectQos, this);
         DisconnectQos disconnectQos = new DisconnectQos(tmpGlobal);
         con.disconnect(disconnectQos);
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

      System.out.println("============== Connect for general/1");

      try {
         I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(this.glob, senderName, passwd); // == "<qos>...</qos>";
         connectQos.setSessionName(new SessionName(this.glob, "general/1"));
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
            cbAddress.setSecretCbSessionId("someSecredSessionId");
            connectQos.addCallbackAddress(cbAddress);
         }

         con.connect(connectQos, this);  // Login to xmlBlaster, register for updates
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
      System.out.println("============== Entering tearDown(), test is finished");
      log.info("Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestPersistentSession-AGENT" +
                      "</key>";

      String qos = "<qos><forceDestroy>true</forceDestroy></qos>";
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      try {
         System.out.println("============== tearDown(), erase: " + xmlKey);
         con.erase(xmlKey, qos);

         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info("Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }
      finally {
         System.out.println("============== tearDown(), disconnect");
         con.disconnect(null);
         Util.delay(1000);
         System.out.println("============== tearDown(), stopXmlBlaster");
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
         // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
         Util.resetPorts(this.serverGlobal);
         Util.resetPorts(this.glob);
         Util.resetPorts(this.origGlobal);
         this.glob = null;
         this.serverGlobal = null;
         con = null;
         Global.instance().shutdown();
      }
      System.out.println("============== tearDown() done");
   }

   /**
    * TEST: Subscribe to messages with XPATH.
    */
   private void doSubscribe(int num, boolean isExact, boolean isPersistent) {
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
         SubscribeReturnQos subscriptionId = this.glob.getXmlBlasterAccess().subscribe(key, qos, this.updateInterceptors[num]);

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
                      "   <TestPersistentSession-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPersistentSession-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      this.glob.getXmlBlasterAccess().publish(msgUnit);
      log.info("Success: Publishing of " + oid + " done");
   }

   /**
    * TEST: <br />
    */
   public void persistentSession(boolean doStop) {
      //doSubscribe(); -> see reachedAlive()
      log.info("Going to publish " + numPublish + " messages, xmlBlaster will be down for message 3 and 4");
      // 
      doSubscribe(0, this.exactSubscription, TRANSIENT);
      doSubscribe(1, this.exactSubscription, PERSISTENT);
      
      for (int i=0; i<numPublish; i++) {
         try {
            if (i == numStop) { // 3
               if (doStop) {
                  log.info("Stopping xmlBlaster, but continue with publishing ...");
                  EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
                  System.out.println("============== Stopped xmlBlaster, but continue with publishing");
                  this.serverThread = null;
               }
               else {
                  log.info("changing run level but continue with publishing ...");
                  this.serverThread.changeRunlevel(0, true);
                  System.out.println("============== Changed run level to 0 but continue with publishing");
               }
            }
            if (i == numStart) {
               if (doStop) {
                  log.info("Starting xmlBlaster again, expecting the previous published two messages ...");
                  // serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
                  serverThread = EmbeddedXmlBlaster.startXmlBlaster(this.serverGlobal);
                  log.info("xmlBlaster started, waiting on tail back messsages");
                  System.out.println("============== XmlBlaster started, waiting on two tail back messsages");
               }
               else {
                  log.info("changing runlevel again to runlevel 9. Expecting the previous published two messages ...");
                  this.serverThread.changeRunlevel(9, true);
                  log.info("xmlBlaster runlevel 9 reached, waiting on tail back messsages");
                  System.out.println("============== Changed runlevel again to runlevel 9. Expecting the previous published two messages");
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
               log.warning("Lost connection, my connection layer is polling: " + e.getMessage());
            else if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_DEAD)
               assertTrue("Lost connection, my connection layer is NOT polling", false);
            else
               assertTrue("Publishing problems: " + e.getMessage(), false);
         }
      }
      doSubscribe(0, this.exactSubscription, TRANSIENT);
      doSubscribe(1, this.exactSubscription, PERSISTENT);
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      // doSubscribe();    // initialize on startup and on reconnect
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warning("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      log.info("Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      log.severe("update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions), further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      return "OK";
   }


   public void testXPathInitialStop() {
      this.exactSubscription = false;
      this.initialUpdates = true;
      System.out.println("============== testXPathInitialStop");
      persistentSession(true);
   }

   public void testXPathNoInitialStop() {
      this.exactSubscription = false;
      this.initialUpdates = false;
      System.out.println("============== testXPathNoInitialStop");
      persistentSession(true);
   }

   public void testXPathInitialRunlevelChange() {
      this.persistent = true;
      this.exactSubscription = false;
      this.initialUpdates = true;
      System.out.println("============== testXPathInitialRunlevelChange");
      persistentSession(false);
   }

   // -----------------------------------------------------------------
   private Global createConnection(Global parentGlobal, String sessionName, boolean isPersistent, boolean expectEx) {
      try {
         Global ret = parentGlobal.getClone(null);
         I_XmlBlasterAccess con = ret.getXmlBlasterAccess(); // Find orb
         ConnectQos connectQos = new ConnectQos(glob); // == "<qos>...</qos>";
         connectQos.setSessionName(new SessionName(ret, sessionName));
         // set the persistent connection 
         connectQos.setPersistent(isPersistent);
         // Setup fail save handling for connection ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(-1L); // switched off
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
         if (expectEx) assertTrue("an exception was expected here because of overflow: Configuration of session queue probably not working", false);
         return ret;
      }
      catch (XmlBlasterException ex) {
         if (expectEx) log.info("createConnection: exception was OK since overflow was expected");
         else assertTrue("an exception should not occur here", false);
      }
      return null; //to make compiler happy
   }      

   
   /**
    * Tests the requirement:
    * - If the storage for the sessions is overflown, it should throw an exception
    *
    */
   public void testOverflow() {
      System.out.println("============== testXPathNoInitialStop");
      // to change the configuration on server side (limit the queue sizes)
      tearDown();
      setup(true);
      Global[] globals = new Global[5];
      try {
         globals[0] = createConnection(this.origGlobal, "bjoern/1", true , false);
         globals[1] = createConnection(this.origGlobal, "fritz/2", false, false);
         globals[3] = createConnection(this.origGlobal, "dimitri/3", true , true); // <-- exception (since main connection also persistent)
         globals[2] = createConnection(this.origGlobal, "pandora/4", false , false); // OK since transient
         globals[4] = createConnection(this.origGlobal, "jonny/5", true, true);
      }
      finally {
         Util.delay(2000);
         for (int i=0; i < globals.length; i++) {
            if (globals[i] != null) globals[i].getXmlBlasterAccess().disconnect(new DisconnectQos(globals[i]));
         }
      }
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

      testSub.setUp();
      testSub.testXPathInitialStop();
      testSub.tearDown();

      testSub.setUp();
      testSub.testXPathNoInitialStop();
      testSub.tearDown();

      testSub.setUp();
      testSub.testXPathInitialRunlevelChange();
      testSub.tearDown();

      testSub.setUp();
      testSub.testOverflow();
      testSub.tearDown();

      log.info("Main done");
   }
}

