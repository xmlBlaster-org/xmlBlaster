/*------------------------------------------------------------------------------
Name:      TestGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestGet.java,v 1.30 2002/09/12 12:04:09 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.GetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client tests the synchronous method get() with its different qos variants.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestGet
 *    java junit.swingui.TestRunner testsuite.org.xmlBlaster.TestGet
 * </pre>
 */
public class TestGet extends TestCase
{
   private static String ME = "TestGet";
   private final Global glob;
   private final LogChannel log;

   private String publishOid = "TestGet";
   private XmlBlasterConnection connection;
   private String loginName;
   private String senderContent = "A test message";

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   /**
    * Constructs the TestGet object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestGet(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog("test");
      this.loginName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         connection = new XmlBlasterConnection(glob); // Find orb
         String passwd = "secret";
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         // Login to xmlBlaster, don't create a callback server
         connection.login(loginName, passwd, qos);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
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
      connection.disconnect(null);
      // Give the server some millis to finish the iiop handshake ...
      try { Thread.currentThread().sleep(200); } catch (InterruptedException e) {}
      log.info(ME, "Success, logged out");
   }


   /**
    * TEST: Get an not existing and an existing message
    * <p />
    * The returned content is checked
    */
   public void testGet()
   {
      if (log.TRACE) log.trace(ME, "1. Get a not existing message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MessageUnit[] msgArr = connection.get(xmlKey, qos);
         if (msgArr.length > 0) {
            log.error(ME, "Received " + msgArr.length + " unexpected messages");
            for (int i=0; i<msgArr.length; i++) {
               log.error(ME, "Wrong message is " + msgArr[i].toXml());
            }
            assertTrue("get of not existing message is not possible", false);
         }
         else
            log.info(ME, "Success, got zero messages when trying to get unknown message");
      } catch(XmlBlasterException e) {
         log.error(ME, "get of not existing message should not throw an exception");
         System.exit(1);
         assertTrue("get of not existing message should not throw an exception", false);
         //log.info(ME, "Success, got XmlBlasterException for trying to get unknown message: " + e.reason);
      }

      if (log.TRACE) log.trace(ME, "2. Publish a message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n</key>";
         PublishQosWrapper qosWrapper = new PublishQosWrapper(); // the same as "<qos></qos>"
         MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qosWrapper.toXml());
         connection.publish(msgUnit);
         log.info(ME, "Success, published a message");
      } catch(XmlBlasterException e) {
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      if (log.TRACE) log.trace(ME, "3. Get an existing message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MessageUnit[] msgArr = connection.get(xmlKey, qos);
         
         assertEquals("Got wrong number of messages", 1, msgArr.length);
         log.info(ME, "Success, got the message '" + msgArr[0].getXmlKey() + "'");
         if (log.DUMP) log.dump(ME, msgArr[0].toXml());

         GetQos getQos = new GetQos(msgArr[0].qos);
         assertEquals("Sender is corrupted", loginName, getQos.getSender());
         log.info(ME, "Get success from sender " + getQos.getSender());
         
         assertEquals("Corrupted content", senderContent, new String(msgArr[0].content));
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException for trying to get a message: " + e.reason);
         assertTrue("Couldn't get() an existing message", false);
      }

      // TODO: We should use the helpers GetKeyWrapper and GetQosWrapper:
      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseRetQos[] arr = connection.erase(xmlKey, qos);
         if (arr.length != 1) {
            log.error(ME, "Erased " + arr.length + " messages:");
            fail("Message " + publishOid + " was not erased");
         }
         log.info(ME, "Success, erased a message");
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.reason); }
   }


   /**
    * LOAD TEST: get 200 times a not existing message
    */
   public void testGetMany()
   {
      int num = glob.getProperty().get("numTries", 5);
      log.info(ME, "Get " + num + " not existing messages ...");
      String xmlKey = "<key oid='NotExistingMessage' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      for (int ii=0; ii<num; ii++) {
         try {
            MessageUnit[] msgArr = connection.get(xmlKey, qos);
            if (msgArr.length > 0)
               assertTrue("get() of not existing message is not possible", false);
         } catch(XmlBlasterException e) {
            assertTrue("get() of not existing message should not throw an Exception: " + e.toString(), false);
            // log.info(ME, "Success, got XmlBlasterException for trying to get unknown message: " + e.reason);
         }
      }
      log.info(ME, "Get " + num + " not existing messages done");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       Global glob = new Global();
       suite.addTest(new TestGet(glob, "testGet", loginName));
       suite.addTest(new TestGet(glob, "testGetMany", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestGet
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestGet</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + " Init failed");
         System.exit(1);
      }
      TestGet testSub = new TestGet(glob, "TestGet", "Tim");
      testSub.setUp();
      testSub.testGet();
      testSub.testGetMany();
      testSub.tearDown();
   }
}

