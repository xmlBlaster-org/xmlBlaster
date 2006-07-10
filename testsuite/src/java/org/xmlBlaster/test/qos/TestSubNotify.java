/*------------------------------------------------------------------------------
Name:      TestSubNotify.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.address.CallbackAddress;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNotify
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubNotify
 * </pre>
 */
public class TestSubNotify extends TestCase
{
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubNotify.class.getName());
   private String publishOid = "dummyTestSubNotify";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private MsgInterceptor msgInterceptor;

   /**
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubNotify(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.senderName = testName;
   }

   /**
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      try {
         this.senderConnection = glob.getXmlBlasterAccess();
         String passwd = "secret";
         this.msgInterceptor = new MsgInterceptor(this.glob, log, null);
         this.msgInterceptor.countErased(true); // count erased() notifications as well
         this.senderConnection.connect(new ConnectQos(glob, senderName, passwd), this.msgInterceptor); // Login to xmlBlaster
      }
      catch (Exception e) {
         e.printStackTrace();
         fail("Login failed: " + e.toString());
      }
   }

   protected void tearDown() {
      this.senderConnection.disconnect(null);
   }

   private void erase() {
      try {
         EraseKey ek = new EraseKey(this.glob, this.publishOid);
         EraseQos eq = new EraseQos(this.glob);
         EraseReturnQos[] arr = this.senderConnection.erase(ek, eq);
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
   }

   private void subscribe(boolean wantNotify) {
      try {
         SubscribeKey sk = new SubscribeKey(this.glob, this.publishOid);
         SubscribeQos sq = new SubscribeQos(this.glob);
         sq.setWantNotify(wantNotify);
         this.senderConnection.subscribe(sk, sq);
      } catch(XmlBlasterException e) { fail("Subscribe XmlBlasterException: " + e.getMessage()); }
   }

   private void publish() {
      try {
         MsgUnit msgUnit = new MsgUnit("<key oid='"+this.publishOid+"'/>", "Hi".getBytes(), "<qos/>");
         PublishReturnQos tmp = senderConnection.publish(msgUnit);
         assertEquals("Wrong publishOid", publishOid, tmp.getKeyOid());
      } catch(XmlBlasterException e) { fail("publish - XmlBlasterException: " + e.getMessage()); }
   }

   public void testNotify() {
      subscribe(true);

      publish();
      this.msgInterceptor.waitOnUpdate(1000, this.publishOid, Constants.STATE_OK, 1);
      assertEquals("Missing update", 1, this.msgInterceptor.count());
      this.msgInterceptor.clear(); this.msgInterceptor.countErased(true);
      
      erase();
      this.msgInterceptor.waitOnUpdate(1000, this.publishOid, Constants.STATE_ERASED, 1);
      assertEquals("Missing ERASED event", 1, this.msgInterceptor.count());
      log.info("Success, we received the erase notification");
   }

   public void testNoNotify() {
      subscribe(false); // NO ERASE NOTIFICATION WANTED
      
      publish();
      this.msgInterceptor.waitOnUpdate(1000, this.publishOid, Constants.STATE_OK, 1);
      assertEquals("Missing update", 1, this.msgInterceptor.count());
      this.msgInterceptor.clear(); this.msgInterceptor.countErased(true);
      
      erase();
      this.msgInterceptor.waitOnUpdate(1000, this.publishOid, Constants.STATE_ERASED, 0);
      assertEquals("Wrong ERASED event", 0, this.msgInterceptor.count());

      log.info("Success, we didn't receive the erase notification");
   }

   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "TestSubNotify";
       suite.addTest(new TestSubNotify(new Global(), "testNotify"));
       suite.addTest(new TestSubNotify(new Global(), "testNoNotify"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubNotify
    */
   public static void main(String args[]) {
      //junit.swingui.TestRunner.run(Bla.class);
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestSubNotify TestSubNotify = new TestSubNotify(glob, "TestSubNotify");
      
      TestSubNotify.setUp();
      TestSubNotify.testNotify();
      TestSubNotify.tearDown();

      TestSubNotify.setUp();
      TestSubNotify.testNoNotify();
      TestSubNotify.tearDown();
   }
}

