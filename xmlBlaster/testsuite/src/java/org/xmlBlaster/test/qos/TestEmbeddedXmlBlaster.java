/*------------------------------------------------------------------------------
Name:      TestEmbeddedXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test start/stop xmlBlaster in a thread
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.runtime.ThreadLister;
import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.GetKeyWrapper;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.XmlDbMessageWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client tests to start an xmlBlaster server in a thread
 * and stop it again.
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.runlevel.html">engine.runlevel</a>
 * @see org.xmlBlaster.util.EmbeddedXmlBlaster
 */
public class TestEmbeddedXmlBlaster extends TestCase
{
   private static final String ME = "TestEmbeddedXmlBlaster";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;

   private XmlDbMessageWrapper wrap = null;

   /**
    * Constructs the TestEmbeddedXmlBlaster object. 
    * <p />
    * @param testName   The name used in the test suite
    */
   public TestEmbeddedXmlBlaster(String testName) {
      this(Global.instance(), testName);
   }

   public TestEmbeddedXmlBlaster(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog(null);
      this.name = testName; // name to login to xmlBlaster
   }

   /**
    * Sets up the fixture.
    */
   protected void setUp()
   {
   }

   /**
    * Tears down the fixture.
    */
   protected void tearDown()
   {
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    */
   public void testThreadFree()
   {
      log.info(ME, "######## Start testThreadFree()");

      int threadsBefore = ThreadLister.countThreads();
      log.info(ME, "Testing thread consume before xmlBlaster startup=" + threadsBefore);
      ThreadLister.listAllThreads(System.out);

      // Start xmlBlaster
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = {
         "-port",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-socket.port",
         "" + (serverPort-1),
         "-rmi.registryPort",
         "" + (serverPort-2),
         "-xmlrpc.port",
         "" + (serverPort-3),
         "-client.port",
         "" + serverPort,
         "-ProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CorbaDriver",
         "-CbProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CallbackCorbaDriver"
      };
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing JDBC access");


      // Stop xmlBlaster
      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);


      ThreadLister.listAllThreads(System.out);
      int threadsAfter = ThreadLister.countThreads();
      log.info(ME, "Currently used threads after server startup/shutdown" + threadsAfter);
      int allow = threadsBefore + 1; // This 1 thread is temporary
      assertTrue("We have a thread leak, threadsBefore=" + threadsBefore + " threadsAfter=" + threadsAfter, threadsAfter <= allow);

   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }
      TestEmbeddedXmlBlaster test = new TestEmbeddedXmlBlaster(glob, "TestEmbeddedXmlBlaster");
      test.setUp();
      test.testThreadFree();
      test.tearDown();
   }
}

