/*------------------------------------------------------------------------------
Name:      ClusterManager.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Main manager class for clustering
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Log;
import org.xmlBlaster.util.XmlBlasterException;
import org.xmlBlaster.engine.Global;
import org.xmlBlaster.engine.MessageUnitWrapper;
import org.xmlBlaster.engine.helper.Address;
import org.xmlBlaster.engine.helper.MessageUnit;
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.engine.xml2java.PublishQos;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.protocol.I_Driver;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

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
public final class ClusterManager
{
   private final String ME = "ClusterManager";

   private final Global glob;
   private final Log log;

   private final MapMsgToMasterPluginManager mapMsgToMasterPluginManager;
   private final LoadBalancerPluginManager loadBalancerPluginManager;
   private final I_LoadBalancer loadBalancer;

   public final String pluginLoadBalancerType;
   public final String pluginLoadBalancerVersion;

   /**
    * Map containing ClusterNode objects, the key is a 'node Id'
    * The entries are sorted to contain the local node as first entry.
    */
   private Map clusterNodeMap;

   /** Info about myself */
   private ClusterNode myClusterNode = null;

   public ClusterManager(Global glob) throws XmlBlasterException {
      this.glob = glob;
      this.log = this.glob.getLog();

      this.pluginLoadBalancerType = this.glob.getProperty().get("cluster.loadBalancer.type", "RoundRobin");
      this.pluginLoadBalancerVersion = this.glob.getProperty().get("cluster.loadBalancer.version", "1.0");
      this.loadBalancerPluginManager = new LoadBalancerPluginManager(glob);
      loadBalancer = loadBalancerPluginManager.getPlugin(
                this.pluginLoadBalancerType, this.pluginLoadBalancerVersion); // "RoundRobin", "1.0"
      if (loadBalancer == null) {
         String tmp = "No load balancer plugin type='" + this.pluginLoadBalancerType + "' version='" + this.pluginLoadBalancerVersion + "' found, clustering switched off";
         Log.error(ME, tmp);
         //Thread.currentThread().dumpStack();
         throw new XmlBlasterException("ClusterManager.PluginFailed", tmp); // is caught in RequestBroker.java
      }

      this.clusterNodeMap = new TreeMap(new NodeComparator());
      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(glob, this);

      if (this.glob.getNodeId() == null)
         this.log.error(ME, "Node ID is still unknown");
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
               log.info(ME, "Parsing envrionment -" + env[ii] + " for node '" + nodeIdName + "' ...");
               NodeParser nodeParser = new NodeParser(glob, this, xml); // fills the info to ClusterManager
            }
         }
      }

      publish();

      subscribe();

      if (log.DUMP) log.dump(ME, toXml());
      this.log.info(ME, "Initialized and ready");
   }

   private void publish() {
      log.warn(ME, "publish() of cluster messages is missing");
   /*
      StringBuffer buf = new StringBuffer(256);
      buf.append("<key oid='").append(Constants.OID_CLUSTER_INFO).append("[").append(getId()).append("]").append("'><").append(Constants.OID_CLUSTER_INFO)("/></key>");
      msgUnit.setKey(buf.toString());
      msgUnit.setQos(pubQos.toXml());
      XmlKey xmlKey = new XmlKey(msgUnit.getXmlKey(), true);
      retArr[ii] = publish(unsecureSessionInfo, xmlKey, msgUnit, new PublishQos(msgUnit.getQos()));
   */
   }

   private void subscribe() {
      log.warn(ME, "subscribe() of cluster messages is missing");
   }

   /**
    * Initialize ClusterNode object, containing all informations about myself. 
    */
   private void initClusterNode() {
      this.myClusterNode = new ClusterNode(this.glob, this.glob.getNodeId());
      this.addClusterNode(this.myClusterNode);
      I_Driver[] drivers = glob.getPublicProtocolDrivers();
      for (int ii=0; ii<drivers.length; ii++) {
         I_Driver driver = drivers[ii];
         this.myClusterNode.getNodeInfo().addAddress(new Address(driver.getProtocolId(), driver.getRawAddress()));
      }

      if (drivers.length > 0)
         log.info(ME, "Setting " + drivers.length + " addresses for cluster node '" + getId() + "'");
      else
         log.error(ME, "ClusterNode is not properly initialized, no local xmlBlaster address available");
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
    * @return null if no forwarding is done, if we are the master of this message ourself.
    * @exception XmlBlasterException and RuntimeExceptions are just forwarded to the caller
    */
   public String forwardPublish(SessionInfo publisherSession, MessageUnitWrapper msgWrapper) throws XmlBlasterException {
      if (log.CALL) log.call(ME, "Entering forwardPublish(" + msgWrapper.getUniqueKey() + ")");
      XmlBlasterConnection con = getConnection(publisherSession, msgWrapper);
      if (con == null)
         return null;

      PublishQos publishQos = msgWrapper.getPublishQos();
      MessageUnit msgUnit = msgWrapper.getMessageUnit();

      return con.publish(msgUnit);
      /*
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Problems with clustering of published message '" + msgWrapper.getUniqueKey() + "'");
         e.printStackTrace();
         return Constants.RET_FORWARD_ERROR; // "<qos><state>FORWARD_ERROR</state></qos>"
      }
      */
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
         Log.error(ME, "Unknown node id = " + nodeId.toString() + ", can't remove xmlBlasterConnection");
         return;
      }
      info.resetXmlBlasterConnection();
   }
   */

   /**
    * Get connection to the master node (or a node at a closer stratum to the master). 
    * @return null if local node
    */
   public final XmlBlasterConnection getConnection(SessionInfo publisherSession, MessageUnitWrapper msgWrapper) throws XmlBlasterException {

      if (log.CALL) log.call(ME, "Entering getConnection(" + msgWrapper.getUniqueKey() + "), testing " + getClusterNodeMap().size() + " known cluster nodes ...");

      if (msgWrapper.getXmlKey().getUniqueKey().startsWith(Constants.INTERNAL_OID_PRAEFIX)) {
         // internal system messages are handled locally
         if (msgWrapper.getXmlKey().getUniqueKey().startsWith(Constants.INTERNAL_OID_CLUSTER_PRAEFIX))
            glob.getLog().error(ME, "Forwarding of " + msgWrapper.getXmlKey().getUniqueKey() + " implementation is missing");
            // !!! TODO: forward system messages with cluster info of foreign nodes!
         return null;
      }

      // Search all other cluster nodes to find the masters of this message ...

      Set masterSet = new TreeSet(); // Contains the NodeDomainInfo objects which match this message
                                     // Sorted by stratum (0 is the first entry) -> see NodeDomainInfo.compareTo
      int numRulesFound = 0;                             // For nicer logging of warnings

      Iterator it = getClusterNodeMap().values().iterator();
      // for each cluster node ...
      while (it.hasNext()) {
         ClusterNode clusterNode = (ClusterNode)it.next();
         Iterator domains = clusterNode.getDomainInfoMap().values().iterator();
         if (log.TRACE) log.trace(ME, "Testing " + clusterNode.getDomainInfoMap().size() + " domains rules of node " + clusterNode.getId());
         numRulesFound += clusterNode.getDomainInfoMap().size();
         // for each domain mapping rule ...
         while (domains.hasNext()) {
            NodeDomainInfo nodeDomainInfo = (NodeDomainInfo)domains.next();
            I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId(
                               nodeDomainInfo.getType(), nodeDomainInfo.getVersion(), // "DomainToMaster", "1.0"
                               msgWrapper.getContentMime(), msgWrapper.getContentMimeExtended());
            if (domainMapper == null) {
               Log.warn(ME, "No domain mapping plugin type='" + nodeDomainInfo.getType() + "' version='" + nodeDomainInfo.getVersion() + "' found, ignoring rule '" + nodeDomainInfo.getQuery() +
                            "' for message mime='" + msgWrapper.getContentMime() + "' and '" + msgWrapper.getContentMimeExtended() + "'");
               continue;
            }

            // Now invoke the plugin to find out who is the master ...
            ClusterNode master = domainMapper.getMasterId(nodeDomainInfo, msgWrapper);
            if (master != null) {
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
            Log.warn(ME, "No master found for message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
         }
         return null;
      }
      if (masterSet.size() > 1) {
         Log.info(ME, masterSet.size() + " masters found for message oid='" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");
      }

      ClusterNode clusterNode = loadBalancer.getClusterNode(masterSet);

      if (clusterNode == null) {
         log.error(ME, "Message '" + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'" +
                   "has no master, message is lost (implementation to handle this case is missing)!");
         return null;
      }

      log.info(ME, "Using master node '" + clusterNode.getId() + "' for message oid='"
               + msgWrapper.getUniqueKey() + "' domain='" + msgWrapper.getXmlKey().getDomain() + "'");

      return clusterNode.getXmlBlasterConnection();
   }

   public final XmlBlasterConnection getConnection(NodeId nodeId) {
      Log.error(ME, "getConnection() is not implemented");
      return null;
      /*
      ClusterNode clusterNode = getClusterNode(nodeId);
      return (XmlBlasterConnection)connectionMap.get(nodeId.getId());
      */
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
         //Log.info("NodeComparator", "Compare " + id1 + " to " + id2);
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
         //Log.info("MasterNodeComparator", "Compare " + id1 + " to " + id2);

         if (id1.equals(id2))
            return 0;
         if (id1.equals(glob.getId())) // id1 is local node
            return -1;
         if (id2.equals(glob.getId())) // id2 is local node
            return 1;
         return id1.compareTo(id2);
      }
   }  */
}

