/*------------------------------------------------------------------------------
Name:      TestUpdateClientException.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.def.ErrorCode;


/**
 * This client does test exceptions thrown in the update() method on client side.<br />
 * <p />
 * XmlBlaster should only accept exceptions of type ErrorCode.USER_UPDATE* and send the lost messages
 * as 'dead messages' and proceed.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestUpdateClientException
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestUpdateClientException
 * </pre>
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.update.html">The interface.update requirement</a>
 */
public class TestUpdateClientException extends TestCase implements I_Callback
{
   private static String ME = "TestUpdateClientException";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestUpdateClientException.class.getName());

   private I_XmlBlasterAccess con = null;
   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking
   private String updateOid;
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7624;
   private int filterMessageContentBiggerAs = 10;
   private String msgOidWantException = "msgWantsUpdateException";
   private String msgOidWantIllegalException = "msgWantsIllegalUpdateException";
   private String msgOidWantNPE = "msgWantsNullPointerException";
   private String msgOidNormal = "msgWantsNoUpdateException";

   /**
    * Constructs the TestUpdateClientException object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestUpdateClientException(Global glob, String testName, String name)
   {
      super(testName);
      this.glob = glob;

      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it has configured to load our simple demo MIME filter plugin.
    * <p />
    * Then we connect as a client
    */
   protected void setUp()
   {
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
            "-MimeAccessPlugin[ContentLenFilter][1.0]",
            "org.xmlBlaster.engine.qos.demo.ContentLenFilter,DEFAULT_MAX_LEN=200,THROW_EXCEPTION_FOR_LEN=3",
             "-admin.remoteconsole.port",
            "0"};
      glob.init(args);

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(args);
      log.info("XmlBlaster is ready for testing subscribe MIME filter");

      try {
         log.info("Connecting ...");
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.severe("Can't connect to xmlBlaster: " + e.toString());
      }

      // Subscribe to a message with a supplied filter
      try {
         SubscribeQos qos = new SubscribeQos(glob);

         String subscriptionId = con.subscribe("<key oid='" + msgOidWantException + "'/>", qos.toXml()).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscriptionId + " on oid="  + msgOidWantException + " done");

         subscriptionId = con.subscribe("<key oid='" + msgOidWantIllegalException + "'/>", qos.toXml()).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscriptionId + " on oid="  + msgOidWantIllegalException + " done");

         subscriptionId = con.subscribe("<key oid='" + msgOidWantNPE + "'/>", qos.toXml()).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscriptionId + " on oid="  + msgOidWantNPE + " done");

         subscriptionId = con.subscribe("<key oid='" + msgOidNormal + "'/>", qos.toXml()).getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscriptionId + " on oid="  + msgOidNormal + " done");

         subscriptionId = con.subscribe("<key oid='" + Constants.OID_DEAD_LETTER + "'/>", "<qos/>").getSubscriptionId();
         log.info("Success: Subscribe subscription-id=" + subscriptionId + " on oid="  + Constants.OID_DEAD_LETTER + " done");
        
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      try {
         EraseReturnQos[] arr = con.erase("<key oid='"+msgOidNormal+"'/>", "<qos/>");
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      con.disconnect(null);
      con=null;

      try { Thread.sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * We throw different Exception types in the update() method back to xmlBlaster. 
    */
   public void testException()
   {
      log.info("testException() with filterMessageContentBiggerAs=" + filterMessageContentBiggerAs + " ...");

      log.info("TEST 1: Send a message which triggers an exception in update");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidWantException + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 2);


      log.info("TEST 2: Send a normal message, check if everything still works");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidNormal + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 1);

      log.info("TEST 3: Send a message which triggers an illegal exception in update");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidWantIllegalException + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 2);

      log.info("TEST 4: Send a normal message, check if everything still works");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidNormal + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 1);

      log.info("TEST 5: Send a message which triggers a nullpointer exception");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidWantNPE + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 2);

      log.info("TEST 6: Send a normal message, check if everything still works");
      try {
         con.publish(new MsgUnit("<key oid='" + msgOidNormal + "'/>", "Hello".getBytes(), null));
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
      waitOnUpdate(2000L, 1);

      log.info("Success in testException()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) throws XmlBlasterException
   {
      log.info("Receiving update of a message '" + updateKey.getOid() + "' state=" + updateQos.getState());
      updateOid = updateKey.getOid();
      numReceived++;

      if (msgOidWantException.equals(updateKey.getOid()))
         throw new XmlBlasterException(glob, ErrorCode.USER_UPDATE_ERROR, ME, "Test exception from clients update() method");

      if (msgOidWantIllegalException.equals(updateKey.getOid()))
         throw new XmlBlasterException(glob, ErrorCode.RESOURCE_CONFIGURATION_PLUGINFAILED, ME, "Test illegal exception from clients update() method");

      if (msgOidWantNPE.equals(updateKey.getOid()))
         throw new NullPointerException("An artifical NullPointerException");

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
       suite.addTest(new TestUpdateClientException(new Global(), "testException", "Tim"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.qos.TestUpdateClientException
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestUpdateClientException
    * <pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestUpdateClientException testSub = new TestUpdateClientException(glob, "TestUpdateClientException", "Tim");
      testSub.setUp();
      testSub.testException();
      testSub.tearDown();
   }
}

