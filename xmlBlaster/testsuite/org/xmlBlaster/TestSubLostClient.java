/*------------------------------------------------------------------------------
Name:      TestSubLostClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubLostClient.java,v 1.13 2002/06/25 18:03:58 ruff Exp $
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.time.StopWatch;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

import junit.framework.*;


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
 *  java junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient
 *  java junit.ui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient
 * </pre>
 */
public class TestSubLostClient extends TestCase implements I_Callback
{
   private static String ME = "TestSubLostClient";
   private final Global glob;
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
   public TestSubLostClient(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
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
          assertTrue("Login failed: " + e.toString(), false);
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
         try {
            EraseRetQos[] arr = oneConnection.erase(xmlKey, "<qos/>");
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }
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
            sub.connection = new XmlBlasterConnection(glob);
            ConnectQos loginQosW = new ConnectQos(glob); // "<qos></qos>"; During login this is manipulated (callback address added)
            sub.connection.login(sub.loginName, passwd, loginQosW, this);
         }
         catch (Exception e) {
             Log.error(ME, "Login failed: " + e.toString());
             assertTrue("Login failed: " + e.toString(), false);
         }

         try {
            sub.subscribeOid = sub.connection.subscribe(subKey, subQos).getSubscriptionId();
            Log.info(ME, "Client " + sub.loginName + " subscribed to " + subKeyW.getUniqueKey());
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
            assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
         }

         manyClients[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec

      Log.info(ME, numClients + " subscriber clients are ready.");
      Log.info(ME, "Time " + (long)(numClients/timeForLogins) + " logins/sec");

      try {
         manyClients[0].connection.shutdownCb(); // Kill the callback server, without doing a logout
      }
      catch (Throwable e) {
         e.printStackTrace();
         assertTrue("Problems with connection,shutdownCb()", false);
      }
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
         String tmp = oneConnection.publish(msgUnit).getOid();
         assertEquals("Wrong publishOid1", publishOid1, tmp);
         Log.info(ME, "Success: Publishing done, returned oid=" + publishOid1);
      } catch(XmlBlasterException e) {
         Log.error(ME, "XmlBlasterException in publish: " + e.reason);
         assertTrue("XmlBlasterException in publish: " + e.reason, true);
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
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
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
       suite.addTest(new TestSubLostClient(new Global(), "testManyClients", loginName));
       return suite;
   }


   /**
    * Invoke: java testsuite.org.xmlBlaster.TestSubLostClient
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestSubLostClient</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestSubLostClient testSub = new TestSubLostClient(glob, "TestSubLostClient", "Tim");
      testSub.setUp();
      testSub.testManyClients();
      testSub.tearDown();
      Log.exit(TestSubLostClient.ME, "Good bye");
   }
}

