/*------------------------------------------------------------------------------
Name:      TestFailSavePing.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSavePing.java,v 1.25 2002/09/09 13:39:53 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.init.Property;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


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
 *   java junit.textui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing
 *   java junit.swingui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing
 * </pre>
 */
public class TestFailSavePing extends TestCase implements I_Callback, I_ConnectionProblems
{
   private static String ME = "TestFailSavePing";
   private final Global glob;
   private boolean messageArrived = false;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private XmlBlasterConnection con;
   private String senderName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";

   /**
    * Constructs the TestFailSavePing object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestFailSavePing(Global glob, String testName, String loginName)
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
      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      try {
         numReceived = 0;

         con = new XmlBlasterConnection(glob); // Find server

         ConnectQos connectQos = new ConnectQos(glob); // == "<qos></qos>";

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(4000L);         // retry connecting every 4 sec
         addressProp.setRetries(-1);          // -1 == forever
         addressProp.setPingInterval(1000L);  // ping every second
         addressProp.setMaxMsg(1000);         // queue up to 1000 messages
         con.initFailSave(this);

         connectQos.setAddress(addressProp);
         
         /* Old way:
         // Setup fail save handling ...
         long retryInterval = 4000L; // Property.getProperty("Failsave.retryInterval", 4000L);
         int retries = -1;           // -1 == forever
         int maxMessages = 1000;
         long pingInterval = 1000L;
         con.initFailSave(this, retryInterval, retries, maxMessages, pingInterval);
         */

         // and do the login ...
         String passwd = "secret";
         con.login(senderName, passwd, connectQos, this); // Login to xmlBlaster
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
      try {
         EraseRetQos[] arr = con.erase(xmlKey, qos);
         assertEquals("Wrong number of message erased", 1, arr.length);
      } catch(XmlBlasterException e) { assertTrue("tearDown - XmlBlasterException: " + e.reason, false); }

      Util.delay(500L);    // Wait some time
      con.logout();

      Util.delay(200L);    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
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
      try {
         String subscribeOid = con.subscribe(xmlKey, qos).getSubscriptionId();
         Log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
         assertTrue("returned null subscribeOid", subscribeOid != null);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
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

      con.publish(msgUnit);
      Log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSave()
   {
      testSubscribe();
      Util.delay(200L);
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      Util.delay(3000L);    // Wait some time, ping should activate login polling

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
      Util.delay(3000L);    // Wait some time, to allow the ping to reconnect

      numReceived = 0;

      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      Util.delay(5000L);    // Wait some time, ping should activate login polling

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
      Util.delay(5000L);    // Wait some time, to allow the ping to reconnect
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
            assertTrue("Lost connection, my connection layer is not polling", false);
         else
            assertTrue("Publishing problems id=" + e.id + ": " + e.reason, false);
      }

      con.resetQueue(); // discard messages (dummy)
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
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message-1";
      assertEquals("Message oid is wrong", oid, updateKey.getUniqueKey());

      messageArrived = true;
      return "";
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
       suite.addTest(new TestFailSavePing(new Global(), "testFailSave", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestFailSavePing
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestFailSavePing</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestFailSavePing testSub = new TestFailSavePing(glob, "TestFailSavePing", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
      Log.exit(TestFailSavePing.ME, "Good bye");
   }
}

