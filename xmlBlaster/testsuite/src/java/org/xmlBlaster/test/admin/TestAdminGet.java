/*------------------------------------------------------------------------------
Name:      TestAdminGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.admin;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.qos.QuerySpecQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.*;

import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * Tests the activation/deactivation of the DispatchManager.
 * <br />
 * If the DispatchManager is disactivated, asynchronous dispatch should not
 * be possible.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestAdminGet
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestAdminGet
 * </pre>
 */
public class TestAdminGet extends TestCase implements I_Callback
{
   
   public class PublisherThread extends Thread {
      private Global global;
      private long delay;
      private MsgUnit[] msgUnits;
      private Exception ex;
      
      public PublisherThread(Global global, long timeToWaitBeforePublishing, MsgUnit[] msgUnits) {
         this.global = global;
         this.delay = timeToWaitBeforePublishing;
         this.msgUnits = msgUnits;
         start();
      }
      
      public boolean hasException() {
         return (this.ex != null);
      }
      
      public void run() {
         try {
            if (this.delay > 0L) sleep(this.delay);
            this.global.getXmlBlasterAccess().publishArr(this.msgUnits);
         }
         catch (Exception ex) {
            ex.printStackTrace();
            this.ex = ex;
         }
      }
   }
   
   private static String ME = "TestAdminGet";
   
   private Global glob;
   private static Logger log = Logger.getLogger(TestAdminGet.class.getName());

   private MsgInterceptor updateInterceptor;
   private String senderName;

   private final String contentMime = "text/plain";
   
   private String sessionName = "dispatchTester/1";

   public TestAdminGet(String testName) {
      this(null, testName);
   }

