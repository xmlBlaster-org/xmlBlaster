/*------------------------------------------------------------------------------
Name:      LoadTestSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: LoadTestSub.java,v 1.7 2002/12/18 13:16:21 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.stress;

import org.jutils.time.StopWatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_Callback;

import junit.framework.*;


/**
 * This client does a subscribe() with many publish() calls.<br />
 * The same message is published 1000 times, to measure messages/second performance.
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.stress.LoadTestSub
 *    java junit.swingui.TestRunner org.xmlBlaster.test.stress.LoadTestSub
 * </pre>
 */
public class LoadTestSub extends TestCase implements I_Callback
{
   private static String ME = "LoadTestSub";
   private boolean messageArrived = false;
   private StopWatch stopWatch = null;
   private Global glob;
   private LogChannel log;

   private String subscribeOid;
   private String publishOid = "LoadTestSub";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String receiverName;         // sender/receiver is here the same client
   private String passwd;

   private final int numPublish;        // 200;
   private int numReceived = 0;         // error checking
   private int burstModePublish = 1;
   private boolean publishOneway = false;
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";
   private int lastContentNumber = -1;
   private final String someContent = "Yeahh, i'm the new content, my total length is big an bigger but still i want to be longer and longer until i have reached some 180 bytes, here is remaining blahh to fill the last";

   public LoadTestSub() { // JUNIT
      this(new Global(), "LoadTestSub", "LoadTestSub", "secret", 1000, 1, false);
   }
   
