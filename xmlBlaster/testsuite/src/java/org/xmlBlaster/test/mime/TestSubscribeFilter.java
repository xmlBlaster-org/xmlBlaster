/*------------------------------------------------------------------------------
Name:      TestSubscribeFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.mime;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

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
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.mime.TestSubscribeFilter
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.mime.TestSubscribeFilter
 * </pre>
 */
public class TestSubscribeFilter extends TestCase implements I_Callback
{
   private static String ME = "TestSubscribeFilter";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubscribeFilter.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking
   private String updateOid;
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7624;
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
      String[] args = {
         "-bootstrapPort",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-plugin/socket/port",
         "" + (serverPort-1),
         "-plugin/rmi/registryPort",
         "" + (serverPort-2),
         "-plugin/xmlrpc/port",
         "" + (serverPort-3),
         "-MimeAccessPlugin[ContentLenFilter][1.0]",
         "org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,THROW_EXCEPTION_FOR_LEN=3",
         "-admin.remoteconsole.port",
         "0"
      };
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(args);
      log.info("XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      // Subscribe to a message with a supplied filter
      try {
         SubscribeQos qos = new SubscribeQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         String subscribeOid = con.subscribe("<key oid='MSG'/>", qos.toXml()).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscribeOid + " done");

         con.subscribe("<key oid='" + Constants.OID_DEAD_LETTER + "'/>", "<qos/>");
        
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      try {
         EraseReturnQos[] arr = con.erase("<key oid='MSG'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
      con=null;

      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * First we send a message <= 10 content length which should be updated to us,
    * then we send a message with 11 bytes in the content which should be filtered
    */
   public void testFilter()
   {
      log.info("testFilter() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      log.info("TEST 1: Testing unfiltered message");
      try {
         con.publish(new MsgUnit("<key oid='MSG'/>", "1234567890".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 1); // message should come back as it is only 10 bytes


      log.info("TEST 2: Testing filtered message");
      try {
         con.publish(new MsgUnit("<key oid='MSG'/>", "12345678901".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 0); // message should be filtered as it is longer 10 bytes


      log.info("TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         con.publish(new MsgUnit("<key oid='MSG'/>", "123".getBytes(), null));
         waitOnUpdate(2000L, 1); // a dead message should come
         assertEquals("", Constants.OID_DEAD_LETTER, updateOid);
         log.info("SUCCESS: Dead message arrived");
      } catch(XmlBlasterException e) {
         fail("publish forced the plugin to throw an XmlBlasterException, but it should not reach the publisher: " + e.toString());
      }
      waitOnUpdate(2000L, 0); // no message expected on exception

      log.info("Success in testFilter()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message '" + updateKey.getOid() + "' state=" + updateQos.getState());
      updateOid = updateKey.getOid();
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
    *   java org.xmlBlaster.test.mime.TestSubscribeFilter
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.mime.TestSubscribeFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubscribeFilter testSub = new TestSubscribeFilter(glob, "TestSubscribeFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
   }
}

