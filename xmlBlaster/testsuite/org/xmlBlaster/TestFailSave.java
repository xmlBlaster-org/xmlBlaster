/*------------------------------------------------------------------------------
Name:      TestFailSave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSave.java,v 1.8 2000/02/28 18:40:30 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.*;
import org.xmlBlaster.util.*;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
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

   private String subscribeOid;
   private CorbaConnection corbaConnection;
   private String senderName;
   private String receiverName;         // sender/receiver is here the same client

   private int numReceived = 0;         // error checking
   private int numPublish = 8;
   private int numStop = 3;
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

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
       this.receiverName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      startServer();
      try {
         numReceived = 0;

         String[] args = new String[2];
         args[0] = "-iorPort";
         args[1] = "" + serverPort;
         corbaConnection = new CorbaConnection(args); // Find orb

         // Setup fail save handling ...
         long retryInterval = 4000L; // Property.getProperty("Failsave.retryInterval", 4000L);
         int retries = -1;           // -1 == forever
         int maxMessages = 1000;
         corbaConnection.initFailSave(this, retryInterval, retries, maxMessages);

         // and do the login ...
         String passwd = "secret";
         String qos = "<qos></qos>";
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

      Util.delay(1000L);    // Wait some time

      corbaConnection.logout();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testSubscribe()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      subscribeOid = null;
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
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      String oid = "Message" + "-" + counter;
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
      for (int ii=0; ii<numPublish; ii++) {
         try {
            if (ii == numStop) // 3
               stopServer();
            if (ii == 5)
               startServer();
            testPublish(ii+1);
            waitOnUpdate(2000L);
            //assertEquals("numReceived after publishing", ii+1, numReceived); // message arrived?
         }
         catch(XmlBlasterException e) {
            if (e.id.equals("TryingReconnect"))
               Log.warning(ME, e.id + " exception: Lost connection, my connection layer is polling");
            else if (e.id.equals("NoConnect"))
               assert("Lost connection, my connection layer is not polling", false);
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

      assertEquals("Wrong receveiver", receiverName, loginName);
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


   private void startServer()
   {
      serverThread = new ServerThread(serverPort);
      serverThread.start();
      Util.delay(3000L);    // Wait some time
      Log.info(ME, "Server is up again!");
   }


   private void stopServer()
   {
      serverThread.stopServer = true;
      Util.delay(500L);    // Wait some time
      Log.info(ME, "Server is down!");
   }


   /**
    * Start a xmlBlaster server instance.
    * Invoke thread.stopServer=true; to stop it.
    */
   private class ServerThread extends Thread
   {
      private final String ME = "ServerThread";
      int port = 7609; // this is the default port, which is probably blocked by another xmlBlaster server
      boolean stopServer = false;
      org.xmlBlaster.Main xmlBlasterMain = null;


      ServerThread(int port) { this.port = port; }

      public void run() {
         Log.info(ME, "Starting a xmlBlaster server instance for testing ...");
         String[] args = new String[4];
         args[0] = "-iorPort";
         args[1] = "" + port;
         args[2] = "-doBlocking";
         args[3] = "false";
         xmlBlasterMain = new org.xmlBlaster.Main(args);
         while(!stopServer) {
            try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
         }
         xmlBlasterMain.shutdown(false);
         stopServer = false;
         Log.info(ME, "Stopping the xmlBlaster server instance ...");
      }
   } // class ServerThread


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
      Log.setLogLevel(args);
      TestFailSave testSub = new TestFailSave("TestFailSave", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
      Log.exit(TestFailSave.ME, "Good bye");
   }
}

