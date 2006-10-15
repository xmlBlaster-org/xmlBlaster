/*------------------------------------------------------------------------------
Name:      TestSubOneway.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;

import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;

import junit.framework.*;


/**
 * This client tests the oneway callback. 
 * <br />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubOneway
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubOneway
 * </pre>
 */
public class TestSubOneway extends TestCase
{
   private static String ME = "TestSubOneway";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubOneway.class.getName());

   private String oidExact = "OnewayMessage";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   
   private MsgInterceptor msgInterceptor;

   /**
    * Constructs the TestSubOneway object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubOneway(Global glob, String testName, String loginName) {
      super(testName);
      this.glob = glob;

      this.senderName = loginName;
   }


   /**
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      try {
         this.msgInterceptor = new MsgInterceptor(glob, log, null);
         this.senderConnection = glob.getXmlBlasterAccess();
         String passwd = "secret";
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         this.senderConnection.connect(qos, this.msgInterceptor);
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          fail("Login failed: " + e.toString());
      }
   }


   /**
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try {
         UnSubscribeKey sk = new UnSubscribeKey(glob, this.oidExact);
         UnSubscribeQos sq = new UnSubscribeQos(glob);
         this.senderConnection.unSubscribe(sk, sq);
         
         EraseReturnQos[] arr = this.senderConnection.erase("<key oid='" + this.oidExact + "'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      this.senderConnection.disconnect(null);
   }


   /**
    * Subscribe oneway. 
    */
   public void testOneway() throws Exception {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using EXACT syntax ...");

      SubscribeKey sk = new SubscribeKey(glob, this.oidExact);
      SubscribeQos sq = new SubscribeQos(glob);
      sq.setWantUpdateOneway(true);
      this.senderConnection.subscribe(sk, sq);
      log.info("Success: Subscribe done");
      
      PublishKey pk = new PublishKey(glob, this.oidExact);
      PublishQos pq = new PublishQos(glob);
      MsgUnit msgUnit = new MsgUnit(pk, "Hello oneway", pq);
      this.senderConnection.publish(msgUnit);
      
      this.msgInterceptor.waitOnUpdate(2000L, this.oidExact, Constants.STATE_OK, 1);
      Msg[] msgs = this.msgInterceptor.getMsgs();
      assertEquals(1, msgs.length);
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSubOneway(new Global(), "testOneway", "joe"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubOneway
    */
   public static void main(String args[]) throws Exception
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubOneway testSub = new TestSubOneway(glob, "TestSubOneway", "joe");
      testSub.setUp();
      testSub.testOneway();
      testSub.tearDown();
   }
}

