/*------------------------------------------------------------------------------
Name:      NodeId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holds the unique name of a cluster node
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;


/**
 * Holds the unique name of an xmlBlaster server instance (= cluster node)
 * @author ruff@swand.lake.de 
 * @since 0.79e
 * @url http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html
 */
public final class NodeId implements Comparable, java.io.Serializable
{
   private static final String ME = "NodeId";

   public NodeId(String id) {
      setId(id);
   }

   public final String getId() {
      return id;
   }

   public final void setId(String id) {
      if (id == null || id.length() < 1) {
         org.xmlBlaster.util.Global.instance().getLog("cluster").error(ME, "Cluster node has no name");
         id = "NoNameNode";
      }
      this.id = id;
   }

   public final String toString() {
      return getId();
   }
 
   /**
    * Needed for use in TreeSet and TreeMap, enforced by java.lang.Comparable
    */
   public final int compareTo(Object obj)  {
      NodeId n = (NodeId)obj;
      return toString().compareTo(n.toString());
   }

   public final boolean equals(NodeId n) {
      if (id == null) {
         if (n == null || n.getId() == null) return true;
         return false;
      }
      if (n == null || n.getId() == null) return false;
      return id.equals(n.getId());
   }
  
   private String id;
}
