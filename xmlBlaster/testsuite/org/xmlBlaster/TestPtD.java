/*------------------------------------------------------------------------------
Name:      TestPtD.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster and publishing to destinations
Comment:   Testing PtP (point to point) messages
Version:   $Id: TestPtD.java,v 1.1 1999/12/12 18:21:41 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.client.CorbaConnection;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Args;
import org.xmlBlaster.util.StopWatch;
import org.xmlBlaster.serverIdl.*;
import org.xmlBlaster.clientIdl.*;
import test.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style, Manuel sends to Ulrike a love letter. 
 * <p>
 * Note that the two clients (client logins) are simulated in this class.<br />
 * Manuel is the 'sender' and Ulrike the 'receiver'
 * <p>
 * Invoke examples:<br />
 * <code>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestPtD
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestPtD
 * </code>
 */
public class TestPtD extends TestCase
{
   private Server senderXmlBlaster = null;
   private final static String ME = "TestPtD";
   private final String[] args;

   private final String senderName = "Manuel";
   private String publishOid = "";
   private CorbaConnection senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private CorbaConnection receiverConnection = null;
   private Server receiverXmlBlaster = null;
   private int numReceived = 0;

   private boolean messageArrived = false;


   /**
    * Constructs the TestPtD object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    * @param args      Array of command line start parameters
    */
   public TestPtD(String testName, String[] args)
   {
       super(testName);
       this.args = args;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      if (initReceiver() == false) 
         return;

      if (initSender() == false)
         return;
   }


   /**
    * Creates a CORBA connection and does a login. 
    * <p />
    * @return true: No errors, false: panic
    */
   private boolean initReceiver()
   {
      boolean retVal = true;
      try {
         receiverConnection = new CorbaConnection(args);
         BlasterCallback callback = receiverConnection.createCallbackServer(new TestPtDCallback(receiverName, this));
         if (Log.TRACE) Log.trace(receiverName, "Exported BlasterCallback Server interface for " + receiverName);
         String passwd = "some";
         receiverXmlBlaster = receiverConnection.login(receiverName, passwd, callback, "<qos></qos>");
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          retVal = false;
      }
      return retVal;
   }


   /**
    * Creates a CORBA connection and does a login. 
    * <p />
    * @return true: No errors, false: panic
    */
   private boolean initSender()
   {
      boolean retVal = true;
      try {
         senderConnection = new CorbaConnection(args);
         BlasterCallback callback = senderConnection.createCallbackServer(new TestPtDCallback(senderName, this));
         if (Log.TRACE) Log.trace(senderName, "Exported BlasterCallback Server interface for " + senderName);
         String passwd = "some";
         senderXmlBlaster = senderConnection.login(senderName, passwd, callback, "<qos></qos>");
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
          retVal = false;
      }
      return retVal;
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... logout
    */
   protected void tearDown()
   {
      receiverConnection.logout(receiverXmlBlaster);
      senderConnection.logout(senderXmlBlaster);
   }


   /**
    * TEST: Subscribe to messages with XPATH.
    * <p />
    * The returned subscribeOid is checked
    */
   public void testPtOneDestination()
   {
      if (Log.TRACE) Log.trace(ME, "Testing point to one destination ...");

      // Construct a love message and send it to Ulrike
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                        "</key>";

      String qos = "<qos>" +
                     "   <destination queryType='EXACT'>" +
                           receiverName +
                     "   </destination>" +
                     "</qos>";

      senderContent = "Hi " + receiverName + ", i love you, " + senderName;
      MessageUnit messageUnit = new MessageUnit(xmlKey, senderContent.getBytes());
      try {
         publishOid = senderXmlBlaster.publish(messageUnit, qos);
         Log.info(ME, "Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }

      waitOnUpdate(5000L);
      assertEquals("numReceived after sending", 1, numReceived); // message arrived?
      numReceived = 0;
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();

       String[] args = new String[0];  // dummy

       suite.addTest(new TestPtD("testPtOneDestination", args));

       return suite;
   }


   /**
    * The TestPtDCallback.update calls this method, to allow some error checking. 
    * @param loginName        The name to whom the callback belongs
    * @param messageUnitArr   Contains a sequence of 0 - n MessageUnit structs
    * @param qos_literal_Arr  Quality of Service for each MessageUnit
    */
   public void update(String loginName, MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      if (Log.CALLS) Log.calls(ME, "Receiving update of " + messageUnitArr.length + " message ...");

      if (messageUnitArr.length != 0)
         numReceived += messageUnitArr.length;
      else
         numReceived = -1;       // error

      for (int ii=0; ii<messageUnitArr.length; ii++) {
         MessageUnit messageUnit = messageUnitArr[ii];
         UpdateKey updateKey = null;
         UpdateQoS updateQoS = null;
         String keyOid = null;
         byte[] content = messageUnit.content;
         try {
            updateKey = new UpdateKey(messageUnit.xmlKey);
            keyOid = updateKey.getUniqueKey();
            updateQoS = new UpdateQoS(qos_literal_Arr[ii]);
         } catch (XmlBlasterException e) {
            Log.error(ME, e.reason);
         }

         // Now we know all about the received message, dump it or do some checks
         Log.plain("UpdateKey", updateKey.printOn().toString());
         Log.plain("content", (new String(content)).toString());
         Log.plain("UpdateQoS", updateQoS.printOn().toString());
         Log.info(ME, "Received message [" + keyOid + "] from publisher " + updateQoS.getSender());

         assertEquals("Wrong receveiver", receiverName, loginName);
         assertEquals("Wrong sender", senderName, updateQoS.getSender());
         assertEquals("Wrong oid of message returned", publishOid, keyOid);
         assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      }

      messageArrived = true;
   }


   /**
    * Little helper, waits until the variable 'messageArrive' is set
    * to true, or returns when the given timeout occurs.
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
            Log.warning(ME, "Timeout of " + timeout + " occurred");
            break;
         }
      }
      messageArrived = false;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestPtD
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <code>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestPtD</code>
    */
   public static void main(String args[])
   {
      TestPtD testSub = new TestPtD("TestPtD", args);
      testSub.setUp();
      testSub.testPtOneDestination();
      testSub.tearDown();
      Log.exit(TestPtD.ME, "Good bye");
   }
}


/**
 * Example for a callback implementation.
 */
class TestPtDCallback implements BlasterCallbackOperations
{
   private final String ME;
   private final TestPtD boss;
   private final String loginName;

   /**
    * Construct a persistently named object.
    */
   public TestPtDCallback(String name, TestPtD boss)
   {
      this.ME = "TestPtDCallback-" + name;
      this.boss = boss;
      this.loginName = name;
      if (Log.CALLS) Log.trace(ME, "Entering constructor with argument");
   }


   /**
    * This is the callback method invoked from the server
    * informing the client in an asynchronous mode about new messages. 
    * @param loginName        The name to whom the callback belongs
    * @param messageUnitArr   Contains a sequence of 0 - n MessageUnit structs
    * @param qos_literal_Arr  Quality of Service for each MessageUnit
    */
   public void update(MessageUnit[] messageUnitArr, String[] qos_literal_Arr)
   {
      boss.update(loginName, messageUnitArr, qos_literal_Arr); // Call my boss, so she can check for errors
   }
} // TestPtDCallback

