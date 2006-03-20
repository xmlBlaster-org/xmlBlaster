/*------------------------------------------------------------------------------
Name:      TestHistoryZero.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.test.client;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.client.key.EraseKey;
import org.xmlBlaster.client.key.GetKey;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.Constants;
import org.xmlBlaster.util.property.PropString;
import org.xmlBlaster.util.EmbeddedXmlBlaster;
import org.xmlBlaster.client.qos.EraseQos;
import org.xmlBlaster.client.qos.PublishQos;
import org.xmlBlaster.client.qos.GetQos;
import org.xmlBlaster.client.qos.EraseReturnQos;
import org.xmlBlaster.client.I_XmlBlasterAccess;
import org.xmlBlaster.util.qos.HistoryQos;
import org.xmlBlaster.util.qos.TopicProperty;
import org.xmlBlaster.util.qos.storage.HistoryQueueProperty;
import org.xmlBlaster.util.MsgUnit;

import org.xmlBlaster.test.Util;
import org.xmlBlaster.test.MsgInterceptor;
import junit.framework.*;


/**
 * Tests the setting of history queue to zero.
 * <p>
 * Invoke examples:<br />
 * <pre>
 *   java junit.textui.TestRunner -noloading org.xmlBlaster.test.client.TestHistoryZero
 *   java junit.swingui.TestRunner -noloading org.xmlBlaster.test.client.TestHistoryZero
 * </pre>
 * @see org.xmlBlaster.client.I_XmlBlasterAccess
 */
public class TestHistoryZero extends TestCase {
   private static String ME = "TestHistoryZero";
   
   private Global glob;
   private Global serverGlobal;
   private static Logger log = Logger.getLogger(TestHistoryZero.class.getName());

   private int serverPort = 7604;
   private EmbeddedXmlBlaster serverThread;

   private MsgInterceptor updateInterceptor;
   private String senderName;

   private final String contentMime = "text/plain";

   public TestHistoryZero(String testName) {
      this(null, testName);
   }

   public TestHistoryZero(Global glob, String testName) {
      super(testName);
      this.glob = glob;
      this.senderName = testName;
   }

   /**
    * Sets up the fixture.
    * <p />
    * Connect to xmlBlaster and login
    */
   protected void setUp() {
      this.glob = (this.glob == null) ? Global.instance() : this.glob;

      glob.init(Util.getOtherServerPorts(serverPort));

      String[] args = new String[] {"-queue/history/maxEntriesCache", "0",
                                    "-queue/history/maxEntries","0",
                                   };

      this.serverGlobal = this.glob.getClone(args);
      
      serverThread = EmbeddedXmlBlaster.startXmlBlaster(this.serverGlobal);
      log.info("XmlBlaster is ready for testing on bootstrapPort " + serverPort);
      try {
         I_XmlBlasterAccess con = glob.getXmlBlasterAccess(); // Find orb

         String passwd = "secret";
         ConnectQos connectQos = new ConnectQos(glob, senderName, passwd); // == "<qos>...</qos>";
         // set the persistent connection 
         connectQos.setPersistent(false);
         this.updateInterceptor = new MsgInterceptor(this.glob, log, null); // Collect received msgs
         con.connect(connectQos, this.updateInterceptor);  // Login to xmlBlaster, register for updates
      }
      catch (XmlBlasterException e) {
          log.warning("setUp() - login failed: " + e.getMessage());
          fail("setUp() - login fail: " + e.getMessage());
      }
      catch (Exception e) {
          log.severe("setUp() - login failed: " + e.toString());
          e.printStackTrace();
          fail("setUp() - login fail: " + e.toString());
      }
   }

   /**
    * Tears down the fixture.
    * <p />
    * cleaning up .... erase() the previous message OID and logout
    */
   protected void tearDown() {
      log.info("Entering tearDown(), test is finished");
      String xmlKey = "<key oid='' queryType='XPATH'>\n" +
                      "   //TestHistoryZero-AGENT" +
                      "</key>";

      String qos = "<qos><forceDestroy>true</forceDestroy></qos>";
      I_XmlBlasterAccess con = this.glob.getXmlBlasterAccess();
      try {
         EraseReturnQos[] arr = con.erase(xmlKey, qos);

         PropString defaultPlugin = new PropString("CACHE,1.0");
         String propName = defaultPlugin.setFromEnv(this.glob, glob.getStrippedId(), null, "persistence", Constants.RELATING_TOPICSTORE, "defaultPlugin");
         log.info("Lookup of propName=" + propName + " defaultValue=" + defaultPlugin.getValue());
      }
      catch(XmlBlasterException e) {
         log.severe("XmlBlasterException: " + e.getMessage());
      }
      finally {
         con.disconnect(null);
         EmbeddedXmlBlaster.stopXmlBlaster(this.serverThread);
         this.serverThread = null;
         // reset to default server bootstrapPort (necessary if other tests follow in the same JVM).
         Util.resetPorts(this.serverGlobal);
         Util.resetPorts(glob);
         this.glob = null;
         con = null;
         Global.instance().shutdown();
      }
   }

