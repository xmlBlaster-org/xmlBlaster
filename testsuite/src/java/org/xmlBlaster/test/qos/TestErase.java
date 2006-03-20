/*------------------------------------------------------------------------------
Name:      TestErase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;

import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client tests if it receives message erase events. 
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestErase
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestErase
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">update requirement</a>
 */
public class TestErase extends TestCase implements I_Callback
{
   private static String ME = "TestErase";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestErase.class.getName());

   private String subscribeId;
   private String oidExact = "HelloMessage";
   private String oidXpath = "//key[@oid=\""+oidExact+"\"]";
   private MsgUnit msgUnit;
   private String publishOid = null;
   private I_XmlBlasterAccess con;

   private final String contentMime = "text/xml";

   private MsgInterceptor updateInterceptor;

   /**
    * Constructs the TestErase object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestErase(Global glob, String testName) {
       super(testName);
       this.glob = glob;

   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      if (con != null) {
         if (publishOid != null) {
            String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
            try {
               EraseReturnQos[] arr = con.erase(xmlKey, "<qos/>");
               // can be erased already in our tests
            } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
         }

         con.disconnect(null);
         con = null;
      }
   }

   /**
    * Subscribe three times to same message. 
    * <p />
    * The returned subscribeId is checked
    */
   private void subscribe(boolean exact) {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");

      String xmlKey;
      if (exact)
         xmlKey = "<key oid='" + oidExact + "' queryType='EXACT'/>";
      else
         xmlKey = "<key oid='' queryType='XPATH'>" + oidXpath + "</key>";
      String qos = "<qos/>";
      subscribeId = null;
      try {
         subscribeId = con.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeId", subscribeId != null);
         assertTrue("returned subscribeId is empty", 0 != subscribeId.length());
         log.info("Success: Subscribe on " + subscribeId + " done");
      }
      catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   private void publish() {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      String xmlKey = "<key oid='" + oidExact + "' contentMime='" + contentMime + "'/>";
      String senderContent = "Yeahh, i'm the new content";
      try {
         msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos/>");
         publishOid = con.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
      assertEquals("returned publishOid is wrong", oidExact, publishOid);
   }

   /**
    * unSubscribe three times to same message. 
    */
   private void unSubscribe() {
      if (log.isLoggable(Level.FINE)) log.fine("unSubscribing ...");

      String qos = "<qos/>";
      try {
         con.unSubscribe("<key oid='" + subscribeId + "'/>", qos);
         log.info("Success: unSubscribe on " + subscribeId + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   private void erase() {
      if (publishOid != null) {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
         try {
            EraseReturnQos[] arr = con.erase(xmlKey, "<qos/>");
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      }
   }

   private void connect() {
      try {
         this.updateInterceptor = new MsgInterceptor(this.glob, log, this);
         con = glob.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this.updateInterceptor);
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
      this.updateInterceptor.clear();
   }

   /**
    * TEST: Subscribe to a message, publish it, erase it and check if we are notified
    * about the erased message
    * <br />
    */
   public void testEraseEvent() throws Exception {
      log.info("testEraseEvent ...");
      
      connect();

      subscribe(true);
      assertEquals("numReceived after subscribe", 0, this.updateInterceptor.waitOnUpdate(1000L, 0)); // no message arrived?
      
      {
         publish();
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(1500L, oidExact, Constants.STATE_OK, 1));
         Msg msg = this.updateInterceptor.getMsg(oidExact, Constants.STATE_OK);
         assertTrue("Wrong update state", msg.getUpdateQos().isOk());

         msg.compareMsg(msgUnit);
         assertEquals("Message contentMime is corrupted", contentMime, msg.getUpdateKey().getContentMime());

         this.updateInterceptor.clear();
      }

      {
         this.updateInterceptor.countErased(true);
         erase();
         assertEquals("erase event is missing", 1, this.updateInterceptor.waitOnUpdate(2500L, oidExact, Constants.STATE_ERASED, 1));
         Msg msg = this.updateInterceptor.getMsg(oidExact, Constants.STATE_ERASED);
         assertEquals("wrong subscriptionId expected=" + subscribeId, subscribeId, msg.getUpdateQos().getSubscriptionId());
         assertTrue("wrong update state", msg.getUpdateQos().isErased());
         
         this.updateInterceptor.clear();
      }

      log.info("testEraseEvent SUCCESS");
   }

   /**
    * TEST: Subscribe to a message, publish it, erase it and check if we are notified
    * about the erased message
    * <br />
    */
   public void testXPathEraseEvent() throws Exception {
      log.info("testXPathEraseEvent ...");
      
      connect();

      subscribe(false);
      assertEquals("numReceived after subscribe", 0, this.updateInterceptor.waitOnUpdate(1000L, 0)); // no message arrived?

      {
         publish();
         assertEquals("numReceived after sending", 1, this.updateInterceptor.waitOnUpdate(1500L, oidExact, Constants.STATE_OK, 1));
         Msg msg = this.updateInterceptor.getMsg(oidExact, Constants.STATE_OK);
         assertTrue("Wrong update state", msg.getUpdateQos().isOk());

         msg.compareMsg(msgUnit);
         assertEquals("Message contentMime is corrupted", contentMime, msg.getUpdateKey().getContentMime());

         this.updateInterceptor.clear();
      }

      {
         log.info("Erasing now ...");
         this.updateInterceptor.countErased(true);

         erase();
         assertEquals("erase event is missing", 1, this.updateInterceptor.waitOnUpdate(2500L, oidExact, Constants.STATE_ERASED, 1));
         Msg msg = this.updateInterceptor.getMsg(oidExact, Constants.STATE_ERASED);
         assertEquals("wrong subscriptionId expected=" + subscribeId, subscribeId, msg.getUpdateQos().getSubscriptionId());
         assertTrue("wrong update state", msg.getUpdateQos().isErased());
         
         this.updateInterceptor.clear();
      }

      log.info("testXPathEraseEvent SUCCESS");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");
      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestErase(new Global(), "testEraseEvent"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestErase
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestErase</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      try {
         TestErase testSub = new TestErase(glob, "TestErase");
         testSub.setUp();
         //testSub.testEraseEvent();
         testSub.testXPathEraseEvent();
         testSub.tearDown();
      }
      catch (Exception e) {
         e.printStackTrace();
         System.out.println("EERRRROR: " + e.toString());
      }

   }
}

