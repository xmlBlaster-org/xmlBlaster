package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.qos.I_ConnectQosFactory;
import org.xmlBlaster.util.qos.ConnectQosSaxFactory;

import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;
import org.xmlBlaster.protocol.I_CallbackDriver;
import org.xmlBlaster.authentication.plugins.I_ClientPlugin;
import org.xmlBlaster.authentication.plugins.I_SecurityQos;

import junit.framework.*;

/**
 * Test ConnectQos. 
 * <p />
 * All methods starting with 'test' and without arguments are invoked automatically
 * <p />
 * TODO: http://xmlunit.sourceforge.net/
 * <p />
 * Invoke: java -Djava.compiler= junit.textui.TestRunner -noloading org.xmlBlaster.test.classtest.ConnectQosTest
 * @see org.xmlBlaster.util.qos.ConnectQosData
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/interface.connect.html" target="others">the interface.connect requirement</a>
 */
public class ConnectQosTest extends TestCase {
   protected Global glob;
   protected LogChannel log;
   int counter = 0;

   public ConnectQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();
      this.log = this.glob.getLog("test");
   }

   public void testParse() {
      System.out.println("***ConnectQosTest: testParse ...");
      
      try {
         long sessionTimeout = 3600001L;
         String xml =
         "<qos>\n" +
         "   <securityService type='htpasswd' version='1.0'>\n" +
         "      <![CDATA[\n" +
         "         <user>joe</user>\n" +
         "         <passwd>theUsersPwd</passwd>\n" +
         "      ]]>\n" +
         "   </securityService>\n" +
         "   <ptp>false</ptp>\n" +
         "   <clusterNode>true</clusterNode>\n" +
         "   <duplicateUpdates>false</duplicateUpdates>\n" +
         "   <session name='/node/avalon/client/joe/2' timeout='" + sessionTimeout + "' maxSessions='27' clearSessions='true' sessionId='xyz'/>\n" +
         "   <queue relating='subject' type='XY' version='7.0' maxMsg='1009' maxBytes='4009' maxMsgCache='509' maxBytesCache='777' storeSwapLevel='20009' storeSwapBytes='10000' reloadSwapLevel='20000' reloadSwapBytes='30000' onOverflow='deadMessage'>\n" +
         "      <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='60000' retries='1' delay='60000' useForSubjectQueue='true'>\n" +
         "         <ptp>true</ptp>\n" +
         "         IOR:00011200070009990000....\n" +
         "         <compress type='gzip' minSize='1000' />\n" +
         "         <burstMode collectTime='400' />\n" +
         "      </callback>\n" +
         "   </queue>\n" +
         /*
         "   <callback type='IOR'>\n" +
         "      IOR:00000461203\n" +
         "   </callback>\n" +
         "   <callback type='EMAIL'>\n" +
         "      et@mars.universe\n" +
         "      <ptp>false</ptp>\n" +
         "   </callback>\n" +
         */
         "   <queue relating='callback' maxMsg='1600' maxBytes='2000'>\n" +
         "      <callback type='XML-RPC'>\n" +
         "         <ptp>true</ptp>\n" +
         "         http:/www.mars.universe:8080/RPC2\n" +
         "      </callback>\n" +
         "   </queue>\n" +
         "   <serverRef type='IOR'>\n" +
         "      IOR:00011200070009990000....\n" +
         "   </serverRef>\n" +
         "   <serverRef type='EMAIL'>\n" +
         "      et@mars.universe\n" +
         "   </serverRef>\n" +
         "   <serverRef type='XML-RPC'>\n" +
         "      http:/www.mars.universe:8080/RPC2\n" +
         "   </serverRef>\n" +
         "</qos>\n";

         I_ConnectQosFactory factory = this.glob.getConnectQosFactory();
         ConnectQosData qos = factory.readObject(xml); // parse
         String newXml = qos.toXml();                  // dump
         qos = factory.readObject(newXml);             // parse again

         if (log.TRACE) log.trace("ConnectQosTest", "ORIG=\n" + xml + "\n NEW=\n" + newXml);

         SessionQos sessionQos = qos.getSessionQos();
         assertEquals("sessionTimeout failed", sessionTimeout, sessionQos.getSessionTimeout());
         assertEquals("", "/node/avalon/client/joe/2", sessionQos.getSessionName().getAbsoluteName());
         assertEquals("", true, sessionQos.hasPublicSessionId());
         assertEquals("", 2L, sessionQos.getPublicSessionId());
         assertEquals("", sessionTimeout, sessionQos.getSessionTimeout());
         assertEquals("", 27, sessionQos.getMaxSessions());
         assertEquals("", true, sessionQos.clearSessions());
         assertEquals("", "xyz", sessionQos.getSecretSessionId());

         assertEquals("", false, qos.isPtpAllowed());
         assertEquals("", true, qos.isClusterNode());
         assertEquals("", false, qos.duplicateUpdates());

         {
            CbQueueProperty prop = qos.getSubjectQueueProperty();
            assertEquals("", 1009L, prop.getMaxMsg());
            assertEquals("", "XY", prop.getType());
            assertEquals("", "7.0", prop.getVersion());
            assertEquals("", 4009L, prop.getMaxBytes());
            assertEquals("", 509L, prop.getMaxMsgCache());
            assertEquals("", 777L, prop.getMaxBytesCache());
            /* Currently deactivated in code
            assertEquals("", 20009L, prop.getStoreSwapLevel());
            assertEquals("", 10000L, prop.getStoreSwapBytes());
            assertEquals("", 20000L, prop.getReloadSwapLevel());
            assertEquals("", 30000L, prop.getReloadSwapBytes());
            */
            assertEquals("", "deadMessage", prop.getOnOverflow());
            assertEquals("", true, qos.hasSubjectQueueProperty());
         }


         {
            CbQueueProperty prop = qos.getSessionCbQueueProperty();
            assertEquals("", 1600L, prop.getMaxMsg());
         }
         
         // TODO: check all methods !!!

         /*
         protected I_ClientPlugin getPlugin(String mechanism, String version) throws XmlBlasterException
         public String getSecurityData() {
         */
         ServerRef[] refArr = qos.getServerRefs();
         assertEquals("", 3, refArr.length);
         ServerRef ref = qos.getServerRef();
         assertTrue("", ref != null);
         /*
         public ClientQueueProperty getClientQueueProperty() {
         public final I_SecurityQos getSecurityQos() throws XmlBlasterException
         public final String getSecurityPluginType() throws XmlBlasterException
         public final String getSecurityPluginVersion() throws XmlBlasterException
         */
         AddressBase[] addrArr = qos.getAddresses();
         assertEquals("", 1, addrArr.length);
         Address addr = qos.getAddress();
         //assertEquals("", "http:...", addr.getAddress().trim()); // from client queue property
         assertEquals("", false, qos.isPtpAllowed());
         assertEquals("", "joe", qos.getUserId());
      }
      catch (XmlBlasterException e) {
         fail("testParse failed: " + e.toString());
      }

      System.out.println("***ConnectQosTest: testParse [SUCCESS]");
   }

   public void testClientConnectQos() {
      System.out.println("***ConnectQosTest: testClientConnectQos ...");
      /*
      try {
         ConnectQos qos = new ConnectQos(glob);

         // test missing !!!
      }
      catch (XmlBlasterException e) {
         fail("testClientConnectQos failed: " + e.toString());
      }
      */
      System.out.println("***ConnectQosTest: testClientConnectQos [SUCCESS]");
   }

   public void testCredential() {
      System.out.println("***ConnectQosTest: testCredential ...");
      String loginName = "avalon";
      String passwd = "avalonSecret";
      String[] args = {
         "-cluster.node.id",
         loginName,
         "-passwd[avalon]",
         passwd
         };
      
      try {
         Global g = glob.getClone(args);
         ConnectQos qos = new ConnectQos(g);
         qos.setUserId(g.getId());
         assertEquals("Wrong user id", loginName, qos.getSecurityQos().getUserId());
         assertTrue("Wrong password, expected '" + passwd + "': " + qos.toXml(), qos.toXml().indexOf(passwd) > 0);
         //System.out.println("ConnectQos=" + qos.toXml(""));
      }
      catch (XmlBlasterException e) {
         fail("testCredential failed: " + e.toString());
      }

      System.out.println("***ConnectQosTest: testCredential [SUCCESS]");
   }
   /**
    * <pre>
    *  java org.xmlBlaster.test.classtest.ConnectQosTest
    * </pre>
    */
   public static void main(String args[])
   {
      ConnectQosTest testSub = new ConnectQosTest("ConnectQosTest");
      testSub.setUp();
      testSub.testCredential();
      testSub.testParse();
      testSub.testClientConnectQos();
      //testSub.tearDown();
   }
}
