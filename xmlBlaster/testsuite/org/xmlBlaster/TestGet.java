/*------------------------------------------------------------------------------
Name:      TestGet.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestGet.java,v 1.8 2000/06/13 13:04:03 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import test.framework.*;


/**
 * This client tests the synchronous method get() with its different qos variants.
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestGet
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestGet
 * </pre>
 */
public class TestGet extends TestCase
{
   private static String ME = "Tim";

   private String publishOid = "TestGet";
   private CorbaConnection corbaConnection;
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
   public TestGet(String testName, String loginName)
   {
       super(testName);
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
         corbaConnection = new CorbaConnection(); // Find orb
         String passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         corbaConnection.login(loginName, passwd, qos); // Login to xmlBlaster
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
      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = corbaConnection.erase(xmlKey, qos);
         Log.info(ME, "Success, erased a message");
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      corbaConnection.logout();
      // Give the server some millis to finish the iiop handshake ...
      try { Thread.currentThread().sleep(200); } catch (InterruptedException e) {}
      Log.info(ME, "Success, logged out");
   }


   /**
    * TEST: Get an not existing and an existing message
    * <p />
    * The returned content is checked
    */
   public void testGet()
   {
      if (Log.TRACE) Log.trace(ME, "1. Get a not existing message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MessageUnitContainer[] msgArr = corbaConnection.get(xmlKey, qos);
         assert("get of not existing message is not possible", false);
      } catch(XmlBlasterException e) {
         Log.info(ME, "Success, got XmlBlasterException for trying to get unknown message: " + e.reason);
      }

      if (Log.TRACE) Log.trace(ME, "2. Publish a message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n</key>";
         MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes());
         PublishQosWrapper qosWrapper = new PublishQosWrapper(); // the same as "<qos></qos>"
         corbaConnection.publish(msgUnit, qosWrapper.toXml());
         Log.info(ME, "Success, published a message");
      } catch(XmlBlasterException e) {
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      if (Log.TRACE) Log.trace(ME, "3. Get an existing message ...");
      try {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'></key>";
         String qos = "<qos></qos>";
         MessageUnitContainer[] msgArr = corbaConnection.get(xmlKey, qos);
         Log.info(ME, "Success, got the message");
         assertEquals("Corrupted content", senderContent, new String(msgArr[0].msgUnit.content));
      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException for trying to get a message: " + e.reason);
         assert("Couldn't get() an existing message", false);
      }
   }


   /**
    * LOAD TEST: get 200 times a not existing message
    */
   public void testGetMany()
   {
      int num = 200;
      Log.info(ME, "Get " + num + " not existing messages ...");
      String xmlKey = "<key oid='NotExistingMessage' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      for (int ii=0; ii<num; ii++) {
         try {
            MessageUnitContainer[] msgArr = corbaConnection.get(xmlKey, qos);
            assert("get of not existing message is not possible", false);
         } catch(XmlBlasterException e) {
            // Log.info(ME, "Success, got XmlBlasterException for trying to get unknown message: " + e.reason);
         }
      }
      Log.info(ME, "Get " + num + " not existing messages done");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestGet("testGet", loginName));
       suite.addTest(new TestGet("testGetMany", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestGet
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestGet</pre>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestGet testSub = new TestGet("TestGet", "Tim");
      testSub.setUp();
      testSub.testGet();
      testSub.testGetMany();
      testSub.tearDown();
      Log.exit(TestGet.ME, "Good bye");
   }
}

