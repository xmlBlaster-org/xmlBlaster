/*------------------------------------------------------------------------------
Name:      TestUnSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestUnSub.java,v 1.2 2002/09/13 23:18:31 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;

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
   private final LogChannel log;
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "";
   private XmlBlasterConnection senderConnection;
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
      this.log = this.glob.getLog("test");
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
         senderConnection = new XmlBlasterConnection(glob); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, e.toString());
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      try {
         EraseRetQos[] arr = senderConnection.erase(xmlKey, "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribeXPath()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath  +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: UnSubscribe to messages with XPATH.
    * <p />
    */
   public void testUnSubscribeXPath()
   {
      if (log.TRACE) log.trace(ME, "UnSubscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   " + xpath +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         log.info(ME, "Success: UnSubscribe with " + xpath + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("unSubscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: UnSubscribe to messages with EXACT oid (which was returned from our XPATH subscription).
    * <p />
    */
   public void testUnSubscribeExact()
   {
      if (log.TRACE) log.trace(ME, "UnSubscribing using EXACT syntax ...");

      String xmlKey = "<key oid='" + subscribeOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         senderConnection.unSubscribe(xmlKey, qos);
         log.info(ME, "Success: UnSubscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("unSubscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestUnSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <TestUnSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </TestUnSub-DRIVER>"+
                      "   </TestUnSub-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         publishOid = senderConnection.publish(msgUnit).getOid();
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the returned oid.
    */
   public void testSubscribeUnSubscribeExact()
   {
      log.info(ME, "Starting testSubscribeUnSubscribeExact()");
      numReceived = 0;
      testPublish();           // Feed some data
      testSubscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      testUnSubscribeExact();  // cancel XPATH subscription with XPATH-subscription-oid
   }


   /**
    * TEST: Publish a message, subscribe on it with XPATH and
    *       unSubscribe again with the same XPATH query.
    */
   public void testSubscribeUnSubscribeXPath()
   {
      log.info(ME, "Starting testSubscribeUnSubscribeXPath()");
      numReceived = 0;
      testPublish();           // Feed some data
      testSubscribeXPath();    // Subscribe to it
      waitOnUpdate(2000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
      testUnSubscribeXPath();  // cancel with XPATH syntax
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
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
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warn(ME, "Timeout of " + timeout + " occurred");
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
       String loginName = "Tim";
       Global glob = new Global();
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
      TestUnSub testSub = new TestUnSub(new Global(args), "TestUnSub", "Tim");

      testSub.setUp();
      testSub.testSubscribeUnSubscribeXPath();
      testSub.tearDown();

      testSub.setUp();
      testSub.testSubscribeUnSubscribeExact();
      testSub.tearDown();
   }
}

