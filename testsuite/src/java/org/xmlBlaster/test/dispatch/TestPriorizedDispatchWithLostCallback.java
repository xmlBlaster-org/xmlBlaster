/*------------------------------------------------------------------------------
Name:      TestPriorizedDispatchWithLostCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.dispatch;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.def.PriorityEnum;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.client.protocol.xmlrpc.XmlRpcCallbackServer;

import junit.framework.*;


/**
 * This client tests the
 * <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/dispatch.control.plugin.html">dispatch.control.plugin requirement</a>
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
 *    java junit.textui.TestRunner org.xmlBlaster.test.dispatch.TestPriorizedDispatchWithLostCallback
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.dispatch.TestPriorizedDispatchWithLostCallback
 * </pre>
 * @see org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin
 */
public class TestPriorizedDispatchWithLostCallback extends TestCase
{
   private Global glob;
   private static Logger log = Logger.getLogger(TestPriorizedDispatchWithLostCallback.class.getName());

   private ConnectQos connectQos;
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

   /**
    * Constructs the TestPriorizedDispatchWithLostCallback object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestPriorizedDispatchWithLostCallback(Global glob, String testName, String name) {
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
    * Then we connect as an XmlRpc client in fail save mode.
    * We need to shutdown and restart the callback server and this is buggy with CORBA.
    */
   protected void setUp() {  
      glob.init(Util.getOtherServerPorts(serverPort));
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = {
        "-ProtocolPlugin[XMLRPC][1.0]", "org.xmlBlaster.protocol.xmlrpc.XmlRpcDriver",
        "-CbProtocolPlugin[XMLRPC][1.0]", "org.xmlBlaster.protocol.xmlrpc.CallbackXmlRpcDriver",
        "-dispatch/connection/protocol", "XMLRPC",
        "-dispatch/callback/protocol", "XMLRPC",
        "-plugin/xmlrpc/port", ""+(serverPort+1),
        "-dispatch/callback/plugin/xmlrpc/port", ""+(serverPort+1),
        "-DispatchPlugin[Priority][1.0]", "org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin",
        "-DispatchPlugin/defaultPlugin", "undef",  // configure "Priority,1.0" below with CallbackAddress
        "-PriorizedDispatchPlugin.user", "_PriorizedDispatchPlugin",
        "-"+org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin.CONFIG_PROPERTY_KEY, "<msgDispatch defaultStatus='" + BACKUP_LINE + "' defaultAction='send'/>\n"
        // "PriorizedDispatchPlugin/config"
         };
      glob.init(args);

      this.serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing the priority dispatch plugin");

      try {
         // A testsuite helper to collect update messages
         this.updateInterceptor = new MsgInterceptor(glob, log, null);

         // Connecting to server
         log.info("Connecting with XmlRpc ...");
         this.con = glob.getXmlBlasterAccess();
         this.connectQos = new ConnectQos(glob, name, passwd);

         CallbackAddress cbAddress = new CallbackAddress(glob);
         cbAddress.setDelay(1000L);      // retry connecting every 4 sec
         cbAddress.setRetries(-1);       // -1 == forever
         cbAddress.setPingInterval(5000L); // ping every 4 seconds
         cbAddress.setDispatchPlugin("Priority,1.0");  // Activate plugin for callback only
         this.connectQos.addCallbackAddress(cbAddress);

         this.con.connect(this.connectQos, this.updateInterceptor);
      }
      catch (Exception e) {
         e.printStackTrace();
         log.severe("Can't connect to xmlBlaster: " + e.getMessage());
      }

      this.updateInterceptor.clear();
   }

