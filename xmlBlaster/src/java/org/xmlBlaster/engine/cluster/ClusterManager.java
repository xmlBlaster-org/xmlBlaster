/*------------------------------------------------------------------------------
Name:      ClusterManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for clustering
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.jutils.log.LogChannel;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.I_RunlevelListener;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.xml2java.XmlKey;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.engine.xml2java.SubscribeQoS;
import org.xmlBlaster.engine.xml2java.GetQoS;
import org.xmlBlaster.engine.xml2java.EraseQoS;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.client.SubscribeRetQos;
import org.xmlBlaster.client.PublishRetQos;
import org.xmlBlaster.client.EraseRetQos;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;
import org.xmlBlaster.authentication.SessionInfo;

import java.util.Set;
import java.util.TreeSet;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Vector;
import java.util.Iterator;
import java.util.Comparator;

/**
 * The manager instance for a cluster node. 
 * <p />
 * Each xmlBlaster server instance has one instance
 * of this class to manage its behavior in the cluster. 
 * <p />
 * Note: Our own node id is available via glob.getNodeId()
 * <p />
 * See the <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html">cluster requirement</a>
 * for a detailed description.
 * @author ruff@swand.lake.de
 * @since 0.79e
 */
public final class ClusterManager implements I_RunlevelListener
{
   private String ME;

   // The following 3 declarations are 'final' but the SUN JDK 1.3.1 does not like it
   private Global glob;
   private LogChannel log;
   private SessionInfo sessionInfo;

   private MapMsgToMasterPluginManager mapMsgToMasterPluginManager;
   private LoadBalancerPluginManager loadBalancerPluginManager;
   private I_LoadBalancer loadBalancer;

   public String pluginLoadBalancerType;
   public String pluginLoadBalancerVersion;

   /**
    * Map containing ClusterNode objects, the key is a 'node Id'
    * The entries are sorted to contain the local node as first entry.
    */
   private Map clusterNodeMap;

   /** Info about myself */
   private ClusterNode myClusterNode = null;

   private boolean postInitialized = false;

   /**
    * Usually connecting on demand is enough (e.g. connecting when a message needs to be delivered). 
    * <p />
    * If you want to immediately resend tail back messages on server startup we can
    * force to establish the connections to all nodes immediately.<br />
    * The XmlBlasterConnection checks then for tailed back messages which where not yet delivered
    * and sends them.
    */
   private boolean lazyConnect = false;

   /**
    * You need to call postInit() after all drivers are loaded.
    *
    * @param sessionInfo Internal handle to be used directly with RequestBroker
    *                    NOTE: We (the cluster code) are responsible for security checks
    *                    as we directly write into RequestBroker.
    */
   public ClusterManager(Global glob, SessionInfo sessionInfo) {
      this.glob = glob;
      this.sessionInfo = sessionInfo;
      this.log = this.glob.getLog("cluster");
      this.ME = "ClusterManager-" + this.glob.getId();
      glob.addRunlevelListener(this);
   }

