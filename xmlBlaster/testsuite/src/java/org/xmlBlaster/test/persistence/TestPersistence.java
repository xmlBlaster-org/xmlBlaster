/*------------------------------------------------------------------------------
Name:      TestPersistence.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing persistent messages
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.persistence;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.jutils.io.FileUtil;

import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.property.Args;

import junit.framework.*;


/**
 * This client tests the persistence driver, the $lt;persistent/> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistence
 *
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.persistence.TestPersistence
 * </pre>
 */
public class TestPersistence extends TestCase implements I_Callback
{
   private final static String ME = "TestPersistence";
   private Global glob = null;
   private static Logger log = Logger.getLogger(TestPersistence.class.getName());

   private final String senderName = "Gesa";
   private String publishOid = "HelloPersistent";
   private I_XmlBlasterAccess senderConnection = null;
   private String senderContent = "Some persistent content";

   private int numReceived = 0;


   /**
    * Constructs the TestPersistence object.
    * <p />
    * @param testName  The name used in the test suite
    * @param loginName The name to login to the xmlBlaster
    */
   public TestPersistence(String testName)
   {
       super(testName);
   }


   /**
    * Sets up the fixture.
    * <p />
    * Creates a CORBA connection and does a login.<br />
    * - One connection for the sender client<br />
    */
   protected void setUp()
   {
      if (this.glob == null) this.glob = new Global();

      try {
         String passwd = "secret";
         senderConnection = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, senderName, passwd); // == "<qos></qos>";
         senderConnection.connect(qos, this);
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

      String xmlKey = "<key oid='" + publishOid + "' queryType='EXACT'>\n</key>";
      String qos = "<qos></qos>";
      try {
         EraseReturnQos[] arr = senderConnection.erase(xmlKey, qos);
         assertEquals("Erase", 1, arr.length);
      } catch(XmlBlasterException e) { fail("Erase XmlBlasterException: " + e.getMessage()); }
      checkContent(false);

      senderConnection.disconnect(null);
   }


   /**
    * Publish a persistent message.
    * <p />
    */
   public void sendPersistent()
   {
      if (log.isLoggable(Level.FINE)) log.fine("Testing a persistent message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain' contentMimeExtended='2.0' domain='RUGBY'>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <persistent />" +
                   "</qos>";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         String returnedOid = senderConnection.publish(msgUnit).getKeyOid();
         assertEquals("Retunred oid is invalid", publishOid, returnedOid);
         log.info("Sending of '" + senderContent + "' done, returned oid=" + publishOid);
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }

      waitOnUpdate(1000L, 0);
      assertEquals("numReceived after sending", 0, numReceived); // no message arrived?
      numReceived = 0;
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
      checkContent(true);
   }


   /**
    * If the FileDriver is used, check if the correct content is written.
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
      log.info("Receiving update of a message, checking ...");

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
       suite.addTest(new TestPersistence("testPersistent"));
       return suite;
   }


   /**
    * Invoke: java org.xmlBlaster.test.persistence.TestPersistence
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistence</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }
      TestPersistence testSub = new TestPersistence("TestPersistence");
      testSub.setUp();
      testSub.testPersistent();
      testSub.tearDown();
   }
}
