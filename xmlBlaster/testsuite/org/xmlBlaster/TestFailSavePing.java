/*------------------------------------------------------------------------------
Name:      TestFailSavePing.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSavePing.java,v 1.14 2001/09/05 12:48:47 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Property;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ServerThread;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;

import test.framework.*;


/**
 * Tests the fail save behavior of the XmlBlasterConnection client helper class,
 * especially the pinging to xmlBlaster. This allows auto detection if the
 * connection to xmlBlaster is lost.
 *
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of XmlBlasterConnection.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing
 *   jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing
 * </pre>
 */
public class TestFailSavePing extends TestCase implements I_Callback, I_ConnectionProblems
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private int serverPort = 7604;
   private ServerThread serverThread;

   private XmlBlasterConnection corbaConnection;
   private String senderName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";

   /**
    * Constructs the TestFailSavePing object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestFailSavePing(String testName, String loginName)
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
      try {
         numReceived = 0;

         String[] args = new String[2];
         args[0] = "-iorPort";
         args[1] = "" + serverPort;
         corbaConnection = new XmlBlasterConnection(args); // Find orb

         // Setup fail save handling ...
         long retryInterval = 4000L; // Property.getProperty("Failsave.retryInterval", 4000L);
         int retries = -1;           // -1 == forever
         int maxMessages = 1000;
         long pingInterval = 1000L;
         corbaConnection.initFailSave(this, retryInterval, retries, maxMessages, pingInterval);

         // and do the login ...
         String passwd = "secret";
         ConnectQos qos = new ConnectQos(); // == "<qos></qos>";
         corbaConnection.login(senderName, passwd, qos, this); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
          Log.warn(ME, "setUp() - login failed");
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
                      "   //TestFailSavePing-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = corbaConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { assert("tearDown - XmlBlasterException: " + e.reason, false); }
      assertEquals("Wrong number of message erased", 1, strArr.length);

      Util.delay(500L);    // Wait some time
      corbaConnection.logout();

      Util.delay(200L);    // Wait some time
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
                      "   //TestFailSavePing-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String subscribeOid = null;
      try {
         subscribeOid = corbaConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
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
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      String oid = "Message" + "-" + counter;
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSavePing-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSavePing-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQosWrapper qosWrapper = new PublishQosWrapper(); // == "<qos></qos>"
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      corbaConnection.publish(msgUnit);
      Log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSave()
   {
      testSubscribe();
      Util.delay(200L);
      ServerThread.stopXmlBlaster(serverThread);
      Util.delay(3000L);    // Wait some time, ping should activate login polling

      serverThread = ServerThread.startXmlBlaster(serverPort);
      Util.delay(3000L);    // Wait some time, to allow the ping to reconnect

      numReceived = 0;

      ServerThread.stopXmlBlaster(serverThread);
      Util.delay(5000L);    // Wait some time, ping should activate login polling

      serverThread = ServerThread.startXmlBlaster(serverPort);
      Util.delay(3000L);    // Wait some time, to allow the ping to reconnect
   }


   /**
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void reConnected()
   {
      Log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to xmlBlaster");
      testSubscribe();    // initialize subscription again
      try {
         testPublish(1);
         waitOnUpdate(2000L);
         assertEquals("numReceived is wrong", 1, numReceived);
      }
      catch(XmlBlasterException e) {
         if (e.id.equals("TryingReconnect"))
            Log.warn(ME, e.id + " exception: Lost connection, my connection layer is polling");
         else if (e.id.equals("NoConnect"))
            assert("Lost connection, my connection layer is not polling", false);
         else
            assert("Publishing problems id=" + e.id + ": " + e.reason, false);
      }

      corbaConnection.resetQueue(); // discard messages (dummy)
   }


   /**
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void lostConnection()
   {
      Log.warn(ME, "I_ConnectionProblems: Lost connection to xmlBlaster");
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
      Log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQoS.getSender());
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message-1";
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
       suite.addTest(new TestFailSavePing("testFailSave", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestFailSavePing
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestFailSavePing testSub = new TestFailSavePing("TestFailSavePing", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
      Log.exit(TestFailSavePing.ME, "Good bye");
   }
}

