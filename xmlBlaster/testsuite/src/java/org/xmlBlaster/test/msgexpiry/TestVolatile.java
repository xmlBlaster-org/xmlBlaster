/*------------------------------------------------------------------------------
Name:      TestVolatile.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing volatile messages
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.msgexpiry;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.protocol.corba.serverIdl.Server;

import junit.framework.*;


/**
 * This client tests volatile messages, the $lt;isVolatile> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestVolatile
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestVolatile
 * </pre>
 */
public class TestVolatile extends TestCase implements I_Callback {
   private final static String ME = "TestVolatile";
   private final Global glob;
   private final LogChannel log;

   private final String senderName = "Gesa";
   private String publishOid = "HelloVolatile";
   private XmlBlasterConnection senderConnection = null;
   private String senderContent = "Some volatile content";

   private int numReceived = 0;


   /**
    * Constructs the TestVolatile object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestVolatile(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp() {
      try {
         String passwd = "secret";
         senderConnection = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login(senderName, passwd, qos, this);
      }
      catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown() {
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 0, arr.length);   // The volatile message schould not exist !!
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }

      senderConnection.disconnect(null);
   }

   /**
    * Publish a volatile message.
    * <p />
    */
   public void sendVolatile() {
      if (log.TRACE) log.trace(ME, "Testing a volatile message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <isVolatile>true</isVolatile>" +
                   "</qos>";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         String returnedOid = senderConnection.publish(msgUnit).getKeyOid();
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * Publish a volatile message.
    * <p />
    */
   public void subscribeVolatile() {
      log.info(ME, "Subscribing a volatile message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "</qos>";

      try {
         senderConnection.subscribe(xmlKey, qos);
         log.info(ME, "Subscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }

   /**
    * TEST: Publish a volatile message.
    * <p />
    */
   public void testVolatile() {
      sendVolatile();
      
      // First test: we subscribe, the volatile messages should not exist any more:
      subscribeVolatile();
      waitOnUpdate(1000L, 0);
      assertEquals("numReceived after sending", 0, numReceived); // no message arrived?
      numReceived = 0;

      // Second test: we have subscribed already, now we expect a message
      sendVolatile();
      waitOnUpdate(1000L, 1);
      assertEquals("numReceived after sending", 1, numReceived); // one message arrived?
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      log.info(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      return "";
   }

   /**
    * Little helper, waits until the wanted number of messages are arrived
    * or returns when the given timeout occurs.
    * <p />
    * @param timeout in milliseconds
    * @param numWait how many messages to wait
    */
   private void waitOnUpdate(final long timeout, final int numWait) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (numReceived < numWait) {
         try {
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warn(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestVolatile(new Global(), "testVolatile"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestVolatile
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestVolatile</pre>
    */
   public static void main(String args[]) {
      TestVolatile testSub = new TestVolatile(new Global(args), "TestVolatile");
      testSub.setUp();
      testSub.testVolatile();
      testSub.tearDown();
   }
}
