/*------------------------------------------------------------------------------
Name:      RouteInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holding the message specific information about a node.
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Timestamp;

/**
 * This class holds the information about a route node which the message passed. 
 */
public final class RouteInfo {

   private NodeId nodeId;
   private int stratum;
   private Timestamp timestamp;
   private boolean dirtyRead = NodeDomainInfo.DEFAULT_dirtyRead;

   /**
    * @param nodeId The unique name of the xmlBlaster instance
    * @param stratum The distance from the node to the master node, if you don't know
    *                it set it to 0.
    * @param timestamp The receive timestamp of the message (nano seconds)
    */
   public RouteInfo(NodeId nodeId, int stratum, Timestamp timestamp) {
     setNodeId(nodeId);
     setStratum(stratum);
     setTimestamp(timestamp);
   }

   /**
    * The unique node name of the xmlBlaster instance. 
    */
   public void setNodeId(NodeId nodeId) {
      this.nodeId = nodeId;
   }

   /**
    * The unique node name of the xmlBlaster instance. 
    */
   public NodeId getNodeId() {
      return this.nodeId;
   }

   /**
    * The unique node name of the xmlBlaster instance. 
    * @param The string representation of my name
    */
   public String getId() {
      return this.nodeId.getId();
   }

   /**
    * The distance from the current xmlBlaster node from the
    * master node (for this message). 
    */
   public void setStratum(int stratum) {
      this.stratum = stratum;
   }

   /**
    * The distance from the current xmlBlaster node from the
    * master node (for this message). 
    */
   public int getStratum() {
      return this.stratum;
   }

   /**
    * Message receive timestamp in nano seconds
    */
   public void setTimestamp(Timestamp timestamp) {
      this.timestamp = timestamp;
   }

   /**
    * Message receive timestamp in nano seconds
    */
   public Timestamp getTimestamp() {
      return this.timestamp;
   }

   /**
    * @param dirtyRead true if cluster slaves cache forwarded publish messages
    * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.dirtyRead.html">The cluster.dirtyRead requirement</a>
    */
   public final void setDirtyRead(boolean dirtyRead) {
      this.dirtyRead = dirtyRead;
   }

   public final boolean getDirtyRead() {
      return this.dirtyRead;
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
   public final String toXml(String extraOffset)
   {
      StringBuffer sb = new StringBuffer();
      String offset = "\n   ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append("   <node id='").append(getNodeId());
      sb.append("' stratum='").append(getStratum());
      sb.append("' timestamp='").append(getTimestamp().getTimestamp()).append("'");
      //if (dirtyRead != NodeDomainInfo.DEFAULT_dirtyRead)
         sb.append(" dirtyRead='").append(dirtyRead).append("'");
      sb.append("/>");

      return sb.toString();
   }
}
