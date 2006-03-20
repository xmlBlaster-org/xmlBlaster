/*------------------------------------------------------------------------------
Name:      TestGetRegexFilter.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.mime;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.qos.AccessFilterQos;
import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.test.Util;
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
 *    java junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetRegexFilter
 *    java junit.swingui.TestRunner org.xmlBlaster.test.mime.TestGetRegexFilter
 * </pre>
 */
public class TestGetRegexFilter extends TestCase
{
   private static String ME = "Tim";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestGetRegexFilter.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
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
      args[0] = "-bootstrapPort";        // For all protocol we may use set an alternate server port
      args[1] = "" + serverPort;
      args[2] = "-plugin/socket/port";
      args[3] = "" + (serverPort-1);
      args[4] = "-plugin/rmi/registryPort";
      args[5] = "" + (serverPort-2);
      args[6] = "-plugin/xmlrpc/port";
      args[7] = "" + (serverPort-3);
      args[8] = "-MimeAccessPlugin[GnuRegexFilter][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.regex.GnuRegexFilter";
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(args);
      log.info("XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, null); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      try {
         EraseReturnQos[] arr = con.erase("<key oid='MSG'/>", null);
         assertEquals("Erased", 1, arr.length);
      } catch(XmlBlasterException e) { fail("XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
      con = null;

      try { Thread.sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;

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

      log.info("TEST 1: The message content matches the pattern");
      String content = "aaaab"; // check this string
      try {
         con.publish(new MsgUnit("<key oid='MSG'/>", content.getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      try {
         GetQos qos = new GetQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "GnuRegexFilter", "1.0", regPattern));

         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected one returned message", msgUnits!=null);
         assertTrue("Expected exactly one returned message", msgUnits.length==1);
         assertTrue("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         log.info("Success: Got one message.");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("get - XmlBlasterException: " + e.getMessage(), false);
      }


      log.info("TEST 2: The message content does NOT match the pattern");
      try {
         con.publish(new MsgUnit("<key oid='MSG'/>", new String("aaaaaac").getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      try {
         GetQos qos = new GetQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "GnuRegexFilter", "1.0", regPattern));

         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         if (msgUnits.length > 0) log.info(msgUnits[0].toXml());
         assertTrue("Expected zero returned message", msgUnits!=null);
         assertEquals("Expected zero returned message", 0, msgUnits.length);
         log.info("Success: Got no message.");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("get - XmlBlasterException: " + e.getMessage(), false);
      }

      log.info("Success in testFilter()");
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
    *   java org.xmlBlaster.test.mime.TestGetRegexFilter
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetRegexFilter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestGetRegexFilter testSub = new TestGetRegexFilter(glob, "TestGetRegexFilter", "Tim");
      testSub.setUp();
      testSub.testFilter();
   }
}

