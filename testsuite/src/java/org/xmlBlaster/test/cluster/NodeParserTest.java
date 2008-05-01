package org.xmlBlaster.test.cluster;

import java.util.logging.Logger;

import junit.framework.TestCase;

import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.engine.cluster.ClusterManager;
import org.xmlBlaster.engine.cluster.ClusterNode;
import org.xmlBlaster.engine.cluster.NodeMasterInfo;
import org.xmlBlaster.engine.cluster.NodeParser;
import org.xmlBlaster.engine.cluster.NodeStateInfo;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.util.cluster.RouteInfo;
import org.xmlBlaster.util.qos.address.Address;

public class NodeParserTest extends TestCase {
   private static Logger log = Logger.getLogger(NodeParserTest.class.getName());

   private ServerScope serverScope;// = new ServerScope();

   public void setUp() {
      serverScope = new ServerScope();
      serverScope.setUseCluster(true);
   }

   public void tearDown() {
      serverScope.shutdown();
   }

   public void testParseConnectQos() {
      ClusterManager m = new ClusterManager(serverScope, null);

      try {
         m.init(serverScope, null);
         String xml = 
            "<clusternode id='avalon'>"
          + "<connect><qos>"
          + "<address type='SOCKET'>"
          + "   socket://:7501"
          + "</address>"
          + "</qos></connect>"
          + "   </clusternode>";
         NodeParser nodeParser = new NodeParser(serverScope, new ClusterNode(
               serverScope, new NodeId("avalon"), null), xml);
         Address address = nodeParser.getClusterNode().getNodeInfo()
               .getConnectQosData().getAddress();
         log.info("Address='" + address.getRawAddress().trim() + "'");
         assertEquals("socket://:7501", address.getRawAddress().trim());
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }

   public void testParseConnectQosAttribute() {
      ClusterManager m = new ClusterManager(serverScope, null);

      try {
         m.init(serverScope, null);

         String xml =
              "<clusternode id='avalon'>" + "<connect><qos>"
            + "<address type='SOCKET'>"
            + "   socket://:7501"
            + "   <attribute name='useRemoteLoginAsTunnel'>true</attribute>"
            + "</address>"
            + "</qos></connect>"
            + "</clusternode>";
         NodeParser nodeParser = new NodeParser(serverScope, new ClusterNode(
               serverScope, new NodeId("avalon"), null), xml);
         log.info(nodeParser.getClusterNode().toXml());
         Address address = nodeParser.getClusterNode().getNodeInfo()
               .getConnectQosData().getAddress();
         log.info("Address='" + address.getRawAddress().trim() + "'");
         assertEquals("socket://:7501", address.getRawAddress().trim());
         assertEquals(true, address.getEnv("useRemoteLoginAsTunnel", false)
               .getValue());
      } catch (XmlBlasterException e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }

   public void testParse() {
      ClusterManager m = new ClusterManager(serverScope, null);

      try {
         m.init(serverScope, null);

         String xml =
            "<clusternode id='heron.mycomp.com'> <!-- original xml markup -->\n" +
            "   <connect><qos>\n" +
            "     <address type='IOR'>IOR:09456087000</address>\n" +
            "     <address type='XMLRPC'>http://www.mycomp.com/XMLRPC/</address>\n" +
            "     <callback type='RMI'>rmi://mycomp.com</callback>\n" +
            "     <backupnode>\n" +
            "        <clusternode id='bilbo.mycomp.com'/>\n" +
            "        <clusternode id='aragon.mycomp.com'/>\n" +
            "     </backupnode>\n" +
            "   </qos></connect>\n" +
            "   <disconnect/>\n" +
            "   <master type='DomainToMaster'>\n" +
            "     <key queryType='DOMAIN' domain='RUGBY'/>\n" +
            "     <key queryType='XPATH'>//STOCK</key>\n" +
            "     <filter type='ContentLength'>\n" +
            "       8000\n" +
            "     </filter>\n" +
            "     <filter type='ContainsChecker' version='7.1' xy='true'>\n" +
            "       bug\n" +
            "     </filter>\n" +
            "     <someOtherPluginfilter>\n" +
            "        <![CDATA[\n" +
            "        ]]>\n" +
            "     </someOtherPluginfilter>\n" +
            "   </master>\n" +
            "   <master type='DomainToMaster' version='1.1' stratum='4' refid='bla' acceptDefault='false' acceptOtherDefault='true' dirtyRead='true'>\n" +
            "     <key queryType='XPATH'>//RUGBY</key>\n" +
            "   </master>\n" +
            "   <state>\n" +
            "     <cpu id='0' idle='40'/>\n" +
            "     <cpu id='1' idle='44'/>\n" +
            "     <ram free='12000'/>\n" +
            "   </state>\n" +
            "</clusternode>\n";

         NodeParser nodeParser = new NodeParser(serverScope, new ClusterNode(
               serverScope, new NodeId("avalon"), null), xml);
         ClusterNode clusterNode = nodeParser.getClusterNode();
         log.info(clusterNode.toXml());
         
         Address address = clusterNode.getNodeInfo()
               .getConnectQosData().getAddress();
         assertEquals("IOR:09456087000", address.getRawAddress().trim());
         assertEquals(false, address.getEnv("useRemoteLoginAsTunnel", false)
               .getValue());
         
         NodeMasterInfo[] masters = clusterNode.getNodeMasterInfos();
         assertEquals(2, masters.length);
         
         assertEquals("DomainToMaster", masters[0].getType());
         assertEquals("1.0", masters[0].getVersion());
         assertEquals(0, masters[0].getStratum());
         assertEquals(null, masters[0].getRefId());
         assertEquals(NodeMasterInfo.DEFAULT_acceptDefault,  masters[0].isAcceptDefault()); // true
         assertEquals(NodeMasterInfo.DEFAULT_acceptOtherDefault,  masters[0].isAcceptOtherDefault()); // false
         assertEquals(RouteInfo.DEFAULT_dirtyRead, masters[0].isDirtyRead());  // false
         assertEquals(2, masters[0].getAccessFilterArr().length);
         assertEquals(2, masters[0].getKeyMappings().length);
         assertEquals("RUGBY", masters[0].getKeyMappings()[0].getDomain().trim());
         assertEquals("//STOCK", masters[0].getKeyMappings()[1].getQueryString().trim());

         assertEquals("DomainToMaster", masters[1].getType());
         assertEquals("1.1", masters[1].getVersion());
         assertEquals(4, masters[1].getStratum());
         assertEquals("bla", masters[1].getRefId());
         assertEquals(false, masters[1].isAcceptDefault());
         assertEquals(true, masters[1].isAcceptOtherDefault());
         assertEquals(true, masters[1].isDirtyRead());
         assertEquals(0, masters[1].getAccessFilterArr().length);
         assertEquals(1, masters[1].getKeyMappings().length);
         assertEquals("//RUGBY", masters[1].getKeyMappings()[0].getQueryString().trim());

      } catch (XmlBlasterException e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }
   
   public void testParseState() {
      ClusterManager m = new ClusterManager(serverScope, null);

      try {
         m.init(serverScope, null);
  
         String xml =
         "<clusternode id='heron.mycomp.com'>\n" +
         "   <master stratum='1' refid='frodo' type='DomainPlugin' version='2.0' acceptDefault='false' acceptOtherDefault='true'>\n" +
         "     My own rule\n" +
         "   </master>\n" +
         "   <state>\n" +
         "     <cpu id='0' idle='60'/>\n" +
         "     <cpu id='1' idle='58'/>\n" +
         "     <ram free='10657'/>\n" +
         "   </state>\n" +
         "</clusternode>\n";

         NodeParser nodeParser = new NodeParser(serverScope, new ClusterNode(
               serverScope, new NodeId("avalon"), null), xml);
         ClusterNode clusterNode = nodeParser.getClusterNode();
         log.info(clusterNode.toXml());
         NodeStateInfo state = clusterNode.getNodeStateInfo();
         assertEquals(10657, state.getFreeRam());
         assertEquals(59, state.getAvgCpuIdle());

      } catch (XmlBlasterException e) {
         e.printStackTrace();
         fail(e.toString());
      }
   }
}
