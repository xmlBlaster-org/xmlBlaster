/*------------------------------------------------------------------------------
Name:      TestPublishFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.mime;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.publishfilter.html">
 * publish filter plugin framework</a> on publish() invocations. 
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.mime.TestPublishFilter
 *    java junit.swingui.TestRunner org.xmlBlaster.test.mime.TestPublishFilter
 * </pre>
 */
public class TestPublishFilter extends TestCase
{
   private static String ME = "TestPublishFilter";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7619;
   private int filterMessageContentBiggerAs = 10;
   private int numUpdated = 0;

   /**
    * Constructs the TestPublishFilter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestPublishFilter(Global glob, String testName, String name)
   {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog(null);
      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load our simple demo MIME filter plugin.
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = new String[12];
      args[0] = "-port";        // For all protocol we may use set an alternate server port
      args[1] = "" + serverPort;
      args[2] = "-socket.port";
      args[3] = "" + (serverPort-1);
      args[4] = "-rmi.registryPort";
      args[5] = "" + (serverPort-2);
      args[6] = "-xmlrpc.port";
      args[7] = "" + (serverPort-3);
      args[8] = "-MimePublishPlugin[PublishLenChecker][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.demo.PublishLenChecker,DEFAULT_MAX_LEN=10,THROW_EXCEPTION_FOR_LEN=3";
      args[10] = "-client.port";
      args[11] = "" + serverPort;
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing publish MIME filter");

      try {
         log.info(ME, "Connecting ...");
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, new I_Callback() {
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(name, "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
                  numUpdated++;
                  return "";
               }
            });  // Login to xmlBlaster, default handler for updates
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {} // Wait some time

      con.disconnect(null);

      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {} // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * First we send a message <= 10 content length which should be accepted by xmlBlaster
    * and updated to us,
    * then we send a message with 11 bytes in the content which should be filtered. 
    * <p />
    * The test is done in Publish/Subscribe mode
    */
   public void testFilter() {
      log.info(ME, "testFilter() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      log.info(ME, "TEST 1: Testing filtered message");
      String content = "12345678901"; // content is too long, our plugin denies this message
      try {
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='MSG'/>", content.getBytes(), null));
         log.info(ME, "TEST 1: SUCESS returned state=" + rq.getState());
         assertTrue("Return OK", !Constants.STATE_OK.equals(rq.getState()));
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      try {
         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", null);
         assertTrue("Invalid return", msgUnits!=null);
         assertEquals("Expected no returned message", 0, msgUnits.length);
      } catch(XmlBlasterException e) {
         log.warn(ME, "get - XmlBlasterException: " + e.getMessage());
         fail("get - XmlBlasterException: " + e.getMessage());
      }


      log.info(ME, "TEST 2: Testing unfiltered message");
      content = "1234567890";
      try {
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='MSG'/>", content.getBytes(), null));
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
         log.info(ME, "TEST 2: SUCESS");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      try {
         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", null);
         assertTrue("Expected one returned message", msgUnits!=null);
         assertTrue("Expected exactly one returned message", msgUnits.length==1);
         assertTrue("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         log.info(ME, "Success: Got one message.");
      } catch(XmlBlasterException e) {
         log.warn(ME, "get - XmlBlasterException: " + e.getMessage());
         fail("get - XmlBlasterException: " + e.getMessage());
      }

      log.info(ME, "TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         con.publish(new MsgUnit("<key oid='MSG'/>", "123".getBytes(), null));
         fail("publish - expected an XmlBlasterException");
      } catch(XmlBlasterException e) {
         log.warn(ME, "TEST 3: SUCCESS XmlBlasterException: " + e.getMessage());
      }

      try {
         EraseReturnQos[] arr = con.erase("<key oid='MSG'/>", null);
         assertEquals("Erased problem", 1, arr.length);
      } catch(XmlBlasterException e) { fail(ME + " XmlBlasterException: " + e.getMessage()); }

      log.info(ME, "Success in testFilter()");
   }

   /**
    * First we send a message <= 10 content length which should be accepted by xmlBlaster
    * and updated to us,
    * then we send a message with 11 bytes in the content which should be filtered. 
    * <p />
    * The test is done in Point To Point mode
    */
   public void testPtPFilter() {
      log.info(ME, "testPtPFilter() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      numUpdated = 0;

      log.info(ME, "TEST 1: Testing filtered PtP message");
      String content = "12345678901"; // content is too long, our plugin denies this message
      try {
         PublishQos pq = new PublishQos(glob);
         pq.addDestination(new Destination(new SessionName(glob, name)));
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='MSG'/>", content.getBytes(), pq.toXml()));
         log.info(ME, "TEST 1: SUCESS returned state=" + rq.getState());
         assertTrue("Return OK", !Constants.STATE_OK.equals(rq.getState()));
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      try {
         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", null);
         assertTrue("Invalid return", msgUnits!=null);
         assertEquals("Expected no returned message", 0, msgUnits.length);
      } catch(XmlBlasterException e) {
         log.warn(ME, "get - XmlBlasterException: " + e.getMessage());
         fail("get - XmlBlasterException: " + e.getMessage());
      }

      log.info(ME, "TEST 2: Testing unfiltered PtP message");
      content = "1234567890";
      try {
         PublishQos pq = new PublishQos(glob);
         pq.addDestination(new Destination(new SessionName(glob, name)));
         PublishReturnQos rq = con.publish(new MsgUnit("<key oid='MSG'/>", content.getBytes(), pq.toXml()));
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
         log.info(ME, "TEST 2: SUCESS");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      log.info(ME, "TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         PublishQos pq = new PublishQos(glob);
         pq.addDestination(new Destination(new SessionName(glob, name)));
         con.publish(new MsgUnit("<key oid='MSG'/>", "123".getBytes(), pq.toXml()));
         fail("publish - expected an XmlBlasterException");
      } catch(XmlBlasterException e) {
         log.warn(ME, "TEST 3: SUCCESS XmlBlasterException: " + e.getMessage());
      }

      try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
      assertEquals("PtP updates is not one", 1, numUpdated);

      log.info(ME, "Success in testPtPFilter()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestPublishFilter(new Global(), "testFilter", "Tim"));
       suite.addTest(new TestPublishFilter(new Global(), "testPtPFilter", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestPublishFilter
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.mime.TestPublishFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestPublishFilter testSub = new TestPublishFilter(glob, "TestPublishFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
      testSub.testPtPFilter();
   }
}

