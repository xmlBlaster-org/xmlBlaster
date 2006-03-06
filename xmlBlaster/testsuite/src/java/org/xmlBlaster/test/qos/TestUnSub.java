/*------------------------------------------------------------------------------
Name:      TestUnSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UnSubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.UnSubscribeQos;
import org.xmlBlaster.client.qos.UnSubscribeReturnQos;

import junit.framework.*;


/**
 * This client tests the method subscribe() with XPath query and with a later unSubscribe().
 * <br />
 * Multiple identical subscribes shouldn't harm and all following are ignored.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestUnSub
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestUnSub
 * </pre>
 */
public class TestUnSub extends TestCase implements I_Callback
{
   private static String ME = "TestUnSub";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestUnSub.class.getName());
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private String xpath = "//TestUnSub-AGENT";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestUnSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestUnSub(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;

      this.senderName = loginName;
      this.receiverName = loginName;
   }


   /**
    * Sets up the fixture (login).
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);
         senderConnection.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture (logout).
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      if (publishOid.length() > 0) { // not for testSubscribeUnSubscribeEmpty
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                           "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                           "</key>";
         try {
            EraseReturnQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      }

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void subscribeXPath()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath  +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: UnSubscribe to messages with XPATH.
    * <p />
    */
   public void unSubscribeXPath()
   {
      if (log.isLoggable(Level.FINE)) log.fine("UnSubscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         log.info("Success: UnSubscribe with " + xpath + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: UnSubscribe to messages with EXACT oid (which was returned from our XPATH subscription).
    * <p />
    */
   public void unSubscribeExact()
   {
      if (log.isLoggable(Level.FINE)) log.fine("UnSubscribing using EXACT syntax ...");

      String xmlKey = "<key oid='" + subscribeOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         log.info("Success: UnSubscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("unSubscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void doPublish()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestUnSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestUnSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestUnSub-DRIVER>"+
                      "   </TestUnSub-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      PublishReturnQos publishReturnQos = null;
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         publishReturnQos = senderConnection.publish(msgUnit);
         publishOid = publishReturnQos.getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid: " + publishReturnQos.toXml(), 0 != publishOid.length());
   }

   /**
    * TEST: subscribe and unSubscribe on an empty topic (without a publish)
    * The unSubscribe is done with an oid instead of a subId
    */
   public void testSubscribeUnSubscribeOid()
   {
      log.info("Starting testSubscribeUnSubscribeOid()");
      publishOid = "";

      String oid = "SomeDummySubscribe";
      SubscribeReturnQos subRet = null;
      try {
         SubscribeKey sk = new SubscribeKey(glob, oid);
         SubscribeQos sq = new SubscribeQos(glob);
         subRet = senderConnection.subscribe(sk.toXml(), sq.toXml());
         log.info("testSubscribeUnSubscribeOid() subscribed to " + subRet.getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         log.severe("testSubscribeUnSubscribeOid() subscribe failed: " + e.getMessage());
         fail("testSubscribeUnSubscribeOid() subscribe failed: " + e.getMessage());
      }

      try {
         // !! Here we unsubscribe with the oid instead of using the subId !!
         UnSubscribeKey uk = new UnSubscribeKey(glob, oid);
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         UnSubscribeReturnQos[] urq = senderConnection.unSubscribe(uk.toXml(), uq.toXml());
         log.info("testSubscribeUnSubscribeOid() unSubscribed");
         assertEquals("Return wrong", 1, urq.length);
         assertEquals("SubId wrong", subRet.getSubscriptionId(), urq[0].getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         log.severe("testSubscribeUnSubscribeOid() unSubscribe failed: " + e.getMessage());
         fail("testSubscribeUnSubscribeOid() unSubscribe failed: " + e.getMessage());
      }
   }

   /**
    * TEST: subscribe and unSubscribe on an empty topic (without a publish)
    */
   public void testSubscribeUnSubscribeEmpty()
   {
      log.info("Starting testSubscribeUnSubscribeEmpty()");
      publishOid = "";

      SubscribeReturnQos subRet = null;
      try {
         SubscribeKey sk = new SubscribeKey(glob, "SomeDummySubscribe");
         SubscribeQos sq = new SubscribeQos(glob);
         subRet = senderConnection.subscribe(sk.toXml(), sq.toXml());
         log.info("testSubscribeUnSubscribeEmpty() subscribed to " + subRet.getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         log.severe("testSubscribeUnSubscribeEmpty() subscribe failed: " + e.getMessage());
         fail("testSubscribeUnSubscribeEmpty() subscribe failed: " + e.getMessage());
      }

      try {
         UnSubscribeKey uk = new UnSubscribeKey(glob, subRet.getSubscriptionId());
         UnSubscribeQos uq = new UnSubscribeQos(glob);
         UnSubscribeReturnQos[] urq = senderConnection.unSubscribe(uk.toXml(), uq.toXml());
         log.info("testSubscribeUnSubscribeEmpty() unSubscribed");
         assertEquals("Return wrong", 1, urq.length);
         assertEquals("SubId wrong", subRet.getSubscriptionId(), urq[0].getSubscriptionId());
      }
      catch (XmlBlasterException e) {
         log.severe("testSubscribeUnSubscribeEmpty() unSubscribe failed: " + e.getMessage());
         fail("testSubscribeUnSubscribeEmpty() unSubscribe failed: " + e.getMessage());
      }
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the returned oid.
    */
   public void testSubscribeUnSubscribeExact()
   {
      log.info("Starting testSubscribeUnSubscribeExact()");
      numReceived = 0;
      doPublish();           // Feed some data
      subscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      unSubscribeExact();  // cancel XPATH subscription with XPATH-subscription-oid
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the same XPATH query.
    */
   public void testSubscribeUnSubscribeXPath()
   {
      log.info("Starting testSubscribeUnSubscribeXPath()");
      numReceived = 0;
      doPublish();           // Feed some data
      subscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      unSubscribeXPath();  // cancel with XPATH syntax
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");

      numReceived += 1;

      //assertEquals("Wrong sender, used="+senderName+" updated="+updateQos.getSender().getRelativeName(), senderName, updateQos.getSender().getRelativeName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

      messageArrived = true;
      return "";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
   private void waitOnUpdate(final long timeout)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (!messageArrived) {
         try {
            Thread.sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warning("Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived = false;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "TestUnSub/5";
       Global glob = new Global();
       suite.addTest(new TestUnSub(glob, "testSubscribeUnSubscribeEmpty", loginName));
       suite.addTest(new TestUnSub(glob, "testSubscribeUnSubscribeOid", loginName));
       suite.addTest(new TestUnSub(glob, "testSubscribeUnSubscribeExact", loginName));
       suite.addTest(new TestUnSub(glob, "testSubscribeUnSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestUnSub
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestUnSub</pre>
    */
   public static void main(String args[])
   {
      TestUnSub testSub = new TestUnSub(new Global(args), "TestUnSub", "TestUnSub/5");

      testSub.setUp();
      testSub.testSubscribeUnSubscribeOid();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubscribeUnSubscribeEmpty();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubscribeUnSubscribeXPath();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubscribeUnSubscribeExact();
      testSub.tearDown();
   }
}

