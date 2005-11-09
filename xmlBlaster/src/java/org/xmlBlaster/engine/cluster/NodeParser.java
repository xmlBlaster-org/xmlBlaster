/*------------------------------------------------------------------------------
Name:      NodeParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XML parsing cluster node specific messages
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.SaxHandlerBase;
import org.xmlBlaster.util.cluster.NodeId;
import org.xmlBlaster.authentication.SessionInfo;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
/* Deprecated variant:
 *     &lt;info>
 *        &lt;address type='IOR'>IOR:09456087000&lt;/address>
 *        &lt;address type='XMLRPC'>http://www.mars.universe:8080/RPC2&lt;/address>
 *        &lt;callback type='XMLRPC'>http://www.mars.universe:8080/RPC2&lt;/callback>
 *        &lt;backupnode>
 *           &lt;clusternode id='avalon'/>
 *           &lt;clusternode id='golan'/>
 *        &lt;/backupnode>
 *        &lt;nameservice>true&lt;/nameservice>
 *     &lt;/info>
 */

/**
 * XML parsing cluster node specific messages. 
 * <p />
 * Example:
 * <pre>
 *&lt;clusternode id='heron' maxConnections='800'> &lt;!-- NodeParser -->
 *
 *  &lt;!-- Messages of type "__sys__cluster.node:heron": -->
 *  &lt;!-- Parsed by NodeInfo.java -->
 *
 *  &lt;connect>
 *    &lt;qos> &lt;--! a ConnectQos markup -->
 *       &lt;address type='IOR'>IOR:09456087000&lt;/address>
 *       &lt;address type='XMLRPC'>http://www.mars.universe:8080/RPC2&lt;/address>
 *       &lt;callback type='XMLRPC'>http://www.mars.universe:8080/RPC2&lt;/callback>
 *       &lt;backupnode>
 *          &lt;clusternode id='avalon'/>
 *          &lt;clusternode id='golan'/>
 *       &lt;/backupnode>
 *       &lt;nameservice>true&lt;/nameservice>
 *    &lt;/qos>
 *  &lt;/connect>
 *
 *
 *  &lt;!-- Messages of type "__sys__cluster.node.domainmapping:heron": -->
 *  &lt;!-- Parsed by NodeDomainInfo.java -->
 *
 *  &lt;master stratum='0' type='DomainToMaster'> <!-- Specify your plugin -->
 *     &lt;key queryType='DOMAIN' domain='RUGBY'/>
 *  &lt;/master>
 *
 *
 *  &lt;!-- Messages of type "__sys__cluster.node.state:heron": -->
 *  &lt;!-- Parsed by NodeStateInfo.java -->
 *
 *  &lt;state>
 *     &lt;cpu id='0' idle='40'/>
 *     &lt;ram free='12000'/>
 *  &lt;/state>
 *
 *&lt;/clusternode>
 * </pre>
 * Note that maxConnections is specific to message types "__sys__cluster.node:heron"
 * <p />
 * The parsed data is directly written into the ClusterManager attributes
 */
public class NodeParser extends SaxHandlerBase
{
   private String ME = "NodeParser";

   private final Global glob;
   private final ClusterManager clusterManager;

   /** The unique node id */
   private String id = null;

   private int inClusternode = 0; // parsing inside <clusternode>, we use the nested level depth here
   private ClusterNode tmpClusterNode = null;

   // "__sys__cluster.node:heron"
   private boolean inConnect = false; // parsing inside <info> or <connect> (info is deprecated) ?
   private NodeInfo tmpNodeInfo = null;

   // "__sys__cluster.node.domainmapping:heron"
   private boolean inMaster = false; // parsing inside <master> ?
   private NodeDomainInfo tmpMaster = null;

   // "__sys__cluster.node.state:heron"
   private boolean inState = false; // parsing inside <state> ?
   private NodeStateInfo tmpState = null; // Helper variable

   private final SessionInfo sessionInfo;


