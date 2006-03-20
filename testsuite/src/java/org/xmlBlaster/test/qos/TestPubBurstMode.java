/*------------------------------------------------------------------------------
Name:      TestPubBurstMode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.xmlBlaster.util.StopWatch;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the method publish() in burst mode, messages are
 * collected and sent in one method call. 
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *  java org.xmlBlaster.test.qos.TestPubBurstMode -numPublish 10000 -dispatch/connection/protocol RMI -warn false
 *  java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPubBurstMode
 *  java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestPubBurstMode
 * </pre>
 */
public class TestPubBurstMode extends TestCase
{
   private static String ME = "TestPubBurstMode";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPubBurstMode.class.getName());

   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "AMessage";
   private I_XmlBlasterAccess senderConnection;
   private String senderName;

   private int numPublish;
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";
   
   private StopWatch stopWatch = new StopWatch();


   /**
    * Constructs the TestPubBurstMode object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPubBurstMode(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;

      this.senderName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         numPublish = glob.getProperty().get("numPublish", 100);
         senderConnection = glob.getXmlBlasterAccess();   // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);
         senderConnection.connect(connectQos, null); // Login to xmlBlaster
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }


   /**
    * TEST: Construct a message and publish it.
    */
   public void testPublish()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing messages ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "   <TestPubBurstMode-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestPubBurstMode-AGENT>" +
                      "</key>";
      PublishQos qosWrapper = new PublishQos(glob);
      String qos = qosWrapper.toXml(); // == "<qos></qos>"

      MsgUnit[] msgUnitArr = new MsgUnit[numPublish];
      try {
         for (int ii=0; ii<numPublish; ii++) {
            String senderContent = ME + ii; // different content forcing xmlBlaster to store
            msgUnitArr[ii] = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         }
      }
      catch (XmlBlasterException e) {
         fail(e.getMessage());
      }

      PublishReturnQos[] publishOidArr = null;
      try {
         log.info("Publishing " + numPublish + " messages in burst mode ...");
         stopWatch = new StopWatch();
         publishOidArr = senderConnection.publishArr(msgUnitArr);
         double elapsed = (double)stopWatch.elapsed()/1000.; // msec -> sec
         log.info("Published " + numPublish + " messages in burst mode in " + elapsed + " sec: " +
                       (long)(numPublish/elapsed) + " messages/sec");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      assertTrue("returned publishOidArr == null", publishOidArr != null);
      assertEquals("returned publishOidArr", numPublish, publishOidArr.length);
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishMany()
   {
      testPublish();
      System.out.println("");
      testPublish();
      System.out.println("");
      testPublish();
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestPubBurstMode(new Global(), "testPublishMany", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestPubBurstMode
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPubBurstMode</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPubBurstMode testPub = new TestPubBurstMode(glob, "TestPubBurstMode", "Tim");
      testPub.setUp();
      testPub.testPublishMany();
      testPub.tearDown();
   }
}

