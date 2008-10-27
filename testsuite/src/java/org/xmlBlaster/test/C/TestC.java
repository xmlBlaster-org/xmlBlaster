/*------------------------------------------------------------------------------
Name:      TestC.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Integrate C tests into junit reports
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.C;

import java.io.File;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.Execute;
import org.xmlBlaster.util.I_ExecuteListener;

//import java.io.*;

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
   private final Global glob;
   private static Logger log = Logger.getLogger(TestC.class.getName());

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
      log.info("XmlBlaster is ready for testing C client library");
      */

      /* Find the location of the C binaries */
      String xmlBlasterHome = getXmlBlasterHomePath();
      this.pathToCBinary = xmlBlasterHome+sep+"testsuite"+sep+"src"+sep+"c"+sep+"bin";
      File f = new File(this.pathToCBinary);
      log.fine("Looking under '" + f.toString() + "'");
      if (f.exists()) {
         log.info("Found C executables under '" + this.pathToCBinary + "'");
      }
      assertTrue("Path to C binaries not found, no testing of C client library is not possible", this.pathToCBinary!=null);
   }

   /**
    * @return for example ""
    */
   private String getXmlBlasterHomePath() {
      String xmlBlasterHome = glob.getProperty().get("XMLBLASTER_HOME", "$HOME"+sep+"xmlBlaster");
      File f = new File(xmlBlasterHome);
      if (f.exists()) {
         return xmlBlasterHome;
      }
      xmlBlasterHome = "..";
      f = new File(xmlBlasterHome+"RELEASE_NOTES");
      if (f.exists()) {
         return xmlBlasterHome;
      }
      for (int i=0; i<10; i++) {
         xmlBlasterHome += sep+"..";
         f = new File(xmlBlasterHome+sep+"RELEASE_NOTES");
         if (f.exists())
            return xmlBlasterHome;
      }
      return null;
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
      String[] commandArr = { pathToCBinary+sep+"TestMethods" };
      String[] envArr = { "" };

      log.info("######## Start test_C_MethodInvocations('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_MethodInvocations('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API with illegal arguments. 
    */
   public void test_C_IllegalArguments()
   {
      String[] commandArr = { pathToCBinary+sep+"TestError" };
      String[] envArr = { "" };

      log.info("######## Start test_C_IllegalArguments('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_IllegalArguments('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_Stress()
   {
      String[] commandArr = { pathToCBinary+sep+"TestStress" };
      String[] envArr = { "" };

      log.info("######## Start test_C_Stress('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_Stress('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_Util()
   {
      String[] commandArr = { pathToCBinary+sep+"TestUtil" };
      String[] envArr = { "" };

      log.info("######## Start test_C_Util('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_Util('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test all C method invocations against a running xmlBlaster. 
    */
   public void test_C_XmlUtil()
   {
      String[] commandArr = { pathToCBinary+sep+"TestXmlUtil" };
      String[] envArr = { "" };

      log.info("######## Start test_C_XmlUtil('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_XmlUtil('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   public void test_C_Timeout()
   {
      String[] commandArr = { pathToCBinary+sep+"TestTimeout" };
      String[] envArr = { "" };

      log.info("######## Start test_C_Timeout('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_Timeout('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API persistent queue implementation. 
    */
   public void test_C_Queue()
   {
      String[] commandArr = { pathToCBinary+sep+"TestQueue" };
      String[] envArr = { "" };

      log.info("######## Start test_C_Queue('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_Queue('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API implementation, leaving the server without disconnect. 
    */
   public void test_C_LeaveServer()
   {
      String[] commandArr = { pathToCBinary+sep+"TestLeaveServer" };
      String[] envArr = { "" };

      log.info("######## Start test_C_LeaveServer('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_LeaveServer('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   /**
    * Test the C API SOCKET implementation. 
    */
   public void test_C_Socket()
   {
      String[] commandArr = { pathToCBinary+sep+"TestSocket" };
      String[] envArr = { "" };

      log.info("######## Start test_C_Socket('" + commandArr[0] + "')");

      Execute e = new Execute(commandArr, envArr);
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

      log.info("######## SUCCESS test_C_Socket('" + commandArr[0] + "') exit=" +
               e.getExitValue() + " : " + e.getStdout());
   }

   public void stdout(String data) {
      log.info("Native C output: " + data);
   }
   public void stderr(String data) {
      log.severe("Native C output: " + data);
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
      test.test_C_XmlUtil();
      test.test_C_Timeout();
      test.test_C_MethodInvocations();
      test.test_C_IllegalArguments();
      test.test_C_Queue();
      test.test_C_LeaveServer();
      test.test_C_Socket();
      test.test_C_Stress();
      test.tearDown();
   }
}

