/*------------------------------------------------------------------------------
Name:      TestPersistenceXMLDB.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing persistent messages using dbXMLDriver Persistence
Version:   $Id$
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.persistence;

import java.util.logging.Level;
import java.util.logging.Logger;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.XmlBlasterException;


/**
 * This client tests the persistence driver, the $lt;persistent> flag.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistenceXMLDB
 *
 *    java junit.swingui.TestRunner org.xmlBlaster.test.persistence.TestPersistenceXMLDB
 * </pre>
 */
public class TestPersistenceXMLDB extends TestCase implements I_Callback {
   private final static String ME = "TestPersistenceXMLDB";
   private Global glob = null;
   private static Logger log = Logger.getLogger(TestPersistenceXMLDB.class.getName());

   private final String senderName = "Benedikt";
   private final String senderPasswd = "secret";
   private String publishOid = "amIpersistent";
   private String subscribeString = "subscribeMe";
   private I_XmlBlasterAccess senderConnection = null;
   private String senderContent = "Smoked < Ham"; // not well formed XML on purpose
   // private String sendetContent = "<description>Smoked Ham</description>";

   private int numReceived = 0;

   private EmbeddedXmlBlaster st;
   private EmbeddedXmlBlaster serverThread1;
   private EmbeddedXmlBlaster serverThread2;
   private int serverPort = 7604;

   /**
    * Constructs the TestPersistenceXMLDB object.
    * <p />
    * @param glob                Keeps global args and parameters.
    * @param testName The name used in the test suite.
    */
   public TestPersistenceXMLDB(Global glob, String testName)
   {
      super(testName);
      this.glob = glob;

   }