   public void postInit() throws XmlBlasterException {
      this.pluginLoadBalancerType = this.glob.getProperty().get("cluster.loadBalancer.type", "RoundRobin");
      this.pluginLoadBalancerVersion = this.glob.getProperty().get("cluster.loadBalancer.version", "1.0");
      this.loadBalancerPluginManager = new LoadBalancerPluginManager(glob, this);
      loadBalancer = loadBalancerPluginManager.getPlugin(
                this.pluginLoadBalancerType, this.pluginLoadBalancerVersion); // "RoundRobin", "1.0"
      if (loadBalancer == null) {
         String tmp = "No load balancer plugin type='" + this.pluginLoadBalancerType + "' version='" + this.pluginLoadBalancerVersion + "' found, clustering switched off";
         log.error(ME, tmp);
         //Thread.currentThread().dumpStack();
         throw new XmlBlasterException("ClusterManager.PluginFailed", tmp); // is caught in RequestBroker.java
      }

      this.clusterNodeMap = new TreeMap(new NodeComparator());
      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(glob, this);

      if (this.glob.getNodeId() == null)
         this.log.error(ME, "Node ID is still unknown, please set '-cluster.node.id' to a unique name.");
      else
         initClusterNode();

      // Look for environment settings to configure startup clustering
      String[] env = { "cluster.node", "cluster.node.info", "cluster.node.master" };
      for (int ii=0; ii<env.length; ii++) {
         Map nodeMap = glob.getProperty().get(env[ii], (Map)null);
         if (nodeMap != null) {
            Iterator iter = nodeMap.keySet().iterator();
            if (log.TRACE) log.trace(ME, "Found -" + env[ii] + " with " + nodeMap.size() + " array size, ii=" + ii);
            while (iter.hasNext()) {
               String nodeIdName = (String)iter.next();       // e.g. "heron" from "cluster.node.master[heron]=..."
               String xml = (String)nodeMap.get(nodeIdName);  // The "<clusternode>..." xml ASCII string for heron
               if (xml == null || xml.length() < 1) {
                  log.info(ME, "Ignoring envrionment setting -" + env[ii]);
                  continue;
               }
               if (log.TRACE) log.trace(ME, "Parsing envrionment -" + env[ii] + " for node '" + nodeIdName + "' ...");
               NodeParser nodeParser = new NodeParser(glob, this, xml, sessionInfo); // fills the info to ClusterManager
               log.info(ME, "Envrionment for node '" + nodeIdName + "' parsed.");
            }
         }
      }

      publish();

      subscribe();

      if (log.DUMP) log.dump(ME, toXml());
      this.log.info(ME, "Initialized and ready");
      postInitialized = true;

      if (!lazyConnect)
         initConnections();
   }

   /**
    * TODO: not implemented yet
    * You can't currently configure the cluster setup with messages, only statically
    * on startup
    */
   private void publish() {
      if (log.TRACE) log.trace(ME, "publish() of cluster internal messages is missing");
   /*
      StringBuffer buf = new StringBuffer(256);
      buf.append("<key oid='").append(Constants.OID_CLUSTER_INFO).append("[").append(getId()).append("]").append("'><").append(Constants.OID_CLUSTER_INFO)("/></key>");
      msgUnit.setKey(buf.toString());
      msgUnit.setQos(pubQos.toXml());
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);
      retArr[ii] = publish(unsecureSessionInfo, xmlKey, msgUnit, new PublishQos(glob, msgUnit.getQos()));
   */
   }

   /**
    * TODO: not implemented yet
    * You can't currently configure the cluster setup with messages, only statically
    * on startup
    */
   private void subscribe() {
      if (log.TRACE) log.trace(ME, "subscribe() of cluster internal messages is missing");
   }

   /**
    * Initialize ClusterNode object, containing all informations about myself. 
    */
   private void initClusterNode() {
      this.myClusterNode = new ClusterNode(this.glob, this.glob.getNodeId(), this.sessionInfo);
      this.addClusterNode(this.myClusterNode);
      I_Driver[] drivers = glob.getPublicProtocolDrivers();
      for (int ii=0; ii<drivers.length; ii++) {
         I_Driver driver = drivers[ii];
         Address addr = new Address(glob, driver.getProtocolId(), glob.getId());
         addr.setAddress(driver.getRawAddress());
         this.myClusterNode.getNodeInfo().addAddress(addr);
      }

      if (drivers.length > 0) {
         if (log.TRACE) log.trace(ME, "Setting " + drivers.length + " addresses for cluster node '" + getId() + "'");
      }
      else
         log.error(ME, "ClusterNode is not properly initialized, no local xmlBlaster (node=" + getId() + ") address available");
   }

   /**
    * Check if supplied address would connect to our own node. 
    */
   public final boolean isLocalAddress(Address other) {
      return getMyClusterNode().getNodeInfo().contains(other);
   }

   /**
    * Return myself
    */
   public ClusterNode getMyClusterNode() {
      return this.myClusterNode;
   }

   /**
    * Access the unique cluster node id (as NodeId object). 
    */
   public final NodeId getNodeId() {
      return glob.getNodeId();
   }

   /**
    * Access the unique cluster node id (as a String). 
    * @return The name of this xmlBlaster instance, e.g. "heron.mycompany.com"
    */
   public final String getId() {
      return glob.getId();
   }

