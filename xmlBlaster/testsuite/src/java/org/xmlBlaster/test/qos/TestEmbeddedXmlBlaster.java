/*------------------------------------------------------------------------------
Name:      TestEmbeddedXmlBlaster.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test start/stop xmlBlaster in a thread
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.runtime.ThreadLister;
import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.util.MsgUnit;
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
   private static Logger log = Logger.getLogger(TestEmbeddedXmlBlaster.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;

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
    * RMI fails as we don't know how to kill the registry
    * JacORB 1.3.30 can't reinitialize the orb if once shutdown (so we leave it alive)
    *        hopefully fixed with JacORB 1.4.2
    * XmlRpc has one thread open???
    *
    * <pre>
    * BEFORE:
    *
    * Thread Group: system  Max Priority: 10
    *     Thread: Signal dispatcher  Priority: 5 Daemon
    *     Thread: Reference Handler  Priority: 10 Daemon
    *     Thread: Finalizer  Priority: 8 Daemon
    *     Thread Group: main  Max Priority: 10
    *         Thread: main  Priority: 5
    *
    * AFTER:
    *
    * Thread Group: system  Max Priority: 10
    *     Thread: Signal dispatcher  Priority: 5 Daemon
    *     Thread: Reference Handler  Priority: 10 Daemon
    *     Thread: Finalizer  Priority: 8 Daemon
    *     Thread: RMI TCP Accept-1  Priority: 5 Daemon
    *     Thread: RMI TCP Accept-2  Priority: 5 Daemon
    *     Thread: GC Daemon  Priority: 2 Daemon
    *     Thread: RMI RenewClean-[192.168.1.3:33493]  Priority: 5 Daemon
    *     Thread: RMI LeaseChecker  Priority: 5 Daemon
    *     Thread: RMI ConnectionExpiration-[192.168.1.3:33493]  Priority: 5 Daemon
    *     Thread: RMI ConnectionExpiration-[kinder:7613]  Priority: 5 Daemon
    *     Thread Group: main  Max Priority: 10
    *         Thread: XmlBlaster MainThread  Priority: 5
    *         Thread: Thread-11  Priority: 10 Daemon
    *         Thread: JacORB Listener Thread on port 33489  Priority: 5 Daemon
    *         Thread: Thread-14  Priority: 10 Daemon
    *         Thread: Thread-16  Priority: 5 Daemon
    *         Thread Group: XMLRPC Runner  Max Priority: 10
    *     Thread Group: RMI Runtime  Max Priority: 10
    *         Thread: TCP Connection(3)-192.168.1.3  Priority: 5 Daemon
    *         Thread: TCP Connection(4)-192.168.1.3  Priority: 5 Daemon
    * </pre>
    */
   public void testThreadFree()
   {
      log.info("######## Start testThreadFree()");

      int threadsBefore = ThreadLister.countThreads();
      log.info("Testing thread consume before xmlBlaster startup=" + threadsBefore);
      ThreadLister.listAllThreads(System.out);

      // Start xmlBlaster
      // We register here the demo plugin with xmlBlaster server, supplying an argument to the plugin
      String[] args = {
         "-bootstrapPort",        // For all protocol we may use set an alternate server port
         "" + serverPort,
         "-plugin/socket/port",
         "" + (serverPort-1),
         "-plugin/rmi/registryPort",
         "" + (serverPort-2),
         "-plugin/xmlrpc/port",
         "" + (serverPort-3),
         "-ProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CorbaDriver",
         "-CbProtocolPlugin[IOR][1.0]",
         "org.xmlBlaster.protocol.corba.CallbackCorbaDriver"
      };
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info("XmlBlaster is ready for testing JDBC access");

      // Stop xmlBlaster
      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;


      ThreadLister.listAllThreads(System.out);
      int threadsAfter = ThreadLister.countThreads();
      log.info("Currently used threads after server startup/shutdown" + threadsAfter);
      int allow = threadsBefore + 1; // This 1 thread is temporary
      assertTrue("We have a thread leak, threadsBefore=" + threadsBefore + " threadsAfter=" + threadsAfter, threadsAfter <= allow);

   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestEmbeddedXmlBlaster
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

