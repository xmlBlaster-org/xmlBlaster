/*------------------------------------------------------------------------------
Name:      TestErase.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Demo code for a client using xmlBlaster
------------------------------------------------------------------------------*/
package testsuite.org.xmlBlaster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;

import junit.framework.*;


/**
 * This client tests if it receives message erase events. 
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading testsuite.org.xmlBlaster.TestErase
 *    java junit.ui.TestRunner -noloading testsuite.org.xmlBlaster.TestErase
 * </pre>
 * @see <a href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/interface.update.html" target="others">update requirement</a>
 */
public class TestErase extends TestCase implements I_Callback
{
   private static String ME = "TestErase";
   private final Global glob;
   private final LogChannel log;

   private String subscribeId;
   private String oidExact = "HelloMessage";
   private String publishOid = null;
   private XmlBlasterConnection con;
   private String senderContent;

   private boolean expectingErase = false;

   private int numReceived = 0;         // error checking
   private final String contentMime = "text/xml";

   /**
    * Constructs the TestErase object.
    * <p />
    * @param testName  The name used in the test suite
    */
   public TestErase(Global glob, String testName) {
       super(testName);
       this.glob = glob;
       this.log = glob.getLog(null);
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      numReceived = 0;
      expectingErase = false;
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      if (con != null) {
         if (publishOid != null) {
            String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
            try {
               EraseRetQos[] arr = con.erase(xmlKey, "<qos/>");
               // can be erased already in our tests
            } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }
         }

         con.disconnect(null);
         con = null;
      }
   }

   /**
    * Subscribe three times to same message. 
    * <p />
    * The returned subscribeId is checked
    */
   private void subscribe() {
      if (log.TRACE) log.trace(ME, "Subscribing ...");

      String xmlKey = "<key oid='" + oidExact + "' queryType='EXACT'/>";
      String qos = "<qos/>";
      numReceived = 0;
      subscribeId = null;
      try {
         subscribeId = con.subscribe(xmlKey, qos).getSubscriptionId();
         assertTrue("returned null subscribeId", subscribeId != null);
         assertTrue("returned subscribeId is empty", 0 != subscribeId.length());
         log.info(ME, "Success: Subscribe on " + subscribeId + " done");
      }
      catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("subscribe - XmlBlasterException: " + e.reason, false);
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    * The returned publishOid is checked
    */
   private void publish() {
      if (log.TRACE) log.trace(ME, "Publishing a message ...");

      numReceived = 0;
      String xmlKey = "<key oid='" + oidExact + "' contentMime='" + contentMime + "'/>";
      senderContent = "Yeahh, i'm the new content";
      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), "<qos/>");
      try {
         publishOid = con.publish(msgUnit).getOid();
         log.info(ME, "Success: Publishing done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }

      assertTrue("returned publishOid == null", publishOid != null);
      assertTrue("returned publishOid", 0 != publishOid.length());
      assertEquals("returned publishOid is wrong", oidExact, publishOid);
   }

   /**
    * unSubscribe three times to same message. 
    */
   private void unSubscribe() {
      if (log.TRACE) log.trace(ME, "unSubscribing ...");

      String qos = "<qos/>";
      numReceived = 0;
      try {
         con.unSubscribe("<key oid='" + subscribeId + "'/>", qos);
         log.info(ME, "Success: unSubscribe on " + subscribeId + " done");
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.reason);
         assertTrue("unSubscribe - XmlBlasterException: " + e.reason, false);
      }
   }

   private void erase() {
      if (publishOid != null) {
         String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'/>";
         try {
            EraseRetQos[] arr = con.erase(xmlKey, "<qos/>");
            assertEquals("Erase", 1, arr.length);
         } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.reason); }
      }
   }

   private void connect() {
      try {
         con = new XmlBlasterConnection(glob); // Find orb
         ConnectQos qos = new ConnectQos(glob);
         con.connect(qos, this);
      }
      catch (Exception e) {
          log.error(ME, "Login failed: " + e.toString());
          e.printStackTrace();
          assertTrue("Login failed: " + e.toString(), false);
      }
   }

   /**
    * TEST: Subscribe to a message, publish it, erase it and check if we are notified
    * about the erased message
    * <br />
    */
   public void testEraseEvent() {
      log.info(ME, "testEraseEvent ...");
      numReceived = 0;
      
      connect();

      subscribe();
      Util.delay(1000L);   // Wait some time for callback to arrive ...
      assertEquals("numReceived after subscribe", 0, numReceived);  // there should be no Callback

      numReceived = 0;
      publish();
      try { Thread.currentThread().sleep(2000L); } catch( InterruptedException i) {}
      assertEquals("numReceived after publishing", 1, numReceived); // only one message arrived?

      numReceived = 0;
      expectingErase = true;
      erase();
      Util.delay(1000L);   // Wait some time for callback to arrive ...
      assertEquals("numReceived after erase", 1, numReceived);  // erase event arrived

      log.info(ME, "testEraseEvent SUCCESS");
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      if (log.CALL) log.call(ME, "Receiving update of a message ...");

      numReceived += 1;

      if (expectingErase) {
         assertEquals("Wrong update state", Constants.STATE_ERASED, updateQos.getState());
         assertTrue("Wrong update state", updateQos.isErased());
      }
      else {
         assertEquals("Wrong update state", Constants.STATE_OK, updateQos.getState());
         assertTrue("Wrong update state", updateQos.isOk());
      }

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      assertEquals("Message contentMime is corrupted", contentMime, updateKey.getContentMime());

      return "";
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestErase(new Global(), "testEraseEvent"));
       return suite;
   }

   /**
    * Invoke: java testsuite.org.xmlBlaster.TestErase
    * <p />
    * Note you need 'java' instead of 'java' to start the TestRunner, otherwise the JDK ORB is used
    * instead of the JacORB ORB, which won't work.
    * <br />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner testsuite.org.xmlBlaster.TestErase</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println("Init failed");
         System.exit(1);
      }
      TestErase testSub = new TestErase(glob, "TestErase");
      testSub.setUp();
      testSub.testEraseEvent();
      testSub.tearDown();

   }
}

