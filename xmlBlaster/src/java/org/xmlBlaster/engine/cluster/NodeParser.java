/*------------------------------------------------------------------------------
Name:      NodeParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XML parsing cluster node specific messages
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.helper.Destination;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.XmlBlasterProperty;
import org.xmlBlaster.util.SaxHandlerBase;

import java.util.Vector;
import java.util.Map;
import java.util.Iterator;

import org.xml.sax.Attributes;

/**
 * XML parsing cluster node specific messages. 
 * <p />
 * Example:
 * <pre>
 *  &lt;clusternode id='heron' maxConnections='800'> &lt;!-- NodeParser -->
 *
 *     &lt;!-- Messages of type "__sys__cluster.node:heron": -->
 *     &lt;!-- Parsed by NodeInfo.java -->
 *
 *     &lt;info>
 *        &lt;address type='IOR'>IOR:09456087000&lt;/address>
 *        &lt;address type='XML-RPC'>http://www.mars.universe:8080/RPC2&lt;/address>
 *        &lt;callback type='XML-RPC'>http://www.mars.universe:8080/RPC2&lt;/callback>
 *        &lt;backupnode>
 *           &lt;clusternode id='avalon'/>
 *           &lt;clusternode id='golan'/>
 *        &lt;/backupnode>
 *        &lt;nameservice>true&lt;/nameservice>
 *     &lt;/info>
 *
 *
 *     &lt;!-- Messages of type "__sys__cluster.node.domainmapping:heron": -->
 *     &lt;!-- Parsed by NodeDomainInfo.java -->
 *
 *     &lt;master stratum='0' type='DomainToMaster'> <!-- Specify your plugin -->
 *        &lt;key domain=''/>
 *        &lt;key domain='rugby'/>
 *     &lt;/master>
 *
 *
 *     &lt;!-- Messages of type "__sys__cluster.node.state:heron": -->
 *     &lt;!-- Parsed by NodeStateInfo.java -->
 *
 *     &lt;state>
 *        &lt;cpu id='0' idle='40'/>
 *        &lt;ram free='12000'/>
 *     &lt;/state>
 *
 *  &lt;/clusternode>
 * </pre>
 * Note that maxConnections is specific to message types "__sys__cluster.node:heron"
 * <p />
 * The parsed data is directly written into the ClusterManager attributes
 */
public class NodeParser extends SaxHandlerBase
{
   private String ME = "NodeParser";

   private final Global glob;

   /** The unique node id */
   private String id = null;

   private int inClusternode = 0; // parsing inside <clusternode>, we use the nested level depth here
   private ClusterNode tmpClusterNode = null;

   // "__sys__cluster.node:heron"
   private boolean inInfo = false; // parsing inside <info> ?
   private NodeInfo tmpNodeInfo = null;

   // "__sys__cluster.node.domainmapping:heron"
   private boolean inMaster = false; // parsing inside <master> ?
   private NodeDomainInfo tmpMaster = null;

   // "__sys__cluster.node.state:heron"
   private boolean inState = false; // parsing inside <state> ?
   private NodeStateInfo tmpState = null; // Helper variable


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param xml  The XML based ASCII string
    */
   public NodeParser(Global glob, String xml) throws XmlBlasterException {
      // if (Log.TRACE) Log.trace(ME, "\n"+xml);
      this.glob = glob;
      init(xml);  // use SAX parser to parse it (is slow)
   }

   private final String getId() {
      if (id == null) glob.getLog().warn(ME, "cluster node id is null");
      return id;
   }

   /**
    * Access the parsed ClusterNode object
    */
   public ClusterNode getClusterNode() {
      return tmpClusterNode;
   }

