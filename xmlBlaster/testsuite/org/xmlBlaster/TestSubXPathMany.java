/*------------------------------------------------------------------------------
Name:      TestSubXPathMany.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Test XPath.
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.*;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;

import junit.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query. 
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubXPathMany
 *    java -Djava.compiler= junit.ui.TestRunner -noloading testsuite.org.xmlBlaster.TestSubXPathMany
 * </pre>
 * @author ruff@swand.lake.de
 */
public class TestSubXPathMany extends TestCase
{
   private static String ME = "Tim";
   private final Global glob;
   private final LogChannel log;

   private String publishOid = "";
   private XmlBlasterConnection con1, con2, con3;

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
       this.glob = Global.instance();
       this.log = glob.getLog(null);
   }

   /**
    * Constructs the TestSubXPathMany object, used by main.
    */
   public TestSubXPathMany(Global glob) {
       super("TestSubXPathMany");
       this.glob = glob;
       this.log = glob.getLog(null);
   }

   /**
    * Sets up the fixture, connect to xmlBlaster 3 times. 
    */
   protected void setUp() {
      ConnectQos connectQos;
      try {
         con1 = new XmlBlasterConnection(glob); // Find orb
         connectQos = new ConnectQos(glob, "con1", "secret");
         con1.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("con1", "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived1++;
               assertEquals("Message not expected", "command-navigation", updateKey.getOid());
               messageArrived1.append("OK");
               return "";
            }
         });

         con2 = new XmlBlasterConnection(glob); // Find orb
         connectQos = new ConnectQos(glob, "con2", "secret");
         con2.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("con2", "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived2++;
               assertEquals("Message not expected", "command-radar-1", updateKey.getOid());
               messageArrived2.append("OK");
               return "";
            }
         });

         con3 = new XmlBlasterConnection(glob); // Find orb
         connectQos = new ConnectQos(glob, "con3", "secret");
         con3.connect(connectQos,  new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               log.info("con3", "Reveiving asynchronous message '" + updateKey.getOid() + "' in default handler");
               numReceived3++;
               assertEquals("Message not expected", "command-radar-1", updateKey.getOid());
               messageArrived3.append("OK");
               return "";
            }
         });

      }
      catch (XmlBlasterException e) {
         log.error(ME, "login failed: " + e.toString());
         fail("Login failed: " + e.toString());
      }
   }


   /**
    * Tear down the fixture, erase messages and disconnect from xmlBlaster. 
    */
   protected void tearDown() {
      if (con1 != null) {
         try {
            EraseKeyWrapper ek = new EraseKeyWrapper("command-navigation");
            EraseQosWrapper eq = new EraseQosWrapper();
            con1.erase(ek.toXml(), eq.toXml());

            ek = new EraseKeyWrapper("command-radar-1");
            eq = new EraseQosWrapper();
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
      log.info(ME, "*****Subscribing using XPath syntax ...");

      try {
         SubscribeKeyWrapper sk = new SubscribeKeyWrapper("//key[@oid = 'command-navigation']", Constants.XPATH);
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         String subId = con1.subscribe(sk.toXml(), sq.toXml());

         String xpath2 = "//key[starts-with(@oid,'command-radar')]";
         sk = new SubscribeKeyWrapper(xpath2, Constants.XPATH);
         sq = new SubscribeQosWrapper();
         subId = con2.subscribe(sk.toXml(), sq.toXml());

         sk = new SubscribeKeyWrapper(xpath2, Constants.XPATH);
         sq = new SubscribeQosWrapper();
         subId = con3.subscribe(sk.toXml(), sq.toXml());
      }
      catch (XmlBlasterException e) {
         fail("doPublish failed: " + e.toString());
      }
   }

   /**
    * Publish some messages. 
    */
   public void doPublish() {
      log.info(ME, "*****Publishing messages ...");

      try {
         PublishKeyWrapper pk = new PublishKeyWrapper("command-navigation", "text/plain", "1.0");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         String retQos = con1.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");

         pk = new PublishKeyWrapper("command-radar-1", "text/plain", "1.0");
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         retQos = con1.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");

         pk = new PublishKeyWrapper("dummy", "text/plain", "1.0");
         pq = new PublishQosWrapper();
         msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         retQos = con1.publish(msgUnit);
         log.info(ME, "Published message '" + pk.getOid() + "'");
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
      Util.delay(2000L);                                             // Wait some time for callback to arrive ...
      assertEquals("numReceived1 after subscribe", 0, numReceived1); // there should be no Callback
      assertEquals("numReceived2 after subscribe", 0, numReceived2); // there should be no Callback
      assertEquals("numReceived3 after subscribe", 0, numReceived3); // there should be no Callback

      doPublish();
      waitOnUpdate(5000L, messageArrived1);
      assertEquals("numReceived1 after publishing", 1, numReceived1); // message arrived?
      waitOnUpdate(5000L, messageArrived2);
      assertEquals("numReceived2 after publishing", 1, numReceived2); // message arrived?
      waitOnUpdate(5000L, messageArrived3);
      assertEquals("numReceived3 after publishing", 1, numReceived3); // message arrived?

      numReceived1 = numReceived2 = numReceived3 = 0;
      messageArrived1.setLength(0);
      messageArrived2.setLength(0);
      messageArrived3.setLength(0);

      if (con3 != null) { con3.disconnect(null); con3 = null; }

      doPublish();
      waitOnUpdate(5000L, messageArrived1);
      assertEquals("numReceived1 after publishing", 1, numReceived1); // message arrived?
      waitOnUpdate(5000L, messageArrived2);
      assertEquals("numReceived2 after publishing", 1, numReceived2); // message arrived?
      waitOnUpdate(5000L, messageArrived3);
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
            Thread.currentThread().sleep(pollingInterval);
         }
         catch( InterruptedException i)
         {}
         sum += pollingInterval;
         if (sum > timeout) {
            log.warn(ME, "Timeout of " + timeout + " occurred");
            //fail("Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived.setLength(0);
   }

   /**
    * <pre>
    *   java -Dtrace=true -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubXPathMany
    *   java -Djava.compiler= testsuite.org.xmlBlaster.TestSubXPathMany -trace true
    * </pre>
    */
   public static void main(String args[]) {
      TestSubXPathMany testSub = new TestSubXPathMany(new Global(args));
      testSub.setUp();
      testSub.testIt();
      testSub.tearDown();
   }
}