   /**
    * Constructs the LoadTestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param numPublish The number of messages to send
    * @param burstModePublish send given number of publish messages in one bulk
    */
   public LoadTestSub(Global glob, String testName, String loginName, String passwd, int numPublish, int burstModePublish, boolean publishOneway)
   {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog("test");
       this.senderName = loginName;
       this.receiverName = loginName;
       this.passwd = passwd;
       this.numPublish = numPublish;
       this.burstModePublish = burstModePublish;
       this.publishOneway = publishOneway;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = new XmlBlasterConnection(glob);
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
         if (burstModePublish > numPublish)
            burstModePublish = numPublish;
         if ((numPublish % burstModePublish) != 0)
            log.error(ME, "numPublish should by dividable by publish.burstMode");
         log.info(ME, "Connected to xmlBlaster, numPublish=" + numPublish + " burstModePublish=" + burstModePublish + " cb.burstMode.collectTime=" + glob.getProperty().get("cb.burstMode.collectTime", 0L));
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
          assertTrue(e.toString(), false);
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
      log.info(ME, numPublish + " messages updated, average messages/second = " + avg + stopWatch.nice());

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         if (arr.length != 1) log.error(ME, "Erased " + arr.length + " messages:");
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void doSubscribeXPath()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using XPath syntax ...");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='' queryType='XPATH'>\n" +
                      "   //LoadTestSub-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = senderConnection.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         // assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void doPublish()
   {
      log.info(ME, "Publishing " + numPublish + " messages ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <LoadTestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "      <LoadTestSub-DRIVER id='FileProof' pollingFreq='10'>" +
                      "      </LoadTestSub-DRIVER>"+
                      "   </LoadTestSub-AGENT>" +
                      "</key>";
      String qos = "";
      //String qos = "<qos><isDurable>false</isDurable></qos>";
      //String qos = "<qos><isDurable>true</isDurable></qos>";

      MsgUnit[] arr = new MsgUnit[burstModePublish];
      PublishReturnQos[] publishOids;
      try {
         for (int kk=0; kk<burstModePublish; kk++)
            arr[kk] = new MsgUnit(xmlKey, someContent.getBytes(), qos);
      }
      catch (XmlBlasterException e) {
         fail(e.getMessage());
      }
      stopWatch = new StopWatch();
      try {
         for (int ii=0; ii<numPublish; ) {
            for (int jj=0; jj<burstModePublish; jj++) {
               arr[jj] = new MsgUnit(arr[jj], null, new String(someContent + (ii+1)).getBytes(), null);
            }
            ii+=burstModePublish;
            if (publishOneway)
               senderConnection.publishOneway(arr);
            else
               publishOids = senderConnection.publishArr(arr);
            /*
            if (((ii+1) % 1) == 0)
               log.info(ME, "Success: Publishing done: '" + someContent + "'");
            */
         }
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numPublish / elapsed);
         log.info(ME, "Success: Publishing done, " + numPublish + " messages sent, average messages/second = " + avg);
         //assertEquals("oid is different", oid, publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      // assertTrue("returned publishOid == null", publishOid != null);
      // assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testManyPublish()
   {
      doSubscribeXPath();
      doPublish();
      waitOnUpdate(60*1000L, numPublish);
      assertEquals("numReceived after publishing", numPublish, numReceived); // message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");

      numReceived++;
      if ((numReceived % 1000) == 0) {
         long avg = (long)((double)numReceived / (double)(stopWatch.elapsed()/1000.));
         String contentStr = new String(content);
         String tok = "... " + contentStr.substring(contentStr.length() - 10);
         log.info(ME, "Success: Update #" + numReceived + " received: '" + tok + "', average messages/second = " + avg);
      }
      messageArrived = true;
      String currentContent = new String(content);
      int val = -1;
      if (lastContentNumber >= 0) {
         String number = currentContent.substring(someContent.length());
         try { val = new Integer(number).intValue(); } catch (NumberFormatException e) { log.error(ME, e.toString()); }
         if (val <= lastContentNumber) {
            log.error(ME, "lastContent=" + lastContentNumber + " currentContent=" + currentContent);
            //assertTrue("Sequence of received message is broken", false);
         }
      }
      lastContentNumber = val;
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
      while (numReceived < numWait) {
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
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       int numMsg = 200;
       suite.addTest(new LoadTestSub(new Global(), "testManyPublish", loginName, "secret", numMsg, 200, false));
       return suite;
   }

   static void usage()
   {
      System.out.println("\nAvailable options:");
      System.out.println("   -numPublish         Number of messages to send [5000].");
      System.out.println("   -publish.burstMode  Collect given number of messages when publishing [1].");
      System.out.println("   -publish.oneway     Send messages oneway (publish does not receive return value) [false].");
      XmlBlasterConnection.usage();
   }

   /**
    * Invoke: java org.xmlBlaster.test.stress.LoadTestSub
    * <br />
    * You can use the command line option -numPublish 5000 to change the number of messages sent.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.stress.LoadTestSub</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      int ret = glob.init(args);
      if (ret != 0) {
         usage();
         System.out.println("Oneway Example: java -Xms18M -Xmx32M org.xmlBlaster.test.qos.LoadTestSub -publish.oneway true -burstMode.collectTimeOneway 500 -cb.oneway true -cb.burstMode.collectTime 200 -numPublish 5000 -client.protocol IOR");
         System.out.println("Syn    Example: java -Xms18M -Xmx32M org.xmlBlaster.test.qos.LoadTestSub -publish.oneway false -cb.oneway false -publish.burstMode 200 -cb.burstMode.collectTime 200 -numPublish 5000 -client.protocol IOR");
         System.exit(1);
      }

      int numPublish = glob.getProperty().get("numPublish", 5000);
      int burstModePublish = glob.getProperty().get("publish.burstMode", 1);
      boolean publishOneway = glob.getProperty().get("publish.oneway", false);

      LoadTestSub testSub = new LoadTestSub(glob, "LoadTestSub", glob.getProperty().get("name", "Tim"),
                                            glob.getProperty().get("passwd", "secret"),
                                            numPublish, burstModePublish, publishOneway);
      testSub.setUp();
      testSub.testManyPublish();
      System.out.println("Success, hit a key to logout and exit");
      try { System.in.read(); } catch(java.io.IOException e) {}
      testSub.tearDown();
   }
}

