/*------------------------------------------------------------------------------
Name:      TestGetFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestGetFilter.java,v 1.2 2002/09/13 23:18:26 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.mime;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">
 * access filter plugin framework</a> on get() invocations. 
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetFilter
 *    java junit.swingui.TestRunner org.xmlBlaster.test.mime.TestGetFilter
 * </pre>
 */
public class TestGetFilter extends TestCase
{
   private static String ME = "TestGetFilter";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;
   private int filterMessageContentBiggerAs = 10;

   /**
    * Constructs the TestGetFilter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestGetFilter(Global glob, String testName, String name)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
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
      args[8] = "-MimeAccessPlugin[ContentLenFilter][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,THROW_EXCEPTION_FOR_LEN=3";
      args[10] = "-client.port";
      args[11] = "" + serverPort;
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info(ME, "Connecting ...");
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, null); // Login to xmlBlaster
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
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      try {
         EraseRetQos[] arr = con.erase("<key oid='MSG'/>", null);
         if (arr.length != 1) log.error(ME, "Erased " + arr.length + " messages:");
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.reason); }

      con.disconnect(null);

      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * First we send a message <= 10 content length which should be updated to us,
    * then we send a message with 11 bytes in the content which should be filtered
    */
   public void testFilter()
   {
      log.info(ME, "testFilter() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      log.info(ME, "TEST 1: Testing unfiltered message");
      String content = "1234567890";
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected one returned message", msgUnits!=null);
         assertTrue("Expected exactly one returned message", msgUnits.length==1);
         assertTrue("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         log.info(ME, "Success: Got one message.");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("get - XmlBlasterException: " + e.reason, false);
      }


      log.info(ME, "TEST 2: Testing filtered message");
      content = "12345678901"; // content is too long, our plugin denies this message
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected one returned message", msgUnits!=null);
         assertEquals("Expected no returned message", 0, msgUnits.length);
         log.info(ME, "Success: Got no message.");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         fail("get - XmlBlasterException: " + e.reason);
      }


      log.info(ME, "TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         con.publish(new MessageUnit("<key oid='MSG'/>", "123".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         fail("publish - XmlBlasterException: " + e.reason);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         fail("get() message should throw an XmlBlasterException, but it didn't happen");
      } catch(XmlBlasterException e) {
         log.info(ME, "SUCCESS: We expected an XmlBlasterException: " + e.reason);
      }

      log.info(ME, "Success in testFilter()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestGetFilter(new Global(), "testFilter", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestGetFilter
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestGetFilter testSub = new TestGetFilter(glob, "TestGetFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
   }
}

