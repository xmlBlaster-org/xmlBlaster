/*------------------------------------------------------------------------------
Name:      TestC.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Integrate C tests into junit reports
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.C;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Execute;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html">
 * C SOCKET client library, it is only a wrapper around the real C tests in xmlBlaster/testsuite/src/c
 * to retrieve the results for our HTML test report (see build.xml 'report' task). 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.C.TestC
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.C.TestC
 * </pre>
 */
public class TestC extends TestCase
{
   private static String ME = "TestC";
   private final Global glob;
   private final LogChannel log;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;

   /**
    * Constructs the TestC object. 
    * <p />
    * @param testName   The name used in the test suite
    */
   public TestC(String testName) {
      super(testName);
      this.glob = Global.instance();
      this.log = glob.getLog(null);
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load the tinySQL C driver to test SQL access (with dBase files)
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      /*
      Vector argsVec = Util.getOtherServerPortVec(serverPort);
      glob.init((String[])argsVec.toArray(new String[argsVec.size()]));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing C client library");
      */
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      /*
      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      Util.resetPorts();
      */
   }

   /**
    * Create a RDBMS table, fill some data and destroy it again. 
    * We use the tinySQL dBase C driver for testing.
    */
   public void testC()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { "../../../../c/bin/TestMethods" };
      //String[] commandArr = { "../../../../../c/bin/TestMethods" };
      //String[] commandArr = { "testsuite/src/c/bin/TestMethods" };
      String[] envArr = { "" };

      log.info(ME, "######## Start testC('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.run();

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS testC('" + commandArr[0] + "'): " + e.getStdout());
   }

   /**
    * Invoke: java org.xmlBlaster.test.C.TestC
    * @deprecated Use the TestRunner from the testsuite to run it
    */
   public static void main(String args[]) {
      Global glob = Global.instance();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestC test = new TestC("TestC");
      test.setUp();
      test.testC();
      test.tearDown();
   }
}

