/*------------------------------------------------------------------------------
Name:      TestPtSession.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.qos;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.SessionName;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.client.qos.ConnectReturnQos;
import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.util.enum.PriorityEnum;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.PublishReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.qos.UpdateReturnQos;
import org.xmlBlaster.client.qos.SubscribeQos;
import org.xmlBlaster.client.qos.SubscribeReturnQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.qos.UpdateQos;
import org.xmlBlaster.client.key.UpdateKey;
import org.xmlBlaster.client.key.PublishKey;
import org.xmlBlaster.client.key.SubscribeKey;
import org.xmlBlaster.client.I_Callback;
import org.xmlBlaster.util.MsgUnit;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.address.Destination;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.Msg;
import org.xmlBlaster.test.MsgInterceptor;

import junit.framework.*;


/**
 * This client tests the
 * engine.qos.publish.destination.PtPa href="http://www.xmlblaster.org/xmlBlaster/doc/requirements/engine.qos.publish.destination.PtP.html">engine.qos.publish.destination.PtP requirement</a>
 * <p />
 * We login as a publisher for PtP messages and many receivers.
 * Depending on the destination name a message is sent to all sessions or
 * only to specified sessions of the same user.
 * <p />
 * We start our own xmlBlaster server in a thread.
 * This client may be invoked multiple time on the same xmlBlaster server,
 * as it cleans up everything after his tests are done.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *    java junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtSession
 *    java junit.swingui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtSession
 * </pre>
 */
public class TestPtSession extends TestCase
{
   private static String ME = "TestPtSession";
   private final Global glob;
   private final LogChannel log;

   class ConHolder {
      public XmlBlasterConnection con;
      public MsgInterceptor update;
      public ConnectReturnQos connectReturnQos;
   };

   private int numCons = 5;
   private ConHolder[] conHolderArr;

   private String name;
   private String passwd = "secret";
   private EmbeddedXmlBlaster serverThread;
   private int serverPort = 9560;
   private boolean startEmbedded = true;

   private final String msgOid = "ptpTestMessage";

   private int msgSequenceNumber = 0;

   /**
    * Constructs the TestPtSession object.
    * <p />
    * @param testName   The name used in the test suite
    * @param name       The name to login to the xmlBlaster
    */
   public TestPtSession(Global glob, String testName, String name) {
      super(testName);
      this.glob = glob;
      this.log = glob.getLog("test");
      this.name = name;
   }

   /**
    * Sets up the fixture.
    * <p />
    * We start an own xmlBlaster server in a separate thread.
    * <p />
    * Then we connect our 5 clients
    */
   protected void setUp() {  
      this.startEmbedded = glob.getProperty().get("startEmbedded", this.startEmbedded);

      if (this.startEmbedded) {
         glob.init(Util.getOtherServerPorts(serverPort));
         String[] args = {};
         glob.init(args);

         serverThread = EmbeddedXmlBlaster.startXmlBlaster(glob);
         log.info(ME, "XmlBlaster is ready for testing the session PtP messages");
      }
      else
         log.warn(ME, "You need to start an external xmlBlaster server for this test");

      this.conHolderArr = new ConHolder[numCons];

      for(int ii=0; ii<conHolderArr.length; ii++) {
         this.conHolderArr[ii] = new ConHolder();
         try {
            log.info(ME, "Connecting ...");
            this.conHolderArr[ii].con = new XmlBlasterConnection(glob);
            ConnectQos qos = new ConnectQos(glob, name, passwd);
            this.conHolderArr[ii].update = new MsgInterceptor(glob, log, null);
            this.conHolderArr[ii].connectReturnQos = this.conHolderArr[ii].con.connect(qos, this.conHolderArr[ii].update);
         }
         catch (Exception e) {
            Thread.currentThread().dumpStack();
            log.error(ME, "Can't connect to xmlBlaster: " + e.toString());
            fail("Can't connect to xmlBlaster: " + e.toString());
         }
      }

      for(int ii=0; ii<conHolderArr.length; ii++) {
         this.conHolderArr[ii].update.clear();
      }
   }

