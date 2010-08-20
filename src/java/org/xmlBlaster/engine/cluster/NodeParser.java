/*------------------------------------------------------------------------------
Name:      NodeParser.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   XML parsing cluster node specific messages
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import java.util.logging.Logger;

import org.xmlBlaster.client.qos.DisconnectQos;
import org.xmlBlaster.engine.ServerScope;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.util.def.ErrorCode;
import org.xmlBlaster.util.def.MethodName;
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
 *  &lt;!-- Parsed by NodeMasterInfo.java -->
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
 * <p />
 * TODO: Support full XmlScript syntax by using our xmlScript parser
 */
public class NodeParser extends SaxHandlerBase
{
   private static Logger log = Logger.getLogger(NodeParser.class.getName());
   private String ME = "NodeParser";

   private final ServerScope glob;
   private final ClusterManager clusterManager;

   /** The unique node id */
   private String id = null;

   private int inClusternode = 0; // parsing inside <clusternode>, we use the nested level depth here
   private ClusterNode tmpClusterNode = null;

   // "__sys__cluster.node:heron"
   private boolean inConnect = false; // parsing inside <info> or <connect> (info is deprecated) ?
   private NodeConnectQos tmpNodeInfo = null;

   // "__sys__cluster.node.domainmapping:heron"
   private boolean inMaster = false; // parsing inside <master> ?
   private NodeMasterInfo tmpMaster = null;

   // "__sys__cluster.node.state:heron"
   private boolean inState = false; // parsing inside <state> ?
   private NodeStateInfo tmpState = null; // Helper variable

   private boolean inDisconnect = false; // parsing inside <disconnect>

   private final SessionInfo sessionInfo;


   /**
    * Parses the XML markup of exactly one cluster node configuration. 
    * @param xml  The XML based ASCII string
    * @param sessionInfo The sessionInfo needs to be passed through to ClusterNode
    */
   public NodeParser(ServerScope glob, ClusterManager clusterManager, String xml, SessionInfo sessionInfo) throws XmlBlasterException {
      this.glob = glob;
      this.clusterManager = clusterManager;
      this.sessionInfo = sessionInfo;
      init(xml);  // use SAX parser to parse it (is slow)
   }

   /**
    * Constructor variant to only parse the &lt;master> part of the XML. 
    * <pre>
    *  &lt;clusternode id='heron.mycomp.com'>" +
    *   &lt;master type='DomainToMaster' version='1.0'>\n" +
    *    &lt;key queryType='DOMAIN' domain='RUGBY'/>\n" +
    *   &lt;/master>\n" +
    *  &lt;/clusternode>\n";
    * </pre>
    * @param glob
    * @param clusterNode
    * @param xml
    * @throws XmlBlasterException
    */
   public NodeParser(ServerScope glob, ClusterNode clusterNode, String xml) throws XmlBlasterException {
      this.glob = glob;
      this.clusterManager = null;
      this.sessionInfo = null;
      this.tmpClusterNode = clusterNode;
      init(xml);  // use SAX parser to parse it (is slow)
   }

   /**
    * Access the parsed ClusterNode object
    */
   public ClusterNode getClusterNode() {
      return this.tmpClusterNode;
   }
   
