/*------------------------------------------------------------------------------
Name:      TestFailSavePing.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.init.Property;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.enum.ErrorCode;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;
import org.xmlBlaster.client.I_ConnectionHandler;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * Tests the fail save behavior of the I_XmlBlasterAccess client helper class,
 * especially the pinging to xmlBlaster. This allows auto detection if the
 * connection to xmlBlaster is lost.
 *
 * <br />For a description of what this fail save mode can do for you, please
 * read the API documentation of I_XmlBlasterAccess.
 * <p>
 * This is an interesting example, since it creates a XmlBlaster server instance
 * in the same JVM , but in a separate thread, talking over CORBA with it.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner org.xmlBlaster.test.qos.TestFailSavePing
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestFailSavePing
 * </pre>
 */
public class TestFailSavePing extends TestCase implements I_Callback, I_ConnectionStateListener
{
   private static String ME = "TestFailSavePing";
   private final Global glob;
   private final LogChannel log;
   private boolean messageArrived = false;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private I_XmlBlasterAccess con;
   private String senderName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/plain";

   /**
    * Constructs the TestFailSavePing object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestFailSavePing(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
      this.senderName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      try {
         numReceived = 0;

         con = glob.getXmlBlasterAccess(); // Find server

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(4000L);         // retry connecting every 4 sec
         addressProp.setRetries(-1);          // -1 == forever
         addressProp.setPingInterval(1000L);  // ping every second
         con.registerConnectionListener(this);

         connectQos.setAddress(addressProp);
         
         // and do the login ...
         con.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed");
      }
      catch (Exception e) {
          log.error(ME, "setUp() - login failed: " + e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      log.info(ME, "Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSavePing-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);
         assertEquals("Wrong number of message erased", 1, arr.length);
      } catch(XmlBlasterException e) { assertTrue("tearDown - XmlBlasterException: " + e.getMessage(), false); }

      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      con.disconnect(null);

      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void testSubscribe()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSavePing-AGENT" +
                      "</key>";
      String qos = "<qos></qos>";
      try {
         String subscribeOid = con.subscribe(xmlKey, qos).getSubscriptionId();
         log.info(ME, "Success: Subscribe on " + subscribeOid + " done");
         assertTrue("returned null subscribeOid", subscribeOid != null);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void testPublish(int counter) throws XmlBlasterException
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      String oid = "Message" + "-" + counter;
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestFailSavePing-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestFailSavePing-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      con.publish(msgUnit);
      log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: <br />
    */
   public void testFailSave()
   {
      testSubscribe();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}    // Wait some time, ping should activate login polling

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}    // Wait some time, to allow the ping to reconnect

      numReceived = 0;

      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      try { Thread.currentThread().sleep(5000L); } catch( InterruptedException i) {}    // Wait some time, ping should activate login polling

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
      try { Thread.currentThread().sleep(5000L); } catch( InterruptedException i) {}    // Wait some time, to allow the ping to reconnect
   }


   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
      log.info(ME, "I_ConnectionStateListener: We were lucky, reconnected to xmlBlaster");
      testSubscribe();    // initialize subscription again
      try {
         testPublish(1);
         waitOnUpdate(2000L);
         assertEquals("numReceived is wrong", 1, numReceived);
      }
      catch(XmlBlasterException e) {
         if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_POLLING)
            log.warn(ME, "Lost connection, my connection layer is polling: " + e.getMessage());
         else if (e.getErrorCode() == ErrorCode.COMMUNICATION_NOCONNECTION_DEAD)
            assertTrue("Lost connection, my connection layer is not polling", false);
         else
            assertTrue("Publishing problems: " + e.getMessage(), false);
      }

      connectionHandler.getQueue().clear(); // discard messages (dummy)
   }


   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
      log.warn(ME, "I_ConnectionStateListener: Lost connection to xmlBlaster");
   }

   public void reachedDead(ConnectionStateEnum oldState, I_ConnectionHandler connectionHandler) {
      log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of message oid=" + updateKey.getOid() + " ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      String oid = "Message-1";
      assertEquals("Message oid is wrong", oid, updateKey.getOid());

      messageArrived = true;
      return "";
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
   private void waitOnUpdate(final long timeout)
   {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (!messageArrived) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.info(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived = false;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestFailSavePing(new Global(), "testFailSave", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestFailSavePing
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestFailSavePing</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestFailSavePing testSub = new TestFailSavePing(glob, "TestFailSavePing", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
   }
}

