package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.MethodName;
import org.xmlBlaster.util.qos.ClientProperty;
import org.xmlBlaster.util.qos.QueryQosData;
import org.xmlBlaster.engine.admin.CommandWrapper;

import junit.framework.*;

/**
 * Test CommandWrapper class. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.classtest.CommandWrapperTest
 *
 * @see org.xmlBlaster.engine.admin.CommandWrapper
 */
public class CommandWrapperTest extends TestCase {
   private String ME = "CommandWrapperTest";
   protected Global glob;
   protected LogChannel log;
   private StopWatch stopWatch = new StopWatch();

   public CommandWrapperTest(String name) {
      super(name);
   }

   protected void setUp() {
      glob = new Global();
      log = glob.getLog(null);
      glob.setId("heron");
   }

   protected void tearDown() {
   }

   public void testBasic() {
      assertEquals("Wrong node id", "heron", glob.getId());
      String cmd = null;
      CommandWrapper w = null;

      try {
         cmd = "/node/heron/client/joe/?sessionList";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/?sessionList", w.getTail());
      
         cmd = "/node/heron/client/joe/";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/", w.getTail());

         cmd = "/node/heron/client/joe";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe", w.getTail());

         cmd = "/node/heron/client/";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", null, w.getTail());

         cmd = "/node/heron/client";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "/node/heron/client", w.getCommand());
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", null, w.getTail());

         cmd = "client/joe/?sessionList";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/?sessionList", w.getTail());
      
         cmd = "client";
         w = new CommandWrapper(glob, cmd, null);
         assertEquals("Command '" + cmd + "' wrong parsed", "/node/heron/client", w.getCommand());
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", null, w.getTail());
      }
      catch(XmlBlasterException e) {
         fail("Failed: " + e.toString());
      }
   }

   public void testInvalid() {
      assertEquals("Wrong node id", "heron", glob.getId());
      String cmd = null;
      CommandWrapper w = null;

      try {
         cmd = "/node/heron/";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/node/foeignNode/client/?joe";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/strangeNode/heron/client/?joe";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/node/";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = null;
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/////";
         w = new CommandWrapper(glob, cmd, null);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }
   }

   /*
   private void checkInvalid(String cmd) {
      try {
         String command = "/node/heron/client/joe/?sessionList";
         CommandWrapper w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + + "' wrong parsed", 
      }
      catch(XmlBlasterException e) {
         fail(testName + " failed: " + e.toString());
      }
   }
   */


   private void singleQosData(String cmd, boolean doCheck) {
      try {
         CommandWrapper w = new CommandWrapper(glob, cmd, new QueryQosData(this.glob, MethodName.GET));
         QueryQosData qos = w.getQueryQosData();
         this.log.info(ME, qos.toXml());
         if (!doCheck) return;
         ClientProperty clp = qos.getClientProperty("_one");
         assertNotNull("should not be null", clp);
         assertEquals("wrong value for this property", "1", clp.getStringValue());
         clp = qos.getClientProperty("_two");
         assertNotNull("should not be null", clp);
         assertEquals("wrong value for this property", "2", clp.getStringValue());
         clp = qos.getClientProperty("_three");
         assertNull("should be null", clp);
      }
      catch(XmlBlasterException e) {
         e.printStackTrace();
         assertTrue("exception should not occur here: " + e.getMessage(), false);
      }
   }

   public void testQosData() {
      String cmd = null;

      cmd = "client/joe/1/?queue";
      singleQosData(cmd, false);

      cmd = "client/joe/1/?queue&qos.one=1&qos.two=2";
      singleQosData(cmd, true);

      cmd = "client/joe/1/?queue&qos.one=1&qos.two=2&";
      singleQosData(cmd, true);

      cmd = "client/joe/1/?queue=aaa&qos.one=1&qos.two=2";
      singleQosData(cmd, true);


   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestCommandWrapperTest
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestActivateDispatcher</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println("Init failed");
         System.exit(1);
      }

      CommandWrapperTest test = new CommandWrapperTest("CommandWrapperTest");
      test.setUp();
      test.testQosData();
      test.tearDown();
   }


}
