/*------------------------------------------------------------------------------
Name:      TestSubManyClients.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubManyClients.java,v 1.8 2002/05/01 21:40:24 ruff Exp $
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
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
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
 *  java  -Xms10m -Xmx220m org.xmlBlaster.Main -info false
 *
 *  java testsuite.org.xmlBlaster.TestSubManyClients -numClients 10000 -client.protocol RMI -warn false
 *
 *  jaco test.textui.TestRunner testsuite.org.xmlBlaster.TestSubManyClients
 *  jaco test.ui.TestRunner testsuite.org.xmlBlaster.TestSubManyClients
 * </pre>
 */
public class TestSubManyClients extends TestCase implements I_Callback
{
   private static String ME = "Tim";
   private boolean messageArrived = false;

   private final String publishOid1 = "dummy1";
   private final String publishOid2 = "dummy2";
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
    * Constructs the TestSubManyClients object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubManyClients(String testName, String loginName)
   {
       super(testName);
       this.oneName = loginName;
       numClients = XmlBlasterProperty.get("numClients", 10);
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
      if (numReceived != numClients) {
         Log.error(ME, "numClients=" + numClients + " but numReceived=" + numReceived);
         assertEquals("numClients=" + numClients + " but numReceived=" + numReceived, numClients, numReceived);
      }

      Log.removeLogLevel("INFO");
      if (manyClients != null) {
         for (int ii=0; ii<numClients; ii++) {
            Client sub = manyClients[ii];
            sub.connection.logout();
         }
      }
      Log.addLogLevel("INFO");

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

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid2 + "' queryType='EXACT'>\n" +
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
   public void subcribeMany()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing ...");

      String passwd = "secret";

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numClients];

      long usedBefore = getUsedServerMemory();

      Log.info(ME, "Setting up " + numClients + " subscriber clients ...");
      Log.removeLogLevel("INFO");
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
      Log.addLogLevel("INFO");

      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      Log.info(ME, numClients + " subscriber clients are ready.");
      Log.info(ME, "Server memory per login consumed=" + memPerLogin);
      Log.info(ME, "Time " + (long)(numClients/timeForLogins) + " logins/sec");
   }


   /**
    * Query xmlBlaster for its current memory consumption. 
    */
   long getUsedServerMemory() {
      String xmlKey = "<key oid='__sys__UsedMem' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      try {
         MessageUnit[] msgArr = oneConnection.get(xmlKey, qos);
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
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("publishOne - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update. 
    */
   public void testManyClients()
   {
      Log.plain(ME, "");
      Log.info(ME, "TEST 1, many publishers, one subscriber ...");

      subcribeMany();
      Util.delay(1000L);                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publishOne();
      Log.info(ME, "Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);


      Log.plain(ME, "");
      Log.info(ME, "TEST 2, many publishers, one subscriber ...");

      subcribeOne();
      Util.delay(100L);                                             // Wait some time ...

      numReceived = 0;
      publishMany();
      Log.info(ME, "Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);
   }


   /**
    * One client subscribes to a message. 
    */
   public void subcribeOne()
   {
      if (Log.TRACE) Log.trace(ME, "Subscribing ...");

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid2);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid2 + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      try {
         String subscribeOid = oneConnection.subscribe(subKey, subQos);
         Log.info(ME, "Client " + oneName + " subscribed to " + subKeyW.getUniqueKey());
      } catch(XmlBlasterException e) {
         Log.warn(ME, "XmlBlasterException: " + e.reason);
         assert("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid1 is checked
    */
   public void publishMany()
   {
      if (Log.TRACE) Log.trace(ME, "Publishing a message ...");

      PublishKeyWrapper pubKeyW = new PublishKeyWrapper(publishOid2, contentMime, contentMimeExtended);
      String pubKey = pubKeyW.toXml(); // "<key oid='" + publishOid2 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'></key>"

      PublishQosWrapper pubQosW = new PublishQosWrapper();
      String pubQos = pubQosW.toXml(); // "<qos></qos>"

      long usedBefore = getUsedServerMemory();

      Log.info(ME, numClients + " clients are publishing one message each ...");
      Log.removeLogLevel("INFO");
      stopWatch = new StopWatch();

      for (int ii=0; ii<numClients; ii++) {
         Client client = manyClients[ii];
         // The content changes, equal contents would not be updated to the subscriber without <forceUpdate/>
         String senderContent = "New content from publisher " + client.loginName;
         MessageUnit msgUnit = new MessageUnit(pubKey, senderContent.getBytes(), pubQos);
         try {
            String tmp = oneConnection.publish(msgUnit);
            assertEquals("Wrong publishOid2", publishOid2, tmp);
         } catch(XmlBlasterException e) {
            Log.warn(ME, "XmlBlasterException: " + e.reason);
            assert("publishOne - XmlBlasterException: " + e.reason, false);
         }
      }

      double timeToPublish = (double)stopWatch.elapsed()/1000.; // msec -> sec
      Log.addLogLevel("INFO");

      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      Log.info(ME, numClients + " have published their messages.");
      Log.info(ME, "Server memory consumed=" + memPerLogin + " bytes.");
      Log.info(ME, "Time " + (long)(numClients/timeToPublish) + " publish/sec");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      //Log.info(ME, "Client " + loginName + " receiving update of message oid=" + updateKey.getUniqueKey() + "...");
      numReceived++;

      if (numReceived == numClients) {
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         Log.info(ME, numReceived + " messages updated, average messages/second = " + avg + stopWatch.nice());
      }
      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
       TestSuite suite= new TestSuite();
       String loginName = "Tim";
       suite.addTest(new TestSubManyClients("testManyClients", loginName));
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
      testSub.testManyClients();
      testSub.tearDown();
      Log.exit(TestSubManyClients.ME, "Good bye");
   }
}

