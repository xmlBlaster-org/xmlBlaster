/*------------------------------------------------------------------------------
Name:      TestSessionCb.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestSessionCb.java,v 1.3 2002/06/03 09:40:35 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.client.EraseKeyWrapper;
import org.xmlBlaster.client.EraseQosWrapper;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


/**
 * This client does test callbacks for two sessions and dead letters. 
 * <p />
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestSessionCb
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestSessionCb
 * </pre>
 */
public class TestSessionCb extends TestCase
{
   private static String ME = "TestSessionCb";
   private final Global glob;
   private final LogChannel log;
   private XmlBlasterConnection con1 = null;
   private XmlBlasterConnection con2 = null;
   private String assertInUpdate = null;
   private String oid = "TestSessionCb-msg";
   private int deadLetterCounter = 0;

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
   }

   /**
    */
   protected void tearDown() {
      try {
         EraseKeyWrapper ek = new EraseKeyWrapper(oid);
         EraseQosWrapper eq = new EraseQosWrapper();
         con2.erase(ek.toXml(), eq.toXml());
      } catch (XmlBlasterException e) {
         log.error(ME, e.toString());
      }

      con2.disconnect(null);
   }

   /**
    */
   public void testSessionCb() {
      log.info(ME, "testSessionCb() ...");
      try {
         log.info(ME, "Connecting ...");
         con1 = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob);
         assertInUpdate = null;
         con1.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = glob.getId() + ": Did not expect message update in first handler";
                  fail(assertInUpdate); // This is routed to server, not to junit
                  return "";
               }
            });

         SubscribeKeyWrapper sk = new SubscribeKeyWrapper(oid);
         SubscribeQosWrapper sq = new SubscribeQosWrapper();
         SubscribeRetQos sr1 = con1.subscribe(sk.toXml(), sq.toXml());

         try { Thread.currentThread().sleep(1000); } catch( InterruptedException i) {} // Wait some time
         assertTrue(assertInUpdate, assertInUpdate == null);
         con1.shutdownCb();

         assertInUpdate = null;
         con2 = new XmlBlasterConnection(glob);
         con2.connect(qos, new I_Callback() {  // Login to xmlBlaster, register for updates
               public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
                  assertInUpdate = glob.getId() + "Reveiving asynchronous message '" + updateKey.getOid() + "' in second handler";
                  log.info(ME, assertInUpdate);
                  return "";
               }
            });

         sk = new SubscribeKeyWrapper(oid);
         sq = new SubscribeQosWrapper();
         SubscribeRetQos sr2 = con2.subscribe(sk.toXml(), sq.toXml());

         sk = new SubscribeKeyWrapper(Constants.OID_DEAD_LETTER);
         sq = new SubscribeQosWrapper();
         SubscribeRetQos srDeadLetter = con2.subscribe(sk.toXml(), sq.toXml(), new I_Callback() {
            public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
               deadLetterCounter++;
               log.info(ME, "Reveiving asynchronous message '" + updateKey.getOid() + "' in deadLetter handler, content=" + new String(content));
               assertEquals("No dead letter received", Constants.OID_DEAD_LETTER, updateKey.getOid());
               return "";
            }
         });  // subscribe with our specific update handler

         PublishKeyWrapper pk = new PublishKeyWrapper(oid, "text/plain", "1.0");
         PublishQosWrapper pq = new PublishQosWrapper();
         MessageUnit msgUnit = new MessageUnit(pk.toXml(), "Hi".getBytes(), pq.toXml());
         PublishRetQos retQos = con2.publish(msgUnit);
         log.info(ME, "Published message oid=" + oid);

         try { Thread.currentThread().sleep(2000); } catch( InterruptedException i) {} // Wait some time
         assertEquals("DeadLetter is missing", 1, deadLetterCounter);
         assertTrue("Update is missing", assertInUpdate != null);

         try {
            con1.get("<key oid='__sys__FreeMem'/>", null);
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
    *   java testsuite.org.xmlBlaster.TestSessionCb
    *   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSessionCb
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

