/*------------------------------------------------------------------------------
Name:      TestPriorizedDeliveryWithLostCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.dispatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.I_CallbackExtended;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
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
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcCallbackServer;

import junit.framework.*;


/**
 * This client tests the
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/delivery.control.plugin.html">delivery.control.plugin requirement</a>
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * <p>
 * This tests runs only based on XmlRpc, as with xmlrpc we can easily start/stop the callback server
 * independent from our connection
 * </p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.dispatch.TestPriorizedDeliveryWithLostCallback
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.TestPriorizedDeliveryWithLostCallback
 * </pre>
 * @see org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin
 */
public class TestPriorizedDeliveryWithLostCallback extends TestCase
{
   private static String ME = "TestPriorizedDeliveryWithLostCallback";
   private Global glob;
   private LogChannel log;

   private ConnectQos connectQos;
   private ConnectReturnQos connectReturnQos;
   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9660;
   private MsgInterceptor updateInterceptor;
   private MsgInterceptor updateMsgs; // just used as message container, class scope to be usable in inner update class

   private final String msgOid = "dispatchTestMessage";

   private int msgSequenceNumber = 0;

   private String statusOid = "_bandwidth.status";
   private String NORMAL_LINE = "2M";
   private String BACKUP_LINE = "64k";
   private String DEAD_LINE = "DOWN";

   private boolean connected;

   /**
    * Constructs the TestPriorizedDeliveryWithLostCallback object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestPriorizedDeliveryWithLostCallback(Global glob, String testName, String name) {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog("test");
      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load our demo dispatch plugin.
    * <p />
    * Then we connect as an XmlRpc client in fail save mode.
    * We need to shutdown and restart the callback server and this is buggy with CORBA.
    */
   protected void setUp() {  
      glob.init(Util.getOtherServerPorts(serverPort));
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = {
        "-ProtocolPlugin[XMLRPC][1.0]", "org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver",
        "-CbProtocolPlugin[XMLRPC][1.0]", "org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver",
        "-dispatch/clientSide/protocol", "XMLRPC",
        "-dispatch/callback/protocol", "XMLRPC",
        "-protocol/xmlrpc/portxmlrpc.port", ""+(serverPort+1),
        "-dispatch/callback/protocol/xmlrpc/port", ""+(serverPort+1),
        "-DispatchPlugin[Priority][1.0]", "org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin",
        "-DispatchPlugin/defaultPlugin", "undef",  // configure "Priority,1.0" below with CallbackAddress
        "-PriorizedDeliveryPlugin.user", "_PriorizedDeliveryPlugin",
        "-"+org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin.CONFIG_PROPERTY_KEY, "<msgDispatch defaultStatus='" + BACKUP_LINE + "' defaultAction='send'/>\n"
        // "PriorizedDeliveryPlugin/config"
         };
      glob.init(args);

      this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing the priority dispatch plugin");

      try {
         // A testsuite helper to collect update messages
         this.updateInterceptor = new MsgInterceptor(glob, log, null);

         // Connecting to server
         log.info(ME, "Connecting with XmlRpc ...");
         this.con = glob.getXmlBlasterAccess();
         this.connectQos = new ConnectQos(glob, name, passwd);

         CallbackAddress cbAddress = new CallbackAddress(glob);
         cbAddress.setDelay(1000L);      // retry connecting every 4 sec
         cbAddress.setRetries(-1);       // -1 == forever
         cbAddress.setPingInterval(5000L); // ping every 4 seconds
         cbAddress.setDispatchPlugin("Priority,1.0");  // Activate plugin for callback only
         this.connectQos.addCallbackAddress(cbAddress);

         this.connectReturnQos = this.con.connect(this.connectQos, this.updateInterceptor);
      }
      catch (Exception e) {
         e.printStackTrace();
         log.error(ME, "Can't connect to xmlBlaster: " + e.getMessage());
      }

      this.updateInterceptor.clear();
   }