   /**
    * The plugin loader instance to map messages to their master node. 
    */
   public MapMsgToMasterPluginManager getMapMsgToMasterPluginManager() {
      return this.mapMsgToMasterPluginManager;
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal publish return value of the remote cluster node and the responsible
    *         NodeDomainInfo instance.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public PublishRetQosWrapper forwardPublish(SessionInfo publisherSession, MessageUnitWrapper msgWrapper) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering forwardPublish(" + msgWrapper.getUniqueKey() + ")");
      NodeDomainInfo nodeDomainInfo = getConnection(publisherSession, msgWrapper);
      if (nodeDomainInfo == null)
         return null;
      XmlBlasterConnection con =  nodeDomainInfo.getClusterNode().getXmlBlasterConnection();
      if (con == null)
         return null;

      PublishQos publishQos = msgWrapper.getPublishQos();
      if (nodeDomainInfo.getDirtyRead() == true) {
         // mark QoS of published message that we dirty read the message:
         RouteInfo[] ris = publishQos.getRouteNodes();
         if (ris == null || ris.length < 1) {
            log.error(ME, "The route info for '" + msgWrapper.getUniqueKey() + "' is missing");
            Thread.currentThread().dumpStack();
         }
         else {
            ris[ris.length-1].setDirtyRead(true);
         }
      }
      MessageUnit msgUnit = msgWrapper.getMessageUnit();
      msgUnit.setQos(publishQos.toXml());

      return new PublishRetQosWrapper(nodeDomainInfo, con.publish(msgUnit));
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]'/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The message will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal publish return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public SubscribeRetQos forwardSubscribe(SessionInfo publisherSession, XmlKey xmlKey, SubscribeQoS subscribeQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering forwardSubscribe(" + xmlKey.getUniqueKey() + ")");

      MessageUnitWrapper msgWrapper = new MessageUnitWrapper(glob.getRequestBroker(), xmlKey,
                                      new MessageUnit(xmlKey.literal(), new byte[0], subscribeQos.toXml()),
                                      /*!!! subscribeQos.toXml()*/ new PublishQos(glob, ""));
      NodeDomainInfo nodeDomainInfo = getConnection(publisherSession, msgWrapper);
      if (nodeDomainInfo == null)
         return null;
      XmlBlasterConnection con =  nodeDomainInfo.getClusterNode().getXmlBlasterConnection();
      if (con == null) {
         if (log.TRACE) log.trace(ME, "forwardSubscribe - Nothing to forward");
         return null;
      }

      return con.subscribe(xmlKey.literal(), subscribeQos.toXml());
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         msgUnit.length==0 if message is
    *         tailed back because cluster node is temporary not available. The command will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal get return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public MessageUnit[] forwardGet(SessionInfo publisherSession, XmlKey xmlKey, GetQoS getQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering forwardGet(" + xmlKey.getUniqueKey() + ")");

      MessageUnitWrapper msgWrapper = new MessageUnitWrapper(glob.getRequestBroker(), xmlKey,
                                      new MessageUnit(xmlKey.literal(), new byte[0], getQos.toXml()),
                                      /*!!! getQos.toXml()*/ new PublishQos(glob, ""));
      NodeDomainInfo nodeDomainInfo = getConnection(publisherSession, msgWrapper);
      if (nodeDomainInfo == null)
         return null;
      XmlBlasterConnection con =  nodeDomainInfo.getClusterNode().getXmlBlasterConnection();
      if (con == null) {
         if (log.TRACE) log.trace(ME, "forwardGet - Nothing to forward");
         return null;
      }

      return con.get(xmlKey.literal(), getQos.toXml());
   }

