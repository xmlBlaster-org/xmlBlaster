/*------------------------------------------------------------------------------
Name:      TestGetSql92Filter.java
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
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.PublishQos;
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
 *    java junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetSql92Filter
 *    java junit.swingui.TestRunner org.xmlBlaster.test.mime.TestGetSql92Filter
 * </pre>
 */
public class TestGetSql92Filter extends TestCase
{
   private static String ME = "TestGetSql92Filter";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestGetSql92Filter.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7604;

   /**
    * Constructs the TestGetSql92Filter object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestGetSql92Filter(Global glob, String testName, String name)
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
      args[8] = "-MimeAccessPlugin[Sql92Filter][1.0]";
      args[9] = "org.xmlBlaster.engine.mime.sql92.Sql92Filter";
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
         Thread.dumpStack();
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
      String filter = "posX BETWEEN 0 AND 200 AND posY BETWEEN 0 AND 200";
      String content = "space-shuttle";
      log.info("TEST 2: The published message does match the pattern");
      try {
         PublishQos pubQos = new PublishQos(this.glob);
         pubQos.addClientProperty("posX", 50);
         pubQos.addClientProperty("posY", 150);
         MsgUnit msgUnit = new MsgUnit(new PublishKey(this.glob, "MSG"), content, pubQos);
         con.publish(msgUnit);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      try {
         GetQos qos = new GetQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "Sql92Filter", "1.0", filter));

         MsgUnit[] msgUnits = con.get("<key oid='MSG'/>", qos.toXml());
         assertTrue("Expected one returned message", msgUnits != null);
         assertTrue("Expected exactly one returned message", msgUnits.length==1);
         assertTrue("Message content in corrupted '" + new String(msgUnits[0].getContent()) + "' versus '" + content + "'",
                msgUnits[0].getContent().length == content.length());
         log.info("Success: Got one message.");
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("get - XmlBlasterException: " + e.getMessage(), false);
      }


      log.info("TEST 2: The published message does NOT match the pattern");
      try {
         PublishQos pubQos = new PublishQos(this.glob);
         pubQos.addClientProperty("posX", 250);
         pubQos.addClientProperty("posY", 150);
         MsgUnit msgUnit = new MsgUnit(new PublishKey(this.glob, "MSG"), content, pubQos);
         con.publish(msgUnit);
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      try {
         GetQos qos = new GetQos(glob);
         qos.addAccessFilter(new AccessFilterQos(glob, "Sql92Filter", "1.0", filter));

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
       suite.addTest(new TestGetSql92Filter(new Global(), "testFilter", "sql92"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.mime.TestGetSql92Filter
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.mime.TestGetSql92Filter
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestGetSql92Filter testSub = new TestGetSql92Filter(glob, "TestGetSql92Filter", "sql92");
      testSub.setUp();
      testSub.testFilter();
   }
}

