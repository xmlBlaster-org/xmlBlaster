/*------------------------------------------------------------------------------
Name:      TestSubLostClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubLostClient.java,v 1.4 2002/03/18 00:31:23 ruff Exp $
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
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

import test.framework.*;


/**
 * This client tests if a subscribe still works when an other subcriber
 * on the same messages disappears.
 * <p>
 * This bug was reported by
 *       "Stefan Nickisch" <nickisch.stefan@stn-atlas.de>
 * <p>
 * Invoke examples:<br />
 * <pre>
 *  java org.xmlBlaster.Main
 *
 *  java testsuite.org.xmlBlaster.TestSubLostClient -client.protocol IIOP
 *
 *  jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient
 *  jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient
 * </pre>
 */
public class TestSubLostClient extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private final String publishOid1 = "dummy1";
   private XmlBlasterConnection oneConnection;
   private String oneName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   class Client {
      String loginName;
      XmlBlasterConnection connection;
      String subscribeOid;
   }

   private int numClients;
   private Client[] manyClients;

   private StopWatch stopWatch = new StopWatch();

   /**
    * Constructs the TestSubLostClient object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubLostClient(String testName, String loginName)
   {
       super(testName);
       this.oneName = loginName;
       numClients = 2;
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      Log.info(ME, "Setting up test ...");
      numReceived = 0;
      try {
         oneConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         oneConnection.login(oneName, passwd, null, this); // Login to xmlBlaster
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
      if (numReceived != (numClients-1)) Log.error(ME, "numClients=" + (numClients-1) + " but numReceived=" + numReceived);
      assertEquals("numClients=1 but numReceived=" + numReceived, numClients-1, numReceived);

      if (manyClients != null) {
         for (int ii=0; ii<numClients; ii++) {
            Client sub = manyClients[ii];
            sub.connection.logout();
         }
      }

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         String[] strArr = null;
         try {
            strArr = oneConnection.erase(xmlKey, qos);
            if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
         } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      }

      oneConnection.logout();
      Log.info(ME, "Logout done");
   }


   /**
    * Many clients subscribe to a message.
    */
   public void susbcribeMany()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing ...");

      String passwd = "secret";

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numClients];

      Log.info(ME, "Setting up " + numClients + " subscriber clients ...");
      stopWatch = new StopWatch();
      for (int ii=0; ii<numClients; ii++) {
         Client sub = new Client();
         sub.loginName = "Joe-" + ii;

         try {
            sub.connection = new XmlBlasterConnection();
            ConnectQos loginQosW = new ConnectQos(); // "<qos></qos>"; During login this is manipulated (callback address added)
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

         manyClients[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec

      Log.info(ME, numClients + " subscriber clients are ready.");
      Log.info(ME, "Time " + (long)(numClients/timeForLogins) + " logins/sec");

      manyClients[0].connection.shutdown(); // Kill the shutdown server, without doing a logout
      Log.info(ME, "Killed callback server of first client.");
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid1 is checked
    */
   public void publishOne()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid1 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "</key>";
      String senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
      try {
         stopWatch = new StopWatch();
         String tmp = oneConnection.publish(msgUnit);
         assertEquals("Wrong publishOid1", publishOid1, tmp);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid1);
      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException in publish: " + e.reason);
         assert("XmlBlasterException in publish: " + e.reason, true);
      }
   }


   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update.
    */
   public void testManyClients()
   {
      Log.plain(ME, "");
      Log.info(ME, "TEST 1, many subscribers, one publisher ...");

      susbcribeMany();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publishOne();
      Log.info(ME, "Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      Log.info(ME, "Received " + numReceived + " updates");
      assertEquals("Wrong number of updates", numClients-1, numReceived); // One client killed its callback server
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQoS)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQoS updateQoS)
   {
      Log.info(ME, "Client " + cbSessionId + " receiving update of message oid=" + updateKey.getUniqueKey() + "...");
      numReceived++;
      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubLostClient("testManyClients", loginName));
       return suite;
   }


   /**
    * Invoke: jaco testsuite.org.xmlBlaster.TestSubLostClient
    * <p />
    * Note you need 'jaco' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   jaco -Djava.compiler= test.textui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient</pre>
    */
   public static void main(String args[])
   {
      try {
         XmlBlasterProperty.init(args);
      } catch(org.jutils.JUtilsException e) {
         Log.panic(ME, e.toString());
      }
      TestSubLostClient testSub = new TestSubLostClient("TestSubLostClient", "Tim");
      testSub.setUp();
      testSub.testManyClients();
      testSub.tearDown();
      Log.exit(TestSubLostClient.ME, "Good bye");
   }
}