   private void publish(ConHolder conHolder, String oid, SessionName[] sessionNameArr) {
      try {
         msgSequenceNumber++;
         String content = "" + msgSequenceNumber;
         
         PublishQos pq = new PublishQos(glob);
         for(int i=0; i<sessionNameArr.length; i++)
            pq.addDestination(new Destination(sessionNameArr[i]));
         
         MsgUnit msgUnit = new MsgUnit("<key oid='"+oid+"'/>", content.getBytes(), pq.toXml());

         PublishReturnQos rq = conHolder.con.publish(msgUnit);
         
         log.info(ME, "SUCCESS publish '" + oid + "' with " + sessionNameArr.length + " destinations, returned state=" + rq.getState());
         assertEquals("Returned oid wrong", oid, rq.getKeyOid());
         assertEquals("Return not OK", Constants.STATE_OK, rq.getState());
      } catch(XmlBlasterException e) {
         log.warn(ME, "XmlBlasterException: " + e.getMessage());
         fail("publish - XmlBlasterException: " + e.getMessage());
      }
   }

   /**
    * Test all tuples of possibilities
    */
   public void testPtSession() {
      log.info(ME, "testPtSession() ...");
      long sleep = 1000L;

      {
         log.info(ME, "TEST #1: Sending PtP message to all sessions of client " + name);
         SessionName[] sessionNameArr = { new SessionName(glob, name) };
         publish(this.conHolderArr[0], this.msgOid, sessionNameArr);
         for(int ii=0; ii<this.conHolderArr.length; ii++) {
            assertEquals("", 1, this.conHolderArr[ii].update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
            this.conHolderArr[ii].update.clear();
         }
      }

      {
         SessionName sessionName3 = this.conHolderArr[3].connectReturnQos.getSessionName();
         log.info(ME, "TEST #2: Sending PtP message to session " + sessionName3.getAbsoluteName());
         SessionName[] sessionNameArr = { sessionName3 };
         publish(this.conHolderArr[0], this.msgOid, sessionNameArr);
         for(int ii=0; ii<this.conHolderArr.length; ii++) {
            int numExpected = (ii==3) ? 1 : 0;
            assertEquals("ii="+ii, numExpected, this.conHolderArr[ii].update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
            this.conHolderArr[ii].update.clear();
         }
      }

      {
         SessionName sessionName1 = this.conHolderArr[1].connectReturnQos.getSessionName();
         SessionName sessionName3 = this.conHolderArr[3].connectReturnQos.getSessionName();
         log.info(ME, "TEST #3: Sending PtP message to session " + sessionName3.getAbsoluteName() + " and " + sessionName1.getAbsoluteName());
         SessionName[] sessionNameArr = { sessionName3, sessionName1 };
         publish(this.conHolderArr[0], this.msgOid, sessionNameArr);
         for(int ii=0; ii<this.conHolderArr.length; ii++) {
            int numExpected = (ii==1 || ii==3) ? 1 : 0;
            assertEquals("ii="+ii, numExpected, this.conHolderArr[ii].update.waitOnUpdate(sleep, msgOid, Constants.STATE_OK));
            this.conHolderArr[ii].update.clear();
         }
      }

      log.info(ME, "Success in testPtSession()");
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      for(int ii=0; ii<conHolderArr.length; ii++) {
         conHolderArr[ii].con.disconnect(null);
      }

      if (this.startEmbedded) {
         EmbeddedXmlBlaster.stopXmlBlaster(serverThread);

         // reset to default server port (necessary if other tests follow in the same JVM).
         Util.resetPorts();
      }
   }

   /**
    * Method is used by TestRunner to load these tests
    */
   public static Test suite() {
       TestSuite suite= new TestSuite();
       String loginName = "PtSession";
       suite.addTest(new TestPtSession(Global.instance(), "testPtSession", "PtSession"));
       return suite;
   }

   /**
    * Invoke: 
    * <pre>
    *  java org.xmlBlaster.test.qos.TestPtSession -trace[qos] true -call[core] true -startEmbedded false
    *  java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.qos.TestPtSession
    * <pre>
    */
   public static void main(String args[]) {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.exit(0);
      }
      TestPtSession testSub = new TestPtSession(glob, "TestPtSession", "TestPtSession");
      testSub.setUp();
      testSub.testPtSession();
      testSub.tearDown();
   }
}

