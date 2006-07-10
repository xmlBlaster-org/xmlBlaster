/*------------------------------------------------------------------------------
Name:      TestCallback.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.property.Args;

import org.xmlBlaster.test.Util;
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
   private static Logger log = Logger.getLogger(TestCallback.class.getName());
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking

   private boolean isDeadMessage = false;
   private String subscribeDeadMessageOid = null;
   private I_XmlBlasterAccess conAdmin = null;
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
      else {
         driverType = glob.getProperty().get("protocol", "dummy");
         if (driverType.equalsIgnoreCase("SOCKET"))
            isSocket = true;
      }

      if (isSocket) {
         log.warning("callback test ignored for driverType=" + driverType + " as callback server uses same socket as invoke channel");
         return;
      }

      try {
         Global globAdmin = this.glob.getClone(null);
         conAdmin = globAdmin.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(globAdmin, "admin", passwd);
         conAdmin.connect(qos, this);

         subscribeDeadMessageOid = conAdmin.subscribe("<key oid='__sys__deadMessage'/>", null).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeDeadMessageOid + " done");
      }
      catch (Exception e) {
         log.severe(e.toString());
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
            EraseReturnQos[] strArr = conAdmin.erase("<key oid='" + publishOid + "'/>", null);
            if (strArr.length != 1) log.severe("ERROR: Erased " + strArr.length + " messages");
            conAdmin.disconnect(null);
         }
      }
      catch (Exception e) {
         log.severe(e.toString());
         e.printStackTrace();
         assertTrue(e.toString(), false);
      }
   }

   /**
    * We expect dead letters after destroying our callback server. 
    */
   public void testCallbackFailure()
   {
      if (isSocket) return;
      log.info("testCallbackFailure() ...");
      try {
         log.info("Connecting ...");
         Global globSub = this.glob.getClone(null);
         I_XmlBlasterAccess con = globSub.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(globSub, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster

         try {
            con.getCbServer().shutdown(); // Destroy the callback server
         }
         catch (Throwable e) {
            log.severe("testCallbackFailure: " + e.toString());
            fail(e.toString());
         }

         String subscribeOid = con.subscribe("<key oid='testCallbackMsg'/>", null).getSubscriptionId();
         log.info("Success: Subscribe on " + subscribeOid + " done");

         MsgUnit msgUnit = new MsgUnit("<key oid='testCallbackMsg'/>", "Bla".getBytes(), null);
         publishOid = con.publish(msgUnit).getKeyOid();
         log.info("Success: Publishing done, returned oid=" + publishOid);

         waitOnUpdate(2000L, 1);
         assertTrue("Expected a dead letter", isDeadMessage);
         isDeadMessage = false;

         //Global.waitOnKeyboardHit("Session destroyed? Hit a key to continue");
         
         // Check with user "admin" if session of "Tim" has disappeared
         try {
            MsgUnit[] msgs = Util.adminGet(glob, "__cmd:?clientList");
            assertEquals("Can't access __cmd:?clientList", 1, msgs.length);
            log.info("Got clientList=" + msgs[0].getContentStr());
            assertEquals("Session of " + name + " was not destroyed by failing callback",
                      -1, msgs[0].getContentStr().indexOf(name));
         }
         catch (XmlBlasterException e) {
            fail("Session was not destroyed: " + e.toString());
         }
      }
      catch (Exception e) {
         log.severe(e.toString());
         assertTrue(e.toString(), false);
      }
      log.info("Success in testCallbackFailure()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Receiving update of a message " + updateKey.getOid());
      numReceived++;
      isDeadMessage = updateKey.isDeadMessage();
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
         try { Thread.sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.sleep(timeout); } catch( InterruptedException i) {}
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
    *  java org.xmlBlaster.test.qos.TestCallback -dispatch/callback/retries 0 -dispatch/callback/delay 3000
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

