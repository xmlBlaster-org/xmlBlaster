/*------------------------------------------------------------------------------
Name:      RamTest.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Load test for xmlBlaster
Version:   $Id: RamTest.java,v 1.15 2000/10/29 20:25:33 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.time.StopWatch;
import org.jutils.runtime.Memory;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;

import test.framework.*;


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
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.RamTest
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.RamTest
 * </pre>
 */
public class RamTest extends TestCase
{
   private static String ME = "Tim";
   private StopWatch stopWatch = null;

   private String publishOid = "";
   private XmlBlasterConnection senderConnection;
   private String senderName;
   private String senderContent;

   private final int numPublish;        // 100;
   private final String contentMime = "text/plain";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the RamTest object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param numPublish The number of messages to send
    */
   public RamTest(String testName, String loginName, int numPublish)
   {
       super(testName);
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
         senderConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null); // Login to xmlBlaster without Callback
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
      Log.info(ME, "tearDown() ...");
      stopWatch = new StopWatch();

      for (int ii=0; ii<numPublish; ii++) {
         String xmlKey = "<key oid='RamTest-" + (ii+1) + "'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = senderConnection.erase(xmlKey, qos);
            assertNotEquals("returned erased oid array == null", null, strArr);
            assertEquals("num erased messages is wrong", 1, strArr.length);
         } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      }

      long avg = 0;
      double elapsed = stopWatch.elapsed();
      if (elapsed > 0.)
         avg = (long)(1000.0 * numPublish / elapsed);
      Log.info(ME, "Success: Erasing done, " + numPublish + " messages erased, average messages/second = " + avg);

      senderConnection.logout();
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void testPublish()
   {
      Log.info(ME, "Publishing " + numPublish + " messages ...");

      long usedMemBefore = 0L;

      MessageUnit[] msgUnitArr = new MessageUnit[numPublish];
      for (int ii=0; ii<numPublish; ii++) {
         String xmlKey = "<key oid='RamTest-" + (ii+1) + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                         "   <RamTest-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                         "      <RamTest-DRIVER id='FileProof' pollingFreq='10'>" +
                         "      </RamTest-DRIVER>"+
                         "   </RamTest-AGENT>" +
                         "</key>";
         senderContent = "" + (ii+1);
         MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         msgUnitArr[ii] = msgUnit;
      }

      try {
         // 1. Query the current memory allocated in xmlBlaster
         String xmlKey = "<key oid='__sys__UsedMem' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MessageUnit[] msgArr = senderConnection.get(xmlKey, qos);

         assertNotEquals("returned msgArr == null", null, msgArr);
         assertEquals("msgArr.length!=1", 1, msgArr.length);
         assertNotEquals("returned msgArr[0].msgUnit == null", null, msgArr[0]);
         assertNotEquals("returned msgArr[0].msgUnit.content == null", null, msgArr[0].content);
         assertNotEquals("returned msgArr[0].msgUnit.content.length == 0", 0, msgArr[0].content.length);
         String mem = new String(msgArr[0].content);
         usedMemBefore = new Long(mem).longValue();
         Log.info(ME, "xmlBlaster used allocated memory before publishing = " + Memory.byteString(usedMemBefore));


         stopWatch = new StopWatch();
         // 2. publish all the messages
         String[] publishOidArr = senderConnection.publishArr(msgUnitArr);

         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numPublish / elapsed);
         Log.info(ME, "Success: Publishing done, " + numPublish + " messages sent, average messages/second = " + avg);

         assertNotEquals("returned publishOidArr == null", null, publishOidArr);
         assertEquals("numPublished is wrong", numPublish, publishOidArr.length);


         // 3. Query the memory allocated in xmlBlaster after publishing all the messages
         msgArr = senderConnection.get(xmlKey, qos);
         long usedMemAfter = new Long(new String(msgArr[0].content)).longValue();
         Log.info(ME, "xmlBlaster used allocated memory after publishing = " + Memory.byteString(usedMemAfter));
         Log.info(ME, "Consumed memory for each message = " + Memory.byteString((usedMemAfter-usedMemBefore)/numPublish));

      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      } catch(Exception e) {
         Log.warn(ME, "Exception: " + e.toString());
         e.printStackTrace();
         assert("get or publish - Exception: " + e.toString(), false);
      }
   }


   /**
    * TEST: Construct 1000 messages and publish it.
    */
   public void testManyPublish()
   {
      testPublish();
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       int numMsg = 100;
       suite.addTest(new RamTest("testManyPublish", loginName, numMsg));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.RamTest
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * You can use the command line option -numPublish 1000 to change the number of messages sent.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.RamTest</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      int numPublish = XmlBlasterProperty.get("numPublish", 1000);
      RamTest testSub = new RamTest("RamTest", "Tim", numPublish);
      testSub.setUp();
      testSub.testManyPublish();
      testSub.tearDown();
      Log.exit(RamTest.ME, "Good bye");
   }
}

