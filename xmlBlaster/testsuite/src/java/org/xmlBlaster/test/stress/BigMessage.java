/*------------------------------------------------------------------------------
Name:      BigMessage.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.stress;

import org.jutils.log.LogChannel;
import org.jutils.time.StopWatch;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.GetKeyWrapper;
import org.xmlBlaster.client.GetQosWrapper;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;

import junit.framework.*;


/**
 * This client tests a message of 2 Megabytes published and subscribed
 * <p>
 * We start our own xmlBlaster server in a thread.
 * </p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.stress.BigMessage
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.stress.BigMessage
 * </pre>
 */
public class BigMessage extends TestCase implements I_Callback
{
   private static String ME = "BigMessage";
   private final Global glob;
   private final LogChannel log;

   private XmlBlasterConnection con = null;
   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7615;
   private String oid = "BigMessage";
   private int contentSize = 2 * 1000 * 1000; // 2 MB

   private boolean messageArrived = false;
   private String assertInUpdate = null;

   /**
    * Constructs the BigMessage object. 
    * <p />
    * @param testName   The name used in the test suite
    */
   public BigMessage(String testName) {
      super(testName);
      this.glob = Global.instance();
      this.log = glob.getLog(null);
      this.name = testName; // name to login to xmlBlaster
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread,
    * it is configured to load the tinySQL BigMessage driver to test SQL access (with dBase files)
    * <p />
    * Then we connect as a client
    */
   protected void setUp() {
      glob.init(Util.getOtherServerPorts(serverPort));
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      log.info(ME, "XmlBlaster is ready for testing a big message");

      try {
         log.info(ME, "Connecting ...");
         con = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd);
         con.connect(qos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
         Thread.currentThread().dumpStack();
         log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try {
         log.info(ME, "Erasing message " + oid + " ...");
         EraseRetQos[] arr = con.erase("<key oid='" + oid + "'/>", "<qos/>");
         log.info(ME, "Erasing of message " + oid + " done.");
         assertEquals("Wrong number of message erased", 1, arr.length);
         assertTrue(assertInUpdate, assertInUpdate == null);
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.reason); }

      con.disconnect(null);

      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * Create a RDBMS table, fill some data and destroy it again. 
    * We use the tinySQL dBase BigMessage driver for testing.
    */
   public void testBigMessage() {
      log.info(ME, "######## Start testBigMessage()");
      StopWatch stopWatch = null;

      stopWatch = new StopWatch();
      byte[] content = new byte[contentSize];
      for (int i=0; i<content.length; i++) {
         content[i] = (byte)(i % 255);
      }
      log.info(ME, "Allocated message content with size=" + content.length/1000000 + " MB");
      try {
         PublishQosWrapper qosWrapper = new PublishQosWrapper(); // == "<qos></qos>"
         MessageUnit msgUnit = new MessageUnit("<key oid='" + oid + "'/>", content, "<qos/>");
         stopWatch = new StopWatch();
         
         con.publish(msgUnit);

         long avg = 0;
         long elapsed = stopWatch.elapsed();
         if (elapsed > 0L)
            avg = ((long)(contentSize)) / elapsed; // byte/milli == kbyte/sec

         log.info(ME, "Success: Publishing of " + oid + " with size=" + contentSize/1000000 + " MB done, avg=" + avg + " KB/sec " + stopWatch.nice());
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.reason);
         fail("Can't publish huge message: " + e.reason); 
      }

      messageArrived = false;

      try {
         SubscribeRetQos subscriptionId = con.subscribe("<key oid='" + oid + "'/>", "<qos/>");
         log.info(ME, "Success: Subscribe on subscriptionId=" + subscriptionId.getSubscriptionId() + " done");
      } catch(XmlBlasterException e) {
         log.error(ME, "XmlBlasterException: " + e.reason);
         fail("subscribe - XmlBlasterException: " + e.reason);
      }

      waitOnUpdate(8000L);
      assertTrue(assertInUpdate, assertInUpdate == null);
      assertEquals("Message not arrived", true, messageArrived);

      // Allow the update to return to xmlBlaster ...
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}
      log.info(ME, "######## End testBigMessage()");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message state=" + updateQos.getState());

      if (updateQos.isErased()) {
         log.info(ME, "Ignore erase event");
         return ""; // We ignore the erase event on tearDown
      }

      log.info(ME, "Receiving update of message oid=" + updateKey.getUniqueKey() + 
                   " size=" + content.length + " ...");

      assertInUpdate = "Wrong sender, expected:" + name + " but was:" + updateQos.getSender();
      assertEquals("Wrong sender", name, updateQos.getSender());

      assertInUpdate = "Wrong oid of message returned expected:" + oid + " but was:" + updateKey.getUniqueKey();
      assertEquals("Message oid is wrong", oid, updateKey.getUniqueKey());

      assertInUpdate = "Wrong message size arrived in update, expected:" + contentSize + " but was:" + content.length;
      assertEquals("Wrong message size arrived", contentSize, content.length);

      assertInUpdate = null;
      messageArrived = true;
      return "";
   }

   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or fails when the given timeout occurs.
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
            fail("Timeout of " + timeout + " occurred");
         }
      }
   }
}