   /**
    * Starts a xmlBlaster serverthread.
    * @return the server thread.
    */
   protected EmbeddedXmlBlaster startServer() {
      EmbeddedXmlBlaster st;
      glob.init(Util.getOtherServerPorts(serverPort));
      /*
      How to get the PersistenceDriver switched on fromrunning xmlBlaster embedded?
      Example:

       String[] args = {
                         "-isRelease", "false",
                         "-logConsole", "true",
                         "-bootstrapHostname", "localhost",
                         "-socket.subscriptions", "ATD,VDM",
                         "-mom.username", "shInt",
                         "-mom.password", "xx",
                         "-appServ.username", "momusr",
                         "-appServ.password", "xx"
                       };
       glob.init(args);
       serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
      */

      st = EmbeddedXmlBlaster.startXmlBlaster(Util.getOtherServerPorts(serverPort));
      log.info("XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      return st;
   } // end of startServer


   /**
    * Stops a xmlBlaster serverthread.
    * @param st keeps the server thread
    */
   protected void stopServer(EmbeddedXmlBlaster st) {
         EmbeddedXmlBlaster.stopXmlBlaster(st);
         log.info("Xmlblaster stopped");
         st = null;
   } // end of stopServer


   /**
    * Connects a client at the server.
    * @param name               The loginname.
    * @param passwd     The loginpassword.
    * @return The sender connection.
    */
   protected I_XmlBlasterAccess connectClient(String name, String passwd) {
      log.info("connect to client: name='" + name + "' passwd='" + passwd + "'");
         I_XmlBlasterAccess sc = null;
      try {
         sc = glob.getXmlBlasterAccess();
         ConnectQos qos = new ConnectQos(glob, name, passwd); // == "<qos></qos>";
         sc.connect(qos, this);
         log.info(name + " connected" );
      } catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
      return sc;
   } // end of connectClient


   /**
    * Disconnects a client from the server.
    * @param sc A connection of a client to xmlBlaster.
    */
   protected void disconnectClient(I_XmlBlasterAccess sc) {
      try {
         sc.disconnect(null);
         sc = null;
      } catch (Exception e) {
          log.severe(e.toString());
          e.printStackTrace();
      }
   } // end of disconnectClient


   /**
    * Sets up the fixture.
    * Sends a persistent message to be stored in persistence driver.
    * <p />
    * Starts the server, creates a connection and does a login.<br />
    * Sends a persistent message and disconnects from server.<br />
    * Shuts the server down.<br />
    */
   protected void setUp() {
      serverThread1 = startServer();
      senderConnection = connectClient(senderName, senderPasswd);

      sendPersistent(senderConnection);

      disconnectClient(senderConnection);
      stopServer(serverThread1);

      serverThread1 = null;
      senderConnection = null;
   } // end of setUp

   protected void tearDown()
   {
      // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
      Util.resetPorts();
   }

   /**
    * Publish a persistent message.
    * @param sc A connection of a client to xmlBlaster.
    */
   public void sendPersistent(I_XmlBlasterAccess sc) {
        if (log.isLoggable(Level.FINER)) log.finer("sendPersistent");
      if (log.isLoggable(Level.FINE)) log.fine("Testing a persistent message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "   <" + subscribeString + "/>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <persistent />" +
                   "</qos>";

      try {
         MsgUnit msgUnit = new MsgUnit(xmlKey, senderContent.getBytes(), qos);
         String returnedOid = sc.publish(msgUnit).getKeyOid();
         assertEquals("Returned oid is invalid", publishOid, returnedOid);
         log.info("Sending of '" + senderContent + "' done, returned oid '" + publishOid + "'");
      } catch(XmlBlasterException e) {
         log.severe("publish() XmlBlasterException: " + e.getMessage());
         assertTrue("publish - XmlBlasterException: " + e.getMessage(), false);
      }
   } // end of sendPersistent


   /**
    * Subscribes to publishOid at the given connection.
    * @param sc A connection of a client to xmlBlaster.
    */
   protected void subscribe(I_XmlBlasterAccess sc) {
        if (log.isLoggable(Level.FINER)) log.finer("subscribe");

      String xmlKeySub = "<key oid='' queryType='XPATH'>\n" + "/xmlBlaster/key/" + subscribeString + " </key>";
      log.info("Subscribe to '" + xmlKeySub + "' ...");

      try {
         sc.subscribe(xmlKeySub, "<qos></qos>");
      } catch(XmlBlasterException e2) {
         log.warning("XmlBlasterException: " + e2.getMessage());
      }
      //log.fine("Subscribed to '" + xmlKeySub + "' ...");
   } // end of subscribe


   /**
    * TEST: Subscribes to the message with the given key per XPATH.
    * <p />
    * Starts the server, creates a connection and does a login.<br />
    * Subscribes to a persistent message waits a while and disconnects from server.<br />
    * Shuts the server down.<br />
    */
   public void testPersistent() {

      serverThread2 = startServer();
      senderConnection = connectClient(senderName, senderPasswd);

      subscribe(senderConnection);
      try { Thread.sleep(2000L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      disconnectClient(senderConnection);
      stopServer(serverThread2);

      serverThread2 = null;
      senderConnection = null;
   } // end of testPersistent


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      //log.info("Receiving update of a message ...");
      if (log.isLoggable(Level.FINER)) log.finer("Receiving update of a message ...");

      numReceived += 1;

      System.out.println(updateKey.toXml());
      System.out.println((new String(content)).toString());
      System.out.println(updateQos.toXml());

      assertEquals("Wrong sender", senderName, updateQos.getSender().getLoginName());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getOid());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      return "";
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestPersistenceXMLDB(new Global(), "testPersistent"));
       return suite;
   }

   /**
    * Invoke: java org.xmlBlaster.test.persistence.TestPersistenceXMLDB
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.persistence.TestPersistenceXMLDB</pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();

      if (glob.init(args) != 0) {
         System.err.println(ME + ": Init failed");
         System.exit(1);
      }

      TestPersistenceXMLDB testSub = new TestPersistenceXMLDB(glob, "TestPersistenceXMLDB");
      testSub.setUp();
      testSub.testPersistent();
   }
}
