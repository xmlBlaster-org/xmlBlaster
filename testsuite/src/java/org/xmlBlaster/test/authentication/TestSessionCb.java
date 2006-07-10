/*------------------------------------------------------------------------------
Name:      TestSessionCb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import java.util.logging.Logger;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * This client does test callbacks for two sessions and dead letters. 
 * <p />
 * Test does not work with SOCKET protocol as here we use the same socket for
 * callback and we can't simulate a lost callback
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionCb
 *    java junit.swingui.TestRunner org.xmlBlaster.test.authentication.TestSessionCb
 * </pre>
 */
public class TestSessionCb extends TestCase
{
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSessionCb.class.getName());
   private I_XmlBlasterAccess con1 = null;
   private I_XmlBlasterAccess con2 = null;
   private String assertInUpdate = null;
   private String oid = "TestSessionCb-msg";
   private int deadMessageCounter = 0;

   /**
   * Test does not work with SOCKET protocol as here we use the same socket for
   * callback and we can't simulate a lost callback
   */
   private boolean isSocket = false;

   /**
    * Constructs the TestSessionCb object.
    */
   public TestSessionCb(Global glob, String testName) {
      super(testName);
      this.glob = glob;

   }

   /**
    */
   protected void setUp() {
      String driverType = glob.getProperty().get("client.protocol", "dummy");
      if (driverType.equalsIgnoreCase("SOCKET"))
         isSocket = true;

      if (isSocket) {
         log.warning("callback test ignored for driverType=" + driverType + " as callback server uses same socket as invoce channel");
         return;
      }
   }

   /**
    */
   protected void tearDown() {
      if (isSocket) return;
      if (con2 != null) {
         try {
            EraseKey ek = new EraseKey(glob, oid);
            EraseQos eq = new EraseQos(glob);
            con2.erase(ek.toXml(), eq.toXml());
         } catch (XmlBlasterException e) {
            log.severe(e.toString());
         }

         con2.disconnect(null);
      }
   }

   /**
    */
   public void testSessionCb() {
      if (isSocket) return;
      log.info("testSessionCb() ...");
      final Global glob1 = glob.getClone(null);
      final Global glob2 = glob.getClone(null);
      String name1 = "NUMBER_ONE";
      try {
         log.info("Connecting ...");
         con1 = glob1.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob1, name1, "secret");
         assertInUpdate = null;
         con1.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("****** Con1 update arrived" + updateKey.toXml() + updateQos.toXml());
                  assertInUpdate = glob1.getId() + ": Did not expect message update in first handler";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });

         SubscribeKey sk = new SubscribeKey(glob1, oid);
         SubscribeQos sq = new SubscribeQos(glob1);
         SubscribeReturnQos sr1 = con1.subscribe(sk.toXml(), sq.toXml());

         try { Thread.sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         con1.getCbServer().shutdown();

         log.info("############ Con1 is down");

         assertInUpdate = null;
         con2 = glob2.getXmlBlasterAccess();
         qos = new ConnectQos(glob2);  // force a new session
         con2.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info("****** Con2 update arrived" + updateKey.toXml() + updateQos.toXml());
                  assertInUpdate = glob2.getId() + "Reveiving asynchronous message '" + updateKey.getOid() + "' in second handler";
                  log.info(assertInUpdate);
                  return "";
               }
            });

         sk = new SubscribeKey(glob2, oid);
         sq = new SubscribeQos(glob2);
         SubscribeReturnQos sr2 = con2.subscribe(sk.toXml(), sq.toXml());

         sk = new SubscribeKey(glob2, Constants.OID_DEAD_LETTER);
         sq = new SubscribeQos(glob2);
         SubscribeReturnQos srDeadMessage = con2.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               deadMessageCounter++;
               log.info("****** Reveiving asynchronous message '" + updateKey.getOid() + "' in deadMessage handler, content=" + new String(content));
               assertEquals("No dead letter received", Constants.OID_DEAD_LETTER, updateKey.getOid());
               return "";
            }
         });  // subscribe with our specific update handler

         log.info("############ Con2 subscribed for msg and for DEAD letter, publishing now msg");

         PublishKey pk = new PublishKey(glob2, oid, "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob2);
         MsgUnit msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         PublishReturnQos retQos = con2.publish(msgUnit);
         log.info("Published message oid=" + oid);

         log.info("############ Con2 is waiting for msg and for DEAD letter ...");

         try { Thread.sleep(2000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("DeadMessage is missing", 1, deadMessageCounter);
         assertTrue("Update is missing", assertInUpdate != null);

         try {
            log.info("Check that session has dissapeared ...");
            MsgUnit[] msgs = Util.adminGet(glob, "__cmd:?clientList");
            assertEquals("Can't access __cmd:?clientList", 1, msgs.length);
            log.info("Got userList=" + msgs[0].getContentStr() + " checking for " + name1);
            assertEquals("Session of " + name1 + " was not destroyed by failing callback",
                      -1, msgs[0].getContentStr().indexOf(name1));
         }
         catch (XmlBlasterException e) {
            fail("Session was not destroyed: " + e.toString());
         }
      }
      catch (XmlBlasterException e) {
         fail("SessionCb test failed: " + e.toString());
      }
      log.info("Success in testSessionCb()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       Global glob = new Global();
       suite.addTest(new TestSessionCb(glob, "testSessionCb"));
       suite.addTest(new TestSessionCb(glob, "testSessionCb")); // Run it twice
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *   java org.xmlBlaster.test.authentication.TestSessionCb
    *   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.authentication.TestSessionCb
    * <pre>
    */
   public static void main(String args[]) {
      TestSessionCb testSub = new TestSessionCb(new Global(args), "testSessionCb");
      testSub.setUp();
      testSub.testSessionCb();
      testSub.testSessionCb();
      testSub.tearDown();
   }
}

