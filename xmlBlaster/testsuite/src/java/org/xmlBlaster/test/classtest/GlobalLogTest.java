package org.xmlBlaster.test.classtest;

import org.xmlBlaster.util.Global;
import org.jutils.log.*;

import junit.framework.*;

/**
 * Test util Global. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * @see org.xmlBlaster.util.Global
 */
public class GlobalLogTest extends TestCase {
   protected Global glob;
   int counter = 0;

   public GlobalLogTest(String name) {
      super(name);
   }

   protected void setUp() {
      System.out.println("***GlobalLogTest: setup ...");
      String[] args = new String[2];
      args[0] = "-call[cluster]";
      args[1] = "true";
      glob = new Global(args);
   }

   public void testLog() {
      System.out.println("***GlobalLogTest: testLog ...");
      
      counter = 0;
      
      class MyApp implements LogableDevice {
         MyApp() {}
         // Event fired by Log.java through interface LogableDevice.
         public void log(int level, String source, String str) {
            //System.out.println("MyApp: " + LogChannel.bitToLogLevel(level) + " " + source + ": " + str + " counter=" + counter);
            if (!str.equals("OK") && !str.equals("ERROR"))
               return; // Ignore logs from Global etc.
            assertEquals("Logging device error", "OK", str);
            counter++;
         }
      }

      assertEquals("call = true is lost", true, glob.getProperty().get("call[cluster]", true));

      LogChannel clusterChannel = new LogChannel("cluster", glob.getProperty());
      MyApp myApp = new MyApp();
      clusterChannel.addLogDevice(myApp);
      glob.addLogChannel(clusterChannel);

      LogChannel logC = glob.getLog("cluster");

      //System.out.println("***GlobalLogTest: logC=" + logC.toXml());

      assertEquals("call = true is lost", true, logC.isLoglevelEnabled(LogChannel.LOG_CALL));
      assertEquals("trace = false is lost", false, logC.isLoglevelEnabled(LogChannel.LOG_TRACE));

      logC.info("ClusterManager", "OK");
      if (logC.TRACE) logC.trace("ClusterManager", "ERROR");
      if (logC.CALL) logC.call("ClusterManager", "OK");
      if (logC.DUMP) logC.info("ClusterManager", "ERROR");

      assertEquals("logging was not called correct", 2, counter);

      System.out.println("***GlobalLogTest: testLog [SUCCESS]");
   }

   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.GlobalLogTest
    * </pre>
    */
   public static void main(String args[])
   {
      GlobalLogTest testSub = new GlobalLogTest("GlobalLogTest");
      testSub.setUp();
      testSub.testLog();
      //testSub.tearDown();
   }
}
