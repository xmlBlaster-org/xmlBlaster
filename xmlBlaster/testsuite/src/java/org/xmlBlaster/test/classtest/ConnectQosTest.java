package org.xmlBlaster.test.classtest;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.ConnectQos;

import org.xmlBlaster.util.qos.address.Address;
import org.xmlBlaster.util.qos.address.AddressBase;
import org.xmlBlaster.util.qos.address.CallbackAddress;
import org.xmlBlaster.util.enum.Constants;
import org.xmlBlaster.util.qos.storage.QueueProperty;
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
 * @see org.xmlBlaster.util.ConnectQos
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
         "   <isClusterNode>true</isClusterNode>\n" +
         "   <duplicateUpdates>false</duplicateUpdates>\n" +
         "   <session name='/node/avalon/client/joe/2' timeout='" + sessionTimeout + "' maxSessions='27' clearSessions='true' sessionId='xyz'/>\n" +
         "   <queue relating='subject' maxMsg='1009' maxBytes='4000' onOverflow='deadMessage'>\n" +
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
         "   <queue relating='session' maxMsg='1600' maxBytes='2000'>\n" +
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

         ConnectQos qos = new ConnectQos(glob, xml); // parse
         String newXml = qos.toXml();                // dump
         qos = new ConnectQos(glob, newXml);         // parse again

         log.info("ConnectQosTest", "ORIG=\n" + xml + "\n NEW=\n" + newXml);

         assertEquals("sessionTimeout failed", sessionTimeout, qos.getSessionTimeout());
         assertEquals("", "/node/avalon/client/joe/2", qos.getSessionName().getAbsoluteName());
         assertEquals("", true, qos.hasPublicSessionId());
         assertEquals("", 2L, qos.getPublicSessionId());
         assertEquals("", sessionTimeout, qos.getSessionTimeout());
         assertEquals("", 27, qos.getMaxSessions());
         assertEquals("", true, qos.clearSessions());
         assertEquals("", "xyz", qos.getSessionId());

         assertEquals("", false, qos.isPtpAllowed());
         assertEquals("", true, qos.isClusterNode());
         assertEquals("", false, qos.duplicateUpdates());

         {
            CbQueueProperty prop = qos.getSubjectCbQueueProperty();
            assertEquals("", 1009L, prop.getMaxMsg());
            assertEquals("", true, qos.subjectCbQueuePropertyIsInitialized());
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
         public QueueProperty getClientQueueProperty() {
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
         ConnectQos qos = new ConnectQos(g.getId(), g);
         qos.setUserId(g.getId());
         assertEquals("Wrong user id", loginName, qos.getSecurityQos().getUserId());
         assertTrue("Wrong password", qos.toXml("").indexOf(passwd) > 0);
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
      testSub.testParse();
      //testSub.tearDown();
   }
}
