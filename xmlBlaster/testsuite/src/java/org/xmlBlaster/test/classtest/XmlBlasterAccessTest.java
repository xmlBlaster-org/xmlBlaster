package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.dispatch.DeliveryManager;
import org.xmlBlaster.util.error.I_MsgErrorHandler;
import org.xmlBlaster.util.error.MsgErrorInfo;
import org.xmlBlaster.util.queuemsg.MsgQueueEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueConnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueDisconnectEntry;
import org.xmlBlaster.client.queuemsg.MsgQueuePublishEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueUnSubscribeEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueEraseEntry;
import org.xmlBlaster.client.queuemsg.MsgQueueGetEntry;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.dispatch.I_ConnectionStatusListener;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.client.I_ConnectionHandler;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.XmlBlasterAccess;

import junit.framework.*;

/**
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.XmlBlasterAccessTest
 * @see org.xmlBlaster.util.I_XmlBlasterAccess
 */
public class XmlBlasterAccessTest extends TestCase {
   private String ME = "XmlBlasterAccessTest";
   private Global glob;
   private LogChannel log;

   public XmlBlasterAccessTest(Global glob, String name) {
      super(name);
      this.glob = glob;
   }

   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;
      this.log = this.glob.getLog("client");
   }

   public void testCreation() {
      System.out.println("***XmlBlasterAccessTest: testCreation ...");
      Global nullGlobal = null;
      I_XmlBlasterAccess xmlBlasterAccess = new XmlBlasterAccess(nullGlobal);
      assertEquals("", null, xmlBlasterAccess.getQueue());
      assertEquals("", false, xmlBlasterAccess.isAlive());
      assertEquals("", false, xmlBlasterAccess.isPolling());
      assertEquals("", false, xmlBlasterAccess.isDead());
      log.info(ME, "SUCCESS: Check I_ConnectionHandler");

      assertEquals("", false, xmlBlasterAccess.isConnected());
      xmlBlasterAccess.registerConnectionListener((I_ConnectionStateListener)null);


      assertTrue("", xmlBlasterAccess.getCbServer() == null);
      try {
         I_CallbackServer cbServer = xmlBlasterAccess.initCbServer(null, null, null);
         assertTrue("", cbServer != null);
         assertTrue("", cbServer.getCbProtocol() != null);
         assertTrue("", cbServer.getCbAddress() != null);
         log.info(ME, "SUCCESS: initCbServer protocol=" + cbServer.getCbProtocol() + " address=" + cbServer.getCbAddress());
         cbServer.shutdown();
         log.info(ME, "SUCCESS: CbServer.shutdown()");
         assertTrue("", xmlBlasterAccess.getCbServer() == null); // is not cached in xmlBlasterAccess
      }
      catch (XmlBlasterException e) {
         fail("testCreation failed: " + e.getMessage());
      }

      assertTrue("", xmlBlasterAccess.getSecurityPlugin() == null);
      assertEquals("", false, xmlBlasterAccess.disconnect(null));
      assertTrue("", xmlBlasterAccess.getConnectReturnQos() == null);
      assertTrue("", xmlBlasterAccess.getConnectQos() == null);
      assertEquals("", false, xmlBlasterAccess.disconnect(null, true, true, true));
      assertEquals("", "UNKNOWN_SESSION", xmlBlasterAccess.getId());
      assertTrue("", xmlBlasterAccess.getSessionName() == null);

      xmlBlasterAccess.setServerNodeId(null);
      assertTrue("", xmlBlasterAccess.getServerNodeId() == null);

      xmlBlasterAccess.setServerNodeId("FRISH FISH");
      assertEquals("", "FRISH FISH", xmlBlasterAccess.getServerNodeId());

      try {
         xmlBlasterAccess.subscribe((SubscribeKey)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.subscribe((String)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.subscribe((SubscribeKey)null, null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.get((String)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.get((GetKey)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.unSubscribe((String)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.unSubscribe((UnSubscribeKey)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.publish(null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }
         
      try {
         xmlBlasterAccess.publishOneway(null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }
         
      try {
         xmlBlasterAccess.publishArr(null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }
         
      try {
         xmlBlasterAccess.erase((String)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      try {
         xmlBlasterAccess.erase((EraseKey)null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testCreation failed: " + e.getMessage());
      }

      assertTrue("", xmlBlasterAccess.getGlobal() != null);
      assertTrue("", xmlBlasterAccess.toXml() != null);

      log.info(ME, "SUCCESS: Check I_XmlBlasterAccess");

      System.out.println("***XmlBlasterAccessTest: testCreation [SUCCESS]");
   }

   public void testDefaultConnectWithoutServer() {
      System.out.println("***XmlBlasterAccessTest: testDefaultConnectWithoutServer ...");
      Global nullGlobal = null;
      I_XmlBlasterAccess xmlBlasterAccess = new XmlBlasterAccess(nullGlobal);

      assertEquals("", false, xmlBlasterAccess.isConnected());
      /*
      xmlBlasterAccess.registerConnectionListener((I_ConnectionStateListener)null);
      try {
         ConnectQos q = new ConnectQos(null);
         log.info(ME, "Default ConnectQos=" + q.toXml());
         ConnectReturnQos connectReturnQos = xmlBlasterAccess.connect(null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info(ME, "Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testDefaultConnectWithoutServer failed: " + e.getMessage());
      }
      log.info(ME, "SUCCESS: Check I_XmlBlasterAccess");
      */
      log.info(ME, "TEST MISSING");

      System.out.println("***XmlBlasterAccessTest: testDefaultConnectWithoutServer [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.XmlBlasterAccessTest
    * </pre>
    */
   public static void main(String args[]) {
      XmlBlasterAccessTest testSub = new XmlBlasterAccessTest(new Global(args, true, false), "XmlBlasterAccessTest");
      testSub.setUp();
      testSub.testCreation();
      testSub.testDefaultConnectWithoutServer();
      //testSub.tearDown();
   }
}