   /**
    * TEST: Construct a message and publish it.
    * <p />
    */
   public void doPublish(String oid, int counter, long numHistory) throws XmlBlasterException {
      log.info("Publishing a message " + oid + " ...");
      String xmlKey = "<key oid='" + oid + "' contentMime='" + contentMime + "'>\n" +
                      "   <TestHistoryZero-AGENT id='192.168.124.10' subId='1' type='generic'>" +
                      "   </TestHistoryZero-AGENT>" +
                      "</key>";
      String content = "" + counter;
      PublishQos qosWrapper = new PublishQos(glob); // == "<qos></qos>"
      if (numHistory > -1) {
         TopicProperty topicProp = new TopicProperty(this.glob);
         HistoryQueueProperty historyQueueProp = topicProp.getHistoryQueueProperty();
         historyQueueProp.setMaxEntries(numHistory);
         // TODO TEST THE maxEntriesCache != entriesCache. First specify the required behaviour.
         historyQueueProp.setMaxEntriesCache(numHistory);
         qosWrapper.setTopicProperty(topicProp);               
      }
      
      MsgUnit msgUnit = new MsgUnit(xmlKey, content.getBytes(), qosWrapper.toXml());

      this.glob.getXmlBlasterAccess().publish(msgUnit);
      log.info("Success: Publishing of " + oid + " done");
   }

   private void doGet(String oid, int expect) {
      GetKey key = new GetKey(this.glob, oid);
      GetQos qos = new GetQos(this.glob);
      HistoryQos histQos = new HistoryQos(this.glob, 2);
      qos.setHistoryQos(histQos);
      try {
         MsgUnit[] msg = this.glob.getXmlBlasterAccess().get(key, qos);
         assertNotNull("should not be null", msg);
         assertEquals("the number of returned values is wrong", expect, msg.length);
      }
      catch (XmlBlasterException ex) {
         ex.printStackTrace();
         assertTrue("no exception should take place here", false);
      }
   }
      

   /**
    * TEST: <br />
    */
   public void testHistoryZero() {

      int numPublish = 5;
      for (int i=0; i<numPublish; i++) {
         try {
            doPublish("historyZeroA", i+1, -1);
            doPublish("historyZeroB", i+1, 0);
            doPublish("historyZeroC", i+1, 1);
            doPublish("historyZeroD", i+1, 2);
            doPublish("historyZeroE", i+1, 5);
         }
         catch (XmlBlasterException ex) {
            ex.printStackTrace();
            assertTrue("an exception should not occur here", false);
         }   
      }
      doGet("historyZeroA", 0);
      doGet("historyZeroB", 0);
      doGet("historyZeroC", 1);
      doGet("historyZeroD", 2);
      doGet("historyZeroE", 2);
      try {
         EraseKey eraseKey = new EraseKey(this.glob, "//TestHistoryZero-AGENT", "XPATH");
         EraseQos eraseQos = new EraseQos(this.glob);
         this.glob.getXmlBlasterAccess().erase(eraseKey, eraseQos);
      }
      catch (XmlBlasterException e) {
         e.printStackTrace();
         assertTrue("exception should not occur here " + e.getMessage(), false);
      }
   }

   /**
    * Invoke: java org.xmlBlaster.test.client.TestHistoryZero
    * <p />
    * @deprecated Use the TestRunner from the testsuite to run it:<p />
    * <pre>   java -Djava.compiler= junit.textui.TestRunner org.xmlBlaster.test.client.TestHistoryZero</pre>
    */
   public static void main(String args[])
   {
      Global glob = new Global();
      if (glob.init(args) != 0) {
         System.out.println(ME + ": Init failed");
         System.exit(1);
      }

      TestHistoryZero test = new TestHistoryZero(glob, "TestHistoryZero/1");
      
      test.setUp();
      test.testHistoryZero();
      test.tearDown();
   }
}