   /**
    * @param The oid of the status message 
    * @param state Choose one of "2M" or "64k"
    */
   private void changeStatus(String oid, String state) {
      log.info(ME, "Changing band width state to '" + state + "'");
      try {
         PublishReturnQos rq = con.publish(new MsgUnit(glob, "<key oid='" + oid + "'/>", state, null));
         log.info(ME, "SUCCESS for state change to '" + state + "', " + rq.getState());
         // Sleep to be shure the plugin has got and processed the message
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
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
         log.info(ME, "SUCCESS publish '" + oid + "' with prio=" + prio.toString() + " returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("publish prio=" + prio.toString() + " - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Change the configuration of the plugin
    */
   private void publishNewConfig(String config) {
      String configKey = org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDeliveryPlugin.CONFIG_PROPERTY_KEY; // -PriorizedDeliveryPlugin/config=
      try {
         String oid = "__cmd:sysprop/?" + configKey;
         String contentStr = config;
         PublishQos pq = new PublishQos(glob);
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='"+oid+"'/>", contentStr.getBytes(), pq.toXml()));
         log.info(ME, "SUCCESS publish '" + oid + "' " + pq.toXml() + ", returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         e.printStackTrace();
         fail("publish of configuration data - XmlBlasterException: " + e.getMessage());
      }

      /* Does not work as Main.java creates a new engine.Global for the server
      try {
         glob.getProperty().set(PriorizedDeliveryPlugin.CONFIG_PROPERTY_KEY, config);
      }
      catch (org.jutils.JUtilsException e) {
         fail(e.toString());
      }
      */
   }

   private void subscribe(String oid) {
      try {
         SubscribeKey sk = new SubscribeKey(glob, oid);
         SubscribeQos sq = new SubscribeQos(glob);
         SubscribeReturnQos srq = con.subscribe(sk.toXml(), sq.toXml());
         log.info(ME, "SUCCESS subscribe to '" + oid + "' returned state=" + srq.getState());
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Tests what happens if dispatcher frameworks looses the callback connection to us
    * and starts polling
    */
   public void testPriorizedDeliveryPluginConnectionState() {
      log.info(ME, "testPriorizedDeliveryPluginConnectionState() ...");
      String config = 
            "<msgDispatch defaultStatus='" + NORMAL_LINE + "' defaultAction='send'>\n"+
            "  <onStatus oid='" + statusOid + "' content='" + NORMAL_LINE + "' defaultAction='send'>\n" +
            "    <action do='send'  ifPriority='0-9'/>\n" +
            "  </onStatus>\n" +
            "  <onStatus oid='" + statusOid + "' content='" + DEAD_LINE + "' defaultAction='queue' connectionState='polling'>\n" +
            "    <action do='queue'  ifPriority='4-9'/>\n" +
            "    <action do='destroy'  ifPriority='0-3'/>\n" +
            "  </onStatus>\n" +
            "</msgDispatch>\n";

      publishNewConfig(config);


      String text = "Testing configuration";

      long sleep = 2000L;

      subscribe(msgOid);

      int maxPrio = PriorityEnum.MAX_PRIORITY.getInt() + 1;

      // First check normal operation
      //changeStatus(statusOid, NORMAL_LINE);
      publish(msgOid, 1);
      assertEquals(text, 1, this.updateInterceptor.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
      this.updateInterceptor.clear();

      // Now kill our callback server
      log.info(ME, "Shutdown callback, expecting messages to be queued or destroyed depending on the priority");
      try {
         con.getCbServer().shutdown();
      }
      catch (XmlBlasterException e) {
         fail("ShutdownCB: " + e.getMessage());
      }
      this.updateInterceptor.clear();

      // These messages are depending on the priority queued or destroyed
      // as the callback connection is polling ...
      for (int priority=0; priority < maxPrio; priority++) {
         publish(msgOid, priority);
      }
      assertEquals(text, 0, this.updateInterceptor.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
      this.updateInterceptor.clear();


      // Now reestablish the callback server ...
      I_CallbackServer cbServer = null;
      try {
         updateMsgs = new MsgInterceptor(glob, log, null); // just used as message container
         this.updateInterceptor.clear();
         try {
            cbServer = new XmlRpcCallbackServer();
            CallbackAddress cbAddress = new CallbackAddress(glob);
            cbServer.initialize(this.glob, name, cbAddress, new AbstractCallbackExtended(glob) {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
                  try {
                     String contentStr = new String(content);
                     String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
                     this.log.info(ME, "Receiving update of a message oid=" + updateKey.getOid() +
                         " priority=" + updateQos.getPriority() +
                         " state=" + updateQos.getState() +
                         " content=" + cont);
                     if (!updateQos.isErased()) {
                        updateMsgs.add(new Msg(cbSessionId, updateKey, content, updateQos));
                     }
                  }
                  catch (Throwable e) {
                     this.log.error(ME, "Error in update method: " + e.toString());
                     e.printStackTrace();
                  }
                  return "";
               }
               public I_ClientPlugin getSecurityPlugin() { return null; }
            }); // Establish new callback server
         }
         catch (Throwable e) {
            log.error(ME, "Can't restart callback server: " + e.toString());
            fail("Can't restart callback server: " + e.toString());
         }

         log.info(ME, "Waiting long enough that xmlBlaster reconnected to us and expecting the 6 queued messages ...");
         try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}
         assertEquals(text, 0, this.updateInterceptor.getMsgs().length);
         assertEquals(text, 6, updateMsgs.getMsgs(msgOid, Constants.STATE_OK).length);
         Msg[] msgArr = updateMsgs.getMsgs();
         assertEquals(text, 6, msgArr.length);
         int lastNum = -1;
         int lastPrio = PriorityEnum.MAX_PRIORITY.getInt() + 1;
         for (int i=0; i<msgArr.length; i++) {
            int currPrio = msgArr[i].getUpdateQos().getPriority().getInt();
            int currNum = msgArr[i].getContentInt();
            if (lastPrio < currPrio || lastPrio == currPrio && lastNum >= currNum)
               fail(text + " Sequence is not ascending: last=" + lastNum + " curr=" + currNum);
            lastNum = currNum;
            lastPrio = currPrio;
         }
         assertEquals("", PriorityEnum.MAX_PRIORITY, msgArr[0].getUpdateQos().getPriority());
         assertEquals("", 4, msgArr[5].getUpdateQos().getPriority().getInt());
         updateMsgs.clear();
         this.updateInterceptor.clear();
      }
      finally {
         if (cbServer != null) {
            try { cbServer.shutdown(); } catch (Exception e) { log.error(ME, e.toString()); };
         }
      }

      log.info(ME, "Success in testPriorizedDeliveryPluginConnectionState()");
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {} // Wait some time

      con.disconnect(null);
      con = null;
      
      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {} // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
      this.log = null;
      this.connectQos = null;
      this.connectReturnQos = null;
      this.con = null;
      this.updateInterceptor = null;
      this.updateMsgs = null;
      Global.instance().shutdown();
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "PriorizedDeliveryPlugin";
       suite.addTest(new TestPriorizedDeliveryWithLostCallback(Global.instance(), "testPriorizedDeliveryPluginConnectionState", "PriorizedDeliveryPluginRecovery"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *  java org.xmlBlaster.test.dispatch.TestPriorizedDeliveryWithLostCallback -trace[dispatch] true -call[core] true
    *  java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.dispatch.TestPriorizedDeliveryWithLostCallback
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestPriorizedDeliveryWithLostCallback testSub = new TestPriorizedDeliveryWithLostCallback(glob, "TestPriorizedDeliveryWithLostCallback", "TestPriorizedDeliveryWithLostCallback");
      testSub.setUp();
      testSub.testPriorizedDeliveryPluginConnectionState();
      testSub.tearDown();
   }
}

