/*------------------------------------------------------------------------------
Name:      TestSubManyClients.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id: TestSubManyClients.java,v 1.2 2002/09/13 23:18:31 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.time.StopWatch;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.SubscribeKeyWrapper;
import org.xmlBlaster.client.SubscribeQosWrapper;
import org.xmlBlaster.client.PublishKeyWrapper;
import org.xmlBlaster.client.PublishQosWrapper;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.xmlBlaster.test.Util;
import junit.framework.*;


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
 *  java org.xmlBlaster.test.qos.TestSubManyClients -numClients 10000 -client.protocol RMI -warn false
 *
 *  java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubManyClients
 *  java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubManyClients
 * </pre>
 */
public class TestSubManyClients extends TestCase implements I_Callback
{
   private static String ME = "TestSubManyClients";
   private final Global glob;
   private final LogChannel log;

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
   public TestSubManyClients(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
      this.oneName = loginName;
      numClients = glob.getProperty().get("numClients", 10);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp()
   {
      log.info(ME, "Setting up test ...");
      numReceived = 0;
      try {
         oneConnection = new XmlBlasterConnection(); // Find orb
         String passwd = "secret";
         oneConnection.login(oneName, passwd, null, this); // Login to xmlBlaster
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
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
      if (numReceived != numClients) {
         log.error(ME, "numClients=" + numClients + " but numReceived=" + numReceived);
         assertEquals("numClients=" + numClients + " but numReceived=" + numReceived, numClients, numReceived);
      }

      log.removeLogLevel("INFO");
      if (manyClients != null) {
         for (int ii=0; ii<numClients; ii++) {
            Client sub = manyClients[ii];
            sub.connection.disconnect(null);
         }
      }
      log.addLogLevel("INFO");

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid1 + "' queryType='EXACT'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         try {
            EraseRetQos[] arr = oneConnection.erase(xmlKey, qos);
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase-XmlBlasterException: " + e.reason); }
      }

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid2 + "' queryType='EXACT'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         try {
            EraseRetQos[] arr = oneConnection.erase(xmlKey, qos);
            assertEquals("Ersae", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase-XmlBlasterException: " + e.reason); }
      }

      oneConnection.disconnect(null);
      log.info(ME, "Logout done");
   }


   /**
    * Many clients subscribe to a message.
    */
   public void subcribeMany()
   {
      if (log.TRACE) log.trace(ME, "Subscribing ...");

      String passwd = "secret";

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numClients];

      long usedBefore = getUsedServerMemory();

      log.info(ME, "Setting up " + numClients + " subscriber clients ...");
      log.removeLogLevel("INFO");
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
             log.error(ME, "Login failed: " + e.toString());
             assertTrue("Login failed: " + e.toString(), false);
         }

         try {
            sub.subscribeOid = sub.connection.subscribe(subKey, subQos).getSubscriptionId();
            log.info(ME, "Client " + sub.loginName + " subscribed to " + subKeyW.getUniqueKey());
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.reason);
            assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
         }

         manyClients[ii] = sub;
      }
      double timeForLogins = (double)stopWatch.elapsed()/1000.; // msec -> sec
      log.addLogLevel("INFO");

      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      log.info(ME, numClients + " subscriber clients are ready.");
      log.info(ME, "Server memory per login consumed=" + memPerLogin);
      log.info(ME, "Time " + (long)(numClients/timeForLogins) + " logins/sec");
   }


   /**
    * Query xmlBlaster for its current memory consumption. 
    */
   long getUsedServerMemory() {
      String xmlKey = "<key oid='__cmd:?usedMem' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      try {
         MessageUnit[] msgArr = oneConnection.get(xmlKey, qos);
         String mem = new String(msgArr[0].content);
         return new Long(mem).longValue();
      } catch (XmlBlasterException e) {
         log.warn(ME, e.toString());
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
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

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
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid1);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publishOne - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update. 
    */
   public void testManyClients()
   {
      log.plain(ME, "");
      log.info(ME, "TEST 1, many publishers, one subscriber ...");

      subcribeMany();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publishOne();
      log.info(ME, "Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);


      log.plain(ME, "");
      log.info(ME, "TEST 2, many publishers, one subscriber ...");

      subcribeOne();
      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}                                             // Wait some time ...

      numReceived = 0;
      publishMany();
      log.info(ME, "Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);
   }


   /**
    * One client subscribes to a message. 
    */
   public void subcribeOne()
   {
      if (log.TRACE) log.trace(ME, "Subscribing ...");

      SubscribeKeyWrapper subKeyW = new SubscribeKeyWrapper(publishOid2);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid2 + "' queryType='EXACT'></key>";

      SubscribeQosWrapper subQosW = new SubscribeQosWrapper(); // "<qos></qos>";
      String subQos = subQosW.toXml();

      try {
         SubscribeRetQos subscribeOid = oneConnection.subscribe(subKey, subQos);
         log.info(ME, "Client " + oneName + " subscribed to " + subKeyW.getUniqueKey());
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid1 is checked
    */
   public void publishMany()
   {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      PublishKeyWrapper pubKeyW = new PublishKeyWrapper(publishOid2, contentMime, contentMimeExtended);
      String pubKey = pubKeyW.toXml(); // "<key oid='" + publishOid2 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'></key>"

      PublishQosWrapper pubQosW = new PublishQosWrapper();
      String pubQos = pubQosW.toXml(); // "<qos></qos>"

      long usedBefore = getUsedServerMemory();

      log.info(ME, numClients + " clients are publishing one message each ...");
      log.removeLogLevel("INFO");
      stopWatch = new StopWatch();

      for (int ii=0; ii<numClients; ii++) {
         Client client = manyClients[ii];
         // The content changes, equal contents would not be updated to the subscriber without <forceUpdate/>
         String senderContent = "New content from publisher " + client.loginName;
         MessageUnit msgUnit = new MessageUnit(pubKey, senderContent.getBytes(), pubQos);
         try {
            PublishRetQos tmp = oneConnection.publish(msgUnit);
            assertEquals("Wrong publishOid2", publishOid2, tmp.getOid());
         } catch(XmlBlasterException e) {
            log.warn(ME, "XmlBlasterException: " + e.reason);
            assertTrue("publishOne - XmlBlasterException: " + e.reason, false);
         }
      }

      double timeToPublish = (double)stopWatch.elapsed()/1000.; // msec -> sec
      log.addLogLevel("INFO");

      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      log.info(ME, numClients + " have published their messages.");
      log.info(ME, "Server memory consumed=" + memPerLogin + " bytes.");
      log.info(ME, "Time " + (long)(numClients/timeToPublish) + " publish/sec");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      //log.info(ME, "Client " + loginName + " receiving update of message oid=" + updateKey.getUniqueKey() + "...");
      numReceived++;

      if (numReceived == numClients) {
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         log.info(ME, numReceived + " messages updated, average messages/second = " + avg + stopWatch.nice());
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
       suite.addTest(new TestSubManyClients(new Global(), "testManyClients", loginName));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestSubManyClients
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubManyClients</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestSubManyClients testSub = new TestSubManyClients(glob, "TestSubManyClients", "Tim");
      testSub.setUp();
      testSub.testManyClients();
      testSub.tearDown();
   }
}

