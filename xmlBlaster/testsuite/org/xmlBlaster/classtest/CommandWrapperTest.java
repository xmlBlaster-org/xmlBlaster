package classtest;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.admin.CommandWrapper;

import junit.framework.*;

/**
 * Test CommandWrapper class. 
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner classtest.CommandWrapperTest
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
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/?sessionList", w.getTail());
      
         cmd = "/node/heron/client/joe/";
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/", w.getTail());

         cmd = "/node/heron/client/joe";
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe", w.getTail());

         cmd = "/node/heron/client/";
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", null, w.getTail());

         cmd = "/node/heron/client";
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "/node/heron/client", w.getCommand());
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", null, w.getTail());

         cmd = "client/joe/?sessionList";
         w = new CommandWrapper(glob, cmd);
         assertEquals("Command '" + cmd + "' wrong parsed", "node", w.getRoot());
         assertEquals("Command '" + cmd + "' wrong parsed", "heron", w.getClusterNodeId());
         assertEquals("Command '" + cmd + "' wrong parsed", "client", w.getThirdLevel());
         assertEquals("Command '" + cmd + "' wrong parsed", "joe/?sessionList", w.getTail());
      
         cmd = "client";
         w = new CommandWrapper(glob, cmd);
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
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/node/foeignNode/client/?joe";
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/strangeNode/heron/client/?joe";
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/node/";
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/";
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "";
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = null;
         w = new CommandWrapper(glob, cmd);
         fail("Failed, expected exception for '" + cmd + "'");
      }
      catch(XmlBlasterException e) {
         System.out.println("OK - expected Exception: " + e.toString());
      }

      try {
         cmd = "/////";
         w = new CommandWrapper(glob, cmd);
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
}
