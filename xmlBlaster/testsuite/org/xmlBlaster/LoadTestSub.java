/*------------------------------------------------------------------------------
Name:      LoadTestSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: LoadTestSub.java,v 1.22 2002/03/13 16:41:37 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.*;

import test.framework.*;


/**
 * This client does a subscribe() with many publish() calls.<br />
 * The same message is published 1000 times, to measure messages/second performance.
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.LoadTestSub
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.LoadTestSub
 * </pre>
 */
public class LoadTestSub extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;
   private StopWatch stopWatch = null;

   private String subscribeOid;
   private String publishOid = "LoadTestSub";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String senderContent;
   private String receiverName;         // sender/receiver is here the same client
   private String passwd;

   private final int numPublish;        // 200;
   private int numReceived = 0;         // error checking
   private int burstModePublish = 1;
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";
   private int lastContentNumber = -1;
   private final String someContent = "Yeahh, i'm the new content number ";

   /**
    * Constructs the LoadTestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param numPublish The number of messages to send
    * @param burstModePublish send given number of publish messages in one bulk
    */
   public LoadTestSub(String testName, String loginName, String passwd, int numPublish, int burstModePublish)
   {
       super(testName);
       this.senderName = loginName;
       this.receiverName = loginName;
       this.passwd = passwd;
       this.numPublish = numPublish;
       this.burstModePublish = burstModePublish;
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
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
         Log.info(ME, "Connected to xmlBlaster, numPublish=" + numPublish + " burstModePublish=" + burstModePublish + " burstMode.collectTime=" + XmlBlasterProperty.get("burstMode.collectTime", 0L));
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
          assert(e.toString(), false);
      }

   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      long avg = 0;
      double elapsed = stopWatch.elapsed();
      if (elapsed > 0.)
         avg = (long)(1000.0 * numPublish / elapsed);
      Log.info(ME, numPublish + " messages updated, average messages/second = " + avg + stopWatch.nice());

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout();
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
         subscribeOid = senderConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
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
      Log.info(ME, "Publishing " + numPublish + " messages ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <LoadTestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <LoadTestSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </LoadTestSub-DRIVER>"+
                      "   </LoadTestSub-AGENT>" +
                      "</key>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit[] arr = new MessageUnit[burstModePublish];
      String[] publishOids;
      for (int kk=0; kk<burstModePublish; kk++)
         arr[kk] = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      stopWatch = new StopWatch();
      try {
         for (int ii=0; ii<numPublish; ) {
            for (int jj=0; jj<burstModePublish; jj++) {
               arr[jj].content = new String(someContent + (ii+1)).getBytes();
            }
            ii+=burstModePublish;
            publishOids = senderConnection.publishArr(arr);
            /*
            if (((ii+1) % 1) == 0)
               Log.info(ME, "Success: Publishing done: '" + senderContent + "'");
            */
         }
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numPublish / elapsed);
         Log.info(ME, "Success: Publishing done, " + numPublish + " messages sent, average messages/second = " + avg);
         //assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
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
      waitOnUpdate(60*1000L, numPublish);
      assertEquals("numReceived after publishing", numPublish, numReceived); // message arrived?
   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterConnection
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
      if (Log.CALL) Log.call(ME, "Receiving update of a message ...");

      numReceived++;
      if ((numReceived % 1000) == 0) {
         long avg = numReceived / (stopWatch.elapsed()/1000L);
         Log.info(ME, "Success: Update #" + numReceived + " received: '" + new String(content) + "', average messages/second = " + avg);
      }
      messageArrived = true;
      String currentContent = new String(content);
      int val = -1;
      if (lastContentNumber >= 0) {
         String number = currentContent.substring(someContent.length());
         try { val = new Integer(number).intValue(); } catch (NumberFormatException e) { Log.error(ME, e.toString()); }
         if (val <= lastContentNumber) {
            Log.error(ME, "lastContent=" + lastContentNumber + " currentContent=" + currentContent);
            //assert("Sequence of received message is broken", false);
         }
      }
      lastContentNumber = val;
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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
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
       int numMsg = 200;
       suite.addTest(new LoadTestSub("testManyPublish", loginName, "secret", numMsg, 200));
       return suite;
   }

   static void usage()
   {
      Log.plain("\nAvailable options:");
      Log.plain("   -name               The login name [Tim].");
      Log.plain("   -passwd             The login name [secret].");
      Log.plain("   -numPublish         Number of messages to send [5000].");
      Log.plain("   -burstMode.publish  Collect given number of messages when publishing [1].");
      XmlBlasterConnection.usage();
      Log.usage();
   }

   /**
    * Invoke: jaco testsuite.org.xmlBlaster.LoadTestSub
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * You can use the command line option -numPublish 5000 to change the number of messages sent.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.LoadTestSub</pre>
    */
   public static void main(String args[])
   {
      try {
         boolean showUsage = XmlBlasterProperty.init(args);
         if (showUsage) {
            usage();
            Log.exit(ME, "Example: java -Xms18M -Xmx32M testsuite.org.xmlBlaster.LoadTestSub -burstMode.publish 100 -burstMode.collectTime 100 -numPublish 5000 -client.protocol IOR");
         }
      } catch(org.jutils.JUtilsException e) {
         usage();
         Log.panic(ME, "Example: java -Xms18M -Xmx32M testsuite.org.xmlBlaster.LoadTestSub -burstMode.publish 100 -burstMode.collectTime 100 -numPublish 5000 -client.protocol IOR");
      }
      int numPublish = XmlBlasterProperty.get("numPublish", 5000);
      int burstModePublish = XmlBlasterProperty.get("burstMode.publish", 1);
      LoadTestSub testSub = new LoadTestSub("LoadTestSub", XmlBlasterProperty.get("name", "Tim"),
                                            XmlBlasterProperty.get("passwd", "secret"),
                                            numPublish, burstModePublish);
      testSub.setUp();
      testSub.testManyPublish();
      testSub.tearDown();
      Log.exit(LoadTestSub.ME, "Good bye");
   }
}

