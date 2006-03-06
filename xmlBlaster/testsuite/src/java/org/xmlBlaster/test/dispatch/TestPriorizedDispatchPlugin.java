/*------------------------------------------------------------------------------
Name:      TestPriorizedDispatchPlugin.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.dispatch;

import java.util.logging.Logger;
import java.util.logging.Level;
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
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client tests the
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">dispatch.control.plugin requirement</a>
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.dispatch.TestPriorizedDispatchPlugin
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.TestPriorizedDispatchPlugin
 * </pre>
 * @see org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
 */
public class TestPriorizedDispatchPlugin extends TestCase
{
   private static String ME = "TestPriorizedDispatchPlugin";
   private Global glob;
   private static Logger log = Logger.getLogger(TestPriorizedDispatchPlugin.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9560;
   private boolean startEmbedded = true;
   private MsgInterceptor update; // collects updated messages

   private final String msgOid = "dispatchTestMessage";

   private int msgSequenceNumber = 0;

   private String statusOid = "_bandwidth.status";
   private String NORMAL_LINE = "2M";
   private String BACKUP_LINE = "64k";
   private String DEAD_LINE = "DOWN";

   private String[] states = { NORMAL_LINE, BACKUP_LINE, DEAD_LINE };
   private String[][] expectedActions = { 
      {"send", "send", "send", "send", "send", "send", "send", "send", "send", "send"},
      {"destroy", "destroy", "destroy", "destroy", "queue", "queue", "queue,notifySender", "send", "send", "send"},
      {"destroy", "destroy", "destroy", "destroy", "queue", "queue", "queue", "queue", "queue", "queue"}
    };

   /**
    * Constructs the TestPriorizedDispatchPlugin object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestPriorizedDispatchPlugin(Global glob, String testName, String name) {
      super(testName);
      this.glob = glob;

      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load our demo dispatch plugin.
    * <p />
    * Then we connect as a client
    */
   protected void setUp() {
      //Global embeddedGlobal = glob.getClone(null);  
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = {
        "-DispatchPlugin[Priority][1.0]", "org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin",
        "-DispatchPlugin/defaultPlugin", "undef", 
        "-PriorizedDispatchPlugin/user", "_PriorizedDispatchPlugin",
        "-"+org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin.CONFIG_PROPERTY_KEY, //"-PriorizedDispatchPlugin/config", 
            "<msgDispatch defaultStatus='" + BACKUP_LINE + "' defaultAction='send'>\n"+
            "  <onStatus oid='" + statusOid + "' content='" + NORMAL_LINE + "' defaultAction='send'>\n" +
            //"    <action do='send'  ifPriority='0-9'/>\n" +
            "  </onStatus>\n" +
            "  <onStatus oid='" + statusOid + "' content='" + BACKUP_LINE + "' defaultAction='send'>\n" +
            "     <action do='send'  ifPriority='7'/>\n" +
            "     <action do='queue,notifySender'  ifPriority='6'/>\n" +
            "     <action do='queue'  ifPriority='4-5'/>\n" +
            "     <action do='destroy'  ifPriority='0-3'/>\n" +
            "  </onStatus>\n" +
            "  <onStatus oid='" + statusOid + "' content='" + DEAD_LINE + "' defaultAction='queue'>\n" +
            "    <action do='destroy'  ifPriority='0-3'/>\n" +
            "  </onStatus>\n" +
            "</msgDispatch>\n"
         };
      glob.init(args);

      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing the priority dispatch plugin");
      }

      try {
         log.info("Connecting ...");
         this.con = glob.getXmlBlasterAccess();

         // Activate plugin for callback only:
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         CallbackAddress cbAddress = new CallbackAddress(glob);
         cbAddress.setDispatchPlugin("Priority,1.0");
         qos.addCallbackAddress(cbAddress);

         this.update = new MsgInterceptor(glob, log, null);
         this.con.connect(qos, update);
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      this.update.clear();
   }

   /**
    * @param The oid of the status message 
    * @param state Choose one of "2M" or "64k"
    */
   private void changeStatus(String oid, String state) {
      log.info("Changing band width state to '" + state + "'");
      try {
         PublishReturnQos rq = con.publish(new MsgUnit(glob, "<key oid='" + oid + "'/>", state, null));
         log.info("SUCCESS for state change to '" + state + "', " + rq.getState());
         // Sleep to be shure the plugin has got and processed the message
         try { Thread.sleep(1000L); } catch( InterruptedException i) {}
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("publish bandwidth state - XmlBlasterException: " + e.getMessage());
      }
   }

   private void publish(String oid, int priority) {
      PriorityEnum prio = PriorityEnum.toPriorityEnum(priority);
      try {
         msgSequenceNumber++;
         String content = "" + msgSequenceNumber;
         PublishQos pq = new PublishQos(glob);
         pq.setPriority(prio);
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='"+oid+"'/>", content.getBytes(), pq.toXml()));
         log.info("SUCCESS publish '" + oid + "' with prio=" + prio.toString() + " content=" + content + " returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("publish prio=" + prio.toString() + " - XmlBlasterException: " + e.getMessage());
      }
   }

   private void subscribe(String oid) {
      try {
         SubscribeKey sk = new SubscribeKey(glob, oid);
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos srq = con.subscribe(sk.toXml(), sq.toXml());
         log.info("SUCCESS subscribe to '" + oid + "' returned state=" + srq.getState());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Test all tuples of possibilities
    */
   public void testPriorizedDispatchPlugin() {
      log.info("testPriorizedDispatchPlugin() ...");
      long sleep = 1000L;
      String text;

      subscribe(msgOid);

      int queueCounter = 0;
      int destroyCounter = 0;

      try {
         for (int i=0; i<states.length; i++) {
            changeStatus(statusOid, states[i]);
            log.info("========================state=" + states[i]);
            for (int priority=0; priority<expectedActions[i].length; priority++) {
               String action = expectedActions[i][priority];
               text = "state=" + states[i] + " action=" + action;
               log.info("Doing " + text + " queueCounter=" + queueCounter);

               boolean expectsNotify = false;
               if (action.indexOf("notifySender") >= 0) {
                  expectsNotify = true;
                  log.info(text + ": Expecting notify");
               }

               if (action.startsWith("send")) {
                  publish(msgOid, priority);
                  assertEquals(text, 1, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
                  int count = expectsNotify ? 2 : 1;
                  assertEquals(text, count, this.update.count());
                  if (expectsNotify) {
                     String expectedState = "send,notifySender";
                     Msg msg = this.update.getMsg(msgOid, expectedState); // PtP notification
                     assertTrue("send,notifySender PtP not arrived", msg != null);
                  }
               }
               else if (action.startsWith("queue")) {
                  publish(msgOid, priority);
                  queueCounter++;
                  assertEquals(text, 0, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
                  int count = expectsNotify ? 1 : 0;
                  assertEquals(text, count, this.update.count());
                  if (expectsNotify) {
                     assertEquals(text, "_PriorizedDispatchPlugin", this.update.getMsgs()[0].getUpdateQos().getSender().getLoginName()); // PtP notification
                  }
               }
               else if (action.startsWith("destroy")) {
                  publish(msgOid, priority);
                  destroyCounter++;
                  assertEquals(text, 0, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
                  int count = expectsNotify ? 1 : 0;
                  assertEquals(text, count, this.update.count());
                  if (expectsNotify) {
                     assertEquals(text, "_PriorizedDispatchPlugin", this.update.getMsgs()[0].getUpdateQos().getSender().getLoginName()); // PtP notification
                  }
               }
               else {
                  log.severe(text + ": Action is not supported");
                  fail(text + ": Action is not supported");
               }

               this.update.clear();
            } // for prio
         } // for states

         text = "Checking ascending sequence of flushed " + queueCounter + " messages which where hold back";
         this.update.clear();
         changeStatus(statusOid, NORMAL_LINE);
         assertEquals(text, queueCounter, this.update.waitOnUpdate(2000L, msgOid, Constants.STATE_OK));
         assertEquals(text, queueCounter, this.update.count());
         Msg[] msgArr = this.update.getMsgs();
         assertEquals(text, queueCounter, msgArr.length);
         int lastNum = -1;
         int lastPrio = PriorityEnum.MAX_PRIORITY.getInt() + 1;
         for (int i=0; i<msgArr.length; i++) {
            log.info("Received flushed hold back message " + msgArr[i].getUpdateKey().getOid() + 
                         " priority=" + msgArr[i].getUpdateQos().getPriority() +
                         " content=" + msgArr[i].getContentStr() +
                         " state=" + msgArr[i].getUpdateQos().getState());
         }
         for (int i=0; i<msgArr.length; i++) {
            int currPrio = msgArr[i].getUpdateQos().getPriority().getInt();
            int currNum = msgArr[i].getContentInt();
            if (lastPrio < currPrio || lastPrio == currPrio && lastNum >= currNum)
               fail(text + " Sequence is not ascending: last=" + lastNum + " curr=" + currNum);
            lastNum = currNum;
            lastPrio = currPrio;
         }
         this.update.clear();
      }
      catch (XmlBlasterException e) {
         fail(e.toString());
      }
      log.info("Success in testPriorizedDispatchPlugin()");
   }

   /**
    * Tests to change the plugin configuration and different status message oids. 
    */
   public void testPriorizedDispatchPluginReconfigure() {
      log.info("testPriorizedDispatchPluginReconfigure() ...");
      String statusOid2 = statusOid+"-2";
      String config = 
            "<msgDispatch defaultStatus='GO' defaultAction='send'>\n"+
            "  <onStatus oid='" + statusOid + "' content='GO' defaultAction='send'>\n" +
            "    <action do='send'  ifPriority='0-9'/>\n" +
            "  </onStatus>\n" +
            "  <onStatus oid='" + statusOid2 + "' content='" + BACKUP_LINE + "' defaultAction='send'>\n" +
            "     <action do='queue'  ifPriority='0-9'/>\n" +
            "  </onStatus>\n" +
            "</msgDispatch>\n";

      publishNewConfig(config);

      String text = "Testing configuration";

      long sleep = 2000L;

      //try {
         subscribe(msgOid);

         int maxPrio = PriorityEnum.MAX_PRIORITY.getInt() + 1;

         // check normal operation
         changeStatus(statusOid, "GO");
         for (int priority=0; priority < maxPrio; priority++) {
            publish(msgOid, priority);
         }
         assertEquals(text, maxPrio, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
         log.info("SUCCESS, state=GO");
         this.update.clear();

         // queue messages
         changeStatus(statusOid2, BACKUP_LINE);
         for (int priority=0; priority < maxPrio; priority++) {
            publish(msgOid, priority);
         }
         assertEquals(text, 0, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
         log.info("SUCCESS, state=" + BACKUP_LINE);
         this.update.clear();

         // flush the before queued messages
         changeStatus(statusOid, "GO");
         assertEquals(text, maxPrio, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
         log.info("SUCCESS, state=GO");
         this.update.clear();

         // check unkown message content
         changeStatus(statusOid, "??YYXX");
         for (int priority=0; priority < maxPrio; priority++) {
            publish(msgOid, priority);
         }
         assertEquals(text, maxPrio, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
         log.info("SUCCESS, state=GO");
         this.update.clear();
         /*
      }
      catch (XmlBlasterException e) {
         fail(e.toString());
      }    */
      log.info("Success in testPriorizedDispatchPluginReconfigure()");
   }

   /**
    * Change the configuration of the plugin
    */
   private void publishNewConfig(String config) {
      String configKey = org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin.CONFIG_PROPERTY_KEY; // "PriorizedDispatchPlugin/config"
      try {
         String oid = "__cmd:sysprop/?" + configKey;
         String contentStr = config;
         PublishQos pq = new PublishQos(glob);
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='"+oid+"'/>", contentStr.getBytes(), pq.toXml()));
         log.info("SUCCESS publish new configuration '" + oid + "' returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.toString());
         fail("publish of configuration data - XmlBlasterException: " + e.getMessage());
      }

      /* Does not work as Main.java creates a new engine.Global for the server
      try {
         glob.getProperty().set(configKey, config);
      }
      catch (org.jutils.JUtilsException e) {
         fail(e.toString());
      }
      */
   }

   /**
    * Test the notifySender message
    * 1. subscribe to a message
    * 2. change state to 64k
    * 3. send a message with prio 6 which should trigger a notify PtP message
    */
   public void testPriorizedDispatchPluginOne() {
      log.info("testPriorizedDispatchPluginOne() ...");

      long sleep = 2000L;
      String text = "state=" + BACKUP_LINE + " action=queue,notifySender";

      // <action do='queue,notifySender'  ifPriority='6'/>

      subscribe(msgOid);

      changeStatus(statusOid, BACKUP_LINE);
      try { Thread.sleep(1000L); } catch( InterruptedException i) {} // Wait some time

      int priority = 6;
      log.info(text + ": Expecting notify");

      this.update.clear();
      publish(msgOid, priority);
      assertEquals(text, 0, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
      assertEquals(text, 1, this.update.count());
      assertEquals(text, "_PriorizedDispatchPlugin", this.update.getMsgs()[0].getUpdateQos().getSender().getLoginName()); // PtP notification

      this.update.clear();

      changeStatus(statusOid, NORMAL_LINE);
      log.info(text + ": Expecting queued message");
      assertEquals(text, 1, this.update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));

      log.info("Success in testPriorizedDispatchPluginOne()");
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try { Thread.sleep(200L); } catch( InterruptedException i) {} // Wait some time

      this.con.disconnect(null);
      this.con = null;

      if (this.startEmbedded) {
         try { Thread.sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
     
      this.con = null;
      this.update = null;
      Global.instance().shutdown();
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "PriorizedDispatchPlugin";
       suite.addTest(new TestPriorizedDispatchPlugin(Global.instance(), "testPriorizedDispatchPluginOne", "PriorizedDispatchPluginOne"));
       suite.addTest(new TestPriorizedDispatchPlugin(Global.instance(), "testPriorizedDispatchPlugin", "PriorizedDispatchPlugin"));
       suite.addTest(new TestPriorizedDispatchPlugin(Global.instance(), "testPriorizedDispatchPluginReconfigure", "PriorizedDispatchPluginRecovery"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *  java org.xmlBlaster.test.dispatch.TestPriorizedDispatchPlugin -trace[dispatch] true -call[core] true
    *  java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.dispatch.TestPriorizedDispatchPlugin
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestPriorizedDispatchPlugin testSub = new TestPriorizedDispatchPlugin(glob, "TestPriorizedDispatchPlugin", "TestPriorizedDispatchPlugin");
      testSub.setUp();
      testSub.testPriorizedDispatchPlugin();
      //testSub.testPriorizedDispatchPluginReconfigure();
      //testSub.testPriorizedDispatchPluginOne();
      testSub.tearDown();
   }
}

