/*------------------------------------------------------------------------------
Name:      TestGetRegexFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestGetRegexFilter.java,v 1.3 2002/05/11 10:07:54 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.AccessFilterQos;
import org.xmlBlaster.util.ServerThread;

import junit.framework.*;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/mime.plugin.accessfilter.html">
 * access filter plugin framework</a> on get() invocations. 
 * <p />
 * Here we test the regular expression plugin which allows us to select messages
 * when the message content matches a given regular expression.
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java test.textui.TestRunner testsuite.org.xmlBlaster.TestGetRegexFilter
 *    java test.ui.TestRunner testsuite.org.xmlBlaster.TestGetRegexFilter
 * </pre>
 */
public class TestGetRegexFilter extends TestCase
{
   private static String ME = "Tim";
   private final Global glob;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private ServerThread serverThread;
   private int serverPort = 7604;

   /**
    * Constructs the TestGetRegexFilter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestGetRegexFilter(Global glob, String testName, String name)
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
      args[8] = "-MimeAccessPlugin[GnuRegexFilter][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.regex.GnuRegexFilter";
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
    * First we send a message which matches our regular expression and should be returned to us,
    * then we send a message with a different content which should be filtered
    */
   public void testFilter()
   {
      String regPattern = "a*b"; // String with any numbers of 'a' and ending with one 'b'

      Log.info(ME, "TEST 1: The message content matches the pattern");
      String content = "aaaab"; // check this string
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "GnuRegexFilter", "1.0", regPattern));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected one returned message", msgUnits!=null);
         assertTrue("Expected exactly one returned message", msgUnits.length==1);
         assertTrue("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         Log.info(ME, "Success: Got one message.");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("get - XmlBlasterException: " + e.reason, false);
      }


      Log.info(ME, "TEST 2: The message content does NOT match the pattern");
      try {
         con.publish(new MessageUnit("<key oid='MSG'/>", new String("aaaaaac").getBytes(), null));
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      try {
         GetQosWrapper qos = new GetQosWrapper();
         qos.addAccessFilter(new AccessFilterQos(glob, "GnuRegexFilter", "1.0", regPattern));

         MessageUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected zero returned message", msgUnits!=null);
         assertTrue("Expected zero returned message", msgUnits.length==0);
         Log.info(ME, "Success: Got no message.");
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("get - XmlBlasterException: " + e.reason, false);
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
       suite.addTest(new TestGetRegexFilter(new Global(), "testFilter", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java testsuite.org.xmlBlaster.TestGetRegexFilter
    *   java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestGetRegexFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestGetRegexFilter testSub = new TestGetRegexFilter(glob, "TestGetRegexFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
      Log.exit(TestGetRegexFilter.ME, "Good bye");
   }
}

