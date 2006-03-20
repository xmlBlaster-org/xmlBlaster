/*------------------------------------------------------------------------------
Name:      TestPtPPersistent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.test.util.PtPDestination;

import junit.framework.*;


/**
 * Tests the sending of persistent PtP messages to a session
 * while resourcea are critical (swapping of all queues and callback
 * queue overflow) when both the server and client crash.
 *
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestPtPPersistent
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestPtPPersistent
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestPtPPersistent extends TestCase  {
   private static String ME = "TestPtPPersistent";
   private static final long PUB_DELAY=250L;
   private Global glob;
   private static Logger log = Logger.getLogger(TestPtPPersistent.class.getName());

   private int serverPort = 7674;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private String senderName;

   private final long reconnectDelay = 500L;
   private PtPDestination destination;

   public TestPtPPersistent(String testName) {
      this(null, testName);
   }

   public TestPtPPersistent(Global glob, String testName) {
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
         I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd); // == "<qos>...</qos>";
         // Setup fail save handling for connection ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(500L); // switched off
         connectQos.setAddress(addressProp);
         con.connect(connectQos, null);  // Login to xmlBlaster, register for updates
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
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      try {
         log.info("Entering tearDown(), test is finished");
         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info("Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
         EraseKey eraseKey = new EraseKey(this.glob, "//airport", "XPATH");      
         EraseQos eraseQos = new EraseQos(this.glob);
         con.erase(eraseKey, eraseQos);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
      }
      finally {
         con.disconnect(null);
         try {
            Thread.sleep(1000L);
         }
         catch (Exception ex) {
         }
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
         // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
         Util.resetPorts(glob);
         this.glob = null;
         con = null;
         Global.instance().shutdown();
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * 
    * <p />
    */
   public void doPublish(int counter, String oid, boolean doGc, long sleep) throws XmlBlasterException {
      String content = "" + counter;
      log.info("Publishing message " + content);

      PublishKey key = new PublishKey(this.glob);
      if (oid != null) key.setOid(oid);
      key.setClientTags("<airport/>");
      PublishQos qos = new PublishQos(glob);
      qos.setPersistent(true);
      qos.setVolatile(true);
      qos.addDestination(new Destination(new SessionName(this.glob, "joe")));
      MsgUnit msgUnit = new MsgUnit(key, content, qos);

      this.glob.getXmlBlasterAccess().publish(msgUnit);
      if (doGc) Util.gc(2);
      try {
         Thread.sleep(sleep);
      }
      catch (Exception ex) {
      }
      log.info("Success: Publishing of " + content + " done");
   }

   public void testPersistentPtPOneOidWithGc() {
      persistentPtP("persistentPtP", true);
   }

   public void testPersistentPtPOneOidNoGc() {
      persistentPtP("persistentPtP", false);
   }

   public void testPersistentPtPNoOidWithGc() {
      persistentPtP(null, true);
   }

   public void testPersistentPtPNoOidNoGc() {
      persistentPtP(null, false);
   }
   /**
    * TEST: <br />
    * Sets up a PtP destination (a subject)
    * 
    */
   public void persistentPtP(String oid, boolean doGc) {
      long cbMaxEntries = 3;
      long cbMaxEntriesCache = 2;
      long subjMaxEntries = 3;
      long subjMaxEntriesCache = 2;
      
      long exLimit = cbMaxEntries + subjMaxEntries + 2;

      
      this.destination = new PtPDestination(this.glob, "joe/1");
      /** wants PtP messages and does not shutdown */
      boolean wantsPtP = true;
      boolean shutdownCB = false;
      try {
         this.destination.init(wantsPtP, shutdownCB, cbMaxEntries, cbMaxEntriesCache, subjMaxEntries, subjMaxEntriesCache);
      }
      catch (XmlBlasterException ex) {
         assertTrue("an exception while initing the destination should not occur " + ex.getMessage(), false);               
      }
      
      for (int i=0; i < exLimit; i++) {
         try {
            doPublish(i, oid, doGc, PUB_DELAY);
         }
         catch (Exception ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }
         
      int ret = this.destination.getUpdateInterceptor().waitOnUpdate(300L*exLimit, (int)exLimit);
      assertEquals("wrong number of entries arrived", (int)exLimit, ret);
      ret = this.destination.getUpdateInterceptor().waitOnUpdate(500L, (int)exLimit+1);
      assertEquals("wrong number of entries arrived", (int)exLimit, ret);
      
      Msg[] msg = this.destination.getUpdateInterceptor().getMsgs();
      assertEquals("wrong number of messages", exLimit, msg.length);
      for (int i=0; i < exLimit; i++) {
         assertEquals("wrong message sequence at ", i, msg[i].getContentInt());
      }
      this.destination.getUpdateInterceptor().clear();      

      // now stop the receiver by shutting down its cbServer and fill cbQueue and subjQueue
      this.destination.getConnection().leaveServer(null);

      for (long i=exLimit; i < 2 * exLimit; i++) {
         try {
            doPublish((int)i, oid, doGc, PUB_DELAY);
         }
         catch (XmlBlasterException ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }
      this.destination.check(250L, 0);
      
      for (long i=2*exLimit; i < 2*exLimit + 2; i++) {
         try {
            doPublish((int)i, oid, doGc, PUB_DELAY);
            assertTrue("an exception on publish '" + i + "' should have occurred ", false);
         }
         catch (XmlBlasterException ex) {
            log.info("this is an allowed exception since queues are overflown");
         }
      }

      this.destination.check(250L, 0);

      // stop and restart the server
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(this.serverPort));

      // reconnect to server (for the destination, the publisher never left) 
      this.destination = new PtPDestination(this.glob, "joe/1");
      /** wants PtP messages and does not shutdown */
      
      try {
         // we pass -1 -1 for the subject queue to avoid reconviguration 
         // otherwise it will shut down the callback
         this.destination.init(true, false, cbMaxEntries, cbMaxEntriesCache, subjMaxEntries, subjMaxEntriesCache);
         Thread.sleep(1000L);
      }
      catch (Exception ex) {
         assertTrue("an exception while initing the destination should not occur " + ex.getMessage(), false);
      }

      for (long i=2*exLimit; i < 3*exLimit; i++) {
         try {
            doPublish((int)i, oid, doGc, PUB_DELAY);
         }
         catch (Exception ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }

      ret = this.destination.getUpdateInterceptor().waitOnUpdate(3000L*exLimit, (int)(2*exLimit));
      assertEquals("wrong number of messages arrived", (int)2*exLimit, ret);
      ret = this.destination.getUpdateInterceptor().waitOnUpdate(1000L, (int)(2*exLimit));
      assertEquals("wrong number of messages arrived", (int)(2*exLimit), ret);
      
      msg = this.destination.getUpdateInterceptor().getMsgs();
      if (oid != null) { // if oid is different sequence is not garanteed
         for (int i=0; i < msg.length; i++) {
            assertEquals("wrong message sequence (number of entries arrived: " + msg.length + ") ", i+(int)exLimit, msg[i].getContentInt());
         }
      }
      if ((long)msg.length != exLimit*2) {
         try {
            GetKey getKey = new GetKey(this.glob, "__cmd?dump");
            GetQos getQos = new GetQos(this.glob); 
            MsgUnit[] tmp = this.glob.getXmlBlasterAccess().get(getKey, getQos);
            if (tmp.length > 0) 
               log.info(tmp[0].getContentStr());
         }
         catch (XmlBlasterException ex) {
            ex.printStackTrace();
         }
      }
      assertEquals("wrong number of entries arrived", exLimit*2, (long)msg.length);
      this.destination.getUpdateInterceptor().clear();            
      this.destination.shutdown(true);
      this.destination = null;
   }


   /**
    * Invoke: java org.xmlBlaster.test.client.TestPtPPersistent
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestPtPPersistent</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestPtPPersistent test = new TestPtPPersistent(glob, "TestPtPPersistent/1");

      test.setUp();
      test.testPersistentPtPOneOidWithGc();
      test.tearDown();

      test.setUp();
      test.testPersistentPtPOneOidNoGc();
      test.tearDown();

      test.setUp();
      test.testPersistentPtPNoOidWithGc();
      test.tearDown();

      test.setUp();
      test.testPersistentPtPNoOidNoGc();
      test.tearDown();

   }
}

