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
import org.xmlBlaster.util.I_ExecuteListener;
import org.xmlBlaster.test.Util;

import java.io.*;

import junit.framework.*;


/**
 * This client tests the <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/client.c.socket.html">
 * C SOCKET client library, it is only a wrapper around the real C tests in xmlBlaster/testsuite/src/c
 * to retrieve the results for our HTML test report (see build.xml 'report' task). 
 * <p />
 * xmlBlaster needs to be started separately.
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.C.TestC
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.C.TestC
 * </pre>
 */
public class TestC extends TestCase implements I_ExecuteListener
{
   private static String ME = "TestC";
   private final Global glob;
   private final LogChannel log;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;
   private String pathToCBinary = null;
   String sep = File.separator;

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

      /* Find the location of the C binaries */
      String xmlBlasterHome = glob.getProperty().get("XMLBLASTER_HOME", "$HOME"+sep+"xmlBlaster");
      String[] pathToCArr = { ".."+sep+".."+sep+".."+sep+".."+sep+"c"+sep+"bin",
                              ".."+sep+".."+sep+".."+sep+".."+sep+".."+sep+"c"+sep+"bin",
                             "testsuite"+sep+"src"+sep+"c"+sep+"bin",
                             xmlBlasterHome+sep+"testsuite"+sep+"src"+sep+"c"+sep+"bin"};
      for (int i=0; i<pathToCArr.length; i++) {
         File f = new File(pathToCArr[i]);
         log.trace(ME, "Looking under '" + f.toString() + "'");
         if (f.exists()) {
            this.pathToCBinary = pathToCArr[i];
            log.info(ME, "Found C executables under '" + this.pathToCBinary + "'");
            break;
         }
      }
      assertTrue("Path to C binaries not found, no testing of C client library is not possible", this.pathToCBinary!=null);
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      /*
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      Util.resetPorts();
      */
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_MethodInvocations()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { pathToCBinary+sep+"TestMethods" };
      String[] envArr = { "" };

      log.info(ME, "######## Start test_C_MethodInvocations('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.setExecuteListener(this);
      e.run();

      if (e.getExitValue() != 0) {
         fail("C client library test '" + commandArr[0] + "' + failed exit=" + e.getExitValue() + ": " + e.getStderr());
      }

      if (e.getErrorText() != null) {
         fail(e.getErrorText());
      }

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS test_C_MethodInvocations('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API with illegal arguments. 
    */
   public void test_C_IllegalArguments()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { pathToCBinary+sep+"TestError" };
      String[] envArr = { "" };

      log.info(ME, "######## Start test_C_IllegalArguments('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.setExecuteListener(this);
      e.run();

      if (e.getExitValue() != 0) {
         fail("C client library test '" + commandArr[0] + "' + failed exit=" + e.getExitValue() + ": " + e.getStderr());
      }

      if (e.getErrorText() != null) {
         fail(e.getErrorText());
      }

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS test_C_IllegalArguments('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_Stress()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { pathToCBinary+sep+"TestStress" };
      String[] envArr = { "" };

      log.info(ME, "######## Start test_C_Stress('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.setExecuteListener(this);
      e.run();

      if (e.getExitValue() != 0) {
         fail("C client library test '" + commandArr[0] + "' + failed exit=" + e.getExitValue() + ": " + e.getStderr());
      }

      if (e.getErrorText() != null) {
         fail(e.getErrorText());
      }

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS test_C_Stress('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_Util()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { pathToCBinary+sep+"TestUtil" };
      String[] envArr = { "" };

      log.info(ME, "######## Start test_C_Util('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.setExecuteListener(this);
      e.run();

      if (e.getExitValue() != 0) {
         fail("C client library test '" + commandArr[0] + "' + failed exit=" + e.getExitValue() + ": " + e.getStderr());
      }

      if (e.getErrorText() != null) {
         fail(e.getErrorText());
      }

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS test_C_Util('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API persistent queue implementation. 
    */
   public void test_C_Queue()
   {
      Runtime runtime = Runtime.getRuntime();
      String[] commandArr = { pathToCBinary+sep+"TestQueue" };
      String[] envArr = { "" };

      log.info(ME, "######## Start test_C_Queue('" + commandArr[0] + "')");

      Execute e = new Execute(glob, commandArr, envArr);
      e.setExecuteListener(this);
      e.run();

      if (e.getExitValue() != 0) {
         fail("C client library test '" + commandArr[0] + "' + failed exit=" + e.getExitValue() + ": " + e.getStderr());
      }

      if (e.getErrorText() != null) {
         fail(e.getErrorText());
      }

      if (e.getStdout().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStdout());
      }
      if (e.getStderr().indexOf("[TEST FAIL]") != -1) {
         fail("C client library test '" + commandArr[0] + "' + failed: " + e.getStderr());
      }

      log.info(ME, "######## SUCCESS test_C_Queue('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   public void stdout(String data) {
      log.info(ME, "Native C output: " + data);
   }
   public void stderr(String data) {
      log.error(ME, "Native C output: " + data);
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
      test.test_C_Util();
      test.test_C_MethodInvocations();
      test.test_C_IllegalArguments();
      test.test_C_Queue();
      test.test_C_Stress();
      test.tearDown();
   }
}

