/*------------------------------------------------------------------------------
Name:      TestPersistenceXMLDB.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Testing durable messages using dbXMLDriver Persistence
Version:   $Id: TestPersistenceXMLDB.java,v 1.2 2002/09/13 23:18:28 ruff Exp $
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.persistence;

import org.jutils.log.LogChannel;

import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.util.ConnectQos;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.client.UpdateKey;
import org.xmlBlaster.client.UpdateQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.protocol.corba.serverIdl.Server;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.helper.MessageUnit;

import org.xmlBlaster.util.EmbeddedXmlBlaster;

import org.xmlBlaster.test.Util;
import junit.framework.*;


/**
 * This client tests the persistence driver, the $lt;isDurable> flag.
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
   private final LogChannel log;

   private final String senderName = "Benedikt";
   private final String senderPasswd = "secret";
   private String publishOid = "amIdurable";
   private String subscribeString = "subscribeMe";
   private XmlBlasterConnection senderConnection = null;
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
      this.log = this.glob.getLog("test");
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
                         "-hostname", "localhost",
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
      log.info(ME, "XmlBlaster is ready for testing on port " + serverPort);
      return st;
   } // end of startServer


   /**
    * Stops a xmlBlaster serverthread.
    * @param st keeps the server thread
    */
   protected void stopServer(EmbeddedXmlBlaster st) {
         EmbeddedXmlBlaster.stopXmlBlaster(st);
         log.info( ME, "Xmlblaster stopped");
         st = null;
   } // end of stopServer


   /**
    * Connects a client at the server.
    * @param name               The loginname.
    * @param passwd     The loginpassword.
    * @return The sender connection.
    */
   protected XmlBlasterConnection connectClient(String name, String passwd) {
      log.info(ME, "connect to client: name='" + name + "' passwd='" + passwd + "'");
         XmlBlasterConnection sc = null;
      try {
         sc = new XmlBlasterConnection(glob);
         ConnectQos qos = new ConnectQos(glob, name, passwd); // == "<qos></qos>";
         sc.connect(qos, this);
         log.info( ME, name + " connected" );
      } catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
      return sc;
   } // end of connectClient


   /**
    * Disconnects a client from the server.
    * @param sc A connection of a client to xmlBlaster.
    */
   protected void disconnectClient(XmlBlasterConnection sc) {
      try {
         sc.disconnect(null);
         sc = null;
      } catch (Exception e) {
          log.error(ME, e.toString());
          e.printStackTrace();
      }
   } // end of disconnectClient


   /**
    * Sets up the fixture.
    * Sends a durable message to be stored in persistence driver.
    * <p />
    * Starts the server, creates a connection and does a login.<br />
    * Sends a durable message and disconnects from server.<br />
    * Shuts the server down.<br />
    */
   protected void setUp() {
      serverThread1 = startServer();
      senderConnection = connectClient(senderName, senderPasswd);

      sendDurable(senderConnection);

      disconnectClient(senderConnection);
      stopServer(serverThread1);

      serverThread1 = null;
      senderConnection = null;
   } // end of setUp


   /**
    * Publish a durable message.
    * @param sc A connection of a client to xmlBlaster.
    */
   public void sendDurable(XmlBlasterConnection sc) {
        if (log.CALL) log.call(ME, "sendDurable");
      if (log.TRACE) log.trace(ME, "Testing a durable message ...");

      String xmlKey = "<key oid='" + publishOid + "' contentMime='text/plain'>\n" +
                      "   <" + subscribeString + "/>\n" +
                      "</key>";

      String qos = "<qos>" +
                   "   <isDurable />" +
                   "</qos>";

      MessageUnit msgUnit = new MessageUnit(xmlKey, senderContent.getBytes(), qos);
      try {
         String returnedOid = sc.publish(msgUnit).getOid();
         assertEquals("Returned oid is invalid", publishOid, returnedOid);
         log.info(ME, "Sending of '" + senderContent + "' done, returned oid '" + publishOid + "'");
      } catch(XmlBlasterException e) {
         log.error(ME, "publish() XmlBlasterException: " + e.reason);
         assertTrue("publish - XmlBlasterException: " + e.reason, false);
      }
   } // end of sendDurable


   /**
    * Subscribes to publishOid at the given connection.
    * @param sc A connection of a client to xmlBlaster.
    */
   protected void subscribe(XmlBlasterConnection sc) {
        if (log.CALL) log.call(ME, "subscribe");

      String xmlKeySub = "<key oid='' queryType='XPATH'>\n" + "/xmlBlaster/key/" + subscribeString + " </key>";
      log.info(ME, "Subscribe to '" + xmlKeySub + "' ...");

      try {
         sc.subscribe(xmlKeySub, "<qos></qos>");
      } catch(XmlBlasterException e2) {
         log.warn(ME, "XmlBlasterException: " + e2.reason);
      }
      //log.trace(ME, "Subscribed to '" + xmlKeySub + "' ...");
   } // end of subscribe


   /**
    * TEST: Subscribes to the message with the given key per XPATH.
    * <p />
    * Starts the server, creates a connection and does a login.<br />
    * Subscribes to a durable message waits a while and disconnects from server.<br />
    * Shuts the server down.<br />
    */
   public void testDurable() {

      serverThread2 = startServer();
      senderConnection = connectClient(senderName, senderPasswd);

      subscribe(senderConnection);
      try { Thread.currentThread().sleep(3000L); } catch( InterruptedException i) {}   // Wait 200 milli seconds, until all updates are processed ...

      disconnectClient(senderConnection);
      stopServer(serverThread2);

      serverThread2 = null;
      senderConnection = null;
   } // end of testDurable


   /**
    * This is the callback method invoked from xmlBlaster
    * delivering us a new asynchronous message.
    * @see org.xmlBlaster.client.I_Callback#update(String, UpdateKey, byte[], UpdateQos)
    */
   public String update(String cbSessionId, UpdateKey updateKey, byte[] content, UpdateQos updateQos) {
      //log.info(ME, "Receiving update of a message ...");
      if (log.CALL) log.call(ME, "Receiving update of a message ...");

      numReceived += 1;

      log.plain("UpdateKey", updateKey.toXml());
      log.plain("content", (new String(content)).toString());
      log.plain("UpdateQos", updateQos.toXml());

      assertEquals("Wrong sender", senderName, updateQos.getSender());
      assertEquals("Wrong oid of message returned", publishOid, updateKey.getUniqueKey());
      assertEquals("Message content is corrupted", new String(senderContent), new String(content));
      return "";
   }


   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       suite.addTest(new TestPersistenceXMLDB(new Global(), "testDurable"));
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
      testSub.testDurable();
   }
}
