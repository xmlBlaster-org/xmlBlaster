/*------------------------------------------------------------------------------
Name:      TestSessionCb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.authentication;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
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
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.MsgUnit;

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
   private static String ME = "TestSessionCb";
   private final Global glob;
   private final LogChannel log;
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
      this.log = glob.getLog(null);
   }

   /**
    */
   protected void setUp() {
      String driverType = glob.getProperty().get("client.protocol", "dummy");
      if (driverType.equalsIgnoreCase("SOCKET"))
         isSocket = true;

      if (isSocket) {
         log.warn(ME, "callback test ignored for driverType=" + driverType + " as callback server uses same socket as invoce channel");
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
            log.error(ME, e.toString());
         }

         con2.disconnect(null);
      }
   }

   /**
    */
   public void testSessionCb() {
      if (isSocket) return;
      log.info(ME, "testSessionCb() ...");
      final Global glob1 = glob.getClone(null);
      final Global glob2 = glob.getClone(null);
      try {
         log.info(ME, "Connecting ...");
         con1 = glob1.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob1);
         assertInUpdate = null;
         con1.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(ME, "****** Con1 update arrived" + updateKey.toXml() + updateQos.toXml());
                  assertInUpdate = glob1.getId() + ": Did not expect message update in first handler";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });

         SubscribeKey sk = new SubscribeKey(glob1, oid);
         SubscribeQos sq = new SubscribeQos(glob1);
         SubscribeReturnQos sr1 = con1.subscribe(sk.toXml(), sq.toXml());

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         con1.getCbServer().shutdown();

         log.info(ME, "############ Con1 is down");

         assertInUpdate = null;
         con2 = glob2.getXmlBlasterAccess();
         qos = new ConnectQos(glob2);  // force a new session
         con2.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  log.info(ME, "****** Con2 update arrived" + updateKey.toXml() + updateQos.toXml());
                  assertInUpdate = glob2.getId() + "Reveiving asynchronous message '" + updateKey.getOid() + "' in second handler";
                  log.info(ME, assertInUpdate);
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
               log.info(ME, "****** Reveiving asynchronous message '" + updateKey.getOid() + "' in deadMessage handler, content=" + new String(content));
               assertEquals("No dead letter received", Constants.OID_DEAD_LETTER, updateKey.getOid());
               return "";
            }
         });  // subscribe with our specific update handler

         log.info(ME, "############ Con2 subscribed for msg and for DEAD letter, publishing now msg");

         PublishKey pk = new PublishKey(glob2, oid, "text/plain", "1.0");
         PublishQos pq = new PublishQos(glob2);
         MsgUnit msgUnit = new MsgUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         PublishReturnQos retQos = con2.publish(msgUnit);
         log.info(ME, "Published message oid=" + oid);

         log.info(ME, "############ Con2 is waiting for msg and for DEAD letter ...");

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("DeadMessage is missing", 1, deadMessageCounter);
         assertTrue("Update is missing", assertInUpdate != null);

         try {
            con1.get("<key oid='__cmd:?freeMem'/>", null);
            fail("XmlBlaster should have killed us because of callback problems");
         }
         catch (XmlBlasterException e) {
            log.info(ME, "OK, expected destroyed session: " + e.toString());
         }
         
      }
      catch (XmlBlasterException e) {
         fail("SessionCb test failed: " + e.toString());
      }
      log.info(ME, "Success in testSessionCb()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
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

