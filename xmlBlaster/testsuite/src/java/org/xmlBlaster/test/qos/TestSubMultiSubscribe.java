/*------------------------------------------------------------------------------
Name:      TestSubMultiSubscribe.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
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
 * This client tests a subscribe() with multiSubscribe=false to avoid receiving
 * duplicate updates from the same topic on multiple subscribes. 
 * <br />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubMultiSubscribe
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubMultiSubscribe
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.subscribe.multiSubscribe.html" target="others">interface subscribe requirement</a>
 */
public class TestSubMultiSubscribe extends TestCase
{
   private static String ME = "TestSubMultiSubscribe";
   private final Global glob;
   private final LogChannel log;

   private String subscribeId;
   private MsgInterceptor updateInterceptor;
   
   private String publishOid = "HelloMessageMultiSub";
   private XmlBlasterConnection connection;

   /**
    * Constructs the TestSubMultiSubscribe object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestSubMultiSubscribe(Global glob, String testName) {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog(null);
   }

   /**
    * Sets up the fixture.
    */
   protected void setUp() {
      try {
         connection = new XmlBlasterConnection(glob); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         this.updateInterceptor = new MsgInterceptor(glob,log, null);
         connection.connect(qos, this.updateInterceptor);
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          fail("Login failed: " + e.toString());
      }
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
    * Subscribe multiple times to the same message with &lt;multiSubscribe>false&lt;/multiSubscribe>
    */
   public void subscribe() {
      if (log.TRACE) log.trace(ME, "Subscribing ...");
      try {
         for(int i=0; i<10; i++) {
            SubscribeKey key = new SubscribeKey(glob, publishOid);
            SubscribeQos qos = new SubscribeQos(glob);
            qos.setMultiSubscribe(false);
            SubscribeReturnQos ret = this.connection.subscribe(key.toXml(), qos.toXml());
            log.info(ME, "Subscribe #" + i + " state=" + ret.getState() + " subscriptionId=" + ret.getSubscriptionId());
            if (subscribeId == null) {
               subscribeId = ret.getSubscriptionId();
               assertEquals("", Constants.STATE_OK, ret.getState());
               continue;
            }
            assertEquals("", subscribeId, ret.getSubscriptionId());
            assertEquals("", Constants.STATE_WARN, ret.getState());
         }
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Construct a message and publish it.
    */
   public void publish() {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      String xmlKey = "<key oid='" + publishOid + "'/>";
      String senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos/>");
         publishOid = connection.publish(msgUnit).getKeyOid();
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("publish - XmlBlasterException: " + e.getMessage());
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
   }

   /**
    * unSubscribe twice to same message. 
    */
   public void unSubscribe() {
      if (log.TRACE) log.trace(ME, "unSubscribing ...");

      String qos = "<qos/>";
      try {
         connection.unSubscribe("<key oid='" + subscribeId + "'/>", qos);
         log.info(ME, "Success: unSubscribe 1 on " + subscribeId + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("unSubscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * TEST: Construct a message and publish it,
    * the first subscription shouldn't  receive the message as local==false
    */
   public void testMultiSubscribe() {
      log.info(ME, "testMultiSubscribe ...");
      
      subscribe();   // there should be no Callback 
      assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));

      int numPub = 5;
      for (int i=0; i<numPub; i++)
         publish();     // We expect numPub updates only
      assertEquals("", numPub, this.updateInterceptor.waitOnUpdate(1000L, publishOid, Constants.STATE_OK));
      this.updateInterceptor.clear();

      unSubscribe(); // One single unSubscribe should be enough

      publish();
      assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestSubMultiSubscribe(new Global(), "testMultiSubscribe"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubMultiSubscribe
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubMultiSubscribe</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestSubMultiSubscribe testSub = new TestSubMultiSubscribe(glob, "TestSubMultiSubscribe");
      testSub.setUp();
      testSub.testMultiSubscribe();
      testSub.tearDown();
   }
}

