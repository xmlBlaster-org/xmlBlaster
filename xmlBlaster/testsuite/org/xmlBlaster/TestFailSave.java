/*------------------------------------------------------------------------------
Name:      TestFailSave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSave.java,v 1.20 2000/06/20 13:32:58 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.Log;

import org.xmlBlaster.client.*;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnit;
import org.xmlBlaster.protocol.corba.serverIdl.MessageUnitContainer;
import test.framework.*;


/**
 * Tests the fail save behavior of the CorbaConnection client helper class.
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of CorbaConnection.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSave
 *   jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestFailSave
 * </pre>
 */
public class TestFailSave extends TestCase implements I_Callback, I_ConnectionProblems
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private int serverPort = 7604;
   private ServerThread serverThread;

   private CorbaConnection corbaConnection;
   private String senderName;

   private int numReceived = 0;         // error checking
   private int numPublish = 8;
   private int numStop = 3;
   private final String contentMime = "text/plain";

   /**
    * Constructs the TestFailSave object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestFailSave(String testName, String loginName)
   {
       super(testName);
       this.senderName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      serverThread = ServerThread.startXmlBlaster(serverPort);
      Log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);
      try {
         numReceived = 0;

         String[] args = new String[2];
         args[0] = "-iorPort";
         args[1] = "" + serverPort;
         corbaConnection = new CorbaConnection(args); // Find orb

         // Setup fail save handling ...
         long retryInterval = 4000L; // XmlBlasterProperty.get("Failsave.retryInterval", 4000L);
         int retries = -1;           // -1 == forever
         int maxMessages = 1000;
         long pingInterval = 0L;     // switched off
         corbaConnection.initFailSave(this, retryInterval, retries, maxMessages, pingInterval);

         // and do the login ...
         String passwd = "secret";
         LoginQosWrapper qos = new LoginQosWrapper(); // == "<qos></qos>";
         corbaConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
          Log.warning(ME, "setUp() - login failed");
      }
      catch (Exception e) {
          Log.error(ME, "setUp() - login failed: " + e.toString());
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
      Log.info(ME, "Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = corbaConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      assertEquals("Wrong number of message erased", strArr.length, (numPublish - numStop));

      Util.delay(500L);    // Wait some time
      corbaConnection.logout();

      Util.delay(500L);    // Wait some time
      ServerThread.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      String[] args = new String[2];
      args[0] = "-iorPort";
      args[1] = "" + org.xmlBlaster.protocol.corba.CorbaDriver.DEFAULT_HTTP_PORT;
      try {
         XmlBlasterProperty.addArgs2Props(args);
      } catch(org.jutils.JUtilsException e) {
         assert(e.toString(), false);
      }
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void testSubscribe()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String subscribeOid = null;
      try {
         subscribeOid = corbaConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warning(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscribeOid", subscribeOid != null);
      // NOT FOR FAIL SAVE: assertNotEquals("returned subscribeOid is empty", 0, subscribeOid.length());
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void testPublish(int counter) throws XmlBlasterException
   {
      String oid = "Message" + "-" + counter;
      Log.info(ME, "Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSave-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSave-AGENT>" +
                      "</key>";
      String content = "" + counter;
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes());
      PublishQosWrapper qosWrapper = new PublishQosWrapper(); // == "<qos></qos>"

      corbaConnection.publish(msgUnit, qosWrapper.toXml());
      Log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSave()
   {
      testSubscribe();
      Log.info(ME, "Going to publish " + numPublish + " messages, xmlBlaster will be down for message 3 and 4");
      for (int ii=0; ii<numPublish; ii++) {
         try {
            if (ii == numStop) { // 3
               Log.info(ME, "Stopping xmlBlaster, but continue with publishing ...");
               ServerThread.stopXmlBlaster(serverThread);
            }
            if (ii == 5) {
               Log.info(ME, "Starting xmlBlaster again, expecting the previous published messages ...");
               serverThread = ServerThread.startXmlBlaster(serverPort);
               waitOnUpdate(8000L);
            }
            testPublish(ii+1);
            waitOnUpdate(2000L);
            //assertEquals("numReceived after publishing", ii+1, numReceived); // message arrived?
         }
         catch(XmlBlasterException e) {
            if (e.id.equals("TryingReconnect"))
               Log.warning(ME, e.id + " exception: Lost connection, my connection layer is polling");
            else if (e.id.equals("NoConnect"))
               assert("Lost connection, my connection layer is NOT polling", false);
            else
               assert("Publishing problems id=" + e.id + ": " + e.reason, false);
         }
         //Util.delay(3000L);    // Wait some time
      }

      assertEquals("numReceived is wrong", numPublish, numReceived);
   }


   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void reConnected()
   {
      Log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to xmlBlaster");
      testSubscribe();    // initialize subscription again
      try {
         corbaConnection.flushQueue();    // send all tailback messages
         // corbaConnection.resetQueue(); // or discard them (it is our choice)
      } catch (XmlBlasterException e) {
         assert("Exception during reconnection recovery: " + e.reason, false);
      }
   }


   /**
    * This is the callback method invoked from CorbaConnection
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void lostConnection()
   {
      Log.warning(ME, "I_ConnectionProblems: Lost connection to xmlBlaster");
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
      Log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message" + "-" + numReceived;
      assertEquals("Message oid is wrong", oid, updateKey.getUniqueKey());

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
            Log.info(ME, "Timeout of " + timeout + " occurred");
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
       suite.addTest(new TestFailSave("testFailSave", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestFailSave
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSave</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestFailSave testSub = new TestFailSave("TestFailSave", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
      Log.exit(TestFailSave.ME, "Good bye");
   }
}

