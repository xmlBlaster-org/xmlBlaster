/*------------------------------------------------------------------------------
Name:      TestSubManyClients.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubManyClients.java,v 1.3 2000/11/05 21:23:57 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.xmlBlaster.util.Log;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQoS;
import org.xmlBlaster.client.LoginQosWrapper;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests the method subscribe() with a later publish() with XPath query.
 * <br />
 * The subscribe() should be recognized for this later arriving publish()
 * <p>
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestSubManyClients
 *    jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestSubManyClients
 * </pre>
 */
public class TestSubManyClients extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private String publishOid = "dummy";
   private XmlBlasterConnection senderConnection;
   private String senderName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   class Subscriber {
      String loginName;
      XmlBlasterConnection connection;
      String subscribeOid;
   }

   private int numClients;
   private Subscriber[] subscribers;

   private StopWatch stopWatch = new StopWatch();

   /**
    * Constructs the TestSubManyClients object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubManyClients(String testName, String loginName)
   {
       super(testName);
       this.senderName = loginName;
       numClients = XmlBlasterProperty.get("numClients", 10);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      try {
         senderConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         senderConnection.login(senderName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          Log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          assert("Login failed: " + e.toString(), false);
      }
   }


   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown()
   {
      if (numReceived != numClients) {
         Log.error(ME, "numClients=" + numClients + " but numReceived=" + numReceived);
         assertEquals("numClients=" + numClients + " but numReceived=" + numReceived, numClients, numReceived);
      }

      Log.removeLogLevel("INFO");
      if (subscribers != null) {
         for (int ii=0; ii<numClients; ii++) {
            Subscriber sub = subscribers[ii];
            sub.connection.logout();
         }
      }
      Log.addLogLevel("INFO");

      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' queryType='EXACT'>\n" +
                      "</key>";
      String qos = "<qos></qos>";
      String[] strArr = null;
      try {
         strArr = senderConnection.erase(xmlKey, qos);
         if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }

      senderConnection.logout();
      Log.info(ME, "Logout done");
   }


   /**
    * Many clients subscribe to a message.
    */
   public void subcribe()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing ...");

      String passwd = "secret";

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      subscribers = new Subscriber[numClients];

      long usedBefore = getUsedServerMemory();

      Log.info(ME, "Setting up " + numClients + " subscriber clients ...");
      Log.removeLogLevel("INFO");
      stopWatch = new StopWatch();
      for (int ii=0; ii<numClients; ii++) {
         Subscriber sub = new Subscriber();
         sub.loginName = "Joe-" + ii;

         try {
            sub.connection = new XmlBlasterConnection();
            LoginQosWrapper loginQosW = new LoginQosWrapper(); // "<qos></qos>"; During login this is manipulated (callback address added)
            sub.connection.login(sub.loginName, passwd, loginQosW, this);
         }
         catch (Exception e) {
             Log.error(ME, "Login failed: " + e.toString());
             assert("Login failed: " + e.toString(), false);
         }

         try {
            sub.subscribeOid = sub.connection.subscribe(subKey, subQos);
            Log.info(ME, "Client " + sub.loginName + " subscribed to " + subKeyW.getUniqueKey());
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
            assert("subscribe - XmlBlasterException: " + e.reason, false);
         }

         subscribers[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec
      Log.addLogLevel("INFO");

      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      Log.info(ME, numClients + " subscriber clients are ready.");
      Log.info(ME, "Server memory per login consumed=" + memPerLogin);
      Log.info(ME, "Time " + (long)(numClients/timeForLogins) + " logins/sec");
   }


   long getUsedServerMemory() {
      String xmlKey = "<key oid='__sys__UsedMem' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      try {
         MessageUnit[] msgArr = senderConnection.get(xmlKey, qos);
         String mem = new String(msgArr[0].content);
         return new Long(mem).longValue();
      } catch (XmlBlasterException e) {
         Log.warn(ME, e.toString());
         return 0L;
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   public void publish()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "</key>";
      String senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         stopWatch = new StopWatch();
         String tmp = senderConnection.publish(msgUnit);
         assertEquals("Wrong publishOid", publishOid, tmp);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publish - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it,<br />
    * the previous XPath subscription should match and send an update.
    */
   public void testPublishAfterSubscribe()
   {
      subcribe();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publish();
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      //assertEquals("numReceived after publishing", numClients, numReceived); // message arrived?
   }


   /**
    * This is the callback method (I_Callback) invoked from XmlBlasterConnection
    * informing the client in an asynchronous mode about a new message.
    * <p />
    * The raw CORBA-BlasterCallback.update() is unpacked and for each arrived message
    * this update is called.
    *
    * @param loginName The name to whom the callback belongs
    * @param updateKey The arrived key
    * @param content   The arrived message content
    * @param qos       Quality of Service of the MessageUnit
    */
   public void update(String loginName, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      //Log.info(ME, "Client " + loginName + " receiving update of message oid=" + updateKey.getUniqueKey() + "...");
      numReceived += 1;
      if (numReceived == numClients) {
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         Log.info(ME, numReceived + " messages updated, average messages/second = " + avg + stopWatch.nice());
      }
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubManyClients("testPublishAfterSubscribe", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestSubManyClients
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSubManyClients</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestSubManyClients testSub = new TestSubManyClients("TestSubManyClients", "Tim");
      testSub.setUp();
      testSub.testPublishAfterSubscribe();
      testSub.tearDown();
      Log.exit(TestSubManyClients.ME, "Good bye");
   }
}