   public TestAdminGet(Global glob, String testName) {
      super(testName);
      this.senderName = testName;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;

      this.updateInterceptor = new MsgInterceptor(this.glob, this.log, null);
      
      try {
         I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(this.glob, senderName, passwd); // == "<qos>...</qos>";
         connectQos.setSessionName(new SessionName(this.glob, this.sessionName));
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
      log.info("Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestAdminGet-AGENT" +
                      "</key>";

      String qos = "<qos><forceDestroy>true</forceDestroy></qos>";
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);

         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info("Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }
      finally {
         con.disconnect(null);
         // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
         this.glob = null;
         con = null;
         Global.instance().shutdown();
      }
   }

   /**
    * TEST: Subscribe to a specific oid
    */
   private void doSubscribe(String oid) {
      try {
         SubscribeKey key = new SubscribeKey(this.glob, oid);

         SubscribeQos qos = new SubscribeQos(this.glob); // "<qos><persistent>true</persistent></qos>";
         qos.setWantNotify(false); // to avoig getting erased messages

         SubscribeReturnQos subscriptionId = this.glob.getXmlBlasterAccess().subscribe(key, qos, this.updateInterceptor);

         log.info("Success: Subscribe on subscriptionId=" + subscriptionId.getSubscriptionId() + " done");
         assertTrue("returned null subscriptionId", subscriptionId != null);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }
 
   private void doUnSubscribe(String oid) {
      try {
         UnSubscribeKey key = new UnSubscribeKey(this.glob, oid);

         UnSubscribeQos qos = new UnSubscribeQos(this.glob);
         this.glob.getXmlBlasterAccess().unSubscribe(key, qos);
      } 
      catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }
 
   /**
    * TEST: Construct a message and publish it.
    * If the counter is negative, the content of the message will be an empty string.
    * <p />
    */
   public void doPublish(int counter, String oid) throws XmlBlasterException {
      log.info("Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'><test></test></key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = null;
      if (counter > -1) msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());
      else  msgUnit = new MsgUnit(xmlKey, "", qosWrapper.toXml());

      this.glob.getXmlBlasterAccess().publish(msgUnit);
      log.info("Success: Publishing of " + oid + " done");
   }

   /**
    * Tests the activation flag setting and getting, i.e. disactivating/activating of the
    * dispatcher.
    */
   public void testActivationFlag() {
      try {
         String oid = "TestActivationFlag";
         log.info("Going to publish 3 times on message '" + oid + "' (first time before subscribing)");
         doPublish(1, oid);
         doSubscribe(oid);
         doPublish(2, oid);
         doPublish(3, oid);
         assertEquals("wrong number of updates received", 3, this.updateInterceptor.waitOnUpdate(500L));
         this.updateInterceptor.clear();

         String getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive=one,two,three";
         MsgUnit[] msg = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), new GetQos(this.glob));
         assertEquals("wrong number of messages returned", 1, msg.length);
         for (int i=0; i < msg.length; i++) {
            log.info("testActivationFlag: dispatcherActive: (" + i + ") : '" + msg[i].getContentStr() + "'");
            assertEquals("wrong return value", "true", msg[i].getContentStr());
         }
      
         getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive=false";
         doPublish(-1, getOid);

         getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive";
         msg = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), new GetQos(this.glob));
         assertEquals("wrong number of messages returned", 1, msg.length);
         for (int i=0; i < msg.length; i++) {
            log.info("testActivationFlag: dispatcherActive (result): (" + i + ") : '" + msg[i].getContentStr() + "'");
            assertEquals("wrong return value", "false", msg[i].getContentStr());
         }

         doPublish(4, oid);
         doPublish(5, oid);
         int numArrived = this.updateInterceptor.waitOnUpdate(2000L);
         assertEquals("wrong number of messages arrived", 0, numArrived);
                  
         getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive=true";
         doPublish(-1, getOid);

         getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive";
         msg = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), new GetQos(this.glob));
         assertEquals("wrong number of messages returned", 1, msg.length);
         for (int i=0; i < msg.length; i++) {
            log.info("testActivationFlag: dispatcherActive (result): (" + i + ") : '" + msg[i].getContentStr() + "'");
            assertEquals("wrong return value", "true", msg[i].getContentStr());
         }
         
         numArrived = this.updateInterceptor.waitOnUpdate(2000L);
         assertEquals("wrong number of messages arrived", 2, numArrived);
         
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("exception should not occur here", false);
      }
   }

   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
      String contentStr = new String(content);
      String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
      log.info("Receiving update of a message oid=" + updateKey.getOid() +
                        " priority=" + updateQos.getPriority() +
                        " state=" + updateQos.getState() +
                        " content=" + cont);
      log.info("further log for receiving update of a message cbSessionId=" + cbSessionId +
                     updateKey.toXml() + "\n" + new String(content) + updateQos.toXml());
      log.severe("update: should never be invoked (msgInterceptors take care of it since they are passed on subscriptions)");
      return "OK";
   }


   /**
    * Testing the getting of queue entries without removing them from the queue.
    * TEST: <br />
    */
   public void testGetQueueEntries() {
      try {
         String oid = "TestGetQueueEntries";
         doSubscribe(oid);

         String getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive=false";
         doPublish(-1, getOid);

         doPublish(1, oid);
         doPublish(2, oid);
         doPublish(3, oid);
         log.info("Going to publish 3 times on message '" + oid + "'");
         // should not receive anything yet since the dispatcher is not active anymore 
         assertEquals("wrong number of updates received", 0, this.updateInterceptor.waitOnUpdate(500L));
         this.updateInterceptor.clear();

         // query with a given GetQos ...         
         GetQos getQos = new GetQos(this.glob);
         // HistoryQos historyQos = new HistoryQos(this.glob);
         // historyQos.setNumEntries(3);
         // getQos.setHistoryQos(historyQos);
         QuerySpecQos querySpecQos = new QuerySpecQos(this.glob, "QueueQuery", "1.0", "maxEntries=3&maxSize=-1&consumable=false&waitingDelay=0");
         getQos.addQuerySpec(querySpecQos);

         getOid = "__cmd:client/" + this.sessionName + "/?cbQueueEntries";
         MsgUnit[] mu = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), getQos);
         assertEquals("wrong number of retreived entries", 3, mu.length);
         
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("exception should not occur here", false);
      }
   }

   private void doActivateDispatch(boolean doDispatch) throws XmlBlasterException {
      // inhibit delivery of subscribed messages ...
      String getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive=" + doDispatch;
      doPublish(-1, getOid);
      // query with a given GetQos ...         
      getOid = "__cmd:client/" + this.sessionName + "/?dispatcherActive";
      MsgUnit[] msg = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), new GetQos(this.glob));
      assertEquals("wrong number of messages returned", 1, msg.length);
      assertEquals("wrong return value", "" + doDispatch, msg[0].getContentStr());
   } 


   /**
    * Testing the getting of queue entries. Note that before this method is called, the queue must be empty
    * TEST: <br />
    */
   private void adminGet(String oid, boolean consumable, long waitingDelay, int maxEntries, int initialEntries, int endEntries, int entriesExpected) {
      try {
         int sizePerMsg = 0;
         doActivateDispatch(false);
         assertEquals("wrong prerequisite: entries have arrived before starting the test: probably coming from an inconsistency in the previous test", 0, this.updateInterceptor.count());
         this.updateInterceptor.clear();
         for (int i=0; i < initialEntries; i++) doPublish(i, oid);
         log.info("In the callback queue there should now be '" + initialEntries + "' entries");
         int ret = this.updateInterceptor.waitOnUpdate(200L);
         assertEquals("no update should arrive here ", 0, ret);
         
         // prepare the messages to be published
         // wait a third of the total waiting time before publishing in a separate thread (while we wait for updates)
         
         int extraEntries = endEntries - initialEntries;
         MsgUnit[] msgs = new MsgUnit[extraEntries];
         for (int i=0; i < extraEntries; i++) {
            String content = "extraMsg" + i;
            msgs[i] = new MsgUnit(new PublishKey(this.glob, oid), content, new PublishQos(this.glob));
         }
         long delay = waitingDelay / 3 + 10L;
         PublisherThread pubThread = new PublisherThread(this.glob, delay, msgs);

         // query with a given GetQos ...         
         GetQos getQos = new GetQos(this.glob);
         QuerySpecQos querySpecQos = new QuerySpecQos(this.glob, "QueueQuery", "1.0", "maxEntries=" + maxEntries + "&maxSize=-1&consumable=" + consumable + "&waitingDelay=" + waitingDelay);
         getQos.addQuerySpec(querySpecQos);
         String getOid = "__cmd:client/" + this.sessionName + "/?cbQueueEntries";
         MsgUnit[] mu = this.glob.getXmlBlasterAccess().get(new GetKey(this.glob, getOid), getQos);
         assertEquals("an exception occured when it should not", false, pubThread.hasException());
         assertEquals("wrong number of retreived entries", entriesExpected, mu.length);

         assertEquals("messages should not arrive here", 0, this.updateInterceptor.count());
         doActivateDispatch(true);
         if (consumable) {
            int rest = endEntries-mu.length;
            int arrived = 0;
            if (rest < 1) {
               arrived = this.updateInterceptor.waitOnUpdate(500L, 1);
            }
            else arrived = this.updateInterceptor.waitOnUpdate(500L, rest);
            assertEquals("wrong number of messages arrived (some should have been consumed by the get", rest, arrived);
         }
         else {
            int arrived = this.updateInterceptor.waitOnUpdate(200L, endEntries);
            assertEquals("all published messages should arrive here", endEntries, arrived);
         }
         this.updateInterceptor.clear();
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("exception should not occur here", false);
      }
   }

   public void testGetNonConsumableNoWaiting() {
      String oid = "NonConsumableNoWaiting";
      doSubscribe(oid);
      boolean consumable = false;
      long waiting = 0L; // no waiting
      int maxEntries = 3;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, 0);
      adminGet(oid, consumable, waiting, maxEntries, 2, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 0, 2, 0);
      doUnSubscribe(oid);
   }

   public void testGetConsumableNoWaiting() {
      String oid = "ConsumableNoWaiting";
      doSubscribe(oid);
      boolean consumable = true;
      long waiting = 0L; // no waiting
      int maxEntries = 3;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, 0);
      adminGet(oid, consumable, waiting, maxEntries, 2, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 0, 2, 0);
      doUnSubscribe(oid);
   }

   public void testGetNonConsumableDoWaiting() {
      String oid = "NonConsumableDoWaiting";
      doSubscribe(oid);
      boolean consumable = false;
      long waiting = 200L; // no waiting
      int maxEntries = 3;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 2, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 0, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 1, maxEntries, maxEntries);
      waiting = -1L;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 1, maxEntries, maxEntries);
      doUnSubscribe(oid);
   }

   public void testGetConsumableDoWaiting() {
      String oid = "ConsumableDoWaiting";
      doSubscribe(oid);
      boolean consumable = false;
      long waiting = 200L; // no waiting
      int maxEntries = 3;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 2, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 0, 2, 2);
      adminGet(oid, consumable, waiting, maxEntries, 1, maxEntries, maxEntries);
      waiting = -1L;
      adminGet(oid, consumable, waiting, maxEntries, 4, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 0, 4, maxEntries);
      adminGet(oid, consumable, waiting, maxEntries, 1, maxEntries, maxEntries);
      doUnSubscribe(oid);
   }

   
   /**
    * Invoke: java org.xmlBlaster.test.client.TestAdminGet
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestAdminGet</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestAdminGet testSub = new TestAdminGet(glob, "TestAdminGet/1");

      testSub.setUp();
      testSub.testActivationFlag();
      testSub.tearDown();

      testSub.setUp();
      testSub.testGetQueueEntries();
      testSub.tearDown();

      testSub.setUp();
      testSub.testGetNonConsumableNoWaiting();
      testSub.tearDown();

      testSub.setUp();
      testSub.testGetNonConsumableDoWaiting();
      testSub.tearDown();

      testSub.setUp();
      testSub.testGetConsumableNoWaiting();
      testSub.tearDown();

      testSub.setUp();
      testSub.testGetConsumableDoWaiting();
      testSub.tearDown();

   }
}

