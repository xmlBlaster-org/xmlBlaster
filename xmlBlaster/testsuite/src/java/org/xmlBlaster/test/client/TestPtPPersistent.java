/*------------------------------------------------------------------------------
Name:      TestPtPPersistent.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
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
   
   private Global glob;
   private LogChannel log;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private I_XmlBlasterAccess con;
   private String senderName;

   private int numPublish = 8;
   private int numStop = 3;
   private int numStart = 5;
   private final String contentMime = "text/plain";

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
      this.log = this.glob.getLog("test");
      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      try {
         con = glob.getXmlBlasterAccess(); // Find orb
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
      PropString defaultPlugin = new PropString("CACHE,1.0");
      String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
      log.info(ME, "Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
      con.disconnect(null);
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
      this.con = null;
      Global.instance().shutdown();
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish(int counter) throws XmlBlasterException {
      String content = "" + counter;
      log.info(ME, "Publishing message " + content);

      PublishKey key = new PublishKey(this.glob);
      PublishQos qos = new PublishQos(glob);
      qos.setPersistent(true);
      qos.addDestination(new Destination(new SessionName(this.glob, "joe")));
      MsgUnit msgUnit = new MsgUnit(key, content, qos);

      con.publish(msgUnit);
      log.info(ME, "Success: Publishing of " + content + " done");
   }

   /**
    * TEST: <br />
    * Sets up a PtP destination (a subject)
    * 
    */
   public void testPersistentPtP() {
      long cbMaxEntries = 5;
      long cbMaxEntriesCache = 2;
      long subjMaxEntries = 5;
      long subjMaxEntriesCache = 2;
      
      long exLimit = cbMaxEntries + subjMaxEntries + 2;

      
      this.destination = new PtPDestination(this.glob, "joe/1");
      /** wants PtP messages and does not shutdown */
      
      try {
         this.destination.init(true, false, cbMaxEntries, cbMaxEntriesCache, subjMaxEntries, subjMaxEntriesCache);
      }
      catch (XmlBlasterException ex) {
         assertTrue("an exception while initing the destination should not occur " + ex.getMessage(), false);               
      }
      
      for (int i=0; i < exLimit; i++) {
         try {
            doPublish(i);
            Thread.sleep(250L);
         }
         catch (Exception ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }
      
      Msg[] msg = this.destination.getUpdateInterceptor().getMsgs();
      assertEquals("wrong number of messages", exLimit, msg.length);
      for (int i=0; i < exLimit; i++) {
         assertEquals("wrong message sequence", i, msg[i].getContentInt());
      }
      this.destination.getUpdateInterceptor().clear();      

      // now stop the receiver by shutting down its cbServer and fill cbQueue and subjQueue
      this.destination.getConnection().leaveServer(null);

      for (long i=exLimit; i < 2 * exLimit; i++) {
         try {
            doPublish((int)i);
         }
         catch (XmlBlasterException ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }
      this.destination.check(250L, 0);
      
      for (long i=2*exLimit; i < 2*exLimit + 5; i++) {
         try {
            doPublish((int)i);
            assertTrue("an exception on publish '" + i + "' should have occurred ", false);
         }
         catch (XmlBlasterException ex) {
            this.log.info(ME, "this is an allowed exception since queues are overflown");
         }
      }

      this.destination.check(250L, 0);

      // stop and restart the server      
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);

      try {
         Thread.sleep(2500L);
      }
      catch (Exception ex) {
      }

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
            doPublish((int)i);
            Thread.sleep(500L);
         }
         catch (Exception ex) {
            assertTrue("an exception on publish '" + i + "' should not occur " + ex.getMessage(), false);
         }
      }

      try {
         Thread.sleep(2000L);
      }
      catch (Exception ex) {
      }
      
      msg = this.destination.getUpdateInterceptor().getMsgs();
      for (int i=0; i < msg.length; i++) {
         assertEquals("wrong message sequence", i+(int)exLimit, msg[i].getContentInt());
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

      TestPtPPersistent testSub = new TestPtPPersistent(glob, "TestPtPPersistent/1");

      testSub.setUp();
      testSub.testPersistentPtP();
      testSub.tearDown();
   }
}

