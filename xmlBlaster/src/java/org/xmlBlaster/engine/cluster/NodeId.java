/*------------------------------------------------------------------------------
Name:      NodeId.java
Project:   xmlBlaster.org
Copyright: xmlBlaster.org, see xmlBlaster-LICENSE file
Comment:   Holds the unique name of a cluster node
Author:    ruff@swand.lake.de
------------------------------------------------------------------------------*/
package org.xmlBlaster.engine.cluster;

import org.xmlBlaster.util.Log;

/**
 * Holds the unique name of an xmlBlaster server instance (= cluster node)
 * @author ruff@swand.lake.de 
 * @since 0.79e
 * @url http://www.xmlBlaster.org/xmlBlaster/doc/requirements/cluster.html
 */
public final class NodeId
{
   private static final String ME = "NodeId";

   public NodeId(String id) {
      setId(id);
   }

   public String getId() {
      return id;
   }

   public void setId(String id) {
      if (id == null || id.length() < 1) {
         Log.error(ME, "Cluster node has no name");
         id = "NoNameNode";
      }
      this.id = id;
   }

   public String toString() {
      return getId();
   }

   private String id;
}
