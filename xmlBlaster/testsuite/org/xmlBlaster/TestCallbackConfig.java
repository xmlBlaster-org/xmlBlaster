/*------------------------------------------------------------------------------
Name:      TestCallbackConfig.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Login/logout test for xmlBlaster
Version:   $Id: TestCallbackConfig.java,v 1.7 2002/05/09 11:54:51 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.DisconnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.CallbackAddress;

import junit.framework.*;


/**
 * This client does test different callback configuration settings. 
 * <p />
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 */
public class TestCallbackConfig extends TestCase implements I_Callback
{
   private static String ME = "Tim";

   private String name;
   private String passwd = "secret";
   private int numReceived = 0;         // error checking

   private boolean isDeadLetter = false;
   private String subscribeDeadLetterOid = null;
   private XmlBlasterConnection con = null;
   private String publishOid = null;
   private String cbSessionId = "topSecret";

   /**
    * Constructs the TestCallbackConfig object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestCallbackConfig(String testName, String name)
   {
       super(testName);
       this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login as admin, subscribe to dead letters
    */
   protected void setUp()
   {
      try {
         con = new XmlBlasterConnection();
         ConnectQos qos = new ConnectQos(null, "admin", passwd);
         
         // We configure detailed how our callback is handled by xmlBlaster
         // In connect() a default callback server is created and its address is added to cbProps
         CallbackAddress cbProps = new CallbackAddress(new Global());
         cbProps.setCollectTime(0L); // cb.burstMode.collectTime"
         cbProps.setSessionId(cbSessionId);
         cbProps.setPingInterval(10000);
         cbProps.setRetries(1);
         cbProps.setDelay(1000);
         cbProps.setPtpAllowed(true);
         qos.addCallbackAddress(cbProps);

         con.connect(qos, this);
      }
      catch (Exception e) {
         Log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      try {
         if (con != null) {
            String[] strArr = con.erase("<key oid='" + publishOid + "'/>", null);
            if (strArr.length != 1) Log.error(ME, "ERROR: Erased " + strArr.length + " messages");
            con.disconnect(new DisconnectQos());
         }
      }
      catch (Exception e) {
         Log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
   }

   /**
    */
   public void testCbSessionId()
   {
      Log.info(ME, "testCbSessionId() ...");
      try {
         con.subscribe("<key oid='testCallbackMsg'/>", null);

         publishOid = con.publish(new MessageUnit("<key oid='testCallbackMsg'/>", "Bla".getBytes(), null));

         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);

         waitOnUpdate(2000L, 1);
      }
      catch (Exception e) {
         Log.error(ME, e.toString());
         assertTrue(e.toString(), false);
      }
      Log.info(ME, "Success in testCbSessionId()");
   }


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.info(ME, "Receiving update of a message " + updateKey.getUniqueKey());
      numReceived++;
      if (!this.cbSessionId.equals(cbSessionId))
         Log.error(ME, "Invalid cbSessionId");
      assertEquals("Invalid cbSessionId", this.cbSessionId, cbSessionId);
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
         try { Thread.currentThread().sleep(pollingInterval); } catch( InterruptedException i) {}
         sum += pollingInterval;
         assertTrue("Timeout of " + timeout + " occurred without update", sum <= timeout);
      }

      // check if too many are arriving
      try { Thread.currentThread().sleep(timeout); } catch( InterruptedException i) {}
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
       suite.addTest(new TestCallbackConfig("testCbSessionId", "Tim"));
       return suite;
   }

   /**
    * Invoke:
    * <pre>
    *  java -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestCallbackConfig
    *
    *  java testsuite.org.xmlBlaster.TestCallbackConfig
    * </pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestCallbackConfig testSub = new TestCallbackConfig("TestCallbackConfig", "Tim");
      testSub.setUp();
      testSub.testCbSessionId();
      testSub.tearDown();
      Log.exit(TestCallbackConfig.ME, "Good bye");
   }
}

