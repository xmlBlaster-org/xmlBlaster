/*------------------------------------------------------------------------------
Name:      TestXmlBlasterAccessMultiThreaded.java
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
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.test.Msg;
import junit.framework.*;

import java.util.Vector;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Tests the thread safety of the I_XmlBlasterAccess client helper class
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestXmlBlasterAccessMultiThreaded
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestXmlBlasterAccessMultiThreaded
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestXmlBlasterAccessMultiThreaded extends TestCase implements I_ConnectionStateListener
{
   private static String ME = "TestXmlBlasterAccessMultiThreaded";
   private Global glob;
   private static Logger log = Logger.getLogger(TestXmlBlasterAccessMultiThreaded.class.getName());

   private int serverPort = 7404;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private I_XmlBlasterAccess con;
   private String senderName;

   private int numPublish = 20;
   private int numStop = 3;
   private int numStart = 5;

   private int iThread = 0;
   private final String contentMime = "text/plain";

   private final long reconnectDelay = 2000L;

   public TestXmlBlasterAccessMultiThreaded(String testName) {
      this(null, testName);
   }

   public TestXmlBlasterAccessMultiThreaded(Global glob, String testName) {
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
      this.glob = (this.glob == null) ? new Global() : this.glob;


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
                      "   //TestXmlBlasterAccessMultiThreaded-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);
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
                      "   //TestXmlBlasterAccessMultiThreaded-AGENT" +
                      "</key>";
      String qos = "<qos><notify>false</notify></qos>"; // send no erase events
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
   public MsgUnit doPublish(String oid, String content) throws XmlBlasterException {
      log.info("Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestXmlBlasterAccessMultiThreaded-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestXmlBlasterAccessMultiThreaded-AGENT>" +
                      "</key>";
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      con.publish(msgUnit);
      log.info("Success: Publishing of " + oid + " content='" + content + "' done");
      return msgUnit;
   }


   /**
    * TEST: <br />
    */
   public void testPublishThreads()
   {
      //doSubscribe(); -> see reachedAlive()
      ME = "TestXmlBlasterAccessMultiThreaded.testPublishThreads()";
      final String oid = "TestXmlBlasterAccessMultiThreaded";
      int numThreads = 5;
      log.info("Going to publish " + numPublish + " messages with each of " + numThreads + " threads");
      final Vector sentMsgVec = new Vector(numPublish*numThreads);
      PublishThread[] publishThreads = new PublishThread[numThreads];
      for (iThread=0; iThread<numThreads; iThread++) {
         publishThreads[iThread] = new PublishThread(""+iThread, numPublish, oid);
         publishThreads[iThread].start();
      }

      log.info("Trying join ...");

      for (int kk=0; kk<numThreads; kk++) {
         try {
            publishThreads[kk].join();
         }
         catch (InterruptedException ie) {
            log.warning("Caught join() exception: " + ie.toString());
         }
      }

      log.info("Threads are joined");

      // Now check everything:

      this.updateInterceptor.waitOnUpdate(3000L, numPublish * numThreads);
      Msg[] msgs = this.updateInterceptor.getMsgs(oid, Constants.STATE_OK);
      //msg.compareMsg(sentMsgVec.elementAt[i]);
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}
      assertEquals("Too many messages arrived", numPublish * numThreads, this.updateInterceptor.count());

      HashSet set = new HashSet();
      for (int ii=0; ii<msgs.length; ii++) {
         assertEquals("Duplicate messages!!! '" + msgs[ii].getContentStr(), true, set.add(msgs[ii].getContentStr()));
      }

      int[] lastMsg = new int[numThreads];
      for (int iThread=0; iThread<numThreads; iThread++) lastMsg[iThread] = -1;

      for (int ii=0; ii<msgs.length; ii++) {
         String content = msgs[ii].getContentStr();
         int sepPos = content.indexOf(":");
         int iThread = Integer.parseInt(content.substring(0, sepPos));
         int iMsg = Integer.parseInt(content.substring(sepPos+1));
         if (iMsg <= lastMsg[iThread]) {
            fail("Messages are not in ascending order, last=" + lastMsg + " curr=" + iMsg);
         }
         lastMsg[iThread] = iMsg;
      }
      
      log.info("SUCCESS, all check are OK.");
   }

   /**
    * Helper class for publisher threads
    */
   class PublishThread extends Thread {
      private final ArrayList sentMsgList;
      private final int numPublish;
      private final String oid;
      /** @param name Is thread index from 0 to numThreads-1 */
      public PublishThread(String name, int numPublish, String oid) {
         super(name);
         this.numPublish = numPublish;
         this.oid = oid;
         this.sentMsgList = new ArrayList(numPublish);
      }
      public void run() {
         log.info("Started thread " + iThread + ": " + Thread.currentThread().getName());
         for (int ii=0; ii<numPublish; ii++) {
            try {
               MsgUnit msgUnit = doPublish(oid, Thread.currentThread().getName() + ":" + (ii+1));
               sentMsgList.add(msgUnit);
            }
            catch (XmlBlasterException e) {
               log.severe("Fail: " + e.getMessage());
               fail(ME+": "+e.getMessage());
            }
         }
         log.info(Thread.currentThread().getName() + ": Published " + numPublish + " messages");
      }
      public final ArrayList getSentMsgList() {
         return this.sentMsgList;
      }
   };

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info("I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      doSubscribe();    // initialize on startup and on reconnect
   }

   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warning("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.POLLING);
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.severe("DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestXmlBlasterAccessMultiThreaded
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestXmlBlasterAccessMultiThreaded</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }
      TestXmlBlasterAccessMultiThreaded testSub = new TestXmlBlasterAccessMultiThreaded(glob, "TestXmlBlasterAccessMultiThreaded");
      testSub.setUp();
      testSub.testPublishThreads();
      testSub.tearDown();
   }
}

