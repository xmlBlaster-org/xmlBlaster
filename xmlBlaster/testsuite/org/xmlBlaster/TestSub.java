/*------------------------------------------------------------------------------
Name:      TestSub.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSub.java,v 1.6 1999/12/12 18:21:41 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import test.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestSub
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestSub
 * </code>
 */
public class TestSub extends TestCase
{
   private Server xmlBlaster = null;
   private static String ME = "Tim";
   private String[] args;
   private boolean messageArrived = false;

   private String subscribeOid;
   private String publishOid = "";
   private CorbaConnection senderConnection;
   private String senderName;

   private int numReceived = 0;         // error checking

   /**
    * Constructs the TestSub object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param args      Array of command line start parameters
    */
   public TestSub(String testName, String loginName, String[] args)
   {
       super(testName);
       this.senderName = loginName;
       this.args = args;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = new CorbaConnection(args); // Find orb

         // Building a Callback server
         BlasterCallback callback = senderConnection.createCallbackServer(new TestSubCallback(ME, this));

         String qos = "<qos></qos>";
         String passwd = "some";
         xmlBlaster = senderConnection.login(senderName, passwd, callback, qos);  // Login to xmlBlaster
      }
      catch (Exception e) {
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
                      "   //TestSub-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      numReceived = 0;
      subscribeOid = null;
      try {
         subscribeOid = xmlBlaster.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
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
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                        "<key oid='' contentMime='text/xml'>\n" +
                        "   <TestSub-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                        "      <TestSub-DRIVER id='FileProof' pollingFreq='10'>" +
                        "      </TestSub-DRIVER>"+
                        "   </TestSub-AGENT>" +
                        "</key>";
      String content = "Yeahh, i'm the new content";
      MessageUnit messageUnit = new MessageUnit(xmlKey, content.getBytes());
      try {
         publishOid = xmlBlaster.publish(messageUnit, "<qos></qos>");
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      assert("returned publishOid == null", publishOid != null);
      assertNotEquals("returned publishOid", 0, publishOid.length());
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
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
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();

       String[] args = new String[0];  // dummy
       String loginName = "Tim";

       suite.addTest(new TestSub("testPublishAfterSubscribeXPath", loginName, args));

       return suite;
   }


   /**
    * The TestSubCallback.update calls this method, to allow some error checking. 
    * @param messageUnitArr   Contains a sequence of 0 - n MessageUnit structs
    * @param qos_literal_Arr  Quality of Service for each MessageUnit
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of " + messageUnitArr.length + " message ...");

      if (messageUnitArr.length != 0)
         numReceived += messageUnitArr.length;
      else
         numReceived = -1;       // error


      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         String keyOid = null;
         byte[] content = messageUnit.content;
         try {
            updateKey = new UpdateKey(messageUnit.xmlKey);
            keyOid = updateKey.getUniqueKey();
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it or do some checks
         Log.plain("UpdateKey", updateKey.printOn().toString());
         Log.plain("content", (new String(content)).toString());
         Log.plain("UpdateQoS", updateQoS.printOn().toString());
         Log.info(ME, "Received message [" + keyOid + "] from publisher " + updateQoS.getSender());
      }

      messageArrived = true;
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
            Log.warning(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived = false;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestSub
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSub</code>
    */
   public static void main(String args[])
   {
      // check if parameter -name <userName> is given at startup of client
      String senderName = Args.getArg(args, "-name", TestSub.ME);
      TestSub testSub = new TestSub("TestSub", senderName, args);
      testSub.setUp();
      testSub.testPublishAfterSubscribeXPath();
      testSub.tearDown();
      Log.exit(TestSub.ME, "Good bye");
   }
}


/**
 * Example for a callback implementation.
 */
class TestSubCallback implements BlasterCallbackOperations
{
   private final String ME;
   private final TestSub boss;

   /**
    * Construct a persistently named object.
    */
   public TestSubCallback(java.lang.String name, TestSub boss)
   {
      this.ME = "TestSubCallback-" + name;
      this.boss = boss;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages
    * @param messageUnitArr   Contains a sequence of 0 - n MessageUnit structs
    * @param qos_literal_Arr  Quality of Service for each MessageUnit
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      boss.update(messageUnitArr, qos_literal_Arr); // Call my boss, so she can check for errors
   }
} // TestSubCallback

