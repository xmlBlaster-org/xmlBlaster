/*------------------------------------------------------------------------------
Name:      TestCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestCallback.java,v 1.1 2002/09/12 21:01:43 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client test dead letter generation on callback problems. 
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 */
public class TestCallback extends TestCase implements I_Callback
{
   private static String ME = "TestCallback";
   private final Global glob;
   private final LogChannel log;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking

   private boolean isDeadLetter = false;
   private String subscribeDeadLetterOid = null;
   private XmlBlasterConnection conAdmin = null;
   private String publishOid = null;

   private boolean isSocket = false;

   /**
    * Constructs the TestCallback object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestCallback(Global glob, String testName, String name)
   {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog("test");
       this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login as admin, subscribe to dead letters
    */
   protected void setUp()
   {
      String driverType = glob.getProperty().get("client.protocol", "dummy");
      if (driverType.equalsIgnoreCase("SOCKET"))
         isSocket = true;

      if (isSocket) {
         log.warn(ME, "callback test ignored for driverType=" + driverType + " as callback server uses same socket as invoce channel");
         return;
      }

      try {
         conAdmin = new XmlBlasterConnection();
         ConnectQos qos = new ConnectQos(glob, "admin", passwd);
         conAdmin.connect(qos, this);

         subscribeDeadLetterOid = conAdmin.subscribe("<key oid='__sys__deadLetter'/>", null).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeDeadLetterOid + " done");
      }
      catch (Exception e) {
         log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      if (isSocket) return;
      try {
         if (conAdmin != null) {
            EraseRetQos[] strArr = conAdmin.erase("<key oid='" + publishOid + "'/>", null);
            if (strArr.length != 1) log.error(ME, "ERROR: Erased " + strArr.length + " messages");
            conAdmin.disconnect(new DisconnectQos());
         }
      }
      catch (Exception e) {
         log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
   }

   /**
    * We expect dead letters after destroying our callback server. 
    */
   public void testCallbackFailure()
   {
      if (isSocket) return;
      log.info(ME, "testCallbackFailure() ...");
      try {
         log.info(ME, "Connecting ...");
         XmlBlasterConnection con = new XmlBlasterConnection();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster

         con.shutdownCb(); // Destroy the callback server

         String subscribeOid = con.subscribe("<key oid='testCallbackMsg'/>", null).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");

         MessageUnit msgUnit = new MessageUnit("<key oid='testCallbackMsg'/>", "Bla".getBytes(), null);
         publishOid = con.publish(msgUnit).getOid();
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);

         waitOnUpdate(2000L, 1);
         assertTrue("Expected a dead letter", isDeadLetter);
         isDeadLetter = false;

         try { // this should fail
            con.subscribe("<key oid='testCallbackMsg'/>", null);
            assertTrue("Session should be destroyed by xmlBlaster", false);
            //con.disconnect(null);
         }
         catch (Exception e2) {
            log.info(ME, "SUCCESS: The session was destroyed by xmlBlaster");
         }
      }
      catch (Exception e) {
         log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
      log.info(ME, "Success in testCallbackFailure()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message " + updateKey.getUniqueKey());
      numReceived++;
      isDeadLetter = updateKey.isDeadLetter();
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
       suite.addTest(new TestCallback(new Global(), "testCallbackFailure", "Tim"));
       return suite;
   }

   /**
    * Invoke:
    * <pre>
    *  java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestCallback
    *
    *  java org.xmlBlaster.test.qos.TestCallback -cb.retries 0 -cb.delay 3000
    * </pre>
    */
   public static void main(String args[])
   {
      TestCallback testSub = new TestCallback(new Global(args), "TestCallback", "Tim");
      testSub.setUp();
      testSub.testCallbackFailure();
      testSub.tearDown();
   }
}

