/*------------------------------------------------------------------------------
Name:      RamTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.stress;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;

import junit.framework.*;


/**
 * This client publishes 1000 different messages to measure RAM consumption/message.
 * <br />
 * The RAM consumption in kByte/Message is logged to the console.
 * <br />
 * Note that this is the net RAM consumption, without any content and a very small XmlKey.
 * You may see this as the internal memory overhead in xmlBlaster for each published message.
 * <br />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.stress.RamTest
 *    java junit.swingui.TestRunner org.xmlBlaster.test.stress.RamTest
 * </pre>
 */
public class RamTest extends TestCase
{
   private final Global glob;
   private static Logger log = Logger.getLogger(RamTest.class.getName());
   private StopWatch stopWatch = null;

   private I_XmlBlasterAccess senderConnection;
   private String senderName;
   private String senderContent;

   private final int numPublish;        // 100;
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /** Constructor for Junit */
   public RamTest() {
      this(new Global(), "RamTest", "RamTest", 1000);
   }

   /**
    * Constructs the RamTest object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param numPublish The number of messages to send
    */
   public RamTest(Global glob, String testName, String loginName, int numPublish)
   {
      super(testName);
      this.glob = glob;

      this.senderName = loginName;
      this.numPublish = numPublish;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);
         senderConnection.connect(connectQos, null); // Login to xmlBlaster without Callback
      }
      catch (Exception e) {
          log.severe(e.toString());
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
      log.info("tearDown() ...");
      stopWatch = new StopWatch();

      for (int ii=0; ii<numPublish; ii++) {
         String xmlKey = "<key oid='RamTest-" + (ii+1) + "'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
            assertTrue("returned erased oid array == null", null != arr);
            assertEquals("num erased messages is wrong", 1, arr.length);
         } catch(XmlBlasterException e) { log.severe("XmlBlasterException: " + e.getMessage()); }
      }

      long avg = 0;
      double elapsed = stopWatch.elapsed();
      if (elapsed > 0.)
         avg = (long)(1000.0 * numPublish / elapsed);
      log.info("Success: Erasing done, " + numPublish + " messages erased, average messages/second = " + avg);

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void doPublish()
   {
      log.info("Publishing " + numPublish + " messages ...");

      long usedMemBefore = 0L;

      MsgUnit[] msgUnitArr = new MsgUnit[numPublish];

      try {
         for (int ii=0; ii<numPublish; ii++) {
            String xmlKey = "<key oid='RamTest-" + (ii+1) + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                            "   <RamTest-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                            "      <RamTest-DRIVER id='FileProof' pollingFreq='10'>" +
                            "      </RamTest-DRIVER>"+
                            "   </RamTest-AGENT>" +
                            "</key>";
            senderContent = "" + (ii+1);
            MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
            msgUnitArr[ii] = msgUnit;
         }


         // 1. Query the current memory allocated in xmlBlaster
         String xmlKey = "<key oid='__cmd:?usedMem' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MsgUnit[] msgArr = senderConnection.get(xmlKey, qos);

         assertTrue("returned msgArr == null", null != msgArr);
         assertEquals("msgArr.length!=1", 1, msgArr.length);
         assertTrue("returned msgArr[0].msgUnit == null", null != msgArr[0]);
         assertTrue("returned msgArr[0].msgUnit.content == null", null != msgArr[0].getContent());
         assertTrue("returned msgArr[0].msgUnit.content.length == 0", 0 != msgArr[0].getContent().length);
         String mem = new String(msgArr[0].getContent());
         usedMemBefore = new Long(mem).longValue();
         log.info("xmlBlaster used allocated memory before publishing = " + Global.byteString(usedMemBefore));


         stopWatch = new StopWatch();
         // 2. publish all the messages
         PublishReturnQos[] publishOidArr = senderConnection.publishArr(msgUnitArr);

         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numPublish / elapsed);
         log.info("Success: Publishing done, " + numPublish + " messages sent, average messages/second = " + avg);

         assertTrue("returned publishOidArr == null", null != publishOidArr);
         assertEquals("numPublished is wrong", numPublish, publishOidArr.length);


         // 3. Query the memory allocated in xmlBlaster after publishing all the messages
         msgArr = senderConnection.get(xmlKey, qos);
         long usedMemAfter = new Long(new String(msgArr[0].getContent())).longValue();
         log.info("xmlBlaster used allocated memory after publishing = " + Global.byteString(usedMemAfter));
         log.info("Consumed memory for each message = " + Global.byteString((usedMemAfter-usedMemBefore)/numPublish));

      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      } catch(Exception e) {
         log.warning("Exception: " + e.toString());
         e.printStackTrace();
         assertTrue("get or publish - Exception: " + e.toString(), false);
      }
   }


   /**
    * TEST: Construct 1000 messages and publish it.
    */
   public void testManyPublish()
   {
      doPublish();
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       int numMsg = 100;
       suite.addTest(new RamTest(new Global(), "testManyPublish", loginName, numMsg));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.stress.RamTest
    * <br />
    * You can use the command line option -numPublish 1000 to change the number of messages sent.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.stress.RamTest</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global(args);
      int numPublish = glob.getProperty().get("numPublish", 1000);
      RamTest testSub = new RamTest(glob, "RamTest", "Tim", numPublish);
      testSub.setUp();
      testSub.testManyPublish();
      testSub.tearDown();
   }
}