   private void publish(String oid, int priority) {
      PriorityEnum prio = PriorityEnum.toPriorityEnum(priority);
      try {
         msgSequenceNumber++;
         String content = "" + msgSequenceNumber;
         PublishQos pq = new PublishQos(glob);
         pq.setPriority(prio);
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='"+oid+"'/>", content.getBytes(), pq.toXml()));
         log.info("SUCCESS publish '" + oid + "' with prio=" + prio.toString() + " returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("publish prio=" + prio.toString() + " - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Change the configuration of the plugin
    */
   private void publishNewConfig(String config) {
      String configKey = org.xmlBlaster.util.dispatch.plugins.prio.PriorizedDispatchPlugin.CONFIG_PROPERTY_KEY; // -PriorizedDispatchPlugin/config=
      try {
         String oid = "__cmd:sysprop/?" + configKey;
         String contentStr = config;
         PublishQos pq = new PublishQos(glob);
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='"+oid+"'/>", contentStr.getBytes(), pq.toXml()));
         log.info("SUCCESS publish '" + oid + "' " + pq.toXml() + ", returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         e.printStackTrace();
         fail("publish of configuration data - XmlBlasterException: " + e.getMessage());
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
    * Tests what happens if dispatcher frameworks looses the callback connection to us
    * and starts polling
    */
   public void testPriorizedDispatchPluginConnectionState() {
      log.info("testPriorizedDispatchPluginConnectionState() ...");
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
      log.info("Shutdown callback, expecting messages to be queued or destroyed depending on the priority");
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
            // TODO change this since it can not work when using xmlrpc with singleChannel=true
            cbServer = new XmlRpcCallbackServer();
            CallbackAddress cbAddress = new CallbackAddress(glob);
            cbServer.initialize(this.glob, name, cbAddress, new AbstractCallbackExtended(glob) {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException {
                  try {
                     String contentStr = new String(content);
                     String cont = (contentStr.length() > 10) ? (contentStr.substring(0,10)+"...") : contentStr;
                     log.info("Receiving update of a message oid=" + updateKey.getOid() +
                         " priority=" + updateQos.getPriority() +
                         " state=" + updateQos.getState() +
                         " content=" + cont);
                     if (!updateQos.isErased()) {
                        updateMsgs.add(new Msg(cbSessionId, updateKey, content, updateQos));
                     }
                  }
                  catch (Throwable e) {
                     log.severe("Error in update method: " + e.toString());
                     e.printStackTrace();
                  }
                  return "";
               }
               public I_ClientPlugin getSecurityPlugin() { return null; }
               public void lostConnection(XmlBlasterException xmlBlasterException) {}
            }); // Establish new callback server
         }
         catch (Throwable e) {
            log.severe("Can't restart callback server: " + e.toString());
            fail("Can't restart callback server: " + e.toString());
         }

         log.info("Waiting long enough that xmlBlaster reconnected to us and expecting the 6 queued messages ...");
         try { Thread.sleep(3000L); } catch( InterruptedException i) {}
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
            try { cbServer.shutdown(); } catch (Exception e) { log.severe(e.toString()); };
         }
      }

      log.info("Success in testPriorizedDispatchPluginConnectionState()");
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try { Thread.sleep(200L); } catch( InterruptedException i) {} // Wait some time

      con.disconnect(null);
      con = null;
      
      try { Thread.sleep(500L); } catch( InterruptedException i) {} // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);
      this.glob = null;
     
      this.connectQos = null;
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
       suite.addTest(new TestPriorizedDispatchWithLostCallback(Global.instance(), "testPriorizedDispatchPluginConnectionState", "PriorizedDispatchPluginRecovery"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *  java org.xmlBlaster.test.dispatch.TestPriorizedDispatchWithLostCallback  -logging/org.xmlBlaster.engine.dispatch FINE -logging/org.xmlBlaster.util.dispatch FINE -logging/org.xmlBlaster.engine FINEST
    *  java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.dispatch.TestPriorizedDispatchWithLostCallback
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestPriorizedDispatchWithLostCallback testSub = new TestPriorizedDispatchWithLostCallback(glob, "TestPriorizedDispatchWithLostCallback", "TestPriorizedDispatchWithLostCallback");
      testSub.setUp();
      testSub.testPriorizedDispatchPluginConnectionState();
      testSub.tearDown();
   }
}

