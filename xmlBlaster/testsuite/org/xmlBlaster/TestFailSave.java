/*------------------------------------------------------------------------------
Name:      TestFailSave.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing publish()
Version:   $Id: TestFailSave.java,v 1.39 2002/09/10 19:00:27 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.MessageUnit;
import junit.framework.*;


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
 *   java junit.textui.TestRunner -noloading testsuite.org.xmlBlaster.TestFailSave
 *   java junit.swingui.TestRunner -noloading testsuite.org.xmlBlaster.TestFailSave
 * </pre>
 * @see org.xmlBlaster.client.protocol.XmlBlasterConnection
 */
public class TestFailSave extends TestCase implements I_Callback, I_ConnectionProblems
{
   private static String ME = "TestFailSave";
   private final Global glob;
   private final LogChannel log;

   private boolean messageArrived = false;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private XmlBlasterConnection con;
   private String senderName;

   private int numReceived = 0;         // error checking
   private int numPublish = 8;
   private int numStop = 3;
   private final String contentMime = "text/plain";

   private final long reconnectDelay = 2000L;

   private String assertInUpdate = null;

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
      this.log = glob.getLog(null);
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

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);
      try {
         numReceived = 0;

         con = new XmlBlasterConnection(glob); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd); // == "<qos>...</qos>";

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(reconnectDelay); // retry connecting every 2 sec
         addressProp.setRetries(-1);       // -1 == forever
         addressProp.setPingInterval(-1L); // switched off
         addressProp.setMaxMsg(1000);      // queue up to 1000 messages
         con.initFailSave(this);

         connectQos.setAddress(addressProp);

         // and do the login ...
         con.connect(connectQos, this);  // Login to xmlBlaster, register for updates
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed");
          fail("setUp() - login faile");
      }
      catch (Exception e) {
          log.error(ME, "setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login faile");
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info(ME, "Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseRetQos[] arr = con.erase(xmlKey, qos);
         assertEquals("Wrong number of message erased", arr.length, (numPublish - numStop));
         assertTrue(assertInUpdate, assertInUpdate == null);
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.reason); }

      Util.delay(500L);    // Wait some time
      con.logout();

      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void testSubscribe()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSave-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         SubscribeRetQos subscriptionId = con.subscribe(xmlKey, qos);
         log.info(ME, "Success: Subscribe on subscriptionId=" + subscriptionId.getSubscriptionId() + " done");
         assertTrue("returned null subscriptionId", subscriptionId != null);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish(int counter) throws XmlBlasterException
   {
      String oid = "Message" + "-" + counter;
      log.info(ME, "Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSave-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSave-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQosWrapper qosWrapper = new PublishQosWrapper(); // == "<qos></qos>"
      MessageUnit msgUnit = new MessageUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      con.publish(msgUnit);
      log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSave()
   {
      testSubscribe();
      log.info(ME, "Going to publish " + numPublish + " messages, xmlBlaster will be down for message 3 and 4");
      for (int ii=0; ii<numPublish; ii++) {
         try {
            if (ii == numStop) { // 3
               log.info(ME, "Stopping xmlBlaster, but continue with publishing ...");
               EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
            }
            if (ii == 5) {
               log.info(ME, "Starting xmlBlaster again, expecting the previous published two messages ...");
               serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
               log.info(ME, "xmlBlaster started, waiting on tail back messsages");
               waitOnUpdate(reconnectDelay*2L); // Message-4 We need to wait until the client reconnected (reconnect interval)
               waitOnUpdate(4000L); // Message-5
            }
            doPublish(ii+1);
            waitOnUpdate(4000L);
            assertTrue(assertInUpdate, assertInUpdate == null);
            assertInUpdate = null;
            //assertEquals("numReceived after publishing", ii+1, numReceived); // message arrived?
         }
         catch(XmlBlasterException e) {
            if (e.id.equals("TryingReconnect"))
               log.warn(ME, e.id + " exception: Lost connection, my connection layer is polling");
            else if (e.id.equals("NoConnect"))
               assertTrue("Lost connection, my connection layer is NOT polling", false);
            else
               assertTrue("Publishing problems id=" + e.id + ": " + e.reason, false);
         }
      }

      Util.delay(2000L);    // Wait some time
      assertEquals("numReceived is wrong", numPublish, numReceived);
   }


   /**
    * This is the callback method invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionProblems
    */
   public void reConnected()
   {
      log.info(ME, "I_ConnectionProblems: We were lucky, reconnected to xmlBlaster");
      testSubscribe();    // initialize subscription again
      try {
         con.flushQueue();    // send all tailback messages
         // con.resetQueue(); // or discard them (it is our choice)
      } catch (XmlBlasterException e) {
         assertTrue("Exception during reconnection recovery: " + e.reason, false);
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
      log.warn(ME, "I_ConnectionProblems: Lost connection to xmlBlaster");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message state=" + updateQos.getState());

      if (updateQos.isErased()) {
         log.info(ME, "Ignore erase event");
         return ""; // We ignore the erase event on tearDown
      }

      numReceived += 1;
      log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + " numReceived=" + numReceived + " ...");

      assertInUpdate = "Wrong sender, expected:" + senderName + " but was:" + updateQos.getSender();
      assertEquals("Wrong sender", senderName, updateQos.getSender());

      assertInUpdate = "Message contentMime is corrupted expected:" + contentMime + " but was:" + updateKey.getContentMime();
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message" + "-" + numReceived;
      assertInUpdate = "Wrong oid of message returned expected:" + oid + " but was:" + updateKey.getUniqueKey();
      assertEquals("Message oid is wrong", oid, updateKey.getUniqueKey());

      assertInUpdate = null;
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
            log.info(ME, "Timeout of " + timeout + " occurred");
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
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestFailSave</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }
      TestFailSave testSub = new TestFailSave(glob, "TestFailSave", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
   }
}

