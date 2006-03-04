/*------------------------------------------------------------------------------
Name:      TestSynchronousCache.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.GetReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.SynchronousCache;

import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * Here we test the client side synchronous cache for high performing getCached() invocations. 
 * <p>
 * </p>
 * <p>
 * Invoke examples:
 * </p>
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.client.TestSynchronousCache
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestSynchronousCache
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/client.cache.html">The client.cache requirement</a>
 * @see org.xmlBlaster.client.SynchronousCache
 */
public class TestSynchronousCache extends TestCase {
   private String ME = "TestSynchronousCache";
   private Global glob;
   private static Logger log = Logger.getLogger(TestSynchronousCache.class.getName());

   private I_XmlBlasterAccess con = null;
   private MsgInterceptor updateInterceptor;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 34576;
   private boolean startEmbedded = true;
   private SynchronousCache synchronousCache;
   private String[] publishOidArr = new String[] { "oid-0", "oid-1", "xx-oid-2" };
   private String[] contentArr = new String[] { "content-oid-0", "content-oid-1", "content-oid-2" };

   private int numReceived = 0;

   /**
    * Constructs the TestSynchronousCache object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSynchronousCache(Global glob, String testName) {
      super(testName);
      this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp() {
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);
      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         String[] args = { };
         glob.init(args);
         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info("XmlBlaster is ready for testing the client cache");
      }

      try {
         this.con = glob.getXmlBlasterAccess();
         this.synchronousCache = con.createSynchronousCache(100); // remember handle to check in this test
         ConnectQos connectQos = new ConnectQos(glob);
         this.updateInterceptor = new MsgInterceptor(glob,log, null);
         this.con.connect(connectQos, this.updateInterceptor);
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      for (int i=0; i<publishOidArr.length; i++) {
         // Erase if not all have been destroyed during test
         //try {
            EraseReturnQos[] arr = sendErase(publishOidArr[i]);
         //} catch(XmlBlasterException e) {
         //   fail("Erase XmlBlasterException: " + e.getMessage());
         //}
      }

      this.con.disconnect(null);
      this.con = null;

      if (this.startEmbedded) {
         try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {} // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
      }

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts(glob);

      this.glob = null;
      this.log = null;
      this.updateInterceptor = null;
      this.synchronousCache = null;
   }

   public EraseReturnQos[] sendErase(String publishOid) {
      log.info("Erasing topic '" + publishOid + "'");
      try {
         EraseQos eq = new EraseQos(glob);
         // !!!! NOTE: if force destroy is true the erase event may not
         // come through and the cache is not cleared !!! How to relove?
         eq.setForceDestroy(false);
         EraseKey ek = new EraseKey(glob, publishOid);
         EraseReturnQos[] er = con.erase(ek, eq);
         // Wait 200 milli seconds, until erase event is processed and cache is cleared ...
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}
         return er;
      } catch(XmlBlasterException e) {
         fail("Erase XmlBlasterException: " + e.getMessage());
      }
      return null;
   }

   /**
    * Publish an almost volatile message.
    */
   public PublishReturnQos publishMsg(String publishOid, String content) {
      log.info("Sending a message '" + content + "'");
      try {
         // Publish a volatile message
         PublishKey pk = new PublishKey(glob, publishOid, "text/xml", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk, content, pq);
         PublishReturnQos publishReturnQos = con.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, publishReturnQos.getKeyOid());
         log.info("Sending of '" + content + "' done, returned oid=" + publishOid + " " + msgUnit.toXml());
         return publishReturnQos;
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      return null; // never reached
   }

