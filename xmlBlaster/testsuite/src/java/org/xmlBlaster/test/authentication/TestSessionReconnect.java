/*------------------------------------------------------------------------------
Name:      TestSessionReconnect.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionProblems;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.engine.RunlevelManager;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client does test login sessions. 
 * <br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionReconnect
 *    java junit.swingui.TestRunner org.xmlBlaster.test.authentication.TestSessionReconnect
 * </pre>
 */
public class TestSessionReconnect extends TestCase implements I_Callback
{
   private static String ME = "TestSessionReconnect";
   private final Global glob;
   private final LogChannel log;
   private String name;
   private String passwd = "secret";
   private int serverPort = 7615;
   private int numReceived = 0;         // error checking
   private EmbeddedXmlBlaster serverThread = null;
   private XmlBlasterConnection con;
   private boolean connected = false;

   /** For Junit */
   public TestSessionReconnect() {
      this(new Global(), "TestSessionReconnect");
   }

   /**
    * Constructs the TestSessionReconnect object.
    * <p />
    * @param testName   The name used in the test suite and to login to xmlBlaster
    */
   public TestSessionReconnect(Global glob, String testName)
   {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog(null);
       this.name = testName;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      glob.init(Util.getOtherServerPorts(serverPort));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing a big message");
   }

   /**
    * Cleaning up. 
    */
   protected void tearDown()
   {
      try { Thread.currentThread().sleep(1000);} catch(Exception ex) {} 
      if (serverThread != null)
         serverThread.stopServer(true);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    */
   public void testSessionReconnect()
   {
      log.info(ME, "testSessionReconnect() ...");
      try {
         con = new XmlBlasterConnection(glob);
         con.initFailSave(new I_ConnectionProblems() {
               
               public void reConnected() {
                  connected = true;
                  ConnectReturnQos conRetQos = con.getConnectReturnQos();
                  log.info(ME, "I_ConnectionProblems: We were lucky, connected to " + glob.getId() + " as " + conRetQos.getSessionName());
                  //initClient();    // initialize subscription etc. again
                  try {
                     con.flushQueue();    // send all tailback messages
                     // con.resetQueue(); // or discard them (it is our choice)
                  } catch (XmlBlasterException e) {
                     log.error(ME, "Exception during reconnection recovery: " + e.getMessage());
                  }
               }

               public void lostConnection() {
                  log.warn(ME, "I_ConnectionProblems: No connection to " + glob.getId());
                  connected = false;
               }
            });
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
         {  
            MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);
            assertEquals("Get test failed", 1, msgs.length);
         }

         int numErrors = serverThread.getMain().getGlobal().getRunlevelManager().changeRunlevel(RunlevelManager.RUNLEVEL_STANDBY, true);
         try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}

         log.error(ME, "TESTCODE IS MISSING !!! ");
         if (false) {
            log.info(ME, "Trying to get messages from shutdown server");
            MsgUnit[] msgs = con.get("<key oid='__cmd:?freeMem'/>", null);
            assertEquals("Get test failed", 0, msgs.length);
         }

      }
      catch (XmlBlasterException e) {
         log.error(ME, e.toString());
         fail(e.toString());
      }
      finally { // clean up
         con.disconnect(null);
      }
      log.info(ME, "Success in testSessionReconnect()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message " + updateKey.getOid());
      numReceived++;
      return "";
   }


   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      // check if too few are arriving
      while (numReceived < numWait) {
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("TestSessionReconnecteout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
      assertEquals("Wrong number of messages arrived", numWait, numReceived);

      numReceived = 0;
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "TestSessionReconnect";
       Global glob = new Global();
       suite.addTest(new TestSessionReconnect(glob, "testSessionReconnect"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.authentication.TestSessionReconnect
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionReconnect
    * <pre>
    */
   public static void main(String args[])
   {
      TestSessionReconnect testSub = new TestSessionReconnect(new Global(args), "TestSessionReconnect");
      testSub.setUp();
      testSub.testSessionReconnect();
      testSub.tearDown();
   }
}

