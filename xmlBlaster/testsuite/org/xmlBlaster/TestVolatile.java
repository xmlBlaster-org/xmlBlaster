/*------------------------------------------------------------------------------
Name:      TestVolatile.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing volatile messages
Version:   $Id: TestVolatile.java,v 1.5 2002/05/03 10:37:49 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.jutils.io.FileUtil;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests volatile messages, the $lt;isVolatile> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestVolatile
 *
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestVolatile
 * </pre>
 */
public class TestVolatile extends TestCase implements I_Callback
{
   private final static String ME = "TestVolatile";
   private Global glob = null;

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
   public TestVolatile(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";
         senderConnection = new XmlBlasterConnection();
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login(senderName, passwd, qos, this);
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          e.printStackTrace();
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown()
   {
      Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 0) Log.error(ME, "Erased " + strArr.length + " messages:");

      senderConnection.logout();
   }


   /**
    * Publish a volatile message.
    * <p />
    */
   public void sendVolatile()
   {
      if (Log.TRACE) Log.trace(ME, "Testing a volatile message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <isVolatile>true</isVolatile>" +
                   "</qos>";

      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      try {
         String returnedOid = senderConnection.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         Log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * Publish a volatile message.
    * <p />
    */
   public void subscribeVolatile()
   {
      Log.info(ME, "Subscribing a volatile message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "</qos>";

      try {
         senderConnection.subscribe(xmlKey, qos);
         Log.info(ME, "Subscribing of '" + publishOid + "' done");
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Publish a volatile message.
    * <p />
    */
   public void testVolatile()
   {
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
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      Log.info(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestVolatile("testVolatile"));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestVolatile
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestVolatile</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestVolatile testSub = new TestVolatile("TestVolatile");
      testSub.setUp();
      testSub.testVolatile();
      testSub.tearDown();
      Log.exit(TestVolatile.ME, "Good bye");
   }
}
