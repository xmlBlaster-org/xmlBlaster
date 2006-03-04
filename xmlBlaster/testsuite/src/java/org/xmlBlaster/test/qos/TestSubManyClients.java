/*------------------------------------------------------------------------------
Name:      TestSubManyClients.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.time.StopWatch;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.util.MsgUnit;

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
 *  java org.xmlBlaster.test.qos.TestSubManyClients -numClients 10000 -dispatch/connection/protocol RMI -warn false
 *
 *  java junit.textui.TestRunner org.xmlBlaster.test.qos.TestSubManyClients
 *  java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestSubManyClients
 * </pre>
 */
public class TestSubManyClients extends TestCase implements I_Callback
{
   private static String ME = "TestSubManyClients";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestSubManyClients.class.getName());

   private boolean messageArrived = false;

   private final String publishOid1 = "dummy1";
   private final String publishOid2 = "dummy2";
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
    * Constructs the TestSubManyClients object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestSubManyClients(Global glob, String testName, String loginName)
   {
      super(testName);
      this.glob = glob;

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
      log.info("Setting up test ...");
      numReceived = 0;
      try {
         Global globOne = glob.getClone(null);
         oneConnection = globOne.getXmlBlasterAccess(); // Find orb
         String passwd = "secret";
         ConnectQos qos = new ConnectQos(globOne, oneName, passwd);
         oneConnection.connect(qos, this); // Login to xmlBlaster
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
      if (numReceived != numClients) {
         log.severe("numClients=" + numClients + " but numReceived=" + numReceived);
         assertEquals("numClients=" + numClients + " but numReceived=" + numReceived, numClients, numReceived);
      }


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
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = oneConnection.erase(xmlKey, qos);
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase-XmlBlasterException: " + e.getMessage()); }
      }

      {
         String xmlKey = "<?xml version='1.0' encoding='ISO-8859-1' ?>\n" +
                         "<key oid='" + publishOid2 + "' queryType='EXACT'>\n" +
                         "</key>";
         String qos = "<qos></qos>";
         try {
            EraseReturnQos[] arr = oneConnection.erase(xmlKey, qos);
            assertEquals("Ersae", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase-XmlBlasterException: " + e.getMessage()); }
      }

      oneConnection.disconnect(null);
      log.info("Logout done");
   }


   /**
    * Many clients subscribe to a message.
    */
   public void subcribeMany()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");

      String passwd = "secret";

      SubscribeKey subKeyW = new SubscribeKey(glob, publishOid1);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid1 + "' queryType='EXACT'></key>";

      SubscribeQos subQosW = new SubscribeQos(glob); // "<qos></qos>";
      String subQos = subQosW.toXml();

      manyClients = new Client[numClients];

      long usedBefore = getUsedServerMemory();

      log.info("Setting up " + numClients + " subscriber clients ...");

      stopWatch = new StopWatch();
      for (int ii=0; ii<numClients; ii++) {
         Client sub = new Client();
         sub.loginName = "Joe-" + ii;

         try {
            Global globTmp = glob.getClone(null);
            sub.connection = globTmp.getXmlBlasterAccess();
            ConnectQos loginQosW = new ConnectQos(globTmp, sub.loginName, passwd); // "<qos></qos>"; During login this is manipulated (callback address added)
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


      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      log.info(numClients + " subscriber clients are ready.");
      log.info("Server memory per login consumed=" + memPerLogin);
      log.info("Time " + (long)(numClients/timeForLogins) + " logins/sec");
   }


   /**
    * Query xmlBlaster for its current memory consumption. 
    */
   long getUsedServerMemory() {
      String xmlKey = "<key oid='__cmd:?usedMem' queryType='EXACT'></key>";
      String qos = "<qos></qos>";
      try {
         MsgUnit[] msgArr = oneConnection.get(xmlKey, qos);
         String mem = new String(msgArr[0].getContent());
         return new Long(mem).longValue();
      } catch (XmlBlasterException e) {
         log.warning(e.toString());
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
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("publishOne - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it,
    * all clients should receive an update. 
    */
   public void testManyClients()
   {
      System.out.println("");
      log.info("TEST 1, many publishers, one subscriber ...");

      subcribeMany();
      try { Thread.currentThread().sleep(1000L); } catch( InterruptedException i) {}                                            // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      publishOne();
      log.info("Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);


      System.out.println("");
      log.info("TEST 2, many publishers, one subscriber ...");

      subcribeOne();
      try { Thread.currentThread().sleep(100L); } catch( InterruptedException i) {}                                             // Wait some time ...

      numReceived = 0;
      publishMany();
      log.info("Waiting long enough for updates ...");
      Util.delay(2000L + 10 * numClients);                          // Wait some time for callback to arrive ...
      assertEquals("Wrong number of updates", numClients, numReceived);
   }


   /**
    * One client subscribes to a message. 
    */
   public void subcribeOne()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Subscribing ...");

      SubscribeKey subKeyW = new SubscribeKey(glob, publishOid2);
      String subKey = subKeyW.toXml(); // "<key oid='" + publishOid2 + "' queryType='EXACT'></key>";

      SubscribeQos subQosW = new SubscribeQos(glob); // "<qos></qos>";
      String subQos = subQosW.toXml();

      try {
         SubscribeReturnQos subscribeOid = oneConnection.subscribe(subKey, subQos);
         log.info("Client " + oneName + " subscribed to " + subKeyW.getOid());
      } catch(XmlBlasterException e) {
         log.warning("XmlBlasterException: " + e.getMessage());
         assertTrue("subscribe - XmlBlasterException: " + e.getMessage(), false);
      }
   }


   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid1 is checked
    */
   public void publishMany()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Publishing a message ...");

      PublishKey pubKeyW = new PublishKey(glob, publishOid2, contentMime, contentMimeExtended);
      String pubKey = pubKeyW.toXml(); // "<key oid='" + publishOid2 + "' contentMime='" + contentMime + "' contentMimeExtended='" + contentMimeExtended + "'></key>"

      PublishQos pubQosW = new PublishQos(glob);
      String pubQos = pubQosW.toXml(); // "<qos></qos>"

      long usedBefore = getUsedServerMemory();

      log.info(numClients + " clients are publishing one message each ...");

      stopWatch = new StopWatch();

      for (int ii=0; ii<numClients; ii++) {
         Client client = manyClients[ii];
         // The content changes, equal contents would not be updated to the subscriber without <forceUpdate/>
         String senderContent = "New content from publisher " + client.loginName;
         try {
            MsgUnit msgUnit = new MsgUnit(pubKey, senderContent.getBytes(), pubQos);
            PublishReturnQos tmp = oneConnection.publish(msgUnit);
            assertEquals("Wrong publishOid2", publishOid2, tmp.getKeyOid());
         } catch(XmlBlasterException e) {
            log.warning("XmlBlasterException: " + e.getMessage());
            assertTrue("publishOne - XmlBlasterException: " + e.getMessage(), false);
         }
      }

      double timeToPublish = (double)stopWatch.elapsed()/1000.; // msec -> sec


      long usedAfter = getUsedServerMemory();
      long memPerLogin = (usedAfter - usedBefore)/numClients;

      log.info(numClients + " have published their messages.");
      log.info("Server memory consumed=" + memPerLogin + " bytes.");
      log.info("Time " + (long)(numClients/timeToPublish) + " publish/sec");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      //log.info("Client " + loginName + " receiving update of message oid=" + updateKey.getOid() + "...");
      numReceived++;

      if (numReceived == numClients) {
         long avg = 0;
         double elapsed = stopWatch.elapsed();
         if (elapsed > 0.)
            avg = (long)(1000.0 * numReceived / elapsed);
         log.info(numReceived + " messages updated, average messages/second = " + avg + stopWatch.nice());
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

