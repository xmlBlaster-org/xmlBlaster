/*------------------------------------------------------------------------------
Name:      TestFailSaveAsync.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.init.Property;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_ConnectionStateListener;
import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.dispatch.ConnectionStateEnum;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * Tests the fail save behavior of the I_XmlBlasterAccess client helper class,
 * especially the asynchronous playback of messages. 
 * <p />
 * When the connection to xmlBlaster is lost, and you continue to publish messages
 * they are stored locally with the invocation recorder on harddisk.<br />
 * On reconnect they are flushed with an adjustable rate in background.<br />
 * If your client code decides to publish new messages during playback recovery,
 * your new messages will overtake some of the play back messages.
 * <p />
 * If you want guaranteed sequence, please don't send messages during playback.
 * <p />
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestFailSaveAsync
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestFailSaveAsync
 * </pre>
 */
public class TestFailSaveAsync extends TestCase implements I_Callback, I_ConnectionStateListener
{
   private static String ME = "TestFailSaveAsync";
   private final Global glob;
   private final LogChannel log;
   private boolean messageArrived = false;

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private I_XmlBlasterAccess con;
   private String senderName;

   private int numReceived;
   private int numTailbackReceived;
   private int numNormalPublishReceived;

   private final String contentMime = "text/plain";

   private boolean reconnected;
   private boolean allTailbackAreFlushed;

   /** TEST: Sendin 0-19 directly, sending 20-39 to recorder (no connection), sending 40-100 directly */
   private final int maxEntries = 100;
   private final int failMsg = 20;
   private final int reconnectMsg = 40;

   /** publish rate msg/sec */
   private final long publishRate = 5;
   /** publish rate of tailback msg/sec */
   private final long pullbackRate = 1;

   PublishKey publishKeyWrapper;
   PublishQos publishQosWrapper;