   /**
    * @return null if no forwarding is done, if we are the master of this message ourself<br />
    *         <pre>&lt;qos>&lt;state id='OK' info='QUEUED[bilbo]/>&lt;/qos></pre> if message is
    *         tailed back because cluster node is temporary not available. The command will
    *         be flushed on reconnect.<br />
    *         Otherwise the normal erase return value of the remote cluster node.  
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public EraseRetQos[] forwardErase(SessionInfo publisherSession, XmlKey xmlKey, EraseQoS eraseQos) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering forwardErase(" + xmlKey.getUniqueKey() + ")");

      MessageUnitWrapper msgWrapper = new MessageUnitWrapper(glob.getRequestBroker(), xmlKey,
                                      new MessageUnit(xmlKey.literal(), new byte[0], eraseQos.toXml()),
                                      /*!!! eraseQos.toXml()*/ new PublishQos(glob, ""));
      NodeDomainInfo nodeDomainInfo = getConnection(publisherSession, msgWrapper);
      if (nodeDomainInfo == null)
         return null;
      XmlBlasterConnection con =  nodeDomainInfo.getClusterNode().getXmlBlasterConnection();
      if (con == null) {
         if (log.TRACE) log.trace(ME, "forwardErase - Nothing to forward");
         return null;
      }

      return con.erase(xmlKey.literal(), eraseQos.toXml());
   }

   /**
    * Add a new node info object or overwrite an existing one. 
    * @param The ClusterNode instance
    * @exception  IllegalArgumentException
    */
   public final void addClusterNode(ClusterNode clusterNode) {
      if (clusterNode == null || clusterNode.getNodeId() == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "Illegal argument in addClusterNode()");
         throw new IllegalArgumentException("Illegal argument in addClusterNode()");
      }
      this.clusterNodeMap.put(clusterNode.getId(), clusterNode);
   }

   /**
    * Return the map containing all known cluster nodes. 
    * @return never null, map contains ClusterNode objects, please treat as read only.
    */
   public Map getClusterNodeMap() {
      return this.clusterNodeMap;
   }

   /**
    * Access the informations belonging to a node id
    * @return The ClusterNode instance or null if unknown
    */
   public final ClusterNode getClusterNode(NodeId nodeId) {
      return getClusterNode(nodeId.getId());
   }

   /**
    * Access the informations belonging to a node id
    * @param The cluster node id as a string
    * @return The ClusterNode instance or null if unknown
    */
   public final ClusterNode getClusterNode(String id) {
      return (ClusterNode)this.clusterNodeMap.get(id);
   }

   /*
   public final void addConnection(NodeId nodeId, XmlBlasterConnection connection) throws XmlBlasterException {
      ClusterNode info = getClusterNode(nodeId);
      if (info == null)
         throw new XmlBlasterException(ME, "Unknown node id = " + nodeId.toString() + ", can't add xmlBlasterConnection");
      info.setXmlBlasterConnection(connection);
   }

   public final void removeConnection(NodeId nodeId) {
      ClusterNode info = getClusterNode(nodeId);
      if (info == null) {
         log.error(ME, "Unknown node id = " + nodeId.toString() + ", can't remove xmlBlasterConnection");
         return;
      }
      info.resetXmlBlasterConnection();
   }
   */

   /**
    * Usually the connection is established on demand (a message wants to travel to a node). 
    * <p />
    * Here you can force to establish connections to all known cluster nodes.
    */
   private void initConnections() throws XmlBlasterException {
      Iterator it = getClusterNodeMap().values().iterator();
      // for each cluster node ...
      while (it.hasNext()) {
         ClusterNode clusterNode = (ClusterNode)it.next();
         // force a connect (not allowed and local node are checked to do nothing) ...
         clusterNode.getXmlBlasterConnection();    // should we check for Exception and proceed with other nodes ?
      }
   }

   /**
    * Get connection to the master node (or a node at a closer stratum to the master). 
    * @return null if local node, otherwise access other node with <code>nodeDomainInfo.getClusterNode().getXmlBlasterConnection()</code>
    */
   public final NodeDomainInfo getConnection(SessionInfo publisherSession, MessageUnitWrapper msgWrapper) throws XmlBlasterException {
      if (!postInitialized) {
         // !!! we need proper run level initialization
         if (log.TRACE) log.trace(ME, "Entering getConnection(" + msgWrapper.getUniqueKey() + "), but clustering is not ready, handling in local node");
         return null;
      }

      if (log.CALL) log.call(ME, "Entering getConnection(" + msgWrapper.getUniqueKey() + "), testing " + getClusterNodeMap().size() + " known cluster nodes ...");

      if (msgWrapper.getXmlKey().getUniqueKey().startsWith(Constants.INTERNAL_OID_PRAEFIX)) {
         // internal system messages are handled locally
         if (msgWrapper.getXmlKey().getUniqueKey().startsWith(Constants.INTERNAL_OID_CLUSTER_PRAEFIX))
            log.error(ME, "Forwarding of " + msgWrapper.getXmlKey().getUniqueKey() + " implementation is missing");
            // !!! TODO: forward system messages with cluster info of foreign nodes!
         return null;
      }

      // Search all other cluster nodes to find the masters of this message ...
      // NOTE: If no filters are used, the masterSet=f(msgWrapper) could be cached for performance gain
      //       Cache implementation is currently missing

      Set masterSet = new TreeSet(); // Contains the NodeDomainInfo objects which match this message
                                     // Sorted by stratum (0 is the first entry) -> see NodeDomainInfo.compareTo
      int numRulesFound = 0;                             // For nicer logging of warnings

      PublishQos publishQos = msgWrapper.getPublishQos();
      if (publishQos.count(glob.getNodeId()) > 1) { // Checked in RequestBroker as well with warning
         log.warn(ME, "Warning, message oid='" + msgWrapper.getXmlKey().getUniqueKey() +
            "' passed my node id='" + glob.getId() + "' before, we have a circular routing problem, keeping message locally");
         return null;
      }

      Iterator it = getClusterNodeMap().values().iterator();
      // for each cluster node ...
      while (it.hasNext()) {
         ClusterNode clusterNode = (ClusterNode)it.next();
         if (clusterNode.getDomainInfoMap().size() < 1)
            continue;
         if (clusterNode.isAllowed() == false) {
            if (log.TRACE) log.trace(ME, "Ignoring master node id='" + clusterNode.getId() + "' because it is not available");
            continue;
         }
         if (!clusterNode.isLocalNode() && publishQos.count(clusterNode.getNodeId()) > 0) {
            if (log.TRACE) log.trace(ME, "Ignoring node id='" + clusterNode.getId() + "' for routing, message oid='" + msgWrapper.getXmlKey().getUniqueKey() +
               "' has been there already");
            continue;
         }
         Iterator domains = clusterNode.getDomainInfoMap().values().iterator();
         if (log.TRACE) log.trace(ME, "Testing " + clusterNode.getDomainInfoMap().size() + " domains rules of node " + clusterNode.getId() + " for oid=" + msgWrapper.getUniqueKey());
         numRulesFound += clusterNode.getDomainInfoMap().size();
         // for each domain mapping rule ...
         while (domains.hasNext()) {
            NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)domains.next();
            I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId(
                                 nodeDomainInfo.getType(), nodeDomainInfo.getVersion(), // "DomainToMaster", "1.0"
                                 msgWrapper.getContentMime(), msgWrapper.getContentMimeExtended());
            if (domainMapper == null) {
               log.warn(ME, "No domain mapping plugin type='" + nodeDomainInfo.getType() + "' version='" + nodeDomainInfo.getVersion() +
                              "' found for message mime='" + msgWrapper.getContentMime() + "' and '" + msgWrapper.getContentMimeExtended() +
                              "' ignoring rules " + nodeDomainInfo.toXml());
               continue;
            }

            // Now invoke the plugin to find out who is the master ...
            nodeDomainInfo = domainMapper.getMasterId(nodeDomainInfo, msgWrapper);
            if (nodeDomainInfo != null) {
               masterSet.add(nodeDomainInfo);
               break; // found one
            }
         }
      }

      if (masterSet.size() < 1) {
         if (numRulesFound == 0) {
            if (log.TRACE) log.trace(ME, "Using local node for message, no master mapping rules are known.");
         }
         else {
            log.info(ME, "No master found for message oid='" + msgWrapper.getUniqueKey() + "' mime='" + msgWrapper.getContentMime() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "', using local node.");
         }
         return null;
      }
      if (masterSet.size() > 1) {
         if (log.TRACE) log.trace(ME, masterSet.size() + " masters found for message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
      }

      NodeDomainInfo nodeDomainInfo = loadBalancer.getClusterNode(masterSet); // Invoke for masterSet.size()==1 as well, the balancer may choose to ignore it

      /*
      if (nodeDomainInfo == null) {
         log.error(ME, "Message '" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'" +
                   "has no master, message is lost (implementation to handle this case is missing)!");
         return null;
      }
      */
      if (nodeDomainInfo == null || nodeDomainInfo.getClusterNode().isLocalNode()) {
         if (log.TRACE) log.trace(ME, "Using local node '" + getMyClusterNode().getId() + "' as master for message oid='"
               + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
         if (log.DUMP) log.dump(ME, "Received message at master node: " + msgWrapper.getPublishQos().toXml());
         return null;
      }
      else {
         if (log.TRACE) log.info(ME, "Using master node '" + nodeDomainInfo.getClusterNode().getId() + "' for message oid='"
               + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
      }

      return nodeDomainInfo;
   }

   public final XmlBlasterConnection getConnection(NodeId nodeId) {
      log.error(ME, "getConnection() is not implemented");
      return null;
      /*
      ClusterNode clusterNode = getClusterNode(nodeId);
      return (XmlBlasterConnection)connectionMap.get(nodeId.getId());
      */
   }

   public void shutdown(boolean force) {
      if (clusterNodeMap != null && clusterNodeMap.size() > 0) {
         Iterator it = clusterNodeMap.values().iterator();
         while (it.hasNext()) {
            ClusterNode info = (ClusterNode)it.next();
            info.shutdown(force);
         }
      }
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml() {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset) {
      StringBuffer sb = new StringBuffer(1024);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<clusterManager>");
      if (clusterNodeMap != null && clusterNodeMap.size() > 0) {
         Iterator it = clusterNodeMap.values().iterator();
         while (it.hasNext()) {
            ClusterNode info = (ClusterNode)it.next();
            sb.append(info.toXml(extraOffset + "   "));
         }
      }
      sb.append(offset).append("</clusterManager>");

      return sb.toString();
   }

   /**
    * Sorts the cluster nodes for the clusterNodeMap
    * <ol>
    *   <li>First is the local node</li>
    *   <li>Others by node id</li>
    * </ol>
    */
   class NodeComparator implements Comparator
   {
      /**
       * We compare the cluster node id string. 
       */
      public final int compare(Object o1, Object o2) {
         String id1 = (String)o1;
         String id2 = (String)o2;
         //log.info("NodeComparator", "Compare " + id1 + " to " + id2);
         if (id1.equals(id2))
            return 0;
         if (id1.equals(glob.getId())) // id1 is local node
            return -1;
         if (id2.equals(glob.getId())) // id2 is local node
            return 1;
         return id1.compareTo(id2);
      }
   }

   /**
    * Sorts the cluster nodes for the masterSet
    * <ol>
    *   <li>First is the local node</li>
    *   <li>Others by node id</li>
    * </ol>
    */
    /*
   class MasterNodeComparator implements Comparator
   {

      public final int compare(Object o1, Object o2) {
         NodeDomainInfo id1 = (NodeDomainInfo)o1;
         NodeDomainInfo id2 = (NodeDomainInfo)o2;
         //log.info("MasterNodeComparator", "Compare " + id1 + " to " + id2);

         if (id1.equals(id2))
            return 0;
         if (id1.equals(glob.getId())) // id1 is local node
            return -1;
         if (id2.equals(glob.getId())) // id2 is local node
            return 1;
         return id1.compareTo(id2);
      }
   }  */

   /**
    * A human readable name of the listener for logging. 
    * <p />
    * Enforced by I_RunlevelListener
    */
   public String getName() {
      return ME;
   }

   /**
    * Invoked on run level change, see Constants.RUNLEVEL_HALTED and Constants.RUNLEVEL_RUNNING
    * <p />
    * Enforced by I_RunlevelListener
    */
   public void runlevelChange(int from, int to, boolean force) throws org.xmlBlaster.util.XmlBlasterException {
      if (to < from) {
         if (to == Constants.RUNLEVEL_HALTED) {
            shutdown(force);
         }
      }
   }


   /**
    * Command line usage.
    * <p />
    * These variables may be set in your property file as well.
    * Don't use the "-" prefix there.
    * <p />
    * Set the verbosity when loading properties (outputs with System.out).
    * <p />
    * 0=nothing, 1=info, 2=trace, configure with
    * <pre>
    * java -Dproperty.verbose 2 ...
    *
    * java org.xmlBlaster.Main -property.verbose 2
    * </pre>
    */
   public static String usage()
   {
      StringBuffer sb = new StringBuffer(512);
      sb.append("Cluster support:\n");
      sb.append("   -cluster            Switch cluster support to true or false [true].\n");
      sb.append("   -cluster.node.id    A unique name for this xmlBlaster instance, e.g. 'com.myCompany.myHost'.\n");
      sb.append("                       If not specified a unique name is choosen and displayed on command line.\n");
      sb.append("   ...                 See http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html\n");
      return sb.toString();
   }
} // class ClusterManager

