/*------------------------------------------------------------------------------
Name:      ClusterNode.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding all information about the current node.
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.engine.Global;
import org.xmlBlaster.client.protocol.XmlBlasterConnection;

import java.util.Map;
import java.util.TreeMap;
import java.util.Iterator;

/**
 * This class holds the informations about an xmlBlaster server instance (=cluster node). 
 * <p />
 * It collects the node informations from NodeInfo.java, NodeDomainInfo.java and NodeStateInfo.java
 */
public final class ClusterNode
{
   private final String ME;
   private final Global glob;
   
   private XmlBlasterConnection xmlBlasterConnection = null;
   private boolean available;

   /** Holds address and backup informations */
   private NodeInfo nodeInfo;
   
   /** Holds performance informations for load balancing */
   private NodeStateInfo state;
   
   /** Hold mapping informations to map a message to a master node */
   private Map domainInfoMap;

   /**
    * Create an object holding all informations about a node
    */
   public ClusterNode(Global glob, NodeId nodeId) {
      this.glob = glob;
      this.nodeInfo = new NodeInfo(glob, nodeId);
      this.state = new NodeStateInfo(glob);
      this.ME = "ClusterNode." + getId();
   }

   /**
    * Convenience, delegates call to NodeInfo. 
    * @return The unique name of the managed xmlBlaster instance e.g. "bilbo.mycompany.com"
    */
   public String getId(){
     return nodeInfo.getId();
   }

   /**
    * Convenience, delegates call to NodeInfo. 
    * @return The unique name of the managed xmlBlaster instance
    */
   public NodeId getNodeId() {
     return nodeInfo.getNodeId();
   }

   /**
    * Convenience, delegates call to NodeInfo. 
    * @param The unique name of the managed xmlBlaster instance
    */
   public void setNodeId(NodeId nodeId) {
      nodeInfo.setNodeId(nodeId);
   }

   public XmlBlasterConnection getXmlBlasterConnection() {
      return xmlBlasterConnection;
   }

   public void setXmlBlasterConnection(XmlBlasterConnection xmlBlasterConnection) {
      this.xmlBlasterConnection = xmlBlasterConnection;
   }

   public void resetXmlBlasterConnection() {
      if (this.xmlBlasterConnection != null) {
         this.xmlBlasterConnection.disconnect(null);
         this.xmlBlasterConnection = null;
      }
   }

   /**
    * Access the current nodeInfo of the node. 
    */
   public NodeInfo getNodeInfo() {
      return nodeInfo;
   }

   /**
    * Overwrite the current nodeInfo of the node. 
    */
   public void setNodeInfo(NodeInfo nodeInfo) {
      this.nodeInfo = nodeInfo;
   }

   /**
    * Access the current state of the node, like current CPU and memory informations. 
    */
   public NodeStateInfo getNodeStateInfo() {
      return state;
   }

   /**
    * Access the current state of the node, like current CPU and memory informations. 
    */
   public void setNodeStateInfo(NodeStateInfo state) {
      this.state = state;
   }

   /**
    * Access the filter rules to determine the master of a message. 
    */
   public Map getDomainInfoMap() {
      return domainInfoMap;
   }

   /**
    * Set the filter rules to determine the master of a message. 
    */
   public void addDomainInfo(NodeDomainInfo domainInfo) {
      if (this.domainInfoMap == null) this.domainInfoMap = new TreeMap();
      this.domainInfoMap.put(""+domainInfo.getId(), domainInfo);
   }

   /**
    * Check if we have currently a functional connection to this node. 
    */
   public boolean isAvailable() {
      return available;
   }

   /**
    * Set the connection status
    */
   public void setAvailable(boolean available) {
      this.available = available;
   }

   /**
    * Dump state of this object into a XML ASCII string.
    */
   public final String toXml()
   {
      return toXml((String)null);
   }

   /**
    * Dump state of this object into a XML ASCII string.
    * @param extraOffset indenting of tags for nice output
    */
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer(512);
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("<clusternode id='").append(getId()).append("'>");

      sb.append(getNodeInfo().toXml(extraOffset + "   "));

      if (getDomainInfoMap() != null) {
         Iterator it = getDomainInfoMap().values().iterator();
         while (it.hasNext()) {
            NodeDomainInfo info = (NodeDomainInfo)it.next();
            sb.append(info.toXml(extraOffset + "   "));
         }
      }
      
      sb.append(getNodeStateInfo().toXml(extraOffset + "   "));
      
      sb.append(offset).append("</clusternode>");

      return sb.toString();
   }
}