   /**
    * Characters.
    * The text between two tags, in the following example 'Hello':
    * <key>Hello</key>
    */
   public void characters(char ch[], int start, int length) {
      if (tmpNodeInfo != null) {
         if (this.inConnect) {
            this.tmpNodeInfo.characters(ch, start, length, this.character);
            return;
         }
      }
      super.characters(ch, start, length);
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
                     log.severe("<clusternode> attribute 'id' is missing, ignoring message");
                     throw new RuntimeException("NodeParser: <clusternode> attribute 'id' is missing, ignoring message");
                  }
                  id = id.trim();
                  if (this.clusterManager != null) {
                     this.tmpClusterNode = this.clusterManager.getClusterNode(id);
                     if (this.tmpClusterNode == null) {
                        this.tmpClusterNode = new ClusterNode(glob, new NodeId(id), sessionInfo);
                        this.clusterManager.addClusterNode(this.tmpClusterNode);
                     }
                  }
               }
               return;
            }
         }

         if (inMaster) { // delegate master internal tags
            if (tmpMaster == null) { log.severe("Internal problem in <master> section"); return; }
            tmpMaster.startElement(uri, localName, name, character, attrs);
            return;
         }
         if (!inConnect && name.equalsIgnoreCase("master")) {
            if (inClusternode != 1) return;
            inMaster = true;
            tmpMaster = new NodeMasterInfo(glob, this.tmpClusterNode);
            if (tmpMaster.startElement(uri, localName, name, character, attrs) == true) {
               this.tmpClusterNode.addNodeMasterInfo(tmpMaster);
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
               this.tmpClusterNode.setNodeStateInfo(tmpState);
            else {
               log.severe("Internal problem in <state> section");
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
         
         // <info> is deprecated
         if (name.equalsIgnoreCase("info") || name.equalsIgnoreCase("connect")) {
            if (inClusternode != 1) return;
            inConnect = true;
            tmpNodeInfo = new NodeConnectQos(this.tmpClusterNode.getRemoteGlob(), this.tmpClusterNode.getNodeId());
            if (tmpNodeInfo.startElement(uri, localName, name, character, attrs) == true)
               this.tmpClusterNode.setNodeInfo(tmpNodeInfo);
            else {
               log.severe("Internal problem in <"+name+"> section");
               tmpNodeInfo = null;
            }
            character.setLength(0);
            return;
         }

         if (inClusternode == 1) {
            if (name.equals(MethodName.DISCONNECT.getMethodName())) {
               this.inDisconnect = true;
               return;
            }
         }
      }
      catch (XmlBlasterException e) {
         throw new org.xmlBlaster.util.StopParseException(e);
      }
      catch (Throwable e) {
         XmlBlasterException ex = new XmlBlasterException(glob, ErrorCode.INTERNAL_UNKNOWN, ME, "ClusterNode parser failed", e);
         throw new SAXException("", ex);
      }

      log.warning("startElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
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

      if (inDisconnect) {
         this.inDisconnect = false;
         if (tmpNodeInfo == null) return;
         // TODO: Parse specific disconnect attributes/tags (use xmlScript parser)
         this.tmpNodeInfo.setDisconnectQos(new DisconnectQos(this.tmpNodeInfo.getRemoteGlob()));
         return;
      }

      log.warning("endElement: Ignoring unknown name=" + name + " character='" + character.toString() + "' inClusternode=" + inClusternode);
   }


   /** For testing with JUnit: java org.xmlBlaster.test.cluster.NodeParserTest */
   public static void main(String[] args)
   {
      ServerScope glob = new ServerScope(args);
      glob.setUseCluster(true);
      ClusterManager m = new ClusterManager(glob, null);

      try {
         m.init(glob, null);
         {
             String xml =
            	 "  <clusternode id='heron'>" +
            	 "     <connect><qos>" +
            	 "        <securityService type='htpasswd' version='1.0'>" +
            	 "             <![CDATA[" +
            	 "                <user>testuser</user>" +
            	 "                <passwd>XXXXX</passwd>" +
            	 "             ]]>" +
            	 "        </securityService>" +
            	 "        <persistent/>" +
            	 "           <address type='socket_mobile'>" +
            	 "             socket://avalon:3412" +
            	 "              <compress type='zlib:stream'/>" +
            	 "              <attribute name='doSomething'>true</attribute>" +
            	 "           </address>" +
            	 "           <queue relating='callback' maxEntries='1000' maxBytes='4000000' onOverflow='deadMessage'>" +
                 "              <callback type='IOR' sessionId='4e56890ghdFzj0' pingInterval='10000' retries='-1' delay='10000' oneway='false' dispatcherActive='true' dispatchPlugin='undef'>" +
                 "                 IOR:10000010033200000099000010...." +
                 "                 <burstMode collectTime='400' maxEntries='20' maxBytes='-1' />" +
                 "                 <compress type='gzip' minSize='3000'/>" +
                 "                 <ptp>true</ptp>" +
                 "                 <attribute name='key1' type='int'>2005</attribute>" +
                 "              </callback>" +
                 "           </queue>" +
            	 "     </qos></connect>" +
            	 "  </clusternode>";
                System.out.println("\nmaster message from client ...");
                NodeParser nodeParser = new NodeParser(glob, new ClusterNode(glob, new NodeId("heron"), null), xml);
                System.out.println(nodeParser.getClusterNode().toXml());
             }

         {
         String xml =
            "<clusternode id='heron.mycomp.com'>" +
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
            "</clusternode>\n";

            System.out.println("\nmaster message from client ...");
            NodeParser nodeParser = new NodeParser(glob, new ClusterNode(glob, new NodeId("heron.mycomp.com"), null), xml);
            System.out.println(nodeParser.getClusterNode().toXml());
         }

         {
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
            System.out.println("\nFull Message from client ...");
            NodeParser nodeParser = new NodeParser(glob, glob.getClusterManager(), xml, null);
            System.out.println(nodeParser.getClusterNode().toXml());
         }
      }
      catch(Throwable e) {
         e.printStackTrace();
         log.severe(e.toString());
      }
   }
}
