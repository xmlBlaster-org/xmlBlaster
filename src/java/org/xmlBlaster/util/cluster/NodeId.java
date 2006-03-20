/*------------------------------------------------------------------------------
Name:      NodeId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holds the unique name of a cluster node
Author:    xmlBlaster@marcelruff.info
------------------------------------------------------------------------------*/
package org.xmlBlaster.util.cluster;

import java.util.logging.Logger;


/**
 * Holds the unique name of an xmlBlaster server instance (= cluster node)
 * @author xmlBlaster@marcelruff.info 
 * @since 0.79e
 * @see <a href="http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html">cluster requirement</a>
 */
public final class NodeId implements Comparable, java.io.Serializable
{
   private static final long serialVersionUID = 1L;
   private static Logger log = Logger.getLogger(NodeId.class.getName());
   private static final String ME = "NodeId";

   /**
    * @see #setId(String)
    */
   public NodeId(String id) {
      setId(id);
   }

   /**
    * @return e.g. "heron" (/node/heron/... is stripped)
    */
   public final String getId() {
      return id;
   }

   /**
    * You need to pass a valid node name without any special characters,
    * e.g. "http://xy.com:3412" is not allowed as it contains '/' and ':' chars. 
    * @param id The cluster node id, e.g. "heron".<br />
    * If you pass "/node/heron/client/joe" everything is stripped to get "heron"
    * @see org.xmlBlaster.util.Global#getStrippedId()
    */
   public final void setId(String id) {
      if (id == null || id.length() < 1) {
         log.severe("Cluster node has no name please specify one with -cluster.node.id XXX");
         id = "NoNameNode";
      }
      this.id = id;
      if (this.id.startsWith("/node/"))
         this.id = this.id.substring("/node/".length()); // strip leading "/node/"

      int index = this.id.indexOf("/");   // strip tailing tokens, e.g. from "heron/client/joe" make a "heron"
      if (index == 0) {
         throw new IllegalArgumentException(ME+": The given cluster node ID '" + id + "' may not start with a '/'");
      }
      if (index > 0) {
         this.id = this.id.substring(0, index);
      }
   }

   /**
    * @see #getId()
    */
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
