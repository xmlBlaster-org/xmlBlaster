/*------------------------------------------------------------------------------
Name:      TestPersistence2.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing durable messages
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.persistence;

// 24/09/1 19:27 mAd@ktaland.com
// 
// to test this code Alown : 
//  java \
//      -cp lib/javarb.jar:lib/xmlBlaster.jar:lib/junit.jar:lib/testsuite.jar \
//      junit.textui.TestRunner \
//      org.xmlBlaster.test.persistence.TestPersistence2
//

import org.jutils.io.FileUtil;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * This client tests the persistence driver, the $lt;isDurable> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistence2
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.persistence.TestPersistence2
 * </pre>
 * @author mAd@ktaland.com
 */
public class TestPersistence2 extends TestCase implements I_Callback
{
   private final static String ME = "TestPersistence2";
   private final Global glob;
   private final LogChannel log;

   private final String senderName = "Gesa";
   private final String senderPasswd = "secret";

   private String publishOid = "HelloDurable";
   private XmlBlasterConnection senderConnection = null;
   private String senderContent = "Some durable content";

   private int numReceived = 0;

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7604;

   /**
    * Constructs the TestPersistence2 object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPersistence2(Global glob, String testName)
   {
      super(testName);
      this.glob = glob;
      this.log = this.glob.getLog("test");
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);

      doLogin();
   }

   private void doLogin() {
      try {
         senderConnection = new XmlBlasterConnection(Util.getOtherServerPorts(serverPort)); // Find orb
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login( senderName, senderPasswd, qos, this);
      }
      catch (XmlBlasterException e) {
          log.warn(ME, "setUp() - login failed");
      }
      catch (Exception e) {
          log.error(ME, e.toString());
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
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         if (arr.length != 1) log.error(ME, "Erased " + arr.length + " messages:");
      } catch(XmlBlasterException e) { log.error(ME, "XmlBlasterException: " + e.getMessage()); }
      checkContent(false);

      senderConnection.disconnect(null);

      try { Thread.currentThread().sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }


   /**
    * Publish a durable message.
    * <p />
    */
   public void sendDurable()
   {
      if (log.TRACE) log.trace(ME, "Testing a durable message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain' contentMimeExtended='2.0' domain='RUGBY'/>";

      String qos = "<qos>" +
                   "   <isDurable />" +
                   "</qos>";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         String returnedOid = senderConnection.publish(msgUnit).getKeyOid();
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      waitOnUpdate(1000L, 0);
      assertEquals("numReceived after sending", 0, numReceived); // no message arrived?
      numReceived = 0;
   }


   /**
    * TEST: Publish a durable message.
    * <p />
    */
   public void testDurable()
   {
      sendDurable();
      checkContent(true);
      
      senderContent = senderContent + " again";
      sendDurable();
      RestartTestServer();

      doLogin();

      try {
         senderConnection.subscribe("<key oid='" + publishOid + "'/>", "<qos/>");
         log.info(ME, "Subscribe done");
      } catch(XmlBlasterException e) {
         log.error(ME, "subscribe() XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }

      waitOnUpdate(2000L, 1);
      assertEquals("numReceived after restart", 1, numReceived);
      numReceived = 0;

      checkContent(true);
   }

   /**
    * a dirty hack to restart the Test Server between send and checkcontent.
    * - disconnect(null)
    * - stopXmlBlaster()
    * - delay()
    * - startXmlBlaster()
    * - delay()
    * - login()
    * <p />
    */
   public void RestartTestServer() {
      long    delay4Server = 4000L ;
      log.info( ME, "Restarting Test Server" );

      try {
         senderConnection.disconnect(null);
         EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
         serverThread = null ;
         Util.delay( delay4Server );    // Wait some time

         serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
         Util.delay( delay4Server );    // Wait some time
         ConnectQos conectqos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login(senderName, senderPasswd, conectqos, this);

      }
      catch (XmlBlasterException e) {
         log.warn(ME, "setUp() - login failed");
      }
      catch (Exception e) {
         log.error(ME, e.toString());
         e.printStackTrace();
      }
   }

   /**
    * If the FileDriver is used, check if the correct content is written.
    */
   void checkContent(boolean checkContent)
   {
      String driverType = glob.getProperty().get("Persistence.Driver.Type", (String)null);
      if (driverType == null || !driverType.equals("filestore")) {
         log.info(ME, "Sorry, can't check persistence store, only checks for FileDriver is implemented");
         return;
      }

      String path = glob.getProperty().get("Persistence.Path", (String)null);
      if (path == null) {
         log.info(ME, "Sorry, xmlBlaster is running memory based only, no checks possible");
         return;
      }

      if (checkContent) {

         log.info(ME, "Checking content of message " + publishOid);

         try {
            String persistenceContent = FileUtil.readAsciiFile(path, publishOid);
            assertEquals("Written content is corrupted", senderContent, persistenceContent);
         }
         catch (Exception e) {
            assertTrue("Couldn't read file " + FileUtil.concatPath(path, publishOid), false);
         }
      }
      else { // Check if erased
         java.io.File f = new java.io.File(path, publishOid);
         if (f.exists())
            assertTrue("File " + FileUtil.concatPath(path, publishOid) + " is not erased properly", false);
      }
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      log.info(ME, "Receiving update of a message, checking ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Wrong mime of message returned", "text/plain", updateKey.getContentMime());
      assertEquals("Wrong extended mime of message returned", "2.0", updateKey.getContentMimeExtended());
      assertEquals("Wrong domain of message returned", "RUGBY", updateKey.getDomain());
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
            log.warn(ME, "Timeout of " + timeout + " occurred");
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
      suite.addTest(new TestPersistence2(new Global(), "testDurable"));
      return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.persistence.TestPersistence2
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistence2</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPersistence2 testSub = new TestPersistence2(glob, "TestPersistence2");
      testSub.setUp();
      testSub.testDurable();
      testSub.tearDown();
   }
}
