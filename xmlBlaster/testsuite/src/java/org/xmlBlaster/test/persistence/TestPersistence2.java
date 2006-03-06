/*------------------------------------------------------------------------------
Name:      TestPersistence2.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing persistent messages
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

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * This client tests the persistence driver, the $lt;persistent> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistence2
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.TestPersistence2
 * </pre>
 * @author mAd@ktaland.com
 */
public class TestPersistence2 extends TestCase
{
   private final static String ME = "TestPersistence2";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPersistence2.class.getName());

   private final String senderName = "Gesa";
   private final String senderPasswd = "secret";

   private String publishOid = "HelloPersistent";
   private I_XmlBlasterAccess senderConnection = null;
   private String senderContent = "Some persistent content";

   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 7604;

   private MsgInterceptor updateInterceptor;

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
      log.info("XmlBlaster is ready for testing on bootstrapPort " + serverPort);

      doLogin();
   }

   private void doLogin() {
      try {
         Global senderGlobal = Util.getOtherServerPorts(glob, serverPort);
         this.senderConnection = senderGlobal.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(senderGlobal, senderName, senderPasswd);

         this.updateInterceptor = new MsgInterceptor(senderGlobal, log, null);
         this.senderConnection.connect(qos, this.updateInterceptor);
      }
      catch (XmlBlasterException e) {
          log.warning("setUp() - login failed");
      }
      catch (Exception e) {
          log.severe(e.toString());
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
      try { Thread.sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = this.senderConnection.erase(xmlKey, qos);
         if (arr.length != 1) log.severe("Erased " + arr.length + " messages:");
      } catch(XmlBlasterException e) { log.severe("XmlBlasterException: " + e.getMessage()); }
      //checkContent(false);

      this.senderConnection.disconnect(null);
      this.senderConnection = null;

      try { Thread.sleep(500L); } catch( InterruptedException i) {}    // Wait some time
      EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
      this.serverThread = null;
      // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }


   /**
    * Publish a persistent message.
    * <p />
    */
   public void sendPersistent()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Testing a persistent message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain' contentMimeExtended='2.0' domain='RUGBY'/>";

      String qos = "<qos>" +
                   "   <persistent />" +
                   "</qos>";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         String returnedOid = this.senderConnection.publish(msgUnit).getKeyOid();
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         log.info("Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      assertEquals("numReceived after sending", 0, this.updateInterceptor.waitOnUpdate(1000L, publishOid, Constants.STATE_OK));
      assertEquals("", 0, this.updateInterceptor.count());
   }


   /**
    * TEST: Publish a persistent message.
    * <p />
    */
   public void testPersistent()
   {
      sendPersistent();
      checkContent(true);
      
      senderContent = senderContent + " again";
      sendPersistent();
      RestartTestServer();

      doLogin();

      try {
         this.senderConnection.subscribe("<key oid='" + publishOid + "'/>", "<qos/>");
         log.info("Subscribe done");
      } catch(XmlBlasterException e) {
         log.severe("subscribe() XmlBlasterException: " + e.getMessage());
         fail("subscribe - XmlBlasterException: " + e.getMessage());
      }

      assertEquals("", 1, this.updateInterceptor.waitOnUpdate(2000L, publishOid, Constants.STATE_OK));
      //this.updateInterceptor.compareToReceived(sentArr, null);
      //this.updateInterceptor.compareToReceived(sentQos);

      Msg msg = this.updateInterceptor.getMsgs()[0];

      assertEquals("Wrong sender", senderName, msg.getUpdateQos().getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, msg.getUpdateKey().getOid());
      assertEquals("Wrong mime of message returned", "text/plain", msg.getUpdateKey().getContentMime());
      assertEquals("Wrong extended mime of message returned", "2.0", msg.getUpdateKey().getContentMimeExtended());
      assertEquals("Wrong domain of message returned", "RUGBY", msg.getUpdateKey().getDomain());
      assertEquals("Message content is corrupted", new String(senderContent), msg.getContentStr());

      this.updateInterceptor.clear();
      //checkContent(true);
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
      log.info("Restarting Test Server" );

      try {
         this.senderConnection.disconnect(null);
         EmbeddedXmlBlaster.stopXmlBlaster(serverThread);
         serverThread = null ;
         Util.delay( delay4Server );    // Wait some time

         serverThread = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
         Util.delay( delay4Server );    // Wait some time

         Global globSender = Util.getOtherServerPorts(glob, serverPort);
         this.senderConnection = globSender.getXmlBlasterAccess(); // Find orb
         ConnectQos qos = new ConnectQos(globSender, senderName, senderPasswd);
         this.updateInterceptor = new MsgInterceptor(globSender, log, null);
         this.senderConnection.connect(qos, this.updateInterceptor);
      }
      catch (XmlBlasterException e) {
         log.warning("setUp() - login failed");
      }
      catch (Exception e) {
         log.severe(e.toString());
         e.printStackTrace();
      }
   }

   /**
    * If the FileDriver is used, check if the correct content is written.
    * @deprecated FileDriver is deprecated
    */
   void checkContent(boolean checkContent)
   {
      String driverType = glob.getProperty().get("Persistence.Driver.Type", (String)null);
      if (driverType == null || !driverType.equals("filestore")) {
         log.info("Sorry, can't check persistence store, only checks for FileDriver is implemented");
         return;
      }

      String path = glob.getProperty().get("Persistence.Path", (String)null);
      if (path == null) {
         log.info("Sorry, xmlBlaster is running memory based only, no checks possible");
         return;
      }

      if (checkContent) {

         log.info("Checking content of message " + publishOid);

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
    * Method is used by TestRunner to load these tests
    */
   public static Test suite()
   {
      TestSuite suite= new TestSuite();
      suite.addTest(new TestPersistence2(new Global(), "testPersistent"));
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
      testSub.testPersistent();
      testSub.tearDown();
   }
}
