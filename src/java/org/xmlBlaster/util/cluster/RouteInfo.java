/*------------------------------------------------------------------------------
Name:      RouteInfo.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.cluster;

import org.apache.commons.collections.functors.ForClosure;
import org.xmlBlaster.util.Timestamp;
import org.xmlBlaster.util.XmlBuffer;

/**
 * This class holds the information about a route node which the message passed. 
 * @author xmlBlaster@marcelruff.info
 */
public final class RouteInfo implements java.io.Serializable {
   //private transient final Global;
   private NodeId nodeId;
   private int stratum;
   private Timestamp timestamp;
   public static final boolean DEFAULT_dirtyRead = false;
   private boolean dirtyRead = DEFAULT_dirtyRead;

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
	   return toXml(extraOffset, false);
   }
   public final String toXml(String extraOffset, boolean forceReadable)
   {
      XmlBuffer sb = new XmlBuffer(126);
      String offset = "\n ";
      if (extraOffset == null) extraOffset = "";
      offset += extraOffset;

      sb.append(offset).append(" <node id='").appendAttributeEscaped(getNodeId().getId());
      sb.append("' stratum='").append(getStratum());
      sb.append("' timestamp='").append(getTimestamp().getTimestamp()).append("'");
      //if (dirtyRead != DEFAULT_dirtyRead)
         sb.append(" dirtyRead='").append(dirtyRead).append("'");
         
      if (forceReadable) {
         sb.append(">");
         sb.appendEscaped(getTimestamp().toString());
         sb.append("</node>");
      }
      else {
         sb.append("/>");
      }
      return sb.toString();
   }
}