   /**
    * Constructs the TestFailSaveAsync object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestFailSaveAsync(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog(null);
      this.senderName = loginName;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      numReceived = 0;
      numTailbackReceived = 0;
      numNormalPublishReceived = 0;

      reconnected = false;
      allTailbackAreFlushed = true;

      glob.init(Util.getOtherServerPorts(serverPort));

      serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      try {
         numReceived = 0;

         con = glob.getXmlBlasterAccess(); // Find server

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd);

         // Setup fail save handling ...
         Address addressProp = new Address(glob);
         addressProp.setDelay(400L);          // retry connecting every 400 milli sec
         addressProp.setRetries(-1);          // -1 == forever
         addressProp.setPingInterval(400L);   // ping every 400 milli second
         con.registerConnectionListener(this);

         connectQos.setAddress(addressProp);
         
         // and do the login ...
         con.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed: " + e.toString());
          fail("setUp() - login failed: " + e.toString());
      }
      catch (Exception e) {
          log.error(ME, "setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login failed: " + e.toString());
      }

      publishKeyWrapper = new PublishKey(glob, "emptyOid", contentMime);
      publishKeyWrapper.setClientTags("<TestFailSaveAsync-AGENT id='192.168.124.10' subId='1' type='generic'/>");
      /*
         String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                         "   <TestFailSaveAsync-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                         "   </TestFailSaveAsync-AGENT>" +
                         "</key>";
      */
      publishQosWrapper = new PublishQos(glob); // == "<qos></qos>"
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
                      "   //TestFailSaveAsync-AGENT" +
                      "</key>";
      try {
         try {
            EraseReturnQos[] arr = con.erase(xmlKey, null);


            PropString defaultPlugin = new PropString("CACHE,1.0");
            String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
            log.info(ME, "Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
            
            if (defaultPlugin.getValue().startsWith("RAM"))
               assertEquals("Wrong number of message erased", (maxEntries-failMsg), arr.length);
               // expect 80 to delete as the first 20 are lost when server 'crashed'
            else
               assertEquals("Wrong number of message erased", maxEntries, arr.length);
         } catch(XmlBlasterException e) { assertTrue("tearDown - XmlBlasterException: " + e.getMessage(), false); }

         con.disconnect(null);
      }
      finally {
         try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}    // Wait some time
         EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

         // reset to default server port (necessary if other tests follow in the same JVM).
         Util.resetPorts();
      }
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    */
   public void subscribe()
   {
      if (log.TRACE) log.trace(ME, "Subscribing using EXACT oid syntax ...");

      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestFailSaveAsync-AGENT" +
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
    * Construct a message and publish it.
    */
   public void publish(int counter) {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      long publishDelay = 1000/publishRate;  // 20 msg/sec -> send every 50 milli one
      String oid = "Message-" + counter;
      try {
         publishKeyWrapper.setOid(oid);
         String content = "" + counter;
         MsgUnit msgUnit = new MsgUnit(publishKeyWrapper.toXml(), content.getBytes(), publishQosWrapper.toXml());

         con.publish(msgUnit);
         Util.delay(publishDelay);  // Wait some time
      }
      catch(XmlBlasterException e) {
         fail(ME + ": Publish failed: " + e.toString());
      }
      log.info(ME, "Success: Publishing of " + oid + " done");
   }


   /**
    * TEST: Sendin 0-19 directly, sending 20-39 to recorder (no connection), sending 40-100 directly
    */
   public void testFailSave() {
      subscribe();

      for (int ii=0; ii<maxEntries; ii++) {
         if (ii==failMsg) {
            EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
            Util.delay(600L);    // Wait some time, ping should activate login polling
            log.info(ME, "lostConnection, sending message " + ii + " - " + (reconnectMsg-1));
         }

         if (ii==reconnectMsg) {
            serverThread = EmbeddedXmlBlaster.startXmlBlaster(serverPort);
            while (true) {
               if (reconnected == true)
                  break;
               Util.delay(10L); // Wait some time, to allow the login poller to reconnect
            }
            log.info(ME, "Reconnected, sending message " + ii + " - " + (maxEntries-1));
         }

         publish(ii);
      }

      int numFailsave = reconnectMsg-failMsg;  // 20
      int numPublish = maxEntries-numFailsave;     // 80
      waitOnUpdate(16000L + (long)((1000.0 * numPublish / publishRate) + (1000.0 * numFailsave / pullbackRate)), maxEntries);
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was established.
    */
   public void reachedAlive(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.info(ME, "I_ConnectionStateListener: We were lucky, (re)connected to xmlBlaster");
      subscribe();    // initialize subscription again
      reconnected = true;
      allTailbackAreFlushed = true;
   }

   /**
    * This is the callback method invoked from I_XmlBlasterAccess
    * informing the client in an asynchronous mode if the connection was lost.
    * <p />
    * This method is enforced through interface I_ConnectionStateListener
    */
   public void reachedPolling(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.warn(ME, "I_ConnectionStateListener: Lost connection to xmlBlaster");
      allTailbackAreFlushed = false;
   }

   public void reachedDead(ConnectionStateEnum oldState, I_XmlBlasterAccess connection) {
      log.error(ME, "DEBUG ONLY: Changed from connection state " + oldState + " to " + ConnectionStateEnum.DEAD);
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      synchronized (this) {

         String oid = updateKey.getOid();

         if (updateQos.isErased()) {
            return "";
         }

         numReceived++;

         assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
         assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

         int ii = 0;
         try {
            ii = Integer.parseInt(oid.substring("Message-".length()));
         } catch(NumberFormatException e) {
            log.error(ME, "Can't extract message number " + oid);
            fail("Can't extract message number " + oid);
         }

         if (ii >= failMsg && ii < reconnectMsg)
            numTailbackReceived++;
         else
            numNormalPublishReceived++;

         // Check content
         try {
            int contentCounter = 0;
            String cnt = new String(content);
            contentCounter = Integer.parseInt(cnt);
            assertEquals("Wrong counter in content", ii, contentCounter);
         } catch(NumberFormatException e) {
            log.error(ME, "Can't extract message number '" + new String(content) + "': " + updateQos.toXml());
         }

         log.info(ME, "Update message oid=" + oid + " numReceived=" + numReceived + ", numNormalPublishReceived=" + numNormalPublishReceived + " numTailbackReceived=" + numTailbackReceived + " ...");

         // Check here async behavior:
         if (numReceived == 80) {
            int expectedTailback = (int)((80.-reconnectMsg)*(1.*pullbackRate/publishRate));
            int diff = Math.abs(numTailbackReceived - expectedTailback);

            if (diff > 6) {
               String text = "Expected tailback updates = " + expectedTailback + " but got " + numTailbackReceived;
               log.error(ME, text);
               fail(text);
            }
            log.info(ME, "TEST SUCCESS: Expected tailback updates = " + expectedTailback + " and got " + numTailbackReceived);
         }

         messageArrived = true;
      
      } // synchronized as we have the client as publisher and the invocation recorder as a publisher
      
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
       String loginName = "Tim";
       suite.addTest(new TestFailSaveAsync(new Global(), "testFailSave", loginName));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.qos.TestFailSaveAsync
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestFailSaveAsync</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         glob.getLog(null).error(ME, "Init failed");
         System.exit(1);
      }
      TestFailSaveAsync testSub = new TestFailSaveAsync(glob, "TestFailSaveAsync", "Tim");
      testSub.setUp();
      testSub.testFailSave();
      testSub.tearDown();
   }
}