   /**
    * Start element, event from SAX parser.
    * <p />
    * @param name Tag name
    * @param attrs the attributes of the tag
    */
   public final void startElement(String uri, String localName, String name, Attributes attrs)
   {
      glob.getLog().info(ME, "startElement: name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);

      if (name.equalsIgnoreCase("clusternode")) {
         inClusternode++;
         if (inClusternode == 1) { // Handle the top level <clusternode>
            if (attrs != null) {
               id = attrs.getValue("id");
               if (id == null) {
                  glob.getLog().error(ME, "<clusternode> attribute 'id' is missing, ignoring message");
                  throw new RuntimeException("NodeParser: <clusternode> attribute 'id' is missing, ignoring message");
               }
               id = id.trim();
               tmpClusterNode = glob.getClusterManager().getClusterNode(id);
               if (tmpClusterNode == null) {
                  tmpClusterNode = new ClusterNode(glob, new NodeId(id));
                  glob.getClusterManager().addClusterNode(tmpClusterNode);
               }
            }
            return;
         }
      }

      if (inMaster) { // delegate master internal tags
         if (tmpMaster == null) { glob.getLog().error(ME, "Internal problem in <master> section"); return; }
         tmpMaster.startElement(uri, localName, name, character, attrs);
         return;
      }
      if (name.equalsIgnoreCase("master")) {
         if (inClusternode != 1) return;
         inMaster = true;
         tmpMaster = new NodeDomainInfo(glob);
         if (tmpMaster.startElement(uri, localName, name, character, attrs) == true)
            tmpClusterNode.addDomainInfo(tmpMaster);
         else
            tmpMaster = null;
         return;
      }

      if (inState) { // delegate state internal tags
         if (tmpState == null) return;
         tmpState.startElement(uri, localName, name, character, attrs);
         return;
      }
      if (name.equalsIgnoreCase("state")) {
         if (inClusternode != 1) return;
         inState = true;
         tmpState = new NodeStateInfo(glob);
         if (tmpState.startElement(uri, localName, name, character, attrs) == true)
            tmpClusterNode.setNodeStateInfo(tmpState);
         else {
            glob.getLog().error(ME, "Internal problem in <state> section");
            tmpState = null;
         }
         character.setLength(0);
         return;
      }

      if (inInfo) { // delegate info internal tags
         if (tmpNodeInfo == null) return;
         tmpNodeInfo.startElement(uri, localName, name, character, attrs);
         return;
      }
      if (name.equalsIgnoreCase("info")) {
         if (inClusternode != 1) return;
         inInfo = true;
         tmpNodeInfo = new NodeInfo(glob, tmpClusterNode.getNodeId());
         if (tmpNodeInfo.startElement(uri, localName, name, character, attrs) == true)
            tmpClusterNode.setNodeInfo(tmpNodeInfo);
         else {
            glob.getLog().error(ME, "Internal problem in <info> section");
            tmpNodeInfo = null;
         }
         character.setLength(0);
         return;
      }

      glob.getLog().warn(ME, "startElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name)
   {
      glob.getLog().info(ME, "endElement: name=" + name + " character='" + character.toString() + "'");

      if (name.equalsIgnoreCase("clusternode")) {
         inClusternode--;
         if (inClusternode == 0) return; // Ignore the top level <clusternode>
      }

      if (name.equalsIgnoreCase("master") && inClusternode == 1) {
         inMaster = false;
         tmpMaster.endElement(uri, localName, name, character);
         return;
      }
      if (inMaster) { // delegate master internal tags
         tmpMaster.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("state") ) {
         inState = false;
         if (tmpState == null) return;
         tmpState.endElement(uri, localName, name, character);
         return;
      }
      if (inState) {
         if (tmpState == null) return;
         tmpState.endElement(uri, localName, name, character);
         return;
      }

      if (name.equalsIgnoreCase("info") ) {
         inInfo = false;
         if (tmpNodeInfo == null) return;
         tmpNodeInfo.endElement(uri, localName, name, character);
         return;
      }
      if (inInfo) {
         if (tmpNodeInfo == null) return;
         tmpNodeInfo.endElement(uri, localName, name, character);
         return;
      }

      glob.getLog().warn(ME, "endElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
   }


   /** For testing: java org.xmlBlaster.engine.cluster.NodeParser */
   public static void main(String[] args)
   {
      try {
         Global glob = new Global(args);

         XmlBlasterProperty.init(args);
         String xml =
            "<clusternode id='heron.mycomp.com'> <!-- original xml markup -->\n" +
            "   <info>\n" +
            "     <address type='IOR'>IOR:09456087000</address>\n" +
            "     <address type='XML-RPC'>http://www.mycomp.com/XMLRPC/</address>\n" +
            "     <callback type='RMI'>rmi://mycomp.com</callback>\n" +
            "     <backupnode>\n" +
            "        <clusternode id='bilbo.mycomp.com'/>\n" +
            "        <clusternode id='aragon.mycomp.com'/>\n" +
            "     </backupnode>\n" +
            "   </info>\n" +
            "   <master type='DOMAIN'>\n" +
            "     <![CDATA[\n" +
            "     <key domain='RUGBY'/>\n" +
            "     <key type='XPATH'>//STOCK</key>\n" +
            "     ]]>\n" +
            "   </master>\n" +
            "   <state>\n" +
            "     <cpu id='0' idle='40'/>\n" +
            "     <cpu id='1' idle='44'/>\n" +
            "     <ram free='12000'/>\n" +
            "   </state>\n" +
            "</clusternode>\n";

         {
            System.out.println("\nFull Message from client ...");
            NodeParser nodeParser = new NodeParser(glob, xml);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
 
         xml =
            "<clusternode id='heron.mycomp.com'>\n" +
            "   <master stratum='1' refid='frodo' type='DomainPlugin' version='2.0'>\n" +
            "     My own rule\n" +
            "   </master>\n" +
            "   <state>\n" +
            "     <cpu id='0' idle='40'/>\n" +
            "     <cpu id='1' idle='44'/>\n" +
            "     <ram free='12000'/>\n" +
            "   </state>\n" +
            "</clusternode>\n";
         
         
         {
            System.out.println("\nFull Message from client ...");
            NodeParser nodeParser = new NodeParser(glob, xml);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
         /*
         xml = "<clusternode></clusternode>";
         {
            System.out.println("\nEmpty message from client ...");
            NodeParser nodeParser = new NodeParser(glob, xml);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
         */
      }
      catch(Throwable e) {
         e.printStackTrace();
         Log.error("TestFailed", e.toString());
      }
   }
}
