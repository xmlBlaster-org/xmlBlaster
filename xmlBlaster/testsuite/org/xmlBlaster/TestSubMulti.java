/*------------------------------------------------------------------------------
Name:      TestSubMulti.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubMulti.java,v 1.9 2002/05/11 10:07:54 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

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
 *    java test.textui.TestRunner testsuite.org.xmlBlaster.TestSubMulti
 *    java test.ui.TestRunner testsuite.org.xmlBlaster.TestSubMulti
 * </pre>
 */
public class TestSubMulti extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;

   private String publishOid = "";
   private XmlBlasterConnection con;
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
         con = new XmlBlasterConnection(glob);
         String passwd = "secret";
         con.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, "Login failed: " + e.toString());
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
      String[] strArr = new String[0];
      try {
         strArr = con.erase("<key oid='"+publishOid+"'/>", "<qos></qos>");
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      con.logout();
   }


   /**
    * TEST: Subscribe twice to messages with XPATH.
    */
   public void testSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");
      try {
        {
          SubscribeKeyWrapper key = new SubscribeKeyWrapper("//key/location[@dest='agent-192.168.10.218']", "XPATH");
          SubscribeQosWrapper qos = new SubscribeQosWrapper();
          String subscriptionId = con.subscribe(key.toXml(), qos.toXml(),this);
        }

        {
          SubscribeKeyWrapper key = new SubscribeKeyWrapper("//key[@contentMimeExtended='action']/location[@dest='agent-192.168.10.218' and @driver='PSD1']", "XPATH");
          SubscribeQosWrapper qos = new SubscribeQosWrapper();
          String subscriptionId = con.subscribe(key.toXml(), qos.toXml(), this);
        }
      }
      catch(XmlBlasterException e) {
        Log.warn(ME, "XmlBlasterException: " + e.reason);
        assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    */
   public void testPublish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");
      numReceived = 0;

      PublishKeyWrapper key = new PublishKeyWrapper("", contentMime, contentMimeExtended);
      key.wrap("<location dest='agent-192.168.10.218' driver='PSD1'></location>");
      PublishQosWrapper qos = new PublishQosWrapper();
      senderContent = "some content";
      MessageUnit msgUnit = new MessageUnit(key.toXml(), senderContent.getBytes(), qos.toXml());
      try {
         sentTimestamp = new Timestamp();
         publishOid = con.publish(msgUnit);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
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
      Log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + "...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Sleep to assure that publish() is returned with publishOid
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
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
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
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
    * Invoke: java testsuite.org.xmlBlaster.TestSubMulti
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSubMulti</pre>
    */
   public static void main(String args[])
   {
      TestSubMulti testSub = new TestSubMulti(new Global(args), "TestSubMulti", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
      Log.exit(TestSubMulti.ME, "Good bye");
   }
}

