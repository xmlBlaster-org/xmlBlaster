/*------------------------------------------------------------------------------
Name:      TestPersistence2.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing durable messages
Version:   ? cvs will update it ?
------------------------------------------------------------------------------*/

// 24/09/1 19:27 mAd@ktaland.com
// 
// to test this code Alown : 
//  java \
//      -cp lib/javarb.jar:lib/xmlBlaster.jar:lib/junit.jar:lib/testsuite.jar \
//      junit.textui.TestRunner \
//      testsuite.org.xmlBlaster.TestPersistence2
//

package testsuite.org.xmlBlaster;

import org.jutils.io.FileUtil;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.xmlBlaster.util.ServerThread;

import junit.framework.*;


/**
 * This client tests the persistence driver, the $lt;isDurable> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner testsuite.org.xmlBlaster.TestPersistence2
 *
 *    java junit.ui.TestRunner testsuite.org.xmlBlaster.TestPersistence2
 * </pre>
 */
public class TestPersistence2 extends TestCase implements I_Callback
{
   private final static String ME = "TestPersistence2";
   private final Global glob;

   private final String senderName = "Gesa";
   private final String senderPasswd = "secret";

   private String publishOid = "HelloDurable";
   private XmlBlasterConnection senderConnection = null;
   private String senderContent = "Some durable content";

   private int numReceived = 0;

   private ServerThread serverThread;
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
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      serverThread = ServerThread.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      try { Thread.currentThread().sleep(4000L); } catch( InterruptedException i) {}
      Log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);

      try {
         senderConnection = new XmlBlasterConnection(Util.getOtherServerPorts(serverPort)); // Find orb
         ConnectQos qos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login( senderName, senderPasswd, qos, this);
      }
      catch (XmlBlasterException e) {
          Log.warn(ME, "setUp() - login failed");
      }
      catch (Exception e) {
          Log.error(ME, e.toString());
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
      Util.delay(200L);   // Wait 200 milli seconds, until all updates are processed ...

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      String[] strArr = new String[0];
      try {
         strArr = senderConnection.erase(xmlKey, qos);
      } catch(XmlBlasterException e) { Log.error(ME, "XmlBlasterException: " + e.reason); }
      if (strArr.length != 1) Log.error(ME, "Erased " + strArr.length + " messages:");
      checkContent(false);

      senderConnection.logout();

      Util.delay(500L);    // Wait some time
      ServerThread.stopXmlBlaster(serverThread);
      // reset to default server port (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }


   /**
    * Publish a durable message.
    * <p />
    */
   public void sendDurable()
   {
      if (Log.TRACE) Log.trace(ME, "Testing a durable message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <isDurable />" +
                   "</qos>";

      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      try {
         String returnedOid = senderConnection.publish(msgUnit);
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         Log.info(ME, "Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         Log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
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
      checkContent(true);
   }

   /**
    * a dirty hack to restart the Test Server between send and checkcontent.
    * - logout()
    * - stopXmlBlaster()
    * - delay()
    * - startXmlBlaster()
    * - delay()
    * - login()
    * <p />
    */
   public void RestartTestServer() {
      long    delay4Server = 4000L ;
      Log.info( ME, "Restarting Test Server" );

      try {
         senderConnection.logout();
         ServerThread.stopXmlBlaster(serverThread);
         serverThread = null ;
         Util.delay( delay4Server );    // Wait some time

         serverThread = ServerThread.startXmlBlaster(Util.getOtherServerPorts(serverPort));
         Util.delay( delay4Server );    // Wait some time
         ConnectQos conectqos = new ConnectQos(glob); // == "<qos></qos>";
         senderConnection.login(senderName, senderPasswd, conectqos, this);

      }
      catch (XmlBlasterException e) {
         Log.warn(ME, "setUp() - login failed");
      }
      catch (Exception e) {
         Log.error(ME, e.toString());
         e.printStackTrace();
      }
   }

   /**
    * If the FileDriver is used, check if the correct content is written.
    */
   void checkContent(boolean checkContent)
   {
      String driverClass = glob.getProperty().get("Persistence.Driver", (String)null);
      if (driverClass == null || !driverClass.equals("org.xmlBlaster.engine.persistence.filestore.FileDriver")) {
         Log.info(ME, "Sorry, can't check persistence store, only checks for FileDriver is implemented");
         return;
      }

      String path = glob.getProperty().get("Persistence.Path", (String)null);
      if (path == null) {
         Log.info(ME, "Sorry, xmlBlaster is running memory based only, no checks possible");
         return;
      }

      if (checkContent) {

         Log.info(ME, "Checking content of message " + publishOid);

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
      if (Log.CALL) Log.call(ME, "Receiving update of a message ...");

      numReceived += 1;

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
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
            Log.warn(ME, "Timeout of " + timeout + " occurred");
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
    * Invoke: java testsuite.org.xmlBlaster.TestPersistence2
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestPersistence2</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         Log.panic(ME, "Init failed");
      }
      TestPersistence2 testSub = new TestPersistence2(glob, "TestPersistence2");
      testSub.setUp();
      testSub.testDurable();
      testSub.tearDown();
      Log.exit(TestPersistence2.ME, "Good bye");
   }
}
