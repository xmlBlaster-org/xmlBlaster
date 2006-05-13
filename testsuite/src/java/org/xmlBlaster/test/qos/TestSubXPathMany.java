/*------------------------------------------------------------------------------
Name:      TestSubXPathMany.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test XPath.
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.def.Constants;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query. 
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubXPathMany
 *    java -Djava.compiler= junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestSubXPathMany
 * </pre>
 * @author xmlBlaster@marcelruff.info
 */
public class TestSubXPathMany extends TestCase {
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubXPathMany.class.getName());

   private I_XmlBlasterAccess con1, con2, con3;

   private int numReceived1 = 0;        // error checking
   private StringBuffer messageArrived1 = new StringBuffer();
   private int numReceived2 = 0;        // error checking
   private StringBuffer messageArrived2 = new StringBuffer();
   private int numReceived3 = 0;        // error checking
   private StringBuffer messageArrived3 = new StringBuffer();

   /**
    * Constructs the TestSubXPathMany object, used by junit. 
    */
   public TestSubXPathMany(String name) {
       super(name);
       this.glob = new Global();

   }

   /**
    * Constructs the TestSubXPathMany object, used by main.
    */
   public TestSubXPathMany(Global glob) {
       super("TestSubXPathMany");
       this.glob = glob;

   }

   /**
    * Sets up the fixture, connect to xmlBlaster 3 times. 
    */
   protected void setUp() {
      ConnectQos connectQos;
      try {
         Global glob1 = glob.getClone(null);
         con1 = glob1.getXmlBlasterAccess(); // Find orb
         connectQos = new ConnectQos(glob1, "con1", "secret");
         con1.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived1++;
               assertEquals("Message not expected", "command-navigation", updateKey.getOid());
               messageArrived1.append("OK");
               return "";
            }
         });

         Global glob2 = glob.getClone(null);
         con2 = glob2.getXmlBlasterAccess(); // Find orb
         connectQos = new ConnectQos(glob2, "con2", "secret");
         con2.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived2++;
               assertEquals("Message not expected", "command-radar-1", updateKey.getOid());
               messageArrived2.append("OK");
               return "";
            }
         });

         Global glob3 = glob.getClone(null);
         con3 = glob3.getXmlBlasterAccess(); // Find orb
         connectQos = new ConnectQos(glob3, "con3", "secret");
         con3.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived3++;
               assertEquals("Message not expected", "command-radar-1", updateKey.getOid());
               messageArrived3.append("OK");
               return "";
            }
         });

      }
      catch (XmlBlasterException e) {
         log.severe("login failed: " + e.toString());
         fail("Login failed: " + e.toString());
      }
   }


   /**
    * Tear down the fixture, erase messages and disconnect from xmlBlaster. 
    */
   protected void tearDown() {
      if (con1 != null) {
         try {
            EraseKey ek = new EraseKey(glob, "command-navigation");
            EraseQos eq = new EraseQos(glob);
            con1.erase(ek.toXml(), eq.toXml());

            ek = new EraseKey(glob, "command-radar-1");
            eq = new EraseQos(glob);
            con1.erase(ek.toXml(), eq.toXml());

            ek = new EraseKey(glob, "dummyTestSubXPathMany");
            eq = new EraseQos(glob);
            con1.erase(ek.toXml(), eq.toXml());
         }
         catch (XmlBlasterException e) {
            fail("doPublish failed: " + e.toString());
         }
         con1.disconnect(null);
      }
      if (con2 != null) { con2.disconnect(null); con2=null; }
      if (con3 != null) { con3.disconnect(null); con3=null; }
   }

   /**
    * Subscribe with XPATH.
    */
   public void doSubscribe() {
      log.info("*****Subscribing using XPath syntax ...");

      try {
         SubscribeKey sk = new SubscribeKey(glob, "//key[@oid = 'command-navigation']", Constants.XPATH);
         SubscribeQos sq = new SubscribeQos(glob);
         con1.subscribe(sk.toXml(), sq.toXml()).getSubscriptionId();

         String xpath2 = "//key[starts-with(@oid,'command-radar')]";
         sk = new SubscribeKey(glob, xpath2, Constants.XPATH);
         sq = new SubscribeQos(glob);
         con2.subscribe(sk.toXml(), sq.toXml()).getSubscriptionId();

         sk = new SubscribeKey(glob, xpath2, Constants.XPATH);
         sq = new SubscribeQos(glob);
         con3.subscribe(sk.toXml(), sq.toXml()).getSubscriptionId();
      }
      catch (XmlBlasterException e) {
         fail("doPublish failed: " + e.toString());
      }
   }

   /**
    * Publish some messages. 
    */
   public void doPublish() {
      log.info("*****Publishing messages ...");

      try {
         PublishKey pk = new PublishKey(glob, "command-navigation", "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob);
         MsgUnit msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con1.publish(msgUnit);
         log.info("Published message '" + pk.getOid() + "'");

         pk = new PublishKey(glob, "command-radar-1", "text/plain", "1.0");
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con1.publish(msgUnit);
         log.info("Published message '" + pk.getOid() + "'");

         pk = new PublishKey(glob, "dummyTestSubXPathMany", "text/plain", "1.0");
         pq = new PublishQos(glob);
         msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         con1.publish(msgUnit);
         log.info("Published message '" + pk.getOid() + "'");
      }
      catch (XmlBlasterException e) {
         fail("doPublish failed: " + e.toString());
      }
   }


   /**
    * TEST: Publish some messages, disconnect client3 subscribe with XPath and
    * check if everything is OK.
    */
   public void testIt()
   {
      doSubscribe();
      try { Thread.sleep(2000L); } catch( InterruptedException i) {}                                             // Wait some time for callback to arrive ...
      assertEquals("numReceived1 after subscribe", 0, numReceived1); // there should be no Callback
      assertEquals("numReceived2 after subscribe", 0, numReceived2); // there should be no Callback
      assertEquals("numReceived3 after subscribe", 0, numReceived3); // there should be no Callback

      doPublish();
      waitOnUpdate(2000L, messageArrived1);
      assertEquals("numReceived1 after publishing", 1, numReceived1); // message arrived?
      waitOnUpdate(2000L, messageArrived2);
      assertEquals("numReceived2 after publishing", 1, numReceived2); // message arrived?
      waitOnUpdate(2000L, messageArrived3);
      assertEquals("numReceived3 after publishing", 1, numReceived3); // message arrived?

      numReceived1 = numReceived2 = numReceived3 = 0;
      messageArrived1.setLength(0);
      messageArrived2.setLength(0);
      messageArrived3.setLength(0);

      if (con3 != null) { con3.disconnect(null); con3 = null; }

      doPublish();
      waitOnUpdate(2000L, messageArrived1);
      assertEquals("numReceived1 after publishing", 1, numReceived1); // message arrived?
      waitOnUpdate(2000L, messageArrived2);
      assertEquals("numReceived2 after publishing", 1, numReceived2); // message arrived?
      waitOnUpdate(2000L, messageArrived3);
      assertEquals("numReceived3 after publishing", 0, numReceived3); // message arrived?
      
      numReceived1 = numReceived2 = numReceived3 = 0;
      messageArrived1.setLength(0);
      messageArrived2.setLength(0);
      messageArrived3.setLength(0);
   }

   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * or returns when the given timeout occurs.
    * @param timeout in milliseconds
    */
   private void waitOnUpdate(final long timeout, StringBuffer messageArrived) {
      long pollingInterval = 50L;  // check every 0.05 seconds
      if (timeout < 50)  pollingInterval = timeout / 10L;
      long sum = 0L;
      while (messageArrived.length() < 1) {
         try {
            Thread.sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warning("Timeout of " + timeout + " occurred");
            //fail("Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived.setLength(0);
   }

   /**
    * <pre>
    *   java -Dtrace=true -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubXPathMany
    *   java -Djava.compiler= org.xmlBlaster.test.qos.TestSubXPathMany -logging FINE
    * </pre>
    */
   public static void main(String args[]) {
      TestSubXPathMany testSub = new TestSubXPathMany(new Global(args));
      testSub.setUp();
      testSub.testIt();
      testSub.tearDown();
   }
}

