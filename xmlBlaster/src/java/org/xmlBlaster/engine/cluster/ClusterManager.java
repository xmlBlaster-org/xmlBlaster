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
import org.xmlBlaster.engine.helper.Constants;
import org.xmlBlaster.authentication.SessionInfo;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import java.util.Map;
import java.util.HashMap;

/**
 * The manager instance for a cluster node. 
 * Each xmlBlaster server instance has one instance
 * of this class to manage its behaviour in the cluster. 
 * @author ruff@swand.lake.de
 * @since 0.79e
 */
public final class ClusterManager
{
   private final String ME = "DomainToMaster";

   private final Global glob;
   private final Log log;

   private final MapMsgToMasterPluginManager mapMsgToMasterPluginManager;
   private final LoadBalancerPluginManager loadBalancerPluginManager;

   /**  Map containing NodeInfo objects, the key is a 'node Id' */
   private Map nodeInfoMap;

   public ClusterManager(Global glob) {
      this.glob = glob;
      this.log = this.glob.getLog();
      this.nodeInfoMap = new HashMap();
      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(glob);
      this.loadBalancerPluginManager = new LoadBalancerPluginManager(glob);
   }

   /**
    * The plugin loader instance to map messages to their master node. 
    */
   public MapMsgToMasterPluginManager getMapMsgToMasterPluginManager() {
      return this.mapMsgToMasterPluginManager;
   }

   /**
    * @return null if no forwarding is done
    */
   public String forwardPublish(SessionInfo sessionInfo, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      //log.error(ME, "forwardPublish() is not implemented!");
      return null;
      //return Constants.RET_FORWARD_ERROR; // "<qos><state>FORWARD_ERROR</state></qos>"
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

   public final XmlBlasterConnection getConnection(SessionInfo publisherSession, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId("","1.0",
                                        msgUnitWrapper.getContentMime(), msgUnitWrapper.getContentMimeExtended());
      NodeId nodeId = domainMapper.getMasterId(msgUnitWrapper);
      NodeInfo nodeInfo = getNodeInfo(nodeId);

      I_LoadBalancer balancer = loadBalancerPluginManager.getPlugin("RoundRobin", "1.0"); // TODO!!!! Allow other plugins
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
