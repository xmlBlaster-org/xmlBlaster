/*------------------------------------------------------------------------------
Name:      TestSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestSession.java,v 1.9 2002/05/20 19:03:52 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client does test login sessions.<br />
 * login/logout combinations are checked with subscribe()/publish() calls
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestSession
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestSession
 * </pre>
 */
public class TestSession extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private final Global glob;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking

   /**
    * Constructs the TestSession object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestSession(Global glob, String testName, String name)
   {
       super(testName);
       this.glob = glob;
       this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
   }

   /**
    */
   public void testZeroSessions()
   {
      Log.info(ME, "testZeroSessions() ...");
      try {
         Log.info(ME, "Connecting ...");
         XmlBlasterConnection con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         qos.setMaxSessions(-16);
         con.connect(qos, this); // Login to xmlBlaster
         assertTrue("Connecting with zero sessions should not be possible", false);
      }
      catch (Exception e) {
         Log.info(ME, "Success, can't connect with zero sessions");
      }
      Log.info(ME, "Success in testZeroSessions()");
   }


   /**
    */
   public void testSessionOverflow()
   {
      Log.info(ME, "testSessionOverflow() ...");
      int numLogin = 5;
      int maxSessions = numLogin - 2;
      XmlBlasterConnection[] con = new XmlBlasterConnection[5];
      try {
         for (int ii=0; ii<numLogin; ii++) {
            try {
               Log.info(ME, "Connecting number " + ii + " of " + numLogin + " max=" + maxSessions);
               con[ii] = new XmlBlasterConnection(glob);
               ConnectQos qos = new ConnectQos(glob, name, passwd);
               qos.setMaxSessions(maxSessions);
               con[ii].connect(qos, this); // Login to xmlBlaster
               if (ii >= maxSessions)
                  assertTrue("Connecting number " + ii + " of max=" + maxSessions + " is not allowed", false);
            }
            catch (Exception e) {
               if (ii >= maxSessions) {
                  Log.info(ME, "Success, connecting number " + ii + " of max=" + maxSessions + " was denied: " + e.toString());
               }
               else {
                  Log.error(ME, e.toString());
                  assertTrue("Connecting number " + ii + " of max=" + maxSessions + " should be possible", false);
               }
            }
         }
      }
      finally { // clean up
         try {
            for (int ii=0; ii<maxSessions; ii++) {
               DisconnectQos disQos = null;
               con[ii].disconnect(disQos);
            }
         }
         catch (Throwable e) {
            assertTrue(e.toString(), false);
         }
      }
      Log.info(ME, "Success in testSessionOverflow()");
   }


   /**
    * We login with session timeout 1 sec and sleep for 2 sec.
    * A get() invocation should fail since the session is expired.
    */
   public void testSessionTimeout()
   {
      Log.info(ME, "testSessionTimeout() ...");
      long timeout = 1000L;
      XmlBlasterConnection con = null;
      try {
         try {
            con = new XmlBlasterConnection();
            ConnectQos qos = new ConnectQos(null, name, passwd);
            qos.setSessionTimeout(timeout);
            con.connect(qos, this);
         }
         catch (Exception e) {
            Log.error(ME, e.toString());
            assertTrue("Login failed" + e.toString(), false);
         }

         try { Thread.currentThread().sleep(timeout*2); } catch (Exception e) { } // wait until session expires

         try {
            Log.info(ME, "Check access ...");
            con.get("<key oid='__sys__FreeMem'/>", null);
            assertTrue("get of expired login session is not possible", false);
         }
         catch (Exception e) {
            Log.info(ME, "Success, no access after session expiry");
         }
      }
      finally { // clean up
         try {
            con.disconnect(null);
         }
         catch (Throwable e) {
            assertTrue(e.toString(), false);
         }
      }
      Log.info(ME, "Success in testSessionTimeout()");
   }


   /**
    * We login with session timeout 1 sec, call every 500 millis get()
    * which should respan the session timeout. 
    * If this goes well for 2 sec, the refresh seems to work
    */
   public void testSessionTimeoutRespan()
   {
      Log.info(ME, "testSessionTimeoutRespan() ...");
      long timeout = 1000L;
      XmlBlasterConnection con = null;
      try {
         try {
            con = new XmlBlasterConnection();
            ConnectQos qos = new ConnectQos(null, name, passwd);
            qos.setSessionTimeout(timeout);
            con.connect(qos, this);
         }
         catch (Exception e) {
            Log.error(ME, e.toString());
            assertTrue("Login failed" + e.toString(), false);
         }

         try {
            for (int ii=0; ii<4; ii++) {
               try { Thread.currentThread().sleep(timeout/2); } catch (Exception e) { }
               Log.info(ME, "Check access #" + ii + " ...");
               con.get("<key oid='__sys__FreeMem'/>", null);
               Log.info(ME, "Check access #" + ii + " OK");
            }
         }
         catch (Exception e) {
            Log.error(ME, "No access: " + e.toString());
            assertTrue("Session is expired", false);
         }
      }
      finally { // clean up
         try {
            con.disconnect(null);
         }
         catch (Throwable e) {
            assertTrue(e.toString(), false);
         }
      }
      Log.info(ME, "Success in testSessionTimeoutRespan()");
   }


   /**
    */
   public void testClearSession()
   {
      Log.info(ME, "testClearSession() ...");
      int numLogin = 5;
      int maxSessions = numLogin - 2;
      XmlBlasterConnection[] con = new XmlBlasterConnection[5];
      for (int ii=0; ii<numLogin; ii++) {
         try {
            Log.info(ME, "Connecting number " + ii + " of " + numLogin + " max=" + maxSessions);
            con[ii] = new XmlBlasterConnection();
            ConnectQos qos = new ConnectQos(null, name, passwd);
            qos.setMaxSessions(maxSessions);
            con[ii].connect(qos, this); // Login to xmlBlaster
         }
         catch (Exception e) {
            if (ii >= maxSessions) {
               Log.info(ME, "Success, connecting number " + ii + " of max=" + maxSessions + " was denied: " + e.toString());
               Log.info(ME, "We try to clear the old sessions now");
               try {
                  ConnectQos qos = new ConnectQos(null, name, passwd);
                  qos.setMaxSessions(maxSessions);
                  qos.clearSessions(true);
                  con[ii].connect(qos, this);
                  Log.info(ME, "Success, login is possible again");
                  con[ii].get("<key oid='__sys__FreeMem'/>", null);
                  Log.info(ME, "Success, get works");
               }
               catch (Exception e2) {
                  Log.error(ME, e2.toString());
                  assertTrue("Login failed" + e2.toString(), false);
               }
            }
            else {
               Log.error(ME, e.toString());
               assertTrue("Connecting number " + ii + " of max=" + maxSessions + " should be possible", false);
            }
         }
      }
         
      // clean up
      try {
         for (int ii=maxSessions; ii<numLogin; ii++) {
            DisconnectQos disQos = null;
            con[ii].disconnect(disQos);
         }
      }
      catch (Throwable e) {
         assertTrue(e.toString(), false);
      }
      Log.info(ME, "Success in testClearSession()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.info(ME, "Receiving update of a message " + updateKey.getUniqueKey());
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
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
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
       String loginName = "Tim";
       Global glob = new Global();
       suite.addTest(new TestSession(glob, "testZeroSessions", "Tim"));
       suite.addTest(new TestSession(glob, "testSessionOverflow", "Tim"));
       suite.addTest(new TestSession(glob, "testSessionTimeout", "Tim"));
       suite.addTest(new TestSession(glob, "testSessionTimeoutRespan", "Tim"));
       suite.addTest(new TestSession(glob, "testClearSession", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java testsuite.org.xmlBlaster.TestSession
    *   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSession
    * <pre>
    */
   public static void main(String args[])
   {
      TestSession testSub = new TestSession(new Global(args), "TestSession", "Tim");
      testSub.setUp();
      testSub.testZeroSessions();
      testSub.testSessionOverflow();
      testSub.testSessionTimeout();
      testSub.testSessionTimeoutRespan();
      testSub.testClearSession();
      testSub.tearDown();
      Log.exit(TestSession.ME, "Good bye");
   }
}

