/*------------------------------------------------------------------------------
Name:      TestFailSave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSave.java,v 1.29 2002/05/03 10:37:49 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.ServerThread;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.MessageUnit;
import test.framework.*;


/**
 * Tests the fail save behavior of the XmlBlasterConnection client helper class.
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of XmlBlasterConnection.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSave
 *   java test.ui.TestRunner testsuite.org.xmlBlaster.TestFailSave
 * </pre>
 * @see org.xmlBlaster.client.protocol.XmlBlasterConnection
 */
public class TestFailSave extends TestCase implements I_Callback, I_ConnectionProblems
{
   private static String ME = "Tim";
   private final Global glob;

   private boolean messageArrived = false;

   private int serverPort = 7604;
   private ServerThread serverThread;

   private XmlBlasterConnection con;
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
   public TestFailSave(Global glob, String testName, String loginName)
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

      serverThread = ServerThread.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      Log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);
      try {
         numReceived = 0;

         con = new XmlBlasterConnection(glob); // Find orb

         ConnectQos connectQos = new ConnectQos(glob); // == "<qos></qos>";

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(4000L);      // retry connecting every 4 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(0L);  // switched off
         addressProp.setMaxMsg(1000);      // queue up to 1000 messages
         con.initFailSave(this);

         connectQos.setAddress(addressProp);

         /* Old way:
         // Setup fail save handling ...
         long retryInterval = 4000L; // XmlBlasterProperty.get("Failsave.retryInterval", 4000L);
         int retries = -1;           // -1 == forever
         long pingInterval = 0L;     // switched off
         int maxMessages = 1000;
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
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = new String[0];
      try {
         strArr = con.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      assertEquals("Wrong number of message erased", strArr.length, (numPublish - numStop));

      Util.delay(500L);    // Wait some time
      con.logout();

      Util.delay(500L);    // Wait some time
      ServerThread.stopXmlBlaster(serverThread);

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
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      String subscriptionId = null;
      try {
         subscriptionId = con.subscribe(xmlKey, qos);
         Log.info(ME, "Success: Subscribe on subscriptionId=" + subscriptionId + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
      assert("returned null subscriptionId", subscriptionId != null);
      // NOT FOR FAIL SAVE: assertNotEquals("returned subscriptionId is empty", 0, subscriptionId.length());
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
            waitOnUpdate(4000L);
            //assertEquals("numReceived after publishing", ii+1, numReceived); // message arrived?
         }
         catch(XmlBlasterException e) {
            if (e.id.equals("TryingReconnect"))
               Log.warn(ME, e.id + " exception: Lost connection, my connection layer is polling");
            else if (e.id.equals("NoConnect"))
               assert("Lost connection, my connection layer is NOT polling", false);
            else
               assert("Publishing problems id=" + e.id + ": " + e.reason, false);
         }
         //Util.delay(3000L);    // Wait some time
      }

      assertEquals("numReceived is wrong", numPublish, numReceived);
      Util.delay(1000L);    // Wait some time
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
         con.flushQueue();    // send all tailback messages
         // con.resetQueue(); // or discard them (it is our choice)
      } catch (XmlBlasterException e) {
         assert("Exception during reconnection recovery: " + e.reason, false);
      }
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
      numReceived += 1;
      Log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " numReceived=" + numReceived + " ...");

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message" + "-" + numReceived;
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
       suite.addTest(new TestFailSave(new Global(), "testFailSave", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestFailSave
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestFailSave</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestFailSave testSub = new TestFailSave(glob, "TestFailSave", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
      Log.exit(TestFailSave.ME, "Good bye");
   }
}

