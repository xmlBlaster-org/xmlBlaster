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
public class ClusterManager
{
   private final String ME = "DomainToMaster";

   private final Global glob;
   private final Log log;

   private MapMsgToMasterPluginManager mapMsgToMasterPluginManager = null;

   /**  The key is a 'node Id', the value an XmlBlasterConnection entry */
   private Map connectionMap;

   public ClusterManager(Global glob) {
      this.glob = glob;
      this.log = this.glob.getLog();
      this.connectionMap = new HashMap();
      this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(glob);
   }

   /**
    * The plugin loader instance to map messages to their master node. 
    */
   private MapMsgToMasterPluginManager getMapMsgToMasterPluginManager(){
      if (this.mapMsgToMasterPluginManager == null) {
         synchronized(this) {
            if (this.mapMsgToMasterPluginManager == null)
               this.mapMsgToMasterPluginManager = new MapMsgToMasterPluginManager(this.glob);
         }
      }
      return this.mapMsgToMasterPluginManager;
   }

   public String forwardPublish(SessionInfo sessionInfo, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      log.error(ME, "forwardPublish() is not implemented!");
      return Constants.RET_FORWARD_ERROR; // "<qos><state>FORWARD_ERROR</state></qos>"
   }

   public void addConnection(NodeId nodeId, XmlBlasterConnection connection) {
      this.connectionMap.put(nodeId.getId(), connection);
   }

   public void removeConnection(NodeId nodeId) {
      this.connectionMap.remove(nodeId.getId());
   }

   public XmlBlasterConnection getConnection(SessionInfo publisherSession, MessageUnitWrapper msgUnitWrapper) throws XmlBlasterException {
      I_MapMsgToMasterId domainMapper = this.mapMsgToMasterPluginManager.getMapMsgToMasterId("","1.0",
                                        msgUnitWrapper.getContentMime(), msgUnitWrapper.getContentMimeExtended());
      NodeId nodeId = domainMapper.getMasterId(msgUnitWrapper);
      /*
      NodeInfo nodeInfo = nodeInfoMap.get(nodeId.getId());
      I_LoadBalancer balancer = 
      XmlBlasterConnection con = balancer.getConnection(nodeInfo);
      XmlBlasterConnection con = (XmlBlasterConnection)connectionMap.get(nodeId.getId());
      if (con != null)
         return con;
      */
      return null;
   }

   public XmlBlasterConnection getConnection(NodeId nodeId) {
      return (XmlBlasterConnection)connectionMap.get(nodeId.getId());
   }
}
