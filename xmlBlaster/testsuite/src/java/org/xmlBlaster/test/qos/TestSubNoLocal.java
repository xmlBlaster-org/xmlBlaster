/*------------------------------------------------------------------------------
Name:      TestSubNoLocal.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client tests a subscribe() with local=false to avoid receiving
 * its own publishes. 
 * <br />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNoLocal
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubNoLocal
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.subcribe.html" target="others">interface subscribe requirement</a>
 */
public class TestSubNoLocal extends TestCase implements I_Callback
{
   private static String ME = "TestSubNoLocal";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubNoLocal.class.getName());

   private String subscribeId1;
   private MsgInterceptor updateInterceptor1;
   
   private String subscribeId2;
   private MsgInterceptor updateInterceptor2;

   private String publishOid = "HelloMessageNoLocal";
   private I_XmlBlasterAccess connection;

   /**
    * Constructs the TestSubNoLocal object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestSubNoLocal(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Sets up the fixture.
    */
   protected void setUp() {
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      if (this.connection != null) {
         if (this.publishOid != null) {
            String xmlKey = "<key oid='" + this.publishOid + "' queryType='EXACT'/>";
            try {
               EraseReturnQos[] arr = this.connection.erase(xmlKey, "<qos/>");
               assertEquals("Erase", 1, arr.length);
            } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
         }

         this.connection.disconnect(null);
         this.connection = null;
      }
   }

   /**
    * Subscribe twice to the same message, one time with &lt;local>false&lt;/local>
    */
   public void subscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");
      try {
         {
            SubscribeKey key = new SubscribeKey(glob, publishOid);
            SubscribeQos qos = new SubscribeQos(glob);
            qos.setWantLocal(false);
            this.updateInterceptor1 = new MsgInterceptor(glob, log, null); // Collect received msgs
            SubscribeReturnQos ret = this.connection.subscribe(key.toXml(), qos.toXml(), this.updateInterceptor1);
            subscribeId1 = ret.getSubscriptionId();
         }
         {
            SubscribeKey key = new SubscribeKey(glob, publishOid);
            SubscribeQos qos = new SubscribeQos(glob);
            qos.setWantLocal(true);
            this.updateInterceptor2 = new MsgInterceptor(glob, log, null); // Collect received msgs
            SubscribeReturnQos ret = connection.subscribe(key.toXml(), qos.toXml(), this.updateInterceptor2);
            subscribeId2 = ret.getSubscriptionId();
         }
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Construct a message and publish it.
    */
   public void publish() {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      String xmlKey = "<key oid='" + publishOid + "'/>";
      String senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos/>");
         publishOid = connection.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("publish - XmlBlasterException: " + e.getMessage());
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
   }

   /**
    * unSubscribe twice to same message. 
    */
   public void unSubscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("unSubscribing ...");

      String qos = "<qos/>";
      try {
         connection.unSubscribe("<key oid='" + subscribeId1 + "'/>", qos);
         log.info("Success: unSubscribe 1 on " + subscribeId1 + " done");

         connection.unSubscribe("<key oid='" + subscribeId2 + "'/>", qos);
         log.info("Success: unSubscribe 2 on " + subscribeId2 + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("unSubscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   private void connect() {
      try {
         connection = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         connection.connect(qos, this);
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          fail("Login failed: " + e.toString());
      }
   }

   /**
    * TEST: Construct a message and publish it,
    * the first subscription shouldn't  receive the message as local==false
    */
   public void testLocalUpdates() {
      log.info("testLocalUpdates ...");
      
      connect();

      subscribe();   // there should be no Callback 
      assertEquals("", 0, this.updateInterceptor1.waitOnUpdate(1000L, 0));
      assertEquals("", 0, this.updateInterceptor2.waitOnUpdate(1000L, 0));

      int numPub = 5;
      for (int i=0; i<numPub; i++)
         publish();     // We expect sub 2 to receive the updates only
      assertEquals("", 0, this.updateInterceptor1.waitOnUpdate(1000L, 0));
      assertEquals("", numPub, this.updateInterceptor2.waitOnUpdate(1000L, publishOid, Constants.STATE_OK));
      this.updateInterceptor2.clear();

      unSubscribe();

      publish();
      assertEquals("", 0, this.updateInterceptor1.waitOnUpdate(1000L, 0));
      assertEquals("", 0, this.updateInterceptor2.waitOnUpdate(1000L, 0));
   }

   /**
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.severe("Receiving unexpected update of a message " + updateKey.getOid() + " state=" + updateQos.getState());
      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSubNoLocal(new Global(), "testLocalUpdates"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubNoLocal
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubNoLocal</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestSubNoLocal testSub = new TestSubNoLocal(glob, "TestSubNoLocal");
      testSub.setUp();
      testSub.testLocalUpdates();
      testSub.tearDown();
   }
}

