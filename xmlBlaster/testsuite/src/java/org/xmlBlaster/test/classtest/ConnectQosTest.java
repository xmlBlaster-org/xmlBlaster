package org.xmlBlaster.test.classtest;

import java.util.logging.Logger;
import java.util.logging.Level;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.qos.SessionQos;
import org.xmlBlaster.util.qos.ConnectQosData;
import org.xmlBlaster.client.qos.ConnectQos;
import org.xmlBlaster.util.qos.I_ConnectQosFactory;
import org.xmlBlaster.util.def.Constants;

import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.storage.ClientQueueProperty;
import org.xmlBlaster.util.qos.storage.CbQueueProperty;
import org.xmlBlaster.util.qos.address.ServerRef;

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
   private static Logger log = Logger.getLogger(ConnectQosTest.class.getName());
   int counter = 0;

   public ConnectQosTest(String name) {
      super(name);
   }

   protected void setUp() {
      this.glob = Global.instance();

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
         "   <instanceId>/node/heron/instanceId/123445</instanceId>\n" +
         "   <reconnected>true</reconnected>\n" +
         "   <duplicateUpdates>false</duplicateUpdates>\n" +
         "   <session name='/node/avalon/client/joe/2' timeout='" + sessionTimeout + "' maxSessions='27' clearSessions='true' sessionId='xyz'/>\n" +
         "   <queue relating='subject' type='XY' version='7.0' maxEntries='1009' maxBytes='4009' maxEntriesCache='509' maxBytesCache='777' storeSwapLevel='20009' storeSwapBytes='10000' reloadSwapLevel='20000' reloadSwapBytes='30000' onOverflow='deadMessage'>\n" +
         /*
         "      <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='60000' retries='1' delay='60000' useForSubjectQueue='true'>\n" +
         "         <ptp>true</ptp>\n" +
         "         IOR:00011200070009990000....\n" +
         "         <compress type='gzip' minSize='1000' />\n" +
         "         <burstMode collectTime='400' maxEntries='12' maxBytes='24' />\n" +
         "      </callback>\n" +
         */
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
         "   <queue relating='connection' type='RAM' version='4.0' maxEntries='22' maxBytes='44'>\n" +
         "      <address type='XMLRPC'>\n" +
         "         http:/www.mars.universe:8080/RPC2\n" +
         "         <attribute name='intKey2' type='int'>34</attribute>\n" +
         "      </address>\n" +
         "   </queue>\n" +
         "   <queue relating='callback' maxEntries='1600' maxBytes='2000'>\n" +
         "      <callback type='XMLRPC'>\n" +
         "         <ptp>true</ptp>\n" +
         "         http:/www.mars.universe:8080/RPC2\n" +
         "         <compress type='gzip' minSize='1000' />\n" +
         "         <burstMode collectTime='400' maxEntries='12' maxBytes='24' />\n" +
         "         <attribute name='intKey1' type='int'>1234</attribute>\n" +
         "      </callback>\n" +
         "   </queue>\n" +
         "   <serverRef type='IOR'>\n" +
         "      IOR:00011200070009990000....\n" +
         "   </serverRef>\n" +
         "   <serverRef type='EMAIL'>\n" +
         "      et@mars.universe\n" +
         "   </serverRef>\n" +
         "   <serverRef type='XMLRPC'>\n" +
         "      http:/www.mars.universe:8080/RPC2\n" +
         "   </serverRef>\n" +
         "   <clientProperty name='intKey' type='int'>123</clientProperty>\n" +
         "   <clientProperty name='StringKey' type='String' encoding='" + Constants.ENCODING_BASE64 + "'>QmxhQmxhQmxh</clientProperty>\n" +
         "   <persistent>true</persistent>\n" +
         "</qos>\n";

         I_ConnectQosFactory factory = this.glob.getConnectQosFactory();
         ConnectQosData qos = factory.readObject(xml); // parse
         assertEquals("", true, qos.getPersistentProp().getValue());
         String newXml = qos.toXml();                  // dump
         qos = factory.readObject(newXml);             // parse again

         if (log.isLoggable(Level.FINE)) log.fine("ORIG=\n" + xml + "\n NEW=\n" + newXml);

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
         assertEquals("", true, qos.isReconnected());
         assertEquals("", "/node/heron/instanceId/123445", qos.getInstanceId());
         assertEquals("", false, qos.duplicateUpdates());
         assertEquals("", true, qos.getPersistentProp().getValue());

         {
            ClientQueueProperty prop = qos.getClientQueueProperty();
            assertEquals("", 22L, prop.getMaxEntries());
            assertEquals("", "RAM", prop.getType());
            assertEquals("", "4.0", prop.getVersion());
            assertEquals("", 44L, prop.getMaxBytes());
            AddressBase[] addrArr = prop.getAddresses();
            assertEquals("Address array", 1, addrArr.length);
            AddressBase addr = addrArr[0];
            assertEquals("", "34", addr.getEnv("intKey2", "").getValue());
         }

         {
            CbQueueProperty prop = qos.getSubjectQueueProperty();
            assertEquals("", 1009L, prop.getMaxEntries());
            assertEquals("", "XY", prop.getType());
            assertEquals("", "7.0", prop.getVersion());
            assertEquals("", 4009L, prop.getMaxBytes());
            assertEquals("", 509L, prop.getMaxEntriesCache());
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
            assertEquals("", 1600L, prop.getMaxEntries());
            AddressBase[] addrArr = prop.getAddresses();
            assertEquals("Address array", 1, addrArr.length);
            AddressBase addr = addrArr[0];
            assertEquals("", 400, addr.getCollectTime());
            assertEquals("", 12, addr.getBurstModeMaxEntries());
            assertEquals("", 24, addr.getBurstModeMaxBytes());
            assertEquals("", "1234", addr.getEnv("intKey1", "").getValue());
         }

         assertEquals("Wrong number of clientProperties", 2, qos.getClientProperties().size());
         {
            String prop = qos.getClientProperty("StringKey", (String)null);
            assertTrue("Missing client property", prop != null);
            assertEquals("Wrong base64 decoding", "BlaBlaBla", prop); // Base64: QmxhQmxhQmxh -> BlaBlaBla
         }

         {
            int prop = qos.getClientProperty("intKey", -1);
            assertEquals("Wrong value", 123, prop);
         }

         //System.out.println(qos.toXml());
         
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
         assertEquals("Address array", 1, addrArr.length);
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

   public void testParse2() {
      System.out.println("***ConnectQosTest: testParse2 ...");
      
      try {
         long sessionTimeout = 3600001L;
         String xml =
         "<qos>\n" +
         "  <securityService type='htpasswd' version='1.0'><![CDATA[\n" +
         "   <user>A-native-client-plugin</user>\n" +
         "   <passwd>secret</passwd>\n" +
         "  ]]></securityService>\n" +
         "  <session name='client/A-native-client-plugin' timeout='86400000' maxSessions='10' clearSessions='false' reconnectSameClientOnly='false'/>\n" +
         "  <queue relating='connection' type='RAM' version='1.0' maxEntries='10000000' maxEntriesCache='1000'>\n" +
         "   <address type='LOCAL' pingInterval='0' dispatchPlugin='undef'>\n" +
         "    <burstMode collectTime='0'/>\n" +
         "   </address>\n" +
         "  </queue>\n" +
         " </qos>\n";

         I_ConnectQosFactory factory = this.glob.getConnectQosFactory();
         ConnectQosData qos = factory.readObject(xml); // parse
         String newXml = qos.toXml();                  // dump
         qos = factory.readObject(newXml);             // parse again

         if (log.isLoggable(Level.FINE)) log.fine("ORIG=\n" + xml + "\n NEW=\n" + newXml);
         
         ClientQueueProperty prop = qos.getClientQueueProperty();
         assertEquals("", "RAM", prop.getType());
         assertEquals("", "1.0", prop.getVersion());
         System.out.println(qos.toXml());
      }
      catch (XmlBlasterException e) {
         fail("testParse2 failed: " + e.toString());
      }

      System.out.println("***ConnectQosTest: testParse2 [SUCCESS]");
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
      testSub.testParse2();
      //testSub.tearDown();
   }
}
