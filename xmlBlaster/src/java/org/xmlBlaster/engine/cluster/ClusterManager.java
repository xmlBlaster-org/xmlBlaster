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

import java.util.Map;
import java.util.HashMap;
import java.util.Vector;

/**
 * The manager instance for a cluster node. 
 * <p />
 * Each xmlBlaster server instance has one instance
 * of this class to manage its behavior in the cluster. 
 * <p />
 * Note: Our own node id is available via glob.getNodeId()
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

   public final String pluginDomainMapperType;
   public final String pluginDomainMapperVersion;

   public final String pluginLoadBalancerType;
   public final String pluginLoadBalancerVersion;

   /**  Map containing NodeInfo objects, the key is a 'node Id' */
   private Map nodeInfoMap;

   /** Info about myself */
   private NodeInfo myNodeInfo = null;

   public ClusterManager(Global glob) {
      this.glob = glob;
      this.log = this.glob.getLog();

      this.pluginDomainMapperType = this.glob.getProperty().get("cluster.domainMapper.type", "DomainToMaster");
      this.pluginDomainMapperVersion = this.glob.getProperty().get("cluster.domainMapper.version", "1.0");
      this.pluginLoadBalancerType = this.glob.getProperty().get("cluster.loadBalancer.type", "RoundRobin");
      this.pluginLoadBalancerVersion = this.glob.getProperty().get("cluster.loadBalancer.version", "1.0");
      this.nodeInfoMap = new HashMap();
      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(glob);
      this.loadBalancerPluginManager = new LoadBalancerPluginManager(glob);

      if (this.glob.getNodeId() == null)
         this.log.error(ME, "Node ID is still unknown");
      else
         initNodeInfo();
      this.log.info(ME, "Initialized and ready");
   }

   /**
    * Initialize NodeInfo object, containing all informations about myself. 
    */
   private void initNodeInfo() {
      this.myNodeInfo = new NodeInfo(this.glob.getNodeId());
      this.addNodeInfo(this.myNodeInfo);
      Vector protocols = glob.getProtocolDrivers();
      if (protocols.size() > 0) {
         Address[] addresses = new Address[protocols.size()];
         for (int ii=0; ii<protocols.size(); ii++) {
            I_Driver driver = (I_Driver)protocols.elementAt(ii);
            addresses[ii] = new Address(driver.getProtocolId(), driver.getRawAddress());
         }
         this.myNodeInfo.setAddresses(addresses);
         log.info(ME, "Setting " + addresses.length + " addresses for cluster node '" + getNodeName() + "'");
      }
      else {
         log.error(ME, "NodeInfo is not properly initialized, no local xmlBlaster address available");
      }
   }

   /**
    * Access the unique cluster node id (as NodeId object). 
    */
   public final NodeId getNodeId() {
      return glob.getNodeId();
   }

   /**
    * Access the unique cluster node id (as a String). 
    */
   public final String getNodeName() {
      return getNodeId().getId();
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
   public String forwardPublish(SessionInfo publisherSession, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      XmlBlasterConnection con = getConnection(publisherSession, msgUnitWrapper);
      if (con == null)
         return null;

      PublishQos publishQos = msgUnitWrapper.getPublishQos();
      MessageUnit msgUnit = msgUnitWrapper.getMessageUnit();

      return con.publish(msgUnit);
      /*
      }
      catch (XmlBlasterException e) {
         Log.error(ME, "Problems with clustering of published message '" + msgUnitWrapper.getUniqueKey() + "'");
         e.printStackTrace();
         return Constants.RET_FORWARD_ERROR; // "<qos><state>FORWARD_ERROR</state></qos>"
      }
      */
   }

   /**
    * Add a new node info object or overwrite an existing one. 
    * @param The NodeInfo instance
    * @exception  IllegalArgumentException
    */
   public final void addNodeInfo(NodeInfo nodeInfo) {
      if (nodeInfo == null || nodeInfo.getNodeId() == null) {
         Thread.currentThread().dumpStack();
         log.error(ME, "Illegal argument in addNodeInfo()");
         throw new IllegalArgumentException("Illegal argument in addNodeInfo()");
      }
      this.nodeInfoMap.put(nodeInfo.getNodeId().getId(), nodeInfo);
   }

   /**
    * Access the informations belonging to a node id
    * @return The NodeInfo instance or null if unknown
    */
   public final NodeInfo getNodeInfo(NodeId nodeId) {
      return (NodeInfo)this.nodeInfoMap.get(nodeId.getId());
   }

   public final void addConnection(NodeId nodeId, XmlBlasterConnection connection) throws XmlBlasterException {
      NodeInfo info = getNodeInfo(nodeId);
      if (info == null)
         throw new XmlBlasterException(ME, "Unknown node id = " + nodeId.toString() + ", can't add xmlBlasterConnection");
      info.setXmlBlasterConnection(connection);
   }

   public final void removeConnection(NodeId nodeId) {
      NodeInfo info = getNodeInfo(nodeId);
      if (info == null) {
         Log.error(ME, "Unknown node id = " + nodeId.toString() + ", can't remove xmlBlasterConnection");
         return;
      }
      info.resetXmlBlasterConnection();
   }

   /**
    * @return null if plugins not found
    */
   public final XmlBlasterConnection getConnection(SessionInfo publisherSession, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {

      I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId(
                         this.pluginDomainMapperType, this.pluginDomainMapperVersion, // "DomainToMaster", "1.0"
                         msgUnitWrapper.getContentMime(), msgUnitWrapper.getContentMimeExtended());
      if (domainMapper == null) {
         Log.warn(ME, "No domain mapping plugin found, clustering switched off");
         return null;
      }

      NodeId nodeId = domainMapper.getMasterId(msgUnitWrapper);
      NodeInfo nodeInfo = getNodeInfo(nodeId);

      I_LoadBalancer balancer = loadBalancerPluginManager.getPlugin(
                this.pluginLoadBalancerType, this.pluginLoadBalancerVersion); // "RoundRobin", "1.0"
      if (balancer == null) {
         Log.warn(ME, "No load balancer plugin found, clustering switched off");
         return null;
      }

      /* !!!
      XmlBlasterConnection con = balancer.getConnection(nodeInfo);
      XmlBlasterConnection con = (XmlBlasterConnection)connectionMap.get(nodeId.getId());
      if (con != null)
         return con;
      */
      return null;
   }

   public final XmlBlasterConnection getConnection(NodeId nodeId) {
      Log.error(ME, "getConnection() is not implemented");
      return null;
      /*
      NodeInfo nodeInfo = getNodeInfo(nodeId);
      return (XmlBlasterConnection)connectionMap.get(nodeId.getId());
      */
   }
}
