/*------------------------------------------------------------------------------
Name:      LoadTestSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: LoadTestSub.java,v 1.7 2000/02/24 22:19:53 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import test.framework.*;


/**
 * This client does a subscribe() with many publish() calls.<br />
 * The same message is published 1000 times, to measure messages/second performance.
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.LoadTestSub
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.LoadTestSub
 * </code>
 */
public class LoadTestSub extends TestCase implements I_Callback
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";
   private boolean messageArrived = false;
   private StopWatch stopWatch = null;

   private String subscribeOid;
   private String publishOid = "";
   private CorbaConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client

   private final int NUM_PUBLISH = 1000;
   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the LoadTestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public LoadTestSub(String testName, String loginName)
   {
       super(testName);
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
         senderConnection = new CorbaConnection(); // Find orb
         String passwd = "secret";
         String qos = "<qos></qos>";
         xmlBlaster = senderConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }

   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      long avg = NUM_PUBLISH / (stopWatch.elapsed()/1000L);
      Log.info(ME, NUM_PUBLISH + " messages updated, average messages/second = " + avg + stopWatch.nice());

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = xmlBlaster.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout(xmlBlaster);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribeXPath()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //LoadTestSub-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         // assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      // assert("returned null subscribeOid", subscribeOid != null);
      // assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String oid = "LoadTestSub";
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <LoadTestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <LoadTestSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </LoadTestSub-DRIVER>"+
                      "   </LoadTestSub-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes());
      stopWatch = new StopWatch();
      try {
         for (int ii=0; ii<NUM_PUBLISH; ii++) {
            senderContent = "Yeahh, i'm the new content number " + (ii+1);
            msgUnit.content = senderContent.getBytes();
            publishOid = xmlBlaster.publish(msgUnit, "<qos></qos>");
            /*
            if (((ii+1) % 1) == 0)
               Log.info(ME, "Success: Publishing done: '" + senderContent + "'");
            */
         }
         long avg = NUM_PUBLISH / (stopWatch.elapsed()/1000L);
         Log.info(ME, "Success: Publishing done, " + NUM_PUBLISH + " messages sent, average messages/second = " + avg);
         assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      // assert("returned publishOid == null", publishOid != null);
      // assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testManyPublish()
   {
      testSubscribeXPath();
      testPublish();
      waitOnUpdate(60*1000L, NUM_PUBLISH);
      assertEquals("numReceived after publishing", NUM_PUBLISH, numReceived); // message arrived?
   }


   /**
    * This is the callback method (I_Callback) invoked from CorbaConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of a message ...");

      numReceived++;
      /*
      if ((numReceived % 1) == 0) {
         long avg = numReceived / (stopWatch.elapsed()/1000L);
         Log.info(ME, "Success: Update #" + numReceived + " received: '" + new String(content) + "', average messages/second = " + avg);
      }
      */
      messageArrived = true;
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
      while (numReceived < numWait) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            Log.warning(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new LoadTestSub("testManyPublish", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.LoadTestSub
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.LoadTestSub</code>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      LoadTestSub testSub = new LoadTestSub("LoadTestSub", "Tim");
      testSub.setUp();
      testSub.testManyPublish();
      testSub.tearDown();
      Log.exit(LoadTestSub.ME, "Good bye");
   }
}

