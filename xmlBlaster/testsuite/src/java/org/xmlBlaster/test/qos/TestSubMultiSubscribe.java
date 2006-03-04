/*------------------------------------------------------------------------------
Name:      TestSubMultiSubscribe.java
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
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.util.qos.AccessFilterQos;
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
   private static Logger log = Logger.getLogger(TestSubMultiSubscribe.class.getName());

   private String subscribeId;
   private final String myDomain = "myDomain";
   private MsgInterceptor updateInterceptor;
   
   private String publishOid = "HelloMessageMultiSub";
   private I_XmlBlasterAccess connection;

   /**
    * Constructs the TestSubMultiSubscribe object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestSubMultiSubscribe(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Sets up the fixture.
    */
   protected void setUp() {
      this.subscribeId = null;
      try {
         connection = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         this.updateInterceptor = new MsgInterceptor(glob,log, null);
         connection.connect(qos, this.updateInterceptor);
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
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
   public void subscribe(String queryString, String queryType, AccessFilterQos aq, int numSub) {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");
      try {
         for(int i=0; i<numSub; i++) {
            SubscribeKey key = new SubscribeKey(glob, queryString, queryType);
            SubscribeQos qos = new SubscribeQos(glob);
            qos.setMultiSubscribe(false);
            if (aq != null) {
               qos.addAccessFilter(aq);
            }
            SubscribeReturnQos ret = this.connection.subscribe(key.toXml(), qos.toXml());
            log.info("Subscribe #" + i + " state=" + ret.getState() + " subscriptionId=" + ret.getSubscriptionId());
            if (subscribeId == null) {
               subscribeId = ret.getSubscriptionId();
               assertEquals("", Constants.STATE_OK, ret.getState());
               continue;
            }
            assertEquals("", subscribeId, ret.getSubscriptionId());
            assertEquals("", Constants.STATE_WARN, ret.getState());
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

      String xmlKey = "<key oid='" + publishOid + "' domain='"+myDomain+"'/>";
      String senderContent = "Yeahh, i'm the new content";
      String xmlQos = "<qos><clientProperty name='phone'>1200003</clientProperty></qos>";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), xmlQos);
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
         connection.unSubscribe("<key oid='" + subscribeId + "'/>", qos);
         log.info("Success: unSubscribe 1 on " + subscribeId + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         fail("unSubscribe - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * TEST: Construct a message and publish it,
    * the first subscription shouldn't  receive the message as local==false
    */
   public void testMultiSubscribeOid() {
      log.info("testMultiSubscribeOid ...");
      
      subscribe(publishOid, Constants.EXACT, null, 10);   // there should be no Callback 
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
    * TEST: Construct a message and publish it,
    * the first subscription shouldn't  receive the message as local==false
    */
   public void testMultiSubscribeXPath() {
      log.info("testMultiSubscribeXPath ...");
      
      subscribe("//key[@oid='"+publishOid+"']", Constants.XPATH, null, 10);   // there should be no Callback 
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
    * TEST: Change AccessFilter of SubscribeQos and test if reconfiguration works. 
    */
   public void testSubscribeReconfigure() {
      log.info("testSubscribeReconfigure ...");

      final String filterType = "Sql92Filter";
      final String filterVersion = "1.0";
      String filterQuery = "phone LIKE '12%3'";

      {
         log.info("Matching accessFilter");
         AccessFilterQos aq = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
         subscribe("//key[@oid='"+publishOid+"']", Constants.XPATH, aq, 1);
         assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));

         publish();
         assertEquals("", 1, this.updateInterceptor.waitOnUpdate(1000L, publishOid, Constants.STATE_OK));
         this.updateInterceptor.clear();
      }

      {
         log.info("NOT matching accessFilter");
         filterQuery = "phone LIKE '1XX%3'";
         AccessFilterQos aq = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
         subscribe("//key[@oid='"+publishOid+"']", Constants.XPATH, aq, 1);
         assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));

         publish();
         assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
         this.updateInterceptor.clear();
      }

      {
         log.info("Matching accessFilter");
         filterQuery = "phone LIKE '12%3'";
         AccessFilterQos aq = new AccessFilterQos(glob, filterType, filterVersion, filterQuery);
         subscribe("//key[@oid='"+publishOid+"']", Constants.XPATH, aq, 1);
         assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));

         publish();
         assertEquals("", 1, this.updateInterceptor.waitOnUpdate(1000L, publishOid, Constants.STATE_OK));
         this.updateInterceptor.clear();
      }

      unSubscribe(); // One single unSubscribe should be enough

      publish();
      assertEquals("", 0, this.updateInterceptor.waitOnUpdate(1000L, 0));
   }

   /**
    * TEST: Construct a message and publish it,
    * the first subscription shouldn't  receive the message as local==false
    */
   public void testMultiSubscribeDomain() {
      log.info("testMultiSubscribeDomain ...");
      
      // For domain queries the topic must exist: Therefor publish one message to create it!
      publish();     // We expect numPub updates only

      subscribe(myDomain, Constants.DOMAIN, null, 10);   // there should be no Callback 
      assertEquals("", 1, this.updateInterceptor.waitOnUpdate(1000L, 1));
      this.updateInterceptor.clear();

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
       suite.addTest(new TestSubMultiSubscribe(new Global(), "testMultiSubscribeOid"));
       suite.addTest(new TestSubMultiSubscribe(new Global(), "testMultiSubscribeXPath"));
       suite.addTest(new TestSubMultiSubscribe(new Global(), "testSubscribeReconfigure"));
       suite.addTest(new TestSubMultiSubscribe(new Global(), "testMultiSubscribeDomain"));
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
      //testSub.testMultiSubscribeOid();
      //testSub.testMultiSubscribeXPath();
      testSub.testSubscribeReconfigure();
      //testSub.testMultiSubscribeDomain();
      testSub.tearDown();
   }
}

