/*------------------------------------------------------------------------------
Name:      TestGetFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestGetFilter.java,v 1.4 2002/05/03 10:37:49 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.ServerThread;

import test.framework.*;


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
 *    java test.textui.TestRunner testsuite.org.xmlBlaster.TestGetFilter
 *    java test.ui.TestRunner testsuite.org.xmlBlaster.TestGetFilter
 * </pre>
 */
public class TestGetFilter extends TestCase
{
   private static String ME = "Tim";
   private final Global glob;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private ServerThread serverThread;
   private int serverPort = 7604;
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
         con.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         Log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
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
      String content = "1234567890";
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos("ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assert("Expected one returned message", msgUnits!=null);
         assert("Expected exactly one returned message", msgUnits.length==1);
         assert("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         Log.info(ME, "Success: Got one message.");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("get - XmlBlasterException: " + e.reason, false);
      }


      Log.info(ME, "TEST 2: Testing filtered message");
      content = "12345678901"; // content is too long, our plugin denies this message
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos("ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assert("Expected one returned message", msgUnits!=null);
         assert("Expected exactly one returned message", msgUnits.length==0);
         Log.info(ME, "Success: Got no message.");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("get - XmlBlasterException: " + e.reason, false);
      }


      Log.info(ME, "TEST 3: Test what happens if the plugin throws an exception");
      try {   // see THROW_EXCEPTION_FOR_LEN=3
         con.publish(new MessageUnit("<key oid='MSG'/>", "123".getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos("ContentLenFilter", "1.0", ""+filterMessageContentBiggerAs));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assert("get() message should throw an XmlBlasterException, but it didn't happen", false);
      } catch(XmlBlasterException e) {
         Log.info(ME, "SUCCESS: We expected an XmlBlasterException: " + e.reason);
      }

      Log.info(ME, "Success in testFilter()");
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
    *   java testsuite.org.xmlBlaster.TestGetFilter
    *   java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestGetFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestGetFilter testSub = new TestGetFilter(glob, "TestGetFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
      Log.exit(TestGetFilter.ME, "Good bye");
   }
}

