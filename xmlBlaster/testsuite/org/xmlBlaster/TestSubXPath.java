/*------------------------------------------------------------------------------
Name:      TestSubXPath.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubXPath.java,v 1.15 2002/05/17 06:52:20 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() on message 3 should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubXPath
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestSubXPath
 * </pre>
 */
public class TestSubXPath extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;
   private boolean messageArrived = false;

   private String publishOid = "";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private int numPublish = 5;
   private final String contentMime = "text/xml";
   private String subscribeOid = null;

   /**
    * Constructs the TestSubXPath object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubXPath(Global glob, String testName, String loginName)
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
         senderConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
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
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = new String[0];
      try {
         Log.info(ME, "Erasing the " + numPublish + " messages again");
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      assertEquals("Couldn't erase the correct number of messages", numPublish, strArr.length);

      senderConnection.logout();
   }


   /**
    * TEST: Subscribe to message number 3 with XPATH.
    * <p />
    */
   public void testSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   /xmlBlaster/key/AGENT[@id='message_3']" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done:\n" + xmlKey);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assertTrue("returned null subscribeOid", subscribeOid != null);
      assertTrue("returned subscribeOid is empty", 0 != subscribeOid.length());
   }


   /**
    * TEST: Construct 5 messages and publish them.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      for (int counter= 1; counter <= numPublish; counter++) {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + counter + "' contentMime='" + contentMime + "'>\n" +
                         "<AGENT id='message_" + counter + "' subId='1' type='generic'>" +
                         "<DRIVER id='FileProof' pollingFreq='10'>" +
                         "</DRIVER>"+
                         "</AGENT>" +
                         "</key>";
         String content = "Content: message_" + counter;
         MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), "<qos></qos>");
         try {
            publishOid = senderConnection.publish(msgUnit);
            Log.info(ME, "Success: Publishing #" + counter + " done, returned oid=" + publishOid);
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
            assertTrue("publish - XmlBlasterException: " + e.reason, false);
         }

         assertTrue("returned publishOid == null", publishOid != null);
         assertTrue("returned publishOid", 0 != publishOid.length());
      }
   }


   /**
    * TEST: Construct 5 messages and publish them,<br />
    * the previous XPath subscription should match message #3 and send an update.
    */
   public void testPublishAfterSubscribeXPath()
   {
      testSubscribeXPath();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      testPublish();
      waitOnUpdate(5000L);
      assertEquals("numReceived after publishing", 1, numReceived); // message arrived?
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
      String contentStr = new String(content);
      assertEquals("Message content is corrupted", "Content: message_3", contentStr);
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());
      assertEquals("engine.qos.update.subscriptionId: Wrong subscriptionId", subscribeOid, updateQos.getSubscriptionId());

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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
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
       suite.addTest(new TestSubXPath(new Global(), "testPublishAfterSubscribeXPath", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestSubXPath
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubXPath</pre>
    */
   public static void main(String args[])
   {
      TestSubXPath testSub = new TestSubXPath(new Global(args), "TestSubXPath", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
      Log.exit(TestSubXPath.ME, "Good bye");
   }
}

