/*------------------------------------------------------------------------------
Name:      TestCallbackConfig.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.property.Args;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * This client does test different callback configuration settings. 
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 */
public class TestCallbackConfig extends TestCase
{
   private static String ME = "TestCallbackConfig";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestCallbackConfig.class.getName());
   private String name;
   private String passwd = "secret";
   private I_XmlBlasterAccess con = null;
   private String publishOid = null;
   private String cbSessionId = "topSecret";
   private MsgInterceptor updateInterceptor;

   /**
    * Constructs the TestCallbackConfig object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestCallbackConfig(Global glob, String testName, String name) {
       super(testName);
       this.glob = glob;

       this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login as admin, subscribe to dead letters
    */
   protected void setUp() {
      Util.resetPorts();
      Util.resetPorts(glob);
      try {
         con = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(null, "admin", passwd);
         
         // We configure detailed how our callback is handled by xmlBlaster
         // In connect() a default callback server is created and its address is added to cbProps
         CallbackAddress cbProps = new CallbackAddress(new Global());
         cbProps.setCollectTime(0L); // dispatch/callback/burstMode/collectTime"
         cbProps.setSecretSessionId(cbSessionId);
         cbProps.setPingInterval(10000);
         cbProps.setRetries(1);
         cbProps.setDelay(1000);
         cbProps.setPtpAllowed(true);
         qos.addCallbackAddress(cbProps);

         this.updateInterceptor = new MsgInterceptor(this.glob, log, null);
         con.connect(qos, this.updateInterceptor); // Login to xmlBlaster and collect update messages with interceptor
      }
      catch (Exception e) {
         log.severe(e.toString() + " \n" + glob.getProperty().toXml() + " GLOBAL.INSTANCE:\n" + Global.instance().getProperty().toXml());
         e.printStackTrace();
         assertTrue(e.toString(), false);
      }
      this.updateInterceptor.clear();
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      try {
         if (con != null) {
            EraseReturnQos[] strArr = con.erase("<key oid='" + publishOid + "'/>", null);
            if (strArr.length != 1) log.severe("ERROR: Erased " + strArr.length + " messages");
            con.disconnect(new DisconnectQos(glob));
         }
      }
      catch (Exception e) {
         log.severe(e.toString());
         assertTrue(e.toString(), false);
      }
   }

   /**
    */
   public void testCbSessionId() {
      log.info("testCbSessionId() ...");
      try {
         con.subscribe("<key oid='testCallbackMsg'/>", null);

         publishOid = con.publish(new MsgUnit("<key oid='testCallbackMsg'/>", "Bla".getBytes(), null)).getKeyOid();

         log.info("Success: Publishing done, returned oid=" + publishOid);

         assertEquals("returned oid", "testCallbackMsg", publishOid);
         assertEquals("numReceived after publishing", 1, this.updateInterceptor.waitOnUpdate(2000L, publishOid, Constants.STATE_OK));
         assertEquals("", 1, this.updateInterceptor.getMsgs().length);
         assertEquals("", this.cbSessionId, this.updateInterceptor.getMsgs()[0].getCbSessionId());
      }
      catch (Exception e) {
         log.severe(e.toString());
         assertTrue(e.toString(), false);
      }
      log.info("Success in testCbSessionId()");
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestCallbackConfig(new Global(), "testCbSessionId", "Tim"));
       return suite;
   }

   /**
    * Invoke:
    * <pre>
    *  java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestCallbackConfig
    *
    *  java org.xmlBlaster.test.qos.TestCallbackConfig
    * </pre>
    */
   public static void main(String args[]) {
      TestCallbackConfig testSub = new TestCallbackConfig(new Global(args), "TestCallbackConfig", "Tim");
      testSub.setUp();
      testSub.testCbSessionId();
      testSub.tearDown();
   }
}