   /**
    * Constructs the specialized quality of service object for a publish() call.
    * @param xml  The XML based ASCII string
    * @param sessionInfo The sessionInfo needs to be passed through to ClusterNode
    */
   public NodeParser(Global glob, ClusterManager clusterManager, String xml, SessionInfo sessionInfo) throws XmlBlasterException {
      this.glob = glob;
      this.clusterManager = clusterManager;
      this.sessionInfo = sessionInfo;
      init(xml);  // use SAX parser to parse it (is slow)
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
      throws SAXException
   {
      // glob.getLog("cluster").info(ME, "startElement: name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
      try {
         if (name.equalsIgnoreCase("clusternode")) {
            inClusternode++;
            if (inClusternode == 1) { // Handle the top level <clusternode>
               if (attrs != null) {
                  id = attrs.getValue("id");
                  if (id == null) {
                     glob.getLog("cluster").error(ME, "<clusternode> attribute 'id' is missing, ignoring message");
                     throw new RuntimeException("NodeParser: <clusternode> attribute 'id' is missing, ignoring message");
                  }
                  id = id.trim();
                  tmpClusterNode = clusterManager.getClusterNode(id);
                  if (tmpClusterNode == null) {
                     tmpClusterNode = new ClusterNode(glob, new NodeId(id), sessionInfo);
                     clusterManager.addClusterNode(tmpClusterNode);
                  }
               }
               return;
            }
         }

         if (inMaster) { // delegate master internal tags
            if (tmpMaster == null) { glob.getLog("cluster").error(ME, "Internal problem in <master> section"); return; }
            tmpMaster.startElement(uri, localName, name, character, attrs);
            return;
         }
         if (!inConnect && name.equalsIgnoreCase("master")) {
            if (inClusternode != 1) return;
            inMaster = true;
            tmpMaster = new NodeDomainInfo(glob, tmpClusterNode);
            if (tmpMaster.startElement(uri, localName, name, character, attrs) == true) {
               tmpClusterNode.addDomainInfo(tmpMaster);
            }
            else
               tmpMaster = null;
            return;
         }

         if (inState) { // delegate state internal tags
            if (tmpState == null) return;
            tmpState.startElement(uri, localName, name, character, attrs);
            return;
         }
         if (!inConnect && name.equalsIgnoreCase("state")) {
            if (inClusternode != 1) return;
            inState = true;
            tmpState = new NodeStateInfo(glob);
            if (tmpState.startElement(uri, localName, name, character, attrs) == true)
               tmpClusterNode.setNodeStateInfo(tmpState);
            else {
               glob.getLog("cluster").error(ME, "Internal problem in <state> section");
               tmpState = null;
            }
            character.setLength(0);
            return;
         }

         if (inConnect) { // delegate <connect> internal tags
            if (tmpNodeInfo == null) return;
            tmpNodeInfo.startElement(uri, localName, name, character, attrs);
            return;
         }
         if (name.equalsIgnoreCase("info") || name.equalsIgnoreCase("connect")) {
            if (inClusternode != 1) return;
            inConnect = true;
            tmpNodeInfo = new NodeInfo(glob, tmpClusterNode.getNodeId());
            if (tmpNodeInfo.startElement(uri, localName, name, character, attrs) == true)
               tmpClusterNode.setNodeInfo(tmpNodeInfo);
            else {
               glob.getLog("cluster").error(ME, "Internal problem in <"+name+"> section");
               tmpNodeInfo = null;
            }
            character.setLength(0);
            return;
         }
      }
      catch (XmlBlasterException e) {
         throw new org.xmlBlaster.util.StopParseException(e);
      }
      catch (Throwable e) {
         XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "ClusterNode parser failed", e);
         throw new SAXException("", ex);
      }

      glob.getLog("cluster").warn(ME, "startElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
   }


   /**
    * End element, event from SAX parser.
    * <p />
    * @param name Tag name
    */
   public void endElement(String uri, String localName, String name) throws SAXException
   {
      // glob.getLog("cluster").info(ME, "endElement: name=" + name + " character='" + character.toString() + "'");

      if (name.equalsIgnoreCase("clusternode")) {
         inClusternode--;
         if (inClusternode == 0) return; // Ignore the top level <clusternode>
      }

      if (inMaster) { // delegate master internal tags
         if (name.equalsIgnoreCase("master") && inClusternode == 1) {
            inMaster = false;
            tmpMaster.endElement(uri, localName, name, character);
            return;
         }
         tmpMaster.endElement(uri, localName, name, character);
         return;
      }

      if (inState) {
         if (name.equalsIgnoreCase("state") ) {
            inState = false;
            if (tmpState == null) return;
            tmpState.endElement(uri, localName, name, character);
            return;
         }
         if (tmpState == null) return;
         tmpState.endElement(uri, localName, name, character);
         return;
      }

      if (inConnect) {
         if (name.equalsIgnoreCase("info") || name.equalsIgnoreCase("connect")) {
            inConnect = false;
            if (tmpNodeInfo == null) return;
            tmpNodeInfo.endElement(uri, localName, name, character);
            return;
         }
         if (tmpNodeInfo == null) return;
         tmpNodeInfo.endElement(uri, localName, name, character);
         return;
      }

      glob.getLog("cluster").warn(ME, "endElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
   }


   /** For testing: java org.xmlBlaster.engine.cluster.NodeParser */
   public static void main(String[] args)
   {
      Global glob = new Global(args);
      try {
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
            "   <master type='DomainToMaster' version='0.9'>\n" +
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
            "   <master type='DomainToMaster' version='1.1'>\n" +
            "     //RUGBY\n" +
            "   </master>\n" +
            "   <state>\n" +
            "     <cpu id='0' idle='40'/>\n" +
            "     <cpu id='1' idle='44'/>\n" +
            "     <ram free='12000'/>\n" +
            "   </state>\n" +
            "</clusternode>\n";

         {
            System.out.println("\nFull Message from client ...");
            NodeParser nodeParser = new NodeParser(glob, glob.getClusterManager(), xml, null);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
 
         xml =
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
         
         
         {
            System.out.println("\nFull Message from client ...");
            NodeParser nodeParser = new NodeParser(glob, glob.getClusterManager(), xml, null);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
         /*
         xml = "<clusternode></clusternode>";
         {
            System.out.println("\nEmpty message from client ...");
            NodeParser nodeParser = new NodeParser(glob, glob.getClusterManager(), xml);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
         */
      }
      catch(Throwable e) {
         e.printStackTrace();
         glob.getLog(null).error("TestFailed", e.toString());
      }
   }
}