   /**
    * THIS IS THE TEST
    * <p>
    * We publish some messages and try cached access.
    * </p>
    */
   public void testCachedAccess() {
      this.ME = "TestSynchronousCache-testCachedAccess";
      {
         log.info("Entering testCachedAccess ...");
         try {
            publishMsg(publishOidArr[0], contentArr[0]);
            publishMsg(publishOidArr[2], contentArr[2]);
            try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

            GetKey gk = new GetKey(glob, publishOidArr[0]);
            GetQos gq = new GetQos(glob);

            for (int i=0; i<10; i++) {
               MsgUnit[] msgs = con.getCached(gk, gq);
               assertEquals(this.synchronousCache.toXml(""), 1, msgs.length);
               GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
               assertEquals("", 1, this.synchronousCache.getNumQueriesCached());
               log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                              "' and status=" + grq.getState());
            }

            EraseReturnQos[] arr0 = sendErase(publishOidArr[0]);
            assertEquals("", 0, this.synchronousCache.getNumQueriesCached());
            EraseReturnQos[] arr2 = sendErase(publishOidArr[2]);
         }
         catch (XmlBlasterException e) {
            log.severe("testCachedAccess() failed: " + e.getMessage());
            fail(e.getMessage());
         }
         assertEquals("Unexpected update arrived", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
      }

      {
         log.info("Entering testCachedAccess with updated MsgUnit ...");
         try {
            PublishReturnQos publishReturnQos = publishMsg(publishOidArr[0], contentArr[0]);

            GetKey gk = new GetKey(glob, publishOidArr[0]);
            GetQos gq = new GetQos(glob);

            for (int i=0; i<5; i++) {
               MsgUnit[] msgs = con.getCached(gk, gq);
               GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
               assertEquals(this.synchronousCache.toXml(""), 1, msgs.length);
               assertEquals("", 1, this.synchronousCache.getNumQueriesCached());
               assertEquals("", publishReturnQos.getRcvTimestamp(), grq.getRcvTimestamp());
               assertEquals("", contentArr[0], msgs[0].getContentStr());
               log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                              "' and status=" + grq.getState() + " rcv=" + grq.getRcvTimestamp());
            }

            // Now publish again an check if cache is updated
            String contentNew = contentArr[0]+"-NEW";
            publishReturnQos = publishMsg(publishOidArr[0], contentNew);
            try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
            for (int i=0; i<5; i++) {
               MsgUnit[] msgs = con.getCached(gk, gq);
               GetReturnQos grq = new GetReturnQos(glob, msgs[0].getQos());
               assertEquals(this.synchronousCache.toXml(""), 1, msgs.length);
               assertEquals("", 1, this.synchronousCache.getNumQueriesCached());
               assertEquals("", publishReturnQos.getRcvTimestamp().getTimestamp(), grq.getRcvTimestamp().getTimestamp());
               assertEquals("", publishReturnQos.getKeyOid(), msgs[0].getKeyOid());
               assertEquals("", contentNew, msgs[0].getContentStr());
               log.info("Accessed xmlBlaster message with content '" + new String(msgs[0].getContent()) +
                              "' and status=" + grq.getState() + " rcv=" + grq.getRcvTimestamp());
            }

            EraseReturnQos[] arr0 = sendErase(publishOidArr[0]);
            assertEquals("", 0, this.synchronousCache.getNumQueriesCached());
         }
         catch (XmlBlasterException e) {
            log.severe("testCachedAccess() failed: " + e.getMessage());
            fail(e.getMessage());
         }
         assertEquals("Unexpected update arrived", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
      }

      {
         log.info("Entering testCachedAccess with XPATH ...");
         try {
            PublishReturnQos publishReturnQos0 = publishMsg(publishOidArr[0], contentArr[0]);
            PublishReturnQos publishReturnQos1 = publishMsg(publishOidArr[1], contentArr[1]);
            publishMsg(publishOidArr[2], contentArr[2]);
            try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

            // This should match [0] and [1] msg:
            GetKey gk = new GetKey(glob, "//key[starts-with(@oid,'oid-')]", Constants.XPATH);
            GetQos gq = new GetQos(glob);

            for (int i=0; i<10; i++) {
               MsgUnit[] msgs = con.getCached(gk, gq);
               assertEquals("", 2, msgs.length);
               GetReturnQos grq0 = new GetReturnQos(glob, msgs[0].getQos());
               GetReturnQos grq1 = new GetReturnQos(glob, msgs[1].getQos());
               assertEquals(this.synchronousCache.toXml(""), 2, msgs.length);
               assertEquals(this.synchronousCache.toXml(""), 1, this.synchronousCache.getNumQueriesCached());
               log.info(" publishReturnQos0.getRcvTimestamp()=" + publishReturnQos0.getRcvTimestamp() +
                            " publishReturnQos1.getRcvTimestamp()=" + publishReturnQos1.getRcvTimestamp() +
                            " grq0.getRcvTimestamp()=" + grq0.getRcvTimestamp() +
                            " grq1.getRcvTimestamp()=" + grq1.getRcvTimestamp());
               assertTrue("", publishReturnQos0.getRcvTimestamp().equals(grq0.getRcvTimestamp()) ||
                              publishReturnQos0.getRcvTimestamp().equals(grq1.getRcvTimestamp()));
               assertTrue("", publishReturnQos1.getRcvTimestamp().equals(grq0.getRcvTimestamp()) ||
                              publishReturnQos1.getRcvTimestamp().equals(grq1.getRcvTimestamp()));
               assertTrue("", !grq0.getRcvTimestamp().equals(grq1.getRcvTimestamp()));
               assertEquals("", 2, msgs.length);
               log.info("Accessed " + msgs.length + " xmlBlaster messages with content '" +
                              new String(msgs[0].getContent()) +
                              "' and '" + new String(msgs[1].getContent()) + "' and status=" + grq0.getState());
            }

            log.info("Current cache:" + this.synchronousCache.toXml(""));
            assertEquals("", 1, this.synchronousCache.getNumQueriesCached());
            EraseReturnQos[] arr0 = sendErase(publishOidArr[0]);
            assertEquals("", 1, this.synchronousCache.getNumQueriesCached());
            EraseReturnQos[] arr1 = sendErase(publishOidArr[1]);
            log.info("Current cache:" + this.synchronousCache.toXml(""));

            // The cache is not cleared automatically for XPATH, we do it manually
            this.synchronousCache.removeEntryByQueryString(this.synchronousCache.getQueryString(gk));
            log.info("Current cache:" + this.synchronousCache.toXml(""));
            assertEquals("", 0, this.synchronousCache.getNumQueriesCached());
            EraseReturnQos[] arr2 = sendErase(publishOidArr[2]);
            assertEquals("", 0, this.synchronousCache.getNumQueriesCached());
         }
         catch (XmlBlasterException e) {
            log.severe("testCachedAccess() failed: " + e.getMessage());
            fail(e.getMessage());
         }
         assertEquals("Unexpected update arrived", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
      }
      log.info("SUCCESS testCachedAccess");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSynchronousCache(new Global(), "testCachedAccess"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestSynchronousCache -startEmbedded false
    */
   public static void main(String args[]) {
      TestSynchronousCache testSub = new TestSynchronousCache(new Global(args), "TestSynchronousCache");
      testSub.setUp();
      testSub.testCachedAccess();
      testSub.tearDown();
   }
}
