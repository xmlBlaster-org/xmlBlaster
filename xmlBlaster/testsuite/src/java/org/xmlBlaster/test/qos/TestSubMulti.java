/*------------------------------------------------------------------------------
Name:      TestSubMulti.java
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
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests multi subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribes should be recognized for this later arriving publish()
 * Test is based on a bug report by Juergen Freidling
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubMulti
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubMulti
 * </pre>
 */
public class TestSubMulti extends TestCase implements I_Callback
{
   private static String ME = "TestSubMulti";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubMulti.class.getName());

   private String publishOid = "";
   private I_XmlBlasterAccess con;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private Timestamp sentTimestamp;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "action";

   /**
    * Constructs the TestSubMulti object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubMulti(Global glob, String testName, String loginName)
   {
       super(testName);
       this.glob = glob;

       this.senderName = loginName;
       this.receiverName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         con = glob.getXmlBlasterAccess();
         String passwd = "secret";
         ConnectQos qos = new ConnectQos(glob, senderName, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try {
         EraseReturnQos[] arr = con.erase("<key oid='"+publishOid+"'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
   }


   /**
    * TEST: Subscribe twice to messages with XPATH.
    */
   public void testSubscribeXPath()
   {
      log.info("Subscribing using XPath syntax ...");
      try {
        {
          SubscribeKey key = new SubscribeKey(glob, "//key/location[@dest='agent-192.168.10.218']", "XPATH");
          SubscribeQos qos = new SubscribeQos(glob);
          con.subscribe(key.toXml(), qos.toXml(),this);
        }

        {
          SubscribeKey key = new SubscribeKey(glob, "//key[@contentMimeExtended='action']/location[@dest='agent-192.168.10.218' and @driver='PSD1']", "XPATH");
          SubscribeQos qos = new SubscribeQos(glob);
          con.subscribe(key.toXml(), qos.toXml(), this);
        }
      }
      catch(XmlBlasterException e) {
        log.warning("XmlBlasterException: " + e.getMessage());
        assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    */
   public void testPublish()
   {
      log.info("Publishing a message ...");
      numReceived = 0;

      PublishKey key = new PublishKey(glob, "", contentMime, contentMimeExtended);
      key.setClientTags("<location dest='agent-192.168.10.218' driver='PSD1'></location>");
      PublishQos qos = new PublishQos(glob);
      senderContent = "some content";
      try {
         MsgUnit msgUnit = new MsgUnit(key.toXml(), senderContent.getBytes(), qos.toXml());
         sentTimestamp = new Timestamp();
         publishOid = con.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribeXPath();
      waitOnUpdate(1000L, 0); // Wait some time for callback to arrive ... there should be no Callback

      testPublish();
      waitOnUpdate(4000L, 2);
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of message oid=" + updateKey.getOid() + "...");

      numReceived += 1;

      if (updateQos.isErased()) {
         return "";
      }

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      try { Thread.sleep(1000); } catch( InterruptedException i) {} // Sleep to assure that publish() is returned with publishOid
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("Message contentMimeExtended is corrupted", contentMimeExtended, updateKey.getContentMimeExtended());

      // Test requirement "engine.qos.update.rcvTimestamp":
      assertTrue("sentTimestamp="+sentTimestamp+" not in hamony with rcvTimestamp="+updateQos.getRcvTimestamp(),
             sentTimestamp.getMillis() < updateQos.getRcvTimestamp().getMillis() &&
             (sentTimestamp.getMillis()+1000) > updateQos.getRcvTimestamp().getMillis());
      return "";
   }

   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      // check if too few are arriving
      while (numReceived < numWait) {
         try { Thread.sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.sleep(timeout); } catch( InterruptedException i) {}
      assertEquals("Wrong number of messages arrived", numWait, numReceived);

      numReceived = 0;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubMulti(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubMulti
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubMulti</pre>
    */
   public static void main(String args[])
   {
      TestSubMulti testSub = new TestSubMulti(new Global(args), "TestSubMulti", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
   }
}

