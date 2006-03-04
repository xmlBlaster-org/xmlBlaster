package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.queue.I_Queue;
import org.xmlBlaster.util.queue.StorageId;
import org.xmlBlaster.util.dispatch.DispatchManager;
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
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.protocol.I_XmlBlaster;
import org.xmlBlaster.client.protocol.I_CallbackServer;
import org.xmlBlaster.client.protocol.AbstractCallbackExtended;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.Address;
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
 * Test the client lib XmlBlasterAccess without a connection to a server. 
 * java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.XmlBlasterAccessTest
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 * @see org.xmlBlaster.client.XmlBlasterAccess
 */
public class XmlBlasterAccessTest extends TestCase {
   private String ME = "XmlBlasterAccessTest";
   private Global glob;
   private static Logger log = Logger.getLogger(XmlBlasterAccessTest.class.getName());

   public XmlBlasterAccessTest() {
      this(null, "XmlBlasterAccessTest");
   }

   public XmlBlasterAccessTest(Global glob, String name) {
      super(name);
      this.glob = (glob == null) ? Global.instance() : glob;
   }

   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;

   }

   /**
    * Test the three creation variants of XmlBlasterAccess and tests
    * all methods with null arguments
    */
   public void testCreation() {
      System.out.println("***XmlBlasterAccessTest: testCreation ...");
      I_XmlBlasterAccess xmlBlasterAccess = null;
      for (int i=0; i<3; i++) {
         if (i == 0) {
            Global nullGlobal = null;
            xmlBlasterAccess = new XmlBlasterAccess(nullGlobal);
         }
         else if (i == 1) {
            String[] nullArgs = null;
            xmlBlasterAccess = new XmlBlasterAccess(nullArgs);
         }
         else {
            xmlBlasterAccess = glob.getXmlBlasterAccess();
         }

         xmlBlasterAccess.setServerNodeId("/node/test");
         assertEquals("", "/node/test", xmlBlasterAccess.getServerNodeId());

         xmlBlasterAccess.setServerNodeId("FRISH FISH");
         assertEquals("", "/node/FRISH FISH", xmlBlasterAccess.getServerNodeId());

         try {
            ConnectQos connectQos = new ConnectQos(glob);
            Address address = new Address(glob);
            address.setBootstrapPort(8999); // a wrong port to avoid connection
            address.setRetries(0);       // switch off polling
            connectQos.setAddress(address);
            ConnectReturnQos connectReturnQos = xmlBlasterAccess.connect(connectQos, null);
            fail("Not expected successful connect");
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testDefaultConnectWithoutServer failed: " + e.getMessage());
         }

         if (xmlBlasterAccess.isAlive()) {
            log.severe("No testing possible when xmlBlaster is running - ignoring this test");
            xmlBlasterAccess.disconnect(null);
            continue;
         }

         assertTrue("", null != xmlBlasterAccess.getQueue());
         assertEquals("", false, xmlBlasterAccess.isAlive());
         assertEquals("", false, xmlBlasterAccess.isPolling());
         assertEquals("", false, xmlBlasterAccess.isDead());
         log.info("SUCCESS: Check I_ConnectionHandler");

         assertEquals("", false, xmlBlasterAccess.isConnected());
         xmlBlasterAccess.registerConnectionListener((I_ConnectionStateListener)null);


         assertTrue("", xmlBlasterAccess.getCbServer() == null);
         try {
            I_CallbackServer cbServer = xmlBlasterAccess.initCbServer(null, null);
            assertTrue("", cbServer != null);
            assertTrue("", cbServer.getCbProtocol() != null);
            assertTrue("", cbServer.getCbAddress() != null);
            log.info("SUCCESS: initCbServer protocol=" + cbServer.getCbProtocol() + " address=" + cbServer.getCbAddress());
            cbServer.shutdown();
            log.info("SUCCESS: CbServer.shutdown()");
            assertTrue("", xmlBlasterAccess.getCbServer() == null); // is not cached in xmlBlasterAccess
         }
         catch (XmlBlasterException e) {
            fail("testCreation failed: " + e.getMessage());
         }

         assertTrue("", xmlBlasterAccess.getSecurityPlugin() != null);
         assertEquals("", true, xmlBlasterAccess.disconnect(null));
         assertTrue("", xmlBlasterAccess.getConnectReturnQos() == null);
         assertTrue("", xmlBlasterAccess.getConnectQos() == null);
         assertTrue("", xmlBlasterAccess.getId() != null);
         assertTrue("", xmlBlasterAccess.getSessionName() == null);

         try {
            xmlBlasterAccess.subscribe((SubscribeKey)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.subscribe((String)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.subscribe((SubscribeKey)null, null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.get((String)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.get((GetKey)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.unSubscribe((String)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.unSubscribe((UnSubscribeKey)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.publish(null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }
            
         try {
            xmlBlasterAccess.publishOneway(null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }
            
         try {
            xmlBlasterAccess.publishArr(null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }
            
         try {
            xmlBlasterAccess.erase((String)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         try {
            xmlBlasterAccess.erase((EraseKey)null, null);
         }
         catch (XmlBlasterException e) {
            if (e.isUser())
               log.info("Exception is OK if not connected: " + e.getErrorCode());
            else
               fail("testCreation failed: " + e.getMessage());
         }

         assertTrue("", xmlBlasterAccess.getGlobal() != null);
         assertTrue("", xmlBlasterAccess.toXml() != null);

         log.info("SUCCESS: Check I_XmlBlasterAccess");
      }

      System.out.println("***XmlBlasterAccessTest: testCreation [SUCCESS]");
   }

   public void testDefaultConnectWithoutServer() throws Exception {
      System.out.println("***XmlBlasterAccessTest: testDefaultConnectWithoutServer ...");
      Global nullGlobal = null;
      I_XmlBlasterAccess xmlBlasterAccess = new XmlBlasterAccess(nullGlobal);

      assertEquals("", false, xmlBlasterAccess.isConnected());

      ConnectQos q = new ConnectQos(null);
      log.info("Default ConnectQos=" + q.toXml() + " retries=" + q.getAddress().getRetries() + " delay=" + q.getAddress().getDelay());
      assertEquals("", -1, q.getAddress().getRetries()); // retry forever
      assertEquals("", q.getAddress().getDefaultDelay(), q.getAddress().getDelay()); // 5000L
      assertTrue("", q.getAddress().getDelay() > 0);
      /*
      xmlBlasterAccess.registerConnectionListener((I_ConnectionStateListener)null);
      try {
         ConnectReturnQos connectReturnQos = xmlBlasterAccess.connect(null, null);
      }
      catch (XmlBlasterException e) {
         if (e.isUser())
            log.info("Exception is OK if not connected: " + e.getErrorCode());
         else
            fail("testDefaultConnectWithoutServer failed: " + e.getMessage());
      }
      log.info("SUCCESS: Check I_XmlBlasterAccess");
      */
      log.info("TEST MISSING");

      System.out.println("***XmlBlasterAccessTest: testDefaultConnectWithoutServer [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.XmlBlasterAccessTest
    * </pre>
    */
   public static void main(String args[]) {
      XmlBlasterAccessTest testSub = new XmlBlasterAccessTest(new Global(args, true, false), "XmlBlasterAccessTest");
      try {
         testSub.setUp();
         testSub.testCreation();
         testSub.testDefaultConnectWithoutServer();
         //testSub.tearDown();
      }
      catch (Exception e) {
         System.out.println("TEST FAILED: " + e.toString());
      }
   }
}
