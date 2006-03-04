/*------------------------------------------------------------------------------
Name:      TestPtD.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing PtP (point to point) messages
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.jutils.init.Args;
import org.jutils.time.StopWatch;

import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.util.MsgUnit;

import junit.framework.*;


/**
 * This client tests the PtP (or PtD = point to destination) style.
 * <p>
 * Note that the three clients (client logins) are simulated in this class.<br />
 * Tests performed:<br />
 * <ul>
 *    <li>Manuel is the 'sender' and Ulrike the 'receiver' of a love letter</li>
 *    <li>Manuel sends a message to two destinations</li>
 * </ul>
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtD
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.qos.TestPtD
 * </pre>
 */
public class TestPtD extends TestCase implements I_Callback
{
   private final static String ME = "TestPtD";
   private final Global glob;
   private static Logger log = Logger.getLogger(TestPtD.class.getName());

   private final String senderName = "Manuel";
   private String publishOid = "";
   private I_XmlBlasterAccess senderConnection = null;
   private String senderContent;

   private final String receiverName = "Ulrike";
   private I_XmlBlasterAccess receiverConnection = null;

   private final String receiver2Name = "KGB";
   private I_XmlBlasterAccess receiver2Connection = null;

   private int numReceived = 0;


   /**
    * Constructs the TestPtD object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPtD(Global glob, String testName)
   {
      super(testName);
      this.glob = glob;

   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    * - One connection for the receiver client
    * - One connection for the receiver2 client
    */
   protected void setUp()
   {
      try {
         String passwd = "secret";

         Global receiverGlob = glob.getClone(null);
         receiverConnection = receiverGlob.getXmlBlasterAccess();
         receiverConnection.connect(new ConnectQos(receiverGlob, receiverName, passwd), this);

         Global receiver2Glob = glob.getClone(null);
         receiver2Connection = receiver2Glob.getXmlBlasterAccess();
         receiver2Connection.connect(new ConnectQos(receiver2Glob, receiver2Name, passwd), this);

         Global receiver3Glob = glob.getClone(null);
         senderConnection = receiver3Glob.getXmlBlasterAccess();
         senderConnection.connect(new ConnectQos(receiver3Glob, senderName, passwd), this);
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
      try { Thread.currentThread().sleep(200L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...
      receiverConnection.disconnect(null);
      receiver2Connection.disconnect(null);
      senderConnection.disconnect(null);
   }


   /**
    * TEST: Send a message to one destination
    * <p />
    * The returned subscribeOid is checked
    */
   public void testPtOneDestination()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Testing point to one destination ...");

      // Construct a love message and send it to Ulrike
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "</qos>";

      senderContent = "Hi " + receiverName + ", i love you, " + senderName;
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         publishOid = senderConnection.publish(msgUnit).getKeyOid();
         log.info("Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      waitOnUpdate(5000L, 1);
      assertEquals("numReceived after sending", 1, numReceived); // message arrived?
      numReceived = 0;
   }


   /**
    * TEST: Send a message to two destinations
    * <p />
    */
   public void testPtManyDestinations()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Testing point to many destinations ...");

      // Construct a love message and send it to Ulrike
      String xmlKey = "<key oid='' contentMime='text/plain'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <destination queryType='EXACT'>" +
                           receiverName +
                   "   </destination>" +
                   "   <destination queryType='EXACT'>" +
                           receiver2Name +
                   "   </destination>" +
                   "</qos>";

      senderContent = "Hi " + receiver2Name + ", i know you are listening, " + senderName;
      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         publishOid = senderConnection.publish(msgUnit).getKeyOid();
         log.info("Sending done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      waitOnUpdate(5000L, 2);
      assertEquals("numReceived after sending", 2, numReceived); // message arrived at receiver and receiver2 ?
      numReceived = 0;
   }

   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message. 
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos)
   {
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");

      numReceived += 1;

      // Wait that publish() returns and set 'publishOid' properly
      try { Thread.currentThread().sleep(200); } catch( InterruptedException i) {}

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
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
            log.warning("Timeout of " + timeout + " occurred");
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
       Global glob = new Global();
       suite.addTest(new TestPtD(glob, "testPtOneDestination"));
       suite.addTest(new TestPtD(glob, "testPtManyDestinations"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.qos.TestPtD
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.qos.TestPtD</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPtD testSub = new TestPtD(glob, "TestPtD");
      testSub.setUp();
      testSub.testPtOneDestination();
      testSub.tearDown();
   }
}
