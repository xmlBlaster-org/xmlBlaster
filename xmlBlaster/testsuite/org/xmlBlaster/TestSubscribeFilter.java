/*------------------------------------------------------------------------------
Name:      TestSubscribeFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestSubscribeFilter.java,v 1.12 2002/05/17 06:52:20 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.ServerThread;

import junit.framework.*;


/**
 * This client does test login sessions.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubscribeFilter
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestSubscribeFilter
 * </pre>
 */
public class TestSubscribeFilter extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking
   private ServerThread serverThread;
   private int serverPort = 7604;
   private int filterMessageContentBiggerAs = 10;

   /**
    * Constructs the TestSubscribeFilter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestSubscribeFilter(Global glob, String testName, String name)
   {
      super(testName);
      this.glob = glob;
      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it has configured to load our simple demo MIME filter plugin.
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = new String[10];
      args[0] = "-port";        // For all protocol we may use set an alternate server port
      args[1] = "" + serverPort;
      args[2] = "-socket.port";
      args[3] = "" + (serverPort-1);
      args[4] = "-rmi.registryPort";
      args[5] = "" + (serverPort-2);
      args[6] = "-xmlrpc.port";
      args[7] = "" + (serverPort-3);
      args[8] = "-MimeAccessPlugin[ContentLenFilter][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,THROW_EXCEPTION_FOR_LEN=3";
      glob.init(args);

      serverThread = ServerThread.startXmlBlaster(args);
      try { Thread.currentThread().sleep(4000L); } catch( InterruptedException i) {}
      Log.info(ME, "XmlBlaster is ready for testing subscribe MIME filter");

      try {
         Log.info(ME, "Connecting ...");
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         Log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
      }

      // Subscribe to a message with a supplied filter
      try {
         SubscribeQosWrapper qos = new SubscribeQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         String subscribeOid = con.subscribe("<key oid='MSG'/>", qos.toXml());
         Log.info(ME, "Success: Subscribe subscription-id=" + subscribeOid + " done");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...

      String[] strArr = null;
      try {
         strArr = con.erase("<key oid='MSG'/>", null);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");

      con.disconnect(null);

      Util.delay(500L);    // Wait some time
      ServerThread.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * First we send a message <= 10 content length which should be updated to us,
    * then we send a message with 11 bytes in the content which should be filtered
    */
   public void testFilter()
   {
      Log.info(ME, "testFilter() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      Log.info(ME, "TEST 1: Testing unfiltered message");
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", "1234567890".getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }
      waitOnUpdate(2000L, 1); // message should come back as it is only 10 bytes


      Log.info(ME, "TEST 2: Testing filtered message");
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", "12345678901".getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }
      waitOnUpdate(2000L, 0); // message should be filtered as it is longer 10 bytes


      Log.info(ME, "TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         con.publish(new MessageUnit("<key oid='MSG'/>", "123".getBytes(), null));
         assertTrue("publish forced the plugin to throw an XmlBlasterException, but it didn't happen", false);
      } catch(XmlBlasterException e) {
         Log.info(ME, "SUCCESS: We expected an XmlBlasterException: " + e.reason);
      }
      waitOnUpdate(2000L, 0); // no message expected on exception

      Log.info(ME, "Success in testFilter()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.info(ME, "Receiving update of a message " + updateKey.getUniqueKey());
      numReceived++;
      return "";
   }

   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      // check if too few are arriving
      while (numReceived < numWait) {
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
      assertEquals("Wrong number of messages arrived", numWait, numReceived);

      numReceived = 0;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubscribeFilter(new Global(), "testFilter", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java testsuite.org.xmlBlaster.TestSubscribeFilter
    *   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubscribeFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestSubscribeFilter testSub = new TestSubscribeFilter(glob, "TestSubscribeFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
      Log.exit(TestSubscribeFilter.ME, "Good bye");
   }
}

