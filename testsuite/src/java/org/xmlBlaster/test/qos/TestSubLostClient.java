/*------------------------------------------------------------------------------
Name:      TestSubLostClient.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.xmlBlaster.util.StopWatch;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.protocol.socket.SocketCallbackImpl;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
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
 *  java org.xmlBlaster.test.qos.TestSubLostClient -dispatch/connection/protocol IIOP
 *
 *  java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubLostClient
 *  java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubLostClient
 * </pre>
 */
public class TestSubLostClient extends TestCase implements I_Callback
{
   private static String ME = "TestSubLostClient";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubLostClient.class.getName());

   private boolean messageArrived = false;

   private final String publishOid1 = "dummy1";
   private I_XmlBlasterAccess oneConnection;
   private String oneName;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";
   private final String contentMimeExtended = "1.0";

   class Client {
      String loginName;
      I_XmlBlasterAccess connection;
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
      log.info("Setting up test ...");
      numReceived = 0;
      try {
         oneConnection = glob.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, oneName, passwd);
         oneConnection.connect(connectQos, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.severe("Login failed: " + e.toString());
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
      if (numReceived != (numClients-1)) log.severe("numClients=" + (numClients-1) + " but numReceived=" + numReceived);
      assertEquals("numClients=1 but numReceived=" + numReceived, numClients-1, numReceived);

      if (manyClients != null) {
         for (int ii=0; ii<numClients; ii++) {
            Client sub = manyClients[ii];
            sub.connection.disconnect(null);
         }
      }

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
                         "</key>";
         try {
            EraseReturnQos[] arr = oneConnection.erase(xmlKey, "<qos/>");
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      }

      oneConnection.disconnect(null);
      log.info("Logout done");
   }


   /**
    * Many clients subscribe to a message.
    */
   public void susbcribeMany()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");

      String passwd = "secret";

      SubscribeKey subKeyW = new SubscribeKey(glob, publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQos subQosW = new SubscribeQos(glob); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numClients];

      log.info("Setting up " + numClients + " subscriber clients ...");
      stopWatch = new StopWatch();
      for (int ii=0; ii<numClients; ii++) {
         Client sub = new Client();
         sub.loginName = "Joe-" + ii;

         try {
            Global globSub = glob.getClone(null);
            sub.connection = globSub.getXmlBlasterAccess();
            ConnectQos loginQosW = new ConnectQos(globSub, sub.loginName, passwd); // "<qos></qos>"; During login this is manipulated (callback address added)
            sub.connection.connect(loginQosW, this);
         }
         catch (Exception e) {
             log.severe("Login failed: " + e.toString());
             assertTrue("Login failed: " + e.toString(), false);
         }

         try {
            sub.subscribeOid = sub.connection.subscribe(subKey, subQos).getSubscriptionId();
            log.info("Client " + sub.loginName + " subscribed to " + subKeyW.getOid());
         } catch(XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
            assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
         }

         manyClients[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec

      log.info(numClients + " subscriber clients are ready.");
      log.info("Time " + (long)(numClients/timeForLogins) + " logins/sec");

      boolean isSocket = (manyClients[0].connection.getCbServer() instanceof SocketCallbackImpl);

      if (isSocket) {
         // xmlBlaster destroys our first session:
         manyClients[0].connection.leaveServer(null);
         log.info("Leave server of first client.");
      }
      else { // "IOR"
         try {
            manyClients[0].connection.getCbServer().shutdown(); // Kill the callback server, without doing a logout
         }
         catch (Throwable e) {
            e.printStackTrace();
            assertTrue("Problems with connection,shutdownCb()", false);
         }
         log.info("Killed callback server of first client.");
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid1 is checked
    */
   public void publishOne()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                      "<key oid='" + publishOid1 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'>\n" +
                      "</key>";
      String senderContent = "Yeahh, i'm the new content";
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), "<qos></qos>");
         stopWatch = new StopWatch();
         String tmp = oneConnection.publish(msgUnit).getKeyOid();
         assertEquals("Wrong publishOid1", publishOid1, tmp);
         log.info("Success: Publishing done, returned oid=" + publishOid1);
      } catch(XmlBlasterException e) {
         log.severe("XmlBlasterException in publish: " + e.getMessage());
         assertTrue("XmlBlasterException in publish: " + e.getMessage(), true);
      }
   }


   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update.
    */
   public void testManyClients()
   {
      System.out.println("");
      log.info("TEST 1, many subscribers, one publisher ...");

      susbcribeMany();
      try { Thread.sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publishOne();
      log.info("Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      log.info("Received " + numReceived + " updates");
      assertEquals("Wrong number of updates", numClients-1, numReceived); // One client killed its callback server
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info("Client " + cbSessionId + " receiving update of message oid=" + updateKey.getOid() + "...");
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
    * Invoke: java org.xmlBlaster.test.qos.TestSubLostClient
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubLostClient</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubLostClient testSub = new TestSubLostClient(glob, "TestSubLostClient", "Tim");
      testSub.setUp();
      testSub.testManyClients();
      testSub.tearDown();
   }
}

