/*------------------------------------------------------------------------------
Name:      TestCorbaThreads.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestCorbaThreads.java,v 1.3 2000/06/18 15:22:01 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.jutils.log.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;
import org.xmlBlaster.protocol.corba.serverIdl.*;
import org.xmlBlaster.protocol.corba.clientIdl.*;
import test.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style.
 * <p>
 * Note that the three clients (client logins) are simulated in this class.<br />
 * Tests performed:<br />
 * <ul>
 *    <li>Manuel is the 'sender' and Ulrike the 'receiver' of a love letter</li>
 *    <li>Manuel sends a message to two destinations</li>
 * </ul>
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestCorbaThreads
 *
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestCorbaThreads
 * </pre>
 */
public class TestCorbaThreads extends TestCase implements I_Callback
{
   private Server xmlBlaster = null;
   private final static String ME = "TestCorbaThreads";

   private final String loginName = "Manuel";
   private String publishOid = "";
   private CorbaConnection corbaConnection = null;
   private String corbaContent;
   private int numReceived = 0;


   /**
    * Constructs the TestCorbaThreads object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestCorbaThreads(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    * - One connection for the receiver client
    * - One connection for the receiver2 client
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";

         corbaConnection = new CorbaConnection();
         xmlBlaster = corbaConnection.login(loginName, passwd, new LoginQosWrapper(), this);
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown()
   {
      if (corbaConnection != null) {
         Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...
         corbaConnection.logout();
         corbaConnection = null;
         xmlBlaster = null;
         System.gc();
      }
   }


   /**
    * TEST: Send a message to one destination
    * <p />
    * The returned subscribeOid is checked
    */
   public void testThread()
   {
      Log.info(ME, "Testing thread consume on multiple login/logouts, used threads before any login=" + ThreadLister.countThreads());
      ThreadLister.listAllThreads(System.out);
      int threadsBefore = 0;

      for (int ii=0; ii<5; ii++) {
         Log.info(ME, "Testing login/logout no = " + ii);
         setUp();
         tearDown();
         if (ii==0) {
            threadsBefore = ThreadLister.countThreads();
            Log.info(ME, "Testing thread consume on multiple login/logouts, used threads after first login=" + threadsBefore);
            ThreadLister.listAllThreads(System.out);
         }
      }
      ThreadLister.listAllThreads(System.out);
      int threadsAfter = ThreadLister.countThreads();
      Log.info(ME, "Currently used threads after 5 login/logout=" + threadsAfter);
      int allow = threadsBefore + 1; // This 1 thread is temporary
      assert("We have a thread leak, threadsBefore=" + threadsBefore + " threadsAfter=" + threadsAfter, threadsAfter <= allow);
   }


   /**
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of a message ...");
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestCorbaThreads("testThread"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestCorbaThreads
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestCorbaThreads</pre>
    */
   public static void main(String args[])
   {
      Log.setLogLevel(args);
      TestCorbaThreads testSub = new TestCorbaThreads("TestCorbaThreads");
      testSub.testThread();
      Log.exit(TestCorbaThreads.ME, "Good bye");
   }
}
